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
                                            String currentSourceName) {
        discoveredTests.clear();
        java.util.List<AstNode> decls = new ArrayList<>();
        int ti = 0;
        for (AstNode d : unit.declarations()) {
            if (d instanceof TestDeclarationNode t) {
                String fn = "kof_test_" + ti++;
                discoveredTests.add(new CompilerDriver.TestInfo(t.name(), fn));
                decls.add(new FunctionDeclarationNode(t.position(), List.of(), "void", fn,
                        List.of(), List.of(), List.of(), t.body()));
            } else {
                decls.add(d);
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
            withHarness.add(buildTestHarnessMain(discoveredTests, currentSourceName));
            decls = withHarness;
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), unit.imports(),
                java.util.Collections.unmodifiableList(decls));
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

    static FunctionDeclarationNode buildTestHarnessMain(
        List<CompilerDriver.TestInfo> discoveredTests, String currentSourceName) {
        SourcePosition p = new SourcePosition(currentSourceName != null ? currentSourceName : "", 0, 0, 0, 0);
        List<StatementNode> body = new ArrayList<>();
        ExpressionNode failedVar = new IdentifierExpr(p, "__kof_failed");
        body.add(new VarDeclStmt(p, "Int", "__kof_failed",
                new LiteralExpr(p, ConcreteLiteralKind.INT, "0")));
        for (int i = 0; i < discoveredTests.size(); i++) {
            CompilerDriver.TestInfo test = discoveredTests.get(i);
            ExpressionNode nameLit = new LiteralExpr(p, ConcreteLiteralKind.STRING, test.name());
            List<StatementNode> tryBody = new ArrayList<>();
            tryBody.add(new ExpressionStmt(p, new MethodCallExpr(p, null,
                    test.functionName(), List.of(), List.of())));
            tryBody.add(new ExpressionStmt(p, callPrintln(p, concat(p,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, "PASS "), nameLit))));
            ExpressionNode failMsg = concat(p,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, "FAIL "), nameLit,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, ": "),
                    new IdentifierExpr(p, "e"));
            List<StatementNode> catchBody = new ArrayList<>();
            catchBody.add(new ExpressionStmt(p, callPrintln(p, failMsg)));
            catchBody.add(new ExpressionStmt(p, new AssignmentExpr(p, failedVar, "=",
                    new BinaryExpr(p, "+", failedVar,
                            new LiteralExpr(p, ConcreteLiteralKind.INT, "1")))));
            body.add(new TryStmt(p, tryBody,
                    List.of(new CatchClause(p, "String", "e", catchBody)), List.of()));
        }
        body.add(new ExpressionStmt(p, callPrintln(p,
                new LiteralExpr(p, ConcreteLiteralKind.STRING, "────────"))));
        ExpressionNode summary = concat(p,
                failedVar,
                new LiteralExpr(p, ConcreteLiteralKind.STRING, " failed of "
                        + discoveredTests.size() + " tests"));
        body.add(new ExpressionStmt(p, callPrintln(p, summary)));
        // falha = exit code != 0 em todos os targets, sem stack trace:
        // JVM System.exit / Native syscall exit / JS sentinel no runner
        body.add(new IfStmt(p,
                new BinaryExpr(p, ">", failedVar, new LiteralExpr(p, ConcreteLiteralKind.INT, "0")),
                new BlockStmt(p, List.of(new ExpressionStmt(p, new MethodCallExpr(p,
                        new IdentifierExpr(p, "process"), "exit", List.of(),
                        List.of(new LiteralExpr(p, ConcreteLiteralKind.INT, "1")))))),
                null));
        return new FunctionDeclarationNode(p, List.of(), "void", "main",
                List.of(), List.of(), List.of(), List.copyOf(body));
    }

    static ExpressionNode concat(SourcePosition p, ExpressionNode... parts) {
        ExpressionNode acc = parts[0];
        for (int i = 1; i < parts.length; i++) {
            acc = new BinaryExpr(p, "+", acc, parts[i]);
        }
        return acc;
    }

    static ExpressionNode callPrintln(SourcePosition p, ExpressionNode arg) {
        return new MethodCallExpr(p, null, "println", List.of(), List.of(arg));
    }
}