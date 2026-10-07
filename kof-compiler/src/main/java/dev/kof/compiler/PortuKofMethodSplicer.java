package dev.kof.compiler;

import dev.kof.compiler.lang.PortuKofMethodAliases;

import java.util.ArrayList;
import java.util.List;

/**
 * D-PORTUKOF unidade 3 (07/10) — splice receiver-aware dos metodos/campos de
 * superficie. Roda DEPOIS da analise semantica e ANTES do lowering: o tipo do
 * receiver ja e conhecido (cache de identidade do analyzer), entao `tamanho`
 * resolve para `length` em String/Array e `size` em colecoes — exatamente o
 * que os emissores nativos possuem (kof_string_length existe; kof_string_size
 * nao). Sem passo receiver-aware, alias sem tipo nunca e cross-target honesto.
 *
 * Mecanica (a unica permitida pelo contrato):
 *   1. percorre a AST ja canonicizada (unidades 1/2 normalizaram palavras e
 *      membros de namespace), de baixo para cima;
 *   2. classifica o receiver pelo TIPO REAL (PortuKofMethodCategories, que
 *      espelha a ordem de dispatch dos tybers);
 *   3. troca o nome de superficie pelo canonico via tabela gerada dos
 *      dispatchers reais (identidade p/ nome ja canonico — custo zero em EN);
 *   4. re-indexa os caches de identidade do analyzer (expressionTypes /
 *      resolvedMethods / resolvedConstructors) do no antigo para o novo, e
 *      so quando o no efetivamente mudou (no igual => caches intactos).
 *
 * NADA de lowering paralelo, NADA de runtime paralelo, NENHUM `if (portugues)`
 * espalhado: um unico passo de pipeline. Classes do usuario: categoria null —
 * um metodo proprio `contem` do usuario permanece `contem` (PARTE 5).
 */
public final class PortuKofMethodSplicer {

    private final CompilerDriver driver;
    private final SemanticAnalyzer sa;

    private PortuKofMethodSplicer(CompilerDriver driver, SemanticAnalyzer sa) {
        this.driver = driver;
        this.sa = sa;
    }

    public static CompilationUnitNode run(CompilerDriver driver, SemanticAnalyzer sa,
                                          CompilationUnitNode unit) {
        if (sa == null || unit == null) return unit;
        return new PortuKofMethodSplicer(driver, sa).unit(unit);
    }

    // ---------------- decls ----------------

    private CompilationUnitNode unit(CompilationUnitNode u) {
        List<AstNode> decls = new ArrayList<>();
        boolean changed = false;
        for (AstNode d : u.declarations()) {
            AstNode n = decl(d);
            if (n != d) changed = true;
            decls.add(n);
        }
        if (!changed) return u;
        return new CompilationUnitNode(u.position(), u.packageName(),
                new ArrayList<>(u.imports()), decls);
    }

    private AstNode decl(AstNode d) {
        return switch (d) {
            case FunctionDeclarationNode f -> {
                List<StatementNode> b = stmts(f.body());
                List<FormalParameterNode> ps = params(f.parameters());
                if (b == f.body() && ps == f.parameters()) yield f;
                yield new FunctionDeclarationNode(f.position(), f.modifiers(), f.returnType(),
                        f.name(), ps, f.thrownExceptions(), f.typeParameters(), b, f.annotations());
            }
            case ClassDeclarationNode c -> {
                List<AstNode> ms = members(c.members());
                if (ms == c.members()) yield c;
                yield new ClassDeclarationNode(c.position(), c.name(), c.modifiers(),
                        c.superClass(), c.interfaces(), c.typeParameters(), ms, c.annotations());
            }
            case RecordDeclarationNode r -> {
                List<AstNode> ms = members(r.members());
                if (ms == r.members()) yield r;
                yield new RecordDeclarationNode(r.position(), r.name(), r.modifiers(),
                        r.superClass(), r.interfaces(), r.typeParameters(), r.components(),
                        ms, r.annotations());
            }
            case InterfaceDeclarationNode i -> {
                List<AstNode> ms = members(i.members());
                if (ms == i.members()) yield i;
                yield new InterfaceDeclarationNode(i.position(), i.name(), i.modifiers(),
                        i.interfaces(), i.typeParameters(), ms, i.annotations());
            }
            case TestDeclarationNode t -> {
                List<StatementNode> b = stmts(t.body());
                if (b == t.body()) yield t;
                yield new TestDeclarationNode(t.position(), t.name(), t.tags(), b);
            }
            case ApplicationDeclarationNode a -> {
                List<StatementNode> os = stmts(a.onStart());
                List<StatementNode> sf = stmts(a.onShutdown());
                if (os == a.onStart() && sf == a.onShutdown()) yield a;
                yield new ApplicationDeclarationNode(a.position(), os, sf);
            }
            case FunctionDeclStmt fds -> {
                AstNode n = decl(fds.function());
                if (n == fds.function()) yield fds;
                yield new FunctionDeclStmt(fds.position(), (FunctionDeclarationNode) n);
            }
            default -> d;
        };
    }

    private List<FormalParameterNode> params(List<FormalParameterNode> ps) {
        List<FormalParameterNode> out = new ArrayList<>();
        boolean changed = false;
        for (FormalParameterNode f : ps) {
            ExpressionNode de = expr(f.defaultExpression());
            if (de != f.defaultExpression()) changed = true;
            out.add(de == f.defaultExpression() ? f : new FormalParameterNode(
                    f.position(), f.modifiers(), f.type(), f.name(), de, f.annotations()));
        }
        return changed ? out : ps;
    }

    private List<AstNode> members(List<? extends AstNode> ms) {
        List<AstNode> out = new ArrayList<>();
        boolean changed = false;
        for (AstNode m : ms) {
            AstNode n = decl(m);
            if (n != m) changed = true;
            out.add(n);
        }
        return changed ? out : nullSafeCast(ms);
    }

    @SuppressWarnings("unchecked")
    private static List<AstNode> nullSafeCast(List<? extends AstNode> ms) {
        return (List<AstNode>) ms;
    }

    private List<StatementNode> stmts(List<StatementNode> b) {
        if (b == null) return null;
        List<StatementNode> out = new ArrayList<>();
        boolean changed = false;
        for (StatementNode s : b) {
            StatementNode n = stmt(s);
            if (n != s) changed = true;
            out.add(n);
        }
        return changed ? out : b;
    }

    private StatementNode stmt(StatementNode s) {
        return switch (s) {
            case ExpressionStmt es -> {
                ExpressionNode e = expr(es.expression());
                yield e == es.expression() ? es : new ExpressionStmt(es.position(), e);
            }
            case ReturnStmt r -> {
                ExpressionNode v = expr(r.value());
                yield v == r.value() ? r : new ReturnStmt(r.position(), v);
            }
            case VarDeclStmt v -> {
                ExpressionNode i0 = expr(v.initializer());
                yield i0 == v.initializer() ? v : new VarDeclStmt(v.position(), v.type(),
                        v.name(), i0);
            }
            case IfStmt i -> {
                ExpressionNode c = expr(i.condition());
                StatementNode t = stmt(i.thenBranch());
                StatementNode e = i.elseBranch() != null ? stmt(i.elseBranch()) : null;
                yield c == i.condition() && t == i.thenBranch() && e == i.elseBranch() ? i
                        : new IfStmt(i.position(), c, t, e);
            }
            case WhileStmt w -> {
                ExpressionNode c = expr(w.condition());
                StatementNode b = stmt(w.body());
                yield c == w.condition() && b == w.body() ? w
                        : new WhileStmt(w.position(), c, b);
            }
            case DoWhileStmt d -> {
                ExpressionNode c = expr(d.condition());
                StatementNode b = stmt(d.body());
                yield c == d.condition() && b == d.body() ? d
                        : new DoWhileStmt(d.position(), c, b);
            }
            case ForStmt f -> {
                StatementNode init = f.init() != null ? stmt(f.init()) : null;
                ExpressionNode c = expr(f.condition());
                ExpressionNode u = expr(f.update());
                StatementNode b = stmt(f.body());
                yield init == f.init() && c == f.condition() && u == f.update()
                        && b == f.body() ? f
                        : new ForStmt(f.position(), init, c, u, b);
            }
            case ForInStmt fi -> {
                ExpressionNode col = expr(fi.collection());
                StatementNode b = stmt(fi.body());
                yield col == fi.collection() && b == fi.body() ? fi
                        : new ForInStmt(fi.position(), fi.varName(), col, b);
            }
            case BlockStmt b -> {
                List<StatementNode> ns = stmts(b.statements());
                yield ns == b.statements() ? b : new BlockStmt(b.position(), ns);
            }
            case ThrowStmt t -> {
                ExpressionNode e = expr(t.expression());
                yield e == t.expression() ? t : new ThrowStmt(t.position(), e);
            }
            case SpawnStmt sp -> {
                ExpressionNode e = expr(sp.expression());
                yield e == sp.expression() ? sp : new SpawnStmt(sp.position(), e);
            }
            case AssertStmt a -> {
                ExpressionNode c = expr(a.condition());
                yield c == a.condition() ? a : new AssertStmt(a.position(), c, a.message());
            }
            case SwitchStmt sw -> {
                ExpressionNode e = expr(sw.expression());
                List<dev.kof.compiler.SwitchCase> cs = cases(sw.cases());
                List<StatementNode> db = stmts(sw.defaultBody());
                yield e == sw.expression() && cs == sw.cases() && db == sw.defaultBody() ? sw
                        : new SwitchStmt(sw.position(), e, cs, db, sw.hasDefault());
            }
            case TryStmt t -> {
                List<StatementNode> tb = stmts(t.tryBody());
                List<CatchClause> cc = catches(t.catchClauses());
                List<StatementNode> fb = stmts(t.finallyBody());
                yield tb == t.tryBody() && cc == t.catchClauses() && fb == t.finallyBody() ? t
                        : new TryStmt(t.position(), tb, cc, fb);
            }
            case UsingStmt u -> {
                ExpressionNode i0 = expr(u.init());
                ExpressionNode cl = expr(u.closer());
                List<StatementNode> b = stmts(u.body());
                yield i0 == u.init() && cl == u.closer() && b == u.body() ? u
                        : new UsingStmt(u.position(), u.name(), i0, cl, b);
            }
            case FunctionDeclStmt fds -> {
                AstNode n = decl(fds.function());
                yield n == fds.function() ? fds : new FunctionDeclStmt(fds.position(),
                        (FunctionDeclarationNode) n);
            }
            default -> s;
        };
    }

    private List<dev.kof.compiler.SwitchCase> cases(List<dev.kof.compiler.SwitchCase> cs) {
        List<dev.kof.compiler.SwitchCase> out = new ArrayList<>();
        boolean changed = false;
        for (dev.kof.compiler.SwitchCase c : cs) {
            ExpressionNode v = expr(c.value());
            List<StatementNode> b = stmts(c.body());
            boolean ch = v != c.value() || b != c.body();
            if (ch) changed = true;
            out.add(ch ? new dev.kof.compiler.SwitchCase(c.position(), v, b) : c);
        }
        return changed ? out : cs;
    }

    private List<CatchClause> catches(List<CatchClause> cs) {
        List<CatchClause> out = new ArrayList<>();
        boolean changed = false;
        for (CatchClause c : cs) {
            List<StatementNode> b = stmts(c.body());
            if (b != c.body()) {
                changed = true;
                out.add(new CatchClause(c.position(), c.exceptionType(),
                        c.exceptionName(), b));
            } else out.add(c);
        }
        return changed ? out : cs;
    }

    // ---------------- exprs ----------------

    private ExpressionNode expr(ExpressionNode e) {
        if (e == null) return null;
        return switch (e) {
            case MethodCallExpr mc -> methodCall(mc);
            case FieldAccessExpr fa -> fieldAccess(fa);
            case BinaryExpr b -> {
                ExpressionNode l = expr(b.left());
                ExpressionNode r = expr(b.right());
                yield keep(b, l == b.left() && r == b.right() ? b
                        : new BinaryExpr(b.position(), b.operator(), l, r));
            }
            case UnaryExpr u -> {
                ExpressionNode o = expr(u.operand());
                yield keep(u, o == u.operand() ? u
                        : new UnaryExpr(u.position(), u.operator(), o, u.prefix()));
            }
            case AssignmentExpr a -> {
                ExpressionNode t = expr(a.target());
                ExpressionNode v = expr(a.value());
                yield keep(a, t == a.target() && v == a.value() ? a
                        : new AssignmentExpr(a.position(), t, a.operator(), v));
            }
            case ArrayAccessExpr aa -> {
                ExpressionNode r = expr(aa.receiver());
                ExpressionNode i = expr(aa.index());
                yield keep(aa, r == aa.receiver() && i == aa.index() ? aa
                        : new ArrayAccessExpr(aa.position(), r, i));
            }
            case IfExpr ie -> {
                ExpressionNode c = expr(ie.condition());
                ExpressionNode t = expr(ie.thenExpr());
                ExpressionNode f = expr(ie.elseExpr());
                yield keep(ie, c == ie.condition() && t == ie.thenExpr() && f == ie.elseExpr() ? ie
                        : new IfExpr(ie.position(), c, t, f));
            }
            case LambdaExpr l -> {
                List<FormalParameterNode> ps = params(l.parameters());
                List<StatementNode> b = stmts(l.body());
                yield keep(l, ps == l.parameters() && b == l.body() ? l
                        : new LambdaExpr(l.position(), ps, b));
            }
            case NewExpr ne -> {
                List<ExpressionNode> as = exprs(ne.arguments());
                NewExpr built = as == ne.arguments() ? ne
                        : new NewExpr(ne.position(), ne.typeName(), ne.typeArguments(), as);
                yield keepCtor(ne, built);
            }
            case NewArrayExpr na -> {
                ExpressionNode sz = expr(na.size());
                List<ExpressionNode> md = exprs(na.moreDims());
                yield keep(na, sz == na.size() && md == na.moreDims() ? na
                        : new NewArrayExpr(na.position(), na.elementType(), sz, md));
            }
            case SwitchExpr se -> {
                ExpressionNode v = expr(se.expression());
                List<dev.kof.compiler.SwitchExprCase> cs = switchCases(se.cases());
                ExpressionNode dv = expr(se.defaultValue());
                yield keep(se, v == se.expression() && cs == se.cases() && dv == se.defaultValue()
                        ? se : new SwitchExpr(se.position(), v, cs, dv));
            }
            case PatternExpr pe -> {
                ExpressionNode g = expr(pe.guard());
                yield keep(pe, g == pe.guard() ? pe
                        : new PatternExpr(pe.position(), pe.typeName(), pe.varName(),
                                pe.fieldVars(), g));
            }
            case QueryDslExpr q -> {
                ExpressionNode db = expr(q.dbArg());
                List<ExpressionNode> ws = exprs(q.whereClauses());
                List<ExpressionNode> ob = exprs(q.orderByFields());
                ExpressionNode lim = expr(q.limit());
                yield keep(q, db == q.dbArg() && ws == q.whereClauses()
                        && ob == q.orderByFields() && lim == q.limit() ? q
                        : new QueryDslExpr(q.position(), q.entityType(), db, ws, ob,
                                q.orderByDirs(), lim));
            }
            default -> e;
        };
    }

    private List<ExpressionNode> exprs(List<ExpressionNode> es) {
        List<ExpressionNode> out = new ArrayList<>();
        boolean changed = false;
        for (ExpressionNode e : es) {
            ExpressionNode n = expr(e);
            if (n != e) changed = true;
            out.add(n);
        }
        return changed ? out : es;
    }

    private List<dev.kof.compiler.SwitchExprCase> switchCases(
            List<dev.kof.compiler.SwitchExprCase> cs) {
        List<dev.kof.compiler.SwitchExprCase> out = new ArrayList<>();
        boolean changed = false;
        for (dev.kof.compiler.SwitchExprCase c : cs) {
            ExpressionNode v = expr(c.value());
            ExpressionNode b = expr(c.body());
            boolean ch = v != c.value() || b != c.body();
            if (ch) changed = true;
            out.add(ch ? new dev.kof.compiler.SwitchExprCase(c.position(), v, b) : c);
        }
        return changed ? out : cs;
    }

    private ExpressionNode methodCall(MethodCallExpr mc) {
        ExpressionNode recv = expr(mc.receiver());
        List<ExpressionNode> args = exprs(mc.arguments());
        String recorded = sa.surfaceMethodAlias(mc);
        String name = recorded != null ? recorded : mc.methodName();
        boolean changed = recv != mc.receiver() || args != mc.arguments()
                || !name.equals(mc.methodName());
        if (!changed) return mc;
        MethodCallExpr out = new MethodCallExpr(mc.position(), recv, name,
                mc.typeArguments(), args);
        if (!name.equals(mc.methodName())) sa.spliceMethodCache(mc, out);
        return out;
    }

    private ExpressionNode fieldAccess(FieldAccessExpr fa) {
        ExpressionNode recv = expr(fa.receiver());
        String recorded = sa.surfaceFieldAlias(fa);
        String name = recorded != null ? recorded : fa.fieldName();
        boolean changed = recv != fa.receiver() || !name.equals(fa.fieldName());
        if (!changed) return fa;
        FieldAccessExpr out = new FieldAccessExpr(fa.position(), recv, name);
        return keep(fa, out);
    }

    /** Hook de ENTRADA dos tybers semanticos (D-PORTUKOF u3): com o tipo do
     *  receiver ja resolvido, o nome de superficie vira o canonico APENAS na
     *  copia local usada para o dispatch de tipagem, e a decisao (no original
     *  -> nome canonico) e REGISTRADA no analyzer. O splice pos-analise aplica
     *  os registros na AST (idempotente: nomes canonicos ficam intactos;
     *  classes de usuario = categoria null; nada registrado). */
    public static MethodCallExpr canonicalCall(SemanticAnalyzer sa, Type recvType,
                                               CompilationUnitNode unit, MethodCallExpr mc) {
        String c = canonicalize(mc.methodName(), recvType, unit);
        if (c.equals(mc.methodName())) return mc;
        sa.recordSurfaceMethod(mc, c);
        return new MethodCallExpr(mc.position(), mc.receiver(), c,
                mc.typeArguments(), mc.arguments());
    }

    public static FieldAccessExpr canonicalField(SemanticAnalyzer sa, Type recvType,
                                                 CompilationUnitNode unit, FieldAccessExpr fa) {
        String c = canonicalize(fa.fieldName(), recvType, unit);
        if (c.equals(fa.fieldName())) return fa;
        sa.recordSurfaceField(fa, c);
        return new FieldAccessExpr(fa.position(), fa.receiver(), c);
    }

    public static String canonicalize(String name, Type recvType, CompilationUnitNode unit) {
        PortuKofMethodCategories cat = PortuKofMethodCategories.of(recvType, unit);
        if (cat == null) return name;
        return PortuKofMethodAliases.canonicalize(cat.name(), name);
    }

    /** Re-indexa o cache de tipos quando o no mudou de identidade. */
    private ExpressionNode keep(ExpressionNode oldNode, ExpressionNode newNode) {
        if (newNode != oldNode) sa.spliceTypeCache(oldNode, newNode);
        return newNode;
    }

    private ExpressionNode keepCtor(NewExpr oldNode, NewExpr newNode) {
        if (newNode != oldNode) {
            sa.spliceTypeCache(oldNode, newNode);
            sa.spliceCtorCache(oldNode, newNode);
        }
        return newNode;
    }
}
