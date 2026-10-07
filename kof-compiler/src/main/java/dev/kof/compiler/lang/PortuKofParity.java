package dev.kof.compiler.lang;

import dev.kof.compiler.AstNode;
import dev.kof.compiler.CompilationUnitNode;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.FormalParameterNode;
import dev.kof.compiler.IdentifierExpr;
import dev.kof.compiler.MethodCallExpr;
import dev.kof.compiler.StatementNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D-PORTUKOF (07/10) — normalização de paridade pós-parse: o AST de uma
 * superfície (PortuKof) é reescrito para os SÍMBOLOS CANÔNICOS do Kof. Alias
 * ≠ implementação: `escreva` vira `print`, `arquivo.ler` vira `file.read` —
 * o MESMO símbolo, a MESMA implementação, o MESMO IR abaixo.
 *
 * <p>Domínio fechado e determinístico (PARTE 8/27): só nomes de BUILTINS de
 * chamada nua e RAÍZES de namespace stdlib/biblioteca (tabela do perfil) são
 * reescritos. Identificadores do usuário NUNCA são tocados (PARTE 5), e a
 * reescrita só ocorre na POSIÇÃO canônica do símbolo (receiver nulo p/
 * builtin; receiver-identificador p/ namespace) — um método de classe do
 * usuário chamado `escreva` ou `ler` permanece intocado.
 */
public final class PortuKofParity {

    private PortuKofParity() {}

    /** Rewrites the parsed unit of a non-canonical surface to canonical symbols. */
    public static CompilationUnitNode normalize(LanguageProfile profile, CompilationUnitNode unit) {
        if (profile == LanguageProfile.KOF) return unit;
        Map<String, String> al = profile.symbolAliases();
        List<AstNode> decls = new ArrayList<>();
        for (AstNode d : unit.declarations()) decls.add(decl(profile, al, d));
        return new CompilationUnitNode(unit.position(), unit.packageName(),
                new ArrayList<>(unit.imports()), decls);
    }

    private static AstNode decl(LanguageProfile p, Map<String, String> al, AstNode d) {
        return switch (d) {
            case dev.kof.compiler.FunctionDeclarationNode f -> new dev.kof.compiler.FunctionDeclarationNode(
                    f.position(), f.modifiers(), canonType(f.returnType()),
                    canonName(al, f.name()), params(p, al, f.parameters()),
                    f.thrownExceptions(), f.typeParameters(), body(p, al, f.body()), f.annotations());
            case dev.kof.compiler.ClassDeclarationNode c -> new dev.kof.compiler.ClassDeclarationNode(
                    c.position(), c.name(), c.modifiers(), c.superClass(), c.interfaces(),
                    c.typeParameters(), members(p, al, c.members()), c.annotations());
            case dev.kof.compiler.RecordDeclarationNode r -> new dev.kof.compiler.RecordDeclarationNode(
                    r.position(), r.name(), r.modifiers(), r.superClass(), r.interfaces(),
                    r.typeParameters(), r.components(), members(p, al, r.members()), r.annotations());
            case dev.kof.compiler.InterfaceDeclarationNode i -> new dev.kof.compiler.InterfaceDeclarationNode(
                    i.position(), i.name(), i.modifiers(), i.interfaces(), i.typeParameters(),
                    members(p, al, i.members()), i.annotations());
            case dev.kof.compiler.EntityDeclarationNode e -> e;
            case dev.kof.compiler.EnumDeclarationNode e -> e;
            case dev.kof.compiler.TestDeclarationNode t -> new dev.kof.compiler.TestDeclarationNode(
                    t.position(), t.name(), t.tags(), body(p, al, t.body()));
            case dev.kof.compiler.ApplicationDeclarationNode a -> new dev.kof.compiler.ApplicationDeclarationNode(
                    a.position(), stmts(p, al, a.onStart()), stmts(p, al, a.onShutdown()));
            case dev.kof.compiler.InfraDeclarationNode i -> i;
            case dev.kof.compiler.ExternalFunctionNode x -> x;
            case dev.kof.compiler.ForeignModuleNode m -> m;
            default -> d;
        };
    }

    private static List<FormalParameterNode> params(LanguageProfile p, Map<String, String> al,
                                                    List<FormalParameterNode> ps) {
        List<FormalParameterNode> out = new ArrayList<>();
        for (FormalParameterNode f : ps) out.add(new FormalParameterNode(
                f.position(), f.modifiers(), canonType(f.type()), f.name(),
                expr(p, al, f.defaultExpression()), f.annotations()));
        return out;
    }

    private static List<AstNode> members(LanguageProfile p, Map<String, String> al,
                                         List<? extends AstNode> ms) {
        List<AstNode> out = new ArrayList<>();
        for (AstNode m : ms) out.add(decl(p, al, m));
        return out;
    }

    private static List<StatementNode> body(LanguageProfile p, Map<String, String> al,
                                            List<StatementNode> b) {
        return stmts(p, al, b);
    }

    private static List<StatementNode> stmts(LanguageProfile p, Map<String, String> al,
                                             List<StatementNode> b) {
        if (b == null) return b;
        List<StatementNode> out = new ArrayList<>();
        for (StatementNode s : b) out.add(stmt(p, al, s));
        return out;
    }

    private static StatementNode stmt(LanguageProfile p, Map<String, String> al, StatementNode s) {
        return switch (s) {
            case dev.kof.compiler.ExpressionStmt es -> new dev.kof.compiler.ExpressionStmt(
                    es.position(), expr(p, al, es.expression()));
            case dev.kof.compiler.ReturnStmt r -> new dev.kof.compiler.ReturnStmt(
                    r.position(), expr(p, al, r.value()));
            case dev.kof.compiler.VarDeclStmt v -> new dev.kof.compiler.VarDeclStmt(
                    v.position(), canonType(v.type()), v.name(), expr(p, al, v.initializer()));
            case dev.kof.compiler.IfStmt i -> new dev.kof.compiler.IfStmt(i.position(),
                    expr(p, al, i.condition()), stmt(p, al, i.thenBranch()),
                    i.elseBranch() != null ? stmt(p, al, i.elseBranch()) : null);
            case dev.kof.compiler.WhileStmt w -> new dev.kof.compiler.WhileStmt(w.position(),
                    expr(p, al, w.condition()), stmt(p, al, w.body()));
            case dev.kof.compiler.DoWhileStmt d -> new dev.kof.compiler.DoWhileStmt(d.position(),
                    expr(p, al, d.condition()), stmt(p, al, d.body()));
            case dev.kof.compiler.ForStmt f -> new dev.kof.compiler.ForStmt(f.position(),
                    f.init() != null ? stmt(p, al, f.init()) : null, expr(p, al, f.condition()),
                    expr(p, al, f.update()), stmt(p, al, f.body()));
            case dev.kof.compiler.ForInStmt fi -> new dev.kof.compiler.ForInStmt(fi.position(),
                    fi.varName(), expr(p, al, fi.collection()), stmt(p, al, fi.body()));
            case dev.kof.compiler.BlockStmt b -> new dev.kof.compiler.BlockStmt(
                    b.position(), stmts(p, al, b.statements()));
            case dev.kof.compiler.ThrowStmt t -> new dev.kof.compiler.ThrowStmt(
                    t.position(), expr(p, al, t.expression()));
            case dev.kof.compiler.SpawnStmt sp -> new dev.kof.compiler.SpawnStmt(
                    sp.position(), expr(p, al, sp.expression()));
            case dev.kof.compiler.AssertStmt a -> new dev.kof.compiler.AssertStmt(
                    a.position(), expr(p, al, a.condition()), a.message());
            case dev.kof.compiler.SwitchStmt sw -> new dev.kof.compiler.SwitchStmt(sw.position(),
                    expr(p, al, sw.expression()), cases(p, al, sw.cases()),
                    stmts(p, al, sw.defaultBody()), sw.hasDefault());
            case dev.kof.compiler.TryStmt t -> new dev.kof.compiler.TryStmt(t.position(),
                    stmts(p, al, t.tryBody()), catches(p, al, t.catchClauses()),
                    stmts(p, al, t.finallyBody()));
            case dev.kof.compiler.UsingStmt u -> new dev.kof.compiler.UsingStmt(u.position(),
                    u.name(), expr(p, al, u.init()), expr(p, al, u.closer()),
                    stmts(p, al, u.body()));
            case dev.kof.compiler.FunctionDeclStmt fds -> new dev.kof.compiler.FunctionDeclStmt(
                    fds.position(), (dev.kof.compiler.FunctionDeclarationNode)
                            decl(p, al, fds.function()));
            default -> s;
        };
    }

    private static List<dev.kof.compiler.SwitchCase> cases(LanguageProfile p, Map<String, String> al,
                                                           List<dev.kof.compiler.SwitchCase> cs) {
        List<dev.kof.compiler.SwitchCase> out = new ArrayList<>();
        for (dev.kof.compiler.SwitchCase c : cs) out.add(new dev.kof.compiler.SwitchCase(
                c.position(), expr(p, al, c.value()), stmts(p, al, c.body())));
        return out;
    }

    private static List<dev.kof.compiler.CatchClause> catches(LanguageProfile p, Map<String, String> al,
                                                              List<dev.kof.compiler.CatchClause> cs) {
        List<dev.kof.compiler.CatchClause> out = new ArrayList<>();
        for (dev.kof.compiler.CatchClause c : cs) out.add(new dev.kof.compiler.CatchClause(
                c.position(), c.exceptionType(), c.exceptionName(), stmts(p, al, c.body())));
        return out;
    }

    private static ExpressionNode expr(LanguageProfile p, Map<String, String> al, ExpressionNode e) {
        if (e == null) return null;
        return switch (e) {
            case dev.kof.compiler.MethodCallExpr mc -> methodCall(p, al, mc);
            case dev.kof.compiler.BinaryExpr b -> new dev.kof.compiler.BinaryExpr(
                    b.position(), b.operator(), expr(p, al, b.left()), expr(p, al, b.right()));
            case dev.kof.compiler.UnaryExpr u -> new dev.kof.compiler.UnaryExpr(
                    u.position(), u.operator(), expr(p, al, u.operand()), u.prefix());
            case dev.kof.compiler.AssignmentExpr a -> new dev.kof.compiler.AssignmentExpr(
                    a.position(), expr(p, al, a.target()), a.operator(), expr(p, al, a.value()));
            case dev.kof.compiler.ArrayAccessExpr aa -> new dev.kof.compiler.ArrayAccessExpr(
                    aa.position(), expr(p, al, aa.receiver()), expr(p, al, aa.index()));
            case dev.kof.compiler.FieldAccessExpr fa -> new dev.kof.compiler.FieldAccessExpr(
                    fa.position(), expr(p, al, fa.receiver()), fa.fieldName());
            case dev.kof.compiler.IfExpr ie -> new dev.kof.compiler.IfExpr(ie.position(),
                    expr(p, al, ie.condition()), expr(p, al, ie.thenExpr()),
                    expr(p, al, ie.elseExpr()));
            case dev.kof.compiler.LambdaExpr l -> new dev.kof.compiler.LambdaExpr(
                    l.position(), params(p, al, l.parameters()), stmts(p, al, l.body()));
            case dev.kof.compiler.LiteralExpr le -> le;
            case dev.kof.compiler.IdentifierExpr id -> id;
            case dev.kof.compiler.NewExpr ne -> new dev.kof.compiler.NewExpr(
                    ne.position(), ne.typeName(), ne.typeArguments(),
                    exprs(p, al, ne.arguments()));
            case dev.kof.compiler.NewArrayExpr na -> new dev.kof.compiler.NewArrayExpr(
                    na.position(), canonType(na.elementType()), expr(p, al, na.size()),
                    exprs(p, al, na.moreDims()));
            case dev.kof.compiler.SwitchExpr se -> new dev.kof.compiler.SwitchExpr(se.position(),
                    expr(p, al, se.expression()), switchCases(p, al, se.cases()),
                    expr(p, al, se.defaultValue()));
            case dev.kof.compiler.PatternExpr pe -> new dev.kof.compiler.PatternExpr(
                    pe.position(), pe.typeName(), pe.varName(), pe.fieldVars(),
                    expr(p, al, pe.guard()));
            case dev.kof.compiler.QueryDslExpr q -> new dev.kof.compiler.QueryDslExpr(
                    q.position(), q.entityType(), expr(p, al, q.dbArg()),
                    exprs(p, al, q.whereClauses()), exprs(p, al, q.orderByFields()),
                    q.orderByDirs(), expr(p, al, q.limit()));
            default -> e;
        };
    }

    private static List<ExpressionNode> exprs(LanguageProfile p, Map<String, String> al,
                                              List<ExpressionNode> es) {
        List<ExpressionNode> out = new ArrayList<>();
        for (ExpressionNode e : es) out.add(expr(p, al, e));
        return out;
    }

    private static List<dev.kof.compiler.SwitchExprCase> switchCases(LanguageProfile p,
                                                                      Map<String, String> al,
                                                                      List<dev.kof.compiler.SwitchExprCase> cs) {
        List<dev.kof.compiler.SwitchExprCase> out = new ArrayList<>();
        for (dev.kof.compiler.SwitchExprCase c : cs) out.add(new dev.kof.compiler.SwitchExprCase(
                c.position(), expr(p, al, c.value()), expr(p, al, c.body())));
        return out;
    }

    private static ExpressionNode methodCall(LanguageProfile p, Map<String, String> al,
                                             MethodCallExpr mc) {
        ExpressionNode recv = mc.receiver() == null ? null : expr(p, al, mc.receiver());
        String name = mc.methodName();
        if (mc.receiver() == null) {
            String canon = al.get(name);
            if (canon != null && PortuKofVocabulary.builtins().containsValue(name)) name = canon;
        } else if (recv instanceof IdentifierExpr id) {
            String canonNs = al.get(id.name());
            if (canonNs != null && PortuKofVocabulary.namespaces().containsValue(id.name())) {
                recv = new IdentifierExpr(id.position(), canonNs);
                String canonMember = PortuKofVocabulary.memberAliases(canonNs).get(name);
                if (canonMember != null) name = canonMember;
            }
        }
        return new MethodCallExpr(mc.position(), recv, name, mc.typeArguments(),
                exprs(p, al, mc.arguments()));
    }

    private static String canonName(Map<String, String> al, String n) {
        // SÓ a entrada canônica é renomeada (principal → main). Nomes de
        // usuário NUNCA são tocados (PARTE 5) — a tabela de namespaces não
        // se aplica a declarações.
        return "principal".equals(n) ? "main" : n;
    }

    private static String canonType(String t) {
        return t == null ? null : t;
    }
}
