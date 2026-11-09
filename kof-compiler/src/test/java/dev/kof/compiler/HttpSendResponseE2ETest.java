package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1126 — `http.send(method, url, body, headers)` returns `kof.http.Response`
 * with `status()/header(name)/headers()/body()` and NEVER throws on 4xx/5xx
 * (the maintainer decision 05/11, recorded in the issue): the contract lives
 * in the status code and headers, not in an exception. `get`/`post` stay
 * untouched. Slice 1 = JVM (where get/post work); Script/JS are slice 2.
 */
class HttpSendResponseE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** The issue's exact probe, parameterized by the ephemeral port. */
    private static String probe(int port) {
        return """
                main() {
                    val r = http.send("PUT", "http://127.0.0.1:PORT/created", "{}", "Content-Type: application/json")
                    println(r.status())
                    println(r.header("Location"))
                    println(r.header("ETag"))
                    println(r.body())
                    val bad = http.send("GET", "http://127.0.0.1:PORT/boom", null, null)
                    println(bad.status())
                    println(bad.body())
                }
                """.replace("PORT", String.valueOf(port));
    }

    private static HttpServer startServer(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/created", ex -> {
            byte[] body = "created-payload".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Location", "http://127.0.0.1/tasks/9");
            ex.getResponseHeaders().add("ETag", "W/\"v1\"");
            ex.sendResponseHeaders(201, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/boom", ex -> {
            byte[] body = "boom-payload".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(500, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server;
    }

    @Test
    void sendReturnsStatusHeadersBodyWithoutThrowingOn5xxJvm() throws Exception {
        int port;
        try (var probeSocket = new java.net.ServerSocket(0)) {
            port = probeSocket.getLocalPort();
        }
        HttpServer server = startServer(port);
        try {
            Path dir = tmp.resolve("send");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("Main.kf"), probe(port));
            Path out = dir.resolve("out");
            CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
            assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
            var stdout = new ByteArrayOutputStream();
            var previousOut = System.out;
            try {
                System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
                try (var loader = new URLClassLoader(new URL[]{out.toUri().toURL()},
                        getClass().getClassLoader())) {
                    Class.forName("Default.Main", true, loader)
                            .getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                }
            } finally {
                System.setOut(previousOut);
            }
            String outText = stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
            // NO throw on 5xx; the status/headers/body are all readable.
            assertEquals("201\nhttp://127.0.0.1/tasks/9\nW/\"v1\"\ncreated-payload\n500\nboom-payload", outText);
        } finally {
            server.stop(0);
        }
    }
}
