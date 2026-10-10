package dev.kof.compiler.js;

/** kof-runtime.mjs — web server (WEB001) + timers cooperativos. */
public final class JsRuntimeUiWeb {
    private JsRuntimeUiWeb() {
    }

    // §176: inicializado via método (NÃO literal constante) para que o valor
    // NÃO seja inlined/constant-folded nos consumidores (JsRuntimeSlices). Um
    // `static final` literal embute a string no .class do Slices, e o rebuild
    // incremental não recompila dependentes quando só a fonte-mestre muda —
    // o slice quebrado antigo sobrevive no gerador. getstatic lê o valor vivo.
    static final String UI_WEB_RUNTIME = uiWebRuntime() + JsRuntimeTime.calendarRuntime() + JsRuntimeTime.timeRuntime() + JsRuntimeCron.cronRuntime();

    private static String uiWebRuntime() {
        return """
            // ── Web runtime (WEB001) — GraalJS HttpServer com handler invoke
            // Handler lambda tem metodo invoke(); usamos Value para interop.
            const kofWebApps = new Map();
            let kofWebPort = 8080;
            let kofWebServer = null;
            let kofWebRunning = false;
            // WEB001-T1: fila criada LAZY no primeiro kofWebListen — o slice
            // ui-web é incluído por fallback em programas SEM web e a criação
            // top-level (Java.type) quebraria a execução desses programas.
            let kofWebPending = null;
            // WEB001-T1 (13/09): request corrente p/ os helpers de contexto
            // Kof (param/query/header/body/method/path). GraalJS executa o
            // handler numa thread por dispatch — o HttpServer default roda
            // o handler sequencialmente no dispatcher thread, e o handler
            // Kof roda sincrono dentro dele: contexto corrente e seguro.
            let kofWebRequest = null;

            // ── WEB001-T1: encoder UTF-8 puro JS (interop de INSTÂNCIA Java
            // host não expõe métodos nesta build — statics e Java.to ok) ──
            function kofWebBytesToUtf8(raw) {
                if (raw == null || raw.length === 0) return "";
                const out = [];
                for (let i = 0; i < raw.length; i++) {
                    const b = raw[i] & 0xFF;
                    out.push(b);
                }
                let result = "";
                let i = 0;
                while (i < out.length) {
                    const b = out[i];
                    let cp = 0, len = 0;
                    if (b < 0x80) { cp = b; len = 1; }
                    else if ((b & 0xE0) === 0xC0) { cp = b & 0x1F; len = 2; }
                    else if ((b & 0xF0) === 0xE0) { cp = b & 0x0F; len = 3; }
                    else { cp = b & 0x07; len = 4; }
                    for (let k = 1; k < len && i + k < out.length; k++) cp = (cp << 6) | (out[i + k] & 0x3F);
                    i += len;
                    result += String.fromCodePoint(cp);
                }
                return result;
            }

            function kofWebUtf8Bytes(text) {
                const out = [];
                for (let i = 0; i < text.length; i++) {
                    let c = text.charCodeAt(i);
                    if (c >= 0xD800 && c <= 0xDBFF && i + 1 < text.length) {
                        const c2 = text.charCodeAt(i + 1);
                        if (c2 >= 0xDC00 && c2 <= 0xDFFF) { c = 0x10000 + ((c - 0xD800) << 10) + (c2 - 0xDC00); i++; }
                    }
                    if (c < 0x80) out.push(c);
                    else if (c < 0x800) { out.push(0xC0 | (c >> 6), 0x80 | (c & 63)); }
                    else if (c < 0x10000) { out.push(0xE0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63)); }
                    else { out.push(0xF0 | (c >> 18), 0x80 | ((c >> 12) & 63), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63)); }
                }
                return Java.to(out, "byte[]");
            }
            function kofWebHandleRequest(exchange) {
                const path = exchange.getRequestURI().getPath();
                const method = exchange.getRequestMethod();
                // WEB001-T1 (13/09): match em TODOS os apps registrados — rotas
                // vivem em app.handlers (chave "METHOD:path"), idem JVM dispatch.
                let handler = null;
                let matched = null;
                for (const [, app] of kofWebApps) {
                    if (handler) break;
                    handler = app.handlers.get(method + ":" + path);
                    if (handler) break;
                    for (const [key, h] of app.handlers) {
                        const sep = key.indexOf(":");
                        const m = key.slice(0, sep);
                        const r = key.slice(sep + 1);
                        if (m !== method || !r.includes(":")) continue;
                        const rSegs = r.split("/"), pSegs = path.split("/");
                        if (rSegs.length !== pSegs.length) continue;
                        const params = new Map();
                        let ok = true;
                        for (let i = 0; i < rSegs.length; i++) {
                            if (rSegs[i].startsWith(":")) params.set(rSegs[i].slice(1), decodeURIComponent(pSegs[i]));
                            else if (rSegs[i] !== pSegs[i]) { ok = false; break; }
                        }
                        if (ok) { handler = h; matched = params; break; }
                    }
                }
                if (!handler) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                // WEB001 SSE (16/09): rotas sse vivem embrulhadas — o wrapper
                // carrega o handler real + flag; matching ignora o método
                // (idem JVM: SSE só responde GET).
                let sseRoute = false;
                if (handler && handler.__sse === true) {
                    sseRoute = true;
                    handler = handler.handler;
                }
                try {
                    // WEB001-T1 (13/09): body = stream do request lido inteiro
                    // e decodificado UTF-8 (métodos de instância em objetos
                    // host RECEBIDOS funcionam; a criação new JString() dentro
                    // do JS não expõe membros nesta build — por isso o decoder
                    // puro JS kofWebUtf8Bytes para a resposta).
                    let body = null;
                    {
                        const is = exchange.getRequestBody();
                        const raw = is.readAllBytes();
                        body = kofWebBytesToUtf8(raw);
                        is.close();
                    }
                    kofWebRequest = {
                        method: method,
                        path: path,
                        query: exchange.getRequestURI().getQuery(),
                        headers: exchange.getRequestHeaders(),
                        body: body,
                        paramsMap: matched || new Map(),
                        // §265 (JS): status/headerSet DEFERIDOS idem JVM
                        // (KOF_WEB_STATUS/KOF_WEB_HEADERS thread-local) — o
                        // handler faz `return status(201, body)`; o pump aplica
                        // no envio. O desenho antigo (response.status escrevendo
                        // na hora) nunca funcionava: `kofWebRequest` nao tinha o
                        // campo `response`, e o guard engolia a chamada (R6).
                        _status: null,
                        _headerQueue: []
                    };
                    const ctx = {
                        request: {
                            method: method,
                            path: path,
                            query: exchange.getRequestURI().getQuery(),
                            headers: exchange.getRequestHeaders()
                        },
                        body: body,
                        // §265 (JS): response.status/header DEFERIDOS idem
                        // kofWebStatus/kofWebHeaderSet — escrevia na hora e o
                        // pump escrevia DE NOVO (sendResponseHeaders 2x =
                        // erro; o branch `result.status === 'function'` no
                        // pump nunca era casado — o status() real devolvia
                        // String). O handler Kof usa status()/headerSet()
                        // documentados; ctx.response fica consistente.
                        response: {
                            status: function(code, text) {
                                kofWebRequest._status = Number(code);
                                return text == null ? "" : String(text);
                            },
                            header: function(name, value) {
                                kofWebRequest._headerQueue.push([String(name), String(value)]);
                                return String(value);
                            }
                        }
                    };
                    // WEB001-T1 (13/09): idem JVM (JvmRuntimeWebDispatch) — o
                    // RETORNO do handler é o body 200; null/undefined → 404.
                    if (sseRoute) {
                        // WEB001 SSE (16/09): HANDLER-SCOPED. O pump JS é
                        // single-thread — o stream vive DURANTE o corpo do
                        // handler (send/event flusham por evento, idem JVM
                        // writeFrame) e fecha no retorno. Push pós-return ou
                        // de outro handler = WEB003 (não há scheduler-side
                        // sender no JS); sse(text) fora de rota já falha em
                        // compile-time (contextJsSupported 16/09).
                        const conn = kofWebSseMakeConn(exchange);
                        kofWebSseConn = conn;
                        try {
                            if (typeof handler.invoke === 'function') handler.invoke(conn);
                            else if (typeof handler === 'function') handler(conn);
                        } catch (se) {
                            // idem JVM (JvmRuntimeWebServer): erro do handler
                            // SSE vai p/ stderr e o stream fecha — nunca 500
                            // (os headers do stream já foram enviados).
                            console.log("kof web sse handler error: " + se);
                        } finally {
                            kofWebSseConn = null;
                            conn.close();
                        }
                        return;
                    }
                    const result = (typeof handler.invoke === 'function') ? handler.invoke(ctx)
                                 : (typeof handler === 'function' ? handler(ctx) : undefined);
                    // §265 (JS): aplica os headers deferidos ANTES do envio
                    // (idem JVM — KOF_WEB_HEADERS lido no write) e o status
                    // deferido; sem status() o default é 200 (ou 404 se o
                    // handler retornou null).
                    for (const hv of kofWebRequest._headerQueue) {
                        exchange.getResponseHeaders().set(hv[0], hv[1]);
                    }
                    if (result === null || result === undefined) {
                        const code404 = kofWebRequest._status != null ? kofWebRequest._status : 404;
                        const nf = '{"error": "not found"}';
                        exchange.sendResponseHeaders(code404, kofWebUtf8Bytes(nf).length);
                        const os0 = exchange.getResponseBody();
                        os0.write(kofWebUtf8Bytes(nf));
                        os0.close();
                    } else {
                        const text = String(result);
                        const bytes = kofWebUtf8Bytes(text);
                        const code = kofWebRequest._status != null ? kofWebRequest._status : 200;
                        exchange.sendResponseHeaders(code, bytes.length);
                        const os1 = exchange.getResponseBody();
                        os1.write(bytes);
                        os1.close();
                    }
                } catch(e) {
                    console.log("kofWeb handler error: " + e);
                    const msg = String(e);
                    exchange.sendResponseHeaders(500, msg.length);
                    const os = exchange.getResponseBody();
                    os.write(kofWebUtf8Bytes(msg));
                    os.close();
                } finally {
                    kofWebRequest = null;
                }
            }

            // ── WEB001-T1: helpers de contexto (idem JVM kof_web_*) ──
            export function kofWebParam(name) {
                if (!kofWebRequest) return "";
                const v = kofWebRequest.paramsMap.get(String(name));
                return v === undefined ? "" : v;
            }
            export function kofWebQuery(name) {
                if (!kofWebRequest || !kofWebRequest.query) return null;
                for (const kv of String(kofWebRequest.query).split("&")) {
                    const eq = kv.indexOf("=");
                    if (eq > 0 && kv.slice(0, eq) === String(name))
                        return decodeURIComponent(kv.slice(eq + 1));
                }
                return null;
            }
            export function kofWebHeader(name) {
                if (!kofWebRequest) return null;
                const v = kofWebRequest.headers.getFirst(String(name));
                return v === undefined || v === null ? null : String(v);
            }
            export function kofWebBody() {
                return kofWebRequest && kofWebRequest.body != null ? String(kofWebRequest.body) : "";
            }
            export function kofWebMethod() {
                return kofWebRequest ? kofWebRequest.method : "";
            }
            export function kofWebPath() {
                return kofWebRequest ? kofWebRequest.path : "";
            }
            export function kofWebStatus(code, text) {
                // §265 (JS): defere — o pump aplica no envio (idem JVM:
                // KOF_WEB_STATUS.set + retorna o body p/ uso como retorno).
                if (kofWebRequest) kofWebRequest._status = Number(code);
                return text == null ? "" : String(text);
            }
            export function kofWebHeaderSet(name, value) {
                // §265 (JS): defere p/ antes do sendResponseHeaders (o JDK
                // HttpServer exige headers ANTES do envio; idem JVM).
                if (kofWebRequest) kofWebRequest._headerQueue.push([String(name), String(value)]);
                return value == null ? "" : String(value);
            }
            export function kofWebAppNew() {
                const app = {
                    handlers: new Map(),
                    _register: function(method, path, handler) {
                        this.handlers.set(method + ":" + path, handler);
                    }
                };
                const id = "app_" + kofWebApps.size;
                kofWebApps.set(id, app);
                return id;
            }
            export function kofWebRoute(appId, method, path, handler) {
                const app = kofWebApps.get(appId);
                if (!app) throw new Error("Invalid app handle: " + appId);
                app._register(method, path, handler);
                return 0;
            }
            export function kofWebListen(appId, port) {
                const app = kofWebApps.get(appId);
                if (!app) throw new Error("Invalid app handle: " + appId);
                if (kofWebPending === null) {
                    kofWebPending = new (Java.type('java.util.concurrent.LinkedBlockingQueue'))();
                }
                kofWebPort = port | 0 || 8080;
                const HttpServer = Java.type('com.sun.net.httpserver.HttpServer');
                const InetSocketAddress = Java.type('java.net.InetSocketAddress');
                kofWebServer = HttpServer.create(new InetSocketAddress(kofWebPort), 0);
                for (const [key, handler] of app.handlers) {
                    const [method, path] = key.split(":");
                    // WEB001-T1 (13/09): Context GraalJS é thread-confined — o callback do
            // HttpServer roda na thread do dispatcher e NÃO pode executar JS.
            // O dispatcher só ENFILEIRA o exchange (Java host-side); kofWebListen
            // desfile e processa cada request na main thread (event-loop).
                            // WEB001-T1: handler 100% Java (KofJsWebQueue) — callback JS rodaria
                // na thread do dispatcher e o Context GraalJS é thread-confined.
                // HttpServer não tem matching de :params — registra o PREFIXO
                // estático (ex.: "/u/:id" → "/u/"); match real no kofWebHandleRequest.
                const QueueHandler = Java.type('dev.kof.runtime.KofJsWebQueue');
                let ctxPath = path;
                if (ctxPath.includes(":")) {
                    ctxPath = ctxPath.slice(0, ctxPath.indexOf(":"));
                    if (!ctxPath.endsWith("/")) ctxPath = ctxPath + "/";
                }
                kofWebServer.createContext(ctxPath, new QueueHandler(kofWebPending));
                }
                kofWebServer.setExecutor(null);
                kofWebServer.start();
                // WEB001-T1 (13/09): idem JVM — listen é BLOQUEANTE. O contexto
                // GraalJS é thread-confined: requests processados AQUI (main
                // thread) a partir da fila que o dispatcher enche.
                kofWebRunning = true;
                const Thread_ = Java.type('java.lang.Thread');
                while (kofWebRunning) {
                    let ex = null;
                    try {
                        ex = kofWebPending.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (ie) {
                        continue;
                    }
                    if (ex !== null) kofWebHandleRequest(ex);
                }
                return 0;
            }

            export function kofEnumValueOf(values, name) {
                if (values != null && name != null) {
                    for (const v of values) {
                        if (v === name) return v;
                    }
                }
                return null;
            }

            export function kofEnumOrdinal(name, values) {
                if (values != null && name != null) {
                    return values.indexOf(name);
                }
                return -1;
            }

            export function kofNow() {
                return Date.now();
            }

            """;
    }

}
