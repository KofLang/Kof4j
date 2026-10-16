package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
Emissão do ASM de io3 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeIo3 {

    private RuntimeIo3() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_range_err:
                addq $8, %rsp        # #848: realinha antes do throw (não retorna)
                movq %rbx, %rdi
                call kof_io_throw_not_found
            .Lio_range_inval:
                addq $8, %rsp
                movq %rbx, %rdi
                call kof_io_throw_unreadable

            .globl kof_io_write_bytes
            .type kof_io_write_bytes, @function
            kof_io_write_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $577, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_wb_err
                movq %rax, %rbx
                movl 16(%r12), %r14d
                leal 16(%r14), %edi
                call kof_alloc
                movq %rax, %r13
                xorq %rcx, %rcx
            .Lio_wb_copy:
                cmpq %r14, %rcx
                jge .Lio_wb_write
                movl 24(%r12,%rcx,4), %eax
                movb %al, (%r13,%rcx)
                incq %rcx
                jmp .Lio_wb_copy
            .Lio_wb_write:
                movq %rbx, %rdi
                movq %r13, %rsi
                movq %r14, %rdx
                movq $1, %rax
                syscall
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                movq $1, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_wb_err:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_append_bytes
            .type kof_io_append_bytes, @function
            kof_io_append_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $1089, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_ab_err
                movq %rax, %rbx
                movl 16(%r12), %r14d
                leal 16(%r14), %edi
                call kof_alloc
                movq %rax, %r13
                xorq %rcx, %rcx
            .Lio_ab_copy:
                cmpq %r14, %rcx
                jge .Lio_ab_write
                movl 24(%r12,%rcx,4), %eax
                movb %al, (%r13,%rcx)
                incq %rcx
                jmp .Lio_ab_copy
            .Lio_ab_write:
                movq %rbx, %rdi
                movq %r13, %rsi
                movq %r14, %rdx
                movq $1, %rax
                syscall
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                movq $1, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_ab_err:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            // ── Directory ────────────────────────────────────────

            .globl kof_io_dir_create
            .type kof_io_dir_create, @function
            kof_io_dir_create:
                pushq %rbx
                movq %rdi, %rbx
                leaq 24(%rbx), %rdi
                movq $493, %rsi
                movq $83, %rax
                syscall
                testq %rax, %rax
                je .Lio_mkdir_ok
                xorl %eax, %eax
                popq %rbx
                ret
            .Lio_mkdir_ok:
                movq $1, %rax
                popq %rbx
                ret

            .globl kof_io_dir_create_dirs
            .type kof_io_dir_create_dirs, @function
            kof_io_dir_create_dirs:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $520, %rsp
                movq %rdi, %rbx
                movl 16(%rbx), %r13d
                movq $1, %r12
            .Lio_dirs_scan:
                cmpq %r13, %r12
                jg .Lio_dirs_final
                leaq 24(%rbx), %rax
                cmpb $47, (%rax,%r12)
                jne .Lio_dirs_advance
                call .Lio_dirs_mkdir_prefix
                testq %rax, %rax
                je .Lio_dirs_advance
                jmp .Lio_dirs_err
            .Lio_dirs_advance:
                incq %r12
                jmp .Lio_dirs_scan
            .Lio_dirs_final:
                leaq 24(%rbx), %rdi
                movq $493, %rsi
                movq $83, %rax
                syscall
                testq %rax, %rax
                je .Lio_dirs_ok
                cmpq $-17, %rax
                jne .Lio_dirs_err
            .Lio_dirs_ok:
                movq $1, %rax
                addq $520, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_dirs_err:
                xorl %eax, %eax
                addq $520, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_dirs_mkdir_prefix:
                leaq 8(%rsp), %r8
                movq %r12, %rcx
                xorq %r9, %r9
                leaq 24(%rbx), %rsi
                movq %r8, %rdi
            .Lio_dirs_copy:
                cmpq %rcx, %r9
                jge .Lio_dirs_copy_done
                movb (%rsi,%r9), %al
                movb %al, (%rdi,%r9)
                incq %r9
                jmp .Lio_dirs_copy
            .Lio_dirs_copy_done:
                movb $0, (%rdi,%r9)
                cmpq $1, %rcx
                jle .Lio_dirs_prefix_skip
                movq %r8, %rdi
                movq $493, %rsi
                movq $83, %rax
                syscall
                testq %rax, %rax
                je .Lio_dirs_prefix_skip
                cmpq $-17, %rax
                je .Lio_dirs_prefix_skip
                movq $-1, %rax
                ret
            .Lio_dirs_prefix_skip:
                xorl %eax, %eax
                ret

            .section .rodata
            .Lio_dd_slash:
                .byte 47
            .section .text
            // D-FULL-PARITY-050 row 13 slice 14 (native x86-64, 26/09): recursivo
            // (JVM Files.walk: dir+arquivo+subdir); stat -> dir? itera getdents64 e
            // recursa em cada filho, depois rmdir; nao-dir -> unlink. Ausente/falha=0.
            .globl kof_io_dir_delete
            .type kof_io_dir_delete, @function
            kof_io_dir_delete:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, %rbx
                call kof_io_stat_mode
                testl %eax, %eax
                js .Lio_dd_zero
                andl $0xF000, %eax
                cmpl $0x4000, %eax
                jne .Lio_dd_file
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                xorl %edx, %edx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_dd_zero
                movq %rax, %r12
                movq $8192, %rdi
                call kof_alloc
                movq %rax, %r13
            .Lio_dd_loop:
                movq %r12, %rdi
                movq %r13, %rsi
                movq $8192, %rdx
                movq $217, %rax
                syscall
                testq %rax, %rax
                jle .Lio_dd_loop_done
                movq %rax, %r14
                movq %r13, %r15
            .Lio_dd_entry:
                movzwq 16(%r15), %rdx
                testq %rdx, %rdx
                je .Lio_dd_loop_done
                cmpb $46, 19(%r15)
                jne .Lio_dd_add
                cmpb $0, 20(%r15)
                je .Lio_dd_skip
                cmpb $46, 20(%r15)
                jne .Lio_dd_add
                cmpb $0, 21(%r15)
                je .Lio_dd_skip
            .Lio_dd_add:
                leaq 19(%r15), %rdi
                call kof_io_strlen
                movq %rax, %rsi
                leaq 19(%r15), %rdi
                call kof_io_make_string
                movq %rax, (%rsp)
                leaq .Lio_dd_slash(%rip), %rdi
                movq $1, %rsi
                call kof_io_make_string
                movq %rax, %rsi
                movq %rbx, %rdi
                call kof_string_concat
                movq %rax, %rdi
                movq (%rsp), %rsi
                call kof_string_concat
                movq %rax, %rdi
                call kof_io_dir_delete
            .Lio_dd_skip:
                movzwq 16(%r15), %rdx
                addq %rdx, %r15
                leaq (%r13,%r14), %rax
                cmpq %rax, %r15
                jb .Lio_dd_entry
                jmp .Lio_dd_loop
            .Lio_dd_loop_done:
                movq %r12, %rdi
                movq $3, %rax
                syscall
                leaq 24(%rbx), %rdi
                movq $84, %rax
                syscall
                testq %rax, %rax
                jne .Lio_dd_zero
                movq $1, %rax
                jmp .Lio_dd_done
            .Lio_dd_file:
                leaq 24(%rbx), %rdi
                movq $87, %rax
                syscall
                testq %rax, %rax
                jne .Lio_dd_zero
                movq $1, %rax
                jmp .Lio_dd_done
            .Lio_dd_zero:
                xorl %eax, %eax
            .Lio_dd_done:
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_dir_list
            .type kof_io_dir_list, @function
            kof_io_dir_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32768, %rsp
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $65536, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_list_err
                movq %rax, %rbx
                movq %rsp, %r13
                call kof_list_new
                movq %rax, %r12
            .Lio_list_loop:
                movq %rbx, %rdi
                movq %r13, %rsi
                movq $32768, %rdx
                movq $217, %rax
                syscall
                testq %rax, %rax
                jle .Lio_list_done
                movq %rax, %r14
                movq %r13, %r15
            .Lio_list_entry:
                movzwq 16(%r15), %rdx
                testq %rdx, %rdx
                je .Lio_list_next_buf
                cmpb $46, 19(%r15)
                jne .Lio_list_add
                cmpb $0, 20(%r15)
                je .Lio_list_skip
                cmpb $46, 20(%r15)
                jne .Lio_list_add
                cmpb $0, 21(%r15)
                je .Lio_list_skip
            .Lio_list_add:
                leaq 19(%r15), %rdi
                call kof_io_strlen
                movq %rax, %rsi
                leaq 19(%r15), %rdi
                call kof_io_make_string
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_list_add
            .Lio_list_skip:
                movzwq 16(%r15), %rdx
                addq %rdx, %r15
                leaq (%r13,%r14), %rax
                cmpq %rax, %r15
                jb .Lio_list_entry
                jmp .Lio_list_loop
            .Lio_list_next_buf:
                jmp .Lio_list_done
            .Lio_list_done:
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                call .Lio_list_sort
                movq %r12, %rax
                addq $32768, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_list_sort:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r11
                movq %r12, %rbx
                movl 16(%rbx), %r12d
                movq 24(%rbx), %r11
                movq $1, %r13
            .Lio_sort_i:
                cmpl %r12d, %r13d
                jge .Lio_sort_done
                movq (%r11,%r13,8), %r15
                movl %r13d, %r14d
            .Lio_sort_j:
                testl %r14d, %r14d
                jle .Lio_sort_place
                movq -8(%r11,%r14,8), %rdi
                movq %r15, %rsi
                call .Lio_str_less
                testq %rax, %rax
                jne .Lio_sort_place
                movq -8(%r11,%r14,8), %rax
                movq %rax, (%r11,%r14,8)
                decl %r14d
                jmp .Lio_sort_j
            .Lio_sort_place:
                movq %r15, (%r11,%r14,8)
                incq %r13
                jmp .Lio_sort_i
            .Lio_sort_done:
                popq %r11
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_str_less:
                movl 16(%rdi), %ecx
                movl 16(%rsi), %r8d
                xorl %r9d, %r9d
            .Lio_less_loop:
                cmpl %ecx, %r9d
                jge .Lio_less_left_done
                cmpl %r8d, %r9d
                jge .Lio_less_longer
                movzbl 24(%rdi,%r9), %eax
                movzbl 24(%rsi,%r9), %r10d
                cmpl %r10d, %eax
                jl .Lio_less_true
                jg .Lio_less_false
                incl %r9d
                jmp .Lio_less_loop
            .Lio_less_left_done:
                cmpl %r8d, %r9d
                jl .Lio_less_true
            .Lio_less_false:
                xorl %eax, %eax
                ret
            .Lio_less_longer:
                xorl %eax, %eax
                ret
            .Lio_less_true:
                movq $1, %rax
                ret
            .Lio_list_err:
                xorl %eax, %eax
                addq $32768, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }
}