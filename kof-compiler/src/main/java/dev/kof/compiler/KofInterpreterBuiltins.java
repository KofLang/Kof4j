package dev.kof.compiler;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Builtins do {@link KofInterpreter}: aritmética/comparação (semântica
 * JVM exata do emitter), coleções nativas (ArrayList/HashMap/HashSet/
 * LinkedBlockingQueue — os mesmos alvos do bytecode), lambdas Kof
 * (spawn/await/map/filter/reduce/interval) e despacho reflexivo ao
 * KofRuntime gerado para o resto (json/io/http/db/...).
 */
final class KofInterpreterBuiltins {

    static final Object NOT_HANDLED = new Object();

    private final KofInterpreter interp;
    private final List<Thread> tasks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<Object, Thread> taskThreads = new java.util.concurrent.ConcurrentHashMap<>();

    KofInterpreterBuiltins(KofInterpreter interp) {
        this.interp = interp;
    }

    // ── runtime gerado ──────────────────────────────────────────────

    void prepareRuntime(Path workDir, boolean usesVk) throws Exception {
        Files.createDirectories(workDir);
        JvmRuntime.ensureCompiled(workDir, interp.module().classes(), usesVk);
        interp.loadRuntimeClass(workDir);
    }

    Object runtimeFn(String name, Object[] args) throws Throwable {
        return runtimeFn(name, args, null);
    }

    Object runtimeFn(String name, Object[] args, Type ret) throws Throwable {
        // json.encode sobre objeto Kof: campos do mapa (mesmo formato do
        // runtime gerado, que lê declared fields de instâncias reais)
        if (name.equals("kof_json_encode") && args.length == 1 && args[0] instanceof KofInterpreter.KofObj ko) {
            return encodeKof(ko);
        }
        Class<?> rt = interp.runtimeClass();
        for (Method m : rt.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Object[] coerced = coerceArgs(m.getParameterTypes(), args);
            try {
                Object r = m.invoke(null, coerced);
                return normalizeReturn(r, ret);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
        throw new NoSuchMethodError("KofRuntime." + name + "/" + args.length);
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

    /**
     * Normaliza retorno de reflexão para a representação da pilha JVM:
     * primitivo bool → Integer 0/1, primitivo char → Integer; tipos boxed
     * (Boolean/Character) ficam como objeto. Guiado pelo tipo de retorno da
     * IR — senão Boolean.valueOf(v) viraria 1 e println imprimiria "1".
     */
    static Object normalizeReturn(Object r, Type ret) {
        if (r == null) return null;
        if (ret instanceof Type.PrimitiveType pt) {
            if (Type.canonicalPrimitiveName(pt.name()).equals("bool") && r instanceof Boolean b) {
                return b ? 1 : 0;
            }
            if (Type.canonicalPrimitiveName(pt.name()).equals("char") && r instanceof Character c) {
                return (int) c;
            }
        }
        return r;
    }

    static Object normalizeReturn(Object r) {
        if (r instanceof Boolean b) return b ? 1 : 0;
        if (r instanceof Character c) return (int) c;
        return r;
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
            if (v instanceof KofInterpreter.KofObj) return kofToString(v);
            return String.valueOf(v);
        }
        if ((param == Object.class || param.isInterface()) && v instanceof KofInterpreter.KofObj ko) {
            return wrapForExternal(ko);
        }
        return v;
    }

    Object coerceFor(Type type, Object v) {
        if (v == null || type == null) return v;
        if (type instanceof Type.NullableType nt) return coerceFor(nt.inner(), v);
        if (!(type instanceof Type.PrimitiveType pt)) return v;
        return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "long" -> v instanceof Number n ? n.longValue() : v;
            case "double" -> v instanceof Number n ? n.doubleValue() : v;
            case "float" -> v instanceof Number n ? n.floatValue() : v;
            case "int", "char", "bool", "byte", "short" -> v instanceof Number n ? n.intValue() : v;
            default -> v;
        };
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
                            pkgOf(c.name()), simpleOf(c.name()), List.of()));
                    IRMethod m = interp.findKofMethod(cc, method.getName(),
                            margs == null ? 0 : margs.length);
                    if (m == null) throw new NoSuchMethodError(c.name() + "." + method.getName());
                    Object[] full = KofInterpreter.prepend(proxy, margs == null ? new Object[0] : margs);
                    return interp.evalKof(cc, m, full, true);
                });
    }

    private static String pkgOf(String internal) {
        int sl = internal.lastIndexOf('/');
        return sl < 0 ? "" : internal.substring(0, sl).replace('/', '.');
    }

    private static String simpleOf(String internal) {
        int sl = internal.lastIndexOf('/');
        return sl < 0 ? internal : internal.substring(sl + 1);
    }

    // ── aritmética / comparação (semântica JVM do emitBinary) ───────

    Object binary(KofBinary kb, Object a, Object b) {
        Type t = kb.operandType();
        if (kb.op() == KofBinaryOp.EQ || kb.op() == KofBinaryOp.NE) {
            // referência: igual ao IF_ACMPEQ/IF_ACMPNE do bytecode (identidade)
            boolean eq = isRefType(t) ? a == b : numEq(a, b);
            boolean want = kb.op() == KofBinaryOp.EQ;
            return (eq == want) ? 1 : 0;
        }
        if (isRefType(t) && (kb.op() == KofBinaryOp.LT || kb.op() == KofBinaryOp.GT
                || kb.op() == KofBinaryOp.LE || kb.op() == KofBinaryOp.GE)) {
            int c = compareRefs(a, b);
            return cmpResult(kb.op(), c);
        }
        if (isFloatType(t)) {
            double x = ((Number) a).doubleValue(), y = ((Number) b).doubleValue();
            return switch (kb.op()) {
                case ADD -> (float) (x + y);
                case SUB -> (float) (x - y);
                case MUL -> (float) (x * y);
                case DIV -> (float) (x / y);
                case MOD -> (float) (x % y);
                default -> cmpResult(kb.op(), Float.compare((float) x, (float) y));
            };
        }
        if (isDoubleType(t)) {
            double x = ((Number) a).doubleValue(), y = ((Number) b).doubleValue();
            return switch (kb.op()) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                case DIV -> x / y;
                case MOD -> x % y;
                default -> cmpResult(kb.op(), Double.compare(x, y));
            };
        }
        if (isLongType(t)) {
            long x = ((Number) a).longValue(), y = ((Number) b).longValue();
            return switch (kb.op()) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                case DIV -> x / y;
                case MOD -> x % y;
                case AND -> x & y;
                case OR -> x | y;
                case XOR -> x ^ y;
                case SHL -> x << y;
                case SHR -> x >> y;
                case USHR -> x >>> y;
                default -> cmpResult(kb.op(), Long.compare(x, y));
            };
        }
        int x = KofInterpreter.unboxInt(a), y = KofInterpreter.unboxInt(b);
        return switch (kb.op()) {
            case ADD -> x + y;
            case SUB -> x - y;
            case MUL -> x * y;
            case DIV -> x / y;
            case MOD -> x % y;
            case AND -> x & y;
            case OR -> x | y;
            case XOR -> x ^ y;
            case SHL -> x << y;
            case SHR -> x >> y;
            case USHR -> x >>> y;
            default -> cmpResult(kb.op(), Integer.compare(x, y));
        };
    }

    private static int cmpResult(KofBinaryOp op, int c) {
        return switch (op) {
            case LT -> c < 0 ? 1 : 0;
            case LE -> c <= 0 ? 1 : 0;
            case GT -> c > 0 ? 1 : 0;
            case GE -> c >= 0 ? 1 : 0;
            case EQ -> c == 0 ? 1 : 0;
            case NE -> c != 0 ? 1 : 0;
            default -> 0;
        };
    }

    private static boolean numEq(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            if (a instanceof Long || b instanceof Long) return x.longValue() == y.longValue();
            if (a instanceof Double || b instanceof Double || a instanceof Float || b instanceof Float) {
                return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
            }
            return x.intValue() == y.intValue();
        }
        return Objects.equals(a, b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareRefs(Object a, Object b) {
        if (a instanceof Comparable ca && b instanceof Comparable cb) {
            return ((Comparable) ca).compareTo(cb);
        }
        throw new ClassCastException("not comparable");
    }

    static boolean isRefType(Type t) {
        return t instanceof Type.ClassType || t instanceof Type.ArrayType
                || t instanceof Type.TypeVariable
                || (t instanceof Type.NullableType nt && !(nt.inner() instanceof Type.PrimitiveType));
    }

    private static boolean isLongType(Type t) {
        return JvmOpCollections.isPrimitiveOf(t, "long");
    }

    private static boolean isFloatType(Type t) {
        return JvmOpCollections.isPrimitiveOf(t, "float");
    }

    private static boolean isDoubleType(Type t) {
        return JvmOpCollections.isPrimitiveOf(t, "double");
    }

    boolean compare(KofComparison cmp, Type t, Object a, Object b) {
        if (isRefType(t)) {
            int c = (cmp == KofComparison.EQ || cmp == KofComparison.NE)
                    ? (Objects.equals(a, b) ? 0 : 1) : compareRefs(a, b);
            return switch (cmp) {
                case EQ -> c == 0;
                case NE -> c != 0;
                case LT -> c < 0;
                case LE -> c <= 0;
                case GT -> c > 0;
                case GE -> c >= 0;
            };
        }
        if (isLongType(t)) {
            long x = ((Number) a).longValue(), y = ((Number) b).longValue();
            return switch (cmp) {
                case EQ -> x == y; case NE -> x != y; case LT -> x < y;
                case LE -> x <= y; case GT -> x > y; case GE -> x >= y;
            };
        }
        if (isFloatType(t) || isDoubleType(t)) {
            double x = ((Number) a).doubleValue(), y = ((Number) b).doubleValue();
            int c = Double.compare(x, y);
            return switch (cmp) {
                case EQ -> c == 0; case NE -> c != 0; case LT -> c < 0;
                case LE -> c <= 0; case GT -> c > 0; case GE -> c >= 0;
            };
        }
        int x = KofInterpreter.unboxInt(a), y = KofInterpreter.unboxInt(b);
        return switch (cmp) {
            case EQ -> x == y; case NE -> x != y; case LT -> x < y;
            case LE -> x <= y; case GT -> x > y; case GE -> x >= y;
        };
    }

    Object unary(KofUnary ku, Object v) {
        Type t = ku.operandType();
        return switch (ku.op()) {
            case NEG -> isLongType(t) ? -((Number) v).longValue()
                    : isFloatType(t) ? -((Number) v).floatValue()
                    : isDoubleType(t) ? -((Number) v).doubleValue()
                    : -KofInterpreter.unboxInt(v);
            case NOT -> KofInterpreter.unboxInt(v) == 0 ? 1 : 0;
            case I2L -> ((Number) v).longValue();
            case I2F -> ((Number) v).floatValue();
            case I2D -> ((Number) v).doubleValue();
            case I2C -> (char) KofInterpreter.unboxInt(v);
            case L2I -> ((Number) v).intValue();
            case L2F -> ((Number) v).floatValue();
            case L2D -> ((Number) v).doubleValue();
            case F2D -> ((Number) v).doubleValue();
            case D2F -> ((Number) v).floatValue();
            case D2I -> ((Number) v).intValue();
            case F2I -> ((Number) v).intValue();
            case D2L -> ((Number) v).longValue();
            case F2L -> ((Number) v).longValue();
        };
    }

    boolean instanceOf(Type type, Object v) {
        if (v == null) return false;
        if (type instanceof Type.ClassType ct) {
            if (v instanceof KofInterpreter.KofObj ko) {
                IRClass c = interp.kofClassOrNull(type);
                if (c != null) {
                    IRClass cur = ko.clazz;
                    while (cur != null) {
                        if (cur.name().equals(c.name())) return true;
                        for (String i : cur.interfaces()) {
                            if (i.equals(ct.internalName())) return true;
                        }
                        cur = interp.classByInternal(cur.superName());
                    }
                    return false;
                }
                return false;
            }
            try {
                return classForType(ct).isInstance(v);
            } catch (Throwable e) {
                return false;
            }
        }
        if (type instanceof Type.ArrayType) return v.getClass().isArray();
        return false;
    }

    Class<?> classForType(Type.ClassType ct) throws ClassNotFoundException {
        String name = ct.packageName().isEmpty() ? ct.name()
                : ct.packageName() + "." + ct.name();
        return Class.forName(name);
    }

    Object newArray(Type elementType, int size) {
        if (elementType instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "long" -> new long[size];
                case "double" -> new double[size];
                case "float" -> new float[size];
                case "char" -> new char[size];
                case "bool" -> new boolean[size];
                default -> new int[size];
            };
        }
        Class<?> comp = Object.class;
        if (elementType instanceof Type.ClassType ct) {
            try {
                comp = classForType(ct);
            } catch (Throwable ignored) {
            }
        }
        return Array.newInstance(comp, size);
    }

    Object arrayLoad(KofArrayLoad al, Object arr, int idx) {
        Object v = Array.get(arr, idx);
        Type t = al.elementType();
        if (t instanceof Type.PrimitiveType pt && Type.canonicalPrimitiveName(pt.name()).equals("bool")) {
            return ((Boolean) v) ? 1 : 0;
        }
        return v;
    }

    void arrayStore(KofArrayStore as, Object arr, int idx, Object v) {
        Type t = as.elementType();
        if (t instanceof Type.PrimitiveType pt && Type.canonicalPrimitiveName(pt.name()).equals("bool")) {
            Array.set(arr, idx, KofInterpreter.unboxInt(v) != 0);
            return;
        }
        Array.set(arr, idx, v);
    }

    // ── coleções nativas (mesmos alvos do emitter) ──────────────────

    Object collections(KofCall kc, Object recv, Object[] args) throws Throwable {
        String name = kc.methodName();
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

    // ── lambdas / concorrência ──────────────────────────────────────

    Object lambdaAware(KofCall kc, Object recv, Object[] args) throws Throwable {
        String name = kc.methodName();
        switch (name) {
            case "kof_list_map": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                ArrayList<Object> out = new ArrayList<>();
                for (Object o : src) out.add(interp.invokeLambda(args[1], new Object[]{o}));
                return out;
            }
            case "kof_list_filter": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                ArrayList<Object> out = new ArrayList<>();
                for (Object o : src) {
                    Object keep = interp.invokeLambda(args[1], new Object[]{o});
                    if (Boolean.TRUE.equals(keep) || Integer.valueOf(1).equals(keep)) out.add(o);
                }
                return out;
            }
            case "kof_list_reduce": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                Object acc = args[1];
                for (Object o : src) acc = interp.invokeLambda(args[2], new Object[]{acc, o});
                return acc;
            }
            case "kof_spawn_result": {
                CompletableFuture<Object> future = new CompletableFuture<>();
                startTask(future, () -> future.complete(interp.invokeLambda(args[0], new Object[0])));
                return future;
            }
            case "kof_spawn": {
                startTask(null, () -> interp.invokeLambda(args[0], new Object[0]));
                return null;
            }
            case "kof_await": {
                if (args[0] instanceof java.util.concurrent.Future<?> fu) {
                    try {
                        return normalizeReturn(fu.get());
                    } catch (java.util.concurrent.ExecutionException e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof RuntimeException re) throw re;
                        if (cause instanceof Error er) throw er;
                        throw new RuntimeException(cause);
                    }
                }
                throw new IllegalStateException("await: handle inválido");
            }
            case "kof_await_timeout": {
                java.util.concurrent.Future<?> fu = (java.util.concurrent.Future<?>) args[0];
                try {
                    return normalizeReturn(fu.get(KofInterpreter.unboxInt(args[1]),
                            java.util.concurrent.TimeUnit.MILLISECONDS));
                } catch (java.util.concurrent.TimeoutException te) {
                    throw new RuntimeException("awaitTimeout: estourou o tempo limite de "
                            + args[1] + "ms");
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof RuntimeException re) throw re;
                    if (cause instanceof Error er) throw er;
                    throw new RuntimeException(cause);
                }
            }
            case "kof_poll": {
                if (args[0] instanceof CompletableFuture<?> cf) return normalizeReturn(cf.getNow(null));
                if (args[0] instanceof java.util.concurrent.Future<?> f && f.isDone()) {
                    try {
                        return normalizeReturn(f.get());
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
            }
            case "kof_done":
                return args[0] instanceof java.util.concurrent.Future<?> f && f.isDone() ? 1 : 0;
            case "kof_cancel":
                return args[0] instanceof java.util.concurrent.Future<?> f && f.cancel(true) ? 1 : 0;
            case "kof_cancelled":
                return 0;
            case "kof_select_any": {
                @SuppressWarnings("unchecked")
                List<Object> handles = (List<Object>) args[0];
                CompletableFuture<?>[] arr = handles.stream()
                        .map(h -> (CompletableFuture<?>) h)
                        .toArray(CompletableFuture[]::new);
                return normalizeReturn(CompletableFuture.anyOf(arr).get());
            }
            case "kof_time_interval":
            case "kof_scheduler_every": {
                int ms = KofInterpreter.unboxInt(args[0]);
                Thread t = new Thread(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            Thread.sleep(ms);
                            interp.invokeLambda(args[1], new Object[0]);
                        } catch (InterruptedException e) {
                            return;
                        } catch (Throwable e) {
                            return;
                        }
                    }
                }, "kof-interval");
                t.setDaemon(true);
                t.start();
                return "kof-interval-" + t.threadId();
            }
            case "kof_time_cancel":
            case "kof_scheduler_cancel":
                return null;
            default:
                return NOT_HANDLED;
        }
    }

    private void startTask(Object handle, ThrowingRunnable body) {
        Runnable wrapped = () -> {
            try {
                body.run();
            } catch (Throwable e) {
                if (handle instanceof CompletableFuture<?> cf) {
                    cf.completeExceptionally(e);
                } else {
                    interp.err().println("spawn task failed: " + KofInterpreter.kofErrorMessage(e));
                }
            } finally {
                activeTasks.decrementAndGet();
            }
        };
        activeTasks.incrementAndGet();
        Thread t = startVirtualOrPlatform(wrapped);
        tasks.add(t);
        if (handle != null) taskThreads.put(handle, t);
    }

    interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static final java.util.concurrent.atomic.AtomicInteger activeTasks =
            new java.util.concurrent.atomic.AtomicInteger();

    private static Thread startVirtualOrPlatform(Runnable body) {
        try {
            Method m = Thread.class.getMethod("startVirtualThread", Runnable.class);
            return (Thread) m.invoke(null, body);
        } catch (Throwable ignored) {
            Thread t = new Thread(body, "kof-task");
            t.start();
            return t;
        }
    }

    /** Espelha o shutdown hook do runtime gerado: espera tarefas de spawn. */
    void awaitAllTasks() {
        while (activeTasks.get() > 0) {
            Thread.onSpinWait();
        }
    }

    // ── toString/equals/hashCode de objeto Kof (JvmRecordEmitter) ───

    Object kofObjectMethod(String name, Object recv, Object[] args, IRClass owner) {
        if (!(recv instanceof KofInterpreter.KofObj ko)) return NOT_HANDLED;
        switch (name) {
            case "toString":
                return kofToString(ko);
            case "equals": {
                Object other = args[0];
                if (other == ko) return 1;
                if (!(other instanceof KofInterpreter.KofObj ok)) return 0;
                if (!ok.clazz.name().equals(ko.clazz.name())) return 0;
                for (IRField f : ko.clazz.fields()) {
                    Object x = ko.fields.get(f.name());
                    Object y = ok.fields.get(f.name());
                    if (!fieldEquals(f.type(), x, y)) return 0;
                }
                return 1;
            }
            case "hashCode": {
                int h = 1;
                for (IRField f : ko.clazz.fields()) {
                    h = 31 * h + fieldHash(f.type(), ko.fields.get(f.name()));
                }
                return h;
            }
            default:
                return NOT_HANDLED;
        }
    }

    private static boolean fieldEquals(Type t, Object x, Object y) {
        if (t instanceof Type.PrimitiveType pt) {
            return numEq(pt, x, y);
        }
        return Objects.equals(x, y);
    }

    private static boolean numEq(Type pt, Object x, Object y) {
        if (x == null || y == null) return Objects.equals(x, y);
        return switch (Type.canonicalPrimitiveName(pt instanceof Type.PrimitiveType p ? p.name() : "")) {
            case "long" -> ((Number) x).longValue() == ((Number) y).longValue();
            case "float" -> Float.compare(((Number) x).floatValue(), ((Number) y).floatValue()) == 0;
            case "double" -> Double.compare(((Number) x).doubleValue(), ((Number) y).doubleValue()) == 0;
            default -> ((Number) x).intValue() == ((Number) y).intValue();
        };
    }

    private static int fieldHash(Type t, Object v) {
        if (v == null) return 0;
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "long" -> Long.hashCode(((Number) v).longValue());
                case "float" -> Float.floatToIntBits(((Number) v).floatValue());
                case "double" -> Double.hashCode(((Number) v).doubleValue());
                default -> ((Number) v).intValue();
            };
        }
        return Objects.hashCode(v);
    }

    String kofToString(Object v) {
        if (v == null) return "null";
        if (v instanceof KofInterpreter.KofObj ko) {
            if (ko.isRecord()) {
                String simple = simpleOf(ko.internalName());
                StringBuilder sb = new StringBuilder(simple).append('[');
                boolean first = true;
                for (IRField f : ko.clazz.fields()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append(f.name()).append('=').append(appendValue(f.type(), ko.fields.get(f.name())));
                }
                return sb.append(']').toString();
            }
            return simpleOf(ko.internalName()) + "@"
                    + Integer.toHexString(System.identityHashCode(ko));
        }
        if (v instanceof Integer[] arr) return java.util.Arrays.toString(arr);
        return String.valueOf(v);
    }

    private String appendValue(Type t, Object v) {
        if (v == null) return "null";
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "bool" -> ((Number) v).intValue() != 0 ? "true" : "false";
                case "char" -> String.valueOf((char) ((Number) v).intValue());
                default -> String.valueOf(v);
            };
        }
        return kofToString(v);
    }

    // ── externo (JDK) ───────────────────────────────────────────────

    Object newExternal(Type type, Object[] args) throws Throwable {
        if (type instanceof Type.ClassType ct) {
            Class<?> c = classForType(ct);
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
            c = classForType(ct);
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
            return normalizeReturn(best.invoke(recv, coerceArgs(best.getParameterTypes(), args)), kc.returnType());
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
            if (p.isPrimitive() != primitiveIr && ir != null && !isBoxedIr(ir, p)) {
                if (p != Object.class) return -1;
            }
            if (ir != null && primitiveMatchesIr(p, ir)) score += 2;
            if (args[i] != null && p.isInstance(args[i])) score += 1;
        }
        return score;
    }

    private static boolean isBoxedIr(Type ir, Class<?> p) {
        if (!(ir instanceof Type.PrimitiveType pt)) return false;
        String n = Type.canonicalPrimitiveName(pt.name());
        return switch (n) {
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
        String n = Type.canonicalPrimitiveName(pt.name());
        return switch (n) {
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
