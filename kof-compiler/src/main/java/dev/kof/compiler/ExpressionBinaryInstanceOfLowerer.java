package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de BinaryExpr {@code instanceof}/{@code as} — extraído de
 * {@link ExpressionBinaryLowerer} para manter a classe abaixo do limite de
 * 600 linhas (regra "Small parts").
 */
final class ExpressionBinaryInstanceOfLowerer {

    private ExpressionBinaryInstanceOfLowerer() {}

    static int lower(CompilerDriver driver, BinaryExpr bin, List<KofOperation> ops,
                     String owner, int localIdx, List<IRLocalVariable> locals) {
    Type targetType = Type.UnknownType.UNKNOWN;
    if (bin.right() instanceof IdentifierExpr ie) {
        // toType resolve imports ("View" + import → android.view.View)
        targetType = CompilerTypes.toType(ie.name(), driver.currentUnit);
    }
    // §356/#295 (família §355): `x as T[]` / `o instanceof T` baixavam o
    // leaf fantasma ClassType("","T") → `checkcast [LT;` (NoClassDefFoundError
    // "T" em runtime). O alvo do cast passa pelos type-params DO ESCOPO em
    // lowering (driver.currentTypeParams) — cada leaf que é type-param vira
    // TypeVariable e apaga para o bound (Object), como o descriptor do campo.
    if (!driver.currentTypeParams.isEmpty()) {
        targetType = TypeParams.rewrite(targetType, n ->
                TypeParams.variable(n, driver.currentTypeParams, driver.currentUnit,
                        driver.semanticAnalyzer));
    }
    Type fromCastType = ExpressionTyper.inferExprType(driver, bin.left(), locals);
    // #293: primitivo → STRING (`42 as String`) caia no ramo §213 de box +
    // CHECKCAST java/lang/String — o boxed (Integer/Boolean/...) NAO e String
    // -> ClassCastException em runtime no JVM (JS/Script stringificam = oracle
    // da issue). stringify = descer pela MESMA rota do `x + ""` (valueOf nos 4
    // alvos). ANTES de emitir left (a recursao emite a sua propria vez).
    if ("as".equals(bin.operator()) && BuiltinTypes.isString(targetType)
            && TypeMetrics.isPrimitiveType(fromCastType)) {
        return ExpressionBinaryLowerer.lower(driver, new BinaryExpr(bin.position(), "+",
                bin.left(), new LiteralExpr(bin.position(), ConcreteLiteralKind.STRING, "")),
                ops, owner, localIdx, locals);
    }
    localIdx = ExpressionLowerer.emitExpression(driver, bin.left(), ops, owner, localIdx, locals);
    // UIW050: handle de UI/mídia APAGA para int no runtime (JvmTypeMapper
    // .toDescriptor → "I"). `label as Int` é IDENTITY, não checkcast — um
    // CHECKCAST sobre um valor int é inválido e derrubava o verifier
    // ("Bad type on operand stack") em qualquer função que monta UI.
    boolean handleAsInt = ExpressionBinaryPredicates.isIntPrimitive(targetType)
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
}
