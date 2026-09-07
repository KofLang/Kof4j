package dev.kof.compiler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Frame de execução do interpretador (pilha de operandos + locais + try
 * stack) e a exception table JVM: {@code handleException} espelha o
 * comportamento do bytecode (pc na região + tipo do catch, throw-as-String
 * via getMessage), {@code asThrowable} converte o valor do throw.
 */
final class KofInterpreterFrame {

    private final KofInterpreter interp;

    KofInterpreterFrame(KofInterpreter interp) {
        this.interp = interp;
    }

    static final class Frame {
        final List<KofOperation> ops;
        final Map<LabelId, Integer> labels = new HashMap<>();
        // LinkedList (não ArrayDeque): a pilha JVM guarda referências null
        // (ex.: função que retorna null) — ArrayDeque rejeita null.
        final Deque<Object> stack = new java.util.LinkedList<>();
        final Deque<TryRegion> tryStack = new ArrayDeque<>();
        Object[] locals;
        int pc;
        Frame(List<KofOperation> ops) { this.ops = ops; }
    }

    record TryRegion(int startPc, int endPc, int handlerPc,
                     String exceptionType, int localIndex, int stackDepth) {}

    /**
     * Monta o frame e resolve labels: KofLabel, e KofTryStart/KofCatchStart
     * materializam seus labels na própria posição (igual ao visitLabel do
     * emitter JVM) — não há KofLabel separada para eles.
     */
    Frame newFrame(List<KofOperation> ops) {
        Frame f = new Frame(ops);
        for (int i = 0; i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            if (op instanceof KofLabel kl) f.labels.putIfAbsent(kl.label(), i);
            else if (op instanceof KofTryStart ts) f.labels.putIfAbsent(ts.startLabel(), i);
            else if (op instanceof KofCatchStart cs) f.labels.putIfAbsent(cs.handlerLabel(), i);
        }
        return f;
    }

    TryRegion newTryRegion(Frame f, KofTryStart ts, int stackDepth) {
        return new TryRegion(f.labels.get(ts.startLabel()), f.labels.get(ts.endLabel()),
                f.labels.get(ts.handlerLabel()), ts.exceptionType(), ts.excLocalIndex(),
                stackDepth);
    }

    /** Espelha a exception table JVM: pc na região + tipo do catch. */
    boolean handleException(Frame f, Throwable t) {
        int at = f.pc - 1;
        for (TryRegion r : f.tryStack) {
            if (at < r.startPc() || at > r.endPc()) continue;
            Object exc = t;
            if (!catchMatches(r.exceptionType(), exc)) continue;
            while (f.stack.size() > r.stackDepth()) f.stack.pop();
            while (!f.tryStack.isEmpty() && f.tryStack.peek() != r) f.tryStack.pop();
            if (!f.tryStack.isEmpty()) f.tryStack.pop();
            if ("String".equals(r.exceptionType()) && exc instanceof RuntimeException re) {
                exc = re.getMessage();
            }
            f.stack.push(exc);
            f.pc = r.handlerPc();
            return true;
        }
        return false;
    }

    private boolean catchMatches(String kofType, Object exc) {
        if (kofType == null || kofType.isEmpty() || "Throwable".equals(kofType)
                || "Exception".equals(kofType) || "RuntimeException".equals(kofType)) {
            return exc instanceof Throwable;
        }
        if ("String".equals(kofType)) return exc instanceof RuntimeException;
        if (exc instanceof KofInterpreter.KofObj ko) {
            IRClass c = interp.classByInternal(ko.internalName());
            while (c != null) {
                if (c.name().endsWith("/" + kofType) || c.name().equals(kofType)) return true;
                c = interp.classByInternal(c.superName());
            }
            return false;
        }
        try {
            return Class.forName(kofType).isInstance(exc);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Throwable asThrowable(Object v) {
        if (v instanceof Throwable t) return t;
        if (v instanceof String s) return new RuntimeException(s);
        if (v instanceof KofInterpreter.KofObj ko) {
            Object msg = ko.fields.get("message");
            String simple = ko.internalName();
            int sl = simple.lastIndexOf('/');
            return new RuntimeException((sl >= 0 ? simple.substring(sl + 1) : simple)
                    + (msg != null ? ": " + msg : ""));
        }
        return new RuntimeException(String.valueOf(v));
    }
}
