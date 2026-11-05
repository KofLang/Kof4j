package dev.kof.compiler;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JVM router answered by registration order: {@code GET /posts/:id}
 * registered before {@code GET /posts/seals} swallowed {@code /posts/seals}.
 * At the first segment where two routes of the same length differ, a literal
 * now wins over a {@code :param} (Spring's rule) on the JVM and on JS, whatever
 * the order the routes were registered in.
 */
class WebRoutePrecedenceE2ETest extends ServerProcessSupport {

    private static final String APP = """
            main() {
                var app = web.app()
                app.get("/posts/:id") { return "param " + param("id") }
                app.get("/posts/seals") { return "literal" }
                app.get("/:a/b/:c") { return "a-first" }
                app.get("/:a/:b/c") { return "b-later" }
                app.get("/x/:b/:c") { return "x-first" }
                app.listen(PORT)
            }
            """;

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static String body(int port, String path) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(15000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.0\r\nHost: x\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int idx = raw.indexOf("\r\n\r\n");
            return idx >= 0 ? raw.substring(idx + 4).trim() : raw;
        }
    }

    private static void assertPrecedence(int port) throws IOException {
        assertEquals("literal", body(port, "/posts/seals"));
        assertEquals("param 7", body(port, "/posts/7"));
        // `/x/b/c`: `/x/:b/:c` has the literal at the first segment that differs.
        assertEquals("x-first", body(port, "/x/b/c"));
        // `/q/b/c`: `/:a/b/:c` has the literal at the second segment.
        assertEquals("a-first", body(port, "/q/b/c"));
    }

    @Test
    void literalSegmentsWinOnTheJvm(@TempDir Path tmp) throws Exception {
        int port = freePort();
        Path source = tmp.resolve("App.kf");
        Files.writeString(source, APP.replace("PORT", String.valueOf(port)));
        Path out = tmp.resolve("classes");
        CompilationResult r = new CompilerDriver().compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        serverProcess = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        TestServerFixture.awaitListening(serverProcess, port);
        assertPrecedence(port);
    }

    @Test
    void literalSegmentsWinOnJs(@TempDir Path tmp) throws Exception {
        int port = freePort();
        Path source = tmp.resolve("App.kf");
        Files.writeString(source, APP.replace("PORT", String.valueOf(port)));
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(source, out, Target.JS);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        Path entry;
        try (var s = Files.walk(out)) {
            entry = s.filter(p -> p.toString().endsWith(".mjs")).sorted(
                    (a, b) -> Boolean.compare(!a.endsWith("Default.mjs"), !b.endsWith("Default.mjs")))
                    .findFirst().orElseThrow();
        }
        Thread server = new Thread(() -> {
            try {
                dev.kof.runtime.KofJsRunner.run(entry, OutputStream.nullOutputStream(),
                        InputStream.nullInputStream(), OutputStream.nullOutputStream());
            } catch (Exception ignored) {
                // the socket closing at the end of the test ends the runner
            }
        }, "kof-web-route-precedence-js");
        server.setDaemon(true);
        server.start();
        assertTrue(TestServerFixture.awaitPort(port, 50, 100), "the JS server must listen on " + port);
        assertPrecedence(port);
    }
}
