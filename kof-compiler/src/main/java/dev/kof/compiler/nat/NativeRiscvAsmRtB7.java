package dev.kof.compiler.nat;

// FASE 3 (STDLIB S2b): fatia 7 de RISCV_RUNTIME_ASM_B — kof.strings conversores
// que ALOCAM String (capitalize/reverse). Layout KofStr: typeId=1@0, super@4,
// vtable@8(len 8B), length@16, pad@20, bytes@24, NUL final. Alocação = len+25
// alinhada a 16 (padrão kof_string_from_literal em NativeRiscvAsmRt0).
// Convenção RISC-V LP64: a0..a2 = args, a0 = retorno. ASCII (byte 0-255).
public final class NativeRiscvAsmRtB7 {

    static final String RISCV_RUNTIME_ASM_B_7 = """

            # ── kof.strings (STDLIB S2b) — conversores que alocam ─────

            # kof_strings_capitalize(a0=str) -> String (1º byte [a-z] => -32)
            # null/"" => retorna ponteiro original.
            .section .text
            .globl kof_strings_capitalize
            kof_strings_capitalize:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0              # str original
                beqz s0, .Lv_str_cap_orig
                lw   s1, 16(s0)          # len
                blez s1, .Lv_str_cap_orig
                # alocar (len+25+15)&-16
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0              # novo obj
                li   t0, 1
                sw   t0, 0(s3)
                li   t0, 0
                sw   t0, 4(s3)
                sd   t0, 8(s3)
                sw   s1, 16(s3)
                sw   t0, 20(s3)
                # primeiro byte, possivelmente maiusculado
                lbu  t1, 24(s0)          # s[0]
                li   t2, 97
                blt  t1, t2, .Lv_str_cap_put   # < 'a'
                li   t2, 122
                bgt  t1, t2, .Lv_str_cap_put   # > 'z'
                addi t1, t1, -32         # -> [A-Z]
            .Lv_str_cap_put:
                sb   t1, 24(s3)
                # resto: memcpy(novo+25, orig+25, len-1)
                addi s2, s1, -1          # len-1
                beqz s2, .Lv_str_cap_term
                addi a0, s3, 25
                addi a1, s0, 25
                mv   a2, s2
                call kof_memcpy
            .Lv_str_cap_term:
                li   t0, 0
                addi t1, s3, 24
                add  t1, t1, s1
                sb   t0, 0(t1)           # NUL
                mv   a0, s3
                j    .Lv_str_cap_done
            .Lv_str_cap_orig:
                mv   a0, s0
            .Lv_str_cap_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            # kof_strings_reverse(a0=str) -> String (byte-reverso, ASCII)
            # null/"" => retorna ponteiro original.
            .globl kof_strings_reverse
            kof_strings_reverse:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                beqz s0, .Lv_str_rev_orig
                lw   s1, 16(s0)
                blez s1, .Lv_str_rev_orig
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                li   t0, 0
                sw   t0, 4(s3)
                sd   t0, 8(s3)
                sw   s1, 16(s3)
                sw   t0, 20(s3)
                li   s2, 0               # i
            .Lv_str_rev_loop:
                bge  s2, s1, .Lv_str_rev_term
                addi t2, s1, -1
                sub  t2, t2, s2          # len-1-i
                add  t3, s0, 24
                add  t3, t3, t2
                lbu  t3, 0(t3)           # s[len-1-i]
                add  t4, s3, 24
                add  t4, t4, s2
                sb   t3, 0(t4)           # novo[i]
                addi s2, s2, 1
                j    .Lv_str_rev_loop
            .Lv_str_rev_term:
                li   t0, 0
                addi t1, s3, 24
                add  t1, t1, s1
                sb   t0, 0(t1)
                mv   a0, s3
                j    .Lv_str_rev_done
            .Lv_str_rev_orig:
                mv   a0, s0
            .Lv_str_rev_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            """;
}
