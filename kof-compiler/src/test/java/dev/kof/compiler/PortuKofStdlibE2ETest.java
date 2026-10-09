package dev.kof.compiler;

import dev.kof.compiler.lang.LanguageProfile;
import dev.kof.compiler.lang.PortuKofParity;
import dev.kof.compiler.lang.PortuKofStdlibMembers;
import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-PORTUKOF (07/10) — paridade COMPLETA da superfície stdlib (regra absoluta
 * da mantenedora): cada membro canônico do catálogo real tem exatamente um
 * alias pt-BR, e o alias resolve para o MESMO símbolo — mesma AST canônica,
 * mesmo output, nenhum runtime paralelo.
 */
class PortuKofStdlibE2ETest {

    private static String shape(CompilationUnitNode unit) {
        return unit.toString().replaceAll("Main\\.\\w+", "FILE").replaceAll("\\d+", "#");
    }

    private static CompilationUnitNode parseUnit(String src, String fileName,
                                                 LanguageProfile profile) {
        DiagnosticCollector diags = new DiagnosticCollector();
        Lexer lexer = new Lexer(src, fileName, diags, profile);
        List<Token> tokens = lexer.tokenize();
        assertFalse(diags.hasErrors(), diags.getDiagnostics().toString());
        Parser parser = new Parser(tokens, diags, fileName, profile);
        CompilationUnitNode unit = parser.parse();
        assertFalse(diags.hasErrors(), diags.getDiagnostics().toString());
        return PortuKofParity.normalize(profile, unit);
    }

    @Test
    void theGeneratedTableIsTotalAndBijective() {
        Map<String, String[][]> table = PortuKofStdlibMembers.table();
        assertFalse(table.isEmpty(), "gerado a partir do catalogo real — nunca vazio");
        for (String ns : StdCatalog.namespaces()) {
            String[][] rows = table.get(ns);
            assertTrue(rows != null, "namespace sem tabela de membros: " + ns);
            List<String> members = StdCatalog.membersOf(ns);
            assertEquals(members.size(), rows.length,
                    "cobertura total: " + ns);
            java.util.Set<String> canon = new java.util.HashSet<>();
            java.util.Set<String> alias = new java.util.HashSet<>();
            for (int i = 0; i < rows.length; i++) {
                assertEquals(members.get(i), rows[i][0], "ordem/paridade de membros em " + ns);
                assertTrue(canon.add(rows[i][0]), "canonical dup: " + ns + "." + rows[i][0]);
                assertTrue(alias.add(rows[i][1]),
                        "colisao de alias em " + ns + ": " + rows[i][1]);
            }
        }
        int total = table.values().stream().mapToInt(v -> v.length).sum();
        assertTrue(total >= 300, "o catalogo real tem centenas de membros; tabela=" + total);
    }

    @Test
    void namespaceMemberAliasesResolveToTheCanonicalSymbol() {
        String ptkf = "principal() {\n" +
                "    var v = matematica.max(3, 5)\n" +
                "    var t = textos.ehAlfa(\"abc\")\n" +
                "    escrevaln(v)\n" +
                "    escrevaln(t)\n" +
                "}\n";
        String kof = "main() {\n" +
                "    var v = math.max(3, 5)\n" +
                "    var t = strings.isAlpha(\"abc\")\n" +
                "    println(v)\n" +
                "    println(t)\n" +
                "}\n";
        assertEquals(shape(parseUnit(kof, "Main.kf", LanguageProfile.KOF)),
                shape(parseUnit(ptkf, "Main.ptkf", LanguageProfile.PORTUKOF)),
                "aliases de membros devem cair no simbolo canonico");
    }

    @Test
    void stdlibAliasesProduceTheSameOutputOnTheScriptTarget() throws Exception {
        String ptkf = "principal() {\n" +
                "    escrevaln(matematica.max(3, 5) + \"|\" + textos.ehAlfa(\"abc\") + \"|\" + listaDe(1, 2))\n" +
                "}\n";
        String kof = "main() {\n" +
                "    println(math.max(3, 5) + \"|\" + strings.isAlpha(\"abc\") + \"|\" + listOf(1, 2))\n" +
                "}\n";
        Path root = Files.createTempDirectory("ptkf-stdlib-");
        Path pt = root.resolve("Main.ptkf");
        Path kf = root.resolve("Main.kf");
        Files.writeString(pt, ptkf);
        Files.writeString(kf, kof);
        KofInterpreter.Result rp = new CompilerDriver().interpret(List.of(pt), root, new String[0]);
        KofInterpreter.Result rk = new CompilerDriver().interpret(List.of(kf), root, new String[0]);
        assertEquals(rk.stdout(), rp.stdout(),
                "mesma saida: kof=[" + rk.stdout() + "] ptkf=[" + rp.stdout() + "]");
        assertFalse(rp.stdout().isBlank(), "saida vazia = falso verde");
        assertEquals(0, rp.exitCode());
    }

    @Test
    void unknownNamespaceLikeMemberNamesAreNeverRewritten() {
        // `max` é membro de `math`/`matematica`; como chamada NUA é função do
        // usuário e deve permanecer `max` (PARTE 5/8: só a posição de símbolo
        // canônica é aliás).
        CompilationUnitNode u = parseUnit(
                "Int max(Int a) {\n    retorna a\n}\nprincipal() {\n    escrevaln(max(2))\n}\n",
                "Main.ptkf", LanguageProfile.PORTUKOF);
        assertTrue(u.toString().contains("max"), "funcao de usuario chamada `max` intacta");
    }
}
