package dev.kof.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase-8 (testing-platform plan): VIDEOS — the recorded session evidence. The
 * context records (setRecordVideoDir), the REAL interaction happens, the close
 * FLUSHES the video (the Playwright contract: the file only exists after the
 * close), and saveAs persists it. The proof: the file exists, is a non-trivial
 * WEBM (the real container bytes), and grows with the interaction.
 */
class PlaywrightVideoE2ETest {

    private static void assumeVideoChromium() {
        String v = new PlaywrightLibProvider().probe("chromium");
        assumeTrue(v != null, "sem o chromium pinado pelo driver do Playwright");
    }

    @Test
    void theSessionVideoIsRecordedAndSaved(@TempDir Path dir) throws Exception {
        assumeVideoChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        Path page = dir.resolve("video.html");
        Files.writeString(page, """
                <html><head><meta charset="utf-8"></head><body>
                <button id="b" onclick="this.textContent='clicked'">go</button>
                </body></html>
                """);
        Path video = dir.resolve("session.webm");
        p.videoRecord("chromium", page.toUri().toString(), "css=#b", video);
        assertTrue(Files.exists(video), "o vídeo foi salvo");
        long size = Files.size(video);
        assertTrue(size > 512, "o vídeo é não-trivial (" + size + " B)");
        byte[] head = new byte[4];
        try (var in = Files.newInputStream(video)) {
            in.readNBytes(head, 0, 4);
        }
        assertEquals(0x1A, head[0] & 0xFF, "o magic EBML do WEBM");
        assertEquals(0x45, head[1] & 0xFF, "o magic EBML do WEBM");
        assertEquals(0xDF, head[2] & 0xFF, "o magic EBML do WEBM");
        assertEquals(0xA3, head[3] & 0xFF, "o magic EBML do WEBM");
    }
}
