package dev.kof.compiler;

/**
 * Emissão do ASM do GC e erros de runtime (kof_gc/kof_process_exit/kof_panic/
 * kof_null_error/kof_bounds_error) do runtime nativo. Domínio isolado do
 * NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeGc {

    private RuntimeGc() {}

    static void emitGc(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lgc_tick: .quad 0
            .section .text
            .globl kof_gc_mark
            .type kof_gc_mark, @function
            kof_gc_mark:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %rbp
                movq %rsp, %r12
                movq %rbp, %r13
                testq %r13, %r13
                je .Lgc_mark_stack_fallback
                cmpq %r13, %r12
                jae .Lgc_mark_stack_fallback
                movq %r13, %rax
                subq %r12, %rax
                cmpq $1048576, %rax
                ja .Lgc_mark_stack_fallback
                jmp .Lgc_mark_stack
            .Lgc_mark_stack_fallback:
                leaq 4096(%r12), %r13
            .Lgc_mark_stack:
                cmpq %r13, %r12
                jge .Lgc_mark_stack_done
                movq (%r12), %rdi
                call kof_gc_mark_transitive
                addq $8, %r12
                jmp .Lgc_mark_stack
            .Lgc_mark_stack_done:
                # raizes estaticas: varre a area de dados do runtime
                # (.data+.bss) -- cache/mq/config/etc. vivem em .data, NAO bss,
                # e "kof_heap_root_start" e emitido como primeiro rotulo do
                # generateRuntimeAssembly, antes de qualquer .data do runtime.
                leaq kof_heap_root_start(%rip), %r12
                leaq _end(%rip), %r13
            .Lgc_mark_bss:
                cmpq %r13, %r12
                jge .Lgc_mark_bss_done
                movq (%r12), %rdi
                call kof_gc_mark_transitive
                addq $8, %r12
                jmp .Lgc_mark_bss
            .Lgc_mark_bss_done:
            .Lgc_mark_heap_done:
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_gc_try_mark
            .type kof_gc_try_mark, @function
            kof_gc_try_mark:
                pushq %rbx
                pushq %r12
                pushq %r10
                movq %rdi, %r12
                cmpq $0x1000, %r12
                jb .Ltry_done_pop
                testq $7, %r12
                jne .Ltry_done_pop
                movq kof_heap_low(%rip), %rbx
                testq %rbx, %rbx
                je .Ltry_heap_ok
                cmpq %rbx, %r12
                jb .Ltry_done_pop
                movq kof_heap_high(%rip), %rbx
                cmpq %rbx, %r12
                jae .Ltry_done_pop
            .Ltry_heap_ok:
                movq kof_gc_head(%rip), %rbx
                movq $10000, %r10
            .Ltry_loop:
                testq %rbx, %rbx
                je .Ltry_done_pop
                decq %r10
                je .Ltry_done_pop
                leaq 32(%rbx), %rax
                cmpq %rax, %r12
                je .Ltry_found
                movq 0(%rbx), %rcx
                subq $32, %rcx
                leaq 32(%rbx), %rdx
                cmpq %rdx, %r12
                jb .Ltry_next
                addq %rcx, %rdx
                cmpq %rdx, %r12
                jae .Ltry_next
            .Ltry_found:
                cmpb $0, 24(%rbx)
                jne .Ltry_done
                movb $1, 24(%rbx)
                jmp .Ltry_done
            .Ltry_next:
                movq 16(%rbx), %rbx
                jmp .Ltry_loop
            .Ltry_done:
                popq %r10
                popq %r12
                popq %rbx
                ret
            .Ltry_done_pop:
                popq %r10
                popq %r12
                popq %rbx
                ret

            .globl kof_gc_mark_transitive
            .type kof_gc_mark_transitive, @function
            kof_gc_mark_transitive:
                pushq %rbx
                pushq %rbp
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                cmpq $0x1000, %r12
                jb .Lmtrans_ret
                testq $7, %r12
                jne .Lmtrans_ret
                movq kof_heap_low(%rip), %rbx
                testq %rbx, %rbx
                je .Lmtrans_heap_ok
                cmpq %rbx, %r12
                jb .Lmtrans_ret
                movq kof_heap_high(%rip), %rbx
                cmpq %rbx, %r12
                jae .Lmtrans_ret
            .Lmtrans_heap_ok:
                movq kof_gc_head(%rip), %rbx
                movq $10000, %r10
            .Lmtrans_loop:
                testq %rbx, %rbx
                je .Lmtrans_ret
                decq %r10
                je .Lmtrans_ret
                leaq 32(%rbx), %rax
                cmpq %rax, %r12
                je .Lmtrans_found
                movq 0(%rbx), %rcx
                subq $32, %rcx
                leaq 32(%rbx), %rdx
                cmpq %rdx, %r12
                jb .Lmtrans_next
                addq %rcx, %rdx
                cmpq %rdx, %r12
                jae .Lmtrans_next
            .Lmtrans_found:
                cmpb $0, 24(%rbx)
                jne .Lmtrans_ret
                movb $1, 24(%rbx)
                movq 0(%rbx), %rcx
                testq %rcx, %rcx
                je .Lmtrans_ret
                leaq 32(%rbx), %r13
                leaq 32(%rbx), %r14
                addq %rcx, %r14
            .Lmtrans_fields:
                cmpq %r14, %r13
                jae .Lmtrans_ret
                movq (%r13), %rdi
                testq %rdi, %rdi
                je .Lmtrans_field_next
                pushq %r13
                pushq %r14
                call kof_gc_mark_transitive
                popq %r14
                popq %r13
                jmp .Lmtrans_field_next
            .Lmtrans_field_next:
                addq $8, %r13
                jmp .Lmtrans_fields
            .Lmtrans_next:
                movq 16(%rbx), %rbx
                jmp .Lmtrans_loop
            .Lmtrans_ret:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbp
                popq %rbx
                ret

            .globl kof_gc_sweep
            .type kof_gc_sweep, @function
            kof_gc_sweep:
                # Percorre a GC list; para cada bloco:
                #   byte flags @24: bit0=mark, bit1=in-free-list
                #   mark==1       -> limpa mark (sobreviveu ao ciclo)
                #   mark==0, !free-> insere na free list (morto), seta bit1
                pushq %rbx
                pushq %r12
                movq kof_gc_head(%rip), %rbx
            .Lgc_sweep_loop:
                testq %rbx, %rbx
                je .Lgc_sweep_done
                movzbl 24(%rbx), %eax
                testb $1, %al
                jz .Lgc_sweep_free_it
                # sobreviveu: limpa mark
                andb $~1, %al
                movb %al, 24(%rbx)
                jmp .Lgc_sweep_next
            .Lgc_sweep_free_it:
                testb $2, %al
                jnz .Lgc_sweep_next         # ja esta na free list
                # insere na free list
                movq 0(%rbx), %r12          # size (total)
                movq kof_free_head(%rip), %rax
                movq %rax, 8(%rbx)          # next_free = cabeca antiga
                movq %rbx, kof_free_head(%rip)
                orb $2, 24(%rbx)            # marca como liberado
                incq .Lkof_free_count(%rip)
                addq %r12, .Lkof_free_bytes(%rip)
            .Lgc_sweep_next:
                movq 16(%rbx), %rbx         # proximo na gc list
                jmp .Lgc_sweep_loop
            .Lgc_sweep_done:
                popq %r12
                popq %rbx
                ret

            # collect sem o tick-guard: usado por kof_alloc quando a free
            # list esta esgotada (evita mmap quando ha lixo coletavel).
            # Empilha os callee-saved para que o mark conservador tambem
            # enxergue ponteiros vivos em %rbx/%r12-%r15/%rbp do caller
            # (o mark so varre a stack -- registrador puro era coletado:
            # ex.: 2.º alloc do kof_spawn_handle_new perdia o handle em %rbx).
            .globl kof_gc_collect_now
            .type kof_gc_collect_now, @function
            kof_gc_collect_now:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %rbp
                call kof_gc_mark
                call kof_gc_sweep
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_gc_collect
            .type kof_gc_collect, @function
            kof_gc_collect:
                movq .Lgc_tick(%rip), %rax
                andq $4095, %rax
                jne .Lgc_collect_skip
                pushq %rbx
                call kof_gc_mark
                call kof_gc_sweep
                popq %rbx
            .Lgc_collect_skip:
                incq .Lgc_tick(%rip)
                ret
            .globl kof_gc_tick
            .type kof_gc_tick, @function
            kof_gc_tick:
                movq .Lgc_tick(%rip), %rax
                ret
            """);
    }

    static void emitProcessExit(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_process_exit
            .type kof_process_exit, @function
            kof_process_exit:
                movq %rdi, %rdi
                movq $60, %rax
                syscall
            """);
    }

    static void emitPanic(StringBuilder sb) {
        sb.append("""
            .section .data
            kof_exc_chain: .quad 0
            .section .text
            .globl kof_panic
            .type kof_panic, @function
            kof_panic:
                call kof_println
                movq $60, %rax
                movq $1, %rdi
                syscall
            """);
        sb.append("""
            .globl kof_throw_string
            .type kof_throw_string, @function
            kof_throw_string:
                movq %rdi, %rsi
                movq kof_exc_chain(%rip), %rax
                testq %rax, %rax
                jz .Lkof_throw_panic
                movq 8(%rax), %rsp
                movq 16(%rax), %rbp
                movq 24(%rax), %rcx
                movq %rcx, kof_exc_chain(%rip)
                movq 0(%rax), %rcx
                testq %rcx, %rcx
                jz .Lkof_throw_panic
                jmp *%rcx
            .Lkof_throw_panic:
                movq %rsi, %rdi
                call kof_println_string
                movq $60, %rax
                movq $1, %rdi
                syscall
            """);
    }

    static void emitNullError(StringBuilder sb) {
        sb.append(".Lstr_null_err: .asciz \"Runtime error: null pointer access\"\n");
        sb.append("""
            .globl kof_null_error
            .type kof_null_error, @function
            kof_null_error:
                leaq .Lstr_null_err(%rip), %rdi
                call kof_panic
            """);
    }

    static void emitBoundsError(StringBuilder sb) {
        sb.append(".Lstr_bounds_err: .asciz \"Runtime error: array index out of bounds\"\n");
        sb.append("""
            .globl kof_bounds_error
            .type kof_bounds_error, @function
            kof_bounds_error:
                leaq .Lstr_bounds_err(%rip), %rdi
                call kof_panic
            """);
    }

}