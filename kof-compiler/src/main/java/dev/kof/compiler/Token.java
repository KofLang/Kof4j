package dev.kof.compiler;

public record Token(TokenType type, String value, String file, int line, int column, int offset, int length) {

    boolean is(TokenType t) {
        return type == t;
    }

    boolean is(TokenType t, String v) {
        return type == t && value.equals(v);
    }
}
