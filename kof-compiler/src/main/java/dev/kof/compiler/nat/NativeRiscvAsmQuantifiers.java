package dev.kof.compiler.nat;

// D-MULTIPARADIGMA-PHASE1A (slices 1a+1b) — eager short-circuit search ops
// any/all/none + find/count(pred) em riscv64, espelho do x86
// RuntimeListQuantifiers: loop com reload do elemento por iteração, invoke
// da lambda via slot, verdade = nonzero (igual ao filter), Bool/Int crus
// em a0, find ausente = 0 (contrato Map.get-missing). Só mnemônicos que o
// NativeAarch64Translator conhece (aarch64 deriva daqui). Concatenado em
// NativeRiscvAsm.
public final class NativeRiscvAsmQuantifiers {

    private NativeRiscvAsmQuantifiers() {}

    static String RISCV_QUANTIFIERS_ASM = """
            .text
            # kof_list_any(a0=list, a1=fn) -> a0 1/0
            .globl kof_list_any
            kof_list_any:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
            .Llany_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llany_false
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                bnez a0, .Llany_true
                addi s2, s2, 1
                j    .Llany_loop
            .Llany_true:
                li   a0, 1
                j    .Llany_done
            .Llany_false:
                li   a0, 0
            .Llany_done:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_all(a0=list, a1=fn) -> a0 1/0
            .globl kof_list_all
            kof_list_all:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
            .Llall_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llall_true
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                beqz a0, .Llall_false
                addi s2, s2, 1
                j    .Llall_loop
            .Llall_false:
                li   a0, 0
                j    .Llall_done
            .Llall_true:
                li   a0, 1
            .Llall_done:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_none(a0=list, a1=fn) -> a0 1/0
            .globl kof_list_none
            kof_list_none:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
            .Llnone_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llnone_true
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                bnez a0, .Llnone_false
                addi s2, s2, 1
                j    .Llnone_loop
            .Llnone_false:
                li   a0, 0
                j    .Llnone_done
            .Llnone_true:
                li   a0, 1
            .Llnone_done:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_find(a0=list, a1=fn, a2=tag) -> a0 boxed | 0.
            # Same box contract as x86 (NativeBoxTags numbering); tag in a2
            # is caller-saved, so it is parked in s3 on entry.
            .globl kof_list_find
            kof_list_find:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s3, a2
                li   s2, 0
            .Llfind_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llfind_miss
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                bnez a0, .Llfind_hit
                addi s2, s2, 1
                j    .Llfind_loop
            .Llfind_hit:
                li   t0, 1
                beq  s3, t0, .Llfind_pass
                li   t0, 6
                beq  s3, t0, .Llfind_pass
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a0, 0(t1)
                li   t0, 2
                beq  s3, t0, .Llfind_long
                li   t0, 3
                beq  s3, t0, .Llfind_bool
                li   t0, 4
                beq  s3, t0, .Llfind_double
                li   t0, 5
                beq  s3, t0, .Llfind_float
                call kof_box_int
                j    .Llfind_done
            .Llfind_long:
                call kof_box_long
                j    .Llfind_done
            .Llfind_bool:
                call kof_box_bool
                j    .Llfind_done
            .Llfind_double:
                call kof_box_double
                j    .Llfind_done
            .Llfind_float:
                call kof_box_float
                j    .Llfind_done
            .Llfind_pass:
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a0, 0(t1)
                j    .Llfind_done
            .Llfind_miss:
                li   a0, 0
            .Llfind_done:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_sorted(a0=list, a1=tag) -> a0 new List, insertion
            # order via kof_list_cmp (stable; the receiver is never mutated).
            # Slice 1g (D-MULTIPARADIGMA-SORTED). Lives here (not in
            # Lookups0) so that file stays under the 500 gate.
            .globl kof_list_sorted
            kof_list_sorted:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)          # src
                sd   s1, 40(sp)          # tag
                sd   s2, 32(sp)          # out
                sd   s3, 24(sp)          # i
                sd   s4, 16(sp)          # key
                sd   s5, 8(sp)           # n
                sd   s6, 0(sp)           # j
                mv   s0, a0
                mv   s1, a1
                lw   s5, 16(s0)
                call kof_list_new
                mv   s2, a0
                li   s3, 0
            .Llso_copy:
                bge  s3, s5, .Llso_isort
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s2
                call kof_list_add
                addi s3, s3, 1
                j    .Llso_copy
            .Llso_isort:
                li   s3, 1
            .Llso_outer:
                bge  s3, s5, .Llso_done
                mv   a0, s2
                mv   a1, s3
                call kof_list_get
                mv   s4, a0              # key
                addi s6, s3, -1          # j = i - 1
            .Llso_inner:
                bltz s6, .Llso_insert
                mv   a0, s2
                mv   a1, s6
                call kof_list_get        # a0 = b = get(j)
                mv   a1, a0              # arg1 = b
                mv   a0, s4              # arg0 = key
                mv   a2, s1
                call kof_list_cmp        # a0 = cmp(key, b)
                bgez a0, .Llso_insert    # key >= b: stop (stable)
                mv   a0, s2
                mv   a1, s6
                call kof_list_get        # a0 = v = get(j)
                mv   a2, a0
                mv   a0, s2
                addi a1, s6, 1
                call kof_list_set        # set(j+1, v)
                addi s6, s6, -1          # j--
                j    .Llso_inner
            .Llso_insert:
                mv   a0, s2
                addi a1, s6, 1
                mv   a2, s4
                call kof_list_set        # set(j+1, key)
                addi s3, s3, 1
                j    .Llso_outer
            .Llso_done:
                mv   a0, s2
                ld   s6, 0(sp)
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_list_sort_cmp(a0=list, a1=cmp) — in-place insertion sort
            # ordered by the comparator (negative/zero/positive Int); stable
            # for pure comparators; the receiver IS mutated (#685 enum sort).
            # aarch64 herda via translateRiscvToAarch64.
            .globl kof_list_sort_cmp
            kof_list_sort_cmp:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)          # list
                sd   s1, 40(sp)          # cmp lambda
                sd   s2, 32(sp)          # (unused)
                sd   s3, 24(sp)          # i
                sd   s4, 16(sp)          # key
                sd   s5, 8(sp)           # n
                sd   s6, 0(sp)           # j
                mv   s0, a0
                mv   s1, a1
                lw   s5, 16(s0)
                li   s3, 1
            .Llsoc_outer:
                bge  s3, s5, .Llsoc_done
                mv   a0, s0
                mv   a1, s3
                call kof_list_get
                mv   s4, a0              # key
                addi s6, s3, -1          # j = i - 1
            .Llsoc_inner:
                bltz s6, .Llsoc_insert
                mv   a0, s0
                mv   a1, s6
                call kof_list_get        # a0 = a = get(j)
                mv   a2, a0              # arg1 = a
                mv   a1, s4              # arg0 = key
                mv   a0, s1              # cmp lambda
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3                  # a0 = cmp(key, a)
                bgez a0, .Llsoc_insert   # a >= key: stop (stable)
                mv   a0, s0
                mv   a1, s6
                call kof_list_get        # a0 = v = get(j)
                mv   a2, a0
                mv   a0, s0
                addi a1, s6, 1
                call kof_list_set        # set(j+1, v)
                addi s6, s6, -1          # j--
                j    .Llsoc_inner
            .Llsoc_insert:
                mv   a0, s0
                addi a1, s6, 1
                mv   a2, s4
                call kof_list_set        # set(j+1, key)
                addi s3, s3, 1
                j    .Llsoc_outer
            .Llsoc_done:
                ld   s6, 0(sp)
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_list_sorted_cmp(a0=list, a1=cmp) -> a0 new List ordered by
            # the comparator (negative/zero/positive Int); insertion sort
            # (stable for pure comparators); the receiver is never mutated.
            # Lambda call mirrors x86 (a0=lambda, a1, a2 through the vtable
            # slot); raw slots like map/filter. Slice 1g.
            .globl kof_list_sorted_cmp
            kof_list_sorted_cmp:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)          # src
                sd   s1, 40(sp)          # cmp lambda
                sd   s2, 32(sp)          # out
                sd   s3, 24(sp)          # i
                sd   s4, 16(sp)          # key
                sd   s5, 8(sp)           # n
                sd   s6, 0(sp)           # j
                mv   s0, a0
                mv   s1, a1
                lw   s5, 16(s0)
                call kof_list_new
                mv   s2, a0
                li   s3, 0
            .Llsc_copy:
                bge  s3, s5, .Llsc_isort
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s2
                call kof_list_add
                addi s3, s3, 1
                j    .Llsc_copy
            .Llsc_isort:
                li   s3, 1
            .Llsc_outer:
                bge  s3, s5, .Llsc_done
                mv   a0, s2
                mv   a1, s3
                call kof_list_get
                mv   s4, a0              # key
                addi s6, s3, -1          # j = i - 1
            .Llsc_inner:
                bltz s6, .Llsc_insert
                mv   a0, s2
                mv   a1, s6
                call kof_list_get        # a0 = a = get(j)
                mv   a2, a0              # arg1 = a
                mv   a1, s4              # arg0 = key
                mv   a0, s1              # cmp lambda
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3                  # a0 = cmp(key, a)
                bgez a0, .Llsc_insert    # a >= key: stop (stable)
                mv   a0, s2
                mv   a1, s6
                call kof_list_get        # a0 = v = get(j)
                mv   a2, a0
                mv   a0, s2
                addi a1, s6, 1
                call kof_list_set        # set(j+1, v)
                addi s6, s6, -1          # j--
                j    .Llsc_inner
            .Llsc_insert:
                mv   a0, s2
                addi a1, s6, 1
                mv   a2, s4
                call kof_list_set        # set(j+1, key)
                addi s3, s3, 1
                j    .Llsc_outer
            .Llsc_done:
                mv   a0, s2
                ld   s6, 0(sp)
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_list_count_pred(a0=list, a1=fn) -> a0 count
            .globl kof_list_count_pred
            kof_list_count_pred:
                addi sp, sp, -56
                sd   ra, 48(sp)
                sd   s0, 40(sp)
                sd   s1, 32(sp)
                sd   s2, 24(sp)
                sd   s3, 16(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
                li   s3, 0
            .Llcount_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llcount_done
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                beqz a0, .Llcount_next
                addi s3, s3, 1
            .Llcount_next:
                addi s2, s2, 1
                j    .Llcount_loop
            .Llcount_done:
                mv   a0, s3
                ld   s3, 16(sp)
                ld   s2, 24(sp)
                ld   s1, 32(sp)
                ld   s0, 40(sp)
                ld   ra, 48(sp)
                addi sp, sp, 56
                ret

            # kof_list_foreach(a0=list, a1=fn) -> void (effect only)
            .globl kof_list_foreach
            kof_list_foreach:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
            .Lforeach_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Lforeach_done
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                addi s2, s2, 1
                j    .Lforeach_loop
            .Lforeach_done:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_flatmap(a0=list, a1=fn) -> a0 new List (concat).
            # Counter in s3 like kof_list_filter (callee-saved: calls keep it).
            .globl kof_list_flatmap
            kof_list_flatmap:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                call kof_list_new
                mv   s2, a0
                li   s3, 0
            .Llflat_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Llflat_done
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                mv   a1, a0
                mv   a0, s2
                call kof_list_add_all
                addi s3, s3, 1
                j    .Llflat_loop
            .Llflat_done:
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            # kof_list_distinct(a0=list, a1=tag) -> a0 new List.
            # Equality reuses kof_list_contains (same tag taxonomy:
            # 0=raw cmpq, 1=kof_string_equals, 2=kof_obj_equals).
            .globl kof_list_distinct
            kof_list_distinct:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                call kof_list_new
                mv   s2, a0
                li   s3, 0
            .Lldist_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Lldist_done
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s2
                mv   a2, s1
                call kof_list_contains
                bnez a0, .Lldist_next
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s2
                call kof_list_add
            .Lldist_next:
                addi s3, s3, 1
                j    .Lldist_loop
            .Lldist_done:
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            """;
}
