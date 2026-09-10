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
 * <p>{@code randomDouble}/{@code randomBytes}/{@code randomChoice} ficam para
 * S10b: double exige o primeiro caminho de ponto-flutuante no runtime
 * cross-arch (FLT gate), bytes/choice alocam tipos que o backend asm puro
 * ainda não devolve (array / objeto de List indexado).
 */
public final class KofRandom {

    private KofRandom() {}

    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;

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
            default -> null;
        };
    }

    /**
     * S10a: as duas funções só precisam de getrandom (fatia B25 já portada) +
     * aritmética inteira — rodam nos 5 alvos sem gate. FLT (double) e
     * alocação (bytes/choice) chegam S10b com gate honesto próprio.
     */
    static boolean supportedOn(String function, Target target) {
        return true;
    }

    static String gapCode(String function) {
        return "RND001";
    }
}
