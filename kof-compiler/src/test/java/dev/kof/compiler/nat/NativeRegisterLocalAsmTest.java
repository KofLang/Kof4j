package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D of the assembly-optimization plan ({@code D-CODEGEN-REWRITE}): the
 * x86-64 register-local asm fragments. Pure string emission — these tests pin the
 * exact instructions the prologue/epilogue/load/store will use; compiler behavior
 * is unchanged (nothing calls them yet).
 */
class NativeRegisterLocalAsmTest {

    private static final IntFunction<Integer> OFFSETS = i -> -128 - i * 8;

    @Test
    void prologueSavesCalleeSavedIntoFrameSlots() {
        StringBuilder sb = new StringBuilder();
        NativeRegisterLocalAsm.prologueSaveCalleeSaved(sb, List.of("rbx", "r12"), OFFSETS);
        assertEquals("    movq %rbx, -128(%rbp)\n    movq %r12, -136(%rbp)\n", sb.toString(),
                "each used callee-saved register is saved into its frame slot");
    }

    @Test
    void epilogueRestoresCalleeSavedFromFrameSlots() {
        StringBuilder sb = new StringBuilder();
        NativeRegisterLocalAsm.epilogueRestoreCalleeSaved(sb, List.of("rbx", "r12"), OFFSETS);
        assertEquals("    movq -128(%rbp), %rbx\n    movq -136(%rbp), %r12\n", sb.toString(),
                "each register is restored from its save slot before frame teardown");
    }

    @Test
    void loadPlacedRegisterPushesIt() {
        StringBuilder sb = new StringBuilder();
        NativeRegisterLocalAsm.loadPlacedRegister(sb, "rbx");
        assertEquals("    pushq %rbx\n", sb.toString());
    }

    @Test
    void storePlacedRegisterPopsIntoIt() {
        StringBuilder sb = new StringBuilder();
        NativeRegisterLocalAsm.storePlacedRegister(sb, "rbx");
        assertEquals("    popq %rbx\n", sb.toString());
    }

    @Test
    void saveAndRestoreAreSymmetric() {
        // Save then restore must name the same registers in the same order, so the
        // value read back is the one saved.
        StringBuilder save = new StringBuilder();
        StringBuilder restore = new StringBuilder();
        List<String> regs = List.of("rbx", "r13", "r15");
        NativeRegisterLocalAsm.prologueSaveCalleeSaved(save, regs, OFFSETS);
        NativeRegisterLocalAsm.epilogueRestoreCalleeSaved(restore, regs, OFFSETS);
        for (int i = 0; i < regs.size(); i++) {
            assertTrue(save.toString().contains("movq %" + regs.get(i) + ", " + OFFSETS.apply(i) + "(%rbp)"),
                    "save of " + regs.get(i));
            assertTrue(restore.toString().contains("movq " + OFFSETS.apply(i) + "(%rbp), %" + regs.get(i)),
                    "restore of " + regs.get(i));
        }
    }
}
