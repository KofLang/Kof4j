package dev.kof.compiler;

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
final class JsControlFlowParser {

    private final JsMethodParser p;

    JsControlFlowParser(JsMethodParser p) {
        this.p = p;
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
            if (op instanceof KofLabel kl) {
                if (endLabels.contains(kl.label())) {
                    pos[0]++;
                    exits.add(kl.label());
                    return out;
                }
                if (ctx.isLoopLabel(kl.label()) || looksLikeContinueLabel(ctx, pos, kl.label())) {
                    return out;
                }
                if (isLoopStart(ctx, pos, kl.label())) {
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
            out.addAll(parseStatement(ctx, pos));
        }
        return out;
    }

    /**
     * The continue label of the enclosing for-loop: a label followed by the
     * update statements and the back-edge jump to the loop start.
     */
boolean looksLikeContinueLabel(MethodCtx ctx, int[] pos, LabelId label) {
        LoopCtx loop = ctx.currentLoop();
        if (loop == null || label.equals(loop.start) || label.equals(loop.end)) return false;
        for (int i = pos[0] + 1; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofJump kj) {
                return kj.target().equals(loop.start);
            }
            if (op instanceof KofLabel || op instanceof KofConditionalJump
                    || op instanceof KofTryStart || op instanceof KofCatchStart
                    || op instanceof KofReturn || op instanceof KofReturnVoid
                    || op instanceof KofThrow) {
                return false;
            }
        }
        return false;
    }

    /**
     * A label is a loop start when a later instruction jumps to it (back edge)
     * or conditionally jumps to it (do-while condition).
     */
boolean isLoopStart(MethodCtx ctx, int[] pos, LabelId label) {
        for (int i = pos[0] + 1; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofJump kj && kj.target().equals(label)) return true;
            if (op instanceof KofConditionalJump cj && cj.trueLabel().equals(label)) return true;
        }
        return false;
    }

List<JsIr.JsStatement> parseStatement(MethodCtx ctx, int[] pos) {
        KofOperation op = ctx.ops.get(pos[0]);
        if (op instanceof KofReturnVoid) {
            pos[0]++;
            return List.of(new JsIr.JsReturn(null));
        }
        if (op instanceof KofTryStart) {
            return List.of(parseTryStatement(ctx, pos));
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
            throw new IllegalStateException("KofJS: unexpected KofCatchStart at statement level");
        }
        return p.expr.parseExpressionStatement(ctx, pos);
    }

    /**
     * Statement-level if: [cond ops, CJump, Label(true), then, Jump(end),
     * Label(false), (else), Label(end)].
     */
JsIr.JsStatement parseIfBody(MethodCtx ctx, int[] pos, KofConditionalJump cj,
                                         JsIr.JsExpression condition, List<Object> stack) {
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl && kl.label().equals(cj.trueLabel()))) {
            throw new IllegalStateException("KofJS: if pattern expected Label(true)");
        }
        pos[0]++;
        List<JsIr.JsStatement> thenBranch = parseStatements(ctx, pos, Set.of(cj.falseLabel()), new ArrayList<>());
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl2
                && kl2.label().equals(cj.falseLabel())) {
            pos[0]++;
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
            // Label(end) — no else branch. A loop label (continue/start) is
            // not the if's end; it belongs to the enclosing loop.
            KofLabel end = (KofLabel) ctx.ops.get(pos[0]);
            if (!ctx.isLoopLabel(end.label())) {
                pos[0]++;
            }
            return new JsIr.JsIf(condition, thenBranch, List.of());
        }
        List<JsIr.JsStatement> elseBranch = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl3
                && !ctx.isLoopLabel(kl3.label())) {
            // Label(end) — end of else branch (loop labels belong to the loop)
            pos[0]++;
        }
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

JsIr.JsExpression comparisonExpr(KofComparison comp, JsIr.JsExpression left, JsIr.JsExpression right) {
        if (comp == KofComparison.NE && right instanceof JsIr.JsNumber n && "0".equals(n.text())) {
            // boolean conditions: (cond, 0) CJump(NE) — truthiness in JS
            return left;
        }
        return switch (comp) {
            case EQ -> new JsIr.JsBinary(left, "===", right);
            case NE -> new JsIr.JsBinary(left, "!==", right);
            case LT -> new JsIr.JsBinary(left, "<", right);
            case LE -> new JsIr.JsBinary(left, "<=", right);
            case GT -> new JsIr.JsBinary(left, ">", right);
            case GE -> new JsIr.JsBinary(left, ">=", right);
        };
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
        JsIr.JsExpression condition = comparisonExpr(cj2.comparison(), left, right);
        if (!condPreamble.isEmpty()) {
            condition = new JsIr.JsSequence(condPreamble, condition);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel bodyLabel && bodyLabel.label().equals(cj2.trueLabel()))) {
            throw new IllegalStateException("KofJS: loop body label mismatch");
        }
        pos[0]++;
        // Detect the continue label before parsing the body: the back edge is
        // the first Jump(start) after the body label, and the continue label
        // is the label immediately before the update statements (for-loops).
        LabelId continueLabel = startLabel;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofJump kj && kj.target().equals(startLabel)) {
                for (int j = i - 1; j >= pos[0]; j--) {
                    KofOperation op = ctx.ops.get(j);
                    if (op instanceof KofLabel kl) {
                        continueLabel = kl.label();
                        break;
                    }
                    if (op instanceof KofJump || op instanceof KofConditionalJump) break;
                }
                break;
            }
        }
        ctx.loops.add(new LoopCtx(startLabel, continueLabel, cj2.falseLabel()));
        List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(startLabel), new ArrayList<>());
        ctx.loops.remove(ctx.loops.size() - 1);
        // After the body: either Jump(start) (while) or Label(continue) + update + Jump(start) (for).
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel nextLabel
                && !nextLabel.label().equals(startLabel)
                && !nextLabel.label().equals(cj2.falseLabel())) {
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
        ctx.loops.add(new LoopCtx(startLabel, startLabel, endLabel));
        List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(startLabel), new ArrayList<>());
        ctx.loops.remove(ctx.loops.size() - 1);
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
        JsIr.JsExpression condition = comparisonExpr(cj.comparison(), left, right);
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

JsIr.JsStatement parseTryStatement(MethodCtx ctx, int[] pos) {
        KofTryStart ts = (KofTryStart) ctx.ops.get(pos[0]);
        pos[0]++;
        List<JsIr.JsStatement> tryBody = parseStatements(ctx, pos, Set.of(ts.endLabel()), new ArrayList<>());
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel tryEnd
                && tryEnd.label().equals(ts.endLabel())) {
            // The end label may already have been consumed as a region exit
            // (e.g. when the try body ends with throw: the trailing jump is
            // unreachable and the optimizer drops it).
            pos[0]++;
        }
        List<JsIr.JsCatchClause> catches = new ArrayList<>();
        while (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofCatchStart cs) {
            if ("Throwable".equals(cs.exceptionType())) {
                // catch-all + rethrow emulates finally; JS finally is native.
                pos[0]++;
                if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump) {
                    pos[0]++;
                }
                break;
            }
            pos[0]++;
            String param = p.expr.localName(ctx, cs.localIndex());
            List<JsIr.JsStatement> catchBody = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
            catches.add(new JsIr.JsCatchClause(param, catchBody));
        }
        if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofTryEnd)) {
            throw new IllegalStateException("KofJS: try expected KofTryEnd");
        }
        pos[0]++;
        List<JsIr.JsStatement> finallyBody = List.of();
        // o label do finally é uma label nova da região do try — nunca um
        // label do loop (ex.: o destino do catch no fim do try dentro de um
        // for tem o continue label como próximo — não é um finally)
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel finallyStart
                && !ctx.isLoopLabel(finallyStart.label())) {
            pos[0]++;
            List<LabelId> exits = new ArrayList<>();
            finallyBody = parseStatements(ctx, pos, Set.of(), exits);
            // skip the rethrow machinery: Label(rethrow) ... Label(done)
            LabelId done = exits.isEmpty() ? null : exits.get(exits.size() - 1);
            if (done != null) {
                while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofLabel kl
                        && kl.label().equals(done))) {
                    pos[0]++;
                }
                if (pos[0] < ctx.ops.size()) pos[0]++;
            } else if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
                // no-finally: the trailing empty label (done) ends the try
                pos[0]++;
            }
        }
        return new JsIr.JsTry(tryBody, catches, finallyBody);
    }
}
