package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S10/S10a/S10b).
 * kof.random — entropia SEMPRE SecureRandom (R11: nunca primitivo caseiro).
 *
 * <p>Merge beta/main (10/09): os dois apelidos convivem — a face beta usa
 * {@code kof_random_bool}/{@code kof_random_string} (S10a/S10b) e a face
 * main usa {@code kof_random_double}/{@code kof_random_boolean}/
 * {@code kof_random_hex} (S10, matriz stdrandom). {@code kof_random_int} é
 * um só método (mesma semântica nos dois lados: bound<=0 => 0). Definir
 * {@code kof_random_int} duas vezes na classe gerada quebraria o build do
 * KofRuntime — por isso a fusão mora aqui, não em dois fragmentos.
 */
public final class JvmStringRandomRuntime {

    private JvmStringRandomRuntime() {}

    static String source() {
        return """

                // ── kof.random (STDLIB S10/S10a/S10b) — entropia = SecureRandom (R11) ──
                private static final java.security.SecureRandom KOF_RND =
                        new java.security.SecureRandom();

                // [0,1): SecureRandom.longToDoubleBits-like — 53 bits de
                // mantissa (2^53-1) / 2^53; nunca 1.0.
                public static double kof_random_double() {
                    long v = KOF_RND.nextLong() >>> 11;   // 53 bits
                    return v / (double) (1L << 53);
                }

                // Bool: 'boolean' (face main) e 'bool' (face beta) são o
                // MESMO contrato — um método, dois nomes no dispatch KofRandom.
                public static boolean kof_random_boolean() {
                    return KOF_RND.nextBoolean();
                }

                public static boolean kof_random_bool() {
                    return KOF_RND.nextBoolean();
                }

                // [0,bound); bound<=0 => 0 (contrato honesto, paridade x86
                // kof_sec_random_int / riscv B27 — face única pros dois apelidos).
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

                // S10b: n chars uniformes do alfabeto; n<=0 OU alfabeto
                // nula/vazia => "" (face leniente do plano).
                public static String kof_random_string(int n, String alphabet) {
                    if (n <= 0 || alphabet == null || alphabet.length() == 0) return "";
                    int len = alphabet.length();
                    StringBuilder sb = new StringBuilder(n);
                    for (int i = 0; i < n; i++) {
                        sb.append(alphabet.charAt(KOF_RND.nextInt(len)));
                    }
                    return sb.toString();
                }
        """;
    }
}
