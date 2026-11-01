package dev.kof.compiler.nat;

// FASE 3 (REFACTOR-500): fatia 0 de RISCV_MAPSET_ASM — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
public final class NativeRiscvAsmMapset0 {

    private NativeRiscvAsmMapset0() {}

    static  String RISCV_MAPSET_ASM_0 = """
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
                li   t0, 1
                sw   t0, 40(s0)          # §123: key tag — 1=String default
                mv   a0, s0
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_map_find(map, key) -> idx | -1  (interno)
            # §123: tag da chave no header (off 40): 1=String (kof_string_equals),
            # 0=raw (chave Int virava PONTEIRO no equals de String → SIGSEGV).
            .globl kof_map_find
            kof_map_find:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # map
                sd   s1, 24(sp)          # key
                sd   s2, 16(sp)          # i
                sd   s3, 8(sp)           # tag
                mv   s0, a0
                mv   s1, a1
                lw   s3, 40(s0)          # tag
                li   s2, 0
            .Lkmf_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Lkmf_miss
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a0, 0(t1)           # candidato
                li   t3, 1
                beq  s3, t3, .Lkmf_str
                li   t3, 2
                beq  s3, t3, .Lkmf_obj
            .Lkmf_raw:
                bne  a0, s1, .Lkmf_next
                j    .Lkmf_hit
            .Lkmf_str:
                beqz a0, .Lkmf_next
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Lkmf_hit
                j    .Lkmf_next
            .Lkmf_obj:                      # §104b-ii: tag 2 = objeto Kof
                beqz a0, .Lkmf_next
                mv   a1, s1
                call kof_obj_equals
                bnez a0, .Lkmf_hit
                j    .Lkmf_next
            .Lkmf_next:
                addi s2, s2, 1
                j    .Lkmf_loop
            .Lkmf_hit:
                mv   a0, s2
                j    .Lkmf_ret
            .Lkmf_miss:
                li   a0, -1
            .Lkmf_ret:
                ld   s3, 8(sp)
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
                lw   t1, 20(s0)          # cap (old)
                blt  t0, t1, .Lkmp_space
                # #933: cresce 2x — os DOIS arrays novos em newCap*8 bytes
                # (o original alocava oldCap*8: os índices além da cap antiga
                # escreviam OOB no heap .bss — corrupção silenciosa). A cópia
                # fica em oldCap*8 exata (sem overread do bloco antigo). O
                # cap *= 2 (SLOTS) vem primeiro para o t3 derivar o newCap.
                slli t2, t0, 3           # oldCap*8 (cópia; size==cap aqui)
                slli t1, t1, 1
                sw   t1, 20(s0)          # cap *= 2 (slots)
                slli t3, t1, 3           # newCap*8 (alloc)
                addi a0, s0, 24
                ld   a0, 0(a0)           # keys
                mv   a1, t3
                mv   a2, t2
                call kof_copy_alloc
                sd   a0, 24(s0)
                addi a0, s0, 32
                ld   a0, 0(a0)           # vals (t2/t3 sobrevivem ao helper)
                mv   a1, t3
                mv   a2, t2
                call kof_copy_alloc
                sd   a0, 32(s0)
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

            # kof_copy_alloc(src, alloc_bytes, copy_bytes) -> novo bloco com os
            # primeiros copy_bytes de src (#933: alloc e cópia separados).
            # Frame 64 = sp 16-alinhado (o ABI LP64D exige sp≡0 mod 16 no call).
            .globl kof_copy_alloc
            kof_copy_alloc:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)          # src
                sd   s1, 40(sp)          # alloc
                sd   s2, 32(sp)          # copy
                sd   s3, 24(sp)          # dst
                sd   s4, 16(sp)          # i
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                mv   a0, s1
                call kof_alloc
                mv   s3, a0
                li   s4, 0
            .Lkca_loop:
                bge  s4, s2, .Lkca_done
                add  t0, s0, s4
                lbu  t1, 0(t0)
                add  t2, s3, s4
                sb   t1, 0(t2)
                addi s4, s4, 1
                j    .Lkca_loop
            .Lkca_done:
                mv   a0, s3
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_map_get_or_default(map, key, def) -> val | def
            .globl kof_map_get_or_default
            kof_map_get_or_default:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)
                sd   s1, 16(sp)
                sd   s2, 8(sp)
                mv   s0, a0
                mv   s1, a2                 # def
                call kof_map_find
                li   t0, -1
                beq  a0, t0, .LKMGD_miss
                ld   t1, 32(s0)
                slli t2, a0, 3
                add  t1, t1, t2
                ld   a0, 0(t1)
                j    .LKMGD_ret
            .LKMGD_miss:
                mv   a0, s1
            .LKMGD_ret:
                ld   s2, 8(sp)
                ld   s1, 16(sp)
                ld   s0, 24(sp)
                ld   ra, 32(sp)
                addi sp, sp, 40
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

            # kof_map_clear(map) — D-MEMORY-CLEAR (O-03): anula keys e vals
            # ANTES de encolher (o map nao retem mais nenhuma referencia)
            .globl kof_map_clear
            kof_map_clear:
                lw   t0, 16(a0)          # size
                ld   t1, 24(a0)          # keys
                ld   t2, 32(a0)          # vals
                li   t3, 0               # i
            .LKMC_loop:
                bge  t3, t0, .LKMC_done
                slli t4, t3, 3
                add  t5, t1, t4
                sd   zero, 0(t5)
                add  t5, t2, t4
                sd   zero, 0(t5)
                addi t3, t3, 1
                j    .LKMC_loop
            .LKMC_done:
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
                # §284-map (18/09): slot de valor pode ser a CAIXA Int/Long
                # (contrato do Map no nativo, igual HashMap<Integer> no JVM).
                # A visao List<V> nativa e de qwords CRUS — abre a caixa na
                # fonte. Nao-box/null passa cru. Constante local: esta fatia
                # pode ser poda-da sem B49 (o valor e o MAGIC de
                # RuntimeErasureBox — manter em sincronia).
                beqz a1, .LKMV_add
                la   t3, .Lkmsv_magic
                ld   t3, 0(t3)
                ld   t4, 0(a1)
                bne  t3, t4, .LKMV_add
                ld   a1, 16(a1)
            .LKMV_add:
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

            """;
}