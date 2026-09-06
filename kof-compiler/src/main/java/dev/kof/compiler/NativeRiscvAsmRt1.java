package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 1 de RISCV_RUNTIME_ASM — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmRt1 {

    private NativeRiscvAsmRt1() {}

    static final String RISCV_RUNTIME_ASM_1 = """
            .globl kof_string_concat
            kof_string_concat:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
                li   s3, 0
                beqz s0, .Lcc_a_null
                lw   s2, 16(s0)
                j    .Lcc_b
            .Lcc_a_null:
                addi s2, s2, 4
                li   s3, 1
            .Lcc_b:
                beqz s1, .Lcc_bnull
                lw   t0, 16(s1)
                add  s2, s2, t0
                j    .Lcc_alloc
            .Lcc_bnull:
                addi s2, s2, 4
                ori  s3, s3, 2
            .Lcc_alloc:
                addi a0, s2, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                li   t0, 0
                sw   t0, 4(s4)
                sd   t0, 8(s4)
                sw   s2, 16(s4)
                sw   t0, 20(s4)
                addi a0, s4, 24
                andi t1, s3, 1
                bnez t1, .Lcc_copy_a_null
                addi a1, s0, 24
                lw   a2, 16(s0)
                call kof_memcpy
                lw   s5, 16(s0)
                j    .Lcc_after_a
            .Lcc_copy_a_null:
                la   a1, .Lstr_null
                li   a2, 4
                call kof_memcpy
                li   s5, 4
            .Lcc_after_a:
                addi a0, s4, 24
                add  a0, a0, s5
                andi t1, s3, 2
                bnez t1, .Lcc_copy_b_null
                beqz s1, .Lcc_done
                addi a1, s1, 24
                lw   a2, 16(s1)
                call kof_memcpy
                j    .Lcc_done
            .Lcc_copy_b_null:
                la   a1, .Lstr_null
                li   a2, 4
                call kof_memcpy
            .Lcc_done:
                li   t0, 0
                addi t1, s4, 24
                add  t1, t1, s2
                sb   t0, 0(t1)
                mv   a0, s4
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                ld   s6, 0(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_string_equals(a, b) -> 0/1 (null-safe)
            .globl kof_string_equals
            kof_string_equals:
                beqz a0, .Lse_a0
                beqz a1, .Lse_no
                j    .Lse_body
            .Lse_a0:
                beqz a1, .Lse_yes
                j    .Lse_no
            .Lse_body:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   t0, 16(s0)
                lw   t1, 16(s1)
                bne  t0, t1, .Lse_no_pop
                li   t2, 0
            .Lse_loop:
                bge  t2, t0, .Lse_yes_pop
                addi t3, s0, 24
                add  t3, t3, t2
                lbu  t3, 0(t3)
                addi t4, s1, 24
                add  t4, t4, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Lse_no_pop
                addi t2, t2, 1
                j    .Lse_loop
            .Lse_yes_pop:
                li   a0, 1
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lse_no_pop:
                li   a0, 0
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lse_yes:
                li   a0, 1
                ret
            .Lse_no:
                li   a0, 0
                ret

            # kof_string_char_at(str, idx) -> Int (char code)
            .globl kof_string_char_at
            kof_string_char_at:
                lw   t0, 16(a0)
                bge  a1, t0, .Lca_bounds
                blt  a1, zero, .Lca_bounds
                addi t1, a0, 24
                add  t1, t1, a1
                lbu  a0, 0(t1)
                ret
            .Lca_bounds:
                call kof_bounds_error

            # kof_string_substring(str, start, end) -> KofStr* (end=0 → até o fim)
            .globl kof_string_substring
            kof_string_substring:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                lw   t0, 16(s0)
                beqz s2, .Lss_endlen
                j    .Lss_chk
            .Lss_endlen:
                mv   s2, t0
            .Lss_chk:
                bgt  s2, t0, .Lss_bounds
                blt  s1, zero, .Lss_bounds
                bgt  s1, s2, .Lss_bounds
                sub  s3, s2, s1
                addi a0, s3, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                li   t0, 0
                sw   t0, 4(s4)
                sd   t0, 8(s4)
                sw   s3, 16(s4)
                sw   t0, 20(s4)
                addi a0, s4, 24
                addi a1, s0, 24
                add  a1, a1, s1
                mv   a2, s3
                call kof_memcpy
                li   t0, 0
                addi t1, s4, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                mv   a0, s4
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lss_bounds:
                call kof_bounds_error

            # kof_string_contains(a, b) -> 0/1
            .globl kof_string_contains
            kof_string_contains:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s1)
                beqz s2, .Lcn_found
                lw   s3, 16(s0)
                bgt  s2, s3, .Lcn_no
                li   t0, 0
            .Lcn_outer:
                bge  t0, s3, .Lcn_no
                li   t1, 0
            .Lcn_inner:
                bge  t1, s2, .Lcn_found
                addi t2, s0, 24
                add  t2, t2, t0
                add  t2, t2, t1
                lbu  t2, 0(t2)
                addi t3, s1, 24
                add  t3, t3, t1
                lbu  t3, 0(t3)
                bne  t2, t3, .Lcn_next
                addi t1, t1, 1
                j    .Lcn_inner
            .Lcn_next:
                addi t0, t0, 1
                j    .Lcn_outer
            .Lcn_found:
                li   a0, 1
                j    .Lcn_ret
            .Lcn_no:
                li   a0, 0
            .Lcn_ret:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_starts_with(a, b) -> 0/1
            .globl kof_string_starts_with
            kof_string_starts_with:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s1)
                lw   t0, 16(s0)
                bgt  s2, t0, .Lsw_no
                li   t1, 0
            .Lsw_loop:
                bge  t1, s2, .Lsw_yes
                addi t2, s0, 24
                add  t2, t2, t1
                lbu  t2, 0(t2)
                addi t3, s1, 24
                add  t3, t3, t1
                lbu  t3, 0(t3)
                bne  t2, t3, .Lsw_no
                addi t1, t1, 1
                j    .Lsw_loop
            .Lsw_yes:
                li   a0, 1
                j    .Lsw_ret
            .Lsw_no:
                li   a0, 0
            .Lsw_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_string_ends_with(a, b) -> 0/1
            .globl kof_string_ends_with
            kof_string_ends_with:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s1)
                lw   s3, 16(s0)
                bgt  s2, s3, .Lew_no
                sub  t0, s3, s2
            .Lew_loop:
                bge  t0, s3, .Lew_yes
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                addi t2, s1, 24
                add  t2, t2, t0
                addi t3, s2, 0
                sub  t3, t3, s3
                add  t2, t2, t3
                lbu  t2, 0(t2)
                bne  t1, t2, .Lew_no
                addi t0, t0, 1
                j    .Lew_loop
            .Lew_yes:
                li   a0, 1
                j    .Lew_ret
            .Lew_no:
                li   a0, 0
            .Lew_ret:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_index_of(a, b) -> Int
            .globl kof_string_index_of
            kof_string_index_of:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   s3, 16(s1)
                beqz s3, .Liof_found0
                bgt  s3, s2, .Liof_no
                li   t0, 0
            .Liof_outer:
                mv   t1, s2
                sub  t1, t1, s3
                bgt  t0, t1, .Liof_no
                li   t2, 0
            .Liof_inner:
                bge  t2, s3, .Liof_found
                addi t3, s0, 24
                add  t4, t0, t2
                add  t3, t3, t4
                lbu  t3, 0(t3)
                addi t4, s1, 24
                add  t4, t4, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Liof_next
                addi t2, t2, 1
                j    .Liof_inner
            .Liof_next:
                addi t0, t0, 1
                j    .Liof_outer
            .Liof_found0:
                li   t0, 0
            .Liof_found:
                mv   a0, t0
                j    .Liof_ret
            .Liof_no:
                li   a0, -1
            .Liof_ret:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            """;
}
