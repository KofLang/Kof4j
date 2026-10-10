package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D-SCOPED-RESOURCES-GO slice 1 — {@code using (x = init, closer) { body }}
 * desugars (pre-lowering, {@code DesugarSteps.defaults()} first) to
 * {@code { var x = init; try { body } finally { closer } }}.
 *
 * <p>The closer is EXPLICIT (never a convention): {@code x.close()} is false
 * for {@code db} (the handle is a String closed via {@code db.close(handle)}),
 * while {@code conn.close()} / {@code sse.close()} stay writable as the
 * closer expression. A missing closer is a parse error, never a silent
 * non-closing program (R6).</p>
 */
class UsingDesugarE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    /** Binding visible in body and closer; closer runs after the body. */
    private static final String HAPPY = """
            main() {
                using (c = listOf(1, 2).get(0), println(c)) {
                    println(c + 10)
                }
                println("after")
            }
            """;

    // Order is body -> closer -> rest (try/finally semantics): the golden
    // "11 / 1 / after" reads oddly but is exactly acquire-use-release.

    /** The closer runs even when the body throws (finally semantics). */
    private static final String EXC = """
            main() {
                try {
                    using (c = 7, println(c)) {
                        println("body")
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught")
                }
            }
            """;

    /** No closer, no program: must not compile (R6 — never a silent leak). */
    private static final String NO_CLOSER = """
            main() {
                using (c = 1) {
                    println(c)
                }
            }
            """;

    /** The binding is block-scoped: use outside must not compile. */
    private static final String LEAK = """
            main() {
                using (c = 1, println(c)) {
                    println(c)
                }
                println(c)
            }
            """;

    /** Nested `using` closes inside-out (reverse order by nesting). */
    private static final String NEST = """
            main() {
                using (a = 1, println(a)) {
                    using (b = 2, println(b)) {
                        println(a + b)
                    }
                }
            }
            """;
    /** Flagship idiom: H2-hermetic `db` acquire → use → release.
     * No `DB_CLOSE_DELAY`: H2 drops a mem DB when its last connection closes,
     * so a leaked (unclosed) connection keeps `t` alive and the NEXT test's
     * CREATE fails — isolation-by-release, not by name. */
    private static final String DB_HAPPY = """
            main() {
                using (conn = db.connect("jdbc:h2:mem:usingdb"), db.close(conn)) {
                    db.execute(conn, "CREATE TABLE t (id INT PRIMARY KEY, v VARCHAR)")
                    db.execute(conn, "INSERT INTO t VALUES (1, 'a')")
                    var rows = db.query(conn, "select v from t where id = ?", 1)
                    println(rows.get(0))
                }
                println("closed")
            }
            """;

    /** The `db` closer runs on the exception path without breaking unwind. */
    private static final String DB_EXC = """
            main() {
                try {
                    using (conn = db.connect("jdbc:h2:mem:usingexc"), db.close(conn)) {
                        db.execute(conn, "CREATE TABLE t (id INT PRIMARY KEY)")
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught")
                }
            }
            """;

    /** JS face: own mem name — the Graal host shares the in-JVM H2 registry
     * with the JVM/Script tests, so `usingdb`/`usingexc` would collide. */
    private static final String DB_JS_HAPPY = """
            main() {
                using (conn = db.connect("jdbc:h2:mem:usingjs"), db.close(conn)) {
                    db.execute(conn, "CREATE TABLE t (id INT PRIMARY KEY, v VARCHAR)")
                    db.execute(conn, "INSERT INTO t VALUES (1, 'a')")
                    var rows = db.query(conn, "select v from t where id = ?", 1)
                    println(rows.get(0))
                }
                println("closed")
            }
            """;

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        return execJvm(out);
    }

    private Run execJvm(Path out) throws Exception {
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (ReflectiveOperationException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return new Run(false, "THROW: " + c);
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private Run runScript(Path src, Path root) {
        try {
            var r = driver.interpret(java.util.List.of(src), root, new String[0]);
            return new Run(r.exitCode() == 0, r.stdout());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage());
        }
    }

    private Run runNativeX86(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        if (!r.success()) return new Run(false, diags(r));
        Path binary = out.resolve("Default/Main");
        if (!Files.isRegularFile(binary)) return new Run(false, "no binary at " + binary);
        Process p = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        return new Run(p.waitFor() == 0, output);
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private static String norm(String s) {
        return s.replace("\r\n", "\n").trim();
    }

    /** Byte-for-byte JVM == Script == JS for a source (rule 5). */
    private void assertManagedTargets(Path tmp, String base, String source, String expected)
            throws Exception {
        Path src = tmp.resolve(base + ".kf");
        Files.writeString(src, source);
        Run jvm = runJvm(src, tmp.resolve("o-" + base + "-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals(expected, norm(jvm.output()), "JVM output");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(norm(jvm.output()), norm(scr.output()), "JVM x Script byte-a-byte");
        Run js = runJs(src, tmp.resolve("o-" + base + "-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(norm(jvm.output()), norm(js.output()), "JVM x JS byte-a-byte");
    }

    @Test
    void usingRunsBodyThenCloser(@TempDir Path tmp) throws Exception {
        assertManagedTargets(tmp, "UsingHappy", HAPPY, "11\n1\nafter");
    }

    @Test
    void usingClosesOnException(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingExc.kf");
        Files.writeString(src, EXC);
        Run jvm = runJvm(src, tmp.resolve("o-UsingExc-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals("body\n7\ncaught", norm(jvm.output()), "JVM output");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(norm(jvm.output()), norm(scr.output()), "JVM x Script byte-a-byte");
    }

    @Test
    void usingClosesOnExceptionNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("UsingExcNative.kf");
        Files.writeString(src, EXC);
        Run n = runNativeX86(src, tmp.resolve("o-using-exc-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
        assertEquals("body\n7\ncaught", norm(n.output()), "Native output");
    }

    /**
     * Pre-existing JS backend gap (no {@code using} involved): a hand-written
     * {@code throw} inside a nested {@code try} inside an outer
     * {@code try/catch} fails JS compilation with
     * {@code COMP002 unexpected KofCatchStart} — same family as §174 (fixed
     * 13/09 for return/throw-inside-if-inside-try). The desugar only emits
     * shapes the language already has, so this pins the boundary loudly
     * (JS lane's front) instead of hiding it: it goes red the day JS fixes it.
     */
    @Test
    void usingExceptionPathOnJsIsHonestComp002(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingExcJs.kf");
        Files.writeString(src, EXC);
        CompilationResult r = driver.compile(src, tmp.resolve("o-using-exc-js"), Target.JS);
        assertFalse(r.success(), "throw-in-nested-try must fail loudly on JS, not silently");
        assertTrue(diags(r).contains("KofCatchStart"),
                "the honest JS diagnostic, got: " + diags(r));
    }

    @Test
    void usingRunsOnNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("UsingNative.kf");
        Files.writeString(src, HAPPY);
        Run n = runNativeX86(src, tmp.resolve("o-using-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
        assertEquals("11\n1\nafter", norm(n.output()), "Native output");
    }

    @Test
    void missingCloserIsACompileError(@TempDir Path tmp) throws Exception {        Path src = tmp.resolve("UsingNoCloser.kf");
        Files.writeString(src, NO_CLOSER);
        CompilationResult r = driver.compile(src, tmp.resolve("o-using-nocloser"), Target.JVM);
        assertFalse(r.success(), "using without a closer must not compile");
        assertTrue(diags(r).contains("closer"),
                "the diagnostic must name the missing closer, got: " + diags(r));
    }

    @Test
    void bindingDoesNotEscapeTheBlock(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingLeak.kf");
        Files.writeString(src, LEAK);
        CompilationResult r = driver.compile(src, tmp.resolve("o-using-leak"), Target.JVM);
        assertFalse(r.success(), "use of the binding outside the block must not compile");
    }

    @Test
    void usingNestsWithReverseClose(@TempDir Path tmp) throws Exception {
        assertManagedTargets(tmp, "UsingNest", NEST, "3\n2\n1");
    }

    @Test
    void usingNestsWithReverseCloseNative(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("UsingNestNative.kf");
        Files.writeString(src, NEST);
        Run n = runNativeX86(src, tmp.resolve("o-using-nest-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
        assertEquals("3\n2\n1", norm(n.output()), "Native output");
    }

    @Test
    void usingDbHappyJvm(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingDb.kf");
        Files.writeString(src, DB_HAPPY);
        Path out = tmp.resolve("o-using-db");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        String d = diags(r);
        assertTrue(r.success(), "db+using must compile: " + d);
        assertFalse(d.contains("MEM014"),
                "the desugared finally-close must silence MEM014: " + d);
        Run run = execJvm(out);
        assertTrue(run.ok(), () -> "run: " + run.output());
        assertEquals("{\"v\":\"a\"}\nclosed", norm(run.output()), "db golden");
    }

    @Test
    void usingDbClosesOnExceptionJvm(@TempDir Path tmp) throws Exception {        Path src = tmp.resolve("UsingDbExc.kf");
        Files.writeString(src, DB_EXC);
        Path out = tmp.resolve("o-using-db-exc");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        String d = diags(r);
        assertTrue(r.success(), "db+using+throw must compile: " + d);
        assertFalse(d.contains("MEM014"),
                "the desugared finally-close must silence MEM014: " + d);
        Run run = execJvm(out);
        assertTrue(run.ok(), () -> "run: " + run.output());
        assertEquals("caught", norm(run.output()), "exception golden");
    }

    @Test
    void usingDbHappyScript(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingDbScript.kf");
        Files.writeString(src, DB_HAPPY);
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals("{\"v\":\"a\"}\nclosed", norm(scr.output()), "Script output");
    }

    @Test
    void usingDbClosesOnExceptionScript(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingDbExcScript.kf");
        Files.writeString(src, DB_EXC);
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals("caught", norm(scr.output()), "Script output");
    }

    @Test
    void usingDbHappyJs(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingDbJs.kf");
        Files.writeString(src, DB_JS_HAPPY);
        Run js = runJs(src, tmp.resolve("o-using-db-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals("{\"v\":\"a\"}\nclosed", norm(js.output()), "JS output");
    }

    private String runCross(Path src, String base, Path tmp, Target t, String arch, String expected)
            throws Exception {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                "cross " + arch + " toolchain + qemu ausente — pulando");
        Path out = tmp.resolve("o-" + base + "-" + arch);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), arch + " compile: " + diags(r));
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(bin), arch + " binary must exist");
        String output = NativeRiscv64E2ETest.runQemu(arch, bin);
        assertEquals(expected, norm(output), arch + " output");
        return output;
    }

    @Test
    void usingRunsOnCrossRiscv64(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingCrossRv.kf");
        Files.writeString(src, HAPPY);
        runCross(src, "UsingCrossRv", tmp, Target.NATIVE_RISCV64, "riscv64", "11\n1\nafter");
    }

    @Test
    void usingRunsOnCrossAarch64(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingCrossAa.kf");
        Files.writeString(src, HAPPY);
        runCross(src, "UsingCrossAa", tmp, Target.NATIVE_AARCH64, "aarch64", "11\n1\nafter");
    }

    @Test
    void usingNestsOnCrossRiscv64(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingNestRv.kf");
        Files.writeString(src, NEST);
        runCross(src, "UsingNestRv", tmp, Target.NATIVE_RISCV64, "riscv64", "3\n2\n1");
    }

    @Test
    void usingNestsOnCrossAarch64(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("UsingNestAa.kf");
        Files.writeString(src, NEST);
        runCross(src, "UsingNestAa", tmp, Target.NATIVE_AARCH64, "aarch64", "3\n2\n1");
    }
}
