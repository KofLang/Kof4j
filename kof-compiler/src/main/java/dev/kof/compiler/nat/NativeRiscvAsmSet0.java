package dev.kof.compiler.nat;

// FASE 3 (REFACTOR-500): a fatia SET da Mapset0 (kof_set_* + o epilogo rodata
// .Lkmsv_magic §284 que fecha a antiga fatia 0) — runtime assembly riscv64.
// Dividida da NativeRiscvAsmMapset0 pelo gate check_500 (606 linhas); concatenada
// ENTRE a Mapset0 e a Mapset1 por NativeRiscvAsm — a ordem preserva o asm
// byte-idêntico (corpo verbatim, fechamento na coluna 12).
public final class NativeRiscvAsmSet0 {

    private NativeRiscvAsmSet0() {}

    static  String RISCV_SET_ASM_0 = """
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
                li   t4, 2
                beq  s2, t4, .Lksc_obj
                beq  t3, s1, .Lksc_yes   # pointer
                j    .Lksc_next
            .Lksc_str:
                beqz t3, .Lksc_next
                mv   a0, t3
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Lksc_yes
                j    .Lksc_next
            .Lksc_obj:
                beqz t3, .Lksc_next
                mv   a0, t3
                mv   a1, s1
                call kof_obj_equals
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
                li   t4, 2
                beq  s2, t4, .Lksr_obj
                beq  t3, s1, .Lksr_found
                j    .Lksr_next
            .Lksr_str:
                beqz t3, .Lksr_next
                mv   a0, t3
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Lksr_found
                j    .Lksr_next
            .Lksr_obj:
                beqz t3, .Lksr_next
                mv   a0, t3
                mv   a1, s1
                call kof_obj_equals
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

            .section .rodata
            .p2align 3
            .Lkmsv_magic: .8byte 0x4B4F46425F425801   # MAGIC §284 (RuntimeErasureBox)
            .text
            """;
}
