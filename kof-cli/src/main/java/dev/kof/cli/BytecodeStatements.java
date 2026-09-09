package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Reconstrução de corpo por statements (loops/stores/multi-statement/switch/
 * try) a partir do bytecode — metade statement do decompiler (Fase C/E).
 * Compartilha os helpers de expressão com {@link BytecodeDecoder}.
 */
final class BytecodeStatements {

    private BytecodeStatements() {
    }

    static List<String> recoverStatements(byte[] code, String[] cp, BytecodeFrame frame, int[][] handlers) {
        List<BytecodeReader.Insn> insns = BytecodeReader.decode(code);
        List<String> sw = recoverSwitch(code, insns, cp, frame);
        if (sw != null) return sw;
        if (handlers != null && handlers.length > 0) {
            List<String> fin = recoverFinally(insns, cp, frame, handlers);
            if (fin != null) return fin;
            List<String> tc = tryCatch(insns, cp, frame, handlers);
            if (tc != null) return tc;
        }
        List<BytecodeReader.Block> blocks = BytecodeReader.cfg(insns, new int[0]);
        if (blocks.isEmpty()) return null;
        Map<Integer, BytecodeReader.Block> byStart = new HashMap<>();
        for (BytecodeReader.Block b : blocks) byStart.put(b.start, b);
        List<String> out = new ArrayList<>();
        Set<Integer> emitted = new HashSet<>();
        Set<Integer> declared = new HashSet<>();
        if (!struct(blocks.get(0), insns, byStart, cp, frame, out, emitted, declared, -1)) {
            return null;
        }
        return out;
    }

    /** Simple try/catch (único handler): reconstroi try + catch como statement. */
    private static List<String> tryCatch(List<BytecodeReader.Insn> insns, String[] cp,
                                         BytecodeFrame frame, int[][] handlers) {
        if (handlers.length != 1) return null;
        int start = handlers[0][0];
        int end = handlers[0][1];
        int handler = handlers[0][2];
        List<BytecodeReader.Insn> trySeq = range(insns, start, end);
        List<BytecodeReader.Insn> handlerSeq = range(insns, handler, Integer.MAX_VALUE);
        // handler começa com astore/astore_N (exceção → bind implícito no catch)
        if (!handlerSeq.isEmpty()) {
            int op = handlerSeq.get(0).opcode();
            if (op == 0x4b || op == 0x4c || op == 0x4d || op == 0x4e        // astore_0..3
                    || op == 0x3a || op == 0x36 || op == 0x37 || op == 0x38 || op == 0x39) { // astore/istore...
                handlerSeq = handlerSeq.subList(1, handlerSeq.size());
            }
        }
        List<String> tryStmts;
        String tryExpr = BytecodeDecoder.linearReturn(trySeq, cp, frame);
        if (tryExpr == null) {
            return null;
        }
        tryStmts = tryExpr.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of("return " + tryExpr));
        List<String> handlerStmts = emitLinear(handlerSeq, cp, frame, new HashSet<>());
        if (handlerStmts == null) return null;
        List<String> out = new ArrayList<>();
        out.add("try {");
        out.addAll(tryStmts);
        out.add("} catch (String e) {");
        out.addAll(handlerStmts);
        out.add("}");
        return out;
    }

    private static List<BytecodeReader.Insn> range(List<BytecodeReader.Insn> insns, int from, int to) {
        List<BytecodeReader.Insn> out = new ArrayList<>();
        for (BytecodeReader.Insn in : insns) {
            if (in.offset() >= from && in.offset() < to) out.add(in);
        }
        return out;
    }

    // ── try/finally (bloco duplicado + handler catch-all) ────────────────

    private static List<String> recoverFinally(List<BytecodeReader.Insn> insns, String[] cp,
                                               BytecodeFrame frame, int[][] handlers) {
        if (handlers.length != 1 || handlers[0].length < 4 || handlers[0][3] != 1) return null;
        int start = handlers[0][0];
        int to = handlers[0][1];
        List<BytecodeReader.Insn> tryInsns = range(insns, start, to);
        // último store da região try guarda o resultado num temp
        int lastStoreIdx = -1;
        int tempSlot = -1;
        for (int i = tryInsns.size() - 1; i >= 0; i--) {
            BytecodeReader.Insn in = tryInsns.get(i);
            if (isStore(in.opcode())) { lastStoreIdx = i; tempSlot = storeSlot(in); break; }
        }
        if (lastStoreIdx <= 0) return null;
        String tryResult = BytecodeDecoder.linearReturn(tryInsns.subList(0, lastStoreIdx), cp, frame);
        if (tryResult == null) return null;

        // retOff = load do temp (após o finally), delimita o corpo do finally
        int retOff = -1;
        for (BytecodeReader.Insn in : insns) {
            if (in.offset() < to) continue;
            int op = in.opcode();
            if ((op == 0x15 || op == 0x19) && in.operands()[0] == tempSlot) { retOff = in.offset(); break; }
            if (op >= 0x1a && op <= 0x1d && (op - 0x1a) == tempSlot) { retOff = in.offset(); break; }
            if (op >= 0x2a && op <= 0x2d && (op - 0x2a) == tempSlot) { retOff = in.offset(); break; }
        }
        if (retOff < 0) return null;
        List<String> finStmts = emitLinear(range(insns, to, retOff), cp, frame, new HashSet<>());
        if (finStmts == null) return null;

        List<String> out = new ArrayList<>();
        out.add("try {");
        out.add("return " + tryResult);
        out.add("} finally {");
        out.addAll(finStmts);
        out.add("}");
        return out;
    }

    private static boolean isStore(int op) {
        return (op >= 0x36 && op <= 0x3a) || (op >= 0x3b && op <= 0x4e);
    }

    private static int storeSlot(BytecodeReader.Insn in) {
        int op = in.opcode();
        if (op >= 0x36 && op <= 0x3a) return in.operands()[0];
        if (op >= 0x3b && op <= 0x3e) return op - 0x3b;  // istore_0..3
        if (op >= 0x3f && op <= 0x42) return op - 0x3f;  // lstore_0..3
        if (op >= 0x43 && op <= 0x46) return op - 0x43;  // fstore_0..3
        if (op >= 0x47 && op <= 0x4a) return op - 0x47;  // dstore_0..3
        return op - 0x4b;                                // astore_0..3
    }

    // ── switch (tableswitch/lookupswitch) ─────────────────────────────────

    private record SwitchInfo(int dflt, int[] values, int[] targets) {
    }

    private static SwitchInfo parseSwitch(byte[] code, int pc) {
        int op = code[pc] & 0xFF;
        if (op != 0xaa && op != 0xab) return null;
        int pad = (4 - ((pc + 1) % 4)) % 4;
        int p = pc + 1 + pad;
        if (p + 4 > code.length) return null;
        int dflt = pc + readInt4(code, p);
        p += 4;
        if (op == 0xaa) {
            int low = readInt4(code, p); p += 4;
            int high = readInt4(code, p); p += 4;
            int n = high - low + 1;
            if (n < 0 || p + n * 4 > code.length) return null;
            int[] values = new int[n];
            int[] targets = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = low + i;
                targets[i] = pc + readInt4(code, p);
                p += 4;
            }
            return new SwitchInfo(dflt, values, targets);
        }
        int npairs = readInt4(code, p); p += 4;
        if (npairs < 0 || p + npairs * 8 > code.length) return null;
        int[] values = new int[npairs];
        int[] targets = new int[npairs];
        for (int i = 0; i < npairs; i++) {
            values[i] = readInt4(code, p); p += 4;
            targets[i] = pc + readInt4(code, p);
            p += 4;
        }
        return new SwitchInfo(dflt, values, targets);
    }

    private static int readInt4(byte[] code, int pc) {
        return (code[pc] << 24) | ((code[pc + 1] & 0xFF) << 16)
             | ((code[pc + 2] & 0xFF) << 8) | (code[pc + 3] & 0xFF);
    }

    private static List<String> recoverSwitch(byte[] code, List<BytecodeReader.Insn> insns,
                                              String[] cp, BytecodeFrame frame) {
        // localiza o switch
        int swOff = -1;
        int swOp = -1;
        for (int pc = 0; pc < code.length; ) {
            int op = code[pc] & 0xFF;
            if (op == 0xaa || op == 0xab) { swOff = pc; swOp = op; break; }
            int l = BytecodeReader.length(op);
            pc += (l == -1) ? 1 : l;
        }
        if (swOff < 0) return null;
        SwitchInfo si = parseSwitch(code, swOff);
        if (si == null) return null;

        // expressão do switch = topo da pilha antes do switch
        String expr = BytecodeDecoder.linearReturn(range(insns, 0, swOff), cp, frame);
        if (expr == null) return null;

        // limites (targets ordenados) p/ reconstruir cada corpo
        java.util.TreeSet<Integer> bounds = new java.util.TreeSet<>();
        bounds.add(si.dflt);
        for (int t : si.targets) bounds.add(t);
        int maxOff = insns.get(insns.size() - 1).offset() + 1;

        List<String> out = new ArrayList<>();
        out.add("switch (" + expr + ") {");
        for (int i = 0; i < si.targets.length; i++) {
            int bodyEnd = nextBound(bounds, si.targets[i], maxOff);
            String val = BytecodeDecoder.linearReturn(range(insns, si.targets[i], bodyEnd), cp, frame);
            if (val == null) return null;
            out.add("case " + si.values[i] + ": return " + val);
        }
        int dfltEnd = nextBound(bounds, si.dflt, maxOff);
        String dfltVal = BytecodeDecoder.linearReturn(range(insns, si.dflt, dfltEnd), cp, frame);
        if (dfltVal == null) return null;
        out.add("default: return " + dfltVal);
        out.add("}");
        return out;
    }

    /** Condição de CONTINUAÇÃO (lado do fallthrough) — não invertida. */
    private static String contCond(int op) {
        return switch (op) {
            case 0x99 -> "== 0"; case 0x9a -> "!= 0"; case 0x9b -> "< 0";
            case 0x9c -> ">= 0"; case 0x9d -> "> 0"; case 0x9e -> "<= 0";
            case 0x9f -> "=="; case 0xa0 -> "!="; case 0xa1 -> "<";
            case 0xa2 -> ">="; case 0xa3 -> ">"; case 0xa4 -> "<=";
            default -> null;
        };
    }

    private static int nextBound(java.util.TreeSet<Integer> bounds, int start, int maxOff) {
        Integer higher = bounds.higher(start);
        return higher == null ? maxOff : higher;
    }

    private static boolean struct(BytecodeReader.Block b, List<BytecodeReader.Insn> insns,
                                  Map<Integer, BytecodeReader.Block> byStart, String[] cp,
                                  BytecodeFrame frame, List<String> out,
                                  Set<Integer> emitted, Set<Integer> declared, int header) {
        // Re-entrância de bloco: só a aresta de volta ao header do loop
        // ATUALMENTE ABERTO é legítima (back-edge normal; continue pode cair
        // no próprio header/incremento do loop). Re-entrar em bloco já emitido
        // que NÃO é o header aberto = ponto de junção compartilhado entre
        // braços de diamond (break, continue de loop externo, &&/||/?: com
        // braços que caem no mesmo bloco) — re-emiti-lo dentro de um ramo
        // perde o fluxo do outro ramo (ex.: `for` com `continue`: incremento
        // emitido só num dos caminhos; `&&`: return final sugado p/ o else
        // interno). Recovery de join estruturado é trabalho futuro; recusar →
        // stub UNKNOWN honesto (R6: nunca código errado compilável).
        if (emitted.contains(b.start)) return b.start == header;
        emitted.add(b.start);

        if (b.succ.isEmpty()) {
            List<String> stmts = emitLinear(BytecodeDecoder.insnsWithin(b, insns), cp, frame, declared);
            if (stmts == null) return false;
            out.addAll(stmts);
            return true;
        }
        if (b.succ.size() == 2) {
            // Bottom-tested loop (do-while): o bloco cond tem aresta para si
            // MESMO ou para um bloco ANTERIOR (teste embaixo; o corpo fica
            // fundido no próprio bloco). Em loop top-tested (while/for) o
            // bloco de teste é o menor offset do laço e ambos os sucessores
            // são > start (o back-edge vem de um bloco posterior).
            int back = -1;
            int exit = -1;
            for (int s : b.succ) {
                if (s <= b.start) back = s; else exit = s;
            }
            if (back >= 0) {
                if (back != b.start) return false;  // corpo separado do teste — não recuperar
                List<BytecodeReader.Insn> block = BytecodeDecoder.insnsWithin(b, insns);
                if (block.isEmpty()) return false;
                BytecodeReader.Insn last = block.get(block.size() - 1);
                if (!last.isCond()) return false;
                int op = last.opcode();
                String cond;
                List<BytecodeReader.Insn> body;
                if (op >= 0x99 && op <= 0x9e) {
                    if (block.size() < 2) return false;
                    String a = BytecodeDecoder.loadValue(block.get(block.size() - 2), frame);
                    if (a == null) return false;
                    cond = a + " " + contCond(op);
                    body = block.subList(0, block.size() - 2);
                } else if (op >= 0x9f && op <= 0xa4) {
                    if (block.size() < 3) return false;
                    String x = BytecodeDecoder.loadValue(block.get(block.size() - 3), frame);
                    String y = BytecodeDecoder.loadValue(block.get(block.size() - 2), frame);
                    if (x == null || y == null) return false;
                    cond = x + " " + contCond(op) + " " + y;
                    body = block.subList(0, block.size() - 3);
                } else {
                    return false;
                }
                if (body.isEmpty()) return false;
                List<String> stmts = emitLinear(body, cp, frame, declared);
                if (stmts == null) return false;
                out.add("do {");
                out.addAll(stmts);
                out.add("} while (" + cond + ")");
                return struct(byStart.get(exit), insns, byStart, cp, frame, out, emitted, declared, header);
            }
            String cond = BytecodeDecoder.blockCondition(b, insns, frame);
            if (cond == null) return false;
            int exitStart = b.succ.get(0);   // alvo do branch (falso)
            int thenStart = b.succ.get(1);   // fall-through (verdadeiro)
            boolean loop = BytecodeReader.isLoopHeader(b);
            if (loop) {
                out.add("while (" + cond + ") {");
                if (!struct(byStart.get(thenStart), insns, byStart, cp, frame, out, emitted, declared, b.start))
                    return false;
                out.add("}");
                return struct(byStart.get(exitStart), insns, byStart, cp, frame, out, emitted, declared, header);
            }
            out.add("if (" + cond + ") {");
            if (!struct(byStart.get(thenStart), insns, byStart, cp, frame, out, emitted, declared, header))
                return false;
            out.add("} else {");
            if (!struct(byStart.get(exitStart), insns, byStart, cp, frame, out, emitted, declared, header))
                return false;
            out.add("}");
            return true;
        }
        if (b.succ.size() == 1) {
            List<String> stmts = emitLinear(BytecodeDecoder.insnsWithin(b, insns), cp, frame, declared);
            if (stmts == null) return false;
            out.addAll(stmts);
            return struct(byStart.get(b.succ.get(0)), insns, byStart, cp, frame, out, emitted, declared, header);
        }
        return false;
    }

    /** Emite statements lineares de um bloco (para em branch/goto/return). */
    private static List<String> emitLinear(List<BytecodeReader.Insn> seq,
                                           String[] cp, BytecodeFrame frame,
                                           Set<Integer> declared) {
        List<String> stmts = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        for (BytecodeReader.Insn in : seq) {
            int op = in.opcode();
            switch (op) {
                case 0x01 -> stack.push("null");
                case 0x02 -> stack.push("-1");
                case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> stack.push(String.valueOf(op - 0x03));
                // lconst/dconst carregam o TIPO no próprio opcode (lição do
                // bug 62: só emitir quando o tipo não pode driftar). Kof tem
                // literal Long (42L) e Double (2.25) — fconst fica recusado.
                case 0x09 -> stack.push("0L");
                case 0x0a -> stack.push("1L");
                case 0x0b, 0x0c, 0x0d -> { return null; }  // fconst: sem literal float em Kof
                case 0x0e -> stack.push("0.0");
                case 0x0f -> stack.push("1.0");
                case 0x10 -> stack.push(String.valueOf((byte) in.operands()[0]));
                case 0x11 -> stack.push(String.valueOf((short) in.operands()[0]));
                case 0x12, 0x13 -> {                            // ldc, ldc_w
                    String c = BytecodeDecoder.ldc(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c);
                }
                case 0x14 -> {
                    String c = BytecodeDecoder.ldc2(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c);
                }
                case 0xba -> { // invokedynamic — só String concat (CONCAT: no CP)
                    String rec = BytecodeConcat.recipe(cp, in.operands()[0]);
                    if (rec == null) return null;
                    String expr = BytecodeConcat.apply(stack, rec);
                    if (expr == null) return null;
                    stack.push(expr);
                }
                case 0x1a, 0x1b, 0x1c, 0x1d -> stack.push(BytecodeDecoder.slotName(op - 0x1a, frame));
                case 0x1e, 0x1f, 0x20, 0x21 -> stack.push(BytecodeDecoder.slotName(op - 0x1e, frame));   // lload_0..3
                case 0x26, 0x27, 0x28, 0x29 -> stack.push(BytecodeDecoder.slotName(op - 0x26, frame));   // dload_0..3
                case 0x2a, 0x2b, 0x2c, 0x2d -> stack.push(BytecodeDecoder.slotName(op - 0x2a, frame));
                case 0x15, 0x19 -> stack.push(BytecodeDecoder.slotName(in.operands()[0], frame));
                case 0x16, 0x17, 0x18 -> stack.push(BytecodeDecoder.slotName(in.operands()[0], frame));   // lload/fload/dload
                case 0x36, 0x37, 0x38, 0x39, 0x3a -> { // istore..astore idx
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    int slot = in.operands()[0];
                    stmts.add(assign(slot, val, frame, declared));
                }
                case 0x3b, 0x3c, 0x3d, 0x3e -> { // istore_0..3
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(assign(op - 0x3b, val, frame, declared));
                }
                case 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a -> {
                    // lstore_0..3 / fstore_0..3 / dstore_0..3: 4 opcodes POR tipo
                    // → slot = (op - 0x3f) % 4 (dstore_1 = 0x48 → slot 1, não 9)
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(assign((op - 0x3f) % 4, val, frame, declared));
                }
                case 0x4b, 0x4c, 0x4d, 0x4e -> { // astore_0..3
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(assign(op - 0x4b, val, frame, declared));
                }
                case 0x84 -> { // iinc: byte1=index, byte2=const (signed)
                    int w = in.operands()[0];
                    int slot = (w >> 8) & 0xFF;
                    int k = (byte) (w & 0xFF);
                    String name = BytecodeDecoder.slotName(slot, frame);
                    declared.add(slot);
                    stmts.add(name + " = " + name + " + " + k);
                }
                case 0x60 -> { if (!BytecodeDecoder.bin(stack, "+")) return null; }
                case 0x64 -> { if (!BytecodeDecoder.bin(stack, "-")) return null; }
                case 0x68 -> { if (!BytecodeDecoder.bin(stack, "*")) return null; }
                case 0x6c -> { if (!BytecodeDecoder.bin(stack, "/")) return null; }
                case 0x70 -> { if (!BytecodeDecoder.bin(stack, "%")) return null; }
                case 0x74 -> { if (stack.isEmpty()) return null; stack.push("-" + stack.pop()); }
                // Fase E (09/09): pop/instanceof/checkcast com whitelist de
                // tipos sempre-em-escopo + heurística de chamada p/ pop — a
                // semântica (R6, recusa→stub) mora em BytecodeKofTypes.
                case 0x57, 0xc0, 0xc1 -> {
                    int idx = op == 0x57 ? 0 : in.operands()[0];   // pop não tem operando
                    if (stack.isEmpty() || !BytecodeKofTypes.exprOp(op, stack, cp, idx, stmts))
                        return null;
                }
                case 0xb8 -> { // invokestatic
                    String[] m = BytecodeDecoder.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = BytecodeDecoder.callArgs(stack, BytecodeDecoder.argCount(m[2]));
                    if (a == null) return null;
                    String mapped = BytecodeStdlib.statics(m[0], m[1], a, m[2]);
                    if (mapped == null && BytecodeDecoder.isJdkOwner(m[0])) return null;   // R6: owner JDK
                    String call = mapped != null ? mapped : BytecodeDecoder.simpleOwner(m[0]) + "." + m[1] + "(" + a + ")";
                    if (BytecodeDecoder.isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb6, 0xb9 -> { // invokevirtual / invokeinterface
                    String[] m = BytecodeDecoder.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = BytecodeDecoder.callArgs(stack, BytecodeDecoder.argCount(m[2]));
                    if (a == null || stack.isEmpty()) return null;
                    String recv = stack.pop();
                    String mapped = BytecodeStdlib.virtual(recv, m[0], m[1], a);
                    if (mapped == null && recv.startsWith("⟦new⟧")) return null;  // R6: método em novo Objeto JDK
                    String call = mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")";
                    if (BytecodeDecoder.isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb4 -> { // getfield
                    String[] f = BytecodeDecoder.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String obj = stack.pop();
                    stack.push(obj + "." + f[1]);
                }
                case 0xb2 -> { // getstatic
                    String[] f = BytecodeDecoder.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null) return null;
                    stack.push(BytecodeDecoder.simpleOwner(f[0]) + "." + f[1]);
                }
                case 0xb5 -> { // putfield
                    String[] f = BytecodeDecoder.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String val = stack.pop();
                    if (stack.isEmpty()) return null;
                    String obj = stack.pop();
                    stmts.add(obj + "." + f[1] + " = " + val);
                }
                case 0xb3 -> { // putstatic
                    String[] f = BytecodeDecoder.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(BytecodeDecoder.simpleOwner(f[0]) + "." + f[1] + " = " + val);
                }
                case 0xac, 0xad, 0xae, 0xaf -> { if (stack.isEmpty()) return null; stmts.add("return " + stack.pop()); return stmts; }
                case 0xb0 -> { if (stack.isEmpty()) return null; stmts.add("return " + stack.pop()); return stmts; }
                case 0xb1 -> { stmts.add("return"); return stmts; }
                // terminadores de bloco: branch/goto — paramos sem emitir
                case 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7 -> { return stmts; }
                default -> {
                    if (BytecodeDecoder.blockerSink != null) BytecodeDecoder.blockerSink.accept(op);
                    return null;
                }
            }
        }
        return stmts;
    }

    private static String assign(int slot, String value, BytecodeFrame frame, Set<Integer> declared) {
        String name = BytecodeDecoder.slotName(slot, frame);
        if (!declared.contains(slot)) {
            declared.add(slot);
            return "var " + name + " = " + value;
        }
        return name + " = " + value;
    }
}
