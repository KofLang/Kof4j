package dev.kof.compiler.nat;

/**
 * FASE 3 (REFACTOR-500): runtime assembly riscv64 do NativeBackend.
 * Os 4 blocos originais (RISCV_RUNTIME_ASM/_STRN002_/_B/_MAPSET_) eram
 * ~4700 linhas numa só classe; aqui são fatiados em NativeRiscvAsm*
 * (≤500) e remontados por concatenação — valor byte-idêntico ao original
 * (prova: diff do .s gerado nos 3 targets).
 */
public final class NativeRiscvAsm {

    private NativeRiscvAsm() {}

    static final String RISCV_RUNTIME_ASM = NativeRiscvAsmRt0.RISCV_RUNTIME_ASM_0 + NativeRiscvAsmRt1.RISCV_RUNTIME_ASM_1;
    static final String RISCV_STRN002_ASM = NativeRiscvAsmStrn0.RISCV_STRN002_ASM_0 + NativeRiscvAsmStrn1.RISCV_STRN002_ASM_1;
    // A cadeia B_0..B_9 ultrapassa o limite de 64KB de string-constante do pool
    // quando dobrada em compile-time (javac "constant string too long" no uso).
    // Concatenar via StringBuilder = mesmo bytes, calculado no <clinit>.
    static final String RISCV_RUNTIME_ASM_B = runtimeB();
    private static String runtimeB() {
        return new StringBuilder()
                .append(NativeRiscvAsmRtB0.RISCV_RUNTIME_ASM_B_0)
                .append(NativeRiscvAsmRtB1.RISCV_RUNTIME_ASM_B_1)
                .append(NativeRiscvAsmRtB2.RISCV_RUNTIME_ASM_B_2)
                .append(NativeRiscvAsmRtB3.RISCV_RUNTIME_ASM_B_3)
                .append(NativeRiscvAsmRtB4.RISCV_RUNTIME_ASM_B_4)
                .append(NativeRiscvAsmRtB5.RISCV_RUNTIME_ASM_B_5)
                .append(NativeRiscvAsmRtB6.RISCV_RUNTIME_ASM_B_6)
                .append(NativeRiscvAsmRtB7.RISCV_RUNTIME_ASM_B_7)
                .append(NativeRiscvAsmRtB8.RISCV_RUNTIME_ASM_B_8)
                .append(NativeRiscvAsmRtB9.RISCV_RUNTIME_ASM_B_9)
                .append(NativeRiscvAsmRtB10.RISCV_RUNTIME_ASM_B_10)
                .append(NativeRiscvAsmRtB11.RISCV_RUNTIME_ASM_B_11)
                .append(NativeRiscvAsmRtB12.RISCV_RUNTIME_ASM_B_12)
                .append(NativeRiscvAsmRtB13.RISCV_RUNTIME_ASM_B_13)
                .append(NativeRiscvAsmRtB14.RISCV_RUNTIME_ASM_B_14)
                .append(NativeRiscvAsmRtB15.RISCV_RUNTIME_ASM_B_15)
                .append(NativeRiscvAsmRtB16.RISCV_RUNTIME_ASM_B_16)
                .toString();
    }
    static final String RISCV_MAPSET_ASM = NativeRiscvAsmMapset0.RISCV_MAPSET_ASM_0 + NativeRiscvAsmMapset1.RISCV_MAPSET_ASM_1 + NativeRiscvAsmMapset2.RISCV_MAPSET_ASM_2;
}
