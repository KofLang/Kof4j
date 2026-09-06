package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lambdas (REFACTOR-500, FASE 7): detecção e parsing de parâmetros
 * ({@code (x) ->}, {@code { x: Int -> }}, trailing lambda). Move o cursor
 * do {@link ParseContext} — nunca duplica estado.
 */
class LambdaParser {

    static boolean looksLikeLambdaParams(ParseContext ctx) {
        // `(x: T) -> ...` — IDENTIFIER COLON no início é inequívoco: uma
        // expressão entre parênteses não começa com 'id :'. Cobre também
        // `(s: (Int) -> Int) -> ...` (o ARROW do tipo de função seria
        // confundido com o delimitador da lambda no scan abaixo).
        if (ctx.checkNext(TokenType.IDENTIFIER)
                && ctx.pos + 2 < ctx.tokens.size()
                && ctx.tokens.get(ctx.pos + 2).type() == TokenType.COLON) {
            return true;
        }
        int i = ctx.pos + 1;
        int depth = 0;
        while (i < ctx.tokens.size()) {
            TokenType t = ctx.tokens.get(i).type();
            if (t == TokenType.LPAREN) {
                depth++;
            } else if (t == TokenType.RPAREN) {
                if (depth == 0) {
                    return i + 1 < ctx.tokens.size() && ctx.tokens.get(i + 1).type() == TokenType.ARROW;
                }
                depth--;
            } else if (t == TokenType.ARROW) {
                // ARROW no depth 0 é o delimitador da lambda; dentro de parens
                // aninhados (ex.: `(x) -> ((y) -> ...)`) faz parte de outro
                // lambda — ignorar.
                if (depth == 0) return false;
            }
            i++;
        }
        return false;
    }

    /**
     * Trailing-lambda block with explicit parameters:
     * {@code method { s -> ... }}, {@code method { s: Int -> ... }} or
     * {@code method { a, b -> ... }}. A plain block ({@code method { ... }})
     * returns false — statements start with keywords/identifiers, and an
     * identifier followed by `->` or `,` or `: type ->` is a parameter list.
     */
    static boolean looksLikeLambdaBlockParams(ParseContext ctx) {
        int i = ctx.pos + 1; // first token inside the block
        if (i >= ctx.tokens.size()) return false;
        int look = 0;
        // scan a small window: ident (: type)? (, ident (: type)?)* ->
        while (look < 8 && i + look < ctx.tokens.size()) {
            TokenType t = ctx.tokens.get(i + look).type();
            if (t == TokenType.ARROW) {
                return look > 0;
            }
            if (t == TokenType.IDENTIFIER || TypeParser.isPrimitiveTypeToken(t)) {
                look++;
                // optional ": Type"
                if (i + look < ctx.tokens.size() && ctx.tokens.get(i + look).type() == TokenType.COLON) {
                    look++;
                    // type reference: identifier or primitive type keyword
                    if (i + look < ctx.tokens.size()
                            && (ctx.tokens.get(i + look).type() == TokenType.IDENTIFIER
                                    || TypeParser.isPrimitiveTypeToken(ctx.tokens.get(i + look).type()))) {
                        look++;
                    } else {
                        return false;
                    }
                }
                if (i + look < ctx.tokens.size() && ctx.tokens.get(i + look).type() == TokenType.COMMA) {
                    look++;
                    continue;
                }
                continue;
            }
            return false;
        }
        return false;
    }

    /** Parses {@code { s: Int -> ... }} parameter list (the LBRACE is current). */
    static List<FormalParameterNode> parseLambdaBlockParams(ParseContext ctx) {
        ctx.expect(TokenType.LBRACE, "Expected '{'", "PARSE013");
        List<FormalParameterNode> params = new ArrayList<>();
        params.add(LambdaParser.parseLambdaParameter(ctx));
        while (ctx.check(TokenType.COMMA)) {
            ctx.advance();
            params.add(LambdaParser.parseLambdaParameter(ctx));
        }
        return params;
    }

    static List<FormalParameterNode> parseLambdaParams(ParseContext ctx) {
        List<FormalParameterNode> params = new ArrayList<>();
        ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE011");
        if (!ctx.check(TokenType.RPAREN)) {
            params.add(LambdaParser.parseLambdaParameter(ctx));
            while (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                params.add(LambdaParser.parseLambdaParameter(ctx));
            }
        }
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE012");
        return params;
    }

    static FormalParameterNode parseLambdaParameter(ParseContext ctx) {
        SourcePosition p = ctx.pos();
        String name = ctx.expectId("Expected parameter name", "PARSE010");
        String type = "Object";
        if (ctx.check(TokenType.COLON)) {
            ctx.advance();
            type = TypeParser.parseTypeRef(ctx);
        }
        return new FormalParameterNode(p, List.of(), type, name);
    }

    static List<StatementNode> parseLambdaBody(ParseContext ctx) {
        if (ctx.check(TokenType.LBRACE)) {
            return StatementParser.parseBlock(ctx);
        }
        ExpressionNode expr = ExpressionParser.parseExpression(ctx);
        if (ctx.check(TokenType.SEMICOLON)) ctx.advance();
        return List.of(new ReturnStmt(ctx.pos(), expr));
    }
}
