package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 AArch64 ("use of immediate forms"): {@link NativeAarch64Helpers#isLogicalImm}
 * must match the REAL assembler's accepted set of bitmask immediates for
 * {@code and}/{@code orr}/{@code eor}. The cases below were MEASURED with
 * {@code aarch64-linux-gnu-as} (accept vs reject).
 */
class NativeAarch64LogicalImmTest {

    private static void accepted(long v) {
        assertTrue(NativeAarch64Helpers.isLogicalImm(v), v + " should be a valid logical immediate");
    }

    private static void rejected(long v) {
        assertFalse(NativeAarch64Helpers.isLogicalImm(v), v + " should be rejected by the assembler");
    }

    @Test
    void assemblerAcceptedValues() {
        accepted(1); accepted(2); accepted(3); accepted(6); accepted(7);
        accepted(15); accepted(31); accepted(63); accepted(127); accepted(255);
        accepted(511); accepted(1023); accepted(65520); accepted(65280); accepted(61440);
        accepted(-16); accepted(-256); accepted(-4); accepted(4294967295L);
        accepted(0xFF00L);
        accepted(0xAAAAAAAAAAAAAAAAL);
        accepted(0x00FF00FF00FF00FFL);
    }

    @Test
    void assemblerRejectedValues() {
        // 0 and -1 are NOT valid logical immediates (the assembler rejects them).
        rejected(0);
        rejected(-1L);
        // Multi-run / non-replicating patterns the assembler rejects.
        rejected(0xDEADBEEFL);
        rejected(0x12345678L);
        rejected(0xFF00FFL);
    }
}
