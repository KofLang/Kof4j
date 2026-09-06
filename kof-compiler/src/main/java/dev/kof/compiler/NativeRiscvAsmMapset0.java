package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 0 de RISCV_MAPSET_ASM — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmMapset0 {

    private NativeRiscvAsmMapset0() {}

    static final String RISCV_MAPSET_ASM_0 = """
            .section .text
            # kof_map_new() -> Map*
            .globl kof_map_new
            kof_map_new:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                li   a0, 64
                call kof_alloc
                mv   s0, a0
                li   t0, 100
                sw   t0, 0(s0)
                li   t0, 0
                sw   t0, 4(s0)
                sd   t0, 8(s0)
                sw   t0, 16(s0)          # size=0
                li   t0, 16
                sw   t0, 20(s0)          # cap=16
                li   a0, 128
                call kof_alloc
                sd   a0, 24(s0)          # keys
                li   a0, 128
                call kof_alloc
                sd   a0, 32(s0)          # vals
                mv   a0, s0
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_map_find(map, key) -> idx | -1  (interno)
            .globl kof_map_find
            kof_map_find:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # map
                sd   s1, 24(sp)          # key
                sd   s2, 16(sp)          # i
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
            .Lkmf_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Lkmf_miss
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a0, 0(t1)           # candidato
                beqz a0, .Lkmf_next
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Lkmf_hit
            .Lkmf_next:
                addi s2, s2, 1
                j    .Lkmf_loop
            .Lkmf_hit:
                mv   a0, s2
                j    .Lkmf_ret
            .Lkmf_miss:
                li   a0, -1
            .Lkmf_ret:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_map_put(map, key, val) -> anterior | 0
            .globl kof_map_put
            kof_map_put:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # map
                sd   s1, 24(sp)          # key
                sd   s2, 16(sp)          # val
                sd   s3, 8(sp)           # idx
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                mv   a0, s0
                mv   a1, s1
                call kof_map_find
                mv   s3, a0
                li   t0, -1
                beq  s3, t0, .Lkmp_insert
                ld   t1, 32(s0)          # vals
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a0, 0(t1)           # anterior
                sd   s2, 0(t1)
                j    .Lkmp_ret
            .Lkmp_insert:
                lw   t0, 16(s0)          # size
                lw   t1, 20(s0)          # cap
                blt  t0, t1, .Lkmp_space
                # cresce 2x: copia oldCap*8 bytes p/ novo bloco
                slli t2, t0, 3           # oldCap*8
                mv   a0, s0
                addi a0, a0, 24
                ld   a0, 0(a0)           # keys
                mv   a1, t2
                call kof_copy_alloc
                sd   a0, 24(s0)
                addi a0, s0, 32
                ld   a0, 0(a0)           # vals
                mv   a1, t2
                call kof_copy_alloc
                sd   a0, 32(s0)
                slli t1, t1, 1
                sw   t1, 20(s0)          # cap *= 2
            .Lkmp_space:
                lw   t0, 16(s0)
                ld   t1, 24(s0)
                slli t2, t0, 3
                add  t1, t1, t2
                sd   s1, 0(t1)           # keys[size]=key
                ld   t1, 32(s0)
                add  t1, t1, t2
                sd   s2, 0(t1)           # vals[size]=val
                addi t0, t0, 1
                sw   t0, 16(s0)          # size++
                li   a0, 0
            .Lkmp_ret:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_copy_alloc(src, nbytes) -> novo bloco copiado (interno)
            .globl kof_copy_alloc
            kof_copy_alloc:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # src
                sd   s1, 24(sp)          # n
                sd   s2, 16(sp)          # dst
                sd   s3, 8(sp)           # i
                mv   s0, a0
                mv   s1, a1
                mv   a0, s1
                call kof_alloc
                mv   s2, a0
                li   s3, 0
            .Lkca_loop:
                bge  s3, s1, .Lkca_done
                add  t0, s0, s3
                lbu  t1, 0(t0)
                add  t2, s2, s3
                sb   t1, 0(t2)
                addi s3, s3, 1
                j    .Lkca_loop
            .Lkca_done:
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_map_get(map, key) -> val | 0
            .globl kof_map_get
            kof_map_get:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                call kof_map_find
                li   t0, -1
                beq  a0, t0, .LKMG_miss
                ld   t1, 32(s0)
                slli t2, a0, 3
                add  t1, t1, t2
                ld   a0, 0(t1)
                j    .LKMG_ret
            .LKMG_miss:
                li   a0, 0
            .LKMG_ret:
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_map_remove(map, key) -> removido | 0
            .globl kof_map_remove
            kof_map_remove:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # map
                sd   s1, 24(sp)          # idx
                sd   s2, 16(sp)          # removed
                mv   s0, a0
                call kof_map_find
                li   t0, -1
                beq  a0, t0, .LKMR_miss
                mv   s1, a0
                ld   t1, 32(s0)
                slli t2, s1, 3
                add  t1, t1, t2
                ld   s2, 0(t1)           # valor removido
                lw   t3, 16(s0)          # size
                addi t3, t3, -1          # count-1
            .LKMR_shift:
                bge  s1, t3, .LKMR_last
                ld   t1, 24(s0)
                slli t2, s1, 3
                add  t1, t1, t2
                ld   t4, 8(t1)
                sd   t4, 0(t1)           # keys[i]=keys[i+1]
                ld   t1, 32(s0)
                add  t1, t1, t2
                ld   t4, 8(t1)
                sd   t4, 0(t1)           # vals[i]=vals[i+1]
                addi s1, s1, 1
                j    .LKMR_shift
            .LKMR_last:
                sw   t3, 16(s0)          # size--
                mv   a0, s2
                j    .LKMR_ret
            .LKMR_miss:
                li   a0, 0
            .LKMR_ret:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_map_contains(map, key) -> 1/0
            .globl kof_map_contains
            kof_map_contains:
                addi sp, sp, -32
                sd   ra, 24(sp)
                call kof_map_find
                li   t0, -1
                beq  a0, t0, .LKMC_no
                li   a0, 1
                j    .LKMC_ret
            .LKMC_no:
                li   a0, 0
            .LKMC_ret:
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_map_size(map) -> Int
            .globl kof_map_size
            kof_map_size:
                lw   a0, 16(a0)
                ret

            # kof_map_is_empty(map) -> Bool
            .globl kof_map_is_empty
            kof_map_is_empty:
                lw   a0, 16(a0)
                seqz a0, a0
                ret

            # kof_map_clear(map)
            .globl kof_map_clear
            kof_map_clear:
                sw   zero, 16(a0)
                ret

            # kof_map_keys(map) -> List
            .globl kof_map_keys
            kof_map_keys:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # map
                sd   s1, 24(sp)          # result list
                sd   s2, 16(sp)          # keys array
                sd   s3, 8(sp)           # i
                mv   s0, a0
                call kof_list_new
                mv   s1, a0
                li   s3, 0
            .LKMK_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .LKMK_done
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                call kof_list_add
                addi s3, s3, 1
                j    .LKMK_loop
            .LKMK_done:
                mv   a0, s1
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_map_values(map) -> List
            .globl kof_map_values
            kof_map_values:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                call kof_list_new
                mv   s1, a0
                li   s3, 0
            .LKMV_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .LKMV_done
                ld   t1, 32(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                call kof_list_add
                addi s3, s3, 1
                j    .LKMV_loop
            .LKMV_done:
                mv   a0, s1
                ld   s3, 8(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # ---- Set (lista + tag: 1=string→equals, 0→pointer) ----
            # kof_set_new() -> Set* (mesmo layout de List)
            .globl kof_set_new
            kof_set_new:
                j    kof_list_new

            # kof_set_contains(set, elem, tag) -> 1/0
            .globl kof_set_contains
            kof_set_contains:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # set
                sd   s1, 24(sp)          # elem
                sd   s2, 16(sp)          # tag
                sd   s3, 8(sp)           # i
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                li   s3, 0
            .Lksc_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Lksc_no
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   t3, 0(t1)           # candidato
                li   t4, 1
                beq  s2, t4, .Lksc_str
                beq  t3, s1, .Lksc_yes   # pointer
                j    .Lksc_next
            .Lksc_str:
                beqz t3, .Lksc_next
                mv   a0, t3
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Lksc_yes
            .Lksc_next:
                addi s3, s3, 1
                j    .Lksc_loop
            .Lksc_yes:
                li   a0, 1
                j    .Lksc_ret
            .Lksc_no:
                li   a0, 0
            .Lksc_ret:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_set_add(set, elem, tag) -> 1 inseriu | 0 existia
            .globl kof_set_add
            kof_set_add:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                call kof_set_contains
                bnez a0, .Lksa_dup
                mv   a0, s0
                mv   a1, s1
                call kof_list_add
                li   a0, 1
                j    .Lksa_ret
            .Lksa_dup:
                li   a0, 0
            .Lksa_ret:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_set_remove(set, elem, tag) -> 1/0
            .globl kof_set_remove
            kof_set_remove:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                li   s3, 0
            .Lksr_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Lksr_no
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   t3, 0(t1)
                li   t4, 1
                beq  s2, t4, .Lksr_str
                beq  t3, s1, .Lksr_found
                j    .Lksr_next
            .Lksr_str:
                beqz t3, .Lksr_next
                mv   a0, t3
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Lksr_found
            .Lksr_next:
                addi s3, s3, 1
                j    .Lksr_loop
            .Lksr_found:
                mv   a0, s0
                mv   a1, s3
                call kof_list_remove
                li   a0, 1
                j    .Lksr_ret
            .Lksr_no:
                li   a0, 0
            .Lksr_ret:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_set_size / is_empty / clear → delegam p/ list
            .globl kof_set_size
            kof_set_size:
                j    kof_list_size
            .globl kof_set_is_empty
            kof_set_is_empty:
                j    kof_list_is_empty
            .globl kof_set_clear
            kof_set_clear:
                j    kof_list_clear

            # kof_json_decode_long = alias de int (paridade x86_64: jmp)
            """;
}
