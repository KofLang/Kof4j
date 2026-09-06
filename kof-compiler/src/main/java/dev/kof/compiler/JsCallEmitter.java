package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsCallEmitter — lowering de chamadas (print, super, static, instance, construtores) e operadores binários/unários/literais (REFACTOR-500 FASE 4).
 */
final class JsCallEmitter {

    private final JsMethodParser p;

    JsCallEmitter(JsMethodParser p) {
        this.p = p;
    }

void handleCall(MethodCtx ctx, List<Object> stack,
                                 List<JsIr.JsExpression> preambleExprs, KofCall kc) {
        if (kc.kind() == KofCallKind.CONSTRUCTOR) {
            handleConstructorCall(stack, kc);
            return;
        }
        // kof.web on JS: now lowered as runtime call (was WEB001) — handled via isRuntimeOp/kofWeb* helpers
        if (false && kc.methodName().startsWith("kof_web_")) {
            throw new IllegalStateException("kof.web is not supported on the js target yet (WEB001)");
        }
        boolean hasReceiver = kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE;
        List<JsIr.JsExpression> args = new ArrayList<>();
        for (int i = 0; i < kc.parameterTypes().size(); i++) {
            args.add(p.expr.pop(stack));
        }
        java.util.Collections.reverse(args);
        JsIr.JsExpression receiver = hasReceiver ? p.expr.pop(stack) : null;
        if (isPrintCall(kc)) {
            JsIr.JsExpression value = args.get(0);
            String fn = "println".equals(kc.methodName()) ? "kofPrintln" : "kofPrint";
            if ("kofPrint".equals(fn)) {
                p.lc.registerIoRuntime(fn);
            } else {
                p.lc.registerRuntime(fn);
            }
            throw new StatementEnd(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of(value)));
        }
        if ("valueOf".equals(kc.methodName()) && kc.kind() == KofCallKind.STATIC) {
            if (BuiltinTypes.isString(kc.ownerType())) {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String"), List.of(args.get(0))));
            } else if (!kc.parameterTypes().isEmpty()
                    && kc.parameterTypes().get(0) instanceof Type.PrimitiveType pt
                    && "bool".equals(Type.canonicalPrimitiveName(pt.name()))) {
                // Boolean.valueOf(Z) — format 0/1 as true/false
                stack.add(new JsIr.JsConditional(args.get(0),
                        new JsIr.JsIdentifier("true"), new JsIr.JsIdentifier("false")));
            } else {
                // boxed valueOf — JS values are already boxed; identity
                stack.add(args.get(0));
            }
            return;
        }
        if (p.coll.isChannelOp(kc)) {
            p.coll.handleChannelOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (p.coll.isListOp(kc)) {
            p.coll.handleListOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (p.coll.isMapOp(kc)) {
            p.coll.handleMapOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (p.coll.isSetOp(kc)) {
            p.coll.handleSetOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (p.rt.isRuntimeOp(kc)) {
            // kof_json_* / kof_io_* / kof_now / kof_box / kof_unbox — checked
            // before string ops: json.encode("...") has a String owner.
            p.rt.handleRuntimeOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (isStringOp(kc)) {
            handleStringOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (kc.kind() == KofCallKind.FUNCTION) {
            // top-level function call (arity routes default-parameter wrappers)
            finishCall(stack, kc, new JsIr.JsCall(
                    new JsIr.JsIdentifier(p.lc.jsFunctionName(kc.methodName(), kc.parameterTypes().size())),
                    args));
            return;
        }
        if (kc.kind() == KofCallKind.SUPER) {
            // super.method(args) — JS supports it natively inside class
            // methods; the receiver on the stack is this and is discarded.
            p.expr.pop(stack);
            finishCall(stack, kc, new JsIr.JsCall(
                    new JsIr.JsMember(new JsIr.JsIdentifier("super"), JsTypeMapper.sanitizeName(kc.methodName())), args));
            return;
        }
        if (kc.kind() == KofCallKind.STATIC) {
            String owner = JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(kc.ownerType()));
            finishCall(stack, kc, new JsIr.JsCall(
                    new JsIr.JsMember(new JsIr.JsIdentifier(owner), JsTypeMapper.sanitizeName(kc.methodName())), args));
            return;
        }
        // INSTANCE / INTERFACE — structural dispatch
        String owner = JsTypeMapper.ownerInternalName(kc.ownerType());
        if ("equals".equals(kc.methodName()) && owner != null
                && !ctx.hasClassMethod(owner, "equals")) {
            // Object.equals — reference equality (JVM semantics)
            stack.add(new JsIr.JsBinary(receiver, "===", args.get(0)));
            return;
        }
        finishCall(stack, kc, new JsIr.JsCall(
                new JsIr.JsMember(receiver, JsTypeMapper.sanitizeName(kc.methodName())), args));
    }

JsIr.JsExpression maybeAwait(KofCall kc, JsIr.JsExpression call) {
        KofCallKind kind = kc.kind();
        boolean needsAwait = false;
        if (kind == KofCallKind.STATIC || kind == KofCallKind.FUNCTION || kind == KofCallKind.SUPER) {
            needsAwait = p.lc.asyncMethods.getOrDefault(JsLoweringContext.calleeKeyFromCall(kc), false);
        } else if (kind == KofCallKind.INSTANCE || kind == KofCallKind.INTERFACE) {
            needsAwait = p.lc.asyncMethodNamesAnywhere.contains(kc.methodName());
        }
        return needsAwait ? new JsIr.JsAwait(call) : call;
    }

void finishCall(List<Object> stack, KofCall kc, JsIr.JsExpression call) {
        call = maybeAwait(kc, call);
        if (Type.isVoid(kc.returnType())) {
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

void handleConstructorCall(List<Object> stack, KofCall kc) {
        List<JsIr.JsExpression> args = new ArrayList<>();
        for (int i = 0; i < kc.parameterTypes().size(); i++) {
            if (stack.isEmpty()) break;
            Object top = stack.get(stack.size() - 1);
            if (top instanceof NewPending || top instanceof DupMarker) break;
            args.add(p.expr.pop(stack));
        }
        java.util.Collections.reverse(args);
        Object top = p.expr.popRaw(stack);
        if (top instanceof DupMarker) {
            Object newObj = p.expr.popRaw(stack);
            if (newObj instanceof NewPending np) {
                stack.add(new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), args));
                return;
            }
            throw new IllegalStateException("KofJS: DupMarker without NewPending");
        }
        if (top instanceof NewPending np) {
            stack.add(new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), args));
            return;
        }
        // super(...) constructor call
        throw new StatementEnd(new JsIr.JsCall(new JsIr.JsIdentifier("super"), args));
    }

boolean isPrintCall(KofCall kc) {
        if (!(kc.ownerType() instanceof Type.ClassType ct)) return false;
        return "java.io".equals(ct.packageName()) && "PrintStream".equals(ct.name())
                && ("println".equals(kc.methodName()) || "print".equals(kc.methodName()));
    }

boolean isStringOp(KofCall kc) {
        return BuiltinTypes.isString(kc.ownerType());
    }

void handleStringOp(MethodCtx ctx, List<Object> stack,
                                List<JsIr.JsExpression> preambleExprs, KofCall kc,
                                JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        switch (kc.methodName()) {
            case "kof_string_concat" -> stack.add(new JsIr.JsBinary(args.get(0), "+", args.get(1)));
            case "kof_string_equals" -> stack.add(new JsIr.JsConditional(
                    new JsIr.JsBinary(args.get(0), "===", args.get(1)),
                    new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
            case "valueOf" -> stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String"), List.of(args.get(0))));
            case "charAt" -> stack.add(new JsIr.JsCall(
                    new JsIr.JsMember(receiver, "charCodeAt"), List.of(args.get(0))));
            case "length" -> stack.add(new JsIr.JsMember(receiver, "length"));
            case "equals" -> stack.add(new JsIr.JsBinary(receiver, "===", args.get(0)));
            case "equalsIgnoreCase" -> stack.add(new JsIr.JsBinary(
                    new JsIr.JsCall(new JsIr.JsMember(receiver, "toUpperCase"), List.of()),
                    "===",
                    new JsIr.JsCall(new JsIr.JsMember(args.get(0), "toUpperCase"), List.of())));
            case "replace" -> {
                // Kof replace replaces all occurrences; JS replace only the
                // first, so lower through split/join. With two String
                // arguments the args are used as-is; with two characters
                // (Kof Ints) they are converted with String.fromCharCode.
                Type first = !kc.parameterTypes().isEmpty() ? kc.parameterTypes().get(0) : null;
                boolean charArgs = first instanceof Type.PrimitiveType pt
                        && "char".equals(Type.canonicalPrimitiveName(pt.name()));
                JsIr.JsExpression from = charArgs
                        ? new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("String"), "fromCharCode"),
                                List.of(args.get(0)))
                        : args.get(0);
                JsIr.JsExpression to = charArgs
                        ? new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("String"), "fromCharCode"),
                                List.of(args.get(1)))
                        : args.get(1);
                stack.add(new JsIr.JsCall(
                        new JsIr.JsMember(
                                new JsIr.JsCall(new JsIr.JsMember(receiver, "split"), List.of(from)),
                                "join"),
                        List.of(to)));
            }
            default -> {
                // substring, contains, indexOf, trim, toUpperCase, toLowerCase,
                // startsWith, endsWith, concat, split — direct JS mapping.
                JsIr.JsExpression method = "contains".equals(kc.methodName())
                        ? new JsIr.JsMember(receiver, "includes")
                        : new JsIr.JsMember(receiver, JsTypeMapper.sanitizeName(kc.methodName()));
                stack.add(new JsIr.JsCall(method, args));
            }
        }
    }

JsIr.JsExpression binaryExpr(KofBinary kb, JsIr.JsExpression left, JsIr.JsExpression right) {
        return switch (kb.op()) {
            case ADD -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "+", right));
            case SUB -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "-", right));
            case MUL -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "*", right));
            case DIV -> {
                if (JsTypeMapper.isIntFamily(kb.operandType())) {
                    yield intWrap(kb.operandType(), new JsIr.JsBinary(left, "/", right));
                }
                if (JsTypeMapper.isLongType(kb.operandType())) {
                    // JS / yields doubles; truncate toward zero like JVM LIDIV
                    yield new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("Math"), "trunc"),
                            List.of(new JsIr.JsBinary(left, "/", right)));
                }
                yield new JsIr.JsBinary(left, "/", right);
            }
            case MOD -> new JsIr.JsBinary(left, "%", right);
            case EQ -> new JsIr.JsBinary(left, "===", right);
            case NE -> new JsIr.JsBinary(left, "!==", right);
            case LT -> new JsIr.JsBinary(left, "<", right);
            case LE -> new JsIr.JsBinary(left, "<=", right);
            case GT -> new JsIr.JsBinary(left, ">", right);
            case GE -> new JsIr.JsBinary(left, ">=", right);
            case AND -> JsTypeMapper.isBoolOperand(kb.operandType())
                    ? new JsIr.JsBinary(left, "&&", right)
                    : new JsIr.JsBinary(left, "&", right);
            case OR -> JsTypeMapper.isBoolOperand(kb.operandType())
                    ? new JsIr.JsBinary(left, "||", right)
                    : new JsIr.JsBinary(left, "|", right);
            case XOR -> new JsIr.JsBinary(left, "^", right);
            case SHL -> new JsIr.JsBinary(left, "<<", right);
            case SHR -> new JsIr.JsBinary(left, ">>", right);
            case USHR -> new JsIr.JsBinary(left, ">>>", right);
        };
    }

    /**
     * Kof Int is a signed 32-bit type; JavaScript numbers are doubles. Wrap
     * int arithmetic with ToInt32 (| 0) to preserve Kof/JVM 32-bit semantics.
     */
JsIr.JsExpression intWrap(Type operandType, JsIr.JsExpression inner) {
        if (JsTypeMapper.isIntFamily(operandType)) {
            return new JsIr.JsBinary(inner, "|", new JsIr.JsNumber("0"));
        }
        return inner;
    }

JsIr.JsExpression unaryExpr(KofUnary ku, JsIr.JsExpression operand) {
        return switch (ku.op()) {
            case NEG -> new JsIr.JsUnary("-", operand);
            case NOT -> new JsIr.JsConditional(operand, new JsIr.JsNumber("0"), new JsIr.JsNumber("1"));
            case I2L, I2F, I2D, I2C, L2I, L2F, L2D, F2D, D2F -> operand;
            case D2I, F2I, D2L, F2L -> new JsIr.JsCall(new JsIr.JsIdentifier("Math.trunc"),
                    List.of(operand));
        };
    }

JsIr.JsExpression literalExpr(KofLoadLiteral lit) {
        if (lit.type() instanceof Type.PrimitiveType pt
                && "bool".equals(Type.canonicalPrimitiveName(pt.name()))) {
            Object v = lit.value();
            return new JsIr.JsIdentifier((v instanceof Integer i && i != 0) ? "true" : "false");
        }
        if (lit.value() instanceof Integer i) return new JsIr.JsNumber(Integer.toString(i));
        if (lit.value() instanceof Long l) return new JsIr.JsNumber(Long.toString(l));
        if (lit.value() instanceof Float f) return new JsIr.JsNumber(Float.toString(f));
        if (lit.value() instanceof Double d) return new JsIr.JsNumber(Double.toString(d));
        if (lit.value() instanceof String s) return new JsIr.JsString(s);
        return new JsIr.JsNull();
    }
}
