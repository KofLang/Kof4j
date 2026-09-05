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
}
