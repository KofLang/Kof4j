package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR6 hardening E2E: connection cap, mutable limits, counters and SSE timeout.
 */
class KofWebHardeningTest {

    private static final String JAVA_BIN = Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private static final String VALID_HEADERS = "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Version: 13\r\n"
            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n";

    private static final byte[] MASK = {0x12, 0x34, 0x56, 0x78};

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

    private static Path testClassesDir() throws Exception {
        return Path.of(KofWebHardeningTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toRealPath();
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
        int attempt = 0;
        while (attempt < 40) {
            if (!serverProcess.isAlive()) {
                String out = new String(serverProcess.getInputStream().readAllBytes(),
                                StandardCharsets.UTF_8)
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
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = socket.getInputStream().read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            return response.toString();
        }
    }

    private static String bodyOf(String rawResponse) {
        int idx = rawResponse.indexOf("\r\n\r\n");
        return idx >= 0 ? rawResponse.substring(idx + 4) : rawResponse;
    }

    private String stats(int port) throws IOException {
        String response = request(port, "GET /stats HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 200 OK"), response);
        return bodyOf(response);
    }

    private void statsEventually(int port, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 2000;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            last = stats(port);
            if (expected.equals(last)) {
                return;
            }
            Thread.sleep(20);
        }
        assertEquals(expected, last);
    }

    private static final class SseClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader in;
        final String status;
        final List<String> headers;

        SseClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(2000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /events HTTP/1.1\r\n"
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

    private static final class WsResponse implements AutoCloseable {
        private final Socket socket;
        final String status;
        final List<String> headers;

        WsResponse(Socket socket, String status, List<String> headers) {
            this.socket = socket;
            this.status = status;
            this.headers = headers;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private WsResponse handshake(int port) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        OutputStream out = socket.getOutputStream();
        out.write(("GET /ws HTTP/1.1\r\nHost: x\r\n"
                + VALID_HEADERS
                + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
        String status = in.readLine();
        List<String> headers = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            headers.add(line);
        }
        return new WsResponse(socket, status, headers);
    }

    private static void writeMaskedFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        int len = payload.length;
        byte[] frame;
        int headerLen;
        if (len <= 125) {
            frame = new byte[2 + 4 + len];
            frame[1] = (byte) (0x80 | len);
            headerLen = 2;
        } else if (len <= 0xFFFF) {
            frame = new byte[4 + 4 + len];
            frame[1] = (byte) (0x80 | 126);
            frame[2] = (byte) ((len >> 8) & 0xFF);
            frame[3] = (byte) (len & 0xFF);
            headerLen = 4;
        } else {
            frame = new byte[10 + 4 + len];
            frame[1] = (byte) (0x80 | 127);
            for (int i = 0; i < 8; i++) {
                frame[2 + i] = (byte) ((len >> (56 - i * 8)) & 0xFF);
            }
            headerLen = 10;
        }
        frame[0] = (byte) (0x80 | opcode);
        System.arraycopy(MASK, 0, frame, headerLen, 4);
        for (int i = 0; i < len; i++) {
            frame[headerLen + 4 + i] = (byte) (payload[i] ^ MASK[i % 4]);
        }
        out.write(frame);
        out.flush();
    }

    private static void writeOversizedFrameHeader(OutputStream out, long len) throws IOException {
        byte[] frame = new byte[14];
        frame[0] = (byte) 0x82;
        frame[1] = (byte) (0x80 | 127);
        for (int i = 0; i < 8; i++) {
            frame[2 + i] = (byte) ((len >> (56 - i * 8)) & 0xFF);
        }
        System.arraycopy(MASK, 0, frame, 10, 4);
        out.write(frame);
        out.flush();
    }

    private static byte[] readServerFrame(java.io.InputStream in) throws IOException {
        byte[] header = new byte[10];
        readFully(in, header, 0, 2);
        long len = header[1] & 0x7F;
        int headerLen = 2;
        if (len == 126) {
            readFully(in, header, 2, 2);
            len = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
            headerLen = 4;
        } else if (len == 127) {
            readFully(in, header, 2, 8);
            len = 0;
            for (int i = 0; i < 8; i++) {
                len = (len << 8) | (header[2 + i] & 0xFF);
            }
            headerLen = 10;
        }
        byte[] payload = new byte[(int) len];
        readFully(in, payload, 0, payload.length);
        byte[] frame = new byte[headerLen + payload.length];
        System.arraycopy(header, 0, frame, 0, headerLen);
        System.arraycopy(payload, 0, frame, headerLen, payload.length);
        return frame;
    }

    private static byte[] framePayload(byte[] frame) {
        long len = frame[1] & 0x7F;
        int offset;
        if (len == 126) {
            len = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
            offset = 4;
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) {
                len = (len << 8) | (frame[2 + i] & 0xFF);
            }
            offset = 10;
        } else {
            offset = 2;
        }
        return Arrays.copyOfRange(frame, offset, offset + (int) len);
    }

    private static int closeCode(byte[] payload) {
        return ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
    }

    private static void readFully(java.io.InputStream in, byte[] buf, int off, int len) throws IOException {
        while (len > 0) {
            int n = in.read(buf, off, len);
            if (n < 0) throw new IOException("EOF reading frame");
            off += n;
            len -= n;
        }
    }

    @Test
    void connection_cap_returns_503_when_exceeded(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.configure("maxConnections", 1)
                    app.get("/hello") { return "ok" }
                    app.listen(PORT)
                }
                """);
        Thread.sleep(100);
        try (Socket held = new Socket("127.0.0.1", port)) {
            held.setSoTimeout(2000);
            held.getOutputStream().write("GET /hello HTTP/1.1\r\nHost: x\r\n".getBytes(StandardCharsets.UTF_8));
            held.getOutputStream().flush();
            Thread.sleep(100);
            String second = request(port, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
            assertTrue(second.startsWith("HTTP/1.1 503 Service Unavailable"), second);
        }
    }

    @Test
    void sse_connection_counter_increments_and_decrements(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.sse("/events") {
                        sse.send("connected")
                        time.sleep(1500)
                    }
                    app.get("/stats") {
                        return stats("SSE_CONNECTIONS_ACTIVE")
                    }
                    app.listen(PORT)
                }
                """);
        try (SseClient client = new SseClient(port)) {
            assertEquals("HTTP/1.1 200 OK", client.status);
            assertEquals("data: connected", client.readEvent());
            statsEventually(port, "1");
        }
        Thread.sleep(1600);
        statsEventually(port, "0");
    }

    @Test
    void ws_connection_counter_increments_and_decrements(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.ws("/ws") { return "x" }
                    app.get("/stats") {
                        return stats("WS_CONNECTIONS_ACTIVE")
                    }
                    app.listen(PORT)
                }
                """);
        try (WsResponse ws = handshake(port)) {
            assertEquals("HTTP/1.1 101 Switching Protocols", ws.status);
            statsEventually(port, "1");
        }
        Thread.sleep(100);
        statsEventually(port, "0");
    }

    @Test
    void sse_events_sent_counter_tracks_calls(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.sse("/events") {
                        sse.send("one")
                        sse.send("two")
                        sse.send("three")
                    }
                    app.get("/stats") {
                        return stats("SSE_EVENTS_SENT")
                    }
                    app.listen(PORT)
                }
                """);
        try (SseClient client = new SseClient(port)) {
            assertEquals("data: one", client.readEvent());
            assertEquals("data: two", client.readEvent());
            assertEquals("data: three", client.readEvent());
        }
        assertEquals("3", stats(port));
    }

    @Test
    void ws_messages_counters_track_calls(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.ws("/ws") {
                        var m = wsMessage()
                        wsSend("echo: " + m)
                    }
                    app.get("/stats") {
                        return stats("WS_MESSAGES_RECEIVED")
                                + ":" + stats("WS_MESSAGES_SENT")
                    }
                    app.listen(PORT)
                }
                """);
        try (WsResponse ws = handshake(port)) {
            assertEquals("HTTP/1.1 101 Switching Protocols", ws.status);
            writeMaskedFrame(ws.socket.getOutputStream(), 0x1,
                    "hello".getBytes(StandardCharsets.UTF_8));
            assertArrayEquals("echo: hello".getBytes(StandardCharsets.UTF_8),
                    framePayload(readServerFrame(ws.socket.getInputStream())));
            writeMaskedFrame(ws.socket.getOutputStream(), 0x1,
                    "world".getBytes(StandardCharsets.UTF_8));
            assertArrayEquals("echo: world".getBytes(StandardCharsets.UTF_8),
                    framePayload(readServerFrame(ws.socket.getInputStream())));
        }
        assertEquals("2:2", stats(port));
    }

    @Test
    void oversized_frame_closes_with_1009(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.configure("maxFrameBytes", 1024)
                    app.ws("/ws") { return "x" }
                    app.listen(PORT)
                }
                """);
        try (WsResponse ws = handshake(port)) {
            assertEquals("HTTP/1.1 101 Switching Protocols", ws.status);
            writeOversizedFrameHeader(ws.socket.getOutputStream(), 2048);
            byte[] frame = readServerFrame(ws.socket.getInputStream());
            assertEquals(0x88, frame[0] & 0xFF);
            assertEquals(0, frame[1] & 0x80);
            assertEquals(1009, closeCode(framePayload(frame)));
        }
    }
}
