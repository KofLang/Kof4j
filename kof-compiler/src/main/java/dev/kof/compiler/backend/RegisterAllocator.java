package dev.kof.compiler.backend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Register allocation over the {@link InterferenceGraph} (Phase C of
 * {@code docs/assembly-optimization-plan.md} §5: "allocation").
 *
 * <p>The initial strategy is deliberately simple and deterministic, as §5
 * allows: greedy graph coloring in descending-degree order (ties broken by
 * ascending slot index, §20). Each node takes the lowest colour not used by any
 * of its neighbours. A node with no free colour within the register budget is
 * SPILLED (colour {@code -1}) — the allocator never assigns two interfering
 * slots the same register, so the result is always correct, at worst
 * sub-optimal.
 *
 * <p>Colors are abstract register numbers ({@code 0..registerCount-1}); mapping
 * them to a real ISA's registers is Phase D/E work (target lowering), and the
 * spill mechanism (reloading a spilled slot around its uses) is the follow-on
 * slice. This class only computes the assignment; nothing consumes it yet, so
 * emitted code is unchanged (the same additive discipline as Phases A and B).
 */
public final class RegisterAllocator {

    /** The allocation result: a colour per slot ({@code -1} = spilled) plus the
     *  set of spilled slots. */
    public record Allocation(Map<Integer, Integer> colorOf, Set<Integer> spills) {

        /** The colour assigned to a slot, or {@code -1} if spilled/unseen. */
        public int color(int slot) {
            return colorOf.getOrDefault(slot, -1);
        }

        /** True when the slot could not be coloured and must spill to memory. */
        public boolean isSpilled(int slot) {
            return spills.contains(slot);
        }

        /** The number of distinct colours actually used. */
        public int colorsUsed() {
            Set<Integer> used = new HashSet<>();
            for (int c : colorOf.values()) {
                if (c >= 0) used.add(c);
            }
            return used.size();
        }
    }

    private RegisterAllocator() {
    }

    /** Allocates {@code registerCount} colours over the graph. */
    public static Allocation allocate(InterferenceGraph graph, int registerCount) {
        return allocate(graph, registerCount, null);
    }

    /**
     * Allocates {@code registerCount} colours over the graph, using {@code
     * spillCost} (when non-null) to choose WHICH node spills. When a node has no
     * free colour, the cheapest-to-spill node among it and its already-coloured
     * neighbours is spilled to make room; a lower-cost neighbour is preferred
     * over spilling the current node.
     */
    public static Allocation allocate(InterferenceGraph graph, int registerCount,
                                      SpillCost spillCost) {
        if (registerCount < 1) {
            throw new IllegalArgumentException("registerCount must be >= 1");
        }
        List<Integer> nodes = new ArrayList<>(graph.nodes());
        nodes.sort(Comparator.<Integer>comparingInt(graph::degree).reversed()
                .thenComparingInt(Integer::intValue));

        Map<Integer, Integer> colorOf = new TreeMap<>();
        Set<Integer> spills = new java.util.TreeSet<>();
        for (int slot : nodes) {
            Set<Integer> used = usedColors(graph, colorOf, slot);
            int chosen = lowestFree(used, registerCount);
            if (chosen >= 0) {
                colorOf.put(slot, chosen);
                continue;
            }
            // No free colour: spill the cheapest node among {slot} and its
            // coloured neighbours. Without a cost model, spill the current node.
            int victim = slot;
            if (spillCost != null) {
                int best = spillCost.costOf(slot);
                for (int n : graph.neighbors(slot)) {
                    Integer c = colorOf.get(n);
                    if (c == null || c < 0) continue;
                    int cost = spillCost.costOf(n);
                    if (cost < best || (cost == best && n > victim && victim == slot)) {
                        best = cost;
                        victim = n;
                    }
                }
            }
            if (victim == slot) {
                colorOf.put(slot, -1);
                spills.add(slot);
            } else {
                colorOf.put(victim, -1);
                spills.add(victim);
                // Recompute: freeing the victim may have made a colour available.
                int freed = lowestFree(usedColors(graph, colorOf, slot), registerCount);
                colorOf.put(slot, freed);
                if (freed < 0) spills.add(slot);
            }
        }
        return new Allocation(Map.copyOf(colorOf), Set.copyOf(spills));
    }

    private static Set<Integer> usedColors(InterferenceGraph graph,
                                           Map<Integer, Integer> colorOf, int slot) {
        Set<Integer> used = new HashSet<>();
        for (int n : graph.neighbors(slot)) {
            Integer c = colorOf.get(n);
            if (c != null && c >= 0) used.add(c);
        }
        return used;
    }

    private static int lowestFree(Set<Integer> used, int registerCount) {
        for (int c = 0; c < registerCount; c++) {
            if (!used.contains(c)) return c;
        }
        return -1;
    }

    /**
     * Call-aware allocation: a slot in {@code callCrossing} is LIVE ACROSS A CALL,
     * so it may only take a colour in {@code [0, calleeSavedCount)} — the
     * callee-saved colours, which survive a call (the codegen maps colour → the
     * register at that index of the class's callee-saved-first allocatable list,
     * see {@code X86_64RegisterFile}). A call-crossing slot with no free
     * callee-saved colour is SPILLED (the caller reloads it after the call).
     *
     * <p>Without this, the allocator could place a value live across a call in a
     * caller-saved register and the call would clobber it — a miscompile.
     */
    public static Allocation allocate(InterferenceGraph graph, int registerCount,
                                      SpillCost spillCost, Set<Integer> callCrossing,
                                      int calleeSavedCount) {
        if (registerCount < 1) {
            throw new IllegalArgumentException("registerCount must be >= 1");
        }
        List<Integer> nodes = new ArrayList<>(graph.nodes());
        nodes.sort(Comparator.<Integer>comparingInt(graph::degree).reversed()
                .thenComparingInt(Integer::intValue));

        Map<Integer, Integer> colorOf = new TreeMap<>();
        Set<Integer> spills = new java.util.TreeSet<>();
        for (int slot : nodes) {
            boolean cross = callCrossing.contains(slot);
            int budget = cross ? Math.max(1, Math.min(calleeSavedCount, registerCount)) : registerCount;
            Set<Integer> used = usedColors(graph, colorOf, slot);
            int chosen = lowestFree(used, budget);
            if (chosen >= 0) {
                colorOf.put(slot, chosen);
                continue;
            }
            // No free colour: spill the cheapest node among {slot} and its
            // coloured neighbours. Without a cost model, spill the current node.
            int victim = slot;
            if (spillCost != null) {
                int best = spillCost.costOf(slot);
                for (int n : graph.neighbors(slot)) {
                    Integer c = colorOf.get(n);
                    if (c == null || c < 0) continue;
                    // A call-crossing victim can only be freed into the
                    // callee-saved budget; do not evict one for a non-crossing slot.
                    if (callCrossing.contains(n) && !cross) continue;
                    int cost = spillCost.costOf(n);
                    if (cost < best || (cost == best && n > victim && victim == slot)) {
                        best = cost;
                        victim = n;
                    }
                }
            }
            if (victim == slot) {
                colorOf.put(slot, -1);
                spills.add(slot);
            } else {
                colorOf.put(victim, -1);
                spills.add(victim);
                int freed = lowestFree(usedColors(graph, colorOf, slot), budget);
                colorOf.put(slot, freed);
                if (freed < 0) spills.add(slot);
            }
        }
        return new Allocation(Map.copyOf(colorOf), Set.copyOf(spills));
    }
}
