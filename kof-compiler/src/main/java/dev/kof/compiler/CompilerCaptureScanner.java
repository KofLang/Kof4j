package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Análise de capturas mutadas e declarações/lambdas no escopo da função.
 */
final class CompilerCaptureScanner {

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
        if (stmt instanceof ExpressionStmt es) {
            collectDeclaredVarNamesExpr(es.expression());
        } else if (stmt instanceof ReturnStmt rs) {
            if (rs.value() != null) collectDeclaredVarNamesExpr(rs.value());
        } else if (stmt instanceof BlockStmt b) {
            for (StatementNode s : b.statements()) collectDeclaredVarNamesStmt(s, out);
        } else if (stmt instanceof IfStmt i) {
            collectDeclaredVarNamesExpr(i.condition());
            collectDeclaredVarNamesStmt(i.thenBranch(), out);
            if (i.elseBranch() != null) collectDeclaredVarNamesStmt(i.elseBranch(), out);
        } else if (stmt instanceof WhileStmt w) {
            collectDeclaredVarNamesExpr(w.condition());
            collectDeclaredVarNamesStmt(w.body(), out);
        } else if (stmt instanceof DoWhileStmt dw) {
            collectDeclaredVarNamesStmt(dw.body(), out);
            collectDeclaredVarNamesExpr(dw.condition());
        } else if (stmt instanceof ForStmt f) {
            if (f.init() instanceof VarDeclStmt vds) {
                out.add(vds.name());
                if (vds.initializer() != null) collectDeclaredVarNamesExpr(vds.initializer());
            } else if (f.init() instanceof ExpressionStmt ies) {
                collectDeclaredVarNamesExpr(ies.expression());
            }
            if (f.condition() != null) collectDeclaredVarNamesExpr(f.condition());
            if (f.update() != null) collectDeclaredVarNamesExpr(f.update());
            collectDeclaredVarNamesStmt(f.body(), out);
        } else if (stmt instanceof ForInStmt fi) {
            collectDeclaredVarNamesExpr(fi.collection());
            collectDeclaredVarNamesStmt(fi.body(), out);
        } else if (stmt instanceof VarDeclStmt vds) {
            out.add(vds.name());
            if (vds.initializer() != null) collectDeclaredVarNamesExpr(vds.initializer());
        } else if (stmt instanceof ThrowStmt ts) {
            collectDeclaredVarNamesExpr(ts.expression());
        } else if (stmt instanceof AssertStmt as) {
            collectDeclaredVarNamesExpr(as.condition());
        } else if (stmt instanceof SpawnStmt ss) {
            collectDeclaredVarNamesExpr(ss.expression());
        } else if (stmt instanceof SwitchStmt sw) {
            collectDeclaredVarNamesExpr(sw.expression());
            for (SwitchCase c : sw.cases()) {
                if (c.value() != null) collectDeclaredVarNamesExpr(c.value());
                for (StatementNode s : c.body()) collectDeclaredVarNamesStmt(s, out);
            }
            if (sw.defaultBody() != null) {
                for (StatementNode s : sw.defaultBody()) collectDeclaredVarNamesStmt(s, out);
            }
        } else if (stmt instanceof TryStmt ts) {
            for (StatementNode s : ts.tryBody()) collectDeclaredVarNamesStmt(s, out);
            for (CatchClause cc : ts.catchClauses()) {
                for (StatementNode s : cc.body()) collectDeclaredVarNamesStmt(s, out);
            }
            if (ts.finallyBody() != null) {
                for (StatementNode s : ts.finallyBody()) collectDeclaredVarNamesStmt(s, out);
            }
        }
    }

    static void collectDeclaredVarNamesExpr(ExpressionNode expr) {
        if (expr instanceof LambdaExpr) {
            return; // declarações internas da lambda pertencem a ela
        }
        if (expr instanceof BinaryExpr be) {
            collectDeclaredVarNamesExpr(be.left());
            collectDeclaredVarNamesExpr(be.right());
        } else if (expr instanceof UnaryExpr ue) {
            collectDeclaredVarNamesExpr(ue.operand());
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) collectDeclaredVarNamesExpr(mc.receiver());
            for (ExpressionNode a : mc.arguments()) collectDeclaredVarNamesExpr(a);
        } else if (expr instanceof FieldAccessExpr fa) {
            collectDeclaredVarNamesExpr(fa.receiver());
        } else if (expr instanceof AssignmentExpr ae) {
            collectDeclaredVarNamesExpr(ae.target());
            collectDeclaredVarNamesExpr(ae.value());
        } else if (expr instanceof IfExpr iex) {
            collectDeclaredVarNamesExpr(iex.condition());
            collectDeclaredVarNamesExpr(iex.thenExpr());
            collectDeclaredVarNamesExpr(iex.elseExpr());
        } else if (expr instanceof SwitchExpr sex) {
            collectDeclaredVarNamesExpr(sex.expression());
            for (SwitchExprCase sc : sex.cases()) collectDeclaredVarNamesExpr(sc.body());
            if (sex.defaultValue() != null) collectDeclaredVarNamesExpr(sex.defaultValue());
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectDeclaredVarNamesExpr(aa.receiver());
            collectDeclaredVarNamesExpr(aa.index());
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode a : ne.arguments()) collectDeclaredVarNamesExpr(a);
        } else if (expr instanceof NewArrayExpr nae) {
            collectDeclaredVarNamesExpr(nae.size());
        }
    }

    /** Coleta todas as lambdas do corpo (para computar capturas reais). */
    static void collectLambdasStmt(StatementNode stmt, java.util.List<LambdaExpr> out) {
        if (stmt instanceof ExpressionStmt es) {
            collectLambdasExpr(es.expression(), out);
        } else if (stmt instanceof ReturnStmt rs) {
            if (rs.value() != null) collectLambdasExpr(rs.value(), out);
        } else if (stmt instanceof BlockStmt b) {
            for (StatementNode s : b.statements()) collectLambdasStmt(s, out);
        } else if (stmt instanceof IfStmt i) {
            collectLambdasExpr(i.condition(), out);
            collectLambdasStmt(i.thenBranch(), out);
            if (i.elseBranch() != null) collectLambdasStmt(i.elseBranch(), out);
        } else if (stmt instanceof WhileStmt w) {
            collectLambdasExpr(w.condition(), out);
            collectLambdasStmt(w.body(), out);
        } else if (stmt instanceof DoWhileStmt dw) {
            collectLambdasStmt(dw.body(), out);
            collectLambdasExpr(dw.condition(), out);
        } else if (stmt instanceof ForStmt f) {
            if (f.init() instanceof VarDeclStmt vds) {
                if (vds.initializer() != null) collectLambdasExpr(vds.initializer(), out);
            } else if (f.init() instanceof ExpressionStmt ies) {
                collectLambdasExpr(ies.expression(), out);
            }
            if (f.condition() != null) collectLambdasExpr(f.condition(), out);
            if (f.update() != null) collectLambdasExpr(f.update(), out);
            collectLambdasStmt(f.body(), out);
        } else if (stmt instanceof ForInStmt fi) {
            collectLambdasExpr(fi.collection(), out);
            collectLambdasStmt(fi.body(), out);
        } else if (stmt instanceof VarDeclStmt vds) {
            if (vds.initializer() != null) collectLambdasExpr(vds.initializer(), out);
        } else if (stmt instanceof ThrowStmt ts) {
            collectLambdasExpr(ts.expression(), out);
        } else if (stmt instanceof AssertStmt as) {
            collectLambdasExpr(as.condition(), out);
        } else if (stmt instanceof SpawnStmt ss) {
            collectLambdasExpr(ss.expression(), out);
        } else if (stmt instanceof SwitchStmt sw) {
            collectLambdasExpr(sw.expression(), out);
            for (SwitchCase c : sw.cases()) {
                if (c.value() != null) collectLambdasExpr(c.value(), out);
                for (StatementNode s : c.body()) collectLambdasStmt(s, out);
            }
            if (sw.defaultBody() != null) {
                for (StatementNode s : sw.defaultBody()) collectLambdasStmt(s, out);
            }
        } else if (stmt instanceof TryStmt ts) {
            for (StatementNode s : ts.tryBody()) collectLambdasStmt(s, out);
            for (CatchClause cc : ts.catchClauses()) {
                for (StatementNode s : cc.body()) collectLambdasStmt(s, out);
            }
            if (ts.finallyBody() != null) {
                for (StatementNode s : ts.finallyBody()) collectLambdasStmt(s, out);
            }
        }
    }

    static void collectLambdasExpr(ExpressionNode expr, java.util.List<LambdaExpr> out) {
        if (expr instanceof LambdaExpr le) {
            out.add(le);
            return;
        }
        if (expr instanceof BinaryExpr be) {
            collectLambdasExpr(be.left(), out);
            collectLambdasExpr(be.right(), out);
        } else if (expr instanceof UnaryExpr ue) {
            collectLambdasExpr(ue.operand(), out);
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) collectLambdasExpr(mc.receiver(), out);
            for (ExpressionNode a : mc.arguments()) collectLambdasExpr(a, out);
        } else if (expr instanceof FieldAccessExpr fa) {
            collectLambdasExpr(fa.receiver(), out);
        } else if (expr instanceof AssignmentExpr ae) {
            collectLambdasExpr(ae.target(), out);
            collectLambdasExpr(ae.value(), out);
        } else if (expr instanceof IfExpr iex) {
            collectLambdasExpr(iex.condition(), out);
            collectLambdasExpr(iex.thenExpr(), out);
            collectLambdasExpr(iex.elseExpr(), out);
        } else if (expr instanceof SwitchExpr sex) {
            collectLambdasExpr(sex.expression(), out);
            for (SwitchExprCase sc : sex.cases()) collectLambdasExpr(sc.body(), out);
            if (sex.defaultValue() != null) collectLambdasExpr(sex.defaultValue(), out);
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectLambdasExpr(aa.receiver(), out);
            collectLambdasExpr(aa.index(), out);
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode a : ne.arguments()) collectLambdasExpr(a, out);
        } else if (expr instanceof NewArrayExpr nae) {
            collectLambdasExpr(nae.size(), out);
        }
    }

    static void collectMutatedCapturesStmt(CompilerDriver driver, StatementNode stmt, java.util.Set<String> shadowed,
                                             boolean inLambda) {
        if (stmt instanceof ExpressionStmt es) {
            collectMutatedCapturesExpr(driver, es.expression(), shadowed, inLambda);
        } else if (stmt instanceof ReturnStmt rs) {
            if (rs.value() != null) collectMutatedCapturesExpr(driver, rs.value(), shadowed, inLambda);
        } else if (stmt instanceof BlockStmt b) {
            java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
            for (StatementNode s : b.statements()) collectMutatedCapturesStmt(driver, s, inner, inLambda);
        } else if (stmt instanceof IfStmt i) {
            collectMutatedCapturesExpr(driver, i.condition(), shadowed, inLambda);
            collectMutatedCapturesStmt(driver, i.thenBranch(), new java.util.HashSet<>(shadowed), inLambda);
            if (i.elseBranch() != null) collectMutatedCapturesStmt(driver, i.elseBranch(), new java.util.HashSet<>(shadowed), inLambda);
        } else if (stmt instanceof WhileStmt w) {
            collectMutatedCapturesExpr(driver, w.condition(), shadowed, inLambda);
            collectMutatedCapturesStmt(driver, w.body(), new java.util.HashSet<>(shadowed), inLambda);
        } else if (stmt instanceof DoWhileStmt dw) {
            collectMutatedCapturesStmt(driver, dw.body(), new java.util.HashSet<>(shadowed), inLambda);
            collectMutatedCapturesExpr(driver, dw.condition(), shadowed, inLambda);
        } else if (stmt instanceof ForStmt f) {
            if (f.init() instanceof VarDeclStmt vds) {
                collectMutatedCapturesStmt(driver, vds, shadowed, inLambda);
            } else if (f.init() instanceof ExpressionStmt ies) {
                collectMutatedCapturesExpr(driver, ies.expression(), shadowed, inLambda);
            }
            if (f.condition() != null) collectMutatedCapturesExpr(driver, f.condition(), shadowed, inLambda);
            if (f.update() != null) collectMutatedCapturesExpr(driver, f.update(), shadowed, inLambda);
            collectMutatedCapturesStmt(driver, f.body(), new java.util.HashSet<>(shadowed), inLambda);
        } else if (stmt instanceof ForInStmt fi) {
            collectMutatedCapturesExpr(driver, fi.collection(), shadowed, inLambda);
            java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
            inner.add(fi.varName());
            collectMutatedCapturesStmt(driver, fi.body(), inner, inLambda);
        } else if (stmt instanceof VarDeclStmt vds) {
            shadowed.add(vds.name());
            if (vds.initializer() != null) collectMutatedCapturesExpr(driver, vds.initializer(), shadowed, inLambda);
        } else if (stmt instanceof ThrowStmt ts) {
            collectMutatedCapturesExpr(driver, ts.expression(), shadowed, inLambda);
        } else if (stmt instanceof AssertStmt as) {
            collectMutatedCapturesExpr(driver, as.condition(), shadowed, inLambda);
        } else if (stmt instanceof SpawnStmt ss) {
            collectMutatedCapturesExpr(driver, ss.expression(), shadowed, inLambda);
        } else if (stmt instanceof SwitchStmt sw) {
            collectMutatedCapturesExpr(driver, sw.expression(), shadowed, inLambda);
            for (SwitchCase c : sw.cases()) {
                if (c.value() != null) collectMutatedCapturesExpr(driver, c.value(), shadowed, inLambda);
                for (StatementNode s : c.body()) collectMutatedCapturesStmt(driver, s, new java.util.HashSet<>(shadowed), inLambda);
            }
            if (sw.defaultBody() != null) {
                for (StatementNode s : sw.defaultBody()) collectMutatedCapturesStmt(driver, s, new java.util.HashSet<>(shadowed), inLambda);
            }
        } else if (stmt instanceof TryStmt ts) {
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
    }

    static void collectMutatedCapturesExpr(CompilerDriver driver, ExpressionNode expr, java.util.Set<String> shadowed,
                                             boolean inLambda) {
        if (expr instanceof LambdaExpr le) {
            for (StatementNode s : le.body()) {
                collectMutatedCapturesStmt(driver, s, new java.util.HashSet<>(), true);
            }
        } else if (expr instanceof AssignmentExpr ae) {
            if (ae.target() instanceof IdentifierExpr ie && !shadowed.contains(ie.name())
                    && (inLambda || driver.lambdaCapturedNames.contains(ie.name()))) {
                driver.mutatedCapturedNames.add(ie.name());
            }
            collectMutatedCapturesExpr(driver, ae.target(), shadowed, inLambda);
            collectMutatedCapturesExpr(driver, ae.value(), shadowed, inLambda);
        } else if (expr instanceof UnaryExpr ue) {
            if (inLambda && ue.operand() instanceof IdentifierExpr ie && !shadowed.contains(ie.name())
                    && ("++".equals(ue.operator()) || "--".equals(ue.operator()))) {
                driver.mutatedCapturedNames.add(ie.name());
            }
            collectMutatedCapturesExpr(driver, ue.operand(), shadowed, inLambda);
        } else if (expr instanceof BinaryExpr bin) {
            collectMutatedCapturesExpr(driver, bin.left(), shadowed, inLambda);
            collectMutatedCapturesExpr(driver, bin.right(), shadowed, inLambda);
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) collectMutatedCapturesExpr(driver, mc.receiver(), shadowed, inLambda);
            for (ExpressionNode arg : mc.arguments()) collectMutatedCapturesExpr(driver, arg, shadowed, inLambda);
        } else if (expr instanceof FieldAccessExpr fa) {
            collectMutatedCapturesExpr(driver, fa.receiver(), shadowed, inLambda);
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectMutatedCapturesExpr(driver, aa.receiver(), shadowed, inLambda);
            collectMutatedCapturesExpr(driver, aa.index(), shadowed, inLambda);
        } else if (expr instanceof IfExpr iex) {
            collectMutatedCapturesExpr(driver, iex.condition(), shadowed, inLambda);
            collectMutatedCapturesExpr(driver, iex.thenExpr(), shadowed, inLambda);
            collectMutatedCapturesExpr(driver, iex.elseExpr(), shadowed, inLambda);
        } else if (expr instanceof SwitchExpr sex) {
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
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode arg : ne.arguments()) collectMutatedCapturesExpr(driver, arg, shadowed, inLambda);
        } else if (expr instanceof NewArrayExpr nae) {
            collectMutatedCapturesExpr(driver, nae.size(), shadowed, inLambda);
        }
    }
}