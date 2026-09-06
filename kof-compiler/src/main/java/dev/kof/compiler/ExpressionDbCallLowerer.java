package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace KofDb (db.*) no emitExpression.
 */
final class ExpressionDbCallLowerer {

    private ExpressionDbCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
    KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
    if (dbCall != null) {
        if (!KofDb.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        ((IdentifierExpr) mc.receiver()).name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (" + KofDb.gapCode() + ")",
                        KofDb.gapCode());
            }
            return localIdx;
        }
        for (int i = 0; i < mc.arguments().size() && i < 2; i++) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
        }
        for (int i = 2; i < mc.arguments().size(); i++) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
            TypeEmitter.boxPrimitive(ops, argTypes.get(i));
        }
        if (KofDb.isQuery(mc.methodName())) {
            if (typed && !mc.typeArguments().isEmpty()) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.typeArguments().get(0)));
            } else {
                ops.add(new KofLoadLiteral(Type.UnknownType.UNKNOWN, null));
            }
        }
        List<Type> params = new ArrayList<>(dbCall.parameterTypes());
        Type retType = dbCall.returnType();
        if (KofDb.isQuery(mc.methodName())) {
            // o className (ou null) é sempre empurrado; o
            // param precisa estar na lista para o native
            // popar na ordem certa
            params.add(BuiltinTypes.STRING);
            if (typed) {
                retType = new Type.ClassType("kof", "List",
                        List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
            }
        }
        ops.add(new KofCall(new Type.ClassType("kof.db", "Db", List.of()),
                dbCall.function(), params, retType, KofCallKind.FUNCTION));
    }
    return localIdx;
    }
}