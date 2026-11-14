package dev.kof.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §8.2 (testing-platform plan, fase 8): o comparador pixel da regressão visual.
 * O motor é PURO (sem browser) — determinístico por construção: as mesmas
 * entradas produzem o mesmo relatório, sempre. A perna chromium fica no teste
 * E2E separado, com o probe honesto (skip ambiental nomeado).
 */
class VisualRegressionTest {

    private static BufferedImage solid(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, rgb);
        return img;
    }

    @Test
    void identicalImagesMatchWithZeroRatioAndNoMaskPixels() throws Exception {
        BufferedImage a = solid(16, 16, 0x336699);
        BufferedImage b = solid(16, 16, 0x336699);
        VisualRegression.Result r = VisualRegression.compare(a, b, 0);
        assertTrue(r.match(), "idênticas casam");
        assertEquals(0.0, r.mismatchRatio(), 1e-9, "razão zero");
        assertEquals(0, r.diffPixelCount(), "nenhum pixel na máscara");
        assertNull(r.diffImage(), "sem diferença, sem máscara");
        assertEquals(0, r.minX());
        assertEquals(0, r.minY());
        assertEquals(0, r.maxX());
        assertEquals(0, r.maxY());
    }

    @Test
    void onePixelDifferenceIsIsolatedInMaskAndReport() {
        BufferedImage a = solid(16, 16, 0x000000);
        BufferedImage b = solid(16, 16, 0x000000);
        b.setRGB(5, 9, 0xFFFFFF);
        VisualRegression.Result r = VisualRegression.compare(a, b, 0);
        assertFalse(r.match(), "um pixel diferente = mismatch");
        assertEquals(1, r.diffPixelCount());
        assertEquals(5, r.minX());
        assertEquals(9, r.minY());
        assertEquals(5, r.maxX());
        assertEquals(9, r.maxY());
        assertEquals(1.0 / (16 * 16), r.mismatchRatio(), 1e-12);
        assertNotNull(r.diffImage());
        assertEquals(0xFFFFFF, r.diffImage().getRGB(5, 9) & 0xFFFFFF, "o pixel marcado na máscara");
        assertEquals(0x000000, r.diffImage().getRGB(6, 6) & 0xFFFFFF, "o resto da máscara fica escuro");
    }

    @Test
    void tolerancePerChannelAbsorbsSmallDeltas() {
        BufferedImage a = solid(16, 16, 0x101010);
        BufferedImage b = solid(16, 16, 0x141410); // ΔR=4, ΔG=4, ΔB=0
        assertTrue(VisualRegression.compare(a, b, 4).match(), "Δ dentro da tolerância casa");
        assertFalse(VisualRegression.compare(a, b, 3).match(), "Δ acima da tolerância é mismatch");
    }

    @Test
    void differentSizesAreAMismatchWithANamedReason() {
        VisualRegression.Result r = VisualRegression.compare(solid(8, 8, 0), solid(9, 8, 0), 0);
        assertFalse(r.match(), "tamanhos diferentes nunca casam");
        assertTrue(r.reason().contains("dimension"), "o motivo nomeia a dimensão: " + r.reason());
        assertEquals(1.0, r.mismatchRatio(), 1e-9, "tamanho divergente = razão 1");
    }

    @Test
    void theReportCarriesThePixelCountAndTheExtentBox() {
        BufferedImage a = solid(16, 16, 0);
        BufferedImage b = solid(16, 16, 0);
        b.setRGB(1, 1, 0xFF0000);
        b.setRGB(10, 14, 0x00FF00);
        VisualRegression.Result r = VisualRegression.compare(a, b, 0);
        assertEquals(2, r.diffPixelCount());
        assertEquals(1, r.minX());
        assertEquals(1, r.minY());
        assertEquals(10, r.maxX());
        assertEquals(14, r.maxY());
        assertTrue(r.reason().contains("2"), "o relatório cita a contagem: " + r.reason());
    }

    @Test
    void theFileFaceWritesTheMaskAndRoundTripsThroughPng(@TempDir Path tmp) throws Exception {
        Path baseline = tmp.resolve("baseline.png");
        Path actual = tmp.resolve("actual.png");
        ImageIO.write(solid(16, 16, 0x0A0B0C), "png", baseline.toFile());
        BufferedImage b = solid(16, 16, 0x0A0B0C);
        b.setRGB(3, 4, 0x112233);
        ImageIO.write(b, "png", actual.toFile());
        Path mask = tmp.resolve("diff.png");
        VisualRegression.Result r = VisualRegression.compareFiles(baseline, actual, mask, 0);
        assertFalse(r.match());
        assertEquals(1, r.diffPixelCount());
        assertTrue(Files.exists(mask), "a máscara foi escrita");
        assertEquals(16, ImageIO.read(mask.toFile()).getWidth(), "a máscara tem o tamanho da imagem");
        assertTrue(r.reason().contains("diff"), "o relatório cita a máscara: " + r.reason());
    }

    /** §8.2 o laço REAL no chromium: baseline → screenshot → match; a página
     *  mutada → mismatch + máscara. Skip ambiental nomeado (sem Chrome no host). */
    @Test
    void theRealLoopOnChromeBaselineThenMismatch(@TempDir Path tmp) throws Exception {
        ChromeHeadlessProvider found = ChromeHeadlessProvider.find();
        assumeTrue(found != null, "sem Chrome no host — o laço visual pula com o motivo");
        // página determinística: fundo sólido, sem texto (o raster de fonte varia
        // entre versões do chrome; a cor sólida não).
        String page = "data:text/html,<body style=%22background:%23336699;margin:0%22>";
        Path baseline = tmp.resolve("baseline.png");
        found.screenshot(page, baseline.toString());
        Path shot2 = tmp.resolve("again.png");
        found.screenshot(page, shot2.toString());
        Path mask = tmp.resolve("same.png");
        VisualRegression.Result same = VisualRegression.compareFiles(baseline, shot2, mask, 0);
        assertTrue(same.match(), "a MESMA página casa bit a bit: " + same.reason());
        assertTrue(Files.notExists(mask), "sem diferença, a máscara não é escrita");
        // a página MUTADA: outra cor de fundo
        String mutated = "data:text/html,<body style=%22background:%23993366;margin:0%22>";
        Path shot3 = tmp.resolve("mutated.png");
        found.screenshot(mutated, shot3.toString());
        Path mask2 = tmp.resolve("diff.png");
        VisualRegression.Result diff = VisualRegression.compareFiles(baseline, shot3, mask2, 0);
        assertFalse(diff.match(), "a página mutada é mismatch");
        assertTrue(diff.mismatchRatio() > 0.5, "o fundo trocado diverge na maioria: " + diff.mismatchRatio());
        assertTrue(Files.exists(mask2), "a máscara da diferença foi escrita");
        BufferedImage m = ImageIO.read(mask2.toFile());
        assertEquals(1280, m.getWidth(), "a máscara tem o tamanho do screenshot");
        assertEquals(0xFFFFFF, m.getRGB(640, 400) & 0xFFFFFF, "o centro divergente marcado");
    }
}
