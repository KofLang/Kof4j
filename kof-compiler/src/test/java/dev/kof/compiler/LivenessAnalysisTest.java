package dev.kof.compiler;

import dev.kof.compiler.backend.LivenessAnalysis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C of the assembly-optimization plan: liveness analysis over the CFG.
 * The analysis is a pure read of the IR — these tests pin the dataflow (a
 * slot's value lives from its store to its last load, across joins and loops);
 * compiler behavior is unchanged.
 */
class LivenessAnalysisTest {

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofStoreLocal store(int slot) {
        return new KofStoreLocal(Type.PrimitiveType.INT, slot);
    }

    @Test
    void aStoreThenLoadKeepsTheSlotLiveUntilTheLoad() {
        // 0: lit; 1: store(1); 2: load(1); 3: return
        KofStoreLocal st = store(1);
        KofLoadLocal ld = load(1);
        LivenessAnalysis la = LivenessAnalysis.ofOps(List.of(
                KofLoadLiteral.ofInt(7), st, ld, new KofReturnVoid()));
        assertTrue(la.isLiveAfter(st, 1), "the slot is live from its store to its load");
        assertFalse(la.isLiveAfter(ld, 1), "after the last load the slot is dead");
    }

    @Test
    void aSlotOverwrittenBeforeReadIsNotLiveAcross() {
        // 0: lit; 1: store(1); 2: lit; 3: store(1); 4: load(1); 5: return
        KofStoreLocal first = store(1);
        LivenessAnalysis la = LivenessAnalysis.ofOps(List.of(
                KofLoadLiteral.ofInt(1), first,
                KofLoadLiteral.ofInt(2), store(1),
                load(1), new KofReturnVoid()));
        assertFalse(la.isLiveAfter(first, 1),
                "an overwritten slot is not live across the second store");
    }

    @Test
    void aSlotUsedInOneBranchIsLiveOutOfTheJoin() {
        // if-style: the slot is stored before the branch and read in one arm.
        LabelId t = new LabelId(0);
        LabelId f = new LabelId(1);
        KofStoreLocal st = store(2);
        // 0: lit; 1: store(2); 2: cjump(t,f); 3: label t; 4: load(2); 5: jump end;
        // 6: label f; 7: label end; 8: return
        LabelId end = new LabelId(2);
        LivenessAnalysis la = LivenessAnalysis.ofOps(List.of(
                KofLoadLiteral.ofInt(5), st,
                new KofConditionalJump(KofComparison.EQ, Type.PrimitiveType.INT, t, f),
                new KofLabel(t), load(2), new KofJump(end),
                new KofLabel(f), new KofLabel(end), new KofReturnVoid()));
        assertTrue(la.isLiveAfter(st, 2),
                "a slot read in a later arm is live across the branch");
    }

    @Test
    void aLoopCarriedSlotIsLiveAtTheBackEdge() {
        // A counter slot read at the loop head is live out of the body block
        // (it flows back through the back edge).
        LabelId head = new LabelId(0);
        // 0: lit; 1: store(1); 2: label head; 3: load(1); 4: cjump(head, exit);
        // 5: load(1); 6: lit; 7: add; 8: store(1); 9: jump head;
        // 10: label exit; 11: return
        LabelId exit = new LabelId(1);
        KofStoreLocal bodyStore = store(1);
        LivenessAnalysis la = LivenessAnalysis.ofOps(List.of(
                KofLoadLiteral.ofInt(0), store(1),
                new KofLabel(head), load(1),
                new KofConditionalJump(KofComparison.EQ, Type.PrimitiveType.INT, head, exit),
                load(1), KofLoadLiteral.ofInt(1),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT), bodyStore,
                new KofJump(head),
                new KofLabel(exit), new KofReturnVoid()));
        assertTrue(la.isLiveAfter(bodyStore, 1),
                "a loop-carried slot is live at the back edge");
    }

    @Test
    void aSlotNeverLoadedIsNeverLive() {
        // 0: lit; 1: store(1); 2: return
        KofStoreLocal st = store(1);
        LivenessAnalysis la = LivenessAnalysis.ofOps(List.of(
                KofLoadLiteral.ofInt(3), st, new KofReturnVoid()));
        assertFalse(la.isLiveAfter(st, 1), "a stored-but-never-loaded slot is dead");
    }

    @Test
    void aHandlerReadMakesTheSlotLiveAcrossTheTry() {
        // A slot read only in the catch handler must stay live through the try
        // body (conservative exception edge).
        LabelId start = new LabelId(0);
        LabelId end = new LabelId(1);
        LabelId handler = new LabelId(2);
        KofStoreLocal st = store(3);
        // 0: lit; 1: store(3); 2: trystart; 3: label start; 4: label end;
        // 5: jump after; 6: label handler; 7: load(3); 8: return;
        // 9: label after; 10: return
        LabelId after = new LabelId(3);
        LivenessAnalysis la = LivenessAnalysis.ofOps(List.of(
                KofLoadLiteral.ofInt(9), st,
                new KofTryStart(start, end, handler, "String", 4),
                new KofLabel(start), new KofLabel(end), new KofJump(after),
                new KofLabel(handler), load(3), new KofReturnVoid(),
                new KofLabel(after), new KofReturnVoid()));
        assertTrue(la.isLiveAfter(st, 3),
                "a slot read only in the handler is live through the try");
    }

    @Test
    void analysisIsDeterministic() {
        // §20 determinism: the same op stream yields identical live sets.
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(0), store(1),
                load(1), new KofReturnVoid());
        LivenessAnalysis a = LivenessAnalysis.ofOps(ops);
        LivenessAnalysis b = LivenessAnalysis.ofOps(ops);
        assertEquals(a.liveIn(0), b.liveIn(0));
        assertEquals(a.liveOut(0), b.liveOut(0));
        assertEquals(a.liveAfter(ops.get(1)), b.liveAfter(ops.get(1)));
    }
}
