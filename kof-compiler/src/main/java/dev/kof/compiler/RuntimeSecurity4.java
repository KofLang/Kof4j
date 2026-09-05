package dev.kof.compiler;

/**
 * Emissão do ASM de security4 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity4 {

    private RuntimeSecurity4() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .Lpv_iter:
                movzbl (%rsi), %eax
                cmpb $36, %al
                je .Lpv_iter_done
                cmpb $48, %al
                jb .Lpv_false
                cmpb $57, %al
                ja .Lpv_false
                imulq $10, %r15, %r15
                subb $48, %al
                movzbl %al, %eax
                addq %rax, %r15
                incq %rsi
                incq %r8
                cmpq $10, %r8
                ja .Lpv_false
                jmp .Lpv_iter
            .Lpv_iter_done:
                testq %r15, %r15
                jz .Lpv_false
                incq %rsi                 # pula '$' (inicio do salt b64)
                # salt b64: do inicio ate o proximo '$'
                movq %rsi, %r13           # salt start
                xorq %r9, %r9             # saltlen
            .Lpv_salt:
                movzbl (%rsi), %eax
                cmpb $36, %al
                je .Lpv_salt_done
                cmpb $0, %al
                je .Lpv_false
                incq %rsi
                incq %r9
                cmpq $40, %r9
                ja .Lpv_false
                jmp .Lpv_salt
            .Lpv_salt_done:
                movq %r9, %r14           # saltlen em registrador preservado
                subq $96, %rsp            # salt 0, dkexp 16, dkcalc 48
                # decode salt (b64 -> 0(%rsp))
                movq %rsp, %rdi
                movq %r13, %rsi
                movq %r9, %rdx
                call kof_b64_decode_internal
                cmpq $16, %rax
                jb .Lpv_bad
                # dk b64: apos o '$' do salt ate o fim (recomputa via r13/r14)
                leaq 1(%r13,%r14), %r13  # dk start = salt start + saltlen + 1
                movq 16(%r12), %rax
                leaq 24(%r12), %rcx
                addq %rcx, %rax
                subq %r13, %rax           # dk b64 len
                cmpq $10, %rax
                jb .Lpv_bad
                # decode dk (b64 -> 16(%rsp))
                leaq 16(%rsp), %rdi
                movq %r13, %rsi
                movq %rax, %rdx
                call kof_b64_decode_internal
                cmpq $16, %rax
                jb .Lpv_bad
                # dkcalc = PBKDF2(password, salt, iter)
                leaq 48(%rsp), %rdi
                movq %rbx, %rsi
                movq %rsp, %rdx
                movq $16, %rcx
                movq %r15, %r8
                call kof_sec_pbkdf2_internal
                # constant-time: 32 bytes (dkexp em 16, dkcalc em 48)
                xorq %rcx, %rcx
                xorl %r10d, %r10d
            .Lpv_ct:
                cmpq $32, %rcx
                jae .Lpv_ct_done
                movzbl 16(%rsp,%rcx), %eax
                movzbl 48(%rsp,%rcx), %edx
                xorl %edx, %eax
                orl %eax, %r10d
                incq %rcx
                jmp .Lpv_ct
            .Lpv_ct_done:
                addq $96, %rsp
                testl %r10d, %r10d
                jnz .Lpv_false
                movl $1, %eax
                jmp .Lpv_ret
            .Lpv_bad:
                addq $96, %rsp
            .Lpv_false:
                xorl %eax, %eax
            .Lpv_ret:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_password_needs_rehash(rdi=hash) → 1/0
            .globl kof_sec_password_needs_rehash
            .type kof_sec_password_needs_rehash, @function
            kof_sec_password_needs_rehash:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lpr_true
                movl 16(%rbx), %r12d
                cmpl $44, %r12d
                jb .Lpr_true
                leaq 24(%rbx), %rsi
                leaq .Lsec_pb_mid(%rip), %rcx
                xorq %r8, %r8
            .Lpr_pre:
                cmpq $14, %r8
                jae .Lpr_parse
                movzbl (%rsi,%r8), %eax
                cmpb (%rcx,%r8), %al
                jne .Lpr_true
                incq %r8
                jmp .Lpr_pre
            .Lpr_parse:
                leaq 14(%rsi), %rsi
                xorq %r9, %r9
                xorq %r10, %r10
            .Lpr_iter:
                movzbl (%rsi), %eax
                cmpb $36, %al
                je .Lpr_done
                cmpb $48, %al
                jb .Lpr_true
                cmpb $57, %al
                ja .Lpr_true
                imulq $10, %r9, %r9
                subb $48, %al
                movzbl %al, %eax
                addq %rax, %r9
                incq %rsi
                incq %r10
                cmpq $10, %r10
                ja .Lpr_true
                jmp .Lpr_iter
            .Lpr_done:
                cmpq $600000, %r9
                jae .Lpr_false
            .Lpr_true:
                movl $1, %eax
                popq %r12
                popq %rbx
                ret
            .Lpr_false:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

# ── JWT (RFC 7519, HS256 fixo) ────────────────────────────────
            # kof_b64url_encode_internal(rdi=out, rsi=src, rdx=len) → chars
            # base64url SEM padding (RFC 4648 §5)
            kof_b64url_encode_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12          # out
                movq %rsi, %r13          # src
                movq %rdx, %r14          # len
                xorq %r15, %r15          # i
                leaq .Lsec_b64url_chars(%rip), %rbx
            .Lb64u_loop:
                cmpq %r14, %r15
                jae .Lb64u_done
                leaq 1(%r15), %rdx
                cmpq %r14, %rdx
                jae .Lb64u_one
                leaq 2(%r15), %rcx
                cmpq %r14, %rcx
                jae .Lb64u_two
                movzbl (%r13,%r15), %eax
                shll $16, %eax
                movzbl (%r13,%rdx), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl (%r13,%rcx), %edx
                orl %edx, %eax
                movl %eax, %edx
                shrl $18, %eax
                movb (%rbx,%rax), %al
                movb %al, 0(%r12)
                movl %edx, %eax
                shrl $12, %eax
                andl $63, %eax
                movb (%rbx,%rax), %al
                movb %al, 1(%r12)
                movl %edx, %eax
                shrl $6, %eax
                andl $63, %eax
                movb (%rbx,%rax), %al
                movb %al, 2(%r12)
                andl $63, %edx
                movb (%rbx,%rdx), %al
                movb %al, 3(%r12)
                addq $4, %r12
                addq $3, %r15
                jmp .Lb64u_loop
            .Lb64u_two:
                movzbl (%r13,%r15), %eax
                shll $8, %eax
                movzbl (%r13,%rdx), %edx
                orl %edx, %eax
                movl %eax, %edx
                shrl $10, %eax
                movb (%rbx,%rax), %al
                movb %al, 0(%r12)
                movl %edx, %eax
                shrl $4, %eax
                andl $63, %eax
                movb (%rbx,%rax), %al
                movb %al, 1(%r12)
                movl %edx, %eax
                shll $2, %eax
                andl $63, %eax
                movb (%rbx,%rax), %al
                movb %al, 2(%r12)
                addq $3, %r12
                addq $2, %r15
                jmp .Lb64u_loop
            .Lb64u_one:
                movzbl (%r13,%r15), %eax
                movl %eax, %edx
                shrl $2, %eax
                movb (%rbx,%rax), %al
                movb %al, 0(%r12)
                andl $3, %edx
                shll $4, %edx
                movb (%rbx,%rdx), %al
                movb %al, 1(%r12)
                addq $2, %r12
                addq $1, %r15
                jmp .Lb64u_loop
            .Lb64u_done:
                movb $0, 0(%r12)
                movq %r12, %rax
                subq %rdi, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_b64url_decode_internal(rdi=out, rsi=src, rdx=len) → nbytes
            kof_b64url_decode_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12          # out
                movq %rsi, %r13          # src
                movq %rdx, %r14          # len
                xorq %rbx, %rbx          # i
                xorq %r15, %r15          # nbytes
                xorq %rcx, %rcx          # acumulador
                xorq %r9, %r9            # chars no grupo
            .Lb64ud_loop:
                cmpq %r14, %rbx
                jae .Lb64ud_end
                movzbl (%r13,%rbx), %eax
                incq %rbx
                cmpb $65, %al
                jb .Lb64ud_digit
                cmpb $90, %al
                ja .Lb64ud_lower
                subb $65, %al
                jmp .Lb64ud_got
            .Lb64ud_lower:
                cmpb $97, %al
                jb .Lb64ud_other
                cmpb $122, %al
                ja .Lb64ud_other
                subb $71, %al
                jmp .Lb64ud_got
            .Lb64ud_digit:
                cmpb $48, %al
                jb .Lb64ud_other
                cmpb $57, %al
                ja .Lb64ud_other
                addb $4, %al
                jmp .Lb64ud_got
            .Lb64ud_other:
                cmpb $45, %al            # '-'
                je .Lb64ud_minus
                cmpb $95, %al            # '_'
                jne .Lb64ud_loop
                movb $63, %al
                jmp .Lb64ud_got
            .Lb64ud_minus:
                movb $62, %al
            .Lb64ud_got:
                shlq $6, %rcx
                movzbl %al, %eax
                orq %rax, %rcx
                incq %r9
                cmpq $4, %r9
                jne .Lb64ud_loop
                movq %rcx, %rax
                shrq $16, %rax
                movb %al, 0(%r12)
                movq %rcx, %rax
                shrq $8, %rax
                movb %al, 1(%r12)
                movb %cl, 2(%r12)
                addq $3, %r12
                addq $3, %r15
                xorq %rcx, %rcx
                xorq %r9, %r9
                jmp .Lb64ud_loop
            .Lb64ud_end:
                cmpq $2, %r9
                jb .Lb64ud_out
                cmpq $3, %r9
                je .Lb64ud_three
                movq %rcx, %rax
                shrq $4, %rax
                movb %al, 0(%r12)
                incq %r12
                incq %r15
                jmp .Lb64ud_out
            .Lb64ud_three:
                movq %rcx, %rax
                shrq $10, %rax
                movb %al, 0(%r12)
                movq %rcx, %rax
                shrq $2, %rax
                movb %al, 1(%r12)
                addq $2, %r12
                addq $2, %r15
            .Lb64ud_out:
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_jwt_fail(rdi=.asciz ptr, rsi=len) → lança exceção String
            kof_jwt_fail:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %r13
                movq %rsi, %r12
                leaq 25(%r12), %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl %r12d, 16(%rbx)
                movl $0, 20(%rbx)
                xorq %rcx, %rcx
            .Ljwt_fail_cp:
                cmpq %r12, %rcx
                jge .Ljwt_fail_done
                movzbl (%r13,%rcx), %eax
                movb %al, 24(%rbx,%rcx)
                incq %rcx
                jmp .Ljwt_fail_cp
            .Ljwt_fail_done:
                movb $0, 24(%rbx,%r12)
                movq %rbx, %rdi
                call kof_throw_string
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_jwt_sign_buf(rdi=secret, rsi=h_ptr, rdx=h_len,
            #                        rcx=p_ptr, r8=p_len) → KofString* (b64url do mac)
            kof_sec_jwt_sign_buf:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12          # secret
                movq %rsi, %rbx          # h_ptr
                movq %rdx, %r13          # h_len
                movq %rcx, %r14          # p_ptr
                movq %r8, %r15           # p_len
                subq $448, %rsp          # k64 0, data 64, mac 320, sig 352
                movl 16(%r12), %eax
                cmpl $64, %eax
                jg .Ljs_khash
                xorq %rcx, %rcx
            .Ljs_kcopy:
                cmpl %eax, %ecx
                jge .Ljs_kzero
                movb 24(%r12,%rcx), %dl
                movb %dl, 0(%rsp,%rcx)
                incq %rcx
                jmp .Ljs_kcopy
            .Ljs_kzero:
                movslq %eax, %rcx
            .Ljs_kzero_loop:
                cmpq $64, %rcx
                jge .Ljs_kready
                movb $0, 0(%rsp,%rcx)
                incq %rcx
                jmp .Ljs_kzero_loop
            .Ljs_khash:
                leaq 192(%rsp), %rdi
                leaq 24(%r12), %rsi
                movslq %eax, %rdx
                call kof_sec_sha256_internal
                xorq %rcx, %rcx
            .Ljs_khash_copy:
                cmpq $32, %rcx
            """);
    }
}