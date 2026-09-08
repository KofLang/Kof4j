package dev.kof.compiler.js;
import dev.kof.compiler.AccessFlags;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRField;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.LabelId;
import dev.kof.compiler.js.JsLoweringContext;
import dev.kof.compiler.js.JsTypeMapper;
import dev.kof.compiler.KofTryStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
public final class MethodCtx {
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
        // known-bugs #63: os PARÂMETROS já estão ligados pela assinatura da
        // função JS, então o primeiro store num slot de parâmetro é uma
        // atribuição, nunca uma declaração (`let a = ...` redeclara e o
        // SyntaxError derruba o módulo inteiro). Deriva da MESMA fonte que
        // monta a assinatura (parameterSlots), para que os dois não possam
        // divergir.
        declared.addAll(parameterSlots());
    }

    /**
     * Slots ligados pela assinatura da função JS, na ordem. É a fonte única
     * usada tanto para emitir a lista de parâmetros quanto para saber quais
     * slots já estão declarados (known-bugs #63).
     *
     * <p>As capturas ficam de fora: em {@code invoke()} elas são locais
     * copiados dos campos e precisam manter o próprio {@code let}.
     */
    List<Integer> parameterSlots() {
        if ("main".equals(methodName) && paramCount == 1) {
            // O parâmetro String[] injetado no main não é parâmetro de fonte.
            return List.of();
        }
        List<Integer> slots = new ArrayList<>();
        int start = instanceMethod ? 1 : 0;
        for (int i = start; i < localNames.size() && slots.size() < paramCount; i++) {
            if (captureSlots.contains(i)) continue;
            if (localNames.get(i) != null) slots.add(i);
        }
        return slots;
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

    /** true se `label` é o endLabel de QUALQUER try no método (try aninhado/outer). */
    boolean isTryEndLabel(LabelId label) {
        for (KofOperation op : ops) {
            if (op instanceof KofTryStart ts && ts.endLabel().equals(label)) return true;
        }
        return false;
    }

    boolean hasClassMethod(String kofClassName, String method) {
        Set<String> names = lc.classMethodNames.get(kofClassName);
        return names != null && names.contains(method);
    }
}
