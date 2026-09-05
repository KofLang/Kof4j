package dev.kof.compiler;

/**
 * Emissão do ASM de map do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeMap {

    private RuntimeMap() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_map_new
            .type kof_map_new, @function
            kof_map_new:
                pushq %rbx
                movq $64, %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $100, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl $0, 16(%rbx)
                movl $16, 20(%rbx)
                movq $128, %rdi
                call kof_alloc
                movq %rax, 24(%rbx)
                movq $128, %rdi
                call kof_alloc
                movq %rax, 32(%rbx)
                movq %rbx, %rax
                popq %rbx
                ret

            # kof_map_put(rdi=map, rsi=key, rdx=val) -> valor anterior | 0
            .globl kof_map_put
            .type kof_map_put, @function
            kof_map_put:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rdx, %r13
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_map_find
                cmpq $-1, %rax
                je .Lkmp_insert
                movslq %eax, %rcx
                movq 32(%rbx), %rdx
                movq (%rdx,%rcx,8), %r14    # anterior
                movq %r13, (%rdx,%rcx,8)
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkmp_insert:
                movl 16(%rbx), %eax
                cmpl 20(%rbx), %eax
                jl .Lkmp_space
                # crescimento 2x: aloca novo e copia oldCap*8 bytes
                movl 20(%rbx), %ecx
                shll $3, %ecx               # oldCap*8
                movl %ecx, %r14d
                addl %ecx, %ecx
                movl %ecx, 20(%rbx)         # cap *= 2
                movslq %ecx, %rcx
                movq 24(%rbx), %rdi
                movq %rcx, %rsi
                call kof_copy_alloc
                movq %rax, 24(%rbx)
                movq 32(%rbx), %rdi
                movl %r14d, %esi            # copia só oldCap*8 (sem overread)
                movslq %esi, %rsi
                call kof_copy_alloc
                movq %rax, 32(%rbx)
            .Lkmp_space:
                movl 16(%rbx), %eax
                movslq %eax, %rcx
                movq 24(%rbx), %rdx
                movq %r12, (%rdx,%rcx,8)
                movq 32(%rbx), %rdx
                movq %r13, (%rdx,%rcx,8)
                addl $1, 16(%rbx)
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # auxiliar: kof_copy_alloc(rdi=src, rsi=nbytes) -> novo bloco com cópia
            kof_copy_alloc:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %r12, %rdi
                call kof_alloc
                movq %rax, %r13
                xorq %r14, %r14
            .Lkca_loop:
                cmpq %r12, %r14
                jge .Lkca_done
                movzbl (%rbx,%r14), %eax
                movb %al, (%r13,%r14)
                incq %r14
                jmp .Lkca_loop
            .Lkca_done:
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_map_get(rdi=map, rsi=key) -> val | 0
            .globl kof_map_get
            .type kof_map_get, @function
            kof_map_get:
                pushq %rbx
                movq %rdi, %rbx
                call kof_map_find
                cmpq $-1, %rax
                je .LKMG_miss
                movq 32(%rbx), %rdx
                movslq %eax, %rcx
                movq (%rdx,%rcx,8), %rax
                popq %rbx
                ret
            .LKMG_miss:
                xorl %eax, %eax
                popq %rbx
                ret

            # kof_map_remove(rdi=map, rsi=key) -> val removido | 0
            .globl kof_map_remove
            .type kof_map_remove, @function
            kof_map_remove:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx             # map
                movq %rsi, %r12             # key
                call kof_map_find
                cmpq $-1, %rax
                je .LKMR_miss
                movslq %eax, %r13           # idx
                movq 24(%rbx), %r14         # keys
                movq 32(%rbx), %r15         # vals
                movq (%r15,%r13,8), %r12    # valor removido
                movl 16(%rbx), %ecx
                decl %ecx                   # count-1
            .LKMR_shift:
                cmpl %ecx, %r13d
                jge .LKMR_last
                movq 8(%r14,%r13,8), %rax
                movq %rax, (%r14,%r13,8)
                movq 8(%r15,%r13,8), %rax
                movq %rax, (%r15,%r13,8)
                incq %r13
                jmp .LKMR_shift
            .LKMR_last:
                decl 16(%rbx)
                movq %r12, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKMR_miss:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_map_contains(rdi=map, rsi=key) -> 1/0
            .globl kof_map_contains
            .type kof_map_contains, @function
            kof_map_contains:
                call kof_map_find
                cmpq $-1, %rax
                setne %al
                movzbl %al, %eax
                ret

            .globl kof_map_size
            .type kof_map_size, @function
            kof_map_size:
                movl 16(%rdi), %eax
                ret

            .globl kof_map_is_empty
            .type kof_map_is_empty, @function
            kof_map_is_empty:
                cmpl $0, 16(%rdi)
                sete %al
                movzbl %al, %eax
                ret

            .globl kof_map_clear
            .type kof_map_clear, @function
            kof_map_clear:
                movl $0, 16(%rdi)
                ret

            .globl kof_map_keys
            .type kof_map_keys, @function
            kof_map_keys:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # map
                movq 24(%rbx), %r14         # array de chaves
                call kof_list_new
                movq %rax, %r12             # lista resultado
                xorq %r13, %r13
            .LKMK_loop:
                cmpl 16(%rbx), %r13d
                jge .LKMK_done
                movq (%r14,%r13,8), %rsi
                movq %r12, %rdi
                call kof_list_add
                incq %r13
                jmp .LKMK_loop
            .LKMK_done:
                movq %r12, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_map_values
            .type kof_map_values, @function
            kof_map_values:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # map
                movq 32(%rbx), %r14         # array de valores
                call kof_list_new
                movq %rax, %r12
                xorq %r13, %r13
            .LKMV_loop:
                cmpl 16(%rbx), %r13d
                jge .LKMV_done
                movq (%r14,%r13,8), %rsi
                movq %r12, %rdi
                call kof_list_add
                incq %r13
                jmp .LKMV_loop
            .LKMV_done:
                movq %r12, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── kof.collections: Set<T> nativo (P1) ─────────────────────
            # Set = List com checagem de contido no add (busca linear)
            # kof_set_new -> usa kof_list_new
            """);
    }
}