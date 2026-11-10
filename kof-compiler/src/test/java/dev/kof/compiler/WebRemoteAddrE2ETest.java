package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G14 — a route handler could not read the TCP peer address: the runtime kept
 * it (the rate limiter keys on it) but no context function exposed it, so a
 * client IP needed reflection. {@code remoteAddr()} returns it on the JVM and
 * JS web runtimes; Native refuses it at compile time like the other context
 * functions it does not emit.
 */
class WebRemoteAddrE2ETest {

    private static final String APP = """
            main() {
                var app = web.app()
                app.get("/ip") {
                    return "ip=" + remoteAddr()
                }
                app.listen(PORT)
            }
            """;

    @TempDir Path tmp;

    @Test
    void remoteAddrIsThePeerAddressOnJvm() throws Exception {
        int port = freePort();
        Path src = write("Jvm.kf", APP.replace("PORT", String.valueOf(port)));
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("jvm"), Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process server = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                tmp.resolve("jvm").toString(), "Default.Main").redirectErrorStream(true).start();
        try {
            assertTrue(TestServerFixture.awaitPort(port, 100, 100), "JVM server did not open " + port);
            assertEquals("ip=127.0.0.1", body(port));
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void remoteAddrIsThePeerAddressOnJs() throws Exception {
        int port = freePort();
        Path src = write("Js.kf", APP.replace("PORT", String.valueOf(port)));
        Path out = tmp.resolve("js");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread t = new Thread(() -> {
            try {
                dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), java.io.OutputStream.nullOutputStream(),
                        InputStream.nullInputStream(), err);
            } catch (Exception ignored) {
                // the test ends with the server still listening
            }
        }, "remote-addr-js");
        t.setDaemon(true);
        t.start();
        assertTrue(TestServerFixture.awaitPort(port, 50, 100),
                "JS server did not open " + port + ": " + err.toString(StandardCharsets.UTF_8));
        assertEquals("ip=127.0.0.1", body(port));
    }

    @Test
    void nativeRefusesItAtCompileTime() throws Exception {
        Path src = write("Nat.kf", APP.replace("PORT", "8080"));
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("nat"), Target.NATIVE);
        assertFalse(r.success(), "Native has no remoteAddr symbol; it must be refused, not linked broken");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "WEB001".equals(d.code())
                && d.message().contains("remoteAddr")), "the named WEB001 gap: " + r.diagnostics().getDiagnostics());
    }

    private Path write(String name, String code) throws IOException {
        Path p = tmp.resolve(name);
        Files.writeString(p, code);
        return p;
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static String body(int port) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(10000);
            s.getOutputStream().write("GET /ip HTTP/1.0\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            String raw = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int i = raw.indexOf("\r\n\r\n");
            return (i >= 0 ? raw.substring(i + 4) : raw).trim();
        }
    }
}
