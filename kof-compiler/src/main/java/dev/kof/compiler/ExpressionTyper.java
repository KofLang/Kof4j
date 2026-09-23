package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipo de expressões (inferExprType) do CompilerDriver.
 * Recebe o driver como host.
 */
public final class ExpressionTyper {

    private ExpressionTyper() {}

    static Type inferExprType(CompilerDriver driver, ExpressionNode expr, List<IRLocalVariable> locals) {
        return switch (expr) {
            case LiteralExpr lit -> switch (lit.kind()) {
                case ConcreteLiteralKind.INT -> Type.PrimitiveType.INT;
                case ConcreteLiteralKind.LONG -> Type.PrimitiveType.LONG;
                case ConcreteLiteralKind.FLOAT -> Type.PrimitiveType.FLOAT;
                case ConcreteLiteralKind.DOUBLE -> Type.PrimitiveType.DOUBLE;
                case ConcreteLiteralKind.STRING -> BuiltinTypes.STRING;
                case ConcreteLiteralKind.BOOLEAN -> Type.PrimitiveType.BOOL;
                case ConcreteLiteralKind.CHAR -> Type.PrimitiveType.CHAR;
                case ConcreteLiteralKind.NULL -> Type.UnknownType.UNKNOWN;
                default -> Type.UnknownType.UNKNOWN;
            };
            case QueryDslExpr q -> new Type.ClassType("kof", "List", List.of(CompilerTypes.toType(q.entityType(), driver.currentUnit)));
            case IdentifierExpr ie -> {
                if (driver.loweringMain && "args".equals(ie.name())
                        && !locals.stream().anyMatch(lv2 -> lv2.name().equals(ie.name()))) {
                    // #397: same declared-local-wins guard as the emit path —
                    // without it the typer yielded String[] for a user-declared
                    // `args` local and the unbox/checkcast followed the wrong
                    // type (the emit's list read crashed with AIOOBE).
                    if (driver.mainArgsListField) {
                        yield KofProcess.STRING_LIST;
                    }
                    yield new Type.ArrayType(BuiltinTypes.STRING);
                }
                for (int i = locals.size() - 1; i >= 0; i--) {
                    if (locals.get(i).name().equals(ie.name())) {
                        IRLocalVariable lv = locals.get(i);
                        if (driver.boxFactory.isBoxType(lv.type())) {
                            yield driver.boxFactory.boxValueType(lv.type());
                        }
                        yield lv.type();
                    }
                }
                if ("super".equals(ie.name()) && driver.semanticAnalyzer != null
                        && !locals.isEmpty()
                        && locals.get(0).type() instanceof Type.ClassType selfType
                        && !"Object".equals(selfType.name())) {
                    // `super` NÃO é variável — é o receiver `this` tipado na
                    // SUPERCLASSE. Sem isto saía UNKNOWN, o lowerField não
                    // resolvia o campo herdado e vertia o valor num
                    // temporário Object (PUTFIELD owner "?").
                    Type sup = HierarchyResolver.superTypeOf(
                            driver.semanticAnalyzer, selfType.internalName());
                    if (sup != null) yield sup;
                }
                if (driver.semanticAnalyzer != null) {
                    // Resolve field within the current class first (via 'this'
                    // at index 0) to avoid picking a same-named field from an
                    // unrelated class — e.g. Config.entries vs MemoryLayer.entries.
                    if (!locals.isEmpty() && locals.get(0).type() instanceof Type.ClassType thisType
                            && !thisType.name().equals("Object")) {
                        SymbolTable.Symbol thisField = driver.semanticAnalyzer.resolveInHierarchy(
                                thisType.name(), ie.name());
                        if (thisField != null) {
                            if (thisField instanceof SymbolTable.FieldSymbol fs) yield fs.type();
                            if (thisField instanceof SymbolTable.MethodSymbol ms
                                    && ms.parameterTypes().isEmpty()) yield ms.returnType();
                        }
                    }
                    SymbolTable.Symbol sym = HierarchyResolver.resolveFromSemantic(ie.name(), driver.semanticAnalyzer);
                    if (sym != null) yield sym.type();
                    SymbolTable.ClassSymbol cls = driver.semanticAnalyzer.getClass(ie.name());
                    if (cls != null) yield cls.type();
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case UnaryExpr ue -> inferExprType(driver, ue.operand(), locals);
            case BinaryExpr bin -> {
                // Left-associative chains (huge string concatenations in
                // generated UIs, editors) are iterated instead of recursed:
                // deep chains would overflow the compiler's own stack.
                java.util.List<BinaryExpr> chain = new ArrayList<>();
                ExpressionNode cursor = bin;
                while (cursor instanceof BinaryExpr be) {
                    chain.add(be);
                    cursor = be.left();
                }
                Type leftType = inferExprType(driver, cursor, locals);
                for (int ci = chain.size() - 1; ci >= 0; ci--) {
                    BinaryExpr be = chain.get(ci);
                    Type rightType = inferExprType(driver, be.right(), locals);
                    if ("+".equals(be.operator())
                            && (Type.isString(leftType) || Type.isString(rightType))) {
                        leftType = BuiltinTypes.STRING;
                        continue;
                    }
                    if ("instanceof".equals(be.operator())) {
                        leftType = Type.PrimitiveType.BOOL;
                        continue;
                    }
                    if ("as".equals(be.operator())) {
                        // "x as Tipo": o tipo alvo passa pelo toType (imports)
                        if (be.right() instanceof IdentifierExpr rie
                                && rightType instanceof Type.UnknownType) {
                            Type q = CompilerTypes.toType(rie.name(), driver.currentUnit);
                            if (!(q instanceof Type.UnknownType)) leftType = q;
                            else leftType = rightType;
                        } else {
                            leftType = rightType;
                        }
                        continue;
                    }
                    // #462: `&&`/`||` materializam `Bool` quando NENHUM lado é
                    // nulável. D-TROOL (19/09): com um `Troolean` num dos lados
                    // o resultado é tres-estado — o lowering Kleene deixa a
                    // caixa (Boolean|null) na pilha, e o consumidor precisa
                    // acreditar no tipo certo (a regra antiga forçava `Bool` e
                    // o join de arcs boxed virava VerifyError invertido).
                    if ("&&".equals(be.operator()) || "||".equals(be.operator())) {
                        boolean anyBool = CompilerComparisons.isNullableBool(leftType)
                                || CompilerComparisons.isNullableBool(
                                        ExpressionTyper.inferExprType(driver, be.right(), locals));
                        leftType = anyBool ? new Type.NullableType(Type.PrimitiveType.BOOL)
                                : Type.PrimitiveType.BOOL;
                        continue;
                    }
                    if (TypeMetrics.isComparisonOp(be.operator())) {
                        leftType = Type.PrimitiveType.BOOL;
                        continue;
                    }
                    // aritmética promove: int/long → long etc. (o lowering
                    // usa commonNumericType; a inferência precisa casar)
                    Type rType = inferExprType(driver, be.right(), locals);
                    if (switch (be.operator()) {
                        case "+", "-", "*", "/", "%" -> true;
                        default -> false;
                    } && TypeMetrics.isNumeric(leftType) && TypeMetrics.isNumeric(rType)) {
                        leftType = TypeMetrics.commonNumericType(leftType, rType);
                        continue;
                    }
                    // §167: bitwise `& | ^` promove ao tipo comum (long se
                    // qualquer lado for long); shift `<< >> >>>` tem o tipo do
                    // operando ESQUERDO promovido (JLS 15.19). Sem isto a
                    // inferência dizia INT p/ `int & long` e o box/despacho
                    // usava Integer sobre um long → VerifyError.
                    if (switch (be.operator()) {
                        case "&", "|", "^" -> true;
                        default -> false;
                    } && TypeMetrics.isInteger(leftType) && TypeMetrics.isInteger(rType)) {
                        leftType = TypeMetrics.commonNumericType(leftType, rType);
                        continue;
                    }
                    if (switch (be.operator()) {
                        case "<<", ">>", ">>>" -> true;
                        default -> false;
                    } && TypeMetrics.isInteger(leftType) && TypeMetrics.isInteger(rType)) {
                        leftType = "long".equals(TypeMetrics.primitiveName(leftType))
                                ? Type.PrimitiveType.LONG : Type.PrimitiveType.INT;
                        continue;
                    }
                }
                yield leftType;
            }
            case MethodCallExpr mc -> {
                yield MethodCallTyper.inferType(driver, mc, locals);
            }
            case NewArrayExpr na -> {
                Type elemType = CompilerTypes.toType(na.elementType(), driver.currentUnit);
                for (int i = 0; i < na.moreDims().size(); i++) {
                    elemType = new Type.ArrayType(elemType);
                }
                yield new Type.ArrayType(elemType);
            }
            case NewExpr ne -> {
                Type t = CompilerTypes.toType(ne.typeName(), driver.currentUnit);
                Type coll = CompilerTypes.builtinCollectionType(ne.typeName(), driver.currentUnit, driver.semanticAnalyzer);
                if (coll != null) {
                    t = coll;
                }
                if (!ne.typeArguments().isEmpty() && t instanceof Type.ClassType cts) {
                    // #585: resolução ciente do analyzer — o 2-arg podia deixar
                    // um argumento reference-type sem qualificar (internal "").
                    t = new Type.ClassType(cts.packageName(), cts.name(),
                            ne.typeArguments().stream().map(n -> CompilerTypes.toType(n,
                                    driver.currentUnit, driver.semanticAnalyzer)).toList());
                }
                yield t;
            }
            case ArrayAccessExpr aa -> {
                Type recvType = inferExprType(driver, aa.receiver(), locals);
                // #152/#149: `list[i]`/`m[k]` sobre coleção = get do elemento
                // (o emit baixa p/ kof_list_get/kof_map_get — espelha aqui).
                if (BuiltinTypes.isList(recvType)) {
                    Type elem = recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()
                            ? ct.typeArguments().get(0) : Type.UnknownType.UNKNOWN;
                    yield elem;
                }
                if (BuiltinTypes.isMap(recvType)) {
                    Type val = recvType instanceof Type.ClassType ct && ct.typeArguments().size() > 1
                            ? ct.typeArguments().get(1) : Type.UnknownType.UNKNOWN;
                    yield val;
                }
                if (recvType instanceof Type.ArrayType at) yield at.componentType();
                yield Type.UnknownType.UNKNOWN;
            }
            case FieldAccessExpr fa -> {
                Type recvType = inferExprType(driver, fa.receiver(), locals);
                // narrowing de null-safety: `if (x != null) { x.length }` — inner type
                if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
                if (KofProcess.isResult(recvType) && KofProcess.isField(fa.fieldName())) {
                    yield KofProcess.fieldType(fa.fieldName());
                }
                if (KofUi.isComponent(recvType) && "state".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (KofUi.isWindow(recvType) && "title".equals(fa.fieldName())) {
                    yield BuiltinTypes.STRING;
                }
                if (KofUi.isLabel(recvType) && "text".equals(fa.fieldName())) {
                    yield BuiltinTypes.STRING;
                }
                if (KofUi.isLabel(recvType) && "fontSize".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (KofUi.isLabel(recvType) && "bold".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.BOOL;
                }
                if (KofUi.isLabel(recvType) && "color".equals(fa.fieldName())) {
                    yield KofUi.COLOR;
                }
                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())
                        && KofUi.paletteColor(fa.fieldName()) != null) {
                    yield KofUi.COLOR;
                }
                if (fa.receiver() instanceof IdentifierExpr tid && KofUiTokens.isTokenNamespace(tid.name())
                        && KofUiTokens.tokenValue(tid.name(), fa.fieldName()) != null) {
                    yield Type.PrimitiveType.INT;
                }
                if (BuiltinTypes.isList(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    yield Type.PrimitiveType.INT;
                }
                if (BuiltinTypes.isMap(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    yield Type.PrimitiveType.INT;
                }
                if (BuiltinTypes.isSet(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    yield Type.PrimitiveType.INT;
                }
                if (recvType instanceof Type.ArrayType && ("length".equals(fa.fieldName())
                        || "size".equals(fa.fieldName()) || "count".equals(fa.fieldName()))) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
                    yield BuiltinTypes.STRING;
                }
                if (recvType instanceof Type.ClassType ct
                        && CompilerTypes.isEnumName(ct.name(), driver.currentUnit)) { // #445: pkg real
                    if (!CompilerTypes.enumConstantsOf(ct.name(), driver.currentUnit).contains(fa.fieldName()) && driver.currentDiagnostics != null) {
                        driver.currentDiagnostics.error(fa,
                                "enum '" + ct.name() + "' has no constant '" + fa.fieldName() + "'",
                                "SEM030");
                    }
                    yield recvType;
                }
                if (recvType instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
                    SymbolTable.Symbol s = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
                    if (s instanceof SymbolTable.FieldSymbol fs) {
                        yield CompilerTypes.substituteTypeVariableIn(fs.type(), recvType, driver.currentUnit);
                    }
                    if (s instanceof SymbolTable.MethodSymbol ms && ms.parameterTypes().isEmpty()) {
                        yield CompilerTypes.substituteTypeVariableIn(ms.returnType(), recvType, driver.currentUnit);
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case LambdaExpr le -> {
                List<Type> paramTypes = new ArrayList<>();
                List<IRLocalVariable> extended = new ArrayList<>(locals);
                int pidx = 0;
                for (FormalParameterNode p : le.parameters()) {
                    Type pt = CompilerTypes.toType(p.type(), driver.currentUnit);
                    paramTypes.add(pt);
                    extended.add(new IRLocalVariable(pidx++, p.name(), pt));
                }
                Type returnType = firstReturnValueType(driver, le.body(), extended);
                if (Type.UnknownType.UNKNOWN.equals(returnType)) {
                    // A lambda whose body has no return statement is void.
                    // Without this, the synthetic invoke method is lowered with
                    // an Object return and the backends misparse the bare
                    // KofReturn (empty value stack).
                    // (closures.md §1: corpo-bloco exige return EXPLÍCITO — o
                    // implícito só vale no caminho do spawn, ver
                    // inferLambdaBodyType, GitHub #141.)
                    returnType = Type.PrimitiveType.VOID;
                }
                yield new Type.FunctionType(paramTypes, returnType, driver.lambdaClassNames.get(le));
            }
            case IfExpr ie -> {
                Type thenType = inferExprType(driver, ie.thenExpr(), locals);
                Type elseType = ie.elseExpr() != null ? inferExprType(driver, ie.elseExpr(), locals) : Type.UnknownType.UNKNOWN;
                if (thenType.equals(elseType)) yield thenType;
                if (driver.semanticAnalyzer != null) {
                    // §284-map (18/09): ramo `null` literal obriga o join a
                    // ser NULLABLE do outro lado — o commonSupertype antigo
                    // colapsava `Int`+`null` p/ `Int` (a caixa do join ficava
                    // mentindo o static type: println imprimia o ponteiro da
                    // caixa, `==` derefava inteiro — medido ifexpr-intnull).
                    var joined = HierarchyResolver.commonSupertype(driver.semanticAnalyzer, thenType, elseType);
                    yield nullableIfNullBranch(joined, ie.thenExpr(), ie.elseExpr());
                }
                List<Type> bts = ifBranchTypes(driver, ie, locals);
                if (branchTypesDiffer(bts)) {
                    yield new Type.ClassType("java.lang", "Object", List.of());
                }
                yield thenType;
            }            case SwitchExpr se -> {
                if (!se.cases().isEmpty()) {
                    // #601: o case 0 pode ser um pattern (`case Lit(var v) -> v`)
                    // cuja var ainda não existe em `locals` aqui — a inferência
                    // roda ANTES do lowering/binding. Sem projetar o binding num
                    // locals descartável, o lookup do corpo falhava (UnknownType),
                    // que vazava pro fallback sintético do switch como um default
                    // de REFERÊNCIA (checkcast Integer) contra ramos que empilham
                    // int puro → VerifyError no merge do stack map.
                    List<IRLocalVariable> case0Locals = localsWithPatternBinding(driver, se.cases().get(0), locals);
                    Type case0Body = inferExprType(driver, se.cases().get(0).body(), case0Locals);
                    List<Type> bts = switchBranchTypes(driver, se.cases(), se.defaultValue(), case0Body, locals);
                    if (branchTypesDiffer(bts)) {
                        yield new Type.ClassType("java.lang", "Object", List.of());
                    }
                    // §284-map: mesmo contrato nullable do if-expr.
                    yield nullableIfNullBranch(case0Body, se.cases().get(0).body(),
                            se.defaultValue() != null ? se.defaultValue() : se.cases().get(0).body());
                }
                yield se.defaultValue() != null ? inferExprType(driver, se.defaultValue(), locals)
                        : Type.UnknownType.UNKNOWN;
            }
            default -> Type.UnknownType.UNKNOWN;
        };
    }

    /**
     * #601: projeta o(s) binding(s) de pattern de UM case num locals-escopo
     * descartável (cópia — nunca muta `locals`), só para type-inference. Se
     * `c.value()` não for pattern, devolve `locals` como veio. Espelha o
     * cálculo de tipo de campo de `SwitchExprLowerer.emitPatternBinding` (sem
     * emitir bytecode, índice de slot é irrelevante aqui — a busca de
     * identificador em `inferExprType` é por NOME).
     */
    static List<IRLocalVariable> localsWithPatternBinding(CompilerDriver driver, SwitchExprCase c,
                                                           List<IRLocalVariable> locals) {
        if (!(c.value() instanceof PatternExpr pe)) return locals;
        Type patType = CompilerTypes.toType(pe.typeName(), driver.currentUnit);
        if (patType instanceof Type.UnknownType) patType = BuiltinTypes.STRING;
        List<IRLocalVariable> extended = new ArrayList<>(locals);
        if (pe.varName() != null) {
            extended.add(new IRLocalVariable(locals.size(), pe.varName(), patType));
            return extended;
        }
        String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
        for (int fi = 0; fi < pe.fieldVars().size(); fi++) {
            String fieldVar = pe.fieldVars().get(fi);
            Type fieldType = Type.UnknownType.UNKNOWN;
            if (driver.currentUnit != null) {
                for (AstNode d : driver.currentUnit.declarations()) {
                    if (d instanceof RecordDeclarationNode rec && rec.name().equals(simple)) {
                        if (fi < rec.components().size()) {
                            fieldType = CompilerTypes.toType(rec.components().get(fi).type(), driver.currentUnit);
                        }
                        break;
                    }
                }
            }
            if (fieldType instanceof Type.UnknownType) fieldType = BuiltinTypes.STRING;
            extended.add(new IRLocalVariable(extended.size(), fieldVar, fieldType));
        }
        return extended;
    }

    /**
     * Ramos divergem? (issue #57 generalizada p/ §70): tipos distintos entre
     * branches de if/switch. Só dispara sobre tipos CONCRETOS (primitivo
     * não-void, classe, array, nullable destrinchado); Unknown/TypeVariable/
     * Function (lambdas!) → false = status quo (nunca quebrar o que hoje
     * verifica por acidente). Distintos → cada ramo primitivo é boxeado
     * p/ SEU PRÓPRIO boxed (sem widening: `2L` continua `Long(2)`, não
     * `Double(2.0)` — paridade com o interpretador por construção) e o
     * caller pula o pós-box (join só tem referências).
     */
    static boolean branchTypesDiffer(List<Type> ts) {
        for (Type t : ts) if (!isConcreteBranchType(t)) return false;
        return ts.stream().distinct().count() > 1;
    }

    static boolean isConcreteBranchType(Type t) {
        if (t instanceof Type.PrimitiveType pt) return !"void".equals(pt.name());
        if (t instanceof Type.ClassType) return true;
        if (t instanceof Type.ArrayType) return true;
        if (t instanceof Type.NullableType nt) return isConcreteBranchType(nt.inner());
        return false;
    }

    /** Tipos dos ramos do if (then, else) p/ `branchTypesDiffer`. */
    /** §284-map: `T` + ramo literal `null` → `T?` (nunca toca nullable/objeto). */
    static Type nullableIfNullBranch(Type joined, ExpressionNode... branches) {
        if (!(joined instanceof Type.PrimitiveType pt) || Type.isVoid(pt)) return joined;
        for (ExpressionNode b : branches) {
            if (b instanceof LiteralExpr lit && lit.kind() == ConcreteLiteralKind.NULL) {
                return new Type.NullableType(pt);
            }
        }
        return joined;
    }

    static List<Type> ifBranchTypes(CompilerDriver driver, IfExpr ie,
                                    List<IRLocalVariable> locals) {
        return List.of(branchTypeOrNullAsRef(driver, ie.thenExpr(), locals),
                branchTypeOrNullAsRef(driver, ie.elseExpr(), locals));
    }

    /** Tipos dos corpos do switch (+ default; sem default, o sintético tem o
     *  tipo do switch — igual ao lowering, que emite `defaultValueOp`). */
    static List<Type> switchBranchTypes(CompilerDriver driver, List<SwitchExprCase> cases,
                                        ExpressionNode defaultValue, Type switchFallbackType,
                                        List<IRLocalVariable> locals) {
        var ts = new java.util.ArrayList<Type>();
        for (SwitchExprCase c : cases) ts.add(branchTypeOrNullAsRef(driver, c.body(), locals));
        ts.add(defaultValue != null ? branchTypeOrNullAsRef(driver, defaultValue, locals) : switchFallbackType);
        return ts;
    }

    /**
     * Tipo de um ramo p/ o predicado — com uma exceção honesta: literal
     * `null` infere `UnknownType` (não-concreto → predicado desligaria), mas
     * `null` é referência 1-word no JVM. Sem isso, `if (c) 1 else null`
     * seguia VerifyError mesmo com o mecanismo pronto.
     */
    static Type branchTypeOrNullAsRef(CompilerDriver driver, ExpressionNode e,
                                      List<IRLocalVariable> locals) {
        if (e instanceof LiteralExpr lit && lit.kind() == ConcreteLiteralKind.NULL) {
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        return inferExprType(driver, e, locals);
    }

    /**
     * true = a expressão já boxeou seus ramos primitivos in-branch (#57/§70)
     * → o caller deve PULAR o box pós-expressão (senão box duplo). Cobre
     * IfExpr e SwitchExpr heterogêneos; demais nós → false.
     */
    static boolean boxesOwnBranches(CompilerDriver driver, ExpressionNode e,
                                    List<IRLocalVariable> locals) {
        if (e instanceof IfExpr ie && ie.elseExpr() != null)
            return branchTypesDiffer(ifBranchTypes(driver, ie, locals));
        if (e instanceof SwitchExpr se)
            // §149: fallback sintético (sem default) tem o tipo do RESULTADO, não
            // o do subject — alinhado ao lowering (SwitchExprLowerer.emitSwitchExpr).
            return branchTypesDiffer(switchBranchTypes(driver, se.cases(),
                    se.defaultValue(), inferExprType(driver, se, locals), locals));
        return false;
    }

    /** Boxa o ramo se primitivo (p/ SEU boxed; JVM-only via emitErasureBox). */
    static void boxPrimitiveBranch(CompilerDriver driver, List<KofOperation> ops, Type branchT) {
        // D-NULL-INTENT: Nullable(primitivo) já É boxed em TODO target
        // (slot de `Int?` físico = Integer após #438) — boxar de novo
        // emitia Object.valueOf(Integer) (NoSuchMethodError, §294-2a).
        // isPrimitiveType olha DENTRO do Nullable, então o guard precisa
        // do teste cru: só primitivo NÃO-nullable boxa aqui.
        if (branchT instanceof Type.PrimitiveType) {
            // §284-map (18/09): no NATIVE o join também boxea — o contrato de
            // `Int?` no native passa a ser FISICAMENTE boxed (caixa MAGIC,
            // null = 0), igual ao JVM pós-#438. Os consumidores (println/==/
            // aritmética) leram via soft-unbox/kof_box_equals, que aceitam
            // caixa e null; o cru residual (funções locais, caminho antigo)
            // passa no soft. Medido: sem o box do join, `if (c) 1 else null`
            // imprimia o ponteiro da caixa (`1551450144`) e o `==` derefava
            // inteiro cru — contrato misto.
            driver.emitErasureBox(ops, branchT);
        }
    }

    /**
     * Tipo de RETORNO de uma lambda (bug 29): {@code spawn { println(x) }}
     * é void — o Handle<T> da task deve carregar T=void, não o
     * FunctionType da própria lambda. Lambda sem return é void (mesma
     * regra do caso LambdaExpr acima); lambda que retorna lambda preserva
     * a FunctionType (bug 19).
     *
     * #141: corpo-bloco de UMA expressão ({@code spawn { "ok" }}) É o
     * retorno — o MESMO contrato que a emissão já aplica em
     * {@code CompilerLambdaClass} (convert `ExpressionStmt` único em
     * `ReturnStmt` quando o tipo não é void). Sem este ramo, o typer dava
     * VOID e a conversão do lowerer (gateada em !isVoid) nunca disparava →
     * {@code Handle<Void>} e `SEM033` falso-positivo no `await`. A varredura
     * de `firstReturnValueType` só enxerga `return` EXPLÍCITO; o corpo de
     * expressão única é a borda complementar (não descreve `return`).
     */
    static Type inferLambdaBodyType(CompilerDriver driver, LambdaExpr le,
                                    List<IRLocalVariable> locals) {
        List<IRLocalVariable> extended = new ArrayList<>(locals);
        int pidx = 0;
        for (FormalParameterNode p : le.parameters()) {
            Type pt = CompilerTypes.toType(p.type(), driver.currentUnit);
            extended.add(new IRLocalVariable(pidx++, p.name(), pt));
        }
        Type t = firstReturnValueType(driver, le.body(), extended);
        if (Type.UnknownType.UNKNOWN.equals(t)
                && le.body().size() == 1
                && le.body().get(0) instanceof ExpressionStmt es) {
            Type et = inferExprType(driver, es.expression(), extended);
            if (!Type.UnknownType.UNKNOWN.equals(et) && !Type.isVoid(et)) t = et;
        }
        return Type.UnknownType.UNKNOWN.equals(t) ? Type.PrimitiveType.VOID : t;
    }

    /**
     * Tipo do PRIMEIRO `return` com valor de um corpo de statements, varrendo
     * RECURSIVAMENTE if/switch/try/loops/blocos (bug 53, GitHub #28): antes só
     * se olhava o topo do corpo, então `if (x) { return "ok" } return null`
     * tipava a lambda como VOID (o `return null` é UNKNOWN) e o backend
     * descartava o valor de sucesso — o handler web respondia 404. Não desce
     * em lambdas aninhadas (o return delas pertence a outro corpo).
     */
    private static Type firstReturnValueType(CompilerDriver driver,
                                             List<StatementNode> body,
                                             List<IRLocalVariable> locals) {
        // §176: declarações locais do próprio corpo (`var x = 1; return x`)
        // precisam entrar no escopo da varredura, senão o `return x` é UNKNOWN
        // e a lambda tipa VOID (o backend descarta o valor). Cópia mutável:
        // o escopo do chamador não pode ser poluído.
        List<IRLocalVariable> scope = new ArrayList<>(locals);
        for (StatementNode s : body) {
            if (s instanceof VarDeclStmt vds) {
                Type vt = CompilerTypes.toType(vds.type(), driver.currentUnit);
                if (("var".equals(vds.type()) || "val".equals(vds.type()))
                        && vds.initializer() != null) {
                    vt = inferExprType(driver, vds.initializer(), scope);
                }
                scope.add(new IRLocalVariable(0, vds.name(), vt));
                continue;
            }
            Type t = returnValueType(driver, s, scope);
            if (!Type.UnknownType.UNKNOWN.equals(t)) return t;
        }
        return Type.UnknownType.UNKNOWN;
    }

    private static Type returnValueType(CompilerDriver driver, StatementNode s,
                                        List<IRLocalVariable> locals) {
        if (s instanceof ReturnStmt rs) {
            return rs.value() != null ? inferExprType(driver, rs.value(), locals)
                    : Type.UnknownType.UNKNOWN;
        }
        if (s instanceof BlockStmt bs) return firstReturnValueType(driver, bs.statements(), locals);
        if (s instanceof IfStmt is) {
            Type t = returnValueType(driver, is.thenBranch(), locals);
            if (!Type.UnknownType.UNKNOWN.equals(t)) return t;
            if (is.elseBranch() != null) return returnValueType(driver, is.elseBranch(), locals);
            return Type.UnknownType.UNKNOWN;
        }
        if (s instanceof SwitchStmt ss) {
            for (SwitchCase sc : ss.cases()) {
                Type t = firstReturnValueType(driver, sc.body(), locals);
                if (!Type.UnknownType.UNKNOWN.equals(t)) return t;
            }
            return firstReturnValueType(driver, ss.defaultBody(), locals);
        }
        if (s instanceof TryStmt ts) {
            Type t = firstReturnValueType(driver, ts.tryBody(), locals);
            if (!Type.UnknownType.UNKNOWN.equals(t)) return t;
            for (CatchClause cc : ts.catchClauses()) {
                t = firstReturnValueType(driver, cc.body(), locals);
                if (!Type.UnknownType.UNKNOWN.equals(t)) return t;
            }
            return firstReturnValueType(driver, ts.finallyBody(), locals);
        }
        if (s instanceof WhileStmt ws) return returnValueType(driver, ws.body(), locals);
        if (s instanceof DoWhileStmt dws) return returnValueType(driver, dws.body(), locals);
        if (s instanceof ForStmt fs) return returnValueType(driver, fs.body(), locals);
        if (s instanceof ForInStmt fis) return returnValueType(driver, fis.body(), locals);
        return Type.UnknownType.UNKNOWN;
    }



    /**
     * True quando a cadeia de superclasses a partir de internalName é
     * inteiramente conhecida pelo SemanticAnalyzer (nenhuma classe externa
     * no caminho). Só nesse caso "método não resolvido" prova inexistência.
     */
}