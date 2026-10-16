package dev.kof.compiler;

import dev.kof.compiler.AbiLayout.Abi;
import dev.kof.compiler.backend.CallingConvention;
import dev.kof.compiler.backend.X86_64RegisterFile;
import dev.kof.compiler.backend.X86_64RegisterFile.RegClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D of the assembly-optimization plan ({@code D-CODEGEN-REWRITE}): the
 * x86-64 System V register-file model. Pure ABI data — these tests pin the
 * classes, allocatable registers, argument/return registers and callee-saved
 * sets the codegen rewrite consumes; compiler behavior is unchanged.
 */
class X86_64RegisterFileTest {

    @Test
    void floatsAndDoublesUseSseOthersUseInteger() {
        assertEquals(RegClass.SSE, X86_64RegisterFile.classOf(Type.PrimitiveType.DOUBLE));
        assertEquals(RegClass.SSE, X86_64RegisterFile.classOf(Type.PrimitiveType.FLOAT));
        assertEquals(RegClass.INTEGER, X86_64RegisterFile.classOf(Type.PrimitiveType.INT));
        assertEquals(RegClass.INTEGER, X86_64RegisterFile.classOf(Type.PrimitiveType.LONG));
        assertEquals(RegClass.INTEGER, X86_64RegisterFile.classOf(Type.PrimitiveType.BOOL));
    }

    @Test
    void integerArgumentsFollowSysvOrder() {
        assertEquals("rdi", X86_64RegisterFile.argumentRegisters(RegClass.INTEGER).get(0));
        assertEquals("rsi", X86_64RegisterFile.argumentRegisters(RegClass.INTEGER).get(1));
        assertEquals("rdx", X86_64RegisterFile.argumentRegisters(RegClass.INTEGER).get(2));
        assertEquals(6, X86_64RegisterFile.argumentRegisters(RegClass.INTEGER).size());
    }

    @Test
    void sseArgumentsFollowSysvOrder() {
        assertEquals("xmm0", X86_64RegisterFile.argumentRegisters(RegClass.SSE).get(0));
        assertEquals(8, X86_64RegisterFile.argumentRegisters(RegClass.SSE).size());
    }

    @Test
    void returnRegistersAreRaxAndXmm0() {
        assertEquals("rax", X86_64RegisterFile.returnRegister(RegClass.INTEGER));
        assertEquals("xmm0", X86_64RegisterFile.returnRegister(RegClass.SSE));
    }

    @Test
    void allocatableIntegerRegistersPreferCalleeSavedAndExcludeReserved() {
        var alloc = X86_64RegisterFile.allocatable(RegClass.INTEGER);
        assertTrue(X86_64RegisterFile.isCalleeSaved(RegClass.INTEGER, alloc.get(0)),
                "the first allocatable register is callee-saved (preferred)");
        assertFalse(alloc.contains("rsp"), "rsp is reserved");
        assertFalse(alloc.contains("rbp"), "rbp is reserved (frame pointer)");
        assertTrue(alloc.contains("r12"));
    }

    @Test
    void sseHasNoCalleeSavedRegistersOnSysv() {
        for (String r : X86_64RegisterFile.allocatable(RegClass.SSE)) {
            assertFalse(X86_64RegisterFile.isCalleeSaved(RegClass.SSE, r),
                    "SysV x86-64 has no callee-saved SSE registers");
        }
        assertEquals(0, X86_64RegisterFile.calleeSavedCount(RegClass.SSE));
    }

    @Test
    void calleeSavedRegistersAreAtTheFrontOfTheAllocatableList() {
        int k = X86_64RegisterFile.calleeSavedCount(RegClass.INTEGER);
        assertEquals(5, k, "rbx + r12-r15");
        var alloc = X86_64RegisterFile.allocatable(RegClass.INTEGER);
        for (int i = 0; i < k; i++) {
            assertTrue(X86_64RegisterFile.isCalleeSaved(RegClass.INTEGER, alloc.get(i)),
                    "colour " + i + " (" + alloc.get(i) + ") must be callee-saved");
        }
        assertFalse(X86_64RegisterFile.isCalleeSaved(RegClass.INTEGER, alloc.get(k)),
                "colour k is the first caller-saved register");
    }

    @Test
    void argumentRegistersAreAllocatable() {
        for (RegClass c : RegClass.values()) {
            for (String r : X86_64RegisterFile.argumentRegisters(c)) {
                assertTrue(X86_64RegisterFile.allocatable(c).contains(r),
                        c + ": argument register " + r + " must be allocatable");
            }
        }
    }

    @Test
    void integerRegistersAgreeWithTheSysvCallingConvention() {
        // The register file must stay consistent with CallingConvention.SYSV_X86_64.
        var cc = CallingConvention.of(Abi.SYSV_X86_64);
        assertEquals(cc.integerArgumentRegisters(),
                X86_64RegisterFile.argumentRegisters(RegClass.INTEGER),
                "argument registers match the SysV convention");
        for (String r : X86_64RegisterFile.allocatable(RegClass.INTEGER)) {
            assertTrue(cc.isCallerSaved(r) || cc.isCalleeSaved(r),
                    "allocatable register " + r + " must be classified by the convention");
            assertFalse(r.equals("rsp") || r.equals("rbp"),
                    "rsp/rbp are never allocatable");
        }
        for (String r : X86_64RegisterFile.allocatable(RegClass.INTEGER)) {
            if (cc.isCalleeSaved(r)) {
                assertTrue(X86_64RegisterFile.isCalleeSaved(RegClass.INTEGER, r),
                        "callee-saved agreement for " + r);
            }
        }
    }
}
