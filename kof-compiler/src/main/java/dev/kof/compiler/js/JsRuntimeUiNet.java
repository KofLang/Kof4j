package dev.kof.compiler.js;

/**
 * Runtime JS do kof.net (STDLIB S8) — 6 campos escalares de URI + fachada
 * query*. MESMA máquina de estados do JvmStringNetRuntime / oracle do plano
 * §4 (v1: sem colchetes IPv6, sem lançar, ausente=>""). queryEncode/Decode
 * delegam ao urlEncode/urlDecode do Stdlib (mesmo módulo).
 */
public final class JsRuntimeUiNet {

    private JsRuntimeUiNet() {}

    static final String NET_RUNTIME = """

            // ── kof.net (STDLIB S8) ────────────────────────────────────────
            function kofNetSplit(u) {
                if (u == null) return null;
                const r = ["", "", "", "", "", ""];
                const cp = u.indexOf(":");
                let after = 0;
                if (cp > 0) {
                    let ok = /[A-Za-z]/.test(u[0]);
                    if (ok) {
                        for (let i = 1; i < cp; i++) {
                            if (!/[A-Za-z0-9+.\\-]/.test(u[i])) { ok = false; break; }
                        }
                    }
                    if (ok) { r[0] = u.slice(0, cp); after = cp + 1; }
                }
                const tail = u.slice(after);
                const fp = tail.indexOf("#");
                const body = fp < 0 ? tail : tail.slice(0, fp);
                if (fp >= 0) r[5] = tail.slice(fp + 1);
                const qp = body.indexOf("?");
                if (qp >= 0) { r[3] = body.slice(0, qp); r[4] = body.slice(qp + 1); }
                else r[3] = body;
                if (r[3].startsWith("//")) {
                    let a = r[3].slice(2);
                    let cut = a.length;
                    for (let i = 0; i < a.length; i++) {
                        if (a[i] === "/" || a[i] === "?" || a[i] === "#") { cut = i; break; }
                    }
                    let auth = a.slice(0, cut);
                    r[3] = a.slice(cut);
                    const at = auth.lastIndexOf("@");
                    if (at >= 0) auth = auth.slice(at + 1);
                    const hp = auth.indexOf(":");
                    if (hp >= 0) { r[1] = auth.slice(0, hp); r[2] = auth.slice(hp + 1); }
                    else r[1] = auth;
                }
                return r;
            }
            const kofNetField = (u, i) => { const p = kofNetSplit(u); return p === null ? null : p[i]; };
            export function kofNetScheme(v) { return kofNetField(v, 0); }
            export function kofNetHost(v) { return kofNetField(v, 1); }
            export function kofNetPort(v) { return kofNetField(v, 2); }
            export function kofNetPath(v) { return kofNetField(v, 3); }
            export function kofNetQuery(v) { return kofNetField(v, 4); }
            export function kofNetFragment(v) { return kofNetField(v, 5); }
            export function kofNetQueryEncode(v) { return kofEncodingUrlEncode(v); }
            export function kofNetQueryDecode(v) { return kofEncodingUrlDecode(v); }
            """;
}
