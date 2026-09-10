package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.random} (STDLIB S10, plan-stdlib-
 * expansion). Geração aleatória de propósito geral — entropia SEMPRE da
 * primitiva do SO (getrandom nos nativos, SecureRandom no JVM,
 * kof_platform.randomBytesHex no JS): NUNCA caseira (R11/regra 7).
 *
 * <p>Merge beta/main (10/09): os dois apelidos de cada função convivem —
 * a face beta usa {@code randomInt}/{@code randomBoolean}/{@code randomString}
 * (S10a/S10b, 5 alvos, com a fatia riscv B28) e a face main usa
 * {@code random.double}/{@code random.boolean}/{@code random.int}/{@code random.hex}
 * (S10, matriz stdrandom). Nenhuma chamada antiga quebra (retrocompat aditiva).
 *
 * Semântica travada na matriz {@code stdrandom}:
 *  - random.double()  -> Double em [0,1); nunca 1.0.
 *  - random.boolean() -> Bool (bit do 1º byte).
 *  - random.int(bound) -> Int em [0,bound); bound<=0 => 0 (face leniente;
 *    security.randomInt lança — paridade honesta, não silenciosa).
 *  - random.hex(n)     -> String de 2n dígito hex minúsculo (n>=1; n<=0=>null).
 *  - randomString(n, alphabet) -> n chars uniformes do alfabeto (S10b).
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
            // face main (S10): double/boolean/int/hex
            case "double" -> argc == 0
                    ? new RandomCall("kof_random_double", Type.PrimitiveType.DOUBLE, List.of()) : null;
            case "boolean" -> argc == 0
                    ? new RandomCall("kof_random_boolean", BOOL, List.of()) : null;
            case "int" -> argc == 1
                    ? new RandomCall("kof_random_int", INT, List.of(INT)) : null;
            case "hex" -> argc == 1
                    ? new RandomCall("kof_random_hex", STR, List.of(INT)) : null;
            // face beta (S10a/S10b): randomInt/randomBoolean/randomString
            case "randomInt" -> argc == 1 && argTypes.get(0) == INT
                    ? new RandomCall("kof_random_int", INT, List.of(INT)) : null;
            case "randomBoolean" -> argc == 0
                    ? new RandomCall("kof_random_bool", BOOL, List.of()) : null;
            case "randomString" -> argc == 2 && argTypes.get(0) == INT && argTypes.get(1) == STR
                    ? new RandomCall("kof_random_string", STR, List.of(INT, STR)) : null;
            default -> null;
        };
    }

    /**
     * S10/S10a/S10b: getrandom (primitiva SECN000/B25) + aritmética inteira +
     * String — rodam nos 5 alvos sem gate. O double usa a convenção FP do
     * cross-emit (fcvt.d.l → f0 → fmv.x.d → t0), já portada para aarch64
     * (divu/remu/fcvt/fld/fmv no tradutor). FLT (novo tipo de dados) fica
     * S1b; choice (objeto estruturado) S10c.
     */
    static boolean supportedOn(String function, Target target) {
        return true;
    }

    static String gapCode(String function) {
        return "RND001";
    }
}
