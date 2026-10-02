package dev.kof.compiler;

import java.util.List;

/**
 * Inferência de tipo de métodos de coleção (List/Map/Set/String).
 */
public final class CollectionMethodTyper {

    private CollectionMethodTyper() {}

    static Type inferCollectionType(CompilerDriver driver, Type recvType, MethodCallExpr mc,
                                     List<IRLocalVariable> locals) {
    if (BuiltinTypes.isList(recvType)) {
        String mn = mc.methodName();
        if (("map".equals(mn) || "filter".equals(mn) || "reduce".equals(mn)
                || "any".equals(mn) || "all".equals(mn) || "none".equals(mn)
                || "find".equals(mn) || "count".equals(mn) || "forEach".equals(mn)
                || "flatMap".equals(mn))
                && mc.arguments().stream().anyMatch(a -> a instanceof LambdaExpr)) {
            Type lambdaT = null;
            for (ExpressionNode arg : mc.arguments()) {
                if (arg instanceof LambdaExpr lam) {
                    lambdaT = ExpressionTyper.inferExprType(driver, lam, locals);
                    break;
                }
            }
            if (lambdaT instanceof Type.FunctionType ft
                    && !(ft.returnType() instanceof Type.UnknownType)) {
                if ("map".equals(mn)) {
                    return new Type.ClassType("kof", "List",
                            List.of(ft.returnType()));
                }
                if ("filter".equals(mn)) return recvType;
                if ("reduce".equals(mn)) return ft.returnType();
                // D-MULTIPARADIGMA-PHASE1A — quantifiers always return Bool.
                if ("any".equals(mn) || "all".equals(mn) || "none".equals(mn))
                    return Type.PrimitiveType.BOOL;
                // D-MULTIPARADIGMA-PHASE1A slice 1b — find returns the element
                // type as nullable (missing = null per target, like Map.get).
                if ("find".equals(mn))
                    return new Type.NullableType(driver.listElementType(recvType));
                // D-MULTIPARADIGMA-PHASE1A slice 1c — forEach always returns
                // Void, whatever the lambda yields.
                if ("forEach".equals(mn)) return Type.PrimitiveType.VOID;
                // D-MULTIPARADIGMA-PHASE1A slice 1d — flatMap returns the
                // lambda's List<R> itself (no re-wrap); non-List lambda
                // result is UNKNOWN honest (the runtime cast fails loudly).
                if ("flatMap".equals(mn)) {
                    if (ft.returnType() instanceof Type.ClassType ct
                            && "List".equals(ct.name())) return ft.returnType();
                    return Type.UnknownType.UNKNOWN;
                }
                // D-MULTIPARADIGMA-PHASE1A slice 1h — groupBy returns
                // Map<K,List<E>> (K = lambda return).
                if ("groupBy".equals(mn))
                    return new Type.ClassType("kof", "Map", List.of(ft.returnType(),
                            new Type.ClassType("kof", "List",
                                    List.of(driver.listElementType(recvType)))));
            }
            return Type.UnknownType.UNKNOWN;
        }
        if ("get".equals(mn) || "remove".equals(mn)) return driver.listElementType(recvType);
        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) return Type.PrimitiveType.INT;
        if ("contains".equals(mn) || "isEmpty".equals(mn)) return Type.PrimitiveType.BOOL;
        // #382 — indexOf/lastIndexOf são Int (posição; -1 quando ausente,
        // mesmo oracle java.util.List); addAll é Bool (mudou?); subList
        // devolve List do MESMO tipo de elemento; sort é void (in-place).
        if ("indexOf".equals(mn) || "lastIndexOf".equals(mn)) return Type.PrimitiveType.INT;
        if ("addAll".equals(mn)) return Type.PrimitiveType.BOOL;
        if ("subList".equals(mn)) return recvType;
        // pagination P1 — take/drop/slice: List<E> do mesmo tipo.
        if ("take".equals(mn) || "drop".equals(mn) || "slice".equals(mn)) return recvType;
        // D-MULTIPARADIGMA-PHASE1A slice 1e — distinct returns List<E> (copy).
        if ("distinct".equals(mn)) return recvType;
        // D-MULTIPARADIGMA-PHASE1A slice 1g — sorted/sorted_cmp: List<E>.
        if ("sorted".equals(mn)) return recvType;
        // D-MULTIPARADIGMA-PHASE1A slice 1i — zip: List<Pair<A,B>>
        // (package "" per MemberCallTyper rationale above).
        if ("zip".equals(mn)) {
            Type argElem = Type.UnknownType.UNKNOWN;
            if (!mc.arguments().isEmpty()) {
                Type at = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                if (at instanceof Type.ClassType act
                        && "List".equals(act.name()) && !act.typeArguments().isEmpty()) {
                    argElem = act.typeArguments().get(0);
                }
            }
            return CollectionMultiparadigmaLowerer.zipPairListType(
                    driver.listElementType(recvType), argElem);
        }
        if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                || "set".equals(mn) || "clear".equals(mn) || "sort".equals(mn)) {
            return Type.PrimitiveType.VOID;
        }
    }
    if (BuiltinTypes.isMap(recvType)) {
        String mn = mc.methodName();
        Type valueType = Type.UnknownType.UNKNOWN;
        if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) valueType = ct.typeArguments().get(1);
        Type keyType = Type.UnknownType.UNKNOWN;
        if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) keyType = ct.typeArguments().get(0);
        if ("get".equals(mn)) {
            // SG-008 (bug 87): get() devolve V? para TODO valor — ausência é
            // null comparável (`x == null`), nunca NPE por unbox
            return new Type.NullableType(valueType);
        }
        // D-NULL-INTENT/I7: `remove`/`put` devolvem V? — Java Map contract
        // (valor anterior/removido OU null quando ausente), mesma razão do
        // `get` acima (SG-008/bug 87). Antes tipava V (não-nullable) e
        // fold null→default no ausente contradizia o contrato.
        if ("remove".equals(mn)) return new Type.NullableType(valueType);
        if ("put".equals(mn)) return new Type.NullableType(valueType);
        if ("getOrDefault".equals(mn)) return valueType;
        // #386 — containsValue é Bool; putIfAbsent segue o contrato Java
        // (valor anterior OU null quando ausente) → V? pela mesma razão do
        // put/remove acima (D-NULL-INTENT/I7).
        if ("containsValue".equals(mn)) return Type.PrimitiveType.BOOL;
        if ("putIfAbsent".equals(mn)) return new Type.NullableType(valueType);
        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) return Type.PrimitiveType.INT;
        if ("containsKey".equals(mn) || "contains".equals(mn) || "isEmpty".equals(mn)) return Type.PrimitiveType.BOOL;
        if ("clear".equals(mn)) return Type.PrimitiveType.VOID;
        if ("keys".equals(mn)) return new Type.ClassType("kof", "List", List.of(keyType));
        if ("values".equals(mn)) return new Type.ClassType("kof", "List", List.of(valueType));
    }
    if (BuiltinTypes.isSet(recvType)) {
        String mn = mc.methodName();
        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) return Type.PrimitiveType.INT;
        if ("contains".equals(mn) || "isEmpty".equals(mn)) return Type.PrimitiveType.BOOL;
        if ("add".equals(mn) || "remove".equals(mn)) return Type.PrimitiveType.BOOL;
        if ("clear".equals(mn)) return Type.PrimitiveType.VOID;
    }
    if (Type.isString(recvType)) {
        String mn = mc.methodName();
        if ("charAt".equals(mn)) return Type.PrimitiveType.CHAR;
        if ("toInt".equals(mn)) return Type.PrimitiveType.INT;
        if ("toLong".equals(mn)) return Type.PrimitiveType.LONG;
        if ("toDouble".equals(mn)) return Type.PrimitiveType.DOUBLE;
        if ("toFloat".equals(mn)) return Type.PrimitiveType.FLOAT;
        if ("length".equals(mn) || "indexOf".equals(mn) || "lastIndexOf".equals(mn)
                || "compareTo".equals(mn) || "compareToIgnoreCase".equals(mn)
                || "hashCode".equals(mn) || "size".equals(mn) || "count".equals(mn)) {
            return Type.PrimitiveType.INT;
        }
        if ("contains".equals(mn) || "startsWith".equals(mn) || "endsWith".equals(mn)
                || "equals".equals(mn) || "equalsIgnoreCase".equals(mn)
                || "isEmpty".equals(mn) || "matches".equals(mn)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("substring".equals(mn) || "concat".equals(mn) || "trim".equals(mn)
                || "toUpperCase".equals(mn) || "toLowerCase".equals(mn)
                || "replace".equals(mn) || "replaceAll".equals(mn)
                || "replaceFirst".equals(mn) || "valueOf".equals(mn)) {
            return BuiltinTypes.STRING;
        }
        if ("toCharArray".equals(mn)) {
            return new Type.ArrayType(Type.PrimitiveType.CHAR);
        }
        if ("split".equals(mn)) {
            return new Type.ArrayType(BuiltinTypes.STRING);
        }
    }
    return Type.UnknownType.UNKNOWN;
}
}
