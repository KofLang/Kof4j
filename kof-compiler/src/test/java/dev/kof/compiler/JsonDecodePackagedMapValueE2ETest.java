package dev.kof.compiler;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Residual of §571/#733: `json.decode<Map<String, Record>>` leaves the VALUE
// type argument UNQUALIFIED (bare `DeviceInfo`), so a following `get` lowers a
// `checkcast DeviceInfo` — the class does not exist at that path and the JVM
// dies `NoClassDefFoundError: DeviceInfo` at Main LOAD (Script resolves
// reflectively and runs). §571's chokepoint qualified only the OUTER type; this
// pins the recursive type-argument qualification.
public class JsonDecodePackagedMapValueE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path root) throws Exception {
        Files.createDirectories(root.resolve("pk"));
        Files.writeString(root.resolve("pk/R.kf"), """
                package pk

                record R(String x, Int n)
                """);
        Files.writeString(root.resolve("Main.kf"), """
                import pk.R

                main() {
                    var m = json.decode<Map<String, R>>("{\\"a\\":{\\"x\\":\\"hello\\",\\"n\\":7}}")
                    var r: R? = m.get("a")
                    if (r != null) {
                        println("x=" + r.x())
                        println("n=" + r.n())
                    }
                    println("size=" + m.size)
                }
                """);
        Path out = root.resolve("out");
        CompilationResult res = driver.compileSources(
                List.of(root.resolve("pk/R.kf"), root.resolve("Main.kf")),
                out, Target.JVM, root);
        assertTrue(res.success(), () -> res.diagnostics().getDiagnostics().toString());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "runtime: " + output);
        return output;
    }

    @Test
    void mapValueRecordQualifiesOnJvm(@TempDir Path tempDir) throws Exception {
        assertEquals("x=hello\nn=7\nsize=1", runJvm(tempDir.resolve("proj")));
    }

    @Test
    void mapValueRecordRunsInterpreted(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("proj");
        Files.createDirectories(root.resolve("pk"));
        Files.writeString(root.resolve("pk/R.kf"), """
                package pk

                record R(String x, Int n)
                """);
        Files.writeString(root.resolve("Main.kf"), """
                import pk.R

                main() {
                    var m = json.decode<Map<String, R>>("{\\"a\\":{\\"x\\":\\"hello\\",\\"n\\":7}}")
                    var r: R? = m.get("a")
                    if (r != null) {
                        println("x=" + r.x())
                    }
                }
                """);
        KofInterpreter.Result r = driver.interpret(
                List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, r.exitCode(), "stderr: " + r.stderr());
        assertTrue(r.stdout().contains("x=hello"), "stdout: " + r.stdout());
    }
}
