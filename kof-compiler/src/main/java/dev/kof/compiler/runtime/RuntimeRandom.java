package dev.kof.compiler.runtime;

/**
 * Fatia de runtime x86_64 — kof.random (STDLIB S10/S10a/S10b).
 * Entropia SEMPRE getrandom(2) (syscall 318 — mesma primitiva da crypto
 * lane): NUNCA caseira (R11).
 *
 * <p>random_int/hex = alias (tail-jmp) de kof_sec_random_int/hex (mesma
 * fonte de entropia; a face da API é que muda — random é a não-
 * criptográfica, security a de propósito criptográfico; contrato leniente
 * bound<=0 -> 0 já vive no callee). random_bool/boolean: 1 byte de
 * getrandom & 1 (falha do syscall -> 0, nunca false fraco "por falta de
 * fonte"). random_double (S10 main): 8 bytes aleatórios, 53 bits de
 * mantissa (>>11) / 2^53; xmm0 no retorno; 0.0 em falha do SO.
 * random_string (S10b): n chars uniformes do alfabeto (ASCII).
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

            # kof_random_boolean = alias de nome (face S10) — MESMA máquina.
            .globl kof_random_boolean
            .type kof_random_boolean, @function
            kof_random_boolean:
                jmp kof_random_bool

            # kof_random_hex(n) -> tail-jmp p/ kof_sec_random_hex (crypto lane)
            .globl kof_random_hex
            .type kof_random_hex, @function
            kof_random_hex:
                jmp kof_sec_random_hex

            # kof_random_double() -> Double em [0,1) via xmm0 (53-bit mantissa)
            .globl kof_random_double
            .type kof_random_double, @function
            kof_random_double:
                pushq %rbx
                subq $16, %rsp                  # buf 8 bytes (16-align)
                movq %rsp, %rdi
                movq $8, %rsi
                xorq %rdx, %rdx
                movq $318, %rax                 # getrandom
                syscall
                testq %rax, %rax
                js .Lrnd_d_fail
                movq (%rsp), %rax               # 64 bits aleatorios
                shrq $11, %rax                  # 53 bits (>= 0, < 2^53)
                cvtsi2sdq %rax, %xmm0           # v como double
                movsd .Lrnd_two53(%rip), %xmm1  # 2^53
                divsd %xmm1, %xmm0              # [0,1)
                addq $16, %rsp
                popq %rbx
                ret
            .Lrnd_d_fail:
                xorq %rax, %rax
                cvtsi2sdq %rax, %xmm0           # 0.0 (falha do SO)
                addq $16, %rsp
                popq %rbx
                ret

            # kof_random_string(edi=n, rsi=alphabet) -> String (S10b)
            # n<=0 / alphabet null/vazio -> "" (leniente, mesmo repeat).
            # cada char = alphabet[kof_sec_random_int(alen)] (ASCII — layout
            # de String nativa; paridade UTF-16 é JVM/JS, como todo o S2).
            .globl kof_random_string
            .type kof_random_string, @function
            kof_random_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movl %edi, %ebx              # n
                movq %rsi, %r12              # alphabet
                testl %ebx, %ebx
                jle .Lv_rand_str_emp
                testq %r12, %r12
                jz .Lv_rand_str_emp
                movl 16(%r12), %r13d         # alen
                testl %r13d, %r13d
                jle .Lv_rand_str_emp
                leal 25(%rbx), %edi
                call kof_alloc
                movq %rax, %r14              # novo
                movl $1, (%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %ebx, 16(%r14)
                movl $0, 20(%r14)
                xorl %r15d, %r15d            # i
            .Lv_rand_str_loop:
                cmpl %ebx, %r15d
                jge .Lv_rand_str_term
                movl %r13d, %edi
                call kof_sec_random_int      # eax = idx (movl zera rax)
                movzbl 24(%r12,%rax), %edx
                movb %dl, 24(%r14,%r15)
                incq %r15
                jmp .Lv_rand_str_loop
            .Lv_rand_str_term:
                movb $0, 24(%r14,%rbx)
                movq %r14, %rax
                jmp .Lv_rand_str_done
            .Lv_rand_str_emp:
                movl $40, %edi
                call kof_alloc
                movq %rax, %r14
                movl $1, (%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl $0, 16(%r14)
                movl $0, 20(%r14)
                movb $0, 24(%r14)
                movq %r14, %rax
            .Lv_rand_str_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .rodata
            .balign 8
            .Lrnd_two53:
                .quad 0x4340000000000000        # 9007199254740992.0 = 2^53
            .section .text
            """);
    }
}
