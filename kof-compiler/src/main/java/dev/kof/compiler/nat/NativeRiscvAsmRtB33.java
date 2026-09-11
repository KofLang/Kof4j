package dev.kof.compiler.nat;

// FASE (bug 43 cross): fatia 33 de RISCV_RUNTIME_ASM_B — String length/charAt
// riscv64 em CODE UNITS UTF-16. Port das faces x86_64 já corrigidas
// (RuntimeStringBase.kof_string_length / RuntimeStringOps.emitStringCharAt):
// o contrato é SEQUÊNCIA de code units UTF-16 (par astral = high+low = 2),
// não bytes UTF-8 — "café".length=4 (era 5), café.charAt(3)=233 (era 195),
// "a😀b": length=4, charAt(1)=55357, charAt(2)=56832, charAt(3)=98. Reusa o
// decoder `kof_su_next` da B32 (cursor {off@0,pendLow@4}, só t-regs, preserva
// a0/a1/a2); bounds com contagem de UNITS (walk até achar a unit ou o fim),
// kof_bounds_error no fora (mesmo panic do path antigo — paridade x86).
// aarch64 herda do tradutor riscv→aarch (lição B25/B32).
//
// Chamadas via trampoline (`j`) dos símbolos globais byte-based em Rt0/Rt1 —
// corpo aqui p/ não crescer as fatias gigantes. Lição da B32: lw de valor
// negativo via pilha precisa de sext.w (tradutor = zero-extend); o retorno de
// charAt/length é sempre unit >= 0 (a sentinela -1 do decoder nunca vira
// valor de saída — bounds primeiro), então sem risco aqui; ainda assim o
// kof_su_next retorna a0 64-bit limpo (valores <= 0xFFFF).
//
// ⚠ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB33 {

    private NativeRiscvAsmRtB33() {}

    static final String RISCV_RUNTIME_ASM_B_33 = """

            .section .text
            # kof_su_length(a0=str) -> a0 = n de code units UTF-16
            .globl kof_su_length
            kof_su_length:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0                # str
                lw   s1, 16(s0)            # byteLen
                li   s2, 0                 # contador de units
                sw   zero, 0(sp)           # cursor.off
                sw   zero, 4(sp)           # cursor.pend
            .Ls43l_loop:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s1
                call kof_su_next
                li   t1, -1
                beq  a0, t1, .Ls43l_done
                addi s2, s2, 1
                j    .Ls43l_loop
            .Ls43l_done:
                mv   a0, s2
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                addi sp, sp, 48
                ret

            # kof_su_char_at(a0=str, a1=idx) -> a0 = code unit UTF-16 idx
            # bounds EM UNITS (idx >= length ou < 0 → kof_bounds_error).
            # Lição: contador do laço em s-REG (t2 seria clobbered pelo
            # kof_su_next, que usa t0..t6).
            .globl kof_su_char_at
            kof_su_char_at:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0                # str
                mv   s1, a1                # idx desejado
                lw   s2, 16(s0)            # byteLen
                sw   zero, 0(sp)           # cursor.off
                sw   zero, 4(sp)           # cursor.pend
                li   s3, 0                 # i (unidades consumidas)
            .Ls43c_loop:
                addi a0, s0, 24
                mv   a1, sp
                mv   a2, s2
                call kof_su_next
                li   t1, -1
                beq  a0, t1, .Ls43c_bounds # idx além do fim (unidades)
                beq  s3, s1, .Ls43c_ret    # i == idx → retorna unit em a0
                addi s3, s3, 1
                j    .Ls43c_loop
            .Ls43c_ret:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret
            .Ls43c_bounds:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                j    kof_bounds_error
            """;
}
