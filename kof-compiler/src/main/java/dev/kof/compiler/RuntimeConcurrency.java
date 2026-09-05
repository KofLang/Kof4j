package dev.kof.compiler;

/**
 * Emissão do ASM de concorrência (kof_spawn/kof_spawn_result/kof_await/kof_thread) do
 * runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeConcurrency {

    private RuntimeConcurrency() {}

    static void emitConcurrency(StringBuilder sb) {
        sb.append("""
            .section .bss
            .balign 8
            kof_spawn_handles: .quad 0          # cabeca da lista (no: [next, handle])
            kof_spawn_count: .quad 0
            kof_cancelled_flags: .space 256     # cancel cooperativo por TID % 256
            .section .text
            .globl kof_spawn_trampoline
            .type kof_spawn_trampoline, @function
            kof_spawn_trampoline:
                # rdi = bloco {task, handle}
                # 2 pushes -> site do call rsp ≡ 0 (mesmo padrão do pthread_create
                # em kof_spawn_handle_new). Sem subq: mantém pthread_self e o
                # call *task no alinhamento que já funciona.
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                # limpa a flag de cancel deste TID (slot pode ser de worker
                # anterior reutilizado; mod 256).
                call pthread_self               # TID em rax
                movabs $0x9E3779B97F4A7C15, %r10
                mulq %r10                       # rdx = (TID*phi) >> 64
                shrq $56, %rdx                  # slot 0..255
                leaq kof_cancelled_flags(%rip), %r10
                movb $0, (%r10,%rdx,1)
                movq 0(%rbx), %rdi              # task
                movq 8(%rbx), %r12              # handle (0 p/ stmt)
                movq 8(%rdi), %rax              # task vtable
                movq (%rax), %rax               # vtable[0] = invoke
                call *%rax
                testq %r12, %r12
                jz .Lkof_spawn_thr_done
                movq %rax, 16(%r12)             # handle->result
                movl $1, 4(%r12)                # handle->done = 1
            .Lkof_spawn_thr_done:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_spawn_handle_new
            .type kof_spawn_handle_new, @function
            kof_spawn_handle_new:
                # rdi = task, esi = wants_result -> handle
                # entry ≡8; 4 push -> ≡8; subq 24 -> ≡8-24? 16k+8-32-24 = 16k-48 ≡ 0 no call ✓
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $24, %rsp
                movq %rdi, %r13
                movl %esi, %r14d
                movl $32, %edi
                call kof_alloc
                movq %rax, %rbx                 # handle
                movl $2, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movq $0, 16(%rbx)
                # bloco do trampolim
                movl $16, %edi
                call kof_alloc
                movq %r13, 0(%rax)              # task
                movq %rbx, 8(%rax)              # handle
                # GC: o trampolim só é referenciado pelo arg do pthread_create;
                # quando o worker executa, nada na stack/bss do main aponta pra
                # ele → o mark-sweep varreria como morto e o free corromperia o
                # worker. Ancora no handle (24) -- handles ficam na lista global
                # (bss) até o join, então o bloco continua visível ao GC.
                movq %rax, 24(%rbx)
                leaq 8(%rbx), %rdi              # &handle->thread
                xorl %esi, %esi                 # attr = NULL
                leaq kof_spawn_trampoline(%rip), %rdx
                movq %rax, %rcx                 # arg = bloco
                # pthread_create é um C call: a ABI SysV exige rsp ≡ 0 (mod 16)
                # NO SITE DO CALL. O caller (main) pode chegar desalinhado quando
                # um println/print precede o spawn (a convenção args-by-stack via
                # push empilha um slot a mais) -- sem alinhar, a glibc segfaulta
                # em pthread_attr_copy escrevendo no frame. Alinha na hora,
                # preservando r15 (callee-saved, livre aqui) e o frame de rsp:
                pushq %r15                      # [A-8]=r15c ; rsp=A-8
                movq %rsp, %r15                 # r15=A-8
                andq $-16, %rsp                 # rsp=B (B%16==0)
                call pthread_create
                subq %rsp, %r15                 # r15=(A-8)-B = delta
                addq %r15, %rsp                 # rsp=B+delta=A-8
                popq %r15                       # r15c ; rsp=A (frame restaurado)
                testl %eax, %eax
                jz .Lkof_spawn_ok
                # falha no pthread: roda inline (degradacao segura)
                movq %r13, %rdi
                call kof_spawn_trampoline
                movl $1, 4(%rbx)
                jmp .Lkof_spawn_next
            .Lkof_spawn_ok:
                # adiciona o handle na lista global p/ join implicito
                movl $16, %edi
                call kof_alloc
                leaq kof_spawn_handles(%rip), %rcx
                movq (%rcx), %rdx               # head atual
                movq %rbx, 8(%rax)              # no->handle
                movq %rdx, 0(%rax)              # no->next
                movq %rax, (%rcx)
                incq kof_spawn_count(%rip)
            .Lkof_spawn_next:
                addq $24, %rsp
                movq %rbx, %rax                 # retorna handle
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_spawn
            .type kof_spawn, @function
            kof_spawn:
                # rdi = task (stmt) -> handle REGISTRADO: o fim do main chama
                # kof_spawn_join_all e aguarda TODAS as tasks -- tarefa spawnada
                # nunca fica órfã (senão o processo sai antes do worker rodar).
                movl $1, %esi
                jmp kof_spawn_result

            .globl kof_spawn_result
            .type kof_spawn_result, @function
            kof_spawn_result:
                # rdi = task -> handle registrado (await/join depois)
                movl $1, %esi
                jmp kof_spawn_handle_new

            .globl kof_await
            .type kof_await, @function
            kof_await:
                # rdi = handle -> valor (join da thread)
                testq %rdi, %rdi
                jz .Lkof_await_null
                cmpl $2, 0(%rdi)
                jne .Lkof_await_null
                cmpq $0, 8(%rdi)
                je .Lkof_await_val
                pushq %rdi                      # rsp: ≡8 -> ≡0 no call (ABI)
                movq 8(%rdi), %rdi              # pthread_join(tid, NULL)
                xorl %esi, %esi
                call pthread_join
                popq %rdi                       # restaura handle base
            .Lkof_await_val:
                movq 16(%rdi), %rax
                ret
            .Lkof_await_null:
                xorl %eax, %eax
                ret

            .globl kof_spawn_join_all
            .type kof_spawn_join_all, @function
            kof_spawn_join_all:
                # join implicito: percorre a lista e aguarda todas as tasks
                pushq %rbx
                pushq %r12
                subq $8, %rsp
                movq kof_spawn_handles(%rip), %rbx
            .Lkof_join_loop:
                testq %rbx, %rbx
                jz .Lkof_join_done
                movq 8(%rbx), %r12              # handle
                cmpq $0, 8(%r12)
                je .Lkof_join_next
                movq 8(%r12), %rdi              # tid
                xorl %esi, %esi                 # retval = NULL
                call pthread_join
            .Lkof_join_next:
                movq 0(%rbx), %rbx              # next
                jmp .Lkof_join_loop
            .Lkof_join_done:
                addq $8, %rsp
                popq %r12
                popq %rbx
                ret

            # kof_await_timeout(handle, timeoutMs): valor se a task terminar no prazo;
            # senão lança (kof_throw_string -> try/catch do usuário) ou panic.
            # Polling 1ms (o handle já existe; sem join para não bloquear demais).
            .Lstr_await_timeout: .asciz "awaitTimeout: estourou o tempo limite"
            .globl kof_await_timeout
            .type kof_await_timeout, @function
            kof_await_timeout:
                # rdi = handle, esi = timeoutMs (>=0)
                # entry rsp≡8; 2 push -> rsp≡0? nao: 16k+8-8-8 = 16k-8 ≡ 8 no call ✓
                pushq %rbx
                pushq %r12
                testq %rdi, %rdi
                jz .Lkat_zero
                cmpl $2, 0(%rdi)
                jne .Lkat_zero
                movq %rdi, %rbx
                movl %esi, %r12d                    # iterações restantes (~1ms cada)
            .Lkat_poll:
                cmpl $1, 4(%rbx)                    # done?
                je .Lkat_result
                testl %r12d, %r12d
                jle .Lkat_timeout
                movl $1000, %edi
                call usleep
                decl %r12d
                jmp .Lkat_poll
            .Lkat_result:
                movq 16(%rbx), %rax
                popq %r12
                popq %rbx
                ret
            .Lkat_timeout:
                leaq .Lstr_await_timeout(%rip), %rdi
                call kof_throw_string               # longjmp p/ o try; panic se não houver
            .Lkat_zero:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            # CONC001 (residual): done/poll não-bloqueantes sobre o handle.
            # Handle: 0=tag(2), 4=done, 8=pthread_t, 16=result. x86 TSO
            # garante visibilidade do store do worker p/ um load simples.
            .globl kof_done
            .type kof_done, @function
            kof_done:
                # rdi = handle -> 1 se a tarefa terminou, 0 caso contrário.
                # movzbl: zero-estende p/ rax de 64 bits (bool limpo)
                testq %rdi, %rdi
                jz .Lkof_done_zero
                cmpl $2, 0(%rdi)
                jne .Lkof_done_zero
                movzbl 4(%rdi), %eax
                ret
            .Lkof_done_zero:
                xorl %eax, %eax
                ret

            .globl kof_poll
            .type kof_poll, @function
            kof_poll:
                # rdi = handle -> valor se pronto, 0 se ainda não (não bloqueia)
                testq %rdi, %rdi
                jz .Lkof_poll_zero
                cmpl $2, 0(%rdi)
                jne .Lkof_poll_zero
                cmpl $1, 4(%rdi)
                jne .Lkof_poll_zero
                movq 16(%rdi), %rax
                ret
            .Lkof_poll_zero:
                xorl %eax, %eax
                ret

            # cancel(handle): marca a flag do TID do handle (cooperativo).
            # sem calls -> alinhamento irrelevante.
            .globl kof_cancel
            .type kof_cancel, @function
            kof_cancel:
                # rdi = handle -> 1 se marcou, 0 se handle nulo/inválido
                testq %rdi, %rdi
                jz .Lkof_cancel_no
                cmpl $2, 0(%rdi)
                jne .Lkof_cancel_no
                movq 8(%rdi), %rax              # TID
                testq %rax, %rax
                jz .Lkof_cancel_no              # nunca disparou: sem TID
                movabs $0x9E3779B97F4A7C15, %r10
                mulq %r10                       # rdx = (TID*phi) >> 64
                shrq $56, %rdx                  # slot 0..255
                leaq kof_cancelled_flags(%rip), %r10
                movb $1, (%r10,%rdx,1)
                movq $1, %rax                   # rax 64b limpo (movl deixaria altos do mulq)
                ret
            .Lkof_cancel_no:
                xorl %eax, %eax
                ret

            # cancelled(): a flag do TID ATUAL foi marcada?
            # 1 call (pthread_self) -> rsp≡8 na entrada, ok.
            .globl kof_cancelled
            .type kof_cancelled, @function
            kof_cancelled:
                call pthread_self               # TID em rax
                movabs $0x9E3779B97F4A7C15, %r10
                mulq %r10                       # rdx = (TID*phi) >> 64
                shrq $56, %rdx                  # slot 0..255
                leaq kof_cancelled_flags(%rip), %r10
                movzbl (%r10,%rdx,1), %eax
                ret

            # selectAny(list): valor do primeiro handle pronto; senão
            # aguarda (polling 1ms) até um terminar -- paridade JVM anyOf.
            # frame: 2 push + subq 16 -> rsp≡8 nos calls.
            .globl kof_select_any
            .type kof_select_any, @function
            kof_select_any:
                pushq %rbx                      # list
                pushq %r12                      # index
                subq $16, %rsp                  # -8(%rsp)=size
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lkof_sel_no
                call kof_list_size              # rsp≡8
                testq %rax, %rax
                jz .Lkof_sel_no
                movq %rax, -8(%rsp)            # size
                xorl %r12d, %r12d
            .Lkof_sel_scan:
                movq -8(%rsp), %rax             # rax = size
                cmpq %rax, %r12                 # r12 - rax = index - size
                jge .Lkof_sel_wait              # index >= size -> aguarda e re-escaneia
                movq %rbx, %rdi
                movl %r12d, %esi
                call kof_list_get               # rsp≡8
                testq %rax, %rax
                jz .Lkof_sel_next
                cmpl $2, 0(%rax)
                jne .Lkof_sel_next
                cmpl $1, 4(%rax)
                jne .Lkof_sel_next
                mfence                          # visibilidade do done/result escrito pelo worker
                movq 16(%rax), %rax             # pronto: devolve resultado
                addq $16, %rsp
                popq %r12
                popq %rbx
                ret
            .Lkof_sel_next:
                incq %r12
                jmp .Lkof_sel_scan
            .Lkof_sel_wait:
                movl $1000, %edi                # usleep(1ms)
                call usleep                     # rsp≡8
                xorl %r12d, %r12d               # RE-SCAN: reset index (senão loopa p/ sempre)
                jmp .Lkof_sel_scan
            .Lkof_sel_no:
                xorl %eax, %eax
                addq $16, %rsp
                popq %r12
                popq %rbx
                ret
            """);
    }

}