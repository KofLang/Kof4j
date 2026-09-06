package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de expressões (emitExpression) do CompilerDriver. Recebe o driver como host.
 */
final class ExpressionLowerer {

    private ExpressionLowerer() {}

    static int emitExpression(CompilerDriver driver, ExpressionNode expr, List<KofOperation> ops, String owner, int localIdx,
                               List<IRLocalVariable> locals) {
        return switch (expr) {
            case LiteralExpr lit -> {
                switch (lit.kind()) {
                    case ConcreteLiteralKind.INT -> ops.add(KofLoadLiteral.ofInt(driver.parseIntLiteral(lit.value())));
                    case ConcreteLiteralKind.LONG -> ops.add(KofLoadLiteral.ofLong(Long.parseLong(driver.stripSuffix(lit.value()))));
                    case ConcreteLiteralKind.FLOAT -> ops.add(KofLoadLiteral.ofFloat(Float.parseFloat(driver.stripSuffix(lit.value()))));
                    case ConcreteLiteralKind.DOUBLE -> ops.add(KofLoadLiteral.ofDouble(Double.parseDouble(driver.stripSuffix(lit.value()))));
                    case ConcreteLiteralKind.STRING -> ops.add(KofLoadLiteral.ofString(lit.value()));
                    case ConcreteLiteralKind.BOOLEAN -> ops.add(KofLoadLiteral.ofBool(Boolean.parseBoolean(lit.value())));
                    case ConcreteLiteralKind.CHAR -> ops.add(KofLoadLiteral.ofInt(lit.value().charAt(0)));
                    case ConcreteLiteralKind.NULL -> ops.add(KofLoadLiteral.ofNull());
                }
                yield localIdx;
            }
            case IdentifierExpr ie -> {
                if (driver.loweringMain && "args".equals(ie.name())) {
                    if (driver.mainArgsListField) {
                        // args: List<String> — the converted list lives in
                        // slot 1 (set by the main prologue)
                        ops.add(new KofLoadLocal(KofProcess.STRING_LIST, 1));
                    } else if (driver.target == Target.JVM) {
                        ops.add(new KofLoadLocal(new Type.ArrayType(BuiltinTypes.STRING), 0));
                    } else {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        ops.add(new KofNewArray(BuiltinTypes.STRING));
                    }
                    yield localIdx;
                }
                // constante de enum não-qualificada → literal String tipado
                if (driver.currentUnit != null && driver.findLocalVar(ie.name(), locals) == null
                        && (driver.semanticAnalyzer == null || !driver.semanticAnalyzer.allClasses().containsKey(ie.name()))) {
                    for (AstNode d0 : driver.currentUnit.declarations()) {
                        if (d0 instanceof EnumDeclarationNode en0
                                && en0.constants().contains(ie.name())) {
                            ops.add(new KofLoadLiteral(new Type.ClassType("", en0.name(), List.of()),
                                    ie.name()));
                            yield localIdx;
                        }
                    }
                }
                for (int i = locals.size() - 1; i >= 0; i--) {
                    if (locals.get(i).name().equals(ie.name())) {
                        IRLocalVariable lv = locals.get(i);
                        if (driver.boxFactory.isBoxType(lv.type())) {
                            ops.add(new KofLoadLocal(lv.type(), lv.index()));
                            ops.add(new KofLoadField(lv.type(), "value",
                                    driver.boxFactory.boxValueType(lv.type())));
                        } else {
                            ops.add(new KofLoadLocal(lv.type(), lv.index()));
                        }
                        yield localIdx;
                    }
                }
                if (!owner.isEmpty() && driver.semanticAnalyzer != null) {
                    String className = owner.substring(owner.lastIndexOf('/') + 1);
                    SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(className);
                    if (cs == null) {
                        for (var entry : driver.semanticAnalyzer.allClasses().entrySet()) {
                            if (entry.getValue().internalName().equals(owner)) { cs = entry.getValue(); break; }
                        }
                    }
                    if (cs != null) {
                        SymbolTable.Symbol fieldSym = HierarchyResolver.resolveFieldInHierarchy(cs.name(), ie.name(), driver.semanticAnalyzer);
                        if (fieldSym instanceof SymbolTable.FieldSymbol fs) {
                            ops.add(new KofLoadLocal(cs.type(), 0));
                            ops.add(new KofLoadField(cs.type(), ie.name(), fs.type()));
                            yield localIdx;
                        } else if (fieldSym instanceof SymbolTable.MethodSymbol ms
                                && ms.parameterTypes().isEmpty()) {
                            // Record/class-with-primary-constructor: the
                            // accessor method (kind()) shares the component
                            // field name (kind); a bare identifier refers to
                            // the field, not the accessor call.
                            ops.add(new KofLoadLocal(cs.type(), 0));
                            ops.add(new KofLoadField(cs.type(), ie.name(), ms.returnType()));
                            yield localIdx;
                        }
                    }
                }
                // Nome de TIPO builtin (String/Int/Long/…) como receiver de
                // método estático: não existe valor para empilhar — o KofCall
                // STATIC abaixo não consome receiver. Empilhar algo aqui
                // (o fallback aload_0 de antes) desalinha a pilha do call
                // (frame crash / VerifyError).
                if (driver.isBuiltinStaticReceiver(ie.name(), locals)) {
                    yield localIdx;
                }
                ops.add(new KofLoadLocal(Type.UnknownType.UNKNOWN, 0));
                yield localIdx;
            }
            case BinaryExpr bin -> {
                yield ExpressionBinaryLowerer.lower(driver, bin, ops, owner, localIdx, locals);
            }
            case UnaryExpr ue -> {
                Type operandType = ExpressionTyper.inferExprType(driver, ue.operand(), locals);
                if ("++".equals(ue.operator()) || "--".equals(ue.operator())) {
                    localIdx = driver.emitIncrement(ue, operandType, ops, owner, localIdx, locals);
                    yield localIdx;
                }
                localIdx = ExpressionLowerer.emitExpression(driver, ue.operand(), ops, owner, localIdx, locals);
                if ("-".equals(ue.operator())) {
                    ops.add(new KofUnary(KofUnaryOp.NEG, operandType));
                } else if ("!".equals(ue.operator())) {
                    ops.add(new KofUnary(KofUnaryOp.NOT, operandType));
                }
                yield localIdx;
            }
            case MethodCallExpr mc -> {
                yield ExpressionMethodCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
            }
            case AssignmentExpr ae -> {
                yield ExpressionAssignmentLowerer.lower(driver, ae, ops, owner, localIdx, locals);
            }
            case NewExpr ne -> {
                Type type = CompilerTypes.toType(ne.typeName(), driver.currentUnit);
                if ("List".equals(ne.typeName()) || "ArrayList".equals(ne.typeName())) {
                    type = BuiltinTypes.LIST;
                }
                if (!ne.typeArguments().isEmpty() && type instanceof Type.ClassType cts) {
                    type = new Type.ClassType(cts.packageName(), cts.name(),
                            ne.typeArguments().stream().map(n -> CompilerTypes.toType(n, driver.currentUnit)).toList());
                }
                if (BuiltinTypes.isList(type)) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : ne.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    ops.add(new KofCall(BuiltinTypes.LIST, "kof_list_new", argTypes, BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : ne.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                SymbolTable.ConstructorSymbol resolvedCtor = driver.semanticAnalyzer.getResolvedConstructor(ne);
                if (resolvedCtor == null && type instanceof Type.ClassType ct
                        && driver.semanticAnalyzer != null) {
                    // fallback: resolver por assignability quando o registro
                    // por identidade falhou (ex.: node recriado no desugar)
                    SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(ct.name());
                    if (cs != null) {
                        SymbolTable.Symbol anyInit = cs.members().resolve("<init>");
                        if (anyInit instanceof SymbolTable.ConstructorSet set) {
                            for (SymbolTable.ConstructorSymbol c : set.constructors()) {
                                if (c.parameterTypes().size() == argTypes.size()) {
                                    boolean compatible = true;
                                    for (int ai = 0; ai < argTypes.size(); ai++) {
                                        if (!driver.ctorCompatible(c.parameterTypes().get(ai), argTypes.get(ai))) {
                                            compatible = false;
                                            break;
                                        }
                                    }
                                    if (compatible) { resolvedCtor = c; break; }
                                }
                            }
                        }
                    }
                }
                if (resolvedCtor == null && type instanceof Type.ClassType ct
                        && driver.semanticAnalyzer != null) {
                    SymbolTable.ClassSymbol cs2 = driver.semanticAnalyzer.getClass(ct.name());
                    if (cs2 != null) {
                        SymbolTable.Symbol anyInit2 = cs2.members().resolve("<init>");
                        if (anyInit2 instanceof SymbolTable.ConstructorSet set2) {
                            for (SymbolTable.ConstructorSymbol c : set2.constructors()) {
                                if (c.parameterTypes().size() == argTypes.size()) {
                                    resolvedCtor = c;
                                    break;
                                }
                            }
                        }
                    }
                }
                ops.add(new KofNewObject(type, argTypes));
                ops.add(new KofDup());
                List<Type> ctorParamTypes;
                if (resolvedCtor != null
                        && resolvedCtor.parameterTypes().size() == ne.arguments().size()) {
                    ctorParamTypes = resolvedCtor.parameterTypes();
                } else if (type instanceof Type.ClassType ct && !ct.packageName().isEmpty()
                        && driver.externalClasspath != null
                        && driver.externalClasspath.knows(ct.internalName())) {
                    // construtor de classe externa: descritor exato do classpath
                    ExternalClasspath.MethodSignature extCtor =
                            driver.externalClasspath.resolveConstructor(ct.internalName(), ne.arguments().size());
                    if (extCtor != null) {
                        List<Type> formal = new ArrayList<>();
                        for (String d : extCtor.parameterDescriptors()) {
                            formal.add(ExternalClasspath.typeFromDescriptor(d));
                        }
                        ctorParamTypes = formal;
                    } else {
                        ctorParamTypes = argTypes;
                    }
                } else {
                    ctorParamTypes = argTypes;
                }
                localIdx = driver.emitArgumentsWithFormalTypes(ne.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                ops.add(new KofCall(type, "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                yield localIdx;
            }
            case NewArrayExpr na -> {
                Type elemType = CompilerTypes.toType(na.elementType(), driver.currentUnit);
                localIdx = ExpressionLowerer.emitExpression(driver, na.size(), ops, owner, localIdx, locals);
                ops.add(new KofNewArray(elemType));
                yield localIdx;
            }
            case ArrayAccessExpr aa -> {
                localIdx = ExpressionLowerer.emitExpression(driver, aa.receiver(), ops, owner, localIdx, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, aa.index(), ops, owner, localIdx, locals);
                Type recvType = ExpressionTyper.inferExprType(driver, aa.receiver(), locals);
                Type elemType = Type.arrayElementType(recvType);
                ops.add(new KofArrayLoad(elemType));
                yield localIdx;
            }
            case FieldAccessExpr fa -> {
                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())) {
                    Integer color = KofUi.paletteColor(fa.fieldName());
                    if (color != null) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, color));
                        yield localIdx;
                    }
                }
                if (fa.receiver() instanceof IdentifierExpr sid2 && "super".equals(sid2.name())
                        && !owner.isEmpty() && driver.semanticAnalyzer != null) {
                    // super.campo: GETFIELD com owner na superclasse
                    String superInternal = HierarchyResolver.findSuperClass(owner, driver.semanticAnalyzer);
                    if (superInternal == null) superInternal = "java/lang/Object";
                    superInternal = superInternal.replace('.', '/');
                    Type superType = CompilerTypes.ownerTypeFromInternal(superInternal, driver.semanticAnalyzer);
                    String superSimple = superInternal.substring(superInternal.lastIndexOf('/') + 1);
                    SymbolTable.Symbol fieldSym = driver.semanticAnalyzer.resolveInHierarchy(superSimple, fa.fieldName());
                    Type fieldType = fieldSym != null ? fieldSym.type() : ExpressionTyper.inferExprType(driver, fa, locals);
                    ops.add(new KofLoadLocal(CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer), 0));
                    ops.add(new KofLoadField(superType, fa.fieldName(), fieldType));
                    yield localIdx;
                }
                {
                    // campo de classe EXTERNA: owner e tipo vêm do classpath
                    Type extRecv = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
                    if (extRecv instanceof Type.ClassType ect && !ect.packageName().isEmpty()
                            && driver.externalClasspath != null
                            && driver.externalClasspath.knows(ect.internalName())) {
                        String desc = driver.externalClasspath.resolveFieldType(ect.internalName(), fa.fieldName());
                        if (desc != null) {
                            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                            ops.add(new KofLoadField(ect, fa.fieldName(),
                                    ExternalClasspath.typeFromDescriptor(desc)));
                            yield localIdx;
                        }
                    }
                }
                Type faType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
                if (KofProcess.isResult(faType) && KofProcess.isField(fa.fieldName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadField(KofProcess.RESULT, fa.fieldName(),
                            KofProcess.fieldType(fa.fieldName())));
                    yield localIdx;
                }
                if (KofUi.isWindow(faType) && "title".equals(fa.fieldName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_window_title", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isLabel(faType) && "text".equals(fa.fieldName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_label_text", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isLabel(faType) && "fontSize".equals(fa.fieldName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_label_font_size", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isLabel(faType) && "bold".equals(fa.fieldName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_label_bold", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isLabel(faType) && "color".equals(fa.fieldName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_label_color", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isButton(faType) && "text".equals(fa.fieldName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_button_text", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isInput(faType) && "text".equals(fa.fieldName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_input_text", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isComponent(faType) && "state".equals(fa.fieldName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_component_state_get", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                Type recvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
                // narrowing de null-safety (`if (x != null) { x.length }`): o tipo do
                // receptor é o inner — antes emitia `getfield "?".length` para String?
                // (owner "?" inválido → erro de launcher/verificação no JVM).
                if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
                if (BuiltinTypes.isList(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(recvType, "kof_list_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
                    yield localIdx;
                }
                // Map/Set `.size` propriedade (bug 14): antes caía no field-access
                // genérico → getfield HashMap.size → NoSuchFieldError em runtime.
                if (BuiltinTypes.isMap(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(recvType, "kof_map_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
                    yield localIdx;
                }
                if (BuiltinTypes.isSet(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(recvType, "kof_set_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
                    yield localIdx;
                }
                if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    yield localIdx;
                }
                // enum constant access: Color.Red — literal String tipado como Color
                if (recvType instanceof Type.ClassType ct && ct.packageName().isEmpty()
                        && CompilerTypes.isEnumName(ct.name(), driver.currentUnit)) {
                    if (!CompilerTypes.enumConstantsOf(ct.name(), driver.currentUnit).contains(fa.fieldName())) {
                        if (driver.currentDiagnostics != null) {
                            driver.currentDiagnostics.error(fa.position() != null ? fa.position().file() : "",
                                    fa.position() != null ? fa.position().line() : 0,
                                    fa.position() != null ? fa.position().column() : 0, 0,
                                    "enum '" + ct.name() + "' não tem constante '" + fa.fieldName() + "'",
                                    "SEM030");
                        }
                        yield localIdx;
                    }
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, fa.fieldName()));
                    yield localIdx;
                }
                // static field access: Class.field — no receiver on the stack
                if (recvType instanceof Type.ClassType ct && ct.packageName().isEmpty()
                        && CompilerTypes.isEnumName(ct.name(), driver.currentUnit) && CompilerTypes.enumConstantsOf(ct.name(), driver.currentUnit).contains(fa.fieldName())) {
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, fa.fieldName()));
                    yield localIdx;
                }
                if (recvType instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
                    SymbolTable.Symbol staticSym = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
                    if (staticSym instanceof SymbolTable.FieldSymbol fs
                            && (fs.accessFlags() & AccessFlags.STATIC) != 0) {
                        ops.add(new KofGetStatic(recvType, fa.fieldName(), fs.type()));
                        yield localIdx;
                    }
                }
                localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                if (recvType instanceof Type.ArrayType && "length".equals(fa.fieldName())) {
                    ops.add(new KofArrayLength());
                } else if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    ops.add(new KofLoadField(recvType, fa.fieldName(), Type.PrimitiveType.INT));
                } else {
                    Type fieldType = Type.UnknownType.UNKNOWN;
                    SymbolTable.Symbol accessor = null;
                    if (recvType instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
                        accessor = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
                        if (accessor != null) fieldType = accessor.type();
                    }
                    if (accessor instanceof SymbolTable.MethodSymbol ms && ms.parameterTypes().isEmpty()) {
                        ops.add(new KofCall(recvType, fa.fieldName(), List.of(), ms.returnType(), KofCallKind.INSTANCE));
                    } else {
                        ops.add(new KofLoadField(recvType, fa.fieldName(), fieldType));
                    }
                }
                yield localIdx;
            }
            case IfExpr ie -> {
                LabelId thenLabel = LabelId.create();
                LabelId elseLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                if (ie.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                    localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()), driver.comparisonOperandType(bin, locals), thenLabel, elseLabel));
                } else {
                    localIdx = ExpressionLowerer.emitExpression(driver, ie.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.NE, thenLabel, elseLabel));
                }
                ops.add(new KofLabel(thenLabel));
                localIdx = ExpressionLowerer.emitExpression(driver, ie.thenExpr(), ops, owner, localIdx, locals);
                ops.add(new KofJump(endLabel));
                ops.add(new KofLabel(elseLabel));
                localIdx = ExpressionLowerer.emitExpression(driver, ie.elseExpr(), ops, owner, localIdx, locals);
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case SwitchExpr se -> {
                localIdx = SwitchExprLowerer.emitSwitchExpr(driver, se, ops, owner, localIdx, locals);
                yield localIdx;
            }
            case LambdaExpr le -> {
                Type.FunctionType ft = (Type.FunctionType) ExpressionTyper.inferExprType(driver, le, locals);
                List<IRLocalVariable> captures = driver.collectCaptures(le, locals);
                String lambdaClass = driver.lambdaClass(le, ft, captures);
                List<IRLocalVariable> effective = driver.lambdaEffectiveCaptures.get(le);
                if (effective != null) captures = effective;
                if (ft.className() == null) {
                    ft = new Type.FunctionType(ft.parameterTypes(), ft.returnType(), lambdaClass);
                }
                Type lambdaType = new Type.ClassType("", lambdaClass, List.of());
                List<Type> captureTypes = new ArrayList<>();
                for (IRLocalVariable cap : captures) captureTypes.add(cap.type());
                ops.add(new KofNewObject(lambdaType, captureTypes));
                ops.add(new KofDup());
                for (IRLocalVariable cap : captures) {
                    ops.add(new KofLoadLocal(cap.type(), cap.index()));
                }
                ops.add(new KofCall(lambdaType, "<init>", captureTypes,
                        Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                yield localIdx;
            }
            case QueryDslExpr q -> {
                yield driver.lowerQueryDsl(q, ops, owner, localIdx, locals);
            }
            default -> localIdx;
        };
    }
}