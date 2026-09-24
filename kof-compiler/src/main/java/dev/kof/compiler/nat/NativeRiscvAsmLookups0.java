package dev.kof.compiler.nat;

// #386/#382 (fatia 2) — lookups de map + família de list (indexOf/
// lastIndexOf/addAll/subList/sort/cmp) em riscv64, espelho byte-a-byte de
// RuntimeMapLookups/RuntimeListLookups do x86_64. Só mnemônicos que o
// NativeAarch64Translator conhece (aarch64 deriva daqui): ld/sd/lw/sw,
// li/mv/add/addi/slli/srai, beq/bne/blt/bge/bgt/bltu/bgeu, slt, xor, neg,
// fmv.d.x/flt.d/feq.d. O Double do sort usa flt.d/feq.d com a semântica do
// Double.compare medida no oráculo JDK (NaN maior, NaN==NaN, -0.0<0.0).
// §352/NAT001 fechado 21/09: o Float alarga os 32 bits do slot p/ Double
// (fmv.w.x + fcvt.d.s, ambos tradutíveis) e cai no MESMO compare. Concatenado
// em NativeRiscvAsm.
public final class NativeRiscvAsmLookups0 {

    private NativeRiscvAsmLookups0() {}

    static String RISCV_LOOKUPS_ASM_0 = """
            .section .rodata
            .p2align 3
            .Lkvlk_magic: .8byte 0x4B4F46425F425801   # MAGIC §284 (RuntimeErasureBox)
            .section .text
            # kof_value_kind(a0=val) -> a0: 0=raw/ponteiro, 1=String, 2=caixa
            # §352 NAT002: guarda de arena (_kof_heap.._kof_heap_end, o bump
            # do G-0) ANTES de derefar — bits de primitivo cru viram 0 sem
            # leitura, o SIGSEGV clássico do slot de Double nunca ocorre.
            .globl kof_value_kind
            kof_value_kind:
                beqz a0, .Lkvk_zero
                la   t0, _kof_heap
                bltu a0, t0, .Lkvk_zero
                la   t0, _kof_heap_end
                bgeu a0, t0, .Lkvk_zero
                la   t1, .Lkvlk_magic
                ld   t1, 0(t1)
                ld   t0, 0(a0)
                beq  t0, t1, .Lkvk_box
                li   t1, 1
                bne  t0, t1, .Lkvk_zero
                li   a0, 1
                ret
            .Lkvk_box:
                li   a0, 2
                ret
            .Lkvk_zero:
                li   a0, 0
                ret

            # kof_map_contains_value(a0=map, a1=val, a2=tag) -> 0/1 (#386)
            # tag: 0=raw, 1=String, 2=caixa MAGIC, 3=miss garantido,
            # 6=valor Object dinâmico (§352: classifica arg e entradas com
            # kof_value_kind — box/box, str/str, ptr/ptr; kind ≠ = miss).
            .globl kof_map_contains_value
            kof_map_contains_value:
                li   t0, 3
                beq  a2, t0, .Lmlcv_zero
                li   t0, 6
                beq  a2, t0, .Lmlcv_dyn
                addi sp, sp, -56
                sd   ra, 48(sp)
                sd   s0, 40(sp)          # map
                sd   s1, 32(sp)          # val
                sd   s2, 24(sp)          # tag
                sd   s3, 16(sp)          # i
                sd   s4, 8(sp)           # slot
                sd   s5, 0(sp)           # arr
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                li   s3, 0
            .Lmlcv_loop:
                lw   t1, 16(s0)
                bge  s3, t1, .Lmlcv_no
                ld   t1, 32(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   s4, 0(t1)
                li   t0, 1
                beq  s2, t0, .Lmlcv_str
                li   t0, 2
                beq  s2, t0, .Lmlcv_box
                li   t0, 7
                beq  s2, t0, .Lmlcv_obj
                bne  s4, s1, .Lmlcv_next
                j    .Lmlcv_yes
            .Lmlcv_str:
                mv   a0, s4
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Lmlcv_yes
                j    .Lmlcv_next
            .Lmlcv_box:
                mv   a0, s4
                mv   a1, s1
                call kof_box_equals
                bnez a0, .Lmlcv_yes
                j    .Lmlcv_next
            .Lmlcv_obj:
                # #604 (§104b-ii, face VALOR): mesma sonda de conteúdo que
                # get/containsKey/remove já usam pra CHAVE desde hoje.
                mv   a0, s4
                mv   a1, s1
                call kof_obj_equals
                bnez a0, .Lmlcv_yes
            .Lmlcv_next:
                addi s3, s3, 1
                j    .Lmlcv_loop
            .Lmlcv_dyn:
                addi sp, sp, -56
                sd   ra, 48(sp)
                sd   s0, 40(sp)          # map
                sd   s1, 32(sp)          # val
                sd   s2, 24(sp)          # kind do arg
                sd   s3, 16(sp)          # i
                sd   s4, 8(sp)           # slot
                sd   s5, 0(sp)           # reserva
                mv   s0, a0
                mv   s1, a1
                mv   a0, s1
                call kof_value_kind
                mv   s2, a0
                li   s3, 0
            .Lmlcv_dloop:
                lw   t1, 16(s0)
                bge  s3, t1, .Lmlcv_no
                ld   t1, 32(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   s4, 0(t1)
                mv   a0, s4
                call kof_value_kind
                bne  a0, s2, .Lmlcv_dnext
                li   t0, 2
                beq  s2, t0, .Lmlcv_dbox
                li   t0, 1
                beq  s2, t0, .Lmlcv_dstr
                bne  s4, s1, .Lmlcv_dnext
                j    .Lmlcv_yes
            .Lmlcv_dbox:
                mv   a0, s4
                mv   a1, s1
                call kof_box_equals
                bnez a0, .Lmlcv_yes
                j    .Lmlcv_dnext
            .Lmlcv_dstr:
                mv   a0, s4
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Lmlcv_yes
            .Lmlcv_dnext:
                addi s3, s3, 1
                j    .Lmlcv_dloop
            .Lmlcv_yes:
                li   a0, 1
                j    .Lmlcv_ret
            .Lmlcv_no:
                li   a0, 0
            .Lmlcv_ret:
                ld   s5, 0(sp)
                ld   s4, 8(sp)
                ld   s3, 16(sp)
                ld   s2, 24(sp)
                ld   s1, 32(sp)
                ld   s0, 40(sp)
                ld   ra, 48(sp)
                addi sp, sp, 56
                ret
            .Lmlcv_zero:
                li   a0, 0
                ret

            # kof_map_put_if_absent(a0=map, a1=key, a2=val) -> anterior | 0
            .globl kof_map_put_if_absent
            kof_map_put_if_absent:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)          # map
                sd   s1, 16(sp)          # key
                sd   s2, 8(sp)           # val
                sd   s3, 0(sp)           # idx
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                call kof_map_find
                mv   s3, a0
                li   t0, -1
                beq  s3, t0, .Lmpia_ins
                ld   t1, 32(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a0, 0(t1)           # anterior, sem sobrescrever
                j    .Lmpia_ret
            .Lmpia_ins:
                mv   a0, s0
                mv   a1, s1
                mv   a2, s2
                call kof_map_put         # insere; retorno já é 0 (null)
                li   a0, 0
            .Lmpia_ret:
                ld   s3, 0(sp)
                ld   s2, 8(sp)
                ld   s1, 16(sp)
                ld   s0, 24(sp)
                ld   ra, 32(sp)
                addi sp, sp, 40
                ret

            # kof_list_cmp(a0=a, a1=b, a2=tag) -> a0 -1/0/1 (#382)
            # tag 3 = Float (§352/NAT001 fechado 21/09): o slot guarda os 32
            # bits crus; alarga p/ Double (fmv.w.x + fcvt.d.s) e reusa a
            # semântica do Double.compare — sem flw, só mnemônicos que o
            # tradutor aarch64 conhece.
            .globl kof_list_cmp
            kof_list_cmp:
                li   t0, 1
                beq  a2, t0, .Llk_str
                li   t0, 2
                beq  a2, t0, .Llk_dbl
                li   t0, 3
                beq  a2, t0, .Llk_flt
                j    .Llk_int
            .Llk_dbl:
                fmv.d.x f0, a0
                fmv.d.x f1, a1
                j    .Llk_fp
            .Llk_flt:
                fmv.w.x f0, a0
                fmv.w.x f1, a1
                fcvt.d.s f0, f0
                fcvt.d.s f1, f1
                fmv.x.d a0, f0             # bits double p/ o check de ±0.0
                fmv.x.d a1, f1
            .Llk_fp:
                flt.d t0, f0, f1
                bnez t0, .Llk_lt
                flt.d t0, f1, f0
                bnez t0, .Llk_gt
                feq.d t0, f0, f0
                beqz t0, .Llk_aNaN
                feq.d t0, f1, f1
                beqz t0, .Llk_lt            # b NaN: a < b
                xor  t1, a0, a1
                beqz t1, .Llk_eq
                slt  t2, a0, x0
                beqz t2, .Llk_gt            # a=+0.0, b=-0.0
                j    .Llk_lt                # a=-0.0 < b=+0.0
            .Llk_aNaN:
                feq.d t0, f1, f1
                beqz t0, .Llk_eq            # ambos NaN = 0 (Double.compare)
                j    .Llk_gt                # a NaN > não-NaN
            .Llk_str:
                j    String_compareTo
            .Llk_int:
                slt  t0, a0, a1
                bnez t0, .Llk_lt
                slt  t0, a1, a0
                bnez t0, .Llk_gt
            .Llk_eq:
                li   a0, 0
                ret
            .Llk_lt:
                li   a0, -1
                ret
            .Llk_gt:
                li   a0, 1
                ret

            # kof_list_sort(a0=list, a1=tag) — seleção in-place (#382)
            .globl kof_list_sort
            kof_list_sort:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)          # list
                sd   s1, 56(sp)          # tag
                sd   s2, 48(sp)          # i
                sd   s3, 40(sp)          # min
                sd   s4, 32(sp)          # j
                sd   s5, 24(sp)          # n
                sd   s6, 16(sp)          # tmp/vmin
                sd   s7, 8(sp)           # a
                sd   s8, 0(sp)           # b
                mv   s0, a0
                mv   s1, a1
                lw   s5, 16(s0)
                li   s2, 0
            .Lls_i:
                bge  s2, s5, .Lls_done
                mv   s3, s2              # min = i
                addi s4, s2, 1           # j = i+1
            .Lls_j:
                bge  s4, s5, .Lls_swap
                mv   a0, s0
                mv   a1, s4
                call kof_list_get
                mv   s7, a0              # a = get(j)
                mv   a0, s0
                mv   a1, s3
                call kof_list_get
                mv   s8, a0              # b = get(min)
                mv   a0, s7
                mv   a1, s8
                mv   a2, s1
                call kof_list_cmp
                bgez a0, .Lls_next
                mv   s3, s4              # min = j
            .Lls_next:
                addi s4, s4, 1
                j    .Lls_j
            .Lls_swap:
                beq  s3, s2, .Lls_iinc
                mv   a0, s0
                mv   a1, s2
                call kof_list_get
                mv   s6, a0              # tmp = get(i)
                mv   a0, s0
                mv   a1, s3
                call kof_list_get
                mv   a2, a0              # vmin
                mv   a0, s0
                mv   a1, s2
                call kof_list_set        # set(i, vmin)
                mv   a0, s0
                mv   a1, s3
                mv   a2, s6
                call kof_list_set        # set(min, tmp)
            .Lls_iinc:
                addi s2, s2, 1
                j    .Lls_i
            .Lls_done:
                ld   s8, 0(sp)
                ld   s7, 8(sp)
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

            # kof_list_index_of(a0=list, a1=elem, a2=tag) -> idx | -1
            .globl kof_list_index_of
            kof_list_index_of:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)          # list
                sd   s1, 16(sp)          # elem
                sd   s2, 8(sp)           # tag
                sd   s3, 0(sp)           # i
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                li   s3, 0
            .Llio_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Llio_none
                ld   t0, 24(s0)
                slli t1, s3, 3
                add  t0, t0, t1
                ld   t2, 0(t0)
                li   t0, 1
                beq  s2, t0, .Llio_str
                li   t0, 2
                beq  s2, t0, .Llio_obj
                bne  t2, s1, .Llio_next
                j    .Llio_hit
            .Llio_str:
                mv   a0, t2
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Llio_hit
                j    .Llio_next
            .Llio_obj:
                beqz t2, .Llio_next
                mv   a0, t2
                mv   a1, s1
                call kof_obj_equals
                bnez a0, .Llio_hit
            .Llio_next:
                addi s3, s3, 1
                j    .Llio_loop
            .Llio_hit:
                mv   a0, s3
                j    .Llio_ret
            .Llio_none:
                li   a0, -1
            .Llio_ret:
                ld   s3, 0(sp)
                ld   s2, 8(sp)
                ld   s1, 16(sp)
                ld   s0, 24(sp)
                ld   ra, 32(sp)
                addi sp, sp, 40
                ret

            # kof_list_last_index_of(a0=list, a1=elem, a2=tag) -> idx | -1
            # (s2/tag no stack: o kof_string_equals clobbera t0-t6 — os
            #  registradores do loop inteiro sao salvos s0-s3)
            .globl kof_list_last_index_of
            kof_list_last_index_of:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)          # list
                sd   s1, 16(sp)          # elem
                sd   s2, 8(sp)           # tag
                sd   s3, 0(sp)           # i (descendente)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                lw   s3, 16(s0)
                addi s3, s3, -1
            .Lllo_loop:
                blt  s3, zero, .Lllo_none
                ld   t0, 24(s0)
                slli t1, s3, 3
                add  t0, t0, t1
                ld   t2, 0(t0)
                li   t0, 1
                beq  s2, t0, .Lllo_str
                li   t0, 2
                beq  s2, t0, .Lllo_obj
            .Lllo_raw:
                bne  t2, s1, .Lllo_next
                j    .Lllo_hit
            .Lllo_str:
                mv   a0, t2
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Lllo_hit
                j    .Lllo_next
            .Lllo_obj:
                beqz t2, .Lllo_next
                mv   a0, t2
                mv   a1, s1
                call kof_obj_equals
                bnez a0, .Lllo_hit
                j    .Lllo_next
            .Lllo_next:
                addi s3, s3, -1
                j    .Lllo_loop
            .Lllo_hit:
                mv   a0, s3
                j    .Lllo_ret
            .Lllo_none:
                li   a0, -1
            .Lllo_ret:
                ld   s3, 0(sp)
                ld   s2, 8(sp)
                ld   s1, 16(sp)
                ld   s0, 24(sp)
                ld   ra, 32(sp)
                addi sp, sp, 40
                ret

            # kof_list_sub_list(a0=list, a1=begin, a2=end) -> nova List
            # bounds honesto (familia get(i)); copia materializada. Os
            # registradores do loop sao todos salvos (kof_list_add clobbera
            # t0-t6 a cada passo).
            .globl kof_list_sub_list
            kof_list_sub_list:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # list
                sd   s1, 24(sp)          # begin
                sd   s2, 16(sp)          # end
                sd   s3, 8(sp)           # nova
                sd   s4, 0(sp)           # i
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                blt  s1, zero, .Llsl_bad
                lw   t0, 16(s0)
                bgt  s2, t0, .Llsl_bad
                bgt  s1, s2, .Llsl_bad
                call kof_list_new
                mv   s3, a0
                mv   s4, s1
            .Llsl_loop:
                bge  s4, s2, .Llsl_done
                mv   a0, s3
                ld   t0, 24(s0)
                slli t1, s4, 3
                add  t0, t0, t1
                ld   a1, 0(t0)
                call kof_list_add
                addi s4, s4, 1
                j    .Llsl_loop
            .Llsl_done:
                mv   a0, s3
                j    .Llsl_ret
            .Llsl_bad:
                blt  s1, zero, .Llsl_bidx
                mv   a0, s2                      # ofensor = end (fam. JVM toIndex)
                j    .Llsl_berr
            .Llsl_bidx:
                mv   a0, s1                      # ofensor = begin negativo
            .Llsl_berr:
                lw   a1, 16(s0)
                call kof_bounds_error
            .Llsl_ret:
                ld   s4, 0(sp)
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_add_all(a0=dst, a1=src) -> 0/1 mudou (#386/#382)
            # espelho riscv do x86 RuntimeListLookups: copia elemento a
            # elemento via kof_list_add; mudou = tamanho final != inicial.
            .globl kof_list_add_all
            kof_list_add_all:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)          # dst
                sd   s1, 16(sp)          # src
                sd   s2, 8(sp)           # old size
                sd   s3, 0(sp)           # j
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                li   s3, 0
            .Llaa_loop:
                lw   t0, 16(s1)
                bge  s3, t0, .Llaa_done
                mv   a0, s0
                ld   t0, 24(s1)
                slli t1, s3, 3
                add  t0, t0, t1
                ld   a1, 0(t0)
                call kof_list_add
                addi s3, s3, 1
                j    .Llaa_loop
            .Llaa_done:
                li   a0, 0
                lw   t0, 16(s0)
                beq  t0, s2, .Llaa_ret
                li   a0, 1
            .Llaa_ret:
                ld   s3, 0(sp)
                ld   s2, 8(sp)
                ld   s1, 16(sp)
                ld   s0, 24(sp)
                ld   ra, 32(sp)
                addi sp, sp, 40
                ret
            """;
}
