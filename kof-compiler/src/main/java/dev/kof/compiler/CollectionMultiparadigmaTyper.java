package dev.kof.compiler;

import java.util.List;

/**
 * D-MULTIPARADIGMA-PHASE1A — retorno dos higher-orders de {@code List}
 * (extraído do {@link MemberCallTyper} pelo gate 500).
 */
final class CollectionMultiparadigmaTyper {

    private CollectionMultiparadigmaTyper() {
    }

    /**
     * Retorna null quando não é higher-order de List (o chamador segue no
     * restante da tipagem).
     */
    static Type inferHigherOrder(Type elemType, Type recvType, MethodCallExpr mc,
                                 SemanticAnalyzer sa) {
        String mn = mc.methodName();
        // #334 — `map` devolvia recvType (ELEMENTO-FONTE) e `reduce`
        // devolvia elemType: a expressao era cacheada com o tipo errado
        // (inferType guarda o resultado no no), entao `strs.get(0)`
        // emitia checkcast do tipo FONTE sobre o valor real da lambda →
        // ClassCastException silenciosa. Agora espelha o EMIT
        // (`MethodCallTyper` #149 / `CollectionMethodTyper`): map →
        // List<retorno-da-lambda>, reduce → retorno, filter → recvType
        // (mesmo elemento, correto). Lambda sem retorno inferido =
        // UNKNOWN honesto (o emit trata igual) — nunca mentir com o
        // tipo da fonte.
        if ("map".equals(mn) || "filter".equals(mn) || "reduce".equals(mn)
                || "any".equals(mn) || "all".equals(mn) || "none".equals(mn)
                || "flatMap".equals(mn)) {
            Type lamRet = Type.UnknownType.UNKNOWN;
            for (ExpressionNode arg : mc.arguments()) {
                if (arg instanceof LambdaExpr || !(arg instanceof MethodCallExpr)) {
                    if (sa.expressionTypes().get(arg) instanceof Type.FunctionType ft) {
                        lamRet = ft.returnType();
                        break;
                    }
                }
            }
            if ("filter".equals(mn)) return recvType;
            // D-MULTIPARADIGMA-PHASE1A — quantifiers always return Bool.
            if ("any".equals(mn) || "all".equals(mn) || "none".equals(mn))
                return Type.PrimitiveType.BOOL;
            if (lamRet instanceof Type.UnknownType) return Type.UnknownType.UNKNOWN;
            if ("map".equals(mn)) {
                return new Type.ClassType("kof", "List", List.of(lamRet));
            }
            return lamRet;
        }
        return null;
    }
}
