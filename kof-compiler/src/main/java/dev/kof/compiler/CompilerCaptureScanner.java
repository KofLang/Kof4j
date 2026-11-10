package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Análise de capturas mutadas e declarações/lambdas no escopo da função.
 */
public final class CompilerCaptureScanner {

    private CompilerCaptureScanner() {}

    static void collectMutatedCaptures(CompilerDriver driver, List<StatementNode> body,
                                     List<IRLocalVariable> params) {
        // Capturas REAIS: uma variável só precisa de box se for capturada por
        // uma lambda (coletCaptures resolve contra os nomes do escopo da função)
        // E mutada em qualquer lugar. Antes só mutações DENTRO da lambda eram
        // detectadas, então `var f = (x) -> x + offset; offset = 20` capturava
        // offset por valor (resultado desatualizado). Ver learn/16-lambdas.md.
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (IRLocalVariable p : params) declared.add(p.name());
        for (StatementNode stmt : body) collectDeclaredVarNamesStmt(stmt, declared);
        java.util.List<IRLocalVariable> outerLocals = new ArrayList<>(params);
        for (String n : declared) {
            if (driver.findLocalVar(n, outerLocals) == null) {
                outerLocals.add(new IRLocalVariable(outerLocals.size(), n, Type.UnknownType.UNKNOWN));
            }
        }
        driver.lambdaCapturedNames.clear();
        java.util.List<LambdaExpr> lambdas = new ArrayList<>();
        for (StatementNode stmt : body) collectLambdasStmt(stmt, lambdas);
        for (LambdaExpr le : lambdas) {
            for (IRLocalVariable c : CompilerCaptures.collectCaptures(driver, le, outerLocals)) {
                driver.lambdaCapturedNames.add(c.name());
            }
        }
        for (StatementNode stmt : body) {
            collectMutatedCapturesStmt(driver, stmt, new java.util.HashSet<>(), false);
        }
    }

    /** Nomes de var-decl no escopo da função (não desce em lambdas). */
    static void collectDeclaredVarNamesStmt(StatementNode stmt, java.util.Set<String> out) {

        switch (stmt) {
            case ExpressionStmt es -> {
                collectDeclaredVarNamesExpr(es.expression());
            }
            case ReturnStmt rs -> {
                if (rs.value() != null) collectDeclaredVarNamesExpr(rs.value());
            }
            case BlockStmt b -> {
                for (StatementNode s : b.statements()) collectDeclaredVarNamesStmt(s, out);
            }
            case IfStmt i -> {
                collectDeclaredVarNamesExpr(i.condition());
                collectDeclaredVarNamesStmt(i.thenBranch(), out);
                if (i.elseBranch() != null) collectDeclaredVarNamesStmt(i.elseBranch(), out);
            }
            case WhileStmt w -> {
                collectDeclaredVarNamesExpr(w.condition());
                collectDeclaredVarNamesStmt(w.body(), out);
            }
            case LockStmt w -> {
                collectDeclaredVarNamesExpr(w.target());
                collectDeclaredVarNamesStmt(w.body(), out);
            }
            case DoWhileStmt dw -> {
                collectDeclaredVarNamesStmt(dw.body(), out);
                collectDeclaredVarNamesExpr(dw.condition());
            }
            case ForStmt f -> {
                if (f.init() instanceof VarDeclStmt vds) {
                    out.add(vds.name());
                    if (vds.initializer() != null) collectDeclaredVarNamesExpr(vds.initializer());
                } else if (f.init() instanceof ExpressionStmt ies) {
                    collectDeclaredVarNamesExpr(ies.expression());
                }
                if (f.condition() != null) collectDeclaredVarNamesExpr(f.condition());
                if (f.update() != null) collectDeclaredVarNamesExpr(f.update());
                collectDeclaredVarNamesStmt(f.body(), out);
            }
            case ForInStmt fi -> {
                collectDeclaredVarNamesExpr(fi.collection());
                collectDeclaredVarNamesStmt(fi.body(), out);
            }
            case VarDeclStmt vds -> {
                out.add(vds.name());
                if (vds.initializer() != null) collectDeclaredVarNamesExpr(vds.initializer());
            }
            case ThrowStmt ts -> {
                collectDeclaredVarNamesExpr(ts.expression());
            }
            case AssertStmt as -> {
                collectDeclaredVarNamesExpr(as.condition());
            }
            case SpawnStmt ss -> {
                collectDeclaredVarNamesExpr(ss.expression());
            }
            case SwitchStmt sw -> {
                collectDeclaredVarNamesExpr(sw.expression());
                for (SwitchCase c : sw.cases()) {
                    if (c.value() != null) collectDeclaredVarNamesExpr(c.value());
                    for (StatementNode s : c.body()) collectDeclaredVarNamesStmt(s, out);
                }
                if (sw.defaultBody() != null) {
                    for (StatementNode s : sw.defaultBody()) collectDeclaredVarNamesStmt(s, out);
                }
            }
            case TryStmt ts -> {
                for (StatementNode s : ts.tryBody()) collectDeclaredVarNamesStmt(s, out);
                for (CatchClause cc : ts.catchClauses()) {
                    for (StatementNode s : cc.body()) collectDeclaredVarNamesStmt(s, out);
                }
                if (ts.finallyBody() != null) {
                    for (StatementNode s : ts.finallyBody()) collectDeclaredVarNamesStmt(s, out);
                }
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else original)
        }
    }

    static void collectDeclaredVarNamesExpr(ExpressionNode expr) {
        if (expr instanceof LambdaExpr) {
            return; // declarações internas da lambda pertencem a ela
        }
        switch (expr) {
            case BinaryExpr be -> {
                collectDeclaredVarNamesExpr(be.left());
                collectDeclaredVarNamesExpr(be.right());
            }
            case UnaryExpr ue -> {
                collectDeclaredVarNamesExpr(ue.operand());
            }
            case MethodCallExpr mc -> {
                if (mc.receiver() != null) collectDeclaredVarNamesExpr(mc.receiver());
                for (ExpressionNode a : mc.arguments()) collectDeclaredVarNamesExpr(a);
            }
            case FieldAccessExpr fa -> {
                collectDeclaredVarNamesExpr(fa.receiver());
            }
            case AssignmentExpr ae -> {
                collectDeclaredVarNamesExpr(ae.target());
                collectDeclaredVarNamesExpr(ae.value());
            }
            case IfExpr iex -> {
                collectDeclaredVarNamesExpr(iex.condition());
                collectDeclaredVarNamesExpr(iex.thenExpr());
                collectDeclaredVarNamesExpr(iex.elseExpr());
            }
            case SwitchExpr sex -> {
                collectDeclaredVarNamesExpr(sex.expression());
                for (SwitchExprCase sc : sex.cases()) collectDeclaredVarNamesExpr(sc.body());
                if (sex.defaultValue() != null) collectDeclaredVarNamesExpr(sex.defaultValue());
            }
            case ArrayAccessExpr aa -> {
                collectDeclaredVarNamesExpr(aa.receiver());
                collectDeclaredVarNamesExpr(aa.index());
            }
            case NewExpr ne -> {
                for (ExpressionNode a : ne.arguments()) collectDeclaredVarNamesExpr(a);
            }
            case NewArrayExpr nae -> {
                collectDeclaredVarNamesExpr(nae.size());
                for (ExpressionNode dim : nae.moreDims()) collectDeclaredVarNamesExpr(dim);
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else original)
        }
    }

    /**
     * §253 face A: `nome` é lido como identificador livre dentro de ALGUMA lambda
     * deste inicializador (não no nível top — só via lambda). Usado pelo
     * StatementAnalyzer para gate SEM092 (native) e pre-define (demais alvos).
     * Shadowing: parâmetro/formal/var-decl de mesmo nome DENTRO da lambda não conta.
     */
    static boolean lambdaExprReadsName(ExpressionNode expr, String name) {
        java.util.List<LambdaExpr> lambdas = new java.util.ArrayList<>();
        collectLambdasExpr(expr, lambdas);
        for (LambdaExpr le : lambdas) {
            java.util.Set<String> shadowed = new java.util.HashSet<>();
            for (FormalParameterNode p : le.parameters()) shadowed.add(p.name());
            for (StatementNode s : le.body()) {
                if (stmtReadsNameFree(s, name, shadowed)) return true;
            }
        }
        return false;
    }

    private static boolean stmtReadsNameFree(StatementNode stmt, String name, java.util.Set<String> shadowed) {
        switch (stmt) {
            case ExpressionStmt es -> { return exprReadsNameFree(es.expression(), name, shadowed); }
            case ReturnStmt rs -> {
                return rs.value() != null && exprReadsNameFree(rs.value(), name, shadowed);
            }
            case BlockStmt b -> {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                for (StatementNode s : b.statements()) {
                    if (stmtReadsNameFree(s, name, inner)) return true;
                }
                return false;
            }
            case IfStmt i -> {
                java.util.Set<String> then = new java.util.HashSet<>(shadowed);
                if (exprReadsNameFree(i.condition(), name, then)) return true;
                if (stmtReadsNameFree(i.thenBranch(), name, new java.util.HashSet<>(shadowed))) return true;
                if (i.elseBranch() != null
                        && stmtReadsNameFree(i.elseBranch(), name, new java.util.HashSet<>(shadowed))) return true;
                return false;
            }
            case WhileStmt w -> {
                if (exprReadsNameFree(w.condition(), name, new java.util.HashSet<>(shadowed))) return true;
                return stmtReadsNameFree(w.body(), name, new java.util.HashSet<>(shadowed));
            }
            case LockStmt w -> {
                if (exprReadsNameFree(w.target(), name, new java.util.HashSet<>(shadowed))) return true;
                return stmtReadsNameFree(w.body(), name, new java.util.HashSet<>(shadowed));
            }
            case VarDeclStmt vds -> {
                // decl shadowe o nome → não é mais livre dentro deste bloco.
                if (vds.initializer() != null
                        && exprReadsNameFree(vds.initializer(), name, shadowed)) return true;
                shadowed.add(vds.name());
                return false;
            }
            case ThrowStmt ts -> {
                return ts.expression() != null
                        && exprReadsNameFree(ts.expression(), name, shadowed);
            }
            case AssertStmt as -> {
                return as.condition() != null
                        && exprReadsNameFree(as.condition(), name, shadowed);
            }
            case SpawnStmt ss -> {
                return exprReadsNameFree(ss.expression(), name, shadowed);
            }
            default -> { return false; }
        }
    }

    private static boolean exprReadsNameFree(ExpressionNode expr, String name, java.util.Set<String> shadowed) {
        if (expr == null) return false;
        if (expr instanceof IdentifierExpr ie) return name.equals(ie.name()) && !shadowed.contains(name);
        // nested lambda: parâmetros da lambda shadowe; recursão de corpo é
        // "livre" — mas para o gate SEM092 basta QUALQUER leitura.
        if (expr instanceof LambdaExpr le) {
            java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
            for (FormalParameterNode p : le.parameters()) inner.add(p.name());
            for (StatementNode s : le.body()) {
                if (stmtReadsNameFree(s, name, inner)) return true;
            }
            return false;
        }
        switch (expr) {
            case BinaryExpr be -> {
                return exprReadsNameFree(be.left(), name, shadowed)
                        || exprReadsNameFree(be.right(), name, shadowed);
            }
            case UnaryExpr ue -> { return exprReadsNameFree(ue.operand(), name, shadowed); }
            case MethodCallExpr mc -> {
                if (exprReadsNameFree(mc.receiver(), name, shadowed)) return true;
                for (ExpressionNode a : mc.arguments()) {
                    if (exprReadsNameFree(a, name, shadowed)) return true;
                }
                return false;
            }
            case FieldAccessExpr fa -> { return exprReadsNameFree(fa.receiver(), name, shadowed); }
            case AssignmentExpr ae -> {
                return exprReadsNameFree(ae.target(), name, shadowed)
                        || exprReadsNameFree(ae.value(), name, shadowed);
            }
            case IfExpr iex -> {
                return exprReadsNameFree(iex.condition(), name, shadowed)
                        || exprReadsNameFree(iex.thenExpr(), name, shadowed)
                        || exprReadsNameFree(iex.elseExpr(), name, shadowed);
            }
            case ArrayAccessExpr aa -> {
                return exprReadsNameFree(aa.receiver(), name, shadowed)
                        || exprReadsNameFree(aa.index(), name, shadowed);
            }
            case NewExpr ne -> {
                for (ExpressionNode a : ne.arguments()) {
                    if (exprReadsNameFree(a, name, shadowed)) return true;
                }
                return false;
            }
            case NewArrayExpr nae -> {
                if (exprReadsNameFree(nae.size(), name, shadowed)) return true;
                for (ExpressionNode d : nae.moreDims()) {
                    if (exprReadsNameFree(d, name, shadowed)) return true;
                }
                return false;
            }
            default -> { return false; }
        }
    }

    /** Coleta todas as lambdas do corpo (para computar capturas reais). */
    static void collectLambdasStmt(StatementNode stmt, java.util.List<LambdaExpr> out) {        switch (stmt) {
            case ExpressionStmt es -> {
                collectLambdasExpr(es.expression(), out);
            }
            case ReturnStmt rs -> {
                if (rs.value() != null) collectLambdasExpr(rs.value(), out);
            }
            case BlockStmt b -> {
                for (StatementNode s : b.statements()) collectLambdasStmt(s, out);
            }
            case IfStmt i -> {
                collectLambdasExpr(i.condition(), out);
                collectLambdasStmt(i.thenBranch(), out);
                if (i.elseBranch() != null) collectLambdasStmt(i.elseBranch(), out);
            }
            case WhileStmt w -> {
                collectLambdasExpr(w.condition(), out);
                collectLambdasStmt(w.body(), out);
            }
            case LockStmt w -> {
                collectLambdasExpr(w.target(), out);
                collectLambdasStmt(w.body(), out);
            }
            case DoWhileStmt dw -> {
                collectLambdasStmt(dw.body(), out);
                collectLambdasExpr(dw.condition(), out);
            }
            case ForStmt f -> {
                if (f.init() instanceof VarDeclStmt vds) {
                    if (vds.initializer() != null) collectLambdasExpr(vds.initializer(), out);
                } else if (f.init() instanceof ExpressionStmt ies) {
                    collectLambdasExpr(ies.expression(), out);
                }
                if (f.condition() != null) collectLambdasExpr(f.condition(), out);
                if (f.update() != null) collectLambdasExpr(f.update(), out);
                collectLambdasStmt(f.body(), out);
            }
            case ForInStmt fi -> {
                collectLambdasExpr(fi.collection(), out);
                collectLambdasStmt(fi.body(), out);
            }
            case VarDeclStmt vds -> {
                if (vds.initializer() != null) collectLambdasExpr(vds.initializer(), out);
            }
            case ThrowStmt ts -> {
                collectLambdasExpr(ts.expression(), out);
            }
            case AssertStmt as -> {
                collectLambdasExpr(as.condition(), out);
            }
            case SpawnStmt ss -> {
                collectLambdasExpr(ss.expression(), out);
            }
            case SwitchStmt sw -> {
                collectLambdasExpr(sw.expression(), out);
                for (SwitchCase c : sw.cases()) {
                    if (c.value() != null) collectLambdasExpr(c.value(), out);
                    for (StatementNode s : c.body()) collectLambdasStmt(s, out);
                }
                if (sw.defaultBody() != null) {
                    for (StatementNode s : sw.defaultBody()) collectLambdasStmt(s, out);
                }
            }
            case TryStmt ts -> {
                for (StatementNode s : ts.tryBody()) collectLambdasStmt(s, out);
                for (CatchClause cc : ts.catchClauses()) {
                    for (StatementNode s : cc.body()) collectLambdasStmt(s, out);
                }
                if (ts.finallyBody() != null) {
                    for (StatementNode s : ts.finallyBody()) collectLambdasStmt(s, out);
                }
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else original)
        }
    }

    static void collectLambdasExpr(ExpressionNode expr, java.util.List<LambdaExpr> out) {
        if (expr instanceof LambdaExpr le) {
            out.add(le);
            return;
        }
        switch (expr) {
            case BinaryExpr be -> {
                collectLambdasExpr(be.left(), out);
                collectLambdasExpr(be.right(), out);
            }
            case UnaryExpr ue -> {
                collectLambdasExpr(ue.operand(), out);
            }
            case MethodCallExpr mc -> {
                if (mc.receiver() != null) collectLambdasExpr(mc.receiver(), out);
                for (ExpressionNode a : mc.arguments()) collectLambdasExpr(a, out);
            }
            case FieldAccessExpr fa -> {
                collectLambdasExpr(fa.receiver(), out);
            }
            case AssignmentExpr ae -> {
                collectLambdasExpr(ae.target(), out);
                collectLambdasExpr(ae.value(), out);
            }
            case IfExpr iex -> {
                collectLambdasExpr(iex.condition(), out);
                collectLambdasExpr(iex.thenExpr(), out);
                collectLambdasExpr(iex.elseExpr(), out);
            }
            case SwitchExpr sex -> {
                collectLambdasExpr(sex.expression(), out);
                for (SwitchExprCase sc : sex.cases()) collectLambdasExpr(sc.body(), out);
                if (sex.defaultValue() != null) collectLambdasExpr(sex.defaultValue(), out);
            }
            case ArrayAccessExpr aa -> {
                collectLambdasExpr(aa.receiver(), out);
                collectLambdasExpr(aa.index(), out);
            }
            case NewExpr ne -> {
                for (ExpressionNode a : ne.arguments()) collectLambdasExpr(a, out);
            }
            case NewArrayExpr nae -> {
                collectLambdasExpr(nae.size(), out);
                for (ExpressionNode dim : nae.moreDims()) collectLambdasExpr(dim, out);
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else original)
        }
    }

    static void collectMutatedCapturesStmt(CompilerDriver driver, StatementNode stmt, java.util.Set<String> shadowed,
                                             boolean inLambda) {
        switch (stmt) {
            case ExpressionStmt es -> {
                collectMutatedCapturesExpr(driver, es.expression(), shadowed, inLambda);
            }
            case ReturnStmt rs -> {
                if (rs.value() != null) collectMutatedCapturesExpr(driver, rs.value(), shadowed, inLambda);
            }
            case BlockStmt b -> {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                for (StatementNode s : b.statements()) collectMutatedCapturesStmt(driver, s, inner, inLambda);
            }
            case IfStmt i -> {
                collectMutatedCapturesExpr(driver, i.condition(), shadowed, inLambda);
                collectMutatedCapturesStmt(driver, i.thenBranch(), new java.util.HashSet<>(shadowed), inLambda);
                if (i.elseBranch() != null) collectMutatedCapturesStmt(driver, i.elseBranch(), new java.util.HashSet<>(shadowed), inLambda);
            }
            case WhileStmt w -> {
                collectMutatedCapturesExpr(driver, w.condition(), shadowed, inLambda);
                collectMutatedCapturesStmt(driver, w.body(), new java.util.HashSet<>(shadowed), inLambda);
            }
            case LockStmt w -> {
                collectMutatedCapturesExpr(driver, w.target(), shadowed, inLambda);
                collectMutatedCapturesStmt(driver, w.body(), new java.util.HashSet<>(shadowed), inLambda);
            }
            case DoWhileStmt dw -> {
                collectMutatedCapturesStmt(driver, dw.body(), new java.util.HashSet<>(shadowed), inLambda);
                collectMutatedCapturesExpr(driver, dw.condition(), shadowed, inLambda);
            }
            case ForStmt f -> {
                if (f.init() instanceof VarDeclStmt vds) {
                    collectMutatedCapturesStmt(driver, vds, shadowed, inLambda);
                } else if (f.init() instanceof ExpressionStmt ies) {
                    collectMutatedCapturesExpr(driver, ies.expression(), shadowed, inLambda);
                }
                if (f.condition() != null) collectMutatedCapturesExpr(driver, f.condition(), shadowed, inLambda);
                if (f.update() != null) collectMutatedCapturesExpr(driver, f.update(), shadowed, inLambda);
                collectMutatedCapturesStmt(driver, f.body(), new java.util.HashSet<>(shadowed), inLambda);
            }
            case ForInStmt fi -> {
                collectMutatedCapturesExpr(driver, fi.collection(), shadowed, inLambda);
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                inner.add(fi.varName());
                collectMutatedCapturesStmt(driver, fi.body(), inner, inLambda);
            }
            case VarDeclStmt vds -> {
                // §253 face A (c): `var id = time.interval(…, () -> cancel(id))` —
                // a lambda do PRÓPRIO inicializador lê o nome da decl. Sem isto
                // o nome nunca entra em mutatedCapturedNames (nenhuma assign
                // externa) → captura por valor de slot vazio (leitura stale/null).
                // Pre-marcar força o box (CapturedVarBox.emit) com store do box
                // ANTES de avaliar o init (seam (d)).
                if (vds.initializer() != null
                        && lambdaExprReadsName(vds.initializer(), vds.name())) {
                    driver.mutatedCapturedNames.add(vds.name());
                }
                shadowed.add(vds.name());
                if (vds.initializer() != null) collectMutatedCapturesExpr(driver, vds.initializer(), shadowed, inLambda);
            }
            case ThrowStmt ts -> {
                collectMutatedCapturesExpr(driver, ts.expression(), shadowed, inLambda);
            }
            case AssertStmt as -> {
                collectMutatedCapturesExpr(driver, as.condition(), shadowed, inLambda);
            }
            case SpawnStmt ss -> {
                collectMutatedCapturesExpr(driver, ss.expression(), shadowed, inLambda);
            }
            case SwitchStmt sw -> {
                collectMutatedCapturesExpr(driver, sw.expression(), shadowed, inLambda);
                for (SwitchCase c : sw.cases()) {
                    if (c.value() != null) collectMutatedCapturesExpr(driver, c.value(), shadowed, inLambda);
                    for (StatementNode s : c.body()) collectMutatedCapturesStmt(driver, s, new java.util.HashSet<>(shadowed), inLambda);
                }
                if (sw.defaultBody() != null) {
                    for (StatementNode s : sw.defaultBody()) collectMutatedCapturesStmt(driver, s, new java.util.HashSet<>(shadowed), inLambda);
                }
            }
            case TryStmt ts -> {
                for (StatementNode s : ts.tryBody()) collectMutatedCapturesStmt(driver, s, new java.util.HashSet<>(shadowed), inLambda);
                for (CatchClause cc : ts.catchClauses()) {
                    java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                    inner.add(cc.exceptionName());
                    for (StatementNode s : cc.body()) collectMutatedCapturesStmt(driver, s, inner, inLambda);
                }
                if (ts.finallyBody() != null) {
                    for (StatementNode s : ts.finallyBody()) collectMutatedCapturesStmt(driver, s, new java.util.HashSet<>(shadowed), inLambda);
                }
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else original)
        }
    }

    static void collectMutatedCapturesExpr(CompilerDriver driver, ExpressionNode expr, java.util.Set<String> shadowed,
                                             boolean inLambda) {
        switch (expr) {
            case LambdaExpr le -> {
                for (StatementNode s : le.body()) {
                    collectMutatedCapturesStmt(driver, s, new java.util.HashSet<>(), true);
                }
            }
            case AssignmentExpr ae -> {
                if (ae.target() instanceof IdentifierExpr ie && !shadowed.contains(ie.name())
                        && (inLambda || driver.lambdaCapturedNames.contains(ie.name()))) {
                    driver.mutatedCapturedNames.add(ie.name());
                }
                collectMutatedCapturesExpr(driver, ae.target(), shadowed, inLambda);
                collectMutatedCapturesExpr(driver, ae.value(), shadowed, inLambda);
            }
            case UnaryExpr ue -> {
                if (inLambda && ue.operand() instanceof IdentifierExpr ie && !shadowed.contains(ie.name())
                        && ("++".equals(ue.operator()) || "--".equals(ue.operator()))) {
                    driver.mutatedCapturedNames.add(ie.name());
                }
                collectMutatedCapturesExpr(driver, ue.operand(), shadowed, inLambda);
            }
            case BinaryExpr bin -> {
                collectMutatedCapturesExpr(driver, bin.left(), shadowed, inLambda);
                collectMutatedCapturesExpr(driver, bin.right(), shadowed, inLambda);
            }
            case MethodCallExpr mc -> {
                if (mc.receiver() != null) collectMutatedCapturesExpr(driver, mc.receiver(), shadowed, inLambda);
                for (ExpressionNode arg : mc.arguments()) collectMutatedCapturesExpr(driver, arg, shadowed, inLambda);
            }
            case FieldAccessExpr fa -> {
                collectMutatedCapturesExpr(driver, fa.receiver(), shadowed, inLambda);
            }
            case ArrayAccessExpr aa -> {
                collectMutatedCapturesExpr(driver, aa.receiver(), shadowed, inLambda);
                collectMutatedCapturesExpr(driver, aa.index(), shadowed, inLambda);
            }
            case IfExpr iex -> {
                collectMutatedCapturesExpr(driver, iex.condition(), shadowed, inLambda);
                collectMutatedCapturesExpr(driver, iex.thenExpr(), shadowed, inLambda);
                collectMutatedCapturesExpr(driver, iex.elseExpr(), shadowed, inLambda);
            }
            case SwitchExpr sex -> {
                collectMutatedCapturesExpr(driver, sex.expression(), shadowed, inLambda);
                for (SwitchExprCase sc : sex.cases()) {
                    java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                    if (sc.value() instanceof PatternExpr pe) {
                        if (pe.varName() != null) inner.add(pe.varName());
                        inner.addAll(pe.fieldVars());
                    }
                    collectMutatedCapturesExpr(driver, sc.body(), inner, inLambda);
                }
                if (sex.defaultValue() != null) {
                    collectMutatedCapturesExpr(driver, sex.defaultValue(), shadowed, inLambda);
                }
            }
            case NewExpr ne -> {
                for (ExpressionNode arg : ne.arguments()) collectMutatedCapturesExpr(driver, arg, shadowed, inLambda);
            }
            case NewArrayExpr nae -> {
                collectMutatedCapturesExpr(driver, nae.size(), shadowed, inLambda);
                for (ExpressionNode dim : nae.moreDims()) collectMutatedCapturesExpr(driver, dim, shadowed, inLambda);
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else original)
        }
    }
}