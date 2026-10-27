package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Q7 (no silent fallback): the riscv→aarch64 text translator must REFUSE a
 * mnemonic it has no translation for, rather than passing the riscv line through
 * verbatim — a same-named aarch64 instruction with different semantics would be a
 * silent miscompile.
 */
class NativeAarch64TranslatorRefusesTest {

    @Test
    void anUnknownMnemonicIsRefused() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> NativeAarch64Translator.translateRiscvToAarch64("kofmadeup a0, a1, a2"));
        assertTrue(e.getMessage().startsWith("NATIVE002"), e.getMessage());
    }

    @Test
    void labelsAndDirectivesStillPassThrough() {
        assertEquals(java.util.List.of(".Lkof_0:"),
                NativeAarch64Translator.translateRiscvToAarch64(".Lkof_0:"));
        assertEquals(java.util.List.of("    .loc 1 3"),
                NativeAarch64Translator.translateRiscvToAarch64("    .loc 1 3"));
    }
}
