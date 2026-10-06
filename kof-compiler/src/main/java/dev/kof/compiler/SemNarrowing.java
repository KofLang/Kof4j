package dev.kof.compiler;

/**
 * REFACTOR-500 (fase 6, extracao de {@link SemExpressionTyper}): narrowing
 * de nullabilidade em expressoes ({@code s != null && s.length} ve
 * {@code s: T}) e vinculacao das variaveis de um pattern de switch
 * ({@code case Point(var x, var y)}). Stateless — o estado compartilhado
 * ({@link SemanticAnalyzer}) entra por parâmetro, igual ao typer-mestre.
 */
final class SemNarrowing {

    private SemNarrowing() {}

    /**
     * SG-005: escopo com os narrowings de nullability de `cond` aplicados
     * (lado direito de `&&`: `s != null && s.length` vê `s: T`). Mirror do
     * collectNarrowing do StatementAnalyzer, nível expressão — só `x != null`
     * e conjunção; `||` não narrow.
     */
    static SymbolTable narrowedScope(ExpressionNode cond, SymbolTable scope) {
        return narrowedScope(cond, scope, false);
    }

    /**
     * #770: narrowing dos RAMOS de um `if`-expressão. `negated=false` é o
     * ramo then (`x != null` narrowa `x: T`); `negated=true` é o ramo else
     * (`x == null` narrowa `x: T`). Mesma tabela de polaridade do
     * collectNarrowing do StatementAnalyzer (`!=` so narrowa o then, `==`
     * so narrowa o else, `&&` recursa os dois lados, `||` não narrowa) —
     * a assimetria anterior (statement narrowava, expressão não) dava
     * SEM025 honesto p/ `return if (q != null) { math.parseInt(q) } else
     * { 0 }` enquanto a forma-statement compilava.
     */
    static SymbolTable narrowedScope(ExpressionNode cond, SymbolTable scope, boolean negated) {
        if (scope == null) return scope;
        java.util.List<SymbolTable.LocalVariableSymbol> narrow = new java.util.ArrayList<>();
        collectCondNarrowing(cond, scope, narrow, negated);
        if (narrow.isEmpty()) return scope;
        SymbolTable child = scope.enterScope();
        for (SymbolTable.LocalVariableSymbol s : narrow) child.define(s);
        return child;
    }

    private static void collectCondNarrowing(ExpressionNode cond, SymbolTable scope,
            java.util.List<SymbolTable.LocalVariableSymbol> out, boolean negated) {
        if (!(cond instanceof BinaryExpr be)) return;
        String op = be.operator();
        if ("&&".equals(op)) {
            collectCondNarrowing(be.left(), scope, out, negated);
            collectCondNarrowing(be.right(), scope, out, negated);
            return;
        }
        if ("||".equals(op)) return;
        if (!(be.right() instanceof LiteralExpr rl && rl.kind() == ConcreteLiteralKind.NULL)) return;
        if (!(be.left() instanceof IdentifierExpr id)) return;
        boolean thenNarrow = "!=".equals(op);
        boolean elseNarrow = "==".equals(op);
        if (!thenNarrow && !elseNarrow) return;
        if (negated ? !elseNarrow : !thenNarrow) return;
        SymbolTable.Symbol sym = scope.resolve(id.name());
        if (sym != null && sym.type() instanceof Type.NullableType nt) {
            out.add(new SymbolTable.LocalVariableSymbol(id.name(), nt.inner(), 0));
        }
    }

    /**
     * Define no escopo do case as variáveis de um pattern:
     * {@code case T v} → {@code v:T}; {@code case T(var x, var y)} → campos por
     * índice (record) ou por nome. Espelha a lógica do {@code SwitchStmt}.
     */
    static void bindPatternVars(SemanticAnalyzer sa, PatternExpr pe, SymbolTable scope) {
        Type patType = MemberResolver.resolveType(sa, pe.typeName(), scope);
        if (patType == null) patType = Type.UnknownType.UNKNOWN;
        if (pe.varName() != null) {
            scope.define(new SymbolTable.LocalVariableSymbol(pe.varName(), patType, 0));
            return;
        }
        if (pe.fieldVars().isEmpty()) return;
        String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
        SymbolTable.ClassSymbol cls = sa.getClass(simple);
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
                        for (AstNode d : sa.unit().declarations()) {
                            if (d instanceof RecordDeclarationNode rec && rec.name().equals(simple)) {
                                if (i < rec.components().size()) {
                                    fieldType = MemberResolver.resolveType(sa, rec.components().get(i).type(), scope);
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
}
