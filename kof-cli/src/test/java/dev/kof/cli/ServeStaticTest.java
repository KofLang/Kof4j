package dev.kof.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F3 (plataforma): servidor de estáticos para full-stack (run/serve).
 * Porta efêmera (0); GET / → index.html; content-type por extensão;
 * path traversal (../) → 404 (R6: nunca serve fora do webRoot).
 */
class ServeStaticTest {

    private HttpServer server;
    private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String get(String path) throws IOException, InterruptedException {
        HttpResponse<String> r = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return r.statusCode() + " " + r.headers().firstValue("Content-Type").orElse("") + " :: " + r.body();
    }

    private int code(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void servesIndexAtRoot_andFiles(@TempDir Path web) throws Exception {
        write(web, "index.html", "<html>app</html>");
        write(web, "style.css", ".a{}");
        write(web, "js/app.mjs", "export {}");
        server = KofCliSupport.serveStatic(web, "127.0.0.1", 0);
        port = server.getAddress().getPort();

        assertTrue(get("/").contains("<html>app</html>"), get("/"));
        assertTrue(get("/").contains("text/html"), "Content-Type html");
        assertTrue(get("/style.css").contains(".a{}"));
        assertTrue(get("/style.css").contains("text/css"));
        assertTrue(get("/js/app.mjs").contains("export {}"));
        assertTrue(get("/js/app.mjs").contains("text/javascript"));
        assertEquals(404, code("/nao-existe.txt"));
    }

    @Test
    void pathTraversal_blocked(@TempDir Path web, @TempDir Path outside) throws Exception {
        write(web, "index.html", "ok");
        write(outside, "secret.txt", "TOP-SECRETO");
        // traversal relativo ao webRoot (../secret) → sempre 404
        Path secret = outside.resolve("secret.txt");
        server = KofCliSupport.serveStatic(web, "127.0.0.1", 0);
        port = server.getAddress().getPort();

        // traversal relativo ao webRoot → sempre 404
        assertEquals(404, code("/../" + secret.getFileName()));
        assertEquals(404, code("/..%2f..%2fetc%2fpasswd"));
        // o arquivo de fora NUNCA é servido
        assertNotEquals(200, code("/" + secret.getFileName()));
    }

    @Test
    void directoryServesIndex(@TempDir Path web) throws Exception {
        write(web, "sub/index.html", "<html>sub</html>");
        server = KofCliSupport.serveStatic(web, "127.0.0.1", 0);
        port = server.getAddress().getPort();
        assertTrue(get("/sub/").contains("<html>sub</html>"), get("/sub/"));
    }

    private static void write(Path dir, String rel, String content) throws IOException {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
    }
}
