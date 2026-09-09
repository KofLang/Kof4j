package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de tempo (kof_time e kof_io_time) do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeTime {

    private RuntimeTime() {}

    public static void emitIoTimeFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lstr_read_err: .asciz "Runtime error: cannot read"
            .section .text
            .globl kof_now
            .type kof_now, @function
            kof_now:
                subq $16, %rsp
                movq %rsp, %rdi
                xorq %rsi, %rsi
                movq $96, %rax
                syscall
                movq 8(%rsp), %rax
                xorq %rdx, %rdx
                movq $1000, %r8
                divq %r8
                movq 0(%rsp), %rcx
                imulq $1000, %rcx
                addq %rcx, %rax
                addq $16, %rsp
                ret

            .globl kof_read_line
            .type kof_read_line, @function
            kof_read_line:
                pushq %rbx
                pushq %r12
                subq $512, %rsp
                movq %rsp, %rbx
                xorq %r12, %r12
            .Lkof_read_line_loop:
                cmpq $511, %r12
                jge .Lkof_read_line_done
                movq $0, %rax
                movq $0, %rdi
                movq $1, %rsi
                movq %rbx, %rdx
                addq %r12, %rdx
                syscall
                testq %rax, %rax
                jle .Lkof_read_line_eof
                movq %rbx, %rcx
                addq %r12, %rcx
                cmpb $10, (%rcx)
                je .Lkof_read_line_done
                incq %r12
                jmp .Lkof_read_line_loop
            .Lkof_read_line_eof:
                # EOF sem nenhum byte lido -> null (paridade com o JVM,
                # que devolve null no fim do stdin); linha parcial -> devolve
                cmpq $0, %r12
                jne .Lkof_read_line_done
                xorl %eax, %eax
                addq $512, %rsp
                popq %r12
                popq %rbx
                ret
            .Lkof_read_line_done:
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %rcx
                movl $1, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl %r12d, 16(%rcx)
                movl $0, 20(%rcx)
                movq %rcx, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                movl %r12d, %edx
                call kof_memcpy
                movq %rcx, %r13
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                addq $512, %rsp
                popq %r12
                popq %rbx
                ret

            .globl kof_read_file
            .type kof_read_file, @function
            kof_read_file:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $0, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lkof_read_file_err
                movq %rax, %r12
                subq $144, %rsp
                movq %r12, %rdi
                movq %rsp, %rsi
                movq $5, %rax
                syscall
                movq 48(%rsp), %r13
                addq $144, %rsp
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %r14
                movl $1, 0(%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %r13d, 16(%r14)
                movl $0, 20(%r14)
                movq %r12, %rdi
                movq %r14, %rsi
                addq $24, %rsi
                movl %r13d, %edx
                movq $0, %rax
                syscall
                movq %r12, %rdi
                movq $3, %rax
                syscall
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_read_file_err:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_write_file
            .type kof_write_file, @function
            kof_write_file:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $577, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lkof_write_file_fail
                movq %rax, %rdi
                leaq 24(%r12), %rsi
                movl 16(%r12), %edx
                movq $1, %rax
                syscall
                movq %rdi, %rdi
                movq $3, %rax
                syscall
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret
            .Lkof_write_file_fail:
                movq $-1, %rax
                popq %r12
                popq %rbx
                ret
            """);
    }

    public static void emitKofTimeFunctions(StringBuilder sb) {
        sb.append("""
            .globl kof_time_now
            .type kof_time_now, @function
            kof_time_now:
                jmp kof_now

            # ── kof.time (STDLIB S7-wedge) — calendário civil ──────────────
            # kof_time_isLeapYear(edi=year) -> 0/1. Gregório: %4 && (!%100 || %400).
            # Como year%4==0 => k=year/4; year%100==0 <=> k%25==0; year%400==0 <=> k%100==0.
            .globl kof_time_isLeapYear
            .type kof_time_isLeapYear, @function
            kof_time_isLeapYear:
                pushq %rbx
                pushq %rcx
                cmpl $1, %edi          # ano < 1 (Gregório): não bissexto (SIGNED)
                jl   .Lly_false
                movl %edi, %eax
                movl %edi, %ebx
                andl $3, %eax
                testl %eax, %eax
                jne .Lly_false
                shrl $2, %ebx          # k = year/4
                movl %ebx, %eax
                xorl %edx, %edx
                movl $25, %ecx
                divl %ecx             # edx = k%25
                testl %edx, %edx
                jne .Lly_true         # year%100 != 0 => bissexto
                movl %ebx, %eax
                xorl %edx, %edx
                movl $100, %ecx
                divl %ecx             # edx = k%100
                testl %edx, %edx
                jne .Lly_false        # year%400 != 0 => não
                jmp .Lly_true
            .Lly_true:
                movl $1, %eax
                popq %rcx
                popq %rbx
                ret
            .Lly_false:
                xorl %eax, %eax
                popq %rcx
                popq %rbx
                ret

            # kof_time_daysInMonth(edi=year, esi=month) -> dias (mês inválido => 0)
            .globl kof_time_daysInMonth
            .type kof_time_daysInMonth, @function
            kof_time_daysInMonth:
                pushq %rbx
                pushq %rcx
                pushq %rdx
                cmpl $1, %edi          # ano < 1 => 0 (paridade JVM/JS; SIGNED)
                jl   .Ldim_zero
                movl %esi, %ebx
                cmpl $1, %ebx
                jl .Ldim_zero
                cmpl $12, %ebx
                jg .Ldim_zero
                cmpl $2, %ebx
                je .Ldim_feb
                cmpl $4, %ebx
                je .Ldim_30
                cmpl $6, %ebx
                je .Ldim_30
                cmpl $9, %ebx
                je .Ldim_30
                cmpl $11, %ebx
                je .Ldim_30
                movl $31, %eax
                jmp .Ldim_done
            .Ldim_30:
                movl $30, %eax
                jmp .Ldim_done
            .Ldim_feb:
                movl %edi, %eax
                call kof_time_isLeapYear
                testl %eax, %eax
                jne .Ldim_29
                movl $28, %eax
                jmp .Ldim_done
            .Ldim_29:
                movl $29, %eax
                jmp .Ldim_done
            .Ldim_zero:
                xorl %eax, %eax
            .Ldim_done:
                popq %rdx
                popq %rcx
                popq %rbx
                ret

            # ── kof.time (S7.2) — serial civil (Hinnant days-from-civil) ───
            # Válido: 1<=ano<=9999, 1<=mês<=12, 1<=dia<=dim => senão 0
            # (paridade exata JVM/JS/riscv). ano>=1 => dividendos >=0 => divl.
            # interno .Lkd_valid(edi=y,esi=m,edx=d) -> eax 0/1
            .Lkd_valid:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %ebx
                movl %esi, %r12d
                movl %edx, %r13d
                cmpl $1, %ebx
                jl .Lkd_vfalse
                cmpl $9999, %ebx
                jg .Lkd_vfalse
                cmpl $1, %r12d
                jl .Lkd_vfalse
                cmpl $12, %r12d
                jg .Lkd_vfalse
                cmpl $1, %r13d
                jl .Lkd_vfalse
                call kof_time_daysInMonth       # edi/esi vivos: eax = dim
                cmpl %eax, %r13d
                jg .Lkd_vfalse                  # d > dim
                movl $1, %eax
                jmp .Lkd_vdone
            .Lkd_vfalse:
                xorl %eax, %eax
            .Lkd_vdone:
                popq %r13
                popq %r12
                popq %rbx
                ret

            # interno .Lkd_epoch(edi=y,esi=m,edx=d) -> eax (pré-validado)
            .Lkd_epoch:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movl %edx, %ebx                 # d
                movl %edi, %eax
                cmpl $2, %esi
                jg .Lkd_e_nod
                decl %eax                       # y2 = y-1 (m<=2)
            .Lkd_e_nod:
                xorl %edx, %edx
                movl $400, %ecx
                divl %ecx                       # eax=era edx=yoe
                movl %eax, %r12d
                movl %edx, %r13d
                movl %r13d, %eax
                imull $1461, %eax, %eax         # yoe*1461 (= yoe*365+yoe/4 exato)
                xorl %edx, %edx
                movl $4, %ecx
                divl %ecx                       # eax = yoe*365 + yoe/4
                movl %eax, %r14d
                movl %r13d, %eax
                xorl %edx, %edx
                movl $100, %ecx
                divl %ecx                       # eax = yoe/100 (QUOCIENTE)
                subl %eax, %r14d
                movl %esi, %eax
                cmpl $2, %esi
                jle .Lkd_e_mp9
                subl $3, %eax                   # mp = m-3
                jmp .Lkd_e_mp
            .Lkd_e_mp9:
                addl $9, %eax                   # mp = m+9
            .Lkd_e_mp:
                imull $153, %eax, %eax
                addl $2, %eax
                xorl %edx, %edx
                movl $5, %ecx
                divl %ecx
                addl %ebx, %eax
                decl %eax                       # doy
                addl %eax, %r14d                # doe
                imull $146097, %r12d, %eax      # era*146097 (era<=24)
                addl %eax, %r14d
                subl $719468, %r14d
                movl %r14d, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_time_dayOfWeek(edi=y,esi=m,edx=d) -> 1..7 | 0
            .globl kof_time_dayOfWeek
            .type kof_time_dayOfWeek, @function
            kof_time_dayOfWeek:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %ebx
                movl %esi, %r12d
                movl %edx, %r13d
                call .Lkd_valid
                testl %eax, %eax
                je .Lkd_dw0
                movl %ebx, %edi
                movl %r12d, %esi
                movl %r13d, %edx
                call .Lkd_epoch
                addl $719470, %eax              # ed+3 (bias: 719468+2, mín 308>0)
                xorl %edx, %edx
                movl $7, %ecx
                divl %ecx                       # edx = floorMod(ed+3,7)
                leal 1(%rdx), %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkd_dw0:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_time_daysBetween(rdi..r9 = y1,m1,d1,y2,m2,d2) -> Int | 0
            .globl kof_time_daysBetween
            .type kof_time_daysBetween, @function
            kof_time_daysBetween:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %r9                       # d2 (6º arg SysV)
                movl %edi, %ebx                 # y1
                movl %esi, %r12d                # m1
                movl %edx, %r13d                # d1 -> depois ep1
                movl %ecx, %r14d                # y2
                movl %r8d, %r15d                # m2
                call .Lkd_valid
                testl %eax, %eax
                je .Lkd_db0
                movl %ebx, %edi
                movl %r12d, %esi
                movl %r13d, %edx
                call .Lkd_epoch
                movl %eax, %r13d                # r13 = ep1 (d1 não precisa mais)
                movl %r14d, %edi
                movl %r15d, %esi
                movl (%rsp), %edx               # d2
                call .Lkd_valid
                testl %eax, %eax
                je .Lkd_db0
                movl %r14d, %edi
                movl %r15d, %esi
                movl (%rsp), %edx
                call .Lkd_epoch
                subl %r13d, %eax                # ep2 - ep1
                jmp .Lkd_dbdone
            .Lkd_db0:
                xorl %eax, %eax
            .Lkd_dbdone:
                popq %r9
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_time_sleep
            .type kof_time_sleep, @function
            kof_time_sleep:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %ebx
                movl %ebx, %eax
                xorl %edx, %edx
                movl $1000, %ecx
                divl %ecx
                movl %eax, %r12d
                movl %edx, %r13d
                imull $1000000, %r13d
                subq $16, %rsp
                movq %r12, (%rsp)
                movq %r13, 8(%rsp)
                movq %rsp, %rdi
                xorq %rsi, %rsi
                movq $35, %rax
                syscall
                addq $16, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

}