package dev.kof.browser;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase-8 (testing-platform plan): UPLOAD/DOWNLOAD — as faces de transferência
 * de arquivos. O upload usa `setInputFiles` (o evento change é síncrono no
 * fixture: `file.name`/`file.size`); o download usa `waitForDownload` +
 * `saveAs` e o teste compara os BYTES reais do arquivo salvo. Servido por
 * HTTP local (downloads de file:// são bloqueados pelo chromium — medido).
 */
class PlaywrightTransferE2ETest {

    private static final byte[] PAYLOAD =
            "kof transfer payload — linha com acento\n".getBytes(StandardCharsets.UTF_8);

    /** Um input file que imprime name+size no #out (leitura SINCRONA do
     *  FileList — sem FileReader), e um link gerador de blob com `download`.
     *  O servidor serve o HTML e NADA mais. */
    private static String fixture(Path dir) throws Exception {
        Path page = dir.resolve("transfer.html");
        Files.writeString(page, """
                <html><head><meta charset="utf-8"></head><body>
                <input id="file" type="file">
                <div id="out">pending</div>
                <a id="dl" href="#" download="kof-dl.txt">baixar</a>
                <script>
                  document.getElementById('file').addEventListener('change', e => {
                    const f = e.target.files[0];
                    document.getElementById('out').textContent
                        = f.name + ' ' + f.size;
                  });
                  document.getElementById('dl').addEventListener('click', e => {
                    e.preventDefault();
                    const blob = new Blob(['kof transfer payload — linha com acento\\n'],
                                          {type: 'text/plain'});
                    const u = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = u; a.download = 'kof-dl.txt'; a.click();
                    URL.revokeObjectURL(u);
                  });
                </script>
                </body></html>
                """);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] html = Files.readAllBytes(page);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length);
            exchange.getResponseBody().write(html);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/transfer.html";
    }

    private static void assumeTransferChromium() {
        String v = new PlaywrightLibProvider().probe("chromium");
        assumeTrue(v != null, "sem o chromium pinado pelo driver do Playwright");
    }

    @Test
    void theUploadCarriesTheRealFileToTheInput(@TempDir Path dir) throws Exception {
        assumeTransferChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir);
        Path file = dir.resolve("enviado.txt");
        Files.write(file, PAYLOAD);
        assertEquals("enviado.txt " + PAYLOAD.length,
                p.uploadAssert("chromium", url, "css=#file", file,
                        "css=#out", "text").strip());
    }

    @Test
    void theDownloadSavesTheRealBytes(@TempDir Path dir) throws Exception {
        assumeTransferChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir);
        Path saved = dir.resolve("recebido.txt");
        p.downloadTo("chromium", url, "css=#dl", saved);
        assertTrue(java.nio.file.Files.exists(saved), "o download foi salvo");
        assertArrayEquals(PAYLOAD, Files.readAllBytes(saved), "os bytes batem");
    }
}
