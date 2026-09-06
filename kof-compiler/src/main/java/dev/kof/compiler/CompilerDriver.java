package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompilerDriver {

    IRModule currentModule;
    CompilationUnitNode currentUnit;
    SemanticAnalyzer semanticAnalyzer;
Target target = Target.JVM;
    boolean optimizeEnabled = true;
    private boolean debugInfoEnabled = true;
    private java.util.function.BiConsumer<IRModule, IRModule> irObserver;
    private IRObserver irStatsObserver;
    DiagnosticCollector currentDiagnostics;
    private String currentSourceName;
    private final java.util.IdentityHashMap<KofOperation, SourcePosition> currentDebugPositions =
            new java.util.IdentityHashMap<>();
    final java.util.Deque<LabelId> breakLabels = new java.util.ArrayDeque<>();
    final java.util.Deque<LabelId> continueLabels = new java.util.ArrayDeque<>();
    boolean loweringMain;
    boolean mainArgsListField;

    public CompilationResult compile(Path sourceFile, Path outputDir) {
        return compile(sourceFile, outputDir, Target.JVM);
    }

    /** Um caso `test "nome" { }` descoberto em compile-time. */
    public record TestInfo(String name, String functionName) {
    }

    /** Testes descobertos na última compilação (ordem de declaração). */
    public java.util.List<TestInfo> discoveredTests() {
        return List.copyOf(discoveredTests);
    }

    /**
     * Compila em modo harness de testes: cada `test "nome" { }` vira uma
     * função void (`kof_test_N`) e o main do programa é substituído por um
     * runner sintetizado que executa os testes isolados por try/catch,
     * imprime PASS/FAIL por nome e sai com código != 0 quando há falha.
     * O main original é ignorado (como cargo test).
     */
    public CompilationResult compileForTests(Path sourceFile, Path outputDir, Target target) {
        this.testHarnessMode = true;
        try {
            return compile(sourceFile, outputDir, target);
        } finally {
            this.testHarnessMode = false;
        }
    }

    /** Variante multi-arquivo do harness de testes (um diretório = um módulo). */
    public CompilationResult compileForTestsSources(java.util.List<Path> sources,
                                                    Path outputDir, Target target, Path moduleRoot) {
        this.testHarnessMode = true;
        try {
            return compileSources(sources, outputDir, target, moduleRoot);
        } finally {
            this.testHarnessMode = false;
        }
    }

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

    /**
     * Classpath externo (.jar/.aar/diretórios) fornecido pelo build tool
     * (Gradle no Android). Usado para resolver assinaturas de métodos de
     * superclasses externas — o INVOKESPECIAL de super.metodo() exige o
     * descritor exato declarado na classe externa.
     */
    final ExternalClasspath externalClasspath = new ExternalClasspath();
    private final List<String> pendingClasspathWarnings = new ArrayList<>();

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
    private void flushClasspathWarnings() {
        if (currentDiagnostics != null && !pendingClasspathWarnings.isEmpty()) {
            for (String w : pendingClasspathWarnings) {
                currentDiagnostics.warning("", 0, 0, 0, w, "CP002");
            }
            pendingClasspathWarnings.clear();
        }
    }

    public CompilationResult compile(Path sourceFile, Path outputDir, Target target) {
        return compileSources(java.util.List.of(sourceFile), outputDir, target);
    }

    /**
     * Compilação MULTI-ARQUIVO: todos os .kf do diretório formam UM módulo
     * (convenção Go-like: diretório = pacote). Classes/funções de um arquivo
     * são visíveis aos demais sem import — o import fica para classes
     * EXTERNAS (JVM/Android via ExternalClasspath).
     */
    public CompilationResult compileSources(java.util.List<Path> sources, Path outputDir, Target target) {
        return compileSources(sources, outputDir, target, ModuleRoots.moduleRootFor(sources));
    }

    /**
     * Deriva o moduleRoot do menor ancestral comum dos diretórios-pai de todas
     * as fontes — resolução unificada de `import a.b.C` para projetos
     * multi-diretório (P1-4). Fontes no mesmo diretório mantêm o diretório
     * como raiz (comportamento anterior, convenção Go-like).
     */


    public CompilationResult compileSources(java.util.List<Path> sources, Path outputDir, Target target,
                                            Path moduleRoot) {
        this.moduleRoot = moduleRoot;
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        this.target = target;
        this.currentDiagnostics = diagnostics;
        flushClasspathWarnings();
        this.entitySchemas.clear();
        try {
            Path rootAbs = moduleRoot != null ? moduleRoot.toAbsolutePath().normalize() : null;
            CompilationUnitNode unit = parseAndMerge(sources, rootAbs, diagnostics);
            if (unit == null) {
                return new CompilationResult(false, diagnostics, outputDir);
            }
            lowerAndEmit(unit, diagnostics, outputDir, target);
            if (diagnostics.hasErrors()) {
                return new CompilationResult(false, diagnostics, outputDir);
            }
            return new CompilationResult(true, diagnostics, outputDir);
        } catch (IOException e) {
            diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                    "Error reading source file: " + e.getMessage(), "COMP001");
            return new CompilationResult(false, diagnostics, outputDir);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("CONC003-JS-01")) {
                // Erro de usuário esperado (lambda comum precisaria virar
                // async), não um bug do compilador — sem stack trace, sem o
                // wrapper genérico de COMP002.
                diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                        e.getMessage(), "CONC003-JS-01");
                return new CompilationResult(false, diagnostics, outputDir);
            }
            if (e.getMessage() != null && e.getMessage().startsWith("FLT001")) {
                // FLT001: print/println de float/double no runtime riscv64/
                // aarch64 (asm puro, sem libc) — diagnóstico honesto, nunca
                // segfault silencioso (R6).
                diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                        e.getMessage(), "FLT001");
                return new CompilationResult(false, diagnostics, outputDir);
            }
            e.printStackTrace();
            diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                    "Internal compiler error: " + e.getMessage(), "COMP002");
            return new CompilationResult(false, diagnostics, outputDir);
        } catch (Exception e) {
            e.printStackTrace();
            diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                    "Internal compiler error: " + e.getMessage(), "COMP002");
            return new CompilationResult(false, diagnostics, outputDir);
        }
    }

    /**
     * Imports de PACOTES KOF (código Kof em outras pastas):
     *   import vendas.models            → módulo inteiro do diretório
     *   import vendas.models.Cliente    → arquivo Cliente.kf daquele pacote
     *
     * Resolução: relativa à RAIZ do módulo (diretório passado ao build),
     * TRANSITIVA (imports dos imports), sem ciclos. Tipos ficam visíveis
     * pelo nome simples — a IR é única e global ao build.
     */


    private void lowerAndEmit(CompilationUnitNode unit, DiagnosticCollector diagnostics,
                              Path outputDir, Target target) throws IOException {
        if (System.getProperty("kof.trace") != null) {
            System.err.println("LOWER-AND-EMIT decls=" + unit.declarations().size() + " out=" + outputDir);
        }
        this.target = target;
        this.currentDiagnostics = diagnostics;
        flushClasspathWarnings();
        this.entitySchemas.clear();
        IRModule irModule = analyzeAndLower(unit, diagnostics);
        if (irModule == null) {
            return;
        }
        Files.createDirectories(outputDir);
            Backend backend = selectBackend(target);
            backend.emit(irModule, outputDir, debugInfoEnabled);
            if (target == Target.ANDROID) {
                new AndroidProjectWriter().write(outputDir, irModule);
            }
    }

    /**
     * Frontend completo até a IR otimizada: desugar → analisar → lower →
     * otimizar. Retorna null se houver diagnósticos de erro. Compartilhado
     * pelo caminho de emissão (lowerAndEmit) e pelo interpretador
     * (KofInterpreter) — paridade por construção: mesmo parser, mesma
     * semântica, mesmo lowering, mesma otimização.
     */
    IRModule analyzeAndLower(CompilationUnitNode unit, DiagnosticCollector diagnostics) {
        BuiltinTypes.resetEnums();
        for (AstNode d : unit.declarations()) {
            if (d instanceof EnumDeclarationNode en) BuiltinTypes.registerEnum(en.name());
        }
        unit = CompilerDesugar.desugarTests(unit, discoveredTests, testHarnessMode, currentSourceName);
        unit = CompilerDesugar.desugarApplication(unit);
        discoveredConfigKeys.clear();
        if (target == Target.ANDROID) {
            unit = appendAndroidHostIfNeeded(unit);
        }
        semanticAnalyzer = new SemanticAnalyzer();
        semanticAnalyzer.setExternalTypes(externalClasspath);
        semanticAnalyzer.setDeclarationPackageLookup(d -> declarationPackages.get(d));
        semanticAnalyzer.analyze(unit, diagnostics);
        if (diagnostics.hasErrors()) {
            return null;
        }
        LabelId.reset();
        currentModule = new IRModule("", List.of(), List.of());
        currentUnit = unit;
        IRModule irModule = applySuperBridges(lowerToIR(unit, diagnostics));
        if (diagnostics.hasErrors()) {
            return null;
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
        return irModule;
    }

    /**
     * PREPARE PARA INTERPRETAÇÃO (sem emitir bytecode): roda o frontend
     * completo (parse → merge → imports → desugar → análise → lowering →
     * otimização) e entrega a IR pronta para o KofInterpreter executar.
     * Mesma pipeline do compile() — paridade por construção.
     */
    IRModule prepareForInterpretation(java.util.List<Path> sources, Path moduleRoot) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        this.moduleRoot = moduleRoot;
        this.target = Target.JVM;
        this.currentDiagnostics = diagnostics;
        flushClasspathWarnings();
        this.entitySchemas.clear();
        try {
            Path rootAbs = moduleRoot != null ? moduleRoot.toAbsolutePath().normalize() : null;
            CompilationUnitNode unit = parseAndMerge(sources, rootAbs, diagnostics);
            if (unit == null) {
                throw new KofInterpretException(diagnostics);
            }
            IRModule ir = analyzeAndLower(unit, diagnostics);
            if (ir == null) {
                throw new KofInterpretException(diagnostics);
            }
            return ir;
        } catch (IOException e) {
            diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                    "Error reading source file: " + e.getMessage(), "COMP001");
            throw new KofInterpretException(diagnostics);
        }
    }

    /**
     * INTERPRETA um módulo Kof sem emitir bytecode nem fork de JVM — o
     * target KofScript. Roda o mesmo frontend do compile() e executa a IR
     * otimizada no KofInterpreter (paridade por construção). Captura
     * stdout/stderr e retorna exit code. Falhas do frontend viram
     * {@link KofInterpretException} com os diagnósticos.
     */
    public KofInterpreter.Result interpret(java.util.List<Path> sources, Path moduleRoot,
                                           String[] args) {
        IRModule ir = prepareForInterpretation(sources, moduleRoot);
        return KofInterpreter.run(ir, args);
    }

    /** Parse + merge multi-arquivo + expansão de imports (extraído de compileSources). */
    private CompilationUnitNode parseAndMerge(java.util.List<Path> sources, Path rootAbs,
                                              DiagnosticCollector diagnostics) throws IOException {
        this.currentSourceName = sources.get(0).getFileName() != null
                ? sources.get(0).getFileName().toString() : null;
        java.util.List<CompilationUnitNode> parsedUnits = new ArrayList<>();
        for (Path src : sources) {
            String code = Files.readString(src);
            String fileName = src.getFileName().toString();
            Lexer lexer = new Lexer(code, fileName, diagnostics);
            List<Token> tokens = lexer.tokenize();
            if (diagnostics.hasErrors()) return null;
            Parser parser = new Parser(tokens, diagnostics, fileName);
            CompilationUnitNode unit = parser.parse();
            if (diagnostics.hasErrors()) return null;
            parsedUnits.add(unit);
        }
        java.util.List<String> unitPkgs = new ArrayList<>();
        for (int i = 0; i < parsedUnits.size(); i++) {
            String declared = parsedUnits.get(i).packageName();
            String derivedPkg = ModuleRoots.derivedPackageOf(sources.get(i), rootAbs);
            if (!declared.isEmpty() && !declared.equals(derivedPkg)) {
                diagnostics.error(sources.get(i).toString(), 0, 0, 0,
                        "package '" + declared
                                + "' não corresponde ao diretório ('" + derivedPkg
                                + "') — um diretório é um pacote",
                        "PKG004");
                return null;
            }
            unitPkgs.add(derivedPkg);
        }
        List<String> mergedImports = new ArrayList<>();
        List<AstNode> mergedDecls = new ArrayList<>();
        int mainCount = 0;
        for (int i = 0; i < parsedUnits.size(); i++) {
            CompilationUnitNode u = parsedUnits.get(i);
            for (String imp : u.imports()) {
                if (!mergedImports.contains(imp)) mergedImports.add(imp);
            }
            String pkgU = unitPkgs.get(i);
            for (AstNode d : u.declarations()) {
                declarationPackages.put(d, pkgU);
                if (d instanceof FunctionDeclarationNode fd && "main".equals(fd.name())) mainCount++;
                mergedDecls.add(d);
            }
        }
        if (mainCount > 1) {
            diagnostics.error("", 0, 0, 0,
                    "module has " + mainCount + " main() functions; expected exactly one",
                    "PKG002");
            return null;
        }
        CompilationUnitNode merged = new CompilationUnitNode(
                parsedUnits.get(0).position(), "",
                mergedImports, mergedDecls);
        merged = CompilerImports.expandKofImports(merged, moduleRoot, diagnostics, declarationPackages);
        if (diagnostics.hasErrors()) return null;
        return merged;
    }

    /**
     * Target android: se o programa não declarou a própria MainActivity
     * (em Kof), injeta o host WebView embutido — escrito EM KOF, compilado
     * pelo mesmo frontend. Nenhum arquivo Java é gerado.
     */
    private CompilationUnitNode appendAndroidHostIfNeeded(CompilationUnitNode unit) {
        boolean userHasHost = unit.declarations().stream()
                .anyMatch(d -> d instanceof TypeDeclarationNode t && "MainActivity".equals(t.name()));
        if (userHasHost) return unit;
        // sem android.jar no ExternalClasspath o host não resolve — avisar
        // (AND004) e seguir com o programa puro em vez de SEM015 confuso
        if (externalClasspath == null || !externalClasspath.knows("android/app/Activity")) {
            if (currentDiagnostics != null) {
                currentDiagnostics.warning("", 0, 0, 0,
                        "target android sem android.jar no ExternalClasspath: "
                                + "a host Activity não foi incluída no jar",
                        "AND004");
            }
            return unit;
        }
        try (var in = CompilerDriver.class.getResourceAsStream("/dev/kof/android-host.kf")) {
            String hostSource = in != null
                    ? new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    : Files.readString(Path.of("src/main/resources/dev/kof/android-host.kf"));
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "android-host.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "android-host.kf");
            CompilationUnitNode hostUnit = parser.parse();
            List<String> imports = new ArrayList<>(unit.imports());
            for (String imp : hostUnit.imports()) {
                if (!imports.contains(imp)) imports.add(imp);
            }
            List<AstNode> decls = new ArrayList<>(unit.declarations());
            decls.addAll(hostUnit.declarations());
            return new CompilationUnitNode(unit.position(), unit.packageName(), imports, decls);
        } catch (IOException e) {
            if (currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "android host could not be loaded: " + e.getMessage(), "AND004");
            }
            return unit;
        }
    }

    private Backend selectBackend(Target target) {
        return switch (target) {
            case JVM -> backendWithClasspath(new JvmBackend());
            case NATIVE -> new NativeBackend(Target.NATIVE);
            case NATIVE_RISCV64 -> new NativeBackend(Target.NATIVE_RISCV64);
            case NATIVE_AARCH64 -> new NativeBackend(Target.NATIVE_AARCH64);
            case JS -> new JsBackend();
            // Android: ART executa bytecode dex'd — a emissão é a mesma do
            // backend JVM; o alvo vive nas validações AND* e no empacotamento
            case ANDROID -> backendWithClasspath(new JvmBackend());
        };
    }

    private Backend backendWithClasspath(JvmBackend backend) {
        backend.setExternalTypes(externalClasspath);
        return backend;
    }


    /** Espelho driver-side do qualifyViaImports do SemanticAnalyzer. */

    /** Nome JVM da entidade: as classes top-level do programa ficam sem
     *  pacote (User.class); o Main é Default/Main. */



    private IRModule lowerToIR(CompilationUnitNode unit, DiagnosticCollector diagnostics) {
        List<String> imports = new ArrayList<>(unit.imports());
        List<IRClass> classes = new ArrayList<>();
        List<IRMethod> topLevelFunctions = new ArrayList<>();
        String moduleName = unit.packageName().isEmpty() ? "Default" : unit.packageName().replace('.', '/');
        int nextTypeId = 10;
        for (AstNode decl : unit.declarations()) {
            String declPkg = declPackage(decl, unit.packageName());
            if (decl instanceof ClassDeclarationNode cls) classes.add(lowerClass(cls, declPkg, nextTypeId++));
            else if (decl instanceof InterfaceDeclarationNode iface) classes.add(lowerInterface(iface, declPkg, nextTypeId++));
            else if (decl instanceof RecordDeclarationNode rec) classes.add(lowerRecord(rec, declPkg, nextTypeId++));
            else if (decl instanceof EntityDeclarationNode ent) {
                entitySchemas.put(ent.name(), ent.fields());
                List<RecordComponentNode> components = new java.util.ArrayList<>();
                for (EntityFieldNode f : ent.fields()) {
                    components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
                }
                classes.add(lowerRecord(new RecordDeclarationNode(ent.position(), ent.name(),
                        ent.modifiers(), null, List.of(), components, List.of()),
                        declPkg, nextTypeId++));
            }
            else if (decl instanceof FunctionDeclarationNode func) {
                topLevelFunctions.add(lowerFunction(func));
                topLevelFunctions.addAll(lowerFunctionDefaults(func));
            }
        }
        if (!topLevelFunctions.isEmpty()) {
            String mainClassName = moduleName.isEmpty() ? "Main" : moduleName + "/Main";
            classes.add(0, new IRClass(mainClassName, "java/lang/Object", List.of(),
                    AccessFlags.PUBLIC | AccessFlags.SUPER, List.of(), topLevelFunctions, List.of(), null, 0));
        }
        classes.addAll(syntheticClasses);
        return new IRModule(moduleName, classes, imports, currentSourceName);
    }

    final List<IRClass> syntheticClasses = new ArrayList<>();

    /** Cache de interfaces sintéticas de função (uma por assinatura). */
    private final java.util.Map<String, Type.ClassType> functionInterfaces = new java.util.HashMap<>();

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
    final java.util.IdentityHashMap<LambdaExpr, String> lambdaClassNames = new java.util.IdentityHashMap<>();
    /** Pontes super.metodo() geradas para lambdas: dono interno → método. */
    private final Map<String, List<IRMethod>> pendingSuperBridges = new java.util.LinkedHashMap<>();

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
        if (pendingSuperBridges.isEmpty()) return module;
        List<IRClass> classes = new ArrayList<>();
        for (IRClass clazz : module.classes()) {
            List<IRMethod> bridges = pendingSuperBridges.get(clazz.name());
            if (bridges == null) {
                classes.add(clazz);
                continue;
            }
            List<IRMethod> methods = new ArrayList<>(clazz.methods());
            methods.addAll(bridges);
            classes.add(new IRClass(clazz.name(), clazz.superName(), clazz.interfaces(),
                    clazz.accessFlags(), clazz.fields(), methods,
                    clazz.innerClasses(), clazz.signature(), clazz.typeId(),
                    clazz.annotations()));
        }
        pendingSuperBridges.clear();
        return new IRModule(module.name(), classes, module.imports(), module.sourceName());
    }

    /** Raiz do módulo: base para resolver imports de pacotes Kof (dirs). */
    private Path moduleRoot;

    /** Pacote declarado de cada declaração (multi-pacote num só módulo). */
    private final Map<AstNode, String> declarationPackages =
            new java.util.IdentityHashMap<>();


    /** Pacote derivado do DIRETÓRIO do arquivo relativo à raiz do módulo. */

    /**
     * P3-10: em {@code orm.where<T>(db, "col", v)}, {@code orm.where_op<T>(db,
     * "col", op, v)} e {@code orm.count<T>(db, "col", v)} a coluna é o 2º arg.
     * Se for um literal de string, ele tem que nomear um campo da entidade —
     * caso contrário falha em compile-time (ORM003), sem esperar o SQL falhar
     * em runtime. Colunas dinâmicas (arg não-literal) seguem liberadas.
     */
    void validateOrmField(MethodCallExpr mc, String entityName,
                                  List<EntityFieldNode> fields) {
        String m = mc.methodName();
        boolean isWhere = "where".equals(m) || "where_op".equals(m);
        boolean isCountWhere = "count".equals(m) && mc.arguments().size() == 3;
        if (!isWhere && !isCountWhere) return;
        ExpressionNode fieldArg = mc.arguments().get(1);
        if (!(fieldArg instanceof LiteralExpr lit) || lit.kind() != ConcreteLiteralKind.STRING) return;
        String col = lit.value();
        for (EntityFieldNode f : fields) {
            if (f.name().equals(col)) return;
        }
        if (currentDiagnostics != null) {
            SourcePosition p = mc.position();
            currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                    "orm." + m + ": unknown column '" + col + "' in entity '"
                            + entityName + "'",
                    "ORM003");
        }
    }

    /**
     * Query DSL tipada (ORM001): baixa {@code Entity.query(db) { where ...;
     * orderBy ...; limit N }} para {@code kof_db_queryN(db, sql, binds...,
     * className)} — o mesmo caminho de {@code db.query<T>}. A SQL é montada em
     * compile-time a partir do schema da entidade (validação de coluna à la
     * ORM003); os valores de {@code where} são binds preparados ({@code ?}).
     */
    int lowerQueryDsl(QueryDslExpr q, List<KofOperation> ops, String owner,
                              int localIdx, List<IRLocalVariable> locals) {
        if (!KofDb.supportedOn(target)) {
            if (currentDiagnostics != null) {
                SourcePosition p = q.position();
                currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                        q.entityType() + ".query: not available on the " + target
                                + " target yet (" + KofDb.gapCode() + ")",
                        KofDb.gapCode());
            }
            return localIdx;
        }
        String entity = q.entityType();
        List<EntityFieldNode> fields = entitySchemas.get(entity);
        // identificadores sempre quotados (ANSI "ident") — nomes de entidade/
        // coluna podem ser palavras reservadas do SQL (ex.: user)
        String table = '"' + KofOrm.tableName(entity) + '"';

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
        List<ExpressionNode> binds = new ArrayList<>();
        boolean firstWhere = true;
        for (ExpressionNode w : q.whereClauses()) {
            if (!(w instanceof BinaryExpr be)) {
                if (currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: where clause must be a comparison (a > b)", "ORM004");
                }
                return localIdx;
            }
            if (!(be.left() instanceof IdentifierExpr col)) {
                if (currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: where field must be a column name", "ORM004");
                }
                return localIdx;
            }
            if (fields == null) {
                if (currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: unknown entity '" + entity + "' (ORM002)", "ORM002");
                }
                return localIdx;
            }
            boolean valid = fields.stream().anyMatch(f -> f.name().equals(col.name()));
            if (!valid) {
                if (currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: unknown column '" + col.name() + "' in entity '"
                                    + entity + "' (ORM003)", "ORM003");
                }
                return localIdx;
            }
            String op = sqlOp(be.operator());
            if (op == null) {
                if (currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: unsupported operator '" + be.operator() + "' (use =, ==, !=, <, <=, >, >=)",
                            "ORM004");
                }
                return localIdx;
            }
            sql.append(firstWhere ? " WHERE " : " AND ")
                    .append('"').append(col.name()).append('"')
                    .append(' ').append(op).append(" ?");
            firstWhere = false;
            binds.add(be.right());
        }
        if (!q.orderByFields().isEmpty()) {
            for (int i = 0; i < q.orderByFields().size(); i++) {
                ExpressionNode f = q.orderByFields().get(i);
                if (!(f instanceof IdentifierExpr idf)) {
                    if (currentDiagnostics != null) {
                        SourcePosition p = q.position();
                        currentDiagnostics.error(p != null ? p.file() : "",
                                p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                "query: orderBy field must be a column name", "ORM004");
                    }
                    return localIdx;
                }
                sql.append(i == 0 ? " ORDER BY " : ", ")
                        .append('"').append(idf.name()).append('"')
                        .append(" ").append(q.orderByDirs().get(i).toUpperCase());
            }
        }
        // limit: literal inline; não-literal vira bind
        if (q.limit() != null) {
            if (q.limit() instanceof LiteralExpr le && le.kind() == ConcreteLiteralKind.INT) {
                sql.append(" LIMIT ").append(le.value());
            } else {
                sql.append(" LIMIT ?");
                binds.add(q.limit());
            }
        }

        int nBinds = binds.size();
        if (nBinds > KofDb.MAX_BIND) {
            if (currentDiagnostics != null) {
                SourcePosition p = q.position();
                currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                        "query: at most " + KofDb.MAX_BIND + " binds (where + limit)", "ORM004");
            }
            return localIdx;
        }
        String fn = "kof_db_query" + nBinds;
        // 1) db id
        localIdx = ExpressionLowerer.emitExpression(this, q.dbArg(), ops, owner, localIdx, locals);
        // 2) sql
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, sql.toString()));
        // 3) binds (primitivos boxed — o runtime espera Object)
        for (ExpressionNode b : binds) {
            Type bt = ExpressionTyper.inferExprType(this, b, locals);
            localIdx = ExpressionLowerer.emitExpression(this, b, ops, owner, localIdx, locals);
            if (TypeMetrics.isPrimitiveType(bt)) TypeEmitter.boxPrimitive(ops, bt);
        }
        // 4) className
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, CompilerTypes.classNameFor(entity)));
        // 5) a chamada
        List<Type> params = new ArrayList<>();
        params.add(BuiltinTypes.STRING); // id
        params.add(BuiltinTypes.STRING); // sql
        for (int i = 0; i < nBinds; i++) params.add(Type.UnknownType.UNKNOWN);
        params.add(BuiltinTypes.STRING); // className
        Type retType = new Type.ClassType("kof", "List", List.of(CompilerTypes.toType(entity, currentUnit)));
        ops.add(new KofCall(new Type.ClassType("kof.db", "Db", List.of()),
                fn, params, retType, KofCallKind.FUNCTION));
        return localIdx;
    }

    /** Operador Kof → operador SQL ({@code ==} → {@code =}); null se não suportado. */
    private static String sqlOp(String op) {
        return switch (op) {
            case "=", "==", "!=" -> op.equals("==") ? "=" : op;
            case "<", "<=", ">", ">=" -> op;
            default -> null;
        };
    }

    private String declPackage(AstNode decl, String fallback) {
        String pkg = declarationPackages.get(decl);
        return pkg != null ? pkg : fallback;
    }

    /** Dono real da lambda (classe onde o corpo foi escrito) por classe sintética. */
    final java.util.Map<String, String> lambdaEnclosingOwner = new java.util.LinkedHashMap<>();
    /** Variáveis externas ESCRITAS dentro de lambdas do método sendo lowered → box mutável. */
    java.util.Set<String> mutatedCapturedNames = new java.util.HashSet<>();
    private final java.util.Set<String> lambdaCapturedNames = new java.util.HashSet<>();
    /** Nomes das classes BoxN sintéticas (captura mutável) — acesso via campo `value`. */
    final BoxClassFactory boxFactory = new BoxClassFactory();

    final java.util.IdentityHashMap<LambdaExpr, List<IRLocalVariable>> lambdaEffectiveCaptures =
            new java.util.IdentityHashMap<>();

    /** Lambda que usa super.metodo() precisa capturar o this externo ($outer). */
    private final java.util.IdentityHashMap<LambdaExpr, Boolean> lambdaNeedsOuter =
            new java.util.IdentityHashMap<>();

    /** Dono do método sendo lowered agora (para capturar this de lambda). */
    String currentLoweringOwner;

    /** Detecta uso de super.metodo() no corpo da lambda. */
    private static boolean lambdaUsesSuper(Object node) {
        if (node instanceof LambdaExpr le) {
            for (StatementNode st : le.body()) {
                if (lambdaUsesSuper(st)) return true;
            }
            return false;
        }
        if (node instanceof MethodCallExpr mc) {
            if (mc.receiver() instanceof IdentifierExpr rid && "super".equals(rid.name())) return true;
            if (lambdaUsesSuper(mc.receiver())) return true;
            for (ExpressionNode arg : mc.arguments()) if (lambdaUsesSuper(arg)) return true;
            return false;
        }
        if (node instanceof IdentifierExpr ie) return "super".equals(ie.name());
        if (node instanceof FieldAccessExpr fa) return lambdaUsesSuper(fa.receiver());
        if (node instanceof BinaryExpr be) return lambdaUsesSuper(be.left()) || lambdaUsesSuper(be.right());
        if (node instanceof UnaryExpr ue) return lambdaUsesSuper(ue.operand());
        if (node instanceof AssignmentExpr ae) return lambdaUsesSuper(ae.target()) || lambdaUsesSuper(ae.value());
        if (node instanceof VarDeclStmt v) return v.initializer() != null && lambdaUsesSuper(v.initializer());
        if (node instanceof ExpressionStmt es) return es.expression() != null && lambdaUsesSuper(es.expression());
        if (node instanceof ReturnStmt rs) return rs.value() != null && lambdaUsesSuper(rs.value());
        if (node instanceof IfStmt is) return lambdaUsesSuper(is.condition())
                || lambdaUsesSuper(is.thenBranch())
                || (is.elseBranch() != null && lambdaUsesSuper(is.elseBranch()));
        if (node instanceof WhileStmt ws) return lambdaUsesSuper(ws.condition()) || lambdaUsesSuper(ws.body());
        if (node instanceof ForStmt fs) return lambdaUsesSuper(fs.init()) || lambdaUsesSuper(fs.condition())
                || lambdaUsesSuper(fs.update()) || lambdaUsesSuper(fs.body());
        if (node instanceof BlockStmt bs) {
            for (StatementNode st : bs.statements()) if (lambdaUsesSuper(st)) return true;
            return false;
        }
        return false;
    }
    private final java.util.List<TestInfo> discoveredTests = new java.util.ArrayList<>();
    boolean testHarnessMode = false;
    int lambdaCounter = 0;

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

    private final java.util.List<ConfigKeyInfo> discoveredConfigKeys = new java.util.ArrayList<>();
    private final java.util.Set<String> discoveredConfigKeySet = new java.util.LinkedHashSet<>();

    /** Chaves de config descobertas na última compilação (ordem de uso). */
    public java.util.List<ConfigKeyInfo> discoveredConfigKeys() {
        return List.copyOf(discoveredConfigKeys);
    }

    /**
     * Registra `config.method("chave"[, default])` em compile-time (P3 —
     * kof config gen). Só aceita chave como literal de string; chave
     * computada não aparece no template (nada é inferido em runtime).
     */
    void recordConfigKey(MethodCallExpr mc) {
        List<ExpressionNode> args = mc.arguments();
        if (args.isEmpty()) return;
        if (!(args.get(0) instanceof LiteralExpr le)
                || le.kind() != ConcreteLiteralKind.STRING) {
            return;
        }
        String key = le.value();
        String def = null;
        if (args.size() >= 2 && args.get(1) instanceof LiteralExpr dl) {
            def = switch (dl.kind()) {
                case ConcreteLiteralKind.STRING -> "\"" + dl.value() + "\"";
                case ConcreteLiteralKind.INT, ConcreteLiteralKind.LONG,
                        ConcreteLiteralKind.BOOLEAN, ConcreteLiteralKind.FLOAT,
                        ConcreteLiteralKind.DOUBLE -> dl.value();
                default -> null;
            };
        }
        String method = "required".equals(mc.methodName()) || "get".equals(mc.methodName())
                ? "required" : mc.methodName();
        String dedupe = method + "|" + key + "|" + def;
        if (discoveredConfigKeySet.add(dedupe)) {
            SourcePosition pos = mc.position();
            discoveredConfigKeys.add(new ConfigKeyInfo(method, key, def,
                    pos != null ? pos.file() : "", pos != null ? pos.line() : 0));
        }
    }

    /**
     * Gera um template `kof.config` a partir das chaves descobertas na
     * última compilação — para deploy (docs/stdlib-config.md §8.2 P3).
     * Chaves com default viram comentário (o programa já tem valor);
     * required/get sem default viram linha ativa.
     */
    public String generateConfigTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append("# kof.config — gerado por kof config gen\n");
        sb.append("# Chaves usadas pelo programa (em ordem de primeiro uso).\n");
        sb.append("# Chaves com default estão comentadas — descomente para sobrescrever.\n\n");
        for (ConfigKeyInfo k : discoveredConfigKeys) {
            if (k.hasDefault()) {
                sb.append("# ").append(k.key()).append(" = ")
                  .append(k.defaultLiteral().isEmpty() ? "" : k.defaultLiteral())
                  .append("\n");
            } else {
                sb.append("# REQUIRED (sem default no código):\n")
                  .append(k.key()).append(" = \n");
            }
        }
        return sb.toString();
    }

    /**
     * Synthetic lambda class. Captured outer locals become private final
     * fields set by a capturing <init>; invoke() copies them into locals at
     * entry, so the body lowers unchanged (captures are read-only snapshots).
     */
    String lambdaClass(LambdaExpr le, Type.FunctionType ft, List<IRLocalVariable> captures) {
        return lambdaClass(le, ft, captures, false);
    }

    /** @param isTask true for spawn bodies ({@code LambdaTask*}), not for map/filter/UI handlers */
    String lambdaClass(LambdaExpr le, Type.FunctionType ft, List<IRLocalVariable> captures,
                               boolean isTask) {
        String existing = lambdaClassNames.get(le);
        if (existing != null) return existing;
        String name = (isTask ? "LambdaTask" : "Lambda") + (lambdaCounter++);
        Type ownerType = new Type.ClassType("", name, List.of());
        // super.metodo() dentro da lambda: captura o this EXTERNO como $outer
        boolean needsOuter = lambdaUsesSuper(le) && currentLoweringOwner != null;
        if (needsOuter) {
            lambdaNeedsOuter.put(le, true);
            lambdaEnclosingOwner.put(name, currentLoweringOwner);
            Type outerType = CompilerTypes.ownerTypeFromInternal(currentLoweringOwner, semanticAnalyzer);
            List<IRLocalVariable> eff = new ArrayList<>();
            eff.add(new IRLocalVariable(0, "$outer", outerType));
            eff.addAll(captures);
            captures = eff;
            lambdaEffectiveCaptures.put(le, eff);
        }
        // lambda retornando lambda (bug 19): preservar a FunctionType (o
        // round-trip por string a destruía) — o className do lambda interno
        // será preenchido após a emissão do corpo.
        Type returnType = ft.returnType() instanceof Type.FunctionType
                ? ft.returnType()
                : CompilerTypes.toType(CompilerTypes.typeToString(ft.returnType()), currentUnit);
        List<FormalParameterNode> params = le.parameters();
        List<Type> paramTypes = new ArrayList<>();
        for (FormalParameterNode p : params) paramTypes.add(CompilerTypes.toType(p.type(), currentUnit));

        List<IRField> fields = new ArrayList<>();
        List<Type> captureTypes = new ArrayList<>();
        for (IRLocalVariable cap : captures) {
            fields.add(new IRField(cap.name(), cap.type(),
                    AccessFlags.PRIVATE | AccessFlags.FINAL, null));
            captureTypes.add(cap.type());
        }

        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        // JVM invoke(): the real parameters arrive physically at slots 1..k
        // (after this). The captures are re-homed to slots AFTER the params:
        // the prologue copies the incoming parameters to their final slots
        // first, then loads each capture field into its slot. This keeps the
        // parameter slots owned by the caller's arguments — no clobbering.
        int localIdx = 1;
        int[] paramSlots = new int[params.size()];
        int paramSlot = 1;
        for (int i = 0; i < params.size(); i++) {
            paramSlots[i] = paramSlot;
            paramSlot += TypeMetrics.isDoubleWidth(paramTypes.get(i)) ? 2 : 1;
        }
        int captureBase = paramSlot;
        int captureSlot = captureBase;
        for (IRLocalVariable cap : captures) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, cap.name(), cap.type()));
            ops.add(new KofStoreLocal(cap.type(), captureSlot));
            locals.add(new IRLocalVariable(captureSlot, cap.name(), cap.type()));
            captureSlot += TypeMetrics.isDoubleWidth(cap.type()) ? 2 : 1;
        }
        localIdx = captureSlot;
        for (int i = 0; i < params.size(); i++) {
            locals.add(new IRLocalVariable(paramSlots[i], params.get(i).name(), paramTypes.get(i)));
        }
        java.util.Set<String> savedMutated = mutatedCapturedNames;
        mutatedCapturedNames = new java.util.HashSet<>();
        // lambda não-void com corpo de expressão única: a expressão É o retorno
        // (ExpressionStmt emitiria POP e mataria o valor antes do areturn)
        java.util.List<StatementNode> bodyStmts = le.body();
        if (!Type.isVoid(returnType) && bodyStmts.size() == 1
                && bodyStmts.get(0) instanceof ExpressionStmt es) {
            bodyStmts = java.util.List.of(new ReturnStmt(
                    es.position() != null ? es.position() : le.position(), es.expression()));
        }
        for (StatementNode stmt : bodyStmts) {
            localIdx = emitStatement(stmt, ops, name, localIdx, locals, returnType);
        }
        mutatedCapturedNames = savedMutated;
        // bug 19: lambda que RETORNA outra lambda — o lambda interno é
        // sintetizado durante a emissão do corpo acima; o className dele só
        // agora está disponível. Atualiza o returnType para o descriptor do
        // invoke casar com o call site (senão NoSuchMethodError).
        if (returnType instanceof Type.FunctionType rtFt && rtFt.className() == null) {
            for (StatementNode stmt : bodyStmts) {
                if (stmt instanceof ReturnStmt rs && rs.value() instanceof LambdaExpr retLam) {
                    String cn = lambdaClassNames.get(retLam);
                    if (cn != null) {
                        returnType = new Type.FunctionType(rtFt.parameterTypes(), rtFt.returnType(), cn);
                        break;
                    }
                }
            }
        }
        KofOperation last = ops.isEmpty() ? null : ops.get(ops.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
            else ops.add(new KofReturn(returnType));
        }
        IRMethod invoke = new IRMethod("invoke", returnType, paramTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);

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

        IRClass cls = new IRClass(name, "java/lang/Object",
                List.of(lambdaInterfaceType(ft).internalName()),
                AccessFlags.PUBLIC | AccessFlags.SUPER, fields,
                List.of(invoke, ctor), List.of(), null, 200 + lambdaCounter);
        syntheticClasses.add(cls);
        lambdaClassNames.put(le, name);
        return name;
    }

    /**
     * Interface sintética por assinatura de função — o dispatch por interface
     * (bug 8) para valores de tipo de função DECLARADO (`f(x)` com
     * `f: (Int) -> Int`): o tipo não carrega className, então o call site
     * invoca via interface que TODAS as lambdas da assinatura implementam.
     */
    Type.ClassType lambdaInterfaceType(Type.FunctionType ft) {
        StringBuilder key = new StringBuilder("Function").append(ft.parameterTypes().size());
        for (Type p : ft.parameterTypes()) key.append('_').append(mangleTypeForIface(p));
        key.append('_').append(mangleTypeForIface(ft.returnType()));
        String name = "kof/" + key;
        Type.ClassType cached = functionInterfaces.get(name);
        if (cached != null) return cached;
        Type.ClassType iface = new Type.ClassType("kof", key.toString().replace('/', '_'), List.of());
        // método SAM abstract: invoke(params): ret
        IRMethod invoke = new IRMethod("invoke", ft.returnType(), ft.parameterTypes(),
                AccessFlags.PUBLIC | AccessFlags.ABSTRACT, List.of(),
                List.of(), List.of(new IRLocalVariable(0, "this", iface)));
        IRClass cls = new IRClass(name, "java/lang/Object", List.of(),
                AccessFlags.PUBLIC | AccessFlags.INTERFACE | AccessFlags.ABSTRACT,
                List.of(), List.of(invoke), List.of(), null, 400 + lambdaCounter);
        syntheticClasses.add(cls);
        functionInterfaces.put(name, iface);
        return iface;
    }

    private String mangleTypeForIface(Type t) {
        if (t instanceof Type.PrimitiveType pt) return Type.canonicalPrimitiveName(pt.name());
        if (t instanceof Type.ClassType ct) return "C" + ct.name().replace('.', '_');
        if (t instanceof Type.ArrayType at) return "A" + mangleTypeForIface(at.componentType());
        if (t instanceof Type.NullableType nt) return "N" + mangleTypeForIface(nt.inner());
        return "O";
    }


    /**
     * Captured outer locals referenced by the lambda body, in first-reference
     * order. Identifiers shadowed by locals declared inside the lambda are
     * not captured.
     */
    List<IRLocalVariable> collectCaptures(LambdaExpr le, List<IRLocalVariable> outerLocals) {
        List<IRLocalVariable> captures = new ArrayList<>();
        java.util.Set<String> captured = new java.util.HashSet<>();
        java.util.Set<String> shadowed = new java.util.HashSet<>();
        for (FormalParameterNode p : le.parameters()) shadowed.add(p.name());
        collectCapturesStmts(le.body(), outerLocals, captures, captured, shadowed);
        return captures;
    }

    private void collectCapturesStmts(StatementNode stmt, List<IRLocalVariable> outerLocals,
                                      List<IRLocalVariable> captures, java.util.Set<String> captured,
                                      java.util.Set<String> shadowed) {
        collectCapturesStmts(List.of(stmt), outerLocals, captures, captured, shadowed);
    }

    private void collectCapturesStmts(List<StatementNode> body, List<IRLocalVariable> outerLocals,
                                      List<IRLocalVariable> captures, java.util.Set<String> captured,
                                      java.util.Set<String> shadowed) {
        for (StatementNode s : body) {
            if (s instanceof ExpressionStmt es) {
                collectCapturesExpr(es.expression(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof ReturnStmt rs) {
                if (rs.value() != null) {
                    collectCapturesExpr(rs.value(), outerLocals, captures, captured, shadowed);
                }
            } else if (s instanceof BlockStmt b) {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                collectCapturesStmts(b.statements(), outerLocals, captures, captured, inner);
            } else if (s instanceof IfStmt i) {
                collectCapturesExpr(i.condition(), outerLocals, captures, captured, shadowed);
                collectCapturesStmts(i.thenBranch(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
                if (i.elseBranch() != null) {
                    collectCapturesStmts(i.elseBranch(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
            } else if (s instanceof WhileStmt w) {
                collectCapturesExpr(w.condition(), outerLocals, captures, captured, shadowed);
                collectCapturesStmts(w.body(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
            } else if (s instanceof DoWhileStmt dw) {
                collectCapturesStmts(dw.body(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
                collectCapturesExpr(dw.condition(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof ForStmt f) {
                if (f.init() instanceof VarDeclStmt vds) {
                    collectCapturesVarDecl(vds, outerLocals, captures, captured, shadowed);
                } else if (f.init() instanceof ExpressionStmt ies) {
                    collectCapturesExpr(ies.expression(), outerLocals, captures, captured, shadowed);
                }
                if (f.condition() != null) {
                    collectCapturesExpr(f.condition(), outerLocals, captures, captured, shadowed);
                }
                if (f.update() != null) {
                    collectCapturesExpr(f.update(), outerLocals, captures, captured, shadowed);
                }
                collectCapturesStmts(f.body(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
            } else if (s instanceof ForInStmt fi) {
                collectCapturesExpr(fi.collection(), outerLocals, captures, captured, shadowed);
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                inner.add(fi.varName());
                collectCapturesStmts(fi.body(), outerLocals, captures, captured, inner);
            } else if (s instanceof VarDeclStmt vds) {
                collectCapturesVarDecl(vds, outerLocals, captures, captured, shadowed);
            } else if (s instanceof ThrowStmt ts) {
                collectCapturesExpr(ts.expression(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof AssertStmt as) {
                collectCapturesExpr(as.condition(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof SpawnStmt ss) {
                // spawn lambdas have their own (capture-free) scope.
                collectCapturesExpr(ss.expression(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof SwitchStmt sw) {
                collectCapturesExpr(sw.expression(), outerLocals, captures, captured, shadowed);
                for (SwitchCase c : sw.cases()) {
                    if (c.value() != null) {
                        collectCapturesExpr(c.value(), outerLocals, captures, captured, shadowed);
                    }
                    collectCapturesStmts(c.body(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
                if (sw.defaultBody() != null) {
                    collectCapturesStmts(sw.defaultBody(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
            } else if (s instanceof TryStmt ts) {
                collectCapturesStmts(ts.tryBody(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
                for (CatchClause cc : ts.catchClauses()) {
                    java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                    inner.add(cc.exceptionName());
                    collectCapturesStmts(cc.body(), outerLocals, captures, captured, inner);
                }
                collectCapturesStmts(ts.finallyBody(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
            }
        }
    }

    private void collectCapturesVarDecl(VarDeclStmt vds, List<IRLocalVariable> outerLocals,
                                        List<IRLocalVariable> captures, java.util.Set<String> captured,
                                        java.util.Set<String> shadowed) {
        if (vds.initializer() != null) {
            collectCapturesExpr(vds.initializer(), outerLocals, captures, captured, shadowed);
        }
        shadowed.add(vds.name());
    }

    private void collectCapturesExpr(ExpressionNode expr, List<IRLocalVariable> outerLocals,
                                     List<IRLocalVariable> captures, java.util.Set<String> captured,
                                     java.util.Set<String> shadowed) {
        if (expr instanceof IdentifierExpr ie) {
            if (shadowed.contains(ie.name()) || captured.contains(ie.name())) return;
            IRLocalVariable outer = findLocalVar(ie.name(), outerLocals);
            if (outer != null) {
                captures.add(outer);
                captured.add(ie.name());
            }
        } else if (expr instanceof BinaryExpr bin) {
            collectCapturesExpr(bin.left(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(bin.right(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof UnaryExpr ue) {
            collectCapturesExpr(ue.operand(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof AssignmentExpr ae) {
            collectCapturesExpr(ae.target(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(ae.value(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) {
                collectCapturesExpr(mc.receiver(), outerLocals, captures, captured, shadowed);
            }
            for (ExpressionNode arg : mc.arguments()) {
                collectCapturesExpr(arg, outerLocals, captures, captured, shadowed);
            }
        } else if (expr instanceof FieldAccessExpr fa) {
            collectCapturesExpr(fa.receiver(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectCapturesExpr(aa.receiver(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(aa.index(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof IfExpr iex) {
            collectCapturesExpr(iex.condition(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(iex.thenExpr(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(iex.elseExpr(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof SwitchExpr sex) {
            collectCapturesExpr(sex.expression(), outerLocals, captures, captured, shadowed);
            for (SwitchExprCase sc : sex.cases()) {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                if (sc.value() instanceof PatternExpr pe) {
                    if (pe.varName() != null) inner.add(pe.varName());
                    inner.addAll(pe.fieldVars());
                }
                collectCapturesExpr(sc.body(), outerLocals, captures, captured, inner);
            }
            if (sex.defaultValue() != null) {
                collectCapturesExpr(sex.defaultValue(), outerLocals, captures, captured, shadowed);
            }
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode arg : ne.arguments()) {
                collectCapturesExpr(arg, outerLocals, captures, captured, shadowed);
            }
        } else if (expr instanceof NewArrayExpr nae) {
            collectCapturesExpr(nae.size(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof LambdaExpr le2) {
            // lambda retornando lambda: variáveis livres do lambda INTERNO
            // que pertencem ao escopo do EXTERNO são capturas do externo —
            // o interno não pode alcançá-las por conta própria (o externo
            // precisa repassá-las via constructor). Os params/locals do
            // interno entram no shadowed para não virarem capturas.
            java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
            for (FormalParameterNode p : le2.parameters()) inner.add(p.name());
            collectCapturesStmts(le2.body(), outerLocals, captures, captured, inner);
        }
    }

    private void collectMutatedCaptures(List<StatementNode> body, List<IRLocalVariable> params) {
        // Capturas REAIS: uma variável só precisa de box se for capturada por
        // uma lambda (coletCaptures resolve contra os nomes do escopo da função)
        // E mutada em qualquer lugar. Antes só mutações DENTRO da lambda eram
        // detectadas, então `var f = (x) -> x + offset; offset = 20` capturava
        // offset por valor (resultado desatualizado). Ver learn/16-lambdas.md.
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (IRLocalVariable p : params) declared.add(p.name());
        for (StatementNode stmt : body) collectDeclaredVarNamesStmt(stmt, declared);
        java.util.List<IRLocalVariable> outerLocals = new ArrayList<>(params);
        for (String n : declared) {
            if (findLocalVar(n, outerLocals) == null) {
                outerLocals.add(new IRLocalVariable(outerLocals.size(), n, Type.UnknownType.UNKNOWN));
            }
        }
        lambdaCapturedNames.clear();
        java.util.List<LambdaExpr> lambdas = new ArrayList<>();
        for (StatementNode stmt : body) collectLambdasStmt(stmt, lambdas);
        for (LambdaExpr le : lambdas) {
            for (IRLocalVariable c : collectCaptures(le, outerLocals)) {
                lambdaCapturedNames.add(c.name());
            }
        }
        for (StatementNode stmt : body) {
            collectMutatedCapturesStmt(stmt, new java.util.HashSet<>(), false);
        }
    }

    /** Nomes de var-decl no escopo da função (não desce em lambdas). */
    private void collectDeclaredVarNamesStmt(StatementNode stmt, java.util.Set<String> out) {
        if (stmt instanceof ExpressionStmt es) {
            collectDeclaredVarNamesExpr(es.expression());
        } else if (stmt instanceof ReturnStmt rs) {
            if (rs.value() != null) collectDeclaredVarNamesExpr(rs.value());
        } else if (stmt instanceof BlockStmt b) {
            for (StatementNode s : b.statements()) collectDeclaredVarNamesStmt(s, out);
        } else if (stmt instanceof IfStmt i) {
            collectDeclaredVarNamesExpr(i.condition());
            collectDeclaredVarNamesStmt(i.thenBranch(), out);
            if (i.elseBranch() != null) collectDeclaredVarNamesStmt(i.elseBranch(), out);
        } else if (stmt instanceof WhileStmt w) {
            collectDeclaredVarNamesExpr(w.condition());
            collectDeclaredVarNamesStmt(w.body(), out);
        } else if (stmt instanceof DoWhileStmt dw) {
            collectDeclaredVarNamesStmt(dw.body(), out);
            collectDeclaredVarNamesExpr(dw.condition());
        } else if (stmt instanceof ForStmt f) {
            if (f.init() instanceof VarDeclStmt vds) {
                out.add(vds.name());
                if (vds.initializer() != null) collectDeclaredVarNamesExpr(vds.initializer());
            } else if (f.init() instanceof ExpressionStmt ies) {
                collectDeclaredVarNamesExpr(ies.expression());
            }
            if (f.condition() != null) collectDeclaredVarNamesExpr(f.condition());
            if (f.update() != null) collectDeclaredVarNamesExpr(f.update());
            collectDeclaredVarNamesStmt(f.body(), out);
        } else if (stmt instanceof ForInStmt fi) {
            collectDeclaredVarNamesExpr(fi.collection());
            collectDeclaredVarNamesStmt(fi.body(), out);
        } else if (stmt instanceof VarDeclStmt vds) {
            out.add(vds.name());
            if (vds.initializer() != null) collectDeclaredVarNamesExpr(vds.initializer());
        } else if (stmt instanceof ThrowStmt ts) {
            collectDeclaredVarNamesExpr(ts.expression());
        } else if (stmt instanceof AssertStmt as) {
            collectDeclaredVarNamesExpr(as.condition());
        } else if (stmt instanceof SpawnStmt ss) {
            collectDeclaredVarNamesExpr(ss.expression());
        } else if (stmt instanceof SwitchStmt sw) {
            collectDeclaredVarNamesExpr(sw.expression());
            for (SwitchCase c : sw.cases()) {
                if (c.value() != null) collectDeclaredVarNamesExpr(c.value());
                for (StatementNode s : c.body()) collectDeclaredVarNamesStmt(s, out);
            }
            if (sw.defaultBody() != null) {
                for (StatementNode s : sw.defaultBody()) collectDeclaredVarNamesStmt(s, out);
            }
        } else if (stmt instanceof TryStmt ts) {
            for (StatementNode s : ts.tryBody()) collectDeclaredVarNamesStmt(s, out);
            for (CatchClause cc : ts.catchClauses()) {
                for (StatementNode s : cc.body()) collectDeclaredVarNamesStmt(s, out);
            }
            if (ts.finallyBody() != null) {
                for (StatementNode s : ts.finallyBody()) collectDeclaredVarNamesStmt(s, out);
            }
        }
    }

    private void collectDeclaredVarNamesExpr(ExpressionNode expr) {
        if (expr instanceof LambdaExpr) {
            return; // declarações internas da lambda pertencem a ela
        }
        if (expr instanceof BinaryExpr be) {
            collectDeclaredVarNamesExpr(be.left());
            collectDeclaredVarNamesExpr(be.right());
        } else if (expr instanceof UnaryExpr ue) {
            collectDeclaredVarNamesExpr(ue.operand());
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) collectDeclaredVarNamesExpr(mc.receiver());
            for (ExpressionNode a : mc.arguments()) collectDeclaredVarNamesExpr(a);
        } else if (expr instanceof FieldAccessExpr fa) {
            collectDeclaredVarNamesExpr(fa.receiver());
        } else if (expr instanceof AssignmentExpr ae) {
            collectDeclaredVarNamesExpr(ae.target());
            collectDeclaredVarNamesExpr(ae.value());
        } else if (expr instanceof IfExpr iex) {
            collectDeclaredVarNamesExpr(iex.condition());
            collectDeclaredVarNamesExpr(iex.thenExpr());
            collectDeclaredVarNamesExpr(iex.elseExpr());
        } else if (expr instanceof SwitchExpr sex) {
            collectDeclaredVarNamesExpr(sex.expression());
            for (SwitchExprCase sc : sex.cases()) collectDeclaredVarNamesExpr(sc.body());
            if (sex.defaultValue() != null) collectDeclaredVarNamesExpr(sex.defaultValue());
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectDeclaredVarNamesExpr(aa.receiver());
            collectDeclaredVarNamesExpr(aa.index());
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode a : ne.arguments()) collectDeclaredVarNamesExpr(a);
        } else if (expr instanceof NewArrayExpr nae) {
            collectDeclaredVarNamesExpr(nae.size());
        }
    }

    /** Coleta todas as lambdas do corpo (para computar capturas reais). */
    private void collectLambdasStmt(StatementNode stmt, java.util.List<LambdaExpr> out) {
        if (stmt instanceof ExpressionStmt es) {
            collectLambdasExpr(es.expression(), out);
        } else if (stmt instanceof ReturnStmt rs) {
            if (rs.value() != null) collectLambdasExpr(rs.value(), out);
        } else if (stmt instanceof BlockStmt b) {
            for (StatementNode s : b.statements()) collectLambdasStmt(s, out);
        } else if (stmt instanceof IfStmt i) {
            collectLambdasExpr(i.condition(), out);
            collectLambdasStmt(i.thenBranch(), out);
            if (i.elseBranch() != null) collectLambdasStmt(i.elseBranch(), out);
        } else if (stmt instanceof WhileStmt w) {
            collectLambdasExpr(w.condition(), out);
            collectLambdasStmt(w.body(), out);
        } else if (stmt instanceof DoWhileStmt dw) {
            collectLambdasStmt(dw.body(), out);
            collectLambdasExpr(dw.condition(), out);
        } else if (stmt instanceof ForStmt f) {
            if (f.init() instanceof VarDeclStmt vds) {
                if (vds.initializer() != null) collectLambdasExpr(vds.initializer(), out);
            } else if (f.init() instanceof ExpressionStmt ies) {
                collectLambdasExpr(ies.expression(), out);
            }
            if (f.condition() != null) collectLambdasExpr(f.condition(), out);
            if (f.update() != null) collectLambdasExpr(f.update(), out);
            collectLambdasStmt(f.body(), out);
        } else if (stmt instanceof ForInStmt fi) {
            collectLambdasExpr(fi.collection(), out);
            collectLambdasStmt(fi.body(), out);
        } else if (stmt instanceof VarDeclStmt vds) {
            if (vds.initializer() != null) collectLambdasExpr(vds.initializer(), out);
        } else if (stmt instanceof ThrowStmt ts) {
            collectLambdasExpr(ts.expression(), out);
        } else if (stmt instanceof AssertStmt as) {
            collectLambdasExpr(as.condition(), out);
        } else if (stmt instanceof SpawnStmt ss) {
            collectLambdasExpr(ss.expression(), out);
        } else if (stmt instanceof SwitchStmt sw) {
            collectLambdasExpr(sw.expression(), out);
            for (SwitchCase c : sw.cases()) {
                if (c.value() != null) collectLambdasExpr(c.value(), out);
                for (StatementNode s : c.body()) collectLambdasStmt(s, out);
            }
            if (sw.defaultBody() != null) {
                for (StatementNode s : sw.defaultBody()) collectLambdasStmt(s, out);
            }
        } else if (stmt instanceof TryStmt ts) {
            for (StatementNode s : ts.tryBody()) collectLambdasStmt(s, out);
            for (CatchClause cc : ts.catchClauses()) {
                for (StatementNode s : cc.body()) collectLambdasStmt(s, out);
            }
            if (ts.finallyBody() != null) {
                for (StatementNode s : ts.finallyBody()) collectLambdasStmt(s, out);
            }
        }
    }

    private void collectLambdasExpr(ExpressionNode expr, java.util.List<LambdaExpr> out) {
        if (expr instanceof LambdaExpr le) {
            out.add(le);
            return;
        }
        if (expr instanceof BinaryExpr be) {
            collectLambdasExpr(be.left(), out);
            collectLambdasExpr(be.right(), out);
        } else if (expr instanceof UnaryExpr ue) {
            collectLambdasExpr(ue.operand(), out);
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) collectLambdasExpr(mc.receiver(), out);
            for (ExpressionNode a : mc.arguments()) collectLambdasExpr(a, out);
        } else if (expr instanceof FieldAccessExpr fa) {
            collectLambdasExpr(fa.receiver(), out);
        } else if (expr instanceof AssignmentExpr ae) {
            collectLambdasExpr(ae.target(), out);
            collectLambdasExpr(ae.value(), out);
        } else if (expr instanceof IfExpr iex) {
            collectLambdasExpr(iex.condition(), out);
            collectLambdasExpr(iex.thenExpr(), out);
            collectLambdasExpr(iex.elseExpr(), out);
        } else if (expr instanceof SwitchExpr sex) {
            collectLambdasExpr(sex.expression(), out);
            for (SwitchExprCase sc : sex.cases()) collectLambdasExpr(sc.body(), out);
            if (sex.defaultValue() != null) collectLambdasExpr(sex.defaultValue(), out);
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectLambdasExpr(aa.receiver(), out);
            collectLambdasExpr(aa.index(), out);
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode a : ne.arguments()) collectLambdasExpr(a, out);
        } else if (expr instanceof NewArrayExpr nae) {
            collectLambdasExpr(nae.size(), out);
        }
    }

    private void collectMutatedCapturesStmt(StatementNode stmt, java.util.Set<String> shadowed, boolean inLambda) {
        if (stmt instanceof ExpressionStmt es) {
            collectMutatedCapturesExpr(es.expression(), shadowed, inLambda);
        } else if (stmt instanceof ReturnStmt rs) {
            if (rs.value() != null) collectMutatedCapturesExpr(rs.value(), shadowed, inLambda);
        } else if (stmt instanceof BlockStmt b) {
            java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
            for (StatementNode s : b.statements()) collectMutatedCapturesStmt(s, inner, inLambda);
        } else if (stmt instanceof IfStmt i) {
            collectMutatedCapturesExpr(i.condition(), shadowed, inLambda);
            collectMutatedCapturesStmt(i.thenBranch(), new java.util.HashSet<>(shadowed), inLambda);
            if (i.elseBranch() != null) collectMutatedCapturesStmt(i.elseBranch(), new java.util.HashSet<>(shadowed), inLambda);
        } else if (stmt instanceof WhileStmt w) {
            collectMutatedCapturesExpr(w.condition(), shadowed, inLambda);
            collectMutatedCapturesStmt(w.body(), new java.util.HashSet<>(shadowed), inLambda);
        } else if (stmt instanceof DoWhileStmt dw) {
            collectMutatedCapturesStmt(dw.body(), new java.util.HashSet<>(shadowed), inLambda);
            collectMutatedCapturesExpr(dw.condition(), shadowed, inLambda);
        } else if (stmt instanceof ForStmt f) {
            if (f.init() instanceof VarDeclStmt vds) {
                collectMutatedCapturesStmt(vds, shadowed, inLambda);
            } else if (f.init() instanceof ExpressionStmt ies) {
                collectMutatedCapturesExpr(ies.expression(), shadowed, inLambda);
            }
            if (f.condition() != null) collectMutatedCapturesExpr(f.condition(), shadowed, inLambda);
            if (f.update() != null) collectMutatedCapturesExpr(f.update(), shadowed, inLambda);
            collectMutatedCapturesStmt(f.body(), new java.util.HashSet<>(shadowed), inLambda);
        } else if (stmt instanceof ForInStmt fi) {
            collectMutatedCapturesExpr(fi.collection(), shadowed, inLambda);
            java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
            inner.add(fi.varName());
            collectMutatedCapturesStmt(fi.body(), inner, inLambda);
        } else if (stmt instanceof VarDeclStmt vds) {
            shadowed.add(vds.name());
            if (vds.initializer() != null) collectMutatedCapturesExpr(vds.initializer(), shadowed, inLambda);
        } else if (stmt instanceof ThrowStmt ts) {
            collectMutatedCapturesExpr(ts.expression(), shadowed, inLambda);
        } else if (stmt instanceof AssertStmt as) {
            collectMutatedCapturesExpr(as.condition(), shadowed, inLambda);
        } else if (stmt instanceof SpawnStmt ss) {
            collectMutatedCapturesExpr(ss.expression(), shadowed, inLambda);
        } else if (stmt instanceof SwitchStmt sw) {
            collectMutatedCapturesExpr(sw.expression(), shadowed, inLambda);
            for (SwitchCase c : sw.cases()) {
                if (c.value() != null) collectMutatedCapturesExpr(c.value(), shadowed, inLambda);
                for (StatementNode s : c.body()) collectMutatedCapturesStmt(s, new java.util.HashSet<>(shadowed), inLambda);
            }
            if (sw.defaultBody() != null) {
                for (StatementNode s : sw.defaultBody()) collectMutatedCapturesStmt(s, new java.util.HashSet<>(shadowed), inLambda);
            }
        } else if (stmt instanceof TryStmt ts) {
            for (StatementNode s : ts.tryBody()) collectMutatedCapturesStmt(s, new java.util.HashSet<>(shadowed), inLambda);
            for (CatchClause cc : ts.catchClauses()) {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                inner.add(cc.exceptionName());
                for (StatementNode s : cc.body()) collectMutatedCapturesStmt(s, inner, inLambda);
            }
            if (ts.finallyBody() != null) {
                for (StatementNode s : ts.finallyBody()) collectMutatedCapturesStmt(s, new java.util.HashSet<>(shadowed), inLambda);
            }
        }
    }

    private void collectMutatedCapturesExpr(ExpressionNode expr, java.util.Set<String> shadowed, boolean inLambda) {
        if (expr instanceof LambdaExpr le) {
            for (StatementNode s : le.body()) {
                collectMutatedCapturesStmt(s, new java.util.HashSet<>(), true);
            }
        } else if (expr instanceof AssignmentExpr ae) {
            if (ae.target() instanceof IdentifierExpr ie && !shadowed.contains(ie.name())
                    && (inLambda || lambdaCapturedNames.contains(ie.name()))) {
                mutatedCapturedNames.add(ie.name());
            }
            collectMutatedCapturesExpr(ae.target(), shadowed, inLambda);
            collectMutatedCapturesExpr(ae.value(), shadowed, inLambda);
        } else if (expr instanceof UnaryExpr ue) {
            if (inLambda && ue.operand() instanceof IdentifierExpr ie && !shadowed.contains(ie.name())
                    && ("++".equals(ue.operator()) || "--".equals(ue.operator()))) {
                mutatedCapturedNames.add(ie.name());
            }
            collectMutatedCapturesExpr(ue.operand(), shadowed, inLambda);
        } else if (expr instanceof BinaryExpr bin) {
            collectMutatedCapturesExpr(bin.left(), shadowed, inLambda);
            collectMutatedCapturesExpr(bin.right(), shadowed, inLambda);
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) collectMutatedCapturesExpr(mc.receiver(), shadowed, inLambda);
            for (ExpressionNode arg : mc.arguments()) collectMutatedCapturesExpr(arg, shadowed, inLambda);
        } else if (expr instanceof FieldAccessExpr fa) {
            collectMutatedCapturesExpr(fa.receiver(), shadowed, inLambda);
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectMutatedCapturesExpr(aa.receiver(), shadowed, inLambda);
            collectMutatedCapturesExpr(aa.index(), shadowed, inLambda);
        } else if (expr instanceof IfExpr iex) {
            collectMutatedCapturesExpr(iex.condition(), shadowed, inLambda);
            collectMutatedCapturesExpr(iex.thenExpr(), shadowed, inLambda);
            collectMutatedCapturesExpr(iex.elseExpr(), shadowed, inLambda);
        } else if (expr instanceof SwitchExpr sex) {
            collectMutatedCapturesExpr(sex.expression(), shadowed, inLambda);
            for (SwitchExprCase sc : sex.cases()) {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                if (sc.value() instanceof PatternExpr pe) {
                    if (pe.varName() != null) inner.add(pe.varName());
                    inner.addAll(pe.fieldVars());
                }
                collectMutatedCapturesExpr(sc.body(), inner, inLambda);
            }
            if (sex.defaultValue() != null) {
                collectMutatedCapturesExpr(sex.defaultValue(), shadowed, inLambda);
            }
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode arg : ne.arguments()) collectMutatedCapturesExpr(arg, shadowed, inLambda);
        } else if (expr instanceof NewArrayExpr nae) {
            collectMutatedCapturesExpr(nae.size(), shadowed, inLambda);
        }
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


    Type listOfElementType(MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (!mc.arguments().isEmpty()) {
            return ExpressionTyper.inferExprType(this, mc.arguments().get(0), locals);
        }
        if (!mc.typeArguments().isEmpty()) {
            return CompilerTypes.toType(mc.typeArguments().get(0), currentUnit);
        }
        return Type.UnknownType.UNKNOWN;
    }


    private IRMethod lowerFunction(FunctionDeclarationNode func) {
        String prevOwner = currentLoweringOwner;
        currentLoweringOwner = mainClassInternalName();
        try {
            return lowerFunctionInner(func);
        } finally {
            currentLoweringOwner = prevOwner;
        }
    }

    private String mainClassInternalName() {
        return "Default/Main";
    }

    private IRMethod lowerFunctionInner(FunctionDeclarationNode func) {
        Type returnType = CompilerTypes.resolveWithTypeParams(func.returnType(), func.typeParameters(), currentUnit, semanticAnalyzer);
        if (Type.isVoid(returnType)) {
            // inferência de retorno: percorre o corpo acumulando locais
            // (params + var decls) até achar um ReturnStmt com valor
            List<IRLocalVariable> tmpLocals = new ArrayList<>();
            int tmpIdx = 0;
            for (FormalParameterNode p : func.parameters()) {
                Type pt = CompilerTypes.resolveWithTypeParams(p.type(), func.typeParameters(), currentUnit, semanticAnalyzer);
                tmpLocals.add(new IRLocalVariable(tmpIdx, p.name(), pt));
                tmpIdx += TypeMetrics.isDoubleWidth(pt) ? 2 : 1;
            }
            for (StatementNode stmt : func.body()) {
                if (stmt instanceof VarDeclStmt vds && vds.initializer() != null) {
                    Type vt = vds.type() != null && !"var".equals(vds.type())
                            ? CompilerTypes.toType(vds.type(), currentUnit)
                            : ExpressionTyper.inferExprType(this, vds.initializer(), tmpLocals);
                    tmpLocals.add(new IRLocalVariable(tmpIdx, vds.name(), vt));
                    tmpIdx += TypeMetrics.isDoubleWidth(vt) ? 2 : 1;
                }
                if (stmt instanceof ReturnStmt ret && ret.value() != null) {
                    Type inferred = ExpressionTyper.inferExprType(this, ret.value(), tmpLocals);
                    if (!(inferred instanceof Type.UnknownType) && !Type.isVoid(inferred)) {
                        returnType = inferred;
                    }
                    break;
                }
                if (stmt instanceof ExpressionStmt es && es.expression() instanceof MethodCallExpr) {
                    break; // void call termina a busca
                }
            }
        }
        List<Type> paramTypes = func.parameters().stream()
                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), func.typeParameters(), currentUnit, semanticAnalyzer)).toList();
        boolean mainArgsList = "main".equals(func.name()) && func.parameters().size() == 1
                && "args".equals(func.parameters().get(0).name())
                && BuiltinTypes.isList(paramTypes.get(0));
        boolean isMain = "main".equals(func.name())
                && (paramTypes.isEmpty() || mainArgsList);
        if (isMain) {
            paramTypes = List.of(new Type.ArrayType(BuiltinTypes.STRING));
        }
        boolean prevMain = loweringMain;
        loweringMain = isMain;
        boolean prevMainArgsList = mainArgsListField;
        mainArgsListField = mainArgsList;
        int access = AccessFlags.PUBLIC | AccessFlags.STATIC;
        List<IRLocalVariable> locals = new ArrayList<>();
        List<KofOperation> body = new ArrayList<>();
        int localIdx = 0;
        if (isMain) {
            if (mainArgsList) {
                // args: List<String> — convert the injected String[] once
                // at method entry (JVM); Native/JS start with an empty list.
                if (target == Target.JVM) {
                    body.add(new KofLoadLocal(new Type.ArrayType(BuiltinTypes.STRING), 0));
                    body.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_args_list", List.of(new Type.ArrayType(BuiltinTypes.STRING)),
                            BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    body.add(new KofStoreLocal(BuiltinTypes.LIST, 1));
                } else if (target == Target.JS) {
                    body.add(new KofCall(BuiltinTypes.LIST, "kof_args", List.of(),
                            BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    body.add(new KofStoreLocal(BuiltinTypes.LIST, 1));
                } else {
                    body.add(new KofCall(BuiltinTypes.LIST, "kof_list_new", List.of(),
                            BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    body.add(new KofStoreLocal(BuiltinTypes.LIST, 1));
                }
            }
            localIdx = 1;
        }
        for (FormalParameterNode p : func.parameters()) {
            Type paramType = CompilerTypes.resolveWithTypeParams(p.type(), func.typeParameters(), currentUnit, semanticAnalyzer);
            locals.add(new IRLocalVariable(localIdx, p.name(), paramType));
            localIdx += TypeMetrics.isDoubleWidth(paramType) ? 2 : 1;
        }
        java.util.Set<String> savedMutated = mutatedCapturedNames;
        mutatedCapturedNames = new java.util.HashSet<>();
        collectMutatedCaptures(func.body(), locals);
        for (StatementNode stmt : func.body()) {
            localIdx = emitStatement(stmt, body, "", localIdx, locals, returnType);
        }
        mutatedCapturedNames = savedMutated;
        KofOperation last = body.isEmpty() ? null : body.get(body.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(returnType)) body.add(new KofReturnVoid());
            else body.add(new KofReturn(returnType));
        }
        KofDebugInfo debugInfo = currentDebugPositions.isEmpty()
                ? KofDebugInfo.EMPTY
                : new KofDebugInfo(new java.util.HashMap<>(currentDebugPositions));
        currentDebugPositions.clear();
        loweringMain = prevMain;
        mainArgsListField = prevMainArgsList;
        return new IRMethod(func.name(), returnType, paramTypes, access, func.thrownExceptions(),
                List.of(new IRBasicBlock(0, body)), locals, debugInfo,
                lowerAnnotations(func.annotations()), lowerParameterAnnotations(func.parameters()));
    }

    /**
     * Default parameter values: for each trailing default, a wrapper with the
     * same name and fewer parameters is generated. The wrapper evaluates the
     * default expressions and delegates to the canonical function — pure
     * compile-time semantics, no runtime machinery.
     */
    private List<IRMethod> lowerFunctionDefaults(FunctionDeclarationNode func) {
        List<IRMethod> wrappers = new ArrayList<>();
        List<FormalParameterNode> params = func.parameters();
        if (params.isEmpty() || params.stream().noneMatch(p -> p.defaultExpression() != null)) {
            return wrappers;
        }
        if ("main".equals(func.name())) return wrappers;
        int n = params.size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (params.get(i).defaultExpression() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return wrappers;
        List<Type> canonicalTypes = params.stream()
                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), func.typeParameters(), currentUnit, semanticAnalyzer)).toList();
        Type returnType = CompilerTypes.resolveWithTypeParams(func.returnType(), func.typeParameters(), currentUnit, semanticAnalyzer);
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = canonicalTypes.subList(0, paramCount);
            List<IRLocalVariable> locals = new ArrayList<>();
            List<KofOperation> ops = new ArrayList<>();
            int localIdx = 0;
            for (int i = 0; i < paramCount; i++) {
                locals.add(new IRLocalVariable(localIdx, params.get(i).name(), paramTypes.get(i)));
                ops.add(new KofLoadLocal(paramTypes.get(i), localIdx));
                localIdx++;
            }
            for (int i = paramCount; i < n; i++) {
                localIdx = ExpressionLowerer.emitExpression(this, params.get(i).defaultExpression(), ops, "",
                        localIdx, locals);
            }
            ops.add(new KofCall(CompilerTypes.mainClassType(currentModule), func.name(), canonicalTypes,
                    returnType, KofCallKind.FUNCTION));
            ops.add(new KofReturn(returnType));
            wrappers.add(new IRMethod(func.name(), returnType, paramTypes,
                    AccessFlags.PUBLIC | AccessFlags.STATIC, func.thrownExceptions(),
                    List.of(new IRBasicBlock(0, ops)), locals));
        }
        return wrappers;
    }

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

    private boolean needsErasureBoxing() {
        return target == Target.JVM;
    }

    private boolean isJvmTarget() {
        return target == Target.JVM;
    }





    /** Compatibilidade largura para fallback de resolução de construtor:
     *  primitivos por largura, tipos de referência por hierarquia, Unknown aceita tudo. */

    boolean ctorCompatible(Type formal, Type arg) {
        if (formal == null || arg == null) return true;
        if (Type.isUnknown(formal) || Type.isUnknown(arg)) return true;
        if (formal.equals(arg)) return true;
        if (formal instanceof Type.PrimitiveType fp && arg instanceof Type.PrimitiveType ap) {
            return TypeMetrics.primWidth(ap) <= TypeMetrics.primWidth(fp);
        }
        if (formal instanceof Type.ClassType fc && arg instanceof Type.ClassType ac
                && semanticAnalyzer != null) {
            java.util.Set<String> visited = new java.util.HashSet<>();
            java.util.Queue<String> queue = new java.util.LinkedList<>();
            queue.add(ac.name());
            visited.add(ac.name());
            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current.equals(fc.name())) return true;
                SymbolTable.ClassSymbol cur = semanticAnalyzer.getClass(current);
                if (cur == null) continue;
                if (cur.superClass() != null && !cur.superClass().equals("java/lang/Object")
                        && visited.add(cur.superClass())) queue.add(cur.superClass());
                for (String i : cur.interfaces()) {
                    if (visited.add(i)) queue.add(i);
                }
            }
        }
        return true;
    }

    void emitWideningIfNeeded(List<KofOperation> ops, Type from, Type to) {
        if (from.equals(to)) return;
        String fn = TypeMetrics.primitiveName(from);
        String tn = TypeMetrics.primitiveName(to);
        KofUnaryOp conv = switch (tn) {
            case "long", "Long" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2L;
                default -> null;
            };
            case "float", "Float" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2F;
                case "long", "Long" -> KofUnaryOp.L2F;
                case "double", "Double" -> KofUnaryOp.D2F;
                default -> null;
            };
            case "double", "Double" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2D;
                case "long", "Long" -> KofUnaryOp.L2D;
                case "float", "Float" -> KofUnaryOp.F2D;
                default -> null;
            };
            default -> null;
        };
        if (conv != null) {
            ops.add(new KofUnary(conv, from));
        }
    }

    void emitPrimNarrow(List<KofOperation> ops, Type from, Type to) {
        if (from.equals(to)) return;
        String fn = TypeMetrics.primitiveName(from);
        String tn = TypeMetrics.primitiveName(to);
        KofUnaryOp conv = switch (tn) {
            case "int", "Int" -> switch (fn) {
                case "long", "Long" -> KofUnaryOp.L2I;
                case "float", "Float" -> KofUnaryOp.F2I;
                case "double", "Double" -> KofUnaryOp.D2I;
                default -> null;
            };
            case "long", "Long" -> switch (fn) {
                case "float", "Float" -> KofUnaryOp.F2L;
                case "double", "Double" -> KofUnaryOp.D2L;
                default -> null;
            };
            default -> null;
        };
        if (conv != null) {
            ops.add(new KofUnary(conv, from));
        }
    }

    static boolean isZeroLiteral(LiteralExpr lit) {
        if (lit.value() == null) return false;
        String v = lit.value().trim();
        boolean zero = "0".equals(v) || "-0".equals(v)
                || "0.0".equals(v) || "-0.0".equals(v) || "0.00".equals(v);
        return switch (lit.kind()) {
            case ConcreteLiteralKind.INT, ConcreteLiteralKind.LONG -> zero;
            case ConcreteLiteralKind.FLOAT, ConcreteLiteralKind.DOUBLE -> zero;
            default -> false;
        };
    }


    void emitErasureBox(List<KofOperation> ops, Type primitive) {
        if (!needsErasureBoxing()) return;
        Type boxed = TypeMetrics.boxedTypeFor(primitive);
        Type boxParam = primitive instanceof Type.PrimitiveType pt
                && ("char".equals(pt.name()) || "Char".equals(pt.name())) ? Type.PrimitiveType.INT : primitive;
        ops.add(new KofCall(boxed, "kof_box", List.of(boxParam), boxed, KofCallKind.FUNCTION));
    }

    void emitErasureUnbox(List<KofOperation> ops, Type primitive) {
        if (!needsErasureBoxing()) return;
        Type boxed = TypeMetrics.boxedTypeFor(primitive);
        ops.add(new KofCall(primitive, "kof_unbox", List.of(boxed), primitive, KofCallKind.FUNCTION));
    }

    boolean erasesToReference(Type t) {
        return t instanceof Type.TypeVariable || t instanceof Type.ClassType
                || t instanceof Type.ArrayType || t instanceof Type.UnknownType;
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
        if (lambdaUsesSuper(le) && currentLoweringOwner != null) {
            Type outerType = CompilerTypes.ownerTypeFromInternal(currentLoweringOwner, semanticAnalyzer);
            List<IRLocalVariable> eff = new ArrayList<>();
            eff.add(new IRLocalVariable(0, "$outer", outerType));
            eff.addAll(captures);
            captures = eff;
        }
        String className = samAdapterNames.computeIfAbsent(le,
                k -> "Sam" + iface.name().replace('.', '_') + "_" + (++lambdaCounter));
        if (lambdaUsesSuper(le) && currentLoweringOwner != null) {
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

        if (!samAdapterNames.containsValue(className) || !syntheticExists(className)) {
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

    boolean syntheticExists(String name) {
        for (IRClass c : syntheticClasses) {
            if (c.name().equals(name)) return true;
        }
        return false;
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
    boolean fpSupportedOnNative(Type type, SourcePosition pos) {
        // Native float/double now supported via XMM (was FLT001) — KofJS always was
        return true;
    }



    boolean jsonSupported(Type type, boolean isDecode) {
        Type check = BuiltinTypes.isList(type) ? listElementType(type) : type;
        if (check instanceof Type.PrimitiveType pt && ("float".equals(pt.name()) || "double".equals(pt.name()))) {
            // JSN001 fechado: encode/decode float/double no Native
            // (kof_json_encode_double + kof_string_to_double, FP XMM).
            return true;
        }
        if (isDecode && type instanceof Type.ArrayType at) {
            // JSN003 fechado: int/long/bool/string[] tem decoders nativos.
            // JSN001: float/double[] também decodifica no Native.
            return true;
        }
        if (check instanceof Type.ClassType && target.isNative() && !BuiltinTypes.isList(type)
                && !BuiltinTypes.isString(type)) {
            // JSN002 fechado para classes cujos campos sao todos suportados
            // pelo walker nativo (primitivos, string e objetos aninhados).
            String cn = check instanceof Type.ClassType ct
                    ? (ct.packageName().isEmpty() ? ct.name()
                      : ct.packageName() + "." + ct.name())
                    : "";
            if (!nativeObjJsonFieldsOk(cn, new java.util.HashSet<>(), null)) {
                return false;
            }
        }
        return true;
    }

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
            if (!fieldOk(f[1], className, visiting)) ok = false;
        }
        return ok;
    }

    // v1 flat: objetos aninhados ainda nao sao suportados pelo walker
    boolean fieldOk(String typeName, String className, java.util.Set<String> visiting) {
        Type t = CompilerTypes.toType(typeName, currentUnit);
        if (t instanceof Type.PrimitiveType) return true;
        if (BuiltinTypes.isString(t)) return true;
        if (currentDiagnostics != null) {
            currentDiagnostics.error("", 0, 0, 0,
                    "json: class " + className + " has field of type " + typeName
                            + " not supported by the Native JSON encoder yet"
                            + " (use int, long, bool or string fields; nested objects coming soon)",
                    "JSN002");
        }
        return false;
    }




    Type listElementType(Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }



    boolean isComparisonShortcut(BinaryExpr bin, List<IRLocalVariable> locals) {
        if (!TypeMetrics.isComparisonOp(bin.operator())) return false;
        if ("==".equals(bin.operator()) || "!=".equals(bin.operator())) {
            Type left = ExpressionTyper.inferExprType(this, bin.left(), locals);
            Type right = ExpressionTyper.inferExprType(this, bin.right(), locals);
            if (Type.isString(left) || Type.isString(right)) return false;
            // enum == enum compara conteúdo (string) — nunca identidade
            if (CompilerTypes.isEnumType(left, currentUnit) || CompilerTypes.isEnumType(right, currentUnit)) return false;
            // primitivo vs null → constante (caminho da cadeia binária)
            boolean leftNull = bin.left() instanceof LiteralExpr ll2 && ll2.kind() == ConcreteLiteralKind.NULL;
            boolean rightNull = bin.right() instanceof LiteralExpr rl2 && rl2.kind() == ConcreteLiteralKind.NULL;
            if ((leftNull && TypeMetrics.isPrimitiveType(right)) || (rightNull && TypeMetrics.isPrimitiveType(left))) return false;
        }
        return true;
    }

    /**
     * Operand type of a comparison shortcut: the common numeric type of the
     * two operands (int, long, float or double). The IR carries it so the
     * JVM backend can emit the correct compare instruction.
     */
    Type comparisonOperandType(BinaryExpr bin, List<IRLocalVariable> locals) {
        Type left = ExpressionTyper.inferExprType(this, bin.left(), locals);
        Type right = ExpressionTyper.inferExprType(this, bin.right(), locals);
        if (TypeMetrics.isNumeric(left) && TypeMetrics.isNumeric(right)) {
            return TypeMetrics.commonNumericType(left, right);
        }
        // comparação contra literal null é sempre referência (if_acmp*);
        // quando o outro lado é Unknown (get de Map, etc.) marca como Object
        if (isNullLiteral(bin.left()) || isNullLiteral(bin.right())) {
            Type other = isNullLiteral(bin.left()) ? right : left;
            if (other instanceof Type.ClassType || other instanceof Type.ArrayType
                    || other instanceof Type.TypeVariable || other instanceof Type.NullableType) {
                return other;
            }
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        // referências conhecidas (String vs String, record vs record):
        // preserva o tipo para o backend emitir if_acmp*
        if (left instanceof Type.ClassType || left instanceof Type.ArrayType
                || left instanceof Type.TypeVariable || left instanceof Type.NullableType) {
            return left;
        }
        if (right instanceof Type.ClassType || right instanceof Type.ArrayType
                || right instanceof Type.TypeVariable || right instanceof Type.NullableType) {
            return right;
        }
        return Type.PrimitiveType.INT;
    }

    boolean isNullLiteral(ExpressionNode e) {
        return e instanceof LiteralExpr le && le.kind() == ConcreteLiteralKind.NULL;
    }

    /**
     * Emits both operands of a comparison-shortcut condition, widening each
     * to the common numeric type (e.g. `longExpr < 2000` must widen the
     * literal before the compare).
     */
    int emitComparisonShortcut(BinaryExpr bin, List<KofOperation> ops, String owner,
                                       int localIdx, List<IRLocalVariable> locals) {
        Type common = comparisonOperandType(bin, locals);
        if (!fpSupportedOnNative(common, bin.position())) {
            return localIdx;
        }
        localIdx = ExpressionLowerer.emitExpression(this, bin.left(), ops, owner, localIdx, locals);
        emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(this, bin.left(), locals), common);
        localIdx = ExpressionLowerer.emitExpression(this, bin.right(), ops, owner, localIdx, locals);
        emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(this, bin.right(), locals), common);
        return localIdx;
    }

    KofComparison mapComparison(String op) {
        return switch (op) {
            case ">" -> KofComparison.GT;
            case "<" -> KofComparison.LT;
            case ">=" -> KofComparison.GE;
            case "<=" -> KofComparison.LE;
            case "==" -> KofComparison.EQ;
            case "!=" -> KofComparison.NE;
            default -> KofComparison.NE;
        };
    }

    private KofComparison invertComparison(String op) {
        return switch (op) {
            case ">" -> KofComparison.LE;
            case "<" -> KofComparison.GE;
            case ">=" -> KofComparison.LT;
            case "<=" -> KofComparison.GT;
            case "==" -> KofComparison.NE;
            case "!=" -> KofComparison.EQ;
            default -> KofComparison.NE;
        };
    }

    // Int → Long[] slot (I2L) ou Long → Int[] slot (L2I): sem isso o emit
    // do array store usa o opcode do slot com um valor do outro tipo e o
    // verifier rejeita (frame crash / VerifyError "JavaFX").
    void emitPrimWidenNarrow(List<KofOperation> ops, ExpressionNode value,
                                     Type elemType, List<IRLocalVariable> locals) {
        Type vt = ExpressionTyper.inferExprType(this, value, locals);
        if (elemType instanceof Type.PrimitiveType et && vt instanceof Type.PrimitiveType st) {
            if ("long".equals(et.name()) && "int".equals(st.name())) {
                ops.add(new KofUnary(KofUnaryOp.I2L, Type.PrimitiveType.INT));
            } else if ("int".equals(et.name()) && "long".equals(st.name())) {
                ops.add(new KofUnary(KofUnaryOp.L2I, Type.PrimitiveType.LONG));
            }
        }
    }

    boolean hasReturnValue(ExpressionNode expr, List<IRLocalVariable> locals) {
        return hasReturnValueInner(expr, locals);
    }

    private boolean hasReturnValueInner(ExpressionNode expr, List<IRLocalVariable> locals) {
        if (expr instanceof AssignmentExpr) return false;
        if (expr instanceof MethodCallExpr mc) {
            if ("print".equals(mc.methodName()) || "println".equals(mc.methodName())) return false;
            // cache.* primeiro: cache.delete é void e o nome colide com o
            // File.delete do Io (que o check genérico abaixo não sabe tipar
            // com receiver Unknown) — sem isto o Pop extra diverge o frame
            // idem emit: `cache` pode ser VARIÁVEL LOCAL List (kof_list_add) —
            // só é namespace builtin se não for local/param (frame COMP002:
            // pop duplo em cache.add(...) com local chamado "cache")
            if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                    && KofCache.isCacheNamespace(rid.name())) {
                List<Type> cacheArgTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) cacheArgTypes.add(ExpressionTyper.inferExprType(this, arg, locals));
                KofCache.CacheCall cc = KofCache.staticCall(mc.methodName(), cacheArgTypes);
                if (cc == null) return true;
                return !(cc.returnType() instanceof Type.PrimitiveType pt && "void".equals(pt.name()));
            }
            // gpu.*: todas as funções retornam valor (bool/str/int)
            if (mc.receiver() instanceof IdentifierExpr rid && KofGpu.isGpuNamespace(rid.name())) {
                return true;
            }
            if (mc.receiver() != null && KofIo.instanceMethod(Type.UnknownType.UNKNOWN,
                    mc.methodName(), mc.arguments().size()) != null) {
                return true;
            }
            // List methods that leave a value on the stack (get, remove,
            // size, contains, isEmpty) must be popped at statement level;
            // add/set/clear are already popped by the JVM backend.
            if (mc.receiver() != null && BuiltinTypes.isList(ExpressionTyper.inferExprType(this, mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "get", "remove", "size", "length", "count",
                            "contains", "isEmpty" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() != null && BuiltinTypes.isMap(ExpressionTyper.inferExprType(this, mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "get", "remove", "put", "size", "length", "count",
                            "contains", "containsKey", "isEmpty", "keys", "values" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() != null && BuiltinTypes.isSet(ExpressionTyper.inferExprType(this, mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "contains", "isEmpty", "size", "length", "count",
                            "add", "remove" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() instanceof IdentifierExpr rid && KofOrm.isOrmNamespace(rid.name())) {
                // todos os orm.* retornam valor (Bool/Object/List/Long) — antes
                // dos checks genéricos (o "delete" também é rota do web)
                return true;
            }
            if (mc.receiver() != null) {
                List<Type> webArgTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) webArgTypes.add(ExpressionTyper.inferExprType(this, arg, List.of()));
                KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
                if (webCall != null) {
                    return !(webCall.returnType() instanceof Type.PrimitiveType pt && "void".equals(pt.name()));
                }
            }
            if (mc.receiver() instanceof IdentifierExpr rid && KofIo.isConstructor(rid.name())
                    && KofIo.staticMethod(rid.name(), mc.methodName(), mc.arguments().size()) != null) {
                return true;
            }
            if (semanticAnalyzer != null) {
                SymbolTable.MethodSymbol resolved = semanticAnalyzer.getResolvedMethod(mc);
                if (resolved != null) {
                    // add/set/clear de coleção builtin: o JVM backend já
                    // descarta o valor no emit (POP) — um KofPop extra aqui
                    // vira stack underflow no merge de frames (COMP002)
                    String oc = resolved.ownerClass();
                    if (("List".equals(oc) || "ArrayList".equals(oc) || "java/util/List".equals(oc)
                            || "Map".equals(oc) || "HashMap".equals(oc)
                            || "Set".equals(oc) || "HashSet".equals(oc))
                            && ("add".equals(mc.methodName()) || "push".equals(mc.methodName())
                                || "append".equals(mc.methodName()) || "set".equals(mc.methodName())
                                || "clear".equals(mc.methodName()) || "put".equals(mc.methodName()))) {
                        return false;
                    }
                    Type resolvedType = resolved.returnType();
                    if (Type.isVoid(resolvedType)) return false;
                    return !(resolvedType instanceof Type.UnknownType);
                }
            }
            Type t = ExpressionTyper.inferExprType(this, mc, locals);
            if (t instanceof Type.UnknownType || Type.isVoid(t)) return false;
            // add/push/append/set/clear/put de coleção: o emit do backend
            // já descarta o valor (POP no kof_list_add/kof_map_put) — sem
            // KofPop aqui (underflow no merge de frames, COMP002).
            if (mc.receiver() instanceof IdentifierExpr) {
                String mn = mc.methodName();
                if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                        || "set".equals(mn) || "clear".equals(mn) || "put".equals(mn)) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

    private IRClass lowerClass(ClassDeclarationNode cls, String packageName, int typeId) {
        String internalName = toInternalName(packageName, cls.name());
        // usa o superClass QUALIFICADO pelo analyzer ("extends Activity" +
        // import → android/app/Activity); cai pro cru se analyzer ausente
        String superName = null;
        if (semanticAnalyzer != null) {
            SymbolTable.ClassSymbol sym = semanticAnalyzer.getClass(cls.name());
            if (sym != null && sym.superClass() != null && !"Object".equals(sym.superClass())) {
                superName = toInternalName("", sym.superClass());
            }
        }
        if (superName == null) {
            superName = cls.superClass() != null ? toInternalName("", cls.superClass())
                    : "java/lang/Object";
        }
        List<String> ifaces = cls.interfaces().stream().map(this::externalOrLocalInternalName).toList();
        int access = computeAccess(cls.modifiers());
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        java.util.Map<String, ExpressionNode> fieldInits = new java.util.LinkedHashMap<>();
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) {
                IRField irField = lowerField(field, cls.typeParameters());
                fields.add(irField);
                if (field.initializer() != null && irField.initialValue() == null) {
                    fieldInits.put(field.name(), field.initializer());
                }
            } else if (member instanceof MethodDeclarationNode method) {
                methods.add(lowerMethod(method, internalName, false, cls.typeParameters()));
            } else if (member instanceof ConstructorDeclarationNode ctor) {
                methods.add(lowerConstructor(ctor, internalName, superName, cls.typeParameters(), fields, fieldInits));
                methods.addAll(lowerConstructorDefaults(ctor, internalName, superName,
                        cls.typeParameters(), fields, fieldInits));
            }
        }
        if (!methods.stream().anyMatch(m -> m.name().equals("<init>"))) {
            methods.add(0, generateDefaultConstructor(internalName, superName, fields, fieldInits));
        }
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null,
                typeId, lowerAnnotations(cls.annotations()));
    }

    private IRClass lowerInterface(InterfaceDeclarationNode iface, String packageName, int typeId) {
        String internalName = toInternalName(packageName, iface.name());
        List<String> ifaces = iface.interfaces().stream().map(this::externalOrLocalInternalName).toList();
        int access = computeAccess(iface.modifiers()) | AccessFlags.ABSTRACT | AccessFlags.INTERFACE;
        List<IRMethod> methods = new ArrayList<>();
        List<IRField> fields = new ArrayList<>();
        for (AstNode member : iface.members()) {
            if (member instanceof MethodDeclarationNode method) methods.add(lowerMethod(method, internalName, true, List.of()));
            else if (member instanceof FieldDeclarationNode field) fields.add(lowerField(field, List.of()));
        }
        return new IRClass(internalName, "java/lang/Object", ifaces, access, fields, methods, List.of(), null,
                typeId, lowerAnnotations(iface.annotations()));
    }

    private IRClass lowerRecord(RecordDeclarationNode rec, String packageName, int typeId) {
        String internalName = toInternalName(packageName, rec.name());
        String superName = "java/lang/Record";
        List<String> ifaces = rec.interfaces().stream().map(this::externalOrLocalInternalName).toList();
        int access = computeAccess(rec.modifiers()) | AccessFlags.FINAL | AccessFlags.PUBLIC;
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        List<String> typeParams = rec.typeParameters() == null ? List.of() : rec.typeParameters();
        for (RecordComponentNode comp : rec.components()) {
            fields.add(new IRField(comp.name(), CompilerTypes.resolveWithTypeParams(comp.type(), typeParams, currentUnit, semanticAnalyzer),
                    AccessFlags.PRIVATE | AccessFlags.FINAL,
                    null, lowerAnnotations(comp.annotations())));
        }
        methods.add(0, generateRecordConstructor(rec, internalName));
        methods.addAll(generateRecordDefaultOverloads(rec, internalName));
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, semanticAnalyzer);
        for (RecordComponentNode comp : rec.components()) {
            Type compType = CompilerTypes.resolveWithTypeParams(comp.type(), typeParams, currentUnit, semanticAnalyzer);
            List<KofOperation> body = new ArrayList<>();
            body.add(new KofLoadLocal(ownerType, 0));
            body.add(new KofLoadField(ownerType, comp.name(), compType));
            body.add(new KofReturn(compType));
            methods.add(new IRMethod(comp.name(), compType, List.of(), AccessFlags.PUBLIC, List.of(),
                    List.of(new IRBasicBlock(0, body)),
                    List.of(new IRLocalVariable(0, "this", ownerType))));
        }
        for (AstNode member : rec.members()) {
            if (member instanceof MethodDeclarationNode method) {
                methods.add(lowerMethod(method, internalName, false, typeParams));
            } else if (member instanceof ConstructorDeclarationNode ctor) {
                methods.add(lowerConstructor(ctor, internalName, "java/lang/Record",
                        typeParams, fields, java.util.Map.of()));
            }
        }
        // Native: records não geram toString/equals nos backends (JVM/JS
        // geram nos seus emitters). Sintetiza no IR para paridade — bug 11
        // (native `==` dava undefined reference) e toString imprimia o handle.
        if (target == Target.NATIVE || target == Target.NATIVE_RISCV64
                || target == Target.NATIVE_AARCH64) {
            methods.add(buildRecordToStringMethod(internalName, rec, fields, typeParams));
            methods.add(buildRecordEqualsMethod(internalName, fields, typeParams));
        }
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null,
                typeId, lowerAnnotations(rec.annotations()));
    }


    private IRField lowerField(FieldDeclarationNode field, List<String> typeParams) {
        Type fieldType = CompilerTypes.resolveWithTypeParams(field.type(), typeParams, currentUnit, semanticAnalyzer);
        Object initVal = null;
        if (field.initializer() instanceof LiteralExpr lit) {
            initVal = switch (lit.kind()) {
                case ConcreteLiteralKind.INT -> parseIntLiteral(lit.value());
                case ConcreteLiteralKind.LONG -> Long.parseLong(stripSuffix(lit.value()));
                case ConcreteLiteralKind.FLOAT -> Float.parseFloat(stripSuffix(lit.value()));
                case ConcreteLiteralKind.DOUBLE -> Double.parseDouble(stripSuffix(lit.value()));
                case ConcreteLiteralKind.STRING -> lit.value();
                case ConcreteLiteralKind.BOOLEAN -> Boolean.parseBoolean(lit.value()) ? 1 : 0;
                default -> null;
            };
        }
        return new IRField(field.name(), fieldType, computeAccess(field.modifiers()), initVal,
                lowerAnnotations(field.annotations()));
    }

    /**
     * Converte annotations do AST para a IR: nome resolvido para o formato
     * interno JVM e valores já constantes (o parser só aceita literais).
     */
    private List<IRAnnotation> lowerAnnotations(List<AnnotationNode> annos) {
        if (annos == null || annos.isEmpty()) return List.of();
        List<IRAnnotation> out = new ArrayList<>();
        for (AnnotationNode anno : annos) {
            java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
            for (AnnotationPair pair : anno.pairs()) {
                String key = pair.key() != null ? pair.key()
                        : (anno.pairs().size() == 1 ? "value" : null);
                if (key != null) {
                    Object folded = foldAnnotationValue(pair.value());
                    if (!(folded instanceof ParseSentinel)) values.put(key, folded);
                }
            }
            out.add(new IRAnnotation(resolveAnnotationInternalName(anno.name()), values));
        }
        return out;
    }

    /**
     * Nome interno JVM de uma interface declarada: simples vinda de import
     * ("import android.view.OnClickListener") qualifica; senão, classe local.
     */
    private String externalOrLocalInternalName(String name) {
        Type q = CompilerTypes.qualifyViaImports(name, currentUnit);
        if (q instanceof Type.ClassType qt && !qt.packageName().isEmpty()) {
            return qt.internalName();
        }
        return toInternalName("", name);
    }

    /**
     * Dobra valores de annotation: refs de Classe.class e Enum.CONST viram
     * constantes resolvidas; enum só passa se o classpath provar a classe.
     */
    private Object foldAnnotationValue(Object value) {
        if (value instanceof AnnotationClassRef ref) {
            return new IRClassConstant(resolveAnnotationInternalName(ref.typeName()));
        }
        if (value instanceof AnnotationEnumRef ref) {
            int lastDot = ref.qualifiedConstant().lastIndexOf('.');
            if (lastDot > 0 && externalClasspath != null) {
                String internal = resolveAnnotationInternalName(
                        ref.qualifiedConstant().substring(0, lastDot));
                String constant = ref.qualifiedConstant().substring(lastDot + 1);
                if (externalClasspath.knows(internal)
                        && externalClasspath.isEnum(internal)
                        && externalClasspath.hasEnumConstant(internal, constant)) {
                    return new IREnumConstant(internal, constant);
                }
            }
            if (currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "enum constant '" + ref.qualifiedConstant()
                                + "' could not be resolved from the external classpath",
                        "ANNOT001");
            }
            return ParseSentinel.INSTANCE;
        }
        if (value instanceof List<?> items) {
            List<Object> folded = new ArrayList<>();
            for (Object item : items) folded.add(foldAnnotationValue(item));
            return folded;
        }
        return value;
    }

    private static final class ParseSentinel {
        static final ParseSentinel INSTANCE = new ParseSentinel();
    }

    /**
     * Resolve o nome da annotation para o formato interno JVM. Nomes
     * qualificados vão direto; simples usam imports do arquivo; os de
     * java.lang são embutidos; senão assume-se a própria classe local.
     */
    private String resolveAnnotationInternalName(String name) {
        if (name.contains(".")) return name.replace('.', '/');
        switch (name) {
            case "Override": return "java/lang/Override";
            case "Deprecated": return "java/lang/Deprecated";
            case "FunctionalInterface": return "java/lang/FunctionalInterface";
            case "SafeVarargs": return "java/lang/SafeVarargs";
            case "SuppressWarnings": return "java/lang/SuppressWarnings";
        }
        if (currentUnit != null) {
            for (String imp : currentUnit.imports()) {
                if (!"*.kof".equals(imp) && imp.endsWith("." + name)) {
                    return imp.replace('.', '/');
                }
            }
        }
        return name;
    }

    private IRMethod lowerMethod(MethodDeclarationNode method, String owner, boolean isInterface, List<String> typeParams) {
        String prevOwner = currentLoweringOwner;
        currentLoweringOwner = owner;
        try {
            return lowerMethodInner(method, owner, isInterface, typeParams);
        } finally {
            currentLoweringOwner = prevOwner;
        }
    }

    private IRMethod lowerMethodInner(MethodDeclarationNode method, String owner, boolean isInterface, List<String> typeParams) {
        Type returnType = CompilerTypes.resolveWithTypeParams(method.returnType(), typeParams, currentUnit, semanticAnalyzer);
        List<Type> paramTypes = method.parameters().stream()
                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), typeParams, currentUnit, semanticAnalyzer)).toList();
        if (Type.isVoid(returnType) && method.body() != null && !method.body().isEmpty()
                && method.body().getLast() instanceof ReturnStmt ret && ret.value() != null) {
            List<IRLocalVariable> tmpLocals = new ArrayList<>();
            int tmpIdx = 1;
            for (FormalParameterNode p : method.parameters()) {
                tmpLocals.add(new IRLocalVariable(tmpIdx, p.name(), CompilerTypes.resolveWithTypeParams(p.type(), typeParams, currentUnit, semanticAnalyzer)));
                tmpIdx++;
            }
            Type inferred = ExpressionTyper.inferExprType(this, ret.value(), tmpLocals);
            if (inferred instanceof Type.UnknownType && semanticAnalyzer != null) {
                Type semanticRt = semanticAnalyzer.resolvedMethodReturnType(method);
                if (semanticRt != null && !(semanticRt instanceof Type.UnknownType) && !Type.isVoid(semanticRt)) {
                    inferred = semanticRt;
                }
            }
            if (!(inferred instanceof Type.UnknownType)) {
                returnType = inferred;
            }
        }
        int access = computeAccess(method.modifiers());
        if (isInterface && !method.modifiers().contains("default")) access |= AccessFlags.ABSTRACT;
        List<IRBasicBlock> body = List.of();
        List<IRLocalVariable> locals = List.of();
        if (method.body() != null && !method.body().isEmpty() && !isAbstractMethod(method)) {
            List<KofOperation> ops = new ArrayList<>();
            List<IRLocalVariable> localVars = new ArrayList<>();
            Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, semanticAnalyzer);
            // método ESTÁTICO: sem this, params começam no slot 0
            boolean isStaticMethod = (access & AccessFlags.STATIC) != 0;
            if (!isStaticMethod) {
                localVars.add(new IRLocalVariable(0, "this", ownerType));
            }
            int localIdx = isStaticMethod ? 0 : 1;
            for (FormalParameterNode param : method.parameters()) {
                Type paramType = CompilerTypes.resolveWithTypeParams(param.type(), typeParams, currentUnit, semanticAnalyzer);
                localVars.add(new IRLocalVariable(localIdx, param.name(), paramType));
                localIdx += TypeMetrics.isDoubleWidth(paramType) ? 2 : 1;
            }
            java.util.Set<String> savedMutated = mutatedCapturedNames;
            mutatedCapturedNames = new java.util.HashSet<>();
            collectMutatedCaptures(method.body(), localVars);
            for (StatementNode stmt : method.body()) localIdx = emitStatement(stmt, ops, owner, localIdx, localVars, returnType);
            mutatedCapturedNames = savedMutated;
            KofOperation lastOp = ops.isEmpty() ? null : ops.get(ops.size() - 1);
            if (lastOp == null || !(lastOp instanceof KofReturn || lastOp instanceof KofReturnVoid)) {
                if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
                else ops.add(new KofReturn(returnType));
            }
            body = List.of(new IRBasicBlock(0, ops));
            locals = localVars;
        } else if (!isInterface && !isAbstractMethod(method)) {
            // corpo vazio em classe concreta: sem Code attribute o JVM rejeita
            // a classe (Absent Code attribute) — emite corpo com return default
            List<KofOperation> ops = new ArrayList<>(List.of(Type.isVoid(returnType)
                    ? new KofReturnVoid() : new KofReturn(returnType)));
            body = List.of(new IRBasicBlock(0, ops));
        }
        KofDebugInfo debugInfo = currentDebugPositions.isEmpty()
                ? KofDebugInfo.EMPTY
                : new KofDebugInfo(new java.util.HashMap<>(currentDebugPositions));
        currentDebugPositions.clear();
        return new IRMethod(method.name(), returnType, paramTypes, access, method.thrownExceptions(),
                body, locals, debugInfo,
                lowerAnnotations(method.annotations()), lowerParameterAnnotations(method.parameters()));
    }

    /** Annotations por parâmetro, alinhadas à ordem de parameterTypes. */
    private List<List<IRAnnotation>> lowerParameterAnnotations(List<FormalParameterNode> params) {
        List<List<IRAnnotation>> out = new ArrayList<>();
        boolean any = false;
        for (FormalParameterNode p : params) {
            List<IRAnnotation> annos = lowerAnnotations(p.annotations());
            if (!annos.isEmpty()) any = true;
            out.add(annos);
        }
        return any ? out : List.of();
    }

    /**
     * Default parameter values on constructors: for each trailing default, a
     * wrapper <init> with fewer parameters evaluates the default expressions
     * and delegates to the canonical constructor — the same semantics as
     * lowerFunctionDefaults for functions.
     */
    private List<IRMethod> lowerConstructorDefaults(ConstructorDeclarationNode ctor, String owner,
                                                    String superName, List<String> typeParams,
                                                    List<IRField> fields,
                                                    java.util.Map<String, ExpressionNode> fieldInits) {
        List<IRMethod> wrappers = new ArrayList<>();
        List<FormalParameterNode> params = ctor.parameters();
        if (params.isEmpty() || params.stream().noneMatch(p -> p.defaultExpression() != null)) {
            return wrappers;
        }
        int n = params.size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (params.get(i).defaultExpression() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return wrappers;
        List<Type> canonicalTypes = new ArrayList<>();
        for (FormalParameterNode p : params) canonicalTypes.add(CompilerTypes.resolveWithTypeParams(p.type(), typeParams, currentUnit, semanticAnalyzer));
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, semanticAnalyzer);
        Type superType = CompilerTypes.ownerTypeFromInternal(superName, semanticAnalyzer);
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = canonicalTypes.subList(0, paramCount);
            List<IRLocalVariable> locals = new ArrayList<>();
            locals.add(new IRLocalVariable(0, "this", ownerType));
            List<KofOperation> ops = new ArrayList<>();
            // No super()/field inits here: the canonical <init> performs
            // them; the wrapper only supplies the default arguments.
            ops.add(new KofLoadLocal(ownerType, 0));
            int localIdx = 1;
            for (int i = 0; i < paramCount; i++) {
                locals.add(new IRLocalVariable(localIdx, params.get(i).name(), paramTypes.get(i)));
                ops.add(new KofLoadLocal(paramTypes.get(i), localIdx));
                localIdx++;
            }
            for (int i = paramCount; i < n; i++) {
                localIdx = ExpressionLowerer.emitExpression(this, params.get(i).defaultExpression(), ops, owner,
                        localIdx, locals);
            }
            ops.add(new KofCall(ownerType, "<init>", canonicalTypes, Type.PrimitiveType.VOID,
                    KofCallKind.CONSTRUCTOR));
            ops.add(new KofReturnVoid());
            wrappers.add(new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes,
                    computeAccess(ctor.modifiers()), ctor.thrownExceptions(),
                    List.of(new IRBasicBlock(0, ops)), locals));
        }
        return wrappers;
    }

    private IRMethod lowerConstructor(ConstructorDeclarationNode ctor, String owner, String superName,
                                      List<String> typeParams, List<IRField> fields,
                                      java.util.Map<String, ExpressionNode> fieldInits) {
        String prevOwner = currentLoweringOwner;
        currentLoweringOwner = owner;
        try {
            return lowerConstructorInner(ctor, owner, superName, typeParams, fields, fieldInits);
        } finally {
            currentLoweringOwner = prevOwner;
        }
    }

    private IRMethod lowerConstructorInner(ConstructorDeclarationNode ctor, String owner, String superName,
                                      List<String> typeParams, List<IRField> fields,
                                      java.util.Map<String, ExpressionNode> fieldInits) {
        List<Type> paramTypes = ctor.parameters().stream()
                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), typeParams, currentUnit, semanticAnalyzer)).toList();
        int access = computeAccess(ctor.modifiers());
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> localVars = new ArrayList<>();
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, semanticAnalyzer);
        Type superType = CompilerTypes.ownerTypeFromInternal(superName, semanticAnalyzer);
        localVars.add(new IRLocalVariable(0, "this", ownerType));
        boolean delegatesToThis = !ctor.body().isEmpty() &&
                ctor.body().getFirst() instanceof ExpressionStmt es &&
                es.expression() instanceof MethodCallExpr mc &&
                "this".equals(mc.methodName());
        boolean hasExplicitSuper = !ctor.body().isEmpty() &&
                ctor.body().getFirst() instanceof ExpressionStmt es &&
                es.expression() instanceof MethodCallExpr mc &&
                "super".equals(mc.methodName());
        // this(...): o construtor alvo executa super() e os inicializadores
        if (!delegatesToThis && !hasExplicitSuper && !"java/lang/Object".equals(superName)) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        if (!delegatesToThis) {
            emitFieldInitializers(ops, ownerType, fields);
        }
        int localIdx = 1;
        for (FormalParameterNode param : ctor.parameters()) {
            Type paramType = CompilerTypes.resolveWithTypeParams(param.type(), typeParams, currentUnit, semanticAnalyzer);
            localVars.add(new IRLocalVariable(localIdx, param.name(), paramType));
            localIdx += TypeMetrics.isDoubleWidth(paramType) ? 2 : 1;
        }
        for (var entry : fieldInits.entrySet()) {
            if (delegatesToThis) break;
            Type fieldType = fields.stream().filter(f -> f.name().equals(entry.getKey())).findFirst()
                    .map(f -> f.type()).orElse(Type.UnknownType.UNKNOWN);
            ops.add(new KofLoadLocal(ownerType, 0));
            localIdx = ExpressionLowerer.emitExpression(this, entry.getValue(), ops, owner, localIdx, localVars);
            ops.add(new KofStoreField(ownerType, entry.getKey(), fieldType));
        }
        for (StatementNode stmt : ctor.body()) localIdx = emitStatement(stmt, ops, owner, localIdx, localVars, Type.PrimitiveType.VOID);
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, access, ctor.thrownExceptions(),
                List.of(new IRBasicBlock(0, ops)), localVars, KofDebugInfo.EMPTY,
                lowerAnnotations(ctor.annotations()), lowerParameterAnnotations(ctor.parameters()));
    }

    private IRMethod generateDefaultConstructor(String owner, String superName, List<IRField> fields,
                                                 java.util.Map<String, ExpressionNode> fieldInits) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, semanticAnalyzer);
        Type superType = CompilerTypes.ownerTypeFromInternal(superName, semanticAnalyzer);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        if (!"java/lang/Object".equals(superName)) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        emitFieldInitializers(ops, ownerType, fields);
        int localIdx = 1;
        for (var entry : fieldInits.entrySet()) {
            Type fieldType = fields.stream().filter(f -> f.name().equals(entry.getKey())).findFirst()
                    .map(f -> f.type()).orElse(Type.UnknownType.UNKNOWN);
            ops.add(new KofLoadLocal(ownerType, 0));
            localIdx = ExpressionLowerer.emitExpression(this, entry.getValue(), ops, owner, localIdx, locals);
            ops.add(new KofStoreField(ownerType, entry.getKey(), fieldType));
        }
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, List.of(), AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * Field initializers must run in the constructor (after super(), before
     * the body) — instance fields with a default value are assigned there.
     * Never silently ignore an initializer.
     */
    private void emitFieldInitializers(List<KofOperation> ops, Type ownerType, List<IRField> fields) {
        for (IRField field : fields) {
            if (field.initialValue() == null || (field.accessFlags() & AccessFlags.STATIC) != 0) continue;
            ops.add(new KofLoadLocal(ownerType, 0));
            Object v = field.initialValue();
            String fieldName = field.type() instanceof Type.PrimitiveType pt
                    ? Type.canonicalPrimitiveName(pt.name()) : "";
            if (v instanceof Integer) {
                int iv = (Integer) v;
                if ("long".equals(fieldName)) {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.LONG, (long) iv));
                } else if ("double".equals(fieldName)) {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.DOUBLE, (double) iv));
                } else if ("float".equals(fieldName)) {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.FLOAT, (float) iv));
                } else {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, iv));
                }
            } else if (v instanceof Long) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.LONG, (Long) v));
            } else if (v instanceof String) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, (String) v));
            } else if (v instanceof Double) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.DOUBLE, (Double) v));
            } else if (v instanceof Float) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.FLOAT, (Float) v));
            } else if (v instanceof Boolean) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, ((Boolean) v) ? 1 : 0));
            } else {
                continue;
            }
            ops.add(new KofStoreField(ownerType, field.name(), field.type()));
        }
    }

    /**
     * toString() nativo de record: "Nome[campo=valor, ...]" — sintetizado no
     * IR (padrão de concat: valueOf + kof_string_concat).
     */
    private IRMethod buildRecordToStringMethod(String internalName, RecordDeclarationNode rec,
                                               List<IRField> fields, List<String> typeParams) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, semanticAnalyzer);
        String simpleName = internalName.contains("/")
                ? internalName.substring(internalName.lastIndexOf('/') + 1) : internalName;
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        // "Nome[x=valor, y=valor]" — concat: literal, campo, separador...
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, simpleName + "["));
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
        for (int i = 0; i < fields.size(); i++) {
            IRField f = fields.get(i);
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, f.name() + "="));
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            if (!Type.isString(f.type())) TypeEmitter.boxPrimitive(ops, f.type());
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            if (i + 1 < fields.size()) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ", "));
                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                        List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            }
        }
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "]"));
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                BuiltinTypes.STRING, KofCallKind.FUNCTION));
        ops.add(new KofReturn(BuiltinTypes.STRING));
        return new IRMethod("toString", BuiltinTypes.STRING, List.of(), AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * equals() nativo de record: compara todos os componentes (bug 11 native).
     */
    private IRMethod buildRecordEqualsMethod(String internalName, List<IRField> fields,
                                             List<String> typeParams) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, semanticAnalyzer);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        locals.add(new IRLocalVariable(1, "other", ownerType));
        for (int i = 0; i < fields.size(); i++) {
            IRField f = fields.get(i);
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            ops.add(new KofLoadLocal(ownerType, 1));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            ops.add(new KofBinary(KofBinaryOp.EQ, f.type()));
            // AND acumula a partir do 2º campo: [bool0] → (bool0 AND bool1)
            // O AND só após a 2ª comparação ter empilhado o 2º bool.
            if (i > 0) {
                ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.BOOL));
            }
        }
        if (fields.isEmpty()) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        }
        ops.add(new KofReturn(Type.PrimitiveType.BOOL));
        return new IRMethod("equals", Type.PrimitiveType.BOOL, List.of(ownerType), AccessFlags.PUBLIC,
                List.of(), List.of(new IRBasicBlock(0, ops)), locals);
    }

    private IRMethod generateRecordConstructor(RecordDeclarationNode rec, String owner) {
        List<String> typeParams = rec.typeParameters() == null ? List.of() : rec.typeParameters();
        List<Type> compTypes = rec.components().stream().map(c -> CompilerTypes.resolveWithTypeParams(c.type(), typeParams, currentUnit, semanticAnalyzer)).toList();
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, semanticAnalyzer);
        Type superType = new Type.ClassType("java.lang", "Record", List.of());
        locals.add(new IRLocalVariable(0, "this", ownerType));
        if (isJvmTarget()) {


            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        int localIdx = 1;
        for (RecordComponentNode comp : rec.components()) {
            Type compType = CompilerTypes.resolveWithTypeParams(comp.type(), typeParams, currentUnit, semanticAnalyzer);
            locals.add(new IRLocalVariable(localIdx, comp.name(), compType));
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadLocal(compType, localIdx));
            ops.add(new KofStoreField(ownerType, comp.name(), compType));
            localIdx += TypeMetrics.isDoubleWidth(compType) ? 2 : 1;
        }
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, compTypes, AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    private List<IRMethod> generateRecordDefaultOverloads(RecordDeclarationNode rec, String owner) {
        List<IRMethod> overloads = new ArrayList<>();
        int n = rec.components().size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (rec.components().get(i).initializer() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return overloads;
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, semanticAnalyzer);
        List<Type> canonicalTypes = rec.components().stream().map(c -> CompilerTypes.toType(c.type(), currentUnit)).toList();
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = new ArrayList<>();
            List<IRLocalVariable> locals = new ArrayList<>();
            List<KofOperation> ops = new ArrayList<>();
            locals.add(new IRLocalVariable(0, "this", ownerType));
            ops.add(new KofLoadLocal(ownerType, 0));
            int localIdx = 1;
            for (int i = 0; i < paramCount; i++) {
                Type t = CompilerTypes.toType(rec.components().get(i).type(), currentUnit);
                paramTypes.add(t);
                locals.add(new IRLocalVariable(localIdx, rec.components().get(i).name(), t));
                ops.add(new KofLoadLocal(t, localIdx));
                localIdx += TypeMetrics.isDoubleWidth(t) ? 2 : 1;
            }
            for (int i = paramCount; i < n; i++) {
                ExpressionNode init = rec.components().get(i).initializer();
                if (init != null) {
                    localIdx = ExpressionLowerer.emitExpression(this, init, ops, owner, localIdx, locals);
                }
            }
            ops.add(new KofCall(ownerType, "<init>", canonicalTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            ops.add(new KofReturnVoid());
            IRMethod m = new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, AccessFlags.PUBLIC,
                    List.of(), List.of(new IRBasicBlock(0, ops)), locals);
            overloads.add(m);
        }
        return overloads;
    }

    private String toInternalName(String packageName, String simpleName) {
        if (simpleName.contains("/")) return simpleName;
        if (simpleName.contains(".")) return simpleName.replace('.', '/');
        if (packageName.isEmpty()) return simpleName;
        return packageName.replace('.', '/') + "/" + simpleName;
    }

    int computeAccess(List<String> modifiers) {
        int access = 0;
        boolean hasVisibility = false;
        for (String mod : modifiers) {
            access |= switch (mod) {
                case "public" -> AccessFlags.PUBLIC;
                case "private" -> AccessFlags.PRIVATE;
                case "protected" -> AccessFlags.PROTECTED;
                case "static" -> AccessFlags.STATIC;
                case "final" -> AccessFlags.FINAL;
                case "abstract" -> AccessFlags.ABSTRACT;
                default -> 0;
            };
            if ("public".equals(mod) || "private".equals(mod) || "protected".equals(mod)) {
                hasVisibility = true;
            }
        }
        if (!hasVisibility) access |= AccessFlags.PUBLIC;
        return access;
    }

    private boolean isAbstractMethod(MethodDeclarationNode method) {
        return method.body() == null;
    }

    /**
     * Parses an integer literal, including hexadecimal (0xFF...). ARGB color
     * values may exceed Integer.MAX_VALUE; they wrap to the signed 32-bit
     * representation, which the Kof color semantics use (shifts + mask).
     */
    int parseIntLiteral(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            // no suffix stripping: hex digits may end in a..f
            return (int) Long.parseLong(value.substring(2), 16);
        }
        return Integer.parseInt(stripSuffix(value));
    }

    String stripSuffix(String value) {
        if (value.endsWith("l") || value.endsWith("L") ||
            value.endsWith("f") || value.endsWith("F") ||
            value.endsWith("d") || value.endsWith("D")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

}
