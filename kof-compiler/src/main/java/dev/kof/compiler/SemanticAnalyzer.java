package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

class SemanticAnalyzer {

    private static final String SSE_CONNECTION_TYPE =
            "dev.kof.runtime.KofRuntime$SseConnection";

    private SymbolTable currentScope;
    private CompilationUnitNode currentUnit;

    /** Classpath externo (android.jar etc.) para resolver membros de classes fora da IR. */
    private ExternalClasspath externalTypes;

    void setExternalTypes(ExternalClasspath cp) {
        this.externalTypes = cp;
    }

    private boolean isExternal(Type.ClassType ct) {
        return externalTypes != null && !ct.packageName().isEmpty()
                && externalTypes.knows(ct.internalName());
    }

    private boolean isObjectMethod(String name, int argCount) {
        return switch (name) {
            case "hashCode", "toString", "getClass" -> argCount == 0;
            case "equals" -> argCount == 1;
            default -> false;
        };
    }

    private boolean isBuiltinTypeName(String name) {
        return switch (name) {
            case "String", "string", "Object", "Int", "int", "Long", "long",
                    "Bool", "bool", "boolean", "Boolean", "Char", "char",
                    "Byte", "byte", "Short", "short", "Float", "float",
                    "Double", "double", "void", "Void" -> true;
            default -> false;
        };
    }

    /**
     * Nome simples declarado em import vira tipo qualificado
     * ("import android.webkit.WebView" → ClassType("android.webkit","WebView")).
     * Sem isso, tipos de classes externas saem sem pacote e o descritor
     * JVM quebra.
     */
    private Type qualifyViaImports(String name) {
        if (name.contains(".") || name.contains("<") || name.endsWith("[]")) return null;
        if (currentUnit == null) return null;
        for (String imp : currentUnit.imports()) {
            if (!imp.endsWith("*") && imp.endsWith("." + name)) {
                String pkg = imp.substring(0, imp.lastIndexOf('.'));
                return new Type.ClassType(pkg, name, List.of());
            }
        }
        return null;
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

    private String packageOf(AstNode decl) {
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
            preDeclareType(decl);
        }
        // N16: fwd-ref — membros de TODAS as classes antes de analisar corpos,
        // independente da ordem dos arquivos/PARTS
        for (AstNode decl : unit.declarations()) {
            defineMembers(decl);
        }
        for (AstNode decl : unit.declarations()) {
            analyzeDeclaration(decl);
        }
        resolveMethodCalls(unit);
    }

    private void defineMembers(AstNode decl) {
        switch (decl) {
            case ClassDeclarationNode cls -> defineClassMembers(cls);
            case RecordDeclarationNode rec -> defineRecordMembers(rec);
            case EntityDeclarationNode ent -> defineEntityMembers(ent);
            case InterfaceDeclarationNode iface -> defineInterfaceMembers(iface);
            case EnumDeclarationNode en -> { }
            default -> {}
        }
    }

    private void defineClassMembers(ClassDeclarationNode cls) {
        if (classMemberScopes.containsKey(cls.name())) return;
        SymbolTable.ClassSymbol classSym = knownClasses.get(cls.name());
        SymbolTable classScope = classSym.members().enterScope();
        classMemberScopes.put(cls.name(), classScope);
        for (String tp : cls.typeParameters()) {
            classScope.define(new SymbolTable.TypeParameterSymbol(tp));
        }
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) {
                Type fieldType = resolveType(field.type(), classScope);
                int flags = field.modifiers().contains("static") ? AccessFlags.STATIC : 0;
                SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(field.name(), fieldType, flags, cls.name());
                classSym.members().define(fs);
                classScope.define(fs);
            }
        }
        boolean hasCtor = false;
        for (AstNode member : cls.members()) {
            if (member instanceof ConstructorDeclarationNode ctor) {
                defineConstructorSymbol(ctor, cls.name(), classScope);
                hasCtor = true;
            } else if (member instanceof MethodDeclarationNode method) {
                defineMethodSymbol(method, cls.name(), classScope);
            }
        }
        if (!hasCtor) {
            classScope.define(new SymbolTable.ConstructorSymbol(cls.name(), List.of(), 1));
        }
    }

    private void defineRecordMembers(RecordDeclarationNode rec) {
        if (classMemberScopes.containsKey(rec.name())) return;
        SymbolTable.ClassSymbol classSym = knownClasses.get(rec.name());
        SymbolTable classScope = classSym.members().enterScope();
        classMemberScopes.put(rec.name(), classScope);
        List<String> typeParams = rec.typeParameters() == null ? List.of() : rec.typeParameters();
        for (String tp : typeParams) {
            classScope.define(new SymbolTable.TypeParameterSymbol(tp));
        }
        List<Type> compTypes = new ArrayList<>();
        for (RecordComponentNode comp : rec.components()) {
            Type compType = resolveType(comp.type(), classScope);
            compTypes.add(compType);
            SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(comp.name(), compType, 0, rec.name());
            classSym.members().define(fs);
            classScope.define(fs);
        }
        SymbolTable.ConstructorSymbol ctorSym = new SymbolTable.ConstructorSymbol(rec.name(), compTypes, 1);
        classSym.members().define(ctorSym);
        classScope.define(ctorSym);
        for (RecordComponentNode comp : rec.components()) {
            Type compType = resolveType(comp.type(), classScope);
            SymbolTable.MethodSymbol ms = new SymbolTable.MethodSymbol(comp.name(), rec.name(),
                    compType, List.of(), 1, SymbolTable.DispatchKind.INSTANCE);
            classSym.members().define(ms);
            classScope.define(ms);
        }
        for (AstNode member : rec.members()) {
            if (member instanceof MethodDeclarationNode method) {
                defineMethodSymbol(method, rec.name(), classScope);
            }
        }
    }

    private void defineEntityMembers(EntityDeclarationNode ent) {
        List<RecordComponentNode> components = new ArrayList<>();
        for (EntityFieldNode f : ent.fields()) {
            components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
        }
        RecordDeclarationNode synthetic = new RecordDeclarationNode(ent.position(), ent.name(), ent.modifiers(),
                null, List.of(), components, List.of());
        // preDeclare já criou classSym para ent; reutiliza
        defineRecordMembers(synthetic);
    }

    private void defineInterfaceMembers(InterfaceDeclarationNode iface) {
        if (classMemberScopes.containsKey(iface.name())) return;
        SymbolTable.ClassSymbol classSym = knownClasses.get(iface.name());
        SymbolTable classScope = classSym.members().enterScope();
        classMemberScopes.put(iface.name(), classScope);
        for (AstNode member : iface.members()) {
            if (member instanceof MethodDeclarationNode method) {
                Type returnType = resolveType(method.returnType(), classScope);
                List<Type> paramTypes = new ArrayList<>();
                for (FormalParameterNode p : method.parameters()) paramTypes.add(Type.of(p.type()));
                SymbolTable.MethodSymbol ms = new SymbolTable.MethodSymbol(method.name(), iface.name(),
                        returnType, paramTypes, 0, SymbolTable.DispatchKind.INSTANCE);
                classScope.define(ms);
                classSym.members().define(ms);
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
        return knownClasses;
    }

    boolean isInterfaceType(String name) {
        return interfaceNames.contains(name);
    }

    SymbolTable.Symbol resolveInHierarchy(String className, String memberName) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(className);
        visited.add(className);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            SymbolTable.ClassSymbol cs = knownClasses.get(current);
            if (cs == null) continue;
            SymbolTable.Symbol s = cs.members().resolve(memberName);
            if (s != null) return s;
            if (cs.superClass() != null && !"Object".equals(cs.superClass()) && !visited.contains(cs.superClass())) {
                visited.add(cs.superClass());
                queue.add(cs.superClass());
            }
            for (String iface : cs.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
        }
        return null;
    }

    private void preDeclareType(AstNode decl) {
        if (decl instanceof ClassDeclarationNode cls) {
            SymbolTable members = new SymbolTable();
            // superclasse qualificada pelos imports: "extends Activity" com
            // "import android.app.Activity" vira "android.app.Activity" —
            // sem isso a resolução externa (classpath) nunca encontra a classe
            String superQualified = cls.superClass();
            if (superQualified != null && !"Object".equals(superQualified)) {
                Type viaImports = qualifyViaImports(superQualified);
                if (viaImports instanceof Type.ClassType qt) {
                    superQualified = qt.packageName() + "." + qt.name();
                }
            }
            String declPkg = packageOf(cls);
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(cls.name(), declPkg,
                    cls.superClass() != null ? superQualified : "Object",
                    cls.interfaces(), members);
            knownClasses.put(cls.name(), sym);
            currentScope.define(sym);
        } else if (decl instanceof RecordDeclarationNode rec) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(rec.name(), packageOf(rec),
                    "Record", rec.interfaces(), members);
            knownClasses.put(rec.name(), sym);
            currentScope.define(sym);
        } else if (decl instanceof EntityDeclarationNode ent) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(ent.name(), packageOf(ent),
                    "Record", List.of(), members);
            knownClasses.put(ent.name(), sym);
            currentScope.define(sym);
        } else if (decl instanceof EnumDeclarationNode en) {
            SymbolTable members = new SymbolTable();
            Type self = new Type.ClassType("", en.name(), List.of());
            members.define(new SymbolTable.MethodSymbol("values", en.name(),
                    new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING)), List.of(),
                    AccessFlags.STATIC, SymbolTable.DispatchKind.STATIC));
            members.define(new SymbolTable.MethodSymbol("valueOf", en.name(),
                    self, List.of(BuiltinTypes.STRING),
                    AccessFlags.STATIC, SymbolTable.DispatchKind.STATIC));
            members.define(new SymbolTable.MethodSymbol("name", en.name(),
                    BuiltinTypes.STRING, List.of(),
                    0, SymbolTable.DispatchKind.INSTANCE));
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(en.name(), packageOf(en),
                    "Object", List.of(), members);
            knownClasses.put(en.name(), sym);
            currentScope.define(sym);
        } else if (decl instanceof InterfaceDeclarationNode iface) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(iface.name(), packageOf(iface),
                    "Object", iface.interfaces(), members);
            knownClasses.put(iface.name(), sym);
            interfaceNames.add(iface.name());
            currentScope.define(sym);
        }
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
        Type viaImports = qualifyViaImports(name);
        if (viaImports != null) return viaImports;
        // qualifyDeep: recursa nos type-arguments — `List<NodeUI>` com
        // `import com.dev.NodeUI` precisa do pacote no ARG (senão o receiver
        // do `.get()` fica ClassType("","NodeUI") e o checkcast sai sem pacote
        // → NoClassDefFoundError). Idempotente; não toca builtin/enum/nome local.
        return CompilerTypes.qualifyDeep(qualifiedType(Type.of(name)), currentUnit, this);
    }

    /**
     * Nomes qualificados ("android.os.Bundle") precisam do pacote separado
     * do nome simples — senão o descritor JVM sai com pontos
     * (Landroid.os.Bundle;) e a classe não carrega.
     */
    static Type qualifiedType(Type type) {
        if (type instanceof Type.ClassType ct && !ct.name().contains("<")
                && ct.packageName().isEmpty()) {
            int lastDot = ct.name().lastIndexOf('.');
            if (lastDot > 0) {
                return new Type.ClassType(ct.name().substring(0, lastDot),
                        ct.name().substring(lastDot + 1), ct.typeArguments());
            }
        }
        return type;
    }

    private void analyzeClass(ClassDeclarationNode cls) {
        String prevClass = currentClassName;
        currentClassName = cls.name();
        SymbolTable classScope = classMemberScopes.get(cls.name());
        if (classScope == null) {
            defineClassMembers(cls);
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

    private void defineConstructorSymbol(ConstructorDeclarationNode ctor, String className, SymbolTable classScope) {
        List<Type> paramTypes = new ArrayList<>();
        SymbolTable ctorScope = classScope.enterScope();
        ctorScope.define(new SymbolTable.ParameterSymbol("this",
                new Type.ClassType(currentPackage, className, List.of()), 0));
        int idx = 1;
        for (FormalParameterNode param : ctor.parameters()) {
            Type paramType = resolveType(param.type(), ctorScope);
            paramTypes.add(paramType);
            ctorScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        SymbolTable.ConstructorSymbol ctorSym = new SymbolTable.ConstructorSymbol(className, paramTypes, 1);
        classScope.define(ctorSym);
        SymbolTable.ClassSymbol cs = knownClasses.get(className);
        if (cs != null) cs.members().define(ctorSym);
        ctorScopes.put(ctor, ctorScope);
    }

    private void defineMethodSymbol(MethodDeclarationNode method, String className, SymbolTable classScope) {
        SymbolTable methodScope = classScope.enterScope();
        methodScope.define(new SymbolTable.ParameterSymbol("this",
                new Type.ClassType(currentPackage, className, List.of()), 0));
        Type returnType = resolveType(method.returnType(), methodScope);
        List<Type> paramTypes = new ArrayList<>();
        int idx = 1;
        for (FormalParameterNode param : method.parameters()) {
            Type paramType = resolveType(param.type(), methodScope);
            paramTypes.add(paramType);
            methodScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        SymbolTable.MethodSymbol methodSym = new SymbolTable.MethodSymbol(method.name(), className,
                returnType, paramTypes, 1, SymbolTable.DispatchKind.INSTANCE);
        classScope.define(methodSym);
        SymbolTable.ClassSymbol cs = knownClasses.get(className);
        if (cs != null) cs.members().define(methodSym);
        methodScopes.put(method, methodScope);
        methodSymbols.put(method, methodSym);
    }

    private void analyzeConstructorBody(ConstructorDeclarationNode ctor) {
        SymbolTable ctorScope = ctorScopes.get(ctor);
        if (ctorScope == null || ctor.body() == null || ctor.body().isEmpty()) return;
        SymbolTable prevScope = currentScope;
        currentScope = ctorScope;
        analyzeBody(ctor.body(), ctorScope, Type.PrimitiveType.VOID);
        currentScope = prevScope;
    }

    private void analyzeMethodBody(MethodDeclarationNode method) {
        SymbolTable methodScope = methodScopes.get(method);
        if (methodScope == null || method.body() == null || method.body().isEmpty()) return;
        Type returnType = resolveType(method.returnType(), methodScope);
        SymbolTable prevScope = currentScope;
        currentScope = methodScope;
        analyzeBody(method.body(), methodScope, returnType);
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
            defineRecordMembers(rec);
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
            defineInterfaceMembers(iface);
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
        analyzeBody(func.body(), funcScope, returnType);
        currentScope = prevScope;
    }

    private void analyzeBody(List<StatementNode> body, SymbolTable scope, Type returnType) {
        for (StatementNode stmt : body) {
            analyzeStatement(stmt, scope, returnType);
        }
    }

    /**
     * Assignment como STATEMENT (`a = b`, `i = i + 1` no update do for):
     * infere alvo/valor e valida assignability (SEM012) SEM emitir o SEM027
     * (que é reservado para assignment usado como VALOR — bug 12).
     */
    private Type analyzeAssignmentStatement(AssignmentExpr ae, SymbolTable scope) {
        Type valueType = inferType(ae.value(), scope);
        Type targetType = Type.UnknownType.UNKNOWN;
        if (ae.target() instanceof IdentifierExpr ie) {
            SymbolTable.Symbol sym = scope.resolve(ie.name());
            if (sym != null) {
                targetType = sym.type();
                if (diagnostics != null && !Type.isUnknown(targetType)
                        && !Type.isUnknown(valueType)
                        && !isAssignable(valueType, targetType)) {
                    diagnostics.error("", 0, 0, 0,
                            "Type mismatch: cannot assign " + valueType + " to " + targetType,
                            "SEM012");
                }
            } else {
                targetType = inferType(ae.target(), scope);
            }
        } else if (ae.target() != null) {
            targetType = inferType(ae.target(), scope);
        }
        return targetType;
    }

    private void analyzeStatement(StatementNode stmt, SymbolTable scope, Type returnType) {
        switch (stmt) {
            case BlockStmt block -> {
                SymbolTable blockScope = scope.enterScope();
                for (StatementNode s : block.statements()) analyzeStatement(s, blockScope, returnType);
            }
            case TryStmt tryStmt -> {
                // corpos de try/catch/finally eram ignorados pela análise
                // semântica (ex.: `throw 42` dentro de try passava e gerava
                // bytecode inválido). Analisa os três blocos.
                SymbolTable tryScope = scope.enterScope();
                for (StatementNode s : tryStmt.tryBody()) analyzeStatement(s, tryScope, returnType);
                for (CatchClause cc : tryStmt.catchClauses()) {
                    SymbolTable catchScope = scope.enterScope();
                    if (cc.exceptionName() != null) {
                        Type excType = "String".equals(cc.exceptionType()) ? BuiltinTypes.STRING
                                : Type.of(cc.exceptionType());
                        catchScope.define(new SymbolTable.LocalVariableSymbol(cc.exceptionName(), excType, 0));
                    }
                    for (StatementNode s : cc.body()) analyzeStatement(s, catchScope, returnType);
                }
                if (tryStmt.finallyBody() != null) {
                    SymbolTable finScope = scope.enterScope();
                    for (StatementNode s : tryStmt.finallyBody()) analyzeStatement(s, finScope, returnType);
                }
            }
            case VarDeclStmt vds -> {
                Type varType;
                if (vds.type() != null && !vds.type().isEmpty() && !"var".equals(vds.type())) {
                    Type viaImports = qualifyViaImports(vds.type());
                    varType = viaImports != null ? viaImports : Type.of(vds.type());
                } else if (vds.initializer() != null) {
                    varType = inferType(vds.initializer(), scope);
                } else {
                    varType = Type.UnknownType.UNKNOWN;
                }
                // SC5: redeclaração no MESMO escopo é erro
                if (scope.hasLocal(vds.name()) && diagnostics != null) {
                    diagnostics.error("", 0, 0, 0,
                            "variable '" + vds.name() + "' is already defined in this scope",
                            "SEM024");
                }
                if (vds.initializer() != null) inferType(vds.initializer(), scope);
                // SC2: tipo explícito ≠ tipo do inicializador
                if (diagnostics != null && vds.initializer() != null
                        && !varType.equals(Type.UnknownType.UNKNOWN)) {
                    Type initType = inferType(vds.initializer(), scope);
                    if (!initType.equals(Type.UnknownType.UNKNOWN)
                            && !isAssignable(initType, varType)
                            && !(initType instanceof Type.FunctionType)
                            && !(varType instanceof Type.FunctionType)) {
                        diagnostics.error("", 0, 0, 0,
                                "type mismatch: cannot assign " + initType
                                        + " to '" + vds.name() + ": " + varType + "'",
                                "SEM021");
                    }
                }
                scope.define(new SymbolTable.LocalVariableSymbol(vds.name(), varType, 0));
            }
            case ReturnStmt ret -> {
                if (ret.value() != null) {
                    Type valueType = inferType(ret.value(), scope);
                    expressionTypes.put(ret.value(), valueType);
                    if (diagnostics != null && !Type.isUnknown(returnType) && !Type.isVoid(returnType)
                            && !Type.isUnknown(valueType) && !isAssignable(valueType, returnType)) {
                        diagnostics.error("", 0, 0, 0,
                                "Return type mismatch: expected " + returnType + " but got " + valueType, "SEM010");
                    }
                }
            }
            case BreakStmt ignored -> {}
            case ContinueStmt ignored -> {}
            case IfStmt ifStmt -> {
                Type condType = inferType(ifStmt.condition(), scope);
                // Nullability narrowing: if (x != null) { x: T } where x: T?
                SymbolTable ifScope = scope.enterScope();
                if (ifStmt.condition() instanceof BinaryExpr be && "!=".equals(be.operator())
                        && be.left() instanceof IdentifierExpr ie
                        && be.right() instanceof LiteralExpr le && le.kind() == ConcreteLiteralKind.NULL) {
                    SymbolTable.Symbol sym = scope.resolve(ie.name());
                    if (sym != null && sym.type() instanceof Type.NullableType nt) {
                        ifScope.define(new SymbolTable.LocalVariableSymbol(ie.name(), nt.inner(), 0));
                    }
                } else if (ifStmt.condition() instanceof BinaryExpr be2 && "!=".equals(be2.operator())
                        && be2.right() instanceof IdentifierExpr ie2
                        && be2.left() instanceof LiteralExpr le2 && le2.kind() == ConcreteLiteralKind.NULL) {
                    SymbolTable.Symbol sym2 = scope.resolve(ie2.name());
                    if (sym2 != null && sym2.type() instanceof Type.NullableType nt2) {
                        ifScope.define(new SymbolTable.LocalVariableSymbol(ie2.name(), nt2.inner(), 0));
                    }
                }
                analyzeStatement(ifStmt.thenBranch(), ifScope, returnType);
                if (ifStmt.elseBranch() != null) analyzeStatement(ifStmt.elseBranch(), scope, returnType);
            }
            case WhileStmt ws -> {
                inferType(ws.condition(), scope);
                SymbolTable whileScope = scope.enterScope();
                analyzeStatement(ws.body(), whileScope, returnType);
            }
            case DoWhileStmt dws -> {
                SymbolTable doScope = scope.enterScope();
                analyzeStatement(dws.body(), doScope, returnType);
                inferType(dws.condition(), doScope);
            }
            case ForStmt fs -> {
                SymbolTable forScope = scope.enterScope();
                if (fs.init() != null) analyzeStatement(fs.init(), forScope, returnType);
                if (fs.condition() != null) inferType(fs.condition(), forScope);
                analyzeStatement(fs.body(), forScope, returnType);
                if (fs.update() != null) {
                    // `i = i + 1` no update é statement, não valor
                    if (fs.update() instanceof AssignmentExpr ae) {
                        inferType(ae.value(), forScope);
                        if (ae.target() != null) inferType(ae.target(), forScope);
                    } else {
                        inferType(fs.update(), forScope);
                    }
                }
            }
            case ForInStmt fis -> {
                SymbolTable forScope = scope.enterScope();
                Type collType = inferType(fis.collection(), forScope);
                Type elemType = Type.UnknownType.UNKNOWN;
                if (collType instanceof Type.ClassType ct && "List".equals(ct.name()) && !ct.typeArguments().isEmpty()) {
                    elemType = ct.typeArguments().get(0);
                } else if (collType instanceof Type.ArrayType at) {
                    elemType = at.componentType();
                }
                forScope.define(new SymbolTable.LocalVariableSymbol(fis.varName(), elemType, 0));
                analyzeStatement(fis.body(), forScope, returnType);
            }
            case SwitchStmt ss -> {
                inferType(ss.expression(), scope);
                SymbolTable switchScope = scope.enterScope();
                for (SwitchCase sc : ss.cases()) {
                    if (sc.value() instanceof PatternExpr pe) {
                        Type patType = resolveType(pe.typeName(), scope);
                        if (patType == null) patType = Type.UnknownType.UNKNOWN;
                        SymbolTable caseScope = switchScope.enterScope();
                        if (pe.varName() != null) {
                            caseScope.define(new SymbolTable.LocalVariableSymbol(pe.varName(), patType, 0));
                        }
                        if (!pe.fieldVars().isEmpty()) {
                            String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
                            SymbolTable.ClassSymbol cls = getClass(simple);
                            if (cls == null) {
                                // Try via scope resolve
                                Type t2 = resolveType(pe.typeName(), scope);
                                if (t2 instanceof Type.ClassType ct2) cls = getClass(ct2.name());
                            }
                            java.util.List<String> fieldNames = pe.fieldVars();
                            for (int i = 0; i < fieldNames.size(); i++) {
                                String fv = fieldNames.get(i);
                                Type fieldType = Type.UnknownType.UNKNOWN;
                                if (cls != null) {
                                    // Try to find field by index or name
                                    var members = cls.members();
                                    // For records, fields are in order; try to get by index
                                    java.util.List<SymbolTable.Symbol> fields = new java.util.ArrayList<>();
                                    for (var e : members.localSymbols().values()) {
                                        if (e instanceof SymbolTable.FieldSymbol) fields.add(e);
                                    }
                                    // If fieldVars size matches record field count, use positional
                                    if (fields.size() == fieldNames.size() && i < fields.size()) {
                                        fieldType = fields.get(i).type();
                                    } else {
                                        SymbolTable.Symbol sym = members.resolve(fv);
                                        if (sym != null) fieldType = sym.type();
                                        else {
                                            // Try by field name from record declaration
                                            for (AstNode d : currentUnit.declarations()) {
                                                if (d instanceof RecordDeclarationNode rec && rec.name().equals(simple)) {
                                                    if (i < rec.components().size()) {
                                                        fieldType = resolveType(rec.components().get(i).type(), scope);
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                                caseScope.define(new SymbolTable.LocalVariableSymbol(fv, fieldType, 0));
                            }
                        }
                        analyzeBody(sc.body(), caseScope, returnType);
                    } else {
                        inferType(sc.value(), scope);
                        SymbolTable caseScope = switchScope.enterScope();
                        analyzeBody(sc.body(), caseScope, returnType);
                    }
                }
                if (!ss.defaultBody().isEmpty()) {
                    SymbolTable defaultScope = switchScope.enterScope();
                    analyzeBody(ss.defaultBody(), defaultScope, returnType);
                }
            }
            case ExpressionStmt es -> {
                if (es.expression() != null) {
                    // `a = b` como STATEMENT é legítimo: check de assignability
                    // (SEM012) sem o SEM027 (que é só para uso como VALOR —
                    // bug 12). Mesmo helper usado pelo update do for.
                    if (es.expression() instanceof AssignmentExpr ae) {
                        expressionTypes.put(es.expression(),
                                analyzeAssignmentStatement(ae, scope));
                    } else {
                        Type exprType = inferType(es.expression(), scope);
                        expressionTypes.put(es.expression(), exprType);
                    }
                }
            }
            case ThrowStmt ts -> {
                if (ts.expression() != null) {
                    Type t = inferType(ts.expression(), scope);
                    // bug 1: `throw <não-String>` gerava bytecode inválido no
                    // JVM (wrap em RuntimeException assumindo String). Exceções
                    // são Strings em Kof — rejeita com diagnóstico limpo.
                    if (diagnostics != null && t != null && !Type.isUnknown(t)
                            && !BuiltinTypes.isString(t)) {
                        diagnostics.error("", 0, 0, 0,
                                "throw exige uma String (exceções são Strings em Kof),"
                                        + " recebeu " + Type.canonicalPrimitiveName(
                                        t instanceof Type.ClassType ct ? ct.name() : t.toString()),
                                "SEM026");
                    }
                }
            }
            case SpawnStmt ss -> {
                if (ss.expression() != null) inferType(ss.expression(), scope);
            }
            case AssertStmt asrt -> {
                if (asrt.condition() != null) inferType(asrt.condition(), scope);
            }
            default -> {}
        }
    }

    private static boolean isConcurrentHandle(Type t) {
        return t instanceof Type.ClassType ct
                && "kof.concurrent".equals(ct.packageName())
                && "Handle".equals(ct.name());
    }

    Type inferType(ExpressionNode expr, SymbolTable scope) {
        Type cached = expressionTypes.get(expr);
        if (cached != null && !Type.isUnknown(cached)) return cached;
        Type result = inferTypeInternal(expr, scope);
        expressionTypes.put(expr, result);
        return result;
    }

    private Type inferTypeInternal(ExpressionNode expr, SymbolTable scope) {
        return switch (expr) {
            case PatternExpr pe -> {
                Type t = resolveType(pe.typeName(), scope);
                yield t != null ? t : Type.UnknownType.UNKNOWN;
            }
            case QueryDslExpr q -> {
                // Query DSL tipada: Entity.query(db) { ... } -> List<Entity>.
                // where/orderBy referenciam COLUNAS do schema (não variáveis
                // em escopo) — validadas no lowering; não inferir aqui (senão
                // SEM011 "undefined variable" nas colunas). dbArg e limit são
                // expressões reais.
                inferType(q.dbArg(), scope);
                if (q.limit() != null) inferType(q.limit(), scope);
                Type elem = resolveType(q.entityType(), scope);
                yield new Type.ClassType("kof", "List",
                        List.of(elem != null ? elem : Type.UnknownType.UNKNOWN));
            }
            case LiteralExpr lit -> inferLiteralType(lit);
            case IdentifierExpr ie -> {
                SymbolTable.Symbol sym = scope.resolve(ie.name());
                if (sym != null) yield sym.type();
                if ("args".equals(ie.name()) && "main".equals(currentFunctionName)) {
                    yield new Type.ArrayType(BuiltinTypes.STRING);
                }
                // constante de enum não-qualificada (rótulos de case, etc.):
                // Red → Color quando algum enum declara Red
                if (currentUnit != null && !knownClasses.containsKey(ie.name())) {
                    for (AstNode d0 : currentUnit.declarations()) {
                        if (d0 instanceof EnumDeclarationNode en0
                                && en0.constants().contains(ie.name())) {
                            yield new Type.ClassType("", en0.name(), List.of());
                        }
                    }
                }
                if (currentClassName != null && !currentClassName.isEmpty()) {
                    SymbolTable.Symbol fieldSym = resolveInHierarchy(currentClassName, ie.name());
                    if (fieldSym != null) {
                        expressionTypes.put(ie, fieldSym.type());
                        yield fieldSym.type();
                    }
                }
                if (diagnostics != null && !"this".equals(ie.name()) && !"super".equals(ie.name())
                        && !"json".equals(ie.name()) && !"process".equals(ie.name())
                        && !KofWeb.isWebNamespace(ie.name())
                        && !KofConfig.isConfigNamespace(ie.name())
                        && !KofCache.isCacheNamespace(ie.name())
                        && !KofGpu.isGpuNamespace(ie.name())
                        && !KofDb.isDbNamespace(ie.name())
                        && !KofOrm.isOrmNamespace(ie.name())
                        && !KofLog.isLogNamespace(ie.name())
                        && !KofSecurity.isSecurityNamespace(ie.name())
                        && !KofValidation.isValidationNamespace(ie.name())
                        && !KofObservability.isObservabilityNamespace(ie.name())
                        && !KofHttp.isHttpNamespace(ie.name())
                        && !KofMq.isMqNamespace(ie.name())
                        && !KofTime.isTimeNamespace(ie.name())
                        && !KofScheduler.isSchedulerNamespace(ie.name())
                        && !KofTetris.isTetrisNamespace(ie.name())
                        && !KofMedia.isStaticNamespace(ie.name())
                        && !KofUi.isPalette(ie.name()) && !KofUi.isConstructor(ie.name())
                        && !KofUi.isRouterNamespace(ie.name())
                        && !"Theme".equals(ie.name())
                        && !isBuiltinTypeName(ie.name())
                        && !knownClasses.containsKey(ie.name())) {
                    diagnostics.error("", 0, 0, 0,
                            "Undefined variable or type: '" + ie.name() + "'", "SEM011");
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case AssignmentExpr ae -> {
                // bug 12: assignment como VALOR de expressão (`var c = a = b`)
                // gerava bytecode inválido no JVM. Kof não tem assignment como
                // expressão — rejeita com diagnóstico limpo (statements passam
                // pelo ExpressionStmt, que não chega aqui).
                if (diagnostics != null) {
                    diagnostics.error("", 0, 0, 0,
                            "atribuição é um statement, não uma expressão (use '=' em linha própria)",
                            "SEM027");
                }
                Type valueType = inferType(ae.value(), scope);
                Type targetType = Type.UnknownType.UNKNOWN;
                if (ae.target() instanceof IdentifierExpr ie) {
                    SymbolTable.Symbol sym = scope.resolve(ie.name());
                    // SC1: atribuição a variável nunca declarada
                    if (sym == null && diagnostics != null) {
                        boolean hasField = false;
                        if (currentClassName != null) {
                            hasField = resolveInHierarchy(currentClassName, ie.name()) != null;
                        }
                        if (!hasField
                                && !"json".equals(ie.name()) && !"process".equals(ie.name())
                                && !KofWeb.isWebNamespace(ie.name())
                                && !KofConfig.isConfigNamespace(ie.name())
                                && !KofCache.isCacheNamespace(ie.name())
                                && !KofGpu.isGpuNamespace(ie.name())
                                && !KofDb.isDbNamespace(ie.name())
                                && !KofOrm.isOrmNamespace(ie.name())
                                && !KofLog.isLogNamespace(ie.name())
                                && !KofSecurity.isSecurityNamespace(ie.name())
                                && !KofValidation.isValidationNamespace(ie.name())
                                && !KofObservability.isObservabilityNamespace(ie.name())
                                && !KofHttp.isHttpNamespace(ie.name())
                                && !KofMq.isMqNamespace(ie.name())
                                && !KofTime.isTimeNamespace(ie.name())
                                && !KofScheduler.isSchedulerNamespace(ie.name())
                                && !knownClasses.containsKey(ie.name())) {
                            diagnostics.error("", 0, 0, 0,
                                    "undefined variable: '" + ie.name() + "'", "SEM020");
                        }
                    }
                    if (sym != null) {
                        targetType = sym.type();
                        if (diagnostics != null && !Type.isUnknown(targetType) && !Type.isUnknown(valueType)
                                && !isAssignable(valueType, targetType)) {
                            diagnostics.error("", 0, 0, 0,
                                    "Type mismatch: cannot assign " + valueType + " to " + targetType, "SEM012");
                        }
                    }
                } else if (ae.target() instanceof FieldAccessExpr fa) {
                    targetType = inferType(fa, scope);
                }
                yield targetType;
            }
            case BinaryExpr bin -> {
                // Left-associative chains (huge string concatenations in
                // generated UIs, editors) are iterated instead of recursed:
                // deep chains would overflow the compiler's own stack.
                java.util.List<BinaryExpr> chain = new ArrayList<>();
                ExpressionNode cursor = bin;
                while (cursor instanceof BinaryExpr be) {
                    chain.add(be);
                    cursor = be.left();
                }
                Type accType = inferType(cursor, scope);
                for (int ci = chain.size() - 1; ci >= 0; ci--) {
                    BinaryExpr be = chain.get(ci);
                    Type rightType = inferType(be.right(), scope);
                    accType = inferBinaryResultType(be.operator(), accType, rightType);
                }
                yield accType;
            }
            case UnaryExpr ue -> {
                Type operandType = inferType(ue.operand(), scope);
                if ("!".equals(ue.operator())) yield Type.PrimitiveType.BOOL;
                yield operandType;
            }
            case MethodCallExpr mc -> {
                // F10: métodos de instância do handle de process.spawn
                if (mc.receiver() != null) {
                    Type recv = inferType(mc.receiver(), scope);
                    // bug 17: array não tem método get()/set() — a API é o
                    // operador arr[i]. Antes o compilador aceitava e emitia
                    // bytecode inválido (ClassFormatError no JVM, undefined
                    // reference no Native).
                    if (recv instanceof Type.ArrayType && diagnostics != null) {
                        diagnostics.error("", 0, 0, 0,
                                "array não tem método '" + mc.methodName()
                                        + "()'; use o operador arr[i] / arr[i] = v",
                                "SEM028");
                    }
                    if (KofProcess.isHandle(recv)) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(), argTypes);
                        if (hm != null) yield hm.returnType();
                    }
                    // Canais tipados: c.send(v) / c.receive() -> T
                    if (BuiltinTypes.isChannel(recv)) {
                        for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                        if ("send".equals(mc.methodName())) yield Type.PrimitiveType.VOID;
                        if ("receive".equals(mc.methodName())) yield BuiltinTypes.channelElement(recv);
                    }
                    // Map<K,V>: get() devolve V? para valores de referência (ausência = null,
                    // narrowing via if (x != null)); primitivos/UI não representam ausência
                    if (BuiltinTypes.isMap(recv)) {
                        for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                        Type valueType = BuiltinTypes.mapValue(recv);
                        if ("get".equals(mc.methodName())) {
                            yield valueType instanceof Type.ClassType ct
                                    && !KofUi.isUiType(ct) && !KofMedia.isHandleType(ct)
                                    ? new Type.NullableType(valueType) : valueType;
                        }
                        if ("put".equals(mc.methodName()) || "remove".equals(mc.methodName())) yield valueType;
                        if ("size".equals(mc.methodName()) || "length".equals(mc.methodName())
                                || "count".equals(mc.methodName())) yield Type.PrimitiveType.INT;
                        if ("contains".equals(mc.methodName()) || "containsKey".equals(mc.methodName())
                                || "isEmpty".equals(mc.methodName())) yield Type.PrimitiveType.BOOL;
                        if ("clear".equals(mc.methodName())) yield Type.PrimitiveType.VOID;
                        if ("keys".equals(mc.methodName())) yield new Type.ClassType("kof", "List",
                                List.of(BuiltinTypes.mapKey(recv)));
                        if ("values".equals(mc.methodName())) yield new Type.ClassType("kof", "List",
                                List.of(valueType));
                    }
                }
                if (mc.receiver() == null && "channel".equals(mc.methodName())
                        && mc.arguments().isEmpty()) {
                    // channel<T>() -> Channel<T>; sem argumento é Channel<Unknown>
                    Type elemType = mc.typeArguments().isEmpty()
                            ? Type.UnknownType.UNKNOWN
                            : resolveType(mc.typeArguments().get(0), scope);
                    yield new Type.ClassType("kof.concurrent", "Channel", List.of(elemType));
                }
                if (mc.receiver() == null && "listOf".equals(mc.methodName())) {
                    // listOf(...) keeps its element type: List<T> must survive
                    // the whole pipeline (for-in, get, method resolution).
                    Type elemType = Type.UnknownType.UNKNOWN;
                    if (!mc.typeArguments().isEmpty()) {
                        elemType = resolveType(mc.typeArguments().get(0), scope);
                    } else if (!mc.arguments().isEmpty()) {
                        elemType = inferType(mc.arguments().get(0), scope);
                    }
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield new Type.ClassType("kof", "List", List.of(elemType));
                }
                if (mc.receiver() == null && "mapOf".equals(mc.methodName())) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    // pinning no primeiro par (k1, v1, ...) — espelha o emit e o
                    // CompilerDriver.inferExprType; sem isso Map<Unknown,Unknown>
                    // vazava para var x = mapOf(...) e get() devolvia Unknown
                    Type keyType = mc.arguments().isEmpty() ? Type.UnknownType.UNKNOWN
                            : inferType(mc.arguments().get(0), scope);
                    Type valueType = mc.arguments().size() < 2 ? Type.UnknownType.UNKNOWN
                            : inferType(mc.arguments().get(1), scope);
                    yield new Type.ClassType("kof", "Map", List.of(keyType, valueType));
                }
                if (mc.receiver() == null && "setOf".equals(mc.methodName())) {
                    Type elemType = Type.UnknownType.UNKNOWN;
                    if (!mc.arguments().isEmpty()) elemType = inferType(mc.arguments().get(0), scope);
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield new Type.ClassType("kof", "Set", List.of(elemType));
                }
                if (mc.receiver() == null && knownClasses.containsKey(mc.methodName())) {
                    // Implicit construction: ClassName(args) without `new`.
                    // User classes take precedence over builtin helpers with
                    // the same name (e.g. KofUi's Color).
                    SymbolTable.ClassSymbol ctorClass = knownClasses.get(mc.methodName());
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    SymbolTable.ConstructorSymbol ctor = SymbolTable.constructorFor(
                            ctorClass.members(), mc.arguments().size());
                    if (ctor != null) {
                        resolvedMethods.put(mc, new SymbolTable.MethodSymbol("<init>", mc.methodName(),
                                ctor.type(), ctor.parameterTypes(), ctor.accessFlags(), SymbolTable.DispatchKind.STATIC));
                    }
                    yield new Type.ClassType(ctorClass.packageName(), ctorClass.name(), List.of());
                }
                if ("println".equals(mc.methodName()) || "print".equals(mc.methodName())) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.VOID;
                }
                if (mc.receiver() == null && "now".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    yield Type.PrimitiveType.LONG;
                }
                if (mc.receiver() == null && "readLine".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    yield BuiltinTypes.STRING;
                }
                if (mc.receiver() == null && KofWeb.isContextFunction(mc.methodName())
                        && KofWeb.contextCall(mc.methodName(), mc.arguments().size()) != null) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofWeb.contextCall(mc.methodName(), mc.arguments().size()).returnType();
                }
                if ((mc.receiver() == null && KofScheduler.isSchedulerMethod(mc.methodName()))
                        || (mc.receiver() instanceof IdentifierExpr rid2 && KofScheduler.isSchedulerNamespace(rid2.name())
                                && KofScheduler.isSchedulerMethod(mc.methodName()))) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    if ("cancel".equals(mc.methodName())) {
                        // cancel(Handle<T>) é o cancel de concorrência (retorna Bool);
                        // cancel(String taskId) é o do scheduler (VOID). Distingue pelo
                        // tipo do argumento para o + string converter o Bool certo.
                        Type a0 = inferType(mc.arguments().get(0), scope);
                        if (isConcurrentHandle(a0)) {
                            yield Type.PrimitiveType.BOOL;
                        }
                        yield Type.PrimitiveType.VOID;
                    }
                    else yield BuiltinTypes.STRING;
                }
                if (mc.receiver() == null && "transaction".equals(mc.methodName())
                        && mc.arguments().size() == 1) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.VOID;
                }
                if (mc.receiver() == null && "uiNodesLive".equals(mc.methodName())
                        && mc.arguments().isEmpty()) {
                    // kof.ui probe (testes de leak): nº de componentes vivos.
                    yield Type.PrimitiveType.INT;
                }
                if (mc.receiver() == null && "emit".equals(mc.methodName())
                        && mc.arguments().size() == 2) {
                    // Fase 5: dispara evento (bubbling) — args inferidos.
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.VOID;
                }
                if (mc.receiver() == null && "storesLive".equals(mc.methodName())
                        && mc.arguments().isEmpty()) {
                    // kof.ui probe de leak de stores.
                    yield Type.PrimitiveType.INT;
                }
                if (mc.receiver() == null && "readFile".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield BuiltinTypes.STRING;
                }
                if (mc.receiver() == null && "writeFile".equals(mc.methodName()) && mc.arguments().size() == 2) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.INT;
                }
                if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofIo.constructorType(mc.methodName());
                }
                if (mc.receiver() == null && "Color".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 3)) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofUi.COLOR;
                }
                if (mc.receiver() == null && "Window".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.WINDOW;
                }
                if (mc.receiver() == null && "Label".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.LABEL;
                }
                if (mc.receiver() == null && "Button".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofUi.BUTTON;
                }
                if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.INPUT;
                }
                if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
                        && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield "Column".equals(mc.methodName()) ? KofUi.COLUMN : KofUi.ROW;
                }
                if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.VIEW;
                }
                if (mc.receiver() == null && KofUi.isConstructor(mc.methodName())
                        && !mc.arguments().isEmpty() && mc.arguments().size() <= 3) {
                    Type ct = KofUi.constructorType(mc.methodName());
                    if (KofUi.isLayoutType(ct) || KofUi.isStore(ct)) {
                        for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                        yield ct;
                    }
                }
                if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofUi.STYLE;
                }
                if (mc.receiver() == null && "Link".equals(mc.methodName()) && mc.arguments().size() == 2) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofUi.LINK;
                }
                if (mc.receiver() == null && "Image".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.IMAGE;
                }
                if (mc.receiver() == null && "Icon".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofUi.ICON;
                }
                if (mc.receiver() == null && "Font".equals(mc.methodName())
                        && (mc.arguments().size() == 2 || mc.arguments().size() == 3)) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofUi.FONT;
                }
                if (mc.receiver() == null && "Component".equals(mc.methodName())
                        && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.COMPONENT;
                }
                if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
                    KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
                    if (uiCall != null) {
                        for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                        yield uiCall.returnType();
                    }
                }
                if (mc.receiver() instanceof IdentifierExpr ridR && KofUi.isRouterNamespace(ridR.name())) {
                    KofUi.UiCall routerCall = KofUi.staticMethod("Router", mc.methodName(), mc.arguments().size());
                    if (routerCall != null) {
                        for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                        yield routerCall.returnType();
                    }
                }
                if (mc.receiver() != null) {
                    // Nome de CLASSE KOF (de qualquer pacote do modulo) como
                    // receiver para metodo ESTATICO: Desconto.aplicar(c)
                    if (mc.receiver() instanceof IdentifierExpr krid
                            && !isLocalName(krid.name(), scope)
                            && knownClasses.containsKey(krid.name())) {
                        SymbolTable.Symbol km = resolveInHierarchy(krid.name(), mc.methodName());
                        if (km instanceof SymbolTable.MethodSymbol kms
                                && kms.parameterTypes().size() == mc.arguments().size()) {
                            SymbolTable.ClassSymbol kt = knownClasses.get(krid.name());
                            resolvedMethods.put(mc, new SymbolTable.MethodSymbol(
                                    kms.name(), kt.internalName(), kms.returnType(),
                                    kms.parameterTypes(), kms.accessFlags(),
                                    SymbolTable.DispatchKind.STATIC));
                            for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                            checkArgTypes(mc.methodName(), inferArgTypes(mc, scope), kms.parameterTypes());
                            yield kms.returnType();
                        }
                    }
                    // Nome de CLASSE EXTERNA como receiver: Button.inflate(...)
                    // — resolve pelo classpath antes dos namespaces builtin
                    // (Button também é widget do kof.ui; o import decide)
                    if (mc.receiver() instanceof IdentifierExpr rid) {
                        Type q = qualifyViaImports(rid.name());
                        if (q == null && rid.name().contains(".")) {
                            q = qualifiedType(Type.of(rid.name()));
                        }
                        if (q instanceof Type.ClassType qt && isExternal(qt)) {
                            ExternalClasspath.MethodSignature sig = externalTypes.resolveMethod(
                                    qt.internalName(), mc.methodName(), mc.arguments().size());
                            if (sig != null) {
                                List<Type> params = new ArrayList<>();
                                for (String d : sig.parameterDescriptors()) {
                                    params.add(ExternalClasspath.typeFromDescriptor(d));
                                }
                                Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                                resolvedMethods.put(mc, new SymbolTable.MethodSymbol(mc.methodName(),
                                        qt.internalName(), ret, params, 1,
                                        SymbolTable.DispatchKind.STATIC));
                                yield ret;
                            }
                        }
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && "super".equals(rid.name())) {
                        // super.method(args): resolve against the superclass
                        // hierarchy of the enclosing class. The resolved symbol
                        // is intentionally NOT registered in resolvedMethods —
                        // lowering emits a non-virtual SUPER call.
                        String superName = "Object";
                        if (currentClassName != null) {
                            SymbolTable.ClassSymbol self = knownClasses.get(currentClassName);
                            if (self != null && self.superClass() != null && !"Object".equals(self.superClass())) {
                                superName = self.superClass();
                            }
                        }
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        SymbolTable.Symbol m = resolveInHierarchy(superName, mc.methodName());
                        if (m instanceof SymbolTable.MethodSymbol ms) {
                            checkArgTypes(mc.methodName(), argTypes, ms.parameterTypes());
                            yield ms.returnType();
                        }
                        yield Type.UnknownType.UNKNOWN;
                    }
                    Type recvType = inferType(mc.receiver(), scope);
                    // coleções: infere o retorno dos métodos (get → elemento,
                    // size → Int, ...). Sem isso `var f = l.get(0)` de uma
                    // List<FunctionType> inferia Unknown → `f(4)` dava SEM015
                    // (bug 20). Espelha o inferExprType do CompilerDriver.
                    if (BuiltinTypes.isList(recvType)) {
                        Type elemType = Type.UnknownType.UNKNOWN;
                        if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty())
                            elemType = ct.typeArguments().get(0);
                        String mn = mc.methodName();
                        if ("get".equals(mn)) yield elemType;
                        if ("remove".equals(mn)) yield elemType;
                        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                            yield Type.PrimitiveType.INT;
                        if ("contains".equals(mn) || "isEmpty".equals(mn))
                            yield Type.PrimitiveType.BOOL;
                        if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                                || "set".equals(mn) || "clear".equals(mn))
                            yield Type.PrimitiveType.VOID;
                    }
                    if (BuiltinTypes.isMap(recvType)) {
                        Type valueType = Type.UnknownType.UNKNOWN;
                        Type keyType = Type.UnknownType.UNKNOWN;
                        if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) {
                            valueType = ct.typeArguments().get(1);
                            keyType = ct.typeArguments().get(0);
                        }
                        String mn = mc.methodName();
                        if ("get".equals(mn)) yield valueType;
                        if ("remove".equals(mn)) yield valueType;
                        if ("put".equals(mn)) yield valueType;
                        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                            yield Type.PrimitiveType.INT;
                        if ("containsKey".equals(mn) || "contains".equals(mn) || "isEmpty".equals(mn))
                            yield Type.PrimitiveType.BOOL;
                        if ("clear".equals(mn)) yield Type.PrimitiveType.VOID;
                        if ("keys".equals(mn)) yield new Type.ClassType("kof", "List", List.of(keyType));
                        if ("values".equals(mn)) yield new Type.ClassType("kof", "List", List.of(valueType));
                    }
                    if (BuiltinTypes.isSet(recvType)) {
                        Type elemType = Type.UnknownType.UNKNOWN;
                        if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty())
                            elemType = ct.typeArguments().get(0);
                        String mn = mc.methodName();
                        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                            yield Type.PrimitiveType.INT;
                        if ("contains".equals(mn) || "isEmpty".equals(mn))
                            yield Type.PrimitiveType.BOOL;
                        if ("add".equals(mn) || "remove".equals(mn)) yield Type.PrimitiveType.BOOL;
                        if ("clear".equals(mn)) yield Type.PrimitiveType.VOID;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofDb.isDbNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
                        KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
                        if (dbCall != null) {
                            if (typed && !mc.typeArguments().isEmpty()) {
                                yield new Type.ClassType("kof", "List",
                                        List.of(resolveType(mc.typeArguments().get(0), scope)));
                            }
                            yield dbCall.returnType();
                        }
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofLog.isLogNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
                        if (logCall != null) yield logCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofOrm.isOrmNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        boolean typed = !mc.typeArguments().isEmpty();
                        String entityName = typed ? mc.typeArguments().get(0) : null;
                        KofOrm.OrmCall ormCall = KofOrm.staticCall(mc.methodName(), argTypes, typed, entityName);
                        if (ormCall != null) {
                            if ("save".equals(mc.methodName()) && !argTypes.isEmpty()) {
                                yield argTypes.get(argTypes.size() - 1);
                            }
                            if (typed && !mc.typeArguments().isEmpty()) {
                                if ("all".equals(mc.methodName()) || "where".equals(mc.methodName())
                                        || "page".equals(mc.methodName())) {
                                    yield new Type.ClassType("kof", "List",
                                            List.of(resolveType(mc.typeArguments().get(0), scope)));
                                }
                                if ("find".equals(mc.methodName())) {
                                    yield resolveType(mc.typeArguments().get(0), scope);
                                }
                            }
                            yield ormCall.returnType();
                        }
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                            && !isLocalName(rid.name(), scope)) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
                        if (procCall != null) yield procCall.returnType();
                        KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
                        if (exitCall != null) yield exitCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofConfig.isConfigNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
                        if (cfgCall != null) yield cfgCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofCache.isCacheNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofCache.CacheCall cacheCall = KofCache.staticCall(mc.methodName(), argTypes);
                        if (cacheCall != null) yield cacheCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofGpu.isGpuNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofGpu.GpuCall gpuCall = KofGpu.staticCall(mc.methodName(), argTypes);
                        if (gpuCall != null) yield gpuCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofHttp.isHttpNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
                        if (httpCall != null) yield httpCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofMq.isMqNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
                        if (mqCall != null) yield mqCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofTime.isTimeNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
                        if (timeCall != null) yield timeCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofSecurity.isSecurityNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (secCall != null) yield secCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofValidation.isValidationNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (vCall != null) yield vCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofObservability.isObservabilityNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (oCall != null) yield oCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofTetris.isTetrisNamespace(rid.name())) {
                        KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                                mc.arguments().size());
                        if (tetrisCall != null) {
                            for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                            yield tetrisCall.returnType();
                        }
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofMedia.isStaticNamespace(rid.name())) {
                        KofMedia.MediaCall mediaCall = KofMedia.staticCall(rid.name(), mc.methodName(),
                                mc.arguments().size());
                        if (mediaCall != null) {
                            for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                            yield mediaCall.returnType();
                        }
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofWeb.isWebNamespace(rid.name())
                            && "app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        yield KofWeb.APP;
                    }
                    if (KofWeb.isAppType(recvType)) {
                        if ("sse".equals(mc.methodName()) && mc.arguments().size() == 2
                                && mc.arguments().get(1) instanceof LambdaExpr le
                                && le.parameters().isEmpty()) {
                            mc.arguments().set(1, new LambdaExpr(le.position(),
                                    List.of(new FormalParameterNode(le.position(), List.of(),
                                            SSE_CONNECTION_TYPE, "sse")), le.body()));
                        }
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), argTypes);
                        if (webCall != null) yield webCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (KofWeb.isSseConnectionType(recvType)) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofWeb.WebCall sseCall = KofWeb.sseConnectionMethod(mc.methodName(), argTypes);
                        if (sseCall != null) yield sseCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (KofMedia.isImageData(recvType) || KofMedia.isAudio(recvType)) {
                        KofMedia.MediaCall mediaCall = KofMedia.isImageData(recvType)
                                ? KofMedia.imageDataMethod(mc.methodName(), mc.arguments().size())
                                : KofMedia.audioMethod(mc.methodName(), mc.arguments().size());
                        if (mediaCall != null) {
                            for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                            yield mediaCall.returnType();
                        }
                    }
                    if (recvType instanceof Type.FunctionType ft) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        checkArgTypes("function call", argTypes, ft.parameterTypes());
                        yield ft.returnType();
                    }
                    if (recvType instanceof Type.ClassType ct) {
                        SymbolTable.Symbol m = resolveInHierarchy(ct.name(), mc.methodName());
                        if (m instanceof SymbolTable.MethodSymbol ms) {
                            resolvedMethods.put(mc, ms);
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                            checkArgTypes(mc.methodName(), argTypes, ms.parameterTypes());
                            yield ms.returnType();
                        }
                        // receiver de classe EXTERNA (android.* etc.): assinatura
                        // vem do classpath — sem isso o lowering emitiria
                        // invokevirtual com owner vazio
                        if (isExternal(ct)) {
                            ExternalClasspath.MethodSignature sig = externalTypes.resolveMethod(
                                    ct.internalName(), mc.methodName(), mc.arguments().size());
                            if (sig != null) {
                                List<Type> params = new ArrayList<>();
                                for (String d : sig.parameterDescriptors()) {
                                    params.add(ExternalClasspath.typeFromDescriptor(d));
                                }
                                Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                                resolvedMethods.put(mc, new SymbolTable.MethodSymbol(mc.methodName(),
                                        ct.internalName(), ret, params, 1,
                                        SymbolTable.DispatchKind.INSTANCE));
                                yield ret;
                            }
                        }
                        // Nenhum símbolo encontrado — método inexistente (SC3)
                        // Nota: String/Int/Long/Bool são tipos JDK — métodos como
                        // contains/split são resolvidos via lowering direto (JVM) ou
                        // via runtime (Native/JS), não via externalTypes. Evita SEM025
                        // falso-positivo para esses tipos. Object methods (hashCode etc.)
                        // são válidos para qualquer classe.
                        if (diagnostics != null && !BuiltinTypes.isList(ct)
                                && !isObjectMethod(mc.methodName(), mc.arguments().size())) {
                            boolean isKnownReceiver = knownClasses.containsKey(ct.name())
                                    || isExternal(ct);
                            if (isKnownReceiver) {
                                diagnostics.error("", 0, 0, 0,
                                        "Cannot resolve method '" + mc.methodName()
                                                + "' on type '" + ct.name() + "'",
                                        "SEM025");
                            }
                        }
                    }
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null) {
                    SymbolTable.Symbol localSym = scope != null ? scope.resolve(mc.methodName()) : null;
                    if (localSym != null && localSym.type() instanceof Type.FunctionType lft) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        checkArgTypes(mc.methodName(), argTypes, lft.parameterTypes());
                        yield lft.returnType();
                    }
                    if (localSym instanceof SymbolTable.LocalVariableSymbol
                            || localSym instanceof SymbolTable.ParameterSymbol) {
                        // variável DECLARADA sendo chamada como função, mas não é
                        // uma FunctionType. Distingue de "função inexistente"
                        // (SEM015) — ex.: `(s) -> s(1)` com param sem tipo.
                        if (diagnostics != null) {
                            String extra = (localSym.type() instanceof Type.UnknownType)
                                    ? " (sem tipo — declare o tipo do parâmetro da lambda)"
                                    : "";
                            diagnostics.error("", 0, 0, 0,
                                    "variable '" + mc.methodName() + "' is not a function"
                                            + " and cannot be called" + extra,
                                    "SEM015");
                        }
                        for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (currentClassName != null && !currentClassName.isEmpty()) {
                        SymbolTable.Symbol m = resolveInHierarchy(currentClassName, mc.methodName());
                        if (m instanceof SymbolTable.MethodSymbol ms) {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                            checkArgTypes(mc.methodName(), argTypes, ms.parameterTypes());
                            resolvedMethods.put(mc, ms);
                            yield ms.returnType();
                        }
                        // chamada implícita (this) herdada de SUPERCLASSE
                        // EXTERNA: setContentView(...) dentro da Activity Kof
                        SymbolTable.ClassSymbol self = knownClasses.get(currentClassName);
                        String superName = self != null ? self.superClass() : null;
                        if (superName != null && externalTypes != null && !"Object".equals(superName)) {
                            String superInternal = superName.contains(".")
                                    ? superName.replace('.', '/') : superName;
                            ExternalClasspath.MethodSignature sig = externalTypes.resolveMethod(
                                    superInternal, mc.methodName(), mc.arguments().size());
                            if (sig != null) {
                                List<Type> params = new ArrayList<>();
                                for (String d : sig.parameterDescriptors()) {
                                    params.add(ExternalClasspath.typeFromDescriptor(d));
                                }
                                Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                                resolvedMethods.put(mc, new SymbolTable.MethodSymbol(mc.methodName(),
                                        superInternal, ret, params, 1,
                                        SymbolTable.DispatchKind.INSTANCE));
                                yield ret;
                            }
                        }
                    }
                }
                if (mc.receiver() == null
                        && ("super".equals(mc.methodName()) || "this".equals(mc.methodName()))) {
                    // super(args) / this(args): chamadas de construtor —
                    // válidas apenas dentro do corpo de um construtor
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.VOID;
                }
                if (mc.receiver() == null && "__kof_spawn_expr".equals(mc.methodName())) {
                    Type t = inferType(mc.arguments().get(0), scope);
                    yield new Type.ClassType("kof.concurrent", "Handle", List.of(t));
                }
                if (mc.receiver() == null && "cancel".equals(mc.methodName())
                        && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield Type.PrimitiveType.BOOL;
                }
                if (mc.receiver() == null && "cancelled".equals(mc.methodName())
                        && mc.arguments().isEmpty()) {
                    yield Type.PrimitiveType.BOOL;
                }
                if (mc.receiver() == null && "selectAny".equals(mc.methodName())
                        && !mc.arguments().isEmpty()) {
                    Type t0 = Type.UnknownType.UNKNOWN;
                    for (ExpressionNode arg : mc.arguments()) t0 = inferType(arg, scope);
                    if (t0 instanceof Type.ClassType ct
                            && "kof.concurrent".equals(ct.packageName())
                            && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && "awaitTimeout".equals(mc.methodName())
                        && mc.arguments().size() == 2) {
                    Type t0 = inferType(mc.arguments().get(0), scope);
                    inferType(mc.arguments().get(1), scope);
                    if (t0 instanceof Type.ClassType ct
                            && "kof.concurrent".equals(ct.packageName())
                            && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && ("poll".equals(mc.methodName())
                        || "done".equals(mc.methodName()))) {
                    Type t0 = inferType(mc.arguments().get(0), scope);
                    if ("done".equals(mc.methodName())) yield Type.PrimitiveType.BOOL;
                    if (t0 instanceof Type.ClassType ct
                            && "kof.concurrent".equals(ct.packageName())
                            && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && "__kof_await".equals(mc.methodName())) {
                    Type t = inferType(mc.arguments().get(0), scope);
                    if (t instanceof Type.ClassType ct
                            && "kof.concurrent".equals(ct.packageName())
                            && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && currentUnit != null
                        && !"println".equals(mc.methodName()) && !"print".equals(mc.methodName())
                        && !"listOf".equals(mc.methodName()) && !"mapOf".equals(mc.methodName()) && !"setOf".equals(mc.methodName())
                        && !"now".equals(mc.methodName()) && !"readLine".equals(mc.methodName())
                        && !"readFile".equals(mc.methodName()) && !"writeFile".equals(mc.methodName())
                        && !"super".equals(mc.methodName())
                        && !KofIo.isConstructor(mc.methodName())
                        && !KofUi.isConstructor(mc.methodName())
                        && !KofWeb.isContextFunction(mc.methodName())
                        && !KofScheduler.isSchedulerMethod(mc.methodName())
                        && !"transaction".equals(mc.methodName())
                        && !"uiNodesLive".equals(mc.methodName())
                        && !"emit".equals(mc.methodName())
                        && !"storesLive".equals(mc.methodName())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                    boolean found = false;
                    for (AstNode d : currentUnit.declarations()) {
                        if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                            found = true;
                            boolean hasDefaults = fn.parameters().stream()
                                    .anyMatch(p -> p.defaultExpression() != null);
                            if (fn.typeParameters().isEmpty() && (!hasDefaults
                                    || mc.arguments().size() >= fn.parameters().size())) {
                                List<Type> paramTypes = new ArrayList<>();
                                for (FormalParameterNode p : fn.parameters()) paramTypes.add(resolveType(p.type(), scope));
                                checkArgTypes(mc.methodName(), argTypes, paramTypes);
                                // registra o tipo de retorno da função top-level
                                // para o var local inferir (evita Unknown que
                                // quebra a resolução de métodos do receiver)
                                Type fnRet = resolveType(fn.returnType(), scope);
                                if (!Type.isVoid(fnRet)) {
                                    expressionTypes.put(mc, fnRet);
                                    yield fnRet;
                                }
                            }
                            break;
                        }
                    }
                    if (!found && diagnostics != null && !knownClasses.containsKey(mc.methodName())) {
                        diagnostics.error("", 0, 0, 0,
                                "Undefined function: '" + mc.methodName() + "'", "SEM015");
                    }
                }
                SymbolTable.ClassSymbol ctorClass = knownClasses.get(mc.methodName());
                if (ctorClass != null) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    SymbolTable.ConstructorSymbol ctor = SymbolTable.constructorFor(
                            ctorClass.members(), mc.arguments().size());
                    if (ctor != null) {
                        resolvedMethods.put(mc, new SymbolTable.MethodSymbol("<init>", mc.methodName(),
                                ctor.type(), ctor.parameterTypes(), ctor.accessFlags(), SymbolTable.DispatchKind.STATIC));
                    }
                    yield new Type.ClassType(ctorClass.packageName(), ctorClass.name(), List.of());
                }
                for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                // String API: métodos que devolvem Int (indexOf, lastIndexOf,
                // length, compareTo...) — sem isso o var local infere Unknown
                // e o backend emite aload+if_icmp* (VerifyError)
                if (mc.receiver() != null) {
                    Type recv = inferType(mc.receiver(), scope);
                    if (Type.isString(recv) || recv instanceof Type.NullableType nt && Type.isString(nt.inner())) {
                        yield switch (mc.methodName()) {
                            case "indexOf", "lastIndexOf", "length", "size", "count",
                                 "compareTo", "compareToIgnoreCase", "hashCode" -> Type.PrimitiveType.INT;
                            case "isEmpty" -> Type.PrimitiveType.BOOL;
                            default -> Type.UnknownType.UNKNOWN;
                        };
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case NewExpr ne -> {
                SymbolTable.ClassSymbol cs = knownClasses.get(ne.typeName());
                if (cs != null) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : ne.arguments()) {
                        argTypes.add(inferType(arg, scope));
                    }
                    SymbolTable.ConstructorSymbol ctor3 =
                            SymbolTable.constructorFor(cs.members(), ne.arguments().size());
                    if (ctor3 != null) {
                        resolvedConstructors.put(ne, ctor3);
                    } else if (diagnostics != null) {
                        SymbolTable.Symbol anyInit = cs.members().resolve("<init>");
                        if (anyInit instanceof SymbolTable.ConstructorSymbol c) {
                            diagnostics.error("", 0, 0, 0,
                                    "no constructor of '" + ne.typeName() + "' with "
                                            + ne.arguments().size() + " argument(s) (expected "
                                            + c.parameterTypes().size() + ")",
                                    "SEM023");
                        } else if (anyInit instanceof SymbolTable.ConstructorSet set
                                && !set.constructors().isEmpty()) {
                            diagnostics.error("", 0, 0, 0,
                                    "no constructor of '" + ne.typeName() + "' with "
                                            + ne.arguments().size() + " argument(s)",
                                    "SEM023");
                        }
                    }
                    yield new Type.ClassType(cs.packageName(), cs.name(), List.of());
                }
                // classe EXTERNA (android.webkit.WebView etc.): qualifica pelo
                // import e registra o construtor do classpath — sem isso a
                // variável fica Unknown e toda a cadeia de chamadas seguinte
                // perde o tipo
                String qname = ne.typeName();
                if (!qname.contains(".")) {
                    Type viaImport = qualifyViaImports(qname);
                    if (viaImport != null) qname = viaImport instanceof Type.ClassType qt
                            ? qt.packageName() + "." + qt.name() : qname;
                }
                if (qname.contains(".") && externalTypes != null) {
                    String internal = qname.replace('.', '/');
                    if (externalTypes.knows(internal)) {
                        ExternalClasspath.MethodSignature sig =
                                externalTypes.resolveConstructor(internal, ne.arguments().size());
                        if (sig != null) {
                            List<Type> params = new ArrayList<>();
                            for (String d : sig.parameterDescriptors()) {
                                params.add(ExternalClasspath.typeFromDescriptor(d));
                            }
                            resolvedConstructors.put(ne, new SymbolTable.ConstructorSymbol(
                                    internal.substring(internal.lastIndexOf('/') + 1), params, 1));
                        }
                        int lastDot = qname.lastIndexOf('.');
                        yield new Type.ClassType(qname.substring(0, lastDot),
                                qname.substring(lastDot + 1), List.of());
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case FieldAccessExpr fa -> {
                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())
                        && KofUi.paletteColor(fa.fieldName()) != null) {
                    yield KofUi.COLOR;
                }
                Type recvType = inferType(fa.receiver(), scope);
                if (KofUi.isComponent(recvType) && "state".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (KofProcess.isResult(recvType) && KofProcess.isField(fa.fieldName())) {
                    yield KofProcess.fieldType(fa.fieldName());
                }
                if (recvType instanceof Type.ArrayType at && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
                    yield BuiltinTypes.STRING;
                }
                if (recvType instanceof Type.ClassType ct) {
                    SymbolTable.Symbol field = resolveInHierarchy(ct.name(), fa.fieldName());
                    if (field != null) yield field.type();
                    if (isExternal(ct)) {
                        String desc = externalTypes.resolveFieldType(ct.internalName(), fa.fieldName());
                        if (desc != null) {
                            yield ExternalClasspath.typeFromDescriptor(desc);
                        }
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case NewArrayExpr na -> {
                Type elemType = Type.of(na.elementType());
                inferType(na.size(), scope);
                yield new Type.ArrayType(elemType);
            }
            case ArrayAccessExpr aa -> {
                Type recvType = inferType(aa.receiver(), scope);
                inferType(aa.index(), scope);
                if (recvType instanceof Type.ArrayType at) {
                    yield at.componentType();
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case LambdaExpr le -> {
                SymbolTable lambdaScope = scope.enterScope();
                List<Type> paramTypes = new ArrayList<>();
                int idx = 0;
                for (FormalParameterNode p : le.parameters()) {
                    Type paramType = resolveType(p.type(), scope);
                    paramTypes.add(paramType);
                    lambdaScope.define(new SymbolTable.ParameterSymbol(p.name(), paramType, idx));
                    idx++;
                }
                analyzeBody(le.body(), lambdaScope, Type.UnknownType.UNKNOWN);
                Type returnType = Type.UnknownType.UNKNOWN;
                for (StatementNode s : le.body()) {
                    if (s instanceof ReturnStmt rs && rs.value() != null) {
                        returnType = inferType(rs.value(), lambdaScope);
                        break;
                    }
                    if (s instanceof BlockStmt b) {
                        for (StatementNode inner : b.statements()) {
                            if (inner instanceof ReturnStmt rs2 && rs2.value() != null) {
                                returnType = inferType(rs2.value(), lambdaScope);
                                break;
                            }
                        }
                    }
                }
                yield new Type.FunctionType(paramTypes, returnType);
            }
            case IfExpr ie -> {
                Type thenType = inferType(ie.thenExpr(), scope);
                Type elseType = inferType(ie.elseExpr(), scope);
                if (thenType.equals(elseType)) yield thenType;
                if (thenType instanceof Type.PrimitiveType && elseType instanceof Type.PrimitiveType) {
                    yield thenType;
                }
                yield thenType;
            }
            case SwitchExpr se -> {
                Type subjectType = inferType(se.expression(), scope);
                Type result = Type.UnknownType.UNKNOWN;
                int armCount = 0;
                for (SwitchExprCase sc : se.cases()) {
                    SymbolTable caseScope = scope.enterScope();
                    if (sc.value() instanceof PatternExpr pe) {
                        bindPatternVars(pe, caseScope);
                    } else {
                        inferType(sc.value(), scope);
                    }
                    Type t = inferType(sc.body(), caseScope);
                    if (armCount == 0) result = t;
                    armCount++;
                }
                if (se.defaultValue() != null) {
                    SymbolTable defaultScope = scope.enterScope();
                    inferType(se.defaultValue(), defaultScope);
                } else {
                    // exaustividade: sem default, switch sobre enum precisa cobrir
                    // todas as constantes (mesma regra do statement, SEM031).
                    if (subjectType instanceof Type.ClassType sct && sct.packageName().isEmpty()
                            && currentUnit != null) {
                        java.util.Set<String> covered = new java.util.HashSet<>();
                        for (SwitchExprCase sc : se.cases()) {
                            String cn = enumConstantOfExpr(sc.value());
                            if (cn != null) covered.add(cn);
                        }
                        List<String> constants = enumConstantsOf(sct.name());
                        List<String> missing = constants.stream().filter(c -> !covered.contains(c)).toList();
                        if (!missing.isEmpty()) {
                            reportError(se, "switch expressão sobre '" + sct.name()
                                    + "' não cobre: " + String.join(", ", missing)
                                    + " (adicione default ou os casos faltantes)", "SEM032");
                        }
                    } else {
                        reportError(se, "switch expressão exige 'default' (ou exaustividade de enum)", "SEM032");
                    }
                }
                yield result;
            }
            default -> Type.UnknownType.UNKNOWN;
        };
    }

    /** Reporta erro de análise sem posição precisa (estilo dos demais SEM*xx). */
    private void reportError(AstNode n, String message, String code) {
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
    private void bindPatternVars(PatternExpr pe, SymbolTable scope) {
        Type patType = resolveType(pe.typeName(), scope);
        if (patType == null) patType = Type.UnknownType.UNKNOWN;
        if (pe.varName() != null) {
            scope.define(new SymbolTable.LocalVariableSymbol(pe.varName(), patType, 0));
            return;
        }
        if (pe.fieldVars().isEmpty()) return;
        String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
        SymbolTable.ClassSymbol cls = getClass(simple);
        java.util.List<String> fieldNames = pe.fieldVars();
        for (int i = 0; i < fieldNames.size(); i++) {
            String fv = fieldNames.get(i);
            Type fieldType = Type.UnknownType.UNKNOWN;
            if (cls != null) {
                var members = cls.members();
                java.util.List<SymbolTable.Symbol> fields = new java.util.ArrayList<>();
                for (var e : members.localSymbols().values()) {
                    if (e instanceof SymbolTable.FieldSymbol) fields.add(e);
                }
                if (fields.size() == fieldNames.size() && i < fields.size()) {
                    fieldType = fields.get(i).type();
                } else {
                    SymbolTable.Symbol sym = members.resolve(fv);
                    if (sym != null) fieldType = sym.type();
                    else {
                        for (AstNode d : currentUnit.declarations()) {
                            if (d instanceof RecordDeclarationNode rec && rec.name().equals(simple)) {
                                if (i < rec.components().size()) {
                                    fieldType = resolveType(rec.components().get(i).type(), scope);
                                }
                                break;
                            }
                        }
                    }
                }
            }
            scope.define(new SymbolTable.LocalVariableSymbol(fv, fieldType, 0));
        }
    }

    private java.util.List<String> enumConstantsOf(String name) {
        if (name == null || currentUnit == null) return List.of();
        for (AstNode d : currentUnit.declarations()) {
            if (d instanceof EnumDeclarationNode en && en.name().equals(name)) {
                return en.constants();
            }
        }
        return List.of();
    }

    private String enumConstantOfExpr(ExpressionNode e) {
        if (e instanceof FieldAccessExpr fa && fa.receiver() instanceof IdentifierExpr rid) {
            return enumConstantsOf(rid.name()).contains(fa.fieldName()) ? fa.fieldName() : null;
        }
        if (e instanceof LiteralExpr l && l.kind() == ConcreteLiteralKind.STRING) {
            return l.value();
        }
        if (e instanceof IdentifierExpr ie) {
            // não-qualificado: Red quando algum enum declara Red
            if (currentUnit != null) {
                for (AstNode d : currentUnit.declarations()) {
                    if (d instanceof EnumDeclarationNode en && en.constants().contains(ie.name())) {
                        return ie.name();
                    }
                }
            }
            return null;
        }
        return null;
    }

    private Type inferLiteralType(LiteralExpr lit) {
        return switch (lit.kind()) {
            case ConcreteLiteralKind.INT -> Type.PrimitiveType.INT;
            case ConcreteLiteralKind.LONG -> Type.PrimitiveType.LONG;
            case ConcreteLiteralKind.FLOAT -> Type.PrimitiveType.FLOAT;
            case ConcreteLiteralKind.DOUBLE -> Type.PrimitiveType.DOUBLE;
            case ConcreteLiteralKind.STRING -> BuiltinTypes.STRING;
            case ConcreteLiteralKind.BOOLEAN -> Type.PrimitiveType.BOOL;
            case ConcreteLiteralKind.CHAR -> Type.PrimitiveType.CHAR;
            case ConcreteLiteralKind.NULL -> Type.UnknownType.UNKNOWN;
        };
    }

    private Type inferBinaryResultType(String operator, Type left, Type right) {
        if ("==".equals(operator) || "!=".equals(operator) || "<".equals(operator) ||
                ">".equals(operator) || "<=".equals(operator) || ">=".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }

        if ("&&".equals(operator) || "||".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("instanceof".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("as".equals(operator)) {
            return right;
        }
        if ("!".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if (Type.isString(left) || Type.isString(right)) {
            if ("+".equals(operator)) {
                return BuiltinTypes.STRING;
            }
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot apply '" + operator + "' to String and " + right, "SEM001");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.PrimitiveType lp && right instanceof Type.PrimitiveType rp) {
            if ("int".equals(lp.name())) {
                if ("long".equals(rp.name()) || "Long".equals(rp.name())) return Type.PrimitiveType.LONG;
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.INT;
            }
            if ("long".equals(lp.name()) || "Long".equals(lp.name())) {
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.LONG;
            }
            if ("float".equals(lp.name()) || "Float".equals(lp.name())) {
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.FLOAT;
            }
            if ("double".equals(lp.name()) || "Double".equals(lp.name())) {
                return Type.PrimitiveType.DOUBLE;
            }
                if ("bool".equals(lp.name()) || "bool".equals(rp.name())) {
                    if ("+".equals(operator) || "-".equals(operator) || "*".equals(operator) ||
                            "/".equals(operator) || "%".equals(operator)) {
                        if (diagnostics != null) {
                            diagnostics.error("", 0, 0, 0,
                                    "Cannot apply '" + operator + "' to boolean types. Use == or != for comparison.", "SEM002");
                        }
                        return Type.UnknownType.UNKNOWN;
                    }
                }
            return left;
        }
        if (left instanceof Type.ArrayType || right instanceof Type.ArrayType) {
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.UnknownType || right instanceof Type.UnknownType) {
            return Type.UnknownType.UNKNOWN;
        }
        // Aritmética sobre tipo referência (ex.: param de lambda sem anotação
        // → Object) não tem opcode: o emit cairia em IADD sobre referência e a
        // JVM rejeitaria o bytecode (VerifyError). Diagnóstico explícito, nunca
        // fallback silencioso (R6). String + já foi tratado acima.
        if (isArithmeticOp(operator) && (isReferenceType(left) || isReferenceType(right))) {
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot apply '" + operator + "' to non-numeric type "
                                + (isReferenceType(left) ? left : right)
                                + " (declare o tipo do parâmetro, ex.: (x: Int) -> ...)",
                        "SEM001");
            }
            return Type.UnknownType.UNKNOWN;
        }
        return left;
    }

    private static boolean isArithmeticOp(String op) {
        return "+".equals(op) || "-".equals(op) || "*".equals(op)
                || "/".equals(op) || "%".equals(op);
    }

    private static boolean isReferenceType(Type t) {
        return t instanceof Type.ClassType || t instanceof Type.FunctionType;
    }

    private List<Type> inferArgTypes(MethodCallExpr mc, SymbolTable scope) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
        return argTypes;
    }

    private void checkArgTypes(String methodName, List<Type> argTypes, List<Type> paramTypes) {
        if (diagnostics == null || paramTypes.isEmpty() && !argTypes.isEmpty()) return;
        if (argTypes.size() != paramTypes.size()) {
            diagnostics.error("", 0, 0, 0,
                    "Wrong number of arguments for '" + methodName + "': expected "
                            + paramTypes.size() + " but got " + argTypes.size(), "SEM013");
            return;
        }
        for (int i = 0; i < argTypes.size(); i++) {
            if (!Type.isUnknown(argTypes.get(i)) && !Type.isUnknown(paramTypes.get(i))
                    && !isAssignable(argTypes.get(i), paramTypes.get(i))) {
                diagnostics.error("", 0, 0, 0,
                        "Argument " + (i + 1) + " of '" + methodName + "': expected "
                                + paramTypes.get(i) + " but got " + argTypes.get(i), "SEM014");
                return;
            }
        }
    }

    private boolean isAssignable(Type from, Type to) {
        if (from == null || to == null) return true;
        if (Type.isUnknown(from) || Type.isUnknown(to)) return true;
        if (from instanceof Type.TypeVariable || to instanceof Type.TypeVariable) return true;
        if (from instanceof Type.NullableType fn) {
            if (to instanceof Type.NullableType tn) return isAssignable(fn.inner(), tn.inner());
            return false;
        }
        if (to instanceof Type.NullableType tn) {
            if (from instanceof Type.NullableType) return isAssignable(((Type.NullableType)from).inner(), tn.inner());
            return isAssignable(from, tn.inner());
        }
        if (from.equals(to)) return true;
        if (from instanceof Type.PrimitiveType fp && to instanceof Type.PrimitiveType tp) {
            // double → float: o lowering emite D2F; sem isso literais
            // decimais (1000.0) não atribuem a campos Float
            if ("double".equals(fp.name()) && "float".equals(tp.name())) return true;
            int fw = primitiveWidth(fp);
            int tw = primitiveWidth(tp);
            return fw <= tw;
        }
        if (from instanceof Type.FunctionType && to instanceof Type.ClassType) {
            // lambda → interface funcional externa (SAM conversion): a
            // compatibilidade real (aridade/tipos) é validada na emissão
            return true;
        }
        if (from instanceof Type.PrimitiveType && to instanceof Type.ClassType ct
                && "Object".equals(ct.name()) && "java.lang".equals(ct.packageName())) {
            // bug 15: primitivo → Object (auto-boxing no emit) — `Object n = 42`
            return true;
        }
        if (from instanceof Type.PrimitiveType fp
                && "double".equals(fp.name())
                && to instanceof Type.PrimitiveType tp
                && "float".equals(tp.name())) {
            // double → float: o lowering emite D2F; sem isso literais
            // decimais (1000.0) não atribuem a campos Float
            return true;
        }
        if (to instanceof Type.ClassType) {
            return from instanceof Type.ClassType;
        }
        return false;
    }

    private int primitiveWidth(Type.PrimitiveType pt) {
        return switch (pt.name()) {
            case "bool", "Bool" -> 0;
            case "char", "Char" -> 1;
            case "int", "Int", "byte", "short" -> 2;
            case "long", "Long" -> 3;
            case "float", "Float" -> 4;
            case "double", "Double" -> 5;
            default -> 2;
        };
    }

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
