package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompilerDriver extends CompilerDriverState {
    public CompilationResult compile(Path sourceFile, Path outputDir) {
        return CompilerPipeline.compile(this, sourceFile, outputDir);
    }

    public java.util.List<TestInfo> discoveredTests() {
        return CompilerPipeline.discoveredTests(this);
    }

    public CompilationResult compileForTests(Path sourceFile, Path outputDir, Target target) {
        return CompilerPipeline.compileForTests(this, sourceFile, outputDir, target);
    }

    public CompilationResult compileForTestsSources(java.util.List<Path> sources, Path outputDir,
                                                    Target target, Path moduleRoot) {
        return CompilerPipeline.compileForTestsSources(this, sources, outputDir, target, moduleRoot);
    }

    public CompilationResult compile(Path sourceFile, Path outputDir, Target target) {
        return CompilerPipeline.compile(this, sourceFile, outputDir, target);
    }

    public CompilationResult compileSources(java.util.List<Path> sources, Path outputDir, Target target) {
        return CompilerPipeline.compileSources(this, sources, outputDir, target);
    }

    public CompilationResult compileSources(java.util.List<Path> sources, Path outputDir, Target target,
                                            Path moduleRoot) {
        return CompilerPipeline.compileSources(this, sources, outputDir, target, moduleRoot);
    }
Target target = Target.JVM;


    /** Um caso `test "nome" { }` descoberto em compile-time. */
    public record TestInfo(String name, String functionName) {
    }

    /** Testes descobertos na última compilação (ordem de declaração). */

    /**
     * Compila em modo harness de testes: cada `test "nome" { }` vira uma
     * função void (`kof_test_N`) e o main do programa é substituído por um
     * runner sintetizado que executa os testes isolados por try/catch,
     * imprime PASS/FAIL por nome e sai com código != 0 quando há falha.
     * O main original é ignorado (como cargo test).
     */

    /** Variante multi-arquivo do harness de testes (um diretório = um módulo). */

    /** Enable or disable IR optimization passes (enabled by default). */
    public CompilerDriver setOptimizationEnabled(boolean enabled) {
        this.optimizeEnabled = enabled;
        return this;
    }

    /** Enable or disable debug metadata emission (line tables, source names). */
    public CompilerDriver setDebugInfoEnabled(boolean enabled) {
        this.debugInfoEnabled = enabled;
        return this;
    }

    /**
     * Observes the IR before and after optimization (identical modules when
     * optimization is disabled). Used by tooling (kof inspect).
     */
    public CompilerDriver setIRObserver(java.util.function.BiConsumer<IRModule, IRModule> observer) {
        this.irObserver = observer;
        return this;
    }

    /** Observes IR statistics (public API for tooling; no IR types exposed). */
    public CompilerDriver setIRObserver(IRObserver observer) {
        this.irStatsObserver = observer;
        return this;
    }

    public CompilerDriver setExternalClasspath(java.util.List<Path> entries) {
        try {
            externalClasspath.setEntries(entries);
        } catch (java.io.IOException e) {
            if (currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "external classpath could not be read: " + e.getMessage(), "CP001");
            } else {
                pendingClasspathWarnings.add("external classpath could not be read: " + e.getMessage());
            }
        }
        pendingClasspathWarnings.addAll(externalClasspath.loadWarnings());
        return this;
    }

    /** Emite warnings acumulados do classpath externo quando houver coletor. */


    /**
     * Compilação MULTI-ARQUIVO: todos os .kf do diretório formam UM módulo
     * (convenção Go-like: diretório = pacote). Classes/funções de um arquivo
     * são visíveis aos demais sem import — o import fica para classes
     * EXTERNAS (JVM/Android via ExternalClasspath).
     */

    /**
     * Deriva o moduleRoot do menor ancestral comum dos diretórios-pai de todas
     * as fontes — resolução unificada de `import a.b.C` para projetos
     * multi-diretório (P1-4). Fontes no mesmo diretório mantêm o diretório
     * como raiz (comportamento anterior, convenção Go-like).
     */



    /**
     * Imports de PACOTES KOF (código Kof em outras pastas):
     *   import vendas.models            → módulo inteiro do diretório
     *   import vendas.models.Cliente    → arquivo Cliente.kf daquele pacote
     *
     * Resolução: relativa à RAIZ do módulo (diretório passado ao build),
     * TRANSITIVA (imports dos imports), sem ciclos. Tipos ficam visíveis
     * pelo nome simples — a IR é única e global ao build.
     */


    Type listOfElementType(MethodCallExpr mc, List<IRLocalVariable> locals) {
        return CompilerTypeSupport.listOfElementType(this, mc, locals);
    }

    boolean ctorCompatible(Type formal, Type arg) {
        return CompilerTypeSupport.ctorCompatible(this, formal, arg);
    }

    boolean erasesToReference(Type t) {
        return CompilerTypeSupport.erasesToReference(t);
    }

    boolean jsonSupported(Type type, boolean isDecode) {
        return CompilerTypeSupport.jsonSupported(this, type, isDecode);
    }

    boolean fpSupportedOnNative(Type type, SourcePosition pos) {
        return CompilerTypeSupport.fpSupportedOnNative(this, type, pos);
    }

    Type listElementType(Type listType) {
        return CompilerTypeSupport.listElementType(this, listType);
    }

    String toInternalName(String packageName, String simpleName) {
        return CompilerTypeSupport.toInternalName(packageName, simpleName);
    }

    int computeAccess(List<String> modifiers) {
        return CompilerTypeSupport.computeAccess(modifiers);
    }

    int parseIntLiteral(String value) {
        return CompilerTypeSupport.parseIntLiteral(value);
    }

    String stripSuffix(String value) {
        return CompilerTypeSupport.stripSuffix(value);
    }

    boolean needsErasureBoxing() {
        return CompilerEmissionHelpers.needsErasureBoxing(this);
    }

    boolean isJvmTarget() {
        return CompilerEmissionHelpers.isJvmTarget(this);
    }

    void emitWideningIfNeeded(List<KofOperation> ops, Type from, Type to) {
        CompilerEmissionHelpers.emitWideningIfNeeded(this, ops, from, to);
    }

    void emitPrimNarrow(List<KofOperation> ops, Type from, Type to) {
        CompilerEmissionHelpers.emitPrimNarrow(this, ops, from, to);
    }

    static boolean isZeroLiteral(LiteralExpr lit) {
        return CompilerEmissionHelpers.isZeroLiteral(lit);
    }

    void emitErasureBox(List<KofOperation> ops, Type primitive) {
        CompilerEmissionHelpers.emitErasureBox(this, ops, primitive);
    }

    void emitErasureUnbox(List<KofOperation> ops, Type primitive) {
        CompilerEmissionHelpers.emitErasureUnbox(this, ops, primitive);
    }

    public java.util.List<ConfigKeyInfo> discoveredConfigKeys() {
        return CompilerConfigSupport.discoveredConfigKeys(this);
    }

    void recordConfigKey(MethodCallExpr mc) {
        CompilerConfigSupport.recordConfigKey(this, mc);
    }

    public String generateConfigTemplate() {
        return CompilerConfigSupport.generateConfigTemplate(this);
    }

    void lowerAndEmit(CompilationUnitNode unit, DiagnosticCollector diagnostics,
                              Path outputDir, Target target) throws IOException {
        if (System.getProperty("kof.trace") != null) {
            System.err.println("LOWER-AND-EMIT decls=" + unit.declarations().size() + " out=" + outputDir);
        }
        this.target = target;
        this.currentDiagnostics = diagnostics;
        CompilerPipeline.flushClasspathWarnings(this);
        this.entitySchemas.clear();
        BuiltinTypes.resetEnums();
        for (AstNode d : unit.declarations()) {
            if (d instanceof EnumDeclarationNode en) BuiltinTypes.registerEnum(en.name());
        }
            unit = CompilerDesugar.desugarTests(unit, discoveredTests, testHarnessMode, currentSourceName);
            unit = CompilerDesugar.desugarApplication(unit);
            discoveredConfigKeys.clear();
            if (target == Target.ANDROID) {
                unit = CompilerPipeline.appendAndroidHostIfNeeded(this, unit);
            }
            semanticAnalyzer = new SemanticAnalyzer();
            semanticAnalyzer.setExternalTypes(externalClasspath);
            semanticAnalyzer.setDeclarationPackageLookup(d -> declarationPackages.get(d));
            semanticAnalyzer.analyze(unit, diagnostics);
            if (diagnostics.hasErrors()) {
                return;
            }
            LabelId.reset();
            currentModule = new IRModule("", List.of(), List.of());
            currentUnit = unit;
            IRModule irModule = applySuperBridges(CompilerPipeline.lowerToIR(this, unit, diagnostics));
            if (diagnostics.hasErrors()) {
                return;
            }
            currentModule = irModule;
            IRModule unoptimized = irModule;
            if (optimizeEnabled) {
                irModule = Optimizer.optimize(irModule);
                currentModule = irModule;
            }
            if (irObserver != null) {
                irObserver.accept(unoptimized, irModule);
            }
            if (irStatsObserver != null) {
                irStatsObserver.observed(IRStatistics.of(unoptimized, irModule));
            }
            Files.createDirectories(outputDir);
            Backend backend = CompilerPipeline.selectBackend(this, target);
            backend.emit(irModule, outputDir, debugInfoEnabled);
            if (target == Target.ANDROID) {
                new AndroidProjectWriter().write(outputDir, irModule);
            }
    }

    /** Cache de interfaces sintéticas de função (uma por assinatura). */
    final java.util.Map<String, Type.ClassType> functionInterfaces = new java.util.HashMap<>();
    final java.util.IdentityHashMap<LambdaExpr, String> lambdaClassNames = new java.util.IdentityHashMap<>();

    /**
     * Garante um método-ponte na classe DONA da lambda:
     *   kof_super$metodo(...) { super.metodo(...); }
     * A lambda chama a ponte (invokevirtual no $outer) — o verificador JVM
     * rejeita INVOKESPECIAL direto quando a classe corrente não é subclasse.
     */
    String ensureSuperBridge(String ownerInternal, String superInternal,
                                     String methodName, List<Type> paramTypes, Type returnType) {
        String bridgeName = "kof_super$" + methodName;
        List<IRMethod> bridges = pendingSuperBridges.computeIfAbsent(ownerInternal,
                k -> new ArrayList<>());
        for (IRMethod b : bridges) {
            if (b.name().equals(bridgeName)) return bridgeName;
        }
        Type ownerT = CompilerTypes.ownerTypeFromInternal(ownerInternal, semanticAnalyzer);
        Type superT = CompilerTypes.ownerTypeFromInternal(superInternal, semanticAnalyzer);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerT));
        int idx = 1;
        for (Type pt : paramTypes) {
            locals.add(new IRLocalVariable(idx, "arg" + idx, pt));
            idx += TypeMetrics.isDoubleWidth(pt) ? 2 : 1;
        }
        ops.add(new KofLoadLocal(ownerT, 0));
        int argIdx = 1;
        for (Type pt : paramTypes) {
            ops.add(new KofLoadLocal(pt, argIdx));
            argIdx += TypeMetrics.isDoubleWidth(pt) ? 2 : 1;
        }
        ops.add(new KofCall(superT, methodName, paramTypes, returnType, KofCallKind.SUPER));
        if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
        else ops.add(new KofReturn(returnType));
        bridges.add(new IRMethod(bridgeName, returnType, paramTypes,
                AccessFlags.PUBLIC | AccessFlags.FINAL, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals));
        return bridgeName;
    }

    /** Aplica as pontes pendentes às classes do módulo (após lowering). */
    IRModule applySuperBridges(IRModule module) {
        return CompilerOrmSupport.applySuperBridges(this, module);
    }

    void validateOrmField(MethodCallExpr mc, String entityName,
                          List<EntityFieldNode> fields) {
        CompilerOrmSupport.validateOrmField(this, mc, entityName, fields);
    }

    int lowerQueryDsl(QueryDslExpr q, List<KofOperation> ops, String owner,
                      int localIdx, List<IRLocalVariable> locals) {
        return CompilerOrmSupport.lowerQueryDsl(this, q, ops, owner, localIdx, locals);
    }

    String declPackage(AstNode decl, String fallback) {
        return CompilerOrmSupport.declPackage(this, decl, fallback);
    }

    /** Detecta uso de super.metodo() no corpo da lambda. */
    String lambdaClass(LambdaExpr le, Type.FunctionType ft, List<IRLocalVariable> captures) {
        return CompilerLambdaClass.lambdaClass(this, le, ft, captures);
    }

    String lambdaClass(LambdaExpr le, Type.FunctionType ft, List<IRLocalVariable> captures,
                       boolean isTask) {
        return CompilerLambdaClass.lambdaClass(this, le, ft, captures, isTask);
    }

    Type.ClassType lambdaInterfaceType(Type.FunctionType ft) {
        return CompilerLambdaClass.lambdaInterfaceType(this, ft);
    }

    /** Uma chave de config descoberta em compile-time (kof config gen). */
    public record ConfigKeyInfo(String method, String key, String defaultLiteral,
                                String file, int line) {
        /** Tipo declarado do valor, para o template gerado. */
        public String typeHint() {
            return switch (method) {
                case "int" -> "Int";
                case "long" -> "Long";
                case "bool" -> "Bool";
                case "str", "required", "get" -> "String";
                default -> "String";
            };
        }

        public boolean hasDefault() {
            return defaultLiteral != null;
        }

        public ConfigKeyInfo {
            // "..." no source vira conteúdo sem aspas aqui (vem da AST);
            // null = sem default (required/get)
            defaultLiteral = normalizeDefault(defaultLiteral);
        }
        private static String normalizeDefault(String d) {
            if (d == null) return null;
            return d.replaceFirst("^\"", "").replaceFirst("\"$", "");
        }
    }

    /** Chaves de config descobertas na última compilação (ordem de uso). */

    /**
     * Registra `config.method("chave"[, default])` em compile-time (P3 —
     * kof config gen). Só aceita chave como literal de string; chave
     * computada não aparece no template (nada é inferido em runtime).
     */

    /**
     * Gera um template `kof.config` a partir das chaves descobertas na
     * última compilação — para deploy (docs/stdlib-config.md §8.2 P3).
     * Chaves com default viram comentário (o programa já tem valor);
     * required/get sem default viram linha ativa.
     */

    /**
     * Synthetic lambda class. Captured outer locals become private final
     * fields set by a capturing <init>; invoke() copies them into locals at
     * entry, so the body lowers unchanged (captures are read-only snapshots).
     */


    /**
     * Captured outer locals referenced by the lambda body, in first-reference
     * order. Identifiers shadowed by locals declared inside the lambda are
     * not captured.
     */
    List<IRLocalVariable> collectCaptures(LambdaExpr le, List<IRLocalVariable> outerLocals) {
        return CompilerCaptures.collectCaptures(this, le, outerLocals);
    }


    /** Constantes por enum declarado na unidade atual (nome → [A, B, ...]). */


    /**
     * O tipo é um RECORD (dados imutáveis com equals/hashCode gerados)? Usado
     * no lowering de `==`/`!=` (bug 11) para despachar para equals (conteúdo)
     * em vez de igualdade de referência.
     */

    /**
     * O tipo (ou seus type arguments) contém uma FunctionType com className
     * null? Isso indica um tipo de lambda vindo da análise semântica (que roda
     * antes da síntese) — obsoleto para o emit do invoke (bug 20).
     */

    /** Nome da constante de enum representada por um rótulo de case. */




    int emitStatement(StatementNode stmt, List<KofOperation> ops, String owner, int localIdx,
                              List<IRLocalVariable> locals, Type returnType) {
        int before = ops.size();
        int result = StatementLowerer.emitStatementInner(this, stmt, ops, owner, localIdx, locals, returnType);
        if (stmt.position() != null) {
            for (int i = before; i < ops.size(); i++) {
                currentDebugPositions.put(ops.get(i), stmt.position());
            }
        }
        return result;
    }







    /** Compatibilidade largura para fallback de resolução de construtor:
     *  primitivos por largura, tipos de referência por hierarquia, Unknown aceita tudo. */








    void emitPrimWidenNarrow(List<KofOperation> ops, ExpressionNode value,
                             Type elemType, List<IRLocalVariable> locals) {
        CompilerComparisons.emitPrimWidenNarrow(this, ops, value, elemType, locals);
    }


    int emitArgumentsWithFormalTypes(List<ExpressionNode> args, List<Type> formalTypes,
                                             List<KofOperation> ops, String owner, int localIdx,
                                             List<IRLocalVariable> locals) {
        for (int i = 0; i < args.size(); i++) {
            Type formal = i < formalTypes.size() ? formalTypes.get(i) : null;
            // SAM conversion: lambda → interface funcional externa
            // (setOnClickListener(v -> ...) com OnClickListener no classpath)
            if (args.get(i) instanceof LambdaExpr le && formal instanceof Type.ClassType ct
                    && !ct.packageName().isEmpty() && externalClasspath != null
                    && externalClasspath.isInterface(ct.internalName())) {
                ExternalClasspath.Sam sam = externalClasspath.resolveSam(ct.internalName());
                if (sam != null) {
                    localIdx = emitSamAdapter(le, ct, sam, ops, owner, localIdx, locals);
                    continue;
                }
            }
            localIdx = ExpressionLowerer.emitExpression(this, args.get(i), ops, owner, localIdx, locals);
            Type argType = ExpressionTyper.inferExprType(this, args.get(i), locals);
            if (formal != null && formal instanceof Type.PrimitiveType fpt
                    && argType instanceof Type.PrimitiveType apt
                    && !BuiltinTypes.isString(formal)) {
                emitWideningIfNeeded(ops, argType, formal);
            }
            if (formal != null && erasesToReference(formal) && TypeMetrics.isPrimitiveType(argType)
                    && !BuiltinTypes.isString(formal)) {
                emitErasureBox(ops, argType);
            }
        }
        return localIdx;
    }

    private final java.util.IdentityHashMap<LambdaExpr, String> samAdapterNames =
            new java.util.IdentityHashMap<>();

    /**
     * Gera (uma vez por lambda) a classe sintética que IMPLEMENTA a
     * interface externa: o método SAM contém o corpo da lambda e as
     * capturas viram campos finais + construtor — o mesmo modelo das
     * lambdas nativas. Emite NEW+DUP+capturas+&lt;init&gt; na pilha.
     */
    int emitSamAdapter(LambdaExpr le, Type.ClassType iface, ExternalClasspath.Sam sam,
                               List<KofOperation> ops, String owner, int localIdx,
                               List<IRLocalVariable> locals) {
        List<IRLocalVariable> captures = collectCaptures(le, locals);
        if (CompilerLambdaClass.lambdaUsesSuper(le) && currentLoweringOwner != null) {
            Type outerType = CompilerTypes.ownerTypeFromInternal(currentLoweringOwner, semanticAnalyzer);
            List<IRLocalVariable> eff = new ArrayList<>();
            eff.add(new IRLocalVariable(0, "$outer", outerType));
            eff.addAll(captures);
            captures = eff;
        }
        String className = samAdapterNames.computeIfAbsent(le,
                k -> "Sam" + iface.name().replace('.', '_') + "_" + (++lambdaCounter));
        if (CompilerLambdaClass.lambdaUsesSuper(le) && currentLoweringOwner != null) {
            lambdaEnclosingOwner.put(className, currentLoweringOwner);
        }

        List<Type> samParamTypes = new ArrayList<>();
        for (String d : sam.signature().parameterDescriptors()) {
            samParamTypes.add(ExternalClasspath.typeFromDescriptor(d));
        }
        Type samReturn = ExternalClasspath.typeFromDescriptor(sam.signature().returnDescriptor());

        if (le.parameters().size() != samParamTypes.size() && currentDiagnostics != null) {
            SourcePosition p = le.position();
            currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                    "SAM mismatch: lambda has " + le.parameters().size()
                            + " parameter(s) but " + iface.name() + "." + sam.methodName()
                            + " needs " + samParamTypes.size(),
                    "SAM001");
        }

        if (!samAdapterNames.containsValue(className) || !CompilerTypeSupport.syntheticExists(this, className)) {
            buildSyntheticAdapter(className, iface.internalName(), sam.methodName(),
                    samParamTypes, samReturn, le, captures);
        }

        Type adapterType = new Type.ClassType("", className, List.of());
        List<Type> captureTypes = new ArrayList<>();
        for (IRLocalVariable cap : captures) captureTypes.add(cap.type());
        ops.add(new KofNewObject(adapterType, captureTypes));
        ops.add(new KofDup());
        for (IRLocalVariable cap : captures) {
            ops.add(new KofLoadLocal(cap.type(), cap.index()));
        }
        ops.add(new KofCall(adapterType, "<init>", captureTypes,
                Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        return localIdx;
    }


    /**
     * Corpo do adapter: mesmo esqueleto de lambdaClass, mas implementa a
     * interface externa e o método tem o nome/assinatura do SAM. Os params
     * da lambda são ligados POSICIONALMENTE aos params do SAM.
     */
    private void buildSyntheticAdapter(String className, String ifaceInternal, String samName,
                                       List<Type> samParamTypes, Type samReturnType,
                                       LambdaExpr le, List<IRLocalVariable> captures) {
        Type ownerType = new Type.ClassType("", className, List.of());
        List<FormalParameterNode> params = le.parameters();

        List<IRField> fields = new ArrayList<>();
        List<Type> captureTypes = new ArrayList<>();
        for (IRLocalVariable cap : captures) {
            fields.add(new IRField(cap.name(), cap.type(),
                    AccessFlags.PRIVATE | AccessFlags.FINAL, null));
            captureTypes.add(cap.type());
        }

        // invoke(): copia capturas pra locais e chama o método SAM real,
        // que contém o corpo da lambda
        List<KofOperation> ctorOps = new ArrayList<>();
        List<IRLocalVariable> ctorLocals = new ArrayList<>();
        ctorLocals.add(new IRLocalVariable(0, "this", ownerType));
        int cidx = 1;
        for (IRLocalVariable cap : captures) {
            ctorOps.add(new KofLoadLocal(ownerType, 0));
            ctorOps.add(new KofLoadLocal(cap.type(), cidx));
            ctorOps.add(new KofStoreField(ownerType, cap.name(), cap.type()));
            ctorLocals.add(new IRLocalVariable(cidx, cap.name(), cap.type()));
            cidx += TypeMetrics.isDoubleWidth(cap.type()) ? 2 : 1;
        }
        ctorOps.add(new KofReturnVoid());
        IRMethod ctor = new IRMethod("<init>", Type.PrimitiveType.VOID, captureTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ctorOps)), ctorLocals);

        // método SAM: this + capturas nos primeiros slots + params do SAM
        List<KofOperation> bodyOps = new ArrayList<>();
        List<IRLocalVariable> bodyLocals = new ArrayList<>();
        bodyLocals.add(new IRLocalVariable(0, "this", ownerType));
        int bidx = 1;
        for (IRLocalVariable cap : captures) {
            bodyOps.add(new KofLoadLocal(ownerType, 0));
            bodyOps.add(new KofLoadField(ownerType, cap.name(), cap.type()));
            bodyOps.add(new KofStoreLocal(cap.type(), bidx));
            bodyLocals.add(new IRLocalVariable(bidx, cap.name(), cap.type()));
            bidx += TypeMetrics.isDoubleWidth(cap.type()) ? 2 : 1;
        }
        for (int i = 0; i < params.size() && i < samParamTypes.size(); i++) {
            bodyLocals.add(new IRLocalVariable(bidx, params.get(i).name(), samParamTypes.get(i)));
            bidx += TypeMetrics.isDoubleWidth(samParamTypes.get(i)) ? 2 : 1;
        }
        int localEnd = bidx;
        for (StatementNode stmt : le.body()) {
            localEnd = emitStatement(stmt, bodyOps, className, localEnd, bodyLocals, samReturnType);
        }
        KofOperation last = bodyOps.isEmpty() ? null : bodyOps.get(bodyOps.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(samReturnType)) bodyOps.add(new KofReturnVoid());
            else bodyOps.add(new KofReturn(samReturnType));
        }
        IRMethod samMethod = new IRMethod(samName, samReturnType, samParamTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, bodyOps)), bodyLocals);

        IRClass cls = new IRClass(className, "java/lang/Object",
                List.of(ifaceInternal),
                AccessFlags.PUBLIC | AccessFlags.SUPER | AccessFlags.FINAL,
                fields, List.of(samMethod, ctor), List.of(), null, 300 + lambdaCounter);
        syntheticClasses.add(cls);
    }




    IRLocalVariable findLocalVar(String name, List<IRLocalVariable> locals) {
        for (int i = locals.size() - 1; i >= 0; i--) {
            if (locals.get(i).name().equals(name)) return locals.get(i);
        }
        return null;
    }

    /**
     * Namespace da stdlib (web/db/log/...) sombreado por variável local:
     * "var web = ..." torna "web.foo()" chamada de instância, não de namespace.
     */
    boolean isLocalVarName(String name, List<IRLocalVariable> locals) {
        return findLocalVar(name, locals) != null;
    }

    /** Nome de tipo builtin usado como receiver estático (String.valueOf etc.) */
    static boolean isBuiltinStaticReceiver(String name, List<IRLocalVariable> locals) {
        if (findLocalVarStatic(locals, name) != null) return false;
        return switch (name) {
            case "String", "Int", "Integer", "Long", "Float", "Double",
                    "Bool", "Boolean", "Byte", "Short", "Char", "Character",
                    "Object", "Math", "System" -> true;
            default -> false;
        };
    }

    private static IRLocalVariable findLocalVarStatic(List<IRLocalVariable> locals, String name) {
        for (IRLocalVariable lv : locals) {
            if (lv.name().equals(name)) return lv;
        }
        return null;
    }

    int findLocalIndex(String name, List<IRLocalVariable> locals) {
        for (int i = locals.size() - 1; i >= 0; i--) {
            if (locals.get(i).name().equals(name)) return locals.get(i).index();
        }
        return 0;
    }

    /**
     * Emits ++/-- on assignable targets (locals, fields, array elements) with
     * correct prefix/postfix semantics: the result value stays on the stack
     * and the target is stored back.
     */
    int emitIncrement(UnaryExpr ue, Type operandType, List<KofOperation> ops,
                              String owner, int localIdx, List<IRLocalVariable> locals) {
        boolean prefix = ue.prefix();
        KofBinaryOp op = "++".equals(ue.operator()) ? KofBinaryOp.ADD : KofBinaryOp.SUB;
        ExpressionNode target = ue.operand();
        if (target instanceof IdentifierExpr ie) {
            IRLocalVariable var = findLocalVar(ie.name(), locals);
            if (var != null) {
                // local: [load v, (dup), 1, add, (dup), store v]
                ops.add(new KofLoadLocal(var.type(), var.index()));
                if (!prefix) ops.add(new KofDup());
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                ops.add(new KofBinary(op, var.type()));
                if (prefix) ops.add(new KofDup());
                ops.add(new KofStoreLocal(var.type(), var.index()));
                return localIdx;
            }
            if (!owner.isEmpty() && semanticAnalyzer != null) {
                String className = owner.substring(owner.lastIndexOf('/') + 1);
                SymbolTable.Symbol fieldSym = HierarchyResolver.resolveFieldInHierarchy(className, ie.name(), semanticAnalyzer);
                if (fieldSym instanceof SymbolTable.FieldSymbol fs) {
                    Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, semanticAnalyzer);
                    ops.add(new KofLoadLocal(ownerType, 0));
                    localIdx = emitFieldIncrement(ownerType, ie.name(), fs.type(), prefix, op,
                            ops, localIdx, locals);
                    return localIdx;
                }
            }
        }
        if (target instanceof FieldAccessExpr fa) {
            localIdx = ExpressionLowerer.emitExpression(this, fa.receiver(), ops, owner, localIdx, locals);
            Type recvType = ExpressionTyper.inferExprType(this, fa.receiver(), locals);
            Type fieldType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct) {
                SymbolTable.Symbol fs = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), semanticAnalyzer);
                if (fs != null) fieldType = fs.type();
            }
            localIdx = emitFieldIncrement(recvType, fa.fieldName(), fieldType, prefix, op,
                    ops, localIdx, locals);
            return localIdx;
        }
        if (target instanceof ArrayAccessExpr aa) {
            localIdx = ExpressionLowerer.emitExpression(this, aa.receiver(), ops, owner, localIdx, locals);
            Type recvType = ExpressionTyper.inferExprType(this, aa.receiver(), locals);
            Type elemType = Type.arrayElementType(recvType);
            int arrTmp = localIdx++;
            int idxTmp = localIdx++;
            int valTmp = localIdx++;
            locals.add(new IRLocalVariable(arrTmp, "#arr", recvType));
            locals.add(new IRLocalVariable(idxTmp, "#idx", Type.PrimitiveType.INT));
            locals.add(new IRLocalVariable(valTmp, "#val", elemType));
            ops.add(new KofStoreLocal(recvType, arrTmp));
            localIdx = ExpressionLowerer.emitExpression(this, aa.index(), ops, owner, localIdx, locals);
            ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxTmp));
            ops.add(new KofLoadLocal(recvType, arrTmp));
            ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxTmp));
            ops.add(new KofArrayLoad(elemType));
            ops.add(new KofStoreLocal(elemType, valTmp));
            ops.add(new KofLoadLocal(elemType, valTmp));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
            ops.add(new KofBinary(op, elemType));
            if (prefix) {
                // [array, index, new] -> [new, array, index, new]
                ops.add(new KofDupX2());
                ops.add(new KofArrayStore(elemType));
            } else {
                ops.add(new KofArrayStore(elemType));
                ops.add(new KofLoadLocal(elemType, valTmp));
            }
            return localIdx;
        }
        // non-assignable operand: evaluate as expression (legacy behavior)
        localIdx = ExpressionLowerer.emitExpression(this, ue.operand(), ops, owner, localIdx, locals);
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        ops.add(new KofBinary(op, operandType));
        return localIdx;
    }

    /**
     * Field increment: the receiver must survive the field read for the store.
     * Postfix needs a temp for the previous value (JVM putfield consumes the
     * top two slots as value+receiver).
     */
    private int emitFieldIncrement(Type ownerType, String fieldName, Type fieldType,
                                   boolean prefix, KofBinaryOp op,
                                   List<KofOperation> ops, int localIdx,
                                   List<IRLocalVariable> locals) {
        int recvTmp = localIdx++;
        int valTmp = localIdx++;
        int newTmp = localIdx++;
        locals.add(new IRLocalVariable(recvTmp, "#recv", ownerType));
        locals.add(new IRLocalVariable(valTmp, "#inc", fieldType));
        locals.add(new IRLocalVariable(newTmp, "#new", fieldType));
        ops.add(new KofStoreLocal(ownerType, recvTmp));
        ops.add(new KofLoadLocal(ownerType, recvTmp));
        ops.add(new KofLoadField(ownerType, fieldName, fieldType));
        ops.add(new KofStoreLocal(fieldType, valTmp));
        ops.add(new KofLoadLocal(fieldType, valTmp));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        ops.add(new KofBinary(op, fieldType));
        ops.add(new KofStoreLocal(fieldType, newTmp));
        ops.add(new KofLoadLocal(ownerType, recvTmp));
        ops.add(new KofLoadLocal(fieldType, newTmp));
        ops.add(new KofStoreField(ownerType, fieldName, fieldType));
        ops.add(new KofLoadLocal(fieldType, prefix ? newTmp : valTmp));
        return localIdx;
    }

    int emitPackedColor(List<ExpressionNode> args, List<KofOperation> ops,
                               String owner, int localIdx, List<IRLocalVariable> locals) {
        for (int i = 0; i < args.size(); i++) {
            localIdx = ExpressionLowerer.emitExpression(this, args.get(i), ops, owner, localIdx, locals);
            if (i == 0) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 24));
                ops.add(new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
            } else if (i == 1) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 16));
                ops.add(new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
            } else if (i == 2) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 8));
                ops.add(new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
            }
            if (i < 3 && i > 0) {
                ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
            }
            if (i == 3) {
                ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
            }
        }
        if (args.size() == 3) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
            ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
        }
        return localIdx;
    }

    int emitUiInstance(Type recvType, MethodCallExpr mc, List<KofOperation> ops,
                                String owner, int localIdx, List<IRLocalVariable> locals) {
        if (KofUi.isComponent(recvType) || KofUi.isStore(recvType)) {
            KofUi.UiCall cc = KofUi.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
            if (cc != null) {
                for (ExpressionNode arg : mc.arguments()) {
                    localIdx = ExpressionLowerer.emitExpression(this, arg, ops, owner, localIdx, locals);
                }
                List<Type> ccParams = new ArrayList<>();
                ccParams.add(Type.PrimitiveType.INT);
                ccParams.addAll(cc.parameterTypes());
                ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                        cc.function(), ccParams, cc.returnType(), KofCallKind.FUNCTION));
                return localIdx;
            }
            return localIdx;
        }
        if (KofUi.isWindow(recvType) || KofUi.isLabel(recvType) || KofUi.isButton(recvType)
                || KofUi.isInput(recvType) || KofUi.isView(recvType)
                || KofUi.isLink(recvType) || KofUi.isImage(recvType) || KofUi.isIcon(recvType)
                || KofUi.isCanvas(recvType)) {
            KofUi.UiCall uiCall = KofUi.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
            if (uiCall != null) {
                for (ExpressionNode arg : mc.arguments()) {
                    localIdx = ExpressionLowerer.emitExpression(this, arg, ops, owner, localIdx, locals);
                }
                List<Type> uiParams = new ArrayList<>();
                uiParams.add(Type.PrimitiveType.INT);
                uiParams.addAll(uiCall.parameterTypes());
                ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                        uiCall.function(), uiParams, uiCall.returnType(), KofCallKind.FUNCTION));
                return localIdx;
            }
            return localIdx;
        }
        if (KofUi.isColor(recvType)) {
            switch (mc.methodName()) {
                case "red" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 24));
                    ops.add(new KofBinary(KofBinaryOp.USHR, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "green" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 16));
                    ops.add(new KofBinary(KofBinaryOp.USHR, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "blue" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 8));
                    ops.add(new KofBinary(KofBinaryOp.USHR, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "alpha" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "isOpaque" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "withAlpha" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFFFFFF00));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = ExpressionLowerer.emitExpression(this, arg, ops, owner, localIdx, locals);
                    }
                    ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "toCss" -> {
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_color_to_css", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    return localIdx;
                }
                default -> {
                    return localIdx;
                }
            }
        }
        if (KofUi.isTheme(recvType)) {
            switch (mc.methodName()) {
                case "background", "surface", "primary", "secondary", "text", "error" -> {
                    int tagTmp = localIdx++;
                    locals.add(new IRLocalVariable(tagTmp, "#theme", Type.PrimitiveType.INT));
                    ops.add(new KofStoreLocal(Type.PrimitiveType.INT, tagTmp));
                    ops.add(new KofLoadLocal(Type.PrimitiveType.INT, tagTmp));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    LabelId darkLabel = LabelId.create();
                    LabelId lightLabel = LabelId.create();
                    LabelId endLabel = LabelId.create();
                    ops.add(new KofConditionalJump(KofComparison.NE, lightLabel, darkLabel));
                    ops.add(new KofLabel(lightLabel));
                    Integer light = KofUi.themeColor(mc.methodName(), 0);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, light));
                    ops.add(new KofJump(endLabel));
                    ops.add(new KofLabel(darkLabel));
                    Integer dark = KofUi.themeColor(mc.methodName(), 1);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, dark));
                    ops.add(new KofLabel(endLabel));
                    return localIdx;
                }
                case "isDark" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                    return localIdx;
                }
                default -> {
                    return localIdx;
                }
            }
        }
        return localIdx;
    }


    /**
     * FLT001: no Native, float/double ainda não têm aritmética SSE nem
     * formatação real (os bits vivem na pilha como inteiros). Operações de
     * ponto flutuante viram diagnóstico em compile-time — nunca resultado
     * silenciosamente errado. JSON já tem o próprio código (JSN001).
     */




    /**
     * JSN002: valida recursivamente que toda instancia tem layout
     * conhecido em compile-time e campos suportados pelo walker nativo.
     * Qualquer campo fora do conjunto (List, Map, float/double) diagnostica
     * explicitamente — nunca resultado silenciosamente errado.
     */
    /**
     * Campos ordenados (nome, tipo) de uma classe/record/entity declarada
     * na unidade corrente — usados pela composicao JSON no Native.
     */
    java.util.List<String[]> classFieldsOrdered(String className) {
        java.util.List<String[]> out = new ArrayList<>();
        for (AstNode d : currentUnit.declarations()) {
            if (d instanceof RecordDeclarationNode r && r.name().equals(className)) {
                for (RecordComponentNode f : r.components()) {
                    out.add(new String[]{f.name(), f.type()});
                }
            } else if (d instanceof EntityDeclarationNode e && e.name().equals(className)) {
                for (EntityFieldNode f : e.fields()) {
                    out.add(new String[]{f.name(), f.type()});
                }
            } else if (d instanceof ClassDeclarationNode c && c.name().equals(className)) {
                for (AstNode m : c.members()) {
                    if (m instanceof FieldDeclarationNode f) {
                        out.add(new String[]{f.name(), f.type()});
                    }
                }
            }
        }
        return out;
    }

    boolean nativeObjJsonFieldsOk(String className, java.util.Set<String> visiting,
                                          String ownerForDiag) {
        if (visiting.contains(className)) return true; // ciclo: aceita no nivel externo
        visiting.add(className);
        boolean ok = true;
        for (String[] f : classFieldsOrdered(className)) {
            if (!CompilerTypeSupport.fieldOk(this, f[1], className, visiting)) ok = false;
        }
        return ok;
    }

    // v1 flat: objetos aninhados ainda nao sao suportados pelo walker







    boolean isComparisonShortcut(BinaryExpr bin, List<IRLocalVariable> locals) {
        return CompilerComparisons.isComparisonShortcut(this, bin, locals);
    }

    Type comparisonOperandType(BinaryExpr bin, List<IRLocalVariable> locals) {
        return CompilerComparisons.comparisonOperandType(this, bin, locals);
    }

    boolean isNullLiteral(ExpressionNode e) {
        return CompilerComparisons.isNullLiteral(e);
    }

    int emitComparisonShortcut(BinaryExpr bin, List<KofOperation> ops, String owner,
                               int localIdx, List<IRLocalVariable> locals) {
        return CompilerComparisons.emitComparisonShortcut(this, bin, ops, owner, localIdx, locals);
    }

    KofComparison mapComparison(String op) {
        return CompilerComparisons.mapComparison(op);
    }

    boolean hasReturnValue(ExpressionNode expr, List<IRLocalVariable> locals) {
        return CompilerComparisons.hasReturnValue(this, expr, locals);
    }

    /**
     * toString() nativo de record: "Nome[campo=valor, ...]" — sintetizado no
     * IR (padrão de concat: valueOf + kof_string_concat).
     */


    boolean isAbstractMethod(MethodDeclarationNode method) {
        return method.body() == null;
    }

    /**
     * Parses an integer literal, including hexadecimal (0xFF...). ARGB color
     * values may exceed Integer.MAX_VALUE; they wrap to the signed 32-bit
     * representation, which the Kof color semantics use (shifts + mask).
     */


}
