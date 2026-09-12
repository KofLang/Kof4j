package dev.kof.compiler;
import dev.kof.compiler.jvm.JvmOpCollections;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de classes Kof e acesso a campos/estáticos do interpretador:
 * resolve IRClass por Type e implementa LOAD/STORE FIELD e GET/PUT STATIC
 * com a mesma semântica do bytecode (System.out/err redirecionados,
 * {@code <clinit>} lazy, initialValue de campos estáticos).
 */
public final class KofInterpreterMembers {

    private final KofInterpreter interp;
    private final Map<String, IRClass> kofClasses = new HashMap<>();
    // staticFields/initialized são tocados por virtual threads do `spawn`
    // (ensureInit/kofStatics no dispatch): HashMap comum → CME no
    // computeIfAbsent/putIfAbsent concorrente (bug 55). ConcurrentHashMap
    // torna a criação do mapa de statics e a guarda de init atômicas.
    // SG-020 (modelo de memória): os VALORES também — mapa interno
    // concorrente dá HB (read-after-write) por campo de static, honrando a
    // borda 5 do modelo (statics sequentialmente consistentes) no
    // interpretador; o backend JVM compilado usa vars estáticas JVM (JMM).
    private final Map<String, Map<String, Object>> staticFields = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Boolean> initialized = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Object> initLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Boolean> claimed = new java.util.concurrent.ConcurrentHashMap<>();

    KofInterpreterMembers(KofInterpreter interp, List<IRClass> classes) {
        this.interp = interp;
        for (IRClass c : classes) kofClasses.put(c.name(), c);
    }

    void ensureInit(IRClass c) throws Throwable {
        if (initialized.containsKey(c.name())) return;
        // Lock por classe: dois threads que tocam a mesma classe (spawn)
        // esperam o <clinit> terminar (semântica JVM: exatamente uma vez,
        // os outros bloqueiam até done). `claimed` só detecta reentrância
        // do MESMO thread (clinit que chama método estático da própria
        // classe) — putIfAbsent sozinho deixava o perdedor seguir com
        // statics vazios (bug 55). A cadeia de super é uma árvore → ordem
        // de lock sub→super é consistente → sem deadlock.
        Object lock = initLocks.computeIfAbsent(c.name(), k -> new Object());
        synchronized (lock) {
            if (initialized.containsKey(c.name())) return;
            if (claimed.containsKey(c.name())) return;
            claimed.put(c.name(), true);
            if (c.superName() != null) {
                IRClass sup = kofClasses.get(c.superName());
                if (sup != null) ensureInit(sup);
            }
            // campos estáticos com valor inicial (constante do campo no bytecode
            // JVM): semear o mapa de statics — o interpretador não tem o
            // "ConstantValue" do class file, então aplica aqui.
            Map<String, Object> st = kofStatics(c.name());
            for (IRField fl : c.fields()) {
                if ((fl.accessFlags() & 8) != 0 && fl.initialValue() != null) {
                    st.put(fl.name(), fl.initialValue());
                }
            }
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) {
                    interp.invokeKof(c, m, new Object[0], false);
                }
            }
            initialized.put(c.name(), true);
        }
    }

    IRClass kofClassOf(Type t) {
        IRClass c = kofClassOrNull(t);
        if (c == null) throw new IllegalStateException("unknown class: " + t);
        return c;
    }

    IRClass kofClassOrNull(Type t) {
        if (!(t instanceof Type.ClassType ct)) return null;
        return kofClasses.get(ct.internalName());
    }

    IRClass classByInternal(String internal) {
        return internal == null ? null : kofClasses.get(internal);
    }

    // SG-011B: com a assinatura do KofCall (mesma fonte do descritor JVM) o
    // dispatch é EXATO — twice(Int) vs twice(String) (mesma aridade) deixam de
    // colidir. Sem correspondência exata (ou sem sig), cai no nome+aridade de
    // sempre (zero regressão p/ classes e p/ calls de um único candidato).
    IRMethod findKofMethod(IRClass c, String name, int argc, List<Type> sig) {
        if (sig != null && sig.size() == argc) {
            String want = TopLevelOverload.sigTag(sig);
            for (IRMethod m : c.methods()) {
                if (m.name().equals(name) && m.parameterTypes().size() == argc
                        && TopLevelOverload.sigTag(m.parameterTypes()).equals(want)) return m;
            }
        }
        for (IRMethod m : c.methods()) {
            if (m.name().equals(name) && m.parameterTypes().size() == argc) return m;
        }
        IRClass sup = c.superName() == null ? null : classByInternal(c.superName());
        return sup == null ? null : findKofMethod(sup, name, argc, sig);
    }

    Object loadField(KofLoadField lf, Object recv) {
        if (BuiltinTypes.isString(lf.ownerType()) && "length".equals(lf.name())) {
            return ((String) recv).length();
        }
        if (recv instanceof KofInterpreter.KofObj ko) {
            Object v = ko.fields.get(lf.name());
            return v != null ? v : defaultValue(lf.fieldType());
        }
        try {
            Field fl = findField(recv.getClass(), lf.name());
            if (fl != null) {
                fl.setAccessible(true);
                return fl.get(recv);
            }
        } catch (IllegalAccessException e) {
            throw new NoSuchFieldError(lf.ownerType() + "." + lf.name());
        }
        throw new NoSuchFieldError(lf.ownerType() + "." + lf.name());
    }

    private Field findField(Class<?> c, String name) {
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    void storeField(KofStoreField sf, Object recv, Object v) {
        if (recv instanceof KofInterpreter.KofObj ko) {
            ko.fields.put(sf.name(), v);
            return;
        }
        try {
            Field fl = findField(recv.getClass(), sf.name());
            if (fl != null) {
                fl.setAccessible(true);
                fl.set(recv, v);
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        throw new NoSuchFieldError(sf.ownerType() + "." + sf.name());
    }

    Object getStatic(KofGetStatic gs) throws Throwable {
        if (gs.ownerType() instanceof Type.ClassType ct) {
            if ("java.lang".equals(ct.packageName()) && "System".equals(ct.name())) {
                if ("out".equals(gs.name())) return interp.out();
                if ("err".equals(gs.name())) return interp.err();
                if ("in".equals(gs.name())) return System.in;
            }
            IRClass c = kofClasses.get(ct.internalName());
            if (c != null) {
                ensureInit(c);
                return kofStatics(c.name()).getOrDefault(gs.name(), defaultValue(gs.fieldType()));
            }
            Class<?> ext = interp.builtins.classForType(ct);
            Field fl = ext.getDeclaredField(gs.name());
            fl.setAccessible(true);
            return fl.get(null);
        }
        throw new NoSuchFieldError("static " + gs.ownerType() + "." + gs.name());
    }

    void putStatic(KofPutStatic ps, Object v) {
        if (ps.ownerType() instanceof Type.ClassType ct) {
            IRClass c = kofClasses.get(ct.internalName());
            if (c != null) {
                kofStatics(c.name()).put(ps.name(), v);
                return;
            }
        }
        throw new NoSuchFieldError("static " + ps.ownerType() + "." + ps.name());
    }

    static Object defaultValue(Type t) {
        if (JvmOpCollections.isPrimitiveType(t)) {
            String n = Type.canonicalPrimitiveName(
                    t instanceof Type.PrimitiveType pt ? pt.name() : "");
            return switch (n) {
                case "long" -> 0L;
                case "float" -> 0f;
                case "double" -> 0d;
                default -> 0;
            };
        }
        return null;
    }

    Map<String, Object> kofStatics(String internal) {
        // SG-020: mapa de valores concorrente — HB por campo de static
        return staticFields.computeIfAbsent(internal,
                k -> new java.util.concurrent.ConcurrentHashMap<>());
    }
}
