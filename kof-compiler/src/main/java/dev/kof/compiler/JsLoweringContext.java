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
}
