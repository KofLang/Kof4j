package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.web (native web stack: WebApp/router/middleware/static/serve) - parte 1/2 de JvmWebRuntime. Concatenacao preserva byte-a-byte.
 */
public final class JvmWebCoreRuntime {

    private JvmWebCoreRuntime() {}

    static String source() {
        return """
                // ── kof.web — native web stack ────────────────────

                private static final java.util.concurrent.ConcurrentHashMap<String, WebApp> KOF_WEB_APPS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_WEB_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();
                private static final ThreadLocal<WebRequest> KOF_WEB_REQUEST = new ThreadLocal<>();
                private static final ThreadLocal<Integer> KOF_WEB_STATUS = new ThreadLocal<>();
                private static final ThreadLocal<java.util.Map<String, String>> KOF_WEB_HEADERS =
                        ThreadLocal.withInitial(java.util.HashMap::new);
                // #1012: Set-Cookie REPETE em vez de substituir (RFC 6265 §3,
                // qualquer caixa) — os valores múltiplos ficam FORA do map
                // last-wins; o build escreve uma linha por valor.
                private static final ThreadLocal<java.util.List<String[]>> KOF_WEB_SET_COOKIES =
                        ThreadLocal.withInitial(java.util.ArrayList::new);
                // D-SEC C18: headers de resposta do middleware de security
                // (CSP/HSTS/CORS/Set-Cookie) — separados de KOF_WEB_HEADERS
                // porque o dispatch limpa estes ANTES de invocar a rota; estes
                // sobrevivem até o build da resposta.
                private static final ThreadLocal<java.util.Map<String, String>> KOF_SEC_RESPONSE_HEADERS =
                        ThreadLocal.withInitial(java.util.LinkedHashMap::new);

                public static final java.util.concurrent.atomic.AtomicLong SSE_CONNECTIONS_ACTIVE =
                        new java.util.concurrent.atomic.AtomicLong();
                public static final java.util.concurrent.atomic.AtomicLong WS_CONNECTIONS_ACTIVE =
                        new java.util.concurrent.atomic.AtomicLong();
                public static final java.util.concurrent.atomic.AtomicLong SSE_EVENTS_SENT =
                        new java.util.concurrent.atomic.AtomicLong();
                public static final java.util.concurrent.atomic.AtomicLong WS_MESSAGES_RECEIVED =
                        new java.util.concurrent.atomic.AtomicLong();
                public static final java.util.concurrent.atomic.AtomicLong WS_MESSAGES_SENT =
                        new java.util.concurrent.atomic.AtomicLong();

                public static String kof_web_status(int code, String body) {
                    KOF_WEB_STATUS.set(code);
                    return body;
                }

                public static String kof_web_header_set(String name, String value) {
                    // #1012: CR/LF num nome ou valor nunca chega ao fio (RFC
                    // 9112 §5) — recusar AQUI (o catch do dispatch responde
                    // 500) em vez de escrever uma linha injetada. A mensagem
                    // NÃO ecoa o valor: um payload CRLF num diagnóstico é
                    // log-injection.
                    if (name != null && (name.indexOf('\\r') >= 0 || name.indexOf('\\n') >= 0)) {
                        throw new IllegalArgumentException(
                                "header name must not contain CR/LF");
                    }
                    if (value != null && (value.indexOf('\\r') >= 0 || value.indexOf('\\n') >= 0)) {
                        throw new IllegalArgumentException(
                                "header value must not contain CR/LF");
                    }
                    // #1012: Set-Cookie REPETE em vez de substituir (qualquer
                    // caixa, RFC 6265 §3); todo outro header fica last-wins.
                    if (name != null && name.equalsIgnoreCase("Set-Cookie")) {
                        KOF_WEB_SET_COOKIES.get().add(new String[]{name, value});
                        return value;
                    }
                    KOF_WEB_HEADERS.get().put(name, value);
                    return value;
                }

                private static String kof_web_status_text(int code) {
                    return switch (code) {
                        case 200 -> "OK";
                        case 201 -> "Created";
                        case 202 -> "Accepted";
                        case 204 -> "No Content";
                        case 301 -> "Moved Permanently";
                        case 302 -> "Found";
                        case 304 -> "Not Modified";
                        case 400 -> "Bad Request";
                        case 401 -> "Unauthorized";
                        case 403 -> "Forbidden";
                        case 404 -> "Not Found";
                        case 409 -> "Conflict";
                        case 422 -> "Unprocessable Entity";
                        case 429 -> "Too Many Requests";
                        case 500 -> "Internal Server Error";
                        case 502 -> "Bad Gateway";
                        case 503 -> "Service Unavailable";
                        default -> "OK";
                    };
                }

                public enum RouteKind { HTTP, SSE, WS }

                record WebDispatchResult(RouteKind kind, String response, WebRoute route, byte[] body) {
                    WebDispatchResult(RouteKind kind, String response, WebRoute route) {
                        this(kind, response, route, null);
                    }
                }

                public static final class WebRoute {
                    final String method;
                    final String path;
                    final String[] segments;
                    final boolean[] params;
                    final Object handler;
                    final RouteKind kind;
                    // D-HTTP-POLICIES (F3): opts por endpoint (app.get(path, opts)
                    // { }) — null para rotas sem opts (herdam escopo/global).
                    final Policy policy;

                    WebRoute(RouteKind kind, String method, String path, Object handler) {
                        this(kind, method, path, handler, null);
                    }
                    WebRoute(RouteKind kind, String method, String path, Object handler, Policy policy) {
                        this.kind = kind;
                        this.method = method;
                        this.path = path;
                        String[] raw = path.split("/");
                        this.segments = new String[raw.length];
                        this.params = new boolean[raw.length];
                        for (int i = 0; i < raw.length; i++) {
                            this.segments[i] = raw[i];
                            this.params[i] = raw[i].startsWith(":");
                        }
                        this.handler = handler;
                        this.policy = policy;
                    }

                    /** §798: at the first differing segment, a literal beats a :param. */
                    static void addTo(java.util.List<WebRoute> routes, WebRoute r) {
                        int at = routes.size();
                        for (int i = 0; i < routes.size() && at == routes.size(); i++) {
                            boolean[] o = routes.get(i).params;
                            if (o.length != r.params.length) continue;
                            for (int k = 0; k < o.length; k++) {
                                if (o[k] != r.params[k]) { if (o[k]) at = i; break; }
                            }
                        }
                        routes.add(at, r);
                    }
                }

                public static final class WebRequest {
                    final String method;
                    final String path;
                    final String query;
                    final String rawHeaders;
                    final String body;
                    final String remoteAddr;
                    final boolean secure;
                    final java.util.Map<String, String> params = new java.util.HashMap<>();
                    final java.util.Map<String, String> headers = new java.util.HashMap<>();

                    WebRequest(String method, String path, String query, String rawHeaders, String body) {
                        this(method, path, query, rawHeaders, body, null, false);
                    }

                    WebRequest(String method, String path, String query, String rawHeaders, String body,
                            String remoteAddr, boolean secure) {
                        this.method = method;
                        this.path = path;
                        this.query = query;
                        this.rawHeaders = rawHeaders;
                        this.body = body;
                        this.remoteAddr = remoteAddr;
                        this.secure = secure;
                        String[] lines = rawHeaders.split("\\r\\n");
                        for (int i = 1; i < lines.length; i++) {
                            int colon = lines[i].indexOf(':');
                            if (colon > 0) {
                                headers.put(lines[i].substring(0, colon).trim().toLowerCase(),
                                        lines[i].substring(colon + 1).trim());
                            }
                        }
                    }

                    String param(String name) {
                        return params.get(name);
                    }

                    // Paridade JVM x JS (§2.4/§12 do pagination-plan): o JS usa
                    // decodeURIComponent no VALOR; a chave e comparada CRUA e
                    // so pares com '=' (eq > 0) casam. Antes o JVM devolvia o
                    // valor cru (`?name=a%20b` -> "a%20b") enquanto o JS devolvia
                    // "a b" — divergencia silenciosa de cross-target.
                    String query(String name) {
                        if (query == null || query.isEmpty()) return null;
                        String key = String.valueOf(name);
                        for (String kv : query.split("&")) {
                            int eq = kv.indexOf('=');
                            if (eq > 0 && kv.substring(0, eq).equals(key)) {
                                return decodeQueryComponent(kv.substring(eq + 1));
                            }
                        }
                        return null;
                    }

                    /** Espelha decodeURIComponent: `%XX` UTF-8 estrito; `+`
                     *  permanece literal (ao contrario de form-urlencoded);
                     *  sequencia malformada = URIError no JS -> aqui IllegalArgumentException. */
                    static String decodeQueryComponent(String s) {
                        if (s == null || s.indexOf('%') < 0) return s;
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length());
                        for (int i = 0; i < s.length(); i++) {
                            char c = s.charAt(i);
                            if (c == '%') {
                                if (i + 2 >= s.length()) throw new IllegalArgumentException("URI malformed");
                                int hi = Character.digit(s.charAt(i + 1), 16);
                                int lo = Character.digit(s.charAt(i + 2), 16);
                                if (hi < 0 || lo < 0) throw new IllegalArgumentException("URI malformed");
                                out.write((hi << 4) | lo);
                                i += 2;
                            } else {
                                byte[] b = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                out.write(b, 0, b.length);
                            }
                        }
                        try {
                            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                    .decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString();
                        } catch (java.nio.charset.CharacterCodingException e) {
                            throw new IllegalArgumentException("URI malformed", e);
                        }
                    }

                    String header(String name) {
                        return headers.get(name.toLowerCase());
                    }
                }

                public static final class SseConnection implements SseSender {
                    private final java.io.OutputStream out;
                    private final byte[] nl = "\\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    private final java.util.concurrent.atomic.AtomicBoolean open =
                            new java.util.concurrent.atomic.AtomicBoolean(true);
                    private final java.util.concurrent.locks.ReentrantLock writeLock =
                            new java.util.concurrent.locks.ReentrantLock();

                    SseConnection(java.io.OutputStream out) {
                        this.out = out;
                    }

                    public void send(String data) {
                        if (!open.get()) return;
                        SSE_EVENTS_SENT.incrementAndGet();
                        writeData(data);
                    }

                    public void event(String name, String data) {
                        if (!open.get()) return;
                        SSE_EVENTS_SENT.incrementAndGet();
                        writeFrame("event: " + name + "\\n");
                        writeData(data);
                    }

                    public void close() {
                        if (open.compareAndSet(true, false)) {
                            writeLock.lock();
                            try {
                                out.flush();
                                out.close();
                            } catch (java.io.IOException ignored) {
                            } finally {
                                writeLock.unlock();
                            }
                        }
                    }

                    public boolean isOpen() {
                        return open.get();
                    }

                    private void writeData(String data) {
                        for (String line : data.split("\\n", -1)) {
                            writeFrame("data: " + line + "\\n");
                        }
                        writeFrame("\\n");
                    }

                    private void writeFrame(String frame) {
                        if (!open.get()) return;
                        writeLock.lock();
                        try {
                            out.write(frame.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            out.flush();
                        } catch (java.io.IOException e) {
                            open.set(false);
                        } finally {
                            writeLock.unlock();
                        }
                    }
                }

                /** Interface para o KofRuntime acessar o envio SSE sem ciclo de import. */
                public interface SseSender {
                    void send(String event);
                }

                public static final class WsConnection implements WsSender {
                    private final java.io.OutputStream out;
                    private final java.util.concurrent.locks.ReentrantLock writeLock =
                            new java.util.concurrent.locks.ReentrantLock();

                    WsConnection(java.io.OutputStream out) {
                        this.out = out;
                    }

                    public void sendText(String s) {
                        send(WsFrame.encode(0x1, s.getBytes(java.nio.charset.StandardCharsets.UTF_8), true));
                    }
                    public void sendBinary(byte[] payload) {
                        send(WsFrame.encode(0x2, payload, true));
                    }
                    public void ping(byte[] payload) {
                        if (payload.length > 125) throw new IllegalArgumentException("ping payload > 125");
                        send(WsFrame.encode(0x9, payload, true));
                    }
                    public void pong(byte[] payload) {
                        if (payload.length > 125) throw new IllegalArgumentException("pong payload > 125");
                        send(WsFrame.encode(0xA, payload, true));
                    }
                    public void close(int code, String reason) {
                        byte[] body = new byte[2 + (reason == null ? 0 : reason.length())];
                        body[0] = (byte) ((code >> 8) & 0xFF);
                        body[1] = (byte) (code & 0xFF);
                        if (reason != null) {
                            byte[] rb = reason.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            System.arraycopy(rb, 0, body, 2, rb.length);
                        }
                        send(WsFrame.encode(0x8, body, true));
                    }

                    private void send(byte[] frame) {
                        writeLock.lock();
                        try {
                            out.write(frame);
                            out.flush();
                        } catch (java.io.IOException e) {
                            // close silently
                        } finally {
                            writeLock.unlock();
                        }
                    }
                }

                public static final class WebApp {
                    final String id;
                    final java.util.List<WebRoute> routes = new java.util.ArrayList<>();
                    final java.util.List<Object> middlewares = new java.util.ArrayList<>();
                    // C18 (D-SEC): config do app.security() aplicada pelo
                    // dispatch — ordem fixa rate-limit → cors → headers →
                    // session → csrf → auth → RBAC, sempre antes das rotas
                    // (security by default não depende do usuário lembrar de
                    // compor). União das duas lanes que implementaram C18.
                    volatile boolean securityConfigured;
                    // D-HTTP-POLICIES (F1): politica global parseada de
                    // app.security(opts) — substitui os campos security* flat.
                    // #1112: o global inicial é SEM hardening (opt-in pela
                    // app.security() — a decisão 05/11); app.security() substitui
                    // o policy inteiro (headers ON por default lá).
                    volatile Policy globalPolicy = Policy.noHardening();
                    // D-HTTP-POLICIES (F2): policies de recurso de
                    // app.policy(prefix, opts); resolvidas por longest-prefix
                    // sobre o global a cada request (kof_web_effective_policy).
                    final java.util.List<ScopedPolicy> policies =
                            new java.util.concurrent.CopyOnWriteArrayList<>();
                    final java.util.List<StaticDir> staticDirs = new java.util.ArrayList<>();
                    final java.util.List<String> healthPaths = new java.util.ArrayList<>();
                    final java.util.concurrent.atomic.AtomicInteger activeConnections =
                            new java.util.concurrent.atomic.AtomicInteger();
                    public static final int DEFAULT_MAX_CONNECTIONS = 1024;
                    volatile int maxConnections = DEFAULT_MAX_CONNECTIONS;
                    volatile java.net.ServerSocket serverSocket;
                    volatile boolean running;

                    WebApp(String id) {
                        this.id = id;
                    }

                    /** Diretório de arquivos estáticos servido sob um prefixo
                     *  de URL (app.serveDir("/img", "assets")) — content-type
                     *  derivado da extensão, sem o app colar base64 em String. */
                    public static final class StaticDir {
                        final String prefix;
                        final java.nio.file.Path dir;
                        StaticDir(String prefix, java.nio.file.Path dir) {
                            this.prefix = prefix;
                            this.dir = dir;
                        }
                    }
                }

                public static String kof_web_app_new() {
                    String id = "app" + KOF_WEB_SEQ.incrementAndGet();
                    KOF_WEB_APPS.put(id, new WebApp(id));
                    return id;
                }

                private static WebApp kof_web_app(String appId) {
                    WebApp app = KOF_WEB_APPS.get(appId);
                    if (app == null) throw new IllegalArgumentException("unknown web app: " + appId);
                    return app;
                }

                public static void kof_web_configure(String appId, String key, Object value) {
                    WebApp app = kof_web_app(appId);
                    switch (key) {
                        case "maxConnections" -> app.maxConnections = ((Number) value).intValue();
                        case "maxFrameBytes" -> WsFrame.maxFrameBytes.set(((Number) value).longValue());
                        case "maxMessageBytes" -> WsFrame.maxMessageBytes.set(((Number) value).longValue());
                        case "idleMs" -> KofRuntime.idleMs.set(((Number) value).intValue());
                        default -> throw new IllegalArgumentException("unknown config key: " + key);
                    }
                }

                public static void kof_web_configure(String appId, String key, int value) {
                    kof_web_configure(appId, key, (Object) value);
                }

                public static String kof_web_stats(String name) {
                    return switch (name) {
                        case "SSE_CONNECTIONS_ACTIVE" -> String.valueOf(SSE_CONNECTIONS_ACTIVE.get());
                        case "WS_CONNECTIONS_ACTIVE" -> String.valueOf(WS_CONNECTIONS_ACTIVE.get());
                        case "SSE_EVENTS_SENT" -> String.valueOf(SSE_EVENTS_SENT.get());
                        case "WS_MESSAGES_RECEIVED" -> String.valueOf(WS_MESSAGES_RECEIVED.get());
                        case "WS_MESSAGES_SENT" -> String.valueOf(WS_MESSAGES_SENT.get());
                        default -> throw new IllegalArgumentException("unknown web counter: " + name);
                    };
                }

                public static void kof_web_route(String appId, String method, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    String m = method.toUpperCase();
                    if ("SSE".equals(m) || "WS".equals(m)) {
                        throw new IllegalArgumentException(
                                "route method " + m + " requires kof_web_sse_route/kof_web_ws_route");
                    }
                    WebRoute.addTo(kof_web_app(appId).routes, new WebRoute(RouteKind.HTTP, m, path, handler));
                }

                /** D-HTTP-POLICIES (F3): {@code app.get(path, opts) { }} — rota
                 *  HTTP com policy por endpoint; casa mais profundo que os
                 *  escopos e o global (merge §4.3). csrfDefault=false (herda). */
                public static void kof_web_route_opts(String appId, String method, String path,
                        java.util.Map<?, ?> opts, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    String m = method.toUpperCase();
                    if ("SSE".equals(m) || "WS".equals(m)) {
                        throw new IllegalArgumentException(
                                "route method " + m + " requires kof_web_sse_route/kof_web_ws_route");
                    }
                    WebRoute.addTo(kof_web_app(appId).routes, new WebRoute(RouteKind.HTTP, m, path, handler,
                            Policy.parse(opts, false)));
                }

                public static void kof_web_sse_route(String appId, String method, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    WebRoute.addTo(kof_web_app(appId).routes, new WebRoute(RouteKind.SSE, method.toUpperCase(), path, handler));
                }

                public static void kof_web_ws_route(String appId, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    WebRoute.addTo(kof_web_app(appId).routes, new WebRoute(RouteKind.WS, "WS", path, handler));
                }

                public static void kof_web_use(String appId, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("middleware is null");
                    kof_web_app(appId).middlewares.add(handler);
                }

                public static int kof_web_port(String appId) {
                    java.net.ServerSocket ss = kof_web_app(appId).serverSocket;
                    return ss == null ? -1 : ss.getLocalPort();
                }

                public static void kof_web_close(String appId) {
                    WebApp app = kof_web_app(appId);
                    app.running = false;
                    if (app.serverSocket != null) {
                        try {
                            app.serverSocket.close();
                        } catch (java.io.IOException ignored) {
                        }
                    }
                }

""";
    }
}
