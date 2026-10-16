package dev.kof.compiler.backend;

import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofStoreLocal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Spill-materialization plan (Phase C of
 * {@code docs/development/assembly-optimization-plan.md} §5: "spills").
 *
 * <p>Given the register {@link RegisterAllocator.Allocation}, the slots it
 * spilled must be kept in memory: a spilled slot is RELOADED before each of its
 * uses and STORED BACK after each of its definitions. This class computes that
 * plan as pure DATA — the set of ops that need a reload inserted before them and
 * the set of ops that need a store-back inserted after them — WITHOUT modifying
 * the op stream.
 *
 * <p>It is the bridge between the (additive) allocation decision and the
 * (non-additive) codegen wiring: it lets the plan be proven correct here, before
 * any backend emission changes. Nothing consumes it yet, so emitted code is
 * unchanged.
 */
public final class SpillPlanner {

    /** The plan: reload-before and store-back-after points, per spilled slot. */
    public record Plan(Map<Integer, List<KofOperation>> reloadBefore,
                       Map<Integer, List<KofOperation>> storeBackAfter) {

        /** The ops that read {@code slot} and therefore need it reloaded first. */
        public List<KofOperation> reloadPoints(int slot) {
            return reloadBefore.getOrDefault(slot, List.of());
        }

        /** The ops that define {@code slot} and therefore need it stored back. */
        public List<KofOperation> storeBackPoints(int slot) {
            return storeBackAfter.getOrDefault(slot, List.of());
        }

        /** Total inserted ops if the plan were applied (reloads + store-backs). */
        public int insertedOps() {
            int n = 0;
            for (List<KofOperation> l : reloadBefore.values()) n += l.size();
            for (List<KofOperation> l : storeBackAfter.values()) n += l.size();
            return n;
        }
    }

    private SpillPlanner() {
    }

    /**
     * Builds the plan for the slots the allocation spilled. {@code reloadBefore}
     * lists, per spilled slot, the {@link KofLoadLocal} ops that read it (each
     * needs a reload first); {@code storeBackAfter} lists, per spilled slot, the
     * {@link KofStoreLocal} ops that define it (each needs a store-back after).
     */
    public static Plan plan(List<KofOperation> ops, RegisterAllocator.Allocation allocation) {
        Map<Integer, List<KofOperation>> reloadBefore = new TreeMap<>();
        Map<Integer, List<KofOperation>> storeBackAfter = new TreeMap<>();
        for (KofOperation op : ops) {
            if (op instanceof KofLoadLocal ll && allocation.isSpilled(ll.index())) {
                reloadBefore.computeIfAbsent(ll.index(), k -> new ArrayList<>()).add(op);
            } else if (op instanceof KofStoreLocal sl && allocation.isSpilled(sl.index())) {
                storeBackAfter.computeIfAbsent(sl.index(), k -> new ArrayList<>()).add(op);
            }
        }
        return new Plan(copy(reloadBefore), copy(storeBackAfter));
    }

    /**
     * True when the plan is a no-op for every op — i.e. nothing was spilled. A
     * caller can use this to skip the materialization step entirely.
     */
    public static boolean isEmpty(Plan plan) {
        return plan.insertedOps() == 0;
    }

    private static Map<Integer, List<KofOperation>> copy(Map<Integer, List<KofOperation>> m) {
        Map<Integer, List<KofOperation>> out = new TreeMap<>();
        for (var e : m.entrySet()) out.put(e.getKey(), List.copyOf(e.getValue()));
        return Map.copyOf(out);
    }
}
