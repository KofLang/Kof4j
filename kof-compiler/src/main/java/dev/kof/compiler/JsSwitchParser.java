package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsSwitchParser — reconhece switch de valores e switch de patterns do IR e recria como switch/if-else JS (REFACTOR-500 FASE 4).
 */
final class JsSwitchParser {

    private final JsMethodParser p;

    JsSwitchParser(JsMethodParser p) {
        this.p = p;
    }

JsIr.JsStatement parseSwitchStatement(MethodCtx ctx, int[] pos) {
        // Detect pattern switch: immediate next after load #switch is KofInstanceOf
        boolean hasPattern = false;
        if (pos[0] + 1 < ctx.ops.size() && ctx.ops.get(pos[0] + 1) instanceof KofInstanceOf) {
            hasPattern = true;
        }
        if (hasPattern) {
            return parsePatternSwitch(ctx, pos);
        }
        // [load #switch, <caseValue>, SUB, load 0, CJump(EQ, body, next)] *
        // followed by: [Label(body0), stmts, Jump(end)] * [Label(default), stmts, Label(end)]
        List<JsIr.JsExpression> caseValues = new ArrayList<>();
        List<LabelId> bodyLabels = new ArrayList<>();
        LabelId defaultLabel = null;
        LabelId endLabel = null;
        String subjectName = null;
        if (!(ctx.ops.get(pos[0]) instanceof KofLoadLocal ll
                && "#switch".equals(ctx.rawLocalNames.get(ll.index())))) {
            throw new IllegalStateException("KofJS: switch subject not found");
        }
        while (true) {
            if (!(ctx.ops.get(pos[0]) instanceof KofLoadLocal l2
                    && "#switch".equals(ctx.rawLocalNames.get(l2.index())))) {
                break;
            }
            pos[0]++;
            if (subjectName == null) {
                subjectName = p.expr.localName(ctx, l2.index());
            }
            List<Object> stack = new ArrayList<>();
            stack.add(new JsIr.JsIdentifier(subjectName));
            boolean stringEq = false;
            while (true) {
                KofOperation op = ctx.ops.get(pos[0]);
                if (op instanceof KofBinary kb && kb.op() == KofBinaryOp.SUB && stack.size() == 2) {
                    pos[0]++;
                    break;
                }
                // bug 4: switch de String usa kof_string_equals em vez de SUB
                // (String - String gerava bytecode inválido no JVM). O call é
                // pulado aqui: no JS o `switch` já compara strings por valor
                // (===), então o caseValue coletado é o literal.
                if (op instanceof KofCall kc && "kof_string_equals".equals(kc.methodName())
                        && stack.size() == 2) {
                    stringEq = true;
                    pos[0]++;
                    break;
                }
                if (!p.expr.isExpressionOp(op)) {
                    throw new IllegalStateException("KofJS: unexpected op in switch case: " + op);
                }
                p.expr.consumeExpressionOp(ctx, pos, stack, new ArrayList<>());
            }
            JsIr.JsExpression caseValue = p.expr.pop(stack);
            p.expr.pop(stack);
            if (!(ctx.ops.get(pos[0]) instanceof KofLoadLiteral zero
                    && zero.value() instanceof Integer i && i == 0)) {
                throw new IllegalStateException("KofJS: switch case expected 0");
            }
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj
                    && (stringEq
                        ? cj.comparison() == KofComparison.NE
                        : cj.comparison() == KofComparison.EQ))) {
                throw new IllegalStateException("KofJS: switch case expected CJump("
                        + (stringEq ? "NE" : "EQ") + ")");
            }
            pos[0]++;
            caseValues.add(caseValue);
            bodyLabels.add(cj.trueLabel());
            defaultLabel = cj.falseLabel();
            endLabel = cj.falseLabel();
            if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel
                    && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLoadLocal next
                    && "#switch".equals(ctx.rawLocalNames.get(next.index()))) {
                pos[0]++;
                continue;
            }
            break;
        }
        if (subjectName == null) {
            throw new IllegalStateException("KofJS: switch subject not found");
        }
        List<JsIr.JsSwitchCase> fullCases = new ArrayList<>();
        for (LabelId bodyLabel : bodyLabels) {
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofLabel kl)
                    || !kl.label().equals(bodyLabel)) {
                throw new IllegalStateException("KofJS: switch body label missing");
            }
            pos[0]++;
            List<LabelId> exits = new ArrayList<>();
            List<JsIr.JsStatement> body = p.flow.parseStatements(ctx, pos, Set.of(), exits);
            if (endLabel == null && !exits.isEmpty()) {
                endLabel = exits.get(exits.size() - 1);
            }
            fullCases.add(new JsIr.JsSwitchCase(caseValues.get(fullCases.size()), body));
        }
        List<JsIr.JsStatement> defaultCase = List.of();
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel dl
                && dl.label().equals(defaultLabel)) {
            pos[0]++;
            defaultCase = p.flow.parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
            // Label(end) — end of the switch
            pos[0]++;
        }
        JsIr.JsExpression subject = new JsIr.JsIdentifier(subjectName);
        return new JsIr.JsSwitch(subject, fullCases, defaultCase);
    }

JsIr.JsStatement parsePatternSwitch(MethodCtx ctx, int[] pos) {
        // Pattern switch lowering in CompilerDriver:
        //   load #switch; instanceof T; 0; CJ EQ nextTest, body
        //   ... (for each case, preceded by Label nextTest unless first)
        //   Label default; (default body); Jump end
        //   Label body0; load #switch; checkcast T; store var; body0 stmts; Jump end
        //   ...
        //   Label end
        if (!(ctx.ops.get(pos[0]) instanceof KofLoadLocal ll
                && "#switch".equals(ctx.rawLocalNames.get(ll.index())))) {
            throw new IllegalStateException("KofJS: pattern switch subject not found");
        }
        String subjectName = p.expr.localName(ctx, ll.index());
        List<JsIr.JsExpression> conditions = new ArrayList<>();
        List<LabelId> bodyLabels = new ArrayList<>();
        LabelId defaultLabel = null;
        LabelId endLabel = null;

        // Collect test section
        while (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLoadLocal cur
                && "#switch".equals(ctx.rawLocalNames.get(cur.index()))) {
            pos[0]++; // consume load #switch
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofInstanceOf io)) {
                // Not a pattern case (should not happen for pure pattern switches)
                break;
            }
            // Build JS condition directly from KofInstanceOf type
            JsIr.JsExpression cond;
            if (BuiltinTypes.isString(io.type())) {
                cond = new JsIr.JsBinary(new JsIr.JsUnary("typeof", new JsIr.JsIdentifier(subjectName)), "===", new JsIr.JsString("string"));
            } else if (io.type() instanceof Type.PrimitiveType pt) {
                String cn = Type.canonicalPrimitiveName(pt.name());
                if ("int".equals(cn) || "long".equals(cn) || "float".equals(cn) || "double".equals(cn) || "byte".equals(cn) || "short".equals(cn) || "char".equals(cn)) {
                    cond = new JsIr.JsBinary(new JsIr.JsUnary("typeof", new JsIr.JsIdentifier(subjectName)), "===", new JsIr.JsString("number"));
                } else if ("bool".equals(cn) || "boolean".equals(cn)) {
                    cond = new JsIr.JsBinary(new JsIr.JsUnary("typeof", new JsIr.JsIdentifier(subjectName)), "===", new JsIr.JsString("boolean"));
                } else {
                    cond = new JsIr.JsInstanceOf(new JsIr.JsIdentifier(subjectName), JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(io.type())));
                }
            } else {
                cond = new JsIr.JsInstanceOf(new JsIr.JsIdentifier(subjectName), JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(io.type())));
            }
            conditions.add(cond);
            pos[0]++; // consume instanceof
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofLoadLiteral zero
                    && zero.value() instanceof Integer i && i == 0)) {
                throw new IllegalStateException("KofJS: pattern switch expected 0");
            }
            pos[0]++; // consume 0
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj
                    && cj.comparison() == KofComparison.EQ)) {
                throw new IllegalStateException("KofJS: pattern switch expected CJump EQ");
            }
            bodyLabels.add(cj.falseLabel());
            defaultLabel = cj.trueLabel();
            pos[0]++; // consume CJ
            // If next op is Label for next test, consume it and continue
            if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel lbl
                    && lbl.label().equals(cj.trueLabel())) {
                // Peek ahead: is next after label a load #switch?
                if (pos[0] + 1 < ctx.ops.size() && ctx.ops.get(pos[0] + 1) instanceof KofLoadLocal nxt
                        && "#switch".equals(ctx.rawLocalNames.get(nxt.index()))) {
                    pos[0]++; // consume nextTest label
                    continue;
                } else {
                    // No more pattern cases, test section ends; pos is at default label
                    break;
                }
            } else {
                break;
            }
        }
        if (bodyLabels.isEmpty()) {
            throw new IllegalStateException("KofJS: pattern switch no bodies");
        }
        // Now pos should be at Label defaultLabel
        // Parse default body to find endLabel
        List<JsIr.JsStatement> defaultCase = List.of();
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel dl
                && dl.label().equals(defaultLabel)) {
            pos[0]++; // consume default label
            // Default body runs until Jump end
            List<LabelId> exits = new ArrayList<>();
            // Parse statements until we hit a Jump; the Jump's target is endLabel
            // We need to detect Jump explicitly
            List<JsIr.JsStatement> defStmts = new ArrayList<>();
            while (pos[0] < ctx.ops.size()) {
                if (ctx.ops.get(pos[0]) instanceof KofJump j) {
                    endLabel = j.target();
                    pos[0]++; // consume Jump end
                    break;
                }
                if (ctx.ops.get(pos[0]) instanceof KofLabel) {
                    // This would be first body label - no default body? then endLabel is this label?
                    break;
                }
                // For default with actual statements, use parseStatements chunk
                // Simpler: parse via parseStatements with end detection
                // But we already are in a loop; use parseStatement
                if (ctx.ops.get(pos[0]) instanceof KofLabel cl && bodyLabels.contains(cl.label())) {
                    break;
                }
                List<JsIr.JsStatement> chunk = p.flow.parseStatement(ctx, pos);
                defStmts.addAll(chunk);
                // After chunk, if next is Jump, handle
                if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump j2) {
                    endLabel = j2.target();
                    pos[0]++;
                    break;
                }
            }
            defaultCase = defStmts;
            // If default was empty, the Jump we just consumed is the one after defaultLabel
            // If we broke without consuming Jump because next is body label, then default was empty and Jump was already consumed?
            // For empty default (no default body), the IR is Label default (= end) then Jump end - but default==end, so label is end
            // Handle empty default case: if we didn't capture endLabel yet, scan for it
            if (endLabel == null && pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel lbl2) {
                // Might be end label already
                // Need to find the Jump's target from earlier; it should have been the Jump after default
                // If default is empty, the label default is also end, and the Jump after default is Jump end (self)
                // We'll find end by looking at bodies' Jump targets
            }
        }
        // If endLabel still null (no default body), find it from first body's Jump or from default's Jump
        if (endLabel == null) {
            // Scan ahead for first Jump that is not a loop jump
            for (int i = pos[0]; i < ctx.ops.size(); i++) {
                if (ctx.ops.get(i) instanceof KofJump j) {
                    endLabel = j.target();
                    break;
                }
            }
        }
        // Parse each pattern body
        List<List<JsIr.JsStatement>> bodies = new ArrayList<>();
        for (int bi = 0; bi < bodyLabels.size(); bi++) {
            LabelId bodyLabel = bodyLabels.get(bi);
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofLabel kl)
                    || !kl.label().equals(bodyLabel)) {
                throw new IllegalStateException("KofJS: pattern switch body label missing expected " + bodyLabel + " got " + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]) : "EOF"));
            }
            pos[0]++; // consume body label
            // Body starts with load #switch; checkcast; store var  (if pattern var exists)
            // Let parseStatements handle it, but we need to stop at Jump end
            List<JsIr.JsStatement> bodyStmts = new ArrayList<>();
            while (pos[0] < ctx.ops.size()) {
                if (ctx.ops.get(pos[0]) instanceof KofJump j && j.target().equals(endLabel)) {
                    pos[0]++; // consume Jump end
                    break;
                }
                if (ctx.ops.get(pos[0]) instanceof KofLabel) {
                    // Next body label - should not happen before Jump
                    break;
                }
                List<JsIr.JsStatement> chunk = p.flow.parseStatement(ctx, pos);
                bodyStmts.addAll(chunk);
            }
            bodies.add(bodyStmts);
        }
        // Consume final end label if present
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel el
                && el.label().equals(endLabel)) {
            pos[0]++;
        }
        // Build nested if-else from the end
        JsIr.JsStatement result = defaultCase.isEmpty() ? null : new JsIr.JsBlock(defaultCase);
        for (int i = conditions.size() - 1; i >= 0; i--) {
            JsIr.JsExpression cond = conditions.get(i);
            List<JsIr.JsStatement> thenBranch = bodies.get(i);
            List<JsIr.JsStatement> elseBranch = result == null ? List.of() : List.of(result);
            result = new JsIr.JsIf(cond, thenBranch, elseBranch);
        }
        return result != null ? result : new JsIr.JsBlock(List.of());
    }
}
