package dev.kof.compiler.runtime;

/**
 * Fatia runtime do {@code process.spawn} no Native x86-64 (host profile) —
 * linha 1 do ledger de paridade total (D-FULL-PARITY-050, fatia B).
 *
 * <p>Contrato JVM-oráculo ({@code JvmRuntimeCore}):
 * <ul>
 *   <li>{@code kof_process_spawn(program, args)} → handle opaco; falha de
 *       spawn (programa inexistente) = {@code -1}; stdin do filho de
 *       {@code /dev/null} (a JVM redireciona — por isso {@code write} é um
 *       no-op honesto nos dois lados, turning input into a live pipe é
 *       mudança de contrato, regra 6);</li>
 *   <li>{@code kof_spawn_read_line(h)} → uma linha do stdout sem o
 *       {@code \n}; EOF/morto = {@code ""};</li>
 *   <li>{@code kof_spawn_exit_code(h)} → código do filho; vivo =
 *       {@code Integer.MIN_VALUE}; handle inválido/killado = {@code -1};</li>
 *   <li>{@code kof_spawn_alive(h)} → Bool;</li>
 *   <li>{@code kof_spawn_kill(h)} → SIGKILL + reap + esquece o handle;</li>
 *   <li>{@code kof_spawn_write(h, s)} → no-op (stdin ≠ vivo, ver acima).</li>
 * </ul>
 *
 * <p>O handle é o ÍNDICE da tabela persistente em {@code .bss} (64 slots de
 * 32 B): {@code {pid, out_r, flags, exit_code}} (flags 0=livre, 1=vivo,
 * 2=morto). {@code -1} é o handle inválido. Não é objeto de heap — evita
 * varredura de GC sobre um inteiro.
 *
 * <p>Mecânica: {@code pipe2} (stdout) + {@code pipe2(O_CLOEXEC)}
 * (exec-fail, convenção posix_spawn), {@code fork} (57); o filho faz
 * {@code dup2} de stdout, stdin/stderr em {@code /dev/null}, monta o argv do
 * {@code List<String>} (payload UTF-8 NUL-terminado no offset 24) e chama
 * {@code execvp} (PATH libc == ProcessBuilder); no fail escreve 1 byte no
 * pipe CLOEXEC e {@code _exit(127)}. O pai lê o pipe CLOEXEC: byte presente =
 * exec falhou → reap + {@code -1}; EOF (pipe fechado pelo exec) = sucesso.
 */
public final class RuntimeProcessSpawn {

    private RuntimeProcessSpawn() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── kof.process.spawn (Native x86-64, host) — linha 1, fatia B ──
            .section .rodata
            .Lkof_spawn_devnull:
                .asciz "/dev/null"
            .section .bss
            .align 16
            .Lkof_spawn_tab:
                .space 2048                 # 64 slots x 32 B {pid,out_r,flags,exit_code}
            .Lkof_spawn_linebuf:
                .space 65536                # buffer de linha (readLine, 1 B por vez)
            .section .text

            # kof_spawn_write(handle, String) — no-op honesto: o stdin do filho
            # e /dev/null nos dois alvos (JVM ProcessBuilder.Redirect.from).
            .globl kof_spawn_write
            .type kof_spawn_write, @function
            kof_spawn_write:
                ret

            .globl kof_process_spawn
            .type kof_process_spawn, @function
            kof_process_spawn:
                # rdi = program (KofString), rsi = List<String> -> rax = indice| -1
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx             # program
                movq %rsi, %r12             # list
                subq $64, %rsp
                movq %rsp, %r14
                # slot livre?
                xorl %r13d, %r13d
            .Lkof_spawn_find:
                cmpl $64, %r13d
                jge .Lkof_spawn_full
                movq %r13, %rax
                shlq $5, %rax
                leaq .Lkof_spawn_tab(%rip), %rcx
                cmpl $0, 8(%rcx,%rax)
                je .Lkof_spawn_found
                incl %r13d
                jmp .Lkof_spawn_find
            .Lkof_spawn_found:
                # pipe2(out=0(%r14), 0)
                leaq 0(%r14), %rdi
                xorl %esi, %esi
                movl $293, %eax
                syscall
                testq %rax, %rax
                js .Lkof_spawn_sysfail
                # pipe2(fail=8(%r14), O_CLOEXEC)
                leaq 8(%r14), %rdi
                movl $524288, %esi
                movl $293, %eax
                syscall
                testq %rax, %rax
                js .Lkof_spawn_sysfail
                # fork
                movl $57, %eax
                syscall
                testq %rax, %rax
                js .Lkof_spawn_sysfail
                jne .Lkof_spawn_parent

                # ── FILHO ─────────────────────────────────────────────
                movl 4(%r14), %edi          # dup2(out_w → 1)
                movl $1, %esi
                movl $33, %eax
                syscall
                movl 0(%r14), %edi          # close(out_r)
                movl $3, %eax
                syscall
                movl 8(%r14), %edi          # close(fail_r)
                movl $3, %eax
                syscall
                xorl %edi, %edi             # close(0)
                movl $3, %eax
                syscall
                leaq .Lkof_spawn_devnull(%rip), %rdi
                xorl %esi, %esi
                xorl %edx, %edx
                movl $2, %eax
                syscall                     # stdin = /dev/null
                xorl %edi, %edi             # dup2(0 → 2): stderr também /dev/null
                movl $2, %esi               # (spawn não tem accessor de stderr;
                movl $33, %eax              #  a JVM deixa um pipe não drenado)
                syscall
                # argv = [prog, args..., NULL] na pilha
                movl 16(%r12), %r13d        # size
                leal 2(%r13), %eax
                movslq %eax, %rax
                shlq $3, %rax
                addq $15, %rax
                andq $-16, %rax
                subq %rax, %rsp
                movq %rsp, %r15
                movq %rbx, %rax
                testq %rax, %rax
                je .Lkof_spawn_nullprog
                leaq 24(%rax), %rcx
                movq %rcx, 0(%r15)
                jmp .Lkof_spawn_argv0ok
            .Lkof_spawn_nullprog:
                movq $0, 0(%r15)
            .Lkof_spawn_argv0ok:
                xorl %ebx, %ebx
            .Lkof_spawn_argvloop:
                cmpl 16(%r12), %ebx
                jge .Lkof_spawn_argvdone
                movq %r12, %rdi
                movl %ebx, %esi
                call kof_list_get
                testq %rax, %rax
                je .Lkof_spawn_argvnull
                leaq 24(%rax), %rcx
                movq %rcx, 8(%r15,%rbx,8)
                jmp .Lkof_spawn_argvnext
            .Lkof_spawn_argvnull:
                movq $0, 8(%r15,%rbx,8)
            .Lkof_spawn_argvnext:
                incl %ebx
                jmp .Lkof_spawn_argvloop
            .Lkof_spawn_argvdone:
                movq $0, 8(%r15,%rbx,8)
                movq 0(%r15), %rdi
                movq %r15, %rsi
                call execvp
                # exec falhou: 1 byte no pipe CLOEXEC + _exit(127)
                leaq .Lkof_spawn_devnull(%rip), %rsi
                movl $1, %edx
                movl 12(%r14), %edi
                movl $1, %eax
                syscall
                movl $127, %edi
                movl $60, %eax
                syscall

                # ── PAI ───────────────────────────────────────────────
            .Lkof_spawn_parent:
                movl %eax, 16(%r14)         # pid
                movl 4(%r14), %edi          # close(out_w)
                movl $3, %eax
                syscall
                movl 12(%r14), %edi         # close(fail_w)
                movl $3, %eax
                syscall
                # read(fail_r, buf, 1): >0 = exec falhou; 0 = exec ok
                movl 8(%r14), %edi
                leaq 24(%r14), %rsi
                movl $1, %edx
                xorl %eax, %eax
                syscall
                movl %eax, %r15d
                movl 8(%r14), %edi          # close(fail_r)
                movl $3, %eax
                syscall
                testl %r15d, %r15d
                jg .Lkof_spawn_execfail
                # sucesso: grava o slot
                movq %r13, %rax
                shlq $5, %rax
                leaq .Lkof_spawn_tab(%rip), %rcx
                movl 16(%r14), %edx
                movl %edx, 0(%rcx,%rax)
                movl 0(%r14), %edx
                movl %edx, 4(%rcx,%rax)
                movl $1, 8(%rcx,%rax)
                movl $0, 12(%rcx,%rax)
                movq %r13, %rax
                jmp .Lkof_spawn_ret
            .Lkof_spawn_execfail:
                movl 0(%r14), %edi          # close(out_r)
                movl $3, %eax
                syscall
                movl 16(%r14), %edi         # reap do filho falho
                leaq 24(%r14), %rsi
                xorl %edx, %edx
                xorl %r10d, %r10d
                movl $61, %eax
                syscall
                movq $-1, %rax
                jmp .Lkof_spawn_ret
            .Lkof_spawn_sysfail:
                movq $-1, %rax
                jmp .Lkof_spawn_ret
            .Lkof_spawn_full:
                movq $-1, %rax
            .Lkof_spawn_ret:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_spawn_alive
            .type kof_spawn_alive, @function
            kof_spawn_alive:
                # rdi = handle -> eax 0/1
                cmpq $-1, %rdi
                je .Lkof_alive_false
                cmpq $64, %rdi
                jae .Lkof_alive_false
                movq %rdi, %r8
                shlq $5, %r8
                leaq .Lkof_spawn_tab(%rip), %r9
                cmpl $1, 8(%r9,%r8)
                jne .Lkof_alive_false
                subq $32, %rsp
                movl 0(%r9,%r8), %edi
                leaq 8(%rsp), %rsi
                movl $1, %edx               # WNOHANG
                xorl %r10d, %r10d
                movl $61, %eax
                syscall
                testq %rax, %rax
                jg .Lkof_alive_reaped
                jl .Lkof_alive_neg
                movl $1, %eax               # == 0 → vivo
                addq $32, %rsp
                ret
            .Lkof_alive_reaped:
                movl 8(%rsp), %edx
                sarl $8, %edx
                andl $255, %edx
                movl %edx, 12(%r9,%r8)
                movl $2, 8(%r9,%r8)
                xorl %eax, %eax
                addq $32, %rsp
                ret
            .Lkof_alive_neg:
                movl $2, 8(%r9,%r8)         # ECHILD: já foi colhido
                xorl %eax, %eax
                addq $32, %rsp
                ret
            .Lkof_alive_false:
                xorl %eax, %eax
                ret

            .globl kof_spawn_exit_code
            .type kof_spawn_exit_code, @function
            kof_spawn_exit_code:
                # rdi = handle -> eax
                cmpq $-1, %rdi
                je .Lkof_ec_neg
                cmpq $64, %rdi
                jae .Lkof_ec_neg
                movq %rdi, %r8
                shlq $5, %r8
                leaq .Lkof_spawn_tab(%rip), %r9
                movl 8(%r9,%r8), %ecx
                cmpl $0, %ecx
                je .Lkof_ec_neg
                cmpl $2, %ecx
                je .Lkof_ec_dead
                subq $32, %rsp
                movl 0(%r9,%r8), %edi
                leaq 8(%rsp), %rsi
                movl $1, %edx
                xorl %r10d, %r10d
                movl $61, %eax
                syscall
                testq %rax, %rax
                jg .Lkof_ec_nowdead
                addq $32, %rsp
                movl $0x80000000, %eax      # MIN_VALUE: ainda vivo
                ret
            .Lkof_ec_nowdead:
                movl 8(%rsp), %edx
                sarl $8, %edx
                andl $255, %edx
                movl %edx, 12(%r9,%r8)
                movl $2, 8(%r9,%r8)
                addq $32, %rsp
                movl %edx, %eax
                ret
            .Lkof_ec_dead:
                movl 12(%r9,%r8), %eax
                ret
            .Lkof_ec_neg:
                movl $-1, %eax
                ret

            .globl kof_spawn_kill
            .type kof_spawn_kill, @function
            kof_spawn_kill:
                cmpq $-1, %rdi
                je .Lkof_kill_ret
                cmpq $64, %rdi
                jae .Lkof_kill_ret
                movq %rdi, %r8
                shlq $5, %r8
                leaq .Lkof_spawn_tab(%rip), %r9
                cmpl $0, 8(%r9,%r8)
                je .Lkof_kill_ret
                cmpl $1, 8(%r9,%r8)
                jne .Lkof_kill_close
                movl 0(%r9,%r8), %edi
                movl $9, %esi               # SIGKILL
                movl $62, %eax
                syscall
                subq $32, %rsp
                movl 0(%r9,%r8), %edi
                leaq 8(%rsp), %rsi
                xorl %edx, %edx
                xorl %r10d, %r10d
                movl $61, %eax
                syscall
                addq $32, %rsp
            .Lkof_kill_close:
                movl 4(%r9,%r8), %edi
                movl $3, %eax
                syscall
                movl $0, 8(%r9,%r8)         # esquece o handle (exitCode -1)
                movl $-1, 12(%r9,%r8)
            .Lkof_kill_ret:
                ret

            .globl kof_spawn_read_line
            .type kof_spawn_read_line, @function
            kof_spawn_read_line:
                # rdi = handle -> rax KofString (linha sem quebra; vazio em EOF)
                pushq %rbx
                pushq %r12
                pushq %r13
                cmpq $-1, %rdi
                je .Lkof_rl_empty
                cmpq $64, %rdi
                jae .Lkof_rl_empty
                movq %rdi, %r8
                shlq $5, %r8
                leaq .Lkof_spawn_tab(%rip), %r9
                cmpl $0, 8(%r9,%r8)
                je .Lkof_rl_empty
                movl 4(%r9,%r8), %r13d      # out_r
                leaq .Lkof_spawn_linebuf(%rip), %rbx
                xorl %r12d, %r12d
            .Lkof_rl_loop:
                cmpl $65535, %r12d
                jge .Lkof_rl_build
                movl %r13d, %edi
                leaq (%rbx,%r12), %rsi
                movl $1, %edx
                xorl %eax, %eax
                syscall
                testq %rax, %rax
                jle .Lkof_rl_build           # EOF/erro
                movzbl (%rbx,%r12), %eax
                cmpl $10, %eax
                je .Lkof_rl_build            # newline consumido, nao entra
                incl %r12d
                jmp .Lkof_rl_loop
            .Lkof_rl_build:
                movq %rbx, %rdi
                movl %r12d, %esi
                call kof_string_from_literal
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_rl_empty:
                leaq .Lkof_spawn_linebuf(%rip), %rdi
                xorl %esi, %esi
                call kof_string_from_literal
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
