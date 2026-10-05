package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Construção das tables de símbolos (pre-declaração de tipos e definição
 * de membros), extraída do SemanticAnalyzer (REFACTOR-500 fase 6).
 */
public final class SymbolTableBuilder {

    private SymbolTableBuilder() {}

    static void preDeclareType(SemanticAnalyzer sa, AstNode decl) {
        if (decl instanceof ClassDeclarationNode cls) {
            SymbolTable members = new SymbolTable();
            // superclasse qualificada pelos imports: "extends Activity" com
            // "import android.app.Activity" vira "android.app.Activity" —
            // sem isso a resolução externa (classpath) nunca encontra a classe.
            // Para classes genéricas ("Container<String>"), o nome base da superclasse
            // é extraído ("Container") antes da resolução e herança (Issue #246).
            String superQualified = cls.superClass();
            if (superQualified != null && superQualified.contains("<")) {
                superQualified = superQualified.substring(0, superQualified.indexOf('<')).trim();
            }
            superQualified = HeritageQualifier.qualify(sa.unit(), sa, superQualified);
            String declPkg = sa.packageOf(cls);
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(cls.name(), declPkg,
                    cls.superClass() != null ? superQualified : "Object",
                    cls.interfaces().stream().map(n -> HeritageQualifier.qualifyInterface(sa.unit(), sa, n)).toList(), members);
            sa.putClass(cls.name(), sym);
            sa.registerSymbol(decl, sym);
            sa.currentScope().define(sym);
        } else if (decl instanceof RecordDeclarationNode rec) {
            SymbolTable members = new SymbolTable();
            String superQualified = HeritageQualifier.qualify(sa.unit(), sa, rec.superClass());
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(rec.name(), sa.packageOf(rec),
                    rec.superClass() != null ? superQualified : "Record",
                    rec.interfaces().stream().map(n -> HeritageQualifier.qualifyInterface(sa.unit(), sa, n)).toList(), members);
            sa.putClass(rec.name(), sym);
            sa.registerSymbol(decl, sym);
            sa.currentScope().define(sym);
        } else if (decl instanceof EntityDeclarationNode ent) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(ent.name(), sa.packageOf(ent),
                    "Record", List.of(), members);
            sa.putClass(ent.name(), sym);
            sa.registerSymbol(decl, sym);
            sa.currentScope().define(sym);
        } else if (decl instanceof EnumDeclarationNode en) {
            SymbolTable members = new SymbolTable();
            Type self = new Type.ClassType(sa.packageOf(en), en.name(), List.of()); // #445: pkg real (era "" raiz — quebrava arquivo importado)
            members.define(new SymbolTable.MethodSymbol("values", en.name(),
                    new Type.ClassType("kof", "List", List.of(self)), List.of(),
                    AccessFlags.STATIC, SymbolTable.DispatchKind.STATIC));
            members.define(new SymbolTable.MethodSymbol("valueOf", en.name(),
                    self, List.of(BuiltinTypes.STRING),
                    AccessFlags.STATIC, SymbolTable.DispatchKind.STATIC));
            members.define(new SymbolTable.MethodSymbol("name", en.name(),
                    BuiltinTypes.STRING, List.of(),
                    0, SymbolTable.DispatchKind.INSTANCE));
            members.define(new SymbolTable.MethodSymbol("toString", en.name(),
                    BuiltinTypes.STRING, List.of(),
                    0, SymbolTable.DispatchKind.INSTANCE));
            members.define(new SymbolTable.MethodSymbol("ordinal", en.name(),
                    Type.PrimitiveType.INT, List.of(),
                    0, SymbolTable.DispatchKind.INSTANCE));
            members.define(new SymbolTable.MethodSymbol("compareTo", en.name(),
                    Type.PrimitiveType.INT, List.of(self),
                    0, SymbolTable.DispatchKind.INSTANCE));
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(en.name(), sa.packageOf(en),
                    "Enum", List.of(), members);
            sa.putClass(en.name(), sym);
            sa.registerSymbol(decl, sym);
            sa.currentScope().define(sym);
        } else if (decl instanceof InterfaceDeclarationNode iface) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(iface.name(), sa.packageOf(iface),
                    "Object", iface.interfaces().stream().map(n -> HeritageQualifier.qualifyInterface(sa.unit(), sa, n)).toList(), members);
            sa.putClass(iface.name(), sym);
            sa.registerSymbol(decl, sym);
            sa.addInterface(iface.name());
            sa.currentScope().define(sym);
        }
        // SG-017 (SEM041): registra classes abstratas — `new A()` vira erro.
        if (decl instanceof ClassDeclarationNode cls
                && cls.modifiers().contains("abstract")) {
            sa.addAbstractClass(cls.name());
        }
        // #339 (SEM070): registra classes `final` — `class D extends F` vira erro.
        if (decl instanceof ClassDeclarationNode cls
                && cls.modifiers().contains("final")) {
            sa.addFinalClass(cls.name());
        }
        // #470 (SEM070): records — inclusive os SINTETICOS do parser
        // (`class Animal(String name)` -> RecordDeclarationNode, TypeDeclarations
        // :105-116) — sao gravados ACC_FINAL no bytecode. Sem registra-los
        // aqui, `class Dog(...) extends Animal` passava limpo e morria no load
        // com IncompatibleClassChangeError — R6 exige diagnostico no compile.
        if (decl instanceof RecordDeclarationNode rec) {
            sa.addFinalClass(rec.name());
        }
        // X5.1 (D-X5-SURFACE): registra os tipos `sealed` (class/record/
        // interface) com a unidade de compilação que os declara — o conjunto
        // de subtipos é fechado e SEM080 barra um subtipo de fora.
        if (decl instanceof ClassDeclarationNode sc && sc.modifiers().contains("sealed")) {
            sa.addSealedType(sc.name(), sc.position().file());
        } else if (decl instanceof RecordDeclarationNode sr && sr.modifiers().contains("sealed")) {
            sa.addSealedType(sr.name(), sr.position().file());
        } else if (decl instanceof InterfaceDeclarationNode si && si.modifiers().contains("sealed")) {
            sa.addSealedType(si.name(), si.position().file());
        }
    }

    static void defineMembers(SemanticAnalyzer sa, AstNode decl) {
        switch (decl) {
            case ClassDeclarationNode cls -> defineClassMembers(sa, cls);
            case RecordDeclarationNode rec -> defineRecordMembers(sa, rec);
            case EntityDeclarationNode ent -> defineEntityMembers(sa, ent);
            case InterfaceDeclarationNode iface -> defineInterfaceMembers(sa, iface);
            case EnumDeclarationNode _ -> { }
            default -> {}
        }
    }

    static void defineClassMembers(SemanticAnalyzer sa, ClassDeclarationNode cls) {
        // #639 face 2: guarda por DECLARAÇÃO (não por nome simples) — duas
        // packages com o mesmo nome precisam cada uma dos SEUS membros.
        if (sa.membersDefined(cls)) return;
        SymbolTable.ClassSymbol classSym = sa.symbolForDecl(cls);
        if (classSym == null) classSym = sa.allClasses().get(cls.name());
        if (classSym == null) return;
        sa.markMembersDefined(cls);
        SymbolTable classScope = classSym.members().enterScope();
        sa.putClassMemberScope(cls.name(), classScope);
        sa.registerTypeParameters(cls.name(), cls.typeParameters()); // X5.3
        for (String tp : cls.typeParameters()) {
            // §355: entry pode trazer bound ("T: Animal") — nome limpo +
            // bound no symbol, para o TypeVariable do escopo apagar certo.
            classScope.define(TypeParams.symbol(tp, sa));
        }
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) {
                Type fieldType = MemberResolver.resolveType(sa, field.type(), classScope);
                // #331/#327 (espelha SG-013 dos metodos, :361): private/
                // protected/FINAL precisam chegar ao simbolo — antes so
                // STATIC era preservado e os cheques de acesso/atribuicao de
                // campo nao tinham informacao (IllegalAccessError
                // silencioso no runtime).
                int flags = AccessFlags.PUBLIC;
                if (field.modifiers().contains("private")) flags |= AccessFlags.PRIVATE;
                else if (field.modifiers().contains("protected")) flags |= AccessFlags.PROTECTED;
                if (field.modifiers().contains("static")) flags |= AccessFlags.STATIC;
                if (field.modifiers().contains("final")) flags |= AccessFlags.FINAL;
                SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(field.name(), fieldType, flags, cls.name());
                classSym.members().define(fs);
                classScope.define(fs);
            }
        }
        boolean hasCtor = false;
        for (AstNode member : cls.members()) {
            if (member instanceof ConstructorDeclarationNode ctor) {
                defineConstructorSymbol(sa, ctor, classSym, classScope);
                hasCtor = true;
            } else if (member instanceof MethodDeclarationNode method) {
                defineMethodSymbol(sa, method, classSym, classScope);
            }
        }
        if (!hasCtor) {
            SymbolTable.ConstructorSymbol defaultCtor = new SymbolTable.ConstructorSymbol(cls.name(), List.of(), 1);
            classScope.define(defaultCtor);
            classSym.members().define(defaultCtor);
        }
        checkMemberSignatureDupes(sa, cls.members(), cls.name(), classScope, "class");
    }

    /**
     * #264 — dois membros de classe com MESMA assinatura JVM (nome + tipos de
     * parametro + tipo de retorno apagados a descritor) — incluindo um estatico
     * e um de instancia — produzem dois metodos com o par (name, descriptor)
     * identico no mesmo .class: JVMS §4.6 proibe, a classe NAO carrega
     * (ClassFormatError: Duplicate method name) — o compilador aceitava em
     * silencio (R6 violado). Espelha o SEM047 de funcao top-level (SG-011B,
     * ja ratificado): DUPLICATA EXATA e erro; sobrecarga por assinatura
     * DIFERENTE e permitida (§131). Staticness NAO faz parte do descritor JVM
     * (e nao do "method signature" do JVMS) — `static go(Int):Int` +
     * `go(Int):Int` colidem. Chave derivada dos TIPOS RESOLVIDOS (mesma chave
     * ⇒ mesmo descritor: o mapper e funcao do Type; nomes prefixados/retornos
     * diferentes continuam passando, medido). Construtores: <init>(params)V.
     */
    static void checkMemberSignatureDupes(SemanticAnalyzer sa, List<? extends AstNode> members,
                                          String className, SymbolTable classScope, String kind) {
        DiagnosticCollector dc = sa.diagnostics();
        if (dc == null) return;
        Map<String, SourcePosition> seen = new java.util.LinkedHashMap<>();
        for (AstNode member : members) {
            String key;
            String shown;
            SourcePosition pos;
            if (member instanceof FieldDeclarationNode fd) {
                // #294 (forma ESTRETA, 18/09): um campo por NOME por classe,
                // qualquer tipo — regra do javac. O guard original usava
                // (nome, descritor) porque a JVMS 4.5 so proibe entradas
                // duplicadas, mas as 4 faces do par "legal-JVM" `static Int a`
                // + `Long a` foram medidas e NENHUM target jamais o executou
                // (VerifyError / ICE JS / toString da classe no script /
                // ponteiro-lixo silencioso no native, 289) — logo a chave por
                // nome e aditiva sobre o que funciona e fecha a ferida na raiz.
                key = "F:" + fd.name();
                shown = "field '" + fd.name() + "' of type '" + fd.type() + "'";
                pos = fd.position();
                SourcePosition prevF = seen.putIfAbsent(key, pos);
                if (prevF != null) {
                    dc.error(pos != null ? pos.file() : "", pos != null ? pos.line() : 0,
                            pos != null ? pos.column() : 0, 0,
                            "'" + shown + "' is already defined in class '" + className
                                    + "' at line " + prevF.line()
                                    + " — a class cannot declare two fields with the same name"
                                    + " (static or not, any type); rename one",
                            "SEM076");
                }
                continue;
            }
            if (member instanceof MethodDeclarationNode m) {
                SymbolTable.MethodSymbol ms = sa.methodSymbols().get(m);
                if (ms == null) continue;
                key = signatureKey(m.name(), ms.parameterTypes(), ms.returnType());
                shown = m.name() + "(" + joinParamTypes(m.parameters()) + ")";
                pos = m.position();
            } else if (member instanceof ConstructorDeclarationNode c) {
                List<Type> pt = new ArrayList<>();
                for (FormalParameterNode p : c.parameters()) {
                    pt.add(MemberResolver.resolveType(sa, p.type(), classScope));
                }
                key = signatureKey("<init>", pt, Type.PrimitiveType.VOID);
                shown = "constructor " + className + "(" + joinParamTypes(c.parameters()) + ")";
                pos = c.position();
            } else {
                continue;
            }
            SourcePosition prev = seen.putIfAbsent(key, pos);
            if (prev != null) {
                dc.error(pos != null ? pos.file() : "", pos != null ? pos.line() : 0,
                        pos != null ? pos.column() : 0, 0,
                        "'" + shown + "' is already defined in " + kind + " '" + className + "' at line "
                                + prev.line() + " — same JVM descriptor; overload requires a DIFFERENT"
                                + " parameter or return type (static and instance do not differ here)",
                        "SEM061");
            }
        }
    }

    private static String joinParamTypes(List<FormalParameterNode> ps) {
        StringBuilder sb = new StringBuilder();
        for (FormalParameterNode p : ps) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.type() != null ? p.type() : "?");
        }
        return sb.toString();
    }

    private static String signatureKey(String name, List<Type> params, Type ret) {
        StringBuilder sb = new StringBuilder(name).append('(');
        for (Type t : params) sb.append(typeKey(t));
        return sb.append(')').append(typeKey(ret)).toString();
    }

    /** Chave de APAGAMENTO (erasure), espelhando o descritor JVM: argumentos de
     *  tipo caem ({@code List<Int>} × {@code List<String>} → APAGADOS para o
     *  mesmo ArrayList — javac rejeita como "same erasure", e a JVM tambem:
     *  mesmo descritor → ClassFormatError, medido), {@code T} vira Object.
     *  Retorno incluido (faz parte do descritor). */
    private static String typeKey(Type t) {
        if (t == null) return "?";
        return switch (t) {
            case Type.PrimitiveType p -> "P:" + p.name();
            case Type.ClassType c -> "java.lang".equals(c.packageName()) && "Object".equals(c.name())
                    ? "Ljava/lang/Object;" : "C:" + c.packageName() + "." + c.name();
            case Type.ArrayType a -> "[" + typeKey(a.componentType());
            // Int? apaga para java/lang/Integer (boxed — c0cf805e), NAO para I:
            // go(Int) e go(Int?) tem descritores DIFERENTES (mediado no tip:
            // (I)I × (Ljava/lang/Integer;)I) — nao pode casar na chave.
            case Type.NullableType n -> n.inner() instanceof Type.PrimitiveType p
                    ? "B:" + p.name() : typeKey(n.inner());
            case Type.TypeVariable _ -> "Ljava/lang/Object;";
            case Type.FunctionType f -> f.className() != null ? "C:" + f.className() : "Ljava/lang/Object;";
            case Type.WildcardType _ -> "Ljava/lang/Object;";
            default -> "O:" + t;
        };
    }

    static void defineRecordMembers(SemanticAnalyzer sa, RecordDeclarationNode rec) {
        if (sa.membersDefined(rec)) return;
        SymbolTable.ClassSymbol classSym = sa.symbolForDecl(rec);
        if (classSym == null) classSym = sa.allClasses().get(rec.name());
        if (classSym == null) return;
        sa.markMembersDefined(rec);
        SymbolTable classScope = classSym.members().enterScope();
        sa.putClassMemberScope(rec.name(), classScope);
        List<String> typeParams = rec.typeParameters() == null ? List.of() : rec.typeParameters();
        sa.registerTypeParameters(rec.name(), typeParams); // X5.3
        for (String tp : typeParams) {
            classScope.define(TypeParams.symbol(tp, sa)); // §355
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
            if (member instanceof FieldDeclarationNode field) {
                Type fieldType = MemberResolver.resolveType(sa, field.type(), classScope);
                int flags = field.modifiers().contains("static") ? AccessFlags.STATIC : 0;
                SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(field.name(), fieldType, flags, rec.name());
                classSym.members().define(fs);
                classScope.define(fs);
                SymbolTable.MethodSymbol ms = new SymbolTable.MethodSymbol(field.name(), rec.name(),
                        fieldType, List.of(), 1, SymbolTable.DispatchKind.INSTANCE);
                classSym.members().define(ms);
                classScope.define(ms);
            }
        }
        for (AstNode member : rec.members()) {
            if (member instanceof MethodDeclarationNode method) {
                defineMethodSymbol(sa, method, classSym, classScope);
            }
        }
        checkMemberSignatureDupes(sa, rec.members(), rec.name(), classScope, "record");
    }

    static void defineEntityMembers(SemanticAnalyzer sa, EntityDeclarationNode ent) {
        List<RecordComponentNode> components = new ArrayList<>();
        for (EntityFieldNode f : ent.fields()) {
            components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
        }
        RecordDeclarationNode synthetic = new RecordDeclarationNode(ent.position(), ent.name(), ent.modifiers(),
                null, List.of(), components, List.of());
        // preDeclare já criou classSym para ent; reutiliza. #639 face 2: mapeia
        // o nó SINTÉTICO para o símbolo da entity (a guarda é por declaração).
        sa.registerSymbol(synthetic, sa.symbolForDecl(ent) != null
                ? sa.symbolForDecl(ent) : sa.allClasses().get(ent.name()));
        defineRecordMembers(sa, synthetic);
    }

    static void defineInterfaceMembers(SemanticAnalyzer sa, InterfaceDeclarationNode iface) {
        if (sa.membersDefined(iface)) return;
        SymbolTable.ClassSymbol classSym = sa.symbolForDecl(iface);
        if (classSym == null) classSym = sa.allClasses().get(iface.name());
        if (classSym == null) return;
        sa.markMembersDefined(iface);
        SymbolTable classScope = classSym.members().enterScope();
        sa.putClassMemberScope(iface.name(), classScope);
        // #160: type-params de interface genérica entram no escopo ANTES dos
        // membros, igual a defineClassMembers — sem isso `map(T input)` não
        // resolve o T.
        sa.registerTypeParameters(iface.name(), iface.typeParameters()); // X5.3
        for (String tp : iface.typeParameters()) {
            classScope.define(TypeParams.symbol(tp, sa)); // §355
        }
        for (AstNode member : iface.members()) {
            if (member instanceof FieldDeclarationNode field) {
                Type fieldType = MemberResolver.resolveType(sa, field.type(), classScope);
                int flags = AccessFlags.STATIC;
                if (field.modifiers().contains("static")) flags |= AccessFlags.STATIC;
                SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(field.name(), fieldType, flags, iface.name());
                classSym.members().define(fs);
                classScope.define(fs);
            } else if (member instanceof MethodDeclarationNode method) {
                // #213: corpo de método de interface (default) precisa de escopo
                // próprio (params/this) para a análise semântica — antes era
                // descartado e a chamada nua `greet(name)` virava função hoisted.
                defineMethodSymbol(sa, method, classSym, classScope, true);
            }
        }
        checkMemberSignatureDupes(sa, iface.members(), iface.name(), classScope, "interface");
    }

    static int requiredConstructorArity(List<FormalParameterNode> parameters) {
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).defaultExpression() != null) return i;
        }
        return parameters.size();
    }

    static void defineConstructorSymbol(SemanticAnalyzer sa, ConstructorDeclarationNode ctor,
                                        SymbolTable.ClassSymbol classSym, SymbolTable classScope) {
        String className = classSym.name();
        List<Type> paramTypes = new ArrayList<>();
        SymbolTable ctorScope = classScope.enterScope();
        ctorScope.define(new SymbolTable.ParameterSymbol("this",
                new Type.ClassType(classSym.packageName(), className, List.of()), 0));
        int idx = 1;
        for (FormalParameterNode param : ctor.parameters()) {
            Type paramType = MemberResolver.resolveType(sa, param.type(), ctorScope);
            paramTypes.add(paramType);
            ctorScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        int requiredArity = requiredConstructorArity(ctor.parameters());
        SymbolTable.ConstructorSymbol ctorSym = new SymbolTable.ConstructorSymbol(className, paramTypes, 1,
                requiredArity);
        classScope.define(ctorSym);
        // #639 face 2: define no símbolo da PRÓPRIA declaração (não no de nome
        // simples, que colide quando duas packages declaram o mesmo nome).
        classSym.members().define(ctorSym);
        sa.putCtorScope(ctor, ctorScope);
    }

    static void defineMethodSymbol(SemanticAnalyzer sa, MethodDeclarationNode method,
                                   SymbolTable.ClassSymbol classSym, SymbolTable classScope) {
        defineMethodSymbol(sa, method, classSym, classScope, false);
    }

    static void defineMethodSymbol(SemanticAnalyzer sa, MethodDeclarationNode method,
                                   SymbolTable.ClassSymbol classSym, SymbolTable classScope, boolean isInterface) {
        String className = classSym.name();
        SymbolTable methodScope = classScope.enterScope();
        methodScope.define(new SymbolTable.ParameterSymbol("this",
                new Type.ClassType(classSym.packageName(), className, List.of()), 0));
        Type returnType = MemberResolver.resolveType(sa, method.returnType(), methodScope);
        List<Type> paramTypes = new ArrayList<>();
        int idx = 1;
        for (FormalParameterNode param : method.parameters()) {
            Type paramType = MemberResolver.resolveType(sa, param.type(), methodScope);
            paramTypes.add(paramType);
            methodScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        // SG-013 (SEM046): preserva private/protected no símbolo — antes era
        // hardcoded 1 (PUBLIC) e a checagem compile-time não tinha informação.
        int accessFlags = AccessFlags.PUBLIC;
        if (method.modifiers().contains("private")) accessFlags = AccessFlags.PRIVATE;
        else if (method.modifiers().contains("protected")) accessFlags = AccessFlags.PROTECTED;
        // #133 (irmão): a flag STATIC precisa chegar ao símbolo — sem ela o
        // lowering de chamada sem receiver não distingue método estático de
        // instância e emitia aload_0 (this) + invokevirtual sobre método
        // estático → IncompatibleClassChangeError (JVM, contexto de instância)
        // / VerifyError (contexto estático, <clinit>).
        if (method.modifiers().contains("static")) accessFlags |= AccessFlags.STATIC;
        if (method.modifiers().contains("abstract")) accessFlags |= AccessFlags.ABSTRACT;
        // #213: método de interface SEM corpo é abstract; com corpo é default
        // (implementação real) — não deve ser cobrado no implementador (SEM043).
        boolean hasBody = method.body() != null && !method.body().isEmpty();
        if (isInterface && !hasBody && (accessFlags & AccessFlags.STATIC) == 0) {
            accessFlags |= AccessFlags.ABSTRACT;
        }
        SymbolTable.MethodSymbol methodSym = new SymbolTable.MethodSymbol(method.name(), className,
                returnType, paramTypes, accessFlags, SymbolTable.DispatchKind.INSTANCE);
        classScope.define(methodSym);
        // #639 face 2: no símbolo da própria declaração (ver constructor acima).
        classSym.members().define(methodSym);
        sa.putMethodScope(method, methodScope);
        sa.putMethodSymbol(method, methodSym);
    }
}
