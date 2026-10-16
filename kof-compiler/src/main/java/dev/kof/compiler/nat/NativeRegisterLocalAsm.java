package dev.kof.compiler.nat;

import java.util.List;

/**
 * x86-64 assembly fragments for the register-mapped local model (Phase D of
 * {@code docs/development/assembly-optimization-plan.md}; decision
 * {@code D-CODEGEN-REWRITE}).
 *
 * <p>Emits the exact instruction text the native backend's prologue, epilogue and
 * local load/store use when a method's int/bool locals live in physical
 * registers instead of frame slots:
 * <ul>
 *   <li>{@link #prologueSaveCalleeSaved} — push the callee-saved registers the
 *       method uses, saving them into dedicated frame slots so they survive the
 *       {@code movq %rbp, %rsp} the epilogue performs;</li>
 *   <li>{@link #epilogueRestoreCalleeSaved} — restore them from those slots at
 *       every return, before the frame is torn down;</li>
 *   <li>{@link #loadPlacedRegister} / {@link #storePlacedRegister} — a
 *       register-placed local's load/store as a {@code push}/{@code pop} (the
 *       backend's value stack holds operands in memory, so a value in a register
 *       must be pushed/popped like any other operand).</li>
 * </ul>
 *
 * <p>This is a pure string emitter (no state); the caller passes the registers and
 * the frame offsets. ADDITIVE — nothing calls it yet; it is the fragment layer the
 * emission switch-over builds on.
 */
final class NativeRegisterLocalAsm {

    private NativeRegisterLocalAsm() {
    }

    /**
     * Save each used callee-saved register into the frame at {@code slotOffset}
     * (a byte offset from {@code %rbp}, e.g. {@code -128}). <b>The caller MUST
     * extend the frame by {@code regs.size() * 8} bytes and place the save slots
     * within that extended frame</b> (right after the locals, at
     * {@code -(localsBytes + (i+1)*8)}); an offset beyond the allocated frame
     * would write below {@code %rsp} and corrupt the stack.
     */
    static void prologueSaveCalleeSaved(StringBuilder sb, List<String> regs,
                                        java.util.function.IntFunction<Integer> slotOffset) {
        int i = 0;
        for (String reg : regs) {
            sb.append("    movq ").append(at(reg)).append(", ")
                    .append(slotOffset.apply(i)).append("(%rbp)\n");
            i++;
        }
    }

    /** Restore each used callee-saved register from its save slot. */
    static void epilogueRestoreCalleeSaved(StringBuilder sb, List<String> regs,
                                           java.util.function.IntFunction<Integer> slotOffset) {
        int i = 0;
        for (String reg : regs) {
            sb.append("    movq ").append(slotOffset.apply(i)).append("(%rbp), ")
                    .append(at(reg)).append("\n");
            i++;
        }
    }

    /** Load a register-placed local: push its register onto the value stack. */
    static void loadPlacedRegister(StringBuilder sb, String reg) {
        sb.append("    pushq ").append(at(reg)).append("\n");
    }

    /** Store into a register-placed local: pop the value stack into its register. */
    static void storePlacedRegister(StringBuilder sb, String reg) {
        sb.append("    popq ").append(at(reg)).append("\n");
    }

    /** AT&T-syntax register reference: prefix the bare name (e.g. {@code rbx}) with
     *  {@code %}. {@code X86_64RegisterFile} stores bare names. */
    private static String at(String reg) {
        return reg.startsWith("%") ? reg : "%" + reg;
    }

    /**
     * Load a local slot: from its register when {@code reg} is non-null, else from
     * its frame slot (the existing frame-slot path).
     */
    static void loadLocal(StringBuilder sb, int slot, String reg) {
        if (reg != null) {
            loadPlacedRegister(sb, reg);
        } else {
            sb.append("    movq -").append((slot + 1) * 8).append("(%rbp), %rax\n");
            sb.append("    pushq %rax\n");
        }
    }

    /** Store a local slot: into its register when {@code reg} is non-null, else
     *  into its frame slot (the existing frame-slot path). */
    static void storeLocal(StringBuilder sb, int slot, String reg) {
        if (reg != null) {
            storePlacedRegister(sb, reg);
        } else {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, -").append((slot + 1) * 8).append("(%rbp)\n");
        }
    }

    /** Copy a param's staged frame slot into its assigned register (prologue). */
    static void copySlotToRegister(StringBuilder sb, int slot, String reg) {
        sb.append("    movq -").append((slot + 1) * 8).append("(%rbp), ")
                .append(at(reg)).append("\n");
    }
}
