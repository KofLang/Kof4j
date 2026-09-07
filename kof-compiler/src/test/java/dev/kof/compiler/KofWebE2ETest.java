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
class KofWebE2ETest {

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

    private static final String WEB_APP = """
            record User(String name, Int age)

            main() {
                var app = web.app()
                app.use {
                    if (header("x-auth") == "secret") {
                        return null
                    }
                    return "{\\"error\\": \\"unauthorized\\"}"
                }
                app.get("/hello") {
                    return "Hello from Kof"
                }
                app.get("/users/:id") {
                    return "user " + param("id") + " q=" + query("name")
                }
                app.get("/agent") {
                    return "agent=" + header("user-agent")
                }
                app.get("/me") {
                    return method() + " " + path()
                }
                app.post("/echo") {
                    return "got:" + body()
                }
                app.post("/user") {
                    var user = json.decode<User>(body())
                    return json.encode(user)
                }
                app.listen(PORT)
            }
            """;

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
        int attempt = 0;
        while (attempt < 40) {
            if (!serverProcess.isAlive()) {
                String out = new String(serverProcess.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
                throw new IOException("server exited early: " + out);
            }
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                return port;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            attempt++;
        }
        throw new IOException("server did not start listening");
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
        String app = """
                main() {
                    var app = web.app()
                    app.health("/health")
                    app.use {
                        if (header("x-auth") == "secret") {
                            return null
                        }
                        return "{\\"error\\": \\"unauthorized\\"}"
                    }
                    app.get("/hello") {
                        return "Hello from Kof"
                    }
                    app.listen(PORT)
                }
                """;
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
        String app = """
                main() {
                    var app = web.app()
                    app.delete("/item/:id") {
                        return "deleted:" + param("id")
                    }
                    app.listen(PORT)
                }
                """;
        int port = startServer(tempDir, app);
        String r = request(port, "DELETE /item/7 HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(bodyOf(r).equals("deleted:7"), r);
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
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.get("/a") { return "A" }
                    app.get("/b") { return "B" }
                    app.listen(PORT)
                }
                """);
        assertEquals("A", bodyOf(request(port, "GET /a HTTP/1.1\r\nHost: x\r\n\r\n")));
        assertEquals("B", bodyOf(request(port, "GET /b HTTP/1.1\r\nHost: x\r\n\r\n")));
    }

    @Test
    void sseAndWsGapOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.sse("/events") { return "x" }
                    app.ws("/chat") { return "x" }
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        var diagnostics = result.diagnostics().getDiagnostics();
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("WEB003")),
                "Should have WEB003, got: " + diagnostics);
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("WEB004")),
                "Should have WEB004, got: " + diagnostics);
    }
}
