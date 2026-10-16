package dev.kof.compiler.backend;

import dev.kof.compiler.Type;

import java.util.List;

/**
 * x86-64 System V register file (Phase D of
 * {@code docs/development/assembly-optimization-plan.md}; decision
 * {@code D-CODEGEN-REWRITE}, maintainer 13/10).
 *
 * <p>The codegen rewrite maps Kof locals to PHYSICAL registers, so the
 * allocator's abstract colours must resolve to real registers of the correct
 * CLASS: integers/pointers live in the general-purpose (INTEGER) file, floats
 * and doubles in the SSE file. This class is the pure DATA the rewrite consumes —
 * the two register classes, their allocatable registers in preference order
 * (callee-saved first, since those survive a call), the argument registers, and
 * the return register — grounded in the x86-64 System V ABI.
 *
 * <p>It binds nothing and changes no behavior; it is the foundation the
 * register-mapped local model is built on (the same additive discipline as the
 * Phase A–C analyses).
 */
public final class X86_64RegisterFile {

    /** The two x86-64 register classes a Kof value maps to. */
    public enum RegClass { INTEGER, SSE }

    private X86_64RegisterFile() {
    }

    /** The class a Kof type's value lives in: floats/doubles → SSE, else INTEGER. */
    public static RegClass classOf(Type t) {
        if (t.equals(Type.PrimitiveType.FLOAT) || t.equals(Type.PrimitiveType.DOUBLE)) {
            return RegClass.SSE;
        }
        return RegClass.INTEGER;
    }

    /**
     * Allocatable registers in preference order: callee-saved first (a value kept
     * there survives a call without spilling), then caller-saved. rsp/rbp are
     * excluded (the frame pointer and stack pointer are reserved).
     */
    public static List<String> allocatable(RegClass c) {
        return c == RegClass.INTEGER ? INTEGER_ALLOCATABLE : SSE_ALLOCATABLE;
    }

    /** The registers a call passes its first arguments in, by class. */
    public static List<String> argumentRegisters(RegClass c) {
        return c == RegClass.INTEGER ? INTEGER_ARGUMENTS : SSE_ARGUMENTS;
    }

    /** The register a call returns its result in, by class. */
    public static String returnRegister(RegClass c) {
        return c == RegClass.INTEGER ? "rax" : "xmm0";
    }

    /** True when a register is preserved across a call (callee-saved). */
    public static boolean isCalleeSaved(RegClass c, String reg) {
        return (c == RegClass.INTEGER ? INTEGER_CALLEE_SAVED : SSE_CALLEE_SAVED).contains(reg);
    }

    /**
     * The number of callee-saved registers at the FRONT of the allocatable list
     * (colours {@code 0..count-1} are callee-saved). INTEGER has 5 (rbx, r12–r15);
     * SSE has 0 (SysV has no callee-saved SSE registers).
     */
    public static int calleeSavedCount(RegClass c) {
        return c == RegClass.INTEGER ? INTEGER_CALLEE_SAVED.size() : SSE_CALLEE_SAVED.size();
    }

    // SysV: callee-saved rbx, r12-r15 (preferred first), then caller-saved
    // rax, rcx, rdx, rsi, rdi, r8-r11. rsp/rbp are reserved.
    private static final List<String> INTEGER_ALLOCATABLE = List.of(
            "rbx", "r12", "r13", "r14", "r15",
            "rax", "rcx", "rdx", "rsi", "rdi", "r8", "r9", "r10", "r11");
    private static final List<String> INTEGER_CALLEE_SAVED = List.of(
            "rbx", "r12", "r13", "r14", "r15");

    // SysV SSE: xmm0-xmm15, all caller-saved (no callee-saved SSE registers).
    private static final List<String> SSE_ALLOCATABLE = List.of(
            "xmm0", "xmm1", "xmm2", "xmm3", "xmm4", "xmm5", "xmm6", "xmm7",
            "xmm8", "xmm9", "xmm10", "xmm11", "xmm12", "xmm13", "xmm14", "xmm15");
    private static final List<String> SSE_CALLEE_SAVED = List.of();

    private static final List<String> INTEGER_ARGUMENTS =
            List.of("rdi", "rsi", "rdx", "rcx", "r8", "r9");
    private static final List<String> SSE_ARGUMENTS =
            List.of("xmm0", "xmm1", "xmm2", "xmm3", "xmm4", "xmm5", "xmm6", "xmm7");
}
