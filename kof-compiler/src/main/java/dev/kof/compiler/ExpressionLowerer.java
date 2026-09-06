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
                yield ExpressionMethodCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
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