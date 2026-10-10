package dev.kof.compiler.nat;

// pagination P1 (D-PAGINATION, D-FUTURE-BATCH-2809B) — List.take/drop/slice
// em riscv64, espelho byte-a-byte de RuntimeListLookups (x86_64): clamping
// honesto (n>size devolve o todo/vazio; offset>size devolve vazio, sem erro);
// negativo = erro nomeado PAGINATION. Só mnemônicos que o
// NativeAarch64Translator conhece (aarch64 deriva daqui). Concatenado em
// NativeRiscvAsm.
public final class NativeRiscvAsmSlices {

    private NativeRiscvAsmSlices() {}

    static String RISCV_SLICES_ASM = """
            .text
            # pagination P1 — kof_list_take(a0=list, a1=n) -> nova List com
            # os primeiros min(n,size); n<0 -> erro nomeado PAGINATION.
            .globl kof_list_take
            kof_list_take:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                blt  a1, zero, .Ltk_bad
                lw   t0, 16(s0)
                blt  a1, t0, .Ltk_end
                mv   a1, t0
            .Ltk_end:
                mv   a0, s0
                mv   a2, a1
                li   a1, 0
                call kof_list_sub_list
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Ltk_bad:
                la   a0, .Lpag_count_msg
                call kof_throw_string

            # pagination P1 — kof_list_drop(a0=list, a1=n) -> nova List a
            # partir de min(n,size); n<0 -> PAGINATION.
            .globl kof_list_drop
            kof_list_drop:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                blt  a1, zero, .Ldp_bad
                lw   a2, 16(s0)
                blt  a1, a2, .Ldp_ok
                mv   a1, a2
            .Ldp_ok:
                mv   a0, s0
                call kof_list_sub_list
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Ldp_bad:
                la   a0, .Lpag_count_msg
                call kof_throw_string

            # pagination P1 — kof_list_slice(a0=list, a1=offset, a2=limit) ->
            # [start, start+min(limit,size-start)); negativos -> PAGINATION;
            # offset>size -> vazia (sem erro).
            .globl kof_list_slice
            kof_list_slice:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                blt  a1, zero, .Lsc_bad
                blt  a2, zero, .Lsc_bad
                mv   s3, a2
                lw   s1, 16(s0)
                mv   s2, a1
                bge  s2, s1, .Lsc_clamp
                j    .Lsc_ok
            .Lsc_clamp:
                mv   s2, s1
            .Lsc_ok:
                sub  t0, s1, s2
                mv   t1, s3
                ble  t1, t0, .Lsc_sum
                mv   t1, t0
            .Lsc_sum:
                add  a2, s2, t1
                mv   a0, s0
                mv   a1, s2
                call kof_list_sub_list
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .Lsc_bad:
                la   a0, .Lpag_ol_msg
                call kof_throw_string

            # ---------------------- literais PAGINATION --------------------
            .align 4
            .Lpag_count_msg:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 27
                .int 0
                .ascii "PAGINATION: count must be >= 0"
                .byte 0
            .align 4
            .Lpag_ol_msg:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 34
                .int 0
                .ascii "PAGINATION: limit/offset must be >= 0"
                .byte 0
            .text
            """;
}
