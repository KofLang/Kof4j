package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §1219 (issue #1176) — `json.decode` silently coerced WRONG
 * primitives in typed record fields: a number into a String field became
 * "123" as DATA (`String.valueOf`), a digit string into an Int field became 30
 * (`Integer.parseInt`) and a non-boolean string into a Bool field became
 * FALSE (`Boolean.parseBoolean`) — the worst face, a refusal disgused as data.
 *
 * <p>Contract under test (the #1103/#1128 JSN006 decision applied at ANY
 * depth): a payload value of the wrong KIND in a typed slot throws the named
 * JSN006 error at runtime; correct payloads keep binding (including the §397
 * SQL-driver Integer 0/1 → Bool face, shared with the ORM binder).
 */
class JsonDecodeSwappedPrimitivesE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** Runs the program on the JVM; returns the FULL stderr/stdout text and the exit code. */
    private record Run(String output, int exitCode) {}

    private Run decodeRun(String payload) throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), """
                import kof.json

                record Item(String nome, Int idade, Bool ativo)

                main() {
                    var v = json.decode<Item>("%s")
                    println("decoded=" + v.nome)
                }
                """.formatted(payload.replace("\"", "\\\"")));
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compileSources(
                java.util.List.of(src.resolve("Main.kf")), out, Target.JVM,
                src.toAbsolutePath().normalize());
        assertTrue(r.success(), "compile failed: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String text = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        p.waitFor();
        return new Run(text, p.exitValue());
    }

    @Test
    void numberIntoStringFieldIsRefusedJvm() throws Exception {
        Run run = decodeRun("{\"nome\":123}");
        assertNotEquals(0, run.exitCode(),
                "a number into a String field must NOT decode as data (\"123\")");
        assertTrue(run.output().contains("JSN006"),
                "the refusal must be the NAMED JSN006, got: " + run.output());
        assertTrue(run.output().contains("String"),
                "the message must name the expected type, got: " + run.output());
    }

    @Test
    void stringIntoIntFieldIsRefusedJvm() throws Exception {
        Run run = decodeRun("{\"nome\":\"x\",\"idade\":\"30\",\"ativo\":true}");
        assertNotEquals(0, run.exitCode(),
                "a digit string into an Int field must NOT parse as 30 silently");
        assertTrue(run.output().contains("JSN006"),
                "the refusal must be the NAMED JSN006, got: " + run.output());
        assertTrue(run.output().contains("number"),
                "the message must say a number was expected, got: " + run.output());
    }

    @Test
    void nonBooleanStringIntoBoolFieldIsRefusedJvm() throws Exception {
        Run run = decodeRun("{\"nome\":\"x\",\"idade\":30,\"ativo\":\"sim\"}");
        assertNotEquals(0, run.exitCode(),
                "the string \"sim\" into a Bool field must NOT become false silently — "
                        + "the refusal disguised as data is the worst face");
        assertTrue(run.output().contains("JSN006"),
                "the refusal must be the NAMED JSN006, got: " + run.output());
        assertTrue(run.output().contains("boolean"),
                "the message must say a boolean was expected, got: " + run.output());
    }

    @Test
    void correctPayloadStillBindsJvm() throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), """
                import kof.json

                record Item(String nome, Int idade, Bool ativo)

                main() {
                    var ok = json.decode<Item>("{\\"nome\\":\\"x\\",\\"idade\\":30,\\"ativo\\":true}")
                    println("ok=" + ok.nome + "/" + String.valueOf(ok.idade) + "/" + String.valueOf(ok.ativo))
                }
                """);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compileSources(
                java.util.List.of(src.resolve("Main.kf")), out, Target.JVM,
                src.toAbsolutePath().normalize());
        assertTrue(r.success(), "compile failed: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String text = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "correct payloads keep decoding: " + text);
        assertTrue(text.contains("ok=x/30/true"), "the control run: " + text);
    }
}
