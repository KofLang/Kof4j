package dev.kof.compiler;

import java.util.List;

/**
 * X8 fatia 1 (IMPLEMENTATION-UNIVERSAL-PLATFORM.md, fila X) — namespace
 * {@code rng}: PRNG SEMEÁVEL (xorshift128, Marsaglia) para property-based
 * testing (R10). Contrato central: MESMA seed => MESMA sequência em qualquer
 * backend — o algoritmo usa só xor/shift e multiplicação mod 2^32 (splitmix32
 * no seed), então JVM e JS (Math.imul) produzem bits idênticos por construção.
 *
 * <p>NUNCA usar para chaves/tokens/segredos: isso é {@code random}/
 * {@code security} (entropia do SO, R11 — nunca primitivo caseiro). O rng é
 * o inverso deliberado: determinismo reprodutível.
 *
 * <p>Fatia 2 = JVM + JS + NATIVE x86_64 + ANDROID (R7 honesto; android reusa o
 * runtime JVM desde 07/10, issue #777). Cross riscv64/aarch64: gap diagnóstico
 * {@code RNG001} (nada de fallback silencioso, R6).
 *
 * <p>Contratos lenientes (paridade com random.int): bound &lt;= 0 =&gt; 0;
 * string com n &lt;= 0 ou alfabeto vazio =&gt; "". double em [0,1) com 52 bits
 * de mantissa — representação exata em IEEE 754 duplo nos dois backends.
 */
public final class KofRng {

    private KofRng() {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;

    record RngCall(String function, Type returnType, List<Type> parameterTypes) {}

    static final List<String> NAMESPACES = List.of("rng");

    static boolean isRngNamespace(String name) {
        return NAMESPACES.contains(name);
    }


    /** X10 fatia 1: nomes aceitos pelo staticMethod (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case literals do switch(name) abaixo. */
    static List<String> functions() { return List.of("seed", "int", "boolean", "double", "string"); }
    static RngCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (!"rng".equals(namespace)) return null;
        int argc = argTypes.size();
        return switch (name) {
            case "seed" -> argc == 1 && argTypes.get(0) == INT
                    ? new RngCall("kof_rng_seed", Type.PrimitiveType.VOID, List.of(INT)) : null;
            case "int" -> argc == 1 && argTypes.get(0) == INT
                    ? new RngCall("kof_rng_int", INT, List.of(INT)) : null;
            case "boolean" -> argc == 0
                    ? new RngCall("kof_rng_bool", BOOL, List.of()) : null;
            case "double" -> argc == 0
                    ? new RngCall("kof_rng_double", Type.PrimitiveType.DOUBLE, List.of()) : null;
            case "string" -> argc == 2 && argTypes.get(0) == INT && argTypes.get(1) == STR
                    ? new RngCall("kof_rng_string", STR, List.of(INT, STR)) : null;
            default -> null;
        };
    }

    /**
     * Fatia 2 (R7): JVM + JS + NATIVE x86_64 (RuntimeRng — mesmos bits por
     * construção); cross riscv64/aarch64 = RNG001 (gap honesto — port riscv
     * com qemu na fatia 3).
     *
     * <p>ANDROID entra na fatia 2 (07/10, issue #777): o alvo reusa o
     * {@code JvmBackend} e o MESMO {@code KofRuntime} gerado (inclui
     * {@code JvmStringRngRuntime}); como o rng usa só ops int 32-bit
     * (xor/shift/mul com wrap), os bits são idênticos ao JVM por construção —
     * provado em {@code KofRngTest.androidMatchesOracle} (bytecode Android
     * executado no host produz a mesma sequência). O ART executa o mesmo
     * bytecode.
     */
    static boolean supportedOn(@SuppressWarnings("unused") String function, Target target) {
        return target == Target.JVM || target == Target.JS || target == Target.NATIVE
                || target == Target.ANDROID;
    }

    static String gapCode(@SuppressWarnings("unused") String function) {
        return "RNG001";
    }
}
