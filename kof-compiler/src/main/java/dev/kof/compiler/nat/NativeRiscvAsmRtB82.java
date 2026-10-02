package dev.kof.compiler.nat;

// FASE (STDLIB S7a-ext): kof_time addMonths em data ISO (riscv64). Domínio
// isolado da B33 (regra <=500 linhas/classe) — MESMO arquivo .s do cross-emit,
// então .Lu8_parse2/.Lu8_put4/.Lu8_put2 e kof_time_daysInMonth/kdv_valid já
// emitidos antes da B82 neste .s (label file-scope: forward/backward ref ok).
// Aarch64: tradutor linha-a-linha (sdiv+msub no floor-div, t>=12 => sign-safe).
public final class NativeRiscvAsmRtB82 {

    static  String RISCV_RUNTIME_ASM_B_82 = """

            .section .text

            # kof_time_addMonths(a0=str, a1=n) -> String (alloc; inválida ou
            # fora de t=[12,119999] => "" — paridade x86/JVM/JS). MESMA aritmética
            # inteira: t = y*12+(m-1)+n; y1=t/12; m1=t%12+1; d1=min(d,dim(y1,m1)).
            # t>=12 => div/rem SIGNADOS = floor (tradutor aarch64: sdiv+msub).
            # Reusa .Lu8_parse2 + kof_time_daysInMonth (clobbers s0/t*, NUNCA
            # s1..s4 — lição B14) + .Lu8_put4/.Lu8_put2 (cauda idêntica ao addDays).
            .globl kof_time_addMonths
            kof_time_addMonths:
                addi sp, sp, -64
                sd ra, 56(sp)
                sd s1, 40(sp)
                sd s2, 32(sp)
                sd s3, 24(sp)
                sd s4, 16(sp)
                mv   s1, a1                    # n
                li   s3, 0                     # present
                mv   a1, sp                    # slots 0/4/8 = y/m/d
                call .Lu8_parse2
                beqz a0, .Lu8_am_r
                lw   a0, 0(sp)                 # y
                li   t0, 12
                mul  a0, a0, t0                # y*12
                lw   t1, 4(sp)                 # m
                add  a0, a0, t1                # + m
                add  a0, a0, s1                # + n
                addi a0, a0, -1                # t = y*12 + (m-1) + n
                li   t0, 12
                blt  a0, t0, .Lu8_am_r         # t < 12 -> fora de [1,9999]
                li   t0, 119999
                bgt  a0, t0, .Lu8_am_r         # t > 119999
                li   t1, 12
                div  t2, a0, t1                # y1 = t/12
                rem  t3, a0, t1                # m1-1 = t%12
                sw   t2, 0(sp)                 # y1
                addi t3, t3, 1
                sw   t3, 4(sp)                 # m1
                mv   a0, t2
                mv   a1, t3
                call kof_time_daysInMonth      # a0 = dim(y1,m1)
                lw   t5, 8(sp)                 # d
                blt  t5, a0, .Lu8_am_keepd     # d < dim -> d1 = d
                j    .Lu8_am_setd              # d1 = dim (a0)
            .Lu8_am_keepd:
                mv   a0, t5
            .Lu8_am_setd:
                sw   a0, 8(sp)                 # d1
                li   s3, 1                     # present
            .Lu8_am_r:
                li   s2, 10
                beqz s3, .Lu8_am_r0
                j    .Lu8_am_a
            .Lu8_am_r0:
                li   s2, 0
            .Lu8_am_a:
                addi a0, s2, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                li   t0, 0
                sw   t0, 4(s4)
                sd   t0, 8(s4)
                sw   s2, 16(s4)
                sw   t0, 20(s4)
                sb   t0, 24(s4)
                beqz s3, .Lu8_am_d
                addi a0, s4, 24
                lw   a1, 0(sp)
                call .Lu8_put4
                li   t0, 45
                sb   t0, 0(a0)
                addi a0, a0, 1
                lw   a1, 4(sp)
                call .Lu8_put2
                li   t0, 45
                sb   t0, 0(a0)
                addi a0, a0, 1
                lw   a1, 8(sp)
                call .Lu8_put2
                li   t0, 0
                sb   t0, 0(a0)
            .Lu8_am_d:
                mv   a0, s4
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_time_addYears(a0=str, a1=years) -> String (alloc; inválida ou
            # resultado FORA de [1,9999] => ""). MESMA política do addMonths:
            # clamp de fim de mês (dia=min(dia, dim(y1, mês))). y1 = y + years
            # (int32; years pode ser negativo — soma antes do guard, depois do
            # guard cabe em int32). Reusa .Lu8_parse2 + kof_time_daysInMonth +
            # .Lu8_put4/.Lu8_put2 (cauda idêntica ao addMonths).
            .globl kof_time_addYears
            kof_time_addYears:
                addi sp, sp, -64
                sd ra, 56(sp)
                sd s1, 40(sp)
                sd s2, 32(sp)
                sd s3, 24(sp)
                sd s4, 16(sp)
                mv   s1, a1                    # years
                li   s3, 0                     # present
                mv   a1, sp                    # slots 0/4/8 = y/m/d
                call .Lu8_parse2
                beqz a0, .Lu8_ay_r
                lw   a0, 0(sp)                 # y
                add  a0, a0, s1                # y1 = y + years
                li   t0, 1
                blt  a0, t0, .Lu8_ay_r         # y1 < 1 -> fora
                li   t0, 9999
                bgt  a0, t0, .Lu8_ay_r         # y1 > 9999 -> fora
                sw   a0, 0(sp)                 # y1
                lw   a1, 4(sp)                 # m
                call kof_time_daysInMonth      # a0 = dim(y1, m)
                lw   t5, 8(sp)                 # d
                blt  t5, a0, .Lu8_ay_keepd     # d < dim -> d1 = d
                j    .Lu8_ay_setd              # d1 = dim
            .Lu8_ay_keepd:
                mv   a0, t5
            .Lu8_ay_setd:
                sw   a0, 8(sp)                 # d1
                li   s3, 1                     # present
            .Lu8_ay_r:
                li   s2, 10
                beqz s3, .Lu8_ay_r0
                j    .Lu8_ay_a
            .Lu8_ay_r0:
                li   s2, 0
            .Lu8_ay_a:
                addi a0, s2, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                li   t0, 0
                sw   t0, 4(s4)
                sd   t0, 8(s4)
                sw   s2, 16(s4)
                sw   t0, 20(s4)
                sb   t0, 24(s4)
                beqz s3, .Lu8_ay_d
                addi a0, s4, 24
                lw   a1, 0(sp)
                call .Lu8_put4
                li   t0, 45
                sb   t0, 0(a0)
                addi a0, a0, 1
                lw   a1, 4(sp)
                call .Lu8_put2
                li   t0, 45
                sb   t0, 0(a0)
                addi a0, a0, 1
                lw   a1, 8(sp)
                call .Lu8_put2
                li   t0, 0
                sb   t0, 0(a0)
            .Lu8_ay_d:
                mv   a0, s4
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            """;
}
