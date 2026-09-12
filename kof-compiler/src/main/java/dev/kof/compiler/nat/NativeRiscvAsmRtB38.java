package dev.kof.compiler.nat;

/**
 * Fatia B38 — kof.strings indent / dedent para riscv64 (PR #88 / #87).
 * B37 já estava tomado por kof_multi_alloc (§113) na beta — fatia renumerada
 * com 0 colisões .L/.globl verificadas (lição B25b/B36).
 * aarch64 herda via tradutor.
 */
final class NativeRiscvAsmRtB38 {

    private NativeRiscvAsmRtB38() {}

    static final String RISCV_RUNTIME_ASM_B_38 = """
            .section .text

            # kof_strings_indent(a0=v, a1=n) -> a0=String
            .globl kof_strings_indent
            kof_strings_indent:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   s6, 16(sp)

                mv   s0, a0              # v
                mv   s6, a1              # n
                beqz s0, .Lsi_r_ret_orig
                blez s6, .Lsi_r_ret_orig
                lw   s1, 16(s0)          # len
                blez s1, .Lsi_r_ret_orig

                # 1. Contar quebras de linha
                li   s2, 1               # line_count = 1
                li   t0, 0               # i = 0
            .Lsi_r_cnt_lines:
                bge  t0, s1, .Lsi_r_alloc
                add  t1, s0, t0
                lbu  t2, 24(t1)
                addi t0, t0, 1
                li   t3, 10              # '\\n'
                bne  t2, t3, .Lsi_r_cnt_lines
                addi s2, s2, 1
                j    .Lsi_r_cnt_lines

            .Lsi_r_alloc:
                mul  t1, s2, s6          # line_count * n
                add  t1, t1, s1          # + len
                addi a0, t1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0              # dst
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   zero, 20(s2)

                li   s3, 0               # in_pos = 0
                li   s4, 0               # out_pos = 0
                li   s5, 1               # at_line_start = 1

            .Lsi_r_loop:
                bge  s3, s1, .Lsi_r_term
                add  t0, s0, s3
                lbu  t0, 24(t0)          # char
                addi s3, s3, 1

                li   t1, 10              # '\\n'
                beq  t0, t1, .Lsi_r_is_lf
                li   t1, 13              # '\\r'
                beq  t0, t1, .Lsi_r_put_char

                beqz s5, .Lsi_r_put_char # se não está no início da linha, emite direto
                # emite n espaços
                li   t2, 0               # k = 0
            .Lsi_r_pad:
                bge  t2, s6, .Lsi_r_pad_done
                add  t3, s2, s4
                li   t4, 32
                sb   t4, 24(t3)
                addi s4, s4, 1
                addi t2, t2, 1
                j    .Lsi_r_pad
            .Lsi_r_pad_done:
                li   s5, 0               # at_line_start = 0

            .Lsi_r_put_char:
                add  t3, s2, s4
                sb   t0, 24(t3)
                addi s4, s4, 1
                j    .Lsi_r_loop

            .Lsi_r_is_lf:
                add  t3, s2, s4
                sb   t0, 24(t3)
                addi s4, s4, 1
                li   s5, 1               # at_line_start = 1
                j    .Lsi_r_loop

            .Lsi_r_term:
                sw   s4, 16(s2)          # len
                add  t0, s2, s4
                sb   zero, 24(t0)        # NUL
                mv   a0, s2
                j    .Lsi_r_done

            .Lsi_r_ret_orig:
                mv   a0, s0
            .Lsi_r_done:
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

            # kof_strings_dedent(a0=v) -> a0=String
            .globl kof_strings_dedent
            kof_strings_dedent:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   s6, 16(sp)

                mv   s0, a0              # v
                sd   a0, 8(sp)           # v no slot livre do frame (64(sp) é o s0 do caller)
                beqz s0, .Lsd_r_ret_orig
                lw   s1, 16(s0)          # len
                blez s1, .Lsd_r_ret_orig

                # Passo 1: Calcular min_indent entre linhas com conteúdo
                li   s2, -1              # min_indent = -1
                li   s3, 0               # i = 0
            .Lsd_r_scan_line:
                bge  s3, s1, .Lsd_r_scan_done
                li   s4, 0               # ws = 0
            .Lsd_r_cnt_ws:
                add  t0, s3, s4
                bge  t0, s1, .Lsd_r_chk_content
                add  t1, s0, t0
                lbu  t2, 24(t1)
                li   t3, 32              # ' '
                beq  t2, t3, .Lsd_r_inc_ws
                li   t3, 9               # '\\t'
                beq  t2, t3, .Lsd_r_inc_ws
                j    .Lsd_r_chk_content
            .Lsd_r_inc_ws:
                addi s4, s4, 1
                j    .Lsd_r_cnt_ws

            .Lsd_r_chk_content:
                add  t0, s3, s4
                bge  t0, s1, .Lsd_r_skip_to_lf
                add  t1, s0, t0
                lbu  t2, 24(t1)
                li   t3, 10              # '\\n'
                beq  t2, t3, .Lsd_r_skip_to_lf
                li   t3, 13              # '\\r'
                beq  t2, t3, .Lsd_r_skip_to_lf
                # Linha tem conteúdo não-espaço!
                li   t4, -1
                beq  s2, t4, .Lsd_r_set_min
                bge  s4, s2, .Lsd_r_skip_to_lf
            .Lsd_r_set_min:
                mv   s2, s4

            .Lsd_r_skip_to_lf:
                bge  s3, s1, .Lsd_r_scan_done
                add  t0, s0, s3
                lbu  t1, 24(t0)
                addi s3, s3, 1
                li   t2, 10
                bne  t1, t2, .Lsd_r_skip_to_lf
                j    .Lsd_r_scan_line

            .Lsd_r_scan_done:
                blez s2, .Lsd_r_ret_orig

                # Passo 2: Alocar buffer e remover s2 espaços do início de cada linha
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0              # dst
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   zero, 20(s3)

                li   s4, 0               # in_pos = 0
                li   s5, 0               # out_pos = 0
                li   s6, 1               # at_line_start = 1
                li   s0, 0               # ws_skipped = 0 (reaproveitando s0 temporariamente)
                mv   t6, a0              # salva dst

            .Lsd_r_emit_loop:
                bge  s4, s1, .Lsd_r_emit_term
                ld   t0, 8(sp)           # recarrega v original (slot próprio)
                add  t0, t0, s4
                lbu  t0, 24(t0)          # char
                addi s4, s4, 1

                li   t1, 10              # '\\n'
                beq  t0, t1, .Lsd_r_emit_lf
                li   t1, 13              # '\\r'
                beq  t0, t1, .Lsd_r_put_byte

                beqz s6, .Lsd_r_put_byte
                # No início da linha: se for espaço/tab e ws_skipped < min_indent, pula
                li   t2, 32
                beq  t0, t2, .Lsd_r_try_skip
                li   t2, 9
                beq  t0, t2, .Lsd_r_try_skip
                li   s6, 0               # para de pular
                j    .Lsd_r_put_byte

            .Lsd_r_try_skip:
                bge  s0, s2, .Lsd_r_stop_skip
                addi s0, s0, 1
                j    .Lsd_r_emit_loop
            .Lsd_r_stop_skip:
                li   s6, 0

            .Lsd_r_put_byte:
                add  t1, s3, s5
                sb   t0, 24(t1)
                addi s5, s5, 1
                j    .Lsd_r_emit_loop

            .Lsd_r_emit_lf:
                add  t1, s3, s5
                sb   t0, 24(t1)
                addi s5, s5, 1
                li   s6, 1               # at_line_start = 1
                li   s0, 0               # ws_skipped = 0
                j    .Lsd_r_emit_loop

            .Lsd_r_emit_term:
                sw   s5, 16(s3)
                add  t0, s3, s5
                sb   zero, 24(t0)
                mv   a0, s3
                j    .Lsd_r_done

            .Lsd_r_ret_orig:
                ld   a0, 8(sp)           # v original (slot próprio)
            .Lsd_r_done:
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
