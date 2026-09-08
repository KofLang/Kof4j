package dev.kof.compiler.nat;

// FASE 3 (STDLIB S1): fatia 5 de RISCV_RUNTIME_ASM_B — kof.math Int-only.
// Convenção RISC-V LP64: a0/a1/a2 = args, a0 = retorno.
public final class NativeRiscvAsmRtB5 {

    static final String RISCV_RUNTIME_ASM_B_5 = """

            # ── kof.math (STDLIB S1) — Int-only ───────────────────────

            .globl kof_math_abs
            kof_math_abs:
                bltz a0, .Lv_math_abs_neg
                ret
            .Lv_math_abs_neg:
                neg  a0, a0
                ret

            # kof_math_sign(a0=v) -> 1/0/-1
            .globl kof_math_sign
            kof_math_sign:
                bltz a0, .Lv_math_sign_neg
                beqz a0, .Lv_math_sign_zero
                li   a0, 1
                ret
            .Lv_math_sign_neg:
                li   a0, -1
                ret
            .Lv_math_sign_zero:
                li   a0, 0
                ret

            # kof_math_clamp(a0=v, a1=lo, a2=hi) -> v
            .globl kof_math_clamp
            kof_math_clamp:
            # v < lo ? lo : v
            blt  a0, a1, .Lv_math_clamp_lo
                mv   t0, a0
                j    .Lv_math_clamp_hi
            .Lv_math_clamp_lo:
                mv   t0, a1
            .Lv_math_clamp_hi:
            # t0 > hi ? hi : t0
            bgt  t0, a2, .Lv_math_clamp_hi2
                mv   a0, t0
                ret
            .Lv_math_clamp_hi2:
                mv   a0, a2
                ret

            # kof_math_min(a0, a1) -> menor
            .globl kof_math_min
            kof_math_min:
                blt  a0, a1, .Lv_math_min_a0
                mv   a0, a1
            .Lv_math_min_a0:
                ret

            # kof_math_max(a0, a1) -> maior
            .globl kof_math_max
            kof_math_max:
                bgt  a0, a1, .Lv_math_max_a0
                mv   a0, a1
            .Lv_math_max_a0:
                ret

            # kof_math_isEven(a0=v) -> 1/0
            .globl kof_math_isEven
            kof_math_isEven:
                andi t0, a0, 1
                beqz t0, .Lv_math_even_1
            .Lv_math_even_0:
                li   a0, 0
                ret
            .Lv_math_even_1:
                li   a0, 1
                ret

            # kof_math_isOdd(a0=v) -> 1/0
            .globl kof_math_isOdd
            kof_math_isOdd:
                andi t0, a0, 1
                beqz t0, .Lv_math_odd_0
            .Lv_math_odd_1:
                li   a0, 1
                ret
            .Lv_math_odd_0:
                li   a0, 0
                ret

            # kof_math_isPositive(a0=v) -> 1/0
            .globl kof_math_isPositive
            kof_math_isPositive:
                bgtz a0, .Lv_math_pos_1
            .Lv_math_pos_0:
                li   a0, 0
                ret
            .Lv_math_pos_1:
                li   a0, 1
                ret

            # kof_math_isNegative(a0=v) -> 1/0
            .globl kof_math_isNegative
            kof_math_isNegative:
                bltz a0, .Lv_math_neg_1
            .Lv_math_neg_0:
                li   a0, 0
                ret
            .Lv_math_neg_1:
                li   a0, 1
                ret

            # kof_math_isZero(a0=v) -> 1/0
            .globl kof_math_isZero
            kof_math_isZero:
                beqz a0, .Lv_math_zero_1
            .Lv_math_zero_0:
                li   a0, 0
                ret
            .Lv_math_zero_1:
                li   a0, 1
                ret

            """;
}
