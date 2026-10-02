package dev.kof.compiler.js;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofComparison;
import dev.kof.compiler.KofContinueLabel;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStatementIf;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.LabelId;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsControlFlowParser — reconhece as estruturas de controle do IR (if/while/for/do-while/try) e produz JsIr nativo de JS (REFACTOR-500 FASE 4).
 */
public final class JsControlFlowParser {

    private final JsMethodParser p;
    private final JsTryParser tryParser;

    JsControlFlowParser(JsMethodParser p) {
        this.p = p;
        this.tryParser = new JsTryParser(this, p);
    }

    /**
     * Parses a statement list. The region ends when:
     *  - a label/jump in endLabels is encountered (consumed);
     *  - a jump to an unknown label is found (region exit, consumed);
     *  - the continue label of the enclosing for-loop is found (not consumed);
     *  - an unmatched label (belongs to the enclosing pattern) is found
     *    (not consumed).
     * Region exits are recorded in exits (jump targets).
     */
List<JsIr.JsStatement> parseStatements(MethodCtx ctx, int[] pos,
                                                   Set<LabelId> endLabels, List<LabelId> exits) {
        List<JsIr.JsStatement> out = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof KofContinueLabel) {
                // §266: fronteira corpo/update marcada no lowering. NÃO
                // consumir — o dono (parseLoop do loop a que pertence, dado
                // pelo loopStart do marcador) come marcador + Label(continue).
                return out;
            }
            if (op instanceof KofLabel kl) {
                if (endLabels.contains(kl.label())) {
                    pos[0]++;
                    exits.add(kl.label());
                    return out;
                }
                if (ctx.isLoopLabel(kl.label())) {
                    // §266: a fronteira corpo/update vem do marcador do lowering
                    // (LoopCtx carrega o continue label REAL, rotulado pelo
                    // startLabel do dono). O antigo guess post-hoc
                    // (looksLikeContinueLabel — varredura p/ o próximo
                    // Jump(start)) confundia o Label(end) de um `if` sem
                    // `else` no fim do corpo com o continue label → o corpo
                    // quebrava cedo e a cauda do `if` virava update-clause
                    // fora do escopo dos próprios `let` (ReferenceError
                    // silencioso; vazio no browser).
                    return out;
                }
                if (JsLabelParser.isLoopStart(ctx, pos, kl.label())) {
                    out.add(parseLoop(ctx, pos, kl.label()));
                    continue;
                }
                // unmatched label — the enclosing pattern owns it
                return out;
            }
            if (op instanceof KofJump kj) {
                if (endLabels.contains(kj.target())) {
                    pos[0]++;
                    exits.add(kj.target());
                    return out;
                }
                if (ctx.isLoopLabel(kj.target())) {
                    pos[0]++;
                    if (ctx.isLoopEnd(kj.target())) {
                        out.add(new JsIr.JsBreak());
                    } else {
                        out.add(new JsIr.JsContinue());
                    }
                    continue;
                }
                // region exit (if/try/finally jump)
                pos[0]++;
                exits.add(kj.target());
                return out;
            }
            if (op instanceof KofTryEnd) {
                // fim da região do try — o dono (parseTryStatement) consome
                return out;
            }
            if (op instanceof dev.kof.compiler.KofExcUnlink) {
                // §549: pop de handler do caminho normal — o JS reconstrói a
                // estrutura try/catch/finally; nada a emitir.
                pos[0]++;
                continue;
            }
            out.addAll(parseStatement(ctx, pos));
        }
        return out;
    }

    /**
     * The continue label of the enclosing for-loop: a label followed by the
     * update statements and the back-edge jump to the loop start.
     */

    /**
     * A label is a loop start when a later instruction jumps to it (back edge)
     * or conditionally jumps to it (do-while condition).
     */
List<JsIr.JsStatement> parseStatement(MethodCtx ctx, int[] pos) {
        KofOperation op = ctx.ops.get(pos[0]);
        if (op instanceof KofStatementIf si) {
            // §267: `if` de STATEMENT (marcado no lowering). Consome o marcador
            // e registra o trueLabel do CJump do ramo; o dispatcher de
            // statements (em JsExpressionStatementParser) pula a dobra em
            // if-expressao quando bate nesse label (a ternária engoliria o
            // statement seguinte = leitura obsoleta, §267). Delega no
            // dispatcher de expressao, que ve a condicao + o CJump e cai em
            // parseIfBody (sem tryParseIfExpr).
            ctx.statementIfLabels.add(si.branchTrueLabel());
            pos[0]++;
            return p.expr.parseExpressionStatement(ctx, pos);
        }
        if (op instanceof KofReturnVoid) {
            pos[0]++;
            return List.of(new JsIr.JsReturn(null));
        }
        if (op instanceof KofTryStart) {
            return List.of(tryParser.parse(ctx, pos));
        }
        if (op instanceof KofLoadLocal ll && "#switch".equals(ctx.rawLocalNames.get(ll.index()))) {
            // Distinguish switch test (load #switch + instanceof or case value) from
            // pattern body prologue (load #switch + checkcast). The body prologue
            // should be handled as a normal store expression, not as a switch.
            if (pos[0] + 1 < ctx.ops.size() && ctx.ops.get(pos[0] + 1) instanceof KofCheckCast) {
                // pattern body: let s = #switch; fall through to expression handling
            } else {
                return List.of(p.sw.parseSwitchStatement(ctx, pos));
            }
        }
        if (op instanceof KofJump kj) {
            pos[0]++;
            if (ctx.isLoopEnd(kj.target())) {
                return List.of(new JsIr.JsBreak());
            }
            return List.of(new JsIr.JsContinue());
        }
        if (op instanceof KofLabel) {
            throw new IllegalStateException("KofJS: unexpected label at statement level");
        }
        if (op instanceof KofCatchStart) {
            for (int k=0;k<ctx.ops.size();k++) System.err.println("  " + k + ": " + ctx.ops.get(k).getClass().getSimpleName());
            throw new IllegalStateException("KofJS: unexpected KofCatchStart at statement level");
        }
        return p.expr.parseExpressionStatement(ctx, pos);
    }

    /**
     * Statement-level if: [cond ops, CJump, Label(true), then, Jump(end),
     * Label(false), (else), Label(end)].
     */
JsIr.JsStatement parseIfBody(MethodCtx ctx, int[] pos, KofConditionalJump cj,
                                          JsIr.JsExpression condition) {
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl && kl.label().equals(cj.trueLabel()))) {
            throw new IllegalStateException("KofJS: if pattern expected Label(true)");
        }
        pos[0]++;
        // §380: falseLabel ativo na pilha — um `if` interno nunca consome label de
        // estrutura envolvente (exceção aborta a compilação: sem leak de pilha).
        ctx.enclosingIfFalses.push(cj.falseLabel());
        List<JsIr.JsStatement> thenBranch = parseStatements(ctx, pos, Set.of(cj.falseLabel()), new ArrayList<>());
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl2
                && kl2.label().equals(cj.falseLabel())) {
            pos[0]++;
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
            // Label(end) — no else branch. A loop label (continue/start) or o
            // endLabel de um try ENVOLVENTE não é do if (consumi-lo deixa o
            // KofCatchStart solto no statement level — COMP002, §174).
            KofLabel end = (KofLabel) ctx.ops.get(pos[0]);
            if (ctx.isIfEndLabel(pos, end.label())
                    && !ctx.isEnclosingIfFalse(end.label())) { // §380: fronteira alheia
                pos[0]++;
            }
            ctx.enclosingIfFalses.pop();
            return new JsIr.JsIf(condition, thenBranch, List.of());
        }
        // §147 (12/09, #101 — detalhe em JsIfThrowElse): IR linear sem
        // Jump/Label de end; o else pára antes do trailing-return do método
        // (senão ganha `return` fantasma e o epílogo "some").
        List<JsIr.JsStatement> elseBranch;
        if (JsIfThrowElse.thenEndsUnconditional(thenBranch)) {
            elseBranch = JsIfThrowElse.parseElse(this, ctx, pos);
        } else {
            elseBranch = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl3
                && ctx.isIfEndLabel(pos, kl3.label())
                && !ctx.isEnclosingIfFalse(kl3.label())) { // §380
            // Label(end) — end of else branch (loop labels belong to the loop)
            pos[0]++;
        }
        ctx.enclosingIfFalses.pop();
        return new JsIr.JsIf(condition, thenBranch, elseBranch);
    }

    /**
     * Attempts to parse an if-expression: [Label(true), expr, Jump(end),
     * Label(false), expr, Label(end)]. Returns null (restoring the position)
     * when the upcoming ops form a statement-level if instead.
     */
JsIr.JsExpression tryParseIfExpr(MethodCtx ctx, int[] pos, KofConditionalJump cj,
                                             JsIr.JsExpression condition) {
        int saved = pos[0];
        try {
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl && kl.label().equals(cj.trueLabel()))) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            JsIr.JsExpression thenExpr = p.expr.parseExpressionFragment(ctx, pos);
            if (!(ctx.ops.get(pos[0]) instanceof KofJump)) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl2 && kl2.label().equals(cj.falseLabel()))) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            JsIr.JsExpression elseExpr = p.expr.parseExpressionFragment(ctx, pos);
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel)) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            return new JsIr.JsConditional(condition, thenExpr, elseExpr);
        } catch (RuntimeException e) {
            // statement-level if: expressions in the branches failed, so the
            // construct is a statement, not an if-expression
            pos[0] = saved;
            return null;
        }
    }

JsIr.JsStatement parseLoop(MethodCtx ctx, int[] pos, LabelId startLabel) {
        pos[0]++;
        // A do-while loop is the only construct whose conditional jump targets
        // its own start label; scan the whole remaining stream to find it.
        KofConditionalJump doWhileJump = null;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofConditionalJump cj && cj.trueLabel().equals(startLabel)) {
                doWhileJump = cj;
                break;
            }
        }
        if (doWhileJump != null) {
            return parseDoWhile(ctx, pos, startLabel, doWhileJump);
        }
        // while / for: condition ops, CJump(body, end), Label(body), body, ...
        // while / for / for-in: condition ops, CJump(body, end), Label(body), body, ...
        // A condition region contains a CJump before any statement boundary;
        // otherwise the optimizer folded while(true) into a direct jump and
        // the ops are the loop body (parse it without consuming anything).
        boolean hasCondition = false;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofConditionalJump) {
                hasCondition = true;
                break;
            }
            if (op instanceof KofStoreLocal || op instanceof KofLabel
                    || op instanceof KofJump || op instanceof KofReturn
                    || op instanceof KofReturnVoid || op instanceof KofThrow
                    || op instanceof KofPop) {
                break;
            }
        }
        if (!hasCondition) {
            // while (true): [Label(start), body..., Jump(start), Label(end)]
            return parseTrueLoop(ctx, pos, startLabel);
        }
        List<Object> condStack = new ArrayList<>();
        List<JsIr.JsExpression> condPreamble = new ArrayList<>();
        while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofConditionalJump)) {
            if (ctx.ops.get(pos[0]) instanceof KofStoreLocal
                    || !p.expr.isExpressionOp(ctx.ops.get(pos[0]))) {
                break;
            }
            p.expr.consumeExpressionOp(ctx, pos, condStack, condPreamble);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj2)) {
            throw new IllegalStateException("KofJS: loop condition not terminated");
        }
        pos[0]++;
        JsIr.JsExpression right = p.expr.pop(condStack);
        JsIr.JsExpression left = p.expr.pop(condStack);
        if (!condStack.isEmpty()) {
            throw new IllegalStateException("KofJS: malformed loop condition stack");
        }
        JsIr.JsExpression condition = JsComparisons.comparisonExpr(cj2.comparison(), left, right, cj2.operandType());
        if (!condPreamble.isEmpty()) {
            condition = new JsIr.JsSequence(condPreamble, condition);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel bodyLabel && bodyLabel.label().equals(cj2.trueLabel()))) {
            throw new IllegalStateException("KofJS: loop body label mismatch");
        }
        pos[0]++;
        // §266: fronteira corpo/update = marcador EMITIDO NO LOWERING
        // (KofContinueLabel rotulado com o startLabel do loop dono), varrido
        // para FRENTE a partir do início do corpo — match exato `loopStart ==
        // startLabel` ignora marcadores de loops INTERNOS. A antiga varredura
        // para trás (label antes do back-edge) confundia o Label(endX) de um
        // `if` sem `else` no fim do corpo com o continue label: os statements
        // posteriores ao `if` caíam na cláusula update do `for(;…;…)` fora do
        // escopo dos próprios `let` → ReferenceError silencioso (vazio no
        // browser, kofUiRender engolia). Sem marcador = não é for: while.
        LabelId continueLabel = startLabel;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofContinueLabel cl && cl.loopStart().equals(startLabel)) {
                continueLabel = cl.label();
                break;
            }
        }
        ctx.loops.add(new LoopCtx(startLabel, continueLabel, cj2.falseLabel()));
        List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(startLabel), new ArrayList<>());
        ctx.loops.remove(ctx.loops.size() - 1);
        // After the body: either Jump(start) (while) or [Label(continue) +
        // update +] Jump(start) (for), the for-shape identified by the marker.
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofContinueLabel marker
                && marker.loopStart().equals(startLabel) && !startLabel.equals(continueLabel)) {
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel cl2 && cl2.label().equals(continueLabel))) {
                throw new IllegalStateException("KofJS: for-loop expected Label(continue) after marker");
            }
            pos[0]++;
            List<JsIr.JsStatement> update = new ArrayList<>();
            while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofJump)) {
                update.addAll(parseStatement(ctx, pos));
            }
            if (!(ctx.ops.get(pos[0]) instanceof KofJump kj) || !kj.target().equals(startLabel)) {
                throw new IllegalStateException("KofJS: for-loop expected Jump(start)");
            }
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel end && end.label().equals(cj2.falseLabel()))) {
                throw new IllegalStateException("KofJS: for-loop expected Label(end)");
            }
            pos[0]++;
            return new JsIr.JsFor(List.of(), condition, update, body);
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump kj
                && kj.target().equals(startLabel)) {
            pos[0]++;
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel end && end.label().equals(cj2.falseLabel()))) {
            throw new IllegalStateException("KofJS: loop expected Label(end)");
        }
        pos[0]++;
        return new JsIr.JsWhile(condition, body, false);
    }

    /**
     * while (true): the optimizer folds the literal-true condition into a
     * direct jump, leaving [Label(start), body..., Jump(start), Label(end)].
     */
JsIr.JsStatement parseTrueLoop(MethodCtx ctx, int[] pos, LabelId startLabel) {
        LabelId endLabel = null;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofJump kj && kj.target().equals(startLabel)
                    && i + 1 < ctx.ops.size()
                    && ctx.ops.get(i + 1) instanceof KofLabel kl) {
                endLabel = kl.label();
            }
        }
        if (endLabel == null) {
            throw new IllegalStateException("KofJS: true-loop end label not found");
        }
        // §266: `for(;;)` (condição nula) chega sem CJump e cai aqui; o
        // marcador é a única forma de distingui-lo de `while (true)`.
        LabelId trueContinue = startLabel;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofContinueLabel cl && cl.loopStart().equals(startLabel)) {
                trueContinue = cl.label();
                break;
            }
        }
        ctx.loops.add(new LoopCtx(startLabel, trueContinue, endLabel));
        List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(startLabel), new ArrayList<>());
        ctx.loops.remove(ctx.loops.size() - 1);
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofContinueLabel marker
                && marker.loopStart().equals(startLabel) && !startLabel.equals(trueContinue)) {
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel cl2 && cl2.label().equals(trueContinue))) {
                throw new IllegalStateException("KofJS: for(;;) expected Label(continue) after marker");
            }
            pos[0]++;
            List<JsIr.JsStatement> update = new ArrayList<>();
            while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofJump)) {
                update.addAll(parseStatement(ctx, pos));
            }
            if (!(ctx.ops.get(pos[0]) instanceof KofJump kj) || !kj.target().equals(startLabel)) {
                throw new IllegalStateException("KofJS: for(;;) expected Jump(start)");
            }
            pos[0]++;
            if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl2
                    && kl2.label().equals(endLabel)) {
                pos[0]++;
            }
            return new JsIr.JsFor(List.of(), new JsIr.JsNumber("1"), update, body);
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump kj
                && kj.target().equals(startLabel)) {
            pos[0]++;
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl
                && kl.label().equals(endLabel)) {
            pos[0]++;
        }
        return new JsIr.JsWhile(new JsIr.JsNumber("1"), body, false);
    }

JsIr.JsStatement parseDoWhile(MethodCtx ctx, int[] pos, LabelId startLabel,
                                          KofConditionalJump loopJump) {
        ctx.loops.add(new LoopCtx(startLabel, startLabel, loopJump.falseLabel()));
        List<JsIr.JsStatement> body = new ArrayList<>();
        while (true) {
            if (pos[0] >= ctx.ops.size()) {
                throw new IllegalStateException("KofJS: do-while condition not found");
            }
            if (isDoWhileConditionAhead(ctx, pos, startLabel)) {
                break;
            }
            body.addAll(parseStatement(ctx, pos));
        }
        ctx.loops.remove(ctx.loops.size() - 1);
        List<Object> condStack = new ArrayList<>();
        List<JsIr.JsExpression> condPreamble = new ArrayList<>();
        while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofConditionalJump)) {
            if (!p.expr.isExpressionOp(ctx.ops.get(pos[0]))) {
                throw new IllegalStateException("KofJS: unexpected op in do-while condition: " + ctx.ops.get(pos[0]));
            }
            p.expr.consumeExpressionOp(ctx, pos, condStack, condPreamble);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj)) {
            throw new IllegalStateException("KofJS: do-while condition not terminated");
        }
        pos[0]++;
        JsIr.JsExpression right = p.expr.pop(condStack);
        JsIr.JsExpression left = p.expr.pop(condStack);
        JsIr.JsExpression condition = JsComparisons.comparisonExpr(cj.comparison(), left, right, cj.operandType());
        while (!condStack.isEmpty()) {
            condition = new JsIr.JsSequence(List.of(p.expr.pop(condStack)), condition);
        }
        if (!condPreamble.isEmpty()) {
            condition = new JsIr.JsSequence(condPreamble, condition);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel end && end.label().equals(loopJump.falseLabel()))) {
            throw new IllegalStateException("KofJS: do-while expected Label(end)");
        }
        pos[0]++;
        return new JsIr.JsWhile(condition, body, true);
    }

boolean isDoWhileConditionAhead(MethodCtx ctx, int[] pos, LabelId startLabel) {
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofConditionalJump cj) {
                return cj.trueLabel().equals(startLabel);
            }
            // Statement boundaries end the body: a store (e.g. the body's
            // last assignment), a label, a jump or a return.
            if (op instanceof KofStoreLocal || op instanceof KofLabel
                    || op instanceof KofJump || op instanceof KofReturn
                    || op instanceof KofReturnVoid || op instanceof KofThrow) {
                return false;
            }
            if (!p.expr.isExpressionOp(op)) {
                return false;
            }
        }
        return false;
    }

}