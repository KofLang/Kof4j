package dev.kof.compiler;

import dev.kof.compiler.backend.LivenessDeadStores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C of the assembly-optimization plan: liveness-driven dead-store
 * elimination. A store is dead when its slot is not live after it — the stored
 * value is never read. The pass consumes {@link LivenessAnalysis}; these tests
 * pin the removals and the conservatism (opaque producers, the read-back, live
 * slots).
 */
class LivenessDeadStoresTest {

    private final LivenessDeadStores pass = new LivenessDeadStores();

    private List<KofOperation> run(List<KofOperation> ops) {
        return pass.run(ops, new java.util.IdentityHashMap<>());
    }

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofStoreLocal store(int slot) {
        return new KofStoreLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void aStoreToANeverReadSlotIsRemovedWithItsProducer() {
        // lit(1); store(1); return -> slot 1 is never read -> both removed.
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(1), store(1), new KofReturnVoid()));
        assertEquals(1, out.size(), "the dead store and its producer are gone");
        assertInstanceOf(KofReturnVoid.class, out.get(0));
    }

    @Test
    void aReadSlotKeepsItsStore() {
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(1), store(1), load(1), new KofReturnVoid()));
        assertEquals(4, out.size(), "a read slot keeps the store");
    }

    @Test
    void theStoreLoadReadBackIsKept() {
        // store(1); load(1): the load makes the slot live -> the pair stays.
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(1), store(1), load(1), new KofReturnVoid()));
        assertTrue(out.stream().anyMatch(o -> o instanceof KofStoreLocal));
        assertTrue(out.stream().anyMatch(o -> o instanceof KofLoadLocal));
    }

    @Test
    void anOverwrittenStoreIsRemoved() {
        // lit(1); store(1); lit(2); store(1); load(1); return
        // the first store is overwritten before any read -> removed.
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(1), store(1),
                KofLoadLiteral.ofInt(2), store(1),
                load(1), new KofReturnVoid()));
        assertEquals(4, out.size(), "the first store pair is removed");
    }

    @Test
    void aStoreWithOpaqueProducerIsKeptEvenIfDead() {
        // call; store(1); return -> slot 1 never read, but the producer is opaque
        // (a call) so removing only the store would unbalance the stack -> kept.
        List<KofOperation> out = run(List.of(
                new KofCall(null, "m", List.of(), Type.PrimitiveType.INT, KofCallKind.STATIC),
                store(1), new KofReturnVoid()));
        assertEquals(3, out.size(), "an opaque producer keeps its store");
        assertTrue(out.stream().anyMatch(o -> o instanceof KofCall));
    }

    @Test
    void aSlotLiveAcrossABranchKeepsItsStore() {
        LabelId t = new LabelId(0);
        LabelId f = new LabelId(1);
        // lit; store(1); cjump; label t; load(1); ... : slot 1 is live in an arm.
        List<KofOperation> out = run(List.of(
                KofLoadLiteral.ofInt(1), store(1),
                new KofConditionalJump(KofComparison.EQ, Type.PrimitiveType.INT, t, f),
                new KofLabel(t), load(1), new KofReturnVoid(),
                new KofLabel(f), new KofReturnVoid()));
        assertTrue(out.stream().anyMatch(o -> o instanceof KofStoreLocal),
                "a slot read in a later arm keeps its store");
    }

    @Test
    void aMethodWithATryRemovesNothing() {
        // §709 + §712: the pass is CONSERVATIVE around exception regions — a store
        // that looks dead must survive because a catch/finally may read it through
        // the (under-modelable) exception edge. Nothing is removed from any method
        // that has a try/catch.
        LabelId start = new LabelId(0), end = new LabelId(1), handler = new LabelId(2), after = new LabelId(3);
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(9), store(1),
                new KofTryStart(start, end, handler, "String", 4),
                new KofLabel(start), new KofLabel(end), new KofJump(after),
                new KofCatchStart(handler, "String", 4), load(1), new KofReturnVoid(),
                new KofLabel(after), new KofReturnVoid());
        List<KofOperation> out = run(ops);
        assertEquals(ops.size(), out.size(),
                "a method with a try/catch removes nothing (conservative)");
    }
}
