package dev.kof.compiler;

import dev.kof.compiler.backend.RegisterAllocator;
import dev.kof.compiler.backend.SpillPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C of the assembly-optimization plan: the spill-materialization plan.
 * Given the allocation's spill set, it computes (as pure data) which ops need a
 * reload inserted before them and which need a store-back after them. Nothing
 * applies it yet — these tests pin the plan; compiler behavior is unchanged.
 */
class SpillPlannerTest {

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofStoreLocal store(int slot) {
        return new KofStoreLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void aSpilledSlotsUsesAndDefsBecomeReloadAndStoreBackPoints() {
        // slot 1 is spilled: its two loads need reloads, its store needs a store-back.
        KofLoadLocal l1 = load(1);
        KofLoadLocal l2 = load(1);
        KofStoreLocal s1 = store(1);
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), s1,
                KofLoadLiteral.ofInt(1), store(2),
                l1, l2, new KofReturnVoid());
        // The planner is tested in isolation from the allocator's heuristics:
        // hand it an allocation that spills slot 1.
        RegisterAllocator.Allocation alloc =
                new RegisterAllocator.Allocation(java.util.Map.of(1, -1, 2, 0), java.util.Set.of(1));
        SpillPlanner.Plan plan = SpillPlanner.plan(ops, alloc);
        assertEquals(2, plan.reloadPoints(1).size(), "two loads -> two reload points");
        assertEquals(1, plan.storeBackPoints(1).size(), "one store -> one store-back point");
        assertTrue(plan.reloadPoints(1).contains(l1));
        assertTrue(plan.reloadPoints(1).contains(l2));
        assertTrue(plan.storeBackPoints(1).contains(s1));
        assertEquals(3, plan.insertedOps());
    }

    @Test
    void aNonSpilledSlotHasNoPlan() {
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), store(1),
                KofLoadLiteral.ofInt(1), store(2),
                load(1), load(2), new KofReturnVoid());
        RegisterAllocator.Allocation alloc =
                new RegisterAllocator.Allocation(java.util.Map.of(1, 0, 2, 1), java.util.Set.of());
        SpillPlanner.Plan plan = SpillPlanner.plan(ops, alloc);
        assertTrue(SpillPlanner.isEmpty(plan), "nothing spilled -> empty plan");
        assertEquals(0, plan.insertedOps());
    }

    @Test
    void aNeverUsedSpilledSlotHasNoReloadPoints() {
        // slot 1 is stored but never loaded; it needs no reload, only a store-back.
        KofStoreLocal s1 = store(1);
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), s1,
                KofLoadLiteral.ofInt(1), store(2),
                load(2), new KofReturnVoid());
        RegisterAllocator.Allocation alloc =
                new RegisterAllocator.Allocation(java.util.Map.of(1, -1, 2, 0), java.util.Set.of(1));
        SpillPlanner.Plan plan = SpillPlanner.plan(ops, alloc);
        assertEquals(0, plan.reloadPoints(1).size(), "no uses -> no reloads");
        assertEquals(1, plan.storeBackPoints(1).size(), "its def still stores back");
    }

    @Test
    void planIsDeterministic() {
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), store(1),
                KofLoadLiteral.ofInt(1), store(2),
                load(1), load(2), new KofReturnVoid());
        RegisterAllocator.Allocation alloc =
                new RegisterAllocator.Allocation(java.util.Map.of(1, -1, 2, 0), java.util.Set.of(1));
        SpillPlanner.Plan a = SpillPlanner.plan(ops, alloc);
        SpillPlanner.Plan b = SpillPlanner.plan(ops, alloc);
        assertEquals(a.insertedOps(), b.insertedOps());
        assertEquals(a.reloadBefore(), b.reloadBefore());
        assertEquals(a.storeBackAfter(), b.storeBackAfter());
    }
}
