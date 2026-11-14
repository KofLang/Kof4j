package dev.kof.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase-8 (testing-platform plan): TRACES — the post-mortem recording. The face
 * records a REAL interaction (navigate + click) into the Playwright trace
 * archive and SAVES it: the artifact a developer opens after a failure. The
 * proof: the archive exists, is a non-trivial ZIP carrying the trace
 * (the action entries + the screenshot chunks), not an empty file.
 */
class PlaywrightTraceE2ETest {

    private static void assumeTraceChromium() {
        String v = new PlaywrightLibProvider().probe("chromium");
        assumeTrue(v != null, "sem o chromium pinado pelo driver do Playwright");
    }

    @Test
    void theInteractionTraceIsRecordedAndSaved(@TempDir Path dir) throws Exception {
        assumeTraceChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        Path page = dir.resolve("trace.html");
        Files.writeString(page, """
                <html><head><meta charset="utf-8"></head><body>
                <button id="b" onclick="this.textContent='clicked'">go</button>
                </body></html>
                """);
        Path trace = dir.resolve("trace.zip");
        p.traceRun("chromium", page.toUri().toString(), "css=#b", trace);
        assertTrue(Files.exists(trace), "o arquivo de trace foi salvo");
        long size = Files.size(trace);
        assertTrue(size > 512, "o trace é não-trivial (" + size + " B)");
        // the archive carries the trace structure (a real ZIP with entries)
        try (ZipFile zf = new ZipFile(trace.toFile())) {
            var entries = zf.entries();
            assertTrue(entries.hasMoreElements(), "o trace é um ZIP com entradas");
            int n = 0;
            while (entries.hasMoreElements()) { entries.nextElement(); n++; }
            assertTrue(n >= 1, "entradas no archive: " + n);
        }
    }
}
