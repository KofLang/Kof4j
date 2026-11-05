package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lambda passed where {@code () -> String?} is expected was typed by its own
 * body ({@code String}, or void for a lone {@code return null}) and its class
 * implemented another functional interface than the one the callee invokes:
 * {@code IncompatibleClassChangeError: Lambda0 does not implement
 * Function0_NCString}. A web middleware that only ever returns {@code null}
 * failed every request the same way ({@code handler error}). The lambda is
 * now lowered against the expected function type wherever one is written: an
 * argument, a declared local, a field assignment and a {@code return}.
 */
class LambdaNullableReturnTargetE2ETest extends ServerProcessSupport {

    @TempDir Path tmp;

    @Test
    void aLambdaArgumentImplementsTheExpectedNullableFunctionType() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
            String? run(f: () -> String?) = f()
            main() {
                println(run(() -> {
                    println("side")
                    return null
                }))
                println(run(() -> { return "a" }))
                println(run(() -> {
                    if (1 > 2) { return "b" }
                    return null
                }))
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("side\nnull\na\nnull\n", buf.toString());
    }

    @Test
    void declaredLocalsFieldsAndReturnsTypeTheLambdaToo() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
            String? call(() -> String? f) { return f() }
            () -> String? mk() {
                return () -> "r"
            }
            class H {
                () -> String? f
                H(() -> String? g) { this.f = g }
                void clear() { this.f = () -> null }
                String? run() {
                    val g = this.f
                    return g()
                }
            }
            main() {
                val h: () -> String? = () -> "y"
                println(call(h))
                println(mk()())
                val k = H(() -> "init")
                println(k.run())
                k.clear()
                println(k.run() == null)
            }
            """);
        Path out = tmp.resolve("out2");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("y\nr\ninit\ntrue\n", buf.toString());
    }

    @Test
    void aMiddlewareThatOnlyReturnsNullLetsTheRouteAnswer() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        Path src = tmp.resolve("App.kf");
        Files.writeString(src, """
            main() {
                var app = web.app()
                app.use {
                    headerSet("X-Request-Id", "r-1")
                    return null
                }
                app.get("/hi") { return "hello" }
                app.listen(PORT)
            }
            """.replace("PORT", String.valueOf(port)));
        Path out = tmp.resolve("classes");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        serverProcess = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        TestServerFixture.awaitListening(serverProcess, port);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream o = socket.getOutputStream();
            o.write("GET /hi HTTP/1.0\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            o.flush();
            InputStream in = socket.getInputStream();
            String resp = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(resp.startsWith("HTTP/1.1 200"), resp);
            assertTrue(resp.contains("X-Request-Id: r-1"), resp);
            assertTrue(resp.endsWith("hello"), resp);
        }
    }
}
