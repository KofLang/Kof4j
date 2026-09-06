package dev.kof.compiler;

import java.util.List;

/**
 * Resolução de membros e hierarquia extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Puro — recebe o estado necessário por parâmetro.
 */
final class MemberResolver {

    private MemberResolver() {}

    /** BFS pela hierarquia (super + interfaces) em busca de um membro. */
    static SymbolTable.Symbol resolveInHierarchy(SemanticAnalyzer sa, String className, String memberName) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(className);
        visited.add(className);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            SymbolTable.ClassSymbol cs = sa.getClass(current);
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

    static boolean isObjectMethod(String name, int argCount) {
        return switch (name) {
            case "hashCode", "toString", "getClass" -> argCount == 0;
            case "equals" -> argCount == 1;
            default -> false;
        };
    }

    static boolean isBuiltinTypeName(String name) {
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
    static Type qualifyViaImports(CompilationUnitNode unit, String name) {
        if (name.contains(".") || name.contains("<") || name.endsWith("[]")) return null;
        if (unit == null) return null;
        for (String imp : unit.imports()) {
            if (!imp.endsWith("*") && imp.endsWith("." + name)) {
                String pkg = imp.substring(0, imp.lastIndexOf('.'));
                return new Type.ClassType(pkg, name, List.of());
            }
        }
        return null;
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

    /** Resolve um nome de tipo no escopo (type param → import → qualificado). */
    static Type resolveType(SemanticAnalyzer sa, String name, SymbolTable scope) {
        SymbolTable.Symbol sym = scope != null ? scope.resolve(name) : null;
        if (sym instanceof SymbolTable.TypeParameterSymbol) return sym.type();
        Type viaImports = qualifyViaImports(sa.unit(), name);
        if (viaImports != null) return viaImports;
        // qualifyDeep: recursa nos type-arguments — `List<NodeUI>` com
        // `import com.dev.NodeUI` precisa do pacote no ARG (senão o receiver
        // do `.get()` fica ClassType("","NodeUI") e o checkcast sai sem pacote
        // → NoClassDefFoundError). Idempotente; não toca builtin/enum/nome local.
        return CompilerTypes.qualifyDeep(qualifiedType(Type.of(name)), sa.unit(), sa);
    }

    /** Constantes de um enum declarado na unit (vazio se não for enum). */
    static List<String> enumConstantsOf(CompilationUnitNode unit, String name) {
        if (name == null || unit == null) return List.of();
        for (AstNode d : unit.declarations()) {
            if (d instanceof EnumDeclarationNode en && en.name().equals(name)) {
                return en.constants();
            }
        }
        return List.of();
    }

    /** Nome da constante de enum referenciada por uma expressão, ou null. */
    static String enumConstantOfExpr(CompilationUnitNode unit, ExpressionNode e) {
        if (e instanceof FieldAccessExpr fa && fa.receiver() instanceof IdentifierExpr rid) {
            return enumConstantsOf(unit, rid.name()).contains(fa.fieldName()) ? fa.fieldName() : null;
        }
        if (e instanceof LiteralExpr l && l.kind() == ConcreteLiteralKind.STRING) {
            return l.value();
        }
        if (e instanceof IdentifierExpr ie) {
            // não-qualificado: Red quando algum enum declara Red
            if (unit != null) {
                for (AstNode d : unit.declarations()) {
                    if (d instanceof EnumDeclarationNode en && en.constants().contains(ie.name())) {
                        return ie.name();
                    }
                }
            }
            return null;
        }
        return null;
    }
}
