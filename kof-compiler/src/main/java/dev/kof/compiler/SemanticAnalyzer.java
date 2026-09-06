package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

class SemanticAnalyzer {

    private SymbolTable currentScope;
    private CompilationUnitNode currentUnit;

    /** Classpath externo (android.jar etc.) para resolver membros de classes fora da IR. */
    private ExternalClasspath externalTypes;

    void setExternalTypes(ExternalClasspath cp) {
        this.externalTypes = cp;
    }

    boolean isExternal(Type.ClassType ct) {
        return externalTypes != null && !ct.packageName().isEmpty()
                && externalTypes.knows(ct.internalName());
    }



    private final Map<String, SymbolTable.ClassSymbol> knownClasses = new HashMap<>();
    private final java.util.Set<String> interfaceNames = new java.util.HashSet<>();
    private final Map<ExpressionNode, Type> expressionTypes = new IdentityHashMap<>();
    private final Map<MethodCallExpr, SymbolTable.MethodSymbol> resolvedMethods = new IdentityHashMap<>();
    private final Map<NewExpr, SymbolTable.ConstructorSymbol> resolvedConstructors = new IdentityHashMap<>();
    private final Map<String, SymbolTable> classMemberScopes = new HashMap<>();
    private String currentClassName;
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
        return knownClasses;
    }

    boolean isInterfaceType(String name) {
        return interfaceNames.contains(name);
    }


    SymbolTable.Symbol resolveInHierarchy(String className, String memberName) {
        return MemberResolver.resolveInHierarchy(this, className, memberName);
    }

    private void analyzeDeclaration(AstNode decl) {
        switch (decl) {
            case ClassDeclarationNode cls -> analyzeClass(cls);
            case RecordDeclarationNode rec -> analyzeRecord(rec);
            case EntityDeclarationNode ent -> analyzeEntity(ent);
            case InterfaceDeclarationNode iface -> analyzeInterface(iface);
            case EnumDeclarationNode en -> { }
            case FunctionDeclarationNode func -> analyzeFunction(func);
            default -> {}
        }
    }

    private boolean isLocalName(String name, SymbolTable scope) {
        if (scope == null) return false;
        return scope.resolve(name) != null;
    }

    private Type resolveType(String name, SymbolTable scope) {
        SymbolTable.Symbol sym = scope != null ? scope.resolve(name) : null;
        if (sym instanceof SymbolTable.TypeParameterSymbol) return sym.type();
        Type viaImports = MemberResolver.qualifyViaImports(currentUnit, name);
        if (viaImports != null) return viaImports;
        return MemberResolver.qualifiedType(Type.of(name));
    }


    private void analyzeClass(ClassDeclarationNode cls) {
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
            expressionTypes.clear();
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
            if (!changed) break;
        }
        currentScope = prevScope;
        currentClassName = prevClass;
    }

    private final java.util.IdentityHashMap<ConstructorDeclarationNode, SymbolTable> ctorScopes = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<MethodDeclarationNode, SymbolTable> methodScopes = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<MethodDeclarationNode, SymbolTable.MethodSymbol> methodSymbols = new java.util.IdentityHashMap<>();

    // Acesso ao estado compartilhado para as classes extraídas (REFACTOR-500
    // fase 6). Não há duplicação de estado: apenas leitura/direção.
    SymbolTable currentScope() { return currentScope; }
    CompilationUnitNode unit() { return currentUnit; }
    ExternalClasspath externalTypes() { return externalTypes; }
    String currentClassName() { return currentClassName; }
    String currentFunctionName() { return currentFunctionName; }
    String currentPackage() { return currentPackage; }
    DiagnosticCollector diagnostics() { return diagnostics; }
    java.util.Set<String> interfaceNames() { return interfaceNames; }
    Map<ExpressionNode, Type> expressionTypes() { return expressionTypes; }
    Map<MethodCallExpr, SymbolTable.MethodSymbol> resolvedMethods() { return resolvedMethods; }
    Map<NewExpr, SymbolTable.ConstructorSymbol> resolvedConstructors() { return resolvedConstructors; }
    Map<String, SymbolTable> classMemberScopes() { return classMemberScopes; }
    java.util.IdentityHashMap<ConstructorDeclarationNode, SymbolTable> ctorScopes() { return ctorScopes; }
    java.util.IdentityHashMap<MethodDeclarationNode, SymbolTable> methodScopes() { return methodScopes; }
    java.util.IdentityHashMap<MethodDeclarationNode, SymbolTable.MethodSymbol> methodSymbols() { return methodSymbols; }

    private void analyzeConstructorBody(ConstructorDeclarationNode ctor) {
        SymbolTable ctorScope = ctorScopes.get(ctor);
        if (ctorScope == null || ctor.body() == null || ctor.body().isEmpty()) return;
        SymbolTable prevScope = currentScope;
        currentScope = ctorScope;
        StatementAnalyzer.analyzeBody(this, ctor.body(), ctorScope, Type.PrimitiveType.VOID);
        currentScope = prevScope;
    }

    private void analyzeMethodBody(MethodDeclarationNode method) {
        SymbolTable methodScope = methodScopes.get(method);
        if (methodScope == null || method.body() == null || method.body().isEmpty()) return;
        Type returnType = resolveType(method.returnType(), methodScope);
        SymbolTable prevScope = currentScope;
        currentScope = methodScope;
        StatementAnalyzer.analyzeBody(this, method.body(), methodScope, returnType);
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

    private void analyzeEntity(EntityDeclarationNode ent) {
        List<RecordComponentNode> components = new java.util.ArrayList<>();
        for (EntityFieldNode f : ent.fields()) {
            components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
        }
        analyzeRecord(new RecordDeclarationNode(ent.position(), ent.name(), ent.modifiers(),
                null, List.of(), components, List.of()));
    }

    private void analyzeRecord(RecordDeclarationNode rec) {
        String prevClass = currentClassName;
        currentClassName = rec.name();
        SymbolTable classScope = classMemberScopes.get(rec.name());
        if (classScope == null) {
            SymbolTableBuilder.defineRecordMembers(this, rec);
            classScope = classMemberScopes.get(rec.name());
        }
        SymbolTable prevScope = currentScope;
        currentScope = classScope;
        for (RecordComponentNode comp : rec.components()) {
            if (comp.initializer() != null) {
                inferType(comp.initializer(), classScope);
            }
        }
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            expressionTypes.clear();
            for (AstNode member : rec.members()) {
                if (member instanceof MethodDeclarationNode method) {
                    SymbolTable.MethodSymbol ms = methodSymbols.get(method);
                    Type before = ms != null ? ms.returnType() : null;
                    analyzeMethodBody(method);
                    Type after = ms != null ? ms.returnType() : null;
                    if (before != null && after != null && !before.equals(after)) {
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        currentScope = prevScope;
        currentClassName = prevClass;
    }

    private void analyzeInterface(InterfaceDeclarationNode iface) {
        String prevClass = currentClassName;
        currentClassName = iface.name();
        SymbolTable classScope = classMemberScopes.get(iface.name());
        if (classScope == null) {
            SymbolTableBuilder.defineInterfaceMembers(this, iface);
            classScope = classMemberScopes.get(iface.name());
        }
        SymbolTable prevScope = currentScope;
        currentScope = classScope;
        currentScope = prevScope;
        currentClassName = prevClass;
    }

    private void analyzeFunction(FunctionDeclarationNode func) {
        String prevFunction = currentFunctionName;
        currentFunctionName = func.name();
        SymbolTable funcScope = currentScope.enterScope();
        for (String tp : func.typeParameters()) {
            funcScope.define(new SymbolTable.TypeParameterSymbol(tp));
        }
        Type returnType = resolveType(func.returnType(), funcScope);
        int idx = 0;
        for (FormalParameterNode param : func.parameters()) {
            Type paramType = Type.of(param.type());
            funcScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        SymbolTable prevScope = currentScope;
        currentScope = funcScope;
        StatementAnalyzer.analyzeBody(this, func.body(), funcScope, returnType);
        currentScope = prevScope;
    }

    /**
     * Assignment como STATEMENT (`a = b`, `i = i + 1` no update do for):
     * infere alvo/valor e valida assignability (SEM012) SEM emitir o SEM027
     * (que é reservado para assignment usado como VALOR — bug 12).
     */

    Type inferType(ExpressionNode expr, SymbolTable scope) {
        return ExpressionTyper.inferType(this, expr, scope);
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
        for (AstNode decl : unit.declarations()) {
            if (decl instanceof FunctionDeclarationNode func && func.body() != null) {
                for (StatementNode stmt : func.body()) resolveInStatement(stmt);
            } else if (decl instanceof ClassDeclarationNode cls) {
                for (AstNode member : cls.members()) {
                    if (member instanceof MethodDeclarationNode method && method.body() != null) {
                        for (StatementNode stmt : method.body()) resolveInStatement(stmt);
                    } else if (member instanceof ConstructorDeclarationNode ctor) {
                        for (StatementNode stmt : ctor.body()) resolveInStatement(stmt);
                    }
                }
            } else if (decl instanceof RecordDeclarationNode rec) {
                for (AstNode member : rec.members()) {
                    if (member instanceof MethodDeclarationNode method && method.body() != null) {
                        for (StatementNode stmt : method.body()) resolveInStatement(stmt);
                    }
                }
            }
        }
    }

    private void resolveInStatement(StatementNode stmt) {
        switch (stmt) {
            case BlockStmt block -> {
                for (StatementNode s : block.statements()) resolveInStatement(s);
            }
            case IfStmt ifStmt -> {
                resolveInStatement(ifStmt.thenBranch());
                if (ifStmt.elseBranch() != null) resolveInStatement(ifStmt.elseBranch());
            }
            case WhileStmt ws -> resolveInStatement(ws.body());
            case DoWhileStmt dws -> resolveInStatement(dws.body());
            case ForStmt fs -> {
                if (fs.init() != null) resolveInStatement(fs.init());
                if (fs.update() != null) resolveInExpression(fs.update());
                resolveInStatement(fs.body());
            }
            case ExpressionStmt es -> resolveInExpression(es.expression());
            case ReturnStmt ret -> {
                if (ret.value() != null) resolveInExpression(ret.value());
            }
            default -> {}
        }
    }

    private void resolveInExpression(ExpressionNode expr) {
        if (expr == null) return;
        switch (expr) {
            case MethodCallExpr mc -> {
                if (mc.receiver() != null) resolveInExpression(mc.receiver());
                for (ExpressionNode arg : mc.arguments()) resolveInExpression(arg);
            }
            case BinaryExpr bin -> {
                // iterate the left-associative chain (huge concat trees)
                ExpressionNode cur = bin;
                while (cur instanceof BinaryExpr be) {
                    resolveInExpression(be.right());
                    cur = be.left();
                }
                resolveInExpression(cur);
            }
            case UnaryExpr ue -> resolveInExpression(ue.operand());
            case AssignmentExpr ae -> {
                resolveInExpression(ae.target());
                resolveInExpression(ae.value());
            }
            case NewExpr ne -> {
                for (ExpressionNode arg : ne.arguments()) resolveInExpression(arg);
            }
            case FieldAccessExpr fa -> resolveInExpression(fa.receiver());
            default -> {}
        }
    }
}
