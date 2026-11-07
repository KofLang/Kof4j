package dev.kof.compiler.jvm;

/**
 * Decoders de mapa JSON (§103.1, GitHub #103) — mesmo KofRuntime gerado;
 * split de JvmRuntimeJson p/ regra ≤500 (concatenação preserva byte-a-byte).
 */
public final class JvmRuntimeJsonMap {

    private JvmRuntimeJsonMap() {}

    static String source() {
        return """
                // §103.1 (#103): decode<Map<String,T>> — o parser
                // devolve LinkedHashMap; o lowerer espera java.util.Map
                // (HashMap quebra o checkcast quando a IR guarda Lkof/Map;).
                public static java.util.Map<Object, Object> kof_json_decode_map(String json) {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof Map<?, ?> m)) throw jsonShapeError("an object", parsed);
                    java.util.Map<Object, Object> result = new LinkedHashMap<>();
                    result.putAll(m);
                    return result;
                }

                // decode<Map<String,Classe>>: cada VALOR é bindado à classe
                // (mesmo kof_json_bind do object_list). Espelha o caminho de
                // lista; só muda o container.
                public static java.util.Map<Object, Object> kof_json_decode_object_map(String json, String cn)
                        throws Exception {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof Map<?, ?> m)) throw jsonShapeError("an object", parsed);
                    Class<?> type = Class.forName(cn);
                    java.util.Map<Object, Object> result = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet())
                        result.put(e.getKey(), kof_json_bind(type, e.getValue()));
                    return result;
                }

                // §811: a JSON list bound to an ARRAY slot (a `Byte[]` record
                // component) is built element by element at the component type;
                // the raw List reached the record constructor ("argument type
                // mismatch").
                private static Object kof_json_bind_array(Class<?> type, Object value) throws Exception {
                    if (!(value instanceof List<?> l)) return value;
                    Class<?> ct = type.getComponentType();
                    Object out = java.lang.reflect.Array.newInstance(ct, l.size());
                    for (int i = 0; i < l.size(); i++)
                        java.lang.reflect.Array.set(out, i, kof_json_bind(ct, ct, l.get(i)));
                    return out;
                }

        """;
    }
}
