package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeDecoder 603→<600): a família
 * de PILHA SIMBÓLICA (conv/bin2/bin/loadValue) movida verbatim.
 */
final class BytecodeDecoderStack {

    private BytecodeDecoderStack() {}

static boolean conv(java.util.Deque<String> st, int op) {
    // Fase E (22/10): o NARROWING 0x91-0x93 (i2b/i2c/i2s) — o §695/#471
    // deu ao Kof `as Byte`/`as Short`/`as Char` a semântica FIEL do Java
    // (o JVM emite i2b/i2s/i2c reais; o x86 e o riscv idem). O comentário
    // antigo de "sem equivalente Kof" descrevia o código PRE-§695. Medido:
    // 300 as Byte=44, 256 as Byte=0, -1 as Byte=-1, 70000 as Short=4464,
    // 70000 as Byte=112 — os cinco idênticos ao Java.
    if (op == 0x91 || op == 0x92 || op == 0x93) {
        if (st.isEmpty()) return false;
        String t = op == 0x91 ? "Byte" : op == 0x92 ? "Char" : "Short";
        st.push("(" + st.pop() + " as " + t + ")");
        return true;
    }
    String m = switch (op) {
        case 0x85 -> "toLong"; case 0x86 -> "toFloat"; case 0x87 -> "toDouble";
        case 0x88 -> "toInt"; case 0x89 -> "toFloat"; case 0x8a -> "toDouble";
        case 0x8b -> "toInt"; case 0x8c -> "toLong"; case 0x8d -> "toDouble";
        case 0x8e -> "toInt"; case 0x8f -> "toLong"; case 0x90 -> "toFloat";
        default -> null;
    };
    if (m == null || st.isEmpty()) return false;
    st.push(st.pop() + "." + m + "()");
    return true;
}

static boolean bin2(java.util.Deque<String> st, String op) {
    if (st.size() < 2) return false;
    String b = st.pop();
    String a = st.pop();
    st.push("(" + a + " " + op + " " + b + ")");
    return true;
}

static String blockReturnExpr(BytecodeReader.Block b, List<BytecodeReader.Insn> insns,
                                      String[] cp, BytecodeFrame frame) {
    return BytecodeDecoder.linearReturn(BytecodeDecoder.insnsWithin(b, insns), cp, frame);
}

static String slotName(int slot, BytecodeFrame frame) {
    return frame.name(slot);
}

/**
 * Fase E (15/10): o overload COM pool de constantes — o `ldc2_w` (0x14,
 * const Long/Double do CP) agora resolve na extração de condições (medido no
 * withTax: `if (sub > 200.0)` via [dload, ldc2_w, dcmpl, ifle] stubava porque
 * o loadValue de 2 args não tem o cp). Sem cp (null) o 0x14 continua null =
 * recusa honesta.
 */
static String loadValue(BytecodeReader.Insn in, BytecodeFrame frame, String[] cp) {
    if (in.opcode() == 0x14 && cp != null) {
        return BytecodeCp.ldc2(cp, in.operands()[0]);
    }
    return loadValue(in, frame);
}

static String loadValue(BytecodeReader.Insn in, BytecodeFrame frame) {
    return switch (in.opcode()) {
        case 0x1a, 0x1b, 0x1c, 0x1d -> BytecodeDecoderStack.slotName(in.opcode() - 0x1a, frame);
        case 0x1e, 0x1f, 0x20, 0x21 -> BytecodeDecoderStack.slotName(in.opcode() - 0x1e, frame);
        case 0x26, 0x27, 0x28, 0x29 -> BytecodeDecoderStack.slotName(in.opcode() - 0x26, frame);
        case 0x2a, 0x2b, 0x2c, 0x2d -> BytecodeDecoderStack.slotName(in.opcode() - 0x2a, frame);
        case 0x15, 0x19, 0x16, 0x17, 0x18 -> BytecodeDecoderStack.slotName(in.operands()[0], frame);
        case 0x03 -> "0";
        case 0x04 -> "1";
        case 0x05 -> "2";
        case 0x06 -> "3";
        case 0x07 -> "4";
        case 0x08 -> "5";
        case 0x02 -> "-1";
        case 0x01 -> "null";
        // Fase E (16/10): os consts WIDE (o lconst/fconst/dconst) — o
        // `while (left > 0)` com o Double compila o header como [dload,
        // dconst_0, dcmpl, ifle]; sem isto o par-case recusa = cond-null
        // (medido no Bank2.monthsToPay).
        case 0x09 -> "0L";
        case 0x0a -> "1L";
        case 0x0b -> "0.0f";
        case 0x0c -> "1.0f";
        case 0x0d -> "2.0f";
        case 0x0e -> "0.0";
        case 0x0f -> "1.0";
        case 0x10 -> String.valueOf((byte) in.operands()[0]);
        case 0x11 -> String.valueOf((short) in.operands()[0]);   // sipush — 14/09
        // espelha o machineRun (linha 114 ja tratava 0x11): o loadValue SEM
        // sipush fazia `if (a == 30000)` (const > bipush-range) stubar em
        // blockCondition/comparisonReturn = DIVERGENCIA de passada, nao
        // forma-nova. Seguranca: so e emitido COM o hoist §238 no lugar (sem
        // ele, o `int r` escapante viraria saida nao-compilavel — o que o
        // re-medidor Roi3 marcou como pre-requisito).
        default -> null;
    };
}

static boolean bin(Deque<String> stack, String op) {
    if (stack.size() < 2) return false;
    String b = stack.pop();
    String a = stack.pop();
    stack.push("(" + a + " " + op + " " + b + ")");
    return true;
}
}
