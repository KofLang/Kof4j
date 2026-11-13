package dev.kof.compiler.js;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.TopLevelOverload;
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
            handleConstructorCall(ctx, stack, kc);
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
            } else if (isCollectionValue(p0)) {
                // §388-B/§682: List/Set/Map (e array) pegam o MESMO formato de
                // container do Kof ("[1, 2]"/"{k=1}") — `String(x)` cru dava o
                // `Array.prototype.toString`/`[object Set]` do host. Usa os
                // predicados canônicos (não a comparação de pacote/nome, que
                // não casava a forma inferida de um `listOf` direto).
                emitContainerFormat(stack, args.get(0), p0);
            } else if (BuiltinTypes.isString(kc.ownerType()) && p0 != null && isDoubleOrFloatUnwrapped(p0)) {
                p.lc.registerRuntime("kofNumFmt");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofNumFmt"),
                        List.of(args.get(0), new JsIr.JsNumber(isFloatUnwrapped(p0) ? "1" : "0"))));
            } else if (BuiltinTypes.isString(kc.ownerType()) && (isErasedType(p0) || BuiltinTypes.isObject(p0))) {
                // §682: the erased-Object valueOf (the `+`/String.valueOf
                // desugars pass UNKNOWN outside native) — kofFormat is the Kof
                // container formatter, identical to String(x) for scalars.
                emitContainerFormat(stack, args.get(0), p0);
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
        // fatia 7 (D-KOF-EXPR): os predicados de classe chegam como FUNCTION
        // com owner String — precisam rotear ANTES do isStringOp, que casa
        // qualquer dono String e os derrubaria no default do handleStringOp
        // (JsMember(receiver=null) → ICE "unknown JS expression: null").
        if (kc.kind() == KofCallKind.FUNCTION && kc.methodName().startsWith("kof_expr_is_")) {
            // §9 do plano: o runtime JS usa o nome camelCase (kof_expr_is_digit
            // → kofExprIsDigit) — o MESMO mapeamento do registry de slices.
            String jsName = JsTypeMapper.runtimeJsName(kc.methodName());
            p.lc.registerRuntime(jsName);
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(jsName), args));
            return;
        }
        if (isStringOp(kc)) {
            JsStringOpEmitter.handleStringOp(this, p, ctx, stack, preambleExprs, kc, receiver, args);
            return;
        }
        if (kc.kind() == KofCallKind.FUNCTION) {
            finishCall(stack, kc, new JsIr.JsCall(
                    new JsIr.JsIdentifier(JsTypeMapper.sanitizeName(
                            p.lc.jsFunctionName(kc.methodName(), kc.parameterTypes(), kc.parameterTypes().size()))),
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
                && (isDoubleOrFloatUnwrapped(kc.ownerType()) || JsStringOpEmitter.isJdkWrapperFp(kc.ownerType()))) {
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

void handleConstructorCall(MethodCtx ctx, List<Object> stack, KofCall kc) {
        String owner = JsTypeMapper.ownerInternalName(kc.ownerType());
        boolean dispatch = owner != null && !owner.isEmpty() && p.lc.ctorDispatch.contains(owner);
        List<JsIr.JsExpression> args = new ArrayList<>();
        for (int i = 0; i < kc.parameterTypes().size(); i++) {
            if (stack.isEmpty()) break;
            Object top = stack.get(stack.size() - 1);
            if (top instanceof NewPending || top instanceof DupMarker) break;
            args.add(p.expr.pop(stack));
        }
        java.util.Collections.reverse(args);
        List<JsIr.JsExpression> sourceArgs = new ArrayList<>(args);
        if (dispatch) {
            int max = p.lc.ctorMaxArity.getOrDefault(owner, args.size());
            while (args.size() < max) {
                args.add(new JsIr.JsNull());
            }
            args.add(new JsIr.JsString(TopLevelOverload.sigTag(kc.parameterTypes()).replace('_', '$')));
        }
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
        // #757: a private overloaded constructor body cannot be a real JS
        // constructor, so delegation to another <init> of the same class goes
        // through a prototype function with an explicit receiver.
        if (dispatch && ctx != null && owner.equals(ctx.kofClassName)) {
            JsIr.JsExpression receiver = top instanceof JsIr.JsExpression expr
                    ? expr : new JsIr.JsThis();
            List<JsIr.JsExpression> callArgs = new ArrayList<>();
            callArgs.add(receiver);
            callArgs.addAll(sourceArgs);
            String ctorName = "__kof_ctor"
                    + TopLevelOverload.sigTag(kc.parameterTypes()).replace('_', '$');
            JsIr.JsExpression privateCtor = new JsIr.JsMember(
                    new JsIr.JsMember(new JsIr.JsIdentifier(JsTypeMapper.jsClassName(owner)), "prototype"),
                    ctorName);
            throw new StatementEnd(new JsIr.JsCall(
                    new JsIr.JsMember(privateCtor, "call"), callArgs));
        }
        // super(...) constructor call
        throw new StatementEnd(new JsIr.JsCall(new JsIr.JsIdentifier("super"), args));
    }

boolean isPrintCall(KofCall kc) {
        if (!(kc.ownerType() instanceof Type.ClassType ct)) return false;
        return "java.io".equals(ct.packageName()) && "PrintStream".equals(ct.name())
                && ("println".equals(kc.methodName()) || "print".equals(kc.methodName()));
    }

    /**
     * #1123: format a container for the JS target. A typed
     * {@code List<Double>}/{@code Set<Float>}/{@code List<Long>} keeps its
     * element formatting (a JS number has no Int/Double distinction, so the
     * kind must come from the compiler); everything else uses the generic
     * {@code kofFormat}.
     */
    void emitContainerFormat(List<Object> stack, JsIr.JsExpression value, Type t) {
        String elemKind = t == null ? null : JsCollectionElementKind.of(t);
        if (elemKind != null) {
            p.lc.registerRuntime("kofFormatTyped");
            p.lc.registerRuntime("kofFormat");
            p.lc.registerRuntime("kofNumFmt");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofFormatTyped"),
                    List.of(value, new JsIr.JsString(elemKind))));
        } else {
            p.lc.registerRuntime("kofFormat");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofFormat"), List.of(value)));
        }
    }

    boolean isStringOp(KofCall kc) {
        return BuiltinTypes.isString(kc.ownerType());
    }

    /** {@code String.valueOf}/{@code java.lang.*.valueOf} — valueOf de plataforma. */
    private static boolean isJdkValueOfOwner(Type owner) {
        if (BuiltinTypes.isString(owner)) return true;
        return owner instanceof Type.ClassType ct && "java.lang".equals(ct.packageName());
    }

    /** §682: valor cuja stringificação no Kof é o formato de CONTAINER
     *  (`[a, b]`/`{k=v}`) — List/Set/Map/array, aceitando Nullable. */
    static boolean isCollectionValue(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        return inner instanceof Type.ArrayType || BuiltinTypes.isList(inner)
                || BuiltinTypes.isMap(inner) || BuiltinTypes.isSet(inner);
    }

    /** §682: tipo do argumento APAGADO (o `+`/`String.valueOf` passam
     *  `Type.UnknownType` no JS fora do native, e um `T` genérico chega como
     *  `TypeVariable`) — o valueOf só tem a forma do VALOR em runtime. */
    static boolean isErasedType(Type t) {
        // null = parameterTypes vazio (o call não carrega o tipo) — mesma
        // situação de tipo desconhecido: só a forma do VALOR decide.
        return t == null || t instanceof Type.UnknownType || t instanceof Type.TypeVariable;
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

}

