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

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1110 — a route handler can read the TCP peer address of the request via
 * `remoteAddr()` (String; `""` when unknown; the maintainer decision 05/11
 * recorded in the issue). The WebRequest already CARRIES the peer address
 * (the rate limiter's key reads it) — nothing exposed it to handlers
 * (SEM015). Behind a proxy the client is the `x-forwarded-for` header (the
 * documented note).
 *
 * <p>The JVM face uses this repo's `KofWebPrograms` harness; the JS face and
 * the named Native gap come from the contributing PR (#1111), which measured
 * both — the merged suite keeps the lab naming and both extra faces.
 */
class WebRemoteAddrE2ETest extends KofWebPrograms {

    private static final String APP = """
            main() {
                var app = web.app()
                app.get("/ip") {
                    return "ip=" + remoteAddr()
                }
                app.listen(PORT)
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void remoteAddrReadsThePeerAddressJvm() throws Exception {
        KofWebE2ETest harness = new KofWebE2ETest();
        var method = KofWebE2ETest.class.getDeclaredMethod("startServer", Path.class, String.class);
        method.setAccessible(true);
        int port = (int) method.invoke(harness, tempDir, APP);
        var req = KofWebE2ETest.class.getDeclaredMethod("request", int.class, String.class);
        req.setAccessible(true);
        var body = KofWebE2ETest.class.getDeclaredMethod("bodyOf", String.class);
        body.setAccessible(true);
        String raw = (String) req.invoke(harness, port,
                "GET /ip HTTP/1.1\r\nHost: x\r\n\r\n");
        String bodyText = (String) body.invoke(harness, raw);
        assertTrue(bodyText.startsWith("ip="), "the peer address face: " + bodyText);
        assertFalse(bodyText.equals("ip="), "the address is recorded, not empty: " + bodyText);
        assertTrue(bodyText.contains("127.0.0.1"),
                "the loopback request reads the loopback peer: " + bodyText);
    }

    @Test
    void remoteAddrIsThePeerAddressOnJs() throws Exception {
        int port = freePort();
        Path src = write("Js.kf", APP.replace("PORT", String.valueOf(port)));
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
        }, "remote-addr-js");
        t.setDaemon(true);
        t.start();
        assertTrue(TestServerFixture.awaitPort(port, 200, 100),
                "JS server did not open " + port + ": " + err.toString(StandardCharsets.UTF_8));
        assertEquals("ip=127.0.0.1", body(port));
    }

    @Test
    void nativeRefusesItAtCompileTime() throws Exception {
        Path src = write("Nat.kf", APP.replace("PORT", "8080"));
        CompilationResult r = new CompilerDriver().compile(src, tempDir.resolve("nat"), Target.NATIVE);
        assertFalse(r.success(), "Native has no remoteAddr symbol; it must be refused, not linked broken");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "WEB001".equals(d.code())
                && d.message().contains("remoteAddr")), "the named WEB001 gap: " + r.diagnostics().getDiagnostics());
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

    private static String body(int port) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(10000);
            s.getOutputStream().write("GET /ip HTTP/1.0\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.UTF_8));
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