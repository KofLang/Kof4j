package dev.kof.compiler.runtime;

/**
 * Fatia runtime do namespace process no Native x86-64 (host profile) —
 * linha 1 do ledger de paridade total (D-FULL-PARITY-050).
 *
 * <p>Contrato JVM-oráculo ({@code JvmRuntimeCore.kof_process_run}):
 * {@code ProcessResult(stdout, stderr, exitCode)} — stdout/stderr capturados
 * separados (redirectErrorStream(false)), stdin de /dev/null, exit code real
 * do filho, falha de spawn = {@code ("", mensagem, -1)} (a JVM devolve a
 * mensagem da exceção no campo stderr).
 *
 * <p>Mecânica (SysV x86-64, syscalls cruas + execvp do libc host p/ semântica
 * de PATH idêntica à do ProcessBuilder): {@code pipe2} para stdout, stderr e
 * exec-fail (este com O_CLOEXEC, convenção posix_spawn), {@code fork} (57);
 * no filho: dup2 dos pipes em 1/2, stdin de /dev/null, argv montado do
 * List&lt;String&gt; (payload UTF-8 NUL-terminado no offset 24), {@code execvp};
 * se o exec falha, o filho escreve a mensagem no stderr e 1 byte no pipe
 * CLOEXEC e _exit(127). No pai: fecha as pontas de escrita, drena os DOIS
 * pipes com {@code poll} (sem deadlock — a JVM lê os dois em threads),
 * {@code wait4} → WEXITSTATUS, lê o byte de exec-fail e monta o Result
 * opaco: {@code [0]=stdout [8]=stderr [16]=exitCode} — os campos do frontend
 * (ExpressionLowerer) baixam para os accessors
 * {@code kof_process_result_stdout/stderr/exitcode} no Native.
 *
 * <p>Dreno: buffers GLOBAIS (kof_proc_outbuf/errbuf/chunk — 1 MiB + 1 MiB +
 * 4 KiB), allocados na primeira chamada e reutilizados; NUNCA liberados por
 * chamada (o from_literal copia para o Result; reciclar blocos grandes por
 * run poluía a freelist — corrupção medida em alloc pequeno pós-free).
 *
 * <p>Layout do frame (r14 = base): out_r/out_w 0/4, err_r/err_w 8/12,
 * fail_r/fail_w 16/20, status 24, pid 28, failflag 32, eofout 36, eoferr 40,
 * trios de buffer out 48/56/64 e err 72/80/88 (ptr/len/cap), pollfds 96 e
 * 104 (revents em 102 e 110), chunkptr 112. Total 4224
 * (múltiplo de 16; entrada ≡8, 5 push ≡ +40 → ≡0 nas chamadas).
 */
public final class RuntimeProcess {

    private RuntimeProcess() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── kof.process (Native x86-64, host) — linha 1 do ledger de paridade ──
            .section .rodata
            .Lkof_proc_devnull:
                .asciz "/dev/null"
            .Lkof_proc_overflow: .asciz "process: stream exceeds the 1 MiB native capture buffer (PROC001)"
            .Lkof_proc_execfail:
                .asciz "process: exec failed"
            .Lkof_proc_sysfail:
                .asciz "process: syscall failed"
            .Lkof_proc_rw_nocwd_msg:
                .asciz "kof_shell_runwith: chdir failed"

            # Dreno global do kof_process_run (allocado uma vez, reutilizado)
            # .balign 8 OBRIGATÓRIO nos 4 globals: o scan de raízes do GC anda
            # de 8 em 8 — .quad após .asciz cai desalinhado e fica INVISÍVEL
            # ao mark (sweep liberava o outbuf vivo e o first-fit re-entregava
            # o mesmo nó p/ errbuf/chunk: OUT==ERR==CHUNK medido no gdb).
            .data
            .balign 8
            kof_proc_bufs_ready: .quad 0
            .balign 8
            kof_proc_outbuf:     .quad 0
            .balign 8
            kof_proc_errbuf:     .quad 0
            .balign 8
            kof_proc_chunk:      .quad 0
            .section .text

            .globl kof_process_result_stdout
            .type kof_process_result_stdout, @function
            kof_process_result_stdout:
                movq 0(%rdi), %rax
                ret

            .globl kof_process_result_stderr
            .type kof_process_result_stderr, @function
            kof_process_result_stderr:
                movq 8(%rdi), %rax
                ret

            .globl kof_process_result_exitCode
            .type kof_process_result_exitCode, @function
            kof_process_result_exitCode:
                movl 16(%rdi), %eax
                ret

            # ── .Lkof_proc_append(trio, src, n): trio = {ptr,len,cap} em rdi ──
            # cresce por duplicação (kof_alloc + kof_memcpy); preserva r14/r15
            # do chamador (frame base e scratch). Sem free — arena igual aos
            # demais slices.
            .Lkof_proc_append:
                # (rdi=trio, rsi=bytes, rdx=n) — buffer PRE-ALOCADO (1 MiB):
                # sem realloc no dreno (o caminho de grow corrompia caudas de
                # chunk — NULs medidos nas fronteiras de cap; raiz sob §AUDIT).
                # Estouro = pânico honesto R6, nunca truncamento silencioso.
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rdx, %r13
                movq 8(%rbx), %r14          # len
                leaq (%r14,%r13), %r15      # need = len+n
                cmpq 16(%rbx), %r15
                ja .Lkof_proc_append_over
                movq (%rbx), %rdi           # ptr
                addq %r14, %rdi             # + len
                movq %r12, %rsi
                movq %r13, %rdx
                call kof_memcpy
                addq %r13, 8(%rbx)          # len += n
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_proc_append_over:
                leaq .Lkof_proc_overflow(%rip), %rdi
                call kof_panic

            .globl kof_process_run
            .type kof_process_run, @function
            kof_process_run:
                # rdi = program (KofString), rsi = List<String>
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx             # program
                movq %rsi, %r12             # list
                subq $4224, %rsp
                movq %rsp, %r14
                # zera flags (32..43) — trios vao para pre-alocacao abaixo
                movq $0, 32(%r14)
                movq $0, 40(%r14)
                # Buffers de dreno GLOBAIS (allocados UMA vez, reutilizados em
                # toda chamada): outbuf/errbuf 1 MiB + chunk 4 KiB. NAO há
                # kof_free por chamada — o copy do from_literal já isola o
                # Result, e reciclar 2 MiB+4K por run poluía a freelist de
                # blocos grandes (alloc pequeno pós-free = corrupção medida:
                # 2º runWith perdia o item da lista rest; §catalogado no
                # ledger). Reset dos len por chamada (56/80 no frame).
                cmpq $0, kof_proc_bufs_ready(%rip)
                jne .Lkof_proc_bufs_ok
                movl $1048576, %edi
                call kof_alloc
                movq %rax, kof_proc_outbuf(%rip)
                movl $1048576, %edi
                call kof_alloc
                movq %rax, kof_proc_errbuf(%rip)
                movl $4096, %edi
                call kof_alloc
                movq %rax, kof_proc_chunk(%rip)
                movq $1, kof_proc_bufs_ready(%rip)
            .Lkof_proc_bufs_ok:
                movq kof_proc_outbuf(%rip), %rax
                movq %rax, 48(%r14)
                movq $0, 56(%r14)
                movq $1048576, 64(%r14)
                movq kof_proc_errbuf(%rip), %rax
                movq %rax, 72(%r14)
                movq $0, 80(%r14)
                movq $1048576, 88(%r14)
                movq kof_proc_chunk(%rip), %rax
                movq %rax, 112(%r14)     # chunkptr (slot do antigo tmp; pollfds ficam em 96/104)
                # pipe2(out=0(%r14), 0)
                leaq 0(%r14), %rdi
                xorl %esi, %esi
                movl $293, %eax
                syscall
                # pipe2(err=8(%r14), 0)
                leaq 8(%r14), %rdi
                xorl %esi, %esi
                movl $293, %eax
                syscall
                # pipe2(fail=16(%r14), O_CLOEXEC)
                leaq 16(%r14), %rdi
                movl $524288, %esi
                movl $293, %eax
                syscall
                # fork
                movl $57, %eax
                syscall
                testl %eax, %eax
                js .Lkof_proc_run_fail_syscall
                jne .Lkof_proc_run_parent

                # ── FILHO ──────────────────────────────────────────────
                movl 4(%r14), %edi          # dup2(out_w → 1)
                movl $1, %esi
                movl $33, %eax
                syscall
                movl 12(%r14), %edi         # dup2(err_w → 2)
                movl $2, %esi
                movl $33, %eax
                syscall
                movl 0(%r14), %edi          # close(out_r)
                movl $3, %eax
                syscall
                movl 8(%r14), %edi          # close(err_r)
                movl $3, %eax
                syscall
                movl 16(%r14), %edi         # close(fail_r)
                movl $3, %eax
                syscall
                xorl %edi, %edi             # close(0)
                movl $3, %eax
                syscall
                leaq .Lkof_proc_devnull(%rip), %rdi
                xorl %esi, %esi
                xorl %edx, %edx
                movl $2, %eax
                syscall                     # open(/dev/null) → 0
                # ── runwith hook (shell fatia B): herdado quando 0 ────────
                # O contexto (env/cwd) vive na PILHA do wrapper — o fork
                # copiou o stack, então o filho lê via kof_runwith_ctx
                # (endereço do bloco [env@0, cwd@8]; 0 = chamada comum).
                # chdir(cwd): o payload KofStr já é NUL-terminado (offset 24).
                # chdir falho → stderr honesto + byte fail + exit 127 (o
                # contrato JVM devolve Result("", msg, -1)).
                movq kof_runwith_ctx(%rip), %rax
                testq %rax, %rax
                jz .Lkof_proc_rw_noenv
                movq 8(%rax), %rax          # cwd (null = herdado)
                testq %rax, %rax
                jz .Lkof_proc_rw_nocwd
                leaq 24(%rax), %rdi
                call chdir
                testl %eax, %eax
                je .Lkof_proc_rw_nocwd
                leaq .Lkof_proc_rw_nocwd_msg(%rip), %rsi
                movl $31, %edx
                movl 12(%r14), %edi
                movl $1, %eax
                syscall                     # write(err_w, msg)
                leaq .Lkof_proc_rw_nocwd_msg(%rip), %rsi
                movl $1, %edx
                movl 20(%r14), %edi
                movl $1, %eax
                syscall                     # write(fail_w, "x", 1)
                movl $127, %edi
                movl $60, %eax
                syscall                     # _exit(127)
            .Lkof_proc_rw_nocwd:
                # env ADITIVO: setenv(k, v, 1) por entrada (nunca limpa o
                # ambiente — mesmo contrato do putAll do oráculo JVM).
                # r13/r15 estão livres aqui (o bloco do argv os re-assina);
                # sobrevivem ao setenv (callee-saved).
                movq kof_runwith_ctx(%rip), %rax
                movq 0(%rax), %r13          # env Map|null
                testq %r13, %r13
                jz .Lkof_proc_rw_noenv
                xorl %r15d, %r15d           # i
            .Lkof_proc_rw_env_loop:
                cmpl 16(%r13), %r15d
                jge .Lkof_proc_rw_noenv
                movslq %r15d, %r15
                movq 24(%r13), %rax         # array de chaves
                movq (%rax,%r15,8), %rdi    # key KofStr
                movq 32(%r13), %rax         # array de valores
                movq (%rax,%r15,8), %rsi    # value KofStr
                testq %rdi, %rdi
                jz .Lkof_proc_rw_env_next
                testq %rsi, %rsi
                jz .Lkof_proc_rw_env_next
                leaq 24(%rdi), %rdi         # payload NUL-terminado
                leaq 24(%rsi), %rsi
                movl $1, %edx               # overwrite=1 (última entrada ganha,
                call setenv                 #  igual ao putAll JVM)
            .Lkof_proc_rw_env_next:
                incl %r15d
                jmp .Lkof_proc_rw_env_loop
            .Lkof_proc_rw_noenv:
                # argv = [prog, args..., NULL] na pilha do filho
                movl 16(%r12), %r13d        # size
                leal 2(%r13), %eax          # slots = size + 2 (prog + NULL)
                movslq %eax, %rax
                shlq $3, %rax
                addq $15, %rax
                andq $-16, %rax
                subq %rax, %rsp
                movq %rsp, %r15
                movq %rbx, %rax
                testq %rax, %rax
                je .Lkof_proc_child_nullprog
                leaq 24(%rax), %rcx
                movq %rcx, 0(%r15)          # argv[0] = payload do programa
                jmp .Lkof_proc_child_argv0_ok
            .Lkof_proc_child_nullprog:
                movq $0, 0(%r15)
            .Lkof_proc_child_argv0_ok:
                xorl %ebx, %ebx             # índice (filho: rbx livre após guardar payload no argv)
            .Lkof_proc_child_argv_loop:
                cmpl 16(%r12), %ebx
                jge .Lkof_proc_child_argv_done
                movq %r12, %rdi
                movl %ebx, %esi
                call kof_list_get
                testq %rax, %rax
                je .Lkof_proc_child_argv_null
                leaq 24(%rax), %rcx
                movq %rcx, 8(%r15,%rbx,8)
                jmp .Lkof_proc_child_argv_next
            .Lkof_proc_child_argv_null:
                movq $0, 8(%r15,%rbx,8)
            .Lkof_proc_child_argv_next:
                incl %ebx
                jmp .Lkof_proc_child_argv_loop
            .Lkof_proc_child_argv_done:
                movq $0, 8(%r15,%rbx,8)     # argv[size+1] = NULL
                # execvp (libc host: resolução de PATH == ProcessBuilder)
                movq 0(%r15), %rdi
                movq %r15, %rsi
                call execvp
                # exec falhou: mensagem no stderr + byte CLOEXEC + _exit(127)
                leaq .Lkof_proc_execfail(%rip), %rsi
                movl $20, %edx
                movl 12(%r14), %edi
                movl $1, %eax
                syscall
                leaq .Lkof_proc_execfail(%rip), %rsi
                movl $1, %edx
                movl 20(%r14), %edi
                movl $1, %eax
                syscall                     # write(fail_w, "x", 1)
                movl $127, %edi
                movl $60, %eax
                syscall

                # ── PAI ────────────────────────────────────────────────
            .Lkof_proc_run_parent:
                movl %eax, 28(%r14)         # pid
                movl 4(%r14), %edi          # close(out_w)
                movl $3, %eax
                syscall
                movl 12(%r14), %edi         # close(err_w)
                movl $3, %eax
                syscall
                movl 20(%r14), %edi         # close(fail_w)
                movl $3, %eax
                syscall
            .Lkof_proc_run_poll:
                movl 0(%r14), %eax
                movl %eax, 96(%r14)
                movw $1, 100(%r14)          # POLLIN
                movl 8(%r14), %eax
                movl %eax, 104(%r14)
                movw $1, 108(%r14)
                leaq 96(%r14), %rdi
                movl $2, %esi
                movq $-1, %rdx
                movl $7, %eax
                syscall
                testw $0x19, 102(%r14)      # revents & (POLLIN|POLLERR|POLLHUP=0x19)
                jz .Lkof_proc_run_poll_err
                movl 0(%r14), %edi
                movq 112(%r14), %rsi
                movl $4096, %edx
                movl $0, %eax
                syscall
                testl %eax, %eax
                jg .Lkof_proc_run_read_out
                jle .Lkof_proc_run_eof_out
            .Lkof_proc_run_read_out:
                movslq %eax, %rdx
                leaq 48(%r14), %rdi         # trio outbuf
                movq 112(%r14), %rsi
                call .Lkof_proc_append
                jmp .Lkof_proc_run_poll
            .Lkof_proc_run_eof_out:
                movl $1, 36(%r14)
                # cai no check do err (senao eof_err nunca seta: loop eterno)
            .Lkof_proc_run_poll_err:
                testw $0x19, 110(%r14)      # revents & (POLLIN|POLLERR|POLLHUP=0x19)
                jz .Lkof_proc_run_poll_check
                movl 8(%r14), %edi
                movq 112(%r14), %rsi
                movl $4096, %edx
                movl $0, %eax
                syscall
                testl %eax, %eax
                jg .Lkof_proc_run_read_err
                jle .Lkof_proc_run_eof_err
            .Lkof_proc_run_read_err:
                movslq %eax, %rdx
                leaq 72(%r14), %rdi         # trio errbuf
                movq 112(%r14), %rsi
                call .Lkof_proc_append
                jmp .Lkof_proc_run_poll
            .Lkof_proc_run_eof_err:
                movl $1, 40(%r14)
            .Lkof_proc_run_poll_check:
                cmpl $0, 36(%r14)
                je .Lkof_proc_run_poll
                cmpl $0, 40(%r14)
                je .Lkof_proc_run_poll
                # fecha read-ends
                movl 0(%r14), %edi
                movl $3, %eax
                syscall
                movl 8(%r14), %edi
                movl $3, %eax
                syscall
                # exec-fail? read(fail_r) > 0 → spawn falhou
                movl 16(%r14), %edi
                movq 112(%r14), %rsi
                movl $1, %edx
                movl $0, %eax
                syscall
                movl %eax, %r12d            # rc do read (o close abaixo clobberia eax)
                movl 16(%r14), %edi
                movl $3, %eax
                syscall
                movl $0, 32(%r14)
                testl %r12d, %r12d
                jle .Lkof_proc_run_wait
                movl $1, 32(%r14)
            .Lkof_proc_run_wait:
                # wait4(pid, &status, 0, NULL)
                movl 28(%r14), %edi
                leaq 24(%r14), %rsi
                xorl %edx, %edx
                xorl %r10d, %r10d
                movl $61, %eax
                syscall
                # 1) copia AMBOS os stream-strings (from_literal copia — os
                # buffers globais seguem para a próxima chamada). 2) o alloc do
                # Result fica POR ULTIMO (rax = valor de retorno; kof_free
                # clobbera rax — e aqui não há free algum: os buffers de dreno
                # são globais, nunca liberados por chamada).
                movq 48(%r14), %rdi
                movq 56(%r14), %rsi
                call kof_string_from_literal
                movq %rax, %r13             # stdout
                movq 72(%r14), %rdi
                movq 80(%r14), %rsi
                call kof_string_from_literal
                movq %rax, %r15             # stderr
                # exec falhou? contrato JVM: ("", mensagem, -1) — msg no stderr
                cmpl $0, 32(%r14)
                je .Lkof_proc_run_build
                movl $-1, %ebx
                jmp .Lkof_proc_run_build2
            .Lkof_proc_run_build:
                movl 24(%r14), %ebx
                sarl $8, %ebx
                andl $255, %ebx             # WEXITSTATUS
            .Lkof_proc_run_build2:
                movl $32, %edi
                call kof_alloc
                movq %r13, 0(%rax)
                movq %r15, 8(%rax)
                movl %ebx, 16(%rax)
                movq $0, 24(%rax)
                addq $4224, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .Lkof_proc_run_fail_syscall:
                # pipe/fork falhou: ("", "process: syscall failed", -1)
                # (buffers de dreno são globais — nada a liberar)
                leaq .Lkof_proc_sysfail(%rip), %rdi
                movl $23, %esi
                call kof_string_from_literal
                movq %rax, %r13
                movl $32, %edi
                call kof_alloc
                movq $0, 0(%rax)
                movq %r13, 8(%rax)
                movl $-1, 16(%rax)
                movq $0, 24(%rax)
                addq $4224, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
