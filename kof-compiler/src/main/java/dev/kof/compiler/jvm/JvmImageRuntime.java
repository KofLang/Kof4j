package dev.kof.compiler.jvm;

/**
 * Runtime de kof.image — decode de imagens comprimidas via
 * {@code javax.imageio} (JVM-only; outros alvos recebem o gap IMG001).
 *
 * Fragmento do source do KofRuntime gerado. Fica em arquivo próprio pelo
 * mesmo motivo dos demais fragmentos (limite de 65535 bytes por constante
 * do pool). Layout devolvido por {@code kof_image_decode}:
 * {@code [width, height, samples…]}, row-major, RGB (ou RGBA quando a fonte
 * tem alfa) — o mesmo contrato descrito em {@code libs/image/Jpeg.kf}.
 */
public final class JvmImageRuntime {

    private JvmImageRuntime() {}

    static String source() {
        return """
                // ── kof.image — decode via javax.imageio (JVM) ─────────

                public static int[] kof_image_decode(String path) {
                    try {
                        java.nio.file.Path p = java.nio.file.Paths.get(path);
                        if (!java.nio.file.Files.isRegularFile(p)) {
                            throw new RuntimeException("file not found: " + path);
                        }
                        java.awt.image.BufferedImage img =
                                javax.imageio.ImageIO.read(p.toFile());
                        if (img == null) {
                            throw new RuntimeException(
                                    "unsupported image format: " + path);
                        }
                        int w = img.getWidth();
                        int h = img.getHeight();
                        int channels = img.getColorModel().hasAlpha() ? 4 : 3;
                        int[] out = new int[2 + w * h * channels];
                        out[0] = w;
                        out[1] = h;
                        int[] row = new int[w];
                        int idx = 2;
                        for (int y = 0; y < h; y++) {
                            img.getRGB(0, y, w, 1, row, 0, w);
                            for (int x = 0; x < w; x++) {
                                int argb = row[x];
                                out[idx++] = (argb >> 16) & 0xFF;
                                out[idx++] = (argb >> 8) & 0xFF;
                                out[idx++] = argb & 0xFF;
                                if (channels == 4) {
                                    out[idx++] = (argb >> 24) & 0xFF;
                                }
                            }
                        }
                        return out;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(
                                "Image.decode failed: " + e.getMessage(), e);
                    }
                }

                """;
    }
}
