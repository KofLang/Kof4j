package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for the Kof-native web stack ({@code web.app()}).
 *
 * Each test compiles a Kof program to JVM bytecode, runs it as a subprocess
 * (the program registers routes and calls {@code app.listen(port)}), and
 * exercises it over real sockets. No Spring, no servlet container.
 */
class KofWebE2ETest extends KofWebPrograms {

    private static final String JAVA_BIN = java.nio.file.Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();
    private Process serverProcess;

    @AfterEach
    void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
    }


    private int startServer(Path tempDir) throws IOException {
        return startServer(tempDir, WEB_APP);
    }

    private int startServer(Path tempDir, String kofSource) throws IOException {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, kofSource.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        TestServerFixture.awaitListening(serverProcess, port);
        return port;
    }

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private String request(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            return response.toString();
        }
    }

    private String bodyOf(String rawResponse) {
        int idx = rawResponse.indexOf("\r\n\r\n");
        return idx >= 0 ? rawResponse.substring(idx + 4) : rawResponse;
    }

    @Test
    void pathParamAndQuery(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = request(port, "GET /users/42?name=mel HTTP/1.1\r\nHost: x\r\nX-Auth: secret\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(bodyOf(r).equals("user 42 q=mel"), r);
    }

    @Test
    void queryValueIsPercentDecoded(@TempDir Path tempDir) throws IOException {
        // Paridade JVM x JS (§2.4/§12 pagination-plan): o JS ja decodificava
        // (decodeURIComponent); o JVM devolvia o valor cru. `%20` -> espaco,
        // `%2B` -> `+` (e `+` literal NAO vira espaco).
        int port = startServer(tempDir);
        String r = request(port, "GET /users/42?name=a%20b%2Bc HTTP/1.1\r\nHost: x\r\nX-Auth: secret\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(bodyOf(r).equals("user 42 q=a b+c"), r);
        // sequencia malformada = URIError no JS -> nunca 200 silencioso (R6).
        String bad = request(port, "GET /users/42?name=%zz HTTP/1.1\r\nHost: x\r\nX-Auth: secret\r\n\r\n");
        assertFalse(bad.startsWith("HTTP/1.1 200"), bad);
    }

    @Test
    void absentHeaderAndQueryAreNullable(@TempDir Path tempDir) throws IOException {
        // #102 item 4 (comentário PublioSantos): header()/query() presentes como
        // String mas null na ausência -> deref sem narrowing passava no check e
        // NPEava 500 silencioso. Agora String?: o narrowing é obrigatório.
        String app = SRC_ABSENT_HEADER_AND_QUERY_ARE_NULLABLE;
        int port = startServer(tempDir, app);
        String r = request(port, "GET /h HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(bodyOf(r).equals("nada"), r);
        String r2 = request(port, "GET /h HTTP/1.1\r\nHost: x\r\nx-ausente: abc\r\n\r\n");
        assertTrue(bodyOf(r2).equals("len:3"), r2);
        String r3 = request(port, "GET /q?name=mel HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(bodyOf(r3).equals("nome:mel"), r3);
        String r4 = request(port, "GET /q HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(bodyOf(r4).equals("sem-nome"), r4);
    }

    @Test
    void headersAvailable(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = request(port, "GET /agent HTTP/1.1\r\nHost: x\r\nX-Auth: secret\r\nUser-Agent: KofTest\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(bodyOf(r).equals("agent=KofTest"), r);
    }

    @Test
    void helloRoute(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = request(port, "GET /hello HTTP/1.1\r\nHost: x\r\nX-Auth: secret\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(bodyOf(r).equals("Hello from Kof"), r);
    }

    @Test
    void healthEndpointBypassesMiddleware(@TempDir Path tempDir) throws IOException {
        // app.health responde ANTES dos middlewares: sonda sem auth → 200,
        // enquanto /hello sem auth → 401 (middleware bloqueia).
        String app = SRC_HEALTH_ENDPOINT_BYPASSES_MIDDLEWARE;
        int port = startServer(tempDir, app);
        // sonda de health SEM header de auth → 200 JSON de saúde
        String r = request(port, "GET /health HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200"), "health 200: " + r.split("\r\n", 2)[0]);
        String b = bodyOf(r);
        assertTrue(b.contains("\"status\": \"UP\""), "status UP: " + b);
        assertTrue(b.contains("\"ready\": true"), "ready: " + b);
        assertTrue(b.contains("\"alive\": true"), "alive: " + b);
        // rota normal SEM auth → bloqueada pelo middleware (prova do bypass)
        String r2 = request(port, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
        String b2 = bodyOf(r2);
        assertTrue(b2.contains("unauthorized"), "middleware bloqueia /hello: " + b2);
    }

    @Test
    void postBody(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = request(port, "POST /echo HTTP/1.1\r\nHost: x\r\nX-Auth: secret\r\nContent-Length: 7\r\n\r\n{\"a\":1}");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(bodyOf(r).equals("got:{\"a\":1}"), r);
    }

    @Test
    void jsonRoundTrip(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = request(port, "POST /user HTTP/1.1\r\nHost: x\r\nX-Auth: secret\r\nContent-Length: 23\r\n\r\n{\"name\":\"Mel\",\"age\":26}");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(r.contains("application/json"), r);
        assertTrue(bodyOf(r).equals("{\"name\":\"Mel\",\"age\":26}"), r);
    }

    @Test
    void notFoundIs404(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = request(port, "GET /nope HTTP/1.1\r\nHost: x\r\nX-Auth: secret\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 404 Not Found"), r);
    }

    @Test
    void deleteRouteCompilesAndResponds(@TempDir Path tempDir) throws IOException {
        // bug 54 (GitHub #29): app.delete colidia com File.delete no Io →
        // KofPop extra sobre kof_web_route (void) → frame crash no JVM.
        String app = SRC_DELETE_ROUTE_COMPILES_AND_RESPONDS;
        int port = startServer(tempDir, app);
        String r = request(port, "DELETE /item/7 HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(bodyOf(r).equals("deleted:7"), r);
    }

    @Test
    void handlerReturningNullAsLastPathStillRespondsValue(@TempDir Path tempDir) throws IOException {
        // bug 53 (GitHub #28): forma idiomática `if (x) { return valor }
        // return null` tipava invoke() como VOID (typer só varria ReturnStmt
        // top-level) → valor de sucesso descartado → 404 em toda request.
        String app = SRC_HANDLER_RETURNING_NULL_AS_LAST_PATH_STILL_RESPONDS_VALUE;
        int port = startServer(tempDir, app);
        String hit = request(port, "GET /x/1 HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(hit.startsWith("HTTP/1.1 200 OK"), hit);
        assertTrue(bodyOf(hit).equals("one"), bodyOf(hit));
        // return null continua sendo 404 documentado (não regrediu)
        String miss = request(port, "GET /x/2 HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(miss.startsWith("HTTP/1.1 404 Not Found"), miss);
    }

    @Test
    void middlewareShortCircuits(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = request(port, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(bodyOf(r).equals("{\"error\": \"unauthorized\"}"), r);
    }

    @Test
    void multipleTrailingLambdaRoutes(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, SRC_MULTIPLE_TRAILING_LAMBDA_ROUTES);
        assertEquals("A", bodyOf(request(port, "GET /a HTTP/1.1\r\nHost: x\r\n\r\n")));
        assertEquals("B", bodyOf(request(port, "GET /b HTTP/1.1\r\nHost: x\r\n\r\n")));
    }

    @Test
    void sseAndWsGapOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, SRC_SSE_AND_WS_GAP_ON_NATIVE);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        var diagnostics = result.diagnostics().getDiagnostics();
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("WEB003")),
                "Should have WEB003, got: " + diagnostics);
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("WEB004")),
                "Should have WEB004, got: " + diagnostics);
    }

    // #102.2 (13/09): `app.listen("8100")` (String) — antes VerifyError em
    // runtime; agora SEM025 no `kof check` (listen aceita SÓ Int).
    @Test
    void listenWithStringIsRejectedAtCheckTime(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.get("/a") { return "A" }
                    app.listen("8100")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        var diagnostics = result.diagnostics().getDiagnostics();
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("SEM025")),
                "listen(String) deve dar SEM025, got: " + diagnostics);
    }

    // ── D-SEC C18: app.security() — middleware composto ──────────────

    private String headerLine(String rawResponse, String name) {
        for (String line : rawResponse.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).equalsIgnoreCase(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /** HS256 JWT no mesmo formato de {@code kof_sec_jwt_create} (para o teste
     *  forjar credenciais sem depender de uma rota pública de emissão). */
    private String hs256(String claimsJson, String secret) throws Exception {
        java.util.Base64.Encoder b64 = java.util.Base64.getUrlEncoder().withoutPadding();
        long now = System.currentTimeMillis() / 1000;
        String head = claimsJson.substring(0, claimsJson.lastIndexOf('}')).trim();
        String sep = head.isEmpty() || head.endsWith("{") ? "" : ",";
        String payload = head + sep + "\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}";
        String headerB64 = b64.encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = b64.encodeToString(mac.doFinal(
                (headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8)));
        return headerB64 + "." + payloadB64 + "." + sig;
    }

    @Test
    void securityHeadersByDefault(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, SRC_SECURITY_HEADERS_BY_DEFAULT);
        String r = request(port, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertEquals("ok", bodyOf(r));
        assertNotNull(headerLine(r, "Content-Security-Policy"), r);
        assertEquals("nosniff", headerLine(r, "X-Content-Type-Options"), r);
        assertEquals("DENY", headerLine(r, "X-Frame-Options"), r);
        assertEquals("no-referrer", headerLine(r, "Referrer-Policy"), r);
        // HTTP simples (não TLS): HSTS não deve ser emitido.
        assertNull(headerLine(r, "Strict-Transport-Security"), r);
    }

    @Test
    void securityRequiresValidBearerWhenAuthEnabled(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, SRC_SECURITY_REQUIRES_VALID_BEARER_WHEN_AUTH_ENABLED);
        String noAuth = request(port, "GET /me HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(noAuth.startsWith("HTTP/1.1 401 Unauthorized"), noAuth);
        assertEquals("Bearer", headerLine(noAuth, "WWW-Authenticate"), noAuth);

        String bad = request(port, "GET /me HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer nope\r\n\r\n");
        assertTrue(bad.startsWith("HTTP/1.1 401 Unauthorized"), bad);

        String good = hs256("{\"sub\":\"u1\"}", "s3cret");
        String ok = request(port, "GET /me HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + good + "\r\n\r\n");
        assertTrue(ok.startsWith("HTTP/1.1 200 OK"), ok);
        assertEquals("hi u1", bodyOf(ok));
    }

    @Test
    void securityRejectsInvalidTokenIfPresentEvenWithoutAuthRequired(@TempDir Path tempDir)
            throws IOException {
        // auth-if-present: sem `auth:true` uma credencial presente mas
        // inválida NUNCA passa (evita "token ruim vira anônimo").
        int port = startServer(tempDir, SRC_SECURITY_REJECTS_INVALID_TOKEN_IF_PRESENT_EVEN_WITHOUT_AUTH_REQUIRED);
        String anonymous = request(port, "GET /open HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(anonymous.startsWith("HTTP/1.1 200 OK"), anonymous);
        String bad = request(port, "GET /open HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer nope\r\n\r\n");
        assertTrue(bad.startsWith("HTTP/1.1 401 Unauthorized"), bad);
    }

    @Test
    void securityEnforcesRoles(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, SRC_SECURITY_ENFORCES_ROLES);
        String noRole = hs256("{\"sub\":\"u1\",\"roles\":[\"user\"]}", "s3cret");
        String forbidden = request(port,
                "GET /admin HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + noRole + "\r\n\r\n");
        assertTrue(forbidden.startsWith("HTTP/1.1 403 Forbidden"), forbidden);

        String admin = hs256("{\"sub\":\"u1\",\"roles\":[\"admin\"]}", "s3cret");
        String ok = request(port,
                "GET /admin HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + admin + "\r\n\r\n");
        assertTrue(ok.startsWith("HTTP/1.1 200 OK"), ok);
        assertEquals("secret", bodyOf(ok));
    }

    @Test
    void securityCorsDeniesUnknownOriginAndAnswersPreflight(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, SRC_SECURITY_CORS_DENIES_UNKNOWN_ORIGIN_AND_ANSWERS_PREFLIGHT);
        String evil = request(port, "GET /x HTTP/1.1\r\nHost: x\r\nOrigin: https://evil.example\r\n\r\n");
        assertTrue(evil.startsWith("HTTP/1.1 403 Forbidden"), evil);

        String allowed = request(port,
                "GET /x HTTP/1.1\r\nHost: x\r\nOrigin: https://app.example\r\n\r\n");
        assertTrue(allowed.startsWith("HTTP/1.1 200 OK"), allowed);
        assertEquals("https://app.example", headerLine(allowed, "Access-Control-Allow-Origin"), allowed);

        String preflight = request(port, "OPTIONS /x HTTP/1.1\r\nHost: x\r\n"
                + "Origin: https://app.example\r\nAccess-Control-Request-Method: POST\r\n\r\n");
        assertTrue(preflight.startsWith("HTTP/1.1 204 No Content"), preflight);
        assertNotNull(headerLine(preflight, "Access-Control-Allow-Methods"), preflight);
    }

    @Test
    void securityCsrfDoubleSubmit(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, SRC_SECURITY_CSRF_DOUBLE_SUBMIT);
        String blocked = request(port, "POST /p HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\n\r\n");
        assertTrue(blocked.startsWith("HTTP/1.1 403 Forbidden"), blocked);

        String token = "deadbeef";
        String ok = request(port, "POST /p HTTP/1.1\r\nHost: x\r\n"
                + "Cookie: csrf=" + token + "\r\nX-CSRF-Token: " + token + "\r\n"
                + "Content-Length: 0\r\n\r\n");
        assertTrue(ok.startsWith("HTTP/1.1 200 OK"), ok);
        assertEquals("posted", bodyOf(ok));
    }

    @Test
    void securityCsrfIsOnByDefault(@TempDir Path tempDir) throws IOException {
        // DECISIONS §5 (Spring model): CSRF ON por padrão quando app.security()
        // é configurado, sem precisar de `csrf:true` explícito.
        int port = startServer(tempDir, SRC_SECURITY_CSRF_IS_ON_BY_DEFAULT);
        String blocked = request(port, "POST /p HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\n\r\n");
        assertTrue(blocked.startsWith("HTTP/1.1 403 Forbidden"), blocked);

        String token = "cafef00d";
        String ok = request(port, "POST /p HTTP/1.1\r\nHost: x\r\n"
                + "Cookie: csrf=" + token + "\r\nX-CSRF-Token: " + token + "\r\n"
                + "Content-Length: 0\r\n\r\n");
        assertTrue(ok.startsWith("HTTP/1.1 200 OK"), ok);
    }

    @Test
    void securityPermitAllAliasIsPublicPaths(@TempDir Path tempDir) throws IOException {
        // §5: `permitAll` é alias de `publicPaths`.
        int port = startServer(tempDir, SRC_SECURITY_PERMIT_ALL_ALIAS_IS_PUBLIC_PATHS);
        String open = request(port, "GET /open HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(open.startsWith("HTTP/1.1 200 OK"), open);
        assertEquals("open", bodyOf(open));
        String closed = request(port, "GET /closed HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(closed.startsWith("HTTP/1.1 401 Unauthorized"), closed);
    }

    @Test
    void securityRateLimitByRemoteAddress(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, SRC_SECURITY_RATE_LIMIT_BY_REMOTE_ADDRESS);
        assertTrue(request(port, "GET /r HTTP/1.1\r\nHost: x\r\n\r\n").startsWith("HTTP/1.1 200 OK"));
        assertTrue(request(port, "GET /r HTTP/1.1\r\nHost: x\r\n\r\n").startsWith("HTTP/1.1 200 OK"));
        String third = request(port, "GET /r HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(third.startsWith("HTTP/1.1 429 Too Many Requests"), third);
        assertEquals("60", headerLine(third, "Retry-After"), third);
    }

    @Test
    void securityGapOnNativeAndJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.security()
                    app.listen(8100)
                }
                """);
        for (Target target : new Target[] {Target.NATIVE, Target.JS}) {
            CompilationResult result = driver.compile(source, tempDir.resolve("out-" + target), target);
            var diagnostics = result.diagnostics().getDiagnostics();
            assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("WEB006")),
                    "app.security() deve dar WEB006 no " + target + ", got: " + diagnostics);
        }
    }

    // C18 (D-SEC, DECISIONS.md): app.security() middleware composto de ordem fixa
    // rate-limit → cors → headers de segurança → session → csrf (lane .22).
    @Test
    void appSecurityPipelineE2E(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, SRC_APP_SECURITY_PIPELINE_E2_E);

        // 1. Rota pública responde 200 sem credencial + injeta security headers
        String pub = request(port, "GET /public HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(pub.startsWith("HTTP/1.1 200 OK"), pub);
        assertEquals("public content", bodyOf(pub));
        assertNotNull(headerLine(pub, "Content-Security-Policy"), pub);
        assertEquals("nosniff", headerLine(pub, "X-Content-Type-Options"), pub);
        assertEquals("DENY", headerLine(pub, "X-Frame-Options"), pub);

        // 2. Rota protegida fora de publicPaths sem credencial é rejeitada (401).
        String readOpen = request(port, "GET /secret HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(readOpen.startsWith("HTTP/1.1 401 Unauthorized"), readOpen);
        String secNoAuth = request(port,
                "POST /secret HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\n\r\n");
        assertTrue(secNoAuth.startsWith("HTTP/1.1 401 Unauthorized"), secNoAuth);
        assertTrue(bodyOf(secNoAuth).contains("unauthorized"), secNoAuth);

        // 3. Sessão presente mas inválida nunca passa.
        String secBadAuth = request(port,
                "GET /secret HTTP/1.1\r\nHost: x\r\nauthorization: invalid-token\r\n\r\n");
        assertTrue(secBadAuth.startsWith("HTTP/1.1 401 Unauthorized"), secBadAuth);
    }

    // D-HTTP-POLICIES (F0): `responses` declarativos substituem os corpos
    // embutidos das rejeições sintéticas do pipeline (401/403/429). Chaves
    // ausentes mantêm o comportamento de hoje (retrocompatível) — coberto por
    // `appSecurityPipelineE2E` (corpo embutido `{"error":"unauthorized"}`).
    @Test
    void securityDeclarativePayloadsForUnauthorizedAndRateLimit(@TempDir Path tempDir)
            throws IOException {
        int port = startServer(tempDir, SRC_SECURITY_DECLARATIVE_PAYLOADS_FOR_UNAUTHORIZED_AND_RATE_LIMIT);
        // 1ª request: dentro do rate-limit, sem credencial → 401 com corpo declarado.
        String first = request(port, "GET /secret HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(first.startsWith("HTTP/1.1 401 Unauthorized"), first);
        assertEquals("{\"error\":\"custom-401\"}", bodyOf(first));
        // 2ª request na janela: rate-limit estourou → 429 com corpo declarado.
        String second = request(port, "GET /secret HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(second.startsWith("HTTP/1.1 429 Too Many Requests"), second);
        assertEquals("{\"error\":\"custom-429\"}", bodyOf(second));
    }

    @Test
    void securityDeclarativeForbiddenPayload(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, SRC_SECURITY_DECLARATIVE_FORBIDDEN_PAYLOAD);
        String denied = request(port,
                "GET /x HTTP/1.1\r\nHost: x\r\nOrigin: https://evil.example\r\n\r\n");
        assertTrue(denied.startsWith("HTTP/1.1 403 Forbidden"), denied);
        assertEquals("{\"error\":\"custom-403\"}", bodyOf(denied));
    }
}
