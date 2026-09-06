package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TIER 6 — cron maduro do scheduler.at. Verifica que um cron 5-campos
 * ("minuto hora mday mon wday") dispara a tarefa na próxima ocorrência.
 */
class KofCronE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void cronFiresAtNextMinuteBoundary(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        // */1 * * * * → dispara na virada de cada minuto (delay 1..60s).
        Files.writeString(source, """
                main() {
                    scheduler.at("*/1 * * * *") {
                        println("tick")
                    }
                    time.sleep(65000)
                    println("done")
                }
                """);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        assertEquals(0, ec, "exit, out=" + out);
        assertTrue(out.contains("tick"), "cron should fire within 65s, got: " + out);
        assertTrue(out.endsWith("done"), "main should finish, got: " + out);
    }
}