package dev.kof.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contexto de parsing — estado MUTUÁVEL compartilhado do parser
 * recursivo-descendente (REFACTOR-500, FASE 7). O cursor {@code pos} é o
 * único estado mutável: todo parser extraído recebe este contexto por
 * parâmetro e o avança; nenhum estado é duplicado entre as classes.
 */
class ParseContext {

    final List<Token> tokens;
    final DiagnosticCollector diagnostics;
    final String file;
    final Set<String> entityNames = new HashSet<>();
    String currentClassName;
    int pos;

    ParseContext(List<Token> tokens, DiagnosticCollector diagnostics, String file) {
        this.tokens = tokens;
        this.diagnostics = diagnostics;
        this.file = file;
    }

    boolean check(TokenType... types) {
        if (atEnd()) return false;
        TokenType cur = peek().type();
        for (TokenType t : types) {
            if (cur == t) return true;
        }
        return false;
    }

    boolean checkNext(TokenType type) {
        int next = pos + 1;
        return next < tokens.size() && tokens.get(next).type() == type;
    }

    Token peekAt(int n) {
        return tokens.get(Math.min(pos + n, tokens.size() - 1));
    }

    boolean atEnd() {
        return pos >= tokens.size() || peek().type() == TokenType.EOF;
    }

    Token peek() {
        return tokens.get(Math.min(pos, tokens.size() - 1));
    }

    Token advance() {
        if (!atEnd()) pos++;
        return tokens.get(pos - 1);
    }

    Token expect(TokenType type, String message, String code) {
        if (check(type)) return advance();
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
        return peek();
    }

    void expectSemicolon() {
        if (check(TokenType.SEMICOLON)) {
            advance();
        }
    }

    String expectId(String message, String code) {
        if (check(TokenType.IDENTIFIER)) return advance().value();
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
        return "error";
    }

    boolean isTypeKeywordField() {
        return check(TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE,
                TokenType.DOUBLE_TYPE, TokenType.BOOL_TYPE, TokenType.BYTE_TYPE,
                TokenType.SHORT_TYPE, TokenType.CHAR_TYPE, TokenType.STRING_TYPE,
                TokenType.RECORD);
    }

    boolean isTypeKeywordAtNext() {
        if (pos + 1 >= tokens.size()) return false;
        return switch (tokens.get(pos + 1).type()) {
            case INT_TYPE, LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE, BOOL_TYPE,
                    BYTE_TYPE, SHORT_TYPE, CHAR_TYPE, STRING_TYPE -> true;
            default -> false;
        };
    }

    SourcePosition pos() {
        Token t = peek();
        return new SourcePosition(file, t.line(), t.column(), t.offset(), t.length());
    }

    void error(String message, String code) {
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
    }
}
