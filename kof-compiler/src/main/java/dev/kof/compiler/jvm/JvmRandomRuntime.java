package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S10a).
 * kof.random — face NÃO-críptográfica: randomInt/randomBoolean. Entropia
 * do SO (SecureRandom — mesma fonte de kof_sec_random_int). Contrato
 * "leniente" documentado no plano: bound <= 0 retorna 0 (diferença de
 * intenção vs security.randomInt, que lança — random é para shuffle/
 * sorteio, não para gate de segurança; x86 alia kof_sec_random_int e já
 * retorna 0 nesse caso, JVM/JS seguem o mesmo shape p/ paridade).
 * Concatenado em JvmStringRuntime.source().
 */
public final class JvmRandomRuntime {

    private JvmRandomRuntime() {}

    static String source() {
        return """

                // ── kof.random (STDLIB S10a) ──────────────────────────────

                public static int kof_random_int(int bound) {
                    if (bound <= 0) return 0;
                    return KOF_SEC_RANDOM.nextInt(bound);
                }

                public static boolean kof_random_bool() {
                    return KOF_SEC_RANDOM.nextBoolean();
                }
""";
    }
}
