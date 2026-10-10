package dev.kof.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
