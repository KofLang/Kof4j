package dev.kof.compiler.jvm;
import dev.kof.compiler.Type;

/**
 * Web JVM: dispatch de rotas, invoke de handlers, build de resposta e acessores de contexto.
 * Extraído de JvmRuntime.source (REFACTOR-500 Fase 5) — fragmento de source
 * do KofRuntime gerado; concatenação preserva ordem e conteúdo byte-a-byte.
 */
public final class JvmRuntimeWebDispatch {

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

                /**
                 * C18 (D-SEC): pipeline do {@code app.security()} — ordem FIXA
                 * rate-limit → cors → headers → cookies/session → csrf →
                 * auth (Bearer JWT) → RBAC → rota. Retorna a resposta de
                 * rejeição (429/403/401) ou null para seguir. Os headers de
                 * segurança vão para {@code KOF_SEC_RESPONSE_HEADERS} (o
                 * dispatch limpa {@code KOF_WEB_HEADERS} antes da rota).
                 */
                private static String kof_web_security_pipeline(WebApp app, WebRequest req,
                        Policy p, String routePattern) {
                    // 1. rate-limit (janela configurável; F5: chave por
                    //    IP + padrão de rota — limites de escopos/endpoints
                    //    distintos não compartilham contador).
                    if (p.rateLimit > 0) {
                        String ip = req.headers.get("x-forwarded-for");
                        if (ip == null || ip.isBlank()) ip = req.remoteAddr;
                        if (ip == null || ip.isBlank()) ip = "local";
                        String rlKey = ip + "|" + (routePattern == null ? "" : routePattern);
                        if (!kof_sec_rate_limit(rlKey, p.rateLimit, p.rateWindow)) {
                            kof_web_sec_header("Retry-After",
                                    String.valueOf(p.rateWindow));
                            return kof_web_build(429, "Too Many Requests",
                                    kof_web_sec_response(p, "tooManyRequests",
                                            "{\\"error\\":\\"too many requests\\"}"));
                        }
                    }
                    // 2. cors (só age se houver Origin)
                    if (p.cors != null) {
                        String origin = req.headers.get("origin");
                        if (origin != null && !origin.isBlank()) {
                            if (!kof_sec_cors_allowed(origin, p.cors)) {
                                return kof_web_build(403, "Forbidden",
                                        kof_web_sec_response(p, "forbidden",
                                                "{\\"error\\":\\"cors origin denied\\"}"));
                            }
                            kof_web_sec_header("Access-Control-Allow-Origin",
                                    p.cors.contains("*") ? "*" : origin);
                            kof_web_sec_header("Vary", "Origin");
                            if ("OPTIONS".equalsIgnoreCase(req.method)
                                    && req.headers.get("access-control-request-method") != null) {
                                kof_web_sec_header("Access-Control-Allow-Methods",
                                        "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                                String acrh = req.headers.get("access-control-request-headers");
                                kof_web_sec_header("Access-Control-Allow-Headers",
                                        acrh != null ? acrh
                                                : "Content-Type, Authorization, X-CSRF-Token");
                                kof_web_sec_header("Access-Control-Max-Age", "600");
                                return kof_web_build(204, "No Content", "");
                            }
                        }
                    }
                    // 3. headers de hardening (HSTS só sob TLS)
                    if (p.headers) {
                        kof_web_sec_header("Content-Security-Policy", kof_sec_csp_header());
                        kof_web_sec_header("X-Content-Type-Options",
                                kof_sec_content_type_options_header());
                        kof_web_sec_header("X-Frame-Options", kof_sec_frame_header());
                        kof_web_sec_header("Referrer-Policy", kof_sec_referrer_header());
                        if (req.secure) {
                            kof_web_sec_header("Strict-Transport-Security", kof_sec_hsts_header());
                        }
                    }
                    // 4. session: header de auth obrigatório fora dos prefixos
                    //    públicos (§5: autenticado por padrão, LEITURA incluída;
                    //    publicPaths é a allow-list). Sessão inválida nunca passa.
                    if (p.authHeader != null) {
                        boolean isPublic = p.publicPaths.stream()
                                .anyMatch(pp -> req.path.equals(pp) || req.path.startsWith(pp));
                        if (!isPublic) {
                            String tok = req.headers.get(p.authHeader);
                            if (tok == null || tok.isBlank()
                                    || kof_sec_session_get(tok) == null) {
                                return kof_web_build(401, "Unauthorized",
                                        kof_web_sec_response(p, "unauthorized",
                                                "{\\"error\\":\\"unauthorized\\"}"));
                            }
                        }
                    }
                    // 5. csrf: double-submit cookie (ou token de sessão).
                    //    §5 (Spring model): ON por padrão (método seguro emite
                    //    o cookie; mutação exige o double-submit). csrf:false
                    //    desliga explicitamente.
                    if (p.csrf) {
                        boolean safe = "GET".equals(req.method)
                                || "HEAD".equals(req.method)
                                || "OPTIONS".equals(req.method);
                        String cookieToken =
                                kof_sec_cookie_get(req.headers.get("cookie"), "csrf");
                        if (safe) {
                            if (cookieToken.isEmpty()) {
                                kof_web_sec_header("Set-Cookie",
                                        "csrf=" + kof_sec_random_hex(32)
                                        + "; Path=/; SameSite=Lax"
                                        + (req.secure ? "; Secure" : ""));
                            }
                        } else {
                            String headerToken = req.headers.get("x-csrf-token");
                            boolean valid = headerToken != null
                                    && (!cookieToken.isEmpty()
                                            ? kof_sec_constant_time_equals(cookieToken, headerToken)
                                            : kof_sec_csrf_valid(headerToken));
                            if (!valid) {
                                return kof_web_build(403, "Forbidden",
                                        kof_web_sec_response(p, "forbidden",
                                                "{\\"error\\":\\"csrf token invalid\\"}"));
                            }
                        }
                    }
                    // 6. auth: Bearer JWT obrigatório se `auth`; se Authorization
                    //    estiver presente, um token inválido NUNCA passa — mas
                    //    SÓ em app configurada (#1011, escopo D-SEC.4: a regra
                    //    é documentada sob app.security(); na DEFAULT de uma app
                    //    NUNCA configurada o 401 trancava gateways e apps com
                    //    auth própria, e nenhum middleware podia desligar — a
                    //    checagem corre depois dele). securityConfigured é
                    //    setada por app.security() E app.policy(). Quando há
                    //    sessionHeader (modo sessão da lane .22), o passo 4
                    //    já validou o token de sessão — não reinterpretar como
                    //    JWT (senão sessão válida viraria 401).
                    //    §781: auth-if-present only under app.security()/policy (D-SEC.4).
                    if (p.requireAuth
                            || (app.securityConfigured
                                    && p.authHeader == null
                                    && req.headers.get("authorization") != null)) {
                        boolean authenticated = kof_sec_auth_authenticated();
                        if (!authenticated) {
                            kof_web_sec_header("WWW-Authenticate", "Bearer");
                            return kof_web_build(401, "Unauthorized",
                                    kof_web_sec_response(p, "unauthorized",
                                            "{\\"error\\":\\"unauthorized\\"}"));
                        }
                    }
                    // 7. RBAC: todas as roles exigidas (implica auth).
                    if (!p.roles.isEmpty()) {
                        if (!kof_sec_auth_authenticated()) {
                            kof_web_sec_header("WWW-Authenticate", "Bearer");
                            return kof_web_build(401, "Unauthorized",
                                    kof_web_sec_response(p, "unauthorized",
                                            "{\\"error\\":\\"unauthorized\\"}"));
                        }
                        for (String role : p.roles) {
                            if (!kof_sec_auth_has_role(role)) {
                                return kof_web_build(403, "Forbidden",
                                        kof_web_sec_response(p, "forbidden",
                                                "{\\"error\\":\\"forbidden\\"}"));
                            }
                        }
                    }
                    return null;
                }

                private static void kof_web_sec_header(String name, String value) {
                    if (value != null) KOF_SEC_RESPONSE_HEADERS.get().put(name, value);
                }

                /** D-HTTP-POLICIES (F0): corpo de rejeicao declarado via
                 *  app.security(opts).responses; fallback = corpo embutido. */
                private static String kof_web_sec_response(Policy p, String key, String fallback) {
                    String body = p.responses.get(key);
                    return body != null ? body : fallback;
                }

                /** D-HTTP-POLICIES (F3): primeira rota que casa (sem invocar),
                 *  para fundir a policy de endpoint na efetiva ANTES da
                 *  pipeline. Null = nenhuma (404 segue com a policy de path). */
                private static WebRoute kof_web_match_route(WebApp app, WebRequest req) {
                    for (WebRoute route : app.routes) {
                        if (route.kind == RouteKind.HTTP && !route.method.equals(req.method)) {
                            continue;
                        }
                        String[] pathSegs = req.path.split("/");
                        if (pathSegs.length != route.segments.length) continue;
                        boolean match = true;
                        for (int i = 0; i < pathSegs.length; i++) {
                            if (route.params[i]) continue;
                            if (!route.segments[i].equals(pathSegs[i])) {
                                match = false;
                                break;
                            }
                        }
                        if (match) return route;
                    }
                    return null;
                }

                /** #1012: todo reset de headers por request limpa TAMBÉM a
                 *  lista de Set-Cookie (sem isto um cookie vazaria para a
                 *  resposta seguinte na mesma thread). */
                private static void kof_web_clear_response_headers() {
                    KOF_WEB_HEADERS.get().clear();
                    KOF_WEB_SET_COOKIES.get().clear();
                }

                private static WebDispatchResult kof_web_dispatch(WebApp app, WebRequest req) {
                    KOF_WEB_REQUEST.set(req);
                    KOF_WEB_STATUS.remove();
                    kof_web_clear_response_headers();
                    KOF_SEC_RESPONSE_HEADERS.get().clear();
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
                                kof_web_clear_response_headers();
                                return new WebDispatchResult(RouteKind.HTTP, resp, null);
                            }
                        }
                        // C18 (D-SEC): app.security() — ordem fixa rate-limit
                        // → cors → headers → session → csrf, SEMPRE antes das
                        // rotas (security by default não depende de o usuário
                        // compor a ordem à mão). Falha = resposta curta
                        // (429/403/401), nunca silenciosa (R6).
                        Policy pathPolicy = kof_web_effective_policy(app, req);
                        WebRoute matched = kof_web_match_route(app, req);
                        Policy effective = (matched != null && matched.policy != null)
                                ? pathPolicy.merge(matched.policy) : pathPolicy;
                        String securityReject = kof_web_security_pipeline(app, req, effective,
                                matched != null ? matched.path : "");
                        if (securityReject != null) {
                            KOF_WEB_STATUS.remove();
                            kof_web_clear_response_headers();
                            return new WebDispatchResult(RouteKind.HTTP, securityReject, null);
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
                                // SSE: the headers middleware and app.security() set
                                // (CORS, cookies, request id) go out with the stream head
                                String head = route.kind == RouteKind.SSE ? kof_web_extra_head() : null;
                                KOF_WEB_STATUS.remove();
                                kof_web_clear_response_headers();
                                 return new WebDispatchResult(route.kind, null, route);
                            }
                            Object result = kof_web_invoke(route.handler, req);
                            if (result == null) {
                                KOF_WEB_STATUS.remove();
                                kof_web_clear_response_headers();
                                return new WebDispatchResult(RouteKind.HTTP,
                                        kof_web_build(404, "Not Found",
                                                kof_web_sec_response(effective, "notFound",
                                                        "{\\\"error\\\": \\\"not found\\\"}")), null);
                            }
                            Integer st2 = KOF_WEB_STATUS.get();
                            int code2 = st2 != null ? st2 : 200;
                            String text2 = kof_web_status_text(code2);
                            String resp2 = kof_web_build(code2, text2, String.valueOf(result));
                            KOF_WEB_STATUS.remove();
                            kof_web_clear_response_headers();
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
                                            + "Content-Length: 0\\r\\n\\r\\n";
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
                                            + "Content-Length: " + staticBody.length + "\\r\\n\\r\\n";
                                    return new WebDispatchResult(
                                            RouteKind.HTTP, head, null, staticBody);
                                }
                            }
                            kof_web_static_done();
                        }
                        return new WebDispatchResult(RouteKind.HTTP,
                                kof_web_build(404, "Not Found",
                                        kof_web_sec_response(effective, "notFound",
                                                "{\\\"error\\\": \\\"not found\\\"}")), null);
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
                        kof_web_clear_response_headers();
                        return new WebDispatchResult(RouteKind.HTTP,
                                kof_web_build(500, "Internal Server Error",
                                        "{\\"error\\": \\"handler error: " + msg + "\\"}"), null);
                    } finally {
                        KOF_WEB_REQUEST.remove();
                        KOF_LOG_REQUEST_ID.remove();
                        KOF_WEB_STATUS.remove();
                        KOF_WEB_HEADERS.remove();
                        KOF_WEB_SET_COOKIES.remove();
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
                    try {
                        return target.getClass().getMethod("invoke", SseConnection.class)
                                .invoke(target, sse);
                    } catch (NoSuchMethodException e) {
                        // A2: non-compiled handlers (e.g. the interpreter's
                        // InterpretedCallable under --target script) expose a
                        // generic single-parameter invoke arm — scan for it
                        // instead of requiring the exact generated type.
                        for (java.lang.reflect.Method m : target.getClass().getMethods()) {
                            if (m.getName().equals("invoke") && m.getParameterCount() == 1
                                    && m.getParameterTypes()[0].isInstance(sse)) {
                                return m.invoke(target, sse);
                            }
                        }
                        throw e;
                    }
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
                        // incrementa ANTES do send: o cliente pode ler o eco e
                        // consultar /stats antes desta thread voltar do write
                        // (bug 28 — flake ws_messages_counters).
                        WS_MESSAGES_SENT.incrementAndGet();
                        ws.sendText(text);
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
                            for (String v : e.getValue().split("\\n")) {   // §784: repeated Set-Cookie
                                hdr.append(e.getKey()).append(": ").append(v).append("\\r\\n");
                            }
                        }
                    }
                    // #1012: UMA linha por Set-Cookie (RFC 6265 §3), na ordem
                    // das chamadas.
                    for (String[] sc : KOF_WEB_SET_COOKIES.get()) {
                        hdr.append(sc[0]).append(": ").append(sc[1]).append("\\r\\n");
                    }
                    // D-SEC C18: headers do middleware de security sobrevivem
                    // ao clear do dispatch antes da rota.
                    java.util.Map<String, String> secHeaders = KOF_SEC_RESPONSE_HEADERS.get();
                    if (secHeaders != null) {
                        for (java.util.Map.Entry<String, String> e : secHeaders.entrySet()) {
                            if (e.getKey().equalsIgnoreCase("Content-Type")) hasContentType = true;
                            hdr.append(e.getKey()).append(": ").append(e.getValue()).append("\\r\\n");
                        }
                    }
                    String ctHeader = hasContentType ? "" : "Content-Type: " + contentType + "\\r\\n";
                    return "HTTP/1.1 " + status + " " + statusText + "\\r\\n"
                            + ctHeader
                            + hdr.toString()
                            + "Content-Length: " + bodyBytes.length + "\\r\\n"
                            + "\\r\\n"
                            + body;
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

                public static String kof_web_remote_addr() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null || req.remoteAddr == null ? "" : req.remoteAddr;
                }

""";
    }
}
