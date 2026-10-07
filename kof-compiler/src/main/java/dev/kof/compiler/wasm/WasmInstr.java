package dev.kof.compiler.wasm;

import java.util.Locale;

/**
 * WasmInstr — instrucoes do subset 15.2 com UMA representacao. O funcidx de
 * `call` e resolvido pelo WasmModule na serializacao (nome -> indice), o que
 * mantem o lowering independente da ordem final do modulo.
 */
public abstract sealed class WasmInstr permits WasmInstr.Const, WasmInstr.Local,
        WasmInstr.Simple, WasmInstr.Blocking, WasmInstr.Branch, WasmInstr.Call, WasmInstr.NegTop {

    /** @param funcIdx mapa nome→indice para resolver `call` (null quando nao ha calls). */
    public abstract void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> funcIdx);

    /** Depuração textual (nunca produto; plano §25 define o `--emit=wat` futuro). */
    public abstract void wat(StringBuilder sb, int indent);

    static void pad(StringBuilder sb, int n) {
        sb.append("    ".repeat(n));
    }

    /** i32.const / i64.const / f64.const */
    public static final class Const extends WasmInstr {
        public final int kind; // 0 i32, 1 i64, 2 f64
        public final long i;
        public final double d;

        public Const(int kind, long i) { this.kind = kind; this.i = i; this.d = 0; }
        public Const(double d) { this.kind = 2; this.i = 0; this.d = d; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            switch (kind) {
                case 0 -> { out.write(0x41); WasmBinary.writeSleb(out, i); }
                case 1 -> { out.write(0x42); WasmBinary.writeSleb(out, i); }
                default -> {
                    out.write(0x44);
                    long bits = Double.doubleToLongBits(d);
                    for (int b = 0; b < 8; b++) out.write((int) ((bits >>> (8 * b)) & 0xff));
                }
            }
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            if (kind == 2) sb.append("(f64.const ").append(String.format(Locale.US, "%s", d)).append(")\n");
            else sb.append(kind == 0 ? "(i32.const " : "(i64.const ").append(i).append(")\n");
        }
    }

    /** local.get / local.set */
    public static final class Local extends WasmInstr {
        public static final int GET = 0, SET = 1;
        public final int op;
        public final int idx;
        public final String name; // rotulo de depuracao

        public Local(int op, int idx, String name) { this.op = op; this.idx = idx; this.name = name; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            out.write(op == GET ? 0x20 : 0x21);
            WasmBinary.writeUleb(out, idx);
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append(op == GET ? "(local.get $" : "(local.set $").append(name).append(")\n");
        }
    }

    /** instrucoes sem immediato */
    public static final class Simple extends WasmInstr {
        public final int opcode;
        public final String watName;

        public Simple(int opcode, String watName) { this.opcode = opcode; this.watName = watName; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            out.write(opcode);
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append('(').append(watName).append(")\n");
        }
    }

    /** negacao de topo de pilha sem local: (i64.const -1) (i64.mul) */
    public static final class NegTop extends WasmInstr {
        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            out.write(0x42); WasmBinary.writeSleb(out, -1); out.write(0x7e);
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append("(i64.const -1) (i64.mul)  ;; neg de topo\n");
        }
    }

    /** block / loop / if / else / end (blocktype VOID nos dispatchers) */
    public static final class Blocking extends WasmInstr {
        public static final int LOOP = 0, IF = 1, ELSE = 2, END = 3;
        public final int op;
        public final String label;
        public final int blockType; // 0x40 void

        public Blocking(int op, String label, int blockType) { this.op = op; this.label = label; this.blockType = blockType; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            switch (op) {
                case LOOP -> { out.write(0x03); out.write(blockType); }
                case IF -> { out.write(0x04); out.write(blockType); }
                case ELSE -> out.write(0x05);
                default -> out.write(0x0b);
            }
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            switch (op) {
                case LOOP -> sb.append("(loop").append(label != null ? " $" + label : "").append("  ;; abrir\n");
                case IF -> sb.append("(if  ;; abrir\n");
                case ELSE -> sb.append("(else)\n");
                default -> sb.append(";; fechar\n");
            }
        }
    }

    /** br <label> | return */
    public static final class Branch extends WasmInstr {
        public final String label;
        public final int depth;
        public final boolean isReturn;

        public Branch(String label, int depth) { this.label = label; this.depth = depth; this.isReturn = false; }
        private Branch() { this.label = null; this.depth = 0; this.isReturn = true; }

        public static Branch ret() { return new Branch(); }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            if (isReturn) out.write(0x0f);
            else { out.write(0x0c); WasmBinary.writeUleb(out, depth); }
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append(isReturn ? "(return)\n" : "(br $" + label + ")\n");
        }
    }

    /** call <funcidx-resolvido-por-nome> */
    public static final class Call extends WasmInstr {
        public final String targetName;

        public Call(String targetName) { this.targetName = targetName; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> funcIdx) {
            Integer idx = funcIdx == null ? null : funcIdx.get(targetName);
            if (idx == null) {
                throw new WasmUnsupportedException("chamada a funcao fora do subset escalar 15.2"
                        + " (WASM002): '" + targetName + "' — docs/development/wasm-wasi-plan.md (#776)");
            }
            out.write(0x10);
            WasmBinary.writeUleb(out, idx);
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append("(call $").append(targetName).append(")\n");
        }
    }
}
