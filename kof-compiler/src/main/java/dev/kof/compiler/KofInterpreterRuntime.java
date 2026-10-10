package dev.kof.compiler;
import dev.kof.compiler.jvm.JvmOpCollections;
import dev.kof.compiler.jvm.JvmRuntime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ponte do interpretador com o {@code KofRuntime} GERADO (mesma fonte do
 * caminho compilado — {@code JvmRuntime.ensureCompiled}) e com APIs externas
 * do JDK: despacho reflexivo com seleção de overload pelo tipo da IR,
 * coerção de argumentos e proxy para objetos Kof.
 */
public final class KofInterpreterRuntime {

    static final Object NOT_HANDLED = KofInterpreterValues.NOT_HANDLED;

    private final KofInterpreter interp;

    KofInterpreterRuntime(KofInterpreter interp) {
        this.interp = interp;
    }

    void prepareRuntime(Path workDir, boolean usesVk) throws Exception {
        Files.createDirectories(workDir);
        JvmRuntime.ensureCompiled(workDir, interp.module().classes(), usesVk);
        interp.loadRuntimeClass(workDir);
    }

    Object runtimeFn(String name, Object[] args) throws Throwable {
        return runtimeFn(name, args, null);
    }

    Object runtimeFn(String name, Object[] args, Type ret) throws Throwable {
        // UI002 (R6): kof.ui no interpretador é no-op silencioso — avisa UMA
        // vez, nunca quebra a execução (o no-op é design do target).
        if (name.startsWith("kof_ui_")) {
            interp.warnUi002(name);
        }
        // json.encode sobre objeto Kof: campos do mapa (mesmo formato do
        // runtime gerado, que lê declared fields de instâncias reais)
        if (name.equals("kof_json_encode") && args.length == 1
                && args[0] instanceof KofInterpreter.KofObj ko) {
            return encodeKof(ko);
        }
        // §106 (decisão 2b, 13/09): json.encode(Map) -> objeto JSON com chaves
        // SORTED (mesma superfície do JVM/native — determinismo). O call-site
        // baixa p/ kof_json_encode_map(map, tagDoValor) — aceito as duas formas.
        if (name.equals("kof_json_encode_map") && args.length == 2
                && args[0] instanceof Map<?, ?> m) {
            return encodeMapTagged(m, ((Number) args[1]).intValue());
        }
        if (name.equals("kof_json_encode") && args.length == 1
                && args[0] instanceof Map<?, ?> m) {
            return encodeMap(m);
        }
        // §516 fatia 2 (26/09): List<Record> no SCRIPT — o fallback por
        // reflexão chama o KofRuntime GERADO, que lê fields de instância
        // real; no interpretador o record é KofObj (mapa de campos) e saía
        // `{}`/`null` no wire (KeyError 'x' medido no motor py). Espelha o
        // encodeKof/encodeMap de cima — mesma superfície do JVM/nativo.
        if (name.startsWith("kof_json_encode_")
                && (name.equals("kof_json_encode_list") || name.equals("kof_json_encode_set"))
                && args.length == 2 && args[1] instanceof Number n4
                && n4.intValue() == 4 && args[0] instanceof java.util.List<?> l4) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l4.size(); i++) {
                if (i > 0) sb.append(',');
                Object el = l4.get(i);
                if (el instanceof KofInterpreter.KofObj ko) sb.append(encodeKof(ko));
                else if (el instanceof Map<?, ?> mm) sb.append(encodeMap(mm));
                else sb.append(runtimeFn("kof_json_encode", new Object[]{el}));
            }
            return sb.append(']').toString();
        }
        // json.decode<KofClass>: o método gerado faz Class.forName(nome) — mas
        // no interpretador a classe Kof é KofObj (NUNCA vira classe JVM).
        // Espelha encodeKof: parse com o MESMO parser do runtime gerado e
        // monta o KofObj com os campos coeridos pelos tipos da IR.
        if (name.equals("kof_json_decode_object_list") && args.length == 2
                && args[0] instanceof String json && args[1] instanceof String cn) {
            // decode<List<Classe>> — 2 args (json, className). O className é o
            // nome da classe Kof (só existe como KofObj no interpretador).
            IRClass kc = null;
            for (IRClass c : interp.module().classes()) {
                if (KofInterpreterValues.simpleOf(c.name()).equals(cn)
                        || c.name().replace('/', '.').equals(cn)) { kc = c; break; }
            }
            if (kc == null) throw new NoSuchMethodError("KofRuntime." + name + " (class '" + cn + "' not found)");
            Object parsed = runtimeFn("kof_json_parse", new Object[]{json});
            List<Object> out = new ArrayList<>();
            if (parsed instanceof List<?> l) {
                for (Object e : l) out.add(decodeKofValue(kc, e));
            }
            return out;
        }
        // §103.1 (#103): decode<Map<String,Classe>> — mesma limitação do
        // object_list: Class.forName não conhece classe Kof (vira KofObj).
        if (name.equals("kof_json_decode_object_map") && args.length == 2
                && args[0] instanceof String json && args[1] instanceof String cn) {
            IRClass kc = null;
            for (IRClass c : interp.module().classes()) {
                if (KofInterpreterValues.simpleOf(c.name()).equals(cn)
                        || c.name().replace('/', '.').equals(cn)) { kc = c; break; }
            }
            if (kc == null) throw new NoSuchMethodError("KofRuntime." + name + " (class '" + cn + "' not found)");
            Object parsed = runtimeFn("kof_json_parse", new Object[]{json});
            java.util.Map<Object, Object> out = new java.util.LinkedHashMap<>();
            if (parsed instanceof java.util.Map<?, ?> m) {
                for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                    out.put(e.getKey(), decodeKofValue(kc, e.getValue()));
                }
            }
            return out;
        }
        // #633: decode<Map<K, coleção>> (valor aninhado) — o runtime gerado
        // recebe a assinatura genérica JVM e faz o bind recursivo; aqui as
        // classes Kof são KofObj, então parseamos a assinatura para Type e
        // reusamos bindKof (mesma árvore de decisão do JVM, alvo interpretado).
        if (name.equals("kof_json_decode_typed") && args.length == 2
                && args[0] instanceof String json && args[1] instanceof String sig) {
            Type t = parseJvmSignature(sig);
            return bindKof(t, runtimeFn("kof_json_parse", new Object[]{json}));
        }
        if (name.startsWith("kof_json_decode_") && args.length == 1
                && args[0] instanceof String json) {
            IRClass kc = kofClassByDecodeName(name);
            if (kc != null) return decodeKofValue(kc, runtimeFn("kof_json_parse", new Object[]{json}));
        }
        Class<?> rt = interp.runtimeClass();
        for (Method m : rt.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            // §574: um handler de rota web é KofObj (closure IR), e o dispatch
            // do runtime gerado o chama por reflexão host ("invoke") — sem a
            // ponte o lookup falha e a rota morre em HTTP 500 no interpretador.
            if (name.equals("kof_web_route") || name.equals("kof_web_route_opts")
                    || name.equals("kof_web_sse_route") || name.equals("kof_web_ws_route")) {
                args = args.clone();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof KofInterpreter.KofObj ko) args[i] = new InterpretedCallable(interp, ko);
                }
            }
            Object[] coerced = coerceArgs(m.getParameterTypes(), args);
            try {
                Object r = m.invoke(null, coerced);
                return KofInterpreterValues.normalizeReturn(r, ret);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
        throw new NoSuchMethodError("KofRuntime." + name + "/" + args.length);
    }

    /** §106: Map -> JSON objeto com chaves sorted (mesma superfície do JVM). */
    // §106: variante com tag do valor (call-site kof_json_encode_map(map, tag))
    // — chaves sorted, valor codificado pelo tag: 0=int, 1=string, 2=bool.
    private String encodeMapTagged(Map<?, ?> m, int tag) throws Throwable {
        StringBuilder sb = new StringBuilder("{");
        java.util.SortedSet<String> keys = new java.util.TreeSet<>();
        for (Object k : m.keySet()) keys.add(String.valueOf(k));
        boolean first = true;
        for (String k : keys) {
            if (!first) sb.append(',');
            first = false;
            sb.append(runtimeFn("kof_json_encode_string", new Object[]{k}));
            sb.append(':');
            Object v = m.get(k);
            switch (tag) {
                case 1 -> sb.append(runtimeFn("kof_json_encode_string", new Object[]{v}));
                case 2 -> sb.append(runtimeFn("kof_json_encode_bool",
                        new Object[]{v instanceof Boolean b && b ? 1 : 0}));
                default -> sb.append(runtimeFn("kof_json_encode", new Object[]{v}));
            }
        }
        return sb.append('}').toString();
    }

    private String encodeMap(Map<?, ?> m) throws Throwable {
        StringBuilder sb = new StringBuilder("{");
        java.util.SortedSet<String> keys = new java.util.TreeSet<>();
        for (Object k : m.keySet()) keys.add(String.valueOf(k));
        boolean first = true;
        for (String k : keys) {
            if (!first) sb.append(',');
            first = false;
            sb.append(runtimeFn("kof_json_encode_string", new Object[]{k}));
            sb.append(':');
            Object v = m.get(k);
            if (v instanceof KofInterpreter.KofObj) {
                sb.append(encodeKof((KofInterpreter.KofObj) v));
            } else if (v instanceof Map<?, ?>) {
                sb.append(encodeMap((Map<?, ?>) v));
            } else {
                sb.append(runtimeFn("kof_json_encode", new Object[]{v}));
            }
        }
        return sb.append('}').toString();
    }

    private String encodeKof(KofInterpreter.KofObj ko) throws Throwable {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : ko.fields.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(runtimeFn("kof_json_encode_string", new Object[]{e.getKey()}));
            sb.append(':');
            Object v = e.getValue();
            if (v instanceof KofInterpreter.KofObj) {
                sb.append(encodeKof((KofInterpreter.KofObj) v));
            } else {
                sb.append(runtimeFn("kof_json_encode", new Object[]{v}));
            }
        }
        return sb.append('}').toString();
    }

    /** Classe Kof cujo nome simples sanitizado casa `kof_json_decode_<X>`. */
    private IRClass kofClassByDecodeName(String name) {
        String suffix = name.substring("kof_json_decode_".length());
        for (IRClass c : interp.module().classes()) {
            // #627: the call-site now sends the mangled FULLY-QUALIFIED name
            // (same as the generated KofRuntime); the simple-name match stays
            // for backward compatibility with default-package classes.
            if (JsonDispatch.sanitize(c.name().replace('/', '.')).equals(suffix)
                    || JsonDispatch.sanitize(KofInterpreterValues.simpleOf(c.name())).equals(suffix)) return c;
        }
        return null;
    }

    /**
     * Monta um {@link KofInterpreter.KofObj} a partir do valor JSON parseado
     * (Map/List/escalar), coerindo cada campo pelo tipo declarado na IR —
     * espelha o `kof_json_bind` do runtime gerado, mas para classes Kof que
     * só existem como KofObj no interpretador.
     */
    private Object decodeKofValue(IRClass c, Object parsed) throws Throwable {
        KofInterpreter.KofObj obj = new KofInterpreter.KofObj(c);
        if (parsed instanceof java.util.Map<?, ?> m) {
            for (IRField f : c.fields()) {
                if (java.lang.reflect.Modifier.isStatic(f.accessFlags())) continue;
                obj.fields.put(f.name(), bindKof(f.type(), m.get(f.name())));
            }
        }
        return obj;
    }

    private Object bindKof(Type type, Object raw) throws Throwable {
        if (raw == null) return null;
        IRClass nested = interp.kofClassOrNull(type);
        if (nested != null && raw instanceof java.util.Map) return decodeKofValue(nested, raw);
        if (raw instanceof List<?> l && BuiltinTypes.isList(type)) {
            Type elem = ((Type.ClassType) type).typeArguments().isEmpty()
                    ? Type.UnknownType.UNKNOWN : ((Type.ClassType) type).typeArguments().get(0);
            IRClass elemCls = interp.kofClassOrNull(elem);
            List<Object> out = new ArrayList<>(l.size());
            for (Object e : l) out.add(elemCls != null ? decodeKofValue(elemCls, e)
                    : KofInterpreterValues.coerceFor(elem, e));
            return out;
        }
        // #633: `Map<K,V>` com V composto (record/Map/List) — recursa nos
        // valores pelo type-arg (espelho do kof_json_bind JVM). Sem isto um
        // campo `Map<String,E>` ficava como mapa cru (valores LinkedHashMap).
        if (raw instanceof java.util.Map<?, ?> m && BuiltinTypes.isMap(type)) {
            Type vt = ((Type.ClassType) type).typeArguments().size() >= 2
                    ? ((Type.ClassType) type).typeArguments().get(1) : Type.UnknownType.UNKNOWN;
            java.util.Map<Object, Object> out = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> e : m.entrySet())
                out.put(e.getKey(), bindKof(vt, e.getValue()));
            return out;
        }
        return KofInterpreterValues.coerceFor(type, raw);
    }

    /** #633: assinatura genérica JVM (`Ljava/util/Map<...>;`) → Type Kof. */
    private Type parseJvmSignature(String s) {
        return parseSig(s, new int[]{0});
    }

    private Type parseSig(String s, int[] i) {
        char c = s.charAt(i[0]);
        switch (c) {
            case 'Z': i[0]++; return Type.PrimitiveType.BOOL;
            case 'B': i[0]++; return Type.PrimitiveType.BYTE;
            case 'S': i[0]++; return Type.PrimitiveType.SHORT;
            case 'I': i[0]++; return Type.PrimitiveType.INT;
            case 'J': i[0]++; return Type.PrimitiveType.LONG;
            case 'F': i[0]++; return Type.PrimitiveType.FLOAT;
            case 'D': i[0]++; return Type.PrimitiveType.DOUBLE;
            case 'C': i[0]++; return Type.PrimitiveType.CHAR;
            case 'V': i[0]++; return Type.PrimitiveType.VOID;
            case '[': i[0]++; return new Type.ArrayType(parseSig(s, i));
            case 'L': {
                int start = ++i[0];
                int j = start;
                while (s.charAt(j) != ';' && s.charAt(j) != '<') j++;
                String internal = s.substring(start, j);
                List<Type> typeArgs = new ArrayList<>();
                if (s.charAt(j) == '<') {
                    i[0] = j + 1;
                    while (s.charAt(i[0]) != '>') typeArgs.add(parseSig(s, i));
                    i[0]++;
                } else {
                    i[0] = j;
                }
                if (i[0] < s.length() && s.charAt(i[0]) == ';') i[0]++;
                return kofTypeFor(internal, typeArgs);
            }
            default:
                throw new IllegalArgumentException(
                        "kof_json_decode_typed: bad signature '" + s + "'");
        }
    }

    private Type kofTypeFor(String internal, List<Type> args) {
        return switch (internal) {
            // toGenericSignature apaga as coleções Kof para as classes
            // CONCRETAS da JVM (List→ArrayList, Map→HashMap, Set→HashSet) —
            // por isso as três formas de cada um mapeiam para o tipo Kof.
            case "java/util/Map", "java/util/HashMap",
                 "java/util/LinkedHashMap", "java/util/TreeMap",
                 "java/util/SortedMap" -> new Type.ClassType("kof", "Map", args);
            case "java/util/List", "java/util/ArrayList",
                 "java/util/LinkedList", "java/util/AbstractList" -> new Type.ClassType("kof", "List", args);
            case "java/util/Set", "java/util/HashSet",
                 "java/util/LinkedHashSet", "java/util/TreeSet",
                 "java/util/SortedSet" -> new Type.ClassType("kof", "Set", args);
            case "java/lang/String" -> BuiltinTypes.STRING;
            case "java/lang/Object" -> new Type.ClassType("java.lang", "Object", List.of());
            default -> {
                int slash = internal.lastIndexOf('/');
                if (slash < 0) yield new Type.ClassType("", internal, args);
                yield new Type.ClassType(internal.substring(0, slash).replace('/', '.'),
                        internal.substring(slash + 1), args);
            }
        };
    }

    Object[] coerceArgs(Class<?>[] params, Object[] args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) out[i] = coerceFor(params[i], args[i]);
        return out;
    }

    Object coerceFor(Class<?> param, Object v) {
        if (v == null) return null;
        if (param.isInstance(v)) return v;
        if (v instanceof Number num) {
            if (param == int.class || param == Integer.class) return num.intValue();
            if (param == long.class || param == Long.class) return num.longValue();
            if (param == double.class || param == Double.class) return num.doubleValue();
            if (param == float.class || param == Float.class) return num.floatValue();
            if (param == byte.class || param == Byte.class) return num.byteValue();
            if (param == short.class || param == Short.class) return num.shortValue();
            if (param == char.class || param == Character.class) return (char) num.intValue();
            if (param == boolean.class || param == Boolean.class) return num.intValue() != 0;
        }
        if (v instanceof Character ch) {
            if (param == int.class || param == Integer.class) return (int) ch;
            if (param == String.class || param == Object.class || param == CharSequence.class)
                return ch.toString();
        }
        if (param == String.class || param == CharSequence.class) {
            if (v instanceof KofInterpreter.KofObj) return interp.valueToString(v);
            return String.valueOf(v);
        }
        if ((param == Object.class || param.isInterface()) && v instanceof KofInterpreter.KofObj ko) {
            return wrapForExternal(ko);
        }
        return v;
    }

    /**
     * Objeto Kof passado para API externa (ex.: Runnable de spawn): vira
     * proxy dinâmico que encaminha para o método interpretado.
     */
    Object wrapForExternal(KofInterpreter.KofObj ko) {
        IRClass c = ko.clazz;
        List<Class<?>> ifaces = new ArrayList<>();
        for (String i : c.interfaces()) {
            try {
                ifaces.add(Class.forName(i.replace('/', '.')));
            } catch (Throwable ignored) {
            }
        }
        if (ifaces.isEmpty()) return ko;
        Class<?>[] arr = ifaces.toArray(new Class<?>[0]);
        return java.lang.reflect.Proxy.newProxyInstance(
                interp.runtimeClass() == null ? getClass().getClassLoader()
                        : interp.runtimeClass().getClassLoader(),
                arr,
                (proxy, method, margs) -> {
                    IRClass cc = interp.kofClassOf(new Type.ClassType(
                            KofInterpreterValues.pkgOf(c.name()),
                            KofInterpreterValues.simpleOf(c.name()), List.of()));
                    IRMethod m = interp.findKofMethod(cc, method.getName(),
                            margs == null ? 0 : margs.length);
                    if (m == null) throw new NoSuchMethodError(c.name() + "." + method.getName());
                    Object[] full = KofInterpreter.prepend(proxy,
                            margs == null ? new Object[0] : margs);
                    return interp.evalKof(cc, m, full, true);
                });
    }

    Object newExternal(Type type, Object[] args) throws Throwable {
        if (type instanceof Type.ClassType ct) {
            Class<?> c = KofInterpreterValues.classForType(ct);
            if (args.length == 0) return c.getDeclaredConstructor().newInstance();
            for (var ctor : c.getDeclaredConstructors()) {
                if (ctor.getParameterCount() != args.length) continue;
                try {
                    return ctor.newInstance(coerceArgs(ctor.getParameterTypes(), args));
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        throw new NoSuchMethodError("new " + type + "/" + args.length);
    }

    Object invokeExternal(KofCall kc, Object recv, Object[] args) throws Throwable {
        String name = kc.methodName();
        Class<?> c;
        if (recv != null) {
            c = recv.getClass();
        } else if (kc.ownerType() instanceof Type.ClassType ct) {
            c = KofInterpreterValues.classForType(ct);
        } else {
            throw new NoSuchMethodError("call on " + kc.ownerType() + "." + name);
        }
        List<Type> irParams = kc.parameterTypes();
        Method best = null;
        int bestScore = -1;
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) != (recv == null)) continue;
            int score = signatureScore(m.getParameterTypes(), irParams, args);
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        if (best == null) throw new NoSuchMethodError(c.getName() + "." + name + "/" + args.length);
        try {
            return KofInterpreterValues.normalizeReturn(
                    best.invoke(recv, coerceArgs(best.getParameterTypes(), args)),
                    kc.returnType());
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        } catch (IllegalArgumentException ignored) {
            throw new NoSuchMethodError(c.getName() + "." + name + "/" + args.length);
        }
    }

    /**
     * Pontua um overload: +2 por parâmetro que casa o tipo da IR (primitivo
     * vs boxed distinto conta como NÃO-casa — evita Boolean.valueOf(String)
     * ganhar de Boolean.valueOf(boolean)); +1 se o valor atual já é
     * instance-of. Retorno -1 se algum parâmetro é claramente incompatível.
     */
    private int signatureScore(Class<?>[] params, List<Type> irParams, Object[] args) {
        int score = 0;
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];
            Type ir = i < irParams.size() ? irParams.get(i) : null;
            boolean primitiveIr = JvmOpCollections.isPrimitiveType(ir);
            if (p.isPrimitive() != primitiveIr && ir != null
                    && !isBoxedIr(ir, p)) {
                if (p != Object.class) return -1;
            }
            // §124: arg null não pode casar com parâmetro ARRAY (ex.: o
            // println(null) baixa valueOf(Unknown) e o scorer dava empate
            // entre valueOf(char[]) e valueOf(Object) → ordem de getMethods()
            // escolhia char[] → NPE "Cannot read the array length". Array só
            // compete quando o IR declara array de verdade.
            if (args[i] == null && p.isArray() && !(ir instanceof Type.ArrayType)) {
                return -1;
            }
            if (ir != null && primitiveMatchesIr(p, ir)) score += 2;
            if (args[i] != null && p.isInstance(args[i])) score += 1;
        }
        return score;
    }

    private static boolean isBoxedIr(Type ir, Class<?> p) {
        if (!(ir instanceof Type.PrimitiveType pt)) return false;
        return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "int" -> p == Integer.class;
            case "long" -> p == Long.class;
            case "double" -> p == Double.class;
            case "float" -> p == Float.class;
            case "bool" -> p == Boolean.class;
            case "char" -> p == Character.class;
            default -> false;
        };
    }

    private static boolean primitiveMatchesIr(Class<?> p, Type ir) {
        if (!(ir instanceof Type.PrimitiveType pt)) return false;
        return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "int" -> p == int.class || p == Integer.class;
            case "long" -> p == long.class || p == Long.class;
            case "double" -> p == double.class || p == Double.class;
            case "float" -> p == float.class || p == Float.class;
            case "bool" -> p == boolean.class || p == Boolean.class;
            case "char" -> p == char.class || p == Character.class;
            default -> false;
        };
    }
}
