package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IR optimization passes (docs/performance.md §8, §12, §14).
 *
 * The IR is a linear, stack-based op stream with label ops; every backend
 * consumes it in that same order. The optimizer therefore works on the
 * flattened op list and performs only semantics-preserving transformations:
 *
 *  - constant folding (arithmetic, comparisons, unary ops, shift)
 *  - branch simplification (literal conditions → direct jumps)
 *  - dead stack-effect elimination (pure push + pop, dup + pop,
 *    load/store round trips)
 *  - unreachable code elimination (CFG reachability, exception-region aware)
 *  - jump-to-next elimination
 *
 * Debug positions of removed ops are dropped; a folded result keeps the
 * position of the first consumed op.
 */
public final class Optimizer {

    public static IRModule optimize(IRModule module) {
        List<IRClass> classes = new ArrayList<>();
        for (IRClass cls : module.classes()) {
            List<IRMethod> methods = new ArrayList<>();
            for (IRMethod m : cls.methods()) {
                methods.add(optimizeMethod(m));
            }
            classes.add(new IRClass(cls.name(), cls.superName(), cls.interfaces(),
                    cls.accessFlags(), cls.fields(), methods,
                    cls.innerClasses(), cls.signature(), cls.typeId(), cls.annotations()));
        }
        return new IRModule(module.name(), classes, module.imports(), module.sourceName());
    }

    private static IRMethod optimizeMethod(IRMethod method) {
        if (method.basicBlocks().isEmpty()) return method;
        List<KofOperation> ops = new ArrayList<>();
        for (IRBasicBlock block : method.basicBlocks()) {
            ops.addAll(block.operations());
        }
        Map<KofOperation, SourcePosition> positions = new IdentityHashMap<>();
        if (method.debugInfo() != null) {
            positions.putAll(method.debugInfo().positions());
        }
        for (int i = 0; i < 8; i++) {
            List<KofOperation> next = passes(ops, positions);
            if (next.size() == ops.size() && next.equals(ops)) break;
            ops = next;
        }
        KofDebugInfo debugInfo = positions.isEmpty()
                ? KofDebugInfo.EMPTY
                : new KofDebugInfo(positions);
        return new IRMethod(method.name(), method.returnType(), method.parameterTypes(),
                method.accessFlags(), method.thrownExceptions(),
                List.of(new IRBasicBlock(0, ops)), method.localVariables(), debugInfo,
                method.annotations(), method.parameterAnnotations());
    }

    private static List<KofOperation> passes(List<KofOperation> ops,
                                             Map<KofOperation, SourcePosition> positions) {
        ops = OptimizerConstantFold.constantFold(ops, positions);
        ops = deadEffects(ops, positions);
        ops = reachability(ops, positions);
        ops = removeJumpToNext(ops, positions);
        return ops;
    }

    // ── Dead stack effects ────────────────────────────────────────────

    private static List<KofOperation> deadEffects(List<KofOperation> ops,
                                                  Map<KofOperation, SourcePosition> positions) {
        List<KofOperation> out = new ArrayList<>(ops.size());
        for (KofOperation op : ops) {
            if (op instanceof KofPop && !out.isEmpty()) {
                KofOperation prev = out.get(out.size() - 1);
                if (prev instanceof KofLoadLiteral || prev instanceof KofLoadLocal
                        || prev instanceof KofDup) {
                    out.remove(out.size() - 1);
                    positions.remove(prev);
                    continue;
                }
            }
            if (op instanceof KofStoreLocal sl && !out.isEmpty()) {
                KofOperation prev = out.get(out.size() - 1);
                if (prev instanceof KofLoadLocal ll && ll.index() == sl.index()
                        && ll.type().equals(sl.type())) {
                    out.remove(out.size() - 1);
                    positions.remove(prev);
                    continue;
                }
            }
            out.add(op);
        }
        // NOTE: store(slot); load(slot) round trips are intentionally kept
        // verbatim. Removing them (or rewriting them to dup; store) breaks
        // slot initialization: a later load of the slot would read an
        // uninitialized local (JVM VerifyError). Backends also pattern-match
        // on the store/load pairs of compiler temporaries (#switch, #recv).
        return out;
    }

    // ── Unreachable code elimination ──────────────────────────────────

    private static List<KofOperation> reachability(List<KofOperation> ops,
                                                   Map<KofOperation, SourcePosition> positions) {
        Map<LabelId, Integer> labelIndex = new HashMap<>();
        Set<LabelId> tryReferenced = new HashSet<>();
        for (int i = 0; i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            if (op instanceof KofLabel kl) {
                labelIndex.putIfAbsent(kl.label(), i);
            } else if (op instanceof KofTryStart ts) {
                tryReferenced.add(ts.startLabel());
                tryReferenced.add(ts.endLabel());
                tryReferenced.add(ts.handlerLabel());
            } else if (op instanceof KofCatchStart cs) {
                tryReferenced.add(cs.handlerLabel());
            }
        }

        boolean[] reachable = new boolean[ops.size()];
        java.util.ArrayDeque<Integer> work = new java.util.ArrayDeque<>();
        work.add(0);
        for (int i = 0; i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            if (op instanceof KofCatchStart) work.add(i);
            if (op instanceof KofLabel kl && tryReferenced.contains(kl.label())) work.add(i);
        }
        while (!work.isEmpty()) {
            int start = work.poll();
            if (start >= ops.size() || reachable[start]) continue;
            for (int j = start; j < ops.size(); j++) {
                if (reachable[j]) break;
                reachable[j] = true;
                KofOperation op = ops.get(j);
                if (op instanceof KofJump kj) {
                    Integer t = labelIndex.get(kj.target());
                    if (t != null) work.add(t);
                    break;
                }
                if (op instanceof KofConditionalJump cj) {
                    Integer t = labelIndex.get(cj.trueLabel());
                    if (t != null) work.add(t);
                    Integer f = labelIndex.get(cj.falseLabel());
                    if (f != null) work.add(f);
                    continue;
                }
                if (op instanceof KofReturn || op instanceof KofReturnVoid
                        || op instanceof KofThrow) {
                    break;
                }
            }
        }

        List<KofOperation> out = new ArrayList<>(ops.size());
        for (int i = 0; i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            if (reachable[i]) {
                out.add(op);
                continue;
            }
            if (op instanceof KofTryStart) {
                int depth = 1;
                for (int j = i + 1; j < ops.size() && depth > 0; j++) {
                    if (ops.get(j) instanceof KofTryStart) depth++;
                    else if (ops.get(j) instanceof KofTryEnd) depth--;
                    i = j;
                }
                positions.remove(op);
                continue;
            }
            if (op instanceof KofTryEnd || op instanceof KofCatchStart) {
                // Keep unbalanced region markers as dead code so the JVM
                // backend's try-stack stays balanced.
                out.add(op);
                continue;
            }
            if (op instanceof KofLabel kl && tryReferenced.contains(kl.label())) {
                out.add(op);
                continue;
            }
            positions.remove(op);
        }
        return out;
    }

    // ── Jump to next instruction ──────────────────────────────────────

    private static List<KofOperation> removeJumpToNext(List<KofOperation> ops,
                                                       Map<KofOperation, SourcePosition> positions) {
        List<KofOperation> out = new ArrayList<>(ops.size());
        for (int i = 0; i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            if (op instanceof KofJump kj && i + 1 < ops.size()
                    && ops.get(i + 1) instanceof KofLabel kl
                    && kl.label().equals(kj.target())) {
                positions.remove(op);
                continue;
            }
            out.add(op);
        }
        // Drop orphan labels: no remaining op references them and they are
        // not part of an exception region. Keeping a reachable orphan label
        // would confuse the JS backend's structural reconstruction.
        Set<LabelId> referenced = new HashSet<>();
        for (KofOperation op : out) {
            if (op instanceof KofJump kj) referenced.add(kj.target());
            else if (op instanceof KofConditionalJump cj) {
                referenced.add(cj.trueLabel());
                referenced.add(cj.falseLabel());
            } else if (op instanceof KofTryStart ts) {
                referenced.add(ts.startLabel());
                referenced.add(ts.endLabel());
                referenced.add(ts.handlerLabel());
            } else if (op instanceof KofCatchStart cs) {
                referenced.add(cs.handlerLabel());
            }
        }
        List<KofOperation> out2 = new ArrayList<>(out.size());
        for (KofOperation op : out) {
            if (op instanceof KofLabel kl && !referenced.contains(kl.label())) {
                positions.remove(op);
                continue;
            }
            out2.add(op);
        }
        return out2;
    }

    private Optimizer() {
    }
}