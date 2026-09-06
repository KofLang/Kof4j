package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompilerDriver {
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


    IRModule currentModule;
    CompilationUnitNode currentUnit;
    SemanticAnalyzer semanticAnalyzer;
Target target = Target.JVM;
    boolean optimizeEnabled = true;
    private boolean debugInfoEnabled = true;
    private java.util.function.BiConsumer<IRModule, IRModule> irObserver;
    private IRObserver irStatsObserver;
    DiagnosticCollector currentDiagnostics;
    String currentSourceName;
    final java.util.IdentityHashMap<KofOperation, SourcePosition> currentDebugPositions =
            new java.util.IdentityHashMap<>();
    final java.util.Deque<LabelId> breakLabels = new java.util.ArrayDeque<>();
    final java.util.Deque<LabelId> continueLabels = new java.util.ArrayDeque<>();
    boolean loweringMain;
    boolean mainArgsListField;


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

    /**
     * Classpath externo (.jar/.aar/diretórios) fornecido pelo build tool
     * (Gradle no Android). Usado para resolver assinaturas de métodos de
     * superclasses externas — o INVOKESPECIAL de super.metodo() exige o
     * descritor exato declarado na classe externa.
     */
    final ExternalClasspath externalClasspath = new ExternalClasspath();
    final List<String> pendingClasspathWarnings = new ArrayList<>();

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

    /**
     * Target android: se o programa não declarou a própria MainActivity
     * (em Kof), injeta o host WebView embutido — escrito EM KOF, compilado
     * pelo mesmo frontend. Nenhum arquivo Java é gerado.
     */




    /** Espelho driver-side do qualifyViaImports do SemanticAnalyzer. */

    /** Nome JVM da entidade: as classes top-level do programa ficam sem
     *  pacote (User.class); o Main é Default/Main. */




    final List<IRClass> syntheticClasses = new ArrayList<>();

    /** Cache de interfaces sintéticas de função (uma por assinatura). */
    final java.util.Map<String, Type.ClassType> functionInterfaces = new java.util.HashMap<>();

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
    final Map<String, List<IRMethod>> pendingSuperBridges = new java.util.LinkedHashMap<>();

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

    final java.util.List<TestInfo> discoveredTests = new java.util.ArrayList<>();
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


    Type listOfElementType(MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (!mc.arguments().isEmpty()) {
            return ExpressionTyper.inferExprType(this, mc.arguments().get(0), locals);
        }
        if (!mc.typeArguments().isEmpty()) {
            return CompilerTypes.toType(mc.typeArguments().get(0), currentUnit);
        }
        return Type.UnknownType.UNKNOWN;
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

    boolean isJvmTarget() {
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

    void emitPrimWidenNarrow(List<KofOperation> ops, ExpressionNode value,
                             Type elemType, List<IRLocalVariable> locals) {
        CompilerComparisons.emitPrimWidenNarrow(this, ops, value, elemType, locals);
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
    String toInternalName(String packageName, String simpleName) {
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

    boolean isAbstractMethod(MethodDeclarationNode method) {
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
