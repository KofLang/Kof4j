package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

final class CollectionLiteralTypes {

    private CollectionLiteralTypes() {}

    static Type explicit(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope, String kind, int arity) {
        if (!mc.arguments().isEmpty() || mc.typeArguments().size() != arity) return null;
        List<Type> args = new ArrayList<>();
        for (String t : mc.typeArguments()) args.add(MemberResolver.resolveType(sa, t, scope));
        return new Type.ClassType("kof", kind, args);
    }

    static Type mapOf(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (mc.arguments().isEmpty() && mc.typeArguments().size() == 2) {
            return new Type.ClassType("kof", "Map", List.of(
                    CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit),
                    CompilerTypes.toType(mc.typeArguments().get(1), driver.currentUnit)));
        }
        Type keyType = mc.arguments().isEmpty() ? Type.UnknownType.UNKNOWN
                : ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        Type valueType = mc.arguments().size() < 2 ? Type.UnknownType.UNKNOWN
                : ExpressionTyper.inferExprType(driver, mc.arguments().get(1), locals);
        return new Type.ClassType("kof", "Map", List.of(keyType, valueType));
    }

    static Type setOf(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
        Type elemType = Type.UnknownType.UNKNOWN;
        if (!mc.arguments().isEmpty()) {
            elemType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        } else if (mc.typeArguments().size() == 1) {
            elemType = CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
        }
        return new Type.ClassType("kof", "Set", List.of(elemType));
    }
}
