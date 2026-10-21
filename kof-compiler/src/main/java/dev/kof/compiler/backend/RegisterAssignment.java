package dev.kof.compiler.backend;

import dev.kof.compiler.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Local→physical-register mapping (Phase D of
 * {@code docs/assembly-optimization-plan.md}; decision
 * {@code D-CODEGEN-REWRITE}, maintainer 13/10).
 *
 * <p>Resolves the {@link RegisterAllocator}'s ABSTRACT colours to real x86-64
 * registers: each slot's colour is mapped, within the slot's register
 * {@link X86_64RegisterFile.RegClass class}, to the physical register at that
 * index of the class's allocatable list. A spilled slot (colour {@code -1}) or a
 * colour beyond the class's register count has no physical register (it stays in
 * memory).
 *
 * <p>This is the DATA the codegen rewrite consumes to place locals in registers;
 * it binds nothing and changes no behavior — the same additive discipline as the
 * Phase A–C analyses and slice 1.
 */
public final class RegisterAssignment {

    /** A physical register (or none) per slot. */
    public record Assignment(Map<Integer, String> regOf, Set<Integer> inMemory) {

        /** The physical register for a slot, or {@code null} if it is in memory. */
        public String register(int slot) {
            return regOf.get(slot);
        }

        /** True when the slot is placed in a physical register. */
        public boolean isRegister(int slot) {
            return regOf.containsKey(slot);
        }

        /** True when the slot stays in memory (spilled, or unknown type). */
        public boolean isInMemory(int slot) {
            return inMemory.contains(slot);
        }
    }

    private RegisterAssignment() {
    }

    /**
     * Maps the allocation's colours to physical registers, using {@code slotTypes}
     * to know each slot's register class. Slots absent from {@code slotTypes} (no
     * known type), non-placeable types, and colours outside the class's register
     * count go to memory.
     */
    public static Assignment of(Map<Integer, Type> slotTypes,
                                RegisterAllocator.Allocation alloc) {
        Map<Integer, String> regOf = new TreeMap<>();
        Set<Integer> inMemory = new TreeSet<>();
        for (int slot : alloc.colorOf().keySet()) {
            int color = alloc.color(slot);
            Type type = slotTypes.get(slot);
            if (color < 0 || type == null || !placeable(type)) {
                inMemory.add(slot);
                continue;
            }
            List<String> regs = X86_64RegisterFile.allocatable(X86_64RegisterFile.classOf(type));
            if (color < regs.size()) {
                regOf.put(slot, regs.get(color));
            } else {
                inMemory.add(slot);   // colour beyond the class's register count
            }
        }
        return new Assignment(Map.copyOf(regOf), Set.copyOf(inMemory));
    }

    /**
     * Whether a local of this type may be REGISTER-PLACED. Only int/bool qualify.
     * A REFERENCE must stay in the FRAME: the x86-64 collector spills the
     * callee-saved registers before marking ({@code kof_gc_collect_now}), which
     * makes a ref in a register a root in the SIMPLE case — but MEASURED, methods
     * with try/catch/finally (a reference local + the exception path) CRASH
     * (SIGSEGV) when references are register-placed, so the spill is not a
     * sufficient guarantee across every path. Conservative: references stay in the
     * frame. long/float/double are double-width and stay in the frame too.
     */
    public static boolean placeable(Type t) {
        return t.equals(Type.PrimitiveType.INT) || t.equals(Type.PrimitiveType.BOOL);
    }
}
