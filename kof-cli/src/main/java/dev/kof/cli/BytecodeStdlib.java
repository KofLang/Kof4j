package dev.kof.cli;

/**
 * Mapeamento de chamadas de stdlib Java → idiomes Kof (Fase E / R6).
 *
 * <p>O decompiler só emite {@code Owner.name(...)} quando o owner é uma classe
 * de DOMÍNIO; chamadas a {@code java.*}/{@code jdk.*} SEM correspondência aqui
 * são recusadas (→ stub honesto), porque no arquivo {@code .kf} gerado (sem
 * import do dono) viram SEM011 e não compilam — código errado, mas compilável.
 * As regras abaixo usam o {@code descriptor} p/ qualificar o polimorfismo
 * (ex.: {@code Math.abs} é (I)I/(J)J/(F)F/(D)D, mas {@code math.abs} de Kof é
 * Int-only — só o overload que bate emite).
 */
final class BytecodeStdlib {

    private BytecodeStdlib() {
    }

    /** Chamada virtual → idiom Kof (receiver {@code System.out}/{@code String}
     *  etc.), ou null se não mapeia (o chamador então recusa se o owner é JDK). */
    static String virtual(String receiver, String ownerInternal, String name, String args) {
        if ("java/io/PrintStream".equals(ownerInternal) && "System.out".equals(receiver)
                && ("println".equals(name) || "print".equals(name))) {
            return (name.equals("println") ? "println" : "print") + "(" + args + ")";
        }
        if ("java/lang/String".equals(ownerInternal) && "equals".equals(name)) {
            return receiver + " == " + args;
        }
        // Fase E (14/10): indexOf/lastIndexOf com argumento INT (char) NÃO
        // têm equivalente — os do Kof são String-param (medido SEM051: o
        // verbatim `lastIndexOf(46)` não compila). Só o overload String passa.
        if ("java/lang/String".equals(ownerInternal)
                && ("lastIndexOf".equals(name) || "indexOf".equals(name))
                && !args.isEmpty() && !args.startsWith("\"")) {
            return null;
        }
        // Fase E (14/10): o keySet do Map do JDK → keys do Kof (medido
        // SEM025: o verbatim `keySet()` não resolve — os válidos do Map Kof
        // incluem keys/values)
        if ("java/util/Map".equals(ownerInternal) && "keySet".equals(name) && args.isEmpty()) {
            return receiver + ".keys()";
        }
        // métodos sem-argumento que em Kof são PROPRIEDADES (não métodos).
        // `isEmpty` NÃO é propriedade (13/10, medido: `.isEmpty` compilava e
        // quebrava em runtime com NoSuchField — nem String nem ArrayList têm
        // o campo; `s.isEmpty()` CHAMADA é fiel e `Bool != 0` na condição
        // funciona) — sai da lista.
        if (args.isEmpty() && ("length".equals(name) || "size".equals(name))) {
            return receiver + "." + name;
        }
        // #362 (SEM074): unbox de wrapper do JDK = identidade em Kof — não
        // existem valores boxed (checkcast+intValue do javac vira só o valor;
        // `x.intValue()` num primitivo é exatamente o que a classe rejeita).
        if (args.isEmpty() && UNBOX.containsKey(ownerInternal)
                && UNBOX.get(ownerInternal).equals(name)) {
            return receiver;
        }
        return null;
    }

    private static final java.util.Map<String, String> UNBOX = java.util.Map.of(
            "java/lang/Integer", "intValue",
            "java/lang/Long", "longValue",
            "java/lang/Double", "doubleValue",
            "java/lang/Float", "floatValue",
            "java/lang/Boolean", "booleanValue",
            "java/lang/Character", "charValue",
            "java/lang/Short", "shortValue",
            "java/lang/Byte", "byteValue");

    /** Chamada estática → idiom Kof ({@code Integer.parseInt -> .toInt},
     *  {@code Math.abs -> math.abs} com o guard de descriptor). null = não mapeia. */
    static String statics(String ownerInternal, String name, String args, String desc) {
        // Fase E (15/10): o Integer.toString(I) → `"" + x` (o idioma do
        // String.valueOf existente; o Kof Int TEM toString — medido:
        // `s.hashCode().toString()` compila e roda == Java)
        if ("java/lang/Integer".equals(ownerInternal) && "toString".equals(name)
                && "(I)Ljava/lang/String;".equals(desc)) {
            return "\"\" + " + args;
        }
        if ("java/lang/Math".equals(ownerInternal)) {
            switch (desc) {
                case "(I)I":
                    switch (name) {
                        case "abs": case "sign": case "min": case "max": case "clamp":
                            return "math." + name + "(" + args + ")";
                        default: return null;
                    }
                case "(II)I":
                    if ("min".equals(name) || "max".equals(name)) return "math." + name + "(" + args + ")";
                    return null;
                case "(III)I":
                    if ("clamp".equals(name)) return "math.clamp(" + args + ")";
                    return null;
                // Fase E (14/10): sqrt/pow EXISTEM no math p/ Double (medido:
                // sqrt(9.0)=3.0, pow(2,3)=8.0); floor/round/ceil NÃO existem
                // (SEM025) → teto honesto (exigiria stdlib nova = mantenedora).
                case "(D)D":
                    if ("sqrt".equals(name)) return "math.sqrt(" + args + ")";
                    return null;
                case "(DD)D":
                    if ("pow".equals(name)) return "math.pow(" + args + ")";
                    return null;
                default:
                    return null;
            }
        }
        // Fase E (13/10): `String.valueOf(x)` é MUITO comum — o Kof converte
        // para String com a concat de string vazia (medido: "" + x dá o mesmo
        // p/ Int/Long/Double/Bool/Char). Vale p/ os overloads primitivos E o
        // Object (String.valueOf(Object) chama toString; "" + obj idem).
        if ("java/lang/String".equals(ownerInternal) && "valueOf".equals(name)) {
            return "\"\" + " + args;
        }
        // Fase E (13/10): as FABRICAS de colecao imutavel (os idiomas modernos
        // — medido: List.of/Map.of/Set.of stubavam). O javac usa os OVERLOADS
        // de aridade FIXA p/ ate 10 args (sem array no bytecode), entao basta
        // o mapeamento direto dos args: List.of(a, b) → listOf(a, b);
        // Set.of(a, b) → setOf(a, b); Map.of(k1, v1, ...) → mapOf(...) —
        // pares (contados do desc; impares → recusa honesta).
        if ("java/util/List".equals(ownerInternal) && "of".equals(name)
                && desc.endsWith(")Ljava/util/List;")) {
            return "listOf(" + args + ")";
        }
        if ("java/util/Set".equals(ownerInternal) && "of".equals(name)
                && desc.endsWith(")Ljava/util/Set;")) {
            return "setOf(" + args + ")";
        }
        if ("java/util/Map".equals(ownerInternal) && "of".equals(name)
                && desc.endsWith(")Ljava/util/Map;")) {
            if (BytecodeCp.argCount(desc) % 2 != 0) return null;
            return "mapOf(" + args + ")";
        }
        if ("java/lang/Integer".equals(ownerInternal) && ("parseInt".equals(name) || "valueOf".equals(name))) {
            return args + ".toInt()";
        }
        if ("java/lang/Long".equals(ownerInternal) && ("parseLong".equals(name) || "valueOf".equals(name))) {
            return args + ".toLong()";
        }
        if ("java/lang/Double".equals(ownerInternal) && ("parseDouble".equals(name) || "valueOf".equals(name))) {
            return args + ".toDouble()";
        }
        if ("java/lang/Float".equals(ownerInternal) && ("parseFloat".equals(name) || "valueOf".equals(name))) {
            return args + ".toFloat()";
        }
        if ("java/lang/System".equals(ownerInternal) && "currentTimeMillis".equals(name)) {
            return "now()";
        }
        return null;
    }
}
