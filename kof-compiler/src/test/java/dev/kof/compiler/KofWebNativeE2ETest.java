package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WEB002 — kof.web servidor para o target Nativo.
 *
 * T1: accept loop + resposta 200/hello (commit 89ac0d9)
 * T2: parse METHOD+PATH + match literal → 200/404 (commit 6ad63f8)
 * T3 (em andamento): dispatch do handler lambda via trampolim invoke().
 */
class KofWebNativeE2ETest {

    private Process serverProcess;

    @AfterEach
    void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) { }
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
    }

    private static final String SERVER_T1 = """
            main() {
                var app = web.app()
                app.listen(PORT)
            }
            """;

    private static final String SERVER_T2 = """
            main() {
                var app = web.app()
                app.get("/hello") {
                    return "ok-matched"
                }
                app.listen(PORT)
            }
            """;

    // ---------------- helpers ----------------

    private static int freePort() throws IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private Process startServer(Path tempDir, String source) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, source.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path binary = tempDir.resolve("classes/Default/Main");
        assertTrue(Files.exists(binary), "native binary should exist");
        ProcessBuilder pb = new ProcessBuilder(binary.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive()) throw new IOException("server died early");
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                return p;
            } catch (IOException e) { Thread.sleep(50); }
        }
        p.destroyForcibly();
        throw new IOException("did not come up on port " + port);
    }

    private int portFromServer(String source) throws IOException {
        return Integer.parseInt(source.split("app.listen\\(")[1].split("\\)")[0]);
    }

    private String httpGet(int port, String path) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---------------- testes ----------------

    @Test
    void nativeServerAcceptsAndResponds200(@TempDir Path tempDir) throws Exception {
        serverProcess = startServer(tempDir, SERVER_T1);
        assertTrue(true);
    }

    @Test
    void nativeServerMatchesLiteralRoute(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, SERVER_T2.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("classes/Default/Main").toString());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        long deadline = System.currentTimeMillis() + 5000;
        boolean up = false;
        while (System.currentTimeMillis() < deadline && !up) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                up = true;
            } catch (IOException e) { Thread.sleep(50); }
        }
        assertTrue(up, "server should accept connections");
        String match = httpGet(port, "/hello");
        assertTrue(match.contains("200"), "match: " + match);
        assertTrue(match.endsWith("ok-matched"), "match body: " + match);
        String miss = httpGet(port, "/estanaoexiste");
        assertTrue(miss.contains("404"), "miss: " + miss);
        assertTrue(miss.endsWith("Not Found"), "miss body: " + miss);
    }

    @Test
    void nativeServerDispatchesLambdaHandler(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, SERVER_T2.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("classes/Default/Main").toString());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        long deadline = System.currentTimeMillis() + 5000;
        boolean up = false;
        while (System.currentTimeMillis() < deadline && !up) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                up = true;
            } catch (IOException e) { Thread.sleep(50); }
        }
        assertTrue(up);
        String r = httpGet(port, "/hello");
        assertTrue(r.contains("HTTP/1.1 200"), "status: " + r);
        assertTrue(r.endsWith("ok-matched"), "body should come from handler, got: " + r);
    }

    private static final String SERVER_T4 = """
            main() {
                var app = web.app()
                app.post("/echo") {
                    return "got: " + body()
                }
                app.listen(PORT)
            }
            """;

    @Test
    void nativeServerReadsBodyContext(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, SERVER_T4.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("classes/Default/Main").toString());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        long deadline = System.currentTimeMillis() + 5000;
        boolean up = false;
        while (System.currentTimeMillis() < deadline && !up) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                up = true;
            } catch (IOException e) { Thread.sleep(50); }
        }
        assertTrue(up);
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.getOutputStream().write("POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nhello"
                    .getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            String r = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(r.contains("200"), "status: " + r);
            assertTrue(r.endsWith("got: hello"), "body() should echo, got: " + r);
        }
    }

    private static final String SERVER_T5 = """
            main() {
                var app = web.app()
                app.get("/hi") {
                    return method() + "|" + path() + "|" + query("name")
                }
                app.listen(PORT)
            }
            """;

    @Test
    void nativeServerExposesMethodPathQuery(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, SERVER_T5.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("classes/Default/Main").toString());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        long deadline = System.currentTimeMillis() + 5000;
        boolean up = false;
        while (System.currentTimeMillis() < deadline && !up) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                up = true;
            } catch (IOException e) { Thread.sleep(50); }
        }
        assertTrue(up);
        String r = httpGet(port, "/hi?name=mel&other=1");
        assertTrue(r.contains("200"), "status: " + r);
        assertTrue(r.endsWith("GET|/hi|mel"), "method|path|query should be exposed, got: " + r);
    }

    private static final String SERVER_T6 = """
            main() {
                var app = web.app()
                app.get("/hdr") {
                    return header("X-Token")
                }
                app.listen(PORT)
            }
            """;

    @Test
    void nativeServerExposesHeader(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, SERVER_T6.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("classes/Default/Main").toString());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        long deadline = System.currentTimeMillis() + 5000;
        boolean up = false;
        while (System.currentTimeMillis() < deadline && !up) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                up = true;
            } catch (IOException e) { Thread.sleep(50); }
        }
        assertTrue(up);
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.getOutputStream().write(
                    "GET /hdr HTTP/1.1\r\nHost: x\r\nX-Token: secret\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            String r = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(r.contains("200"), "status: " + r);
            assertTrue(r.endsWith("secret"), "header(X-Token) should be exposed, got: " + r);
        }
    }

    private static final String SERVER_T7 = """
            main() {
                var app = web.app()
                app.get("/users/:id") {
                    return "user " + param("id")
                }
                app.post("/users/:id/posts/:pid") {
                    return param("id") + "-" + param("pid")
                }
                app.listen(PORT)
            }
            """;

    @Test
    void nativeServerExtractsPathParams(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, SERVER_T7.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("classes/Default/Main").toString());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        long deadline = System.currentTimeMillis() + 5000;
        boolean up = false;
        while (System.currentTimeMillis() < deadline && !up) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                up = true;
            } catch (IOException e) { Thread.sleep(50); }
        }
        assertTrue(up);
        String single = httpGet(port, "/users/42");
        assertTrue(single.contains("200"), "status: " + single);
        assertTrue(single.endsWith("user 42"), "param(id) should be extracted, got: " + single);

        try (Socket s = new Socket("127.0.0.1", port)) {
            s.getOutputStream().write(
                    "POST /users/7/posts/99 HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            String r = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(r.contains("200"), "status: " + r);
            assertTrue(r.endsWith("7-99"), "two params should be extracted, got: " + r);
        }
    }

    private static final String SERVER_T8 = """
            main() {
                var app = web.app()
                app.get("/created") {
                    headerSet("X-Kind", "demo")
                    return status(201, "made")
                }
                app.listen(PORT)
            }
            """;

    @Test
    void nativeServerSetsStatusAndResponseHeader(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, SERVER_T8.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("classes/Default/Main").toString());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        long deadline = System.currentTimeMillis() + 5000;
        boolean up = false;
        while (System.currentTimeMillis() < deadline && !up) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                up = true;
            } catch (IOException e) { Thread.sleep(50); }
        }
        assertTrue(up);
        String r = httpGet(port, "/created");
        assertTrue(r.contains("HTTP/1.1 201 Created"), "status line should be 201 Created, got: " + r);
        assertTrue(r.contains("X-Kind: demo"), "response header should be set, got: " + r);
        assertTrue(r.endsWith("made"), "body should be returned, got: " + r);
    }
}
