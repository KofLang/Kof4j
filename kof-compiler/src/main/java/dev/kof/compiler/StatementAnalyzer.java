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
    }

    /**
     * Assignment como STATEMENT (`a = b`, `i = i + 1` no update do for):
     * infere alvo/valor e valida assignability (SEM012) SEM emitir o SEM027
     * (que é reservado para assignment usado como VALOR — bug 12).
     */
    static Type analyzeAssignmentStatement(SemanticAnalyzer sa, AssignmentExpr ae, SymbolTable scope) {
        Type valueType = SemExpressionTyper.inferType(sa, ae.value(), scope);
        // SG-005/008 (SEM048): `x = null` é erro — null nunca é atribuível
        if (CompilerComparisons.isNullLiteral(ae.value()) && sa.diagnostics() != null) {
            sa.diagnostics().error("", 0, 0, 0,
                    "null cannot be assigned: null safety works by narrowing"
                            + " (if (x != null)), never by direct null literals",
                    "SEM048");
        }
        Type targetType = Type.UnknownType.UNKNOWN;
        if (ae.target() instanceof IdentifierExpr ie) {
            SymbolTable.Symbol sym = scope.resolve(ie.name());
            if (sym != null) {
                // D-NARROW-WHILE (#159): alvo NARROWADO — a assignabilidade e
                // o `val` vêm da DECLARAÇÃO; o narrowing é de fluxo, não muda
                // o tipo do slot (senão `s = nextVal(i)` num corpo narrowado
                // dava SEM012 falso-positivo).
                SymbolTable.Symbol effective = Narrowing.assignTarget(scope, ie.name(), sym);
                targetType = effective.type();
                // bug 62: `val` é imutável — escrever em val é erro de
                // mutabilidade (SEM037), alinhado à semântica congelada.
                if (effective instanceof SymbolTable.LocalVariableSymbol lv && lv.isVal()
                        && sa.diagnostics() != null) {
                    sa.diagnostics().error("", 0, 0, 0,
                            "cannot assign to immutable 'val' variable '" + ie.name() + "'",
                            "SEM037");
                }
                if (sa.diagnostics() != null && !Type.isUnknown(targetType)
                        && !Type.isUnknown(valueType)
                        && !TypeChecker.isAssignable(sa, valueType, targetType)) {
                    sa.diagnostics().error("", 0, 0, 0,
                            "Type mismatch: cannot assign " + valueType + " to " + targetType,
                            "SEM012");
                }
            } else {
                targetType = SemExpressionTyper.inferType(sa, ae.target(), scope);
            }
        } else if (ae.target() instanceof FieldAccessExpr fa) {
            // #42 (DD-02): escrita em componente de record é SEM038 — o corpus
            // (learn/07) define record como imutável; hoje só o JVM/JS falham
            // em runtime (IllegalAccessError/TypeError) e o interpretador
            // muta em silêncio. O guard no analyzer alinha os 4 caminhos.
            Type recvType = SemExpressionTyper.inferType(sa, fa.receiver(), scope);
            targetType = recvType;
            // §246/#269: escrita em campo por receiver NULLABLE tem o mesmo
            // contrato do READ (SEM049 em SemExpressionTyper): o acesso direto
            // seria NPE em runtime. Sem este guard o analyzer aceitava em
            // silêncio e o lowering emitia `putfield` com descritor errado
            // (`Field "?".num:Ljava/lang/Object;` → VerifyError no load).
            if (recvType instanceof Type.NullableType && sa.diagnostics() != null) {
                SourcePosition faPos = fa.position();
                sa.diagnostics().error(faPos != null ? faPos.file() : "",
                        faPos != null ? faPos.line() : 0, faPos != null ? faPos.column() : 0, 0,
                        "receiver is nullable (T?); narrow first: if (x != null) { x.field = v }",
                        "SEM049");
            }
            // DD-02/#42: escrita em componente de record é SEM038. Para o
            // receiver explícito, o tipo resolve normalmente; para `this`,
            // inferType não tipa o identificador — usa-se currentClassName.
            // `this.x =` só é legal no construtor (init do campo final,
            // JVMS 4.4); em método de record → sintoma (c) do #42.
            boolean onThis = fa.receiver() instanceof IdentifierExpr rid && "this".equals(rid.name());
            boolean recvIsRecord = onThis
                    ? (sa.currentClassName() != null && CompilerTypes.isRecordType(
                            new Type.ClassType("", sa.currentClassName(), List.of()), sa.unit(), sa))
                    : (recvType != null && CompilerTypes.isRecordType(recvType, sa.unit(), sa));
            if (sa.diagnostics() != null && recvIsRecord && !(onThis && sa.inConstructor)) {
                sa.diagnostics().error("", 0, 0, 0,
                        "cannot assign to '" + fa.fieldName() + "': record is immutable",
                        "SEM038");
            }
            // #331/#327 — escrita em campo: MESMO contrato do READ (que passa
            // por SemExpressionTyper), mas aqui o inferType e so do RECEIVER,
            // entao os cheques de acesso/`final` precisam ser feitos a mao.
            // Owner: receiver ClassType explicito, ou currentClassName p/
            // `this.x`. final so e escrito legalmente no <init> da declarante
            // (o construtor ja cai no caminho legal de checkFinalFieldWrite).
            String ownerName = onThis ? sa.currentClassName()
                    : (recvType instanceof Type.ClassType rct ? rct.name() : null);
            if (ownerName != null) {
                SymbolTable.Symbol wf = MemberResolver.resolveFieldInHierarchy(sa, ownerName, fa.fieldName());
                if (wf instanceof SymbolTable.FieldSymbol wfs) {
                    MemberCallTyper.checkFieldAccess(sa, wfs);
                    MemberCallTyper.checkFinalFieldWrite(sa, wfs);
                }
            }
        } else if (ae.target() instanceof ArrayAccessExpr aa) {
            // #149/#152: `l[i]` READ on a List is supported; the WRITE face
            // (`l[i] = v`) was never lowered — it emitted a raw array store
            // (JVM VerifyError "not assignable to Object" at aastore, Native
            // SIGSEGV exit 139, JS silent). R6: reject at compile-time pointing
            // to `.set(i, v)` instead of emitting broken bytecode. Only List
            // needs the check here: String/Map/Set writes already hit the
            // read guard in SemExpressionTyper (avoiding a duplicate SEM054).
            Type recvType = SemExpressionTyper.inferType(sa, aa.receiver(), scope);
            Type unwrapped = recvType instanceof Type.NullableType nt ? nt.inner() : recvType;
            if (sa.diagnostics() != null && BuiltinTypes.isList(unwrapped)) {
                SourcePosition pos = aa.position();
                sa.diagnostics().error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "`[]` assignment only works on arrays in Kof; for a List use l.set(i, v)",
                        "SEM054");
            }
            targetType = SemExpressionTyper.inferType(sa, ae.target(), scope);
        } else if (ae.target() != null) {
            targetType = SemExpressionTyper.inferType(sa, ae.target(), scope);
        }
        return targetType;
    }

    static void analyzeStatement(SemanticAnalyzer sa, StatementNode stmt, SymbolTable scope, Type returnType) {
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
                    sa.diagnostics().error("", 0, 0, 0,
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
                        sa.diagnostics().error("", 0, 0, 0,
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
                        sa.diagnostics().error("", 0, 0, 0,
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
                    sa.diagnostics().error("", 0, 0, 0,
                            "variable '" + vds.name() + "' is already defined in this scope",
                            "SEM024");
                }
                if (vds.initializer() != null) SemExpressionTyper.inferType(sa, vds.initializer(), scope);
                // SC2: tipo explícito ≠ tipo do inicializador
                if (sa.diagnostics() != null && vds.initializer() != null
                        && !varType.equals(Type.UnknownType.UNKNOWN)) {
                    Type initType = SemExpressionTyper.inferType(sa, vds.initializer(), scope);
                    if (!initType.equals(Type.UnknownType.UNKNOWN)
                            && !TypeChecker.isAssignable(sa, initType, varType)
                            && !(initType instanceof Type.FunctionType)
                            && !(varType instanceof Type.FunctionType)) {
                        sa.diagnostics().error("", 0, 0, 0,
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
                    // #529: lambda returned from a function whose return type is
                    // FunctionType — use context-aware inference so untyped params
                    // are resolved from the declared return type instead of Object.
                    Type valueType;
                    if (ret.value() instanceof LambdaExpr le
                            && returnType instanceof Type.FunctionType ft) {
                        valueType = SemExpressionTyper.inferLambdaWithContext(sa, le, scope, ft);
                    } else {
                        valueType = SemExpressionTyper.inferType(sa, ret.value(), scope);
                    }
                    sa.putExpressionType(ret.value(), valueType);
                    if (sa.currentExplicitVoid && sa.diagnostics() != null) {
                        sa.diagnostics().error(ret.position() != null ? ret.position().file() : "", 0, 0, 0,
                                "void function cannot return a value - drop the value (bare `return` exits) or declare a return type",
                                "SEM093");
                    }
                    if (sa.diagnostics() != null && !Type.isUnknown(returnType) && !Type.isVoid(returnType)
                            && !Type.isUnknown(valueType) && !TypeChecker.isAssignable(sa, valueType, returnType)) {
                        sa.diagnostics().error("", 0, 0, 0,
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
                        analyzeAssignmentStatement(sa, ae, forScope);
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
                                analyzeAssignmentStatement(sa, ae, scope));
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
                        sa.diagnostics().error("", 0, 0, 0,
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
