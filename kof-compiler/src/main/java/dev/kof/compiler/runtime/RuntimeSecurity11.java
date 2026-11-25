package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de security11 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeSecurity11 {

    private RuntimeSecurity11() {}

    public static void emit(StringBuilder sb) {
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
                # alloc 24 (header) + 2n (hex) + 1 (NUL) — antes `leaq 25(%rbx)`
                # (n+25) escrevia 57 num bloco de 48 p/ n=16: overflow do
                # cabecalho do proximo bloco do arena (§292, latente p/ o bump
                # self-heal do header; real sob free-list).
                leaq 25(%rbx,%rbx), %rdi
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
                call kof_plat_random
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
                call kof_plat_random
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
                subq $16400, %rsp          # o buffer C do nome (frame alinhado)
                movq %rdi, %rbx            # nome KofString*
                movl 16(%rbx), %r10d       # nlen
                movslq %r10d, %r10
                testq %r10, %r10
                jle .Lssg_fail
                cmpq $16384, %r10          # o nome nao cabe no buffer -> nao casa
                jae .Lssg_fail
                # a costura pede um C string: copia o nome + NUL
                movq %rsp, %r11            # destino
                leaq 24(%rbx), %r12        # dados do nome
                xorq %r13, %r13            # i
            .Lssg_copy:
                cmpq %r10, %r13
                jge .Lssg_copy_done
                movzbl (%r12,%r13), %eax
                movb %al, (%r11,%r13)
                incq %r13
                jmp .Lssg_copy
            .Lssg_copy_done:
                movb $0, (%r11,%r13)
                movq %r11, %rdi
                call kof_plat_env_get      # M-6 (#1135): a busca do ambiente e' da costura
                jmp .Lssg_exit
            .Lssg_fail:
                xorl %eax, %eax
            .Lssg_exit:
                addq $16400, %rsp
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

            """);
    }
}