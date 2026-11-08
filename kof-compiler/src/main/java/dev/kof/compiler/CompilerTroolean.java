package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Split do gate 500 (extraído de {@code CompilerComparisons}): a família
 * Troolean/truthiness — lowerTrooleanAndOr/lowerTrooleanNot,
 * nullableBoolTruthinessRewrite, emitTruthinessJump e o predicado
 * isNullableBool. Nenhuma mudança de semântica; só a moradia.
 */
final class CompilerTroolean {

    private CompilerTroolean() {}

    /** §306(a): {@code Nullable(Bool)} genuíno — slot fisicamente boxed (Commit B). */
    static boolean isNullableBool(Type t) {
        return t instanceof Type.NullableType nt
                && nt.inner() instanceof Type.PrimitiveType pt
                && "bool".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    /**
     * D-TROOL (19/09, DECISIONS.md): {@code &&}/{@code ||} com {@code Troolean}
     * seguem a tabela de Kleene ({@code F} domina o AND, {@code T} domina o OR;
     * {@code U}=null só sobrevive quando nada decide). Escrito em IR (labels +
     * {@code KofStoreLocal}/{@code KofLoadLocal}): cada operando é avaliado
     * UMA vez e o curto-circuito é preservado onde a tabela o permite
     * ({@code false && b} não avalia {@code b}; {@code null && b} avalia —
     * o resultado depende de {@code b}). O slot de resultado é a caixa do
     * §295/§306 ({@code Boolean} ou {@code null}) nos 4 alvos.
     * Retorna -1 quando o operador não é Kleene (caller usa o caminho atual).
     */
    static int lowerTrooleanAndOr(CompilerDriver driver, BinaryExpr bin, List<KofOperation> ops,
                                  String owner, int localIdx, List<IRLocalVariable> locals) {
        String op = bin.operator();
        boolean isAnd = "&&".equals(op);
        if (!isAnd && !"||".equals(op)) return -1;
        Type lt = ExpressionTyper.inferExprType(driver, bin.left(), locals);
        Type rt = ExpressionTyper.inferExprType(driver, bin.right(), locals);
        if (!isNullableBool(lt) && !isNullableBool(rt)) return -1;
        SourcePosition pos = bin.position();
        // D-TROOL (19/09): desugar de Kleene com o PADRAO DOBRAVEL p/ o JS:
        // todo KofConditionalJump e seguido IMEDIATAMENTE do seu Label(true)
        // (shape de if-STATEMENT: tryParseIfExpr/parseIfBody reconhecem), o
        // resto cai no false-label. O resultado vive no temporario $R (caixa
        // Boolean|null do §295/§306) e cada arco e um store de statement —
        // nada de "expressao solta entre jumps", que era o que o parser JS
        // recusava ("unexpected op in expression statement", medido 19/09).
        //   a && b ≡ se a==F → F; senao se a==T → b; senao (b==F ? F : U)
        //   a || b ≡ se a==T → T; senao se a==F → b; senao (b==T ? T : U)
        // Cada operando e avaliado UMA vez (temps $A/$B); curto-circuito
        // preservado onde a tabela permite (a==dom nao toca em b).
        int aIdx = localIdx;
        localIdx = aIdx + 1;
        locals.add(new IRLocalVariable(aIdx, "$klt" + aIdx, lt));
        ExpressionNode a = new IdentifierExpr(pos, "$klt" + aIdx);
        ExpressionNode litTrue = new LiteralExpr(pos, ConcreteLiteralKind.BOOLEAN, "true");
        ExpressionNode litFalse = new LiteralExpr(pos, ConcreteLiteralKind.BOOLEAN, "false");
        ExpressionNode litU = new LiteralExpr(pos, ConcreteLiteralKind.NULL, "null");
        ExpressionNode first = isAnd ? litFalse : litTrue;
        ExpressionNode pureA = isAnd ? litTrue : litFalse;
        ExpressionNode bTest = isAnd ? litFalse : litTrue;
        // MESMA maquina do `!` (lowerTrooleanNot): 1 store + cadeia de IfExpr
        // com ramos de expressao pura — a dobradura JS so reconhece esse
        // shape (medido 19/09: IR com stores nos arcos explode no dispatcher
        // de statements; IfExpr-puro dobra). `b` aparece em dois ramos
        // MUTUAMENTE EXCLUSIVOS do else -> avaliado UMA vez por execucao, e
        // NUNCA quando o dominador venceu (curto-circuito preservado).
        //   a && b ≡ if (a==F) F else (if (a==T) b else (if (b==F) F else null))
        //   a || b ≡ if (a==T) T else (if (a==F) b else (if (b==T) T else null))
        localIdx = ExpressionLowerer.emitExpression(driver, bin.left(), ops, owner, localIdx, locals);
        ops.add(new KofStoreLocal(lt, aIdx));
        localIdx = ExpressionLowerer.emitExpression(driver,
                new IfExpr(pos, new BinaryExpr(pos, "==", a, first), first,
                        new IfExpr(pos, new BinaryExpr(pos, "==", a, pureA), bin.right(),
                                new IfExpr(pos, new BinaryExpr(pos, "==", bin.right(), bTest),
                                        first, litU))),
                ops, owner, localIdx, locals);
        return localIdx;
    }

    /** {@code ($name == <boolLiteral>)} no caminho de VALOR (null-safe) + push 0 p/ o jump. */
    static int nextFreeLocalIndex(List<IRLocalVariable> locals) {
        int m = 0;
        for (IRLocalVariable lv : locals) m = Math.max(m, lv.index() + 1);
        return m;
    }

    /**
     * D-TROOL: o {@code !} de Kleene — {@code !T=F}, {@code !F=T}, {@code !U=U}
     * (a face que hoje é VerifyError no JVM / "not an int" no Script / `true`
     * silencioso no JS). Mesma máquina do AND/OR: IR com temporário único.
     */
    static int lowerTrooleanNot(CompilerDriver driver, UnaryExpr ue, List<KofOperation> ops,
                                String owner, int localIdx, List<IRLocalVariable> locals) {
        Type t = ExpressionTyper.inferExprType(driver, ue.operand(), locals);
        if (!isNullableBool(t)) return -1;
        SourcePosition pos = ue.position();
        int aIdx = Math.max(localIdx, nextFreeLocalIndex(locals));
        locals.add(new IRLocalVariable(aIdx, "$klt" + aIdx, t));
        localIdx = aIdx + 1;
        localIdx = ExpressionLowerer.emitExpression(driver, ue.operand(), ops, owner, localIdx, locals);
        ops.add(new KofStoreLocal(t, aIdx));
        ExpressionNode a = new IdentifierExpr(pos, "$klt" + aIdx);
        ExpressionNode litTrue = new LiteralExpr(pos, ConcreteLiteralKind.BOOLEAN, "true");
        ExpressionNode litFalse = new LiteralExpr(pos, ConcreteLiteralKind.BOOLEAN, "false");
        // !a ≡ if (a == true) false else (if (a == false) true else null)
        localIdx = ExpressionLowerer.emitExpression(driver,
                new IfExpr(pos, new BinaryExpr(pos, "==", a, litTrue), litFalse,
                        new IfExpr(pos, new BinaryExpr(pos, "==", a, litFalse), litTrue,
                                new LiteralExpr(pos, ConcreteLiteralKind.NULL, "null"))),
                ops, owner, localIdx, locals);
        return localIdx;
    }

    /**
     * §306(a)+(b) — salto de TRUTHINESS em posição de condição ({@code if}/
     * {@code while}/if-expr com {@code cond} não-comparação). O slot
     * {@code Nullable(Bool)} é boxing desde o Commit B (#278), mas a caixa era
     * inconsistente: JVM guarda a referência {@code java/lang/Boolean} (o
     * {@code if_icmp*} cru do caminho antigo morria no LOAD da classe —
     * {@code VerifyError} mascarado de "JavaFX", §149) e o interpretador, com
     * {@code kof_box} de identidade, misturava {@code Integer} 0/1 (ofBool)
     * com {@code Boolean} (coerceFor do get) → {@code ==}/{@code println}
     * divergiam por fonte do valor (face (b): "1/0" no Script vs "true/false").
     * O fix é em duas pontas: (1) o box do interpretador agora CANONIZA
     * Number↔Boolean no kof_box/kof_unbox (alinha com a referência da JVM);
     * (2) AÇÚCAR {@code if (b)} → {@code if (b == true)} cai no caminho de
     * VALOR do {@code ==} (D-NULL-INTENT: {@code .equals} null-safe —
     * {@code null == true} é {@code false}, o falsy honesto). No Native o
     * slot é o primitivo cru (varType desembrulhado) e o caminho antigo já
     * é correto — não reescrever (medição local impossível sem qemu; CI
     * cross é o gate). Sem reescrita quando o tipo não é {@code Nullable(Bool)}
     * (byte-idêntico ao comportamento atual).
     */
    static ExpressionNode nullableBoolTruthinessRewrite(CompilerDriver driver, ExpressionNode cond,
                                                        List<IRLocalVariable> locals) {
        // D-TROOL: SEM early-return p/ BinaryExpr — com Kleene, `if (a && b)`
        // produz CAIXA (Boolean|null) na pilha e o `IF_ICMP 0` cru morre neles
        // igual ao slot de `Bool?` simples; o tipo inferred decide (comparacoes
        // continuam BOOL cru e passam direto pelo guard abaixo).
        // #259/N2: o Native NÃO é mais excluído aqui. O §306 o deixou de fora
        // porque o slot nativo de `Bool?` era o primitivo cru (NE 0 bastava);
        // com a representação atômica o slot é CAIXA (ponteiro), e `NE 0`
        // passaria a testar o PONTEIRO — `Present(false)` é ponteiro não-nulo
        // e viraria "verdadeiro" (medido: `while (fb())` gira para sempre no
        // x86; riscv/aarch davam `true` para `false`). A igualdade de
        // `Nullable(Bool)` já roteia p/ `kof_box_equals` (valor) nos 3 nativos.
        if (!isNullableBool(ExpressionTyper.inferExprType(driver, cond, locals))) {
            return cond;
        }
        return new BinaryExpr(cond.position(), "==", cond,
                new LiteralExpr(cond.position(), ConcreteLiteralKind.BOOLEAN, "true"));
    }

    static int emitTruthinessJump(CompilerDriver driver, ExpressionNode cond,
                                  List<KofOperation> ops, String owner, int localIdx,
                                  List<IRLocalVariable> locals, LabelId trueLabel, LabelId falseLabel) {
        ExpressionNode c = nullableBoolTruthinessRewrite(driver, cond, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, c, ops, owner, localIdx, locals);
        // Fase E (24/10, #776): o zero sintetico leva o TIPO da condicao — um
        // Bool do WASI empilha i32 e o literal INT (i64.const) batia em
        // largura diferente (MODULO INVALIDO medido: `var b = 1 > 2` +
        // `if (b)`, `if (isBig(200))`, `while (flag)`). Com BOOL o literal sai
        // i32.const (o emitLiteral do WASI ja mapeia bool → i32) e o JVM/
        // nativo tratam bool como int — o salto fica INT (o WASI decide a
        // comparacao pela largura real empilhada).
        Type condType = ExpressionTyper.inferExprType(driver, c, locals);
        Type zeroType = condType instanceof Type.PrimitiveType pt
                && "bool".equalsIgnoreCase(pt.name()) ? Type.PrimitiveType.BOOL : Type.PrimitiveType.INT;
        ops.add(new KofLoadLiteral(zeroType, 0));
        ops.add(new KofConditionalJump(KofComparison.NE, trueLabel, falseLabel));
        return localIdx;
    }

}
