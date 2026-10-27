package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §18: the asm-level counters complement {@code IrMetrics} (IR stream) so the ISA
 * peepholes (§7) can be measured on the emitted text. Labels, directives and
 * comments are not instructions; only the mov family is classified by memory
 * operand.
 */
class AsmMetricsTest {

    @Test
    void labelsDirectivesAndCommentsAreNotInstructions() {
        AsmMetrics m = AsmMetrics.of(List.of(
                ".Lkof_0:",
                "    .loc 1 3",
                "    # comentario",
                "",
                "    ret"));
        assertEquals(1, m.instructions());
        assertEquals(1, m.returns());
    }

    @Test
    void memorySourceIsALoadAndMemoryDestinationAStore() {
        AsmMetrics m = AsmMetrics.of(List.of(
                "    movq -8(%rbp), %rax",
                "    movq %rax, -16(%rbp)",
                "    movq %r12, %r13"));
        assertEquals(3, m.instructions());
        assertEquals(1, m.loads());
        assertEquals(1, m.stores());
    }

    @Test
    void pushPopBranchAndCallAreCounted() {
        AsmMetrics m = AsmMetrics.of(List.of(
                "    pushq %rax",
                "    popq %rbx",
                "    cmpl $2, %eax",
                "    jne .Lkof_4",
                "    call kof_alloc",
                "    leaq 23(%rax), %rsi"));
        assertEquals(6, m.instructions());
        assertEquals(1, m.stores());   // push
        assertEquals(1, m.loads());    // pop
        assertEquals(1, m.branches());
        assertEquals(1, m.calls());
    }

    @Test
    void leaIsNotCountedAsALoad() {
        // lea computes an address; it does not dereference memory.
        AsmMetrics m = AsmMetrics.of(List.of("    leaq 23(%rax), %rsi"));
        assertEquals(1, m.instructions());
        assertEquals(0, m.loads());
        assertEquals(0, m.stores());
    }

    @Test
    void aPeepholedPairMeasuresFewerInstructions() {
        AsmMetrics before = AsmMetrics.of(List.of(
                "    pushq %r12", "    movq %r12, %rax", "    popq %rax"));
        AsmMetrics after = AsmMetrics.of(List.of("    movq %r12, %rax"));
        assertTrue(after.instructions() < before.instructions());
    }

    @Test
    void aarch64BranchesAndCallsAreCounted() {
        // Measured limitation fixed: counting only x86 spellings reported 0
        // branches/calls on aarch64/riscv64 asm.
        AsmMetrics m = AsmMetrics.of(List.of(
                "    b.le .Lkof_0",
                "    bl kof_alloc",
                "    cbz x0, .Lkof_1",
                "    ret"));
        assertEquals(1, m.calls());
        assertEquals(2, m.branches());
        assertEquals(1, m.returns());
    }

    @Test
    void riscv64BranchesAndCallsAreCounted() {
        AsmMetrics m = AsmMetrics.of(List.of(
                "    beq a0, a1, .Lkof_0",
                "    jal ra, kof_alloc",
                "    ret"));
        assertEquals(1, m.calls());
        assertEquals(1, m.branches());
        assertEquals(1, m.returns());
    }

    @Test
    void riscv64LoadsAndStoresAreCounted() {
        // Measured defect: RISC-V spells memory access by WIDTH (ld/sd/lw/sw),
        // not with a `mov` family, so the cross runs reported loads=0/stores=0.
        AsmMetrics m = AsmMetrics.of(List.of(
                "    ld a0, 0(sp)",
                "    lw a1, 8(sp)",
                "    lbu a2, 3(sp)",
                "    sd a0, 16(sp)",
                "    sw a1, 24(sp)",
                "    sb a2, 2(sp)"));
        assertEquals(3, m.loads());
        assertEquals(3, m.stores());
    }

    @Test
    void aarch64LoadsAndStoresAreCounted() {
        AsmMetrics m = AsmMetrics.of(List.of(
                "    ldr x0, [sp, #8]",
                "    str x0, [sp, #16]"));
        assertEquals(1, m.loads());
        assertEquals(1, m.stores());
    }
}
