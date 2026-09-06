package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof_string->numerico (OBS-010) - parte 1/5 de JvmStringRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmStringCoreRuntime {

    private JvmStringCoreRuntime() {}

    static String source() {
        return """
                // ── String → numérico (OBS-010: toInt/toLong/toDouble/toFloat)
                // As conversões do String são funções do runtime Kof — o
                // java.lang.String não tem toInt().

                public static int kof_string_to_int(String s) {
                    return Integer.parseInt(s.trim());
                }

                public static long kof_string_to_long(String s) {
                    return Long.parseLong(s.trim());
                }

                public static double kof_string_to_double(String s) {
                    return Double.parseDouble(s.trim());
                }

                public static float kof_string_to_float(String s) {
                    return Float.parseFloat(s.trim());
                }
""";
    }
}
