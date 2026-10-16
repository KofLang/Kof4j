package dev.kof.compiler;

import dev.kof.compiler.backend.InterferenceGraph;
import dev.kof.compiler.backend.RegisterAllocator;
import dev.kof.compiler.backend.SpillCost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C of the assembly-optimization plan: spill cost (loop-nesting-weighted
 * use counts) and the cost-driven spill selection in the allocator. A pure
 * computation over the IR — these tests pin the weights and the victim choice;
 * compiler behavior is unchanged.
 */
class SpillCostTest {

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofStoreLocal store(int slot) {
        return new KofStoreLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void aStraightLineSlotCostsOnePerUse() {
        // 0: lit; 1: store(1); 2: load(1); 3: load(1); 4: return
        SpillCost c = SpillCost.ofOps(List.of(
                KofLoadLiteral.ofInt(7), store(1),
                load(1), load(1), new KofReturnVoid()));
        assertEquals(2, c.costOf(1), "two uses outside any loop cost 2");
    }

    @Test
    void aSlotWithNoUsesCostsZero() {
        SpillCost c = SpillCost.ofOps(List.of(
                KofLoadLiteral.ofInt(7), store(1), new KofReturnVoid()));
        assertEquals(0, c.costOf(1), "a stored-but-never-loaded slot is free to spill");
    }

    @Test
    void aUseInsideALoopCostsMore() {
        // Loop head at block 0; the body reads slot 1 -> weight 2 (one loop deep).
        LabelId head = new LabelId(0);
        LabelId exit = new LabelId(1);
        SpillCost c = SpillCost.ofOps(List.of(
                KofLoadLiteral.ofInt(0), store(1),
                new KofLabel(head),
                load(1),
                new KofConditionalJump(KofComparison.LT, Type.PrimitiveType.INT, head, exit),
                // body: slot 1 += 1
                load(1), KofLoadLiteral.ofInt(1),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT), store(1),
                new KofJump(head),
                new KofLabel(exit), new KofReturnVoid()));
        // the two body uses of slot 1 are inside the loop -> 2 + 2 = 4 (>= 4).
        assertTrue(c.costOf(1) >= 4,
                "a loop-carried slot costs at least 2 per use (got " + c.costOf(1) + ")");
    }

    @Test
    void costDrivenAllocationSpillsTheCheaperNode() {
        // Two interfering slots, one register. Slot 1 is used many times, slot 2
        // once: the allocator should spill slot 2, not slot 1.
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), store(1),
                KofLoadLiteral.ofInt(0), store(2),
                load(1), load(2), load(1), load(1), new KofReturnVoid());
        InterferenceGraph g = InterferenceGraph.ofOps(ops);
        SpillCost cost = SpillCost.ofOps(ops);
        RegisterAllocator.Allocation a = RegisterAllocator.allocate(g, 1, cost);
        assertTrue(a.isSpilled(2), "the cheaper slot (2) spills");
        assertFalse(a.isSpilled(1), "the costlier slot (1) keeps its register");
    }

    @Test
    void costDrivenAllocationStillNeverSharesAColorBetweenInterferingSlots() {
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), store(1),
                KofLoadLiteral.ofInt(1), store(2),
                KofLoadLiteral.ofInt(2), store(3),
                load(1), load(2), load(3), new KofReturnVoid());
        InterferenceGraph g = InterferenceGraph.ofOps(ops);
        RegisterAllocator.Allocation a = RegisterAllocator.allocate(g, 2, SpillCost.ofOps(ops));
        for (int x : g.nodes()) {
            for (int y : g.neighbors(x)) {
                assertNotEquals(a.color(x), a.color(y),
                        "interfering slots " + x + "/" + y + " must not share a colour");
            }
        }
    }
}
