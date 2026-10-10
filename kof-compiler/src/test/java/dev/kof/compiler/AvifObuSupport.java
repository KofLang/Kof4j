package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Fixtures + independent second reader for AVIF slice 2c (item OBU stream). */
final class AvifObuSupport {

    private AvifObuSupport() {}

    /** AV1 obu_size is LEB128, little-endian (AV1 4.10.5): low 7 bits first. */
    static byte[] leb128(int v) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int x = v;
        do {
            int b = x & 127;
            x >>>= 7;
            if (x != 0) b |= 128;
            out.write(b);
        } while (x != 0);
        return out.toByteArray();
    }

    static byte[] obuRaw(int type, byte[] payload, boolean hasSize, boolean reservedBit,
                         boolean forbidden) {
        int head = ((forbidden ? 1 : 0) << 7) | (type << 3) | ((hasSize ? 1 : 0) << 1)
                | (reservedBit ? 1 : 0);
        if (!hasSize) {
            byte[] out = new byte[1 + payload.length];
            out[0] = (byte) head;
            System.arraycopy(payload, 0, out, 1, payload.length);
            return out;
        }
        byte[] size = leb128(payload.length);
        byte[] out = new byte[1 + size.length + payload.length];
        out[0] = (byte) head;
        System.arraycopy(size, 0, out, 1, size.length);
        System.arraycopy(payload, 0, out, 1 + size.length, payload.length);
        return out;
    }

    /** The mix stream: delimiter + reduced seq (mono 8-bit) + metadata + padding. */
    static byte[] mixStream() {
        return AvifMetadataSupport.concat(List.of(
                AvifSeqSupport.delimiter(),
                AvifMetadataSupport.configObu(0, 2, false, false, true),
                obuRaw(5, new byte[]{0x2A, 0x01}, true, false, false),
                obuRaw(15, new byte[0], true, false, false)));
    }

    /** The red stream: delimiter + non-reduced seq (profile 2, 12-bit) + frame header
     *  + tile group + redundant frame header + tile list + reserved type 9 + frame. */
    static byte[] redStream() {
        byte[] seq = AvifSeqSupport.nrMonoObuType(1, 2, 4, true, true, false, false, false, 8, 8);
        return AvifMetadataSupport.concat(List.of(
                AvifSeqSupport.delimiter(),
                seq,
                obuRaw(3, new byte[]{0x00}, true, false, false),
                obuRaw(4, new byte[]{0x01, 0x02}, true, false, false),
                obuRaw(7, new byte[]{0x00}, true, false, false),
                obuRaw(8, new byte[]{0x00}, true, false, false),
                obuRaw(9, new byte[]{0x01}, true, false, false),
                obuRaw(6, new byte[]{0x03}, true, false, false)));
    }

    /** Multi-byte LEB128 size: a 200-byte padding OBU, then a metadata OBU —
     *  the walk must land exactly after the big payload (AV1 4.10.5). */
    static byte[] bigStream() {
        return AvifMetadataSupport.concat(List.of(
                AvifSeqSupport.delimiter(),
                AvifMetadataSupport.configObu(0, 2, false, false, true),
                obuRaw(15, new byte[200], true, false, false),
                obuRaw(5, new byte[]{0x2A, 0x01}, true, false, false)));
    }

    static byte[] streamBytes(String name) {
        switch (name) {
            case "mix.avif": return mixStream();
            case "red.avif": return redStream();
            case "big.avif": return bigStream();
            case "nodelim.avif":
                return AvifMetadataSupport.concat(List.of(
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        AvifSeqSupport.delimiter()));
            case "noseq.avif":
                return AvifMetadataSupport.concat(List.of(
                        AvifSeqSupport.delimiter(),
                        obuRaw(5, new byte[]{0x2A, 0x01}, true, false, false)));
            case "trunc.avif": {
                // claims a 9-byte payload but only 4 exist: size > remaining
                byte[] head = new byte[]{0x0A, 0x09, 0, 0, 0, 0};
                return AvifMetadataSupport.concat(List.of(
                        AvifSeqSupport.delimiter(), head));
            }
            case "nosize.avif":
                return AvifMetadataSupport.concat(List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        obuRaw(5, new byte[]{0x00}, false, false, false)));
            case "resbit.avif":
                return AvifMetadataSupport.concat(List.of(
                        AvifSeqSupport.delimiter(),
                        obuRaw(1, new byte[4], true, true, false)));
            case "forbid.avif":
                return AvifMetadataSupport.concat(List.of(
                        AvifSeqSupport.delimiter(),
                        obuRaw(1, new byte[4], true, false, true)));
            default: return new byte[0];
        }
    }

    /** Container with the stream stored in mdat as primary item 1 (construction 0). */
    static byte[] containerWithItem(byte[] stream) {
        byte[] f = AvifItemsSupport.ftyp();
        byte[] mdat = AvifMetadataSupport.box("mdat", stream);
        byte[] av1c = AvifMetadataSupport.av1c(0, 2, false, false, false, true, false,
                false, AvifMetadataSupport.configObu(0, 2, false, false, true));
        byte[] ispe = AvifMetadataSupport.ispe(8, 8);
        byte[] iprp = AvifMetadataSupport.iprp(List.of(av1c, ispe),
                List.of(AvifMetadataSupport.ipmaEntry(1, 1, 2)));
        byte[] metaPre = AvifItemsSupport.baseMeta(1, List.of(
                av1c, ispe, iprp,
                AvifItemsSupport.iloc(1, List.of(new int[]{1, 0, 0, 0, stream.length}))));
        int payloadAt = f.length + metaPre.length + 8;
        byte[] meta = AvifItemsSupport.baseMeta(1, List.of(
                av1c, ispe, iprp,
                AvifItemsSupport.iloc(1, List.of(new int[]{1, 0, 0, payloadAt, stream.length}))));
        return AvifMetadataSupport.concat(List.of(f, meta, mdat));
    }

    static Path fixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        for (String name : List.of("mix.avif", "red.avif", "big.avif", "nodelim.avif", "noseq.avif")) {
            Files.write(dir.resolve(name), containerWithItem(streamBytes(name)));
        }
        return dir;
    }

    static Path errorFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        for (String name : List.of("trunc.avif", "nosize.avif", "resbit.avif",
                "forbid.avif", "nodelim.avif", "noseq.avif")) {
            Files.write(dir.resolve(name), containerWithItem(streamBytes(name)));
        }
        return dir;
    }

    static String probe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifObu

            String of(Bool v) {
                if (v) {
                    return "1"
                }
                return "0"
            }

            String facts(AvifObuCounts c) {
                return "t=" + c.total + " d=" + c.delimiters + " s=" + c.seqHeaders
                    + " fh=" + c.frameHeaders + " rf=" + c.redundantFrameHeaders
                    + " tg=" + c.tileGroups + " tl=" + c.tileLists
                    + " m=" + c.metadata + " f=" + c.frames + " p=" + c.padding
                    + " r=" + c.reserved + " seq p=" + c.seq.seqProfile
                    + " r=" + of(c.seq.reduced) + " w=" + c.seq.maxWidth
                    + " h=" + c.seq.maxHeight + " mono=" + of(c.seq.monochrome)
                    + " sub=" + of(c.seq.subsamplingX) + "/" + of(c.seq.subsamplingY)
                    + " depth=" + c.seq.bitDepth + " still=" + of(c.seq.stillPicture)
            }

            main() {
                var base = "%s"
                println("mix " + facts(readAvifItemObus(base + "/mix.avif")))
                println("red " + facts(readAvifItemObus(base + "/red.avif")))
                println("big " + facts(readAvifItemObus(base + "/big.avif")))
            }
            """.formatted(base);
    }

    static String errorProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifObu

            main() {
                var base = "%s"
                try {
                    readAvifItemObus(base + "/trunc.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemObus(base + "/nosize.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemObus(base + "/resbit.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemObus(base + "/forbid.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemObus(base + "/nodelim.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemObus(base + "/noseq.avif")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(base);
    }

    // --- second independent reader (plain Java, written from AV1 5.2/5.3/6.2) --

    static String javaObuFacts(Path file) throws Exception {
        byte[] b = Files.readAllBytes(file);
        byte[] item = AvifItemsSupport.readItemJava(file, 1);
        int total = 0, delimiters = 0, seqHeaders = 0, frameHeaders = 0, redundant = 0;
        int tileGroups = 0, tileLists = 0, metadata = 0, frames = 0, padding = 0, reserved = 0;
        String seq = null;
        int pos = 0;
        while (pos < item.length) {
            int head = item[pos] & 255;
            if ((head >> 7) != 0) throw new AssertionError("forbidden");
            int type = (head >> 3) & 15;
            int ext = (head >> 2) & 1;
            if (((head >> 1) & 1) != 1) throw new AssertionError("nosize");
            if ((head & 1) != 0) throw new AssertionError("resbit");
            if (total == 0 && type != 2) throw new AssertionError("nodelim");
            int p = pos + 1 + ext;
            int size = 0;
            int mult = 1;
            for (int i = 0; i < 8; i++) {
                int x = item[p] & 255;
                p++;
                size = size + (x & 127) * mult;
                mult *= 128;
                if ((x & 128) == 0) break;
            }
            int limit = p + size;
            if (limit > item.length) throw new AssertionError("trunc");
            total++;
            switch (type) {
                case 2: delimiters++; break;
                case 1:
                    seqHeaders++;
                    if (seq == null) seq = AvifSeqSupport.javaSeqCore(item, p, limit);
                    break;
                case 3: frameHeaders++; break;
                case 7: redundant++; break;
                case 4: tileGroups++; break;
                case 8: tileLists++; break;
                case 5: metadata++; break;
                case 6: frames++; break;
                case 15: padding++; break;
                default: reserved++; break;
            }
            pos = limit;
        }
        if (seq == null) throw new AssertionError("noseq");
        return "t=" + total + " d=" + delimiters + " s=" + seqHeaders
                + " fh=" + frameHeaders + " rf=" + redundant + " tg=" + tileGroups
                + " tl=" + tileLists + " m=" + metadata + " f=" + frames
                + " p=" + padding + " r=" + reserved + " seq " + seq;
    }
}
