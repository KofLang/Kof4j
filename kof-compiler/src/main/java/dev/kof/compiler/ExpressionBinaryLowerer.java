package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de BinaryExpr (case do emitExpression).
 */
public final class ExpressionBinaryLowerer {

    private ExpressionBinaryLowerer() {}

    /** Unknown OU Nullable(Unknown): o valor pode ser null (get sem pin). */
    private static boolean isMaybeNullType(Type t) {
        return t instanceof Type.UnknownType
                || (t instanceof Type.NullableType nt && nt.inner() instanceof Type.UnknownType);
    }

    /**
     * D-NULL-INTENT/N1: primitivo NÃO-nullable nunca é null (fold de sempre);
     * `Nullable(primitivo)` só entra aqui quando o valor é CRU por natureza
     * (map-miss, SG-008 — nunca é o `T?` boxed genuíno de uma função/local).
     */
    private static boolean isFoldableNeverNullPrim(CompilerDriver driver, ExpressionNode e, Type t,
            List<IRLocalVariable> locals) {
        if (t instanceof Type.PrimitiveType pt && !Type.isVoid(pt)) return true;
        return t instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType
                && CompilerComparisons.isCollectionMissSource(driver, e, locals);
    }

    /** Int (ou Nullable(Int)) — alvo de cast que handle de UI/mídia satisfaz. */
    private static boolean isIntPrimitive(Type t) {
        if (t instanceof Type.NullableType nt) return isIntPrimitive(nt.inner());
        return t instanceof Type.PrimitiveType pt
                && ("int".equals(pt.name()) || "Int".equals(pt.name()));
    }

    /** §167: bitwise inteiro `& | ^` (o `&&`/`||` lógico já saiu antes). */
    private static boolean isBitwiseOp(String op) {
        return "&".equals(op) || "|".equals(op) || "^".equals(op);
    }

    /** §167: shift inteiro `<< >> >>>`. */
    private static boolean isShiftOp(String op) {
        return "<<".equals(op) || ">>".equals(op) || ">>>".equals(op);
    }

    static int lower(CompilerDriver driver, BinaryExpr bin, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals) {
if ("instanceof".equals(bin.operator()) || "as".equals(bin.operator())) {
    localIdx = ExpressionLowerer.emitExpression(driver, bin.left(), ops, owner, localIdx, locals);
    Type targetType = Type.UnknownType.UNKNOWN;
    if (bin.right() instanceof IdentifierExpr ie) {
        // toType resolve imports ("View" + import → android.view.View)
        targetType = CompilerTypes.toType(ie.name(), driver.currentUnit);
    }
    Type fromCastType = ExpressionTyper.inferExprType(driver, bin.left(), locals);
    // UIW050: handle de UI/mídia APAGA para int no runtime (JvmTypeMapper
    // .toDescriptor → "I"). `label as Int` é IDENTITY, não checkcast — um
    // CHECKCAST sobre um valor int é inválido e derrubava o verifier
    // ("Bad type on operand stack") em qualquer função que monta UI.
    boolean handleAsInt = isIntPrimitive(targetType)
            && (KofUi.isUiType(fromCastType) || KofMedia.isHandleType(fromCastType));
    if ("instanceof".equals(bin.operator())) {
        ops.add(new KofInstanceOf(targetType));
    } else if (TypeMetrics.isPrimitiveType(targetType)
            && (TypeMetrics.isPrimitiveType(fromCastType) || handleAsInt)) {
        // cast primitivo (x as Char/Int/…): conversão numérica,
        // NÃO checkcast (que exigiria um objeto na pilha)
        Type fromT = fromCastType;
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
        // bug 127: cast para TIPO-FUNÇÃO (`x as () -> Int`) — o alvo não é
        // uma classe; o valor em runtime é uma lambda que implementa a
        // interface SAM da assinatura. O checkcast vai para a interface
        // sintética (a mesma que o call site usa no dispatch), não p/ "?".
        Type castTarget = targetType;
        if (targetType instanceof Type.FunctionType ft) {
            castTarget = CompilerLambdaClass.lambdaInterfaceType(driver, ft);
        }
        if (TypeMetrics.isPrimitiveType(fromCastType)
                && !TypeMetrics.isPrimitiveType(targetType)) {
            // §213: cast de PRIMITIVO → REFERÊNCIA (`i as Object`, `7 as Object`)
            // emitia o primitivo cru seguido de checkcast → VerifyError
            // ("Bad type on operand stack" — um int não é assignable a
            // referência). Boxa primeiro (mirror `var o: Object = 7` que já
            // faz kof_box via StatementLowerer). JS/Native são untyped — o
            // kof_box é identidade lá.
            driver.emitErasureBox(ops, fromCastType);
        }
        ops.add(new KofCheckCast(castTarget));
        // #205: cast de REFERÊNCIA → PRIMITIVO (`obj as Int` com obj Object)
        // chegava aqui com targetType primitivo (from não é primitivo → o ramo
        // de conversão numérica não pega). O CHECKCAST vira a caixa (Integer),
        // mas sem unbox o stack fica `Integer` onde o consumidor espera `int`
        // (VerifyError) — e p/ Long/Double o COMPUTE_FRAMES da ASM crasha
        // (COMP002). kof_unbox = checkcast(boxed)+unboxMethod no JVM; é
        // identidade no interpretador (Script) e JS (stack já boxed) — o
        // CHECKCAST de cima fica redundante (mesmo alvo, inofensivo).
        if (TypeMetrics.isPrimitiveType(targetType)
                && !TypeMetrics.isPrimitiveType(fromCastType) && !handleAsInt) {
            // kof_unbox: JVM faz CHECKCAST(boxed)+INVOKEVIRTUAL unboxMethod
            // com descriptor "()" + toDescriptor(returnType). Char é guardado
            // BOXED como Integer (§104b-ii): o return tem que ser INT (senão
            // emite Integer.intValue()C inexistente). No interpretador/JS é
            // identidade, então INT-preserving é seguro nos outros alvos
            // (Kof `char` é int-width na JVM por contrato).
            Type unboxResult = TypeMetrics.isCharType(targetType) ? Type.PrimitiveType.INT : targetType;
            ops.add(new KofCall(unboxResult, "kof_unbox",
                    List.of(TypeMetrics.boxedTypeFor(targetType)), unboxResult,
                    KofCallKind.FUNCTION));
        }
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
        // D-NULL-INTENT/N1: `five() + 1` — accType/rightType Nullable(primitivo)
        // GENUÍNO (isNumeric desempacota) já está na pilha como referência
        // boxed; desembrulha ANTES de widen/operar (§0 — Map.get() cru fica de
        // fora via isGenuineNullablePrimitive).
        if (CompilerComparisons.isGenuineNullablePrimitive(driver, cursor, accType, locals)) {
            driver.emitErasureUnbox(ops, ((Type.NullableType) accType).inner());
        }
        driver.emitWideningIfNeeded(ops, accType, commonType);
        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
        if (CompilerComparisons.isGenuineNullablePrimitive(driver, be.right(), rightType, locals)) {
            driver.emitErasureUnbox(ops, ((Type.NullableType) rightType).inner());
        }
        driver.emitWideningIfNeeded(ops, rightType, commonType);
        ops.add(new KofBinary(TypeMetrics.mapArithmeticOp(be.operator()), commonType));
        accType = commonType;
    } else if (isBitwiseOp(be.operator())
            && TypeMetrics.isInteger(accType) && TypeMetrics.isInteger(rightType)) {
        // §167: bitwise `& | ^` com Int e Long misturados. A promoção binária
        // do JVM eleva AMBOS ao tipo comum (long se qualquer lado for long);
        // sem o widening, `long & int` virava `land` sobre um int (VerifyError)
        // e `int & long` truncava o long p/ 32 bits no Native/Script (resultado
        // errado). `operandType` = tipo comum p/ os 4 targets.
        Type commonInt = TypeMetrics.commonNumericType(accType, rightType);
        if (!TypeMetrics.isInteger(commonInt)) commonInt = Type.PrimitiveType.INT;
        driver.emitWideningIfNeeded(ops, accType, commonInt);
        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
        driver.emitWideningIfNeeded(ops, rightType, commonInt);
        KofBinaryOp bitOp = switch (be.operator()) {
            case "&" -> KofBinaryOp.AND;
            case "|" -> KofBinaryOp.OR;
            default -> KofBinaryOp.XOR;
        };
        ops.add(new KofBinary(bitOp, commonInt));
        accType = commonInt;
    } else if (isShiftOp(be.operator())
            && TypeMetrics.isInteger(accType) && TypeMetrics.isInteger(rightType)) {
        // §167: shift `<< >> >>>`. O tipo do resultado é o tipo PROMOVIDO do
        // operando ESQUERDO (JLS 15.19), não o tipo comum: `int << long` tem
        // tipo int. O deslocamento é sempre int no JVM (`lshl`/`ishl` tomam
        // (long,int)/(int,int)) — um RHS long precisa de L2I, senão VerifyError.
        Type resultType = "long".equals(TypeMetrics.primitiveName(accType)) ? Type.PrimitiveType.LONG : Type.PrimitiveType.INT;
        driver.emitWideningIfNeeded(ops, accType, resultType);
        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
        driver.emitPrimNarrow(ops, rightType, Type.PrimitiveType.INT);
        KofBinaryOp shiftOp = switch (be.operator()) {
            case "<<" -> KofBinaryOp.SHL;
            case ">>" -> KofBinaryOp.SHR;
            default -> KofBinaryOp.USHR;
        };
        ops.add(new KofBinary(shiftOp, resultType));
        accType = resultType;
    } else if ("+".equals(be.operator())
            && (Type.isString(accType) || Type.isString(rightType)
                    // §244/#267: operandos NÃO numéricos e NÃO String (genéricos
                    // apagados p/ `Object`, Unknown, referências): o contrato
                    // Kof `String + anything → String` manda stringificar os
                    // dois lados e concatenar. Sem este ramo o `else` final
                    // emitia `ADD` sobre referências → `iadd` → VerifyError.
                    || (!TypeMetrics.isNumeric(accType) && !TypeMetrics.isNumeric(rightType)))) {
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
        // §125-ext (B2, 12/09): o guard do box usa isPrimitiveType (que
        // DESEMPACOTA Nullable) mas o ternário do arg do valueOf usava
        // `instanceof PrimitiveType` (que NÃO desempacota) → p/
        // `Nullable(Int)` (ex.: `"a" + ni()` c/ Int? ni()) o boxPrimitive já
        // STRINGUIFICOU o valor (kof_int_to_string) e o valueOf externo
        // stringuificava DE NOVO o ponteiro da String = lixo. Mesmo guard
        // nos dois lados: se o box já rodou, o valueOf externo é no-op
        // (UNKNOWN).
        // D-NULL-INTENT/N1: `Nullable(primitivo)` GENUÍNO (ex. `ni()`) já
        // chega AQUI boxed de verdade (Integer/Long/...) — boxar de novo é
        // `Integer.valueOf(int)` sobre uma referência = VerifyError. Só o
        // valor CRU do map-miss (SG-008, mesmo shape de tipo) ainda precisa
        // do box aqui — distinguido por forma de chamada, não por tipo (§0).
        boolean accGenuineNullablePrim = accType instanceof Type.NullableType antNt
                && antNt.inner() instanceof Type.PrimitiveType
                && !CompilerComparisons.isCollectionMissSource(driver, be.left(), locals);
        boolean accStringified = !Type.isString(accType) && TypeMetrics.isPrimitiveType(accType)
                && !accGenuineNullablePrim;
        if (accStringified) TypeEmitter.boxPrimitive(ops, accType);
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(driver.target.isNative() && !accStringified && !Type.isString(accType)
                        && !(accType instanceof Type.PrimitiveType)
                        ? accType : Type.UnknownType.UNKNOWN),
                BuiltinTypes.STRING, KofCallKind.STATIC));
        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
        boolean rightGenuineNullablePrim = rightType instanceof Type.NullableType rntNt
                && rntNt.inner() instanceof Type.PrimitiveType
                && !CompilerComparisons.isCollectionMissSource(driver, be.right(), locals);
        boolean rightStringified = !Type.isString(rightType) && TypeMetrics.isPrimitiveType(rightType)
                && !rightGenuineNullablePrim;
        if (rightStringified) TypeEmitter.boxPrimitive(ops, rightType);
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(driver.target.isNative() && !rightStringified && !Type.isString(rightType)
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
                    && isFoldableNeverNullPrim(driver, be.left(), accType, locals))
                || (be.left() instanceof LiteralExpr ll
                    && ll.kind() == ConcreteLiteralKind.NULL
                    && isFoldableNeverNullPrim(driver, be.right(), rightType, locals)))) {
        // primitivo (não-nullable, ou Nullable(primitivo) vindo de map-miss —
        // SG-008, sempre cru/default-on-miss) nunca é null: == → false,
        // != → true. D-NULL-INTENT/N1: um Nullable(primitivo) GENUÍNO (ex.
        // `ni()`) NÃO entra mais aqui — cai no ramo geral abaixo, que faz
        // comparação de referência de verdade (§0: distinção por forma de
        // chamada, não por tipo).
        // (o lado não-nulo já está na pilha — descarta; 2 slots = POP2,
        //  SG-020/bug 79 — POP de Double/Long deixa o 2º slot e o
        //  verificador rejeita: VerifyError mascarado de "JavaFX")
        Type popT = be.right() instanceof LiteralExpr rl2
                && rl2.kind() == ConcreteLiteralKind.NULL ? accType : rightType;
        ops.add(TypeMetrics.isDoubleWidth(popT) ? new KofPop2() : new KofPop());
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
            && (Type.isString(accType) || Type.isString(rightType))) {
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
        // Unknown/Nullable(Unknown) vs primitivo (get de mapOf() sem pin
        // vs int): o lado nullable só pode ser null (miss) → referência
        // com o primitivo boxado (SG-008/bug 87; espelha Objects.equals).
        // O box do lado primitivo acontece ANTES do emit do lado oposto
        // (boxa o valor no topo da pilha, na ordem certa).
        boolean isEqNe = "==".equals(be.operator()) || "!=".equals(be.operator());
        boolean boxLeftNow = isEqNe
                && isMaybeNullType(rightType) && TypeMetrics.isPrimitiveType(accType)
                && !CompilerComparisons.isGenuineNullablePrimitive(driver, be.left(), accType, locals);
        // D-NULL-INTENT/N1: `five() + 1` — operador aritmético/relacional
        // (NÃO ==/!=, que compara por referência) sobre um Nullable(primitivo)
        // GENUÍNO precisa desembrulhar ANTES do operador — o valor na pilha já
        // é referência boxed, não o primitivo cru que ADD/SUB/etc. esperam.
        boolean leftGenuineNullablePrim = !isEqNe
                && CompilerComparisons.isGenuineNullablePrimitive(driver, be.left(), accType, locals);
        if (boxLeftNow) {
            TypeEmitter.boxPrimitive(ops, accType);
        } else if (leftGenuineNullablePrim) {
            driver.emitErasureUnbox(ops, ((Type.NullableType) accType).inner());
        }
        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
        boolean rightGenuineNullablePrim = !isEqNe
                && CompilerComparisons.isGenuineNullablePrimitive(driver, be.right(), rightType, locals);
        if (rightGenuineNullablePrim) {
            driver.emitErasureUnbox(ops, ((Type.NullableType) rightType).inner());
        }
        Type operandType = leftGenuineNullablePrim ? ((Type.NullableType) accType).inner() : accType;
        if (rightGenuineNullablePrim && !leftGenuineNullablePrim) {
            operandType = ((Type.NullableType) rightType).inner();
        }
        if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                && (driver.isNullLiteral(be.left()) || driver.isNullLiteral(be.right()))) {
            Type other = driver.isNullLiteral(be.left()) ? rightType : accType;
            operandType = (other instanceof Type.ClassType || other instanceof Type.ArrayType
                    || other instanceof Type.TypeVariable || other instanceof Type.NullableType)
                    ? other : new Type.ClassType("java.lang", "Object", List.of());
        } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                && ((isMaybeNullType(accType) && TypeMetrics.isPrimitiveType(rightType))
                    || (isMaybeNullType(rightType) && TypeMetrics.isPrimitiveType(accType)))) {
            operandType = new Type.ClassType("java.lang", "Object", List.of());
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