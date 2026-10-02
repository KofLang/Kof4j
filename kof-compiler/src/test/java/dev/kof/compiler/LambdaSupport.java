package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runners JVM/Native/SCRIPT/JS dos testes de lambda ({@code LambdaE2ETest}).
 * Vivem fora da classe de teste para mantê-la abaixo do limite de 500 linhas de
 * teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os
 * testes e o nome da classe seguem no {@code LambdaE2ETest} — zero drift de
 * citação.
 */
abstract class LambdaSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    protected String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    protected String runNative(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    // Fase 4.1 (D-MEMORY-SAFETY, spec B-06): as faces de captura sempre foram
    // pinadas só em JVM/Native; Script e JS ficam de fora da matriz desde
    // 0.2.6-beta. Mesmos programas, mesma saida esperada nos 4 alvos.
    protected String runScript(Path source, String expected) throws IOException {
        Path outDir = source.getParent().resolve("script-out-" + System.nanoTime());
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(source), outDir, new String[0]);
        String output = r.stdout().replace("\r\n", "\n").trim();
        assertEquals(0, r.exitCode(), "SCRIPT exit code, output: '" + output + "'");
        assertEquals(expected, output, "SCRIPT output");
        return output;
    }

    protected String runJs(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compilation should succeed: " + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                (java.io.InputStream) new java.io.ByteArrayInputStream(new byte[0]), out);
        String output = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "JS exit code should be 0, output: '" + output + "'");
        assertEquals(expected, output, "JS output");
        return output;
    }
}
