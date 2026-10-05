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
        boolean isRecord = "java/lang/Record".equals(clazz.superName());
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
                boolean isStatic = (method.accessFlags() & AccessFlags.STATIC) != 0;
                methods.add(p.lowerFunction(method, clazz, isStatic));
            }
        }
        if (isRecord) {
            methods.add(lowerRecordToString(clazz));
            methods.add(lowerRecordToJson(clazz));
            methods.add(lowerRecordFfiFields(clazz));
            methods.add(lowerRecordFfiFrom(clazz, jsName));
            methods.add(lowerRecordEquals(clazz));
            methods.add(lowerRecordHashCode(clazz));
        }
        return new JsIr.JsClass(jsName, jsSuper, fields, methods);
    }

    /**
     * Records get a toString() in JS to mirror the JVM backend's synthetic
     * record toString: "Name[f1=..., f2=...]".
     */
    JsIr.JsFunction lowerRecordToString(IRClass clazz) {
        String simpleName = clazz.name().contains("/")
                ? clazz.name().substring(clazz.name().lastIndexOf('/') + 1) : clazz.name();
        List<JsIr.JsExpression> parts = new ArrayList<>();
        parts.add(new JsIr.JsString(simpleName + "["));
        for (int i = 0; i < clazz.fields().size(); i++) {
            if (i > 0) parts.add(new JsIr.JsString(", "));
            parts.add(new JsIr.JsString(clazz.fields().get(i).name() + "="));
            parts.add(new JsIr.JsMember(new JsIr.JsThis(),
                    "_" + JsTypeMapper.sanitizeName(clazz.fields().get(i).name())));
        }
        parts.add(new JsIr.JsString("]"));
        JsIr.JsExpression joined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            joined = new JsIr.JsBinary(joined, "+", parts.get(i));
        }
        return new JsIr.JsFunction("toString", List.of(),
                List.of(new JsIr.JsReturn(joined)), false, false, false);
    }

    /**
     * Records: igualdade de conteúdo no JS (bug 11) — compara todos os
     * componentes. O lowering de `==` em records despacha para `.equals()`
     * em todos os targets.
     */
    JsIr.JsFunction lowerRecordEquals(IRClass clazz) {
        // Componente objeto (record/classe com equals) compara por conteúdo,
        // como o Objects.equals da JVM — `===` só serve para primitivos.
        p.lc.registerRuntime("kofValEq");
        List<JsIr.JsExpression> conds = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            String backing = "_" + JsTypeMapper.sanitizeName(field.name());
            conds.add(new JsIr.JsCall(
                    new JsIr.JsIdentifier("kofValEq"),
                    List.of(new JsIr.JsMember(new JsIr.JsThis(), backing),
                            new JsIr.JsMember(new JsIr.JsIdentifier("other"), backing))));
        }
        JsIr.JsExpression body = null;
        for (int i = conds.size() - 1; i >= 0; i--) {
            body = (body == null) ? conds.get(i)
                    : new JsIr.JsBinary(conds.get(i), "&&", body);
        }
        if (body == null) body = new JsIr.JsNumber("1");
        // Kof bool é int (0/1): o equals gerado devolve 1/0 para operações
        // subsequentes (ex.: `a != c` compara com 0) não quebrarem.
        JsIr.JsExpression kofBool = new JsIr.JsConditional(
                body, new JsIr.JsNumber("1"), new JsIr.JsNumber("0"));
        return new JsIr.JsFunction("equals", List.of("other"),
                List.of(new JsIr.JsReturn(kofBool)), false, false, false);
    }

    /**
     * Records: hashCode() no JS (bug 42) espelhando o JvmRecordEmitter
     * (result = 31*result + contrib, wrap int32 via | 0). O contrib usa
     * kofHashCode (runtime) que trata number/String/record — campos
     * numéricos dão o valor, refs delegam ao runtime.
     */
    JsIr.JsFunction lowerRecordHashCode(IRClass clazz) {
        p.lc.registerRuntime("kofHashCode");
        List<JsIr.JsStatement> body = new ArrayList<>();
        body.add(new JsIr.JsVarDecl("__h", new JsIr.JsNumber("1"), false));
        for (IRField field : clazz.fields()) {
            String backing = "_" + JsTypeMapper.sanitizeName(field.name());
            JsIr.JsExpression contrib = new JsIr.JsCall(
                    new JsIr.JsIdentifier("kofHashCode"),
                    List.of(new JsIr.JsMember(new JsIr.JsThis(), backing)));
            JsIr.JsExpression next = new JsIr.JsBinary(
                    new JsIr.JsBinary(
                            new JsIr.JsBinary(new JsIr.JsNumber("31"), "*",
                                    new JsIr.JsIdentifier("__h")),
                            "+", contrib),
                    "|", new JsIr.JsNumber("0"));
            body.add(new JsIr.JsAssign("__h", next));
        }
        body.add(new JsIr.JsReturn(new JsIr.JsIdentifier("__h")));
        return new JsIr.JsFunction("hashCode", List.of(), body, false, false, false);
    }

    /**
     * Records serialize as { "f1": ..., "f2": ... } to mirror the JVM backend's
     * reflection-based JSON encoding (JSON.stringify honors toJSON()).
     */
    JsIr.JsFunction lowerRecordToJson(IRClass clazz) {
        List<JsIr.JsObjectEntry> entries = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            entries.add(new JsIr.JsObjectEntry(field.name(),
                    new JsIr.JsMember(new JsIr.JsThis(), "_" + JsTypeMapper.sanitizeName(field.name()))));
        }
        return new JsIr.JsFunction("toJSON", List.of(),
                List.of(new JsIr.JsReturn(new JsIr.JsObjectLiteral(entries))), false, false, false);
    }

    /**
     * D6-1/3.8b (bridge JS): o host não reflete {@code RecordComponent} num objeto
     * GraalJS, então o record expõe os campos na ordem de declaração para o
     * {@code KofJsFfiMarshal} empacotar o struct C por valor — paridade com o
     * {@code kof_ffi_write_struct} reflexivo do JVM. Só records são bindáveis
     * como struct (o gate de {@code isExternBound} fecha o resto).
     */
    JsIr.JsFunction lowerRecordFfiFields(IRClass clazz) {
        List<JsIr.JsExpression> values = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            values.add(new JsIr.JsMember(new JsIr.JsThis(),
                    "_" + JsTypeMapper.sanitizeName(field.name())));
        }
        return new JsIr.JsFunction("__kof_ffi_fields", List.of(),
                List.of(new JsIr.JsReturn(new JsIr.JsArrayLiteral(values))), false, false, false);
    }

    /**
     * D6-1/3.8b (bridge JS, retorno 21/09): o host devolve os campos do struct
     * (por valor) como um array na ordem de declaração — o host não instancia um
     * objeto GraalJS. Este factory ESTÁTICO reconstrói o record pelo construtor
     * canônico, paridade com o {@code kof_ffi_read_struct} reflexivo do JVM. Um
     * campo `Long` vira `BigInt` (o JS representa Long como BigInt).
     */
    JsIr.JsFunction lowerRecordFfiFrom(IRClass clazz, String jsName) {
        List<IRMethod> ctors = new ArrayList<>();
        for (IRMethod method : clazz.methods()) {
            if ("<init>".equals(method.name())) ctors.add(method);
        }
        if (ctors.size() > 1 && "java/lang/Record".equals(clazz.superName())) {
            // A record's FFI layout is the declared component order, which is
            // its canonical constructor — dispatching by `fields.length` would
            // let a 1-component record call a 2-component overload.
            IRMethod canonical = ctors.stream()
                    .filter(m -> m.parameterTypes().size() == clazz.fields().size())
                    .findFirst()
                    .orElseGet(() -> ctors.stream()
                            .max((a, b) -> Integer.compare(a.parameterTypes().size(), b.parameterTypes().size()))
                            .orElseThrow());
            return lowerRecordFfiFrom(clazz, jsName, canonical);
        }
        IRMethod canonical = ctors.isEmpty() ? null : ctors.get(0);
        if (canonical == null) {
            return new JsIr.JsFunction("__kof_ffi_from", List.of("fields"),
                    List.of(new JsIr.JsReturn(new JsIr.JsNew(new JsIr.JsIdentifier(jsName), List.of()))),
                    true, false, false);
        }
        return lowerRecordFfiFrom(clazz, jsName, canonical);
    }

    private JsIr.JsFunction lowerRecordFfiFrom(IRClass clazz, String jsName, IRMethod canonical) {
        List<JsIr.JsExpression> args = new ArrayList<>();
        int idx = 0;
        for (IRField field : clazz.fields()) {
            JsIr.JsExpression elem = new JsIr.JsIndex(
                    new JsIr.JsIdentifier("fields"), new JsIr.JsNumber(String.valueOf(idx)));
            // Os valores vêm do host como Java boxed (foreign GraalJS): coage ao
            // primitivo JS do tipo do campo — Long→BigInt, bool→Boolean, o resto
            // Number — p/ o record ficar com os mesmos tipos do caminho puro JS.
            if (field.type() instanceof Type.PrimitiveType pt) {
                String prim = Type.canonicalPrimitiveName(pt.name());
                if ("long".equals(prim)) {
                    elem = new JsIr.JsCall(new JsIr.JsIdentifier("BigInt"), List.of(elem));
                } else if ("bool".equals(prim)) {
                    elem = new JsIr.JsCall(new JsIr.JsIdentifier("Boolean"), List.of(elem));
                } else {
                    elem = new JsIr.JsCall(new JsIr.JsIdentifier("Number"), List.of(elem));
                }
            }
            args.add(elem);
            idx++;
        }
        return new JsIr.JsFunction("__kof_ffi_from", List.of("fields"),
                List.of(new JsIr.JsReturn(new JsIr.JsNew(new JsIr.JsIdentifier(jsName),
                        p.lc.ctorDispatch.contains(clazz.name())
                                ? appendCtorToken(args, canonical.parameterTypes())
                                : args))),
                true, false, false);
    }

    /**
     * json.decode&lt;Class&gt; binds the parsed object to the Kof class:
     * records use their canonical constructor; classes get a default instance
     * with fields assigned by name (mirroring the JVM reflection binding).
     */
    JsIr.JsFunction lowerDecodeHelper(IRClass clazz) {
        String jsName = JsTypeMapper.jsClassName(clazz.name());
        boolean isRecord = "java/lang/Record".equals(clazz.superName());
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
        if ("java/lang/Record".equals(clazz.superName())) {
            return "_" + JsTypeMapper.sanitizeName(name);
        }
        return JsTypeMapper.sanitizeName(name);
    }

    JsIr.JsFunction lowerConstructor(IRClass clazz, IRMethod method) {
        MethodCtx ctx = new MethodCtx(p.lc, method, clazz);
        List<JsIr.JsStatement> body = p.parseMethodBody(ctx);
        insertFieldDefaults(clazz, body);
        insertSuperCall(clazz, body);
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

        body.add(new JsIr.JsIf(new JsIr.JsNumber("1"), dispatchDefaultBranch(clazz, ctors), List.of()));
        body.add(new JsIr.JsReturn(new JsIr.JsThis()));
        return new JsIr.JsFunction("constructor", List.of("...__kof_ctor_rest"), body,
                false, true, false, false, ctors.isEmpty() ? null : JsMethodParser.firstKofLine(ctors.get(0)));
    }

    private List<JsIr.JsStatement> dispatchDefaultBranch(IRClass clazz, List<IRMethod> ctors) {
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

    private static List<JsIr.JsExpression> appendCtorToken(List<JsIr.JsExpression> args, List<Type> types) {
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
