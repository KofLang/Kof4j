package dev.kof.browser;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §fase-8 (testing-platform plan): NETWORK MOCKING — a página busca um
 * endpoint e o provider serve um STUB via `page.route` + `Route.fulfill`.
 * A rede real NUNCA é tocada: a interceptação é medida pelo desfecho no DOM
 * (o elemento renderiza os campos do JSON mockado). A face negativa prova o
 * corte: sem o mock, o fetch de file:// falha e o elemento fica vazio.
 */
class PlaywrightNetworkMockE2ETest {

    /** A página busca `./api/data.json` no load e imprime os campos no #out;
     *  qualquer falha de fetch cai no ramo do ERRO (o texto do #out nomeia).
     *  Servida por HTTP LOCAL: o servidor NÃO tem `/api/*` (404) — então o
     *  stub renderizado prova que a ROTA interceptou antes da rede. */
    private static String fixture(Path dir) throws Exception {
        Path page = dir.resolve("mock.html");
        Files.writeString(page, """
                <html><head><meta charset="utf-8"></head><body>
                <div id="out">pending</div>
                <script>
                  fetch('./api/data.json')
                    .then(r => {
                      if (!r.ok) { throw new Error('status ' + r.status); }
                      return r.json();
                    })
                    .then(d => { document.getElementById('out').textContent
                                       = d.name + '=' + d.value; })
                    .catch(e => { document.getElementById('out').textContent
                                       = 'erro: ' + e.message; });
                </script>
                </body></html>
                """);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] html = Files.readAllBytes(page);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestURI().getPath().endsWith(".html")
                    ? html : "no api here".getBytes();
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().endsWith(".html")
                    ? 200 : 404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mock.html";
    }

    private static void assumeMockChromium() {
        String v = new PlaywrightLibProvider().probe("chromium");
        assumeTrue(v != null, "sem o chromium pinado pelo driver do Playwright");
    }

    @Test
    void theNegativeFaceWithoutMockTheFetchFails(@TempDir Path dir) throws Exception {
        assumeMockChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir);
        // o servidor não tem /api/* — 404 dispara o ramo do erro; a medida
        // espera a rede assentar antes de ler (fetch é assíncrono)
        assertEquals("erro: status 404",
                p.settleAssert("chromium", url, "css=#out", "text").strip());
    }

    @Test
    void theMockServesTheStubAndTheDomRendersIt(@TempDir Path dir) throws Exception {
        assumeMockChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir);
        // o stub 200 JSON — a página renderiza os campos do mock
        assertEquals("kof=42",
                p.mockAssert("chromium", url, "**/api/data.json",
                        new PlaywrightLibProvider.Stub(200, "application/json",
                                "{\"name\":\"kof\",\"value\":\"42\"}"),
                        "css=#out", "text").strip());
    }

    @Test
    void theStubStatusDrivesTheErrorBranch(@TempDir Path dir) throws Exception {
        assumeMockChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir);
        // o stub 500 — o `!r.ok` do programa dispara o ramo do erro
        assertEquals("erro: status 500",
                p.mockAssert("chromium", url, "**/api/data.json",
                        new PlaywrightLibProvider.Stub(500, "application/json", "{}"),
                        "css=#out", "text").strip());
    }
}
