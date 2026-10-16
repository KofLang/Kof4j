package dev.kof.compiler;

import dev.kof.compiler.backend.ConstantPropagation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B of the assembly-optimization plan: constant propagation. The pass is
 * a stack-effect-preserving rewrite (local load → literal) and is conservative
 * at control boundaries. These tests pin both the propagation and the barriers.
 */
class ConstantPropagationTest {

    private final ConstantPropagation pass = new ConstantPropagation();

    private List<KofOperation> run(List<KofOperation> ops) {
        return pass.run(ops, new java.util.IdentityHashMap<>());
    }

    @Test
    void propagatesAStoredConstantIntoALaterLoad() {
        // literal 7; store(0); [an intervening read of another slot]; load(0)
        // -> the later, non-adjacent load becomes the literal 7.
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(7),
                new KofStoreLocal(Type.PrimitiveType.INT, 0),
                new KofLoadLocal(Type.PrimitiveType.INT, 9),
                new KofLoadLocal(Type.PrimitiveType.INT, 0)));
        assertEquals(4, out.size());
        assertEquals(KofLoadLiteral.ofInt(7), out.get(3), "load(0) propagates to literal 7");
    }

    @Test
    void adjacentReadBackIsKeptVerbatim() {
        // literal 7; store(0); load(0) — the immediate store/load read-back is a
        // compiler-internal encoding the backends pattern-match on; it is NOT folded.
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(7),
                new KofStoreLocal(Type.PrimitiveType.INT, 0),
                new KofLoadLocal(Type.PrimitiveType.INT, 0)));
        assertInstanceOf(KofLoadLocal.class, out.get(2),
                "an adjacent store;load read-back stays verbatim");
    }

    @Test
    void aNonLiteralStoreInvalidatesTheSlot() {
        // literal 7; store(0); [a binary overwrites the stack value]; store(0); load(0)
        // the second store's producer is NOT a literal -> load(0) is NOT propagated.
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(7),
                new KofStoreLocal(Type.PrimitiveType.INT, 0),
                KofLoadLiteral.ofInt(1), KofLoadLiteral.ofInt(2),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                new KofStoreLocal(Type.PrimitiveType.INT, 0),
                new KofLoadLocal(Type.PrimitiveType.INT, 0)));
        assertInstanceOf(KofLoadLocal.class, out.get(out.size() - 1),
                "an unknown producer invalidates the slot");
    }

    @Test
    void controlBoundaryClearsTheConstantMap() {
        // literal 7; store(0); label L; load(0)  -> the join clears -> NOT propagated.
        LabelId L = new LabelId(0);
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(7),
                new KofStoreLocal(Type.PrimitiveType.INT, 0),
                new KofLabel(L),
                new KofLoadLocal(Type.PrimitiveType.INT, 0)));
        assertInstanceOf(KofLoadLocal.class, out.get(3),
                "a value crossing a label is never assumed constant");
    }

    @Test
    void storeToAnotherSlotDoesNotInvalidate() {
        // literal 7; store(0); literal 9; store(1); load(0) -> still propagates 7.
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(7),
                new KofStoreLocal(Type.PrimitiveType.INT, 0),
                KofLoadLiteral.ofInt(9),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 0)));
        assertEquals(KofLoadLiteral.ofInt(7), out.get(4));
    }

    @Test
    void typeMismatchIsNotPropagated() {
        // literal 7 (Int); store(0) as Long; [intervening read]; load(0) as Long
        // -> the recorded const is Int, the Long load must NOT become the Int literal.
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(7),
                new KofStoreLocal(Type.PrimitiveType.LONG, 0),
                new KofLoadLocal(Type.PrimitiveType.LONG, 9),
                new KofLoadLocal(Type.PrimitiveType.LONG, 0)));
        assertInstanceOf(KofLoadLocal.class, out.get(3), "a type mismatch blocks propagation");
    }
}
