package dev.kof.compiler.backend;

import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofStoreLocal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Interference graph over local slots (Phase C of
 * {@code docs/development/assembly-optimization-plan.md} §5: "interference
 * graph").
 *
 * <p>Two slots interfere when they cannot share a physical register: one is
 * still live where the other is (re)defined. The graph is built from the
 * {@link LivenessAnalysis}: for every op that defines slot {@code d}, every slot
 * live immediately after that op interferes with {@code d} (except {@code d}
 * itself — redefining a slot may reuse its own register, since the old value
 * dies at the def).
 *
 * <p>The build is conservative: it never omits an edge the allocator would need
 * (omitting one would be a miscompile). Extra edges only make the coloring less
 * optimal, never wrong. Nodes are the slot indices, kept in a deterministic
 * order (§20) so the allocator's choices are reproducible.
 *
 * <p>This is a pure read of the IR — it allocates nothing in the emitted code
 * and changes no behavior.
 */
public final class InterferenceGraph {

    private final TreeMap<Integer, TreeSet<Integer>> adjacency;

    private InterferenceGraph(TreeMap<Integer, TreeSet<Integer>> adjacency) {
        this.adjacency = adjacency;
    }

    /** The slots that appear as a node, ascending. */
    public Set<Integer> nodes() {
        return Set.copyOf(adjacency.keySet());
    }

    /** The neighbours of a slot (the slots it interferes with), ascending. */
    public Set<Integer> neighbors(int slot) {
        TreeSet<Integer> n = adjacency.get(slot);
        return n == null ? Set.of() : Set.copyOf(n);
    }

    /** True when the two slots cannot share a register. */
    public boolean interferes(int a, int b) {
        return a != b && adjacency.getOrDefault(a, new TreeSet<>()).contains(b);
    }

    /** The degree of a slot (number of neighbours). */
    public int degree(int slot) {
        TreeSet<Integer> n = adjacency.get(slot);
        return n == null ? 0 : n.size();
    }

    /** Number of nodes. */
    public int nodeCount() {
        return adjacency.size();
    }

    /** Number of undirected edges. */
    public int edgeCount() {
        int sum = 0;
        for (TreeSet<Integer> n : adjacency.values()) sum += n.size();
        return sum / 2;
    }

    /** Builds the graph from the op stream and its liveness. */
    public static InterferenceGraph of(List<KofOperation> ops, LivenessAnalysis liveness) {
        TreeMap<Integer, TreeSet<Integer>> adj = new TreeMap<>();
        for (KofOperation op : ops) {
            for (int d : defOf(op)) {
                adj.computeIfAbsent(d, k -> new TreeSet<>());   // every def'd slot is a node
                for (int live : liveness.liveAfter(op)) {
                    if (live == d) continue;
                    addEdge(adj, d, live);
                }
            }
        }
        return new InterferenceGraph(adj);
    }

    /** Convenience: builds the liveness internally. */
    public static InterferenceGraph ofOps(List<KofOperation> ops) {
        return of(ops, LivenessAnalysis.ofOps(ops));
    }

    /**
     * Adds the given slots as nodes and the interference their ABI-defined live
     * ranges imply. A PARAM slot is defined by the ABI (it arrives in a register),
     * never by a {@code KofStoreLocal}, so the def-driven build creates no node for
     * it — leaving the register model inert for the common {@code f(a, b) { ... } }
     * shape. A param is live from method entry, so two params live at entry
     * interfere (they must not share a register), and a param interferes with
     * whatever is live at the same block boundaries. This walks the block
     * boundaries ({@code liveIn} ∪ {@code liveOut}) and adds an edge between every
     * pair of co-live slots where at least one is an extra (param) slot; the
     * def-driven edges already cover def-vs-param interference within a block. The
     * result is conservative (extra edges only cost optimality) and deterministic.
     */
    public InterferenceGraph withParams(LivenessAnalysis liveness, int blockCount,
                                        Set<Integer> extra) {
        TreeMap<Integer, TreeSet<Integer>> adj = new TreeMap<>();
        for (var e : adjacency.entrySet()) adj.put(e.getKey(), new TreeSet<>(e.getValue()));
        for (int s : extra) adj.computeIfAbsent(s, k -> new TreeSet<>());
        for (int b = 0; b < blockCount; b++) {
            TreeSet<Integer> live = new TreeSet<>();
            live.addAll(liveness.liveIn(b));
            live.addAll(liveness.liveOut(b));
            for (int s : extra) {
                if (!live.contains(s)) continue;
                for (int t : live) {
                    if (t != s) addEdge(adj, s, t);
                }
            }
        }
        return new InterferenceGraph(adj);
    }

    private static void addEdge(TreeMap<Integer, TreeSet<Integer>> adj, int a, int b) {
        adj.computeIfAbsent(a, k -> new TreeSet<>()).add(b);
        adj.computeIfAbsent(b, k -> new TreeSet<>()).add(a);
    }

    private static Set<Integer> defOf(KofOperation op) {
        if (op instanceof KofStoreLocal sl) return Set.of(sl.index());
        if (op instanceof KofCatchStart cs) return Set.of(cs.localIndex());
        return Set.of();
    }

    /** A copy relation {@code store(y) fed by load(x)}: the allocator may coalesce
     *  {@code x} and {@code y} (they never need separate registers at that point),
     *  which is the classic move-coalescing hint. Pure read of the IR. */
    public static Map<Integer, Integer> coalesceHints(List<KofOperation> ops) {
        Map<Integer, Integer> hint = new TreeMap<>();
        KofOperation prev = null;
        for (KofOperation op : ops) {
            if (op instanceof KofStoreLocal sl && prev instanceof KofLoadLocal ll
                    && ll.type().equals(sl.type()) && ll.index() != sl.index()) {
                hint.putIfAbsent(sl.index(), ll.index());
            }
            prev = op;
        }
        return hint;
    }
}
