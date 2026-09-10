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
            """);
    }
}
