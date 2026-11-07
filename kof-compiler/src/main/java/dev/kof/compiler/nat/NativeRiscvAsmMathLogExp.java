package dev.kof.compiler.nat;

// MATH-LOG-EXP (07/11, lane 192.168.100.14:0): math.log/math.exp no cross —
// shims p/ a libm (log@PLT/exp@PLT), ligados POR USO (-lm em NativeCrossLink,
// que JA lista "log"/"exp" em LIBM_SYMBOLS, quando o texto PODADO contem
// `call log`/`call exp`). Peca PROPRIA (nao B32) pelo mesmo motivo do
// NativeRiscvAsmMathPow: a poda mantem libm-free quem nunca chama log/exp.
// aarch64 herda via NativeAarch64Translator.
//
// Convencao riscv: arg em a0 (bits IEEE 64), retorno em a0 (o caller faz
// pushRiscv a0). A pilha de operandos (sp) tem paridade imprevisivel (1 push =
// 8B): auto-alinha por um scratch antes do `call` — log/exp sao
// callee-saved-safe e nao tocam t0/t1/t2 (caller-saved).
public final class NativeRiscvAsmMathLogExp {

    private NativeRiscvAsmMathLogExp() {}

    static String RISCV_ASM_MATH_LOG_EXP = """

            .section .text

            # kof_math_log(a0=v) -> a0=bits de log(v) (libm, ln natural)
            .globl kof_math_log
            kof_math_log:
                fmv.d.x fa0, a0
                mv   t0, sp
                mv   t1, sp
                andi t1, t1, -16
                addi t1, t1, -16
                mv   sp, t1
                sd   ra, 8(sp)
                sd   t0, 0(sp)
                call log
                fmv.x.d a0, fa0
                ld   ra, 8(sp)
                ld   t2, 0(sp)
                mv   sp, t2
                ret

            # kof_math_exp(a0=v) -> a0=bits de exp(v) (libm, e^v)
            .globl kof_math_exp
            kof_math_exp:
                fmv.d.x fa0, a0
                mv   t0, sp
                mv   t1, sp
                andi t1, t1, -16
                addi t1, t1, -16
                mv   sp, t1
                sd   ra, 8(sp)
                sd   t0, 0(sp)
                call exp
                fmv.x.d a0, fa0
                ld   ra, 8(sp)
                ld   t2, 0(sp)
                mv   sp, t2
                ret
            """;
}
