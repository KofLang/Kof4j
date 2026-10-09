package dev.kof.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suporte para execução de binários cross sob QEMU nos testes da suíte
 * (test-architecture Fase 5, {@code D-TEST-ARCHITECTURE-PHASES}).
 * Centraliza o helper {@code runQemu} que antes era duplicado em 6 classes.
 */
public interface QemuRunSupport extends NativeToolchainAssumptions {

    CompilerDriver driver();

    default void runQemu(Path tempDir, Target target, String qemu, String source) throws IOException {
        Path file = tempDir.resolve("Main-" + target + "-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-qemu-" + target + "-" + System.nanoTime());
        CompilationResult result = driver().compile(file, outDir, target);
        assertTrue(result.success(), target + " compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        String arch = qemu.startsWith("qemu-") ? qemu.substring(5) : qemu;
        try {
            Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int ec = p.waitFor();
            assertEquals(0, ec, target + " runtime (qemu) exit " + ec + ", out: " + output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("qemu execution interrupted", e);
        }
    }
}
