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
}