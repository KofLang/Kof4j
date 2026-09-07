package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

/**
 * Coleções nativas do interpretador — os MESMOS alvos do emitter JVM
 * (String methods, ArrayList, HashMap, HashSet, LinkedBlockingQueue),
 * espelhando {@code JvmOpCollections.emitStringCall/emitListCall/...}.
 */
final class KofInterpreterCollections {

    static final Object NOT_HANDLED = KofInterpreterValues.NOT_HANDLED;

    Object collections(KofCall kc, Object recv, Object[] args) throws Throwable {
        Type owner = kc.ownerType();
        if (BuiltinTypes.isString(owner)) {
            return stringOps(kc, recv, args);
        }
        if (BuiltinTypes.isList(owner)) {
            return listOps(kc, recv, args);
        }
        if (BuiltinTypes.isMap(owner)) {
            return mapOps(kc, recv, args);
        }
        if (BuiltinTypes.isSet(owner)) {
            return setOps(kc, recv, args);
        }
        if (BuiltinTypes.isChannel(owner)) {
            return channelOps(kc, recv, args);
        }
        return NOT_HANDLED;
    }

    private Object stringOps(KofCall kc, Object recv, Object[] args) {
        // kof_string_concat/equals são FUNCTION (sem receiver): ambos os
        // operandos vêm em args; métodos de instância vêm com recv.
        if ("kof_string_concat".equals(kc.methodName())) {
            return recv == null ? args[0] + (String) args[1] : recv + (String) args[0];
        }
        if ("kof_string_equals".equals(kc.methodName())) {
            return recv == null ? (Objects.equals(args[0], args[1]) ? 1 : 0)
                    : (Objects.equals(recv, args[0]) ? 1 : 0);
        }
        String s = (String) recv;
        return switch (kc.methodName()) {
            case "charAt" -> (int) s.charAt(KofInterpreter.unboxInt(args[0]));
            case "substring" -> args.length == 1 ? s.substring(KofInterpreter.unboxInt(args[0]))
                    : s.substring(KofInterpreter.unboxInt(args[0]), KofInterpreter.unboxInt(args[1]));
            case "contains" -> s.contains(String.valueOf(args[0])) ? 1 : 0;
            case "startsWith" -> args.length == 1 ? (s.startsWith((String) args[0]) ? 1 : 0)
                    : (s.startsWith((String) args[0], KofInterpreter.unboxInt(args[1])) ? 1 : 0);
            case "endsWith" -> s.endsWith((String) args[0]) ? 1 : 0;
            case "equals" -> Objects.equals(s, args[0]) ? 1 : 0;
            case "equalsIgnoreCase" -> s.equalsIgnoreCase(String.valueOf(args[0])) ? 1 : 0;
            case "indexOf" -> args.length == 1 ? s.indexOf((String) args[0])
                    : s.indexOf((String) args[0], KofInterpreter.unboxInt(args[1]));
            case "lastIndexOf" -> args.length == 1 ? s.lastIndexOf((String) args[0])
                    : s.lastIndexOf((String) args[0], KofInterpreter.unboxInt(args[1]));
            case "concat" -> s + (String) args[0];
            case "trim" -> s.trim();
            case "toUpperCase" -> s.toUpperCase();
            case "toLowerCase" -> s.toLowerCase();
            case "replace" -> {
                if (args[0] instanceof String a0) yield s.replace(a0, (String) args[1]);
                yield s.replace((char) KofInterpreter.unboxInt(args[0]),
                        (char) KofInterpreter.unboxInt(args[1]));
            }
            case "split" -> args.length == 1 ? s.split((String) args[0])
                    : s.split((String) args[0], KofInterpreter.unboxInt(args[1]));
            case "hashCode" -> s.hashCode();
            case "toString" -> s;
            default -> NOT_HANDLED;
        };
    }

    private Object listOps(KofCall kc, Object recv, Object[] args) {
        @SuppressWarnings("unchecked")
        ArrayList<Object> l = (ArrayList<Object>) recv;
        return switch (kc.methodName()) {
            case "kof_list_new" -> new ArrayList<>();
            case "kof_list_add" -> {
                l.add(args[0]);
                yield null;
            }
            case "kof_list_get" -> l.get(KofInterpreter.unboxInt(args[0]));
            case "kof_list_set" -> {
                l.set(KofInterpreter.unboxInt(args[0]), args[1]);
                yield null;
            }
            case "kof_list_size" -> l.size();
            case "kof_list_contains" -> l.contains(args[0]) ? 1 : 0;
            case "kof_list_is_empty" -> l.isEmpty() ? 1 : 0;
            case "kof_list_remove" -> l.remove(KofInterpreter.unboxInt(args[0]));
            case "kof_list_clear" -> {
                l.clear();
                yield null;
            }
            default -> NOT_HANDLED;
        };
    }

    private Object mapOps(KofCall kc, Object recv, Object[] args) {
        @SuppressWarnings("unchecked")
        HashMap<Object, Object> m = (HashMap<Object, Object>) recv;
        return switch (kc.methodName()) {
            case "kof_map_new" -> new HashMap<>();
            case "kof_map_put" -> {
                // mapOf baixa como FUNCTION: args na ordem da pilha JVM
                // (pop = último empilhado) → [value, key]; com receiver a
                // ordem é [key, value]. Espelha o SWAP do emitter.
                Object key = recv == null ? args[1] : args[0];
                Object val = recv == null ? args[0] : args[1];
                Object prev = m.put(key, val);
                yield Type.isVoid(kc.returnType()) ? null : prev;
            }
            case "kof_map_get" -> m.get(args[0]);
            case "kof_map_remove" -> m.remove(args[0]);
            case "kof_map_contains" -> m.containsKey(args[0]) ? 1 : 0;
            case "kof_map_size" -> m.size();
            case "kof_map_is_empty" -> m.isEmpty() ? 1 : 0;
            case "kof_map_clear" -> {
                m.clear();
                yield null;
            }
            case "kof_map_keys" -> new ArrayList<>(m.keySet());
            case "kof_map_values" -> new ArrayList<>(m.values());
            default -> NOT_HANDLED;
        };
    }

    private Object setOps(KofCall kc, Object recv, Object[] args) {
        @SuppressWarnings("unchecked")
        HashSet<Object> s = (HashSet<Object>) recv;
        return switch (kc.methodName()) {
            case "kof_set_new" -> new HashSet<>();
            case "kof_set_add" -> {
                s.add(args[0]);
                yield Type.isVoid(kc.returnType()) ? null : (s.contains(args[0]) ? 1 : 0);
            }
            case "kof_set_contains" -> s.contains(args[0]) ? 1 : 0;
            case "kof_set_remove" -> s.remove(args[0]) ? 1 : 0;
            case "kof_set_size" -> s.size();
            case "kof_set_is_empty" -> s.isEmpty() ? 1 : 0;
            case "kof_set_clear" -> {
                s.clear();
                yield null;
            }
            default -> NOT_HANDLED;
        };
    }

    private Object channelOps(KofCall kc, Object recv, Object[] args) throws Throwable {
        @SuppressWarnings("unchecked")
        java.util.concurrent.BlockingQueue<Object> q =
                (java.util.concurrent.BlockingQueue<Object>) recv;
        return switch (kc.methodName()) {
            case "kof_channel_new" -> new java.util.concurrent.LinkedBlockingQueue<>();
            case "kof_channel_send" -> {
                q.put(args[0]);
                yield null;
            }
            case "kof_channel_receive" -> q.take();
            default -> NOT_HANDLED;
        };
    }
}
