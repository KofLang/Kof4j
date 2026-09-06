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
class CompilerDriverState {

IRModule currentModule;

    CompilationUnitNode currentUnit;

    SemanticAnalyzer semanticAnalyzer;

    boolean optimizeEnabled = true;

    protected boolean debugInfoEnabled = true;

    protected java.util.function.BiConsumer<IRModule, IRModule> irObserver;

    protected IRObserver irStatsObserver;

    DiagnosticCollector currentDiagnostics;

    String currentSourceName;

    final java.util.IdentityHashMap<KofOperation, SourcePosition> currentDebugPositions =
            new java.util.IdentityHashMap<>();

    final java.util.Deque<LabelId> breakLabels = new java.util.ArrayDeque<>();

    final java.util.Deque<LabelId> continueLabels = new java.util.ArrayDeque<>();

    boolean loweringMain;

    boolean mainArgsListField;

    /**
     * Classpath externo (.jar/.aar/diretórios) fornecido pelo build tool
     * (Gradle no Android). Usado para resolver assinaturas de métodos de
     * superclasses externas — o INVOKESPECIAL de super.metodo() exige o
     * descritor exato declarado na classe externa.
     */
    final ExternalClasspath externalClasspath = new ExternalClasspath();

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

    /** Dono do método sendo lowered agora (para capturar this de lambda). */
    String currentLoweringOwner;

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

    public CompilationResult compile(Path sourceFile, Path outputDir, Target target) {
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

    CompilerDriverState() {}

}
