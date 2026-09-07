package dev.kof.compiler;

public record Token(TokenType type, String value, String file, int line, int column, int offset, int length) {

    public boolean is(TokenType t) {
        return type == t;
    }

    public boolean is(TokenType t, String v) {
        return type == t && value.equals(v);
    }
}
