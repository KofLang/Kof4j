package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1011 — the web runtime answered 401 to ANY request carrying
 * `Authorization` even when the app NEVER configured `app.security()` /
 * `app.policy()`: the pipeline's step 6 auth-if-present face fired on the
 * DEFAULT policy of every app (`authHeader == null && Authorization
 * present` → JWT check → 401). D-SEC.4 scopes that rule to an app where
 * `app.security()` is configured — a gateway forwarding `Authorization`, or
 * an app doing its own auth in a middleware/route, must reach the route.
 */
class WebAuthIfPresentScopeE2ETest extends KofWebPrograms {

    private static final String BARE_APP = """
            main() {
                var app = web.app()
                app.get("/health") {
                    return "up"
                }
                app.listen(PORT)
            }
            """;

    private static final String SECURED_APP = """
            main() {
                var app = web.app()
                app.security()
                app.get("/health") {
                    return "up"
                }
                app.listen(PORT)
            }
            """;

    private static final String SCOPED_APP = """
            main() {
                var app = web.app()
                app.policy("/api", mapOf("headers", true))
                app.get("/api/health") {
                    return "up"
                }
                app.get("/health") {
                    return "up"
                }
                app.listen(PORT)
            }
            """;

    @TempDir
    Path tempDir;

    private String wire(String app, String raw) throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        var start = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        start.setAccessible(true);
        int port = (int) start.invoke(harness, tempDir, app);
        var req = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        req.setAccessible(true);
        return (String) req.invoke(harness, port, raw);
    }

    @Test
    void bareAppLetsAuthorizationReachTheRoute() throws Exception {
        String raw = wire(BARE_APP, "GET /health HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer x\r\n\r\n");
        assertTrue(raw.contains("200"), "the issue's exact face: the route must answer, not the 401 gate: " + raw);
        assertFalse(raw.contains("unauthorized"), "no 401 body before the route: " + raw);
        assertTrue(raw.contains("up"), "the handler ran: " + raw);
    }

    @Test
    void configuredSecurityStillRejectsAnInvalidAuthorization() throws Exception {
        String raw = wire(SECURED_APP, "GET /health HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer x\r\n\r\n");
        assertTrue(raw.contains("401"), "WITH app.security() the D-SEC.4 rule holds (invalid token never passes): " + raw);
    }

    @Test
    void scopedPolicyAlsoOptsTheFaceIn() throws Exception {
        // securityConfigured é flag de APP (o plano da issue: setada por
        // app.security() E app.policy()) — uma vez configurada, a face
        // auth-if-present vale para o app inteiro (a policy de escopo só
        // refina os outros passos).
        String scoped = wire(SCOPED_APP, "GET /api/health HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer x\r\n\r\n");
        assertTrue(scoped.contains("401"), "an app.policy() scope opts the auth-if-present face in: " + scoped);
        String outside = wire(SCOPED_APP, "GET /health HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer x\r\n\r\n");
        assertTrue(outside.contains("401"), "the flag is app-level: the face covers the whole app: " + outside);
    }
}
