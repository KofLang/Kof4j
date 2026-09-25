package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 9, lane parity, 26/09): fatia 3a do kof.config no
// cross (riscv64 + aarch64) — leitura de environ. Porta RuntimeConfig1 (x86)
// sem depender de getenv: openat/read/close em /proc/self/environ.
// kof_env_getc(a0=nome C-string) -> a0=KofString*|0; .Lcfg1_envname monta
// "KOF_<KEY>" (maiúsculas, '.'/'-' -> '_'). KofStr: len@16, bytes@24.
public final class NativeRiscvAsmConfig1 {

    private NativeRiscvAsmConfig1() {}

    static String RISCV_RUNTIME_ASM_CONFIG_1 = """
            .section .rodata
            .Lcfg1_environ: .asciz "/proc/self/environ"
            .section .text

            # kof_env_getc(a0=nome C-string) -> a0=KofString*|0
            .globl kof_env_getc
            .type kof_env_getc, @function
            kof_env_getc:
                li   t0, 16448
                sub  sp, sp, t0
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                sd   s5, 48(sp)
                mv   s0, a0
                li   s1, 0
            .Lcfg1_nlen:
                add  t1, s0, s1
                lbu  t2, 0(t1)
                beqz t2, .Lcfg1_nlen_done
                addi s1, s1, 1
                j    .Lcfg1_nlen
            .Lcfg1_nlen_done:
                li   a0, -100
                la   a1, .Lcfg1_environ
                li   a2, 0
                li   a3, 0
                li   a7, 56
                ecall
                bltz a0, .Lcfg1_fail
                mv   s2, a0
                mv   a0, s2
                addi a1, sp, 64
                li   a2, 16384
                li   a7, 63
                ecall
                mv   s3, a0
                mv   a0, s2
                li   a7, 57
                ecall
                blez s1, .Lcfg1_fail
                bge  s1, s3, .Lcfg1_fail
                li   s4, 0
            .Lcfg1_scan:
                bge  s4, s3, .Lcfg1_fail
                add  a2, sp, s4
                addi a2, a2, 64
                li   s5, 0
            .Lcfg1_pcmp:
                bge  s5, s1, .Lcfg1_pmatched
                add  t0, s4, s5
                bge  t0, s3, .Lcfg1_fail
                add  t1, s0, s5
                lbu  t2, 0(t1)
                add  t3, a2, s5
                lbu  t4, 0(t3)
                bne  t2, t4, .Lcfg1_advance
                addi s5, s5, 1
                j    .Lcfg1_pcmp
            .Lcfg1_pmatched:
                add  t0, a2, s1
                lbu  t1, 0(t0)
                li   t2, 61
                bne  t1, t2, .Lcfg1_advance
                add  a0, a2, s1
                addi a0, a0, 1
                add  t0, s4, s1
                addi t0, t0, 1
                sub  t1, s3, t0
                li   t2, 0
            .Lcfg1_vscan:
                bge  t2, t1, .Lcfg1_vdone
                add  t3, a0, t2
                lbu  t4, 0(t3)
                beqz t4, .Lcfg1_vdone
                addi t2, t2, 1
                j    .Lcfg1_vscan
            .Lcfg1_vdone:
                mv   a1, t2
                call kof_string_from_literal
                j    .Lcfg1_exit
            .Lcfg1_advance:
                bge  s4, s3, .Lcfg1_fail
                add  t0, sp, s4
                addi t0, t0, 64
                lbu  t1, 0(t0)
                beqz t1, .Lcfg1_adv_null
                addi s4, s4, 1
                j    .Lcfg1_advance
            .Lcfg1_adv_null:
                addi s4, s4, 1
                j    .Lcfg1_scan
            .Lcfg1_fail:
                li   a0, 0
            .Lcfg1_exit:
                ld   s5, 48(sp)
                ld   s4, 40(sp)
                ld   s3, 32(sp)
                ld   s2, 24(sp)
                ld   s1, 16(sp)
                ld   s0, 8(sp)
                ld   ra, 0(sp)
                li   t0, 16448
                add  sp, sp, t0
                ret

            # .Lcfg1_envname(a0=key KofString*, a1=dest) -> "KOF_<KEY>" C-string
            .Lcfg1_envname:
                lw   t0, 16(a0)
                addi t1, a0, 24
                li   t2, 75
                sb   t2, 0(a1)
                li   t2, 79
                sb   t2, 1(a1)
                li   t2, 70
                sb   t2, 2(a1)
                li   t2, 95
                sb   t2, 3(a1)
                li   t2, 4
                li   t3, 0
            .Lcfg1_en_loop:
                bge  t3, t0, .Lcfg1_en_done
                add  t4, t1, t3
                lbu  t5, 0(t4)
                li   t6, 46
                beq  t5, t6, .Lcfg1_en_us
                li   t6, 45
                beq  t5, t6, .Lcfg1_en_us
                li   t6, 97
                blt  t5, t6, .Lcfg1_en_store
                li   t6, 122
                bgt  t5, t6, .Lcfg1_en_store
                addi t5, t5, -32
                j    .Lcfg1_en_store
            .Lcfg1_en_us:
                li   t5, 95
            .Lcfg1_en_store:
                add  t6, a1, t2
                sb   t5, 0(t6)
                addi t2, t2, 1
                addi t3, t3, 1
                j    .Lcfg1_en_loop
            .Lcfg1_en_done:
                add  t6, a1, t2
                sb   zero, 0(t6)
                ret
            """;
}
