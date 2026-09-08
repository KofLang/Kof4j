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
        """;
    }
}
