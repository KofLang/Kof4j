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
            // D-FULL-PARITY-050 row 11: faltava no SCRIPT — caía em NOT_HANDLED
            // (→0) mesmo com o JVM implementando; agora delega ao próprio JDK
            // (CASE_INSENSITIVE_ORDER por code unit).
            case "compareToIgnoreCase" -> s.compareToIgnoreCase(String.valueOf(args[0]));
            case "indexOf" -> args.length == 1 ? s.indexOf((String) args[0])
                    : s.indexOf((String) args[0], KofInterpreter.unboxInt(args[1]));
            case "lastIndexOf" -> args.length == 1 ? s.lastIndexOf((String) args[0])
                    : s.lastIndexOf((String) args[0], KofInterpreter.unboxInt(args[1]));
            case "concat" -> s + (String) args[0];
            case "trim" -> s.trim();
            // §145 (12/09, #101): isEmpty não estava no registry nem aqui —
            // o SCRIPT devolvia NOT_HANDLED→0 (falso) para string não-vazia.
            case "isEmpty" -> s.isEmpty() ? 1 : 0;
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
            // #382 — indexOf/lastIndexOf: box pelo tipo do ARG (bug 35 como
            // contains; o tag extra do call-site é ignorado — equals de
            // conteúdo é o do java.util). -1 quando ausente (mesmo oracle).
            case "kof_list_index_of", "kof_list_last_index_of" -> {
                Type argT = kc.parameterTypes().isEmpty() ? elemType : kc.parameterTypes().get(0);
                yield "kof_list_index_of".equals(kc.methodName())
                        ? l.indexOf(box(argT, args[0]))
                        : l.lastIndexOf(box(argT, args[0]));
            }
            // #382 — addAll: 1 se a lista mudou (false p/ coleção vazia,
            // espelha java.util). O arg chega como o próprio ArrayList.
            case "kof_list_add_all" -> (l.addAll((java.util.Collection<?>) args[0]) ? 1 : 0);
            // #382 — subList: cópia materializada (o IR declara List
            // concreta; view do java.util não tem contrato na linguagem).
            case "kof_list_sub_list" -> new ArrayList<>(
                    l.subList(KofInterpreter.unboxInt(args[0]), KofInterpreter.unboxInt(args[1])));
            // pagination P1 — take/drop/slice: janela materializada com
            // clamping honesto; negativos = erro nomeado PAGINATION.
            case "kof_list_take" -> {
                int n = KofInterpreter.unboxInt(args[0]);
                if (n < 0) throw new RuntimeException("PAGINATION: count must be >= 0");
                int size = l.size();
                yield new ArrayList<>(l.subList(0, n < size ? n : size));
            }
            case "kof_list_drop" -> {
                int n = KofInterpreter.unboxInt(args[0]);
                if (n < 0) throw new RuntimeException("PAGINATION: count must be >= 0");
                int size = l.size();
                yield new ArrayList<>(l.subList(n < size ? n : size, size));
            }
            case "kof_list_slice" -> {
                int offset = KofInterpreter.unboxInt(args[0]);
                int limit = KofInterpreter.unboxInt(args[1]);
                if (offset < 0 || limit < 0) {
                    throw new RuntimeException("PAGINATION: limit/offset must be >= 0");
                }
                int size = l.size();
                int start = offset < size ? offset : size;
                int remaining = size - start;
                yield new ArrayList<>(l.subList(start, start + (limit < remaining ? limit : remaining)));
            }
            // D-MULTIPARADIGMA-PHASE1A slice 1e — distinct dedups by Java
            // equals (same rule as contains on this target); the tag arg is
            // Native-only and ignored here.
            case "kof_list_distinct" -> {
                ArrayList<Object> out = new ArrayList<>();
                for (Object o : l) {
                    if (!out.contains(o)) out.add(o);
                }
                yield out;
            }
            // #382 — sort: ordem natural (Comparator null = mesma escolha
            // do JVM; o gate SEM097 já restringiu o domínio — o NAT001/Float
            // caiu em 21/09, §352).
            case "kof_list_sort" -> {
                l.sort(null);
                yield null;
            }
            // D-MULTIPARADIGMA-PHASE1A slice 1g — sorted devolve cópia
            // ordenada (nunca muta o receiver); o tag é Native-only.
            case "kof_list_sorted" -> {
                var out = new ArrayList<>(l);
                out.sort(null);
                yield out;
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
                // D-NULL-INTENT/I7: get/put/remove devolvem V? de verdade —
                // NÃO chama unbox() aqui (Boolean->Integer 1/0): o valor
                // sai como referência de verdade (Boolean/Integer/etc.),
                // espelhando o JVM (JvmOpCollections.emitNullablyBoxedMapResult,
                // só CHECKCAST, nunca unbox). unbox() continua usado em
                // List/Set, que NÃO viraram Nullable — só Map (#278).
                yield Type.isVoid(kc.returnType()) ? null : prev;
            }
            case "kof_map_get" -> {
                // D-NULL-INTENT/I7 (supersede SG-008 default-guard): get()
                // devolve V? de verdade — ausência é null observável, nunca
                // substituído pelo default do primitivo (espelha o fix do
                // emit JVM, JvmOpCollections.kof_map_get/#278). Sem unbox():
                // o valor sai como referência de verdade.
                yield m.get(box(kT, args[0]));
            }
            case "kof_map_remove" -> m.remove(box(kT, args[0]));
            case "kof_map_get_or_default" -> unbox(vT, m.getOrDefault(box(kT, args[0]), box(vT, args[1])));
            case "kof_map_contains" -> m.containsKey(box(kT, args[0])) ? 1 : 0;
            // #386 — containsValue: kT aqui é exatamente o tipo do ARG
            // (parameterTypes[0]) — para este op o arg é o VALOR candidato,
            // então o box por ele é o correto (equals de conteúdo java.util).
            case "kof_map_contains_value" -> m.containsValue(box(kT, args[0])) ? 1 : 0;
            // #386 — putIfAbsent: sempre INSTANCE (nunca o swap de args do
            // mapOf-FUNCTION); contrato do put (anterior OU null) — sem
            // unbox() no resultado (D-NULL-INTENT/I7, null ausente é
            // observável), box key+value como o put acima.
            case "kof_map_put_if_absent" -> m.putIfAbsent(box(kT, args[0]), box(vT, args[1]));
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
