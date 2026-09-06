package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TIER 6 — kof.workflow (jobs como código). JVM-first: registro de job
 * nomeado (closure), execução via run e pipeline sequencial.
 */
class KofWorkflowE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void workflowRegistersAndRunsJobs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    workflow.job("greet") {
                        println("hi")
                    }
                    workflow.run("greet")
                    workflow.pipeline("release", listOf("greet", "greet"))
                    println("done")
                }
                """);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit, out=" + out);
            assertEquals("hi\nhi\nhi\ndone", out);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void workflowRunMissingJobReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    println("[" + workflow.run("none") + "]")
                }
                """);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit, out=" + out);
            assertEquals("[]", out);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void workflowReportsWf001OnNonJvmTargets(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    workflow.job("x") { println("x") }
                    workflow.run("x")
                }
                """);
        // JS: gap honesto WF001 (nunca stub silencioso).
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(jsResult.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "WF001".equals(d.code())),
                "JS should report WF001, got: " + jsResult.diagnostics().getDiagnostics());
    }
}