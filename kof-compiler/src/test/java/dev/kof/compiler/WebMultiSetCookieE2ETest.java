package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1012 — a SECOND `headerSet("Set-Cookie", ...)` overwrote the first (JVM:
 * `kof_web_header_set` did `Map.put` on KOF_WEB_HEADERS — last wins; JS: the
 * §265 pump applied the queue with `Headers.set`) and a CR/LF in a header
 * value SPLIT the response (the JVM build wrote the value verbatim, so
 * `headerSet("X", "v\r\nInjected: 1")` wrote `Injected: 1` as a real header
 * line). RFC 6265 §3: Set-Cookie REPEATS; RFC 9112 §5: no CR/LF in field
 * values. A login that sets a session cookie and a CSRF cookie must send
 * BOTH.
 */
class WebMultiSetCookieE2ETest extends KofWebPrograms {

    private static final String COOKIE_APP = """
            main() {
                var app = web.app()
                app.get("/login") {
                    headerSet("Set-Cookie", "a=1")
                    headerSet("Set-Cookie", "b=2")
                    headerSet("X-Tag", "v1")
                    headerSet("X-Tag", "v2")
                    return "ok"
                }
                app.listen(PORT)
            }
            """;

    private static final String INJECT_APP = """
            main() {
                var app = web.app()
                app.get("/evil") {
                    headerSet("X-Ok", "v\\r\\nInjected: 1")
                    return "boom"
                }
                app.listen(PORT)
            }
            """;

    @TempDir
    Path tempDir;

    private int startJvmServer(Path dir, String app) throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        Method m = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        m.setAccessible(true);
        return (int) m.invoke(harness, dir, app);
    }

    private String jvmRequest(int port, String raw) throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        Method m = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(harness, port, raw);
    }

    @Test
    void twoSetCookiesReachTheJvmWireAndOthersStayLastWins() throws Exception {
        int port = startJvmServer(tempDir, COOKIE_APP);
        String raw = jvmRequest(port, "GET /login HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(raw.contains("200"), "the route answers ok: " + raw);
        int first = raw.indexOf("Set-Cookie: a=1");
        int second = raw.indexOf("Set-Cookie: b=2");
        assertTrue(first >= 0, "the FIRST cookie must reach the wire: " + raw);
        assertTrue(second >= 0, "the SECOND cookie must reach the wire: " + raw);
        assertTrue(first < second, "the cookies keep call order (RFC 6265 §3): " + raw);
        assertTrue(raw.contains("X-Tag: v2"), "a regular header keeps last-wins: " + raw);
        assertFalse(raw.contains("X-Tag: v1"), "a regular header is NOT repeated: " + raw);
    }

    @Test
    void crlfInAHeaderValueAnswers500AndNeverSplitsJvm() throws Exception {
        int port = startJvmServer(tempDir, INJECT_APP);
        String raw = jvmRequest(port, "GET /evil HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(raw.contains("500"), "the refusal surfaces as the 500 catch: " + raw);
        int bodyStart = raw.indexOf("\r\n\r\n");
        String headers = bodyStart >= 0 ? raw.substring(0, bodyStart) : raw;
        assertFalse(headers.contains("Injected:"),
                "no injected header line in the response HEADERS: " + headers);
    }

    // ── JS faces (the §265 pump) ──────────────────────────────────────

    private int jsPort() throws Exception {
        KofWebJsE2ETest harness = new KofWebJsE2ETest();
        Method m = KofWebJsE2ETest.class.getDeclaredMethod("freePort");
        m.setAccessible(true);
        return (int) m.invoke(harness);
    }

    private String jsRequest(int port, String raw) throws Exception {
        KofWebJsE2ETest harness = new KofWebJsE2ETest();
        Method m = KofWebJsE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(harness, port, raw);
    }

    /** Runs a compiled .mjs web app on a daemon thread (the KofWebJsE2ETest pattern). */
    private String jsServe(Path tempDir, String app, int port, String raw,
            String name) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, app.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve(name + "-out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "the JS compile must succeed: "
                + result.diagnostics().getDiagnostics());
        Method findJsEntry = KofWebJsE2ETest.class.getDeclaredMethod("findJsEntry", Path.class);
        findJsEntry.setAccessible(true);
        Path entry = (Path) findJsEntry.invoke(null, outDir);
        java.io.ByteArrayOutputStream serverErr = new java.io.ByteArrayOutputStream();
        Thread serverThread = new Thread(() -> {
            try {
                dev.kof.runtime.KofJsRunner.run(entry,
                        java.io.OutputStream.nullOutputStream(),
                        java.io.InputStream.nullInputStream(),
                        serverErr);
            } catch (Exception ignored) {
            }
        }, "kof-web-js-" + name);
        serverThread.setDaemon(true);
        serverThread.start();
        assertTrue(TestServerFixture.awaitPort(port, 50, 100),
                "the JS server did not open port " + port + " | stderr: "
                        + serverErr.toString(StandardCharsets.UTF_8));
        return jsRequest(port, raw);
    }

    @Test
    void twoSetCookiesReachTheJsWireAndOthersStayLastWins(@TempDir Path jsDir) throws Exception {
        int port = jsPort();
        String raw = jsServe(jsDir, COOKIE_APP, port,
                "GET /login HTTP/1.0\r\nHost: x\r\n\r\n", "CookieApp");
        assertTrue(raw.contains("200"), "the route answers ok: " + raw);
        // O JDK HttpServer NORMALIZA os nomes ("Set-Cookie" -> "Set-cookie")
        // — as asserções JS são case-insensitive.
        String low = raw.toLowerCase(java.util.Locale.ROOT);
        int first = low.indexOf("set-cookie: a=1");
        int second = low.indexOf("set-cookie: b=2");
        assertTrue(first >= 0, "the FIRST cookie must reach the wire: " + raw);
        assertTrue(second >= 0, "the SECOND cookie must reach the wire: " + raw);
        assertTrue(first < second, "the cookies keep call order (RFC 6265 §3): " + raw);
        assertTrue(low.contains("x-tag: v2"), "a regular header keeps last-wins: " + raw);
        assertFalse(low.contains("x-tag: v1"), "a regular header is NOT repeated: " + raw);
    }

    @Test
    void crlfInAHeaderValueNeverSplitsJs(@TempDir Path jsDir) throws Exception {
        int port = jsPort();
        String raw = jsServe(jsDir, INJECT_APP, port,
                "GET /evil HTTP/1.0\r\nHost: x\r\n\r\n", "InjectApp");
        assertTrue(raw.contains("500"), "the JS refusal surfaces as the 500 catch: " + raw);
        int bodyStart = raw.indexOf("\r\n\r\n");
        String headers = bodyStart >= 0 ? raw.substring(0, bodyStart) : raw;
        assertFalse(headers.contains("Injected:"),
                "no injected header line in the response HEADERS: " + headers);
    }
}
