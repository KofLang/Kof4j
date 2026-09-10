package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S3b-ext). kof.uuid
 * isUuid — shape RFC 4122 (8-4-4-4-12 hex, hífens fixos em 8/13/18/23).
 * Não valida versão/variante; concatena via JvmStringRuntime.source().
 */
public final class JvmUuidRuntime {

    private JvmUuidRuntime() {}

    static String source() {
        return """

                // isUuid (S3b-ext): shape RFC 4122 — 36 bytes: 32 hex +
                // hífens fixos em 8/13/18/23. null => false. Não checa
                // versão/variante (qualquer v1..v5 canônico => true).
                public static boolean kof_uuid_isUuid(String s) {
                    if (s == null || s.length() != 36) return false;
                    for (int i = 0; i < 36; i++) {
                        char c = s.charAt(i);
                        if (i == 8 || i == 13 || i == 18 || i == 23) {
                            if (c != '-') return false;
                        } else if (!((c >= '0' && c <= '9')
                                || (c >= 'a' && c <= 'f')
                                || (c >= 'A' && c <= 'F'))) {
                            return false;
                        }
                    }
                    return true;
                }
        """;
    }
}
