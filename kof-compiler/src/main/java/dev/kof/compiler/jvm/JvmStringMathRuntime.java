package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (STDLIB S1).
 * kof.math (P0-a) - parte Int-only: clamp/abs/sign/min/max + predicados.
 * Concatenado em JvmStringRuntime.source(); paridade byte-idêntica com
 * Native (RuntimeMath) e JS (kofMath*) é o que KofMathTest prova.
 */
public final class JvmStringMathRuntime {

    private JvmStringMathRuntime() {}

    static String source() {
        return """

                // ── kof.math (STDLIB S1) ──────────────────────────────────

                public static int kof_math_abs(int v) {
                    return v < 0 ? -v : v;
                }

                public static int kof_math_sign(int v) {
                    return v > 0 ? 1 : (v < 0 ? -1 : 0);
                }

                public static int kof_math_clamp(int v, int lo, int hi) {
                    return v < lo ? lo : (v > hi ? hi : v);
                }

                public static int kof_math_min(int a, int b) {
                    return a <= b ? a : b;
                }

                public static int kof_math_max(int a, int b) {
                    return a >= b ? a : b;
                }

                public static boolean kof_math_isEven(int v) {
                    return (v & 1) == 0;
                }

                public static boolean kof_math_isOdd(int v) {
                    return (v & 1) != 0;
                }

                public static boolean kof_math_isPositive(int v) {
                    return v > 0;
                }

                public static boolean kof_math_isNegative(int v) {
                    return v < 0;
                }

                public static boolean kof_math_isZero(int v) {
                    return v == 0;
                }

                // ── kof.strings (STDLIB S2a) — predicados de char ──────────
                // Convenção de paridade (travada em KofStringsTest + matriz):
                // string vazia / null => false (nenhum char satisfaz).

                public static boolean kof_strings_isAlpha(String v) {
                    if (v == null || v.isEmpty()) return false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
                    }
                    return true;
                }

                public static boolean kof_strings_isNumeric(String v) {
                    if (v == null || v.isEmpty()) return false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (c < '0' || c > '9') return false;
                    }
                    return true;
                }

                public static boolean kof_strings_isAlphaNumeric(String v) {
                    if (v == null || v.isEmpty()) return false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                                  || (c >= '0' && c <= '9');
                        if (!ok) return false;
                    }
                    return true;
                }

                public static boolean kof_strings_isAscii(String v) {
                    if (v == null || v.isEmpty()) return false;
                    for (int i = 0; i < v.length(); i++) {
                        if (v.charAt(i) >= 128) return false;
                    }
                    return true;
                }

                public static boolean kof_strings_isUpperCase(String v) {
                    if (v == null || v.isEmpty()) return false;
                    boolean hasLetter = false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (c >= 'a' && c <= 'z') return false;
                        if (c >= 'A' && c <= 'Z') hasLetter = true;
                    }
                    return hasLetter;
                }

                public static boolean kof_strings_isLowerCase(String v) {
                    if (v == null || v.isEmpty()) return false;
                    boolean hasLetter = false;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (c >= 'A' && c <= 'Z') return false;
                        if (c >= 'a' && c <= 'z') hasLetter = true;
                    }
                    return hasLetter;
                }

                // Ocorrências NÃO-sobrepostas; vazio/null em qualquer lado => 0.
                public static int kof_strings_count(String v, String sub) {
                    if (v == null || sub == null || sub.isEmpty() || v.isEmpty()) return 0;
                    int n = 0, i = 0;
                    while ((i = v.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
                    return n;
                }

                // ASCII-only: paridade byte-idêntica com Native (byte[0] a-z).
                public static String kof_strings_capitalize(String v) {
                    if (v == null || v.isEmpty()) return v;
                    char c = v.charAt(0);
                    if (c >= 'a' && c <= 'z') return (char) (c - 32) + v.substring(1);
                    return v;
                }

                public static String kof_strings_reverse(String v) {
                    if (v == null) return null;
                    return new StringBuilder(v).reverse().toString();
                }

                public static String kof_strings_repeat(String v, int n) {
                    if (v == null || v.isEmpty() || n <= 0) return "";
                    StringBuilder sb = new StringBuilder(v.length() * n);
                    for (int i = 0; i < n; i++) sb.append(v);
                    return sb.toString();
                }

                public static String kof_strings_truncate(String v, int n) {
                    if (v == null) return null;
                    if (n <= 0) return "";
                    return v.length() <= n ? v : v.substring(0, n);
                }

                // pad = 1ª char do 3º arg; sem pad (null/"") ou len>=n => original.
                public static String kof_strings_padLeft(String v, int n, String pad) {
                    if (v == null) return null;
                    if (pad == null || pad.isEmpty() || v.length() >= n) return v;
                    char p = pad.charAt(0);
                    StringBuilder sb = new StringBuilder(n);
                    for (int i = v.length(); i < n; i++) sb.append(p);
                    return sb.append(v).toString();
                }

                public static String kof_strings_padRight(String v, int n, String pad) {
                    if (v == null) return null;
                    if (pad == null || pad.isEmpty() || v.length() >= n) return v;
                    char p = pad.charAt(0);
                    StringBuilder sb = new StringBuilder(v);
                    for (int i = v.length(); i < n; i++) sb.append(p);
                    return sb.toString();
                }

                // ── kof.strings (STDLIB S2b.4) — split+join de palavras ──
                // Words: sequência de [0-9A-Za-z] ASCII; boundary em: primeiro
                // alnum após não-alnum, lower/digit→Upper, e Upper→Upper lower
                // ("HTTPServer" = http+Server; "XMLParser" = xml+Parser).
                // >=128 (não-ASCII) é delimitador/ignorado — paridade com o
                // byte-a-byte do Native (NAT-STR01 documentado).
                // mode: 0=camel 1=pascal 2=snake 3=kebab 4=slug.
                private static String kof_strings_joinWords(String v, int mode) {
                    if (v == null) return null;
                    StringBuilder out = new StringBuilder();
                    int wc = 0;
                    int prev = -1;
                    for (int i = 0; i < v.length(); i++) {
                        int c = v.charAt(i);
                        boolean upper = c >= 65 && c <= 90;
                        boolean alnum = upper || (c >= 97 && c <= 122) || (c >= 48 && c <= 57);
                        if (!alnum) { prev = -1; continue; }
                        boolean nw = false;
                        if (prev == -1) nw = true;
                        else if (upper) {
                            boolean pl = prev >= 97 && prev <= 122;
                            boolean pd = prev >= 48 && prev <= 57;
                            boolean pu = prev >= 65 && prev <= 90;
                            boolean nl = false;
                            if (i + 1 < v.length()) {
                                int nx = v.charAt(i + 1);
                                nl = nx >= 97 && nx <= 122;
                            }
                            nw = pl || pd || (pu && nl);
                        }
                        if (nw) {
                            if (out.length() > 0 && mode >= 2) out.append((char) (mode == 2 ? '_' : '-'));
                            boolean cap = mode == 1 || (mode == 0 && wc > 0);
                            out.append((char) (cap ? (c >= 97 ? c - 32 : c) : (c >= 65 && c <= 90 ? c + 32 : c)));
                            wc++;
                        } else {
                            out.append((char) (c >= 65 && c <= 90 ? c + 32 : c));
                        }
                        prev = c;
                    }
                    return out.toString();
                }

                public static String kof_strings_toCamelCase(String v) { return kof_strings_joinWords(v, 0); }
                public static String kof_strings_toPascalCase(String v) { return kof_strings_joinWords(v, 1); }
                public static String kof_strings_toSnakeCase(String v) { return kof_strings_joinWords(v, 2); }
                public static String kof_strings_toKebabCase(String v) { return kof_strings_joinWords(v, 3); }
                public static String kof_strings_slugify(String v) { return kof_strings_joinWords(v, 4); }

                // ── kof.encoding (STDLIB S4) — hex (UTF-8 por bytes) ────────
                public static String kof_encoding_hexEncode(String v) {
                    if (v == null) return null;
                    byte[] b = v.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    StringBuilder sb = new StringBuilder(b.length * 2);
                    for (byte x : b) { sb.append(Character.forDigit((x >> 4) & 15, 16)); sb.append(Character.forDigit(x & 15, 16)); }
                    return sb.toString();
                }

                public static String kof_encoding_hexDecode(String v) {
                    if (v == null) return null;
                    byte[] out = new byte[(v.length() + 1) / 2];
                    for (int i = 0; i < out.length; i++) {
                        int hi = kof_enc_hexNib(v.charAt(i * 2));
                        int lo = (i * 2 + 1 < v.length()) ? kof_enc_hexNib(v.charAt(i * 2 + 1)) : 0;
                        out[i] = (byte) ((hi << 4) | lo);
                    }
                    return new String(out, java.nio.charset.StandardCharsets.UTF_8);
                }

                // ASCII-estrito (Character.digit aceitaria dígitos Unicode —
                // paridade byte-a-byte com o asm, que só conhece 0-9a-fA-F).
                private static int kof_enc_hexNib(char c) {
                    if (c >= '0' && c <= '9') return c - '0';
                    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
                    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
                    return 0;
                }

                // ── kof.encoding (STDLIB S4.2) — base64 (RFC 4648) ──────────
                // Tabela + semântica de decode TOLERANTE idênticas ao
                // kof_b64_*_internal do runtime x86 (ignora inválidos, para em
                // '=', grupos <4 incompletos emetem floor(r9*6/8) bytes) e ao
                // kofSecB64Decode do JS.
                private static final String KOF_B64_CHARS =
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

                public static String kof_encoding_base64Encode(String v) {
                    if (v == null) return null;
                    byte[] b = v.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    StringBuilder sb = new StringBuilder((b.length + 2) / 3 * 4);
                    int i = 0;
                    while (i + 2 < b.length) {
                        int n = ((b[i] & 255) << 16) | ((b[i + 1] & 255) << 8) | (b[i + 2] & 255);
                        sb.append(KOF_B64_CHARS.charAt(n >> 18)).append(KOF_B64_CHARS.charAt((n >> 12) & 63))
                          .append(KOF_B64_CHARS.charAt((n >> 6) & 63)).append(KOF_B64_CHARS.charAt(n & 63));
                        i += 3;
                    }
                    if (i + 1 == b.length) {
                        int n = (b[i] & 255) << 16;
                        sb.append(KOF_B64_CHARS.charAt(n >> 18)).append(KOF_B64_CHARS.charAt((n >> 12) & 63))
                          .append("==");
                    } else if (i + 2 == b.length) {
                        int n = ((b[i] & 255) << 16) | ((b[i + 1] & 255) << 8);
                        sb.append(KOF_B64_CHARS.charAt(n >> 18)).append(KOF_B64_CHARS.charAt((n >> 12) & 63))
                          .append(KOF_B64_CHARS.charAt((n >> 6) & 63)).append('=');
                    }
                    return sb.toString();
                }

                public static String kof_encoding_base64Decode(String v) {
                    if (v == null) return null;
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    int acc = 0, nch = 0;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (c == '=') break;
                        int d = KOF_B64_CHARS.indexOf(c);
                        if (d < 0) continue;
                        acc = (acc << 6) | d;
                        if (++nch == 4) {
                            out.write((acc >> 16) & 255);
                            out.write((acc >> 8) & 255);
                            out.write(acc & 255);
                            acc = 0; nch = 0;
                        }
                    }
                    if (nch == 2) out.write((acc >> 4) & 255);
                    else if (nch == 3) {
                        out.write((acc >> 10) & 255);
                        out.write((acc >> 2) & 255);
                    }
                    return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                }

                // ── kof.encoding (STDLIB S4.2c) — base64url (RFC 4648 §5) ──
                // Encode: alfabeto -_ SEM padding (paridade kofSecB64Url JS e
                // kof_b64url_encode_internal x86). Decode: substitui -_/→+/
                // e aplica o base64Decode TOLERANTE comum (aceita os dois
                // alfabetos e padding opcional — especificação única nos 3).
                public static String kof_encoding_base64UrlEncode(String v) {
                    String b64 = kof_encoding_base64Encode(v);
                    if (b64 == null) return null;
                    int eq = b64.indexOf('=');
                    if (eq >= 0) b64 = b64.substring(0, eq);
                    return b64.replace('+', '-').replace('/', '_');
                }

                public static String kof_encoding_base64UrlDecode(String v) {
                    if (v == null) return null;
                    return kof_encoding_base64Decode(v.replace('-', '+').replace('_', '/'));
                }


                // ── kof.encoding (STDLIB S4.2b) — percent-encoding (RFC 3986) ──
                // unreserved [A-Za-z0-9-_.~] preservado; todo outro byte UTF-8
                // vira %XX (hex MAIÚSCULO). Espaço => %20 (não '+'). urlDecode é
                // o inverso (aceita %xx minúsculo; '%' sem 2 dígitos => literal).
                public static String kof_encoding_urlEncode(String v) {
                    if (v == null) return null;
                    final String HEX = "0123456789ABCDEF";
                    byte[] b = v.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    StringBuilder sb = new StringBuilder(b.length * 3);
                    for (byte x : b) {
                        int c = x & 255;
                        boolean unres = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                                || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
                        if (unres) sb.append((char) c);
                        else { sb.append('%').append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 15)); }
                    }
                    return sb.toString();
                }

                public static String kof_encoding_urlDecode(String v) {
                    if (v == null) return null;
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    int i = 0;
                    while (i < v.length()) {
                        char c = v.charAt(i);
                        if (c == '%' && i + 2 < v.length()) {
                            int hi = kof_enc_hexStrict(v.charAt(i + 1));
                            int lo = kof_enc_hexStrict(v.charAt(i + 2));
                            if (hi >= 0 && lo >= 0) {   // ASCII-estrito: inválido => % literal
                                out.write((hi << 4) | lo);
                                i += 3;
                                continue;
                            }
                        }
                        out.write((byte) c);
                        i++;
                    }
                    return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                }

                private static int kof_enc_hexStrict(char c) {
                    if (c >= '0' && c <= '9') return c - '0';
                    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
                    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
                    return -1;
                }

                // ── kof.strings (STDLIB S3.1) — HTML escape/unescape ────────
                // escapeHtml: 5 chars especiais -> entidade nomeada (amp/lt/
                // gt/quot) ou numérica &#39; (apos); bytes >=128 copiados
                // (saída só toca ASCII nas entidades). null -> null, "" -> "".
                public static String kof_strings_escapeHtml(String v) {
                    if (v == null) return null;
                    StringBuilder o = new StringBuilder(v.length() + 8);
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        switch (c) {
                            case '&': o.append("&amp;"); break;
                            case '<': o.append("&lt;"); break;
                            case '>': o.append("&gt;"); break;
                            case '"': o.append("&quot;"); break;
                            case (char) 39: o.append("&#39;"); break;
                            default: o.append(c);
                        }
                    }
                    return o.toString();
                }

                // escapeJson: corpo de string literal JSON (RFC 8259) — as
                // aspas de delimitacao sao do CALLER. backslash dobra; aspas
                // vira backslash-quote; b/f/n/r viram 2-char; ctrl abaixo de
                // 0x20 vira backslash-u com 4 hex minusculos; demais (>=0x20,
                // incl >=128) copiados. null/vazia => original.
                public static String kof_strings_escapeJson(String v) {
                    if (v == null) return null;
                    StringBuilder o = new StringBuilder(v.length() + 8);
                    final char BS = (char) 92;
                    for (int i = 0; i < v.length(); i++) {
                        char c = v.charAt(i);
                        if (c == BS) { o.append(BS).append(BS); continue; }
                        if (c == '"') { o.append(BS).append('"'); continue; }
                        if (c < 32) {
                            if (c == 8) { o.append(BS).append('b'); continue; }
                            if (c == 12) { o.append(BS).append('f'); continue; }
                            if (c == 10) { o.append(BS).append('n'); continue; }
                            if (c == 13) { o.append(BS).append('r'); continue; }
                            if (c == 9) { o.append(BS).append('t'); continue; }
                            o.append(BS).append('u');
                            String hx = Integer.toHexString(c);
                            for (int z = hx.length(); z < 4; z++) o.append('0');
                            o.append(hx);
                            continue;
                        }
                        o.append(c);
                    }
                    return o.toString();
                }

                // unescapeHtml: 5 nomeadas (&amp &lt &gt &quot &apos) +
                // numéricos &#DDD; / &#xHH; (valid <0x10000, não-surogates,
                // >0). Qualquer outro "&..." fica LITERAL (regra travada).
                public static String kof_strings_unescapeHtml(String v) {
                    if (v == null) return null;
                    StringBuilder o = new StringBuilder(v.length());
                    int n = v.length(), i = 0;
                    while (i < n) {
                        char c = v.charAt(i);
                        if (c != '&') { o.append(c); i++; continue; }
                        if (v.startsWith("&amp;", i)) { o.append('&'); i += 5; continue; }
                        if (v.startsWith("&lt;", i)) { o.append('<'); i += 4; continue; }
                        if (v.startsWith("&gt;", i)) { o.append('>'); i += 4; continue; }
                        if (v.startsWith("&quot;", i)) { o.append('"'); i += 6; continue; }
                        if (v.startsWith("&apos;", i)) { o.append((char) 39); i += 6; continue; }
                        if (i + 1 < n && v.charAt(i + 1) == '#') {
                            int j = i + 2; boolean hx = false;
                            if (j < n && (v.charAt(j) == 'x' || v.charAt(j) == 'X')) { hx = true; j++; }
                            int k = j; int acc = 0;
                            while (k < n) {
                                char d = v.charAt(k); int dv;
                                if (d >= '0' && d <= '9') dv = d - '0';
                                else if (hx && d >= 'a' && d <= 'f') dv = d - 'a' + 10;
                                else if (hx && d >= 'A' && d <= 'F') dv = d - 'A' + 10;
                                else break;
                                acc = acc * (hx ? 16 : 10) + dv;
                                if (acc > 0x10FFFF) break;
                                k++;
                            }
                            if (k > j && k < n && v.charAt(k) == ';' && acc > 0
                                    && acc < 0x10000 && !(acc >= 0xD800 && acc <= 0xDFFF)) {
                                o.appendCodePoint(acc); i = k + 1; continue;
                            }
                        }
                        o.append('&'); i++;
                    }
                    return o.toString();
                }
        """;
    }
}
