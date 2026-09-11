package dev.kof.compiler;
import dev.kof.compiler.jvm.JvmOpCollections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

/**
 * Coleções nativas do interpretador — os MESMOS alvos do emitter JVM
 * (String methods, ArrayList, HashMap, HashSet, LinkedBlockingQueue),
 * espelhando {@code JvmOpCollections.emitStringCall/emitListCall/...}.
 */
public final class KofInterpreterCollections {

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
        Type elemType = elemOf(kc.ownerType());
        return switch (kc.methodName()) {
            case "kof_list_new" -> new ArrayList<>();
            case "kof_list_add" -> {
                l.add(box(elemType, args[0]));
                yield null;
            }
            case "kof_list_get" -> unbox(elemType, l.get(KofInterpreter.unboxInt(args[0])));
            case "kof_list_set" -> {
                l.set(KofInterpreter.unboxInt(args[0]), box(elemType, args[1]));
                yield null;
            }
            case "kof_list_size" -> l.size();
            case "kof_list_contains" -> {
                // bug 35 (JVM: box pelo tipo do ARGUMENTO — elemType de
                // listOf() nasce Unknown) espelhado no interpretador.
                Type argT = kc.parameterTypes().isEmpty() ? elemType : kc.parameterTypes().get(0);
                yield l.contains(box(argT, args[0])) ? 1 : 0;
            }
            case "kof_list_is_empty" -> l.isEmpty() ? 1 : 0;
            case "kof_list_remove" -> unbox(elemType, l.remove(KofInterpreter.unboxInt(args[0])));
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
        Type keyType = Type.UnknownType.UNKNOWN;
        Type valueType = Type.UnknownType.UNKNOWN;
        if (kc.ownerType() instanceof Type.ClassType ct && ct.typeArguments().size() == 2
                && !(ct.typeArguments().get(0) instanceof Type.UnknownType)) {
            keyType = ct.typeArguments().get(0);
            valueType = ct.typeArguments().get(1);
        }
        if (!kc.parameterTypes().isEmpty()) {
            keyType = kc.parameterTypes().get(0);
            if (kc.parameterTypes().size() > 1 && !BuiltinTypes.isList(kc.parameterTypes().get(1))) {
                valueType = kc.parameterTypes().get(1);
            }
        }
        final Type kT = keyType;
        final Type vT = valueType;
        return switch (kc.methodName()) {
            case "kof_map_new" -> new HashMap<>();
            case "kof_map_put" -> {
                // mapOf baixa como FUNCTION: args na ordem da pilha JVM
                // (pop = último empilhado) → [value, key]; com receiver a
                // ordem é [key, value]. Espelha o SWAP do emitter.
                Object key = recv == null ? args[1] : args[0];
                Object val = recv == null ? args[0] : args[1];
                Object prev = m.put(box(kT, key), box(vT, val));
                yield Type.isVoid(kc.returnType()) ? null
                        : prevOrDefault(unbox(vT, prev), kc.returnType());
            }
            case "kof_map_get" -> {
                // SG-008 (bug 87): get() devolve V? — o miss é null comparável
                // (`x == null` → true). Mas se o USO espera primitivo
                // (Nullable(primitivo) desembrulhado no typer), o valor null
                // vira o default do primitivo (espelha o guard-unbox do emit
                // JVM, JvmOpCollections.kof_map_get): 0/0.0/false.
                Object v = m.get(box(kT, args[0]));
                if (v == null && kc.returnType() instanceof Type.NullableType nt
                        && nt.inner() instanceof Type.PrimitiveType) {
                    yield KofInterpreterMembers.defaultValue(nt.inner());
                }
                yield unbox(vT, v);
            }
            case "kof_map_remove" -> prevOrDefault(unbox(vT, m.remove(box(kT, args[0]))), kc.returnType());
            case "kof_map_contains" -> m.containsKey(box(kT, args[0])) ? 1 : 0;
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

    /**
     * §112: `put`/`remove` devolvem o valor ANTERIOR, que pode ser null
     * (put novo / remove de chave ausente). Quando o uso espera o primitivo
     * (typer devolve V, não V?), o null estourava NullPointerException no
     * unbox (exit=1). Guard com default do primitivo, espelhando o
     * kof_map_get (SG-008) e o emitPrevValueUnbox do JvmOpCollections.
     */
    private static Object prevOrDefault(Object prev, Type declared) {
        Type inner = declared instanceof Type.NullableType nt ? nt.inner() : declared;
        if (prev == null && inner instanceof Type.PrimitiveType
                && KofInterpreterMembers.defaultValue(inner) != null) {
            return KofInterpreterMembers.defaultValue(inner);
        }
        return prev;
    }

    private Object setOps(KofCall kc, Object recv, Object[] args) {
        @SuppressWarnings("unchecked")
        HashSet<Object> s = (HashSet<Object>) recv;
        Type elemType = elemOf(kc.ownerType());
        if (!kc.parameterTypes().isEmpty()) elemType = kc.parameterTypes().get(0);
        final Type eT = elemType;
        return switch (kc.methodName()) {
            case "kof_set_new" -> new HashSet<>();
            case "kof_set_add" -> {
                // §112: add devolve "foi ADICIONADO?" (false se já existia).
                // O código antigo fazia s.add() e depois s.contains() — sempre
                // true. HashSet.add retorna o correto (mesmo oracle do JVM).
                // §108: box por tipo na inclusão (Boolean no HashSet).
                boolean added = s.add(box(eT, args[0]));
                yield Type.isVoid(kc.returnType()) ? null : (added ? 1 : 0);
            }
            case "kof_set_contains" -> s.contains(box(eT, args[0])) ? 1 : 0;
            case "kof_set_remove" -> s.remove(box(eT, args[0])) ? 1 : 0;
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
        Type elemType = BuiltinTypes.channelElement(kc.ownerType());
        return switch (kc.methodName()) {
            case "kof_channel_new" -> new java.util.concurrent.LinkedBlockingQueue<>();
            case "kof_channel_send" -> {
                q.put(box(elemType, args[0]));
                yield null;
            }
            case "kof_channel_receive" -> unbox(elemType, q.take());
            default -> NOT_HANDLED;
        };
    }

    // ── §108: box/unbox espelhando JvmOpCollections (Boolean ↔ Integer 0/1;
    // int/long/double/char já são o mesmo objeto nos dois storage — o
    // interpretador guarda-os como Number e o emitter JVM boxia p/ o mesmo
    // wrapper —, logo só Bool precisa de conversão; §108: "Char NÃO precisa") ──

    private static Type elemOf(Type t) {
        if (t instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    private static boolean isBool(Type t) {
        return t instanceof Type.PrimitiveType pt
                && "bool".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    private static Object box(Type elemType, Object v) {
        if (isBool(elemType) && v instanceof Number num) return num.intValue() != 0;
        return v;
    }

    private static Object unbox(Type elemType, Object v) {
        if (isBool(elemType) && v instanceof Boolean b) return b ? 1 : 0;
        return v;
    }
}
