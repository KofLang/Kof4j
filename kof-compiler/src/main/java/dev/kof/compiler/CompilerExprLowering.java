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

    private final CompilerDriver driver;
    private final Type self;
    /** Número TOTAL de ocorrências de quantificador = número de slots de contador. */
    private final int slots;
    /** Os nomes de captura na ordem de declaração (fatia 5, Opção T). */
    private final List<String> captures;
    final List<IRMethod> states = new ArrayList<>();
    private int uid;
    private int nextSlot;

    CompilerExprLowering(CompilerDriver driver, Type self, int slots, List<String> captures) {
        this.driver = driver;
        this.self = self;
        this.slots = slots;
        this.captures = captures;
    }

    /** O primeiro slot de CHAVE (o valor capturado, String) — após os contadores. */
    static int keyBaseOf(int slots) {
        return SLOT_FIRST_COUNT + slots;
    }

    /**
     * @return as CLASSES do padrão — o matcher e, se houver capturas, o record
     * gerado da Opção T — ou vazia quando o corpo recusou (diagnóstico já
     * emitido). A montagem da superfície pública vive em
     * {@link KofExprMatchResult}.
     */
    static List<IRClass> lower(CompilerDriver driver, ExprDeclarationNode ex, String packageName, int typeId) {
        return KofExprMatchResult.lower(driver, ex, packageName, typeId);
    }

    // ---------------------------------------------------------------- validação

    /**
     * Recusa honesta antes de emitir qualquer estado: EXPR001 (fora do conjunto
     * das fatias), EXPR002 (sem teto dentro de sem teto) e EXPR003 (corpo
     * anulável num laço sem teto). @return true quando o corpo é baixável.
     */
    boolean validate(ExprNode node) {
        if (node instanceof ExprSequenceNode seq) {
            for (ExprNode unit : seq.units()) if (!validate(unit)) return false;
            return true;
        }
        if (node instanceof ExprAtomNode atom) return validateAtom(atom);
        if (node instanceof ExprQuantifierNode q) {
            int[] bound = boundsOf(q);
            if (bound == null) return false;
            if (bound[1] < 0) {
                if (KofExprShape.containsUnbounded(q.target())) {
                    return refuse("EXPR002", q.position(), "'" + q.kind() + "' contains another quantifier "
                            + "with no limit, which can backtrack exponentially (ReDoS): bound the inner "
                            + "loop with exactly/between, or split the pattern");
                }
                if (KofExprShape.nullable(q.target())) {
                    return refuse("EXPR003", q.position(), "'" + q.kind() + "' over a body that can match "
                            + "empty text would never advance: the body must require at least one character");
                }
            }
            return validate(q.target());
        }
        if (node instanceof ExprAlternationNode a) {
            // Fatia 4: a alternação é do conjunto — o §12 desce em cada ramo.
            if (a.alternatives().isEmpty()) {
                return refuse("EXPR005", a.position(), "'either' needs at least one branch");
            }
            for (ExprNode branch : a.alternatives()) if (!validate(branch)) return false;
            return true;
        }
        if (node instanceof ExprCaptureNode c) {
            // Fatia 5 (Opção T): o nome vira CAMPO do record gerado — precisa
            // ser identificador Kof válido.
            if (!c.name().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return refuse("EXPR005", c.position(), "capture('" + c.name() + "') needs a name that is a "
                        + "valid Kof identifier: it becomes a field of the generated match record");
            }
            return validate(c.body());
        }
        if (node instanceof ExprReferenceNode r) {
            return refuse("EXPR001", r.position(), "pattern reference '" + r.alias() + "' is not part of "
                    + "Kof.expr slice 4 (… + groups + either): composition enters with a later slice");
        }
        return refuse("EXPR001", node.position(), "this pattern element is not part of Kof.expr slice 4 "
                + "(… + groups + either): it enters with a later slice");
    }

    private boolean validateAtom(ExprAtomNode atom) {
        switch (atom.kind()) {
            case "literal", "start", "end" -> { return true; }
            case "one_of", "none_of" -> {
                // §3.1: membership over one-character literals — anything longer
                // is not a membership atom and says so.
                for (String v : atom.values()) {
                    if (KofExprShape.codePointCount(v) != 1) {
                        return refuse("EXPR005", atom.position(), "'" + atom.kind() + "' takes text literals of "
                                + "exactly one character, and \"" + v + "\" is not one: use a sequence of "
                                + "literals for a multi-character match");
                    }
                }
                if (atom.values().isEmpty()) {
                    return refuse("EXPR005", atom.position(), "'" + atom.kind() + "' needs at least one "
                            + "one-character literal");
                }
                return true;
            }
            default -> { }
        }
        if (KofExprUnicode.CLASSES.contains(atom.kind())) {
            return targetSupportsClasses(atom);
        }
        return refuse("EXPR001", atom.position(), "'" + atom.kind() + "' is not a Kof.expr pattern element");
    }

    /**
     * O gate honesto de alvo da fatia 3: o runtime {@code kof_expr_is_*} existe
     * em JVM e Script; JS e Native ainda não o têm, então recusam com EXPR004
     * nomeando a fatia que os traz (7 e 6) — nunca um casamento silencioso que
     * responde a classe errada.
     */
    private boolean targetSupportsClasses(ExprAtomNode atom) {
        if (atom.kind().equals("any")) return true; // não precisa de tabela: lê o ponto de código
        Target t = driver.target;
        if (driver.interpreting || t == Target.JVM || t == Target.ANDROID) return true;
        if (t == Target.JS) {
            return refuse("EXPR004", atom.position(), "the Kof.expr character classes are not on the JS target "
                    + "yet ('" + atom.kind() + "'): the JS runtime predicates enter with slice 7");
        }
        if (t == Target.NATIVE) {
            return refuse("EXPR004", atom.position(), "the Kof.expr character classes are not on the Native "
                    + "target yet ('" + atom.kind() + "'): the native runtime predicates enter with slice 6");
        }
        return refuse("EXPR004", atom.position(), "the Kof.expr character classes are not on the " + t
                + " target yet ('" + atom.kind() + "')");
    }

    /** Quantos VALORES ESCALARES Unicode (não unidades UTF-16) o texto tem. */
    private static int codePointCount(String text) {
        return text.codePointCount(0, text.length());
    }

    /** O intervalo de um quantificador: os três nomeados, ou exactly/between com as contagens. */
    private int[] boundsOf(ExprQuantifierNode q) {
        int[] named = KofExprShape.namedBound(q.kind());
        if (named != null) return named;
        if (q.kind().equals("exactly")) return new int[]{q.min(), q.min()};
        if (q.kind().equals("between")) return new int[]{q.min(), q.max()};
        refuse("EXPR001", q.position(), "'" + q.kind() + "' is not a Kof.expr slice-2 quantifier");
        return null;
    }

    // ---------------------------------------------------------------- geração

    /** A cadeia das unidades, da última para a primeira: cada uma continua na seguinte. */
    String compileUnits(List<ExprNode> units, String tail) {
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
        if (unit instanceof ExprAlternationNode alt) return alternationStates(alt, tail);
        if (unit instanceof ExprCaptureNode cap) return captureStates(cap, tail);
        return tail; // validate() recusou: inalcançável
    }

    /**
     * Os dois estados de uma CAPTURA (fatia 5, Opção T): o estado de entrada
     * grava a posição de início e compila o corpo continuando no
     * {@code after}; o retorno do corpo guarda {@code substring(start, end)}
     * na CHAVE da captura e segue para a cauda. O backtracking do corpo
     * reescreve a chave (a última escrita do caminho bem-sucedido vence — o
     * aceite congela os valores).
     */
    private String captureStates(ExprCaptureNode cap, String tail) {
        int i = captures.indexOf(cap.name());
        String entry = newName();
        String after = newName();
        String body = compileUnit(cap.body(), after);

        List<KofOperation> entryOps = new ArrayList<>();
        entryOps.add(new KofLoadLocal(INT, SLOT_POS));
        entryOps.add(new KofStoreLocal(INT, startSlot(i)));
        opsCall(entryOps, body);
        entryOps.add(new KofReturn(INT));
        states.add(stateMethod(entry, entryOps));

        List<KofOperation> afterOps = new ArrayList<>();
        // keys[i] = s.substring(p_i, pos) — o array compartilhado é o canal
        afterOps.add(new KofLoadLocal(new Type.ArrayType(STR), keyArraySlot()));
        afterOps.add(KofLoadLiteral.ofInt(i));
        afterOps.add(new KofLoadLocal(STR, SLOT_S));
        afterOps.add(new KofLoadLocal(INT, startSlot(i)));
        afterOps.add(new KofLoadLocal(INT, SLOT_POS));
        afterOps.add(new KofCall(STR, "substring", List.of(INT, INT), STR, KofCallKind.INSTANCE));
        afterOps.add(new KofArrayStore(STR));
        opsCall(afterOps, tail);
        afterOps.add(new KofReturn(INT));
        states.add(stateMethod(after, afterOps));
        return entry;
    }

    /**
     * A ALTERNATIVA (fatia 4, §3.3): escolha ORDENADA. Cada ramo é compilado
     * como um caminho que continua na MESMA cauda; o estado de escolha tenta o
     * ramo 1 e, se ele devolver -1 (o próprio ramo recusou OU a cauda recusou
     * depois dele), tenta o ramo 2 — o desfazer é automático porque cada
     * tentativa reentra com a posição ORIGINAL (o estado é uma função pura de
     * (s, pos, c*)). O último ramo devolve o que ele devolver.
     */
    private String alternationStates(ExprAlternationNode alt, String tail) {
        String entry = newName();
        LabelId doneAlt = LabelId.create();
        List<String> branches = new ArrayList<>();
        for (ExprNode b : alt.alternatives()) branches.add(compileUnit(b, tail));

        List<KofOperation> ops = new ArrayList<>();
        LabelId entryLabel = LabelId.create();
        ops.add(new KofLabel(entryLabel));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofStoreLocal(INT, tempSlot()));
        List<LabelId> labels = new ArrayList<>();
        for (int i = 0; i < branches.size(); i++) labels.add(LabelId.create());
        ops.add(new KofJump(labels.get(0)));
        for (int i = 0; i < branches.size(); i++) {
            boolean last = i == branches.size() - 1;
            ops.add(new KofLabel(labels.get(i)));
            opsCall(ops, branches.get(i));
            if (last) {
                ops.add(new KofReturn(INT));
            } else {
                // o ramo (e a cauda depois dele) devolveu -1: desfaz e tenta o próximo
                ops.add(new KofStoreLocal(INT, tempSlot()));
                LabelId next = labels.get(i + 1);
                ops.add(new KofLoadLocal(INT, tempSlot()));
                ops.add(KofLoadLiteral.ofInt(-1));
                ops.add(new KofConditionalJump(KofComparison.NE, INT, doneAlt, next));
                ops.add(new KofLabel(next));
            }
        }
        ops.add(new KofLabel(doneAlt));
        ops.add(new KofLoadLocal(INT, tempSlot()));
        ops.add(new KofReturn(INT));
        states.add(stateMethod(entry, ops));
        return entry;
    }

    /**
     * Aceite de um casamento de SUJEITO INTEIRO: só devolve a posição quando
     * ela É o fim do texto, e -1 caso contrário. A imposição vive AQUI (e não
     * no {@code matches}) porque a alternação decide pelo resultado do ramo:
     * um ramo que casa pela metade tem de devolver -1 para o próximo ramo ser
     * tentado — o caso `either { "cat" or "catalog" }` sobre "catalog".
     */
    String acceptState() {
        String name = newName();
        LabelId full = LabelId.create();
        LabelId shortFall = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        ops.add(new KofLoadLocal(INT, SLOT_POS));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
        ops.add(new KofConditionalJump(KofComparison.EQ, INT, full, shortFall));
        ops.add(new KofLabel(full));
        ops.add(new KofLoadLocal(INT, SLOT_POS));
        ops.add(new KofReturn(INT));
        ops.add(new KofLabel(shortFall));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofReturn(INT));
        states.add(stateMethod(name, ops));
        return name;
    }

    /** Um átomo literal/âncora: casa e continua, ou falha com -1. */
    private String atomState(ExprAtomNode atom, String tail) {
        String name = newName();
        LabelId fail = LabelId.create();
        LabelId ok = LabelId.create();
        LabelId doneJoin = LabelId.create();
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
            case "one_of", "none_of" -> {
                // membership por PONTO DE CÓDIGO: lê o escalar em pos, guarda e
                // compara contra o ponto único de cada literal. `one_of` casa
                // quando ALGUM bate; `none_of` quando NENHUM bate.
                KofExprCodePoint.emitRead(ops, tempSlot());
                ops.add(new KofStoreLocal(INT, tempSlot()));
                LabelId hit = LabelId.create();
                for (String v : atom.values()) {
                    LabelId next = LabelId.create();
                    ops.add(new KofLoadLocal(INT, tempSlot()));
                    ops.add(KofLoadLiteral.ofInt(v.codePointAt(0)));
                    ops.add(new KofConditionalJump(KofComparison.EQ, INT, hit, next));
                    ops.add(new KofLabel(next));
                }
                // nenhum bateu
                if (atom.kind().equals("one_of")) {
                    ops.add(new KofJump(fail));
                } else {
                    KofExprCodePoint.emitAdvance(ops, tempSlot());
                    ops.add(new KofJump(doneJoin));
                }
                ops.add(new KofLabel(hit));
                if (atom.kind().equals("one_of")) {
                    KofExprCodePoint.emitAdvance(ops, tempSlot());
                    ops.add(new KofJump(doneJoin));
                } else {
                    ops.add(new KofJump(fail));
                }
                ops.add(new KofLabel(doneJoin));
            }
            case "any" -> {
                // o escalar em pos, seja ele qual for — mas TEM de existir
                KofExprCodePoint.emitRead(ops, tempSlot());
                ops.add(new KofStoreLocal(INT, tempSlot()));
                LabelId present = LabelId.create();
                ops.add(new KofLoadLocal(INT, tempSlot()));
                ops.add(KofLoadLiteral.ofInt(-1));
                ops.add(new KofConditionalJump(KofComparison.NE, INT, present, fail));
                ops.add(new KofLabel(present));
                KofExprCodePoint.emitAdvance(ops, tempSlot());
            }
            case "end" -> {
                ops.add(new KofLoadLocal(INT, SLOT_POS));
                ops.add(new KofLoadLocal(STR, SLOT_S));
                ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
                ops.add(new KofConditionalJump(KofComparison.EQ, INT, ok, fail));
                ops.add(new KofLabel(ok));
            }
            default -> {
                // as classes com tabela: lê o ponto, guarda (o avanço precisa
                // dele) e pergunta ao runtime
                String predicate = KofExprUnicode.predicateOf(atom.kind());
                KofExprCodePoint.emitRead(ops, tempSlot());
                ops.add(new KofStoreLocal(INT, tempSlot()));
                ops.add(new KofLoadLocal(INT, tempSlot()));
                ops.add(new KofCall(STR, predicate, List.of(INT), BOOL, KofCallKind.FUNCTION));
                ops.add(KofLoadLiteral.ofBool(true));
                ops.add(new KofConditionalJump(KofComparison.EQ, BOOL, ok, fail));
                ops.add(new KofLabel(ok));
                KofExprCodePoint.emitAdvance(ops, tempSlot());
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
        int innerTo = innerFrom + KofExprShape.quantifierCount(q.target());
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

    /** Empilha (s, pos, contadores, chaves, inícios) e chama o estado. */
    private void opsCall(List<KofOperation> ops, String state) {
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofLoadLocal(INT, SLOT_POS));
        List<Type> params = new ArrayList<>(List.of(STR, INT));
        for (int i = 0; i < slots; i++) {
            ops.add(new KofLoadLocal(INT, SLOT_FIRST_COUNT + i));
            params.add(INT);
        }
        if (!captures.isEmpty()) {
            ops.add(new KofLoadLocal(new Type.ArrayType(STR), keyArraySlot()));
            params.add(new Type.ArrayType(STR));
        }
        for (int i = 0; i < captures.size(); i++) {
            ops.add(new KofLoadLocal(INT, startSlot(i)));
            params.add(INT);
        }
        ops.add(new KofCall(self, state, params, INT, KofCallKind.STATIC));
    }

    private int counterSlot() {
        return SLOT_FIRST_COUNT + (nextSlot++);
    }

    /** O slot do ARRAY de chaves compartilhado (as capturas moram NELE, não em locais). */
    private int keyArraySlot() {
        return keyBaseOf(slots);
    }

    private int startSlot(int i) {
        return keyArraySlot() + 1 + i;
    }

    private int tempSlot() {
        return keyArraySlot() + 1 + captures.size();
    }

    // ---------------------------------------------------------------- operações públicas

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
        if (!captures.isEmpty()) {
            Type keys = new Type.ArrayType(STR);
            params.add(keys);
            locals.add(new IRLocalVariable(keyArraySlot(), "keys", keys));
        }
        for (int i = 0; i < captures.size(); i++) {
            params.add(INT);
            locals.add(new IRLocalVariable(startSlot(i), "p" + i, INT));
        }
        locals.add(new IRLocalVariable(tempSlot(), "end", INT));
        return new IRMethod(name, INT, params, AccessFlags.PRIVATE | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    String newName() {
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