package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Síntese do {@code main} runner do {@code kof test} (X8) — a fonte única do
 * catálogo de testes. Extraído do {@code CompilerDesugar} (regra 7: o nome é
 * a responsabilidade).
 *
 * <p>Fatia 3 ({@code D-COMPLETE-FIRST}, 26/09): tags opcionais no primitivo
 * ({@code test "nome", "smoke" { }}), filtro por tag decidido em COMPILE-TIME
 * (propriedade de sistema {@code kof.test.tag} — o harness é gerado no processo
 * que compila, então JVM/Script/Native/JS executam TODOS o mesmo catálogo
 * filtrado, paridade rule-5 por construção) e fixtures {@code setup()} /
 * {@code teardown()} como funções top-level comuns (zero sintaxe nova —
 * SG-023 iii mantido na superfície; a fatia acrescenta a SEMÂNTICA do runner:
 * setup que falha = SKIP nomeado do teste (não-falha, não-conta como
 * executado); teardown roda por {@code finally} em todo teste que o setup
 * deixou rodar — inclusive os que falham.</p>
 */
final class TestHarnessBuilder {

    private TestHarnessBuilder() {}

    /** Uma entrada do catálogo: nome, função gerada e tags (fatia 3). */
    record Entry(String name, String functionName, List<String> tags) {}

    static FunctionDeclarationNode build(List<Entry> tests, String currentSourceName,
                                         boolean hasSetup, boolean hasTeardown, String tagFilter) {
        SourcePosition p = new SourcePosition(
                currentSourceName != null ? currentSourceName : "", 0, 0, 0, 0);
        boolean filtering = tagFilter != null && !tagFilter.isEmpty();
        List<Entry> kept = tests;
        if (filtering) {
            kept = new ArrayList<>();
            for (Entry t : tests) {
                if (t.tags().contains(tagFilter)) kept.add(t);
            }
        }
        List<StatementNode> body = new ArrayList<>();
        if (filtering) {
            body.add(new ExpressionStmt(p, callPrintln(p, concat(p,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING,
                            "kof test: tag '" + tagFilter + "' ("),
                    new LiteralExpr(p, ConcreteLiteralKind.INT, String.valueOf(kept.size())),
                    new LiteralExpr(p, ConcreteLiteralKind.STRING,
                            " of " + tests.size() + ")")))));
            if (kept.isEmpty()) {
                body.add(new ExpressionStmt(p, callPrintln(p, new LiteralExpr(p,
                        ConcreteLiteralKind.STRING,
                        "no tests with tag '" + tagFilter + "' (of " + tests.size() + ")"))));
                return new FunctionDeclarationNode(p, List.of(), "void", "main",
                        List.of(), List.of(), List.of(), List.copyOf(body));
            }
        }
        ExpressionNode failedVar = new IdentifierExpr(p, "__kof_failed");
        body.add(new VarDeclStmt(p, "Int", "__kof_failed",
                new LiteralExpr(p, ConcreteLiteralKind.INT, "0")));
        int i = 0;
        for (Entry test : kept) {
            String name = test.name();
            ExpressionNode nameLit = new LiteralExpr(p, ConcreteLiteralKind.STRING, name);
            List<StatementNode> runBody = new ArrayList<>();
            runBody.add(testCall(p, test));
            runBody.add(new ExpressionStmt(p, callPrintln(p, concat(p,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, "PASS "), nameLit))));
            List<StatementNode> catchBody = new ArrayList<>();
            catchBody.add(new ExpressionStmt(p, callPrintln(p, concat(p,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, "FAIL "), nameLit,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, ": "),
                    new IdentifierExpr(p, "e")))));
            catchBody.add(new ExpressionStmt(p, new AssignmentExpr(p, failedVar, "=",
                    new BinaryExpr(p, "+", failedVar,
                            new LiteralExpr(p, ConcreteLiteralKind.INT, "1")))));
            List<StatementNode> finalizers = new ArrayList<>();
            if (hasTeardown) {
                finalizers.add(new ExpressionStmt(p,
                        new MethodCallExpr(p, null, "teardown", List.of(), List.of())));
            }
            List<StatementNode> runInner = List.of(
                    new TryStmt(p, runBody, List.of(new CatchClause(p, "String", "e", catchBody)),
                            finalizers));
            if (!hasSetup) {
                body.addAll(runInner);
                continue;
            }
            String skipName = "__kof_skip_" + i++;
            body.add(new VarDeclStmt(p, "Bool", skipName,
                    new LiteralExpr(p, ConcreteLiteralKind.BOOLEAN, "false")));
            List<StatementNode> setupCatch = new ArrayList<>();
            setupCatch.add(new ExpressionStmt(p, new AssignmentExpr(p,
                    new IdentifierExpr(p, skipName), "=",
                    new LiteralExpr(p, ConcreteLiteralKind.BOOLEAN, "true"))));
            setupCatch.add(new ExpressionStmt(p, callPrintln(p, concat(p,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, "SKIP "), nameLit,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, ": setup failed: "),
                    new IdentifierExpr(p, "e")))));
            body.add(new TryStmt(p,
                    List.of(new ExpressionStmt(p,
                            new MethodCallExpr(p, null, "setup", List.of(), List.of()))),
                    List.of(new CatchClause(p, "String", "e", setupCatch)), List.of()));
            body.add(new IfStmt(p,
                    new BinaryExpr(p, "==", new IdentifierExpr(p, skipName),
                            new LiteralExpr(p, ConcreteLiteralKind.BOOLEAN, "false")),
                    new BlockStmt(p, runInner), null));
        }
        body.add(new ExpressionStmt(p, callPrintln(p,
                new LiteralExpr(p, ConcreteLiteralKind.STRING, "────────"))));
        ExpressionNode summary = concat(p,
                failedVar,
                new LiteralExpr(p, ConcreteLiteralKind.STRING,
                        " failed of " + kept.size() + " tests"));
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

    private static StatementNode testCall(SourcePosition p, Entry test) {
        return new ExpressionStmt(p,
                new MethodCallExpr(p, null, test.functionName(), List.of(), List.of()));
    }

    static ExpressionNode concat(SourcePosition p, ExpressionNode... parts) {
        ExpressionNode acc = parts[0];
        for (int j = 1; j < parts.length; j++) {
            acc = new BinaryExpr(p, "+", acc, parts[j]);
        }
        return acc;
    }

    static ExpressionNode callPrintln(SourcePosition p, ExpressionNode arg) {
        return new MethodCallExpr(p, null, "println", List.of(), List.of(arg));
    }
}
