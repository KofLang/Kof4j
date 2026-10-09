package dev.kof.compiler;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * D-PORTUKOF unidade 3 (07/10): estado da superfície de métodos/campos do
 * PortuKof. O hook de ENTRADA dos typers registra, por nó ORIGINAL, a decisão
 * nome-de-superfície → nome canônico resolvida pelo tipo real do receiver; o
 * splicer pós-análise aplica os registros na AST. Extraído de
 * {@link SemanticAnalyzer} (REFACTOR-500: a classe cruzou 600 linhas).
 *
 * <p>As rotinas de re-indexação usam as MESMAS tabelas de identidade do
 * analyzer — nada além da entrada do nó antigo é tocado.</p>
 */
final class PortuKofSurfaceState {

    private final Map<MethodCallExpr, String> methodAliases = new IdentityHashMap<>();
    private final Map<FieldAccessExpr, String> fieldAliases = new IdentityHashMap<>();

    private final Map<ExpressionNode, Type> expressionTypes;
    private final Map<MethodCallExpr, SymbolTable.MethodSymbol> resolvedMethods;
    private final Map<NewExpr, SymbolTable.ConstructorSymbol> resolvedConstructors;

    PortuKofSurfaceState(Map<ExpressionNode, Type> expressionTypes,
                         Map<MethodCallExpr, SymbolTable.MethodSymbol> resolvedMethods,
                         Map<NewExpr, SymbolTable.ConstructorSymbol> resolvedConstructors) {
        this.expressionTypes = expressionTypes;
        this.resolvedMethods = resolvedMethods;
        this.resolvedConstructors = resolvedConstructors;
    }

    void recordMethod(MethodCallExpr mc, String canon) { methodAliases.put(mc, canon); }

    void recordField(FieldAccessExpr fa, String canon) { fieldAliases.put(fa, canon); }

    String methodAlias(MethodCallExpr mc) { return methodAliases.get(mc); }

    String fieldAlias(FieldAccessExpr fa) { return fieldAliases.get(fa); }

    void spliceTypeCache(ExpressionNode from, ExpressionNode to) {
        Type t = expressionTypes.get(from);
        if (t != null) expressionTypes.put(to, t);
    }

    void spliceMethodCache(MethodCallExpr from, MethodCallExpr to) {
        spliceTypeCache(from, to);
        SymbolTable.MethodSymbol s = resolvedMethods.get(from);
        if (s != null) resolvedMethods.put(to, s);
    }

    void spliceCtorCache(NewExpr from, NewExpr to) {
        SymbolTable.ConstructorSymbol s = resolvedConstructors.get(from);
        if (s != null) resolvedConstructors.put(to, s);
    }
}
