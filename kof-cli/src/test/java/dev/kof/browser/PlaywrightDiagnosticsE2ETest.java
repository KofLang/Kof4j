package dev.kof.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase-8 (testing-platform plan): ADVANCED DIAGNOSTICS — the console output
 * and the uncaught JS errors captured during the load: the actionable answer
 * to "why did this test fail". One page logs to the console AND throws; BOTH
 * are captured.
 */
class PlaywrightDiagnosticsE2ETest {

    private static void assumeDiagChromium() {
        String v = new PlaywrightLibProvider().probe("chromium");
        assumeTrue(v != null, "sem o chromium pinado pelo driver do Playwright");
    }

    @Test
    void theConsoleAndTheErrorsAreCaptured(@TempDir Path dir) throws Exception {
        assumeDiagChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        Path page = dir.resolve("diag.html");
        Files.writeString(page, """
                <html><head><meta charset="utf-8"></head><body>
                <script>
                  console.log("kof-diag-marker");
                  setTimeout(() => { throw new Error("kof-diag-boom"); }, 30);
                </script>
                </body></html>
                """);
        PageDiagnostics d = p.diagnostics("chromium", page.toUri().toString());
        assertTrue(d.console().stream().anyMatch(l -> l.contains("kof-diag-marker")),
                "o console foi capturado: " + d.console());
        assertTrue(d.errors().stream().anyMatch(l -> l.contains("kof-diag-boom")),
                "o erro JS não capturado foi capturado: " + d.errors());
    }

    @Test
    void aCleanPageHasNoErrors(@TempDir Path dir) throws Exception {
        assumeDiagChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        Path page = dir.resolve("clean.html");
        Files.writeString(page, """
                <html><head><meta charset="utf-8"></head><body>
                <script>console.log("quiet");</script>
                </body></html>
                """);
        PageDiagnostics d = p.diagnostics("chromium", page.toUri().toString());
        assertTrue(d.console().stream().anyMatch(l -> l.contains("quiet")),
                "o console da página limpa: " + d.console());
        assertTrue(d.errors().isEmpty(), "sem erros JS: " + d.errors());
    }
}
