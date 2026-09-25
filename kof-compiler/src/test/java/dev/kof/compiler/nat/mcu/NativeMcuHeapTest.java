package dev.kof.compiler.nat.mcu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// CodeQL #1054 (uncaught-number-format-exception): KOF_MCU_HEAP now parses
// through a defensive gate — garbage, zero and negative are honest NATIVE002
// diagnostics, never a raw NumberFormatException escaping the compiler.
class NativeMcuHeapTest {

    @Test
    void parseHeapAcceptsPositiveNumber() {
        assertEquals(8192L, NativeMcuRiscv32.parseHeap("8192"));
    }

    @Test
    void parseHeapRejectsGarbageWithNative002() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> NativeMcuRiscv32.parseHeap("abc"));
        assertTrue(e.getMessage().contains("NATIVE002: KOF_MCU_HEAP is not a number: 'abc'"),
                "mensagem deve citar o valor, veio: " + e.getMessage());
    }

    @Test
    void parseHeapRejectsZeroAndNegative() {
        IllegalStateException zero = assertThrows(IllegalStateException.class,
                () -> NativeMcuRiscv32.parseHeap("0"));
        assertTrue(zero.getMessage().contains("must be positive"),
                "zero deve ser recusado, veio: " + zero.getMessage());
        IllegalStateException neg = assertThrows(IllegalStateException.class,
                () -> NativeMcuRiscv32.parseHeap("-5"));
        assertTrue(neg.getMessage().contains("must be positive"),
                "negativo deve ser recusado, veio: " + neg.getMessage());
    }
}
