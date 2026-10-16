package dev.kof.compiler.js;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.Type;

import java.util.List;

/**
 * JsJsonOps — lowering das ops `kof_json_*` (encode/decode) para os helpers JS
 * (REFACTOR-500 FASE 4). Extraído de {@code JsRuntimeOps} por responsabilidade
 * (§659d empurrou o arquivo além do teto de 600 linhas); a semântica é verbatim.
 */
public final class JsJsonOps {

    private final JsMethodParser p;

    JsJsonOps(JsMethodParser p) {
        this.p = p;
    }

    /**
     * Trata uma op {@code kof_json_*}. Devolve {@code true} quando consumiu a op
     * (o chamador não deve seguir com o dispatch genérico).
     */
    boolean handle(List<Object> stack, KofCall kc,
                   JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String name = kc.methodName();
        if (!name.startsWith("kof_json_")) return false;
        // JSON encode/decode maps directly to JSON.stringify/parse; the
        // type information stays in the Kof compiler (generics erasure).
        JsIr.JsExpression value = kc.kind() == KofCallKind.FUNCTION
                ? args.get(0) : receiver;
        if (name.equals("kof_json_encode_map")) {
            // §106 residual (13/09): Map no JS é `new Map()` — JSON.stringify
            // devolve '{}' (sem own enumerable props). Roteia p/ o helper
            // que monta o objeto com chaves SORTED (decisão 2b), igual ao
            // JVM/nativo/interp. args = (map, tag).
            p.lc.registerRuntime("kofJsonEncodeMap");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonEncodeMap"),
                    List.of(value, args.get(1))));
        } else if (name.equals("kof_json_encode_list") && args.size() > 1
                && isDoubleTag(args.get(1))) {
            // §659d (List<Double>): o tag 3 (JsonDispatch.listTag) marca o
            // elemento Double — o mesmo `number` do JS imprime `1` e não
            // `1.0`. args = (list, tag).
            p.lc.registerRuntime("kofJsonEncodeDoubleList");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonEncodeDoubleList"),
                    List.of(value)));
        } else if (name.equals("kof_json_encode_double") || name.equals("kof_json_encode_float")) {
            // §659d: um Double/Float do Kof é um `number` do JS, e o
            // JSON.stringify imprime inteiro como `1` (não `1.0`) e o
            // científico como `1e+21` (não `1.0E21`) — o contrato Kof usa o
            // Double.toString do JDK nos outros três alvos. O tipo estático
            // veio no NOME do KofCall (JsonDispatch), então a face ESCALAR
            // despacha pelo nome.
            String helper = name.equals("kof_json_encode_float")
                    ? "kofJsonEncodeFloat" : "kofJsonEncodeDouble";
            p.lc.registerRuntime(helper);
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(helper), List.of(value)));
        } else if (name.equals("kof_json_encode")
                && kc.ownerType() instanceof Type.ClassType oct && isFpRecord(oct)) {
            // §659d (record com componente Double/Float): o toJSON sintético
            // fica CRU (a ponte ORM do JS o usa como wire do banco — um
            // marcador corromperia o valor); o encode JSON seleciona a
            // variante marcada `__kof_tojson_fp`, que o kofJsonEncode
            // desmarca depois do stringify.
            p.lc.registerRuntime("kofJsonEncode");
            JsIr.JsExpression marked = new JsIr.JsCall(
                    new JsIr.JsMember(value, "__kof_tojson_fp"), List.of());
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonEncode"), List.of(marked)));
        } else if (name.contains("encode")) {
            // §659b: kofJsonEncode (não JSON.stringify direto) — um Long do
            // Kof é BigInt no JS e o JSON.stringify o recusa; kofJsonEncode
            // serializa BigInt como o inteiro decimal (paridade JVM/nativo)
            // e mantém o caminho sem-Long byte-idêntico.
            p.lc.registerRuntime("kofJsonEncode");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonEncode"), List.of(value)));
        } else if (name.startsWith("kof_json_decode_")
                && BuiltinTypes.isList(kc.ownerType())) {
            // decode<List<T>> — bind each element to the Kof class
            Type elem = kc.ownerType() instanceof Type.ClassType lct
                    && !lct.typeArguments().isEmpty() ? lct.typeArguments().get(0) : Type.UnknownType.UNKNOWN;
            if (elem instanceof Type.ClassType ect
                    && p.lc.classMethodNames.containsKey(ect.internalName())) {
                String jsName = JsTypeMapper.jsClassName(ect.internalName());
                p.lc.decodeHelpers.add(jsName);
                JsIr.JsExpression parsed = new JsIr.JsCall(
                        new JsIr.JsIdentifier("JSON.parse"), List.of(value));
                JsIr.JsExpression mapper = new JsIr.JsCall(
                        new JsIr.JsIdentifier("__kof_decode_" + jsName),
                        List.of(new JsIr.JsIdentifier("o")));
                stack.add(new JsIr.JsCall(
                        new JsIr.JsMember(parsed, "map"),
                        List.of(new JsIr.JsArrow(List.of("o"), mapper))));
            } else {
                JsIr.JsExpression raw = new JsIr.JsCall(new JsIr.JsIdentifier("JSON.parse"), List.of(value));
                if (BuiltinTypes.isObject(elem) || BuiltinTypes.isMap(elem) || BuiltinTypes.isList(elem)) {
                    p.lc.registerRuntime("kofJsonDeep");
                    raw = new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonDeep"), List.of(raw));
                }
                stack.add(raw);
            }
        } else if (name.startsWith("kof_json_decode_")
                && BuiltinTypes.isMap(kc.ownerType())) {
            // §103.1 (#103): decode<Map<String,T>> — JSON.parse do
            // objeto + monta um Map real (o else dava objeto puro, que
            // não responde m.get/m.size). Valor classe → bind via
            // __kof_decode_<Classe> (mesmo helper da célula de lista).
            Type mv2 = BuiltinTypes.mapValue(kc.ownerType());
            JsIr.JsExpression parsed = new JsIr.JsCall(
                    new JsIr.JsIdentifier("JSON.parse"), List.of(value));
            if (mv2 instanceof Type.ClassType mct
                    && p.lc.classMethodNames.containsKey(mct.internalName())) {
                String jsName = JsTypeMapper.jsClassName(mct.internalName());
                p.lc.decodeHelpers.add(jsName);
                p.lc.registerRuntime("kofJsonDecodeObjectMap");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonDecodeObjectMap"),
                        List.of(parsed, new JsIr.JsIdentifier("__kof_decode_" + jsName))));
            } else {
                p.lc.registerRuntime("kofJsonDecodeMap");
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofJsonDecodeMap"),
                        List.of(parsed)));
            }
        } else if (name.startsWith("kof_json_decode_")
                && p.lc.classMethodNames.containsKey(JsTypeMapper.ownerInternalName(kc.ownerType()))) {
            // decode<Class> — bind the parsed object to the Kof class
            String jsName = JsTypeMapper.jsClassName(JsTypeMapper.ownerInternalName(kc.ownerType()));
            p.lc.decodeHelpers.add(jsName);
            stack.add(new JsIr.JsCall(
                    new JsIr.JsIdentifier("__kof_decode_" + jsName), List.of(value)));
        } else {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("JSON.parse"), List.of(value)));
        }
        return true;
    }

    /** §659d: `JsonDispatch.listTag` 3 = `List<Double>`. The tag reaches the
     *  lowering as a literal second argument of `kof_json_encode_list`, so the
     *  Double-element list can be routed to the JDK-format encoder. */
    private static boolean isDoubleTag(JsIr.JsExpression tag) {
        return tag instanceof JsIr.JsNumber num && "3".equals(num.text());
    }

    /** §659d: true when the record carries the marked `__kof_tojson_fp` variant
     *  (a `Double`/`Float` component) — registered by `JsBackend` from the same
     *  predicate `JsClassEmitter` uses to emit the method. */
    private boolean isFpRecord(Type.ClassType ct) {
        return p.lc.fpRecordClassNames.contains(ct.internalName())
                || p.lc.fpRecordClassNames.contains(ct.name());
    }
}
