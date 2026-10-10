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

// GitHub #735: `json.decode<Map<String, Object>>` lowered to
// `kof_json_decode_object_map` because the lowerer treats `Object` as a user
// class; the JVM binder `kof_json_bind(Object.class, nestedMap)` then built a
// `new Object()` (no fields copied) instead of returning the parsed map, so a
// nested JSON object came back as an opaque `java.lang.Object` and a cast to
// `Map<String, Object>` threw ClassCastException. Arrays of objects (List
// path) were already correct, which is what hid this.
public class JsonDecodeMapObjectValueE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path root) throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), """
                main() {
                    var m = json.decode<Map<String, Object>>("{\\"caps\\":{\\"a\\":1},\\"n\\":5}")
                    var caps = m.get("caps") as Map<String, Object>
                    println("caps=" + caps.size)
                    println("a=" + caps.get("a"))
                    println("n=" + m.get("n"))
                    var arr = json.decode<List<Object>>("[{\\"k\\":2}]")
                    var first = arr.get(0) as Map<String, Object>
                    println("k=" + first.get("k"))
                }
                """);
        Path out = root.resolve("out");
        CompilationResult res = driver.compileSources(
                List.of(root.resolve("Main.kf")), out, Target.JVM, root);
        assertTrue(res.success(), () -> res.diagnostics().getDiagnostics().toString());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "runtime: " + output);
        return output;
    }

    @Test
    void nestedObjectValueIsMapOnJvm(@TempDir Path tempDir) throws Exception {
        assertEquals("caps=1\na=1\nn=5\nk=2", runJvm(tempDir.resolve("proj")));
    }

    @Test
    void nestedObjectValueIsMapInterpreted(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("proj");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), """
                main() {
                    var m = json.decode<Map<String, Object>>("{\\"caps\\":{\\"a\\":1}}")
                    var caps = m.get("caps") as Map<String, Object>
                    println("a=" + caps.get("a"))
                }
                """);
        KofInterpreter.Result r = driver.interpret(
                List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, r.exitCode(), "stderr: " + r.stderr());
        assertTrue(r.stdout().contains("a=1"), "stdout: " + r.stdout());
    }
}
