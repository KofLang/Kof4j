package dev.kof.compiler.runtime;

/**
 * Emissão do ASM de kof.time addMonths em data ISO (STDLIB S7a-ext) do runtime
 * nativo x86. Domínio isolado de RuntimeTimeIso (regra <=500 linhas/classe) —
 * reusa .Lka_parse2/.Lka_put4/.Lka_put2 emitidos por RuntimeTimeIso e
 * .Lkd_valid + kof_time_daysInMonth emitidos por RuntimeTime no MESMO arquivo
 * .s (mesmo StringBuilder em NativeRuntime).
 */
public final class RuntimeTimeMonthIso {

    private RuntimeTimeMonthIso() {}

    public static void emitAddMonths(StringBuilder sb) {
        sb.append("""
            # ── kof_time (STDLIB S7a-ext) — ISO date + N meses ─────────────
            # kof_time_addMonths(rdi=iso, esi=months) -> String | "" (alloc)
            # MESMA aritmética inteira dos demais alvos: t = y*12 + (m-1) + n;
            # y1 = t/12; m1 = t%12 + 1; d1 = min(d, daysInMonth(y1,m1)). Pré-guarda
            # t em [12,119999] => divisão 64-bit sempre POSITIVA (não usa o
            # caminho .Lkd_epoch de dias). Reusa .Lka_parse2 + .Lkd_valid +
            # kof_time_daysInMonth + .Lka_put4/.Lka_put2 (a cauda de render é a
            # cópia exata de addDays). Inválida/out-of-range => "" (String len 0).
            .globl kof_time_addMonths
            .type kof_time_addMonths, @function
            kof_time_addMonths:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                xorl %r14d, %r14d               # present = 0
                movslq %esi, %rbx               # n (sign-extended)
                movq %rsp, %rsi                 # slots = [y,m,d]
                call .Lka_parse2                # edi=iso, rsi=slots -> eax=1 ok
                testl %eax, %eax
                jz .Lka_am_render
                movl 0(%rsp), %eax              # y
                imull $12, %eax, %eax           # y*12
                cltq
                movslq 4(%rsp), %rcx            # m (32-bit slot, sign-extended)
                addq %rcx, %rax                 # + m
                addq %rbx, %rax                 # + n
                decq %rax                       # t = y*12 + (m-1) + n
                cmpq $12, %rax
                jl .Lka_am_render
                cmpq $119999, %rax
                jg .Lka_am_render
                xorl %edx, %edx
                movq $12, %rcx
                divq %rcx                       # rax = y1, rdx = m1-1
                movl %eax, 0(%rsp)              # y1
                addl $1, %edx
                movl %edx, 4(%rsp)              # m1
                movl 0(%rsp), %edi              # y1
                movl 4(%rsp), %esi              # m1
                call kof_time_daysInMonth       # eax = dim(y1,m1)
                movl 8(%rsp), %edx              # d
                cmpl %eax, %edx                 # d - dim
                jge .Lka_am_keepdim             # d >= dim -> d1 = dim
                movl %edx, %eax                 # d < dim -> d1 = d
            .Lka_am_keepdim:
                movl %eax, 8(%rsp)              # d1
                movl $1, %r14d                  # present
                movl $10, %r12d                 # len
            .Lka_am_render:
                testl %r14d, %r14d
                jz .Lka_am_len0
                movl $10, %r12d
                jmp .Lka_am_alloc
            .Lka_am_len0:
                xorl %r12d, %r12d
            .Lka_am_alloc:
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, 0(%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                movb $0, 24(%r15)
                testl %r14d, %r14d
                jz .Lka_am_done
                leaq 24(%r15), %rdi
                movl 0(%rsp), %r13d
                call .Lka_put4
                movb $45, (%rdi)
                incq %rdi
                movl 4(%rsp), %r13d
                call .Lka_put2
                movb $45, (%rdi)
                incq %rdi
                movl 8(%rsp), %r13d
                call .Lka_put2
                movb $0, (%rdi)
            .Lka_am_done:
                movq %r15, %rax
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    public static void emitStartEndOf(StringBuilder sb) {
        sb.append("""
            # ── kof_time (STDLIB S7a-ext3) — inicio/fim do periodo ISO ──────
            # kof_time_startOf(rdi=iso, rsi=unit) / kof_time_endOf(...) -> String
            # unit = day|week|month|year; semana = segunda..domingo (dayOfWeek
            # ISO 1=seg..7=dom). COMPOSICAO dos primitivos ja com paridade
            # provada no MESMO .s (.Lka_parse2/.Lkd_valid/.Lka_put4/.Lka_put2 +
            # kof_time_dayOfWeek/kof_time_addDays/kof_time_daysInMonth) =>
            # byte-identico aos demais alvos por construcao. Data invalida /
            # unit desconhecida / resultado fora de 1..9999 (semana via guardas
            # do addDays) => "". O flag (eax 1=start/0=end) entra pela entrada
            # comum; 5 pushes + subq $48 mantem rsp ≡ 0 mod 16 no call.
            .globl kof_time_startOf
            .type kof_time_startOf, @function
            kof_time_startOf:
                movl $1, %eax
                jmp .Lka_se_entry
            .globl kof_time_endOf
            .type kof_time_endOf, @function
            kof_time_endOf:
                xorl %eax, %eax
            .Lka_se_entry:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                movl %eax, 16(%rsp)             # flag: 1=start 0=end
                xorl %r14d, %r14d               # present = 0
                movq %rdi, %rbx                 # iso
                movq %rsi, %r12                 # unit
                movq %rsp, %rsi
                call .Lka_parse2                # edi=iso -> slots [y,m,d]
                testl %eax, %eax
                jz .Lka_se_render
                movl 0(%rsp), %edi
                movl 4(%rsp), %esi
                movl 8(%rsp), %edx
                call .Lkd_valid
                testl %eax, %eax
                jz .Lka_se_render
                movzbl 24(%r12), %eax           # primeiro byte da unit
                # day=3 / week=4 / month=5 / year=4 -> len 4 DESAMBIGUA por
                # primeiro byte: 'w'=semana, 'y'=ano. Qualquer outro len ou
                # byte = unit desconhecida => "".
                cmpl $3, 16(%r12)
                je .Lka_se_day
                cmpl $5, 16(%r12)
                je .Lka_se_month
                cmpl $4, 16(%r12)
                jne .Lka_se_render
                cmpb $119, %al                  # 'w'
                je .Lka_se_week
                cmpb $121, %al                  # 'y'
                je .Lka_se_year
                jmp .Lka_se_render
            .Lka_se_day:
                cmpb $100, %al                  # 'd'
                jne .Lka_se_render
                jmp .Lka_se_present
            .Lka_se_week:
                movl 0(%rsp), %edi
                movl 4(%rsp), %esi
                movl 8(%rsp), %edx
                call kof_time_dayOfWeek         # eax = dow 1..7
                movl 16(%rsp), %ecx
                testl %ecx, %ecx
                jz .Lka_se_weekend
                movl %eax, %esi
                subl $1, %esi                   # -(dow-1)
                negl %esi
                movq %rbx, %rdi
                call kof_time_addDays
                jmp .Lka_se_ret
            .Lka_se_weekend:
                movl $7, %esi
                subl %eax, %esi                 # 7-dow
                movq %rbx, %rdi
                call kof_time_addDays
                jmp .Lka_se_ret
            .Lka_se_year:
                movl 16(%rsp), %eax
                testl %eax, %eax
                jz .Lka_se_yend
                movl $1, 4(%rsp)
                movl $1, 8(%rsp)
                jmp .Lka_se_present
            .Lka_se_yend:
                movl $12, 4(%rsp)
                movl $31, 8(%rsp)
                jmp .Lka_se_present
            .Lka_se_month:
                movl 16(%rsp), %eax
                testl %eax, %eax
                jnz .Lka_se_mstart
                movl 0(%rsp), %edi
                movl 4(%rsp), %esi
                call kof_time_daysInMonth
                movl %eax, 8(%rsp)
                jmp .Lka_se_present
            .Lka_se_mstart:
                movl $1, 8(%rsp)
            .Lka_se_present:
                movl $1, %r14d
            .Lka_se_render:
                testl %r14d, %r14d
                jz .Lka_se_len0
                movl $10, %r12d
                jmp .Lka_se_alloc
            .Lka_se_len0:
                xorl %r12d, %r12d
            .Lka_se_alloc:
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, 0(%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                movb $0, 24(%r15)
                testl %r14d, %r14d
                jz .Lka_se_done
                leaq 24(%r15), %rdi
                movl 0(%rsp), %r13d
                call .Lka_put4
                movb $45, (%rdi)
                incq %rdi
                movl 4(%rsp), %r13d
                call .Lka_put2
                movb $45, (%rdi)
                incq %rdi
                movl 8(%rsp), %r13d
                call .Lka_put2
                movb $0, (%rdi)
            .Lka_se_done:
                movq %r15, %rax
            .Lka_se_ret:
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    public static void emitAddYears(StringBuilder sb) {
        sb.append("""
            # ── kof_time (STDLIB S7a-ext2) — ISO date + N anos ─────────────
            # kof_time_addYears(rdi=iso, esi=years) -> String | "" (alloc)
            # MESMA política de addMonths: clamp de fim de mês (dia=min(dia,
            # dim(y1, m))); y1 = y + years; resultado FORA de 1..9999 => "";
            # inválida => "". Aritmética inteira pura; anos em int64 só para
            # o add signed (years pode ser negativo), depois o range-check
            # garante caber em int32. Reusa .Lka_parse2 + kof_time_daysInMonth
            # + .Lka_put4/.Lka_put2 (a cauda de render é cópia exata de addMonths).
            .globl kof_time_addYears
            .type kof_time_addYears, @function
            kof_time_addYears:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                xorl %r14d, %r14d               # present = 0
                movslq %esi, %rbx               # years (sign-extended)
                movq %rsp, %rsi                 # slots = [y,m,d]
                call .Lka_parse2                # edi=iso, rsi=slots -> eax=1 ok
                testl %eax, %eax
                jz .Lka_ay_render
                movslq 0(%rsp), %rax            # y
                addq %rbx, %rax                 # y + years
                cmpq $1, %rax
                jl .Lka_ay_render
                cmpq $9999, %rax
                jg .Lka_ay_render
                movl %eax, 0(%rsp)              # y1 (fits int32 by the guard)
                movl 0(%rsp), %edi              # y1
                movl 4(%rsp), %esi              # m
                call kof_time_daysInMonth       # eax = dim(y1, m)
                movl 8(%rsp), %edx              # d
                cmpl %eax, %edx                 # d - dim
                jge .Lka_ay_keepdim             # d >= dim -> d1 = dim
                movl %edx, %eax                 # d < dim -> d1 = d
            .Lka_ay_keepdim:
                movl %eax, 8(%rsp)              # d1
                movl $1, %r14d                  # present
                movl $10, %r12d                 # len
            .Lka_ay_render:
                testl %r14d, %r14d
                jz .Lka_ay_len0
                movl $10, %r12d
                jmp .Lka_ay_alloc
            .Lka_ay_len0:
                xorl %r12d, %r12d
            .Lka_ay_alloc:
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, 0(%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                movb $0, 24(%r15)
                testl %r14d, %r14d
                jz .Lka_ay_done
                leaq 24(%r15), %rdi
                movl 0(%rsp), %r13d
                call .Lka_put4
                movb $45, (%rdi)
                incq %rdi
                movl 4(%rsp), %r13d
                call .Lka_put2
                movb $45, (%rdi)
                incq %rdi
                movl 8(%rsp), %r13d
                call .Lka_put2
                movb $0, (%rdi)
            .Lka_ay_done:
                movq %r15, %rax
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
