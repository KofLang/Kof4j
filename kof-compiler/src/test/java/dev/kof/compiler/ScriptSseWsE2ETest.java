package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A2 (§574 residual, next-plan Track A): the Script bridge for SSE handlers.
 * Compiled lambdas expose invoke(SseConnection); InterpretedCallable now has a
 * generic single-parameter arm and the runtime dispatch falls back to scanning
 * it, so an interpreted `app.sse` route streams its events over a real socket.
 * The WS invoke() arm already existed and is pinned here too (raw handshake +
 * text frame from the SAME daemon under --target script).
 */
class ScriptSseWsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path dir;

    private final Thread[] spawned = new Thread[1];

    private static void waitForPort(int port) throws Exception {
        if (!TestServerFixture.awaitPort(port, 100, 200)) {
            throw new AssertionError("daemon never listened on " + port);
        }
    }

    private void runInterpreted(String program) throws Exception {
        Path src = dir.resolve("ssews.kf");
        Files.writeString(src, program);
        spawned[0] = new Thread(() -> {
            try {
                driver.interpret(List.of(src), dir, new String[0]);
            } catch (Exception ignored) {
            }
        }, "script-sse-ws-daemon");
        spawned[0].setDaemon(true);
        spawned[0].start();
    }

    @Test
    void interpretedSseRouteStreamsAndWsHandshakeAnswers() throws Exception {
        int port = 18987;
        runInterpreted("""
                main() {
                    var app = web.app()
                    app.sse("/events") {
                        sse.send("one")
                        sse.send("two")
                    }
                    app.ws("/ws") {
                        wsSend("echo:" + wsMessage())
                    }
                    app.listen(PORT)
                }
                """.replace("PORT", String.valueOf(port)));

        waitForPort(port);
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(15000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            out.write(("GET /events HTTP/1.1\r\nHost: 127.0.0.1\r\nAccept: text/event-stream\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            long deadline = System.currentTimeMillis() + 15000;
            String seen = "";
            while (System.currentTimeMillis() < deadline) {
                int b = in.read();
                if (b < 0) break;
                buf.write(b);
                seen = buf.toString(StandardCharsets.UTF_8);
                if (seen.contains("data: two")) break;
            }
            assertTrue(seen.contains("200"), "SSE status line: " + seen);
            assertTrue(seen.contains("text/event-stream"), "SSE content type: " + seen);
            assertTrue(seen.contains("data: one"), "first event: " + seen);
            assertTrue(seen.contains("data: two"), "second event: " + seen);
        }

        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(15000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            out.write(("GET /ws HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            String h = "";
            while (!h.contains("\r\n\r\n")) {
                int b = in.read();
                if (b < 0) break;
                head.write(b);
                h = head.toString(StandardCharsets.US_ASCII);
            }
            assertTrue(h.contains("101"), "WS handshake under script: " + h);
            // send a masked TEXT frame "hi" and read the echoed frame
            byte[] mask = {1, 2, 3, 4};
            byte[] payload = {'h', 'i'};
            byte[] frame = new byte[2 + 4 + 2];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) (0x80 | payload.length);
            System.arraycopy(mask, 0, frame, 2, 4);
            for (int i = 0; i < payload.length; i++) {
                frame[6 + i] = (byte) (payload[i] ^ mask[i % 4]);
            }
            out.write(frame);
            out.flush();
            int b0 = in.read();
            int b1 = in.read();
            assertTrue(b0 == 0x81 && b1 >= 0, "echo frame header: " + b0 + "," + b1);
            byte[] got = new byte[b1 & 0x7F];
            in.read(got, 0, got.length);
            assertEquals("echo:hi", new String(got, StandardCharsets.UTF_8));
        } finally {
            if (spawned[0] != null) spawned[0].interrupt();
        }
    }
}
