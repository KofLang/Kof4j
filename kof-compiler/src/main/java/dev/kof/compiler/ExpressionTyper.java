package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipo de expressões (inferExprType) do CompilerDriver.
 * Recebe o driver como host.
 */
final class ExpressionTyper {

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
                // super.metodo(): resolvido AQUI (o cache do analyzer é
                // limpo a cada classe/passe — não dá para confiar nele)
                if (mc.receiver() instanceof IdentifierExpr srid && "super".equals(srid.name())
                        && driver.semanticAnalyzer != null && driver.currentLoweringOwner != null) {
                    String simple = driver.currentLoweringOwner.substring(driver.currentLoweringOwner.lastIndexOf('/') + 1);
                    SymbolTable.ClassSymbol self = driver.semanticAnalyzer.getClass(simple);
                    String sup = self != null && self.superClass() != null ? self.superClass() : "Object";
                    sup = sup.replace('.', '/');
                    SymbolTable.Symbol m2 = driver.semanticAnalyzer.resolveInHierarchy(
                            sup.substring(sup.lastIndexOf('/') + 1), mc.methodName());
                    if (m2 instanceof SymbolTable.MethodSymbol ms2) yield ms2.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                // o analyzer já tipou esta expressão durante a análise:
                // fonte secundária para os demais casos
                if (driver.semanticAnalyzer != null) {
                    Type semantic = driver.semanticAnalyzer.getExpressionType(mc);
                    // tipos com FunctionType de className null vêm da análise
                    // semântica, que roda ANTES da síntese das lambdas — são
                    // obsoletos para o emit (o invoke de lambda precisaria do
                    // className → owner "" → ClassFormatError, bug 20). Re-inferir.
                    if (!(semantic instanceof Type.UnknownType)
                            && !CompilerTypes.containsLambdaFunctionType(semantic)) {
                        if (semantic instanceof Type.TypeVariable tv && mc.receiver() != null) {
                            Type recvT = inferExprType(driver, mc.receiver(), locals);
                            Type subst = CompilerTypes.substituteTypeVariable(tv.name(), recvT, driver.currentUnit);
                            if (subst != null) yield subst;
                        }
                        yield semantic;
                    }
                }
                if (mc.receiver() == null && driver.semanticAnalyzer != null
                        && driver.semanticAnalyzer.getClass(mc.methodName()) != null) {
                    yield driver.semanticAnalyzer.getClass(mc.methodName()).type();
                }
                if (mc.receiver() != null && "toString".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    Type rv = inferExprType(driver, mc.receiver(), locals);
                    if (TypeMetrics.isPrimitiveType(rv) || rv instanceof Type.ArrayType) yield BuiltinTypes.STRING;
                }
                // String.valueOf(x) / Integer.valueOf(x)…: receiver é o NOME
                // do tipo builtin (estático). Sem tipo aqui o concat após um
                // "s = s + String.valueOf(x)" aplicava box+valueOf duplicado
                // no resultado (frame crash — 3 valueOf na pilha)
                if (mc.receiver() instanceof IdentifierExpr srid && mc.arguments().size() == 1
                        && driver.findLocalVar(srid.name(), locals) == null
                        && switch (srid.name()) {
                            case "String", "Int", "Integer", "Long", "Float",
                                    "Double", "Bool", "Boolean" -> true;
                            default -> false;
                        }) {
                    yield BuiltinTypes.STRING;
                }
                if ("println".equals(mc.methodName()) || "print".equals(mc.methodName())) yield Type.PrimitiveType.VOID;
                if ("now".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().isEmpty()) {
                    yield Type.PrimitiveType.LONG;
                }
                if ("uiNodesLive".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().isEmpty()) {
                    yield Type.PrimitiveType.INT;
                }
                if ("emit".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().size() == 2) {
                    yield Type.PrimitiveType.VOID;
                }
                if ("storesLive".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().isEmpty()) {
                    yield Type.PrimitiveType.INT;
                }
                if (mc.receiver() == null && "transaction".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield Type.PrimitiveType.VOID;
                }
                if ("readLine".equals(mc.methodName()) && mc.receiver() == null) {
                    yield new Type.NullableType(BuiltinTypes.STRING);
                }
                if ("readFile".equals(mc.methodName()) && mc.receiver() == null) {
                    yield new Type.NullableType(BuiltinTypes.STRING);
                }
                if (mc.receiver() == null && KofWeb.isContextFunction(mc.methodName())
                        && KofWeb.contextCall(mc.methodName(), mc.arguments().size()) != null) {
                    yield BuiltinTypes.STRING;
                }
                if ("writeFile".equals(mc.methodName()) && mc.receiver() == null) {
                    yield Type.PrimitiveType.INT;
                }
                if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofIo.constructorType(mc.methodName());
                }
                if (mc.receiver() == null && "Color".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 3)) {
                    yield KofUi.COLOR;
                }
                if (mc.receiver() == null && "Window".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofUi.WINDOW;
                }
                if (mc.receiver() == null && "Label".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofUi.LABEL;
                }
                if (mc.receiver() == null && "Button".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
                    yield KofUi.BUTTON;
                }
                if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofUi.INPUT;
                }
                if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
                        && mc.arguments().size() == 1) {
                    yield "Column".equals(mc.methodName()) ? KofUi.COLUMN : KofUi.ROW;
                }
                if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofUi.VIEW;
                }
                if (mc.receiver() == null && KofUi.isConstructor(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2
                                || mc.arguments().size() == 3)) {
                    Type ct = KofUi.constructorType(mc.methodName());
                    if (KofUi.isLayoutType(ct) || KofUi.isStore(ct)) {
                        yield ct;
                    }
                }
                if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
                    yield KofUi.STYLE;
                }
                if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
                    KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
                    if (uiCall != null) yield uiCall.returnType();
                }
                if (mc.receiver() instanceof IdentifierExpr ridR && KofUi.isRouterNamespace(ridR.name())) {
                    KofUi.UiCall routerCall = KofUi.staticMethod("Router", mc.methodName(), mc.arguments().size());
                    if (routerCall != null) {
                        for (ExpressionNode arg : mc.arguments()) inferExprType(driver, arg, locals);
                        yield routerCall.returnType();
                    }
                }
                if ("listOf".equals(mc.methodName()) && mc.receiver() == null) {
                    yield new Type.ClassType("kof", "List", List.of(driver.listOfElementType(mc, locals)));
                }
                if ("mapOf".equals(mc.methodName()) && mc.receiver() == null) {
                    // pinning do tipo no primeiro par — espelha o emit (mapOf(k1,v1,...))
                    Type keyType = mc.arguments().isEmpty() ? Type.UnknownType.UNKNOWN
                            : inferExprType(driver, mc.arguments().get(0), locals);
                    Type valueType = mc.arguments().size() < 2 ? Type.UnknownType.UNKNOWN
                            : inferExprType(driver, mc.arguments().get(1), locals);
                    yield new Type.ClassType("kof", "Map", List.of(keyType, valueType));
                }
                if ("setOf".equals(mc.methodName()) && mc.receiver() == null) {
                    Type elemType = mc.arguments().isEmpty() ? Type.UnknownType.UNKNOWN : inferExprType(driver, mc.arguments().get(0), locals);
                    yield new Type.ClassType("kof", "Set", List.of(elemType));
                }
                if (mc.receiver() == null && "__kof_spawn_expr".equals(mc.methodName())) {
                    Type t = inferExprType(driver, mc.arguments().get(0), locals);
                    yield new Type.ClassType("kof.concurrent", "Handle", List.of(t));
                }
                                if (mc.receiver() == null && "cancel".equals(mc.methodName())
                        && mc.arguments().size() == 1
                        && driver.findLocalVar("cancel", locals) == null) {
                    yield Type.PrimitiveType.BOOL;
                }
                if (mc.receiver() == null && "cancelled".equals(mc.methodName())
                        && mc.arguments().isEmpty()
                        && driver.findLocalVar("cancelled", locals) == null) {
                    yield Type.PrimitiveType.BOOL;
                }
                if (mc.receiver() == null && "selectAny".equals(mc.methodName())
                        && !mc.arguments().isEmpty()
                        && driver.findLocalVar("selectAny", locals) == null) {
                    Type first = inferExprType(driver, mc.arguments().get(0), locals);
                    if (first instanceof Type.ClassType ct
                            && "kof.concurrent".equals(ct.packageName())
                            && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && "poll".equals(mc.methodName())
                        && mc.arguments().size() == 1 && driver.findLocalVar("poll", locals) == null) {
                    Type h = inferExprType(driver, mc.arguments().get(0), locals);
                    if (h instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && "done".equals(mc.methodName())
                        && mc.arguments().size() == 1 && driver.findLocalVar("done", locals) == null) {
                    yield Type.PrimitiveType.BOOL;
                }
                if (mc.receiver() == null && "__kof_await".equals(mc.methodName())) {
                    Type t = inferExprType(driver, mc.arguments().get(0), locals);
                    if (t instanceof Type.ClassType ct
                            && "kof.concurrent".equals(ct.packageName())
                            && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && "awaitTimeout".equals(mc.methodName())
                        && mc.arguments().size() == 2
                        && driver.findLocalVar("awaitTimeout", locals) == null) {
                    Type t = inferExprType(driver, mc.arguments().get(0), locals);
                    if (t instanceof Type.ClassType ct
                            && "kof.concurrent".equals(ct.packageName())
                            && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && CompilerTypes.isEnumName(rid.name(), driver.currentUnit)
                        && driver.findLocalVar(rid.name(), locals) == null) {
                    java.util.List<String> consts = CompilerTypes.enumConstantsOf(rid.name(), driver.currentUnit);
                    Type enumT = new Type.ClassType("", rid.name(), List.of());
                    // MVP: elementos tipados como String (runtime do enum é o nome);
                    // comparação com constantes funciona via string-equals
                    if ("values".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        yield new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING));
                    }
                    if ("valueOf".equals(mc.methodName()) && mc.arguments().size() == 1) {
                        yield enumT;
                    }
                    // constante via sintaxe de método? Color.Red() — não suportado
                    if (consts.contains(mc.methodName())) yield enumT;
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && "json".equals(rid.name())) {
                    if ("encode".equals(mc.methodName())) yield BuiltinTypes.STRING;
                    if ("decode".equals(mc.methodName()) && !mc.typeArguments().isEmpty()) {
                        yield CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofWeb.isWebNamespace(rid.name())) {
                    if ("app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        yield KofWeb.APP;
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofDb.isDbNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
                    KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
                    if (dbCall != null) {
                        if (typed && !mc.typeArguments().isEmpty()) {
                            yield new Type.ClassType("kof", "List",
                                    List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
                        }
                        yield dbCall.returnType();
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofHttp.isHttpNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
                    if (httpCall != null) yield httpCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofMq.isMqNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
                    if (mqCall != null) yield mqCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofTime.isTimeNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
                    if (timeCall != null) yield timeCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofScheduler.isSchedulerNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    KofScheduler.SchedulerCall sc = KofScheduler.staticCall(mc.methodName(), argTypes);
                    if (sc != null) yield sc.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && KofScheduler.isSchedulerMethod(mc.methodName())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    KofScheduler.SchedulerCall sc = KofScheduler.staticCall(mc.methodName(), argTypes);
                    if (sc != null) yield sc.returnType();
                    // fall through
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofLog.isLogNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
                    if (logCall != null) yield logCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofOrm.isOrmNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    boolean typed = !mc.typeArguments().isEmpty();
                    String entityName = typed ? mc.typeArguments().get(0) : null;
                    KofOrm.OrmCall ormCall = KofOrm.staticCall(mc.methodName(), argTypes, typed, entityName);
                    if (ormCall != null) {
                        if ("save".equals(mc.methodName()) && !argTypes.isEmpty()) {
                            yield argTypes.get(argTypes.size() - 1);
                        }
                        if (typed && !mc.typeArguments().isEmpty()) {
                            if ("all".equals(mc.methodName()) || "where".equals(mc.methodName())
                                    || "page".equals(mc.methodName())) {
                                yield new Type.ClassType("kof", "List",
                                        List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
                            }
                            if ("find".equals(mc.methodName())) {
                                yield CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
                            }
                        }
                        yield ormCall.returnType();
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                        && driver.findLocalVar(rid.name(), locals) == null) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
                    if (procCall != null) yield procCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofConfig.isConfigNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                    KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
                    if (cfgCall != null) yield cfgCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofTetris.isTetrisNamespace(rid.name())) {
                    KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                            mc.arguments().size());
                    if (tetrisCall != null) yield tetrisCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid2 && KofIo.isConstructor(rid2.name())) {
                    KofIo.IoCall ioCall = KofIo.staticMethod(rid2.name(), mc.methodName(), mc.arguments().size());
                    if (ioCall != null) yield ioCall.returnType();
                }
                if (mc.receiver() != null) {
                    Type recvType = inferExprType(driver, mc.receiver(), locals);
                    // narrowing de null-safety: `if (x != null) { x.metodo() }`
                    if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
                    if (KofProcess.isHandle(recvType)) {
                        List<Type> hArgs = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) hArgs.add(inferExprType(driver, arg, locals));
                        KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(), hArgs);
                        if (hm != null) yield hm.returnType();
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && KofSecurity.isSecurityNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                        KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (secCall != null) yield secCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && KofValidation.isValidationNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                        KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (vCall != null) yield vCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && KofObservability.isObservabilityNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(driver, arg, locals));
                        KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (oCall != null) yield oCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (KofUi.isUiType(recvType)) {
                        KofUi.UiCall uiCall = KofUi.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
                        if (uiCall != null) yield uiCall.returnType();
                    }
                    if (KofWeb.isAppType(recvType)) {
                        List<Type> webArgTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) webArgTypes.add(inferExprType(driver, arg, locals));
                        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
                        if (webCall != null) yield webCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (KofMedia.isHandleType(recvType)) {
                        KofMedia.MediaCall mediaCall =
                                KofMedia.handleMethod(recvType, mc.methodName(), mc.arguments().size());
                        if (mediaCall != null) yield mediaCall.returnType();
                    }
                    if (KofIo.isIoType(recvType)) {
                        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
                        if (ioCall != null) yield ioCall.returnType();
                        if (KofIo.isIdentityMethod(mc.methodName())) yield recvType;
                    }
                    if (recvType instanceof Type.FunctionType ft) {
                        yield ft.returnType();
                    }
                    if (CompilerTypes.isEnumType(recvType, driver.currentUnit) && "name".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        yield BuiltinTypes.STRING;
                    }
                    if (BuiltinTypes.isList(recvType)) {
                        String mn = mc.methodName();
                        if (("map".equals(mn) || "filter".equals(mn) || "reduce".equals(mn))
                                && mc.arguments().stream().anyMatch(a -> a instanceof LambdaExpr)) {
                            Type lambdaT = null;
                            for (ExpressionNode arg : mc.arguments()) {
                                if (arg instanceof LambdaExpr lam) {
                                    lambdaT = inferExprType(driver, lam, locals);
                                    break;
                                }
                            }
                            if (lambdaT instanceof Type.FunctionType ft
                                    && !(ft.returnType() instanceof Type.UnknownType)) {
                                if ("map".equals(mn)) {
                                    yield new Type.ClassType("kof", "List",
                                            List.of(ft.returnType()));
                                }
                                if ("filter".equals(mn)) yield recvType;
                                if ("reduce".equals(mn)) yield ft.returnType();
                            }
                            yield Type.UnknownType.UNKNOWN;
                        }
                        if ("get".equals(mn) || "remove".equals(mn)) yield driver.listElementType(recvType);
                        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) yield Type.PrimitiveType.INT;
                        if ("contains".equals(mn) || "isEmpty".equals(mn)) yield Type.PrimitiveType.BOOL;
                        if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                                || "set".equals(mn) || "clear".equals(mn)) {
                            yield Type.PrimitiveType.VOID;
                        }
                    }
                    if (BuiltinTypes.isMap(recvType)) {
                        String mn = mc.methodName();
                        Type valueType = Type.UnknownType.UNKNOWN;
                        if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) valueType = ct.typeArguments().get(1);
                        Type keyType = Type.UnknownType.UNKNOWN;
                        if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) keyType = ct.typeArguments().get(0);
                        if ("get".equals(mn)) {
                            // mesmo contrato do emit: valores de referência devolvem V?
                            yield valueType instanceof Type.ClassType ct
                                    && !KofUi.isUiType(ct) && !KofMedia.isHandleType(ct)
                                    ? new Type.NullableType(valueType) : valueType;
                        }
                        if ("remove".equals(mn)) yield valueType;
                        if ("put".equals(mn)) yield valueType;
                        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) yield Type.PrimitiveType.INT;
                        if ("containsKey".equals(mn) || "contains".equals(mn) || "isEmpty".equals(mn)) yield Type.PrimitiveType.BOOL;
                        if ("clear".equals(mn)) yield Type.PrimitiveType.VOID;
                        if ("keys".equals(mn)) yield new Type.ClassType("kof", "List", List.of(keyType));
                        if ("values".equals(mn)) yield new Type.ClassType("kof", "List", List.of(valueType));
                    }
                    if (BuiltinTypes.isSet(recvType)) {
                        String mn = mc.methodName();
                        Type elemType = Type.UnknownType.UNKNOWN;
                        if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) elemType = ct.typeArguments().get(0);
                        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) yield Type.PrimitiveType.INT;
                        if ("contains".equals(mn) || "isEmpty".equals(mn)) yield Type.PrimitiveType.BOOL;
                        if ("add".equals(mn) || "remove".equals(mn)) yield Type.PrimitiveType.BOOL;
                        if ("clear".equals(mn)) yield Type.PrimitiveType.VOID;
                    }
                    if (Type.isString(recvType)) {
                        String mn = mc.methodName();
                        if ("charAt".equals(mn)) yield Type.PrimitiveType.CHAR;
                        if ("toInt".equals(mn)) yield Type.PrimitiveType.INT;
                        if ("toLong".equals(mn)) yield Type.PrimitiveType.LONG;
                        if ("toDouble".equals(mn)) yield Type.PrimitiveType.DOUBLE;
                        if ("toFloat".equals(mn)) yield Type.PrimitiveType.FLOAT;
                        if ("length".equals(mn) || "indexOf".equals(mn) || "lastIndexOf".equals(mn)
                                || "compareTo".equals(mn) || "compareToIgnoreCase".equals(mn)
                                || "hashCode".equals(mn) || "size".equals(mn) || "count".equals(mn)) {
                            yield Type.PrimitiveType.INT;
                        }
                        if ("contains".equals(mn) || "startsWith".equals(mn) || "endsWith".equals(mn)
                                || "equals".equals(mn) || "equalsIgnoreCase".equals(mn)) {
                            yield Type.PrimitiveType.BOOL;
                        }
                        if ("substring".equals(mn) || "concat".equals(mn) || "trim".equals(mn)
                                || "toUpperCase".equals(mn) || "toLowerCase".equals(mn)
                                || "replace".equals(mn) || "valueOf".equals(mn)) {
                            yield BuiltinTypes.STRING;
                        }
                        if ("split".equals(mn)) {
                            yield new Type.ArrayType(BuiltinTypes.STRING);
                        }
                    }
                } else if (driver.currentUnit != null) {
                    IRLocalVariable lambdaVar = driver.findLocalVar(mc.methodName(), locals);
                    if (lambdaVar != null && lambdaVar.type() instanceof Type.FunctionType lft) {
                        yield lft.returnType();
                    }
                    for (AstNode d : driver.currentUnit.declarations()) {
                        if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                            Type returnType = CompilerTypes.toType(fn.returnType(), driver.currentUnit);
                            if (fn.typeParameters().contains(fn.returnType())) {
                                returnType = new Type.TypeVariable(fn.returnType());
                            }
                            if (returnType instanceof Type.TypeVariable tv) {
                                for (int pi = 0; pi < fn.parameters().size(); pi++) {
                                    if (pi < mc.arguments().size() && tv.name().equals(fn.parameters().get(pi).type())) {
                                        yield inferExprType(driver, mc.arguments().get(pi), locals);
                                    }
                                }
                                yield Type.UnknownType.UNKNOWN;
                            }
                            yield returnType;
                        }
                    }
                }
                SymbolTable.MethodSymbol resolvedMethod = driver.semanticAnalyzer.getResolvedMethod(mc);
                if (resolvedMethod != null) {
                    Type rt = resolvedMethod.returnType();
                    if (rt instanceof Type.TypeVariable tv && mc.receiver() != null) {
                        Type recvT = inferExprType(driver, mc.receiver(), locals);
                        Type subst = CompilerTypes.substituteTypeVariable(tv.name(), recvT, driver.currentUnit);
                        if (subst != null) yield subst;
                    }
                    yield rt;
                }
                if (mc.receiver() != null) {
                    Type recvT = inferExprType(driver, mc.receiver(), locals);
                    if (recvT instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
                        SymbolTable.Symbol m = driver.semanticAnalyzer.resolveInHierarchy(ct.name(), mc.methodName());
                        if (m instanceof SymbolTable.MethodSymbol ms) {
                            Type rt = ms.returnType();
                            if (rt instanceof Type.TypeVariable tv) {
                                Type subst = CompilerTypes.substituteTypeVariable(tv.name(), recvT, driver.currentUnit);
                                if (subst != null) yield subst;
                            }
                            yield rt;
                        }
                    }
                    if (recvT instanceof Type.ClassType) {
                        StringMethodRegistry.Sig osig = StringMethodRegistry.objectMethodSignature(mc.methodName(), mc.arguments().size());
                        if (osig != null) yield osig.returnType();
                    }
                }
                SymbolTable.ClassSymbol cs = driver.semanticAnalyzer != null ? driver.semanticAnalyzer.getClass(mc.methodName()) : null;
                if (cs != null) yield cs.type();
                yield Type.UnknownType.UNKNOWN;
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
                if (recvType instanceof Type.ArrayType at && "length".equals(fa.fieldName())) {
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
                Type returnType = Type.UnknownType.UNKNOWN;
                for (StatementNode s : le.body()) {
                    if (s instanceof ReturnStmt rs && rs.value() != null) {
                        returnType = inferExprType(driver, rs.value(), extended);
                        break;
                    }
                }
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
            }
            case SwitchExpr se -> {
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
     * True quando a cadeia de superclasses a partir de internalName é
     * inteiramente conhecida pelo SemanticAnalyzer (nenhuma classe externa
     * no caminho). Só nesse caso "método não resolvido" prova inexistência.
     */
}