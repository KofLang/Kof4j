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
 * D-MULTIPARADIGMA-PHASE1A slice 1b — {@code find} (first match or null) and
 * {@code count(pred)} (arity-overload of bare {@code count}), reusing the
 * {@code kof_list_*} per-target pattern.
 *
 * <p>Null contract mirrors {@code Map.get}-missing per target (JVM/Script/JS
 * null, Native 0); callers use the {@code V?} idiom ({@code == null}).
 * BOUNDARY: {@code take}/{@code drop}/{@code slice} belong to pagination P1.
 * Float predicates are out: {@code x > 2.0f} inside a lambda body is a JVM
 * VerifyError for ALL higher-orders incl. untouched {@code filter}
 * (pre-existing emitter hole; bare-float compares fine) — measured 28/09.</p>
 */
class ListFindCountE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    private static final String BASIC = """
            main() {
                var xs = listOf(1, 2, 3)
                var f = xs.find((x) -> x > 1)
                assert(f != null, "found")
                assert(f == 2, "value")
                var m = xs.find((x) -> x > 9)
                assert(m == null, "missing")
                assert(xs.count((x) -> x > 1) == 2, "count-T")
                assert(xs.count((x) -> x > 9) == 0, "count-F")
                assert(listOf().count((x) -> true) == 0, "count-empty")
                assert(xs.count() == 3, "bare-count")
                var ss = listOf("a", "bb")
                assert(ss.find((s) -> s == "bb") == "bb", "find-str")
                assert(ss.find((s) -> s == "z") == null, "find-str-miss")
                var ls = listOf(1L, 2L)
                assert(ls.find((x) -> x > 1L) == 2L, "find-long")
                var bs = listOf(true, false)
                assert(bs.find((b) -> b) == true, "find-bool")
                var ds = listOf(1.5, 2.5)
                assert(ds.find((d) -> d > 2.0) == 2.5, "find-double")
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

    @Test
    void findCountManaged(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("FindCount.kf");
        Files.writeString(src, BASIC);
        Run jvm = runJvm(src, tmp.resolve("o-fc-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        Run js = runJs(src, tmp.resolve("o-fc-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
    }

    @Test
    void findCountNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("FindCountNative.kf");
        Files.writeString(src, BASIC);
        Run n = runNativeX86(src, tmp.resolve("o-fc-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
    }

    @Test
    void findCountCross(@TempDir Path tmp) throws Exception {
        for (String arch : new String[]{"riscv64", "aarch64"}) {
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross " + arch + " toolchain + qemu ausente — pulando");
            Target t = arch.equals("riscv64") ? Target.NATIVE_RISCV64 : Target.NATIVE_AARCH64;
            Path src = tmp.resolve("FindCount-" + arch + ".kf");
            Files.writeString(src, BASIC);
            Path out = tmp.resolve("o-fc-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + diags(r));
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(bin), arch + " binary must exist");
            NativeRiscv64E2ETest.runQemu(arch, bin);
        }
    }
}
