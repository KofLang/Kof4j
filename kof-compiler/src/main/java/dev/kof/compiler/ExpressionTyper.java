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
                if (driver.loweringMain && "args".equals(ie.name())) {
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
                    leftType = leftType;
                }
                yield leftType;
            }
            case MethodCallExpr mc -> {
                yield MethodCallTyper.inferType(driver, mc, locals);
            }
            case NewArrayExpr na -> {
                Type elemType = CompilerTypes.toType(na.elementType(), driver.currentUnit);
                yield new Type.ArrayType(elemType);
            }
            case NewExpr ne -> {
                Type t = CompilerTypes.toType(ne.typeName(), driver.currentUnit);
                if ("List".equals(ne.typeName()) || "ArrayList".equals(ne.typeName())) {
                    t = BuiltinTypes.LIST;
                }
                if (!ne.typeArguments().isEmpty() && t instanceof Type.ClassType cts) {
                    t = new Type.ClassType(cts.packageName(), cts.name(),
                            ne.typeArguments().stream().map(n -> CompilerTypes.toType(n, driver.currentUnit)).toList());
                }
                yield t;
            }
            case ArrayAccessExpr aa -> {
                Type recvType = inferExprType(driver, aa.receiver(), locals);
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
                if (BuiltinTypes.isList(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    yield Type.PrimitiveType.INT;
                }
                if (BuiltinTypes.isMap(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    yield Type.PrimitiveType.INT;
                }
                if (BuiltinTypes.isSet(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    yield Type.PrimitiveType.INT;
                }
                if (recvType instanceof Type.ArrayType at && ("length".equals(fa.fieldName())
                        || "size".equals(fa.fieldName()) || "count".equals(fa.fieldName()))) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
                    yield BuiltinTypes.STRING;
                }
                if (recvType instanceof Type.ClassType ct && ct.packageName().isEmpty()
                        && CompilerTypes.isEnumName(ct.name(), driver.currentUnit)) {
                    if (!CompilerTypes.enumConstantsOf(ct.name(), driver.currentUnit).contains(fa.fieldName()) && driver.currentDiagnostics != null) {
                        driver.currentDiagnostics.error("", 0, 0, 0,
                                "enum '" + ct.name() + "' não tem constante '" + fa.fieldName() + "'",
                                "SEM030");
                    }
                    yield recvType;
                }
                if (recvType instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
                    SymbolTable.Symbol s = driver.semanticAnalyzer.resolveInHierarchy(ct.name(), fa.fieldName());
                    if (s instanceof SymbolTable.FieldSymbol fs) yield fs.type();
                    if (s instanceof SymbolTable.MethodSymbol ms && ms.parameterTypes().isEmpty()) {
                        yield ms.returnType();
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
                    returnType = Type.PrimitiveType.VOID;
                }
                yield new Type.FunctionType(paramTypes, returnType, driver.lambdaClassNames.get(le));
            }
            case IfExpr ie -> {
                Type thenType = inferExprType(driver, ie.thenExpr(), locals);
                Type elseType = inferExprType(driver, ie.elseExpr(), locals);
                yield thenType;
            }            case SwitchExpr se -> {
                if (!se.cases().isEmpty()) {
                    yield inferExprType(driver, se.cases().get(0).body(), locals);
                }
                yield se.defaultValue() != null ? inferExprType(driver, se.defaultValue(), locals)
                        : Type.UnknownType.UNKNOWN;
            }
            default -> Type.UnknownType.UNKNOWN;
        };
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
            return branchTypesDiffer(switchBranchTypes(driver, se.cases(),
                    se.defaultValue(), inferExprType(driver, se.expression(), locals), locals));
        return false;
    }

    /** Boxa o ramo se primitivo (p/ SEU boxed; JVM-only via emitErasureBox). */
    static void boxPrimitiveBranch(CompilerDriver driver, List<KofOperation> ops, Type branchT) {
        if (TypeMetrics.isPrimitiveType(branchT)) driver.emitErasureBox(ops, branchT);
    }

    /**
     * Tipo de RETORNO de uma lambda (bug 29): {@code spawn { println(x) }}
     * é void — o Handle<T> da task deve carregar T=void, não o
     * FunctionType da própria lambda. Lambda sem return é void (mesma
     * regra do caso LambdaExpr acima); lambda que retorna lambda preserva
     * a FunctionType (bug 19).
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
        for (StatementNode s : body) {
            Type t = returnValueType(driver, s, locals);
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