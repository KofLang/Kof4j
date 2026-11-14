package dev.kof.browser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * §8.2 (testing-platform plan, fase 8): a comparação pixel da regressão visual —
 * `baseline → novo screenshot → comparação pixel → relatório de diferença`.
 *
 * <p>Determinístico por construção: Java puro sobre os pixels RGB lidos dos
 * PNGs — as mesmas entradas produzem o mesmo relatório, sempre (nenhuma
 * dependência de browser, de antialiasing ou de ordem de varredura além da
 * linha-a-linha fixa). O contrato §8.2: a comparação visual é uma CAPACIDADE
 * da plataforma de testes, não obrigatória em todo teste; o chamador decide
 * a tolerância por canal (0 = bit a bit).
 *
 * <p>Honestidade (Q7): tamanhos divergentes são mismatch com motivo nomeado —
 * nunca um falso verde por recorte silencioso; a máscara só é escrita quando
 * o chamador pede a face de arquivo.
 */
public final class VisualRegression {

    private VisualRegression() {
    }

    /** O relatório imutável de uma comparação. */
    public record Result(boolean match, int diffPixelCount, double mismatchRatio,
                         int minX, int minY, int maxX, int maxY,
                         BufferedImage diffImage, String reason) {
    }

    /** Compara duas imagens em memória, tolerância por canal RGB (0..255). */
    public static Result compare(BufferedImage a, BufferedImage b, int tolerance) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return new Result(false, 0, 1.0, 0, 0, 0, 0, null,
                    "dimension mismatch: " + a.getWidth() + "x" + a.getHeight()
                            + " vs " + b.getWidth() + "x" + b.getHeight());
        }
        int w = a.getWidth();
        int h = a.getHeight();
        BufferedImage diff = null;
        int count = 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int ca = a.getRGB(x, y);
                int cb = b.getRGB(x, y);
                if (channelExceeds(ca, cb, tolerance)) {
                    if (diff == null) {
                        diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    }
                    diff.setRGB(x, y, 0xFFFFFF);
                    count++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        double total = (double) w * h;
        String reason = count == 0
                ? "match: " + count + " differing pixels (tolerance " + tolerance + ")"
                : "mismatch: " + count + "/" + (int) total + " pixels differ (tolerance "
                        + tolerance + "), extent [" + minX + "," + minY + "]-[" + maxX + "," + maxY + "]";
        return new Result(count == 0, count, count / total,
                count == 0 ? 0 : minX, count == 0 ? 0 : minY,
                count == 0 ? 0 : maxX, count == 0 ? 0 : maxY, diff, reason);
    }

    /** A face de arquivo: lê os dois PNGs, compara e escreve a máscara diff em {@code maskPath}. */
    public static Result compareFiles(Path baseline, Path actual, Path maskPath, int tolerance)
            throws IOException {
        BufferedImage a = ImageIO.read(baseline.toFile());
        BufferedImage b = ImageIO.read(actual.toFile());
        if (a == null || b == null) {
            throw new IOException("not a readable image: "
                    + (a == null ? baseline : actual));
        }
        Result r = compare(a, b, tolerance);
        if (r.diffImage() != null) {
            ImageIO.write(r.diffImage(), "png", maskPath.toFile());
            return new Result(r.match(), r.diffPixelCount(), r.mismatchRatio(),
                    r.minX(), r.minY(), r.maxX(), r.maxY(), r.diffImage(),
                    r.reason() + "; diff mask written to " + maskPath);
        }
        return r;
    }

    /** Um canal (R, G ou B) difere acima da tolerância? O alfa é ignorado —
     *  os screenshots do provider são RGB opacos. */
    private static boolean channelExceeds(int ca, int cb, int tolerance) {
        int dr = Math.abs(((ca >> 16) & 0xFF) - ((cb >> 16) & 0xFF));
        int dg = Math.abs(((ca >> 8) & 0xFF) - ((cb >> 8) & 0xFF));
        int db = Math.abs((ca & 0xFF) - (cb & 0xFF));
        return dr > tolerance || dg > tolerance || db > tolerance;
    }
}
