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
 * {@code kof_shell_pipeline} (slice B2) lives in {@link RuntimeShellPipeline}.
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

            """);
        RuntimeShellPipeline.emitPipeline(sb);
    }
}
