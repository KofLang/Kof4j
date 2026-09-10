package dev.kof.compiler.nat;

// STDLIB S10a: fatia 27 de RISCV_RUNTIME_ASM_B — kof.random (face NÃO-
// criptográfica: randomInt/randomBoolean). Entropia getrandom(2) ecall 278
// (mesma fonte da fatia B25/uuid; R11 — sem PRNG caseiro).
//
// kof_random_int(a0=bound) -> [0,bound): bound<=0 -> 0 (face leniente do
// plano; security.randomInt lança). Bound = LEMIRE reduzido sem overflow:
// raw = lw(getrandom 4B) & 0x7fffffff (positivo, < 2^31);
// v = ((raw*raw) >>> 31) % bound. raw*raw < 2^62 cabe em 64b; srli 31 +
// rem -> uniforme em [0,bound) a <=2^-31 (sem loop de rejeição).
// kof_random_bool() -> 0/1: getrandom 1 byte & 1.
// Frame -32 (16-align); s0 salvo; ecall clobbera t-regs -> li t1 DEPOIS do
// ecall. aarch64 herda linha-a-linha via NativeAarch64Translator
// (lw/li/and/mul/srli/rem/beqz/bltz/j já mapeados; li 2147483647 ->
// movz/movk, verificado).
public final class NativeRiscvAsmRtB27 {

    static final String RISCV_RUNTIME_ASM_B_27 = """

            # ── kof.random (STDLIB S10a) ──────────────────────────────────
            # kof_random_int(a0=bound) -> a0 [0,bound)
            .globl kof_random_int
            kof_random_int:
                addi sp, sp, -32
                sd   s0, 16(sp)
                mv   s0, a0
                beqz s0, .Lr_int_zero
                bltz s0, .Lr_int_zero
            .Lr_int_get:
                addi a0, sp, 0
                li   a1, 4
                li   a2, 0
                li   a7, 278
                ecall
                bltz a0, .Lr_int_fail
                lw   t0, 0(sp)
                li   t1, 2147483647          # 0x7fffffff (depois do ecall)
                and  t0, t0, t1              # raw < 2^31 (positivo)
                mul  t1, t0, t0              # raw^2 < 2^62
                srli t1, t1, 31              # high31 (32b, >= 0)
                rem  a0, t1, s0
                j    .Lr_int_done
            .Lr_int_zero:                    # bound <= 0 -> 0 (leniente)
                mv   a0, zero
            .Lr_int_fail:                    # getrandom falhou -> 0 (sem fraco
                                            # — paridade x86/JVM/JS; antes
                                            # caia em done com a0=-errno!)
                mv   a0, zero
            .Lr_int_done:
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # kof_random_bool() -> a0 0/1
            .globl kof_random_bool
            kof_random_bool:
                addi sp, sp, -32
                sd   s0, 16(sp)
            .Lr_bool_get:
                addi a0, sp, 0
                li   a1, 1
                li   a2, 0
                li   a7, 278
                ecall
                bltz a0, .Lr_bool_fail
                lbu  a0, 0(sp)
                andi a0, a0, 1
                j    .Lr_bool_done
            .Lr_bool_fail:                   # falhou -> 0
                li   a0, 0
            .Lr_bool_done:
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret
            """;
}
