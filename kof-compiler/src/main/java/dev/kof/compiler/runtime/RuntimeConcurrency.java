package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de concorrência (kof_spawn/kof_spawn_result/kof_await/kof_thread) do
 * runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeConcurrency {

    private RuntimeConcurrency() {}

    public static void emitConcurrency(StringBuilder sb) {
        sb.append("""
            .section .bss
            .balign 8
            kof_spawn_handles: .quad 0          # cabeca da lista (no: [next, handle])
            kof_spawn_count: .quad 0
            # §117 (8a): 256 entries de 16B [tid(8), flag(8)], chave = TID REAL
            # (pthread_self) + probe linear — substitui a tabela por hash
            # truncado (2 TIDs vivos no mesmo slot = cancel perdido/alheio).
            .balign 16
            kof_cancel_slots: .space 4096
            .section .text
            .globl kof_spawn_trampoline
            .type kof_spawn_trampoline, @function
            kof_spawn_trampoline:
                # rdi = bloco {task, handle}
                # 2 pushes + subq 48 -> site do call rsp ≡ 0 (mesmo padrão do
                # pthread_create em kof_spawn_handle_new). Frame de handler de
                # 48B: [0]=handler, [8]=rsp, [16]=rbp, [24]=chain anterior,
                # [32]=handle — o catch lê DAQUI (o task clobbera callee-saved).
                pushq %rbx
                pushq %r12
                subq $48, %rsp                  # §129: nó de handler do worker
                movq %rdi, %rbx
                # §117 (8a): registra (TID real, flag=0) na tabela de slots
                # com CHAVE = pthread_self (probe linear) — zero colisão: a
                # tabela antiga indexava por hash truncado e 2 TIDs vivos
                # podiam cair no mesmo slot (cancel vazava/apagava alheio).
                call pthread_self               # rax = TID
                movq %rax, %rdi
                call kof_cancel_slot_insert     # rax = entry ptr (flag=0)
                movq 8(%rbx), %r12              # handle
                movq %rax, 32(%r12)             # handle->cancelEntry (trampoline SEMPRE tem handle)
                movq %rax, 40(%rsp)             # §286: cópia da entry NO FRAME — o delete
                                                # lê daqui; handle->cancelEntry pode apontar
                                                # p/ slot ALHEIO se o handle for reciclado
                                                # entre done=1 e o nosso delete
                movq %r12, 32(%rsp)             # frame->handle (o catch lê daqui)
                # §286: aplica cancel pendente marcado por kof_cancel ANTES deste
                # registro (worker recém-spawnado ainda não registrado sob carga).
                # Dekker com kof_cancel: store(cancelEntry) → fence → load(pending).
                mfence
                movq 48(%r12), %rdx
                testq %rdx, %rdx
                jz .Lkof_spawn_no_pend
                testq %rax, %rax                # tabela cheia → nada a marcar
                jz .Lkof_spawn_no_pend
                movq $1, 8(%rax)                # flag = 1 (pedido antigo vale agora)
            .Lkof_spawn_no_pend:
                # §129 (DECISIONS §2, opção B): handler POR WORKER. O chain é
                # TLS (RuntimeGc), então um `throw` sem try no worker longjmpa
                # AQUI (não no try da main) e marca o handle como excepcional;
                # await/selectAny do consumidor relança a causa.
                leaq .Lkof_spawn_catch(%rip), %rax
                movq %rax, 0(%rsp)
                movq %rsp, 8(%rsp)
                movq %rbp, 16(%rsp)
                call kof_plat_tls_get       # M-4 (#1135): a cadeia de exceções pela costura
                movq %rax, 24(%rsp)
                pushq %rdi
                pushq %rcx
                leaq 16(%rsp), %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                movq 0(%rbx), %rdi              # task
                movq 8(%rdi), %rax              # task vtable
                movq (%rax), %rax               # vtable[0] = invoke
                call *%rax
                # término normal: desinstala o handler e publica o resultado
                movq 24(%rsp), %rcx
                pushq %rdi
                pushq %rcx
                movq %rcx, %rdi
                call kof_plat_tls_set        # M-4 (#1135): a cadeia pela costura
                popq %rcx
                popq %rdi
                movq 40(%rsp), %rbx             # §286: entry do FRAME antes de desmontar
                addq $48, %rsp
                testq %r12, %r12
                jz .Lkof_spawn_thr_done
                movq %rax, 16(%r12)             # handle->result
                movl $1, 4(%r12)                # handle->done = 1
                jmp .Lkof_spawn_thr_done
            .Lkof_spawn_catch:
                # kof_throw_string já desempilhou o chain e restaurou rsp/rbp
                # ao frame base; %rsi = mensagem. O handle vem do FRAME
                # (32(%rsp)) — NÃO de %r12, que o task pode ter clobberado.
                movq 32(%rsp), %r12
                movq %rsi, 40(%r12)             # handle->exc
                movl $1, 4(%r12)                # handle->done = 1
                movq 40(%rsp), %rbx             # §286: entry do FRAME antes de desmontar
                addq $48, %rsp
            .Lkof_spawn_thr_done:
                # §117/§286: remove a entry deste TID (tid=0) — slot volta a vazio
                # sem tocar em worker alheio. §286: a entry vem do FRAME do próprio
                # worker (%rbx), não do handle: entre `done=1` publicado e este
                # delete o main pode RECICLAR o handle p/ outro worker, e ler
                # handle->cancelEntry apagaria o slot ALHEIO (stale flag=1 →
                # `cancelled()` vazando 999 p/ o worker novo sob carga).
                testq %rbx, %rbx
                jz .Lkof_spawn_thr_nocl
                movq $0, (%rbx)
            .Lkof_spawn_thr_nocl:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            # ---- tabela de cancel por TID real (§117, 8a) ----
            # 256 entries de 16B: [tid(8), flag(8)]. Probe linear a partir do
            # hash phi do TID — chaves REAIS (pthread_self), colisão resolve
            # pelo probe; tid=0 = vazio. Handle: 32=cancelEntry (novo).
            .globl kof_cancel_slot_insert
            .type kof_cancel_slot_insert, @function
            kof_cancel_slot_insert:
                # rdi = TID -> rax = entry (tid=rdi, flag=0) ou 0 (tabela cheia)
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx                 # tid
                movabs $0x9E3779B97F4A7C15, %r10
                mulq %rbx                       # rdx = high 64
                shrq $56, %rdx                  # slot base
                xorl %r12d, %r12d               # i = 0
            .Lkcsi_loop:
                cmpl $256, %r12d
                jge .Lkcsi_full
                movl %r12d, %eax
                addl %edx, %eax
                andl $255, %eax
                imull $16, %eax, %eax
                leaq kof_cancel_slots(%rip), %r10
                addq %rax, %r10                 # entry
                cmpq $0, (%r10)
                je .Lkcsi_claim
                cmpq %rbx, (%r10)
                je .Lkcsi_reuse
                incl %r12d
                jmp .Lkcsi_loop
            .Lkcsi_claim:
                movq %rbx, (%r10)
            .Lkcsi_reuse:
                movq $0, 8(%r10)                # flag = 0
                movq %r10, %rax
                popq %r12
                popq %rbx
                ret
            .Lkcsi_full:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_cancel_slot_find
            .type kof_cancel_slot_find, @function
            kof_cancel_slot_find:
                # rdi = TID -> rax = entry (se registrada e viva) ou 0
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movabs $0x9E3779B97F4A7C15, %r10
                mulq %rbx
                shrq $56, %rdx
                xorl %r12d, %r12d
            .Lkcsf_loop:
                cmpl $256, %r12d
                jge .Lkcsf_none
                movl %r12d, %eax
                addl %edx, %eax
                andl $255, %eax
                imull $16, %eax, %eax
                leaq kof_cancel_slots(%rip), %r10
                addq %rax, %r10
                cmpq %rbx, (%r10)
                je .Lkcsf_hit
                incl %r12d
                jmp .Lkcsf_loop
            .Lkcsf_hit:
                movq %r10, %rax
                popq %r12
                popq %rbx
                ret
            .Lkcsf_none:
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
                # G-6(a)/§260: fecha o gate do auto-collect ANTES de qualquer
                # alloc do caminho de spawn. O contador e CUMULATIVO e o unico
                # leitor e o gatilho em .Lkof_alloc_maybe_gc (RuntimeMemory).
                # Se o incq ficar so no .Lkof_spawn_ok (pos-pthread_create),
                # o worker pode chamar kof_alloc nesse intervalo com count
                # ainda 0: o collect-dispara varre sem a stack do worker (face
                # catalogada NUNCA-silencioso) e libera o result-box vivo do
                # proprio worker -- medido: spawnWorkerThrowIsolated* nativo
                # perdia s1=42 -> s1=0 (19/09). Com o incq na entrada, spawn
                # em andamento ou passado nunca ve auto-collect.
                incq kof_spawn_count(%rip)
                movl $56, %edi                  # §117: 32=cancelEntry, 40=exc; §286: 48=pending
                call kof_alloc
                movq %rax, %rbx                 # handle
                movl $2, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movq $0, 16(%rbx)
                movq $0, 24(%rbx)
                movq $0, 32(%rbx)               # cancelEntry = 0
                movq $0, 40(%rbx)
                movq $0, 48(%rbx)               # §286: pending = 0
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
                call kof_plat_thread_create
                subq %rsp, %r15                 # r15=(A-8)-B = delta
                addq %r15, %rsp                 # rsp=B+delta=A-8
                popq %r15                       # r15c ; rsp=A (frame restaurado)
                testl %eax, %eax
                jz .Lkof_spawn_ok
                # falha no pthread: roda inline (degradacao segura). Passa o
                # BLOCO {task,handle} (handle->block em 24), não a task: o
                # trampolim lê 8(%rdi) como handle.
                movq 24(%rbx), %rdi
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
                # G-6(a): incq movido p/ entrada de kof_spawn_handle_new —
                # aqui era tarde (janela de alloc do worker com gate aberto).
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
                movq $0, 8(%rdi)                # §129: join feito -> zera o TID
                                                # (join_all no fim do main não
                                                # re-join — double join é UB e
                                                # segfaulta com TCB reciclado)
            .Lkof_await_val:
                # §129: task que falhou publica a causa no handle (40); o
                # await RELANÇA no thread do consumidor (paridade JVM).
                movq 40(%rdi), %rcx
                testq %rcx, %rcx
                jnz .Lkof_await_throw
                movq 16(%rdi), %rax
                ret
            .Lkof_await_throw:
                movq %rcx, %rdi
                jmp kof_throw_string
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
                # §129: falha do worker relança no consumidor
                movq 40(%rbx), %rcx
                testq %rcx, %rcx
                jnz .Lkat_throw
                movq 16(%rbx), %rax
                popq %r12
                popq %rbx
                ret
            .Lkat_throw:
                movq %rcx, %rdi
                jmp kof_throw_string
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

            # cancel(handle): marca `canceled` NO PRÓPRIO handle (§117, 8a) —
            # cooperativo; o worker vê via TLS. Sem calls → alinhamento irrelevante.
            .globl kof_cancel
            .type kof_cancel, @function
            kof_cancel:
                # rdi = handle -> 1 se marcou, 0 se handle nulo/inválido
                testq %rdi, %rdi
                jz .Lkof_cancel_no
                cmpl $2, 0(%rdi)
                jne .Lkof_cancel_no
                movq 8(%rdi), %rsi              # TID (pthread_create grava)
                testq %rsi, %rsi
                jz .Lkof_cancel_no              # nunca disparou
                pushq %rbx                      # §286: handle vivo p/ o caminho pending
                movq %rdi, %rbx
                movq %rsi, %rdi
                call kof_cancel_slot_find
                testq %rax, %rax
                jnz .Lkof_cancel_hit
                # §286: handle criado mas a trampoline AINDA NÃO registrou a entry
                # (janela de agendamento sob carga → o cancel se perdia e o
                # assert(cancel) do §117 falava). Marca pending no handle e
                # re-checa a entry — Dekker com o `mfence` da trampoline: ou o
                # cancel pega a entry recém-criada, ou o start aplica o pending.
                movq $1, 48(%rbx)               # pending = 1
                mfence
                movq 8(%rbx), %rdi
                call kof_cancel_slot_find
                testq %rax, %rax
                jz .Lkof_cancel_yes
            .Lkof_cancel_hit:
                movq $1, 8(%rax)                # flag = 1
            .Lkof_cancel_yes:
                movl $1, %eax
                popq %rbx
                ret
            .Lkof_cancel_no:
                xorl %eax, %eax
                ret

            # cancelled(): a flag do TID ATUAL foi marcada?
            # 1 call (pthread_self) -> rsp≡8 na entrada, ok.
            .globl kof_cancelled
            .type kof_cancelled, @function
            kof_cancelled:
                # §117: flag da entry do TID ATUAL (chave real, sem colisão).
                # 0 fora de worker (paridade: interpretador sempre 0).
                pushq %rbx
                pushq %r12
                subq $8, %rsp                   # 2 push + subq 8 -> rsp≡8 no call
                call pthread_self
                movq %rax, %rdi
                call kof_cancel_slot_find
                testq %rax, %rax
                jz .Lkof_cancelled_no
                movzbl 8(%rax), %eax
                addq $8, %rsp
                popq %r12
                popq %rbx
                ret
            .Lkof_cancelled_no:
                xorl %eax, %eax
                addq $8, %rsp
                popq %r12
                popq %rbx
                ret

            # selectAny(list): valor do primeiro handle pronto; senão
            # aguarda (polling 1ms) até um terminar -- paridade JVM anyOf.
            # frame: 3 push -> mesmos offsets de callee-saved do frame
            # antigo. §252: size em r14
            # (callee-saved); na slot -8(%rsp) o `call usleep` gravava o
            # próprio endereço de retorno e o re-scan lia lixo como size.
            .globl kof_select_any
            .type kof_select_any, @function
            kof_select_any:
                pushq %rbx                      # list
                pushq %r12                      # index
                pushq %r14                      # size (mesma prof. do frame)
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lkof_sel_no
                call kof_list_size              # rsp≡8
                testq %rax, %rax
                jz .Lkof_sel_no
                movq %rax, %r14                 # size
                xorl %r12d, %r12d
            .Lkof_sel_scan:
                cmpq %r14, %r12                 # r12 - r14 = index - size
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
                movq 40(%rax), %rcx             # §129: handle excepcional?
                testq %rcx, %rcx
                jnz .Lkof_sel_rethrow
                movq 16(%rax), %rax             # pronto: devolve resultado
                popq %r14
                popq %r12
                popq %rbx
                ret
            .Lkof_sel_rethrow:
                movq %rcx, %rdi
                jmp kof_throw_string
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
                popq %r14
                popq %r12
                popq %rbx
                ret
            """);
    }

}