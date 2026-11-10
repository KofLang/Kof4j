package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1112 — a bare `web.app()` sends NO hardening headers: the hardening
 * (CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy) is
 * OPT-IN through `app.security()` (stdlib-web §3; the maintainer decision
 * 05/11). The old runtime emitted them ALWAYS, overriding the app's own
 * headers. The parity guard: an app WITH `app.security()` still sends the
 * four headers by default (and the app can override them).
 */
class WebHardeningOptInE2ETest extends KofWebPrograms {

    @TempDir
    Path tempDir;

    @Test
    void bareAppSendsNoHardeningHeadersJvm() throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        var start = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        start.setAccessible(true);
        int port = (int) start.invoke(harness, tempDir, """
                main() {
                    var app = web.app()
                    app.get("/x") {
                        return "ok"
                    }
                    app.listen(PORT)
                }
                """);
        var req = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        req.setAccessible(true);
        String raw = (String) req.invoke(harness, port, "GET /x HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(raw.startsWith("HTTP/1.1 200 OK"), raw);
        assertNull(KofWebE2ETest.headerLine(raw, "Content-Security-Policy"),
                "no CSP on a bare app: " + raw);
        assertNull(KofWebE2ETest.headerLine(raw, "X-Content-Type-Options"), raw);
        assertNull(KofWebE2ETest.headerLine(raw, "X-Frame-Options"), raw);
        assertNull(KofWebE2ETest.headerLine(raw, "Referrer-Policy"), raw);
    }

    @Test
    void appSecurityStillEnablesTheHeadersJvm() throws Exception {
        // The parity guard: the opt-in still sends the four headers by
        // default (and the app can override them per-route).
        KofWebE2ETest harness = new KofWebE2ETest();
        var start = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        start.setAccessible(true);
        int port = (int) start.invoke(harness, tempDir, """
                main() {
                    var app = web.app()
                    app.security()
                    app.get("/x") {
                        return "ok"
                    }
                    app.listen(PORT)
                }
                """);
        var req = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        req.setAccessible(true);
        String raw = (String) req.invoke(harness, port, "GET /x HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(raw.startsWith("HTTP/1.1 200 OK"), raw);
        assertNotNull(KofWebE2ETest.headerLine(raw, "Content-Security-Policy"), raw);
        assertEquals("nosniff", KofWebE2ETest.headerLine(raw, "X-Content-Type-Options"), raw);
        assertEquals("DENY", KofWebE2ETest.headerLine(raw, "X-Frame-Options"), raw);
        assertEquals("no-referrer", KofWebE2ETest.headerLine(raw, "Referrer-Policy"), raw);
    }
}
