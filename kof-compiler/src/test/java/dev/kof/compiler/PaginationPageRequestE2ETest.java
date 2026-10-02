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
 * End-to-end test for the P5 HTTP pagination helper (`kof.web.pageRequest`,
 * D-PAGINATION P5). The helper is a pure-Kof host injected on `import kof.web`
 * and reads the ambient request via `query(...)`; the handler maps the named
 * `PAGINATION:` error to 400. JVM proof here; JS shares the same pure-Kof body
 * + `kof_web_query` (already parity-tested); Native is a declared WEB001 gap
 * (plan §13).
 */
class PaginationPageRequestE2ETest {

    private static final String JAVA_BIN = java.nio.file.Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private static final String APP = """
            import kof.web

            main() {
                var app = web.app()
                app.get("/list") {
                    try {
                        val p = pageRequest(20, 100)
                        return "p:" + p.page() + " l:" + p.limit() + " o:" + p.offset()
                    } catch (String e) {
                        return status(400, e)
                    }
                }
                app.listen(PORT)
            }
            """;

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

    private int startServer(Path tempDir) throws IOException {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, APP.replace("PORT", String.valueOf(port)));
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

    private String request(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            return response.toString();
        }
    }

    private String bodyOf(String rawResponse) {
        int idx = rawResponse.indexOf("\r\n\r\n");
        return idx >= 0 ? rawResponse.substring(idx + 4) : rawResponse;
    }

    private String get(int port, String path) throws IOException {
        return request(port, "GET " + path + " HTTP/1.1\r\nHost: x\r\n\r\n");
    }

    @Test
    void pageAndLimitProduceOffset(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = get(port, "/list?page=3&limit=20");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertEquals("p:3 l:20 o:40", bodyOf(r), r);
    }

    @Test
    void defaultsToFirstPage(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = get(port, "/list");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertEquals("p:1 l:20 o:0", bodyOf(r), r);
    }

    @Test
    void limitIsClampedToMax(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = get(port, "/list?limit=500");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertEquals("p:1 l:100 o:0", bodyOf(r), r);
    }

    @Test
    void explicitOffsetWins(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = get(port, "/list?offset=45");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertEquals("p:1 l:20 o:45", bodyOf(r), r);
    }

    @Test
    void pageZeroIsANamedError(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = get(port, "/list?page=0");
        assertTrue(r.startsWith("HTTP/1.1 400"), r);
        assertEquals("PAGINATION: page must be >= 1", bodyOf(r), r);
    }

    @Test
    void nonIntegerLimitIsANamedError(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = get(port, "/list?limit=abc");
        assertTrue(r.startsWith("HTTP/1.1 400"), r);
        assertEquals("PAGINATION: limit must be an integer", bodyOf(r), r);
    }

    @Test
    void negativeOffsetIsANamedError(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir);
        String r = get(port, "/list?offset=-5");
        assertTrue(r.startsWith("HTTP/1.1 400"), r);
        assertEquals("PAGINATION: offset must be >= 0", bodyOf(r), r);
    }

    @Test
    void pageRequestRequiresTheWebImport(@TempDir Path tempDir) throws IOException {
        int port = freePort();
        String source = """
                main() {
                    var app = web.app()
                    app.get("/x") {
                        return "o:" + pageRequest(20).offset()
                    }
                    app.listen(PORT)
                }
                """.replace("PORT", String.valueOf(port));
        Path file = tempDir.resolve("NoImport.kf");
        Files.writeString(file, source);
        CompilationResult result = driver.compile(file, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "pageRequest must be unknown without `import kof.web`");
    }
}
