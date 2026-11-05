package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JVM runtime waited for spawned tasks in a shutdown hook that spun until
 * the task count reached zero. A shutdown hook also runs on
 * {@code process.exit}, {@code System.exit} and SIGTERM, so a program with a
 * never-ending {@code spawn} loop could not exit at all. The implicit join now
 * lives in a non-daemon keeper thread: the end of {@code main} still waits for
 * the tasks, an explicit exit does not.
 */
class SpawnExitE2ETest {

    private static Process run(Path tmp, String kf) throws Exception {
        Path file = tmp.resolve("M.kf");
        Files.writeString(file, kf);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        return new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
    }

    @Test
    void processExitDoesNotWaitForARunningTask(@TempDir Path tmp) throws Exception {
        Process p = run(tmp, """
                main() {
                    spawn {
                        while (true) { time.sleep(50) }
                    }
                    println("bye")
                    process.exit(3)
                }
                """);
        boolean ended = p.waitFor(20, TimeUnit.SECONDS);
        if (!ended) p.destroyForcibly();
        assertTrue(ended, "process.exit must not wait for a task that never ends");
        assertEquals(3, p.exitValue());
        assertEquals("bye", new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
    }

    @Test
    void theEndOfMainStillWaitsForSpawnedTasks(@TempDir Path tmp) throws Exception {
        Process p = run(tmp, """
                main() {
                    spawn {
                        time.sleep(300)
                        println("task")
                    }
                    println("main")
                }
                """);
        assertTrue(p.waitFor(20, TimeUnit.SECONDS), "the program must end once the task ends");
        assertEquals(0, p.exitValue());
        assertEquals("main\ntask", new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim());
    }
}
