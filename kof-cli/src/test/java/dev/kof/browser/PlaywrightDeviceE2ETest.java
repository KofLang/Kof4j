package dev.kof.browser;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase-8 (testing-platform plan): MOBILE EMULATION — a página é lançada num
 * contexto com viewport/touch emulados e o DOM PROVA a emulação (o que o
 * window vê, não o que o launcher pediu). O default (sem emulação) é a face
 * negativa: 1280x800 e sem touch.
 */
class PlaywrightDeviceE2ETest {

    /** O fixture renderiza o viewport REAL (o que o window vê) e o flag de
     *  touch; uma media query estreita esconde o bloco desktop e mostra o
     *  mobile — a face responsiva mede o CSS reagindo à emulação. */
    private static String fixture(Path dir) throws Exception {
        Path page = dir.resolve("device.html");
        Files.writeString(page, """
                <html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                  #desktop { display: block; }
                  #mobile  { display: none;  }
                  @media (max-width: 500px) {
                    #desktop { display: none; }
                    #mobile  { display: block; }
                  }
                </style></head><body>
                <div id="vp">pending</div>
                <div id="touch">pending</div>
                <div id="desktop">desktop</div>
                <div id="mobile">mobile</div>
                <script>
                  document.getElementById('vp').textContent
                      = window.innerWidth + 'x' + window.innerHeight;
                  document.getElementById('touch').textContent
                      = ('ontouchstart' in window) ? 'sim' : 'nao';
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
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/device.html";
    }

    private static void assumeDeviceChromium() {
        String v = new PlaywrightLibProvider().probe("chromium");
        assumeTrue(v != null, "sem o chromium pinado pelo driver do Playwright");
    }

    @Test
    void theEmulatedViewportIsWhatTheWindowSees(@TempDir Path dir) throws Exception {
        assumeDeviceChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir);
        assertEquals("390x844",
                p.deviceAssert("chromium", url, 390, 844, true, "css=#vp", "text").strip());
    }

    @Test
    void theDefaultContextIsNotMobile(@TempDir Path dir) throws Exception {
        assumeDeviceChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir);
        // o default REAL do chromium headless (medido): 1280x720
        assertEquals("1280x720",
                p.assertOn(url, "css=#vp", "text").strip());
    }

    @Test
    void theTouchFlagReachesThePage(@TempDir Path dir) throws Exception {
        assumeDeviceChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir);
        assertEquals("sim",
                p.deviceAssert("chromium", url, 390, 844, true, "css=#touch", "text").strip());
        assertEquals("nao",
                p.deviceAssert("chromium", url, 390, 844, false, "css=#touch", "text").strip());
    }

    @Test
    void theMediaQueryFlipsWithTheViewport(@TempDir Path dir) throws Exception {
        assumeDeviceChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir);
        // sob o viewport estreito o bloco desktop some e o mobile aparece
        assertEquals("false",
                p.deviceAssert("chromium", url, 390, 844, true, "css=#desktop", "visible"));
        assertEquals("true",
                p.deviceAssert("chromium", url, 390, 844, true, "css=#mobile", "visible"));
        // no default o desktop está lá
        assertEquals("true", p.assertOn(url, "css=#desktop", "visible"));
    }
}
