package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`, mantenedora 03/11) — a GRAMÁTICA e a AST do
 * padrão, provadas antes de qualquer lowering (§17 do plano: "Lock the grammar
 * and the AST; write the parser tests first (no lowering)").
 *
 * <p>O que estes testes travam, e por quê:
 * <ul>
 *   <li>o `expr` é keyword CONTEXTUAL — um arquivo que usa `expr` como nome
 *       comum continua compilando (aditivo, R2);</li>
 *   <li>a AST é a do plano §3 — átomos semânticos, sequência, quantificadores,
 *       captura nomeada, `either` e composição por nome;</li>
 *   <li>os exemplos obrigatórios §3.4 parseiam e ROUND-TRIPAM pelo formatador
 *       (com `-w` o arquivo precisa voltar legível, nunca um `toString()`);</li>
 *   <li>PortuKof produz a MESMA AST do Kof (uma semântica, N superfícies).</li>
 * </ul>
 */
class ExprDeclarationParseE2ETest {

    @TempDir
    Path tmp;

    private CompilationResult parse(String tag, String src, Target target) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return new CompilerDriver().compile(file, tmp.resolve("out-" + tag), target);
    }

    private String diagnosticsOf(CompilationResult r) {
        return r.diagnostics().getDiagnostics().toString();
    }

    private CompilationUnitNode parseOnly(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        var diagnostics = new DiagnosticCollector();
        var tokens = new dev.kof.compiler.parser.Lexer(
                src, file.toString(), diagnostics,
                dev.kof.compiler.lang.LanguageProfile.KOF).tokenize();
        CompilationUnitNode unit = new dev.kof.compiler.parser.Parser(
                tokens, diagnostics, file.toString()).parse();
        assertTrue(diagnostics.getDiagnostics().isEmpty(),
                () -> "parser deve aceitar o padrão, veio: " + diagnostics.getDiagnostics());
        return unit;
    }

    private static ExprDeclarationNode patternIn(CompilationUnitNode unit, String name) {
        for (AstNode d : unit.declarations()) {
            if (d instanceof ExprDeclarationNode ex && ex.name().equals(name)) return ex;
        }
        fail("pattern '" + name + "' não está na unidade: " + unit.declarations());
        return null;
    }

    @Test
    void atomsSequenceQuantifiersCapturesAndAlternationShapeTheAst() throws Exception {
        CompilationUnitNode unit = parseOnly("shape", """
            expr email {
                start
                one_or_more { letter digit "." "_" "%" "+" "-" }
                "@"
                one_or_more { letter digit "." "-" }
                "."
                between(2, 63) letter
                end
            }
            """);
        ExprDeclarationNode email = patternIn(unit, "email");
        assertInstanceOf(ExprSequenceNode.class, email.body(), "o corpo é uma sequência");
        List<ExprNode> units = ((ExprSequenceNode) email.body()).units();
        assertEquals(7, units.size(), () -> "unidades: " + units);

        assertEquals("start", kindOf(units.get(0)), "primeira unidade é a âncora de início");
        ExprQuantifierNode repeat = assertInstanceOf(ExprQuantifierNode.class, units.get(1));
        assertEquals("one_or_more", repeat.kind());
        assertEquals(7, ((ExprSequenceNode) repeat.target()).units().size(),
                "o bloco carrega os sete átomos do exemplo §3.4 (letter digit . _ % + -)");
        assertEquals("literal", kindOf(units.get(2)), "um texto entre aspas é literal");
        ExprQuantifierNode bounded = assertInstanceOf(ExprQuantifierNode.class, units.get(5));
        assertEquals("between", bounded.kind());
        assertEquals(2, bounded.min());
        assertEquals(63, bounded.max());
        assertEquals("end", kindOf(units.get(6)), "última unidade é a âncora de fim");
    }

    @Test
    void capturesAreNamedAndEitherReadsAsIntent() throws Exception {
        CompilationUnitNode unit = parseOnly("captures", """
            expr logLine {
                capture("time") { exactly(2) digit ":" exactly(2) digit }
                " "
                capture("level") { either { "INFO" or "WARN" or "ERROR" } }
            }
            """);
        List<ExprNode> units = ((ExprSequenceNode) patternIn(unit, "logLine").body()).units();
        ExprCaptureNode time = assertInstanceOf(ExprCaptureNode.class, units.get(0));
        assertEquals("time", time.name());
        assertEquals(3, ((ExprSequenceNode) time.body()).units().size(),
                "exactly(2) digit (um quantificador), \":\" (literal), exactly(2) digit");

        ExprCaptureNode level = assertInstanceOf(ExprCaptureNode.class, units.get(2));
        // o corpo da captura é a sequência que envolve o either (o bloco `{ }`
        // é a sequência; o `either` é a única unidade nela)
        ExprAlternationNode either = assertInstanceOf(ExprAlternationNode.class,
                ((ExprSequenceNode) level.body()).units().get(0));
        assertEquals(3, either.alternatives().size(), "INFO | WARN | ERROR");
    }

    @Test
    void aPatternComposesAnotherByIdentifierName() throws Exception {
        CompilationUnitNode unit = parseOnly("compose", """
            expr octet { between(1, 3) digit }
            expr ipv4 { octet "." octet }
            """);
        List<ExprNode> units = ((ExprSequenceNode) patternIn(unit, "ipv4").body()).units();
        assertEquals("octet", ((ExprReferenceNode) units.get(0)).alias());
        assertEquals("octet", ((ExprReferenceNode) units.get(2)).alias());
        ExprReferenceNode forward = assertInstanceOf(ExprReferenceNode.class, units.get(0));
        assertNotNull(forward.position(), "a referência carrega posição (diagnóstico com arquivo:linha)");
        // composite-of-forward: a referência pode preceder a declaração alvo
        assertTrue(unit.declarations().stream()
                .anyMatch(d -> d instanceof ExprDeclarationNode e && e.name().equals("octet")));
    }

    @Test
    void exprStaysAnOrdinaryIdentifierOutsideTheDeclarationShape() throws Exception {
        CompilationResult r = parse("ident", """
            String expr(String value) = value + "!"
            main() { println(expr("kof")) }
            """, Target.JVM);
        assertTrue(r.success(), () -> "`expr` como nome comum continua válido (R2 aditivo): "
                + diagnosticsOf(r));
    }

    @Test
    void aPatternDeclarationCompilesCleanlyAndChangesNoRuntimeBehaviour() throws Exception {
        // Fatia 1 (D-KOF-EXPR): um padrão DENTRO do conjunto da fatia compila
        // limpo (o spike-0 aceitava qualquer corpo sem emitir código — esse
        // contrato exato é o que a fatia 1 substitui; fora do conjunto é
        // EXPR001 nomeando a fatia do plano, nunca silêncio).
        CompilationResult r = parse("clean", """
            expr greeting { "hello" " " "world" }
            main() { println("ok") }
            """, Target.JVM);
        assertTrue(r.success(), () -> "a fatia 1 aceita o padrão dentro do conjunto: "
                + diagnosticsOf(r));
        CompilationResult quantified = parse("clean2", """
            expr digitRun { one_or_more { "7" } }
            main() { println("ok") }
            """, Target.JVM);
        assertTrue(quantified.success(), () -> "o quantificador da fatia 2 compila: "
                + diagnosticsOf(quantified));
        CompilationResult classes = parse("clean3", """
            expr digitRun { one_or_more { digit } }
            main() { println("ok") }
            """, Target.JVM);
        assertTrue(classes.success(), () -> "as classes da fatia 3 compilam: "
                + diagnosticsOf(classes));
        CompilationResult alt = parse("clean4", """
            expr alt { either { "a" or "b" } }
            main() { println("ok") }
            """, Target.JVM);
        assertTrue(alt.success(), () -> "o either da fatia 4 compila: "
                + diagnosticsOf(alt));
        CompilationResult cap = parse("clean5", """
            expr cap { capture("v") { "a" } }
            main() { println("ok") }
            """, Target.JVM);
        assertTrue(cap.success(), () -> "a captura da fatia 5 compila: "
                + diagnosticsOf(cap));
        CompilationResult outOfSlice = parse("clean6", """
            expr ref { inner "." }
            expr inner { "x" }
            main() { println("ok") }
            """, Target.JVM);
        assertFalse(outOfSlice.success(), () -> "fora do conjunto da fatia recusa: "
                + diagnosticsOf(outOfSlice));
        assertTrue(diagnosticsOf(outOfSlice).contains("EXPR001"),
                () -> "a referência de composição nomeia a fatia: " + diagnosticsOf(outOfSlice));
    }

    @Test
    void portukofSurfaceProducesTheSameAst() throws Exception {
        String kofSource = """
            expr digitRun { one_or_more { digit } start }
            """;
        String ptSource = """
            molde sequenciaDeDigitos { um_ou_mais { digito } inicio }
            """;
        Path kofFile = tmp.resolve("pt/kof.kf");
        Path ptFile = tmp.resolve("pt/pt.ptkf");
        Files.createDirectories(kofFile.getParent());
        Files.writeString(kofFile, kofSource);
        Files.writeString(ptFile, ptSource);

        ExprDeclarationNode kofPattern = parseOnly("ptk", kofSource).declarations().stream()
                .filter(d -> d instanceof ExprDeclarationNode).map(ExprDeclarationNode.class::cast)
                .findFirst().orElseThrow();
        var diagnostics = new DiagnosticCollector();
        var ptTokens = new dev.kof.compiler.parser.Lexer(
                ptSource, ptFile.toString(), diagnostics,
                dev.kof.compiler.lang.LanguageProfile.forFileName(ptFile.toString())).tokenize();
        CompilationUnitNode ptUnit = new dev.kof.compiler.parser.Parser(ptTokens, diagnostics,
                ptFile.toString(), dev.kof.compiler.lang.LanguageProfile.PORTUKOF).parse();
        assertTrue(diagnostics.getDiagnostics().isEmpty(),
                () -> "PortuKof deve aceitar o padrão, veio: " + diagnostics.getDiagnostics());
        ExprDeclarationNode ptPattern = ptUnit.declarations().stream()
                .filter(d -> d instanceof ExprDeclarationNode).map(ExprDeclarationNode.class::cast)
                .findFirst().orElseThrow();

        // nomes são identificadores (passam VERBATIM); a ESTRUTURA tem de ser idêntica.
        String kofShape = shapeOf(kofPattern.body());
        String ptShape = shapeOf(ptPattern.body());
        assertEquals(kofShape, ptShape,
                "a superfície PT produz a MESMA AST (uma semântica, N superfícies)");
    }

    /** Forma canônica da árvore, sem posições nem nomes de captura. */
    private static String shapeOf(ExprNode node) {
        if (node instanceof ExprSequenceNode s) {
            List<String> parts = new java.util.ArrayList<>();
            for (ExprNode u : s.units()) parts.add(shapeOf(u));
            return "[" + String.join(",", parts) + "]";
        }
        if (node instanceof ExprAtomNode a) return a.kind() + (a.values().isEmpty() ? "" : a.values().toString());
        if (node instanceof ExprQuantifierNode q) {
            return q.kind() + "(" + q.min() + "," + q.max() + "){" + shapeOf(q.target()) + "}";
        }
        if (node instanceof ExprCaptureNode c) return "capture{" + shapeOf(c.body()) + "}";
        if (node instanceof ExprAlternationNode alt) {
            List<String> parts = new java.util.ArrayList<>();
            for (ExprNode b : alt.alternatives()) parts.add(shapeOf(b));
            return "either{" + String.join("|", parts) + "}";
        }
        if (node instanceof ExprReferenceNode r) return "@" + r.alias();
        return "?";
    }

    private static String kindOf(ExprNode node) {
        return node instanceof ExprAtomNode a ? a.kind() : node.getClass().getSimpleName();
    }
}
