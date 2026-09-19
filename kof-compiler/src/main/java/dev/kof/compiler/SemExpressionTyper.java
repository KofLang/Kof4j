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
        Type result = inferTypeInternal(sa, expr, scope);
        sa.putExpressionType(expr, result);
        return result;
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
                if (sa.currentClassName() != null && !sa.currentClassName().isEmpty()) {
                    SymbolTable.Symbol fieldSym = MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), ie.name());
                    if (fieldSym != null) {
                        sa.putExpressionType(ie, fieldSym.type());
                        yield fieldSym.type();
                    }
                }
                if (sa.diagnostics() != null && !"this".equals(ie.name()) && !"super".equals(ie.name())
                        && !"json".equals(ie.name()) && !"process".equals(ie.name()) && !"shell".equals(ie.name())
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
                        && !KofTetris.isTetrisNamespace(ie.name())
                        && !KofMedia.isStaticNamespace(ie.name())
                        && !KofUi.isPalette(ie.name()) && !KofUi.isConstructor(ie.name())
                        && !KofUiTokens.isTokenNamespace(ie.name())
                        && !KofUi.isRouterNamespace(ie.name())
                        && !"Theme".equals(ie.name())
                        && !MemberResolver.isBuiltinTypeName(ie.name())
                        // bug 127: operando de TIPO do cast `as` — `x as
                        // () -> Int` vira IdentifierExpr com o type-ref
                        // completo (não é variável/tipo declarado).
                        && !(ie.name().startsWith("(") && ie.name().contains(" -> "))
                        && !sa.allClasses().containsKey(ie.name())
                        // §134: nome de classe EXTERNA (Button.inflate,
                        // Greeter.hello) — o lowering (ExpressionMethodCall
                        // Lowerer) resolve via ExternalClasspath; sem este
                        // passe a análise semântica marcava SEM011 e a
                        // chamada estática com receiver identificador nunca
                        // chegava ao lowering (só `new X()` e instância
                        // funcionavam).
                        && !isExternalImportedClass(sa, ie.name())) {
                    sa.diagnostics().error("", 0, 0, 0,
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
                    sa.diagnostics().error("", 0, 0, 0,
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
                            sa.diagnostics().error("", 0, 0, 0,
                                    "undefined variable: '" + ie.name() + "'", "SEM020");
                        }
                    }
                    if (sym != null) {
                        targetType = Narrowing.assignTarget(scope, ie.name(), sym).type();
                        if (sa.diagnostics() != null && !Type.isUnknown(targetType) && !Type.isUnknown(valueType)
                                && !TypeChecker.isAssignable(sa, valueType, targetType)) {
                            sa.diagnostics().error("", 0, 0, 0,
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
                    yield TypeChecker.inferBinaryResultType(sa.diagnostics(), "&&", leftT, rightT);
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
                    accType = TypeChecker.inferBinaryResultType(sa.diagnostics(), be.operator(), accType, rightType);
                }
                yield accType;
            }
            case UnaryExpr ue -> {
                Type operandType = inferType(sa, ue.operand(), scope);
                if ("!".equals(ue.operator())) yield Type.PrimitiveType.BOOL;
                yield operandType;
            }
            case MethodCallExpr mc -> SemMethodCallTyper.infer(sa, mc, scope);
            case NewExpr ne -> {
                Type coll = CompilerTypes.builtinCollectionType(ne.typeName(), sa.unit(), sa);
                if (coll != null) {
                    // #193/#198: aplicar os type-arguments no tipo da colecao,
                    // espelhando o ExpressionTyper do emit (que sempre aplicou).
                    // Sem isso `new List<() -> Int>()` tipava como List<Unknown>
                    // no SEMANTICO e o get(0) devolvia Unknown -> `f()` dava
                    // SEM015 (#193) e o call-chainado `get(0)()` emitia Methodref
                    // vazio (ClassFormatError, #198).
                    if (!ne.typeArguments().isEmpty() && coll instanceof Type.ClassType ct) {
                        coll = new Type.ClassType(ct.packageName(), ct.name(),
                                ne.typeArguments().stream().map(Type::of).toList());
                    }
                    yield coll;
                }
                SymbolTable.ClassSymbol cs = sa.getClass(ne.typeName());
                if (cs != null) {
                    // SG-017 (SEM041): classe abstrata não pode ser instanciada.
                    if (sa.abstractClasses().contains(ne.typeName()) && sa.diagnostics() != null) {
                        sa.diagnostics().error("", 0, 0, 0,
                                "cannot instantiate abstract class '" + ne.typeName() + "'",
                                "SEM041");
                    }
                    // #340 (SEM071): interface não é instanciável — `new I()`.
                    ClassShapeChecks.checkInstantiable(sa, ne.typeName());
                    // Inferencia dos argumentos: o efeito colateral importa
                    // (cache expressionTypes + diagnostics de SEM nas exprs), a
                    // resolucao do construtor e por aridade (constructorFor
                    // aceita int) — o container de tipos era write-only
                    // (CodeQL unused-container: achado real, nao FP).
                    for (ExpressionNode arg : ne.arguments()) {
                        inferType(sa, arg, scope);
                    }
                    SymbolTable.ConstructorSymbol ctor3 =
                            SymbolTable.constructorFor(cs.members(), ne.arguments().size());
                    if (ctor3 != null) {
                        sa.putResolvedConstructor(ne, ctor3);
                        // #323: a RESOLUCAO era so por aridade; o tipo dos
                        // argumentos nunca era conferido contra a assinatura
                        // do construtor. Sem isto, `new A("x")` num ctor
                        // `(Int)` compila e a chamada inventa <init>(String)V
                        // → VerifyError no load (R6/Q7: nunca silencioso).
                        // Overload-aware: irmao de mesma aridade que casa
                        // (isAssignable) passa — mesmo predicado do emit.
                        List<Type> argTypes3 = new ArrayList<>();
                        for (ExpressionNode arg : ne.arguments()) {
                            argTypes3.add(inferType(sa, arg, scope));
                        }
                        TypeChecker.checkCtorArgTypes(sa, cs.members(), ne.typeName(),
                                argTypes3);
                    } else if (sa.diagnostics() != null) {
                        SymbolTable.Symbol anyInit = cs.members().resolve("<init>");
                        if (anyInit instanceof SymbolTable.ConstructorSymbol c) {
                            sa.diagnostics().error("", 0, 0, 0,
                                    "no constructor of '" + ne.typeName() + "' with "
                                            + ne.arguments().size() + " argument(s) (expected "
                                            + c.parameterTypes().size() + ")",
                                    "SEM023");
                        } else if (anyInit instanceof SymbolTable.ConstructorSet set
                                && !set.constructors().isEmpty()) {
                            sa.diagnostics().error("", 0, 0, 0,
                                    "no constructor of '" + ne.typeName() + "' with "
                                            + ne.arguments().size() + " argument(s)",
                                    "SEM023");
                        }
                    }
                    yield new Type.ClassType(cs.packageName(), cs.name(), List.of());
                }
                // classe EXTERNA (android.webkit.WebView etc.): qualifica pelo
                // import e registra o construtor do classpath — sem isso a
                // variável fica Unknown e toda a cadeia de chamadas seguinte
                // perde o tipo
                String qname = ne.typeName();
                if (!qname.contains(".")) {
                    Type viaImport = MemberResolver.qualifyViaImports(sa.unit(), qname,
                            sa.externalTypes());
                    if (viaImport != null) qname = viaImport instanceof Type.ClassType qt
                            ? qt.packageName() + "." + qt.name() : qname;
                }
                if (qname.contains(".") && sa.externalTypes() != null) {
                    String internal = qname.replace('.', '/');
                    if (sa.externalTypes().knows(internal)) {
                        ExternalClasspath.MethodSignature sig =
                                sa.externalTypes().resolveConstructor(internal, ne.arguments().size());
                        if (sig != null) {
                            List<Type> params = new ArrayList<>();
                            for (String d : sig.parameterDescriptors()) {
                                params.add(ExternalClasspath.typeFromDescriptor(d));
                            }
                            sa.putResolvedConstructor(ne, new SymbolTable.ConstructorSymbol(
                                    internal.substring(internal.lastIndexOf('/') + 1), params, 1));
                        }
                        int lastDot = qname.lastIndexOf('.');
                        yield new Type.ClassType(qname.substring(0, lastDot),
                                qname.substring(lastDot + 1), List.of());
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case FieldAccessExpr fa -> {
                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name()) && KofUi.paletteColor(fa.fieldName()) != null) yield KofUi.COLOR;
                if (fa.receiver() instanceof IdentifierExpr tid && KofUiTokens.isTokenNamespace(tid.name())) {
                    if (KofUiTokens.tokenValue(tid.name(), fa.fieldName()) == null && sa.diagnostics() != null) {
                        sa.diagnostics().error("", 0, 0, 0,
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
                    sa.diagnostics().error("", 0, 0, 0,
                            "'" + rid.name() + "' is a primitive type, it has no static field "
                                    + "'" + fa.fieldName() + "' (use the literal, "
                                    + "e.g. 2147483647 for Int; there is no Int.MAX_VALUE in Kof)",
                            "SEM050");
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
                        sa.diagnostics().error("", 0, 0, 0,
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
                // #473/#474: same guard as SwitchStmt — Long/Double/Float crash JVM backend
                if (subjectType != null && sa.diagnostics() != null) {
                    String sn = TypeMetrics.primitiveName(subjectType);
                    if ("long".equals(sn) || "Long".equals(sn)
                            || "double".equals(sn) || "Double".equals(sn)
                            || "float".equals(sn) || "Float".equals(sn)) {
                        sa.diagnostics().error("", 0, 0, 0,
                                "`switch` subject must be `Int`, `Char`, `String`, or an enum — "
                                        + "`" + sn + "` is not supported",
                                "SEM094");
                    }
                }
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

    /**
     * §134: o nome simples é uma classe EXTERNA importada cujo .class está
     * nos entries do ExternalClasspath (--classpath/--deps)? Usado para não
     * marcar SEM011 no receiver de chamada estática externa (Greeter.hello),
     * que o lowering resolve via knows()/resolveMethod().
     */
    private static boolean isExternalImportedClass(SemanticAnalyzer sa, String name) {
        if (sa.externalTypes() == null || sa.unit() == null) return false;
        Type t = MemberResolver.qualifyViaImports(sa.unit(), name, sa.externalTypes());
        return t instanceof Type.ClassType ct && !ct.packageName().isEmpty()
                && sa.externalTypes().knows(ct.internalName());
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
