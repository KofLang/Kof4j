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

            export function kofStringsIndent(v, n) {
                if (v == null) return null;
                if (v.length === 0 || n <= 0) return v;
                const LF = String.fromCharCode(10);
                const CR = String.fromCharCode(13);
                const pad = " ".repeat(n);
                const lines = v.split(LF);
                for (let i = 0; i < lines.length; i++) {
                    let line = lines[i];
                    let hasCr = line.endsWith(CR);
                    if (hasCr) line = line.slice(0, -1);
                    if (line.length > 0) {
                        lines[i] = pad + line + (hasCr ? CR : "");
                    } else {
                        lines[i] = hasCr ? CR : "";
                    }
                }
                return lines.join(LF);
            }

            export function kofStringsDedent(v) {
                if (v == null || v.length === 0) return v;
                const LF = String.fromCharCode(10);
                const CR = String.fromCharCode(13);
                const TAB = String.fromCharCode(9);
                const lines = v.split(LF);
                let minIndent = -1;
                for (let i = 0; i < lines.length; i++) {
                    let line = lines[i];
                    if (line.endsWith(CR)) line = line.slice(0, -1);
                    let ws = 0;
                    while (ws < line.length && (line[ws] === ' ' || line[ws] === TAB)) {
                        ws++;
                    }
                    if (ws < line.length) {
                        if (minIndent === -1 || ws < minIndent) minIndent = ws;
                    }
                }
                if (minIndent <= 0) return v;
                for (let i = 0; i < lines.length; i++) {
                    let line = lines[i];
                    let hasCr = line.endsWith(CR);
                    if (hasCr) line = line.slice(0, -1);
                    let ws = 0;
                    while (ws < line.length && ws < minIndent && (line[ws] === ' ' || line[ws] === TAB)) {
                        ws++;
                    }
                    lines[i] = line.slice(ws) + (hasCr ? CR : "");
                }
                return lines.join(LF);
            }

            // STDLIB S3.1c — escapeJson (corpo de string literal JSON, RFC 8259;
            // aspas de delimitação são do caller). \\\\ -> \\\\\\\\  " -> \\" ;
            // \\b \\f \\n \\r \\t 2-char; ctrl <0x20 -> \\u00xx; demais copiados.
            export function kofStringsEscapeJson(v) {
                if (v == null) return null;
                const B = String.fromCharCode(92);
                let o = "";
                for (let i = 0; i < v.length; i++) {
                    const c = v.charCodeAt(i);
                    if (c === 92) o += B + B;
                    else if (c === 34) o += B + '"';
                    else if (c === 8) o += B + "b";
                    else if (c === 12) o += B + "f";
                    else if (c === 10) o += B + "n";
                    else if (c === 13) o += B + "r";
                    else if (c === 9) o += B + "t";
                    else if (c < 32) o += B + "u" + c.toString(16).padStart(4, "0");
                    else o += v[i];
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
