package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Annotations (REFACTOR-500, FASE 7): {@code @Name(k = v)} e valores de
 * compile-time (literais, arrays, {@code X.class}, {@code A.B}). Move o cursor
 * do {@link ParseContext} — nunca duplica estado.
 */
class AnnotationParser {

    /**
     * @Name, @pkg.Name e @Name(valor | key = valor, ...) — valores são
     * constantes em compile-time (literais ou arrays {a, b}). Annotations
     * são metadados de interop: parseadas aqui, preservadas na IR e
     * emitidas no bytecode pelo backend; nunca substituem APIs idiomáticas.
     */
    static List<AnnotationNode> parseAnnotations(ParseContext ctx) {
        List<AnnotationNode> out = new ArrayList<>();
        while (ctx.check(TokenType.AT)) {
            SourcePosition p = ctx.pos();
            ctx.advance();
            StringBuilder name = new StringBuilder(ctx.expectId("Expected annotation name", "PARSE080"));
            while (ctx.check(TokenType.DOT) && ctx.checkNext(TokenType.IDENTIFIER)) {
                ctx.advance();
                name.append('.').append(ctx.advance().value());
            }
            List<AnnotationPair> pairs = new ArrayList<>();
            if (ctx.check(TokenType.LPAREN)) {
                ctx.advance();
                if (!ctx.check(TokenType.RPAREN)) {
                    do {
                        String key = null;
                        if (ctx.check(TokenType.IDENTIFIER) && ctx.checkNext(TokenType.EQUAL)) {
                            key = ctx.advance().value();
                            ctx.advance();
                        }
                        Object value = parseAnnotationValue(ctx);
                        if (value instanceof ParseError) continue;
                        pairs.add(new AnnotationPair(key, value));
                    } while (ctx.check(TokenType.COMMA) && !ctx.advance().is(TokenType.EOF));
                }
                ctx.expect(TokenType.RPAREN, "Expected ')' after annotation arguments", "PARSE081");
            }
            out.add(new AnnotationNode(p, name.toString(), List.copyOf(pairs)));
        }
        return out;
    }

    /** Marcador interno: valor de annotation inválido (já diagnosticado). */
    static final class ParseError {
        static final ParseError INSTANCE = new ParseError();
    }

    static boolean isParseError(Object v) {
        return v == ParseError.INSTANCE;
    }

    static boolean isNumericKind(Class<?> k) {
        return k == Integer.class || k == Long.class || k == Float.class || k == Double.class;
    }

    /**
     * Valor constante de annotation: literal, array {v1, v2} de literais
     * ou identificador simples. Identificadores não-constantes produzem
     * ANNOT001 (nunca um valor silenciosamente errado).
     */
    static Object parseAnnotationValue(ParseContext ctx) {
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            List<Object> items = new ArrayList<>();
            Class<?> elementKind = null;
            boolean mixed = false;
            if (!ctx.check(TokenType.RBRACE)) {
                do {
                    Object v = parseAnnotationValue(ctx);
                    if (!isParseError(v)) {
                        // JVM exige tipo único no array: {1, "a"} é bytecode
                        // inválido — diagnosticado aqui, não emitido
                        Class<?> kind = v != null ? v.getClass() : null;
                        if (kind == Integer.class && elementKind == null) kind = Integer.class;
                        if (elementKind == null) {
                            elementKind = kind;
                        } else if (kind != null && !kind.equals(elementKind)
                                && !(isNumericKind(kind) && isNumericKind(elementKind))) {
                            mixed = true;
                        }
                        items.add(v);
                    }
                } while (ctx.check(TokenType.COMMA) && !ctx.advance().is(TokenType.EOF));
            }
            ctx.expect(TokenType.RBRACE, "Expected '}' after annotation array", "PARSE082");
            if (mixed) {
                ctx.error("Annotation array values must have the same type", "ANNOT002");
                return ParseError.INSTANCE;
            }
            return items;
        }
        if (ctx.check(TokenType.STRING_LITERAL) || ctx.check(TokenType.INT_LITERAL) || ctx.check(TokenType.LONG_LITERAL)
                || ctx.check(TokenType.FLOAT_LITERAL) || ctx.check(TokenType.DOUBLE_LITERAL)
                || ctx.check(TokenType.BOOLEAN_LITERAL) || ctx.check(TokenType.CHAR_LITERAL)
                || ctx.check(TokenType.NULL_LITERAL)) {
            Token t = ctx.advance();
            try {
                return switch (t.type()) {
                    case STRING_LITERAL -> t.value();
                    case INT_LITERAL -> Integer.parseInt(t.value());
                    case LONG_LITERAL -> Long.parseLong(t.value().replaceAll("[lL]$", ""));
                    case FLOAT_LITERAL -> Float.parseFloat(t.value().replaceAll("[fF]$", ""));
                    case DOUBLE_LITERAL -> Double.parseDouble(t.value().replaceAll("[dD]$", ""));
                    case BOOLEAN_LITERAL -> Boolean.parseBoolean(t.value());
                    case CHAR_LITERAL -> t.value().charAt(0);
                    default -> null;
                };
            } catch (NumberFormatException e) {
                ctx.error("Invalid numeric literal in annotation", "PARSE083");
                return ParseError.INSTANCE;
            }
        }
        // negativos
        if (ctx.check(TokenType.MINUS) && (ctx.checkNext(TokenType.INT_LITERAL) || ctx.checkNext(TokenType.LONG_LITERAL)
                || ctx.checkNext(TokenType.FLOAT_LITERAL) || ctx.checkNext(TokenType.DOUBLE_LITERAL))) {
            ctx.advance();
            Token t = ctx.advance();
            try {
                return switch (t.type()) {
                    case INT_LITERAL -> Integer.parseInt("-" + t.value());
                    case LONG_LITERAL -> Long.parseLong("-" + t.value().replaceAll("[lL]$", ""));
                    case FLOAT_LITERAL -> Float.parseFloat("-" + t.value().replaceAll("[fF]$", ""));
                    default -> Double.parseDouble("-" + t.value().replaceAll("[dD]$", ""));
                };
            } catch (NumberFormatException e) {
                ctx.error("Invalid numeric literal in annotation", "PARSE083");
                return ParseError.INSTANCE;
            }
        }
        if (ctx.check(TokenType.IDENTIFIER)) {
            // Nome qualificado: Classe.class (valor Class) ou Enum.CONST
            StringBuilder name = new StringBuilder(ctx.advance().value());
            while (ctx.check(TokenType.DOT) && (ctx.checkNext(TokenType.IDENTIFIER) || ctx.isTypeKeywordAtNext())) {
                ctx.advance();
                name.append('.').append(ctx.advance().value());
            }
            if (ctx.check(TokenType.DOT) && ctx.peek().value().equals("class")) {
                ctx.advance();
                ctx.advance();
                return new AnnotationClassRef(name.toString());
            }
            if (name.indexOf(".") >= 0) {
                return new AnnotationEnumRef(name.toString());
            }
            ctx.error("Annotation values must be compile-time constants ('" + name
                    + "' is not supported yet)", "ANNOT001");
            return ParseError.INSTANCE;
        }
        ctx.error("Expected annotation value", "PARSE084");
        ctx.advance();
        return ParseError.INSTANCE;
    }
}
