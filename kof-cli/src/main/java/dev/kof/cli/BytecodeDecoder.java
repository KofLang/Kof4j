package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconstrução de expressão Kof a partir do bytecode (decompiler, Fase C/E).
 *
 * Opera sobre a lista de instruções do {@link BytecodeReader} com uma pilha
 * simbólica: constantes, loads, aritmética → expressão; comparações booleanas
 * e if/else de retorno → if-expression idiomática. Fora do subconjunto,
 * devolve {@code null} (o decompiler cai no stub honesto — nunca inventa).
 */
final class BytecodeDecoder {

    private BytecodeDecoder() {
    }

    /** Devolve a expressão do corpo, {@code ""} para vazio, ou {@code null} se não recuperável. */
    static String recoverExpression(byte[] code, String[] cp, int paramCount, boolean isStatic) {
        List<BytecodeReader.Insn> insns = BytecodeReader.decode(code);
        if (insns.isEmpty()) return null;

        String lin = linearReturn(insns, cp, paramCount, isStatic);
        if (lin != null) return lin;
        String cmp = comparisonReturn(insns, paramCount, isStatic);
        if (cmp != null) return cmp;
        return ifElseReturn(insns, cp, paramCount, isStatic);
    }

    // ── linear: pilha simbólica → value no return ────────────────────────

    private static String linearReturn(List<BytecodeReader.Insn> insns, String[] cp,
                                       int paramCount, boolean isStatic) {
        Deque<String> stack = new ArrayDeque<>();
        for (BytecodeReader.Insn in : insns) {
            int op = in.opcode();
            switch (op) {
                case 0x02 -> stack.push("-1");
                case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> stack.push(String.valueOf(op - 0x03));
                case 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f -> { return null; }
                case 0x10 -> stack.push(String.valueOf((byte) in.operands()[0]));
                case 0x11 -> stack.push(String.valueOf((short) in.operands()[0]));
                case 0x12 -> {
                    String c = ldc(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c);
                }
                case 0x1a, 0x1b, 0x1c, 0x1d -> stack.push(slotName(op - 0x1a, paramCount, isStatic));
                case 0x2a, 0x2b, 0x2c, 0x2d -> stack.push(slotName(op - 0x2a, paramCount, isStatic));
                case 0x15, 0x19 -> stack.push(slotName(in.operands()[0], paramCount, isStatic));
                case 0x60 -> { if (!bin(stack, "+")) return null; }
                case 0x64 -> { if (!bin(stack, "-")) return null; }
                case 0x68 -> { if (!bin(stack, "*")) return null; }
                case 0x6c -> { if (!bin(stack, "/")) return null; }
                case 0x70 -> { if (!bin(stack, "%")) return null; }
                case 0x74 -> { // ineg
                    if (stack.isEmpty()) return null;
                    stack.push("-" + stack.pop());
                }
                case 0xb8 -> { // invokestatic
                    String[] m = resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = callArgs(stack, argCount(m[2]));
                    if (a == null) return null;
                    String mapped = mapStaticCall(m[0], m[1], a);
                    stack.push(mapped != null ? mapped : simpleOwner(m[0]) + "." + m[1] + "(" + a + ")");
                }
                case 0xb6, 0xb9 -> { // invokevirtual / invokeinterface
                    String[] m = resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = callArgs(stack, argCount(m[2]));
                    if (a == null || stack.isEmpty()) return null;
                    String recv = stack.pop();
                    String mapped = mapStdlib(recv, m[0], m[1], a);
                    stack.push(mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")");
                }
                case 0xb4 -> { // getfield
                    String[] f = resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String obj = stack.pop();
                    stack.push(obj + "." + f[1]);
                }
                case 0xb2 -> { // getstatic
                    String[] f = resolveMethodRef(cp, in.operands()[0]);
                    if (f == null) return null;
                    stack.push(simpleOwner(f[0]) + "." + f[1]);
                }
                case 0xbb -> { // new
                    String cn = resolveClassName(cp, in.operands()[0]);
                    if (cn == null) return null;
                    stack.push("⟦new⟧" + cn);
                }
                case 0x59 -> { // dup (só no padrão new)
                    if (stack.isEmpty()) return null;
                    String t = stack.peek();
                    if (!t.startsWith("⟦new⟧")) return null;
                    stack.push(t);
                }
                case 0xb7 -> { // invokespecial (<init>)
                    String[] m = resolveMethodRef(cp, in.operands()[0]);
                    if (m == null || !"<init>".equals(m[1])) return null;
                    String a = callArgs(stack, argCount(m[2]));
                    if (a == null || stack.size() < 2) return null;
                    stack.pop();                       // receiver (cópia do dup)
                    String result = stack.pop();       // marcador do new
                    if (!result.startsWith("⟦new⟧")) return null;
                    stack.push(result.substring("⟦new⟧".length()) + "(" + a + ")");
                }
                case 0xac, 0xad, 0xae, 0xaf, 0xb0 -> {
                    return stack.isEmpty() ? null : stack.pop();
                }
                case 0xb1 -> {
                    return stack.isEmpty() ? "" : null;
                }
                default -> { return null; }
            }
        }
        // fim sem return: devolve o topo da pilha (região protegida de try)
        return stack.size() == 1 ? stack.peek() : null;
    }

    // ── comparação booleana de retorno ───────────────────────────────────

    private static String comparisonReturn(List<BytecodeReader.Insn> insns, int paramCount, boolean isStatic) {
        int n = insns.size();
        if (n < 5) return null;
        BytecodeReader.Insn last = insns.get(n - 1);
        if (!last.isReturn() || last.opcode() != 0xac) return null;
        BytecodeReader.Insn zero = insns.get(n - 2);
        if (zero.opcode() != 0x03) return null;
        BytecodeReader.Insn go = insns.get(n - 3);
        if (go.opcode() != 0xa7 || go.target() != last.offset()) return null;
        if (insns.get(n - 4).opcode() != 0x04) return null;
        BytecodeReader.Insn cmp = insns.get(n - 5);
        String inv = invCond(cmp.opcode());
        if (inv == null || cmp.target() != zero.offset()) return null;

        java.util.List<String> operands = new java.util.ArrayList<>();
        for (int i = 0; i < n - 5 && operands.size() < 2; i++) {
            String v = loadValue(insns.get(i), paramCount, isStatic);
            if (v == null) return null;
            operands.add(v);
        }
        if (cmp.opcode() >= 0x9f && cmp.opcode() <= 0xa4) {
            if (operands.size() != 2) return null;
            return operands.get(0) + " " + inv + " " + operands.get(1);
        }
        return operands.isEmpty() ? null : operands.get(0) + " " + inv;
    }

    // ── if/else de retorno (via CFG) ─────────────────────────────────────

    private static String ifElseReturn(List<BytecodeReader.Insn> insns, String[] cp,
                                       int paramCount, boolean isStatic) {
        List<BytecodeReader.Block> blocks = BytecodeReader.cfg(insns, new int[0]);
        if (blocks.isEmpty()) return null;
        BytecodeReader.Block entry = blocks.get(0);
        if (entry.succ.size() != 2) return null;
        // entrada é condicional: dois sucessores (then e else)
        String cond = blockCondition(entry, insns, paramCount, isStatic);
        if (cond == null) return null;
        // succ[0] = alvo do branch (falso/else); succ[1] = fall-through (verdadeiro/then)
        int elseStart = entry.succ.get(0);
        int thenStart = entry.succ.get(1);
        BytecodeReader.Block thenB = find(blocks, thenStart);
        BytecodeReader.Block elseB = find(blocks, elseStart);
        if (thenB == null || elseB == null) return null;
        String thenE = blockReturnExpr(thenB, insns, cp, paramCount, isStatic);
        String elseE = blockReturnExpr(elseB, insns, cp, paramCount, isStatic);
        if (thenE == null || elseE == null) return null;
        return "if (" + cond + ") " + thenE + " else " + elseE;
    }

    private static BytecodeReader.Block find(List<BytecodeReader.Block> blocks, int start) {
        for (BytecodeReader.Block b : blocks) if (b.start == start) return b;
        return null;
    }

    private static List<BytecodeReader.Insn> insnsWithin(BytecodeReader.Block b, List<BytecodeReader.Insn> insns) {
        List<BytecodeReader.Insn> block = new java.util.ArrayList<>();
        for (BytecodeReader.Insn in : insns) {
            if (in.offset() >= b.start && in.offset() < b.end) block.add(in);
        }
        return block;
    }

    private static String blockCondition(BytecodeReader.Block entry, List<BytecodeReader.Insn> insns,
                                         int paramCount, boolean isStatic) {
        List<BytecodeReader.Insn> block = insnsWithin(entry, insns);
        if (block.isEmpty()) return null;
        BytecodeReader.Insn last = block.get(block.size() - 1);
        if (!last.isCond()) return null;
        String inv = invCond(last.opcode());
        if (inv == null) return null;
        java.util.List<String> operands = new java.util.ArrayList<>();
        for (int i = 0; i < block.size() - 1 && operands.size() < 2; i++) {
            String v = loadValue(block.get(i), paramCount, isStatic);
            if (v == null) return null;
            operands.add(v);
        }
        if (last.opcode() >= 0x9f && last.opcode() <= 0xa4) {
            if (operands.size() != 2) return null;
            return operands.get(0) + " " + inv + " " + operands.get(1);
        }
        return operands.isEmpty() ? null : operands.get(0) + " " + inv;
    }

    private static String blockReturnExpr(BytecodeReader.Block b, List<BytecodeReader.Insn> insns,
                                          String[] cp, int paramCount, boolean isStatic) {
        return linearReturn(insnsWithin(b, insns), cp, paramCount, isStatic);
    }

    private static String slotName(int slot, int paramCount, boolean isStatic) {
        if (!isStatic && slot == 0) return "this";
        int paramIndex = isStatic ? slot : slot - 1;
        if (paramIndex >= 0 && paramIndex < paramCount) return "arg" + paramIndex;
        return "v" + slot;
    }

    private static String loadValue(BytecodeReader.Insn in, int paramCount, boolean isStatic) {
        return switch (in.opcode()) {
            case 0x1a, 0x1b, 0x1c, 0x1d -> slotName(in.opcode() - 0x1a, paramCount, isStatic);
            case 0x2a, 0x2b, 0x2c, 0x2d -> slotName(in.opcode() - 0x2a, paramCount, isStatic);
            case 0x15, 0x19 -> slotName(in.operands()[0], paramCount, isStatic);
            case 0x03 -> "0";
            case 0x04 -> "1";
            case 0x05 -> "2";
            case 0x06 -> "3";
            case 0x07 -> "4";
            case 0x08 -> "5";
            case 0x02 -> "-1";
            case 0x10 -> String.valueOf((byte) in.operands()[0]);
            default -> null;
        };
    }

    private static boolean bin(Deque<String> stack, String op) {
        if (stack.size() < 2) return false;
        String b = stack.pop();
        String a = stack.pop();
        stack.push("(" + a + " " + op + " " + b + ")");
        return true;
    }

    /** Pop n argumentos da pilha e devolve a lista "a, b, ..." (ou null). */
    private static String callArgs(Deque<String> stack, int n) {
        List<String> args = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (stack.isEmpty()) return null;
            args.add(0, stack.pop());
        }
        return String.join(", ", args);
    }

    private static String invCond(int op) {
        return switch (op) {
            case 0x9f -> "!="; case 0xa0 -> "=="; case 0xa1 -> ">="; case 0xa2 -> "<";
            case 0xa3 -> "<="; case 0xa4 -> ">";  case 0x99 -> "!= 0"; case 0x9a -> "== 0";
            case 0x9b -> ">= 0"; case 0x9c -> "< 0"; case 0x9d -> "<= 0"; case 0x9e -> "> 0";
            default -> null;
        };
    }

    private static String ldc(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (e.startsWith("#")) {
            try {
                int ref = Integer.parseInt(e.substring(1));
                if (ref <= 0 || ref >= cp.length || cp[ref] == null) return null;
                return "\"" + cp[ref] + "\"";
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return e;
    }

    // ── chamadas de método ───────────────────────────────────────────────

    /** Resolve um Methodref/InterfaceMethodref do CP → {ownerInternal, name, desc}. */
    private static String[] resolveMethodRef(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (!e.startsWith("#") || e.indexOf('#', 1) < 0) return null;
        int split = e.indexOf('#', 1);
        Integer classIdx = parseCp(e.substring(1, split));
        Integer natIdx = parseCp(e.substring(split + 1));
        if (classIdx == null || natIdx == null || classIdx >= cp.length || natIdx >= cp.length) return null;
        String classE = cp[classIdx];
        if (classE == null || !classE.startsWith("#")) return null;
        Integer nameIdx = parseCp(classE.substring(1));
        if (nameIdx == null || nameIdx >= cp.length || cp[nameIdx] == null) return null;
        String owner = cp[nameIdx];
        String nat = cp[natIdx];
        if (nat == null || !nat.startsWith("#") || nat.indexOf('#', 1) < 0) return null;
        int split2 = nat.indexOf('#', 1);
        Integer mNameIdx = parseCp(nat.substring(1, split2));
        Integer mDescIdx = parseCp(nat.substring(split2 + 1));
        if (mNameIdx == null || mDescIdx == null || mNameIdx >= cp.length || mDescIdx >= cp.length) return null;
        if (cp[mNameIdx] == null || cp[mDescIdx] == null) return null;
        return new String[]{owner, cp[mNameIdx], cp[mDescIdx]};
    }

    private static Integer parseCp(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Nº de argumentos do descriptor de método. */
    private static int argCount(String desc) {
        int end = desc.indexOf(')');
        int count = 0;
        int i = 1;
        while (i < end) {
            char c = desc.charAt(i);
            if (c == 'L') { i = desc.indexOf(';', i) + 1; }
            else if (c == '[') {
                while (i < end && desc.charAt(i) == '[') i++;
                if (i < end && desc.charAt(i) == 'L') i = desc.indexOf(';', i) + 1;
                else i++;
            } else { i++; }
            count++;
        }
        return count;
    }

    private static String simpleOwner(String internal) {
        int s = internal.lastIndexOf('/');
        return s >= 0 ? internal.substring(s + 1) : internal;
    }

    private static boolean isVoidDesc(String desc) {
        int idx = desc.indexOf(')');
        return idx >= 0 && idx + 1 < desc.length() && desc.charAt(idx + 1) == 'V';
    }

    /** Mapeia chamada de stdlib Java → idiom Kof (decompiler, TRANSLATOR-equivalente). */
    private static String mapStdlib(String receiver, String ownerInternal, String name, String args) {
        if ("java/io/PrintStream".equals(ownerInternal) && "System.out".equals(receiver)
                && ("println".equals(name) || "print".equals(name))) {
            return (name.equals("println") ? "println" : "print") + "(" + args + ")";
        }
        if ("java/lang/String".equals(ownerInternal) && "equals".equals(name)) {
            return receiver + " == " + args;
        }
        // métodos sem-argumento que em Kof são PROPRIEDADES (não métodos)
        if (args.isEmpty() && ("length".equals(name) || "size".equals(name) || "isEmpty".equals(name))) {
            return receiver + "." + name;
        }
        return null;
    }

    /** Mapeia chamada ESTÁTICA de stdlib → idiom Kof (ex.: Integer.parseInt -> .toInt). */
    private static String mapStaticCall(String ownerInternal, String name, String args) {
        if ("java/lang/Integer".equals(ownerInternal) && ("parseInt".equals(name) || "valueOf".equals(name))) {
            return args + ".toInt()";
        }
        if ("java/lang/Long".equals(ownerInternal) && ("parseLong".equals(name) || "valueOf".equals(name))) {
            return args + ".toLong()";
        }
        if ("java/lang/Double".equals(ownerInternal) && ("parseDouble".equals(name) || "valueOf".equals(name))) {
            return args + ".toDouble()";
        }
        if ("java/lang/Float".equals(ownerInternal) && ("parseFloat".equals(name) || "valueOf".equals(name))) {
            return args + ".toFloat()";
        }
        if ("java/lang/System".equals(ownerInternal) && "currentTimeMillis".equals(name)) {
            return "now()";
        }
        return null;
    }

    /** Resolve um nome de classe (Class CP entry) → nome simples. */
    private static String resolveClassName(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (!e.startsWith("#")) return null;
        Integer nameIdx = parseCp(e.substring(1));
        if (nameIdx == null || nameIdx >= cp.length || cp[nameIdx] == null) return null;
        return simpleOwner(cp[nameIdx]);
    }

    // ── statement-based body (loops / stores / multi-statement) ──────────

    static List<String> recoverStatements(byte[] code, String[] cp, int paramCount, boolean isStatic, int[][] handlers) {
        List<BytecodeReader.Insn> insns = BytecodeReader.decode(code);
        List<String> sw = recoverSwitch(code, insns, cp, paramCount, isStatic);
        if (sw != null) return sw;
        if (handlers != null && handlers.length > 0) {
            List<String> fin = recoverFinally(insns, cp, paramCount, isStatic, handlers);
            if (fin != null) return fin;
            List<String> tc = tryCatch(insns, cp, paramCount, isStatic, handlers);
            if (tc != null) return tc;
        }
        List<BytecodeReader.Block> blocks = BytecodeReader.cfg(insns, new int[0]);
        if (blocks.isEmpty()) return null;
        Map<Integer, BytecodeReader.Block> byStart = new HashMap<>();
        for (BytecodeReader.Block b : blocks) byStart.put(b.start, b);
        List<String> out = new ArrayList<>();
        Set<Integer> emitted = new HashSet<>();
        Set<Integer> declared = new HashSet<>();
        if (!struct(blocks.get(0), insns, byStart, cp, paramCount, isStatic, out, emitted, declared)) {
            return null;
        }
        return out;
    }

    /** Simple try/catch (único handler): reconstroi try + catch como statement. */
    private static List<String> tryCatch(List<BytecodeReader.Insn> insns, String[] cp,
                                         int paramCount, boolean isStatic, int[][] handlers) {
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
        String tryExpr = linearReturn(trySeq, cp, paramCount, isStatic);
        if (tryExpr == null) {
            return null;
        }
        tryStmts = tryExpr.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of("return " + tryExpr));
        List<String> handlerStmts = emitLinear(handlerSeq, cp, paramCount, isStatic, new HashSet<>());
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
                                               int paramCount, boolean isStatic, int[][] handlers) {
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
        String tryResult = linearReturn(tryInsns.subList(0, lastStoreIdx), cp, paramCount, isStatic);
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
        List<String> finStmts = emitLinear(range(insns, to, retOff), cp, paramCount, isStatic, new HashSet<>());
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
                                              String[] cp, int paramCount, boolean isStatic) {
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
        String expr = linearReturn(range(insns, 0, swOff), cp, paramCount, isStatic);
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
            String val = linearReturn(range(insns, si.targets[i], bodyEnd), cp, paramCount, isStatic);
            if (val == null) return null;
            out.add("case " + si.values[i] + ": return " + val);
        }
        int dfltEnd = nextBound(bounds, si.dflt, maxOff);
        String dfltVal = linearReturn(range(insns, si.dflt, dfltEnd), cp, paramCount, isStatic);
        if (dfltVal == null) return null;
        out.add("default: return " + dfltVal);
        out.add("}");
        return out;
    }

    private static int nextBound(java.util.TreeSet<Integer> bounds, int start, int maxOff) {
        Integer higher = bounds.higher(start);
        return higher == null ? maxOff : higher;
    }

    private static boolean struct(BytecodeReader.Block b, List<BytecodeReader.Insn> insns,
                                  Map<Integer, BytecodeReader.Block> byStart, String[] cp,
                                  int paramCount, boolean isStatic, List<String> out,
                                  Set<Integer> emitted, Set<Integer> declared) {
        if (emitted.contains(b.start)) return true;  // back-edge / já emitido
        emitted.add(b.start);

        if (b.succ.isEmpty()) {
            List<String> stmts = emitLinear(insnsWithin(b, insns), cp, paramCount, isStatic, declared);
            if (stmts == null) return false;
            out.addAll(stmts);
            return true;
        }
        if (b.succ.size() == 2) {
            String cond = blockCondition(b, insns, paramCount, isStatic);
            if (cond == null) return false;
            int exitStart = b.succ.get(0);   // alvo do branch (falso)
            int thenStart = b.succ.get(1);   // fall-through (verdadeiro)
            boolean loop = BytecodeReader.isLoopHeader(b);
            if (loop) {
                out.add("while (" + cond + ") {");
                if (!struct(byStart.get(thenStart), insns, byStart, cp, paramCount, isStatic, out, emitted, declared))
                    return false;
                out.add("}");
                return struct(byStart.get(exitStart), insns, byStart, cp, paramCount, isStatic, out, emitted, declared);
            }
            out.add("if (" + cond + ") {");
            if (!struct(byStart.get(thenStart), insns, byStart, cp, paramCount, isStatic, out, emitted, declared))
                return false;
            out.add("} else {");
            if (!struct(byStart.get(exitStart), insns, byStart, cp, paramCount, isStatic, out, emitted, declared))
                return false;
            out.add("}");
            return true;
        }
        if (b.succ.size() == 1) {
            List<String> stmts = emitLinear(insnsWithin(b, insns), cp, paramCount, isStatic, declared);
            if (stmts == null) return false;
            out.addAll(stmts);
            return struct(byStart.get(b.succ.get(0)), insns, byStart, cp, paramCount, isStatic, out, emitted, declared);
        }
        return false;
    }

    /** Emite statements lineares de um bloco (para em branch/goto/return). */
    private static List<String> emitLinear(List<BytecodeReader.Insn> seq,
                                           String[] cp, int paramCount, boolean isStatic,
                                           Set<Integer> declared) {
        List<String> stmts = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        for (BytecodeReader.Insn in : seq) {
            int op = in.opcode();
            switch (op) {
                case 0x02 -> stack.push("-1");
                case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> stack.push(String.valueOf(op - 0x03));
                case 0x10 -> stack.push(String.valueOf((byte) in.operands()[0]));
                case 0x11 -> stack.push(String.valueOf((short) in.operands()[0]));
                case 0x12 -> {
                    String c = ldc(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c);
                }
                case 0x1a, 0x1b, 0x1c, 0x1d -> stack.push(slotName(op - 0x1a, paramCount, isStatic));
                case 0x2a, 0x2b, 0x2c, 0x2d -> stack.push(slotName(op - 0x2a, paramCount, isStatic));
                case 0x15, 0x19 -> stack.push(slotName(in.operands()[0], paramCount, isStatic));
                case 0x36, 0x37, 0x38, 0x39, 0x3a -> { // istore..astore idx
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    int slot = in.operands()[0];
                    stmts.add(assign(slot, val, paramCount, isStatic, declared));
                }
                case 0x3b, 0x3c, 0x3d, 0x3e -> { // istore_0..3
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(assign(op - 0x3b, val, paramCount, isStatic, declared));
                }
                case 0x4b, 0x4c, 0x4d, 0x4e -> { // astore_0..3
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(assign(op - 0x4b, val, paramCount, isStatic, declared));
                }
                case 0x84 -> { // iinc: byte1=index, byte2=const (signed)
                    int w = in.operands()[0];
                    int slot = (w >> 8) & 0xFF;
                    int k = (byte) (w & 0xFF);
                    String name = slotName(slot, paramCount, isStatic);
                    declared.add(slot);
                    stmts.add(name + " = " + name + " + " + k);
                }
                case 0x60 -> { if (!bin(stack, "+")) return null; }
                case 0x64 -> { if (!bin(stack, "-")) return null; }
                case 0x68 -> { if (!bin(stack, "*")) return null; }
                case 0x6c -> { if (!bin(stack, "/")) return null; }
                case 0x70 -> { if (!bin(stack, "%")) return null; }
                case 0x74 -> { if (stack.isEmpty()) return null; stack.push("-" + stack.pop()); }
                case 0xb8 -> { // invokestatic
                    String[] m = resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = callArgs(stack, argCount(m[2]));
                    if (a == null) return null;
                    String mapped = mapStaticCall(m[0], m[1], a);
                    String call = mapped != null ? mapped : simpleOwner(m[0]) + "." + m[1] + "(" + a + ")";
                    if (isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb6, 0xb9 -> { // invokevirtual / invokeinterface
                    String[] m = resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = callArgs(stack, argCount(m[2]));
                    if (a == null || stack.isEmpty()) return null;
                    String recv = stack.pop();
                    String mapped = mapStdlib(recv, m[0], m[1], a);
                    String call = mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")";
                    if (isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb4 -> { // getfield
                    String[] f = resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String obj = stack.pop();
                    stack.push(obj + "." + f[1]);
                }
                case 0xb2 -> { // getstatic
                    String[] f = resolveMethodRef(cp, in.operands()[0]);
                    if (f == null) return null;
                    stack.push(simpleOwner(f[0]) + "." + f[1]);
                }
                case 0xb5 -> { // putfield
                    String[] f = resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String val = stack.pop();
                    if (stack.isEmpty()) return null;
                    String obj = stack.pop();
                    stmts.add(obj + "." + f[1] + " = " + val);
                }
                case 0xb3 -> { // putstatic
                    String[] f = resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(simpleOwner(f[0]) + "." + f[1] + " = " + val);
                }
                case 0xac, 0xad, 0xae, 0xaf -> { if (stack.isEmpty()) return null; stmts.add("return " + stack.pop()); return stmts; }
                case 0xb0 -> { if (stack.isEmpty()) return null; stmts.add("return " + stack.pop()); return stmts; }
                case 0xb1 -> { stmts.add("return"); return stmts; }
                // terminadores de bloco: branch/goto — paramos sem emitir
                case 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7 -> { return stmts; }
                default -> { return null; }
            }
        }
        return stmts;
    }

    private static String assign(int slot, String value, int paramCount, boolean isStatic, Set<Integer> declared) {
        String name = slotName(slot, paramCount, isStatic);
        if (!declared.contains(slot)) {
            declared.add(slot);
            return "var " + name + " = " + value;
        }
        return name + " = " + value;
    }
}