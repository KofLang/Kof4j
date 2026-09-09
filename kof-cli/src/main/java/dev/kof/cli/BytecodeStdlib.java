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
        // métodos sem-argumento que em Kof são PROPRIEDADES (não métodos)
        if (args.isEmpty() && ("length".equals(name) || "size".equals(name) || "isEmpty".equals(name))) {
            return receiver + "." + name;
        }
        return null;
    }

    /** Chamada estática → idiom Kof ({@code Integer.parseInt -> .toInt},
     *  {@code Math.abs -> math.abs} com o guard de descriptor). null = não mapeia. */
    static String statics(String ownerInternal, String name, String args, String desc) {
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
                default:
                    return null;
            }
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
