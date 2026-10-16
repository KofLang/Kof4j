package dev.kof.cli;

import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeDecoder 626→<600): a avaliação
 * de uma sequência de instruções numa pilha simbólica (evalInto) movida
 * verbatim; os helpers de conv/bin/slot ficam em BytecodeDecoder.
 */
final class BytecodeEval {

    private BytecodeEval() {}

static boolean evalInto(java.util.List<BytecodeReader.Insn> seq, BytecodeFrame frame,
                        String[] cp, java.util.Deque<String> st) {
    // Fase E (14/10): memória de LOCAIS da própria fatia (o binding
    // `instanceof String s` compila para checkcast+astore+aload — a
    // avaliação straight-line é determinística, o mapa é local à chamada).
    // Só referências/ints (lstore/dstore ocupam 2 slots — fora, honesto).
    java.util.Map<Integer, String> locals = new java.util.HashMap<>();
    for (BytecodeReader.Insn in : seq) {
        int op = in.opcode();
        switch (op) {
            case 0x3a, 0x4b, 0x4c, 0x4d, 0x4e -> {      // astore (binding; astore_0=0x4b)
                if (st.isEmpty()) return false;
                int slot = op == 0x3a ? in.operands()[0] : op - 0x4b;
                locals.put(slot, st.pop());
            }
            case 0x36, 0x3b, 0x3c, 0x3d, 0x3e -> {      // istore (istore_0=0x3b)
                if (st.isEmpty()) return false;
                int slot = op == 0x36 ? in.operands()[0] : op - 0x3b;
                locals.put(slot, st.pop());
            }
            case 0x01 -> st.push("null");
            case 0x02 -> st.push("-1");
            case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> st.push(String.valueOf(op - 0x03));
            case 0x09 -> st.push("0L");
            case 0x0a -> st.push("1L");
            case 0x0e -> st.push("0.0");
            case 0x0f -> st.push("1.0");
            case 0x10 -> st.push(String.valueOf((byte) in.operands()[0]));
            case 0x11 -> st.push(String.valueOf((short) in.operands()[0]));
            case 0x12, 0x13 -> {
                String c = BytecodeCp.ldc(cp, in.operands()[0]);
                if (c == null) return false;
                st.push(c);
            }
            case 0x14 -> {
                String c = BytecodeCp.ldc2(cp, in.operands()[0]);
                if (c == null) return false;
                st.push(c);
            }
            case 0x1a, 0x1b, 0x1c, 0x1d -> {
                int slot = op - 0x1a;
                st.push(locals.containsKey(slot) ? locals.get(slot) : BytecodeDecoderStack.slotName(slot, frame));
            }
            case 0x1e, 0x1f, 0x20, 0x21 -> st.push(BytecodeDecoderStack.slotName(op - 0x1e, frame));
            case 0x26, 0x27, 0x28, 0x29 -> st.push(BytecodeDecoderStack.slotName(op - 0x26, frame));
            case 0x2a, 0x2b, 0x2c, 0x2d -> {
                int slot = op - 0x2a;
                st.push(locals.containsKey(slot) ? locals.get(slot) : BytecodeDecoderStack.slotName(slot, frame));
            }
            case 0x15, 0x19 -> st.push(BytecodeDecoderStack.slotName(in.operands()[0], frame));
            case 0x16, 0x17, 0x18 -> st.push(BytecodeDecoderStack.slotName(in.operands()[0], frame));
            case 0xbe -> {                                 // arraylength
                if (st.isEmpty()) return false;
                st.push(st.pop() + ".length");
            }
            // binárias (+ %): ABERTAS desde 14/10 — o contFor do join do
            // `continue` emite `if (cond) { } else { body }` + o
            // incremento comum, e o ZERO-DRIFT foi provado contra o
            // ORÁCULO JAVA REAL (n=0..20 idênticos). A lista de oráculo
            // antiga da face de guarda (`0 0 1 3 3 7 12`) estava
            // matematicamente errada para esse corpo — foi ela, não o
            // join, que fechava esta porta.
            case 0x60, 0x61, 0x62, 0x63 -> { if (!BytecodeDecoderStack.bin2(st, "+")) return false; }
            case 0x64, 0x65, 0x66, 0x67 -> { if (!BytecodeDecoderStack.bin2(st, "-")) return false; }
            case 0x68, 0x69, 0x6a, 0x6b -> { if (!BytecodeDecoderStack.bin2(st, "*")) return false; }
            case 0x6c, 0x6d, 0x6e, 0x6f -> { if (!BytecodeDecoderStack.bin2(st, "/")) return false; }
            case 0x70, 0x71, 0x72, 0x73 -> { if (!BytecodeDecoderStack.bin2(st, "%")) return false; }
            case 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0x90 -> {
                if (!BytecodeDecoderStack.conv(st, op)) return false;
            }
            // Fase E (14/10): bitwise & | ^ (o Kof tem os tres; medido)
            case 0x7e, 0x7f -> { if (!BytecodeDecoderStack.bin2(st, "&")) return false; }
            case 0x80, 0x81 -> { if (!BytecodeDecoderStack.bin2(st, "|")) return false; }
            case 0x82, 0x83 -> { if (!BytecodeDecoderStack.bin2(st, "^")) return false; }
            case 0x78, 0x79 -> { if (!BytecodeDecoderStack.bin2(st, "<<")) return false; }
            case 0x7a, 0x7b -> { if (!BytecodeDecoderStack.bin2(st, ">>")) return false; }
            case 0x7c, 0x7d -> { if (!BytecodeDecoderStack.bin2(st, ">>>")) return false; }
            // Fase C (13/10): CHAMADAS em condição (`s.isEmpty()`) — o
            // MESMO gate R6 do emitLinear (JDK-owner recusa; método em
            // objeto novo recusa; void em condição não existe).
            case 0xb6, 0xb9 -> {                           // invokevirtual/interface
                String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (m == null) return false;
                    // Fase E (14/10): o overload INT de indexOf/lastIndexOf não
                    // tem equivalente Kof (String-param; SEM051 medida)
                    if ("java/lang/String".equals(m[0])
                            && ((("indexOf".equals(m[1]) || "lastIndexOf".equals(m[1]))
                            && !m[2].startsWith("(Ljava/lang/String;"))
                            || "strip".equals(m[1]) || "stripLeading".equals(m[1]) || "stripTrailing".equals(m[1]))) return false;
                String a = BytecodeCp.callArgs(st, BytecodeCp.argCount(m[2]));
                if (a == null || st.isEmpty()) return false;
                String recv = st.pop();
                String mapped = BytecodeStdlib.virtual(recv, m[0], m[1], a);
                String NEW = new String(new char[]{'\u27e6', 'n', 'e', 'w', '\u27e7'});
                if (mapped == null && recv.startsWith(NEW)) return false;
                st.push(mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")");
            }
            case 0xb8 -> {                                 // invokestatic
                String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (m == null) return false;
                String a = BytecodeCp.callArgs(st, BytecodeCp.argCount(m[2]));
                if (a == null) return false;
                String mapped = BytecodeStdlib.statics(m[0], m[1], a, m[2]);
                if (mapped == null && BytecodeCp.isJdkOwner(m[0])) return false;
                st.push(mapped != null ? mapped : BytecodeCp.simpleOwner(m[0]) + "." + m[1] + "(" + a + ")");
            }
            case 0xb2 -> {                                 // getstatic
                String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (f == null) return false;
                st.push(BytecodeCp.simpleOwner(f[0]) + "." + f[1]);
            }
            case 0xb4 -> {                                 // getfield
                String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (f == null || st.isEmpty()) return false;
                String obj = st.pop();
                st.push(obj + "." + f[1]);
            }
            case 0xba -> {   // invokedynamic — LAMBDA (o texto do pré-pass)
                int idynIdx = in.operands()[0];
                String idyn = idynIdx < cp.length ? cp[idynIdx] : null;
                if (idyn == null || !idyn.startsWith("LAMBDA:")
                        || !BytecodeConcat.LAMBDA_TEXT.containsKey(idynIdx)) return false;
                st.push(BytecodeConcat.LAMBDA_TEXT.get(idynIdx));
            }
            case 0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35 -> {   // xaload
                // Fase E (14/10): carga de array na CONDIÇÃO (medido no
                // maxOf: `if (xs[i] > m)` dentro do loop stubava)
                if (st.size() < 2) return false;
                String idx = st.pop();
                String arr = st.pop();
                st.push(arr + "[" + idx + "]");
            }
            case 0xc1 -> {                                 // instanceof (Kof nativo)
                String ct = BytecodeKofTypes.inlineKofType(cp, in.operands()[0]);
                if (ct == null) {
                    ct = BytecodeKofTypes.indexKofType(cp, in.operands()[0], frame);
                    if (ct == null) return false;
                }
                if (st.isEmpty()) return false;
                st.push("(" + st.pop() + " instanceof " + ct + ")");
            }
            case 0xc0 -> {                                 // checkcast
                String ct = BytecodeKofTypes.inlineKofType(cp, in.operands()[0]);
                if (ct == null) {
                    ct = BytecodeKofTypes.indexKofType(cp, in.operands()[0], frame);
                    if (ct == null) return false;
                }
                if (st.isEmpty()) return false;
                String v = st.pop();
                st.push(ct.equals("Object") ? v : "(" + v + " as " + ct + ")");
            }
            default -> { return false; }
        }
    }
    return true;
}
}
