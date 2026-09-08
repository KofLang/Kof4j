package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S1).
 * kof.math (P0-a) - parte Int-only: clamp/abs/sign/min/max + predicados.
 * Concatenado em JvmStringRuntime.source(); paridade byte-idêntica com
 * Native (RuntimeMath) e JS (kofMath*) é o que KofMathTest prova.
 */
public final class JvmStringMathRuntime {

    private JvmStringMathRuntime() {}

    static String source() {
        return """

                // ── kof.math (STDLIB S1) ──────────────────────────────────

                public static int kof_math_abs(int v) {
                    return v < 0 ? -v : v;
                }

                public static int kof_math_sign(int v) {
                    return v > 0 ? 1 : (v < 0 ? -1 : 0);
                }

                public static int kof_math_clamp(int v, int lo, int hi) {
                    return v < lo ? lo : (v > hi ? hi : v);
                }

                public static int kof_math_min(int a, int b) {
                    return a <= b ? a : b;
                }

                public static int kof_math_max(int a, int b) {
                    return a >= b ? a : b;
                }

                public static boolean kof_math_isEven(int v) {
                    return (v & 1) == 0;
                }

                public static boolean kof_math_isOdd(int v) {
                    return (v & 1) != 0;
                }

                public static boolean kof_math_isPositive(int v) {
                    return v > 0;
                }

                public static boolean kof_math_isNegative(int v) {
                    return v < 0;
                }

                public static boolean kof_math_isZero(int v) {
                    return v == 0;
                }

                // ── kof.strings (STDLIB S2a) — predicados de char ──────────
                // Convenção de paridade (travada em KofStringsTest + matriz):
                // string vazia / null => false (nenhum char satisfaz).

                public static boolean kof_strings_isAlpha(String v) {
                    if (v == null || v.isEmpty()) return false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
                    }
                    return true;
                }

                public static boolean kof_strings_isNumeric(String v) {
                    if (v == null || v.isEmpty()) return false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (c < '0' || c > '9') return false;
                    }
                    return true;
                }

                public static boolean kof_strings_isAlphaNumeric(String v) {
                    if (v == null || v.isEmpty()) return false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                                  || (c >= '0' && c <= '9');
                        if (!ok) return false;
                    }
                    return true;
                }

                public static boolean kof_strings_isAscii(String v) {
                    if (v == null || v.isEmpty()) return false;
                    for (int i = 0; i < v.length(); i++) {
                        if (v.charAt(i) >= 128) return false;
                    }
                    return true;
                }

                public static boolean kof_strings_isUpperCase(String v) {
                    if (v == null || v.isEmpty()) return false;
                    boolean hasLetter = false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (c >= 'a' && c <= 'z') return false;
                        if (c >= 'A' && c <= 'Z') hasLetter = true;
                    }
                    return hasLetter;
                }

                public static boolean kof_strings_isLowerCase(String v) {
                    if (v == null || v.isEmpty()) return false;
                    boolean hasLetter = false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (c >= 'A' && c <= 'Z') return false;
                        if (c >= 'a' && c <= 'z') hasLetter = true;
                    }
                    return hasLetter;
                }

                // Ocorrências NÃO-sobrepostas; vazio/null em qualquer lado => 0.
                public static int kof_strings_count(String v, String sub) {
                    if (v == null || sub == null || sub.isEmpty() || v.isEmpty()) return 0;
                    int n = 0, i = 0;
                    while ((i = v.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
                    return n;
                }

                // ASCII-only: paridade byte-idêntica com Native (byte[0] a-z).
                public static String kof_strings_capitalize(String v) {
                    if (v == null || v.isEmpty()) return v;
                    char c = v.charAt(0);
                    if (c >= 'a' && c <= 'z') return (char) (c - 32) + v.substring(1);
                    return v;
                }

                public static String kof_strings_reverse(String v) {
                    if (v == null) return null;
                    return new StringBuilder(v).reverse().toString();
                }

                public static String kof_strings_repeat(String v, int n) {
                    if (v == null || v.isEmpty() || n <= 0) return "";
                    StringBuilder sb = new StringBuilder(v.length() * n);
                    for (int i = 0; i < n; i++) sb.append(v);
                    return sb.toString();
                }

                public static String kof_strings_truncate(String v, int n) {
                    if (v == null) return null;
                    if (n <= 0) return "";
                    return v.length() <= n ? v : v.substring(0, n);
                }

                // pad = 1ª char do 3º arg; sem pad (null/"") ou len>=n => original.
                public static String kof_strings_padLeft(String v, int n, String pad) {
                    if (v == null) return null;
                    if (pad == null || pad.isEmpty() || v.length() >= n) return v;
                    char p = pad.charAt(0);
                    StringBuilder sb = new StringBuilder(n);
                    for (int i = v.length(); i < n; i++) sb.append(p);
                    return sb.append(v).toString();
                }

                public static String kof_strings_padRight(String v, int n, String pad) {
                    if (v == null) return null;
                    if (pad == null || pad.isEmpty() || v.length() >= n) return v;
                    char p = pad.charAt(0);
                    StringBuilder sb = new StringBuilder(v);
                    for (int i = v.length(); i < n; i++) sb.append(p);
                    return sb.toString();
                }

                // ── kof.strings (STDLIB S2b.4) — split+join de palavras ──
                // Words: sequência de [0-9A-Za-z] ASCII; boundary em: primeiro
                // alnum após não-alnum, lower/digit→Upper, e Upper→Upper lower
                // ("HTTPServer" = http+Server; "XMLParser" = xml+Parser).
                // >=128 (não-ASCII) é delimitador/ignorado — paridade com o
                // byte-a-byte do Native (NAT-STR01 documentado).
                // mode: 0=camel 1=pascal 2=snake 3=kebab 4=slug.
                private static String kof_strings_joinWords(String v, int mode) {
                    if (v == null) return null;
                    StringBuilder out = new StringBuilder();
                    int wc = 0;
                    int prev = -1;
                    for (int i = 0; i < v.length(); i++) {
                        int c = v.charAt(i);
                        boolean upper = c >= 65 && c <= 90;
                        boolean alnum = upper || (c >= 97 && c <= 122) || (c >= 48 && c <= 57);
                        if (!alnum) { prev = -1; continue; }
                        boolean nw = false;
                        if (prev == -1) nw = true;
                        else if (upper) {
                            boolean pl = prev >= 97 && prev <= 122;
                            boolean pd = prev >= 48 && prev <= 57;
                            boolean pu = prev >= 65 && prev <= 90;
                            boolean nl = false;
                            if (i + 1 < v.length()) {
                                int nx = v.charAt(i + 1);
                                nl = nx >= 97 && nx <= 122;
                            }
                            nw = pl || pd || (pu && nl);
                        }
                        if (nw) {
                            if (out.length() > 0 && mode >= 2) out.append((char) (mode == 2 ? '_' : '-'));
                            boolean cap = mode == 1 || (mode == 0 && wc > 0);
                            out.append((char) (cap ? (c >= 97 ? c - 32 : c) : (c >= 65 && c <= 90 ? c + 32 : c)));
                            wc++;
                        } else {
                            out.append((char) (c >= 65 && c <= 90 ? c + 32 : c));
                        }
                        prev = c;
                    }
                    return out.toString();
                }

                public static String kof_strings_toCamelCase(String v) { return kof_strings_joinWords(v, 0); }
                public static String kof_strings_toPascalCase(String v) { return kof_strings_joinWords(v, 1); }
                public static String kof_strings_toSnakeCase(String v) { return kof_strings_joinWords(v, 2); }
                public static String kof_strings_toKebabCase(String v) { return kof_strings_joinWords(v, 3); }
                public static String kof_strings_slugify(String v) { return kof_strings_joinWords(v, 4); }
        """;
    }
}
