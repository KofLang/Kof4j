package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §825 — {@code app.listen(host, port)} binds the given interface. Before,
 * {@code listen(port)} was the only form and always bound {@code 0.0.0.0}:
 * a local-only service was reachable from the network, and on macOS a
 * wildcard bind can share a port another process holds on 127.0.0.1, so a
 * client of 127.0.0.1 reached that process instead.
 */
class WebListenHostE2ETest {

    private static final String APP = """
            main() {
                var app = web.app()
                app.get("/x") { return "ok" }
                app.listen("127.0.0.1", PORT)
            }
            """;

    @TempDir Path tmp;

    @Test
    void listenBindsOnlyTheGivenHostOnJvm() throws Exception {
        int port = freePort();
        Path src = write("Jvm.kf", APP.replace("PORT", String.valueOf(port)));
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("jvm"), Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process server = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                tmp.resolve("jvm").toString(), "Default.Main").redirectErrorStream(true).start();
        try {
            assertTrue(TestServerFixture.awaitPort(port, 100, 100), "JVM server did not open " + port);
            assertEquals("ok", body("127.0.0.1", port));
            assertNotReachableFromOtherInterfaces(port);
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void listenBindsOnlyTheGivenHostOnJs() throws Exception {
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
        }, "listen-host-js");
        t.setDaemon(true);
        t.start();
        assertTrue(TestServerFixture.awaitPort(port, 50, 100),
                "JS server did not open " + port + ": " + err.toString(StandardCharsets.UTF_8));
        assertEquals("ok", body("127.0.0.1", port));
        assertNotReachableFromOtherInterfaces(port);
    }

    @Test
    void nativeRefusesTheHostFormAndAWrongTypeIsSem025() throws Exception {
        Path nat = write("Nat.kf", APP.replace("PORT", "8080"));
        CompilationResult n = new CompilerDriver().compile(nat, tmp.resolve("nat"), Target.NATIVE);
        assertFalse(n.success(), "Native has no host bind; it must be refused, not linked broken");
        assertTrue(n.diagnostics().getDiagnostics().stream().anyMatch(d -> "WEB001".equals(d.code())),
                "the named WEB001 gap: " + n.diagnostics().getDiagnostics());
        Path bad = write("Bad.kf", APP.replace("\"127.0.0.1\", PORT", "8080, 8081"));
        CompilationResult b = new CompilerDriver().compile(bad, tmp.resolve("bad"), Target.JVM);
        assertFalse(b.success(), "listen(Int, Int) must not compile");
        assertTrue(b.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM025".equals(d.code())),
                "expected SEM025: " + b.diagnostics().getDiagnostics());
    }

    /** A loopback-only server refuses a connection to any non-loopback IPv4 address of this host. */
    private static void assertNotReachableFromOtherInterfaces(int port) throws IOException {
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback()) continue;
            for (InetAddress a : Collections.list(nic.getInetAddresses())) {
                if (!(a instanceof Inet4Address)) continue;
                assertThrows(IOException.class, () -> {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(a, port), 2000);
                    }
                }, "the server must not answer on " + a.getHostAddress());
            }
        }
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

    private static String body(String host, int port) throws IOException {
        try (Socket s = new Socket(host, port)) {
            s.setSoTimeout(10000);
            s.getOutputStream().write("GET /x HTTP/1.0\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            String raw = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int i = raw.indexOf("\r\n\r\n");
            return (i >= 0 ? raw.substring(i + 4) : raw).trim();
        }
    }
}
