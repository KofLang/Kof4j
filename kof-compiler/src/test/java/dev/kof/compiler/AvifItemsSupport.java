package dev.kof.compiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Fixtures + independent second reader for AVIF slice 2a (item location).
 *  The {@code iloc} byte layout follows ISO 14496-12 §8.7.4 as quoted by the
 *  mp4parser {@code ItemLocationBox} javadoc and FFmpeg {@code mov_read_iloc}
 *  (both read on the dev host 03/10). */
final class AvifItemsSupport {

    private AvifItemsSupport() {}

    static byte[] colorBytes() {
        byte[] a = new byte[40];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) ((i * 7) % 251);
        }
        return a;
    }

    static byte[] alphaBytes() {
        byte[] a = new byte[20];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (((200 - 3 * i) % 256) & 255);
        }
        return a;
    }

    static String fact(byte[] a) {
        long sum = 0;
        for (byte x : a) {
            sum += x & 255;
        }
        return "len=" + a.length + " first=" + (a[0] & 255)
                + " last=" + (a[a.length - 1] & 255) + " sum=" + sum;
    }

    /** iloc full-box (version 0/1), one extent per entry
     *  (id, construction, base, offset, length). base_offset_size = 4. */
    static byte[] iloc(int ver, List<int[]> entries) {
        int entrySize = ver == 1 ? 20 : 18;
        int n = entries.size();
        byte[] body = new byte[8 + n * entrySize];
        body[0] = (byte) ver;
        body[4] = 0x44;              // offset_size 4, length_size 4
        body[5] = 0x40;              // base_offset_size 4, index/reserved 0
        put16(body, 6, n);
        int p = 8;
        for (int[] e : entries) {
            p = put16(body, p, e[0]);
            if (ver == 1) p = put16(body, p, e[1] & 0x0F);
            p = put16(body, p, 0);
            p = put32(body, p, e[2]);
            p = put16(body, p, 1);
            p = put32(body, p, e[3]);
            p = put32(body, p, e[4]);
        }
        return AvifMetadataSupport.box("iloc", java.util.Arrays.copyOf(body, p));
    }

    /** iloc version 0, base_offset_size = 0 (the real-file shape measured
     *  03/10): the extent offset is the absolute file offset. */
    static byte[] ilocV0(int itemId, int offset, int length) {
        byte[] body = new byte[8 + 18];
        body[0] = 0;
        body[4] = 0x44;              // offset_size 4, length_size 4
        body[5] = 0x00;              // base_offset_size 0, reserved 0
        put16(body, 6, 1);
        int p = 8;
        p = put16(body, p, itemId);
        p = put16(body, p, 0);       // data_reference_index
        p = put16(body, p, 1);       // extent_count (no base_offset)
        p = put32(body, p, offset);
        p = put32(body, p, length);
        return AvifMetadataSupport.box("iloc", java.util.Arrays.copyOf(body, p));
    }

    /** iloc version 1 with TWO extents for one item (base_offset_size = 4):
     *  the face must concatenate them in order. */
    static byte[] ilocMultiV1(int itemId, int base, int[] offs, int[] lens) {
        int ec = offs.length;
        byte[] body = new byte[8 + 2 + 2 + 2 + 4 + 2 + ec * 8];
        body[0] = 1;
        body[4] = 0x44;
        body[5] = 0x40;
        put16(body, 6, 1);
        int p = 8;
        p = put16(body, p, itemId);
        p = put16(body, p, 0);       // reserved 0 + construction_method 0
        p = put16(body, p, 0);       // data_reference_index
        p = put32(body, p, base);
        p = put16(body, p, ec);
        for (int j = 0; j < ec; j++) {
            p = put32(body, p, offs[j]);
            p = put32(body, p, lens[j]);
        }
        return AvifMetadataSupport.box("iloc", java.util.Arrays.copyOf(body, p));
    }

    private static int put16(byte[] b, int p, int v) {
        b[p] = (byte) (v >>> 8);
        b[p + 1] = (byte) v;
        return p + 2;
    }

    private static int put32(byte[] b, int p, int v) {
        b[p] = (byte) (v >>> 24);
        b[p + 1] = (byte) (v >>> 16);
        b[p + 2] = (byte) (v >>> 8);
        b[p + 3] = (byte) v;
        return p + 4;
    }

    static byte[] ftyp() {
        return AvifMetadataSupport.box("ftyp", AvifMetadataSupport.concat(List.of(
                "avif".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0, 0, 0, 0},
                "avif".getBytes(StandardCharsets.US_ASCII))));
    }

    static byte[] meta(List<byte[]> children) {
        return AvifMetadataSupport.box("meta", AvifMetadataSupport.concatWith(
                new byte[]{0, 0, 0, 0}, children));
    }

    static byte[] baseMeta(int itemCount, List<byte[]> extra) {
        byte[] infe1 = AvifMetadataSupport.infe(1, "av01");
        byte[] iinf;
        if (itemCount == 2) {
            iinf = AvifMetadataSupport.iinf(2,
                    List.of(infe1, AvifMetadataSupport.infe(2, "av01")));
        } else {
            iinf = AvifMetadataSupport.iinf(1, List.of(infe1));
        }
        var children = new java.util.ArrayList<byte[]>();
        children.add(AvifMetadataSupport.pitm(1));
        children.add(iinf);
        children.add(AvifMetadataSupport.hdlr());
        children.addAll(extra);
        return meta(children);
    }

    static Path fixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        byte[] color = colorBytes();
        byte[] alpha = alphaBytes();

        // mdat.avif — construction 0, both items inside mdat (absolute).
        byte[] f = ftyp();
        byte[] mdat = AvifMetadataSupport.box("mdat",
                AvifMetadataSupport.concat(List.of(color, alpha)));
        byte[] metaA = baseMeta(2, List.of(iloc(1, List.of(
                new int[]{1, 0, 0, 0, color.length},
                new int[]{2, 0, 0, 0, alpha.length}))));
        int payloadAt = f.length + metaA.length + 8;
        metaA = baseMeta(2, List.of(iloc(1, List.of(
                new int[]{1, 0, 0, payloadAt, color.length},
                new int[]{2, 0, 0, payloadAt + color.length, alpha.length}))));
        Files.write(dir.resolve("mdat.avif"),
                AvifMetadataSupport.concat(List.of(f, metaA, mdat)));

        // idat.avif — construction 1, item 1 relative to the idat payload.
        byte[] idat = AvifMetadataSupport.box("idat", color);
        byte[] metaB = baseMeta(1, List.of(iloc(1, List.of(
                new int[]{1, 1, 0, 0, color.length}))));
        Files.write(dir.resolve("idat.avif"),
                AvifMetadataSupport.concat(List.of(f, metaB, idat)));

        // v0.avif — iloc version 0, base_offset_size 0 (the real-file shape):
        // item 1 points at the color bytes by absolute file offset.
        byte[] metaV0 = baseMeta(1, List.of(ilocV0(1, 0, color.length)));
        int colorAt = f.length + metaV0.length + 8;
        metaV0 = baseMeta(1, List.of(ilocV0(1, colorAt, color.length)));
        Files.write(dir.resolve("v0.avif"),
                AvifMetadataSupport.concat(List.of(f, metaV0,
                        AvifMetadataSupport.box("mdat", color))));

        // multi.avif — one item, TWO extents (color then alpha): the face
        // concatenates them in order.
        byte[] mdat2 = AvifMetadataSupport.box("mdat",
                AvifMetadataSupport.concat(List.of(color, alpha)));
        byte[] metaM = baseMeta(1, List.of(ilocMultiV1(1, 0,
                new int[]{0, color.length},
                new int[]{color.length, alpha.length})));
        int mAt = f.length + metaM.length + 8;
        metaM = baseMeta(1, List.of(ilocMultiV1(1, 0,
                new int[]{mAt, mAt + color.length},
                new int[]{color.length, alpha.length})));
        Files.write(dir.resolve("multi.avif"),
                AvifMetadataSupport.concat(List.of(f, metaM, mdat2)));

        // trunc.avif — extent points beyond the read prefix.
        byte[] metaC = baseMeta(1, List.of(iloc(1, List.of(
                new int[]{1, 0, 0, 100000, 10}))));
        Files.write(dir.resolve("trunc.avif"),
                AvifMetadataSupport.concat(List.of(f, metaC,
                        AvifMetadataSupport.box("mdat", alpha))));

        // v2.avif — iloc version 2.
        byte[] metaD = baseMeta(1, List.of(iloc(2, List.of(
                new int[]{1, 0, 0, 0, 4}))));
        Files.write(dir.resolve("v2.avif"),
                AvifMetadataSupport.concat(List.of(f, metaD, mdat)));

        // noloc.avif — no iloc at all.
        Files.write(dir.resolve("noloc.avif"),
                AvifMetadataSupport.concat(List.of(f, baseMeta(1, List.of()), mdat)));
        return dir;
    }

    // --- second independent reader (plain Java, written from ISO 14496-12) --

    static byte[] readItemJava(Path file, int itemId) throws Exception {
        byte[] b = Files.readAllBytes(file);
        int meta = AvifMetadataSupport.findBox(b, 0, b.length, "meta");
        assertTrue(meta >= 0, "meta");
        int iloc = AvifMetadataSupport.findBox(b, meta + 12,
                AvifMetadataSupport.boxEnd(b, meta), "iloc");
        assertTrue(iloc >= 0, "iloc");
        int ver = b[iloc + 8] & 255;
        assertTrue(ver <= 1, "iloc v0/v1");
        int osz = (b[iloc + 12] & 255) >> 4;
        int lsz = (b[iloc + 12] & 255) & 15;
        int bsz = (b[iloc + 13] & 255) >> 4;
        int isz = ver == 1 ? (b[iloc + 13] & 255) & 15 : 0;
        assertTrue(isz == 0, "index size 0");
        int count = AvifMetadataSupport.be16(b, iloc + 14);
        int p = iloc + 16;
        for (int i = 0; i < count; i++) {
            int id = AvifMetadataSupport.be16(b, p);
            p += 2;
            int constr = 0;
            if (ver == 1) {
                constr = AvifMetadataSupport.be16(b, p) & 15;
                p += 2;
            }
            p += 2; // data_reference_index
            long base = rb(b, p, bsz);
            p += bsz;
            int ec = AvifMetadataSupport.be16(b, p);
            p += 2;
            if (id == itemId) {
                assertTrue(constr <= 1, "construction");
                int idat = constr == 1
                        ? AvifMetadataSupport.findBox(b, 0, b.length, "idat") + 8 : 0;
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                for (int j = 0; j < ec; j++) {
                    long off = rb(b, p, osz);
                    p += osz;
                    long len = rb(b, p, lsz);
                    p += lsz;
                    int at = (int) (idat + base + off);
                    out.write(b, at, (int) len);
                }
                return out.toByteArray();
            }
            p += ec * (osz + lsz);
        }
        throw new AssertionError("item not found");
    }

    private static long rb(byte[] b, int p, int n) {
        long v = 0;
        for (int i = 0; i < n; i++) {
            v = (v << 8) | (b[p + i] & 255);
        }
        return v;
    }

    private static void assertTrue(boolean v, String m) {
        org.junit.jupiter.api.Assertions.assertTrue(v, m);
    }

    static String itemProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifItems

            String fact(Int[] a) {
                Int sum = 0
                for (var x in a) {
                    sum = sum + x
                }
                return "len=" + a.length + " first=" + a[0]
                    + " last=" + a[a.length - 1] + " sum=" + sum
            }

            main() {
                var base = "%s"
                println("mdat1 " + fact(readAvifItemBytes(base + "/mdat.avif", 1)))
                println("mdat2 " + fact(readAvifItemBytes(base + "/mdat.avif", 2)))
                println("idat1 " + fact(readAvifItemBytes(base + "/idat.avif", 1)))
                println("v01 " + fact(readAvifItemBytes(base + "/v0.avif", 1)))
                println("multi1 " + fact(readAvifItemBytes(base + "/multi.avif", 1)))
            }
            """.formatted(base);
    }

    static String errorProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifItems

            main() {
                var base = "%s"
                try {
                    readAvifItemBytes(base + "/trunc.avif", 1)
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemBytes(base + "/v2.avif", 1)
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemBytes(base + "/noloc.avif", 1)
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemBytes(base + "/mdat.avif", 9)
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(base);
    }
}
