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
            // SCRIPT não emite artefato — é interpretado (interpret()). O
            // chamador (lowerAndEmit) bloqueia antes; isto é defensivo.
            case SCRIPT -> throw new IllegalStateException("SCRIPT has no backend");
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
                // FFI (TIER 2.1.3/2.1.7): binding suportado (JVM Int→Int,
                // String→Int, Double→Double; Native Int→Int, String→Int) não é
                // gap; o resto é gap honesto por target — FFI002 no JS (web/edge
                // sem FFI nativo), FFI001 nos demais. Nunca stub silencioso (R6).
                if (diagnostics != null && !CompilerPipeline.isExternBound(driver, ext)) {
                    SourcePosition sp = ext.position();
                    String lib = ext.library() != null ? " in " + ext.library() : "";
                    String code = driver.target == Target.JS ? "FFI002" : "FFI001";
                    String msg = driver.target == Target.JS
                            ? "extern '" + ext.name() + "'" + lib + ": FFI not available on the JS target (FFI002)"
                            : "extern '" + ext.name() + "'" + lib + ": FFI binding not implemented on the "
                                    + driver.target + " target yet (FFI001)";
                    diagnostics.error(sp != null ? sp.file() : "", sp != null ? sp.line() : 0,
                            sp != null ? sp.column() : 0, 0, msg, code);
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
        if (target.isScript()) {
            // KofScript não compila — interpreta (interpret()). Emitir com
            // este target seria fallback silencioso (R6): diagnóstico claro.
            diagnostics.error(driver.currentSourceName, 0, 0, 0,
                    "target 'script' não emite artefatos; use kof run --target script"
                            + " (interpretação direta da IR) ou outro target",
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
        unit = CompilerDesugar.desugarNestedFunctions(unit);
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


    // ── FFI (TIER 2.1.4) — binding suportado por target ──
    static boolean isExternBound(CompilerDriver driver, ExternalFunctionNode ext) {
        if (ext.parameters().size() != 1) return false;
        String p = ext.parameters().get(0).type();
        String r = ext.returnType();
        if (driver.target == Target.JVM) {
            return (isIntType(r) && (isIntType(p) || isStringType(p)))
                    || (isDoubleType(r) && isDoubleType(p));
        }
        // NATIVE: dlopen/dlsym segfaulta no binário nativo (glibc exige TLS
        // que o _start cru não inicializa) — bug registrado (known-bugs);
        // enquanto o backend não inicializa libc, extern nativo é FFI001 (R6).
        return false;
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
