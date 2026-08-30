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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E tests for the RFC 6455 WebSocket handshake in the Kof-native web stack.
 */
class KofWebWsE2ETest {

    private static final String JAVA_BIN = Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private static final String VALID_HEADERS = "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Version: 13\r\n"
            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n";

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
        return Path.of(KofWebWsE2ETest.class.getProtectionDomain()
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

    private static final class WsResponse implements AutoCloseable {
        private final Socket socket;
        private final String status;
        private final List<String> headers;

        WsResponse(Socket socket, String status, List<String> headers) {
            this.socket = socket;
            this.status = status;
            this.headers = headers;
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

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private WsResponse handshake(int port, String extraHeaders) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        OutputStream out = socket.getOutputStream();
        out.write(("GET /ws HTTP/1.1\r\nHost: x\r\n"
                + extraHeaders
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

    private String wsApp() {
        return """
                main() {
                    var app = web.app()
                    app.ws("/ws") { return "x" }
                    app.listen(PORT)
                }
                """;
    }

    private static final byte[] MASK = {0x12, 0x34, 0x56, 0x78};

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

    private static void writeOversizedFrameHeader(OutputStream out) throws IOException {
        byte[] frame = new byte[14];
        frame[0] = (byte) 0x82;
        frame[1] = (byte) (0x80 | 127);
        long len = (1L << 20) + 1;
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

    private static String readTextFrame(java.io.InputStream in) throws IOException {
        byte[] frame = readServerFrame(in);
        assertEquals(0x81, frame[0] & 0xFF);
        assertEquals(0, frame[1] & 0x80);
        return new String(framePayload(frame), StandardCharsets.UTF_8);
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
    void handshake_101_with_correct_accept(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, wsApp());
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            assertEquals("HTTP/1.1 101 Switching Protocols", response.status);
            assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                    response.header("Sec-WebSocket-Accept"));
        }
    }

    @Test
    void handshake_rejects_missing_key(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, wsApp());
        String response = request(port, "GET /ws HTTP/1.1\r\nHost: x\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n");
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request"), response);
    }

    @Test
    void handshake_rejects_old_version(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, wsApp());
        String response = request(port, "GET /ws HTTP/1.1\r\nHost: x\r\n"
                + VALID_HEADERS.replace("Version: 13", "Version: 8")
                + "\r\n");
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request"), response);
    }

    @Test
    void handshake_rejects_no_upgrade(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, wsApp());
        String response = request(port, "GET /ws HTTP/1.1\r\nHost: x\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                + "\r\n");
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request"), response);
    }

    @Test
    void handshake_rejects_no_connection_upgrade(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, wsApp());
        String response = request(port, "GET /ws HTTP/1.1\r\nHost: x\r\n"
                + "Upgrade: websocket\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                + "\r\n");
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request"), response);
    }

    @Test
    void handshake_keeps_socket_open(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, wsApp());
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            assertEquals("HTTP/1.1 101 Switching Protocols", response.status);
            Thread.sleep(300);
            assertTrue(serverProcess.isAlive());
            assertFalse(response.socket.isClosed());
            assertEquals(0, response.socket.getInputStream().available());
        }
    }

    /**
     * Validates the case the previous substring-based validator broke:
     * {@code Connection: keep-alive, Upgrade} carries the Upgrade token in a
     * comma-separated list, not as the only value. RFC 6455 §4.1 requires it
     * to be accepted.
     */
    @Test
    void handshake_accepts_comma_separated_connection_tokens(@TempDir Path tempDir) throws Exception {
        String headers = "Upgrade: websocket\r\n"
                + "Connection: keep-alive, Upgrade\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n";
        int port = startServer(tempDir, wsApp());
        try (WsResponse response = handshake(port, headers)) {
            assertEquals("HTTP/1.1 101 Switching Protocols", response.status);
            assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", response.header("Sec-WebSocket-Accept"));
        }
    }

    @Test
    void handshake_alongside_http_and_sse(@TempDir Path tempDir) throws Exception {
        String app = """
                main() {
                    var app = web.app()
                    app.get("/hello") { return "Hello WS" }
                    app.sse("/events") { sse.send("hello") }
                    app.ws("/ws") { return "x" }
                    app.listen(PORT)
                }
                """;
        int port = startServer(tempDir, app);
        String http = request(port, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(http.startsWith("HTTP/1.1 200 OK"), http);
        assertTrue(http.contains("Hello WS"), http);
        String sse = request(port, "GET /events HTTP/1.1\r\nHost: x\r\n"
                + "Accept: text/event-stream\r\n\r\n");
        assertTrue(sse.startsWith("HTTP/1.1 200 OK"), sse);
        assertTrue(sse.contains("data: hello"), sse);
        try (WsResponse ws = handshake(port, VALID_HEADERS)) {
            assertEquals("HTTP/1.1 101 Switching Protocols", ws.status);
            assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                    ws.header("Sec-WebSocket-Accept"));
        }
    }

    @Test
    void ws_onMessage_echo_text(@TempDir Path tempDir) throws Exception {
        String app = """
                main() {
                    var app = web.app()
                    app.ws("/ws") {
                        ws.onMessage { msg -> ws.send(msg) }
                    }
                    app.listen(PORT)
                }
                """;
        int port = startServer(tempDir, app);
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            writeMaskedFrame(response.socket.getOutputStream(), 0x1,
                    "hello".getBytes(StandardCharsets.UTF_8));
            byte[] frame = readServerFrame(response.socket.getInputStream());
            assertEquals(0x81, frame[0] & 0xFF);
            assertEquals(0, frame[1] & 0x80);
            assertEquals("hello", new String(framePayload(frame), StandardCharsets.UTF_8));
        }
    }

    @Test
    void ws_onMessage_receives_multiple_frames(@TempDir Path tempDir) throws Exception {
        String app = """
                main() {
                    var app = web.app()
                    app.ws("/ws") {
                        ws.onMessage { msg -> ws.send("echo: " + msg) }
                    }
                    app.listen(PORT)
                }
                """;
        int port = startServer(tempDir, app);
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            java.io.OutputStream out = response.socket.getOutputStream();
            writeMaskedFrame(out, 0x1, "one".getBytes(StandardCharsets.UTF_8));
            assertEquals("echo: one", readTextFrame(response.socket.getInputStream()));
            writeMaskedFrame(out, 0x1, "c".getBytes(StandardCharsets.UTF_8));
            assertEquals("echo: c", readTextFrame(response.socket.getInputStream()));
            writeMaskedFrame(out, 0x1, "def".getBytes(StandardCharsets.UTF_8));
            assertEquals("echo: def", readTextFrame(response.socket.getInputStream()));
        }
    }

    @Test
    void ws_onClose_called_when_client_closes(@TempDir Path tempDir) throws Exception {
        String app = """
                main() {
                    var app = web.app()
                    app.ws("/ws") {
                        ws.onClose { code, reason -> ws.send("closed") }
                    }
                    app.listen(PORT)
                }
                """;
        int port = startServer(tempDir, app);
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            byte[] close = {0x03, (byte) 0xE8};
            writeMaskedFrame(response.socket.getOutputStream(), 0x8, close);
            assertEquals("closed", readTextFrame(response.socket.getInputStream()));
            byte[] frame = readServerFrame(response.socket.getInputStream());
            assertEquals(0x88, frame[0] & 0xFF);
            assertEquals(0, frame[1] & 0x80);
            assertEquals(1000, closeCode(framePayload(frame)));
            assertEquals(-1, response.socket.getInputStream().read());
        }
    }

    @Test
    void ws_no_callback_still_does_handshake_and_ping_pong(@TempDir Path tempDir) throws Exception {
        String app = """
                main() {
                    var app = web.app()
                    app.ws("/ws") {
                    }
                    app.listen(PORT)
                }
                """;
        int port = startServer(tempDir, app);
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            assertEquals("HTTP/1.1 101 Switching Protocols", response.status);
            byte[] payload = "pong-me".getBytes(StandardCharsets.UTF_8);
            writeMaskedFrame(response.socket.getOutputStream(), 0x9, payload);
            byte[] frame = readServerFrame(response.socket.getInputStream());
            assertEquals(0x8A, frame[0] & 0xFF);
            assertEquals(0, frame[1] & 0x80);
            assertArrayEquals(payload, framePayload(frame));
        }
    }

    @Test
    void ws_handler_exception_closes_connection_gracefully(@TempDir Path tempDir) throws Exception {
        String app = """
                main() {
                    var app = web.app()
                    app.ws("/ws") {
                        ws.onMessage { msg -> throw "boom" }
                    }
                    app.listen(PORT)
                }
                """;
        int port = startServer(tempDir, app);
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            writeMaskedFrame(response.socket.getOutputStream(), 0x1,
                    "hello".getBytes(StandardCharsets.UTF_8));
            byte[] frame = readServerFrame(response.socket.getInputStream());
            assertEquals(0x88, frame[0] & 0xFF);
            assertEquals(0, frame[1] & 0x80);
            assertEquals(1011, closeCode(framePayload(frame)));
            assertEquals(-1, response.socket.getInputStream().read());
        }
        assertTrue(serverProcess.isAlive());
    }

    @Test
    void ws_ping_pong_e2e(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, wsApp());
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            byte[] payload = "pong-me".getBytes(StandardCharsets.UTF_8);
            writeMaskedFrame(response.socket.getOutputStream(), 0x9, payload);
            byte[] frame = readServerFrame(response.socket.getInputStream());
            assertEquals(0x8A, frame[0] & 0xFF);
            assertEquals(0, frame[1] & 0x80);
            assertArrayEquals(payload, framePayload(frame));
        }
    }

    @Test
    void ws_close_e2e(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, wsApp());
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            byte[] close = {0x03, (byte) 0xE8};
            writeMaskedFrame(response.socket.getOutputStream(), 0x8, close);
            byte[] frame = readServerFrame(response.socket.getInputStream());
            assertEquals(0x88, frame[0] & 0xFF);
            assertEquals(0, frame[1] & 0x80);
            assertEquals(1000, closeCode(framePayload(frame)));
            assertEquals(-1, response.socket.getInputStream().read());
        }
    }

    @Test
    void ws_rejects_oversized_frame_e2e(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, wsApp());
        try (WsResponse response = handshake(port, VALID_HEADERS)) {
            writeOversizedFrameHeader(response.socket.getOutputStream());
            byte[] frame = readServerFrame(response.socket.getInputStream());
            assertEquals(0x88, frame[0] & 0xFF);
            assertEquals(0, frame[1] & 0x80);
            assertEquals(1009, closeCode(framePayload(frame)));
        }
    }
}
