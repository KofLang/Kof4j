package dev.kof.compiler;

import dev.kof.compiler.lang.LanguageProfile;
import dev.kof.compiler.lang.PortuKofParity;
import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-PORTUKOF F7.3 (08/10) — formatter AST DIRIGIDO POR PERFIL.
 *
 * <p>Trava o contrato do `KofFormatter` surface-aware: o source `.ptkf` é
 * parseado PELO MESMO lexer/parser de perfil (NUNCA transpile PT→EN prévio) e
 * a AST canônica é reimpressa NA SUPERFÍCIE DO PERFIL pela ponte única
 * `SurfaceNames` (keywords/tipos/contextuais) com slots de nome VERBATIM
 * (identificadores, métodos receiver-aware, builtins de chamada nua, imports).
 * Kof permanece byte-idêntico ao comportamento histórico.
 *
 * <p>Propriedades travadas (uma por teste, sem inflar): caminho AST primário;
 * keywords na superfície; sem vazamento EN em PT e sem vazamento PT em Kof;
 * nomes de usuário/builtins/métodos receiver-aware intactos; strings e
 * comentários intactos; imports intactos; tipos nos slots estruturais;
 * round-trip AST-equivalente (pós-`PortuKofParity.normalize`); idempotência;
 * contrato de fallback por parse-error; e os exemplos oficiais.
 */
class PortuKofFormatterProfileE2ETest {

    private static String shape(CompilationUnitNode unit) {
        return unit.toString().replaceAll("T\\.\\w+", "FILE").replaceAll("\\d+", "#");
    }

    private static CompilationUnitNode parseUnit(String src, String fileName) {
        LanguageProfile profile = LanguageProfile.forFileName(fileName);
        DiagnosticCollector diags = new DiagnosticCollector();
        Lexer lexer = new Lexer(src, fileName, diags, profile);
        List<Token> tokens = lexer.tokenize();
        assertFalse(diags.hasErrors(), "lex " + fileName + ": " + diags.getDiagnostics());
        Parser parser = new Parser(tokens, diags, fileName, profile);
        CompilationUnitNode unit = parser.parse();
        assertFalse(diags.hasErrors(), "parse " + fileName + ": " + diags.getDiagnostics());
        return PortuKofParity.normalize(profile, unit);
    }

    private static final String KITCHEN_PT = """
            pacote meu.app
            importa arquivo.csv
            importa minha.*
            // comentário de topo — não traduzir
            principal() {
                // comentário interno — ãéõç
                var x = 1
                se (x > 0) { retorna x + 1 }
                senao { var t = "João" }
                escolha (2) { caso 1: sair
                    padrao: segue }
                tenta { var y = 1 } pegar (Texto erro) { segue } porFim { }
                enquanto (falso) { sair }
                var k = 0
                faca { k = k + 1 } enquanto (k < 2)
                para (var i em listaDe(1, 2)) { escreva(i) }
                afirme(x == 1, "mensagem")
                lanca "boom"
                var b = verdadeiro
                var n = nulo
                var s = x instanciaDe Int
                var d = t como texto
            }

            publico classe MinhaClasse estende Base implementa ifaceMinha {
                construtor(Inteiro n) { }
                publico texto minhaFuncao(Int x) { retorna "x" }
            }

            registro MeuRegistro(Int x, texto y)
            """;

    // 1 — o caminho AST é o PRIMÁRIO: format(PT) não é mais null (era o
    //     contrato F7.1) e NORMALIZA (token-fallback não normalizaria spacing
    //     estrutural como `senao {` → bloco multilinha).
    @Test
    void astPathIsPrimaryForPortukof() {
        String messy = "principal() { se (verdadeiro) { escreva(1) } senao {retorna} }\n";
        String out = KofFormatter.format(messy, "P.ptkf");
        assertNotNull(out, "F7.1 devolvia null; F7.3 renderiza pela AST");
        assertTrue(out.contains("senao {"), "corpo else reimpresso pela AST: " + out);
        assertTrue(out.contains("escreva(1)"), "builtin de chamada nua VERBATIM: " + out);
    }

    // 2 — keywords estruturais saem na superfície do catálogo (nunca EN).
    @Test
    void structuralKeywordsRenderSurface() {
        String out = KofFormatter.format(
                "classe C {\n    texto m() { retorna \"x\" }\n    texto n() { escreva(1) retorna \"y\" }\n}\nregistro P(Int x)\nentidade U { id: Int gerado }\n", "T.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("classe C"), out);
        assertTrue(out.contains("retorna"), "statement de retorno multi-linha permanece `retorna`: " + out);
        assertTrue(out.contains("registro P"), out);
        assertTrue(out.contains("entidade U"), out);
        assertTrue(out.contains("gerado"), out);
    }

    // 3 — nenhum leak de keyword EN na saída PT (as keywords estruturais do
    //     printer histórico).
    @Test
    void noEnglishKeywordLeakInPortukof() {
        String out = KofFormatter.format(KITCHEN_PT, "T.ptkf");
        assertNotNull(out, "kitchen-sink PT deve formatar");
        for (String leak : List.of("class ", "record ", "entity ", "extends ", "implements ",
                "constructor(", "return ", "throw ", "else ", "while ", "switch ", "case ",
                "default:", "try ", "catch ", "finally ", "instanceof", "new ", "spawn ",
                "await ", "assert(", "break", "continue")) {
            assertFalse(out.contains(leak), "leak EN '" + leak + "' em:\n" + out);
        }
    }

    // 4 — identificadores de usuário NUNCA são traduzidos (regra-ouro).
    @Test
    void userIdentifiersStayVerbatim() {
        String out = KofFormatter.format(
                "principal() { var minhaVariavel = 1 escrevaln(minhaVariavel) }\n"
              + "classe MinhaClasse { texto minhaFuncao() { retorna \"oi\" } }\n", "T.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("minhaVariavel"), out);
        assertTrue(out.contains("MinhaClasse"), out);
        assertTrue(out.contains("minhaFuncao"), out);
    }

    // 5 — builtins de chamada nua chegam IDENTIFIER verbatim no AST (medido
    //     U1: `escreva`/`escrevaln`/`listaDe`) e o printer NÃO os canoniciza.
    @Test
    void builtinCallNamesStaySurface() {
        String out = KofFormatter.format(
                "principal() { escrevaln(\"a\") var l = listaDe(1) var m = mapaDe(\"a\", 1) leia() }\n", "T.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("escrevaln"), out);
        assertTrue(out.contains("listaDe"), out);
        assertTrue(out.contains("mapaDe"), out);
        assertTrue(out.contains("leia()"), out);
        assertFalse(out.contains("println"), out);
        assertFalse(out.contains("listOf"), out);
    }

    // 6 — strings nunca são traduzidas nem recontadas: conteúdo exato.
    @Test
    void stringsAreUntouched() {
        String out = KofFormatter.format("principal() { escreva(\"João \\\"citação\\\" — \\t\") }\n", "T.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("\"João \\\"citação\\\" — \\t\""), "string byte-a-byte: " + out);
    }

    // 7 — comentários: o mecanismo `KofFormatterComments` (texto-fonte,
    //     independente de perfil) é reusado; conteúdo e ordem preservados.
    @Test
    void commentsSurvive() {
        String src = "// um — ãé\nprincipal() {\n    // dois\n    var x = 1\n}\n";
        String out = KofFormatter.format(src, "T.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("// um — ãé"), out);
        assertTrue(out.contains("// dois"), out);
        assertTrue(out.indexOf("um —") < out.indexOf("dois"), "ordem preservada: " + out);
        String block = "principal() {\n    /* nota do usuário */\n    var x = 1\n}\n";
        String outB = KofFormatter.format(block, "T.ptkf");
        assertNotNull(outB);
        assertTrue(outB.contains("/* nota do usuário */"), "bloco preservado: " + outB);
    }

    // 8 — imports: a grafia do source passa VERBATIM pelo slot de nome
    //     (formatter não resolve import — responsabilidade do CompilerImports).
    @Test
    void importsKeepSurfaceSpelling() {
        String out = KofFormatter.format("importa arquivo.csv\nimporta *\nimporta util.Mat\nprincipal() { }\n", "T.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("importa arquivo.csv"), out);
        assertTrue(out.contains("importa util.Mat"), out);
        assertTrue(out.contains("importa *"), out);
        assertFalse(out.contains("import file.csv"), "nunca canoniciza o import: " + out);
    }

    // 9 — slots de tipo: o lexer canoniciza (medido: `texto`→string), o
    //     printer devolve pela ponte (string→texto); tipos de usuário passam
    //     cru; nullabilidade e generics preservados.
    @Test
    void typeSlotsRenderSurface() {
        String out = KofFormatter.format(
                "texto f(inteiro x) { retorna \"a\" }\nclasse C { texto m(x: Inteiro) { retorna \"b\" } }\nprincipal() { var l = listaDe(1) }\n", "T.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("texto f"), out);
        assertTrue(out.contains("inteiro x") || out.contains("x: Inteiro"), out);
        assertTrue(out.contains("classe C"), out);
    }

    // 10 — método receiver-aware: `tamanho` em String e em List são o MESMO
    //      texto-fonte no AST (medido U1/U3) e o printer nunca canoniciza o
    //      slot de nome — não precisa saber o tipo do receiver.
    @Test
    void receiverMethodSpellingsNeverCanonicalized() {
        String src = """
                principal() {
                    val nome = "Kof"
                    val xs = listaDe(3, 1, 2)
                    escrevaln(nome.tamanho())
                    escrevaln(xs.tamanho())
                    se (xs.contem(2)) { escrevaln("tem dois") }
                }
                """;
        String out = KofFormatter.format(src, "T.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("nome.tamanho()"), out);
        assertTrue(out.contains("xs.tamanho()"), out);
        assertTrue(out.contains("xs.contem(2)"), out);
        assertFalse(out.contains(".length"), "nunca vaza canônico EN: " + out);
        assertFalse(out.contains(".size"), "nunca vaza canônico EN: " + out);
        assertFalse(out.contains(".contains"), out);
    }

    // 11 — round-trip SEMÂNTICO: AST(parse(format(PT))) == AST(parse(PT))
    //      após a normalização canônica (a mesma régua da paridade U1).
    @Test
    void roundTripAstEquivalent() {
        var before = shape(parseUnit(KITCHEN_PT, "T.ptkf"));
        String formatted = KofFormatter.format(KITCHEN_PT, "T.ptkf");
        assertNotNull(formatted);
        var after = shape(parseUnit(formatted, "T.ptkf"));
        assertEquals(before, after, "formatter não pode alterar a AST");
    }

    // 12 — idempotência PT e Kof: format(format(x)) == format(x).
    @Test
    void idempotentBothSurfaces() {
        String pt1 = KofFormatter.format(KITCHEN_PT, "T.ptkf");
        assertNotNull(pt1);
        assertEquals(pt1, KofFormatter.format(pt1, "T.ptkf"), "PT idempotente");
        String kf = "package my.app\nimport file.csv\n// top comment\nmain() {\n    var x = 1\n    if (x > 0) { return x + 1 }\n    else { var t = \"Joao\" }\n}\nclass C extends Base {\n    String m(Int x) { return \"x\" }\n}\n";
        String k1 = KofFormatter.format(kf, "T.kf");
        assertNotNull(k1);
        assertEquals(k1, KofFormatter.format(k1, "T.kf"), "Kof idempotente");
    }

    // 13 — Kof zero-regression no MESMO conjunto estrutural largo: o twin EN
    //      do kitchen-sink formata com as keywords históricas do printer.
    @Test
    void kofSurfaceUnchanged() {
        String twin = """
                package my.app
                import file.csv
                import mine.*
                // top comment
                main() {
                    // inner comment
                    var x = 1
                    if (x > 0) { return x + 1 }
                    else { var t = "Joao" }
                    switch (2) { case 1: break
                        default: continue }
                    try { var y = 1 } catch (String error) { continue } finally { }
                    while (false) { break }
                    var k = 0
                    do { k = k + 1 } while (k < 2)
                    for (var i in listOf(1, 2)) { print(i) }
                    assert(x == 1, "msg")
                    throw "boom"
                    var b = true
                    var n = null
                    var s = x instanceof Int
                    var d = t as string
                }

                public class MyClass extends Base implements MyIface {
                    constructor(Int n) { }
                    public String myFunction(Int x) { return "x" }
                }

                record MyRecord(Int x, string y)
                """;
        String out = KofFormatter.format(twin, "T.kf");
        assertNotNull(out, "twin EN deve formatar pelo caminho histórico: " + out);
        assertTrue(out.contains("package my.app"), out);
        assertTrue(out.contains("public class MyClass extends Base implements MyIface"), out);
        assertTrue(out.contains("record MyRecord"), out);
        assertTrue(out.contains("for (var i in listOf(1, 2))"), out);
        assertTrue(out.contains("var s = x instanceof Int"), out);
        assertEquals(out, KofFormatter.format(out, "T.kf"), "Kof idempotente no twin largo");
    }

    @Test
    void kofNeverLeaksPortuguese() {
        String out = KofFormatter.format(
                "class C {\n    String m() { return \"x\" }\n}\nmain() { var l = listOf(1) for (var i in l) { print(i) } }\n", "T.kf");
        assertNotNull(out);
        assertTrue(out.contains("class C"), out);
        assertTrue(out.contains("for (var i in l)"), out);
        for (String leak : List.of("classe", "registro", "retorna", "enquanto", "texto", "listaDe", "escreva")) {
            assertFalse(out.contains(leak), "leak PT em Kof: " + out);
        }
    }

    // 14 — parse-error PT: format devolve null — o contrato EXPLÍCITO de
    //      fallback por tokens (CLI/LSP preservam superfície byte-a-byte).
    @Test
    void malformedSourceFallsBackToNull() {
        assertNull(KofFormatter.format("principal() { se ( ", "T.ptkf"));
        assertNull(KofFormatter.format("classe { { {", "T.ptkf"));
        assertNull(KofFormatter.format("principal() { funcao x() }", "T.ptkf"));
    }

    // 15 — literais booleanos/nulos saem na superfície (verdadeiro/falso/nulo)
    //      e este/super também (medido: IdentifierExpr canônico `this`).
    @Test
    void literalsAndThisRenderSurface() {
        String out = KofFormatter.format(
                "principal() { var b = verdadeiro var f = falso var n = nulo }\nclasse C { texto m() { retorna este.nome } }\n", "T.ptkf");
        assertNotNull(out);
        assertTrue(out.contains("verdadeiro"), out);
        assertTrue(out.contains("falso"), out);
        assertTrue(out.contains("nulo"), out);
        assertTrue(out.contains("este.nome"), out);
        assertFalse(out.contains("true"), out);
        assertFalse(out.contains("this."), out);
    }

    // 16 — exemplos OFICIAIS: os programas canônicos da lane formatam, são
    //      idempotentes e round-trip AST-equivalentes.
    @Test
    void officialExamplesRoundTrip() {
        String ola = """
                // Olá, mundo — PortuKof (superfície pt-BR do Kof)
                principal() {
                    escrevaln("Olá, mundo!")
                }
                """;
        String outOla = KofFormatter.format(ola, "ola_mundo.ptkf");
        assertNotNull(outOla);
        assertEquals(outOla, KofFormatter.format(outOla, "ola_mundo.ptkf"));
        assertTrue(outOla.contains("escrevaln(\"Olá, mundo!\")"), outOla);

        String colecoes = """
                // Coleções e métodos receiver-aware — PortuKof
                principal() {
                    val xs = listaDe(3, 1, 2)
                    val nome = "Kof"
                    escrevaln(nome.tamanho())
                    escrevaln(xs.tamanho())
                    se (xs.contem(2)) {
                        escrevaln("tem dois")
                    }
                }
                """;
        var before = shape(parseUnit(colecoes, "colecoes.ptkf"));
        String outCol = KofFormatter.format(colecoes, "colecoes.ptkf");
        assertNotNull(outCol);
        assertEquals(outCol, KofFormatter.format(outCol, "colecoes.ptkf"));
        assertEquals(before, shape(parseUnit(outCol, "colecoes.ptkf")));

        String imports = """
                importa util.Mat
                principal() {
                    escrevaln(dobro(21))
                }
                """;
        String outImp = KofFormatter.format(imports, "main.ptkf");
        assertNotNull(outImp);
        assertTrue(outImp.contains("importa util.Mat"), outImp);
    }

    // 17 — construtos de declaração cobertos pelo printer na superfície PT:
    //      construtor, enum, entidade, teste, extern, throw-tipado, lambda
    //      (como argumento — forma real do corpus), novo, acesso.
    @Test
    void declarationConstructsRenderSurface() {
        String src = """
                externo "c" kof_extern(Int x): Int
                enumeracao Cor { VERMELHO, AZUL }
                entidade Usuario { id: Int gerado unico
                    nome: texto }
                teste "suite" { }
                classe C {
                    construtor(Int n) lanca Erro { }
                    texto m() { var r = listaDe(1, 2).algum((x) -> x > 1) var v = novo Vetor() retorna r + v[0] }
                }
                """;
        String out = KofFormatter.format(src, "T.ptkf");
        assertNotNull(out, "declarações PT devem formatar pela AST");
        assertTrue(out.contains("externo \"c\""), out);
        assertTrue(out.contains("enumeracao Cor"), out);
        assertTrue(out.contains("entidade Usuario"), out);
        assertTrue(out.contains("teste \"suite\""), out);
        assertTrue(out.contains("construtor"), out);
        assertTrue(out.contains("lanca Erro"), out);
        assertTrue(out.contains("novo Vetor()"), out);
        assertTrue(out.contains(".algum("), "método receiver-aware verbatim: " + out);
        assertTrue(out.contains("-> x > 1"), "lambda preservado: " + out);
    }

    // 18 — regressão do BUG PREEXISTENTE do printer (a forma `;` entre cases de
    //      switch-EXPRESSION não reparseava em NENHUMA superfície — #229: o
    //      parser só aceita cases separados por espaço). Travado p/ as DUAS.
    @Test
    void switchExprRoundTripBothSurfaces() {
        String en = "main() {\n    var n = 0\n    var msg = switch (n) { case 0 -> \"zero\" default -> \"other\" }\n    println(msg)\n}\n";
        String e1 = KofFormatter.format(en, "T.kf");
        assertNotNull(e1);
        assertFalse(e1.contains("\"zero\"; default"), "printer não pode reemitir `;` (#229): " + e1);
        String e2 = KofFormatter.format(e1, "T.kf");
        assertNotNull(e2, "saída EN deve reparsear");
        assertEquals(e1, e2, "EN idempotente com switch-expr");

        String pt = "principal() {\n    var n = 0\n    var msg = escolha (n) { caso 0 -> \"zero\" padrao -> \"other\" }\n    escrevaln(msg)\n}\n";
        String p1 = KofFormatter.format(pt, "T.ptkf");
        assertNotNull(p1);
        assertTrue(p1.contains("escolha (n)"), p1);
        assertTrue(p1.contains("caso 0 -> \"zero\""), p1);
        assertTrue(p1.contains("padrao -> \"other\""), p1);
        assertFalse(p1.contains("\"zero\"; padrao"), p1);
        String p2 = KofFormatter.format(p1, "T.ptkf");
        assertNotNull(p2, "saída PT deve reparsear");
        assertEquals(p1, p2, "PT idempotente com switch-expr");
    }
}
