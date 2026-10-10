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
 * D-MULTIPARADIGMA-PHASE1A slice 1d — {@code flatMap} (each element maps to a
 * list; results concatenated in order), reusing the {@code kof_list_*}
 * per-target pattern.
 *
 * <p>BOUNDS: empty source and empty inner lists contribute nothing (no error).
 * BOUNDARY: {@code take}/{@code drop}/{@code slice} belong to pagination P1.</p>
 */
class ListFlatMapE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    private static final String BASIC = """
            main() {
                var xs = listOf(1, 2, 3)
                var ys = xs.flatMap((x) -> listOf(x, x * 10))
                println(ys.size)
                println(ys.get(0))
                println(ys.get(5))
                var e = listOf().flatMap((x) -> listOf(x))
                println(e.size)
                var z = listOf(1, 2).flatMap((x) -> listOf())
                println(z.size)
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
    void flatMapManaged(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("FlatMap.kf");
        Files.writeString(src, BASIC);
        Run jvm = runJvm(src, tmp.resolve("o-fm-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals("6\n1\n30\n0\n0", norm(jvm.output()), "JVM output");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(norm(jvm.output()), norm(scr.output()), "JVM x Script byte-a-byte");
        Run js = runJs(src, tmp.resolve("o-fm-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(norm(jvm.output()), norm(js.output()), "JVM x JS byte-a-byte");
    }

    @Test
    void flatMapNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("FlatMapNative.kf");
        Files.writeString(src, BASIC);
        Run n = runNativeX86(src, tmp.resolve("o-fm-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
        assertEquals("6\n1\n30\n0\n0", norm(n.output()), "Native output");
    }

    @Test
    void flatMapCross(@TempDir Path tmp) throws Exception {
        for (String arch : new String[]{"riscv64", "aarch64"}) {
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross " + arch + " toolchain + qemu ausente — pulando");
            Target t = arch.equals("riscv64") ? Target.NATIVE_RISCV64 : Target.NATIVE_AARCH64;
            Path src = tmp.resolve("FlatMap-" + arch + ".kf");
            Files.writeString(src, BASIC);
            Path out = tmp.resolve("o-fm-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + diags(r));
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(bin), arch + " binary must exist");
            String output;
            try {
                output = NativeRiscv64E2ETest.runQemu(arch, bin);
            } catch (AssertionError e) {
                throw new AssertionError(arch + ": " + e.getMessage(), e);
            }
            assertEquals("6\n1\n30\n0\n0", norm(output), arch + " output");
        }
    }
}
