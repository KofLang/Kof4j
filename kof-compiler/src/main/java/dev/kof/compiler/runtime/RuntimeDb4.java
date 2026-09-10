package dev.kof.compiler.runtime;
import dev.kof.compiler.nat.NativeBackend;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.NativeRuntime;

/**
Emissão do ASM de db4 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeDb4 {

    private RuntimeDb4() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # (2 pushes apos andq: call em rsp≡0 — SSE do sqlite3_close exige)
            .globl kof_db_close
            .type kof_db_close, @function
            kof_db_close:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                call kof_db_type
                cmpl $2, %eax
                je .Ldb_close_mysql
                movq %rbx, %rdi
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_close_ret
                movq %rax, %rdi
                call sqlite3_close
                jmp .Ldb_close_ret
            .Ldb_close_mysql:
                movq %rbx, %rdi
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_close_ret
                movq %rax, %rdi
                call kof_net_close
            .Ldb_close_ret:
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_transaction(task) — BEGIN; invoca a lambda; COMMIT (ou
            # ROLLBACK + re-throw). Reusa kof_db_execute (sqlite3_exec / MySQL
            # COM_QUERY) e o EH (kf_throw_string chega no handler com %rdi=exceção
            # e a chain apontando p/ o try externo). Conexão: a última aberta
            # (.Ldb_default_handle), paridade com KOF_DB_DEFAULT no JVM.
            # bug 78 (§77 espelhado no Native, paridade com JvmConfigRuntime):
            # aninhamento — um bloco interno NA MESMA conexão NÃO BEGIN/COMMIT/
            # ROLLBACK: participa da transação externa (erro propaga p/ o
            # externo decidir). .Ldb_tx_handle = equivalente do ThreadLocal
            # KOF_DB_TX. O flag `nested` mora no slot 32 do frame de try (48B:
            # 0/8/16/24 são do unwinder) — a lambda pode clobberar callee-saved.
            .globl kof_db_transaction
            .type kof_db_transaction, @function
            kof_db_transaction:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r14
                movq %rdi, %rbx                    # task (lambda)
                movq .Ldb_default_handle(%rip), %r12   # c
                # nested = (tx_handle != 0 && tx_handle == c) — o %r14 segura o
                # flag até o frame existir (é callee-saved; kof_db_execute não
                # o toca).
                movq .Ldb_tx_handle(%rip), %rax
                xorl %r14d, %r14d                  # nested = 0
                testq %rax, %rax
                jz .Ltx_check_handle
                cmpq %rax, %r12
                jne .Ltx_check_handle
                movl $1, %r14d                     # nested = 1
            .Ltx_check_handle:
                # BEGIN (só se !nested) + marca tx ativa
                testl %r14d, %r14d
                jne .Ltx_begin_done
                testq %r12, %r12
                jz .Ltx_begin_done
                movq %r12, %rdi
                leaq .Ldb_begin_str(%rip), %rsi
                call kof_db_execute
                movq %r12, .Ldb_tx_handle(%rip)    # KOF_DB_TX.set(c)
            .Ltx_begin_done:
                # ── try start (mesmo layout de KofTryStart no NativeBackend;
                # 48B — slot 32 guarda o flag p/ o handler) ──
                subq $48, %rsp
                leaq .Ltx_rollback(%rip), %rax
                movq %rax, 0(%rsp)
                movq %rsp, 8(%rsp)
                movq %rbp, 16(%rsp)
                movq kof_exc_chain(%rip), %rcx
                movq %rcx, 24(%rsp)
                movq %rsp, kof_exc_chain(%rip)
                movl %r14d, 32(%rsp)               # nested (frame p/ o handler)
                # invoca a lambda (vtable[0] = invoke); rdi = this (a lambda,
                # onde ficam as capturas) — mesmo padrão do sched_trampoline.
                movq %rbx, %rdi
                movq 8(%rbx), %rax
                movq (%rax), %rax
                call *%rax
                # ── try end / commit ── (nested NÃO comita — o externo decide).
                # Flag re-lido do FRAME: a lambda pode ter clobberado regs.
                movq 24(%rsp), %rcx
                movq %rcx, kof_exc_chain(%rip)
                movl 32(%rsp), %r14d
                addq $48, %rsp
                testl %r14d, %r14d
                jne .Ltx_done
                movq .Ldb_default_handle(%rip), %r12
                testq %r12, %r12
                jz .Ltx_done
                movq %r12, %rdi
                leaq .Ldb_commit_str(%rip), %rsi
                call kof_db_execute
                movq $0, .Ldb_tx_handle(%rip)      # KOF_DB_TX.remove()
            .Ltx_done:
                popq %r14
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ltx_rollback:
                movq %rdi, %r14                    # preserva a exceção
                # nested vem do FRAME (unwinder deixou %rsp = base do try
                # frame — slot 8; lê ANTES do addq que o desfaz).
                movl 32(%rsp), %eax
                addq $48, %rsp                     # desfaz o subq do try
                # rollback só se !nested (nested propaga p/ o externo decidir)
                testl %eax, %eax
                jne .Ltx_rethrow
                movq .Ldb_default_handle(%rip), %r12   # re-carrega (lambda clobberou)
                testq %r12, %r12
                jz .Ltx_rethrow
                movq %r12, %rdi
                leaq .Ldb_rollback_str(%rip), %rsi
                call kof_db_execute
                movq $0, .Ldb_tx_handle(%rip)      # KOF_DB_TX.remove()
            .Ltx_rethrow:
                movq %r14, %rdi
                call kof_throw_string

            # kof_db_execute(id, sql) → sqlite3_exec
            .globl kof_db_execute
            .type kof_db_execute, @function
            kof_db_execute:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rsi, %rbx
                movq %rdi, %r12
                testq %r12, %r12
                jz .Ldb_exec0_bad
                call kof_db_type
                testl %eax, %eax
                jz .Ldb_exec0_bad
                cmpl $1, %eax
                je .Ldb_exec0_sqlite
                cmpl $2, %eax
                je .Ldb_exec0_mysql
                jmp .Ldb_exec0_bad
            .Ldb_exec0_sqlite:
                movq %r12, %rdi
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_exec0_bad
                movq %rax, %r12
                movq %r12, %rdi
                leaq 24(%rbx), %rsi
                xorl %edx, %edx
                xorl %ecx, %ecx
                xorl %r8d, %r8d
                subq $8, %rsp
                call sqlite3_exec
                addq $8, %rsp
                movl %eax, %eax
                jmp .Ldb_exec0_done
            .Ldb_exec0_mysql:
                movq %r12, %rdi
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_exec0_bad
                movq %rax, %r12
                # COM_QUERY no fd
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%rbx), %rsi
                movl 16(%rbx), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb $0, 3(%r13)
                movq %r12, %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq %r12, %rdi
                movq %r13, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_exec0_bad
                cmpb $0xFF, 4(%r13)
                je .Ldb_exec0_bad
                cmpb $0x00, 4(%r13)
                jne .Ldb_exec0_bad
                movzbl 5(%r13), %eax
                cmpb $0xFC, %al
                je .Ldb_exec0_afc
                cmpb $0xFD, %al
                je .Ldb_exec0_afd
                jmp .Ldb_exec0_done
            .Ldb_exec0_afc:
                movzwl 6(%r13), %eax
                jmp .Ldb_exec0_done
            .Ldb_exec0_afd:
                movzbl 6(%r13), %eax
                movzbl 7(%r13), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl 8(%r13), %edx
                shll $16, %edx
                orl %edx, %eax
            .Ldb_exec0_done:
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ldb_exec0_bad:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # bind helper: rdi=stmt, esi=index, rdx=valor cru (Int ou KofString*)
            kof_db_bind:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                cmpq $0x1000000, %rdx
                jae .Ldb_bind_str
                pushq %rbx
                movl %edx, %ebx
                subq $8, %rsp
                call sqlite3_bind_int
                addq $8, %rsp
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ldb_bind_str:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdx, %rbx
                movl %esi, %r12d
                movq %rdi, %r13
                movq %r13, %rdi
                movl %r12d, %esi
                leaq 24(%rbx), %rdx
                movq $-1, %rcx
                movq $-1, %r8
                subq $8, %rsp
                call sqlite3_bind_text
                addq $8, %rsp
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # Execute/query com binds: layout de pilha uniforme —
            #   6 pushes (48 bytes: rbx,r12,r13,r14,r15,rbp)
            #   +16 para &stmt (sempre), +8 extra para b4 (n>=4)
            #   rbx=id→, rbp=db, r12=sql, r13..r15=b1..b3, 16(%rsp)=b4
            .macro KOF_DB_EXEC_N n
            .globl kof_db_execute\\n
            .type kof_db_execute\\n, @function
            kof_db_execute\\n:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $40, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                .if \\n >= 1
                movq %rdx, %r13
                .endif
                .if \\n >= 2
                movq %rcx, %r14
                .endif
                .if \\n >= 3
                movq %r8, %r15
                .endif
                .if \\n >= 4
                movq %r9, 16(%rsp)
                .endif
                call kof_db_resolve
                movq %rax, 32(%rsp)
                movq %rbx, %rdi
                call kof_db_type
                cmpl $1, %eax
                je .Ldb_exec_sqlite\\n
                cmpl $2, %eax
                je .Ldb_exec_mysql\\n
                jmp .Ldb_exec_bad\\n
            .Ldb_exec_sqlite\\n:
                leaq 24(%r12), %rsi
                movq 32(%rsp), %rdi
                movq $-1, %rdx
                movq %rsp, %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq (%rsp), %r12
                .if \\n >= 1
                movq %r12, %rdi
                movl $1, %esi
                movq %r13, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 2
                movq %r12, %rdi
                movl $2, %esi
                movq %r14, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 3
                movq %r12, %rdi
                movl $3, %esi
                movq %r15, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 4
                movq %r12, %rdi
                movl $4, %esi
                movq 16(%rsp), %rdx
                call kof_db_bind
                .endif
                movq %r12, %rdi
                call sqlite3_step
                movq %r12, %rdi
                call sqlite3_finalize
                movq 32(%rsp), %rdi
                call sqlite3_changes
                jmp .Ldb_exec_done\\n
            .Ldb_exec_mysql\\n:
                .if \\n >= 1
                # binario: COM_STMT_PREPARE + COM_STMT_EXECUTE (fecha o gap prepared)
                # stash args
                leaq .Ldb_prep_args(%rip), %rax
                movq %r13, 0(%rax)
                .if \\n >= 2
                movq %r14, 8(%rax)
                .endif
                .if \\n >= 3
                movq %r15, 16(%rax)
                .endif
                .if \\n >= 4
                movq 16(%rsp), %rcx
                movq %rcx, 24(%rax)
                .endif
                movq 32(%rsp), %rdi
                movq %r12, %rsi
                call kof_db_mysql_prepare
                testl %eax, %eax
                jz .Ldb_exec_subst\\n
                movq 32(%rsp), %rdi
                movl %eax, %esi
                movl $\\n, %edx
                call kof_db_mysql_exec
                # reply: 1 packet OK/ERR — parser reuse
                movq 32(%rsp), %rdi
                leaq .Ldb_mysql_buf(%rip), %r13
                movq %r13, %rsi
                movl $16384, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_exec_bad\\n
                cmpb $0xFF, 4(%r13)
                je .Ldb_exec_bad\\n
                cmpb $0x00, 4(%r13)
                jne .Ldb_exec_bad\\n
                movzbl 5(%r13), %eax
                cmpb $0xFC, %al
                je .Ldb_exec_afc\\n
                cmpb $0xFD, %al
                je .Ldb_exec_afd\\n
                jmp .Ldb_exec_done\\n
            .Ldb_exec_subst\\n:
                # fallback: binds '?' -> literais (COM_QUERY não suporta ?)
                .endif
                .if \\n >= 1
                movq %r13, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 2
                movq %r14, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 3
                movq %r15, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 4
                movq 16(%rsp), %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                # COM_QUERY: [len 3][seq 0][0x03][sql]
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb $0, 3(%r13)
                movq 32(%rsp), %rdi
                movq %r13, %rsi
            """);
    }
}