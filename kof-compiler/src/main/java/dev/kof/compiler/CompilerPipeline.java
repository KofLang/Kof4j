package dev.kof.compiler;
import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;
import dev.kof.compiler.backend.AndroidProjectWriter;
import dev.kof.compiler.backend.Backend;
import dev.kof.compiler.backend.Optimizer;
import dev.kof.compiler.jvm.JvmBackend;
import dev.kof.compiler.nat.NativeBackend;
import dev.kof.compiler.js.JsBackend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestração do pipeline de compilação: parse, semântica, IR, otimização, emit.
 */
public final class CompilerPipeline {

    private CompilerPipeline() {}

    static CompilationResult compile(CompilerDriver driver, Path sourceFile, Path outputDir) {
        return CompilerPipeline.compile(driver, sourceFile, outputDir, Target.JVM);
    }

    static CompilationResult compile(CompilerDriver driver, Path sourceFile, Path outputDir, Target target) {
        return CompilerPipeline.compileSources(driver, java.util.List.of(sourceFile), outputDir, target);
    }

    static CompilationResult compileSources(CompilerDriver driver, java.util.List<Path> sources, Path outputDir, Target target) {
        return CompilerPipeline.compileSources(driver, sources, outputDir, target, rootFor(sources));
    }

    /**
     * Fase 1 (plataforma): se alguma fonte vive sob um projeto com
     * kof.toml, a raiz do projeto vira module root — imports como
     * `import shared.Validation` resolvem a partir da raiz. Sem
     * manifesto, mantém o LCA atual (retrocompatível).
     */
    static Path rootFor(java.util.List<Path> sources) {
        // Fase 1: a descoberta de projeto sobe a partir de cada FONTE
        // (não do LCA). Fonte única em src/Main.kf com kof.toml em tmp/:
        // o LCA seria src/, mas o projeto é tmp/ — imports como
        // `import shared.Validation` só resolvem a partir de tmp/.
        for (Path s : sources) {
            Path project = ProjectLocator.locate(s);
            if (project != null) return project;
        }
        return ModuleRoots.moduleRootFor(sources);
    }

    static CompilationResult compileSources(CompilerDriver driver, java.util.List<Path> sources, Path outputDir, Target target,
                                            Path moduleRoot) {
        driver.moduleRoot = moduleRoot;
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        driver.target = target;
        driver.currentDiagnostics = diagnostics;
        CompilerPipeline.flushClasspathWarnings(driver);
        driver.resetForCompilation();
        try {
            Path rootAbs = driver.moduleRoot != null ? driver.moduleRoot.toAbsolutePath().normalize() : null;
            CompilationUnitNode unit = parseAndMerge(driver, sources, rootAbs, diagnostics);
            if (unit == null) {
                return new CompilationResult(false, diagnostics, outputDir);
            }
            CompilerPipeline.lowerAndEmit(driver, unit, diagnostics, outputDir, driver.target);
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
            if (e.getMessage() != null && e.getMessage().startsWith("NATIVE002")) {
                // NATIVE002: op fora do subset de um emissor nativo (ex.: o
                // slice mínimo do MCU RV32I, B-4) — diagnóstico honesto, nunca
                // um artefato que finge rodar (R6, Q7).
                diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                        e.getMessage(), "NATIVE002");
                return new CompilationResult(false, diagnostics, outputDir);
            }
            if (e.getMessage() != null && e.getMessage().startsWith("CONC003")) {
                // CONC003: concorrência ausente num alvo single-core (ex.: o
                // MCU RV32I, B-4) — diagnóstico honesto, nunca stub (R6).
                diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                        e.getMessage(), "CONC003");
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

    static CompilationResult compileForTests(CompilerDriver driver, Path sourceFile, Path outputDir, Target target) {
        driver.testHarnessMode = true;
        try {
            return CompilerPipeline.compile(driver, sourceFile, outputDir, target);
        } finally {
            driver.testHarnessMode = false;
        }
    }

    static CompilationResult compileForTests(CompilerDriver driver, Path sourceFile, Path outputDir,
                                             Target target, Path moduleRoot) {
        driver.testHarnessMode = true;
        try {
            // #708: raiz de testes explícita — compila o arquivo sozinho
            // (per-file, um main por arquivo) mas resolve o pacote contra a
            // raiz de testes, não contra o diretório imediato do arquivo.
            return CompilerPipeline.compileSources(driver, java.util.List.of(sourceFile),
                    outputDir, target, moduleRoot);
        } finally {
            driver.testHarnessMode = false;
        }
    }

    static CompilationResult compileForTestsSources(CompilerDriver driver, java.util.List<Path> sources,
                                                    Path outputDir, Target target, Path moduleRoot) {
        driver.testHarnessMode = true;
        try {
            return CompilerPipeline.compileSources(driver, sources, outputDir, target, moduleRoot);
        } finally {
            driver.testHarnessMode = false;
        }
    }

    static java.util.List<CompilerDriver.TestInfo> discoveredTests(CompilerDriver driver) {
        return List.copyOf(driver.discoveredTests);
    }

    static void flushClasspathWarnings(CompilerDriver driver) {
        if (driver.currentDiagnostics != null && !driver.pendingClasspathWarnings.isEmpty()) {
            for (String w : driver.pendingClasspathWarnings) {
                driver.currentDiagnostics.warning("", 0, 0, 0, w, "CP002");
            }
            driver.pendingClasspathWarnings.clear();
        }
    }

    static Backend selectBackend(CompilerDriver driver, Target target) {
        return switch (target) {
            case JVM -> CompilerPipeline.backendWithClasspath(driver, new JvmBackend());
            case NATIVE -> new NativeBackend(Target.NATIVE).profile(driver.nativeProfile);
            case NATIVE_RISCV64 -> new NativeBackend(Target.NATIVE_RISCV64).profile(driver.nativeProfile);
            case NATIVE_AARCH64 -> new NativeBackend(Target.NATIVE_AARCH64).profile(driver.nativeProfile);
            case NATIVE_RISCV32 -> new NativeBackend(Target.NATIVE_RISCV32).profile(driver.nativeProfile);
            case NATIVE_MCU_ARM -> new NativeBackend(Target.NATIVE_MCU_ARM).profile(driver.nativeProfile);
            case JS -> new JsBackend();
            // Android: ART executa bytecode dex'd — a emissão é a mesma do
            // backend JVM; o alvo vive nas validações AND* e no empacotamento
            case ANDROID -> CompilerPipeline.backendWithClasspath(driver, new JvmBackend(Target.ANDROID));
            // SCRIPT não emite artefato — é interpretado (interpret()). O
            // chamador (lowerAndEmit) bloqueia antes; isto é defensivo.
            case SCRIPT -> throw new IllegalStateException("SCRIPT has no backend");
            case WASM -> new dev.kof.compiler.wasm.WasmBackend();
            case WASI -> new dev.kof.compiler.wasm.WasmBackend(true);
        };
    }

    static Backend backendWithClasspath(CompilerDriver driver, JvmBackend backend) {
        backend.setExternalTypes(driver.externalClasspath);
        return backend;
    }

    static void lowerAndEmit(CompilerDriver driver, CompilationUnitNode unit, DiagnosticCollector diagnostics,
                              Path outputDir, Target target) throws IOException {
        if (target.isScript()) {
            // KofScript não compila — interpreta (interpret()). Emitir com
            // este target seria fallback silencioso (R6): diagnóstico claro.
            diagnostics.error(driver.currentSourceName, 0, 0, 0,
                    "target 'script' emits no artifacts; use kof run --target script"
                            + " (direct IR interpretation) or another target",
                    "COMP003");
            return;
        }
        if (System.getProperty("kof.trace") != null) {
            System.err.println("LOWER-AND-EMIT decls=" + unit.declarations().size() + " out=" + outputDir);
        }
        driver.target = target;
        driver.currentDiagnostics = diagnostics;
        CompilerPipeline.flushClasspathWarnings(driver);
        driver.entitySchemas.clear();
        IRModule irModule = analyzeAndLower(driver, unit, diagnostics);
        if (irModule == null) {
            return;
        }
        // UI001 (R6): kof.ui é no-op no Native — avisa UMA vez (aditivo, §KOFUI-AUDIT).
        UiTargetDiagnostics.warnIfNative(driver, irModule, diagnostics);
        Files.createDirectories(outputDir);
        Backend backend = CompilerPipeline.selectBackend(driver, target);
        try {
            backend.emit(irModule, outputDir, driver.debugInfoEnabled);
        } catch (dev.kof.compiler.wasm.WasmUnsupportedException e) {
            diagnostics.error(driver.currentSourceName, 0, 0, 0, e.getMessage(), "WASM002");
            return;
        }
        if (target == Target.ANDROID) {
            new AndroidProjectWriter(driver.androidMinSdk, driver.androidTargetSdk)
                    .write(outputDir, irModule);
        }
    }

    /**
     * Frontend completo até a IR otimizada: desugar → analisar → lower →
     * otimizar. Retorna null se houver diagnósticos de erro. Compartilhado
     * pelo caminho de emissão (lowerAndEmit) e pelo interpretador
     * (KofInterpreter) — paridade por construção: mesmo parser, mesma
     * semântica, mesmo lowering, mesma otimização.
     */
    static IRModule analyzeAndLower(CompilerDriver driver, CompilationUnitNode unit,
                                    DiagnosticCollector diagnostics) {
        BuiltinTypes.resetEnums();
        for (AstNode d : unit.declarations()) {
            if (d instanceof EnumDeclarationNode en) BuiltinTypes.registerEnum(en.name());
        }
        // 2.2.3 (D-DESUGAR-STEP): the four source desugars run through the AST
        // registry (default order: tests, application, infra, nested functions).
        unit = DesugarStepPipeline.run(driver.desugarSteps, unit, driver);
        driver.discoveredConfigKeys.clear();
        if (driver.target == Target.ANDROID) {
            unit = AndroidHostAppender.appendIfNeeded(driver, unit);
        }
        driver.semanticAnalyzer = new SemanticAnalyzer();
        // entity-history fatia 2c-parte-2 (D-ENTITY-HISTORY): pré-pass do
        // parse — as entidades `audited` e os nomes de entity ficam visíveis
        // à fase SEM (o conjunto do driver só se enche no IR-build, depois).
        for (AstNode d0 : unit.declarations()) {
            if (d0 instanceof EntityDeclarationNode e0) {
                if (e0.modifiers().contains("audited")) driver.auditedEntities.add(e0.name());
                driver.entityNamesMirror.add(e0.name());
            }
        }
        driver.semanticAnalyzer.setAuditedEntityMirrors(driver.auditedEntities, driver.entityNamesMirror);
        driver.semanticAnalyzer.setTarget(driver.target);
        driver.semanticAnalyzer.setInterpreting(driver.interpreting);
        driver.semanticAnalyzer.setExternalTypes(driver.externalClasspath);
        driver.semanticAnalyzer.setDeclarationPackageLookup(d -> driver.declarationPackages.get(d));
        driver.semanticAnalyzer.analyze(unit, diagnostics);
        if (diagnostics.hasErrors()) {
            return null;
        }
        // D-PORTUKOF unidade 3 (07/10): splice receiver-aware da superficie de
        // metodos/campos (alias -> canonico pelo TIPO REAL do receiver, cache
        // de identidade re-indexado). EN: tabela devolve o mesmo nome — no-op.
        unit = PortuKofMethodSplicer.run(driver, driver.semanticAnalyzer, unit);
        LabelId.reset();
        driver.currentModule = new IRModule("", List.of(), List.of());
        driver.currentUnit = unit;
        IRModule irModule = driver.applySuperBridges(CompilerUnitLowering.lowerToIR(driver, unit, diagnostics));
        if (diagnostics.hasErrors()) {
            return null;
        }
        driver.currentModule = irModule;
        IRModule unoptimized = irModule;
        if (driver.optimizeEnabled) {
            irModule = Optimizer.optimize(irModule);
            driver.currentModule = irModule;
        }
        // R4 (D-CODEGEN-STEP): internal codegen hooks on the optimized IR,
        // before emit/interpret. Empty registry = identity.
        irModule = CodegenStepPipeline.run(driver.codegenSteps, irModule, driver);
        driver.currentModule = irModule;
        if (driver.irObserver != null) {
            driver.irObserver.accept(unoptimized, irModule);
        }
        if (driver.irStatsObserver != null) {
            driver.irStatsObserver.observed(IRStatistics.of(unoptimized, irModule));
        }
        return irModule;
    }

    /**
     * PREPARE PARA INTERPRETAÇÃO (sem emitir bytecode): roda o frontend
     * completo (parse → merge → imports → desugar → análise → lowering →
     * otimização) e entrega a IR pronta para o KofInterpreter executar.
     * Mesma pipeline do compileSources — paridade por construção.
     */
    public static IRModule prepareForInterpretation(CompilerDriver driver, java.util.List<Path> sources,
                                                    Path moduleRoot) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        driver.moduleRoot = moduleRoot;
        driver.target = Target.JVM;
        boolean prevInterpreting = driver.interpreting;
        driver.interpreting = true;
        driver.currentDiagnostics = diagnostics;
        driver.interpreterWarnings = java.util.List.of();
        CompilerPipeline.flushClasspathWarnings(driver);
        driver.entitySchemas.clear();
        try {
            Path rootAbs = moduleRoot != null ? moduleRoot.toAbsolutePath().normalize() : null;
            CompilationUnitNode unit = parseAndMerge(driver, sources, rootAbs, diagnostics);
            if (unit == null) {
                throw new KofInterpretException(diagnostics);
            }
            IRModule ir = analyzeAndLower(driver, unit, diagnostics);
            if (ir == null) {
                throw new KofInterpretException(diagnostics);
            }
            // #678: o JVM/JS/Native imprimem os WARNING do frontend; o Script
            // não tinha canal. Expõe-os para o chamador (paridade de diagnósticos).
            driver.interpreterWarnings = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.severity() == Diagnostic.Severity.WARNING)
                    .toList();
            return ir;
        } catch (IOException e) {
            diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                    "Error reading source file: " + e.getMessage(), "COMP001");
            throw new KofInterpretException(diagnostics);
        } finally {
            driver.interpreting = prevInterpreting;
        }
    }

    /**
     * INTERPRETA um módulo Kof sem emitir bytecode nem fork de JVM — o
     * target KofScript. Roda o mesmo frontend do compileSources e executa a
     * IR otimizada no KofInterpreter (paridade por construção).
     */
    static KofInterpreter.Result interpret(CompilerDriver driver, java.util.List<Path> sources,
                                           Path moduleRoot, String[] args) {
        IRModule ir = prepareForInterpretation(driver, sources, moduleRoot);
        // #678: anexa os WARNING do frontend ao resultado (paridade com o
        // compile, onde o CLI imprime os diagnósticos do Result).
        return KofInterpreter.run(ir, args, driver.interpreterWarnings);
    }

    /** Parse + merge multi-arquivo + expansão de imports (extraído de compileSources). */
    static CompilationUnitNode parseAndMerge(CompilerDriver driver, java.util.List<Path> sources,
                                             Path rootAbs, DiagnosticCollector diagnostics)
            throws IOException {
        driver.currentSourceName = sources.get(0).getFileName() != null
                ? sources.get(0).getFileName().toString() : null;
        driver.currentSourceContent = Files.readString(sources.get(0));
        java.util.List<CompilationUnitNode> parsedUnits = new ArrayList<>();
        for (Path src : sources) {
            String code = src == sources.get(0) ? driver.currentSourceContent : Files.readString(src);
            String fileName = src.getFileName().toString();
            // D-PORTUKOF (07/10): a extensão determina o perfil (autodescoberta
            // determinística). Perfil canônico = comportamento histórico exato.
            dev.kof.compiler.lang.LanguageProfile profile =
                    dev.kof.compiler.lang.LanguageProfile.forFileName(fileName);
            Lexer lexer = new Lexer(code, fileName, diagnostics, profile);
            List<Token> tokens = lexer.tokenize();
            if (diagnostics.hasErrors()) return null;
            Parser parser = new Parser(tokens, diagnostics, fileName, profile);
            CompilationUnitNode unit = parser.parse();
            if (diagnostics.hasErrors()) return null;
            parsedUnits.add(dev.kof.compiler.lang.PortuKofParity.normalize(profile, unit));
        }
        java.util.List<String> unitPkgs = new ArrayList<>();
        for (int i = 0; i < parsedUnits.size(); i++) {
            String declared = parsedUnits.get(i).packageName();
            String derivedPkg = ModuleRoots.derivedPackageOf(sources.get(i), rootAbs);
            if (!declared.isEmpty() && !declared.equals(derivedPkg)) {
                diagnostics.error(sources.get(i).toString(), 0, 0, 0,
                        "package '" + declared
                                + "' does not match the directory ('" + derivedPkg
                                + "') — a directory is a package",
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
                driver.declarationPackages.put(d, pkgU);
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
        merged = CompilerSupervisor.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerWorkflow.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerMakealive.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerInterop.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerPagination.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerOrmHistory.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerPairs.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerTesting.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerTestDb.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerTestWeb.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerTestBrowser.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        merged = CompilerWeb.injectHostIfNeeded(driver, merged, diagnostics);
        if (merged == null) return null;
        ExternalClasspath extCp = (driver.target == Target.JVM || driver.target == Target.ANDROID)
                ? driver.externalClasspath : null;
        merged = CompilerImports.expandKofImports(merged, driver.moduleRoot, diagnostics, driver.declarationPackages, extCp,
                driver.dependencySourceRoots, sources);
        if (diagnostics.hasErrors()) return null;
        return merged;
    }


    static boolean isIntType(String t) {
        return "int".equals(t) || "Int".equals(t);
    }

    static boolean isStringType(String t) {
        return "String".equals(t) || "string".equals(t);
    }

    static boolean isDoubleType(String t) {
        return "double".equals(t) || "Double".equals(t);
    }

}
