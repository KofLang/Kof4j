package dev.kof.compiler;

import java.nio.file.Path;

/**
 * Fachada dos builtins do {@link KofInterpreter}: mantém o ponto de entrada
 * único usado pelo interpretador e delega para colaboradores coesos —
 * {@link KofInterpreterOps} (aritmética/comparação/arrays),
 * {@link KofInterpreterCollections} (List/Map/Set/String/Channel),
 * {@link KofInterpreterConcurrency} (spawn/await/lambdas/time),
 * {@link KofInterpreterRuntime} (KofRuntime gerado + externo) e
 * {@link KofInterpreterObjects} (toString/equals/hashCode de record).
 */
final class KofInterpreterBuiltins {

    static final Object NOT_HANDLED = KofInterpreterValues.NOT_HANDLED;

    private final KofInterpreter interp;
    private final KofInterpreterOps ops;
    private final KofInterpreterCollections collections;
    private final KofInterpreterConcurrency concurrency;
    private final KofInterpreterRuntime runtime;
    private final KofInterpreterObjects objects;

    KofInterpreterBuiltins(KofInterpreter interp) {
        this.interp = interp;
        this.ops = new KofInterpreterOps(interp);
        this.collections = new KofInterpreterCollections();
        this.concurrency = new KofInterpreterConcurrency(interp);
        this.runtime = new KofInterpreterRuntime(interp);
        this.objects = new KofInterpreterObjects();
    }

    // ── runtime gerado ──────────────────────────────────────────────

    void prepareRuntime(Path workDir, boolean usesVk) throws Exception {
        runtime.prepareRuntime(workDir, usesVk);
    }

    Object runtimeFn(String name, Object[] args) throws Throwable {
        return runtime.runtimeFn(name, args);
    }

    Object runtimeFn(String name, Object[] args, Type ret) throws Throwable {
        return runtime.runtimeFn(name, args, ret);
    }

    Object[] coerceArgs(Class<?>[] params, Object[] args) {
        return runtime.coerceArgs(params, args);
    }

    Object coerceFor(Class<?> param, Object v) {
        return runtime.coerceFor(param, v);
    }

    Object coerceFor(Type type, Object v) {
        return KofInterpreterValues.coerceFor(type, v);
    }

    // ── aritmética / comparação / arrays ────────────────────────────

    Object binary(KofBinary kb, Object a, Object b) {
        return ops.binary(kb, a, b);
    }

    boolean compare(KofComparison cmp, Type t, Object a, Object b) {
        return ops.compare(cmp, t, a, b);
    }

    Object unary(KofUnary ku, Object v) {
        return ops.unary(ku, v);
    }

    boolean instanceOf(Type type, Object v) {
        return ops.instanceOf(type, v);
    }

    Class<?> classForType(Type.ClassType ct) throws ClassNotFoundException {
        return KofInterpreterValues.classForType(ct);
    }

    Object newArray(Type elementType, int size) {
        return ops.newArray(elementType, size);
    }

    Object arrayLoad(KofArrayLoad al, Object arr, int idx) {
        return ops.arrayLoad(al, arr, idx);
    }

    void arrayStore(KofArrayStore as, Object arr, int idx, Object v) {
        ops.arrayStore(as, arr, idx, v);
    }

    // ── coleções nativas ────────────────────────────────────────────

    Object collections(KofCall kc, Object recv, Object[] args) throws Throwable {
        return collections.collections(kc, recv, args);
    }

    // ── lambdas / concorrência ──────────────────────────────────────

    Object lambdaAware(KofCall kc, Object recv, Object[] args) throws Throwable {
        return concurrency.lambdaAware(kc, recv, args);
    }

    void awaitAllTasks() {
        concurrency.awaitAllTasks();
    }

    // ── toString/equals/hashCode de objeto Kof ──────────────────────

    Object kofObjectMethod(String name, Object recv, Object[] args, IRClass owner) {
        return objects.kofObjectMethod(name, recv, args, owner);
    }

    String kofToString(Object v) {
        return objects.kofToString(v);
    }

    // ── externo (JDK) ───────────────────────────────────────────────

    Object newExternal(Type type, Object[] args) throws Throwable {
        return runtime.newExternal(type, args);
    }

    Object invokeExternal(KofCall kc, Object recv, Object[] args) throws Throwable {
        return runtime.invokeExternal(kc, recv, args);
    }
}
