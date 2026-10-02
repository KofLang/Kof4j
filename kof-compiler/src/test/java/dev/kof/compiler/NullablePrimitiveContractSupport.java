package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runners JVM/SCRIPT/JS e o oráculo de alvo do corpus do contrato
 * {@code Nullable(primitivo)} ({@code NullablePrimitiveContractE2ETest}).
 * Vivem fora da classe de teste para mantê-la abaixo do limite de 500 linhas de
 * teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os
 * testes e o nome da classe seguem no {@code NullablePrimitiveContractE2ETest}
 * — zero drift de citação.
 */
abstract class NullablePrimitiveContractSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    protected String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            // Runner por reflexão: evita o launcher `java` mascarar
            // VerifyError como "JavaFX runtime ausente" (mesmo padrão de
            // NullablePrimitiveE2ETest/GenericOperandConcatE2ETest).
            Path runnerDir = outDir.resolveSibling(outDir.getFileName() + "-runner");
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            java.nio.file.Path javaHome = java.nio.file.Path.of(System.getProperty("java.home"));
            Process pCompile = new ProcessBuilder(javaHome.resolve("bin").resolve("javac").toString(),
                    "-d", runnerDir.toString(), runnerSrc.toString())
                    .redirectErrorStream(true).start();
            String compileOut = new String(pCompile.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, pCompile.waitFor(), "runner javac: " + compileOut);
            String classpath = String.join(java.io.File.pathSeparator, outDir.toString(), runnerDir.toString());
            Process p = new ProcessBuilder(javaHome.resolve("bin").resolve("java").toString(),
                    "-cp", classpath, "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            return assertTarget("JVM", ec, output, expected);
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    protected String runScript(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        try {
            KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), tempDir, new String[0]);
            return assertTarget("SCRIPT", r.exitCode(), r.stdout().replace("\r\n", "\n").trim(), expected);
        } catch (KofInterpretException e) {
            fail("SCRIPT frontend error: " + e.getMessage());
            return null;
        }
    }

    protected String runJs(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("js-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                (java.io.InputStream) new java.io.ByteArrayInputStream(new byte[0]), out);
        return assertTarget("JS", ec, out.toString().replace("\r\n", "\n").trim(), expected);
    }

    protected void runAll3(Path tempDir, String source, String expected) throws IOException {
        runJvm(tempDir, source, expected);
        runScript(tempDir, source, expected);
        runJs(tempDir, source, expected);
    }

    protected String assertTarget(String target, int ec, String output, String expected) {
        assertEquals(0, ec, target + " exit code, output: " + output);
        assertEquals(expected, output, target + " output");
        return output;
    }
}
