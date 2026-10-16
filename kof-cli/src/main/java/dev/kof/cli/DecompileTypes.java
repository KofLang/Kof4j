package dev.kof.cli;

/**
 * Satélite do REFACTOR-500 (check_500, Decompile 670→<600): a família de
 * mapeamento JVM descriptor -> nome de tipo Kof (fieldKofType + descriptorToName
 * + capitalizePrimitive) movida verbatim.
 */
final class DecompileTypes {

    private DecompileTypes() {}

/** Maps a raw JVM field descriptor (e.g. "I", "[I", "Ljava/lang/String;") to a Kof type name. */
static String fieldKofType(String jvmDesc) {
    return capitalizePrimitive(descriptorToName(jvmDesc));
}

/** Maps a {@code Type.describe()} name (e.g. "int", "String", "int[]") to a Kof type name. */
static String methodKofType(String typeName) {
    return capitalizePrimitive(typeName);
}

private static String capitalizePrimitive(String name) {
    if (name == null || name.isEmpty()) return "Object";
    String arraySuffix = "";
    String base = name;
    while (base.endsWith("[]")) {
        arraySuffix = "[]" + arraySuffix;
        base = base.substring(0, base.length() - 2);
    }
    // Fase E (13/10): genéricos — os ARGUMENTOS de tipo mapeiam TAMBÉM
    // (`List<Integer>` → `List<Int>`; antes só o base era mapeado e o
    // `Integer` interno ficava indefinido, o decompilado não compilava).
    int lt = base.indexOf('<');
    if (lt > 0 && base.endsWith(">")) {
        String inner = base.substring(lt + 1, base.length() - 1);
        StringBuilder args = new StringBuilder();
        int depth = 0, start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '<') depth++;
            if (c == '>') depth--;
            if (c == ',' && depth == 0) {
                args.append(capitalizePrimitive(inner.substring(start, i).trim())).append(", ");
                start = i + 1;
            }
        }
        args.append(capitalizePrimitive(inner.substring(start).trim()));
        // Fase E (14/10): as interfaces funcionais do JDK → tipos função
        // Kof (medido: `(Int) -> Bool` como param compila e roda). As
        // fontes JDK: Predicate<T> / Function<T,R> / Supplier<T>.
        String fnHead = base.substring(0, lt);
        if (fnHead.equals("Predicate")) return "(" + args + ") -> Bool" + arraySuffix;
        if (fnHead.equals("Function") || fnHead.equals("UnaryOperator")) {
            String two = args.toString();
            int comma = two.indexOf(", ");
            if (comma > 0) return "(" + two.substring(0, comma) + ") -> "
                    + two.substring(comma + 2) + arraySuffix;
        }
        if (fnHead.equals("Supplier")) return "() -> " + args + arraySuffix;
        return capitalizePrimitive(fnHead) + "<" + args + ">" + arraySuffix;
    }
    String capitalized = switch (base) {
        case "int" -> "Int";
        case "long" -> "Long";
        case "float" -> "Float";
        case "double" -> "Double";
        case "bool", "boolean" -> "Bool";
        case "char" -> "Char";
        case "byte" -> "Byte";
        case "short" -> "Short";
        case "void" -> "void";
        // Fase E (13/10): os WRAPPERS (assinatura generica
        // `List<Integer>` mantinha `Integer` — tipo indefinido no Kof,
        // o decompilado nao compilava) + a tabela JDK→stdlib do
        // TranslateTypes.kofType (aprovada), espelhada.
        case "Integer" -> "Int";
        case "Boolean" -> "Bool";
        case "Character" -> "Char";
        case "ArrayList" -> "List";
        case "HashMap" -> "Map";
        default -> base;
    };
    return capitalized + arraySuffix;
}

private static String descriptorToName(String desc) {
    if (desc == null || desc.isEmpty()) return "";
    char c = desc.charAt(0);
    if (c == 'L') {
        int end = desc.indexOf(';');
        String cls = end >= 0 ? desc.substring(1, end) : desc.substring(1);
        return Decompile.simpleName(cls);
    }
    if (c == '[') {
        return descriptorToName(desc.substring(1)) + "[]";
    }
    return switch (c) {
        case 'B' -> "byte";
        case 'C' -> "char";
        case 'D' -> "double";
        case 'F' -> "float";
        case 'I' -> "int";
        case 'J' -> "long";
        case 'S' -> "short";
        case 'V' -> "void";
        case 'Z' -> "bool";
        default -> desc;
    };
}

}
