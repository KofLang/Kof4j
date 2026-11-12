package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Kof.expr fatias 1+2 (`D-KOF-EXPR`) — o lowering da DECLARAÇÃO de padrão para
 * uma CLASSE real com as duas operações:
 *
 * <pre>{@code
 * expr greeting { "hello" " " "world" }
 * }</pre>
 *
 * <p>vira a classe {@code greeting} com os estáticos {@code matches(String):
 * Bool} (casamento do SUJEITO INTEIRO) e {@code match(String): String?} (o
 * sujeito casado, ou null). Estratégia P (§8 do plano): o corpo emite o MESMO
 * IR que as chamadas String de usuário emitem — {@code startsWith(prefix,
 * from)} / {@code length()} INSTANCE e {@code kof_string_equals} — zero op de
 * backend novo, então JVM e Script executam a MESMA forma compilada (o oráculo
 * é byte-idêntico; JS/Native entram nas fatias 6/7 sobre o mesmo IR).
 *
 * <p><b>O matcher é uma CADEIA DE ESTADOS</b> — um método sintético por ponto
 * de casamento, com a forma {@code (String s, Int pos, Int c0, …): Int}
 * devolvendo a posição final ou {@code -1}. A sequência é uma CONTINUAÇÃO: a
 * unidade {@code i} casa e chama o estado seguinte (cauda recursiva, sem pilha
 * explícita). Um quantificador acrescenta três estados — o laço, o retorno do
 * corpo e o corpo — que tentam MAIS uma instância antes da cauda (greedy) e
 * desenrolam quando a cauda recusa: isso É casamento com backtracking.
 *
 * <p><b>Um contador por PROFUNDIDADE de aninhamento</b>: cada quantificador tem
 * o SEU contador (o slot da sua profundidade), então um quantificador dentro de
 * outro não compartilha a contagem — a razão de o parâmetro não ser único.
 *
 * <p><b>Terminação por construção (§12 do plano)</b>: as duas formas que não
 * podem terminar ou explodir são RECUSADAS no compile — um quantificador SEM
 * TETO sobre um corpo que pode casar vazio ({@code (a?)*}, o laço infinito
 * clássico) é {@code EXPR003}; um quantificador sem teto DENTRO de outro sem
 * teto, mesmo transitivo por sequências e quantificadores limitados
 * ({@code (a+)+}), é {@code EXPR002} — a opção de recusa-no-compile que o §12
 * autoriza. Um corpo anulável dentro de um quantificador LIMITADO é seguro (o
 * contador avança) e é aceito. O orçamento de passos documentado fica para a
 * fatia 8.
 *
 * <p>Tudo ALÉM do conjunto das fatias recusa no compile com {@code EXPR001}
 * nomeando a fatia do plano — nunca fallback silencioso (R6).
 */
final class CompilerExprLowering {

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final int SLOT_S = 0;
    private static final int SLOT_POS = 1;
    private static final int SLOT_FIRST_COUNT = 2;

    /** Quantificadores nomeados da fatia 2 e o intervalo (min, max) que impõem; max < 0 = sem teto. */
    private static final java.util.Map<String, int[]> NAMED = java.util.Map.of(
            "optional", new int[]{0, 1},
            "zero_or_more", new int[]{0, -1},
            "one_or_more", new int[]{1, -1});

    private final CompilerDriver driver;
    private final Type self;
    /** Número TOTAL de ocorrências de quantificador = número de slots de contador. */
    private final int slots;
    private final List<IRMethod> states = new ArrayList<>();
    private int uid;
    private int nextSlot;

    private CompilerExprLowering(CompilerDriver driver, Type self, int slots) {
        this.driver = driver;
        this.self = self;
        this.slots = slots;
    }

    /** @return a classe do padrão, ou null quando o corpo recusou (diagnóstico já emitido). */
    static IRClass lower(CompilerDriver driver, ExprDeclarationNode ex, String packageName, int typeId) {
        String internalName = driver.toInternalName(packageName, ex.name());
        Type self = new Type.ClassType(packageName, ex.name(), List.of());
        int slots = quantifierCount(ex.body());
        CompilerExprLowering lowering = new CompilerExprLowering(driver, self, slots);
        if (!lowering.validate(ex.body())) return null;
        String accept = lowering.acceptState();
        String entry = lowering.compileUnits(unitsOf(ex.body()), accept);
        List<IRMethod> methods = new ArrayList<>();
        methods.add(lowering.publicMatches(entry));
        methods.add(lowering.publicMatch());
        methods.addAll(lowering.states);
        int access = driver.computeAccess(ex.modifiers()) | AccessFlags.FINAL;
        return new IRClass(internalName, "java/lang/Object", List.of(), access,
                List.of(), methods, List.of(), null, typeId,
                CompilerAnnotations.lowerAnnotations(driver, ex.annotations()));
    }

    // ---------------------------------------------------------------- validação

    /**
     * Recusa honesta antes de emitir qualquer estado: EXPR001 (fora do conjunto
     * das fatias), EXPR002 (sem teto dentro de sem teto) e EXPR003 (corpo
     * anulável num laço sem teto). @return true quando o corpo é baixável.
     */
    private boolean validate(ExprNode node) {
        if (node instanceof ExprSequenceNode seq) {
            for (ExprNode unit : seq.units()) if (!validate(unit)) return false;
            return true;
        }
        if (node instanceof ExprAtomNode atom) return validateAtom(atom);
        if (node instanceof ExprQuantifierNode q) {
            int[] bound = boundsOf(q);
            if (bound == null) return false;
            if (bound[1] < 0) {
                if (containsUnbounded(q.target())) {
                    return refuse("EXPR002", q.position(), "'" + q.kind() + "' contains another quantifier "
                            + "with no limit, which can backtrack exponentially (ReDoS): bound the inner "
                            + "loop with exactly/between, or split the pattern");
                }
                if (nullable(q.target())) {
                    return refuse("EXPR003", q.position(), "'" + q.kind() + "' over a body that can match "
                            + "empty text would never advance: the body must require at least one character");
                }
            }
            return validate(q.target());
        }
        if (node instanceof ExprAlternationNode a) {
            return refuse("EXPR001", a.position(), "'either' (alternation) is not part of Kof.expr slice 2 "
                    + "(literals + anchors + quantifiers): it enters with slice 4");
        }
        if (node instanceof ExprCaptureNode c) {
            return refuse("EXPR001", c.position(), "capture('" + c.name() + "') is not part of Kof.expr "
                    + "slice 2 (literals + anchors + quantifiers): captures enter with slice 5");
        }
        if (node instanceof ExprReferenceNode r) {
            return refuse("EXPR001", r.position(), "pattern reference '" + r.alias() + "' is not part of "
                    + "Kof.expr slice 2 (literals + anchors + quantifiers): composition enters with a later slice");
        }
        return refuse("EXPR001", node.position(), "this pattern element is not part of Kof.expr slice 2 "
                + "(literals + anchors + quantifiers): it enters with a later slice");
    }

    private boolean validateAtom(ExprAtomNode atom) {
        if (atom.kind().equals("literal") || atom.kind().equals("start") || atom.kind().equals("end")) {
            return true;
        }
        return refuse("EXPR001", atom.position(), "'" + atom.kind() + "' (a character class) is not part of "
                + "Kof.expr slice 2 (literals + anchors + quantifiers): the character classes enter with slice 3");
    }

    /** O intervalo de um quantificador: os três nomeados, ou exactly/between com as contagens. */
    private int[] boundsOf(ExprQuantifierNode q) {
        int[] named = NAMED.get(q.kind());
        if (named != null) return named;
        if (q.kind().equals("exactly")) return new int[]{q.min(), q.min()};
        if (q.kind().equals("between")) return new int[]{q.min(), q.max()};
        refuse("EXPR001", q.position(), "'" + q.kind() + "' is not a Kof.expr slice-2 quantifier");
        return null;
    }

    /** Pode a unidade casar texto VAZIO? (decide a terminação de um laço sem teto) */
    private static boolean nullable(ExprNode node) {
        if (node instanceof ExprSequenceNode seq) {
            for (ExprNode unit : seq.units()) if (!nullable(unit)) return false;
            return true;
        }
        if (node instanceof ExprAtomNode atom) {
            if (atom.kind().equals("start") || atom.kind().equals("end")) return true;
            if (atom.kind().equals("literal")) {
                return atom.values().isEmpty() || atom.values().get(0).isEmpty();
            }
            return false;
        }
        if (node instanceof ExprQuantifierNode q) {
            int min;
            if (q.kind().equals("exactly") || q.kind().equals("between")) min = q.min();
            else if (q.kind().equals("one_or_more")) min = 1;
            else min = 0;
            return min == 0 || nullable(q.target());
        }
        return false;
    }

    /** Algum descendente é um quantificador SEM TETO? (a forma exponencial do §12) */
    private static boolean containsUnbounded(ExprNode node) {
        if (node instanceof ExprSequenceNode seq) {
            for (ExprNode unit : seq.units()) if (containsUnbounded(unit)) return true;
            return false;
        }
        if (node instanceof ExprQuantifierNode q) {
            boolean unbounded = q.kind().equals("zero_or_more") || q.kind().equals("one_or_more")
                    || (q.kind().equals("between") && q.max() < 0);
            return unbounded || containsUnbounded(q.target());
        }
        return false;
    }

    /** Quantas OCORRÊNCIAS de quantificador há na árvore = número de slots de contador. */
    private static int quantifierCount(ExprNode node) {
        if (node instanceof ExprSequenceNode seq) {
            int total = 0;
            for (ExprNode unit : seq.units()) total += quantifierCount(unit);
            return total;
        }
        if (node instanceof ExprQuantifierNode q) return 1 + quantifierCount(q.target());
        return 0;
    }

    private static List<ExprNode> unitsOf(ExprNode node) {
        return node instanceof ExprSequenceNode seq ? seq.units() : List.of(node);
    }

    // ---------------------------------------------------------------- geração

    /** A cadeia das unidades, da última para a primeira: cada uma continua na seguinte. */
    private String compileUnits(List<ExprNode> units, String tail) {
        String next = tail;
        for (int i = units.size() - 1; i >= 0; i--) {
            next = compileUnit(units.get(i), next);
        }
        return next;
    }

    private String compileUnit(ExprNode unit, String tail) {
        if (unit instanceof ExprSequenceNode seq) return compileUnits(seq.units(), tail);
        if (unit instanceof ExprAtomNode atom) return atomState(atom, tail);
        if (unit instanceof ExprQuantifierNode q) return quantifierStates(q, tail);
        return tail; // validate() recusou: inalcançável
    }

    /** Aceite de um casamento de sujeito inteiro: a posição corrente É o resultado. */
    private String acceptState() {
        String name = newName();
        List<KofOperation> ops = new ArrayList<>();
        ops.add(new KofLoadLocal(INT, SLOT_POS));
        ops.add(new KofReturn(INT));
        states.add(stateMethod(name, ops));
        return name;
    }

    /** Um átomo literal/âncora: casa e continua, ou falha com -1. */
    private String atomState(ExprAtomNode atom, String tail) {
        String name = newName();
        LabelId fail = LabelId.create();
        LabelId ok = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        switch (atom.kind()) {
            case "literal" -> {
                String lit = atom.values().isEmpty() ? "" : atom.values().get(0);
                ops.add(new KofLoadLocal(STR, SLOT_S));
                ops.add(KofLoadLiteral.ofString(lit));
                ops.add(new KofLoadLocal(INT, SLOT_POS));
                ops.add(new KofCall(STR, "startsWith", List.of(STR, INT), BOOL, KofCallKind.INSTANCE));
                ops.add(KofLoadLiteral.ofBool(true));
                ops.add(new KofConditionalJump(KofComparison.EQ, BOOL, ok, fail));
                ops.add(new KofLabel(ok));
                ops.add(new KofLoadLocal(INT, SLOT_POS));
                ops.add(KofLoadLiteral.ofInt(lit.length()));
                ops.add(new KofBinary(KofBinaryOp.ADD, INT));
                ops.add(new KofStoreLocal(INT, SLOT_POS));
            }
            case "start" -> {
                ops.add(new KofLoadLocal(INT, SLOT_POS));
                ops.add(KofLoadLiteral.ofInt(0));
                ops.add(new KofConditionalJump(KofComparison.EQ, INT, ok, fail));
                ops.add(new KofLabel(ok));
            }
            default -> { // "end" — validate recusou as demais classes
                ops.add(new KofLoadLocal(INT, SLOT_POS));
                ops.add(new KofLoadLocal(STR, SLOT_S));
                ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
                ops.add(new KofConditionalJump(KofComparison.EQ, INT, ok, fail));
                ops.add(new KofLabel(ok));
            }
        }
        emitContinue(ops, tail);
        ops.add(new KofLabel(fail));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofReturn(INT));
        states.add(stateMethod(name, ops));
        return name;
    }

    /**
     * Os três estados de um quantificador: {@code loop} decide se cabe mais uma
     * instância (greedy) e cai na cauda; {@code after} cobra a instância e volta
     * ao laço com o contador incrementado; {@code body} é o corpo compilado
     * continuando em {@code after}.
     *
     * <p>O contador é a PROPRIEDADE DA OCORRÊNCIA (um slot próprio, não um por
     * profundidade: dois quantificadores irmãos no mesmo nível contariam um pelo
     * outro) e os slots do corpo são ZERADOS a cada instância (o corpo deve
     * começar do zero — sem isso um {@code exactly(2)} aninhado entraria com a
     * contagem da instância anterior e casaria vazio).
     *
     * <p>Quando o corpo recusa, o laço NÃO desiste: desenrola para a cauda com a
     * contagem corrente — é exatamente o backtracking que o casamento precisa.
     */
    private String quantifierStates(ExprQuantifierNode q, String tail) {
        int[] bound = boundsOf(q);
        int min = bound[0];
        int max = bound[1];
        int slot = counterSlot();
        int innerFrom = slot + 1;
        int innerTo = innerFrom + quantifierCount(q.target());
        String loop = newName();
        String after = newName();
        String body = compileUnit(q.target(), after);

        // after: o corpo casou — conta a instância e volta ao laço.
        List<KofOperation> afterOps = new ArrayList<>();
        afterOps.add(new KofLoadLocal(INT, slot));
        afterOps.add(KofLoadLiteral.ofInt(1));
        afterOps.add(new KofBinary(KofBinaryOp.ADD, INT));
        afterOps.add(new KofStoreLocal(INT, slot));
        emitContinue(afterOps, loop);
        states.add(stateMethod(after, afterOps));

        // loop: greedy — tenta mais uma instância ANTES da cauda; desenrola na cauda.
        LabelId tryBody = LabelId.create();
        LabelId tryTail = LabelId.create();
        LabelId tryFallthrough = LabelId.create();
        LabelId done = LabelId.create();
        LabelId absent = LabelId.create();
        List<KofOperation> loopOps = new ArrayList<>();
        if (max >= 0) {
            loopOps.add(new KofLoadLocal(INT, slot));
            loopOps.add(KofLoadLiteral.ofInt(max));
            loopOps.add(new KofConditionalJump(KofComparison.LT, INT, tryBody, tryTail));
        } else {
            loopOps.add(new KofJump(tryBody));
        }
        loopOps.add(new KofLabel(tryBody));
        // zera os contadores internos: cada instância do corpo começa do zero
        for (int s = innerFrom; s < innerTo; s++) {
            loopOps.add(KofLoadLiteral.ofInt(0));
            loopOps.add(new KofStoreLocal(INT, s));
        }
        opsCall(loopOps, body);
        loopOps.add(new KofStoreLocal(INT, tempSlot()));
        loopOps.add(new KofLoadLocal(INT, tempSlot()));
        loopOps.add(KofLoadLiteral.ofInt(-1));
        loopOps.add(new KofConditionalJump(KofComparison.NE, INT, done, tryTail));
        loopOps.add(new KofLabel(tryTail));
        if (min > 0) {
            // count >= min: segue para a cauda (o false-branch é o fall-through)
            loopOps.add(new KofLoadLocal(INT, slot));
            loopOps.add(KofLoadLiteral.ofInt(min));
            loopOps.add(new KofConditionalJump(KofComparison.GE, INT, tryFallthrough, absent));
            loopOps.add(new KofLabel(tryFallthrough));
            emitContinue(loopOps, tail);
        } else {
            emitContinue(loopOps, tail);
        }
        loopOps.add(new KofLabel(done));
        loopOps.add(new KofLoadLocal(INT, tempSlot()));
        loopOps.add(new KofReturn(INT));
        loopOps.add(new KofLabel(absent));
        loopOps.add(KofLoadLiteral.ofInt(-1));
        loopOps.add(new KofReturn(INT));
        states.add(stateMethod(loop, loopOps));
        return loop;
    }

    /** Chama o estado seguinte com (s, pos, contadores) e devolve o que ele devolver. */
    private void emitContinue(List<KofOperation> ops, String nextState) {
        opsCall(ops, nextState);
        ops.add(new KofReturn(INT));
    }

    /** Empilha (s, pos, c0..cN) e chama o estado. */
    private void opsCall(List<KofOperation> ops, String state) {
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofLoadLocal(INT, SLOT_POS));
        List<Type> params = new ArrayList<>(List.of(STR, INT));
        for (int i = 0; i < slots; i++) {
            ops.add(new KofLoadLocal(INT, SLOT_FIRST_COUNT + i));
            params.add(INT);
        }
        ops.add(new KofCall(self, state, params, INT, KofCallKind.STATIC));
    }

    private int counterSlot() {
        return SLOT_FIRST_COUNT + (nextSlot++);
    }

    private int tempSlot() {
        return SLOT_FIRST_COUNT + slots;
    }

    // ---------------------------------------------------------------- operações públicas

    /** {@code matches(String): Bool} — sujeito inteiro: o estado final tem de ser o fim do texto. */
    private IRMethod publicMatches(String entry) {
        LabelId pass = LabelId.create();
        LabelId fail = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(KofLoadLiteral.ofInt(0));
        List<Type> params = new ArrayList<>(List.of(STR, INT));
        for (int i = 0; i < slots; i++) {
            ops.add(KofLoadLiteral.ofInt(0));
            params.add(INT);
        }
        ops.add(new KofCall(self, entry, params, INT, KofCallKind.STATIC));
        ops.add(new KofStoreLocal(INT, 1));
        ops.add(new KofLoadLocal(INT, 1));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
        ops.add(new KofConditionalJump(KofComparison.EQ, INT, pass, fail));
        ops.add(new KofLabel(pass));
        ops.add(KofLoadLiteral.ofBool(true));
        ops.add(new KofReturn(BOOL));
        ops.add(new KofLabel(fail));
        ops.add(KofLoadLiteral.ofBool(false));
        ops.add(new KofReturn(BOOL));
        return new IRMethod("matches", BOOL, List.of(STR),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)),
                List.of(new IRLocalVariable(SLOT_S, "s", STR),
                        new IRLocalVariable(1, "end", INT)));
    }

    /** {@code match(String): String?} — o sujeito quando casa, null quando não. */
    private IRMethod publicMatch() {
        LabelId absent = LabelId.create();
        LabelId present = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(self, "matches", List.of(STR), BOOL, KofCallKind.STATIC));
        ops.add(KofLoadLiteral.ofBool(false));
        ops.add(new KofConditionalJump(KofComparison.EQ, BOOL, absent, present));
        ops.add(new KofLabel(present));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofReturn(STR));
        ops.add(new KofLabel(absent));
        ops.add(KofLoadLiteral.ofNull());
        ops.add(new KofReturn(STR));
        return new IRMethod("match", new Type.NullableType(STR), List.of(STR),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)),
                List.of(new IRLocalVariable(SLOT_S, "s", STR)));
    }

    // ---------------------------------------------------------------- apoio

    private IRMethod stateMethod(String name, List<KofOperation> ops) {
        List<Type> params = new ArrayList<>(List.of(STR, INT));
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(SLOT_S, "s", STR));
        locals.add(new IRLocalVariable(SLOT_POS, "pos", INT));
        for (int i = 0; i < slots; i++) {
            params.add(INT);
            locals.add(new IRLocalVariable(SLOT_FIRST_COUNT + i, "c" + i, INT));
        }
        locals.add(new IRLocalVariable(tempSlot(), "end", INT));
        return new IRMethod(name, INT, params, AccessFlags.PRIVATE | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    private String newName() {
        return "kx" + (uid++);
    }

    private boolean refuse(String code, SourcePosition p, String message) {
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0,
                    p != null ? p.length() : 0, message + " (" + code + ")", code);
        }
        return false;
    }
}