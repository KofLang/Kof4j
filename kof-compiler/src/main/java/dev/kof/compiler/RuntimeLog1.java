package dev.kof.compiler;

/**
Emissão do ASM de log1 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeLog1 {

    private RuntimeLog1() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .section .data
            kof_log_threshold: .quad -1
            # KofString "KOF_LOG_LEVEL" (layout: len em +16, data em +24)
            .Llog_env_kstr:
                .quad 0
                .quad 0
                .long 13
                .long 0
                .ascii "KOF_LOG_LEVEL"
            .Llog_env_name:    .asciz "KOF_LOG_LEVEL="
            .Llog_w_debug:     .asciz "debug"
            .Llog_w_info:      .asciz "info"
            .Llog_w_warn:      .asciz "warn"
            .Llog_w_warning:   .asciz "warning"
            .Llog_w_error:     .asciz "error"
            .Llog_w_off:       .asciz "off"
            .Llog_lbl_debug:   .asciz "DEBUG"
            .Llog_lbl_info:    .asciz "INFO"
            .Llog_lbl_warn:    .asciz "WARN"
            .Llog_lbl_error:   .asciz "ERROR"
            .Llog_nullmsg:     .asciz "null"
            .section .text
            .globl kof_log_debug
            .type kof_log_debug, @function
            kof_log_debug:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $0, %rdi
                leaq .Llog_lbl_debug(%rip), %rsi
                jmp kof_log_write

            .globl kof_log_info
            .type kof_log_info, @function
            kof_log_info:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $1, %rdi
                leaq .Llog_lbl_info(%rip), %rsi
                jmp kof_log_write

            .globl kof_log_warn
            .type kof_log_warn, @function
            kof_log_warn:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $2, %rdi
                leaq .Llog_lbl_warn(%rip), %rsi
                jmp kof_log_write

            .globl kof_log_error
            .type kof_log_error, @function
            kof_log_error:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $3, %rdi
                leaq .Llog_lbl_error(%rip), %rsi
                jmp kof_log_write

            # .Llog_ci_eq(rdi=candidato, rsi=bytes, edx=len) -> eax=1 se igual (case-insensitive)
            .Llog_ci_eq:
                xorl %eax, %eax
                testl %edx, %edx
                jle .Llog_ci_no
            .Llog_ci_loop:
                movzbl (%rdi), %r8d
                movzbl (%rsi), %r9d
                orb $0x20, %r8b
                orb $0x20, %r9b
                cmpl %r9d, %r8d
                jne .Llog_ci_no
                incq %rdi
                incq %rsi
                decl %edx
                jnz .Llog_ci_loop
                movl $1, %eax
            .Llog_ci_no:
                ret

            # .Llog_parse_level -> rax = threshold (lazy, uma vez por processo).
            # Autocontido: abre /proc/self/environ e procura "KOF_LOG_LEVEL="
            # (o kof_sec_secret_get espera KofString e não serve aqui).
            .Llog_parse_level:
                pushq %rbx
                pushq %r12
                pushq %r13
                subq $16384, %rsp
                leaq .Lsec_environ_path(%rip), %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax               # SYS_open
                syscall
                testq %rax, %rax
                js .Llog_pl_default
                movq %rax, %r12             # fd
                movq %r12, %rdi
                leaq 0(%rsp), %rsi
                movq $16384, %rdx
                xorq %rax, %rax             # SYS_read
                syscall
                movq %rax, %r13             # bytes lidos
                movq %r12, %rdi
                movq $3, %rax               # close
                syscall
                cmpq $15, %r13
                jl .Llog_pl_default
                xorq %rbx, %rbx             # índice no buffer
            .Llog_scan:
                movq %rsp, %r8
                addq %rbx, %r8
                leaq .Llog_env_name(%rip), %r10
                xorq %r9, %r9
            .Llog_pcmp:
                cmpq $14, %r9
                je .Llog_pfound
                leaq (%rbx,%r9), %rdx
                cmpq %r13, %rdx
                jge .Llog_pl_default
                movzbl (%r8,%r9), %eax
                movzbl (%r10,%r9), %ecx
                cmpl %ecx, %eax
                jne .Llog_padvance
                incq %r9
                jmp .Llog_pcmp
            .Llog_padvance:
                incq %rbx
                jmp .Llog_scan
            .Llog_pfound:
                leaq 14(%r8), %rsi          # valor
                xorl %edx, %edx             # len até NUL
            .Llog_vlen:
                movq %rbx, %rax
                addq $14, %rax               # salta o prefixo "KOF_LOG_LEVEL="
                addq %rdx, %rax
                cmpq %r13, %rax
                jge .Llog_vdone
                cmpb $0, (%rsp,%rax)
                je .Llog_vdone
                incq %rdx
                jmp .Llog_vlen
            .Llog_vdone:
                call .Llog_ci_word
                # dispatch pelo comprimento (debug5 info4 warn4 warning7 error5 off3)
                movl %edx, %ebx
                cmpq $7, %rdx
                jne .Llog_pl_5
                leaq .Llog_w_warning(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_warn
                jmp .Llog_pl_default
            .Llog_pl_5:
                cmpq $5, %rdx
                jne .Llog_pl_4
                movl %ebx, %edx
                leaq .Llog_w_debug(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_debug
                movl %ebx, %edx
                leaq .Llog_w_error(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_error
                jmp .Llog_pl_default
            .Llog_pl_4:
                cmpq $4, %rdx
                jne .Llog_pl_3
                movl %ebx, %edx
                leaq .Llog_w_info(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_default
                movl %ebx, %edx
                leaq .Llog_w_warn(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_warn
                jmp .Llog_pl_default
            .Llog_pl_3:
                cmpq $3, %rdx
                jne .Llog_pl_default
                movl %ebx, %edx
                leaq .Llog_w_off(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_off
                jmp .Llog_pl_default
            .Llog_pl_debug:
                movq $0, %rax
                jmp .Llog_pl_exit
            .Llog_pl_warn:
                movq $2, %rax
                jmp .Llog_pl_exit
            .Llog_pl_error:
                movq $3, %rax
                jmp .Llog_pl_exit
            .Llog_pl_off:
                movq $4, %rax
                jmp .Llog_pl_exit
            .Llog_pl_default:
                movq $1, %rax
            .Llog_pl_exit:
                addq $16384, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Llog_ci_word(rsi=valor, edx=len) -> eax=1 se é uma das palavras válidas
            .Llog_ci_word:
                cmpq $7, %rdx
                je .Llog_ciw_yes
                cmpq $5, %rdx
                je .Llog_ciw_yes
                cmpq $4, %rdx
                je .Llog_ciw_yes
                cmpq $3, %rdx
                je .Llog_ciw_yes
                xorl %eax, %eax
                ret
            .Llog_ciw_yes:
                movl $1, %eax
                ret

            # .Llog_ci_eq2(rdi=candidato lowercase, rsi=bytes, edx=len) -> eax=1 se igual
            .Llog_ci_eq2:
                pushq %rbx
                movl %edx, %ebx
                xorl %eax, %eax
                testl %ebx, %ebx
                jle .Llog_ci2_no
            .Llog_ci2_loop:
                movzbl (%rdi), %r8d
                movzbl (%rsi), %r9d
                orb $0x20, %r8b
                orb $0x20, %r9b
                cmpl %r9d, %r8d
                jne .Llog_ci2_no
                incq %rdi
                incq %rsi
                decl %ebx
                jnz .Llog_ci2_loop
                movl $1, %eax
            .Llog_ci2_no:
                popq %rbx
                ret

            # kof_log_write(rdi=level, rsi=label cstr, rdx=msg KofString|0)
            # slots locais: 0..15 timespec | 16 hh | 20 mi | 24 ss | 28 ms
            #               32 year | 36 mon | 40 day | 44 tempA | 48 tempB
            #               52 doe  | 56 era | 60 epochsec
            kof_log_write:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movq %rdi, %r12
                movq %rsi, %r13
                movq %rdx, %r14
                movq $-1, 44(%rsp)
                # threshold lazy
                movq kof_log_threshold(%rip), %rax
                cmpq $-1, %rax
                jne .Llog_have_thresh
                call .Llog_parse_level
                movq %rax, kof_log_threshold(%rip)
                movq kof_log_threshold(%rip), %rax
            .Llog_have_thresh:
                cmpq %rax, %r12
                jl .Llog_suppressed
                # clock_gettime(CLOCK_REALTIME)
                leaq 0(%rsp), %rdi
                xorq %rsi, %rsi
                movq $96, %rax
                syscall
                movq 8(%rsp), %rax           # nsec
                xorq %rdx, %rdx
                movq $1000000, %rcx
                divq %rcx
                movl %eax, 28(%rsp)          # ms
                movq 0(%rsp), %rax           # epoch sec (timespec já é em segundos)
                movq %rax, 60(%rsp)
                # hora do dia
                movq 60(%rsp), %rax
                xorq %rdx, %rdx
                movq $86400, %rcx
                divq %rcx                    # rax=dias, rdx=secs do dia
                movq %rdx, %rax
                xorq %rdx, %rdx
                movq $3600, %rcx
                divq %rcx
                movl %eax, 16(%rsp)          # hh
                movq %rdx, %rax
                xorq %rdx, %rdx
                movq $60, %rcx
                divq %rcx
                movl %eax, 20(%rsp)          # mi
                movl %edx, 24(%rsp)          # ss
                # data civil (Hinnant) — só registradores, sem slots:
                # r8=era, r9=doe, r10/r11/rdi temporários
                movq 60(%rsp), %rax
                xorq %rdx, %rdx
                movq $86400, %rcx
                divq %rcx                     # rax = dias
                addq $719468, %rax            # z
                movq $146097, %rcx
                xorq %rdx, %rdx
                divq %rcx                     # rax=era, rdx=doe
                movq %rax, %r8
                movq %rdx, %r9
                # N = doe - doe/1460 + doe/36524 - doe/146096
                movq %r9, %rax
                xorq %rdx, %rdx
                movq $1460, %rcx
                divq %rcx
                movq %rax, %r10               # doe/1460
                movq %r9, %rax
                xorq %rdx, %rdx
                movq $36524, %rcx
                divq %rcx
                movq %rax, %r11               # doe/36524
                movq %r9, %rax
                xorq %rdx, %rdx
                movq $146096, %rcx
                divq %rcx                     # rax = doe/146096
                movq %r9, %rdi
                subq %r10, %rdi
                addq %r11, %rdi
                subq %rax, %rdi               # N
                movq $365, %rcx
                xorq %rdx, %rdx
                movq %rdi, %rax
                divq %rcx                     # rax = yoe
                movq %rax, %r11               # r11 = yoe
                # ano = yoe + era*400
                movq %r11, %rax
                imulq $400, %r8
                addq %r8, %rax
                movq %rax, %r10               # r10 = year (provisório)
                # doy = doe(r9) - (365*yoe + yoe/4 - yoe/100)
                movq %r11, %rax
                imulq $365, %rax
                movq %rax, %rdi               # rdi = 365*yoe
                movq %r11, %rax
                shrq $2, %rax
                addq %rax, %rdi               # + yoe/4
                movq %r11, %rax
                xorq %rdx, %rdx
                movq $100, %rcx
                divq %rcx
                subq %rax, %rdi               # - yoe/100
                movq %r9, %rax
                subq %rdi, %rax               # doy
                movq %rax, %r9                # r9 = doy
                # mp = (5*doy + 2)/153
                imulq $5, %rax
                addq $2, %rax
                xorq %rdx, %rdx
                movq $153, %rcx
                divq %rcx                     # rax = mp
                movq %rax, %r11               # r11 = mp (yoe livre agora)
                # day = doy - (153*mp+2)/5 + 1
                imulq $153, %rax
                addq $2, %rax
                xorq %rdx, %rdx
                movq $5, %rcx
                divq %rcx                     # rax = correção
                movq %r9, %rdi                # doy
                subq %rax, %rdi
                incq %rdi                     # day
                # month = mp + 3 - 12*(mp/10)
                movq %r11, %rax
                xorq %rdx, %rdx
                movq $10, %rcx
                divq %rcx
                imulq $12, %rax
                movq %rax, %r8                # r8 = 12*(mp/10) (era livre)
                movq %r11, %rax
                addq $3, %rax
                subq %r8, %rax                # month
                movl %r10d, 32(%rsp)          # year
                movl %eax, 36(%rsp)           # month
                movl %edi, 40(%rsp)           # day
                cmpl $2, 36(%rsp)
                jg .Llog_year_ok
                incl 32(%rsp)
            .Llog_year_ok:
                # buffer = msglen + 80
                movq $4, %rdi
                testq %r14, %r14
                jz .Llog_alloc
                movl 16(%r14), %edi
                addq $4, %rdi
            .Llog_alloc:
                addq $80, %rdi
                call kof_alloc
                movq %rax, %r15
                movq %rax, %rbx
                # ano (4 dígitos)
                movl 32(%rsp), %eax
                xorl %edx, %edx
                movl $1000, %r8d
                divl %r8d
                addb $48, %al
                movb %al, (%rbx)
                # resto em edx: centena
                movl %edx, %eax
                xorl %edx, %edx
                movl $100, %r8d
                divl %r8d
                addb $48, %al
                movb %al, 1(%rbx)
                movl %edx, %eax
                xorl %edx, %edx
                movl $10, %r8d
                divl %r8d
                addb $48, %al
                movb %al, 2(%rbx)
            """);
    }
}