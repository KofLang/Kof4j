package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1114 — `app.listen(host, port)` binds ONE interface (the maintainer
 * decision 05/11 recorded in the issue): `app.listen("127.0.0.1", port)`
 * serves a local-only service; `app.listen(port)` KEEPS the wildcard binding
 * (the existing face untouched — the freeze rule).
 */
class WebListenHostE2ETest extends KofWebPrograms {

    @TempDir
    Path tempDir;

    private static final String HOST_APP = """
            main() {
                var app = web.app()
                app.get("/x") {
                    return "ok"
                }
                app.listen("127.0.0.1", PORT)
            }
            """;

    private static final String WILDCARD_APP = """
            main() {
                var app = web.app()
                app.get("/x") {
                    return "ok"
                }
                app.listen(PORT)
            }
            """;

    @Test
    void listenHostBindsOneInterfaceJvm() throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        var start = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        start.setAccessible(true);
        int port = (int) start.invoke(harness, tempDir, HOST_APP);
        var req = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        req.setAccessible(true);
        var body = KofWebE2ETest.class.getDeclaredMethod("bodyOf", String.class);
        body.setAccessible(true);
        String raw = (String) req.invoke(harness, port, "GET /x HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");
        String bodyText = (String) body.invoke(harness, raw);
        assertTrue(raw.startsWith("HTTP/1.1 200 OK"), "the host-bound server answers: " + raw);
        assertEquals("ok", bodyText, "the chained face");
    }

    @Test
    void listenPortKeepsTheWildcardJvm() throws Exception {
        // The FREEZE face: app.listen(port) keeps binding the wildcard — the
        // existing behavior byte-identical (the arity-1 overload untouched).
        KofWebE2ETest harness = new KofWebE2ETest();
        var start = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        start.setAccessible(true);
        int port = (int) start.invoke(harness, tempDir, WILDCARD_APP);
        var req = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        req.setAccessible(true);
        var body = KofWebE2ETest.class.getDeclaredMethod("bodyOf", String.class);
        body.setAccessible(true);
        String raw = (String) req.invoke(harness, port, "GET /x HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(raw.startsWith("HTTP/1.1 200 OK"), raw);
        assertEquals("ok", (String) body.invoke(harness, raw), "the wildcard face");
    }
}
