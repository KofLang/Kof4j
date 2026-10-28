package dev.kof.compiler.wasm;

import dev.kof.compiler.KofCall;
import dev.kof.compiler.Type;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

import java.util.List;

/**
 * Fase E (27/10, #776 — split check_500): o DESPACHO das COLECOES do backend
 * WASI (Map/Set/List), extraido verbatim do {@code WasmCallCode.emitCall}
 * (620 ≥ 600 derrubava o gate CI). Responsabilidade unica: UMA chamada de
 * colecao — as celulas indiretas (WasmMapRuntime/WasmSetRuntime/
 * WasmListRuntime), os slots i64 com as conversoes de largura (bool estende,
 * double vai como BITS f64→i64) e o rastro {@code ctx.lastPush}. As chaves de
 * elem vem do type argument do OWNER (medido no IR). Comportamento identico
 * (freeze rule 3).
 */
final class WasmCollCallCode {

    private WasmCollCallCode() {
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

    /** true = a chamada era de colecao e ja emitiu. */
    static boolean emitCollCall(KofCall kc, WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (WasmMapRuntime.NAMES.contains(kc.methodName())) {
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
                    // compatibilidade com o owner — a fatia e' <String, Int>
                    // e, desde a MV (28/10), <String, String>)
                    boolean strVal = "string".equalsIgnoreCase(mapValueType(kc));
                    if (!"string".equalsIgnoreCase(mapKeyType(kc))
                            || (!"int".equalsIgnoreCase(mapValueType(kc)) && !strVal)) {
                        throw new WasmUnsupportedException("Map<"
                                + mapKeyType(kc) + ", " + mapValueType(kc)
                                + "> fora da fatia Map<String, Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // FATIA MV (28/10, #776): o valor String = o mapPutStr (o
                    // handle ESTENDIDO no slot i64, o res CRU); a largura do
                    // valor esta' na pilha como i32 — o extend fica NO RUNTIME
                    out.add(new WasmInstr.Call(strVal ? "kof.mapPutStr" : "kof.mapPut"));
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
                case "kof_map_contains" -> {
                    if (!"string".equalsIgnoreCase(mapKeyType(kc))) {
                        throw new WasmUnsupportedException("Map.contains com chave '"
                                + mapKeyType(kc) + "' fora da fatia Map<String, Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // SEM phantom: o IR do contains tem UM param (a chave —
                    // medido; o drop aqui consumia o handle da chave)
                    out.add(new WasmInstr.Call("kof.mapContains"));
                    ctx.lastPush = "bool";
                    ctx.lastPushWide = false;
                }
                case "kof_map_remove" -> {
                    boolean strVal = "string".equalsIgnoreCase(mapValueType(kc));
                    if (!"string".equalsIgnoreCase(mapKeyType(kc))
                            || (!"int".equalsIgnoreCase(mapValueType(kc)) && !strVal)) {
                        throw new WasmUnsupportedException("Map<"
                                + mapKeyType(kc) + ", " + mapValueType(kc)
                                + "> fora da fatia Map<String, Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // FATIA MV: o remove do valor String devolve o CRU
                    out.add(new WasmInstr.Call(strVal ? "kof.mapRemoveStr" : "kof.mapRemove"));
                    // o remove devolve a CAIXA do valor removido (0=null): a
                    // forma declaracao tem o KofPop (consome); a forma VOID
                    // tem o KofPop DO STATEMENT (o hasReturnValue do Compiler
                    // Comparisons — o mesmo pop dos outros valores). FATIA MV:
                    // o rastro do CRU (o String? no KofPop/renderizar `null`)
                    // e' o nullablestring
                    ctx.lastPush = strVal ? "nullablestring" : "object";
                    ctx.lastPushWide = false;
                }
                case "kof_map_get" -> {
                    if (!"string".equalsIgnoreCase(mapKeyType(kc))) {
                        throw new WasmUnsupportedException("Map.get com chave '"
                                + mapKeyType(kc) + "' fora da fatia Map<String, Int> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // FATIA MV: o valor String = o mapGetStr (o CRU — o
                    // mapval passthrough do boxToStr renderiza o String e o
                    // `null` no 0; o MESMO rastro dos dois consumidores)
                    boolean strVal = "string".equalsIgnoreCase(mapValueType(kc));
                    out.add(new WasmInstr.Call(strVal ? "kof.mapGetStr" : "kof.mapGet"));
                    ctx.lastPush = "mapval";                     // o handle da caixa OU o cru (0 = null)
                    ctx.lastPushWide = false;
                }
                case "kof_map_keys", "kof_map_values" -> {
                    // FATIA 3 (27/10): a iteracao — keys/values devolvem a
                    // LISTA nova (o rastro "list" entrega à maquinaria da
                    // LISTA provada: size/get/contains/for-in). A ORDEM nao
                    // e' o contrato (HashMap nao a expoe; a celula WASI e'
                    // de insercao) — as faces de paridade sao livres de
                    // ordem + o get de par unico.
                    out.add(new WasmInstr.Call("kof_map_keys".equals(kc.methodName())
                            ? "kof.mapKeys" : "kof.mapValues"));
                    ctx.lastPush = "list";
                    ctx.lastPushWide = false;
                }
                default -> {                                             // kof_map_size
                    out.add(new WasmInstr.Call("kof.mapSize"));
                    ctx.lastPush = "int";
                    ctx.lastPushWide = true;
                }
            }
        return true;
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
                    // fatia 1 (escalares: int/long/char i64 direto, bool
                    // estende o i32, double vai como BITS f64->i64) e fatia 2
                    // (String: o HANDLE i32 cru — o setAddStr compara
                    // CONTEUDO); o rastro do TOPO no call e' o VALOR
                    boolean strElem = "string".equals(elemSet(kc));
                    boolean valScal = "int".equals(ctx.lastPush) || "long".equals(ctx.lastPush)
                            || "char".equals(ctx.lastPush) || "bool".equals(ctx.lastPush)
                            || "double".equals(ctx.lastPush);
                    if (!strElem && !valScal) {
                        throw new WasmUnsupportedException("Set.add('" + ctx.lastPush
                                + "') fora da fatia Set<Int>/Set<Long>/Set<Double>"
                                + " (WASM002) — docs/wasm-wasi-plan.md (#776)");
                    }
                    // o double: o valor chega f64 e vai como BITS; o bool
                    // estende o i32 para o slot i64
                    if (!strElem && "double".equals(ctx.lastPush)) {
                        out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                    } else if (!strElem && "bool".equals(ctx.lastPush)) {
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                    }
                    out.add(new WasmInstr.Call(strElem ? "kof.setAddStr" : "kof.setAdd"));
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
                    boolean strElem2 = "string".equals(elemSet(kc));
                    boolean scal = "int".equals(elemSet(kc)) || "long".equals(elemSet(kc))
                            || "char".equals(elemSet(kc)) || "bool".equals(elemSet(kc))
                            || "double".equals(elemSet(kc));
                    if (!strElem2 && !scal) {
                        throw new WasmUnsupportedException("Set.contains sobre Set<"
                                + elemSet(kc) + "> fora da fatia Set<Int>/Set<String>/"
                                + "Set<Long>/Set<Double> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // o double: o valor chega f64 e vai como BITS; o bool
                    // estende o i32 para o slot i64
                    if ("double".equals(elemSet(kc))) {
                        out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                    } else if ("bool".equals(elemSet(kc))) {
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                    }
                    out.add(new WasmInstr.Call(strElem2 ? "kof.setContainsStr" : "kof.setContains"));
                    ctx.lastPush = "bool";
                    ctx.lastPushWide = false;
                }
                case "kof_set_remove" -> {
                    boolean scal3 = "int".equals(elemSet(kc)) || "long".equals(elemSet(kc))
                            || "char".equals(elemSet(kc)) || "bool".equals(elemSet(kc))
                            || "double".equals(elemSet(kc));
                    if (!scal3) {
                        throw new WasmUnsupportedException("Set.remove sobre Set<"
                                + elemSet(kc) + "> fora da fatia Set<Int>/Set<Long>/"
                                + "Set<Double> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                    }
                    if ("double".equals(elemSet(kc))) {
                        out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                    } else if ("bool".equals(elemSet(kc))) {
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
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
        return true;
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
            return true;
        }
        return false;
    }
}
