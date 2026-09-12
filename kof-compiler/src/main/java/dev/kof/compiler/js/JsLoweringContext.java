package dev.kof.compiler.js;
import dev.kof.compiler.AccessFlags;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.TopLevelOverload;

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
public class JsLoweringContext {

    final List<String> runtimeImports = new ArrayList<>();
    final List<String> ioRuntimeImports = new ArrayList<>();
    final Set<String> decodeHelpers = new HashSet<>();
    final Set<String> recordClassNames = new HashSet<>();
    Map<String, Set<String>> classMethodNames = Map.of();
    Map<String, Map<Integer, String>> fnArityNames = Map.of();
    /** SG-011B: nome JS quando há ≥2 assinaturas sob o mesmo nome (chave = sigTag). */
    Map<String, Map<String, String>> fnSigNames = Map.of();
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

    /** SG-011B: com assinatura (os tipos do KofCall — a mesma fonte do
     *  descritor JVM) resolve o nome exato quando o nome está sobrecarregado;
     *  senão cai no caminho antigo por (nome, aridade). */
    String jsFunctionName(String name, List<dev.kof.compiler.Type> sig, int arity) {
        Map<String, String> bySig = fnSigNames.get(name);
        if (bySig != null) {
            String resolved = bySig.get(dev.kof.compiler.TopLevelOverload.sigTag(sig));
            if (resolved != null) return resolved;
        }
        return jsFunctionName(name, arity);
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
        // SG-011B: top-level leva a assinatura na chave — duas sobrecargas de
        // mesma aridade (twice(String)/twice(Int)) não podem herdar a cor
        // async uma da outra (await em função não-async = Promise vazando p/
        // valor = divergência silenciosa).
        if (isMainClass(clazz)) return "#" + method.name() + "/" + arity
                + TopLevelOverload.sigTag(method.parameterTypes());
        return clazz.name() + "#" + method.name() + "/" + arity;
    }

    static String calleeKeyFromCall(KofCall kc) {
        int arity = kc.parameterTypes().size();
        String owner = JsTypeMapper.ownerInternalName(kc.ownerType());
        if (owner.isEmpty() || isMainInternalName(owner)) {
            return "#" + kc.methodName() + "/" + arity
                    + TopLevelOverload.sigTag(kc.parameterTypes());
        }
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
