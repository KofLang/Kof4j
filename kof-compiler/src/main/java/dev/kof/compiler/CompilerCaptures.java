package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Coleta de capturas de lambda: resolve nomes usados dentro da lambda
 * contra os locals do escopo externo.
 */
final class CompilerCaptures {

    private CompilerCaptures() {}

    static List<IRLocalVariable> collectCaptures(CompilerDriver driver, LambdaExpr le,
                    List<IRLocalVariable> outerLocals) {
        List<IRLocalVariable> captures = new ArrayList<>();
        java.util.Set<String> captured = new java.util.HashSet<>();
        java.util.Set<String> shadowed = new java.util.HashSet<>();
        for (FormalParameterNode p : le.parameters()) shadowed.add(p.name());
        collectCapturesStmts(driver,le.body(), outerLocals, captures, captured, shadowed);
        return captures;
    }

    static void collectCapturesStmts(CompilerDriver driver, StatementNode stmt,
                                      List<IRLocalVariable> outerLocals,
                                      List<IRLocalVariable> captures, java.util.Set<String> captured,
                                      java.util.Set<String> shadowed) {
        collectCapturesStmts(driver,List.of(stmt), outerLocals, captures, captured, shadowed);
    }

    static void collectCapturesStmts(CompilerDriver driver, List<StatementNode> body,
                                      List<IRLocalVariable> outerLocals,
                                      List<IRLocalVariable> captures, java.util.Set<String> captured,
                                      java.util.Set<String> shadowed) {
        for (StatementNode s : body) {
            if (s instanceof ExpressionStmt es) {
                collectCapturesExpr(driver,es.expression(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof ReturnStmt rs) {
                if (rs.value() != null) {
                    collectCapturesExpr(driver,rs.value(), outerLocals, captures, captured, shadowed);
                }
            } else if (s instanceof BlockStmt b) {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                collectCapturesStmts(driver,b.statements(), outerLocals, captures, captured, inner);
            } else if (s instanceof IfStmt i) {
                collectCapturesExpr(driver,i.condition(), outerLocals, captures, captured, shadowed);
                collectCapturesStmts(driver,i.thenBranch(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
                if (i.elseBranch() != null) {
                    collectCapturesStmts(driver,i.elseBranch(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
            } else if (s instanceof WhileStmt w) {
                collectCapturesExpr(driver,w.condition(), outerLocals, captures, captured, shadowed);
                collectCapturesStmts(driver,w.body(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
            } else if (s instanceof DoWhileStmt dw) {
                collectCapturesStmts(driver,dw.body(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
                collectCapturesExpr(driver,dw.condition(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof ForStmt f) {
                if (f.init() instanceof VarDeclStmt vds) {
                    collectCapturesVarDecl(driver,vds, outerLocals, captures, captured, shadowed);
                } else if (f.init() instanceof ExpressionStmt ies) {
                    collectCapturesExpr(driver,ies.expression(), outerLocals, captures, captured, shadowed);
                }
                if (f.condition() != null) {
                    collectCapturesExpr(driver,f.condition(), outerLocals, captures, captured, shadowed);
                }
                if (f.update() != null) {
                    collectCapturesExpr(driver,f.update(), outerLocals, captures, captured, shadowed);
                }
                collectCapturesStmts(driver,f.body(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
            } else if (s instanceof ForInStmt fi) {
                collectCapturesExpr(driver,fi.collection(), outerLocals, captures, captured, shadowed);
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                inner.add(fi.varName());
                collectCapturesStmts(driver,fi.body(), outerLocals, captures, captured, inner);
            } else if (s instanceof VarDeclStmt vds) {
                collectCapturesVarDecl(driver,vds, outerLocals, captures, captured, shadowed);
            } else if (s instanceof ThrowStmt ts) {
                collectCapturesExpr(driver,ts.expression(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof AssertStmt as) {
                collectCapturesExpr(driver,as.condition(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof SpawnStmt ss) {
                // spawn lambdas have their own (capture-free) scope.
                collectCapturesExpr(driver,ss.expression(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof SwitchStmt sw) {
                collectCapturesExpr(driver,sw.expression(), outerLocals, captures, captured, shadowed);
                for (SwitchCase c : sw.cases()) {
                    if (c.value() != null) {
                        collectCapturesExpr(driver,c.value(), outerLocals, captures, captured, shadowed);
                    }
                    collectCapturesStmts(driver,c.body(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
                if (sw.defaultBody() != null) {
                    collectCapturesStmts(driver,sw.defaultBody(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
            } else if (s instanceof TryStmt ts) {
                collectCapturesStmts(driver,ts.tryBody(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
                for (CatchClause cc : ts.catchClauses()) {
                    java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                    inner.add(cc.exceptionName());
                    collectCapturesStmts(driver,cc.body(), outerLocals, captures, captured, inner);
                }
                collectCapturesStmts(driver,ts.finallyBody(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
            }
        }
    }

    static void collectCapturesVarDecl(CompilerDriver driver, VarDeclStmt vds,
                                         List<IRLocalVariable> outerLocals,
                                        List<IRLocalVariable> captures, java.util.Set<String> captured,
                                        java.util.Set<String> shadowed) {
        if (vds.initializer() != null) {
            collectCapturesExpr(driver,vds.initializer(), outerLocals, captures, captured, shadowed);
        }
        shadowed.add(vds.name());
    }

    static void collectCapturesExpr(CompilerDriver driver, ExpressionNode expr,
                                         List<IRLocalVariable> outerLocals,
                                     List<IRLocalVariable> captures, java.util.Set<String> captured,
                                     java.util.Set<String> shadowed) {
        if (expr instanceof IdentifierExpr ie) {
            if (shadowed.contains(ie.name()) || captured.contains(ie.name())) return;
            IRLocalVariable outer = driver.findLocalVar(ie.name(), outerLocals);
            if (outer != null) {
                captures.add(outer);
                captured.add(ie.name());
            }
        } else if (expr instanceof BinaryExpr bin) {
            collectCapturesExpr(driver,bin.left(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(driver,bin.right(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof UnaryExpr ue) {
            collectCapturesExpr(driver,ue.operand(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof AssignmentExpr ae) {
            collectCapturesExpr(driver,ae.target(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(driver,ae.value(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) {
                collectCapturesExpr(driver,mc.receiver(), outerLocals, captures, captured, shadowed);
            }
            for (ExpressionNode arg : mc.arguments()) {
                collectCapturesExpr(driver,arg, outerLocals, captures, captured, shadowed);
            }
        } else if (expr instanceof FieldAccessExpr fa) {
            collectCapturesExpr(driver,fa.receiver(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectCapturesExpr(driver,aa.receiver(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(driver,aa.index(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof IfExpr iex) {
            collectCapturesExpr(driver,iex.condition(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(driver,iex.thenExpr(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(driver,iex.elseExpr(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof SwitchExpr sex) {
            collectCapturesExpr(driver,sex.expression(), outerLocals, captures, captured, shadowed);
            for (SwitchExprCase sc : sex.cases()) {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                if (sc.value() instanceof PatternExpr pe) {
                    if (pe.varName() != null) inner.add(pe.varName());
                    inner.addAll(pe.fieldVars());
                }
                collectCapturesExpr(driver,sc.body(), outerLocals, captures, captured, inner);
            }
            if (sex.defaultValue() != null) {
                collectCapturesExpr(driver,sex.defaultValue(), outerLocals, captures, captured, shadowed);
            }
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode arg : ne.arguments()) {
                collectCapturesExpr(driver,arg, outerLocals, captures, captured, shadowed);
            }
        } else if (expr instanceof NewArrayExpr nae) {
            collectCapturesExpr(driver,nae.size(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof LambdaExpr le2) {
            // lambda retornando lambda: variáveis livres do lambda INTERNO
            // que pertencem ao escopo do EXTERNO são capturas do externo —
            // o interno não pode alcançá-las por conta própria (o externo
            // precisa repassá-las via constructor). Os params/locals do
            // interno entram no shadowed para não virarem capturas.
            java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
            for (FormalParameterNode p : le2.parameters()) inner.add(p.name());
            collectCapturesStmts(driver,le2.body(), outerLocals, captures, captured, inner);
        }
    }

}