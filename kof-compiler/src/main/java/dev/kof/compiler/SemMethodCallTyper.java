package dev.kof.compiler;

import dev.kof.compiler.SymbolTable.LocalVariableSymbol;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipos de MethodCallExpr, extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Mantém a ordem exata dos branches do switch
 * original (diagnósticos SEM0xx na mesma sequência).
 */
public final class SemMethodCallTyper {

    private SemMethodCallTyper() {}

    static final String SSE_CONNECTION_TYPE =
            "dev.kof.runtime.KofRuntime$SseConnection";

    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        // F10: métodos de instância do handle de process.spawn
        if (mc.receiver() != null) {
            Type recv = SemExpressionTyper.inferType(sa, mc.receiver(), scope);
            // SG-005: deref de T? sem narrowing é erro — null safety é por
            // narrowing (`if (x != null)` re-tipa o símbolo no escopo filho,
            // StatementAnalyzer). Se o receiver AINDA é NullableType aqui, o
            // acesso é direto e seria NPE em runtime. Antes o lowering
            // desembrulhava silenciosamente (ExpressionTyper) — advisory.
            if (recv instanceof Type.NullableType && sa.diagnostics() != null) {
                sa.diagnostics().error("", 0, 0, 0,
                        "receiver is nullable (T?); narrow first: if (x != null) { x.method() }",
                        "SEM049");
            }
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
                for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(), argTypes);
                if (hm != null) return hm.returnType();
            }
            // Canais tipados: c.send(v) / c.receive() -> T
            if (BuiltinTypes.isChannel(recv)) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                if ("send".equals(mc.methodName())) return Type.PrimitiveType.VOID;
                if ("receive".equals(mc.methodName())) return BuiltinTypes.channelElement(recv);
            }
            // Map<K,V>: get() devolve V? para valores de referência (ausência = null,
            // narrowing via if (x != null)); primitivos/UI não representam ausência
            if (BuiltinTypes.isMap(recv)) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                Type valueType = BuiltinTypes.mapValue(recv);
                // mapOf() nasce Map<Unknown,Unknown>: o primeiro put() pina os
                // tipos no SÍMBOLO do local (espelha o pin de IR no emit,
                // CollectionCallLowerer) — sem isso get() permanecia
                // Nullable(Unknown) no cache semântico enquanto o emit já
                // via o tipo concreto (divergência typer/emit, VerifyError)
                if ("put".equals(mc.methodName()) && valueType instanceof Type.UnknownType
                        && mc.arguments().size() == 2
                        && mc.receiver() instanceof IdentifierExpr rid) {
                    Type putKey = sa.expressionTypes().get(mc.arguments().get(0));
                    Type putValue = sa.expressionTypes().get(mc.arguments().get(1));
                    if (putKey != null && putValue != null
                            && !(putKey instanceof Type.UnknownType)
                            && !(putValue instanceof Type.UnknownType)
                            && sa.currentScope() != null) {
                        SymbolTable scopeTbl = sa.currentScope();
                        // resolve o símbolo em qualquer escopo ancestral, mas
                        // atualiza no escopo que O DEFINE (updateLocalType só
                        // mexe no escopo dono — retorna false caso contrário)
                        SymbolTable owner = null;
                        for (SymbolTable s = scopeTbl; s != null; s = s.parent()) {
                            if (s.hasLocal(rid.name())) { owner = s; break; }
                        }
                        if (owner != null && owner.updateLocalType(rid.name(),
                                new Type.ClassType("kof", "Map", List.of(putKey, putValue)))) {
                            valueType = putValue;
                        }
                    }
                }
                if ("get".equals(mc.methodName())) {
                    // SG-008 (bug 87): get() devolve V? para TODO valor —
                    // ausência é null comparável, nunca NPE por unbox
                    return new Type.NullableType(valueType);
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
