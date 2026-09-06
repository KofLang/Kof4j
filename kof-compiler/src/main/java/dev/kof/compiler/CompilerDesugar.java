package dev.kof.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Desugar de testes (`test "nome" {}`) e lifecycle (`application { }`) do
 * CompilerDriver. Puro — recebe o estado (discoveredTests etc.) por parâmetro.
 */
final class CompilerDesugar {

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

    static CompilationUnitNode desugarApplication(CompilationUnitNode unit) {
        java.util.List<AstNode> decls = new ArrayList<>();
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