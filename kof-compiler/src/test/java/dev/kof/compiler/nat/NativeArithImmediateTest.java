package dev.kof.compiler.nat;

import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 x86_64 of {@code docs/development/assembly-optimization-plan.md}
 * ("reduction of instructions for constants"): a CONSTANT right operand uses the
 * immediate form {@code op{l,q} $N, %{e,r}ax} instead of round-tripping it
 * through {@code %rcx}.
 */
class NativeArithImmediateTest {

    @Test
    void aConstantAddUsesTheImmediateForm() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $5\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT));
        assertEquals("    pushq %rbx\n    popq %rax\n    addl $5, %eax\n    pushq %rax\n", sb.toString());
    }

    @Test
    void aConstantSubKeepsTheOperandOrder() {
        // x - 5: the constant (RHS) is on top, so `subl $5, %eax` is x - 5.
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $5\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.SUB, Type.PrimitiveType.INT));
        assertEquals("    pushq %rbx\n    popq %rax\n    subl $5, %eax\n    pushq %rax\n", sb.toString());
    }

    @Test
    void aConstantMulUsesImulImmediate() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $5\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT));
        assertEquals("    pushq %rbx\n    popq %rax\n    imull $5, %eax\n    pushq %rax\n", sb.toString());
    }

    @Test
    void aConstantLongAddUsesThe64BitImmediateForm() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $7\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.LONG));
        assertEquals("    pushq %rbx\n    popq %rax\n    addq $7, %rax\n    pushq %rax\n", sb.toString());
    }

    @Test
    void aConstantFirstOperandIsNotImmediate() {
        // 100 - x: the constant was pushed FIRST, so it is not on top; the tail is
        // a register push and the %rcx form must be kept (the result differs).
        StringBuilder sb = new StringBuilder("    pushq $100\n    pushq %rbx\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.SUB, Type.PrimitiveType.INT));
        assertEquals("    pushq $100\n    pushq %rbx\n    popq %rcx\n    popq %rax\n    subl %ecx, %eax\n    pushq %rax\n",
                sb.toString());
    }
}
