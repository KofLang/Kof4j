package dev.kof.compiler.js;
import dev.kof.compiler.AccessFlags;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRField;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.TopLevelOverload;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * JsClassEmitter — lowering de IRClass para JsIr.JsClass: campos, métodos
 * (via JsMethodParser), sintéticos de record (toString/equals/toJSON) e o
 * helper de binding do json.decode (REFACTOR-500 FASE 4).
 */
public final class JsClassEmitter {

    private final JsMethodParser p;

    JsClassEmitter(JsMethodParser p) {
        this.p = p;
    }

    JsIr.JsClass lowerClass(IRClass clazz) {
        String jsName = JsTypeMapper.jsClassName(clazz.name());
        String jsSuper = null;
        if (clazz.superName() != null && !clazz.superName().isEmpty()
                && !"java/lang/Object".equals(clazz.superName())
                && !"java/lang/Record".equals(clazz.superName())) {
            jsSuper = JsTypeMapper.jsClassName(clazz.superName());
        }
        // #818: a record-ness vem do lowering (IRClass.isRecord) além da
        // superclasse — um record que estende classe do usuário não é
        // `java/lang/Record` mas continua sendo record.
        boolean isRecord = "java/lang/Record".equals(clazz.superName())
                || p.lc.recordClassNames.contains(clazz.name());
        List<JsIr.JsField> fields = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            boolean isStatic = (field.accessFlags() & AccessFlags.STATIC) != 0;
            String fieldName = isRecord ? "_" + JsTypeMapper.sanitizeName(field.name()) : JsTypeMapper.sanitizeName(field.name());
            fields.add(new JsIr.JsField(fieldName,
                    field.initialValue() != null ? JsTypeMapper.literalText(field.initialValue()) : null, isStatic));
        }
        List<JsIr.JsFunction> methods = new ArrayList<>();
        List<IRMethod> ctors = new ArrayList<>();
        for (IRMethod method : clazz.methods()) {
            if ("<init>".equals(method.name())) ctors.add(method);
        }
        IRMethod canonicalCtor = null;
        for (IRMethod method : ctors) {
            if (canonicalCtor == null
                    || method.parameterTypes().size() > canonicalCtor.parameterTypes().size()) {
                canonicalCtor = method;
            }
        }
        boolean ctorDispatch = ctors.size() > 1;
        if (ctorDispatch) {
            p.lc.ctorPrivate.add(clazz.name());
            methods.add(lowerConstructorDispatch(clazz, ctors));
        }
        for (IRMethod method : clazz.methods()) {
            if ("<init>".equals(method.name())) {
                // #757: a JS class cannot carry overloaded constructors in
                // method-name dispatch; emit all <init> bodies under one real
                // JS function so `new T(...)` can still create the object.
                if (ctorDispatch) {
                    JsIr.JsFunction ctor = lowerConstructor(clazz, method);
                    String privateName = "__kof_ctor" + ctorSigSuffix(method);
                    methods.add(new JsIr.JsFunction(privateName, ctor.parameters(), ctor.body(),
                            false, false, false, false, ctor.kofLine()));
                } else if (method == canonicalCtor) {
                    methods.add(lowerConstructor(clazz, method));
                }
            } else if ("<clinit>".equals(method.name())) {
                // #133 (§186): <clinit> não é nome de método válido em JS —
                // renomeia para o static `_kof_clinit()`, chamado pelo
                // módulo antes do main (JsBackend).
                JsIr.JsFunction clinit = p.lowerFunction(method, clazz, true);
                methods.add(new JsIr.JsFunction("_kof_clinit", clinit.parameters(), clinit.body(),
                        true, false, false, clinit.isAsync(), clinit.kofLine()));
            } else {
                // §724: um método ABSTRACT de interface não tem corpo — emitir
                // um stub quebraria a classe JS. Uma interface só chega aqui
                // quando tem membro static (skipClass).
                if ((method.accessFlags() & AccessFlags.ABSTRACT) != 0) continue;
                boolean isStatic = (method.accessFlags() & AccessFlags.STATIC) != 0;
                methods.add(p.lowerFunction(method, clazz, isStatic));
            }
        }
        if (isRecord) {
            methods.add(p.records.lowerRecordToString(clazz));
            methods.add(p.records.lowerRecordToJson(clazz));
            if (JsRecordSynthetics.hasFpField(clazz)) methods.add(p.records.lowerRecordToJsonFp(clazz));
            methods.add(p.records.lowerRecordFfiFields(clazz));
            methods.add(p.records.lowerRecordFfiFrom(clazz, jsName));
            methods.add(p.records.lowerRecordEquals(clazz));
            methods.add(p.records.lowerRecordHashCode(clazz));
        }
        return new JsIr.JsClass(jsName, jsSuper, fields, methods);
    }


    /**
     * json.decode&lt;Class&gt; binds the parsed object to the Kof class:
     * records use their canonical constructor; classes get a default instance
     * with fields assigned by name (mirroring the JVM reflection binding).
     */
    JsIr.JsFunction lowerDecodeHelper(IRClass clazz) {
        String jsName = JsTypeMapper.jsClassName(clazz.name());
        boolean isRecord = "java/lang/Record".equals(clazz.superName())
                || p.lc.recordClassNames.contains(clazz.name());   // #818
        // Accept both a JSON string and an already-parsed object (list decode
        // maps parsed elements through this helper).
        JsIr.JsExpression parsed = new JsIr.JsConditional(
                new JsIr.JsBinary(new JsIr.JsUnary("typeof", new JsIr.JsIdentifier("json")),
                        "===", new JsIr.JsString("string")),
                new JsIr.JsCall(new JsIr.JsIdentifier("JSON.parse"),
                        List.of(new JsIr.JsIdentifier("json"))),
                new JsIr.JsIdentifier("json"));
        List<JsIr.JsStatement> body = new ArrayList<>();
        body.add(new JsIr.JsVarDecl("p", parsed, true));
        // §565 contract on JS (audit JSN-01, ledger §670): a missing/JSON-null
        // NON-nullable PRIMITIVE component is the named JSN004 failure, never a
        // silent undefined (D-JSON-MISSING-PRIMITIVE). Nullable/reference
        // fields keep the null semantics (no guard).
        for (IRField field : clazz.fields()) {
            if (!(field.type() instanceof Type.PrimitiveType)) continue;
            JsIr.JsExpression raw = new JsIr.JsMember(
                    new JsIr.JsIdentifier("p"), JsTypeMapper.sanitizeName(field.name()));
            JsIr.JsExpression missing = new JsIr.JsBinary(
                    new JsIr.JsBinary(raw, "===", new JsIr.JsIdentifier("undefined")), "||",
                    new JsIr.JsBinary(raw, "===", new JsIr.JsIdentifier("null")));
            body.add(new JsIr.JsIf(missing,
                    List.of(new JsIr.JsThrow(new JsIr.JsCall(new JsIr.JsIdentifier("Error"),
                            List.of(new JsIr.JsString("JSN004: missing field '"
                                    + field.name() + "' for " + clazz.name()))))),
                    List.of()));
        }
        List<JsIr.JsExpression> ctorArgs = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            ctorArgs.add(decodeFieldValue(field));
        }
        JsIr.JsExpression instance = new JsIr.JsNew(new JsIr.JsIdentifier(jsName),
                p.lc.ctorDispatch.contains(clazz.name())
                        ? appendCtorToken(ctorArgs, clazz.fields().stream().map(IRField::type).toList())
                        : ctorArgs);
        if (isRecord) {
            body.add(new JsIr.JsVarDecl("o", instance, true));
        } else {
            List<JsIr.JsExpression> defaultArgs = new ArrayList<>();
            if (p.lc.ctorDispatch.contains(clazz.name())
                    && p.lc.ctorSigTokens.getOrDefault(clazz.name(), java.util.Set.of()).contains("")) {
                defaultArgs.add(new JsIr.JsString(""));
            }
            body.add(new JsIr.JsVarDecl("o", new JsIr.JsNew(new JsIr.JsIdentifier(jsName), defaultArgs), true));
            for (IRField field : clazz.fields()) {
                body.add(new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(new JsIr.JsIdentifier("o"), JsTypeMapper.sanitizeName(field.name())), "=",
                        decodeFieldValue(field))));
            }
        }
        body.add(new JsIr.JsReturn(new JsIr.JsIdentifier("o")));
        return new JsIr.JsFunction("__kof_decode_" + jsName, List.of("json"), body, false, false, true);
    }

    /**
     * #740 slice 2: a record field must be converted by its DECLARED type, not
     * copied raw. The old helper assigned `p.field` verbatim, so a nested
     * record stayed a plain JS object (`o.inner.z` read `_z` on `{z:4}` →
     * undefined) and a `List<Record>`/`Map<..,Record>` kept raw objects —
     * silent divergence from the JVM/Script paths. Primitives/Strings/Object
     * are unchanged; the nested class decoders are registered transitively so
     * the helper is emitted. `null`/missing stays `null` (JVM reference
     * semantics; the `== null` guard also catches a missing key = undefined).
     */
    private JsIr.JsExpression decodeFieldValue(IRField field) {
        JsIr.JsExpression raw = new JsIr.JsMember(
                new JsIr.JsIdentifier("p"), JsTypeMapper.sanitizeName(field.name()));
        Type t = field.type() instanceof Type.NullableType nt ? nt.inner() : field.type();
        if (BuiltinTypes.isList(t)) {
            Type elem = BuiltinTypes.listElement(t);
            Type inner = elem instanceof Type.NullableType nt2 ? nt2.inner() : elem;
            if (inner instanceof Type.ClassType e2
                    && p.lc.classMethodNames.containsKey(e2.internalName())) {
                String nested = JsTypeMapper.jsClassName(e2.internalName());
                p.lc.decodeHelpers.add(nested);
                JsIr.JsExpression mapper = new JsIr.JsCall(
                        new JsIr.JsIdentifier("__kof_decode_" + nested),
                        List.of(new JsIr.JsIdentifier("o")));
                JsIr.JsExpression mapped = new JsIr.JsCall(
                        new JsIr.JsMember(raw, "map"), List.of(new JsIr.JsArrow(List.of("o"), mapper)));
                return nullGuard(raw, mapped);
            }
            return raw;
        }
        if (BuiltinTypes.isMap(t)) {
            Type mv = BuiltinTypes.mapValue(t);
            Type inner = mv instanceof Type.NullableType nt3 ? nt3.inner() : mv;
            if (inner instanceof Type.ClassType mvct
                    && p.lc.classMethodNames.containsKey(mvct.internalName())) {
                String nested = JsTypeMapper.jsClassName(mvct.internalName());
                p.lc.decodeHelpers.add(nested);
                p.lc.registerRuntime("kofJsonDecodeObjectMap");
                return nullGuard(raw, new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonDecodeObjectMap"),
                        List.of(raw, new JsIr.JsIdentifier("__kof_decode_" + nested))));
            }
            p.lc.registerRuntime("kofJsonDecodeMap");
            return nullGuard(raw, new JsIr.JsCall(
                    new JsIr.JsIdentifier("kofJsonDecodeMap"), List.of(raw)));
        }
        if (BuiltinTypes.isObject(t)) {
            p.lc.registerRuntime("kofJsonDeep");
            return nullGuard(raw, new JsIr.JsCall(
                    new JsIr.JsIdentifier("kofJsonDeep"), List.of(raw)));
        }
        if (t instanceof Type.ClassType ct && p.lc.classMethodNames.containsKey(ct.internalName())) {
            String nested = JsTypeMapper.jsClassName(ct.internalName());
            p.lc.decodeHelpers.add(nested);
            return nullGuard(raw, new JsIr.JsCall(
                    new JsIr.JsIdentifier("__kof_decode_" + nested), List.of(raw)));
        }
        return raw;
    }

    /**
     * #740 slice 2: the class decoders referenced by this class's fields
     * (nested record, {@code List<Record>} element, {@code Map<_,Record>}
     * value). Used by {@code JsBackend} to close the decode-helper set
     * transitively BEFORE emitting, so a nested helper whose class sorts
     * before the parent is still emitted.
     */
    java.util.Set<String> nestedDecoderNames(IRClass clazz) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (IRField field : clazz.fields()) {
            Type t = field.type() instanceof Type.NullableType nt ? nt.inner() : field.type();
            Type target;
            if (BuiltinTypes.isList(t)) target = BuiltinTypes.listElement(t);
            else if (BuiltinTypes.isMap(t)) target = BuiltinTypes.mapValue(t);
            else target = t;
            if (target instanceof Type.NullableType nt2) target = nt2.inner();
            if (target instanceof Type.ClassType ct && p.lc.classMethodNames.containsKey(ct.internalName())) {
                out.add(JsTypeMapper.jsClassName(ct.internalName()));
            }
        }
        return out;
    }

    /** `raw == null ? null : expr` — loose equality also catches a missing key (undefined). */
    private static JsIr.JsExpression nullGuard(JsIr.JsExpression raw, JsIr.JsExpression expr) {
        return new JsIr.JsConditional(
                new JsIr.JsBinary(raw, "==", new JsIr.JsNull()),
                new JsIr.JsNull(),
                expr);
    }

    /**
     * Records: the component fields are private in Kof/JVM; in JS the accessor
     * method shares the component name, so the backing field gets a "_" prefix
     * (this.name as a property would shadow the name() accessor).
     */
    String jsFieldName(IRClass clazz, String name) {
        // #836: o mesmo predicado de record-ness do emit da classe (linha ~40):
        // um record SINTÉTICO (class X(p) extends Base → componentes+acessores)
        // tem o acessor `p()`; o backing field PRECISA do prefixo `_` mesmo sem
        // a superclasse literal java/lang/Record, senão `this.p = <default>`
        // SOBRESCREVE o método p() (o campo vira número, `p()` deixa de existir
        // → TypeError no JS). Antes só o Record literal levava o prefixo.
        if ("java/lang/Record".equals(clazz.superName())
                || p.lc.recordClassNames.contains(clazz.name())) {
            return "_" + JsTypeMapper.sanitizeName(name);
        }
        return JsTypeMapper.sanitizeName(name);
    }

    JsIr.JsFunction lowerConstructor(IRClass clazz, IRMethod method) {
        MethodCtx ctx = new MethodCtx(p.lc, method, clazz);
        List<JsIr.JsStatement> body = p.parseMethodBody(ctx);
        insertFieldDefaults(clazz, body);
        insertSuperCall(clazz, body);
        // TIER 2.7 (D-VALUE-RECORD, 2.7.4 — ABI JS): um `value record` é um
        // objeto CONGELADO (o plano §4) — sem identidade observável e sem
        // mutação. Kof não muta record, então o freeze é invisível ao código
        // Kof e entrega a garantia de valor no nível do objeto JS.
        if (clazz.isValueRecord()) {
            body.add(new JsIr.JsExprStmt(new JsIr.JsCall(
                    new JsIr.JsIdentifier("Object.freeze"),
                    List.of(new JsIr.JsIdentifier("this")))));
        }
        return new JsIr.JsFunction("constructor", p.parameterNames(ctx), body, false, true, false, false,
                JsMethodParser.firstKofLine(method));
    }

    private JsIr.JsFunction lowerConstructorDispatch(IRClass clazz, List<IRMethod> ctors) {
        List<JsIr.JsStatement> body = new ArrayList<>();
        body.add(new JsIr.JsVarDecl("__kof_args", new JsIr.JsIdentifier("arguments"), false));
        int max = ctors.stream().mapToInt(c -> c.parameterTypes().size()).max().orElse(0);
        for (int i = 0; i < max; i++) {
            body.add(new JsIr.JsVarDecl("__kof_ctor_arg" + i,
                    new JsIr.JsIndex(new JsIr.JsIdentifier("__kof_args"), new JsIr.JsNumber(String.valueOf(i))),
                    false));
        }
        JsIr.JsExpression argsLength = new JsIr.JsMember(new JsIr.JsIdentifier("__kof_args"), "length");
        JsIr.JsExpression hasSignature = new JsIr.JsBinary(argsLength, ">",
                new JsIr.JsNumber(String.valueOf(max)));
        JsIr.JsExpression signatureArg = new JsIr.JsIndex(new JsIr.JsIdentifier("__kof_args"),
                new JsIr.JsNumber(String.valueOf(max)));
        JsIr.JsExpression signature = new JsIr.JsCall(new JsIr.JsIdentifier("String"),
                List.of(signatureArg));
        JsIr.JsExpression sigValue = new JsIr.JsConditional(hasSignature, signature,
                new JsIr.JsString(""));
        body.add(new JsIr.JsVarDecl("__kof_ctor_sig", sigValue, false));

        List<CtorBranch> branches = new ArrayList<>();
        for (int i = 0; i < ctors.size(); i++) {
            IRMethod ctor = ctors.get(i);
            IRMethod renamed = renameConstructorParameters(clazz, ctor, i);
            JsIr.JsFunction lowered = lowerConstructor(clazz, renamed);
            branches.add(new CtorBranch(ctorSigSuffix(ctor), lowered.parameters(), lowered.body()));
        }
        branches.sort((a, b) -> {
            int arity = Integer.compare(a.sig().split("_", -1).length, b.sig().split("_", -1).length);
            if (arity != 0) return -arity;
            return a.sig().compareTo(b.sig());
        });

        for (int i = 0; i < branches.size(); i++) {
            CtorBranch branch = branches.get(i);
            JsIr.JsExpression condition = new JsIr.JsBinary(new JsIr.JsIdentifier("__kof_ctor_sig"),
                    "===", new JsIr.JsString(branch.sig()));
            List<JsIr.JsStatement> branchBody = new ArrayList<>();
            List<String> names = branch.parameters();
            for (int j = 0; j < names.size(); j++) {
                branchBody.add(new JsIr.JsVarDecl(names.get(j),
                        new JsIr.JsIdentifier("__kof_ctor_arg" + j), false));
            }
            branchBody.addAll(branch.body());
            body.add(new JsIr.JsIf(condition, List.of(new JsIr.JsBlock(branchBody)), List.of()));
        }

        body.add(new JsIr.JsIf(new JsIr.JsNumber("1"), dispatchDefaultBranch(clazz), List.of()));
        body.add(new JsIr.JsReturn(new JsIr.JsThis()));
        return new JsIr.JsFunction("constructor", List.of("...__kof_ctor_rest"), body,
                false, true, false, false, ctors.isEmpty() ? null : JsMethodParser.firstKofLine(ctors.get(0)));
    }

    private List<JsIr.JsStatement> dispatchDefaultBranch(IRClass clazz) {
        List<JsIr.JsStatement> out = new ArrayList<>();
        out.add(new JsIr.JsThrow(new JsIr.JsCall(new JsIr.JsIdentifier("Error"),
                List.of(new JsIr.JsString("KofJS: no constructor of " + clazz.name()
                        + " matches the call signature")))));
        return out;
    }

    private IRMethod renameConstructorParameters(IRClass clazz, IRMethod method, int branchIndex) {
        MethodCtx ctx = new MethodCtx(p.lc, method, clazz);
        List<Integer> slots = p.parameterSlots(ctx);
        List<IRLocalVariable> locals = new ArrayList<>();
        for (IRLocalVariable lv : method.localVariables()) {
            String name = lv.name();
            if (!"this".equals(name) && slots.contains(lv.index())) {
                name = "__kof_ctor_p" + branchIndex + "_" + slots.indexOf(lv.index());
            }
            locals.add(new IRLocalVariable(lv.index(), name, lv.type()));
        }
        return new IRMethod(method.name(), method.returnType(), method.parameterTypes(),
                method.accessFlags(), method.thrownExceptions(), method.basicBlocks(), locals,
                method.debugInfo(), method.annotations(), method.parameterAnnotations());
    }

    private static String ctorSigSuffix(IRMethod method) {
        return TopLevelOverload.sigTag(method.parameterTypes()).replace('_', '$');
    }

    static List<JsIr.JsExpression> appendCtorToken(List<JsIr.JsExpression> args, List<Type> types) {
        List<JsIr.JsExpression> out = new ArrayList<>(args);
        out.add(new JsIr.JsString(TopLevelOverload.sigTag(types).replace('_', '$')));
        return out;
    }

    private record CtorBranch(String sig, List<String> parameters, List<JsIr.JsStatement> body) {}

    void insertSuperCall(IRClass clazz, List<JsIr.JsStatement> body) {
        if (clazz.superName() == null || "java/lang/Object".equals(clazz.superName())
                || "java/lang/Record".equals(clazz.superName())) {
            return;
        }
        boolean hasSuper = body.stream().anyMatch(stmt -> stmt instanceof JsIr.JsExprStmt es
                && es.expression() instanceof JsIr.JsCall call
                && call.callee() instanceof JsIr.JsIdentifier id && "super".equals(id.name()));
        if (!hasSuper) {
            body.add(0, new JsIr.JsExprStmt(new JsIr.JsCall(new JsIr.JsIdentifier("super"), List.of())));
        }
    }
    /**
     * JavaScript class fields are undefined until assigned; JVM instance fields
     * default to 0/false/null. Field defaults are emitted at the start of every
     * constructor (after the super call) to preserve Kof/JVM semantics.
     */
    void insertFieldDefaults(IRClass clazz, List<JsIr.JsStatement> body) {
        List<JsIr.JsStatement> defaults = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            if ((field.accessFlags() & AccessFlags.STATIC) != 0) continue;
            JsIr.JsExpression value = field.initialValue() != null
                    ? p.ops.literalExpr(new KofLoadLiteral(field.type(), field.initialValue()))
                    : JsTypeMapper.defaultForType(field.type());
            defaults.add(new JsIr.JsExprStmt(new JsIr.JsBinary(
                    new JsIr.JsMember(new JsIr.JsThis(), jsFieldName(clazz, field.name())), "=", value)));
        }
        if (defaults.isEmpty()) return;
        int insertAt = 0;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof JsIr.JsExprStmt es
                    && es.expression() instanceof JsIr.JsCall call
                    && call.callee() instanceof JsIr.JsIdentifier id && "super".equals(id.name())) {
                insertAt = i + 1;
                break;
            }
        }
        body.addAll(insertAt, defaults);
    }
}
