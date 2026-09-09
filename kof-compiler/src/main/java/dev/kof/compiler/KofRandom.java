package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.random} (STDLIB S10, plan-stdlib-
 * expansion). Geração aleatória de propósito geral — entropia SEMPRE da
 * primitiva do SO (getrandom nos nativos, SecureRandom no JVM,
 * kof_platform.randomBytesHex no JS): NUNCA caseira (R11/regra 7).
 *
 * Semântica travada na matriz {@code stdrandom}:
 *  - random.double()  -> Double em [0,1); nunca 1.0; null em falha do SO.
 *  - random.boolean() -> Bool (bit do 1º byte).
 *  - random.int(bound) -> Int em [0,bound); bound<=0 => 0 (paridade randomInt
 *    de security — rejeição honesta, não silenciosa: bound é do caller).
 *  - random.hex(n)     -> String de 2n dígito hex minúsculo (n>=1; n<=0=>null).
 *
 * {@code choice} fica fora de S10 (devolver elemento de List = objeto
 * estruturado em asm — mesma decisão S8 do net; multi-sessão própria).
 */
public final class KofRandom {

    private KofRandom() {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;

    static final List<String> NAMESPACES = List.of("random");

    static boolean isRandomNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record RandomCall(String function, Type returnType, List<Type> parameterTypes) {}

    static RandomCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (!"random".equals(namespace)) return null;
        int argc = argTypes.size();
        return switch (name) {
            case "double" -> argc == 0
                    ? new RandomCall("kof_random_double", Type.PrimitiveType.DOUBLE, List.of()) : null;
            case "boolean" -> argc == 0
                    ? new RandomCall("kof_random_boolean", BOOL, List.of()) : null;
            case "int" -> argc == 1
                    ? new RandomCall("kof_random_int", INT, List.of(INT)) : null;
            case "hex" -> argc == 1
                    ? new RandomCall("kof_random_hex", STR, List.of(INT)) : null;
            default -> null;
        };
    }

    static boolean supportedOn(String function, Target target) {
        return true;
    }

    static String gapCode(String function) {
        return "RAND001";
    }
}
