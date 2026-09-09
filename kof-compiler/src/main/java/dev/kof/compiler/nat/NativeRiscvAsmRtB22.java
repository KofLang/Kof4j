package dev.kof.compiler.nat;

// FASE 3 (STDLIB S3.1b): fatia 22 de RISCV_RUNTIME_ASM_B — kof.strings
// unescapeHtml. MESMA máquina de RuntimeStringsEsc (x86) / JVM / JS, validada
// em Python (oracle, 30 casos; x86 30/30 no harness C isolado). 5 nomeadas
// (&amp &lt &gt &quot &apos) + numéricos &#DDD;/&#xHH; (valid: >0, <0x10000,
// não-surogate); outro "&" LITERAL. UTF-8 emit 1/2/3 bytes. Saída <= len =>
// buffer len+25. >=128 cópia. Chama kof_alloc => salva ra + s0..s7 (frame 80).
// ⚠️ LEITURA DE BYTE SEM CLOBBER: s5 = &v.bytes[0] (fixo); o endereço do byte
// i é sempre s5+i, e o VALOR lido vai p/ um reg DIFERENTE (t4) — nunca o
// mesmo reg do ponteiro (bug do 1º rascunho: lbu a1,25(a1) sobrescrevia a1).
// Classificação de byte em faixas UNSIGNED (bltu/bgeu).
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB22 {

    static final String RISCV_RUNTIME_ASM_B_22 = """

            .section .text
            .globl kof_strings_unescapeHtml
            kof_strings_unescapeHtml:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   s6, 16(sp)
                sd   s7, 8(sp)
                mv   s0, a0              # v
                beqz s0, .Lv_un_o
                addi s5, a0, 24          # &bytes[0]
                lw   s1, 16(s0)
                blez s1, .Lv_un_o
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0              # novo
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                li   s3, 0               # i
                li   s4, 0               # pos
            .Lv_un_loop:
                bge  s3, s1, .Lv_un_term
                add  t0, s5, s3
                lbu  t0, 0(t0)           # c = v.bytes[i]
                li   t1, 38
                beq  t0, t1, .Lv_un_amp
                add  t2, s2, s4
                sb   t0, 24(t2)
                addi s4, s4, 1
                addi s3, s3, 1
                j    .Lv_un_loop
            # &amp; -> &  (bytes[i+1..i+4]==a m p ;, i+5<=len)
            .Lv_un_amp:
                addi t3, s3, 5
                bgt  t3, s1, .Lv_un_lt
                add  t2, s5, s3
                lbu  t4, 1(t2)
                li   t1, 97
                bne  t4, t1, .Lv_un_lt
                lbu  t4, 2(t2)
                li   t1, 109
                bne  t4, t1, .Lv_un_lt
                lbu  t4, 3(t2)
                li   t1, 112
                bne  t4, t1, .Lv_un_lt
                lbu  t4, 4(t2)
                li   t1, 59
                bne  t4, t1, .Lv_un_lt
                add  t2, s2, s4
                li   t1, 38
                sb   t1, 24(t2)
                addi s4, s4, 1
                mv   s3, t3
                j    .Lv_un_loop
            # &lt; -> <  (bytes[i+1..i+3]==l t ;)
            .Lv_un_lt:
                addi t3, s3, 4
                bgt  t3, s1, .Lv_un_gt
                add  t2, s5, s3
                lbu  t4, 1(t2)
                li   t1, 108
                bne  t4, t1, .Lv_un_gt
                lbu  t4, 2(t2)
                li   t1, 116
                bne  t4, t1, .Lv_un_gt
                lbu  t4, 3(t2)
                li   t1, 59
                bne  t4, t1, .Lv_un_gt
                add  t2, s2, s4
                li   t1, 60
                sb   t1, 24(t2)
                addi s4, s4, 1
                mv   s3, t3
                j    .Lv_un_loop
            # &gt; -> >  (bytes[i+1..i+3]==g t ;)
            .Lv_un_gt:
                addi t3, s3, 4
                bgt  t3, s1, .Lv_un_quot
                add  t2, s5, s3
                lbu  t4, 1(t2)
                li   t1, 103
                bne  t4, t1, .Lv_un_quot
                lbu  t4, 2(t2)
                li   t1, 116
                bne  t4, t1, .Lv_un_quot
                lbu  t4, 3(t2)
                li   t1, 59
                bne  t4, t1, .Lv_un_quot
                add  t2, s2, s4
                li   t1, 62
                sb   t1, 24(t2)
                addi s4, s4, 1
                mv   s3, t3
                j    .Lv_un_loop
            # &quot; -> "  (bytes[i+1..i+5]==q u o t ;)
            .Lv_un_quot:
                addi t3, s3, 6
                bgt  t3, s1, .Lv_un_apos
                add  t2, s5, s3
                lbu  t4, 1(t2)
                li   t1, 113
                bne  t4, t1, .Lv_un_apos
                lbu  t4, 2(t2)
                li   t1, 117
                bne  t4, t1, .Lv_un_apos
                lbu  t4, 3(t2)
                li   t1, 111
                bne  t4, t1, .Lv_un_apos
                lbu  t4, 4(t2)
                li   t1, 116
                bne  t4, t1, .Lv_un_apos
                lbu  t4, 5(t2)
                li   t1, 59
                bne  t4, t1, .Lv_un_apos
                add  t2, s2, s4
                li   t1, 34
                sb   t1, 24(t2)
                addi s4, s4, 1
                mv   s3, t3
                j    .Lv_un_loop
            # &apos; -> '  (bytes[i+1..i+5]==a p o s ;)
            .Lv_un_apos:
                addi t3, s3, 6
                bgt  t3, s1, .Lv_un_num
                add  t2, s5, s3
                lbu  t4, 1(t2)
                li   t1, 97
                bne  t4, t1, .Lv_un_num
                lbu  t4, 2(t2)
                li   t1, 112
                bne  t4, t1, .Lv_un_num
                lbu  t4, 3(t2)
                li   t1, 111
                bne  t4, t1, .Lv_un_num
                lbu  t4, 4(t2)
                li   t1, 115
                bne  t4, t1, .Lv_un_num
                lbu  t4, 5(t2)
                li   t1, 59
                bne  t4, t1, .Lv_un_num
                add  t2, s2, s4
                li   t1, 39
                sb   t1, 24(t2)
                addi s4, s4, 1
                mv   s3, t3
                j    .Lv_un_loop
            # &#DDD; / &#xHH;
            .Lv_un_num:
                addi t4, s3, 1
                bge  t4, s1, .Lv_un_lit
                add  t2, s5, t4
                lbu  t2, 0(t2)
                li   t1, 35
                bne  t2, t1, .Lv_un_lit     # '#'
                li   s7, 0                  # hx
                addi t4, s3, 2              # j = i+2
                bge  t4, s1, .Lv_un_lit
                add  t2, s5, t4
                lbu  t2, 0(t2)
                li   t1, 120
                beq  t2, t1, .Lv_un_sethx
                li   t1, 88
                bne  t2, t1, .Lv_un_d0
            .Lv_un_sethx:
                li   s7, 1
                addi t4, t4, 1
            .Lv_un_d0:
                mv   t5, t4                 # k = j
                li   s6, 0                  # acc
            .Lv_un_digits:
                bge  t5, s1, .Lv_un_dend
                add  t2, s5, t5
                lbu  t2, 0(t2)              # d
                addi t3, t2, -48
                li   t1, 10
                bltu t3, t1, .Lv_un_dok     # 0..9
                beqz s7, .Lv_un_dend         # dec: não-dígito => fim
                addi t3, t2, -97
                li   t1, 6
                bltu t3, t1, .Lv_un_dok10   # a..f
                addi t3, t2, -65
                li   t1, 6
                bltu t3, t1, .Lv_un_dok10   # A..F
                j    .Lv_un_dend
            .Lv_un_dok10:
                addi t3, t3, 10
            .Lv_un_dok:
                beqz s7, .Lv_un_dec
                slli t1, s6, 4
                add  s6, t1, t3
                j    .Lv_un_ovf
            .Lv_un_dec:
                li   t1, 10
                mul  s6, s6, t1
                add  s6, s6, t3
            .Lv_un_ovf:
                li   t1, 0x10FFFF
                blt  t1, s6, .Lv_un_dend
                addi t5, t5, 1
                j    .Lv_un_digits
            .Lv_un_dend:
                beq  t5, t4, .Lv_un_lit
                bge  t5, s1, .Lv_un_lit
                add  t2, s5, t5
                lbu  t2, 0(t2)
                li   t1, 59
                bne  t2, t1, .Lv_un_lit      # ';' ausente
                beqz s6, .Lv_un_lit
                li   t1, 0x10000
                blt  t1, s6, .Lv_un_lit
                li   t1, 0xD800
                sub  t2, s6, t1
                li   t1, 0x800
                bltu t2, t1, .Lv_un_lit      # surrogate
                li   t1, 0x80
                blt  s6, t1, .Lv_un_u1
                li   t1, 0x800
                blt  s6, t1, .Lv_un_u2
                srli t1, s6, 12
                ori  t1, t1, 0xE0
                add  t2, s2, s4
                sb   t1, 24(t2)
                srli t1, s6, 6
                andi t1, t1, 0x3F
                ori  t1, t1, 0x80
                sb   t1, 25(t2)
                andi t1, s6, 0x3F
                ori  t1, t1, 0x80
                sb   t1, 26(t2)
                addi s4, s4, 3
                j    .Lv_un_after
            .Lv_un_u2:
                srli t1, s6, 6
                ori  t1, t1, 0xC0
                add  t2, s2, s4
                sb   t1, 24(t2)
                andi t1, s6, 0x3F
                ori  t1, t1, 0x80
                sb   t1, 25(t2)
                addi s4, s4, 2
                j    .Lv_un_after
            .Lv_un_u1:
                add  t2, s2, s4
                sb   s6, 24(t2)
                addi s4, s4, 1
            .Lv_un_after:
                addi s3, t5, 1
                j    .Lv_un_loop
            .Lv_un_lit:
                add  t2, s2, s4
                li   t1, 38
                sb   t1, 24(t2)
                addi s4, s4, 1
                addi s3, s3, 1
                j    .Lv_un_loop
            .Lv_un_term:
                sw   s4, 16(s2)
                add  t0, s2, s4
                sb   zero, 24(t0)
                mv   a0, s2
                j    .Lv_un_done
            .Lv_un_o:
                mv   a0, s0
            .Lv_un_done:
                ld   s7, 8(sp)
                ld   s6, 16(sp)
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            """;
}
