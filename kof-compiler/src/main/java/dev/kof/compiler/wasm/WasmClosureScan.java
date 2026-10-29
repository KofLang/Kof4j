package dev.kof.compiler.wasm;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.Type;
import dev.kof.compiler.ClassLayout;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.kof.compiler.wasm.WasmScalarOps.ownerSimpleName;
import static dev.kof.compiler.wasm.WasmScalarOps.typeName;
import static dev.kof.compiler.wasm.WasmTypeOracle.isScalarSignature;
import static dev.kof.compiler.wasm.WasmTypeOracle.operandTypeBefore;
import static dev.kof.compiler.wasm.WasmTypeOracle.printableScalar;

/**
 * CLASS-2b (29/10, #776 — split check_500): o FECHO de chamadas do emit
 * (a caminhada de alcançabilidade a partir das entradas escalares + do
 * main). O mesmo código movido do WasmBackend (a classe nova >= 600 = o
 * split obrigatório). Devolve o conjunto IDENTITY dos métodos alcançados
 * (o main + os helpers escalares + os de assinatura de classe + os
 * métodos/overrides das classes).
 */
final class WasmClosureScan {

    private WasmClosureScan() {
    }

    static Set<IRMethod> scan(IRModule module, Map<String, ClassLayout> records, boolean usesRecords,
            Map<String, ClassLayout> classes, boolean usesClasses,
            Map<String, IRMethod> decls, List<IRMethod> entries, IRMethod mainDecl,
            boolean wasi, boolean printIntrinsic) {
        // fecho transitivo de chamadas a partir das entradas escalares: qualquer
        // declaracao FORA do subset que seja de fato alcancada recusa honesto
        // (WASM002) — helper injetado pela stdlib que o programa nao usa nao
        // conta; nunca sucesso parcial silencioso (Q7)
        Deque<IRMethod> work = new ArrayDeque<>(entries);
        if (wasi && mainDecl != null) work.add(mainDecl); // corpo de main entra no fecho
        Set<IRMethod> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!work.isEmpty()) {
            IRMethod cur = work.poll();
            if (!seen.add(cur)) continue;
            for (IRBasicBlock bb : cur.basicBlocks()) {
                List<KofOperation> cops = bb.operations();
                for (int ci = 0; ci < cops.size(); ci++) {
                    var op = cops.get(ci);
                    if (!(op instanceof KofCall kc)) continue;
                    if (wasi && printIntrinsic && ("valueOf".equals(kc.methodName())
                            || "kof_string_concat".equals(kc.methodName())
                            || "kof_string_equals".equals(kc.methodName())
                            || ("length".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("isEmpty".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("equals".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("concat".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("startsWith".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("endsWith".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("indexOf".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("lastIndexOf".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("charAt".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("substring".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("trim".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("compareTo".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("contains".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("replace".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            // Fase E (26/10, #776): o case fold Unicode (a mesma
                            // semantica D-STR-UNICODE do nativo)
                            || ("toUpperCase".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            || ("toLowerCase".equals(kc.methodName())
                                && "string".equalsIgnoreCase(typeName(kc.ownerType())))
                            // Fase E (26/10, #776): a familia INT do math — o
                            // runtime WASI implementa aritmetica i64 pura
                            || (wasi && WasmMathRuntime.NAMES.contains(kc.methodName()))
                            // Fase E (27/10, #776): a fatia LIST<Int> — os
                            // quatro nomes de desugar do listOf tratados na
                            // emissao (WasmCallCode/WasmListRuntime)
                            || (wasi && WasmListRuntime.NAMES.contains(kc.methodName()))
                            // Fase E (27/10, #776): a fatia MAP<String, Int>
                            || (wasi && WasmMapRuntime.NAMES.contains(kc.methodName()))
                            // Fase E (27/10, #776): a fatia SET<Int>
                            || (wasi && WasmSetRuntime.NAMES.contains(kc.methodName()))
                            // Fase E (27/10, #776): a caixa de erasure §284
                            || (wasi && WasmBoxRuntime.NAMES.contains(kc.methodName())))) {
                        continue; // wrapper intrinseco de String tratado na emissao
                    }
                    if (wasi && usesRecords && "kofRecordEq".equals(kc.methodName())) {
                        continue; // igualdade de record: fold inline no call site (15.3d inc2 C2)
                    }
                    if (wasi && usesRecords && kc.kind() == KofCallKind.CONSTRUCTOR
                            && "<init>".equals(kc.methodName())
                            && records.containsKey(ownerSimpleName(kc.ownerType()))) {
                        continue; // construtor de record inlinado (15.3d)
                    }
                    if (wasi && usesRecords && (kc.kind() == KofCallKind.INSTANCE
                            || kc.kind() == KofCallKind.INTERFACE)
                            && records.containsKey(ownerSimpleName(kc.ownerType()))
                            && ("toString".equals(kc.methodName())
                                || "equals".equals(kc.methodName()))) {
                        continue; // toString/equals de record inlinados (15.3d inc2 fatia C)
                    }
                    boolean isPrintCall = wasi && printIntrinsic
                            && "println".equals(kc.methodName());
                    if (isPrintCall) {
                        String t = null;
                        List<KofOperation> flat = new java.util.ArrayList<>();
                        for (IRBasicBlock scan : cur.basicBlocks()) {
                            flat.addAll(scan.operations());
                        }
                        for (int si = 0; si < flat.size(); si++) {
                            if (flat.get(si) == kc) {
                                t = operandTypeBefore(flat, si);
                                break;
                            }
                        }
                        boolean recOk = usesRecords && t != null && records.containsKey(t);
                        if (t == null || (!printableScalar(t) && !recOk)) {
                            throw new WasmUnsupportedException("println '" + t
                                    + "' fora da fatia 1 da unidade 15.3 (WASM002) — o host de "
                                    + "strings de operacoes/records/colecoes/double chega com o runtime"
                                    + " docs/wasm-wasi-plan.md (#776)");
                        }
                        continue;
                    }
                    // CLASS-1 (28/10, #776): as chamadas de classe (o ctor
                    // <init> + os métodos de instância) resolvem pelo dono
                    // QUALIFICADO e entram no fecho
                    if (wasi && usesClasses
                            && (kc.kind() == KofCallKind.CONSTRUCTOR
                                || kc.kind() == KofCallKind.INSTANCE
                                || kc.kind() == KofCallKind.INTERFACE)) {
                        String own = ownerSimpleName(kc.ownerType());
                        if (classes.containsKey(own)) {
                            IRMethod cm = decls.get(own + "." + kc.methodName());
                            if (cm == null) {
                                throw new WasmUnsupportedException("método '" + kc.methodName()
                                        + "' de '" + own + "' nao declarado (WASM002)"
                                        + " — docs/wasm-wasi-plan.md (#776)");
                            }
                            work.add(cm);
                            // CLASS-2 (28/10, #776): as chamadas da cadeia de
                            // despacho = GERADAS em compile-time (o fonte nunca
                            // chama Dog.speak!) — os overrides das
                            // implementações entram no fecho
                            for (String impl : WasmClassScan.implsOf(module, classes, own)) {
                                IRMethod im = decls.get(impl + "." + kc.methodName());
                                if (im != null) work.add(im);
                            }
                            continue;
                        }
                    }
                    IRMethod t = decls.get(kc.methodName());
                    if (t == null) {
                        throw new WasmUnsupportedException("chamada '" + kc.methodName()
                                + "' nao resolvida no subset escalar da unidade 15.2 (WASM002) —"
                                + " IO/stdlib/intrinsics recebem host nas unidades 15.3+;"
                                + " docs/wasm-wasi-plan.md (#776)");
                    }
                    if (!isScalarSignature(t) && !WasmClassScan.classScalarSignature(t, classes)
                            && !(wasi && t == mainDecl)) {
                        throw new WasmUnsupportedException("chamada '" + kc.methodName()
                                + "' fora do subset escalar da unidade 15.2 (WASM002) —"
                                + " classes/colecoes/IO/void estao fora; main com host chega em 15.3;"
                                + " docs/wasm-wasi-plan.md (#776)");
                    }
                    work.add(t);
                }
            }
        }
        return seen;
    }
}
