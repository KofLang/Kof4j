package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S3.2). kof.strings
 * removeWhitespace/normalizeWhitespace — ASCII (bytes >=128 copiados;
 * paridade capitalize). concatena via JvmStringRuntime.source().
 */
public final class JvmStringWsRuntime {

    private JvmStringWsRuntime() {}

    static String source() {
        return """

                // ── kof.strings (STDLIB S3.2) — whitespace ────────────────
                // WS = {tab,LF,VT,FF,CR,espaço} (9..13,32). >=128 NÃO é WS.
                private static boolean kof_isWs(char c) {
                    return c == 32 || (c >= 9 && c <= 13);
                }

                // removeWhitespace: descarta todo WS; null -> null.
                public static String kof_strings_removeWhitespace(String v) {
                    if (v == null) return null;
                    StringBuilder o = new StringBuilder(v.length());
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (!kof_isWs(c)) o.append(c);
                    }
                    return o.toString();
                }

                // normalizeWhitespace: trim ends de WS; colapsa runs internos
                // de WS p/ UM espaço. null -> null. "  a  b  " -> "a b".
                public static String kof_strings_normalizeWhitespace(String v) {
                    if (v == null) return null;
                    StringBuilder o = new StringBuilder(v.length());
                    boolean inws = false, started = false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (kof_isWs(c)) {
                            if (started) inws = true;
                        } else {
                            if (inws) { o.append(' '); inws = false; }
                            o.append(c); started = true;
                        }
                    }
                    return o.toString();
                }
        """;
    }
}
