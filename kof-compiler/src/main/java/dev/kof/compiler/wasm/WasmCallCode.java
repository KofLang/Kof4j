package dev.kof.compiler.wasm;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.Type;
import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.KofConditionalJump;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.kof.compiler.wasm.WasmScalarOps.ownerSimpleName;
import static dev.kof.compiler.wasm.WasmScalarOps.typeName;
import static dev.kof.compiler.wasm.WasmTypeOracle.operandTypeBefore;
import static dev.kof.compiler.wasm.WasmTypeOracle.printlnFollows;

/**
 * Fase E (27/10, #776 — split check_500): o DESPACHO de {@code KofCall} do
 * backend WASI, extraído verbatim do {@code WasmLowering.emitPlain} (684 ≥ 600
 * derrubava o gate CI). Responsabilidade única: resolver UMA chamada — records
 * (eq/init/toString), String intrinsics (WasmStrCalls), valueOf (raw + Object→
 * caixa), math family, caixa §284, println e o fallback FUNCTION/STATIC.
 * Comportamento idêntico (freeze rule 3).
 */
final class WasmCallCode {

    private WasmCallCode() {
    }

    /** A chave/o valor do Map pelo type argument do OWNER (medido no IR:
     *  `Map<String, Int>` chega com typeArguments=[String, int]). */
    private static String mapKeyType(KofCall kc) {
        if (kc.ownerType() instanceof Type.ClassType ct && ct.typeArguments().size() > 0) {
            return typeName(ct.typeArguments().get(0));
        }
        return "";
    }

    private static String mapValueType(KofCall kc) {
        if (kc.ownerType() instanceof Type.ClassType ct && ct.typeArguments().size() > 1) {
            return typeName(ct.typeArguments().get(1));
        }
        return "";
    }

    /** O tipo do elemento de SET pelo type argument do OWNER (medido no IR:
     *  `Set<Int>` chega com typeArguments=[int]). */
    private static String elemSet(KofCall kc) {
        if (kc.ownerType() instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return typeName(ct.typeArguments().get(0));
        }
        return "";
    }

    /** O tipo do elemento pelo type argument do OWNER (medido no IR:
     *  `List<Int>` chega com typeArguments=[int]); lista BARE (sem args,
     *  o caso §374) devolve "" — o add/get dela recusa na fatia. */
    private static String listElemType(KofCall kc) {
        if (kc.ownerType() instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return typeName(ct.typeArguments().get(0));
        }
        return "";
    }

    /** A largura do elemento no slot i64: int/long/char viajam i64 DIRETO;
     *  bool e' i32 e estende; string e' o HANDLE estendido; double vai como
     *  BITS (o reinterpret fica no call site). "" = bare (recusa). */
    private static boolean isDirectI64Elem(String elem) {
        return "int".equals(elem) || "long".equals(elem) || "char".equals(elem);
    }

    /** Os elems que andam como HANDLE de referencia (i32 estendido no slot
     *  i64): String e a LISTA ANINHADA (fatia 7) — o mesmo extend/wrap. */
    private static boolean isRefElem(String elem) {
        return "string".equals(elem) || "list".equalsIgnoreCase(elem);
    }

    static void emitCall(KofCall kc, List<KofOperation> ops, int idx,
                         WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (WasmRecordCtor.isRecordEq(ctx, kc) || WasmRecordCtor.isRecordEqualsCall(ctx, kc)) {
            WasmRecordCtor.lowerRecordEq(ctx, kc, out);
        } else if (WasmRecordCtor.isRecordInit(ctx, kc)) {
            WasmRecordCtor.lowerRecordInit(ctx, kc, out);
        } else if (WasmStrCalls.emit(kc, ctx, out)) {
            // chamadas de String (==/!=, length, isEmpty, equals,
            // startsWith, endsWith, concat) — WasmStrCalls
        } else if (ctx.wasi && "valueOf".equals(kc.methodName())
                && printlnFollows(ops, idx)) {
            // String.valueOf(X) seguido de println: o valor bruto ja esta na
            // pilha; pula o valueOf e deixa o helper escalar consumir
            String v = (!kc.parameterTypes().isEmpty()
                    && !(kc.parameterTypes().get(0) instanceof Type.UnknownType))
                    ? typeName(kc.parameterTypes().get(0))
                    : operandTypeBefore(ops, idx);
            if (v == null) {
                // o desugar do == passa o bool por um local `Object` entre blocos;
                // o lastPush do equals/Boolean.valueOf anterior ja diz o tipo real
                v = ctx.lastPush;
            }
            // Fase E (27/10, #776): o MAP.get devolve o HANDLE DA CAIXA (a
            // nullable anda na §284, rastro "mapval") — o rastro TEM de
            // vencer sobre o walk (o NullableType[int] dizia "int" e o
            // writeInt consumia o handle: o validador rejeitou). A cadeia
            // valueOf tem DOIS passes (Integer.valueOf e String.valueOf) —
            // CADA pass normaliza v e RE-ARMA o rastro (o "object" PURO dos
            // records nao entra: o walk do record diz o tipo real e o print
            // usa o toString dele — a regressao dos records mediu isso)
            if ("mapval".equals(ctx.lastPush)) {
                // o valor e' o HANDLE DA CAIXA: normaliza E RE-ARMA (a cadeia
                // valueOf tem dois passes — o segundo walk diz "int" de novo)
                v = "object";
                ctx.lastPush = "mapval";
            } else {
                ctx.lastPush = v;   // o comportamento ORIGINAL (o char/records)
            }
        } else if (ctx.wasi && "valueOf".equals(kc.methodName())
                && "String".equals(ownerSimpleName(kc.ownerType()))) {
            // String.valueOf como VALOR (variavel/concat) — paridade JVM:
            // Int|Long -> kof.intToStr (handle `[len][digits]` no bump heap);
            // String -> identidade (o `+` do JVM reaplica valueOf no handle);
            // outros tipos recusam honesto (WASM002) sem artefato (Q7).
            if ("string".equals(ctx.lastPush)) {
                ctx.lastPush = "string";
            } else if ("Object".equalsIgnoreCase(ctx.lastPush)
                    && (kc.parameterTypes().isEmpty()
                        || !(kc.parameterTypes().get(0) instanceof Type.PrimitiveType))) {
                // Fase E (27/10, #776): valueOf sobre um local OBJECT — o
                // valor e CRU (handle String/record) ou CAIXA §284 (join
                // heterogeneo): kof.boxToStr despacha pelo MAGIC/tag e
                // devolve handle de String nos dois casos. O walk do
                // operandTypeBefore PULA loads Object (compat do desugar
                // bool) e chegaria a um produtor aleatorio — convertido
                // errado era memory fault (medido: "m=" + m trapava).
                out.add(new WasmInstr.Call("kof.boxToStr"));
                ctx.lastPush = "string";
            } else {
                Type p0 = kc.parameterTypes().isEmpty() ? null : kc.parameterTypes().get(0);
                String vt = (p0 instanceof Type.PrimitiveType)
                        ? typeName(p0)
                        : operandTypeBefore(ops, idx);
                ClassLayout vl = ctx.records == null ? null : ctx.records.get(vt);
                if ("string".equalsIgnoreCase(vt)) {
                    ctx.lastPush = "string";
                } else if ("int".equalsIgnoreCase(vt) || "long".equalsIgnoreCase(vt)) {
                    out.add(new WasmInstr.Call("kof.intToStr"));
                    ctx.lastPush = "string";
                } else if ("bool".equalsIgnoreCase(vt) || "boolean".equalsIgnoreCase(vt)) {
                    // "t=" + true — paridade JVM (String.valueOf(boolean)).
                    out.add(new WasmInstr.Call("kof.strBool"));
                    ctx.lastPush = "string";
                } else if ("char".equalsIgnoreCase(vt)) {
                    // Fase E (27/10, #776): "s" + c / String.valueOf(c) — o
                    // MESMO kof.strChar dos campos Char de record (o runtime
                    // ja existia; o gancho e' que faltava). O Char na pilha
                    // e' i64 e o strChar recebe i64 (wrap interno).
                    out.add(new WasmInstr.Call("kof.strChar"));
                    ctx.lastPush = "string";
                } else if ("double".equalsIgnoreCase(vt)) {
                    // Fase E (27/10, #776): "s" + d / String.valueOf(d) — o
                    // MESMO kof.doubleToStr do println (o Schubfach completo);
                    // o registro das tabelas/funcoes segue o gancho
                    // WasmTypeOracle.usesDoubleValueOf.
                    out.add(new WasmInstr.Call("kof.doubleToStr"));
                    ctx.lastPush = "string";
                } else if (vl != null) {
                    WasmRecordCode.emitToString(vl, vt, ctx, out, 0);
                    ctx.lastPush = "string";
                } else {
                    throw new WasmUnsupportedException("String.valueOf('" + vt
                            + "') fora da 15.3d inc2 (WASM002) — docs/wasm-wasi-plan.md (#776)");
                }
            }
        } else if (ctx.wasi && kc.kind() == KofCallKind.STATIC
                && "valueOf".equals(kc.methodName())
                && "Boolean".equals(ownerSimpleName(kc.ownerType()))) {
            // Boolean.valueOf(bool) e identidade no backend WASI (bool i32); se o
            // merge do desugar deixou i64 na pilha (literal int 0/1), wrap p/ i32.
            if (ctx.lastPushWide) {
                out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
            }
            ctx.lastPush = "bool";
            ctx.lastPushWide = false;
        } else if (ctx.wasi && "toString".equals(kc.methodName())
                && ctx.recordOf(kc.ownerType()) != null) {
            ClassLayout sl = ctx.recordOf(kc.ownerType());
            WasmRecordCode.emitToString(sl, sl.className(), ctx, out, 0);
            ctx.lastPush = "string";
        } else if (ctx.wasi && "equals".equals(kc.methodName())
                && ctx.recordOf(kc.ownerType()) != null) {
            throw new WasmUnsupportedException("chamada explicita de 'equals' de record (WASM002)"
                    + " — o backend WASI ainda nao tem dispatch virtual; o `==` de conteudo ja e"
                    + " suportado (15.3d inc2 C2, kofRecordEq inline). docs/wasm-wasi-plan.md (#776)");
        } else if (ctx.wasi && WasmMapRuntime.NAMES.contains(kc.methodName())) {
            // Fase E (27/10, #776): MAP fatia 1 — Map<String, Int> (o par
            // medido): put [h, key(handle), v(i64)] SEM fantasma; get [h,
            // key] -> o HANDLE DA CAIXA (boxInt no hit, 0=null na falta —
            // o rastro "object" manda pro boxToStr, que renderiza "null");
            // size -> i64. O rastro do tee do desugar e' "map".
            switch (kc.methodName()) {
                case "kof_map_new" -> {
                    out.add(new WasmInstr.Call("kof.mapNew"));
                    ctx.lastPush = "map";
                    ctx.lastPushWide = false;
                }
                case "kof_map_put" -> {
                    // os tipos do par vem do IR (o typer ja' garante a
                    // compatibilidade com o owner — a fatia e' <String, Int>)
                    if (!"string".equalsIgnoreCase(mapKeyType(kc))
                            || !"int".equalsIgnoreCase(mapValueType(kc))) {
                        throw new WasmUnsupportedException("Map<"
                                + mapKeyType(kc) + ", " + mapValueType(kc)
                                + "> fora da fatia Map<String, Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    out.add(new WasmInstr.Call("kof.mapPut"));
                    // o runtime DEVOLVE o valor anterior como caixa (o
                    // contrato HashMap.put): a forma VOID do desugar (a链 do
                    // mapOf) dropa aqui; a forma Nullable (o put-declaracao)
                    // deixa o KofPop consumir
                    if (kc.returnType() instanceof Type.PrimitiveType pt
                            && "void".equalsIgnoreCase(pt.name())) {
                        out.add(new WasmInstr.Simple(0x1a, "drop"));
                    }
                    // o rastro volta "map": CADA put da cadeia tem o seu Dup
                    // (o tee deixou o original na pilha e ele permanece — a
                    // MESMA lei do add da lista; o null aqui deixava o
                    // segundo put sem receiver: nothing on stack, medido)
                    ctx.lastPush = "map";
                    ctx.lastPushWide = false;
                }
                case "kof_map_get" -> {
                    if (!"string".equalsIgnoreCase(mapKeyType(kc))) {
                        throw new WasmUnsupportedException("Map.get com chave '"
                                + mapKeyType(kc) + "' fora da fatia Map<String, Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    out.add(new WasmInstr.Call("kof.mapGet"));
                    ctx.lastPush = "mapval";                     // o handle DA CAIXA (0 = null)
                    ctx.lastPushWide = false;
                }
                default -> {                                             // kof_map_size
                    out.add(new WasmInstr.Call("kof.mapSize"));
                    ctx.lastPush = "int";
                    ctx.lastPushWide = true;
                }
            }
        } else if (ctx.wasi && WasmSetRuntime.NAMES.contains(kc.methodName())) {
            // Fase E (27/10, #776): SET fatia 1 — Set<Int> (o par medido):
            // new/add/contains/remove/size. O add DEDUPLICA (o scan de
            // conteudo no runtime — o contrato HashSet); os widths como os
            // da lista (o valor i64 direto); o rastro do tee do desugar e'
            // "set".
            switch (kc.methodName()) {
                case "kof_set_new" -> {
                    out.add(new WasmInstr.Call("kof.setNew"));
                    ctx.lastPush = "set";
                    ctx.lastPushWide = false;
                }
                case "kof_set_add" -> {
                    // o rastro do TOPO no call e' o VALOR (o arg — "int");
                    // o receiver (o tee do desugar) esta' embaixo
                    if (!"int".equals(ctx.lastPush)) {
                        throw new WasmUnsupportedException("Set.add('" + ctx.lastPush
                                + "') fora da fatia Set<Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    if (!"int".equals(elemSet(kc))) {
                        throw new WasmUnsupportedException("Set<" + elemSet(kc)
                                + "> fora da fatia Set<Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    out.add(new WasmInstr.Call("kof.setAdd"));
                    // o runtime DEVOLVE o booleano do HashSet.add (anexou ou
                    // dedup): a forma VOID da cadeia setOf dropa aqui (o
                    // add-declaracao deixa o KofPop consumir)
                    if (kc.returnType() instanceof Type.PrimitiveType pt
                            && "void".equalsIgnoreCase(pt.name())) {
                        out.add(new WasmInstr.Simple(0x1a, "drop"));
                    }
                    ctx.lastPush = "set";
                    ctx.lastPushWide = false;
                }
                case "kof_set_contains" -> {
                    if (!"int".equals(elemSet(kc))) {
                        throw new WasmUnsupportedException("Set.contains sobre Set<"
                                + elemSet(kc) + "> fora da fatia Set<Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    out.add(new WasmInstr.Call("kof.setContains"));
                    ctx.lastPush = "bool";
                    ctx.lastPushWide = false;
                }
                case "kof_set_remove" -> {
                    if (!"int".equals(elemSet(kc))) {
                        throw new WasmUnsupportedException("Set.remove sobre Set<"
                                + elemSet(kc) + "> fora da fatia Set<Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    out.add(new WasmInstr.Call("kof.setRemove"));
                    ctx.lastPush = null;
                    ctx.lastPushWide = false;
                }
                default -> {                                             // kof_set_size
                    out.add(new WasmInstr.Call("kof.setSize"));
                    ctx.lastPush = "int";
                    ctx.lastPushWide = true;
                }
            }
        } else if (ctx.wasi && WasmListRuntime.NAMES.contains(kc.methodName())) {
            // Fase E (27/10, #776): LIST fatia 2 — o desugar do listOf:
            // `kof_list_new` + N× `kof_list_add` (INSTANCE, void) + size/get
            // (→ int) + contains/index_of/last_index_of/is_empty. O handle e'
            // i32 (celula indireta — ver WasmListRuntime). O tipo do elemento
            // vem do type argument do OWNER (medido no IR): Int viaja i64;
            // String viaja como handle i32 zero-extendido no slot i64.
            String elem = listElemType(kc);
            switch (kc.methodName()) {
                case "kof_list_new" -> {
                    out.add(new WasmInstr.Call("kof.listNew"));
                    ctx.lastPush = "list";
                    ctx.lastPushWide = false;
                }
                case "kof_list_add" -> {
                    if (isDirectI64Elem(elem)) {
                        // o rastro do ARG diz a largura: int/long/char sao
                        // todos i64-wide (o literal 1L chega com o rastro
                        // "long" — medido) — o TIPO do elem vem do owner
                        if (!"int".equals(ctx.lastPush) && !"long".equals(ctx.lastPush)
                                && !"char".equals(ctx.lastPush)) {
                            throw new WasmUnsupportedException("List.add('" + ctx.lastPush
                                    + "') em List<" + elem + "> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                        }
                        out.add(new WasmInstr.Call("kof.listAdd"));
                    } else if ("bool".equals(elem)) {
                        // bool = i32: estende para o slot i64
                        if (!"bool".equals(ctx.lastPush)) {
                            throw new WasmUnsupportedException("List.add('" + ctx.lastPush
                                    + "') em List<Bool> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                        }
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                        out.add(new WasmInstr.Call("kof.listAdd"));
                    } else if ("double".equals(elem)) {
                        if (!"double".equals(ctx.lastPush)) {
                            throw new WasmUnsupportedException("List.add('" + ctx.lastPush
                                    + "') em List<Double> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                        }
                        // o f64 anda no slot i64 como BITS (o contains/indexOf
                        // comparam bits = exatamente Double.equals)
                        out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                        out.add(new WasmInstr.Call("kof.listAdd"));
                    } else if (isRefElem(elem)) {
                        // String e a lista aninhada: o handle i32 vira o slot
                        // i64 (zero-extend; handle e' positivo) — o rastro do
                        // arg de uma lista aninhada e' "list" (o tee do desugar)
                        // o rastro do arg pode ser "list" (o tee do desugar)
                        // ou "List" (o load de um LOCAL tipado List — o
                        // typeName da classe, capitalizado)
                        if (!"string".equals(ctx.lastPush) && !"list".equalsIgnoreCase(ctx.lastPush)) {
                            throw new WasmUnsupportedException("List.add('" + ctx.lastPush
                                    + "') em List<" + elem + "> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                        }
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                        out.add(new WasmInstr.Call("kof.listAdd"));
                    } else {
                        throw new WasmUnsupportedException("List<" + elem
                                + "> fora da fatia List<Int>/List<String>/List<Double> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // o tee do KofDup DEIXOU o handle original na pilha: o
                    // rastro verdadeiro depois do add e' "list" (os dups
                    // seguintes do desugar leem este rastro)
                    ctx.lastPush = "list";
                    ctx.lastPushWide = false;
                }
                case "kof_list_size" -> {
                    out.add(new WasmInstr.Call("kof.listSize"));
                    ctx.lastPush = "int";
                    ctx.lastPushWide = true;
                }
                case "kof_list_get" -> {
                    if (isRefElem(elem)) {
                        // o slot i64 guarda o handle: volta i32; o rastro e'
                        // "string" ou "list" (a lista aninhada encadeia
                        // size/get — a maquinaria da LIST-1)
                        out.add(new WasmInstr.Call("kof.listGet"));
                        out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
                        ctx.lastPush = "list".equalsIgnoreCase(elem) ? "list" : "string";
                        ctx.lastPushWide = false;
                    } else if (isDirectI64Elem(elem)) {
                        out.add(new WasmInstr.Call("kof.listGet"));
                        // long rende o rastro "int" (a caixa 0/2 renderiza igual);
                        // char rende "char" (o strChar/writeChar recebem i64)
                        ctx.lastPush = "char".equals(elem) ? "char" : "int";
                        ctx.lastPushWide = true;
                    } else if ("bool".equals(elem)) {
                        // o slot guarda o bool estendido: volta i32
                        out.add(new WasmInstr.Call("kof.listGet"));
                        out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
                        ctx.lastPush = "bool";
                        ctx.lastPushWide = false;
                    } else if ("double".equals(elem)) {
                        // os BITS do slot voltam f64
                        out.add(new WasmInstr.Call("kof.listGet"));
                        out.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
                        ctx.lastPush = "double";
                        ctx.lastPushWide = true;
                    } else {
                        throw new WasmUnsupportedException("List.get de '" + elem
                                + "' fora da fatia List<Int>/List<String>/List<Double> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                }
                case "kof_list_set", "kof_list_remove", "kof_list_clear" -> {
                    // Fase E (27/10, #776): os MUTADORES (fatia 3). O set e'
                    // [h, i, v] SEM fantasma (medido no WAT: o drop aqui
                    // consumia o proprio valor); o remove e' [int]→int SEM
                    // fantasma (o KofPop do desugar consome o elemento
                    // devolvido); o clear e' []→void.
                    if ("kof_list_set".equals(kc.methodName())) {
                        if (isRefElem(elem)) {
                            if (!"string".equals(ctx.lastPush) && !"list".equalsIgnoreCase(ctx.lastPush)) {
                                throw new WasmUnsupportedException("List.set('" + ctx.lastPush
                                        + "') em List<" + elem + "> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                            }
                            out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                        } else if ("double".equals(elem)) {
                            if (!"double".equals(ctx.lastPush)) {
                                throw new WasmUnsupportedException("List.set('" + ctx.lastPush
                                        + "') em List<Double> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                            }
                            out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                        } else if (isDirectI64Elem(elem) && !"char".equals(ctx.lastPush)
                                && !"int".equals(ctx.lastPush) && !"long".equals(ctx.lastPush)) {
                            throw new WasmUnsupportedException("List.set('" + ctx.lastPush
                                    + "') em List<" + elem + "> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                        } else if ("bool".equals(elem)) {
                            if (!"bool".equals(ctx.lastPush)) {
                                throw new WasmUnsupportedException("List.set('" + ctx.lastPush
                                        + "') em List<Bool> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                            }
                            out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                        }
                        out.add(new WasmInstr.Call("kof.listSet"));
                    } else if ("kof_list_remove".equals(kc.methodName())) {
                        out.add(new WasmInstr.Call("kof.listRemove"));
                    } else {
                        out.add(new WasmInstr.Call("kof.listClear"));
                    }
                    ctx.lastPush = null;
                    ctx.lastPushWide = false;
                }
                case "kof_list_contains", "kof_list_index_of", "kof_list_last_index_of" -> {
                    // Fase E (27/10, #776): fatia 4 — sobre List<String> o
                    // match e' IGUALDADE DE CONTEUDO (o kof.strEq existente,
                    // o mesmo do fold de `==` de record): as variantes Str
                    // recebem o HANDLE cru (i32 — sem extend). Sobre outros
                    // elems recusa (nunca compare errado).
                    String rn;
                    if (isDirectI64Elem(elem) || "double".equals(elem) || "bool".equals(elem)) {
                        rn = switch (kc.methodName()) {
                            case "kof_list_contains" -> "kof.listContains";
                            case "kof_list_index_of" -> "kof.listIndexOf";
                            default -> "kof.listLastIndexOf";
                        };
                    } else if ("string".equals(elem)) {
                        rn = switch (kc.methodName()) {
                            case "kof_list_contains" -> "kof.listContainsStr";
                            case "kof_list_index_of" -> "kof.listIndexOfStr";
                            default -> "kof.listLastIndexOfStr";
                        };
                    } else if ("list".equalsIgnoreCase(elem)) {
                        // a JVM compara equals = igualdade PROFUNDA de lista —
                        // uma face propria (recusa nomeada, nunca handle-identidade)
                        throw new WasmUnsupportedException("List." + kc.methodName()
                                + " sobre List<List> fora da fatia (a igualdade profunda"
                                + " de listas e' uma face propria) (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    } else {
                        throw new WasmUnsupportedException("List." + kc.methodName()
                                + " sobre List<" + elem + "> fora da fatia (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // o desugar materializa o PARAM 0 (o receiver) como um
                    // FANTASMA no topo — medido no IR ([h, v, 0]); o drop vem
                    // PRIMEIRO (sobre o phantom) e SO entao o double
                    // reinterpreta o valor (a ordem antiga reinterpretava o
                    // proprio phantom — o validador rejeitou)
                    out.add(new WasmInstr.Simple(0x1a, "drop"));
                    // as larguras do valor DEPOIS do drop (o topo era o
                    // phantom — a LIST-5 pagou essa lei pro double e a
                    // LIST-6 repetiu-a pro bool: o extend antes do drop
                    // consumia o phantom)
                    if ("double".equals(elem)) {
                        // o valor chega f64 e vai como BITS (a comparacao de
                        // bits = exatamente Double.equals: NaN=NaN por bits,
                        // 0.0 vs -0.0 distintos)
                        out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                    } else if ("bool".equals(elem)) {
                        // o valor i32 estende antes do bit-scan (o slot
                        // guarda o estendido — bits 0/1 exatos)
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                    }
                    out.add(new WasmInstr.Call(rn));
                    if ("kof_list_contains".equals(kc.methodName())) {
                        ctx.lastPush = "bool";
                        ctx.lastPushWide = false;
                    } else {
                        ctx.lastPush = "int";
                        ctx.lastPushWide = true;
                    }
                }
                default -> {                                             // kof_list_is_empty
                    // size == 0: SEM runtime novo (o listSize + eqz aqui)
                    out.add(new WasmInstr.Call("kof.listSize"));
                    out.add(new WasmInstr.Simple(0x50, "i64.eqz"));
                    ctx.lastPush = "bool";
                    ctx.lastPushWide = false;
                }
            }
        } else if (ctx.wasi && WasmBoxRuntime.NAMES.contains(kc.methodName())) {
            // Fase E (27/10, #776): a CAIXA DE ERASURE — o MESMO contrato
            // da nativa §284 (24B [MAGIC][tag][valor]); o join HETEROGENEO
            // do if-expression unifica em Object com os ramos primitivos
            // boxeados (Integer.valueOf/String.valueOf no JVM medido). O
            // resultado e um handle i32 — "object" no rastro, nunca wide.
            String rn = switch (kc.methodName()) {
                case "kof_box_int" -> "kof.boxInt";
                case "kof_box_long" -> "kof.boxLong";
                case "kof_box_bool" -> "kof.boxBool";
                case "kof_box_double" -> "kof.boxDouble";
                default -> "kof.boxToStr";
            };
            out.add(new WasmInstr.Call(rn));
            ctx.lastPush = "object";
            ctx.lastPushWide = false;
        } else if (ctx.wasi && WasmMathRuntime.NAMES.contains(kc.methodName())) {
            // Fase E (26/10, #776): a familia INT do math — o nome DA
            // funcao e' o nome do runtime (kof_math_*, aritmetica i64
            // pura). O rastro de largura TEM de ser atualizado: sem ele o
            // println(isEven(x)) despachava para o println i64 com um
            // resultado i32 (modulo invalido).
            out.add(new WasmInstr.Call(kc.methodName()));
            boolean pred = kc.methodName().startsWith("kof_math_is");
            boolean dbl = kc.methodName().equals("kof_math_roundTo");
            ctx.lastPush = pred ? "bool" : (dbl ? "double" : "int");
            ctx.lastPushWide = !pred;
        } else if (ctx.wasi && "println".equals(kc.methodName())) {
            WasmPrintCode.emitPrintln(kc, ctx, out);
            // o print CONSOME o valor: limpar o rastro de largura (sem
            // isto o literal 0 do statement seguinte herdava "bool" e
            // saia i32.const no parametro i64 do call seguinte)
            ctx.lastPush = null;
            ctx.lastPushWide = false;
        } else if (kc.kind() == KofCallKind.FUNCTION || kc.kind() == KofCallKind.STATIC) {
            out.add(new WasmInstr.Call(kc.methodName()));
        } else {
            throw new WasmUnsupportedException("chamada '" + kc.methodName()
                    + "' fora do subset escalar 15.2 (WASM002) — docs/wasm-wasi-plan.md (#776)");
        }
    }
}
