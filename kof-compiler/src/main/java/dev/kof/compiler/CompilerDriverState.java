package dev.kof.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estado compartilhado do CompilerDriver: campos de lowering e configuração.
 * CompilerDriver estende esta classe; as classes de apoio acessam os
 * campos via driver.xxx (herança) — nenhum call site precisa mudar.
 */
public class CompilerDriverState {

IRModule currentModule;

    CompilationUnitNode currentUnit;

    SemanticAnalyzer semanticAnalyzer;

    boolean optimizeEnabled = true;

    protected boolean debugInfoEnabled = true;

    /** kof-android Fase 4: minSdk/targetSdk do APK (defaults 24/34). */
    protected int androidMinSdk = 24;

    protected int androidTargetSdk = 34;

    protected java.util.function.BiConsumer<IRModule, IRModule> irObserver;

    protected IRObserver irStatsObserver;

    DiagnosticCollector currentDiagnostics;

    String currentSourceName;

    /** Primary source text (the first file, i.e. what currentSourceName names) —
     *  embedded as `sourcesContent` in the KofJS V3 source map. */
    String currentSourceContent;

    final java.util.IdentityHashMap<KofOperation, SourcePosition> currentDebugPositions =
            new java.util.IdentityHashMap<>();

    final java.util.Deque<LabelId> breakLabels = new java.util.ArrayDeque<>();

    final java.util.Deque<LabelId> continueLabels = new java.util.ArrayDeque<>();

    /**
     * §551: nº de regiões `try` lexicamente ativas durante o lowering de UMA
     * função. `break`/`continue`/`return` que SAEM de uma região precisam
     * desvincular o handler nativo em runtime (`KofExcUnlink` por região
     * atravessada) — antes, saltavam sem desvincular (frame pendurado →
     * over-catch ou UAF no próximo `throw`). Resetado no início de cada
     * função/lambda/ctor.
     */
    int tryDepth;

    /** Profundidade de `tryDepth` no alvo de cada `break` (paralelo a breakLabels). */
    final java.util.Deque<Integer> breakDepths = new java.util.ArrayDeque<>();

    /** Profundidade de `tryDepth` no alvo de cada `continue` (paralelo a continueLabels). */
    final java.util.Deque<Integer> continueDepths = new java.util.ArrayDeque<>();

    /**
     * DD-01 (bug 45, opção 4a ratificada 13/09): pilha de try/finally ativos
     * durante o lowering de UMA função. `ReturnStmt` com frame ativo não faz
     * return direto — store do valor no slot do frame + jump p/ o epílogo
     * `returnFinally` do frame (que roda o corpo do finally e retorna).
     * Lambda/class-body lowering salva e zera esta pilha (mesmo padrão de
     * savedMutated) p/ não vazar frame do método externo.
     */
    record FinallyFrame(LabelId returnFinallyLabel, LabelId rethrowLabel, int slotValor, Type returnType,
                        int tryDepthSelf) {
    }

    final java.util.Deque<FinallyFrame> finallyFrames = new java.util.ArrayDeque<>();

    boolean loweringMain;

    boolean mainArgsListField;

    /**
     * Classpath externo (.jar/.aar/diretórios) fornecido pelo build tool
     * (Gradle no Android). Usado para resolver assinaturas de métodos de
     * superclasses externas — o INVOKESPECIAL de super.metodo() exige o
     * descritor exato declarado na classe externa.
     */
    final ExternalClasspath externalClasspath = new ExternalClasspath();

    /** Raízes de FONTE de dependências (#566, opção b): onde o `import` procura depois do módulo e da stdlib. */
    final List<Path> dependencySourceRoots = new ArrayList<>();

    final List<String> pendingClasspathWarnings = new ArrayList<>();

    /**
     * Target android: se o programa não declarou a própria MainActivity
     * (em Kof), injeta o host WebView embutido — escrito EM KOF, compilado
     * pelo mesmo frontend. Nenhum arquivo Java é gerado.
     */

    /** Espelho driver-side do qualifyViaImports do SemanticAnalyzer. */

    /** Nome JVM da entidade: as classes top-level do programa ficam sem
     *  pacote (User.class); o Main é Default/Main. */

    final List<IRClass> syntheticClasses = new ArrayList<>();

    /**
     * R4 (`DECISIONS.md` §D-CODEGEN-STEP, 21/09): internal compile-time codegen
     * hooks, run in registration order on the OPTIMIZED IR before emit/interpret.
     * Empty by default = identity (zero behavior change).
     */
    final List<CodegenStep> codegenSteps = new ArrayList<>();

    /**
     * 2.2.3 (`DECISIONS.md` §D-DESUGAR-STEP, 21/09): internal AST-phase desugar
     * steps, run in registration order after parse and before analysis. Default
     * = the four built-in desugars in their historical order, so behavior is
     * unchanged (freeze rule 3).
     */
    final List<DesugarStep> desugarSteps = new ArrayList<>(DesugarSteps.defaults());

    /**
     * G6: desugar `test "nome" { }` para função void `kof_test_N` logo
     * após o parse — semântica, resolução e lowering tratam os testes como
     * funções comuns (zero casos especiais). Com o harness ativo, o main
     * do usuário é substituído pelo runner sintetizado em compile-time
     * (nunca reflection): cada teste roda isolado por try/catch, PASS/FAIL
     * por nome e exit code != 0 quando há falha.
     */

    /**
     * Desugar `application { onStart { ... } onShutdown { ... } }` para duas
     * funções void sintetizadas e embrulha o main para chamá-las no prólogo
     * (onStart) e no epílogo (onShutdown). Zero container, zero reflection —
     * mesmo padrão do `test "nome" {}`.
     */

    /**
     * Runner de testes sintetizado em compile-time (nunca reflection):
     *
     * main() {
     *     var __kof_failed = 0
     *     try {
     *         kof_test_0()
     *         println("PASS " + "nome")
     *     } catch (String e) {
     *         println("FAIL " + "nome" + ": " + e)
     *         __kof_failed = __kof_failed + 1
     *     }
     *     ...
     *     println("────────")
     *     println(__kof_failed + " failed of N tests")
     *     if (__kof_failed > 0) {
     *         throw "__kof_tests_failed__"
     *     }
     * }
     *
     * O throw final vira exit code != 0 em todos os targets (JVM: exceção
     * não capturada; Native: kof_panic; JS: runner reporta 1).
     */

    final java.util.Map<String, List<EntityFieldNode>> entitySchemas = new java.util.LinkedHashMap<>();

    /** FFI (TIER 2.1): declarações {@code extern} por nome (preenchido no lowering). */
    final java.util.Map<String, ExternalFunctionNode> externSignatures = new java.util.LinkedHashMap<>();

    /**
     * #678 (`D-SCRIPT-WARN-SURFACE`): diagnósticos WARNING do frontend na
     * última preparação para interpretação. O JVM/JS/Native imprimem os
     * warnings do compile; o Script os expõe em {@code KofInterpreter.Result}
     * para paridade. Limpo a cada {@code interpret()}.
     */
    List<Diagnostic> interpreterWarnings = java.util.List.of();

    /** Pontes super.metodo() geradas para lambdas: dono interno → método. */
    final Map<String, List<IRMethod>> pendingSuperBridges = new java.util.LinkedHashMap<>();

    /** Raiz do módulo: base para resolver imports de pacotes Kof (dirs). */
    Path moduleRoot;

    /** Pacote declarado de cada declaração (multi-pacote num só módulo). */
    final java.util.Map<AstNode, String> declarationPackages =
            new java.util.IdentityHashMap<>();

    /** Dono real da lambda (classe onde o corpo foi escrito) por classe sintética. */
    final java.util.Map<String, String> lambdaEnclosingOwner = new java.util.LinkedHashMap<>();

    /** Variáveis externas ESCRITAS dentro de lambdas do método sendo lowered → box mutável. */
    java.util.Set<String> mutatedCapturedNames = new java.util.HashSet<>();

    final java.util.Set<String> lambdaCapturedNames = new java.util.HashSet<>();

    /** Nomes das classes BoxN sintéticas (captura mutável) — acesso via campo `value`. */
    final BoxClassFactory boxFactory = new BoxClassFactory();

    final java.util.IdentityHashMap<LambdaExpr, List<IRLocalVariable>> lambdaEffectiveCaptures =
            new java.util.IdentityHashMap<>();

    /** Lambda que usa super.metodo() precisa capturar o this externo ($outer). */
    final java.util.IdentityHashMap<LambdaExpr, Boolean> lambdaNeedsOuter =
            new java.util.IdentityHashMap<>();

    // §277: estes dois caches keyed por AST/assinatura vivem junto de
    // `syntheticClasses` — sem clear no reset, a 2a compilacao num driver
    // compartilhado achava o cache POPULADO e pulava o add() do IRClass
    // sintetizado (a interface de funcao com o metodo `invoke`); o backend
    // entao nao emitia o trampolim/vtable `kof_FunctionN_..._invoke` e o
    // link nativo morria com `undefined reference` (medido: JVM->NATIVE e
    // JS->NATIVE de `((Int) -> Int, (Int) -> Int) -> Int`).
    /** Cache de interfaces sintéticas de função (uma por assinatura). */
    final java.util.Map<String, Type.ClassType> functionInterfaces = new java.util.HashMap<>();
    final java.util.IdentityHashMap<LambdaExpr, String> lambdaClassNames = new java.util.IdentityHashMap<>();

    /** Dono do método sendo lowered agora (para capturar this de lambda). */
    String currentLoweringOwner;

    /** §356: type-params do método/constructor/campo em lowering (`as T[]`). */
    java.util.List<String> currentTypeParams = java.util.List.of();

    /**
     * §357/#295: o interpretador baixa com {@code target = JVM} "por tempero"
     * (prepareForInterpretation), mas o RUNTIME é dinâmico — gates de EMISSÃO
     * JVM (SEM098) não podem rejeitar um programa que o script sempre aceitou
     * (compatibilidade regressiva, regra 2 do freeze).
     */
    boolean interpreting;

    final java.util.List<CompilerDriver.TestInfo> discoveredTests = new java.util.ArrayList<>();

    boolean testHarnessMode = false;

    int lambdaCounter = 0;

    final java.util.List<CompilerDriver.ConfigKeyInfo> discoveredConfigKeys = new java.util.ArrayList<>();

    final java.util.Set<String> discoveredConfigKeySet = new java.util.LinkedHashSet<>();


    public CompilationResult compile(Path sourceFile, Path outputDir) {
        return CompilerPipeline.compile((CompilerDriver) this, sourceFile, outputDir);
    }

    public java.util.List<CompilerDriver.TestInfo> discoveredTests() {
        return CompilerPipeline.discoveredTests((CompilerDriver) this);
    }

    public CompilationResult compileForTests(Path sourceFile, Path outputDir, Target target) {
        return CompilerPipeline.compileForTests((CompilerDriver) this, sourceFile, outputDir, target);
    }

    /**
     * #708: variante com module root EXPLÍCITO — uma raiz de testes separada
     * (ex.: {@code src/test/kof}) deixa fontes em subdiretórios-pacote
     * ({@code exemplo/CalcTest.kf}) resolverem a correspondência PKG004.
     */
    public CompilationResult compileForTests(Path sourceFile, Path outputDir, Target target, Path moduleRoot) {
        return CompilerPipeline.compileForTests((CompilerDriver) this, sourceFile, outputDir, target, moduleRoot);
    }

    public CompilationResult compile(Path sourceFile, Path outputDir, Target target) {
        return CompilerPipeline.compile((CompilerDriver) this, sourceFile, outputDir, target);
    }

    /** B-1: compila p/ nativo com perfil explícito (HOST padrão; FREESTANDING = estático, sem libc). */
    public CompilationResult compile(Path sourceFile, Path outputDir, Target target,
                                     dev.kof.compiler.nat.NativeProfile profile) {
        ((CompilerDriver) this).nativeProfile = profile == null
                ? dev.kof.compiler.nat.NativeProfile.HOST : profile;
        return CompilerPipeline.compile((CompilerDriver) this, sourceFile, outputDir, target);
    }

    public CompilationResult compileSources(java.util.List<Path> sources, Path outputDir, Target target) {
        return CompilerPipeline.compileSources((CompilerDriver) this, sources, outputDir, target);
    }

    Type listOfElementType(MethodCallExpr mc, List<IRLocalVariable> locals) {
        return CompilerTypeSupport.listOfElementType((CompilerDriver) this, mc, locals);
    }

    boolean ctorCompatible(Type formal, Type arg) {
        return CompilerTypeSupport.ctorCompatible((CompilerDriver) this, formal, arg);
    }

    boolean erasesToReference(Type t) {
        return CompilerTypeSupport.erasesToReference(t);
    }

    boolean jsonSupported(Type type, boolean isDecode) {
        return CompilerTypeSupport.jsonSupported((CompilerDriver) this, type, isDecode);
    }

    boolean fpSupportedOnNative(Type type, SourcePosition pos) {
        return CompilerTypeSupport.fpSupportedOnNative((CompilerDriver) this, type, pos);
    }

    Type listElementType(Type listType) {
        return CompilerTypeSupport.listElementType((CompilerDriver) this, listType);
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

    long parseLongLiteral(String value) {
        return CompilerTypeSupport.parseLongLiteral(value);
    }

    float parseFloatLiteral(String value) {
        return CompilerTypeSupport.parseFloatLiteral(value);
    }

    double parseDoubleLiteral(String value) {
        return CompilerTypeSupport.parseDoubleLiteral(value);
    }

    String stripSuffix(String value) {
        return CompilerTypeSupport.stripSuffix(value);
    }

    boolean needsErasureBoxing() {
        return CompilerEmissionHelpers.needsErasureBoxing((CompilerDriver) this);
    }

    boolean isJvmTarget() {
        return CompilerEmissionHelpers.isJvmTarget((CompilerDriver) this);
    }

    void emitWideningIfNeeded(List<KofOperation> ops, Type from, Type to) {
        CompilerEmissionHelpers.emitWideningIfNeeded((CompilerDriver) this, ops, from, to);
    }

    void emitPrimNarrow(List<KofOperation> ops, Type from, Type to) {
        CompilerEmissionHelpers.emitPrimNarrow((CompilerDriver) this, ops, from, to);
    }

    static boolean isZeroLiteral(LiteralExpr lit) {
        return CompilerEmissionHelpers.isZeroLiteral(lit);
    }

    void emitErasureBox(List<KofOperation> ops, Type primitive) {
        CompilerEmissionHelpers.emitErasureBox((CompilerDriver) this, ops, primitive);
    }

    void emitErasureUnbox(List<KofOperation> ops, Type primitive) {
        CompilerEmissionHelpers.emitErasureUnbox((CompilerDriver) this, ops, primitive);
    }

    public java.util.List<CompilerDriver.ConfigKeyInfo> discoveredConfigKeys() {
        return CompilerConfigSupport.discoveredConfigKeys((CompilerDriver) this);
    }

    void recordConfigKey(MethodCallExpr mc) {
        CompilerConfigSupport.recordConfigKey((CompilerDriver) this, mc);
    }

    public String generateConfigTemplate() {
        return CompilerConfigSupport.generateConfigTemplate((CompilerDriver) this);
    }

    /** Aplica as pontes pendentes às classes do módulo (após lowering). */
    IRModule applySuperBridges(IRModule module) {
        return CompilerOrmSupport.applySuperBridges((CompilerDriver) this, module);
    }

    String declPackage(AstNode decl, String fallback) {
        return CompilerOrmSupport.declPackage((CompilerDriver) this, decl, fallback);
    }

    /** Detecta uso de super.metodo() no corpo da lambda. */
    String lambdaClass(LambdaExpr le, Type.FunctionType ft, List<IRLocalVariable> captures) {
        return CompilerLambdaClass.lambdaClass((CompilerDriver) this, le, ft, captures);
    }

    Type.ClassType lambdaInterfaceType(Type.FunctionType ft) {
        return CompilerLambdaClass.lambdaInterfaceType((CompilerDriver) this, ft);
    }

    /**
     * Captured outer locals referenced by the lambda body, in first-reference
     * order. Identifiers shadowed by locals declared inside the lambda are
     * not captured.
     */
    List<IRLocalVariable> collectCaptures(LambdaExpr le, List<IRLocalVariable> outerLocals) {
        return CompilerCaptures.collectCaptures((CompilerDriver) this, le, outerLocals);
    }

    boolean isComparisonShortcut(BinaryExpr bin, List<IRLocalVariable> locals) {
        return CompilerComparisons.isComparisonShortcut((CompilerDriver) this, bin, locals);
    }

    Type comparisonOperandType(BinaryExpr bin, List<IRLocalVariable> locals) {
        return CompilerComparisons.comparisonOperandType((CompilerDriver) this, bin, locals);
    }

    boolean isNullLiteral(ExpressionNode e) {
        return CompilerComparisons.isNullLiteral(e);
    }

    KofComparison mapComparison(String op) {
        return CompilerComparisons.mapComparison(op);
    }

    boolean hasReturnValue(ExpressionNode expr, List<IRLocalVariable> locals) {
        return CompilerComparisons.hasReturnValue((CompilerDriver) this, expr, locals);
    }


    /** Enable or disable IR optimization passes (enabled by default). */
    public CompilerDriver setOptimizationEnabled(boolean enabled) {
        this.optimizeEnabled = enabled;
        return (CompilerDriver) this;
    }

    /** Enable or disable debug metadata emission (line tables, source names). */
    public CompilerDriver setDebugInfoEnabled(boolean enabled) {
        this.debugInfoEnabled = enabled;
        return (CompilerDriver) this;
    }

    /**
     * kof-android Fase 4: sobrescreve minSdk/targetSdk do APK gerado
     * (`--min-sdk`/`--target-sdk`). Defaults 24/34 — nenhuma mudança de
     * contrato, só parametrização do empacotamento.
     */
    public CompilerDriver setAndroidSdk(int minSdk, int targetSdk) {
        this.androidMinSdk = minSdk;
        this.androidTargetSdk = targetSdk;
        return (CompilerDriver) this;
    }

    /**
     * Observes the IR before and after optimization (identical modules when
     * optimization is disabled). Used by tooling (kof inspect).
     */
    public CompilerDriver setIRObserver(java.util.function.BiConsumer<IRModule, IRModule> observer) {
        this.irObserver = observer;
        return (CompilerDriver) this;
    }

    /** Observes IR statistics (public API for tooling; no IR types exposed). */
    public CompilerDriver setIRObserver(IRObserver observer) {
        this.irStatsObserver = observer;
        return (CompilerDriver) this;
    }

    /**
     * #566 (opção b): pacotes publicados são consumidos como MÓDULO-FONTE. Cada raiz é o
     * diretório-fonte de uma dependência instalada (`regsmoke/Greeter.kf` sob a raiz);
     * o `import` a procura DEPOIS do módulo local e da stdlib oficial, em todos os alvos.
     */
    public CompilerDriver setDependencySourceRoots(java.util.List<Path> roots) {
        dependencySourceRoots.clear();
        if (roots != null) dependencySourceRoots.addAll(roots);
        return (CompilerDriver) this;
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
        return (CompilerDriver) this;
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

    boolean isAbstractMethod(MethodDeclarationNode method) {
        return method.body() == null;
    }

    /**
     * Limpa o estado acumulado entre compilações (bug 51). Sem reset, um
     * CompilerDriver reutilizado vazava classes sintéticas/lambdaCounter
     * do programa anterior para o `.s`/bytecode do seguinte — no Native o
     * link quebrava com referências órfãs a símbolos da compilação anterior.
     * Não toca configuração (target, moduleRoot, diagnostics corrente).
     */
    void resetForCompilation() {
        syntheticClasses.clear();
        functionInterfaces.clear();
        lambdaClassNames.clear();
        entitySchemas.clear();
        externSignatures.clear();
        pendingSuperBridges.clear();
        declarationPackages.clear();
        lambdaEnclosingOwner.clear();
        mutatedCapturedNames.clear();
        lambdaCapturedNames.clear();
        lambdaEffectiveCaptures.clear();
        lambdaNeedsOuter.clear();
        discoveredTests.clear();
        discoveredConfigKeys.clear();
        discoveredConfigKeySet.clear();
        lambdaCounter = 0;
        breakLabels.clear();
        continueLabels.clear();
        breakDepths.clear();
        continueDepths.clear();
        tryDepth = 0;
        currentModule = null;
        currentUnit = null;
    }

    CompilerDriverState() {}

}
