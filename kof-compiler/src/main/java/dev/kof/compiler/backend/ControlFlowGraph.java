package dev.kof.compiler.backend;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.LabelId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structured control-flow graph over the linear op stream (Phase A of
 * {@code docs/development/assembly-optimization-plan.md}).
 *
 * <p>The IR is a linear, stack-based op stream with label ops; every backend
 * consumes it in that same order and the existing {@link Optimizer} works on
 * the FLATTENED list. This class adds the structured foundation the plan's
 * Phase A calls for — a basic-block partition with successor edges — WITHOUT
 * changing any emitted behavior: it is a pure read of the op stream, built
 * additively so later phases (dataflow, register allocation, dead-code on the
 * graph) can consume it while the linear passes stay untouched.
 *
 * <p>Block boundaries follow the classic leader rule for a linear stream:
 * <ul>
 *   <li>the first op is a leader;</li>
 *   <li>the op right after a terminator ({@link KofJump},
 *       {@link KofConditionalJump}, {@link KofReturn}, {@link KofReturnVoid},
 *       {@link KofThrow}) is a leader;</li>
 *   <li>any op carrying a {@link KofLabel} whose id is the target of a jump is
 *       a leader.</li>
 * </ul>
 *
 * <p>Successors: a jump has one (its target block); a conditional jump has two
 * (true/false); a return/throw has none; any other block falls through to the
 * next block in stream order. The graph is cross-target by construction — it
 * reads only the shared IR, never an ISA.
 */
public final class ControlFlowGraph {

    /** A basic block: the ops it owns plus the indices of its successors. */
    public record Block(int index, List<KofOperation> operations, List<Integer> successors) {
    }

    private final List<Block> blocks;
    private final Map<LabelId, Integer> labelToBlock;

    private ControlFlowGraph(List<Block> blocks, Map<LabelId, Integer> labelToBlock) {
        this.blocks = List.copyOf(blocks);
        this.labelToBlock = Map.copyOf(labelToBlock);
    }

    public List<Block> blocks() {
        return blocks;
    }

    /** The block that begins at {@code label}, or {@code -1} if unlabelled. */
    public int blockOf(LabelId label) {
        return labelToBlock.getOrDefault(label, -1);
    }

    /** Builds the CFG from a method's blocks (flattened in order, as every
     *  backend consumes them). */
    public static ControlFlowGraph of(List<IRBasicBlock> basicBlocks) {
        List<KofOperation> ops = new ArrayList<>();
        for (IRBasicBlock b : basicBlocks) {
            ops.addAll(b.operations());
        }
        return ofOps(ops);
    }

    /** Builds the CFG from a flat op list. */
    public static ControlFlowGraph ofOps(List<KofOperation> ops) {
        // 1) leaders: op 0, ops after a terminator, and every jump target label.
        Set<Integer> jumpTargets = new HashSet<>();
        for (KofOperation op : ops) {
            if (op instanceof KofJump j) {
                jumpTargets.add(labelIndexOf(ops, j.target()));
            } else if (op instanceof KofConditionalJump cj) {
                jumpTargets.add(labelIndexOf(ops, cj.trueLabel()));
                jumpTargets.add(labelIndexOf(ops, cj.falseLabel()));
            }
        }

        boolean[] leader = new boolean[ops.size()];
        if (!ops.isEmpty()) leader[0] = true;
        for (int i = 0; i < ops.size(); i++) {
            if (isTerminator(ops.get(i)) && i + 1 < ops.size()) {
                leader[i + 1] = true;
            }
        }
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i) instanceof KofLabel && jumpTargets.contains(i)) {
                leader[i] = true;
            }
        }
        // §709: um KofCatchStart é a entrada do HANDLER de exceção (o IR não
        // emite um KofLabel para o handlerLabel — o handler é o próprio op). Ele
        // é fronteira de bloco e a origem da aresta de EXCEÇÃO try→handler; sem
        // isto blockOf(handlerLabel) devolve -1 e a liveness subestima (um slot
        // lido só no catch vira "morto" → o dead-store o remove).
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i) instanceof KofCatchStart) {
                leader[i] = true;
            }
        }
        // §709 (face finally): o KofTryStart é fronteira de bloco. A região protegida começa
        // NELE; uma exceção pode ocorrer em qualquer ponto dela, então um store
        // ANTERIOR ao try que inicializa um slot lido no handler está VIVO (não
        // pode ser removido como dead store). Sem a fronteira, esse store cai no
        // MESMO bloco do try e o walk backward o mata.
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i) instanceof KofTryStart) {
                leader[i] = true;
            }
        }

        // 2) partition into blocks.
        List<List<KofOperation>> raw = new ArrayList<>();
        int cur = -1;
        for (int i = 0; i < ops.size(); i++) {
            if (leader[i] || cur < 0) {
                raw.add(new ArrayList<>());
                cur = raw.size() - 1;
            }
            raw.get(cur).add(ops.get(i));
        }

        // 3) label -> block index (the block that STARTS at the label op).
        Map<LabelId, Integer> labelToBlock = new HashMap<>();
        for (int b = 0; b < raw.size(); b++) {
            KofOperation first = raw.get(b).get(0);
            if (first instanceof KofLabel kl) {
                labelToBlock.putIfAbsent(kl.label(), b);
            }
        }
        // §709: mapeia o handlerLabel de cada try ao bloco do seu KofCatchStart.
        for (int b = 0; b < raw.size(); b++) {
            for (KofOperation op : raw.get(b)) {
                if (op instanceof KofCatchStart cs) {
                    labelToBlock.putIfAbsent(cs.handlerLabel(), b);
                }
            }
        }

        // 4) successors.
        List<Block> blocks = new ArrayList<>();
        for (int b = 0; b < raw.size(); b++) {
            List<KofOperation> body = raw.get(b);
            KofOperation last = body.get(body.size() - 1);
            List<Integer> succ = new ArrayList<>();
            if (last instanceof KofJump j) {
                int t = labelToBlock.getOrDefault(j.target(), -1);
                if (t >= 0) succ.add(t);
            } else if (last instanceof KofConditionalJump cj) {
                int t = labelToBlock.getOrDefault(cj.trueLabel(), -1);
                int f = labelToBlock.getOrDefault(cj.falseLabel(), -1);
                if (t >= 0) succ.add(t);
                if (f >= 0) succ.add(f);
            } else if (!isTerminator(last)) {
                if (b + 1 < raw.size()) succ.add(b + 1);   // fall-through
            }
            blocks.add(new Block(b, List.copyOf(body), List.copyOf(succ)));
        }
        return new ControlFlowGraph(blocks, labelToBlock);
    }

    private static int labelIndexOf(List<KofOperation> ops, LabelId label) {
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i) instanceof KofLabel kl && kl.label().equals(label)) return i;
        }
        return -1;
    }

    private static boolean isTerminator(KofOperation op) {
        return op instanceof KofJump || op instanceof KofConditionalJump
                || op instanceof KofReturn || op instanceof KofReturnVoid
                || op instanceof KofThrow;
    }
}
