package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.validation (G4) - parte 3/5 de JvmStringRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmStringValidationRuntime {

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

""";
    }
}
