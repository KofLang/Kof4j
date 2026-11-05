package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G27 — the hardening headers (CSP, nosniff, X-Frame-Options,
 * Referrer-Policy) are what {@code app.security()} enables (stdlib-web §3),
 * but the runtime sent them on every response of an app that never called
 * it: the bare app's global policy defaulted to {@code headers = true}. They
 * are now opt-in, as documented (maintainer decision 05/11); an app that
 * calls {@code app.security()} keeps them by default.
 */
class WebSecurityHeadersOptInE2ETest {

    @TempDir Path tmp;

    private String get(String app) throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) { port = s.getLocalPort(); }
        Path src = tmp.resolve("App" + port + ".kf");
        Files.writeString(src, app.replace("PORT", String.valueOf(port)));
        Path out = tmp.resolve("out" + port);
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        Process server = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                out.toString(), "Default.Main").redirectErrorStream(true).start();
        try {
            assertTrue(TestServerFixture.awaitPort(port, 100, 100), "server did not open " + port);
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(10000);
                s.getOutputStream().write("GET /x HTTP/1.0\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void aBareAppSendsNoHardeningHeaders() throws Exception {
        String raw = get("""
                main() {
                    var app = web.app()
                    app.get("/x") { return "ok" }
                    app.listen(PORT)
                }
                """);
        assertTrue(raw.contains("200"), raw);
        for (String h : new String[]{"Content-Security-Policy", "X-Content-Type-Options", "X-Frame-Options",
                "Referrer-Policy"}) {
            assertFalse(raw.toLowerCase().contains(h.toLowerCase() + ":"), h + " without app.security(): " + raw);
        }
    }

    @Test
    void appSecurityStillEnablesThemByDefault() throws Exception {
        String raw = get("""
                main() {
                    var app = web.app()
                    app.security()
                    app.get("/x") { return "ok" }
                    app.listen(PORT)
                }
                """);
        assertTrue(raw.contains("X-Frame-Options: DENY"), raw);
        assertTrue(raw.contains("X-Content-Type-Options: nosniff"), raw);
    }

    @Test
    void aPolicyScopeEnablesThemOnlyWhenItSaysSo() throws Exception {
        String on = get("""
                main() {
                    var app = web.app()
                    app.policy("/x", mapOf("headers", true))
                    app.get("/x") { return "ok" }
                    app.listen(PORT)
                }
                """);
        assertTrue(on.contains("X-Frame-Options: DENY"), on);
        String off = get("""
                main() {
                    var app = web.app()
                    app.policy("/x", mapOf("rateLimit", "100/60"))
                    app.get("/x") { return "ok" }
                    app.listen(PORT)
                }
                """);
        assertFalse(off.contains("X-Frame-Options"), off);
    }
}
