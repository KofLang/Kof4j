package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.strings} (STDLIB S2).
 *
 * S2a = predicados + contagem (retornam Bool/Int, sem alocar String nova —
 * paridade byte-idêntica em JVM/Native/JS/interpreter via loop de chars).
 * Convergemos apenas o que NÃO colide com métodos String já existentes
 * (toUpperCase/toLowerCase/trim/replace/contains/split/substring/indexOf
 * já existem em StringMethodRegistry) nem com validation.isInt/isLong.
 * S2b (conversores de caixa / pad / reverse / slugify — aloca String) vem depois.
 */
public final class KofStrings {

    private KofStrings() {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type INT = Type.PrimitiveType.INT;

    static final List<String> NAMESPACES = List.of("strings");

    static boolean isStringsNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record StringsCall(String function, Type returnType, List<Type> parameterTypes) {}

    static StringsCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (!"strings".equals(namespace)) return null;
        int argc = argTypes.size();
        // S2a: predicados de classe pura de char (String→Bool), todos no padrão
        // "não-vazio && todo byte na classe". Semântica ASCII fixada na matriz
        // stdstrings. S2a.3: count (ocorrências NÃO-sobrepostas; "" => 0);
        // S2a.4: isUpperCase/isLowerCase exigem ≥1 letra e todas as letras na
        // caixa (outros chars ignorados). Conversores (alocam String) = S2b.
        return switch (name) {
            case "isAlpha", "isNumeric", "isAlphaNumeric", "isAscii",
                    "isUpperCase", "isLowerCase" -> argc == 1
                    ? new StringsCall("kof_strings_" + name, BOOL, List.of(STR)) : null;
            case "count" -> argc == 2
                    ? new StringsCall("kof_strings_count", INT, List.of(STR, STR)) : null;
            default -> null;
        };
    }

    /** S2a (predicados/count) presentes em todos os targets. */
    static boolean supportedOn(String function, Target target) {
        return true;
    }

    static String gapCode(String function) {
        return "STR001";
    }
}
