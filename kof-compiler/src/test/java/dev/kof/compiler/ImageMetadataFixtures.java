package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hand-built byte headers for {@link ImageMetadataE2ETest} (no codec): the
 * leading bytes of each supported format, so the pure-Kof metadata reader is
 * exercised without shipping binary fixtures. Extracted to keep the test class
 * under the oversized threshold.
 */
final class ImageMetadataFixtures {

    private ImageMetadataFixtures() {
    }

    static Path imageFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("a.png"), png(5, 7));
        Files.write(dir.resolve("b.gif"), gif(3, 4));
        Files.write(dir.resolve("c.bmp"), bmpInfo(9, 11));
        Files.write(dir.resolve("d.bmp"), bmpCore(2, 3));
        Files.write(dir.resolve("e.jpg"), jpeg(20, 10));
        Files.write(dir.resolve("f.webp"), webpVp8(6, 8));
        Files.write(dir.resolve("g.webp"), webpVp8l(13, 9));
        Files.write(dir.resolve("h.webp"), webpVp8x(100, 50));
        Files.write(dir.resolve("i.tif"), tiff(12, 34, true));
        Files.write(dir.resolve("j.tif"), tiff(12, 34, false));
        Files.write(dir.resolve("k.ico"), ico(16, 32));
        Files.write(dir.resolve("l.ppm"), ppm(5, 7));
        Files.write(dir.resolve("m.qoi"), qoi(13, 9));
        Files.write(dir.resolve("n.psd"), psd(40, 30));
        Files.write(dir.resolve("o.dds"), dds(40, 30));
        Files.write(dir.resolve("p.ff"), farbfeld(40, 30));
        Files.write(dir.resolve("q.avif"), avif(40, 30));
        Files.write(dir.resolve("bad.bin"), new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07});
        return dir;
    }

    static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.write(new byte[]{0, 0, 0, 13, 'I', 'H', 'D', 'R'});
        out.write(be32(w));
        out.write(be32(h));
        out.write(new byte[]{8, 2, 0, 0, 0, 0, 0, 0, 0});
        out.write(new byte[]{0, 0, 0, 0});
        return out.toByteArray();
    }

    static byte[] gif(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
        out.write(le16(w));
        out.write(le16(h));
        out.write(new byte[]{0x00, 0x00, 0x00});
        return out.toByteArray();
    }

    static byte[] bmpInfo(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'B', 'M'});
        out.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        out.write(le32(40));
        out.write(le32(w));
        out.write(le32(h));
        out.write(new byte[]{1, 0, 24, 0});
        return out.toByteArray();
    }

    static byte[] bmpCore(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'B', 'M'});
        out.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        out.write(le32(12));
        out.write(le16(w));
        out.write(le16(h));
        out.write(new byte[]{1, 0, 24, 0});
        return out.toByteArray();
    }

    static byte[] jpeg(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
        out.write(new byte[]{(byte) 0xFF, (byte) 0xE0, 0x00, 0x10});
        for (int i = 0; i < 14; i++) out.write(0x00);
        out.write(new byte[]{(byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08});
        out.write(be16(h));
        out.write(be16(w));
        out.write(new byte[]{0x03, 0, 0, 0});
        return out.toByteArray();
    }

    static byte[] webpVp8(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.write("VP8 ".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write(new byte[]{0x00, 0x00, 0x00});
        out.write(new byte[]{(byte) 0x9D, 0x01, 0x2A});
        out.write(le16(w));
        out.write(le16(h));
        return out.toByteArray();
    }

    static byte[] webpVp8l(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.write("VP8L".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write(0x2F);
        int bits = ((w - 1) & 0x3FFF) | (((h - 1) & 0x3FFF) << 14);
        out.write(le32(bits));
        return out.toByteArray();
    }

    static byte[] webpVp8x(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.write("VP8X".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write(new byte[]{0, 0, 0, 0});
        out.write(le24(w - 1));
        out.write(le24(h - 1));
        return out.toByteArray();
    }

    static byte[] tiff(int w, int h, boolean little) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (little) {
            out.write(new byte[]{'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00});
            out.write(new byte[]{0x02, 0x00});
            out.write(new byte[]{0x00, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00});
            out.write(le32(w));
            out.write(new byte[]{0x01, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00});
            out.write(le32(h));
        } else {
            out.write(new byte[]{'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08});
            out.write(new byte[]{0x00, 0x02});
            out.write(new byte[]{0x01, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01});
            out.write(new byte[]{0x00, (byte) w, 0x00, 0x00});
            out.write(new byte[]{0x01, 0x01, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01});
            out.write(new byte[]{0x00, (byte) h, 0x00, 0x00});
        }
        out.write(new byte[]{0x00, 0x00, 0x00, 0x00});
        return out.toByteArray();
    }

    static byte[] ico(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x00, 0x00, 0x01, 0x00, 0x01, 0x00});
        out.write(new byte[]{(byte) (w & 0xFF), (byte) (h & 0xFF)});
        out.write(new byte[]{0x00, 0x00, 0x02, 0x00});
        out.write(le32(40));
        out.write(le32(22));
        return out.toByteArray();
    }

    static byte[] ppm(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("P6\n" + w + " " + h + "\n255\n").getBytes(StandardCharsets.US_ASCII));
        for (int i = 0; i < w * h * 3; i++) out.write(0x00);
        return out.toByteArray();
    }

    static byte[] qoi(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("qoif".getBytes(StandardCharsets.US_ASCII));
        out.write(be32(w));
        out.write(be32(h));
        out.write(new byte[]{4, 0, 1, 0});
        return out.toByteArray();
    }

    static byte[] psd(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("8BPS".getBytes(StandardCharsets.US_ASCII));
        out.write(be16(1));
        out.write(new byte[]{0, 0, 0, 0, 0, 0});
        out.write(be16(3));
        out.write(be32(h));
        out.write(be32(w));
        out.write(new byte[]{8, 3});
        return out.toByteArray();
    }

    static byte[] dds(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("DDS ".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(124));
        out.write(le32(0x1007));
        out.write(le32(h));
        out.write(le32(w));
        out.write(new byte[]{0, 0, 0, 0});
        return out.toByteArray();
    }

    static byte[] farbfeld(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("farbfeld".getBytes(StandardCharsets.US_ASCII));
        out.write(be32(w));
        out.write(be32(h));
        return out.toByteArray();
    }

    static byte[] avif(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(be32(28));
        out.write("ftyp".getBytes(StandardCharsets.US_ASCII));
        out.write("avif".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{0, 0, 0, 0});
        out.write("mif1".getBytes(StandardCharsets.US_ASCII));
        out.write("avif".getBytes(StandardCharsets.US_ASCII));
        out.write(be32(20));
        out.write("ispe".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{0, 0, 0, 0});
        out.write(be32(w));
        out.write(be32(h));
        return out.toByteArray();
    }

    static byte[] be16(int v) {
        return new byte[]{(byte) ((v >> 8) & 0xFF), (byte) (v & 0xFF)};
    }

    static byte[] be32(int v) {
        return new byte[]{(byte) ((v >> 24) & 0xFF), (byte) ((v >> 16) & 0xFF),
                (byte) ((v >> 8) & 0xFF), (byte) (v & 0xFF)};
    }

    static byte[] le16(int v) {
        return new byte[]{(byte) (v & 0xFF), (byte) ((v >> 8) & 0xFF)};
    }

    static byte[] le24(int v) {
        return new byte[]{(byte) (v & 0xFF), (byte) ((v >> 8) & 0xFF), (byte) ((v >> 16) & 0xFF)};
    }

    static byte[] le32(int v) {
        return new byte[]{(byte) (v & 0xFF), (byte) ((v >> 8) & 0xFF),
                (byte) ((v >> 16) & 0xFF), (byte) ((v >> 24) & 0xFF)};
    }

}
