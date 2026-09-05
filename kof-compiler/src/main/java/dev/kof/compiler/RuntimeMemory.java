package dev.kof.compiler;

/**
 * Emissão do ASM de alocação/liberação (kof_alloc/kof_free/kof_init_object/kof_memstats)
 * do runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeMemory {

    private RuntimeMemory() {}

    static void emitInitObject(StringBuilder sb) {
        sb.append("""
            .globl kof_init_object
            .type kof_init_object, @function
            kof_init_object:
                movl %esi, 0(%rdi)
                movl $0, 4(%rdi)
                movq %rdx, 8(%rdi)
                ret
            """);
    }

    static void emitAlloc(StringBuilder sb) {
        sb.append("""
            .section .bss
            .balign 8
            kof_alloc_lock: .space 40          # pthread_mutex_t (zero-init = default)
            kof_free_head: .quad 0
            .globl kof_gc_head
            .balign 8
            kof_gc_head: .quad 0
            .balign 8
            kof_heap_low: .quad 0
            .balign 8
            kof_heap_high: .quad 0
            .balign 8
            kof_main_tid: .quad 0              # tid do main thread p/ o GC (conservador lê a stack)
            .section .data
            .Lstr_alloc_fail: .asciz "Runtime error: out of memory"
            .section .rodata
            .Lkof_alloc_dbg: .ascii "."
            .section .text
            .globl kof_alloc
            .type kof_alloc, @function
            kof_alloc:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $24, %rsp
                movq %rdi, (%rsp)                # tamanho solicitado
                leaq kof_alloc_lock(%rip), %rsi  # &lock
                xorl %eax, %eax                  # esperado 0
            .Lkof_alloc_lock_try:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)        # 0->1 atomically?
                testl %eax, %eax
                jz .Lkof_alloc_locked
                # ocupado: futex wait
                movl $1, %edx                    # val=1
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                movq $202, %rax                  # SYS_futex WAIT
                syscall
                jmp .Lkof_alloc_lock_try
            .Lkof_alloc_locked:
                movq $0, 8(%rsp)                 # flag: GC ainda nao tentou
                movq (%rsp), %r12
                addq $7, %r12
                andq $~7, %r12
                addq $32, %r12
                movq kof_free_head(%rip), %r13
                xorq %r14, %r14
                movq $1048576, %r11
            .Lkof_alloc_search:
                testq %r13, %r13
                je .Lkof_alloc_maybe_gc
                decq %r11
                je .Lkof_alloc_mmap
                movq 0(%r13), %r15
                cmpq %r12, %r15
                jb .Lkof_alloc_next
                cmpq $0, %r14
                je .Lkof_alloc_found_head
                movq 8(%r13), %r15
                movq %r15, 8(%r14)
                jmp .Lkof_alloc_found
            .Lkof_alloc_found_head:
                movq 8(%r13), %rax
                movq %rax, kof_free_head(%rip)
            .Lkof_alloc_found:
                movb $0, 24(%r13)
                movq %r13, %rax
                addq $32, %rax
                incq .Lkof_alloc_count(%rip)
                addq %r12, .Lkof_alloc_bytes(%rip)
                movq %rax, (%rsp)                # preserva retorno
                leaq kof_alloc_lock(%rip), %rdi
                movl $0, (%rdi)
                movl $1, %esi                    # FUTEX_WAKE, 1 waiter
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                syscall
                movq (%rsp), %rax
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_alloc_next:
                movq %r13, %r14
                movq 8(%r13), %r13
                jmp .Lkof_alloc_search
            .Lkof_alloc_maybe_gc:
                jmp .Lkof_alloc_mmap
                # GC auto-collect: mark+sweep real existe (kof_gc_collect_now),
                # mas auto-invogar DENTRO de kof_alloc é inseguro:
                # kof_alloc tem um ponteiro NAO ainda na stack (o ponteiro do
                # bloco livre) -- a mark conservadora nao o ve, o sweep o
                # enfileira na free list e o alloc o reusa DUPLO. O hang
                # documentado em status.md era exatamente isso. Fechar GC
                # completamente exige safe-points: (a) inserção de collect
                # antes de toda kof_alloc em loop, ou (b) geração de mapa de
                # raízes por frame. Fora do escopo aqui: o comportamento
                # correto atual é mmap (mais memória, sem corrupção).
            .Lkof_alloc_maybe_gc_skip:
                jmp .Lkof_alloc_mmap
            .Lkof_alloc_mmap:
                movq $0, %rdi
                movq %r12, %rsi
                movq $3, %rdx
                movq $0x22, %r10
                movq $-1, %r8
                movq $0, %r9
                movq $9, %rax
                syscall
                testq %rax, %rax
                js .Lkof_alloc_fail
                movq %r12, 0(%rax)
                movq $0, 8(%rax)
                movq kof_gc_head(%rip), %rcx
                movq %rcx, 16(%rax)
                movb $0, 24(%rax)
                movq %rax, kof_gc_head(%rip)
                movq kof_heap_low(%rip), %rcx
                testq %rcx, %rcx
                je .Lheap_set_low
                cmpq %rcx, %rax
                jae .Lheap_low_ok
            .Lheap_set_low:
                movq %rax, kof_heap_low(%rip)
            .Lheap_low_ok:
                movq kof_heap_high(%rip), %rcx
                movq %rax, %rdx
                addq %r12, %rdx
                cmpq %rdx, %rcx
                jae .Lheap_high_ok
                movq %rdx, kof_heap_high(%rip)
            .Lheap_high_ok:
                addq $32, %rax
                incq .Lkof_alloc_count(%rip)
                addq %r12, .Lkof_alloc_bytes(%rip)
                movq %rax, (%rsp)                # preserva retorno
                leaq kof_alloc_lock(%rip), %rdi
                movl $0, (%rdi)
                movl $1, %esi                    # FUTEX_WAKE, 1 waiter
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                syscall
                movq (%rsp), %rax
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_alloc_fail:
                leaq kof_alloc_lock(%rip), %rdi
                movl $0, (%rdi)
                movl $1, %esi
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                syscall
                leaq .Lstr_alloc_fail(%rip), %rdi
                call kof_panic
            """);
    }

    static void emitFree(StringBuilder sb) {
        sb.append("""
            .globl kof_free
            .type kof_free, @function
            kof_free:
                testq %rdi, %rdi
                jz .Lkof_free_done
                movq -32(%rdi), %rsi
                leaq -32(%rdi), %rdi
                movb $2, 24(%rdi)           # bit1: esta na free list (sweep nao re-insere)
                movq kof_free_head(%rip), %rax
                movq %rax, 8(%rdi)
                movq %rdi, kof_free_head(%rip)
                incq .Lkof_free_count(%rip)
                addq %rsi, .Lkof_free_bytes(%rip)
            .Lkof_free_done:
                ret
            """);
    }

    static void emitMemstats(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lkof_alloc_count: .quad 0
            .Lkof_free_count: .quad 0
            .Lkof_alloc_bytes: .quad 0
            .Lkof_free_bytes: .quad 0
            .Lkof_memstats_lbl_alloc: .asciz "allocs: "
            .Lkof_memstats_lbl_free: .asciz "frees: "
            .Lkof_memstats_lbl_live: .asciz "live bytes: "
            .Lkof_memstats_nl: .asciz "\\n"
            .section .text
            .globl kof_memstats
            .type kof_memstats, @function
            kof_memstats:
                pushq %rbx
                leaq .Lkof_memstats_lbl_alloc(%rip), %rdi
                call kof_print
                movq .Lkof_alloc_count(%rip), %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                leaq .Lkof_memstats_lbl_free(%rip), %rdi
                call kof_print
                movq .Lkof_free_count(%rip), %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                leaq .Lkof_memstats_lbl_live(%rip), %rdi
                call kof_print
                movq .Lkof_alloc_bytes(%rip), %rbx
                subq .Lkof_free_bytes(%rip), %rbx
                movq %rbx, %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            """);
    }

}