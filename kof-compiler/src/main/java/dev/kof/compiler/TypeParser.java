package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Tipos (REFACTOR-500, FASE 7): referências de tipo, tipos de função,
 * argumentos genéricos, parâmetros formais e throws. Move o cursor
 * do {@link ParseContext} — nunca duplica estado.
 */
class TypeParser {

    /**
     * Consumes the generic type arguments starting at the current LESS token
     * and returns their source text, e.g. "<Int, String>".
     */
    static String consumeGenericTypeArgs(ParseContext ctx) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (!ctx.atEnd()) {
            Token t = ctx.advance();
            sb.append(t.value());
            if (t.type() == TokenType.LESS) depth++;
            else if (t.type() == TokenType.GREATER) {
                depth--;
                if (depth == 0) break;
            }
        }
        return sb.toString();
    }

    static List<String> parseTypeParameters(ParseContext ctx) {
        List<String> typeParams = new ArrayList<>();
        if (ctx.check(TokenType.LESS)) {
            ctx.advance();
            while (!ctx.check(TokenType.GREATER) && !ctx.atEnd()) {
                if (ctx.check(TokenType.IDENTIFIER)) {
                    typeParams.add(ctx.advance().value());
                } else {
                    ctx.advance();
                }
                if (ctx.check(TokenType.COMMA)) ctx.advance();
            }
            ctx.expect(TokenType.GREATER, "Expected '>' after type parameters", "PARSE075");
        }
        return typeParams;
    }

    static boolean isPrimitiveTypeAtNext(ParseContext ctx) {
        if (ctx.pos + 1 >= ctx.tokens.size()) return false;
        Token n = ctx.tokens.get(ctx.pos + 1);
        return switch (n.type()) {
            case INT_TYPE, LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE, BOOL_TYPE, BYTE_TYPE,
                    SHORT_TYPE, CHAR_TYPE, STRING_TYPE -> true;
            default -> false;
        };
    }

    static List<FormalParameterNode> parseFormalParameters(ParseContext ctx) {
        ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE021");
        List<FormalParameterNode> params = new ArrayList<>();
        if (!ctx.check(TokenType.RPAREN)) {
            params.add(TypeParser.parseFormalParameter(ctx));
            while (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                params.add(TypeParser.parseFormalParameter(ctx));
            }
        }
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE022");
        return params;
    }

    static FormalParameterNode parseFormalParameter(ParseContext ctx) {
        List<AnnotationNode> annos = AnnotationParser.parseAnnotations(ctx);
        List<String> mods = Parser.parseModifiers(ctx);
        if (ctx.check(TokenType.IDENTIFIER) && ctx.checkNext(TokenType.COLON)) {
            // name: Type — annotation form (idiomatic for main(args: List<String>))
            String name = ctx.advance().value();
            ctx.advance();
            String type = TypeParser.parseTypeRef(ctx);
            ExpressionNode defaultValue = null;
            if (ctx.check(TokenType.EQUAL)) {
                ctx.advance();
                defaultValue = ExpressionParser.parseExpression(ctx);
            }
            return new FormalParameterNode(ctx.pos(), mods, type, name, defaultValue, annos);
        }
        String type = TypeParser.parseTypeRef(ctx);
        String name = ctx.expectId("Expected parameter name", "PARSE023");
        ExpressionNode defaultValue = null;
        if (ctx.check(TokenType.EQUAL)) {
            ctx.advance();
            defaultValue = ExpressionParser.parseExpression(ctx);
        }
        return new FormalParameterNode(ctx.pos(), mods, type, name, defaultValue, annos);
    }

    static List<String> parseThrows(ParseContext ctx) {
        List<String> thrown = new ArrayList<>();
        if (ctx.check(TokenType.THROW)) {
            ctx.advance();
            thrown.add(TypeParser.parseTypeRef(ctx));
            while (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                thrown.add(TypeParser.parseTypeRef(ctx));
            }
        }
        return thrown;
    }

    static String parseTypeRef(ParseContext ctx) {
        StringBuilder type = new StringBuilder();
        if (ctx.check(TokenType.VOID)) {
            ctx.advance();
            return "void";
        }
        // tipo de função: (Int) -> Int ou (Int, String) -> Bool (bug 8)
        if (ctx.check(TokenType.LPAREN)) {
            return TypeParser.parseFunctionTypeRef(ctx);
        }
        if (TypeParser.isPrimitiveType(ctx)) {
            return ctx.advance().value();
        }
        if (ctx.check(TokenType.IDENTIFIER)) {
            type.append(ctx.advance().value());
            while (ctx.check(TokenType.DOT) && ctx.checkNext(TokenType.IDENTIFIER)) {
                ctx.advance();
                type.append('.').append(ctx.advance().value());
            }
        } else {
            ctx.error("Expected type", "PARSE044");
            return "Object";
        }
        if (ctx.check(TokenType.LESS)) {
            StringBuilder args = new StringBuilder("<");
            int depth = 0;
            boolean first = true;
            do {
                Parser.splitShiftRight(ctx);
                boolean isClose = ctx.check(TokenType.GREATER);
                if (ctx.check(TokenType.LESS)) depth++;
                else if (isClose) depth--;
                if (!first && !isClose) args.append(ctx.tokens.get(ctx.pos).value());
                else if (!first && isClose && depth > 0) args.append(ctx.tokens.get(ctx.pos).value());
                first = false;
                ctx.advance();
            } while (depth > 0 && !ctx.atEnd());
            args.append(">");
            // SG-007: wildcard `? extends/super` não é suportado em Kof — deve ser
            // rejeitado com diagnóstico claro, não gerar NoClassDefFoundError
            String argsStr = args.toString();
            String inner = argsStr.length() >= 2 ? argsStr.substring(1, argsStr.length() - 1).trim() : "";
            boolean isWildcard = inner.startsWith("?") || inner.contains(",?") || inner.contains(", ?")
                    || argsStr.contains("?extends") || argsStr.contains("? extends");
            if (isWildcard) {
                ctx.error("Wildcard types '? extends/super' are not supported in Kof; use a concrete type or nullable 'T?'", "PARSE086");
            }
            type.append(args);
        }
        while (ctx.check(TokenType.LBRACKET)) {
            ctx.advance();
            ctx.expect(TokenType.RBRACKET, "Expected ']'", "PARSE045");
            type.append("[]");
        }
        while (ctx.check(TokenType.QUESTION)) {
            ctx.advance();
            type.append("?");
        }
        return type.toString();
    }

    static boolean isPrimitiveType(ParseContext ctx) {
        return ctx.check(TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE,
                TokenType.DOUBLE_TYPE, TokenType.BOOL_TYPE, TokenType.BYTE_TYPE,
                TokenType.SHORT_TYPE, TokenType.CHAR_TYPE, TokenType.STRING_TYPE);
    }

    /**
     * Tipo de função: `(Int) -> Int`, `(Int, String) -> Bool`. Bug 8 — antes
     * não parseava como tipo (param de lambda, arg genérico, declaração).
     */
    static String parseFunctionTypeRef(ParseContext ctx) {
        StringBuilder sb = new StringBuilder("(");
        ctx.expect(TokenType.LPAREN, "Expected '('", "PARSE040");
        boolean first = true;
        while (!ctx.check(TokenType.RPAREN) && !ctx.atEnd()) {
            if (!first) {
                if (ctx.check(TokenType.COMMA)) ctx.advance();
                sb.append(", ");
            }
            sb.append(TypeParser.parseTypeRef(ctx));
            first = false;
        }
        ctx.expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
        sb.append(")");
        ctx.expect(TokenType.ARROW, "Expected '->'", "PARSE042");
        sb.append(" -> ").append(TypeParser.parseTypeRef(ctx));
        return sb.toString();
    }

    static boolean isPrimitiveTypeToken(TokenType t) {
        return t == TokenType.INT_TYPE || t == TokenType.LONG_TYPE
                || t == TokenType.FLOAT_TYPE || t == TokenType.DOUBLE_TYPE
                || t == TokenType.BOOL_TYPE || t == TokenType.BYTE_TYPE
                || t == TokenType.SHORT_TYPE || t == TokenType.CHAR_TYPE
                || t == TokenType.STRING_TYPE;
    }
}
