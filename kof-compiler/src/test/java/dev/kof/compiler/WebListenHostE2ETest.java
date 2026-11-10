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

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1114 — `app.listen(host, port)` binds ONE interface (the maintainer
 * decision 05/11 recorded in the issue): `app.listen("127.0.0.1", port)`
 * serves a local-only service; `app.listen(port)` KEEPS the wildcard binding
 * (the existing face untouched — the freeze rule).
 *
 * <p>The JVM faces use this repo's `KofWebPrograms` harness; the JS face and
 * the named Native/SEM025 refusals come from the contributing PR (#1115),
 * which measured both.
 */
class WebListenHostE2ETest extends KofWebPrograms {

    @TempDir
    Path tempDir;

    private static final String HOST_APP = """
            main() {
                var app = web.app()
                app.get("/x") {
                    return "ok"
                }
                app.listen("127.0.0.1", PORT)
            }
            """;

    private static final String WILDCARD_APP = """
            main() {
                var app = web.app()
                app.get("/x") {
                    return "ok"
                }
                app.listen(PORT)
            }
            """;

    @Test
    void listenHostBindsOneInterfaceJvm() throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        var start = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        start.setAccessible(true);
        int port = (int) start.invoke(harness, tempDir, HOST_APP);
        var req = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        req.setAccessible(true);
        var body = KofWebE2ETest.class.getDeclaredMethod("bodyOf", String.class);
        body.setAccessible(true);
        String raw = (String) req.invoke(harness, port, "GET /x HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");
        String bodyText = (String) body.invoke(harness, raw);
        assertTrue(raw.startsWith("HTTP/1.1 200 OK"), "the host-bound server answers: " + raw);
        assertEquals("ok", bodyText, "the chained face");
        assertNotReachableFromOtherInterfaces(port);
    }

    @Test
    void listenPortKeepsTheWildcardJvm() throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        var start = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        start.setAccessible(true);
        int port = (int) start.invoke(harness, tempDir, WILDCARD_APP);
        var req = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        req.setAccessible(true);
        var body = KofWebE2ETest.class.getDeclaredMethod("bodyOf", String.class);
        body.setAccessible(true);
        String raw = (String) req.invoke(harness, port, "GET /x HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");
        assertEquals("ok", body.invoke(harness, raw), "the wildcard face is untouched");
    }

    @Test
    void listenBindsOnlyTheGivenHostOnJs() throws Exception {
        int port = freePort();
        Path src = write("Js.kf", HOST_APP.replace("PORT", String.valueOf(port)));
        Path out = tempDir.resolve("js");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread t = new Thread(() -> {
            try {
                dev.kof.runtime.KofJsRunner.run(findJsEntry(out), java.io.OutputStream.nullOutputStream(),
                        InputStream.nullInputStream(), err);
            } catch (Exception ignored) {
                // the test ends with the server still listening
            }
        }, "listen-host-js");
        t.setDaemon(true);
        t.start();
        assertTrue(TestServerFixture.awaitPort(port, 200, 100),
                "JS server did not open " + port + ": " + err.toString(StandardCharsets.UTF_8));
        assertEquals("ok", body("127.0.0.1", port));
        assertNotReachableFromOtherInterfaces(port);
    }

    @Test
    void nativeRefusesTheHostFormAndAWrongTypeIsSem025() throws Exception {
        Path nat = write("Nat.kf", HOST_APP.replace("PORT", "8080"));
        CompilationResult n = new CompilerDriver().compile(nat, tempDir.resolve("nat"), Target.NATIVE);
        assertFalse(n.success(), "Native has no host bind; it must be refused, not linked broken");
        assertTrue(n.diagnostics().getDiagnostics().stream().anyMatch(d -> "WEB001".equals(d.code())),
                "the named WEB001 gap: " + n.diagnostics().getDiagnostics());
        Path bad = write("Bad.kf", HOST_APP.replace("\"127.0.0.1\", PORT", "8080, 8081"));
        CompilationResult b = new CompilerDriver().compile(bad, tempDir.resolve("bad"), Target.JVM);
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
        Path p = tempDir.resolve(name);
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
            String raw = TestServerFixture.readResponse(s.getInputStream());
            int i = raw.indexOf("\r\n\r\n");
            return (i >= 0 ? raw.substring(i + 4) : raw).trim();
        }
    }

    /** The compiled JS entry (the same lookup `KofWebJsE2ETest` uses). */
    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new IOException("no .mjs in " + dir));
        }
    }
}