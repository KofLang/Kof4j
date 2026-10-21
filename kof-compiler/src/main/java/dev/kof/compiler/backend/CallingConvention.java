package dev.kof.compiler.backend;

import dev.kof.compiler.AbiLayout.Abi;

import java.util.List;
import java.util.Set;

/**
 * Caller/callee-saved register classification per target ABI (Phase C of
 * {@code docs/assembly-optimization-plan.md} §5: "caller/callee-saved
 * awareness").
 *
 * <p>The register allocator must know which registers a callee may clobber
 * across a call. Caller-saved (volatile) registers may be overwritten by a call,
 * so a value live across a call cannot stay in one unless it is spilled or moved
 * to a callee-saved register. This class exposes that classification as pure
 * DATA for the three Native ABIs the compiler targets, keyed on the existing
 * {@link Abi} enum.
 *
 * <p>The sets are the standard ABI definitions (x86-64 System V, AAPCS64,
 * RISC-V LP64D) — public, stable contracts, not implementation details. The
 * argument registers are the subset a call passes its first integer arguments
 * in. This class binds nothing and changes no behavior; the allocator's
 * call-awareness (spill a caller-saved value live across a call) is the follow-on
 * slice.
 */
public final class CallingConvention {

    /** The integer argument registers, caller-saved registers, and callee-saved
     *  registers of one ABI. */
    public record Convention(List<String> integerArgumentRegisters,
                             Set<String> callerSaved, Set<String> calleeSaved) {

        /** True when {@code register} may be clobbered by a call. */
        public boolean isCallerSaved(String register) {
            return callerSaved.contains(register);
        }

        /** True when {@code register} is preserved across a call. */
        public boolean isCalleeSaved(String register) {
            return calleeSaved.contains(register);
        }
    }

    private CallingConvention() {
    }

    public static Convention of(Abi abi) {
        return switch (abi) {
            case SYSV_X86_64 -> SYSV_X86_64;
            case AAPCS64 -> AAPCS64;
            case RISCV64 -> RISCV64;
        };
    }

    // x86-64 System V: rdi,rsi,rdx,rcx,r8,r9 carry the first integer args;
    // rax,rcx,rdx,rsi,rdi,r8-r11 are volatile; rbx,rbp,r12-r15 are preserved.
    private static final Convention SYSV_X86_64 = new Convention(
            List.of("rdi", "rsi", "rdx", "rcx", "r8", "r9"),
            Set.of("rax", "rcx", "rdx", "rsi", "rdi", "r8", "r9", "r10", "r11"),
            Set.of("rbx", "rbp", "r12", "r13", "r14", "r15"));

    // AAPCS64: x0-x7 carry the first integer args; x0-x18 are volatile;
    // x19-x28 plus the frame pointer (x29) and link register (x30) are preserved.
    private static final Convention AAPCS64 = new Convention(
            List.of("x0", "x1", "x2", "x3", "x4", "x5", "x6", "x7"),
            Set.of("x0", "x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8", "x9",
                    "x10", "x11", "x12", "x13", "x14", "x15", "x16", "x17", "x18"),
            Set.of("x19", "x20", "x21", "x22", "x23", "x24", "x25", "x26", "x27",
                    "x28", "x29", "x30"));

    // RISC-V LP64D: a0-a7 carry the first integer args (and are volatile);
    // t0-t6 are volatile temporaries; s0-s11 are preserved.
    private static final Convention RISCV64 = new Convention(
            List.of("a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"),
            Set.of("t0", "t1", "t2", "t3", "t4", "t5", "t6",
                    "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"),
            Set.of("s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7",
                    "s8", "s9", "s10", "s11"));
}
