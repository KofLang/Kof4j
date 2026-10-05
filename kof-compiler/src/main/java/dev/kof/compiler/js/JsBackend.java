package dev.kof.compiler.js;
import dev.kof.compiler.backend.Backend;
import dev.kof.compiler.AccessFlags;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.TopLevelOverload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsBackend — KofJS backend.
 *
 * Consumes the same Kof IR as the JVM and Native backends and lowers it to a
 * JavaScript AST (JsIr), which the JsEmitter renders as a modern ECMAScript
 * module (ES2022+, ESM) for Node.js.
 *
 * The Kof IR is a stack-based linear instruction list; this backend converts
 * the stack discipline into the tree-shaped JsIr. Control-flow patterns
 * emitted by the frontend (if/while/for/do-while/for-in/switch/try) are
 * recognized structurally and re-created as native JavaScript control flow.
 *
 * Runtime semantics (List, String helpers, JSON, IO, print) are provided by
 * the small KofJS runtime modules (kof-runtime.mjs, kof-runtime-node.mjs)
 * written next to the generated program. This backend never emits
 * console.* / process.* calls directly into user code.
 */
public class JsBackend implements Backend {

    private final JsLoweringContext lc = new JsLoweringContext();
    private final JsMethodParser parser = new JsMethodParser(lc);
    private final JsClassEmitter classEmitter = new JsClassEmitter(parser);

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        emit(module, outputDir, true);
    }

    @Override
    public void emit(IRModule module, Path outputDir, boolean debugInfo) throws IOException {
        Files.createDirectories(outputDir);
        lc.runtimeImports.clear();
        lc.ioRuntimeImports.clear();
        JsIr.JsModule jsModule = lowerModule(module);
        JsEmitter emitter = new JsEmitter();
        String code = emitter.emit(jsModule);
        String fileName = JsArtifactWriter.moduleFileName(module.name());
        JsArtifactWriter artifacts = new JsArtifactWriter();
        artifacts.writeModule(outputDir, fileName, code, debugInfo);
        artifacts.writeRuntime(outputDir, jsModule.runtimeImports(), jsModule.ioRuntimeImports());
        artifacts.writeHtmlEntry(outputDir, module.name());
        if (debugInfo) {
            artifacts.writeSourceMap(module, outputDir, fileName, emitter.functionLines());
        }
    }

    // ── Module lowering ─────────────────────────────────────────────

    private JsIr.JsModule lowerModule(IRModule module) {
        List<IRClass> effClasses = injectInterfaceDefaults(module.classes());
        List<JsIr.JsClass> classes = new ArrayList<>();
        List<JsIr.JsFunction> functions = new ArrayList<>();
        Map<String, Set<String>> methodNames = new HashMap<>();
        for (IRClass clazz : effClasses) {
            if (!JsLoweringContext.skipClass(clazz)) {
                methodNames.put(clazz.name(), new HashSet<>());
                for (IRMethod m : clazz.methods()) {
                    methodNames.get(clazz.name()).add(m.name());
                }
                if ("java/lang/Record".equals(clazz.superName())) {
                    // record gera equals() no JS (bug 11) — registra para o
                    // dispatch de .equals()/== não cair em referência (===)
                    methodNames.get(clazz.name()).add("equals");
                    methodNames.get(clazz.name()).add("hashCode");
                }
            }
        }
        this.lc.classMethodNames = methodNames;
        lc.recordClassNames.clear();
        for (IRClass clazz : effClasses) {
            if ("java/lang/Record".equals(clazz.superName())) {
                lc.recordClassNames.add(clazz.name());
                lc.recordClassNames.add(clazz.name().replace('/', '.'));
                // also add simple name
                String simple = clazz.name().substring(clazz.name().lastIndexOf('/') + 1);
                lc.recordClassNames.add(simple);
            }
        }
        // Default-parameter wrappers share the canonical name; JS has no
        // overloading, so wrappers are mangled by dropped-arity and calls
        // are routed by (name, arity).
        this.lc.fnArityNames = new HashMap<>();
        this.lc.fnSigNames = new HashMap<>();
        for (IRClass clazz : effClasses) {
            if (JsLoweringContext.skipClass(clazz)) continue;
            Map<String, Integer> maxArity = new HashMap<>();
            Map<String, Set<String>> sigsByName = new HashMap<>();
            for (IRMethod method : clazz.methods()) {
                if ("<init>".equals(method.name())) continue;
                maxArity.merge(method.name(), method.parameterTypes().size(), Math::max);
                sigsByName.computeIfAbsent(method.name(), k -> new LinkedHashSet<>())
                        .add(TopLevelOverload.sigTag(method.parameterTypes()));
            }
            for (IRMethod method : clazz.methods()) {
                if ("<init>".equals(method.name())) continue;
                int arity = method.parameterTypes().size();
                int max = maxArity.getOrDefault(method.name(), arity);
                String jsName = arity == max
                        ? method.name()
                        : method.name() + "$d" + (max - arity);
                // SG-011B: duas assinaturas distintas sob o mesmo nome → sufixo
                // de assinatura no NOME JS (JavaScript não tem sobrecarga). Um
                // único candidato por nome mantém o nome cru (zero regressão).
                boolean overloaded = sigsByName.get(method.name()).size() > 1;
                if (overloaded) jsName += TopLevelOverload.sigTag(method.parameterTypes()).replace('_', '$');
                lc.fnArityNames.computeIfAbsent(method.name(), k -> new HashMap<>())
                        .put(arity, jsName);
                if (overloaded) {
                    lc.fnSigNames.computeIfAbsent(method.name(), k -> new HashMap<>())
                            .put(TopLevelOverload.sigTag(method.parameterTypes()), jsName);
                }
            }
        }
        Map<String, Integer> ctorMaxArity = new HashMap<>();
        Map<String, Set<String>> ctorSigTokens = new HashMap<>();
        Set<String> ctorDispatch = new HashSet<>();
        for (IRClass clazz : effClasses) {
            if (JsLoweringContext.skipClass(clazz) || JsLoweringContext.isMainClass(clazz)) continue;
            Set<String> ctorSigs = new LinkedHashSet<>();
            for (IRMethod method : clazz.methods()) {
                if (!"<init>".equals(method.name())) continue;
                ctorSigs.add(TopLevelOverload.sigTag(method.parameterTypes()));
                ctorMaxArity.merge(clazz.name(), method.parameterTypes().size(), Math::max);
            }
            if (!ctorSigs.isEmpty()) {
                ctorSigTokens.put(clazz.name(), ctorSigs);
            }
            if (ctorSigs.size() > 1) ctorDispatch.add(clazz.name());
        }
        this.lc.ctorMaxArity = ctorMaxArity;
        this.lc.ctorSigTokens = ctorSigTokens;
        this.lc.ctorDispatch = ctorDispatch;
        computeAsyncColoring(effClasses);
        // #133 (§186): clinit por classe (inclui Main) — chamado no topo do
        // módulo, antes do main; JS não tem <clinit> nativo.
        List<JsIr.JsExpression> clinitTargets = new ArrayList<>();
        for (IRClass clazz : effClasses) {
            if (JsLoweringContext.skipClass(clazz)) continue;
            if (JsLoweringContext.isMainClass(clazz)) {
                for (IRMethod method : clazz.methods()) {
                    if ("<init>".equals(method.name())) continue;
                    if ("<clinit>".equals(method.name())) {
                        JsIr.JsFunction f = parser.lowerFunction(method, null, false, true);
                        functions.add(new JsIr.JsFunction("_kof_clinit", f.parameters(), f.body(),
                                false, false, true, f.isAsync(), f.kofLine()));
                        clinitTargets.add(new JsIr.JsIdentifier("_kof_clinit"));
                        continue;
                    }
                    functions.add(parser.lowerFunction(method, null, false, true));
                }
            } else {
                for (IRMethod method : clazz.methods()) {
                    if ("<clinit>".equals(method.name())) {
                        clinitTargets.add(new JsIr.JsMember(
                                new JsIr.JsIdentifier(JsTypeMapper.jsClassName(clazz.name())),
                                "_kof_clinit"));
                    }
                }
            }
        }
        for (IRClass clazz : effClasses) {
            if (JsLoweringContext.skipClass(clazz) || JsLoweringContext.isMainClass(clazz)) continue;
            classes.add(classEmitter.lowerClass(clazz));
        }
        // #740 slice 2: close the decode-helper set transitively (a nested
        // record field pulls in its own decoder) BEFORE emitting, so a nested
        // helper whose class sorts before the parent is not lost.
        boolean grew = true;
        while (grew) {
            grew = false;
            for (IRClass clazz : effClasses) {
                if (JsLoweringContext.skipClass(clazz) || JsLoweringContext.isMainClass(clazz)) continue;
                if (!lc.decodeHelpers.contains(JsTypeMapper.jsClassName(clazz.name()))) continue;
                for (String nested : classEmitter.nestedDecoderNames(clazz)) {
                    if (lc.decodeHelpers.add(nested)) grew = true;
                }
            }
        }
        for (IRClass clazz : effClasses) {
            if (JsLoweringContext.skipClass(clazz) || JsLoweringContext.isMainClass(clazz)) continue;
            if (lc.decodeHelpers.contains(JsTypeMapper.jsClassName(clazz.name()))) {
                functions.add(classEmitter.lowerDecodeHelper(clazz));
            }
        }
        List<JsIr.JsStatement> moduleStatements = new ArrayList<>();
        for (JsIr.JsExpression target : clinitTargets) {
            moduleStatements.add(new JsIr.JsExprStmt(new JsIr.JsCall(target, List.of())));
        }
        for (JsIr.JsFunction fn : functions) {
            if ("main".equals(fn.name())) {
                JsIr.JsExpression entry = new JsIr.JsCall(
                        new JsIr.JsIdentifier("main"), List.of());
                if (fn.isAsync()) {
                    entry = new JsIr.JsAwait(entry);
                }
                moduleStatements.add(new JsIr.JsExprStmt(entry));
                break;
            }
        }
        return new JsIr.JsModule(module.name(), classes, functions,
                new ArrayList<>(new LinkedHashSet<>(lc.runtimeImports)),
                new ArrayList<>(new LinkedHashSet<>(lc.ioRuntimeImports)), moduleStatements);
    }

    /**
     * §248: JavaScript has no runtime interface, so an interface default method
     * inherited by an implementor has no target. Materialise each inherited
     * default as a real method on the implementor (unless the class or one of its
     * superclasses overrides it), mirroring the JVM/Native semantics. This keeps
     * every downstream map (class methods, arity/signature names) consistent
     * because it runs before they are built.
     */
    private static List<IRClass> injectInterfaceDefaults(List<IRClass> all) {
        Map<String, IRClass> byName = new HashMap<>();
        for (IRClass c : all) {
            if (c.name() != null && !c.name().isBlank()) byName.put(c.name(), c);
        }
        List<IRClass> out = new ArrayList<>(all.size());
        for (IRClass c : all) {
            if ((c.accessFlags() & AccessFlags.INTERFACE) != 0) {
                out.add(c);
                continue;
            }
            List<IRMethod> defaults = collectInheritedDefaults(c, byName);
            if (defaults.isEmpty()) {
                out.add(c);
                continue;
            }
            List<IRMethod> methods = new ArrayList<>(c.methods());
            methods.addAll(defaults);
            out.add(new IRClass(c.name(), c.superName(), c.interfaces(), c.accessFlags(),
                    c.fields(), methods, c.innerClasses(), c.signature(), c.typeId(), c.annotations()));
        }
        return out;
    }

    private static List<IRMethod> collectInheritedDefaults(IRClass clazz, Map<String, IRClass> byName) {
        Set<String> overridden = new HashSet<>();
        Set<String> interfaces = new HashSet<>();
        for (String current = clazz.name(); current != null && !current.isEmpty()
                && !"java/lang/Object".equals(current) && !"java/lang/Record".equals(current); ) {
            IRClass c = byName.get(current);
            if (c == null) break;
            for (IRMethod m : c.methods()) overridden.add(m.name() + "/" + m.parameterTypes().size());
            for (String i : c.interfaces()) interfaces.add(i);
            current = c.superName();
        }
        List<IRMethod> out = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>(interfaces);
        Set<String> added = new HashSet<>();
        while (!queue.isEmpty()) {
            String ifaceName = queue.poll();
            if (!visited.add(ifaceName)) continue;
            IRClass iface = byName.get(ifaceName);
            if (iface == null) continue;
            for (IRMethod m : iface.methods()) {
                if ("<init>".equals(m.name()) || "<clinit>".equals(m.name())) continue;
                if (m.basicBlocks().isEmpty()) continue; // abstract → no body
                String key = m.name() + "/" + m.parameterTypes().size();
                if (overridden.contains(key) || !added.add(key)) continue;
                out.add(m);
            }
            for (String i : iface.interfaces()) queue.add(i);
        }
        return out;
    }

    private void computeAsyncColoring(List<IRClass> effClasses) {
        Map<String, Boolean> async = new HashMap<>();
        for (IRClass clazz : effClasses) {
            if (JsLoweringContext.skipClass(clazz)) continue;
            for (IRMethod method : clazz.methods()) {
                String key = JsLoweringContext.asyncMethodKey(clazz, method);
                async.put(key, false);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> asyncNamesAnywhere = new HashSet<>();
            for (Map.Entry<String, Boolean> e : async.entrySet()) {
                if (!e.getValue()) continue;
                asyncNamesAnywhere.add(JsLoweringContext.methodNameFromAsyncKey(e.getKey()));
            }
            for (IRClass clazz : effClasses) {
                if (JsLoweringContext.skipClass(clazz)) continue;
                boolean isTaskLambda = clazz.name() != null && clazz.name().startsWith("LambdaTask");
                boolean isRegularLambda = clazz.name() != null && clazz.name().startsWith("Lambda")
                        && !isTaskLambda;
                for (IRMethod method : clazz.methods()) {
                    String key = JsLoweringContext.asyncMethodKey(clazz, method);
                    if (async.get(key)) continue;
                    List<KofOperation> ops = method.basicBlocks().stream()
                            .flatMap(b -> b.operations().stream()).toList();
                    boolean markAsync = false;
                    for (KofOperation op : ops) {
                        if (!(op instanceof KofCall kc)) continue;
                        if (lc.ASYNC_RUNTIME_OPS.contains(kc.methodName())) {
                            markAsync = true;
                            break;
                        }
                        KofCallKind kind = kc.kind();
                        if (kind == KofCallKind.STATIC
                                || kind == KofCallKind.FUNCTION
                                || kind == KofCallKind.SUPER) {
                            if (async.getOrDefault(JsLoweringContext.calleeKeyFromCall(kc), false)) {
                                markAsync = true;
                                break;
                            }
                        } else if (kind == KofCallKind.INSTANCE || kind == KofCallKind.INTERFACE) {
                            if (asyncNamesAnywhere.contains(kc.methodName())) {
                                markAsync = true;
                                break;
                            }
                        }
                    }
                    if (markAsync) {
                        if (isRegularLambda) {
                            throw new IllegalStateException(
                                    "CONC003-JS-01: lambda passed to list.map/filter/reduce "
                                            + "(or a UI/timer/mq handler) cannot use "
                                            + "await/spawn/channel.receive() — only spawn { ... } can");
                        }
                        async.put(key, true);
                        changed = true;
                    }
                }
            }
        }
        Set<String> finalAsyncNames = new HashSet<>();
        for (Map.Entry<String, Boolean> e : async.entrySet()) {
            if (!e.getValue()) continue;
            finalAsyncNames.add(JsLoweringContext.methodNameFromAsyncKey(e.getKey()));
        }
        this.lc.asyncMethods = async;
        this.lc.asyncMethodNamesAnywhere = finalAsyncNames;
    }


















    // ── Per-method context ──────────────────────────────────────────





    // ── Statement parser ────────────────────────────────────────────





    // ── If statement ────────────────────────────────────────────────




    // ── Loops ───────────────────────────────────────────────────────





    // ── Try statement ───────────────────────────────────────────────


    // ── Switch statement ────────────────────────────────────────────



    // ── Expression statements ───────────────────────────────────────










    // ── Expression lowering ─────────────────────────────────────────




    // ── Calls ───────────────────────────────────────────────────────








    // ── Operator lowering ───────────────────────────────────────────











    // ── List / String / runtime lowering ────────────────────────────














    // ── Plumbing ────────────────────────────────────────────────────














}
