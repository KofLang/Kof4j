package dev.kof.compiler.js;

import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.LabelId;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JsIfStatementParser — o statement-level if e o if-expressão do backend JS,
 * extraídos do JsControlFlowParser no MESMO commit que a guarda família-#995
 * (o arquivo cruzou 590→605 no gate ≤600; a família "if" é autocontida: o
 * padrão linear [cond, CJump, Label(true), then, (Jump(end)), Label(false),
 * (else), Label(end)] e o fold if-throw-else que o JsIfThrowElse implementa).
 *
 * <p>A saída PRÓPRIA do if pós-fold é o par [Jump(end), Label(end)] — mas só
 * quando o label após o Jump NÃO é um false/try/loop label ENVOLVENTE (esse
 * par pertence ao ancestral; a guarda simétrica vive no JsIfThrowElse).
 */
final class JsIfStatementParser {

    private JsIfStatementParser() {}

    /**
     * Statement-level if: [cond ops, CJump, Label(true), then, Jump(end),
     * Label(false), (else), Label(end)].
     */
static JsIr.JsStatement parseIfBody(JsControlFlowParser flow, MethodCtx ctx, int[] pos,
                                        KofConditionalJump cj, JsIr.JsExpression condition) {
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl && kl.label().equals(cj.trueLabel()))) {
            throw new IllegalStateException("KofJS: if pattern expected Label(true)");
        }
        pos[0]++;
        // §380: falseLabel ativo na pilha — um `if` interno nunca consome label de
        // estrutura envolvente (exceção aborta a compilação: sem leak de pilha).
        ctx.enclosingIfFalses.push(cj.falseLabel());
        List<LabelId> thenExits = new ArrayList<>();
        List<JsIr.JsStatement> thenBranch = flow.parseStatements(ctx, pos, Set.of(cj.falseLabel()), thenExits);
        if (System.getenv("KOF_JS_PARSE_PROBE") != null) {  // PROBE
            System.out.println("PARSE-IF cond=" + (condition == null ? "null" : condition.getClass().getSimpleName())
                    + " then=" + thenBranch.size() + " opsLeft=" + (ctx.ops.size() - pos[0])
                    + " firstOp=" + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]).getClass().getSimpleName() : "eof")
                    + " exits=" + thenExits.size());
        }
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
                    && !ctx.isEnclosingIfFalse(end.label())   // §380: fronteira alheia
                    && !ctx.isEnclosingIfEnd(end.label())) {  // §734: fim do if envolvente
                pos[0]++;
            }
            ctx.enclosingIfFalses.pop();
            return new JsIr.JsIf(condition, thenBranch, List.of());
        }
        // §734: o alvo do Jump de saída do then é o FIM deste if. Um if ANINHADO
        // no else não pode tomá-lo como o seu próprio fim — roubava o label e o
        // epílogo do método era absorvido no else externo (a função devolvia
        // `undefined`; JVM/Script/native corretos). Guardado durante o else.
        ctx.enclosingIfEnds.addAll(thenExits);
        // §147 (12/09, #101 — detalhe em JsIfThrowElse): IR linear sem
        // Jump/Label de end; o else pára antes do trailing-return do método
        // (senão ganha `return` fantasma e o epílogo "some").
        List<JsIr.JsStatement> elseBranch;
        if (JsIfThrowElse.thenEndsUnconditional(thenBranch)) {
            elseBranch = JsIfThrowElse.parseElse(flow, ctx, pos);
            // Família #995: com o else consumido pelo fold, a saída PRÓPRIA
            // deste if é o par [Jump(end), Label(end)] — mas só quando o label
            // após o Jump NÃO é um false-label envolvente ativo (esse par
            // pertence ao ancestral; ver a guarda em JsIfThrowElse). Sem isto
            // o Jump+Label próprios ficavam na stream e o Label(end) vazio
            // estourava como label órfão no statement level.
            int q = pos[0];
            if (q < ctx.ops.size() && ctx.ops.get(q) instanceof KofJump kj
                    && q + 1 < ctx.ops.size() && ctx.ops.get(q + 1) instanceof KofLabel nl
                    && !ctx.isEnclosingIfFalse(nl.label())
                    && !ctx.isEnclosingIfEnd(nl.label())
                    && !ctx.isLoopLabel(nl.label())
                    && !ctx.isTryEndLabel(nl.label())) {
                pos[0] += 2;
            }
        } else {
            elseBranch = flow.parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        }
        ctx.enclosingIfEnds.removeAll(thenExits);
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl3
                && ctx.isIfEndLabel(pos, kl3.label())
                && !ctx.isEnclosingIfFalse(kl3.label())   // §380
                && !ctx.isEnclosingIfEnd(kl3.label())) {  // §734: fim do if envolvente
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
static JsIr.JsExpression tryParseIfExpr(JsControlFlowParser flow, MethodCtx ctx, int[] pos,
                                            KofConditionalJump cj, JsIr.JsExpression condition) {
        int saved = pos[0];
        try {
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl && kl.label().equals(cj.trueLabel()))) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            JsIr.JsExpression thenExpr = flow.methodParser().expr.parseExpressionFragment(ctx, pos);
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
            JsIr.JsExpression elseExpr = flow.methodParser().expr.parseExpressionFragment(ctx, pos);
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
}
