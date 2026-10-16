package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Coleta de capturas de lambda: resolve nomes usados dentro da lambda
 * contra os locals do escopo externo.
 */
public final class CompilerCaptures {

    private CompilerCaptures() {}

    /**
     * #381 — push do valor de uma captura no creation-site. Capturas normais
     * são locals (`cap.index() >= 0`); capturas de campo (índice sentinel < 0,
     * criadas pelo collector) são lidas de `this` da classe ENVOLVENTE — o
     * creation-site está dentro do método externo, onde slot 0 é o receiver
     * real e o campo existe.
     */
    static void pushCapture(CompilerDriver driver, List<KofOperation> ops, IRLocalVariable cap) {
        if (cap.index() < 0) {
            Type encType = CompilerTypes.ownerTypeFromInternal(
                    driver.currentLoweringOwner, driver.semanticAnalyzer);
            ops.add(new KofLoadLocal(encType, 0));
            ops.add(new KofLoadField(encType, cap.name(), cap.type()));
        } else {
            ops.add(new KofLoadLocal(cap.type(), cap.index()));
        }
    }

    static List<IRLocalVariable> collectCaptures(CompilerDriver driver, LambdaExpr le,
                    List<IRLocalVariable> outerLocals) {        List<IRLocalVariable> captures = new ArrayList<>();
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
            switch (s) {
                case ExpressionStmt es -> {
                    collectCapturesExpr(driver,es.expression(), outerLocals, captures, captured, shadowed);
                }
                case ReturnStmt rs -> {
                    if (rs.value() != null) {
                        collectCapturesExpr(driver,rs.value(), outerLocals, captures, captured, shadowed);
                    }
                }
                case BlockStmt b -> {
                    java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                    collectCapturesStmts(driver,b.statements(), outerLocals, captures, captured, inner);
                }
                case IfStmt i -> {
                    collectCapturesExpr(driver,i.condition(), outerLocals, captures, captured, shadowed);
                    collectCapturesStmts(driver,i.thenBranch(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                    if (i.elseBranch() != null) {
                        collectCapturesStmts(driver,i.elseBranch(), outerLocals, captures, captured,
                                new java.util.HashSet<>(shadowed));
                    }
                }
                case WhileStmt w -> {
                    collectCapturesExpr(driver,w.condition(), outerLocals, captures, captured, shadowed);
                    collectCapturesStmts(driver,w.body(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
                case DoWhileStmt dw -> {
                    collectCapturesStmts(driver,dw.body(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                    collectCapturesExpr(driver,dw.condition(), outerLocals, captures, captured, shadowed);
                }
                case ForStmt f -> {
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
                }
                case ForInStmt fi -> {
                    collectCapturesExpr(driver,fi.collection(), outerLocals, captures, captured, shadowed);
                    java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                    inner.add(fi.varName());
                    collectCapturesStmts(driver,fi.body(), outerLocals, captures, captured, inner);
                }
                case VarDeclStmt vds -> {
                    collectCapturesVarDecl(driver,vds, outerLocals, captures, captured, shadowed);
                }
                case ThrowStmt ts -> {
                    collectCapturesExpr(driver,ts.expression(), outerLocals, captures, captured, shadowed);
                }
                case AssertStmt as -> {
                    collectCapturesExpr(driver,as.condition(), outerLocals, captures, captured, shadowed);
                }
                case SpawnStmt ss -> {
                    // spawn lambdas have their own (capture-free) scope.
                    collectCapturesExpr(driver,ss.expression(), outerLocals, captures, captured, shadowed);
                }
                case SwitchStmt sw -> {
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
                }
                case TryStmt ts -> {
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
                case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else original)
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

    /**
     * #846 (`known-bugs` §735): chamada **NUA** (`somar(n)`) de um método de
     * INSTÂNCIA da classe ENVOLVENTE — o receptor implícito é o `this` EXTERNO,
     * não a própria lambda. Sem esta captura, o corpo da lambda resolve o
     * receptor para o seu próprio slot 0 (`locals[0] = ("this", Lambda0)`) e:
     * JVM recusa a classe (`VerifyError: Bad type on operand stack`), Script
     * imprime o objeto (valor ERRADO e silencioso), JS lança em runtime e o
     * nativo dá SIGSEGV — medido nos quatro alvos.
     *
     * <p>O caminho EXPLÍCITO (`this.somar(n)`) já funciona porque menciona `this`
     * e a captura acontece; aqui a chamada nua não menciona nada. A resolução é
     * pela CLASSE + varredura de membro/aridade (`semanticAnalyzer.getClass` +
     * `members().resolve`) — o lookup por nome simples no escopo não bastava (§735).
     * Captura-se o `this` na ordem em que o `CompilerLambdaClass` re-hospeda as
     * capturas DEPOIS de `locals[0]`, e `findLocalVar` varre de trás para frente,
     * então a captura vence o slot 0.
     */
    private static void captureEnclosingThis(CompilerDriver driver, MethodCallExpr mc,
                                             List<IRLocalVariable> outerLocals,
                                             List<IRLocalVariable> captures,
                                             java.util.Set<String> captured) {
        if (driver.semanticAnalyzer == null || captured.contains("this")) return;
        String enclosing = driver.currentLoweringOwner;
        if (enclosing == null || enclosing.isEmpty()) return;
        String className = enclosing.substring(enclosing.lastIndexOf('/') + 1);
        SymbolTable.ClassSymbol cls = driver.semanticAnalyzer.getClass(className);
        if (cls == null) return;
        SymbolTable.Symbol sym = cls.members().resolve(mc.methodName());
        if (!(sym instanceof SymbolTable.MethodSymbol ms)) return;
        if ((ms.accessFlags() & AccessFlags.STATIC) != 0) return;
        if (ms.parameterTypes().size() != mc.arguments().size()) return;
        IRLocalVariable outerThis = driver.findLocalVar("this", outerLocals);
        if (outerThis == null) return;
        captures.add(outerThis);
        captured.add("this");
    }

    static void collectCapturesExpr(CompilerDriver driver, ExpressionNode expr,
                                         List<IRLocalVariable> outerLocals,
                                     List<IRLocalVariable> captures, java.util.Set<String> captured,
                                     java.util.Set<String> shadowed) {
        switch (expr) {
            case IdentifierExpr ie -> {
                if (shadowed.contains(ie.name()) || captured.contains(ie.name())) return;
                IRLocalVariable outer = driver.findLocalVar(ie.name(), outerLocals);
                if (outer != null) {
                    captures.add(outer);
                    captured.add(ie.name());
                    return;
                }
                // #381 — identificador livre que é campo NÃO-estático da classe
                // envolvente (lambda criada dentro de método de instância) deve
                // ser capturado por VALOR: lido no creation-site, onde `this` é o
                // receiver real. Sem isto, o corpo não resolvia o nome (o owner
                // da lambda não tem o campo) e caía no fallback aload_0 do
                // emitter — empurrando o objeto Lambda no lugar do valor →
                // VerifyError no JVM, "not an int: Lambda0@…" no script.
                // Índice < 0 é o sentinel do campo (o push do creation-site lê
                // `this`+getfield em vez do local). Campos estáticos ficam com o
                // GETSTATIC do próprio corpo; campo de instância em método
                // estático já é barrado por SEM075 (#345), então `this` no push
                // só existe quando existe receiver.
                String enclosing = driver.currentLoweringOwner;
                if (enclosing != null && !enclosing.isEmpty() && driver.semanticAnalyzer != null) {
                    String className = enclosing.substring(enclosing.lastIndexOf('/') + 1);
                    SymbolTable.Symbol fieldSym =
                            HierarchyResolver.resolveFieldInHierarchy(className, ie.name(), driver.semanticAnalyzer);
                    if (fieldSym instanceof SymbolTable.FieldSymbol fs
                            && (fs.accessFlags() & AccessFlags.STATIC) == 0) {
                        captures.add(new IRLocalVariable(-1, ie.name(), fs.type()));
                        captured.add(ie.name());
                    }
                }
            }
            case BinaryExpr bin -> {
                collectCapturesExpr(driver,bin.left(), outerLocals, captures, captured, shadowed);
                collectCapturesExpr(driver,bin.right(), outerLocals, captures, captured, shadowed);
            }
            case UnaryExpr ue -> {
                collectCapturesExpr(driver,ue.operand(), outerLocals, captures, captured, shadowed);
            }
            case AssignmentExpr ae -> {
                collectCapturesExpr(driver,ae.target(), outerLocals, captures, captured, shadowed);
                collectCapturesExpr(driver,ae.value(), outerLocals, captures, captured, shadowed);
            }
            case MethodCallExpr mc -> {
                if (mc.receiver() != null) {
                    collectCapturesExpr(driver,mc.receiver(), outerLocals, captures, captured, shadowed);
                } else if (!shadowed.contains(mc.methodName()) && !captured.contains(mc.methodName())) {
                    // Chamada "nua" (sem receiver, ex.: `f(x)`) pode ser uma variável
                    // local de tipo função (`var f = () -> {...}`), não só uma função
                    // top-level — o parser não distingue as duas formas. Sem este
                    // check, um lambda que chama outro lambda capturado do escopo
                    // externo perdia essa referência (não virava campo da classe
                    // gerada) e falhava em runtime: "ReferenceError: f is not defined".
                    IRLocalVariable outerFn = driver.findLocalVar(mc.methodName(), outerLocals);
                    if (outerFn != null) {
                        captures.add(outerFn);
                        captured.add(mc.methodName());
                    } else {
                        captureEnclosingThis(driver, mc, outerLocals, captures, captured);
                    }
                }
                for (ExpressionNode arg : mc.arguments()) {
                    collectCapturesExpr(driver,arg, outerLocals, captures, captured, shadowed);
                }
            }
            case FieldAccessExpr fa -> {
                collectCapturesExpr(driver,fa.receiver(), outerLocals, captures, captured, shadowed);
            }
            case ArrayAccessExpr aa -> {
                collectCapturesExpr(driver,aa.receiver(), outerLocals, captures, captured, shadowed);
                collectCapturesExpr(driver,aa.index(), outerLocals, captures, captured, shadowed);
            }
            case IfExpr iex -> {
                collectCapturesExpr(driver,iex.condition(), outerLocals, captures, captured, shadowed);
                collectCapturesExpr(driver,iex.thenExpr(), outerLocals, captures, captured, shadowed);
                collectCapturesExpr(driver,iex.elseExpr(), outerLocals, captures, captured, shadowed);
            }
            case SwitchExpr sex -> {
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
            }
            case NewExpr ne -> {
                for (ExpressionNode arg : ne.arguments()) {
                    collectCapturesExpr(driver,arg, outerLocals, captures, captured, shadowed);
                }
            }
            case NewArrayExpr nae -> {
                collectCapturesExpr(driver,nae.size(), outerLocals, captures, captured, shadowed);
                for (ExpressionNode dim : nae.moreDims()) {
                    collectCapturesExpr(driver, dim, outerLocals, captures, captured, shadowed);
                }
            }
            case LambdaExpr le2 -> {
                // lambda retornando lambda: variáveis livres do lambda INTERNO
                // que pertencem ao escopo do EXTERNO são capturas do externo —
                // o interno não pode alcançá-las por conta própria (o externo
                // precisa repassá-las via constructor). Os params/locals do
                // interno entram no shadowed para não virarem capturas.
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                for (FormalParameterNode p : le2.parameters()) inner.add(p.name());
                collectCapturesStmts(driver,le2.body(), outerLocals, captures, captured, inner);
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else original)
        }
    }

}