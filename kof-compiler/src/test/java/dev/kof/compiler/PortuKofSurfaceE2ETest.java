package dev.kof.compiler;

import dev.kof.compiler.lang.KofKeywords;
import dev.kof.compiler.lang.LanguageProfile;
import dev.kof.compiler.lang.PortuKofParity;
import dev.kof.compiler.lang.PortuKofVocabulary;
import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-PORTUKOF (07/10) — paridade da superfície PortuKof com o Kof canônico.
 *
 * Regra absoluta da mantenedora: 100% do frontend Kof alcançável em .ptkf,
 * com o MESMO AST, o MESMO IR, os MESMOS erros. Estes testes travam:
 * bijetividade lexical; AST canônico-idêntico pós-normalização; comportamento
 * idêntico (script + compilação); paridade de diagnósticos; e a recusa
 * honesta das palavras reservadas (`funcao`).
 */
class PortuKofSurfaceE2ETest {

    private static String shape(CompilationUnitNode unit) {
        return unit.toString().replaceAll("Main\\.\\w+", "FILE").replaceAll("\\d+", "#");
    }

    private static CompilationUnitNode parseUnit(String src, String fileName,
                                                 LanguageProfile profile) {
        DiagnosticCollector diags = new DiagnosticCollector();
        return parseUnit(src, fileName, profile, diags);
    }

    private static CompilationUnitNode parseUnit(String src, String fileName,
                                                 LanguageProfile profile,
                                                 DiagnosticCollector diags) {
        Lexer lexer = new Lexer(src, fileName, diags, profile);
        List<Token> tokens = lexer.tokenize();
        assertFalse(diags.hasErrors(), "lex of " + fileName + ": " + diags.getDiagnostics());
        Parser parser = new Parser(tokens, diags, fileName, profile);
        CompilationUnitNode unit = parser.parse();
        assertFalse(diags.hasErrors(), "parse of " + fileName + ": " + diags.getDiagnostics());
        return PortuKofParity.normalize(profile, unit);
    }

    @Test
    void keywordTableIsBijectiveAndCollisionFree() {
        Map<String, String> ptToCanonical = new HashMap<>();
        Set<TokenType> covered = new HashSet<>();
        for (String[] p : PortuKofVocabulary.pairs()) {
            TokenType ct = KofKeywords.MAP.get(p[0]);
            assertNotNullToken(p[0], ct);
            String want = p[2].startsWith("BOOLEAN_LITERAL") ? "BOOLEAN_LITERAL" : p[2];
            assertEquals(want, ct.name(), "token for " + p[0]);
            ptToCanonical.put(p[1], p[0]);
            covered.add(ct);
        }
        assertEquals(KofKeywords.MAP.size(), ptToCanonical.size(),
                "every Lexer keyword must have exactly one PortuKof word (absolute parity)");
        assertEquals(KofKeywords.MAP.values().stream().distinct().count(), covered.size(),
                "token coverage incomplete");
        // bijetividade: duas palavras pt nunca colidem; e cada palavra pt
        // pertence à tabela do perfil com o token canônico
        Map<String, TokenType> ptTable = PortuKofVocabulary.keywords();
        for (String[] p : PortuKofVocabulary.pairs()) {
            TokenType viaTable = ptTable.get(p[1]);
            assertNotNullToken(p[1], viaTable);
            assertEquals(KofKeywords.MAP.get(p[0]), viaTable, "profile token for " + p[1]);
        }
    }

    private static void assertNotNullToken(String word, TokenType t) {
        org.junit.jupiter.api.Assertions.assertTrue(t != null, "no token for keyword word: " + word);
    }

    @Test
    void portuKofLexesKeywordsWithCanonicalTokenValues() {
        String src = "principal() {\n    se (verdadeiro) { escreva(\"oi\") }\n}\n";
        DiagnosticCollector diags = new DiagnosticCollector();
        List<Token> tokens = new Lexer(src, "Main.ptkf", diags,
                LanguageProfile.PORTUKOF).tokenize();
        assertFalse(diags.hasErrors(), diags.getDiagnostics().toString());
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).type(), "principal is a user name");
        assertEquals("principal", tokens.get(0).value());
        assertEquals(TokenType.IF, tokens.get(4).type());
        assertEquals("if", tokens.get(4).value(), "keyword VALUE is canonical");
        assertEquals(TokenType.BOOLEAN_LITERAL, tokens.get(6).type());
        assertEquals("true", tokens.get(6).value());
        assertEquals(TokenType.IDENTIFIER, tokens.get(9).type(), "escreva stays an identifier");
        assertEquals("escreva", tokens.get(9).value());
    }

    @Test
    void canonicalKofKeepsItsBehaviorUnchanged() {
        CompilationUnitNode u = parseUnit("main() {\n    if (true) { print(\"oi\") }\n}\n",
                "Main.kf", LanguageProfile.KOF);
        assertEquals("main", ((FunctionDeclarationNode) u.declarations().get(0)).name());
    }

    @Test
    void portuKofProducesTheSameAstAsKofAfterNormalization() {
        String ptkf = "Int soma(Int a, Int b) {\n" +
                "    retorna a + b\n" +
                "}\n" +
                "\n" +
                "principal() {\n" +
                "    var total = soma(10, 20)\n" +
                "    para (var x em listaDe(1, 2, 3)) {\n" +
                "        se (x == 2) {\n" +
                "            escreva(\"achou\")\n" +
                "        } senao {\n" +
                "            escreva(x)\n" +
                "        }\n" +
                "    }\n" +
                "    enquanto (total > 0) {\n" +
                "        total = total - 1\n" +
                "    }\n" +
                "    escrevaln(total)\n" +
                "}\n";
        String kof = "Int soma(Int a, Int b) {\n" +
                "    return a + b\n" +
                "}\n" +
                "\n" +
                "main() {\n" +
                "    var total = soma(10, 20)\n" +
                "    for (var x in listOf(1, 2, 3)) {\n" +
                "        if (x == 2) {\n" +
                "            print(\"achou\")\n" +
                "        } else {\n" +
                "            print(x)\n"
                + "        }\n" +
                "    }\n" +
                "    while (total > 0) {\n" +
                "        total = total - 1\n" +
                "    }\n" +
                "    println(total)\n" +
                "}\n";
        CompilationUnitNode k = parseUnit(kof, "Main.kf", LanguageProfile.KOF);
        CompilationUnitNode p = parseUnit(ptkf, "Main.ptkf", LanguageProfile.PORTUKOF);
        assertEquals(shape(k), shape(p),
                "normalized PortuKof AST must be structurally identical to the Kof AST");
    }

    @Test
    void portuKofRunsIdenticallyOnTheScriptTarget() throws Exception {
        String ptkf = "principal() {\n" +
                "    var nome = \"Mel\"\n" +
                "    se (nome == \"Mel\") {\n" +
                "        escrevaln(\"Ola, \" + nome)\n" +
                "    } senao {\n" +
                "        escrevaln(\"estrangeiro\")\n" +
                "    }\n" +
                "}\n";
        String kof = "main() {\n" +
                "    var nome = \"Mel\"\n" +
                "    if (nome == \"Mel\") {\n" +
                "        println(\"Ola, \" + nome)\n" +
                "    } else {\n" +
                "        println(\"estrangeiro\")\n" +
                "    }\n" +
                "}\n";
        Path root = Files.createTempDirectory("ptkf-parity-");
        Path pt = root.resolve("Main.ptkf");
        Path kf = root.resolve("Main.kf");
        Files.writeString(pt, ptkf);
        Files.writeString(kf, kof);
        KofInterpreter.Result rp = new CompilerDriver().interpret(List.of(pt), root, new String[0]);
        KofInterpreter.Result rk = new CompilerDriver().interpret(List.of(kf), root, new String[0]);
        assertEquals("Ola, Mel\n", rp.stdout());
        assertEquals(rk.stdout(), rp.stdout(), "ptkf must print exactly what kof prints");
        assertEquals(rk.exitCode(), rp.exitCode());
    }

    @Test
    void reservedFunctionWordIsRefusedOnBothSurfaces() {
        DiagnosticCollector diags = new DiagnosticCollector();
        String ptkf = "funcao soma(Int a) {\n    retorna a\n}\n";
        Lexer lexer = new Lexer(ptkf, "Main.ptkf", diags, LanguageProfile.PORTUKOF);
        new Parser(lexer.tokenize(), diags, "Main.ptkf", LanguageProfile.PORTUKOF).parse();
        assertTrue(diags.hasErrors(), "`funcao` is reserved — never a function keyword");

        DiagnosticCollector diagsEn = new DiagnosticCollector();
        String kof = "func soma(Int a) {\n    return a\n}\n";
        Lexer lx = new Lexer(kof, "Main.kf", diagsEn, LanguageProfile.KOF);
        new Parser(lx.tokenize(), diagsEn, "Main.kf", LanguageProfile.KOF).parse();
        assertTrue(diagsEn.hasErrors(), "`func` stays reserved on the canonical surface");
    }

    @Test
    void diagnosticsMatchAcrossSurfaces() throws Exception {
        String ptkf = "principal() {\n    escreva(idadeNaoDeclarada)\n}\n";
        String kof = "main() {\n    print(idadeNaoDeclarada)\n}\n";
        Path root = Files.createTempDirectory("ptkf-diag-");
        Path f = root.resolve("Main.ptkf");
        Files.writeString(f, ptkf);
        Path fk = root.resolve("Main.kf");
        Files.writeString(fk, kof);
        CompilationResult rp = new CompilerDriver().compileSources(List.of(f),
                Files.createTempDirectory("ptkf-diag-out-p-"), Target.SCRIPT, root);
        CompilationResult rk = new CompilerDriver().compileSources(List.of(fk),
                Files.createTempDirectory("ptkf-diag-out-k-"), Target.SCRIPT, root);
        assertFalse(rp.diagnostics().getDiagnostics().isEmpty(), "undefined name must diagnose");
        assertFalse(rk.diagnostics().getDiagnostics().isEmpty());
        assertEquals(rk.diagnostics().getDiagnostics().get(0).code(),
                rp.diagnostics().getDiagnostics().get(0).code(),
                "diagnostic CODE must be identical across surfaces");
    }

    @Test
    void stringContentsAreNeverTranslated() {
        CompilationUnitNode u = parseUnit("principal() {\n    escreva(\"se while return\")\n}\n",
                "Main.ptkf", LanguageProfile.PORTUKOF);
        assertTrue(u.toString().contains("se while return"), "string bytes survive");
    }

    @Test
    void userIdentifiersAreNeverTranslated() {
        CompilationUnitNode u = parseUnit(
                "Int se1(Int escreva) {\n    retorna escreva\n}\n" +
                "principal() {\n    var teste = se1(1)\n    escrevaln(teste)\n}\n",
                "Main.ptkf", LanguageProfile.PORTUKOF);
        String s = u.toString();
        assertTrue(s.contains("escreva"), "user parameter named `escreva` stays `escreva`");
        assertTrue(s.contains("se1"), "user function named `se1` stays `se1`");
    }

    @Test
    void mainAliasRunsAsEntryAndCanonicalNameIsMain() {
        CompilationUnitNode u = parseUnit("principal() {\n}\n", "Main.ptkf",
                LanguageProfile.PORTUKOF);
        assertEquals("main", ((FunctionDeclarationNode) u.declarations().get(0)).name());
    }

    @Test
    void portuKofCompilesToJvmLikeKof() throws Exception {
        String ptkf = "principal() {\n    var v = listaDe(3, 1, 2)\n    escrevaln(v)\n}\n";
        Path root = Files.createTempDirectory("ptkf-jvm-");
        Path f = root.resolve("Main.ptkf");
        Files.writeString(f, ptkf);
        CompilationResult r = new CompilerDriver().compileSources(List.of(f),
                Files.createTempDirectory("ptkf-jvm-out-"), Target.JVM, root);
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .noneMatch(d -> d.message().contains("SEM")),
                "stdlib alias surface must resolve on JVM: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void speechSugarNormalizesToTheSameCanonicalBuiltins() {
        CompilationUnitNode sugar = parseUnit(
                "principal() {\n    diga(\"a\")\n    diz(\"b\")\n}\n", "Main.ptkf",
                LanguageProfile.PORTUKOF);
        CompilationUnitNode primary = parseUnit(
                "principal() {\n    escrevaln(\"a\")\n    escreva(\"b\")\n}\n", "Main.ptkf",
                LanguageProfile.PORTUKOF);
        assertEquals(shape(primary), shape(sugar),
                "D-PORTUKOF-SUGAR: diga/diz normalize to println/print — same AST, not a new call");
    }

    @Test
    void speechSugarRunsIdenticallyOnTheScriptTarget() throws Exception {
        String ptkf = "principal() {\n" +
                "    diga(\"a\")\n" +
                "    diz(\"b\")\n" +
                "    diz(\"c\")\n" +
                "    diga(\"\")\n" +
                "}\n";
        Path root = Files.createTempDirectory("ptkf-sugar-");
        Path pt = root.resolve("Main.ptkf");
        Files.writeString(pt, ptkf);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(pt), root, new String[0]);
        assertEquals(0, r.exitCode(), "sugar must run: " + r.stderr());
        assertEquals("a\nbc\n", r.stdout(),
                "diga=println (line), diz=print (no line) — measured");
    }

    @Test
    void sugarDoesNotSpoilPrimaryRendering() {
        String rendered = dev.kof.compiler.lang.SurfaceNames.symbol(
                LanguageProfile.PORTUKOF, "println");
        assertEquals("escrevaln", rendered,
                "sugar extends accepted spellings; the PRIMARY surface name stays escrevaln");
        assertEquals("print", dev.kof.compiler.lang.SurfaceNames
                .builtin(LanguageProfile.KOF, "print"));
    }

}
