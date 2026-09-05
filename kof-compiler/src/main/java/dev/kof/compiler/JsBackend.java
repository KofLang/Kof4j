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
        String fileName = moduleFileName(module.name());
        String sourceMapUrl = debugInfo ? "//# sourceMappingURL=" + fileName + ".map\n" : "";
        Path outFile = outputDir.resolve(fileName);
        Files.writeString(outFile, code + sourceMapUrl);
        writeRuntime(outputDir);
        writeHtmlEntry(outputDir, module.name());
        if (debugInfo) {
            writeSourceMap(module, outputDir, fileName, emitter.functionLines());
        }
    }

    private static String moduleFileName(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) return "Default.mjs";
        return moduleName + ".mjs";
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

    // ── Runtime emission ────────────────────────────────────────────

    private static final String CORE_RUNTIME = """
            // KofJS core runtime — platform-neutral helpers.
            // Generated by the Kof compiler (KofJS backend).
            // This module is not a VM: it only provides operations that
            // JavaScript does not represent directly.

            if (typeof document === "undefined") {
                function kofMakeEl(tag) {
                    return {
                        tagName: String(tag).toUpperCase(),
                        id: null,
                        className: "",
                        textContent: "",
                        children: [],
                        parentNode: null,
                        style: {},
                        appendChild(child) {
                            if (child && child.parentNode) child.parentNode.removeChild(child);
                            child.parentNode = this;
                            this.children.push(child);
                            return child;
                        },
                        removeChild(child) {
                            const i = this.children.indexOf(child);
                            if (i >= 0) { this.children.splice(i, 1); child.parentNode = null; }
                            return child;
                        },
                        remove() { if (this.parentNode) this.parentNode.removeChild(this); },
                        addEventListener(type, fn) { this._handlers = this._handlers || {}; (this._handlers[type] = this._handlers[type] || []).push(fn); }
                    };
                }
                const kofRoot = kofMakeEl("div");
                kofRoot.id = "kof-root";
                const kofElements = { "kof-root": kofRoot };
                const kofHead = kofMakeEl("head");
                const kofHtml = kofMakeEl("html");
                kofHtml.appendChild(kofHead);
                globalThis.document = {
                    title: "",
                    head: kofHead,
                    documentElement: kofHtml,
                    createElement(tag) { return kofMakeEl(tag); },
                    getElementById(id) { return kofElements[id] || null; }
                };
                globalThis.window = globalThis;
                globalThis.__kofRegisterElement = function (id, el) { kofElements[id] = el; };
            }

            function kofEscapeHtml(s) {
                return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
                        .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
            }

            function kofSerialize(node) {
                if (!node) return "";
                const tag = (node.tagName ? node.tagName.toLowerCase() : "div");
                let attrs = "";
                if (node.id) attrs += ' id="' + kofEscapeHtml(node.id) + '"';
                if (node.className) attrs += ' class="' + kofEscapeHtml(node.className) + '"';
                const kids = Array.from(node.children || []);
                const inner = kids.map(kofSerialize).join("");
                const content = inner.length > 0 ? inner : kofEscapeHtml(node.textContent || "");
                return "<" + tag + attrs + ">" + content + "</" + tag + ">";
            }

            export function kofUiSerializeHtml() {
                const root = document.getElementById("kof-root");
                if (!root) return "";
                const html = "<!DOCTYPE html>\\n<html>\\n<head>\\n<meta charset=\\"utf-8\\">\\n"
                        + "<title>" + kofEscapeHtml(document.title || "Kof") + "</title>\\n"
                        + "</head>\\n<body>\\n" + kofSerialize(root) + "\\n</body>\\n</html>\\n";
                globalThis.kof__uiRootHtml = html;
                return html;
            }

            export function kofPrintln(x) {
                console.log(x);
            }

            let kofLogLevel = 1; // default "info": 0 debug, 1 info, 2 warn, 3 error, 4 off
            try {
                const lv = (process.env.KOF_LOG_LEVEL || "info").trim().toLowerCase();
                if (lv === "debug") kofLogLevel = 0;
                else if (lv === "warn") kofLogLevel = 2;
                else if (lv === "error") kofLogLevel = 3;
                else if (lv === "off") kofLogLevel = 4;
            } catch (e) { /* non-node env: keep default */ }

            export function kofLogDebug(msg) { if (kofLogLevel <= 0) console.log(String(msg)); }
            export function kofLogInfo(msg)  { if (kofLogLevel <= 1) console.log(String(msg)); }
            export function kofLogWarn(msg)  { if (kofLogLevel <= 2) (console.warn || console.log)(String(msg)); }
            export function kofLogError(msg) { if (kofLogLevel <= 3) (console.error || console.log)(String(msg)); }

            export function kofUiWindowNew(title) {
                if (typeof document === "undefined") {
                    return -1;
                }
                kofUiInjectTheme();
                const root = document.getElementById("kof-root");
                if (!root) {
                    return -1;
                }
                if (typeof window.__kofWindows === "undefined") {
                    window.__kofWindows = {};
                }
                const id = Object.keys(window.__kofWindows).length + 1;
                const winEl = document.createElement("div");
                winEl.className = "kof-window";
                window.__kofWindows[id] = winEl;
                document.title = title;
                root.appendChild(winEl);
                return id;
            }

            // Kof UI theme — the same Dracula/VSCode aesthetic in every host
            // (browser, webview, KofWebHost): one Kof program, one look.
            function kofUiInjectTheme() {
                if (typeof document === "undefined") return;
                if (document.getElementById("kof-theme")) return;
                const style = document.createElement("style");
                style.id = "kof-theme";
                style.textContent = [
                    ":root { --bg:#282a36; --fg:#f8f8f2; --panel:#21222c; --border:#2e303e;",
                    "  --hover:#2e303e; --accent:#8be9fd; --dim:#6272a4; --output:#50fa7b; }",
                    "html, body { margin:0; height:100%; overflow:hidden; }",
                    "body { background:var(--bg); color:var(--fg); font-family:",
                    '  "Cascadia Code", "Fira Code", Consolas, Menlo, monospace; }',
                    ".kof-label { color:var(--output); font-size:13px; line-height:1.5;",
                    "  white-space:pre-wrap; padding:2px 0; }"
                ].join("\\n");
                (document.head || document.documentElement).appendChild(style);
            }

            export function kofUiWindowSetTitle(window, title) {
                if (typeof document !== "undefined") {
                    document.title = title;
                }
            }

            export function kofUiWindowTitle(window) {
                return typeof document !== "undefined" ? document.title : "";
            }

            export function kofUiWindowBind(win, label) {
                if (typeof document === "undefined") {
                    return;
                }
                const winEl = globalThis.window && globalThis.window.__kofWindows
                        && globalThis.window.__kofWindows[win];
                if (!winEl) return;
                // Component Core: a component binds under the window and mounts
                // (view + onMount) — lifecycle is automatic.
                const comp = kofUiComponents.get(label);
                if (comp) {
                    if (comp.el) winEl.appendChild(comp.el);
                    kofUiComponentMount(label);
                    return;
                }
                const nodes = globalThis.window && globalThis.window.__kofNodes;
                const node = nodes && nodes[label];
                if (node) {
                    winEl.appendChild(node);
                }
            }

            export function kofUiWindowShow(window) {
                kofUiSerializeHtml();
            }

            export function kofUiWindowSetSize(window, width, height) {
                if (typeof document === "undefined") {
                    return;
                }
                const winEl = window.__kofWindows && window.__kofWindows[window];
                if (winEl) {
                    winEl.style.width = width + "px";
                    winEl.style.height = height + "px";
                }
                try {
                    if (typeof window.resizeTo === "function") {
                        window.resizeTo(width, height);
                    }
                } catch (e) {
                    // resizeTo is blocked on some hosts; the CSS sizing above
                    // still constrains the window content.
                }
            }

            export function kofUiWindowClose(window) {
                if (typeof document === "undefined") {
                    return;
                }
                const winEl = window.__kofWindows && window.__kofWindows[window];
                if (winEl) {
                    if (winEl.parentNode) {
                        winEl.parentNode.removeChild(winEl);
                    }
                    delete window.__kofWindows[window];
                }
            }

            export function kofUiWindowSetTheme(window, theme) {
                if (typeof document === "undefined") {
                    return;
                }
                const root = document.getElementById("kof-root");
                if (!root) {
                    return;
                }
                const colors = theme
                        ? { background: 0x121212FF, text: 0xFFFFFFFF }
                        : { background: 0xFFFFFFFF, text: 0x000000FF };
                root.style.backgroundColor = kofUiColorToCss(colors.background);
                root.style.color = kofUiColorToCss(colors.text);
            }
            """;

    private static final String UI_COMPONENT_RUNTIME = """
            // ── Component Core (docs/ui/architecture.md) ─────────────
            // A UI is a tree of components. A Component node carries: identity,
            // state (Int), a view builder, lifecycle hooks, effects (auto-cleaned)
            // and events. Rendering is KofJS; the framework (not the widget)
            // owns the tree, the render schedule and the lifecycle.
            const kofUiComponents = new Map();
            let kofUiSeq = 0;
            let kofNodeSeq = 0;
            let kofUiFlushing = false;
            const kofUiDirty = [];
            const KOF_UI_EV = {
                click: "click", dblclick: "dblclick", mousedown: "mousedown",
                mouseup: "mouseup", mousemove: "mousemove", mouseenter: "mouseenter",
                mouseleave: "mouseleave", wheel: "wheel", keydown: "keydown",
                keyup: "keyup", focus: "focus", blur: "blur", input: "input", change: "change"
            };

            function kofUiIsNode(id) {
                return window.__kofNodes && Object.prototype.hasOwnProperty.call(window.__kofNodes, id);
            }
            function kofUiParentOf(id) {
                const n = window.__kofNodes && window.__kofNodes[id];
                return n && n.parentNode ? n : null;
            }
            function kofUiSubtreeIds(rootId) {
                // all nodes reachable from rootId (BFS over the DOM tree)
                const out = [];
                const q = [window.__kofNodes[rootId]];
                while (q.length > 0) {
                    const n = q.shift();
                    if (!n || n._kofGone) continue;
                    out.push(n);
                    const kids = Array.from(n.children || []);
                    for (const k of kids) q.push(k);
                }
                return out;
            }
            function kofUiDetachDom(id) {
                const n = window.__kofNodes && window.__kofNodes[id];
                if (n && n.parentNode && n.parentNode.removeChild) {
                    n.parentNode.removeChild(n);
                }
            }
            function kofUiRemoveSubtree(rootId) {
                // remove the DOM subtree of a widget id and prune the registry
                if (!kofUiIsNode(rootId)) return;
                for (const n of kofUiSubtreeIds(rootId)) {
                    if (n.parentNode) n.parentNode.removeChild(n);
                    n._kofGone = true;
                    for (const key in window.__kofNodes) {
                        if (window.__kofNodes[key] === n) {
                            delete window.__kofNodes[key];
                            break;
                        }
                    }
                }
            }

            function kofUiRunFn(fn) {
                // a Kof lambda compiles to a class with an invoke() method;
                // plain functions pass through.
                return fn && typeof fn.invoke === "function" ? fn.invoke.bind(fn) : fn;
            }

            function kofUiScheduleFlush() {
                if (kofUiFlushing) {
                    // already rendering — batch the rest
                    if (typeof Promise !== "undefined" && Promise.resolve) {
                        Promise.resolve().then(() => kofUiFlushQueue());
                    }
                    return;
                }
                kofUiFlushQueue();
            }

            function kofUiFlushQueue() {
                if (kofUiFlushing) return;
                kofUiFlushing = true;
                try {
                    while (kofUiDirty.length > 0) {
                        const id = kofUiDirty.shift();
                        const c = kofUiComponents.get(id);
                        if (c && c.mounted && c.view) {
                            kofUiRender(c);
                        }
                    }
                } finally {
                    kofUiFlushing = false;
                }
            }

            function kofUiRender(c) {
                // rebuild the component's child subtree: run the view builder
                // with the current state, then swap the fresh DOM in place.
                // (handle diffing is a Phase-9 optimization)
                let rootId = 0;
                try {
                    const v = kofUiRunFn(c.view);
                    rootId = v ? v(c.state) : 0;
                } catch (e) {
                    rootId = 0;
                }
                if (c.el) {
                    const oldEl = window.__kofNodes && window.__kofNodes[c.root];
                    if (c.root !== rootId && oldEl
                            && oldEl.parentNode === c.el && oldEl.parentNode.removeChild) {
                        c.el.removeChild(oldEl);
                    }
                    const rootEl = window.__kofNodes && window.__kofNodes[rootId];
                    if (rootEl && rootEl.parentNode !== c.el) {
                        c.el.appendChild(rootEl);
                    }
                }
                c.root = rootId;
            }

            export function kofUiComponentNew(state) {
                const id = ++kofUiSeq;
                const c = {
                    id: id, name: "c" + id, state: state,
                    view: null, mounted: false, disposed: false,
                    el: null, root: null, onMountFn: null, onDisposeFn: null,
                    effects: [], effectFns: [],
                    parent: null, children: []
                };
                kofUiComponents.set(id, c);
                if (typeof document !== "undefined") {
                    const wrap = document.createElement("div");
                    wrap.className = "kof-component";
                    c.el = wrap;
                }
                return id;
            }

            export function kofUiComponentStateGet(c) {
                const n = kofUiComponents.get(c);
                return n ? n.state : 0;
            }

            export function kofUiComponentStateSet(c, value) {
                const n = kofUiComponents.get(c);
                if (!n) return;
                n.state = value;
                // state change is the invalidation point: mark ONLY this
                // component dirty and schedule a batched re-render.
                if (n.mounted && !kofUiDirty.includes(c)) {
                    kofUiDirty.push(c);
                }
                kofUiScheduleFlush();
            }

            export function kofUiComponentView(c, builder) {
                const n = kofUiComponents.get(c);
                if (!n) return;
                n.view = builder;
                if (n.mounted) {
                    if (!kofUiDirty.includes(c)) kofUiDirty.push(c);
                    kofUiScheduleFlush();
                }
            }

            export function kofUiComponentOnMount(c, fn) {
                const n = kofUiComponents.get(c);
                if (n) n.onMountFn = fn;
            }

            export function kofUiComponentOnDispose(c, fn) {
                const n = kofUiComponents.get(c);
                if (n) n.onDisposeFn = fn;
            }

            export function kofUiComponentEffect(c, fn) {
                // effects run on mount (or immediately when the component is
                // already mounted) and their cleanup runs on unmount, in
                // reverse registration order — no manual leak management.
                const n = kofUiComponents.get(c);
                if (!n) return;
                const f = kofUiRunFn(fn);
                if (!f) return;
                n.effectFns.push(f);
                if (n.mounted) kofUiRunEffect(n, f);
            }

            function kofUiRunEffect(n, f) {
                let result;
                try {
                    result = f();
                } catch (e) {
                    result = null;
                }
                n.effects.push(result);
            }

            export function kofUiComponentMount(c) {
                const n = kofUiComponents.get(c);
                if (!n || n.mounted) return;
                n.mounted = true;
                // mount: (mount view) -> onMount() -> effects — deterministic
                if (n.view) kofUiRender(n);
                const om = kofUiRunFn(n.onMountFn);
                if (om) {
                    try { om(); } catch (e) {}
                }
                for (const f of n.effectFns) kofUiRunEffect(n, f);
            }

            export function kofUiComponentUnmount(c) {
                const n = kofUiComponents.get(c);
                if (!n || !n.mounted) return;
                n.mounted = false;
                // unmount cascades top-down: children first (they lose their
                // host), then this node's hooks. Detach once at the root.
                for (const child of n.children.slice()) {
                    const cc = kofUiComponents.get(child);
                    if (cc && cc.mounted) kofUiComponentUnmount(child);
                }
                // unmount: onDispose() -> effects() in REVERSE
                const od = kofUiRunFn(n.onDisposeFn);
                if (od) {
                    try { od(); } catch (e) {}
                }
                for (let i = n.effects.length - 1; i >= 0; i--) {
                    try {
                        const ef = n.effects[i];
                        if (typeof ef === "function") ef();
                    } catch (e) {}
                }
                n.effects.length = 0;
                n.effectFns.length = 0;
                n.disposed = true;
            }

            export function kofUiComponentBind(c, child) {
                // compose: attach a child widget or component under this one.
                const n = kofUiComponents.get(c);
                if (!n || !n.el) return;
                // a child component mounts on bind (lifecycle is automatic)
                const childComp = kofUiComponents.get(child);
                if (childComp) {
                    childComp.parent = n;
                    if (!n.children.includes(child)) n.children.push(child);
                    if (childComp.el) n.el.appendChild(childComp.el);
                    kofUiComponentMount(child);
                    return;
                }
                const childEl = window.__kofNodes && window.__kofNodes[child];
                if (childEl) n.el.appendChild(childEl);
            }

            export function kofUiComponentRemove(c) {
                const n = kofUiComponents.get(c);
                if (!n) return;
                // detach from the parent's child list (tree is the source of truth)
                if (n.parent) {
                    const i = n.parent.children.indexOf(c);
                    if (i >= 0) n.parent.children.splice(i, 1);
                    n.parent = null;
                }
                if (n.mounted) {
                    // unmount the subtree, freeing every component in it
                    kofUiRemoveSubtreeComponents(c);
                } else {
                    n.disposed = true;
                    kofUiDetachDom(c);
                    kofUiComponents.delete(c);
                }
            }

            function kofUiRemoveSubtreeComponents(c) {
                const n = kofUiComponents.get(c);
                if (!n) return;
                for (const child of n.children.slice()) {
                    kofUiRemoveSubtreeComponents(child);
                }
                if (n.mounted) {
                    // unmount runs hooks + cleanup; skip the recursive
                    // children walk (already freed above)
                    n.children.length = 0;
                    kofUiComponentUnmount(c);
                }
                kofUiComponents.delete(c);
            }

            export function kofUiComponentOn(c, type, handler) {
                // centralised event dispatch on the component root element.
                const n = kofUiComponents.get(c);
                if (!n || !n.el || !type || !handler) return;
                const domType = KOF_UI_EV[type] || type;
                n.el._kofHandlers = n.el._kofHandlers || {};
                const arr = n.el._kofHandlers[domType];
                if (arr) arr.push(handler);
                else n.el._kofHandlers[domType] = [handler];
                if (typeof n.el.addEventListener === "function") {
                    n.el.addEventListener(domType, function (ev) {
                        kofUiDispatchEvent(c, domType, ev);
                    });
                }
            }


            // centralised event dispatch: one registry, deterministic cleanup
            export function kofUiWidgetOn(id, type, handler) {
                if (!type || !handler) return;
                const node = window.__kofNodes && window.__kofNodes[id];
                if (!node) return;
                node._kofHandlers = node._kofHandlers || {};
                const domType = KOF_UI_EV[type] || type;
                const arr = node._kofHandlers[domType];
                if (arr) arr.push(handler);
                else node._kofHandlers[domType] = [handler];
                if (typeof node.addEventListener === "function") {
                    node.addEventListener(domType, function (ev) {
                        const h = node._kofHandlers && node._kofHandlers[domType];
                        if (!h) return;
                        for (const fn of h) {
                            try {
                                if (typeof fn.invoke === "function") fn.invoke();
                                else fn();
                            } catch (e) {}
                        }
                    });
                }
            }

            export function kofUiNodesLive() {
                return kofUiComponents.size;
            }

            export function kofUiFlushUi() {
                kofUiFlushQueue();
            }

            export function kofUiEventType(type) {
                // kof.ui.Event identity: the event kind as registered.
                return type || "";
            }

            // ── Link ────────────────────────────────────────────
            export function kofUiLinkNew(text, url) {
                if (typeof document === "undefined") return -1;
                const a = document.createElement("a");
                a.textContent = text;
                a.href = url;
                a.target = "_blank";
                a.rel = "noopener";
                a.className = "kof-link";
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = a;
                return id;
            }
            export function kofUiLinkSetText(link, text) {
                const n = window.__kofNodes && window.__kofNodes[link];
                if (n) n.textContent = text;
            }
            export function kofUiLinkText(link) {
                const n = window.__kofNodes && window.__kofNodes[link];
                return n ? n.textContent : "";
            }
            export function kofUiLinkSetUrl(link, url) {
                const n = window.__kofNodes && window.__kofNodes[link];
                if (n) {
                    n.href = url;
                    if (!n.target) { n.target = "_blank"; n.rel = "noopener"; }
                }
            }
            export function kofUiLinkUrl(link) {
                const n = window.__kofNodes && window.__kofNodes[link];
                return n ? n.href : "";
            }
            export function kofUiLinkRemove(link) {
                const n = window.__kofNodes && window.__kofNodes[link];
                if (n && n.parentNode) n.parentNode.removeChild(n);
            }

            // ── Image (preview) ────────────────────────────────
            export function kofUiImageNew(src) {
                if (typeof document === "undefined") return -1;
                const img = document.createElement("img");
                img.src = src;
                img.className = "kof-image";
                img.alt = "";
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = img;
                return id;
            }
            export function kofUiImageSetSrc(image, src) {
                const n = window.__kofNodes && window.__kofNodes[image];
                if (n) n.src = src;
            }
            export function kofUiImageSrc(image) {
                const n = window.__kofNodes && window.__kofNodes[image];
                return n ? n.src : "";
            }
            export function kofUiImageRemove(image) {
                const n = window.__kofNodes && window.__kofNodes[image];
                if (n && n.parentNode) n.parentNode.removeChild(n);
            }

            // ── Icon (SVG paths embutidos) ─────────────────────
            const KOF_ICONS = {
              home: "M12 3l9 8h-3v9h-5v-6h-2v6H6v-9H3z",
              star: "M12 2l2.9 6.3 6.9.7-5.1 4.6 1.4 6.8L12 17l-6.1 3.4 1.4-6.8L2.2 9l6.9-.7z",
              heart: "M12 21s-8-5.3-8-11a4.6 4.6 0 018-3 4.6 4.6 0 018 3c0 5.7-8 11-8 11z",
              search: "M10 2a8 8 0 105.3 14l5.4 5.4 1.4-1.4-5.4-5.4A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z",
              settings: "M12 8a4 4 0 100 8 4 4 0 000-8zm9 4l-2.1-.6a7 7 0 00-.6-1.5l1.1-1.9-1.5-1.5-1.9 1.1a7 7 0 00-1.5-.6L14 3h-4l-.6 2.1a7 7 0 00-1.5.6L6 4.6 4.5 6.1l1.1 1.9a7 7 0 00-.6 1.5L3 10v4l2.1.6c.1.5.3 1 .6 1.5l-1.1 1.9 1.5 1.5 1.9-1.1c.5.3 1 .5 1.5.6L10 23h4l.6-2.1c.5-.1 1-.3 1.5-.6l1.9 1.1 1.5-1.5-1.1-1.9c.3-.5.5-1 .6-1.5L21 14z",
              user: "M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-4.4 0-8 2.2-8 5v3h16v-3c0-2.8-3.6-5-8-5z",
              menu: "M3 5h18v2H3zM3 11h18v2H3zM3 17h18v2H3z",
              close: "M6 5l13 13-1.4 1.4L4.6 6.4zM19 5L6 18l1.4 1.4L20.4 6.4z",
              check: "M9 16.2l-4.2-4.2L3.4 13.4 9 19 21 7l-1.4-1.4z",
              plus: "M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z",
              minus: "M5 11h14v2H5z",
              trash: "M6 7h12l-1 14H7zM9 4h6l1 2h4v2H4V6h4z",
              edit: "M4 20h4L20 8l-4-4L4 16zm2.8-3.4L16.6 6.8 17.2 7.4 7.4 17.2z",
              share: "M18 8a3 3 0 10-2.8-4H15L6 9.3a3 3 0 100 5.4l9 5.3h.2A3 3 0 1018 16a3 3 0 00-2 .8L7.3 11.6a3 3 0 000-1.2L15.9 5.3A3 3 0 0118 8z",
              download: "M12 16l-5-5h3V4h4v7h3zM5 18h14v2H5z",
              upload: "M12 3l5 5h-3v8h-4V8H7zM5 18h14v2H5z",
              mail: "M2 5h20v14H2zm2 2v.4l8 5 8-5V7l-8 5z",
              phone: "M6 2h4l2 5-2.5 1.5a12 12 0 006 6L17 12l5 2v4a2 2 0 01-2 2A17 17 0 014 4a2 2 0 012-2z",
              calendar: "M7 2h2v2h6V2h2v2h4v18H3V4h4zm12 8H5v10h14zM7 6H5v2h14V6h-2z",
              clock: "M12 2a10 10 0 100 20 10 10 0 000-20zm1 5h-2v6l5 3 1-1.7-4-2.3z",
              eye: "M12 5C6 5 2 12 2 12s4 7 10 7 10-7 10-7-4-7-10-7zm0 11a4 4 0 110-8 4 4 0 010 8z",
              lock: "M6 10V7a6 6 0 1112 0v3h2v12H4V10zm2 0h8V7a4 4 0 10-8 0z"
            };
            export function kofUiIconNew(name) { return kofUiIconNewSize(name, 24); }
            export function kofUiIconNewSize(name, size) {
                if (typeof document === "undefined") return -1;
                const d = KOF_ICONS[name] || KOF_ICONS["close"];
                const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
                svg.setAttribute("viewBox", "0 0 24 24");
                svg.setAttribute("width", size);
                svg.setAttribute("height", size);
                const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
                p.setAttribute("d", d);
                p.setAttribute("fill", "currentColor");
                svg.appendChild(p);
                svg.dataset.kofIcon = name;
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = svg;
                return id;
            }
            export function kofUiIconSetName(icon, name) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                if (!n) return;
                const d = KOF_ICONS[name];
                if (d) n.querySelector("path").setAttribute("d", d);
                n.dataset.kofIcon = name;
            }
            export function kofUiIconName(icon) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                return n ? (n.dataset.kofIcon || "") : "";
            }
            export function kofUiIconSetSize(icon, size) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                if (n) { n.setAttribute("width", size); n.setAttribute("height", size); }
            }
            export function kofUiIconSize(icon) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                return n ? parseInt(n.getAttribute("width"), 10) || 24 : 24;
            }
            export function kofUiIconRemove(icon) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                if (n && n.parentNode) n.parentNode.removeChild(n);
            }

            // ── Font ───────────────────────────────────────────
            let __kofFontSeq = 0;
            export function kofUiFontNew(family, size) {
                window.__kofFonts = window.__kofFonts || {};
                const id = ++__kofFontSeq;
                window.__kofFonts[id] = { family, size, bold: false };
                return id;
            }
            export function kofUiFontNewBold(family, size, bold) {
                window.__kofFonts = window.__kofFonts || {};
                const id = ++__kofFontSeq;
                window.__kofFonts[id] = { family, size, bold: !!bold };
                return id;
            }
            export function kofUiWidgetSetFont(widget, fontId) {
                const n = window.__kofNodes && window.__kofNodes[widget];
                const f = window.__kofFonts && window.__kofFonts[fontId];
                if (n && f) {
                    n.style.fontFamily = '"' + f.family + '", system-ui, sans-serif';
                    n.style.fontSize = f.size + "px";
                    n.style.fontWeight = f.bold ? "700" : "400";
                    n.dataset.kofFont = String(fontId);
                }
            }
            export function kofUiWidgetFont(widget) {
                const n = window.__kofNodes && window.__kofNodes[widget];
                return n && n.dataset.kofFont ? parseInt(n.dataset.kofFont, 10) : -1;
            }

            export function kofUiLabelNew(text) {
                if (typeof document === "undefined") {
                    return -1;
                }
                const span = document.createElement("span");
                span.textContent = text;
                span.className = "kof-label";
                if (typeof window.__kofNodes === "undefined") {
                    window.__kofNodes = {};
                }
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = span;
                return id;
            }

            export function kofUiLabelSetText(label, text) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    window.__kofNodes[label].textContent = text;
                }
            }

            export function kofUiLabelText(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    return window.__kofNodes[label].textContent;
                }
                return "";
            }

            export function kofUiLabelSetFontSize(label, size) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    window.__kofNodes[label].style.fontSize = size + "px";
                }
            }

            export function kofUiLabelFontSize(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    const fs = window.__kofNodes[label].style.fontSize;
                    if (typeof fs === "string" && fs.endsWith("px")) {
                        const v = parseInt(fs, 10);
                        if (!isNaN(v)) return v;
                    }
                }
                return 0;
            }

            export function kofUiLabelSetBold(label, bold) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    window.__kofNodes[label].style.fontWeight = bold ? "bold" : "normal";
                }
            }

            export function kofUiLabelBold(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    return window.__kofNodes[label].style.fontWeight === "bold" ? 1 : 0;
                }
                return 0;
            }

            export function kofUiLabelSetColor(label, color) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    window.__kofNodes[label].style.color = kofUiColorToCss(color);
                }
            }

            export function kofUiLabelColor(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    const css = window.__kofNodes[label].style.color;
                    const m = typeof css === "string" ? css.match(/rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)/) : null;
                    if (m) {
                        return ((parseInt(m[1], 10) << 24) | (parseInt(m[2], 10) << 16)
                                | (parseInt(m[3], 10) << 8) | 0xFF) >>> 0;
                    }
                }
                return 0;
            }

            export function kofUiLabelRemove(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    const node = window.__kofNodes[label];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[label];
                }
            }

            function kofUiCreateNode(tag, className) {
                if (typeof document === "undefined") {
                    return -1;
                }
                const el = document.createElement(tag);
                el.className = className;
                if (typeof window.__kofNodes === "undefined") {
                    window.__kofNodes = {};
                }
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = el;
                return id;
            }

            function kofUiSetAction(id, action) {
                if (!action || typeof document === "undefined") {
                    return;
                }
                window.__kofActions = window.__kofActions || {};
                window.__kofActions[id] = action;
                const node = window.__kofNodes[id];
                if (node && typeof node.addEventListener === "function") {
                    node.addEventListener("click", function () {
                        action.invoke();
                    });
                }
            }

            export function kofUiButtonNew(text) {
                const id = kofUiCreateNode("button", "kof-button");
                if (id < 0) {
                    return -1;
                }
                window.__kofNodes[id].textContent = text;
                return id;
            }

            export function kofUiButtonNewAction(text, action) {
                const id = kofUiCreateNode("button", "kof-button");
                if (id < 0) {
                    return -1;
                }
                window.__kofNodes[id].textContent = text;
                kofUiSetAction(id, action);
                return id;
            }

            export function kofUiButtonSetText(button, text) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[button]) {
                    window.__kofNodes[button].textContent = text;
                }
            }

            export function kofUiButtonText(button) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[button]) {
                    return window.__kofNodes[button].textContent;
                }
                return "";
            }

            export function kofUiButtonRemove(button) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[button]) {
                    const node = window.__kofNodes[button];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[button];
                    if (window.__kofActions) {
                        delete window.__kofActions[button];
                    }
                }
            }

            export function kofUiInputNew(text) {
                const id = kofUiCreateNode("input", "kof-input");
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                node.type = "text";
                node.value = text;
                return id;
            }

            export function kofUiInputSetText(input, text) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    window.__kofNodes[input].value = text;
                }
            }

            export function kofUiInputText(input) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    return window.__kofNodes[input].value;
                }
                return "";
            }

            export function kofUiInputRemove(input) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    const node = window.__kofNodes[input];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[input];
                }
            }

            export function kofUiColumnNew(ids) {
                const id = kofUiCreateNode("div", "kof-column");
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                if (ids) {
                    for (const childId of ids) {
                        const child = window.__kofNodes[childId];
                        if (child) {
                            node.appendChild(child);
                        }
                    }
                }
                return id;
            }

            export function kofUiRowNew(ids) {
                const id = kofUiCreateNode("div", "kof-row");
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                if (ids) {
                    for (const childId of ids) {
                        const child = window.__kofNodes[childId];
                        if (child) {
                            node.appendChild(child);
                        }
                    }
                }
                return id;
            }

            export function kofUiStyleNew(background, foreground, padding, radius) {
                if (typeof document === "undefined") {
                    return -1;
                }
                window.__kofStyles = window.__kofStyles || {};
                const id = Object.keys(window.__kofStyles).length + 1;
                window.__kofStyles[id] = { background: background, foreground: foreground,
                        padding: padding, radius: radius };
                return id;
            }

            export function kofUiViewNew(style) {
                const id = kofUiCreateNode("div", "kof-view");
                if (id < 0) {
                    return -1;
                }
                const s = window.__kofStyles && window.__kofStyles[style];
                const node = window.__kofNodes[id];
                if (s) {
                    const css = node.style;
                    if (s.background !== 0) {
                        css.backgroundColor = kofUiColorToCss(s.background);
                    }
                    if (s.foreground !== 0) {
                        css.color = kofUiColorToCss(s.foreground);
                    }
                    if (s.padding > 0) {
                        css.padding = s.padding + "px";
                    }
                    if (s.radius > 0) {
                        css.borderRadius = s.radius + "px";
                    }
                }
                return id;
            }

            export function kofUiViewBind(view, child) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[view]
                        && window.__kofNodes[child]) {
                    window.__kofNodes[view].appendChild(window.__kofNodes[child]);
                }
            }

            // ── Layout primitives (docs/ui/architecture.md §2.8) ──────
            // CSS-first: the framework never computes pixel positions; each
            // primitive maps to a flex/grid CSS pattern.
            function kofUiLayoutContainerNew(tag, className, ids, extraStyle) {
                const id = kofUiCreateNode(tag, className);
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                if (extraStyle) {
                    for (const k in extraStyle) node.style[k] = extraStyle[k];
                }
                if (ids) {
                    for (const childId of ids) {
                        const child = window.__kofNodes[childId];
                        if (child) {
                            node.appendChild(child);
                        }
                    }
                }
                return id;
            }

            export function kofUiBoxNew(ids) {
                return kofUiLayoutContainerNew("div", "kof-box kof-view", ids);
            }

            export function kofUiStackNew(ids) {
                // overlapping children (z-stack): all children in the same cell
                return kofUiLayoutContainerNew("div", "kof-stack", ids,
                        { display: "grid" });
            }

            export function kofUiWrapNew(ids) {
                return kofUiLayoutContainerNew("div", "kof-wrap", ids,
                        { display: "flex", flexDirection: "row", flexWrap: "wrap" });
            }

            export function kofUiGridNew(cols, ids) {
                return kofUiLayoutContainerNew("div", "kof-grid", ids,
                        { display: "grid",
                          gridTemplateColumns: "repeat(" + (cols > 0 ? cols : 1) + ", 1fr)" });
            }

            export function kofUiSpacerNew(size) {
                return kofUiLayoutContainerNew("div", "kof-spacer", null,
                        { flex: size > 0 ? String(size) : "1" });
            }

            export function kofUiCenterNew(ids) {
                return kofUiLayoutContainerNew("div", "kof-center", ids,
                        { display: "flex", alignItems: "center", justifyContent: "center" });
            }

            export function kofUiAlignNew(horizontal, vertical, ids) {
                // horizontal/vertical: 0=start, 1=center, 2=end
                const justify = horizontal === 1 ? "center" : horizontal === 2 ? "flex-end" : "flex-start";
                const align = vertical === 1 ? "center" : vertical === 2 ? "flex-end" : "flex-start";
                return kofUiLayoutContainerNew("div", "kof-align", ids,
                        { display: "flex", justifyContent: justify, alignItems: align });
            }

            export function kofUiViewRemove(view) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[view]) {
                    const node = window.__kofNodes[view];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[view];
                }
            }

            export function kofUiColorToCss(color) {
                const r = (color >>> 24) & 0xFF;
                const g = (color >>> 16) & 0xFF;
                const b = (color >>> 8) & 0xFF;
                const a = color & 0xFF;
                if (a === 255) {
                    return "rgb(" + r + ", " + g + ", " + b + ")";
                }
                return "rgba(" + r + ", " + g + ", " + b + ", " + a + ")";
            }

            export function kofListNew() {
                return [];
            }

            export function kofChannelNew() {
                return { items: [], resolvers: [] };
            }

            export function kofChannelSend(chan, value) {
                if (chan.resolvers.length > 0) {
                    chan.resolvers.shift()(value);
                } else {
                    chan.items.push(value);
                }
            }

            export function kofChannelReceive(chan) {
                if (chan.items.length > 0) {
                    return Promise.resolve(chan.items.shift());
                }
                return new Promise(resolve => chan.resolvers.push(resolve));
            }

            export function kofListAdd(list, value) {
                list.push(value);
            }

            export function kofListGet(list, index) {
                if (index < 0 || index >= list.length) {
                    throw new Error("Index out of bounds: " + index + " (size " + list.length + ")");
                }
                return list[index];
            }

            export function kofListSet(list, index, value) {
                if (index < 0 || index >= list.length) {
                    throw new Error("Index out of bounds: " + index + " (size " + list.length + ")");
                }
                list[index] = value;
            }

            export function kofListSize(list) {
                return list.length;
            }

            export function kofListContains(list, value) {
                return list.includes(value) ? 1 : 0;
            }

            export function kofListIsEmpty(list) {
                return list.length === 0 ? 1 : 0;
            }

            export function kofListRemove(list, index) {
                if (index < 0 || index >= list.length) {
                    throw new Error("Index out of bounds: " + index + " (size " + list.length + ")");
                }
                return list.splice(index, 1)[0];
            }

            export function kofListClear(list) {
                list.length = 0;
            }

            export function kofMapNew() {
                return new Map();
            }

            export function kofMapPut(map, key, value) {
                const prev = map.get(key);
                map.set(key, value);
                return prev === undefined ? null : prev;
            }

            export function kofMapGet(map, key) {
                const v = map.get(key);
                return v === undefined ? null : v;
            }

            export function kofMapRemove(map, key) {
                const v = map.get(key);
                map.delete(key);
                return v === undefined ? null : v;
            }

            export function kofMapContains(map, key) {
                return map.has(key) ? 1 : 0;
            }

            export function kofMapSize(map) {
                return map.size;
            }

            export function kofMapClear(map) {
                map.clear();
            }

            export function kofMapIsEmpty(map) {
                return map.size === 0 ? 1 : 0;
            }

            export function kofMapKeys(map) {
                return Array.from(map.keys());
            }

            export function kofMapValues(map) {
                return Array.from(map.values());
            }

            export function kofSetNew() {
                return new Set();
            }

            export function kofSetAdd(set, value) {
                const had = set.has(value);
                set.add(value);
                return had ? 0 : 1;
            }

            export function kofSetContains(set, value) {
                return set.has(value) ? 1 : 0;
            }

            export function kofSetRemove(set, value) {
                return set.delete(value) ? 1 : 0;
            }

            export function kofSetSize(set) {
                return set.size;
            }

            export function kofSetClear(set) {
                set.clear();
            }

            export function kofSetIsEmpty(set) {
                return set.size === 0 ? 1 : 0;
            }

            export function kofListMap(list, fn) {
                return list.map(x => (typeof fn.invoke === 'function' ? fn.invoke(x) : fn(x)));
            }

            export function kofListFilter(list, fn) {
                return list.filter(x => !!(typeof fn.invoke === 'function' ? fn.invoke(x) : fn(x)));
            }

            export function kofListReduce(list, initial, fn) {
                return list.reduce((acc, x) => (typeof fn.invoke === 'function' ? fn.invoke(acc, x) : fn(acc, x)), initial);
            }

            let kofActiveTasks = 0;
            globalThis.kofActiveTasks = kofActiveTasks;

            export function kofSpawn(task) {
                kofActiveTasks++;
                globalThis.kofActiveTasks = kofActiveTasks;
                Promise.resolve().then(() => {
                    return (task && typeof task.invoke === "function") ? task.invoke() : task;
                }).catch(err => {
                    const msg = (err && err.message !== undefined) ? err.message : String(err);
                    (console.error || console.log)("spawn task failed: " + msg);
                }).finally(() => {
                    kofActiveTasks--;
                    globalThis.kofActiveTasks = kofActiveTasks;
                });
            }

            export function kofSpawnResult(task) {
                kofActiveTasks++;
                globalThis.kofActiveTasks = kofActiveTasks;
                const handle = { done: false, value: undefined, error: undefined, cancelled: false };
                const promise = Promise.resolve().then(() => {
                    return (task && typeof task.invoke === "function") ? task.invoke() : task;
                }).then(value => {
                    handle.done = true;
                    handle.value = value;
                    return value;
                }).catch(err => {
                    handle.done = true;
                    handle.error = err;
                    throw err;
                }).finally(() => {
                    kofActiveTasks--;
                    globalThis.kofActiveTasks = kofActiveTasks;
                });
                handle.promise = promise;
                promise.catch(() => {});
                return handle;
            }

            export function kofPoll(handle) {
                return handle && handle.done && !handle.error ? handle.value : null;
            }

            export function kofDone(handle) {
                return (handle && handle.done) ? 1 : 0;
            }

            export function kofCancel(handle) {
                if (!handle) return 0;
                const wasDone = handle.done;
                if (!wasDone) handle.cancelled = true;
                return wasDone ? 0 : 1;
            }

            export function kofCancelled() {
                // Sem thread-local em JS embutido: não há "task atual" para
                // consultar — cancelamento cooperativo via handle.cancelled.
                return 0;
            }

            export function kofSelectAny(handles) {
                return Promise.race((handles || []).map(h =>
                    (h && h.promise !== undefined) ? h.promise : Promise.resolve(h)));
            }

            export function kofAwait(handle) {
                if (handle != null && handle.promise !== undefined) {
                    return handle.promise;
                }
                return handle;
            }

            export async function kofAwaitTimeout(handle, timeoutMs) {
                const deadline = Date.now() + timeoutMs;
                while (Date.now() < deadline) {
                    if (handle && handle.done) {
                        if (handle.error) {
                            const e = handle.error;
                            throw (e && e.message !== undefined) ? e.message : String(e);
                        }
                        return handle.value;
                    }
                    await Promise.resolve();
                }
                throw "await timeout after " + timeoutMs + "ms";
            }

            export function kofWebStub() {
                // JS stub for kof.web/db — keeps KofJS compilable; real impl is JVM/Native
                return 0;
            }

            let kofHttpTimeoutSec = 10;
            let kofHttpRetries = 0;
            let kofHttpCircuitTrips = 0;
            let kofHttpCircuitFailures = 0;
            let kofHttpCircuitOpenUntil = 0;
            const KOF_HTTP_CIRCUIT_WINDOW_MS = 30000;
            export function kofHttpTimeoutSet(sec) { kofHttpTimeoutSec = sec; }
            export function kofHttpRetrySet(n) { kofHttpRetries = Math.max(0, n | 0); }
            export function kofHttpCircuitSet(trips) {
                kofHttpCircuitTrips = Math.max(0, trips | 0);
                if (kofHttpCircuitTrips <= 0) { kofHttpCircuitFailures = 0; kofHttpCircuitOpenUntil = 0; }
            }
            function kofHttpCircuitOpen() {
                if (kofHttpCircuitOpenUntil === 0) return false;
                if (Date.now() >= kofHttpCircuitOpenUntil) { kofHttpCircuitOpenUntil = 0; return false; }
                return true;
            }
            function kofHttpCircuitRecordFailure() {
                if (kofHttpCircuitTrips <= 0) return;
                if (++kofHttpCircuitFailures >= kofHttpCircuitTrips) {
                    kofHttpCircuitOpenUntil = Date.now() + KOF_HTTP_CIRCUIT_WINDOW_MS;
                }
            }
            function kofHttpCircuitRecordSuccess() {
                kofHttpCircuitFailures = 0;
                kofHttpCircuitOpenUntil = 0;
            }
            export function kofHttpGet(url) { return kofHttpRequest(url, "GET", null, null); }
            export function kofHttpGetHeaders(url, headers) { return kofHttpRequest(url, "GET", headers, null); }
            export function kofHttpDelete(url) { return kofHttpRequest(url, "DELETE", null, null); }
            export function kofHttpDeleteHeaders(url, headers) { return kofHttpRequest(url, "DELETE", headers, null); }
            export function kofHttpOptions(url) { return kofHttpRequest(url, "OPTIONS", null, null); }
            export function kofHttpOptionsHeaders(url, headers) { return kofHttpRequest(url, "OPTIONS", headers, null); }
            export function kofHttpPost(url, body) { return kofHttpRequest(url, "POST", null, body); }
            export function kofHttpPostHeaders(url, body, headers) { return kofHttpRequest(url, "POST", headers, body); }
            export function kofHttpPut(url, body) { return kofHttpRequest(url, "PUT", null, body); }
            export function kofHttpPutHeaders(url, body, headers) { return kofHttpRequest(url, "PUT", headers, body); }
            export function kofHttpPatch(url, body) { return kofHttpRequest(url, "PATCH", null, body); }
            export function kofHttpPatchHeaders(url, body, headers) { return kofHttpRequest(url, "PATCH", headers, body); }
            export function kofHttpStatus(url) {
                try {
                    const HttpClient = Java.type('java.net.http.HttpClient');
                    const HttpRequest = Java.type('java.net.http.HttpRequest');
                    const URI = Java.type('java.net.URI');
                    const Duration = Java.type('java.time.Duration');
                    let client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(kofHttpTimeoutSec)).build();
                    let builder = HttpRequest.newBuilder().uri(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(kofHttpTimeoutSec));
                    let req = builder.build();
                    let resp = client.send(req, Java.type('java.net.http.HttpResponse$BodyHandlers').discarding());
                    return resp.statusCode();
                } catch(e) { return 0; }
            }
            function kofHttpRequest(url, method, headers, body) {
                if (kofHttpCircuitOpen()) {
                    throw new Error("kof.http circuit open (fail fast): " + url);
                }
                let lastErr = null;
                const attempts = kofHttpRetries + 1;
                for (let attempt = 0; attempt < attempts; attempt++) {
                    try {
                        // Prefer Java HttpClient via GraalJS interop (synchronous, works in KofJsRunner)
                        if (typeof Java !== 'undefined' && Java.type) {
                            const HttpClient = Java.type('java.net.http.HttpClient');
                            const HttpRequest = Java.type('java.net.http.HttpRequest');
                            const URI = Java.type('java.net.URI');
                            const Duration = Java.type('java.time.Duration');
                            let client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(kofHttpTimeoutSec)).build();
                            let builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(kofHttpTimeoutSec));
                            if (headers) {
                                let lines = headers.split("\\n");
                                for (let line of lines) {
                                    let idx = line.indexOf(":");
                                    if (idx > 0) builder.header(line.substring(0, idx).trim(), line.substring(idx+1).trim());
                                }
                            }
                            let publisher = body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody();
                            builder.method(method, publisher);
                            let req = builder.build();
                            let resp = client.send(req, Java.type('java.net.http.HttpResponse$BodyHandlers').ofString());
                            if (resp.statusCode() >= 500) {
                                lastErr = new Error("HTTP " + resp.statusCode() + " from " + url);
                                kofHttpCircuitRecordFailure();
                                continue;
                            }
                            kofHttpCircuitRecordSuccess();
                            return resp.body() != null ? resp.body() : "";
                        }
                        // Fallback to fetch if Java interop not available (Node/Browser)
                        if (typeof fetch !== 'undefined') {
                            // synchronous fallback not possible - use deasync via Atomics if available
                            // For MVP, do blocking via fetch sync is not supported; return empty
                            kofHttpCircuitRecordSuccess();
                            return "";
                        }
                        kofHttpCircuitRecordSuccess();
                        return "";
                    } catch(e) {
                        lastErr = e;
                        kofHttpCircuitRecordFailure();
                    }
                }
                if (lastErr == null) lastErr = new Error("request failed: " + url);
                throw lastErr;
            }

            export function kofSchedulerEvery(ms, fn) {
                // delega ao kofTimeInterval: fila cooperativa bombeada por
                // kofTimeSleep no GraalJS (sem setInterval), nativa no browser
                return kofTimeInterval(ms, fn);
            }
            export function kofSchedulerAt(cron, fn) {
                return kofSchedulerEvery(60000, fn);
            }
            export function kofSchedulerCancel(id) { kofTimeCancel(id); }

            // ── Web runtime (WEB001) — GraalJS HttpServer com handler invoke
            // Handler lambda tem metodo invoke(); usamos Value para interop.
            const kofWebApps = new Map();
            let kofWebPort = 8080;
            let kofWebServer = null;
            function kofWebHandleRequest(exchange) {
                const path = exchange.getRequestURI().getPath();
                const method = exchange.getRequestMethod();
                const handler = kofWebApps.get(method + ":" + path);
                if (!handler) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                try {
                    const body = exchange.getRequestBodyBodyHandlers ? exchange.getRequestBodyBodyHandlers().fileDownload() : null;
                    const ctx = {
                        request: {
                            method: method,
                            path: path,
                            query: exchange.getRequestURI().getQuery(),
                            headers: exchange.getRequestHeaders()
                        },
                        body: body,
                        response: {
                            status: function(code, text) {
                                exchange.sendResponseHeaders(code, (text || "").length);
                                const os = exchange.getResponseBody();
                                os.write(text ? String.toBytes(text) : new Uint8Array(0));
                                os.close();
                            },
                            header: function(name, value) {
                                exchange.getResponseHeaders().set(name, value);
                            }
                        }
                    };
                    if (typeof handler.invoke === 'function') handler.invoke(ctx);
                    else if (typeof handler === 'function') handler(ctx);
                } catch(e) {
                    exchange.sendResponseHeaders(500, String.toBytes(String(e)));
                }
            }
            export function kofWebAppNew() {
                const app = {
                    handlers: new Map(),
                    _register: function(method, path, handler) {
                        this.handlers.set(method + ":" + path, handler);
                    }
                };
                const id = "app_" + kofWebApps.size;
                kofWebApps.set(id, app);
                return id;
            }
            export function kofWebRoute(appId, method, path, handler) {
                const app = kofWebApps.get(appId);
                if (!app) throw new Error("Invalid app handle: " + appId);
                app._register(method, path, handler);
                return 0;
            }
            export function kofWebListen(appId, port) {
                const app = kofWebApps.get(appId);
                if (!app) throw new Error("Invalid app handle: " + appId);
                kofWebPort = port | 0 || 8080;
                const HttpServer = Java.type('com.sun.net.httpserver.HttpServer');
                kofWebServer = HttpServer.create(Java.type('java.net.InetSocketAddress').create(0, kofWebPort), 0);
                for (const [key, handler] of app.handlers) {
                    const [method, path] = key.split(":");
                    kofWebServer.createContext(path, (exchange) => kofWebHandleRequest(exchange));
                }
                kofWebServer.setExecutor(null);
                kofWebServer.start();
                return 0;
            }

            export function kofEnumValueOf(values, name) {
                if (values != null && name != null) {
                    for (const v of values) {
                        if (v === name) return v;
                    }
                }
                return null;
            }

            export function kofNow() {
                return Date.now();
            }

            export function kofTimeNow() {
                return Date.now();
            }

            export function kofTimeSleep(ms) {
                const end = Date.now() + ms;
                // bombeia a fila cooperativa de timers durante o wait (GraalJS
                // single-thread: sem isso, time.interval nunca dispara)
                while (Date.now() < end) {
                    kofTimePump();
                }
                kofTimePump();
            }

            // ── Cooperative timers (TIME001 fechado): GraalJS não tem
            // event loop nativo nem setInterval, então os jobs vivem numa
            // fila bombeada por kofTimeSleep (que já bloqueia). Em browser/
            // Node, onde setInterval existe, os timers disparam assíncronos.
            const kofTimeJobs = new Map();
            const kofTimeSeq = { value: 0 };
            function kofTimeRunJob(fn) {
                if (typeof fn.invoke === 'function') fn.invoke();
                else if (typeof fn === 'function') fn();
            }
            export function kofTimeInterval(ms, fn) {
                if (typeof setInterval === 'function') {
                    return "n" + String(setInterval(() => kofTimeRunJob(fn), ms));
                }
                const id = "c" + (++kofTimeSeq.value);
                kofTimeJobs.set(id, { ms: ms, run: () => kofTimeRunJob(fn), next: Date.now() + ms });
                return id;
            }
            function kofTimePump() {
                const now = Date.now();
                for (const [id, job] of kofTimeJobs) {
                    if (now >= job.next) {
                        job.next = now + job.ms;
                        job.run();
                    }
                }
            }
            export function kofTimeCancel(id) {
                const key = String(id);
                if (key.charAt(0) === "n") {
                    if (typeof clearInterval === 'function') clearInterval(Number(key.substring(1)));
                    return;
                }
                kofTimeJobs.delete(key);
            }

            export function kofConfigGet(key) {
                return kofConfigLookup(key);
            }

            export function kofConfigEnv(key) {
                return kofConfigLookup(key);
            }

            export function kofConfigHas(key) {
                return kofConfigLookup(key) != null ? 1 : 0;
            }

            export function kofConfigRequired(key) {
                const v = kofConfigLookup(key);
                if (v == null) {
                    throw new Error("Kof config: missing required key '" + key + "'");
                }
                return v;
            }

            export function kofConfigStr(key, def) {
                const v = kofConfigLookup(key);
                return v != null ? v : def;
            }

            export function kofConfigInt(key, def) {
                const v = kofConfigLookup(key);
                if (v == null) return def | 0;
                const n = parseInt(v, 10);
                return isNaN(n) ? def | 0 : n | 0;
            }

            export function kofConfigLong(key, def) {
                const v = kofConfigLookup(key);
                if (v == null) return def;
                const n = parseInt(v, 10);
                return isNaN(n) ? def : n;
            }

            export function kofConfigBool(key, def) {
                const v = kofConfigLookup(key);
                if (v == null) return def ? 1 : 0;
                const s = String(v).toLowerCase();
                if (s === 'true' || s === '1' || s === 'yes') return 1;
                if (s === 'false' || s === '0' || s === 'no') return 0;
                return def ? 1 : 0;
            }

            // P2 (docs/stdlib-config.md §8.2): interpolação ${key} —
            // resolve referências entre chaves; ciclo/missing → literal.
            function kofConfigInterpolate(value) {
                if (!value || !value.includes('${')) return value;
                const seen = new Set();
                let current = value;
                for (let depth = 0; depth < 16; depth++) {
                    const start = current.indexOf('${');
                    if (start < 0) break;
                    const end = current.indexOf('}', start + 2);
                    if (end < 0) break;
                    const ref = current.slice(start + 2, end);
                    const resolved = kofConfigLookup(ref);
                    if (resolved == null || seen.has(ref)) return value;
                    seen.add(ref);
                    current = current.slice(0, start) + resolved + current.slice(end + 1);
                }
                return current;
            }

            function kofConfigLookup(key) {
                try {
                    if (typeof process !== 'undefined' && process.env) {
                        if (key in process.env) return process.env[key];
                        const kofKey = 'KOF_' + key.replace(/[^a-zA-Z0-9]/g, '_').toUpperCase();
                        if (kofKey in process.env) return process.env[kofKey];
                        const flat = key.replace(/\\./g, '_').toUpperCase();
                        if (flat in process.env) return process.env[flat];
                    }
                } catch (e) {}
                try {
                    if (typeof globalThis !== 'undefined' && globalThis.__kofConfig && key in globalThis.__kofConfig) {
                        return globalThis.__kofConfig[key];
                    }
                } catch (e) {}
                try {
                    // arquivo kof.config no diretório de trabalho (precedência 4)
                    if (!globalThis.__kofConfigFile) {
                        globalThis.__kofConfigFile = {};
                        if (typeof kof_platform !== 'undefined' && kof_platform.readFile) {
                            const text = kof_platform.readFile('kof.config');
                            if (text) {
                                for (const line of String(text).split('\\n')) {
                                    const t = line.trim();
                                    if (!t || t.startsWith('#')) continue;
                                    const eq = t.indexOf('=');
                                    if (eq <= 0) continue;
                                    globalThis.__kofConfigFile[t.slice(0, eq).trim()] = t.slice(eq + 1).trim();
                                }
                            }
                        }
                    }
                    if (key in globalThis.__kofConfigFile) return kofConfigInterpolate(globalThis.__kofConfigFile[key]);
                } catch (e) {}
                return null;
            }

            """;

    private static final String UI_SUPPORT_RUNTIME = """
            export const kofMqSubs = new Map();
            export const kofMqQueues = new Map();
            export let kofMqSeq = 0;
            export function kofMqPublish(topic, msg) {
                const subs = kofMqSubs.get(topic);
                if (subs) {
                    for (const fn of [...subs]) {
                        try {
                            if (typeof fn.invoke === 'function') fn.invoke(msg);
                            else if (typeof fn === 'function') fn(msg);
                        } catch (e) {}
                    }
                }
            }
            export function kofMqSubscribe(topic, fn) {
                if (!kofMqSubs.has(topic)) kofMqSubs.set(topic, []);
                kofMqSubs.get(topic).push(fn);
            }
            export function kofMqUnsubscribe(topic, fn) {
                const subs = kofMqSubs.get(topic);
                if (!subs) return;
                const idx = subs.indexOf(fn);
                if (idx >= 0) subs.splice(idx, 1);
            }
            export function kofMqQueue() {
                const id = 'q-' + (++kofMqSeq);
                kofMqQueues.set(id, []);
                return id;
            }
            export function kofMqPush(queue, msg) {
                const q = kofMqQueues.get(queue);
                if (q) q.push(msg);
            }
            export function kofMqPop(queue) {
                const q = kofMqQueues.get(queue);
                if (!q || q.length === 0) return null;
                return q.shift();
            }
            export function kofMqQueueSize(queue) {
                const q = kofMqQueues.get(queue);
                return q ? q.length : 0;
            }

            export const kofCacheData = new Map();
            export const kofCacheExpiry = new Map();
            export function kofCacheGet(key) {
                const exp = kofCacheExpiry.get(key);
                if (exp != null && exp !== 0 && Date.now() > exp) {
                    kofCacheData.delete(key);
                    kofCacheExpiry.delete(key);
                    return null;
                }
                return kofCacheData.get(key) ?? null;
            }
            export function kofCacheSet(key, value) {
                kofCacheData.set(key, value);
                kofCacheExpiry.delete(key);
            }
            export function kofCacheSetTtl(key, value, ttl) {
                kofCacheData.set(key, value);
                if (ttl > 0) {
                    kofCacheExpiry.set(key, Date.now() + ttl * 1000);
                } else {
                    kofCacheExpiry.delete(key);
                }
            }
            export function kofCacheTtl(key) {
                const exp = kofCacheExpiry.get(key);
                if (exp == null || exp === 0) return -1;
                const remaining = exp - Date.now();
                if (remaining <= 0) {
                    kofCacheData.delete(key);
                    kofCacheExpiry.delete(key);
                    return -1;
                }
                return Math.floor(remaining / 1000);
            }
            export function kofCacheDelete(key) {
                kofCacheData.delete(key);
                kofCacheExpiry.delete(key);
            }
            export function kofCacheClear() {
                kofCacheData.clear();
                kofCacheExpiry.clear();
            }

            // ── kof.security (docs/security.md §5) ────────────────────

            function kofSecBytesToHex(bytes) {
                let hex = '';
                for (let i = 0; i < bytes.length; i++) {
                    hex += bytes[i].toString(16).padStart(2, '0');
                }
                return hex;
            }

            function kofSecUtf8(str) {
                const bytes = [];
                for (let i = 0; i < str.length; i++) {
                    let code = str.codePointAt(i);
                    if (code > 0xFFFF) i++;
                    if (code < 0x80) {
                        bytes.push(code);
                    } else if (code < 0x800) {
                        bytes.push(0xC0 | (code >> 6), 0x80 | (code & 0x3F));
                    } else if (code < 0x10000) {
                        bytes.push(0xE0 | (code >> 12), 0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
                    } else {
                        bytes.push(0xF0 | (code >> 18), 0x80 | ((code >> 12) & 0x3F),
                                0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
                    }
                }
                return Uint8Array.from(bytes);
            }

            function kofSecUtf8Decode(bytes) {
                let out = '';
                for (let i = 0; i < bytes.length;) {
                    const b = bytes[i];
                    if (b < 0x80) {
                        out += String.fromCharCode(b);
                        i += 1;
                    } else if (b < 0xE0) {
                        out += String.fromCharCode(((b & 0x1F) << 6) | (bytes[i + 1] & 0x3F));
                        i += 2;
                    } else if (b < 0xF0) {
                        out += String.fromCharCode(((b & 0x0F) << 12) | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F));
                        i += 3;
                    } else {
                        const cp = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3F) << 12)
                                | ((bytes[i + 2] & 0x3F) << 6) | (bytes[i + 3] & 0x3F);
                        out += String.fromCodePoint(cp);
                        i += 4;
                    }
                }
                return out;
            }

            function kofSecSha256Bytes(msg) {
                const K = [
                    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
                    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
                    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
                    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
                    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
                    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
                    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
                    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
                ];
                const len = msg.length;
                const bitLen = len * 8;
                const padded = new Uint8Array(((len + 8) >>> 6 << 6) + 64);
                padded.set(msg);
                padded[len] = 0x80;
                const view = new DataView(padded.buffer);
                view.setUint32(padded.length - 8, Math.floor(bitLen / 0x100000000));
                view.setUint32(padded.length - 4, bitLen >>> 0);
                let h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
                let h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;
                const w = new Uint32Array(64);
                for (let off = 0; off < padded.length; off += 64) {
                    for (let i = 0; i < 16; i++) {
                        w[i] = view.getUint32(off + i * 4);
                    }
                    for (let i = 16; i < 64; i++) {
                        const s0 = ((w[i - 15] >>> 7) | (w[i - 15] << 25)) ^ ((w[i - 15] >>> 18) | (w[i - 15] << 14)) ^ (w[i - 15] >>> 3);
                        const s1 = ((w[i - 2] >>> 17) | (w[i - 2] << 15)) ^ ((w[i - 2] >>> 19) | (w[i - 2] << 13)) ^ (w[i - 2] >>> 10);
                        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
                    }
                    let a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7;
                    for (let i = 0; i < 64; i++) {
                        const S1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7));
                        const ch = (e & f) ^ (~e & g);
                        const t1 = (h + S1 + ch + K[i] + w[i]) >>> 0;
                        const S0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10));
                        const maj = (a & b) ^ (a & c) ^ (b & c);
                        const t2 = (S0 + maj) >>> 0;
                        h = g; g = f; f = e; e = (d + t1) >>> 0; d = c; c = b; b = a; a = (t1 + t2) >>> 0;
                    }
                    h0 = (h0 + a) >>> 0; h1 = (h1 + b) >>> 0; h2 = (h2 + c) >>> 0; h3 = (h3 + d) >>> 0;
                    h4 = (h4 + e) >>> 0; h5 = (h5 + f) >>> 0; h6 = (h6 + g) >>> 0; h7 = (h7 + h) >>> 0;
                }
                const out = new Uint8Array(32);
                const outView = new DataView(out.buffer);
                outView.setUint32(0, h0); outView.setUint32(4, h1); outView.setUint32(8, h2); outView.setUint32(12, h3);
                outView.setUint32(16, h4); outView.setUint32(20, h5); outView.setUint32(24, h6); outView.setUint32(28, h7);
                return out;
            }

            function kofSecHmacRaw(keyBytes, dataBytes) {
                const blockSize = 64;
                let key = keyBytes;
                if (key.length > blockSize) {
                    key = kofSecSha256Bytes(key);
                }
                const ipad = new Uint8Array(blockSize);
                const opad = new Uint8Array(blockSize);
                for (let i = 0; i < blockSize; i++) {
                    ipad[i] = (key[i] || 0) ^ 0x36;
                    opad[i] = (key[i] || 0) ^ 0x5c;
                }
                const inner = new Uint8Array(ipad.length + dataBytes.length);
                inner.set(ipad);
                inner.set(dataBytes, ipad.length);
                const innerHash = kofSecSha256Bytes(inner);
                const outer = new Uint8Array(opad.length + innerHash.length);
                outer.set(opad);
                outer.set(innerHash, opad.length);
                return kofSecSha256Bytes(outer);
            }

            export function kofSecSha256(data) {
                return kofSecBytesToHex(kofSecSha256Bytes(kofSecUtf8(data)));
            }

            export function kofSecSha512(data) {
                return kofSecBytesToHex(kofSecSha512Bytes(kofSecUtf8(data)));
            }

            function kofSecSha512Bytes(msg) {
                return kofSecSha512Impl(msg);
            }

            function kofSecSha512Impl(msg) {
                // compact re-entry point so hmac can reuse it
                const K = [
                    0x428a2f98d728ae22n, 0x7137449123ef65cdn, 0xb5c0fbcfec4d3b2fn, 0xe9b5dba58189dbbcn,
                    0x3956c25bf348b538n, 0x59f111f1b605d019n, 0x923f82a4af194f9bn, 0xab1c5ed5da6d8118n,
                    0xd807aa98a3030242n, 0x12835b0145706fben, 0x243185be4ee4b28cn, 0x550c7dc3d5ffb4e2n,
                    0x72be5d74f27b896fn, 0x80deb1fe3b1696b1n, 0x9bdc06a725c71235n, 0xc19bf174cf692694n,
                    0xe49b69c19ef14ad2n, 0xefbe4786384f25e3n, 0x0fc19dc68b8cd5b5n, 0x240ca1cc77ac9c65n,
                    0x2de92c6f592b0275n, 0x4a7484aa6ea6e483n, 0x5cb0a9dcbd41fbd4n, 0x76f988da831153b5n,
                    0x983e5152ee66dfabn, 0xa831c66d2db43210n, 0xb00327c898fb213fn, 0xbf597fc7beef0ee4n,
                    0xc6e00bf33da88fc2n, 0xd5a79147930aa725n, 0x06ca6351e003826fn, 0x142929670a0e6e70n,
                    0x27b70a8546d22ffcn, 0x2e1b21385c26c926n, 0x4d2c6dfc5ac42aedn, 0x53380d139d95b3dfn,
                    0x650a73548baf63den, 0x766a0abb3c77b2a8n, 0x81c2c92e47edaee6n, 0x92722c851482353bn,
                    0xa2bfe8a14cf10364n, 0xa81a664bbc423001n, 0xc24b8b70d0f89791n, 0xc76c51a30654be30n,
                    0xd192e819d6ef5218n, 0xd69906245565a910n, 0xf40e35855771202an, 0x106aa07032bbd1b8n,
                    0x19a4c116b8d2d0c8n, 0x1e376c085141ab53n, 0x2748774cdf8eeb99n, 0x34b0bcb5e19b48a8n,
                    0x391c0cb3c5c95a63n, 0x4ed8aa4ae3418acbn, 0x5b9cca4f7763e373n, 0x682e6ff3d6b2b8a3n,
                    0x748f82ee5defb2fcn, 0x78a5636f43172f60n, 0x84c87814a1f0ab72n, 0x8cc702081a6439ecn,
                    0x90befffa23631e28n, 0xa4506cebde82bde9n, 0xbef9a3f7b2c67915n, 0xc67178f2e372532bn,
                    0xca273eceea26619cn, 0xd186b8c721c0c207n, 0xeada7dd6cde0eb1en, 0xf57d4f7fee6ed178n,
                    0x06f067aa72176fban, 0x0a637dc5a2c898a6n, 0x113f9804bef90daen, 0x1b710b35131c471bn,
                    0x28db77f523047d84n, 0x32caab7b40c72493n, 0x3c9ebe0a15c9bebcn, 0x431d67c49c100d4cn,
                    0x4cc5d4becb3e42b6n, 0x597f299cfc657e2an, 0x5fcb6fab3ad6faecn, 0x6c44198c4a475817n
                ];
                const len = msg.length;
                const bitLen = BigInt(len) * 8n;
                const paddedLen = (((len + 16) >>> 7 << 7) + 128);
                const padded = new Uint8Array(paddedLen);
                padded.set(msg);
                padded[len] = 0x80;
                const view = new DataView(padded.buffer);
                view.setBigUint64(paddedLen - 8, bitLen, false);
                let h0 = 0x6a09e667f3bcc908n, h1 = 0xbb67ae8584caa73bn, h2 = 0x3c6ef372fe94f82bn, h3 = 0xa54ff53a5f1d36f1n;
                let h4 = 0x510e527fade682d1n, h5 = 0x9b05688c2b3e6c1fn, h6 = 0x1f83d9abfb41bd6bn, h7 = 0x5be0cd19137e2179n;
                const w = new Array(80);
                for (let off = 0; off < padded.length; off += 128) {
                    for (let i = 0; i < 16; i++) {
                        w[i] = view.getBigUint64(off + i * 8, false);
                    }
                    for (let i = 16; i < 80; i++) {
                        const s0 = ((w[i - 15] >> 1n) | (w[i - 15] << 63n)) ^ ((w[i - 15] >> 8n) | (w[i - 15] << 56n)) ^ (w[i - 15] >> 7n);
                        const s1 = ((w[i - 2] >> 19n) | (w[i - 2] << 45n)) ^ ((w[i - 2] >> 61n) | (w[i - 2] << 3n)) ^ (w[i - 2] >> 6n);
                        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) & 0xffffffffffffffffn;
                    }
                    let a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7;
                    for (let i = 0; i < 80; i++) {
                        const S1 = ((e >> 14n) | (e << 50n)) ^ ((e >> 18n) | (e << 46n)) ^ ((e >> 41n) | (e << 23n));
                        const ch = (e & f) ^ (~e & g);
                        const t1 = (h + S1 + ch + K[i] + w[i]) & 0xffffffffffffffffn;
                        const S0 = ((a >> 28n) | (a << 36n)) ^ ((a >> 34n) | (a << 30n)) ^ ((a >> 39n) | (a << 25n));
                        const maj = (a & b) ^ (a & c) ^ (b & c);
                        const t2 = (S0 + maj) & 0xffffffffffffffffn;
                        h = g; g = f; f = e; e = (d + t1) & 0xffffffffffffffffn; d = c; c = b; b = a; a = (t1 + t2) & 0xffffffffffffffffn;
                    }
                    h0 = (h0 + a) & 0xffffffffffffffffn; h1 = (h1 + b) & 0xffffffffffffffffn; h2 = (h2 + c) & 0xffffffffffffffffn; h3 = (h3 + d) & 0xffffffffffffffffn;
                    h4 = (h4 + e) & 0xffffffffffffffffn; h5 = (h5 + f) & 0xffffffffffffffffn; h6 = (h6 + g) & 0xffffffffffffffffn; h7 = (h7 + h) & 0xffffffffffffffffn;
                }
                const out = new Uint8Array(64);
                const outView = new DataView(out.buffer);
                const hs = [h0, h1, h2, h3, h4, h5, h6, h7];
                for (let i = 0; i < 8; i++) outView.setBigUint64(i * 8, hs[i], false);
                return out;
            }

            export function kofSecHmacSha256(key, data) {
                return kofSecBytesToHex(kofSecHmacRaw(kofSecUtf8(key), kofSecUtf8(data)));
            }

            export function kofSecRandomHex(bytes) {
                if (bytes < 0 || bytes > 4096) {
                    throw new Error("invalid length: " + bytes);
                }
                return kof_platform.randomBytesHex(bytes);
            }

            export function kofSecRandomInt(bound) {
                if (bound <= 0) {
                    throw new Error("bound must be positive");
                }
                return kof_platform.randomInt(bound);
            }

            export function kofSecConstantTimeEquals(a, b) {
                if (a === null || b === null) {
                    return a === b ? 1 : 0;
                }
                const ab = kofSecUtf8(String(a));
                const bb = kofSecUtf8(String(b));
                if (ab.length !== bb.length) {
                    return 0;
                }
                let diff = 0;
                for (let i = 0; i < ab.length; i++) {
                    diff |= ab[i] ^ bb[i];
                }
                return diff === 0 ? 1 : 0;
            }

            export function kofSecRedact(value) {
                if (value === null) {
                    return null;
                }
                if (value.length <= 8) {
                    return "********";
                }
                return value.substring(0, 4) + "********" + value.substring(value.length - 4);
            }

            export function kofSecSecretGet(name) {
                return kof_platform.getenv(name);
            }

            export function kofSecSecretGetDefault(name, fallback) {
                const v = kof_platform.getenv(name);
                return v === null || v === undefined ? fallback : v;
            }

            // password hashing — pbkdf2$sha256$<iterations>$<saltB64>$<hashB64>

            function kofSecConcatBytes(a, b) {
                const out = new Uint8Array(a.length + b.length);
                out.set(a);
                out.set(b, a.length);
                return out;
            }

            function kofSecPbkdf2Raw(passwordBytes, saltBytes, iterations, dkLen) {
                const block1 = kofSecConcatBytes(saltBytes, new Uint8Array([0, 0, 0, 1]));
                let u = kofSecHmacRaw(passwordBytes, block1);
                const t = new Uint8Array(u);
                for (let i = 1; i < iterations; i++) {
                    u = kofSecHmacRaw(passwordBytes, u);
                    for (let j = 0; j < t.length; j++) {
                        t[j] ^= u[j];
                    }
                }
                return t;
            }

            export function kofSecPasswordHash(password) {
                const saltHex = kof_platform.randomBytesHex(16);
                const saltBytes = kofSecHexToBytes(saltHex);
                // The embedded runner delegates PBKDF2 to the platform (fast);
                // standalone JS engines fall back to the pure-JS implementation.
                const dkHex = (typeof kof_platform.pbkdf2Hex === "function")
                        ? kof_platform.pbkdf2Hex(password, saltHex, 600000)
                        : kofSecBytesToHex(kofSecPbkdf2Raw(kofSecUtf8(password), saltBytes, 600000, 32));
                const dk = kofSecHexToBytes(dkHex);
                return "pbkdf2$sha256$600000$" + kofSecB64Encode(saltBytes) + "$" + kofSecB64Encode(dk);
            }

            function kofSecHexToBytes(hex) {
                const out = new Uint8Array(hex.length / 2);
                for (let i = 0; i < out.length; i++) {
                    out[i] = parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                }
                return out;
            }

            export function kofSecPasswordVerify(password, hash) {
                if (hash === null || hash === undefined) {
                    return 0;
                }
                const parts = hash.split("$");
                if (parts.length !== 5 || parts[0] !== "pbkdf2" || parts[1] !== "sha256") {
                    return 0;
                }
                try {
                    const iterations = parseInt(parts[2], 10);
                    const saltBytes = kofSecB64Decode(parts[3]);
                    const expected = kofSecB64Decode(parts[4]);
                    const saltHex = kofSecBytesToHex(saltBytes);
                    const actualHex = (typeof kof_platform.pbkdf2Hex === "function")
                            ? kof_platform.pbkdf2Hex(password, saltHex, iterations)
                            : kofSecBytesToHex(kofSecPbkdf2Raw(kofSecUtf8(password), saltBytes, iterations, expected.length));
                    const actual = kofSecHexToBytes(actualHex);
                    let diff = 0;
                    for (let i = 0; i < expected.length; i++) {
                        diff |= expected[i] ^ actual[i];
                    }
                    return diff === 0 ? 1 : 0;
                } catch (e) {
                    return 0;
                }
            }

            export function kofSecPasswordNeedsRehash(hash) {
                if (hash === null || hash === undefined) {
                    return 1;
                }
                const parts = hash.split("$");
                if (parts.length !== 5 || parts[0] !== "pbkdf2" || parts[1] !== "sha256") {
                    return 1;
                }
                const iterations = parseInt(parts[2], 10);
                return Number.isNaN(iterations) || iterations < 600000 ? 1 : 0;
            }

            // JWT — HS256 only; the algorithm is never taken from the token.

            const KOF_SEC_B64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

            function kofSecB64Encode(bytes) {
                let out = '';
                for (let i = 0; i < bytes.length; i += 3) {
                    const b0 = bytes[i];
                    const b1 = i + 1 < bytes.length ? bytes[i + 1] : -1;
                    const b2 = i + 2 < bytes.length ? bytes[i + 2] : -1;
                    out += KOF_SEC_B64_CHARS[b0 >> 2];
                    out += KOF_SEC_B64_CHARS[((b0 & 3) << 4) | (b1 >= 0 ? b1 >> 4 : 0)];
                    out += b1 >= 0 ? KOF_SEC_B64_CHARS[((b1 & 15) << 2) | (b2 >= 0 ? b2 >> 6 : 0)] : '=';
                    out += b2 >= 0 ? KOF_SEC_B64_CHARS[b2 & 63] : '=';
                }
                return out;
            }

            // strict=true rejeita tamanho não múltiplo de 4 (paridade java.util.Base64).
            // strict=false (default) tolera b64-url sem padding (JWT).
            function kofSecB64Decode(s, strict) {
                if (strict && s.length % 4 !== 0) throw new Error("invalid base64 length");
                const out = [];
                let buffer = 0;
                let bits = 0;
                for (let i = 0; i < s.length; i++) {
                    const c = s.charAt(i);
                    if (c === '=') break;
                    const v = KOF_SEC_B64_CHARS.indexOf(c);
                    if (v < 0) continue;
                    buffer = (buffer << 6) | v;
                    bits += 6;
                    if (bits >= 8) {
                        bits -= 8;
                        out.push((buffer >> bits) & 0xFF);
                    }
                }
                return Uint8Array.from(out);
            }

            function kofSecB64Url(bytes) {
                let b64 = kofSecB64Encode(bytes);
                b64 = b64.split("+").join("-").split("/").join("_");
                return b64.indexOf("=") >= 0 ? b64.substring(0, b64.indexOf("=")) : b64;
            }

            function kofSecB64UrlDecode(s) {
                const b64 = s.split("-").join("+").split("_").join("/");
                return kofSecB64Decode(b64);
            }

            export function kofSecJwtSecret() {
                const env = kof_platform.getenv("KOF_JWT_SECRET");
                if (env !== null && env !== undefined && env !== "") {
                    return env;
                }
                return kof_platform.randomBytesHex(32);
            }

            export function kofSecJwtCreate(claimsJson, secret) {
                return kofSecJwtCreateTtl(claimsJson, secret, 3600);
            }

            export function kofSecJwtCreateTtl(claimsJson, secret, ttlSeconds) {
                const parsed = JSON.parse(claimsJson);
                if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
                    throw new Error("JWT claims must be a JSON object");
                }
                const now = Math.floor(Date.now() / 1000);
                parsed.iat = now;
                parsed.exp = now + ttlSeconds;
                const headerB64 = kofSecB64Url(kofSecUtf8('{"alg":"HS256","typ":"JWT"}'));
                const payloadB64 = kofSecB64Url(kofSecUtf8(JSON.stringify(parsed)));
                return headerB64 + "." + payloadB64 + "." + kofSecJwtSign(headerB64, payloadB64, secret);
            }

            function kofSecJwtSign(headerB64, payloadB64, secret) {
                return kofSecB64Url(kofSecHmacRaw(kofSecUtf8(secret),
                        kofSecUtf8(headerB64 + "." + payloadB64)));
            }

            export function kofSecJwtVerify(token, secret) {
                return kofSecJwtVerifyIssAud(token, secret, null, null);
            }

            export function kofSecJwtVerifyIssAud(token, secret, issuer, audience) {
                if (token === null || token === undefined || secret === null || secret === undefined) {
                    throw new Error("invalid token or secret");
                }
                const parts = token.split(".");
                if (parts.length !== 3) {
                    throw new Error("malformed token");
                }
                const headerJson = kofSecUtf8Decode(kofSecB64UrlDecode(parts[0]));
                if (!headerJson.includes('"HS256"')) {
                    throw new Error("algorithm not allowed");
                }
                const expected = kofSecJwtSign(parts[0], parts[1], secret);
                if (kofSecConstantTimeEquals(expected, parts[2]) !== 1) {
                    throw new Error("invalid signature");
                }
                const payloadJson = kofSecUtf8Decode(kofSecB64UrlDecode(parts[1]));
                const claims = JSON.parse(payloadJson);
                if (typeof claims !== "object" || claims === null) {
                    throw new Error("invalid payload");
                }
                if (typeof claims.exp === "number" && claims.exp * 1000 <= Date.now()) {
                    throw new Error("token expired");
                }
                if (issuer !== null && claims.iss !== issuer) {
                    throw new Error("issuer mismatch");
                }
                if (audience !== null && claims.aud !== audience) {
                    throw new Error("audience mismatch");
                }
                return payloadJson;
            }

            // ── AES-256-GCM (SECN002 fechado 01/09) ───────────────────
            // Puro JS (roda no GraalJS e no browser). Formato idêntico ao
            // JVM/Native: aesgcm$<ivB64>$<ct||tagB64>, key 32 bytes (64 hex),
            // IV 12 bytes aleatórios, tag 128-bit. S-box FIPS 197; GHASH/CTR
            // NIST SP 800-38D. Validado byte-a-byte contra node:crypto e o
            // vetor NIST (KofSecurityTest.aesGcmJsRoundTrip + paridade JVM).

            const KOF_SEC_AES_SBOX = new Uint8Array([
                0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
                0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
                0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
                0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
                0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
                0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
                0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
                0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
                0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
                0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
                0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
                0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
                0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
                0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
                0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
                0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
            ]);
            const KOF_SEC_AES_RCON = [0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36,0x6c,0xd8,0xab,0x4d];

            function kofSecAesKeyExpansion(key) {
                const Nk = 8, Nr = 14;
                const w = new Uint32Array(4 * (Nr + 1));
                for (let i = 0; i < Nk; i++) {
                    w[i] = ((key[4*i]<<24)|(key[4*i+1]<<16)|(key[4*i+2]<<8)|key[4*i+3]) >>> 0;
                }
                for (let i = Nk; i < 4*(Nr+1); i++) {
                    let temp = w[i-1];
                    if (i % Nk === 0) {
                        temp = ((temp << 8) | (temp >>> 24)) >>> 0;
                        temp = ((KOF_SEC_AES_SBOX[(temp>>>24)&0xff]<<24)|(KOF_SEC_AES_SBOX[(temp>>>16)&0xff]<<16)
                                |(KOF_SEC_AES_SBOX[(temp>>>8)&0xff]<<8)|KOF_SEC_AES_SBOX[temp&0xff]) >>> 0;
                        temp = (temp ^ (KOF_SEC_AES_RCON[i/Nk - 1] << 24)) >>> 0;
                    } else if (i % Nk === 4) {
                        temp = ((KOF_SEC_AES_SBOX[(temp>>>24)&0xff]<<24)|(KOF_SEC_AES_SBOX[(temp>>>16)&0xff]<<16)
                                |(KOF_SEC_AES_SBOX[(temp>>>8)&0xff]<<8)|KOF_SEC_AES_SBOX[temp&0xff]) >>> 0;
                    }
                    w[i] = (w[i-Nk] ^ temp) >>> 0;
                }
                return w;
            }

            function kofSecAesXtime(a) { return ((a<<1) ^ ((a & 0x80)?0x1b:0)) & 0xff; }
            function kofSecAesGmul(a,b) { let r=0; for(let i=0;i<8;i++){ if(b&1) r^=a; a=kofSecAesXtime(a); b>>>=1;} return r&0xff; }

            function kofSecAesEncryptBlock(rk, input) {
                const s = new Uint8Array(16);
                s.set(input);
                for (let c = 0; c < 4; c++) {
                    const w = rk[c];
                    s[4*c] ^= (w>>>24)&0xff; s[4*c+1] ^= (w>>>16)&0xff; s[4*c+2] ^= (w>>>8)&0xff; s[4*c+3] ^= w&0xff;
                }
                for (let round = 1; round < 14; round++) {
                    for (let i=0;i<16;i++) s[i]=KOF_SEC_AES_SBOX[s[i]];
                    let t;
                    t=s[1]; s[1]=s[5]; s[5]=s[9]; s[9]=s[13]; s[13]=t;
                    t=s[2]; s[2]=s[10]; s[10]=t; t=s[6]; s[6]=s[14]; s[14]=t;
                    t=s[15]; s[15]=s[11]; s[11]=s[7]; s[7]=s[3]; s[3]=t;
                    for (let c=0;c<4;c++) {
                        const a0=s[4*c],a1=s[4*c+1],a2=s[4*c+2],a3=s[4*c+3];
                        s[4*c]   = kofSecAesGmul(a0,2)^kofSecAesGmul(a1,3)^a2^a3;
                        s[4*c+1] = a0^kofSecAesGmul(a1,2)^kofSecAesGmul(a2,3)^a3;
                        s[4*c+2] = a0^a1^kofSecAesGmul(a2,2)^kofSecAesGmul(a3,3);
                        s[4*c+3] = kofSecAesGmul(a0,3)^a1^a2^kofSecAesGmul(a3,2);
                    }
                    for (let c = 0; c < 4; c++) {
                        const w = rk[4*round + c];
                        s[4*c] ^= (w>>>24)&0xff; s[4*c+1] ^= (w>>>16)&0xff; s[4*c+2] ^= (w>>>8)&0xff; s[4*c+3] ^= w&0xff;
                    }
                }
                for (let i=0;i<16;i++) s[i]=KOF_SEC_AES_SBOX[s[i]];
                let t;
                t=s[1]; s[1]=s[5]; s[5]=s[9]; s[9]=s[13]; s[13]=t;
                t=s[2]; s[2]=s[10]; s[10]=t; t=s[6]; s[6]=s[14]; s[14]=t;
                t=s[15]; s[15]=s[11]; s[11]=s[7]; s[7]=s[3]; s[3]=t;
                for (let c = 0; c < 4; c++) {
                    const w = rk[56 + c];
                    s[4*c] ^= (w>>>24)&0xff; s[4*c+1] ^= (w>>>16)&0xff; s[4*c+2] ^= (w>>>8)&0xff; s[4*c+3] ^= w&0xff;
                }
                return s;
            }

            function kofSecGcmInc32(c) { for (let i=15;i>=12;i--) { c[i]=(c[i]+1)&0xff; if(c[i]!==0) break; } }

            function kofSecGcmGctr(rk, ctr0, data) {
                const out = new Uint8Array(data.length);
                const ctr = ctr0.slice();
                for (let off=0; off<data.length; off+=16) {
                    const ks = kofSecAesEncryptBlock(rk, ctr);
                    const n = Math.min(16, data.length-off);
                    for (let j=0;j<n;j++) out[off+j]=data[off+j]^ks[j];
                    kofSecGcmInc32(ctr);
                }
                return out;
            }

            function kofSecGcmMult(X, Y) {
                const Z = new Uint8Array(16);
                const V = Y.slice();
                for (let i=0;i<128;i++) {
                    if ((X[i>>3] >>> (7-(i&7))) & 1) { for (let j=0;j<16;j++) Z[j]^=V[j]; }
                    const lsb = V[15] & 1;
                    for (let j=15;j>0;j--) V[j] = ((V[j]>>>1) | ((V[j-1]&1)<<7)) & 0xff;
                    V[0] = V[0]>>>1;
                    if (lsb) V[0] ^= 0xe1;
                }
                return Z;
            }

            function kofSecGhashAbsorb(Y, H, data) {
                for (let off=0; off<data.length; off+=16) {
                    const block = new Uint8Array(16);
                    block.set(data.subarray(off, Math.min(off+16, data.length)));
                    for (let j=0;j<16;j++) Y[j]^=block[j];
                    Y = kofSecGcmMult(Y, H);
                }
                return Y;
            }

            function kofSecGhash(H, aad, ct) {
                let Y = new Uint8Array(16);
                Y = kofSecGhashAbsorb(Y, H, aad);
                Y = kofSecGhashAbsorb(Y, H, ct);
                const lenBlock = new Uint8Array(16);
                const dv = new DataView(lenBlock.buffer);
                dv.setUint32(0, Math.floor(aad.length*8 / 4294967296));
                dv.setUint32(4, (aad.length*8) >>> 0);
                dv.setUint32(8, Math.floor(ct.length*8 / 4294967296));
                dv.setUint32(12, (ct.length*8) >>> 0);
                for (let j=0;j<16;j++) Y[j]^=lenBlock[j];
                Y = kofSecGcmMult(Y, H);
                return Y;
            }

            function kofSecGcmCore(key, iv, data, aad, decrypting) {
                const rk = kofSecAesKeyExpansion(key);
                const H = kofSecAesEncryptBlock(rk, new Uint8Array(16));
                const J0 = new Uint8Array(16); J0.set(iv); J0[15] = 1;
                const ctr = J0.slice(); kofSecGcmInc32(ctr);
                const out = kofSecGcmGctr(rk, ctr, data);
                const ctForTag = decrypting ? data : out;
                const S = kofSecGhash(H, aad, ctForTag);
                const encJ0 = kofSecAesEncryptBlock(rk, J0);
                const tag = new Uint8Array(16);
                for (let j=0;j<16;j++) tag[j] = S[j] ^ encJ0[j];
                return { out: out, tag: tag };
            }

            function kofSecRandomBytes(n) {
                if (typeof crypto !== "undefined" && crypto.getRandomValues) {
                    const b = new Uint8Array(n);
                    crypto.getRandomValues(b);
                    return b;
                }
                return kofSecHexToBytes(kof_platform.randomBytesHex(n));
            }

            export function kofSecAesgcmEncrypt(plaintext, keyHex) {
                const key = kofSecHexToBytes(keyHex);
                if (key.length !== 32) throw new Error("AES-GCM key must be 32 bytes (64 hex chars)");
                const iv = kofSecRandomBytes(12);
                const pt = kofSecUtf8(String(plaintext));
                const r = kofSecGcmCore(key, iv, pt, new Uint8Array(0), false);
                const ctTag = new Uint8Array(r.out.length + 16);
                ctTag.set(r.out);
                ctTag.set(r.tag, r.out.length);
                return "aesgcm$" + kofSecB64Encode(iv) + "$" + kofSecB64Encode(ctTag);
            }

            export function kofSecAesgcmDecrypt(ciphertext, keyHex) {
                const key = kofSecHexToBytes(keyHex);
                if (key.length !== 32) throw new Error("AES-GCM key must be 32 bytes (64 hex chars)");
                const parts = String(ciphertext).split("$");
                if (parts.length !== 3 || parts[0] !== "aesgcm") throw new Error("invalid ciphertext format");
                const iv = kofSecB64Decode(parts[1], true);
                const ctTag = kofSecB64Decode(parts[2], true);
                if (ctTag.length < 16) throw new Error("decryption failed: ciphertext too short");
                const ct = ctTag.subarray(0, ctTag.length - 16);
                const tag = ctTag.subarray(ctTag.length - 16);
                const r = kofSecGcmCore(key, iv, ct, new Uint8Array(0), true);
                let diff = 0;
                for (let j=0;j<16;j++) diff |= r.tag[j] ^ tag[j];
                if (diff !== 0) throw new Error("decryption failed: tag mismatch");
                return kofSecUtf8Decode(r.out);
            }

            // ── kof.validation (G4) ───────────────────────────────────

            export function kofValidationRequired(value) {
                return value != null && value.length > 0 ? 1 : 0;
            }

            export function kofValidationNotBlank(value) {
                return value != null && value.trim().length > 0 ? 1 : 0;
            }

            export function kofValidationMinLength(value, min) {
                return value != null && value.length >= min ? 1 : 0;
            }

            export function kofValidationMaxLength(value, max) {
                return value != null && value.length <= max ? 1 : 0;
            }

            export function kofValidationLengthBetween(value, min, max) {
                return value != null && value.length >= min && value.length <= max ? 1 : 0;
            }

            export function kofValidationIsEmail(value) {
                if (value == null) return 0;
                return /^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$/.test(value) ? 1 : 0;
            }

            export function kofValidationIsUrl(value) {
                if (value == null) return 0;
                return value.startsWith("http://") || value.startsWith("https://") ? 1 : 0;
            }

            export function kofValidationMatches(value, pattern) {
                if (value == null || pattern == null) return 0;
                try { return new RegExp(pattern).test(value) ? 1 : 0; } catch (e) { return 0; }
            }

            export function kofValidationIsInt(value) {
                if (value == null) return 0;
                return /^-?[0-9]+$/.test(value.trim()) ? 1 : 0;
            }

            export function kofValidationIsLong(value) {
                if (value == null) return 0;
                return /^-?[0-9]+$/.test(value.trim()) ? 1 : 0;
            }

            export function kofValidationInRange(value, min, max) {
                return value >= min && value <= max ? 1 : 0;
            }

            export function kofValidationMin(value, min) {
                return value >= min ? 1 : 0;
            }

            export function kofValidationMax(value, max) {
                return value <= max ? 1 : 0;
            }

            // ── kof.observability (G5) ──────────────────────────────

            const __kofObsCounters = {};
            const __kofObsGauges = {};
            const __kofObsHistograms = {};

            export function kofObservabilityHealth() {
                return "UP";
            }

            export function kofObservabilityReadiness() {
                return 1;
            }

            export function kofObservabilityLiveness() {
                return 1;
            }

            export function kofObservabilityCounter(name) {
                if (name == null) name = "";
                const v = (__kofObsCounters[name] || 0) + 1;
                __kofObsCounters[name] = v;
                return v;
            }

            export function kofObservabilityIncrement(name, delta) {
                if (name == null) name = "";
                const v = (__kofObsCounters[name] || 0) + delta;
                __kofObsCounters[name] = v;
                return v;
            }

            export function kofObservabilityGauge(name, value) {
                if (name == null) name = "";
                __kofObsGauges[name] = value;
            }

            export function kofObservabilityHistogram(name, value) {
                if (name == null) name = "";
                const h = __kofObsHistograms[name] || (__kofObsHistograms[name] = { sum: 0, count: 0 });
                h.sum += value;
                h.count += 1;
            }

            const __kofObsSpans = new Map();
            let __kofObsActiveTrace = null;

            export function kofObservabilitySpanStart(name) {
                const id = kofObservabilityTraceId() + kofObservabilitySpanId();
                __kofObsSpans.set(id, Date.now() * 1000);
                return id;
            }

            export function kofObservabilitySpanEnd(handle) {
                const start = __kofObsSpans.get(handle);
                if (start === undefined) return "{}";
                __kofObsSpans.delete(handle);
                const end = Date.now() * 1000;
                const trace = __kofObsActiveTrace || kofObservabilityTraceId();
                return JSON.stringify({
                    traceId: trace,
                    spanId: handle.substring(32),
                    parentSpanId: "",
                    name: "span",
                    startMicros: start,
                    endMicros: end,
                    durationMicros: end - start
                });
            }

            function __kofPromName(name, suffix) {
                let out = String(name).replace(/[^a-zA-Z0-9_:]/g, "_");
                if (out.length === 0) out = "k";
                return out + suffix;
            }

            export function kofObservabilityMetrics() {
                let sb = "";
                const counters = Object.keys(__kofObsCounters).sort();
                for (const m of counters) {
                    const n = __kofPromName(m, "");
                    sb += "# TYPE " + n + " counter\\n" + n + " " + __kofObsCounters[m] + "\\n";
                }
                const gauges = Object.keys(__kofObsGauges).sort();
                for (const m of gauges) {
                    const n = __kofPromName(m, "");
                    sb += "# TYPE " + n + " gauge\\n" + n + " " + __kofObsGauges[m] + "\\n";
                }
                const hists = Object.keys(__kofObsHistograms).sort();
                for (const m of hists) {
                    const n = __kofPromName(m, "");
                    const h = __kofObsHistograms[m];
                    sb += "# TYPE " + n + "_count counter\\n" + n + "_count " + h.count + "\\n";
                    sb += "# TYPE " + n + "_sum gauge\\n" + n + "_sum " + h.sum + "\\n";
                }
                return sb;
            }

            export function kofObservabilityRequestId() {
                if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
                return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, c => {
                    const r = Math.random() * 16 | 0;
                    const v = c === "x" ? r : (r & 0x3 | 0x8);
                    return v.toString(16);
                });
            }

            export function kofObservabilityCorrelationId() {
                return kofObservabilityRequestId();
            }

            function __kofObsRandomHex(bytes) {
                const out = [];
                for (let i = 0; i < bytes; i++) out.push(Math.floor(Math.random() * 256));
                return out.map(v => v.toString(16).padStart(2, "0")).join("");
            }

            export function kofObservabilityTraceId() {
                return __kofObsRandomHex(16);
            }

            export function kofObservabilitySpanId() {
                return __kofObsRandomHex(8);
            }

            // ── kof.security G9 (rate limiting / sessions / API keys) ──

            const __kofRateLimit = {};
            const __kofSessions = {};
            const __kofApiKeys = {};

            export function kofSecRateLimit(key, limit, windowSeconds) {
                if (key == null) key = "";
                if (limit <= 0 || windowSeconds <= 0) return 0;
                const now = Date.now();
                const windowMillis = windowSeconds * 1000;
                let entry = __kofRateLimit[key];
                if (!entry || now - entry.windowStart >= windowMillis) {
                    __kofRateLimit[key] = { windowStart: now, count: 1 };
                    return 1;
                }
                if (entry.count < limit) {
                    entry.count++;
                    return 1;
                }
                return 0;
            }

            export function kofSecSessionCreate(data) {
                const id = kofSecRandomHex(16);
                __kofSessions[id] = data == null ? "" : data;
                return id;
            }

            export function kofSecSessionGet(id) {
                if (id == null) return null;
                const v = __kofSessions[id];
                return v === undefined ? null : v;
            }

            export function kofSecSessionDestroy(id) {
                if (id == null) return 0;
                if (__kofSessions[id] !== undefined) {
                    delete __kofSessions[id];
                    return 1;
                }
                return 0;
            }

            export function kofSecApiKeyGenerate() {
                const key = kofSecRandomHex(32);
                __kofApiKeys[key] = true;
                return key;
            }

            export function kofSecApiKeyValid(key) {
                if (key == null) return 0;
                return __kofApiKeys[key] ? 1 : 0;
            }
            """;

    private static final String UI_EVENT_RUNTIME = """
            // Fase 5 (docs/ui/architecture.md §2.5): target -> bubbles up the
            // component tree (child -> parent). The handler receives a Kof
            // Event with type + stopPropagation support.
            function kofUiDispatchEvent(targetId, domType, ev) {
                const kofEv = {
                    stopped: false,
                    // Kof accesses event kind as e.type() (a method call)
                    type() { return domType; },
                    stopPropagation() { this.stopped = true; },
                    // raw DOM event passthrough (null in the host mock)
                    raw: ev || null
                };
                let current = targetId;
                while (current != null) {
                    const n = kofUiComponents.get(current);
                    if (!n) break;
                    const h = n.el && n.el._kofHandlers && n.el._kofHandlers[domType];
                    if (h) {
                        for (const fn of h) {
                            try {
                                const f = kofUiRunFn(fn);
                                if (f) f(kofEv);
                            } catch (e) {}
                        }
                    }
                    if (kofEv.stopped) break;
                    current = n.parent ? n.parent.id : null;
                }
            }

            /** Test/entry hook: fires an event at a component (bubbles up). */
            export function kofUiEmit(c, type) {
                kofUiDispatchEvent(c, KOF_UI_EV[type] || type, null);
            }

            export function kofUiEventStop(ev) {
                if (ev && typeof ev.stopPropagation === "function") ev.stopPropagation();
            }

            // ── Store: shared observable state (docs/ui/architecture.md §2.6)
            // One Store, many component subscribers. set() notifies every
            // subscriber; a component that re-renders on its own state stays
            // with minimal invalidation — the Store only carries the value.
            const kofUiStores = new Map();
            let kofUiStoreSeq = 0;

            export function kofUiStoreNew(initial) {
                const id = ++kofUiStoreSeq;
                kofUiStores.set(id, { value: initial, subs: [] });
                return id;
            }

            export function kofUiStoreGet(s) {
                const st = kofUiStores.get(s);
                return st ? st.value : 0;
            }

            export function kofUiStoreSet(s, value) {
                const st = kofUiStores.get(s);
                if (!st) return;
                st.value = value;
                // notify every subscriber synchronously (ordering: subscription)
                for (const f of st.subs.slice()) {
                    try { f(value); } catch (e) {}
                }
            }

            export function kofUiStoreSubscribe(s, fn) {
                const st = kofUiStores.get(s);
                if (!st) return;
                const f = kofUiRunFn(fn);
                if (!f) return;
                st.subs.push(f);
                // the subscriber receives the current value immediately
                try { f(st.value); } catch (e) {}
            }

            export function kofUiStoreUnsubscribe(s, fn) {
                const st = kofUiStores.get(s);
                if (!st) return;
                const i = st.subs.indexOf(fn);
                if (i >= 0) st.subs.splice(i, 1);
            }

            export function kofUiStoresLive() {
                return kofUiStores.size;
            }

            // ── Fase 7: Navegação (docs/ui/architecture.md §2.9) ──────
            // Route = nome + builder(componente raiz). Navegar troca o
            // componente raiz da janela: unmount do antigo (lifecycle
            // completo) + mount do novo. back/forward = histórico em stack.
            const kofUiRouterState = {
                routes: {},          // name -> root component id
                current: null,       // nome da rota ativa
                param: null,         // params da rota ativa
                history: [],         // stack para back()
                forwardStack: [],    // stack para forward()
            };

            export function kofUiRouteRegister(name, rootComponent) {
                kofUiRouterState.routes[name] = rootComponent;
            }

            function kofUiRouterHost() {
                // primeiro window montado (o app de janela única usa o id 1)
                return typeof window !== "undefined" && window.__kofWindows
                    ? window.__kofWindows[1] : null;
            }

            function kofUiRouterShow(name, param, pushHistory) {
                const root = kofUiRouterState.routes[name];
                if (root === undefined || root === null) return false;
                const prev = kofUiRouterState.current;
                // desmonta qualquer rota montada que não seja o destino
                // (cobre o caso do bind inicial, que monta sem registrar current)
                for (const key of Object.keys(kofUiRouterState.routes)) {
                    if (key === name) continue;
                    const rc = kofUiRouterState.routes[key];
                    const rn = kofUiComponents.get(rc);
                    if (rn && rn.mounted) {
                        kofUiComponentUnmount(rc);
                        const rel = kofUiComponents.get(rc);
                        if (rel && rel.el && rel.el.parentNode) {
                            rel.el.parentNode.removeChild(rel.el);
                        }
                    }
                }
                if (pushHistory && prev !== null && prev !== name) {
                    kofUiRouterState.forwardStack.length = 0;
                    kofUiRouterState.history.push({ name: prev, param: kofUiRouterState.param });
                }
                kofUiRouterState.current = name;
                kofUiRouterState.param = param;
                const comp = kofUiComponents.get(root);
                if (comp && kofUiRouterHost()) {
                    if (comp.el && !comp.el.parentNode) {
                        kofUiRouterHost().appendChild(comp.el);
                    }
                    kofUiComponentMount(root);
                }
                return true;
            }

            function host() { return kofUiRouterHost(); }

            export function kofUiRouterGo1(name) {
                return kofUiRouterShow(name, null, true);
            }

            export function kofUiRouterGo2(name, param) {
                return kofUiRouterShow(name, param, true);
            }

            export function kofUiRouterReplace1(name) {
                return kofUiRouterNavigate(name, null);
            }

            export function kofUiRouterReplace2(name, param) {
                return kofUiRouterNavigate(name, param);
            }

            export function kofUiRouterBack() {
                if (kofUiRouterState.history.length === 0) return 0;
                const entry = kofUiRouterState.history.pop();
                if (kofUiRouterState.current !== null) {
                    kofUiRouterState.forwardStack.push(
                            { name: kofUiRouterState.current, param: kofUiRouterState.param });
                }
                const ok = kofUiRouterNavigate(entry.name, entry.param);
                return ok ? 1 : 0;
            }

            // troca sem mexer nos stacks (usada por back/forward)
            function kofUiRouterNavigate(name, param) {
                const root = kofUiRouterState.routes[name];
                if (root === undefined || root === null) return false;
                const prev = kofUiRouterState.current;
                if (prev !== null && prev !== name) {
                    const prevComp = kofUiRouterState.routes[prev];
                    if (prevComp !== undefined) {
                        kofUiComponentUnmount(prevComp);
                        const prevEl = kofUiComponents.get(prevComp);
                        if (prevEl && prevEl.el && prevEl.el.parentNode) {
                            prevEl.el.parentNode.removeChild(prevEl.el);
                        }
                    }
                }
                kofUiRouterState.current = name;
                kofUiRouterState.param = param;
                const comp = kofUiComponents.get(root);
                if (comp && kofUiRouterHost()) {
                    if (comp.el && !comp.el.parentNode) kofUiRouterHost().appendChild(comp.el);
                    kofUiComponentMount(root);
                }
                return true;
            }

            export function kofUiRouterForward() {
                if (kofUiRouterState.forwardStack.length === 0) return 0;
                const entry = kofUiRouterState.forwardStack.pop();
                if (kofUiRouterState.current !== null) {
                    kofUiRouterState.history.push(
                            { name: kofUiRouterState.current, param: kofUiRouterState.param });
                }
                const ok = kofUiRouterNavigate(entry.name, entry.param);
                return ok ? 1 : 0;
            }

            export function kofUiRouterParam() {
                return kofUiRouterState.param == null ? "" : String(kofUiRouterState.param);
            }

            export function kofUiRouterCurrent() {
                return kofUiRouterState.current == null ? "" : kofUiRouterState.current;
            }

            export function kofUiRouterDepth() {
                return kofUiRouterState.history.length;
            }
            """;


    private static final String IO_RUNTIME = """
            // KofJS platform runtime — filesystem/console operations for the
            // KofJS target. Generated by the Kof compiler (KofJS backend).
            //
            // This module delegates to the kof_platform object exposed by the
            // Kof process (dev.kof.runtime.KofJsRunner) when Kof executes the
            // generated module with its embedded JavaScript engine. The
            // generated .mjs files are standard ES2022+ modules and do not
            // depend on Node.js or any other external runtime.
            //
            // In a browser (kof-webview / web deployment) there is no
            // kof_platform host object: console operations fall back to the
            // browser console and IO operations report a clear error instead
            // of a ReferenceError.

            const kof_platform = globalThis.kof_platform || new Proxy({ print(x) { console.log(String(x)); } }, {
                get(t, prop) {
                    if (prop in t) return t[prop];
                    return function () {
                        throw new Error("kof.io: " + String(prop) + " is not available in the browser");
                    };
                }
            });

            export function kofPrint(x) {
                kof_platform.print(String(x));
            }

            export function kofArgs() {
                if (kof_platform.args) {
                    return kof_platform.args();
                }
                if (typeof process !== "undefined" && process.argv) {
                    return process.argv.slice(2);
                }
                return [];
            }

            export function kofProcessRun(program, args) {
                const result = kof_platform.processRun(program, args);
                return {
                    stdout: result.stdout,
                    stderr: result.stderr,
                    exitCode: result.exitCode
                };
            }

            export function kofProcessExit(code) {
                // sentinel: o runner converte no exit code do processo
                throw { __kof_exit__: code };
            }

            export function kofReadLine() {
                return kof_platform.readLine();
            }

            export function kofReadFile(p) {
                return kof_platform.readFile(p);
            }

            export function kofWriteFile(p, content) {
                return kof_platform.writeFile(p, content);
            }

            export function kofIoFileExists(p) {
                return kof_platform.fileExists(p);
            }

            export function kofIoFileIsFile(p) {
                return kof_platform.fileIsFile(p);
            }

            export function kofIoFileIsDir(p) {
                return kof_platform.fileIsDir(p);
            }

            export function kofIoReadText(p) {
                return kof_platform.readText(p);
            }

            export function kofIoWriteText(p, content) {
                return kof_platform.writeText(p, content);
            }

            export function kofIoAppendText(p, content) {
                return kof_platform.appendText(p, content);
            }

            export function kofIoReadBytes(p) {
                return kof_platform.readBytes(p);
            }

            export function kofIoWriteBytes(p, bytes) {
                return kof_platform.writeBytes(p, bytes);
            }

            export function kofIoAppendBytes(p, bytes) {
                return kof_platform.appendBytes(p, bytes);
            }

            export function kofIoDelete(p) {
                return kof_platform.delete(p);
            }

            export function kofIoFileSize(p) {
                return kof_platform.fileSize(p);
            }

            export function kofIoFileName(p) {
                return kof_platform.fileName(p);
            }

            export function kofIoPathParent(p) {
                return kof_platform.pathParent(p);
            }

            export function kofIoPathFileName(p) {
                return kof_platform.pathFileName(p);
            }

            export function kofIoPathExtension(p) {
                return kof_platform.pathExtension(p);
            }

            export function kofIoPathNormalize(p) {
                return kof_platform.pathNormalize(p);
            }

            export function kofIoPathResolve(base, child) {
                return kof_platform.pathResolve(base, child);
            }

            export function kofIoPathIsAbsolute(p) {
                return kof_platform.pathIsAbsolute(p);
            }

            export function kofIoPathToAbsolute(p) {
                return kof_platform.pathToAbsolute(p);
            }

            export function kofIoDirCreate(p) {
                return kof_platform.dirCreate(p);
            }

            export function kofIoDirCreateDirs(p) {
                return kof_platform.dirCreateDirs(p);
            }

            export function kofIoDirDelete(p) {
                return kof_platform.dirDelete(p);
            }

            export function kofIoDirList(p) {
                return kof_platform.dirList(p);
            }
            """;

    private void writeRuntime(Path outputDir) throws IOException {
        Path core = outputDir.resolve("kof-runtime.mjs");
        if (!Files.exists(core)) {
            // separate writes: a single concatenated constant would exceed the
            // JVM's 64KiB string / constant-pool limits
            Files.writeString(core, CORE_RUNTIME);
            Files.writeString(core, UI_COMPONENT_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(core, UI_SUPPORT_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(core, UI_EVENT_RUNTIME,
                    java.nio.file.StandardOpenOption.APPEND);
        }
        Path node = outputDir.resolve("kof-runtime-io.mjs");
        if (!Files.exists(node)) {
            Files.writeString(node, IO_RUNTIME);
        }
    }

    private void writeHtmlEntry(Path outputDir, String moduleName) throws IOException {
        String entry = (moduleName.isEmpty() ? "Default" : moduleName.replace('.', '/')) + ".mjs";
        String title = moduleName.isEmpty() ? "Kof" : moduleName;
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>__TITLE__ — Kof</title>
                  <style>
                    :root {
                      --bg: #282a36; --fg: #f8f8f2; --panel: #21222c;
                      --border: #2e303e; --hover: #2e303e; --accent: #8be9fd;
                      --output: #50fa7b; --dim: #6272a4;
                    }
                    * { box-sizing: border-box; }
                    html, body { margin: 0; height: 100%; overflow: hidden; }
                    body {
                      background: var(--bg); color: var(--fg);
                      font-family: "Cascadia Code", "Fira Code", Consolas, Menlo, monospace;
                      display: flex; flex-direction: column;
                    }
                    #kof-titlebar {
                      display: flex; align-items: center; gap: 10px;
                      background: var(--panel); border-bottom: 1px solid var(--border);
                      padding: 7px 14px; font-size: 12px; user-select: none;
                    }
                    #kof-titlebar .dot { width: 11px; height: 11px; border-radius: 50%; display: inline-block; }
                    #kof-titlebar .dot.red { background: #ff5f57; }
                    #kof-titlebar .dot.yellow { background: #febc2e; }
                    #kof-titlebar .dot.green { background: #28c840; }
                    #kof-titlebar .name { color: var(--accent); font-weight: 600; }
                    #kof-titlebar .kind { color: var(--dim); margin-left: auto; }
                    #kof-root {
                      flex: 1; overflow: auto; padding: 14px 16px;
                      display: flex; flex-direction: column; gap: 2px;
                      background: var(--bg);
                    }
                    .kof-label {
                      font-size: 13px; line-height: 1.5; color: var(--output);
                      white-space: pre-wrap; word-break: break-word;
                    }
                    .kof-button {
                      font-size: 13px; padding: 6px 14px; cursor: pointer;
                      background: var(--panel); color: var(--fg);
                      border: 1px solid var(--border); border-radius: 6px;
                    }
                    .kof-button:hover { background: var(--hover); }
                    .kof-input {
                      font-size: 13px; padding: 6px 10px; width: 100%;
                      background: var(--panel); color: var(--fg);
                      border: 1px solid var(--border); border-radius: 6px;
                      font-family: inherit;
                    }
                    .kof-column { display: flex; flex-direction: column; gap: 8px; }
                    .kof-row { display: flex; flex-direction: row; gap: 8px; align-items: center; }
                    .kof-view { box-sizing: border-box; }
                    .kof-window {
                      box-sizing: border-box; padding: 16px; border-radius: 8px;
                      border: 1px solid var(--border); background: var(--bg);
                      display: flex; flex-direction: column; gap: 8px;
                    }
                    #kof-status {
                      background: var(--panel); border-top: 1px solid var(--border);
                      color: var(--dim); font-size: 11px; padding: 4px 14px;
                    }
                  </style>
                </head>
                <body>
                  <div id="kof-titlebar">
                    <span class="dot red"></span><span class="dot yellow"></span><span class="dot green"></span>
                    <span class="name">__TITLE__</span><span class="kind">Kof output</span>
                  </div>
                  <div id="kof-root"></div>
                  <div id="kof-status">terminated</div>
                  <script type="module" src="__ENTRY__"></script>
                </body>
                </html>
                """.replace("__TITLE__", title).replace("__ENTRY__", entry);
        Files.writeString(outputDir.resolve("index.html"), html);
    }

    private void writeSourceMap(IRModule module, Path outputDir, String fileName,
                                List<JsIr.JsFunctionLine> functionLines) throws IOException {
        String source = module.name().isEmpty() ? "Default.kf" : module.name() + ".kf";
        String mappings = buildSourceMapMappings(functionLines);
        String map = "{\"version\":3,\"file\":\"" + fileName
                + "\",\"sources\":[\"" + source + "\"],\"sourcesContent\":null"
                + ",\"names\":[],\"mappings\":\"" + mappings + "\"}";
        Files.writeString(outputDir.resolve(fileName + ".map"), map);
    }

    /**
     * Mappings VLQ (source map V3, formato padrão) — mapeamento de nível de
     * linha: cada linha gerada com mapeamento tem um segmento
     * {@code [genCol=0, srcIdx=0, srcLine(0-based), srcCol=0]}. As linhas geradas
     * são entradas separadas por {@code ';'} (linhas sem mapeamento ficam
     * vazias); {@code srcIdx}/{@code srcLine}/{@code srcCol} são deltas
     * acumulativos entre segmentos; {@code genCol} zera a cada linha.
     */
    private static String buildSourceMapMappings(List<JsIr.JsFunctionLine> lines) {
        if (lines == null || lines.isEmpty()) return "";
        java.util.TreeMap<Integer, Integer> byGen = new java.util.TreeMap<>();
        for (JsIr.JsFunctionLine fl : lines) {
            if (fl.generatedLine() > 1 && fl.kofLine() > 0) {
                byGen.putIfAbsent(fl.generatedLine(), fl.kofLine());
            }
        }
        if (byGen.isEmpty()) return "";
        int maxGen = byGen.lastKey();
        java.util.List<String> entries = new java.util.ArrayList<>(maxGen);
        int prevSrcLine0 = 0;   // linha 0-based acumulativa entre segmentos
        for (int g = 1; g <= maxGen; g++) {
            Integer src = byGen.get(g);
            if (src == null) {
                entries.add("");
                continue;
            }
            int srcLine0 = src - 1;
            entries.add(vlq(0) + vlq(0) + vlq(srcLine0 - prevSrcLine0) + vlq(0));
            prevSrcLine0 = srcLine0;
        }
        return String.join(";", entries);
    }

    /** VLQ base64 do source map (RFC 3436 + tabela do source map V3). */
    private static final String VLQ_B64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private static String vlq(int value) {
        int v = (value < 0) ? (((-value) << 1) | 1) : (value << 1);
        StringBuilder out = new StringBuilder();
        while (true) {
            int digit = v & 31;
            v >>= 5;
            if (v > 0) digit |= 32;
            out.append(VLQ_B64.charAt(digit));
            if (v == 0) break;
        }
        return out.toString();
    }
}