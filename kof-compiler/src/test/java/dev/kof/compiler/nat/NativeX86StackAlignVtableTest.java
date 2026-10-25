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
}
