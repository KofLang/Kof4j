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
    """;
}
