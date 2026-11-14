package dev.kof.browser;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase-8 (testing-platform plan): WEBSOCKET — a REAL browser page connects via
 * WebSocket to a REAL Kof ws server (the RFC 6455 face the generated engine
 * ships — the handshake + the frame codec + the masking), sends a message and
 * writes the server's echo into the DOM; the settle assert measures it. THE
 * FULL CHAIN: browser WS client -> Kof WS server -> the echo back -> the DOM.
 */
class PlaywrightWsE2ETest {

    private static void assumeWsChromium() {
        String v = new PlaywrightLibProvider().probe("chromium");
        assumeTrue(v != null, "sem o chromium pinado pelo driver do Playwright");
    }

    @Test
    void theBrowserEchoesThroughTheKofWsServer(@TempDir Path dir) throws Exception {
        assumeWsChromium();
        // the Kof ws server (the interpreter in a daemon thread — the
        // ScriptSseWsE2ETest precedent), an ephemeral port
        var listener = new java.net.ServerSocket(0, 64,
                java.net.InetAddress.getByName("127.0.0.1"));
        int port = listener.getLocalPort();
        listener.close();
        var src = dir.resolve("wsserve.kf");
        Files.writeString(src, """
                main() {
                    var app = web.app()
                    app.ws("/chat") {
                        wsSend("echo:" + wsMessage())
                    }
                    app.listen(%d)
                }
                """.formatted(port));
        Thread server = new Thread(() -> {
            try {
                new dev.kof.compiler.CompilerDriver().interpret(
                        List.of(src), dir, new String[0]);
            } catch (Exception ignored) {
            }
        }, "kof-ws-daemon");
        server.setDaemon(true);
        server.start();
        // NO port wait here: the driver page JS retries the connection (the
        // browser is the waiter) — the test never parks (the ratchet).

        // the driver page: connects, sends, writes the echo into #out
        Path page = dir.resolve("ws.html");
        Files.writeString(page, """
                <html><head><meta charset="utf-8"></head><body>
                <div id="out">pending</div>
                <script>
                  let tries = 0;
                  function dial() {
                    const sock = new WebSocket('ws://127.0.0.1:%d/chat');
                    sock.onmessage = e => {
                      document.getElementById('out').textContent = e.data;
                    };
                    sock.onopen = () => sock.send('kof-ws');
                    sock.onclose = () => { if (++tries < 60) setTimeout(dial, 150); };
                  }
                  dial();
                </script>
                </body></html>
                """.formatted(port));
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] html = Files.readAllBytes(page);
        http.createContext("/", ex -> {
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, html.length);
            ex.getResponseBody().write(html);
            ex.close();
        });
        http.start();
        String url = "http://127.0.0.1:" + http.getAddress().getPort() + "/ws.html";

        PlaywrightLibProvider p = new PlaywrightLibProvider();
        // the async arrival: WAIT for the text to leave "pending" (the WebSocket
        // echo takes seconds — the interpreter boots), then measure
        assertEquals("echo:kof-ws",
                p.assertTextBecomes("chromium", url, "css=#out", "pending", 15000).strip());
        http.stop(0);
    }
}
