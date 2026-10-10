package dev.kof.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harness cross-target dos testes {@code NativeIo*CrossTest} (Fase 5 do
 * {@code test-architecture-plan}, {@code D-TEST-ARCHITECTURE-PHASES}): o
 * executor parametrizado por {@link Target} e o guard de toolchain que cada
 * face de {@code kof.io} repetia byte a byte. Cada subclasse mantem os seus
 * proprios {@code @Test} por alvo (JVM / riscv64 / aarch64) — a contagem e o
 * nome por alvo permanecem reais e visiveis; so o mecanismo de execucao e
 * compartilhado. O compilador nao e tocado (regra de ouro do plano).
 */
abstract class NativeCrossSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    protected static boolean has(String... cmds) {
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

    protected static String capture(Process p) throws IOException, InterruptedException {
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + out);
        return out;
    }

    /** Compila {@code src} para JVM e devolve a saida do processo. */
    protected static String runJvm(CompilerDriver driver, Path src, Path outDir) throws IOException {
        CompilationResult result = driver.compile(src, outDir, Target.JVM);
        assertTrue(result.success(), "jvm compile: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    /** Compila {@code src} para {@code target} e executa sob {@code qemu}. */
    protected static String runCross(CompilerDriver driver, Path src, Path outDir,
                                     String qemu, Target target) throws IOException {
        CompilationResult result = driver.compile(src, outDir, target);
        assertTrue(result.success(), "cross compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(qemu.substring(5), bin);
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    /** Variante de instancia: usa o {@link #driver} da subclasse. */
    protected String runJvm(Path src, Path outDir) throws IOException {
        return runJvm(driver, src, outDir);
    }

    /** Variante de instancia: usa o {@link #driver} da subclasse. */
    protected String runCross(Path src, Path outDir, String qemu, Target target) throws IOException {
        return runCross(driver, src, outDir, qemu, target);
    }

    /** Executa o binario nativo x86-64 local (Linux). */
    protected String runNative(Path src, Path outDir) throws IOException {
        CompilationResult result = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(result.success(), "x86-64 compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    protected Path base(Path tempDir, String tag) throws IOException {
        Path b = tempDir.resolve("base-" + tag);
        Files.createDirectories(b);
        return b;
    }
}
