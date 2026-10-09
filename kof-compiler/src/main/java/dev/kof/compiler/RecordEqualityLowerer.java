package dev.kof.compiler;

import java.util.List;

/**
 * Lowering da igualdade de CONTEÚDO de record (§262). Extraído de
 * {@link ExpressionBinaryLowerer} (ratchet §140, precedente do JsTryParser do
 * §266): o caso `==`/`!=` entre records é o único construtor que precisa de
 * guarda de null nos operandos, e ocupa ~96 linhas de IR.
 *
 * <p>Regra: {@code a == b} entre records é igualdade de CONTEÚDO
 * ({@code a.equals(b)}), NUNCA referência (bug 11). Mas os operandos podem ser
 * NULOS ({@code Point?} vindo de um get de map ou de uma função que retorna
 * null). Chamar {@code receiver.equals(arg)} com receiver nulo é NPE (JVM) /
 * "Cannot read property of null" (JS) / SIGSEGV (Native); com arg nulo é
 * TypeError/SIGSEGV porque o {@code equals} sintético do record NÃO guarda o
 * ARG (só o instanceof). Por isso o §262 face (b) desuga p/ semântica
 * {@code java.util.Objects.equals}: ambos nulos → true; um nulo → false; senão
 * {@code a.equals(b)}. Só entra aqui quando NENHUM lado é o literal {@code null}
 * (esse caso é comparação de referência, tratado no chamador).
 *
 * <p>#278 (D-NULL-INTENT, I6): o MESMO mecanismo cobre dois
 * {@code Nullable(primitivo)} GENUÍNOS (ex. {@code Int? a(); Int? b(); a() ==
 * b()}) — agora que a representação física é boxed de verdade, comparar por
 * referência (if_acmp) colidiria com o cache do {@code Integer} (dois
 * {@code 10000} são objetos DIFERENTES). {@code Integer.equals}/
 * {@code Long.equals}/etc. já fazem igualdade por VALOR null-safe — o
 * "recordType" vira o wrapper boxed em vez de uma classe record.
 */
final class RecordEqualityLowerer {

    private RecordEqualityLowerer() {}

    /**
     * #259: o tipo ESTÁTICO do lado (já desembrulhado o {@code Nullable}) é
     * {@code float}/{@code double}? Decide entre o helper de wrapper (NaN/±0.0)
     * e o {@code ===} comum do JS.
     */
    private static boolean isFloatingOperand(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        return inner instanceof Type.PrimitiveType && TypeMetrics.isFloatingPoint(inner);
    }

    /**
     * Emite a comparação de conteúdo null-safe. Deixa um número 0/1 (BOOL) no
     * topo representando {@code a == b} (NUNCA aplica o `!=` — o chamador faz
     * isso uma vez). O chamador define {@code accType = BOOL}. Pilha ao entrar:
     * [L, R].
     */
    static int emit(CompilerDriver driver, BinaryExpr be, List<KofOperation> ops,
                    String owner, int localIdx, List<IRLocalVariable> locals,
                    Type accType, Type rightType) {
        Type objT = new Type.ClassType("java.lang", "Object", List.of());
        // JS NÃO suporta o desugar com jumps abaixo em posição de CONDIÇÃO
        // (if/while): o reconstructor do KofJS move os temporários para fora do
        // corpo do loop (ReferenceError, medido 17/09) — o MESMO motivo pelo
        // qual `&&`/`||` dão um caminho próprio quando o alvo é JS
        // (ExpressionBinaryLowerer:167). Uma ÚNICA chamada de função é uma
        // expressão opaca p/ todos os backends. kofRecordEq(a,b) -> 1/0 tem a
        // SEMANTICA de Objects.equals (JsRuntimeCore.kofRecordEq).
        // WASI (15.3d inc2 fatia C2): o dispatcher wasm (`loop $dispatch` +
        // `$pc`) NAO carrega a pilha de valores entre blocos — a ternária com
        // jumps acima mergearia resultados por label, o que a linearização por
        // pc ainda não modela (medido na fatia C1). MESMO mecanismo aprovado
        // p/ JS: uma chamada opaca; o backend WASI sintetiza o fold de campo
        // INLINE no call site (WasmRecordCode.emitEquals, sem fluxo cruzado).
        if (driver.target == Target.JS || driver.target == Target.WASI) {
            localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
            // D-NULL-INTENT (I6, caso misto): lado direito primitivo CRU
            // (`m.get("a") == 1`) — no JS não há física de boxing real, mas
            // o box IR é no-op lá (número continua número); mantém o par
            // simétrico com o box do lado esquerdo (ExpressionBinaryLowerer).
            if ((rightType instanceof Type.PrimitiveType rpt0 && !Type.isVoid(rpt0)) || ExpressionBinaryPredicates.isBareHandleErasedToInt(rightType)) {
                // §284-map: native = kof_box_* (TypeEmitter e JVM-only).
                if (driver.target.isNative()) {
                    CompilerEmissionHelpers.emitErasureBox(driver, ops, rightType);
                } else {
                    TypeEmitter.boxPrimitive(ops, rightType);
                }
            }
            // #259: `Float?`/`Double?` nullable baixa p/ o helper de semântica
            // do wrapper JVM (NaN != NaN e +0.0 != -0.0 no `===` do JS); Int/
            // Long/Char/Bool seguem no kofRecordEq, cujo `===` já coincide.
            String eqFn = isFloatingOperand(accType) || isFloatingOperand(rightType)
                    ? "kofFpEq" : "kofRecordEq";
            ops.add(new KofCall(BuiltinTypes.STRING, eqFn, List.of(objT, objT),
                    Type.PrimitiveType.INT, KofCallKind.FUNCTION));
            return localIdx;
        }
        // JVM/Script/Native: desuga para ternária NESTED com jumps, cada CJump
        // com ramos que convergem no SEU label (o padrão do IfExpr). TODOS os
        // ramos produzem inteiro 0/1 (nunca bool): o `!=` e o consumo p/ valueOf
        // batem igual nos 3 targets. O equals() do record NÃO guarda o ARG,
        // então o ramo de conteúdo só roda com os DOIS não-nulos.
        localIdx = ExpressionLowerer.emitExpression(driver, be.right(), ops, owner, localIdx, locals);
        // D-NULL-INTENT (I6, caso misto): lado direito primitivo CRU
        // precisa boxear antes de entrar no par de temporários Object
        // abaixo (KofStoreLocal ASTORE exige referência na pilha).
        if ((rightType instanceof Type.PrimitiveType rpt1 && !Type.isVoid(rpt1)) || ExpressionBinaryPredicates.isBareHandleErasedToInt(rightType)) {
            // §284-map: native = kof_box_* (o literal `1` de `m.get("a") == 1`
            // precisa chegar CAIXA p/ o kof_box_equals comparar por valor).
            if (driver.target.isNative()) {
                CompilerEmissionHelpers.emitErasureBox(driver, ops, rightType);
            } else {
                TypeEmitter.boxPrimitive(ops, rightType);
            }
        }
        // D-NULL-INTENT (I6): quando NENHUM lado é record, o chamador só
        // despacha aqui com os DOIS lados Nullable(primitivo) — resolve o
        // WRAPPER boxed (Integer/Long/...) como "recordType": seu `.equals`
        // real já faz igualdade por VALOR null-safe (mesma mecânica).
        Type recordType;
        if (ExpressionBinaryPredicates.isRecordLike(accType, driver)) {
            recordType = accType instanceof Type.NullableType nta ? nta.inner() : accType;
        } else if (ExpressionBinaryPredicates.isRecordLike(rightType, driver)) {
            recordType = rightType instanceof Type.NullableType ntr ? ntr.inner() : rightType;
        } else if (accType instanceof Type.NullableType ntp && ntp.inner() instanceof Type.PrimitiveType apt
                && TypeMetrics.boxedTypeFor(apt) instanceof Type.ClassType aboxed) {
            recordType = aboxed;
        } else if (rightType instanceof Type.NullableType ntq && ntq.inner() instanceof Type.PrimitiveType rpt
                && TypeMetrics.boxedTypeFor(rpt) instanceof Type.ClassType rboxed) {
            recordType = rboxed;
        } else if (ExpressionBinaryPredicates.boxedHandleType(accType) instanceof Type.ClassType hb1) {
            // §519/#632: Nullable(handle)/handle — a caixa fisica e Integer
            // (UIW050); `.equals` do wrapper e o caminho de VALOR null-safe.
            recordType = hb1;
        } else if (ExpressionBinaryPredicates.boxedHandleType(rightType) instanceof Type.ClassType hb2) {
            recordType = hb2;
        } else {
            recordType = objT;
        }
        // Pilha ao entrar: [L, R]. Guarda os DOIS em temporários (cada lado
        // avaliado UMA vez) — o get de map / chamada de função não pode rodar
        // duas vezes.
        ops.add(new KofStoreLocal(objT, localIdx));           // tR = R (topo)
        locals.add(new IRLocalVariable(localIdx, "#recR", objT));
        int rTmp = localIdx; localIdx += 1;
        ops.add(new KofStoreLocal(objT, localIdx));           // tL = L
        locals.add(new IRLocalVariable(localIdx, "#recL", objT));
        int lTmp = localIdx; localIdx += 1;
        LabelId then1 = LabelId.create();
        LabelId else1 = LabelId.create();
        LabelId eqBranch = LabelId.create();
        LabelId falBranch = LabelId.create();
        LabelId innerEnd = LabelId.create();
        LabelId outerEnd = LabelId.create();
        // (tL != null) ? ((tR != null) ? tL.equals(tR) : 0) : ((tL == tR) ? 1 : 0)
        ops.add(new KofLoadLocal(objT, lTmp));
        ops.add(KofLoadLiteral.ofNull());
        ops.add(new KofConditionalJump(KofComparison.NE, objT, then1, else1));
        ops.add(new KofLabel(then1));
        ops.add(new KofLoadLocal(objT, rTmp));
        ops.add(KofLoadLiteral.ofNull());
        ops.add(new KofConditionalJump(KofComparison.NE, objT, eqBranch, falBranch));
        ops.add(new KofLabel(eqBranch));
        ops.add(new KofLoadLocal(objT, lTmp));
        ops.add(new KofCheckCast(recordType));
        ops.add(new KofLoadLocal(objT, rTmp));
        ops.add(new KofCall(recordType, "equals", List.of(objT),
                Type.PrimitiveType.BOOL, KofCallKind.INSTANCE));
        ops.add(new KofJump(innerEnd));
        ops.add(new KofLabel(falBranch));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        ops.add(new KofLabel(innerEnd));
        ops.add(new KofJump(outerEnd));
        // else1: comparação de REFERÊNCIA (ambos null → 1; só um null → 0),
        // SUA PRÓPRIA sub-ternária numérica com label de convergência (refEnd)
        // ANTES da outerEnd — o consumo de um fragmento para no PRIMEIRO Label,
        // então compartilhar a outerEnd faria a ternária externa não achar o
        // terminador (stack underflow, medido).
        ops.add(new KofLabel(else1));
        LabelId refTrue = LabelId.create();
        LabelId refFalse = LabelId.create();
        LabelId refEnd = LabelId.create();
        ops.add(new KofLoadLocal(objT, lTmp));
        ops.add(new KofLoadLocal(objT, rTmp));
        ops.add(new KofConditionalJump(KofComparison.EQ, objT, refTrue, refFalse));
        ops.add(new KofLabel(refTrue));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        ops.add(new KofJump(refEnd));
        ops.add(new KofLabel(refFalse));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        ops.add(new KofLabel(refEnd));
        ops.add(new KofLabel(outerEnd));
        return localIdx;
    }
}
