package dev.kof.compiler;

import java.util.List;

/**
 * Guardas de DIAGNÓSTICO em compile-time para chamadas de método num receptor
 * String (frontend único — os 5 backends herdam o MESMO erro). Extraído do
 * `ExpressionInstanceCallLowerer` (gate REFACTOR-500 ≤600): as três famílias
 * abaixo não alteram a pilha/ops — só emitem diagnostics — e são puras
 * condições sobre (nome do método, args, tipo do formal).
 *
 * - SEM052 (bug 96): funções da stdlib `strings.*` chamadas como método.
 * - SEM066 (§193): acessor de coleção/mapa numa String (a linha crua do
 *   db.query é JSON String desde DB001).
 * - SEM051 (bug 100): argumento NÃO-String num parâmetro String/CharSequence.
 */
final class StringReceiverGuards {

    private StringReceiverGuards() {}

    /** Métodos de String cujo parâmetro é String/CharSequence (SEM051 — bug 100). */
    private static final java.util.Set<String> STRING_ARG_METHODS = java.util.Set.of(
            "indexOf", "lastIndexOf", "contains", "startsWith", "endsWith",
            "split", "concat", "equalsIgnoreCase", "compareTo", "compareToIgnoreCase");

    /**
     * Funções da stdlib {@code strings.*} que NÃO são métodos de instância em
     * Kof (SEM052 — bug 96). Chamá-las como método era ACEITO e quebrava de
     * um jeito em cada target (JVM NoSuchMethodError, Native link-fail, JS
     * roda o nativo do JS, Script roda/quebra por reflexão) — paridade
     * absoluta JVM=JS=X86=ARM=RISC. O idiom do corpus é só a função
     * (training/idioms/stdlib.md, learn/39-stdlib.md).
     */
    private static final java.util.Set<String> NOT_INSTANCE_METHODS = java.util.Set.of(
            "repeat", "truncate", "padLeft", "padRight", "padStart", "padEnd",
            "capitalize", "uncapitalize", "reverse", "count",
            "isAlpha", "isNumeric", "isAlphaNumeric", "isAscii",
            "isUpperCase", "isLowerCase", "toCamelCase", "toPascalCase",
            "toSnakeCase", "toKebabCase", "slugify", "escapeHtml",
            "unescapeHtml", "escapeJson", "removeWhitespace", "normalizeWhitespace");

    /** §193: nomes de acessor de coleção/mapa chamados numa String (a linha
     *  crua do db.query é JSON String) → SEM066 em vez de runtime quebrado. */
    private static final java.util.Set<String> COLLECTION_ACCESSORS_ON_STRING = java.util.Set.of(
            "get", "put", "getOrDefault", "remove", "size", "keys", "values", "containsKey", "entries",
            // #382/#386 — novos acessores que numa String seriam SEM066 (e não
            // indexOf/lastIndexOf, que SÃO métodos de String de verdade).
            "containsValue", "putIfAbsent", "subList", "addAll", "sort");

    /** Nome canônico da função `strings.*` p/ o nome de método errado (SEM052). */
    private static String padHint(String methodName) {
        return switch (methodName) {
            case "padStart" -> "padLeft";
            case "padEnd" -> "padRight";
            default -> methodName;
        };
    }

    /** SEM051: Char/numérico/array/classe NÃO-Kof-String num formal String. */
    private static boolean notStringForFormal(Type t) {
        if (t instanceof Type.NullableType nt) t = nt.inner();
        if (t instanceof Type.UnknownType) return false;
        if (BuiltinTypes.isString(t)) return false;
        return TypeMetrics.isPrimitiveType(t) || t instanceof Type.ClassType
                || t instanceof Type.ArrayType;
    }

    private static String typeNameFor(Type t) {
        if (t instanceof Type.PrimitiveType p) return switch (p.name()) {
            case "char" -> "Char";
            case "int" -> "Int";
            case "long" -> "Long";
            case "double" -> "Double";
            case "float" -> "Float";
            case "bool" -> "Bool";
            case "byte" -> "Byte";
            case "short" -> "Short";
            default -> p.name();
        };
        if (t instanceof Type.ArrayType a) return typeNameFor(a.componentType()) + "[]";
        if (t instanceof Type.ClassType c) return c.name();
        return String.valueOf(t);
    }

    static void check(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals,
                      List<Type> methodParamTypes) {
        if (driver.currentDiagnostics == null) return;
        if (NOT_INSTANCE_METHODS.contains(mc.methodName())) {
            // bug 96 (paridade absoluta): REJEITAR em compile-time com SEM052
            // apontando p/ o idiom real do corpus — o MESMO erro nos 5 backends
            // (este lowering é o frontend único). NÃO confunda com
            // `toUpperCase`/`toLowerCase`/`trim`/`split`/`replace`/`substring`,
            // que SÃO métodos de String na registry (e em Kof).
            var pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "Kof has no \"" + mc.methodName() + "\" method on String; use the stdlib "
                            + "function: strings." + padHint(mc.methodName()) + "(...",
                    "SEM052");
        }
        // §193 (face crua do db.query, R6): a linha do `db.query` NÃO-tipado é
        // String JSON (`{"col":...}`) desde DB001; chamar acessor de coleção
        // nela (`rows.get(0).get("col")`) era ACEITO e quebrava em runtime —
        // JVM `NoSuchMethodError: String.get`, JS `.get` nativo inexistente →
        // undefined silencioso, Nativo `undefined reference` no link. Meta do
        // registro: diagnóstico em compile-time apontando o caminho canônico
        // (`db.query<Record>` tipado ou json.decode). NÃO confunda com os
        // métodos JDK reais de String (getBytes/isBlank/strip/repeat via
        // classpath externo) — esses continuam legais.
        if (COLLECTION_ACCESSORS_ON_STRING.contains(mc.methodName())) {
            var pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0,
                    pos != null ? pos.column() : 0, 0,
                    "String has no \"" + mc.methodName() + "\" method — looks like a "
                            + "collection accessor; the raw row from db.query is a JSON String: use "
                            + "db.query<Record> (typed) or json.decode<Map<String, Object>>(row)",
                    "SEM066");
        }
        // bug 100 (R6, paridade absoluta JVM=JS=X86=ARM=RISC): argumento
        // NÃO-String num parâmetro String/CharSequence (Char, Int, Long, lista…)
        // era ACEITO e quebrava de um jeito em CADA target (JVM
        // VerifyError/NoSuchMethodError/ExceptionInInitializer, Native SIGSEGV/
        // saída vazia, Script `false`/vazio). Opção B (decisão da mantenedora):
        // REJEITAR em tempo de compilação (SEM051), o MESMO erro nos 5 backends
        // (este lowering é o frontend único). Checagem POR POSIÇÃO pela formal
        // da registry (indexOf("a", 2) tem formal String,Int → só a posição 0
        // é stringy; indexOf('c') pega na 0). compareTo/compareToIgnoreCase não
        // estão na registry (sig=null) → tratadas TODAS as posições como stringy.
        // NÃO flaguemos `replace` (formal CHAR quando args são Char — widening
        // legal) nem `charAt`/`substring` (formal numérico, fora da lista).
        if (STRING_ARG_METHODS.contains(mc.methodName())) {
            StringMethodRegistry.Sig sigG = StringMethodRegistry.stringMethodSignature(
                    mc.methodName(), mc.arguments().size(), methodParamTypes);
            List<Type> formals = sigG != null ? sigG.parameterTypes() : null;
            for (int ai = 0; ai < mc.arguments().size(); ai++) {
                boolean formalIsStringy = formals == null
                        || (ai < formals.size() && (BuiltinTypes.isString(formals.get(ai))
                            || (formals.get(ai) instanceof Type.ClassType fc
                                && "CharSequence".equals(fc.name()))));
                if (!formalIsStringy) continue;
                Type at = ExpressionTyper.inferExprType(driver, mc.arguments().get(ai), locals);
                if (notStringForFormal(at)) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            "String." + mc.methodName() + " does not accept " + typeNameFor(at)
                                    + " as argument " + (ai + 1) + " (the parameter is String); "
                                    + "use a String literal, e.g.: " + mc.methodName() + "(\"c\")",
                            "SEM051");
                    break;
                }
            }
        }
        StringMethodRegistry.Sig intSig = StringMethodRegistry.stringMethodSignature(
                mc.methodName(), mc.arguments().size(), methodParamTypes);
        if (intSig != null) {
            List<Type> formals = intSig.parameterTypes();
            for (int ai = 0; ai < mc.arguments().size() && ai < formals.size(); ai++) {
                if (!Type.PrimitiveType.INT.equals(formals.get(ai))) continue;
                Type at = ExpressionTyper.inferExprType(driver, mc.arguments().get(ai), locals);
                if (notIntForFormal(at)) {
                    driver.currentDiagnostics.error(mc.arguments().get(ai),
                            "Argument " + (ai + 1) + " of '" + mc.methodName() + "': expected 'Int' but got '"
                                    + Type.display(at) + "'",
                            "SEM014");
                    break;
                }
            }
        }
    }

    private static boolean notIntForFormal(Type t) {
        if (BuiltinTypes.isString(t) || t instanceof Type.ArrayType) return true;
        return t instanceof Type.PrimitiveType && !TypeChecker.isAssignable(t, Type.PrimitiveType.INT);
    }
}
