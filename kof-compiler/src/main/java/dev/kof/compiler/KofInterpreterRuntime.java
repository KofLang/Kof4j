package dev.kof.compiler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Ponte do interpretador com o {@code KofRuntime} GERADO (mesma fonte do
 * caminho compilado — {@code JvmRuntime.ensureCompiled}) e com APIs externas
 * do JDK: despacho reflexivo com seleção de overload pelo tipo da IR,
 * coerção de argumentos e proxy para objetos Kof.
 */
final class KofInterpreterRuntime {

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
        // json.encode sobre objeto Kof: campos do mapa (mesmo formato do
        // runtime gerado, que lê declared fields de instâncias reais)
        if (name.equals("kof_json_encode") && args.length == 1
                && args[0] instanceof KofInterpreter.KofObj ko) {
            return encodeKof(ko);
        }
        Class<?> rt = interp.runtimeClass();
        for (Method m : rt.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
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
