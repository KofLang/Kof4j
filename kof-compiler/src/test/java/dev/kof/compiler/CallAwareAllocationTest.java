package dev.kof.compiler;

import dev.kof.compiler.backend.InterferenceGraph;
import dev.kof.compiler.backend.LivenessAnalysis;
import dev.kof.compiler.backend.RegisterAllocator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D of the assembly-optimization plan ({@code D-CODEGEN-REWRITE}): the
 * call-awareness primitives. A value live across a call must land in a
 * callee-saved register (colour {@code 0..calleeSavedCount-1}) or spill, else a
 * call clobbers it. These are additive primitives (nothing consumes them yet).
 */
class CallAwareAllocationTest {

    private static KofLoadLocal load(int slot) {
        return new KofLoadLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofStoreLocal store(int slot) {
        return new KofStoreLocal(Type.PrimitiveType.INT, slot);
    }

    private static KofCall call() {
        return new KofCall(null, "m", List.of(), Type.PrimitiveType.INT, KofCallKind.STATIC);
    }

    @Test
    void liveAcrossCallsFindsASlotLiveThroughACall() {
        // lit; store(1); call; load(1); return -> slot 1 is live across the call.
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(1), store(1),
                call(), new KofPop(),
                load(1), new KofReturn(Type.PrimitiveType.INT));
        LivenessAnalysis live = LivenessAnalysis.ofOps(ops);
        assertTrue(live.liveAcrossCalls(ops).contains(1),
                "slot 1 is live across the call");
    }

    @Test
    void aSlotDeadBeforeACallIsNotLiveAcrossIt() {
        // lit; store(1); load(1); pop; call; return -> slot 1 dies before the call.
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(1), store(1),
                load(1), new KofPop(),
                call(), new KofPop(),
                new KofReturnVoid());
        LivenessAnalysis live = LivenessAnalysis.ofOps(ops);
        assertFalse(live.liveAcrossCalls(ops).contains(1),
                "slot 1 is not live across the call");
    }

    @Test
    void aCallCrossingSlotGetsACalleeSavedColor() {
        // Two slots: slot 1 live across a call, slot 2 not. With calleeSavedCount=1,
        // slot 1 must take colour 0 (callee-saved) even though slot 2 could also use it.
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(1), store(1),
                call(), new KofPop(),
                KofLoadLiteral.ofInt(2), store(2),
                load(2), new KofPop(),
                load(1), new KofReturn(Type.PrimitiveType.INT));
        LivenessAnalysis live = LivenessAnalysis.ofOps(ops);
        InterferenceGraph g = InterferenceGraph.of(ops, live);
        Set<Integer> crossing = live.liveAcrossCalls(ops);
        assertTrue(crossing.contains(1));
        var alloc = RegisterAllocator.allocate(g, 4, null, crossing, 1);
        assertTrue(alloc.color(1) >= 0 && alloc.color(1) < 1,
                "the call-crossing slot is confined to the callee-saved colour(s)");
    }

    @Test
    void aCallCrossingSlotSpillsWhenNoCalleeSavedColorIsFree() {
        // Two slots BOTH live across the call, but only 1 callee-saved colour:
        // one of them must spill (it cannot take a caller-saved colour).
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(1), store(1),
                KofLoadLiteral.ofInt(2), store(2),
                call(), new KofPop(),
                load(1), load(2), new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT),
                new KofReturn(Type.PrimitiveType.INT));
        LivenessAnalysis live = LivenessAnalysis.ofOps(ops);
        InterferenceGraph g = InterferenceGraph.of(ops, live);
        Set<Integer> crossing = live.liveAcrossCalls(ops);
        assertTrue(crossing.contains(1) && crossing.contains(2));
        var alloc = RegisterAllocator.allocate(g, 4, null, crossing, 1);
        long inCalleeSaved = java.util.stream.Stream.of(1, 2)
                .filter(s -> alloc.color(s) >= 0 && alloc.color(s) < 1).count();
        assertEquals(1, inCalleeSaved,
                "only one call-crossing slot fits the single callee-saved colour");
    }
}
