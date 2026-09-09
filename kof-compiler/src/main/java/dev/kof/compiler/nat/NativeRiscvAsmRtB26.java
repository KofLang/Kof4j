package dev.kof.compiler.nat;

// FASE 3 (STDLIB S3.1c): fatia 26 de RISCV_RUNTIME_ASM_B — kof.strings
// escapeJson. MESMA máquina de JVM/JS/x86 (oracle Python): corpo de string
// literal JSON (RFC 8259) — \ -> \\, " -> \", \b \f \n \r \t 2-char,
// ctrl <0x20 -> \\u00xx (hex MINÚSCULO, dígito ALTO primeiro), demais bytes
// (>=128) copiados. Buffer 6*len; null/"" => original. Chama kof_alloc =>
// salva ra + s0..s5; frame 64 (16-align). lbs em reg-base separado do
// reg-valor (lição B22). Só t0..t5 existem (lição B24).
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB26 {

    static final String RISCV_RUNTIME_ASM_B_26 = """

            .section .text
            .globl kof_strings_escapeJson
            kof_strings_escapeJson:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0              # v
                beqz s0, .Lv_ej_o
                lw   s1, 16(s0)          # len
                blez s1, .Lv_ej_o
                li   t0, 6
                mul  t0, t0, s1          # 6*len
                addi t0, t0, 25
                addi a0, t0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0              # novo
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                li   s3, 0               # i
                li   s4, 0               # pos
            .Lv_ej_loop:
                bge  s3, s1, .Lv_ej_term
                add  a1, s0, s3
                lbu  t0, 24(a1)          # c = v.bytes[i]
                li   t1, 92
                beq  t0, t1, .Lv_ej_bs
                li   t1, 34
                beq  t0, t1, .Lv_ej_dq
                li   t1, 8
                beq  t0, t1, .Lv_ej_b
                li   t1, 12
                beq  t0, t1, .Lv_ej_f
                li   t1, 10
                beq  t0, t1, .Lv_ej_n
                li   t1, 13
                beq  t0, t1, .Lv_ej_r
                li   t1, 9
                beq  t0, t1, .Lv_ej_t
                li   t1, 32
                blt  t0, t1, .Lv_ej_u    # ctrl -> \\u00xx
                add  t2, s2, s4
                sb   t0, 24(t2)          # literal (incl >=128)
                addi s4, s4, 1
                addi s3, s3, 1
                j    .Lv_ej_loop
            .Lv_ej_bs:
                add  t2, s2, s4
                li   t3, 92
                sb   t3, 24(t2)
                sb   t3, 25(t2)
                addi s4, s4, 2
                j    .Lv_ej_adv
            .Lv_ej_dq:
                add  t2, s2, s4
                li   t3, 92
                sb   t3, 24(t2)
                li   t3, 34
                sb   t3, 25(t2)
                addi s4, s4, 2
                j    .Lv_ej_adv
            .Lv_ej_b:
                add  t2, s2, s4
                li   t3, 92
                sb   t3, 24(t2)
                li   t3, 98
                sb   t3, 25(t2)          # b
                addi s4, s4, 2
                j    .Lv_ej_adv
            .Lv_ej_f:
                add  t2, s2, s4
                li   t3, 92
                sb   t3, 24(t2)
                li   t3, 102
                sb   t3, 25(t2)          # f
                addi s4, s4, 2
                j    .Lv_ej_adv
            .Lv_ej_n:
                add  t2, s2, s4
                li   t3, 92
                sb   t3, 24(t2)
                li   t3, 110
                sb   t3, 25(t2)          # n
                addi s4, s4, 2
                j    .Lv_ej_adv
            .Lv_ej_r:
                add  t2, s2, s4
                li   t3, 92
                sb   t3, 24(t2)
                li   t3, 114
                sb   t3, 25(t2)          # r
                addi s4, s4, 2
                j    .Lv_ej_adv
            .Lv_ej_t:
                add  t2, s2, s4
                li   t3, 92
                sb   t3, 24(t2)
                li   t3, 116
                sb   t3, 25(t2)          # t
                addi s4, s4, 2
                j    .Lv_ej_adv
            .Lv_ej_u:
                add  t2, s2, s4
                li   t3, 92
                sb   t3, 24(t2)          # backslash
                li   t3, 117
                sb   t3, 25(t2)          # u
                li   t3, 48
                sb   t3, 26(t2)          # 0
                sb   t3, 27(t2)          # 0
                # nibble ALTO (c>>4 & 15) -> 28
                srli t3, t0, 4
                andi t3, t3, 15
                li   t4, 10
                blt  t3, t4, .Lv_ej_u1h
                addi t3, t3, 87          # a..
                j    .Lv_ej_u2h
            .Lv_ej_u1h:
                addi t3, t3, 48          # 0..
            .Lv_ej_u2h:
                sb   t3, 28(t2)
                # nibble BAIXO (c & 15) -> 29
                andi t3, t0, 15
                li   t4, 10
                blt  t3, t4, .Lv_ej_u1l
                addi t3, t3, 87
                j    .Lv_ej_u2l
            .Lv_ej_u1l:
                addi t3, t3, 48
            .Lv_ej_u2l:
                sb   t3, 29(t2)
                addi s4, s4, 6
            .Lv_ej_adv:
                addi s3, s3, 1
                j    .Lv_ej_loop
            .Lv_ej_term:
                sw   s4, 16(s2)
                add  t0, s2, s4
                sb   zero, 24(t0)
                mv   a0, s2
                j    .Lv_ej_done
            .Lv_ej_o:
                mv   a0, s0
            .Lv_ej_done:
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            """;
}
