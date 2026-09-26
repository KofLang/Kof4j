package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * §625 — maquina de comentarios do formatter.
 *
 * O Lexer descarta comentarios por contrato e o formatter reimprime da AST,
 * entao `kof fmt` os deletava em silencio (e a heuristica de 50% do
 * KofFormatter decidia POR ACIDENTE qual caminho rodava). Esta classe da a
 * fonte unica dos dois papeis que consertam isso:
 *
 *  1. `scan(src)` coleta cada comentario `//` e `*​/` do texto-fonte junto da
 *     linha onde comeca — sem tratar como comentario nada dentro de string
 *     ou char literal (mesma gramatica de escapes do Lexer).
 *  2. `Pending` e o cursor de costura: o formatter, antes de imprimir cada
 *     construcao com posicao, despeja `flushBefore(linha)` — os comentarios
 *     ainda nao emitidos cuja linha e anterior a da construcao (comentarios
 *     inline da MESMA linha saem como linha propria logo antes; preserva o
 *     conteudo e a ordem, que e o contrato da issue).
 *
 * Determinismo: mesmo programa = mesmo resultado, com 0 ou N comentarios.
 */
final class KofFormatterComments {

    private KofFormatterComments() {}

    record Comment(int line, String text) {}

    /** Coleta os comentarios do texto-fonte na ordem em que aparecem. */
    static List<Comment> scan(String src) {
        List<Comment> out = new ArrayList<>();
        int line = 1;
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') { line++; i++; continue; }
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < n) {
                    char s = src.charAt(i);
                    if (s == '\\' && i + 1 < n) {
                        if (src.charAt(i + 1) == '\n') line++;
                        i += 2;
                        continue;
                    }
                    if (s == '\n') { line++; i++; continue; }
                    if (s == quote) { i++; break; }
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                int start = i;
                while (i < n && src.charAt(i) != '\n') i++;
                out.add(new Comment(line, src.substring(start, i)));
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int startLine = line;
                int start = i;
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') line++;
                    i++;
                }
                if (i + 1 >= n) return out; // unterminated: LEX001 cuida do diagnostico
                i += 2;
                out.add(new Comment(startLine, src.substring(start, i)));
                continue;
            }
            i++;
        }
        return out;
    }

    /** Cursor de costura: comentarios ja vistos, em ordem, despejados por linha. */
    static final class Pending {
        private final List<Comment> comments;
        private int idx;

        Pending(List<Comment> comments) {
            this.comments = comments;
        }

        /** Escreve os comentarios pendentes cuja linha e < line, com indent. */
        void flushBefore(StringBuilder out, int indent, int line) {
            String pad = "    ".repeat(indent);
            while (idx < comments.size() && comments.get(idx).line() < line) {
                emit(out, pad, comments.get(idx).text());
                idx++;
            }
        }

        /** Escreve os pendentes cuja linha e <= line (uso p/ linha da construcao). */
        void flushUpTo(StringBuilder out, int indent, int line) {
            String pad = "    ".repeat(indent);
            while (idx < comments.size() && comments.get(idx).line() <= line) {
                emit(out, pad, comments.get(idx).text());
                idx++;
            }
        }

        /** Escreve o que sobrou (comentarios depois da ultima construcao). */
        void flushAll(StringBuilder out, int indent) {
            String pad = "    ".repeat(indent);
            while (idx < comments.size()) {
                emit(out, pad, comments.get(idx).text());
                idx++;
            }
        }

        private static void emit(StringBuilder out, String pad, String text) {
            for (String l : text.split("\n", -1)) {
                out.append(pad).append(l.stripTrailing()).append('\n');
            }
        }
    }
}
