package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de BinaryExpr (case do emitExpression).
 */
final class ExpressionBinaryLowerer {

    private ExpressionBinaryLowerer() {}

    static int lower(CompilerDriver driver, BinaryExpr bin, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals) {
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
    return localIdx;
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
    return localIdx;
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
            return localIdx;
        }
        Type commonType = TypeMetrics.commonNumericType(accType, rightType);
        if (!driver.fpSupportedOnNative(commonType, be.position())) {
            return localIdx;
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
        // suportam — FLT001 fechado; o return incondicional
        // descartava o operando: "a=" + 1.5 virava só "a=").
        if (((Type.isString(accType) && TypeMetrics.isFloatingPoint(rightType))
                || (Type.isString(rightType) && TypeMetrics.isFloatingPoint(accType)))
                && !driver.fpSupportedOnNative(TypeMetrics.isFloatingPoint(rightType) ? rightType : accType,
                        be.position())) {
            return localIdx;
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
        } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                && accType instanceof Type.UnknownType && rightType instanceof Type.UnknownType) {
            // ambos UnknownType (ex.: `var a = null; var b = null`): comparação
            // de REFERÊNCIA (if_acmp*). Unknown só surge de null/untyped-get —
            // nunca de int inferido (que dá INT) — então acmp é seguro e casa
            // com o interpretador (Objects.equals: null==null → true). Sem
            // isso, Unknown caía no default INT → if_icmpeq sobre null →
            // VerifyError (bug 36).
            operandType = new Type.ClassType("java.lang", "Object", List.of());
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
return localIdx;
    }
}