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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §773 — the JVM web server answered every response with
 * {@code Connection: close} and served one request per TCP connection. It now
 * keeps HTTP/1.1 connections open (RFC 9112 §9.3): several requests, pipelined
 * ones included, are served in order on one socket; {@code Connection: close},
 * HTTP/1.0 without {@code keep-alive} and an idle connection still close.
 */
class KofWebKeepAliveE2ETest extends ServerProcessSupport {

    private static final String APP = """
            main() {
                var app = web.app()
                app.get("/a") { return "alpha" }
                app.post("/echo") { return "got:" + body() }
                app.listen(PORT)
            }
            """;

    @TempDir Path tmp;

    private int startServer() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        Path source = tmp.resolve("App.kf");
        Files.writeString(source, APP.replace("PORT", String.valueOf(port)));
        Path out = tmp.resolve("classes");
        CompilationResult r = new CompilerDriver().compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        serverProcess = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        TestServerFixture.awaitListening(serverProcess, port);
        return port;
    }

    private static void send(Socket s, String raw) throws IOException {
        OutputStream out = s.getOutputStream();
        out.write(raw.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Test
    void sequentialRequestsShareOneConnection() throws Exception {
        int port = startServer();
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(3000);
            InputStream in = s.getInputStream();
            send(s, "GET /a HTTP/1.1\r\nHost: x\r\n\r\n");
            String first = TestServerFixture.readResponse(in);
            assertTrue(first.contains("Connection: keep-alive\r\n"), first);
            assertTrue(first.endsWith("\r\n\r\nalpha"), first);
            send(s, "GET /a HTTP/1.1\r\nHost: x\r\n\r\n");
            assertTrue(TestServerFixture.readResponse(in).endsWith("\r\n\r\nalpha"));
        }
    }

    @Test
    void pipelinedRequestsAreAnsweredInOrder() throws Exception {
        int port = startServer();
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(3000);
            InputStream in = s.getInputStream();
            send(s, "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 4\r\n\r\nOlá"
                    + "GET /a HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n");
            assertTrue(TestServerFixture.readResponse(in).endsWith("\r\n\r\ngot:Olá"));
            String last = TestServerFixture.readResponse(in);
            assertTrue(last.contains("Connection: close\r\n"), last);
            assertTrue(last.endsWith("\r\n\r\nalpha"), last);
            assertEquals(-1, in.read(), "the server closes after Connection: close");
        }
    }

    @Test
    void http10WithoutKeepAliveCloses() throws Exception {
        int port = startServer();
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(3000);
            send(s, "GET /a HTTP/1.0\r\n\r\n");
            String r = TestServerFixture.readResponse(s.getInputStream());
            assertTrue(r.contains("Connection: close\r\n"), r);
            assertEquals(-1, s.getInputStream().read());
        }
    }

    @Test
    void anIdleConnectionIsClosedByTheServer() throws Exception {
        int port = startServer();
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(15000);
            send(s, "GET /a HTTP/1.1\r\nHost: x\r\n\r\n");
            TestServerFixture.readResponse(s.getInputStream());
            long start = System.nanoTime();
            assertEquals(-1, s.getInputStream().read(), "idle keep-alive connection must be closed");
            assertTrue(System.nanoTime() - start < 10_000_000_000L, "closed by the 5s idle timeout");
        }
    }
}
