package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * JsClassEmitter — lowering de IRClass para JsIr.JsClass: campos, métodos
 * (via JsMethodParser), sintéticos de record (toString/equals/toJSON) e o
 * helper de binding do json.decode (REFACTOR-500 FASE 4).
 */
final class JsClassEmitter {

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
        IRMethod canonicalCtor = null;
        for (IRMethod method : clazz.methods()) {
            if ("<init>".equals(method.name())
                    && (canonicalCtor == null
                    || method.parameterTypes().size() > canonicalCtor.parameterTypes().size())) {
                canonicalCtor = method;
            }
        }
        for (IRMethod method : clazz.methods()) {
            if ("<init>".equals(method.name())) {
                // A JS class can only have one constructor: the canonical
                // (max-arity) one is emitted. Default-parameter wrapper
                // constructors only exist for the JVM/Native backends.
                if (method == canonicalCtor) {
                    methods.add(lowerConstructor(clazz, method));
                }
            } else {
                boolean isStatic = (method.accessFlags() & AccessFlags.STATIC) != 0;
                methods.add(p.lowerFunction(method, clazz, isStatic));
            }
        }
        if (isRecord) {
            methods.add(lowerRecordToString(clazz));
            methods.add(lowerRecordToJson(clazz));
            methods.add(lowerRecordEquals(clazz));
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
        List<JsIr.JsExpression> conds = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            String backing = "_" + JsTypeMapper.sanitizeName(field.name());
            conds.add(new JsIr.JsBinary(
                    new JsIr.JsMember(new JsIr.JsThis(), backing),
                    "===",
                    new JsIr.JsMember(new JsIr.JsIdentifier("other"), backing)));
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
            ctorArgs.add(new JsIr.JsMember(new JsIr.JsIdentifier("p"), JsTypeMapper.sanitizeName(field.name())));
        }
        JsIr.JsExpression instance = new JsIr.JsNew(new JsIr.JsIdentifier(jsName), ctorArgs);
        if (isRecord) {
            body.add(new JsIr.JsVarDecl("o", instance, true));
        } else {
            body.add(new JsIr.JsVarDecl("o", new JsIr.JsNew(new JsIr.JsIdentifier(jsName), List.of()), true));
            for (IRField field : clazz.fields()) {
                body.add(new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(new JsIr.JsIdentifier("o"), JsTypeMapper.sanitizeName(field.name())), "=",
                        new JsIr.JsMember(new JsIr.JsIdentifier("p"), JsTypeMapper.sanitizeName(field.name())))));
            }
        }
        body.add(new JsIr.JsReturn(new JsIr.JsIdentifier("o")));
        return new JsIr.JsFunction("__kof_decode_" + jsName, List.of("json"), body, false, false, true);
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
                    ? p.calls.literalExpr(new KofLoadLiteral(field.type(), field.initialValue()))
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
