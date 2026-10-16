package dev.kof.compiler;

import dev.kof.compiler.backend.Optimizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §20 of the assembly-optimization plan: determinism. "Given the same source,
 * target, flags and compiler version, the result must be reproducible." The
 * production {@link Optimizer} must therefore be deterministic (the same op
 * stream always yields the same result) and idempotent (optimizing an already
 * optimized stream changes nothing — a fixed point).
 */
class OptimizerDeterminismTest {

    private static IRModule module(IRMethod method) {
        IRClass cls = new IRClass("Main", "java/lang/Object", List.<String>of(), 0,
                List.of(), List.of(method), List.<String>of(), null, 0, List.of(), false);
        return new IRModule("Main", List.of(cls), List.<String>of(), null, null);
    }

    private static List<KofOperation> optimize(List<KofOperation> ops) {
        IRMethod m = new IRMethod("m", Type.PrimitiveType.INT, List.<Type>of(), 0,
                List.<String>of(), List.of(new IRBasicBlock(0, ops)), List.of(),
                KofDebugInfo.EMPTY, List.of(), List.of());
        IRModule out = Optimizer.optimize(module(m));
        return out.classes().get(0).methods().get(0).basicBlocks().get(0).operations();
    }

    private static List<KofOperation> sample() {
        return List.of(
                KofLoadLiteral.ofInt(1),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(2),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(4),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT));
    }

    @Test
    void optimizationIsDeterministic() {
        // §20: the same input yields the same output across runs.
        List<KofOperation> a = optimize(sample());
        List<KofOperation> b = optimize(sample());
        assertEquals(a.size(), b.size(), "the same input yields the same size");
        assertEquals(a, b, "the same input yields an identical result");
    }

    @Test
    void optimizationIsIdempotent() {
        // Optimizing an already-optimized stream is a fixed point.
        List<KofOperation> once = optimize(sample());
        List<KofOperation> twice = optimize(once);
        assertEquals(once, twice, "the optimizer reaches a fixed point");
    }

    @Test
    void aFoldedMultiplyBecomesAShift() {
        // x * 4 reaches the §12 strength reduction through the production pipeline.
        List<KofOperation> out = optimize(List.of(
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(4),
                new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT)));
        boolean hasShift = out.stream().anyMatch(o -> o instanceof KofBinary b
                && b.op() == KofBinaryOp.SHL);
        assertTrue(hasShift, "x * 4 is strength-reduced to a shift in production");
    }
}
