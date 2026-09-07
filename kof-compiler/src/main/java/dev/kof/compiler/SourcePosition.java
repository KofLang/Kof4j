package dev.kof.compiler;

public record SourcePosition(String file, int line, int column, int offset, int length) {

    static SourcePosition of(String file, Token token) {
        return new SourcePosition(file, token.line(), token.column(), token.offset(), token.length());
    }
}
