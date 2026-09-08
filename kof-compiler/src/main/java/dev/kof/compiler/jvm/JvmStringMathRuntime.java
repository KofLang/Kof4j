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
        """;
    }
}
