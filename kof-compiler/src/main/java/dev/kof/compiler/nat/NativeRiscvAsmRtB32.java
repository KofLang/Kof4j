package dev.kof.compiler.nat;

// FASE (bug 97 cross): fatia 32 de RISCV_RUNTIME_ASM_B — String.compareTo /
// String.hashCode riscv64. Port do algoritmo x86_64 (runtime/
// RuntimeStringCompare.java): decode UTF-8 → SEQUÊNCIA de code units UTF-16
// (par astral → high, low pendurado no cursor em +4), sentinela de FIM -1
// (code unit 0/NUL é legítima), diff = primeira unit diferente (A−B, como o
// JVM), prefixo = diferença de contagem de units; hash = h*31+unit (32-bit
// wrap via mul+add+sext.w). Byte-sum/memcmp daria paridade FALSA astral vs BMP
// (mesma lição do bug 43). aarch64 vem do tradutor riscv→aarch (mesma lição do
// B25 uuid — só instruções já cobertas pelo tradutor). Regra dos 32 bits: o
// registrador riscv é de 64 bits; TODA aritmética que o JVM faz em int passa
// por sext.w (movslq no x86). kof_su_next é FOLHA (não usa ra, só t-regs) e
// preserva a0/a1/a2 → o chamador guarda o unit em t-regs/pilha ANTES do
// próximo call e o estado cruzado em s-regs. Lição do x86 (a): o byte cru é
// mascarado SÓ DEPOIS da validação da continuação (senão é(233)→192). Labels
// no namespace .Ls97* (B2 já usa .Lct*).
//
// ⚠ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB32 {

    private NativeRiscvAsmRtB32() {}

    static final String RISCV_RUNTIME_ASM_B_32 = """

            .section .text
            # kof_su_next(a0=bytes, a1=cursor{off@0,pendLow@4}, a2=nBytes)->a0=unit
            # (-1 = fim). Preserva a0/a1/a2; clobbers t0..t6; leaf (sem ra).
            .globl kof_su_next
            kof_su_next:
                lw   t0, 4(a1)
                bnez t0, .Ls97n_low
                lw   t1, 0(a1)
                bgeu t1, a2, .Ls97n_oob
                add  t2, a0, t1
                lbu  t0, 0(t2)             # lead cru
                li   t3, 0x80
                bltu t0, t3, .Ls97n_a1     # ASCII
                li   t3, 0xE0
                and  t4, t0, t3
                li   t3, 0xC0
                beq  t4, t3, .Ls97n_2
                li   t3, 0xF0
                and  t4, t0, t3
                li   t3, 0xE0
                beq  t4, t3, .Ls97n_3
                li   t3, 0xF8
                and  t4, t0, t3
                li   t3, 0xF0
                beq  t4, t3, .Ls97n_4
            .Ls97n_g1:                     # malformado: avança 1, devolve cru (t0)
                addi t1, t1, 1
                sw   t1, 0(a1)
                mv   a0, t0
                ret
            .Ls97n_low:
                mv   a0, t0
                sw   zero, 4(a1)
                ret
            .Ls97n_oob:
                li   a0, -1
                ret
            .Ls97n_a1:
                addi t1, t1, 1
                sw   t1, 0(a1)
                mv   a0, t0
                ret
            .Ls97n_2:                      # (l&1F)<<6 | (c1&3F)
                addi t2, t1, 1
                bgeu t2, a2, .Ls97n_g1
                add  t2, a0, t2
                lbu  t4, 0(t2)             # c1 cru
                li   t3, 0xC0
                and  t2, t4, t3
                li   t3, 0x80
                bne  t2, t3, .Ls97n_g1
                addi t1, t1, 2
                sw   t1, 0(a1)
                andi t0, t0, 0x1F
                slli t0, t0, 6
                andi t4, t4, 0x3F
                or   a0, t0, t4
                ret
            .Ls97n_3:                      # (l&0F)<<12 | (c1&3F)<<6 | (c2&3F)
                addi t2, t1, 1
                bgeu t2, a2, .Ls97n_g1
                add  t2, a0, t2
                lbu  t4, 0(t2)             # c1 cru
                li   t3, 0xC0
                and  t2, t4, t3
                li   t3, 0x80
                bne  t2, t3, .Ls97n_g1
                addi t2, t1, 2
                bgeu t2, a2, .Ls97n_g1
                add  t2, a0, t2
                lbu  t5, 0(t2)             # c2 cru
                li   t3, 0xC0
                and  t2, t5, t3
                li   t3, 0x80
                bne  t2, t3, .Ls97n_g1
                addi t1, t1, 3
                sw   t1, 0(a1)
                andi t0, t0, 0x0F
                slli t0, t0, 12
                andi t4, t4, 0x3F
                slli t4, t4, 6
                or   t0, t0, t4
                andi t5, t5, 0x3F
                or   a0, t0, t5
                ret
            .Ls97n_4:                      # astral → high retorna, low pendurado
                addi t2, t1, 1
                bgeu t2, a2, .Ls97n_g1
                add  t2, a0, t2
                lbu  t4, 0(t2)             # c1 cru
                li   t3, 0xC0
                and  t2, t4, t3
                li   t3, 0x80
                bne  t2, t3, .Ls97n_g1
                addi t2, t1, 2
                bgeu t2, a2, .Ls97n_g1
                add  t2, a0, t2
                lbu  t5, 0(t2)             # c2 cru
                li   t3, 0xC0
                and  t2, t5, t3
                li   t3, 0x80
                bne  t2, t3, .Ls97n_g1
                addi t2, t1, 3
                bgeu t2, a2, .Ls97n_g1
                add  t2, a0, t2
                lbu  t6, 0(t2)             # c3 cru
                li   t3, 0xC0
                and  t2, t6, t3
                li   t3, 0x80
                bne  t2, t3, .Ls97n_g1
                addi t2, t1, 4
                sw   t2, 0(a1)             # off += 4
                andi t0, t0, 0x07
                slli t0, t0, 18
                andi t4, t4, 0x3F
                slli t4, t4, 12
                or   t0, t0, t4
                andi t5, t5, 0x3F
                slli t5, t5, 6
                or   t0, t0, t5
                andi t6, t6, 0x3F
                or   t0, t0, t6            # cp
                li   t2, 0x10000
                sub  t0, t0, t2
                srli t4, t0, 10
                li   t2, 0xD800
                add  t4, t4, t2            # high
                andi t0, t0, 0x3FF
                li   t2, 0xDC00
                add  t0, t0, t2            # low
                sw   t0, 4(a1)             # pendura o low no cursor
                mv   a0, t4                # retorna o high
                ret

            # kof_string_compare_to(a0=strA, a1=strB) -> a0 = diff (Int, JVM)
            .globl kof_string_compare_to
            kof_string_compare_to:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                mv   s0, a0                # strA
                mv   s1, a1                # strB
                lw   s2, 16(s0)            # nBytesA
                lw   s3, 16(s1)            # nBytesB
                sw   zero, 0(sp)           # cursorA.off
                sw   zero, 4(sp)           # cursorA.pend
                li   s4, 0                 # nA (contagem de units)
            .Ls97c_ca:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s2
                call kof_su_next
                mv   t0, a0
                li   t1, -1
                beq  t0, t1, .Ls97c_cad
                addi s4, s4, 1
                j    .Ls97c_ca
            .Ls97c_cad:
                sw   zero, 8(sp)           # cursorB.off
                sw   zero, 12(sp)          # cursorB.pend
                li   s5, 0                 # nB
            .Ls97c_cb:
                addi a0, s1, 24
                addi a1, sp, 8
                mv   a2, s3
                call kof_su_next
                mv   t0, a0
                li   t1, -1
                beq  t0, t1, .Ls97c_cbd
                addi s5, s5, 1
                j    .Ls97c_cb
            .Ls97c_cbd:
                sw   zero, 0(sp)
                sw   zero, 4(sp)
                sw   zero, 8(sp)
                sw   zero, 12(sp)
            .Ls97c_loop:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s2
                call kof_su_next
                sw   a0, 16(sp)            # ua
                addi a0, s1, 24
                addi a1, sp, 8
                mv   a2, s3
                call kof_su_next           # ub em a0
                lw   t0, 16(sp)            # ua
                sext.w t0, t0              # lw do riscv é sign-extend; o tradutor
                                           # aarch faz ldr w (zero) — sext.w é
                                           # no-op no riscv e corrige no aarch
                                           # (o valor pode ser -1 = sentinela)
                li   t1, -1
                bne  a0, t0, .Ls97c_diff   # ub != ua
                beq  a0, t1, .Ls97c_cnt    # ambos no fim → conta de units
                j    .Ls97c_loop
            .Ls97c_diff:
                beq  t0, t1, .Ls97c_cnt    # A terminou → conta
                beq  a0, t1, .Ls97c_cnt    # B terminou → conta
                sub  a0, t0, a0            # ua - ub
                j    .Ls97c_ret
            .Ls97c_cnt:
                sub  a0, s4, s5            # nA - nB
            .Ls97c_ret:
                sext.w a0, a0
                ld   ra, 72(sp)
                ld   s0, 64(sp)
                ld   s1, 56(sp)
                ld   s2, 48(sp)
                ld   s3, 40(sp)
                ld   s4, 32(sp)
                ld   s5, 24(sp)
                addi sp, sp, 80
                ret

            # kof_string_hash_code(a0=str) -> a0 = h (Int; h = 31*h+unit por unit)
            .globl kof_string_hash_code
            kof_string_hash_code:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0                # str
                lw   s1, 16(s0)            # nBytes
                li   s2, 0                 # h
                sw   zero, 0(sp)
                sw   zero, 4(sp)
            .Ls97h_loop:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s1
                call kof_su_next
                mv   t0, a0
                li   t1, -1
                beq  t0, t1, .Ls97h_done
                li   t2, 31
                mul  s2, s2, t2
                add  s2, s2, t0
                sext.w s2, s2              # wrap int32
                j    .Ls97h_loop
            .Ls97h_done:
                mv   a0, s2
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                addi sp, sp, 48
                ret
            """;
}
