package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.TimeUnit;

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
class KofMediaE2ETest {

    private static final String JAVA_BIN = java.nio.file.Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();
    private Process serverProcess;

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

    @AfterEach
    void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
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
        for (int attempt = 0; attempt < 40; attempt++) {
            if (!serverProcess.isAlive()) {
                String out = new String(serverProcess.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
                throw new IOException("server exited early: " + out);
            }
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                return port;
            } catch (IOException e) {
                try { Thread.sleep(100); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        }
        throw new IOException("server did not start listening");
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

    private static byte[] makeWav(int sampleRate, int channels, int framesOfSine) {
        byte[] pcm = new byte[framesOfSine * channels * 2];
        for (int i = 0; i < framesOfSine; i++) {
            int sample = (int) (Math.sin(i / 200.0) * 10000);
            for (int ch = 0; ch < channels; ch++) {
                int off = (i * channels + ch) * 2;
                pcm[off] = (byte) (sample & 0xFF);
                pcm[off + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }
        byte[] out = new byte[44 + pcm.length];
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        int dataSize = pcm.length;
        out[4] = (byte) (36 + dataSize); out[5] = (byte) ((36 + dataSize) >> 8);
        out[6] = (byte) ((36 + dataSize) >> 16); out[7] = (byte) ((36 + dataSize) >> 24);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        out[16] = 16;
        out[20] = 1; out[21] = 0;                       // PCM (0x0001 little-endian)
        out[22] = (byte) channels;
        out[24] = (byte) (sampleRate & 0xFF); out[25] = (byte) ((sampleRate >> 8) & 0xFF);
        out[26] = (byte) ((sampleRate >> 16) & 0xFF); out[27] = (byte) ((sampleRate >> 24) & 0xFF);
        int byteRate = sampleRate * channels * 2;
        out[28] = (byte) (byteRate & 0xFF); out[29] = (byte) ((byteRate >> 8) & 0xFF);
        out[30] = (byte) ((byteRate >> 16) & 0xFF); out[31] = (byte) ((byteRate >> 24) & 0xFF);
        out[32] = (byte) (channels * 2);
        out[34] = 16;
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        out[40] = (byte) (dataSize & 0xFF); out[41] = (byte) ((dataSize >> 8) & 0xFF);
        out[42] = (byte) ((dataSize >> 16) & 0xFF); out[43] = (byte) ((dataSize >> 24) & 0xFF);
        System.arraycopy(pcm, 0, out, 44, pcm.length);
        return out;
    }

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

    private static byte[] mp4Box(String type, byte[] payload) {
        byte[] out = new byte[8 + payload.length];
        out[0] = (byte) (out.length >>> 24);
        out[1] = (byte) (out.length >>> 16);
        out[2] = (byte) (out.length >>> 8);
        out[3] = (byte) out.length;
        System.arraycopy(type.getBytes(StandardCharsets.ISO_8859_1), 0, out, 4, 4);
        System.arraycopy(payload, 0, out, 8, payload.length);
        return out;
    }

    /** MP4 mínimo com moov/mvhd v0: timescale=1000, duration=3000 → 3000ms. */
    private static byte[] makeMp4() {
        byte[] mvhdPayload = new byte[100];
        mvhdPayload[0] = 0;                              // version 0
        // timescale at payload[12..15] = 1000 (0x03E8)
        mvhdPayload[12] = 0; mvhdPayload[13] = 0;
        mvhdPayload[14] = (byte) 0x03; mvhdPayload[15] = (byte) 0xE8;
        // duration at payload[16..19] = 3000 (0x0BB8)
        mvhdPayload[16] = 0; mvhdPayload[17] = 0;
        mvhdPayload[18] = (byte) 0x0B; mvhdPayload[19] = (byte) 0xB8;
        byte[] mvhd = mp4Box("mvhd", mvhdPayload);
        byte[] moov = mp4Box("moov", mvhd);
        byte[] ftypPayload = "isom".getBytes(StandardCharsets.ISO_8859_1);
        byte[] head = mp4Box("ftyp", ftypPayload);
        byte[] out = new byte[head.length + moov.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(moov, 0, out, head.length, moov.length);
        return out;
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
