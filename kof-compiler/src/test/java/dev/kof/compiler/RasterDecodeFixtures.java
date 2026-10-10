package dev.kof.compiler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Fixtures (hand-built bytes + ImageIO-generated PNG/GIF/JPEG) and Kof probes
 * for {@link RasterDecodeE2ETest}; extracted to keep the test class under the
 * oversized threshold.
 */
final class RasterDecodeFixtures {

    private RasterDecodeFixtures() {
    }

    static String rasterProbe(Path dir) {
        String base = path(dir);
        return """
            import image.Raster

            String dump(Raster r) {
                var out = r.format + ":" + r.width + "x" + r.height + " ch=" + r.channels + "\\npx="
                var i = 0
                while (i < r.samples.length) {
                    if (i > 0) {
                        out = out + ","
                    }
                    out = out + r.samples[i]
                    i = i + 1
                }
                return out
            }

            main() {
                var base = "%s"
                println(dump(decodeRaster(base + "/rgb.ppm")))
                println(dump(decodeRaster(base + "/gray.pgm")))
                println(dump(decodeRaster(base + "/rgba.ff")))
                var rgb = decodeRaster(base + "/rgb.ppm")
                println(dump(cropRaster(rgb, 1, 0, 1, 2)))
                println(dump(resizeNearest(rgb, 4, 4)))
                println(dump(flipHorizontal(rgb)))
                println(dump(flipVertical(rgb)))
                println(dump(rotate90(rgb)))
                println(dump(decodeRaster(base + "/t.bmp")))
                println(dump(grayscale(rgb)))
                println(dump(threshold(rgb, 5)))
                println(dump(boxBlur(decodeRaster(base + "/blur.pgm"))))
                println(dump(decodeRaster(base + "/q2.qoi")))
                println(dump(decodeRaster(base + "/q3.qoi")))
            }
            """.formatted(base);
    }

    static String pngProbe(Path dir) {
        String base = path(dir);
        return """
            import image.Raster

            String dump(Raster r) {
                var out = r.format + ":" + r.width + "x" + r.height + " ch=" + r.channels + "\\npx="
                var i = 0
                while (i < r.samples.length) {
                    if (i > 0) {
                        out = out + ","
                    }
                    out = out + r.samples[i]
                    i = i + 1
                }
                return out
            }

            main() {
                var base = "%s"
                println(dump(decodeRaster(base + "/p_rgb.png")))
                println(dump(decodeRaster(base + "/p_rgba.png")))
            }
            """.formatted(base);
    }

    static String gifProbe(Path dir) {
        String base = path(dir);
        return """
            import image.Raster

            String dump(Raster r) {
                var out = r.format + ":" + r.width + "x" + r.height + " ch=" + r.channels + "\\npx="
                var i = 0
                while (i < r.samples.length) {
                    if (i > 0) {
                        out = out + ","
                    }
                    out = out + r.samples[i]
                    i = i + 1
                }
                return out
            }

            main() {
                println(dump(decodeRaster("%s/p.gif")))
            }
            """.formatted(base);
    }

    static String errorProbe(Path src) {
        return """
            import image.Raster

            main() {
                try {
                    var r = decodeRaster("%s")
                    println(r.format)
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(path(src));
    }

    static String path(Path p) {
        return p.toString().replace('\\', '/');
    }

    static byte[] decodeHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    static String vp8lProbe(Path dir) {
        String base = path(dir);
        return """
            import image.Raster

            String dump(Raster r) {
                var out = r.format + ":" + r.width + "x" + r.height + " ch=" + r.channels + "\\npx="
                var i = 0
                while (i < r.samples.length) {
                    if (i > 0) {
                        out = out + ","
                    }
                    out = out + r.samples[i]
                    i = i + 1
                }
                return out
            }

            main() {
                println(dump(decodeRaster("%s/v.webp")))
                println(dump(decodeRaster("%s/vsub.webp")))
                println(dump(decodeRaster("%s/vnorm.webp")))
                println(dump(decodeRaster("%s/vcache.webp")))
                println(dump(decodeRaster("%s/vpred.webp")))
                println(dump(decodeRaster("%s/vindex.webp")))
                println(dump(decodeRaster("%s/vmeta.webp")))
            }
            """.formatted(base, base, base, base, base, base, base);
    }

    static String jpegProbe(Path dir) {
        String base = path(dir);
        return """
            import image.Jpeg
            import image.Raster

            String dump(Raster r) {
                var out = r.format + ":" + r.width + "x" + r.height + " ch=" + r.channels + "\\npx="
                var i = 0
                while (i < r.samples.length) {
                    if (i > 0) {
                        out = out + ","
                    }
                    out = out + r.samples[i]
                    i = i + 1
                }
                return out
            }

            main() {
                println(dump(decodeJpegRaster("%s/u.jpg")))
            }
            """.formatted(base);
    }

    static String jpegExpected(Path file) throws Exception {
        BufferedImage img = ImageIO.read(file.toFile());
        int w = img.getWidth();
        int h = img.getHeight();
        int channels = img.getColorModel().hasAlpha() ? 4 : 3;
        StringBuilder sb = new StringBuilder("JPEG:").append(w).append("x").append(h)
                .append(" ch=").append(channels).append("\npx=");
        int[] row = new int[w];
        boolean first = true;
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                for (int c = 0; c < channels; c++) {
                    int v = switch (c) {
                        case 0 -> (argb >> 16) & 0xFF;
                        case 1 -> (argb >> 8) & 0xFF;
                        case 2 -> argb & 0xFF;
                        default -> (argb >> 24) & 0xFF;
                    };
                    if (!first) sb.append(',');
                    sb.append(v);
                    first = false;
                }
            }
        }
        return sb.toString();
    }

    static Path rasterFixtures(Path dir) throws Exception {        Files.createDirectories(dir);
        Files.write(dir.resolve("rgb.ppm"), rasterPpm());
        Files.write(dir.resolve("gray.pgm"), rasterPgm());
        Files.write(dir.resolve("rgba.ff"), rasterFarbfeld());
        Files.write(dir.resolve("t.bmp"), rasterBmp24());
        Files.write(dir.resolve("blur.pgm"), rasterBlur());
        Files.write(dir.resolve("q2.qoi"), qoiRgba());
        Files.write(dir.resolve("q3.qoi"), qoiRun());
        writeImage(dir.resolve("p_rgb.png"), "png", BufferedImage.TYPE_INT_RGB, new int[]{
                0x0A141E, 0x28323C, 0x46505A, 0x646E78});
        writeImage(dir.resolve("p_rgba.png"), "png", BufferedImage.TYPE_INT_ARGB, new int[]{
                0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10});
        writeImage(dir.resolve("p.gif"), "gif", BufferedImage.TYPE_INT_RGB, new int[]{
                0x0A141E, 0x28323C, 0x46505A, 0x646E78});
        writeImage(dir.resolve("u.jpg"), "jpg", BufferedImage.TYPE_INT_RGB, new int[]{
                0x0A141E, 0x28323C, 0x46505A, 0x646E78});
        Files.write(dir.resolve("v.webp"), decodeHex(
                "5249464619000000574542505650384c0d0000002f01400000284515ead1ff0200"));
        Files.write(dir.resolve("vsub.webp"), decodeHex(
                "5249464619000000574542505650384c0d0000002f0140000045296a5f85fe1700"));
        Files.write(dir.resolve("vnorm.webp"), decodeHex(
                "5249464622000000574542505650384c160000002f034000001008241293ec4f17c51550eec1d3ff0c0f"));
        Files.write(dir.resolve("vcache.webp"), decodeHex(
                "5249464612010000574542505650384c060100002f07c0010016a7110020ca3b1635babb445d81630297"
                        + "48768f2e159a0fc00a1ab98fd0dd9d517c01b2d3000008a9dd1b1bb059d1b66d0da06e2df1d18a88b68"
                        + "d01b4c335271200a0c9daaa6f69c90d7777f7ee6e1fe00124d744e305b4d168f007881c0f7072ff83f6"
                        + "1ee595b16ddbc42a81ec46ed56b5293fe734f63319ec470c20908857cabe6065ab2005f1e8b403d6943"
                        + "fd1d7d9d80215aa4e7cfc556272acc3257b7ced2fb319d6c92e3ff7c48cb3fa2142c15da7bde2bfedbc9"
                        + "9515527a29ce6d06b1ce6735098fbd5e36b8fc2013fb1eb71da56f3a1765599dd45557a79764b4f43f61e"
                        + "5b6503f2bfe4018ca790a5f8f8ce00a1d43f762205fc4802202a0d05418811"));
        Files.write(dir.resolve("vpred.webp"), decodeHex(
                "524946462a000000574542505650384c1e0000002f07c00100898ce87f2c220adeff309049dbb4fe6df79b81"
                        + "91191370da19"));
        Files.write(dir.resolve("vindex.webp"), decodeHex(
                "5249464652000000574542505650384c460000002f07c001003f201048da1f7a8df9171014f93fdafc07b241"
                        + "24b001369124ab9674121ec7fc62fa024ec2e71805f08fe87f0000b4dcffe94807995f8788a89f512dcdd29c"
                        + "510c"));
        Files.write(dir.resolve("vmeta.webp"), decodeHex(
                "5249464628000000574542505650384c1c0000002f07c0010084030414a000fd2f0057a114f5e87f018a5cb2"
                        + "caf4bf00"));
        Files.write(dir.resolve("vlarge.webp"), decodeHex(
                "5249464652000000574542505650384c450000002f9fc01d00092049c0ffe38d11fd4f550b2141c2ffe546631c"
                        + "02bcff49728b414ddb068c5afed87b4d29041000058d82b40d58d8ee441e5f1da9bdf0cefff93fffe77f040000"));
        return dir;
    }

    static String largeWebpProbe(Path dir) {
        String base = path(dir);
        return """
            import image.Raster

            main() {
                var r = decodeRaster("%s/vlarge.webp")
                println(r.format + ":" + r.width + "x" + r.height + " ch=" + r.channels)
                var last = (r.width * r.height - 1) * r.channels
                println("first=" + r.samples[0] + "," + r.samples[1] + "," + r.samples[2]
                    + " last=" + r.samples[last] + "," + r.samples[last + 1] + "," + r.samples[last + 2])
            }
            """.formatted(base);
    }

    static String encodeProbe(Path dir) {
        String base = path(dir);
        return """
            import image.Encode
            import image.Raster

            String dump(Raster r) {
                var out = r.format + ":" + r.width + "x" + r.height + " ch=" + r.channels + "\\npx="
                var i = 0
                while (i < r.samples.length) {
                    if (i > 0) {
                        out = out + ","
                    }
                    out = out + r.samples[i]
                    i = i + 1
                }
                return out
            }

            main() {
                var rgb = decodeRaster("%s/rgb.ppm")
                var rgba = decodeRaster("%s/rgba.ff")
                writeRaster("%s/e.ppm", rgb, "PPM")
                writeRaster("%s/e.pgm", rgb, "PGM")
                writeRaster("%s/e.bmp", rgb, "BMP")
                writeRaster("%s/e.ff", rgba, "farbfeld")
                writeRaster("%s/e.qoi", rgba, "QOI")
                writeRaster("%s/e3.qoi", rgb, "QOI")
                println(dump(decodeRaster("%s/e.ppm")))
                println(dump(decodeRaster("%s/e.pgm")))
                println(dump(decodeRaster("%s/e.bmp")))
                println(dump(decodeRaster("%s/e.ff")))
                println(dump(decodeRaster("%s/e.qoi")))
                println(dump(decodeRaster("%s/e3.qoi")))
            }
            """.formatted(base, base, base, base, base, base, base, base, base, base, base, base, base, base);
    }


    static byte[] rasterPpm() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("P6\n2 2\n255\n".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
        return out.toByteArray();
    }

    static void writeLargePnm6(Path src, int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("P6\n" + w + " " + h + "\n255\n").getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[w * h * 3]);
        Files.write(src, out.toByteArray());
    }

    static byte[] rasterPgm() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("P5\n3 1\n255\n".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{10, 20, 30});
        return out.toByteArray();
    }

    static byte[] rasterFarbfeld() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("farbfeld".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{0, 0, 0, 2, 0, 0, 0, 2});
        int[] vals = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160};
        for (int v : vals) {
            out.write(v & 0xFF);
            out.write(v & 0xFF);
        }
        return out.toByteArray();
    }

    static byte[] rasterBlur() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("P5\n3 3\n255\n".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{0, 0, 0, 0, 9, 0, 0, 0, 0});
        return out.toByteArray();
    }

    static byte[] rasterBmp24() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // BITMAPFILEHEADER (14) + BITMAPINFOHEADER (40) = 54-byte header.
        out.write("BM".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(70));
        out.write(le32(0));
        out.write(le32(54));
        out.write(le32(40));
        out.write(le32(2));
        out.write(le32(2));
        out.write(new byte[]{1, 0});
        out.write(new byte[]{24, 0});
        out.write(le32(0));
        out.write(le32(16));
        out.write(le32(0));
        out.write(le32(0));
        out.write(le32(0));
        out.write(le32(0));
        // bottom-up: row0 = bottom (C=(70,80,90), D=(100,110,120)) stored BGR
        out.write(new byte[]{(byte) 90, (byte) 80, (byte) 70, (byte) 120, (byte) 110, (byte) 100, 0, 0});
        // row1 = top (A=(10,20,30), B=(40,50,60))
        out.write(new byte[]{30, 20, 10, 60, 50, 40, 0, 0});
        return out.toByteArray();
    }

    static byte[] qoiRgba() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("qoif".getBytes(StandardCharsets.US_ASCII));
        out.write(be32(2));
        out.write(be32(2));
        out.write(new byte[]{4, 0});
        out.write(new byte[]{(byte) 0xFF, 1, 2, 3, 4});
        out.write(new byte[]{(byte) 0xFF, 5, 6, 7, 8});
        out.write(new byte[]{(byte) 0xFF, 9, 10, 11, 12});
        out.write(new byte[]{(byte) 0xFF, 13, 14, 15, 16});
        out.write(new byte[8]);
        return out.toByteArray();
    }

    static byte[] qoiRun() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("qoif".getBytes(StandardCharsets.US_ASCII));
        out.write(be32(1));
        out.write(be32(3));
        out.write(new byte[]{4, 0});
        out.write(new byte[]{(byte) 0xFF, 10, 20, 30, (byte) 255});
        out.write(new byte[]{(byte) 0xC2});
        out.write(new byte[8]);
        return out.toByteArray();
    }

    static void writeImage(Path file, String format, int type, int[] argb) throws Exception {
        BufferedImage img = new BufferedImage(2, 2, type);
        for (int i = 0; i < 4; i++) {
            img.setRGB(i % 2, i / 2, argb[i]);
        }
        ImageIO.write(img, format, file.toFile());
    }

    static byte[] be32(int v) {
        return new byte[]{(byte) ((v >> 24) & 0xFF), (byte) ((v >> 16) & 0xFF),
                (byte) ((v >> 8) & 0xFF), (byte) (v & 0xFF)};
    }

    static byte[] le32(int v) {
        return new byte[]{(byte) (v & 0xFF), (byte) ((v >> 8) & 0xFF),
                (byte) ((v >> 16) & 0xFF), (byte) ((v >> 24) & 0xFF)};
    }

}
