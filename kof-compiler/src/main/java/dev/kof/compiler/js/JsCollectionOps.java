package dev.kof.compiler.js;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsCollectionOps — lowering das operações de List/Map/Set/Channel da stdlib Kof para os helpers do runtime JS (REFACTOR-500 FASE 4).
 */
public final class JsCollectionOps {

    private final JsMethodParser p;

    JsCollectionOps(JsMethodParser p) {
        this.p = p;
    }

boolean isListOp(KofCall kc) {
        return BuiltinTypes.isList(kc.ownerType()) && kc.methodName().startsWith("kof_list_");
    }

boolean isChannelOp(KofCall kc) {
        return BuiltinTypes.isChannel(kc.ownerType()) && kc.methodName().startsWith("kof_channel_");
    }

boolean isMapOp(KofCall kc) {
        return BuiltinTypes.isMap(kc.ownerType()) && kc.methodName().startsWith("kof_map_");
    }

boolean isSetOp(KofCall kc) {
        return BuiltinTypes.isSet(kc.ownerType()) && kc.methodName().startsWith("kof_set_");
    }

void handleChannelOp(MethodCtx ctx, List<Object> stack,
                               List<JsIr.JsExpression> preambleExprs, KofCall kc,
                               JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        // Canais tipados (JS sequencial): FIFO { items: [] } — send push, receive shift.
        String fn = switch (kc.methodName()) {
            case "kof_channel_new" -> "kofChannelNew";
            case "kof_channel_send" -> "kofChannelSend";
            case "kof_channel_receive" -> "kofChannelReceive";
            default -> throw new IllegalStateException("KofJS: unknown channel op " + kc.methodName());
        };
        p.lc.registerRuntime(fn);
        if ("kof_channel_new".equals(kc.methodName())) {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of()));
            return;
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        callArgs.add(receiver);
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if ("kof_channel_receive".equals(kc.methodName())) {
            call = new JsIr.JsAwait(call);
        }
        if (Type.isVoid(kc.returnType())) {
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private JsIr.JsExpression slotStoreCoerce(KofCall kc, int valIdx,
                                              JsIr.JsExpression value) {
        Type elem = null;
        if (kc.ownerType() instanceof dev.kof.compiler.Type.ClassType ct
                && ct.typeArguments().size() == 1) {
            elem = ct.typeArguments().get(0);
        }
        if (elem == null || valIdx < 0 || valIdx >= kc.parameterTypes().size()) {
            return value;
        }
        Type arg = kc.parameterTypes().get(valIdx);
        if (arg == null) return value;
        if (elem instanceof dev.kof.compiler.Type.NullableType en) elem = en.inner();
        if (arg instanceof dev.kof.compiler.Type.NullableType an) arg = an.inner();
        if (!(arg instanceof dev.kof.compiler.Type.PrimitiveType ap)
                || !(elem instanceof dev.kof.compiler.Type.PrimitiveType ep)) {
            return value;
        }
        String en = dev.kof.compiler.Type.canonicalPrimitiveName(ep.name());
        String an = dev.kof.compiler.Type.canonicalPrimitiveName(ap.name());
        if (en.equals(an)) return value;
        boolean argBool = an.equals("bool");
        boolean elemBool = en.equals("bool");
        if (argBool && (en.equals("int") || en.equals("short") || en.equals("byte"))) {
            return numLit(value, "1", "0");
        }
        if (argBool && en.equals("long")) {
            return numLit(value, "1n", "0n");
        }
        if (elemBool && (an.equals("int") || an.equals("short") || an.equals("byte")
                || an.equals("char") || an.equals("long") || an.equals("float")
                || an.equals("double"))) {
            return new JsIr.JsConditional(value,
                    new JsIr.JsIdentifier("true"), new JsIr.JsIdentifier("false"));
        }
        return value;
    }

    private static JsIr.JsExpression numLit(JsIr.JsExpression cond, String one, String zero) {
        return new JsIr.JsConditional(cond,
                new JsIr.JsNumber(one), new JsIr.JsNumber(zero));
    }

void handleListOp(MethodCtx ctx, List<Object> stack,
                               List<JsIr.JsExpression> preambleExprs, KofCall kc,
                               JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        // §383/#561 (opcao (a)): o "miss abencoado" do §126 rescreve o valor
        // pelo tipo PINADO do slot no store (JVM Integer.valueOf sobre o padrao
        // de bits do bool; Script/Native gravam 1/0). O JS e untyped e gravava
        // o original -> `listOf(1).add(true)` lia `true` so no JS. Coercion no
        // MESMO ponto dos outros alvos: o store (add arg 0, set arg 1). bool em
        // slot long chega aqui ja convertido (I2L do lowerer -> BigInt); o
        // caso long+bool abaixo e defensivo. Unknown (bare) e categoria String
        // nao alcancam este gancho (pin ausente / SEM056 na frente).
        int storeValIdx = "kof_list_add".equals(kc.methodName()) ? 0
                : "kof_list_set".equals(kc.methodName()) ? 1 : -1;
        if (storeValIdx >= 0 && storeValIdx < args.size()) {
            args = new ArrayList<>(args);
            args.set(storeValIdx, slotStoreCoerce(kc, storeValIdx, args.get(storeValIdx)));
        }
        String fn = switch (kc.methodName()) {
            case "kof_list_new" -> "kofListNew";
            case "kof_list_add" -> "kofListAdd";
            case "kof_list_get" -> "kofListGet";
            case "kof_list_set" -> "kofListSet";
            case "kof_list_size" -> "kofListSize";
            case "kof_list_contains" -> "kofListContains";
            case "kof_list_is_empty" -> "kofListIsEmpty";
            case "kof_list_remove" -> "kofListRemove";
            case "kof_list_clear" -> "kofListClear";
            // #382 — indexOf/lastIndexOf/addAll/subList/sort (arg tag extra
            // do lowerer é ignorado aqui: kofValEq/kofNaturalCmp decidem por
            // tipo de valor, como no JVM via equals).
            case "kof_list_index_of" -> "kofListIndexOf";
            case "kof_list_last_index_of" -> "kofListLastIndexOf";
            case "kof_list_add_all" -> "kofListAddAll";
            case "kof_list_sub_list" -> "kofListSubList";
            // pagination P1 — take/drop/slice
            case "kof_list_take" -> "kofListTake";
            case "kof_list_drop" -> "kofListDrop";
            case "kof_list_slice" -> "kofListSlice";
            // D-MULTIPARADIGMA-PHASE1A — any/all/none (lambda rides as arg).
            case "kof_list_any" -> "kofListAny";
            case "kof_list_all" -> "kofListAll";
            case "kof_list_none" -> "kofListNone";
            // D-MULTIPARADIGMA-PHASE1A slice 1b — find (null when missing,
            // like kofMapGet) + count with predicate.
            case "kof_list_find" -> "kofListFind";
            case "kof_list_count_pred" -> "kofListCountPred";
            // D-MULTIPARADIGMA-PHASE1A slice 1c — forEach (effect only; the
            // void path below carries it as a statement). Name follows the
            // generic runtimeJsName mapping (kof_list_foreach → kofListForeach).
            case "kof_list_foreach" -> "kofListForeach";
            // D-MULTIPARADIGMA-PHASE1A slice 1d — flatMap (native concat).
            case "kof_list_flatmap" -> "kofListFlatmap";
            // D-MULTIPARADIGMA-PHASE1A slice 1e — distinct (tag ignored here;
            // the prelude mirrors contains: kofValEq per element).
            case "kof_list_distinct" -> "kofListDistinct";
            case "kof_list_sort" -> "kofListSort";
            // D-MULTIPARADIGMA-PHASE1A slice 1g — sorted returns a fresh
            // copy (natural via kofNaturalCmp, comparator via the lambda);
            // insertion sort keeps both stable.
            case "kof_list_sorted" -> "kofListSorted";
            case "kof_list_sorted_cmp" -> "kofListSortedCmp";
            // #685 — enum sort(): in-place insertion via the comparator.
            case "kof_list_sort_cmp" -> "kofListSortCmp";
            // D-MULTIPARADIGMA-PHASE1A slice 1h — groupBy (insertion-ordered
            // Map of fresh lists; the tag rides along ignored like distinct).
            case "kof_list_groupby" -> "kofListGroupBy";
            default -> throw new IllegalStateException("KofJS: unknown list op " + kc.methodName());
        };
        p.lc.registerRuntime(fn);
        if ("kof_list_new".equals(kc.methodName())) {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of()));
            return;
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        callArgs.add(receiver);
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if (Type.isVoid(kc.returnType())) {
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")
                    && !stack.isEmpty() && stack.get(stack.size() - 1) instanceof JsIr.JsSequence seq
                    && seq.value().equals(receiver)) {
                // mid-expression list construction (listOf(...) element append)
                List<JsIr.JsExpression> exprs = new ArrayList<>(seq.expressions());
                exprs.add(call);
                stack.remove(stack.size() - 1);
                stack.add(new JsIr.JsSequence(exprs, seq.value()));
                return;
            }
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")) {
                // The dup'd copy of the list reference stays on the stack for the
                // next append; the append itself must execute before any
                // later operation.
                preambleExprs.add(call);
                return;
            }
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

void handleMapOp(MethodCtx ctx, List<Object> stack,
                              List<JsIr.JsExpression> preambleExprs, KofCall kc,
                              JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String fn = switch (kc.methodName()) {
            case "kof_map_new" -> "kofMapNew";
            case "kof_map_put" -> "kofMapPut";
            case "kof_map_get" -> "kofMapGet";
            case "kof_map_get_or_default" -> "kofMapGetOrDefault";
            case "kof_map_remove" -> "kofMapRemove";
            case "kof_map_contains" -> "kofMapContains";
            case "kof_map_size" -> "kofMapSize";
            case "kof_map_clear" -> "kofMapClear";
            case "kof_map_is_empty" -> "kofMapIsEmpty";
            case "kof_map_keys" -> "kofMapKeys";
            case "kof_map_values" -> "kofMapValues";
            // #386 — containsValue/putIfAbsent (kofMapPutIfAbsent devolve o
            // anterior OU null; Nullable(V) → sem o `?? default` abaixo).
            case "kof_map_contains_value" -> "kofMapContainsValue";
            case "kof_map_put_if_absent" -> "kofMapPutIfAbsent";
            default -> throw new IllegalStateException("KofJS: unknown map op " + kc.methodName());
        };
        p.lc.registerRuntime(fn);
        if ("kof_map_new".equals(kc.methodName())) {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of()));
            return;
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        callArgs.add(receiver);
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        // D-NULL-INTENT/I7 (#278, supersede §112-JS/§127): get/put/remove
        // agora declaram Nullable(V) de verdade (CollectionCallLowerer) —
        // ausência é null observável, nunca substituída pelo default do
        // primitivo. O `?? default` só faz sentido se o retorno ainda fosse
        // primitivo CRU (bare, não-nullable), o que não acontece mais para
        // estes 3 métodos; mantido defensivo para qualquer chamador futuro.
        boolean barePrimitiveReturn = kc.returnType() instanceof Type.PrimitiveType;
        if (barePrimitiveReturn && ("kof_map_put".equals(kc.methodName())
                || "kof_map_remove".equals(kc.methodName())
                || "kof_map_get".equals(kc.methodName()))) {
            call = new JsIr.JsBinary(call, "??", JsTypeMapper.defaultForType(kc.returnType()));
        }
        if (Type.isVoid(kc.returnType())) {
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")
                    && !stack.isEmpty() && stack.get(stack.size() - 1) instanceof JsIr.JsSequence seq
                    && seq.value().equals(receiver)) {
                // construção mid-expression (ex.: pares do mapOf): anexa mantendo o valor
                List<JsIr.JsExpression> exprs = new ArrayList<>(seq.expressions());
                exprs.add(call);
                stack.remove(stack.size() - 1);
                stack.add(new JsIr.JsSequence(exprs, seq.value()));
                return;
            }
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")) {
                // a cópia duplicada permanece na pilha para o próximo par
                preambleExprs.add(call);
                return;
            }
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

void handleSetOp(MethodCtx ctx, List<Object> stack,
                              List<JsIr.JsExpression> preambleExprs, KofCall kc,
                              JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String fn = switch (kc.methodName()) {
            case "kof_set_new" -> "kofSetNew";
            case "kof_set_add" -> "kofSetAdd";
            case "kof_set_contains" -> "kofSetContains";
            case "kof_set_remove" -> "kofSetRemove";
            case "kof_set_size" -> "kofSetSize";
            case "kof_set_clear" -> "kofSetClear";
            case "kof_set_is_empty" -> "kofSetIsEmpty";
            default -> throw new IllegalStateException("KofJS: unknown set op " + kc.methodName());
        };
        p.lc.registerRuntime(fn);
        if ("kof_set_new".equals(kc.methodName())) {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of()));
            return;
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        callArgs.add(receiver);
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if (Type.isVoid(kc.returnType())) {
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")
                    && !stack.isEmpty() && stack.get(stack.size() - 1) instanceof JsIr.JsSequence seq
                    && seq.value().equals(receiver)) {
                // construção mid-expression: anexa à sequência mantendo o valor
                List<JsIr.JsExpression> exprs = new ArrayList<>(seq.expressions());
                exprs.add(call);
                stack.remove(stack.size() - 1);
                stack.add(new JsIr.JsSequence(exprs, seq.value()));
                return;
            }
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")) {
                // a cópia duplicada permanece na pilha para o próximo append
                preambleExprs.add(call);
                return;
            }
            throw new StatementEnd(call);
        }
        stack.add(call);
    }
}
