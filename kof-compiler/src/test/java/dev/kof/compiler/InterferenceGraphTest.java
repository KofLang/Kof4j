package dev.kof.compiler;

import dev.kof.compiler.backend.InterferenceGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C of the assembly-optimization plan: the interference graph built over
 * the liveness analysis. It is a pure read of the IR — these tests pin the
 * edges (two slots that are live at the same time interfere; a slot and its own
 * redefinition do not) and the coalescing hint; compiler behavior is unchanged.
 */
class InterferenceGraphTest {

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofStoreLocal store(int slot) {
        return new KofStoreLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void slotsLiveAtTheSameTimeInterfere() {
        // 0: lit; 1: store(1); 2: lit; 3: store(2); 4: load(1); 5: load(2); 6: return
        // slot 1 is live when slot 2 is defined -> edge 1-2.
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(7), store(1),
                KofLoadLiteral.ofInt(8), store(2),
                load(1), load(2), new KofReturnVoid()));
        assertTrue(g.interferes(1, 2), "1 is live across the def of 2");
        assertTrue(g.interferes(2, 1), "interference is symmetric");
        assertEquals(1, g.edgeCount());
    }

    @Test
    void slotsWhoseLifetimesDoNotOverlapDoNotInterfere() {
        // 0: lit; 1: store(1); 2: load(1); 3: lit; 4: store(2); 5: load(2); 6: return
        // slot 1 dies before slot 2 is defined -> no edge.
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(1), store(1), load(1),
                KofLoadLiteral.ofInt(2), store(2), load(2),
                new KofReturnVoid()));
        assertFalse(g.interferes(1, 2), "disjoint lifetimes do not interfere");
        assertEquals(0, g.edgeCount());
    }

    @Test
    void aSlotDoesNotInterfereWithItsOwnRedefinition() {
        // 0: lit; 1: store(1); 2: lit; 3: store(1); 4: load(1); 5: return
        // the redefinition of slot 1 may reuse the same register -> no self edge.
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(1), store(1),
                KofLoadLiteral.ofInt(2), store(1),
                load(1), new KofReturnVoid()));
        assertFalse(g.interferes(1, 1), "a slot never interferes with itself");
        assertEquals(0, g.edgeCount());
    }

    @Test
    void loopCarriedSlotsInterfereAcrossTheBackEdge() {
        // A counter (slot 1) and an accumulator (slot 2) both live across the loop.
        LabelId head = new LabelId(0);
        LabelId exit = new LabelId(1);
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(0), store(1),
                KofLoadLiteral.ofInt(0), store(2),
                new KofLabel(head),
                load(1), load(2),
                new KofConditionalJump(KofComparison.LT, Type.PrimitiveType.INT, head, exit),
                // body: 2 += 1; 1 += 1
                load(2), KofLoadLiteral.ofInt(1),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT), store(2),
                load(1), KofLoadLiteral.ofInt(1),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT), store(1),
                new KofJump(head),
                new KofLabel(exit), new KofReturnVoid()));
        assertTrue(g.interferes(1, 2),
                "both slots are live across the loop body and back edge");
    }

    @Test
    void degreeCountsAllNeighbours() {
        // slot 1 stays live while slots 2 and 3 are each defined.
        InterferenceGraph g = InterferenceGraph.ofOps(List.of(
                KofLoadLiteral.ofInt(0), store(1),
                KofLoadLiteral.ofInt(1), store(2),
                KofLoadLiteral.ofInt(2), store(3),
                load(1), load(2), load(3), new KofReturnVoid()));
        assertTrue(g.degree(1) >= 2, "slot 1 interferes with 2 and 3");
        assertTrue(g.nodes().contains(1));
    }

    @Test
    void copyRelationYieldsACoalescingHint() {
        // load(1); store(2) — a copy: the allocator may coalesce 1 and 2.
        Map<Integer, Integer> hints = InterferenceGraph.coalesceHints(List.of(
                KofLoadLiteral.ofInt(5), store(1),
                load(1), store(2),
                load(2), new KofReturnVoid()));
        assertEquals(1, hints.get(2), "store(2) fed by load(1) hints 2 -> 1");
    }

    @Test
    void graphIsDeterministic() {
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(7), store(1),
                KofLoadLiteral.ofInt(8), store(2),
                load(1), load(2), new KofReturnVoid());
        InterferenceGraph a = InterferenceGraph.ofOps(ops);
        InterferenceGraph b = InterferenceGraph.ofOps(ops);
        assertEquals(a.nodes(), b.nodes());
        assertEquals(a.neighbors(1), b.neighbors(1));
        assertEquals(a.edgeCount(), b.edgeCount());
    }
}
