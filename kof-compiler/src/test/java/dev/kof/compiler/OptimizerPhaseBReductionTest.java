package dev.kof.compiler;

import dev.kof.compiler.backend.Optimizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B close criterion of {@code docs/development/assembly-optimization-plan.md}
 * §21: "measurable reduction in instructions on representative fixtures". These
 * fixtures go through the PRODUCTION {@link Optimizer#optimize}, so they pin the
 * wired pass pipeline (constant propagation + copy propagation + dead-store
 * elimination), not the standalone passes.
 *
 * <p>The measured baseline (the pre-Phase-B optimizer) leaves all of these
 * unchanged; the new passes remove the dead stores, which is the Phase B
 * reduction.
 */
class OptimizerPhaseBReductionTest {

    private static IRModule module(IRMethod method) {
        IRClass cls = new IRClass("Main", "java/lang/Object", List.of(), 0,
                List.of(), List.of(method), List.of(), null, 0, List.of(), false);
        return new IRModule("Main", List.of(cls), List.of());
    }

    private static IRMethod method(String name, List<KofOperation> ops) {
        return new IRMethod(name, Type.PrimitiveType.INT, List.of(), 0, List.of(),
                List.of(new IRBasicBlock(0, ops)), List.of());
    }

    private static List<KofOperation> optimizeOps(List<KofOperation> ops) {
        IRModule out = Optimizer.optimize(module(method("m", ops)));
        return out.classes().get(0).methods().get(0).basicBlocks().get(0).operations();
    }

    @Test
    void deadStoreIsEliminated() {
        // x = 1; x = 2; return x — the first store is never observed.
        List<KofOperation> in = List.of(
                KofLoadLiteral.ofInt(1),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(2),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                new KofReturn(Type.PrimitiveType.INT));
        List<KofOperation> out = optimizeOps(in);
        assertEquals(4, out.size(),
                "a dead store (producer + store) is removed (in=" + in.size()
                        + " out=" + out.size() + ")");
    }

    @Test
    void twoDeadStoresAreEliminated() {
        // x = 1; y = 2; x = 3; y = 4; return x + y — both first stores are dead.
        List<KofOperation> in = List.of(
                KofLoadLiteral.ofInt(1),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(2),
                new KofStoreLocal(Type.PrimitiveType.INT, 2),
                KofLoadLiteral.ofInt(3),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(4),
                new KofStoreLocal(Type.PrimitiveType.INT, 2),
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 2),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT));
        List<KofOperation> out = optimizeOps(in);
        // Through the full pipeline (Phase B + Phase C liveness dead-store pass),
        // both dead stores are removed, the reads fold to literals and the ADD
        // folds, leaving the returned literal.
        assertEquals(2, out.size(),
                "the whole expression folds to a literal return (in=" + in.size()
                        + " out=" + out.size() + ")");
    }

    @Test
    void propagatedLiteralIsNotRemovedWithADeadProducer() {
        // x = 7; x = 9; return x + x — the first store is dead, but the reads of x
        // after the second store must survive. If a propagated literal shared an
        // IDENTITY with the dead store's producer, dead-store elimination (which
        // removes by identity) would delete the surviving reads too — the exact
        // Raster.kf regression (missing call argument -> ASM frame crash).
        List<KofOperation> in = List.of(
                KofLoadLiteral.ofInt(7),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(9),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT));
        List<KofOperation> out = optimizeOps(in);
        // Both operands of the ADD must still be pushed: the dead store is gone
        // but the two surviving reads are intact (they fold to literals).
        int addIndex = -1;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) instanceof KofBinary) addIndex = i;
        }
        assertTrue(addIndex >= 2, "the ADD survived");
        assertTrue(isPush(out.get(addIndex - 1)) && isPush(out.get(addIndex - 2)),
                "both ADD operands are still pushed after the dead-store removal");
    }

    private static boolean isPush(KofOperation op) {
        return op instanceof KofLoadLiteral || op instanceof KofLoadLocal;
    }

    @Test
    void livenessRemovesANeverReadStoreThatPhaseBAloneKeeps() {
        // x = 1; y = 2; return x — slot 1 is never read (not even overwritten), so
        // the Phase B overwrite-only rule cannot remove it; the Phase C liveness
        // pass can (slot 1 is not live after its store).
        List<KofOperation> in = List.of(
                KofLoadLiteral.ofInt(1),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(2),
                new KofStoreLocal(Type.PrimitiveType.INT, 2),
                new KofLoadLocal(Type.PrimitiveType.INT, 2),
                new KofReturn(Type.PrimitiveType.INT));
        List<KofOperation> out = optimizeOps(in);
        assertTrue(out.size() < in.size(),
                "the never-read store is removed by the liveness pass (in=" + in.size()
                        + " out=" + out.size() + ")");
    }
}
