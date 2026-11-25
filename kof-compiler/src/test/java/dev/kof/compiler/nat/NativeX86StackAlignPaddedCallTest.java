package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1226 — a call that HAS an argument pushed under it (delta ≡ 8) is padded with
 * {@code subq $8, %rsp}, and the padding stays under the pushed argument until
 * the {@code popq} that consumes it. The {@code call}'s own stack state was
 * correct, so the ABI is fine; but the CALLEE's first 8 bytes below its return
 * address now hold the pad instead of the pushed argument.
 *
 * <p>MEASURED shape (Énois, compiled native from the tree CLI):
 * <pre>
 *     pushq %rax          # System.out
 *     pushq $237          # the literal
 *     pushq $1            # titleLines
 *     movq $3, %rsi       # bodyLines
 *     popq %rdi           # ← reads the PAD (0), not $1
 *     call enois_BlogPostCard_heightFor
 * </pre>
 * Here a complete call ({@code heightFor(1, 3)} = 289) landed between a pushed
 * operand and the consumption of that push, so the site was 8-aligned and got
 * a pad — and the {@code popq} that moves the operand into {@code %rdi} read
 * the pad instead ({@code rdi = 0}), silently changing the first argument.
 *
 * <p>The rule: the padding must never sit between a pushed operand and the
 * {@code pop}/{@code addq $8} that re-reads it. A region with a naked
 * {@code popq %<reg>} that moves the stack into an argument register is exactly
 * that read; when it is present, the aligner must leave the call alone. (The
 * rest of the emitted code keeps its alignment; this is the same "fixed window
 * under a push" shape §1212 refused for a nested call.)
 */
class NativeX86StackAlignPaddedCallTest {

    /** The measured emitter shape of the Énois main (CLI, 25/11). */
    private static List<String> callsBetweenPushes() {
        return List.of(
                "Default_Main_main:",
                "    pushq %rbp",
                "    movq %rsp, %rbp",
                "    subq $32, %rsp",
                "    leaq kof_static_java_lang_System_out(%rip), %rax",
                "    movq 0(%rax), %rax",
                "    pushq %rax",
                "    pushq $237",
                "    pushq $1",
                "    movq $3, %rsi",
                "    popq %rdi",
                "    call enois_BlogPostCard_heightFor",
                "    imull $0, %eax",
                "    movq %rax, %rcx",
                "    popq %rax",
                "    addl %ecx, %eax",
                "    call enois_BlogPostCard_heightFor",
                "    leave",
                "    ret",
                "Default_Main_end:");
    }

    @Test
    void aCallThatReadsItsOperandWithAPopIsNotPadded() {
        List<String> lines = new ArrayList<>(callsBetweenPushes());
        int padded = NativeX86StackAlign.alignCalls(lines, 1);
        // the outer call (kof_int_to_string) may still need its own pad; what must
        // never happen is a pad ABOVE the pushed $1/$237, i.e. between them and the
        // `popq %rdi`.
        int popIndex = lines.indexOf("    popq %rdi");
        int callIndex = lines.indexOf("    call enois_BlogPostCard_heightFor");
        assertTrue(popIndex >= 0 && callIndex == popIndex + 1,
                "nenhum pad entre o popq e a chamada: " + lines);
        assertFalse(lines.subList(0, popIndex).contains("    subq $8, %rsp"),
                "nenhum pad encoberto por um push pendente: " + lines);
        assertTrue(padded >= 0, "contagem: " + padded);
    }

}
