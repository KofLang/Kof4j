package dev.kof.compiler.nat;

// §667: runtime assembly riscv64 — decode de string JSON com o conjunto
// COMPLETO de escapes. Split de NativeRiscvAsmMapset1 (kof_json_decode_string)
// e NativeRiscvAsmRtB4 (kof_json_dec_str, o helper da lista de strings) por
// responsabilidade — as duas cresceram além do teto de 500/600 do REFACTOR-500.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico).
public final class NativeRiscvAsmJsonStr {

    private NativeRiscvAsmJsonStr() {}

    static String RISCV_JSONSTR_ASM = """
            # kof_json_decode_string(json) -> String. §667: decodifica o conjunto
            # COMPLETO de escapes (aspas barra, b f n r t e a forma unicode de 4
            # dígitos hex em UTF-8) — espelha o JsonParser do JVM e o asm
            # x86-64 (RuntimeJsonDecode2). AUTOCONTIDO: escreve direto no buffer
            # alocado (sem builder), preservando o fecho de peças do modelo
            # (RiscvSlices) — o decode_string é a única face JSON do piso.
            .globl kof_json_decode_string
            kof_json_decode_string:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)          # json
                sd   s1, 56(sp)          # len
                sd   s2, 48(sp)          # pos
                sd   s3, 40(sp)          # cursor de escrita
                sd   s4, 32(sp)          # string alocada
                sd   s5, 24(sp)          # code point
                sd   s6, 16(sp)          # contador de dígitos hex
                sd   s7, 8(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                li   s2, 0
            .Lkds_open:
                bge  s2, s1, .Lkds_raw   # documento vazio -> span cru (vazio)
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                li   t2, 32              # espacos a esquerda: pula
                beq  t1, t2, .Lkds_open_inc
                li   t2, 10
                beq  t1, t2, .Lkds_open_inc
                li   t2, 13
                beq  t1, t2, .Lkds_open_inc
                li   t2, 9
                beq  t1, t2, .Lkds_open_inc
                li   t2, 34              # aspa -> string citada
                beq  t1, t2, .Lkds_body
                j    .Lkds_raw           # qualquer outro -> TEXTO CRU
            .Lkds_open_inc:
                addi s2, s2, 1
                j    .Lkds_open
            # #1174 (19/11): valor NÃO-string (objeto/array/número/bool/null)
            # devolve o TEXTO JSON CRU aparado nas duas pontas — contrato do
            # oráculo JVM (`{"a":1}` -> `{"a":1}`). Antes devolvia VAZIO.
            .Lkds_raw:
                li   s2, 0
            .Lkds_raw_ltrim:
                bge  s2, s1, .Lkds_empty
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                li   t2, 32
                beq  t1, t2, .Lkds_raw_linc
                li   t2, 10
                beq  t1, t2, .Lkds_raw_linc
                li   t2, 13
                beq  t1, t2, .Lkds_raw_linc
                li   t2, 9
                beq  t1, t2, .Lkds_raw_linc
                j    .Lkds_raw_end
            .Lkds_raw_linc:
                addi s2, s2, 1
                j    .Lkds_raw_ltrim
            .Lkds_raw_end:
                mv   s7, s1              # fim exclusivo
            .Lkds_raw_trim:
                beq  s7, s2, .Lkds_raw_body      # so os espacos centrais -> corpo
                addi t0, s7, -1
                add  t0, s0, t0
                lbu  t1, 24(t0)                  # payload @24: byte em s7-1
                li   t2, 32
                beq  t1, t2, .Lkds_raw_dec
                li   t2, 10
                beq  t1, t2, .Lkds_raw_dec
                li   t2, 13
                beq  t1, t2, .Lkds_raw_dec
                li   t2, 9
                beq  t1, t2, .Lkds_raw_dec
                j    .Lkds_raw_body
            .Lkds_raw_dec:
                addi s7, s7, -1
                j    .Lkds_raw_trim
            .Lkds_raw_body:
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                sw   zero, 4(s4)
                sd   zero, 8(s4)
                sw   zero, 16(s4)
                sw   zero, 20(s4)
                addi s3, s4, 24
            .Lkds_raw_copy:
                bge  s2, s7, .Lkds_fin_lbl
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                sb   t1, 0(s3)
                addi s3, s3, 1
                addi s2, s2, 1
                j    .Lkds_raw_copy
            .Lkds_body:
                addi s2, s2, 1           # pos após a aspa de abertura
                # a saída NUNCA excede a entrada (escape 2->1, unicode 6->até 3)
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                sw   zero, 4(s4)
                sd   zero, 8(s4)
                sw   zero, 16(s4)
                sw   zero, 20(s4)
                addi s3, s4, 24
            .Lkds_loop:
                bge  s2, s1, .Lkds_fin
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                li   t2, 34
                beq  t1, t2, .Lkds_close
                li   t2, 92              # '\'
                beq  t1, t2, .Lkds_esc
                sb   t1, 0(s3)
                addi s3, s3, 1
                addi s2, s2, 1
                j    .Lkds_loop
            .Lkds_esc:
                addi s2, s2, 1
                bge  s2, s1, .Lkds_fin
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                li   t2, 110             # n
                beq  t1, t2, .Lkds_nl
                li   t2, 116             # t
                beq  t1, t2, .Lkds_tb
                li   t2, 114             # r
                beq  t1, t2, .Lkds_cr
                li   t2, 98              # b
                beq  t1, t2, .Lkds_bs
                li   t2, 102             # f
                beq  t1, t2, .Lkds_ff
                li   t2, 117             # u
                beq  t1, t2, .Lkds_u
                # aspas barra e desconhecidos: byte literal
                sb   t1, 0(s3)
                addi s3, s3, 1
                addi s2, s2, 1
                j    .Lkds_loop
            .Lkds_nl:
                li   t1, 10
                j    .Lkds_emit
            .Lkds_tb:
                li   t1, 9
                j    .Lkds_emit
            .Lkds_cr:
                li   t1, 13
                j    .Lkds_emit
            .Lkds_bs:
                li   t1, 8
                j    .Lkds_emit
            .Lkds_ff:
                li   t1, 12
            .Lkds_emit:
                sb   t1, 0(s3)
                addi s3, s3, 1
                addi s2, s2, 1
                j    .Lkds_loop
            .Lkds_u:
                addi s2, s2, 1           # primeiro dígito hex
                li   s5, 0               # code point
                li   s6, 4               # 4 dígitos
            .Lkds_uhex:
                bge  s2, s1, .Lkds_uenc
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                li   t2, 48
                blt  t1, t2, .Lkds_uenc
                li   t2, 57
                ble  t1, t2, .Lkds_udig
                li   t2, 65
                blt  t1, t2, .Lkds_uenc
                li   t2, 70
                ble  t1, t2, .Lkds_uup
                li   t2, 97
                blt  t1, t2, .Lkds_uenc
                li   t2, 102
                bgt  t1, t2, .Lkds_uenc
                addi t1, t1, -87         # a-f -> 10-15
                j    .Lkds_uacc
            .Lkds_uup:
                addi t1, t1, -55         # A-F -> 10-15
                j    .Lkds_uacc
            .Lkds_udig:
                addi t1, t1, -48         # 0-9 -> 0-9
            .Lkds_uacc:
                slli s5, s5, 4
                or   s5, s5, t1
                addi s2, s2, 1
                addi s6, s6, -1
                bnez s6, .Lkds_uhex
            .Lkds_uenc:
                li   t2, 128
                blt  s5, t2, .Lkds_u1
                li   t2, 2048
                blt  s5, t2, .Lkds_u2
                li   t2, 65536
                blt  s5, t2, .Lkds_u3
                srli t1, s5, 18
                ori  t1, t1, 240
                sb   t1, 0(s3)
                addi s3, s3, 1
                srli t1, s5, 12
                andi t1, t1, 63
                ori  t1, t1, 128
                sb   t1, 0(s3)
                addi s3, s3, 1
                srli t1, s5, 6
                andi t1, t1, 63
                ori  t1, t1, 128
                sb   t1, 0(s3)
                addi s3, s3, 1
                j    .Lkds_ucont
            .Lkds_u3:
                srli t1, s5, 12
                ori  t1, t1, 224
                sb   t1, 0(s3)
                addi s3, s3, 1
                srli t1, s5, 6
                andi t1, t1, 63
                ori  t1, t1, 128
                sb   t1, 0(s3)
                addi s3, s3, 1
                j    .Lkds_ucont
            .Lkds_u2:
                srli t1, s5, 6
                ori  t1, t1, 192
                sb   t1, 0(s3)
                addi s3, s3, 1
            .Lkds_ucont:
                andi t1, s5, 63
                ori  t1, t1, 128
                sb   t1, 0(s3)
                addi s3, s3, 1
                j    .Lkds_loop
            .Lkds_u1:
                sb   s5, 0(s3)
                addi s3, s3, 1
                j    .Lkds_loop
            .Lkds_close:
                addi s2, s2, 1
            .Lkds_fin_lbl:
                j    .Lkds_fin
            .Lkds_fin:
                sub  t0, s3, s4
                addi t0, t0, -24
                sw   t0, 16(s4)
                add  t1, s4, t0
                addi t1, t1, 24
                sb   zero, 0(t1)
                mv   a0, s4
                j    .Lkds_ret
            .Lkds_empty:
                li   a0, 0
            .Lkds_ret:
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

            # Helper: a0 = json, a1 = cursor -> a0 = KofString, a2 = novo
            # cursor (após a aspa de fechamento). §667: decodifica o conjunto
            # COMPLETO de escapes (aspas barra, b f n r t e a forma unicode de 4
            # dígitos hex em UTF-8) escrevendo direto no buffer — espelha o
            # JsonParser do JVM e o asm x86-64 (RuntimeJsonDecode2).
            kof_json_dec_str:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
            .Lds_skip:
                bge  s1, s2, .Lds_empty
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 32
                beq  a0, t1, .Lds_skip_inc
                li   t1, 10
                beq  a0, t1, .Lds_skip_inc
                li   t1, 13
                beq  a0, t1, .Lds_skip_inc
                li   t1, 9
                beq  a0, t1, .Lds_skip_inc
                j    .Lds_open
            .Lds_skip_inc:
                addi s1, s1, 1
                j    .Lds_skip
            .Lds_open:
                li   t1, 34
                bne  a0, t1, .Lds_empty
                addi s1, s1, 1
                li   t0, 16
                mul  t0, s2, t0
                addi t0, t0, 32
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                sw   zero, 4(s4)
                sd   zero, 8(s4)
                sw   zero, 16(s4)
                sw   zero, 20(s4)
                addi s3, s4, 24
            .Lds_loop:
                bge  s1, s2, .Lds_fin
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 34
                beq  a0, t1, .Lds_close
                li   t1, 92
                bne  a0, t1, .Lds_plain
                addi s1, s1, 1
                bge  s1, s2, .Lds_fin
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 110
                beq  a0, t1, .Lds_nl
                li   t1, 116
                beq  a0, t1, .Lds_tab
                li   t1, 114
                beq  a0, t1, .Lds_cr
                li   t1, 98
                beq  a0, t1, .Lds_bs
                li   t1, 102
                beq  a0, t1, .Lds_ff
                li   t1, 117
                beq  a0, t1, .Lds_uni
                j    .Lds_plain
            .Lds_nl:
                li   a0, 10
                j    .Lds_plain
            .Lds_tab:
                li   a0, 9
                j    .Lds_plain
            .Lds_cr:
                li   a0, 13
                j    .Lds_plain
            .Lds_bs:
                li   a0, 8
                j    .Lds_plain
            .Lds_ff:
                li   a0, 12
                j    .Lds_plain
            .Lds_uni:
                addi s1, s1, 1           # primeiro dígito hex
                li   t3, 0               # code point
                li   t4, 4               # 4 dígitos
            .Lds_uhex:
                bge  s1, s2, .Lds_uenc
                add  t0, s0, s1
                lbu  t5, 24(t0)
                li   t6, 48
                blt  t5, t6, .Lds_uenc
                li   t6, 57
                ble  t5, t6, .Lds_udig
                li   t6, 65
                blt  t5, t6, .Lds_uenc
                li   t6, 70
                ble  t5, t6, .Lds_uup
                li   t6, 97
                blt  t5, t6, .Lds_uenc
                li   t6, 102
                bgt  t5, t6, .Lds_uenc
                addi t5, t5, -87         # a-f -> 10-15
                j    .Lds_uacc
            .Lds_uup:
                addi t5, t5, -55         # A-F -> 10-15
                j    .Lds_uacc
            .Lds_udig:
                addi t5, t5, -48         # 0-9 -> 0-9
            .Lds_uacc:
                slli t3, t3, 4
                or   t3, t3, t5
                addi s1, s1, 1
                addi t4, t4, -1
                bnez t4, .Lds_uhex
            .Lds_uenc:
                li   t6, 128
                blt  t3, t6, .Lds_u1
                li   t6, 2048
                blt  t3, t6, .Lds_u2
                li   t6, 65536
                blt  t3, t6, .Lds_u3
                srli t5, t3, 18
                ori  t5, t5, 240
                sb   t5, 0(s3)
                addi s3, s3, 1
                srli t5, t3, 12
                andi t5, t5, 63
                ori  t5, t5, 128
                sb   t5, 0(s3)
                addi s3, s3, 1
                srli t5, t3, 6
                andi t5, t5, 63
                ori  t5, t5, 128
                sb   t5, 0(s3)
                addi s3, s3, 1
                j    .Lds_ucont
            .Lds_u3:
                srli t5, t3, 12
                ori  t5, t5, 224
                sb   t5, 0(s3)
                addi s3, s3, 1
                srli t5, t3, 6
                andi t5, t5, 63
                ori  t5, t5, 128
                sb   t5, 0(s3)
                addi s3, s3, 1
                j    .Lds_ucont
            .Lds_u2:
                srli t5, t3, 6
                ori  t5, t5, 192
                sb   t5, 0(s3)
                addi s3, s3, 1
            .Lds_ucont:
                andi t5, t3, 63
                ori  t5, t5, 128
                sb   t5, 0(s3)
                addi s3, s3, 1
                j    .Lds_loop
            .Lds_u1:
                sb   t3, 0(s3)
                addi s3, s3, 1
                j    .Lds_loop
            .Lds_plain:
                sb   a0, 0(s3)
                addi s3, s3, 1
                addi s1, s1, 1
                j    .Lds_loop
            .Lds_close:
                addi s1, s1, 1
            .Lds_fin:
                sub  t0, s3, s4
                addi t0, t0, -24
                sw   t0, 16(s4)
                add  t1, s4, t0
                addi t1, t1, 24
                sb   zero, 0(t1)
                mv   a0, s4
                mv   a2, s1
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret
            .Lds_empty:
                la   a0, .Lstr_empty
                li   a1, 0
                call kof_string_from_literal
                mv   a2, s1
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret

            """;
}
