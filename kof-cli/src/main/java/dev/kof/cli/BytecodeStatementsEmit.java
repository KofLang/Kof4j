package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeStatements 616→<600): a emissão
 * LINEAR das statements recuperadas (emitLinear + assign) separada da fase de
 * recuperação. Zero mudança de comportamento.
 */
final class BytecodeStatementsEmit {

    private BytecodeStatementsEmit() {}

    // espelha o refuse de BytecodeStatements: o sink é estático e compartilhado
    private static void refuse(String reason) {
        if (BytecodeStatements.refusalSink != null)
            BytecodeStatements.refusalSink.merge(reason, 1, Integer::sum);
    }

    static List<String> emitLinear(List<BytecodeReader.Insn> seq,
                                           String[] cp, BytecodeFrame frame,
                                           Set<Integer> declared) {
        List<String> stmts = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        for (BytecodeReader.Insn in : seq) {
            int op = in.opcode();
            String opcodeTag = "emitLinear-null:0x" + String.format("%02x", op);
            switch (op) {
                case 0x01 -> stack.push("null");
                case 0x02 -> stack.push("-1");
                case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> stack.push(String.valueOf(op - 0x03));
                // lconst/dconst carregam o TIPO no próprio opcode (lição do
                // bug 62: só emitir quando o tipo não pode driftar). Kof tem
                // literal Long (42L) e Double (2.25) — fconst fica recusado.
                case 0x09 -> stack.push("0L");
                case 0x0a -> stack.push("1L");
                case 0x0b, 0x0c, 0x0d -> { { refuse(opcodeTag); return null; } }  // fconst: sem literal float em Kof
                case 0x0e -> stack.push("0.0");
                case 0x0f -> stack.push("1.0");
                case 0x10 -> stack.push(String.valueOf((byte) in.operands()[0]));
                case 0x11 -> stack.push(String.valueOf((short) in.operands()[0]));
                case 0x12, 0x13 -> {                            // ldc, ldc_w
                    String c = BytecodeCp.ldc(cp, in.operands()[0]);
                    if (c == null) { refuse(opcodeTag); return null; }
                    stack.push(c);
                }
                case 0x14 -> {
                    String c = BytecodeCp.ldc2(cp, in.operands()[0]);
                    if (c == null) { refuse(opcodeTag); return null; }
                    stack.push(c);
                }
                case 0xba -> { // invokedynamic — String concat OU lambda
                    int idynIdx = in.operands()[0];
                    String idyn = idynIdx < cp.length ? cp[idynIdx] : null;
                    if (idyn != null && idyn.startsWith("LAMBDA:")
                            && BytecodeConcat.LAMBDA_TEXT.containsKey(idynIdx)) {
                        stack.push(BytecodeConcat.LAMBDA_TEXT.get(idynIdx));
                    } else {
                    String rec = BytecodeConcat.recipe(cp, in.operands()[0]);
                    if (rec == null) { refuse(opcodeTag); return null; }
                    String expr = BytecodeConcat.apply(stack, rec);
                    if (expr == null) { refuse(opcodeTag); return null; }
                    stack.push(expr);
                    }
                }
                case 0x1a, 0x1b, 0x1c, 0x1d -> stack.push(BytecodeDecoderStack.slotName(op - 0x1a, frame));
                case 0x1e, 0x1f, 0x20, 0x21 -> stack.push(BytecodeDecoderStack.slotName(op - 0x1e, frame));   // lload_0..3
                case 0x26, 0x27, 0x28, 0x29 -> stack.push(BytecodeDecoderStack.slotName(op - 0x26, frame));   // dload_0..3
                case 0x2a, 0x2b, 0x2c, 0x2d -> stack.push(BytecodeDecoderStack.slotName(op - 0x2a, frame));
                case 0x15, 0x19 -> stack.push(BytecodeDecoderStack.slotName(in.operands()[0], frame));
                case 0x16, 0x17, 0x18 -> stack.push(BytecodeDecoderStack.slotName(in.operands()[0], frame));   // lload/fload/dload
                case 0x36, 0x37, 0x38, 0x39, 0x3a -> { // istore..astore idx
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    if (val.equals("null")) { refuse(opcodeTag); return null; }   // SEM048 congelada
                    int slot = in.operands()[0];
                    // Fase E (14/10): o slot de um StringBuilder vira STRING
                    // (o idioma Kof é concat; StringBuilder não existe —
                    // SEM015). `var name = ""`.
                    if (val.startsWith("⟦sb⟧")) {
                        BytecodeStatements.builderSlots.add(slot);
                        String name = BytecodeDecoderStack.slotName(slot, frame);
                        declared.add(slot);
                        stmts.add("var " + name + " = \"\"");
                        continue;
                    }
                    stmts.add(assign(slot, val, frame, declared));
                }
                case 0x3b, 0x3c, 0x3d, 0x3e -> { // istore_0..3
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    if (val.equals("null")) { refuse(opcodeTag); return null; }   // SEM048 congelada
                    stmts.add(assign(op - 0x3b, val, frame, declared));
                }
                case 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a -> {
                    // lstore_0..3 / fstore_0..3 / dstore_0..3: 4 opcodes POR tipo
                    // → slot = (op - 0x3f) % 4 (dstore_1 = 0x48 → slot 1, não 9)
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    if (val.equals("null")) { refuse(opcodeTag); return null; }   // SEM048 congelada
                    stmts.add(assign((op - 0x3f) % 4, val, frame, declared));
                }
                case 0x4b, 0x4c, 0x4d, 0x4e -> { // astore_0..3
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    if (val.equals("null")) { refuse(opcodeTag); return null; }   // SEM048 congelada
                    int slot2 = op - 0x4b;
                    if (val.startsWith("⟦sb⟧")) {
                        BytecodeStatements.builderSlots.add(slot2);
                        String name2 = BytecodeDecoderStack.slotName(slot2, frame);
                        declared.add(slot2);
                        stmts.add("var " + name2 + " = \"\"");
                        continue;
                    }
                    stmts.add(assign(slot2, val, frame, declared));
                }
                case 0x84 -> { // iinc: byte1=index, byte2=const (signed)
                    int w = in.operands()[0];
                    int slot = (w >> 8) & 0xFF;
                    int k = (byte) (w & 0xFF);
                    String name = BytecodeDecoderStack.slotName(slot, frame);
                    declared.add(slot);
                    stmts.add(name + " = " + name + " + " + k);
                }
                // Fase C (13/10): aritmetica INT/LONG/FLOAT/DOUBLE — as
                // variantes long/float/double (0x61-0x63/0x65-0x67/0x69-0x6b/
                // 0x6d-0x6f/0x71-0x73) faltavam: QUALQUER corpo multi-statement
                // com math long/double stubava. O bin e type-agnostico (os
                // operandos carregam o valor).
                case 0x60, 0x61, 0x62, 0x63 -> { if (!BytecodeDecoderStack.bin(stack, "+")) { refuse(opcodeTag); return null; } }
                case 0x64, 0x65, 0x66, 0x67 -> { if (!BytecodeDecoderStack.bin(stack, "-")) { refuse(opcodeTag); return null; } }
                case 0x68, 0x69, 0x6a, 0x6b -> { if (!BytecodeDecoderStack.bin(stack, "*")) { refuse(opcodeTag); return null; } }
                case 0x6c, 0x6d, 0x6e, 0x6f -> { if (!BytecodeDecoderStack.bin(stack, "/")) { refuse(opcodeTag); return null; } }
                case 0x70, 0x71, 0x72, 0x73 -> { if (!BytecodeDecoderStack.bin(stack, "%")) { refuse(opcodeTag); return null; } }
                // Fase C (13/10): shifts (0x78-0x7d) — Int/Long << >> >>> (a
                // semantica por-target e do COMPILADOR, corrigida no §698)
                // Fase C (13/10): CONVERSOES primitivas (0x85-0x90) — Int/Long/
                // Float/Double via .toInt()/.toLong()/.toFloat()/.toDouble()
                // Fase E (22/10): o NARROWING 0x91-0x93 (i2b/i2c/i2s) — o
                // §695/#471 deu ao Kof `as Byte`/`as Short`/`as Char` a
                // semantica fiel do Java (o JVM emite i2b/i2s/i2c reais; o
                // x86 e o riscv idem). O comentario antigo de "sem
                // equivalente" descrevia o codigo PRE-§695 — a recusa
                // reprovava o metodo inteiro (medido: o t1 do Micro6, o
                // shiftChar do Cipher: `int pos = c - 'a';
                // return (char) ('a' + pos);` = emitLinear-null:0x92).
                case 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0x90,
                     0x91, 0x92, 0x93 -> {
                    if (!BytecodeDecoderStack.conv(stack, op)) { refuse(opcodeTag); return null; }
                }
                // Fase E (14/10): BITWISE (0x7e-0x83) — & | ^ p/ Int/Long
                // (medido: o Kof tem os tres; o ~ e iconst_m1+ixor no javac)
                case 0x7e, 0x7f -> { if (!BytecodeDecoderStack.bin(stack, "&")) { refuse(opcodeTag); return null; } }
                case 0x80, 0x81 -> { if (!BytecodeDecoderStack.bin(stack, "|")) { refuse(opcodeTag); return null; } }
                case 0x82, 0x83 -> { if (!BytecodeDecoderStack.bin(stack, "^")) { refuse(opcodeTag); return null; } }
                case 0x78, 0x79 -> { if (!BytecodeDecoderStack.bin(stack, "<<")) { refuse(opcodeTag); return null; } }
                case 0x7a, 0x7b -> { if (!BytecodeDecoderStack.bin(stack, ">>")) { refuse(opcodeTag); return null; } }
                case 0x7c, 0x7d -> { if (!BytecodeDecoderStack.bin(stack, ">>>")) { refuse(opcodeTag); return null; } }
                case 0x74 -> { if (stack.isEmpty()) { refuse(opcodeTag); return null; } stack.push("-" + stack.pop()); }
                // Fase E: construção/teste-emit (0xbb new, 0xbd anewarray,
                // 0x59 dup, 0xb7 init, 0x57 pop, 0xc0 cast, 0xc1 instanceof)
                // — semântica em BytecodeKofTypes (gate ≤500); false = stub.
                case 0x57, 0xc0, 0xc1, 0xbb, 0xbc, 0xbd, 0xc5, 0x59, 0xb7,
                     0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35,
                     0x4f, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0xbe -> {
                    if (!BytecodeKofTypes.statementOp(op, in, stack, cp, stmts, frame)) { refuse(opcodeTag); return null; }
                }
                case 0xb8 -> { // invokestatic
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) { refuse(opcodeTag); return null; }
                    String a = BytecodeCp.callArgs(stack, BytecodeCp.argCount(m[2]));
                    if (a == null) { refuse(opcodeTag); return null; }
                    String mapped = BytecodeStdlib.statics(m[0], m[1], a, m[2]);
                    if (mapped == null && BytecodeCp.isJdkOwner(m[0])) { refuse(opcodeTag); return null; }   // R6: owner JDK
                    String call = mapped != null ? mapped : BytecodeCp.simpleOwner(m[0]) + "." + m[1] + "(" + a + ")";
                    if (BytecodeCp.isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb6, 0xb9 -> { // invokevirtual / invokeinterface
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) { refuse(opcodeTag); return null; }
                    // Fase E (14/10): StringBuilder.append/toString — o idioma
                    // Kof é concat (SEM015): `sb = sb + x` / o próprio sb.
                    if ("java/lang/StringBuilder".equals(m[0]) && !BytecodeStatements.builderSlots.isEmpty()) {
                        if ("toString".equals(m[1]) && BytecodeCp.argCount(m[2]) == 0) {
                            String recvT = stack.pop();
                            stack.push(recvT);
                            continue;
                        }
                        if ("append".equals(m[1]) && BytecodeCp.argCount(m[2]) == 1) {
                            String aB = BytecodeCp.callArgs(stack, 1);
                            if (aB == null || stack.isEmpty()) { refuse(opcodeTag); return null; }
                            String recvB = stack.pop();
                            stmts.add(recvB + " = " + recvB + " + " + aB);
                            stack.push("⟦sb⟧");
                            continue;
                        }
                    }
                    // Fase E (14/10): o overload INT de indexOf/lastIndexOf não
                    // tem equivalente Kof (String-param; SEM051 medida)
                    if ("java/lang/String".equals(m[0])
                            && ((("indexOf".equals(m[1]) || "lastIndexOf".equals(m[1]))
                            && !m[2].startsWith("(Ljava/lang/String;"))
                            || "strip".equals(m[1]) || "stripLeading".equals(m[1]) || "stripTrailing".equals(m[1]))) { refuse(opcodeTag); return null; }
                    String a = BytecodeCp.callArgs(stack, BytecodeCp.argCount(m[2]));
                    if (a == null || stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String recv = stack.pop();
                    String mapped = BytecodeStdlib.virtual(recv, m[0], m[1], a);
                    if (mapped == null && recv.startsWith("⟦new⟧")) { refuse(opcodeTag); return null; }  // R6: método em novo Objeto JDK
                    String call = mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")";
                    if (BytecodeCp.isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb4 -> { // getfield
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String obj = stack.pop();
                    stack.push(obj + "." + f[1]);
                }
                case 0xb2 -> { // getstatic
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null) { refuse(opcodeTag); return null; }
                    stack.push(BytecodeCp.simpleOwner(f[0]) + "." + f[1]);
                }
                case 0xb5 -> { // putfield
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String obj = stack.pop();
                    stmts.add(obj + "." + f[1] + " = " + val);
                }
                case 0xb3 -> { // putstatic
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    stmts.add(BytecodeCp.simpleOwner(f[0]) + "." + f[1] + " = " + val);
                }
                case 0xac, 0xad, 0xae, 0xaf -> { if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String rv = stack.pop();
                    // SEM048 congelada: o literal null é BANIDO (o mantenedor:
                    // "total ban of the literal, not even to T?") — `return
                    // null` não compila → recusa honesta (R6)
                    if (rv.equals("null")) { refuse(opcodeTag); return null; }
                    // coerção Bool (SEM010, medido no found/anyNeg): método `Z`
                    // com retorno iconst 1/0 → true/false (espelho do ternário)
                    if ("Z".equals(frame.retType())) {
                        if ("1".equals(rv)) rv = "true";
                        if ("0".equals(rv)) rv = "false";
                    }
                    stmts.add("return " + rv); return stmts; }
                case 0xb0 -> { if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String rv2 = stack.pop();
                    if (rv2.equals("null")) { refuse(opcodeTag); return null; }   // SEM048 congelada
                    if ("Z".equals(frame.retType())) {
                        if ("1".equals(rv2)) rv2 = "true";
                        if ("0".equals(rv2)) rv2 = "false";
                    }
                    stmts.add("return " + rv2); return stmts; }
                case 0xb1 -> { stmts.add("return"); return stmts; }
                case 0xbf -> { // athrow → `throw x` (o Kof lança a exceção/String)
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    stmts.add("throw " + stack.pop());
                    return stmts;
                }
                // terminadores de bloco: branch/goto — paramos sem emitir
                case 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xc6, 0xc7 -> { return stmts; }
                default -> {
                    if (BytecodeDecoder.blockerSink != null) BytecodeDecoder.blockerSink.accept(op);
                    { refuse(opcodeTag); return null; }
                }
            }
        }
        return stmts;
    }

    private static String assign(int slot, String value, BytecodeFrame frame, Set<Integer> declared) {
        String name = BytecodeDecoderStack.slotName(slot, frame);
        // Fase E (14/10): slot NOMEADO (parâmetro/this) NUNCA redeclara — o
        // `var arg1 = ...` criava local de BLOCO e o parâmetro nunca
        // atualizava (medido: o gcd decompilado rodava para sempre, R6).
        if (!frame.isNamedSlot(slot) && !declared.contains(slot)) {
            declared.add(slot);
            return "var " + name + " = " + value;
        }
        return name + " = " + value;
}
}
