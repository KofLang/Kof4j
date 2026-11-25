package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1212 — a receiver call sitting between an already-pushed argument and the
 * call that CONSUMES it must never be padded in between.
 *
 * <p>MEASURED shape (Énois {@code Skeleton.shade}, compiled native, clean
 * artifact): the emitter pushes the first argument, then the SECOND argument is
 * itself a receiver call ({@code Tokens.Surface2().r()}), so a complete call
 * lands on top of that push:
 * <pre>
 *     call Tokens_SurfaceCard
 *     movq %rax, %rdi
 *     movq 8(%rax), %r11 / movq (%r11), %r11
 *     call *%r11              # receiver call (delta 8 after the push: misaligned)
 *     pushq %rax              # the arg it produced
 *     call Tokens_Surface2    # the receiver of the nested call — delta 0 here
 *     movq %rax, %rdi
 *     movq 8(%rax), %r11 / movq (%r11), %r11
 *     call *%r11
 *     pushq %rax
 *     movq -8(%rbp), %rax / popq %rsi / popq %rdi
 *     call Sk_channel
 * </pre>
 * The aligner saw the inner site 8-aligned and padded it
 * ({@code subq $8} before {@code call *%r11}, {@code addq $8} after). The inner
 * callee restores its own frame with {@code leave} + {@code ret}, so the
 * restore never ran: {@code %rsp} was left 8 bytes low and the two {@code popq}
 * read the pushed arguments — duplicated, so every channel of the mix came out
 * doubled ({@code 255,255,255} for both pulse ends instead of the card white and
 * the surface-2 token, §1212) — and the process grew 8 bytes of stack per
 * evaluation (the same shape while painting per frame).
 *
 * <p>The fix: the padding is inserted only when the ENTIRE block between the
 * insertion point and the call is composed of the emitter's stack-machine lines
 * (pushes, pops, register moves, {@code .loc}). A nested call / scratch write in
 * that range means the region is a fixed-window read (the callee reads its
 * operands at fixed {@code %rsp} offsets), so it is left alone instead of
 * miscompiled.
 */
class NativeX86StackAlignScratchTest {

    /** The measured emitter shape of {@code Skeleton.shade} (Énois, 25/11). */
    private static List<String> shadeShape() {
        return List.of(
                "enois_Sk_shade:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    subq $48, %rsp",
                "    movq %rdi, -8(%rbp)",
                "    call enois_Tokens_SurfaceCard",
                "    movq %rax, %rdi",
                "    movq 8(%rax), %r11",
                "    addq $1, %r11",
                "    movq (%r11), %r11",
                "    call *%r11",
                "    pushq %rax",
                "    call enois_Tokens_Surface2",
                "    movq %rax, %rdi",
                "    movq 8(%rax), %r11",
                "    addq $1, %r11",
                "    movq (%r11), %r11",
                "    call *%r11",
                "    pushq %rax",
                "    movq -8(%rbp), %rax",
                "    movq %rax, %rdx",
                "    popq %rsi",
                "    popq %rdi",
                "    call enois_Sk_channel",
                "    leave",
                "    ret");
    }

    @Test
    void aReceiverCallIsNeverPaddedInFrontOfItsReceiverCall() {
        List<String> lines = new ArrayList<>(shadeShape());
        NativeX86StackAlign.alignCalls(lines, 0);
        int innerVtable = lines.lastIndexOf("    call *%r11");
        int innerReceiver = lines.indexOf("    call enois_Tokens_Surface2");
        assertTrue(innerReceiver >= 0, "the fixture keeps the nested call: " + lines);
        assertTrue(innerVtable > innerReceiver, "the nested receiver call follows it: " + lines);
        // No padding between the nested receiver call and the call it feeds:
        // the nested callee's `leave` discards the padding and the following
        // popq would read it as the pushed arguments (§1212).
        for (int i = innerReceiver; i < innerVtable; i++) {
            assertEquals(false, lines.get(i).trim().equals("addq $8, %rsp"),
                    "a restore between the nested call and its consumer is the §1212"
                            + " duplicated-argument defect: padding=" + lines);
        }
    }

    @Test
    void aScratchWriteBelowTheRegionsOnlyCallIsStillPadded() {
        List<String> lines = new ArrayList<>(List.of(
                "Fn_x:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    pushq $1",
                "    movl $7, %esi",
                "    call kof_helper",
                "    leave",
                "    ret"));
        assertEquals(1, NativeX86StackAlign.alignCalls(lines, 0),
                "the padding sits under the pushes, below the helper setup, and moves"
                        + " nothing the helper reads: " + lines);
    }

    /** A plain single-call body still gets its padding (no over-correction). */
    @Test
    void aPlainMisalignedCallIsStillPadded() {
        List<String> lines = new ArrayList<>(List.of(
                "Fn_plain:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    pushq $1",
                "    leaq .Lstr_0(%rip), %rdi",
                "    movl $2, %esi",
                "    call kof_string_from_literal",
                "    leave",
                "    ret"));
        assertEquals(1, NativeX86StackAlign.alignCalls(lines, 0), "" + lines);
    }
}
