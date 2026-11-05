package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §832 — {@code http.send(method, url[, body][, headers...])} returns a
 * {@code Response} with {@code status()}, {@code body()}, {@code header(name)}
 * and {@code headers()}; a 4xx/5xx is returned, not thrown, and a transport
 * failure still reaches {@code catch (String e)}. Same output on JVM, Script
 * and JS; Native refuses with HTTP002.
 */
class HttpSendResponseE2ETest {

    private static final String EXPECTED = String.join("\n",
            "201", "PUT hello t1", "yes", "yes", "true", "yes", "Response[status=201]",
            "404", "nope", "503", "DELETE down", "refused");

    private HttpServer server;
    private ExecutorService pool;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pool = Executors.newFixedThreadPool(2);
        server.setExecutor(pool);
        server.createContext("/echo", ex -> {
            String in = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = (ex.getRequestMethod() + " " + in + " " + ex.getRequestHeaders().getFirst("X-Token"))
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("X-Reply", "yes");
            ex.sendResponseHeaders(201, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/missing", ex -> reply(ex, 404, "nope"));
        server.createContext("/boom", ex -> reply(ex, 503, ex.getRequestMethod() + " down"));
        server.start();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        pool.shutdownNow();
    }

    private String program() throws IOException {
        String closed;
        try (ServerSocket s = new ServerSocket(0)) {
            closed = "http://127.0.0.1:" + s.getLocalPort() + "/";
        }
        return """
                main() {
                    val r = http.send("PUT", "BASE/echo", "hello", "X-Token: t1", "Accept: text/plain")
                    println(r.status())
                    println(r.body())
                    println(r.header("x-reply"))
                    println(r.header("X-REPLY"))
                    println(r.header("absent") == null)
                    val hs = r.headers()
                    println(hs.get("x-reply"))
                    println(r)
                    val m: Response = http.send("GET", "BASE/missing")
                    println(m.status())
                    println(m.body())
                    val d = http.send("delete", "BASE/boom", null)
                    println(d.status())
                    println(d.body())
                    try {
                        http.send("GET", "CLOSED")
                        println("no")
                    } catch (String e) {
                        println("refused")
                    }
                }
                """.replace("BASE", "http://127.0.0.1:" + server.getAddress().getPort()).replace("CLOSED", closed);
    }

    @Test
    void sendReturnsTheResponseOnJvmScriptAndJs(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, program());

        Path jvmOut = tmp.resolve("jvm");
        CompilationResult jvm = new CompilerDriver().compile(src, jvmOut, Target.JVM);
        assertTrue(jvm.success(), () -> "JVM compile: " + jvm.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{jvmOut.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals(EXPECTED, buf.toString(StandardCharsets.UTF_8).trim(), "JVM");

        KofInterpreter.Result script = new CompilerDriver().interpret(List.of(src), tmp, new String[0]);
        assertEquals(EXPECTED, script.stdout().trim(), () -> "Script, stderr: " + script.stderr());

        Path jsOut = tmp.resolve("js");
        CompilationResult js = new CompilerDriver().compile(src, jsOut, Target.JS);
        assertTrue(js.success(), () -> "JS compile: " + js.diagnostics().getDiagnostics());
        ByteArrayOutputStream jsBuf = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsOut.resolve("Default.mjs"), jsBuf,
                new java.io.ByteArrayInputStream(new byte[0]), jsBuf);
        assertEquals(0, exit, () -> "JS: " + jsBuf);
        assertEquals(EXPECTED, jsBuf.toString(StandardCharsets.UTF_8).trim(), "JS");
    }

    @Test
    void anUnknownAccessorIsSem025AndNativeRefusesWithHttp002(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
                main() {
                    val r = http.send("GET", "http://127.0.0.1:1/")
                    println(r.json())
                }
                """);
        CompilationResult bad = new CompilerDriver().compile(src, tmp.resolve("bad"), Target.JVM);
        assertFalse(bad.success());
        assertTrue(bad.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM025".equals(d.code())
                && d.message().contains("valid: status/body/header/headers")), () -> bad.diagnostics().getDiagnostics().toString());

        Files.writeString(src, """
                main() {
                    println(http.send("GET", "http://127.0.0.1:1/").status())
                }
                """);
        CompilationResult nat = new CompilerDriver().compile(src, tmp.resolve("nat"), Target.NATIVE);
        assertFalse(nat.success());
        assertTrue(nat.diagnostics().getDiagnostics().stream().anyMatch(d -> "HTTP002".equals(d.code())
                && d.message().startsWith("http.send")), () -> nat.diagnostics().getDiagnostics().toString());
    }
}
