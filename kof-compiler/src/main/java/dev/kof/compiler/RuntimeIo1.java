package dev.kof.compiler;

/**
Emissão do ASM de io1 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeIo1 {

    private RuntimeIo1() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lio_slash: .asciz "/"
            .Lio_dot: .asciz "."
            .Lio_dotdot: .asciz ".."
            .Lio_read_err: .asciz "Runtime error: cannot read file"
            .section .text

            // kof_io_make_string(data_ptr, len) → new KofString
            .globl kof_io_make_string
            .type kof_io_make_string, @function
            kof_io_make_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                movq %r12, %rdx
                call kof_memcpy
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            // kof_io_strlen(rdi) → byte length of a NUL-terminated string
            .globl kof_io_strlen
            .type kof_io_strlen, @function
            kof_io_strlen:
                xorq %rax, %rax
            .Lio_strlen_loop:
                cmpb $0, (%rdi,%rax)
                je .Lio_strlen_done
                incq %rax
                jmp .Lio_strlen_loop
            .Lio_strlen_done:
                ret

            // kof_io_last_slash(str) → index of last '/' or -1
            .globl kof_io_last_slash
            .type kof_io_last_slash, @function
            kof_io_last_slash:
                movl 16(%rdi), %eax
                decl %eax
            .Lio_last_slash_loop:
                cmpl $0, %eax
                jl .Lio_last_slash_done
                leaq 24(%rdi), %rcx
                cmpb $47, (%rcx,%rax)
                je .Lio_last_slash_done
                decl %eax
                jmp .Lio_last_slash_loop
            .Lio_last_slash_done:
                ret

            // kof_io_strip_trailing(str) → effective length (trailing '/' removed, root kept)
            .globl kof_io_strip_trailing
            .type kof_io_strip_trailing, @function
            kof_io_strip_trailing:
                movl 16(%rdi), %eax
            .Lio_strip_loop:
                cmpl $1, %eax
                jle .Lio_strip_done
                leaq 24(%rdi), %rcx
                cmpb $47, -1(%rcx,%rax)
                jne .Lio_strip_done
                decl %eax
                jmp .Lio_strip_loop
            .Lio_strip_done:
                ret

            // kof_io_stat_mode(str) → st_mode or -1
            .globl kof_io_stat_mode
            .type kof_io_stat_mode, @function
            kof_io_stat_mode:
                pushq %rbx
                subq $144, %rsp
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq %rsp, %rdx
                xorq %r10, %r10
                movq $262, %rax
                syscall
                testq %rax, %rax
                js .Lio_stat_err
                movl 24(%rsp), %eax
                addq $144, %rsp
                popq %rbx
                ret
            .Lio_stat_err:
                movq $-1, %rax
                addq $144, %rsp
                popq %rbx
                ret

            // ── Path ──────────────────────────────────────────────

            .globl kof_io_file_name
            .type kof_io_file_name, @function
            kof_io_file_name:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                call kof_io_strip_trailing
                movl %eax, %r13d
                movl %eax, %r12d
                decl %r12d
            .Lio_name_find:
                cmpl $0, %r12d
                jl .Lio_name_no_slash
                leaq 24(%rbx), %rcx
                cmpb $47, (%rcx,%r12)
                je .Lio_name_found
                decl %r12d
                jmp .Lio_name_find
            .Lio_name_found:
                leal 1(%r12), %eax
                movl %r13d, %ecx
                subl %eax, %ecx
                jg .Lio_name_sub
                movq %rbx, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_name_sub:
                leaq 24(%rbx), %rdi
                addq %r12, %rdi
                incq %rdi
                movslq %ecx, %rsi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_name_no_slash:
                movslq %r13d, %rsi
                leaq 24(%rbx), %rdi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_file_name
            .type kof_io_path_file_name, @function
            kof_io_path_file_name:
                jmp kof_io_file_name

            .globl kof_io_path_parent
            .type kof_io_path_parent, @function
            kof_io_path_parent:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                call kof_io_strip_trailing
                movl %eax, %r13d
                movl %eax, %r12d
                decl %r12d
            .Lio_parent_find:
                cmpl $0, %r12d
                jl .Lio_parent_none
                leaq 24(%rbx), %rcx
                cmpb $47, (%rcx,%r12)
                je .Lio_parent_found
                decl %r12d
                jmp .Lio_parent_find
            .Lio_parent_found:
                testl %r12d, %r12d
                jne .Lio_parent_prefix
                leaq .Lio_slash(%rip), %rdi
                movq $1, %rsi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_parent_prefix:
                leaq 24(%rbx), %rdi
                movslq %r12d, %rsi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_parent_none:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_extension
            .type kof_io_path_extension, @function
            kof_io_path_extension:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                call kof_io_strip_trailing
                movl %eax, %r13d
                movl %eax, %r14d
                decl %r14d
            .Lio_ext_find_slash:
                cmpl $0, %r14d
                jl .Lio_ext_dot_from
                leaq 24(%rbx), %rcx
                cmpb $47, (%rcx,%r14)
                je .Lio_ext_dot_from
                decl %r14d
                jmp .Lio_ext_find_slash
            .Lio_ext_dot_from:
                movl %r13d, %r12d
                decl %r12d
            .Lio_ext_find_dot:
                cmpl %r14d, %r12d
                jle .Lio_ext_empty
                leaq 24(%rbx), %rcx
                cmpb $46, (%rcx,%r12)
                je .Lio_ext_found
                decl %r12d
                jmp .Lio_ext_find_dot
            .Lio_ext_found:
                movl %r13d, %ecx
                subl %r12d, %ecx
                decl %ecx
                jg .Lio_ext_sub
            .Lio_ext_empty:
                xorl %esi, %esi
                leaq .Lio_dot(%rip), %rdi
                call kof_io_make_string
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_ext_sub:
                leaq 24(%rbx), %rdi
                addq %r12, %rdi
                incq %rdi
                movslq %ecx, %rsi
                call kof_io_make_string
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_resolve
            .type kof_io_path_resolve, @function
            kof_io_path_resolve:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                cmpb $47, 24(%r12)
                je .Lio_resolve_b
                cmpl $0, 16(%rbx)
                je .Lio_resolve_b
                movl 16(%rbx), %eax
                leaq 24(%rbx), %rcx
                cmpb $47, -1(%rcx,%rax)
                je .Lio_resolve_concat
                leaq .Lio_slash(%rip), %rdi
                movq $1, %rsi
                call kof_string_from_literal
                movq %rax, %r13
                movq %rbx, %rdi
                movq %r13, %rsi
                call kof_string_concat
                movq %rax, %r14
                movq %r14, %rdi
                movq %r12, %rsi
                call kof_string_concat
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_resolve_concat:
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_string_concat
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_resolve_b:
                movq %r12, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_is_absolute
            .type kof_io_path_is_absolute, @function
            kof_io_path_is_absolute:
                cmpl $0, 16(%rdi)
                jle .Lio_abs_no
                cmpb $47, 24(%rdi)
                jne .Lio_abs_no
                movq $1, %rax
                ret
            .Lio_abs_no:
                xorl %eax, %eax
                ret

            .globl kof_io_path_to_absolute
            .type kof_io_path_to_absolute, @function
            kof_io_path_to_absolute:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                cmpb $47, 24(%rbx)
                je .Lio_toabs_ret
                subq $4096, %rsp
                movq %rsp, %rdi
                movq $4096, %rsi
                movq $79, %rax
                syscall
                testq %rax, %rax
                js .Lio_toabs_err
                movq %rsp, %rdi
                call kof_io_strlen
                movq %rax, %rsi
                movq %rsp, %rdi
                call kof_io_make_string
                movq %rax, %rdi
                movq %rbx, %rsi
                call kof_io_path_resolve
                movq %rax, %r12
                addq $4096, %rsp
                movq %r12, %rax
                popq %r12
                popq %rbx
                ret
            .Lio_toabs_err:
                addq $4096, %rsp
            .Lio_toabs_ret:
                movq %rbx, %rax
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_normalize
            .type kof_io_path_normalize, @function
            kof_io_path_normalize:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $576, %rsp
                movq %rdi, %rbx
                movq $0, 512(%rsp)
                cmpl $0, 16(%rbx)
                je .Lio_norm_scan
                cmpb $47, 24(%rbx)
                jne .Lio_norm_scan
                movq $1, 512(%rsp)
            .Lio_norm_scan:
                xorl %r12d, %r12d
                xorl %r13d, %r13d
                xorl %r14d, %r14d
            .Lio_norm_collect:
                movl 16(%rbx), %ecx
                cmpl %ecx, %r13d
                jge .Lio_norm_final
                leaq 24(%rbx), %rax
                cmpb $47, (%rax,%r13)
                jne .Lio_norm_advance
                movl %r13d, %r9d
                subl %r14d, %r9d
                jz .Lio_norm_seg_done
                cmpl $1, %r9d
                jne .Lio_norm_check_dotdot
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                je .Lio_norm_seg_done
            .Lio_norm_check_dotdot:
                cmpl $2, %r9d
                jne .Lio_norm_push
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                jne .Lio_norm_push
                cmpb $46, 1(%rax,%r14)
                jne .Lio_norm_push
                testl %r12d, %r12d
                jle .Lio_norm_dotdot_empty
                decl %r12d
                jmp .Lio_norm_seg_done
            .Lio_norm_dotdot_empty:
                cmpq $0, 512(%rsp)
                jne .Lio_norm_seg_done
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
                jmp .Lio_norm_seg_done
            .Lio_norm_push:
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
            """);
    }
}