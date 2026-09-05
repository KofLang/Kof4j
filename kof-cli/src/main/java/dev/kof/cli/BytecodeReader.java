package dev.kof.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Decoder de bytecode JVM + CFG para o decompiler (Fase B/C — DECOMPILER.md).
 * Tabela de tamanhos de instrução correta, alvos de branch, blocos básicos com
 * sucessores/predecessores e detecção de back-edge (loop). Switch/`athrow`
 * ficam opacos (caem em stub) por ora. ≤500 linhas, isolado das gigantes.
 */
final class BytecodeReader {

    private BytecodeReader() {
    }

    record Insn(int offset, int opcode, int[] operands, int target) {
        boolean isReturn() { return opcode >= 0xac && opcode <= 0xb1; }
        boolean isGoto() { return opcode == 0xa7 || opcode == 0xc8; }
        boolean isCond() { return (opcode >= 0x99 && opcode <= 0xa6) || opcode == 0xc6 || opcode == 0xc7; }
        boolean isOpaque() { return opcode == 0xaa || opcode == 0xab || opcode == 0xbf || opcode == 0xc4; }
    }

    static int length(int op) {
        return switch (op) {
            case 0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, // bipush, ldc, x-load
                 0x36, 0x37, 0x38, 0x39, 0x3a,                // x-store
                 0xa9, 0xbc -> 2;                            // ret, newarray
            case 0x11, 0x13, 0x14, 0x84,                      // sipush, ldc_w, ldc2_w, iinc
                 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e,          // ifeq..ifle
                 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4,          // if_icmp*
                 0xa5, 0xa6, 0xa7, 0xa8,                      // if_acmp*, goto, jsr
                 0xb2, 0xb3, 0xb4, 0xb5,                      // get/put static/field
                 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xba,          // invokes + new + anewarray + invokedynamic
                 0xc0, 0xc1, 0xc6, 0xc7 -> 3;                // checkcast, instanceof, ifnull, ifnonnull
            case 0xc5 -> 4;                                   // multianewarray
            case 0xb9, 0xc8, 0xc9 -> 5;                       // invokeinterface, goto_w, jsr_w
            case 0xaa, 0xab, 0xc4 -> -1;                      // switch / wide (variável)
            default -> 1;
        };
    }

    static List<Insn> decode(byte[] code) {
        List<Insn> out = new ArrayList<>();
        int pc = 0;
        while (pc < code.length) {
            int op = code[pc] & 0xFF;
            int len = length(op);
            if (len == -1) {
                out.add(new Insn(pc, op, new int[0], -1));    // opaco
                pc = skipVariable(code, pc, op);
                continue;
            }
            int[] operands = new int[0];
            int target = -1;
            if (len == 2) {
                operands = new int[]{code[pc + 1] & 0xFF};
            } else if (len == 3) {
                int w = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
                operands = new int[]{w};
                if (isBranch(op)) {
                    target = pc + (short) w;
                }
            } else if (len == 5 && (op == 0xc8 || op == 0xc9)) {
                target = pc + readInt(code, pc + 1);
            } else if (len == 5 && op == 0xb9) { // invokeinterface: índice CP nos 2 bytes
                operands = new int[]{((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF)};
            }
            out.add(new Insn(pc, op, operands, target));
            pc += len;
        }
        return out;
    }

    private static boolean isBranch(int op) {
        return (op >= 0x99 && op <= 0xa8) || op == 0xc6 || op == 0xc7;
    }

    private static int skipVariable(byte[] code, int pc, int op) {
        if (op == 0xaa) { // tableswitch
            int base = pc;
            int pad = (4 - ((pc + 1) % 4)) % 4;
            pc += 1 + pad;
            readInt(code, pc); pc += 4;   // default
            int low = readInt(code, pc);  pc += 4;
            int high = readInt(code, pc); pc += 4;
            for (int i = 0; i < high - low + 1; i++) { pc += 4; }
            return pc;
        }
        if (op == 0xab) { // lookupswitch
            int pad = (4 - ((pc + 1) % 4)) % 4;
            pc += 1 + pad;
            pc += 4;                     // default
            int npairs = readInt(code, pc); pc += 4;
            pc += npairs * 8;
            return pc;
        }
        // wide: opcode + 1 byte + 2/4 bytes
        return pc + 1 + (op == 0x84 ? 4 : 2);
    }

    private static int readInt(byte[] code, int pc) {
        return (code[pc] << 24) | ((code[pc + 1] & 0xFF) << 16)
             | ((code[pc + 2] & 0xFF) << 8) | (code[pc + 3] & 0xFF);
    }

    // ── CFG ──────────────────────────────────────────────────────────────

    static final class Block {
        final int start;
        int end;
        final List<Integer> succ = new ArrayList<>();
        final List<Integer> pred = new ArrayList<>();
        Block(int start) { this.start = start; }
        @Override public String toString() { return "B[" + start + "," + end + ")->" + succ; }
    }

    /**
     * Blocos básicos + arestas. {@code handlerStarts} são offsets de handlers de
     * exceção (também líderes). Back-edge = aresta para um líder com offset menor.
     */
    static List<Block> cfg(List<Insn> insns, int[] handlerStarts) {
        TreeSet<Integer> leaders = new TreeSet<>();
        leaders.add(insns.get(0).offset());
        for (Insn in : insns) {
            if (in.target() >= 0) leaders.add(in.target());
            // fall-through leader: offset após instrução terminal (goto/return/cond/opaco)
            if (in.isGoto() || in.isReturn() || in.isCond() || in.isOpaque()) {
                int next = in.offset() + (in.isOpaque() ? skipVariableBytes(in) : length(in.opcode()));
                // next não vira líder (a branch já cobre)
            }
            // para cond, o fall-through (pc+len) é líder:
            if (in.isCond()) {
                leaders.add(in.offset() + length(in.opcode()));
            }
        }
        for (int hs : handlerStarts) if (hs >= 0) leaders.add(hs);

        // resolve end de cada bloco como início do próximo líder (em offset de instrução)
        List<Integer> leaderList = new ArrayList<>(leaders);
        List<Block> blocks = new ArrayList<>();
        Map<Integer, Integer> insnLen = new HashMap<>();
        for (Insn in : insns) insnLen.put(in.offset(), in.isOpaque() ? skipVariableBytes(in) : length(in.opcode()));

        for (int i = 0; i < leaderList.size(); i++) {
            int s = leaderList.get(i);
            // end = offset da última instrução do bloco + tamanho = próximo líder (na lista de instruções)
            // procurar a maior instrução com offset >= s e < próximo líder
            int nextLeader = (i + 1 < leaderList.size()) ? leaderList.get(i + 1) : Integer.MAX_VALUE;
            int end = s;
            for (Insn in : insns) {
                if (in.offset() < s) continue;
                if (in.offset() >= nextLeader) break;
                int l = insnLen.getOrDefault(in.offset(), 1);
                end = Math.max(end, in.offset() + l);
            }
            Block b = new Block(s);
            b.end = end;
            blocks.add(b);
        }
        Map<Integer, Block> byStart = new HashMap<>();
        for (Block b : blocks) byStart.put(b.start, b);

        for (Block b : blocks) {
            Insn last = null;
            for (Insn in : insns) {
                if (in.offset() >= b.start && in.offset() < b.end) last = in;
            }
            if (last == null) continue;
            if (last.isGoto()) {
                addEdge(b, byStart, last.target());
            } else if (last.isCond()) {
                addEdge(b, byStart, last.target());
                addEdge(b, byStart, last.offset() + length(last.opcode()));
            } else if (last.isReturn() || last.isOpaque()) {
                // terminal / opaco: sem aresta de saída (switch/catch tratados depois)
            } else {
                addEdge(b, byStart, last.offset() + length(last.opcode()));
            }
        }
        return blocks;
    }

    private static int skipVariableBytes(Insn in) {
        return in.opcode() == 0xaa || in.opcode() == 0xab ? 1 : 4;
    }

    /** True se o bloco é alvo de uma back-edge (header de loop). */
    static boolean isLoopHeader(Block b) {
        for (int p : b.pred) if (p >= b.start) return true;
        return false;
    }

    private static void addEdge(Block from, Map<Integer, Block> byStart, int target) {
        for (Block b : byStart.values()) {
            if (target >= b.start && target < b.end) {
                if (!from.succ.contains(b.start)) from.succ.add(b.start);
                if (!b.pred.contains(from.start)) b.pred.add(from.start);
                return;
            }
        }
        // target fora do método (ex.: return) — ignorado
    }
}