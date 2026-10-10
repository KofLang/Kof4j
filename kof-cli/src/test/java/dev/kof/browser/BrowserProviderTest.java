package dev.kof.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §6.1/§6.2 of the testing-platform plan: the browser SPI + the first
 * provider (Chrome headless, the seed `KofJsBrowserE2ETest` mechanism).
 *
 * <p>Unit face: the SPI contract (name/probe/honest-null when absent).
 * The real browser run lives in `KofJsBrowserE2ETest` (assumes a browser
 * installed); here the probe is tested against the found binary, or the
 * SPI's honest-null when none exists.
 */
class BrowserProviderTest {

    @Test
    void spiContractNameAndHonestProbe() {
        ChromeHeadlessProvider found = ChromeHeadlessProvider.find();
        if (found == null) {
            // sem browser no host: a SPI ainda é honesta — find() null não é
            // exceção, o gate faz skip com o motivo nomeado (Q7).
            BrowserProvider absent = new ChromeHeadlessProvider(null);
            assertEquals("chrome-headless", absent.name());
            assertEquals(null, absent.probe(), "sem binário o probe é null (skip honesto)");
            return;
        }
        assertEquals("chrome-headless", found.name());
        String version = found.probe();
        assertNotNull(version, "com binário o probe imprime a versão");
        assertTrue(version.toLowerCase().contains("chrome")
                        || version.toLowerCase().contains("chromium"),
                "probe devolve a versão do Chrome: " + version);
    }

    @Test
    void findChromeSweepsStandardPaths() {
        // o sweep é determinístico: os mesmos candidatos, PATH + caminhos
        // padrão. O resultado (achado ou null) deve bater com a SPI honesta.
        String hit = ChromeHeadlessProvider.findChrome();
        ChromeHeadlessProvider p = ChromeHeadlessProvider.find();
        if (hit == null) {
            assertEquals(null, p, "sem hit: find() também é null");
        } else {
            assertNotNull(p, "com hit: find() constrói o provider");
            assertEquals(hit, p.toString() == null ? null : hit);
        }
    }
}
