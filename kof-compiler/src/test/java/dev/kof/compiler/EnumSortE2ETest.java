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
 * #685 — enum em List.sort()/sorted() na ordem ORDINAL (natural).
 *
 * <p>O gate SEM097 rejeitava enum porque nenhum {@code sortTag} o cobria.
 * A mantenedora autorizou a ordem ordinal. A ordem natural de enum é
 * rebaixada para o comparador {@code a.compareTo(b)} (D-ENUM207: determinismo
 * por ordinal em todos os alvos).</p>
 */
class EnumSortE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    private static final String SRC = """
            enum Semaforo { VERMELHO, AMARELO, VERDE }

            main() {
                var xs = listOf(Semaforo.VERDE, Semaforo.VERMELHO, Semaforo.AMARELO)
                xs.sort()
                println(xs.get(0))
                println(xs.get(1))
                println(xs.get(2))
                var ys = listOf(Semaforo.VERDE, Semaforo.VERMELHO, Semaforo.AMARELO)
                var zs = ys.sorted()
                println(zs.get(0))
                println(zs.get(1))
                println(zs.get(2))
                println(ys.get(0))
            }
            """;

    private static final String EXPECTED = "VERMELHO\nAMARELO\nVERDE\nVERMELHO\nAMARELO\nVERDE\nVERDE";

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
    void enumSortManaged(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("EnumSort.kf");
        Files.writeString(src, SRC);
        Run jvm = runJvm(src, tmp.resolve("o-es-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals(EXPECTED, norm(jvm.output()), "JVM output");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(norm(jvm.output()), norm(scr.output()), "JVM x Script byte-a-byte");
        Run js = runJs(src, tmp.resolve("o-es-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(norm(jvm.output()), norm(js.output()), "JVM x JS byte-a-byte");
    }

    @Test
    void enumSortNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("EnumSortNative.kf");
        Files.writeString(src, SRC);
        Run n = runNativeX86(src, tmp.resolve("o-es-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
        assertEquals(EXPECTED, norm(n.output()), "Native output");
    }

    @Test
    void enumSortCross(@TempDir Path tmp) throws Exception {
        for (String arch : new String[]{"riscv64", "aarch64"}) {
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross " + arch + " toolchain + qemu ausente — pulando");
            Target t = arch.equals("riscv64") ? Target.NATIVE_RISCV64 : Target.NATIVE_AARCH64;
            Path src = tmp.resolve("EnumSort-" + arch + ".kf");
            Files.writeString(src, SRC);
            Path out = tmp.resolve("o-es-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + diags(r));
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(bin), arch + " binary must exist");
            String output = NativeRiscv64E2ETest.runQemu(arch, bin);
            assertEquals(EXPECTED, norm(output), arch + " output");
        }
    }
}
