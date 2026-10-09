package dev.kof.compiler.nat;

import dev.kof.compiler.KofComparison;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.Type;

/**
 * Salto condicional do backend cross riscv64 (extraído de
 * {@code NativeRiscvCrossOps} para manter a classe abaixo de 600 linhas,
 * REFACTOR-500). O aarch64 herda via tradução linha-a-linha do texto riscv.
 */
final class NativeRiscvCrossCondJump {

    private final NativeBackend nb;

    NativeRiscvCrossCondJump(NativeBackend nb) { this.nb = nb; }

    void emit(StringBuilder sb, KofConditionalJump kc) {
        Type opTy = kc.operandType();
        if (opTy != null && NativeTypeKinds.isFloatType(opTy)) {
            // §622: float/double NÃO podem comparar o bit pattern como inteiro
            // (não-monotônico: -1.0 > 1.0 quando lido com sinal). Reinterpreta
            // os qwords empilhados como FP (Float = low 32 bits, §142) e compara
            // na FPU; o resultado vira um booleano t2 que ramifica. NaN: a
            // comparação ordenada dá 0 (false), exceto NE (feq=0 => true),
            // idêntico ao x86 `ucomiss`+PF.
            // §627: para Float a comparação TEM de ser `.s` — `fmv.w.x` faz
            // NaN-box (preenche os 32 bits altos com 1s), então `feq.d` lia o
            // valor como um double NaN e `2.5 == 2.5` dava falso.
            sb.append("    pop t0\n");   // b (topo)
            sb.append("    pop t1\n");   // a (abaixo)
            sb.append("    fmv.w.x f0, t1\n");
            sb.append("    fmv.w.x f1, t0\n");
            emitFpCompare(sb, kc.comparison(), "s");
            sb.append("    bnez t2, ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
            sb.append("    j ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
            return;
        }
        if (opTy != null && NativeTypeKinds.isDoubleType(opTy)) {
            sb.append("    pop t0\n");   // b (topo)
            sb.append("    pop t1\n");   // a (abaixo)
            sb.append("    fmv.d.x f0, t1\n");
            sb.append("    fmv.d.x f1, t0\n");
            emitFpCompare(sb, kc.comparison(), "d");
            sb.append("    bnez t2, ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
            sb.append("    j ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
            return;
        }
        sb.append("    pop t0\n");   // b (topo)
        sb.append("    pop t1\n");   // a (abaixo)
        String cond;
        switch (kc.comparison()) {
            case EQ -> cond = "bne";
            case NE -> cond = "beq";
            case LT -> cond = "bge";
            case LE -> cond = "bgt";
            case GT -> cond = "ble";
            case GE -> cond = "blt";
            default -> cond = "b";
        }
        sb.append("    ").append(cond).append(" t1, t0, ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
        sb.append("    j ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
    }

    /**
     * §622: compara {@code f0} vs {@code f1} (já carregados) e deixa o booleano
     * do predicado em {@code t2} (1 = comparação verdadeira, 0 = falsa).
     * {@code f0}=a (esquerda), {@code f1}=b (direita). NaN => false para
     * EQ/LT/LE/GT/GE e true para NE, espelhando o x86 ({@code ucomis*}+PF).
     * {@code p} = precisão: {@code s} para Float, {@code d} para Double (§627).
     */
    private void emitFpCompare(StringBuilder sb, KofComparison cmp, String p) {
        switch (cmp) {
            case EQ -> sb.append("    feq.").append(p).append(" t2, f0, f1\n");
            case NE -> {
                sb.append("    feq.").append(p).append(" t2, f0, f1\n");
                sb.append("    xori t2, t2, 1\n");
            }
            case LT -> sb.append("    flt.").append(p).append(" t2, f0, f1\n");
            case LE -> sb.append("    fle.").append(p).append(" t2, f0, f1\n");
            case GT -> sb.append("    flt.").append(p).append(" t2, f1, f0\n");
            case GE -> sb.append("    fle.").append(p).append(" t2, f1, f0\n");
            default -> sb.append("    li t2, 0\n");
        }
    }
}
