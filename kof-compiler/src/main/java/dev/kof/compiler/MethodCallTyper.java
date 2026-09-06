package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipos de MethodCallExpr, extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Mantém a ordem exata dos branches do switch
 * original (diagnósticos SEM0xx na mesma sequência).
 */
final class MethodCallTyper {

    private MethodCallTyper() {}

    static final String SSE_CONNECTION_TYPE =
            "dev.kof.runtime.KofRuntime$SseConnection";

    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        // F10: métodos de instância do handle de process.spawn
        if (mc.receiver() != null) {
            Type recv = ExpressionTyper.inferType(sa, mc.receiver(), scope);
            // bug 17: array não tem método get()/set() — a API é o
            // operador arr[i]. Antes o compilador aceitava e emitia
            // bytecode inválido (ClassFormatError no JVM, undefined
            // reference no Native).
            if (recv instanceof Type.ArrayType && sa.diagnostics() != null) {
                sa.diagnostics().error("", 0, 0, 0,
                        "array não tem método '" + mc.methodName()
                                + "()'; use o operador arr[i] / arr[i] = v",
                        "SEM028");
            }
            if (KofProcess.isHandle(recv)) {
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
                KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(), argTypes);
                if (hm != null) return hm.returnType();
            }
            // Canais tipados: c.send(v) / c.receive() -> T
            if (BuiltinTypes.isChannel(recv)) {
                for (ExpressionNode arg : mc.arguments()) ExpressionTyper.inferType(sa, arg, scope);
                if ("send".equals(mc.methodName())) return Type.PrimitiveType.VOID;
                if ("receive".equals(mc.methodName())) return BuiltinTypes.channelElement(recv);
            }
            // Map<K,V>: get() devolve V? para valores de referência (ausência = null,
            // narrowing via if (x != null)); primitivos/UI não representam ausência
            if (BuiltinTypes.isMap(recv)) {
                for (ExpressionNode arg : mc.arguments()) ExpressionTyper.inferType(sa, arg, scope);
                Type valueType = BuiltinTypes.mapValue(recv);
                if ("get".equals(mc.methodName())) {
                    return valueType instanceof Type.ClassType ct
                            && !KofUi.isUiType(ct) && !KofMedia.isHandleType(ct)
                            ? new Type.NullableType(valueType) : valueType;
                }
                if ("put".equals(mc.methodName()) || "remove".equals(mc.methodName())) return valueType;
                if ("size".equals(mc.methodName()) || "length".equals(mc.methodName())
                        || "count".equals(mc.methodName())) return Type.PrimitiveType.INT;
                if ("contains".equals(mc.methodName()) || "containsKey".equals(mc.methodName())
                        || "isEmpty".equals(mc.methodName())) return Type.PrimitiveType.BOOL;
                if ("clear".equals(mc.methodName())) return Type.PrimitiveType.VOID;
                if ("keys".equals(mc.methodName())) return new Type.ClassType("kof", "List",
                        List.of(BuiltinTypes.mapKey(recv)));
                if ("values".equals(mc.methodName())) return new Type.ClassType("kof", "List",
                        List.of(valueType));
            }
        }
        Type builtin = BuiltinCallTyper.infer(sa, mc, scope);
        if (builtin != null) return builtin;
        if (mc.receiver() != null) {
            Type member = MemberCallTyper.infer(sa, mc, scope);
            if (member != null) return member;
        }
        return BuiltinCallTyper.inferTail(sa, mc, scope);
    }
}
