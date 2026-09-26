package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class SemanticAnalyzer {

    private SymbolTable currentScope;
    private CompilationUnitNode currentUnit;

    /** Classpath externo (android.jar etc.) para resolver membros de classes fora da IR. */
    private ExternalClasspath externalTypes;

    /** §253 face A: o alvo determina o gate SEM092 (native tem face B em runtime). */
    private Target target = Target.JVM;

    void setTarget(Target t) {
        this.target = t;
    }

    Target target() {
        return target;
    }

    void setExternalTypes(ExternalClasspath cp) {
        this.externalTypes = cp;
    }

    boolean isExternal(Type.ClassType ct) {
        return externalTypes != null && !ct.packageName().isEmpty()
                && externalTypes.knows(ct.internalName());
    }



    private final Map<String, SymbolTable.ClassSymbol> knownClasses = new HashMap<>();
    private final java.util.Set<String> interfaceNames = new java.util.HashSet<>();
    /** SG-017 (SEM041): classes declaradas `abstract` — `new A()` vira erro compile-time. */
    private final java.util.Set<String> abstractClasses = new java.util.HashSet<>();
    /** #339 (SEM070): classes declaradas `final` — `class D extends F` vira erro compile-time. */
    private final java.util.Set<String> finalClasses = new java.util.HashSet<>();
    /** X5.1 (D-X5-SURFACE): tipos `sealed` — nome simples → unidade de
     *  compilação (arquivo) que os declara. O conjunto de subtipos é fechado:
     *  um subtipo declarado fora dessa unidade é SEM080. */
    private final Map<String, String> sealedTypes = new HashMap<>();
    /** X5.3 (D-TYPE-VARIANCE): variância declaration-site por tipo genérico —
     *  nome simples → lista de variâncias ("out"/"in"/"") na ordem dos
     *  type-params. Ausente = invariante (compatibilidade total com o que
     *  já existia, §270). */
    private final Map<String, java.util.List<String>> genericVariance = new HashMap<>();
    private final Map<ExpressionNode, Type> expressionTypes = new IdentityHashMap<>();
    private final Map<MethodCallExpr, SymbolTable.MethodSymbol> resolvedMethods = new IdentityHashMap<>();
    private final Map<NewExpr, SymbolTable.ConstructorSymbol> resolvedConstructors = new IdentityHashMap<>();
    private final Map<String, SymbolTable> classMemberScopes = new HashMap<>();
    /** #628: expressões tipadas pelo grupo de análise em andamento. */
    private java.util.Set<ExpressionNode> trackedExpressionTypes;
    private String currentClassName;
    private boolean currentMethodStatic;

    /** #345: corpo sendo analisado é de método `static`? */
    boolean isCurrentMethodStatic() { return currentMethodStatic; }
    /** DD-02/#42: dentro de corpo de construtor? `this.campo =` em record só
     *  é legal no construtor (JVMS: final field init); métodos → SEM038. */
    boolean inConstructor;
    // #333: true durante corpo cujo `return <valor>` e SEMPRE erro (void
    // explicito ou funcao top-level sem tipo; a inferencia bug-26 so vale p/ METODOS,
    // onde SA e codegen mudam de tipo juntos — §130).
    boolean currentExplicitVoid;
    /** Pacote efetivo por declaração (multi-pacote num módulo), vindo do driver. */
    private java.util.function.Function<AstNode, String> declarationPackageLookup;

    void setDeclarationPackageLookup(java.util.function.Function<AstNode, String> lookup) {
        this.declarationPackageLookup = lookup;
    }

    String packageOf(AstNode decl) {
        if (declarationPackageLookup != null) {
            String pkg = declarationPackageLookup.apply(decl);
            if (pkg != null) return pkg;
        }
        return currentPackage;
    }
    private String currentFunctionName;
    private String currentPackage;
    private DiagnosticCollector diagnostics;

    void analyze(CompilationUnitNode unit, DiagnosticCollector diagnostics) {
        this.diagnostics = diagnostics;
        this.currentPackage = unit.packageName();
        this.currentScope = new SymbolTable();
        this.currentUnit = unit;
        // SG-011B (SEM047): sobrecarga de função top-level É permitida (oracle
        // JVM): nomes iguais com ASSINATURAS diferentes coexistem e a chamada
        // resolve o candidato no typer (TopLevelOverload). O que continua ERRO é
        // DUPLICATA EXATA — mesmo nome e mesmos tipos de parâmetro (a JVM também
        // rejeita; retorno diferente não conta como assinatura, igual ao JVM).
        // Antes (≤11/09) QUALQUER par homônimo era SEM047; afrouxar não regride
        // nada porque todo programa compilável tinha no máximo um candidato por
        // nome (a seleção multi-candidato só roda em código novo).
        if (diagnostics != null) {
            Map<String, SourcePosition> fnSigs = new HashMap<>();
            for (AstNode decl : unit.declarations()) {
                if (decl instanceof FunctionDeclarationNode f) {
                    List<String> pt = new ArrayList<>();
                    for (var p : f.parameters()) pt.add(p.type() != null ? p.type() : "?");
                    String sig = f.name() + "(" + String.join(",", pt) + ")";
                    SourcePosition prev = fnSigs.get(sig);
                    if (prev != null) {
                        diagnostics.error(f.position().file(), f.position().line(),
                                f.position().column(), 0,
                                "function '" + f.name() + "' with parameters ("
                                        + String.join(", ", pt) + ") is already defined at line "
                                        + prev.line() + "; duplicate signatures are not allowed"
                                        + " — overload requires a DIFFERENT parameter list",
                                "SEM047");
                    } else {
                        fnSigs.put(sig, f.position());
                    }
                }
            }
        }
        for (AstNode decl : unit.declarations()) {
            SymbolTableBuilder.preDeclareType(this, decl);
        }
        // N16: fwd-ref — membros de TODAS as classes antes de analisar corpos,
        // independente da ordem dos arquivos/PARTS
        for (AstNode decl : unit.declarations()) {
            SymbolTableBuilder.defineMembers(this, decl);
        }
        for (AstNode decl : unit.declarations()) {
            analyzeDeclaration(decl);
        }
        resolveMethodCalls(unit);
        checkNullLiteralArguments();
        // §251: tipos DECLARADOS (retorno/param/campo/componente) que não
        // resolvem nunca eram validados — a classe nem carregava
        // (`NoClassDefFoundError`), silencioso (R6). DEDICADO p/ manter este
        // arquivo quente longe do limite de 500 linhas.
        DeclaredTypeChecker.check(this);
    }

    /**
     * #266 (c) — emenda da mantenedora 15/09 (DECISIONS §7): o `null` literal
     * NUNCA é fabricável em posição alguma (SG-005/SEM048: null safety é por
     * narrowing; null só chega de API que devolve `T?`). Num parâmetro
     * NÃO-nullable PRIMITIVO (`f(Int n)` chamado `f(null)`) o call-site empurrava
     * `aconst_null` contra slot `int` e a classe só morria no load
     * (`VerifyError: Type null ... not assignable to integer`) — R6: zero
     * diagnóstico no compile. Agora é SEM048 honesto no `null` (espelha a
     * atribuição `x = null` em StatementAnalyzer e o return §125).
     * UM `T?` (NullableType) NUNCA cai aqui — passar null a ele é LEGÍTIMO
     * (a frente boxed dos 4 targets é a fila §241/#266/#259, não este guard).
     */
    private void checkNullLiteralArguments() {
        if (diagnostics == null) return;
        for (var e : resolvedMethods.entrySet()) {
            checkNullArgs(diagnostics, e.getKey().arguments(), e.getKey().position(),
                    e.getValue().parameterTypes(), e.getValue().name());
        }
        for (var e : resolvedConstructors.entrySet()) {
            checkNullArgs(diagnostics, e.getKey().arguments(), e.getKey().position(),
                    e.getValue().parameterTypes(), e.getValue().ownerClass());
        }
    }

    static void checkNullArgs(DiagnosticCollector diagnostics, List<ExpressionNode> args,
                              SourcePosition pos, List<Type> formals, String callee) {
        if (diagnostics == null || args == null || formals == null) return;
        for (int i = 0; i < args.size() && i < formals.size(); i++) {
            Type formal = formals.get(i);
            if (formal instanceof Type.PrimitiveType
                    && CompilerComparisons.isNullLiteral(args.get(i))) {
                diagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "null cannot be passed as argument " + (i + 1) + " of '" + callee
                                + "()': primitive parameter is non-nullable and null is never"
                                + " fabricable — declare the parameter '" + Type.describe(formal)
                                + "?' to accept an absent value, or pass a real value",
                        "SEM048");
            }
        }
    }

    Type getExpressionType(ExpressionNode expr) {
        Type t = expressionTypes.get(expr);
        return t != null ? t : Type.UnknownType.UNKNOWN;
    }

    SymbolTable.MethodSymbol getResolvedMethod(MethodCallExpr mc) {
        return resolvedMethods.get(mc);
    }

    Type resolvedMethodReturnType(MethodDeclarationNode method) {
        SymbolTable.MethodSymbol ms = methodSymbols.get(method);
        return ms != null ? ms.returnType() : null;
    }

    SymbolTable.ConstructorSymbol getResolvedConstructor(NewExpr ne) {
        return resolvedConstructors.get(ne);
    }

    SymbolTable.ClassSymbol getClass(String name) {
        return knownClasses.get(name);
    }

    Map<String, SymbolTable.ClassSymbol> allClasses() {
        return java.util.Collections.unmodifiableMap(knownClasses);
    }

    boolean isInterfaceType(String name) {
        return interfaceNames.contains(name);
    }


    SymbolTable.Symbol resolveInHierarchy(String className, String memberName) {
        return MemberResolver.resolveInHierarchy(this, className, memberName);
    }

    private void analyzeDeclaration(AstNode decl) {
        SourcePosition prev = diagnostics == null ? null : diagnostics.fallbackPosition();
        if (diagnostics != null && decl != null && decl.position() != null) {
            diagnostics.setFallbackPosition(decl.position());
        }
        try {
            analyzeDeclarationAt(decl);
        } finally {
            if (diagnostics != null) diagnostics.setFallbackPosition(prev);
        }
    }

    private void analyzeDeclarationAt(AstNode decl) {
        switch (decl) {
            case ClassDeclarationNode cls -> analyzeClass(cls);
            case RecordDeclarationNode rec -> {
                ClassShapeChecks.checkRecordDeclaration(this, rec);
                SemDeclarationAnalyzer.analyzeRecord(this, rec);
            }
            case EntityDeclarationNode ent -> SemDeclarationAnalyzer.analyzeEntity(this, ent);
            case InterfaceDeclarationNode iface -> SemDeclarationAnalyzer.analyzeInterface(this, iface);
            case FunctionDeclarationNode func -> SemDeclarationAnalyzer.analyzeFunction(this, func);
            default -> {}
        }
    }

    private boolean isLocalName(String name, SymbolTable scope) {
        if (scope == null) return false;
        return scope.resolve(name) != null;
    }

    Type resolveType(String name, SymbolTable scope) {
        return MemberResolver.resolveType(this, name, scope);
    }


    private void analyzeClass(ClassDeclarationNode cls) {
        // #339/#341 — forma da classe (extends final, final+abstract) antes
        // de qualquer análise de corpo: a JVM morreria no load.
        ClassShapeChecks.checkClassDeclaration(this, cls);
        String prevClass = currentClassName;
        currentClassName = cls.name();
        SymbolTable classScope = classMemberScopes.get(cls.name());
        if (classScope == null) {
            SymbolTableBuilder.defineClassMembers(this, cls);
            classScope = classMemberScopes.get(cls.name());
        }
        SymbolTable prevScope = currentScope;
        currentScope = classScope;
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field && field.initializer() != null) {
                inferType(field.initializer(), classScope);
            }
        }
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            beginExpressionTypeGroup();
            for (AstNode member : cls.members()) {
                if (member instanceof ConstructorDeclarationNode ctor) {
                    analyzeConstructorBody(ctor);
                } else if (member instanceof MethodDeclarationNode method) {
                    SymbolTable.MethodSymbol ms = methodSymbols.get(method);
                    Type before = ms != null ? ms.returnType() : null;
                    analyzeMethodBody(method);
                    Type after = ms != null ? ms.returnType() : null;
                    if (before != null && after != null && !before.equals(after)) {
                        changed = true;
                    }
                }
            }
            if (!changed) {
                discardExpressionTypeGroup();
                break;
            }
            clearExpressionTypes();
        }
        for (AstNode member : cls.members()) {
            if (member instanceof MethodDeclarationNode method && method.modifiers().contains("abstract")
                    && !cls.modifiers().contains("abstract")) {
                if (diagnostics != null) {
                    SourcePosition pos = method.position() != null ? method.position() : cls.position();
                    diagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            "abstract method '" + method.name() + "' is not allowed in non-abstract class '"
                                    + cls.name() + "' (declare the class as 'abstract')",
                            "SEM041");
                }
            }
        }
        ImplementationChecker.checkInterfaceImplementation(this, cls, classScope);
        ImplementationChecker.checkConflictingDefaults(this, cls, classScope);
        ImplementationChecker.checkOverrideReturnCompatibility(this, cls);
        MemberResolver.checkAbstractClassImplementation(this, cls);
        currentScope = prevScope;
        currentClassName = prevClass;
    }

    private final java.util.IdentityHashMap<ConstructorDeclarationNode, SymbolTable> ctorScopes = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<MethodDeclarationNode, SymbolTable> methodScopes = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<MethodDeclarationNode, SymbolTable.MethodSymbol> methodSymbols = new java.util.IdentityHashMap<>();
    private final java.util.Set<MethodDeclarationNode> reportedReturnPath =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    // Acesso ao estado compartilhado para as classes extraídas (REFACTOR-500
    // fase 6). Não há duplicação de estado: apenas leitura/direção.
    SymbolTable currentScope() { return currentScope; }
    CompilationUnitNode unit() { return currentUnit; }
    ExternalClasspath externalTypes() { return externalTypes; }
    String currentClassName() { return currentClassName; }
    String currentFunctionName() { return currentFunctionName; }
    String currentPackage() { return currentPackage; }
    DiagnosticCollector diagnostics() { return diagnostics; }
    java.util.Set<String> interfaceNames() { return java.util.Collections.unmodifiableSet(interfaceNames); }

    java.util.Set<String> abstractClasses() { return java.util.Collections.unmodifiableSet(abstractClasses); }
    /** #339 (SEM070): nomes simples das classes `final` do programa. */
    java.util.Set<String> finalClasses() { return java.util.Collections.unmodifiableSet(finalClasses); }
    Map<ExpressionNode, Type> expressionTypes() { return java.util.Collections.unmodifiableMap(expressionTypes); }
    Map<MethodCallExpr, SymbolTable.MethodSymbol> resolvedMethods() { return java.util.Collections.unmodifiableMap(resolvedMethods); }
    Map<NewExpr, SymbolTable.ConstructorSymbol> resolvedConstructors() { return java.util.Collections.unmodifiableMap(resolvedConstructors); }
    Map<String, SymbolTable> classMemberScopes() { return java.util.Collections.unmodifiableMap(classMemberScopes); }
    Map<ConstructorDeclarationNode, SymbolTable> ctorScopes() { return java.util.Collections.unmodifiableMap(ctorScopes); }
    Map<MethodDeclarationNode, SymbolTable> methodScopes() { return java.util.Collections.unmodifiableMap(methodScopes); }
    Map<MethodDeclarationNode, SymbolTable.MethodSymbol> methodSymbols() { return java.util.Collections.unmodifiableMap(methodSymbols); }

    // Mutadores package-private para as classes extraídas (REFACTOR-500 fase 6).
    // NÃO expõem a coleção interna (CodeQL `java/internal-representation-exposure`):
    // a mutação acontece AQUI, dentro do dono do estado. Os leitores usam os
    // getters read-only (`unmodifiable*`) acima.
    void putExpressionType(ExpressionNode expr, Type type) {
        expressionTypes.put(expr, type);
        if (trackedExpressionTypes != null) trackedExpressionTypes.add(expr);
    }
    void putResolvedMethod(MethodCallExpr call, SymbolTable.MethodSymbol sym) { resolvedMethods.put(call, sym); }
    void putResolvedConstructor(NewExpr expr, SymbolTable.ConstructorSymbol sym) { resolvedConstructors.put(expr, sym); }
    void putClassMemberScope(String className, SymbolTable scope) { classMemberScopes.put(className, scope); }
    void putCtorScope(ConstructorDeclarationNode ctor, SymbolTable scope) { ctorScopes.put(ctor, scope); }
    void putMethodScope(MethodDeclarationNode method, SymbolTable scope) { methodScopes.put(method, scope); }
    void putMethodSymbol(MethodDeclarationNode method, SymbolTable.MethodSymbol sym) { methodSymbols.put(method, sym); }
    void putClass(String name, SymbolTable.ClassSymbol sym) { knownClasses.put(name, sym); }
    void addInterface(String name) { interfaceNames.add(name); }
    void addAbstractClass(String name) { abstractClasses.add(name); }
    void addFinalClass(String name) { finalClasses.add(name); }

    /** X5.1: registra um tipo `sealed` com a unidade de compilação que o declara. */
    void addSealedType(String name, String file) { sealedTypes.put(name, file); }

    /** X5.1: o tipo (nome simples) foi declarado `sealed`? */
    boolean isSealedType(String name) { return sealedTypes.containsKey(name); }

    /** X5.1: unidade de compilação (arquivo) do tipo `sealed`, ou {@code null}. */
    String sealedTypeUnit(String name) { return sealedTypes.get(name); }

    /** X5.3: registra a variância declaration-site dos type-params de um tipo
     *  genérico (entradas cruas `"out T"`/`"in T"`/`"T"`). */
    void registerTypeParameters(String name, java.util.List<String> typeParameters) {
        if (name == null || typeParameters == null || typeParameters.isEmpty()) return;
        genericVariance.put(name, typeParameters.stream().map(TypeParams::variance).toList());
    }

    /** X5.3: variâncias declaradas do tipo (índice = posição do type-param),
     *  ou {@code null} quando o tipo não declara type-params no módulo. */
    java.util.List<String> varianceOf(String name) { return genericVariance.get(name); }
    // REFACTOR-500 (split p/ SemDeclarationAnalyzer): mutadores de ESTADO DE
    // CONTEXTO — a mutacao acontece no dono do estado (mesmo padrao da fase 6);
    // os satellites dirigem via estes setters.
    void setCurrentScope(SymbolTable scope) { this.currentScope = scope; }
    void setCurrentClassName(String name) { this.currentClassName = name; }
    void setCurrentFunctionName(String name) { this.currentFunctionName = name; }
    /** Remove apenas o grupo atual (bug 26); preserva tipos de outras declarações (#628). */
    void clearExpressionTypes() {
        if (trackedExpressionTypes == null) return;
        for (ExpressionNode expr : trackedExpressionTypes) expressionTypes.remove(expr);
        trackedExpressionTypes = null;
    }

    /** Marca o início de uma reanálise; só as expressões dela podem ser descartadas. */
    void beginExpressionTypeGroup() {
        trackedExpressionTypes = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /** Mantém os tipos do grupo e para de rastreá-los (última passada). */
    void discardExpressionTypeGroup() {
        trackedExpressionTypes = null;
    }

    boolean knowsClass(String name) { return knownClasses.containsKey(name); }

    private void analyzeConstructorBody(ConstructorDeclarationNode ctor) {
        SymbolTable ctorScope = ctorScopes.get(ctor);
        if (ctorScope == null || ctor.body() == null || ctor.body().isEmpty()) return;
        SymbolTable prevScope = currentScope;
        currentScope = ctorScope;
        boolean prevCtor = inConstructor;
        inConstructor = true;
        // §130: o laço de 4 passes (inference de return-type) chama isto de novo
        // no MESMO escopo — sem filho, o 2º pass reclama SEM024 de cada `var`
        // já definido no 1º. Escopo-filho por análise: params/`this`/campos
        // continuam visíveis via resolve() pai-acima; locals não vazam entre
        // passes (só o pinning de tipo SG-008 é por-pass, e o codegen lê
        // expressionTypes, não estes escopos).
        boolean prevEvCtor = currentExplicitVoid;
        currentExplicitVoid = true; // #333: constructor nunca devolve valor
        StatementAnalyzer.analyzeBody(this, ctor.body(), ctorScope.enterScope(), Type.PrimitiveType.VOID);
        currentExplicitVoid = prevEvCtor;
        inConstructor = prevCtor;
        currentScope = prevScope;
    }

    void analyzeMethodBody(MethodDeclarationNode method) {
        SymbolTable methodScope = methodScopes.get(method);
        if (methodScope == null) return;
        checkThrowsClause(method.thrownExceptions(), "method '" + method.name() + "'");
        Type returnType = resolveType(method.returnType(), methodScope);
        // bug 26: corpo pode terminar sem return/throw → SEM036 (uma vez por
        // método — o loop de 4 passes chamaria de novo). Antes do early-return
        // de corpo vazio, que é exatamente o caso do bug. Abstract não conta
        // (corpo vazio é legítimo).
        if (!method.modifiers().contains("abstract") && reportedReturnPath.add(method)) {
            ReturnPathAnalyzer.check(this, method.body(), returnType, method.position(),
                    "method '" + method.name() + "'");
        }
        if (method.body() == null || method.body().isEmpty()) return;
        SymbolTable prevScope = currentScope;
        currentScope = methodScope;
        // #345: contexto static — campo de instância referido nu dentro de
        // método static (implícito `this`) não tem slot: o emit gerava
        // aload_0 → VerifyError "Bad local variable type" no load. A SEM
        // rejeita com SEM075 (R6; a referência só existe via instância).
        boolean prevStatic = currentMethodStatic;
        currentMethodStatic = method.modifiers().contains("static");
        // §130: ver analyzeConstructorBody — o laço de 4 passes re-executa o
        // corpo (quando um `return <expr>` void reinfer o tipo via bug 26) e o
        // MESMO escopo reclamava SEM024 de cada var do pass anterior.
        StatementAnalyzer.analyzeBody(this, method.body(), methodScope.enterScope(), returnType);
        currentMethodStatic = prevStatic;
        currentScope = prevScope;
        if (Type.isVoid(returnType) && method.body().getLast() instanceof ReturnStmt ret
                && ret.value() != null) {
            Type inferred = inferType(ret.value(), methodScope);
            SymbolTable.MethodSymbol ms = methodSymbols.get(method);
            if (ms != null && !(inferred instanceof Type.UnknownType)) {
                ms.setReturnType(inferred);
            }
        }
    }

    /**
     * SG-019 (SEM045): a cláusula `throw X, Y` não é checked-exception (exceções
     * são Strings em Kof), mas os nomes devem ser TIPOS conhecidos — classe do
     * módulo, interface, ou externa via import. Antes era capturado pelo parser
     * e nunca validado (decorativo).
     */
    void checkThrowsClause(List<String> thrown, String owner) {
        SemDeclarationAnalyzer.checkThrowsClause(this, thrown, owner);
    }

    /**
     * Assignment como STATEMENT (`a = b`, `i = i + 1` no update do for):
     * infere alvo/valor e valida assignability (SEM012) SEM emitir o SEM027
     * (que é reservado para assignment usado como VALOR — bug 12).
     */

    Type inferType(ExpressionNode expr, SymbolTable scope) {
        return SemExpressionTyper.inferType(this, expr, scope);
    }

    /** Reporta erro de análise sem posição precisa (estilo dos demais SEM*xx). */
    void reportError(AstNode n, String message, String code) {
        if (diagnostics == null) return;
        SourcePosition p = n.position();
        String file = p != null ? p.file() : "";
        int line = p != null ? p.line() : 0;
        int col = p != null ? p.column() : 0;
        int len = p != null ? p.length() : 0;
        diagnostics.error(file, line, col, len, message, code);
    }

    /**
     * Define no escopo do case as variáveis de um pattern:
     * {@code case T v} → {@code v:T}; {@code case T(var x, var y)} → campos por
     * índice (record) ou por nome. Espelha a lógica do {@code SwitchStmt}.
     */



    private void resolveMethodCalls(CompilationUnitNode unit) {
        // §140 (12/09): este visitor era NO-OP — resolvia chamadas mas nunca
        // populava resolvedMethods/expressionTypes nem reportava diagnóstico.
        // Desligá-lo mantém a suíte semântica inteira verde (CompilerDriverTest
        // 252 + SemanticResolutionTest 25 + TopLevelOverload 6 + CoreRegression
        // 50 + Exceptions 9 + KofEnumSwitch 4 = 346, medido 12/09). A resolução
        // REAL de sobrecarga hoje vive em MethodCallTyper/OverloadSelector
        // (SG-011B, 40abd0ed/b55c24c0) — o visitor era resíduo de 05e10140.
        // Mantido o método (e a chamada em analyze()) como contrato de fase do
        // pipeline; corpo vazio até a decisão de deletar a fase por completo.
    }

}
