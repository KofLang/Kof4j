package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1130 — a lambda or a `spawn` inside a `try` with `finally` used to break
 * the enclosing try's lowering (COMP002 NoSuchElementException: the nested
 * body lowerers saved `driver.finallyFrames` into the SAME deque reference,
 * so `clear()` emptied both and the restore added nothing) AND polluted
 * `tryDepth` (a later return unlinked the wrong handler count). The frames
 * are saved as a COPY and the depth is restored — the finally runs and the
 * return values match the oracle with or without the nested body.
 */
class NestedBodyFinallyE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SPAWN_FACE = """
            Int one() { return 1 }

            Int viaSpawn() {
                try {
                    val h = spawn one()
                    return await h
                } finally {
                    println("finally spawn")
                }
            }

            main() {
                println(viaSpawn())
            }
            """;

    private static final String LAMBDA_FACE = """
            Int viaLambda() {
                try {
                    val inc = (x: Int) -> x + 1
                    return inc(41)
                } finally {
                    println("finally lambda")
                }
            }

            main() {
                println(viaLambda())
            }
            """;

    @Test
    void spawnInsideTryFinallyCompilesAndRunsJvm() throws Exception {
        assertEquals("finally spawn\n1", runJvm("n1130-spawn", SPAWN_FACE));
    }

    @Test
    void lambdaInsideTryFinallyCompilesAndRunsJvm() throws Exception {
        assertEquals("finally lambda\n42", runJvm("n1130-lambda", LAMBDA_FACE));
    }

    private String runJvm(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                    getClass().getClassLoader())) {
                Class.forName("Default.Main", true, cl)
                        .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
    }
}
