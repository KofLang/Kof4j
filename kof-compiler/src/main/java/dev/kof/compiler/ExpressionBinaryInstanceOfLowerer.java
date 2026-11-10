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
        // toType resolve imports ("View" + import → android.view.View); §776:
        // the analyzer-aware overload also qualifies a class of the module's
        // own package (`v instanceof DbNullBind` in `package kernel.db`).
        targetType = CompilerTypes.toType(ie.name(), driver.currentUnit, driver.semanticAnalyzer);
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
    // #1054: the narrowed nullable-primitive left's SLOT is the D-NULL-INTENT
    // boxed reference while its SEMANTIC type is the primitive — both cast
    // paths below must open the box before treating the stack as raw.
    boolean slotBoxedNullablePrim = false;
    if (bin.left() instanceof IdentifierExpr lie0) {
        IRLocalVariable lv0 = driver.findLocalVar(lie0.name(), locals);
        if (lv0 != null && lv0.type() instanceof Type.NullableType nt0
                && TypeMetrics.isPrimitiveType(nt0.inner())) {
            slotBoxedNullablePrim = true;
            CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops, lv0.type(),
                    nt0.inner());
            fromCastType = nt0.inner();
        }
    }
    if ("instanceof".equals(bin.operator())) {
        ops.add(new KofInstanceOf(targetType));
    } else if (TypeMetrics.isPrimitiveType(targetType)
            && (TypeMetrics.isPrimitiveType(fromCastType) || handleAsInt)) {
        // cast primitivo (x as Char/Int/…): conversão numérica,
        // NÃO checkcast (que exigiria um objeto na pilha)
        // a `Long?` SOURCE is the boxed wrapper (the PR #1071 face): open it
        // before the arithmetic paths; the narrowed VAR #1097 face below already
        // rewrote fromCastType to the inner, so this guard is inert there.
        Type fromT = CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops, fromCastType, targetType);
        driver.emitWideningIfNeeded(ops, fromT, targetType);
        // narrowing numérico (cast explícito): L2I, F2I, D2I,
        // F2L, D2L + os estreitos byte/short/char (#1087: o passo
        // largo→Int vem ANTES do i2b/i2s/i2c) — sem isso FP→Int gerava
        // bytecode inválido (bug 5) e Long→Byte VerifyError
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
        // §777: a `T?` primitive is already a boxed reference — re-boxing it
        // emitted `Long.valueOf(Ljava/lang/Long;)` (NoSuchMethodError).
        if (TypeMetrics.isPrimitiveType(fromCastType) && !TypeMetrics.isNullablePrimitive(fromCastType)
                && !TypeMetrics.isPrimitiveType(targetType)) {
            // §213: cast de PRIMITIVO → REFERÊNCIA (`i as Object`, `7 as Object`)
            // emitia o primitivo cru seguido de checkcast → VerifyError
            // ("Bad type on operand stack" — um int não é assignable a
            // referência). Boxa primeiro (mirror `var o: Object = 7` que já
            // faz kof_box via StatementLowerer). JS/Native são untyped — o
            // kof_box é identidade lá.
            // #1097: MAS um fromType Nullable(primitivo) (`score() as Object`
            // com score(): Int?) já deixou a REFERÊNCIA boxada na pilha
            // (D-NULL-INTENT — o descritor do método é o wrapper) — re-boxar
            // emitia `Integer.valueOf(Integer)` (NoSuchMethodError, a face
            // exata da issue). O checkcast Object aceita a referência como
            // está; o box é só para o primitivo CRU.
            driver.emitErasureBox(ops, fromCastType);
        }
        // §678 (D-SEM-REJECTS-1210): `x as T?` é o cast SUAVE SEMPRE-NULL — em
        // TODO alvo um valor que não é T vira `null`. Medido antes: o JVM
        // lançava `ClassCastException` e os três alvos value-typed devolviam o
        // OBJETO do tipo errado (o `KofCheckCast` é no-op lá). O desugar vive
        // no lowering COMPARTILHADO (`if (x instanceof T) x else null`, ops já
        // provadas), então a paridade dos 4 alvos é POR CONSTRUÇÃO e não há
        // runtime novo. Só a face NULÁVEL de REFERÊNCIA: o alvo nulável
        // primitivo tem o próprio caminho de unbox abaixo.
        if (castTarget instanceof Type.NullableType ntSoft
                && !TypeMetrics.isPrimitiveType(ntSoft.inner())) {
            Type softInner = ntSoft.inner();
            // O valor vai por um LOCAL temporário: `x` é avaliado UMA vez
            // (um `f() as T?` não pode chamar f() duas vezes) e cada RAMO do
            // if-expr tem ops próprias — o parser de if-expr do JS exige ramo
            // NÃO-VAZIO (o `DUP`/pilha-implícita não o satisfaz).
            int softTmp = localIdx;
            ops.add(new KofStoreLocal(fromCastType, softTmp));
            locals.add(new IRLocalVariable(softTmp, "#softcast", fromCastType));
            localIdx += TypeMetrics.isDoubleWidth(fromCastType) ? 2 : 1;
            LabelId keepLabel = LabelId.create();
            LabelId nullLabel = LabelId.create();
            LabelId softEnd = LabelId.create();
            ops.add(new KofLoadLocal(fromCastType, softTmp));
            ops.add(new KofInstanceOf(softInner));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofConditionalJump(KofComparison.NE, keepLabel, nullLabel));
            ops.add(new KofLabel(keepLabel));
            ops.add(new KofLoadLocal(fromCastType, softTmp));
            ops.add(new KofJump(softEnd));
            ops.add(new KofLabel(nullLabel));
            ops.add(KofLoadLiteral.ofNull());
            ops.add(new KofLabel(softEnd));
        } else {
            ops.add(new KofCheckCast(castTarget));
        }
        // #205: cast de REFERÊNCIA → PRIMITIVO (`obj as Int` com obj Object)
        // chegava aqui com targetType primitivo (from não é primitivo → o ramo
        // de conversão numérica não pega). O CHECKCAST vira a caixa (Integer),
        // mas sem unbox o stack fica `Integer` onde o consumidor espera `int`
        // (VerifyError) — e p/ Long/Double o COMPUTE_FRAMES da ASM crasha
        // (COMP002). kof_unbox = checkcast(boxed)+unboxMethod no JVM; é
        // identidade no interpretador (Script) e JS (stack já boxed) — o
        // CHECKCAST de cima fica redundante (mesmo alvo, inofensivo).
        if (TypeMetrics.isPrimitiveType(targetType)
                && (!TypeMetrics.isPrimitiveType(fromCastType) || slotBoxedNullablePrim)
                && !handleAsInt) {
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
