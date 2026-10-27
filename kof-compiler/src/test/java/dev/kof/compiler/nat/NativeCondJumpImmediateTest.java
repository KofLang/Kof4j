package dev.kof.compiler.nat;

import dev.kof.compiler.KofComparison;
import dev.kof.compiler.Target;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.LabelId;
import dev.kof.compiler.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 x86_64 of {@code docs/assembly-optimization-plan.md}
 * ("reduction of instructions for constants"): a CONSTANT right operand of an
 * integer conditional jump uses the immediate form {@code cmp{l,q} $N,
 * %{e,r}ax} (or the canonical {@code test} for {@code == 0}/{@code != 0})
 * instead of popping it into {@code %rcx}. Uses a minimal backend whose
 * {@code resolveLabel} is a stub so the assertion can focus on the compare.
 */
class NativeCondJumpImmediateTest {

    private static KofConditionalJump cj(KofComparison c) {
        return new KofConditionalJump(c, Type.PrimitiveType.INT, new LabelId(0), new LabelId(1));
    }

    /** A backend whose label resolution is a fixed string (the compare is what we assert). */
    private static NativeBackend stub() {
        return new NativeBackend(Target.NATIVE) {
            @Override public String resolveLabel(LabelId id) { return "L" + id.id(); }
        };
    }

    @Test
    void anEqualityWithZeroUsesTestInABranch() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $0\n");
        NativeOpHelpers.emitConditionalJump(stub(), sb, cj(KofComparison.EQ));
        assertTrue(sb.toString().contains("popq %rax\n    testl %eax, %eax\n    je L0"),
                "x == 0 uses testl %eax, %eax; got:\n" + sb);
    }

    @Test
    void aGreaterThanWithAConstantBranchesImmediate() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $5\n");
        NativeOpHelpers.emitConditionalJump(stub(), sb, cj(KofComparison.GT));
        assertTrue(sb.toString().contains("popq %rax\n    cmpl $5, %eax\n    jg L0"),
                "x > 5 uses cmpl $5, %eax; got:\n" + sb);
    }

    @Test
    void aComputedOperandKeepsTheRegisterCompare() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq %r12\n");
        NativeOpHelpers.emitConditionalJump(stub(), sb, cj(KofComparison.EQ));
        assertTrue(sb.toString().contains("popq %rax\n    popq %rcx\n    cmpl %eax, %ecx"),
                "a computed operand keeps the register compare; got:\n" + sb);
    }
}
