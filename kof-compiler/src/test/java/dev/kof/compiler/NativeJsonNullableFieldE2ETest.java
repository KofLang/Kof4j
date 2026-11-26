package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §1238 (issue #1181, as fatias 1+2): o decode NATIVO de um record
 * com campo NULLABLE (`String?`) — antes recusado com o JSN002 do ENCODER (a
 * mensagem falava do encoder para um problema do decoder). O gate aceita o
 * nullable (o inner decide) e o find_value trata o literal JSON `null` como o
 * AUSENTE (não a string "null"). As 4 faces: presente, ausente, null-literal,
 * e a leitura do null.
 */
class NativeJsonNullableFieldE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(String output, int exitCode) {}

    private Run decodeRun() throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), """
                record Pessoa(String id, String? nome)

                main() {
                    var p = json.decode<Pessoa>("{\\"id\\":\\"1\\",\\"nome\\":\\"mel\\"}")
                    println(p.id)
                    var q = json.decode<Pessoa>("{\\"id\\":\\"2\\"}")
                    println(q.id)
                    var r = json.decode<Pessoa>("{\\"id\\":\\"3\\",\\"nome\\":null}")
                    println(r.id)
                    if (q.nome == null) { println("ausente-ok") }
                    if (r.nome == null) { println("null-ok") } else { println("null-ERRO") }
                }
                """);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compileSources(
                java.util.List.of(src.resolve("Main.kf")), out, Target.NATIVE,
                src.toAbsolutePath().normalize());
        assertTrue(r.success(), "compile failed: " + r.diagnostics().getDiagnostics());
        var pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        p.waitFor();
        return new Run(text, p.exitValue());
    }

    @Test
    void nullableFieldDecodesOnNativeAllFourFaces() throws Exception {
        Run run = decodeRun();
        assertEquals(0, run.exitCode(), run.output());
        assertEquals("1\n2\n3\nausente-ok\nnull-ok", run.output().trim(),
                "the nullable field accepts present/absent/null — the JSN002 of the ENCODER "
                        + "was the #1181 face");
    }
}
