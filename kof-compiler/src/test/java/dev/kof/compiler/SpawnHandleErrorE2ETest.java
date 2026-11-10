package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A spawned expression ({@code val h = spawn work()}) whose task died with a
 * JVM {@code Error} (NoSuchMethodError, VerifyError, StackOverflowError) only
 * completed its handle exceptionally: with nobody awaiting the handle the
 * failure left no trace and the resource the task owned stayed open. An
 * {@code Error} is now reported on stderr when it happens; the handle still
 * fails for {@code await}.
 */
class SpawnHandleErrorE2ETest {

    @Test
    void anErrorInAnUnawaitedSpawnedTaskIsReported(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("M.kf");
        Files.writeString(file, """
                Int deep(n: Int) = deep(n + 1) + 1
                main() {
                    val h = spawn deep(0)
                    time.sleep(500)
                    println("main done")
                }
                """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "the program must end");
        String all = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(all.contains("spawn task failed: java.lang.StackOverflowError"), all);
        assertTrue(all.contains("main done"), all);
    }
}
