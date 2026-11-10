package dev.kof.compiler;

/**
 * Narrowing de nullability por CAMINHO (D-NARROW-WHILE / #159). O `if`/`while`
 * retipam locais no escopo filho (`collectNarrowing` → símbolo com o tipo
 * interno); um acesso a CAMPO (`b.data`) não é `IdentifierExpr`, então não era
 * narrowável: o receiver continuava `Nullable` e o `SEM049` rejeitava
 * `if (b.data != null) { b.data.length() }`. O campo narrowado vira um símbolo
 * SINTÉTICO (nome = prefixo + path) no escopo do ramo/corpo; o acesso consulta
 * o path e devolve o tipo interno. O nome sintético nunca colide com um
 * identificador do programa.
 *
 * Segundo papel: a atribuição a um local narrowado precisa da tipo da
 * DECLARAÇÃO para a assignabilidade (D-NARROW-WHILE: "reassignment inside the
 * narrowed scope keeps the nullability of the DECLARATION") — sem isso
 * `s = nextVal(i)` dentro do corpo narrowado dá SEM012 falso-positivo.
 */
final class Narrowing {

    private Narrowing() {}

    private static final String FIELD_PREFIX = "\u0000narrow#";

    /** Path textual de um lvalue/receiver (`b.data`, `a.b.c`) ou null. */
    static String pathOf(ExpressionNode e) {
        if (e instanceof IdentifierExpr ie) return ie.name();
        if (e instanceof FieldAccessExpr fa) {
            String base = pathOf(fa.receiver());
            return base == null ? null : base + "." + fa.fieldName();
        }
        return null;
    }

    /** Símbolo sintético que marca o campo `path` como não-nulo no escopo. */
    static SymbolTable.LocalVariableSymbol fieldNarrow(String path, Type type) {
        return new SymbolTable.LocalVariableSymbol(FIELD_PREFIX + path, type, 0);
    }

    /** Tipo não-nulo do campo narrowado, ou null se não há narrowing ativo. */
    static Type narrowedField(SymbolTable scope, String path) {
        if (scope == null || path == null) return null;
        SymbolTable.Symbol s = scope.resolve(FIELD_PREFIX + path);
        return s != null ? s.type() : null;
    }

    /**
     * Declaração (Nullable) de um local, ignorando o símbolo narrowado: sobe a
     * cadeia de escopos até o primeiro `LocalVariableSymbol` com tipo
     * `NullableType` (o narrowado tem o tipo INTERNO, não-nulo, e é pulado).
     * Null quando o nome não tem declaração nulável.
     */
    static SymbolTable.LocalVariableSymbol declarationOf(SymbolTable scope, String name) {
        for (SymbolTable s = scope; s != null; s = s.parent()) {
            SymbolTable.Symbol sym = s.localSymbols().get(name);
            if (sym instanceof SymbolTable.LocalVariableSymbol lv
                    && lv.type() instanceof Type.NullableType) {
                return lv;
            }
        }
        return null;
    }

    /**
     * Símbolo que rege a ESCrita de `name`: se o símbolo resolvido é o
     * narrowado (não-nulo) e existe declaração Nullable, vale a declaração —
     * o slot do local tem o tipo da declaração, o narrowing é só de leitura.
     */
    static SymbolTable.Symbol assignTarget(SymbolTable scope, String name,
            SymbolTable.Symbol resolved) {
        if (resolved == null || resolved.type() instanceof Type.NullableType) return resolved;
        SymbolTable.LocalVariableSymbol decl = declarationOf(scope, name);
        return decl != null ? decl : resolved;
    }
/**
     * §353 (early-return narrowing): o ramo garante que o fluxo NÃO continua
     * apos o `if` — return/throw/continue/break; bloco cujo ULTIMO statement
     * sai; if/else em que AMBOS os ramos saem. Conservador: qualquer outra
     * forma (loop, ramo unico sem else) = NAO sai. Vive aqui por ser do
     * dominio de narrowing (regra 7); gate <=500 do StatementAnalyzer.
     */
    static boolean thenBranchExits(StatementNode s) {
        if (s instanceof ReturnStmt || s instanceof ThrowStmt) return true;
        if (s instanceof ContinueStmt || s instanceof BreakStmt) return true;
        if (s instanceof BlockStmt b) {
            return !b.statements().isEmpty()
                    && thenBranchExits(b.statements().get(b.statements().size() - 1));
        }
        if (s instanceof LockStmt l) return thenBranchExits(l.body()); // G240
        if (s instanceof IfStmt i) {
            return i.elseBranch() != null && thenBranchExits(i.thenBranch())
                    && thenBranchExits(i.elseBranch());
        }
        return false;
    }
}
