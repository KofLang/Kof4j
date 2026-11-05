package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JVM dispatcher cleared the response headers middleware had set
 * ({@code X-Request-Id}, CORS, cookies) before it handed an SSE route to the
 * server, which wrote only its fixed stream head. Those headers now go out
 * with the SSE head; the four fixed stream headers keep their values.
 */
class SseMiddlewareHeadersE2ETest extends ServerProcessSupport {

    @Test
    void middlewareHeadersReachTheStreamHead(@TempDir Path tmp) throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        Path source = tmp.resolve("App.kf");
        Files.writeString(source, """
            main() {
                var app = web.app()
                app.use {
                    headerSet("X-Request-Id", "r-1")
                    headerSet("Content-Type", "text/plain")
                    if (header("x-block") != null) {
                        return "blocked"
                    }
                    return null
                }
                app.sse("/events") {
                    sse.send("one")
                    sse.close()
                }
                app.listen(PORT)
            }
            """.replace("PORT", String.valueOf(port)));
        Path out = tmp.resolve("classes");
        CompilationResult r = new CompilerDriver().compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        serverProcess = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        TestServerFixture.awaitListening(serverProcess, port);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream o = socket.getOutputStream();
            o.write("GET /events HTTP/1.1\r\nHost: x\r\nAccept: text/event-stream\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            o.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            assertEquals("HTTP/1.1 200 OK", in.readLine());
            List<String> head = new ArrayList<>();
            for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) head.add(line);
            assertTrue(head.contains("X-Request-Id: r-1"), head.toString());
            assertTrue(head.contains("Content-Type: text/event-stream"), head.toString());
            assertTrue(head.stream().noneMatch(h -> h.equals("Content-Type: text/plain")), head.toString());
            assertEquals("data: one", in.readLine());
        }
    }
}
