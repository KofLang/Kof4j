package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de `String.format(String, Object...)` — varargs do JDK.
 *
 * <p>#156/#216: o classpath externo casa overloads por name+arity apenas e
 * não enxerga {@code java.lang.String} (o JDK não está nos entries), então a
 * chamada caía no descritor fabricado `(String,String,int)Object` e explodia
 * com {@code NoSuchMethodError} em runtime. Aqui os argumentos extras são
 * empacotados num {@code Object[]} (primitivos boxados) e o descritor REAL
 * {@code (Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;} é emitido.
 * Cobre 0 args extras (`format("Hello World")`).
 */
final class StringFormatCallLowerer {

    private StringFormatCallLowerer() {}

    /** A chamada é `String.format(...)` com o 1º arg String (ou tipo desconhecido)? */
    static boolean matches(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (mc.arguments().isEmpty()) return false;
        Type first = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        return BuiltinTypes.isString(first) || first == Type.UnknownType.UNKNOWN;
    }

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
            String owner, int localIdx, List<IRLocalVariable> locals) {
        Type object = new Type.ClassType("java.lang", "Object", List.of());
        Type objectArray = new Type.ArrayType(object);
        Type localeType = new Type.ClassType("java.util", "Locale", List.of());
        List<ExpressionNode> args = mc.arguments();
        // Push Locale.ROOT so format output is locale-independent (e.g. '.' not ',' for decimals)
        ops.add(new KofGetStatic(localeType, "ROOT", localeType));
        localIdx = ExpressionLowerer.emitExpression(driver, args.get(0), ops, owner, localIdx, locals);
        int extra = args.size() - 1;
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, extra));
        ops.add(new KofNewArray(object));
        for (int i = 0; i < extra; i++) {
            ops.add(new KofDup());
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, i));
            ExpressionNode arg = args.get(i + 1);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            Type argType = ExpressionTyper.inferExprType(driver, arg, locals);
            if (argType instanceof Type.PrimitiveType) {
                TypeEmitter.boxPrimitive(ops, argType);
            }
            ops.add(new KofArrayStore(object));
        }
        ops.add(new KofCall(BuiltinTypes.STRING, "format",
                List.of(localeType, BuiltinTypes.STRING, objectArray), BuiltinTypes.STRING, KofCallKind.STATIC));
        return localIdx;
    }
}
