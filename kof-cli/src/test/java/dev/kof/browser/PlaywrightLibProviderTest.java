package dev.kof.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §6.2 Playwright provider over the JAVA lib (the fourth chat poll put the
 * lib on kof-cli's pom as a normal dependency). E2E against the
 * Playwright-managed Chromium (installed via `npx playwright install` — the
 * per-project opt-in half of the decision). A served local page gives the
 * real navigation/content/screenshot faces.
 *
 * <p>RED-first: pre-slice there was NO PlaywrightLibProvider — the §6.2
 * surface (launch/navigate/content/screenshot via the lib) did not exist.
 */
class PlaywrightLibProviderTest {

    private static void assumeChromium() {
        Path chrome = PlaywrightLibProvider.findPlaywrightChromium();
        assumeTrue(chrome != null,
                "sem o Chromium do Playwright — rode npx playwright install chromium");
    }

    @Test
    void spiContractAndProbe() {
        assumeChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        assertEquals("playwright", p.name());
        assertNotNull(p.probe(), "com o Chromium instalado o probe não é null");
        assertTrue(p.probe().startsWith("playwright-managed"));
    }

    @Test
    void navigatesAndReturnsRenderedContent(@TempDir Path dir) throws Exception {
        assumeChromium();
        Path page = dir.resolve("page.html");
        Files.writeString(page, "<html><body><h1 id=hdr>kof-test-content</h1></body></html>");
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String content = p.content(page.toUri().toString());
        assertTrue(content.contains("kof-test-content"),
                "o content do Chromium renderizado contém o título:\n" + content);
    }

    @Test
    void screenshotIsRealPngFromManagedChromium(@TempDir Path dir) throws Exception {
        assumeChromium();
        Path page = dir.resolve("page.html");
        Files.writeString(page, "<html><body><h1>shot</h1></body></html>");
        Path shot = dir.resolve("shot.png");
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        p.screenshot(page.toUri().toString(), shot.toString());
        byte[] head = Files.readAllBytes(shot);
        assertTrue(head.length > 8, "screenshot não vazio");
        assertEquals(0x89, head[0] & 0xFF, "PNG magic");
        assertEquals(0x50, head[1] & 0xFF, "PNG magic 'P'");
    }

    // ===== §6.11 cross-browser (a fatia da ordem §11 confirmada, fase 7) —
    // o MESMO suíte sobre os três engines; o probe por kind é o gate honesto
    // (binário pinado ausente = skip com o motivo nomeado, nunca falso verde).

    private static void assumeKind(String kind) {
        String probe = new PlaywrightLibProvider().probe(kind);
        assumeTrue(probe != null, "sem o " + kind + " pinado pelo driver do Playwright"
                + " — rode o install do driver para " + kind);
    }

    @Test
    void unknownKindIsRejectedWithoutLaunching() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> PlaywrightLibProvider.requireSupported("safari"));
        assertTrue(e.getMessage().contains("chromium, firefox, webkit"),
                "a recusa nomeia os kinds suportados: " + e.getMessage());
        for (String kind : PlaywrightLibProvider.KINDS) {
            assertDoesNotThrow(() -> PlaywrightLibProvider.requireSupported(kind),
                    "kind declarado não pode ser rejeitado: " + kind);
        }
    }

    @Test
    void probeByKindReportsTheDriverPinnedBinary() {
        for (String kind : PlaywrightLibProvider.KINDS) {
            String probe = new PlaywrightLibProvider().probe(kind);
            if (probe != null) {
                assertTrue(probe.startsWith("playwright-managed "),
                        "o probe por kind reporta o caminho do driver: " + probe);
                assertTrue(probe.contains(kind),
                        "o probe reporta o engine certo (" + kind + "): " + probe);
            }
            // null é o contrato honesto quando o binário pinado não existe —
            // o que roda de verdade é medido pelo suíte E2E abaixo (Q7).
        }
    }

    /** O MESMO face de content sobre o engine pedido — com o guard de
     *  ambiente: "Host system is missing dependencies" é condição do HOST
     *  (libs de sistema que exigem sudo; precedente da lane nativa: ferramenta
     *  ausente = skip ambiental EXPLÍCITO com o motivo nomeado), nunca um
     *  bug do provider (Q5/Q7 — nem falso verde, nem skip escondido). */
    private static void assertRendersWithHostDepGuard(String kind, Path dir) throws Exception {
        assumeKind(kind);
        Path page = dir.resolve("page-" + kind + ".html");
        Files.writeString(page, "<html><body><h1 id=hdr>kof-" + kind + "</h1></body></html>");
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        try {
            String content = p.content(kind, page.toUri().toString());
            assertTrue(content.contains("kof-" + kind),
                    "o " + kind + " renderizado não contém o título:\n" + content);
        } catch (com.microsoft.playwright.PlaywrightException e) {
            abortOnHostDeps(kind, e);
            throw e;
        }
    }

    private static void abortOnHostDeps(String kind, com.microsoft.playwright.PlaywrightException e) {
        if (String.valueOf(e.getMessage()).contains("missing dependencies")) {
            org.junit.jupiter.api.Assumptions.abort(kind + ": o host não tem as libs de"
                    + " sistema do browser (instale com o install-deps do Playwright — requer sudo)");
        }
    }

    // o chromium já tem o face content provado acima (navigatesAndReturns…);
    // firefox e webkit ganham o método explícito — o resultado por engine fica
    // visível no relatório (verde real ou skip nomeado), nunca agregado.
    @Test
    void contentRendersOnFirefox(@TempDir Path dir) throws Exception {
        assertRendersWithHostDepGuard("firefox", dir);
    }

    @Test
    void contentRendersOnWebkit(@TempDir Path dir) throws Exception {
        assertRendersWithHostDepGuard("webkit", dir);
    }

    @Test
    void sameScreenshotSuiteIsRealPngOnEveryProbedEngine(@TempDir Path dir) throws Exception {
        Path page = dir.resolve("page.html");
        Files.writeString(page, "<html><body><h1>cross-shot</h1></body></html>");
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        for (String kind : PlaywrightLibProvider.KINDS) {
            assumeKind(kind);
            Path shot = dir.resolve("shot-" + kind + ".png");
            try {
                p.screenshot(kind, page.toUri().toString(), shot.toString());
            } catch (com.microsoft.playwright.PlaywrightException e) {
                abortOnHostDeps(kind, e);
                throw e;
            }
            byte[] head = Files.readAllBytes(shot);
            assertTrue(head.length > 8, "screenshot do " + kind + " não vazio");
            assertEquals(0x89, head[0] & 0xFF, "PNG magic do " + kind);
            assertEquals(0x50, head[1] & 0xFF, "PNG magic 'P' do " + kind);
        }
    }
}
