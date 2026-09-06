package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for {@code kof.http} — the HTTP client.
 *
 * A Kof server ({@code web.app()}) runs as a subprocess and the client
 * (compiled Kof code using {@code http.get/post/status}) talks to it over
 * real sockets. No external servers, no Docker.
 */
class KofHttpE2ETest {

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

    private static final String SERVER = """
            main() {
                var app = web.app()
                app.get("/hello") {
                    return "Hello from Kof"
                }
                app.post("/echo") {
                    return "got:" + body()
                }
                app.get("/agent") {
                    return "agent=" + header("user-agent")
                }
                app.listen(PORT)
            }
            """;

    private int startServer(Path tempDir) throws IOException {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, SERVER.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        int attempt = 0;
        while (attempt < 40) {
            if (!serverProcess.isAlive()) {
                String out = new String(serverProcess.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
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
                }
            }
            attempt++;
        }
        serverProcess.destroyForcibly();
        throw new IOException("server did not come up on port " + port);
    }

    private static int freePort() throws IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private String runJvm(Path tempDir, String kofSource, String expected) throws IOException {
        Path source = tempDir.resolve("Client.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("client-classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-Dfile.encoding=UTF-8", "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    @Test
    void getPostAndStatus(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        runJvm(tempDir, """
                main() {
                    println(http.get("http://127.0.0.1:%d/hello"))
                    println(http.status("http://127.0.0.1:%d/hello"))
                    println(http.post("http://127.0.0.1:%d/echo", "abc"))
                    println(http.status("http://127.0.0.1:%d/nope"))
                    println(http.get("http://127.0.0.1:%d/nope"))
                }
                """.formatted(port, port, port, port, port), "Hello from Kof\n200\ngot:abc\n404\n{\"error\": \"not found\"}");
    }

    @Test
    void headersAreSent(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        runJvm(tempDir, """
                main() {
                    println(http.get("http://127.0.0.1:%d/agent", "User-Agent: kof-client/1.0"))
                }
                """.formatted(port), "agent=kof-client/1.0");
    }

    @Test
    void nativeCompilesAndServesGet(@TempDir Path tempDir) throws IOException {
        // HTTP002 (Native): asm puro — socket+connect+read/parse HTTP/1.1.
        // Sobe o servidor KofJVM (existente) e bate nele com um binário nativo.
        int port = startServer(tempDir);
        Path source = tempDir.resolve("NatCli.kf");
        Files.writeString(source, """
                main() {
                    println(http.get("http://127.0.0.1:%d/hello"))
                    println(http.status("http://127.0.0.1:%d/hello"))
                    println(http.get("http://127.0.0.1:%d/nope"))
                }
                """.formatted(port, port, port));
        Path outDir = tempDir.resolve("nat-out");
        // driver fresco: o driver deste teste já carregou as rotas do server
        // (web.app lambdas) e o IR vaza para o binario nativo do cliente.
        CompilationResult result = new CompilerDriver().compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native should compile http.get: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default").resolve("Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        try {
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit, out=" + out);
            assertEquals("Hello from Kof\n200\n{\"error\": \"not found\"}", out);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void nativePostEcho(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        Path source = tempDir.resolve("NatPost.kf");
        Files.writeString(source, """
                main() {
                    println(http.post("http://127.0.0.1:%d/echo", "abc"))
                }
                """.formatted(port));
        Path outDir = tempDir.resolve("nat-post");
        CompilationResult result = new CompilerDriver().compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native should compile http.post: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default").resolve("Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        try {
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit, out=" + out);
            assertEquals("got:abc", out);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void nativeAndJsSupportHttp(@TempDir Path tempDir) throws IOException {
        // HTTP002: agora os 3 targets compilam http.get (Native via asm puro).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    println(http.get("http://example.com"))
                }
                """);
        CompilationResult nativeResult = new CompilerDriver().compile(source, tempDir.resolve("native"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native should compile http.get: " + nativeResult.diagnostics().getDiagnostics());
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(jsResult.success(), "JS should support http.get via fetch/Java HttpClient: " + jsResult.diagnostics().getDiagnostics());
    }

    @Test
    void jsHttpGet(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        Path source = tempDir.resolve("JsClient.kf");
        Files.writeString(source, """
                main() {
                    println(http.get("http://127.0.0.1:%d/hello"))
                }
                """.formatted(port));
        Path outDir = tempDir.resolve("js-out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "" + result.diagnostics().getDiagnostics());
        String entry = outDir.resolve("Default.mjs").toString();
        if (!Files.exists(Path.of(entry))) {
            try (var s = Files.walk(outDir)) {
                entry = s.filter(p -> p.toString().endsWith(".mjs")).findFirst().map(Path::toString).orElse(null);
            }
        }
        assertNotNull(entry, "JS entry not found");
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream beos = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(Path.of(entry), baos, System.in, beos, false, new String[0]);
        assertEquals(0, ec, "JS exit code should be 0, stderr: " + beos.toString());
        String out = baos.toString().replace("\r\n", "\n").trim();
        assertEquals("Hello from Kof", out);
    }

    @Test
    void nativeHttpGetTimesOut(@TempDir Path tempDir) throws Exception {
        // HTTP003 cauda (timeout): servidor aceita mas nunca responde; o SO_RCVTIMEO
        // (via http.timeout(1)) deve lançar "kof.http: timeout" em ~1s.
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            int port = ss.getLocalPort();
            Thread holder = new Thread(() -> {
                try (Socket s = ss.accept()) {
                    Thread.sleep(8000); // segura a conexão sem responder
                } catch (Exception ignored) { }
            });
            holder.setDaemon(true);
            holder.start();

            Path source = tempDir.resolve("NatTimeout.kf");
            Files.writeString(source, """
                    main() {
                        http.timeout(1)
                        try {
                            println("body=" + http.get("http://127.0.0.1:%d/"))
                        } catch (String e) {
                            println("caught: " + e)
                        }
                    }
                    """.formatted(port));
            Path outDir = tempDir.resolve("nat-timeout");
            CompilationResult result = new CompilerDriver().compile(source, outDir, Target.NATIVE);
            assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
            Path bin = outDir.resolve("Default").resolve("Main");
            long start = System.currentTimeMillis();
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            long elapsed = System.currentTimeMillis() - start;
            assertEquals(0, ec, "exit, out=" + out);
            assertTrue(out.contains("caught: kof.http: timeout"), "should time out, got: " + out);
            assertTrue(elapsed < 7000, "should time out in ~1s, took " + elapsed + "ms");
            holder.interrupt();
        }
    }

    @Test
    void nativeHttpRetryAndCircuit(@TempDir Path tempDir) throws Exception {
        // HTTP003 cauda (retry + circuit): retry numa servidor flaky (500 x2 -> 200)
        // e circuit breaker falha rápido num porta fechada.
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
        java.net.ServerSocket flaky = new java.net.ServerSocket(0);
        int flakyPort = flaky.getLocalPort();
        Thread server = new Thread(() -> {
            try {
                while (true) {
                    try (Socket c = flaky.accept()) {
                        c.setSoTimeout(2000);
                        int n = count.getAndIncrement();
                        String body = n < 2 ? "err" : "ok-" + (n + 1);
                        String status = n < 2 ? "500 Internal Server Error" : "200 OK";
                        String resp = "HTTP/1.1 " + status + "\r\nContent-Length: "
                                + body.length() + "\r\nConnection: close\r\n\r\n" + body;
                        c.getOutputStream().write(resp.getBytes(StandardCharsets.UTF_8));
                        c.getOutputStream().flush();
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        });
        server.setDaemon(true);
        server.start();

        int closedPort;
        try (java.net.ServerSocket cs = new java.net.ServerSocket(0)) {
            closedPort = cs.getLocalPort();
        }

        Path source = tempDir.resolve("NatResilience.kf");
        Files.writeString(source, """
                main() {
                    http.retry(2)
                    println("retry=" + http.get("http://127.0.0.1:%d/"))
                    http.circuit(2)
                    try { println(http.get("http://127.0.0.1:%d/")) } catch (String e) { println("fail1") }
                    try { println(http.get("http://127.0.0.1:%d/")) } catch (String e) { println("fail2") }
                    try { println(http.get("http://127.0.0.1:%d/")) } catch (String e) { println("open=" + e) }
                    http.circuit(0)
                    try { println(http.get("http://127.0.0.1:%d/")) } catch (String e) { println("reset") }
                }
                """.formatted(flakyPort, closedPort, closedPort, closedPort, closedPort));
        Path outDir = tempDir.resolve("nat-resilience");
        CompilationResult result = new CompilerDriver().compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default").resolve("Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "exit, out=" + out);
        assertEquals("retry=ok-3\nfail1\nfail2\nopen=kof.http circuit open\nreset", out);
    }
}