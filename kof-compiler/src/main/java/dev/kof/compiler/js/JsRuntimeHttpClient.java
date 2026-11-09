package dev.kof.compiler.js;

/**
 * JsRuntimeHttpClient — a família JS do cliente HTTP (o núcleo
 * {@code kofHttpRequest} com circuito/timeout/retries + os wrappers
 * get/post/put/delete/options/patch + o {@code kofHttpSend} da #1126
 * devolvendo o Response como valor sem throw em 4xx/5xx), extraída do
 * JsRuntimeUiLayout verbatim no MESMO commit que a fatia 2 da #1126 (o
 * arquivo cruzou 571→644 no gate ≤500). Estado do circuito é
 * módulo-nível do bloco (incluído junto quando qualquer wrapper é semeado).
 */
public final class JsRuntimeHttpClient {

    private JsRuntimeHttpClient() {}

    public static String HTTP_CLIENT_RUNTIME() { return """
            let kofHttpTimeoutSec = 10;
            let kofHttpRetries = 0;
            let kofHttpCircuitTrips = 0;
            let kofHttpCircuitFailures = 0;
            let kofHttpCircuitOpenUntil = 0;
            const KOF_HTTP_CIRCUIT_WINDOW_MS = 30000;
            export function kofHttpTimeoutSet(sec) { kofHttpTimeoutSec = sec; }
            export function kofHttpRetrySet(n) { kofHttpRetries = Math.max(0, n | 0); }
            export function kofHttpCircuitSet(trips) {
                kofHttpCircuitTrips = Math.max(0, trips | 0);
                if (kofHttpCircuitTrips <= 0) { kofHttpCircuitFailures = 0; kofHttpCircuitOpenUntil = 0; }
            }
            function kofHttpCircuitOpen() {
                if (kofHttpCircuitOpenUntil === 0) return false;
                if (Date.now() >= kofHttpCircuitOpenUntil) { kofHttpCircuitOpenUntil = 0; return false; }
                return true;
            }
            function kofHttpCircuitRecordFailure() {
                if (kofHttpCircuitTrips <= 0) return;
                if (++kofHttpCircuitFailures >= kofHttpCircuitTrips) {
                    kofHttpCircuitOpenUntil = Date.now() + KOF_HTTP_CIRCUIT_WINDOW_MS;
                }
            }
            function kofHttpCircuitRecordSuccess() {
                kofHttpCircuitFailures = 0;
                kofHttpCircuitOpenUntil = 0;
            }
            export function kofHttpGet(url) { return kofHttpRequest(url, "GET", null, null); }
            export function kofHttpGetHeaders(url, headers) { return kofHttpRequest(url, "GET", headers, null); }
            export function kofHttpDelete(url) { return kofHttpRequest(url, "DELETE", null, null); }
            export function kofHttpDeleteHeaders(url, headers) { return kofHttpRequest(url, "DELETE", headers, null); }
            export function kofHttpOptions(url) { return kofHttpRequest(url, "OPTIONS", null, null); }
            export function kofHttpOptionsHeaders(url, headers) { return kofHttpRequest(url, "OPTIONS", headers, null); }
            export function kofHttpPost(url, body) { return kofHttpRequest(url, "POST", null, body); }
            export function kofHttpPostHeaders(url, body, headers) { return kofHttpRequest(url, "POST", headers, body); }
            export function kofHttpPut(url, body) { return kofHttpRequest(url, "PUT", null, body); }
            export function kofHttpPutHeaders(url, body, headers) { return kofHttpRequest(url, "PUT", headers, body); }
            export function kofHttpPatch(url, body) { return kofHttpRequest(url, "PATCH", null, body); }
            export function kofHttpPatchHeaders(url, body, headers) { return kofHttpRequest(url, "PATCH", headers, body); }
            // #1126 fatia 2: http.send — a resposta é um VALOR lido pelo
            // caller (status/headers/body), NUNCA throw em 4xx/5xx (o
            // contrato da decisão 05/11; o kofHttpRequest velho lança em 5xx
            // e devolve só o body — get/post intactos). O núcleo reusa a
            // MESMA disciplina de circuito/timeout do bloco.
            export function kofHttpSend(method, url, body, headers) {
                if (kofHttpCircuitOpen()) {
                    throw new Error("kof.http circuit open (fail fast): " + url);
                }
                let lastErr = null;
                const attempts = kofHttpRetries + 1;
                for (let attempt = 0; attempt < attempts; attempt++) {
                    try {
                        if (typeof Java !== 'undefined' && Java.type) {
                            const HttpClient = Java.type('java.net.http.HttpClient');
                            const HttpRequest = Java.type('java.net.http.HttpRequest');
                            const URI = Java.type('java.net.URI');
                            const Duration = Java.type('java.time.Duration');
                            let client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(kofHttpTimeoutSec)).build();
                            let builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(kofHttpTimeoutSec));
                            if (headers) {
                                let lines = headers.split("\\n");
                                for (let line of lines) {
                                    let idx = line.indexOf(":");
                                    if (idx > 0) builder.header(line.substring(0, idx).trim(), line.substring(idx+1).trim());
                                }
                            }
                            let publisher = body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody();
                            builder.method(method, publisher);
                            let req = builder.build();
                            let resp = client.send(req, Java.type('java.net.http.HttpResponse$BodyHandlers').ofString());
                            kofHttpCircuitRecordSuccess();
                            let hs = "";
                            const map = resp.headers().map();
                            for (const k of map.keys()) {
                                for (const v of map.get(k)) hs += k + ": " + v + "\\n";
                            }
                            return { status: resp.statusCode(), headers: hs, body: resp.body() != null ? resp.body() : "" };
                        }
                        lastErr = new Error("http.send: no Java host interop on this runtime");
                        kofHttpCircuitRecordFailure();
                    } catch (e) {
                        // status HTTP nunca é erro (#1126); só falha de CONEXÃO
                        // conta tentativa (o circuito) e propaga como no JVM
                        // (catchable String #756).
                        lastErr = e;
                        kofHttpCircuitRecordFailure();
                    }
                }
                throw lastErr != null ? lastErr : new Error("http.send failed: " + url);
            }

            export function kofHttpResponseStatus(r) { return r.status; }
            export function kofHttpResponseHeader(r, name) {
                const want = name.toLowerCase() + ":";
                const lines = r.headers.split("\\n");
                for (const line of lines) {
                    if (line.toLowerCase().startsWith(want)) {
                        return line.substring(line.indexOf(':') + 1).trim();
                    }
                }
                return "";
            }
            export function kofHttpResponseHeaders(r) { return r.headers; }
            export function kofHttpResponseBody(r) { return r.body; }
            export function kofHttpStatus(url) {
                try {
                    const HttpClient = Java.type('java.net.http.HttpClient');
                    const HttpRequest = Java.type('java.net.http.HttpRequest');
                    const URI = Java.type('java.net.URI');
                    const Duration = Java.type('java.time.Duration');
                    let client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(kofHttpTimeoutSec)).build();
                    let builder = HttpRequest.newBuilder().uri(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(kofHttpTimeoutSec));
                    let req = builder.build();
                    let resp = client.send(req, Java.type('java.net.http.HttpResponse$BodyHandlers').discarding());
                    return resp.statusCode();
                } catch (e) {
                    // sem interop Java (Node/Browser): HEAD via fetch → Promise
                    // (resolve no await; HTTP004 — face síncrona JS não existe).
                    if (typeof fetch !== 'undefined') {
                        const ac = new AbortController();
                        const timer = setTimeout(() => ac.abort(), (kofHttpTimeoutSec || 30) * 1000);
                        return fetch(url, { method: "HEAD", signal: ac.signal })
                            .then(r => { clearTimeout(timer); return r.status; },
                                  err => { clearTimeout(timer); throw err; });
                    }
                    throw new Error("kof.http.status: no transport (sem Java interop nem fetch): " + url);
                }
            }
            function kofHttpRequest(url, method, headers, body) {
                if (kofHttpCircuitOpen()) {
                    throw new Error("kof.http circuit open (fail fast): " + url);
                }
                let lastErr = null;
                const attempts = kofHttpRetries + 1;
                for (let attempt = 0; attempt < attempts; attempt++) {
                    try {
                        // Prefer Java HttpClient via GraalJS interop (synchronous, works in KofJsRunner)
                        if (typeof Java !== 'undefined' && Java.type) {
                            const HttpClient = Java.type('java.net.http.HttpClient');
                            const HttpRequest = Java.type('java.net.http.HttpRequest');
                            const URI = Java.type('java.net.URI');
                            const Duration = Java.type('java.time.Duration');
                            let client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(kofHttpTimeoutSec)).build();
                            let builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(kofHttpTimeoutSec));
                            if (headers) {
                                let lines = headers.split("\\n");
                                for (let line of lines) {
                                    let idx = line.indexOf(":");
                                    if (idx > 0) builder.header(line.substring(0, idx).trim(), line.substring(idx+1).trim());
                                }
                            }
                            let publisher = body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody();
                            builder.method(method, publisher);
                            let req = builder.build();
                            let resp = client.send(req, Java.type('java.net.http.HttpResponse$BodyHandlers').ofString());
                            if (resp.statusCode() >= 500) {
                                lastErr = new Error("HTTP " + resp.statusCode() + " from " + url);
                                kofHttpCircuitRecordFailure();
                                continue;
                            }
                            kofHttpCircuitRecordSuccess();
                            return resp.body() != null ? resp.body() : "";
                        }
                        // Fallback assíncrono real (Node/Browser): fetch devolve
                        // Promise — propaga pelo kofSpawnResult (.then encadeia)
                        // e resolve no await. Chamada síncrona sem await recebe o
                        // Promise cru (face síncrona JS não existe: HTTP004).
                        if (typeof fetch !== 'undefined') {
                            const h = {};
                            if (headers) {
                                const lines = headers.split("\\n");
                                for (const line of lines) {
                                    const i = line.indexOf(":");
                                    if (i > 0) h[line.substring(0, i).trim()] = line.substring(i + 1).trim();
                                }
                            }
                            const ac = new AbortController();
                            const timer = setTimeout(() => ac.abort(), (kofHttpTimeoutSec || 30) * 1000);
                            return fetch(url, { method, headers: h, body: body != null ? body : undefined, signal: ac.signal })
                                .then(r => r.text().then(b => [r.status, b]))
                                .then(([status, bodyText]) => {
                                    clearTimeout(timer);
                                    if (status >= 500) {
                                        kofHttpCircuitRecordFailure();
                                        throw new Error("HTTP " + status + " from " + url);
                                    }
                                    kofHttpCircuitRecordSuccess();
                                    return bodyText;
                                }, err => { clearTimeout(timer); kofHttpCircuitRecordFailure(); throw err; });
                        }
                        throw new Error("kof.http: no transport (sem Java interop nem fetch)");
                    } catch(e) {
                        lastErr = e;
                        kofHttpCircuitRecordFailure();
                    }
                }
                if (lastErr == null) lastErr = new Error("request failed: " + url);
                throw lastErr;
            }

            export function kofSchedulerEvery(ms, fn) {
                // delega ao kofTimeInterval: fila cooperativa bombeada por
                // kofTimeSleep no GraalJS (sem setInterval), nativa no browser
                return kofTimeInterval(ms, fn);
            }
            export function kofSchedulerAt(cron, fn) {
                // CRON001: cron real (5 campos, UTC) — a fila cooperativa do
                // kofTimeJobs recalcula o próximo disparo a cada execução.
                return kofTimeScheduleCron(cron, fn);
            }
            export function kofSchedulerCancel(id) { kofTimeCancel(id); }

            """;
    }
}
