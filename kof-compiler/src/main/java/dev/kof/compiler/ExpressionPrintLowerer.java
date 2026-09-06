package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de print/println no emitExpression.
 */
final class ExpressionPrintLowerer {

    private ExpressionPrintLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
if (("print".equals(mc.methodName()) || "println".equals(mc.methodName())) && mc.arguments().size() == 1) {
    Type printedType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if (Type.isVoid(printedType)) {
        // void não é um valor: println(f()) com f void empilhava
        // nada e o backend dava pop de lixo (segfault Native /
        // VerifyError JVM). Diagnóstico limpo em vez disso.
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    mc.methodName() + "(...) recebeu um valor void — a chamada não"
                            + " retorna valor (adicione 'return' ou não a use como argumento)",
                    "SEM033");
        }
        return localIdx;
    }
    if (!driver.fpSupportedOnNative(printedType, mc.position())) {
        return localIdx;
    }
    ops.add(new KofGetStatic(
            new Type.ClassType("java.lang", "System", List.of()),
            "out", new Type.ClassType("java.io", "PrintStream", List.of())));
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    Type argType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if (TypeMetrics.isPrimitiveType(argType)) {
        if (driver.target.isNative()) {
            // println(char) é NUMÉRICO (congelado: strings.md
            // "72 (H)" + execStringCharAt). valueOf(char) solto
            // é o caractere UTF-8 (common-mistakes.md "h").
            // O dispatch nativo do valueOf decide pelo tipo do
            // parâmetro — aqui mapeia char→Int para imprimir o
            // codepoint sem quebrar String.valueOf(char).
            Type nativeArg = (argType instanceof Type.PrimitiveType p
                    && "char".equals(p.name()))
                    ? Type.PrimitiveType.INT : argType;
            ops.add(new KofCall(
                    BuiltinTypes.STRING,
                    "valueOf", List.of(nativeArg),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        } else {
            TypeEmitter.boxPrimitive(ops, argType);
            ops.add(new KofCall(
                    BuiltinTypes.STRING,
                    "valueOf", List.of(Type.UnknownType.UNKNOWN),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        }
    } else {
        // o tipo REAL do arg só vai para o valueOf NATIVO (para
        // despachar toString de records). JVM/JS usam Object
        // (String.valueOf(Object) chama toString; valueOf de um
        // ClassType específico não existe no JVM).
        ops.add(new KofCall(
                BuiltinTypes.STRING,
                "valueOf", List.of(driver.target.isNative()
                        && !Type.isString(argType) ? argType
                        : Type.UnknownType.UNKNOWN),
                BuiltinTypes.STRING, KofCallKind.STATIC));
    }
    ops.add(new KofCall(
            new Type.ClassType("java.io", "PrintStream", List.of()),
            mc.methodName(), List.of(BuiltinTypes.STRING),
            Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
        return localIdx;
    }
        return -1;
    }
}