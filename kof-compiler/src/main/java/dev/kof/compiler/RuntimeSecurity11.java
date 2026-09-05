package dev.kof.compiler;

/**
 * Emissão do ASM de security11 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity11 {

    private RuntimeSecurity11() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                ret

            # kof_sec_random_hex(rdi=nbytes) → hex string via getrandom
            .globl kof_sec_random_hex
            .type kof_sec_random_hex, @function
            kof_sec_random_hex:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx          # nbytes
                # alloc n + 24 + 1
                leaq 25(%rbx), %rdi
                call kof_alloc
                movq %rax, %r12
                movl $1, 0(%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                leal (%rbx,%rbx), %eax
                movl %eax, 16(%r12)
                movl $0, 20(%r12)
                # getrandom(buf, nbytes, 0)
                movq %r12, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                xorq %rdx, %rdx
                movq $318, %rax
                syscall
                testq %rax, %rax
                js .Lsec_random_fail
                # hex encode nbytes at 24(%r12) into 24..24+2n
                movq %rbx, %rcx
                decq %rcx
            .Lsec_random_hex_loop:
                testq %rcx, %rcx
                jl .Lsec_random_hex_done
                movzbl 24(%r12,%rcx), %eax
                movl %eax, %edx
                shrb $4, %al
                andb $0x0f, %dl
                leaq .Lsec_hex_chars(%rip), %r14
                movb (%r14,%rax), %al
                movb %al, 24(%r12,%rcx,2)
                movb (%r14,%rdx), %al
                movb %al, 25(%r12,%rcx,2)
                decq %rcx
                jmp .Lsec_random_hex_loop
            .Lsec_random_hex_done:
                movb $0, 24(%r12,%rbx,2)
                movq %r12, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_random_fail:
                movq $0, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_random_int(rdi=bound) → secure random int in [0, bound)
            .globl kof_sec_random_int
            .type kof_sec_random_int, @function
            kof_sec_random_int:
                pushq %rbx
                movq %rdi, %rbx
                testq %rbx, %rbx
                jg .Lsec_random_int_ok
                xorl %eax, %eax
                popq %rbx
                ret
            .Lsec_random_int_ok:
                # rejection sampling: 32-bit value < bound * (2^32 / bound)
                movl %ebx, %r10d
                xorl %r9d, %r9d
                movl $1, %r11d
                # range = (2^32 / bound) * bound
                movl $0xffffffff, %eax
                xorl %edx, %edx
                divl %ebx              # eax = 2^32/bound
                movl %eax, %r9d
                imull %ebx, %r9d       # range
                subq $4, %rsp
            .Lsec_random_int_retry:
                movq %rsp, %rdi
                movq $4, %rsi
                xorq %rdx, %rdx
                movq $318, %rax
                syscall
                testq %rax, %rax
                js .Lsec_random_int_fail
                movl (%rsp), %eax
                cmpl %r9d, %eax
                jae .Lsec_random_int_retry
                xorl %edx, %edx
                divl %ebx
                movl %edx, %eax
                addq $4, %rsp
                popq %rbx
                ret
            .Lsec_random_int_fail:
                addq $4, %rsp
                xorl %eax, %eax
                popq %rbx
                ret

            # kof_sec_redact(rdi=value) → masked string
            .globl kof_sec_redact
            .type kof_sec_redact, @function
            kof_sec_redact:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                cmpl $8, %r12d
                jg .Lsec_redact_long
                # return "********"
                movl $32, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $8, 16(%r13)
                movl $0, 20(%r13)
                movq $0x2a2a2a2a2a2a2a2a, %rax
                movq %rax, 24(%r13)
                movb $0, 32(%r13)
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_redact_long:
                # first4 + "********" + last4: total 16 chars
                movl $40, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $16, 16(%r13)
                movl $0, 20(%r13)
                movq $0x2a2a2a2a2a2a2a2a, %rax
                movq %rax, 28(%r13)
                movb 24(%rbx), %al
                movb %al, 24(%r13)
                movb 25(%rbx), %al
                movb %al, 25(%r13)
                movb 26(%rbx), %al
                movb %al, 26(%r13)
                movb 27(%rbx), %al
                movb %al, 27(%r13)
                movl %r12d, %r14d
                movl %r12d, %eax
                subl $4, %eax
                movl %eax, %r14d
                movb 24(%rbx,%r14), %al
                movb %al, 36(%r13)
                movl %r12d, %r14d
                subl $3, %r14d
                movb 24(%rbx,%r14), %al
                movb %al, 37(%r13)
                movl %r12d, %r14d
                subl $2, %r14d
                movb 24(%rbx,%r14), %al
                movb %al, 38(%r13)
                movl %r12d, %r14d
                subl $1, %r14d
                movb 24(%rbx,%r14), %al
                movb %al, 39(%r13)
                movb $0, 40(%r13)
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_secret_get(rdi=name) → value or 0 (null)
            # reads /proc/self/environ via syscalls (no libc)
            .globl kof_sec_secret_get
            .type kof_sec_secret_get, @function
            # kof_sec_secret_get(rdi=nome KofString*) -> rax = KofString*|0
            # Reescrita linear (mesma disciplina do kof_env_getc/kof.log):
            # ponteiros absolutos, busca "NAME=" como substring com '='
            # exigido logo apos, valor ate o NUL. Sem reuso ambiguo de
            # registradores -- o original tinha r14/r15 com dupla funcao
            # (cursor de entrada vs inicio/fim do valor) e retornava
            # fragmentos errados quando a var existia.
            kof_sec_secret_get:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16384, %rsp
                movq %rdi, %rbx              # nome KofString*
                movl 16(%rbx), %r10d         # nlen
                movslq %r10d, %r10
                testq %r10, %r10
                jle .Lssg_fail
                leaq .Lsec_environ_path(%rip), %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax                # SYS_open
                syscall
                testq %rax, %rax
                js .Lssg_fail
                movq %rax, %r12              # fd
                movq %r12, %rdi
                movq %rsp, %rsi
                movq $16384, %rdx
                xorq %rax, %rax              # SYS_read
                syscall
                movq %rax, %r13              # bytes lidos
                movq %r12, %rdi
                movq $3, %rax                # close
                syscall
                # r13 = len; r10 = nlen; rbx = nome KofString*
                xorq %r14, %r14              # i = 0
            .Lssg_scan:
                cmpq %r13, %r14
                jge .Lssg_fail
                movq %rsp, %r8
                addq %r14, %r8               # r8 = buf + i
                xorq %r9, %r9                # j = 0
            .Lssg_pcmp:
                cmpq %r10, %r9
                je .Lssg_pmatched
                movq %r14, %rax
                addq %r9, %rax
                cmpq %r13, %rax
                jge .Lssg_fail
                movzbl 24(%rbx,%r9), %eax    # name[j] (data da KofString em +24)
                movzbl (%r8,%r9), %ecx       # buf[i+j]
                cmpl %ecx, %eax
                jne .Lssg_advance
                incq %r9
                jmp .Lssg_pcmp
            .Lssg_pmatched:
                cmpb $61, 0(%r8,%r10)        # buf[i+nlen] == '=' ?
                jne .Lssg_advance
                leaq 1(%r8,%r10), %rdi       # valor = buf + i + nlen + 1
                movq %r14, %rsi
                addq %r10, %rsi
                incq %rsi                    # offset do valor
                movq %r13, %rdx
                subq %rsi, %rdx              # limite restante no buffer
                movq %rdx, %r15              # r15 = limite
                xorq %rcx, %rcx              # vallen
            .Lssg_vscan:
                cmpq %r15, %rcx
                jge .Lssg_vdone
                cmpb $0, (%rdi,%rcx)
                je .Lssg_vdone
                incq %rcx
                jmp .Lssg_vscan
            .Lssg_vdone:
                movq %rcx, %rsi              # ESI = vallen (contrato do from_literal)
                call kof_string_from_literal # rdi ja aponta para o valor
                jmp .Lssg_exit
            .Lssg_advance:
                cmpb $0, (%r8)
                je .Lssg_adv_null
                incq %r8
                incq %r14
                jmp .Lssg_advance
            .Lssg_adv_null:
                incq %r14                    # pula o NUL da entrada
                jmp .Lssg_scan
            .Lssg_fail:
                xorl %eax, %eax
            .Lssg_exit:
                addq $16384, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_secret_not_found:
                addq $65536, %rsp
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_secret_open_fail:
                addq $65536, %rsp
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_secret_get_default(rdi=name, rsi=fallback) → value or fallback
            .globl kof_sec_secret_get_default
            .type kof_sec_secret_get_default, @function
            kof_sec_secret_get_default:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx          # name
                movq %rsi, %r12          # fallback (callee-saved; rsi is clobbered)
                call kof_sec_secret_get
                testq %rax, %rax
                jnz .Lsec_secret_default_done
                movq %r12, %rax
            .Lsec_secret_default_done:
                popq %r12
                popq %rbx
                ret

            .Lsec_environ_path:
                .asciz "/proc/self/environ"
            """);
    }
}