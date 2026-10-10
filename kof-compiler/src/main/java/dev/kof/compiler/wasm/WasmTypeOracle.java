package dev.kof.compiler.wasm;

import dev.kof.compiler.*;

import java.util.*;

import static dev.kof.compiler.wasm.WasmScalarOps.*;

/**
 * Oraculo de tipos do lowering WASI (TIER 15, #776): quem empilha o que, em cada
 * chamada (`operandTypeBefore`), que tipos de println aparecem no programa
 * (`printOperandTypes`), que ops exigem runtime de String, e o que e escalares
 * imprimiveis. Separação de responsabilidade do dispatcher (`WasmLowering`) —
 * gate 500, small-parts.
 */
final class WasmTypeOracle {

    private WasmTypeOracle() {
    }

    static boolean isScalarSignature(IRMethod m) {
        for (Type t : m.parameterTypes()) {
            if (!(t instanceof Type.PrimitiveType pt) || !scalar(pt.name())) return false;
        }
        return m.returnType() instanceof Type.PrimitiveType pr
                && (scalar(pr.name()) || "void".equalsIgnoreCase(pr.name()));
    }

    private static boolean scalar(String name) {
        return switch (name.toLowerCase()) {
            case "int", "long", "double", "bool", "boolean", "char" -> true;
            default -> false;
        };
    }

    /** tipo do ultimo produtor de pilha antes da chamada (IR de pilha:
   receiver do println via KofGetStatic e ignorado). */
    static String operandTypeBefore(List<KofOperation> ops, int idx) {
        for (int i = idx - 1; i >= 0; i--) {
            KofOperation op = ops.get(i);
            if (op instanceof KofGetStatic || op instanceof KofLabel || op instanceof KofPop
                    || op instanceof KofJump || op instanceof KofStoreLocal || op instanceof KofLabel
                    || op instanceof KofConditionalJump || op instanceof KofCheckCast) {
                continue;
            }
            if (op instanceof KofLoadLiteral lit) {
                if (lit.value() == null) return "null";
                return typeName(lit.type());
            }
            if (op instanceof KofLoadLocal ll) {
                if (ll.type() instanceof Type.ClassType ct && "Object".equals(ct.name())
                        && "java.lang".equals(ct.packageName())) {
                    continue; // Object = caixa de valor (bool/String do desugar) — anda p/ o produtor real
                }
                String tn = typeName(ll.type());
                if ("bool".equalsIgnoreCase(tn) || "boolean".equalsIgnoreCase(tn)) {
                    return "bool"; // desugar `==` produz bool na pilha mesmo via temp `int`
                }
                return (ll.type() instanceof Type.ClassType ct) ? ct.name() : tn;
            }
            if (op instanceof KofNewObject no) {
                return (no.type() instanceof Type.ClassType ct) ? ct.name() : typeName(no.type());
            }
            if (op instanceof KofLoadField lf) return WasmRecordOps.isStringField(lf.fieldType())
                    ? "string" : typeName(lf.fieldType());
            if (op instanceof KofArrayLoad al) return typeName(al.elementType());
            if (op instanceof KofArrayLength) return "int";
            if (op instanceof KofBinary bin) return typeName(bin.operandType());
            if (op instanceof KofUnary un) return typeName(un.operandType());
            if (op instanceof KofCall kc) {
                if (kc.kind() == KofCallKind.CONSTRUCTOR && kc.ownerType() instanceof Type.ClassType oct) {
                    return oct.name(); // record: o handle fica na pilha apesar do ret void
                }
                if ("valueOf".equals(kc.methodName()) && !kc.parameterTypes().isEmpty()
                        && kc.parameterTypes().get(0) instanceof Type.PrimitiveType) {
                    return typeName(kc.parameterTypes().get(0));
                }
                if ("valueOf".equals(kc.methodName())) {
                    return operandTypeBefore(ops, i); // Unknown/Object: anda p/ o valor real
                }
                return typeName(kc.returnType());
            }
            return null;
        }
        return null;
    }

    static boolean usesStringOps(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofLoadLiteral c && "string".equalsIgnoreCase(typeName(c.type()))) {
                        return true;
                    }
                    if (op instanceof KofLoadField lf && WasmRecordOps.isStringField(lf.fieldType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean usesStringConcat(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && "kof_string_concat".equals(kc.methodName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static java.util.Set<String> printOperandTypes(Iterable<IRMethod> ms) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (IRMethod m : ms) {
            List<KofOperation> flat = new ArrayList<>();
            for (IRBasicBlock bb : m.basicBlocks()) {
                flat.addAll(bb.operations());
            }
            for (int i = 0; i < flat.size(); i++) {
                if (flat.get(i) instanceof KofCall kc && "println".equals(kc.methodName())) {
                    String t = operandTypeBefore(flat, i);
                    out.add(t == null ? "?" : t.toLowerCase());
                }
            }
        }
        return out;
    }

    static boolean printableScalar(String name) {
        if ("string".equals(name.toLowerCase())) return true; // 15.3b (literal)
        return switch (name.toLowerCase()) {
            case "int", "long", "bool", "boolean", "char" -> true;
            default -> false;
        };
    }

    static boolean printlnFollows(List<KofOperation> ops, int idx) {
        for (int i = idx + 1; i < ops.size(); i++) {
            KofOperation next = ops.get(i);
            if (next instanceof KofGetStatic || next instanceof KofLabel
                    || (next instanceof KofCall nk && "valueOf".equals(nk.methodName()))) {
                continue; // caixa intermediaria da cadeia de valueOf
            }
            return next instanceof KofCall kc && "println".equals(kc.methodName());
        }
        return false;
    }

}
