package dev.kof.compiler;

/**
 * Web JVM: dispatch de rotas, invoke de handlers, build de resposta, readRequest e acessores de contexto.
 * Extraído de JvmRuntime.source (REFACTOR-500 Fase 5) — fragmento de source
 * do KofRuntime gerado; concatenação preserva ordem e conteúdo byte-a-byte.
 */
final class JvmRuntimeWebDispatch {

    private JvmRuntimeWebDispatch() {}

    static String source() {
        return """
                private static void readFully(java.io.InputStream in, byte[] buf) throws java.io.IOException {
                    int off = 0;
                    while (off < buf.length) {
                        int n = in.read(buf, off, buf.length - off);
                        if (n < 0) throw new java.io.IOException("EOF reading " + buf.length + " bytes (got " + off + ")");
                        off += n;
                    }
                }

                // Splits a comma-separated header value (RFC 7230 §3.2.2) into
                // trimmed tokens, returning true when any token equals the
                // expected name case-insensitively. Null/empty header -> false.
                // Used by WS handshake to validate Upgrade/Connection tokens
                // even when the same line carries unrelated tokens.
                private static boolean containsToken(String headerValue, String expected) {
                    if (headerValue == null) return false;
                    for (String token : headerValue.split(",")) {
                        if (token.trim().equalsIgnoreCase(expected)) return true;
                    }
                    return false;
                }

                private static WebDispatchResult kof_web_dispatch(WebApp app, WebRequest req) {
                    KOF_WEB_REQUEST.set(req);
                    KOF_WEB_STATUS.remove();
                    KOF_WEB_HEADERS.get().clear();
                    KOF_LOG_REQUEST_ID.set(kof_sec_random_hex(16));
                    try {
                        // app.health(path): built-in — responde antes dos
                        // middlewares (sondas de load balancer não passam por
                        // auth/middleware): estado de saúde em JSON.
                        for (String hp : app.healthPaths) {
                            if (req.path.equals(hp)) {
                                String resp = kof_web_build(200, "OK",
                                        "{\\"status\\": \\"" + kof_observability_health()
                                        + "\\","
                                        + "\\"ready\\": " + kof_observability_readiness()
                                        + ", \\"alive\\": " + kof_observability_liveness() + "}");
                                return new WebDispatchResult(RouteKind.HTTP, resp, null);
                            }
                        }
                        for (Object middleware : app.middlewares) {
                            Object result = kof_web_invoke(middleware, req);
                            if (result != null) {
                                Integer st = KOF_WEB_STATUS.get();
                                int code = st != null ? st : 200;
                                String text = kof_web_status_text(code);
                                String resp = kof_web_build(code, text, String.valueOf(result));
                                KOF_WEB_STATUS.remove();
                                KOF_WEB_HEADERS.get().clear();
                                return new WebDispatchResult(RouteKind.HTTP, resp, null);
                            }
                        }
                        for (WebRoute route : app.routes) {
                            if (route.kind == RouteKind.HTTP && !route.method.equals(req.method)) continue;
                            String[] pathSegs = req.path.split("/");
                            if (pathSegs.length != route.segments.length) continue;
                            boolean match = true;
                            java.util.Map<String, String> params = new java.util.HashMap<>();
                            for (int i = 0; i < pathSegs.length; i++) {
                                if (route.params[i]) {
                                    params.put(route.segments[i].substring(1), pathSegs[i]);
                                } else if (!route.segments[i].equals(pathSegs[i])) {
                                    match = false;
                                    break;
                                }
                            }
                            if (!match) continue;
                            req.params.putAll(params);
                            if (route.kind != RouteKind.HTTP) {
                                KOF_WEB_STATUS.remove();
                                KOF_WEB_HEADERS.get().clear();
                                return new WebDispatchResult(route.kind, null, route);
                            }
                            KOF_WEB_STATUS.remove();
                            KOF_WEB_HEADERS.get().clear();
                            Object result = kof_web_invoke(route.handler, req);
                            if (result == null) {
                                KOF_WEB_STATUS.remove();
                                KOF_WEB_HEADERS.get().clear();
                                return new WebDispatchResult(RouteKind.HTTP,
                                        kof_web_build(404, "Not Found", "{\\"error\\": \\"not found\\"}"), null);
                            }
                            Integer st2 = KOF_WEB_STATUS.get();
                            int code2 = st2 != null ? st2 : 200;
                            String text2 = kof_web_status_text(code2);
                            String resp2 = kof_web_build(code2, text2, String.valueOf(result));
                            KOF_WEB_STATUS.remove();
                            KOF_WEB_HEADERS.get().clear();
                            return new WebDispatchResult(RouteKind.HTTP, resp2, null);
                        }
                        // Arquivos estáticos (app.serveDir): fallback quando
                        // nenhuma rota dinâmica casa — conteúdo binário do
                        // disco com content-type e Range (vídeo navegável no
                        // browser), sem o app colar base64 em String.
                        if (kof_web_static_match(app, req.path) == 0) {
                            String staticMeta = kof_web_static_meta();
                            if (staticMeta != null) {
                                int sep = staticMeta.indexOf('|');
                                String mime = staticMeta.substring(0, sep);
                                long total = Long.parseLong(staticMeta.substring(sep + 1));
                                String range = req.header("range");
                                long start = 0, end = total - 1;
                                boolean ranged = false;
                                if (range != null && range.startsWith("bytes=")) {
                                    String spec = range.substring(6).split(",", 2)[0].trim();
                                    int dash = spec.indexOf('-');
                                    if (dash > 0) {
                                        String s = spec.substring(0, dash).trim();
                                        String e = spec.substring(dash + 1).trim();
                                        start = s.isEmpty()
                                                ? Math.max(0, total - Long.parseLong(e))
                                                : Long.parseLong(s);
                                        end = e.isEmpty()
                                                ? total - 1
                                                : Math.min(Long.parseLong(e), total - 1);
                                        ranged = true;
                                    }
                                }
                                if (ranged && (start > end || start >= total)) {
                                    String h416 = "HTTP/1.1 416 Range Not Satisfiable\\r\\n"
                                            + "Content-Range: bytes */" + total + "\\r\\n"
                                            + "Content-Length: 0\\r\\nConnection: close\\r\\n\\r\\n";
                                    return new WebDispatchResult(RouteKind.HTTP, h416, null);
                                }
                                byte[] staticBody = kof_web_static_read(start, end);
                                if (staticBody != null) {
                                    String head = (ranged ? "HTTP/1.1 206 Partial Content"
                                            : "HTTP/1.1 200 OK") + "\\r\\n"
                                            + "Content-Type: " + mime + "\\r\\n"
                                            + "Accept-Ranges: bytes\\r\\n"
                                            + "Cache-Control: public, max-age=86400\\r\\n"
                                            + (ranged
                                            ? "Content-Range: bytes " + start + "-"
                                                    + (start + staticBody.length - 1) + "/" + total + "\\r\\n"
                                            : "")
                                            + "Content-Length: " + staticBody.length + "\\r\\n"
                                            + "Connection: close\\r\\n\\r\\n";
                                    return new WebDispatchResult(
                                            RouteKind.HTTP, head, null, staticBody);
                                }
                            }
                            kof_web_static_done();
                        }
                        return new WebDispatchResult(RouteKind.HTTP,
                                kof_web_build(404, "Not Found", "{\\"error\\": \\"not found\\"}"), null);
                    } catch (Exception e) {
                        // handler lambda é invocado via reflection: a exceção
                        // real chega embrulhada em InvocationTargetException —
                        // sem desempacotar, o 500 diz só "InvocationTargetException"
                        // e esconde o diagnóstico (violation R6).
                        Throwable root = e;
                        while (root instanceof java.lang.reflect.InvocationTargetException
                                && root.getCause() != null) {
                            root = root.getCause();
                        }
                        String msg = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
                        KOF_WEB_STATUS.remove();
                        KOF_WEB_HEADERS.get().clear();
                        return new WebDispatchResult(RouteKind.HTTP,
                                kof_web_build(500, "Internal Server Error",
                                        "{\\"error\\": \\"handler error: " + msg + "\\"}"), null);
                    } finally {
                        KOF_WEB_REQUEST.remove();
                        KOF_LOG_REQUEST_ID.remove();
                        KOF_WEB_STATUS.remove();
                        KOF_WEB_HEADERS.remove();
                    }
                }

                private static Object kof_web_invoke(Object target, WebRequest req) throws Exception {
                    try {
                        return target.getClass().getMethod("invoke").invoke(target);
                    } catch (NoSuchMethodException e) {
                        return target.getClass()
                                .getMethod("invoke", String.class, String.class, String.class,
                                        String.class, String.class)
                                .invoke(target, req.method, req.path, req.body, req.query, req.rawHeaders);
                    }
                }

                private static Object kof_web_invoke(Object target, SseConnection sse) throws Exception {
                    return target.getClass().getMethod("invoke", SseConnection.class)
                            .invoke(target, sse);
                }

                // ── WS/SSE context (kof_web_ws_message/wsSend/sse) ──
                private static final ThreadLocal<Object> KOF_WS_CONNECTION = new ThreadLocal<>();
                private static final ThreadLocal<String> KOF_WS_MESSAGE = new ThreadLocal<>();
                private static final ThreadLocal<Object> KOF_SSE_SENDER = new ThreadLocal<>();

                /** wsMessage() — mensagem TEXT corrente da conexão WebSocket. */
                public static String kof_web_ws_message() {
                    return KOF_WS_MESSAGE.get();
                }

                /** wsSend(text) — envia TEXT pela conexão WebSocket corrente. */
                public static void kof_web_ws_send(String text) {
                    Object conn = KOF_WS_CONNECTION.get();
                    if (conn instanceof WsSender ws) {
                        ws.sendText(text);
                        WS_MESSAGES_SENT.incrementAndGet();
                    }
                }

                /** sse(text) — envia um evento SSE pela conexão corrente. */
                public static String kof_web_sse_send(String text) {
                    Object sender = KOF_SSE_SENDER.get();
                    if (sender instanceof SseSender s) {
                        s.send(text);
                    }
                    return text;
                }

                private static String kof_web_build(int status, String statusText, String body) {
                    byte[] bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    String contentType = "text/plain; charset=utf-8";
                    String trimmed = body.trim();
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        contentType = "application/json; charset=utf-8";
                    }
                    java.util.Map<String, String> extra = KOF_WEB_HEADERS.get();
                    boolean hasContentType = false;
                    StringBuilder hdr = new StringBuilder();
                    if (extra != null) {
                        for (java.util.Map.Entry<String, String> e : extra.entrySet()) {
                            if (e.getKey().equalsIgnoreCase("Content-Type")) hasContentType = true;
                            hdr.append(e.getKey()).append(": ").append(e.getValue()).append("\\r\\n");
                        }
                    }
                    String ctHeader = hasContentType ? "" : "Content-Type: " + contentType + "\\r\\n";
                    return "HTTP/1.1 " + status + " " + statusText + "\\r\\n"
                            + ctHeader
                            + hdr.toString()
                            + "Content-Length: " + bodyBytes.length + "\\r\\n"
                            + "Connection: close\\r\\n"
                            + "\\r\\n"
                            + body;
                }

                private static WebRequest readRequest(java.io.InputStream in) throws java.io.IOException {
                    StringBuilder head = new StringBuilder();
                    byte[] buffer = new byte[8192];
                    int headerEnd = -1;
                    while (true) {
                        int n = in.read(buffer);
                        if (n == -1) throw new java.io.IOException("connection closed before headers");
                        head.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                        headerEnd = head.indexOf("\\r\\n\\r\\n");
                        if (headerEnd >= 0) break;
                        if (head.length() > 65536) throw new java.io.IOException("headers too large");
                    }

                    String requestText = head.toString();
                    String headerBlock = requestText.substring(0, headerEnd);
                    StringBuilder body = new StringBuilder(requestText.substring(headerEnd + 4));

                    int contentLength = 0;
                    for (String line : headerBlock.split("\\r\\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            try {
                                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    while (body.length() < contentLength) {
                        int n = in.read(buffer);
                        if (n == -1) break;
                        body.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                    }
                    if (body.length() > contentLength) {
                        body.setLength(contentLength);
                    }

                    String[] lines = headerBlock.split("\\r\\n");
                    String[] parts = lines.length > 0 ? lines[0].split(" ") : new String[0];
                    String method = parts.length > 0 ? parts[0] : "GET";
                    String fullPath = parts.length > 1 ? parts[1] : "/";
                    String path = fullPath;
                    String query = "";
                    int q = fullPath.indexOf('?');
                    if (q >= 0) {
                        path = fullPath.substring(0, q);
                        query = fullPath.substring(q + 1);
                    }
                    return new WebRequest(method, path, query, headerBlock, body.toString());
                }

                public static String kof_web_param(String name) {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.param(name);
                }

                public static String kof_web_query(String name) {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.query(name);
                }

                public static String kof_web_header(String name) {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.header(name);
                }

                public static String kof_web_body() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.body;
                }

                public static String kof_web_method() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.method;
                }

                public static String kof_web_path() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.path;
                }

""";
    }
}
