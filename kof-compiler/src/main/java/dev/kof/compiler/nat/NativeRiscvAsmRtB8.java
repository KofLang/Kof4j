package dev.kof.compiler.nat;

// FASE 3 (STDLIB S2b.2): fatia 8 de RISCV_RUNTIME_ASM_B — kof.strings
// repeat/truncate (alocam String; modelo B7/kof_string_from_literal).
// Convenção LP64: a0..a2 args, a0 retorno; s0-s4/pilha preservam estado.
public final class NativeRiscvAsmRtB8 {

    static final String RISCV_RUNTIME_ASM_B_8 = """

            # ── kof.strings (STDLIB S2b.2) — repeat/truncate ──────────

            # kof_strings_repeat(a0=str, a1=n) -> String
            # null/""/n<=0 => "". total = len*n (mul 64-bit; iguala o produto int
            # do JVM p/ qualquer total alocável — >2^31 bytes não aloca de todo).
            .section .text
            .globl kof_strings_repeat
            kof_strings_repeat:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                mv   s0, a0              # str
                mv   s2, a1              # n
                beqz s0, .Lv_str_rep_emp
                blez s2, .Lv_str_rep_emp
                lw   s1, 16(s0)          # len
                blez s1, .Lv_str_rep_emp
                mul  s1, s1, s2          # s1 = total = len*n
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   s1, 16(s3)
                sw   zero, 20(s3)
                li   s4, 0               # offset de destino
                li   t5, 0               # i
            .Lv_str_rep_outer:
                bge  t5, s2, .Lv_str_rep_term
                addi a0, s3, 24
                add  a0, a0, s4          # dst = novo.bytes + off
                addi a1, s0, 24          # src = str.bytes
                lw   a2, 16(s0)          # len original
                add  s4, s4, a2          # off += len
                call kof_memcpy
                addi t5, t5, 1
                j    .Lv_str_rep_outer
            .Lv_str_rep_term:
                addi t0, s3, 24
                add  t0, t0, s4
                sb   zero, 0(t0)         # NUL
                mv   a0, s3
                j    .Lv_str_rep_done
            .Lv_str_rep_emp:
                li   a0, 40
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   zero, 16(s3)
                sw   zero, 20(s3)
                sb   zero, 24(s3)
                mv   a0, s3
            .Lv_str_rep_done:
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                addi sp, sp, 64
                ret

            # kof_strings_truncate(a0=str, a1=n) -> String
            # null=>0; n<=0=>""; len<=n=>original; senao bytes[0,n).
            .globl kof_strings_truncate
            kof_strings_truncate:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0              # str
                mv   s2, a1              # n
                beqz s0, .Lv_str_tru_null
                lw   s1, 16(s0)          # len
                blez s2, .Lv_str_tru_emp
                bge  s2, s1, .Lv_str_tru_orig
                addi a0, s2, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   s2, 16(s3)
                sw   zero, 20(s3)
                addi a0, s3, 24
                addi a1, s0, 24
                mv   a2, s2
                call kof_memcpy
                addi t0, s3, 24
                add  t0, t0, s2
                sb   zero, 0(t0)         # NUL
                mv   a0, s3
                j    .Lv_str_tru_done
            .Lv_str_tru_orig:
                mv   a0, s0
                j    .Lv_str_tru_done
            .Lv_str_tru_emp:
                li   a0, 40
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   zero, 16(s3)
                sw   zero, 20(s3)
                sb   zero, 24(s3)
                mv   a0, s3
                j    .Lv_str_tru_done
            .Lv_str_tru_null:
                li   a0, 0
            .Lv_str_tru_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            """;
}
