package dev.kof.compiler;

/**
Emissão do ASM de io2 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeIo2 {

    private RuntimeIo2() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                incl %r12d
            .Lio_norm_seg_done:
                incl %r13d
                movl %r13d, %r14d
                jmp .Lio_norm_collect
            .Lio_norm_advance:
                incl %r13d
                jmp .Lio_norm_collect
            .Lio_norm_final:
                movl %r13d, %r9d
                subl %r14d, %r9d
                jz .Lio_norm_build
                cmpl $1, %r9d
                jne .Lio_norm_final_dotdot
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                je .Lio_norm_build
            .Lio_norm_final_dotdot:
                cmpl $2, %r9d
                jne .Lio_norm_final_push
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                jne .Lio_norm_final_push
                cmpb $46, 1(%rax,%r14)
                jne .Lio_norm_final_push
                testl %r12d, %r12d
                jle .Lio_norm_final_dotdot_empty
                decl %r12d
                jmp .Lio_norm_build
            .Lio_norm_final_dotdot_empty:
                cmpq $0, 512(%rsp)
                jne .Lio_norm_build
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
                jmp .Lio_norm_build
            .Lio_norm_final_push:
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
            .Lio_norm_build:
                xorl %ecx, %ecx
                cmpq $0, 512(%rsp)
                je .Lio_norm_total_segs
                incl %ecx
            .Lio_norm_total_segs:
                testl %r12d, %r12d
                jle .Lio_norm_total_done
                xorl %r9d, %r9d
            .Lio_norm_total_loop:
                cmpl %r12d, %r9d
                jge .Lio_norm_total_done
                movl 4(%rsp,%r9,8), %eax
                addl %eax, %ecx
                testl %r9d, %r9d
                je .Lio_norm_total_next
                incl %ecx
            .Lio_norm_total_next:
                incl %r9d
                jmp .Lio_norm_total_loop
            .Lio_norm_total_done:
                cmpq $0, 512(%rsp)
                jne .Lio_norm_alloc
                testl %r12d, %r12d
                jg .Lio_norm_alloc
                movl $1, %ecx
                movq $-1, 520(%rsp)
                jmp .Lio_norm_alloc2
            .Lio_norm_alloc:
                movq $0, 520(%rsp)
            .Lio_norm_alloc2:
                movslq %ecx, %rdi
                call kof_alloc
                movq %rax, %r15
                xorl %r9d, %r9d
                cmpq $0, 512(%rsp)
                je .Lio_norm_build_rel
                movb $47, (%r15)
                incl %r9d
            .Lio_norm_build_rel:
                cmpq $-1, 520(%rsp)
                jne .Lio_norm_copy_segs
                movb $46, (%r15)
                incl %r9d
                jmp .Lio_norm_done
            .Lio_norm_copy_segs:
                xorl %r10d, %r10d
            .Lio_norm_seg_loop:
                cmpl %r12d, %r10d
                jge .Lio_norm_done
                testl %r10d, %r10d
                je .Lio_norm_seg_no_sep
                movb $47, (%r15,%r9)
                incl %r9d
            .Lio_norm_seg_no_sep:
                movl (%rsp,%r10,8), %r11d
                movl 4(%rsp,%r10,8), %eax
                movl %eax, %ecx
            .Lio_norm_seg_inner:
                testl %ecx, %ecx
                jle .Lio_norm_seg_next
                leaq 24(%rbx), %rdi
                movb (%rdi,%r11), %dl
                movb %dl, (%r15,%r9)
                incl %r9d
                incq %r11
                decl %ecx
                jmp .Lio_norm_seg_inner
            .Lio_norm_seg_next:
                incl %r10d
                jmp .Lio_norm_seg_loop
            .Lio_norm_done:
                movq %r15, %rdi
                movslq %r9d, %rsi
                call kof_io_make_string
                addq $576, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            // ── File ─────────────────────────────────────────────

            .globl kof_io_file_exists
            .type kof_io_file_exists, @function
            kof_io_file_exists:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_exists_no
                movq $1, %rax
                popq %rbx
                ret
            .Lio_exists_no:
                xorl %eax, %eax
                popq %rbx
                ret

            .globl kof_io_file_is_file
            .type kof_io_file_is_file, @function
            kof_io_file_is_file:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_is_file_no
                andq $61440, %rax
                cmpq $32768, %rax
                jne .Lio_is_file_no
                movq $1, %rax
                popq %rbx
                ret
            .Lio_is_file_no:
                xorl %eax, %eax
                popq %rbx
                ret

            .globl kof_io_file_is_dir
            .type kof_io_file_is_dir, @function
            kof_io_file_is_dir:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_is_dir_no
                andq $61440, %rax
                cmpq $16384, %rax
                jne .Lio_is_dir_no
                movq $1, %rax
                popq %rbx
                ret
            .Lio_is_dir_no:
                xorl %eax, %eax
                popq %rbx
                ret

            .globl kof_io_read_text
            .type kof_io_read_text, @function
            kof_io_read_text:
                jmp kof_read_file

            .globl kof_io_write_text
            .type kof_io_write_text, @function
            kof_io_write_text:
                call kof_write_file
                testq %rax, %rax
                je .Lio_ok1
                xorl %eax, %eax
                ret
            .Lio_ok1:
                movq $1, %rax
                ret

            .globl kof_io_append_text
            .type kof_io_append_text, @function
            kof_io_append_text:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $1089, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_append_fail
                movq %rax, %rdi
                leaq 24(%r12), %rsi
                movl 16(%r12), %edx
                movq $1, %rax
                syscall
                movq %rdi, %rdi
                movq $3, %rax
                syscall
                movq $1, %rax
                popq %r12
                popq %rbx
                ret
            .Lio_append_fail:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_io_delete
            .type kof_io_delete, @function
            kof_io_delete:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_del_fail
                andq $61440, %rax
                cmpq $16384, %rax
                je .Lio_del_rmdir
                leaq 24(%rbx), %rdi
                movq $87, %rax
                syscall
                testq %rax, %rax
                je .Lio_del_ok
            .Lio_del_fail:
                xorl %eax, %eax
                popq %rbx
                ret
            .Lio_del_rmdir:
                leaq 24(%rbx), %rdi
                movq $84, %rax
                syscall
                testq %rax, %rax
                jne .Lio_del_fail
            .Lio_del_ok:
                movq $1, %rax
                popq %rbx
                ret

            .Lstr_io_size_prefix: .byte 115,105,122,101,58,32,102,105,108,101,32,110,111,116,32,102,111,117,110,100,58,32
            .globl kof_io_file_size
            .type kof_io_file_size, @function
            kof_io_file_size:
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
                js .Lio_size_err
                movq 48(%rsp), %rax
                addq $144, %rsp
                popq %rbx
                ret
            .Lio_size_err:
                leaq .Lstr_io_size_prefix(%rip), %rdi
                movl $22, %esi
                call kof_string_from_literal   # rax = KofString "size: file not found: "
                movq %rax, %rdi
                movq %rbx, %rsi                 # path (preservado em rbx)
                call kof_string_concat          # rax = prefixo + path
                movq %rax, %rdi
                call kof_throw_string           # longjmp p/ o try; panic se não houver — não retorna

            // ── Bytes ────────────────────────────────────────────

            .globl kof_io_read_bytes
            .type kof_io_read_bytes, @function
            kof_io_read_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $0, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_bytes_err
                movq %rax, %rbx
                subq $144, %rsp
                movq %rbx, %rdi
                movq %rsp, %rsi
                movq $5, %rax
                syscall
                movq 48(%rsp), %r12
                addq $144, %rsp
                movq %r12, %rdi
                call kof_alloc
                movq %rax, %r15
                movq %rbx, %rdi
                movq %r15, %rsi
                movq %r12, %rdx
                movq $0, %rax
                syscall
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                movl %r12d, %edi
                movq $4, %rsi
                call kof_array_alloc
                movq %rax, %r13
                xorq %rcx, %rcx
            .Lio_bytes_spread:
                cmpq %r12, %rcx
                jge .Lio_bytes_done
                movb (%r15,%rcx), %al
                movb %al, 24(%r13,%rcx,4)
                incq %rcx
                jmp .Lio_bytes_spread
            .Lio_bytes_done:
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_bytes_err:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── M32-gguf: leitura com offset p/ arquivos grandes (sem carregar o todo)
            # kof_io_read_range(rdi=path KofString*, rsi=offset, rdx=len) → Int[]|0
            # kof_io_read_range_path idêntica (o File receiver resolve o path em
            # compile-time; no native as duas caem no mesmo corpo)
            .globl kof_io_read_range
            .type kof_io_read_range, @function
            kof_io_read_range:
            .globl kof_io_read_range_path
            .type kof_io_read_range_path, @function
            kof_io_read_range_path:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $8, %rsp
                movq %rdi, %rbx      # path KofString*
                movq %rsi, %r14      # offset
                movq %rdx, %r15      # len
                # openat(AT_FDCWD=-100, path+24, O_RDONLY=0, 0)
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                xorl %edx, %edx
                xorl %r10d, %r10d
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_range_err
                movq %rax, %r12      # fd
                # buf = kof_alloc(len)
                movq %r15, %rdi
                call kof_alloc
                movq %rax, %r13      # buf
                # pread(fd, buf, len, offset)
                movq %r12, %rdi
                movq %r13, %rsi
                movq %r15, %rdx
                movq %r14, %r10
                movq $17, %rax
                syscall
                movq %rax, %r14      # lidos (reusa o slot do offset)
                # close(fd)
                movq %r12, %rdi
                movq $3, %rax
                syscall
                # Int[] elemsize 4
                movl %r14d, %edi
                movq $4, %rsi
                call kof_array_alloc
                movq %rax, %r12      # array (fd slot já não precisa)
                # spread byte → Int
                xorq %rcx, %rcx
            .Lio_range_spread:
                cmpq %r14, %rcx
                jge .Lio_range_done
                movb (%r13,%rcx), %al
                movzbl %al, %eax
                movl %eax, 24(%r12,%rcx,4)
                incq %rcx
                jmp .Lio_range_spread
            .Lio_range_done:
                movq %r12, %rax
                addq $8, %rsp
                popq %r15
                popq %r14
            """);
    }
}