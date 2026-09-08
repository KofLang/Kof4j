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

    static String linearReturn(List<BytecodeReader.Insn> insns, String[] cp,
                                       int paramCount, boolean isStatic) {
        Deque<String> stack = new ArrayDeque<>();
        for (BytecodeReader.Insn in : insns) {
            int op = in.opcode();
            switch (op) {
                case 0x02 -> stack.push("-1");
                case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> stack.push(String.valueOf(op - 0x03));
                // lconst/dconst: tipo embutido no opcode (lição bug 62 — só
                // emitir quando não pode driftar). fconst (0x0b-0x0d) recusado.
                case 0x09 -> stack.push("0L");
                case 0x0a -> stack.push("1L");
                case 0x0b, 0x0c, 0x0d -> { return null; }
                case 0x0e -> stack.push("0.0");
                case 0x0f -> stack.push("1.0");
                case 0x10 -> stack.push(String.valueOf((byte) in.operands()[0]));
                case 0x11 -> stack.push(String.valueOf((short) in.operands()[0]));
                case 0x12 -> {
                    String c = ldc(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c);
                }
                case 0x14 -> {
                    String c = ldc2(cp, in.operands()[0]);
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
                case 0xba -> { // invokedynamic — só String concat (CONCAT: no CP)
                    String rec = BytecodeConcat.recipe(cp, in.operands()[0]);
                    if (rec == null) return null;
                    String expr = BytecodeConcat.apply(stack, rec);
                    if (expr == null) return null;
                    stack.push(expr);
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

    static String comparisonReturn(List<BytecodeReader.Insn> insns, int paramCount, boolean isStatic) {
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

    static String ifElseReturn(List<BytecodeReader.Insn> insns, String[] cp,
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

    static BytecodeReader.Block find(List<BytecodeReader.Block> blocks, int start) {
        for (BytecodeReader.Block b : blocks) if (b.start == start) return b;
        return null;
    }

    static List<BytecodeReader.Insn> insnsWithin(BytecodeReader.Block b, List<BytecodeReader.Insn> insns) {
        List<BytecodeReader.Insn> block = new java.util.ArrayList<>();
        for (BytecodeReader.Insn in : insns) {
            if (in.offset() >= b.start && in.offset() < b.end) block.add(in);
        }
        return block;
    }

    static String blockCondition(BytecodeReader.Block entry, List<BytecodeReader.Insn> insns,
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

    static String blockReturnExpr(BytecodeReader.Block b, List<BytecodeReader.Insn> insns,
                                          String[] cp, int paramCount, boolean isStatic) {
        return linearReturn(insnsWithin(b, insns), cp, paramCount, isStatic);
    }

    static String slotName(int slot, int paramCount, boolean isStatic) {
        if (!isStatic && slot == 0) return "this";
        int paramIndex = isStatic ? slot : slot - 1;
        if (paramIndex >= 0 && paramIndex < paramCount) return "arg" + paramIndex;
        return "v" + slot;
    }

    static String loadValue(BytecodeReader.Insn in, int paramCount, boolean isStatic) {
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

    static boolean bin(Deque<String> stack, String op) {
        if (stack.size() < 2) return false;
        String b = stack.pop();
        String a = stack.pop();
        stack.push("(" + a + " " + op + " " + b + ")");
        return true;
    }

    /** Pop n argumentos da pilha e devolve a lista "a, b, ..." (ou null). */
    static String callArgs(Deque<String> stack, int n) {
        List<String> args = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (stack.isEmpty()) return null;
            args.add(0, stack.pop());
        }
        return String.join(", ", args);
    }

    static String invCond(int op) {
        return switch (op) {
            case 0x9f -> "!="; case 0xa0 -> "=="; case 0xa1 -> ">="; case 0xa2 -> "<";
            case 0xa3 -> "<="; case 0xa4 -> ">";  case 0x99 -> "!= 0"; case 0x9a -> "== 0";
            case 0x9b -> ">= 0"; case 0x9c -> "< 0"; case 0x9d -> "<= 0"; case 0x9e -> "> 0";
            default -> null;
        };
    }

    /**
     * ldc2_w (0x14) — só Long/Double no CP. Classificação por FORMA (lição
     * bug 62: só emitir quando o tipo não pode driftar): String.valueOf(long)
     * é sempre dígitos ([sinal]) → literal Long com sufixo `L`; o verificador
     * garante const tipo == uso. Double.toString SEMPRE traz '.'/E (mesmo
     * 100.0 → "100.0") → literal Double; NaN/Infinity ficam sem literal em
     * Kof → recusar (stub honesto).
     */
    static String ldc2(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (e.startsWith("#")) return null;           // só String ref — nunca ldc2
        if (e.equals("NaN") || e.equals("Infinity") || e.equals("-Infinity")) return null;
        if (e.indexOf('.') >= 0 || e.indexOf('e') >= 0 || e.indexOf('E') >= 0) return e;
        return e + "L";
    }

    static String ldc(String[] cp, int idx) {
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
        // Constante float (CP tag 4, via ldc): o parser agora guarda o valor
        // float (ex. "3.5"), mas Kof não tem literal float inline — "3.5" é
        // Double e drifta o tipo do método (SEM010). Recusar → stub honesto
        // (irmão de Double/Long, que já caem em ldc2_w → default → null).
        if (looksLikeFloatLiteral(e)) return null;
        return e;
    }

    /** Float.toString → sempre contém '.', 'e'/'E', ou NaN/Infinity. */
    private static boolean looksLikeFloatLiteral(String s) {
        if (s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0) return true;
        return s.equals("NaN") || s.equals("Infinity") || s.equals("-Infinity");
    }

    // ── chamadas de método ───────────────────────────────────────────────

    static String[] resolveMethodRef(String[] cp, int idx) {
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

    static Integer parseCp(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Nº de argumentos do descriptor de método. */
    static int argCount(String desc) {
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

    static String simpleOwner(String internal) {
        int s = internal.lastIndexOf('/');
        return s >= 0 ? internal.substring(s + 1) : internal;
    }

    static boolean isVoidDesc(String desc) {
        int idx = desc.indexOf(')');
        return idx >= 0 && idx + 1 < desc.length() && desc.charAt(idx + 1) == 'V';
    }

    /** Mapeia chamada de stdlib Java → idiom Kof (decompiler, TRANSLATOR-equivalente). */
    static String mapStdlib(String receiver, String ownerInternal, String name, String args) {
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
    static String mapStaticCall(String ownerInternal, String name, String args) {
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
    static String resolveClassName(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (!e.startsWith("#")) return null;
        Integer nameIdx = parseCp(e.substring(1));
        if (nameIdx == null || nameIdx >= cp.length || cp[nameIdx] == null) return null;
        return simpleOwner(cp[nameIdx]);
    }

    // ── statement-based body (loops / stores / multi-statement) ──────────
}
