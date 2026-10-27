package dev.kof.compiler.backend;

import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;

import java.util.Map;
import java.util.TreeMap;

/**
 * Spill cost of a local slot (Phase C of
 * {@code docs/assembly-optimization-plan.md} §5: "spill
 * heuristics").
 *
 * <p>A slot's spill cost estimates how much the program pays if the slot is
 * pushed to memory instead of a register: each USE is a reload, and a use inside
 * a loop is paid on every iteration. The cost is therefore the number of uses
 * weighted by loop nesting depth ({@code 1} outside any loop, {@code 2} one loop
 * deep, and so on). A slot never used costs {@code 0} — the cheapest to spill.
 *
 * <p>Loop depth is derived from the {@link ControlFlowGraph}: because blocks are
 * numbered in stream order, a back edge is an edge to a block with an index
 * {@code <=} the source's; a block lies in a loop at depth {@code d} when it is
 * covered by {@code d} such back-edge intervals. This is a deterministic,
 * conservative approximation (it may over-count nesting, never under-count a
 * loop), which is the safe direction for a spill heuristic.
 *
 * <p>Pure read of the IR — allocates nothing, changes no behavior.
 */
public final class SpillCost {

    private final Map<Integer, Integer> cost;

    private SpillCost(Map<Integer, Integer> cost) {
        this.cost = cost;
    }

    /** The spill cost of a slot ({@code 0} if it has no uses). */
    public int costOf(int slot) {
        return cost.getOrDefault(slot, 0);
    }

    /** All slots with a recorded cost, ascending. */
    public Map<Integer, Integer> costs() {
        return Map.copyOf(cost);
    }

    public static SpillCost of(ControlFlowGraph cfg) {
        int[] depth = loopDepth(cfg);
        Map<Integer, Integer> cost = new TreeMap<>();
        int b = 0;
        for (ControlFlowGraph.Block block : cfg.blocks()) {
            int weight = 1 << depth[b];   // 1, 2, 4, ... by nesting
            for (KofOperation op : block.operations()) {
                if (op instanceof KofLoadLocal ll) {
                    cost.merge(ll.index(), weight, Integer::sum);
                }
            }
            b++;
        }
        return new SpillCost(cost);
    }

    /** Convenience: build from a flat op list. */
    public static SpillCost ofOps(java.util.List<KofOperation> ops) {
        return of(ControlFlowGraph.ofOps(ops));
    }

    /**
     * Loop depth per block: for each back edge {@code b -> t} (t <= b), every
     * block in {@code [t, b]} is one level deeper.
     */
    static int[] loopDepth(ControlFlowGraph cfg) {
        var blocks = cfg.blocks();
        int n = blocks.size();
        int[] depth = new int[n];
        for (int b = 0; b < n; b++) {
            for (int s : blocks.get(b).successors()) {
                if (s <= b) {                       // back edge
                    for (int k = s; k <= b; k++) depth[k]++;
                }
            }
        }
        return depth;
    }
}
