package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arquivo de apoio do lowering de UM método (REFACTOR-500 FASE 4).
 * O estado por método vive em {@link MethodCtx}, o estado por módulo em
 * {@link JsLoweringContext}; os marcadores de pilha (NewPending/DupMarker)
 * e a exceção de controle (StatementEnd) acompanham.
 */
/** Região de um loop em lowering: início, continue e fim. */
final class LoopCtx {
    final LabelId start;
    final LabelId continueLabel;
    final LabelId end;

    LoopCtx(LabelId start, LabelId continueLabel, LabelId end) {
        this.start = start;
        this.continueLabel = continueLabel;
        this.end = end;
    }
}

/**
 * A pending `new T` awaiting its <init> call: [NewPending, args...] or
 * [NewPending, DupMarker, args...] — lowered to `new T(args)`.
 */
record NewPending(String typeName) {
}

final class DupMarker {
}

/**
 * Thrown when a void call (or a constructor super call) completes the
 * current statement.
 */
final class StatementEnd extends RuntimeException {
    final JsIr.JsExpression call;

    StatementEnd(JsIr.JsExpression call) {
        this.call = call;
    }
}

final class MethodCtx {
    final JsLoweringContext lc;
    final List<KofOperation> ops;
    final Map<Integer, String> localNames = new HashMap<>();
    final Map<Integer, String> rawLocalNames = new HashMap<>();
    final Set<Integer> declared = new HashSet<>();
    final Set<String> usedNames = new HashSet<>();
    final List<String> tempDecls = new ArrayList<>();
    final List<LoopCtx> loops = new ArrayList<>();
    final boolean instanceMethod;
    final String kofClassName;
    final String methodName;
    final int paramCount;
    final boolean recordClass;
    final boolean isAsync;
    /** slots of lambda capture fields (come before the real parameters) */
    final Set<Integer> captureSlots = new HashSet<>();
    int tempCounter = 0;

    MethodCtx(JsLoweringContext lc, IRMethod method, IRClass clazz) {
        this.lc = lc;
        this.ops = new ArrayList<>(method.basicBlocks().stream()
                .flatMap(b -> b.operations().stream()).toList());
        this.instanceMethod = clazz != null && !JsLoweringContext.isMainClass(clazz)
                && (method.accessFlags() & AccessFlags.STATIC) == 0;
        this.kofClassName = clazz == null ? null : clazz.name();
        this.methodName = method.name();
        this.paramCount = method.parameterTypes().size();
        this.recordClass = clazz != null && "java/lang/Record".equals(clazz.superName());
        String asyncKey = clazz == null
                ? "#" + method.name() + "/" + method.parameterTypes().size()
                : JsLoweringContext.asyncMethodKey(clazz, method);
        this.isAsync = lc.asyncMethods.getOrDefault(asyncKey, false);
        // lambda synthetic classes hold captured locals as private final
        // fields at the first slots; the real parameters come after them.
        Set<String> captureFields = new HashSet<>();
        if (clazz != null && clazz.name() != null
                && (clazz.name().startsWith("Lambda") || clazz.name().startsWith("LambdaTask"))) {
            for (IRField f : clazz.fields()) {
                if ((f.accessFlags() & AccessFlags.PRIVATE) != 0
                        && (f.accessFlags() & AccessFlags.FINAL) != 0) {
                    captureFields.add(f.name());
                }
            }
        }
        for (IRLocalVariable lv : method.localVariables()) {
            rawLocalNames.put(lv.index(), lv.name());
            if (instanceMethod && lv.index() == 0) {
                localNames.put(lv.index(), "this");
                continue;
            }
            if (captureFields.contains(lv.name()) && lv.index() < 1 + captureFields.size()
                    && !"<init>".equals(method.name())) {
                // invoke(): the captures are fields copied to locals before
                // the real params — they are NOT the method's parameters.
                // <init>() receives the captures AS its parameters.
                captureSlots.add(lv.index());
            }
            localNames.put(lv.index(), uniqueName(JsTypeMapper.sanitizeName(lv.name())));
        }
    }

    String uniqueName(String base) {
        String name = base;
        int n = 1;
        while (!usedNames.add(name)) {
            name = base + "_" + (n++);
        }
        return name;
    }

    String freshTemp() {
        String name = uniqueName("__kof_t" + (tempCounter++));
        tempDecls.add(name);
        return name;
    }

    LoopCtx currentLoop() {
        return loops.isEmpty() ? null : loops.get(loops.size() - 1);
    }

    boolean isLoopLabel(LabelId label) {
        for (LoopCtx loop : loops) {
            if (label.equals(loop.start) || label.equals(loop.continueLabel) || label.equals(loop.end)) {
                return true;
            }
        }
        return false;
    }

    boolean isLoopEnd(LabelId label) {
        for (LoopCtx loop : loops) {
            if (label.equals(loop.end)) return true;
        }
        return false;
    }

    boolean hasClassMethod(String kofClassName, String method) {
        Set<String> names = lc.classMethodNames.get(kofClassName);
        return names != null && names.contains(method);
    }
}
