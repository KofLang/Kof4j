package dev.kof.compiler;

import java.util.List;

/**
 * D-MULTIPARADIGMA-PHASE1A slices 1g/1h — lowering de
 * {@code sorted}/{@code sorted_cmp} e {@code groupBy} (extraído do
 * {@link CollectionCallLowerer} pelo gate 500).
 */
final class CollectionMultiparadigmaLowerer {

    private CollectionMultiparadigmaLowerer() {
    }

    /** sorted: aridade 0 = natural, 1 = comparador (o gate de aridade
     *  compartilhado trata o resto pela fn escolhida). */
    static String sortedFn(MethodCallExpr mc) {
        return mc.arguments().size() == 1 ? "kof_list_sorted_cmp" : "kof_list_sorted";
    }

    /** Gate de forma lambda (R6, mesma classe do gate higher-order: sem
     *  ela o call quebraria diferente em cada backend). A aridade já passou
     *  no gate compartilhado; aqui vale a FORMA do argumento. Retorna true
     *  com o diagnóstico SEM025 emitido (o chamador retorna localIdx). */
    static boolean checkLambdaForm(CompilerDriver driver, String listFn, MethodCallExpr mc) {
        String want = "kof_list_sorted_cmp".equals(listFn) ? "sorted"
                : "kof_list_groupby".equals(listFn) ? "groupBy" : null;
        if (want == null) return false;
        boolean oneLambda = mc.arguments().size() == 1
                && mc.arguments().get(0) instanceof LambdaExpr;
        if (!oneLambda && driver.currentDiagnostics != null) {
            var pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "List." + want + " takes exactly one lambda argument",
                    "SEM025");
            return true;
        }
        return false;
    }

    /** Tipo da chave do groupBy = retorno da lambda (Unknown honesto quando
     *  não inferido; tag/typing degradam como mapOf). */
    static Type groupByKeyType(java.util.List<Type> argTypes) {
        if (!argTypes.isEmpty() && argTypes.get(0) instanceof Type.FunctionType ft
                && !(ft.returnType() instanceof Type.UnknownType)) {
            return ft.returnType();
        }
        return Type.UnknownType.UNKNOWN;
    }

    /** Tipo de retorno do groupBy: Map&lt;K,List&lt;E&gt;&gt;. */
    static Type groupReturnType(Type elemType, java.util.List<Type> argTypes) {
        return new Type.ClassType("kof", "Map", List.of(groupByKeyType(argTypes),
                new Type.ClassType("kof", "List", List.of(elemType))));
    }

    /** Tipo de retorno do zip: List&lt;Pair&lt;A,B&gt;&gt; (record nominal
     *  injetado de pairs.kf). */
    static Type zipPairListType(Type elemA, Type elemB) {
        return new Type.ClassType("kof", "List", List.of(
                new Type.ClassType("", "Pair", List.of(elemA, elemB))));
    }

    /** Tag de chave do groupBy (mesma taxonomia das chaves de mapOf via
     *  mapKeyTag; -1/Unknown mantém o default histórico 1, como mapOf). */
    static int groupKeyTag(java.util.List<Type> argTypes) {
        Type keyType = groupByKeyType(argTypes);
        return CollectionWrites.mapKeyTag(keyType, keyType);
    }
}
