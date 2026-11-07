package dev.kof.compiler.jvm;

/**
 * Decoders de ARRAY/LISTA tipada (split de JvmRuntimeJson — o #1103 empurrou
 * a classe pela linha vermelha 600) + o helper JSN006 (#1103, decisão da
 * mantenedora 05/11): shape errada no decode é ERRO NOMEADO, nunca container
 * vazio silencioso. Mesmo KofRuntime gerado; concatenação byte-a-byte.
 */
public final class JvmRuntimeJsonArrays {

    private JvmRuntimeJsonArrays() {}

    static String source() {
        return """
                // #1103 (JSN006, decisão da mantenedora 05/11): shape errada
                // no decode é ERRO NOMEADO, nunca container vazio silencioso.
                public static RuntimeException jsonShapeError(String expected, Object parsed) {
                    String got = parsed instanceof Map<?, ?> ? "an object"
                            : parsed instanceof List<?> ? "an array"
                            : parsed instanceof String ? "a string"
                            : parsed instanceof Boolean ? "a bool"
                            : parsed instanceof Number ? "a number"
                            : parsed == null ? "null" : parsed.getClass().getSimpleName();
                    return new RuntimeException("JSN006: json.decode expected " + expected
                            + " but the payload is " + got);
                }

                public static int[] kof_json_decode_int_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof List<?> l)) throw jsonShapeError("an array", parsed);
                    int[] out = new int[l.size()];
                    for (int i = 0; i < l.size(); i++) out[i] = ((Number) l.get(i)).intValue();
                    return out;
                }

                public static long[] kof_json_decode_long_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof List<?> l)) throw jsonShapeError("an array", parsed);
                    long[] out = new long[l.size()];
                    for (int i = 0; i < l.size(); i++) out[i] = ((Number) l.get(i)).longValue();
                    return out;
                }

                // #132: Bool[] no JVM é boolean[] real (BALOAD/BASTORE batem em
                // `new Bool[n]` E em `json.decode<Bool[]>`).
                public static boolean[] kof_json_decode_bool_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof List<?> l)) throw jsonShapeError("an array", parsed);
                    boolean[] out = new boolean[l.size()];
                    for (int i = 0; i < l.size(); i++) out[i] = (Boolean) l.get(i);
                    return out;
                }

                public static double[] kof_json_decode_double_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof List<?> l)) throw jsonShapeError("an array", parsed);
                    double[] out = new double[l.size()];
                    for (int i = 0; i < l.size(); i++) out[i] = ((Number) l.get(i)).doubleValue();
                    return out;
                }

                public static String[] kof_json_decode_string_array(String json) {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof List<?> l)) throw jsonShapeError("an array", parsed);
                    String[] out = new String[l.size()];
                    for (int i = 0; i < l.size(); i++) out[i] = String.valueOf(l.get(i));
                    return out;
                }

                public static ArrayList<Integer> kof_json_decode_int_list(String json) {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof List<?> l)) throw jsonShapeError("an array", parsed);
                    ArrayList<Integer> result = new ArrayList<>();
                    for (Object e : l) result.add(((Number) e).intValue());
                    return result;
                }

                public static ArrayList<String> kof_json_decode_string_list(String json) {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof List<?> l)) throw jsonShapeError("an array", parsed);
                    ArrayList<String> result = new ArrayList<>();
                    for (Object e : l) result.add(e == null ? null : String.valueOf(e));
                    return result;
                }

                public static ArrayList<Object> kof_json_decode_list(String json) {
                    Object parsed = kof_json_parse(json);
                    if (parsed instanceof ArrayList<?> l) return new ArrayList<Object>(l);
                    throw jsonShapeError("an array", parsed);
                }

                public static ArrayList<Object> kof_json_decode_object_list(String json, String cn)
                        throws Exception {
                    Object parsed = kof_json_parse(json);
                    if (!(parsed instanceof List<?> l)) throw jsonShapeError("an array", parsed);
                    ArrayList<Object> result = new ArrayList<>();
                    Class<?> type = Class.forName(cn);
                    for (Object e : l) result.add(kof_json_bind(type, e));
                    return result;
                }

        """;
    }
}
