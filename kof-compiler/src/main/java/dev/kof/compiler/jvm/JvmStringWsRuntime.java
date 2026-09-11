package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S3.2). kof.strings
 * removeWhitespace/normalizeWhitespace — ASCII (bytes >=128 copiados;
 * paridade capitalize). concatena via JvmStringRuntime.source().
 */
public final class JvmStringWsRuntime {

    private JvmStringWsRuntime() {}

    static String source() {
        return """

                // ── kof.strings (STDLIB S3.2) — whitespace ────────────────
                // WS = {tab,LF,VT,FF,CR,espaço} (9..13,32). >=128 NÃO é WS.
                private static boolean kof_isWs(char c) {
                    return c == 32 || (c >= 9 && c <= 13);
                }

                // removeWhitespace: descarta todo WS; null -> null.
                // uncapitalize (S11): espelho do capitalize — byte 0 A-Z -> a-z.
                public static String kof_strings_uncapitalize(String v) {
                    if (v == null || v.isEmpty()) return v;
                    char c = v.charAt(0);
                    if (c >= 'A' && c <= 'Z') return (char) (c + 32) + v.substring(1);
                    return v;
                }

                public static String kof_strings_removeWhitespace(String v) {
                    if (v == null) return null;
                    StringBuilder o = new StringBuilder(v.length());
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (!kof_isWs(c)) o.append(c);
                    }
                    return o.toString();
                }

                // normalizeWhitespace: trim ends de WS; colapsa runs internos
                // de WS p/ UM espaço. null -> null. "  a  b  " -> "a b".
                public static String kof_strings_normalizeWhitespace(String v) {
                    if (v == null) return null;
                    StringBuilder o = new StringBuilder(v.length());
                    boolean inws = false, started = false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (kof_isWs(c)) {
                            if (started) inws = true;
                        } else {
                            if (inws) { o.append(' '); inws = false; }
                            o.append(c); started = true;
                        }
                    }
                    return o.toString();
                }

                // indent: adiciona n espaços no início de cada linha com conteúdo; null -> null.
                public static String kof_strings_indent(String v, int n) {
                    if (v == null) return null;
                    if (v.isEmpty() || n <= 0) return v;
                    StringBuilder o = new StringBuilder(v.length() + n * 4);
                    int len = v.length();
                    int i = 0;
                    while (i < len) {
                        int next = v.indexOf(10, i);
                        int lineEnd = (next == -1) ? len : next;
                        boolean hasCr = (lineEnd > i && v.charAt(lineEnd - 1) == 13);
                        int contentEnd = hasCr ? lineEnd - 1 : lineEnd;
                        if (contentEnd > i) {
                            for (int k = 0; k < n; k++) o.append(' ');
                            o.append(v, i, contentEnd);
                        }
                        if (hasCr) o.append((char) 13);
                        if (next != -1) {
                            o.append((char) 10);
                            i = next + 1;
                        } else {
                            break;
                        }
                    }
                    return o.toString();
                }

                // dedent: remove indentação comum mínima de linhas não-vazias; null -> null.
                public static String kof_strings_dedent(String v) {
                    if (v == null || v.isEmpty()) return v;
                    int len = v.length();
                    int minIndent = -1;
                    int i = 0;
                    while (i < len) {
                        int next = v.indexOf(10, i);
                        int lineEnd = (next == -1) ? len : next;
                        int contentEnd = (lineEnd > i && v.charAt(lineEnd - 1) == 13) ? lineEnd - 1 : lineEnd;
                        int ws = 0;
                        while (i + ws < contentEnd && (v.charAt(i + ws) == 32 || v.charAt(i + ws) == 9)) {
                            ws++;
                        }
                        if (i + ws < contentEnd) {
                            if (minIndent == -1 || ws < minIndent) minIndent = ws;
                        }
                        if (next == -1) break;
                        i = next + 1;
                    }
                    if (minIndent <= 0) return v;
                    StringBuilder o = new StringBuilder(v.length());
                    i = 0;
                    while (i < len) {
                        int next = v.indexOf(10, i);
                        int lineEnd = (next == -1) ? len : next;
                        boolean hasCr = (lineEnd > i && v.charAt(lineEnd - 1) == 13);
                        int contentEnd = hasCr ? lineEnd - 1 : lineEnd;
                        int ws = 0;
                        while (i + ws < contentEnd && ws < minIndent && (v.charAt(i + ws) == 32 || v.charAt(i + ws) == 9)) {
                            ws++;
                        }
                        o.append(v, i + ws, contentEnd);
                        if (hasCr) o.append((char) 13);
                        if (next != -1) {
                            o.append((char) 10);
                            i = next + 1;
                        } else {
                            break;
                        }
                    }
                    return o.toString();
                }
        """;
    }
}
