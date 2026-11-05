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
                    return new LinkedHashMap<Object, Object>(kof_json_object(json));
                }

                // decode<Map<String,Classe>>: cada VALOR é bindado à classe
                // (mesmo kof_json_bind do object_list). Espelha o caminho de
                // lista; só muda o container.
                public static java.util.Map<Object, Object> kof_json_decode_object_map(String json, String cn)
                        throws Exception {
                    Map<?, ?> m = kof_json_object(json);
                    java.util.Map<Object, Object> result = new LinkedHashMap<>();
                    Class<?> type = Class.forName(cn);
                    for (Map.Entry<?, ?> e : m.entrySet())
                        result.put(e.getKey(), kof_json_bind(type, e.getValue()));
                    return result;
                }

                // §819: a JSON value of the wrong shape for the requested
                // container (an array for a Map, an object for a List) used to
                // decode as an EMPTY container, in silence. It is JSN006 now.
                public static List<?> kof_json_array(String json) {
                    Object v = kof_json_parse(json);
                    if (v instanceof List<?> l) return l;
                    throw kof_json_shape("an array", v);
                }

                public static Map<?, ?> kof_json_object(String json) {
                    Object v = kof_json_parse(json);
                    if (v instanceof Map<?, ?> m) return m;
                    throw kof_json_shape("an object", v);
                }

                static RuntimeException kof_json_shape(String want, Object v) {
                    String found = v == null ? "null" : v instanceof List<?> ? "an array"
                            : v instanceof Map<?, ?> ? "an object" : v instanceof String ? "a string"
                            : v instanceof Boolean ? "a boolean" : "a number";
                    return new RuntimeException("JSN006: json.decode expected " + want + ", found " + found);
                }

        """;
    }
}
