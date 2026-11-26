package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §1234 (issue #1177): `json.decode` de um record GENÉRICO instanciado
 * (`record Pagina<T>(List<T> items, String? nextCursor)` decodificado como
 * `Pagina<Item>`). Antes: a assinatura apagava o T (`ArrayList<Object>`), o
 * binder reflexivo lia o erasure e a mensagem JSN006 descrevia o PAYLOAD
 * ("expected an array") em vez do problema do alvo. Agora: a assinatura
 * formaliza o T (`TT;`), o record genérico emite a ClassSignature e o
 * call-site roteia o decode instanciado pelo decoder signature-guided (#633)
 * com o tvar substituído pelo arg por nome.
 */
class GenericRecordJsonDecodeE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(String output, int exitCode) {}

    private Run decodeRun() throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), """
                import kof.json

                record Item(String id)
                record NoArg(List<Item> items, String? nextCursor)
                record Pagina<T>(List<T> items, String? nextCursor)

                main() {
                    var payload = "{\\"items\\":[{\\"id\\":\\"a\\"}],\\"nextCursor\\":null}"
                    var a = json.decode<NoArg>(payload)
                    println("raw=" + a.items.size())
                    var p = json.decode<Pagina<Item>>(payload)
                    println("gen=" + p.items.size() + " " + p.items.get(0).id)
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
        p.waitFor();
        return new Run(text, p.exitValue());
    }

    @Test
    void instantiatedGenericRecordDecodesLikeTheRawOne() throws Exception {
        Run run = decodeRun();
        assertEquals(0, run.exitCode(), run.output());
        assertEquals("raw=1\ngen=1 a", run.output().trim(),
                "the generic decode must bind Item (not raw maps) — the misleading JSN006 "
                        + "was the #1177 defect; now both faces agree");
    }
}
