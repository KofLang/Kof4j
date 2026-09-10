package dev.kof.compiler.nat;

// FASE 4 (STDLIB S10): fatia 27 de RISCV_RUNTIME_ASM_B — kof.random.
// Entropia por getrandom(2) via ecall — syscall 278 (CONFIRMADO no probe
// SECN000/B25, qemu-riscv64 E qemu-aarch64). R11: só a primitiva do SO.
//
// kof_random_double() -> Double [0,1): 8 bytes aleatorios, >>11 (53 bits de
//   mantissa), fcvt.d.l + fdiv.d por 2^53 (const .Lrnd_two53 em .rodata,
//   acessada com la/fld — mesma tecnica do B4). rc!=8 => 0.0 (falha honesta,
//   mesmo contrato do x86 — nunca saida fraca silenciosa). Return FP = f0
//   (convencao do tradutor: I2D/L2D usam f0; o cross-emit move f0->t0).
// kof_random_boolean() -> Bool (bit baixo do 1o byte; rc!=1 => 0).
// kof_random_int(bound)/kof_random_hex(n) => tail-j p/ B25-era? NAO: a
//   crypto lane x86 tem kof_sec_random_int/hex; no riscv eles ainda NAO
//   existem (SECN000 portou apenas a primitiva getrandom em B25 com shape
//   uuid). Aqui implemento os 4 direto sobre getrandom (self-contained):
//   int: 8 bytes, rejection sampling (range = floor(2^64/bound)*bound —
//   64-bit unsigned via ld; o x86 usa 32-bit div — sem paridade bit-level,
//   a distribuição uniforme é o contrato que a matriz prova); hex: bytes ->
//   2n hex minusculo.
//
// Frame -80 (16-align p/ kof_alloc; buf sp+0..15; s-regs 24..72; ra 72).
// Sub .Lrnd_hx: a0=nibble -> a0=char ('0'-'9'/'a'-'f'); usa t4/t5 (o
//   caller .Lrnd_h_loop guarda o byte em t3 — sub NÃO pode clobber).
// FP conv: f0 = return (fcvt.d.l; tradutor aarch64 precisa de ld+fmov.d.x
//   p/ fld e ucvtf p/ fcvt.d.l — adicionados ao NativeAarch64Translator).
// ⚠ `.section .text` NO TOPO (licao 7be4fd0a).
public final class NativeRiscvAsmRtB27 {

    static final String RISCV_RUNTIME_ASM_B_27 = """

            .section .text
            .globl kof_random_double
            kof_random_double:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                # --- getrandom(sp, 8, 0) ---
                addi a0, sp, 0
                li   a1, 8
                li   a2, 0
                li   a7, 278
                ecall
                li   t1, 8
                bne  a0, t1, .Lrnd_d_fail
                ld   t0, 0(sp)
                srli t0, t0, 11          # 53 bits (0..2^53-1)
                fcvt.d.l f0, t0
                la   t1, .Lrnd_two53
                fld   f1, 0(t1)
                fdiv.d f0, f0, f1
                j    .Lrnd_d_done
            .Lrnd_d_fail:
                fmv.d.x f0, zero        # 0.0 (falha do SO — mnemônico
                                        # traduzível; fcvt.d.w não é)
            .Lrnd_d_done:
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            .globl kof_random_boolean
            kof_random_boolean:
                addi sp, sp, -80
                addi a0, sp, 0
                li   a1, 1
                li   a2, 0
                li   a7, 278
                ecall
                li   t1, 1
                bne  a0, t1, .Lrnd_b_fail
                lbu  t0, 0(sp)
                andi a0, t0, 1
                j    .Lrnd_b_done
            .Lrnd_b_fail:
                li   a0, 0
            .Lrnd_b_done:
                addi sp, sp, 80
                ret

            # kof_random_int(a0=bound) -> Int em [0,bound); bound<=0 => 0
            # (blez trata negativo e zero).
            # range = floor(2^64/bound)*bound (64-bit unsigned — uniforme
            # em 64 bits; o x86 usa 32-bit div — sem paridade bit-level,
            # a distribuição uniforme é o contrato que a matriz prova).
            .globl kof_random_int
            kof_random_int:
                blez a0, .Lrnd_i_zero
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                mv   s0, a0              # bound
                # range = floor(2^64/bound)*bound  (64-bit unsigned):
                # divu (2^64-1)/bound + 1; rejeição garante x < range e
                # range*bound <= 2^64 (divu satura overflow p/ 2^64-1 no
                # bound=1, mas bound=1 => range=2^64-1*1+... remu com s0=1
                # = 0 — uniforme trivial; sem UB: divu nunca div-by-0
                # porque blez já desviou bound<=0).
                li   t1, -1              # 2^64-1
                divu t1, t1, s0          # floor((2^64-1)/bound)
                addi t1, t1, 1           # floor(2^64/bound)
                mul  s1, t1, s0          # s1 = range
            .Lrnd_i_retry:
                addi a0, sp, 0
                li   a1, 8
                li   a2, 0
                li   a7, 278
                ecall
                li   t1, 8
                bne  a0, t1, .Lrnd_i_fail
                ld   t0, 0(sp)           # 64-bit unsigned (ld — mesmo que o
                                         # OS escreva 4 bytes válidos, o buf
                                         # está zerado a mais no frame)
                bgeu t0, s1, .Lrnd_i_retry
                remu a0, t0, s0          # uniforme em [0,bound)
            .Lrnd_i_ok:
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            .Lrnd_i_fail:
                li   a0, 0
                j    .Lrnd_i_ok
            .Lrnd_i_zero:
                li   a0, 0
                ret

            # kof_random_hex(a0=nbytes) -> String 2n hex minusculo; n<=0 => null
            .globl kof_random_hex
            kof_random_hex:
                blez a0, .Lrnd_h_null
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                mv   s3, a0              # nbytes
                # --- getrandom(sp, nbytes, 0) ---
                addi a0, sp, 0
                mv   a1, s3
                li   a2, 0
                li   a7, 278
                ecall
                beq  a0, s3, .Lrnd_h_ok
                li   a0, 0               # falha/parcial => null (R11)
                j    .Lrnd_h_done
            .Lrnd_h_ok:
                # --- alloc String 2n: 2n+25 -> round 16 ---
                slli a0, s3, 1
                addi a0, a0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s0, a0              # novo obj
                li   t0, 1
                sw   t0, 0(s0)
                sw   zero, 4(s0)
                sd   zero, 8(s0)
                slli t0, s3, 1
                sw   t0, 16(s0)
                sw   zero, 20(s0)
                li   s1, 0               # i (byte)
                li   s2, 0               # out pos
            .Lrnd_h_loop:
                bge  s1, s3, .Lrnd_h_term
                add  t2, sp, s1
                lbu  t3, 0(t2)
                srli a0, t3, 4
                call .Lrnd_hx
                add  t4, s0, s2
                addi t4, t4, 24
                sb   a0, 0(t4)
                addi s2, s2, 1
                andi a0, t3, 15
                call .Lrnd_hx
                add  t4, s0, s2
                addi t4, t4, 24
                sb   a0, 0(t4)
                addi s2, s2, 1
                addi s1, s1, 1
                j    .Lrnd_h_loop
            .Lrnd_h_term:
                add  t0, s0, s2
                addi t0, t0, 24
                sb   zero, 0(t0)
                mv   a0, s0
            .Lrnd_h_done:
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            .Lrnd_h_null:
                li   a0, 0
                ret

            # nibble -> hex char (a0 in 0..15 -> a0 '0'-'9'/'a'-'f')
            # usa t4/t5 (NÃO t3 — o caller guarda o byte lá)
            .Lrnd_hx:
                li   t4, 10
                bge  a0, t4, .Lrnd_hx_af
                addi a0, a0, 48          # '0'
                ret
            .Lrnd_hx_af:
                addi a0, a0, 87          # 'a'-10
                ret

            .section .rodata
            .balign 8
            .Lrnd_two53:
                .quad 0x4340000000000000
            .section .text
        """;
}
