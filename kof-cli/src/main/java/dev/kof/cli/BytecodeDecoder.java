package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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
                case 0x1a, 0x1b, 0x1c, 0x1d -> stack.push(local(op - 0x1a, paramCount, isStatic));
                case 0x2a, 0x2b, 0x2c, 0x2d -> stack.push(local(op - 0x2a, paramCount, isStatic));
                case 0x15, 0x19 -> stack.push(local(in.operands()[0], paramCount, isStatic));
                case 0x60 -> { if (!bin(stack, "+")) return null; }
                case 0x64 -> { if (!bin(stack, "-")) return null; }
                case 0x68 -> { if (!bin(stack, "*")) return null; }
                case 0x6c -> { if (!bin(stack, "/")) return null; }
                case 0x70 -> { if (!bin(stack, "%")) return null; }
                case 0x74 -> { // ineg
                    if (stack.isEmpty()) return null;
                    stack.push("-" + stack.pop());
                }
                case 0xac, 0xb0 -> {
                    return stack.isEmpty() ? null : stack.pop();
                }
                case 0xb1 -> {
                    return "";
                }
                default -> { return null; }
            }
        }
        return null;
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

    private static String local(int idx, int paramCount, boolean isStatic) {
        if (isStatic) return "arg" + idx;
        if (idx == 0) return "this";
        return "arg" + (idx - 1);
    }

    private static String loadValue(BytecodeReader.Insn in, int paramCount, boolean isStatic) {
        return switch (in.opcode()) {
            case 0x1a, 0x1b, 0x1c, 0x1d -> local(in.opcode() - 0x1a, paramCount, isStatic);
            case 0x2a, 0x2b, 0x2c, 0x2d -> local(in.opcode() - 0x2a, paramCount, isStatic);
            case 0x15, 0x19 -> local(in.operands()[0], paramCount, isStatic);
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
}