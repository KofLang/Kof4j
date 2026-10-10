package dev.kof.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Desugar de testes (`test "nome" {}`) e lifecycle (`application { }`) do
 * CompilerDriver. Puro — recebe o estado (discoveredTests etc.) por parâmetro.
 */
public final class CompilerDesugar {

    private CompilerDesugar() {}

    static CompilationUnitNode desugarTests(CompilationUnitNode unit,
                                            List<CompilerDriver.TestInfo> discoveredTests,
                                            boolean testHarnessMode,
                                            String currentSourceName,
                                            DiagnosticCollector diagnostics) {
        discoveredTests.clear();
        java.util.List<AstNode> decls = new ArrayList<>();
        java.util.List<TestHarnessBuilder.Entry> harnessEntries = new ArrayList<>();
        String setupName = null, teardownName = null;
        String beforeAllName = null, afterAllName = null;
        AstNode setupAliasConflict = null, teardownAliasConflict = null;
        boolean sawSetup = false, sawBeforeEach = false;
        boolean sawTeardown = false, sawAfterEach = false;
        int ti = 0;
        for (AstNode d : unit.declarations()) {
            if (d instanceof TestDeclarationNode t) {
                String fn = "kof_test_" + ti++;
                discoveredTests.add(new CompilerDriver.TestInfo(t.name(), fn, List.copyOf(t.tags())));
                harnessEntries.add(new TestHarnessBuilder.Entry(t.name(), fn, t.tags()));
                decls.add(new FunctionDeclarationNode(t.position(), List.of(), "void", fn,
                        List.of(), List.of(), List.of(), t.body()));
            } else {
                decls.add(d);
            }
            if (d instanceof FunctionDeclarationNode f && f.parameters().isEmpty()
                    && "void".equals(f.returnType())) {
                if ("setup".equals(f.name())) {
                    if (sawBeforeEach) setupAliasConflict = d;
                    sawSetup = true;
                    setupName = f.name();
                }
                if ("beforeEach".equals(f.name())) {
                    if (sawSetup) setupAliasConflict = d;
                    sawBeforeEach = true;
                    setupName = f.name();
                }
                if ("teardown".equals(f.name())) {
                    if (sawAfterEach) teardownAliasConflict = d;
                    sawTeardown = true;
                    teardownName = f.name();
                }
                if ("afterEach".equals(f.name())) {
                    if (sawTeardown) teardownAliasConflict = d;
                    sawAfterEach = true;
                    teardownName = f.name();
                }
                if ("beforeAll".equals(f.name())) beforeAllName = f.name();
                if ("afterAll".equals(f.name())) afterAllName = f.name();
            }
        }
        if (testHarnessMode && !discoveredTests.isEmpty() && diagnostics != null) {
            if (setupAliasConflict != null) {
                diagnostics.error(setupAliasConflict,
                        "ambiguous test lifecycle: both `setup()` and `beforeEach()` are declared "
                                + "— they are the same hook; keep only one", "TEST001");
            }
            if (teardownAliasConflict != null) {
                diagnostics.error(teardownAliasConflict,
                        "ambiguous test lifecycle: both `teardown()` and `afterEach()` are declared "
                                + "— they are the same hook; keep only one", "TEST001");
            }
        }
        if (testHarnessMode && !discoveredTests.isEmpty()) {
            java.util.List<AstNode> withHarness = new ArrayList<>();
            for (AstNode d : decls) {
                if (d instanceof FunctionDeclarationNode f && "main".equals(f.name())) {
                    continue; // kof test roda só os testes (como cargo test)
                }
                withHarness.add(d);
            }
            withHarness.add(TestHarnessBuilder.build(harnessEntries, currentSourceName,
                    setupName, teardownName, System.getProperty("kof.test.tag"),
                    beforeAllName, afterAllName));
            decls = withHarness;
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), unit.imports(),
                java.util.Collections.unmodifiableList(decls));
    }

    /**
     * D-SCOPED-RESOURCES-GO slice 1 — {@code using (name = init, closer) { body }}
     * lowers to {@code { var name = init; try { body } finally { closer } }}.
     * Runs FIRST in {@code DesugarSteps.defaults()}: downstream steps
     * (tests/application/infra/nested-functions) see plain try/finally, and
     * escape discipline stays with the memory-safety passes (no ownership here).
     * Programs without {@code using} return the unit untouched (freeze rule 3).
     */
    static CompilationUnitNode desugarUsing(CompilationUnitNode unit) {
        boolean any = false;
        for (AstNode d : unit.declarations()) {
            if (d instanceof FunctionDeclarationNode f && containsUsing(f.body())) {
                any = true;
                break;
            }
            if (d instanceof TestDeclarationNode t && containsUsing(t.body())) {
                any = true;
                break;
            }
        }
        if (!any) return unit;
        java.util.List<AstNode> decls = new ArrayList<>();
        for (AstNode d : unit.declarations()) {
            if (d instanceof FunctionDeclarationNode f) {
                decls.add(new FunctionDeclarationNode(f.position(), f.modifiers(), f.returnType(),
                        f.name(), f.parameters(), f.thrownExceptions(), f.typeParameters(),
                        rewriteUsing(f.body()), f.annotations()));
            } else if (d instanceof TestDeclarationNode t) {
                decls.add(new TestDeclarationNode(t.position(), t.name(), t.tags(),
                        rewriteUsing(t.body())));
            } else {
                decls.add(d);
            }
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), unit.imports(),
                java.util.Collections.unmodifiableList(decls));
    }

    private static boolean containsUsing(java.util.List<StatementNode> body) {
        if (body == null) return false;
        for (StatementNode s : body) {
            if (s instanceof UsingStmt) return true;
            if (s instanceof BlockStmt b && containsUsing(b.statements())) return true;
            if (s instanceof IfStmt is && (containsUsing(List.of(is.thenBranch()))
                    || (is.elseBranch() != null && containsUsing(List.of(is.elseBranch()))))) return true;
            if (s instanceof WhileStmt ws && containsUsing(List.of(ws.body()))) return true;
            if (s instanceof DoWhileStmt ds && containsUsing(List.of(ds.body()))) return true;
            if (s instanceof ForStmt fs && (containsUsing(List.of(fs.init()))
                    || containsUsing(List.of(fs.body())))) return true;
            if (s instanceof ForInStmt fis && containsUsing(List.of(fis.body()))) return true;
            if (s instanceof TryStmt ts) {
                if (containsUsing(ts.tryBody())) return true;
                for (CatchClause c : ts.catchClauses()) {
                    if (containsUsing(c.body())) return true;
                }
                if (ts.finallyBody() != null && containsUsing(ts.finallyBody())) return true;
            }
            if (s instanceof SwitchStmt sw) {
                for (SwitchCase c : sw.cases()) {
                    if (containsUsing(c.body())) return true;
                }
                if (sw.defaultBody() != null && containsUsing(sw.defaultBody())) return true;
            }
            if (s instanceof FunctionDeclStmt fn
                    && containsUsing(fn.function().body())) return true;
        }
        return false;
    }

    private static java.util.List<StatementNode> rewriteUsing(java.util.List<StatementNode> body) {
        java.util.List<StatementNode> out = new ArrayList<>();
        for (StatementNode s : body) {
            out.add(rewriteUsingOne(s));
        }
        return out;
    }

    private static StatementNode rewriteUsingOne(StatementNode s) {
        if (s instanceof UsingStmt u) {
            // { var name = init; try { body } finally { closer } } — the binding
            // lives in the generated block: visible to body+closer, gone after.
            java.util.List<StatementNode> block = new ArrayList<>();
            block.add(new VarDeclStmt(u.position(), "var", u.name(), u.init()));
            block.add(new TryStmt(u.position(), rewriteUsing(u.body()), List.of(),
                    List.of(new ExpressionStmt(u.closer().position(), u.closer()))));
            return new BlockStmt(u.position(), block);
        }
        if (s instanceof BlockStmt b) {
            return new BlockStmt(b.position(), rewriteUsing(b.statements()));
        }
        if (s instanceof IfStmt is) {
            return new IfStmt(is.position(), is.condition(), rewriteUsingOne(is.thenBranch()),
                    is.elseBranch() == null ? null : rewriteUsingOne(is.elseBranch()));
        }
        if (s instanceof WhileStmt ws) {
            return new WhileStmt(ws.position(), ws.condition(), rewriteUsingOne(ws.body()));
        }
        if (s instanceof DoWhileStmt ds) {
            return new DoWhileStmt(ds.position(), ds.condition(), rewriteUsingOne(ds.body()));
        }
        if (s instanceof ForStmt fs) {
            return new ForStmt(fs.position(), rewriteUsingOne(fs.init()), fs.condition(),
                    fs.update(), rewriteUsingOne(fs.body()));
        }
        if (s instanceof ForInStmt fis) {
            return new ForInStmt(fis.position(), fis.varName(), fis.collection(),
                    rewriteUsingOne(fis.body()));
        }
        if (s instanceof TryStmt ts) {
            java.util.List<CatchClause> catches = new ArrayList<>();
            for (CatchClause c : ts.catchClauses()) {
                catches.add(new CatchClause(c.position(), c.exceptionType(), c.exceptionName(),
                        rewriteUsing(c.body())));
            }
            return new TryStmt(ts.position(), rewriteUsing(ts.tryBody()), catches,
                    ts.finallyBody() == null ? null : rewriteUsing(ts.finallyBody()));
        }
        if (s instanceof SwitchStmt sw) {
            java.util.List<SwitchCase> cases = new ArrayList<>();
            for (SwitchCase c : sw.cases()) {
                cases.add(new SwitchCase(c.position(), c.value(), rewriteUsing(c.body())));
            }
            return new SwitchStmt(sw.position(), sw.expression(), cases,
                    sw.defaultBody() == null ? null : rewriteUsing(sw.defaultBody()),
                    sw.hasDefault());
        }
        if (s instanceof FunctionDeclStmt fn) {
            FunctionDeclarationNode inner = fn.function();
            return new FunctionDeclStmt(fn.position(), new FunctionDeclarationNode(inner.position(),
                    inner.modifiers(), inner.returnType(), inner.name(), inner.parameters(),
                    inner.thrownExceptions(), inner.typeParameters(),
                    rewriteUsing(inner.body()), inner.annotations()));
        }
        return s;
    }

    /**
     * SG-011: hoisting de funções aninhadas. Cada FunctionDeclStmt vira uma
     * função top-level `outer__inner` (nome único por enclosing) inserida
     * ANTES da outer ("inner primeiro"), e as chamadas `inner(...)` no corpo
     * da outer são reescritas para `outer__inner(...)`. O statement some do
     * corpo (a declaração é efetiva desde o início do corpo — a outer chama
     * e aguarda o retorno).
     */
    static CompilationUnitNode desugarNestedFunctions(CompilationUnitNode unit) {
        boolean any = false;
        for (AstNode d : unit.declarations()) {
            if (d instanceof FunctionDeclarationNode f && hasNestedFunction(f.body())) {
                any = true;
                break;
            }
        }
        if (!any) return unit;
        java.util.List<AstNode> decls = new ArrayList<>();
        for (AstNode d : unit.declarations()) {
            if (d instanceof FunctionDeclarationNode f) {
                List<StatementNode> hoisted = new ArrayList<>();
                List<FunctionDeclarationNode> inners = new ArrayList<>();
                stripNestedFunctions(f.body(), f.name(), hoisted, inners);
                if (!inners.isEmpty()) {
                    // "inner primeiro": as inner entram ANTES da outer
                    decls.addAll(inners);
                    decls.add(new FunctionDeclarationNode(f.position(), f.modifiers(),
                            f.returnType(), f.name(), f.parameters(), f.thrownExceptions(),
                            f.typeParameters(), rewriteCalls(hoisted, inners), f.annotations()));
                    continue;
                }
            }
            decls.add(d);
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), unit.imports(),
                java.util.Collections.unmodifiableList(decls));
    }

    private static boolean hasNestedFunction(List<StatementNode> body) {
        if (body == null) return false;
        for (StatementNode s : body) {
            if (s instanceof FunctionDeclStmt) return true;
            if (s instanceof BlockStmt b && hasNestedFunction(b.statements())) return true;
            if (s instanceof IfStmt is && (hasNestedFunction(List.of(is.thenBranch()))
                    || (is.elseBranch() != null && hasNestedFunction(List.of(is.elseBranch()))))) return true;
            if (s instanceof WhileStmt ws && hasNestedFunction(List.of(ws.body()))) return true;
            if (s instanceof ForStmt fs && hasNestedFunction(List.of(fs.body()))) return true;
            if (s instanceof ForInStmt fis && hasNestedFunction(List.of(fis.body()))) return true;
            if (s instanceof TryStmt ts) {
                if (hasNestedFunction(ts.tryBody())) return true;
                for (CatchClause c : ts.catchClauses()) {
                    if (hasNestedFunction(c.body())) return true;
                }
                if (ts.finallyBody() != null && hasNestedFunction(ts.finallyBody())) return true;
            }
        }
        return false;
    }

    /** Remove os FunctionDeclStmt do corpo (recursivo) e coleta as funções renomeadas. */
    private static void stripNestedFunctions(List<StatementNode> body, String outerName,
                                             List<StatementNode> out,
                                             List<FunctionDeclarationNode> inners) {
        for (StatementNode s : body) {
            if (s instanceof FunctionDeclStmt fds) {
                FunctionDeclarationNode fn = fds.function();
                String qualified = outerName + "__" + fn.name();
                inners.add(new FunctionDeclarationNode(fn.position(), List.of(),
                        fn.returnType(), qualified, fn.parameters(), fn.thrownExceptions(),
                        fn.typeParameters(), rewriteCalls(fn.body(), inners), List.of()));
                continue;
            }
            if (s instanceof BlockStmt b) {
                List<StatementNode> inner = new ArrayList<>();
                stripNestedFunctions(b.statements(), outerName, inner, inners);
                out.add(new BlockStmt(b.position(), inner));
                continue;
            }
            if (s instanceof IfStmt is) {
                StatementNode thenB = hoistOne(is.thenBranch(), outerName, inners);
                StatementNode elseB = is.elseBranch() != null
                        ? hoistOne(is.elseBranch(), outerName, inners) : null;
                out.add(new IfStmt(is.position(), is.condition(), thenB, elseB));
                continue;
            }
            if (s instanceof WhileStmt ws) {
                out.add(new WhileStmt(ws.position(), ws.condition(),
                        hoistOne(ws.body(), outerName, inners)));
                continue;
            }
            if (s instanceof ForStmt fs) {
                out.add(new ForStmt(fs.position(), fs.init(), fs.condition(), fs.update(),
                        hoistOne(fs.body(), outerName, inners)));
                continue;
            }
            if (s instanceof ForInStmt fis) {
                out.add(new ForInStmt(fis.position(), fis.varName(),
                        fis.collection(), hoistOne(fis.body(), outerName, inners)));
                continue;
            }
            if (s instanceof TryStmt ts) {
                List<CatchClause> newCatches = new ArrayList<>();
                for (CatchClause c : ts.catchClauses()) {
                    List<StatementNode> cb = new ArrayList<>();
                    stripNestedFunctions(c.body(), outerName, cb, inners);
                    newCatches.add(new CatchClause(c.position(), c.exceptionType(),
                            c.exceptionName(), cb));
                }
                List<StatementNode> fin = ts.finallyBody() != null ? new ArrayList<>() : null;
                if (fin != null) stripNestedFunctions(ts.finallyBody(), outerName, fin, inners);
                List<StatementNode> tb = new ArrayList<>();
                stripNestedFunctions(ts.tryBody(), outerName, tb, inners);
                out.add(new TryStmt(ts.position(), tb, newCatches, fin));
                continue;
            }
            out.add(s);
        }
    }

    private static StatementNode hoistOne(StatementNode s, String outerName,
                                          List<FunctionDeclarationNode> inners) {
        List<StatementNode> single = new ArrayList<>();
        stripNestedFunctions(List.of(s), outerName, single, inners);
        return single.isEmpty() ? s : single.get(0);
    }

    /** Reescreve chamadas `inner(...)` → `outer__inner(...)` nas statements. */
    private static List<StatementNode> rewriteCalls(List<StatementNode> body,
                                                    List<FunctionDeclarationNode> inners) {
        if (inners.isEmpty() || body == null) return body;
        java.util.Map<String, String> renames = new java.util.HashMap<>();
        for (FunctionDeclarationNode in : inners) {
            String q = in.name();
            renames.put(q.substring(q.lastIndexOf("__") + 2), q);
        }
        List<StatementNode> out = new ArrayList<>();
        for (StatementNode s : body) out.add(rewriteStmt(s, renames));
        return out;
    }

    private static StatementNode rewriteStmt(StatementNode s, java.util.Map<String, String> renames) {
        if (s instanceof ExpressionStmt es && es.expression() != null) {
            return new ExpressionStmt(es.position(), rewriteExpr(es.expression(), renames));
        }
        if (s instanceof ReturnStmt rs && rs.value() != null) {
            return new ReturnStmt(rs.position(), rewriteExpr(rs.value(), renames));
        }
        if (s instanceof VarDeclStmt vd && vd.initializer() != null) {
            return new VarDeclStmt(vd.position(), vd.type(), vd.name(),
                    rewriteExpr(vd.initializer(), renames));
        }
        if (s instanceof IfStmt is) {
            StatementNode thenB = rewriteStmt(is.thenBranch(), renames);
            StatementNode elseB = is.elseBranch() != null ? rewriteStmt(is.elseBranch(), renames) : null;
            return new IfStmt(is.position(), rewriteExpr(is.condition(), renames), thenB, elseB);
        }
        if (s instanceof WhileStmt ws) {
            return new WhileStmt(ws.position(), rewriteExpr(ws.condition(), renames),
                    rewriteStmt(ws.body(), renames));
        }
        if (s instanceof BlockStmt b) {
            List<StatementNode> inner = new ArrayList<>();
            for (StatementNode st : b.statements()) inner.add(rewriteStmt(st, renames));
            return new BlockStmt(b.position(), inner);
        }
        return s;
    }

    private static ExpressionNode rewriteExpr(ExpressionNode e, java.util.Map<String, String> renames) {
        if (e == null) return null;
        if (e instanceof MethodCallExpr mc && mc.receiver() == null) {
            String target = renames.get(mc.methodName());
            if (target != null) {
                List<ExpressionNode> args = new ArrayList<>();
                for (ExpressionNode a : mc.arguments()) args.add(rewriteExpr(a, renames));
                return new MethodCallExpr(mc.position(), null, target,
                        mc.typeArguments(), args);
            }
            List<ExpressionNode> args = new ArrayList<>();
            for (ExpressionNode a : mc.arguments()) args.add(rewriteExpr(a, renames));
            return new MethodCallExpr(mc.position(), mc.receiver() != null
                    ? rewriteExpr(mc.receiver(), renames) : null,
                    mc.methodName(), mc.typeArguments(), args);
        }
        if (e instanceof BinaryExpr bin) {
            return new BinaryExpr(bin.position(), bin.operator(),
                    rewriteExpr(bin.left(), renames), rewriteExpr(bin.right(), renames));
        }
        if (e instanceof UnaryExpr ue) {
            return new UnaryExpr(ue.position(), ue.operator(),
                    rewriteExpr(ue.operand(), renames), ue.prefix());
        }
        return e;
    }

    static CompilationUnitNode desugarApplication(CompilationUnitNode unit) {        java.util.List<AstNode> decls = new ArrayList<>();
        boolean hasOnStart = false;
        boolean hasOnShutdown = false;
        for (AstNode d : unit.declarations()) {
            if (d instanceof ApplicationDeclarationNode app) {
                if (!app.onStart().isEmpty()) {
                    decls.add(new FunctionDeclarationNode(app.position(), List.of(), "void",
                            "kof_app_on_start", List.of(), List.of(), List.of(), app.onStart()));
                    hasOnStart = true;
                }
                if (!app.onShutdown().isEmpty()) {
                    decls.add(new FunctionDeclarationNode(app.position(), List.of(), "void",
                            "kof_app_on_shutdown", List.of(), List.of(), List.of(), app.onShutdown()));
                    hasOnShutdown = true;
                }
            } else {
                decls.add(d);
            }
        }
        if (!hasOnStart && !hasOnShutdown) {
            return unit;
        }
        // Embrulha o main do usuário (se existir) com as chamadas de lifecycle.
        java.util.List<AstNode> wrapped = new ArrayList<>();
        for (AstNode d : decls) {
            if (d instanceof FunctionDeclarationNode f && "main".equals(f.name())) {
                java.util.List<StatementNode> body = new ArrayList<>();
                if (hasOnStart) {
                    body.add(new ExpressionStmt(f.position(),
                            new MethodCallExpr(f.position(), null, "kof_app_on_start", List.of(), List.of())));
                }
                body.addAll(f.body());
                if (hasOnShutdown) {
                    body.add(new ExpressionStmt(f.position(),
                            new MethodCallExpr(f.position(), null, "kof_app_on_shutdown", List.of(), List.of())));
                }
                wrapped.add(new FunctionDeclarationNode(f.position(), f.modifiers(), f.returnType(),
                        f.name(), f.parameters(), f.thrownExceptions(), f.typeParameters(), body,
                        f.annotations()));
            } else {
                wrapped.add(d);
            }
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), unit.imports(),
                java.util.Collections.unmodifiableList(wrapped));
    }

    /**
     * `infra "prod" { ... }` → `design(): Infrastructure` (§D-MAKEALIVE-SYNTAX).
     * Açúcar puro sobre as faces do host: um local `__infra =
     * Infrastructure("prod")`, cada chamada nua `face(args)` vira
     * `__infra.face(args)`, e o desenho é retornado. Sem keyword/token/tipo/
     * runtime novo — o resultado é idêntico ao `design()` escrito à mão.
     */
    static CompilationUnitNode desugarInfra(CompilationUnitNode unit) {
        java.util.List<AstNode> decls = new ArrayList<>();
        boolean any = false;
        for (AstNode d : unit.declarations()) {
            if (d instanceof InfraDeclarationNode infra) {
                decls.add(buildDesignFunction(infra));
                any = true;
            } else {
                decls.add(d);
            }
        }
        if (!any) {
            return unit;
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), unit.imports(),
                java.util.Collections.unmodifiableList(decls));
    }

    private static FunctionDeclarationNode buildDesignFunction(InfraDeclarationNode infra) {
        SourcePosition p = infra.position();
        List<StatementNode> body = new ArrayList<>();
        body.add(new VarDeclStmt(p, "Infrastructure", "__infra",
                new NewExpr(p, "Infrastructure", List.of(),
                        List.of(new LiteralExpr(p, ConcreteLiteralKind.STRING, infra.name())))));
        for (StatementNode st : infra.body()) {
            if (st instanceof ExpressionStmt es && es.expression() instanceof MethodCallExpr mc
                    && mc.receiver() == null) {
                body.add(new ExpressionStmt(p, new MethodCallExpr(p,
                        new IdentifierExpr(p, "__infra"), mc.methodName(), mc.typeArguments(),
                        mc.arguments())));
            } else {
                body.add(st);
            }
        }
        body.add(new ReturnStmt(p, new IdentifierExpr(p, "__infra")));
        return new FunctionDeclarationNode(p, List.of(), "Infrastructure", "design",
                List.of(), List.of(), List.of(), body);
    }
}
