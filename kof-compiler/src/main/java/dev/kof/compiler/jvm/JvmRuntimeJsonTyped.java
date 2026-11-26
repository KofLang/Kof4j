package dev.kof.compiler.jvm;

/**
 * #633: fragmento do source de {@code KofRuntime} com o decode guiado por
 * assinatura genérica (chamado pelo lowerer para {@code json.decode<Map<K,
 * coleção>>}). Extraído de {@link JvmRuntimeJson} para manter a fatia sob o teto
 * de 600 linhas (regra ≤500/600); a concatenação preserva o conteúdo.
 */
public final class JvmRuntimeJsonTyped {

    private JvmRuntimeJsonTyped() {}

    static String source() {
        return """
                // #633: decode de um tipo cuja FORMA só o compilador conhece
                // (ex.: `Map<String, Map<String, E>>`) — a assinatura genérica
                // (JVM Signature, igual à de toGenericSignature) é parseada
                // para um java.lang.reflect.Type e o binder recursivo
                // (kof_json_bind) fala o resto. Antes, esse caso caía em
                // kof_json_decode_map (passthrough cru) e perdia os valores
                // internos em SILÊNCIO (ClassCastException no primeiro uso).
                // #1177: o retorno vira Object — o decode_typed agora também
                // serve a RECORDS instanciados (Pagina<Item>), cujo valor é o
                // record, não um Map. O chamador faz o CHECKCAST (o emit).
                public static Object kof_json_decode_typed(String json, String signature)
                        throws Exception {
                    int[] pos = new int[] { 0 };
                    java.lang.reflect.Type t = parseSignature(signature, pos);
                    Class<?> raw = t instanceof Class<?> c ? c
                            : (Class<?>) ((java.lang.reflect.ParameterizedType) t).getRawType();
                    return kof_json_bind(raw, t, kof_json_parse(json));
                }

                /** Parser do subconjunto de JVM Signature que o compilador
                 *  emite para tipos decodificáveis (classe, primitivo, e
                 *  genéricos aninhados de Map/List). */
                private static java.lang.reflect.Type parseSignature(String s, int[] pos) throws Exception {
                    char c = s.charAt(pos[0]++);
                    if (c == 'L') {
                        int start = pos[0];
                        while (pos[0] < s.length() && s.charAt(pos[0]) != '<' && s.charAt(pos[0]) != ';') pos[0]++;
                        Class<?> raw = Class.forName(s.substring(start, pos[0]).replace('/', '.'));
                        if (s.charAt(pos[0]) == '<') {
                            pos[0]++;
                            ArrayList<java.lang.reflect.Type> args = new ArrayList<>();
                            while (s.charAt(pos[0]) != '>') args.add(parseSignature(s, pos));
                            pos[0]++; // '>'
                            pos[0]++; // ';'
                            return new SignatureType(raw, args.toArray(new java.lang.reflect.Type[0]));
                        }
                        pos[0]++; // ';'
                        return raw;
                    }
                    return switch (c) {
                        case 'I' -> int.class; case 'J' -> long.class;
                        case 'D' -> double.class; case 'F' -> float.class;
                        case 'Z' -> boolean.class; case 'B' -> byte.class;
                        case 'S' -> short.class; case 'C' -> char.class;
                        default -> Object.class;
                    };
                }

                private static final class SignatureType implements java.lang.reflect.ParameterizedType {
                    private final Class<?> raw;
                    private final java.lang.reflect.Type[] args;
                    SignatureType(Class<?> raw, java.lang.reflect.Type[] args) { this.raw = raw; this.args = args; }
                    public java.lang.reflect.Type getRawType() { return raw; }
                    public java.lang.reflect.Type[] getActualTypeArguments() { return args; }
                    public java.lang.reflect.Type getOwnerType() { return null; }
                }

                // #633: V de um `Map<K,V>` refletido (o genérico completo
                // sobrevive no RecordComponent/Field) — o binder recursa nos
                // valores; null = mapa cru (sem informação de tipo).
                private static java.lang.reflect.Type mapValueType(java.lang.reflect.Type generic) {
                    if (generic instanceof java.lang.reflect.ParameterizedType pt) {
                        java.lang.reflect.Type[] ta = pt.getActualTypeArguments();
                        if (ta.length == 2) return ta[1];
                    }
                    return null;
                }

                """;
    }
}
