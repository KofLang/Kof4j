package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bytecode → Kof expression recovery (decompiler, Fase E — DECOMPILER.md).
 *
 * Reconstrói o corpo de métodos linear (sem branches/loops) usando uma pilha
 * simbólica: constantes, loads de local e aritmética básica viram uma
 * expressão Kof idiomática. Métodos fora desse subconjunto devolvem
 * {@code null}, e o decompiler cai no stub honesto {@code throw "body not
 * recovered"} — nunca fabricamos comportamento (LEGACY_IR).
 */
final class BytecodeDecoder {

    private BytecodeDecoder() {
    }

    /**
     * Recupera a expressão do corpo de um método linear.
     * @return a expressão Kof, {@code ""} para corpo vazio (void), ou {@code null} se não recuperável.
     */
    static String recoverExpression(byte[] code, String[] cp, int paramCount, boolean isStatic) {
        Deque<String> stack = new ArrayDeque<>();
        for (int pc = 0; pc < code.length; ) {
            int op = code[pc] & 0xFF;
            switch (op) {
                // ── integer constants ──
                case 0x02 -> { push(stack, "-1"); pc++; }
                case 0x03 -> { push(stack, "0"); pc++; }
                case 0x04 -> { push(stack, "1"); pc++; }
                case 0x05 -> { push(stack, "2"); pc++; }
                case 0x06 -> { push(stack, "3"); pc++; }
                case 0x07 -> { push(stack, "4"); pc++; }
                case 0x08 -> { push(stack, "5"); pc++; }
                // ── long/float/double constants (não decompilados por ora) ──
                case 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f -> { return null; }
                // ── bipush / sipush ──
                case 0x10 -> {
                    if (pc + 1 >= code.length) return null;
                    push(stack, String.valueOf((byte) code[pc + 1]));
                    pc += 2;
                }
                case 0x11 -> {
                    if (pc + 2 >= code.length) return null;
                    int v = (short) (((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF));
                    push(stack, String.valueOf(v));
                    pc += 3;
                }
                // ── ldc / ldc_w / ldc2_w ──
                case 0x12 -> {
                    if (pc + 1 >= code.length) return null;
                    String c = ldc(cp, code[pc + 1] & 0xFF);
                    if (c == null) return null;
                    push(stack, c);
                    pc += 2;
                }
                case 0x13 -> {
                    if (pc + 2 >= code.length) return null;
                    String c = ldc(cp, ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF));
                    if (c == null) return null;
                    push(stack, c);
                    pc += 3;
                }
                // ── loads (int/aload iload_0..3) ──
                case 0x1a -> { push(stack, local(0, paramCount, isStatic)); pc++; }
                case 0x1b -> { push(stack, local(1, paramCount, isStatic)); pc++; }
                case 0x1c -> { push(stack, local(2, paramCount, isStatic)); pc++; }
                case 0x1d -> { push(stack, local(3, paramCount, isStatic)); pc++; }
                case 0x2a -> { push(stack, local(0, paramCount, isStatic)); pc++; }
                case 0x2b -> { push(stack, local(1, paramCount, isStatic)); pc++; }
                case 0x2c -> { push(stack, local(2, paramCount, isStatic)); pc++; }
                case 0x2d -> { push(stack, local(3, paramCount, isStatic)); pc++; }
                case 0x15 -> { // iload idx
                    if (pc + 1 >= code.length) return null;
                    push(stack, local(code[pc + 1] & 0xFF, paramCount, isStatic));
                    pc += 2;
                }
                case 0x19 -> { // aload idx
                    if (pc + 1 >= code.length) return null;
                    push(stack, local(code[pc + 1] & 0xFF, paramCount, isStatic));
                    pc += 2;
                }
                // ── integer arithmetic ──
                case 0x60 -> { if (!binary(stack, "+")) return null; pc++; }
                case 0x64 -> { if (!binary(stack, "-")) return null; pc++; }
                case 0x68 -> { if (!binary(stack, "*")) return null; pc++; }
                case 0x6c -> { if (!binary(stack, "/")) return null; pc++; }
                case 0x70 -> { if (!binary(stack, "%")) return null; pc++; }
                // ── returns ──
                case 0xac -> { // ireturn
                    pc++;
                    String v = stack.isEmpty() ? null : stack.pop();
                    return v;
                }
                case 0xb0 -> { // areturn
                    pc++;
                    return stack.isEmpty() ? null : stack.pop();
                }
                case 0xb1 -> { // return (void)
                    pc++;
                    return "";
                }
                default -> {
                    // opcode fora do subconjunto linear → não recuperável
                    return null;
                }
            }
        }
        return null;
    }

    private static void push(Deque<String> stack, String v) {
        stack.push(v);
    }

    private static boolean binary(Deque<String> stack, String op) {
        if (stack.size() < 2) return false;
        String b = stack.pop();
        String a = stack.pop();
        stack.push("(" + a + " " + op + " " + b + ")");
        return true;
    }

    /** Nome do local (índice de bytecode) como variável Kof. */
    private static String local(int idx, int paramCount, boolean isStatic) {
        if (isStatic) return "arg" + idx;
        if (idx == 0) return "this";
        return "arg" + (idx - 1);
    }

    /** Resolve um operando ldc do constant pool. */
    private static String ldc(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String entry = cp[idx];
        if (entry.startsWith("#")) {
            try {
                int ref = Integer.parseInt(entry.substring(1));
                if (ref <= 0 || ref >= cp.length || cp[ref] == null) return null;
                return "\"" + cp[ref] + "\"";
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // Integer/Float literal armazenado como string numérica
        return entry;
    }
}