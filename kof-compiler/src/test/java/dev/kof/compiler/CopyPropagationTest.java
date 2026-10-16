package dev.kof.compiler;

import dev.kof.compiler.backend.CopyPropagation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B of the assembly-optimization plan: copy propagation. A copy
 * {@code load(x); store(y)} makes a later, non-adjacent {@code load(y)} read
 * {@code x}; the rewrite is stack-effect-preserving and conservative at control
 * boundaries. An immediate {@code store(y); load(y)} read-back is left verbatim
 * (the backends pattern-match on it, see {@code Optimizer.deadEffects}).
 */
class CopyPropagationTest {

    private final CopyPropagation pass = new CopyPropagation();

    private List<KofOperation> run(List<KofOperation> ops) {
        return pass.run(ops, new java.util.IdentityHashMap<>());
    }

    private static KofLoadLocal load(int slot, Type t) { return new KofLoadLocal(t, slot); }
    private static KofStoreLocal store(int slot, Type t) { return new KofStoreLocal(t, slot); }
    // An intervening read of an unrelated slot: separates a store from a later
    // read of the same slot so the adjacent read-back guard does not apply.
    private static KofLoadLocal spacer() { return new KofLoadLocal(Type.PrimitiveType.INT, 99); }

    @Test
    void redirectsALaterLoadToTheCopiedSource() {
        // load(0); store(1); [spacer]; load(1)  -> load(1) becomes load(0)
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                spacer(),
                load(1, Type.PrimitiveType.INT)));
        assertEquals(4, out.size());
        assertEquals(load(0, Type.PrimitiveType.INT), out.get(3), "load(1) redirects to load(0)");
    }

    @Test
    void adjacentReadBackIsNotRedirected() {
        // load(0); store(1); load(1) — the immediate store;load read-back is kept
        // verbatim, not redirected.
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.INT)));
        assertEquals(load(1, Type.PrimitiveType.INT), out.get(2),
                "an adjacent store;load read-back stays verbatim");
    }

    @Test
    void storeToTheSourceInvalidatesTheCopy() {
        // load(0); store(1); [redefine slot 0]; [spacer]; load(1) -> NOT redirected
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                KofLoadLiteral.ofInt(5),
                store(0, Type.PrimitiveType.INT),   // slot 0 re-written
                spacer(),
                load(1, Type.PrimitiveType.INT)));
        assertEquals(load(1, Type.PrimitiveType.INT), out.get(out.size() - 1),
                "a copy of a re-written source is stale");
    }

    @Test
    void controlBoundaryClearsTheCopyMap() {
        LabelId L = new LabelId(0);
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                new KofLabel(L),
                load(1, Type.PrimitiveType.INT)));
        assertEquals(load(1, Type.PrimitiveType.INT), out.get(3),
                "a copy crossing a label is not propagated");
    }

    @Test
    void typeMismatchBlocksTheRedirect() {
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                spacer(),
                load(1, Type.PrimitiveType.LONG)));   // type differs from the copy
        assertEquals(load(1, Type.PrimitiveType.LONG), out.get(3));
    }

    @Test
    void chainResolvesToTheRootSource() {
        // load(0); store(1); [spacer]; load(1); store(2); [spacer]; load(2)
        // -> load(2) becomes load(0)
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                spacer(),
                load(1, Type.PrimitiveType.INT),
                store(2, Type.PrimitiveType.INT),
                spacer(),
                load(2, Type.PrimitiveType.INT)));
        assertEquals(load(0, Type.PrimitiveType.INT), out.get(6), "the chain resolves to slot 0");
    }
}
