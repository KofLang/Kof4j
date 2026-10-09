package dev.kof.compiler.wasm;

import java.util.Locale;

/**
 * WasmInstr — instrucoes do subset 15.2 com UMA representacao. O funcidx de
 * `call` e resolvido pelo WasmModule na serializacao (nome -> indice), o que
 * mantem o lowering independente da ordem final do modulo.
 */
public abstract sealed class WasmInstr permits WasmInstr.Const, WasmInstr.Local,
        WasmInstr.Simple, WasmInstr.Blocking, WasmInstr.Branch, WasmInstr.Call, WasmInstr.NegTop, WasmInstr.Store8, WasmInstr.I32,
        WasmInstr.Mem, WasmInstr.Global {

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
        public static final int GET = 0, SET = 1, TEE = 2;
        public final int op;
        public final int idx;
        public final String name; // rotulo de depuracao

        public Local(int op, int idx, String name) { this.op = op; this.idx = idx; this.name = name; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            out.write(op == GET ? 0x20 : (op == SET ? 0x21 : 0x22));
            WasmBinary.writeUleb(out, idx);
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append(op == GET ? "(local.get $" : (op == SET ? "(local.set $" : "(local.tee $"))
                  .append(name).append(")\n");
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

    /** i32.store8 em offset estatico: empilha (addr)(value) antes. */
    public static final class Store8 extends WasmInstr {
        public final int offset;

        public Store8(int offset) { this.offset = offset; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            out.write(0x3a);
            WasmBinary.writeUleb(out, 0);
            WasmBinary.writeUleb(out, offset);
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append("(i32.store8 offset=").append(offset).append(")\n");
        }
    }

    /** load/store com memarg estatico (host 15.3: iovec + nwritten; 15.3d: slots i64). */
    public static final class Mem extends WasmInstr {
        public static final int LOAD = 0x28, STORE = 0x36, LOAD8U = 0x2d;
        public static final int LOAD64 = 0x29, STORE64 = 0x37;
        public static final int LOAD_F64 = 0x2b, STORE_F64 = 0x39;
        public final int op;
        public final int offset;

        public Mem(int op, int offset) { this.op = op; this.offset = offset; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            out.write(op);
            WasmBinary.writeUleb(out, align()); // byte 0, i32 2, i64 3
            WasmBinary.writeUleb(out, offset);
        }

        private int align() {
            return switch (op) {
                case LOAD8U -> 0;
                case LOAD64, STORE64, LOAD_F64, STORE_F64 -> 3;
                default -> 2;
            };
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            String name = switch (op) {
                case LOAD -> "(i32.load offset=";
                case STORE -> "(i32.store offset=";
                case LOAD64 -> "(i64.load offset=";
                case STORE64 -> "(i64.store offset=";
                case LOAD_F64 -> "(f64.load offset=";
                case STORE_F64 -> "(f64.store offset=";
                default -> "(i32.load8_u offset=";
            };
            sb.append(name).append(offset).append(")\n");
        }
    }

    /** i32 util do host (add/sub/const ja existem via Simple/Const). */
    /** i32.get / i32.set num global mutavel (heap bump pointer da 15.3c). */
    public static final class Global extends WasmInstr {
        public static final int GET = 0, SET = 1;
        public final int op;
        public final int index;

        public Global(int op, int index) { this.op = op; this.index = index; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> funcIdx) {
            out.write(op == GET ? 0x23 : 0x24);
            WasmBinary.writeUleb(out, index);
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append(op == GET ? "(global.get " : "(global.set ").append(index).append(")\n");
        }
    }

    public static final class I32 extends WasmInstr {
        public final int op;
        public final String name;

        public I32(int op, String name) { this.op = op; this.name = name; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            out.write(op);
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append('(').append(name).append(")\n");
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
        public static final int BLOCK = 4, LOOP = 0, IF = 1, ELSE = 2, END = 3;
        public final int op;
        public final String label;
        public final int blockType; // 0x40 void

        public Blocking(int op, String label, int blockType) { this.op = op; this.label = label; this.blockType = blockType; }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            switch (op) {
                case BLOCK -> { out.write(0x02); out.write(blockType); }
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
        public final boolean cond;

        public Branch(String label, int depth) { this(label, depth, false); }

        public Branch(String label, int depth, boolean cond) {
            this.label = label; this.depth = depth; this.isReturn = false; this.cond = cond;
        }
        private Branch() { this.label = null; this.depth = 0; this.isReturn = true; this.cond = false; }

        public static Branch ret() { return new Branch(); }

        @Override public void encode(java.io.ByteArrayOutputStream out, java.util.Map<String, Integer> f) {
            if (isReturn) out.write(0x0f);
            else { out.write(cond ? 0x0d : 0x0c); WasmBinary.writeUleb(out, depth); }
        }

        @Override public void wat(StringBuilder sb, int n) {
            pad(sb, n);
            sb.append(isReturn ? "(return)\n" : (cond ? "(br_if $" + label + ")\n" : "(br $" + label + ")\n"));
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
