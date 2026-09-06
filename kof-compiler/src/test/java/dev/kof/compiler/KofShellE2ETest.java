package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TIER 6 — kof.shell (comando via shell, sobre kof.process). JVM-first.
 */
class KofShellE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void shellRunExecutesThroughShell(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    println(shell.run("echo -n hi"))
                    println(shell.run("printf world"))
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
            assertEquals("hi\nworld\ndone", out);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void shellReportsGapOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    println(shell.run("ls"))
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("native"), Target.NATIVE);
        assertTrue(result.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SHL001".equals(d.code())),
                "Native should report SHL001, got: " + result.diagnostics().getDiagnostics());
    }
}