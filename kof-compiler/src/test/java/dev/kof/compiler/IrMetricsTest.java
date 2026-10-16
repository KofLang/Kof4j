package dev.kof.compiler;

import dev.kof.compiler.backend.IrMetrics;
import dev.kof.compiler.backend.RegisterAllocator;
import dev.kof.compiler.backend.SpillCost;
import dev.kof.compiler.backend.InterferenceGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §18 of the assembly-optimization plan: the instruction metrics. A pure counter
 * over the IR — these tests pin the counts; compiler behavior is unchanged. The
 * counters let an optimization's effect be measured rather than asserted.
 */
class IrMetricsTest {

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofStoreLocal store(int slot) {
        return new KofStoreLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void countsLoadsStoresBranchesCallsReturns() {
        LabelId t = new LabelId(0);
        LabelId f = new LabelId(1);
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), store(1),
                load(1),
                new KofConditionalJump(KofComparison.EQ, Type.PrimitiveType.INT, t, f),
                new KofLabel(t),
                new KofCall(null, "m", List.of(), Type.PrimitiveType.VOID, KofCallKind.STATIC),
                new KofJump(f),
                new KofLabel(f),
                new KofReturnVoid());
        IrMetrics m = IrMetrics.of(ops);
        assertEquals(ops.size(), m.instructions());
        assertEquals(1, m.loads());
        assertEquals(1, m.stores());
        assertEquals(2, m.branches(), "one conditional jump + one unconditional jump");
        assertEquals(1, m.calls());
        assertEquals(1, m.returns());
        assertEquals(0, m.spillCount());
        assertEquals(0, m.registersUsed());
    }

    @Test
    void allocationAddsSpillAndRegisterCounts() {
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), store(1),
                KofLoadLiteral.ofInt(1), store(2),
                load(1), load(2), new KofReturnVoid());
        RegisterAllocator.Allocation alloc =
                RegisterAllocator.allocate(InterferenceGraph.ofOps(ops), 1, SpillCost.ofOps(ops));
        IrMetrics m = IrMetrics.of(ops, alloc);
        assertEquals(alloc.spills().size(), m.spillCount());
        assertEquals(alloc.colorsUsed(), m.registersUsed());
        assertTrue(m.spillCount() >= 1, "one register for two interfering slots forces a spill");
    }

    @Test
    void aReductionIsMeasurable() {
        // §18: the counter makes the Phase B dead-store reduction observable.
        List<KofOperation> before = List.of(
                KofLoadLiteral.ofInt(1), store(1),
                KofLoadLiteral.ofInt(2), store(1),
                load(1), new KofReturnVoid());
        List<KofOperation> after = List.of(
                KofLoadLiteral.ofInt(2), store(1),
                load(1), new KofReturnVoid());
        assertTrue(IrMetrics.of(after).instructions() < IrMetrics.of(before).instructions(),
                "the counter detects the instruction reduction");
        assertTrue(IrMetrics.of(after).stores() < IrMetrics.of(before).stores(),
                "the counter detects the store reduction");
    }

    @Test
    void emptyStreamIsAllZero() {
        IrMetrics m = IrMetrics.of(List.of());
        assertEquals(0, m.instructions());
        assertEquals(0, m.loads());
        assertEquals(0, m.stores());
    }
}
