package dev.kof.cli;

import java.util.ArrayList;
import java.util.List;

/** Lexer + tokens + parser-base do {@code kof translate} (extraído p/ gate <=500). */
    enum T {
        IDENT, INT, FLOAT, STR, CHAR, P, // { } ( ) [ ] ; , . 
        EQ, EQEQ, NE, LT, LE, GT, GE, PLUS, MINUS, STAR, SLASH, PERCENT,
        ANDAND, OROR, NOT, PLUSEQ, MINUSEQ, STAREQ, SLASHEQ, PERCENTEQ,
        INC, DEC, ARROW, EOF
    }

    final class Tok {
        final T type;
        final String text;
        Tok(T type, String text) { this.type = type; this.text = text; }
    }

    final class TranslateLexer {
    static final java.util.Set<String> KEYWORDS = java.util.Set.of(
            "public", "private", "protected", "static", "final", "abstract",
            "class", "interface", "extends", "implements", "return", "if",
            "else", "while", "for", "new", "package", "import", "null",
            "true", "false", "throw", "try", "catch", "finally", "void",
            "boolean", "byte", "short", "int", "long", "float", "double",
            "char", "String", "this", "super", "switch", "case", "break",
            "default", "do");

        static List<Tok> lex(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                StringBuilder sb = new StringBuilder();
                while (j < n && s.charAt(j) != '"') {
                    if (s.charAt(j) == '\\' && j + 1 < n) {
                        char e = s.charAt(j + 1);
                        sb.append(switch (e) {
                            case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r';
                            case '"' -> '"'; case '\\' -> '\\';
                            default -> e;
                        });
                        j += 2;
                    } else {
                        sb.append(s.charAt(j)); j++;
                    }
                }
                out.add(new Tok(T.STR, sb.toString()));
                i = j + 1;
                continue;
            }
            if (c == '\'') {
                if (i + 2 < n && s.charAt(i + 2) == '\'') {
                    out.add(new Tok(T.CHAR, String.valueOf(s.charAt(i + 1))));
                    i += 3;
                } else {
                    i++;
                }
                continue;
            }
            if (Character.isDigit(c)) {
                int j = i;
                boolean isFloat = false;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                    if (s.charAt(j) == '.') isFloat = true;
                    j++;
                }
                out.add(new Tok(isFloat ? T.FLOAT : T.INT, s.substring(i, j)));
                i = j;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int j = i;
                while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                String w = s.substring(i, j);
                if (KEYWORDS.contains(w)) {
                    out.add(new Tok(T.IDENT, w)); // keyword kept as IDENT text
                } else {
                    out.add(new Tok(T.IDENT, w));
                }
                i = j;
                continue;
            }
            switch (c) {
                case '{' -> out.add(new Tok(T.P, "{"));
                case '}' -> out.add(new Tok(T.P, "}"));
                case '(' -> out.add(new Tok(T.P, "("));
                case ')' -> out.add(new Tok(T.P, ")"));
                case '[' -> out.add(new Tok(T.P, "["));
                case ']' -> out.add(new Tok(T.P, "]"));
                case ';' -> out.add(new Tok(T.P, ";"));
                case ',' -> out.add(new Tok(T.P, ","));
                case ':' -> out.add(new Tok(T.P, ":"));
                case '?' -> out.add(new Tok(T.P, "?"));
                case '.' -> out.add(new Tok(T.P, "."));
                case '=' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.EQEQ, "==")); i += 2; continue; } out.add(new Tok(T.EQ, "=")); }
                case '!' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.NE, "!=")); i += 2; continue; } out.add(new Tok(T.NOT, "!")); }
                case '<' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.LE, "<=")); i += 2; continue; } out.add(new Tok(T.LT, "<")); }
                case '>' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.GE, ">=")); i += 2; continue; } out.add(new Tok(T.GT, ">")); }
                case '+' -> { if (i + 1 < n && s.charAt(i + 1) == '+') { out.add(new Tok(T.INC, "++")); i += 2; continue; } if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.PLUSEQ, "+=")); i += 2; continue; } out.add(new Tok(T.PLUS, "+")); }
                case '-' -> { if (i + 1 < n && s.charAt(i + 1) == '-') { out.add(new Tok(T.DEC, "--")); i += 2; continue; } if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.MINUSEQ, "-=")); i += 2; continue; } if (i + 1 < n && s.charAt(i + 1) == '>') { out.add(new Tok(T.ARROW, "->")); i += 2; continue; } out.add(new Tok(T.MINUS, "-")); }
                case '*' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.STAREQ, "*=")); i += 2; continue; } out.add(new Tok(T.STAR, "*")); }
                case '/' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.SLASHEQ, "/=")); i += 2; continue; } out.add(new Tok(T.SLASH, "/")); }
                case '%' -> { if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.PERCENTEQ, "%=")); i += 2; continue; } out.add(new Tok(T.PERCENT, "%")); }
                case '&' -> { if (i + 1 < n && s.charAt(i + 1) == '&') { out.add(new Tok(T.ANDAND, "&&")); i += 2; continue; } }
                case '|' -> { if (i + 1 < n && s.charAt(i + 1) == '|') { out.add(new Tok(T.OROR, "||")); i += 2; continue; } }
                default -> i++;
            }
            i = Math.min(i + 1, n); // guarded advance for simple single-char cases
        }
        out.add(new Tok(T.EOF, ""));
        return out;
    }

    // ── Parser + Emitter range helpers ────────────────────────────────────

    }
class TranslateException extends RuntimeException {
        TranslateException(String m) { super(m); }
    }

    class Parser {
        final List<Tok> toks;
        int pos;
        Parser(List<Tok> toks) { this.toks = toks; }
        Tok peek() { return toks.get(pos); }
        Tok peek(int ahead) { int i = Math.min(pos + ahead, toks.size() - 1); return toks.get(i); }
        Tok next() { Tok t = toks.get(pos); if (pos < toks.size() - 1) pos++; return t; }
        boolean at(String text) { return peek().text.equals(text); }
        boolean at(T t) { return peek().type == t; }
        Tok expect(String text) {
            if (!at(text)) throw new TranslateException("expected '" + text + "' but found '" + peek().text + "'");
            return next();
        }
        Tok expectPunct() { return next(); }
    }
