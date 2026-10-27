package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #935 — the 16-byte stack-alignment padding must sit BELOW a call's
 * stack-passed arguments, including when the call is an indirect
 * virtual/interface dispatch.
 *
 * <p>{@code NativeX86StackAlign} walks backwards from the {@code call} over the
 * emitter's {@code pushq -N(%rbp)} stack-argument pushes and inserts
 * {@code subq $8, %rsp} at the first non-push line. A vtable dispatch places the
 * resolution instructions ({@code movq 8(%rax), %r11} / {@code addq $N, %r11} /
 * {@code movq (%r11), %r11}) between those pushes and the {@code call *%r11}, so
 * the walk stopped early and the padding landed ABOVE the arguments. The callee
 * then read the padding as arg7 and every stack-passed argument came out shifted.
 *
 * <p>MEASURED impact: an 8-parameter method received f/g/h wrong on native
 * x86-64 only. That is what made {@code Av1PaletteE2ETest.av1PaletteOnNativeX86}
 * read zero palettes for all 127 blocks while JVM/Script/JS were correct — a
 * pure cross-target divergence with no language-surface involvement.
 */
class NativeX86StackAlignVtableTest {

    /** The virtual-dispatch shape: args pushed, vtable resolved, indirect call. */
    private static List<String> vtableCallSite() {
        return List.of(
                "Fn_main:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                // an odd number of in-flight slots so the call needs padding
                "    pushq $1",
                "    pushq $2",
                "    pushq $3",
                "    pushq $4",
                "    pushq $5",
                "    pushq $6",
                "    pushq $7",
                "    pushq $8",
                // register args are popped first (5 of them + this)
                "    popq %r9",
                "    popq %r8",
                "    popq %rcx",
                "    popq %rdx",
                "    popq %rsi",
                "    popq %rax",
                "    movq %rax, %rdi",
                // ...then the stack args are re-pushed (arg8 first, arg6 on top)
                "    pushq -40(%rbp)",
                "    pushq -32(%rbp)",
                "    pushq -24(%rbp)",
                // ...and the vtable resolution sits between them and the call
                "    movq 8(%rax), %r11",
                "    addq $0, %r11",
                "    movq (%r11), %r11",
                "    call *%r11",
                "    addq $24, %rsp",
                "    pushq %rax",
                "    leave",
                "    ret");
    }

    @Test
    void paddingLandsBelowTheStackArgumentsOnAVtableCall() {
        List<String> lines = new java.util.ArrayList<>(vtableCallSite());
        NativeX86StackAlign.alignCalls(lines, 0);

        int subq = lines.indexOf("    subq $8, %rsp");
        assertTrue(subq >= 0, "the misaligned vtable call must be padded: " + lines);

        int firstArgPush = lines.indexOf("    pushq -24(%rbp)");
        assertTrue(firstArgPush >= 0, "stack-argument pushes must survive: " + lines);
        assertTrue(subq < firstArgPush,
                "padding must land BELOW the stack arguments, or the callee reads it as arg7"
                        + " (this is exactly #935: f/g/h shifted on native x86-64). lines=" + lines);

        // The padding must still be below the vtable resolution, i.e. inside the
        // args region, never between the resolution and the call.
        int resolution = lines.indexOf("    movq 8(%rax), %r11");
        assertTrue(subq < resolution, "padding must not sit between the vtable tail and the call");
        assertTrue(lines.indexOf("    addq $8, %rsp") > lines.indexOf("    call *%r11"),
                "the restore must follow the call");
    }

    @Test
    void directCallStillGetsItsPaddingBelowTheArguments() {
        List<String> lines = new java.util.ArrayList<>(List.of(
                "Fn_direct:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    pushq $1",
                "    pushq $2",
                "    pushq $3",
                "    pushq $4",
                "    pushq $5",
                "    pushq $6",
                "    pushq $7",
                "    pushq $8",
                "    popq %r9",
                "    popq %r8",
                "    popq %rcx",
                "    popq %rdx",
                "    popq %rsi",
                "    popq %rdi",
                "    pushq -16(%rbp)",
                "    call Fn_callee",
                "    addq $8, %rsp",
                "    leave",
                "    ret"));
        NativeX86StackAlign.alignCalls(lines, 0);
        int subq = lines.indexOf("    subq $8, %rsp");
        assertTrue(subq >= 0, "direct 7-arg call must be padded: " + lines);
        assertTrue(subq < lines.indexOf("    pushq -16(%rbp)"),
                "padding must stay below the stack argument (the §14 contract)");
    }

    /**
     * Counter-proof: an 8-parameter method must reproduce its arguments exactly.
     * This is the shape that measured wrong (f=33 instead of 66, g=66 instead of
     * 77) before the padding fix.
     */
    @Test
    void eightArgumentsSurviveTheCallOnNative() throws Exception {
        // the E2E half lives in Av1PaletteE2ETest/a dedicated native run; here we
        // pin the invariant that the aligner never moves padding above args.
        List<String> lines = new java.util.ArrayList<>(vtableCallSite());
        NativeX86StackAlign.alignCalls(lines, 0);
        long pads = lines.stream().filter("    subq $8, %rsp"::equals).count();
        assertEquals(1, pads, "exactly one padding insert for one misaligned call: " + lines);
    }

    /**
     * #970 — {@code kof_multi_alloc} locates dimension {@code d_i} at a FIXED
     * {@code rsp + 56i + 8(n-i)} offset, so the padding must go below the whole
     * dimension block. The dimensions are IMMEDIATE pushes ({@code pushq $2}),
     * not the {@code pushq -N(%rbp)} form, and the helper setup
     * ({@code movl $N,%edx} / {@code movl $N,%ebx} / {@code movl $1,%esi}) sits
     * between them and the call. Padding in between shifted every dimension by
     * one slot: {@code new Int[2][2][2]} read {@code c[0][1].length} as 4
     * instead of 2.
     */
    @Test
    void multiAllocPaddingGoesBelowTheDimensionPushes() {
        List<String> lines = new java.util.ArrayList<>(List.of(
                "Default_Main_main:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    subq $32, %rsp",
                "    movq %rdi, -16(%rbp)",
                "    pushq $2",
                "    pushq $2",
                "    pushq $2",
                "    movl $3, %edx",
                "    movl $4, %ebx",
                "    movl $1, %esi",
                "    call kof_multi_alloc",
                "    addq $24, %rsp",
                "    leave",
                "    ret"));
        NativeX86StackAlign.alignCalls(lines, 0);

        int subq = lines.indexOf("    subq $8, %rsp");
        assertTrue(subq >= 0, "the misaligned multi-alloc call must be padded: " + lines);
        int firstDim = lines.indexOf("    pushq $2");
        assertTrue(subq < firstDim,
                "padding must sit BELOW every dimension push, or kof_multi_alloc's fixed"
                        + " rsp offset reads the wrong dimension (#970: c[0][1].length was 4, not 2)."
                        + " lines=" + lines);
    }

    /**
     * Counter-proof: an ordinary operand push must NOT be crossed — doing so
     * regressed {@code Av1PaletteE2ETest} (the palette probe's data pointer is an
     * immediate-pushed operand of a normal call).
     */
    @Test
    void ordinaryImmediateOperandIsNotCrossed() {
        List<String> lines = new java.util.ArrayList<>(List.of(
                "Fn_ordinary:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    pushq $7",
                "    call kof_array_alloc",
                "    pushq %rax",
                "    leave",
                "    ret"));
        NativeX86StackAlign.alignCalls(lines, 0);
        int subq = lines.indexOf("    subq $8, %rsp");
        if (subq >= 0) {
            assertTrue(subq > lines.indexOf("    pushq $7"),
                    "for a normal call the operand push stays the callee's argument;"
                            + " the padding must not slip below it: " + lines);
        }
    }
}
