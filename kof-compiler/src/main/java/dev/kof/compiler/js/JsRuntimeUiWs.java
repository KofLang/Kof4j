package dev.kof.compiler.js;

/**
 * STDLIB S3.2 — kof.strings removeWhitespace/normalizeWhitespace (JS).
 * WS = 9..13,32 (ASCII; >=128 não é WS). Concat via JsArtifactWriter.
 */
public final class JsRuntimeUiWs {

    private JsRuntimeUiWs() {}

    static final String WS_RUNTIME = """

            // ── kof.strings (STDLIB S3.2) — whitespace ──────────────────
            function kofIsWsC(c) { return c === 32 || (c >= 9 && c <= 13); }
            export function kofStringsRemoveWhitespace(v) {
                if (v == null) return null;
                let o = "";
                for (let i = 0; i < v.length; i++) {
                    const c = v.charCodeAt(i);
                    if (!kofIsWsC(c)) o += v[i];
                }
                return o;
            }
            export function kofStringsNormalizeWhitespace(v) {
                if (v == null) return null;
                let o = "", inws = false, started = false;
                for (let i = 0; i < v.length; i++) {
                    const c = v.charCodeAt(i);
                    if (kofIsWsC(c)) {
                        if (started) inws = true;
                    } else {
                        if (inws) { o += " "; inws = false; }
                        o += v[i]; started = true;
                    }
                }
                return o;
            }

            // STDLIB S3.1b — unescapeHtml (5 nomeadas + numéricos &#DDD;/&#xHH;
            // val <0x10000 não-surogate >0; outro "&..." fica LITERAL).
            function kofUnescHexD(c) {
                if (c >= 48 && c <= 57) return c - 48;
                if (c >= 97 && c <= 102) return c - 97 + 10;
                if (c >= 65 && c <= 70) return c - 65 + 10;
                return -1;
            }
            export function kofStringsUnescapeHtml(v) {
                if (v == null) return null;
                let o = "", n = v.length, i = 0;
                while (i < n) {
                    const c = v[i];
                    if (c !== "&") { o += c; i++; continue; }
                    if (v.startsWith("&amp;", i)) { o += "&"; i += 5; continue; }
                    if (v.startsWith("&lt;", i)) { o += "<"; i += 4; continue; }
                    if (v.startsWith("&gt;", i)) { o += ">"; i += 4; continue; }
                    if (v.startsWith("&quot;", i)) { o += '\"'; i += 6; continue; }
                    if (v.startsWith("&apos;", i)) { o += String.fromCharCode(39); i += 6; continue; }
                    if (i + 1 < n && v.charCodeAt(i + 1) === 35) {   // '#'
                        let j = i + 2, hx = false;
                        const x = v.charCodeAt(j);
                        if (j < n && (x === 120 || x === 88)) { hx = true; j++; }
                        let k = j, acc = 0;
                        while (k < n) {
                            const dv = kofUnescHexD(v.charCodeAt(k));
                            if (dv < 0 || (dv > 9 && !hx)) break;
                            if (!hx && (v.charCodeAt(k) < 48 || v.charCodeAt(k) > 57)) break;
                            acc = acc * (hx ? 16 : 10) + dv;
                            if (acc > 0x10FFFF) break;
                            k++;
                        }
                        if (k > j && k < n && v.charCodeAt(k) === 59 && acc > 0
                                && acc < 0x10000 && !(acc >= 0xD800 && acc <= 0xDFFF)) {
                            o += String.fromCodePoint(acc); i = k + 1; continue;
                        }
                    }
                    o += "&"; i++;
                }
                return o;
            }

    """;
}
