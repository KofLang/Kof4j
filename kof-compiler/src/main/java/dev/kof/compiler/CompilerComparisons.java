package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers de comparação (mapComparison/invert), shortcuts numéricos e
 * detecção de retorno (hasReturnValue).
 */
public final class CompilerComparisons {

    private CompilerComparisons() {}

    /** Unknown OU Nullable(Unknown): o valor pode ser null (get sem pin). */
    private static boolean isMaybeNullType(Type t) {
        return t instanceof Type.UnknownType
                || (t instanceof Type.NullableType nt && nt.inner() instanceof Type.UnknownType);
    }

    static boolean isComparisonShortcut(CompilerDriver driver, BinaryExpr bin, List<IRLocalVariable> locals) {
        if (!TypeMetrics.isComparisonOp(bin.operator())) return false;
        if ("==".equals(bin.operator()) || "!=".equals(bin.operator())) {
            Type left = ExpressionTyper.inferExprType(driver, bin.left(), locals);
            Type right = ExpressionTyper.inferExprType(driver, bin.right(), locals);
            if (Type.isString(left) || Type.isString(right)) return false;
            // bug 188: record == record (ou qualquer record em ==) compara CONTEÚDO via .equals()
            // desativar shortcut para não emitir if_acmpeq direto
            // §262(b): Nullable(record) também (Point? do get/retorno de fn) —
            // antes caía no shortcut → if_acmp de referência (contrato é conteúdo).
            Type leftU = left instanceof Type.NullableType nl ? nl.inner() : left;
            Type rightU = right instanceof Type.NullableType nr ? nr.inner() : right;
            if (CompilerTypes.isRecordType(leftU, driver.currentUnit, driver.semanticAnalyzer)
                    || CompilerTypes.isRecordType(rightU, driver.currentUnit, driver.semanticAnalyzer)) {
                return false;
            }
            // D-SECRETS face 1: `Secret == Secret` compara CONTEÚDO
            // constant-time (KofRuntime$Secret.equals) — desativa o shortcut de
            // identidade (if_acmpeq), como o record.
            if (KofSecurity.isSecretType(leftU) || KofSecurity.isSecretType(rightU)) {
                return false;
            }
            // §553 / D-EQ-UNBOUNDED-T (mantenedora 02/10): `==`/`!=` sobre um
            // type variable SEM bound é igualdade ESTRUTURAL — desativa o
            // shortcut (if_acmp cru) e deixa o caminho de VALOR
            // (ExpressionBinaryLowerer -> Objects.equals) assumir; o chamador
            // salta sobre o BOOL resultante.
            if (ExpressionBinaryPredicates.isUnboundedTypeVar(left)
                    || ExpressionBinaryPredicates.isUnboundedTypeVar(right)) {
                return false;
            }
            // enum == enum: D-ENUM207 — as constantes são INSTÂNCIAS (singletons
            // de <clinit>), então a igualdade é por IDENTIDADE (if_acmp), não por
            // conteúdo String. Deixa o caminho de referência assumir.
            // primitivo vs null → constante (caminho da cadeia binária)
            boolean leftNull = bin.left() instanceof LiteralExpr ll2 && ll2.kind() == ConcreteLiteralKind.NULL;
            boolean rightNull = bin.right() instanceof LiteralExpr rl2 && rl2.kind() == ConcreteLiteralKind.NULL;
            if ((leftNull && TypeMetrics.isPrimitiveType(right)) || (rightNull && TypeMetrics.isPrimitiveType(left))) return false;
            // D-NULL-INTENT (I6): Nullable(primitivo) GENUÍNO (nenhum lado
            // literal null) — mesma exclusão do record acima. O shortcut
            // (if_icmp*/if_acmp* cru) faria unwrap incorreto (VerifyError,
            // valor na pilha é a referência boxed) ou compararia por
            // IDENTIDADE de wrapper (cache do Integer, I6). Desativa e deixa
            // o caminho de VALOR (ExpressionBinaryLowerer/RecordEqualityLowerer,
            // `.equals()` null-safe) assumir — o chamador (assert/if/while)
            // então salta sobre o BOOL resultante.
            boolean leftNullablePrim = isNullablePrim(left);
            boolean rightNullablePrim = isNullablePrim(right);
            if (!leftNull && !rightNull && (leftNullablePrim || rightNullablePrim)) return false;
        }
        return true;
    }

    /** D-NULL-INTENT: {@code Nullable(primitivo)} de verdade (física boxed). */
    private static boolean isNullablePrim(Type t) {
        return t instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType pt
                && !Type.isVoid(pt);
    }

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
    private static int nextFreeLocalIndex(List<IRLocalVariable> locals) {
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
        localIdx = ExpressionLowerer.emitExpression(driver,
                nullableBoolTruthinessRewrite(driver, cond, locals), ops, owner, localIdx, locals);
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        ops.add(new KofConditionalJump(KofComparison.NE, trueLabel, falseLabel));
        return localIdx;
    }

    /**
     * Operand type of a comparison shortcut: the common numeric type of the
     * two operands (int, long, float or double). The IR carries it so the
     * JVM backend can emit the correct compare instruction.
     */
    static Type comparisonOperandType(CompilerDriver driver, BinaryExpr bin, List<IRLocalVariable> locals) {
        Type left = ExpressionTyper.inferExprType(driver, bin.left(), locals);
        Type right = ExpressionTyper.inferExprType(driver, bin.right(), locals);
        // T? desembrulha: Nullable(primitivo) é numérico (unbox com guard
        // do kof_map_get), Nullable(referência) é referência (SG-008/bug 87)
        if (left instanceof Type.NullableType nl) left = nl.inner();
        if (right instanceof Type.NullableType nr) right = nr.inner();
        if (TypeMetrics.isNumeric(left) && TypeMetrics.isNumeric(right)) {
            return TypeMetrics.commonNumericType(left, right);
        }
        // Unknown/Nullable(Unknown) vs primitivo (get de mapOf() sem pin
        // vs literal int): o lado nullable só pode ser null (miss) →
        // comparação de referência com o primitivo BOXADO (espelha o
        // interpretador, Objects.equals). Sem isso o default INT emitia
        // if_icmp* sobre null → VerifyError (SG-008/bug 87).
        if ((isMaybeNullType(left) && TypeMetrics.isPrimitiveType(right))
                || (isMaybeNullType(right) && TypeMetrics.isPrimitiveType(left))) {
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        // comparação contra literal null é sempre referência (if_acmp*);
        // quando o outro lado é Unknown (get de Map, etc.) marca como Object
        if (isNullLiteral(bin.left()) || isNullLiteral(bin.right())) {
            Type other = isNullLiteral(bin.left()) ? right : left;
            if (other instanceof Type.ClassType || other instanceof Type.ArrayType
                    || other instanceof Type.TypeVariable) {
                return other;
            }
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        // referências conhecidas (String vs String, record vs record):
        // preserva o tipo para o backend emitir if_acmp*
        if (left instanceof Type.ClassType || left instanceof Type.ArrayType
                || left instanceof Type.TypeVariable) {
            return left;
        }
        if (right instanceof Type.ClassType || right instanceof Type.ArrayType
                || right instanceof Type.TypeVariable) {
            return right;
        }
        // ambos UnknownType (ex.: `if (a == b)` com `var a = null`): referência
        // (if_acmp*) — espelha ExpressionBinaryLowerer (bug 36). Unknown nunca
        // surge de int inferido (dá INT), então acmp é seguro.
        if (left instanceof Type.UnknownType && right instanceof Type.UnknownType) {
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        return Type.PrimitiveType.INT;
    }

    static boolean isNullLiteral(ExpressionNode e) {
        return e instanceof LiteralExpr le && le.kind() == ConcreteLiteralKind.NULL;
    }

    /**
     * §125 (decisão da mantenedora 12/09, opção A): `return null` em função
     * Nullable(primitivo) vale o DEFAULT do primitivo — mesmo precedente do
     * map-miss (SG-008/bug-87). `StatementLowerer` cai no `else` do ReturnStmt
     * (defaultValueOp, que já desempacota Nullable(primitivo)) em vez de
     * emitir aconst_null + unbox-de-Unknown → Object.intValue (VerifyError
     * no JVM, NoSuchMethodError Integer.valueOf/1 no interpretador).
     */
    static boolean isNullablePrimNullReturn(ReturnStmt ret, Type returnType) {
        return ret.value() != null && isNullLiteral(ret.value())
                && returnType instanceof Type.NullableType nt
                && nt.inner() instanceof Type.PrimitiveType;
    }

    /**
     * §125 (decisão A) extensão: `Int? f() = if (c) x else null`,
     * `Int? f() = switch { case 1 -> 1 default -> null }` e
     * `Int? v = if (c) x else null` — o `null` NÃO é literal no topo do
     * ReturnStmt/VarDecl (é um RAMO do if/switch), então o fold
     * `isNullablePrimNullReturn` não dispara e o ramo null cai no join
     * heterogêneo (branchTypeOrNullAsRef → Object) → ramo primitivo é
     * boxeado → `ireturn`/`istore` sobre referência (VerifyError JVM,
     * `Integer.valueOf/1` no interpretador) enquanto Native/JS imprimem o
     * default. Aqui: se o TIPO DE DESTINO é Nullable(primitivo), reescreve
     * CADA ramo `null` (profundo, só if/switch) p/ o default do primitivo —
     * o MESMO valor que `defaultValueOp`/map-miss já produzem — de modo que
     * o join deixe de ser heterogêneo e os 4 targets convirjam no contrato
     * congelado (opção A: null de primitivo = default). Não dispara p/
     * destino não-Nullable(prim) (ex.: `println(if (c) 1 else null)` —
     * semântica de expressão standalone, intocada: zero regressão).
     */
    static ExpressionNode foldNullablePrimBranches(ExpressionNode e, Type destType) {
        if (e == null) return e;
        Type.PrimitiveType prim = null;
        if (destType instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType p) {
            prim = p;
        } else if (destType instanceof Type.PrimitiveType p && !Type.isVoid(p)) {
            prim = p;
        }
        if (prim == null) return e;
        return foldNullBranches(e, prim);
    }

    private static ExpressionNode foldNullBranches(ExpressionNode e, Type.PrimitiveType prim) {
        if (isNullLiteral(e)) return defaultLiteral(prim, ((LiteralExpr) e).position());
        if (e instanceof IfExpr ie) {
            return new IfExpr(ie.position(), ie.condition(),
                    foldNullBranches(ie.thenExpr(), prim), foldNullBranches(ie.elseExpr(), prim));
        }
        if (e instanceof SwitchExpr se) {
            List<SwitchExprCase> cs = new ArrayList<>();
            for (SwitchExprCase c : se.cases()) {
                cs.add(new SwitchExprCase(c.position(), c.value(),
                        foldNullBranches(c.body(), prim)));
            }
            return new SwitchExpr(se.position(), se.expression(), cs,
                    foldNullBranches(se.defaultValue(), prim));
        }
        return e;
    }

    static LiteralExpr defaultLiteral(Type.PrimitiveType prim, SourcePosition pos) {
        return switch (Type.canonicalPrimitiveName(prim.name())) {
            case "long" -> new LiteralExpr(pos, ConcreteLiteralKind.LONG, "0");
            case "float" -> new LiteralExpr(pos, ConcreteLiteralKind.FLOAT, "0.0f");
            case "double" -> new LiteralExpr(pos, ConcreteLiteralKind.DOUBLE, "0.0");
            case "bool", "boolean" -> new LiteralExpr(pos, ConcreteLiteralKind.BOOLEAN, "false");
            default -> new LiteralExpr(pos, ConcreteLiteralKind.INT, "0");
        };
    }

    /**
     * Emits both operands of a comparison-shortcut condition, widening each
     * to the common numeric type (e.g. `longExpr < 2000` must widen the
     * literal before the compare).
     */
    static int emitComparisonShortcut(CompilerDriver driver, BinaryExpr bin, List<KofOperation> ops,
                                         String owner, int localIdx, List<IRLocalVariable> locals) {
        Type common = CompilerComparisons.comparisonOperandType(driver, bin, locals);
        if (!driver.fpSupportedOnNative(common, bin.position())) {
            return localIdx;
        }
        Type leftT = ExpressionTyper.inferExprType(driver, bin.left(), locals);
        Type rightT = ExpressionTyper.inferExprType(driver, bin.right(), locals);
        if (ExpressionBinaryPredicates.isRelationalOp(bin.operator())
                && ExpressionBinaryPredicates.isUnorderedAgainstFloating(leftT, rightT)) {
            reportUnorderedFloatingComparison(driver, bin, leftT, rightT);
            return localIdx;
        }
        // lado "Unknown-ou-Nullable(Unknown)" pode conter null (get de
        // mapOf() sem pin) — o primitivo oposto é boxado (comparação vira
        // referência; espelha o interpretador, Objects.equals)
        boolean leftMaybeNull = isMaybeNullType(leftT);
        boolean rightMaybeNull = isMaybeNullType(rightT);
        localIdx = ExpressionLowerer.emitExpression(driver, bin.left(), ops, owner, localIdx, locals);
        // D-NULL-INTENT (buraco da face relacional do #278/#438, achado ao fechar o
        // §279): um Nullable(primitivo) GENUINO chega FISICAMENTE boxed no JVM (o
        // return/local do Commit B do #278). Nas comparacoes RELACIONAIS (>,<,>=,<=)
        // o shortcut emite `if_icmp*`/`DCMP*` DIRETO sobre a pilha — entao o operando
        // precisa ser DESEMBACOTADO antes do compare. O caminho de VALOR
        // (ExpressionBinaryLowerer, `isNumericComparison`) ja desempacota (linhas
        // 288/318); so o atalho de CONDICA0 (if/while/print-if) nao fazia → na JVM
        // `Int? v > 0` caia em `if_icmpgt` sobre `java/lang/Integer` → VerifyError no
        // LOAD da classe (compila limpo, morre ao carregar). No-op em JS/Script/
        // Native (needsErasureBoxing e so JVM, onde o nulavel nao e boxed). `==`/`!=`
        // nao passam por aqui com um lado nulavel (o shortcut e desativado la em
        // cima), entao so relacionais alcancam este desempacote.
        if (isNullablePrim(leftT)) {
            Type leftInner = ((Type.NullableType) leftT).inner();
            // §284-map: soft no native (slot de Map = caixa; funcao local = cru)
            CompilerEmissionHelpers.emitErasureUnboxSoft(driver, ops, leftInner);
            leftT = leftInner;
        }
        // rightMaybeNull: o left (na pilha) é primitivo → boxa ele AGORA
        // (antes do emit do right, que empilha por cima)
        if (rightMaybeNull && TypeMetrics.isPrimitiveType(leftT)) {
            TypeEmitter.boxPrimitive(ops, leftT);
        }
        driver.emitWideningIfNeeded(ops, leftT, common);
        localIdx = ExpressionLowerer.emitExpression(driver, bin.right(), ops, owner, localIdx, locals);
        if (isNullablePrim(rightT)) {
            Type rightInner = ((Type.NullableType) rightT).inner();
            // §284-map: soft no native (slot de Map = caixa; funcao local = cru)
            CompilerEmissionHelpers.emitErasureUnboxSoft(driver, ops, rightInner);
            rightT = rightInner;
        }
        // leftMaybeNull: o right (acabou de emitir, topo da pilha) é primitivo
        // → boxa ele DEPOIS do emit
        if (leftMaybeNull && TypeMetrics.isPrimitiveType(rightT)) {
            TypeEmitter.boxPrimitive(ops, rightT);
        }
        driver.emitWideningIfNeeded(ops, rightT, common);
        return localIdx;
    }

    static void reportUnorderedFloatingComparison(CompilerDriver driver, BinaryExpr bin,
                                                   Type leftT, Type rightT) {
        if (driver.currentDiagnostics == null) return;
        Type bad = TypeMetrics.isFloatingPoint(leftT) ? rightT : leftT;
        Type shown = bad instanceof Type.NullableType nt ? nt.inner() : bad;
        String label = Type.isUnknown(shown) || shown instanceof Type.TypeVariable
                ? "the operand type (not known)" : "'" + Type.display(shown) + "'";
        SourcePosition pos = bin.position();
        driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                pos != null ? pos.line() : 0,
                pos != null ? pos.column() : 0,
                0,
                "Kof has no operator '" + bin.operator() + "' for " + label
                        + " compared to a Double/Float operand — ordering is defined only"
                        + " for numeric operands; compare numeric values, or use"
                        + " value.toDouble() on a known dynamic value",
                "SEM104");
    }

    static KofComparison mapComparison(String op) {
        return switch (op) {
            case ">" -> KofComparison.GT;
            case "<" -> KofComparison.LT;
            case ">=" -> KofComparison.GE;
            case "<=" -> KofComparison.LE;
            case "==" -> KofComparison.EQ;
            case "!=" -> KofComparison.NE;
            default -> KofComparison.NE;
        };
    }

    static KofComparison invertComparison(String op) {
        return switch (op) {
            case ">" -> KofComparison.LE;
            case "<" -> KofComparison.GE;
            case ">=" -> KofComparison.LT;
            case "<=" -> KofComparison.GT;
            case "==" -> KofComparison.NE;
            case "!=" -> KofComparison.EQ;
            default -> KofComparison.NE;
        };
    }

    // Int → Long[] slot (I2L) ou Long → Int[] slot (L2I): sem isso o emit
    // do array store usa o opcode do slot com um valor do outro tipo e o
    // verifier rejeita (frame crash / VerifyError "JavaFX").
    static void emitPrimWidenNarrow(CompilerDriver driver, List<KofOperation> ops, ExpressionNode value,
                                     Type elemType, List<IRLocalVariable> locals) {
        Type vt = ExpressionTyper.inferExprType(driver, value, locals);
        if (elemType instanceof Type.PrimitiveType et && vt instanceof Type.PrimitiveType st) {
            if ("long".equals(et.name()) && "int".equals(st.name())) {
                ops.add(new KofUnary(KofUnaryOp.I2L, Type.PrimitiveType.INT));
            } else if ("int".equals(et.name()) && "long".equals(st.name())) {
                ops.add(new KofUnary(KofUnaryOp.L2I, Type.PrimitiveType.LONG));
            }
        }
    }

    static boolean hasReturnValue(CompilerDriver driver, ExpressionNode expr, List<IRLocalVariable> locals) {
        return CompilerComparisons.hasReturnValueInner(driver, expr, locals);
    }

    static boolean hasReturnValueInner(CompilerDriver driver, ExpressionNode expr,
                                              List<IRLocalVariable> locals) {
        if (expr instanceof AssignmentExpr) return false;
        if (expr instanceof MethodCallExpr mc) {
            if ("print".equals(mc.methodName()) || "println".equals(mc.methodName())) return false;
            // cache.* primeiro: cache.delete é void e o nome colide com o
            // File.delete do Io (que o check genérico abaixo não sabe tipar
            // com receiver Unknown) — sem isto o Pop extra diverge o frame
            // idem emit: `cache` pode ser VARIÁVEL LOCAL List (kof_list_add) —
            // só é namespace builtin se não for local/param (frame COMP002:
            // pop duplo em cache.add(...) com local chamado "cache")
            if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                    && KofCache.isCacheNamespace(rid.name())) {
                List<Type> cacheArgTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) cacheArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                KofCache.CacheCall cc = KofCache.staticCall(mc.methodName(), cacheArgTypes);
                if (cc == null) return true;
                return !(cc.returnType() instanceof Type.PrimitiveType pt && "void".equals(pt.name()));
            }
            // gpu.*: todas as funções retornam valor (bool/str/int)
            if (mc.receiver() instanceof IdentifierExpr rid && KofGpu.isGpuNamespace(rid.name())) {
                return true;
            }
            if (mc.receiver() != null && KofIo.instanceMethod(Type.UnknownType.UNKNOWN,
                    mc.methodName(), mc.arguments().size()) != null) {
                return true;
            }
            // List methods that leave a value on the stack (get, remove,
            // size, contains, isEmpty) must be popped at statement level;
            // add/set/clear are already popped by the JVM backend.
            if (mc.receiver() != null && BuiltinTypes.isList(ExpressionTyper.inferExprType(driver, mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "get", "remove", "size", "length", "count",
                            "contains", "isEmpty",
                            // #382 — devolvem valor (sort é void, fica fora)
                            "indexOf", "lastIndexOf", "addAll", "subList" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() != null && BuiltinTypes.isMap(ExpressionTyper.inferExprType(driver, mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "get", "remove", "put", "size", "length", "count",
                            "contains", "containsKey", "isEmpty", "keys", "values", "getOrDefault",
                            // #386 — containsValue/putIfAbsent deixam valor
                            "containsValue", "putIfAbsent" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() != null && BuiltinTypes.isSet(ExpressionTyper.inferExprType(driver, mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "contains", "isEmpty", "size", "length", "count",
                            "add", "remove" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() instanceof IdentifierExpr rid && KofOrm.isOrmNamespace(rid.name())) {
                // todos os orm.* retornam valor (Bool/Object/List/Long) — antes
                // dos checks genéricos (o "delete" também é rota do web)
                return true;
            }
            // #755: the web-route table is receiver-gated on `kof.web.App`
            // (MethodCallTyper does the same); without the gate ANY user
            // method named get/post/put/patch/delete/options was treated as a
            // void route here, so a statement `b.post(...)` returning String
            // skipped its POP and the JVM class failed VerifyError.
            if (mc.receiver() != null
                    && KofWeb.isAppType(ExpressionTyper.inferExprType(driver, mc.receiver(), locals))) {
                List<Type> webArgTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) webArgTypes.add(ExpressionTyper.inferExprType(driver, arg, List.of()));
                KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
                if (webCall != null) {
                    return !(webCall.returnType() instanceof Type.PrimitiveType pt && "void".equals(pt.name()));
                }
            }
            if (mc.receiver() instanceof IdentifierExpr rid && KofIo.isConstructor(rid.name())
                    && KofIo.staticMethod(rid.name(), mc.methodName(), mc.arguments().size()) != null) {
                return true;
            }
            if (driver.semanticAnalyzer != null) {
                SymbolTable.MethodSymbol resolved = driver.semanticAnalyzer.getResolvedMethod(mc);
                if (resolved != null) {
                    // add/set/clear de coleção builtin: o JVM backend já
                    // descarta o valor no emit (POP) — um KofPop extra aqui
                    // vira stack underflow no merge de frames (COMP002)
                    String oc = resolved.ownerClass();
                    if (("List".equals(oc) || "ArrayList".equals(oc) || "java/util/List".equals(oc)
                            || "Map".equals(oc) || "HashMap".equals(oc)
                            || "Set".equals(oc) || "HashSet".equals(oc))
                            && ("add".equals(mc.methodName()) || "push".equals(mc.methodName())
                                || "append".equals(mc.methodName()) || "set".equals(mc.methodName())
                                || "clear".equals(mc.methodName()) || "put".equals(mc.methodName()))) {
                        return false;
                    }
                    Type resolvedType = resolved.returnType();
                    if (Type.isVoid(resolvedType)) return false;
                    return !(resolvedType instanceof Type.UnknownType);
                }
            }
            Type t = ExpressionTyper.inferExprType(driver, mc, locals);
            if (t instanceof Type.UnknownType || Type.isVoid(t)) return false;
            // add/push/append/set/clear/put de coleção: o emit do backend
            // já descarta o valor (POP no kof_list_add/kof_map_put) — sem
            // KofPop aqui (underflow no merge de frames, COMP002).
            if (mc.receiver() instanceof IdentifierExpr) {
                String mn = mc.methodName();
                if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                        || "set".equals(mn) || "clear".equals(mn) || "put".equals(mn)) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

}