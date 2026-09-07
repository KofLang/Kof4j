package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestração do pipeline de compilação: parse, semântica, IR, otimização, emit.
 */
final class CompilerPipeline {

    private CompilerPipeline() {}

    static CompilationResult compile(CompilerDriver driver, Path sourceFile, Path outputDir) {
        return CompilerPipeline.compile(driver, sourceFile, outputDir, Target.JVM);
    }

    static CompilationResult compile(CompilerDriver driver, Path sourceFile, Path outputDir, Target target) {
        return CompilerPipeline.compileSources(driver, java.util.List.of(sourceFile), outputDir, target);
    }

    static CompilationResult compileSources(CompilerDriver driver, java.util.List<Path> sources, Path outputDir, Target target) {
        return CompilerPipeline.compileSources(driver, sources, outputDir, target, ModuleRoots.moduleRootFor(sources));
    }

    static CompilationResult compileSources(CompilerDriver driver, java.util.List<Path> sources, Path outputDir, Target target,
                                            Path moduleRoot) {
        driver.moduleRoot = moduleRoot;
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        driver.target = target;
        driver.currentDiagnostics = diagnostics;
        CompilerPipeline.flushClasspathWarnings(driver);
        driver.entitySchemas.clear();
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

    static CompilationResult compileForTestsSources(CompilerDriver driver, java.util.List<Path> sources,
                                                    Path outputDir, Target target, Path moduleRoot) {
        driver.testHarnessMode = true;
        try {
            return CompilerPipeline.compileSources(driver, sources, outputDir, target, driver.moduleRoot);
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

    static CompilationUnitNode appendAndroidHostIfNeeded(CompilerDriver driver, CompilationUnitNode unit) {
        boolean userHasHost = unit.declarations().stream()
                .anyMatch(d -> d instanceof TypeDeclarationNode t && "MainActivity".equals(t.name()));
        if (userHasHost) return unit;
        // sem android.jar no ExternalClasspath o host não resolve — avisar
        // (AND004) e seguir com o programa puro em vez de SEM015 confuso
        if (driver.externalClasspath == null || !driver.externalClasspath.knows("android/app/Activity")) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.warning("", 0, 0, 0,
                        "driver.target android sem android.jar no ExternalClasspath: "
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
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error("", 0, 0, 0,
                        "android host could not be loaded: " + e.getMessage(), "AND004");
            }
            return unit;
        }
    }

    static Backend selectBackend(CompilerDriver driver, Target target) {
        return switch (target) {
            case JVM -> CompilerPipeline.backendWithClasspath(driver, new JvmBackend());
            case NATIVE -> new NativeBackend(Target.NATIVE);
            case NATIVE_RISCV64 -> new NativeBackend(Target.NATIVE_RISCV64);
            case NATIVE_AARCH64 -> new NativeBackend(Target.NATIVE_AARCH64);
            case JS -> new JsBackend();
            // Android: ART executa bytecode dex'd — a emissão é a mesma do
            // backend JVM; o alvo vive nas validações AND* e no empacotamento
            case ANDROID -> CompilerPipeline.backendWithClasspath(driver, new JvmBackend());
        };
    }

    static Backend backendWithClasspath(CompilerDriver driver, JvmBackend backend) {
        backend.setExternalTypes(driver.externalClasspath);
        return backend;
    }

    static IRModule lowerToIR(CompilerDriver driver, CompilationUnitNode unit, DiagnosticCollector diagnostics) {
        List<String> imports = new ArrayList<>(unit.imports());
        List<IRClass> classes = new ArrayList<>();
        List<IRMethod> topLevelFunctions = new ArrayList<>();
        String moduleName = unit.packageName().isEmpty() ? "Default" : unit.packageName().replace('.', '/');
        int nextTypeId = 10;
        for (AstNode decl : unit.declarations()) {
            String declPkg = driver.declPackage(decl, unit.packageName());
            if (decl instanceof ClassDeclarationNode cls) classes.add(CompilerClassLowering.lowerClass(driver, cls, declPkg, nextTypeId++));
            else if (decl instanceof InterfaceDeclarationNode iface) classes.add(CompilerClassLowering.lowerInterface(driver, iface, declPkg, nextTypeId++));
            else if (decl instanceof RecordDeclarationNode rec) classes.add(CompilerClassLowering.lowerRecord(driver, rec, declPkg, nextTypeId++));
            else if (decl instanceof EntityDeclarationNode ent) {
                driver.entitySchemas.put(ent.name(), ent.fields());
                List<RecordComponentNode> components = new java.util.ArrayList<>();
                for (EntityFieldNode f : ent.fields()) {
                    components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
                }
                classes.add(CompilerClassLowering.lowerRecord(driver, new RecordDeclarationNode(ent.position(), ent.name(),
                        ent.modifiers(), null, List.of(), components, List.of()),
                        declPkg, nextTypeId++));
            }
            else if (decl instanceof FunctionDeclarationNode func) {
                topLevelFunctions.add(CompilerFunctionLowering.lowerFunction(driver, func));
                topLevelFunctions.addAll(CompilerFunctionLowering.lowerFunctionDefaults(driver, func));
            }
            else if (decl instanceof ExternalFunctionNode ext) {
                driver.externSignatures.put(ext.name(), ext);
                // FFI (R3): binding suportado não é gap; o resto é gap honesto por
                // target — FFI002 no JS, FFI001 nos demais — nunca stub silencioso.
                if (driver.currentDiagnostics != null && !driver.isExternBound(ext)) {
                    SourcePosition sp = ext.position();
                    String lib = ext.library() != null ? " in " + ext.library() : "";
                    String code = driver.target == Target.JS ? "FFI002" : "FFI001";
                    String msg = driver.target == Target.JS
                            ? "extern '" + ext.name() + "'" + lib + ": FFI not available on the JS target (FFI002)"
                            : "extern '" + ext.name() + "'" + lib + ": FFI binding not implemented on the "
                                    + driver.target + " target yet (FFI001)";
                    driver.currentDiagnostics.error(sp != null ? sp.file() : "",
                            sp != null ? sp.line() : 0, sp != null ? sp.column() : 0, 0, msg, code);
                }
            }
        }
        if (!topLevelFunctions.isEmpty()) {
            String mainClassName = moduleName.isEmpty() ? "Main" : moduleName + "/Main";
            classes.add(0, new IRClass(mainClassName, "java/lang/Object", List.of(),
                    AccessFlags.PUBLIC | AccessFlags.SUPER, List.of(), topLevelFunctions, List.of(), null, 0));
        }
        classes.addAll(driver.syntheticClasses);
        return new IRModule(moduleName, classes, imports, driver.currentSourceName);
    }


    static void lowerAndEmit(CompilerDriver driver, CompilationUnitNode unit, DiagnosticCollector diagnostics,
                              Path outputDir, Target target) throws IOException {
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
        Files.createDirectories(outputDir);
        Backend backend = CompilerPipeline.selectBackend(driver, target);
        backend.emit(irModule, outputDir, driver.debugInfoEnabled);
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
    static IRModule analyzeAndLower(CompilerDriver driver, CompilationUnitNode unit,
                                    DiagnosticCollector diagnostics) {
        BuiltinTypes.resetEnums();
        for (AstNode d : unit.declarations()) {
            if (d instanceof EnumDeclarationNode en) BuiltinTypes.registerEnum(en.name());
        }
        unit = CompilerDesugar.desugarTests(unit, driver.discoveredTests, driver.testHarnessMode, driver.currentSourceName);
        unit = CompilerDesugar.desugarApplication(unit);
        driver.discoveredConfigKeys.clear();
        if (driver.target == Target.ANDROID) {
            unit = CompilerPipeline.appendAndroidHostIfNeeded(driver, unit);
        }
        driver.semanticAnalyzer = new SemanticAnalyzer();
        driver.semanticAnalyzer.setExternalTypes(driver.externalClasspath);
        driver.semanticAnalyzer.setDeclarationPackageLookup(d -> driver.declarationPackages.get(d));
        driver.semanticAnalyzer.analyze(unit, diagnostics);
        if (diagnostics.hasErrors()) {
            return null;
        }
        LabelId.reset();
        driver.currentModule = new IRModule("", List.of(), List.of());
        driver.currentUnit = unit;
        IRModule irModule = driver.applySuperBridges(CompilerPipeline.lowerToIR(driver, unit, diagnostics));
        if (diagnostics.hasErrors()) {
            return null;
        }
        driver.currentModule = irModule;
        IRModule unoptimized = irModule;
        if (driver.optimizeEnabled) {
            irModule = Optimizer.optimize(irModule);
            driver.currentModule = irModule;
        }
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
    static IRModule prepareForInterpretation(CompilerDriver driver, java.util.List<Path> sources,
                                             Path moduleRoot) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        driver.moduleRoot = moduleRoot;
        driver.target = Target.JVM;
        driver.currentDiagnostics = diagnostics;
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
            return ir;
        } catch (IOException e) {
            diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                    "Error reading source file: " + e.getMessage(), "COMP001");
            throw new KofInterpretException(diagnostics);
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
        return KofInterpreter.run(ir, args);
    }

    /** Parse + merge multi-arquivo + expansão de imports (extraído de compileSources). */
    static CompilationUnitNode parseAndMerge(CompilerDriver driver, java.util.List<Path> sources,
                                             Path rootAbs, DiagnosticCollector diagnostics)
            throws IOException {
        driver.currentSourceName = sources.get(0).getFileName() != null
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
        merged = CompilerImports.expandKofImports(merged, driver.moduleRoot, diagnostics, driver.declarationPackages);
        if (diagnostics.hasErrors()) return null;
        return merged;
    }

}