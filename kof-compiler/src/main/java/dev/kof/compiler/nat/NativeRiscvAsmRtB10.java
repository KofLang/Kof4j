package dev.kof.compiler.nat;

// FASE 3 (STDLIB S4): fatia 10 de RISCV_RUNTIME_ASM_B — kof.encoding
// hexEncode/hexDecode. Sem tabela de dados: dígito hex é aritmético
// (d<10 -> '0'+d, senao 'a'+d-10) — o que também facilita o tradutor
// aarch64 (sem .rodata). Paridade byte-a-byte com RuntimeEncoding.x86.
public final class NativeRiscvAsmRtB10 {

    static final String RISCV_RUNTIME_ASM_B_10 = """

            .section .text
            # kof_encoding_hexEncode(a0=str) -> String (minúsculo)
            .globl kof_encoding_hexEncode
            kof_encoding_hexEncode:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0              # str
                beqz s0, .Lv_enc_he_orig
                lw   s1, 16(s0)          # len
                slli t0, s1, 1
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                slli s2, s1, 1           # outLen = 2*len
                sw   s2, 16(s3)
                li   t5, 0               # i
            .Lv_enc_he_loop:
                bge  t5, s2, .Lv_enc_he_term
                mv   t0, t5
                srli t0, t0, 1           # i/2
                add  t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)           # b = s[i/2]
                andi t0, t5, 1
                beqz t0, .Lv_enc_he_hi
                andi t1, t1, 15          # ímpar: nibble baixo
                j    .Lv_enc_he_char
            .Lv_enc_he_hi:
                srli t1, t1, 4           # par: nibble alto
            .Lv_enc_he_char:
                # char = d<10 ? '0'+d : 'a'+d-10
                li   t3, 10
                blt  t1, t3, .Lv_enc_he_d9
                addi t1, t1, 87          # 97+d-10
                j    .Lv_enc_he_put
            .Lv_enc_he_d9:
                addi t1, t1, 48          # '0'+d
            .Lv_enc_he_put:
                add  t3, s3, 24
                add  t3, t3, t5
                sb   t1, 0(t3)
                addi t5, t5, 1
                j    .Lv_enc_he_loop
            .Lv_enc_he_term:
                addi t0, s3, 24
                add  t0, t0, s2
                sb   zero, 0(t0)
                mv   a0, s3
                j    .Lv_enc_he_done
            .Lv_enc_he_orig:
                mv   a0, s0
            .Lv_enc_he_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            # kof_encoding_hexDecode(a0=str) -> String
            # cada 2 chars -> 1 byte; char fora de [0-9a-fA-F] => 0;
            # comprimento ímpar: último char é nibble alto (baixo=0).
            .globl kof_encoding_hexDecode
            kof_encoding_hexDecode:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0              # str
                beqz s0, .Lv_enc_hd_null
                lw   s1, 16(s0)          # inLen
                srli s2, s1, 1           # outLen = inLen/2
                andi t0, s1, 1
                add  s2, s2, t0          # +1 se ímpar
                addi a0, s2, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   s2, 16(s3)
                li   t5, 0               # i
            .Lv_enc_hd_loop:
                bge  t5, s2, .Lv_enc_hd_term
                # hi = nib(s[2i])
                slli t0, t5, 1
                add  t1, s0, 24
                add  t1, t1, t0
                lbu  a1, 0(t1)
                call .Lv_enc_hd_nib
                slli t2, a0, 4           # hi<<4
                # lo: se existe s[2i+1], nib; senao 0
                slli t0, t5, 1
                addi t0, t0, 1
                bge  t0, s1, .Lv_enc_hd_merge
                add  t1, s0, 24
                add  t1, t1, t0
                lbu  a1, 0(t1)
                call .Lv_enc_hd_nib
                or   t2, t2, a0
            .Lv_enc_hd_merge:
                add  t1, s3, 24
                add  t1, t1, t5
                sb   t2, 0(t1)
                addi t5, t5, 1
                j    .Lv_enc_hd_loop
            .Lv_enc_hd_term:
                addi t0, s3, 24
                add  t0, t0, s2
                sb   zero, 0(t0)
                mv   a0, s3
                j    .Lv_enc_hd_done
            .Lv_enc_hd_null:
                li   a0, 0
            .Lv_enc_hd_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret
            # sub-rotina: a1=char -> a0=nibble (0 se inválido).
            # Só usa caller-saved (a0/t3), não toca t2/t5/s* (sobrevivem ao bl).
            # Mnemônicos: APENAS bltu (tradutor aarch64 mapeia bltu->b.lo; bleu
            # NÃO existe no switch -> não usar).
            .Lv_enc_hd_nib:
                li   t3, 48
                bltu a1, t3, .Lv_enc_hd_bad0      # < '0'
                li   t3, 58
                bltu a1, t3, .Lv_enc_hd_num0      # '0'..'9'
                li   t3, 65
                bltu a1, t3, .Lv_enc_hd_bad0      # ':'..'@'
                li   t3, 71
                bltu a1, t3, .Lv_enc_hd_high0     # 'A'..'F'
                li   t3, 97
                bltu a1, t3, .Lv_enc_hd_bad0      # 'G'..'`'
                li   t3, 103
                bltu a1, t3, .Lv_enc_hd_low0      # 'a'..'f'
                j    .Lv_enc_hd_bad0
            .Lv_enc_hd_num0:
                addi a0, a1, -48
                ret
            .Lv_enc_hd_low0:
                addi a0, a1, -87
                ret
            .Lv_enc_hd_high0:
                addi a0, a1, -55
                ret
            .Lv_enc_hd_bad0:
                li   a0, 0
                ret

            """;
}
