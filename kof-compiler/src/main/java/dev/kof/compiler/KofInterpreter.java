package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interpretador da IR Kof (KofScript = target de execução direta).
 *
 * Executa a MESMA IR que o backend JVM emite (mesmo parse, análise,
 * lowering e otimização via {@link CompilerPipeline#prepareForInterpretation})
 * — paridade por construção, não por reimplementação. A pilha carrega
 * valores reais do JDK (String, Integer, ArrayList, HashMap...); objetos
 * de classes Kof são {@link KofObj}. Builtins sem lambda Kof são
 * despachados por reflexão ao KofRuntime gerado (mesmo source do caminho
 * compilado); builtins de coleção/string/channel espelham o emitter JVM.
 *
 * Representação de primitivos na pilha (igual ao bytecode JVM):
 * int/char/bool/byte/short → Integer; long → Long; float → Float;
 * double → Double; void → nada.
 *
 * Colaboradores: {@link KofInterpreterBuiltins} (fachada de builtins),
 * {@link KofInterpreterMembers} (classes/fields/statics) e
 * {@link KofInterpreterFrame} (frames + exception table).
 */
public final class KofInterpreter {

    /** Resultado de uma execução interpretada. */
    public record Result(int exitCode, String stdout, String stderr) {}

    /** Objeto de classe Kof na pilha do interpretador. */
    static final class KofObj {
        final IRClass clazz;
        final Map<String, Object> fields = new LinkedHashMap<>();
        KofObj(IRClass clazz) { this.clazz = clazz; }
        String internalName() { return clazz.name(); }
        boolean isRecord() { return "java/lang/Record".equals(clazz.superName()); }
    }

    /** NEW de classe externa (ex.: RuntimeException do throw): construído no <init>. */
    static final class PendingNew {
        final Type type;
        PendingNew(Type type) { this.type = type; }
    }

    private final IRModule module;
    private final PrintStream out;
    private final PrintStream err;
    private Class<?> runtimeClass;
    final KofInterpreterBuiltins builtins;
    private final KofInterpreterMembers members;
    private final KofInterpreterFrame frames;
    private Object lastReturned;

    KofInterpreter(IRModule module, PrintStream out, PrintStream err) {
        this.module = module;
        this.out = out;
        this.err = err;
        this.members = new KofInterpreterMembers(this, module.classes());
        this.frames = new KofInterpreterFrame(this);
        this.builtins = new KofInterpreterBuiltins(this);
    }

    /**
     * Interpreta um módulo Kof (IR vinda do frontend completo). Sem
     * emissão de bytecode e sem fork de JVM — é o target KofScript.
     */
    public static Result run(IRModule module, String[] args) {
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        PrintStream po = new PrintStream(so, true);
        PrintStream pe = new PrintStream(se, true);
        KofInterpreter interp = new KofInterpreter(module, po, pe);
        int code = 0;
        try {
            interp.ensureRuntimeForModule();
            interp.execute(args == null ? new String[0] : args);
        } catch (Throwable t) {
            code = 1;
            pe.println(kofErrorMessage(t));
            if (System.getProperty("kof.interp.trace") != null) t.printStackTrace(pe);
        } finally {
            po.flush();
            pe.flush();
        }
        return new Result(code, so.toString(), se.toString());
    }

    /**
     * Executa sem capturar saída (stdout/stderr reais) — REPL do KofScript
     * precisa de escrita imediata, linha a linha.
     */
    public static Result runInherit(IRModule module, String[] args) {
        KofInterpreter interp = new KofInterpreter(module, System.out, System.err);
        int code = 0;
        try {
            interp.ensureRuntimeForModule();
            interp.execute(args == null ? new String[0] : args);
        } catch (Throwable t) {
            code = 1;
            System.err.println(kofErrorMessage(t));
        }
        return new Result(code, null, null);
    }

    static String kofErrorMessage(Throwable t) {
        if (t instanceof java.util.concurrent.ExecutionException e && e.getCause() != null) {
            return kofErrorMessage(e.getCause());
        }
        if (t.getMessage() != null) return t.getMessage();
        return t.getClass().getName();
    }

    private void ensureRuntimeForModule() throws Exception {
        Path workDir = Path.of(System.getProperty("java.io.tmpdir"),
                "kof-interp-" + System.nanoTime());
        builtins.prepareRuntime(workDir, usesVk());
    }

    private boolean usesVk() {
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock bb : m.basicBlocks()) {
                    for (KofOperation op : bb.operations()) {
                        if (op instanceof KofCall kc && (kc.methodName().startsWith("kof_vk_")
                                || kc.methodName().startsWith("kof_mv64_"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void execute(String[] args) throws Throwable {
        IRClass main = null;
        IRMethod mainMethod = null;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("main".equals(m.name()) && (m.accessFlags() & 8) != 0
                        && m.parameterTypes().size() == 1
                        && m.parameterTypes().get(0) instanceof Type.ArrayType) {
                    main = c;
                    mainMethod = m;
                }
            }
        }
        if (main == null) throw new IllegalStateException("no main() in module");
        invokeKof(main, mainMethod, new Object[]{args}, false);
        builtins.awaitAllTasks();
    }

    /**
     * Executa um método Kof. `hasThis` decide o mapeamento de locais:
     * INSTANCE/CONSTRUCTOR/SUPER deslocam params em 1 (this no índice 0);
     * STATIC/FUNCTION começam no 0 — igual ao bytecode JVM.
     */
    void invokeKof(IRClass clazz, IRMethod m, Object[] args, boolean hasThis) throws Throwable {
        List<KofOperation> ops = new ArrayList<>();
        for (IRBasicBlock bb : m.basicBlocks()) ops.addAll(bb.operations());
        KofInterpreterFrame.Frame f = frames.newFrame(ops);
        int size = args.length + (hasThis ? 1 : 0);
        for (KofOperation op : ops) {
            if (op instanceof KofLoadLocal ll && ll.index() + 1 > size) size = ll.index() + 1;
            if (op instanceof KofStoreLocal sl && sl.index() + 1 > size) size = sl.index() + 1;
            if (op instanceof KofCatchStart cs && cs.localIndex() + 1 > size) size = cs.localIndex() + 1;
        }
        f.locals = new Object[size];
        System.arraycopy(args, 0, f.locals, 0, args.length);
        runFrame(f);
    }

    Object evalKof(IRClass clazz, IRMethod m, Object[] args, boolean hasThis) throws Throwable {
        invokeKof(clazz, m, args, hasThis);
        return lastReturned;
    }

    private void runFrame(KofInterpreterFrame.Frame f) throws Throwable {
        Deque<Object> st = f.stack;
        while (f.pc < f.ops.size()) {
            KofOperation op = f.ops.get(f.pc++);
            try {
                if (op instanceof KofLoadLiteral kl) {
                    st.push(kl.value());
                } else if (op instanceof KofLoadLocal ll) {
                    st.push(f.locals[ll.index()]);
                } else if (op instanceof KofStoreLocal sl) {
                    f.locals[sl.index()] = st.pop();
                } else if (op instanceof KofLoadField lf) {
                    st.push(members.loadField(lf, st.pop()));
                } else if (op instanceof KofStoreField sf) {
                    Object v = st.pop();
                    Object recv = st.pop();
                    members.storeField(sf, recv, v);
                } else if (op instanceof KofGetStatic gs) {
                    st.push(members.getStatic(gs));
                } else if (op instanceof KofPutStatic ps) {
                    members.putStatic(ps, st.pop());
                } else if (op instanceof KofBinary kb) {
                    Object b = st.pop();
                    Object a = st.pop();
                    st.push(builtins.binary(kb, a, b));
                } else if (op instanceof KofUnary ku) {
                    st.push(builtins.unary(ku, st.pop()));
                } else if (op instanceof KofLabel) {
                    // no-op
                } else if (op instanceof KofJump kj) {
                    f.pc = f.labels.get(kj.target());
                } else if (op instanceof KofConditionalJump cj) {
                    Object b = st.pop();
                    Object a = st.pop();
                    boolean taken = builtins.compare(cj.comparison(), cj.operandType(), a, b);
                    f.pc = f.labels.get(taken ? cj.trueLabel() : cj.falseLabel());
                } else if (op instanceof KofCall kc) {
                    call(f, kc);
                } else if (op instanceof KofNewObject no) {
                    IRClass k = members.kofClassOrNull(no.type());
                    st.push(k != null ? new KofObj(k) : new PendingNew(no.type()));
                } else if (op instanceof KofReturn kr) {
                    lastReturned = Type.isVoid(kr.returnType()) ? null : st.pop();
                    return;
                } else if (op instanceof KofReturnVoid) {
                    lastReturned = null;
                    return;
                } else if (op instanceof KofDup) {
                    st.push(st.peek());
                } else if (op instanceof KofDupX1) {
                    Object top = st.pop();
                    Object below = st.pop();
                    st.push(top);
                    st.push(below);
                    st.push(top);
                } else if (op instanceof KofDupX2) {
                    Object top = st.pop();
                    Object b = st.pop();
                    Object a = st.pop();
                    st.push(top);
                    st.push(a);
                    st.push(b);
                    st.push(top);
                } else if (op instanceof KofPop) {
                    st.pop();
                } else if (op instanceof KofCheckCast) {
                    // cast sem efeito observável na pilha do interpretador
                } else if (op instanceof KofInstanceOf ki) {
                    st.push(builtins.instanceOf(ki.type(), st.pop()) ? 1 : 0);
                } else if (op instanceof KofArrayLoad al) {
                    int idx = unboxInt(st.pop());
                    Object arr = st.pop();
                    st.push(Array.get(arr, idx));
                    if (al.elementType() != null) { /* unbox é no-op na representação */ }
                } else if (op instanceof KofArrayStore as) {
                    Object v = st.pop();
                    int idx = unboxInt(st.pop());
                    Object arr = st.pop();
                    Array.set(arr, idx, builtins.coerceFor(as.elementType(), v));
                } else if (op instanceof KofNewArray na) {
                    st.push(builtins.newArray(na.elementType(), unboxInt(st.pop())));
                } else if (op instanceof KofArrayLength) {
                    st.push(Array.getLength(st.pop()));
                } else if (op instanceof KofThrow) {
                    throw KofInterpreterFrame.asThrowable(st.pop());
                } else if (op instanceof KofTryStart ts) {
                    f.tryStack.push(frames.newTryRegion(f, ts, st.size()));
                } else if (op instanceof KofTryEnd) {
                    if (!f.tryStack.isEmpty()) f.tryStack.pop();
                } else if (op instanceof KofCatchStart cs) {
                    f.locals[cs.localIndex()] = st.pop();
                }
            } catch (Throwable t) {
                if (!frames.handleException(f, t)) throw t;
            }
        }
    }

    private void call(KofInterpreterFrame.Frame f, KofCall kc) throws Throwable {
        int n = kc.parameterTypes().size();
        Object[] args = new Object[n];
        for (int i = n - 1; i >= 0; i--) args[i] = f.stack.pop();
        Object recv = null;
        boolean popsRecv = kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE
                || kc.kind() == KofCallKind.SUPER || kc.kind() == KofCallKind.CONSTRUCTOR;
        if (popsRecv) recv = f.stack.pop();
        Object result = dispatch(kc, recv, args);
        // NEW externo (RuntimeException do throw): o <init> constrói o objeto
        // real — substitui o PendingNew em todos os slots (KofDup duplicou).
        if (kc.kind() == KofCallKind.CONSTRUCTOR && recv instanceof PendingNew pn
                && result != null) {
            replacePending(f.stack, pn, result);
            return;
        }
        if (!Type.isVoid(kc.returnType())) f.stack.push(result);
    }

    private static void replacePending(Deque<Object> stack, PendingNew pn, Object real) {
        Object[] arr = stack.toArray();
        stack.clear();
        for (Object v : arr) stack.push(v == pn ? real : v);
    }

    Object dispatch(KofCall kc, Object recv, Object[] args) throws Throwable {
        String name = kc.methodName();
        // box/unbox: identidade — a pilha do interpretador já é boxed (JVM)
        if ("kof_box".equals(name) || "kof_unbox".equals(name)) {
            return args.length > 0 ? args[0] : recv;
        }
        // receiver Kof → dispatch VIRTUAL pela classe real (polimorfismo)
        IRClass owner = recv instanceof KofObj ko ? members.classByInternal(ko.internalName())
                : members.kofClassOrNull(kc.ownerType());
        // métodos sintéticos de objeto Kof (record equals/hashCode/toString)
        if (recv instanceof KofObj && owner != null && findKofMethod(owner, name, args.length) == null) {
            Object synth = builtins.kofObjectMethod(name, recv, args, owner);
            if (synth != KofInterpreterBuiltins.NOT_HANDLED) return synth;
        }
        // lambdas/concorrência/coleta com lambda: nativos do interpretador
        Object ho = builtins.lambdaAware(kc, recv, args);
        if (ho != KofInterpreterBuiltins.NOT_HANDLED) return ho;
        // runtime gerado (json/io/http/db/... sobre valores JDK)
        if (JvmRuntime.hasRuntimeFn(name)) {
            Object[] ra = (kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE
                    || kc.kind() == KofCallKind.SUPER)
                    ? prepend(recv, args) : args;
            return builtins.runtimeFn(name, ra, kc.returnType());
        }
        // string/list/map/set/channel nativos (mesma semântica do emitter)
        Object coll = builtins.collections(kc, recv, args);
        if (coll != KofInterpreterBuiltins.NOT_HANDLED) return coll;
        // String.valueOf(objeto Kof) → toString de Kof
        if ("valueOf".equals(name) && args.length == 1 && args[0] instanceof KofObj
                && kc.ownerType() instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                && "String".equals(ct.name())) {
            return builtins.kofToString(args[0]);
        }
        // classe Kof: interpretar método
        if (owner != null) {
            IRMethod m = findKofMethod(owner, name, args.length);
            if (m == null && "<init>".equals(name)) return null; // construtor padrão
            if (m == null) throw new NoSuchMethodError(owner.name() + "." + name);
            members.ensureInit(owner);
            boolean hasThis = kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE
                    || kc.kind() == KofCallKind.SUPER || kc.kind() == KofCallKind.CONSTRUCTOR;
            Object[] full = hasThis ? prepend(recv, args) : args;
            if ("<init>".equals(name)) {
                invokeKof(owner, m, full, true);
                return null;
            }
            return evalKof(owner, m, full, hasThis);
        }
        // NEW externo pendente (RuntimeException do throw): constrói agora
        if ("<init>".equals(name) && recv instanceof PendingNew pn) {
            return builtins.newExternal(pn.type, args);
        }
        // externo (JDK): reflexão com coerção de primitivos
        return builtins.invokeExternal(kc, recv, args);
    }

    static Object[] prepend(Object recv, Object[] args) {
        Object[] full = new Object[args.length + 1];
        full[0] = recv;
        System.arraycopy(args, 0, full, 1, args.length);
        return full;
    }

    IRMethod findKofMethod(IRClass c, String name, int argc) {
        for (IRMethod m : c.methods()) {
            if (m.name().equals(name) && m.parameterTypes().size() == argc) return m;
        }
        IRClass sup = c.superName() == null ? null : members.classByInternal(c.superName());
        return sup == null ? null : findKofMethod(sup, name, argc);
    }

    IRClass kofClassOf(Type t) { return members.kofClassOf(t); }
    IRClass kofClassOrNull(Type t) { return members.kofClassOrNull(t); }
    IRClass classByInternal(String internal) { return members.classByInternal(internal); }

    static Object defaultValue(Type t) { return KofInterpreterMembers.defaultValue(t); }

    static int unboxInt(Object v) {
        if (v instanceof Number num) return num.intValue();
        if (v instanceof Character ch) return ch;
        throw new ClassCastException("not an int: " + v);
    }

    Class<?> runtimeClass() { return runtimeClass; }
    void setRuntimeClass(Class<?> c) { this.runtimeClass = c; }
    PrintStream out() { return out; }
    PrintStream err() { return err; }
    IRModule module() { return module; }

    void loadRuntimeClass(Path workDir) throws Exception {
        URLClassLoader cl = new URLClassLoader(
                new java.net.URL[]{workDir.toUri().toURL()},
                KofInterpreter.class.getClassLoader());
        setRuntimeClass(Class.forName("dev.kof.runtime.KofRuntime", true, cl));
    }

    Map<String, Object> kofStatics(String internal) { return members.kofStatics(internal); }

    /** Invoca método de lambda/Runnable/Callable Kof com args reais. */
    Object invokeLambda(Object lambda, Object[] args) throws Throwable {
        if (lambda instanceof KofObj ko) {
            IRClass c = members.classByInternal(ko.internalName());
            IRMethod m = findKofMethod(c, "invoke", args.length);
            if (m == null) m = findKofMethod(c, "run", args.length);
            if (m == null) m = findKofMethod(c, "call", args.length);
            if (m == null) throw new NoSuchMethodError(ko.internalName() + ".invoke/" + args.length);
            return evalKof(c, m, prepend(ko, args), true);
        }
        for (Method mm : lambda.getClass().getMethods()) {
            if (mm.getParameterCount() == args.length
                    && (mm.getName().equals("invoke") || mm.getName().equals("run"))) {
                return mm.invoke(lambda, args);
            }
        }
        throw new NoSuchMethodError("lambda invoke(" + args.length + " args)");
    }

    /** toString canônico de valor Kof (records: Name[f=v, ...]). */
    String valueToString(Object v) { return builtins.kofToString(v); }
}
