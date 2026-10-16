package dev.kof.compiler.backend;

import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Liveness analysis over the structured CFG (Phase C of
 * {@code docs/development/assembly-optimization-plan.md} §5: "liveness
 * analysis").
 *
 * <p>The IR addresses locals by slot index; a slot is LIVE at a program point
 * when its current value may still be read before being overwritten. This class
 * computes that per block ({@code liveIn}/{@code liveOut}) and per operation
 * ({@code liveAfter}), using the classic backward dataflow equations over the
 * {@link ControlFlowGraph} built in Phase A:
 *
 * <pre>
 *   liveOut(b) = ∪ liveIn(s)   for every successor s of b
 *   liveIn(b)  = use(b) ∪ (liveOut(b) \ def(b))
 * </pre>
 *
 * <p>{@code use(b)} is the set of slots read before being written in the block
 * (upward-exposed uses); {@code def(b)} the slots written. The only defs/uses
 * are {@link KofStoreLocal}/{@link KofCatchStart} (defs) and
 * {@link KofLoadLocal} (uses) — the IR is stack-based, so a local's value lives
 * from its store to its last load.
 *
 * <p><b>Exception edges:</b> a {@link KofTryStart} adds a conservative edge from
 * its block to the handler block, so a slot read only in a handler is never
 * wrongly considered dead (over-approximating liveness is safe; under-
 * approximating is not).
 *
 * <p>This is a pure read of the IR: it allocates nothing in the emitted code and
 * changes no behavior. It is the analysis the Phase C register allocator will
 * consume.
 */
public final class LivenessAnalysis {

    private final List<Set<Integer>> liveIn;
    private final List<Set<Integer>> liveOut;
    private final Map<KofOperation, Set<Integer>> liveAfter;

    private LivenessAnalysis(List<Set<Integer>> liveIn, List<Set<Integer>> liveOut,
                             Map<KofOperation, Set<Integer>> liveAfter) {
        this.liveIn = liveIn;
        this.liveOut = liveOut;
        this.liveAfter = liveAfter;
    }

    /** Slots live on entry to a block. */
    public Set<Integer> liveIn(int block) {
        return liveIn.get(block);
    }

    /** The number of blocks the analysis covers. */
    public int blockCount() {
        return liveIn.size();
    }

    /** Slots live on exit from a block. */
    public Set<Integer> liveOut(int block) {
        return liveOut.get(block);
    }

    /** Slots live immediately after {@code op}; empty for an unknown op. */
    public Set<Integer> liveAfter(KofOperation op) {
        return liveAfter.getOrDefault(op, Set.of());
    }

    /** True when {@code slot} is live immediately after {@code op}. */
    public boolean isLiveAfter(KofOperation op, int slot) {
        return liveAfter(op).contains(slot);
    }

    public static LivenessAnalysis of(ControlFlowGraph cfg) {
        List<ControlFlowGraph.Block> blocks = cfg.blocks();
        int n = blocks.size();

        List<Set<Integer>> use = new ArrayList<>(n);
        List<Set<Integer>> def = new ArrayList<>(n);
        for (ControlFlowGraph.Block b : blocks) {
            Set<Integer> u = new LinkedHashSet<>();
            Set<Integer> d = new LinkedHashSet<>();
            for (KofOperation op : b.operations()) {
                for (int s : useOf(op)) {
                    if (!d.contains(s)) u.add(s);
                }
                d.addAll(defOf(op));
            }
            use.add(u);
            def.add(d);
        }

        // Successors, plus conservative exception edges (try -> handler).
        List<List<Integer>> succ = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            succ.add(new ArrayList<>(blocks.get(i).successors()));
        }
        // §709/§712: um try tem MAIS de um handler — o catch do usuário E o
        // catch-all SINTÉTICO que emula o `finally` (handlers com handlerLabel
        // DISTINTOS). Adicionar a aresta de exceção só ao handlerLabel do
        // KofTryStart deixava o bloco do FINALLY fora do fluxo, então um slot
        // lido SÓ no finally (ou inicializado antes do try) parecia morto e o
        // LivenessDeadStores o removia → o handler do finally via o slot como
        // `top` (JVM VerifyError). Varre a REGIÃO do try (até o KofTryEnd
        // casado, respeitando aninhamento) e liga a TODOS os KofCatchStart.
        List<KofOperation> flat = new ArrayList<>();
        for (ControlFlowGraph.Block b : blocks) flat.addAll(b.operations());
        java.util.Map<KofOperation, Integer> opBlock = new java.util.IdentityHashMap<>();
        for (int b = 0; b < n; b++) {
            for (KofOperation o : blocks.get(b).operations()) opBlock.put(o, b);
        }
        for (int i = 0; i < flat.size(); i++) {
            if (!(flat.get(i) instanceof KofTryStart ts)) continue;
            int src = opBlock.getOrDefault(flat.get(i), -1);
            if (src < 0) continue;
            // o handlerLabel declarado do try (o catch do usuário; um shape
            // sintético pode não ter KofCatchStart).
            int direct = cfg.blockOf(ts.handlerLabel());
            if (direct >= 0 && !succ.get(src).contains(direct)) succ.get(src).add(direct);
            // e TODOS os KofCatchStart da região (o catch do usuário E o
            // catch-all sintético que emula o `finally`, com handlerLabels
            // DISTINTOS) — §712: sem o handler do finally, um slot lido só nele
            // parece morto e o dead-store o remove → `top` no handler.
            int depth = 1;
            for (int j = i + 1; j < flat.size() && depth > 0; j++) {
                KofOperation o = flat.get(j);
                if (o instanceof KofTryStart) { depth++; }
                else if (o instanceof KofTryEnd) { depth--; }
                else if (o instanceof KofCatchStart cs && depth == 1) {
                    int h = cfg.blockOf(cs.handlerLabel());
                    if (h >= 0 && !succ.get(src).contains(h)) succ.get(src).add(h);
                }
            }
        }

        List<Set<Integer>> liveIn = new ArrayList<>(n);
        List<Set<Integer>> liveOut = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            liveIn.add(new LinkedHashSet<>());
            liveOut.add(new LinkedHashSet<>());
        }

        // §709 (face finally): para cada bloco, os handlers de exceção dos KofTryStart nele —
        // o liveIn do handler é vivo no INÍCIO do bloco (a exceção pode ocorrer
        // antes de qualquer def da região protegida), então NÃO pode ser morto
        // pelos defs do bloco.
        // §709/§712: TODOS os handlers de exceção de cada bloco — o catch do
        // usuário E o catch-all sintético que emula o `finally` (handlerLabels
        // DISTINTOS). Um slot lido só no finally precisa contar como vivo no
        // INÍCIO do bloco protegido (a exceção pode disparar antes de qualquer
        // def), senão o dead-store o remove e o handler vê `top` (VerifyError).
        List<List<Integer>> tryHandlers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) tryHandlers.add(new ArrayList<>());
        for (int i = 0; i < flat.size(); i++) {
            if (!(flat.get(i) instanceof KofTryStart)) continue;
            int src = opBlock.getOrDefault(flat.get(i), -1);
            if (src < 0) continue;
            int depth = 1;
            for (int j = i + 1; j < flat.size() && depth > 0; j++) {
                KofOperation o = flat.get(j);
                if (o instanceof KofTryStart) { depth++; }
                else if (o instanceof KofTryEnd) { depth--; }
                else if (o instanceof KofCatchStart cs && depth == 1) {
                    int h = cfg.blockOf(cs.handlerLabel());
                    if (h >= 0 && !tryHandlers.get(src).contains(h)) tryHandlers.get(src).add(h);
                }
            }
        }

        // Backward fixpoint (deterministic order; iterate until stable).
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = n - 1; i >= 0; i--) {
                Set<Integer> out = new LinkedHashSet<>();
                for (int s : succ.get(i)) out.addAll(liveIn.get(s));
                Set<Integer> in = new LinkedHashSet<>(use.get(i));
                for (int s : out) {
                    if (!def.get(i).contains(s)) in.add(s);
                }
                for (int h : tryHandlers.get(i)) {
                    in.addAll(liveIn.get(h));
                }
                if (!out.equals(liveOut.get(i))) {
                    liveOut.set(i, out);
                    changed = true;
                }
                if (!in.equals(liveIn.get(i))) {
                    liveIn.set(i, in);
                    changed = true;
                }
            }
        }

        // Per-op liveness: walk each block backward from its liveOut.
        Map<KofOperation, Set<Integer>> liveAfter = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            Set<Integer> live = new LinkedHashSet<>(liveOut.get(i));
            List<KofOperation> ops = blocks.get(i).operations();
            for (int j = ops.size() - 1; j >= 0; j--) {
                KofOperation op = ops.get(j);
                liveAfter.put(op, Set.copyOf(live));
                live.removeAll(defOf(op));
                live.addAll(useOf(op));
            }
        }

        return new LivenessAnalysis(
                List.copyOf(liveIn), List.copyOf(liveOut),
                Collections.unmodifiableMap(liveAfter));
    }

    private static Set<Integer> defOf(KofOperation op) {
        if (op instanceof KofStoreLocal sl) return Set.of(sl.index());
        if (op instanceof KofCatchStart cs) return Set.of(cs.localIndex());
        return Set.of();
    }

    private static Set<Integer> useOf(KofOperation op) {
        if (op instanceof KofLoadLocal ll) return Set.of(ll.index());
        return Set.of();
    }

    /** Convenience: build the analysis directly from a flat op list. */
    public static LivenessAnalysis ofOps(List<KofOperation> ops) {
        return of(ControlFlowGraph.ofOps(ops));
    }

    /**
     * The slots LIVE ACROSS A CALL: a slot live immediately after a {@link KofCall}
     * may be clobbered by the call if it sits in a caller-saved register, so the
     * allocator must place it in a callee-saved register or spill it. A call that
     * returns void has nothing live after it in the value sense, but a slot read
     * later is still live across the call — {@link #liveAfter} captures that.
     */
    public Set<Integer> liveAcrossCalls(List<KofOperation> ops) {
        Set<Integer> out = new TreeSet<>();
        for (KofOperation op : ops) {
            if (op instanceof KofCall) out.addAll(liveAfter(op));
        }
        return out;
    }
}
