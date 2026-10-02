package dev.kof.compiler.js;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.Type;

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
public final class JsCallEmitter {

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
        // Só o valueOf de PLATAFORMA (String.valueOf / Integer.valueOf /
        // Boolean.valueOf…) é a identidade boxed ou conversão. Um
        // `Color.valueOf(name)` de usuário (D-ENUM207, enum real) tem o
        // MÉTODO estático da própria classe — sem este guard o call site
        // colapsava para o argumento (`Color.valueOf("Blue")` virava "Blue").
        if ("valueOf".equals(kc.methodName()) && kc.kind() == KofCallKind.STATIC
                && isJdkValueOfOwner(kc.ownerType())) {
            Type p0 = kc.parameterTypes().isEmpty() ? null : kc.parameterTypes().get(0);
            if (p0 != null && isCharUnwrapped(p0)) {
                if (p0 instanceof Type.NullableType) {
                    p.lc.registerRuntime("kofCharValueOf");
                    stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofCharValueOf"), List.of(args.get(0))));
                } else {
                    stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String.fromCharCode"), List.of(args.get(0))));
                }
            } else if (p0 instanceof Type.ArrayType) {
                // §388-B: array cru (readBytes et al.) pega o MESMO formato de
                // container da casa ("[65, 66]") — String() cru dava `65,66`
                // sem colchetes (a paridade reversa medida do §388-B).
                p.lc.registerRuntime("kofFormat");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofFormat"), List.of(args.get(0))));
            } else if (p0 instanceof Type.ClassType ct && "kof".equals(ct.packageName())
                    && (ct.name().equals("List") || ct.name().equals("Map") || ct.name().equals("Set"))) {
                p.lc.registerRuntime("kofFormat");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofFormat"), List.of(args.get(0))));
            } else if (BuiltinTypes.isString(kc.ownerType()) && p0 != null && isDoubleOrFloatUnwrapped(p0)) {
                p.lc.registerRuntime("kofNumFmt");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofNumFmt"),
                        List.of(args.get(0), new JsIr.JsNumber(isFloatUnwrapped(p0) ? "1" : "0"))));
            } else if (BuiltinTypes.isString(kc.ownerType())) {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String"), List.of(args.get(0))));
            } else if (p0 instanceof Type.PrimitiveType pt && "bool".equals(Type.canonicalPrimitiveName(pt.name()))) {
                p.lc.registerRuntime("kofBoolValueOf");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofBoolValueOf"), List.of(args.get(0))));
            } else {
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
        if (("kofRecordEq".equals(kc.methodName()) || "kofFpEq".equals(kc.methodName()))
                && kc.parameterTypes().size() == 2) {
            // §262(b): igualdade de record null-safe partilhada (Objects.equals
            // semantics) baixada p/ helper do runtime — o desugar com jumps da
            // lane não dobra em posição de condição no reconstructor JS (o
            // mesmo motivo do `&&`/`||` p/ target != JS, ExpressionBinaryLowerer:167).
            String eqFn = kc.methodName();
            ctx.lc.registerRuntime(eqFn);
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(eqFn), args));
            return;
        }
        // §239 (JS): String.format via host bridge — dispatch no p.rt (JsRuntimeOps)
        if (p.rt.isStaticFormat(kc)) { p.rt.emitStaticFormat(stack, args); return; }
        if (isStringOp(kc)) {
            handleStringOp(ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (kc.kind() == KofCallKind.FUNCTION) {
            // top-level function call (arity routes default-parameter wrappers)
            finishCall(stack, kc, new JsIr.JsCall(
                    new JsIr.JsIdentifier(p.lc.jsFunctionName(kc.methodName(), kc.parameterTypes(), kc.parameterTypes().size())),
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
            // #233 (JS): Double/Float.isNaN/isInfinite/isFinite — estáticos
            // JDK reais; o backend JS não tem java_lang_Double — map para
            // Number.is* (paridade JVM: (D)Z).
            String ownerName = JsTypeMapper.jsClassName(
                    JsTypeMapper.ownerInternalName(kc.ownerType()));
            // §218/#148 (JS): Int/Long.toHexString/toBinaryString — estáticos
            // JDK reais; o backend JS não tem java_lang_Integer/java_lang_Long —
            // map para toString(radix) (paridade JVM: (I)Ljava/lang/String;).
            // O JDK é UNSIGNED de 32/64 bits (-42 → "ffffffd6"); JS mantém o
            // sinal (-2a) — aplicar a máscara >>> 0 (Int) / BigInt.asUintN(64)
            // (Long) antes do radix, igual ao JVM.
            if (kc.parameterTypes().size() == 1
                    && ("java_lang_Integer".equals(ownerName) || "java_lang_Long".equals(ownerName))
                    && ("toHexString".equals(kc.methodName()) || "toBinaryString".equals(kc.methodName()))) {
                boolean isLong = "java_lang_Long".equals(ownerName);
                int radix = "toHexString".equals(kc.methodName()) ? 16 : 2;
                JsIr.JsExpression arg = args.get(0);
                JsIr.JsExpression unsigned = isLong
                        ? new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("BigInt"),
                                "asUintN"), List.of(new JsIr.JsNumber("64"), arg))
                        : new JsIr.JsBinary(arg, ">>>", new JsIr.JsNumber("0"));
                JsIr.JsExpression repr = new JsIr.JsCall(
                        new JsIr.JsMember(unsigned, "toString"), List.of(new JsIr.JsNumber(String.valueOf(radix))));
                finishCall(stack, kc, repr);
                return;
            }
            if (kc.parameterTypes().size() == 1
                    && ("java_lang_Double".equals(ownerName) || "java_lang_Float".equals(ownerName))) {
                if ("toString".equals(kc.methodName())) {
                    // §264 (JS): Double.toString(d)/Float.toString(d) estaticos
                    // — caíam no dispatch generico (ReferenceError, §235 family)
                    // OU, no caso do receiver primitivo, em `String(v)` cru
                    // ("4"). Formato do JDK via kofNumFmt.
                    p.lc.registerRuntime("kofNumFmt");
                    boolean isFloat = "java_lang_Float".equals(ownerName);
                    finishCall(stack, kc, new JsIr.JsCall(new JsIr.JsIdentifier("kofNumFmt"),
                            List.of(args.get(0), new JsIr.JsNumber(isFloat ? "1" : "0"))));
                    return;
                }
                String jsPredicate = switch (kc.methodName()) {
                    case "isNaN" -> "Number.isNaN";
                    case "isInfinite" -> null; // tratado abaixo (JS não tem Number.isInfinite)
                    case "isFinite" -> "Number.isFinite";
                    default -> null;
                };
                if (jsPredicate != null) {
                    finishCall(stack, kc, new JsIr.JsCall(
                            new JsIr.JsIdentifier(jsPredicate), args));
                    return;
                }
                if ("isInfinite".equals(kc.methodName())) {
                    // Number.isFinite(v) && !Number.isNaN(v) ≡ isInfinite? Não —
                    // isInfinite(v) = !Number.isNaN(v) && !Number.isFinite(v)
                    // (NaN: isFinite=false, isInfinite=false — coberto).
                    JsIr.JsExpression arg = args.get(0);
                    JsIr.JsExpression notFinite = new JsIr.JsUnary("!",
                            new JsIr.JsCall(new JsIr.JsIdentifier("Number.isFinite"), List.of(arg)));
                    JsIr.JsExpression notNaN = new JsIr.JsUnary("!",
                            new JsIr.JsCall(new JsIr.JsIdentifier("Number.isNaN"), List.of(arg)));
                    finishCall(stack, kc, new JsIr.JsBinary(notFinite, "&&", notNaN));
                    return;
                }
            }
            // §235 (JS): estáticos `parse*` dos wrappers JDK — o dispatch
            // genérico emitia `java_lang_Integer.parseInt(...)` (ReferenceError
            // silencioso, R6). Os helpers do runtime já existem (github #51 +
            // §81: `kof_string_to_int/_long/_double/_float`, trim/regex estreito/
            // overflow lança como no JVM); `parseBoolean` é "true".equalsIgnoreCase.
            if (kc.parameterTypes().size() == 1) {
                String parseFn = switch (ownerName + "." + kc.methodName()) {
                    case "java_lang_Integer.parseInt" -> "kof_string_to_int";
                    case "java_lang_Long.parseLong" -> "kof_string_to_long";
                    case "java_lang_Double.parseDouble" -> "kof_string_to_double";
                    case "java_lang_Float.parseFloat" -> "kof_string_to_float";
                    default -> null;
                };
                if (parseFn != null) {
                    ctx.lc.registerRuntime(parseFn);
                    finishCall(stack, kc, new JsIr.JsCall(
                            new JsIr.JsIdentifier(parseFn), List.of(args.get(0))));
                    return;
                }
                if ("java_lang_Boolean.parseBoolean".equals(ownerName + "." + kc.methodName())) {
                    JsIr.JsExpression s = new JsIr.JsCall(
                            new JsIr.JsIdentifier("String"), List.of(args.get(0)));
                    JsIr.JsExpression lower = new JsIr.JsCall(
                            new JsIr.JsMember(s, "toLowerCase"), List.of());
                    finishCall(stack, kc, new JsIr.JsBinary(
                            new JsIr.JsCall(new JsIr.JsMember(lower, "trim"), List.of()),
                            "===", new JsIr.JsString("true")));
                    return;
                }
            }
            String owner = JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(kc.ownerType()));
            finishCall(stack, kc, new JsIr.JsCall(
                    new JsIr.JsMember(new JsIr.JsIdentifier(owner), JsTypeMapper.sanitizeName(kc.methodName())), args));
            return;
        }
        // INSTANCE / INTERFACE — structural dispatch
        String owner = JsTypeMapper.ownerInternalName(kc.ownerType());
        if ("toString".equals(kc.methodName()) && kc.parameterTypes().isEmpty()
                && (isDoubleOrFloatUnwrapped(kc.ownerType()) || isJdkWrapperFp(kc.ownerType()))) {
            // §264 (JS): `d.toString()` com d Double/Float (primitivo ou wrapper
            // — o typer boxia p/ java.lang.Double e o dispatch estrutural
            // gerava `(4).toString()` = "4", o String cru do JS). O valor no JS
            // e Number nos dois casos. Formato do JDK via kofNumFmt.
            p.lc.registerRuntime("kofNumFmt");
            boolean isFloat = isFloatUnwrapped(kc.ownerType())
                    || "java_lang_Float".equals(JsTypeMapper.jsClassName(owner));
            finishCall(stack, kc, new JsIr.JsCall(new JsIr.JsIdentifier("kofNumFmt"),
                    List.of(receiver, new JsIr.JsNumber(isFloat ? "1" : "0"))));
            return;
        }
        if ("equals".equals(kc.methodName()) && owner != null
                && !ctx.hasClassMethod(owner, "equals")) {
            // Object.equals — reference equality (JVM semantics)
            stack.add(new JsIr.JsBinary(receiver, "===", args.get(0)));
            return;
        }
        // §131: método de classe sobrecarregado tem nome JS tageado por
        // assinatura (o MESMO mangle do lowerFunction) — structural dispatch
        // precisa usar o nome exato; não-sobrecarregado volta o nome cru.
        String calleeJsName = p.lc.jsFunctionName(kc.methodName(), kc.parameterTypes(),
                kc.parameterTypes().size());
        finishCall(stack, kc, new JsIr.JsCall(
                new JsIr.JsMember(receiver, JsTypeMapper.sanitizeName(calleeJsName)), args));
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

    /** {@code String.valueOf}/{@code java.lang.*.valueOf} — valueOf de plataforma. */
    private static boolean isJdkValueOfOwner(Type owner) {
        if (BuiltinTypes.isString(owner)) return true;
        return owner instanceof Type.ClassType ct && "java.lang".equals(ct.packageName());
    }

    /** §264: Double/Float crus (ou Nullable deles) — o JS trata-os como Number. */
    private static boolean isDoubleOrFloatUnwrapped(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        return inner instanceof Type.PrimitiveType pt
                && ("double".equals(Type.canonicalPrimitiveName(pt.name()))
                        || "float".equals(Type.canonicalPrimitiveName(pt.name())));
    }

    private static boolean isFloatUnwrapped(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        return inner instanceof Type.PrimitiveType pt
                && "float".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    private static boolean isCharUnwrapped(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        return inner instanceof Type.PrimitiveType pt && "char".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    /** §264: wrapper-boxed Double/Float (o typer boxou o receiver de `toString`). */
    private static boolean isJdkWrapperFp(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        return inner instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                && ("Double".equals(ct.name()) || "Float".equals(ct.name()));
    }


void handleStringOp(MethodCtx ctx, List<Object> stack,
                                List<JsIr.JsExpression> preambleExprs, KofCall kc,
                                JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        // §102 (paridade absoluta): com 2 args (needle + from), o
        // String.prototype do JS diverge do JDK no clamp do `from`
        // (lastIndexOf(from<0) JS=0 vs JDK=-1; startsWith(from>len) JS=true vs
        // JDK=false; vazio+from JS difere). Baixa p/ helper top-level com os
        // clamps do JDK. 1-arg cai no default (nativo, bate o JDK).
        if (args.size() >= 2) {
            String s2fn = switch (kc.methodName()) {
                case "indexOf" -> "kof_string_index_of2";
                case "lastIndexOf" -> "kof_string_last_index_of2";
                case "startsWith" -> "kof_string_starts_with2";
                default -> null;
            };
            if (s2fn != null) {
                ctx.lc.registerRuntime(s2fn);
                List<JsIr.JsExpression> full = new ArrayList<>();
                full.add(receiver);
                full.addAll(args);
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(s2fn), full));
                return;
            }
        }
        switch (kc.methodName()) {
            case "kof_string_concat" -> stack.add(new JsIr.JsBinary(args.get(0), "+", args.get(1)));
            case "kof_string_equals" -> stack.add(new JsIr.JsConditional(
                    new JsIr.JsBinary(args.get(0), "===", args.get(1)),
                    new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
            case "valueOf" -> stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String"), List.of(args.get(0))));
            case "charAt" -> stack.add(new JsIr.JsCall(
                    new JsIr.JsMember(receiver, "charCodeAt"), List.of(args.get(0))));
            case "length" -> stack.add(new JsIr.JsMember(receiver, "length"));
            // §145 (12/09, #101): String.prototype NÃO tem isEmpty (é Java);
            // o default gerava `t.isEmpty()` = TypeError. length === 0.
            case "isEmpty" -> stack.add(new JsIr.JsBinary(
                    new JsIr.JsMember(receiver, "length"), "===",
                    new JsIr.JsNumber("0")));
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
            // Conversões String→número (github #51): não existem em String.prototype;
            // o backend JS baixa p/ helper top-level do runtime (paridade JVM/Native:
            // parseInt(s.trim()), erro de parse → exceção). Antes caíam no default
            // e geravam `texto.kof_string_to_int()` → TypeError em runtime.
            case "kof_string_to_int", "kof_string_to_long",
                 "kof_string_to_double", "kof_string_to_float" -> {
                ctx.lc.registerRuntime(kc.methodName());
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(kc.methodName()), List.of(receiver)));
            }
            // bug 97 (face JS): hashCode/compareTo NÃO existem em
            // String.prototype → o default mapeava p/ `a.compareTo()` =
            // TypeError. hashCode reusa kofHashCode (bug 42 — 31*h+unit
            // UTF-16, mesmo algoritmo JVM/x86); compareTo baixa p/ helper
            // kofStringCompareTo (walk de code units, semântica JVM).
            case "hashCode" -> {
                ctx.lc.registerRuntime("kofHashCode");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofHashCode"), List.of(receiver)));
            }
            case "compareTo" -> {
                ctx.lc.registerRuntime("kofStringCompareTo");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStringCompareTo"),
                        List.of(receiver, args.get(0))));
            }
            // D-FULL-PARITY-050 row 11: compareToIgnoreCase baixa para helper
            // kofStringCompareToIgnoreCase (CASE_INSENSITIVE_ORDER do JVM: fold
            // SIMPLES por code unit — upper, senão lower; expandir nao cabe).
            case "compareToIgnoreCase" -> {
                ctx.lc.registerRuntime("kofStringCompareToIgnoreCase");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofStringCompareToIgnoreCase"),
                        List.of(receiver, args.get(0))));
            }
            // D-FULL-PARITY-050 row 11: String.prototype NÃO tem toCharArray →
            // o default gerava TypeError. Helper kofToCharArray (array de code
            // units UTF-16, igual ao JVM).
            case "toCharArray" -> {
                ctx.lc.registerRuntime("kofToCharArray");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofToCharArray"),
                        List.of(receiver)));
            }
            case "split" -> {
                // §111: JS String.prototype.split PRESERVA vazios trailing
                // ("a,".split(",")=["a",""]) mas o contrato é o Java
                // (remove trailing, exceto input "" → [""]). helper kofSplit.
                ctx.lc.registerRuntime("kofSplit");
                List<JsIr.JsExpression> sa = new ArrayList<>();
                sa.add(receiver);
                sa.addAll(args);
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofSplit"), sa));
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

}
