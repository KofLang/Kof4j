package dev.kof.compiler.parser;
import dev.kof.compiler.DiagnosticCollector;
import dev.kof.compiler.Token;
import dev.kof.compiler.TokenType;
import dev.kof.compiler.lang.KofKeywords;
import dev.kof.compiler.lang.LanguageProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {

    /** Tabela canônica — fonte única em `lang/KofKeywords` (`D-PORTUKOF`). */
    private static final Map<String, TokenType> KEYWORDS = KofKeywords.MAP;

    private final String source;
    private final String file;
    private final DiagnosticCollector diagnostics;
    /** Perfil da superfície (`D-PORTUKOF`): tabela de keywords + valores canônicos.
     *  Construtor histórico = perfil KOF canônico — comportamento inalterado. */
    private final LanguageProfile profile;
    private final List<Token> tokens = new ArrayList<>();
    private int pos;
    private int line = 1;
    private int column = 1;

    public Lexer(String source, String file, DiagnosticCollector diagnostics) {
        this(source, file, diagnostics, LanguageProfile.KOF);
    }

    public Lexer(String source, String file, DiagnosticCollector diagnostics,
                 LanguageProfile profile) {
        this.source = source;
        this.file = file;
        this.diagnostics = diagnostics;
        this.profile = profile;
    }

    public List<Token> tokenize() {
        // OBS-008: tolerar um UTF-8 BOM inicial (EF BB BF) — editores do
        // Windows gravam o BOM por padrão; um BOM no começo do arquivo não
        // é um caractere de código Kof.
        if (source.startsWith("\uFEFF")) {
            pos = 1;
        }
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\n' || c == '\r') {
                advance();
                if (c == '\r' && pos < source.length() && source.charAt(pos) == '\n') {
                    advance();
                }
                line++;
                column = 1;
            } else if (c == ' ' || c == '\t' || c == '\f') {
                advance();
            } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                skipLineComment();
            } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '*') {
                skipBlockComment();
            } else if (c == '"') {
                if (pos + 2 < source.length() && source.charAt(pos + 1) == '"' && source.charAt(pos + 2) == '"') {
                    skipTripleQuoted();
                } else {
                    readString();
                }
            } else if (c == '\'') {
                readChar();
            } else if (Character.isDigit(c)) {
                readNumber();
            } else if (Character.isLetter(c) || c == '_' || c == '$') {
                readIdentifier();
            } else {
                readOperatorOrDelimiter();
            }
        }
        tokens.add(new Token(TokenType.EOF, "", file, line, column, pos, 0));
        return tokens;
    }

    private void advance() {
        pos++;
        column++;
    }

    private char peek() {
        return pos < source.length() ? source.charAt(pos) : '\0';
    }

    private char peekNext() {
        return pos + 1 < source.length() ? source.charAt(pos + 1) : '\0';
    }

    private char peekNextNext() {
        return pos + 2 < source.length() ? source.charAt(pos + 2) : '\0';
    }

    private void addToken(TokenType type, String value, int startLine, int startCol, int startOffset, int length) {
        tokens.add(new Token(type, value, file, startLine, startCol, startOffset, length));
    }

    private void skipLineComment() {
        while (pos < source.length() && source.charAt(pos) != '\n') {
            advance();
        }
    }

    private void skipBlockComment() {
        advance();
        advance();
        while (pos < source.length()) {
            if (source.charAt(pos) == '*' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                advance();
                advance();
                return;
            }
            if (source.charAt(pos) == '\n') {
                line++;
                column = 1;
            }
            advance();
        }
        diagnostics.error(file, line, column, 0, "Unterminated block comment", "LEX001");
    }

    private void skipTripleQuoted() {
        // Kof has no raw/multiline string literal (lexical-structure §4.1).
        // #364: `"""..."""` used to fold to an EMPTY string silently — R6
        // violation. Reject with LEX008 and skip the block so one diagnostic
        // lands, not a cascade from every character inside it.
        int startLine = line;
        int startCol = column;
        advance();
        advance();
        advance();
        while (pos < source.length()) {
            if (source.charAt(pos) == '"' && pos + 2 < source.length()
                    && source.charAt(pos + 1) == '"' && source.charAt(pos + 2) == '"') {
                advance();
                advance();
                advance();
                break;
            }
            if (source.charAt(pos) == '\n') {
                line++;
                column = 1;
            }
            advance();
        }
        diagnostics.error(file, startLine, startCol, 1,
                "Kof has no triple-quoted (raw/multiline) string literal: use \"...\" with \\n (no interpolation either — concatenate with +)",
                "LEX008");
    }

    private void readString() {
        int startLine = line;
        int startCol = column;
        int startOffset = pos;
        advance();
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && source.charAt(pos) != '"') {
            if (source.charAt(pos) == '\\') {
                advance();
                if (pos < source.length()) {
                    sb.append(readEscape());
                }
            } else {
                if (source.charAt(pos) == '\n') {
                    line++;
                    column = 1;
                }
                sb.append(source.charAt(pos));
                advance();
            }
        }
        if (pos >= source.length()) {
            diagnostics.error(file, startLine, startCol, 1, "Unterminated string literal", "LEX002");
            return;
        }
        advance();
        addToken(TokenType.STRING_LITERAL, sb.toString(), startLine, startCol, startOffset, pos - startOffset);
    }

    private void readChar() {
        int startLine = line;
        int startCol = column;
        int startOffset = pos;
        advance();
        if (pos >= source.length() || source.charAt(pos) == '\'') {
            diagnostics.error(file, startLine, startCol, 1, "Empty character literal", "LEX003");
            if (pos < source.length() && source.charAt(pos) == '\'') advance();
            return;
        }
        String value;
        if (source.charAt(pos) == '\\') {
            advance();
            value = String.valueOf(readEscape());
        } else {
            value = String.valueOf(source.charAt(pos));
            advance();
        }
        if (pos < source.length() && source.charAt(pos) == '\'') {
            advance();
        } else {
            diagnostics.error(file, startLine, startCol, pos - startOffset, "Unterminated character literal", "LEX004");
        }
        addToken(TokenType.CHAR_LITERAL, value, startLine, startCol, startOffset, pos - startOffset);
    }

    private char readEscape() {
        if (pos >= source.length()) return '\0';
        char c = source.charAt(pos);
        advance();
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"' -> '"';
            case '0' -> '\0';
            case 'u' -> readUnicodeEscape();
            default -> c;
        };
    }

    private char readUnicodeEscape() {
        if (pos + 4 > source.length()) {
            diagnostics.error(file, line, column, 1, "Incomplete unicode escape (expected \\uXXXX)", "LEX006");
            return '\0';
        }
        String hex = source.substring(pos, pos + 4);
        int code;
        try {
            code = Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            diagnostics.error(file, line, column, 4, "Invalid unicode escape: \\u" + hex, "LEX007");
            pos += 4;
            return '\0';
        }
        pos += 4;
        column += 4;
        return (char) code;
    }

    private void readNumber() {
        int startLine = line;
        int startCol = column;
        int startOffset = pos;
        boolean isLong = false;
        boolean isFloat = false;
        boolean isDouble = false;
        // hexadecimal literal: 0x...
        if (pos + 1 < source.length() && source.charAt(pos) == '0'
                && (source.charAt(pos + 1) == 'x' || source.charAt(pos + 1) == 'X')) {
            advance();
            advance();
            while (pos < source.length() && isHexDigit(source.charAt(pos))) {
                advance();
            }
            if (pos < source.length() && (source.charAt(pos) == 'l' || source.charAt(pos) == 'L')) {
                isLong = true;
                advance();
            }
            String hexValue = source.substring(startOffset, pos);
            TokenType type = isLong ? TokenType.LONG_LITERAL : TokenType.INT_LITERAL;
            addToken(type, hexValue, startLine, startCol, startOffset, pos - startOffset);
            return;
        }
        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            advance();
        }
        if (pos < source.length() && source.charAt(pos) == '.' &&
                pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1))) {
            advance();
            isDouble = true;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                advance();
            }
        }
        if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
            isDouble = true;
            advance();
            if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) {
                advance();
            }
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                advance();
            }
        }
        if (pos < source.length() && (source.charAt(pos) == 'f' || source.charAt(pos) == 'F')) {
            isFloat = true;
            advance();
        } else if (pos < source.length() && (source.charAt(pos) == 'd' || source.charAt(pos) == 'D')) {
            isDouble = true;
            advance();
        } else if (pos < source.length() && (source.charAt(pos) == 'l' || source.charAt(pos) == 'L')) {
            isLong = true;
            advance();
        }
        String value = source.substring(startOffset, pos);
        TokenType type;
        if (isFloat) {
            type = TokenType.FLOAT_LITERAL;
        } else if (isDouble) {
            type = TokenType.DOUBLE_LITERAL;
        } else if (isLong) {
            type = TokenType.LONG_LITERAL;
        } else if (isIntegerLiteral(value)) {
            type = TokenType.INT_LITERAL;
        } else {
            type = TokenType.LONG_LITERAL;
        }
        addToken(type, value, startLine, startCol, startOffset, pos - startOffset);
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean isIntegerLiteral(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            try {
                Long.parseLong(value.substring(2), 16);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void readIdentifier() {
        int startLine = line;
        int startCol = column;
        int startOffset = pos;
        while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) ||
                source.charAt(pos) == '_' || source.charAt(pos) == '$')) {
            advance();
        }
        String value = source.substring(startOffset, pos);
        TokenType type = profile.keywords().getOrDefault(value, TokenType.IDENTIFIER);
        String canon = profile.lexicalCanon().get(value);
        addToken(type, canon != null ? canon : value, startLine, startCol, startOffset, pos - startOffset);
    }

    private void readOperatorOrDelimiter() {
        int startLine = line;
        int startCol = column;
        int startOffset = pos;
        int startPos = pos;
        advance();
        char c = source.charAt(startPos);
        TokenType type = switch (c) {
            case '+' -> switch (peek()) {
                case '+' -> { advance(); yield TokenType.PLUS_PLUS; }
                case '=' -> { advance(); yield TokenType.PLUS_EQUAL; }
                default -> TokenType.PLUS;
            };
            case '-' -> switch (peek()) {
                case '-' -> { advance(); yield TokenType.MINUS_MINUS; }
                case '=' -> { advance(); yield TokenType.MINUS_EQUAL; }
                case '>' -> { advance(); yield TokenType.ARROW; }
                default -> TokenType.MINUS;
            };
            case '*' -> switch (peek()) {
                case '=' -> { advance(); yield TokenType.STAR_EQUAL; }
                default -> TokenType.STAR;
            };
            case '/' -> switch (peek()) {
                case '=' -> { advance(); yield TokenType.SLASH_EQUAL; }
                default -> TokenType.SLASH;
            };
            case '%' -> switch (peek()) {
                case '=' -> { advance(); yield TokenType.PERCENT_EQUAL; }
                default -> TokenType.PERCENT;
            };
            case '!' -> switch (peek()) {
                case '=' -> { advance(); yield TokenType.BANG_EQUAL; }
                default -> TokenType.BANG;
            };
            case '=' -> switch (peek()) {
                case '=' -> { advance(); yield TokenType.EQUAL_EQUAL; }
                default -> TokenType.EQUAL;
            };
            case '<' -> switch (peek()) {
                case '=' -> { advance(); yield TokenType.LESS_EQUAL; }
                case '<' -> {
                    advance();
                    if (peek() == '=') { advance(); yield TokenType.LESS_LESS_EQUAL; }
                    yield TokenType.LESS_LESS;
                }
                default -> TokenType.LESS;
            };
            case '>' -> switch (peek()) {
                case '=' -> { advance(); yield TokenType.GREATER_EQUAL; }
                case '>' -> {
                    advance();
                    if (peek() == '>') {
                        advance();
                        if (peek() == '=') { advance(); yield TokenType.GREATER_GREATER_GREATER_EQUAL; }
                        yield TokenType.GREATER_GREATER_GREATER;
                    }
                    if (peek() == '=') { advance(); yield TokenType.GREATER_GREATER_EQUAL; }
                    yield TokenType.GREATER_GREATER;
                }
                default -> TokenType.GREATER;
            };
            case '&' -> switch (peek()) {
                case '&' -> { advance(); yield TokenType.AMP_AMP; }
                case '=' -> { advance(); yield TokenType.AMP_EQUAL; }
                default -> TokenType.AMP;
            };
            case '|' -> switch (peek()) {
                case '|' -> { advance(); yield TokenType.PIPE_PIPE; }
                case '=' -> { advance(); yield TokenType.PIPE_EQUAL; }
                default -> TokenType.PIPE;
            };
            case '^' -> switch (peek()) {
                case '=' -> { advance(); yield TokenType.CARET_EQUAL; }
                default -> TokenType.CARET;
            };
            case '(' -> TokenType.LPAREN;
            case ')' -> TokenType.RPAREN;
            case '{' -> TokenType.LBRACE;
            case '}' -> TokenType.RBRACE;
            case '[' -> TokenType.LBRACKET;
            case ']' -> TokenType.RBRACKET;
            case ';' -> TokenType.SEMICOLON;
            case ',' -> TokenType.COMMA;
            case '.' -> TokenType.DOT;
            case ':' -> TokenType.COLON;
            case '?' -> TokenType.QUESTION;
            case '@' -> TokenType.AT;
            default -> {
                diagnostics.error(file, startLine, startCol, 1,
                        "Unexpected character: '" + c + "'", "LEX005");
                yield TokenType.ERROR;
            }
        };
        addToken(type, source.substring(startOffset, pos), startLine, startCol, startOffset, pos - startOffset);
    }
}
