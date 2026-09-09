package dev.kof.compiler.nat;

// FASE 3 (STDLIB S4.2b): fatia 11 de RISCV_RUNTIME_ASM_B — kof.encoding
// urlEncode/urlDecode (percent-encoding RFC 3986, unreserved [A-Za-z0-9-_.~]).
// Byte-puro, sem tabela e sem FP — port riscv/aarch direto. hex MAIÚSCULO no
// encode (paridade com RuntimeEncoding.x86 / JVM / JS). Mnemônicos só do
// suporte do tradutor aarch64: bltu/bgeu (unsigned), ble (signed), li/mv/
// add/addi/sub/andi/or/slli/srli/lbu/sb/lw/sw/la/j/ret (sem bleu — inexistente).
public final class NativeRiscvAsmRtB11 {

    static final String RISCV_RUNTIME_ASM_B_11 = """

            .section .text
            # kof_encoding_urlEncode(a0=str) -> String (pior caso 3*len)
            .globl kof_encoding_urlEncode
            kof_encoding_urlEncode:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                beqz s0, .Lv_enc_ue_orig
                lw   s1, 16(s0)          # len
                slli t0, s1, 1
                add  t0, t0, s1          # 3*len
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                li   s2, 0               # i
                li   t5, 0               # out pos
            .Lv_enc_ue_loop:
                bge  s2, s1, .Lv_enc_ue_term
                add  t1, s0, 24
                add  t1, t1, s2
                lbu  t1, 0(t1)           # c
                # unreserved?
                li   t2, 65
                bltu t1, t2, .Lv_enc_ue_ck_dig   # <'A'
                li   t2, 91
                bltu t1, t2, .Lv_enc_ue_keep     # 'A'..'Z'
                li   t2, 97
                bltu t1, t2, .Lv_enc_ue_ck_sym   # '['..'`'
                li   t2, 123
                bltu t1, t2, .Lv_enc_ue_keep     # 'a'..'z'
                j    .Lv_enc_ue_ck_sym
            .Lv_enc_ue_ck_dig:
                li   t2, 48
                bltu t1, t2, .Lv_enc_ue_ck_sym   # '-' 45 '.' 46 passam aqui
                li   t2, 58
                bltu t1, t2, .Lv_enc_ue_keep     # '0'..'9'
                j    .Lv_enc_ue_esc
            .Lv_enc_ue_ck_sym:
                li   t2, 45
                beq  t1, t2, .Lv_enc_ue_keep     # '-'
                li   t2, 95
                beq  t1, t2, .Lv_enc_ue_keep     # '_'
                li   t2, 46
                beq  t1, t2, .Lv_enc_ue_keep     # '.'
                li   t2, 126
                beq  t1, t2, .Lv_enc_ue_keep     # '~'
                j    .Lv_enc_ue_esc
            .Lv_enc_ue_keep:
                add  t2, s3, 24
                add  t2, t2, t5
                sb   t1, 0(t2)
                addi t5, t5, 1
                j    .Lv_enc_ue_next
            .Lv_enc_ue_esc:
                add  t2, s3, 24
                add  t2, t2, t5
                li   t3, 37
                sb   t3, 0(t2)
                addi t5, t5, 1
                # hi = c>>4 -> hex upper
                srli t3, t1, 4
                li   t4, 10
                bltu t3, t4, .Lv_enc_ue_hi9
                addi t3, t3, 55          # 'A'-10
                j    .Lv_enc_ue_hiput
            .Lv_enc_ue_hi9:
                addi t3, t3, 48          # '0'
            .Lv_enc_ue_hiput:
                add  t2, s3, 24
                add  t2, t2, t5
                sb   t3, 0(t2)
                addi t5, t5, 1
                # lo = c&15 -> hex upper
                andi t3, t1, 15
                li   t4, 10
                bltu t3, t4, .Lv_enc_ue_lo9
                addi t3, t3, 55
                j    .Lv_enc_ue_loput
            .Lv_enc_ue_lo9:
                addi t3, t3, 48
            .Lv_enc_ue_loput:
                add  t2, s3, 24
                add  t2, t2, t5
                sb   t3, 0(t2)
                addi t5, t5, 1
            .Lv_enc_ue_next:
                addi s2, s2, 1
                j    .Lv_enc_ue_loop
            .Lv_enc_ue_term:
                sw   t5, 16(s3)
                add  t0, s3, 24
                add  t0, t0, t5
                sb   zero, 0(t0)
                mv   a0, s3
                j    .Lv_enc_ue_done
            .Lv_enc_ue_orig:
                mv   a0, s0
            .Lv_enc_ue_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            # kof_encoding_urlDecode(a0=str) -> String
            # %XY (X,Y hex ASCII) => byte; senao char literal. out <= inLen.
            .globl kof_encoding_urlDecode
            kof_encoding_urlDecode:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                beqz s0, .Lv_enc_ud_null
                lw   s1, 16(s0)          # inLen
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                li   s2, 0               # i
                li   t5, 0               # out pos
            .Lv_enc_ud_loop:
                bge  s2, s1, .Lv_enc_ud_term
                add  t1, s0, 24
                add  t1, t1, s2
                lbu  t1, 0(t1)           # c
                li   t2, 37
                bne  t1, t2, .Lv_enc_ud_lit
                # precisa s[i+1], s[i+2]: i+3 <= inLen
                addi t2, s2, 3
                bgt  t2, s1, .Lv_enc_ud_lit
                # hi = strict(s[i+1]) => t3, ou -1 => .lit
                addi t2, s2, 1
                add  t4, s0, 24
                add  t4, t4, t2
                lbu  a1, 0(t4)
                call .Lv_enc_ud_nib      # a0 = nibble ou -1
                li   t2, 0
                blt  a0, t2, .Lv_enc_ud_lit
                slli s4, a0, 4           # hi<<4 em s4 (nib clobbers t3)
                # lo = strict(s[i+2])
                addi t2, s2, 2
                add  t4, s0, 24
                add  t4, t4, t2
                lbu  a1, 0(t4)
                call .Lv_enc_ud_nib
                li   t2, 0
                blt  a0, t2, .Lv_enc_ud_lit
                or   s4, s4, a0
                add  t4, s3, 24
                add  t4, t4, t5
                sb   s4, 0(t4)
                addi t5, t5, 1
                addi s2, s2, 3
                j    .Lv_enc_ud_loop
            .Lv_enc_ud_lit:
                add  t3, s0, 24
                add  t3, t3, s2
                lbu  t3, 0(t3)
                add  t4, s3, 24
                add  t4, t4, t5
                sb   t3, 0(t4)
                addi t5, t5, 1
                addi s2, s2, 1
                j    .Lv_enc_ud_loop
            .Lv_enc_ud_term:
                sw   t5, 16(s3)
                add  t0, s3, 24
                add  t0, t0, t5
                sb   zero, 0(t0)
                mv   a0, s3
                j    .Lv_enc_ud_done
            .Lv_enc_ud_null:
                li   a0, 0
            .Lv_enc_ud_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret
            # sub: a1=char -> a0=nibble (0..15) ou -1 se inválido (ASCII-estrito).
            # Só toca a0/a1/t3 — NÃO destrói s2/s3/t5/t1 (preservam no laço).
            .Lv_enc_ud_nib:
                li   t3, 48
                bltu a1, t3, .Lv_enc_ud_nbad
                li   t3, 58
                bltu a1, t3, .Lv_enc_ud_nnum     # 0..9
                li   t3, 65
                bltu a1, t3, .Lv_enc_ud_nbad
                li   t3, 71
                bltu a1, t3, .Lv_enc_ud_nhigh    # A..F
                li   t3, 97
                bltu a1, t3, .Lv_enc_ud_nbad
                li   t3, 103
                bltu a1, t3, .Lv_enc_ud_nlow     # a..f
                j    .Lv_enc_ud_nbad
            .Lv_enc_ud_nnum:
                addi a0, a1, -48
                ret
            .Lv_enc_ud_nlow:
                addi a0, a1, -87
                ret
            .Lv_enc_ud_nhigh:
                addi a0, a1, -55
                ret
            .Lv_enc_ud_nbad:
                li   a0, -1
                ret

            """;
}
