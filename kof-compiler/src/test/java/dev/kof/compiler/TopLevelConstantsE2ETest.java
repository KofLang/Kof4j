package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED-first (23/11, app do Énois): uma tabela de CONSTANTES pequena escrita
 * como FUNÇÃO de topo (lista de 44 códigos, uma por assunto) tem que compilar.
 *
 * <p>Medido no app: com 43 entradas a classe de tela ainda compila; a 44ª
 * edição levou o arquivo `enois/CommunityTopic.kf` a PKG006 "import não
 * encontrado" — o arquivo existe e se chama assim, então o erro aponta para o
 * lugar errado. A hipótese (e o que este teste prende) é que o caminho da
 * tabela como função de topo NÃO é o afetado.
 */
class TopLevelConstantsE2ETest {

    private static String program(int n) {
        StringBuilder sb = new StringBuilder("package enois\n\n");
        sb.append("// as constantes na FUNÇÃO de topo (listOf dentro de classe cresce a classe)\n");
        sb.append("List<String> topics() {\n    return listOf(\"Todas\"");
        for (int i = 0; i < n; i++) {
            sb.append(", \"T").append(i).append('"');
        }
        sb.append(")\n}\n\nclass Topics {\n    static Int count() { return topics().size() }\n}\n");
        return sb.toString();
    }

    @Test
    void aTopLevelListOfConstantsCompiles(@TempDir Path dir) throws Exception {
        Path pkg = dir.resolve("enois");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Table.kf"), program(43), StandardCharsets.UTF_8);
        Path main = dir.resolve("Main.kf");
        Files.writeString(main, """
                import enois.Table

                main() {
                    println("n=" + Topics.count())
                }
                """, StandardCharsets.UTF_8);
        var r = new CompilerDriver().compileForTests(main, dir.resolve("out"), Target.NATIVE, dir);
        assertTrue(r.success(), "a tabela de topo compila: " + r.diagnostics().getDiagnostics());
    }

    /** A forma de NEGÓCIO que o app usa: `at(i)` e `label(code)` sobre a tabela. */
    @Test
    void aTopLevelTableWithAtAndLabelCompiles(@TempDir Path dir) throws Exception {
        Path pkg = dir.resolve("enois");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Table.kf"), program(43) + """
                class Lookup {
                    static String at(Int i) { return topics().get(i) }
                    static String label(String code) {
                        var i = 0
                        while (i < topics().size()) {
                            if (topics().get(i) == code) { return "" + i }
                            i = i + 1
                        }
                        return code
                    }
                }
                """, StandardCharsets.UTF_8);
        Path main = dir.resolve("Main.kf");
        Files.writeString(main, """
                import enois.Table

                main() {
                    println("a=" + Lookup.at(5) + " l=" + Lookup.label("T7"))
                }
                """, StandardCharsets.UTF_8);
        var r = new CompilerDriver().compileForTests(main, dir.resolve("out"), Target.NATIVE, dir);
        assertTrue(r.success(), "at/label compilam: " + r.diagnostics().getDiagnostics());
    }

    /** O controle: com a mesma tabela, um erro REAL do programa continua erro. */
    @Test
    void aRealErrorInsideTheFileStillFails(@TempDir Path dir) throws Exception {
        Path pkg = dir.resolve("enois");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Table.kf"), program(10) + """
                class Broken {
                    static Int x() { return "texto" }
                }
                """, StandardCharsets.UTF_8);
        Path main = dir.resolve("Main.kf");
        Files.writeString(main, """
                import enois.Table
                import enois.Broken

                main() {
                    println(Broken.x())
                }
                """, StandardCharsets.UTF_8);
        var r = new CompilerDriver().compileForTests(main, dir.resolve("out"), Target.NATIVE, dir);
        assertFalse(r.success(), "o erro de tipo dentro do arquivo segue erro");
    }
}
