package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de kof.math (STDLIB S1) do runtime nativo x86_64.
 * Int-only (paridade byte-idêntica com JVM/JS/interpreter):
 * clamp/abs/sign/min/max + predicados paridade/sinal/zero.
 * SysV: edi/esi/edx = args, eax = retorno.
 */
public final class RuntimeMath {

    private RuntimeMath() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_math_abs
            .type kof_math_abs, @function
            kof_math_abs:
                testl %edi, %edi
                jge .Lv_math_abs_pos
                negl %edi
            .Lv_math_abs_pos:
                movl %edi, %eax
                ret

            # kof_math_sign(edi=v) -> 1/0/-1
            .globl kof_math_sign
            .type kof_math_sign, @function
            kof_math_sign:
                testl %edi, %edi
                jg .Lv_math_sign_pos
                jge .Lv_math_sign_zero
                movl $-1, %eax
                ret
            .Lv_math_sign_zero:
                xorl %eax, %eax
                ret
            .Lv_math_sign_pos:
                movl $1, %eax
                ret

            # kof_math_clamp(edi=v, esi=lo, edx=hi) -> v
            .globl kof_math_clamp
            .type kof_math_clamp, @function
            kof_math_clamp:
                cmpl %esi, %edi
                jge .Lv_math_clamp_mid
                movl %esi, %eax
                jmp .Lv_math_clamp_done
            .Lv_math_clamp_mid:
                movl %edi, %eax
            .Lv_math_clamp_done:
                cmpl %edx, %eax
                jle .Lv_math_clamp_ret
                movl %edx, %eax
            .Lv_math_clamp_ret:
                ret

            # kof_math_min(edi=a, esi=b) -> menor
            .globl kof_math_min
            .type kof_math_min, @function
            kof_math_min:
                cmpl %esi, %edi
                jle .Lv_math_min_a
                movl %esi, %edi
            .Lv_math_min_a:
                movl %edi, %eax
                ret

            # kof_math_max(edi=a, esi=b) -> maior
            .globl kof_math_max
            .type kof_math_max, @function
            kof_math_max:
                cmpl %esi, %edi
                jge .Lv_math_max_a
                movl %esi, %edi
            .Lv_math_max_a:
                movl %edi, %eax
                ret

            # kof_math_isEven(edi=v) -> 1/0
            .globl kof_math_isEven
            .type kof_math_isEven, @function
            kof_math_isEven:
                testl $1, %edi
                jnz .Lv_math_even_false
                movl $1, %eax
                ret
            .Lv_math_even_false:
                xorl %eax, %eax
                ret

            # kof_math_isOdd(edi=v) -> 1/0
            .globl kof_math_isOdd
            .type kof_math_isOdd, @function
            kof_math_isOdd:
                testl $1, %edi
                jz .Lv_math_odd_false
                movl $1, %eax
                ret
            .Lv_math_odd_false:
                xorl %eax, %eax
                ret

            # kof_math_isPositive(edi=v) -> 1/0
            .globl kof_math_isPositive
            .type kof_math_isPositive, @function
            kof_math_isPositive:
                cmpl $0, %edi
                jg .Lv_math_pos_true
                xorl %eax, %eax
                ret
            .Lv_math_pos_true:
                movl $1, %eax
                ret

            # kof_math_isNegative(edi=v) -> 1/0
            .globl kof_math_isNegative
            .type kof_math_isNegative, @function
            kof_math_isNegative:
                cmpl $0, %edi
                jge .Lv_math_neg_false
                movl $1, %eax
                ret
            .Lv_math_neg_false:
                xorl %eax, %eax
                ret

            # kof_math_isZero(edi=v) -> 1/0
            .globl kof_math_isZero
            .type kof_math_isZero, @function
            kof_math_isZero:
                testl %edi, %edi
                jnz .Lv_math_zero_false
                movl $1, %eax
                ret
            .Lv_math_zero_false:
                xorl %eax, %eax
                ret

            # S1b: kof_math_sqrt(xmm0=v) -> xmm0 (FLT fechado 31/08 via XMM;
            # riscv64/aarch64 = MATH001). NaN em <0 — paridade Math.sqrt.
            .globl kof_math_sqrt
            .type kof_math_sqrt, @function
            kof_math_sqrt:
                sqrtsd %xmm0, %xmm0
                ret
        """);
    }
}
