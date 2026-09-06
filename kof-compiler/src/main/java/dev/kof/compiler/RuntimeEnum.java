package dev.kof.compiler;

/**
 * Emissão do ASM de enum do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeEnum {

    private RuntimeEnum() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_enum_value_of
            .type kof_enum_value_of, @function
            kof_enum_value_of:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx              # list
                movq %rsi, %r12              # name
                testq %rbx, %rbx
                jz .Lenum_fail
                testq %r12, %r12
                jz .Lenum_fail
                xorq %r13, %r13              # i = 0
            .Lenum_loop:
                cmpl 16(%rbx), %r13d
                jge .Lenum_fail
                movq 24(%rbx), %rdi          # data array
                movq (%rdi,%r13,8), %rsi     # item
                testq %rsi, %rsi
                jz .Lenum_next
                movq %r12, %rdi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lenum_found
            .Lenum_next:
                incq %r13
                jmp .Lenum_loop
            .Lenum_found:
                movq 24(%rbx), %rax
                movq (%rax,%r13,8), %rax     # retorna o próprio item (internado)
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lenum_fail:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .text

            .section .text

            # ── kof.collections: Map<String,V> nativo (P1) ──────────────
            # Layout Map (64B): [0]=magic 100, [16]=count, [20]=cap,
            #   [24]=ptr keys (array KofString*), [32]=ptr vals (array ptr)

            # interno: kof_map_find(rdi=map, rsi=key) -> idx|-1
            kof_map_find:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # map
                movq %rsi, %r12             # key
                xorq %r13, %r13             # i = 0
            .Lkmf_loop:
                cmpl 16(%rbx), %r13d
                jge .Lkmf_miss
                movq 24(%rbx), %rax         # array de chaves
                movq (%rax,%r13,8), %rdi    # candidato
                testq %rdi, %rdi
                jz .Lkmf_next
                movq %rdi, %r14
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lkmf_hit
            .Lkmf_next:
                incq %r13
                jmp .Lkmf_loop
            .Lkmf_hit:
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkmf_miss:
                movq $-1, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }
}