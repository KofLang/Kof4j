package dev.kof.compiler.js;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofPutStatic;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofStoreField;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;

/** Helper de parsing de statements de expressão (extraído, ≤500). */
final class JsExpressionStatementParser {

    private JsExpressionStatementParser() {}

    static List<JsIr.JsStatement> parseExpressionStatement(JsExpressionParser parser, MethodCtx ctx, int[] pos) {
        List<Object> stack = new ArrayList<>();
        List<JsIr.JsStatement> preamble = new ArrayList<>();
        List<JsIr.JsExpression> preambleExprs = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof KofStoreLocal sl) {
                pos[0]++;
                if (stack.isEmpty()) {
                    throw new IllegalStateException("KofJS: store with empty stack at " + sl
                            + "\nnext=" + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]) : "eof")
                            + "\nops=" + ctx.ops.stream().map(Object::toString).reduce("", (a, b) -> a + "\n" + b));
                }
                JsIr.JsStatement stmt = parser.storeLocalStatement(ctx, sl, parser.pop(stack));
                boolean switchTemp = "#switch".equals(ctx.rawLocalNames.get(sl.index()));
                if (stack.isEmpty() && (!parser.isCompilerTemp(ctx, sl.index()) || switchTemp)) {
                    return parser.finishExpressionStatement(preamble, preambleExprs, stmt);
                }
                // mid-expression store (++/-- temps, compiler temporaries)
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofStoreField sf) {
                pos[0]++;
                JsIr.JsExpression value = parser.pop(stack);
                JsIr.JsExpression receiver = parser.pop(stack);
                JsIr.JsStatement stmt = new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(receiver,
                                ctx.recordClass ? "_" + JsTypeMapper.sanitizeName(sf.name()) : JsTypeMapper.sanitizeName(sf.name())), "=", value));
                if (stack.isEmpty()) {
                    return parser.finishExpressionStatement(preamble, preambleExprs, stmt);
                }
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofPutStatic ps) {
                pos[0]++;
                JsIr.JsExpression value = parser.pop(stack);
                String owner = JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(ps.ownerType()));
                return parser.finishExpressionStatement(preamble, new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(new JsIr.JsIdentifier(owner), JsTypeMapper.sanitizeName(ps.name())), "=", value)));
            }
            if (op instanceof KofArrayStore as) {
                if (stack.isEmpty()) {
                    throw new IllegalStateException("KofJS: arraystore empty stack; next="
                            + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]) : "eof")
                            + "\nops=" + ctx.ops.stream().map(Object::toString).reduce("", (a, b) -> a + "\n" + b));
                }
                pos[0]++;
                JsIr.JsExpression value = parser.pop(stack);
                JsIr.JsExpression index = parser.pop(stack);
                JsIr.JsExpression array = parser.pop(stack);
                JsIr.JsStatement stmt = new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsIndex(array, index), "=", value));
                if (stack.isEmpty() && !parser.isIncTmpLoadAhead(ctx, pos)) {
                    return parser.finishExpressionStatement(preamble, preambleExprs, stmt);
                }
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofPop) {
                pos[0]++;
                JsIr.JsExpression dropped = null;
                if (!stack.isEmpty()) {
                    dropped = parser.pop(stack);
                }
                stack.clear();
                if (dropped instanceof JsIr.JsCall || dropped instanceof JsIr.JsSequence
                        || dropped instanceof JsIr.JsAwait) {
                    // Side-effecting call, sequence, or await used as statement
                    // (e.g. `await r;` / `await spawn tick();`) must survive POP.
                    return parser.finishExpressionStatement(preamble, preambleExprs,
                            new JsIr.JsExprStmt(dropped));
                }
                return parser.finishExpressionStatement(preamble, preambleExprs, null);
            }
            if (op instanceof KofReturn kr) {
                pos[0]++;
                if (Type.isVoid(kr.returnType()) && !stack.isEmpty()) {
                    // A void call's result is still a side-effecting
                    // expression (default-parameter wrapper returning a
                    // void function call): return it so it executes.
                    return parser.finishExpressionStatement(preamble, preambleExprs,
                            new JsIr.JsReturn(parser.pop(stack)));
                }
                if (Type.isVoid(kr.returnType())) {
                    stack.clear();
                    return parser.finishExpressionStatement(preamble, preambleExprs, new JsIr.JsReturn(null));
                }
                return parser.finishExpressionStatement(preamble, preambleExprs, new JsIr.JsReturn(parser.pop(stack)));
            }
            if (op instanceof KofThrow) {
                pos[0]++;
                return parser.finishExpressionStatement(preamble, preambleExprs, new JsIr.JsThrow(parser.pop(stack)));
            }
            if (op instanceof KofConditionalJump cj && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLabel kl
                    && kl.label().equals(cj.trueLabel())) {
                // if-statement OR if-expression (var x = if (...) ... else ...)
                pos[0]++;
                JsIr.JsExpression right = parser.pop(stack);
                JsIr.JsExpression left = parser.pop(stack);
                JsIr.JsExpression condition = parser.p.flow.comparisonExpr(cj.comparison(), left, right);
                while (!stack.isEmpty()) {
                    condition = new JsIr.JsSequence(List.of(parser.pop(stack)), condition);
                }
                JsIr.JsExpression ifExpr = parser.p.flow.tryParseIfExpr(ctx, pos, cj, condition);
                if (ifExpr != null) {
                    stack.add(ifExpr);
                    continue;
                }
                return List.of(parser.p.flow.parseIfBody(ctx, pos, cj, condition, stack));
            }
            if (!parser.isExpressionOp(op)) {
                // statement boundary: wrap any leftover stack (listOf(...) chains,
                // increment temps) and finish the statement
                if (!stack.isEmpty()) {
                    JsIr.JsExpression wrapped = parser.wrapStack(stack);
                    stack.clear();
                    return parser.finishExpressionStatement(preamble, preambleExprs, new JsIr.JsExprStmt(wrapped));
                }
                if (!preamble.isEmpty() || !preambleExprs.isEmpty()) {
                    return parser.finishExpressionStatement(preamble, preambleExprs, null);
                }
                throw new IllegalStateException("KofJS: unexpected op in expression statement: " + op);
            }
            try {
                parser.consumeExpressionOp(ctx, pos, stack, preambleExprs);
            } catch (StatementEnd se) {
                if (!stack.isEmpty()) {
                    JsIr.JsExpression wrapped = parser.wrapStack(stack);
                    stack.clear();
                    return parser.finishExpressionStatement(preamble, preambleExprs, new JsIr.JsExprStmt(
                            new JsIr.JsSequence(List.of(wrapped), se.call)));
                }
                return parser.finishExpressionStatement(preamble, preambleExprs, new JsIr.JsExprStmt(se.call));
            }
        }
        if (!stack.isEmpty()) {
            return parser.finishExpressionStatement(preamble, preambleExprs,
                    new JsIr.JsExprStmt(parser.wrapStack(stack)));
        }
        if (!preambleExprs.isEmpty()) {
            return parser.finishExpressionStatement(preamble, preambleExprs, null);
        }
        throw new IllegalStateException("KofJS: unterminated expression statement");
    }

}