package dev.kof.compiler.wasm;

import dev.kof.compiler.KofCall;
import dev.kof.compiler.Type;

import java.util.List;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/**
 * Fase E (28/10, #776 — split check_500): o despacho do LIST — extraído
 * verbatim do {@code WasmCollCallCode} (607 ≥ 600 derrubava o gate CI).
 * Responsabilidade única: kof_list_new/add/size/get/contains/index_of/
 * last_index_of/is_empty — as fatias 1..7 (o Int/long/char i64 direto, o
 * bool estendido, o double por BITS, o String/da lista aninhada pelo
 * handle). O despacho do MAP fica no {@link WasmCollCallCode}; o SET no
 * {@link WasmCollSetCall}.
 */
final class WasmCollListCall {

    private WasmCollListCall() {
    }

    /** true = a chamada era de colecao e ja emitiu. */
    static boolean emit(KofCall kc, WasmLowering.Ctx ctx, List<WasmInstr> out) {
if (WasmListRuntime.NAMES.contains(kc.methodName())) {
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
                    } else if (isRefElem(elem, ctx)) {
                        // String e a lista aninhada: o handle i32 vira o slot
                        // i64 (zero-extend; handle e' positivo) — o rastro do
                        // arg de uma lista aninhada e' "list" (o tee do desugar)
                        // o rastro do arg pode ser "list" (o tee do desugar)
                        // ou "List" (o load de um LOCAL tipado List — o
                        // typeName da classe, capitalizado)
                        // CLASS-2g (29/10, #776): o ELEMENTO DE CLASSE = o
                        // MESMO slot do handle i32 — o rastro do arg = o
                        // nome da classe ("Animal" do LoadLocal) ou
                        // "class:X" (o tee do alloc)
                        if (!isRefRastro(ctx, ctx.lastPush)) {
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
                    if (isRefElem(elem, ctx)) {
                        // o slot i64 guarda o handle: volta i32; o rastro e'
                        // "string" ou "list" (a lista aninhada encadeia
                        // size/get — a maquinaria da LIST-1)
                        out.add(new WasmInstr.Call("kof.listGet"));
                        out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
                        // CLASS-2g: o rastro do elemento de classe = o NOME da
                        // classe (o despacho sobre o elemento anda na rota
                        // ARMADA; o acesso de campo = o load de campo de
                        // classe)
                        ctx.lastPush = "list".equalsIgnoreCase(elem) ? "list"
                                : "string".equals(elem) ? "string" : elem;
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
                        if (isRefElem(elem, ctx)) {
                            if (!isRefRastro(ctx, ctx.lastPush)) {
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
                        // CLASS-2g (29/10, #776): A LEI DA LARGURA DO
                        // RESULTADO do remove — o slot devolve i64 e o
                        // ELEMENTO anda por elem: o ref (o String/a lista/a
                        // classe) = o wrap i32 + o rastro do elem (o local do
                        // classe = i32: o i64 cru = o mismatch, medido em
                        // P12); o bool = o wrap; o double = o reinterpret dos
                        // bits; o Int/long/char cru = o i64
                        out.add(new WasmInstr.Call("kof.listRemove"));
                        if (isRefElem(elem, ctx)) {
                            out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
                            ctx.lastPush = "list".equalsIgnoreCase(elem) ? "list"
                                    : "string".equals(elem) ? "string" : elem;
                            ctx.lastPushWide = false;
                        } else if ("bool".equals(elem)) {
                            out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
                            ctx.lastPush = "bool";
                            ctx.lastPushWide = false;
                        } else if ("double".equals(elem)) {
                            out.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
                            ctx.lastPush = "double";
                            ctx.lastPushWide = true;
                        } else {
                            ctx.lastPush = "int";
                            ctx.lastPushWide = true;
                        }
                    } else {
                        out.add(new WasmInstr.Call("kof.listClear"));
                        ctx.lastPush = null;
                        ctx.lastPushWide = false;
                    }
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

    /** CLASS-2g (29/10, #776): o elemento REF = o String/a lista OU a
     *  CLASSE alcançada (o handle i32 no slot — a MESMA disciplina do
     *  String). */
    private static boolean isRefElem(String elem, WasmLowering.Ctx ctx) {
        if (isRefElem(elem)) return true;
        return ctx.classes() != null && ctx.classes().containsKey(elem);
    }

    /** O rastro REF-aceitável no add/set: o String/a lista/um nome de
     *  classe ("Animal" do LoadLocal) ou "class:X" (o tee do alloc). */
    private static boolean isRefRastro(WasmLowering.Ctx ctx, String rastro) {
        if ("string".equals(rastro) || "list".equalsIgnoreCase(rastro)) return true;
        String n = rastro.startsWith("class:") ? rastro.substring(6) : rastro;
        return ctx.classes() != null && ctx.classes().containsKey(n);
    }
}
