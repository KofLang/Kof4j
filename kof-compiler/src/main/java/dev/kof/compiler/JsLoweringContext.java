package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsLoweringContext — estado compartilhado de UM lowering de módulo
 * (REFACTOR-500 FASE 4). Vive apenas durante {@code emit()}; nunca é
 * estático global. Os emissores (JsClassEmitter, JsMethodEmitter, ...)
 * recebem o contexto por parâmetro.
 */
class JsLoweringContext {

    final List<String> runtimeImports = new ArrayList<>();
    final List<String> ioRuntimeImports = new ArrayList<>();
    final Set<String> decodeHelpers = new HashSet<>();
    final Set<String> recordClassNames = new HashSet<>();
    Map<String, Set<String>> classMethodNames = Map.of();
    Map<String, Map<Integer, String>> fnArityNames = Map.of();
    Map<String, Boolean> asyncMethods = Map.of();
    Set<String> asyncMethodNamesAnywhere = Set.of();
    /** ops do método em lowering — usado na mensagem de underflow da pilha */
    List<KofOperation> currentCtxOpsDump = List.of();

    // kof_spawn_result/kof_spawn NÃO entram aqui de propósito: spawnar uma
    // task não bloqueia quem chama, só await/receive/selectAny bloqueiam.
    // kofSpawnResult() é uma função JS comum (não-async) que devolve um
    // handle na hora — não exige que quem a chama seja async. O caso de uma
    // task esquecida (handle nunca esperado) já é coberto independentemente
    // pelo pump de kofActiveTasks em KofJsRunner, não pela coloração.
    static final Set<String> ASYNC_RUNTIME_OPS = Set.of(
            "kof_await", "kof_await_timeout", "kof_channel_receive", "kof_select_any");

    /** JS name for a top-level function call resolved by (name, arity). */
    String jsFunctionName(String name, int arity) {
        Map<Integer, String> byArity = fnArityNames.get(name);
        if (byArity != null) {
            String resolved = byArity.get(arity);
            if (resolved != null) return resolved;
        }
        return name;
    }

    void registerRuntime(String fn) {
        if (!runtimeImports.contains(fn)) runtimeImports.add(fn);
    }

    void registerIoRuntime(String fn) {
        if (!ioRuntimeImports.contains(fn)) ioRuntimeImports.add(fn);
    }

    static String methodNameFromAsyncKey(String key) {
        int hash = key.lastIndexOf('#');
        String rest = hash >= 0 ? key.substring(hash + 1) : key;
        int slash = rest.lastIndexOf('/');
        return slash >= 0 ? rest.substring(0, slash) : rest;
    }

    static String asyncMethodKey(IRClass clazz, IRMethod method) {
        int arity = method.parameterTypes().size();
        if (isMainClass(clazz)) return "#" + method.name() + "/" + arity;
        return clazz.name() + "#" + method.name() + "/" + arity;
    }

    static String calleeKeyFromCall(KofCall kc) {
        int arity = kc.parameterTypes().size();
        String owner = JsTypeMapper.ownerInternalName(kc.ownerType());
        if (owner.isEmpty() || isMainInternalName(owner)) return "#" + kc.methodName() + "/" + arity;
        return owner + "#" + kc.methodName() + "/" + arity;
    }

    static boolean isMainInternalName(String internalName) {
        return "Main".equals(internalName) || internalName.endsWith("/Main");
    }

    static boolean skipClass(IRClass clazz) {
        if (clazz.name() == null || clazz.name().isBlank()) return true;
        if ("java/lang/Object".equals(clazz.name()) || "java/lang/Record".equals(clazz.name())) return true;
        // Interfaces are type-level only in Kof; JavaScript has no runtime
        // interface. Calls through interfaces lower to structural method
        // calls (receiver.method(...)), so no JS entity is required.
        return (clazz.accessFlags() & AccessFlags.INTERFACE) != 0;
    }

    static boolean isMainClass(IRClass clazz) {
        return "Main".equals(clazz.name()) || clazz.name().endsWith("/Main");
    }
}
