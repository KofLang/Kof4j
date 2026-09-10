package dev.kof.compiler.nat;

// bug 79 (U3): kof_string_to_int/to_long riscv64 no contrato do JDK (parity
// com o x86 corrigido em 5e062076). O B0 antigo SILENCIOSO (pulava não-dígitos
// -> "abc"=0, "12a34"=1234, perdia sinal fora do índice 0) e NÃO tinha to_long
// (link quebrava). Algoritmo idêntico ao x86: trim (byte<=32 nas pontas),
// sinal +/-, dígito-a-digito, acumulação NEGATIVA (acc<=0; limit=MIN p/ valor
// negativo, -MAX p/ positivo; multmin=limit/10; overflow detectado por-dígito
// ANTES do *10 e antes da subtração). Falha = kof_string_from_literal +
// kof_throw_string (capturável em try/catch; sem handler = panic com código —
// nunca número silencioso, R6). LIÇÕES: frame -64 (ra+s0..s6=7*8=56->round up
// 64; PS 16-align); ra salvo (jalr no call de throw clobbera); len da String só
// em offset 16 (20=0 upper bits); aarch64 herda linha-a-linha (li com
// imediato 64-bit via movz/movk no tradutor; mul/blt/bgt/beqz/j já suportados
// — mesmo conjunto do to_int B0 anterior).
public final class NativeRiscvAsmRtB30 {

    private NativeRiscvAsmRtB30() {}

    private static final String TEMPLATE = """
            .globl %1$s
            %1$s:
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
                beqz s0, %2$s_throw
                lw   s1, 16(s0)
                li   s2, 0
                mv   s3, s1
            %2$s_tl:
                bge  s2, s3, %2$s_throw
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 32
                bgt  t0, t1, %2$s_th0
                addi s2, s2, 1
                j    %2$s_tl
            %2$s_th0:
                ble  s3, s2, %2$s_throw
                addi t0, s3, -1
                addi t0, t0, 24
                add  t0, s0, t0
                lbu  t0, 0(t0)
                li   t1, 32
                bgt  t0, t1, %2$s_sign
                addi s3, s3, -1
                j    %2$s_th0
            %2$s_sign:
                li   s4, 0
                li   s5, %4$s
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 45
                bne  t0, t1, %2$s_plus
                addi s2, s2, 1
                li   s5, %3$s
                li   s4, 1
                j    %2$s_body
            %2$s_plus:
                li   t1, 43
                beq  t0, t1, %2$s_p1
                li   t1, 48
                blt  t0, t1, %2$s_throw
                j    %2$s_body
            %2$s_p1:
                addi s2, s2, 1
            %2$s_body:
                bge  s2, s3, %2$s_throw
                li   s6, %5$s
                li   a0, 0
            %2$s_loop:
                bge  s2, s3, %2$s_fin
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                addi t2, t0, -48
                li   t1, 9
                bgt  t2, t1, %2$s_throw
                li   t1, 0
                blt  t2, t1, %2$s_throw
                blt  a0, s6, %2$s_throw
                li   t1, 10
                mul  a0, a0, t1
                add  t3, s5, t2
                blt  a0, t3, %2$s_throw
                sub  a0, a0, t2
                addi s2, s2, 1
                j    %2$s_loop
            %2$s_fin:
                beqz s4, %2$s_pos
                j    %2$s_done
            %2$s_pos:
                neg  a0, a0
            %2$s_done:
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
            %2$s_throw:
                la   a0, .Lparse_msg
                li   a1, 14
                call kof_string_from_literal
                call kof_throw_string
            """;

    static final String RISCV_RUNTIME_ASM_B_30 = """
            .section .text

            # kof_string_to_int(str) -> Int (contrato Integer.parseInt; bug 79)
            """ + String.format(TEMPLATE, "kof_string_to_int", ".Lsti", "-2147483648", "-2147483647", "-214748364") + """

            # kof_string_to_long(str) -> Long (contrato Long.parseLong; bug 79 —
            # o runtime riscv NÃO tinha to_long: link quebrava)
            """ + String.format(TEMPLATE, "kof_string_to_long", ".Lstl",
                "-9223372036854775808", "-9223372036854775807", "-922337203685477580") + """
            .section .data
            .Lparse_msg: .asciz "Invalid number"
            """;
}
