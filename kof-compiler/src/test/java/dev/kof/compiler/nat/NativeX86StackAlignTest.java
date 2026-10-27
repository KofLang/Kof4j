package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §14 (ABI and optimization): "Optimizations must never incorrectly change …
 * stack alignment". SysV requires {@code %rsp % 16 == 0} at every {@code call};
 * this stack machine pushes operands, so an odd push in flight misaligns the
 * callee — the class recorded in §253 (glibc SSE SIGSEGV).
 */
class NativeX86StackAlignTest {

    private static List<String> lines(String... ls) {
        return new ArrayList<>(List.of(ls));
    }

    @Test
    void anAlignedCallIsLeftAlone() {
        List<String> ls = lines(
                "Default_f:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    subq $32, %rsp",
                "    call kof_alloc",
                "    leave",
                "    ret");
        assertEquals(0, NativeX86StackAlign.alignCalls(ls, 0));
        assertTrue(ls.contains("    call kof_alloc"));
    }

    @Test
    void anOddPushInFlightMisalignsTheCallAndIsPadded() {
        List<String> ls = lines(
                "Default_f:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    subq $32, %rsp",
                "    pushq %rax",
                "    call kof_int_to_string",
                "    addq $8, %rsp",
                "    leave",
                "    ret");
        assertEquals(1, NativeX86StackAlign.misalignedCallSites(ls, 0).size());
        assertEquals(1, NativeX86StackAlign.alignCalls(ls, 0));
        int call = ls.indexOf("    call kof_int_to_string");
        assertEquals("    subq $8, %rsp", ls.get(call - 1));
        assertEquals("    addq $8, %rsp", ls.get(call + 1));
    }

    @Test
    void afterPaddingNoCallIsMisaligned() {
        List<String> ls = lines(
                "Default_f:",
                "    subq $24, %rsp",   // 8 - 24 = -16, then one push -> misaligned
                "    pushq %rax",
                "    call foo",
                "    popq %rax",
                "    pushq %rax",
                "    call bar",
                "    popq %rax",
                "    leave",
                "    ret");
        assertEquals(2, NativeX86StackAlign.misalignedCallSites(ls, 0).size());
        NativeX86StackAlign.alignCalls(ls, 0);
        assertEquals(0, NativeX86StackAlign.misalignedCallSites(ls, 0).size(),
                "every call must end 16-aligned: " + ls);
    }

    @Test
    void aCallImmediatelyFollowedByALabelIsNotPadded() {
        // The inserted restore would be skipped when control jumps to the label.
        List<String> ls = lines(
                "Default_f:",
                "    subq $32, %rsp",
                "    pushq %rax",
                "    call foo",
                ".Lkof_1:",
                "    popq %rax",
                "    leave",
                "    ret");
        assertEquals(0, NativeX86StackAlign.alignCalls(ls, 0),
                "a call whose next op is a label is left alone");
    }

    @Test
    void anAlreadyAlignedFfiCallIsNotPadded() {
        // The FFI downcall aligns itself: `andq $-16` forces %rsp to a multiple
        // of 16 no matter the operand pushes in flight. Padding it again leaves
        // %rsp % 16 == 8 at the call (#974: libm's movaps, exit 139).
        List<String> ls = lines(
                "Default_f:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    subq $32, %rsp",
                "    pushq %rax",            // odd operand in flight
                "    movq %rsp, %rbx",
                "    andq $-16, %rsp",
                "    call sqrt@PLT",
                "    movq %rbx, %rsp",
                "    leave",
                "    ret");
        assertEquals(0, NativeX86StackAlign.alignCalls(ls, 0),
                "an `andq $-16` call is aligned already: " + ls);
        int call = ls.indexOf("    call sqrt@PLT");
        assertEquals("    andq $-16, %rsp", ls.get(call - 1), "nothing inserted: " + ls);
        assertEquals("    movq %rbx, %rsp", ls.get(call + 1), "nothing restored: " + ls);
    }

    @Test
    void theFfiOddSpillPaddingIsAccounted() {
        // With a stack-passed (spilled) argument the emitter subtracts 8 itself
        // so the ODD spill count lands aligned; the pass must count that
        // subtraction instead of adding its own.
        List<String> ls = lines(
                "Default_f:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    subq $32, %rsp",
                "    pushq %rax",            // odd operand in flight
                "    movq %rsp, %rbx",
                "    andq $-16, %rsp",
                "    subq $8, %rsp",          // the emitter's odd-spill parity
                "    pushq -48(%rbp)",        // the spilled argument
                "    call probe9i@PLT",
                "    movq %rbx, %rsp",
                "    leave",
                "    ret");
        assertEquals(0, NativeX86StackAlign.alignCalls(ls, 0),
                "parity already handled by the emitter: " + ls);
    }

    @Test
    void theRestoreKeepsModellingTheFollowingCalls() {
        // `movq %rbx, %rsp` puts the stack back where the save found it; a call
        // after the FFI sequence is still judged against that real delta.
        List<String> ls = lines(
                "Default_f:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    subq $32, %rsp",
                "    pushq %rax",            // odd push survives the FFI call
                "    movq %rsp, %rbx",
                "    andq $-16, %rsp",
                "    call sqrt@PLT",
                "    movq %rbx, %rsp",
                "    call kof_alloc",        // still misaligned -> must be padded
                "    leave",
                "    ret");
        assertEquals(1, NativeX86StackAlign.alignCalls(ls, 0),
                "only the call after the restore is padded: " + ls);
        int ffi = ls.indexOf("    call sqrt@PLT");
        assertEquals("    andq $-16, %rsp", ls.get(ffi - 1), "the FFI call stays alone: " + ls);
        int alloc = ls.indexOf("    call kof_alloc");
        assertEquals("    subq $8, %rsp", ls.get(alloc - 1), "restored delta is 8: " + ls);
    }

    @Test
    void paddingNeverCrossesStackArguments() {
        // 7-arg call: arg7 is pushed so the callee reads it at 0(%rsp). The
        // padding must sit BELOW that push, or it becomes arg7 (measured:
        // Av1CoeffsE2ETest went out-of-bounds).
        List<String> ls = lines(
                "Default_f:",
                "    subq $16, %rsp",   // 8-16-8-8 = -24 (mod 16 == 8)
                "    pushq %rax",
                "    pushq -48(%rbp)",   // arg7 (stack argument)
                "    call foo",
                "    addq $8, %rsp",      // caller cleanup of the stack arg
                "    addq $8, %rsp",      // contract cleanup
                "    popq %rax",
                "    leave",
                "    ret");
        NativeX86StackAlign.alignCalls(ls, 0);
        int call = ls.indexOf("    call foo");
        assertEquals("    pushq -48(%rbp)", ls.get(call - 1),
                "arg7 must stay adjacent to the call: " + ls);
        assertEquals("    subq $8, %rsp", ls.get(call - 2),
                "the padding goes below the stack argument: " + ls);
    }
}
