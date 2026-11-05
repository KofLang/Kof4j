package dev.kof.compiler.js;

/**
 * §832 — JS face of {@code http.send(method, url, body, headers)} and the
 * {@code Response} accessors. Same transports as {@code kofHttpRequest}: the
 * Java HttpClient through GraalJS interop (synchronous), else {@code fetch}
 * (a Promise, resolved by {@code await}). A 4xx/5xx is returned, never thrown;
 * header names are lower-case, as on the JVM.
 */
final class JsRuntimeHttpSend {

    private JsRuntimeHttpSend() {}

    // §176/§257: a method, not a constant literal, so consumers never inline a stale copy.
    static String httpSendRuntime() {
        return """
            function kofHttpResponseOf(status, body, headers) {
                return { status: status, body: body, headers: headers,
                         toString() { return "Response[status=" + status + "]"; } };
            }
            export function kofHttpSend(method, url, body, headers) {
                if (kofHttpCircuitOpen()) {
                    throw new Error("kof.http circuit open (fail fast): " + url);
                }
                const verb = String(method).toUpperCase();
                const lines = headers ? headers.split("\\n") : [];
                if (typeof Java !== 'undefined' && Java.type) {
                    try {
                        const HttpClient = Java.type('java.net.http.HttpClient');
                        const HttpRequest = Java.type('java.net.http.HttpRequest');
                        const URI = Java.type('java.net.URI');
                        const Duration = Java.type('java.time.Duration');
                        const client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(kofHttpTimeoutSec)).build();
                        const builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(kofHttpTimeoutSec));
                        for (const line of lines) {
                            const i = line.indexOf(":");
                            if (i > 0) builder.header(line.substring(0, i).trim(), line.substring(i + 1).trim());
                        }
                        builder.method(verb, body != null ? HttpRequest.BodyPublishers.ofString(body)
                                : HttpRequest.BodyPublishers.noBody());
                        const resp = client.send(builder.build(),
                                Java.type('java.net.http.HttpResponse$BodyHandlers').ofString());
                        const h = new Map();
                        const jm = resp.headers().map();
                        const it = jm.keySet().iterator();
                        while (it.hasNext()) {
                            const k = String(it.next());
                            if (k.startsWith(":")) continue;
                            const vs = jm.get(k);
                            const parts = [];
                            for (let j = 0; j < vs.size(); j++) parts.push(String(vs.get(j)));
                            h.set(k.toLowerCase(), parts.join(", "));
                        }
                        if (resp.statusCode() >= 500) kofHttpCircuitRecordFailure(); else kofHttpCircuitRecordSuccess();
                        return kofHttpResponseOf(resp.statusCode(), resp.body() != null ? String(resp.body()) : "", h);
                    } catch (e) {
                        kofHttpCircuitRecordFailure();
                        throw e;
                    }
                }
                if (typeof fetch !== 'undefined') {
                    const hs = {};
                    for (const line of lines) {
                        const i = line.indexOf(":");
                        if (i > 0) hs[line.substring(0, i).trim()] = line.substring(i + 1).trim();
                    }
                    const ac = new AbortController();
                    const timer = setTimeout(() => ac.abort(), (kofHttpTimeoutSec || 30) * 1000);
                    return fetch(url, { method: verb, headers: hs, body: body != null ? body : undefined, signal: ac.signal })
                        .then(r => r.text().then(text => {
                            clearTimeout(timer);
                            const h = new Map();
                            r.headers.forEach((v, k) => h.set(k.toLowerCase(), v));
                            if (r.status >= 500) kofHttpCircuitRecordFailure(); else kofHttpCircuitRecordSuccess();
                            return kofHttpResponseOf(r.status, text, h);
                        }), err => { clearTimeout(timer); kofHttpCircuitRecordFailure(); throw err; });
                }
                throw new Error("kof.http: no transport (no Java interop and no fetch)");
            }
            export function kofHttpResponseStatus(r) { return r.status; }
            export function kofHttpResponseBody(r) { return r.body; }
            export function kofHttpResponseHeader(r, name) {
                if (name == null) return null;
                const v = r.headers.get(String(name).toLowerCase());
                return v === undefined ? null : v;
            }
            export function kofHttpResponseHeaders(r) { return new Map(r.headers); }
            """;
    }
}
