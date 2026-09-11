package dev.kof.compiler.nat;

// STDLIB S12b: fatia 29 de RISCV_RUNTIME_ASM_B — kof.validation formatCnpj.
// kof_validation_formatCnpj(a0=str) -> String: 14 dígitos => NN.NNN.NNN/NNNN-NN
// (18 chars, IBGE canônico único); senão (incl. null) => original (no-op,
// nunca lança — face leniente da lane, paridade formatCpf/formatCep S12).
// Reusa kof_br_digits_rv (B12, só t-regs) — buf do chamador em sp+0..15.
//
// LIÇÕES travadas (S12/S3b-ext): frame SEMPRE múltiplo de 16 (-48 aqui: ra+
// s0+s1+s2 = 4*8=32 -> round up 48; store ra em offset 40 p/ PS 16-align);
// len da String só em offset 16 (Int32) — 20=0 (upper bits; len@20 = bug);
// call kof_alloc clobbers t* e exige a0 = tamanho. aarch64 herda linha-a-
// linha (lw/sd/sb/lbu/li/addi/call/j/b* todos traduzidos; imediais <=2047).
public final class NativeRiscvAsmRtB29 {

    static final String RISCV_RUNTIME_ASM_B_29 = """

            .section .text
            # kof_validation_formatCnpj(a0=str) -> String (S12b)
            # 14 dígitos => NN.NNN.NNN/NNNN-NN; senão original (no-op).
            # buf em sp+0..15; s0 = str original; s1 = novo obj; t0 = dígito.
            .globl kof_validation_formatCnpj
            kof_validation_formatCnpj:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                addi a1, sp, 0
                call kof_br_digits_rv        # a0 = count
                li   t1, 14
                bne  a0, t1, .Lv_br_fcnpj_orig
                li   a0, 48                  # 18+25 = 43 -> 48 (16-align)
                call kof_alloc
                mv   s1, a0
                li   t0, 1
                sw   t0, 0(s1)
                li   t0, 0
                sw   t0, 4(s1)
                sd   t0, 8(s1)
                sw   t0, 20(s1)              # 20=0: upper bits do len (len@16)
                li   t0, 18
                sw   t0, 16(s1)
                li   t1, 48                  # '0'
                # NN.  d0,d1,'.'
                lbu  t0, 0(sp)
                add  t0, t0, t1
                sb   t0, 24(s1)
                lbu  t0, 1(sp)
                add  t0, t0, t1
                sb   t0, 25(s1)
                li   t0, 46
                sb   t0, 26(s1)
                # NNN.  d2..d4,'.'
                lbu  t0, 2(sp)
                add  t0, t0, t1
                sb   t0, 27(s1)
                lbu  t0, 3(sp)
                add  t0, t0, t1
                sb   t0, 28(s1)
                lbu  t0, 4(sp)
                add  t0, t0, t1
                sb   t0, 29(s1)
                li   t0, 46
                sb   t0, 30(s1)
                # NNN/  d5..d7,'/'
                lbu  t0, 5(sp)
                add  t0, t0, t1
                sb   t0, 31(s1)
                lbu  t0, 6(sp)
                add  t0, t0, t1
                sb   t0, 32(s1)
                lbu  t0, 7(sp)
                add  t0, t0, t1
                sb   t0, 33(s1)
                li   t0, 47
                sb   t0, 34(s1)
                # NNNN-  d8..d11,'-'
                lbu  t0, 8(sp)
                add  t0, t0, t1
                sb   t0, 35(s1)
                lbu  t0, 9(sp)
                add  t0, t0, t1
                sb   t0, 36(s1)
                lbu  t0, 10(sp)
                add  t0, t0, t1
                sb   t0, 37(s1)
                lbu  t0, 11(sp)
                add  t0, t0, t1
                sb   t0, 38(s1)
                li   t0, 45
                sb   t0, 39(s1)
                # NN + NUL  d12,d13,0
                lbu  t0, 12(sp)
                add  t0, t0, t1
                sb   t0, 40(s1)
                lbu  t0, 13(sp)
                add  t0, t0, t1
                sb   t0, 41(s1)
                li   t0, 0
                sb   t0, 42(s1)
                mv   a0, s1
                j    .Lv_br_fcnpj_ret
            .Lv_br_fcnpj_orig:
                mv   a0, s0
            .Lv_br_fcnpj_ret:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                addi sp, sp, 48
                ret
            """;
}
