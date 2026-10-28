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
                // Fase E (27/10, #776): o produtor CAIXA (join heterogeneo do
                // if-expression) — imprimivel via kof.boxToStr no emitPrintln.
                if (WasmBoxRuntime.NAMES.contains(kc.methodName())) {
                    return "box";
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

    /** 15.3d fatia F: algum `==`/`!=` de String foi baixado para `kof_string_equals`.
     *  Cobre a comparacao entre duas VARIAVEIS, que nao passa por
     *  {@link #usesStringOps} (nao ha literal de string no codigo). */
    static boolean usesStringEquals(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && "kof_string_equals".equals(kc.methodName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 15.3d fatia F face 2b: algum `String.length()` (ou `.length` como campo)
     *  foi baixado para `kof.strLen`. */
    static boolean usesStringLength(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && "length".equals(kc.methodName())
                            && "string".equalsIgnoreCase(typeName(kc.ownerType()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 15.3d fatia F face 2c: faces de String cujo helper e' `kof.strIsEmpty` /
     *  `kof.strEq` / `kof.strConcat` conforme o metodo chamado. */
    /** Fase E (27/10, #776): algum String.valueOf com parametro Char (o
     *  kof.strChar do runtime atende o concat/valor — o mesmo dos campos
     *  Char de record). */
    static boolean usesCharValueOf(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && "valueOf".equals(kc.methodName())
                            && !kc.parameterTypes().isEmpty()
                            && kc.parameterTypes().get(0) instanceof Type.PrimitiveType pt
                            && "char".equalsIgnoreCase(pt.name())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Fase E (27/10, #776): algum String.valueOf com parametro Double (o
     *  kof.doubleToStr do Schubfach atende o concat/valor — o mesmo do
     *  println; o registro das tabelas/funcoes segue este gancho). O
     *  parametro pode vir boxed/ausente (o desugar do concat com literal
     *  negativo) — o MESMO operandTypeBefore do despacho decide. */
    static boolean usesDoubleValueOf(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                List<KofOperation> ops = bb.operations();
                for (int i = 0; i < ops.size(); i++) {
                    if (!(ops.get(i) instanceof KofCall kc)
                            || !"valueOf".equals(kc.methodName())) {
                        continue;
                    }
                    Type p0 = kc.parameterTypes().isEmpty() ? null : kc.parameterTypes().get(0);
                    String vt = (p0 instanceof Type.PrimitiveType)
                            ? typeName(p0)
                            : operandTypeBefore(ops, i);
                    if ("double".equalsIgnoreCase(vt)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** FATIA R-CHAR (28/10, #776): algum String.replace com parametro Char
     *  (a forma (Char, Char)) — o registro do kof.strReplaceChar + do
     *  kof.strChar segue este gancho (o programa pode NAO ter valueOf de
     *  char nem records, entao o strChar nao entraria por eles). */
    static boolean usesStringReplaceChar(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (!(op instanceof KofCall kc)
                            || !"replace".equals(kc.methodName())) {
                        continue;
                    }
                    for (Type pt : kc.parameterTypes()) {
                        if (pt instanceof Type.PrimitiveType prim
                                && "char".equalsIgnoreCase(prim.name())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }


    /** Fase E (27/10, #776): algum desugar de List (kof_list_*) no metodo —
     *  o registro da familia WasmListRuntime segue este gancho. */
    /** ARRAY-1 (27/10): o modulo aloca array? (o heapAlloc compartilhado
     *  entra junto — os ops de array sao memoria pura, so o alocador falta). */
    static boolean usesArray(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofNewArray) return true;
                }
            }
        }
        return false;
    }

    static boolean usesList(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && WasmListRuntime.NAMES.contains(kc.methodName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Fase E (27/10, #776): algum desugar de Map (kof_map_*) no metodo —
     *  o registro da familia WasmMapRuntime (com a caixa + strEq) segue
     *  este gancho. */
    static boolean usesMap(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && WasmMapRuntime.NAMES.contains(kc.methodName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Fase E (27/10, #776): algum desugar de Set (kof_set_*) no metodo —
     *  o registro da familia WasmSetRuntime segue este gancho. */
    static boolean usesSet(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && WasmSetRuntime.NAMES.contains(kc.methodName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean usesStringMethod(Iterable<IRMethod> ms, String method) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && method.equals(kc.methodName())
                            && "string".equalsIgnoreCase(typeName(kc.ownerType()))) {
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
        if ("box".equals(name.toLowerCase())) return true; // caixa §284 (27/10)
        return switch (name.toLowerCase()) {
            // double: so' o subconjunto INTEGRAL esta implementado (kof.writeDouble
            // trapa em valor fracionario) — o Schubfach completo e' multi-ciclo.
            case "int", "long", "bool", "boolean", "char", "double" -> true;
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
