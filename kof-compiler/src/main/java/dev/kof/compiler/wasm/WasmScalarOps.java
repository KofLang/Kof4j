package dev.kof.compiler.wasm;

import dev.kof.compiler.*;

import java.util.*;

/**
 * Helpers de emissao escalar compartilhados pelo backend WASM/WASI
 * (TIER 15 unidade 15.2/15.3, issue #776): literais, operadores binarios/
 * unarios e o mapeamento de tipos do subset (Int=i64, D-WASM-02). Fora do
 * subset recusa `WasmUnsupportedException` (WASM002), nunca fallback (R6/Q7).
 */
public final class WasmScalarOps {

    private WasmScalarOps() {
    }

    static void call(String fn, List<WasmInstr> out) {
        out.add(new WasmInstr.Call(fn));
    }

    static void emitLiteral(Object val, Type type, List<WasmInstr> out) {
        if (val instanceof Boolean b) {
            out.add(new WasmInstr.Const(0, b ? 1 : 0));
        } else if (val instanceof Character c) {
            out.add(new WasmInstr.Const(0, c));
        } else if (val instanceof Number n) {
            if (type instanceof Type.PrimitiveType pt && "double".equalsIgnoreCase(pt.name())) {
                out.add(new WasmInstr.Const(n.doubleValue()));
            } else if (type instanceof Type.PrimitiveType pt
                    && ("bool".equalsIgnoreCase(pt.name()) || "boolean".equalsIgnoreCase(pt.name())
                        || "char".equalsIgnoreCase(pt.name()))) {
                out.add(new WasmInstr.Const(0, n.longValue())); // i32 (mapType do subset)
            } else {
                out.add(new WasmInstr.Const(1, n.longValue()));
            }
        } else {
            throw new WasmUnsupportedException("literal fora do subset escalar (WASM002): " + val
                    + " — strings/records/colecoes recebem o runtime das unidades 15.3+;"
                    + " docs/wasm-wasi-plan.md (#776)");
        }
    }

    static void emitBinaryOp(KofBinaryOp op, String type, List<WasmInstr> out) {
        boolean dbl = "double".equalsIgnoreCase(type);
        if (dbl) {
            switch (op) {
                case ADD -> out.add(new WasmInstr.Simple(0xa0, "f64.add"));
                case SUB -> out.add(new WasmInstr.Simple(0xa1, "f64.sub"));
                case MUL -> out.add(new WasmInstr.Simple(0xa2, "f64.mul"));
                case DIV -> out.add(new WasmInstr.Simple(0xa3, "f64.div"));
                case EQ -> out.add(new WasmInstr.Simple(0x61, "f64.eq"));
                case NE -> out.add(new WasmInstr.Simple(0x62, "f64.ne"));
                case LT -> out.add(new WasmInstr.Simple(0x63, "f64.lt"));
                case GT -> out.add(new WasmInstr.Simple(0x64, "f64.gt"));
                case LE -> out.add(new WasmInstr.Simple(0x65, "f64.le"));
                case GE -> out.add(new WasmInstr.Simple(0x66, "f64.ge"));
                default -> throw new WasmUnsupportedException("op f64 '" + op + "' fora do subset 15.2 (WASM002)");
            }
            return;
        }
        switch (op) {
            case ADD -> out.add(new WasmInstr.Simple(0x7c, "i64.add"));
            case SUB -> out.add(new WasmInstr.Simple(0x7d, "i64.sub"));
            case MUL -> out.add(new WasmInstr.Simple(0x7e, "i64.mul"));
            case DIV -> out.add(new WasmInstr.Simple(0x7f, "i64.div_s"));
            case MOD -> out.add(new WasmInstr.Simple(0x81, "i64.rem_s"));
            case AND -> out.add(new WasmInstr.Simple(0x83, "i64.and"));
            case OR -> out.add(new WasmInstr.Simple(0x84, "i64.or"));
            case XOR -> out.add(new WasmInstr.Simple(0x85, "i64.xor"));
            case SHL -> out.add(new WasmInstr.Simple(0x86, "i64.shl"));
            case SHR -> out.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
            case USHR -> out.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
            case EQ -> out.add(new WasmInstr.Simple(0x51, "i64.eq"));
            case NE -> out.add(new WasmInstr.Simple(0x52, "i64.ne"));
            case LT -> out.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
            case GT -> out.add(new WasmInstr.Simple(0x55, "i64.gt_s"));
            case LE -> out.add(new WasmInstr.Simple(0x57, "i64.le_s"));
            case GE -> out.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
            default -> throw new WasmUnsupportedException("op '" + op + "' fora do subset 15.2 (WASM002)");
        }
    }

    static void emitUnary(KofUnary un, List<WasmInstr> out) {
        String t = typeName(un.operandType());
        switch (un.op()) {
            case NEG -> {
                if ("double".equalsIgnoreCase(t)) {
                    out.add(new WasmInstr.Simple(0x9a, "f64.neg"));
                } else {
                    out.add(new WasmInstr.NegTop());
                }
            }
            case NOT -> out.add(new WasmInstr.Simple(0x45, "i32.eqz"));
            default -> throw new WasmUnsupportedException("unario '" + un.op()
                    + "' fora do subset 15.2 (WASM002) — conversions pertencem as unidades 15.3+");
        }
    }

    static WasmInstr cmp(KofConditionalJump cj, String suffix, int i64Op, int f64Op) {
        String tn = typeName(cj.operandType());
        if (cj.operandType() instanceof Type.ClassType) {
            int op = switch (suffix) {
                case "eq" -> 0x46;
                case "ne" -> 0x47;
                default -> throw new WasmUnsupportedException("comparacao de handle '" + suffix
                        + "' fora do subset (WASM002) — docs/wasm-wasi-plan.md (#776)");
            };
            return new WasmInstr.Simple(op, "i32." + suffix);
        }
        if ("bool".equalsIgnoreCase(tn) || "boolean".equalsIgnoreCase(tn)) {
            // bool do WASI empilha i32; apenas eq/ne fazem sentido
            return new WasmInstr.Simple("eq".equals(suffix) ? 0x46 : "ne".equals(suffix) ? 0x47 : -1,
                    "i32." + suffix);
        }
        boolean dbl = "double".equalsIgnoreCase(tn);
        int op = dbl ? f64Op : i64Op;
        String name = (dbl ? "f64." : "i64.") + suffix.replace("_s", "");
        return new WasmInstr.Simple(op, name);
    }


    static String ownerSimpleName(Type t) {
        if (t instanceof Type.ClassType ct) return ct.name();
        return typeName(t);
    }

    static String typeName(Type t) {
        if (t instanceof Type.PrimitiveType pt) return pt.name();
        if (t instanceof Type.ClassType ct) {
            if ("String".equals(ct.name()) && "java.lang".equals(ct.packageName())) {
                return "string"; // 15.3b: literal String na fatia de stdout (issue #776)
            }
            if ("java.lang".equals(ct.packageName())) {
                return ct.name().toLowerCase(); // Object -> "object" (handle i32)
            }
            return ct.name(); // 15.3d: record = nome simples (o mapa records decide)
        }
        return t.toString();
    }

    static int mapType(String name, String context, String role) {
        return switch (name.toLowerCase()) {
            case "int", "long" -> WasmFunc.TYPE_I64;
            case "double" -> WasmFunc.TYPE_F64;
            case "bool", "boolean", "char", "string" -> WasmFunc.TYPE_I32; // string = handle i32 (15.3c)
            case "object" -> WasmFunc.TYPE_I32; // handle/opaque i32 (15.3d record temp)
            default -> throw new WasmUnsupportedException(role + " '" + name + "' em '" + context
                    + "' fora do subset escalar 15.2 (WASM002) — docs/wasm-wasi-plan.md (#776)");
        };
    }

    static boolean isWide(Type t) {
        return t instanceof Type.PrimitiveType pt
                && ("long".equalsIgnoreCase(pt.name()) || "double".equalsIgnoreCase(pt.name()));
    }
}
