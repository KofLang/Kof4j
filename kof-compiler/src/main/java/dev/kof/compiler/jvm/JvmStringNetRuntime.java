package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S8, plan §4). kof.net —
 * 6 campos escalares de URI (RFC 3986 subset v1) + fachada query*. Semântica
 * EXATA travada no oracle do teste (null->null; ausente->""; nunca lança;
 * scheme só [A-Za-z][A-Za-z0-9+.-]* antes do 1º ':'; authority só após "//",
 * userinfo após último '@'; host até 1º ':' do authority — v1 SEM colchetes
 * IPv6; port String após esse ':'; path/query/fragment por 1º '?'/'#').
 * queryEncode/Decode = delegação a encoding.urlEncode/Decode (regra 2).
 */
public final class JvmStringNetRuntime {

    private JvmStringNetRuntime() {}

    static String source() {
        return """

                // ── kof.net (STDLIB S8) ─────────────────────────────────────
                private static String[] kof_net_split(String u) {
                    // [sch, host, port, path, query, frag] — oracle plan §4 v1
                    String[] r = {"", "", "", "", "", ""};
                    if (u == null) return null;
                    int n = u.length();
                    int cp = u.indexOf(':');
                    int after = 0;
                    if (cp > 0) {
                        boolean ok = false;
                        char f = u.charAt(0);
                        if ((f >= 'A' && f <= 'Z') || (f >= 'a' && f <= 'z')) {
                            ok = true;
                            for (int i = 1; i < cp; i++) {
                                char c = u.charAt(i);
                                boolean dg = c >= '0' && c <= '9';
                                boolean le = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
                                if (!dg && !le && c != '+' && c != '-' && c != '.') { ok = false; break; }
                            }
                        }
                        if (ok) { r[0] = u.substring(0, cp); after = cp + 1; }
                    }
                    String tail = u.substring(after);
                    int fp = tail.indexOf('#');
                    String body = fp < 0 ? tail : tail.substring(0, fp);
                    if (fp >= 0) r[5] = tail.substring(fp + 1);
                    int qp = body.indexOf('?');
                    if (qp >= 0) { r[3] = body.substring(0, qp); r[4] = body.substring(qp + 1); }
                    else r[3] = body;
                    if (r[3].startsWith("//")) {
                        String a = r[3].substring(2);
                        int cut = a.length();
                        for (int i = 0; i < a.length(); i++) {
                            char c = a.charAt(i);
                            if (c == '/' || c == '?' || c == '#') { cut = i; break; }
                        }
                        String auth = a.substring(0, cut);
                        r[3] = a.substring(cut);
                        int at = auth.lastIndexOf('@');
                        if (at >= 0) auth = auth.substring(at + 1);
                        int hp = auth.indexOf(':');
                        if (hp >= 0) { r[1] = auth.substring(0, hp); r[2] = auth.substring(hp + 1); }
                        else r[1] = auth;
                    }
                    return r;
                }

                public static String kof_net_scheme(String v) { String[] p = kof_net_split(v); return p == null ? null : p[0]; }
                public static String kof_net_host(String v) { String[] p = kof_net_split(v); return p == null ? null : p[1]; }
                public static String kof_net_port(String v) { String[] p = kof_net_split(v); return p == null ? null : p[2]; }
                public static String kof_net_path(String v) { String[] p = kof_net_split(v); return p == null ? null : p[3]; }
                public static String kof_net_query(String v) { String[] p = kof_net_split(v); return p == null ? null : p[4]; }
                public static String kof_net_fragment(String v) { String[] p = kof_net_split(v); return p == null ? null : p[5]; }
                public static String kof_net_queryEncode(String v) { return kof_encoding_urlEncode(v); }
                public static String kof_net_queryDecode(String v) { return kof_encoding_urlDecode(v); }
        """;
    }
}
