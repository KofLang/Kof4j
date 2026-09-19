package dev.kof.compiler;

import java.util.List;
import java.util.Map;

/**
 * Resolução de tipos do lado do driver (toType/qualifyViaImports/
 * ownerTypeFromInternal/mainClassType). Puro — recebe o estado necessário
 * (currentUnit/semanticAnalyzer/currentModule) por parâmetro.
 */
public final class CompilerTypes {

    private CompilerTypes() {}

     static Type toType(String typeName, CompilationUnitNode currentUnit) {
         return toType(typeName, currentUnit, (ExternalClasspath) null);
     }

     /** §134 residual: wildcard `import a.b.*` qualifica o nome simples de um
      *  `new` externo quando a classe existe num entry carregado (sem isso o
      *  `new Greeter()` de `import ext.*` descia sem pacote → NoClassDefFound
      *  silencioso em runtime). Aditivo: sem cp, comportamento antigo. */
     static Type toType(String typeName, CompilationUnitNode currentUnit,
                        ExternalClasspath external) {
         return toType(typeName, currentUnit, external, !unitDeclaresType(currentUnit, typeName));
     }

     /**
      * §243 (DECISIONS §4/§179): um tipo DECLARADO pelo usuário vence o alias
      * builtin. Sem isto, `class List { … }` era ignorada em todo ponto de uso
      * (o `new List()` virava `java.util.ArrayList` → IllegalAccessError /
      * NoSuchFieldError), apesar de o compilador emitir o `List.class` do
      * usuário. Os pins existem para `new List()`/`Set()`/`Map()` não
      * qualificados (#139/#150/#214); aqui eles só se aplicam quando NÃO há
      * sombra do usuário.
      */
     private static Type toType(String typeName, CompilationUnitNode currentUnit,
                        ExternalClasspath external, boolean allowBuiltinPins) {
         // SG-012: param de lambda sem anotação — Unknown (nunca Object)
         if (typeName == null) return Type.UnknownType.UNKNOWN;
         if (allowBuiltinPins) {
             if ("List".equals(typeName) || "ArrayList".equals(typeName) || "LinkedList".equals(typeName)) return BuiltinTypes.LIST;
             // #139/#150/#214 — `new Set<T>()`/`new Map<K,V>()`: sem este pin o tipo
             // ficava ClassType("", "Set"/"Map") → os métodos (add/size/put)
             // emitiam owner `Set`/`Map` cru → NoClassDefFoundError/ClassFormatError.
             if ("Set".equals(typeName) || "HashSet".equals(typeName)) return BuiltinTypes.SET;
             if ("Map".equals(typeName) || "HashMap".equals(typeName)) return BuiltinTypes.MAP;
             if ("Channel".equals(typeName)) return BuiltinTypes.CHANNEL;
         }
         Type viaImports = qualifyViaImports(typeName, currentUnit, external);
         if (viaImports != null) return viaImports;
         int lastDot = typeName.lastIndexOf('.');
         if (lastDot > 0 && !typeName.contains("<") && !typeName.contains("/")) {
             return new Type.ClassType(typeName.substring(0, lastDot),
                     typeName.substring(lastDot + 1), List.of());
         }
         // §243: o usuário declarou um tipo com este nome → ele vence o alias
         // primitivo/builtin que `Type.of` mapearia (`String` → java.lang.String).
         if (!allowBuiltinPins) return new Type.ClassType("", typeName, List.of());
         // #313: throwable simples do JDK (`Exception`, `RuntimeException`, ...)
         // NUNCA é classe do programa — mora em java.lang. Sem isto,
         // `throw new Exception("x")` saía `new Exception`/`invokespecial
         // Exception.<init>` (nome cru, classe inexistente →
         // NoClassDefFoundError no load), enquanto a exception table (caminho
         // `exceptionType`, #163/#241) qualificava corretamente. Mesmo mapa,
         // agora no toType (face do NEW/invokespecial/descritores).
         if (JAVA_LANG_THROWABLES.contains(typeName)) {
             return new Type.ClassType("java.lang", typeName, List.of());
         }
         return Type.of(typeName);
     }

    /**
     * toType com o contexto semântico completo: além de imports, resolve tipos
     * simples declarados no módulo (mesmo pacote ou outro arquivo) via a
     * SymbolTable — e qualifica RECURSIVAMENTE os argumentos genéricos
     * (`List<NodeUI>`/`Map<String, NodeUI>`/`List<List<NodeUI>>`). Sem isso o
     * type-argument ficava `ClassType("", "NodeUI")` e o cast/descritor JVM saía
     * sem pacote (NoClassDefFoundError) ou com pontos (ClassFormatError).
     */
    static Type toType(String typeName, CompilationUnitNode currentUnit, SemanticAnalyzer sa) {
        boolean userDeclares = unitDeclaresType(currentUnit, typeName)
                || (sa != null && sa.getClass(typeName) != null);
        Type t = toType(typeName, currentUnit, null, !userDeclares);
        return qualifyDeep(t, currentUnit, sa);
    }

    /**
     * #163: tipos de exceção de `java.lang` escritos pelo nome simples no
     * `catch` (`catch (RuntimeException e)` — o corpus documenta
     * `catch (Exception e)`, `learn/27:86`). Sem qualificar, o tipo do local
     * ficava `ClassType("", "RuntimeException")` e o descriptor JVM saía
     * `LRuntimeException;` → `NoClassDefFoundError: RuntimeException` ao
     * chamar qualquer método no `e`. `String` continua sendo a exceção de
     * Kof (mensagem, `RuntimeException` em runtime).
     */
    static final java.util.Set<String> JAVA_LANG_THROWABLES = java.util.Set.of(
            "Throwable", "Exception", "RuntimeException", "IllegalArgumentException",
            "IllegalStateException", "IndexOutOfBoundsException", "NumberFormatException",
            "ArithmeticException", "NullPointerException", "UnsupportedOperationException",
            "ClassCastException", "Error", "OutOfMemoryError", "StackOverflowError",
            // #493: ArrayIndexOutOfBoundsException is a direct subclass of IndexOutOfBoundsException
            // (which IS in the list); missing it caused SEM011 on any catch clause using it.
            "ArrayIndexOutOfBoundsException");

    static Type exceptionType(String typeName, CompilationUnitNode currentUnit) {
        // §243: a exceção de Kof é String — mas um `class String` do usuário
        // (DECISIONS §4) vence o builtin.
        if ("String".equals(typeName) && !unitDeclaresType(currentUnit, typeName)) return BuiltinTypes.STRING;
        Type t = toType(typeName, currentUnit);
        if (t instanceof Type.ClassType ct && ct.packageName().isEmpty()
                && JAVA_LANG_THROWABLES.contains(ct.name())) {
            return new Type.ClassType("java.lang", ct.name(), List.of());
        }
        return t;
    }

    /**
     * §240: tipos do JDK que o Kof trata como BUILTIN (não como classe externa
     * de interop): `String` (a exceção de Kof é String) e QUALQUER throwable
     * (`java.lang.*`, `java.io.IOException`, ...). O
     * {@code ExternalClasspath.knows()} passou a devolver true para TODO
     * `java/*` (para o interop de `StringBuilder`/`String.join`), mas isso
     * fazia {@code SemanticAnalyzer.isExternal} classificar `String` e as
     * exceções como externas, contornando o registro de builtins do Kof
     * (39 reds de suíte: `indexOf`→SEM025, `throw RuntimeException`→SEM026).
     * A separação: externo = JDK MENOS estes; builtin = estes.
     */
    private static final Map<String, Boolean> KOF_BUILTIN_JDK_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    static boolean isKofBuiltinJavaLang(String internalName) {
        if (internalName == null) return false;
        if ("java/lang/String".equals(internalName)) return true;
        return KOF_BUILTIN_JDK_CACHE.computeIfAbsent(internalName, n -> {
            if (!(n.startsWith("java/") || n.startsWith("javax/") || n.startsWith("jdk/"))) {
                return false;
            }
            try {
                return Throwable.class.isAssignableFrom(Class.forName(n.replace('/', '.')));
            } catch (Throwable t) {
                return false;
            }
        });
    }

    /**
     * Qualificação profunda de tipos: separa nomes pontuados no campo `name`
     * ("com.dev.NodeUI" → pkg "com.dev" + name "NodeUI") e resolve nomes simples
     * sem pacote usando imports → classes declaradas no módulo (SymbolTable).
     * Recursiva nos type-arguments/arrays/nullable/function. Idempotente e
     * conservadora: não toca builtin (kof.List), nomes já com pacote, nem faz
     * fallback cego por nome (import ambíguo → null → tipo preservado).
     */
    static Type qualifyDeep(Type t, CompilationUnitNode unit, SemanticAnalyzer sa) {
        if (t instanceof Type.ClassType ct) {
            String name = ct.name();
            String pkg = ct.packageName();
            // 1) nome pontuado no field "name" (ex.: List<com.dev.NodeUI>)
            if (name.contains(".") && !name.contains("<") && !name.contains("/")) {
                int lastDot = name.lastIndexOf('.');
                if (lastDot > 0) {
                    pkg = name.substring(0, lastDot);
                    name = name.substring(lastDot + 1);
                }
            }
            // 2) nome simples sem pacote → imports / declarados no módulo
            if (pkg.isEmpty() && !name.contains(".") && !name.contains("<")) {
                String via = simpleNamePackage(name, unit, sa);
                if (via != null) pkg = via;
            }
            // 2b) §179 (D-BACKEND-SEMANTICS #4): tipo kof.ui/kof.media DECLARADO
            // (var/param/campo/retorno) que nada mais resolveu → builtin. Sem
            // isto o descritor JVM saía `LLabel;` enquanto o handle é `int`
            // (VerifyError). O shadowing do usuário é preservado: se o módulo
            // declara um tipo homônimo (nome simples, mesmo arquivo), ele vence.
            if (pkg.isEmpty() && !name.contains(".") && !name.contains("<")
                    && !unitDeclaresType(unit, name) && (sa == null || sa.getClass(name) == null)) {
                Type builtin = builtinDeclaredType(name);
                if (builtin != null) return qualifyDeep(builtin, unit, sa);
            }
            // 3) args recursivos
            List<Type> args = new java.util.ArrayList<>();
            boolean changedArgs = false;
            for (Type a : ct.typeArguments()) {
                Type qa = qualifyDeep(a, unit, sa);
                args.add(qa);
                if (!qa.equals(a)) changedArgs = true;
            }
            if (pkg.equals(ct.packageName()) && name.equals(ct.name()) && !changedArgs) return ct;
            return new Type.ClassType(pkg, name, List.copyOf(args));
        }
        if (t instanceof Type.ArrayType at) {
            Type qc = qualifyDeep(at.componentType(), unit, sa);
            return qc.equals(at.componentType()) ? at : new Type.ArrayType(qc);
        }
        if (t instanceof Type.NullableType nt) {
            Type q = qualifyDeep(nt.inner(), unit, sa);
            return q.equals(nt.inner()) ? nt : new Type.NullableType(q);
        }
        if (t instanceof Type.FunctionType ft) {
            List<Type> ps = new java.util.ArrayList<>();
            boolean ch = false;
            for (Type p : ft.parameterTypes()) { Type qp = qualifyDeep(p, unit, sa); ps.add(qp); if (!qp.equals(p)) ch = true; }
            Type qr = qualifyDeep(ft.returnType(), unit, sa);
            if (!qr.equals(ft.returnType())) ch = true;
            if (!ch) return ft;
            return new Type.FunctionType(ps, qr, ft.className());
        }
        return t;
    }

    /**
     * Pacote de um nome simples de classe: (1) import explícito não-wildcard
     * (ambíguo → null, sem chute); (2) classe declarada no módulo (SymbolTable —
     * cobre mesmo pacote e outros arquivos do mesmo módulo; classe local do main
     * tem pacote "" → preservado). Retorna null se não resolver (tipo preservado).
     */
    static String simpleNamePackage(String name, CompilationUnitNode unit, SemanticAnalyzer sa) {
        // #445: o ramo "enum = pkg-vazio" era legado da era em que o valor de
        // enum era a String do nome; D-ENUM207 tornou-o INSTÂNCIA da classe
        // emitida (JvmTypeMapper: "Descriptor próprio L<Dir>;"). Enum agora
        // desce pelo fluxo comum (imports/allClasses devolvem o pacote real).
        if (unit != null) {
            String found = null;
            for (String imp : unit.imports()) {
                if (!imp.endsWith("*") && imp.endsWith("." + name)) {
                    if (found != null) return null; // ambíguo: dois imports com o mesmo nome
                    found = imp.substring(0, imp.lastIndexOf('.'));
                }
            }
            if (found != null) return found;
        }
        if (sa != null) {
            SymbolTable.ClassSymbol cs = sa.getClass(name);
            if (cs != null) return cs.packageName();
        }
        return null;
    }

    /**
     * §179: tipo kof.ui/kof.media por nome simples, para RESOLUÇÃO DE TIPO
     * DECLARADO (var/param/campo/retorno). {@code KofUi.typeByName} cobre todos
     * os tipos de UI; {@code ImageData} é o único tipo de DADO de kof.media com
     * nome próprio (Audio/Video de media são namespaces e o typer de construtor
     * já os resolve como ui.Audio/ui.Video). Retorna null se não for builtin.
     */
    static Type builtinDeclaredType(String name) {
        Type ui = KofUi.typeByName(name);
        if (ui != null) return ui;
        if ("ImageData".equals(name)) return KofMedia.IMAGE_DATA;
        return null;
    }

    /** O módulo (mesmo arquivo) declara classe/record/enum com este nome? */
    static boolean unitDeclaresType(CompilationUnitNode unit, String name) {
        if (unit == null) return false;
        for (AstNode d : unit.declarations()) {
            if (d instanceof ClassDeclarationNode c && c.name().equals(name)) return true;
            if (d instanceof RecordDeclarationNode r && r.name().equals(name)) return true;
            if (d instanceof EnumDeclarationNode e && e.name().equals(name)) return true;
        }
        return false;
    }

    /**
     * §243: o usuário declara um tipo com este nome (mesmo arquivo ou módulo)?
     * Usado para o guard de shadowing dos pins de builtin (DECISIONS §4/§179).
     */
    static boolean userDeclaresType(CompilationUnitNode unit, SemanticAnalyzer sa, String name) {
        if (name == null) return false;
        return unitDeclaresType(unit, name) || (sa != null && sa.getClass(name) != null);
    }

    /**
     * §243: tipo de coleção builtin para `new List/Set/Map` — {@code null}
     * quando o usuário declara um tipo homônimo (o dele vence, DECISIONS §4).
     * Centraliza o pin que antes estava duplicado em 3 typers/lowerers.
     */
    static Type builtinCollectionType(String typeName, CompilationUnitNode unit, SemanticAnalyzer sa) {
        if (userDeclaresType(unit, sa, typeName)) return null;
        if ("List".equals(typeName) || "ArrayList".equals(typeName) || "LinkedList".equals(typeName)) return BuiltinTypes.LIST;
        if ("Set".equals(typeName) || "HashSet".equals(typeName)) return BuiltinTypes.SET;
        if ("Map".equals(typeName) || "HashMap".equals(typeName)) return BuiltinTypes.MAP;
        return null;
    }

    /** Espelho driver-side do qualifyViaImports do SemanticAnalyzer. */
    static Type qualifyViaImports(String name, CompilationUnitNode currentUnit) {
        return qualifyViaImports(name, currentUnit, null);
    }

    /** §134 residual: wildcard `import a.b.*` qualifica pelo ExternalClasspath
     *  quando a classe existe num entry. Sem cp, comportamento antigo. */
    static Type qualifyViaImports(String name, CompilationUnitNode currentUnit,
                                  ExternalClasspath external) {
        if (name.contains(".") || name.contains("<") || name.endsWith("[]")) return null;
        if (currentUnit == null) return null;
        if (System.getProperty("kof.trace") != null && name.equals("WebView")) {
            System.err.println("QVI WebView imports=" + currentUnit.imports());
        }
        for (String imp : currentUnit.imports()) {
            if (!imp.endsWith("*") && imp.endsWith("." + name)) {
                String pkg = imp.substring(0, imp.lastIndexOf('.'));
                return new Type.ClassType(pkg, name, List.of());
            }
        }
        if (external != null) {
            for (String imp : currentUnit.imports()) {
                if (imp.endsWith(".*")) {
                    String pkg = imp.substring(0, imp.length() - 2);
                    if (external.knows(pkg.replace('.', '/') + "/" + name)) {
                        return new Type.ClassType(pkg, name, List.of());
                    }
                }
            }
        }
        return null;
    }

    /** Nome JVM da entidade: as classes top-level do programa ficam sem
     *  pacote (User.class); o Main é Default/Main. */
    static String classNameFor(String simpleName) {
        return simpleName;
    }

    static Type ownerTypeFromInternal(String internalName, SemanticAnalyzer semanticAnalyzer) {
        if (semanticAnalyzer != null) {
            String simpleName = internalName.substring(internalName.lastIndexOf('/') + 1);
            SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(simpleName);
            if (cs != null) return cs.type();
        }
        String pkg = "";
        String name = internalName;
        int slashIdx = internalName.lastIndexOf('/');
        if (slashIdx >= 0) {
            pkg = internalName.substring(0, slashIdx).replace('/', '.');
            name = internalName.substring(slashIdx + 1);
        }
        return new Type.ClassType(pkg, name, List.of());
    }

    static Type mainClassType(IRModule currentModule) {
        String mod = currentModule != null && !currentModule.name().isEmpty()
                ? currentModule.name() : "Default";
        if (!mod.contains("/")) mod = mod + "/Main";
        int slashIdx = mod.lastIndexOf('/');
        if (slashIdx >= 0) {
            return new Type.ClassType(mod.substring(0, slashIdx).replace('/', '.'), mod.substring(slashIdx + 1), List.of());
        }
        return new Type.ClassType("", mod, List.of());
    }

    static java.util.List<String> enumConstantsOf(String name, CompilationUnitNode currentUnit) {
        if (name == null || currentUnit == null) return List.of();
        for (AstNode d : currentUnit.declarations()) {
            if (d instanceof EnumDeclarationNode en && en.name().equals(name)) {
                return en.constants();
            }
        }
        return List.of();
    }

    static boolean isEnumType(Type t, CompilationUnitNode currentUnit) {
        // #445: o pacote REAL do enum já flui pelo SymbolTable (ClassSymbol com
        // packageOf) — exigir pacote vazio here era o modelo pré-D-ENUM207
        // (valor=String, sempre raiz). Um enum de arquivo importado tem
        // ClassType(pkg, nome) legítimo e deixa de ser reconhecido sem isso.
        if (!(t instanceof Type.ClassType ct) || !ct.typeArguments().isEmpty()) return false;
        return !enumConstantsOf(ct.name(), currentUnit).isEmpty();
    }

    /**
     * #445 — fonte ÚNICA do tipo de um enum por nome simples: o ClassSymbol
     * registrado (pkg real de `package X`, cobre arquivos importados via
     * expansão de módulo); fallback pacote vazio (mesmo arquivo/raiz — shape
     * idêntica ao modelo antigo). Todo sítio que SYNTEZA um tipo de enum
     * (acesso, statics, symbol table, switch/typer) passa por aqui; quem já
     * RECEBE o recvType qualificado (FieldAccess guard) usa o próprio tipo.
     */
    static Type enumTypeOf(String name, SemanticAnalyzer sa) {
        if (sa != null) {
            SymbolTable.ClassSymbol cs = sa.getClass(name);
            if (cs != null && "Enum".equals(cs.superClass())) return cs.type();
        }
        return new Type.ClassType("", name, List.of());
    }

    static boolean isRecordType(Type t, CompilationUnitNode currentUnit, SemanticAnalyzer semanticAnalyzer) {
        if (!(t instanceof Type.ClassType ct) || ct.typeArguments() != null && !ct.typeArguments().isEmpty()) return false;
        if (currentUnit != null) {
            for (AstNode d : currentUnit.declarations()) {
                if (d instanceof RecordDeclarationNode r && r.name().equals(ct.name())) return true;
            }
        }
        if (semanticAnalyzer != null) {
            SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(ct.name());
            if (cs != null && cs.superClass() != null
                    && (cs.superClass().equals("Record") || cs.superClass().endsWith("Record"))) {
                return true;
            }
        }
        return false;
    }

    static boolean containsLambdaFunctionType(Type t) {
        if (t instanceof Type.FunctionType ft) {
            return ft.className() == null;
        }
        if (t instanceof Type.ClassType ct && ct.typeArguments() != null) {
            for (Type arg : ct.typeArguments()) {
                if (containsLambdaFunctionType(arg)) return true;
            }
        }
        if (t instanceof Type.ArrayType at) {
            return containsLambdaFunctionType(at.componentType());
        }
        return false;
    }

    static String enumConstantOfExpr(ExpressionNode e, CompilationUnitNode currentUnit) {
        if (e instanceof FieldAccessExpr fa && fa.receiver() instanceof IdentifierExpr rid
                && isEnumName(rid.name(), currentUnit)) {
            return enumConstantsOf(rid.name(), currentUnit).contains(fa.fieldName()) ? fa.fieldName() : null;
        }
        if (e instanceof LiteralExpr l && l.kind() == ConcreteLiteralKind.STRING) {
            return l.value();
        }
        if (e instanceof IdentifierExpr ie) {
            // não-qualificado: procura em todos os enums declarados
            if (currentUnit != null) {
                for (AstNode d : currentUnit.declarations()) {
                    if (d instanceof EnumDeclarationNode en && en.constants().contains(ie.name())) {
                        return ie.name();
                    }
                }
            }
        }
        return null;
    }

    static boolean isEnumName(String name, CompilationUnitNode currentUnit) {
        return currentUnit != null && currentUnit.declarations().stream()
                .anyMatch(d -> d instanceof EnumDeclarationNode en && en.name().equals(name));
    }

    static String typeToString(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return Type.canonicalPrimitiveName(pt.name());
        }
        if (type instanceof Type.ClassType ct) return ct.name();
        if (type instanceof Type.ArrayType at) return typeToString(at.componentType()) + "[]";
        return "Object";
    }

    static Type substituteTypeVariable(String tvName, Type recvType, CompilationUnitNode currentUnit) {
        if (!(recvType instanceof Type.ClassType ct) || ct.typeArguments().isEmpty()) return null;
        if (currentUnit != null) {
            for (AstNode d : currentUnit.declarations()) {
                if (d instanceof ClassDeclarationNode cls && cls.name().equals(ct.name())) {
                    for (int i = 0; i < cls.typeParameters().size(); i++) {
                        if (i < ct.typeArguments().size() && cls.typeParameters().get(i).equals(tvName)) {
                            return ct.typeArguments().get(i);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * §245/#268: tipo de um campo/método cujo tipo declarado é um parâmetro
     * genérico (`T wrapped`) — substitui o type-variable pelo argumento real do
     * RECEIVER (`Wrapper<Point>.wrapped` → `Point`). Sem isto o tipo ficava
     * `TypeVariable(T)`/erased e o próximo acesso (`.x`) emitia owner `?`/`""`
     * (`NoClassDefFoundError`/`ClassFormatError`). Não muda nada quando o tipo
     * não é um type-variable ou o receiver não traz argumentos.
     */
    static Type substituteTypeVariableIn(Type memberType, Type recvType, CompilationUnitNode currentUnit) {
        if (memberType instanceof Type.TypeVariable tv) {
            Type sub = substituteTypeVariable(tv.name(), recvType, currentUnit);
            if (sub != null) return sub;
        }
        return memberType;
    }
    static Type resolveWithTypeParams(String typeName, List<String> typeParams, CompilationUnitNode currentUnit) {
        if (typeParams.contains(typeName)) return new Type.TypeVariable(typeName);
        return CompilerTypes.toType(typeName, currentUnit);
    }

    /**
     * 4-arg (com contexto semântico): além de toType, qualifica RECURSIVAMENTE
     * os type-arguments via imports/classes declaradas no módulo. Ponto único
     * de resolução de tipos de campos/parâmetros/retornos/records — sem isso
     * `List<NodeUI>` ficava `ClassType("","NodeUI")` no arg e o cast/descritor
     * JVM saía sem pacote (NoClassDefFoundError).
     */
    static Type resolveWithTypeParams(String typeName, List<String> typeParams,
                                      CompilationUnitNode currentUnit, SemanticAnalyzer sa) {
        if (typeParams.contains(typeName)) return new Type.TypeVariable(typeName);
        return CompilerTypes.toType(typeName, currentUnit, sa);
    }
    static KofLoadLiteral defaultValueOp(Type type) {
        // §125 (decisão da mantenedora 12/09, opção A): default de
        // Nullable(primitivo) é o default do primitivo (`0`/`0.0`/`false`),
        // NUNCA null — mesmo princípio do map-miss SG-008/bug-87
        // (null se perde em transitos de primitivo). Nullable(ref) mantém
        // null (precedente §124).
        if (type instanceof Type.NullableType nt
                && nt.inner() instanceof Type.PrimitiveType) {
            type = nt.inner();
        }
        if (type instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "long" -> new KofLoadLiteral(Type.PrimitiveType.LONG, 0L);
                case "float" -> new KofLoadLiteral(Type.PrimitiveType.FLOAT, 0.0f);
                case "double" -> new KofLoadLiteral(Type.PrimitiveType.DOUBLE, 0.0d);
                default -> new KofLoadLiteral(Type.PrimitiveType.INT, 0);
            };
        }
        return new KofLoadLiteral(type, null);
    }
}
