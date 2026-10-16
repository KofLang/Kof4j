package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 x86_64 of {@code docs/development/assembly-optimization-plan.md}
 * ("reduction of instructions"): the return value is the last value pushed; when
 * that push is {@code pushq %rax} the {@code pushq %rax} + {@code popq %rax}
 * pair is a no-op and is dropped (the value is already in rax).
 */
class NativeReturnPeepholeTest {

    @Test
    void aPushRaxBeforeReturnIsDropped() {
        StringBuilder sb = new StringBuilder("    addl %ecx, %eax\n    pushq %rax\n");
        NativeReturnEmit.popReturnValue(sb);
        assertEquals("    addl %ecx, %eax\n", sb.toString(),
                "pushq %rax + popq %rax at a return is a no-op pair");
    }

    @Test
    void aPushRaxSeparatedByLocIsStillDropped() {
        StringBuilder sb = new StringBuilder("    addl %ecx, %eax\n    pushq %rax\n    .loc 1 1 0\n");
        NativeReturnEmit.popReturnValue(sb);
        assertEquals("    addl %ecx, %eax\n    .loc 1 1 0\n", sb.toString(),
                "the redundant pair is dropped; the .loc directive remains");
    }

    @Test
    void aLiteralPushKeepsThePop() {
        StringBuilder sb = new StringBuilder("    pushq $42\n");
        NativeReturnEmit.popReturnValue(sb);
        assertEquals("    pushq $42\n    popq %rax\n", sb.toString(),
                "a literal push still needs the pop into rax");
    }

    @Test
    void aRegisterLocalPushKeepsThePop() {
        StringBuilder sb = new StringBuilder("    pushq %rbx\n");
        NativeReturnEmit.popReturnValue(sb);
        assertEquals("    pushq %rbx\n    popq %rax\n", sb.toString(),
                "a register-placed local push still needs the pop into rax");
    }
}
