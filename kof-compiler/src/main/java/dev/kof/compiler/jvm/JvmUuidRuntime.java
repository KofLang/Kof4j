package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S3b/S3b.1).
 * kof.uuid: v4 (RFC 4122, SecureRandom) + isUuid (predicado de forma
 * 8-4-4-4-12). Extraído de JvmStringMathRuntime (gate ≤500 — uuid não é
 * math); concatenado em JvmStringRuntime.source() após o fragmento math
 * (métodos em classe única — ordem é semântica invariante; sem golden).
 */
public final class JvmUuidRuntime {

    private JvmUuidRuntime() {}

    static String source() {
        return """
                // ── kof.uuid (STDLIB S3b) — v4 (RFC 4122) ─────────────────
                private static final java.security.SecureRandom KOF_UUID_RANDOM =
                        new java.security.SecureRandom();
                // 16 bytes aleatorios -> 8-4-4-4-12, version=4, variant=10xx.
                public static String kof_uuid_v4() {
                    byte[] b = new byte[16];
                    KOF_UUID_RANDOM.nextBytes(b);
                    b[6] = (byte) ((b[6] & 0x0f) | 0x40);   // version 4
                    b[8] = (byte) ((b[8] & 0x3f) | 0x80);   // variant 10
                    final char[] H = "0123456789abcdef".toCharArray();
                    char[] c = new char[36];
                    int k = 0;
                    for (int i = 0; i < 16; i++) {
                        c[k++] = H[(b[i] >> 4) & 15];
                        c[k++] = H[b[i] & 15];
                        if (i == 3 || i == 5 || i == 7 || i == 9) c[k++] = '-';
                    }
                    return new String(c);
                }

                // isUuid: forma canônica 8-4-4-4-12 (36 chars, traços em
                // 8/13/18/23, demais hex; maiúsculas aceitas; version/variant
                // NÃO verificadas — predicado de forma, paridade travada na matriz).
                public static boolean kof_uuid_isUuid(String v) {
                    if (v == null || v.length() != 36) return false;
                    for (int i = 0; i < 36; i++) {
                        char c = v.charAt(i);
                        if (i == 8 || i == 13 || i == 18 || i == 23) {
                            if (c != '-') return false;
                        } else if (!isUuidHex(c)) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean isUuidHex(char c) {
                    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
                }
        """;
    }
}
