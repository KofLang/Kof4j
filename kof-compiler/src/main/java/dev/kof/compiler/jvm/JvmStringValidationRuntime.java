package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.validation (G4) - parte 3/5 de JvmStringRuntime. Concatenacao preserva byte-a-byte.
 */
public final class JvmStringValidationRuntime {

    private JvmStringValidationRuntime() {}

    static String source() {
        return """

                // ── kof.validation (G4) ─────────────────────────────────

                public static boolean kof_validation_required(String value) {
                    return value != null && !value.isEmpty();
                }

                public static boolean kof_validation_notBlank(String value) {
                    return value != null && !value.trim().isEmpty();
                }

                public static boolean kof_validation_minLength(String value, int min) {
                    return value != null && value.length() >= min;
                }

                public static boolean kof_validation_maxLength(String value, int max) {
                    return value != null && value.length() <= max;
                }

                public static boolean kof_validation_lengthBetween(String value, int min, int max) {
                    return value != null && value.length() >= min && value.length() <= max;
                }

                public static boolean kof_validation_isEmail(String value) {
                    if (value == null) return false;
                    if (value.indexOf(' ') >= 0 || value.indexOf(9) >= 0 || value.indexOf(10) >= 0) return false;
                    int at = value.indexOf('@');
                    if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) return false;
                    String domain = value.substring(at + 1);
                    int dot = domain.indexOf('.');
                    if (dot <= 0 || dot == domain.length() - 1) return false;
                    return true;
                }

                public static boolean kof_validation_isUrl(String value) {
                    if (value == null) return false;
                    return value.startsWith("http://") || value.startsWith("https://");
                }

                public static boolean kof_validation_matches(String value, String pattern) {
                    if (value == null || pattern == null) return false;
                    try { return java.util.regex.Pattern.compile(pattern).matcher(value).find(); } catch (Exception e) { return false; }
                }

                public static boolean kof_validation_isInt(String value) {
                    if (value == null) return false;
                    try { Integer.parseInt(value.trim()); return true; } catch (Exception e) { return false; }
                }

                public static boolean kof_validation_isLong(String value) {
                    if (value == null) return false;
                    try { Long.parseLong(value.trim()); return true; } catch (Exception e) { return false; }
                }

                public static boolean kof_validation_inRange(int value, int min, int max) {
                    return value >= min && value <= max;
                }

                public static boolean kof_validation_min(int value, int min) {
                    return value >= min;
                }

                public static boolean kof_validation_max(int value, int max) {
                    return value <= max;
                }

                // ── kof.validation (STDLIB S5) — documentos BR ──────────────
                // Dígitos extraídos (não-dígitos ignorados); módulo 11.
                private static int[] kof_br_digits(String s) {
                    if (s == null) return new int[0];
                    int n = 0;
                    for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c >= '0' && c <= '9') n++; }
                    int[] d = new int[n];
                    int k = 0;
                    for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c >= '0' && c <= '9') d[k++] = c - '0'; }
                    return d;
                }

                public static boolean kof_validation_isCpf(String s) {
                    int[] d = kof_br_digits(s);
                    if (d.length != 11) return false;
                    boolean allSame = true;
                    for (int i = 1; i < 11; i++) if (d[i] != d[0]) { allSame = false; break; }
                    if (allSame) return false;
                    int r1 = 0, r2 = 0;
                    for (int i = 0; i < 9; i++) r1 += d[i] * (10 - i);
                    r1 %= 11; int dv1 = r1 < 2 ? 0 : 11 - r1;
                    if (dv1 != d[9]) return false;
                    for (int i = 0; i < 10; i++) r2 += d[i] * (11 - i);
                    r2 %= 11; int dv2 = r2 < 2 ? 0 : 11 - r2;
                    return dv2 == d[10];
                }

                public static boolean kof_validation_isCnpj(String s) {
                    int[] d = kof_br_digits(s);
                    if (d.length != 14) return false;
                    int[] w1 = {5,4,3,2,9,8,7,6,5,4,3,2};
                    int[] w2 = {6,5,4,3,2,9,8,7,6,5,4,3,2};
                    int r1 = 0, r2 = 0;
                    for (int i = 0; i < 12; i++) r1 += d[i] * w1[i];
                    r1 %= 11; int dv1 = r1 < 2 ? 0 : 11 - r1;
                    if (dv1 != d[12]) return false;
                    for (int i = 0; i < 13; i++) r2 += d[i] * w2[i];
                    r2 %= 11; int dv2 = r2 < 2 ? 0 : 11 - r2;
                    return dv2 == d[13];
                }

                public static boolean kof_validation_isCep(String s) {
                    int[] d = kof_br_digits(s);
                    return d.length == 8;
                }

                public static boolean kof_validation_isPis(String s) {
                    int[] d = kof_br_digits(s);
                    if (d.length != 11) return false;
                    int[] w = {3,2,9,8,7,6,5,4,3,2};
                    int r = 0;
                    for (int i = 0; i < 10; i++) r += d[i] * w[i];
                    r %= 11; int dv = r < 2 ? 0 : 11 - r;
                    return dv == d[10];
                }

""";
    }
}
