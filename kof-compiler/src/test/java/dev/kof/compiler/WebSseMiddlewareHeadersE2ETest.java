package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1066 — an SSE response DROPPED every header middleware and app.security()
 * set: the dispatcher clears the deferred headers before returning the
 * non-HTTP route, and the server wrote a FIXED stream head
 * (Content-Type/Cache-Control/Connection/X-Accel-Buffering only). An SSE
 * stream behind the same middleware chain as the REST routes must keep the
 * request id, CORS, cookies — next to the fixed stream head, which keeps its
 * values (a middleware Content-Type never replaces text/event-stream).
 */
class WebSseMiddlewareHeadersE2ETest extends KofWebPrograms {

    private static final String MIDDLEWARE_APP = """
            main() {
                var app = web.app()
                app.use {
                    headerSet("X-Request-Id", "r-1")
                    headerSet("Content-Type", "text/plain")
                    headerSet("Set-Cookie", "sid=7")
                    if (header("x-block") != null) { return "blocked" }
                    return null
                }
                app.sse("/events") {
                    sse.send("one")
                    sse.close()
                }
                app.listen(PORT)
            }
            """;

    private static final String SECURED_APP = """
            main() {
                var app = web.app()
                app.security()
                app.sse("/events") {
                    sse.send("one")
                    sse.close()
                }
                app.listen(PORT)
            }
            """;

    @TempDir
    Path tempDir;

    /** Minimal SSE head reader: status + header lines, then close. */
    private static final class HeadClient implements AutoCloseable {
        final String status;
        final List<String> headers;
        private final Socket socket;

        HeadClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /events HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Accept: text/event-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            status = in.readLine();
            headers = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                headers.add(line);
            }
            socket.close();
        }

        String header(String name) {
            for (String line : headers) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(name)) {
                    return line.substring(colon + 1).trim();
                }
            }
            return null;
        }

        int count(String name) {
            int n = 0;
            for (String line : headers) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(name)) n++;
            }
            return n;
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private int startServer(String app) throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        var m = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        m.setAccessible(true);
        return (int) m.invoke(harness, tempDir, app);
    }

    @Test
    void sseKeepsTheMiddlewareHeadersAndTheFixedStreamHead() throws Exception {
        int port = startServer(MIDDLEWARE_APP);
        HeadClient c = new HeadClient(port);
        assertTrue(c.status.contains("200"), "the stream opens: " + c.status);
        assertEquals("r-1", c.header("X-Request-Id"),
                "the middleware X-Request-Id must reach the SSE head: " + c.headers);
        assertEquals("text/event-stream", c.header("Content-Type"),
                "the FIXED stream Content-Type wins (a middleware Content-Type never replaces it): "
                        + c.headers);
        assertEquals(1, c.count("Set-Cookie"),
                "the middleware Set-Cookie reaches the head (RFC 6265 §3, the #1012 integration): "
                        + c.headers);
        assertTrue(c.count("Cache-Control") >= 1, "the fixed Cache-Control stays: " + c.headers);
    }

    @Test
    void sseKeepsTheSecurityHeaders() throws Exception {
        int port = startServer(SECURED_APP);
        HeadClient c = new HeadClient(port);
        assertTrue(c.status.contains("200"), "the stream opens: " + c.status);
        assertNotNull(c.header("Content-Security-Policy"),
                "app.security() hardening headers reach the SSE head: " + c.headers);
        assertEquals("text/event-stream", c.header("Content-Type"),
                "the fixed stream Content-Type is untouched: " + c.headers);
    }
}
