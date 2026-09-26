package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipos de expressões, extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Sem estado próprio — recebe o analyzer (estado
 * compartilhado) por parâmetro.
 */
public final class SemExpressionTyper {

    private SemExpressionTyper() {}

    static Type inferType(SemanticAnalyzer sa, ExpressionNode expr, SymbolTable scope) {
        Type cached = sa.expressionTypes().get(expr);
        if (cached != null && !Type.isUnknown(cached)) return cached;
        DiagnosticCollector diag = sa.diagnostics();
        SourcePosition prev = diag == null ? null : diag.fallbackPosition();
        if (diag != null && expr != null && expr.position() != null) diag.setFallbackPosition(expr.position());
        try {
            Type result = inferTypeInternal(sa, expr, scope);
            sa.putExpressionType(expr, result);
            return result;
        } finally {
            if (diag != null) diag.setFallbackPosition(prev);
        }
    }

    static boolean isLocalName(SymbolTable scope, String name) {
        if (scope == null) return false;
        return scope.resolve(name) != null;
    }

    private static Type inferTypeInternal(SemanticAnalyzer sa, ExpressionNode expr, SymbolTable scope) {
        return switch (expr) {
            case PatternExpr pe -> {
                Type t = MemberResolver.resolveType(sa, pe.typeName(), scope);
                yield t != null ? t : Type.UnknownType.UNKNOWN;
            }
            case QueryDslExpr q -> {
                // Query DSL tipada: Entity.query(db) { ... } -> List<Entity>.
                // where/orderBy referenciam COLUNAS do schema (não variáveis
                // em escopo) — validadas no lowering; não inferir aqui (senão
                // SEM011 "undefined variable" nas colunas). dbArg e limit são
                // expressões reais.
                inferType(sa, q.dbArg(), scope);
                if (q.limit() != null) inferType(sa, q.limit(), scope);
                Type elem = MemberResolver.resolveType(sa, q.entityType(), scope);
                yield new Type.ClassType("kof", "List",
                        List.of(elem != null ? elem : Type.UnknownType.UNKNOWN));
            }
            case LiteralExpr lit -> TypeChecker.inferLiteralType(lit);
            case IdentifierExpr ie -> {
                SymbolTable.Symbol sym = scope.resolve(ie.name());
                if (sym != null) {
                    // #345 (R6): campo de instância referido NU dentro de
                    // método `static` = `this` implícito que não existe — o
                    // emit gerava aload_0 → VerifyError "Bad local variable
                    // type" no load (medido no tip; o `check` passava limpo).
                    // Idiom: referência pela instância, ou campo `static`.
                    if (sym instanceof SymbolTable.FieldSymbol fsm
                            && (fsm.accessFlags() & AccessFlags.STATIC) == 0
                            && sa.isCurrentMethodStatic()
                            && sa.diagnostics() != null) {
                        SourcePosition pos = ie.position();
                        sa.diagnostics().error(pos != null ? pos.file() : "",
                                pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                                "static method cannot reference instance field '" + ie.name()
                                        + "' (no implicit 'this' in a static context; use an instance,"
                                        + " or declare the field 'static')",
                                "SEM075");
                    }
                    yield sym.type();
                }
                if ("args".equals(ie.name()) && "main".equals(sa.currentFunctionName())) {
                    yield new Type.ArrayType(BuiltinTypes.STRING);
                }
                // constante de enum não-qualificada (rótulos de case, etc.):
                // Red → Color quando algum enum declara Red
                if (sa.unit() != null && !sa.allClasses().containsKey(ie.name())) {
                    for (AstNode d0 : sa.unit().declarations()) {
                        if (d0 instanceof EnumDeclarationNode en0
                                && en0.constants().contains(ie.name())) {
                            yield new Type.ClassType(sa.packageOf(en0), en0.name(), List.of()); // #445
                        }
                    }
                }
                if ("super".equals(ie.name()) && sa.currentClassName() != null
                        && !sa.currentClassName().isEmpty()) {
                    SymbolTable.ClassSymbol cur = sa.getClass(sa.currentClassName());
                    Type sup = cur != null
                            ? HierarchyResolver.superTypeOf(sa, cur.internalName()) : null;
                    if (sup != null) {
                        sa.putExpressionType(ie, sup);
                        yield sup;
                    }
                }
                if (sa.currentClassName() != null && !sa.currentClassName().isEmpty()) {
                    SymbolTable.Symbol fieldSym = MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), ie.name());
                    if (fieldSym != null) {
                        sa.putExpressionType(ie, fieldSym.type());
                        yield fieldSym.type();
                    }
                }
                // §400 (voto D-CLOSEALL-BATCH, mantenedora 21/09, opção A):
                // FUNÇÃO top-level nomeada usada como VALOR em posição de
                // argumento (`job("e", probe)` com `Bool probe()`) — não é um
                // variável indefinida: diagnostica a regra real e aponta o
                // idiom lambda que já existe (`() -> probe()`).
                boolean namedTopLevelFunc = false;
                if (sa.unit() != null) {
                    for (AstNode f0 : sa.unit().declarations()) {
                        if (f0 instanceof FunctionDeclarationNode fd0
                                && ie.name().equals(fd0.name())) { namedTopLevelFunc = true; break; }
                    }
                }
                if (namedTopLevelFunc) {
                    SourcePosition pn = ie.position();
                    sa.diagnostics().error(pn != null ? pn.file() : "",
                            pn != null ? pn.line() : 0, pn != null ? pn.column() : 0,
                            0,
                            ie.name() + " is a top-level function, not a value in argument position — "
                                    + "pass the call wrapped in a lambda: () -> " + ie.name() + "()",
                            "SEM011");
                    yield Type.UnknownType.UNKNOWN;
                }
                if (SemUndefinedVarGuard.reportsUndefined(sa, ie.name())) {
                    sa.diagnostics().error(ie,
                            "Undefined variable or type: '" + ie.name() + "'", "SEM011");
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case AssignmentExpr ae -> {
                // bug 12: assignment como VALOR de expressão (`var c = a = b`)
                // gerava bytecode inválido no JVM. Kof não tem assignment como
                // expressão — rejeita com diagnóstico limpo (statements passam
                // pelo ExpressionStmt, que não chega aqui).
                if (sa.diagnostics() != null) {
                    sa.diagnostics().error(ae,
                            "assignment is a statement, not an expression (use '=' on its own line)",
                            "SEM027");
                }
                Type valueType = inferType(sa, ae.value(), scope);
                Type targetType = Type.UnknownType.UNKNOWN;
                if (ae.target() instanceof IdentifierExpr ie) {
                    SymbolTable.Symbol sym = scope.resolve(ie.name());
                    // SC1: atribuição a variável nunca declarada
                    if (sym == null && sa.diagnostics() != null) {
                        boolean hasField = false;
                        if (sa.currentClassName() != null) {
                            hasField = MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), ie.name()) != null;
                        }
                        if (!hasField
                                && !"json".equals(ie.name()) && !"process".equals(ie.name()) && !"shell".equals(ie.name())
                                && !"ssh".equals(ie.name())
                                && !KofWeb.isWebNamespace(ie.name())
                                && !KofConfig.isConfigNamespace(ie.name())
                                && !KofCache.isCacheNamespace(ie.name())
                                && !KofGpu.isGpuNamespace(ie.name())
                                && !KofDb.isDbNamespace(ie.name())
                                && !KofOrm.isOrmNamespace(ie.name())
                                && !KofLog.isLogNamespace(ie.name())
                                && !KofSecurity.isSecurityNamespace(ie.name())
                        && !KofValidation.isValidationNamespace(ie.name())
                        && !KofStd.isStdNamespace(ie.name())
                                && !KofObservability.isObservabilityNamespace(ie.name())
                                && !KofHttp.isHttpNamespace(ie.name())
                                && !KofMq.isMqNamespace(ie.name())
                                && !KofTime.isTimeNamespace(ie.name())
                                && !KofScheduler.isSchedulerNamespace(ie.name())
                                && !sa.allClasses().containsKey(ie.name())) {
                            sa.diagnostics().error(ie,
                                    "undefined variable: '" + ie.name() + "'", "SEM020");
                        }
                    }
                    if (sym != null) {
                        targetType = Narrowing.assignTarget(scope, ie.name(), sym).type();
                        boolean strConcat = "+=".equals(ae.operator()) && BuiltinTypes.isString(targetType);
                        if (sa.diagnostics() != null && !Type.isUnknown(targetType) && !Type.isUnknown(valueType)
                                && !strConcat
                                && !TypeChecker.isAssignable(sa, valueType, targetType)) {
                            sa.diagnostics().error(ae,
                                    "Type mismatch: cannot assign " + valueType + " to " + targetType, "SEM012");
                        }
                    }
                } else if (ae.target() instanceof FieldAccessExpr fa) {
                    targetType = inferType(sa, fa, scope);
                }
                yield targetType;
            }
            case BinaryExpr bin -> {
                // SG-005: narrowing intra-expressão de `&&` — em
                // `s != null && s.length > 0`, o lado direito vê `s` narrowed
                // (o lado só é avaliado se o esquerdo passou; short-circuit).
                if ("&&".equals(bin.operator())) {
                    Type leftT = inferType(sa, bin.left(), scope);
                    SymbolTable rightScope = SemNarrowing.narrowedScope(bin.left(), scope);
                    Type rightT = inferType(sa, bin.right(), rightScope);
                    yield SemBinaryResultTyper.inferBinaryResultType(sa.diagnostics(), "&&", leftT, rightT);
                }
                // Left-associative chains (huge string concatenations in
                // generated UIs, editors) are iterated instead of recursed:
                // deep chains would overflow the compiler's own stack.
                java.util.List<BinaryExpr> chain = new ArrayList<>();
                ExpressionNode cursor = bin;
                while (cursor instanceof BinaryExpr be) {
                    chain.add(be);
                    cursor = be.left();
                }
                Type accType = inferType(sa, cursor, scope);
                for (int ci = chain.size() - 1; ci >= 0; ci--) {
                    BinaryExpr be = chain.get(ci);
                    Type rightType = inferType(sa, be.right(), scope);
                    // "x as Char/Int/…" — o alvo é um identificador de tipo
                    // (não resolve como valor): scope.resolve dá null →
                    // rightType=Unknown (mesmo repair do ExpressionTyper:89,
                    // que só roda no lowering; o cache daqui é o que o
                    // MethodCallTyper lê para `mapOf(k, v as T)`). Sem isto o
                    // V do Map pinava Unknown e o unbox/print do char-em-
                    // coleção (§104b-ii) perdia o tipo.
                    if ("as".equals(be.operator()) && rightType instanceof Type.UnknownType
                            && be.right() instanceof dev.kof.compiler.IdentifierExpr rie) {
                        Type q = CompilerTypes.toType(rie.name(), sa.unit());
                        if (!(q instanceof Type.UnknownType)) rightType = q;
                    }
                    accType = SemBinaryResultTyper.inferBinaryResultType(sa.diagnostics(), be.operator(), accType, rightType);
                }
                yield accType;
            }
            case UnaryExpr ue -> {
                Type operandType = inferType(sa, ue.operand(), scope);
                // #469: `record.x++`/`--` passava no `check` e só falhava em
                // runtime (IllegalAccessError no JVM / TypeError no JS — o
                // campo é privado/final). A escrita direta (`x = v`) e o
                // composto (`x += v`) já são SEM038 no StatementAnalyzer; o
                // incremento não passava por lá. Mesmo contrato (record
                // imutável), mesmo diagnóstico, agora em compile-time nos 4
                // alvos.
                if (("++".equals(ue.operator()) || "--".equals(ue.operator()))
                        && ue.operand() instanceof FieldAccessExpr incFa
                        && sa.diagnostics() != null) {
                    boolean incOnThis = incFa.receiver() instanceof IdentifierExpr rid2
                            && "this".equals(rid2.name());
                    Type incRecv = incOnThis
                            ? (sa.currentClassName() != null
                                    ? new Type.ClassType("", sa.currentClassName(), List.of()) : null)
                            : inferType(sa, incFa.receiver(), scope);
                    if (incRecv != null && CompilerTypes.isRecordType(incRecv, sa.unit(), sa)
                            && !(incOnThis && sa.inConstructor)) {
                        sa.diagnostics().error(incFa,
                                "cannot assign to '" + incFa.fieldName()
                                        + "': record is immutable",
                                "SEM038");
                    }
                }
                // D-TROOL (19/09): `!Troolean` = tres estados (Kleene `!U = U`)
                // — o tipo semantico tem de casar com a caixa do lowering.
                if ("!".equals(ue.operator())) yield CompilerComparisons.isNullableBool(operandType)
                        ? new Type.NullableType(Type.PrimitiveType.BOOL)
                        : Type.PrimitiveType.BOOL;
                yield operandType;
            }
            case MethodCallExpr mc -> SemMethodCallTyper.infer(sa, mc, scope);
            case NewExpr ne -> SemNewExprTyper.infer(sa, ne, scope);
            case FieldAccessExpr fa -> {
                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())) {
                    if (KofUi.paletteColor(fa.fieldName()) != null) yield KofUi.COLOR;
                    // §498: `Palette.bogus` caía no caminho genérico e o emit
                    // gerava `getfield "?".bogus` (NoClassDefFoundError: "?" no
                    // load, compilado limpo). Paleta é CONSTANTE — membro
                    // desconhecido é SEM079, como nos token namespaces.
                    if (sa.diagnostics() != null) {
                        sa.diagnostics().error(fa,
                                "'Palette." + fa.fieldName() + "' is not a palette color "
                                        + "(use a named color, e.g. Palette.red)",
                                "SEM079");
                    }
                    yield KofUi.COLOR;
                }
                if (fa.receiver() instanceof IdentifierExpr tid && KofUiTokens.isTokenNamespace(tid.name())) {
                    if (KofUiTokens.tokenValue(tid.name(), fa.fieldName()) == null && sa.diagnostics() != null) {
                        sa.diagnostics().error(fa,
                                KofUiTokens.unknownMemberMessage(tid.name(), fa.fieldName()), "SEM079");
                    }
                    yield Type.PrimitiveType.INT;
                }
                String en = MemberResolver.enumNameOfConstant(sa.unit(), fa);
                if (en != null) yield CompilerTypes.enumTypeOf(en, sa); // #445: pkg real via ClassSymbol
                Type recvType = inferType(sa, fa.receiver(), scope);
                Type nf = Narrowing.narrowedField(scope, Narrowing.pathOf(fa));
                if (nf != null) yield nf;
                // bug 99 (R6, nunca silencioso): `Int.MAX_VALUE`/`Long.foo` etc.
                // — acesso a campo num NOME DE TIPO PRIMITIVO. `Int` resolve p/
                // UNKNOWN (a isenção isBuiltinTypeName de SEM011 existe p/ posição
                // de TIPO, não p/ receiver de campo) e o guard SEM025 abaixo só
                // dispara em ClassType → o campo passava SEM diagnóstico e o
                // lowering emitia `getfield "?".field` (NoClassDefFoundError/SIGSEGV
                // nos 3 targets; `var x = Int.MAX_VALUE` ainda CRASHAVA o
                // compilador — ASM visitMaxs NegativeArraySizeException). Não há
                // constante estática de primitivo em Kof (idiom = literal/`as`).
                if (recvType instanceof Type.UnknownType
                        && fa.receiver() instanceof IdentifierExpr rid
                        && MemberResolver.isBuiltinTypeName(rid.name())
                        && sa.diagnostics() != null) {
                    sa.diagnostics().error(fa,
                            "'" + rid.name() + "' is a primitive type, it has no static field "
                                    + "'" + fa.fieldName() + "' (use the literal, "
                                    + "e.g. 2147483647 for Int; there is no Int.MAX_VALUE in Kof)",
                            "SEM050");
                    yield Type.UnknownType.UNKNOWN;
                }
                // §496: acesso a CAMPO em um NAMESPACE builtin (math.bogus, e
                // até `math.PI` — não há constante de namespace). O receiver
                // não é local nem tipo (vira UNKNOWN), então caía no caminho
                // genérico e o emit gerava `getfield "?".<nome>` contra uma
                // classe vazia → NoClassDefFoundError: "?" no load, compilado
                // limpo (escondido atrás da mensagem do launcher JavaFX). É a
                // face FIELD da família §495/#126/§490. Enum/paleta (`Color.Red`
                // via KofUi.isPalette), tokens (KofUiTokens) e constantes de
                // enum já retornaram acima, e o receiver tem de ser UNKNOWN
                // (um local chamado `math` tem tipo próprio) — sem falso positivo.
                if (recvType instanceof Type.UnknownType
                        && fa.receiver() instanceof IdentifierExpr nId
                        && SemUndefinedVarGuard.isBuiltinNamespace(nId.name())
                        && sa.diagnostics() != null) {
                    sa.diagnostics().error(fa,
                            "'" + nId.name() + "' is a builtin namespace; it has no field "
                                    + "'" + fa.fieldName() + "' (namespaces expose functions, "
                                    + "not properties — use " + nId.name() + ".someFunction(...))",
                            "SEM102");
                    yield Type.UnknownType.UNKNOWN;
                }
                // §498: acesso a CAMPO em um TIPO kof.ui (Color/Theme e demais
                // construtores UI). Eles expõem FUNÇÕES (`Color.rgba(...)`,
                // `Theme.light()`), não campos — `Color.bogus` compilava limpo e
                // o emit gerava `getfield "?".bogus`. Só dispara se o receiver
                // não virou tipo declarado (recvType UNKNOWN), preservando uma
                // classe do usuário homônima.
                if (recvType instanceof Type.UnknownType
                        && fa.receiver() instanceof IdentifierExpr uId
                        && KofUi.isConstructor(uId.name())
                        && sa.diagnostics() != null) {
                    sa.diagnostics().error(fa,
                            "'" + uId.name() + "' has no field '" + fa.fieldName()
                                    + "' (UI types expose functions, e.g. Color.rgba(...), "
                                    + "Theme.light())",
                            "SEM079");
                    yield Type.UnknownType.UNKNOWN;
                }
                // SG-005: deref de T? sem narrowing é erro (espelha SEM049 de
                // method call) — `s.length` em String? seria NPE em runtime.
                if (recvType instanceof Type.NullableType && sa.diagnostics() != null) {
                    SourcePosition faPos = fa.position();
                    sa.diagnostics().error(faPos != null ? faPos.file() : "",
                            faPos != null ? faPos.line() : 0, faPos != null ? faPos.column() : 0, 0,
                            "receiver is nullable (T?); narrow first: if (x != null) { x.field }",
                            "SEM049");
                }
                if (KofUi.isComponent(recvType) && "state".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (KofProcess.isResult(recvType) && KofProcess.isField(fa.fieldName())) {
                    yield KofProcess.fieldType(fa.fieldName());
                }
                if (recvType instanceof Type.ArrayType && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
                    yield BuiltinTypes.STRING;
                }
                // §491 (SEM102, R6): pseudo-tipo builtin com ramo próprio de typer
                // (Buffer/Secret/KeyHandle e File/Path/Directory) NÃO tem forma de
                // PROPRIEDADE — os acessores são métodos. Um campo desconhecido
                // caía no caminho genérico de ClassType (não é classe do unit nem
                // external) e o emit gerava `getfield <receiver>.<nome>` contra uma
                // classe INEXISTENTE no runtime (kof/Buffer, kof/Secret,
                // kof/io/File) — NoClassDefFoundError no load, compilado limpo
                // (escondido atrás da mensagem do launcher JavaFX). Mesma família
                // de #617/§490, agora a face FIELD. O acesso válido é o método
                // (`File("x").path()`, `.size()`, `buffer.alloc(n).bytes()`,
                // `secret.reveal()`).
                // §503: Channel<T>/Handle<T> (kof.concurrent) entram na mesma
                // família — nenhum dos dois tem PROPRIEDADE: `c.bogusField` /
                // `h.bogusField` compilavam limpos e o emit emitia
                // `getfield LinkedBlockingQueue.bogusField` / `CompletableFuture...`
                // → NoSuchFieldError no runtime (Handle: o idioma é `await h`).
                if ((KofIo.isIoType(recvType) || KofBuffer.isBufferType(recvType)
                        || KofSecurity.isSecretType(recvType) || KofSecurity.isKeyHandleType(recvType)
                        || BuiltinTypes.isChannel(recvType) || TypeChecker.isConcurrentHandle(recvType))
                        && sa.diagnostics() != null) {
                    boolean handle = TypeChecker.isConcurrentHandle(recvType);
                    String builtinName = KofIo.isDirectory(recvType) ? "Directory"
                            : KofIo.isPath(recvType) ? "Path"
                            : KofIo.isFile(recvType) ? "File"
                            : KofBuffer.isBufferType(recvType) ? "Buffer"
                            : (KofSecurity.isSecretType(recvType) ? "Secret"
                            : BuiltinTypes.isChannel(recvType) ? "Channel"
                            : (KofSecurity.isKeyHandleType(recvType) ? "KeyHandle" : "Handle"));
                    sa.diagnostics().error(fa,
                            "'" + builtinName + "' has no field '" + fa.fieldName() + "'"
                                    + (handle ? "; use `await h` to get the value"
                                            : " (this builtin exposes methods, not properties)"),
                            "SEM102");
                    yield Type.UnknownType.UNKNOWN;
                }
                // #375/§355 (rio da erasure): receiver é type-variable COM bound
                // (`item.name` com `item: T: Animal`) — o membro resolve no
                // BOUND, como javac após a erasure. Sem isto o tipo caía em
                // UNKNOWN e o emit saía owner "?" / descritor Object →
                // NoClassDefFoundError: "?".
                if (recvType instanceof Type.TypeVariable tv && tv.bound() != null) {
                    recvType = tv.bound();
                }
                // §500 slice B: campo ESTÁTICO externo pelo NOME da classe
                // (`Integer.MAX_VALUE`/`TimeUnit.SECONDS`; SEM025 honesto,
                // nunca `getfield "?"` morto) — a face vive em
                // StaticClassReceiver (gate ≤500, R6).
                Type s500 = StaticClassReceiver.semInfer(sa, fa, recvType);
                if (s500 != null) yield s500;
                if (recvType instanceof Type.ClassType ct) {
                    SymbolTable.Symbol field = MemberResolver.resolveFieldInHierarchy(sa, ct.name(), fa.fieldName());
                    if (field != null) {
                        // #331/#327 (espelha SEM046 dos metodos): acesso a
                        // campo private/protected de fora da declarante
                        // compila e o load estourava IllegalAccessError em
                        // silencio (R6/Q7). so com `this.x`/x nu (owner ==
                        // caller) e dentro da declarante/subclasse passa.
                        if (field instanceof SymbolTable.FieldSymbol fs) {
                            MemberCallTyper.checkFieldAccess(sa, fs);
                        }
                        yield CompilerTypes.substituteTypeVariableIn(field.type(), recvType, sa.unit());
                    }
                    if (sa.isExternal(ct)) {
                        String desc = sa.externalTypes().resolveFieldType(ct.internalName(), fa.fieldName());
                        if (desc != null) {
                            yield ExternalClasspath.typeFromDescriptor(desc);
                        }
                    }
                    // P0 #3: campo inexistente em classe conhecida nao pode
                    // mascarar com UNKNOWN (mesma regra SEM025 do metodo) —
                    // erro primeiro, depois UNKNOWN p/ error recovery.
                    // Excecoes: constante de enum (Color.Red e FieldAccess)
                    // e metodos de Object (nao sao campos).
                    boolean isKnownReceiver = sa.allClasses().containsKey(ct.name()) || sa.isExternal(ct);
                    boolean isEnumConstant = MemberResolver.enumConstantOfExpr(sa.unit(), fa) != null;
                    if (sa.diagnostics() != null && isKnownReceiver && !isEnumConstant && !MemberResolver.isObjectMethod(fa.fieldName(), 0)) {
                        sa.diagnostics().error(fa,
                                "Cannot resolve field '" + fa.fieldName()
                                        + "' on type '" + ct.name() + "'",
                                "SEM025");
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case NewArrayExpr na -> {
                Type elemType = Type.of(na.elementType());
                inferType(sa, na.size(), scope);
                for (ExpressionNode dim : na.moreDims()) inferType(sa, dim, scope);
                for (int i = 0; i < na.moreDims().size(); i++) {
                    elemType = new Type.ArrayType(elemType);
                }
                yield new Type.ArrayType(elemType);
            }
            case ArrayAccessExpr aa -> {
                Type recvType = inferType(sa, aa.receiver(), scope);
                inferType(sa, aa.index(), scope);
                if (recvType instanceof Type.ArrayType at) {
                    yield at.componentType();
                }
                // paridade absoluta (JVM=JS=X86=ARM=RISC, regra 6/R6) — mesmo
                // padrão do §96/§98/§100: `x[i]` SÓ existe para ARRAY no corpus
                // (`learn/04:84`, `new Int[n]`). Em String/Map/Set o
                // subscript era ACEITO e quebrava de um jeito em cada target
                // ("abc"[0]: JVM VerifyError, Native/Script vazios).
                // #149/#152: List[i] é suportado (roteado para kof_list_get).
                if (sa.diagnostics() != null && isKofCollectionType(recvType) && !BuiltinTypes.isList(recvType)) {
                    var pos = aa.position();
                    sa.diagnostics().error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            "`[]` only indexes arrays in Kof; for this collection use "
                                    + collectionIndexHint(recvType),
                            "SEM054");
                }
                if (BuiltinTypes.isList(recvType)) {
                    if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case LambdaExpr le -> {
                SymbolTable lambdaScope = scope.enterScope();
                List<Type> paramTypes = new ArrayList<>();
                int idx = 0;
                for (FormalParameterNode p : le.parameters()) {
                    Type paramType = MemberResolver.resolveType(sa, p.type(), scope);
                    paramTypes.add(paramType);
                    lambdaScope.define(new SymbolTable.ParameterSymbol(p.name(), paramType, idx));
                    idx++;
                }
                // #333: o corpo de LAMBDA inferiu o tipo pelo contexto (o `-> expr`
                // vira ReturnStmt sintetico no LambdaParser:142) — nunca herda a
                // rejeicao de valor da funcao envolvente.
                boolean prevEv = sa.currentExplicitVoid;
                sa.currentExplicitVoid = false;
                StatementAnalyzer.analyzeBody(sa, le.body(), lambdaScope, Type.UnknownType.UNKNOWN);
                sa.currentExplicitVoid = prevEv;
                Type returnType = Type.UnknownType.UNKNOWN;
                boolean hasReturn = false;
                for (StatementNode s : le.body()) {
                    if (s instanceof ReturnStmt rs) {
                        hasReturn = true;
                        if (rs.value() != null) {
                            returnType = inferType(sa, rs.value(), lambdaScope);
                        } else {
                            returnType = Type.PrimitiveType.VOID;
                        }
                        break;
                    }
                    if (s instanceof BlockStmt b) {
                        for (StatementNode inner : b.statements()) {
                            if (inner instanceof ReturnStmt rs2) {
                                hasReturn = true;
                                if (rs2.value() != null) {
                                    returnType = inferType(sa, rs2.value(), lambdaScope);
                                } else {
                                    returnType = Type.PrimitiveType.VOID;
                                }
                                break;
                            }
                        }
                    }
                }
                if (!hasReturn) {
                    returnType = Type.PrimitiveType.VOID;
                }
                yield new Type.FunctionType(paramTypes, returnType);
            }
            case IfExpr ie -> {
                Type thenType = inferType(sa, ie.thenExpr(), scope);
                Type elseType = ie.elseExpr() != null ? inferType(sa, ie.elseExpr(), scope) : Type.UnknownType.UNKNOWN;
                if (thenType.equals(elseType)) yield thenType;
                yield HierarchyResolver.commonSupertype(sa, thenType, elseType);
            }
            case SwitchExpr se -> {
                Type subjectType = inferType(sa, se.expression(), scope);
                Type result = Type.UnknownType.UNKNOWN;
                int armCount = 0;
                for (SwitchExprCase sc : se.cases()) {
                    SymbolTable caseScope = scope.enterScope();
                    if (sc.value() instanceof PatternExpr pe) {
                        SemNarrowing.bindPatternVars(sa, pe, caseScope);
                        // SG-014: guarda analisada com a var do pattern bound
                        if (pe.guard() != null) {
                            inferType(sa, pe.guard(), caseScope);
                        }
                    } else {
                        inferType(sa, sc.value(), scope);
                    }
                    Type t = inferType(sa, sc.body(), caseScope);
                    if (armCount == 0) result = t;
                    armCount++;
                }
                if (se.defaultValue() != null) {
                    SymbolTable defaultScope = scope.enterScope();
                    inferType(sa, se.defaultValue(), defaultScope);
                } else {
                    MemberResolver.checkSwitchExprExhaustiveness(sa, se, subjectType);
                }
                yield result;
            }
            default -> Type.UnknownType.UNKNOWN;
        };
    }

    private static boolean isKofCollectionType(Type t) {
        if (t instanceof Type.NullableType nt) t = nt.inner();
        if (t instanceof Type.ArrayType) return false;   // array: [] é válido
        if (t instanceof Type.UnknownType) return false; // pode ser array em runtime (SG-008)
        return BuiltinTypes.isString(t) || BuiltinTypes.isList(t)
                || BuiltinTypes.isMap(t) || BuiltinTypes.isSet(t);
    }

    private static String collectionIndexHint(Type t) {
        if (t instanceof Type.NullableType nt) t = nt.inner();
        if (BuiltinTypes.isString(t)) return "charAt(i) / substring(i)";
        if (BuiltinTypes.isMap(t)) return "get(k)";
        return "get(i)";
    }
}

