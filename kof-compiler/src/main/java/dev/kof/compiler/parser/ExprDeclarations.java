package dev.kof.compiler.parser;

import dev.kof.compiler.AnnotationNode;
import dev.kof.compiler.ExprAlternationNode;
import dev.kof.compiler.ExprAtomNode;
import dev.kof.compiler.ExprCaptureNode;
import dev.kof.compiler.ExprDeclarationNode;
import dev.kof.compiler.ExprNode;
import dev.kof.compiler.ExprQuantifierNode;
import dev.kof.compiler.ExprReferenceNode;
import dev.kof.compiler.ExprSequenceNode;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.Token;
import dev.kof.compiler.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`, mantenedora 03/11) — o parser de padrões.
 *
 * <p>Separado de {@link Parser} pelo mesmo motivo de {@link TypeDeclarations}:
 * o ratchet de 500 linhas. Compartilha o MESMO {@link ParseContext}; nenhum
 * estado próprio. O spike-0 só PRODUZ a AST (§17 do plano "Lock the grammar
 * and the AST; write the parser tests first (no lowering)"): nenhum IR é
 * emitido aqui, e a semântica valida o que o parser aceitou.
 *
 * <p>Gramática (canônica, §3 do plano — Q1 = blocos `{ }`, Q2 = declaração
 * `expr NAME { ... }`):
 * <pre>
 * exprDecl   := 'expr' IDENT '{' sequence '}'
 * sequence   := unit*
 * unit       := quantifier | capture | alternation | atom
 * quantifier := ('optional' | 'zero_or_more' | 'one_or_more') block
 *             | ('exactly' '(' INT ')' | 'between' '(' INT ',' INT ')' ) block
 * capture    := 'capture' '(' STRING ')' block
 * alternation:= 'either' '{' sequence ('or' sequence)+ '}'
 * block      := '{' sequence '}'
 * atom       := 'start' | 'end' | 'any' | 'letter' | 'digit' | 'whitespace'
 *             | 'word' | 'hex' | STRING
 *             | 'one_of' '(' STRING (',' STRING)* ')'
 *             | 'none_of' '(' STRING (',' STRING)* ')'
 *             | IDENT
 * </pre>
 * Todo atom canônico é resolvido pelo {@link ParseContext} (perfil
 * PortuKof → canônico), então `letra`, `digito`, `inicio` produzem a MESMA
 * AST de `letter`, `digit`, `start` — uma semântica, N superfícies.
 */
final class ExprDeclarations {

    /** Atoms que reconhecem sem argumento — o `kind` canônico do nó. */
    static final Set<String> BARE_ATOMS = Set.of(
            "start", "end", "any", "letter", "digit", "whitespace", "word", "hex");

    /** Atoms com lista de literais entre parênteses. */
    static final Set<String> LIST_ATOMS = Set.of("one_of", "none_of", "literal");

    /** Quantificadores sem contagem. */
    static final Set<String> SIMPLE_QUANTIFIERS = Set.of(
            "optional", "zero_or_more", "one_or_more");

    private ExprDeclarations() {}

    static ExprDeclarationNode parse(ParseContext ctx, List<String> mods,
                                     List<AnnotationNode> annos) {
        SourcePosition start = ctx.pos();
        ctx.advance(); // 'expr'
        String name = ctx.expectId("Expected pattern name after 'expr'", "PARSE090");
        ExprNode body;
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            body = parseSequence(ctx, TokenType.RBRACE, "PARSE092");
            ctx.expect(TokenType.RBRACE, "Expected '}' after pattern body", "PARSE092");
        } else {
            ctx.error("Expected '{' after pattern name", "PARSE091");
            body = new ExprSequenceNode(start, List.of());
        }
        return new ExprDeclarationNode(start, name, mods, body, annos);
    }

    /**
     * As unidades até o terminador (sem consumi-lo). {@code code} é o código de
     * erro da unidade malformada — o chamador escolhe o mais específico
     * (`PARSE092` no corpo, `PARSE096` num bloco interno).
     */
    static ExprSequenceNode parseSequence(ParseContext ctx, TokenType terminator, String code) {
        return parseSequence(ctx, terminator, code, false);
    }

    /**
     * @param stopAtOr true dentro de um ramo de {@code either}: a palavra
     *                 {@code or} TERMINA o ramo e é consumida pelo chamador.
     *                 Sem isso o `or` cairia em {@link ExprReferenceNode} e o
     *                 ramo engoliria o separador (árvore errada, sem erro).
     */
    static ExprSequenceNode parseSequence(ParseContext ctx, TokenType terminator, String code,
                                          boolean stopAtOr) {
        SourcePosition start = ctx.pos();
        List<ExprNode> units = new ArrayList<>();
        while (!ctx.check(terminator) && !ctx.atEnd() && !(stopAtOr && isWord(ctx, "or"))) {
            int before = ctx.pos;
            ExprNode unit = parseUnit(ctx, code);
            if (unit != null) units.add(unit);
            if (ctx.pos == before) {
                // Trava de progresso (mesma disciplina do parse de entity): um
                // token que nenhuma regra consome viraria laço infinito.
                ctx.error("Expected a pattern element", code);
                ctx.advance();
            }
        }
        return new ExprSequenceNode(start, List.copyOf(units));
    }

    private static ExprNode parseUnit(ParseContext ctx, String code) {
        if (ctx.check(TokenType.STRING_LITERAL)) {
            SourcePosition p = ctx.pos();
            return new ExprAtomNode(p, "literal", List.of(ctx.advance().value()));
        }
        if (ctx.check(TokenType.IDENTIFIER)) {
            return parseWordUnit(ctx, code);
        }
        // Fatia 4 (D-KOF-EXPR): um GRUPO `{ … }` nu é uma unidade — a
        // sequência agrupada que o quantificador carrega inteira
        // (`exactly(2) { "a" "b" }`). Antes as chaves só abriam como alvo de
        // quantificador/captura; a forma é aditiva sobre a gramática Q1.
        if (ctx.check(TokenType.LBRACE)) {
            return parseBlock(ctx, code);
        }
        return null;
    }

    /** Uma palavra na posição de unidade: quantificador, captura, alternação, atom. */
    private static ExprNode parseWordUnit(ParseContext ctx, String code) {
        ContextualPosition where = contextualPosition(ctx);
        return switch (where) {
            case QUANTIFIER -> parseQuantifier(ctx, code);
            case CAPTURE -> parseCapture(ctx, code);
            case ALTERNATION -> parseAlternation(ctx, code);
            case LIST_ATOM -> parseListAtom(ctx, code);
            case BARE_ATOM -> {
                SourcePosition p = ctx.pos();
                yield new ExprAtomNode(p, ctx.canonicalValue(ctx.advance().value()), List.of());
            }
            case OTHER_WORD -> {
                SourcePosition p = ctx.pos();
                // Um identificador que não é atom é uma REFERÊNCIA por nome a
                // outro `expr` (§5 do plano). A resolução — ou a recusa — é
                // semântica: o parser não sabe quais `expr` existem.
                yield new ExprReferenceNode(p, ctx.advance().value());
            }
        };
    }

    /**
     * O que a palavra na posição de unidade É. A classe é decidida pela palavra
     * CANÔNICA (PortuKof resolve para a mesma classe), e o formatos que exigem
     * pontuação (`(`, `{`) só viram essa classe quando a pontuação vem — assim
     * um identificador comum com o nome de um combinador (`letter = 3`) segue
     * sendo referência, e não se transforma em atom por acidente.
     */
    private enum ContextualPosition { QUANTIFIER, CAPTURE, ALTERNATION, LIST_ATOM, BARE_ATOM, OTHER_WORD }

    private static ContextualPosition contextualPosition(ParseContext ctx) {
        String c = ctx.canonicalValue(ctx.peek().value());
        TokenType next = ctx.peekOffset(1).type();
        if (SIMPLE_QUANTIFIERS.contains(c)) {
            // Fatia 2 (D-KOF-EXPR): além do bloco, os exemplos obrigatórios §3.4
            // do plano soletram o açúcar de UM literal — `optional("+55")` — um
            // quantificador sobre um átomo literal único. Aditivo (R2).
            if (next == TokenType.LBRACE || next == TokenType.LPAREN) {
                return ContextualPosition.QUANTIFIER;
            }
            // Fatia 9 (§3.2 do plano: "um único átomo OU um bloco"): o
            // quantificador sobre o ÁTOMO NU seguinte — `zero_or_more any` no
            // logLine obrigatório. O parseBlock consome o átomo seguinte.
            if (next == TokenType.IDENTIFIER) {
                return ContextualPosition.QUANTIFIER;
            }
            return ContextualPosition.OTHER_WORD;
        }
        if (c.equals("exactly") || c.equals("between")) {
            return next == TokenType.LPAREN ? ContextualPosition.QUANTIFIER : ContextualPosition.OTHER_WORD;
        }
        if (c.equals("capture")) {
            return next == TokenType.LPAREN ? ContextualPosition.CAPTURE : ContextualPosition.OTHER_WORD;
        }
        if (c.equals("either")) {
            return next == TokenType.LBRACE ? ContextualPosition.ALTERNATION : ContextualPosition.OTHER_WORD;
        }
        if (LIST_ATOMS.contains(c)) {
            return next == TokenType.LPAREN ? ContextualPosition.LIST_ATOM : ContextualPosition.OTHER_WORD;
        }
        if (BARE_ATOMS.contains(c)) {
            return ContextualPosition.BARE_ATOM;
        }
        return ContextualPosition.OTHER_WORD;
    }

    private static ExprNode parseQuantifier(ParseContext ctx, String code) {
        SourcePosition p = ctx.pos();
        String kind = ctx.canonicalValue(ctx.advance().value());
        int min = 0;
        int max = -1;
        if (ctx.check(TokenType.LPAREN)) {
            ctx.advance();
            if (SIMPLE_QUANTIFIERS.contains(kind)) {
                // `optional("x")` / `zero_or_more("x")` / `one_or_more("x")` —
                // o açúcar §3.4: UM literal de texto como alvo único.
                ExprNode target = literalSugar(ctx, code);
                return new ExprQuantifierNode(p, kind, min, max, target);
            }
            if (kind.equals("exactly")) {
                min = parseCount(ctx);
                max = min;
            } else {
                min = parseCount(ctx);
                ctx.expect(TokenType.COMMA, "Expected ',' in 'between(min, max)'", "PARSE095");
                max = parseCount(ctx);
            }
            ctx.expect(TokenType.RPAREN, "Expected ')' after quantifier counts", "PARSE095");
        }
        ExprNode target = parseBlock(ctx, "PARSE096");
        return new ExprQuantifierNode(p, kind, min, max, target);
    }

    /** O literal único do açúcar `optional("x")`: um átomo literal embrulhado em sequência. */
    private static ExprNode literalSugar(ParseContext ctx, String code) {
        SourcePosition p = ctx.pos();
        if (!ctx.check(TokenType.STRING_LITERAL)) {
            ctx.error("Expected a text literal inside the quantifier", "PARSE101");
            ctx.advance();
            return new ExprSequenceNode(p, List.of());
        }
        ExprAtomNode atom = new ExprAtomNode(p, "literal", List.of(ctx.advance().value()));
        ctx.expect(TokenType.RPAREN, "Expected ')' after the quantifier literal", "PARSE101");
        return new ExprSequenceNode(p, List.of(atom));
    }

    /** A contagem de um quantificador: Int literal ≥ 0, recusado ANTES do lowering. */
    private static int parseCount(ParseContext ctx) {
        if (ctx.check(TokenType.INT_LITERAL)) {
            Token t = ctx.advance();
            try {
                return Integer.parseInt(t.value());
            } catch (NumberFormatException e) {
                ctx.error("Quantifier count is too large", "PARSE094");
                return 0;
            }
        }
        ctx.error("Expected a whole number (quantifier count)", "PARSE093");
        return 0;
    }

    private static ExprNode parseCapture(ParseContext ctx, String code) {
        SourcePosition p = ctx.pos();
        ctx.advance(); // 'capture'
        ctx.expect(TokenType.LPAREN, "Expected '(' after 'capture'", "PARSE097");
        String name = null;
        if (ctx.check(TokenType.STRING_LITERAL)) {
            name = ctx.advance().value();
        } else {
            ctx.error("Expected a capture name as a text literal", "PARSE097");
        }
        ctx.expect(TokenType.RPAREN, "Expected ')' after capture name", "PARSE097");
        ExprNode body = parseBlock(ctx, "PARSE096");
        return new ExprCaptureNode(p, name == null ? "error" : name, body);
    }

    private static ExprNode parseAlternation(ParseContext ctx, String code) {
        SourcePosition p = ctx.pos();
        ctx.advance(); // 'either'
        List<ExprNode> alternatives = new ArrayList<>();
        ctx.expect(TokenType.LBRACE, "Expected '{' after 'either'", "PARSE098");
        while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
            int before = ctx.pos;
            // O ramo TERMINA no `or` (ou `ou` na superfície PT): o separador é
            // do `either`, não uma referência a um `expr` chamado `or`.
            ExprNode branch = parseSequence(ctx, TokenType.RBRACE, "PARSE098", true);
            alternatives.add(branch);
            if (ctx.check(TokenType.RBRACE)) break;
            if (isWord(ctx, "or")) {
                ctx.advance();
            } else if (ctx.pos == before) {
                ctx.error("Expected 'or' or '}' in 'either'", "PARSE098");
                ctx.advance();
            }
        }
        ctx.expect(TokenType.RBRACE, "Expected '}' after 'either' block", "PARSE098");
        return new ExprAlternationNode(p, List.copyOf(alternatives));
    }

    private static ExprNode parseListAtom(ParseContext ctx, String code) {
        SourcePosition p = ctx.pos();
        String kind = ctx.canonicalValue(ctx.advance().value());
        List<String> values = new ArrayList<>();
        ctx.expect(TokenType.LPAREN, "Expected '(' after '" + kind + "'", "PARSE099");
        while (!ctx.check(TokenType.RPAREN) && !ctx.atEnd()) {
            if (ctx.check(TokenType.STRING_LITERAL)) {
                values.add(ctx.advance().value());
            } else {
                ctx.error("Expected a text literal inside '" + kind + "'", "PARSE099");
                ctx.advance();
                continue;
            }
            if (ctx.check(TokenType.COMMA)) {
                ctx.advance();
            }
        }
        ctx.expect(TokenType.RPAREN, "Expected ')' after '" + kind + "' values", "PARSE099");
        return new ExprAtomNode(p, kind, List.copyOf(values));
    }

    /** `{ sequência }` — o alvo de um quantificador ou o corpo de uma captura. */
    /**
     * O alvo de um quantificador ou o corpo de uma captura. As duas formas do
     * §3 do plano são legais:
     * <ul>
     *   <li>um BLOCO agrupa — {@code one_or_more { letter digit }};</li>
     *   <li>um ATOMO solto é a própria unidade — {@code exactly(2) digit}.
     *       Exigir chaves aqui leria {@code between(2, 63) letter} como erro,
     *       e é exatamente o exemplo obrigatório do §3.4.</li>
     * </ul>
     */
    private static ExprNode parseBlock(ParseContext ctx, String code) {
        SourcePosition p = ctx.pos();
        if (!ctx.check(TokenType.LBRACE)) {
            // Fora do desenho `expr` (fim do corpo do padrão) não é um alvo: o
            // diagnóstico diz o que faltou, nunca inventa uma unidade vazia.
            if (ctx.check(TokenType.RBRACE) || ctx.atEnd()) {
                ctx.error("Expected '{' or one pattern element after the quantifier", code);
                return new ExprSequenceNode(p, List.of());
            }
            ExprNode single = parseUnit(ctx, code);
            if (single == null) {
                ctx.error("Expected '{' or one pattern element after the quantifier", code);
                return new ExprSequenceNode(p, List.of());
            }
            return new ExprSequenceNode(p, List.of(single));
        }
        ctx.advance();
        ExprSequenceNode inner = parseSequence(ctx, TokenType.RBRACE, code);
        ctx.expect(TokenType.RBRACE, "Expected '}' after grouped pattern element", code);
        return inner;
    }

    private static boolean isWord(ParseContext ctx, String canonical) {
        return ctx.check(TokenType.IDENTIFIER) && ctx.canonicalValue(ctx.peek().value()).equals(canonical);
    }
}
