package dev.kof.compiler;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// #627: json.decode<T> for a record declared inside a package emitted a call
// to kof_json_decode_<SimpleName> while the runtime defines the decoder under
// the mangled FULLY-QUALIFIED name -> NoSuchMethodError at runtime. The same
// program with the record in the default package worked, so the conformance
// row (jsondec-record, default package only) never exercised it.
public class JsonDecodePackagedRecordE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runProject(Path root) throws Exception {
        Files.createDirectories(root.resolve("dominio"));
        Files.writeString(root.resolve("dominio/Ponto.kf"), """
                package dominio

                record Ponto(Int x, Int y)
                """);
        Files.writeString(root.resolve("Main.kf"), """
                import dominio.Ponto

                main() {
                    var p = Ponto(1, 2)
                    var texto = json.encode(p)
                    println("encode = " + texto)
                    var lido = json.decode<dominio.Ponto>(texto)
                    println("x = " + lido.x())
                    println("y = " + lido.y())
                    var d = json.decode<PontoDefault>("{\\"v\\":9}")
                    println("default = " + d.v())
                }

                record PontoDefault(Int v)
                """);
        List<Path> sources = List.of(
                root.resolve("dominio/Ponto.kf"), root.resolve("Main.kf"));
        Path out = root.resolve("out");
        CompilationResult r = driver.compileSources(sources, out, Target.JVM, root);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "runtime: " + output);
        return output;
    }

    private static final String PONTO_KF = """
                package dominio

                record Ponto(Int x, Int y)
                """;
    private static final String MAIN_KF = """
                import dominio.Ponto

                main() {
                    var lido = json.decode<Ponto>("{\\"x\\":7,\\"y\\":8}")
                    println(lido.x())
                }
                """;

    // Script/interpreter face of the same call — the interpreter reaches the
    // generated runtime reflectively through the SAME JsonDispatch name, so
    // the #627 fix must hold there too (measured, not assumed).
    @Test
    void interpreterDecodesPackagedRecord(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("proj");
        Files.createDirectories(root.resolve("dominio"));
        Files.writeString(root.resolve("dominio/Ponto.kf"), PONTO_KF);
        Files.writeString(root.resolve("Main.kf"), MAIN_KF);
        KofInterpreter.Result r = driver.interpret(
                List.of(root.resolve("Main.kf")),
                root, new String[0]);
        assertEquals(0, r.exitCode(), "stderr: " + r.stderr());
        assertTrue(r.stdout().contains("7"), "stdout: " + r.stdout());
    }

    @Test
    void decodePackagedRecordMatchesJvmDefaultPackage(@TempDir Path tempDir) throws Exception {
        String output = runProject(tempDir.resolve("proj"));
        assertEquals("encode = {\"x\":1,\"y\":2}\n"
                + "x = 1\n"
                + "y = 2\n"
                + "default = 9", output);
    }
}
