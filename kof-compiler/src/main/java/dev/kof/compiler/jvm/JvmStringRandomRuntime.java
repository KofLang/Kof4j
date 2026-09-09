package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S10).
 * kof.random — entropia SEMPRE SecureRandom (R11: nunca primitivo caseiro).
 * Concatenado via JvmStringRuntime.source(); paridade com Native (getrandom,
 * RuntimeRandom x86 / B27 riscv) e JS (kofRandom*) é o que KofRandomTest
 * prova (shape: range/distribuição não-constante; matriz stdrandom).
 */
public final class JvmStringRandomRuntime {

    private JvmStringRandomRuntime() {}

    static String source() {
        return """

                // ── kof.random (STDLIB S10) — entropia = SecureRandom (R11) ──
                private static final java.security.SecureRandom KOF_RND =
                        new java.security.SecureRandom();

                // [0,1): SecureRandom.longToDoubleBits-like — 53 bits de
                // mantissa (2^53-1) / 2^53; nunca 1.0; null em falha não
                // ocorre no JVM (SecureRandom nextBytes sempre preenche).
                public static double kof_random_double() {
                    long v = KOF_RND.nextLong() >>> 11;   // 53 bits
                    return v / (double) (1L << 53);
                }

                public static boolean kof_random_boolean() {
                    return KOF_RND.nextBoolean();
                }

                // [0,bound); bound<=0 => 0 (contrato honesto, paridade x86
                // kof_sec_random_int / riscv B27).
                public static int kof_random_int(int bound) {
                    if (bound <= 0) return 0;
                    return KOF_RND.nextInt(bound);
                }

                // 2n hex minusculo (n bytes); n<=0 => null; mesmo contrato do
                // kof_sec_random_hex (reuso — não reimplementa entropia).
                public static String kof_random_hex(int n) {
                    if (n <= 0) return null;
                    return kof_sec_random_hex(n);
                }
        """;
    }
}
