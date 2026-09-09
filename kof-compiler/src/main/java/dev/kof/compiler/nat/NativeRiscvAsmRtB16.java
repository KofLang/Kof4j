package dev.kof.compiler.nat;

// FASE 3 (STDLIB S6a): fatia 16 de RISCV_RUNTIME_ASM_B — kof.validation
// rede: isIpv4 (dotted-quad, sem zero à esquerda, octeto<=255), isMac
// (6 hex, separador ':' OU '-' consistente, len==17), isPort (1..65535).
// Espelha exatamente RuntimeValidationNet (x86) / JVM / JS — paridade
// byte-a-byte (vetores Python-derivados; x86 confirmado == JVM em 16/16).
//
// Classificação de byte usa sltu (UNSIGNED) — bytes 0..255 sempre.
// isMac usa mulhu+srlv (reciprocais, sem div) para (i+1)%3.
// isIpv4 chama kof_time_daysInMonth? NÃO — sem call aqui (só scan).
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB16 {

    static final String RISCV_RUNTIME_ASM_B_16 = """

            .section .text
            # kof_validation_isIpv4(a0=str) -> a0 0/1
            .globl kof_validation_isIpv4
            kof_validation_isIpv4:
                beqz a0, .Lv_n4_f
                lw   t4, 16(a0)          # len
                li   t3, 0               # i
                li   t1, 0               # octets
                li   t2, 0               # val
                li   t5, 0               # digits
            .Lv_n4_loop:
                bgt  t3, t4, .Lv_n4_f
                li   t0, 46              # c = '.'  (virtual no fim)
                bge  t3, t4, .Lv_n4_body
                add  a1, a0, t3
                lbu  t0, 24(a1)
            .Lv_n4_body:
                li   a1, 57
                bgt  t0, a1, .Lv_n4_maybe
                li   a1, 48
                blt  t0, a1, .Lv_n4_maybe
                j    .Lv_n4_digit
            .Lv_n4_maybe:
                li   a1, 46
                beq  t0, a1, .Lv_n4_dot
                j    .Lv_n4_f
            .Lv_n4_digit:
                # leading zero: digits==0 && c=='0' && i+1<len && s[i+1]!='.'
                bnez t5, .Lv_n4_val
                li   a1, 48
                bne  t0, a1, .Lv_n4_val
                addi a1, t3, 1
                bge  a1, t4, .Lv_n4_val
                add  a2, a0, a1
                lbu  a2, 24(a2)
                li   a1, 46
                beq  a2, a1, .Lv_n4_val
                j    .Lv_n4_f
            .Lv_n4_val:
                li   a1, 10
                mul  t2, t2, a1
                addi t0, t0, -48
                add  t2, t2, t0
                addi t5, t5, 1
                li   a1, 3
                blt  a1, t5, .Lv_n4_f    # digits > 3
                addi t3, t3, 1
                j    .Lv_n4_loop
            .Lv_n4_dot:
                beqz t5, .Lv_n4_f
                li   a1, 255
                blt  a1, t2, .Lv_n4_f
                addi t1, t1, 1
                li   t2, 0
                li   t5, 0
                bge  t3, t4, .Lv_n4_end
                addi t3, t3, 1
                j    .Lv_n4_loop
            .Lv_n4_end:
                li   a1, 4
                bne  t1, a1, .Lv_n4_f
                li   a0, 1
                ret
            .Lv_n4_f:
                li   a0, 0
                ret

            # kof_validation_isMac(a0=str) -> a0 0/1. len==17; sep ':' ou '-'.
            # Posições de separador: i em {2,5,8,11,14} — acumulador t6 (=2, +3)
            # elimina mul/div (tradutor aarch não tem mulhu).
            .globl kof_validation_isMac
            kof_validation_isMac:
                beqz a0, .Lv_mac_f
                lw   t4, 16(a0)
                li   t5, 17
                bne  t4, t5, .Lv_mac_f
                addi t1, a0, 26
                lbu  t1, 0(t1)           # sep = bytes[2]
                li   a1, 58
                beq  t1, a1, .Lv_mac_scan
                li   a1, 45
                bne  t1, a1, .Lv_mac_f
            .Lv_mac_scan:
                li   t3, 0               # i
                li   t6, 2               # próxima posição de separador
            .Lv_mac_loop:
                li   t5, 17
                bge  t3, t5, .Lv_mac_true
                add  a3, a0, t3
                lbu  a3, 24(a3)          # c
                bne  t3, t6, .Lv_mac_hex
                bne  a3, t1, .Lv_mac_f   # era separador: tem de bater c/ sep
                addi t6, t6, 3
                j    .Lv_mac_next
            .Lv_mac_hex:
                li   a1, 48
                blt  a3, a1, .Lv_mac_f
                li   a1, 57
                ble  a3, a1, .Lv_mac_next
                ori  a4, a3, 32
                li   a1, 97
                blt  a4, a1, .Lv_mac_f
                li   a1, 102
                bgt  a4, a1, .Lv_mac_f
            .Lv_mac_next:
                addi t3, t3, 1
                j    .Lv_mac_loop
            .Lv_mac_true:
                li   a0, 1
                ret
            .Lv_mac_f:
                li   a0, 0
                ret

            # kof_validation_isPort(a0=port) -> a0 0/1 (1..65535; sltu unsigned)
            .globl kof_validation_isPort
            kof_validation_isPort:
                addi a0, a0, -1          # port-1 em [0,65534] (unsigned)
                li   a1, 65535
                bltu a0, a1, .Lv_port_t  # port=0 => a0=-1=huge unsigned => false
                li   a0, 0
                ret
            .Lv_port_t:
                li   a0, 1
                ret
            """;
}
