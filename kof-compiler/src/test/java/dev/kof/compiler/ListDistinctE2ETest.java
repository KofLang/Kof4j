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
 * D-MULTIPARADIGMA-PHASE1A slice 1e — {@code distinct} (dedup preserving
 * first-occurrence order), reusing the {@code kof_list_*} per-target pattern.
 *
 * <p>Equality rule per plan §4: String content, rest identity/pointer (like
 * {@code contains}). No lambda: the only new surface is the method name.
 * BOUNDARY: {@code take}/{@code drop}/{@code slice} belong to pagination P1.</p>
 */
class ListDistinctE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    private static final String BASIC = """
            main() {
                var xs = listOf(3, 1, 2, 1, 3)
                var d = xs.distinct()
                println(d.size)
                println(d.get(0))
                println(d.get(1))
                println(d.get(2))
                println(listOf().distinct().size)
                var ss = listOf("b", "a", "b")
                println(ss.distinct().get(0))
                println(ss.distinct().get(1))
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
    void distinctManaged(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Distinct.kf");
        Files.writeString(src, BASIC);
        Run jvm = runJvm(src, tmp.resolve("o-dt-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals("3\n3\n1\n2\n0\nb\na", norm(jvm.output()), "JVM output");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(norm(jvm.output()), norm(scr.output()), "JVM x Script byte-a-byte");
        Run js = runJs(src, tmp.resolve("o-dt-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(norm(jvm.output()), norm(js.output()), "JVM x JS byte-a-byte");
    }

    @Test
    void distinctNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("DistinctNative.kf");
        Files.writeString(src, BASIC);
        Run n = runNativeX86(src, tmp.resolve("o-dt-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
        assertEquals("3\n3\n1\n2\n0\nb\na", norm(n.output()), "Native output");
    }

    @Test
    void distinctCross(@TempDir Path tmp) throws Exception {
        for (String arch : new String[]{"riscv64", "aarch64"}) {
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross " + arch + " toolchain + qemu ausente — pulando");
            Target t = arch.equals("riscv64") ? Target.NATIVE_RISCV64 : Target.NATIVE_AARCH64;
            Path src = tmp.resolve("Distinct-" + arch + ".kf");
            Files.writeString(src, BASIC);
            Path out = tmp.resolve("o-dt-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + diags(r));
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(bin), arch + " binary must exist");
            String output = NativeRiscv64E2ETest.runQemu(arch, bin);
            assertEquals("3\n3\n1\n2\n0\nb\na", norm(output), arch + " output");
        }
    }
}
