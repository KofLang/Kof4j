package dev.kof.compiler;

import dev.kof.compiler.AbiLayout.Abi;
import dev.kof.compiler.backend.CallingConvention;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C of the assembly-optimization plan: the caller/callee-saved register
 * classification per target ABI. Pure ABI data (standard contracts) — these
 * tests pin the sets the register allocator will consult; compiler behavior is
 * unchanged.
 */
class CallingConventionTest {

    @Test
    void sysvX86_64HasTheStandardVolatileAndPreservedSets() {
        var c = CallingConvention.of(Abi.SYSV_X86_64);
        assertEquals(6, c.integerArgumentRegisters().size());
        assertTrue(c.isCallerSaved("rax"));
        assertTrue(c.isCallerSaved("r10"));
        assertTrue(c.isCallerSaved("rdi"));
        assertTrue(c.isCalleeSaved("rbx"));
        assertTrue(c.isCalleeSaved("r12"));
        assertTrue(c.isCalleeSaved("r15"));
        assertFalse(c.isCalleeSaved("rax"));
        assertFalse(c.isCallerSaved("rbx"));
    }

    @Test
    void aapcs64HasTheStandardVolatileAndPreservedSets() {
        var c = CallingConvention.of(Abi.AAPCS64);
        assertEquals(8, c.integerArgumentRegisters().size());
        assertTrue(c.isCallerSaved("x0"));
        assertTrue(c.isCallerSaved("x18"));
        assertTrue(c.isCalleeSaved("x19"));
        assertTrue(c.isCalleeSaved("x30"), "the link register is preserved by the callee");
        assertFalse(c.isCalleeSaved("x0"));
        assertFalse(c.isCallerSaved("x19"));
    }

    @Test
    void riscv64HasTheStandardVolatileAndPreservedSets() {
        var c = CallingConvention.of(Abi.RISCV64);
        assertEquals(8, c.integerArgumentRegisters().size());
        assertTrue(c.isCallerSaved("a0"));
        assertTrue(c.isCallerSaved("t6"));
        assertTrue(c.isCalleeSaved("s0"));
        assertTrue(c.isCalleeSaved("s11"));
        assertFalse(c.isCalleeSaved("a0"));
        assertFalse(c.isCallerSaved("s0"));
    }

    @Test
    void callerAndCalleeSavedSetsAreDisjointOnEveryAbi() {
        for (Abi abi : Abi.values()) {
            var c = CallingConvention.of(abi);
            for (String r : c.callerSaved()) {
                assertFalse(c.calleeSaved().contains(r),
                        abi + ": " + r + " cannot be both caller- and callee-saved");
            }
        }
    }

    @Test
    void argumentRegistersAreCallerSaved() {
        for (Abi abi : Abi.values()) {
            var c = CallingConvention.of(abi);
            for (String r : c.integerArgumentRegisters()) {
                assertTrue(c.isCallerSaved(r),
                        abi + ": argument register " + r + " is volatile across a call");
            }
        }
    }
}
