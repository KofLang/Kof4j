package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lei do sombreamento no nível de FUNÇÃO (#931/#932, mesmo princípio do §243
 * que vale para CLASSES): uma função top-level ou `extern` DECLARADA pelo
 * programa vence qualquer builtin interceptado pelo nome — nunca o contrário,
 * e nunca em silêncio. Antes da lei, um `writeFile(String, String)` do usuário
 * ou um `extern poll(Long, Long, Int)` eram roubados pelo branch de nome fixo
 * do builtin (`kof_write_file` / o typer aridade-cego do `poll`) em TODOS os
 * alvos — JVM, Native e JS compilação limpa, execução errada.
 *
 * <p>Um só oráculo: o helper único alimenta typer semântico, typer de emit e
 * lowerer; o caminho de chamada a função declarada (bare call / extern) já
 * existe a jusante e faz o dispatch correto quando os branches de builtin
 * saem da frente.
 */
final class KofShadowing {

    private KofShadowing() {}

    /** O programa declara função top-level OU `extern` com este nome. */
    static boolean userDeclares(SemanticAnalyzer sa, String name) {
        return sa != null && sa.unit() != null && declares(sa.unit(), name);
    }

    /** Mesma pergunta no lado do emit/lowering (driver). */
    static boolean userDeclares(CompilerDriver driver, String name) {
        return driver != null && driver.currentUnit != null && declares(driver.currentUnit, name);
    }

    /**
     * §778: inside a class, a method of the class (or its hierarchy) called
     * without {@code this.} wins over a builtin of the same name, as an
     * implicit-this call does in Java — {@code query(sql)} inside
     * {@code class Db} is {@code this.query(sql)}, never the web-context
     * {@code query}.
     *
     * <p>#1019 regression (measured, `lab` green): the class only shadows when
     * one of its methods is APPLICABLE to the arguments. A class that declares
     * {@code println(Int)} and calls {@code println("[LOG] " + msg)} must reach
     * the builtin {@code println(String)}, exactly as Java overload resolution
     * does; a name-only test sent the {@code String} to the {@code Int} method
     * (SEM014) and broke {@code CoreRegressionE2ETest#instanceMethodNamedPrintOrPrintlnJvm}.
     */
    static boolean enclosingClassDeclares(SemanticAnalyzer sa, String name,
            List<ArgShape> args) {
        if (args == null) return false;
        SymbolTable.Symbol s = enclosingClassMember(sa, name);
        if (s == null) return false;
        return hasApplicableOverload(s, args);
    }

    /** Same question on the lowering side; {@code owner} is the class being lowered. */
    static boolean enclosingClassDeclares(CompilerDriver driver, String owner, String name,
            List<ArgShape> args) {
        if (args == null) return false;
        SymbolTable.Symbol s = enclosingClassMember(driver, owner, name);
        return s != null && hasApplicableOverload(s, args);
    }

    private static SymbolTable.Symbol enclosingClassMember(SemanticAnalyzer sa, String name) {
        return sa != null && sa.currentClassName() != null && !sa.currentClassName().isEmpty()
                ? MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), name) : null;
    }

    private static SymbolTable.Symbol enclosingClassMember(CompilerDriver driver, String owner, String name) {
        if (driver == null || driver.semanticAnalyzer == null || owner == null || owner.isEmpty()) return null;
        String simple = owner.substring(owner.lastIndexOf('/') + 1);
        return driver.semanticAnalyzer.getClass(simple) != null
                ? driver.semanticAnalyzer.resolveInHierarchy(simple, name) : null;
    }

    /**
     * One argument as far as overload applicability is concerned: its static
     * {@code Type} plus whether it is a bare integer/float LITERAL (which the
     * JVM narrows to {@code Byte}…{@code Double} freely, so it is applicable to
     * every primitive parameter). {@code unknown} means the argument could not
     * be typed in this position — it never blocks the class face.
     */
    record ArgShape(Type type, boolean literal) {}

    /**
     * The shapes of the call's arguments for the SEMANTIC face. An already
     * inferred type is reused; otherwise the argument is typed in {@code scope}.
     *
     * <p>{@code inferArguments == false} is for the builtin branches whose
     * argument is deliberately NOT inferred (the builtin {@code ring1(fn)} takes
     * a bare top-level function on purpose): there the shapes stay
     * {@code unknown}, which never blocks the class face — a proactive
     * inference turned `ring1(task)` into SEM011 and broke
     * {@code RingPrivilegeE2ETest}.
     */
    static List<ArgShape> shapes(SemanticAnalyzer sa, List<? extends AstNode> arguments,
            SymbolTable scope, boolean inferArguments) {
        List<ArgShape> out = new ArrayList<>();
        for (AstNode a : arguments) {
            Type t = a instanceof ExpressionNode e
                    ? (inferArguments ? SemExpressionTyper.inferType(sa, e, scope) : sa.getExpressionType(e))
                    : null;
            out.add(new ArgShape(t, isNumericLiteral(a)));
        }
        return out;
    }

    /** Same on the lowering side, where the arguments are typed against the locals. */
    static List<ArgShape> shapes(CompilerDriver driver, List<? extends AstNode> arguments, List<IRLocalVariable> locals) {
        List<ArgShape> out = new ArrayList<>();
        for (AstNode a : arguments) {
            Type t = a instanceof ExpressionNode e ? ExpressionTyper.inferExprType(driver, e, locals) : null;
            out.add(new ArgShape(t, isNumericLiteral(a)));
        }
        return out;
    }

    /** A numeric literal narrows to any primitive parameter (JVM §5.2). */
    private static boolean isNumericLiteral(AstNode a) {
        return a instanceof LiteralExpr le && (le.kind() == ConcreteLiteralKind.INT
                || le.kind() == ConcreteLiteralKind.LONG || le.kind() == ConcreteLiteralKind.FLOAT
                || le.kind() == ConcreteLiteralKind.DOUBLE);
    }

    private static boolean hasApplicableOverload(SymbolTable.Symbol s, List<ArgShape> args) {
        List<SymbolTable.MethodSymbol> methods = new ArrayList<>();
        if (s instanceof SymbolTable.MethodSymbol m) methods.add(m);
        else if (s instanceof SymbolTable.MethodSet set) methods.addAll(set.methods());
        if (methods.isEmpty()) return false;
        for (SymbolTable.MethodSymbol m : methods) {
            if (MethodSymbolFits.fits(m.parameterTypes(), args)) return true;
        }
        return false;
    }

    private static boolean declares(CompilationUnitNode unit, String name) {
        for (AstNode d : unit.declarations()) {
            if (d instanceof FunctionDeclarationNode fn && fn.name().equals(name)) return true;
            if (d instanceof ExternalFunctionNode ext && ext.name().equals(name)) return true;
        }
        return false;
    }
}
