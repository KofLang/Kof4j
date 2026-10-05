package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de BinaryExpr (case do emitExpression).
 */
public final class ExpressionBinaryLowerer {

    private ExpressionBinaryLowerer() {}

    /** Unknown OU Nullable(Unknown): o valor pode ser null (get sem pin). */
    /**
     * Stringifica um operando de concatenação. D-PRINT (#168, manterdora
     * 15/09): um `Char` vira o CARÁTER ("A"), nunca o code point ("65") — a
     * face numérica de §216 face 2 está SUPERSEDED (regra 4). Usa o overload
     * `String.valueOf(char)` (descritor (C)) com o valor int-width já na
     * pilha, sem boxear; nos 4 alvos o dispatch `valueOf(C)` é o mesmo de
     * `String.valueOf(c)` (§27). Os demais primitivos mantêm o box.
     */
    static void emitOperandToString(CompilerDriver driver, List<KofOperation> ops, Type type) {
        if (ProcessResultPrintGuard.refuseWholeResult(driver, type, "", 0, 0)) return;
        Type check = type instanceof Type.NullableType nt ? nt.inner() : type;
        boolean isChar = check instanceof Type.PrimitiveType p
                && "char".equals(Type.canonicalPrimitiveName(p.name()));
        if (isChar) {
            PrimitiveStringLowering.emitChar(driver, ops, type);
            return;
        }
        // D-NULL-INTENT (#278): `TypeMetrics.isPrimitiveType` desembrulha
        // Nullable — casava tanto o primitivo CRU (precisa de boxPrimitive
        // antes do valueOf) quanto um `Int?`/`Bool?` GENUÍNO (Commit B: já
        // chega boxed/aconst_null da chamada/local/map). Reboxar este
        // segundo caso chama Integer.valueOf(int) sobre uma REFERÊNCIA
        // (VerifyError JVM; NPE silenciosa no interpretador — achado em
        // `"a" + ni()` com `Int? ni() { return null }`). Native mantém o
        boolean stringified = !Type.isString(type) && (type instanceof Type.PrimitiveType pt3 && !Type.isVoid(pt3));
        // §519/#632 (JVM): handle BARE de kof.ui/midia e int-apagado (UIW050) —
        // mesma caixa do primitivo cru: boxa p/ Integer ANTES do valueOf(Object),
        // senao o verifier pega int cru em slot de referencia (`println(cor)`).
        // Nullable(handle) (get de chave ausente) chega FISICAMENTE boxed — nao
        // reboxa (mesma armadilha documentada do `Int?` acima).
        if (!stringified && driver.target == Target.JVM && !Type.isString(type)
                && type instanceof Type.ClassType && (KofUi.isUiType(type) || KofMedia.isHandleType(type))) {
            stringified = true;
        }
        if (driver.target == Target.JS
                && TypeMetrics.isFloatingPoint(
                        type instanceof Type.NullableType ntp ? ntp.inner() : type)) {
            // §264 (JS): Double/Float crus (Number no JS) — valueOf recebe o
            // tipo REAL p/ o emissor formatar no contrato do JDK; sem box.
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(type), BuiltinTypes.STRING, KofCallKind.STATIC));
            return;
        }
        if (stringified) TypeEmitter.boxPrimitive(ops, type);
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(driver.target.isNative() && !stringified && !Type.isString(type)
                        && !(type instanceof Type.PrimitiveType)
                        ? type : Type.UnknownType.UNKNOWN),
                BuiltinTypes.STRING, KofCallKind.STATIC));
    }

    static int lower(CompilerDriver driver, BinaryExpr bin, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals) {
        // D-TROOL (19/09): Kleene antes de qualquer caminho de `&&`/`||` — com
        // um `Troolean` num dos lados o resultado e tres-estado (caixa do
        // §295/§306) nos 4 alvos, incluindo o JS (a exclusao de alvo aqui nao
        // se aplica ao desugar; ela protege o IF_ICMP cru do caminho antigo).
        if ("&&".equals(bin.operator()) || "||".equals(bin.operator())) {
            int kl = CompilerComparisons.lowerTrooleanAndOr(driver, bin, ops, owner, localIdx, locals);
            if (kl >= 0) return kl;
        }
if ("instanceof".equals(bin.operator()) || "as".equals(bin.operator())) {
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
        return lower(driver, new BinaryExpr(bin.position(), "+",
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
// Short-circuit evaluation for || and &&:
// a || b → eval a; if true, jump to true_label; eval b; result = b
// a && b → eval a; if false, jump to false_label; eval b; result = b
        if (("||".equals(bin.operator()) || "&&".equals(bin.operator()))
                && driver.target != Target.JS) {
            LabelId trueLabel = LabelId.create();
            LabelId falseLabel = LabelId.create();
            LabelId endLabel = LabelId.create();
            // §306(a): `b || x` / `b && x` com `b: Bool?` — a truthiness passa
            // pelo rewrite do emitTruthinessJump (`b == true`, caminho de VALOR
            // null-safe); o IF_ICMPNE cru sobre o slot boxed (JVM) dava
            // VerifyError. #462: o RHS avaliado usa o MESMO rewrite — um RHS
            // `Bool?` chegava ao join como referência enquanto o outro arco
            // deixava int (VerifyError; `true && fb()`).
            ExpressionNode leftC = CompilerComparisons.nullableBoolTruthinessRewrite(
                    driver, bin.left(), locals);
            ExpressionNode rightC = CompilerComparisons.nullableBoolTruthinessRewrite(
                    driver, bin.right(), locals);
            localIdx = ExpressionLowerer.emitExpression(driver, leftC, ops, owner, localIdx, locals);
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            if ("||".equals(bin.operator())) {
                ops.add(new KofConditionalJump(KofComparison.NE, trueLabel, falseLabel));
            } else {
                ops.add(new KofConditionalJump(KofComparison.NE, falseLabel, trueLabel));
            }
    ops.add(new KofLabel(falseLabel));
    localIdx = ExpressionLowerer.emitExpression(driver, rightC, ops, owner, localIdx, locals);
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
        && !"instanceof".equals(be.operator())
        // D-TROOL: `&&`/`||` NAO achatam como filho-esquerdo — re-despachados
        // via emitExpression caem no gancho Kleene (ou no #487 p/ Bool puro);
        // no loop viravam KofBinaryOp.AND = iand sobre a caixa Boolean
        // (VerifyError medido 19/09 em `if (a && b)` com Troolean). No TOPO
        // (chain vazio) o flatten segue valendo: la o gancho/#487 ja
        // interceptaram, e parar deixaria cursor==bin -> recursao infinita.
        && (chain.isEmpty() || (!"&&".equals(be.operator()) && !"||".equals(be.operator())))) {
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
    // D-NULL-INTENT: bare primitivo NÃO-nullable dos dois lados — um
    // Nullable(primitivo) GENUÍNO (accType/rightType instanceof
    // NullableType) tem de cair no caminho null-safe mais abaixo
    // (RecordEqualityLowerer/`.equals()`, I6), nunca num if_icmp* cru
    // sobre a referência boxed (VerifyError) — TypeMetrics.isNumeric
    // desempacota Nullable, então não serve de guarda aqui sozinho.
    // Native é EXCEÇÃO (fase 2 da fila D-NULL-INTENT, DECISIONS.md — não
    // tocado): a representação de Nullable(primitivo) lá continua CRUA
    // (não boxed), então o desempacote antigo (TypeMetrics.isNumeric) é
    // seguro e necessário — `.equals()`/`java_lang_Integer_equals` não
    // existe no runtime nativo (achado na CI real, linker `undefined
    // reference`, ausente no Windows local sem `as`/`ld`).
    // D-NULL-INTENT: relacionais (<,<=,>,>=) sobre Nullable(primitivo) NÃO
    // têm caminho `.equals()` (Comparable não faz parte do I6) — precisam
    // desempacotar e comparar por VALOR primitivo cru, então SÃO elegíveis
    // aqui mesmo com um lado Nullable (o bloco abaixo já desempacota
    // accType/rightType antes do DCMPG/etc. — achado ao medir
    // ConformanceMatrixTest.conformanceCoreArithmetic, `d > 1.0` com `d`
    // vindo de Map.get: sem isto, caía no isRefOperand → if_acmp* contra um
    // double CRU não-boxado do lado direito → VerifyError). `==`/`!=`
    // continuam EXCLUÍDOS daqui mesmo com Nullable — ficam com o caminho
    // `.equals()` do I6 abaixo (nunca identidade de wrapper).
    boolean isRelationalOp = switch (be.operator()) {
        case "<", "<=", ">", ">=" -> true;
        default -> false;
    };
    boolean isNumericComparison = TypeMetrics.isComparisonOp(be.operator())
            && ((accType instanceof Type.PrimitiveType && TypeMetrics.isNumeric(accType)
                    && rightType instanceof Type.PrimitiveType && TypeMetrics.isNumeric(rightType))
                // §284-map (18/09): a familia de slot boxed SÓ vale p/ os
                // relacionais aqui — `==`/`!=` com Nullable(primitivo) ficam
                // o caminho I6 (RecordEqualityLowerer + kof_box_equals), que
                // e null-seguro (null==null -> true; sem isto o soft-unbox
                // do `nulleq` arrombava em vez de dar true/false).
                || (driver.target.isNative() && isRelationalOp
                    && TypeMetrics.isNumeric(accType) && TypeMetrics.isNumeric(rightType))
                || (isRelationalOp && ExpressionBinaryPredicates.isNullablePrimOrBarePrim(accType) && ExpressionBinaryPredicates.isNullablePrimOrBarePrim(rightType)
                    && TypeMetrics.isNumeric(accType) && TypeMetrics.isNumeric(rightType)));
    if ((isArithmetic || isNumericComparison)
            && TypeMetrics.isNumeric(accType) && TypeMetrics.isNumeric(rightType)) {
        // D-NULL-INTENT: aritmética exige o valor PRESENTE — um
        // Nullable(primitivo) GENUÍNO chega FISICAMENTE boxed agora
        // (return/local/Map do #278); sem desempacotar, IADD/etc. sobre a
        // referência é VerifyError (achado ao medir
        // KofInterpreterParityTest.printNullablePrimitiveNull, caso
        // `ni() + 1`). `null + 1` continua indefinido (NPE em runtime,
        // como Java) — narrowing explícito é responsabilidade do programa.
        if (accType instanceof Type.NullableType accNt) {
            // §284-map (18/09): SOFT no native — a caixa do slot abre, o cru
            // de variável/função passa cru, null dá o mesmo CCE honesto do
            // estrito. Era o SIGSEGV `nulleq`: unbox ESTRITO cego sobre o
            // null de get ausente.
            CompilerEmissionHelpers.emitErasureUnboxSoft(driver, ops, accNt.inner());
            accType = accNt.inner();
        }
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
        if (rightType instanceof Type.NullableType rightNt) {
            // §284-map: mesmo soft do lado esquerdo.
            CompilerEmissionHelpers.emitErasureUnboxSoft(driver, ops, rightNt.inner());
            rightType = rightNt.inner();
        }
        driver.emitWideningIfNeeded(ops, rightType, commonType);
        ops.add(new KofBinary(TypeMetrics.mapArithmeticOp(be.operator()), commonType));
        // §601: um relacional (`<`/`<=`/`>`/`>=`) sobre numéricos produz um
        // `Bool`, mas o tipo do OPERANDO (commonType) é o numérico — deixar
        // `accType` numérico aqui fazia um `&&`/`||` encadeado ver um operando
        // não-Bool e baixar para bitwise `&`/`|` no JS (sem short-circuit;
        // `i < end && b[i] != …` lia fora dos limites). Aritmética mantém o
        // tipo comum.
        accType = TypeMetrics.isComparisonOp(be.operator())
                ? Type.PrimitiveType.BOOL
                : commonType;
    } else if (ExpressionBinaryPredicates.isBitwiseOp(be.operator())
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
    } else if (ExpressionBinaryPredicates.isShiftOp(be.operator())
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
        emitOperandToString(driver, ops, accType);
        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
        emitOperandToString(driver, ops, rightType);
        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                BuiltinTypes.STRING, KofCallKind.FUNCTION));
        accType = BuiltinTypes.STRING;
    } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
            && ((be.right() instanceof LiteralExpr rl
                    && rl.kind() == ConcreteLiteralKind.NULL
                    && ((accType instanceof Type.PrimitiveType apt && !Type.isVoid(apt))
                        // §519/#632: handle BARE de kof.ui/midia (View/Color/...)
                        // e int-apagado (UIW050) — nunca null em alvo nenhum; o
                        // mesmo fold do primitivo cru (`==null`→false, `!=null`→
                        // true). Nullable(handle) (ex.: get de chave ausente) NAO
                        // entra aqui — e referencia de verdade (caminho abaixo).
                        || (accType instanceof Type.ClassType && (KofUi.isUiType(accType) || KofMedia.isHandleType(accType)))))
                || (be.left() instanceof LiteralExpr ll
                    && ll.kind() == ConcreteLiteralKind.NULL
                    && ((rightType instanceof Type.PrimitiveType rpt && !Type.isVoid(rpt))
                        || (rightType instanceof Type.ClassType && (KofUi.isUiType(rightType) || KofMedia.isHandleType(rightType))))))) {
        // D-NULL-INTENT: só dispara p/ primitivo NÃO-nullable de verdade
        // (`accType`/`rightType` bare PrimitiveType, sem desempacotar
        // NullableType) — um Nullable(primitivo) GENUÍNO cai no ramo
        // abaixo (referência se_acmp*, `NullablePrimitiveContractE2ETest`).
        // Antes usava TypeMetrics.isPrimitiveType (que desempacota Nullable)
        // e fold `Int? f() { return null }; f() == null` virava `false`
        // sempre — supersede §125 opção A (DECISIONS.md 15/09).
        // primitivo nunca é null: == → false, != → true
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
            && !driver.isNullLiteral(be.left()) && !driver.isNullLiteral(be.right())
            && (ExpressionBinaryPredicates.isRecordLike(accType, driver) || ExpressionBinaryPredicates.isRecordLike(rightType, driver)
                // D-SECRETS face 1: `Secret == Secret` é conteúdo constant-time
                // (KofRuntime$Secret.equals) — mesmo caminho null-safe do record.
                || KofSecurity.isSecretType(accType) || KofSecurity.isSecretType(rightType)
                || (!driver.target.isNative()
                    && (ExpressionBinaryPredicates.isNullablePrimLike(accType) || ExpressionBinaryPredicates.isNullablePrimLike(rightType))
                    && ExpressionBinaryPredicates.isNullablePrimOrBarePrim(accType) && ExpressionBinaryPredicates.isNullablePrimOrBarePrim(rightType))
                // §519/#632 (JVM): Nullable(handle) de slot boxed (get de map,
                // campo) cai no MESMO caminho `.equals` null-safe do
                // Nullable(primitivo) — a caixa fisica e Integer (UIW050);
                // sem isto, `m.get(k) == m.get(k)` descia p/ if_icmpeq sobre
                // referencias boxed (VerifyError medido).
                || (driver.target == Target.JVM
                    && (ExpressionBinaryPredicates.isNullableHandleLike(accType) || ExpressionBinaryPredicates.isNullableHandleLike(rightType))
                    && ExpressionBinaryPredicates.isNullablePrimOrBarePrimOrHandle(accType) && ExpressionBinaryPredicates.isNullablePrimOrBarePrimOrHandle(rightType))
                // §284-map: fase 2 D-NULL-INTENT no native SÓ para a familia
                // com caixa fisica de slot (int/char/short/byte/long): o
                // RecordEqualityLowerer guarda os dois lados e chama
                // `.equals` — o backend roteia p/ kof_box_equals (magic).
                // Double/Bool/Float crus ficam no caminho de sempre (identidade
                // == igualdade de numero), senão o .equals deles quebraria o
                // que hoje funciona.
                || (driver.target.isNative()
                    && (ExpressionBinaryPredicates.isBoxedPrimConsumer(accType) || ExpressionBinaryPredicates.isBoxedPrimConsumer(rightType))
                    && ExpressionBinaryPredicates.isNullablePrimOrBarePrim(accType) && ExpressionBinaryPredicates.isNullablePrimOrBarePrim(rightType)))) {
        // §262 / bug 11: `record == record` é igualdade de CONTEÚDO, null-safe
        // (Objects.equals). Desugaring em RecordEqualityLowerer (JS = chamada
        // p/ helper kofRecordEq; JVM/Script/Native = ternária com jumps). O
        // `record == null` literal NAO cai aqui — é comparação de referência
        // (ramo abaixo). Aqui só se aplica o `!=` UMA vez, p/ todos os targets.
        // D-NULL-INTENT (I6): Nullable(primitivo) GENUÍNO — dos dois lados
        // (ex. `a() == b()`) OU misto com primitivo CRU (ex. `m.get("a") ==
        // 1`, o lado cru nunca é null) — entram no MESMO caminho:
        // `.equals()` do wrapper (Integer/Long/...) já faz igualdade por
        // VALOR null-safe, driver exato de I6. O lado bare precisa chegar
        // BOXED no RecordEqualityLowerer (ele guarda os DOIS em temporários
        // Object): a esquerda já emitida boxa AQUI (antes do dispatch); a
        // direita boxa dentro do lowerer, logo após ser emitida.
        // EXCETO Native (fase 2 da fila D-NULL-INTENT, DECISIONS.md — não
        // tocado): `.equals()` de wrapper JDK não existe no runtime nativo
        // (`java_lang_Integer_equals`/`java_lang_Boolean_equals` —
        // `undefined reference` no linker, achado na CI real; ausente no
        // Windows local sem `as`/`ld`). Record continua passando por aqui
        // no Native (equals de classe usuário, já suportado antes do #278).
        if ((accType instanceof Type.PrimitiveType apt4 && !Type.isVoid(apt4))
                || ExpressionBinaryPredicates.isBareHandleErasedToInt(accType)) {
            // §284-map: no native o box do lado cru entra no par Object do
            // RecordEqualityLowerer via kof_box_* (TypeEmitter e bytecode
            // JVM-only).
            if (driver.target.isNative()) {
                CompilerEmissionHelpers.emitErasureBox(driver, ops, accType);
            } else {
                TypeEmitter.boxPrimitive(ops, accType);
            }
        }
        localIdx = RecordEqualityLowerer.emit(driver, be, ops, owner, localIdx, locals,
                accType, rightType);
        if ("!=".equals(be.operator())) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
        }
        accType = Type.PrimitiveType.BOOL;
    } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
            && (Type.isString(accType) || Type.isString(rightType))
            && (TypeMetrics.isPrimitiveType(accType) || TypeMetrics.isPrimitiveType(rightType))) {
        // #338 (dossiê .15): primitivo vs String caiu no ramo do
        // kof_string_equals com um int UNBOXADO na pilha → VerifyError no JVM
        // (mascarado de JavaFX), SIGSEGV latente no Native; JS já dava `false`.
        // O contrato é um só e já existe no repo: igualdade de conteúdo entre
        // tipos diferentes é `false` em TODO target (mesma face do fold
        // String.equals(não-String) em ExpressionInstanceCallLowerer e da
        // "paridade absoluta" documentada lá). Dobrar: POP dos dois lados
        // (POP2 p/ wide — SG-020/bug 79) + BOOL constante.
        if (TypeMetrics.isPrimitiveType(accType)) {
            localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
            ops.add(new KofPop());                                                             // String (topo)
            ops.add(TypeMetrics.isDoubleWidth(accType) ? new KofPop2() : new KofPop());        // primitivo
        } else {
            localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
            ops.add(TypeMetrics.isDoubleWidth(rightType) ? new KofPop2() : new KofPop());      // primitivo (topo)
            ops.add(new KofPop());                                                             // String
        }
        boolean ne = "!=".equals(be.operator());
        ops.add(new KofLoadLiteral(Type.PrimitiveType.BOOL, ne ? 1 : 0));
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
    } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
            && !driver.isNullLiteral(be.left()) && !driver.isNullLiteral(be.right())
            && (ExpressionBinaryPredicates.isUnboundedTypeVar(accType)
                || ExpressionBinaryPredicates.isUnboundedTypeVar(rightType))) {
        // §553 / D-EQ-UNBOUNDED-T: igualdade de CONTEÚDO p/ type var sem bound
        // (o lado esquerdo já está na pilha). Corpo em ExpressionGenericEquality.
        localIdx = ExpressionGenericEquality.emit(driver, be, ops, owner, localIdx, locals);
        accType = Type.PrimitiveType.BOOL;
    } else {
        // Unknown/Nullable(Unknown) vs primitivo (get de mapOf() sem pin
        // vs int): o lado nullable só pode ser null (miss) → referência
        // com o primitivo boxado (SG-008/bug 87; espelha Objects.equals).
        // O box do lado primitivo acontece ANTES do emit do lado oposto
        // (boxa o valor no topo da pilha, na ordem certa).
        // D-NULL-INTENT: só boxa se accType é primitivo CRU (bare
        // PrimitiveType) — um accType já Nullable(primitivo) chega
        // FISICAMENTE boxed (via return/local fix do #278); usar
        // TypeMetrics.isPrimitiveType (que desempacota Nullable) boxava de
        // novo um valor já-Integer → Integer.valueOf(I) com Integer na
        // pilha (VerifyError).
        boolean boxLeftNow = ("==".equals(be.operator()) || "!=".equals(be.operator()))
                && ExpressionBinaryPredicates.isMaybeNullType(rightType) && accType instanceof Type.PrimitiveType apt2 && !Type.isVoid(apt2);
        if (boxLeftNow) TypeEmitter.boxPrimitive(ops, accType);
        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
        Type operandType = accType;
        if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                && (driver.isNullLiteral(be.left()) || driver.isNullLiteral(be.right()))) {
            Type other = driver.isNullLiteral(be.left()) ? rightType : accType;
            operandType = (other instanceof Type.ClassType || other instanceof Type.ArrayType
                    || other instanceof Type.TypeVariable || other instanceof Type.NullableType)
                    ? other : new Type.ClassType("java.lang", "Object", List.of());
        } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                && ((ExpressionBinaryPredicates.isMaybeNullType(accType) && TypeMetrics.isPrimitiveType(rightType))
                    || (ExpressionBinaryPredicates.isMaybeNullType(rightType) && TypeMetrics.isPrimitiveType(accType)))) {
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
        accType = ExpressionBinaryFallbackOps.emit(ops, be.operator(), operandType, accType);
    }
}
return localIdx;
    }
}
