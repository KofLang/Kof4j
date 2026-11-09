package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1110 — a route handler can read the TCP peer address of the request via
 * `remoteAddr()` (String; `""` when unknown; the maintainer decision 05/11
 * recorded in the issue). The WebRequest already CARRIES the peer address
 * (the rate limiter's key reads it) — nothing exposed it to handlers
 * (SEM015). Behind a proxy the client is the `x-forwarded-for` header (the
 * documented note).
 */
class WebRemoteAddrE2ETest extends KofWebPrograms {

    private static final String APP = """
            main() {
                var app = web.app()
                app.get("/ip") {
                    return "ip=" + remoteAddr()
                }
                app.listen(PORT)
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void remoteAddrReadsThePeerAddressJvm() throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        var method = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        method.setAccessible(true);
        int port = (int) method.invoke(harness, tempDir, APP);
        var req = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        req.setAccessible(true);
        var body = KofWebE2ETest.class.getDeclaredMethod("bodyOf", String.class);
        body.setAccessible(true);
        String raw = (String) req.invoke(harness, port,
                "GET /ip HTTP/1.1\r\nHost: x\r\n\r\n");
        String bodyText = (String) body.invoke(harness, raw);
        assertTrue(bodyText.startsWith("ip="), "the peer address face: " + bodyText);
        assertFalse(bodyText.equals("ip="), "the address is recorded, not empty: " + bodyText);
        assertTrue(bodyText.contains("127.0.0.1"),
                "the loopback request reads the loopback peer: " + bodyText);
    }
}
