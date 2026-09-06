package dev.kof.compiler;

import java.util.List;

/**
 * Inferência de tipo de métodos de coleção (List/Map/Set/String).
 */
final class CollectionMethodTyper {

    private CollectionMethodTyper() {}

    static Type inferCollectionType(CompilerDriver driver, Type recvType, MethodCallExpr mc,
                                     List<IRLocalVariable> locals) {
    if (BuiltinTypes.isList(recvType)) {
        String mn = mc.methodName();
        if (("map".equals(mn) || "filter".equals(mn) || "reduce".equals(mn))
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
            }
            return Type.UnknownType.UNKNOWN;
        }
        if ("get".equals(mn) || "remove".equals(mn)) return driver.listElementType(recvType);
        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) return Type.PrimitiveType.INT;
        if ("contains".equals(mn) || "isEmpty".equals(mn)) return Type.PrimitiveType.BOOL;
        if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                || "set".equals(mn) || "clear".equals(mn)) {
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
            // mesmo contrato do emit: valores de referência devolvem V?
            return valueType instanceof Type.ClassType ct
                    && !KofUi.isUiType(ct) && !KofMedia.isHandleType(ct)
                    ? new Type.NullableType(valueType) : valueType;
        }
        if ("remove".equals(mn)) return valueType;
        if ("put".equals(mn)) return valueType;
        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) return Type.PrimitiveType.INT;
        if ("containsKey".equals(mn) || "contains".equals(mn) || "isEmpty".equals(mn)) return Type.PrimitiveType.BOOL;
        if ("clear".equals(mn)) return Type.PrimitiveType.VOID;
        if ("keys".equals(mn)) return new Type.ClassType("kof", "List", List.of(keyType));
        if ("values".equals(mn)) return new Type.ClassType("kof", "List", List.of(valueType));
    }
    if (BuiltinTypes.isSet(recvType)) {
        String mn = mc.methodName();
        Type elemType = Type.UnknownType.UNKNOWN;
        if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) elemType = ct.typeArguments().get(0);
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
                || "equals".equals(mn) || "equalsIgnoreCase".equals(mn)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("substring".equals(mn) || "concat".equals(mn) || "trim".equals(mn)
                || "toUpperCase".equals(mn) || "toLowerCase".equals(mn)
                || "replace".equals(mn) || "valueOf".equals(mn)) {
            return BuiltinTypes.STRING;
        }
        if ("split".equals(mn)) {
            return new Type.ArrayType(BuiltinTypes.STRING);
        }
    }
    return Type.UnknownType.UNKNOWN;
}
}
