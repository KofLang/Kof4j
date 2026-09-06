package dev.kof.compiler;

import java.util.List;

/**
 * Resolução de tipos do lado do driver (toType/qualifyViaImports/
 * ownerTypeFromInternal/mainClassType). Puro — recebe o estado necessário
 * (currentUnit/semanticAnalyzer/currentModule) por parâmetro.
 */
final class CompilerTypes {

    private CompilerTypes() {}

    static Type toType(String typeName, CompilationUnitNode currentUnit) {
        if ("List".equals(typeName) || "ArrayList".equals(typeName)) return BuiltinTypes.LIST;
        if ("Channel".equals(typeName)) return BuiltinTypes.CHANNEL;
        Type viaImports = qualifyViaImports(typeName, currentUnit);
        if (viaImports != null) return viaImports;
        int lastDot = typeName.lastIndexOf('.');
        if (lastDot > 0 && !typeName.contains("<") && !typeName.contains("/")) {
            return new Type.ClassType(typeName.substring(0, lastDot),
                    typeName.substring(lastDot + 1), List.of());
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
        Type t = toType(typeName, currentUnit);
        return qualifyDeep(t, currentUnit, sa);
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
        // enum: o valor em runtime é String (classDescriptor mapeia pkg-vazio +
        // isEnumName → Ljava/lang/String;). Ganhar pacote quebraria o cast —
        // preservado. isEnumName é o registro GLOBAL (cobre enum de outro arquivo).
        if (BuiltinTypes.isEnumName(name)) return null;
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

    /** Espelho driver-side do qualifyViaImports do SemanticAnalyzer. */
    static Type qualifyViaImports(String name, CompilationUnitNode currentUnit) {
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
        if (!(t instanceof Type.ClassType ct) || !ct.packageName().isEmpty() || !ct.typeArguments().isEmpty()) return false;
        return !enumConstantsOf(ct.name(), currentUnit).isEmpty();
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
