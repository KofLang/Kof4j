package dev.kof.compiler;

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
class JsBackend implements Backend {

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
        artifacts.writeRuntime(outputDir);
        artifacts.writeHtmlEntry(outputDir, module.name());
        if (debugInfo) {
            artifacts.writeSourceMap(module, outputDir, fileName, emitter.functionLines());
        }
    }

    // ── Module lowering ─────────────────────────────────────────────

    private JsIr.JsModule lowerModule(IRModule module) {
        List<JsIr.JsClass> classes = new ArrayList<>();
        List<JsIr.JsFunction> functions = new ArrayList<>();
        Map<String, Set<String>> methodNames = new HashMap<>();
        for (IRClass clazz : module.classes()) {
            if (!JsLoweringContext.skipClass(clazz)) {
                methodNames.put(clazz.name(), new HashSet<>());
                for (IRMethod m : clazz.methods()) {
                    methodNames.get(clazz.name()).add(m.name());
                }
                if ("java/lang/Record".equals(clazz.superName())) {
                    // record gera equals() no JS (bug 11) — registra para o
                    // dispatch de .equals()/== não cair em referência (===)
                    methodNames.get(clazz.name()).add("equals");
                }
            }
        }
        this.lc.classMethodNames = methodNames;
        lc.recordClassNames.clear();
        for (IRClass clazz : module.classes()) {
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
        for (IRClass clazz : module.classes()) {
            if (JsLoweringContext.skipClass(clazz) || !JsLoweringContext.isMainClass(clazz)) continue;
            Map<String, Integer> maxArity = new HashMap<>();
            for (IRMethod method : clazz.methods()) {
                if ("<init>".equals(method.name())) continue;
                maxArity.merge(method.name(), method.parameterTypes().size(), Math::max);
            }
            for (IRMethod method : clazz.methods()) {
                if ("<init>".equals(method.name())) continue;
                int arity = method.parameterTypes().size();
                int max = maxArity.getOrDefault(method.name(), arity);
                String jsName = arity == max
                        ? method.name()
                        : method.name() + "$d" + (max - arity);
                lc.fnArityNames.computeIfAbsent(method.name(), k -> new HashMap<>())
                        .put(arity, jsName);
            }
        }
        computeAsyncColoring(module);
        for (IRClass clazz : module.classes()) {
            if (JsLoweringContext.skipClass(clazz)) continue;
            if (JsLoweringContext.isMainClass(clazz)) {
                for (IRMethod method : clazz.methods()) {
                    if ("<init>".equals(method.name())) continue;
                    functions.add(parser.lowerFunction(method, null, false, true));
                }
            }
        }
        for (IRClass clazz : module.classes()) {
            if (JsLoweringContext.skipClass(clazz) || JsLoweringContext.isMainClass(clazz)) continue;
            classes.add(classEmitter.lowerClass(clazz));
        }
        for (IRClass clazz : module.classes()) {
            if (JsLoweringContext.skipClass(clazz) || JsLoweringContext.isMainClass(clazz)) continue;
            if (lc.decodeHelpers.contains(JsTypeMapper.jsClassName(clazz.name()))) {
                functions.add(classEmitter.lowerDecodeHelper(clazz));
            }
        }
        List<JsIr.JsStatement> moduleStatements = new ArrayList<>();
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

    private void computeAsyncColoring(IRModule module) {
        Map<String, Boolean> async = new HashMap<>();
        for (IRClass clazz : module.classes()) {
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
            for (IRClass clazz : module.classes()) {
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
                                    "CONC003-JS-01: lambda passada para list.map/filter/reduce "
                                            + "(ou handler de UI/timer/mq) não pode usar "
                                            + "await/spawn/channel.receive() — só spawn { ... } pode");
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
