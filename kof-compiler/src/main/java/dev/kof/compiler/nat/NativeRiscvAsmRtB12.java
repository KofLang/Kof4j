package dev.kof.compiler.nat;

// FASE 3 (STDLIB S5): fatia 12 de RISCV_RUNTIME_ASM_B — kof.validation
// documentos BR (isCpf/isCnpj/isCep/isPis). Dígitos extraídos (não-dígitos
// ignorados), dígito verificador módulo 11 — algoritmos idênticos a JVM/JS
// (vetores Python-derivados: CPF 52996581504 ok, 111.111.111-11 não).
//
// Robustez cross-arch: só mnemônicos do suporte MÍNIMO do tradutor aarch64
// (add/addi/sub/mv/lbu/lw/sb/li/beq/bne/blt/bltu/mul/andi/sd/ld/j/ret/call).
// mod-11 por SUBTRAÇÃO REPETIDA inlined (nunca div/rem; nunca call p/ label
// local). Pesos CNPJ/PIS por aritmética w=9-((i+off)&7) via andi — sem
// tabela. Acessos ao buf sempre via add reg + 0(reg) (padrão das fatias B*).
// Frame: buf = sp+0..15; regs salvos a partir de 24. Convenção de retorno:
// resultado em s0 (salvo), a0 = cópia final.
//
// ⚠️ LIÇÃO (bug .rodata herdado do B4, corrigido nesta sessão):
// `.section .text` NO TOPO de cada fatia.
public final class NativeRiscvAsmRtB12 {

    static final String RISCV_RUNTIME_ASM_B_12 = """

            .section .text
            # kof_br_digits_rv(a0=str, a1=buf) -> a0 = nº de dígitos.
            # Usa só t-regs (não toca s* — seguro p/ quem chama).
            .globl kof_br_digits_rv
            kof_br_digits_rv:
                mv   t2, a0
                mv   t3, a1
                li   t1, 0
                beqz t2, .Lv_br_rd_end
                lw   t4, 16(t2)
                li   t0, 0
            .Lv_br_rd_loop:
                bge  t0, t4, .Lv_br_rd_end
                add  t5, t2, 24
                add  t5, t5, t0
                lbu  t5, 0(t5)
                addi t0, t0, 1
                addi t5, t5, -48
                li   t6, 9
                bltu t6, t5, .Lv_br_rd_loop
                add  t6, t3, t1
                sb   t5, 0(t6)
                addi t1, t1, 1
                j    .Lv_br_rd_loop
            .Lv_br_rd_end:
                mv   a0, t1
                ret

            # kof_validation_isCep(a0=str) -> 0/1 (exatamente 8 dígitos)
            .globl kof_validation_isCep
            kof_validation_isCep:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                li   s0, 0
                addi a1, sp, 0
                call kof_br_digits_rv        # a0 = count (só t-regs corrompidos)
                li   t1, 8
                bne  a0, t1, .Lv_br_cep_end
                li   s0, 1
            .Lv_br_cep_end:
                mv   a0, s0
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # kof_validation_isCpf(a0=str) -> 0/1
            .globl kof_validation_isCpf
            kof_validation_isCpf:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)              # resultado
                sd   s1, 40(sp)              # d0 (allSame)
                sd   s2, 32(sp)              # acc
                sd   s3, 24(sp)              # i / allSame flag
                li   s0, 0
                addi a1, sp, 0
                call kof_br_digits_rv
                li   t1, 11
                bne  a0, t1, .Lv_br_cpf_ret
                # allSame: d0=buf[0]; se todos == d0 => inválido
                mv   t2, sp
                lbu  t2, 0(t2)
                li   s3, 1
                li   t0, 1
            .Lv_br_cpf_same:
                bge  t0, t1, .Lv_br_cpf_samedone
                add  t3, sp, t0
                lbu  t3, 0(t3)
                beq  t3, t2, .Lv_br_cpf_samecont
                li   s3, 0
                j    .Lv_br_cpf_samedone
            .Lv_br_cpf_samecont:
                addi t0, t0, 1
                j    .Lv_br_cpf_same
            .Lv_br_cpf_samedone:
                bnez s3, .Lv_br_cpf_ret
                # dv1 = sum buf[i]*(10-i), i=0..8; mod 11; r<2?0:11-r; == buf[9]
                li   s2, 0
                li   t0, 0
            .Lv_br_cpf_dv1:
                li   t4, 9
                bge  t0, t4, .Lv_br_cpf_dv1done
                add  t3, sp, t0
                lbu  t4, 0(t3)
                li   t5, 10
                sub  t5, t5, t0
                mul  t4, t4, t5
                add  s2, s2, t4
                addi t0, t0, 1
                j    .Lv_br_cpf_dv1
            .Lv_br_cpf_dv1done:
                li   t1, 11
            .Lv_br_cpf_dv1mod:
                bltu s2, t1, .Lv_br_cpf_dv1r
                sub  s2, s2, t1
                j    .Lv_br_cpf_dv1mod
            .Lv_br_cpf_dv1r:
                li   t2, 2
                blt  s2, t2, .Lv_br_cpf_dv1zero
                li   t3, 11
                sub  t3, t3, s2
                j    .Lv_br_cpf_dv1cmp
            .Lv_br_cpf_dv1zero:
                li   t3, 0
            .Lv_br_cpf_dv1cmp:
                li   t4, 9
                add  t4, sp, t4
                lbu  t4, 0(t4)
                bne  t3, t4, .Lv_br_cpf_ret
                # dv2 = sum buf[i]*(11-i), i=0..9; == buf[10]
                li   s2, 0
                li   t0, 0
            .Lv_br_cpf_dv2:
                li   t4, 10
                bge  t0, t4, .Lv_br_cpf_dv2done
                add  t3, sp, t0
                lbu  t4, 0(t3)
                li   t5, 11
                sub  t5, t5, t0
                mul  t4, t4, t5
                add  s2, s2, t4
                addi t0, t0, 1
                j    .Lv_br_cpf_dv2
            .Lv_br_cpf_dv2done:
                li   t1, 11
            .Lv_br_cpf_dv2mod:
                bltu s2, t1, .Lv_br_cpf_dv2r
                sub  s2, s2, t1
                j    .Lv_br_cpf_dv2mod
            .Lv_br_cpf_dv2r:
                li   t2, 2
                blt  s2, t2, .Lv_br_cpf_dv2zero
                li   t3, 11
                sub  t3, t3, s2
                j    .Lv_br_cpf_dv2cmp
            .Lv_br_cpf_dv2zero:
                li   t3, 0
            .Lv_br_cpf_dv2cmp:
                li   t4, 10
                add  t4, sp, t4
                lbu  t4, 0(t4)
                bne  t3, t4, .Lv_br_cpf_ret
                li   s0, 1
            .Lv_br_cpf_ret:
                mv   a0, s0
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                addi sp, sp, 64
                ret

            # kof_validation_isCnpj(a0=str) -> 0/1
            # w1[i]=9-((i+4)&7) i=0..11 => d[12]; w2[i]=9-((i+3)&7) i=0..12 => d[13]
            .globl kof_validation_isCnpj
            kof_validation_isCnpj:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s2, 40(sp)
                sd   s3, 32(sp)
                li   s0, 0
                addi a1, sp, 0
                call kof_br_digits_rv
                li   t1, 14
                bne  a0, t1, .Lv_br_cnpj_ret
                # dv1
                li   s2, 0
                li   t0, 0
            .Lv_br_cnpj_dv1:
                li   t1, 12
                bge  t0, t1, .Lv_br_cnpj_dv1done
                add  t3, sp, t0
                lbu  t4, 0(t3)
                addi t5, t0, 4
                andi t5, t5, 7
                li   t6, 9
                sub  t5, t6, t5          # w1
                mul  t4, t4, t5
                add  s2, s2, t4
                addi t0, t0, 1
                j    .Lv_br_cnpj_dv1
            .Lv_br_cnpj_dv1done:
                li   t1, 11
            .Lv_br_cnpj_dv1mod:
                bltu s2, t1, .Lv_br_cnpj_dv1r
                sub  s2, s2, t1
                j    .Lv_br_cnpj_dv1mod
            .Lv_br_cnpj_dv1r:
                li   t2, 2
                blt  s2, t2, .Lv_br_cnpj_dv1zero
                li   s3, 11
                sub  s3, s3, s2
                j    .Lv_br_cnpj_dv1cmp
            .Lv_br_cnpj_dv1zero:
                li   s3, 0
            .Lv_br_cnpj_dv1cmp:
                li   t1, 12
                add  t1, sp, t1
                lbu  t1, 0(t1)
                bne  s3, t1, .Lv_br_cnpj_ret
                # dv2
                li   s2, 0
                li   t0, 0
            .Lv_br_cnpj_dv2:
                li   t1, 13
                bge  t0, t1, .Lv_br_cnpj_dv2done
                add  t3, sp, t0
                lbu  t4, 0(t3)
                addi t5, t0, 3
                andi t5, t5, 7
                li   t6, 9
                sub  t5, t6, t5          # w2
                mul  t4, t4, t5
                add  s2, s2, t4
                addi t0, t0, 1
                j    .Lv_br_cnpj_dv2
            .Lv_br_cnpj_dv2done:
                li   t1, 11
            .Lv_br_cnpj_dv2mod:
                bltu s2, t1, .Lv_br_cnpj_dv2r
                sub  s2, s2, t1
                j    .Lv_br_cnpj_dv2mod
            .Lv_br_cnpj_dv2r:
                li   t2, 2
                blt  s2, t2, .Lv_br_cnpj_dv2zero
                li   s3, 11
                sub  s3, s3, s2
                j    .Lv_br_cnpj_dv2cmp
            .Lv_br_cnpj_dv2zero:
                li   s3, 0
            .Lv_br_cnpj_dv2cmp:
                li   t1, 13
                add  t1, sp, t1
                lbu  t1, 0(t1)
                bne  s3, t1, .Lv_br_cnpj_ret
                li   s0, 1
            .Lv_br_cnpj_ret:
                mv   a0, s0
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s2, 40(sp)
                ld   s3, 32(sp)
                addi sp, sp, 64
                ret

            # kof_validation_isPis(a0=str) -> 0/1
            # w[i]=9-((i+6)&7) i=0..9 => d[10]
            .globl kof_validation_isPis
            kof_validation_isPis:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s2, 40(sp)
                sd   s3, 32(sp)
                li   s0, 0
                addi a1, sp, 0
                call kof_br_digits_rv
                li   t1, 11
                bne  a0, t1, .Lv_br_pis_ret
                li   s2, 0
                li   t0, 0
            .Lv_br_pis_loop:
                li   t1, 10
                bge  t0, t1, .Lv_br_pis_done
                add  t3, sp, t0
                lbu  t4, 0(t3)
                addi t5, t0, 6
                andi t5, t5, 7
                li   t6, 9
                sub  t5, t6, t5          # w
                mul  t4, t4, t5
                add  s2, s2, t4
                addi t0, t0, 1
                j    .Lv_br_pis_loop
            .Lv_br_pis_done:
                li   t1, 11
            .Lv_br_pis_mod:
                bltu s2, t1, .Lv_br_pis_r
                sub  s2, s2, t1
                j    .Lv_br_pis_mod
            .Lv_br_pis_r:
                li   t2, 2
                blt  s2, t2, .Lv_br_pis_zero
                li   s3, 11
                sub  s3, s3, s2
                j    .Lv_br_pis_cmp
            .Lv_br_pis_zero:
                li   s3, 0
            .Lv_br_pis_cmp:
                li   t1, 10
                add  t1, sp, t1
                lbu  t1, 0(t1)
                bne  s3, t1, .Lv_br_pis_ret
                li   s0, 1
            .Lv_br_pis_ret:
                mv   a0, s0
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s2, 40(sp)
                ld   s3, 32(sp)
                addi sp, sp, 64
                ret
            """;
}
