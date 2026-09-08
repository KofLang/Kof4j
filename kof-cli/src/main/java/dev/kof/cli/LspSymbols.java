package dev.kof.cli;

import java.util.Set;

/**
 * Símbolos textuais do LSP (EDI001 degrau 0): same-file definition por
 * varredura de texto — a MESMA família de hover/references do `LspServer`
 * (o LSP do Kof é textual, não roda o parser completo por request).
 *
 * <p>Resolve a DECLARAÇÃO de record/class/interface/enum, função e var/val no
 * arquivo aberto. Conservador por design: parâmetros e campos de instância
 * não são resolvidos — retorna null em vez de apontar para o lugar errado
 * (R6: um go-to-definition mentiroso é pior que nenhum). Definições
 * cross-arquivo dependem do índice do workspace (gap futuro, não este).
 */
final class LspSymbols {

    private LspSymbols() {}

    private static final Set<String> CONTROL = Set.of(
            "if", "for", "while", "switch", "return", "catch", "do", "else");

    /**
     * documentSymbol (outline): nomes de tipo (record/class/interface/enum) e
     * função declarados no arquivo, cada um com o offset [start,end) do nome e
     * o tipo LSP (5=Class, 12=Function). Mesma varredura textual de
     * {@link #declarationRange} — sem parser por request.
     */
    record DocSymbol(String name, int kind, int start, int end) {}

    static java.util.List<DocSymbol> documentSymbols(String text) {
        java.util.List<DocSymbol> out = new java.util.ArrayList<>();
        if (text == null) return out;
        int lineStart = 0;
        int n = text.length();
        while (lineStart <= n) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = n;
            String ln = text.substring(lineStart, lineEnd);
            String t = stripLeading(ln);
            int lead = ln.length() - t.length();
            // tipo: record/class/interface/enum NAME
            for (String kw : new String[]{"record ", "class ", "interface ", "enum "}) {
                if (t.startsWith(kw)) {
                    int[] r = matchName(t.substring(kw.length()), null);
                    if (r != null) out.add(new DocSymbol(
                            t.substring(kw.length() + r[0], kw.length() + r[1]), 5,
                            lineStart + lead + kw.length() + r[0], lineStart + lead + kw.length() + r[1]));
                    break;
                }
            }
            // função: [Tipo] nome(...) { | = | :
            int paren = t.indexOf('(');
            if (paren > 0) {
                int end = paren;
                int start = end;
                while (start > 0 && isIdentChar(t.charAt(start - 1))) start--;
                if (start < end && !CONTROL.contains(t.substring(start, end))
                        && looksLikeDeclaration(t, paren)) {
                    out.add(new DocSymbol(t.substring(start, end), 12,
                            lineStart + lead + start, lineStart + lead + end));
                }
            }
            lineStart = lineEnd + 1;
        }
        return out;
    }

    /** Offset [start,end) do NOME declarado para {@code word}, ou null. */
    static int[] declarationRange(String text, String word) {
        if (word == null || word.isEmpty()) return null;
        int lineStart = 0;
        int n = text.length();
        while (lineStart <= n) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = n;
            String ln = text.substring(lineStart, lineEnd);
            String t = stripLeading(ln);
            int lead = ln.length() - t.length();
            int[] r = declInLine(t, word);
            if (r != null) return new int[]{ lineStart + lead + r[0], lineStart + lead + r[1] };
            lineStart = lineEnd + 1;
        }
        return null;
    }

    /** [start,end) relativo a {@code t} (linha sem indentação) do nome declarado. */
    private static int[] declInLine(String t, String word) {
        for (String kw : new String[]{"record ", "class ", "interface ", "enum "}) {
            if (t.startsWith(kw)) {
                int[] r = matchName(t.substring(kw.length()), word);
                if (r != null) return shift(r, kw.length());
            }
        }
        for (String kw : new String[]{"var ", "val "}) {
            if (t.startsWith(kw)) {
                int[] r = matchName(t.substring(kw.length()), word);
                if (r != null) return shift(r, kw.length());
            }
        }
        // função: [Tipo] nome(...) [ : Tipo ] { | = expr }
        int paren = t.indexOf('(');
        if (paren > 0) {
            int end = paren;
            int start = end;
            while (start > 0 && isIdentChar(t.charAt(start - 1))) start--;
            if (start < end) {
                String name = t.substring(start, end);
                if (name.equals(word) && !CONTROL.contains(name) && looksLikeDeclaration(t, paren)) {
                    return new int[]{ start, end };
                }
            }
        }
        return null;
    }

    /** Depois do '(' balanceado, o próximo char não-espaço é '{', ':' ou '='. */
    private static boolean looksLikeDeclaration(String t, int openParen) {
        int depth = 0;
        for (int i = openParen; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) {
                    for (int j = i + 1; j < t.length(); j++) {
                        char d = t.charAt(j);
                        if (d == ' ' || d == '\t') continue;
                        return d == '{' || d == ':' || d == '=';
                    }
                    return false;
                }
            }
        }
        return false;
    }

    /** [0, fim-do-primeiro-identificador); se word!=null, só quando bate. */
    private static int[] matchName(String rest, String word) {
        int i = 0;
        while (i < rest.length() && isIdentChar(rest.charAt(i))) i++;
        if (i == 0) return null;
        if (word == null) return new int[]{ 0, i };
        return rest.substring(0, i).equals(word) ? new int[]{ 0, i } : null;
    }

    private static int[] shift(int[] r, int by) {
        return new int[]{ r[0] + by, r[1] + by };
    }

    private static String stripLeading(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
        return s.substring(i);
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
