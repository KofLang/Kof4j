package dev.kof.compiler.js;

import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRField;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * JsRecordSynthetics — os métodos sintéticos de RECORD do backend JS
 * (toString/equals/hashCode/toJSON/__kof_tojson_fp/FFI fields+from). Extraído
 * de {@code JsClassEmitter} por responsabilidade (§659d empurrou o arquivo além
 * do teto de 500/600 do REFACTOR-500); a semântica é verbatim.
 */
final class JsRecordSynthetics {

    private final JsMethodParser p;

    JsRecordSynthetics(JsMethodParser p) {
        this.p = p;
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
        // §746: o valor de cada campo passa pelo kofFormat — a coercao nativa
        // do `+` renderizava uma List como `a,b` (sem os [ ]) e um Map como
        // `[object Map]`, divergindo do toString do JVM/Script/nativo (medido).
        p.lc.registerRuntime("kofFormat");
        for (int i = 0; i < clazz.fields().size(); i++) {
            if (i > 0) parts.add(new JsIr.JsString(", "));
            IRField field = clazz.fields().get(i);
            parts.add(new JsIr.JsString(field.name() + "="));
            JsIr.JsExpression value = new JsIr.JsMember(new JsIr.JsThis(),
                    "_" + JsTypeMapper.sanitizeName(field.name()));
            Type ft = field.type();
            Type num = ft instanceof Type.NullableType nt ? nt.inner() : ft;
            if (Type.PrimitiveType.DOUBLE.equals(num) || Type.PrimitiveType.FLOAT.equals(num)) {
                p.lc.registerRuntime("kofNumFmt");
                parts.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofNumFmt"), List.of(value,
                        new JsIr.JsNumber(Type.PrimitiveType.FLOAT.equals(num) ? "1" : "0"))));
            } else if (Type.PrimitiveType.CHAR.equals(ft)) {
                parts.add(new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("String"), "fromCharCode"),
                        List.of(value)));
            } else {
                parts.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofFormat"), List.of(value)));
            }
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
     *
     * <p>Stays RAW on purpose: this is ALSO the wire form the JS ORM bridge
     * hands to the database ({@code kofOrmSave(id, obj.toJSON(), …)}), so it must
     * never carry a marker. The {@code json.encode} face uses
     * {@link #lowerRecordToJsonFp} instead.
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
     * §659d: {@code __kof_tojson_fp} — the same shape as {@link #lowerRecordToJson}
     * but with each {@code Double}/{@code Float} component wrapped in
     * {@code kofJsonFp}. A Kof {@code Double}/{@code Float} is a JS {@code number}
     * and {@code JSON.stringify} prints a whole one as `1` where the other three
     * targets print the JDK form `1.0`; the marker survives stringify and
     * {@code kofJsonEncode} replaces it with the raw JDK form afterwards. Only
     * records that actually HAVE such a component get the method.
     */
    JsIr.JsFunction lowerRecordToJsonFp(IRClass clazz) {
        p.lc.registerRuntime("kofJsonFp");
        List<JsIr.JsObjectEntry> entries = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            JsIr.JsExpression value = new JsIr.JsMember(new JsIr.JsThis(),
                    "_" + JsTypeMapper.sanitizeName(field.name()));
            String fpKind = fpFieldKind(field);
            if (fpKind != null) {
                entries.add(new JsIr.JsObjectEntry(field.name(),
                        new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonFp"),
                                List.of(value, new JsIr.JsNumber(fpKind.equals("float") ? "1" : "0")))));
            } else {
                entries.add(new JsIr.JsObjectEntry(field.name(), value));
            }
        }
        return new JsIr.JsFunction("__kof_tojson_fp", List.of(),
                List.of(new JsIr.JsReturn(new JsIr.JsObjectLiteral(entries))), false, false, false);
    }

    /** "float", "double" or null — the JDK-format JSON kind of a component. */
    private static String fpFieldKind(IRField field) {
        Type t = field.type();
        if (t instanceof Type.NullableType nt) t = nt.inner();
        if (!(t instanceof Type.PrimitiveType pt)) return null;
        String n = Type.canonicalPrimitiveName(pt.name());
        return n.equals("double") || n.equals("float") ? n : null;
    }

    /** True when the record has at least one {@code Double}/{@code Float}
     *  component (the only case that needs {@code __kof_tojson_fp}). */
    static boolean hasFpField(IRClass clazz) {
        for (IRField f : clazz.fields()) if (fpFieldKind(f) != null) return true;
        return false;
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
                                ? JsClassEmitter.appendCtorToken(args, canonical.parameterTypes())
                                : args))),
                true, false, false);
    }
}
