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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E tests for the Kof-native SSE stack ({@code app.sse(...)}).
 */
class KofWebSseE2ETest extends ServerProcessSupport {

    private static final String JAVA_BIN = Path.of(
            System.getProperty("java.home"), "bin", "java").toString();



    private static final String SSE_APP = """
            main() {
                var app = web.app()
                app.sse("/events") {
                    HANDLER
                }
                app.listen(PORT)
            }
            """;

    private static Path testClassesDir() throws Exception {
        return Path.of(KofWebSseE2ETest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toRealPath();
    }

    private int startSseServer(Path tempDir, String handlerBody) throws Exception {
        return startServer(tempDir, SSE_APP.replace("HANDLER", handlerBody));
    }

    private int startServer(Path tempDir, String kofSource) throws Exception {
        int port = freePort();
        Path sourceFile = tempDir.resolve("App.kf");
        Files.writeString(sourceFile, kofSource.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilerDriver driver = new CompilerDriver();
        driver.setExternalClasspath(List.of(testClassesDir()));
        CompilationResult result = driver.compile(sourceFile, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: "
                + result.diagnostics().getDiagnostics());
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
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = socket.getInputStream().read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            return response.toString();
        }
    }

    private static final class SseClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader in;
        final String status;
        final List<String> headers;

        SseClient(int port) throws IOException {
            this(port, "");
        }

        SseClient(int port, String query) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(2000);
            OutputStream out = socket.getOutputStream();
            String target = query.isEmpty() ? "/events" : "/events?" + query;
            out.write(("GET " + target + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Accept: text/event-stream\r\n"
                    + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            status = in.readLine();
            headers = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                headers.add(line);
            }
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

        String readEvent() throws IOException {
            StringBuilder frame = new StringBuilder();
            while (true) {
                String line = in.readLine();
                if (line == null) {
                    throw new IOException("SSE stream closed before event terminator");
                }
                if (line.isEmpty()) return frame.toString();
                if (frame.length() > 0) frame.append('\n');
                frame.append(line);
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private SseClient connect(int port) throws IOException {
        return new SseClient(port);
    }

    private void assertHeaders(SseClient client) {
        assertEquals("HTTP/1.1 200 OK", client.status);
        assertEquals("text/event-stream", client.header("Content-Type"));
        assertEquals("no-cache", client.header("Cache-Control"));
        assertEquals("keep-alive", client.header("Connection"));
        assertEquals("no", client.header("X-Accel-Buffering"));
    }

    @Test
    void single_data_event(@TempDir Path tempDir) throws Exception {
        int port = startSseServer(tempDir, "sse.send(\"hello\")");
        try (SseClient client = connect(port)) {
            assertHeaders(client);
            assertEquals("data: hello", client.readEvent());
        }
    }

    @Test
    void named_event(@TempDir Path tempDir) throws Exception {
        int port = startSseServer(tempDir, "sse.event(\"tick\", \"hello\")");
        try (SseClient client = connect(port)) {
            assertHeaders(client);
            assertEquals("event: tick\ndata: hello", client.readEvent());
        }
    }

    @Test
    void multi_event_keeps_alive(@TempDir Path tempDir) throws Exception {
        int port = startSseServer(tempDir, """
                sse.send("one")
                sse.send("two")
                sse.send("three")
                """);
        try (SseClient client = connect(port)) {
            assertHeaders(client);
            assertEquals("data: one", client.readEvent());
            assertEquals("data: two", client.readEvent());
            assertEquals("data: three", client.readEvent());
        }
    }

    @Test
    void multi_line_data_splits(@TempDir Path tempDir) throws Exception {
        int port = startSseServer(tempDir, "sse.send(\"line1\\nline2\")");
        try (SseClient client = connect(port)) {
            assertHeaders(client);
            assertEquals("data: line1\ndata: line2", client.readEvent());
        }
    }

    @Test
    void client_close_no_crash(@TempDir Path tempDir) throws Exception {
        int port = startSseServer(tempDir, "sse.send(\"hello\")");
        try (SseClient client = connect(port)) {
            assertEquals("data: hello", client.readEvent());
        }
        try (SseClient client = connect(port)) {
            assertHeaders(client);
            assertEquals("data: hello", client.readEvent());
        }
    }

    @Test
    void many_concurrent_clients_independent(@TempDir Path tempDir) throws Exception {
        int port = startSseServer(tempDir, "sse.send(\"client-\" + query(\"client\"))");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final int n = i;
                results.add(pool.submit(() -> {
                    try (SseClient client = new SseClient(port, "client=" + n)) {
                        assertHeaders(client);
                        return client.readEvent();
                    }
                }));
            }
            for (int i = 0; i < results.size(); i++) {
                assertEquals("data: client-" + i,
                        results.get(i).get(10, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void hello_route_still_works(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.get("/hello") { return "Hello SSE" }
                    app.listen(PORT)
                }
                """);
        String r = request(port, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(r.contains("Hello SSE"), r);
    }
}
