package dev.kof.compiler.nat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * #721-REGRESSION (01/10, lane memory-safety/native-cross): o merge externo do
 * trig reduziu `needsLibm` a UM `return` na PRIMEIRA linha de código — `call
 * kof_alloc` antes de `call pow` devolvia false e o link cross perdia `-lm`
 * (`undefined reference to 'pow'` em riscv64). A varredura por USO (row 10,
 * 27/09) precisa continuar linha a linha. Fixa SEM dependência de toolchain.
 */
class NativeCrossLinkNeedsLibmTest {

    @Test
    void libmSymbolAfterOtherCallsIsFound() {
        String asm = """
            .globl kof_main
            kof_main:
                call kof_alloc
                call kof_print_int

                # blank lines + other callees first must NOT end the scan
                call kof_heap_root_end
                call pow
                ret
            """;
        assertTrue(NativeCrossLink.needsLibm(asm));
    }

    @Test
    void trigSymbolIsFound() {
        String asm = """
            foo:
                call sin
                ret
            """;
        assertTrue(NativeCrossLink.needsLibm(asm));
    }

    @Test
    void powStaysInLibmSet() {
        // row 10 (27/09): o shim kof_math_pow chama `call pow`; o #721 trocou o
        // matcher literal por LIBM_SYMBOLS e ESQUECEU "pow" — sem a entrada o
        // link cross perde `-lm` (undefined reference).
        String asm = """
            foo:
                call pow
                ret
            """;
        assertTrue(NativeCrossLink.needsLibm(asm));
    }

    @Test
    void commentAndLabelMentionsDoNotCount() {
        String asm = """
            # call pow is used by the shim
            .globl kof_math_pow
            kof_math_pow:
                call kof_alloc
            .Lend:
                ret
            """;
        assertFalse(NativeCrossLink.needsLibm(asm));
    }

    @Test
    void noCallLinesAtAllIsFalse() {
        assertFalse(NativeCrossLink.needsLibm(".text\n.globl x\nx:\n"));
    }
}
