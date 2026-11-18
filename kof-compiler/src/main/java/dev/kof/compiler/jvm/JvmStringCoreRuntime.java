package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof_string->numerico (OBS-010) - parte 1/5 de JvmStringRuntime. Concatenacao preserva byte-a-byte.
 */
public final class JvmStringCoreRuntime {

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

                // §742 / D-SPLIT-LITERAL: o split Kof tem separador LITERAL
                // (nunca regex) — a regra de vazios-trailing/limit do §111/§655
                // é a do String.split(regex,0/limit) do JDK, então a produção
                // replica a SEMÂNTICA do split do JDK sobre um padrão quotado
                // (Pattern.quote) sem construir o Pattern: o mesmo resultado,
                // sem a armadilha dos metacaracteres ("a.b" → [a,b], "a|b" →
                // [a,b], "a[b" → [a,b] — o PatternSyntaxException morre).
                public static String[] kof_string_split_literal(String s, String sep) {
                    if (s == null) return new String[0];
                    if (s.isEmpty()) return new String[]{""};
                    String q = java.util.regex.Pattern.quote(sep == null ? "" : sep);
                    return s.split(q, 0);
                }

                public static String[] kof_string_split_literal_limit(String s, String sep, int limit) {
                    if (s == null) return new String[0];
                    if (s.isEmpty()) return new String[]{""};
                    String q = java.util.regex.Pattern.quote(sep == null ? "" : sep);
                    return s.split(q, limit);
                }

                // S13b (plan-stdlib-expansion §2, P0): parse com default —
                // briefing §43 ("falha de parse = OrNull/OrDefault"). Mesmo
                // contrato do parse (JDK + trim); falha DEVOLVE o default
                // (nunca lança). Paridade byte-idêntica com os wrappers asm
                // x86 (RuntimeStringParseOrDefault) e riscv (B34).
                public static int kof_string_to_int_or_default(String s, int def) {
                    try { return Integer.parseInt(s.trim()); }
                    catch (RuntimeException e) { return def; }
                }

                public static long kof_string_to_long_or_default(String s, long def) {
                    try { return Long.parseLong(s.trim()); }
                    catch (RuntimeException e) { return def; }
                }

                public static double kof_string_to_double_or_default(String s, double def) {
                    try { return Double.parseDouble(s.trim()); }
                    catch (RuntimeException e) { return def; }
                }
""";
    }
}
