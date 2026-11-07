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

    // known-bugs §657: as guardas de tipo dos params (espelho KofMath/KofTime)
    private static boolean isStr(Type t) {
        return STR.equals(t) || "String".equals(t.toString());
    }

    private static boolean isChar(Type t) {
        return t == Type.PrimitiveType.CHAR;
    }

    private static boolean isInt(Type t) {
        return t == INT || "Int".equals(t.toString()) || "int".equals(t.toString());
    }
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type INT = Type.PrimitiveType.INT;

    static final List<String> NAMESPACES = List.of("strings");

    static boolean isStringsNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record StringsCall(String function, Type returnType, List<Type> parameterTypes) {}


    /** X10 fatia 1: nomes aceitos pelo staticMethod (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case literals do switch(name) abaixo. */
    // 19/09 LSP-A fatia 3: DRIFT MAIOR achado — o dispatcher binda 26 nomes
    // (familias de case ligadas por virgula), a lista so tinha o primeiro literal
    // de cada case: 19 membros invisiveis no completion/hover (X10 mentia por
    // omissao). Lista = fonte do staticMethod abaixo, travada pelo lock de
    // virgulas no StdCatalogTest.
    static List<String> functions() {
        return List.of("isAlpha", "isNumeric", "isAlphaNumeric", "isAscii",
                "isUpperCase", "isLowerCase", "count", "capitalize", "uncapitalize",
                "reverse", "toCamelCase", "toPascalCase", "toSnakeCase", "toKebabCase",
                "slugify", "escapeHtml", "unescapeHtml", "escapeJson", "removeWhitespace",
                "normalizeWhitespace", "dedent", "repeat", "truncate", "indent",
                "padLeft", "padRight");
    }
    static StringsCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (!"strings".equals(namespace)) return null;
        int argc = argTypes.size();
        // S2a: predicados de classe pura de char (String→Bool), todos no padrão
        // "não-vazio && todo byte na classe". Semântica ASCII fixada na matriz
        // stdstrings. S2a.3: count (ocorrências NÃO-sobrepostas; "" => 0);
        // S2a.4: isUpperCase/isLowerCase exigem ≥1 letra e todas as letras na
        // caixa (outros chars ignorados).
        // S2b wedge: capitalize/reverse alocam String nova. capitalize é
        // Unicode no JVM/JS (Character.toUpperCase/toReversed) e ASCII-first
        // no Native (byte 0-255): a matriz stdstrings2b trava só ASCII
        // (paridade real nos 4) — gap NAT-STR01. reverse é por CODE POINT em
        // todos os targets (x86 RuntimeStringsConv / riscv NativeRiscvAsmRtB7,
        // aarch via tradutor) e trava o não-ASCII em stdstrings2b2 +
        // NativeStringsReverseCrossTest (D-FULL-PARITY-050 linha 11).
        return switch (name) {
            case "isAlpha", "isNumeric", "isAlphaNumeric", "isAscii",
                    "isUpperCase", "isLowerCase" -> argc == 1
                    ? new StringsCall("kof_strings_" + name, BOOL, List.of(STR)) : null;
            // known-bugs §657: guardas de tipo nos params INT/STR (a ligação
            // era só-por-arity — strings.repeat("ab", 5e9) alocava ~5 bi chars)
            case "count" -> argc == 2 && isStr(argTypes.get(1))
                    ? new StringsCall("kof_strings_count", INT, List.of(STR, STR)) : null;
            case "capitalize", "uncapitalize", "reverse", "toCamelCase", "toPascalCase",
                    "toSnakeCase", "toKebabCase", "slugify" -> argc == 1
                    ? new StringsCall("kof_strings_" + name, STR, List.of(STR)) : null;
            // S2b.2: preencher/encurtar (String,Int→String). null=>null;
            // repeat n<=0 ou vazio => ""; truncate n<=0 => "", n>=len => original.
            // S3.1 (STDLIB): HTML escape/unescape (5 entidades nomeadas +
            // numéricos no decode). escapeHtml saída só ASCII (>=128 cópia).
            case "escapeHtml", "unescapeHtml", "escapeJson", "removeWhitespace", "normalizeWhitespace",
                    "dedent" -> argc == 1
                    ? new StringsCall("kof_strings_" + name, STR, List.of(STR)) : null;
            case "repeat", "truncate" -> argc == 2 && isStr(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? new StringsCall("kof_strings_" + name, STR, List.of(STR, INT)) : null;
            // #1120: strings.join(xs, sep) — the SAME runtime helper List.join
            // uses (kof_list_join(List, sep)); the arg must be a List.
            case "join" -> argc == 2 && argTypes.get(0) instanceof Type.ClassType ct
                    && "List".equals(ct.name())
                    ? new StringsCall("kof_list_join", STR, List.of(argTypes.get(0), STR)) : null;
            case "indent" -> argc == 2 && isStr(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? new StringsCall("kof_strings_indent", STR, List.of(STR, INT)) : null;
            // S2b.3: pad(String,Int,String) — pad é a 1ª char do 3º arg (idiom
            // Kof: escreve "*", não o código Int). null=>null; pad vazio/null ou
            // len>=n => original. ASCII travado na matriz (pad ainda byte-based
            // no Native — gap NAT-STR01).
            case "padLeft", "padRight" -> argc == 3 && isStr(argTypes.get(0))
                    && isInt(argTypes.get(1)) && (isStr(argTypes.get(2)) || isChar(argTypes.get(2)))
                    // literal CHAR é pad válido (o runtime usa a 1ª char do 3º
                    // arg — idiom pré-guard; regressão achada pela validação da
                    // release 0.6.2 no CI)
                    ? new StringsCall("kof_strings_" + name, STR, List.of(STR, INT, STR)) : null;
            default -> null;
        };
    }

    /**
     * S2a/S2b (predicados/count/capitalize/reverse/repeat/truncate/pad) em
     * todos os targets. STRN001 FECHADO 09/09: os conversores de palavras
     * (joinWords — lógica complexa de boundary) foram portados p/ riscv64
     * (fatia B15) e o aarch64 é o mesmo asm via tradutor — paridade
     * byte-a-byte do golden x86 travada no qemu; nenhum caminho é gated.
     */
    private static final java.util.Set<String> WORD_FNS = java.util.Set.of(
            "kof_strings_toCamelCase", "kof_strings_toPascalCase",
            "kof_strings_toSnakeCase", "kof_strings_toKebabCase", "kof_strings_slugify");

    static boolean supportedOn(@SuppressWarnings("unused") String function,
            @SuppressWarnings("unused") Target target) {
        // STRN001 FECHADO 09/09: os conversores de palavra (joinWords) foram
        // portados p/ riscv64 (fatia B15) e o aarch64 é o MESMO asm traduzido —
        // paridade byte-a-byte travada por diff do golden oracle x86 no qemu.
        return true;
    }

    static String gapCode(String function) {
        return WORD_FNS.contains(function) ? "STRN001" : "STR001";
    }
}
