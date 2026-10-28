package dev.kof.compiler.wasm;

import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofDup2;
import dev.kof.compiler.KofDupX1;
import dev.kof.compiler.KofDupX2;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.Type;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/**
 * FATIA MAP-MX (28/10, #776 — split check_500): a FORMA DOS OPERANDOS de um
 * binário double no momento da emissão — a simulação FORWARD dos traços da
 * pilha (a MESMA lei dos emissores, o subconjunto que decide caixa vs
 * escalar). O binário da dança não conhece a forma do op1 (o rastro morre no
 * segundo operando) — a simulação devolve [op1, op2]. ERRO da simulação =
 * módulo inválido NO VALIDATE (o f64.load sobre um escalar / o f64.add sobre
 * caixas) — nunca saída errada silenciosa (Q7).
 */
final class WasmOperandShape {

    private WasmOperandShape() {
    }

    /** As formas CAIXA (o handle §284 na pilha). */
    static boolean isBox(String shape) {
        return "mapval".equals(shape) || "nullabledouble".equals(shape)
                || "nullablebool".equals(shape)
                || "nullablestring".equals(shape) || "object".equals(shape)
                || "box".equals(shape);
    }

    /**
     * As formas [op1, op2] sob o binário em binIdx (null = a simulação não viu
     * dois valores — o conservador: nenhum desbox).
     */
    static String[] pairAt(List<KofOperation> ops, int binIdx, WasmLowering.Ctx ctx) {
        Deque<String> st = new ArrayDeque<>();
        for (int i = 0; i < binIdx && i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            String pushed = pushedShape(op, ops, i, ctx);
            int pops = popsOf(op);
            for (int k = 0; k < pops && !st.isEmpty(); k++) {
                st.pop();
            }
            if (pushed != null) {
                st.push(pushed);
            }
            if (op instanceof KofDup && !st.isEmpty()) {
                st.push(st.peek());                                  // o dup = a cópia do topo
            }
            if (op instanceof KofDup2 && st.size() >= 2) {
                String a = st.pop();
                String b2 = st.pop();
                st.push(b2);
                st.push(a);
                st.push(b2);
                st.push(a);
            }
            if ((op instanceof KofDupX1 || op instanceof KofDupX2) && !st.isEmpty()) {
                st.push(st.peek());
            }
            if (op instanceof KofConditionalJump) {
                break;                                               // o fim da região linear
            }
        }
        if (st.size() < 2) {
            return null;
        }
        String op2 = st.pop();
        String op1 = st.pop();
        return new String[]{op1, op2};
    }

    /** O que o op EMPURRA (null = nada / consumidor). */
    private static String pushedShape(KofOperation op, List<KofOperation> ops, int i,
                                      WasmLowering.Ctx ctx) {
        if (op instanceof KofLoadLocal ll) {
            String lt = ctx.localNullableTrace.get(ll.index());
            return lt != null ? lt : typeName(ll.type());
        }
        if (op instanceof KofLoadLiteral lit) {
            if (lit.value() == null) return "null";
            if (lit.value() instanceof String) return "string";
            return typeName(lit.type());
        }
        if (op instanceof KofCall kc) {
            return callShape(kc, ops, i);
        }
        if (op instanceof KofBinary) return typeName(((KofBinary) op).operandType());
        if (op instanceof KofUnary) return typeName(((KofUnary) op).operandType());
        if (op instanceof KofLoadField lf) {
            return WasmRecordOps.isStringField(lf.fieldType()) ? "string" : typeName(lf.fieldType());
        }
        if (op instanceof KofArrayLoad al) return typeName(al.elementType());
        if (op instanceof KofArrayLength) return "int";
        if (op instanceof KofNewObject no) {
            return (no.type() instanceof Type.ClassType ct) ? ct.name() : typeName(no.type());
        }
        return null;                                                 // o store/pop/jump/label/getstatic/checkcast
    }

    /** A forma do RESULTADO da chamada (o subconjunto que decide a dança). */
    private static String callShape(KofCall kc, List<KofOperation> ops, int i) {
        String m = kc.methodName();
        if ("println".equals(m) || "print".equals(m)) {
            return null;                                             // VOID
        }
        if (WasmMapRuntime.NAMES.contains(m)) {
            if ("kof_map_new".equals(m)) return "map";
            if ("kof_map_size".equals(m) || "kof_map_contains".equals(m)) return "int";
            if ("kof_map_keys".equals(m) || "kof_map_values".equals(m)) return "list";
            // put/get/remove: o VALOR anda na pilha (a caixa ou o cru)
            if ("kof_map_get".equals(m)) return "mapval";
            String vt = mapValueTypeName(kc);
            if ("string".equals(vt)) return "nullablestring";
            if ("double".equals(vt)) return "nullabledouble";
            if ("bool".equals(vt)) return "nullablebool";
            return "object";
        }
        if (m.startsWith("kof_math_")) {
            return m.startsWith("kof_math_is") ? "bool" : "double";
        }
        if ("valueOf".equals(m) && WasmTypeOracle.printlnFollows(ops, i)) {
            return null;     // o raw passa (as cadeias do println: NENHUM push)
        }
        if ("valueOf".equals(m) && "String".equals(WasmScalarOps.ownerSimpleName(kc.ownerType()))) {
            // o valueOf SEM println emendado empurra o handle de String
            return "string";
        }
        if (kc.returnType() == null
                || (kc.returnType() instanceof Type.PrimitiveType pt
                    && "void".equalsIgnoreCase(pt.name()))) {
            return null;
        }
        return typeName(kc.returnType());
    }

    private static String mapValueTypeName(KofCall kc) {
        if (kc.ownerType() instanceof Type.ClassType ct && ct.typeArguments().size() > 1) {
            return typeName(ct.typeArguments().get(1));
        }
        return "";
    }

    /** Quantos valores o op CONSUME da pilha (0 = nada). */
    private static int popsOf(KofOperation op) {
        if (op instanceof KofBinary) return 2;
        if (op instanceof KofStoreLocal) return 1;
        if (op instanceof KofPop) return 1;
        if (op instanceof KofUnary) return 1;
        if (op instanceof KofArrayStore) return 3;
        if (op instanceof KofCall kc) {
            int p = kc.parameterTypes().size();
            if (kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE
                    || kc.kind() == KofCallKind.SUPER) {
                p += 1;                                              // o receptor
            }
            return p;
        }
        return 0;
    }
}
