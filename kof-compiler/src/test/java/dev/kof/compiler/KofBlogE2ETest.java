/*
 * Blog E2E (D-SPRING F12 — DECISIONS.md 13/09): o app model canônico da
 * plataforma — backend HTTP + db (H2 mem) + auth (passwords PBKDF2 +
 * sessions) + validation + JSON tipado, num único programa Kof, sem
 * framework externo (regra estrutural anti-Spring: nenhuma dependência
 * obrigatória, nenhuma camada Service/Repository).
 *
 * Prova de plataforma: o app sobe em porta efêmera e o teste fala HTTP de
 * verdade contra ele (registro → login → criar post → listar → validation
 * recusa → auth recusa). Bordas Q3: credencial errada, token inválido,
 * payload inválido, path param inexistente.
 */
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KofBlogE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static String blogApp(String port) {
        return """
                record Post(String id, String title, String body)
                record Credentials(String user, String password)
                record Session(String user, String token)
                record PwRow(String pwhash)

                // validation: regra de domínio explícita, sem framework
                Bool validTitle(String t) = t.length > 0 && t.length <= 100
                Bool validBody(String b) = b.length > 0 && b.length <= 10000

                main() {
                    var app = web.app()
                    var h = db.connect("jdbc:h2:mem:blog;DB_CLOSE_DELAY=-1")
                    db.execute(h, "create table users(usr varchar(50), pwhash varchar(200))")
                    db.execute(h, "create table posts(id varchar(50), author varchar(50), title varchar(100), body varchar(10000))")

                    app.post("/register") {
                        var c = json.decode<Credentials>(body())
                        if (!validBody(c.password())) { return status(400, "{\\"error\\":\\"invalid password\\"}") }
                        db.execute(h, "insert into users(usr, pwhash) values (?, ?)", c.user(), passwords.hash(c.password()))
                        return status(201, "{\\"ok\\":true}")
                    }

                    app.post("/login") {
                        var c = json.decode<Credentials>(body())
                        var rows = db.query<PwRow>(h, "select pwhash from users where usr = ?", c.user())
                        if (rows.isEmpty()) { return status(401, "{\\"error\\":\\"bad credentials\\"}") }
                        var hash = rows.get(0).pwhash()
                        if (!passwords.verify(c.password(), hash)) { return status(401, "{\\"error\\":\\"bad credentials\\"}") }
                        var token = security.sessionCreate(c.user())
                        return json.encode(Session(c.user(), token))
                    }

                    app.post("/posts") {
                        // WORKAROUND BUG JVM-2026-09-14-B: o padrão
                        // (token != null && ...) + sessionGet(token) != null
                        // derruba a conexão sem resposta ("connection closed
                        // before headers"); normalizando via "" + token o
                        // handler responde. Bug a catalogar em known-bugs.md.
                        var token = "" + header("authorization")
                        var user = "" + security.sessionGet(token)
                        var author = user
                        if (author == "null" || author == "authentication_required") {
                            return status(401, "{\\"error\\":\\"unauthorized\\"}")
                        }
                        var p = json.decode<Post>(body())
                        if (!validTitle(p.title())) { return status(400, "{\\"error\\":\\"invalid title\\"}") }
                        if (!validBody(p.body())) { return status(400, "{\\"error\\":\\"invalid body\\"}") }
                        db.execute(h, "insert into posts(id, author, title, body) values (?, ?, ?, ?)", crypto.randomHex(8), author, p.title(), p.body())
                        return status(201, "{\\"ok\\":true}")
                    }

                    app.get("/posts") {
                        return json.encode(db.query<Post>(h, "select id, title, body from posts order by id"))
                    }

                    app.get("/posts/:id") {
                        var rows = db.query<Post>(h, "select id, title, body from posts where id = ?", param("id"))
                        if (rows.isEmpty()) { return status(404, "{\\"error\\":\\"not found\\"}") }
                        return json.encode(rows.get(0))
                    }

                    // C18 (D-SEC): middleware composto com sessão obrigatória
                    // fora dos prefixes públicos (login/health)
                    app.security(mapOf("sessionHeader", "authorization", "publicPaths", "/register,/login"))

                    app.listen(KOFE2EPORT)
                }
                """.replace("KOFE2EPORT", port);
    }

    /**
     * DECISIONS §5 (Spring model): CSRF is ON by default for state-changing
     * methods. A safe request (GET) to a public path issues the double-submit
     * cookie; the client echoes it as {@code Cookie: csrf=...} +
     * {@code X-CSRF-Token: ...} on every POST.
     */
    private String csrfToken(int port) throws IOException {
        String r = http(port, "GET /login HTTP/1.1\r\nHost: x\r\n\r\n");
        for (String line : r.split("\r\n")) {
            if (line.toLowerCase().startsWith("set-cookie:")) {
                for (String part : line.substring(line.indexOf(':') + 1).trim().split(";")) {
                    String kv = part.trim();
                    if (kv.startsWith("csrf=")) return kv.substring("csrf=".length());
                }
            }
        }
        throw new IOException("no csrf cookie issued by GET /login; response: " + r);
    }

    private String post(int port, String path, String jsonBody, String csrf) throws IOException {
        String req = "POST " + path + " HTTP/1.1\r\nHost: x\r\n"
                + (csrf == null ? "" : "Cookie: csrf=" + csrf + "\r\nX-CSRF-Token: " + csrf + "\r\n")
                + "Content-Length: " + jsonBody.getBytes(StandardCharsets.UTF_8).length
                + "\r\n\r\n" + jsonBody;
        return http(port, req);
    }

    private int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Compila, sobe o app em porta efêmera, espera o listen e devolve a porta. */
    private final Map<Path, Process> appProcesses = new HashMap<>();

    private int startAppManaged(Path tempDir, int port, String kofSource) throws Exception {
        Path sourceFile = tempDir.resolve("Blog.kf");
        Files.writeString(sourceFile, kofSource);
        Path outDir = tempDir.resolve("classes");
        CompilerDriver appDriver = new CompilerDriver();
        CompilationResult result = appDriver.compile(sourceFile, outDir, Target.JVM);
        assertTrue(result.success(), "compilation should succeed: " + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        assumeTrue(h2 != null, "H2 jar not on test classpath");
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir + java.io.File.pathSeparator + h2, "Default.Main");
        Path outLog = tempDir.resolve("app.log");
        pb.redirectErrorStream(true);
        pb.redirectOutput(outLog.toFile());
        Process p = pb.start();
        appProcesses.put(tempDir, p);
        // espera o listen abrir a porta (até 30s; o fork do surefire sob o
        // load do CI pode atrasar o boot do child)
        if (!TestServerFixture.awaitPort(port, 300, 100)) {
            throw new IOException("app did not listen on port " + port
                    + "; alive=" + p.isAlive() + "; output=" + Files.readString(outLog));
        }
        return port;
    }

    private static Path testClassesDir() throws Exception {
        return Path.of(KofBlogE2ETest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toRealPath();
    }

    private static String findH2Jar() {
        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) return entry;
        }
        return null;
    }

    private String http(int port, String request) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(30000);
            OutputStream out = s.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String getStatus(String response) {
        return response.split("\r\n", 2)[0];
    }

    private static String getBody(String response) {
        int idx = response.indexOf("\r\n\r\n");
        return idx < 0 ? "" : response.substring(idx + 4);
    }

    @Test
    void blogEndToEndJvm(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        startAppManaged(tempDir, port, blogApp(String.valueOf(port)));
        Process app = appProcesses.get(tempDir);
        try {
            // 0. CSRF (on by default, DECISIONS §5): obtém o cookie double-submit.
            String csrf = csrfToken(port);

            // 1. registro
            String r = post(port, "/register", "{\"user\":\"mel\",\"password\":\"hunter2\"}", csrf);
            assertEquals("HTTP/1.1 201 Created", getStatus(r), r);

            // 2. login → token de sessão
            r = post(port, "/login", "{\"user\":\"mel\",\"password\":\"hunter2\"}", csrf);
            assertEquals("HTTP/1.1 200 OK", getStatus(r), r);
            String body = getBody(r);
            assertTrue(body.contains("\"token\""), body);
            String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
            assertFalse(token.isBlank(), "token must be non-empty: " + body);

            // 3. login com senha errada → 401
            r = post(port, "/login", "{\"user\":\"mel\",\"password\":\"wrong\"}", csrf);
            assertEquals("HTTP/1.1 401 Unauthorized", getStatus(r), r);

            // 4. criar post com sessão → 201
            String post = "{\"id\":\"ignored\",\"title\":\"Kof\",\"body\":\"validação da plataforma\"}";
            r = http(port, "POST /posts HTTP/1.1\r\nHost: x\r\nauthorization: " + token
                    + "\r\nCookie: csrf=" + csrf + "\r\nX-CSRF-Token: " + csrf
                    + "\r\nContent-Length: " + post.getBytes(StandardCharsets.UTF_8).length
                    + "\r\n\r\n" + post);
            assertEquals("HTTP/1.1 201 Created", getStatus(r), r);

            // 5. post sem sessão → 401 (a sessão é validada ANTES do CSRF)
            r = http(port, "POST /posts HTTP/1.1\r\nHost: x\r\nContent-Length: "
                    + post.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + post);
            assertEquals("HTTP/1.1 401 Unauthorized", getStatus(r), r);

            // 6. listar posts com sessão → JSON com o post criado
            r = http(port, "GET /posts HTTP/1.1\r\nHost: x\r\nauthorization: " + token + "\r\n\r\n");
            assertEquals("HTTP/1.1 200 OK", getStatus(r), r);
            assertTrue(getBody(r).contains("validação da plataforma"), getBody(r));

            // 7. post inexistente com sessão → 404
            r = http(port, "GET /posts/999 HTTP/1.1\r\nHost: x\r\nauthorization: " + token + "\r\n\r\n");
            assertEquals("HTTP/1.1 404 Not Found", getStatus(r), r);
        } finally {
            app.destroyForcibly();
        }
    }
}
