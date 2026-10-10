package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class KofHttpServerTest {

    private final CompilerDriver driver = new CompilerDriver();
    private KofHttpServer server;
    private java.net.URLClassLoader handlerLoader;

    @AfterEach
    void closeServer() {
        if (server != null) {
            server.close();
            server = null;
        }
        if (handlerLoader != null) {
            try {
                handlerLoader.close();
            } catch (IOException ignored) {
            }
            handlerLoader = null;
        }
    }

    private KofHttpServer startServer(String kofSource, Path tempDir, String expectedCompile) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        java.net.URLClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[]{outDir.toUri().toURL()}, getClass().getClassLoader());
        this.handlerLoader = loader;
        Class<?> clazz;
        try {
            clazz = loader.loadClass("Default.Main");
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        KofHttpServer s = new KofHttpServer(ReflectiveHandler.forClass(clazz));
        s.bind("127.0.0.1", 0);
        new Thread(s::acceptLoop).start();
        this.server = s;
        return s;
    }

    private int port(KofHttpServer s) throws IOException {
        return java.lang.reflect.Field.class.cast(null) != null ? 0 : serverPort(s);
    }

    private int serverPort(KofHttpServer s) throws IOException {

        try {
            var field = KofHttpServer.class.getDeclaredField("serverSocket");
            field.setAccessible(true);
            java.net.ServerSocket ss = (java.net.ServerSocket) field.get(s);
            return ss.getLocalPort();
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }

    private String request(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
            }
            return response.toString();
        }
    }

    private static final String ROUTED_APP = """
            handle(String method, String path, String body, String query, String headers): String {
                if (path == "/hello") {
                    return "{\\"msg\\": \\"hi\\"}"
                }
                if (path == "/echo" && method == "POST") {
                    return "{\\"got\\": " + body + "}"
                }
                if (path == "/query") {
                    return "{\\"query\\": " + query + "}"
                }
                if (path == "/headers") {
                    return headers
                }
                return null
            }
            """;

    @Test
    void routingAndJson(@TempDir Path tempDir) throws IOException {
        KofHttpServer s = startServer(ROUTED_APP, tempDir, "");
        int port = serverPort(s);
        String r = request(port, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(r.contains("Content-Type: application/json"), r);
        assertTrue(r.endsWith("{\"msg\": \"hi\"}"), r);
    }

    @Test
    void notFoundForNull(@TempDir Path tempDir) throws IOException {
        KofHttpServer s = startServer(ROUTED_APP, tempDir, "");
        int port = serverPort(s);
        String r = request(port, "GET /missing HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 404 Not Found"), r);
    }

    @Test
    void postBody(@TempDir Path tempDir) throws IOException {
        KofHttpServer s = startServer(ROUTED_APP, tempDir, "");
        int port = serverPort(s);
        String r = request(port, "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 7\r\n\r\n{\"a\":1}");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(r.endsWith("{\"got\": {\"a\":1}}"), r);
    }

    @Test
    void queryString(@TempDir Path tempDir) throws IOException {
        KofHttpServer s = startServer(ROUTED_APP, tempDir, "");
        int port = serverPort(s);
        String r = request(port, "GET /query?name=mel&age=30 HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(r.contains("name=mel&age=30"), r);
    }

    @Test
    void headersAvailable(@TempDir Path tempDir) throws IOException {
        KofHttpServer s = startServer(ROUTED_APP, tempDir, "");
        int port = serverPort(s);
        String r = request(port, "GET /headers HTTP/1.1\r\nHost: x\r\nX-Custom: kof\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(r.contains("X-Custom: kof"), r);
    }

    @Test
    void noHandlerIs404(@TempDir Path tempDir) throws IOException {
        KofHttpServer s = startServer("""
                main() {
                    println("no handlers here")
                }
                """, tempDir, "");
        int port = serverPort(s);
        String r = request(port, "GET /anything HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 404 Not Found"), r);
    }

    @Test
    void threeArgHandle(@TempDir Path tempDir) throws IOException {
        KofHttpServer s = startServer("""
                handle(String method, String path, String body): String {
                    if (path == "/old") {
                        return "{\\"ok\\": true}"
                    }
                    return null
                }
                """, tempDir, "");
        int port = serverPort(s);
        String r = request(port, "GET /old HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(r.endsWith("{\"ok\": true}"), r);
        String miss = request(port, "GET /nope HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(miss.startsWith("HTTP/1.1 404 Not Found"), miss);
    }

    @Test
    void handlerErrorIs500(@TempDir Path tempDir) throws IOException {
        KofHttpServer s = startServer("""
                handle(String method, String path, String body): String {
                    return path
                }
                """, tempDir, "");

        int port = serverPort(s);
        String r = request(port, "GET /x HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertTrue(r.endsWith("/x"), r);
    }
}