package dev.kof.compiler.parser;
import dev.kof.compiler.AnnotationNode;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.FormalParameterNode;
import dev.kof.compiler.Token;
import dev.kof.compiler.TokenType;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Tipos (REFACTOR-500, FASE 7): referências de tipo, tipos de função,
 * argumentos genéricos, parâmetros formais e throws. Move o cursor
 * do {@link ParseContext} — nunca duplica estado.
 */
public class TypeParser {

    /**
     * Consumes the generic type arguments starting at the current LESS token
     * and returns their source text, e.g. "<Int, String>".
     */
    static String consumeGenericTypeArgs(ParseContext ctx) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (!ctx.atEnd()) {
            // #617: `>>`/`>>>` fecham DOIS/TRÊS níveis de generics aninhados
            // (`List<List<Int>>`), mas o lexer emite um único token
            // GREATER_GREATER/GREATER_GREATER_GREATER (maximal munch) — sem
            // separar antes de checar TokenType.GREATER, o depth nunca
            // zerava aqui (só `parseTypeRef` já fazia isso). Mesmo split que
            // ExpressionParser/parseTypeRef usam.
            Parser.splitShiftRight(ctx);
            Token t = ctx.advance();
            sb.append(t.value());
            // X5.4 (D-X5-SURFACE): `out`/`in` num type-argument (`List<out Animal>`)
            // precisa do espaço separador — os tokens são concatenados crus.
            if (t.type() == TokenType.IDENTIFIER && ("out".equals(t.value()) || "in".equals(t.value()))
                    && ctx.pos < ctx.tokens.size()) {
                TokenType nt = ctx.tokens.get(ctx.pos).type();
                if (nt == TokenType.IDENTIFIER || nt == TokenType.LPAREN) sb.append(' ');
            }
            if (t.type() == TokenType.LESS) depth++;
            else if (t.type() == TokenType.GREATER) {
                depth--;
                if (depth == 0) break;
            }
        }
        return sb.toString();
    }

    /**
     * §355: parâmetros de tipo com BOUND real. `T : Animal` era consumido
     * token a token e o `Animal` ENTRAVA como um segundo type-param fantasma
     * (a lista virava ["T","Animal"] e o bound se perdia). Agora a entrada é
     * o nome, com o bound anexado na forma {@code "T: Animal"} — os
     * consumidores comparam/resolvem via {@code CompilerTypes.typeParamName}
     * / {@code typeParamBound} (ponto único). Sem bound: entrada limpa "T"
     * (comportamento idêntico ao atual).
     */
    static List<String> parseTypeParameters(ParseContext ctx) {
        List<String> typeParams = new ArrayList<>();
        if (ctx.check(TokenType.LESS)) {
            ctx.advance();
            while (!ctx.check(TokenType.GREATER) && !ctx.atEnd()) {
                String name = null;
                String variance = null;
                StringBuilder bound = null;
                int depth = 0;
                while (!ctx.atEnd() && !(depth == 0
                        && (ctx.check(TokenType.COMMA) || ctx.check(TokenType.GREATER)))) {
                    Token t = ctx.advance();
                    if (t.type() == TokenType.LESS) depth++;
                    else if (t.type() == TokenType.GREATER) depth--;
                    if (t.type() == TokenType.COLON && bound == null && depth == 0) {
                        bound = new StringBuilder();
                        continue;
                    }
                    if (bound != null) {
                        bound.append(t.value());
                    } else if (name == null && t.type() == TokenType.IDENTIFIER) {
                        // X5.3 (D-TYPE-VARIANCE): `out`/`in` são keywords
                        // contextuais ANTES do nome do type-param (declaration-site
                        // variance). Sem um identificador seguinte continuam sendo
                        // o próprio nome (compat: `class X<in>` segue válido).
                        if (variance == null && ("out".equals(t.value()) || "in".equals(t.value()))) {
                            variance = t.value();
                        } else {
                            name = t.value();
                        }
                    }
                }
                if (name == null && variance != null) {
                    name = variance;
                    variance = null;
                }
                if (name != null) {
                    String boundText = bound == null ? "" : bound.toString().trim();
                    String tp = boundText.isEmpty() ? name : name + ": " + boundText;
                    typeParams.add(variance == null ? tp : variance + " " + tp);
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
        List<String> mods = TypeDeclarations.parseModifiers(ctx);
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
        // D-R3-BUFFER: `Buffer(U8)` — the nominal out-buffer type (maintainer 21/09).
        if ("Buffer".equals(type.toString()) && ctx.check(TokenType.LPAREN)) {
            ctx.advance();
            String elem = parseTypeRef(ctx);
            ctx.expect(TokenType.RPAREN, "Expected ')' in Buffer(...)", "PARSE045");
            if (!("U8".equals(elem) || "u8".equals(elem)
                    || "Byte".equals(elem) || "byte".equals(elem))) {
                ctx.error("Buffer element must be U8 (Byte) — got '" + elem + "'", "SEM096");
            }
            return "Buffer";
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
                if (!first && !isClose) {
                    if (ctx.check(TokenType.LPAREN)) {
                        // bug 155: tipo-função dentro de type-args — a
                        // concatenação crua de tokens virava "(Int)->Int" (sem
                        // espaços), que Type.of não reconhece → ClassType com
                        // nome inválido (ClassFormatError JVM nos 4 targets).
                        args.append(TypeParser.parseFunctionTypeRef(ctx));
                        first = false;
                        continue;
                    }
                    String tv = ctx.tokens.get(ctx.pos).value();
                    args.append(tv);
                    // X5.4 (D-X5-SURFACE): projeção no sítio de uso — `out`/`in`
                    // antes de um type-argument (`List<out Animal>`). O parser
                    // concatena os valores dos tokens sem espaço; a variância
                    // precisa sobreviver como palavra separada para o `Type.of`
                    // (entrada `"out Animal"` → WildcardType).
                    if (("out".equals(tv) || "in".equals(tv))
                            && ctx.checkNext(TokenType.IDENTIFIER)) {
                        args.append(' ');
                    }
                } else if (!first && isClose && depth > 0) {
                    args.append(ctx.tokens.get(ctx.pos).value());
                }
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
            // D-TROOL (19/09): `Bool` tem exatamente dois valores. O terceiro
            // estado mora em `Troolean` (DECISIONS.md) — `Bool?` morre aqui.
            if (isBoolBase(type.toString())) {
                ctx.error("'Bool' has two values; for true/false/unknown use "
                        + "'Troolean' (DECISIONS.md D-TROOL)", "SEM095");
            }
            type.append("?");
        }
        return type.toString();
    }

    /** D-TROOL: base `Bool` (qualquer grafia canonica) nunca leva `?`. */
    static boolean isBoolBase(String typeText) {
        return switch (typeText) {
            case "Bool", "bool", "Boolean", "boolean" -> true;
            default -> false;
        };
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
            // #379 — guard de progresso: se parseTypeRef nao consumir o token
            // atual (ex.: `+`/`:` num contexto que nao e tipo), o loop alocava
            // "Object" para sempre (OutOfMemoryError). Diagnostico honesto +
            // skip do token mantem o progresso e termina o parse.
            int before = ctx.pos;
            sb.append(TypeParser.parseTypeRef(ctx));
            first = false;
            if (ctx.pos == before) {
                if (!ctx.atEnd()) ctx.advance();
                else break;
            }
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
