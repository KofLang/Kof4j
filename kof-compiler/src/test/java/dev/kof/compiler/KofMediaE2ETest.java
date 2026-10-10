package dev.kof.compiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E do kof.media + serveDir — a alternativa ao padrão do
 * Kof-editor-theme-maker (pageCss(): String { um CSS inteiro },
 * kofPngData(): String { um base64 inteiro }).
 *
 * A linguagem ganha gestão de arquivos real:
 *   - app.serveDir("/img", "assets") serve o ARQUIVO do disco com o
 *     content-type correto (binário cru, sem base64 colado em String);
 *   - Image.open/save manipula imagem via javax.imageio;
 *   - Audio.openWav/saveWav via WAV (javax.sound para o mic).
 *
 * Servidor real em subprocesso (mesmo padrão do KofWebE2ETest).
 */
class KofMediaE2ETest extends KofMediaSupport {

    private static final String JAVA_BIN = java.nio.file.Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();

    @org.junit.jupiter.api.io.TempDir
    Path appDir;

    @BeforeEach
    void makeAssets() throws IOException {
        Path assets = appDir.resolve("assets");
        Files.createDirectories(assets);
        BufferedImage img = new BufferedImage(37, 41, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.setRGB(x, y, (x * 305 | y * 7 | 0x404040) & 0xFFFFFF);
            }
        }
        assertTrue(ImageIO.write(img, "png", assets.resolve("logo.png").toFile()));
        Files.writeString(assets.resolve("style.css"),
                ":root { --accent: #8be9fd; }\nbody { background: var(--accent); }\n");
    }


    private int startServer(Path tempDir, String kofSource) throws IOException {
        int port = freePort();
        Path source = appDir.resolve("App.kf");
        Files.writeString(source, kofSource.replace("PORT", String.valueOf(port)));
        Path outDir = appDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        // kof.root = dir do .kf — caminhos relativos do app resolvem daí
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN,
                "-Dkof.root=" + tempDir.toAbsolutePath().toString(),
                "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        TestServerFixture.awaitListening(serverProcess, port);
        return port;
    }

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private String request(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            return response.toString();
        }
    }

    private String headersOf(String rawResponse) {
        int idx = rawResponse.indexOf("\r\n\r\n");
        return idx >= 0 ? rawResponse.substring(0, idx) : rawResponse;
    }

    private byte[] rawBytes(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            StringBuilder head = new StringBuilder();
            byte[] buf = new byte[8192];
            int n;
            int sep = -1;
            while ((n = in.read(buf)) != -1) {
                if (sep < 0) {
                    String chunk = new String(buf, 0, n, StandardCharsets.ISO_8859_1);
                    int at = chunk.indexOf("\r\n\r\n");
                    if (at >= 0) {
                        sep = head.length() + at;
                        head.append(chunk, 0, at + 4);
                        byte[] rest = new byte[n - (at + 4)];
                        System.arraycopy(buf, at + 4, rest, 0, rest.length);
                        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
                        body.write(rest);
                        byte[] more;
                        while ((n = in.read(buf)) != -1) body.write(buf, 0, n);
                        return body.toByteArray();
                    }
                    head.append(chunk);
                }
            }
            return head.toString().getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    // ── serveDir: o arquivo do disco, com content-type ──────────────

    private static final String SERVE_APP = """
            main() {
                var app = web.app()
                app.serveDir("/img", "assets")
                app.listen(PORT)
            }
            """;

    @Test
    void servesPngWithCorrectContentTypeAndRawBytes() throws IOException {
        byte[] png = Files.readAllBytes(appDir.resolve("assets/logo.png"));
        int port = startServer(appDir, SERVE_APP);
        byte[] body = rawBytes(port, "GET /img/logo.png HTTP/1.1\r\nHost: x\r\n\r\n");
        // o corpo devolvido é o ARQUIVO cru — byte a byte, sem base64
        assertArrayEquals(png, body, "PNG servido em binário cru (sem kofPngData base64)");
        String r = request(port, "GET /img/logo.png HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200"), "status 200: " + r.split("\r\n", 2)[0]);
        assertTrue(r.contains("Content-Type: image/png"), "content-type: " + headersOf(r));
        assertTrue(r.contains("Cache-Control: public"), "cache header");
    }

    @Test
    void servesCssWithCorrectContentType() throws IOException {
        byte[] css = Files.readAllBytes(appDir.resolve("assets/style.css"));
        int port = startServer(appDir, SERVE_APP);
        String r = request(port, "GET /img/style.css HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.contains("Content-Type: text/css"), "content-type css: " + headersOf(r));
        String body = r.substring(r.indexOf("\r\n\r\n") + 4);
        assertEquals(new String(css, StandardCharsets.UTF_8), body,
                "CSS servido como ARQUIVO — sem pageCss(): String no fonte");
    }

    @Test
    void rejectsPathTraversal() throws IOException {
        int port = startServer(appDir, SERVE_APP);
        String r = request(port, "GET /img/..%2f..%2fApp.kf HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 404"), "traversal bloqueado (404): "
                + r.split("\r\n", 2)[0]);
        assertFalse(r.contains("web.app()"), "fonte do app não vaza");
    }

    @Test
    void missingFileIs404() throws IOException {
        int port = startServer(appDir, SERVE_APP);
        String r = request(port, "GET /img/nao-existe.png HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 404"), "404: " + r.split("\r\n", 2)[0]);
    }

    @Test
    void serveDirTrailingSlashServesIndex() throws IOException {
        // GitHub #35.2: GET /img/ (barra final — browsers digitam) deve servir
        // o index.html do diretório, não 404.
        Files.writeString(appDir.resolve("assets/index.html"), "<html>home</html>");
        int port = startServer(appDir, SERVE_APP);
        String r = request(port, "GET /img/ HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200"), "barra final → 200: " + r.split("\r\n", 2)[0]);
        String body = r.substring(r.indexOf("\r\n\r\n") + 4);
        assertTrue(body.contains("home"), "index.html servido: " + body);
    }

    // ── serveDir("/") — prefixo RAIZ: o case canônico do full-stack (I2) ──
    // O bundle do frontend é montado em "/" (app.serveDir("/", KOF_WEB_OUT));
    // sem isso só o index servia e os .mjs do bundle davam 404.

    private static final String SERVE_ROOT_APP = """
            main() {
                var app = web.app()
                app.serveDir("/", "assets")
                app.listen(PORT)
            }
            """;

    @Test
    void servesRootPrefixFiles_notJustIndex() throws IOException {
        int port = startServer(appDir, SERVE_ROOT_APP);
        assertTrue(request(port, "GET /style.css HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200"), "arquivo no prefixo raiz serve (200)");
        assertTrue(request(port, "GET /logo.png HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200"), "binário no prefixo raiz serve (200)");
        assertTrue(request(port, "GET / HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 404"), "sem index.html no dir → 404 (não 500)");
    }

    @Test
    void rootPrefix_stillBlocksTraversal() throws IOException {
        int port = startServer(appDir, SERVE_ROOT_APP);
        String r = request(port, "GET /..%2fApp.kf HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 404"), "traversal no prefixo raiz bloqueado (404): "
                + r.split("\r\n", 2)[0]);
        assertFalse(r.contains("web.app()"), "fonte do app não vaza");
    }

    // ── Image: javax.imageio, sem base64 literal ────────────────────

    private static final String IMAGE_APP = """
            main() {
                var app = web.app()
                app.get("/dims") {
                    var img = Image.open("assets/logo.png")
                    var fmt = img.format()
                    return "w=" + img.width() + " h=" + img.height() + " fmt=" + fmt
                }
                app.get("/convert") {
                    var img = Image.open("assets/logo.png")
                    img.saveAs("assets/logo-conv.jpg", "jpeg")
                    return "saved"
                }
                app.listen(PORT)
            }
            """;

    @Test
    void imageOpenWidthHeightFormat() throws IOException {
        int port = startServer(appDir, IMAGE_APP);
        String r = request(port, "GET /dims HTTP/1.1\r\nHost: x\r\n\r\n");
        String body = r.substring(r.indexOf("\r\n\r\n") + 4).trim();
        assertEquals("w=37 h=41 fmt=png", body, "dimensões reais do arquivo: " + body);
    }

    @Test
    void imageSaveConvertsFormat() throws IOException {
        int port = startServer(appDir, IMAGE_APP);
        String r = request(port, "GET /convert HTTP/1.1\r\nHost: x\r\n\r\n");
        String body = r.substring(r.indexOf("\r\n\r\n") + 4).trim();
        assertEquals("saved", body);
        Path out = appDir.resolve("assets/logo-conv.jpg");
        assertTrue(Files.isRegularFile(out), "JPEG gravado no disco");
        BufferedImage read = ImageIO.read(out.toFile());
        assertNotNull(read, "JPEG legível");
        assertEquals(37, read.getWidth());
        assertEquals(41, read.getHeight());
    }

    // ── Audio: WAV (sem hardware) ───────────────────────────────────


    private static final String AUDIO_APP = """
            main() {
                var app = web.app()
                app.get("/info") {
                    var a = Audio.openWav("assets/tone.wav")
                    return "sr=" + a.sampleRate() + " ms=" + a.durationMs()
                }
                app.get("/copy") {
                    var a = Audio.openWav("assets/tone.wav")
                    a.saveWav("assets/tone-copy.wav")
                    return "copied"
                }
                app.listen(PORT)
            }
            """;

    @Test
    void audioWavInfoAndCopy() throws IOException {
        // 0.5s a 16kHz mono
        Files.write(appDir.resolve("assets/tone.wav"), makeWav(16000, 1, 8000));
        int port = startServer(appDir, AUDIO_APP);
        String r = request(port, "GET /info HTTP/1.1\r\nHost: x\r\n\r\n");
        String body = r.substring(r.indexOf("\r\n\r\n") + 4).trim();
        assertEquals("sr=16000 ms=500", body, "leitura do WAV: " + body);
        String r2 = request(port, "GET /copy HTTP/1.1\r\nHost: x\r\n\r\n");
        assertEquals("copied", r2.substring(r2.indexOf("\r\n\r\n") + 4).trim());
        byte[] copy = Files.readAllBytes(appDir.resolve("assets/tone-copy.wav"));
        byte[] orig = Files.readAllBytes(appDir.resolve("assets/tone.wav"));
        assertArrayEquals(orig, copy, "saveWav grava o mesmo PCM");
    }

    // ── Video: metadados do container (sem decodificação de frames) ───


    @Test
    void videoDurationSkipsExtendedSizeBoxBeforeMoov() throws IOException {
        byte[] mp4 = makeMp4WithExtendedSizeBoxBeforeMoov();
        Files.write(appDir.resolve("assets/clip.mp4"), mp4);
        int port = startServer(appDir, VIDEO_APP);
        String r = request(port, "GET /info HTTP/1.1\r\nHost: x\r\n\r\n");
        String body = r.substring(r.indexOf("\r\n\r\n") + 4).trim();
        assertEquals("fmt=mp4 size=" + mp4.length + " ms=3000 path=assets/clip.mp4",
                body, "#623: extended-size box before moov must not blind durationMs(): " + body);
    }

    private static final String VIDEO_APP = """
            main() {
                var app = web.app()
                app.get("/info") {
                    var v = Video.open("assets/clip.mp4")
                    return "fmt=" + v.format() + " size=" + v.size()
                           + " ms=" + v.durationMs() + " path=" + v.path()
                }
                app.serveDir("/media", "assets")
                app.listen(PORT)
            }
            """;

    @Test
    void videoMetadataFromMp4Container() throws IOException {
        byte[] mp4 = makeMp4();
        Files.write(appDir.resolve("assets/clip.mp4"), mp4);
        int port = startServer(appDir, VIDEO_APP);
        String r = request(port, "GET /info HTTP/1.1\r\nHost: x\r\n\r\n");
        String body = r.substring(r.indexOf("\r\n\r\n") + 4).trim();
        assertEquals("fmt=mp4 size=" + mp4.length + " ms=3000 path=assets/clip.mp4",
                body, "metadados do container: " + body);
    }

    // ── Range requests (206/416): vídeo navegável no browser ─────────

    @Test
    void servesPartialContentWithContentRange() throws IOException {
        int port = startServer(appDir, SERVE_APP);
        byte[] full = Files.readAllBytes(appDir.resolve("assets/style.css"));
        byte[] body = rawBytes(port,
                "GET /img/style.css HTTP/1.1\r\nHost: x\r\nRange: bytes=10-19\r\n\r\n");
        assertEquals(10, body.length, "10 bytes do intervalo");
        assertArrayEquals(Arrays.copyOfRange(full, 10, 20), body, "bytes 10..19");
        String r = request(port, "GET /img/style.css HTTP/1.1\r\nHost: x\r\nRange: bytes=10-19\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 206"), "206 Partial Content: " + r.split("\r\n", 2)[0]);
        assertTrue(r.contains("Content-Range: bytes 10-19/" + full.length),
                "Content-Range correto: " + headersOf(r));
        assertTrue(r.contains("Accept-Ranges: bytes"), "Accept-Ranges");
    }

    @Test
    void unsatisfiableRangeIs416() throws IOException {
        int port = startServer(appDir, SERVE_APP);
        String r = request(port, "GET /img/style.css HTTP/1.1\r\nHost: x\r\nRange: bytes=99999-\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 416"), "416: " + r.split("\r\n", 2)[0]);
        assertTrue(r.contains("Content-Range: bytes */"), "Content-Range */: " + headersOf(r));
    }

    @Test
    void fullResponseAdvertisesRangeSupport() throws IOException {
        int port = startServer(appDir, SERVE_APP);
        String r = request(port, "GET /img/style.css HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200"));
        assertTrue(r.contains("Accept-Ranges: bytes"), "Accept-Ranges no 200: " + headersOf(r));
        assertFalse(r.contains("Content-Range:"), "200 completo não tem Content-Range");
    }

    // Q0 regression (17/09): docs (backend-parity + stdlib-web, EN+PT) promise
    // WEB005 for `app.serveDir` on non-JVM targets, but the web gate's
    // catch-all emitted WEB001 — the documented code was NEVER produced
    // (phantom WEB005; the only mapping lived in the dead `KofMedia.appServeDir`).
    // The fix wires `kof_web_serve_dir` → WEB005 in `KofWeb.gapCode`.
    @Test
    void serveDirOnNonJvmEmitsWeb005NotWeb001(@TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("Serve.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.serveDir("/img", "assets")
                    app.listen(8080)
                }
                """);
        for (Target t : new Target[]{Target.JS, Target.NATIVE,
                Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(source, tmp.resolve("serve-" + t), t);
            assertFalse(r.success(), t + " serveDir deve ser gap honesto (JVM-only)");
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("WEB005"),
                    t + " deve reportar WEB005 (contrato das docs): " + diags);
            assertFalse(diags.contains("WEB001"),
                    t + " WEB001 seria o catch-all errado: " + diags);
        }
        // Controle: no JVM o serveDir é real (não é gap) — o gate não vaza.
        CompilationResult jvm = driver.compile(source, tmp.resolve("serve-jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM serveDir deve compilar: " + jvm.diagnostics().getDiagnostics());
    }

    @Test
    void micWithoutHardwareGivesClearGap() throws IOException {
        String kofSource = """
                main() {
                    var app = web.app()
                    app.get("/mic") {
                        var m = Mic.record(1)
                        return "ok"
                    }
                    app.listen(PORT)
                }
                """;
        int port = startServer(appDir, kofSource);
        String r = request(port, "GET /mic HTTP/1.1\r\nHost: x\r\n\r\n");
        // sem hardware de áudio no CI: o erro é claro (handler 500 com o
        // marcador do gap), nunca crash silencioso. As duas formulações do
        // gap (linha não suportada / LineUnavailable) carregam MEDIA003.
        assertTrue(r.contains("MEDIA003") || r.startsWith("HTTP/1.1 200"),
                "gap honesto (MEDIA003) ou sucesso se houver hardware: " + r);
    }
}
