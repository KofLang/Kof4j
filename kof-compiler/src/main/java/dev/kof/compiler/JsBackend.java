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

    private static final Set<String> RESERVED = Set.of(
            "class", "function", "var", "let", "const", "return", "if", "else", "while", "do",
            "for", "switch", "case", "default", "break", "continue", "new", "delete", "typeof",
            "instanceof", "in", "try", "catch", "finally", "throw", "this", "super", "null",
            "true", "false", "void", "static", "extends", "import", "export", "yield", "await",
            "async", "of", "arguments", "eval");

    private final List<String> runtimeImports = new ArrayList<>();
    private final List<String> ioRuntimeImports = new ArrayList<>();
    private final Set<String> decodeHelpers = new HashSet<>();
    private final Set<String> recordClassNames = new HashSet<>();
    private Map<String, Set<String>> classMethodNames = Map.of();
    private Map<String, Map<Integer, String>> fnArityNames = Map.of();
    private Map<String, Boolean> asyncMethods = Map.of();
    private Set<String> asyncMethodNamesAnywhere = Set.of();

    // kof_spawn_result/kof_spawn NÃO entram aqui de propósito: spawnar uma
    // task não bloqueia quem chama, só await/receive/selectAny bloqueiam.
    // kofSpawnResult() é uma função JS comum (não-async) que devolve um
    // handle na hora — não exige que quem a chama seja async. O caso de uma
    // task esquecida (handle nunca esperado) já é coberto independentemente
    // pelo pump de kofActiveTasks em KofJsRunner, não pela coloração.
    private static final Set<String> ASYNC_RUNTIME_OPS = Set.of(
            "kof_await", "kof_await_timeout", "kof_channel_receive", "kof_select_any");

    /** JS name for a top-level function call resolved by (name, arity). */
    private String jsFunctionName(String name, int arity) {
        Map<Integer, String> byArity = fnArityNames.get(name);
        if (byArity != null) {
            String resolved = byArity.get(arity);
            if (resolved != null) return resolved;
        }
        return name;
    }

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        emit(module, outputDir, true);
    }

    @Override
    public void emit(IRModule module, Path outputDir, boolean debugInfo) throws IOException {
        Files.createDirectories(outputDir);
        runtimeImports.clear();
        ioRuntimeImports.clear();
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
            if (!skipClass(clazz)) {
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
        this.classMethodNames = methodNames;
        recordClassNames.clear();
        for (IRClass clazz : module.classes()) {
            if ("java/lang/Record".equals(clazz.superName())) {
                recordClassNames.add(clazz.name());
                recordClassNames.add(clazz.name().replace('/', '.'));
                // also add simple name
                String simple = clazz.name().substring(clazz.name().lastIndexOf('/') + 1);
                recordClassNames.add(simple);
            }
        }
        // Default-parameter wrappers share the canonical name; JS has no
        // overloading, so wrappers are mangled by dropped-arity and calls
        // are routed by (name, arity).
        this.fnArityNames = new HashMap<>();
        for (IRClass clazz : module.classes()) {
            if (skipClass(clazz) || !isMainClass(clazz)) continue;
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
                fnArityNames.computeIfAbsent(method.name(), k -> new HashMap<>())
                        .put(arity, jsName);
            }
        }
        computeAsyncColoring(module);
        for (IRClass clazz : module.classes()) {
            if (skipClass(clazz)) continue;
            if (isMainClass(clazz)) {
                for (IRMethod method : clazz.methods()) {
                    if ("<init>".equals(method.name())) continue;
                    functions.add(lowerFunction(method, null, false, true));
                }
            }
        }
        for (IRClass clazz : module.classes()) {
            if (skipClass(clazz) || isMainClass(clazz)) continue;
            classes.add(lowerClass(clazz));
        }
        for (IRClass clazz : module.classes()) {
            if (skipClass(clazz) || isMainClass(clazz)) continue;
            if (decodeHelpers.contains(jsClassName(clazz.name()))) {
                functions.add(lowerDecodeHelper(clazz));
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
                new ArrayList<>(new LinkedHashSet<>(runtimeImports)),
                new ArrayList<>(new LinkedHashSet<>(ioRuntimeImports)), moduleStatements);
    }

    private void computeAsyncColoring(IRModule module) {
        Map<String, Boolean> async = new HashMap<>();
        for (IRClass clazz : module.classes()) {
            if (skipClass(clazz)) continue;
            for (IRMethod method : clazz.methods()) {
                String key = asyncMethodKey(clazz, method);
                async.put(key, false);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> asyncNamesAnywhere = new HashSet<>();
            for (Map.Entry<String, Boolean> e : async.entrySet()) {
                if (!e.getValue()) continue;
                asyncNamesAnywhere.add(methodNameFromAsyncKey(e.getKey()));
            }
            for (IRClass clazz : module.classes()) {
                if (skipClass(clazz)) continue;
                boolean isTaskLambda = clazz.name() != null && clazz.name().startsWith("LambdaTask");
                boolean isRegularLambda = clazz.name() != null && clazz.name().startsWith("Lambda")
                        && !isTaskLambda;
                for (IRMethod method : clazz.methods()) {
                    String key = asyncMethodKey(clazz, method);
                    if (async.get(key)) continue;
                    List<KofOperation> ops = method.basicBlocks().stream()
                            .flatMap(b -> b.operations().stream()).toList();
                    boolean markAsync = false;
                    for (KofOperation op : ops) {
                        if (!(op instanceof KofCall kc)) continue;
                        if (ASYNC_RUNTIME_OPS.contains(kc.methodName())) {
                            markAsync = true;
                            break;
                        }
                        KofCallKind kind = kc.kind();
                        if (kind == KofCallKind.STATIC
                                || kind == KofCallKind.FUNCTION
                                || kind == KofCallKind.SUPER) {
                            if (async.getOrDefault(calleeKeyFromCall(kc), false)) {
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
            finalAsyncNames.add(methodNameFromAsyncKey(e.getKey()));
        }
        this.asyncMethods = async;
        this.asyncMethodNamesAnywhere = finalAsyncNames;
    }

    private static String methodNameFromAsyncKey(String key) {
        int hash = key.lastIndexOf('#');
        String rest = hash >= 0 ? key.substring(hash + 1) : key;
        int slash = rest.lastIndexOf('/');
        return slash >= 0 ? rest.substring(0, slash) : rest;
    }

    private static String asyncMethodKey(IRClass clazz, IRMethod method) {
        int arity = method.parameterTypes().size();
        if (isMainClass(clazz)) return "#" + method.name() + "/" + arity;
        return clazz.name() + "#" + method.name() + "/" + arity;
    }

    private String calleeKeyFromCall(KofCall kc) {
        int arity = kc.parameterTypes().size();
        String owner = ownerInternalName(kc.ownerType());
        if (owner.isEmpty() || isMainInternalName(owner)) return "#" + kc.methodName() + "/" + arity;
        return owner + "#" + kc.methodName() + "/" + arity;
    }

    private static boolean isMainInternalName(String internalName) {
        return "Main".equals(internalName) || internalName.endsWith("/Main");
    }

    private static boolean skipClass(IRClass clazz) {
        if (clazz.name() == null || clazz.name().isBlank()) return true;
        if ("java/lang/Object".equals(clazz.name()) || "java/lang/Record".equals(clazz.name())) return true;
        // Interfaces are type-level only in Kof; JavaScript has no runtime
        // interface. Calls through interfaces lower to structural method
        // calls (receiver.method(...)), so no JS entity is required.
        return (clazz.accessFlags() & AccessFlags.INTERFACE) != 0;
    }

    private static boolean isMainClass(IRClass clazz) {
        return "Main".equals(clazz.name()) || clazz.name().endsWith("/Main");
    }

    private JsIr.JsClass lowerClass(IRClass clazz) {
        String jsName = jsClassName(clazz.name());
        String jsSuper = null;
        if (clazz.superName() != null && !clazz.superName().isEmpty()
                && !"java/lang/Object".equals(clazz.superName())
                && !"java/lang/Record".equals(clazz.superName())) {
            jsSuper = jsClassName(clazz.superName());
        }
        boolean isRecord = "java/lang/Record".equals(clazz.superName());
        List<JsIr.JsField> fields = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            boolean isStatic = (field.accessFlags() & AccessFlags.STATIC) != 0;
            String fieldName = isRecord ? "_" + sanitizeName(field.name()) : sanitizeName(field.name());
            fields.add(new JsIr.JsField(fieldName,
                    field.initialValue() != null ? literalText(field.initialValue()) : null, isStatic));
        }
        List<JsIr.JsFunction> methods = new ArrayList<>();
        IRMethod canonicalCtor = null;
        for (IRMethod method : clazz.methods()) {
            if ("<init>".equals(method.name())
                    && (canonicalCtor == null
                    || method.parameterTypes().size() > canonicalCtor.parameterTypes().size())) {
                canonicalCtor = method;
            }
        }
        for (IRMethod method : clazz.methods()) {
            if ("<init>".equals(method.name())) {
                // A JS class can only have one constructor: the canonical
                // (max-arity) one is emitted. Default-parameter wrapper
                // constructors only exist for the JVM/Native backends.
                if (method == canonicalCtor) {
                    methods.add(lowerConstructor(clazz, method));
                }
            } else {
                boolean isStatic = (method.accessFlags() & AccessFlags.STATIC) != 0;
                methods.add(lowerFunction(method, clazz, isStatic));
            }
        }
        if (isRecord) {
            methods.add(lowerRecordToString(clazz));
            methods.add(lowerRecordToJson(clazz));
            methods.add(lowerRecordEquals(clazz));
        }
        return new JsIr.JsClass(jsName, jsSuper, fields, methods);
    }

    /**
     * Records: the component fields are private in Kof/JVM; in JS the accessor
     * method shares the component name, so the backing field gets a "_" prefix
     * (this.name as a property would shadow the name() accessor).
     */
    private String jsFieldName(IRClass clazz, String name) {
        if ("java/lang/Record".equals(clazz.superName())) {
            return "_" + sanitizeName(name);
        }
        return sanitizeName(name);
    }

    /**
     * Records get a toString() in JS to mirror the JVM backend's synthetic
     * record toString: "Name[f1=..., f2=...]".
     */
    private JsIr.JsFunction lowerRecordToString(IRClass clazz) {
        String simpleName = clazz.name().contains("/")
                ? clazz.name().substring(clazz.name().lastIndexOf('/') + 1) : clazz.name();
        List<JsIr.JsExpression> parts = new ArrayList<>();
        parts.add(new JsIr.JsString(simpleName + "["));
        for (int i = 0; i < clazz.fields().size(); i++) {
            if (i > 0) parts.add(new JsIr.JsString(", "));
            parts.add(new JsIr.JsString(clazz.fields().get(i).name() + "="));
            parts.add(new JsIr.JsMember(new JsIr.JsThis(),
                    "_" + sanitizeName(clazz.fields().get(i).name())));
        }
        parts.add(new JsIr.JsString("]"));
        JsIr.JsExpression joined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            joined = new JsIr.JsBinary(joined, "+", parts.get(i));
        }
        return new JsIr.JsFunction("toString", List.of(),
                List.of(new JsIr.JsReturn(joined)), false, false, false);
    }

    /**
     * Records: igualdade de conteúdo no JS (bug 11) — compara todos os
     * componentes. O lowering de `==` em records despacha para `.equals()`
     * em todos os targets.
     */
    private JsIr.JsFunction lowerRecordEquals(IRClass clazz) {
        List<JsIr.JsExpression> conds = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            String backing = "_" + sanitizeName(field.name());
            conds.add(new JsIr.JsBinary(
                    new JsIr.JsMember(new JsIr.JsThis(), backing),
                    "===",
                    new JsIr.JsMember(new JsIr.JsIdentifier("other"), backing)));
        }
        JsIr.JsExpression body = null;
        for (int i = conds.size() - 1; i >= 0; i--) {
            body = (body == null) ? conds.get(i)
                    : new JsIr.JsBinary(conds.get(i), "&&", body);
        }
        if (body == null) body = new JsIr.JsNumber("1");
        // Kof bool é int (0/1): o equals gerado devolve 1/0 para operações
        // subsequentes (ex.: `a != c` compara com 0) não quebrarem.
        JsIr.JsExpression kofBool = new JsIr.JsConditional(
                body, new JsIr.JsNumber("1"), new JsIr.JsNumber("0"));
        return new JsIr.JsFunction("equals", List.of("other"),
                List.of(new JsIr.JsReturn(kofBool)), false, false, false);
    }

    /**
     * Records serialize as { "f1": ..., "f2": ... } to mirror the JVM backend's
     * reflection-based JSON encoding (JSON.stringify honors toJSON()).
     */
    private JsIr.JsFunction lowerRecordToJson(IRClass clazz) {
        List<JsIr.JsObjectEntry> entries = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            entries.add(new JsIr.JsObjectEntry(field.name(),
                    new JsIr.JsMember(new JsIr.JsThis(), "_" + sanitizeName(field.name()))));
        }
        return new JsIr.JsFunction("toJSON", List.of(),
                List.of(new JsIr.JsReturn(new JsIr.JsObjectLiteral(entries))), false, false, false);
    }

    /**
     * json.decode&lt;Class&gt; binds the parsed object to the Kof class:
     * records use their canonical constructor; classes get a default instance
     * with fields assigned by name (mirroring the JVM reflection binding).
     */
    private JsIr.JsFunction lowerDecodeHelper(IRClass clazz) {
        String jsName = jsClassName(clazz.name());
        boolean isRecord = "java/lang/Record".equals(clazz.superName());
        // Accept both a JSON string and an already-parsed object (list decode
        // maps parsed elements through this helper).
        JsIr.JsExpression parsed = new JsIr.JsConditional(
                new JsIr.JsBinary(new JsIr.JsUnary("typeof", new JsIr.JsIdentifier("json")),
                        "===", new JsIr.JsString("string")),
                new JsIr.JsCall(new JsIr.JsIdentifier("JSON.parse"),
                        List.of(new JsIr.JsIdentifier("json"))),
                new JsIr.JsIdentifier("json"));
        List<JsIr.JsStatement> body = new ArrayList<>();
        body.add(new JsIr.JsVarDecl("p", parsed, true));
        List<JsIr.JsExpression> ctorArgs = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            ctorArgs.add(new JsIr.JsMember(new JsIr.JsIdentifier("p"), sanitizeName(field.name())));
        }
        JsIr.JsExpression instance = new JsIr.JsNew(new JsIr.JsIdentifier(jsName), ctorArgs);
        if (isRecord) {
            body.add(new JsIr.JsVarDecl("o", instance, true));
        } else {
            body.add(new JsIr.JsVarDecl("o", new JsIr.JsNew(new JsIr.JsIdentifier(jsName), List.of()), true));
            for (IRField field : clazz.fields()) {
                body.add(new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(new JsIr.JsIdentifier("o"), sanitizeName(field.name())), "=",
                        new JsIr.JsMember(new JsIr.JsIdentifier("p"), sanitizeName(field.name())))));
            }
        }
        body.add(new JsIr.JsReturn(new JsIr.JsIdentifier("o")));
        return new JsIr.JsFunction("__kof_decode_" + jsName, List.of("json"), body, false, false, true);
    }

    private JsIr.JsFunction lowerConstructor(IRClass clazz, IRMethod method) {
        MethodCtx ctx = new MethodCtx(method, clazz);
        List<JsIr.JsStatement> body = parseMethodBody(ctx);
        insertFieldDefaults(clazz, body);
        insertSuperCall(clazz, body);
        return new JsIr.JsFunction("constructor", parameterNames(ctx), body, false, true, false, false,
                firstKofLine(method));
    }

    private JsIr.JsFunction lowerFunction(IRMethod method, IRClass clazz, boolean isStatic) {
        return lowerFunction(method, clazz, isStatic, false);
    }

    private JsIr.JsFunction lowerFunction(IRMethod method, IRClass clazz, boolean isStatic, boolean isTopLevel) {
        MethodCtx ctx = new MethodCtx(method, clazz);
        String name = method.name();
        if ("<init>".equals(name)) name = "constructor";
        if (isTopLevel) {
            name = jsFunctionName(name, method.parameterTypes().size());
        }
        return new JsIr.JsFunction(name, parameterNames(ctx), parseMethodBody(ctx), isStatic, false, isTopLevel,
                ctx.isAsync, firstKofLine(method));
    }

    /**
     * Linha Kof da primeira instrução do método (para o source map V3) — vem do
     * {@code KofDebugInfo} que o driver já popula (mesma fonte das line tables
     * do JVM). Sintéticos (toString/toJSON/decode) não têm fonte → null.
     */
    private static Integer firstKofLine(IRMethod method) {
        if (method.debugInfo() == null || method.debugInfo().positions().isEmpty()) return null;
        Integer min = null;
        for (SourcePosition p : method.debugInfo().positions().values()) {
            if (p != null && p.line() > 0) {
                min = (min == null) ? p.line() : Math.min(min, p.line());
            }
        }
        return min;
    }

    private List<JsIr.JsStatement> parseMethodBody(MethodCtx ctx) {
        currentCtxOpsDump = ctx.ops;
        int[] pos = {0};
        List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        if (pos[0] < ctx.ops.size()) {
            throw new IllegalStateException("KofJS: unconsumed ops in method "
                    + ctx.kofClassName + "." + (ctx.methodName == null ? "?" : ctx.methodName)
                    + " at " + ctx.ops.get(pos[0]));
        }
        List<JsIr.JsStatement> predecl = new ArrayList<>();
        int paramStart = ctx.instanceMethod ? 1 : 0;
        int paramEnd = paramStart + ctx.paramCount
                + (ctx.captureSlots.isEmpty() ? 0 : ctx.captureSlots.size());
        for (int slot : ctx.localNames.keySet()) {
            if (slot < paramEnd) continue;
            String name = ctx.localNames.get(slot);
            if (name != null && ctx.declared.add(slot)) {
                predecl.add(new JsIr.JsVarDecl(name, null, false));
            }
        }
        if (!predecl.isEmpty() || !ctx.tempDecls.isEmpty()) {
            List<JsIr.JsStatement> withTemps = new ArrayList<>();
            for (JsIr.JsStatement d : predecl) {
                withTemps.add(d);
            }
            for (String decl : ctx.tempDecls) {
                withTemps.add(new JsIr.JsVarDecl(decl, null, false));
            }
            withTemps.addAll(body);
            return withTemps;
        }
        return body;
    }

    // ── Per-method context ──────────────────────────────────────────

    private record LoopCtx(LabelId start, LabelId continueLabel, LabelId end) {
    }

    /**
     * A pending `new T` awaiting its <init> call: [NewPending, args...] or
     * [NewPending, DupMarker, args...] — lowered to `new T(args)`.
     */
    private record NewPending(String typeName) {
    }

    private static final class DupMarker {
    }

    private final class MethodCtx {
        final List<KofOperation> ops;
        final Map<Integer, String> localNames = new HashMap<>();
        final Map<Integer, String> rawLocalNames = new HashMap<>();
        final Set<Integer> declared = new HashSet<>();
        final Set<String> usedNames = new HashSet<>();
        final List<String> tempDecls = new ArrayList<>();
        final List<LoopCtx> loops = new ArrayList<>();
        final boolean instanceMethod;
        final String kofClassName;
        final String methodName;
        final int paramCount;
        final boolean recordClass;
        final boolean isAsync;
        /** slots of lambda capture fields (come before the real parameters) */
        final Set<Integer> captureSlots = new HashSet<>();
        int tempCounter = 0;

        MethodCtx(IRMethod method, IRClass clazz) {
            this.ops = new ArrayList<>(method.basicBlocks().stream()
                    .flatMap(b -> b.operations().stream()).toList());
            this.instanceMethod = clazz != null && !isMainClass(clazz)
                    && (method.accessFlags() & AccessFlags.STATIC) == 0;
            this.kofClassName = clazz == null ? null : clazz.name();
            this.methodName = method.name();
            this.paramCount = method.parameterTypes().size();
            this.recordClass = clazz != null && "java/lang/Record".equals(clazz.superName());
            String asyncKey = clazz == null
                    ? "#" + method.name() + "/" + method.parameterTypes().size()
                    : asyncMethodKey(clazz, method);
            this.isAsync = asyncMethods.getOrDefault(asyncKey, false);
            // lambda synthetic classes hold captured locals as private final
            // fields at the first slots; the real parameters come after them.
            Set<String> captureFields = new HashSet<>();
            if (clazz != null && clazz.name() != null
                    && (clazz.name().startsWith("Lambda") || clazz.name().startsWith("LambdaTask"))) {
                for (IRField f : clazz.fields()) {
                    if ((f.accessFlags() & AccessFlags.PRIVATE) != 0
                            && (f.accessFlags() & AccessFlags.FINAL) != 0) {
                        captureFields.add(f.name());
                    }
                }
            }
            for (IRLocalVariable lv : method.localVariables()) {
                rawLocalNames.put(lv.index(), lv.name());
                if (instanceMethod && lv.index() == 0) {
                    localNames.put(lv.index(), "this");
                    continue;
                }
                if (captureFields.contains(lv.name()) && lv.index() < 1 + captureFields.size()
                        && !"<init>".equals(method.name())) {
                    // invoke(): the captures are fields copied to locals before
                    // the real params — they are NOT the method's parameters.
                    // <init>() receives the captures AS its parameters.
                    captureSlots.add(lv.index());
                }
                localNames.put(lv.index(), uniqueName(sanitizeName(lv.name())));
            }
        }

        String uniqueName(String base) {
            String name = base;
            int n = 1;
            while (!usedNames.add(name)) {
                name = base + "_" + (n++);
            }
            return name;
        }

        String freshTemp() {
            String name = uniqueName("__kof_t" + (tempCounter++));
            tempDecls.add(name);
            return name;
        }

        LoopCtx currentLoop() {
            return loops.isEmpty() ? null : loops.get(loops.size() - 1);
        }

        boolean isLoopLabel(LabelId label) {
            for (LoopCtx lc : loops) {
                if (label.equals(lc.start) || label.equals(lc.continueLabel) || label.equals(lc.end)) {
                    return true;
                }
            }
            return false;
        }

        boolean isLoopEnd(LabelId label) {
            for (LoopCtx lc : loops) {
                if (label.equals(lc.end)) return true;
            }
            return false;
        }

        boolean hasClassMethod(String kofClassName, String method) {
            Set<String> names = classMethodNames.get(kofClassName);
            return names != null && names.contains(method);
        }
    }

    // ── Statement parser ────────────────────────────────────────────

    /**
     * Parses a statement list. The region ends when:
     *  - a label/jump in endLabels is encountered (consumed);
     *  - a jump to an unknown label is found (region exit, consumed);
     *  - the continue label of the enclosing for-loop is found (not consumed);
     *  - an unmatched label (belongs to the enclosing pattern) is found
     *    (not consumed).
     * Region exits are recorded in exits (jump targets).
     */
    private List<JsIr.JsStatement> parseStatements(MethodCtx ctx, int[] pos,
                                                   Set<LabelId> endLabels, List<LabelId> exits) {
        List<JsIr.JsStatement> out = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof KofLabel kl) {
                if (endLabels.contains(kl.label())) {
                    pos[0]++;
                    exits.add(kl.label());
                    return out;
                }
                if (ctx.isLoopLabel(kl.label()) || looksLikeContinueLabel(ctx, pos, kl.label())) {
                    return out;
                }
                if (isLoopStart(ctx, pos, kl.label())) {
                    out.add(parseLoop(ctx, pos, kl.label()));
                    continue;
                }
                // unmatched label — the enclosing pattern owns it
                return out;
            }
            if (op instanceof KofJump kj) {
                if (endLabels.contains(kj.target())) {
                    pos[0]++;
                    exits.add(kj.target());
                    return out;
                }
                if (ctx.isLoopLabel(kj.target())) {
                    pos[0]++;
                    if (ctx.isLoopEnd(kj.target())) {
                        out.add(new JsIr.JsBreak());
                    } else {
                        out.add(new JsIr.JsContinue());
                    }
                    continue;
                }
                // region exit (if/try/finally jump)
                pos[0]++;
                exits.add(kj.target());
                return out;
            }
            if (op instanceof KofTryEnd) {
                // fim da região do try — o dono (parseTryStatement) consome
                return out;
            }
            out.addAll(parseStatement(ctx, pos));
        }
        return out;
    }

    /**
     * The continue label of the enclosing for-loop: a label followed by the
     * update statements and the back-edge jump to the loop start.
     */
    private boolean looksLikeContinueLabel(MethodCtx ctx, int[] pos, LabelId label) {
        LoopCtx loop = ctx.currentLoop();
        if (loop == null || label.equals(loop.start) || label.equals(loop.end)) return false;
        for (int i = pos[0] + 1; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofJump kj) {
                return kj.target().equals(loop.start);
            }
            if (op instanceof KofLabel || op instanceof KofConditionalJump
                    || op instanceof KofTryStart || op instanceof KofCatchStart
                    || op instanceof KofReturn || op instanceof KofReturnVoid
                    || op instanceof KofThrow) {
                return false;
            }
        }
        return false;
    }

    /**
     * A label is a loop start when a later instruction jumps to it (back edge)
     * or conditionally jumps to it (do-while condition).
     */
    private boolean isLoopStart(MethodCtx ctx, int[] pos, LabelId label) {
        for (int i = pos[0] + 1; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofJump kj && kj.target().equals(label)) return true;
            if (op instanceof KofConditionalJump cj && cj.trueLabel().equals(label)) return true;
        }
        return false;
    }

    private List<JsIr.JsStatement> parseStatement(MethodCtx ctx, int[] pos) {
        KofOperation op = ctx.ops.get(pos[0]);
        if (op instanceof KofReturnVoid) {
            pos[0]++;
            return List.of(new JsIr.JsReturn(null));
        }
        if (op instanceof KofTryStart) {
            return List.of(parseTryStatement(ctx, pos));
        }
        if (op instanceof KofLoadLocal ll && "#switch".equals(ctx.rawLocalNames.get(ll.index()))) {
            // Distinguish switch test (load #switch + instanceof or case value) from
            // pattern body prologue (load #switch + checkcast). The body prologue
            // should be handled as a normal store expression, not as a switch.
            if (pos[0] + 1 < ctx.ops.size() && ctx.ops.get(pos[0] + 1) instanceof KofCheckCast) {
                // pattern body: let s = #switch; fall through to expression handling
            } else {
                return List.of(parseSwitchStatement(ctx, pos));
            }
        }
        if (op instanceof KofJump kj) {
            pos[0]++;
            if (ctx.isLoopEnd(kj.target())) {
                return List.of(new JsIr.JsBreak());
            }
            return List.of(new JsIr.JsContinue());
        }
        if (op instanceof KofLabel) {
            throw new IllegalStateException("KofJS: unexpected label at statement level");
        }
        if (op instanceof KofCatchStart) {
            throw new IllegalStateException("KofJS: unexpected KofCatchStart at statement level");
        }
        return parseExpressionStatement(ctx, pos);
    }

    // ── If statement ────────────────────────────────────────────────

    /**
     * Statement-level if: [cond ops, CJump, Label(true), then, Jump(end),
     * Label(false), (else), Label(end)].
     */
    private JsIr.JsStatement parseIfBody(MethodCtx ctx, int[] pos, KofConditionalJump cj,
                                         JsIr.JsExpression condition, List<Object> stack) {
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl && kl.label().equals(cj.trueLabel()))) {
            throw new IllegalStateException("KofJS: if pattern expected Label(true)");
        }
        pos[0]++;
        List<JsIr.JsStatement> thenBranch = parseStatements(ctx, pos, Set.of(cj.falseLabel()), new ArrayList<>());
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl2
                && kl2.label().equals(cj.falseLabel())) {
            pos[0]++;
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
            // Label(end) — no else branch. A loop label (continue/start) is
            // not the if's end; it belongs to the enclosing loop.
            KofLabel end = (KofLabel) ctx.ops.get(pos[0]);
            if (!ctx.isLoopLabel(end.label())) {
                pos[0]++;
            }
            return new JsIr.JsIf(condition, thenBranch, List.of());
        }
        List<JsIr.JsStatement> elseBranch = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl3
                && !ctx.isLoopLabel(kl3.label())) {
            // Label(end) — end of else branch (loop labels belong to the loop)
            pos[0]++;
        }
        return new JsIr.JsIf(condition, thenBranch, elseBranch);
    }

    /**
     * Attempts to parse an if-expression: [Label(true), expr, Jump(end),
     * Label(false), expr, Label(end)]. Returns null (restoring the position)
     * when the upcoming ops form a statement-level if instead.
     */
    private JsIr.JsExpression tryParseIfExpr(MethodCtx ctx, int[] pos, KofConditionalJump cj,
                                             JsIr.JsExpression condition) {
        int saved = pos[0];
        try {
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl && kl.label().equals(cj.trueLabel()))) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            JsIr.JsExpression thenExpr = parseExpressionFragment(ctx, pos);
            if (!(ctx.ops.get(pos[0]) instanceof KofJump)) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl2 && kl2.label().equals(cj.falseLabel()))) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            JsIr.JsExpression elseExpr = parseExpressionFragment(ctx, pos);
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel)) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            return new JsIr.JsConditional(condition, thenExpr, elseExpr);
        } catch (RuntimeException e) {
            // statement-level if: expressions in the branches failed, so the
            // construct is a statement, not an if-expression
            pos[0] = saved;
            return null;
        }
    }

    private JsIr.JsExpression comparisonExpr(KofComparison comp, JsIr.JsExpression left, JsIr.JsExpression right) {
        if (comp == KofComparison.NE && right instanceof JsIr.JsNumber n && "0".equals(n.text())) {
            // boolean conditions: (cond, 0) CJump(NE) — truthiness in JS
            return left;
        }
        return switch (comp) {
            case EQ -> new JsIr.JsBinary(left, "===", right);
            case NE -> new JsIr.JsBinary(left, "!==", right);
            case LT -> new JsIr.JsBinary(left, "<", right);
            case LE -> new JsIr.JsBinary(left, "<=", right);
            case GT -> new JsIr.JsBinary(left, ">", right);
            case GE -> new JsIr.JsBinary(left, ">=", right);
        };
    }

    // ── Loops ───────────────────────────────────────────────────────

    private JsIr.JsStatement parseLoop(MethodCtx ctx, int[] pos, LabelId startLabel) {
        pos[0]++;
        // A do-while loop is the only construct whose conditional jump targets
        // its own start label; scan the whole remaining stream to find it.
        KofConditionalJump doWhileJump = null;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofConditionalJump cj && cj.trueLabel().equals(startLabel)) {
                doWhileJump = cj;
                break;
            }
        }
        if (doWhileJump != null) {
            return parseDoWhile(ctx, pos, startLabel, doWhileJump);
        }
        // while / for: condition ops, CJump(body, end), Label(body), body, ...
        // while / for / for-in: condition ops, CJump(body, end), Label(body), body, ...
        // A condition region contains a CJump before any statement boundary;
        // otherwise the optimizer folded while(true) into a direct jump and
        // the ops are the loop body (parse it without consuming anything).
        boolean hasCondition = false;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofConditionalJump) {
                hasCondition = true;
                break;
            }
            if (op instanceof KofStoreLocal || op instanceof KofLabel
                    || op instanceof KofJump || op instanceof KofReturn
                    || op instanceof KofReturnVoid || op instanceof KofThrow
                    || op instanceof KofPop) {
                break;
            }
        }
        if (!hasCondition) {
            // while (true): [Label(start), body..., Jump(start), Label(end)]
            return parseTrueLoop(ctx, pos, startLabel);
        }
        List<Object> condStack = new ArrayList<>();
        List<JsIr.JsExpression> condPreamble = new ArrayList<>();
        while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofConditionalJump)) {
            if (ctx.ops.get(pos[0]) instanceof KofStoreLocal
                    || !isExpressionOp(ctx.ops.get(pos[0]))) {
                break;
            }
            consumeExpressionOp(ctx, pos, condStack, condPreamble);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj2)) {
            throw new IllegalStateException("KofJS: loop condition not terminated");
        }
        pos[0]++;
        JsIr.JsExpression right = pop(condStack);
        JsIr.JsExpression left = pop(condStack);
        if (!condStack.isEmpty()) {
            throw new IllegalStateException("KofJS: malformed loop condition stack");
        }
        JsIr.JsExpression condition = comparisonExpr(cj2.comparison(), left, right);
        if (!condPreamble.isEmpty()) {
            condition = new JsIr.JsSequence(condPreamble, condition);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel bodyLabel && bodyLabel.label().equals(cj2.trueLabel()))) {
            throw new IllegalStateException("KofJS: loop body label mismatch");
        }
        pos[0]++;
        // Detect the continue label before parsing the body: the back edge is
        // the first Jump(start) after the body label, and the continue label
        // is the label immediately before the update statements (for-loops).
        LabelId continueLabel = startLabel;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofJump kj && kj.target().equals(startLabel)) {
                for (int j = i - 1; j >= pos[0]; j--) {
                    KofOperation op = ctx.ops.get(j);
                    if (op instanceof KofLabel kl) {
                        continueLabel = kl.label();
                        break;
                    }
                    if (op instanceof KofJump || op instanceof KofConditionalJump) break;
                }
                break;
            }
        }
        ctx.loops.add(new LoopCtx(startLabel, continueLabel, cj2.falseLabel()));
        List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(startLabel), new ArrayList<>());
        ctx.loops.remove(ctx.loops.size() - 1);
        // After the body: either Jump(start) (while) or Label(continue) + update + Jump(start) (for).
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel nextLabel
                && !nextLabel.label().equals(startLabel)
                && !nextLabel.label().equals(cj2.falseLabel())) {
            pos[0]++;
            List<JsIr.JsStatement> update = new ArrayList<>();
            while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofJump)) {
                update.addAll(parseStatement(ctx, pos));
            }
            if (!(ctx.ops.get(pos[0]) instanceof KofJump kj) || !kj.target().equals(startLabel)) {
                throw new IllegalStateException("KofJS: for-loop expected Jump(start)");
            }
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel end && end.label().equals(cj2.falseLabel()))) {
                throw new IllegalStateException("KofJS: for-loop expected Label(end)");
            }
            pos[0]++;
            return new JsIr.JsFor(List.of(), condition, update, body);
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump kj
                && kj.target().equals(startLabel)) {
            pos[0]++;
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel end && end.label().equals(cj2.falseLabel()))) {
            throw new IllegalStateException("KofJS: loop expected Label(end)");
        }
        pos[0]++;
        return new JsIr.JsWhile(condition, body, false);
    }

    /**
     * while (true): the optimizer folds the literal-true condition into a
     * direct jump, leaving [Label(start), body..., Jump(start), Label(end)].
     */
    private JsIr.JsStatement parseTrueLoop(MethodCtx ctx, int[] pos, LabelId startLabel) {
        LabelId endLabel = null;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofJump kj && kj.target().equals(startLabel)
                    && i + 1 < ctx.ops.size()
                    && ctx.ops.get(i + 1) instanceof KofLabel kl) {
                endLabel = kl.label();
            }
        }
        if (endLabel == null) {
            throw new IllegalStateException("KofJS: true-loop end label not found");
        }
        ctx.loops.add(new LoopCtx(startLabel, startLabel, endLabel));
        List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(startLabel), new ArrayList<>());
        ctx.loops.remove(ctx.loops.size() - 1);
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump kj
                && kj.target().equals(startLabel)) {
            pos[0]++;
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl
                && kl.label().equals(endLabel)) {
            pos[0]++;
        }
        return new JsIr.JsWhile(new JsIr.JsNumber("1"), body, false);
    }

    private JsIr.JsStatement parseDoWhile(MethodCtx ctx, int[] pos, LabelId startLabel,
                                          KofConditionalJump loopJump) {
        ctx.loops.add(new LoopCtx(startLabel, startLabel, loopJump.falseLabel()));
        List<JsIr.JsStatement> body = new ArrayList<>();
        while (true) {
            if (pos[0] >= ctx.ops.size()) {
                throw new IllegalStateException("KofJS: do-while condition not found");
            }
            if (isDoWhileConditionAhead(ctx, pos, startLabel)) {
                break;
            }
            body.addAll(parseStatement(ctx, pos));
        }
        ctx.loops.remove(ctx.loops.size() - 1);
        List<Object> condStack = new ArrayList<>();
        List<JsIr.JsExpression> condPreamble = new ArrayList<>();
        while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofConditionalJump)) {
            if (!isExpressionOp(ctx.ops.get(pos[0]))) {
                throw new IllegalStateException("KofJS: unexpected op in do-while condition: " + ctx.ops.get(pos[0]));
            }
            consumeExpressionOp(ctx, pos, condStack, condPreamble);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj)) {
            throw new IllegalStateException("KofJS: do-while condition not terminated");
        }
        pos[0]++;
        JsIr.JsExpression right = pop(condStack);
        JsIr.JsExpression left = pop(condStack);
        JsIr.JsExpression condition = comparisonExpr(cj.comparison(), left, right);
        while (!condStack.isEmpty()) {
            condition = new JsIr.JsSequence(List.of(pop(condStack)), condition);
        }
        if (!condPreamble.isEmpty()) {
            condition = new JsIr.JsSequence(condPreamble, condition);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel end && end.label().equals(loopJump.falseLabel()))) {
            throw new IllegalStateException("KofJS: do-while expected Label(end)");
        }
        pos[0]++;
        return new JsIr.JsWhile(condition, body, true);
    }

    private boolean isDoWhileConditionAhead(MethodCtx ctx, int[] pos, LabelId startLabel) {
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofConditionalJump cj) {
                return cj.trueLabel().equals(startLabel);
            }
            // Statement boundaries end the body: a store (e.g. the body's
            // last assignment), a label, a jump or a return.
            if (op instanceof KofStoreLocal || op instanceof KofLabel
                    || op instanceof KofJump || op instanceof KofReturn
                    || op instanceof KofReturnVoid || op instanceof KofThrow) {
                return false;
            }
            if (!isExpressionOp(op)) {
                return false;
            }
        }
        return false;
    }

    // ── Try statement ───────────────────────────────────────────────

    private JsIr.JsStatement parseTryStatement(MethodCtx ctx, int[] pos) {
        KofTryStart ts = (KofTryStart) ctx.ops.get(pos[0]);
        pos[0]++;
        List<JsIr.JsStatement> tryBody = parseStatements(ctx, pos, Set.of(ts.endLabel()), new ArrayList<>());
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel tryEnd
                && tryEnd.label().equals(ts.endLabel())) {
            // The end label may already have been consumed as a region exit
            // (e.g. when the try body ends with throw: the trailing jump is
            // unreachable and the optimizer drops it).
            pos[0]++;
        }
        List<JsIr.JsCatchClause> catches = new ArrayList<>();
        while (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofCatchStart cs) {
            if ("Throwable".equals(cs.exceptionType())) {
                // catch-all + rethrow emulates finally; JS finally is native.
                pos[0]++;
                if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump) {
                    pos[0]++;
                }
                break;
            }
            pos[0]++;
            String param = localName(ctx, cs.localIndex());
            List<JsIr.JsStatement> catchBody = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
            catches.add(new JsIr.JsCatchClause(param, catchBody));
        }
        if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofTryEnd)) {
            throw new IllegalStateException("KofJS: try expected KofTryEnd");
        }
        pos[0]++;
        List<JsIr.JsStatement> finallyBody = List.of();
        // o label do finally é uma label nova da região do try — nunca um
        // label do loop (ex.: o destino do catch no fim do try dentro de um
        // for tem o continue label como próximo — não é um finally)
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel finallyStart
                && !ctx.isLoopLabel(finallyStart.label())) {
            pos[0]++;
            List<LabelId> exits = new ArrayList<>();
            finallyBody = parseStatements(ctx, pos, Set.of(), exits);
            // skip the rethrow machinery: Label(rethrow) ... Label(done)
            LabelId done = exits.isEmpty() ? null : exits.get(exits.size() - 1);
            if (done != null) {
                while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofLabel kl
                        && kl.label().equals(done))) {
                    pos[0]++;
                }
                if (pos[0] < ctx.ops.size()) pos[0]++;
            } else if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
                // no-finally: the trailing empty label (done) ends the try
                pos[0]++;
            }
        }
        return new JsIr.JsTry(tryBody, catches, finallyBody);
    }

    // ── Switch statement ────────────────────────────────────────────

    private JsIr.JsStatement parseSwitchStatement(MethodCtx ctx, int[] pos) {
        // Detect pattern switch: immediate next after load #switch is KofInstanceOf
        boolean hasPattern = false;
        if (pos[0] + 1 < ctx.ops.size() && ctx.ops.get(pos[0] + 1) instanceof KofInstanceOf) {
            hasPattern = true;
        }
        if (hasPattern) {
            return parsePatternSwitch(ctx, pos);
        }
        // [load #switch, <caseValue>, SUB, load 0, CJump(EQ, body, next)] *
        // followed by: [Label(body0), stmts, Jump(end)] * [Label(default), stmts, Label(end)]
        List<JsIr.JsExpression> caseValues = new ArrayList<>();
        List<LabelId> bodyLabels = new ArrayList<>();
        LabelId defaultLabel = null;
        LabelId endLabel = null;
        String subjectName = null;
        if (!(ctx.ops.get(pos[0]) instanceof KofLoadLocal ll
                && "#switch".equals(ctx.rawLocalNames.get(ll.index())))) {
            throw new IllegalStateException("KofJS: switch subject not found");
        }
        while (true) {
            if (!(ctx.ops.get(pos[0]) instanceof KofLoadLocal l2
                    && "#switch".equals(ctx.rawLocalNames.get(l2.index())))) {
                break;
            }
            pos[0]++;
            if (subjectName == null) {
                subjectName = localName(ctx, l2.index());
            }
            List<Object> stack = new ArrayList<>();
            stack.add(new JsIr.JsIdentifier(subjectName));
            boolean stringEq = false;
            while (true) {
                KofOperation op = ctx.ops.get(pos[0]);
                if (op instanceof KofBinary kb && kb.op() == KofBinaryOp.SUB && stack.size() == 2) {
                    pos[0]++;
                    break;
                }
                // bug 4: switch de String usa kof_string_equals em vez de SUB
                // (String - String gerava bytecode inválido no JVM). O call é
                // pulado aqui: no JS o `switch` já compara strings por valor
                // (===), então o caseValue coletado é o literal.
                if (op instanceof KofCall kc && "kof_string_equals".equals(kc.methodName())
                        && stack.size() == 2) {
                    stringEq = true;
                    pos[0]++;
                    break;
                }
                if (!isExpressionOp(op)) {
                    throw new IllegalStateException("KofJS: unexpected op in switch case: " + op);
                }
                consumeExpressionOp(ctx, pos, stack, new ArrayList<>());
            }
            JsIr.JsExpression caseValue = pop(stack);
            pop(stack);
            if (!(ctx.ops.get(pos[0]) instanceof KofLoadLiteral zero
                    && zero.value() instanceof Integer i && i == 0)) {
                throw new IllegalStateException("KofJS: switch case expected 0");
            }
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj
                    && (stringEq
                        ? cj.comparison() == KofComparison.NE
                        : cj.comparison() == KofComparison.EQ))) {
                throw new IllegalStateException("KofJS: switch case expected CJump("
                        + (stringEq ? "NE" : "EQ") + ")");
            }
            pos[0]++;
            caseValues.add(caseValue);
            bodyLabels.add(cj.trueLabel());
            defaultLabel = cj.falseLabel();
            endLabel = cj.falseLabel();
            if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel
                    && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLoadLocal next
                    && "#switch".equals(ctx.rawLocalNames.get(next.index()))) {
                pos[0]++;
                continue;
            }
            break;
        }
        if (subjectName == null) {
            throw new IllegalStateException("KofJS: switch subject not found");
        }
        List<JsIr.JsSwitchCase> fullCases = new ArrayList<>();
        for (LabelId bodyLabel : bodyLabels) {
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofLabel kl)
                    || !kl.label().equals(bodyLabel)) {
                throw new IllegalStateException("KofJS: switch body label missing");
            }
            pos[0]++;
            List<LabelId> exits = new ArrayList<>();
            List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(), exits);
            if (endLabel == null && !exits.isEmpty()) {
                endLabel = exits.get(exits.size() - 1);
            }
            fullCases.add(new JsIr.JsSwitchCase(caseValues.get(fullCases.size()), body));
        }
        List<JsIr.JsStatement> defaultCase = List.of();
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel dl
                && dl.label().equals(defaultLabel)) {
            pos[0]++;
            defaultCase = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
            // Label(end) — end of the switch
            pos[0]++;
        }
        JsIr.JsExpression subject = new JsIr.JsIdentifier(subjectName);
        return new JsIr.JsSwitch(subject, fullCases, defaultCase);
    }

    private JsIr.JsStatement parsePatternSwitch(MethodCtx ctx, int[] pos) {
        // Pattern switch lowering in CompilerDriver:
        //   load #switch; instanceof T; 0; CJ EQ nextTest, body
        //   ... (for each case, preceded by Label nextTest unless first)
        //   Label default; (default body); Jump end
        //   Label body0; load #switch; checkcast T; store var; body0 stmts; Jump end
        //   ...
        //   Label end
        if (!(ctx.ops.get(pos[0]) instanceof KofLoadLocal ll
                && "#switch".equals(ctx.rawLocalNames.get(ll.index())))) {
            throw new IllegalStateException("KofJS: pattern switch subject not found");
        }
        String subjectName = localName(ctx, ll.index());
        List<JsIr.JsExpression> conditions = new ArrayList<>();
        List<LabelId> bodyLabels = new ArrayList<>();
        LabelId defaultLabel = null;
        LabelId endLabel = null;

        // Collect test section
        while (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLoadLocal cur
                && "#switch".equals(ctx.rawLocalNames.get(cur.index()))) {
            pos[0]++; // consume load #switch
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofInstanceOf io)) {
                // Not a pattern case (should not happen for pure pattern switches)
                break;
            }
            // Build JS condition directly from KofInstanceOf type
            JsIr.JsExpression cond;
            if (BuiltinTypes.isString(io.type())) {
                cond = new JsIr.JsBinary(new JsIr.JsUnary("typeof", new JsIr.JsIdentifier(subjectName)), "===", new JsIr.JsString("string"));
            } else if (io.type() instanceof Type.PrimitiveType pt) {
                String cn = Type.canonicalPrimitiveName(pt.name());
                if ("int".equals(cn) || "long".equals(cn) || "float".equals(cn) || "double".equals(cn) || "byte".equals(cn) || "short".equals(cn) || "char".equals(cn)) {
                    cond = new JsIr.JsBinary(new JsIr.JsUnary("typeof", new JsIr.JsIdentifier(subjectName)), "===", new JsIr.JsString("number"));
                } else if ("bool".equals(cn) || "boolean".equals(cn)) {
                    cond = new JsIr.JsBinary(new JsIr.JsUnary("typeof", new JsIr.JsIdentifier(subjectName)), "===", new JsIr.JsString("boolean"));
                } else {
                    cond = new JsIr.JsInstanceOf(new JsIr.JsIdentifier(subjectName), jsClassName(ownerInternalName(io.type())));
                }
            } else {
                cond = new JsIr.JsInstanceOf(new JsIr.JsIdentifier(subjectName), jsClassName(ownerInternalName(io.type())));
            }
            conditions.add(cond);
            pos[0]++; // consume instanceof
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofLoadLiteral zero
                    && zero.value() instanceof Integer i && i == 0)) {
                throw new IllegalStateException("KofJS: pattern switch expected 0");
            }
            pos[0]++; // consume 0
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj
                    && cj.comparison() == KofComparison.EQ)) {
                throw new IllegalStateException("KofJS: pattern switch expected CJump EQ");
            }
            bodyLabels.add(cj.falseLabel());
            defaultLabel = cj.trueLabel();
            pos[0]++; // consume CJ
            // If next op is Label for next test, consume it and continue
            if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel lbl
                    && lbl.label().equals(cj.trueLabel())) {
                // Peek ahead: is next after label a load #switch?
                if (pos[0] + 1 < ctx.ops.size() && ctx.ops.get(pos[0] + 1) instanceof KofLoadLocal nxt
                        && "#switch".equals(ctx.rawLocalNames.get(nxt.index()))) {
                    pos[0]++; // consume nextTest label
                    continue;
                } else {
                    // No more pattern cases, test section ends; pos is at default label
                    break;
                }
            } else {
                break;
            }
        }
        if (bodyLabels.isEmpty()) {
            throw new IllegalStateException("KofJS: pattern switch no bodies");
        }
        // Now pos should be at Label defaultLabel
        // Parse default body to find endLabel
        List<JsIr.JsStatement> defaultCase = List.of();
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel dl
                && dl.label().equals(defaultLabel)) {
            pos[0]++; // consume default label
            // Default body runs until Jump end
            List<LabelId> exits = new ArrayList<>();
            // Parse statements until we hit a Jump; the Jump's target is endLabel
            // We need to detect Jump explicitly
            List<JsIr.JsStatement> defStmts = new ArrayList<>();
            while (pos[0] < ctx.ops.size()) {
                if (ctx.ops.get(pos[0]) instanceof KofJump j) {
                    endLabel = j.target();
                    pos[0]++; // consume Jump end
                    break;
                }
                if (ctx.ops.get(pos[0]) instanceof KofLabel) {
                    // This would be first body label - no default body? then endLabel is this label?
                    break;
                }
                // For default with actual statements, use parseStatements chunk
                // Simpler: parse via parseStatements with end detection
                // But we already are in a loop; use parseStatement
                if (ctx.ops.get(pos[0]) instanceof KofLabel cl && bodyLabels.contains(cl.label())) {
                    break;
                }
                List<JsIr.JsStatement> chunk = parseStatement(ctx, pos);
                defStmts.addAll(chunk);
                // After chunk, if next is Jump, handle
                if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump j2) {
                    endLabel = j2.target();
                    pos[0]++;
                    break;
                }
            }
            defaultCase = defStmts;
            // If default was empty, the Jump we just consumed is the one after defaultLabel
            // If we broke without consuming Jump because next is body label, then default was empty and Jump was already consumed?
            // For empty default (no default body), the IR is Label default (= end) then Jump end - but default==end, so label is end
            // Handle empty default case: if we didn't capture endLabel yet, scan for it
            if (endLabel == null && pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel lbl2) {
                // Might be end label already
                // Need to find the Jump's target from earlier; it should have been the Jump after default
                // If default is empty, the label default is also end, and the Jump after default is Jump end (self)
                // We'll find end by looking at bodies' Jump targets
            }
        }
        // If endLabel still null (no default body), find it from first body's Jump or from default's Jump
        if (endLabel == null) {
            // Scan ahead for first Jump that is not a loop jump
            for (int i = pos[0]; i < ctx.ops.size(); i++) {
                if (ctx.ops.get(i) instanceof KofJump j) {
                    endLabel = j.target();
                    break;
                }
            }
        }
        // Parse each pattern body
        List<List<JsIr.JsStatement>> bodies = new ArrayList<>();
        for (int bi = 0; bi < bodyLabels.size(); bi++) {
            LabelId bodyLabel = bodyLabels.get(bi);
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofLabel kl)
                    || !kl.label().equals(bodyLabel)) {
                throw new IllegalStateException("KofJS: pattern switch body label missing expected " + bodyLabel + " got " + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]) : "EOF"));
            }
            pos[0]++; // consume body label
            // Body starts with load #switch; checkcast; store var  (if pattern var exists)
            // Let parseStatements handle it, but we need to stop at Jump end
            List<JsIr.JsStatement> bodyStmts = new ArrayList<>();
            while (pos[0] < ctx.ops.size()) {
                if (ctx.ops.get(pos[0]) instanceof KofJump j && j.target().equals(endLabel)) {
                    pos[0]++; // consume Jump end
                    break;
                }
                if (ctx.ops.get(pos[0]) instanceof KofLabel) {
                    // Next body label - should not happen before Jump
                    break;
                }
                List<JsIr.JsStatement> chunk = parseStatement(ctx, pos);
                bodyStmts.addAll(chunk);
            }
            bodies.add(bodyStmts);
        }
        // Consume final end label if present
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel el
                && el.label().equals(endLabel)) {
            pos[0]++;
        }
        // Build nested if-else from the end
        JsIr.JsStatement result = defaultCase.isEmpty() ? null : new JsIr.JsBlock(defaultCase);
        for (int i = conditions.size() - 1; i >= 0; i--) {
            JsIr.JsExpression cond = conditions.get(i);
            List<JsIr.JsStatement> thenBranch = bodies.get(i);
            List<JsIr.JsStatement> elseBranch = result == null ? List.of() : List.of(result);
            result = new JsIr.JsIf(cond, thenBranch, elseBranch);
        }
        return result != null ? result : new JsIr.JsBlock(List.of());
    }

    // ── Expression statements ───────────────────────────────────────

    /**
     * Thrown when a void call (or a constructor super call) completes the
     * current statement.
     */
    private static final class StatementEnd extends RuntimeException {
        final JsIr.JsExpression call;

        StatementEnd(JsIr.JsExpression call) {
            this.call = call;
        }
    }

    private List<JsIr.JsStatement> parseExpressionStatement(MethodCtx ctx, int[] pos) {
        List<Object> stack = new ArrayList<>();
        List<JsIr.JsStatement> preamble = new ArrayList<>();
        List<JsIr.JsExpression> preambleExprs = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof KofStoreLocal sl) {
                pos[0]++;
                if (stack.isEmpty()) {
                    throw new IllegalStateException("KofJS: store with empty stack at " + sl
                            + "\nnext=" + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]) : "eof")
                            + "\nops=" + ctx.ops.stream().map(Object::toString).reduce("", (a, b) -> a + "\n" + b));
                }
                JsIr.JsStatement stmt = storeLocalStatement(ctx, sl, pop(stack));
                boolean switchTemp = "#switch".equals(ctx.rawLocalNames.get(sl.index()));
                if (stack.isEmpty() && (!isCompilerTemp(ctx, sl.index()) || switchTemp)) {
                    return finishExpressionStatement(preamble, preambleExprs, stmt);
                }
                // mid-expression store (++/-- temps, compiler temporaries)
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofStoreField sf) {
                pos[0]++;
                JsIr.JsExpression value = pop(stack);
                JsIr.JsExpression receiver = pop(stack);
                JsIr.JsStatement stmt = new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(receiver,
                                ctx.recordClass ? "_" + sanitizeName(sf.name()) : sanitizeName(sf.name())), "=", value));
                if (stack.isEmpty()) {
                    return finishExpressionStatement(preamble, preambleExprs, stmt);
                }
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofPutStatic ps) {
                pos[0]++;
                JsIr.JsExpression value = pop(stack);
                String owner = jsClassName(ownerInternalName(ps.ownerType()));
                return finishExpressionStatement(preamble, new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(new JsIr.JsIdentifier(owner), sanitizeName(ps.name())), "=", value)));
            }
            if (op instanceof KofArrayStore as) {
                if (stack.isEmpty()) {
                    throw new IllegalStateException("KofJS: arraystore empty stack; next="
                            + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]) : "eof")
                            + "\nops=" + ctx.ops.stream().map(Object::toString).reduce("", (a, b) -> a + "\n" + b));
                }
                pos[0]++;
                JsIr.JsExpression value = pop(stack);
                JsIr.JsExpression index = pop(stack);
                JsIr.JsExpression array = pop(stack);
                JsIr.JsStatement stmt = new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsIndex(array, index), "=", value));
                if (stack.isEmpty() && !isIncTmpLoadAhead(ctx, pos)) {
                    return finishExpressionStatement(preamble, preambleExprs, stmt);
                }
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofPop) {
                pos[0]++;
                JsIr.JsExpression dropped = null;
                if (!stack.isEmpty()) {
                    dropped = pop(stack);
                }
                stack.clear();
                if (dropped instanceof JsIr.JsCall || dropped instanceof JsIr.JsSequence
                        || dropped instanceof JsIr.JsAwait) {
                    // Side-effecting call, sequence, or await used as statement
                    // (e.g. `await r;` / `await spawn tick();`) must survive POP.
                    return finishExpressionStatement(preamble, preambleExprs,
                            new JsIr.JsExprStmt(dropped));
                }
                return finishExpressionStatement(preamble, preambleExprs, null);
            }
            if (op instanceof KofReturn kr) {
                pos[0]++;
                if (Type.isVoid(kr.returnType()) && !stack.isEmpty()) {
                    // A void call's result is still a side-effecting
                    // expression (default-parameter wrapper returning a
                    // void function call): return it so it executes.
                    return finishExpressionStatement(preamble, preambleExprs,
                            new JsIr.JsReturn(pop(stack)));
                }
                if (Type.isVoid(kr.returnType())) {
                    stack.clear();
                    return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsReturn(null));
                }
                return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsReturn(pop(stack)));
            }
            if (op instanceof KofThrow) {
                pos[0]++;
                return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsThrow(pop(stack)));
            }
            if (op instanceof KofConditionalJump cj && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLabel kl
                    && kl.label().equals(cj.trueLabel())) {
                // if-statement OR if-expression (var x = if (...) ... else ...)
                pos[0]++;
                JsIr.JsExpression right = pop(stack);
                JsIr.JsExpression left = pop(stack);
                JsIr.JsExpression condition = comparisonExpr(cj.comparison(), left, right);
                while (!stack.isEmpty()) {
                    condition = new JsIr.JsSequence(List.of(pop(stack)), condition);
                }
                JsIr.JsExpression ifExpr = tryParseIfExpr(ctx, pos, cj, condition);
                if (ifExpr != null) {
                    stack.add(ifExpr);
                    continue;
                }
                return List.of(parseIfBody(ctx, pos, cj, condition, stack));
            }
            if (!isExpressionOp(op)) {
                // statement boundary: wrap any leftover stack (listOf(...) chains,
                // increment temps) and finish the statement
                if (!stack.isEmpty()) {
                    JsIr.JsExpression wrapped = wrapStack(stack);
                    stack.clear();
                    return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsExprStmt(wrapped));
                }
                if (!preamble.isEmpty() || !preambleExprs.isEmpty()) {
                    return finishExpressionStatement(preamble, preambleExprs, null);
                }
                throw new IllegalStateException("KofJS: unexpected op in expression statement: " + op);
            }
            try {
                consumeExpressionOp(ctx, pos, stack, preambleExprs);
            } catch (StatementEnd se) {
                if (!stack.isEmpty()) {
                    JsIr.JsExpression wrapped = wrapStack(stack);
                    stack.clear();
                    return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsExprStmt(
                            new JsIr.JsSequence(List.of(wrapped), se.call)));
                }
                return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsExprStmt(se.call));
            }
        }
        if (!stack.isEmpty()) {
            return finishExpressionStatement(preamble, preambleExprs,
                    new JsIr.JsExprStmt(wrapStack(stack)));
        }
        if (!preambleExprs.isEmpty()) {
            return finishExpressionStatement(preamble, preambleExprs, null);
        }
        throw new IllegalStateException("KofJS: unterminated expression statement");
    }

    private List<JsIr.JsStatement> finishExpressionStatement(List<JsIr.JsStatement> preamble,
                                                          JsIr.JsStatement finalStmt) {
        return finishExpressionStatement(preamble, List.of(), finalStmt);
    }

    private List<JsIr.JsStatement> finishExpressionStatement(List<JsIr.JsStatement> preamble,
                                                          List<JsIr.JsExpression> preambleExprs,
                                                          JsIr.JsStatement finalStmt) {
        List<JsIr.JsStatement> all = new ArrayList<>();
        for (JsIr.JsExpression pe : preambleExprs) {
            all.add(new JsIr.JsExprStmt(pe));
        }
        all.addAll(preamble);
        if (finalStmt != null) {
            all.add(finalStmt);
        }
        return all;
    }

    /**
     * True when the next op reloads the ++/-- temp (#inc), meaning the array
     * store is part of an increment expression, not the end of a statement.
     */
    private boolean isIncTmpLoadAhead(MethodCtx ctx, int[] pos) {
        if (pos[0] >= ctx.ops.size()) return false;
        KofOperation op = ctx.ops.get(pos[0]);
        if (op instanceof KofLoadLocal ll) {
            String raw = ctx.rawLocalNames.get(ll.index());
            return raw != null && raw.startsWith("#");
        }
        return false;
    }

    private boolean isCompilerTemp(MethodCtx ctx, int index) {
        String raw = ctx.rawLocalNames.get(index);
        return raw != null && raw.startsWith("#");
    }

    private JsIr.JsExpression wrapStack(List<Object> stack) {
        if (stack.size() == 1) {
            return pop(stack);
        }
        List<JsIr.JsExpression> exprs = new ArrayList<>();
        for (int i = 0; i < stack.size() - 1; i++) {
            Object o = stack.get(i);
            if (o instanceof JsIr.JsIdentifier id && "$kofOut".equals(id.name())) {
                continue;
            }
            exprs.add(o instanceof NewPending np
                    ? new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), List.of())
                    : (JsIr.JsExpression) o);
        }
        return new JsIr.JsSequence(exprs, pop(stack));
    }

    private JsIr.JsStatement storeLocalStatement(MethodCtx ctx, KofStoreLocal sl, JsIr.JsExpression value) {
        String name = localName(ctx, sl.index());
        if ("this".equals(name)) {
            throw new IllegalStateException("KofJS: cannot store to 'this'");
        }
        if (ctx.declared.add(sl.index())) {
            return new JsIr.JsVarDecl(name, value, false);
        }
        return new JsIr.JsAssign(name, value);
    }

    private String localName(MethodCtx ctx, int index) {
        String name = ctx.localNames.get(index);
        if (name == null) {
            throw new IllegalStateException("KofJS: unknown local slot " + index);
        }
        return name;
    }

    // ── Expression lowering ─────────────────────────────────────────

    private boolean isExpressionOp(KofOperation op) {
        return op instanceof KofLoadLiteral || op instanceof KofLoadLocal
                || op instanceof KofLoadField || op instanceof KofGetStatic
                || op instanceof KofBinary || op instanceof KofUnary
                || op instanceof KofCall || op instanceof KofNewObject
                || op instanceof KofDup || op instanceof KofDupX1 || op instanceof KofDupX2 || op instanceof KofNewArray
                || op instanceof KofArrayLoad || op instanceof KofArrayLength
                || op instanceof KofInstanceOf || op instanceof KofCheckCast
                || op instanceof KofStoreLocal;
    }

    private void consumeExpressionOp(MethodCtx ctx, int[] pos, List<Object> stack,
                                     List<JsIr.JsExpression> preambleExprs) {
        KofOperation op = ctx.ops.get(pos[0]);
        pos[0]++;
        if (op instanceof KofLoadLiteral lit) {
            stack.add(literalExpr(lit));
        } else if (op instanceof KofLoadLocal ll) {
            stack.add(new JsIr.JsIdentifier(localName(ctx, ll.index())));
        } else if (op instanceof KofStoreLocal sl) {
            // Mid-expression store (sound optimizer round trip: dup; store).
            // The value stays on the stack as an assignment expression.
            JsIr.JsExpression value = pop(stack);
            stack.add(new JsIr.JsAssignExpr(localName(ctx, sl.index()), value));
        } else if (op instanceof KofLoadField lf) {
            JsIr.JsExpression receiver = pop(stack);
            boolean isRecordField = false;
            if (lf.ownerType() instanceof Type.ClassType ct) {
                String ownerInternal = JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
                String ownerSimple = ct.name();
                if (recordClassNames.contains(ownerInternal) || recordClassNames.contains(ct.name())
                        || recordClassNames.contains(ownerSimple) || ctx.recordClass) {
                    isRecordField = true;
                }
            }
            stack.add(new JsIr.JsMember(receiver, isRecordField ? "_" + sanitizeName(lf.name()) : sanitizeName(lf.name())));
        } else if (op instanceof KofGetStatic gs) {
            if ("java.lang".equals(classPackage(gs.ownerType())) && "System".equals(className(gs.ownerType()))
                    && "out".equals(gs.name())) {
                stack.add(new JsIr.JsIdentifier("$kofOut"));
            } else {
                String owner = jsClassName(ownerInternalName(gs.ownerType()));
                stack.add(new JsIr.JsMember(new JsIr.JsIdentifier(owner), sanitizeName(gs.name())));
            }
        } else if (op instanceof KofBinary kb) {
            JsIr.JsExpression right = pop(stack);
            JsIr.JsExpression left = pop(stack);
            stack.add(binaryExpr(kb, left, right));
        } else if (op instanceof KofUnary ku) {
            JsIr.JsExpression operand = pop(stack);
            stack.add(unaryExpr(ku, operand));
        } else if (op instanceof KofNewObject no) {
            stack.add(new NewPending(jsClassName(ownerInternalName(no.type()))));
        } else if (op instanceof KofDup) {
            if (!stack.isEmpty() && stack.get(stack.size() - 1) instanceof NewPending) {
                stack.add(new DupMarker());
                return;
            }
            JsIr.JsExpression top = pop(stack);
            if (top instanceof JsIr.JsNumber || top instanceof JsIr.JsString
                    || top instanceof JsIr.JsNull) {
                stack.add(top);
                stack.add(top);
                return;
            }
            // Materialize the copy as a preamble assignment: `t = v` must
            // execute before any later store that consumes the temp.
            String temp = ctx.freshTemp();
            preambleExprs.add(new JsIr.JsAssignExpr(temp, top));
            stack.add(new JsIr.JsIdentifier(temp));
            stack.add(new JsIr.JsIdentifier(temp));
        } else if (op instanceof KofDupX1) {
            JsIr.JsExpression top = pop(stack);
            JsIr.JsExpression below = pop(stack);
            if (top instanceof JsIr.JsNumber || top instanceof JsIr.JsString
                    || top instanceof JsIr.JsNull) {
                stack.add(top);
                stack.add(below);
                stack.add(top);
                return;
            }
            String temp = ctx.freshTemp();
            stack.add(new JsIr.JsSequence(
                    List.of(new JsIr.JsAssignExpr(temp, top)), new JsIr.JsIdentifier(temp)));
            stack.add(below);
            stack.add(new JsIr.JsIdentifier(temp));
        } else if (op instanceof KofDupX2) {
            JsIr.JsExpression top = pop(stack);
            JsIr.JsExpression middle = pop(stack);
            JsIr.JsExpression bottom = pop(stack);
            if (top instanceof JsIr.JsNumber || top instanceof JsIr.JsString
                    || top instanceof JsIr.JsNull) {
                stack.add(top);
                stack.add(bottom);
                stack.add(middle);
                stack.add(top);
                return;
            }
            String temp = ctx.freshTemp();
            stack.add(new JsIr.JsSequence(
                    List.of(new JsIr.JsAssignExpr(temp, top)), new JsIr.JsIdentifier(temp)));
            stack.add(bottom);
            stack.add(middle);
            stack.add(new JsIr.JsIdentifier(temp));
        } else if (op instanceof KofNewArray na) {
            JsIr.JsExpression size = pop(stack);
            stack.add(new JsIr.JsArray(size, arrayFill(na.elementType())));
        } else if (op instanceof KofArrayLoad al) {
            JsIr.JsExpression index = pop(stack);
            JsIr.JsExpression array = pop(stack);
            stack.add(new JsIr.JsIndex(array, index));
        } else if (op instanceof KofArrayLength) {
            JsIr.JsExpression array = pop(stack);
            stack.add(new JsIr.JsMember(array, "length"));
        } else if (op instanceof KofCheckCast) {
            // JavaScript has no runtime casts; Kof semantics are enforced by
            // the type checker at compile time.
        } else if (op instanceof KofInstanceOf io) {
            JsIr.JsExpression operand = pop(stack);
            if (BuiltinTypes.isString(io.type())) {
                stack.add(new JsIr.JsConditional(
                        new JsIr.JsBinary(new JsIr.JsUnary("typeof", operand), "===", new JsIr.JsString("string")),
                        new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
            } else if (io.type() instanceof Type.PrimitiveType pt) {
                String cn = Type.canonicalPrimitiveName(pt.name());
                if ("int".equals(cn) || "long".equals(cn) || "float".equals(cn) || "double".equals(cn) || "byte".equals(cn) || "short".equals(cn) || "char".equals(cn)) {
                    stack.add(new JsIr.JsConditional(
                            new JsIr.JsBinary(new JsIr.JsUnary("typeof", operand), "===", new JsIr.JsString("number")),
                            new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
                } else if ("bool".equals(cn) || "boolean".equals(cn)) {
                    stack.add(new JsIr.JsConditional(
                            new JsIr.JsBinary(new JsIr.JsUnary("typeof", operand), "===", new JsIr.JsString("boolean")),
                            new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
                } else {
                    stack.add(new JsIr.JsConditional(
                            new JsIr.JsInstanceOf(operand, jsClassName(ownerInternalName(io.type()))),
                            new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
                }
            } else {
                stack.add(new JsIr.JsConditional(
                        new JsIr.JsInstanceOf(operand, jsClassName(ownerInternalName(io.type()))),
                        new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
            }
        } else if (op instanceof KofConditionalJump cj) {
            // if-expression: (cond ? then : else)
            JsIr.JsExpression right = pop(stack);
            JsIr.JsExpression left = pop(stack);
            JsIr.JsExpression condition = comparisonExpr(cj.comparison(), left, right);
            JsIr.JsExpression ifExpr = tryParseIfExpr(ctx, pos, cj, condition);
            if (ifExpr == null) {
                throw new IllegalStateException("KofJS: malformed if-expression");
            }
            stack.add(ifExpr);
        } else if (op instanceof KofCall kc) {
            handleCall(ctx, stack, preambleExprs, kc);
        } else {
            throw new IllegalStateException("KofJS: unhandled IR op " + op);
        }
    }

    /**
     * Parses a self-contained expression fragment (if-expr branches): expression
     * ops until the next statement-level op. The stack must hold exactly one
     * value when finished.
     */
    private JsIr.JsExpression parseExpressionFragment(MethodCtx ctx, int[] pos) {
        List<Object> stack = new ArrayList<>();
        List<JsIr.JsExpression> preambleExprs = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof KofJump || op instanceof KofLabel || op instanceof KofPop
                    || (op instanceof KofStoreLocal && stack.isEmpty()) || op instanceof KofStoreField
                    || op instanceof KofPutStatic || op instanceof KofArrayStore
                    || op instanceof KofReturn || op instanceof KofReturnVoid
                    || op instanceof KofThrow || op instanceof KofTryStart
                    || op instanceof KofCatchStart) {
                break;
            }
            // Mid-expression store (switch-expression pattern binding, nested
            // switch subject): consumeExpressionOp turns it into an assignment
            // expression that stays on the stack; the fragment's final
            // JsSequence renders it as `(v = x, <expr>)`. Slots are
            // pre-declared at the function top, so the binding is a valid var.
            if (op instanceof KofConditionalJump && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLabel kl
                    && kl.label().equals(((KofConditionalJump) op).trueLabel())) {
                // if-expression or a nested if-statement inside a branch
                int saved = pos[0];
                try {
                    consumeExpressionOp(ctx, pos, stack, preambleExprs);
                } catch (RuntimeException e) {
                    pos[0] = saved;
                    break;
                }
                continue;
            }
            if (!isExpressionOp(op)) {
                throw new IllegalStateException("KofJS: unexpected op in expression fragment: " + op);
            }
            consumeExpressionOp(ctx, pos, stack, preambleExprs);
        }
        if (stack.size() != 1) {
            // dup;store round trips leave extra stack entries (a temp
            // sequence and the assignment). They evaluate before the final
            // value, which is the last computed one.
            List<JsIr.JsExpression> pre = new ArrayList<>();
            while (stack.size() > 1) {
                pre.add((JsIr.JsExpression) stack.remove(0));
            }
            JsIr.JsExpression last = pop(stack);
            return new JsIr.JsSequence(pre, last);
        }
        JsIr.JsExpression value = pop(stack);
        if (!preambleExprs.isEmpty()) {
            return new JsIr.JsSequence(preambleExprs, value);
        }
        return value;
    }

    // ── Calls ───────────────────────────────────────────────────────

    private void handleCall(MethodCtx ctx, List<Object> stack,
                                 List<JsIr.JsExpression> preambleExprs, KofCall kc) {
        if (kc.kind() == KofCallKind.CONSTRUCTOR) {
            handleConstructorCall(stack, kc);
            return;
        }
        // kof.web on JS: now lowered as runtime call (was WEB001) — handled via isRuntimeOp/kofWeb* helpers
        if (false && kc.methodName().startsWith("kof_web_")) {
            throw new IllegalStateException("kof.web is not supported on the js target yet (WEB001)");
        }
        boolean hasReceiver = kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE;
        List<JsIr.JsExpression> args = new ArrayList<>();
        for (int i = 0; i < kc.parameterTypes().size(); i++) {
            args.add(pop(stack));
        }
        java.util.Collections.reverse(args);
        JsIr.JsExpression receiver = hasReceiver ? pop(stack) : null;
        if (isPrintCall(kc)) {
            JsIr.JsExpression value = args.get(0);
            String fn = "println".equals(kc.methodName()) ? "kofPrintln" : "kofPrint";
            if ("kofPrint".equals(fn)) {
                registerIoRuntime(fn);
            } else {
                registerRuntime(fn);
            }
            throw new StatementEnd(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of(value)));
        }
        if ("valueOf".equals(kc.methodName()) && kc.kind() == KofCallKind.STATIC) {
            if (BuiltinTypes.isString(kc.ownerType())) {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String"), List.of(args.get(0))));
            } else if (!kc.parameterTypes().isEmpty()
                    && kc.parameterTypes().get(0) instanceof Type.PrimitiveType pt
                    && "bool".equals(Type.canonicalPrimitiveName(pt.name()))) {
                // Boolean.valueOf(Z) — format 0/1 as true/false
                stack.add(new JsIr.JsConditional(args.get(0),
                        new JsIr.JsIdentifier("true"), new JsIr.JsIdentifier("false")));
            } else {
                // boxed valueOf — JS values are already boxed; identity
                stack.add(args.get(0));
            }
            return;
        }
        if (isChannelOp(kc)) {
            handleChannelOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (isListOp(kc)) {
            handleListOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (isMapOp(kc)) {
            handleMapOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (isSetOp(kc)) {
            handleSetOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (isRuntimeOp(kc)) {
            // kof_json_* / kof_io_* / kof_now / kof_box / kof_unbox — checked
            // before string ops: json.encode("...") has a String owner.
            handleRuntimeOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (isStringOp(kc)) {
            handleStringOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (kc.kind() == KofCallKind.FUNCTION) {
            // top-level function call (arity routes default-parameter wrappers)
            finishCall(stack, kc, new JsIr.JsCall(
                    new JsIr.JsIdentifier(jsFunctionName(kc.methodName(), kc.parameterTypes().size())),
                    args));
            return;
        }
        if (kc.kind() == KofCallKind.SUPER) {
            // super.method(args) — JS supports it natively inside class
            // methods; the receiver on the stack is this and is discarded.
            pop(stack);
            finishCall(stack, kc, new JsIr.JsCall(
                    new JsIr.JsMember(new JsIr.JsIdentifier("super"), sanitizeName(kc.methodName())), args));
            return;
        }
        if (kc.kind() == KofCallKind.STATIC) {
            String owner = jsClassName(ownerInternalName(kc.ownerType()));
            finishCall(stack, kc, new JsIr.JsCall(
                    new JsIr.JsMember(new JsIr.JsIdentifier(owner), sanitizeName(kc.methodName())), args));
            return;
        }
        // INSTANCE / INTERFACE — structural dispatch
        String owner = ownerInternalName(kc.ownerType());
        if ("equals".equals(kc.methodName()) && owner != null
                && !ctx.hasClassMethod(owner, "equals")) {
            // Object.equals — reference equality (JVM semantics)
            stack.add(new JsIr.JsBinary(receiver, "===", args.get(0)));
            return;
        }
        finishCall(stack, kc, new JsIr.JsCall(
                new JsIr.JsMember(receiver, sanitizeName(kc.methodName())), args));
    }

    private JsIr.JsExpression maybeAwait(KofCall kc, JsIr.JsExpression call) {
        KofCallKind kind = kc.kind();
        boolean needsAwait = false;
        if (kind == KofCallKind.STATIC || kind == KofCallKind.FUNCTION || kind == KofCallKind.SUPER) {
            needsAwait = asyncMethods.getOrDefault(calleeKeyFromCall(kc), false);
        } else if (kind == KofCallKind.INSTANCE || kind == KofCallKind.INTERFACE) {
            needsAwait = asyncMethodNamesAnywhere.contains(kc.methodName());
        }
        return needsAwait ? new JsIr.JsAwait(call) : call;
    }

    private void finishCall(List<Object> stack, KofCall kc, JsIr.JsExpression call) {
        call = maybeAwait(kc, call);
        if (Type.isVoid(kc.returnType())) {
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private void handleConstructorCall(List<Object> stack, KofCall kc) {
        List<JsIr.JsExpression> args = new ArrayList<>();
        for (int i = 0; i < kc.parameterTypes().size(); i++) {
            if (stack.isEmpty()) break;
            Object top = stack.get(stack.size() - 1);
            if (top instanceof NewPending || top instanceof DupMarker) break;
            args.add(pop(stack));
        }
        java.util.Collections.reverse(args);
        Object top = popRaw(stack);
        if (top instanceof DupMarker) {
            Object newObj = popRaw(stack);
            if (newObj instanceof NewPending np) {
                stack.add(new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), args));
                return;
            }
            throw new IllegalStateException("KofJS: DupMarker without NewPending");
        }
        if (top instanceof NewPending np) {
            stack.add(new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), args));
            return;
        }
        // super(...) constructor call
        throw new StatementEnd(new JsIr.JsCall(new JsIr.JsIdentifier("super"), args));
    }

    private Type listElementType(Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    private String capitalizeUiFn(String name) {
        String rest = name.startsWith("kof_") ? name.substring(4) : name;
        StringBuilder sb = new StringBuilder("kof");
        boolean cap = true;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '_') {
                cap = true;
                continue;
            }
            sb.append(cap ? Character.toUpperCase(c) : c);
            cap = false;
        }
        return sb.toString();
    }

    private boolean isPrintCall(KofCall kc) {
        if (!(kc.ownerType() instanceof Type.ClassType ct)) return false;
        return "java.io".equals(ct.packageName()) && "PrintStream".equals(ct.name())
                && ("println".equals(kc.methodName()) || "print".equals(kc.methodName()));
    }

    // ── Operator lowering ───────────────────────────────────────────

    private boolean isIntFamily(Type type) {
        if (!(type instanceof Type.PrimitiveType pt)) return false;
        return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "int", "byte", "short", "char" -> true;
            default -> false;
        };
    }

    // && / || booleanos → && / || JS (que short-circuitam nativamente);
    // & / | bitwise → & / | (avalia os dois lados). O operador lógico e o
    // bitwise caem no MESMO KofBinaryOp.AND/OR — o operandType (bool vs int)
    // é o que os distingue. Antes: && virava & (bitwise) no JS → sem
    // short-circuit (efeitos colaterais do lado de não deviam ser avaliados).
    private boolean isBoolOperand(Type type) {
        return type instanceof Type.PrimitiveType pt
                && "bool".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    private boolean isLongType(Type type) {
        if (!(type instanceof Type.PrimitiveType pt)) return false;
        return "long".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    private JsIr.JsExpression binaryExpr(KofBinary kb, JsIr.JsExpression left, JsIr.JsExpression right) {
        return switch (kb.op()) {
            case ADD -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "+", right));
            case SUB -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "-", right));
            case MUL -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "*", right));
            case DIV -> {
                if (isIntFamily(kb.operandType())) {
                    yield intWrap(kb.operandType(), new JsIr.JsBinary(left, "/", right));
                }
                if (isLongType(kb.operandType())) {
                    // JS / yields doubles; truncate toward zero like JVM LIDIV
                    yield new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("Math"), "trunc"),
                            List.of(new JsIr.JsBinary(left, "/", right)));
                }
                yield new JsIr.JsBinary(left, "/", right);
            }
            case MOD -> new JsIr.JsBinary(left, "%", right);
            case EQ -> new JsIr.JsBinary(left, "===", right);
            case NE -> new JsIr.JsBinary(left, "!==", right);
            case LT -> new JsIr.JsBinary(left, "<", right);
            case LE -> new JsIr.JsBinary(left, "<=", right);
            case GT -> new JsIr.JsBinary(left, ">", right);
            case GE -> new JsIr.JsBinary(left, ">=", right);
            case AND -> isBoolOperand(kb.operandType())
                    ? new JsIr.JsBinary(left, "&&", right)
                    : new JsIr.JsBinary(left, "&", right);
            case OR -> isBoolOperand(kb.operandType())
                    ? new JsIr.JsBinary(left, "||", right)
                    : new JsIr.JsBinary(left, "|", right);
            case XOR -> new JsIr.JsBinary(left, "^", right);
            case SHL -> new JsIr.JsBinary(left, "<<", right);
            case SHR -> new JsIr.JsBinary(left, ">>", right);
            case USHR -> new JsIr.JsBinary(left, ">>>", right);
        };
    }

    /**
     * Kof Int is a signed 32-bit type; JavaScript numbers are doubles. Wrap
     * int arithmetic with ToInt32 (| 0) to preserve Kof/JVM 32-bit semantics.
     */
    private JsIr.JsExpression intWrap(Type operandType, JsIr.JsExpression inner) {
        if (isIntFamily(operandType)) {
            return new JsIr.JsBinary(inner, "|", new JsIr.JsNumber("0"));
        }
        return inner;
    }

    private JsIr.JsExpression unaryExpr(KofUnary ku, JsIr.JsExpression operand) {
        return switch (ku.op()) {
            case NEG -> new JsIr.JsUnary("-", operand);
            case NOT -> new JsIr.JsConditional(operand, new JsIr.JsNumber("0"), new JsIr.JsNumber("1"));
            case I2L, I2F, I2D, I2C, L2I, L2F, L2D, F2D, D2F -> operand;
            case D2I, F2I, D2L, F2L -> new JsIr.JsCall(new JsIr.JsIdentifier("Math.trunc"),
                    List.of(operand));
        };
    }

    private JsIr.JsExpression literalExpr(KofLoadLiteral lit) {
        if (lit.type() instanceof Type.PrimitiveType pt
                && "bool".equals(Type.canonicalPrimitiveName(pt.name()))) {
            Object v = lit.value();
            return new JsIr.JsIdentifier((v instanceof Integer i && i != 0) ? "true" : "false");
        }
        if (lit.value() instanceof Integer i) return new JsIr.JsNumber(Integer.toString(i));
        if (lit.value() instanceof Long l) return new JsIr.JsNumber(Long.toString(l));
        if (lit.value() instanceof Float f) return new JsIr.JsNumber(Float.toString(f));
        if (lit.value() instanceof Double d) return new JsIr.JsNumber(Double.toString(d));
        if (lit.value() instanceof String s) return new JsIr.JsString(s);
        return new JsIr.JsNull();
    }

    private String literalText(Object value) {
        if (value instanceof Float f) return Float.toString(f);
        if (value instanceof Double d) return Double.toString(d);
        if (value instanceof Boolean b) return b ? "1" : "0";
        if (value instanceof String s) return jsStringLiteral(s);
        return String.valueOf(value);
    }

    private String jsStringLiteral(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private String arrayFill(Type elementType) {
        if (elementType instanceof Type.PrimitiveType) return "0";
        return "null";
    }

    // ── List / String / runtime lowering ────────────────────────────

    private boolean isListOp(KofCall kc) {
        return BuiltinTypes.isList(kc.ownerType()) && kc.methodName().startsWith("kof_list_");
    }

    private boolean isChannelOp(KofCall kc) {
        return BuiltinTypes.isChannel(kc.ownerType()) && kc.methodName().startsWith("kof_channel_");
    }

    private boolean isMapOp(KofCall kc) {
        return BuiltinTypes.isMap(kc.ownerType()) && kc.methodName().startsWith("kof_map_");
    }

    private boolean isSetOp(KofCall kc) {
        return BuiltinTypes.isSet(kc.ownerType()) && kc.methodName().startsWith("kof_set_");
    }

    private void handleChannelOp(MethodCtx ctx, List<Object> stack,
                               List<JsIr.JsExpression> preambleExprs, KofCall kc,
                               JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        // Canais tipados (JS sequencial): FIFO { items: [] } — send push, receive shift.
        String fn = switch (kc.methodName()) {
            case "kof_channel_new" -> "kofChannelNew";
            case "kof_channel_send" -> "kofChannelSend";
            case "kof_channel_receive" -> "kofChannelReceive";
            default -> throw new IllegalStateException("KofJS: unknown channel op " + kc.methodName());
        };
        registerRuntime(fn);
        if ("kof_channel_new".equals(kc.methodName())) {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of()));
            return;
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        callArgs.add(receiver);
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if ("kof_channel_receive".equals(kc.methodName())) {
            call = new JsIr.JsAwait(call);
        }
        if (Type.isVoid(kc.returnType())) {
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private void handleListOp(MethodCtx ctx, List<Object> stack,
                               List<JsIr.JsExpression> preambleExprs, KofCall kc,
                               JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String fn = switch (kc.methodName()) {
            case "kof_list_new" -> "kofListNew";
            case "kof_list_add" -> "kofListAdd";
            case "kof_list_get" -> "kofListGet";
            case "kof_list_set" -> "kofListSet";
            case "kof_list_size" -> "kofListSize";
            case "kof_list_contains" -> "kofListContains";
            case "kof_list_is_empty" -> "kofListIsEmpty";
            case "kof_list_remove" -> "kofListRemove";
            case "kof_list_clear" -> "kofListClear";
            default -> throw new IllegalStateException("KofJS: unknown list op " + kc.methodName());
        };
        registerRuntime(fn);
        if ("kof_list_new".equals(kc.methodName())) {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of()));
            return;
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        callArgs.add(receiver);
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if (Type.isVoid(kc.returnType())) {
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")
                    && !stack.isEmpty() && stack.get(stack.size() - 1) instanceof JsIr.JsSequence seq
                    && seq.value().equals(receiver)) {
                // mid-expression list construction (listOf(...) element append)
                List<JsIr.JsExpression> exprs = new ArrayList<>(seq.expressions());
                exprs.add(call);
                stack.remove(stack.size() - 1);
                stack.add(new JsIr.JsSequence(exprs, seq.value()));
                return;
            }
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")) {
                // The dup'd copy of the list reference stays on the stack for the
                // next append; the append itself must execute before any
                // later operation.
                preambleExprs.add(call);
                return;
            }
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private void handleMapOp(MethodCtx ctx, List<Object> stack,
                              List<JsIr.JsExpression> preambleExprs, KofCall kc,
                              JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String fn = switch (kc.methodName()) {
            case "kof_map_new" -> "kofMapNew";
            case "kof_map_put" -> "kofMapPut";
            case "kof_map_get" -> "kofMapGet";
            case "kof_map_remove" -> "kofMapRemove";
            case "kof_map_contains" -> "kofMapContains";
            case "kof_map_size" -> "kofMapSize";
            case "kof_map_clear" -> "kofMapClear";
            case "kof_map_is_empty" -> "kofMapIsEmpty";
            case "kof_map_keys" -> "kofMapKeys";
            case "kof_map_values" -> "kofMapValues";
            default -> throw new IllegalStateException("KofJS: unknown map op " + kc.methodName());
        };
        registerRuntime(fn);
        if ("kof_map_new".equals(kc.methodName())) {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of()));
            return;
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        callArgs.add(receiver);
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if (Type.isVoid(kc.returnType())) {
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")
                    && !stack.isEmpty() && stack.get(stack.size() - 1) instanceof JsIr.JsSequence seq
                    && seq.value().equals(receiver)) {
                // construção mid-expression (ex.: pares do mapOf): anexa mantendo o valor
                List<JsIr.JsExpression> exprs = new ArrayList<>(seq.expressions());
                exprs.add(call);
                stack.remove(stack.size() - 1);
                stack.add(new JsIr.JsSequence(exprs, seq.value()));
                return;
            }
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")) {
                // a cópia duplicada permanece na pilha para o próximo par
                preambleExprs.add(call);
                return;
            }
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private void handleSetOp(MethodCtx ctx, List<Object> stack,
                              List<JsIr.JsExpression> preambleExprs, KofCall kc,
                              JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String fn = switch (kc.methodName()) {
            case "kof_set_new" -> "kofSetNew";
            case "kof_set_add" -> "kofSetAdd";
            case "kof_set_contains" -> "kofSetContains";
            case "kof_set_remove" -> "kofSetRemove";
            case "kof_set_size" -> "kofSetSize";
            case "kof_set_clear" -> "kofSetClear";
            case "kof_set_is_empty" -> "kofSetIsEmpty";
            default -> throw new IllegalStateException("KofJS: unknown set op " + kc.methodName());
        };
        registerRuntime(fn);
        if ("kof_set_new".equals(kc.methodName())) {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of()));
            return;
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        callArgs.add(receiver);
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if (Type.isVoid(kc.returnType())) {
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")
                    && !stack.isEmpty() && stack.get(stack.size() - 1) instanceof JsIr.JsSequence seq
                    && seq.value().equals(receiver)) {
                // construção mid-expression: anexa à sequência mantendo o valor
                List<JsIr.JsExpression> exprs = new ArrayList<>(seq.expressions());
                exprs.add(call);
                stack.remove(stack.size() - 1);
                stack.add(new JsIr.JsSequence(exprs, seq.value()));
                return;
            }
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")) {
                // a cópia duplicada permanece na pilha para o próximo append
                preambleExprs.add(call);
                return;
            }
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private boolean isStringOp(KofCall kc) {
        return BuiltinTypes.isString(kc.ownerType());
    }

    private void handleStringOp(MethodCtx ctx, List<Object> stack,
                                List<JsIr.JsExpression> preambleExprs, KofCall kc,
                                JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        switch (kc.methodName()) {
            case "kof_string_concat" -> stack.add(new JsIr.JsBinary(args.get(0), "+", args.get(1)));
            case "kof_string_equals" -> stack.add(new JsIr.JsConditional(
                    new JsIr.JsBinary(args.get(0), "===", args.get(1)),
                    new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
            case "valueOf" -> stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String"), List.of(args.get(0))));
            case "charAt" -> stack.add(new JsIr.JsCall(
                    new JsIr.JsMember(receiver, "charCodeAt"), List.of(args.get(0))));
            case "length" -> stack.add(new JsIr.JsMember(receiver, "length"));
            case "equals" -> stack.add(new JsIr.JsBinary(receiver, "===", args.get(0)));
            case "equalsIgnoreCase" -> stack.add(new JsIr.JsBinary(
                    new JsIr.JsCall(new JsIr.JsMember(receiver, "toUpperCase"), List.of()),
                    "===",
                    new JsIr.JsCall(new JsIr.JsMember(args.get(0), "toUpperCase"), List.of())));
            case "replace" -> {
                // Kof replace replaces all occurrences; JS replace only the
                // first, so lower through split/join. With two String
                // arguments the args are used as-is; with two characters
                // (Kof Ints) they are converted with String.fromCharCode.
                Type first = !kc.parameterTypes().isEmpty() ? kc.parameterTypes().get(0) : null;
                boolean charArgs = first instanceof Type.PrimitiveType pt
                        && "char".equals(Type.canonicalPrimitiveName(pt.name()));
                JsIr.JsExpression from = charArgs
                        ? new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("String"), "fromCharCode"),
                                List.of(args.get(0)))
                        : args.get(0);
                JsIr.JsExpression to = charArgs
                        ? new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("String"), "fromCharCode"),
                                List.of(args.get(1)))
                        : args.get(1);
                stack.add(new JsIr.JsCall(
                        new JsIr.JsMember(
                                new JsIr.JsCall(new JsIr.JsMember(receiver, "split"), List.of(from)),
                                "join"),
                        List.of(to)));
            }
            default -> {
                // substring, contains, indexOf, trim, toUpperCase, toLowerCase,
                // startsWith, endsWith, concat, split — direct JS mapping.
                JsIr.JsExpression method = "contains".equals(kc.methodName())
                        ? new JsIr.JsMember(receiver, "includes")
                        : new JsIr.JsMember(receiver, sanitizeName(kc.methodName()));
                stack.add(new JsIr.JsCall(method, args));
            }
        }
    }

    private boolean isRuntimeOp(KofCall kc) {
        String name = kc.methodName();
        return name.startsWith("kof_json_") || name.startsWith("kof_io_")
                || name.startsWith("kof_ui_")
                || name.startsWith("kof_sec_")
                || name.startsWith("kof_validation_")
                || name.startsWith("kof_enum_")
                || name.startsWith("kof_config_")
                || name.startsWith("kof_cache_")
                || name.startsWith("kof_web_") || name.startsWith("kof_db_") || name.startsWith("kof_http_")
                || name.equals("kof_spawn") || name.equals("kof_spawn_result") || name.equals("kof_await")
                || name.equals("kof_poll") || name.equals("kof_done")
                || name.equals("kof_cancel") || name.equals("kof_cancelled")
                || name.equals("kof_await_timeout")
                || name.equals("kof_select_any")
                || name.equals("kof_list_map") || name.equals("kof_list_filter")
                || name.equals("kof_list_reduce")
                || name.startsWith("kof_observability_")
                || name.startsWith("kof_time_")
                || name.startsWith("kof_scheduler_")
                || name.startsWith("kof_mq_")
                || name.startsWith("kof_log_")
                || name.equals("kof_ui_color_to_css")
                || name.equals("kof_now") || name.equals("kof_read_line")
                || name.equals("kof_read_file") || name.equals("kof_write_file")
                || name.equals("kof_process_run") || name.equals("kof_process_exit")
                || name.equals("kof_args")
                || name.equals("kof_box") || name.equals("kof_unbox");
    }

    private void handleRuntimeOp(MethodCtx ctx, List<Object> stack,
                              List<JsIr.JsExpression> preambleExprs, KofCall kc,
                              JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String name = kc.methodName();
        if (name.startsWith("kof_json_")) {
            // JSON encode/decode maps directly to JSON.stringify/parse; the
            // type information stays in the Kof compiler (generics erasure).
            JsIr.JsExpression value = kc.kind() == KofCallKind.FUNCTION
                    ? args.get(0) : receiver;
            if (name.contains("encode")) {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("JSON.stringify"), List.of(value)));
            } else if (name.startsWith("kof_json_decode_")
                    && BuiltinTypes.isList(kc.ownerType())) {
                // decode<List<T>> — bind each element to the Kof class
                Type elem = kc.ownerType() instanceof Type.ClassType lct
                        && !lct.typeArguments().isEmpty() ? lct.typeArguments().get(0) : Type.UnknownType.UNKNOWN;
                if (elem instanceof Type.ClassType ect
                        && classMethodNames.containsKey(ect.internalName())) {
                    String jsName = jsClassName(ect.internalName());
                    decodeHelpers.add(jsName);
                    JsIr.JsExpression parsed = new JsIr.JsCall(
                            new JsIr.JsIdentifier("JSON.parse"), List.of(value));
                    JsIr.JsExpression mapper = new JsIr.JsCall(
                            new JsIr.JsIdentifier("__kof_decode_" + jsName),
                            List.of(new JsIr.JsIdentifier("o")));
                    stack.add(new JsIr.JsCall(
                            new JsIr.JsMember(parsed, "map"),
                            List.of(new JsIr.JsArrow(List.of("o"), mapper))));
                } else {
                    stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("JSON.parse"), List.of(value)));
                }
            } else if (name.startsWith("kof_json_decode_")
                    && classMethodNames.containsKey(ownerInternalName(kc.ownerType()))) {
                // decode<Class> — bind the parsed object to the Kof class
                String jsName = jsClassName(ownerInternalName(kc.ownerType()));
                decodeHelpers.add(jsName);
                stack.add(new JsIr.JsCall(
                        new JsIr.JsIdentifier("__kof_decode_" + jsName), List.of(value)));
            } else {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("JSON.parse"), List.of(value)));
            }
            return;
        }
        if (name.equals("kof_box") || name.equals("kof_unbox")) {
            // JS values are already boxed; these are identity.
            stack.add(kc.kind() == KofCallKind.FUNCTION ? args.get(0) : receiver);
            return;
        }
        if (name.equals("kof_args")) {
            registerIoRuntime("kofArgs");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofArgs"), List.of()));
            return;
        }
        if (name.equals("kof_process_run")) {
            registerIoRuntime("kofProcessRun");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofProcessRun"), args));
            return;
        }
        if (name.equals("kof_process_exit")) {
            // sentinel capturado pelo KofJsRunner — nunca use System.exit
            // dentro da engine (mataria o processo hospedeiro)
            registerIoRuntime("kofProcessExit");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofProcessExit"), args));
            return;
        }
        if (name.equals("kof_ui_color_to_css")) {
            registerRuntime("kofUiColorToCss");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofUiColorToCss"), List.of(args.get(0))));
            return;
        }
        if (name.equals("kof_ui_window_new") || name.equals("kof_ui_label_new")
                || name.equals("kof_ui_button_new") || name.equals("kof_ui_button_new_action")
                || name.equals("kof_ui_input_new") || name.equals("kof_ui_column_new")
                || name.equals("kof_ui_row_new") || name.equals("kof_ui_view_new")
                || name.equals("kof_ui_box_new") || name.equals("kof_ui_stack_new")
                || name.equals("kof_ui_wrap_new") || name.equals("kof_ui_grid_new")
                || name.equals("kof_ui_spacer_new") || name.equals("kof_ui_center_new")
                || name.equals("kof_ui_align_new")
                || name.equals("kof_ui_style_new") || name.equals("kof_ui_view_bind")
                || name.equals("kof_ui_window_set_title") || name.equals("kof_ui_window_title")
                || name.equals("kof_ui_window_bind") || name.equals("kof_ui_window_show")
                || name.equals("kof_ui_window_close") || name.equals("kof_ui_label_set_text")
                || name.equals("kof_ui_label_text") || name.equals("kof_ui_label_remove")
                || name.equals("kof_ui_button_set_text") || name.equals("kof_ui_button_text")
                || name.equals("kof_ui_button_remove") || name.equals("kof_ui_input_set_text")
                || name.equals("kof_ui_input_text") || name.equals("kof_ui_input_remove")
                || name.equals("kof_ui_view_remove") || name.equals("kof_ui_window_set_theme")
                || name.equals("kof_ui_window_set_size")
                || name.equals("kof_ui_label_set_font_size") || name.equals("kof_ui_label_font_size")
                || name.equals("kof_ui_label_set_bold") || name.equals("kof_ui_label_bold")
                || name.equals("kof_ui_label_set_color") || name.equals("kof_ui_label_color")
                || name.startsWith("kof_ui_link_") || name.startsWith("kof_ui_image_")
                || name.startsWith("kof_ui_icon_") || name.startsWith("kof_ui_widget_")
                || name.startsWith("kof_ui_font_")
                || name.equals("kof_ui_component_new") || name.equals("kof_ui_component_state_get")
                || name.equals("kof_ui_component_state_set") || name.equals("kof_ui_component_view")
                || name.equals("kof_ui_component_on_mount") || name.equals("kof_ui_component_on_dispose")
                || name.equals("kof_ui_component_effect") || name.equals("kof_ui_component_on")
                || name.equals("kof_ui_component_bind") || name.equals("kof_ui_component_remove")
                || name.equals("kof_ui_component_mount") || name.equals("kof_ui_component_unmount")
                || name.equals("kof_ui_nodes_live") || name.equals("kof_ui_flush_ui")
                || name.equals("kof_ui_event_type") || name.equals("kof_ui_emit")
                || name.equals("kof_ui_event_stop")
                || name.equals("kof_ui_store_new") || name.equals("kof_ui_store_get")
                || name.equals("kof_ui_store_set") || name.equals("kof_ui_store_subscribe")
                || name.equals("kof_ui_store_unsubscribe") || name.equals("kof_ui_stores_live")
                || name.equals("kof_ui_route_register") || name.equals("kof_ui_router_go1")
                || name.equals("kof_ui_router_go2") || name.equals("kof_ui_router_replace1")
                || name.equals("kof_ui_router_replace2") || name.equals("kof_ui_router_back")
                || name.equals("kof_ui_router_forward") || name.equals("kof_ui_router_param")
                || name.equals("kof_ui_router_current") || name.equals("kof_ui_router_depth")) {
            registerRuntime(capitalizeUiFn(name));
            List<JsIr.JsExpression> callArgs = new ArrayList<>(args);
            if (kc.kind() == KofCallKind.INSTANCE && receiver != null) {
                callArgs.add(0, receiver);
            }
            JsIr.JsExpression call = new JsIr.JsCall(
                    new JsIr.JsIdentifier(capitalizeUiFn(name)), callArgs);
            if (Type.isVoid(kc.returnType())) {
                throw new StatementEnd(call);
            }
            stack.add(call);
            return;
        }
        if (name.equals("kof_scheduler_every")) {
            registerRuntime("kofSchedulerEvery");
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofSchedulerEvery"), args);
            if (Type.isVoid(kc.returnType())) throw new StatementEnd(call);
            stack.add(call);
            return;
        }
        if (name.equals("kof_scheduler_at")) {
            registerRuntime("kofSchedulerAt");
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofSchedulerAt"), args);
            if (Type.isVoid(kc.returnType())) throw new StatementEnd(call);
            stack.add(call);
            return;
        }
        if (name.equals("kof_scheduler_cancel")) {
            registerRuntime("kofSchedulerCancel");
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofSchedulerCancel"), args);
            throw new StatementEnd(call);
        }
        if (name.startsWith("kof_http_")) {
            // JS real via Java HttpClient interop (GraalJS allowAllAccess)
            String jsFn = switch (name) {
                case "kof_http_get" -> "kofHttpGet";
                case "kof_http_get_headers" -> "kofHttpGetHeaders";
                case "kof_http_delete" -> "kofHttpDelete";
                case "kof_http_delete_headers" -> "kofHttpDeleteHeaders";
                case "kof_http_options" -> "kofHttpOptions";
                case "kof_http_options_headers" -> "kofHttpOptionsHeaders";
                case "kof_http_post" -> "kofHttpPost";
                case "kof_http_post_headers" -> "kofHttpPostHeaders";
                case "kof_http_put" -> "kofHttpPut";
                case "kof_http_put_headers" -> "kofHttpPutHeaders";
                case "kof_http_patch" -> "kofHttpPatch";
                case "kof_http_patch_headers" -> "kofHttpPatchHeaders";
                case "kof_http_status" -> "kofHttpStatus";
                case "kof_http_timeout_set" -> "kofHttpTimeoutSet";
                case "kof_http_retry_set" -> "kofHttpRetrySet";
                case "kof_http_circuit_set" -> "kofHttpCircuitSet";
                default -> "kofWebStub";
            };
            registerRuntime(jsFn);
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(jsFn), args);
            if (Type.isVoid(kc.returnType())) {
                throw new StatementEnd(call);
            }
            stack.add(call);
            return;
        }
        if (name.equals("kof_web_status") && args.size() == 2) {
            stack.add(args.get(1));
            return;
        }
        if (name.equals("kof_web_header_set") && args.size() == 2) {
            stack.add(args.get(1));
            return;
        }
        if (name.startsWith("kof_web_")) {
            // JS target: WEB001 REAL IMPLEMENTATION via GraalJS HttpServer
            // Uses Java.type('com.sun.net.8') + Value-based handler invoke
            // for GraalJS CreateObject interop. The handler (lambda obj) has
            // an 'invoke' method that processes Exchange.
            if (name.equals("kof_web_app_new")) {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofWebAppNew"), List.of()));
                return;
            }
            if (name.equals("kof_web_route")) {
                registerRuntime("kofWebRoute");
                JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofWebRoute"), args);
                throw new StatementEnd(call);
            }
            if (name.equals("kof_web_listen")) {
                registerRuntime("kofWebListen");
                JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofWebListen"), args);
                if (Type.isVoid(kc.returnType())) {
                    throw new StatementEnd(call);
                }
                stack.add(call);
                return;
            }
            if (name.equals("kof_web_status") && args.size() == 2) {
                stack.add(args.get(1));
                return;
            }
            if (name.equals("kof_web_header_set") && args.size() == 2) {
                stack.add(args.get(1));
                return;
            }
            // fallback: stub for unimplemented web functions
            registerRuntime("kofWebStub");
            JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier("kofWebStub"), args);
            if (Type.isVoid(kc.returnType())) {
                throw new StatementEnd(call);
            }
            stack.add(call);
            return;
        }
        if (name.equals("kof_now")) {
            stack.add(new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("Date"), "now"), List.of()));
            return;
        }
        if (name.startsWith("kof_sec_")) {
            registerRuntime(runtimeJsName(name));
            List<JsIr.JsExpression> callArgs = new ArrayList<>(args);
            JsIr.JsExpression call = new JsIr.JsCall(
                    new JsIr.JsIdentifier(runtimeJsName(name)), callArgs);
            if (Type.isVoid(kc.returnType())) {
                throw new StatementEnd(call);
            }
            stack.add(call);
            return;
        }
        String fn = runtimeJsName(name);
        if (name.startsWith("kof_io_") || name.equals("kof_read_line")
                || name.equals("kof_read_file") || name.equals("kof_write_file")) {
            registerIoRuntime(fn);
        } else {
            registerRuntime(fn);
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        if (name.startsWith("kof_io_") && receiver != null) {
            callArgs.add(receiver);
        }
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if (name.equals("kof_await") || name.equals("kof_await_timeout")
                || name.equals("kof_select_any")) {
            call = new JsIr.JsAwait(call);
        }
        if (name.equals("kof_poll") && kc.returnType() instanceof Type.PrimitiveType) {
            // poll não-pronto devolve default do primitivo (0/false), não null —
            // paridade JVM/Native e evita await acidental em função síncrona.
            call = new JsIr.JsBinary(call, "??", defaultForType(kc.returnType()));
        }
        if (Type.isVoid(kc.returnType())) {
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private String runtimeJsName(String kofName) {
        StringBuilder sb = new StringBuilder("kof");
        boolean upper = false;
        for (int i = 3; i < kofName.length(); i++) {
            char c = kofName.charAt(i);
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    // ── Plumbing ────────────────────────────────────────────────────

    private void registerRuntime(String fn) {
        if (!runtimeImports.contains(fn)) runtimeImports.add(fn);
    }

    private void registerIoRuntime(String fn) {
        if (!ioRuntimeImports.contains(fn)) ioRuntimeImports.add(fn);
    }

    private JsIr.JsExpression pop(List<Object> stack) {
        Object top = popRaw(stack);
        if (top instanceof NewPending np) {
            return new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), List.of());
        }
        if (top instanceof DupMarker) {
            // The frontend omits the <init> call when the class has only the
            // implicit default constructor; complete the pending new.
            Object base = popRaw(stack);
            if (base instanceof NewPending np) {
                return new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), List.of());
            }
            throw new IllegalStateException("KofJS: DupMarker without NewPending; stack=" + stack);
        }
        return (JsIr.JsExpression) top;
    }

    private Object popRaw(List<Object> stack) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("KofJS: expression stack underflow\nops="
                    + currentCtxOpsDump);
        }
        return stack.remove(stack.size() - 1);
    }

    private List<KofOperation> currentCtxOpsDump = List.of();

    private boolean isPureDuplicate(JsIr.JsExpression expr) {
        return expr instanceof JsIr.JsIdentifier || expr instanceof JsIr.JsThis
                || expr instanceof JsIr.JsMember || expr instanceof JsIr.JsNull
                || expr instanceof JsIr.JsNumber || expr instanceof JsIr.JsString;
    }

    private String ownerInternalName(Type type) {
        if (type instanceof Type.ClassType ct) return ct.internalName();
        return "";
    }

    private String classPackage(Type type) {
        if (type instanceof Type.ClassType ct) return ct.packageName();
        return "";
    }

    private String className(Type type) {
        if (type instanceof Type.ClassType ct) return ct.name();
        return "";
    }

    private static String jsClassName(String internalName) {
        if (internalName == null || internalName.isEmpty()) return "Object";
        return sanitizeName(internalName.replace('/', '_'));
    }

    private static String sanitizeName(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '$') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String result = sb.toString();
        if (result.isEmpty() || Character.isDigit(result.charAt(0))) {
            result = "_" + result;
        }
        if (RESERVED.contains(result)) {
            result = "_" + result;
        }
        return result;
    }

    private List<String> parameterNames(MethodCtx ctx) {
        if ("main".equals(ctx.methodName) && ctx.paramCount == 1) {
            // The injected String[] parameter is not a source parameter.
            return List.of();
        }
        List<String> names = new ArrayList<>();
        int start = ctx.instanceMethod ? 1 : 0;
        for (int i = start; i < ctx.localNames.size() && names.size() < ctx.paramCount; i++) {
            if (ctx.captureSlots.contains(i)) continue;
            String name = ctx.localNames.get(i);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private void insertSuperCall(IRClass clazz, List<JsIr.JsStatement> body) {
        if (clazz.superName() == null || "java/lang/Object".equals(clazz.superName())
                || "java/lang/Record".equals(clazz.superName())) {
            return;
        }
        boolean hasSuper = body.stream().anyMatch(stmt -> stmt instanceof JsIr.JsExprStmt es
                && es.expression() instanceof JsIr.JsCall call
                && call.callee() instanceof JsIr.JsIdentifier id && "super".equals(id.name()));
        if (!hasSuper) {
            body.add(0, new JsIr.JsExprStmt(new JsIr.JsCall(new JsIr.JsIdentifier("super"), List.of())));
        }
    }

    /**
     * JavaScript class fields are undefined until assigned; JVM instance fields
     * default to 0/false/null. Field defaults are emitted at the start of every
     * constructor (after the super call) to preserve Kof/JVM semantics.
     */
    private void insertFieldDefaults(IRClass clazz, List<JsIr.JsStatement> body) {
        List<JsIr.JsStatement> defaults = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            if ((field.accessFlags() & AccessFlags.STATIC) != 0) continue;
            JsIr.JsExpression value = field.initialValue() != null
                    ? literalExpr(new KofLoadLiteral(field.type(), field.initialValue()))
                    : defaultForType(field.type());
            defaults.add(new JsIr.JsExprStmt(new JsIr.JsBinary(
                    new JsIr.JsMember(new JsIr.JsThis(), jsFieldName(clazz, field.name())), "=", value)));
        }
        if (defaults.isEmpty()) return;
        int insertAt = 0;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof JsIr.JsExprStmt es
                    && es.expression() instanceof JsIr.JsCall call
                    && call.callee() instanceof JsIr.JsIdentifier id && "super".equals(id.name())) {
                insertAt = i + 1;
                break;
            }
        }
        body.addAll(insertAt, defaults);
    }

    private JsIr.JsExpression defaultForType(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "bool" -> new JsIr.JsNumber("0");
                default -> new JsIr.JsNumber("0");
            };
        }
        return new JsIr.JsNull();
    }
}
