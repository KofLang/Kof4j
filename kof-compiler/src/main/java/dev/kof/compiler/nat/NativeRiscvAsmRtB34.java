package dev.kof.compiler.nat;

// FASE (bug 43 cross, estágio 2): fatia 34 de RISCV_RUNTIME_ASM_B —
// substring/indexOf/lastIndexOf riscv64 em CODE UNITS UTF-16. Port fiel do
// modelo x86_64 (runtime/RuntimeStringOps.emitStringSubstring +
// RuntimeStringSearch.emitStringIndexOf/LastIndexOf): o walk de LEAD BYTES
// `.Lkof_substr_walk` converte code-unit→byte-offset (1/2/3 bytes → 1 unit,
// astral 4 bytes → 2 units, nunca decodifica — só conta), corte no meio de um
// par astral → cut (substring: diagnóstico R6; indexOf/lastIndexOf: pula a
// posição, needle well-formed nunca casa numa 2ª unit). Needle vazio → 0 /
// total (JVM). needle maior que o alvo → -1. A cópia é a fatia de BYTES entre
// as duas fronteiras (par astral sempre inteiro).
//
// LIÇÕES aplicadas:
//  - walk é FOLHA: só t-regs + a2/a3 scratch, preserva a0/a1 → callers guardam
//    estado em s-regs e SEMPRE salvam/restauram cada s-reg que escrevem
//    (o path antigo não precisava de s6..s8; aqui sim).
//  - cut é flag 0/1 → teste `bnez`, NUNCA `bltz` (1 não é <0).
//  - walk recebe o PONTEIRO str (lê nBytes em 16(a0) e bytes em 24(a0)); os
//    `+24` de acesso a byte ficam NO CALLER, não no arg do walk.
//  - retorno a0: sempre `sext.w` quando pode ser -1 (indexOf/lastIndexOf) ou
//    contagem (substring nunca negativa) — lição da B32 (o tradutor faz lw →
//    ldr w = zero-extend; o valor vem em reg 64-bit de `li`, `mv`, `sub` —
//    sext.w garante o wrap int32 do JVM/JS).
//
// ⚠ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB34 {

    private NativeRiscvAsmRtB34() {}

    static final String RISCV_RUNTIME_ASM_B_34 = """

            .section .text
            # kof_su_walk(a0=str, a1=targetUnits) -> t0=byteOff, t1=units,
            # t2=cut(0/1). FOLHA: só t-regs + a2/a3 scratch, preserva a0/a1.
            .globl kof_su_walk
            kof_su_walk:
                li   t0, 0                   # off (bytes)
                li   t1, 0                   # units consumidas
                li   t2, 0                   # cut flag
                lw   a2, 16(a0)              # nBytes
                addi a3, a0, 24             # bytes base
            .Ls43w_loop:
                bgeu t1, a1, .Ls43w_hit
                bgeu t0, a2, .Ls43w_end
                add  t3, a3, t0
                lbu  t3, 0(t3)              # lead cru
                li   t4, 0x80
                bltu t3, t4, .Ls43w_c1
                li   t4, 0xE0
                and  t5, t3, t4
                li   t4, 0xC0
                beq  t5, t4, .Ls43w_c2
                li   t4, 0xF0
                and  t5, t3, t4
                li   t4, 0xE0
                beq  t5, t4, .Ls43w_c3
                addi t4, t1, 1
                beq  t4, a1, .Ls43w_cut
                addi t1, t1, 2
                addi t0, t0, 4
                j    .Ls43w_loop
            .Ls43w_c1:
                addi t1, t1, 1
                addi t0, t0, 1
                j    .Ls43w_loop
            .Ls43w_c2:
                addi t1, t1, 1
                addi t0, t0, 2
                j    .Ls43w_loop
            .Ls43w_c3:
                addi t1, t1, 1
                addi t0, t0, 3
                j    .Ls43w_loop
            .Ls43w_hit:
                ret
            .Ls43w_cut:
                li   t2, 1
                ret
            .Ls43w_end:
                mv   t0, a2
                ret

            # kof_su_substring(a0=str, a1=start, a2=end) -> KofStr*
            # end=0 (marker call-site 1-arg) → até o fim. Índices em code
            # units; bounds fora → kof_bounds_error; corte de par → panic R6.
            .globl kof_su_substring
            kof_su_substring:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0                # str
                mv   s1, a1                # start (units)
                mv   s6, a2                # end (a2 é clobbered pelo walk!)
                bltz s1, .Ls43sub_bounds
                call kof_su_walk           # a0=str, a1=start
                bnez t2, .Ls43sub_panic
                blt  t1, s1, .Ls43sub_bounds   # start > total
                mv   s5, t0                # startBytes
                beqz s6, .Ls43sub_toend
                mv   a0, s0
                mv   a1, s6                # end
                call kof_su_walk
                bnez t2, .Ls43sub_panic
                blt  t1, s6, .Ls43sub_bounds   # end > total
                blt  t0, s5, .Ls43sub_bounds   # end < start
                mv   s4, t0                # endBytes
                j    .Ls43sub_copy
            .Ls43sub_toend:
                lw   s4, 16(s0)            # endBytes = nBytes
            .Ls43sub_copy:
                sub  s3, s4, s5            # lenBytes (>= 0)
                sext.w s3, s3
                addi a0, s3, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0                # novo KofStr*
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   s3, 16(s2)
                sw   zero, 20(s2)
                addi a0, s2, 24            # dst
                addi a1, s0, 24            # src base
                add  a1, a1, s5            # + startBytes
                mv   a2, s3                # n
                call kof_memcpy
                addi t1, s2, 24
                add  t1, t1, s3
                sb   zero, 0(t1)           # NUL
                mv   a0, s2
                j    .Ls43sub_ret
            .Ls43sub_panic:
                la   a0, .Lstr_substr_astral
                call kof_panic
            .Ls43sub_bounds:
                call kof_bounds_error
            .Ls43sub_ret:
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                ld   s6, 0(sp)
                addi sp, sp, 64
                ret

            # kof_su_index_of(a0=haystack, a1=needle) -> Int unit, -1
            .globl kof_su_index_of
            kof_su_index_of:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s5, 16(sp)
                sd   s6, 8(sp)
                sd   s7, 0(sp)
                mv   s0, a0                # H
                mv   s1, a1                # N
                li   a1, 0x7FFFFFFF
                call kof_su_walk           # a0=H
                mv   s2, t1                # totalH
                mv   a0, s1                # N
                li   a1, 0x7FFFFFFF
                call kof_su_walk           # a0=N
                mv   s3, t1                # totalN
                mv   s5, t0                # lenBytes needle
                beqz s3, .Ls43i_zero       # needle vazio → 0
                bgt  s3, s2, .Ls43i_no     # needle > alvo → -1
                sub  s7, s2, s3            # limite = totalH - totalN
                li   s6, 0                 # i (unit)
            .Ls43i_scan:
                bgt  s6, s7, .Ls43i_no
                mv   a0, s0
                mv   a1, s6
                call kof_su_walk
                bnez t2, .Ls43i_next       # corte de par: não casa aqui
                addi a0, s0, 24            # bytes base
                add  a0, a0, t0            # + startBytes
                addi a2, s1, 24            # needle bytes
                li   t2, 0                 # j
            .Ls43i_cmp:
                bgeu t2, s5, .Ls43i_found
                add  t3, a0, t2
                lbu  t3, 0(t3)
                add  t4, a2, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Ls43i_next
                addi t2, t2, 1
                j    .Ls43i_cmp
            .Ls43i_next:
                addi s6, s6, 1
                j    .Ls43i_scan
            .Ls43i_zero:
                li   s6, 0
            .Ls43i_found:
                mv   a0, s6
                j    .Ls43i_ret
            .Ls43i_no:
                li   a0, -1
            .Ls43i_ret:
                sext.w a0, a0
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s5, 16(sp)
                ld   s6, 8(sp)
                ld   s7, 0(sp)
                addi sp, sp, 64
                ret

            # kof_su_last_index_of(a0=haystack, a1=needle) -> Int unit, -1
            .globl kof_su_last_index_of
            kof_su_last_index_of:
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
                mv   s0, a0                # H
                mv   s1, a1                # N
                li   a1, 0x7FFFFFFF
                call kof_su_walk           # a0=H
                mv   s2, t1                # totalH
                mv   a0, s1
                li   a1, 0x7FFFFFFF
                call kof_su_walk           # a0=N
                mv   s3, t1                # totalN
                mv   s5, t0                # lenBytes needle
                beqz s3, .Ls43l_end0       # needle vazio → totalH
                bgt  s3, s2, .Ls43l_no     # needle > alvo → -1
                sub  s4, s2, s3            # i = totalH - totalN
                addi s6, s0, 24            # bytes base (H+24) — imutável
            .Ls43l_scan:
                bltz s4, .Ls43l_no
                mv   a0, s0
                mv   a1, s4
                call kof_su_walk
                bnez t2, .Ls43l_next       # corte de par: pula
                mv   s7, t0                # startBytes (walk clobbers t0..t2)
                addi a0, s6, 0             # dst base
                add  a0, a0, s7
                addi a2, s1, 24            # needle bytes
                li   t2, 0                 # j
            .Ls43l_cmp:
                bgeu t2, s5, .Ls43l_found
                add  t3, a0, t2
                lbu  t3, 0(t3)
                add  t4, a2, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Ls43l_next
                addi t2, t2, 1
                j    .Ls43l_cmp
            .Ls43l_next:
                addi s4, s4, -1
                j    .Ls43l_scan
            .Ls43l_found:
                mv   a0, s4
                j    .Ls43l_ret
            .Ls43l_end0:
                mv   a0, s2
                j    .Ls43l_ret
            .Ls43l_no:
                li   a0, -1
            .Ls43l_ret:
                sext.w a0, a0
                ld   ra, 88(sp)
                ld   s0, 80(sp)
                ld   s1, 72(sp)
                ld   s2, 64(sp)
                ld   s3, 56(sp)
                ld   s4, 48(sp)
                ld   s5, 40(sp)
                ld   s6, 32(sp)
                ld   s7, 24(sp)
                ld   s8, 16(sp)
                addi sp, sp, 96
                ret

            .section .rodata
            .Lstr_substr_astral: .asciz "Runtime error: substring cannot split an astral code point (native UTF-16 face pending, known-bugs 43)"
            """;
}
