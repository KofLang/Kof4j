package dev.kof.compiler.nat;

// PORT (beta-0.3.0 §113 → 0.4.0): fatia 37 — kof_multi_alloc riscv64.
// Espelha o recursivo x86 (RuntimeArray.emitMultiArrayAlloc) com frames
// FIXOS e ABI próprio: a0=dimsBase (o sp do chamador depois dos empurra —
// d_n em +0, d_1 em +8(n-1); MESMA fórmula `8*(n-i)` do x86), a1=n, a2=i
// (1-based), a3=leafStride. Nós internos: elemSize 8 (ponteiros), slots
// preenchidos pela recursão; folha: elemSize=a3 com payload ZEROED byte a
// byte (paridade MULTIANEWARRAY/newMultiArray — kof_alloc é bump pointer
// sem zero; rep stosb do x86 vira laço sb). aarch64 herda via tradutor
// (sub/slli/mul/sd/ld/bge/beqz — todos cobertos).
// LIÇÕES B25b/B36 (fadiga de numeração): fatia nova SÓ com 0 colisões
// .L/.globl — verificadas contra a árvore vencedora.
final class NativeRiscvAsmRtB37 {

    private NativeRiscvAsmRtB37() {}

    static final String RISCV_RUNTIME_ASM_B_37 = """

            .section .text

            # kof_multi_alloc(a0=dimsBase, a1=n, a2=i, a3=leafStride) -> a0=nó
            .globl kof_multi_alloc
            kof_multi_alloc:
                addi sp, sp, -112
                sd   ra, 104(sp)
                sd   s0, 96(sp)
                sd   s1, 88(sp)
                sd   s2, 80(sp)
                sd   s3, 72(sp)
                sd   s4, 64(sp)
                sd   s5, 56(sp)
                sd   s6, 48(sp)
                mv   s6, a0                # dimsBase
                mv   s2, a1                # n
                mv   s3, a2                # i
                mv   s4, a3                # leafStride
                sub  t0, s2, s3            # n - i
                slli t0, t0, 3
                add  t0, s6, t0
                ld   s1, 0(t0)             # d_i
                mv   a0, s1                # len = d_i
                li   a1, 8                 # nó interno: elemSize 8 (ponteiros)
                bne  s3, s2, .Ls37m_go
                mv   a1, s4                # folha: stride do baseType
            .Ls37m_go:
                call kof_array_alloc
                mv   s0, a0                # nó
                bne  s3, s2, .Ls37m_fill
                addi t1, s0, 24            # payload da folha → zero
                mul  t2, s1, s4
            .Ls37m_zero:
                beqz t2, .Ls37m_done
                sb   zero, 0(t1)
                addi t1, t1, 1
                addi t2, t2, -1
                j    .Ls37m_zero
            .Ls37m_fill:
                li   s5, 0                 # j
            .Ls37m_floop:
                bge  s5, s1, .Ls37m_done
                mv   a0, s6
                mv   a1, s2
                addi a2, s3, 1             # i + 1
                mv   a3, s4
                call kof_multi_alloc
                slli t3, s5, 3
                addi t4, s0, 24
                add  t4, t4, t3
                sd   a0, 0(t4)             # slot j = filho
                addi s5, s5, 1
                j    .Ls37m_floop
            .Ls37m_done:
                mv   a0, s0
                ld   ra, 104(sp)
                ld   s0, 96(sp)
                ld   s1, 88(sp)
                ld   s2, 80(sp)
                ld   s3, 72(sp)
                ld   s4, 64(sp)
                ld   s5, 56(sp)
                ld   s6, 48(sp)
                addi sp, sp, 112
                ret
        """;
}
