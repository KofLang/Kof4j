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
 * End-to-end tests for {@code app.sse} e {@code app.ws} — Server-Sent Events
 * e WebSocket (handshake + frames) sobre sockets reais no target JVM.
 */
class KofWebStreamE2ETest {

    private static final String JAVA_BIN = java.nio.file.Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

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

    private static final String STREAM_APP = """
            main() {
                var app = web.app()
                app.sse("/events/:room") {
                    var r = param("room")
                    sse("joined:" + r)
                    sse("tick")
                }
                app.ws("/chat") {
                    var m = wsMessage()
                    if (m == "bye") {
                        return
                    }
                    wsSend("echo: " + m)
                }
                app.listen(PORT)
            }
            """;

    private int startServer(Path tempDir) throws IOException {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, STREAM_APP.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
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

    @Test
    void sseStreamsEvents(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /events/lobby HTTP/1.1\r\n"
                    + "Host: localhost\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            String response = readAll(socket.getInputStream());
            assertTrue(response.startsWith("HTTP/1.1 200 OK"), "status: " + response);
            assertTrue(response.contains("Content-Type: text/event-stream"), "content-type");
            assertTrue(response.contains("data: joined:lobby\n\ndata: tick\n\n"),
                    "sse events, got: " + response);
        }
    }

    @Test
    void webSocketHandshakeRfcAccept(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /chat HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            String headers = readHeaders(socket.getInputStream());
            assertTrue(headers.startsWith("HTTP/1.1 101"), "status: " + headers);
            assertTrue(headers.contains("Upgrade: websocket"), "upgrade");
            assertTrue(headers.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="),
                    "RFC 6455 accept vector, got: " + headers);
        }
    }

    @Test
    void webSocketEchoFrames(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET /chat HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            readHeaders(in);

            writeMaskedTextFrame(out, "hello");
            assertEquals("echo: hello", readTextFrame(in));

            writeMaskedTextFrame(out, "kof");
            assertEquals("echo: kof", readTextFrame(in));
        }
    }

    @Test
    void webSocketEchoLargePayload(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET /chat HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            readHeaders(in);

            String big = "x".repeat(300);
            writeMaskedTextFrame(out, big);
            assertEquals("echo: " + big, readTextFrame(in));
        }
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder response = new StringBuilder();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
        }
        return response.toString();
    }

    private static String readHeaders(InputStream in) throws IOException {
        StringBuilder headers = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c < 0) throw new IOException("connection closed in headers: " + headers);
            headers.append((char) c);
            int len = headers.length();
            if (len >= 4 && headers.substring(len - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        return headers.toString();
    }

    private static void writeMaskedTextFrame(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] mask = {0x37, (byte) 0xFA, 0x21, (byte) 0x3D};
        out.write(0x81);
        if (payload.length < 126) {
            out.write(0x80 | payload.length);
        } else if (payload.length < 65536) {
            out.write(0x80 | 126);
            out.write((payload.length >>> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        }
        out.write(mask);
        for (int i = 0; i < payload.length; i++) {
            out.write(payload[i] ^ mask[i % 4]);
        }
        out.flush();
    }

    private static String readTextFrame(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        assertTrue(b0 == 0x81, "expected text frame opcode, got 0x" + Integer.toHexString(b0));
        int len = b1 & 0x7F;
        if (len == 126) {
            len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        }
        byte[] data = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(data, off, len - off);
            if (n < 0) break;
            off += n;
        }
        return new String(data, 0, off, StandardCharsets.UTF_8);
    }
}