package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.random} (STDLIB S10, plan-stdlib-
 * expansion). Split de intenção já documentado no plano: {@code random.*} é a
 * face NÃO-críptográfica; a face segura vive em {@code security.*} (já existe).
 *
 * <p>v1 (S10a) entrega: {@code randomInt(Int)} e {@code randomBoolean()}. A
 * entropia é SEMPRE a primitiva do SO (getrandom — mesma fonte do uuid B25 e
 * do {@code security.randomInt}); nada de PRNG caseiro (R11). Bound usa
 * multiply-shift (Lemire reduzido) para não exigir divisão unsigned no asm
 * (o tradutor aarch64 não tem {@code divu}); distribuição uniforme o
 * suficiente para uso não-críptográfico (shuffle, sorteio, teste).
 *
 * <p>S10b: {@code randomString(Int n, String alphabet)} — n chars, cada um
 * uniforme do alfabeto (reusa randomInt). Semântica de borda (mesma face
 * leniente): n<=0 OU alfabeto nula/vazia → "". (O plano lista alphabet
 * opcional; a forma explícita é a que os 5 backends portam agora — a
 * sobrecarga com default é aditiva e trivial sobre esta.)
 *
 * <p>{@code randomDouble} fica S1b (FLT001: probe 09/09 — nem {@code 1.5*2.0}
 * compila em riscv64/aarch64; double não existe no asm puro).
 * {@code randomBytes}/{@code randomChoice} (retorno Array/objeto — sem
 * precedente em stdlib, decisão de design) ficam S10c.
 */
public final class KofRandom {

    private KofRandom() {}

    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type STR = BuiltinTypes.STRING;

    static final List<String> NAMESPACES = List.of("random");

    static boolean isRandomNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record RandomCall(String function, Type returnType, List<Type> parameterTypes) {}

    static RandomCall staticMethod(String namespace, String name, List<Type> argTypes) {
        return switch (name) {
            case "randomInt" -> argTypes.size() == 1 && argTypes.get(0) == INT
                    ? new RandomCall("kof_random_int", INT, List.of(INT)) : null;
            case "randomBoolean" -> argTypes.isEmpty()
                    ? new RandomCall("kof_random_bool", BOOL, List.of()) : null;
            case "randomString" -> argTypes.size() == 2 && argTypes.get(0) == INT
                    && argTypes.get(1) == STR
                    ? new RandomCall("kof_random_string", STR, List.of(INT, STR)) : null;
            default -> null;
        };
    }

    /**
     * S10a+S10b: getrandom (fatia B25/B27) + aritmética inteira + String
     * (machine kof_sec_random_hex x86 / B25 riscv) — rodam nos 5 alvos sem
     * gate. FLT (double) chega S1b; alocação (bytes/choice) S10c.
     */
    static boolean supportedOn(String function, Target target) {
        return true;
    }

    static String gapCode(String function) {
        return "RND001";
    }
}
