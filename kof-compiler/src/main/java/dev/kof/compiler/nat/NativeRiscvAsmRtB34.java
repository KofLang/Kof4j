package dev.kof.compiler.nat;

/**
 * Fatia B34 — faces UTF-16 de String no riscv64 (residual do bug 43 —
 * "riscv/aarch ainda byte-based" registrado na linha do §43):
 * kof_string_length / kof_string_char_at / kof_string_substring contam
 * CODE UNITS UTF-16 (igual JVM/JS/x86), não bytes UTF-8. Port 1:1 dos
 * refs x86 (RuntimeStringBase.emitStringLength; RuntimeStringOps
 * charAt high/low + .Lkof_substr_walk; as 2 calls de walk são o design
 * x86 — nenhum total-walk extra). Substitui os corpos byte-based
 * (removidos de Rt0/Rt1 — sem duplicatas no `.s`). aarch64 herda via
 * tradutor. PROVA: NativeRiscv64E2ETest/NativeAarch64E2ETest
 * stringUtf16Faces (golden = oracle JVM medido, sob qemu).
 * json.decode (AsmMapset1) usa substring com offsets BYTE em JSON
 * ASCII-only → intocável (units==bytes em ASCII; astral-em-JSON-value
 * = sub-residual do §43, não desta fatia).
 */
final class NativeRiscvAsmRtB34 {

    private NativeRiscvAsmRtB34() {}

    static final String RISCV_RUNTIME_ASM_B_34 = """
            .section .rodata
            .Lu9_sa_msg: .asciz "Runtime error: substring cannot split an astral code point (native UTF-16 face pending, known-bugs 43)\\n"
            .section .text

            # .Lu9_walk(str@a0, target@a1) -> a0=byteOff do target,
            # t3=units consumidas, a2=1 se o target caiu no LOW de um par
            # astral (corte proibido). Ordem x86 (.Lkof_substr_walk): hit
            # ANTES do fim (target == total → hit com off=byteLen; target >
            # total → end com t3=total < target = bounds p/ o caller).
            # só t-regs + a0/a1/t0/t3.
            .Lu9_walk:
                lw   t1, 16(a0)                # byteLen
                li   t0, 0                     # off
                li   t3, 0                     # units
                li   a2, 0                     # cut
            .Lu9_w_loop:
                bge  t3, a1, .Lu9_w_hit
                bgeu t0, t1, .Lu9_w_end
                addi t4, a0, 24
                add  t4, t4, t0
                lbu  t4, 0(t4)
                andi t5, t4, 128
                beqz t5, .Lu9_w_1
                andi t5, t4, 224
                li   t6, 192
                beq  t5, t6, .Lu9_w_2
                andi t5, t4, 240
                li   t6, 224
                beq  t5, t6, .Lu9_w_3
                addi t5, t3, 1
                beq  t5, a1, .Lu9_w_cut        # target = LOW do par
                addi t3, t3, 2
                addi t0, t0, 4
                j    .Lu9_w_loop
            .Lu9_w_1:
                addi t3, t3, 1
                addi t0, t0, 1
                j    .Lu9_w_loop
            .Lu9_w_2:
                addi t3, t3, 1
                addi t0, t0, 2
                j    .Lu9_w_loop
            .Lu9_w_3:
                addi t3, t3, 1
                addi t0, t0, 3
                j    .Lu9_w_loop
            .Lu9_w_hit:
                mv   a0, t0                    # byteOff → a0 (saída x86 eax)
                ret
            .Lu9_w_cut:
                li   a2, 1
                mv   a0, t0
                ret
            .Lu9_w_end:
                mv   a0, t0
                ret

            # .Lu9_dec(&lead@a0) -> a0 = code unit (astral → HIGH).
            # .Lu9_dec4l(&lead@a0) -> a0 = LOW do par. só t-regs (a1 t3 t5 t6).
            .Lu9_dec:
                lbu  t2, 0(a0)
                andi t5, t2, 128
                beqz t5, .Lu9_d1
                andi t5, t2, 224
                li   t6, 192
                beq  t5, t6, .Lu9_d2
                andi t5, t2, 240
                li   t6, 224
                beq  t5, t6, .Lu9_d3
                andi t2, t2, 7
                slli t2, t2, 18
                lbu  t3, 1(a0)
                andi t3, t3, 63
                slli t3, t3, 12
                or   t2, t2, t3
                lbu  t3, 2(a0)
                andi t3, t3, 63
                slli t3, t3, 6
                or   t2, t2, t3
                lbu  t3, 3(a0)
                andi t3, t3, 63
                or   t2, t2, t3
                li   t5, 65536
                sub  t2, t2, t5
                srli t2, t2, 10
                li   t5, 55296                 # 0xD800
                add  a0, t2, t5
                ret
            .Lu9_dec4l:
                lbu  t2, 0(a0)
                andi t2, t2, 7
                slli t2, t2, 18
                lbu  t3, 1(a0)
                andi t3, t3, 63
                slli t3, t3, 12
                or   t2, t2, t3
                lbu  t3, 2(a0)
                andi t3, t3, 63
                slli t3, t3, 6
                or   t2, t2, t3
                lbu  t3, 3(a0)
                andi t3, t3, 63
                or   t2, t2, t3
                li   t5, 65536
                sub  t2, t2, t5
                andi t2, t2, 1023
                li   t5, 56320                 # 0xDC00
                add  a0, t2, t5
                ret
            .Lu9_d1:
                mv   a0, t2
                ret
            .Lu9_d2:
                andi t2, t2, 31
                slli t2, t2, 6
                lbu  t3, 1(a0)
                andi t3, t3, 63
                or   a0, t2, t3
                ret
            .Lu9_d3:
                andi t2, t2, 15
                slli t2, t2, 12
                lbu  t3, 1(a0)
                andi t3, t3, 63
                slli t3, t3, 6
                or   t2, t2, t3
                lbu  t3, 2(a0)
                andi t3, t3, 63
                or   a0, t2, t3
                ret

            # kof_string_length(str@a0) -> Int code units UTF-16
            # (café=4, a😀b=4 — JVM/x86 idêntico; astral → 2).
            .globl kof_string_length
            kof_string_length:
                lw   t1, 16(a0)                # byteLen
                li   t0, 0                     # units
                li   t3, 0                     # i
            .Lu9_len_loop:
                bgeu t3, t1, .Lu9_len_done
                addi t4, a0, 24
                add  t4, t4, t3
                lbu  t5, 0(t4)
                andi t4, t5, 128
                beqz t4, .Lu9_len_1
                andi t4, t5, 224
                li   t6, 192
                beq  t4, t6, .Lu9_len_2
                andi t4, t5, 240
                li   t6, 224
                beq  t4, t6, .Lu9_len_3
                addi t0, t0, 2
                addi t3, t3, 4
                j    .Lu9_len_loop
            .Lu9_len_1:
                addi t0, t0, 1
                addi t3, t3, 1
                j    .Lu9_len_loop
            .Lu9_len_2:
                addi t0, t0, 1
                addi t3, t3, 2
                j    .Lu9_len_loop
            .Lu9_len_3:
                addi t0, t0, 1
                addi t3, t3, 3
                j    .Lu9_len_loop
            .Lu9_len_done:
                mv   a0, t0
                ret

            # kof_string_char_at(str@a0, idx@a1) -> Int code unit UTF-16
            # (astral: idx high → HIGH, idx low → LOW — port do
            # kof_string_char_at x86; walk próprio com s0/s1 vivos sobre
            # o call .Lu9_dec*).
            .globl kof_string_char_at
            kof_string_char_at:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                blt  a1, zero, .Lu9_ca_bounds
                lw   t1, 16(s0)                # byteLen
                li   t3, 0                     # off
                li   t4, 0                     # units
            .Lu9_ca_walk:
                bgeu t3, t1, .Lu9_ca_bounds
                bge  t4, s1, .Lu9_ca_at
                addi t5, s0, 24
                add  t5, t5, t3
                lbu  t5, 0(t5)
                andi t0, t5, 128
                beqz t0, .Lu9_ca_a1
                andi t0, t5, 224
                li   t6, 192
                beq  t0, t6, .Lu9_ca_a2
                andi t0, t5, 240
                li   t6, 224
                beq  t0, t6, .Lu9_ca_a3
                addi t0, t4, 1
                beq  t0, s1, .Lu9_ca_low
                addi t4, t4, 2
                addi t3, t3, 4
                j    .Lu9_ca_walk
            .Lu9_ca_a1:
                addi t4, t4, 1
                addi t3, t3, 1
                j    .Lu9_ca_walk
            .Lu9_ca_a2:
                addi t4, t4, 1
                addi t3, t3, 2
                j    .Lu9_ca_walk
            .Lu9_ca_a3:
                addi t4, t4, 1
                addi t3, t3, 3
                j    .Lu9_ca_walk
            .Lu9_ca_at:
                addi a0, s0, 24
                add  a0, a0, t3
                call .Lu9_dec
                j    .Lu9_ca_ret
            .Lu9_ca_low:
                addi a0, s0, 24
                add  a0, a0, t3
                call .Lu9_dec4l
            .Lu9_ca_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lu9_ca_bounds:
                j    kof_bounds_error          # tail-jmp (lição B33: ra)

            # kof_string_substring(str@a0, start@a1, end@a2) -> KofStr*
            # índices = code units UTF-16; end=0 → até o fim; corte no
            # meio do par astral → kof_panic (diagnóstico R6, como o x86).
            # 2x .Lu9_walk (design x86); valores vivos só em s-reg/pilha
            # entre calls.
            .globl kof_string_substring
            kof_string_substring:
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
                mv   s2, a2
                blt  s1, zero, .Lu9_ss_bounds
                mv   a0, s0
                mv   a1, s1
                call .Lu9_walk
                bnez a2, .Lu9_ss_panic
                blt  t3, s1, .Lu9_ss_bounds    # units < start → start > total (x86: cmpl %r12d,%edx; jb)
                mv   s3, a0                    # startOff
                bltz s2, .Lu9_ss_tofim
                mv   a0, s0
                mv   a1, s2
                call .Lu9_walk
                bnez a2, .Lu9_ss_panic
                blt  t3, s2, .Lu9_ss_bounds    # units < end → end > total
                bltu a0, s3, .Lu9_ss_bounds    # end < start
                mv   s4, a0                    # endOff
                j    .Lu9_ss_copy
            .Lu9_ss_tofim:
                lw   s4, 16(s0)                # byteLen
            .Lu9_ss_copy:
                sub  s5, s4, s3                # lenBytes
                addi a0, s5, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s6, a0
                li   t0, 1
                sw   t0, 0(s6)
                sw   zero, 4(s6)
                sd   zero, 8(s6)
                sw   s5, 16(s6)
                sw   zero, 20(s6)
                addi a0, s6, 24
                addi a1, s0, 24
                add  a1, a1, s3
                mv   a2, s5
                call kof_memcpy
                addi t1, s6, 24
                add  t1, t1, s5
                sb   zero, 0(t1)
                mv   a0, s6
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
            .Lu9_ss_bounds:
                j    kof_bounds_error
            .Lu9_ss_panic:
                la   a0, .Lu9_sa_msg
                j    kof_panic
            """;
}
