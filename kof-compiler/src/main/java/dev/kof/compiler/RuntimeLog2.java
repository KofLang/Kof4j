package dev.kof.compiler;

/**
Emissão do ASM de log2 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeLog2 {

    private RuntimeLog2() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                addb $48, %dl
                movb %dl, 3(%rbx)
                addq $4, %rbx
                movb $45, (%rbx)
                incq %rbx
                movl 36(%rsp), %eax
                call .Llog_put2_at_bx
                movb $45, (%rbx)
                incq %rbx
                movl 40(%rsp), %eax
                call .Llog_put2_at_bx
                movb $32, (%rbx)
                incq %rbx
                movl 16(%rsp), %eax
                call .Llog_put2_at_bx
                movb $58, (%rbx)
                incq %rbx
                movl 20(%rsp), %eax
                call .Llog_put2_at_bx
                movb $58, (%rbx)
                incq %rbx
                movl 24(%rsp), %eax
                call .Llog_put2_at_bx
                movb $46, (%rbx)
                incq %rbx
                movl 28(%rsp), %eax
                call .Llog_put3_at_bx
                movb $32, (%rbx)
                incq %rbx
                # label
                movq %r13, %rax
            .Llog_copy_label:
                movzbl (%rax), %ecx
                testl %ecx, %ecx
                jz .Llog_label_done
                movb %cl, (%rbx)
                incq %rax
                incq %rbx
                jmp .Llog_copy_label
            .Llog_label_done:
                movb $32, (%rbx)
                incq %rbx
                # mensagem
                testq %r14, %r14
                jnz .Llog_copy_msg
                leaq .Llog_nullmsg(%rip), %rax
            .Llog_copy_loop:
                movzbl (%rax), %ecx
                testl %ecx, %ecx
                jz .Llog_msg_done
                movb %cl, (%rbx)
                incq %rax
                incq %rbx
                jmp .Llog_copy_loop
            .Llog_copy_msg:
                movl 16(%r14), %ecx
                testl %ecx, %ecx
                jle .Llog_msg_done
                leaq 24(%r14), %rax
                movl %ecx, %edx
            .Llog_copy_bytes:
                movzbl (%rax), %ecx
                movb %cl, (%rbx)
                incq %rax
                incq %rbx
                decl %edx
                jnz .Llog_copy_bytes
            .Llog_msg_done:
                movb $10, (%rbx)
                incq %rbx
                # write(fd, buf, len)
                movq %rbx, %rdx
                subq %r15, %rdx
                movq %r15, %rsi
                movq $1, %rax
                cmpq $2, %r12
                jl .Llog_fd_stdout
                movq $2, %rdi
                jmp .Llog_do_write
            .Llog_fd_stdout:
                movq $1, %rdi
            .Llog_do_write:
                syscall
                movq %r15, %rdi
                call kof_free
            .Llog_suppressed:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # helpers que usam %rbx como cursor (dentro de kof_log_write)
            .Llog_put2_at_bx:
                pushq %rax
                movl %eax, %ecx
                xorl %edx, %edx
                movl $10, %r8d
                divl %r8d
                addb $48, %al
                movb %al, (%rbx)
                addb $48, %dl
                movb %dl, 1(%rbx)
                addq $2, %rbx
                popq %rax
                ret
            .Llog_put3_at_bx:
                pushq %rax
                movl %eax, %ecx
                xorl %edx, %edx
                movl $100, %r8d
                divl %r8d
                addb $48, %al
                movb %al, (%rbx)
                incq %rbx                    # centena gravada; dezena/unidade via put2
                movl %edx, %eax
                call .Llog_put2_at_bx
                popq %rax
                ret
            """);
    }
}