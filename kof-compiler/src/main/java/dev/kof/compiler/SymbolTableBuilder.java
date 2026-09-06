package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Construção das tables de símbolos (pre-declaração de tipos e definição
 * de membros), extraída do SemanticAnalyzer (REFACTOR-500 fase 6).
 */
final class SymbolTableBuilder {

    private SymbolTableBuilder() {}

    static void preDeclareType(SemanticAnalyzer sa, AstNode decl) {
        if (decl instanceof ClassDeclarationNode cls) {
            SymbolTable members = new SymbolTable();
            // superclasse qualificada pelos imports: "extends Activity" com
            // "import android.app.Activity" vira "android.app.Activity" —
            // sem isso a resolução externa (classpath) nunca encontra a classe
            String superQualified = cls.superClass();
            if (superQualified != null && !"Object".equals(superQualified)) {
                Type viaImports = MemberResolver.qualifyViaImports(sa.unit(), superQualified);
                if (viaImports instanceof Type.ClassType qt) {
                    superQualified = qt.packageName() + "." + qt.name();
                }
            }
            String declPkg = sa.packageOf(cls);
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(cls.name(), declPkg,
                    cls.superClass() != null ? superQualified : "Object",
                    cls.interfaces(), members);
            sa.allClasses().put(cls.name(), sym);
            sa.currentScope().define(sym);
        } else if (decl instanceof RecordDeclarationNode rec) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(rec.name(), sa.packageOf(rec),
                    "Record", rec.interfaces(), members);
            sa.allClasses().put(rec.name(), sym);
            sa.currentScope().define(sym);
        } else if (decl instanceof EntityDeclarationNode ent) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(ent.name(), sa.packageOf(ent),
                    "Record", List.of(), members);
            sa.allClasses().put(ent.name(), sym);
            sa.currentScope().define(sym);
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
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(en.name(), sa.packageOf(en),
                    "Object", List.of(), members);
            sa.allClasses().put(en.name(), sym);
            sa.currentScope().define(sym);
        } else if (decl instanceof InterfaceDeclarationNode iface) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(iface.name(), sa.packageOf(iface),
                    "Object", iface.interfaces(), members);
            sa.allClasses().put(iface.name(), sym);
            sa.interfaceNames().add(iface.name());
            sa.currentScope().define(sym);
        }
    }

    static void defineMembers(SemanticAnalyzer sa, AstNode decl) {
        switch (decl) {
            case ClassDeclarationNode cls -> defineClassMembers(sa, cls);
            case RecordDeclarationNode rec -> defineRecordMembers(sa, rec);
            case EntityDeclarationNode ent -> defineEntityMembers(sa, ent);
            case InterfaceDeclarationNode iface -> defineInterfaceMembers(sa, iface);
            case EnumDeclarationNode en -> { }
            default -> {}
        }
    }

    static void defineClassMembers(SemanticAnalyzer sa, ClassDeclarationNode cls) {
        if (sa.classMemberScopes().containsKey(cls.name())) return;
        SymbolTable.ClassSymbol classSym = sa.allClasses().get(cls.name());
        SymbolTable classScope = classSym.members().enterScope();
        sa.classMemberScopes().put(cls.name(), classScope);
        for (String tp : cls.typeParameters()) {
            classScope.define(new SymbolTable.TypeParameterSymbol(tp));
        }
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) {
                Type fieldType = MemberResolver.resolveType(sa, field.type(), classScope);
                int flags = field.modifiers().contains("static") ? AccessFlags.STATIC : 0;
                SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(field.name(), fieldType, flags, cls.name());
                classSym.members().define(fs);
                classScope.define(fs);
            }
        }
        boolean hasCtor = false;
        for (AstNode member : cls.members()) {
            if (member instanceof ConstructorDeclarationNode ctor) {
                defineConstructorSymbol(sa, ctor, cls.name(), classScope);
                hasCtor = true;
            } else if (member instanceof MethodDeclarationNode method) {
                defineMethodSymbol(sa, method, cls.name(), classScope);
            }
        }
        if (!hasCtor) {
            classScope.define(new SymbolTable.ConstructorSymbol(cls.name(), List.of(), 1));
        }
    }

    static void defineRecordMembers(SemanticAnalyzer sa, RecordDeclarationNode rec) {
        if (sa.classMemberScopes().containsKey(rec.name())) return;
        SymbolTable.ClassSymbol classSym = sa.allClasses().get(rec.name());
        SymbolTable classScope = classSym.members().enterScope();
        sa.classMemberScopes().put(rec.name(), classScope);
        List<String> typeParams = rec.typeParameters() == null ? List.of() : rec.typeParameters();
        for (String tp : typeParams) {
            classScope.define(new SymbolTable.TypeParameterSymbol(tp));
        }
        List<Type> compTypes = new ArrayList<>();
        for (RecordComponentNode comp : rec.components()) {
            Type compType = MemberResolver.resolveType(sa, comp.type(), classScope);
            compTypes.add(compType);
            SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(comp.name(), compType, 0, rec.name());
            classSym.members().define(fs);
            classScope.define(fs);
        }
        SymbolTable.ConstructorSymbol ctorSym = new SymbolTable.ConstructorSymbol(rec.name(), compTypes, 1);
        classSym.members().define(ctorSym);
        classScope.define(ctorSym);
        for (RecordComponentNode comp : rec.components()) {
            Type compType = MemberResolver.resolveType(sa, comp.type(), classScope);
            SymbolTable.MethodSymbol ms = new SymbolTable.MethodSymbol(comp.name(), rec.name(),
                    compType, List.of(), 1, SymbolTable.DispatchKind.INSTANCE);
            classSym.members().define(ms);
            classScope.define(ms);
        }
        for (AstNode member : rec.members()) {
            if (member instanceof MethodDeclarationNode method) {
                defineMethodSymbol(sa, method, rec.name(), classScope);
            }
        }
    }

    static void defineEntityMembers(SemanticAnalyzer sa, EntityDeclarationNode ent) {
        List<RecordComponentNode> components = new ArrayList<>();
        for (EntityFieldNode f : ent.fields()) {
            components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
        }
        RecordDeclarationNode synthetic = new RecordDeclarationNode(ent.position(), ent.name(), ent.modifiers(),
                null, List.of(), components, List.of());
        // preDeclare já criou classSym para ent; reutiliza
        defineRecordMembers(sa, synthetic);
    }

    static void defineInterfaceMembers(SemanticAnalyzer sa, InterfaceDeclarationNode iface) {
        if (sa.classMemberScopes().containsKey(iface.name())) return;
        SymbolTable.ClassSymbol classSym = sa.allClasses().get(iface.name());
        SymbolTable classScope = classSym.members().enterScope();
        sa.classMemberScopes().put(iface.name(), classScope);
        for (AstNode member : iface.members()) {
            if (member instanceof MethodDeclarationNode method) {
                Type returnType = MemberResolver.resolveType(sa, method.returnType(), classScope);
                List<Type> paramTypes = new ArrayList<>();
                for (FormalParameterNode p : method.parameters()) paramTypes.add(Type.of(p.type()));
                SymbolTable.MethodSymbol ms = new SymbolTable.MethodSymbol(method.name(), iface.name(),
                        returnType, paramTypes, 0, SymbolTable.DispatchKind.INSTANCE);
                classScope.define(ms);
                classSym.members().define(ms);
            }
        }
    }

    static void defineConstructorSymbol(SemanticAnalyzer sa, ConstructorDeclarationNode ctor,
                                        String className, SymbolTable classScope) {
        List<Type> paramTypes = new ArrayList<>();
        SymbolTable ctorScope = classScope.enterScope();
        ctorScope.define(new SymbolTable.ParameterSymbol("this",
                new Type.ClassType(sa.currentPackage(), className, List.of()), 0));
        int idx = 1;
        for (FormalParameterNode param : ctor.parameters()) {
            Type paramType = MemberResolver.resolveType(sa, param.type(), ctorScope);
            paramTypes.add(paramType);
            ctorScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        SymbolTable.ConstructorSymbol ctorSym = new SymbolTable.ConstructorSymbol(className, paramTypes, 1);
        classScope.define(ctorSym);
        SymbolTable.ClassSymbol cs = sa.allClasses().get(className);
        if (cs != null) cs.members().define(ctorSym);
        sa.ctorScopes().put(ctor, ctorScope);
    }

    static void defineMethodSymbol(SemanticAnalyzer sa, MethodDeclarationNode method,
                                   String className, SymbolTable classScope) {
        SymbolTable methodScope = classScope.enterScope();
        methodScope.define(new SymbolTable.ParameterSymbol("this",
                new Type.ClassType(sa.currentPackage(), className, List.of()), 0));
        Type returnType = MemberResolver.resolveType(sa, method.returnType(), methodScope);
        List<Type> paramTypes = new ArrayList<>();
        int idx = 1;
        for (FormalParameterNode param : method.parameters()) {
            Type paramType = MemberResolver.resolveType(sa, param.type(), methodScope);
            paramTypes.add(paramType);
            methodScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        SymbolTable.MethodSymbol methodSym = new SymbolTable.MethodSymbol(method.name(), className,
                returnType, paramTypes, 1, SymbolTable.DispatchKind.INSTANCE);
        classScope.define(methodSym);
        SymbolTable.ClassSymbol cs = sa.allClasses().get(className);
        if (cs != null) cs.members().define(methodSym);
        sa.methodScopes().put(method, methodScope);
        sa.methodSymbols().put(method, methodSym);
    }
}
