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
                    // CLASS-2i (29/10, #776): o ELEMENTO DE CLASSE = a rota
                    // DIRECT-I64 (o handle i32 estendido no slot — a MESMA
                    // disciplina das classes da lista/mapa; o contains/remove
                    // = o i64.eq = a IDENTIDADE = a paridade do default
                    // equals do JVM) — o rastro do arg = o nome da classe
                    // ("Dog" do LoadLocal) ou "class:X" (o tee do alloc)
                    boolean classElem = isClassElem(kc, ctx);
                    boolean classRastro = isClassRastro(ctx, ctx.lastPush);
                    // SET-REC (29/10, #776): o ELEMENTO de RECORD = a rota do
                    // FOLD (a igualdade de CONTEÚDO — o contrato do HashSet +
                    // o equals de VALOR do record: `setOf(Point(1,2), Point(1,2))`
                    // tem size 1); o rastro do valor = "record:Point" (o ctor)
                    // ou "Point" (o load do local)
                    boolean recElem = isRecordElem(kc, ctx);
                    boolean recRastro = isRecordRastro(ctx, ctx.lastPush);
                    if (!strElem && !valScal && !(classElem && classRastro)
                            && !(recElem && recRastro)) {
                        throw new WasmUnsupportedException("Set.add('" + ctx.lastPush
                                + "') fora da fatia Set<Int>/Set<Long>/Set<Double>/Set<Classe>"
                                + "/Set<Record> (WASM002) — docs/wasm-wasi-plan.md (#776)");
                    }
                    // o double: o valor chega f64 e vai como BITS; o bool
                    // estende o i32 para o slot i64
                    if (!strElem && "double".equals(ctx.lastPush)) {
                        out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                    } else if (!strElem && "bool".equals(ctx.lastPush)) {
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                    } else if (classElem && !strElem) {
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                    } else if (recElem) {
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                    }
                    out.add(new WasmInstr.Call(strElem ? "kof.setAddStr"
                            : recElem ? "kof.setAddRec." + elemSet(kc) : "kof.setAdd"));
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
                    boolean classElem2 = isClassElem(kc, ctx);
                    boolean recElem2 = isRecordElem(kc, ctx);
                    if (!strElem2 && !scal && !classElem2 && !recElem2) {
                        throw new WasmUnsupportedException("Set.contains sobre Set<"
                                + elemSet(kc) + "> fora da fatia Set<Int>/Set<String>/"
                                + "Set<Long>/Set<Double>/Set<Classe>/Set<Record> (WASM002)"
                                + " — docs/wasm-wasi-plan.md (#776)");
                    }
                    // o double: o valor chega f64 e vai como BITS; o bool
                    // estende o i32 para o slot i64
                    if ("double".equals(elemSet(kc))) {
                        out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                    } else if ("bool".equals(elemSet(kc))) {
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                    } else if (classElem2) {
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                    } else if (recElem2) {
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                    }
                    out.add(new WasmInstr.Call(strElem2 ? "kof.setContainsStr"
                            : recElem2 ? "kof.setContainsRec." + elemSet(kc)
                            : "kof.setContains"));
                    ctx.lastPush = "bool";
                    ctx.lastPushWide = false;
                }
                case "kof_set_remove" -> {
                    boolean strElem3 = "string".equals(elemSet(kc));
                    boolean scal3 = "int".equals(elemSet(kc)) || "long".equals(elemSet(kc))
                            || "char".equals(elemSet(kc)) || "bool".equals(elemSet(kc))
                            || "double".equals(elemSet(kc));
                    boolean classElem3 = isClassElem(kc, ctx);
                    boolean recElem3 = isRecordElem(kc, ctx);
                    if (!scal3 && !strElem3 && !classElem3 && !recElem3) {
                        throw new WasmUnsupportedException("Set.remove sobre Set<"
                                + elemSet(kc) + "> fora da fatia Set<Int>/Set<String>/"
                                + "Set<Long>/Set<Double>/Set<Classe>/Set<Record> (WASM002) —"
                                + " docs/wasm-wasi-plan.md (#776)");
                    }
                    if (strElem3) {
                        // SET-STR (28/10, rodada 53): o remove por CONTEUDO —
                        // o handle anda CRU como arg (o setRemoveStr compara
                        // pelo kof.strEq, o mesmo contrato do add/contains)
                        out.add(new WasmInstr.Call("kof.setRemoveStr"));
                    } else if (recElem3) {
                        // SET-REC (29/10, #776): o remove por CONTEUDO do
                        // record — o handle estendido no slot (o wrap de
                        // volta no runtime), o fold compara campo a campo
                        out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                        out.add(new WasmInstr.Call("kof.setRemoveRec." + elemSet(kc)));
                    } else {
                        if ("double".equals(elemSet(kc))) {
                            out.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
                        } else if ("bool".equals(elemSet(kc))) {
                            out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                        } else if (classElem3) {
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

    /** CLASS-2i (29/10, #776): o elemento do Set = uma CLASSE alcançada (a
     *  rota DIRECT-I64 — a identidade = a paridade do default equals). */
    private static boolean isClassElem(KofCall kc, WasmLowering.Ctx ctx) {
        String e = elemSet(kc);
        return ctx.classes() != null && !e.isEmpty() && ctx.classes().containsKey(e);
    }

    /** O rastro de classe aceitável no add ("Dog" do LoadLocal /
     *  "class:X" do tee do alloc). */
    private static boolean isClassRastro(WasmLowering.Ctx ctx, String rastro) {
        String n = rastro.startsWith("class:") ? rastro.substring(6) : rastro;
        return ctx.classes() != null && ctx.classes().containsKey(n);
    }

    /** SET-REC (29/10, #776): o elemento do Set = um RECORD alcançado (a
     *  rota do FOLD — a igualdade de CONTEÚDO, o equals de VALOR do
     *  record). */
    private static boolean isRecordElem(KofCall kc, WasmLowering.Ctx ctx) {
        String e = elemSet(kc);
        return ctx.records != null && !e.isEmpty() && ctx.records.containsKey(e);
    }

    /** O rastro de record aceitável no add ("record:Point" do ctor /
     *  "Point" do load do local). */
    private static boolean isRecordRastro(WasmLowering.Ctx ctx, String rastro) {
        String n = rastro.startsWith("record:") ? rastro.substring(7) : rastro;
        return ctx.records != null && ctx.records.containsKey(n);
    }
}
