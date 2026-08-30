package dev.kof.compiler;

import java.util.List;

/**
 * Runtime do kof.web + kof.http — gerado no KofRuntime junto com o
 * JvmRuntime. Separado num arquivo próprio porque o constant pool do
 * javac limita cada string a 65535 bytes.
 */
final class JvmWebRuntime {

    private JvmWebRuntime() {}

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

                public static String kof_web_status(int code, String body) {
                    KOF_WEB_STATUS.set(code);
                    return body;
                }

                public static String kof_web_header_set(String name, String value) {
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
                        case 500 -> "Internal Server Error";
                        case 502 -> "Bad Gateway";
                        case 503 -> "Service Unavailable";
                        default -> "OK";
                    };
                }

                public static String wsAccept(String secWebSocketKey) {
                    try {
                        String concat = secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                        java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
                        byte[] hash = sha1.digest(concat.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        return java.util.Base64.getEncoder().encodeToString(hash);
                    } catch (Exception e) {
                        throw new RuntimeException("SHA-1 unavailable on JVM", e);
                    }
                }

                public enum RouteKind { HTTP, SSE, WS }

                record WebDispatchResult(RouteKind kind, String response, WebRoute route) {}

                public static final class WebRoute {
                    final String method;
                    final String[] segments;
                    final boolean[] params;
                    final Object handler;
                    final RouteKind kind;

                    WebRoute(RouteKind kind, String method, String path, Object handler) {
                        this.kind = kind;
                        this.method = method;
                        String[] raw = path.split("/");
                        this.segments = new String[raw.length];
                        this.params = new boolean[raw.length];
                        for (int i = 0; i < raw.length; i++) {
                            this.segments[i] = raw[i];
                            this.params[i] = raw[i].startsWith(":");
                        }
                        this.handler = handler;
                    }
                }

                public static final class WebRequest {
                    final String method;
                    final String path;
                    final String query;
                    final String rawHeaders;
                    final String body;
                    final java.util.Map<String, String> params = new java.util.HashMap<>();
                    final java.util.Map<String, String> queryParams = new java.util.HashMap<>();
                    final java.util.Map<String, String> headers = new java.util.HashMap<>();

                    WebRequest(String method, String path, String query, String rawHeaders, String body) {
                        this.method = method;
                        this.path = path;
                        this.query = query;
                        this.rawHeaders = rawHeaders;
                        this.body = body;
                        if (!query.isEmpty()) {
                            for (String pair : query.split("&")) {
                                int eq = pair.indexOf('=');
                                if (eq < 0) queryParams.put(pair, "");
                                else queryParams.put(pair.substring(0, eq), pair.substring(eq + 1));
                            }
                        }
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

                    String query(String name) {
                        return queryParams.get(name);
                    }

                    String header(String name) {
                        return headers.get(name.toLowerCase());
                    }
                }

                public static final class SseConnection {
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
                        writeData(data);
                    }

                    public void event(String name, String data) {
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

                public static final class WsFrame {
                    public final boolean fin;
                    public final int opcode;        // 0x1=TEXT, 0x2=BINARY, 0x8=CLOSE, 0x9=PING, 0xA=PONG, 0x0=continuation
                    public final byte[] payload;

                    WsFrame(boolean fin, int opcode, byte[] payload) {
                        this.fin = fin;
                        this.opcode = opcode;
                        this.payload = payload;
                    }

                    /**
                     * Encode a frame WITHOUT masking (server -> client per RFC 6455).
                     * FIN=1 by default; use overload for FIN=0.
                     */
                    public static byte[] encode(int opcode, byte[] payload, boolean fin) {
                        // Header byte 1: FIN(1) RSV(3) OPCODE(4)
                        int b1 = (fin ? 0x80 : 0x00) | (opcode & 0x0F);
                        // Header byte 2: MASK(1) + LEN(7)
                        long len = payload.length;
                        byte[] header;
                        int headerLen;
                        if (len <= 125) {
                            header = new byte[2];
                            header[1] = (byte) len;       // MASK=0
                            headerLen = 2;
                        } else if (len <= 0xFFFF) {
                            header = new byte[4];
                            header[1] = (byte) 126;       // MASK=0
                            header[2] = (byte) ((len >> 8) & 0xFF);
                            header[3] = (byte) (len & 0xFF);
                            headerLen = 4;
                        } else {
                            header = new byte[10];
                            header[1] = (byte) 127;       // MASK=0
                            for (int i = 0; i < 8; i++) {
                                header[2 + i] = (byte) ((len >> (56 - i * 8)) & 0xFF);
                            }
                            headerLen = 10;
                        }
                        header[0] = (byte) b1;
                        byte[] out = new byte[headerLen + (int) len];
                        System.arraycopy(header, 0, out, 0, headerLen);
                        System.arraycopy(payload, 0, out, headerLen, (int) len);
                        return out;
                    }

                    /**
                     * Decode a server-received (client -> server) frame. Payload is UNMASKED in
                     * the returned WsFrame. RFC 6455 requires client->server masking; we reject
                     * (close 1002) frames missing the mask bit.
                     */
                    public static WsFrame decodeClient(byte[] buf) throws java.io.IOException {
                        if (buf.length < 2) throw new java.io.IOException("frame too short: " + buf.length);
                        boolean fin = (buf[0] & 0x80) != 0;
                        int opcode = buf[0] & 0x0F;
                        boolean masked = (buf[1] & 0x80) != 0;
                        if (!masked) throw new java.io.IOException("client frame must be masked (RFC 6455 §5.1)");
                        long len = buf[1] & 0x7F;
                        int idx = 2;
                        if (len == 126) {
                            if (buf.length < 4) throw new java.io.IOException("frame truncated on extended length");
                            len = ((long)(buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
                            idx = 4;
                        } else if (len == 127) {
                            if (buf.length < 10) throw new java.io.IOException("frame truncated on extended length");
                            len = 0;
                            for (int i = 0; i < 8; i++) {
                                len = (len << 8) | (buf[2 + i] & 0xFF);
                            }
                            idx = 10;
                        }
                        if (len > MAX_FRAME_BYTES) {
                            throw new java.io.IOException("frame too large: " + len + " > " + MAX_FRAME_BYTES);
                        }
                        if (buf.length < idx + 4 + len) throw new java.io.IOException("frame truncated (payload)");
                        byte[] mask = new byte[4];
                        System.arraycopy(buf, idx, mask, 0, 4);
                        idx += 4;
                        byte[] payload = new byte[(int) len];
                        for (long i = 0; i < len; i++) {
                            payload[(int) i] = (byte) (buf[idx + (int) i] ^ mask[(int) (i % 4)]);
                        }
                        return new WsFrame(fin, opcode, payload);
                    }

                    public static final long MAX_FRAME_BYTES = 1L << 20;        // 1 MiB
                    public static final long MAX_MESSAGE_BYTES = 8L << 20;     // 8 MiB
                    public static final int CLOSE_TOO_BIG = 1009;
                    public static final int CLOSE_PROTOCOL_ERROR = 1002;
                    public static final int CLOSE_UNSUPPORTED = 1003;
                    public static final int CLOSE_SERVER_ERROR = 1011;
                }

                public static final class WsConnection {
                    private final java.io.OutputStream out;
                    private final java.util.concurrent.locks.ReentrantLock writeLock =
                            new java.util.concurrent.locks.ReentrantLock();
                    // Lambdas registradas pelo usuário via `ws.onMessage { ... }`
                    // etc. Campos públicos para leitura rápida no frame loop.
                    public Object onOpen;
                    public Object onMessage;
                    public Object onClose;

                    WsConnection(java.io.OutputStream out) {
                        this.out = out;
                    }

                    public void onOpen(Object handler) { this.onOpen = handler; }
                    public void onMessage(Object handler) { this.onMessage = handler; }
                    public void onClose(Object handler) { this.onClose = handler; }

                    public void send(String s) {
                        sendText(s);
                    }
                    public void send(Object value) {
                        sendText(String.valueOf(value));
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
                    volatile java.net.ServerSocket serverSocket;
                    volatile boolean running;

                    WebApp(String id) {
                        this.id = id;
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

                public static void kof_web_route(String appId, String method, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    String m = method.toUpperCase();
                    if ("SSE".equals(m) || "WS".equals(m)) {
                        throw new IllegalArgumentException(
                                "route method " + m + " requires kof_web_sse_route/kof_web_ws_route");
                    }
                    kof_web_app(appId).routes.add(new WebRoute(RouteKind.HTTP, m, path, handler));
                }

                public static void kof_web_sse_route(String appId, String method, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    kof_web_app(appId).routes.add(
                            new WebRoute(RouteKind.SSE, method.toUpperCase(), path, handler));
                }

                public static void kof_web_ws_route(String appId, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    kof_web_app(appId).routes.add(
                            new WebRoute(RouteKind.WS, "WS", path, handler));
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

                // ── kof.http — HTTP client (JDK java.net.http) ───────────
                private static final java.util.concurrent.atomic.AtomicInteger KOF_HTTP_TIMEOUT =
                        new java.util.concurrent.atomic.AtomicInteger(15);

                public static void kof_http_timeout_set(int seconds) {
                    KOF_HTTP_TIMEOUT.set(seconds);
                }

                public static String kof_http_get(String url) throws Exception {
                    return kof_http_request(url, "GET", null, null);
                }

                public static String kof_http_get_headers(String url, String headers) throws Exception {
                    return kof_http_request(url, "GET", headers, null);
                }

                public static String kof_http_delete(String url) throws Exception {
                    return kof_http_request(url, "DELETE", null, null);
                }

                public static String kof_http_delete_headers(String url, String headers) throws Exception {
                    return kof_http_request(url, "DELETE", headers, null);
                }

                public static String kof_http_options(String url) throws Exception {
                    return kof_http_request(url, "OPTIONS", null, null);
                }

                public static String kof_http_options_headers(String url, String headers) throws Exception {
                    return kof_http_request(url, "OPTIONS", headers, null);
                }

                public static String kof_http_post(String url, String body) throws Exception {
                    return kof_http_request(url, "POST", null, body);
                }

                public static String kof_http_post_headers(String url, String body, String headers) throws Exception {
                    return kof_http_request(url, "POST", headers, body);
                }

                public static String kof_http_put(String url, String body) throws Exception {
                    return kof_http_request(url, "PUT", null, body);
                }

                public static String kof_http_put_headers(String url, String body, String headers) throws Exception {
                    return kof_http_request(url, "PUT", headers, body);
                }

                public static String kof_http_patch(String url, String body) throws Exception {
                    return kof_http_request(url, "PATCH", null, body);
                }

                public static String kof_http_patch_headers(String url, String body, String headers) throws Exception {
                    return kof_http_request(url, "PATCH", headers, body);
                }

                private static final java.net.http.HttpClient KOF_HTTP_CLIENT_INSECURE;
                static {
                    java.net.http.HttpClient tmp = null;
                    try {
                        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                        sc.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        }}, new java.security.SecureRandom());
                        javax.net.ssl.SSLParameters sslParams = new javax.net.ssl.SSLParameters();
                        sslParams.setEndpointIdentificationAlgorithm("");
                        tmp = java.net.http.HttpClient.newBuilder()
                                .sslContext(sc)
                                .sslParameters(sslParams)
                                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                                .connectTimeout(java.time.Duration.ofSeconds(15))
                                .build();
                    } catch (Exception e) { tmp = java.net.http.HttpClient.newHttpClient(); }
                    KOF_HTTP_CLIENT_INSECURE = tmp;
                }

                public static int kof_http_status(String url) throws Exception {
                    boolean isHttps = url != null && url.toLowerCase().startsWith("https://");
                    java.net.http.HttpClient client = isHttps ? KOF_HTTP_CLIENT_INSECURE : java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(url))
                            .timeout(java.time.Duration.ofSeconds(KOF_HTTP_TIMEOUT.get()))
                            .method("GET", java.net.http.HttpRequest.BodyPublishers.noBody());
                    java.net.http.HttpResponse<String> r = client
                            .send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    return r.statusCode();
                }

                private static String kof_http_request(String url, String method, String headers, String body)
                        throws Exception {
                    java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(url))
                            .timeout(java.time.Duration.ofSeconds(KOF_HTTP_TIMEOUT.get()));
                    if (headers != null && !headers.isBlank()) {
                        for (String line : headers.split("\\n")) {
                            int c = line.indexOf(':');
                            if (c > 0) {
                                b.header(line.substring(0, c).trim(), line.substring(c + 1).trim());
                            }
                        }
                    }
                    if (body != null) {
                        b.method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        b.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
                    }
                    boolean isHttps2 = url != null && url.toLowerCase().startsWith("https://");
                    java.net.http.HttpClient client2 = isHttps2 ? KOF_HTTP_CLIENT_INSECURE : java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpResponse<String> r = client2
                            .send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    return r.body();
                }

                // ── kof.mq — messageria em memória (pub/sub + filas) ─────
                private static final java.util.concurrent.ConcurrentHashMap<String,
                        java.util.concurrent.CopyOnWriteArrayList<Object>> KOF_MQ_SUBS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_MQ_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();
                private static final java.util.concurrent.ConcurrentHashMap<String,
                        java.util.concurrent.ArrayBlockingQueue<Object>> KOF_MQ_QUEUES =
                        new java.util.concurrent.ConcurrentHashMap<>();

                public static void kof_mq_publish(String topic, Object msg) {
                    java.util.List<Object> subs = KOF_MQ_SUBS.get(topic);
                    if (subs != null) {
                        for (Object fn : subs) {
                            try {
                                fn.getClass().getMethod("invoke", Object.class).invoke(fn, msg);
                            } catch (java.lang.reflect.InvocationTargetException e) {
                                if (e.getCause() instanceof RuntimeException re) throw re;
                                throw new RuntimeException(e.getCause());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }

                public static void kof_mq_subscribe(String topic, Object fn) {
                    KOF_MQ_SUBS.computeIfAbsent(topic, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                            .add(fn);
                }

                public static void kof_mq_unsubscribe(String topic, Object fn) {
                    java.util.List<Object> subs = KOF_MQ_SUBS.get(topic);
                    if (subs != null) {
                        subs.remove(fn);
                    }
                }

                public static String kof_mq_queue() {
                    return "mq-" + KOF_MQ_SEQ.incrementAndGet();
                }

                public static void kof_mq_push(String q, Object item) {
                    KOF_MQ_QUEUES.computeIfAbsent(q,
                            k -> new java.util.concurrent.ArrayBlockingQueue<>(1024)).add(item);
                }

                public static Object kof_mq_pop(String q) {
                    java.util.concurrent.ArrayBlockingQueue<Object> queue = KOF_MQ_QUEUES.get(q);
                    return queue == null ? null : queue.poll();
                }

                public static int kof_mq_queue_size(String q) {
                    java.util.concurrent.ArrayBlockingQueue<Object> queue = KOF_MQ_QUEUES.get(q);
                    return queue == null ? 0 : queue.size();
                }

""";
    }
}
