package dev.kof.compiler;

import java.util.List;

/**
 * Análise de statements/corpos, extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Mantém a ordem exata dos diagnósticos SEM0xx.
 */
public final class StatementAnalyzer {

    private StatementAnalyzer() {}

    static void analyzeBody(SemanticAnalyzer sa, List<StatementNode> body, SymbolTable scope, Type returnType) {
        for (StatementNode stmt : body) {
            analyzeStatement(sa, stmt, scope, returnType);
        }
        // D-MEMORY-SAFETY Fase 3 fatia 1 (D-COMPLETE-FIRST): faces retilineas
        // O-01/MEM001 + O-02/MEM002 — analise de claim/close, conservadora.
        dev.kof.compiler.memory.OwnershipPass.analyze(sa.diagnostics(), body);
    }

    static void analyzeStatement(SemanticAnalyzer sa, StatementNode stmt, SymbolTable scope, Type returnType) {
        DiagnosticCollector diag = sa.diagnostics();
        SourcePosition prev = diag == null ? null : diag.fallbackPosition();
        if (diag != null && stmt != null && stmt.position() != null) diag.setFallbackPosition(stmt.position());
        try {
            analyzeStatementAt(sa, stmt, scope, returnType);
        } finally {
            if (diag != null) diag.setFallbackPosition(prev);
        }
    }

    private static void analyzeStatementAt(SemanticAnalyzer sa, StatementNode stmt, SymbolTable scope, Type returnType) {
        switch (stmt) {
            case BlockStmt block -> {
                SymbolTable blockScope = scope.enterScope();
                for (StatementNode s : block.statements()) analyzeStatement(sa, s, blockScope, returnType);
            }
            case TryStmt tryStmt -> {
                // corpos de try/catch/finally eram ignorados pela análise
                // semântica (ex.: `throw 42` dentro de try passava e gerava
                // bytecode inválido). Analisa os três blocos.
                SymbolTable tryScope = scope.enterScope();
                for (StatementNode s : tryStmt.tryBody()) analyzeStatement(sa, s, tryScope, returnType);
                for (CatchClause cc : tryStmt.catchClauses()) {
                    // #332/#328: valida o TIPO do catch uma vez aqui (compartilhado
                    // por JVM/Native/JS/interpreter) — `catch (Int e)` e `catch
                    // (Foo e)` de classe não-throwable compilavam em silêncio e
                    // morriam no load (NoClassDefFoundError / VerifyError).
                    CatchTypeCheck.check(sa, cc);
                    SymbolTable catchScope = scope.enterScope();
                    if (cc.exceptionName() != null) {
                        // #163: `catch (RuntimeException e)` precisa do tipo
                        // qualificado (java.lang) para o dispatch de método e
                        // o descriptor JVM não saírem `LRuntimeException;`.
                        Type excType = CompilerTypes.exceptionType(cc.exceptionType(), sa.unit());
                        catchScope.define(new SymbolTable.LocalVariableSymbol(cc.exceptionName(), excType, 0));
                    }
                    for (StatementNode s : cc.body()) analyzeStatement(sa, s, catchScope, returnType);
                }
                if (tryStmt.finallyBody() != null) {
                    SymbolTable finScope = scope.enterScope();
                    for (StatementNode s : tryStmt.finallyBody()) analyzeStatement(sa, s, finScope, returnType);
                }
            }
            case VarDeclStmt vds -> {
                Type varType;
                // SG-005/008 (SEM048): o literal `null` NUNCA é atribuível —
                // null safety é por narrowing (`if (x != null)`), nunca por
                // atribuição direta (o próprio nome já diz). APIs devolvem T?;
                // o programador não fabrica null.
                if (vds.initializer() != null && CompilerComparisons.isNullLiteral(vds.initializer())
                        && sa.diagnostics() != null) {
                    sa.diagnostics().error(vds,
                            "null cannot be assigned: null safety works by narrowing"
                                    + " (if (x != null)), never by direct null literals"
                                    + " (variable '" + vds.name() + "')",
                            "SEM048");
                }
                // §253 face A (16/09): `var id = time.interval(…, () -> cancel(id))` —
                // o inicializador contém lambda que LÊ a var em declaração. Hoje o
                // escopo só define `id` DEPOIS de tipar o inicializador → SEM011 nos
                // 3 alvos. Pre-define UNKNOWN ANTES da inferência (a inferência do
                // corpo não depende do tipo do self-ref); o tipo real entra no
                // define final. Nos alvos NATIVE* a leitura do handle capturado
                // SIGSEGVa (face B, §253/nat) — abrir lá converteria SEM011 (alto)
                // em crash: gate SEM092 em compile-time, nunca runtime silencioso (R6).
                boolean selfCapture = vds.initializer() != null
                        && CompilerCaptureScanner.lambdaExprReadsName(vds.initializer(), vds.name());
                boolean selfPredefined = false;
                if (selfCapture && sa.target().isNative()) {
                    if (sa.diagnostics() != null) {
                        sa.diagnostics().error(vds,
                                "self-referencing initializer var inside a lambda "
                                        + "(var '" + vds.name() + "') is not available on the "
                                        + sa.target() + " target yet — captured handle read in the "
                                        + "job crashes the worker (§253 face B, native lane); use "
                                        + "the shadow-handle idiom (var id=\"\"; job reads id after "
                                        + "assignment; id = time.interval(…) after) (SEM092)",
                                "SEM092");
                    }
                } else if (selfCapture && !scope.hasLocal(vds.name())) {
                    scope.define(new SymbolTable.LocalVariableSymbol(vds.name(),
                            Type.UnknownType.UNKNOWN, 0,
                            vds.type() != null && "val".equals(vds.type())));
                    selfPredefined = true;
                }
                // "val"/"var" são palavras-chave de mutabilidade, não tipos —
                // o tipo real vem do initializer (ou do type explícito após ':').
                if (vds.type() != null && !vds.type().isEmpty()
                        && !"var".equals(vds.type()) && !"val".equals(vds.type())) {
                    Type viaImports = MemberResolver.qualifyViaImports(sa.unit(), vds.type(),
                            sa.externalTypes());
                    varType = viaImports != null ? viaImports : Type.of(vds.type());
                    // §249: tipo explícito que não resolve para NENHUM tipo
                    // conhecido era aceito em silêncio (`Foo x`/`s length` viravam
                    // uma declaração-lixo invisível, R6). Diagnostica na raiz.
                    if (sa.diagnostics() != null
                            && MemberResolver.isUnresolvedSimpleType(sa, vds.type(), scope)) {
                        sa.diagnostics().error(vds,
                                "Undefined variable or type: '" + vds.type() + "'", "SEM011");
                    }
                } else if (vds.initializer() != null) {
                    varType = SemExpressionTyper.inferType(sa, vds.initializer(), scope);
                } else {
                    varType = Type.UnknownType.UNKNOWN;
                }
                // SC5: redeclaração no MESMO escopo é erro
                if (scope.hasLocal(vds.name()) && sa.diagnostics() != null
                        && !selfPredefined) {
                    sa.diagnostics().error(vds,
                            "variable '" + vds.name() + "' is already defined in this scope",
                            "SEM024");
                }
                if (vds.initializer() != null) SemExpressionTyper.inferType(sa, vds.initializer(), scope);
                // SC2: tipo explícito ≠ tipo do inicializador
                if (sa.diagnostics() != null && vds.initializer() != null
                        && !varType.equals(Type.UnknownType.UNKNOWN)) {
                    Type initType = SemExpressionTyper.inferType(sa, vds.initializer(), scope);
                    boolean ftIssue = initType instanceof Type.FunctionType
                            && varType instanceof Type.FunctionType;
                    if (!initType.equals(Type.UnknownType.UNKNOWN)
                            && !TypeChecker.isAssignable(sa, initType, varType)
                            && !(ftIssue && TypeChecker.functionTypesConform(initType, varType))) {
                        sa.diagnostics().error(vds,
                                "type mismatch: cannot assign " + initType
                                        + " to '" + vds.name() + ": " + varType + "'",
                                "SEM021");
                    }
                }
                scope.define(new SymbolTable.LocalVariableSymbol(vds.name(), varType, 0,
                        vds.type() != null && "val".equals(vds.type())));
            }
            case ReturnStmt ret -> {
                if (ret.value() != null) {
                    Type valueType = SemExpressionTyper.inferType(sa, ret.value(), scope);
                    sa.putExpressionType(ret.value(), valueType);
                    if (sa.currentExplicitVoid && sa.diagnostics() != null) {
                        sa.diagnostics().error(ret.position() != null ? ret.position().file() : "", 0, 0, 0,
                                "void function cannot return a value - drop the value (bare `return` exits) or declare a return type",
                                "SEM093");
                    }
                    if (sa.diagnostics() != null && !Type.isUnknown(returnType) && !Type.isVoid(returnType)
                            && !Type.isUnknown(valueType) && !TypeChecker.isAssignable(sa, valueType, returnType)) {
                        // #502: use the return statement's own position so the error points at the
                        // offending `return` line rather than emitting the useless 0:0 default.
                        SourcePosition rp = ret.position();
                        sa.diagnostics().error(rp != null ? rp.file() : "",
                                rp != null ? rp.line() : 0,
                                rp != null ? rp.column() : 0, 0,
                                "Return type mismatch: expected '" + Type.display(returnType) + "' but got '" + Type.display(valueType) + "'", "SEM010");
                    }
                }
            }
            case BreakStmt _ -> {}
            case ContinueStmt _ -> {}
            case IfStmt ifStmt -> {
                // Nullability narrowing (SG-005):
                //   if (x != null) → x: T no THEN
                //   if (x == null) → x: T no ELSE
                //   if (x != null && Y) / if (Y && x != null) → x: T no THEN
                //     (a conjunção garante que o ramo tomado satisfaz TODOS)
                SymbolTable ifScope = scope.enterScope();
                java.util.List<SymbolTable.LocalVariableSymbol> thenNarrow = new java.util.ArrayList<>();
                java.util.List<SymbolTable.LocalVariableSymbol> elseNarrow = new java.util.ArrayList<>();
                // A condição do `if` PRECISA ser tipada (como while/for/assert):
                // sem inferType, chamadas dentro dela não entram em
                // resolvedMethods/expressionTypes e o lowering cai no fallback
                // que usa o tipo SUBSTITUÍDO (T→String) como descriptor do call
                // — `Box<String>.get()` virava `()Ljava/lang/String;` em vez do
                // apagado `()Ljava/lang/Object;` → NoSuchMethodError (#161 face 2).
                SemExpressionTyper.inferType(sa, ifStmt.condition(), scope);
                collectNarrowing(sa, ifStmt.condition(), scope, thenNarrow, elseNarrow, false);
                for (SymbolTable.LocalVariableSymbol s : thenNarrow) ifScope.define(s);
                if (!elseNarrow.isEmpty() && ifStmt.elseBranch() != null) {
                    SymbolTable elseScope = scope.enterScope();
                    for (SymbolTable.LocalVariableSymbol s : elseNarrow) elseScope.define(s);
                    analyzeStatement(sa, ifStmt.elseBranch(), elseScope, returnType);
                } else {
                    analyzeStatement(sa, ifStmt.thenBranch(), ifScope, returnType);
                    // a892b3c5 (lane CodeQL) removeu esta linha junto com a var
                    // `condType` marcada como unread — mas ela NÃO era unread:
                    // sem analisar o ELSE, os tipos das expressões do ramo else
                    // não entram em sa.expressionTypes() e o lowering JVM gera
                    // frames inválidos (Supervisor.lacoUnico: AIOOBE em
                    // COMPUTE_FRAMES). Restaurado (fix-forward, regra 8).
                    if (ifStmt.elseBranch() != null) analyzeStatement(sa, ifStmt.elseBranch(), scope, returnType);
                }
                // §353 revealed (makealive fsRead): `if (t == null) { return }`
                // seguido de `t.foo()` e o MESMO narrowing de SG-005 em forma
                // de early-return — sem else, o fluxo que continua e o ramo
                // FALSO, onde elseNarrow vale. So quando o THEN garante saida
                // (return/throw), senao o null pode cair aqui adiante.
                if (ifStmt.elseBranch() == null && !elseNarrow.isEmpty()
                        && Narrowing.thenBranchExits(ifStmt.thenBranch())) {
                    for (SymbolTable.LocalVariableSymbol s : elseNarrow) scope.define(s);
                }
            }
            case WhileStmt ws -> {
                SemExpressionTyper.inferType(sa, ws.condition(), scope);
                SymbolTable whileScope = scope.enterScope();
                // D-NARROW-WHILE (#159): `while (s != null)` narrowa `s` no
                // corpo como o THEN do `if` — o corpo só roda com a condição
                // verdadeira (o `if` já fazia; o while era o falso-positivo).
                java.util.List<SymbolTable.LocalVariableSymbol> bodyNarrow = new java.util.ArrayList<>();
                collectNarrowing(sa, ws.condition(), scope, bodyNarrow,
                        new java.util.ArrayList<>(), false);
                for (SymbolTable.LocalVariableSymbol s : bodyNarrow) whileScope.define(s);
                analyzeStatement(sa, ws.body(), whileScope, returnType);
            }
            case DoWhileStmt dws -> {
                SymbolTable doScope = scope.enterScope();
                analyzeStatement(sa, dws.body(), doScope, returnType);
                SemExpressionTyper.inferType(sa, dws.condition(), doScope);
            }
            case ForStmt fs -> {
                SymbolTable forScope = scope.enterScope();
                if (fs.init() != null) analyzeStatement(sa, fs.init(), forScope, returnType);
                if (fs.condition() != null) SemExpressionTyper.inferType(sa, fs.condition(), forScope);
                analyzeStatement(sa, fs.body(), forScope, returnType);
                if (fs.update() != null) {
                    // `i = i + 1` no update é statement, não valor — passa
                    // pelo MESMO checkpoint de atribuição (SEM012/SEM037/
                    // SEM038); o bypass anterior deixava `for (val i = 0;
                    // ...; i = i + 1)` silencioso (buraco #42 no guard do val).
                    if (fs.update() instanceof AssignmentExpr ae) {
                        SemAssignmentAnalyzer.analyzeAssignmentStatement(sa, ae, forScope);
                    } else {
                        SemExpressionTyper.inferType(sa, fs.update(), forScope);
                    }
                }
            }
            case ForInStmt fis -> {
                SymbolTable forScope = scope.enterScope();
                Type collType = SemExpressionTyper.inferType(sa, fis.collection(), forScope);
                Type elemType = Type.UnknownType.UNKNOWN;
                if (collType instanceof Type.ClassType ct && "List".equals(ct.name()) && !ct.typeArguments().isEmpty()) {
                    elemType = ct.typeArguments().get(0);
                } else if (collType instanceof Type.ArrayType at) {
                    elemType = at.componentType();
                } else if (sa.diagnostics() != null && isNonIterableForIn(collType)) {
                    // bug 145 (espelha o bug 103/SEM054): `for (var c in "abc")`
                    // era ACEITO e quebrava de um jeito em cada target — JVM
                    // VerifyError `arraylength` em String (a classe nem carrega),
                    // Native SIGSEGV, Script "Argument is not an array" e JS
                    // iterava chars em silêncio (divergência cross-target, R6).
                    // `for-in` só itera List<T>/array; rejeitar em compile-time.
                    SourcePosition pos = fis.position();
                    sa.diagnostics().error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            "`for-in` only iterates over `List<T>` or arrays in Kof; for String use "
                                    + "`s.charAt(i)` in a numeric loop",
                            "SEM058");
                }
                forScope.define(new SymbolTable.LocalVariableSymbol(fis.varName(), elemType, 0));
                analyzeStatement(sa, fis.body(), forScope, returnType);
            }
            case SwitchStmt ss -> {
                SemExpressionTyper.inferType(sa, ss.expression(), scope);
                SymbolTable switchScope = scope.enterScope();
                for (SwitchCase sc : ss.cases()) {
                    if (sc.value() instanceof PatternExpr pe) {
                        Type patType = MemberResolver.resolveType(sa, pe.typeName(), scope);
                        if (patType == null) patType = Type.UnknownType.UNKNOWN;
                        SymbolTable caseScope = switchScope.enterScope();
                        if (pe.varName() != null) {
                            caseScope.define(new SymbolTable.LocalVariableSymbol(pe.varName(), patType, 0));
                        }
                        // SG-014: guarda analisada com a var do pattern bound
                        if (pe.guard() != null) {
                            SemExpressionTyper.inferType(sa, pe.guard(), caseScope);
                        }
                        if (!pe.fieldVars().isEmpty()) {
                            String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
                            SymbolTable.ClassSymbol cls = sa.getClass(simple);
                            if (cls == null) {
                                // Try via scope resolve
                                Type t2 = MemberResolver.resolveType(sa, pe.typeName(), scope);
                                if (t2 instanceof Type.ClassType ct2) cls = sa.getClass(ct2.name());
                            }
                            java.util.List<String> fieldNames = pe.fieldVars();
                            for (int i = 0; i < fieldNames.size(); i++) {
                                String fv = fieldNames.get(i);
                                Type fieldType = Type.UnknownType.UNKNOWN;
                                if (cls != null) {
                                    // Try to find field by index or name
                                    var members = cls.members();
                                    // For records, fields are in order; try to get by index
                                    java.util.List<SymbolTable.Symbol> fields = new java.util.ArrayList<>();
                                    for (var e : members.localSymbols().values()) {
                                        if (e instanceof SymbolTable.FieldSymbol) fields.add(e);
                                    }
                                    // If fieldVars size matches record field count, use positional
                                    if (fields.size() == fieldNames.size() && i < fields.size()) {
                                        fieldType = fields.get(i).type();
                                    } else {
                                        SymbolTable.Symbol sym = members.resolve(fv);
                                        if (sym != null) fieldType = sym.type();
                                        else {
                                            // Try by field name from record declaration
                                            for (AstNode d : sa.unit().declarations()) {
                                                if (d instanceof RecordDeclarationNode rec && rec.name().equals(simple)) {
                                                    if (i < rec.components().size()) {
                                                        fieldType = MemberResolver.resolveType(sa, rec.components().get(i).type(), scope);
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                                caseScope.define(new SymbolTable.LocalVariableSymbol(fv, fieldType, 0));
                            }
                        }
                        analyzeBody(sa, sc.body(), caseScope, returnType);
                    } else {
                        SemExpressionTyper.inferType(sa, sc.value(), scope);
                        SymbolTable caseScope = switchScope.enterScope();
                        analyzeBody(sa, sc.body(), caseScope, returnType);
                    }
                }
                if (!ss.defaultBody().isEmpty()) {
                    SymbolTable defaultScope = switchScope.enterScope();
                    analyzeBody(sa, ss.defaultBody(), defaultScope, returnType);
                }
            }
            case ExpressionStmt es -> {
                if (es.expression() != null) {
                    // `a = b` como STATEMENT é legítimo: check de assignability
                    // (SEM012) sem o SEM027 (que é só para uso como VALOR —
                    // bug 12). Mesmo helper usado pelo update do for.
                    if (es.expression() instanceof AssignmentExpr ae) {
                        sa.putExpressionType(es.expression(),
                                SemAssignmentAnalyzer.analyzeAssignmentStatement(sa, ae, scope));
                    } else {
                        Type exprType = SemExpressionTyper.inferType(sa, es.expression(), scope);
                        sa.putExpressionType(es.expression(), exprType);
                    }
                }
            }
            case ThrowStmt ts -> {
                if (ts.expression() != null) {
                    Type t = SemExpressionTyper.inferType(sa, ts.expression(), scope);
                    // bug 1: `throw <não-String>` gerava bytecode inválido no
                    // JVM (wrap em RuntimeException assumindo String). Exceções
                    // são Strings em Kof — rejeita com diagnóstico limpo.
                    if (sa.diagnostics() != null && t != null && !Type.isUnknown(t)
                            && !BuiltinTypes.isString(t)) {
                        sa.diagnostics().error(ts,
                                "throw requires a String (exceptions are Strings in Kof),"
                                        + " got " + Type.canonicalPrimitiveName(
                                        t instanceof Type.ClassType ct ? ct.name() : t.toString()),
                                "SEM026");
                    }
                }
            }
            case SpawnStmt ss -> {
                if (ss.expression() != null) SemExpressionTyper.inferType(sa, ss.expression(), scope);
            }
            case AssertStmt asrt -> {
                if (asrt.condition() != null) SemExpressionTyper.inferType(sa, asrt.condition(), scope);
            }
            default -> {}
        }
    }

    /**
     * SG-005: coleta os narrowings de nullability de uma condição de if.
     * `x != null` → THEN; `x == null` → ELSE; conjunção (&&) une os dois
     * lados no mesmo ramo THEN (o ramo só roda se TODOS os conjuntos valerem).
     * Disjunção (||) NÃO narrow (o ramo roda se UM valer) — recursão para
     * sem coletar. Narrowa locais cujo símbolo é NullableType e, desde
     * D-NARROW-WHILE (#159), também CAMPOS de receiver (`b.data != null`)
     * via um símbolo sintético (Narrowing.fieldNarrow) consultado por path.
     */
    private static void collectNarrowing(SemanticAnalyzer sa, ExpressionNode cond,
            SymbolTable scope,
            java.util.List<SymbolTable.LocalVariableSymbol> thenNarrow,
            java.util.List<SymbolTable.LocalVariableSymbol> elseNarrow,
            boolean underOr) {
        if (!(cond instanceof BinaryExpr be)) return;
        String op = be.operator();
        if ("&&".equals(op) && !underOr) {
            collectNarrowing(sa, be.left(), scope, thenNarrow, elseNarrow, false);
            collectNarrowing(sa, be.right(), scope, thenNarrow, elseNarrow, false);
            return;
        }
        if ("||".equals(op)) {
            // disjunção não narrow nenhum ramo — mas não desce (nada a coletar)
            return;
        }
        boolean isNullTest = be.right() instanceof LiteralExpr rl && rl.kind() == ConcreteLiteralKind.NULL;
        if (!isNullTest) return;
        SymbolTable.LocalVariableSymbol narrowed;
        if (be.left() instanceof IdentifierExpr id) {
            SymbolTable.Symbol sym = scope.resolve(id.name());
            if (!(sym != null && sym.type() instanceof Type.NullableType nt)) return;
            narrowed = new SymbolTable.LocalVariableSymbol(id.name(), nt.inner(), 0);
        } else if (be.left() instanceof FieldAccessExpr) {
            // D-NARROW-WHILE (#159): campo de receiver (`if (b.data != null)`)
            // narrowa o CAMPO no ramo — o tipo efetivo vem da inferência da
            // própria expressão (o receiver do campo pode ser Nullable também).
            String path = Narrowing.pathOf(be.left());
            Type fieldType = SemExpressionTyper.inferType(sa, be.left(), scope);
            if (!(fieldType instanceof Type.NullableType nt) || path == null) return;
            narrowed = Narrowing.fieldNarrow(path, nt.inner());
        } else {
            return;
        }
        switch (op) {
            case "!=" -> thenNarrow.add(narrowed);
            case "==" -> elseNarrow.add(narrowed);
            default -> {}
        }
    }

    /**
     * bug 145: `for-in` só itera `List<T>` ou array. Tipos conhecidamente NÃO
     * iteráveis (String, primitivos, Map/Set, record/classe) são rejeitados em
     * compile-time (SEM058) em vez de virar bytecode inválido/lixo cross-target.
     * `Unknown`/`TypeVariable`/`Nullable` de coleção NÃO são flagados — podem
     * ser List/array em runtime (SG-008) ou genérico.
     */
    static boolean isNonIterableForIn(Type t) {
        if (t instanceof Type.NullableType nt) t = nt.inner();
        if (t instanceof Type.ArrayType) return false;
        if (t instanceof Type.UnknownType) return false;
        if (t instanceof Type.TypeVariable) return false;
        if (BuiltinTypes.isList(t)) return false;
        return true;
    }
}
