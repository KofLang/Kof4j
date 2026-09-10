package dev.kof.compiler.runtime;

/**
 * Fatia de runtime x86_64 — kof.random (STDLIB S10a).
 *
 * <p>random_int = ALIAS de kof_sec_random_int (mesma primitiva de entropia,
 * getrandom(2); a diferença entre os dois é a face da API — random é a
 * não-críptográfica, security a de propósito criptográfico; contrato
 * leniente bound<=0 -> 0 já vive no callee). random_bool: 1 byte de
 * getrandom & 1 (mesmo shape do kof_sec_random_hex: falha do syscall -> 0,
 * nunca false fraco "por falta de fonte").
 */
public final class RuntimeRandom {

    private RuntimeRandom() {}

    public static void emit(StringBuilder sb) {
        sb.append("""


            # ── kof.random (STDLIB S10a) ──────────────────────────────────
            # kof_random_int(bound) -> [0,bound): alias de kof_sec_random_int
            # (mesmo getrandom; face da API diferente — plano S10)
            .globl kof_random_int
            .type kof_random_int, @function
            kof_random_int:
                jmp kof_sec_random_int

            # kof_random_bool() -> 0/1 (eax): getrandom(2) syscall 318, 1B
            .globl kof_random_bool
            .type kof_random_bool, @function
            kof_random_bool:
                subq $4, %rsp
                movq %rsp, %rdi
                movq $1, %rsi
                xorq %rdx, %rdx
                movq $318, %rax
                syscall
                testq %rax, %rax
                jle .Lv_rand_bool_fail
                movzbl (%rsp), %eax
                andl $1, %eax
                addq $4, %rsp
                ret
            .Lv_rand_bool_fail:
                addq $4, %rsp
                xorl %eax, %eax
                ret
            """);
    }
}
