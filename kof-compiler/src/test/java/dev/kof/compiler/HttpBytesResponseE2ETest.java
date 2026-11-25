package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §1227 (issue #1184): every `kof.http` response crossed
 * `BodyHandlers.ofString()` — a binary body arrived MUTILATED (the 0x89 of a
 * PNG turned into U+FFFD, `efbfbd` in UTF-8) and the `length()` even matched,
 * hiding it. The measured contract: `Response.bytes()` delivers the RAW bytes
 * (the same `Int[]` face as `File.readBytes()`), and `body()` still yields the
 * UTF-8-decoded string for the text face.
 */
class HttpBytesResponseE2ETest {

    private static final byte[] PNG = {azerte(0x89), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static byte azerte(int b) {
        return (byte) b;
    }

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void bytesFaceDeliversRawBytesJvm() throws Exception {
        com.sun.net.httpserver.HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/test.png", ex -> {
            ex.getResponseHeaders().add("Content-Type", "image/png");
            ex.sendResponseHeaders(200, PNG.length);
            try (var os = ex.getResponseBody()) {
                os.write(PNG);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            Path src = tmp.resolve("main.kf");
            Files.writeString(src, """
                    import kof.http
                    import kof.encoding

                    main() {
                        var resposta = http.send("GET", "http://127.0.0.1:%d/test.png", "", "")
                        println(resposta.status())
                        var bs = resposta.bytes()
                        println(bs.length)
                        println(bs[0])
                        println(bs[1])
                        println(bs[3])
                        // a face de STRING continua: o body() decoda UTF-8 (o 0x89
                        // vira U+FFFD LÁ — é o contrato de texto, não um crash).
                        println(resposta.body().length)
                    }
                    """.formatted(port));
            Path out = tmp.resolve("out");
            CompilationResult r = driver.compile(src, out, Target.JVM);
            assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
            Process p = new ProcessBuilder("java", "--enable-native-access=ALL-UNNAMED",
                    "-cp", out.toString(), "Default/Main").redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).strip();
            assertEquals(0, p.waitFor(), o);
            // 200 / 8 bytes / 0x89=137 / 0x50=80 / 0x47=71 / body length (o U+FFFD
            // cada byte inválido vira 1 replacement char (o U+FFFD) — 8 bytes -> 8 chars).
            assertEquals("200\n8\n137\n80\n71\n8", o,
                    "#1184: the bytes() face delivers the RAW bytes (0x89 intact), the body() keeps the text contract");
        } finally {
            server.stop(0);
        }
    }
}
