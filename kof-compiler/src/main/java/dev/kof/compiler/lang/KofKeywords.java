package dev.kof.compiler.lang;

import dev.kof.compiler.TokenType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tabela de keywords CANÔNICA do Kof — fonte única, compartilhada pelo
 * `Lexer` (perfil KOF) e como base do vocabulário PortuKof (`D-PORTUKOF`).
 *
 * <p>Palavras RESERVADAS (SG-001, 06/09): `fun`/`fn`/`func` nunca foram
 * keyword de função do Kof (o corpus diz "não existe fun/fn/func") —
 * viraram reserved words para que NÃO voltem nem como identificador.
 * Mesmo mecanismo de sealed/permits (token dedicado, parser não aceita →
 * erro, nunca silencioso).
 */
public final class KofKeywords {

    private KofKeywords() {}

    public static final Map<String, TokenType> MAP = build();

    private static Map<String, TokenType> build() {
        Map<String, TokenType> k = new LinkedHashMap<>();
        k.put("class", TokenType.CLASS);
        k.put("interface", TokenType.INTERFACE);
        k.put("record", TokenType.RECORD);
        k.put("enum", TokenType.ENUM);
        k.put("entity", TokenType.ENTITY);
        k.put("extern", TokenType.EXTERN);
        k.put("generated", TokenType.GENERATED);
        k.put("unique", TokenType.UNIQUE);
        k.put("extends", TokenType.EXTENDS);
        k.put("implements", TokenType.IMPLEMENTS);
        k.put("fun", TokenType.FUN);
        k.put("fn", TokenType.FN);
        k.put("func", TokenType.FUNC);
        k.put("package", TokenType.PACKAGE);
        k.put("import", TokenType.IMPORT);
        k.put("public", TokenType.PUBLIC);
        k.put("private", TokenType.PRIVATE);
        k.put("protected", TokenType.PROTECTED);
        k.put("static", TokenType.STATIC);
        k.put("final", TokenType.FINAL);
        k.put("abstract", TokenType.ABSTRACT);
        k.put("transient", TokenType.TRANSIENT);
        k.put("volatile", TokenType.VOLATILE);
        k.put("synchronized", TokenType.SYNCHRONIZED);
        k.put("native", TokenType.NATIVE);
        k.put("default", TokenType.DEFAULT);
        k.put("override", TokenType.OVERRIDE);
        k.put("void", TokenType.VOID);
        k.put("new", TokenType.NEW);
        k.put("this", TokenType.THIS);
        k.put("super", TokenType.SUPER);
        k.put("return", TokenType.RETURN);
        k.put("throw", TokenType.THROW);
        k.put("if", TokenType.IF);
        k.put("else", TokenType.ELSE);
        k.put("for", TokenType.FOR);
        k.put("while", TokenType.WHILE);
        k.put("do", TokenType.DO);
        k.put("switch", TokenType.SWITCH);
        k.put("case", TokenType.CASE);
        k.put("break", TokenType.BREAK);
        k.put("continue", TokenType.CONTINUE);
        k.put("try", TokenType.TRY);
        k.put("catch", TokenType.CATCH);
        k.put("finally", TokenType.FINALLY);
        k.put("spawn", TokenType.SPAWN);
        k.put("await", TokenType.AWAIT);
        k.put("assert", TokenType.ASSERT);
        k.put("instanceof", TokenType.INSTANCEOF);
        k.put("var", TokenType.VAR);
        k.put("val", TokenType.VAL);
        k.put("as", TokenType.AS);
        k.put("bool", TokenType.BOOL_TYPE);
        k.put("byte", TokenType.BYTE_TYPE);
        k.put("short", TokenType.SHORT_TYPE);
        k.put("int", TokenType.INT_TYPE);
        k.put("long", TokenType.LONG_TYPE);
        k.put("float", TokenType.FLOAT_TYPE);
        k.put("double", TokenType.DOUBLE_TYPE);
        k.put("char", TokenType.CHAR_TYPE);
        k.put("string", TokenType.STRING_TYPE);
        k.put("true", TokenType.BOOLEAN_LITERAL);
        k.put("false", TokenType.BOOLEAN_LITERAL);
        k.put("null", TokenType.NULL_LITERAL);
        return java.util.Collections.unmodifiableMap(k);
    }
}
