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
 * never-ending {@code spawn} loop could not exit at all. The implicit join is
 * still a shutdown hook — the ONLY place from which a task blocked in
 * {@code accept()} can be released (the listen hook closes the socket there) —
 * and {@code process.exit} removes that hook before {@code System.exit}, so an
 * explicit exit never waits.
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

    /** The kof test harness ends a failing file through `process.exit`
     *  (`TestHarnessBuilder`), and that is the G376 shape: the file exits at
     *  once even while an app its tests booted still runs a never-ending task.
     *  The harness passes the same `kof_process_exit` call the program below
     *  makes; a direct `java.lang.System.exit` in user source keeps the `lab`
     *  behaviour (it waits for the tasks, like any JVM shutdown hook). */
    @Test
    void theTestHarnessExitDoesNotWaitForARunningTask(@TempDir Path tmp) throws Exception {
        Process p = run(tmp, """
                main() {
                    spawn {
                        while (true) { time.sleep(50) }
                    }
                    println("fail")
                    process.exit(1)
                }
                """);
        boolean ended = p.waitFor(20, TimeUnit.SECONDS);
        if (!ended) p.destroyForcibly();
        assertTrue(ended, "process.exit must not wait for a task that never ends");
        assertEquals(1, p.exitValue());
        assertEquals("fail", new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
    }

    /** The #1005 shape (measured on the keep-alive server): the task only
     *  finishes when the shutdown hook closes the listening socket, so the
     *  implicit join must NOT keep the JVM from starting to shut down. A
     *  non-daemon keeper thread deadlocked here for ever. */
    @Test
    void aSpawnedServerStopsWhenMainReturns(@TempDir Path tmp) throws Exception {
        Process p = run(tmp, """
                main() {
                    var app = web.app()
                    app.get("/a") { return "alpha" }
                    spawn {
                        app.listen(PORT)
                    }
                    println("main-done")
                }
                """.replace("PORT", String.valueOf(freePort())));
        boolean ended = p.waitFor(20, TimeUnit.SECONDS);
        if (!ended) p.destroyForcibly();
        assertTrue(ended, "the JVM must shut down once main returns");
        assertEquals(0, p.exitValue());
        // the shutdown closes the listening socket under the task: its failure
        // line is expected, only main's output must be intact
        assertTrue(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .startsWith("main-done"), "main output");
    }

    private static int freePort() throws Exception {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
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
