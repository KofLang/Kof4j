package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering das chamadas estáticas de namespaces (higiene de gate check_500 —
 * corpos extraídos do dispatcher {@code ExpressionMethodCallLowerer}; os
 * guards ficam no dispatcher). Mesma forma para todos: infere os tipos dos
 * args, resolve pelo staticMethod do namespace, recusa com o gap code honesto
 * quando o alvo não suporta, ou emite args + KofCall FUNCTION.
 */
final class ExpressionNamespaceCallLowerer {

    private ExpressionNamespaceCallLowerer() {}

    /** Diagnóstico de gap (XXX00x) — posição da chamada, mensagem e código prontos. */
    private static void gapError(CompilerDriver driver, MethodCallExpr mc, String msg, String code) {
        if (driver.currentDiagnostics == null) return;
        SourcePosition p = mc.position();
        driver.currentDiagnostics.error(p != null ? p.file() : "",
                p != null ? p.line() : 0, p != null ? p.column() : 0, 0, msg, code);
    }

    /**
     * §979: the pattern argument when it is provably a string literal (then the
     * native literal search either IS the contract or is refused by the caller),
     * {@code null} when the compiler cannot prove it — a dynamic pattern must not
     * be answered by a literal search.
     */
    private static String literalPattern(MethodCallExpr mc) {
        if (mc.arguments().size() < 2) return "";
        return mc.arguments().get(1) instanceof LiteralExpr lit
                && lit.kind() == ConcreteLiteralKind.STRING ? lit.value() : null;
    }

    /** Emite todos os argumentos da chamada, devolvendo o localIdx atualizado. */
    private static int emitArgs(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
            String owner, int localIdx, List<IRLocalVariable> locals) {
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        return localIdx;
    }

    static int lowerScheduler(CompilerDriver driver, MethodCallExpr mc, IdentifierExpr rid,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofScheduler.SchedulerCall schedCall = KofScheduler.staticCall(mc.methodName(), argTypes);
    if (schedCall != null) {
        if (!KofScheduler.supportedOn(schedCall.function(), driver.target)) {
            String code = KofScheduler.gapCode(schedCall.function());
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + code + ")", code);
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(KofScheduler.SCHEDULER, schedCall.function(), schedCall.parameterTypes(),
                schedCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
    }

    static int lowerGpu(CompilerDriver driver, MethodCallExpr mc, IdentifierExpr rid,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    if (System.getProperty("kof.trace") != null) {
        System.err.println("GPU call " + mc.methodName() + " argTypes=" + argTypes);
    }
    // Unknown (var sem tipo inferido no lowering) casa com
    // qualquer array: o staticCall exige tipos concretos, mas
    // o `var a = new Long[4]` pode chegar como Unknown quando
    // o local foi registrado antes do NewArray. Substitui
    // Unknown por Long[]/Int[] conforme o nome do método.
    List<Type> candidate = new ArrayList<>();
    boolean hasUnknown = false;
    for (Type t : argTypes) {
        if (t instanceof Type.UnknownType) { hasUnknown = true; break; }
    }
    if (hasUnknown) {
        Type arrType = "dispatchMatmul64".equals(mc.methodName())
                ? new Type.ArrayType(Type.PrimitiveType.LONG)
                : new Type.ArrayType(Type.PrimitiveType.INT);
        for (Type t : argTypes) {
            candidate.add(t instanceof Type.UnknownType ? arrType : t);
        }
        argTypes = candidate;
    }
    KofGpu.GpuCall gpuCall = KofGpu.staticCall(mc.methodName(), argTypes);
    if (gpuCall != null) {
        if (!KofGpu.supportedOn(driver.target)) {
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (GPU001)", "GPU001");
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(KofGpu.GPU, gpuCall.function(), gpuCall.parameterTypes(),
                gpuCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
    }

    static int lowerSecurity(CompilerDriver driver, MethodCallExpr mc, IdentifierExpr rid,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
    if (secCall != null) {
        if (!KofSecurity.supportedOn(secCall.function(), driver.target)) {
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + KofSecurity.gapCode(secCall.function()) + ")",
                    KofSecurity.gapCode(secCall.function()));
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.security", "Security", List.of()),
                secCall.function(), secCall.parameterTypes(), secCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
    }

    static int lowerValidation(CompilerDriver driver, MethodCallExpr mc, IdentifierExpr rid,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
    if (vCall != null) {
        if (!KofValidation.supportedOn(vCall.function(), driver.target, literalPattern(mc))) {
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + KofValidation.gapCode(vCall.function()) + ")",
                    KofValidation.gapCode(vCall.function()));
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.validation", "Validation", List.of()),
                vCall.function(), vCall.parameterTypes(), vCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
    }

    static int lowerObservability(CompilerDriver driver, MethodCallExpr mc, IdentifierExpr rid,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
    if (oCall != null) {
        if (!KofObservability.supportedOn(oCall.function(), driver.target)) {
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + KofObservability.gapCode(oCall.function()) + ")",
                    KofObservability.gapCode(oCall.function()));
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.observability", "Observability", List.of()),
                oCall.function(), oCall.parameterTypes(), oCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
    }

    static int lowerTetris(CompilerDriver driver, MethodCallExpr mc, IdentifierExpr rid,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
    KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
            mc.arguments().size());
    if (tetrisCall != null) {
        if (!KofTetris.supportedOn(driver.target)) {
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + KofTetris.gapCode() + ")",
                    KofTetris.gapCode());
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.tetris", "Tetris", List.of()),
                tetrisCall.function(), tetrisCall.parameterTypes(), tetrisCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
    }

    static int lowerImage(CompilerDriver driver, MethodCallExpr mc, IdentifierExpr rid,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
    KofImage.ImageCall imageCall = KofImage.staticMethod(rid.name(), mc.methodName(),
            mc.arguments().size());
    if (imageCall != null) {
        if (!KofImage.supportedOn(driver.target)) {
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": no image codec runtime on the " + driver.target
                    + " target yet (" + KofImage.gapCode() + ")",
                    KofImage.gapCode());
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.image", "Image", List.of()),
                imageCall.function(), imageCall.parameterTypes(), imageCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
    }
}
