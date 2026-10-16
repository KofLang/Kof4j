package dev.kof.compiler;

import dev.kof.compiler.backend.OptimizerMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase F/§17 of the assembly-optimization plan: the before/after comparison
 * harness. It measures an optimization's effect through the production
 * {@link dev.kof.compiler.backend.Optimizer} — the "before/after comparison"
 * §17 requires, measured not asserted.
 */
class OptimizerMetricsTest {

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofStoreLocal store(int slot) {
        return new KofStoreLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void deadStoreReductionIsMeasured() {
        // x = 1; x = 2; return x — the first store is dead.
        OptimizerMetrics.Comparison c = OptimizerMetrics.compare(List.of(
                KofLoadLiteral.ofInt(1), store(1),
                KofLoadLiteral.ofInt(2), store(1),
                load(1), new KofReturn(Type.PrimitiveType.INT)));
        assertTrue(c.reduced(), "the pipeline reduces the instruction count");
        assertTrue(c.storeDelta() > 0, "a store is removed");
        assertTrue(c.instructionDelta() > 0, "instructions are removed");
    }

    @Test
    void anAlreadyMinimalStreamHasNoDelta() {
        // Nothing to optimize: a single return.
        OptimizerMetrics.Comparison c = OptimizerMetrics.compare(List.of(
                new KofReturnVoid()));
        assertEquals(0, c.instructionDelta());
        assertFalse(c.reduced());
    }

    @Test
    void summaryReportsBothSides() {
        OptimizerMetrics.Comparison c = OptimizerMetrics.compare(List.of(
                KofLoadLiteral.ofInt(1), store(1),
                KofLoadLiteral.ofInt(2), store(1),
                load(1), new KofReturn(Type.PrimitiveType.INT)));
        String s = c.summary();
        assertTrue(s.contains("before["), "the summary shows the before metrics");
        assertTrue(s.contains("after["), "the summary shows the after metrics");
        assertTrue(s.contains("delta("), "the summary shows the delta");
    }
}
