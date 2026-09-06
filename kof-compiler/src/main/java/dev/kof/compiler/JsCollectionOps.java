package dev.kof.compiler;

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
final class JsCollectionOps {

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

void handleListOp(MethodCtx ctx, List<Object> stack,
                               List<JsIr.JsExpression> preambleExprs, KofCall kc,
                               JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
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
            case "kof_map_remove" -> "kofMapRemove";
            case "kof_map_contains" -> "kofMapContains";
            case "kof_map_size" -> "kofMapSize";
            case "kof_map_clear" -> "kofMapClear";
            case "kof_map_is_empty" -> "kofMapIsEmpty";
            case "kof_map_keys" -> "kofMapKeys";
            case "kof_map_values" -> "kofMapValues";
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
