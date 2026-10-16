package dev.kof.compiler.nat;

import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 x86_64 of {@code docs/development/assembly-optimization-plan.md}
 * ("reduction of instructions for constants"): a CONSTANT right operand of a
 * comparison uses the immediate form {@code cmp{l,q} $N, %{e,r}ax} (or the
 * canonical {@code test} for {@code == 0}/{@code != 0}) instead of round-tripping
 * it through {@code %rcx}.
 */
class NativeCompareImmediateTest {

    @Test
    void anEqualityWithZeroUsesTest() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $0\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
        assertEquals("    pushq %rbx\n    popq %rax\n    testl %eax, %eax\n    sete %al\n    movzbl %al, %eax\n    pushq %rax\n",
                sb.toString(), "x == 0 uses the canonical testl %eax, %eax");
    }

    @Test
    void anInequalityWithZeroUsesTest() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $0\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.NE, Type.PrimitiveType.INT));
        assertTrue(sb.toString().contains("testl %eax, %eax\n    setne %al"),
                "x != 0 uses testl with setne");
    }

    @Test
    void anEqualityWithAConstantUsesCmpImmediate() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $5\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
        assertEquals("    pushq %rbx\n    popq %rax\n    cmpl $5, %eax\n    sete %al\n    movzbl %al, %eax\n    pushq %rax\n",
                sb.toString());
    }

    @Test
    void aLessThanWithAConstantUsesCmpImmediate() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $5\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.LT, Type.PrimitiveType.INT));
        assertEquals("    pushq %rbx\n    popq %rax\n    cmpl $5, %eax\n    setl %al\n    movzbl %al, %eax\n    pushq %rax\n",
                sb.toString());
    }

    @Test
    void aLongComparisonUsesThe64BitImmediateForm() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $5\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.GE, Type.PrimitiveType.LONG));
        assertEquals("    pushq %rbx\n    popq %rax\n    cmpq $5, %rax\n    setge %al\n    movzbl %al, %eax\n    pushq %rax\n",
                sb.toString());
    }

    @Test
    void aComputedComparisonKeepsTheClForm() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq %r12\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
        assertEquals("    pushq %rbx\n    pushq %r12\n    popq %rcx\n    popq %rax\n    cmpl %ecx, %eax\n    sete %al\n    movzbl %al, %eax\n    pushq %rax\n",
                sb.toString(), "a computed operand keeps the %rcx form");
    }
}
