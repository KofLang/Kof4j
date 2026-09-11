package dev.kof.compiler.nat;

// STDLIB S10b: fatia 28 de RISCV_RUNTIME_ASM_B — kof.random randomString.
// kof_random_string(a0=n, a1=alphabet) -> String de n chars, cada um
// uniforme do alfabeto (reusa kof_random_int da B27 — getrandom por char,
// paridade exata do x86 que chama kof_sec_random_int por char).
//
// Borda leniente (mesma do repeat x86): n<=0 OU alphabet null/vazio -> ""
// (alloc 40, len 0, NUL em 24). UTF-8/ASCII: layout de String nativa só
// guarda bytes — paridade UTF-16 é JVM/JS, como todo o S2.
//
// Frame -48 (16-align: ra, s4, s3, s2, s1, s0 = 6*8=48): i precisa ser
// s-reg (sobrevive ao call kof_random_int, que clobbers t0/t1).
// aarch64 herda linha-a-linha (lw/sd/sb/b*/call já mapeados; imediais
// <=2047 via aarch64AddSubImm).
public final class NativeRiscvAsmRtB28 {

    static final String RISCV_RUNTIME_ASM_B_28 = """

            # ── kof.random randomString (STDLIB S10b) ─────────────────────
            # kof_random_string(a0=n, a1=alphabet) -> String
            .globl kof_random_string
            kof_random_string:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s4, 32(sp)
                sd   s3, 24(sp)
                sd   s2, 16(sp)
                sd   s1, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0              # n
                mv   s1, a1              # alphabet
                blez s0, .Lrs_empty
                beqz s1, .Lrs_empty
                lw   s2, 16(s1)          # alen
                blez s2, .Lrs_empty
                # alloc n+25 -> round 16 (n+25+15 & -16)
                addi a0, s0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0              # novo
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   s0, 16(s3)          # len = n
                sw   zero, 20(s3)
                li   s4, 0               # i
            .Lrs_loop:
                bge  s4, s0, .Lrs_term
                mv   a0, s2              # bound = alen
                call kof_random_int      # a0 = idx em [0,alen)
                addi t0, s1, 24
                add  t0, t0, a0
                lbu  t1, 0(t0)           # alphabet[idx]
                add  t2, s3, s4
                addi t2, t2, 24
                sb   t1, 0(t2)
                addi s4, s4, 1
                j    .Lrs_loop
            .Lrs_term:
                add  t0, s3, s0
                addi t0, t0, 24
                sb   zero, 0(t0)         # NUL
                mv   a0, s3
                j    .Lrs_ret
            .Lrs_empty:                  # "" (shape do repeat x86: 40 bytes)
                li   a0, 40
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   zero, 16(s3)        # len 0
                sw   zero, 20(s3)
                sb   zero, 24(s3)
                mv   a0, s3
            .Lrs_ret:
                ld   s0, 0(sp)
                ld   s1, 8(sp)
                ld   s2, 16(sp)
                ld   s3, 24(sp)
                ld   s4, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            """;
}
