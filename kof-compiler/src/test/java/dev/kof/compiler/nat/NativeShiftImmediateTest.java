package dev.kof.compiler.nat;

import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 x86_64 of {@code docs/assembly-optimization-plan.md}
 * ("reduction of instructions for constants"): a CONSTANT shift count uses the
 * immediate form {@code shl{l,q} $N, %{e,r}ax} instead of round-tripping the
 * count through {@code %rcx} ({@code pushq $N; popq %rcx; shl %cl}).
 */
class NativeShiftImmediateTest {

    @Test
    void aConstantIntShiftUsesTheImmediateForm() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $3\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
        assertEquals("    pushq %rbx\n    popq %rax\n    shll $3, %eax\n    pushq %rax\n",
                sb.toString(),
                "a constant shift uses shll $imm and drops the push/pop of the count");
    }

    @Test
    void aConstantLongShiftUsesTheImmediateForm() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $5\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.LONG));
        assertEquals("    pushq %rbx\n    popq %rax\n    shlq $5, %rax\n    pushq %rax\n", sb.toString());
    }

    @Test
    void aShiftCountIsMaskedTo31ForInt() {
        // Hardware/JVM mask the shift count: int shifts use count & 31.
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq $35\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
        assertTrue(sb.toString().contains("shll $3, %eax"),
                "35 & 31 == 3, matching the JVM/hardware shift semantics");
    }

    @Test
    void aComputedShiftCountKeepsTheClForm() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    pushq %r12\n");
        NativeX86Arith.emitBinary(sb, new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
        assertEquals("    pushq %rbx\n    pushq %r12\n    popq %rcx\n    popq %rax\n    shll %cl, %eax\n    pushq %rax\n",
                sb.toString(),
                "a computed count still uses the %cl form");
    }
}
