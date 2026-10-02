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
 * D-MULTIPARADIGMA-PHASE1A slice 1h — {@code groupBy} (insertion-ordered
 * groups in a {@code Map<K,List<E>>}), reusing the {@code kof_*} per-target
 * pattern.
 *
 * <p>Surface (D-MULTIPARADIGMA-GROUPBY, plan §230): {@code groupBy((T)->K)}
 * with exactly one lambda; keys use the boxed map equality (same taxonomy
 * as {@code mapOf}/{@code put}); values are fresh lists in encounter order.</p>
 */
class ListGroupByE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    private static final String BASIC = """
            main() {
                var xs = listOf(1, 2, 3, 4)
                var g = xs.groupBy((n: Int) -> n % 2)
                println(g.size)
                var odds = g.get(1)
                if (odds != null) {
                    println(odds.size)
                    println(odds.get(0))
                    println(odds.get(1))
                }
                var evens = g.get(0)
                if (evens != null) {
                    println(evens.size)
                    println(evens.get(1))
                }
                var ss = listOf("a", "bb", "c")
                var h = ss.groupBy((s: String) -> s.length)
                println(h.size)
                var twos = h.get(2)
                if (twos != null) {
                    println(twos.size)
                    println(twos.get(0))
                }
                println(listOf(1).groupBy((n: Int) -> n).size)
            }
            """;

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
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

    @Test
    void groupByManaged(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("GroupBy.kf");
        Files.writeString(src, BASIC);
        Run jvm = runJvm(src, tmp.resolve("o-gb-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals("2\n2\n1\n3\n2\n4\n2\n1\nbb\n1", norm(jvm.output()), "JVM output");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(norm(jvm.output()), norm(scr.output()), "JVM x Script byte-a-byte");
        Run js = runJs(src, tmp.resolve("o-gb-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(norm(jvm.output()), norm(js.output()), "JVM x JS byte-a-byte");
    }

    @Test
    void groupByNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("GroupByNative.kf");
        Files.writeString(src, BASIC);
        Run n = runNativeX86(src, tmp.resolve("o-gb-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
        assertEquals("2\n2\n1\n3\n2\n4\n2\n1\nbb\n1", norm(n.output()), "Native output");
    }

    @Test
    void groupByCross(@TempDir Path tmp) throws Exception {
        for (String arch : new String[]{"riscv64", "aarch64"}) {
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross " + arch + " toolchain + qemu ausente — pulando");
            Target t = arch.equals("riscv64") ? Target.NATIVE_RISCV64 : Target.NATIVE_AARCH64;
            Path src = tmp.resolve("GroupBy-" + arch + ".kf");
            Files.writeString(src, BASIC);
            Path out = tmp.resolve("o-gb-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + diags(r));
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(bin), arch + " binary must exist");
            String output = NativeRiscv64E2ETest.runQemu(arch, bin);
            assertEquals("2\n2\n1\n3\n2\n4\n2\n1\nbb\n1", norm(output), arch + " output");
        }
    }
}
