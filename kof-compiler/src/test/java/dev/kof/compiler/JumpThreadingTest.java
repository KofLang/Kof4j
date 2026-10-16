package dev.kof.compiler;

import dev.kof.compiler.backend.JumpThreading;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B/§3 O2 of the assembly-optimization plan: jump threading. A trampoline
 * ({@code Label(L); Jump(M)}) is bypassed — a jump to L is retargeted to M. The
 * pass is additive — these tests pin the rewrite and the conservative barriers.
 */
class JumpThreadingTest {

    private final JumpThreading pass = new JumpThreading();

    private List<KofOperation> run(List<KofOperation> ops) {
        return pass.run(ops, new java.util.IdentityHashMap<>());
    }

    @Test
    void aJumpToATrampolineIsThreaded() {
        LabelId a = new LabelId(0), m = new LabelId(1), end = new LabelId(2);
        // Jump(a); Label(a); Jump(m); Label(m); return ; Label(end); return
        List<KofOperation> out = run(List.of(
                new KofJump(a),
                new KofLabel(a), new KofJump(m),
                new KofLabel(m), new KofReturnVoid(),
                new KofLabel(end), new KofReturnVoid()));
        assertInstanceOf(KofJump.class, out.get(0));
        assertEquals(m, ((KofJump) out.get(0)).target(),
                "the jump to the trampoline is retargeted to its final destination");
    }

    @Test
    void aChainResolvesToTheFinalTarget() {
        LabelId a = new LabelId(0), b = new LabelId(1), m = new LabelId(2);
        // Jump(a); Label(a); Jump(b); Label(b); Jump(m); Label(m); return
        List<KofOperation> out = run(List.of(
                new KofJump(a),
                new KofLabel(a), new KofJump(b),
                new KofLabel(b), new KofJump(m),
                new KofLabel(m), new KofReturnVoid()));
        assertEquals(m, ((KofJump) out.get(0)).target(), "the chain resolves to m");
    }

    @Test
    void aConditionalJumpIsThreadedToo() {
        LabelId a = new LabelId(0), m = new LabelId(1), f = new LabelId(2);
        List<KofOperation> out = run(List.of(
                new KofConditionalJump(KofComparison.EQ, Type.PrimitiveType.INT, a, f),
                new KofLabel(a), new KofJump(m),
                new KofLabel(m), new KofReturnVoid(),
                new KofLabel(f), new KofReturnVoid()));
        KofConditionalJump cj = (KofConditionalJump) out.get(0);
        assertEquals(m, cj.trueLabel(), "the conditional true-branch is retargeted");
        assertEquals(f, cj.falseLabel(), "the unrelated false-branch is untouched");
    }

    @Test
    void aNonTrampolineLabelIsNotThreaded() {
        LabelId a = new LabelId(0), m = new LabelId(1);
        // Label(a) is followed by a return, not a jump -> not a trampoline.
        List<KofOperation> out = run(List.of(
                new KofJump(a),
                new KofLabel(a), new KofReturnVoid(),
                new KofLabel(m), new KofReturnVoid()));
        assertEquals(a, ((KofJump) out.get(0)).target(), "a non-trampoline is left alone");
    }

    @Test
    void noTrampolinesLeavesTheStreamIdentical() {
        LabelId a = new LabelId(0);
        List<KofOperation> ops = List.of(
                new KofJump(a),
                new KofLabel(a), KofLoadLiteral.ofInt(1), new KofReturnVoid());
        assertEquals(ops, run(ops));
    }

    @Test
    void aSelfLoopTrampolineIsLeftAlone() {
        LabelId a = new LabelId(0);
        List<KofOperation> out = run(List.of(
                new KofJump(a),
                new KofLabel(a), new KofJump(a)));
        assertEquals(a, ((KofJump) out.get(0)).target(), "a self-loop is not threaded");
    }
}
