package dev.kof.compiler.nat;

import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.Type;
import dev.kof.compiler.backend.RegisterPlanner;

import java.util.List;

/**
 * Register-mapped local support for the native x86-64 emitter (Phase D of
 * {@code docs/development/assembly-optimization-plan.md}; decision
 * {@code D-CODEGEN-REWRITE}).
 *
 * <p>Holds the {@link RegisterPlanner.Placement} for ONE method (or is absent when
 * the method is not eligible or the feature is off) and emits the register-local
 * prologue/epilogue/load/store fragments. Extracted from {@code
 * NativeMethodEmitter} to keep that class under the 500-line gate.
 *
 * <p>Enabled by the {@code kof.native.register.locals} system property (default
 * OFF); eligible methods are int/bool-only ({@link
 * RegisterPlanner#eligibleForRegisterLocals}).
 */
final class NativeRegisterLocalSupport {

    /** Callee-saved registers only (rbx, r12–r15): the emitter's scratch set
     *  (rax/rcx/rdx/rsi/rdi/r8–r11) must stay free. */
    static final int INT_REGISTER_BUDGET = 5;

    private final RegisterPlanner.Placement placement;
    private final List<String> calleeSavedUsed;
    private final int saveSlotBaseBytes;

    private NativeRegisterLocalSupport(RegisterPlanner.Placement placement,
                                       List<String> calleeSavedUsed, int saveSlotBaseBytes) {
        this.placement = placement;
        this.calleeSavedUsed = calleeSavedUsed;
        this.saveSlotBaseBytes = saveSlotBaseBytes;
    }

    /**
     * The feature gate — ON by default (D-CODEGEN-REWRITE: the register-mapped
     * local model is now the native x86-64 default). Set
     * {@code -Dkof.native.register.locals=false} to fall back to the frame-slot
     * path (an escape hatch, not the normal path).
     */
    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty("kof.native.register.locals", "true"));
    }

    /**
     * Builds the support for a method, or returns {@code null} when the feature is
     * off, the method is not eligible, or the placement is empty (nothing to do).
     */
    static NativeRegisterLocalSupport forMethod(IRMethod method, int localsBytes) {
        if (!enabled() || !RegisterPlanner.eligibleForRegisterLocals(method)) {
            return null;
        }
        RegisterPlanner.Placement p = RegisterPlanner.plan(method, INT_REGISTER_BUDGET);
        if (p.calleeSavedUsed().isEmpty() && !p.isRegister(0)) {
            return null;
        }
        return new NativeRegisterLocalSupport(p, p.calleeSavedUsed(), localsBytes);
    }

    /** The extra frame bytes the save slots need (right after the locals). */
    int saveBytes() {
        return calleeSavedUsed.size() * 8;
    }

    /** The register a slot is placed in, or {@code null} if it is in the frame. */
    String register(int slot) {
        return placement.register(slot);
    }

    /**
     * Emit the prologue: save the used callee-saved registers into the extended
     * frame, then copy each register-placed PARAM from its staged frame slot to its
     * register. Non-param slots take their value from a store before any read, so
     * they need no prologue copy.
     */
    void emitPrologue(StringBuilder sb, List<IRLocalVariable> sortedLocals, int paramSlotMax) {
        NativeRegisterLocalAsm.prologueSaveCalleeSaved(sb, calleeSavedUsed,
                i -> -(saveSlotBaseBytes + (i + 1) * 8));
        for (IRLocalVariable lv : sortedLocals) {
            // Copy every register-placed PARAM slot (0..paramSlotMax-1). Slot 0 is
            // the first param for a static method, or `this` for an instance method
            // (a reference, which is never register-placed, so it is skipped by the
            // null check). Non-param slots take their value from a store before any
            // read, so they need no prologue copy.
            if (lv.index() >= paramSlotMax) continue;
            String reg = placement.register(lv.index());
            if (reg != null) {
                NativeRegisterLocalAsm.copySlotToRegister(sb, lv.index(), reg);
            }
        }
    }

    /** Restore the used callee-saved registers at a return, before frame teardown. */
    void emitReturnRestore(StringBuilder sb) {
        if (!calleeSavedUsed.isEmpty()) {
            NativeRegisterLocalAsm.epilogueRestoreCalleeSaved(sb, calleeSavedUsed,
                    i -> -(saveSlotBaseBytes + (i + 1) * 8));
        }
    }

    /** Load a register-placed local (push its register onto the value stack). */
    void emitLoad(StringBuilder sb, String reg) {
        NativeRegisterLocalAsm.loadPlacedRegister(sb, reg);
    }

    /** Store into a register-placed local (pop the value stack into its register). */
    void emitStore(StringBuilder sb, String reg) {
        NativeRegisterLocalAsm.storePlacedRegister(sb, reg);
    }
}
