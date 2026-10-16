package dev.kof.compiler.nat;

// FASE 3 (REFACTOR-500): fatia 1 de RISCV_STRN002_ASM — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
public final class NativeRiscvAsmStrn1 {

    private NativeRiscvAsmStrn1() {}

    static  String RISCV_STRN002_ASM_1 = """
            .globl kof_string_equals_ignore_case
            kof_string_equals_ignore_case:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)
                sd   s1, 16(sp)
                sd   s2, 8(sp)
                sd   s3, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   s3, 16(s1)
                bne  s2, s3, .Lec_no
                li   t0, 0
            .Lec_loop:
                bge  t0, s2, .Lec_yes
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                addi t2, s1, 24
                add  t2, t2, t0
                lbu  t2, 0(t2)
                li   t3, 65
                blt  t1, t3, .Lec_f2
                li   t3, 90
                bgt  t1, t3, .Lec_f2
                addi t1, t1, 32
            .Lec_f2:
                li   t3, 65
                blt  t2, t3, .Lec_cmp
                li   t3, 90
                bgt  t2, t3, .Lec_cmp
                addi t2, t2, 32
            .Lec_cmp:
                bne  t1, t2, .Lec_no
                addi t0, t0, 1
                j    .Lec_loop
            .Lec_yes:
                li   a0, 1
                j    .Lec_ret
            .Lec_no:
                li   a0, 0
            .Lec_ret:
                ld   s3, 0(sp)
                ld   s2, 8(sp)
                ld   s1, 16(sp)
                ld   s0, 24(sp)
                ld   ra, 32(sp)
                addi sp, sp, 40
                ret

            # kof_string_split(str@a0, sep@a1) -> String[] (array de KofString)
            .globl kof_string_split
            kof_string_split:
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
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   t0, 16(s1)
                li   s3, 0
                beqz t0, .Lsk_count
                addi s3, s1, 24
                lbu  s3, 0(s3)
            .Lsk_count:
                li   s4, 1
                li   s5, 0
            .Lsk_cloop:
                bge  s5, s2, .Lsk_alloc
                addi t1, s0, 24
                add  t1, t1, s5
                lbu  t1, 0(t1)
                bne  t1, s3, .Lsk_cnext
                addi s4, s4, 1
            .Lsk_cnext:
                addi s5, s5, 1
                j    .Lsk_cloop
            .Lsk_alloc:
                mv   a0, s4
                li   a1, 8
                call kof_array_alloc
                mv   s6, a0
                li   s7, 0
                li   s5, 0
                li   s4, 0
            .Lsk_outer:
                bge  s5, s2, .Lsk_last
                addi t1, s0, 24
                add  t1, t1, s5
                lbu  t1, 0(t1)
                bne  t1, s3, .Lsk_onext
                call .Lsk_emit
                addi s4, s5, 1
                addi s5, s5, 1
                j    .Lsk_outer
            .Lsk_onext:
                addi s5, s5, 1
                j    .Lsk_outer
            .Lsk_last:
                bge  s4, s2, .Lsk_done
                mv   s5, s2
                call .Lsk_emit
            .Lsk_done:
                # §111 cross (B37): Java String.split remove vazios TRAILING
                # (port do .Lkof_split_done x86). s7 = pieces MATERIALIZADAS
                # (não o count da alocação — sobra pode ser lixo). Input
                # vazio: força [""] (JVM: "".split → size 1, piece "" — nunca
                # garbage do bump-allocator).
                beqz s2, .Lsk_empty
                mv   t1, s7                      # i = s7
            .Lsk_trim:
                addi t1, t1, -1
                bltz t1, .Lsk_set0
                addi t2, s6, 24
                slli t3, t1, 3
                add  t2, t2, t3
                ld   t2, 0(t2)                   # piece[i]
                lw   t3, 16(t2)                  # nBytes
                beqz t3, .Lsk_trim               # vazio -> continua podando
                addi t1, t1, 1                   # length = i+1
                sw   t1, 16(s6)
                mv   a0, s6
                j    .Lsk_ret
            .Lsk_set0:
                sw   zero, 16(s6)
                mv   a0, s6
                j    .Lsk_ret
            .Lsk_empty:
                li   a0, 32                      # (0+25+15)&-16
                call kof_alloc
                li   t2, 1
                sw   t2, 0(a0)
                sw   zero, 4(a0)
                sd   zero, 8(a0)
                sw   zero, 16(a0)                # nBytes = 0
                sw   zero, 20(a0)
                sb   zero, 24(a0)
                sd   a0, 24(s6)                  # element[0] = ""
                li   t0, 1
                sw   t0, 16(s6)                  # length = 1
                mv   a0, s6
                j    .Lsk_ret
            .Lsk_emit:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sub  t0, s5, s4
                sd   t0, 8(sp)              # len (sobrevive ao memcpy)
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                sd   a0, 0(sp)              # str nova
                ld   t0, 8(sp)              # recarrega len (alloc clobbera t0)
                li   t2, 1
                sw   t2, 0(a0)
                sw   zero, 4(a0)
                sd   zero, 8(a0)
                sw   t0, 16(a0)
                sw   zero, 20(a0)
                addi a0, a0, 24
                addi a1, s0, 24
                add  a1, a1, s4
                mv   a2, t0
                call kof_memcpy
                ld   t1, 0(sp)
                ld   t0, 8(sp)
                li   t2, 0
                addi t3, t1, 24
                add  t3, t3, t0
                sb   t2, 0(t3)
                addi t2, s6, 24             # array: dados inline em 24+i*8
                slli t3, s7, 3
                add  t2, t2, t3
                sd   t1, 0(t2)
                addi s7, s7, 1
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lsk_ret:
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

            # §655: kof_string_split_limit(str@a0, sep@a1, limit@a2) — o
            # contrato JDK da sobrecarga (String, Int): limit>0 = no máximo
            # `limit` pieces e a ÚLTIMA é o resto não dividido (sem a poda
            # §111), limit==0 = ilimitado COM a poda §111, limit<0 =
            # ilimitado SEM a poda. Input vazio → [""] para TODO limit.
            # Espelha kof_string_split peça a peça e REUSA o .Lsk_emit
            # (só t-regs/a-regs — o limit vive em s8, callee-saved, que o
            # emit não toca; o GC conserva callee-saved como os s0-s7 vivos
            # do irmão). aarch64 herda via tradução (s8 → x27).
            .globl kof_string_split_limit
            kof_string_split_limit:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                sd   s6, 32(sp)
                sd   s7, 24(sp)
                sd   s8, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s8, a2
                lw   s2, 16(s0)
                lw   t0, 16(s1)
                li   s3, 0
                beqz t0, .Lskl_count
                addi s3, s1, 24
                lbu  s3, 0(s3)
            .Lskl_count:
                li   s4, 1
                li   s5, 0
            .Lskl_cloop:
                bge  s5, s2, .Lskl_cap
                addi t1, s0, 24
                add  t1, t1, s5
                lbu  t1, 0(t1)
                bne  t1, s3, .Lskl_cnext
                addi s4, s4, 1
            .Lskl_cnext:
                addi s5, s5, 1
                j    .Lskl_cloop
            .Lskl_cap:
                blez s8, .Lskl_alloc
                bge  s4, s8, .Lskl_capped
                j    .Lskl_alloc
            .Lskl_capped:
                mv   s4, s8
            .Lskl_alloc:
                mv   a0, s4
                li   a1, 8
                call kof_array_alloc
                mv   s6, a0
                li   s7, 0
                li   s5, 0
                li   s4, 0
            .Lskl_outer:
                bge  s5, s2, .Lskl_last
                blez s8, .Lskl_norm
                addi t2, s8, -1
                bne  s7, t2, .Lskl_norm
                mv   s5, s2
                call .Lsk_emit
                j    .Lskl_done
            .Lskl_norm:
                addi t1, s0, 24
                add  t1, t1, s5
                lbu  t1, 0(t1)
                bne  t1, s3, .Lskl_onext
                call .Lsk_emit
                addi s4, s5, 1
                addi s5, s5, 1
                j    .Lskl_outer
            .Lskl_onext:
                addi s5, s5, 1
                j    .Lskl_outer
            .Lskl_last:
                mv   s5, s2
                call .Lsk_emit
            .Lskl_done:
                beqz s2, .Lskl_empty
                beqz s8, .Lskl_trimstart
                j    .Lskl_setcount
            .Lskl_trimstart:
                mv   t1, s7
            .Lskl_trim:
                addi t1, t1, -1
                bltz t1, .Lskl_set0
                addi t2, s6, 24
                slli t3, t1, 3
                add  t2, t2, t3
                ld   t2, 0(t2)
                lw   t3, 16(t2)
                beqz t3, .Lskl_trim
                addi t1, t1, 1
                sw   t1, 16(s6)
                mv   a0, s6
                j    .Lskl_ret
            .Lskl_set0:
                sw   zero, 16(s6)
                mv   a0, s6
                j    .Lskl_ret
            .Lskl_setcount:
                sw   s7, 16(s6)
                mv   a0, s6
                j    .Lskl_ret
            .Lskl_empty:
                li   a0, 32
                call kof_alloc
                li   t2, 1
                sw   t2, 0(a0)
                sw   zero, 4(a0)
                sd   zero, 8(a0)
                sw   zero, 16(a0)
                sw   zero, 20(a0)
                sb   zero, 24(a0)
                sd   a0, 24(s6)
                li   t0, 1
                sw   t0, 16(s6)
                mv   a0, s6
                j    .Lskl_ret
            .Lskl_ret:
                ld   s8, 16(sp)
                ld   s7, 24(sp)
                ld   s6, 32(sp)
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret
            # §738 (face CROSS riscv64): split("") — separador VAZIO. O
            # kof_string_split recebia sep=0 (NUL), que nunca casa -> 1
            # elemento; o JVM/Script/JS dao UM elemento por CARACTERE. Percorre
            # por CODE POINT UTF-8 (a direcao do D-STR-UNICODE) e REUSA o
            # .Lsk_emit do kof_string_split (mesma interface s0/s4/s5/s6/s7).
            # limit>0 = no maximo `limit` pieces, a ULTIMA sendo o RESTO (JDK).
            .globl kof_string_split_chars
            kof_string_split_chars:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                sd   s6, 32(sp)
                sd   s7, 24(sp)
                sd   s8, 16(sp)
                mv   s0, a0
                mv   s8, a1
                lw   s2, 16(s0)
                li   s1, 0
                li   t0, 0
            .Lskc_count:
                bge  t0, s2, .Lskc_cdone
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                andi t1, t1, 0xC0
                li   t2, 0x80
                beq  t1, t2, .Lskc_cnext
                addi s1, s1, 1
            .Lskc_cnext:
                addi t0, t0, 1
                j    .Lskc_count
            .Lskc_cdone:
                bnez s2, .Lskc_limit
                li   s1, 1
                j    .Lskc_alloc
            .Lskc_limit:
                blez s8, .Lskc_alloc
                ble  s1, s8, .Lskc_alloc
                mv   s1, s8
            .Lskc_alloc:
                mv   a0, s1
                li   a1, 8
                call kof_array_alloc
                mv   s6, a0
                li   s7, 0
                li   s5, 0
            .Lskc_loop:
                bge  s5, s2, .Lskc_tail
                addi t1, s0, 24
                add  t1, t1, s5
                lbu  t0, 0(t1)
                li   s3, 1
                andi t2, t0, 0xE0
                li   t3, 0xC0
                beq  t2, t3, .Lskc_l2
                andi t2, t0, 0xF0
                li   t3, 0xE0
                beq  t2, t3, .Lskc_l3
                andi t2, t0, 0xF8
                li   t3, 0xF0
                beq  t2, t3, .Lskc_l4
                j    .Lskc_len
            .Lskc_l2:
                li   s3, 2
                j    .Lskc_len
            .Lskc_l3:
                li   s3, 3
                j    .Lskc_len
            .Lskc_l4:
                li   s3, 4
            .Lskc_len:
                blez s8, .Lskc_cut
                addi t0, s7, 1
                bne  t0, s8, .Lskc_cut
                sub  s3, s2, s5
            .Lskc_cut:
                mv   s4, s5
                add  s5, s5, s3
                call .Lsk_emit
                j    .Lskc_loop
            .Lskc_tail:
                bnez s2, .Lskc_setlen
                mv   s4, s5
                call .Lsk_emit
            .Lskc_setlen:
                sw   s1, 16(s6)
                mv   a0, s6
                ld   s8, 16(sp)
                ld   s7, 24(sp)
                ld   s6, 32(sp)
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret

            # §738 (face CROSS): replace("", to) — agulha VAZIA. O
            # kof_string_replace procura a SUBSTRING da agulha e 0 bytes nunca
            # casa -> a string voltava INTACTA; o JVM/Script inserem `to` em
            # TODA posicao, incl. as duas pontas. Por CODE POINT UTF-8 e com os
            # lacos INLINE (sem kof_memcpy por byte — a licao do x86).
            .globl kof_string_replace_empty
            kof_string_replace_empty:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                sd   s6, 32(sp)
                sd   s7, 24(sp)
                sd   s8, 16(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   s3, 16(s1)
                li   s4, 0
                li   t0, 0
            .Lkre_count:
                bge  t0, s2, .Lkre_cdone
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                andi t1, t1, 0xC0
                li   t2, 0x80
                beq  t1, t2, .Lkre_cnext
                addi s4, s4, 1
            .Lkre_cnext:
                addi t0, t0, 1
                j    .Lkre_count
            .Lkre_cdone:
                addi t0, s4, 1
                mul  t0, t0, s3
                add  t0, t0, s2
                mv   s5, t0
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s6, a0
                li   t2, 1
                sw   t2, 0(a0)
                sw   zero, 4(a0)
                sd   zero, 8(a0)
                sw   s5, 16(a0)
                sw   zero, 20(a0)
                addi t3, a0, 24
                add  t3, t3, s5
                sb   zero, 0(t3)
                li   s7, 0
                li   s8, 0
            .Lkre_loop:
                bge  s7, s2, .Lkre_last
                li   t0, 0
            .Lkre_to:
                bge  t0, s3, .Lkre_len
                addi t1, s1, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                addi t2, s6, 24
                add  t2, t2, s8
                add  t2, t2, t0
                sb   t1, 0(t2)
                addi t0, t0, 1
                j    .Lkre_to
            .Lkre_len:
                add  s8, s8, s3
                addi t1, s0, 24
                add  t1, t1, s7
                lbu  t0, 0(t1)
                li   s5, 1
                andi t2, t0, 0xE0
                li   t3, 0xC0
                beq  t2, t3, .Lkre_l2
                andi t2, t0, 0xF0
                li   t3, 0xE0
                beq  t2, t3, .Lkre_l3
                andi t2, t0, 0xF8
                li   t3, 0xF0
                beq  t2, t3, .Lkre_l4
                j    .Lkre_cp
            .Lkre_l2:
                li   s5, 2
                j    .Lkre_cp
            .Lkre_l3:
                li   s5, 3
                j    .Lkre_cp
            .Lkre_l4:
                li   s5, 4
            .Lkre_cp:
                li   t0, 0
            .Lkre_cpl:
                bge  t0, s5, .Lkre_adv
                addi t1, s0, 24
                add  t1, t1, s7
                add  t1, t1, t0
                lbu  t1, 0(t1)
                addi t2, s6, 24
                add  t2, t2, s8
                add  t2, t2, t0
                sb   t1, 0(t2)
                addi t0, t0, 1
                j    .Lkre_cpl
            .Lkre_adv:
                add  s8, s8, s5
                add  s7, s7, s5
                j    .Lkre_loop
            .Lkre_last:
                li   t0, 0
            .Lkre_fin:
                bge  t0, s3, .Lkre_ret
                addi t1, s1, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                addi t2, s6, 24
                add  t2, t2, s8
                add  t2, t2, t0
                sb   t1, 0(t2)
                addi t0, t0, 1
                j    .Lkre_fin
            .Lkre_ret:
                mv   a0, s6
                ld   s8, 16(sp)
                ld   s7, 24(sp)
                ld   s6, 32(sp)
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret

            """;
}
