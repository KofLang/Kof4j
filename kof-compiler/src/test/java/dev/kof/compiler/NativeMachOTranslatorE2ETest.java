package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import dev.kof.compiler.nat.NativeMachO;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-1 (#1135, D-NATIVE-MACOS) — as faces do TRADUTOR que a libSystem do
 * assembler do Mach-O recusou, cada uma medida.
 *
 * <p><b>AS MEDIDAS (o assembler real do Mach-O):</b>
 *
 * <ul>
 *   <li>um comentário que começa por uma PALAVRA DE DIRETIVA (`# if V & 1: …`)
 *       é lido como uma diretiva de pré-processador ("unterminated
 *       conditional directive") — a marca vira {@code //};</li>
 *   <li>{@code .hword} é GNU ("unknown directive") — vira {@code .short};</li>
 *   <li>{@code .4byte}/{@code .8byte}/{@code .balign} são GNU — viram
 *       {@code .long}/{@code .quad}/{@code .align};</li>
 *   <li>{@code .pushsection}/{@code .popsection} são ELF ("unexpected token")
 *       — viram {@code .section} (e a pilha restaura a seção anterior);</li>
 *   <li>o {@code .align} do Mach-O aceita no máximo 16 ("invalid alignment
 *       value" com 32 e 64);</li>
 *   <li>um rótulo NUMÉRICO do GNU as ("1:") é LOCAL — prefixá-lo ("_1:")
 *       colidia com o outro rótulo de número 1 ("symbol '_1' is already
 *       defined").</li>
 * </ul>
 */
class NativeMachOTranslatorE2ETest {

    private static String translate(String asm) {
        return NativeMachO.translate(asm);
    }

    /** Um comentário que começa por palavra de diretiva vira {@code //}. */
    @Test
    void aCommentThatLooksLikeADirectiveBecomesASlashComment() {
        String out = translate("                # if V & 1: V = (V >> 1) ^ 0xE1 (byte 15)");
        assertTrue(out.contains("// if V & 1:"),
                "the directive-shaped comment becomes a // comment:\n" + out);
        assertFalse(out.contains("# if V & 1:"),
                "the '#' form would break the assembler:\n" + out);
    }

    /** Um comentário NORMAL continua verbatim (o contrato do M-1). */
    @Test
    void anOrdinaryCommentStaysVerbatim() {
        String out = translate("    movq $1, %rdi        # nota: valor inicial");
        assertTrue(out.contains("# nota: valor inicial"),
                "an ordinary comment is verbatim:\n" + out);
    }

    /** {@code .hword} (GNU) vira {@code .short}. */
    @Test
    void theGnuHalfWordDirectiveBecomesShort() {
        String out = translate("    .hword 0xb5, 0x39c");
        assertTrue(out.contains(".short 0xb5, 0x39c"),
                ".hword becomes .short:\n" + out);
    }

    /** {@code .4byte} (GNU) vira {@code .long} — e os símbolos ganham o prefixo. */
    @Test
    void theGnuFourByteDirectiveBecomesLongAndKeepsTheSymbolPrefix() {
        String out = translate("    .4byte .Lfe_Pair_init - Pair_init");
        assertTrue(out.contains(".long .Lfe_Pair_init - _Pair_init"),
                ".4byte becomes .long and the symbol is prefixed:\n" + out);
    }

    /** {@code .pushsection}/{@code .popsection} (ELF) viram {@code .section}. */
    @Test
    void theElfSectionStackBecomesMachSections() {
        String out = translate(".pushsection .rodata\n.asciz \"x\"\n.popsection\n");
        assertFalse(out.contains(".pushsection"),
                "the ELF pushsection is gone:\n" + out);
        assertFalse(out.contains(".popsection"),
                "the ELF popsection is gone:\n" + out);
        assertTrue(out.contains(".section __TEXT,__const"),
                "the rodata section is the Mach-O one:\n" + out);
        assertTrue(out.contains(".section __TEXT,__text"),
                "the text section is restored after the pop:\n" + out);
    }

    /** O {@code .align} do Mach-O aceita no máximo 16. */
    @Test
    void theAlignmentIsClampedToWhatMachOAccepts() {
        String out = translate("    .align 64");
        assertTrue(out.contains(".align 16"),
                "the alignment is clamped to 16:\n" + out);
        assertTrue(translate("    .align 8").contains(".align 8"),
                "an accepted alignment is untouched");
    }

    /** Um rótulo NUMÉRICO do GNU as é local: não ganha prefixo. */
    @Test
    void aNumericLabelStaysLocal() {
        String out = translate("1:  movq %rax, %rdi");
        assertTrue(out.contains("1:"),
                "the numeric label stays numeric:\n" + out);
        assertFalse(out.contains("_1:"),
                "the numeric label is not prefixed:\n" + out);
    }
}
