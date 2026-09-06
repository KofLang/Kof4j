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
                if ("instanceof".equals(bin.operator()) || "as".equals(bin.operator())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, bin.left(), ops, owner, localIdx, locals);
                    Type targetType = Type.UnknownType.UNKNOWN;
                    if (bin.right() instanceof IdentifierExpr ie) {
                        // toType resolve imports ("View" + import → android.view.View)
                        targetType = CompilerTypes.toType(ie.name(), driver.currentUnit);
                    }
                    if ("instanceof".equals(bin.operator())) {
                        ops.add(new KofInstanceOf(targetType));
                    } else if (TypeMetrics.isPrimitiveType(targetType) && TypeMetrics.isPrimitiveType(ExpressionTyper.inferExprType(driver, bin.left(), locals))) {
                        // cast primitivo (x as Char/Int/…): conversão numérica,
                        // NÃO checkcast (que exigiria um objeto na pilha)
                        Type fromT = ExpressionTyper.inferExprType(driver, bin.left(), locals);
                        driver.emitWideningIfNeeded(ops, fromT, targetType);
                        if (targetType instanceof Type.PrimitiveType tp2
                                && ("char".equals(tp2.name()) || "Char".equals(tp2.name()))) {
                            ops.add(new KofUnary(KofUnaryOp.I2C, fromT));
                        }
                        // narrowing numérico (cast explícito): L2I, F2I, D2I,
                        // F2L, D2L — sem isso FP→Int gerava bytecode inválido
                        // (bug 5) e Long→Int via wid().não cobria
                        driver.emitPrimNarrow(ops, fromT, targetType);
                    } else {
                        ops.add(new KofCheckCast(targetType));
                        // o resultado do cast tem o tipo alvo — o próximo
                        // acesso (campo/método) precisa enxergá-lo
                        if (bin.left() instanceof IdentifierExpr lie && !Type.isUnknown(targetType)) {
                            for (int li = locals.size() - 1; li >= 0; li--) {
                                if (locals.get(li).name().equals(lie.name())) {
                                    locals.set(li, new IRLocalVariable(locals.get(li).index(),
                                            lie.name(), targetType));
                                    break;
                                }
                            }
                        }
                    }
                    yield localIdx;
                }
                // Short-circuit evaluation for || and &&:
                // a || b → eval a; if true, jump to true_label; eval b; result = b
                // a && b → eval a; if false, jump to false_label; eval b; result = b
                if (("||".equals(bin.operator()) || "&&".equals(bin.operator()))
                        && driver.target != Target.JS) {
                    LabelId trueLabel = LabelId.create();
                    LabelId falseLabel = LabelId.create();
                    LabelId endLabel = LabelId.create();
                    localIdx = ExpressionLowerer.emitExpression(driver, bin.left(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    if ("||".equals(bin.operator())) {
                        ops.add(new KofConditionalJump(KofComparison.NE, trueLabel, falseLabel));
                    } else {
                        ops.add(new KofConditionalJump(KofComparison.NE, falseLabel, trueLabel));
                    }
                    ops.add(new KofLabel(falseLabel));
                    localIdx = ExpressionLowerer.emitExpression(driver, bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofJump(endLabel));
                    ops.add(new KofLabel(trueLabel));
                    if ("||".equals(bin.operator())) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    } else {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    }
                    ops.add(new KofLabel(endLabel));
                    yield localIdx;
                }
                // Left-associative chains (huge string concatenations in
                // generated UIs, editors) are emitted iteratively instead of
                // recursing: deep chains would overflow the compiler stack.
                // `as`/`instanceof` NÃO são associativos à esquerda — parar o
                // flattening neles (bug 13: `(x as Int) + 1` crashava porque o
                // `as` caía no default ADD do loop).
                java.util.List<BinaryExpr> chain = new ArrayList<>();
                ExpressionNode cursor = bin;
                while (cursor instanceof BinaryExpr be
                        && !"as".equals(be.operator())
                        && !"instanceof".equals(be.operator())) {
                    chain.add(be);
                    cursor = be.left();
                }
                localIdx = ExpressionLowerer.emitExpression(driver, cursor, ops, owner, localIdx, locals);
                Type accType = ExpressionTyper.inferExprType(driver, cursor, locals);
                for (int ci = chain.size() - 1; ci >= 0; ci--) {
                    BinaryExpr be = chain.get(ci);
                    Type rightType = ExpressionTyper.inferExprType(driver, be.right(), locals);
                    boolean isArithmetic = switch (be.operator()) {
                        case "+", "-", "*", "/", "%" -> true;
                        default -> false;
                    };
                    boolean isNumericComparison = TypeMetrics.isComparisonOp(be.operator())
                            && TypeMetrics.isNumeric(accType) && TypeMetrics.isNumeric(rightType);
                    if ((isArithmetic || isNumericComparison)
                            && TypeMetrics.isNumeric(accType) && TypeMetrics.isNumeric(rightType)) {
                        // OBS-009: divisão (ou resto) por zero constante é
                        // detectada em compile-time — o compilador conhece a
                        // intenção; o usuário não vê o ArithmeticException do
                        // JVM.
                        boolean integerArithmetic = Type.isInteger(accType) && Type.isInteger(rightType);
                        if (integerArithmetic && ("/".equals(be.operator()) || "%".equals(be.operator()))
                                && be.right() instanceof LiteralExpr lit
                                && driver.isZeroLiteral(lit)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(be.position() != null ? be.position().file() : "",
                                        be.position() != null ? be.position().line() : 0,
                                        be.position() != null ? be.position().column() : 0,
                                        0,
                                        "division by zero: constant " + be.operator()
                                                + " by zero is not allowed",
                                        "ARITH001");
                            }
                            yield localIdx;
                        }
                        Type commonType = TypeMetrics.commonNumericType(accType, rightType);
                        if (!driver.fpSupportedOnNative(commonType, be.position())) {
                            yield localIdx;
                        }
                        driver.emitWideningIfNeeded(ops, accType, commonType);
                        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
                        driver.emitWideningIfNeeded(ops, rightType, commonType);
                        ops.add(new KofBinary(TypeMetrics.mapArithmeticOp(be.operator()), commonType));
                        accType = commonType;
                    } else if ("+".equals(be.operator())
                            && (Type.isString(accType) || Type.isString(rightType))) {
                        // concatenação com float/double no Native formataria
                        // os bits como inteiro — diagnóstico em vez de lixo.
                        // SÓ pula quando o driver.target não suporta FP (agora os 3
                        // suportam — FLT001 fechado; o yield incondicional
                        // descartava o operando: "a=" + 1.5 virava só "a=").
                        if (((Type.isString(accType) && TypeMetrics.isFloatingPoint(rightType))
                                || (Type.isString(rightType) && TypeMetrics.isFloatingPoint(accType)))
                                && !driver.fpSupportedOnNative(TypeMetrics.isFloatingPoint(rightType) ? rightType : accType,
                                        be.position())) {
                            yield localIdx;
                        }
                        if (!Type.isString(accType) && TypeMetrics.isPrimitiveType(accType)) TypeEmitter.boxPrimitive(ops, accType);
                        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                List.of(driver.target.isNative() && !Type.isString(accType)
                                        && !(accType instanceof Type.PrimitiveType)
                                        ? accType : Type.UnknownType.UNKNOWN),
                                BuiltinTypes.STRING, KofCallKind.STATIC));
                        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
                        if (!Type.isString(rightType) && TypeMetrics.isPrimitiveType(rightType)) TypeEmitter.boxPrimitive(ops, rightType);
                        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                List.of(driver.target.isNative() && !Type.isString(rightType)
                                        && !(rightType instanceof Type.PrimitiveType)
                                        ? rightType : Type.UnknownType.UNKNOWN),
                                BuiltinTypes.STRING, KofCallKind.STATIC));
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                BuiltinTypes.STRING, KofCallKind.FUNCTION));
                        accType = BuiltinTypes.STRING;
                    } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                            && ((be.right() instanceof LiteralExpr rl
                                    && rl.kind() == ConcreteLiteralKind.NULL
                                    && TypeMetrics.isPrimitiveType(accType))
                                || (be.left() instanceof LiteralExpr ll
                                    && ll.kind() == ConcreteLiteralKind.NULL
                                    && TypeMetrics.isPrimitiveType(rightType)))) {
                        // primitivo nunca é null: == → false, != → true
                        // (o lado não-nulo já está na pilha — descarta)
                        ops.add(new KofPop());
                        boolean eq = "==".equals(be.operator());
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.BOOL, eq ? 0 : 1));
                        accType = Type.PrimitiveType.BOOL;
                    } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                            && (CompilerTypes.isRecordType(accType, driver.currentUnit, driver.semanticAnalyzer) || CompilerTypes.isRecordType(rightType, driver.currentUnit, driver.semanticAnalyzer))) {
                        // bug 11: `==` em records é igualdade de CONTEÚDO →
                        // left.equals(right) (o record gera equals no JVM e no
                        // JS). Antes emitia referência (if_acmpeq) → false.
                        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
                        Type recordType = CompilerTypes.isRecordType(accType, driver.currentUnit, driver.semanticAnalyzer) ? accType : rightType;
                        Type objT = new Type.ClassType("java.lang", "Object", List.of());
                        ops.add(new KofCall(recordType, "equals", List.of(objT),
                                Type.PrimitiveType.BOOL, KofCallKind.INSTANCE));
                        if ("!=".equals(be.operator())) {
                            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                            ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                        }
                        accType = Type.PrimitiveType.BOOL;
                    } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                            && (Type.isString(accType) || Type.isString(rightType)
                                || CompilerTypes.isEnumType(accType, driver.currentUnit) || CompilerTypes.isEnumType(rightType, driver.currentUnit))) {
                        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
                        if ("!=".equals(be.operator())) {
                            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                            ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                        }
                        accType = Type.PrimitiveType.BOOL;
                    } else {
                        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
                        Type operandType = accType;
                        // comparação contra null é referência (if_acmp*):
                        // usa o tipo do lado não-null, ou Object se Unknown
                        if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                                && (driver.isNullLiteral(be.left()) || driver.isNullLiteral(be.right()))) {
                            Type other = driver.isNullLiteral(be.left()) ? rightType : accType;
                            operandType = (other instanceof Type.ClassType || other instanceof Type.ArrayType
                                    || other instanceof Type.TypeVariable || other instanceof Type.NullableType)
                                    ? other : new Type.ClassType("java.lang", "Object", List.of());
                        }
                        switch (be.operator()) {
                            case "+" -> ops.add(new KofBinary(KofBinaryOp.ADD, operandType));
                            case "-" -> ops.add(new KofBinary(KofBinaryOp.SUB, operandType));
                            case "*" -> ops.add(new KofBinary(KofBinaryOp.MUL, operandType));
                            case "/" -> ops.add(new KofBinary(KofBinaryOp.DIV, operandType));
                            case "%" -> ops.add(new KofBinary(KofBinaryOp.MOD, operandType));
                            case "==" -> ops.add(new KofBinary(KofBinaryOp.EQ, operandType));
                            case "!=" -> ops.add(new KofBinary(KofBinaryOp.NE, operandType));
                            case "<" -> ops.add(new KofBinary(KofBinaryOp.LT, operandType));
                            case "<=" -> ops.add(new KofBinary(KofBinaryOp.LE, operandType));
                            case ">" -> ops.add(new KofBinary(KofBinaryOp.GT, operandType));
                            case ">=" -> ops.add(new KofBinary(KofBinaryOp.GE, operandType));
                            case "&&" -> ops.add(new KofBinary(KofBinaryOp.AND, operandType));
                            case "||" -> ops.add(new KofBinary(KofBinaryOp.OR, operandType));
                            case "&" -> ops.add(new KofBinary(KofBinaryOp.AND, operandType));
                            case "|" -> ops.add(new KofBinary(KofBinaryOp.OR, operandType));
                            case "^" -> ops.add(new KofBinary(KofBinaryOp.XOR, operandType));
                            case "<<" -> ops.add(new KofBinary(KofBinaryOp.SHL, operandType));
                            case ">>" -> ops.add(new KofBinary(KofBinaryOp.SHR, operandType));
                            case ">>>" -> ops.add(new KofBinary(KofBinaryOp.USHR, operandType));
                            default -> ops.add(new KofBinary(KofBinaryOp.ADD, operandType));
                        }
                        accType = switch (be.operator()) {
                            case "==", "!=", "<", "<=", ">", ">=" -> Type.PrimitiveType.BOOL;
                            default -> accType;
                        };
                    }
                }
                yield localIdx;
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
                // User-defined classes take precedence over builtin helpers
                // with the same name: ClassName(args) is implicit construction.
                SymbolTable.ClassSymbol userCtor = driver.semanticAnalyzer != null
                        ? driver.semanticAnalyzer.getClass(mc.methodName()) : null;
                if (mc.receiver() == null && userCtor != null) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    SymbolTable.ConstructorSymbol ctor = null;
                    SymbolTable.Symbol ctorSym = userCtor.members().resolve("<init>");
                    if (ctorSym instanceof SymbolTable.ConstructorSymbol ctorSingle) ctor = ctorSingle;
                    List<Type> ctorParamTypes = (ctor != null
                            && ctor.parameterTypes().size() == mc.arguments().size())
                            ? ctor.parameterTypes() : null;
                    if (ctorParamTypes == null && ctorSym instanceof SymbolTable.ConstructorSet set) {
                        // resolve por assignability: arg pode ser subtipo do
                        // formal (ex.: FixedClock onde TimeSource esperado)
                        for (SymbolTable.ConstructorSymbol c : set.constructors()) {
                            if (c.parameterTypes().size() != argTypes.size()) continue;
                            boolean compatible = true;
                            for (int ai = 0; ai < argTypes.size(); ai++) {
                                Type formalP = c.parameterTypes().get(ai);
                                Type argP = argTypes.get(ai);
                                if (!(formalP.equals(argP) || Type.isUnknown(argP)
                                        || (formalP instanceof Type.ClassType
                                            && argP instanceof Type.ClassType))) {
                                    compatible = false;
                                    break;
                                }
                            }
                            if (compatible) { ctorParamTypes = c.parameterTypes(); break; }
                        }
                        if (ctorParamTypes == null) {
                            for (SymbolTable.ConstructorSymbol c2 : set.constructors()) {
                                if (c2.parameterTypes().size() == argTypes.size()) {
                                    ctorParamTypes = c2.parameterTypes();
                                    break;
                                }
                            }
                        }
                    }
                    if (ctorParamTypes == null) ctorParamTypes = argTypes;
                    ops.add(new KofNewObject(userCtor.type(), argTypes));
                    ops.add(new KofDup());
                    localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                    ops.add(new KofCall(userCtor.type(), "<init>", ctorParamTypes,
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                    yield localIdx;
                }
                if (mc.receiver() == null && "now".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    ops.add(new KofCall(new Type.ClassType("kof", "time", List.of()), "kof_now",
                            List.of(), Type.PrimitiveType.LONG, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "uiNodesLive".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    // kof.ui probe (testes de leak): nº de nós vivos na árvore.
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_nodes_live", List.of(), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "storesLive".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_stores_live", List.of(), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "emit".equals(mc.methodName()) && mc.arguments().size() == 2) {
                    // Fase 5: dispara um evento num componente (bubbling).
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_emit", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                            Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "readLine".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    ops.add(new KofCall(new Type.ClassType("kof", "io", List.of()), "kof_read_line",
                            List.of(), new Type.NullableType(BuiltinTypes.STRING), KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && KofWeb.isContextFunction(mc.methodName())) {
                    KofWeb.WebCall webCtx = KofWeb.contextCall(mc.methodName(), mc.arguments().size());
                    if (webCtx != null) {
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofWeb.APP, webCtx.function(), webCtx.parameterTypes(),
                                webCtx.returnType(), KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                }
                if (mc.receiver() == null && "transaction".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    if (!KofDb.supportedOn(driver.target)) {
                        if (driver.currentDiagnostics != null) {
                            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                    mc.position() != null ? mc.position().line() : 0,
                                    mc.position() != null ? mc.position().column() : 0,
                                    0,
                                    "transaction: not available on the " + driver.target
                                            + " driver.target yet (" + KofDb.gapCode() + ")",
                                    KofDb.gapCode());
                        }
                        yield localIdx;
                    }
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.db", "Db", List.of()),
                            "kof_db_transaction", List.of(Type.UnknownType.UNKNOWN),
                            Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "readFile".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof", "io", List.of()), "kof_read_file",
                            List.of(BuiltinTypes.STRING), new Type.NullableType(BuiltinTypes.STRING), KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "writeFile".equals(mc.methodName()) && mc.arguments().size() == 2) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof", "io", List.of()), "kof_write_file",
                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    yield localIdx;
                }
                if (mc.receiver() == null && "Color".equals(mc.methodName()) && mc.arguments().size() == 3) {
                    localIdx = driver.emitPackedColor(mc.arguments(), ops, owner, localIdx, locals);
                    yield localIdx;
                }
                if (mc.receiver() == null && "Color".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    yield localIdx;
                }
                if (mc.receiver() == null && ("Window".equals(mc.methodName()) || "Label".equals(mc.methodName()))
                        && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    String fn = "Window".equals(mc.methodName()) ? "kof_ui_window_new" : "kof_ui_label_new";
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            fn, List.of(BuiltinTypes.STRING), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_input_new", List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
                        && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    String fn = "Column".equals(mc.methodName()) ? "kof_ui_column_new" : "kof_ui_row_new";
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            fn, List.of(new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_view_new", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                // ── Fase 4: primitivas de layout (docs/ui/architecture.md §2.8)
                if (mc.receiver() == null && ("Box".equals(mc.methodName())
                        || "Stack".equals(mc.methodName()) || "Wrap".equals(mc.methodName())
                        || "Center".equals(mc.methodName()))
                        && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    String fn = switch (mc.methodName()) {
                        case "Box" -> "kof_ui_box_new";
                        case "Stack" -> "kof_ui_stack_new";
                        case "Wrap" -> "kof_ui_wrap_new";
                        default -> "kof_ui_center_new";
                    };
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            fn, List.of(new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Grid".equals(mc.methodName()) && mc.arguments().size() == 2) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_grid_new", List.of(Type.PrimitiveType.INT,
                            new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Spacer".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_spacer_new", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Align".equals(mc.methodName()) && mc.arguments().size() == 3) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                    }
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_align_new", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                            new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                    }
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_style_new", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                            Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Link".equals(mc.methodName()) && mc.arguments().size() == 2) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                    }
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_link_new", List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Image".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_image_new", List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Icon".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                    }
                    if (mc.arguments().size() == 2) {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_icon_new_size", List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    } else {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_icon_new", List.of(BuiltinTypes.STRING),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                }
                if (mc.receiver() == null && "Font".equals(mc.methodName())
                        && (mc.arguments().size() == 2 || mc.arguments().size() == 3)) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                    }
                    if (mc.arguments().size() == 3) {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_font_new_bold", List.of(BuiltinTypes.STRING,
                                        Type.PrimitiveType.INT, Type.PrimitiveType.BOOL),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    } else {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_font_new", List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                }
                if (mc.receiver() == null && "Button".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                    }
                    if (mc.arguments().size() == 2) {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_button_new_action",
                                List.of(BuiltinTypes.STRING, Type.UnknownType.UNKNOWN),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    } else {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_button_new", List.of(BuiltinTypes.STRING),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                }
                if (mc.receiver() == null && "Component".equals(mc.methodName())
                        && mc.arguments().size() == 1) {
                    // Component Core (docs/ui/architecture.md): nó da árvore de
                    // UI com estado reativo + view builder + lifecycle + effects.
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_component_new", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Store".equals(mc.methodName())
                        && mc.arguments().size() == 1) {
                    // Fase 8 (docs/ui/architecture.md §2.6): estado compartilhado
                    // observável entre componentes.
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_store_new", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if ("listOf".equals(mc.methodName()) && mc.receiver() == null) {
                    Type elemType = driver.listOfElementType(mc, locals);
                    Type listType = new Type.ClassType("kof", "List", List.of(elemType));
                    ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
                    for (ExpressionNode arg : mc.arguments()) {
                        ops.add(new KofDup());
                        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        ops.add(new KofCall(listType, "kof_list_add",
                                List.of(ExpressionTyper.inferExprType(driver, arg, locals)), Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                    }
                    yield localIdx;
                }
                if (mc.receiver() == null && ("cancel".equals(mc.methodName())
                        || "cancelled".equals(mc.methodName()) || "selectAny".equals(mc.methodName()))
                        && driver.findLocalVar(mc.methodName(), locals) == null) {
                    boolean argsOk = "cancelled".equals(mc.methodName())
                            ? mc.arguments().isEmpty() : !mc.arguments().isEmpty();
                    if (!argsOk) yield localIdx;
                    // Native: cancel/cancelled/selectAny sobre o handle pthread
                    // (flags de cancel por TID + polling anyOf) — CONC001 fechado.
                    // Android: reusa o caminho JVM (CompletableFuture + platform
                    // threads no ART) — AND001 fechado 31/08.
                    if ("selectAny".equals(mc.methodName())) {
                        Type firstH = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                        Type elemT = new Type.ClassType("kof.concurrent", "Handle",
                                firstH instanceof Type.ClassType fh
                                        && !fh.typeArguments().isEmpty()
                                        ? List.of(fh.typeArguments().get(0)) : List.of());
                        Type listT = new Type.ClassType("kof", "List", List.of(elemT));
                        ops.add(new KofCall(listT, "kof_list_new", List.of(), listT,
                                KofCallKind.FUNCTION));
                        for (ExpressionNode arg : mc.arguments()) {
                            ops.add(new KofDup());
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                            ops.add(new KofCall(listT, "kof_list_add", List.of(elemT),
                                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                        }
                        Type resT = ExpressionTyper.inferExprType(driver, mc, locals);
                        ops.add(new KofCall(
                                new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                                "kof_select_any", List.of(listT), resT, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    String fn = "kof_" + mc.methodName();
                    Type ret = Type.PrimitiveType.BOOL;
                    if ("cancelled".equals(mc.methodName())) {
                        ops.add(new KofCall(
                                new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                                fn, List.of(), ret, KofCallKind.FUNCTION));
                    } else {
                        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                        Type h = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                        ops.add(new KofCall(
                                new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                                fn, List.of(h), ret, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                }
                if (mc.receiver() == null && ("poll".equals(mc.methodName()) || "done".equals(mc.methodName()))
                        && mc.arguments().size() == 1
                        && driver.findLocalVar(mc.methodName(), locals) == null) {
                    // Native: done/poll são leituras não-bloqueantes do flag do
                    // handle (pthread já existe via spawn) — CONC001 fechado p/ estes.
                    // Android: reusa o caminho JVM (Future.isDone/getNow).
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    Type hE = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                    Type rE = Type.UnknownType.UNKNOWN;
                    if (hE instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()
                            && "poll".equals(mc.methodName())) {
                        rE = ct.typeArguments().get(0);
                    }
                    Type ret = "poll".equals(mc.methodName()) ? rE : Type.PrimitiveType.BOOL;
                    ops.add(new KofCall(
                            new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_" + mc.methodName(),
                            List.of(hE), ret, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "awaitTimeout".equals(mc.methodName())
                        && mc.arguments().size() == 2
                        && driver.findLocalVar("awaitTimeout", locals) == null) {
                    // awaitTimeout(r, timeoutMs): valor se a task terminar no prazo;
                    // senão lança exceção (capturável via try/catch). G8/CONC residual.
                    // Android: Future.get(timeout) existe no ART — AND001 fechado.
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    Type hT = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                    Type resT = Type.UnknownType.UNKNOWN;
                    if (hT instanceof Type.ClassType ct && "kof.concurrent".equals(ct.packageName())
                            && !ct.typeArguments().isEmpty()) {
                        resT = ct.typeArguments().get(0);
                    }
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
                    ops.add(new KofCall(
                            new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_await_timeout", List.of(hT, Type.PrimitiveType.INT),
                            resT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "channel".equals(mc.methodName())
                        && mc.arguments().isEmpty()
                        && driver.findLocalVar("channel", locals) == null) {
                    // Canais tipados (concorrência): channel<T>() -> Channel<T>
                    // FIFO thread-safe; c.send(v) enfileira, c.receive() retira.
                    Type elemT = mc.typeArguments().isEmpty()
                            ? Type.UnknownType.UNKNOWN
                            : CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
                    Type chanT = new Type.ClassType("kof.concurrent", "Channel", List.of(elemT));
                    ops.add(new KofCall(chanT, "kof_channel_new", List.of(),
                            chanT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "__kof_spawn_expr".equals(mc.methodName())) {
                    if (driver.target.isNative()) {
                        // CONC001: spawn-expr com handle real (pthread)
                        ExpressionNode bodyN = mc.arguments().get(0);
                        Type resultTN = ExpressionTyper.inferExprType(driver, bodyN, locals);
                        Type handleTN = new Type.ClassType("kof.concurrent", "Handle", List.of(resultTN));
                        LambdaExpr leN2 = bodyN instanceof LambdaExpr l2 ? l2
                                : new LambdaExpr(bodyN.position() != null ? bodyN.position() : mc.position(),
                                        List.of(), List.of(new ExpressionStmt(
                                                bodyN.position() != null ? bodyN.position() : mc.position(), bodyN)));
                        Type.FunctionType ftN2 = new Type.FunctionType(List.of(), resultTN, null);
                        String lambdaClassN2 = driver.lambdaClass(leN2, ftN2, List.of(), true);
                        Type taskTypeN2 = new Type.ClassType("", lambdaClassN2, List.of());
                        ops.add(new KofNewObject(taskTypeN2, List.of()));
                        ops.add(new KofDup());
                        ops.add(new KofCall(taskTypeN2, "<init>", List.of(),
                                Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                                "kof_spawn_result", List.of(taskTypeN2), handleTN, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    ExpressionNode body = mc.arguments().get(0);
                    Type resultT = ExpressionTyper.inferExprType(driver, body, locals);
                    Type handleT = new Type.ClassType("kof.concurrent", "Handle", List.of(resultT));
                    LambdaExpr le = body instanceof LambdaExpr l0 ? l0
                            : new LambdaExpr(body.position() != null ? body.position() : mc.position(),
                                    List.of(), List.of(new ExpressionStmt(
                                            body.position() != null ? body.position() : mc.position(), body)));
                    Type.FunctionType ft = new Type.FunctionType(List.of(), resultT, null);
                    String lambdaClass = driver.lambdaClass(le, ft, List.of(), true);
                    Type taskType = new Type.ClassType("", lambdaClass, List.of());
                    ops.add(new KofNewObject(taskType, List.of()));
                    ops.add(new KofDup());
                    ops.add(new KofCall(taskType, "<init>", List.of(),
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                    ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_spawn_result", List.of(taskType), handleT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "__kof_await".equals(mc.methodName())) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    Type hT = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                    Type resT = Type.UnknownType.UNKNOWN;
                    if (hT instanceof Type.ClassType ct
                            && !ct.typeArguments().isEmpty()) resT = ct.typeArguments().get(0);
                    ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_await", List.of(hT), resT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && CompilerTypes.isEnumName(rid.name(), driver.currentUnit)
                        && !driver.isLocalVarName(rid.name(), locals)) {
                    Type enumT = new Type.ClassType("", rid.name(), List.of());
                    // lista interna com elemento STRING (runtime do enum é o nome);
                    // a tipagem List<Color> fica na checagem de tipos
                    Type stringListT = new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING));
                    if ("values".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        ops.add(new KofCall(stringListT,
                                "kof_list_new", List.of(), stringListT,
                                KofCallKind.FUNCTION));
                        for (String c : CompilerTypes.enumConstantsOf(rid.name(), driver.currentUnit)) {
                            ops.add(new KofDup());
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, c));
                            ops.add(new KofCall(stringListT,
                                    "kof_list_add", List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                                    KofCallKind.INSTANCE));
                        }
                        yield localIdx;
                    }
                    if ("valueOf".equals(mc.methodName()) && mc.arguments().size() == 1) {
                        Type listT = stringListT;
                        ops.add(new KofCall(listT, "kof_list_new", List.of(), listT,
                                KofCallKind.FUNCTION));
                        for (String c : CompilerTypes.enumConstantsOf(rid.name(), driver.currentUnit)) {
                            ops.add(new KofDup());
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, c));
                            ops.add(new KofCall(listT, "kof_list_add", List.of(enumT),
                                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                        }
                        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                        ops.add(new KofCall(enumT, "kof_enum_value_of",
                                List.of(listT, BuiltinTypes.STRING), enumT, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    yield localIdx;
                }
                if ("mapOf".equals(mc.methodName()) && mc.receiver() == null) {

                    Type keyType = Type.UnknownType.UNKNOWN;
                    Type valueType = Type.UnknownType.UNKNOWN;
                    if (!mc.arguments().isEmpty()) {
                        // mapOf(k1, v1, k2, v2, ...): pinning do tipo no primeiro par
                        keyType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                        if (mc.arguments().size() > 1) {
                            valueType = ExpressionTyper.inferExprType(driver, mc.arguments().get(1), locals);
                        }
                    }
                    Type mapType = new Type.ClassType("kof", "Map", List.of(keyType, valueType));
                    ops.add(new KofCall(mapType, "kof_map_new", List.of(), mapType, KofCallKind.FUNCTION));
                    // pares: (k0,v0), (k1,v1), ...
                    for (int ai = 0; ai + 1 < mc.arguments().size(); ai += 2) {
                        ops.add(new KofDup());
                        Type kType = ExpressionTyper.inferExprType(driver, mc.arguments().get(ai), locals);
                        Type vType = ExpressionTyper.inferExprType(driver, mc.arguments().get(ai + 1), locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(ai), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(ai + 1), ops, owner, localIdx, locals);
                        // VOID no put: o map duplicado continua na pilha para o próximo par
                        ops.add(new KofCall(mapType, "kof_map_put", List.of(kType, vType),
                                Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                    }
                    yield localIdx;
                }
                if ("setOf".equals(mc.methodName()) && mc.receiver() == null) {
                    Type elemType = Type.UnknownType.UNKNOWN;
                    if (!mc.arguments().isEmpty()) elemType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                    Type setType = new Type.ClassType("kof", "Set", List.of(elemType));
                    ops.add(new KofCall(setType, "kof_set_new", List.of(), setType, KofCallKind.FUNCTION));
                    for (ExpressionNode arg : mc.arguments()) {
                        ops.add(new KofDup());
                        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        // VOID na construção: o backend descarta o bool e o set
                        // duplicado continua na pilha para o próximo append
                        ops.add(new KofCall(setType, "kof_set_add",
                                List.of(ExpressionTyper.inferExprType(driver, arg, locals)), Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                    }
                    yield localIdx;
                }
                if (("print".equals(mc.methodName()) || "println".equals(mc.methodName())) && mc.arguments().size() == 1) {
                    Type printedType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                    if (Type.isVoid(printedType)) {
                        // void não é um valor: println(f()) com f void empilhava
                        // nada e o backend dava pop de lixo (segfault Native /
                        // VerifyError JVM). Diagnóstico limpo em vez disso.
                        if (driver.currentDiagnostics != null) {
                            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                    mc.position() != null ? mc.position().line() : 0,
                                    mc.position() != null ? mc.position().column() : 0, 0,
                                    mc.methodName() + "(...) recebeu um valor void — a chamada não"
                                            + " retorna valor (adicione 'return' ou não a use como argumento)",
                                    "SEM033");
                        }
                        yield localIdx;
                    }
                    if (!driver.fpSupportedOnNative(printedType, mc.position())) {
                        yield localIdx;
                    }
                    ops.add(new KofGetStatic(
                            new Type.ClassType("java.lang", "System", List.of()),
                            "out", new Type.ClassType("java.io", "PrintStream", List.of())));
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                    Type argType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                    if (TypeMetrics.isPrimitiveType(argType)) {
                        if (driver.target.isNative()) {
                            // println(char) é NUMÉRICO (congelado: strings.md
                            // "72 (H)" + execStringCharAt). valueOf(char) solto
                            // é o caractere UTF-8 (common-mistakes.md "h").
                            // O dispatch nativo do valueOf decide pelo tipo do
                            // parâmetro — aqui mapeia char→Int para imprimir o
                            // codepoint sem quebrar String.valueOf(char).
                            Type nativeArg = (argType instanceof Type.PrimitiveType p
                                    && "char".equals(p.name()))
                                    ? Type.PrimitiveType.INT : argType;
                            ops.add(new KofCall(
                                    BuiltinTypes.STRING,
                                    "valueOf", List.of(nativeArg),
                                    BuiltinTypes.STRING, KofCallKind.STATIC));
                        } else {
                            TypeEmitter.boxPrimitive(ops, argType);
                            ops.add(new KofCall(
                                    BuiltinTypes.STRING,
                                    "valueOf", List.of(Type.UnknownType.UNKNOWN),
                                    BuiltinTypes.STRING, KofCallKind.STATIC));
                        }
                    } else {
                        // o tipo REAL do arg só vai para o valueOf NATIVO (para
                        // despachar toString de records). JVM/JS usam Object
                        // (String.valueOf(Object) chama toString; valueOf de um
                        // ClassType específico não existe no JVM).
                        ops.add(new KofCall(
                                BuiltinTypes.STRING,
                                "valueOf", List.of(driver.target.isNative()
                                        && !Type.isString(argType) ? argType
                                        : Type.UnknownType.UNKNOWN),
                                BuiltinTypes.STRING, KofCallKind.STATIC));
                    }
                    ops.add(new KofCall(
                            new Type.ClassType("java.io", "PrintStream", List.of()),
                            mc.methodName(), List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                        && driver.semanticAnalyzer != null
                        && driver.semanticAnalyzer.getClass(rid.name()) != null) {
                    // Metodo ESTATICO de classe KOF de outro pacote:
                    // Desconto.aplicar(c) -> invokestatic vendas/regras/Desconto.aplicar
                    SymbolTable.MethodSymbol ksm = null;
                    SymbolTable.Symbol ks = driver.semanticAnalyzer.resolveInHierarchy(rid.name(), mc.methodName());
                    if (ks instanceof SymbolTable.MethodSymbol ms0
                            && ms0.parameterTypes().size() == mc.arguments().size()) {
                        ksm = ms0;
                    }
                    if (ksm != null) {
                        SymbolTable.ClassSymbol kt = driver.semanticAnalyzer.getClass(rid.name());
                        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ksm.parameterTypes(),
                                ops, owner, localIdx, locals);
                        ops.add(new KofCall(kt.type(), mc.methodName(), ksm.parameterTypes(),
                                ksm.returnType(), KofCallKind.STATIC));
                        yield localIdx;
                    }
                    yield localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                        && CompilerTypes.qualifyViaImports(rid.name(), driver.currentUnit) instanceof Type.ClassType extQ
                        && !extQ.packageName().isEmpty()
                        && driver.externalClasspath != null
                        && driver.externalClasspath.knows(extQ.internalName())
                        && driver.externalClasspath.resolveMethod(extQ.internalName(), mc.methodName(),
                                mc.arguments().size()) != null) {
                    // Nome de CLASSE EXTERNA como receiver: Button.inflate(...)
                    // estático, interface externa ou instância — resolve pelo
                    // classpath ANTES dos namespaces builtin (Button também é
                    // widget do kof.ui; o import decide). Local sombreia.
                    ExternalClasspath.MethodSignature extSig = driver.externalClasspath.resolveMethod(
                            extQ.internalName(), mc.methodName(), mc.arguments().size());
                    List<Type> extFormal = new ArrayList<>();
                    for (String d : extSig.parameterDescriptors()) {
                        extFormal.add(ExternalClasspath.typeFromDescriptor(d));
                    }
                    Type extRet = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
                    localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), extFormal,
                            ops, owner, localIdx, locals);
                    KofCallKind extKind = extSig.isStatic() ? KofCallKind.STATIC
                            : (extSig.ownerIsInterface() ? KofCallKind.INTERFACE
                            : KofCallKind.INSTANCE);
                    ops.add(new KofCall(extQ, mc.methodName(), extFormal, extRet, extKind));
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && "json".equals(rid.name())) {
                    if ("encode".equals(mc.methodName()) && mc.arguments().size() == 1) {
                        Type argType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
                        if (!driver.jsonSupported(argType, false)) {
                            yield localIdx;
                        }
                        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                        List<Type> paramTypes = List.of(argType);
                        if (BuiltinTypes.isList(argType)) {
                            int tag = JsonDispatch.listTag(driver.listElementType(argType));
                            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                            paramTypes = List.of(argType, Type.PrimitiveType.INT);
                        } else if (driver.target.isNative()
                                && argType instanceof Type.ClassType ect
                                && !BuiltinTypes.isString(argType)
                                // List/Map têm caminho builtin próprio
                                && !BuiltinTypes.isList(argType) && !BuiltinTypes.isMap(argType)) {
                            // JSN002: compoe o JSON em compile-time a partir
                            // dos campos conhecidos (sem reflection, sem
                            // walker generico) — so primitivas testadas.
                            String cn2 = ect.packageName().isEmpty()
                                    ? ect.name() : ect.packageName() + "." + ect.name();
                            java.util.List<String[]> flds = driver.classFieldsOrdered(cn2);
                            // guarda o objeto em local temporario
                            ops.add(new KofStoreLocal(argType, localIdx));
                            locals.add(new IRLocalVariable(localIdx, "#jsonobj", argType));
                            int objTmp = localIdx;
                            localIdx += TypeMetrics.isDoubleWidth(argType) ? 2 : 1;
                            // acc = "{"
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "{"));
                            for (int fi = 0; fi < flds.size(); fi++) {
                                String fname = flds.get(fi)[0];
                                Type ftype = CompilerTypes.toType(flds.get(fi)[1], driver.currentUnit);
                                if (fi > 0) {
                                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ","));
                                    ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                                }
                                ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                                        "\"" + fname + "\":"));
                                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                                // valor do campo
                                ops.add(new KofLoadLocal(argType, objTmp));
                                ops.add(new KofLoadField(argType, fname, ftype));
                                switch (ftype instanceof Type.PrimitiveType fp
                                        ? Type.canonicalPrimitiveName(fp.name()) : "") {
                                    case "long":
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_long_to_string",
                                                List.of(Type.PrimitiveType.LONG), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                        break;
                                    case "bool":
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_bool_to_string",
                                                List.of(Type.PrimitiveType.BOOL), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                        break;
                                    case "int":
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_int_to_string",
                                                List.of(Type.PrimitiveType.INT), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                        break;
                                    case "double":
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_double_to_string",
                                                List.of(Type.PrimitiveType.DOUBLE), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                        break;
                                    case "float":
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_float_to_string",
                                                List.of(Type.PrimitiveType.FLOAT), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                        break;
                                    default: // string
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_json_quote",
                                                List.of(BuiltinTypes.STRING), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                }
                                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                            }
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "}"));
                            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
                            yield localIdx;
                        }
                        ops.add(new KofCall(argType, JsonDispatch.encodeFunction(argType), paramTypes,
                                BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    } else if ("decode".equals(mc.methodName()) && mc.arguments().size() == 1
                            && !mc.typeArguments().isEmpty()) {
                        Type targetType = CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
                        if (!driver.jsonSupported(targetType, true)) {
                            yield localIdx;
                        }
                        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                        String decodeFn = JsonDispatch.decodeFunction(targetType, driver.listElementType(targetType));
                        List<Type> decodeParams = List.of(BuiltinTypes.STRING);
                        if (BuiltinTypes.isList(targetType)
                                && driver.listElementType(targetType) instanceof Type.ClassType ect
                                && !BuiltinTypes.isString(ect)) {
                            // decode<List<T>> where T is a user class: bind
                            // each element to T (the element type survives the
                            // generic erasure through the type system).
                            decodeFn = "kof_json_decode_object_list";
                            decodeParams = List.of(BuiltinTypes.STRING, BuiltinTypes.STRING);
                            String className = ect.packageName().isEmpty()
                                    ? ect.name() : ect.packageName() + "." + ect.name();
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, className));
                        } else if (driver.target.isNative()
                                && targetType instanceof Type.ClassType dct
                                && !BuiltinTypes.isString(targetType)
                                // List/Map têm caminho builtin próprio
                                && !BuiltinTypes.isList(targetType) && !BuiltinTypes.isMap(targetType)) {
                            // JSN002: decode composto — find_value por campo +
                            // decoders escalares + construtor canonico
                            String cn3 = dct.packageName().isEmpty()
                                    ? dct.name() : dct.packageName() + "." + dct.name();
                            java.util.List<String[]> flds = driver.classFieldsOrdered(cn3);
                            // json em local temporario
                            ops.add(new KofStoreLocal(BuiltinTypes.STRING, localIdx));
                            locals.add(new IRLocalVariable(localIdx, "#jsonsrc", BuiltinTypes.STRING));
                            int jTmp = localIdx;
                            localIdx += 1;
                            List<Type> ctorTypes = new ArrayList<>();
                            ops.add(new KofNewObject(targetType,
                                    flds.stream().map(f -> CompilerTypes.toType(f[1], driver.currentUnit)).toList()));
                            ops.add(new KofDup());
                            for (String[] f : flds) {
                                Type ft = CompilerTypes.toType(f[1], driver.currentUnit);
                                ctorTypes.add(ft);
                                ops.add(new KofLoadLocal(BuiltinTypes.STRING, jTmp));
                                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, f[0]));
                                ops.add(new KofCall(BuiltinTypes.STRING, "kof_json_find_value",
                                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                                String dec = switch (ft instanceof Type.PrimitiveType fp
                                        ? Type.canonicalPrimitiveName(fp.name()) : "") {
                                    case "int", "char", "byte", "short" -> "kof_json_decode_int";
                                    case "long" -> "kof_json_decode_long";
                                    case "bool" -> "kof_json_decode_bool";
                                    default -> "kof_json_decode_string";
                                };
                                ops.add(new KofCall(targetType, dec,
                                        List.of(BuiltinTypes.STRING), ft, KofCallKind.FUNCTION));
                            }
                            ops.add(new KofCall(targetType, "<init>", ctorTypes,
                                    Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                            yield localIdx;
                        }
                        ops.add(new KofCall(targetType, decodeFn, decodeParams,
                                targetType, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && KofDb.isDbNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
                    KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
                    if (dbCall != null) {
                        if (!KofDb.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (" + KofDb.gapCode() + ")",
                                        KofDb.gapCode());
                            }
                            yield localIdx;
                        }
                        for (int i = 0; i < mc.arguments().size() && i < 2; i++) {
                            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
                        }
                        for (int i = 2; i < mc.arguments().size(); i++) {
                            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
                            TypeEmitter.boxPrimitive(ops, argTypes.get(i));
                        }
                        if (KofDb.isQuery(mc.methodName())) {
                            if (typed && !mc.typeArguments().isEmpty()) {
                                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.typeArguments().get(0)));
                            } else {
                                ops.add(new KofLoadLiteral(Type.UnknownType.UNKNOWN, null));
                            }
                        }
                        List<Type> params = new ArrayList<>(dbCall.parameterTypes());
                        Type retType = dbCall.returnType();
                        if (KofDb.isQuery(mc.methodName())) {
                            // o className (ou null) é sempre empurrado; o
                            // param precisa estar na lista para o native
                            // popar na ordem certa
                            params.add(BuiltinTypes.STRING);
                            if (typed) {
                                retType = new Type.ClassType("kof", "List",
                                        List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
                            }
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.db", "Db", List.of()),
                                dbCall.function(), params, retType, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofOrm.isOrmNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    boolean typed = !mc.typeArguments().isEmpty();
                    String entityName = typed ? mc.typeArguments().get(0) : null;
                    if (entityName == null && "save".equals(mc.methodName()) && !argTypes.isEmpty()) {
                        Type objType = argTypes.get(argTypes.size() - 1);
                        if (objType instanceof Type.ClassType ct) entityName = ct.name();
                    }
                    KofOrm.OrmCall ormCall = KofOrm.staticCall(mc.methodName(), argTypes, typed, entityName);
                    if (ormCall != null) {
                        if (!KofOrm.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (" + KofOrm.gapCode() + ")",
                                        KofOrm.gapCode());
                            }
                            yield localIdx;
                        }
                        List<EntityFieldNode> fields = entityName == null ? null : driver.entitySchemas.get(entityName);
                        boolean needsEntity = !"migrate".equals(mc.methodName());
                        if (needsEntity && fields == null) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        "orm." + mc.methodName() + ": unknown entity '"
                                                + (entityName == null ? "?" : entityName) + "' (ORM002)",
                                        "ORM002");
                            }
                            yield localIdx;
                        }
                        // P3-10: validação tipada do campo em where/count/where_op —
                        // a coluna tem que ser um campo real da entidade (ORM003)
                        driver.validateOrmField(mc, entityName, fields);
                        // args do usuário: (db[, obj|id]) — primitivos são
                        // boxed (o runtime espera Object para obj/id)
                        for (int ai = 0; ai < mc.arguments().size(); ai++) {
                            ExpressionNode arg = mc.arguments().get(ai);
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                            if (ai > 0 && TypeMetrics.isPrimitiveType(ExpressionTyper.inferExprType(driver, arg, locals))) {
                                TypeEmitter.boxPrimitive(ops, ExpressionTyper.inferExprType(driver, arg, locals));
                            }
                        }
                        // literais do schema (conhecidos em compile-time):
                        // table, schema, [className]
                        boolean isMigrate = "migrate".equals(mc.methodName());
                        String table = entityName == null ? "" : KofOrm.tableName(entityName);
                        String schema = entityName == null ? "" : KofOrm.schemaString(fields);
                        boolean needsClassName = "find".equals(mc.methodName())
                                || "all".equals(mc.methodName())
                                || "where".equals(mc.methodName())
                                || "page".equals(mc.methodName());
                        List<Type> params = new ArrayList<>(ormCall.parameterTypes());
                        if (!isMigrate) {
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, table));
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, schema));
                            params.add(BuiltinTypes.STRING); // table
                            params.add(BuiltinTypes.STRING); // schema
                        }
                        if (needsClassName) {
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, CompilerTypes.classNameFor(entityName)));
                            params.add(BuiltinTypes.STRING); // className
                        }
                        Type retType = ormCall.returnType();
                        if ("save".equals(mc.methodName()) && !argTypes.isEmpty()) {
                            retType = argTypes.get(argTypes.size() - 1);
                        } else if (typed) {
                            if ("all".equals(mc.methodName()) || "page".equals(mc.methodName())
                                    || "where".equals(mc.methodName())) {
                                retType = new Type.ClassType("kof", "List",
                                        List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
                            } else if ("find".equals(mc.methodName())) {
                                retType = CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
                            }
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.orm", "Orm", List.of()),
                                ormCall.function(), params, retType, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofLog.isLogNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
                    if (logCall != null) {
                        if (!KofLog.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (" + KofLog.gapCode() + ")",
                                        KofLog.gapCode());
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.log", "Log", List.of()),
                                logCall.function(), logCall.parameterTypes(), logCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                        && driver.findLocalVar(rid.name(), locals) == null) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
                    if (procCall != null && "kof_process_spawn".equals(procCall.function())) {
                        if (driver.target.isNative()) {
                            // F10: pipes vivos no native exigem fork/exec com
                            // descriptors no runtime asm — gap explícito por ora
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        "process.spawn: interactive stdin/stdout not supported on the Native driver.target yet (JVM/JS support it)",
                                        "PROC001");
                            }
                            yield localIdx;
                        }
                        // F10: process.spawn(program, args...) → monta List<String>
                        // e chama kof_process_spawn (stdin/stdout vivos)
                        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                        Type listType = KofProcess.STRING_LIST;
                        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
                        for (int i = 1; i < mc.arguments().size(); i++) {
                            ops.add(new KofDup());
                            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
                            ops.add(new KofCall(listType, "kof_list_add",
                                    List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                                    KofCallKind.INSTANCE));
                        }
                        ops.add(new KofCall(KofProcess.HANDLE, "kof_process_spawn",
                                List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                                KofProcess.HANDLE, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (procCall != null) {
                        if (driver.target.isNative()) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        "process.run: not supported on the Native driver.target yet (JVM supports it)",
                                        "PROC001");
                            }
                            yield localIdx;
                        }
                        // process.run(program, args...) →
                        // kof_process_run(program, List<String>)
                        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                        Type listType = KofProcess.STRING_LIST;
                        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
                        for (int i = 1; i < mc.arguments().size(); i++) {
                            ops.add(new KofDup());
                            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
                            ops.add(new KofCall(listType, "kof_list_add",
                                    List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                                    KofCallKind.INSTANCE));
                        }
                        ops.add(new KofCall(KofProcess.RESULT, "kof_process_run",
                                List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                                KofProcess.RESULT, KofCallKind.FUNCTION));
                    } else {
                        // process.exit(code) — todos os targets
                        KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
                        if (exitCall != null) {
                            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                            ops.add(new KofCall(new Type.ClassType("kof.process", "Process", List.of()),
                                    exitCall.function(), exitCall.parameterTypes(), exitCall.returnType(),
                                    KofCallKind.FUNCTION));
                        }
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofHttp.isHttpNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
                    if (httpCall != null) {
                        if (!KofHttp.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (HTTP002)",
                                        "HTTP002");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofHttp.HTTP, httpCall.function(), httpCall.parameterTypes(),
                                httpCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofTime.isTimeNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
                    if (timeCall != null) {
                        if (!KofTime.supportedOn(mc.methodName(), driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (TIME001)",
                                        "TIME001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofTime.TIME, timeCall.function(), timeCall.parameterTypes(),
                                timeCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofScheduler.isSchedulerNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofScheduler.SchedulerCall schedCall = KofScheduler.staticCall(mc.methodName(), argTypes);
                    if (schedCall != null) {
                        if (!KofScheduler.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (SCHED001)",
                                        "SCHED001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofScheduler.SCHEDULER, schedCall.function(), schedCall.parameterTypes(),
                                schedCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() == null && KofScheduler.isSchedulerMethod(mc.methodName())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofScheduler.SchedulerCall schedCall = KofScheduler.staticCall(mc.methodName(), argTypes);
                    if (schedCall != null) {
                        if (!KofScheduler.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (SCHED001)",
                                        "SCHED001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofScheduler.SCHEDULER, schedCall.function(), schedCall.parameterTypes(),
                                schedCall.returnType(), KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    // fall through to normal handling if not matched
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofMq.isMqNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
                    if (mqCall != null) {
                        if (!KofMq.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (MQ001)",
                                        "MQ001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofMq.MQ, mqCall.function(), mqCall.parameterTypes(),
                                mqCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofConfig.isConfigNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
                    if (cfgCall != null) {
                        if (!KofConfig.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (CONF001)",
                                        "CONF001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        driver.recordConfigKey(mc);
                        ops.add(new KofCall(KofConfig.CONFIG, cfgCall.function(), cfgCall.parameterTypes(),
                                cfgCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofCache.isCacheNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofCache.CacheCall cacheCall = KofCache.staticCall(mc.methodName(), argTypes);
                    if (cacheCall != null) {
                        if (!KofCache.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (CACHE001)",
                                        "CACHE001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofCache.CACHE, cacheCall.function(), cacheCall.parameterTypes(),
                                cacheCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofGpu.isGpuNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    if (System.getProperty("kof.trace") != null) {
                        System.err.println("GPU call " + mc.methodName() + " argTypes=" + argTypes);
                    }
                    // Unknown (var sem tipo inferido no lowering) casa com
                    // qualquer array: o staticCall exige tipos concretos, mas
                    // o `var a = new Long[4]` pode chegar como Unknown quando
                    // o local foi registrado antes do NewArray. Substitui
                    // Unknown por Long[]/Int[] conforme o nome do método.
                    List<Type> candidate = new ArrayList<>();
                    boolean hasUnknown = false;
                    for (Type t : argTypes) {
                        if (t instanceof Type.UnknownType) { hasUnknown = true; break; }
                    }
                    if (hasUnknown) {
                        Type arrType = "dispatchMatmul64".equals(mc.methodName())
                                ? new Type.ArrayType(Type.PrimitiveType.LONG)
                                : new Type.ArrayType(Type.PrimitiveType.INT);
                        for (Type t : argTypes) {
                            candidate.add(t instanceof Type.UnknownType ? arrType : t);
                        }
                        argTypes = candidate;
                    }
                    KofGpu.GpuCall gpuCall = KofGpu.staticCall(mc.methodName(), argTypes);
                    if (gpuCall != null) {
                        if (!KofGpu.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (GPU001)",
                                        "GPU001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofGpu.GPU, gpuCall.function(), gpuCall.parameterTypes(),
                                gpuCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofSecurity.isSecurityNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
                    if (secCall != null) {
                        if (!KofSecurity.supportedOn(secCall.function(), driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (" + KofSecurity.gapCode(secCall.function()) + ")",
                                        KofSecurity.gapCode(secCall.function()));
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.security", "Security", List.of()),
                                secCall.function(), secCall.parameterTypes(), secCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofValidation.isValidationNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
                    if (vCall != null) {
                        if (!KofValidation.supportedOn(vCall.function(), driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (" + KofValidation.gapCode(vCall.function()) + ")",
                                        KofValidation.gapCode(vCall.function()));
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.validation", "Validation", List.of()),
                                vCall.function(), vCall.parameterTypes(), vCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofObservability.isObservabilityNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
                    if (oCall != null) {
                        if (!KofObservability.supportedOn(oCall.function(), driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (" + KofObservability.gapCode(oCall.function()) + ")",
                                        KofObservability.gapCode(oCall.function()));
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.observability", "Observability", List.of()),
                                oCall.function(), oCall.parameterTypes(), oCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofTetris.isTetrisNamespace(rid.name())) {
                    KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                            mc.arguments().size());
                    if (tetrisCall != null) {
                        if (!KofTetris.supportedOn(driver.target)) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + driver.target
                                                + " driver.target yet (" + KofTetris.gapCode() + ")",
                                        KofTetris.gapCode());
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.tetris", "Tetris", List.of()),
                                tetrisCall.function(), tetrisCall.parameterTypes(), tetrisCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                            && KofWeb.isWebNamespace(rid.name())) {
                    if ("app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        if (driver.target != Target.JVM && driver.target != Target.ANDROID
                                && driver.target != Target.NATIVE
                                && driver.target != Target.NATIVE_RISCV64
                                && driver.target != Target.NATIVE_AARCH64) {
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        "web: not available on the " + driver.target
                                                + " driver.target yet (WEB001)",
                                        "WEB001");
                            }
                            yield localIdx;
                        }
                        KofWeb.WebCall appCall = KofWeb.appConstructor();
                        ops.add(new KofCall(KofWeb.APP, appCall.function(), appCall.parameterTypes(),
                                appCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && KofIo.isConstructor(rid.name())) {
                    KofIo.IoCall ioCall = KofIo.staticMethod(rid.name(), mc.methodName(), mc.arguments().size());
                    if (ioCall != null) {
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                                ioCall.function(), ioCall.parameterTypes(), ioCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && KofMedia.isStaticNamespace(rid.name())) {
                    KofMedia.MediaCall mediaCall = KofMedia.staticCall(rid.name(), mc.methodName(), mc.arguments().size());
                    if (mediaCall != null) {
                        if (driver.target != Target.JVM && driver.target != Target.ANDROID) {
                            String code = KofMedia.gapCode(mediaCall.function());
                            if (driver.currentDiagnostics != null) {
                                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName() + ": not available on the "
                                                + driver.target + " driver.target yet (" + code + ")",
                                        code);
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                                mediaCall.function(), mediaCall.parameterTypes(),
                                mediaCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid2 && KofUi.isPalette(rid2.name())) {
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
                    KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
                    if (uiCall != null && "kof_ui_color_rgba".equals(uiCall.function())) {
                        localIdx = driver.emitPackedColor(mc.arguments(), ops, owner, localIdx, locals);
                        yield localIdx;
                    }
                    if (uiCall != null && "kof_ui_theme_light".equals(uiCall.function())) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        yield localIdx;
                    }
                    if (uiCall != null && "kof_ui_theme_dark".equals(uiCall.function())) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                        yield localIdx;
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr ridRt && KofUi.isRouterNamespace(ridRt.name())) {
                    // Fase 7 (docs/ui/architecture.md §2.9): Router.*
                    KofUi.UiCall routerCall = KofUi.staticMethod("Router", mc.methodName(), mc.arguments().size());
                    if (routerCall != null) {
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofUi.COMPONENT, routerCall.function(), routerCall.parameterTypes(),
                                routerCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() != null) {
                    if (mc.receiver() instanceof IdentifierExpr sid && "super".equals(sid.name())
                            && !owner.isEmpty()) {
                        // super.method(args): non-virtual call to the
                        // superclass implementation — lowered to
                        // INVOKESPECIAL on the direct superclass (JVM).
                        if (driver.target.isNative() && driver.currentDiagnostics != null) {
                            SourcePosition p = mc.position();
                            driver.currentDiagnostics.error(p != null ? p.file() : "",
                                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                    "super." + mc.methodName()
                                            + "() is not supported on the native driver.target yet (SUP001)",
                                    "SUP001");
                            yield localIdx;
                        }
                        // super.metodo() só faz sentido no corpo de um método
                        // de classe; dentro de lambda sintética usa o driver
                        // externo capturado ($outer) — sem ele, gap honesto
                        String effectiveOwner = owner;
                        String ownerSimple0 = owner.substring(owner.lastIndexOf('/') + 1);
                        if (driver.semanticAnalyzer == null || driver.semanticAnalyzer.getClass(ownerSimple0) == null) {
                            String enc = driver.lambdaEnclosingOwner.get(owner);
                            if (enc == null) {
                                if (driver.currentDiagnostics != null) {
                                    SourcePosition p = mc.position();
                                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                            "super." + mc.methodName()
                                                    + "() is only valid inside class methods (SUP002)",
                                            "SUP002");
                                }
                                yield localIdx;
                            }
                            effectiveOwner = enc;
                        }
                        String superInternal = HierarchyResolver.findSuperClass(effectiveOwner, driver.semanticAnalyzer);
                        if (superInternal == null) superInternal = "java/lang/Object";
                        // nomes declarados com pontos (android.view.View)
                        // viram nome interno JVM para resolução e emissão
                        superInternal = superInternal.replace('.', '/');
                        Type superType = CompilerTypes.ownerTypeFromInternal(superInternal, driver.semanticAnalyzer);
                        SymbolTable.MethodSymbol superMethod = null;
                        if (driver.semanticAnalyzer != null) {
                            String superSimple = superInternal.substring(superInternal.lastIndexOf('/') + 1);
                            SymbolTable.Symbol s = driver.semanticAnalyzer.resolveInHierarchy(superSimple, mc.methodName());
                            if (s instanceof SymbolTable.MethodSymbol ms) superMethod = ms;
                        }
                        List<Type> paramTypes;
                        Type returnType;
                        StringMethodRegistry.Sig osig = StringMethodRegistry.objectMethodSignature(mc.methodName(), mc.arguments().size());
                        ExternalClasspath.MethodSignature extSig = null;
                        if (superMethod == null && osig == null && driver.externalClasspath != null) {
                            extSig = driver.externalClasspath.resolveMethod(superInternal, mc.methodName(),
                                    mc.arguments().size());
                        }
                        if (superMethod == null && osig == null && extSig == null
                                && HierarchyResolver.hierarchyFullyKnown(superInternal, driver.semanticAnalyzer) && driver.currentDiagnostics != null) {
                            // hierarquia inteiramente conhecida e o método não
                            // existe — erro em compile-time, não NoSuchMethodError
                            SourcePosition p = mc.position();
                            driver.currentDiagnostics.error(p != null ? p.file() : "",
                                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                    "method '" + mc.methodName() + "' does not exist in superclass '"
                                            + HierarchyResolver.superSimpleName(superInternal) + "'",
                                    "SEM016");
                            yield localIdx;
                        }
                        if (superMethod != null
                                && superMethod.parameterTypes().size() == mc.arguments().size()) {
                            paramTypes = superMethod.parameterTypes();
                            returnType = superMethod.returnType();
                        } else if (osig != null) {
                            paramTypes = osig.parameterTypes();
                            returnType = osig.returnType();
                        } else if (extSig != null) {
                            // assinatura real lida do classpath externo — o
                            // descritor emitido casa com a classe externa
                            List<Type> formal = new ArrayList<>();
                            for (String d : extSig.parameterDescriptors()) {
                                formal.add(ExternalClasspath.typeFromDescriptor(d));
                            }
                            paramTypes = formal;
                            returnType = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
                        } else {
                            paramTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) paramTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                            returnType = ExpressionTyper.inferExprType(driver, mc, locals);
                        }
                        // receiver: dentro de lambda sintética é o $outer e a
                        // chamada vira uma PONTE kof_super$metodo na classe dona
                        IRLocalVariable outerVar = driver.findLocalVar("$outer", locals);
                        if (outerVar != null) {
                            ops.add(new KofLoadLocal(outerVar.type(), outerVar.index()));
                            String bridgeName = driver.ensureSuperBridge(effectiveOwner, superInternal,
                                    mc.methodName(), paramTypes, returnType);
                            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), paramTypes,
                                    ops, effectiveOwner, localIdx, locals);
                            ops.add(new KofCall(CompilerTypes.ownerTypeFromInternal(effectiveOwner, driver.semanticAnalyzer), bridgeName,
                                    paramTypes, returnType, KofCallKind.INSTANCE));
                            yield localIdx;
                        } else {
                            ops.add(new KofLoadLocal(CompilerTypes.ownerTypeFromInternal(effectiveOwner, driver.semanticAnalyzer), 0));
                            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), paramTypes, ops, owner, localIdx, locals);
                            ops.add(new KofCall(superType, mc.methodName(), paramTypes, returnType, KofCallKind.SUPER));
                            yield localIdx;
                        }
                    }
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.receiver(), ops, owner, localIdx, locals);
                    Type recvType = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
                    // narrowing de null-safety (`if (x != null) { x.substring(...) }`):
                    // dispatch pelo inner — antes emitia `"".substring` (owner "" inválido)
                    if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
                    if (KofUi.isUiType(recvType)) {
                        localIdx = driver.emitUiInstance(recvType, mc, ops, owner, localIdx, locals);
                        yield localIdx;
                    }
                    if (CompilerTypes.isEnumType(recvType, driver.currentUnit) && "name".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        // o valor do enum JÁ é o nome (String em runtime): identidade
                        yield localIdx;
                    }
                    if (KofWeb.isAppType(recvType)) {
                        List<Type> webArgTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) webArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
                        if (webCall != null) {
                            boolean nativeWebT1 = (driver.target == Target.NATIVE
                                    || driver.target == Target.NATIVE_RISCV64
                                    || driver.target == Target.NATIVE_AARCH64)
                                    && (webCall.function().equals("kof_web_listen")
                                        || webCall.function().equals("kof_web_route"));
                            if (driver.target != Target.JVM && driver.target != Target.ANDROID && !nativeWebT1) {
                                String webCode = KofWeb.gapCode(webCall.function());
                                String webMsg = switch (webCode) {
                                    case "WEB002" -> "web TLS: not available on the " + driver.target
                                            + " driver.target yet (WEB002)";
                                    case "WEB003" -> "web SSE: not available on the " + driver.target
                                            + " driver.target yet (WEB003)";
                                    case "WEB004" -> "web WebSocket: not available on the " + driver.target
                                            + " driver.target yet (WEB004)";
                                    default -> "web: not available on the " + driver.target
                                            + " driver.target yet (WEB001)";
                                };
                                if (driver.currentDiagnostics != null) {
                                    driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                            mc.position() != null ? mc.position().line() : 0,
                                            mc.position() != null ? mc.position().column() : 0,
                                            0, webMsg, webCode);
                                }
                                yield localIdx;
                            }
                            List<Type> webParams = new ArrayList<>();
                            webParams.add(BuiltinTypes.STRING);
                            if (KofWeb.isRouteMethod(mc.methodName()) && !"ws".equals(mc.methodName())) {
                                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.methodName().toUpperCase()));
                                webParams.add(BuiltinTypes.STRING);
                            }
                            for (ExpressionNode arg : mc.arguments()) {
                                webParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                            }
                            ops.add(new KofCall(KofWeb.APP, webCall.function(), webParams,
                                    webCall.returnType(), KofCallKind.FUNCTION));
                        }
                        yield localIdx;
                    }
                    if (KofMedia.isHandleType(recvType)) {
                        KofMedia.MediaCall mediaCall =
                                KofMedia.handleMethod(recvType, mc.methodName(), mc.arguments().size());
                        if (mediaCall != null) {
                            List<Type> mediaParams = new ArrayList<>();
                            mediaParams.add(Type.PrimitiveType.INT);      // handle (receiver)
                            for (ExpressionNode arg : mc.arguments()) {
                                mediaParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                            }
                            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                                    mediaCall.function(), mediaParams,
                                    mediaCall.returnType(), KofCallKind.FUNCTION));
                        }
                        yield localIdx;
                    }
                    if (KofIo.isIoType(recvType)) {
                        if (KofIo.isIdentityMethod(mc.methodName())) {
                            yield localIdx;
                        }
                        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
                        if (ioCall != null) {
                            // receiver File/Path/Directory é apagado pra String
                            // path em runtime (empilhado acima); os METHOD args
                            // alinham com ioCall.parameterTypes() — a conversão
                            // formal (int literal → long slot no readRange)
                            // evita o frame bug I/J no visitMaxs
                            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ioCall.parameterTypes(),
                                    ops, owner, localIdx, locals);
                            List<Type> ioParams = new ArrayList<>();
                            ioParams.add(BuiltinTypes.STRING);
                            ioParams.addAll(ioCall.parameterTypes());
                            ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                                    ioCall.function(), ioParams, ioCall.returnType(), KofCallKind.FUNCTION));
                            yield localIdx;
                        }
                    }
                    if (recvType instanceof Type.FunctionType ft) {
                        if (ft.className() == null) {
                            // bug 8: valor de TIPO DE FUNÇÃO DECLARADO (param
                            // (s: (Int) -> Int), sem classe sintética). Todas as
                            // lambdas da assinatura implementam a interface
                            // sintética — invoca via INVOKEINTERFACE.
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                            for (ExpressionNode arg : mc.arguments()) {
                                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                            }
                            Type iface = driver.lambdaInterfaceType(ft);
                            ops.add(new KofCall(iface, "invoke", argTypes, ft.returnType(), KofCallKind.INTERFACE));
                            yield localIdx;
                        }
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                        }
                        // f.invoke(): o owner precisa ser a classe sintética
                        // da lambda — FunctionType não tem nome JVM
                        Type invokeOwner = new Type.ClassType("", ft.className(), List.of());
                        ops.add(new KofCall(invokeOwner, "invoke", argTypes, ft.returnType(), KofCallKind.INSTANCE));
                        yield localIdx;
                    }
                    if (BuiltinTypes.isList(recvType)
                            && ("map".equals(mc.methodName()) || "filter".equals(mc.methodName())
                                || "reduce".equals(mc.methodName()))) {
                        String hoFn = "kof_list_" + mc.methodName();
                        // receiver já empilhado acima (3396) — não duplicar
                        Type lambdaT = Type.UnknownType.UNKNOWN;
                        // reduce: init antes; lambda por último
                        for (ExpressionNode arg : mc.arguments()) {
                            if (!(arg instanceof LambdaExpr)) {
                                Type argT = ExpressionTyper.inferExprType(driver, arg, locals);
                                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                                if (TypeMetrics.isPrimitiveType(argT) && driver.target == Target.JVM) {
                                    Type boxed = TypeMetrics.boxedTypeFor(argT);
                                    ops.add(new KofCall(boxed, "kof_box", List.of(argT), boxed, KofCallKind.FUNCTION));
                                }
                            }
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            if (arg instanceof LambdaExpr lam) {
                                lambdaT = ExpressionTyper.inferExprType(driver, lam, locals);
                                localIdx = ExpressionLowerer.emitExpression(driver, lam, ops, owner, localIdx, locals);
                            }
                        }
                        List<Type> callParams = new ArrayList<>();
                        callParams.add(new Type.ClassType("java.util", "ArrayList", List.of()));
                        if ("reduce".equals(mc.methodName())) callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
                        callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
                        Type ret;
                        if ("filter".equals(mc.methodName())) ret = recvType;
                        else if ("map".equals(mc.methodName())) {
                            Type elem = (lambdaT instanceof Type.FunctionType ft && !(ft.returnType() instanceof Type.UnknownType)) ? ft.returnType() : Type.UnknownType.UNKNOWN;
                            ret = new Type.ClassType("kof", "List", List.of(elem));
                        } else {
                            ret = (lambdaT instanceof Type.FunctionType ft) ? ft.returnType() : Type.UnknownType.UNKNOWN;
                        }
                        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()), hoFn, callParams, ret,
                                KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofProcess.isHandle(recvType)) {
                        // F10: h.write/readLine/exitCode/kill/alive — o handle
                        // empilhado entra como 1º parâmetro do call estático
                        KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(),
                                mc.arguments().stream().map(a -> ExpressionTyper.inferExprType(driver, a, locals)).toList());
                        if (hm != null) {
                            List<Type> params = new ArrayList<>();
                            params.add(KofProcess.HANDLE);
                            for (int pi = 1; pi < hm.parameterTypes().size(); pi++) {
                                params.add(hm.parameterTypes().get(pi));
                            }
                            for (ExpressionNode arg : mc.arguments()) {
                                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                            }
                            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                                    hm.function(), params, hm.returnType(), KofCallKind.FUNCTION));
                            yield localIdx;
                        }
                    }
                    if (BuiltinTypes.isChannel(recvType)) {
                        // Canais tipados: c.send(v) enfileira; c.receive() retira.
                        // O receiver (Channel) está empilhado; o elemento vai
                        // após — o backend faz a ordem (send: chan,elem; receive: chan).
                        Type elemT = BuiltinTypes.channelElement(recvType);
                        if ("send".equals(mc.methodName()) && mc.arguments().size() == 1) {
                            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                            ops.add(new KofCall(recvType, "kof_channel_send", List.of(elemT),
                                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                            yield localIdx;
                        }
                        if ("receive".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                            ops.add(new KofCall(recvType, "kof_channel_receive", List.of(),
                                    elemT, KofCallKind.INSTANCE));
                            yield localIdx;
                        }
                    }
                    if (BuiltinTypes.isList(recvType)) {
                        String listFn = switch (mc.methodName()) {
                            case "add", "push", "append" -> "kof_list_add";
                            case "get" -> "kof_list_get";
                            case "set" -> "kof_list_set";
                            case "size", "length", "count" -> "kof_list_size";
                            case "contains" -> "kof_list_contains";
                            case "isEmpty" -> "kof_list_is_empty";
                            case "remove" -> "kof_list_remove";
                            case "clear" -> "kof_list_clear";
                            default -> null;
                        };
                        if (listFn != null) {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                            Type elemType = driver.listElementType(recvType);
                            // listOf() with no type argument produces
                            // List<Unknown>; the first add() pins the element
                            // type on the local so later get() calls are
                            // typed (records, classes) instead of Object.
                            if ("kof_list_add".equals(listFn)
                                    && Type.UnknownType.UNKNOWN.equals(elemType)
                                    && !argTypes.isEmpty()
                                    && !(argTypes.get(0) instanceof Type.UnknownType)
                                    && mc.receiver() instanceof IdentifierExpr rid) {
                                for (int li = 0; li < locals.size(); li++) {
                                    IRLocalVariable lv = locals.get(li);
                                    if (lv.name().equals(rid.name())) {
                                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                                new Type.ClassType("kof", "List", List.of(argTypes.get(0)))));
                                        break;
                                    }
                                }
                            }
                            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                            Type retType = switch (listFn) {
                                case "kof_list_add", "kof_list_set", "kof_list_clear" -> Type.PrimitiveType.VOID;
                                case "kof_list_contains", "kof_list_is_empty" -> Type.PrimitiveType.BOOL;
                                case "kof_list_remove" -> elemType;
                                default -> elemType;
                            };
                            if ("kof_list_contains".equals(listFn)) {

                                int tag = BuiltinTypes.isString(elemType) ? 1 : 0;
                                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                                argTypes = new ArrayList<>(argTypes);
                                argTypes.add(Type.PrimitiveType.INT);
                            }
                            ops.add(new KofCall(recvType, listFn, argTypes, retType, KofCallKind.INSTANCE));
                            yield localIdx;
                        }
                    }
                    if (BuiltinTypes.isMap(recvType)) {

                        String mapFn = switch (mc.methodName()) {
                            case "put" -> "kof_map_put";
                            case "get" -> "kof_map_get";
                            case "remove" -> "kof_map_remove";
                            case "containsKey", "contains" -> "kof_map_contains";
                            case "size", "length", "count" -> "kof_map_size";
                            case "clear" -> "kof_map_clear";
                            case "isEmpty" -> "kof_map_is_empty";
                            case "keys" -> "kof_map_keys";
                            case "values" -> "kof_map_values";
                            default -> null;
                        };
                        if (mapFn != null) {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                            Type keyType = Type.UnknownType.UNKNOWN;
                            Type valueType = Type.UnknownType.UNKNOWN;
                            if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) {
                                keyType = ct.typeArguments().get(0);
                                valueType = ct.typeArguments().get(1);
                            }
                            // mapOf() nasce Map<Unknown,Unknown>: o primeiro put()
                            // pina os tipos no local para que get()/remove() tenham
                            // tipo concreto (comparações e unboxing corretos)
                            if ("kof_map_put".equals(mapFn)
                                    && keyType instanceof Type.UnknownType
                                    && argTypes.size() == 2
                                    && !(argTypes.get(0) instanceof Type.UnknownType)
                                    && mc.receiver() instanceof IdentifierExpr rid) {
                                for (int li = 0; li < locals.size(); li++) {
                                    IRLocalVariable lv = locals.get(li);
                                    if (lv.name().equals(rid.name())) {
                                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                                new Type.ClassType("kof", "Map", List.of(argTypes.get(0), argTypes.get(1)))));
                                        break;
                                    }
                                }
                            }
                            Type retType = switch (mapFn) {
                                case "kof_map_put", "kof_map_remove" -> valueType;
                                // get() devolve V? para valores de REFERÊNCIA (ausência = null,
                                // narrowing via `if (x != null)`); para primitivos/UI a ausência
                                // não é representável no modelo atual (storage é o primitivo) —
                                // ficam como V e a ausência vira exceção/erro de runtime.
                                case "kof_map_get" -> valueType instanceof Type.ClassType ct
                                        && !KofUi.isUiType(ct) && !KofMedia.isHandleType(ct)
                                        ? new Type.NullableType(valueType) : valueType;
                                case "kof_map_contains", "kof_map_is_empty" -> Type.PrimitiveType.BOOL;
                                case "kof_map_size" -> Type.PrimitiveType.INT;
                                case "kof_map_clear" -> Type.PrimitiveType.VOID;
                                case "kof_map_keys", "kof_map_values" -> new Type.ClassType("kof", "List", List.of(mapFn.equals("kof_map_keys") ? keyType : valueType));
                                default -> Type.UnknownType.UNKNOWN;
                            };
                            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                            ops.add(new KofCall(recvType, mapFn, argTypes, retType, KofCallKind.INSTANCE));
                            yield localIdx;
                        }
                    }
                    if (BuiltinTypes.isSet(recvType)) {

                        String setFn = switch (mc.methodName()) {
                            case "add" -> "kof_set_add";
                            case "contains" -> "kof_set_contains";
                            case "remove" -> "kof_set_remove";
                            // add/contains/remove recebem tag de tipo (1=string)
                            case "size", "length", "count" -> "kof_set_size";
                            case "clear" -> "kof_set_clear";
                            case "isEmpty" -> "kof_set_is_empty";
                            default -> null;
                        };
                        if (setFn != null) {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                            Type elemType = Type.UnknownType.UNKNOWN;
                            if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) elemType = ct.typeArguments().get(0);
                            Type retType = switch (setFn) {
                                case "kof_set_add", "kof_set_remove" -> Type.PrimitiveType.BOOL;
                                case "kof_set_contains", "kof_set_is_empty" -> Type.PrimitiveType.BOOL;
                                case "kof_set_size" -> Type.PrimitiveType.INT;
                                case "kof_set_clear" -> Type.PrimitiveType.VOID;
                                default -> Type.UnknownType.UNKNOWN;
                            };
                            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                            if (driver.target.isNative()
                                    && ("kof_set_add".equals(setFn) || "kof_set_contains".equals(setFn)
                                        || "kof_set_remove".equals(setFn))) {
                                // tag de tipo só no Native (HashSet usa equals no JVM)
                                int tag = BuiltinTypes.isString(elemType) ? 1 : 0;
                                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                                argTypes = new ArrayList<>(argTypes);
                                argTypes.add(Type.PrimitiveType.INT);
                            }
                            ops.add(new KofCall(recvType, setFn, argTypes, retType, KofCallKind.INSTANCE));
                            yield localIdx;
                        }
                    }
                    // bug 16: `toArray()` não é suportado (nem documentado) e
                    // caía no caminho genérico → bytecode inválido (JVM) /
                    // undefined reference (Native). Diagnóstico limpo em vez de
                    // saída quebrada.
                    if ("toArray".equals(mc.methodName())
                            && (BuiltinTypes.isList(recvType) || BuiltinTypes.isSet(recvType))
                            && driver.currentDiagnostics != null) {
                        driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                mc.position() != null ? mc.position().line() : 0,
                                mc.position() != null ? mc.position().column() : 0, 0,
                                "método '" + mc.methodName() + "' não é suportado em coleções;"
                                        + " use um loop com new T[n] para materializar um array",
                                "SEM029");
                    }
                    // bug 16 (cauda): `sublist()`/`subSet()` retornam COLEÇÃO —
                    // o backend não sabe materializar o retorno de coleção e
                    // emitia bytecode inválido (JVM) / undefined reference
                    // (Native). Mesmo tratamento do toArray: diagnóstico limpo.
                    if (("sublist".equals(mc.methodName()) || "subSet".equals(mc.methodName()))
                            && (BuiltinTypes.isList(recvType) || BuiltinTypes.isSet(recvType))
                            && driver.currentDiagnostics != null) {
                        driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                mc.position() != null ? mc.position().line() : 0,
                                mc.position() != null ? mc.position().column() : 0, 0,
                                "método '" + mc.methodName() + "' não é suportado em coleções"
                                        + " (retorno de coleção não é materializável);"
                                        + " copie os elementos com um loop",
                                "SEM034");
                    }
                    Type methodReturnType = Type.UnknownType.UNKNOWN;
                    List<Type> methodParamTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) {
                        methodParamTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    }
                    SymbolTable.MethodSymbol resolvedMethod = driver.semanticAnalyzer.getResolvedMethod(mc);
                    if (resolvedMethod != null) {
                        recvType = CompilerTypes.ownerTypeFromInternal(resolvedMethod.ownerClass(), driver.semanticAnalyzer);
                        methodReturnType = resolvedMethod.returnType();
                        methodParamTypes = new ArrayList<>(resolvedMethod.parameterTypes());
                    } else if (BuiltinTypes.isString(recvType)) {
                        StringMethodRegistry.Sig sig = StringMethodRegistry.stringMethodSignature(mc.methodName(), mc.arguments().size(),
                                methodParamTypes);
                        if (sig != null) {
                            methodReturnType = sig.returnType();
                            methodParamTypes = sig.parameterTypes();
                        }
                    } else if (TypeMetrics.isPrimitiveType(recvType) && "toString".equals(mc.methodName())
                            && mc.arguments().isEmpty()) {
                        // primitivo.toString(): o primitivo não tem classe —
                        // boxar e converter (String.valueOf) em vez de gerar
                        // um owner vazio no bytecode (ClassFormatError)
                        TypeEmitter.boxPrimitive(ops, recvType);
                        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
                        yield localIdx;
                    } else {
                        StringMethodRegistry.Sig osig = StringMethodRegistry.objectMethodSignature(mc.methodName(), mc.arguments().size());
                        if (osig != null) {
                            methodReturnType = osig.returnType();
                            methodParamTypes = osig.parameterTypes();
                        }
                    }
                    if (methodReturnType instanceof Type.UnknownType) {
                        // fall back to the lowering's own inference (list-get
                        // chains, user classes resolved through hierarchy)
                        Type inferred = ExpressionTyper.inferExprType(driver, mc, locals);
                        if (!(inferred instanceof Type.UnknownType)) {
                            methodReturnType = inferred;
                        }
                    }
                    localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), methodParamTypes, ops, owner, localIdx, locals);
                    KofCallKind callKind = KofCallKind.INSTANCE;
                    if (recvType instanceof Type.ClassType rt && driver.semanticAnalyzer != null) {
                        if (driver.semanticAnalyzer.isInterfaceType(rt.name())) {
                            callKind = KofCallKind.INTERFACE;
                        }
                    }
                    if (callKind == KofCallKind.INSTANCE && resolvedMethod != null && driver.semanticAnalyzer != null) {
                        String ownerName = resolvedMethod.ownerClass();
                        if (ownerName.contains("/")) ownerName = ownerName.substring(ownerName.lastIndexOf('/') + 1);
                        if (driver.semanticAnalyzer.isInterfaceType(ownerName)) {
                            callKind = KofCallKind.INTERFACE;
                        }
                    }
                    String runtimeMethod = BuiltinTypes.isString(recvType)
                            ? StringMethodRegistry.stringRuntimeMethod(mc.methodName()) : null;
                    // receiver de classe EXTERNA sem símbolo resolvido: última
                    // linha de defesa — assinatura vem do classpath, senão o
                    // descritor sairia errado (owner vazio / retorno Object)
                    if (resolvedMethod == null && runtimeMethod == null
                            && mc.receiver() != null && driver.currentDiagnostics != null) {
                        Type rt2 = driver.semanticAnalyzer != null
                                ? driver.semanticAnalyzer.getExpressionType(mc.receiver())
                                : Type.UnknownType.UNKNOWN;
                        if (!(rt2 instanceof Type.ClassType)) {
                            rt2 = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
                        }
                        if (rt2 instanceof Type.ClassType ct2 && !ct2.packageName().isEmpty()
                                && driver.externalClasspath != null
                                && driver.externalClasspath.knows(ct2.internalName())) {
                            ExternalClasspath.MethodSignature sig = driver.externalClasspath.resolveMethod(
                                    ct2.internalName(), mc.methodName(), mc.arguments().size());
                            if (sig != null) {
                                List<Type> formal = new ArrayList<>();
                                for (String d : sig.parameterDescriptors()) {
                                    formal.add(ExternalClasspath.typeFromDescriptor(d));
                                }
                                recvType = ct2;
                                methodParamTypes = formal;
                                methodReturnType = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                            }
                        }
                    }
                    // String.valueOf/Integer.valueOf/…: receiver é o NOME de
                    // um tipo builtin (não uma variável) — o identificador não
                    // empilha valor; mapeia para o owner JDK estático (sem
                    // isso o emit saía com owner "" → ClassFormatError)
                    if (recvType instanceof Type.UnknownType
                            && mc.receiver() instanceof IdentifierExpr brid
                            && driver.findLocalVar(brid.name(), locals) == null
                            && !brid.name().isEmpty()
                            && Character.isUpperCase(brid.name().charAt(0))) {
                        Type jdkOwner = switch (brid.name()) {
                            case "String" -> BuiltinTypes.STRING;
                            case "Int", "Integer" -> new Type.ClassType("java.lang", "Integer", List.of());
                            case "Long" -> new Type.ClassType("java.lang", "Long", List.of());
                            case "Float" -> new Type.ClassType("java.lang", "Float", List.of());
                            case "Double" -> new Type.ClassType("java.lang", "Double", List.of());
                            case "Bool", "Boolean" -> new Type.ClassType("java.lang", "Boolean", List.of());
                            default -> Type.UnknownType.UNKNOWN;
                        };
                        if (!(jdkOwner instanceof Type.UnknownType)) {
                            recvType = jdkOwner;
                            callKind = KofCallKind.STATIC;
                            if (methodParamTypes.size() == 1
                                    && methodParamTypes.get(0) instanceof Type.PrimitiveType) {
                                // valueOf(I) direto do JDK — sem boxing duplo
                                methodReturnType = BuiltinTypes.STRING;
                            }
                        }
                    }
                    ops.add(new KofCall(recvType,
                            runtimeMethod != null ? runtimeMethod : mc.methodName(),
                            methodParamTypes, methodReturnType, callKind));
                    if (methodReturnType instanceof Type.TypeVariable) {
                        Type effective = ExpressionTyper.inferExprType(driver, mc, locals);
                        if (TypeMetrics.isPrimitiveType(effective)) {
                            driver.emitErasureUnbox(ops, effective);
                        }
                    }
                } else {
                    if (("super".equals(mc.methodName()) || "driver".equals(mc.methodName()))
                            && driver.semanticAnalyzer != null && !owner.isEmpty()) {
                        // super(args): construtor da superclasse (Object quando
                        // a classe não tem extends). driver(args): delegação para
                        // outro construtor da própria classe — o alvo executa
                        // super() e os inicializadores de campo.
                        boolean delegation = "driver".equals(mc.methodName());
                        String targetInternal;
                        if (delegation) {
                            targetInternal = owner;
                        } else {
                            targetInternal = HierarchyResolver.findSuperClass(owner, driver.semanticAnalyzer);
                            if (targetInternal == null) targetInternal = "java/lang/Object";
                            targetInternal = targetInternal.replace('.', '/');
                        }
                        Type targetType = CompilerTypes.ownerTypeFromInternal(targetInternal, driver.semanticAnalyzer);
                        SymbolTable.ClassSymbol targetCs = driver.semanticAnalyzer.getClass(
                                targetInternal.substring(targetInternal.lastIndexOf('/') + 1));
                        SymbolTable.ConstructorSymbol ctor = null;
                        if (targetCs != null) {
                            SymbolTable.Symbol ctorSym = targetCs.members().resolve("<init>");
                            if (ctorSym instanceof SymbolTable.ConstructorSymbol c
                                    && c.parameterTypes().size() == mc.arguments().size()) {
                                ctor = c;
                            }
                        }
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                        ops.add(new KofLoadLocal(CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer), 0));
                        List<Type> ctorParamTypes;
                        if (ctor != null && ctor.parameterTypes().size() == mc.arguments().size()) {
                            ctorParamTypes = ctor.parameterTypes();
                        } else {
                            if (targetCs != null && driver.currentDiagnostics != null) {
                                // classe conhecida e nenhum construtor com essa
                                // aridade — erro em compile-time
                                SourcePosition p = mc.position();
                                driver.currentDiagnostics.error(p != null ? p.file() : "",
                                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                        (delegation ? "no constructor of '" : "no super constructor of '")
                                                + targetInternal.substring(targetInternal.lastIndexOf('/') + 1)
                                                + "' with " + mc.arguments().size() + " argument(s)",
                                        "SEM017");
                                yield localIdx;
                            }
                            ctorParamTypes = argTypes;
                        }
                        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                        ops.add(new KofCall(targetType, "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                        yield localIdx;
                    }
                    SymbolTable.MethodSymbol selfMethod = driver.semanticAnalyzer != null
                            ? driver.semanticAnalyzer.getResolvedMethod(mc) : null;
                    if (selfMethod != null && !owner.isEmpty()
                            && !"<init>".equals(selfMethod.name())
                            && selfMethod.ownerClass() != null) {
                        Type ownerType = CompilerTypes.ownerTypeFromInternal(selfMethod.ownerClass(), driver.semanticAnalyzer);
                        ops.add(new KofLoadLocal(ownerType, 0));
                        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), selfMethod.parameterTypes(),
                                ops, owner, localIdx, locals);
                        ops.add(new KofCall(ownerType, mc.methodName(), selfMethod.parameterTypes(),
                                selfMethod.returnType(), KofCallKind.INSTANCE));
                        yield localIdx;
                    }
                    SymbolTable.ClassSymbol cs = driver.semanticAnalyzer != null ? driver.semanticAnalyzer.getClass(mc.methodName()) : null;
                    if (cs != null) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                        SymbolTable.ConstructorSymbol ctor = null;
                        SymbolTable.Symbol ctorSym = cs.members().resolve("<init>");
                        if (ctorSym instanceof SymbolTable.ConstructorSymbol ctorSingle) ctor = ctorSingle;
                        ops.add(new KofNewObject(cs.type(), argTypes));
                        ops.add(new KofDup());
                        List<Type> ctorParamTypes = (ctor != null
                                && ctor.parameterTypes().size() == mc.arguments().size())
                                ? ctor.parameterTypes() : argTypes;
                        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                        ops.add(new KofCall(cs.type(), "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                    } else {
                        IRLocalVariable lambdaVar = driver.findLocalVar(mc.methodName(), locals);
                        if (lambdaVar != null && lambdaVar.type() instanceof Type.FunctionType lft) {
                            if (lft.className() == null) {
                                // bug 8: valor de TIPO DE FUNÇÃO DECLARADO (param
                                // (s: (Int) -> Int), sem classe sintética). Todas
                                // as lambdas da assinatura implementam a interface
                                // sintética — invoca via INVOKEINTERFACE.
                                localIdx = ExpressionLowerer.emitExpression(driver, new IdentifierExpr(mc.position(), mc.methodName()),
                                        ops, owner, localIdx, locals);
                                List<Type> argTypes = new ArrayList<>();
                                for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                                localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), lft.parameterTypes(),
                                        ops, owner, localIdx, locals);
                                Type iface = driver.lambdaInterfaceType(lft);
                                ops.add(new KofCall(iface, "invoke", argTypes, lft.returnType(),
                                        KofCallKind.INTERFACE));
                            } else {
                            localIdx = ExpressionLowerer.emitExpression(driver, new IdentifierExpr(mc.position(), mc.methodName()),
                                    ops, owner, localIdx, locals);
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), lft.parameterTypes(), ops, owner, localIdx, locals);
                            Type invokeOwner = new Type.ClassType("", lft.className(), List.of());
                            ops.add(new KofCall(invokeOwner, "invoke", argTypes, lft.returnType(), KofCallKind.INSTANCE));
                            }
                        } else {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                            Type returnType = Type.UnknownType.UNKNOWN;
                            if (driver.currentUnit != null) {
                                for (AstNode d : driver.currentUnit.declarations()) {
                                    if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                                        returnType = CompilerTypes.resolveWithTypeParams(fn.returnType(), fn.typeParameters(), driver.currentUnit, driver.semanticAnalyzer);
                                        List<Type> fnTypes = fn.parameters().stream()
                                                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), fn.typeParameters(), driver.currentUnit, driver.semanticAnalyzer)).toList();
                                        boolean hasDefaults = fn.parameters().stream()
                                                .anyMatch(p -> p.defaultExpression() != null);
                                        if (hasDefaults && mc.arguments().size() < fnTypes.size()) {
                                            argTypes = fnTypes.subList(0, mc.arguments().size());
                                        } else {
                                            argTypes = fnTypes;
                                        }
                                        break;
                                    }
                                }
                            }
                            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), argTypes, ops, owner, localIdx, locals);
                            ops.add(new KofCall(CompilerTypes.mainClassType(driver.currentModule), mc.methodName(), argTypes, returnType, KofCallKind.FUNCTION));
                            Type effective = ExpressionTyper.inferExprType(driver, mc, locals);
                            if (returnType instanceof Type.TypeVariable && TypeMetrics.isPrimitiveType(effective)) {
                                driver.emitErasureUnbox(ops, effective);
                            }
                        }
                    }
                }
                yield localIdx;
            }
            case AssignmentExpr ae -> {
                if (ae.target() instanceof IdentifierExpr ie && !owner.isEmpty()) {
                    boolean isLocal = false;
                    for (int i = locals.size() - 1; i >= 0; i--) {
                        if (locals.get(i).name().equals(ie.name())) { isLocal = true; break; }
                    }
                    if (!isLocal) {
                        String className = owner.substring(owner.lastIndexOf('/') + 1);
                        SymbolTable.Symbol fieldSym = driver.semanticAnalyzer != null
                                ? HierarchyResolver.resolveFieldInHierarchy(className, ie.name(), driver.semanticAnalyzer) : null;
                        if (fieldSym != null
                                && (fieldSym instanceof SymbolTable.FieldSymbol
                                || (fieldSym instanceof SymbolTable.MethodSymbol ms
                                        && ms.parameterTypes().isEmpty()))) {
                            Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
                            ops.add(new KofLoadLocal(ownerType, 0));
                            String op = ae.operator();
                            if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                                    || "/=".equals(op) || "%=".equals(op)
                                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                                ops.add(new KofLoadField(ownerType, ie.name(), fieldSym.type()));
                            }
                            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                            if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                                    || "/=".equals(op) || "%=".equals(op)
                                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                                KofBinaryOp binOp = switch (op) {
                                    case "+=" -> KofBinaryOp.ADD;
                                    case "-=" -> KofBinaryOp.SUB;
                                    case "*=" -> KofBinaryOp.MUL;
                                    case "/=" -> KofBinaryOp.DIV;
                                    case "%=" -> KofBinaryOp.MOD;
                                    case "&=" -> KofBinaryOp.AND;
                                    case "|=" -> KofBinaryOp.OR;
                                    case "^=" -> KofBinaryOp.XOR;
                                    default -> KofBinaryOp.ADD;
                                };
                                ops.add(new KofBinary(binOp, fieldSym.type()));
                            }
                            ops.add(new KofStoreField(ownerType, ie.name(), fieldSym.type()));
                            yield localIdx;
                        }
                    }
                }
                if (ae.target() instanceof FieldAccessExpr fa) {
                    if (fa.receiver() instanceof IdentifierExpr rid && driver.semanticAnalyzer != null
                            && driver.semanticAnalyzer.getClass(rid.name()) != null) {
                        // Static field store: Class.field = value.
                        SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(rid.name());
                        SymbolTable.Symbol fs = HierarchyResolver.resolveFieldInHierarchy(cs.name(), fa.fieldName(), driver.semanticAnalyzer);
                        if (fs instanceof SymbolTable.FieldSymbol fld) {
                            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                            ops.add(new KofPutStatic(cs.type(), fa.fieldName(), fld.type()));
                            yield localIdx;
                        }
                    }
                    Type faRecvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
                    if (KofUi.isWindow(faRecvType) && "title".equals(fa.fieldName())) {
                        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_window_set_title", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isLabel(faRecvType) && "text".equals(fa.fieldName())) {
                        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_label_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isLabel(faRecvType) && "fontSize".equals(fa.fieldName())) {
                        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_label_set_font_size", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isLabel(faRecvType) && "bold".equals(fa.fieldName())) {
                        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_label_set_bold", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.BOOL),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isLabel(faRecvType) && "color".equals(fa.fieldName())) {
                        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_label_set_color", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isWindow(faRecvType) && "theme".equals(fa.fieldName())) {
                        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_window_set_theme", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isButton(faRecvType) && "text".equals(fa.fieldName())) {
                        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_button_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isInput(faRecvType) && "text".equals(fa.fieldName())) {
                        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_input_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isComponent(faRecvType) && "state".equals(fa.fieldName())) {
                        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_component_state_set", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    Type recvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
                    String faOp = ae.operator();
                    if ("+=".equals(faOp) || "-=".equals(faOp) || "*=".equals(faOp)
                            || "/=".equals(faOp) || "%=".equals(faOp)
                            || "&=".equals(faOp) || "|=".equals(faOp) || "^=".equals(faOp)) {
                        ops.add(new KofDup());
                        ops.add(new KofLoadField(ExpressionTyper.inferExprType(driver, fa.receiver(), locals), fa.fieldName(),
                                Type.UnknownType.UNKNOWN));
                    }
                    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                    Type fieldType = Type.UnknownType.UNKNOWN;
                    if (recvType instanceof Type.ClassType ct) {
                        SymbolTable.Symbol fs = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
                        if (fs != null) fieldType = fs.type();
                        else if (!ct.packageName().isEmpty() && driver.externalClasspath != null
                                && driver.externalClasspath.knows(ct.internalName())) {
                            String desc = driver.externalClasspath.resolveFieldType(
                                    ct.internalName(), fa.fieldName());
                            if (desc != null) fieldType = ExternalClasspath.typeFromDescriptor(desc);
                        }
                    }
                    if ("+=".equals(faOp) || "-=".equals(faOp) || "*=".equals(faOp)
                            || "/=".equals(faOp) || "%=".equals(faOp)
                            || "&=".equals(faOp) || "|=".equals(faOp) || "^=".equals(faOp)) {
                        KofBinaryOp binOp = switch (faOp) {
                            case "+=" -> KofBinaryOp.ADD;
                            case "-=" -> KofBinaryOp.SUB;
                            case "*=" -> KofBinaryOp.MUL;
                            case "/=" -> KofBinaryOp.DIV;
                            case "%=" -> KofBinaryOp.MOD;
                            case "&=" -> KofBinaryOp.AND;
                            case "|=" -> KofBinaryOp.OR;
                            case "^=" -> KofBinaryOp.XOR;
                            default -> KofBinaryOp.ADD;
                        };
                        ops.add(new KofBinary(binOp, fieldType));
                    }
                    ops.add(new KofStoreField(recvType, fa.fieldName(), fieldType));
                    yield localIdx;
                }
                if (ae.target() instanceof ArrayAccessExpr aa) {
                    localIdx = ExpressionLowerer.emitExpression(driver, aa.receiver(), ops, owner, localIdx, locals);
                    localIdx = ExpressionLowerer.emitExpression(driver, aa.index(), ops, owner, localIdx, locals);
                    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                    Type recvType = ExpressionTyper.inferExprType(driver, aa.receiver(), locals);
                    Type elemType = Type.arrayElementType(recvType);
                    // valor com primitivo ≠ slot (ex.: Int em Long[]) →
                    // converter no IR (I2L/L2I), senão o emit gera aastore/
                    // lastore com tipo errado e o verifier rejeita (o
                    // frame crash COMP002 em new Long[] + a[i] = i*3)
                    driver.emitPrimWidenNarrow(ops, ae.value(), elemType, locals);
                    ops.add(new KofArrayStore(elemType));
                    yield localIdx;
                }
                if (ae.target() instanceof IdentifierExpr ieBox) {
                    for (int i = locals.size() - 1; i >= 0; i--) {
                        if (locals.get(i).name().equals(ieBox.name()) && driver.boxFactory.isBoxType(locals.get(i).type())) {
                            IRLocalVariable boxLv = locals.get(i);
                            String op = ae.operator();
                            Type valType = driver.boxFactory.boxValueType(boxLv.type());
                            if ("+=".equals(op) && BuiltinTypes.isString(valType)) {
                                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                        List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                                        KofCallKind.STATIC));
                                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                                ops.add(new KofStoreField(boxLv.type(), "value", valType));
                            } else if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                                    || "/=".equals(op) || "%=".equals(op)
                                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                                driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), valType);
                                KofBinaryOp binOp = switch (op) {
                                    case "+=" -> KofBinaryOp.ADD;
                                    case "-=" -> KofBinaryOp.SUB;
                                    case "*=" -> KofBinaryOp.MUL;
                                    case "/=" -> KofBinaryOp.DIV;
                                    case "%=" -> KofBinaryOp.MOD;
                                    case "&=" -> KofBinaryOp.AND;
                                    case "|=" -> KofBinaryOp.OR;
                                    case "^=" -> KofBinaryOp.XOR;
                                    default -> KofBinaryOp.ADD;
                                };
                                ops.add(new KofBinary(binOp, valType));
                                driver.emitWideningIfNeeded(ops, valType, valType);
                                ops.add(new KofStoreField(boxLv.type(), "value", valType));
                            } else {
                                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                                driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), valType);
                                ops.add(new KofStoreField(boxLv.type(), "value", valType));
                            }
                            yield localIdx;
                        }
                    }
                }
                // composto sobre local: LHS empurrado ANTES do RHS (a ordem do
                // binário é lhs op rhs). O caminho antigo empurrava o RHS na
                // linha compartilhada e o LHS depois → `a -= 2` virava `2 - 10`
                // (bugs 2 e 3: resultado errado + stack extra no concat de s+=).
                if (ae.target() instanceof IdentifierExpr cie) {
                    IRLocalVariable targetLocal = null;
                    for (int i = locals.size() - 1; i >= 0; i--) {
                        if (locals.get(i).name().equals(cie.name())) { targetLocal = locals.get(i); break; }
                    }
                    if (targetLocal != null) {
                        String op = ae.operator();
                        if ("+=".equals(op) && BuiltinTypes.isString(targetLocal.type())) {
                            ops.add(new KofLoadLocal(targetLocal.type(), targetLocal.index()));
                            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                                    KofCallKind.STATIC));
                            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                                    KofCallKind.STATIC));
                            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
                            ops.add(new KofStoreLocal(targetLocal.type(), targetLocal.index()));
                            yield localIdx;
                        } else if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                                || "/=".equals(op) || "%=".equals(op)
                                || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                            ops.add(new KofLoadLocal(targetLocal.type(), targetLocal.index()));
                            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                            KofBinaryOp binOp = switch (op) {
                                case "+=" -> KofBinaryOp.ADD;
                                case "-=" -> KofBinaryOp.SUB;
                                case "*=" -> KofBinaryOp.MUL;
                                case "/=" -> KofBinaryOp.DIV;
                                case "%=" -> KofBinaryOp.MOD;
                                case "&=" -> KofBinaryOp.AND;
                                case "|=" -> KofBinaryOp.OR;
                                case "^=" -> KofBinaryOp.XOR;
                                default -> KofBinaryOp.ADD;
                            };
                            ops.add(new KofBinary(binOp, targetLocal.type()));
                            driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), targetLocal.type());
                            ops.add(new KofStoreLocal(targetLocal.type(), targetLocal.index()));
                            yield localIdx;
                        }
                    }
                }
                // atribuição simples: empurra o RHS e guarda no slot do local
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                if (ae.target() instanceof IdentifierExpr sie) {
                    for (int i = locals.size() - 1; i >= 0; i--) {
                        if (locals.get(i).name().equals(sie.name())) {
                            driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), locals.get(i).type());
                            // bug 15: `Object o; o = 7` — box primitivo p/ referência
                            if (driver.erasesToReference(locals.get(i).type())
                                    && TypeMetrics.isPrimitiveType(ExpressionTyper.inferExprType(driver, ae.value(), locals))) {
                                driver.emitErasureBox(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals));
                            }
                            ops.add(new KofStoreLocal(locals.get(i).type(), locals.get(i).index()));
                            yield localIdx;
                        }
                    }
                }
                ops.add(new KofStoreLocal(Type.UnknownType.UNKNOWN, localIdx));
                yield localIdx;
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