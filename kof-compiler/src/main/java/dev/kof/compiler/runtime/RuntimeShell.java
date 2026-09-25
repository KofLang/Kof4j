package dev.kof.compiler.runtime;

/**
 * D-FULL-PARITY-050 row 2 — x86-64 runtime piece.
 *
 * Slice A: {@code kof_shell_argv(program, args)} = {@code [program, args...]}
 * (the JVM oracle is a prepend, JvmRuntimeCore.kof_shell_argv — no splitting).
 * Pure list surgery, zero libc — usable even in freestanding.
 *
 * Slice B (runWith): {@code kof_shell_runwith(argv, cwd, env)} mirrors the JVM
 * oracle (JvmRuntimeCore.kof_shell_runwith): argv-first, cwd ""/null =
 * inherited, env ADDITIVE (setenv overwrite=1 — never a silent env wipe),
 * empty argv = honest Result("", msg, -1). The child-side work (chdir +
 * setenv loop) lives in RuntimeProcess's child path, keyed by the two
 * runwith globals below: they are set before kof_process_run and cleared
 * after — the plain process.run path leaves them at zero, so behavior is
 * byte-identical there. Single-threaded window: the synchronous run
 * holds the globals only for the duration of the blocking call (documented
 * limitation; concurrent fibers calling runWith on the native target are
 * not supported — ledger row 2).
 * {@code kof_shell_pipeline} stays PROC001 (slice B follow-up).
 */
public final class RuntimeShell {

    private RuntimeShell() {
    }

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── kof.shell (D-FULL-PARITY-050 row 2) ────────────────────────
            .globl kof_shell_argv
            .type kof_shell_argv, @function
            kof_shell_argv:
                # (rdi=program KofString, rsi=args List|null) -> List [program, args...]
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # program
                movq %rsi, %r12             # args (pode ser null)
                call kof_list_new
                movq %rax, %r13             # novo argv
                movq %r13, %rdi
                movq %rbx, %rsi
                call kof_list_add           # argv[0] = program
                xorl %r14d, %r14d           # i = 0
            .Lkof_shell_argv_loop:
                testq %r12, %r12
                jz .Lkof_shell_argv_done
                movl 16(%r12), %eax         # args.size
                cmpl %eax, %r14d
                jge .Lkof_shell_argv_done
                movq %r12, %rdi
                movl %r14d, %esi
                call kof_list_get
                movq %r13, %rdi
                movq %rax, %rsi
                call kof_list_add
                incl %r14d
                jmp .Lkof_shell_argv_loop
            .Lkof_shell_argv_done:
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── runWith (fatia B): contexto lido no filho ─────────────────
            # O bloco cwd/env vive na PILHA do wrapper (o fork copia o stack;
            # o mark conservador do GC varre a pilha inteira). O global só
            # guarda o ENDEREÇO do bloco — um inteiro, nada para o GC comer.
            # .balign 8 É OBRIGATÓRIO: o scan de raízes estáticas anda de 8
            # em 8 a partir de kof_heap_root_start — global .quad desalinhado
            # após .asciz é INVISÍVEL ao mark (o sweep libera vivo: os buffers
            # de dreno colidiram exatamente assim — T7/T13/T14 medidos).
            .data
            .balign 8
            .globl kof_runwith_ctx
            kof_runwith_ctx:    .quad 0     # endereço do bloco [env, cwd] ou 0

            .section .rodata
            .Lkof_shell_empty:      .asciz ""
            .Lkof_shell_rw_noargv:  .asciz "kof_shell_runwith: empty argv"

            .section .text
            .globl kof_shell_runwith
            .type kof_shell_runwith, @function
            kof_shell_runwith:
                # (rdi=argv List, rsi=cwd KofStr|null, rdx=env Map|null) -> Result
                # JVM: argv vazio = Result("", msg, -1) — honesto, nunca hang.
                # kof_process_run assina (program, args): o wrapper divide o
                # argv — get(0) = programa, [1..] = lista de argumentos.
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16, %rsp              # bloco [env@0, cwd@8] — alinhado
                testq %rdi, %rdi
                jz .Lkof_shell_rw_empty
                movl 16(%rdi), %eax
                testl %eax, %eax
                jz .Lkof_shell_rw_empty
                movq %rdi, %r12             # argv
                # env ANTES da cirurgia de lista (kof_list_* clobberam rdx)
                movq %rdx, 0(%rsp)
                # cwd efetivo: null/"" = herdado (0)
                xorl %r14d, %r14d
                testq %rsi, %rsi
                jz .Lkof_shell_rw_cwd_ok
                cmpb $0, 24(%rsi)          # payload[0] == 0 → ""
                je .Lkof_shell_rw_cwd_ok
                movq %rsi, %r14
            .Lkof_shell_rw_cwd_ok:
                movq %r14, 8(%rsp)
                movq %rsp, kof_runwith_ctx(%rip)
                # program = argv.get(0)
                movq %r12, %rdi
                xorl %esi, %esi
                call kof_list_get
                movq %rax, %rbx             # program
                call kof_list_new           # rest = [argv[1], argv[2], ...]
                movq %rax, %r13
                movl $1, %r15d              # i = 1
            .Lkof_shell_rw_rest_loop:
                cmpl 16(%r12), %r15d
                jge .Lkof_shell_rw_rest_done
                movq %r12, %rdi
                movl %r15d, %esi
                call kof_list_get
                movq %r13, %rdi
                movq %rax, %rsi
                call kof_list_add
                incl %r15d
                jmp .Lkof_shell_rw_rest_loop
            .Lkof_shell_rw_rest_done:
                movq %rbx, %rdi             # (program, rest) → contrato do run
                movq %r13, %rsi
                call kof_process_run        # dreno/wait idênticos ao shell.run
                movq $0, kof_runwith_ctx(%rip)
                addq $16, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_shell_rw_empty:
                # ("", "kof_shell_runwith: empty argv", -1)
                leaq .Lkof_shell_empty(%rip), %rdi
                xorl %esi, %esi
                call kof_string_from_literal
                movq %rax, %r12             # ""
                leaq .Lkof_shell_rw_noargv(%rip), %rdi
                movl $29, %esi
                call kof_string_from_literal
                movq %rax, %rbx             # msg
                movl $32, %edi
                call kof_alloc
                movq %r12, 0(%rax)
                movq %rbx, 8(%rax)
                movl $-1, 16(%rax)
                movq $0, 24(%rax)
                addq $16, %rsp              # desfaz o bloco [env, cwd]
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ═══════════════════════════════════════════════════════════════
            # kof_shell_pipeline(rdi = List<List<String>>) -> Result
            # (fatia B x86-64: oráculo JvmRuntimeCore.kof_shell_pipeline)
            # Contrato JVM:
            #   - stages null/vazio        -> ("", "kof_shell_pipeline: no stages", -1)
            #   - estágio null/vazio       -> ("", "kof_shell_pipeline: empty stage", -1)
            #   - stdin do estágio 0       = /dev/null; demais = pipe do anterior
            #   - capturados               = stdout+stderr APENAS do ÚLTIMO
            #   - exitCode                 = do ÚLTIMO (os intermediários são esperados)
            # Divergência declarada (R7, observavelmente equivalente): stderr dos
            # estágios intermediários vai a /dev/null — no JVM é pipe nunca-lido
            # (backpressure >64K trava o waitFor; intestável por contrato). Cap
            # de 64 estágios com falha honesta (o JVM não tem cap; declarado no
            # ledger da linha 2).
            # Frame (r14 = rsp): 0 n | 8 i | 16 status | 32 chain[64] (pipes,
            # 8B cada) | 544 cap_out[2] | 552 cap_err[2] | 560 pids[64] (4B)
            # | 816 pollfds[2] | 832 eof_out | 836 eof_err | 848 trio out
            # (ptr,len,cap) | 872 trio err | 896 chunkptr — total 928.
            # ────────────────────────────────────────────────────────────────
            .section .rodata
            .Lkof_shell_pl_nostages: .asciz "kof_shell_pipeline: no stages"
            .Lkof_shell_pl_emptyst:  .asciz "kof_shell_pipeline: empty stage"
            .Lkof_shell_pl_toomany:  .asciz "kof_shell_pipeline: too many stages (max 64)"
            .Lkof_shell_pl_execfail: .asciz "process: exec failed"
            .Lkof_shell_pl_devnull:  .asciz "/dev/null"

            .section .text
            .globl kof_shell_pipeline
            .type kof_shell_pipeline, @function
            kof_shell_pipeline:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $928, %rsp
                movq %rsp, %r14
                movq %rdi, %rbx             # stages
                # ── buffers de dreno globais (mesmos do kof_process_run) ──
                cmpq $0, kof_proc_bufs_ready(%rip)
                jne .Lkof_shell_pl_bufs_ok
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
            .Lkof_shell_pl_bufs_ok:
                movq kof_proc_outbuf(%rip), %rax
                movq %rax, 848(%r14)
                movq $0, 856(%r14)
                movq $1048576, 864(%r14)
                movq kof_proc_errbuf(%rip), %rax
                movq %rax, 872(%r14)
                movq $0, 880(%r14)
                movq $1048576, 888(%r14)
                movq kof_proc_chunk(%rip), %rax
                movq %rax, 896(%r14)
                # ── validação: stages null/vazio -> ("", "no stages", -1) ──
                testq %rbx, %rbx
                jz .Lkof_shell_pl_fail_nostages
                movl 16(%rbx), %r12d        # n
                testl %r12d, %r12d
                jz .Lkof_shell_pl_fail_nostages
                cmpl $64, %r12d
                ja .Lkof_shell_pl_fail_toomany
                movl %r12d, 0(%r14)
                xorl %r13d, %r13d           # i = 0
            .Lkof_shell_pl_valid_loop:
                cmpl 0(%r14), %r13d
                jge .Lkof_shell_pl_valid_done
                movq %rbx, %rdi
                movl %r13d, %esi
                call kof_list_get
                testq %rax, %rax
                jz .Lkof_shell_pl_fail_emptyst
                cmpl $0, 16(%rax)
                je .Lkof_shell_pl_fail_emptyst
                incl %r13d
                jmp .Lkof_shell_pl_valid_loop
            .Lkof_shell_pl_valid_done:
                # ── pipes de captura do ÚLTIMO estágio ─────────────────────
                leaq 544(%r14), %rdi        # cap_out
                xorl %esi, %esi
                movl $293, %eax
                syscall
                leaq 552(%r14), %rdi        # cap_err
                xorl %esi, %esi
                movl $293, %eax
                syscall
                # ── fork por estágio ───────────────────────────────────────
                movl $0, 8(%r14)            # i = 0
            .Lkof_shell_pl_stage_loop:
                movl 8(%r14), %eax
                cmpl 0(%r14), %eax
                jge .Lkof_shell_pl_stages_done
                movl %eax, %r13d            # i
                # pipe do estágio i -> i+1 (exceto após o último)
                movl %r13d, %ecx
                incl %ecx
                cmpl 0(%r14), %ecx
                jge .Lkof_shell_pl_no_pipe
                movl %r13d, %eax
                leaq 32(%r14,%rax,8), %rdi  # &chain[i] = 32 + i*8
                xorl %esi, %esi
                movl $293, %eax
                syscall
            .Lkof_shell_pl_no_pipe:
                movl $57, %eax              # fork
                syscall
                testl %eax, %eax
                js .Lkof_shell_pl_fail_syscall
                jne .Lkof_shell_pl_parent
                # ── FILHO i ────────────────────────────────────────────────
                # stdin
                testl %r13d, %r13d
                jnz .Lkof_shell_pl_c_stdin_pipe
                leaq .Lkof_shell_pl_devnull(%rip), %rdi
                xorl %esi, %esi             # O_RDONLY
                xorl %edx, %edx
                movl $2, %eax
                syscall
                movl %eax, %edi
                xorl %esi, %esi
                movl $33, %eax
                syscall                     # dup2(devnull -> 0)
                jmp .Lkof_shell_pl_c_stdout
            .Lkof_shell_pl_c_stdin_pipe:
                movl %r13d, %eax
                decl %eax
                shll $3, %eax
                movl 32(%r14,%rax), %edi    # chain[i-1].rd
                xorl %esi, %esi
                movl $33, %eax
                syscall
            .Lkof_shell_pl_c_stdout:
                movl %r13d, %ecx
                incl %ecx
                cmpl 0(%r14), %ecx
                jge .Lkof_shell_pl_c_stdout_cap
                movl %r13d, %eax
                shll $3, %eax
                movl 36(%r14,%rax), %edi    # chain[i].wr
                movl $1, %esi
                movl $33, %eax
                syscall
                jmp .Lkof_shell_pl_c_stderr
            .Lkof_shell_pl_c_stdout_cap:
                movl 548(%r14), %edi        # cap_out.wr
                movl $1, %esi
                movl $33, %eax
                syscall
            .Lkof_shell_pl_c_stderr:
                movl %r13d, %ecx
                incl %ecx
                cmpl 0(%r14), %ecx
                jge .Lkof_shell_pl_c_stderr_cap
                # intermediário: /dev/null (divergência declarada no cabeçalho)
                leaq .Lkof_shell_pl_devnull(%rip), %rdi
                xorl %esi, %esi
                xorl %edx, %edx
                movl $2, %eax
                syscall
                movl %eax, %edi
                movl $2, %esi
                movl $33, %eax
                syscall
                jmp .Lkof_shell_pl_c_close
            .Lkof_shell_pl_c_stderr_cap:
                movl 556(%r14), %edi        # cap_err.wr
                movl $2, %esi
                movl $33, %eax
                syscall
            .Lkof_shell_pl_c_close:
                # fecha TODOS os fds de pipe (o filho só precisa dos dup2'dos)
                xorl %r15d, %r15d           # k
            .Lkof_shell_pl_c_close_loop:
                movl %r15d, %ecx
                incl %ecx
                cmpl 0(%r14), %ecx
                jge .Lkof_shell_pl_c_close_caps
                movl %r15d, %eax
                shll $3, %eax
                movl 32(%r14,%rax), %edi    # chain[k].rd
                movl $3, %eax
                syscall
                movl %r15d, %eax
                shll $3, %eax
                movl 36(%r14,%rax), %edi    # chain[k].wr
                movl $3, %eax
                syscall
                incl %r15d
                jmp .Lkof_shell_pl_c_close_loop
            .Lkof_shell_pl_c_close_caps:
                movl 544(%r14), %edi        # cap_out.rd
                movl $3, %eax
                syscall
                movl 552(%r14), %edi        # cap_err.rd
                movl $3, %eax
                syscall
                # argv = payload de stage[j] (KofStr @24), NULL-terminado —
                # buffer no HEAP (kof_alloc, (size+1)*8): o estágio pode ter
                # qualquer aridade (cap fixo de stack seria overflow).
                movq %rbx, %rdi
                movl %r13d, %esi
                call kof_list_get           # st (cópia do fork)
                movq %rax, %rbx             # st
                movl 16(%rbx), %r12d        # size
                movl %r12d, %eax
                incl %eax
                shll $3, %eax
                movl %eax, %edi
                call kof_alloc              # argv (size+1) * 8 bytes
                movq %rax, %r15             # argv base
                movl $0, 12(%r14)           # j — slot do frame (privado do
                                            # filho; kof_list_get clobbera rcx)
            .Lkof_shell_pl_c_argv_loop:
                movl 12(%r14), %ecx
                cmpl %r12d, %ecx
                jge .Lkof_shell_pl_c_argv_done
                movq %rbx, %rdi
                movl %ecx, %esi
                call kof_list_get
                leaq 24(%rax), %rax         # ENDEREÇO do payload (a casa usa
                                            # leaq — o slot é a string NUL;
                                            # movq carregaria o CONTEÚDO)
                movl 12(%r14), %ecx
                movq %rax, (%r15,%rcx,8)
                incl %ecx
                movl %ecx, 12(%r14)
                jmp .Lkof_shell_pl_c_argv_loop
            .Lkof_shell_pl_c_argv_done:
                movl 12(%r14), %ecx
                movq $0, (%r15,%rcx,8)      # argv[size] = NULL
                movq (%r15), %rdi
                movq %r15, %rsi
                call execvp
                # exec falhou (ÚLTIMO escreve no stderr capturado; intermediário
                # some no /dev/null — mesmo observável do JVM: exceção eager)
                leaq .Lkof_shell_pl_execfail(%rip), %rsi
                movl $20, %edx
                movl %r13d, %ecx
                incl %ecx
                cmpl 0(%r14), %ecx
                jne .Lkof_shell_pl_c_execfail_quiet
                movl 556(%r14), %edi        # cap_err.wr (último)
                jmp .Lkof_shell_pl_c_execfail_write
            .Lkof_shell_pl_c_execfail_quiet:
                movl $2, %edi
            .Lkof_shell_pl_c_execfail_write:
                movl $1, %eax
                syscall
                movl $127, %edi
                movl $60, %eax
                syscall
                # ── PAI ────────────────────────────────────────────────────
            .Lkof_shell_pl_parent:
                movl %eax, 560(%r14,%r13,4) # pids[i]
                # fecha as pontas que o filho herdou
                testl %r13d, %r13d
                jz .Lkof_shell_pl_p_no_prev
                movl %r13d, %eax
                decl %eax
                shll $3, %eax
                movl 32(%r14,%rax), %edi    # chain[i-1].rd
                movl $3, %eax
                syscall
            .Lkof_shell_pl_p_no_prev:
                movl %r13d, %ecx
                incl %ecx
                cmpl 0(%r14), %ecx
                jge .Lkof_shell_pl_p_next
                movl %r13d, %eax
                shll $3, %eax
                movl 36(%r14,%rax), %edi    # chain[i].wr
                movl $3, %eax
                syscall
            .Lkof_shell_pl_p_next:
                incl %r13d
                movl %r13d, 8(%r14)
                jmp .Lkof_shell_pl_stage_loop
            .Lkof_shell_pl_stages_done:
                movl 548(%r14), %edi        # cap_out.wr
                movl $3, %eax
                syscall
                movl 556(%r14), %edi        # cap_err.wr
                movl $3, %eax
                syscall
                # ── dreno: poll em cap_out.rd + cap_err.rd ─────────────────
                movq $0, 832(%r14)
                movq $0, 840(%r14)          # eof flags
            .Lkof_shell_pl_poll:
                movl 544(%r14), %eax
                movl %eax, 816(%r14)
                movw $1, 820(%r14)          # POLLIN
                movl 552(%r14), %eax
                movl %eax, 824(%r14)
                movw $1, 828(%r14)
                leaq 816(%r14), %rdi
                movl $2, %esi
                movq $-1, %rdx
                movl $7, %eax
                syscall
                testw $0x19, 822(%r14)
                jz .Lkof_shell_pl_poll_err
                movl 544(%r14), %edi
                movq 896(%r14), %rsi
                movl $4096, %edx
                movl $0, %eax
                syscall
                testl %eax, %eax
                jg .Lkof_shell_pl_read_out
                jmp .Lkof_shell_pl_eof_out
            .Lkof_shell_pl_read_out:
                movslq %eax, %rdx
                leaq 848(%r14), %rdi
                movq 896(%r14), %rsi
                call .Lkof_proc_append
                jmp .Lkof_shell_pl_poll
            .Lkof_shell_pl_eof_out:
                movl $1, 832(%r14)
            .Lkof_shell_pl_poll_err:
                testw $0x19, 830(%r14)
                jz .Lkof_shell_pl_poll_check
                movl 552(%r14), %edi
                movq 896(%r14), %rsi
                movl $4096, %edx
                movl $0, %eax
                syscall
                testl %eax, %eax
                jg .Lkof_shell_pl_read_err
                jmp .Lkof_shell_pl_eof_err
            .Lkof_shell_pl_read_err:
                movslq %eax, %rdx
                leaq 872(%r14), %rdi
                movq 896(%r14), %rsi
                call .Lkof_proc_append
                jmp .Lkof_shell_pl_poll
            .Lkof_shell_pl_eof_err:
                movl $1, 836(%r14)
            .Lkof_shell_pl_poll_check:
                cmpl $0, 832(%r14)
                je .Lkof_shell_pl_poll
                cmpl $0, 836(%r14)
                je .Lkof_shell_pl_poll
                # fecha os read-ends
                movl 544(%r14), %edi
                movl $3, %eax
                syscall
                movl 552(%r14), %edi
                movl $3, %eax
                syscall
                # ── reaps: exitCode = ÚLTIMO ───────────────────────────────
                xorl %r13d, %r13d
            .Lkof_shell_pl_reap_loop:
                cmpl 0(%r14), %r13d
                jge .Lkof_shell_pl_reaped
                movl 560(%r14,%r13,4), %edi # pids[i]
                leaq 16(%r14), %rsi
                xorl %edx, %edx
                xorl %r10d, %r10d
                movl $61, %eax              # wait4
                syscall
                incl %r13d
                jmp .Lkof_shell_pl_reap_loop
            .Lkof_shell_pl_reaped:
                movl 16(%r14), %ebx
                sarl $8, %ebx
                andl $255, %ebx             # WEXITSTATUS do último
                # ── Result (from_literal copia; buffers globais seguem) ────
                movq 848(%r14), %rdi
                movq 856(%r14), %rsi
                call kof_string_from_literal
                movq %rax, %r12             # stdout
                movq 872(%r14), %rdi
                movq 880(%r14), %rsi
                call kof_string_from_literal
                movq %rax, %r13             # stderr
                movl $32, %edi
                call kof_alloc
                movq %r12, 0(%rax)
                movq %r13, 8(%rax)
                movl %ebx, 16(%rax)
                movq $0, 24(%rax)
                addq $928, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
                # ── falhas honestas (("", msg, -1)) ────────────────────────
            .Lkof_shell_pl_fail_nostages:
                leaq .Lkof_shell_pl_nostages(%rip), %rdi
                movl $29, %esi
                jmp .Lkof_shell_pl_honest
            .Lkof_shell_pl_fail_emptyst:
                leaq .Lkof_shell_pl_emptyst(%rip), %rdi
                movl $31, %esi
                jmp .Lkof_shell_pl_honest
            .Lkof_shell_pl_fail_toomany:
                leaq .Lkof_shell_pl_toomany(%rip), %rdi
                movl $44, %esi
                jmp .Lkof_shell_pl_honest
            .Lkof_shell_pl_fail_syscall:
                leaq .Lkof_shell_pl_execfail(%rip), %rdi
                movl $20, %esi
            .Lkof_shell_pl_honest:
                call kof_string_from_literal
                movq %rax, %r12             # msg (stdout = "")
                movl $32, %edi
                call kof_alloc
                movq $0, 0(%rax)
                movq %r12, 8(%rax)
                movl $-1, 16(%rax)
                movq $0, 24(%rax)
                addq $928, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
