package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §784 — the response headers were a map keyed by name, so a second
 * {@code headerSet("Set-Cookie", ...)} overwrote the first and only the last
 * cookie reached the client. Set-Cookie may repeat (RFC 6265 §3): each value
 * is now its own header line. A CR/LF in a header name or value (response
 * splitting) is refused instead of being written to the wire.
 */
class WebSetCookieE2ETest {

    private static final String JAVA_BIN = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    private Process server;

    @AfterEach
    void stop() {
        if (server != null) server.destroyForcibly();
    }

    private int start(Path dir, String kf) throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        Path source = dir.resolve("App.kf");
        Files.writeString(source, kf.replace("PORT", String.valueOf(port)));
        Path out = dir.resolve("classes");
        CompilationResult r = new CompilerDriver().compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        server = new ProcessBuilder(JAVA_BIN, "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        TestServerFixture.awaitListening(server, port);
        return port;
    }

    private static String request(int port, String raw) throws Exception {
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new String(socket.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void everySetCookieIsSentAndCrLfIsRefused(@TempDir Path dir) throws Exception {
        int port = start(dir, """
                main() {
                    var app = web.app()
                    app.get("/login") {
                        headerSet("Set-Cookie", "a=1; Path=/")
                        headerSet("Set-Cookie", "b=2; Path=/")
                        headerSet("X-One", "first")
                        headerSet("X-One", "second")
                        return "ok"
                    }
                    app.get("/inject") {
                        headerSet("X-Bad", "v\\r\\nInjected: 1")
                        return "ok"
                    }
                    app.listen(PORT)
                }
                """);
        String r = request(port, "GET /login HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.contains("\r\nSet-Cookie: a=1; Path=/\r\n") && r.contains("\r\nSet-Cookie: b=2; Path=/\r\n"),
                "both cookies, one line each: " + r);
        assertTrue(r.contains("\r\nX-One: second\r\n") && !r.contains("X-One: first"),
                "other headers keep last-wins: " + r);
        String bad = request(port, "GET /inject HTTP/1.1\r\nHost: x\r\n\r\n");
        assertFalse(bad.contains("\r\nInjected: 1"), "CR/LF must never split the response: " + bad);
    }
}
