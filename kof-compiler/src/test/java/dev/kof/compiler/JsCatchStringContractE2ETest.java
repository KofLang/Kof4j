package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #843: on the JS target the binding of {@code catch (String e)} received
 * an {@code Error} OBJECT, not the String the Kof contract requires
 * (`docs/language-reference/statements.md` §8: "catch (String e) catches Kof
 * exceptions (strings)"). Every INTERNAL runtime throw uses
 * {@code new Error(msg)} — the JS-idiomatic form — so `e == "msg"` was false,
 * `e.length()` was `undefined` and `e.startsWith(...)` died with a TypeError,
 * while JVM/Script return the String.
 *
 * <p>The fix normalizes the caught value ONCE at the catch emission
 * ({@code JsEmitter}), which covers every runtime throw site.
 *
 * <p>Oracle is the JVM. The assertions deliberately pin only the CONTRACT (the
 * value is a String that behaves as one); the message TEXT is not part of the
 * contract — the JS runtime words its messages differently
 * ("Index out of bounds: 99 (size 2)" vs the JVM's "Index 99 out of bounds for
 * length 2"), a separate, non-normative divergence catalogued in known-bugs.
 *
 * <p>NATIVE boundary (measured while writing this test, catalogued OPEN in
 * known-bugs): a native bounds error is {@code kof_bounds_error} →
 * {@code kof_panic}, a HARD abort — a `catch (String e)` never sees it, unlike
 * JVM/Script/JS. The internal-throw probes therefore run on the three
 * non-native targets; the native face pins the user-throw/rethrow contract that
 * native does honour.
 */
class JsCatchStringContractE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** Contract probes that every target (native included) must honour. */
    private static final String SRC_CORE = """
            String userThrow() {
                try {
                    throw "boom"
                } catch (String e) {
                    return "user:eq=" + (e == "boom") + ",isString=" + e.startsWith("")
                }
            }

            String rethrown() {
                try {
                    try {
                        throw "inner"
                    } catch (String e) {
                        throw e
                    }
                } catch (String e) {
                    return "rethrow:eq=" + (e == "inner") + ",len=" + (e.length() > 0)
                }
                return "rethrow:none"
            }

            String throwInCallee(Int n) {
                if (n < 0) { throw "neg:" + n }
                return "ok"
            }

            String caughtFromCallee() {
                try {
                    throwInCallee(-7)
                    return "callee:none"
                } catch (String e) {
                    return "callee:eq=" + (e == "neg:-7")
                }
            }

            main() {
                println(userThrow())
                println(rethrown())
                println(caughtFromCallee())
            }
            """;

    private static final String GOLDEN_CORE = String.join("\n",
            "user:eq=true,isString=true",
            "rethrow:eq=true,len=true",
            "callee:eq=true");

    /**
     * The #843 face: an INTERNAL runtime throw (`new Error(...)` in the JS
     * runtime) must still bind a String. Native is excluded — its bounds error
     * is a hard panic (see the class javadoc).
     */
    private static final String SRC_INTERNAL = """
            String listBounds() {
                try {
                    var xs = listOf(1, 2)
                    println(xs.get(99))
                    return "list:none"
                } catch (String e) {
                    return "list:isString=" + e.startsWith("") + ",len=" + (e.length() > 0)
                }
            }

            String parseFail() {
                try {
                    return "n:" + "nope".toInt()
                } catch (String e) {
                    return "parse:isString=" + e.startsWith("") + ",len=" + (e.length() > 0)
                }
            }

            main() {
                println(listBounds())
                println(parseFail())
            }
            """;

    private static final String GOLDEN_INTERNAL = String.join("\n",
            "list:isString=true,len=true",
            "parse:isString=true,len=true");

    // ── the oracle ─────────────────────────────────────────────────────────

    @Test
    void jvmIsTheContractForCaughtExceptions() throws Exception {
        assertEquals(GOLDEN_CORE, jvm("core", SRC_CORE), "o oráculo JVM mudou — revise o caso");
        assertEquals(GOLDEN_INTERNAL, jvm("core-internal", SRC_INTERNAL),
                "o oráculo JVM mudou — revise o caso");
    }

    // ── the #843 face, RED before the fix ──────────────────────────────────

    @Test
    void jsBindsAStringForInternalRuntimeThrows() throws Exception {
        assertEquals(GOLDEN_INTERNAL, js("internal", SRC_INTERNAL),
                "#843: o catch do JS deve ligar a String do throw interno, não o objeto Error");
    }

    @Test
    void jsMatchesTheJvmOnTheCaughtStringContract() throws Exception {
        assertEquals(GOLDEN_CORE, js("core", SRC_CORE));
    }

    @Test
    void scriptMatchesTheJvmOnTheCaughtStringContract() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC_CORE);
        KofInterpreter.Result r = driver.interpret(
                List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN_CORE, r.stdout().strip());
    }

    @Test
    void nativeX86MatchesTheJvmOnUserThrows() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("x86");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC_CORE);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), () -> "x86 saída: " + o);
        assertEquals(GOLDEN_CORE, o);
    }

    // ── harness ────────────────────────────────────────────────────────────

    private String jvm(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM): " + o);
        return o;
    }

    private String js(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "#843: o programa JS falhou — saída: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}