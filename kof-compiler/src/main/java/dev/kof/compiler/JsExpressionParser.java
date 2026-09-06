package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsExpressionParser — converte a pilha de ops do IR em expressões JsIr (statement de expressão, fragmentos, pop/wrap da pilha) (REFACTOR-500 FASE 4).
 */
final class JsExpressionParser {

    private final JsMethodParser p;

    JsExpressionParser(JsMethodParser p) {
        this.p = p;
    }

List<JsIr.JsStatement> parseExpressionStatement(MethodCtx ctx, int[] pos) {
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
                JsIr.JsStatement stmt = storeLocalStatement(ctx, sl, pop(stack));
                boolean switchTemp = "#switch".equals(ctx.rawLocalNames.get(sl.index()));
                if (stack.isEmpty() && (!isCompilerTemp(ctx, sl.index()) || switchTemp)) {
                    return finishExpressionStatement(preamble, preambleExprs, stmt);
                }
                // mid-expression store (++/-- temps, compiler temporaries)
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofStoreField sf) {
                pos[0]++;
                JsIr.JsExpression value = pop(stack);
                JsIr.JsExpression receiver = pop(stack);
                JsIr.JsStatement stmt = new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(receiver,
                                ctx.recordClass ? "_" + JsTypeMapper.sanitizeName(sf.name()) : JsTypeMapper.sanitizeName(sf.name())), "=", value));
                if (stack.isEmpty()) {
                    return finishExpressionStatement(preamble, preambleExprs, stmt);
                }
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofPutStatic ps) {
                pos[0]++;
                JsIr.JsExpression value = pop(stack);
                String owner = JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(ps.ownerType()));
                return finishExpressionStatement(preamble, new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(new JsIr.JsIdentifier(owner), JsTypeMapper.sanitizeName(ps.name())), "=", value)));
            }
            if (op instanceof KofArrayStore as) {
                if (stack.isEmpty()) {
                    throw new IllegalStateException("KofJS: arraystore empty stack; next="
                            + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]) : "eof")
                            + "\nops=" + ctx.ops.stream().map(Object::toString).reduce("", (a, b) -> a + "\n" + b));
                }
                pos[0]++;
                JsIr.JsExpression value = pop(stack);
                JsIr.JsExpression index = pop(stack);
                JsIr.JsExpression array = pop(stack);
                JsIr.JsStatement stmt = new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsIndex(array, index), "=", value));
                if (stack.isEmpty() && !isIncTmpLoadAhead(ctx, pos)) {
                    return finishExpressionStatement(preamble, preambleExprs, stmt);
                }
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofPop) {
                pos[0]++;
                JsIr.JsExpression dropped = null;
                if (!stack.isEmpty()) {
                    dropped = pop(stack);
                }
                stack.clear();
                if (dropped instanceof JsIr.JsCall || dropped instanceof JsIr.JsSequence
                        || dropped instanceof JsIr.JsAwait) {
                    // Side-effecting call, sequence, or await used as statement
                    // (e.g. `await r;` / `await spawn tick();`) must survive POP.
                    return finishExpressionStatement(preamble, preambleExprs,
                            new JsIr.JsExprStmt(dropped));
                }
                return finishExpressionStatement(preamble, preambleExprs, null);
            }
            if (op instanceof KofReturn kr) {
                pos[0]++;
                if (Type.isVoid(kr.returnType()) && !stack.isEmpty()) {
                    // A void call's result is still a side-effecting
                    // expression (default-parameter wrapper returning a
                    // void function call): return it so it executes.
                    return finishExpressionStatement(preamble, preambleExprs,
                            new JsIr.JsReturn(pop(stack)));
                }
                if (Type.isVoid(kr.returnType())) {
                    stack.clear();
                    return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsReturn(null));
                }
                return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsReturn(pop(stack)));
            }
            if (op instanceof KofThrow) {
                pos[0]++;
                return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsThrow(pop(stack)));
            }
            if (op instanceof KofConditionalJump cj && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLabel kl
                    && kl.label().equals(cj.trueLabel())) {
                // if-statement OR if-expression (var x = if (...) ... else ...)
                pos[0]++;
                JsIr.JsExpression right = pop(stack);
                JsIr.JsExpression left = pop(stack);
                JsIr.JsExpression condition = p.flow.comparisonExpr(cj.comparison(), left, right);
                while (!stack.isEmpty()) {
                    condition = new JsIr.JsSequence(List.of(pop(stack)), condition);
                }
                JsIr.JsExpression ifExpr = p.flow.tryParseIfExpr(ctx, pos, cj, condition);
                if (ifExpr != null) {
                    stack.add(ifExpr);
                    continue;
                }
                return List.of(p.flow.parseIfBody(ctx, pos, cj, condition, stack));
            }
            if (!isExpressionOp(op)) {
                // statement boundary: wrap any leftover stack (listOf(...) chains,
                // increment temps) and finish the statement
                if (!stack.isEmpty()) {
                    JsIr.JsExpression wrapped = wrapStack(stack);
                    stack.clear();
                    return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsExprStmt(wrapped));
                }
                if (!preamble.isEmpty() || !preambleExprs.isEmpty()) {
                    return finishExpressionStatement(preamble, preambleExprs, null);
                }
                throw new IllegalStateException("KofJS: unexpected op in expression statement: " + op);
            }
            try {
                consumeExpressionOp(ctx, pos, stack, preambleExprs);
            } catch (StatementEnd se) {
                if (!stack.isEmpty()) {
                    JsIr.JsExpression wrapped = wrapStack(stack);
                    stack.clear();
                    return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsExprStmt(
                            new JsIr.JsSequence(List.of(wrapped), se.call)));
                }
                return finishExpressionStatement(preamble, preambleExprs, new JsIr.JsExprStmt(se.call));
            }
        }
        if (!stack.isEmpty()) {
            return finishExpressionStatement(preamble, preambleExprs,
                    new JsIr.JsExprStmt(wrapStack(stack)));
        }
        if (!preambleExprs.isEmpty()) {
            return finishExpressionStatement(preamble, preambleExprs, null);
        }
        throw new IllegalStateException("KofJS: unterminated expression statement");
    }

List<JsIr.JsStatement> finishExpressionStatement(List<JsIr.JsStatement> preamble,
                                                          JsIr.JsStatement finalStmt) {
        return finishExpressionStatement(preamble, List.of(), finalStmt);
    }

    /**
     * True when the next op reloads the ++/-- temp (#inc), meaning the array
     * store is part of an increment expression, not the end of a statement.
     */
boolean isIncTmpLoadAhead(MethodCtx ctx, int[] pos) {
        if (pos[0] >= ctx.ops.size()) return false;
        KofOperation op = ctx.ops.get(pos[0]);
        if (op instanceof KofLoadLocal ll) {
            String raw = ctx.rawLocalNames.get(ll.index());
            return raw != null && raw.startsWith("#");
        }
        return false;
    }

boolean isCompilerTemp(MethodCtx ctx, int index) {
        String raw = ctx.rawLocalNames.get(index);
        return raw != null && raw.startsWith("#");
    }

JsIr.JsExpression wrapStack(List<Object> stack) {
        if (stack.size() == 1) {
            return pop(stack);
        }
        List<JsIr.JsExpression> exprs = new ArrayList<>();
        for (int i = 0; i < stack.size() - 1; i++) {
            Object o = stack.get(i);
            if (o instanceof JsIr.JsIdentifier id && "$kofOut".equals(id.name())) {
                continue;
            }
            exprs.add(o instanceof NewPending np
                    ? new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), List.of())
                    : (JsIr.JsExpression) o);
        }
        return new JsIr.JsSequence(exprs, pop(stack));
    }

JsIr.JsStatement storeLocalStatement(MethodCtx ctx, KofStoreLocal sl, JsIr.JsExpression value) {
        String name = localName(ctx, sl.index());
        if ("this".equals(name)) {
            throw new IllegalStateException("KofJS: cannot store to 'this'");
        }
        if (ctx.declared.add(sl.index())) {
            return new JsIr.JsVarDecl(name, value, false);
        }
        return new JsIr.JsAssign(name, value);
    }

String localName(MethodCtx ctx, int index) {
        String name = ctx.localNames.get(index);
        if (name == null) {
            throw new IllegalStateException("KofJS: unknown local slot " + index);
        }
        return name;
    }

boolean isExpressionOp(KofOperation op) {
        return op instanceof KofLoadLiteral || op instanceof KofLoadLocal
                || op instanceof KofLoadField || op instanceof KofGetStatic
                || op instanceof KofBinary || op instanceof KofUnary
                || op instanceof KofCall || op instanceof KofNewObject
                || op instanceof KofDup || op instanceof KofDupX1 || op instanceof KofDupX2 || op instanceof KofNewArray
                || op instanceof KofArrayLoad || op instanceof KofArrayLength
                || op instanceof KofInstanceOf || op instanceof KofCheckCast
                || op instanceof KofStoreLocal;
    }

void consumeExpressionOp(MethodCtx ctx, int[] pos, List<Object> stack,
                                     List<JsIr.JsExpression> preambleExprs) {
        KofOperation op = ctx.ops.get(pos[0]);
        pos[0]++;
        if (op instanceof KofLoadLiteral lit) {
            stack.add(p.calls.literalExpr(lit));
        } else if (op instanceof KofLoadLocal ll) {
            stack.add(new JsIr.JsIdentifier(localName(ctx, ll.index())));
        } else if (op instanceof KofStoreLocal sl) {
            // Mid-expression store (sound optimizer round trip: dup; store).
            // The value stays on the stack as an assignment expression.
            JsIr.JsExpression value = pop(stack);
            stack.add(new JsIr.JsAssignExpr(localName(ctx, sl.index()), value));
        } else if (op instanceof KofLoadField lf) {
            JsIr.JsExpression receiver = pop(stack);
            boolean isRecordField = false;
            if (lf.ownerType() instanceof Type.ClassType ct) {
                String ownerInternal = JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
                String ownerSimple = ct.name();
                if (p.lc.recordClassNames.contains(ownerInternal) || p.lc.recordClassNames.contains(ct.name())
                        || p.lc.recordClassNames.contains(ownerSimple) || ctx.recordClass) {
                    isRecordField = true;
                }
            }
            stack.add(new JsIr.JsMember(receiver, isRecordField ? "_" + JsTypeMapper.sanitizeName(lf.name()) : JsTypeMapper.sanitizeName(lf.name())));
        } else if (op instanceof KofGetStatic gs) {
            if ("java.lang".equals(JsTypeMapper.classPackage(gs.ownerType())) && "System".equals(JsTypeMapper.className(gs.ownerType()))
                    && "out".equals(gs.name())) {
                stack.add(new JsIr.JsIdentifier("$kofOut"));
            } else {
                String owner = JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(gs.ownerType()));
                stack.add(new JsIr.JsMember(new JsIr.JsIdentifier(owner), JsTypeMapper.sanitizeName(gs.name())));
            }
        } else if (op instanceof KofBinary kb) {
            JsIr.JsExpression right = pop(stack);
            JsIr.JsExpression left = pop(stack);
            stack.add(p.calls.binaryExpr(kb, left, right));
        } else if (op instanceof KofUnary ku) {
            JsIr.JsExpression operand = pop(stack);
            stack.add(p.calls.unaryExpr(ku, operand));
        } else if (op instanceof KofNewObject no) {
            stack.add(new NewPending(JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(no.type()))));
        } else if (op instanceof KofDup) {
            if (!stack.isEmpty() && stack.get(stack.size() - 1) instanceof NewPending) {
                stack.add(new DupMarker());
                return;
            }
            JsIr.JsExpression top = pop(stack);
            if (top instanceof JsIr.JsNumber || top instanceof JsIr.JsString
                    || top instanceof JsIr.JsNull) {
                stack.add(top);
                stack.add(top);
                return;
            }
            // Materialize the copy as a preamble assignment: `t = v` must
            // execute before any later store that consumes the temp.
            String temp = ctx.freshTemp();
            preambleExprs.add(new JsIr.JsAssignExpr(temp, top));
            stack.add(new JsIr.JsIdentifier(temp));
            stack.add(new JsIr.JsIdentifier(temp));
        } else if (op instanceof KofDupX1) {
            JsIr.JsExpression top = pop(stack);
            JsIr.JsExpression below = pop(stack);
            if (top instanceof JsIr.JsNumber || top instanceof JsIr.JsString
                    || top instanceof JsIr.JsNull) {
                stack.add(top);
                stack.add(below);
                stack.add(top);
                return;
            }
            String temp = ctx.freshTemp();
            stack.add(new JsIr.JsSequence(
                    List.of(new JsIr.JsAssignExpr(temp, top)), new JsIr.JsIdentifier(temp)));
            stack.add(below);
            stack.add(new JsIr.JsIdentifier(temp));
        } else if (op instanceof KofDupX2) {
            JsIr.JsExpression top = pop(stack);
            JsIr.JsExpression middle = pop(stack);
            JsIr.JsExpression bottom = pop(stack);
            if (top instanceof JsIr.JsNumber || top instanceof JsIr.JsString
                    || top instanceof JsIr.JsNull) {
                stack.add(top);
                stack.add(bottom);
                stack.add(middle);
                stack.add(top);
                return;
            }
            String temp = ctx.freshTemp();
            stack.add(new JsIr.JsSequence(
                    List.of(new JsIr.JsAssignExpr(temp, top)), new JsIr.JsIdentifier(temp)));
            stack.add(bottom);
            stack.add(middle);
            stack.add(new JsIr.JsIdentifier(temp));
        } else if (op instanceof KofNewArray na) {
            JsIr.JsExpression size = pop(stack);
            stack.add(new JsIr.JsArray(size, JsTypeMapper.arrayFill(na.elementType())));
        } else if (op instanceof KofArrayLoad al) {
            JsIr.JsExpression index = pop(stack);
            JsIr.JsExpression array = pop(stack);
            stack.add(new JsIr.JsIndex(array, index));
        } else if (op instanceof KofArrayLength) {
            JsIr.JsExpression array = pop(stack);
            stack.add(new JsIr.JsMember(array, "length"));
        } else if (op instanceof KofCheckCast) {
            // JavaScript has no runtime casts; Kof semantics are enforced by
            // the type checker at compile time.
        } else if (op instanceof KofInstanceOf io) {
            JsIr.JsExpression operand = pop(stack);
            if (BuiltinTypes.isString(io.type())) {
                stack.add(new JsIr.JsConditional(
                        new JsIr.JsBinary(new JsIr.JsUnary("typeof", operand), "===", new JsIr.JsString("string")),
                        new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
            } else if (io.type() instanceof Type.PrimitiveType pt) {
                String cn = Type.canonicalPrimitiveName(pt.name());
                if ("int".equals(cn) || "long".equals(cn) || "float".equals(cn) || "double".equals(cn) || "byte".equals(cn) || "short".equals(cn) || "char".equals(cn)) {
                    stack.add(new JsIr.JsConditional(
                            new JsIr.JsBinary(new JsIr.JsUnary("typeof", operand), "===", new JsIr.JsString("number")),
                            new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
                } else if ("bool".equals(cn) || "boolean".equals(cn)) {
                    stack.add(new JsIr.JsConditional(
                            new JsIr.JsBinary(new JsIr.JsUnary("typeof", operand), "===", new JsIr.JsString("boolean")),
                            new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
                } else {
                    stack.add(new JsIr.JsConditional(
                            new JsIr.JsInstanceOf(operand, JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(io.type()))),
                            new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
                }
            } else {
                stack.add(new JsIr.JsConditional(
                        new JsIr.JsInstanceOf(operand, JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(io.type()))),
                        new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
            }
        } else if (op instanceof KofConditionalJump cj) {
            // if-expression: (cond ? then : else)
            JsIr.JsExpression right = pop(stack);
            JsIr.JsExpression left = pop(stack);
            JsIr.JsExpression condition = p.flow.comparisonExpr(cj.comparison(), left, right);
            JsIr.JsExpression ifExpr = p.flow.tryParseIfExpr(ctx, pos, cj, condition);
            if (ifExpr == null) {
                throw new IllegalStateException("KofJS: malformed if-expression");
            }
            stack.add(ifExpr);
        } else if (op instanceof KofCall kc) {
            p.calls.handleCall(ctx, stack, preambleExprs, kc);
        } else {
            throw new IllegalStateException("KofJS: unhandled IR op " + op);
        }
    }

    /**
     * Parses a self-contained expression fragment (if-expr branches): expression
     * ops until the next statement-level op. The stack must hold exactly one
     * value when finished.
     */
JsIr.JsExpression parseExpressionFragment(MethodCtx ctx, int[] pos) {
        List<Object> stack = new ArrayList<>();
        List<JsIr.JsExpression> preambleExprs = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof KofJump || op instanceof KofLabel || op instanceof KofPop
                    || (op instanceof KofStoreLocal && stack.isEmpty()) || op instanceof KofStoreField
                    || op instanceof KofPutStatic || op instanceof KofArrayStore
                    || op instanceof KofReturn || op instanceof KofReturnVoid
                    || op instanceof KofThrow || op instanceof KofTryStart
                    || op instanceof KofCatchStart) {
                break;
            }
            // Mid-expression store (switch-expression pattern binding, nested
            // switch subject): consumeExpressionOp turns it into an assignment
            // expression that stays on the stack; the fragment's final
            // JsSequence renders it as `(v = x, <expr>)`. Slots are
            // pre-declared at the function top, so the binding is a valid var.
            if (op instanceof KofConditionalJump && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLabel kl
                    && kl.label().equals(((KofConditionalJump) op).trueLabel())) {
                // if-expression or a nested if-statement inside a branch
                int saved = pos[0];
                try {
                    consumeExpressionOp(ctx, pos, stack, preambleExprs);
                } catch (RuntimeException e) {
                    pos[0] = saved;
                    break;
                }
                continue;
            }
            if (!isExpressionOp(op)) {
                throw new IllegalStateException("KofJS: unexpected op in expression fragment: " + op);
            }
            consumeExpressionOp(ctx, pos, stack, preambleExprs);
        }
        if (stack.size() != 1) {
            // dup;store round trips leave extra stack entries (a temp
            // sequence and the assignment). They evaluate before the final
            // value, which is the last computed one.
            List<JsIr.JsExpression> pre = new ArrayList<>();
            while (stack.size() > 1) {
                pre.add((JsIr.JsExpression) stack.remove(0));
            }
            JsIr.JsExpression last = pop(stack);
            return new JsIr.JsSequence(pre, last);
        }
        JsIr.JsExpression value = pop(stack);
        if (!preambleExprs.isEmpty()) {
            return new JsIr.JsSequence(preambleExprs, value);
        }
        return value;
    }

JsIr.JsExpression pop(List<Object> stack) {
        Object top = popRaw(stack);
        if (top instanceof NewPending np) {
            return new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), List.of());
        }
        if (top instanceof DupMarker) {
            // The frontend omits the <init> call when the class has only the
            // implicit default constructor; complete the pending new.
            Object base = popRaw(stack);
            if (base instanceof NewPending np) {
                return new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), List.of());
            }
            throw new IllegalStateException("KofJS: DupMarker without NewPending; stack=" + stack);
        }
        return (JsIr.JsExpression) top;
    }

Object popRaw(List<Object> stack) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("KofJS: expression stack underflow\nops="
                    + p.lc.currentCtxOpsDump);
        }
        return stack.remove(stack.size() - 1);
    }

boolean isPureDuplicate(JsIr.JsExpression expr) {
        return expr instanceof JsIr.JsIdentifier || expr instanceof JsIr.JsThis
                || expr instanceof JsIr.JsMember || expr instanceof JsIr.JsNull
                || expr instanceof JsIr.JsNumber || expr instanceof JsIr.JsString;
    }

    List<JsIr.JsStatement> finishExpressionStatement(List<JsIr.JsStatement> preamble,
                                                          List<JsIr.JsExpression> preambleExprs,
                                                          JsIr.JsStatement finalStmt) {
        List<JsIr.JsStatement> all = new ArrayList<>();
        for (JsIr.JsExpression pe : preambleExprs) {
            all.add(new JsIr.JsExprStmt(pe));
        }
        all.addAll(preamble);
        if (finalStmt != null) {
            all.add(finalStmt);
        }
        return all;
    }
}
