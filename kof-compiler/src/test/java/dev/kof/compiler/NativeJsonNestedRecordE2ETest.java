package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §1253 (issue #1181, a fatia 3): o decode NATIVO de um record com
 * campo ANINHADO (`record Externo(String id, Interno interno)`) — o valor do
 * find_value É o TEXTO BRUTO do sub-objeto e o decode recursivo usa o mesmo
 * padrão do flat (NewObject+Dup+finds+ctor do interno; o resultado fica na
 * pilha para o ctor do pai). O keyspace do #1249 (o fnKey no branch de
 * instância) foi o que desbloqueou: antes o ctor do interno consultava a
 * chave errada e o pai lia a slot errada (o id=null + SEGV).
 */
class NativeJsonNestedRecordE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(String output, int exitCode) {}

    private Run decodeRun() throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), """
                record Interno(String a)
                record Externo(String id, Interno interno)

                main() {
                    var e = json.decode<Externo>("{\\"id\\":\\"1\\",\\"interno\\":{\\"a\\":\\"x\\"}}")
                    println(e.id)
                    println(e.interno.a)
                }
                """);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compileSources(
                java.util.List.of(src.resolve("Main.kf")), out, Target.NATIVE,
                src.toAbsolutePath().normalize());
        assertTrue(r.success(), "compile failed: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String text = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        p.waitFor();
        return new Run(text, p.exitValue());
    }

    @Test
    void nestedRecordDecodesOnNative() throws Exception {
        Run run = decodeRun();
        assertEquals(0, run.exitCode(), run.output());
        assertEquals("1\nx", run.output().trim(),
                "the nested record binds (the id + the inner field) — the JSN002 of the "
                        + "encoder was the #1181 slice-3 face; the #1249 keyspace unlocked it");
    }
}
