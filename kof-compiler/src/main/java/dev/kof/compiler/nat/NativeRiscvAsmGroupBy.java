package dev.kof.compiler.nat;

// D-MULTIPARADIGMA-PHASE1A slice 1h — groupBy em riscv64: mapa de grupos por
// ordem de encontro (espelho do x86 kof_list_groupby em
// RuntimeListQuantifiers). Saiu daqui de NativeRiscvAsmQuantifiers pelo gate
// 500 (a classe cruzou 600 com a fatia). Só mnemônicos que o
// NativeAarch64Translator conhece (aarch64 deriva daqui). Concatenado em
// NativeRiscvAsm (ordem irrelevante p/ .globl).
public final class NativeRiscvAsmGroupBy {

    private NativeRiscvAsmGroupBy() {}

    static String RISCV_GROUPBY_ASM = """
            .text
            # kof_list_groupby(a0=list, a1=fn, a2=tag) -> a0 new Map
            # (groups in encounter order). Buckets are fresh lists; the tag
            # goes to the map header (slot 40) like the cross emitter does
            # for kof_map_* calls (tag <0 keeps the historic default).
            # Lambda call mirrors find (raw slots). Lifetimes: new/find/put
            # use s0-s3 + t/a only (verified); s4-s6 therefore survive every
            # call; s0-s3 are saved. Slice 1h.
            .globl kof_list_groupby
            kof_list_groupby:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)          # src
                sd   s1, 56(sp)          # lambda
                sd   s2, 48(sp)          # map
                sd   s3, 40(sp)          # i
                sd   s4, 32(sp)          # key
                sd   s5, 24(sp)          # tag, then bucket temp
                sd   s6, 16(sp)          # (saved; unused)
                mv   s0, a0
                mv   s1, a1
                mv   s5, a2
                call kof_map_new
                mv   s2, a0              # map
                li   t0, 0
                blt  s5, t0, .Llgb_loop_init
                sw   s5, 40(s2)          # key tag
            .Llgb_loop_init:
                li   s3, 0
            .Llgb_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Llgb_done
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                mv   s4, a0
                mv   a0, s2
                mv   a1, s4
                call kof_map_find
                li   t0, -1
                beq  a0, t0, .Llgb_miss
                ld   t1, 32(s2)
                slli t2, a0, 3
                add  t1, t1, t2
                ld   t1, 0(t1)           # existing bucket list
                j    .Llgb_add
            .Llgb_miss:
                call kof_list_new
                mv   a2, a0
                mv   a0, s2
                mv   a1, s4
                call kof_map_put
                mv   a0, s2
                mv   a1, s4
                call kof_map_find
                ld   t1, 32(s2)
                slli t2, a0, 3
                add  t1, t1, t2
                ld   t1, 0(t1)           # bucket list
            .Llgb_add:
                mv   a0, t1              # bucket
                ld   t2, 24(s0)
                slli t3, s3, 3
                add  t2, t2, t3
                ld   a1, 0(t2)
                call kof_list_add
                addi s3, s3, 1
                j    .Llgb_loop
            .Llgb_done:
                mv   a0, s2
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
