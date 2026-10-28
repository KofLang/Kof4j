package dev.kof.compiler.wasm;

import dev.kof.compiler.KofCall;
import dev.kof.compiler.Type;

import java.util.List;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/**
 * Fase E (28/10, #776 — split check_500): o despacho do SET — extraído
 * verbatim do {@code WasmCollCallCode} (607 ≥ 600 derrubava o gate CI).
 * Responsabilidade única: kof_set_new/add/contains/remove/size — as fatias
 * Int/escalares (o valor i64 direto/bits/estendido) e a família STR (o
 * conteúdo via kof.strEq, a fatia SET-STR da rodada 53). O despacho do MAP
 * fica no {@link WasmCollCallCode}; o LIST no {@link WasmCollListCall}.
 */
final class WasmCollSetCall {

    private WasmCollSetCall() {
    }

    /** true = a chamada era de colecao e ja emitiu. */
    static boolean emit(KofCall kc, WasmLowering.Ctx ctx, List<WasmInstr> out) {
if (WasmSetRuntime.NAMES.contains(kc.methodName())) {
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
                    boolean strElem3 = "string".equals(elemSet(kc));
                    boolean scal3 = "int".equals(elemSet(kc)) || "long".equals(elemSet(kc))
                            || "char".equals(elemSet(kc)) || "bool".equals(elemSet(kc))
                            || "double".equals(elemSet(kc));
                    if (!scal3 && !strElem3) {
                        throw new WasmUnsupportedException("Set.remove sobre Set<"
                                + elemSet(kc) + "> fora da fatia Set<Int>/Set<String>/"
                                + "Set<Long>/Set<Double> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                    }
                    if (strElem3) {
                        // SET-STR (28/10, rodada 53): o remove por CONTEUDO —
                        // o handle anda CRU como arg (o setRemoveStr compara
                        // pelo kof.strEq, o mesmo contrato do add/contains)
                        out.add(new WasmInstr.Call("kof.setRemoveStr"));
                    } else {
                        if ("double".equals(elemSet(kc))) {
                            out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                        } else if ("bool".equals(elemSet(kc))) {
                            out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                        }
                        out.add(new WasmInstr.Call("kof.setRemove"));
                    }
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
        }
        return false;
    }

    /** O tipo do elemento de SET pelo type argument do OWNER (medido no IR:
     *  `Set<Int>` chega com typeArguments=[int]). */
    private static String elemSet(KofCall kc) {
        if (kc.ownerType() instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return typeName(ct.typeArguments().get(0));
        }
        return "";
    }
}
