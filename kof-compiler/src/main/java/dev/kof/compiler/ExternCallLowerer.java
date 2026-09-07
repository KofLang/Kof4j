package dev.kof.compiler;

import java.util.List;

/**
 * FFI (R3) — lowering de chamada a {@code extern} declarado para o helper
 * {@code kof_ffi_*} (empilha lib + nome + argumento) + o binding suportado por
 * target. Módulo próprio para manter {@code ExpressionStaticCallLowerer} e
 * {@code CompilerDriver} <=500.
 */
final class ExternCallLowerer {

    private ExternCallLowerer() {}

    static boolean isExternBound(Target target, ExternalFunctionNode ext) {
        if (ext.parameters().size() != 1) return false;
        String p = ext.parameters().get(0).type();
        String r = ext.returnType();
        if (target == Target.JVM || target == Target.ANDROID) {
            return (isIntType(r) && (isIntType(p) || isStringType(p) || isIntArrayType(p)))
                    || (isDoubleType(r) && isDoubleType(p));
        }
        if (target == Target.NATIVE) {
            return isIntType(r) && (isIntType(p) || isStringType(p) || isIntArrayType(p));
        }
        return false;
    }

    private static boolean isIntType(String t) { return "int".equals(t) || "Int".equals(t); }
    private static boolean isStringType(String t) { return "String".equals(t) || "string".equals(t); }
    private static boolean isIntArrayType(String t) { return "Int[]".equals(t) || "int[]".equals(t); }
    private static boolean isDoubleType(String t) { return "double".equals(t) || "Double".equals(t); }

    static Integer tryLower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                            String owner, int localIdx, List<IRLocalVariable> locals) {
        if (mc.receiver() != null || !driver.externSignatures.containsKey(mc.methodName())
                || mc.arguments().size() != 1) {
            return null;
        }
        ExternalFunctionNode ext = driver.externSignatures.get(mc.methodName());
        if (!isExternBound(driver.target, ext)) {
            return null;
        }
        String p = ext.parameters().get(0).type();
        String helper;
        Type argType;
        Type retType;
        if (isDoubleType(p)) {
            helper = "kof_ffi_dd";
            argType = Type.PrimitiveType.DOUBLE;
            retType = Type.PrimitiveType.DOUBLE;
        } else if (isStringType(p)) {
            helper = "kof_ffi_si";
            argType = BuiltinTypes.STRING;
            retType = Type.PrimitiveType.INT;
        } else if (isIntArrayType(p)) {
            helper = "kof_ffi_ai";
            argType = new Type.ArrayType(Type.PrimitiveType.INT);
            retType = Type.PrimitiveType.INT;
        } else {
            helper = "kof_ffi_i";
            argType = Type.PrimitiveType.INT;
            retType = Type.PrimitiveType.INT;
        }
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                ext.library() != null ? ext.library() : ""));
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ext.name()));
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof", "ffi", List.of()), helper,
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING, argType),
                retType, KofCallKind.FUNCTION));
        return localIdx;
    }
}