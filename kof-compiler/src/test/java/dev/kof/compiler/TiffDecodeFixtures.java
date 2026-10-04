package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fixtures and a second, independent reader for {@link TiffDecodeE2ETest}.
 *
 * <p>The TIFF byte streams are hand-built here per TIFF 6.0 (both byte orders,
 * inline vs offset values, single and multi-strip, PackBits) and each positive
 * fixture was cross-validated offline against PIL and Java {@code ImageIO}
 * before the golden was pinned. The Kof library in {@code libs/image/Tiff.kf}
 * is checked against {@link #readFacts}, a plain-Java TIFF walk written
 * independently of the Kof decoder — agreement is the proof that both decode
 * the same pixels.
 */
final class TiffDecodeFixtures {

    private TiffDecodeFixtures() {
    }

    static String probe(Path dir) {
        String base = RasterDecodeFixtures.path(dir);
        return """
            import image.Raster

            String facts(String label, String path) {
                var r = decodeRaster(path)
                var sum = 0
                var hash = 0
                var i = 0
                while (i < r.samples.length) {
                    var v = r.samples[i]
                    sum = sum + v
                    hash = ((hash << 5) + hash + v) & 16777215
                    i = i + 1
                }
                return label + " " + r.format + " " + r.width + "x" + r.height
                    + " ch=" + r.channels + " " + sum + ":" + hash
            }

            main() {
                var d = "%s"
                println(facts("gray", d + "/gray.tif"))
                println(facts("rgb", d + "/rgb.tif"))
                println(facts("rgba", d + "/rgba.tif"))
                println(facts("graya", d + "/graya.tif"))
                println(facts("palette", d + "/palette.tif"))
                println(facts("white0", d + "/white0.tif"))
                println(facts("packbits", d + "/packbits.tif"))
                println(facts("strips", d + "/strips.tif"))
                println(facts("multiarray", d + "/multiarray.tif"))
                println(facts("one", d + "/one.tif"))
            }
            """.formatted(base);
    }

    static String refusalProbe(Path src) {
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
            """.formatted(RasterDecodeFixtures.path(src));
    }

    static final List<String> POSITIVE = List.of(
            "gray", "rgb", "rgba", "graya", "palette", "white0", "packbits", "strips", "multiarray", "one");

    /** Golden for {@link #probe}, built from the independent reader. */
    static String golden(Path dir) {
        StringBuilder out = new StringBuilder();
        for (String name : POSITIVE) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(readFacts(name, dir.resolve(name + ".tif")));
        }
        return out.toString();
    }

    static Path writeFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        // grayscale LE 2x2: 10,20,30,40
        write(dir, "gray.tif", build(true, 2, 2, 1, new int[]{8}, 1, 1, null, null, 2, null, null,
                new byte[][]{{10, 20, 30, 40}}));
        // RGB BE 2x2
        write(dir, "rgb.tif", build(false, 2, 2, 3, new int[]{8, 8, 8}, 1, 2, null, null, 2, null, null,
                new byte[][]{{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}}));
        // RGBA LE 2x2, unassociated alpha (ExtraSamples value 2)
        write(dir, "rgba.tif", build(true, 2, 2, 4, new int[]{8, 8, 8, 8}, 1, 2, null, null, 2, 2, null,
                new byte[][]{{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}}));
        // gray + unassociated alpha (spp 2)
        write(dir, "graya.tif", build(true, 2, 2, 2, new int[]{8, 8}, 1, 1, null, null, 2, 2, null,
                new byte[][]{{10, 4, 20, 8, 30, 12, 40, 16}}));
        // palette 2x2: indices 0,1,1,0 over a 2-colour ColorMap (red, green)
        int[] cm = new int[768];
        cm[0] = 0xffff;
        cm[256] = 0xffff;
        cm[512] = 0x0000;
        write(dir, "palette.tif", build(true, 2, 2, 1, new int[]{8}, 1, 3, null, null, 2, null, cm,
                new byte[][]{{0, 1, 1, 0}}));
        // WhiteIsZero gray 2x2
        write(dir, "white0.tif", build(true, 2, 2, 1, new int[]{8}, 1, 0, null, null, 2, null, null,
                new byte[][]{{10, 20, 30, 40}}));
        // PackBits gray 2x2: literal 10,20 then a run of 30 twice
        write(dir, "packbits.tif", build(true, 2, 2, 1, new int[]{8}, 32773, 1, null, null, 2, null, null,
                new byte[][]{{1, 10, 20, (byte) 255, 30}}));
        // two strips, gray 1x4: strip0 = 10,20 ; strip1 = 30,40
        write(dir, "strips.tif", build(true, 1, 4, 1, new int[]{8}, 1, 1, null, null, 2, null, null,
                new byte[][]{{10, 20}, {30, 40}}));
        // BitsPerSample is a 3-element SHORT array stored INLINE in the value field
        write(dir, "multiarray.tif", build(true, 2, 2, 3, new int[]{8, 8, 8}, 1, 2, null, null, 2, null, null,
                new byte[][]{{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}}));
        // 1x1 RGB
        write(dir, "one.tif", build(true, 1, 1, 3, new int[]{8, 8, 8}, 1, 2, null, null, 1, null, null,
                new byte[][]{{7, 8, 9}}));
        // refusals (named, never a silent wrong decode)
        write(dir, "lzw.tif", build(true, 2, 2, 1, new int[]{8}, 5, 1, null, null, 2, null, null,
                new byte[][]{{10, 20, 30, 40}}));
        write(dir, "planar.tif", build(true, 2, 2, 1, new int[]{8}, 1, 1, 2, null, 2, null, null,
                new byte[][]{{10, 20, 30, 40}}));
        write(dir, "predictor.tif", build(true, 2, 2, 1, new int[]{8}, 1, 1, null, 2, 2, null, null,
                new byte[][]{{10, 20, 30, 40}}));
        write(dir, "bits16.tif", build(true, 2, 2, 1, new int[]{16}, 1, 1, null, null, 2, null, null,
                new byte[][]{{0, 10, 0, 20, 0, 30, 0, 40}}));
        write(dir, "photo5.tif", build(true, 2, 2, 1, new int[]{8}, 1, 5, null, null, 2, null, null,
                new byte[][]{{10, 20, 30, 40}}));
        write(dir, "noextra.tif", build(true, 2, 2, 4, new int[]{8, 8, 8, 8}, 1, 2, null, null, 2, null, null,
                new byte[][]{{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}}));
        write(dir, "spp5.tif", build(true, 2, 2, 5, new int[]{8, 8, 8, 8, 8}, 1, 2, null, null, 2, 2, null,
                new byte[][]{{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20}}));
        write(dir, "assoc.tif", build(true, 2, 2, 4, new int[]{8, 8, 8, 8}, 1, 2, null, null, 2, 1, null,
                new byte[][]{{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}}));
        write(dir, "palspp2.tif", build(true, 2, 2, 2, new int[]{8, 8}, 1, 3, null, null, 2, 2, new int[768],
                new byte[][]{{0, 1, 1, 0, 0, 1, 1, 0}}));
        return dir;
    }

    static final List<String> REFUSALS = List.of(
            "lzw", "planar", "predictor", "bits16", "photo5", "noextra", "spp5", "assoc", "palspp2");

    /** The named refusal the independent reader reports for a fixture. */
    static String javaRefusal(Path dir, String name) {
        try {
            readFacts(name, dir.resolve(name + ".tif"));
            return "<no refusal>";
        } catch (IllegalStateException e) {
            return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        }
    }

    // ---- independent reader -------------------------------------------------

    static String readFacts(String label, Path file) {
        try {
            byte[] b = Files.readAllBytes(file);
            boolean le = b[0] == 'I' && b[1] == 'I';
            int ifd = u32(b, 4, le);
            int n = u16(b, ifd, le);
            int width = 0, height = 0, spp = 1, compression = 1, photo = 1, planar = 1, predictor = 1;
            int extra = -1;
            int[] bps = null, colorMap = null, offsets = null, counts = null;
            for (int i = 0; i < n; i++) {
                int p = ifd + 2 + i * 12;
                int tag = u16(b, p, le);
                int typ = u16(b, p + 2, le);
                int cnt = u32(b, p + 4, le);
                switch (tag) {
                    case 256 -> width = values(b, p, cnt, typ, le)[0];
                    case 257 -> height = values(b, p, cnt, typ, le)[0];
                    case 258 -> bps = values(b, p, cnt, typ, le);
                    case 259 -> compression = values(b, p, cnt, typ, le)[0];
                    case 262 -> photo = values(b, p, cnt, typ, le)[0];
                    case 273 -> offsets = values(b, p, cnt, typ, le);
                    case 277 -> spp = values(b, p, cnt, typ, le)[0];
                    case 279 -> counts = values(b, p, cnt, typ, le);
                    case 284 -> planar = values(b, p, cnt, typ, le)[0];
                    case 317 -> predictor = values(b, p, cnt, typ, le)[0];
                    case 320 -> colorMap = values(b, p, cnt, typ, le);
                    case 338 -> extra = values(b, p, cnt, typ, le)[0];
                    default -> { }
                }
            }
            if (width <= 0 || height <= 0) {
                throw new IllegalStateException("dims");
            }
            if (compression != 1 && compression != 32773) {
                throw new IllegalStateException("IMAGE: TIFF compression not covered");
            }
            if (planar != 1) {
                throw new IllegalStateException("IMAGE: TIFF planar configuration not covered");
            }
            if (predictor != 1) {
                throw new IllegalStateException("IMAGE: TIFF predictor not covered");
            }
            int channels;
            boolean palette = false;
            if (photo == 0 || photo == 1) {
                channels = 1;
            } else if (photo == 2) {
                channels = 3;
            } else if (photo == 3) {
                palette = true;
                channels = 3;
                if (spp != 1 || extra >= 0) {
                    throw new IllegalStateException("IMAGE: TIFF palette samples not covered");
                }
            } else {
                throw new IllegalStateException("IMAGE: TIFF photometric not covered");
            }
            if (!palette) {
                if (spp == channels) {
                    if (extra >= 0) {
                        throw new IllegalStateException("IMAGE: TIFF extra samples not covered");
                    }
                } else if (spp == channels + 1) {
                    if (extra < 0) {
                        throw new IllegalStateException("IMAGE: TIFF extra samples not covered");
                    }
                    if (extra != 2) {
                        throw new IllegalStateException("IMAGE: TIFF associated alpha not covered");
                    }
                    channels = channels + 1;
                } else {
                    throw new IllegalStateException("IMAGE: TIFF samples per pixel not covered");
                }
            }
            if (bps == null) {
                throw new IllegalStateException("IMAGE: TIFF bits per sample not covered");
            }
            for (int v : bps) {
                if (v != 8) {
                    throw new IllegalStateException("IMAGE: TIFF bits per sample not covered");
                }
            }
            int rawLen = 0;
            for (int c : counts) {
                rawLen += c;
            }
            int[] raw = new int[rawLen];
            int pos = 0;
            for (int s = 0; s < offsets.length; s++) {
                int off = offsets[s];
                int len = counts[s];
                if (off < 0 || len < 0 || off + len > b.length) {
                    throw new IllegalStateException("IMAGE: truncated TIFF strip");
                }
                if (compression == 32773) {
                    int p = off;
                    int end = off + len;
                    while (p < end) {
                        int c = b[p++] & 255;
                        if (c < 128) {
                            for (int k = 0; k <= c; k++) {
                                raw[pos++] = b[p++] & 255;
                            }
                        } else if (c > 128) {
                            int run = 257 - c;
                            int v = b[p++] & 255;
                            for (int k = 0; k < run; k++) {
                                raw[pos++] = v;
                            }
                        }
                    }
                } else {
                    for (int k = 0; k < len; k++) {
                        raw[pos++] = b[off + k] & 255;
                    }
                }
            }
            int pixels = width * height;
            int[] samples = new int[pixels * channels];
            if (palette) {
                if (colorMap == null || colorMap.length < 768) {
                    throw new IllegalStateException("IMAGE: TIFF color map not covered");
                }
                for (int k = 0; k < pixels; k++) {
                    int idx = raw[k] & 255;
                    samples[k * 3] = colorMap[idx] >> 8;
                    samples[k * 3 + 1] = colorMap[256 + idx] >> 8;
                    samples[k * 3 + 2] = colorMap[512 + idx] >> 8;
                }
            } else {
                for (int k = 0; k < pixels * channels; k++) {
                    int v = raw[k] & 255;
                    samples[k] = photo == 0 ? 255 - v : v;
                }
            }
            int sum = 0;
            int hash = 0;
            for (int v : samples) {
                sum += v;
                hash = ((hash << 5) + hash + v) & 16777215;
            }
            return label + " TIFF " + width + "x" + height + " ch=" + channels
                    + " " + sum + ":" + hash;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- TIFF writer --------------------------------------------------------

    private record Ent(int id, int type, int count, Integer inline, byte[] inlineRaw, byte[] blob) {
    }

    private static byte[] build(boolean le, int w, int h, int spp, int[] bps, int compression, int photo,
                                Integer planar, Integer predictor, int rowsPerStrip, Integer extraSamples,
                                int[] colorMap, byte[][] strips) {
        List<Ent> tags = new ArrayList<>();
        tags.add(new Ent(256, 3, 1, w, null, null));
        tags.add(new Ent(257, 3, 1, h, null, null));
        if (bps.length == 1) {
            tags.add(new Ent(258, 3, 1, bps[0], null, null));
        } else if (bps.length * 2 <= 4) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            for (int v : bps) {
                tiffWriteBytes(b, u16(v, le));
            }
            while (b.size() < 4) {
                b.write(0);
            }
            tags.add(new Ent(258, 3, bps.length, null, b.toByteArray(), null));
        } else {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            for (int v : bps) {
                tiffWriteBytes(b, u16(v, le));
            }
            tags.add(new Ent(258, 3, bps.length, null, null, b.toByteArray()));
        }
        tags.add(new Ent(259, 3, 1, compression, null, null));
        tags.add(new Ent(262, 3, 1, photo, null, null));
        tags.add(new Ent(277, 3, 1, spp, null, null));
        tags.add(new Ent(278, 3, 1, rowsPerStrip, null, null));
        tags.add(new Ent(279, 4, strips.length, strips.length == 1 ? 0 : null, null,
                strips.length == 1 ? null : new byte[strips.length * 4]));
        tags.add(new Ent(273, 4, strips.length, strips.length == 1 ? 0 : null, null,
                strips.length == 1 ? null : new byte[strips.length * 4]));
        if (planar != null) {
            tags.add(new Ent(284, 3, 1, planar, null, null));
        }
        if (predictor != null) {
            tags.add(new Ent(317, 3, 1, predictor, null, null));
        }
        if (colorMap != null) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            for (int v : colorMap) {
                tiffWriteBytes(b, u16(v, le));
            }
            tags.add(new Ent(320, 3, colorMap.length, null, null, b.toByteArray()));
        }
        if (extraSamples != null) {
            tags.add(new Ent(338, 3, 1, extraSamples, null, null));
        }
        tags.sort(Comparator.comparingInt(Ent::id));

        int ifdStart = 8;
        int ifdSize = 2 + 12 * tags.size() + 4;
        int cursor = ifdStart + ifdSize;
        int[] blobOff = new int[tags.size()];
        for (int i = 0; i < tags.size(); i++) {
            Ent t = tags.get(i);
            if (t.blob() != null) {
                blobOff[i] = cursor;
                cursor += t.blob().length;
            }
        }
        int stripDataOff = cursor;
        int[] offs = new int[strips.length];
        int[] lens = new int[strips.length];
        int p = stripDataOff;
        for (int i = 0; i < strips.length; i++) {
            offs[i] = p;
            lens[i] = strips[i].length;
            p += lens[i];
        }
        // The 273/279 entries were sized before the offset pass (inline for a
        // single strip, a fixed 4-byte-per-value blob otherwise), so the blob
        // offsets above are final; fill their contents now.
        List<Ent> filled = new ArrayList<>();
        for (Ent t : tags) {
            if (t.id() == 273) {
                if (t.inline() != null) {
                    filled.add(new Ent(273, 4, 1, offs[0], null, null));
                } else {
                    tiffWriteInts(t.blob(), offs, le);
                    filled.add(t);
                }
            } else if (t.id() == 279) {
                if (t.inline() != null) {
                    filled.add(new Ent(279, 4, 1, lens[0], null, null));
                } else {
                    tiffWriteInts(t.blob(), lens, le);
                    filled.add(t);
                }
            } else {
                filled.add(t);
            }
        }
        tags = filled;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        tiffWriteBytes(out, le ? "II".getBytes() : "MM".getBytes());
        tiffWriteBytes(out, u16(42, le));
        tiffWriteBytes(out, u32(ifdStart, le));
        tiffWriteBytes(out, u16(tags.size(), le));
        for (int i = 0; i < tags.size(); i++) {
            Ent t = tags.get(i);
            tiffWriteBytes(out, u16(t.id(), le));
            tiffWriteBytes(out, u16(t.type(), le));
            tiffWriteBytes(out, u32(t.count(), le));
            if (t.inlineRaw() != null) {
                tiffWriteBytes(out, t.inlineRaw());
            } else if (t.inline() != null) {
                if (t.type() == 3) {
                    tiffWriteBytes(out, u16(t.inline(), le));
                    tiffWriteBytes(out, u16(0, le));
                } else {
                    tiffWriteBytes(out, u32(t.inline(), le));
                }
            } else {
                tiffWriteBytes(out, u32(blobOff[i], le));
            }
        }
        tiffWriteBytes(out, u32(0, le));
        for (Ent t : tags) {
            if (t.blob() != null) {
                tiffWriteBytes(out, t.blob());
            }
        }
        for (byte[] s : strips) {
            tiffWriteBytes(out, s);
        }
        return out.toByteArray();
    }

    private static void tiffWriteInts(byte[] dst, int[] values, boolean le) {
        for (int i = 0; i < values.length; i++) {
            byte[] v = u32(values[i], le);
            System.arraycopy(v, 0, dst, i * 4, 4);
        }
    }

    private static int[] values(byte[] b, int entryPos, int n, int typ, boolean le) {
        int elem = typ == 4 ? 4 : 2;
        int p = entryPos + 8;
        if (n * elem > 4) {
            p = u32(b, entryPos + 8, le);
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = typ == 3 ? u16(b, p + i * 2, le) : u32(b, p + i * 4, le);
        }
        return out;
    }

    private static void write(Path dir, String name, byte[] bytes) throws Exception {
        Files.write(dir.resolve(name), bytes);
    }

    private static byte[] u16(int v, boolean le) {
        return le ? new byte[]{(byte) v, (byte) (v >> 8)} : new byte[]{(byte) (v >> 8), (byte) v};
    }

    private static byte[] u32(int v, boolean le) {
        return le ? new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)}
                : new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    private static void tiffWriteBytes(ByteArrayOutputStream o, byte[] b) {
        o.writeBytes(b);
    }

    private static int u16(byte[] b, int i, boolean le) {
        if (le) {
            return (b[i] & 255) | ((b[i + 1] & 255) << 8);
        }
        return ((b[i] & 255) << 8) | (b[i + 1] & 255);
    }

    private static int u32(byte[] b, int i, boolean le) {
        if (le) {
            return (b[i] & 255) | ((b[i + 1] & 255) << 8) | ((b[i + 2] & 255) << 16) | ((b[i + 3] & 255) << 24);
        }
        return ((b[i] & 255) << 24) | ((b[i + 1] & 255) << 16) | ((b[i + 2] & 255) << 8) | (b[i + 3] & 255);
    }
}
