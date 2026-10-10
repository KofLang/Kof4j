package dev.kof.compiler;

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
 * D-HTTP-POLICIES (F2): {@code app.policy(prefix, opts)} — policies de recurso.
 *
 * A policy global ({@code app.security}) permanece o default; escopos de
 * recurso casam por prefixo do path e aplicam a lei de merge (§4.3): escalar
 * mais profundo vence, listas ({@code publicPaths}/{@code roles}) somam.
 * Cada teste compila um programa Kof e o dirige por sockets reais.
 */
class KofHttpPoliciesE2ETest extends ServerProcessSupport {

    private static final String JAVA_BIN = java.nio.file.Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();


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

    private String headerLine(String rawResponse, String name) {
        for (String line : rawResponse.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).equalsIgnoreCase(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /** HS256 JWT no mesmo formato de {@code kof_sec_jwt_create}. */
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

    // §4.2/§4.3: o escopo "/admin" adiciona o role exigido SO sob o prefixo;
    // fora dele o default global (sem auth) segue valendo.
    @Test
    void resourceScopeAppliesUnderPrefixOnly(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    auth.secret("s3cret")
                    var app = web.app()
                    app.security()
                    app.policy("/admin", mapOf("roles", "admin"))
                    app.get("/admin/users") { return "admin-users" }
                    app.get("/public") { return "public" }
                    app.listen(PORT)
                }
                """);
        // global default preservado fora do prefixo: GET /public anonimo = 200
        String pub = request(port, "GET /public HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(pub.startsWith("HTTP/1.1 200 OK"), pub);
        assertEquals("public", bodyOf(pub));

        // sob /admin o role e exigido: sem token = 401
        String anon = request(port, "GET /admin/users HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(anon.startsWith("HTTP/1.1 401 Unauthorized"), anon);

        // token sem o role = 403
        String user = hs256("{\"sub\":\"u1\",\"roles\":[\"user\"]}", "s3cret");
        String forbidden = request(port,
                "GET /admin/users HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + user + "\r\n\r\n");
        assertTrue(forbidden.startsWith("HTTP/1.1 403 Forbidden"), forbidden);

        // token com o role = 200
        String admin = hs256("{\"sub\":\"u1\",\"roles\":[\"admin\"]}", "s3cret");
        String ok = request(port,
                "GET /admin/users HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + admin + "\r\n\r\n");
        assertTrue(ok.startsWith("HTTP/1.1 200 OK"), ok);
        assertEquals("admin-users", bodyOf(ok));
    }

    // §4.3: escalar — o escopo mais profundo vence (headers:false desliga o
    // hardening so no prefixo), o default global segue nas rotas sem match.
    @Test
    void scalarOverrideDeepestWins(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.security()
                    var api = mapOf("headers", false)
                    app.policy("/api", api)
                    app.get("/api/x") { return "api" }
                    app.get("/web") { return "web" }
                    app.listen(PORT)
                }
                """);
        String web = request(port, "GET /web HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(web.startsWith("HTTP/1.1 200 OK"), web);
        assertNotNull(headerLine(web, "Content-Security-Policy"), web);

        String api = request(port, "GET /api/x HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(api.startsWith("HTTP/1.1 200 OK"), api);
        assertNull(headerLine(api, "Content-Security-Policy"),
                "scope headers:false desliga o hardening: " + api);
    }

    // §4.3: listas somam — o publicPath global sobrevive ao merge do escopo "*"
    // (senao o filho o removeria), e o path do escopo tambem fica publico.
    @Test
    void publicPathsAccumulateAcrossScopes(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.security(mapOf("sessionHeader", "authorization", "publicPaths", "/open"))
                    app.policy("*", mapOf("publicPaths", "/api/open"))
                    app.get("/open") { return "open" }
                    app.get("/api/open") { return "api-open" }
                    app.get("/closed") { return "closed" }
                    app.listen(PORT)
                }
                """);
        assertTrue(request(port, "GET /open HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200 OK"));
        assertTrue(request(port, "GET /api/open HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200 OK"));
        String closed = request(port, "GET /closed HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(closed.startsWith("HTTP/1.1 401 Unauthorized"), closed);
    }

    // §4.3: roles somam — global "user" + escopo "/admin" "admin"; o escopo
    // nao remove o role global (as duas roles sao exigidas sob /admin).
    @Test
    void rolesAccumulateAcrossScopes(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    auth.secret("s3cret")
                    var app = web.app()
                    app.security(mapOf("roles", "user"))
                    app.policy("/admin", mapOf("roles", "admin"))
                    app.get("/admin/x") { return "ax" }
                    app.get("/other") { return "ot" }
                    app.listen(PORT)
                }
                """);
        String bothToken = hs256("{\"sub\":\"u1\",\"roles\":[\"user\",\"admin\"]}", "s3cret");
        String both = request(port,
                "GET /admin/x HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + bothToken + "\r\n\r\n");
        assertTrue(both.startsWith("HTTP/1.1 200 OK"), both);

        String onlyAdmin = hs256("{\"sub\":\"u1\",\"roles\":[\"admin\"]}", "s3cret");
        String missingUser = request(port,
                "GET /admin/x HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + onlyAdmin + "\r\n\r\n");
        assertTrue(missingUser.startsWith("HTTP/1.1 403 Forbidden"), missingUser);

        String onlyUser = hs256("{\"sub\":\"u1\",\"roles\":[\"user\"]}", "s3cret");
        String missingAdmin = request(port,
                "GET /admin/x HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + onlyUser + "\r\n\r\n");
        assertTrue(missingAdmin.startsWith("HTTP/1.1 403 Forbidden"), missingAdmin);

        // fora do escopo, so o role global vale
        String other = request(port,
                "GET /other HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + onlyUser + "\r\n\r\n");
        assertTrue(other.startsWith("HTTP/1.1 200 OK"), other);
    }

    // §4.3/§4.4: opts por endpoint (F3) — o endpoint vence o escopo (mais
    // profundo); rota sem opts herda o escopo; fora do escopo vale o global.
    @Test
    void endpointPolicyOverridesScope(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.security()
                    app.policy("/api", mapOf("headers", false))
                    var on = mapOf("headers", true)
                    app.get("/api/show", on) { return "show" }
                    app.get("/api/hide") { return "hide" }
                    app.get("/other") { return "other" }
                    app.listen(PORT)
                }
                """);
        String show = request(port, "GET /api/show HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(show.startsWith("HTTP/1.1 200 OK"), show);
        assertNotNull(headerLine(show, "Content-Security-Policy"),
                "endpoint headers:true vence o escopo: " + show);

        String hide = request(port, "GET /api/hide HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(hide.startsWith("HTTP/1.1 200 OK"), hide);
        assertNull(headerLine(hide, "Content-Security-Policy"),
                "rota sem opts herda headers:false do escopo: " + hide);

        String other = request(port, "GET /other HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(other.startsWith("HTTP/1.1 200 OK"), other);
        assertNotNull(headerLine(other, "Content-Security-Policy"),
                "fora do escopo vale o default global: " + other);
    }

    // F4 (D-HTTP-POLICIES §4.5): os corpos declarados vem da politica EFETIVA —
    // o escopo herda a chave global que nao declara e sobrepoe a que declara.
    @Test
    void responsesUseEffectivePolicy(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    auth.secret("s3cret")
                    var app = web.app()
                    val g: Map<String, Object> = mapOf()
                    val authv: Object = true
                    g.put("auth", authv)
                    val gresp: Object = mapOf("unauthorized", "{\\"error\\":\\"G401\\"}")
                    g.put("responses", gresp)
                    app.security(g)
                    val a: Map<String, Object> = mapOf()
                    val roles: Object = "admin"
                    a.put("roles", roles)
                    val aresp: Object = mapOf("forbidden", "{\\"error\\":\\"S403\\"}")
                    a.put("responses", aresp)
                    app.policy("/admin", a)
                    app.get("/admin/x") { return "ax" }
                    app.get("/me") { return "me" }
                    app.listen(PORT)
                }
                """);
        // global 401 payload
        String me = request(port, "GET /me HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(me.startsWith("HTTP/1.1 401 Unauthorized"), me);
        assertEquals("{\"error\":\"G401\"}", bodyOf(me));

        // under /admin: the 401 inherits the global unauthorized payload
        String anon = request(port, "GET /admin/x HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(anon.startsWith("HTTP/1.1 401 Unauthorized"), anon);
        assertEquals("{\"error\":\"G401\"}", bodyOf(anon));

        // 403 uses the scope's own forbidden payload
        String user = hs256("{\"sub\":\"u1\",\"roles\":[\"user\"]}", "s3cret");
        String forbidden = request(port,
                "GET /admin/x HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + user + "\r\n\r\n");
        assertTrue(forbidden.startsWith("HTTP/1.1 403 Forbidden"), forbidden);
        assertEquals("{\"error\":\"S403\"}", bodyOf(forbidden));
    }

    // F4: `notFound` from the effective policy feeds both 404 paths
    // (`return null` and unknown path).
    @Test
    void notFoundPayloadFromEffectivePolicy(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    val g: Map<String, Object> = mapOf()
                    val gresp: Object = mapOf("notFound", "{\\"error\\":\\"NF\\"}")
                    g.put("responses", gresp)
                    app.security(g)
                    app.get("/gone/:id") {
                        var id = param("id").toInt()
                        if (id == 1) {
                            return "one"
                        }
                        return null
                    }
                    app.listen(PORT)
                }
                """);
        // documented absence (`return null`) -> loop 404 with the declared body
        String gone = request(port, "GET /gone/2 HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(gone.startsWith("HTTP/1.1 404 Not Found"), gone);
        assertEquals("{\"error\":\"NF\"}", bodyOf(gone));

        // unknown path -> final 404 with the declared body
        String nope = request(port, "GET /nope HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(nope.startsWith("HTTP/1.1 404 Not Found"), nope);
        assertEquals("{\"error\":\"NF\"}", bodyOf(nope));
    }

    // F5 (D-HTTP-POLICIES §10 risk 3): a chave do rate-limit é IP + padrão de
    // rota — escopos com limites distintos não compartilham contador, e o
    // limite global segue valendo na sua própria rota.
    @Test
    void perRouteRateLimitKeys(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.security(mapOf("rateLimit", "3/60"))
                    app.policy("/a", mapOf("rateLimit", "1/60"))
                    app.policy("/b", mapOf("rateLimit", "2/60"))
                    app.get("/a/x") { return "a" }
                    app.get("/b/x") { return "b" }
                    app.get("/c") { return "c" }
                    app.listen(PORT)
                }
                """);
        // /a/x: limite 1 -> a 1a passa, a 2a estoura
        assertTrue(request(port, "GET /a/x HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200 OK"));
        String a2 = request(port, "GET /a/x HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(a2.startsWith("HTTP/1.1 429 Too Many Requests"), a2);

        // /b/x: limite 2 e contador PRÓPRIO -> 200,200,429 (não herda o 429 de /a)
        assertTrue(request(port, "GET /b/x HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200 OK"));
        assertTrue(request(port, "GET /b/x HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200 OK"));
        String b3 = request(port, "GET /b/x HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(b3.startsWith("HTTP/1.1 429 Too Many Requests"), b3);

        // /c: limite global 3 com contador próprio -> ainda passa
        assertTrue(request(port, "GET /c HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200 OK"));
    }

    // §8 (R6): prefixo invalido falha no build (nunca vira no-op silencioso).
    @Test
    void invalidPolicyPrefixFailsAtStartup(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.policy("admin", mapOf("roles", "admin"))
                    app.listen(8100)
                }
                """);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(),
                "compilation should succeed: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("process hung: " + out);
        }
        assertTrue(process.exitValue() != 0, "invalid prefix must fail: " + out);
        assertTrue(out.contains("policy prefix must start with"), out);
    }

    // F6 (gap honesto): app.policy e route-opts nao existem fora do JVM -> WEB006.
    @Test
    void policyGapOnNativeAndJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.policy("/admin", mapOf("roles", "admin"))
                    app.get("/admin/x", mapOf("roles", "admin")) { return "x" }
                    app.listen(8100)
                }
                """);
        for (Target target : new Target[] {Target.NATIVE, Target.JS}) {
            CompilationResult result = driver.compile(source, tempDir.resolve("out-" + target), target);
            var diagnostics = result.diagnostics().getDiagnostics();
            assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("WEB006")),
                    "app.policy()/route-opts devem dar WEB006 no " + target + ", got: " + diagnostics);
        }
    }
}
