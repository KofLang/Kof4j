package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §19 (debuggability) of {@code docs/assembly-optimization-plan.md}:
 * "Optimizations may remove variables or merge operations, but that should be
 * represented correctly in future DWARF information."
 *
 * <p>With the register-locals model a local may live in a register for the whole
 * function, so describing it as frame-relative would send a debugger to a slot
 * that is never written. A register-placed local must be described by
 * {@code DW_OP_regN}; a framed one keeps {@code DW_OP_fbreg}.
 */
class NativeDwarfLocalLocationTest {

    @Test
    void aFramedLocalKeepsFbreg() {
        byte[] loc = NativeDwarf.locationBytes(new NativeDwarf.Local("s", -16, "Int"));
        assertEquals(0x91, loc[0] & 0xFF);
        assertTrue(loc.length > 1);
    }

    @Test
    void aRegisterPlacedLocalIsDescribedByItsRegister() {
        // rbx is DWARF register 3 → DW_OP_reg3 = 0x53. A frame offset would be a lie.
        byte[] loc = NativeDwarf.locationBytes(new NativeDwarf.Local("i", -24, "Int", 3));
        assertArrayEquals(new byte[]{(byte) 0x53}, loc);
    }

    @Test
    void everyCalleeSavedRegisterMapsToItsSysvNumber() {
        assertEquals(3, NativeDwarf.dwarfRegNumber("rbx"));
        assertEquals(12, NativeDwarf.dwarfRegNumber("r12"));
        assertEquals(13, NativeDwarf.dwarfRegNumber("r13"));
        assertEquals(14, NativeDwarf.dwarfRegNumber("r14"));
        assertEquals(15, NativeDwarf.dwarfRegNumber("r15"));
        assertEquals(0, NativeDwarf.dwarfRegNumber("rax"));
    }

    @Test
    void anUnknownRegisterNameHasNoDwarfNumber() {
        assertNull(NativeDwarf.dwarfRegNumber("xmm0"));
        assertNull(NativeDwarf.dwarfRegNumber("nonsense"));
    }

    @Test
    void anOutOfRangeRegisterIsRefusedRatherThanEncodedWrongly() {
        assertThrows(IllegalStateException.class,
                () -> NativeDwarf.locationBytes(new NativeDwarf.Local("x", 0, "Int", 32)));
    }
}
