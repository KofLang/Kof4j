package dev.kof.compiler;

/**
 * FASE 3 (REFACTOR-500): runtime assembly riscv64 do NativeBackend.
 * Os 4 blocos originais (RISCV_RUNTIME_ASM/_STRN002_/_B/_MAPSET_) eram
 * ~4700 linhas numa só classe; aqui são fatiados em NativeRiscvAsm*
 * (≤500) e remontados por concatenação — valor byte-idêntico ao original
 * (prova: diff do .s gerado nos 3 targets).
 */
final class NativeRiscvAsm {

    private NativeRiscvAsm() {}

    static final String RISCV_RUNTIME_ASM = NativeRiscvAsmRt0.RISCV_RUNTIME_ASM_0 + NativeRiscvAsmRt1.RISCV_RUNTIME_ASM_1;
    static final String RISCV_STRN002_ASM = NativeRiscvAsmStrn0.RISCV_STRN002_ASM_0 + NativeRiscvAsmStrn1.RISCV_STRN002_ASM_1;
    static final String RISCV_RUNTIME_ASM_B = NativeRiscvAsmRtB0.RISCV_RUNTIME_ASM_B_0 + NativeRiscvAsmRtB1.RISCV_RUNTIME_ASM_B_1 + NativeRiscvAsmRtB2.RISCV_RUNTIME_ASM_B_2 + NativeRiscvAsmRtB3.RISCV_RUNTIME_ASM_B_3 + NativeRiscvAsmRtB4.RISCV_RUNTIME_ASM_B_4;
    static final String RISCV_MAPSET_ASM = NativeRiscvAsmMapset0.RISCV_MAPSET_ASM_0 + NativeRiscvAsmMapset1.RISCV_MAPSET_ASM_1 + NativeRiscvAsmMapset2.RISCV_MAPSET_ASM_2;
}
