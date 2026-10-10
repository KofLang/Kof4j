package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

/** Fixtures + probe for AVIF slice 2g (OBU_METADATA payload walk, AV1
 *  5.8.1-5.8.4 + 6.4.1 metadata_type table read from the spec corpus). */
final class AvifMetaSupport {

    private AvifMetaSupport() {}

    /** metadata OBU: leb128 type + payload bytes. */
    static byte[] metaObu(int type, byte[] payload) {
        byte[] out = new byte[3 + payload.length];
        out[0] = (byte) ((5 << 3) | 0x02);            // OBU_METADATA (5), has_size
        out[1] = (byte) (1 + payload.length);         // obu_size
        out[2] = (byte) type;                         // leb128 single-byte type
        System.arraycopy(payload, 0, out, 3, payload.length);
        return out;
    }

    private static byte[] u16(int v) {
        return new byte[]{(byte) (v >> 8), (byte) v};
    }

    private static byte[] u32(long v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    static Path fixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        // t35: metadata_type 4 (METADATA_TYPE_ITUT_T35, AV1 6.4.1), country
        // 0xB5, 4 payload bytes
        Files.write(dir.resolve("t35.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(4, new byte[]{(byte) 0xB5, 0x11, 0x22, 0x33, 0x44})))));
        // t35 with 0xFF country extension: 0xFF, 0x01, 2 payload bytes
        Files.write(dir.resolve("t35x.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(4, new byte[]{(byte) 0xFF, 0x01, 0x21, 0x22})))));
        // cll: max_cll=1000, max_fall=400
        Files.write(dir.resolve("cll.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(1, concatBytes(u16(1000), u16(400)))))));
        // mdcv: primaries (13252,34591)(22413,71241)(3596,7146) white (15635,16450)
        // luminance max=10000000 min=1
        Files.write(dir.resolve("mdcv.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(2, concatBytes(u16(13252), u16(34591), u16(22413), u16(60000),
                                               u16(3596), u16(7146), u16(15635), u16(16450),
                                               u32(10000000L), u32(1L)))))));
        // tc: metadata_type 5 (METADATA_TYPE_TIMECODE), full_timestamp_flag=1:
        // counting_type 3, disc 0, drop 1, n_frames 24, s 45, m 59, h 23,
        // time_offset_length 0 (no value) — 39 bits in 5 bytes
        Files.write(dir.resolve("tc.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(5, bitPack(new int[][]{
                                {3, 5}, {1, 1}, {0, 1}, {1, 1}, {24, 9},
                                {45, 6}, {59, 6}, {23, 5}, {0, 5}}))))));
        // tcf: full_timestamp_flag=0, every optional flag 0: counting_type 1,
        // disc 1, drop 0, n_frames 0, time_offset_length 4, offset 9 —
        // 27 bits in 4 bytes
        Files.write(dir.resolve("tcf.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(5, bitPack(new int[][]{
                                {1, 5}, {0, 1}, {1, 1}, {0, 1}, {0, 9},
                                {0, 1}, {4, 5}, {9, 4}}))))));
        // mixed stream: t35(4) + scalability(3, opaque 5 bytes) + unregistered
        // private(7, 2 bytes) + AOM-reserved(32, 1 byte) + padding OBU (type
        // 15) behind: enumeration covers all metadata OBUs, skips the rest
        Files.write(dir.resolve("mixed.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(4, new byte[]{0x10, 0x11, 0x12}),
                        metaObu(3, new byte[]{1, 2, 3, 4, 5}),
                        metaObu(7, new byte[]{9, 9}),
                        metaObu(32, new byte[]{0x00}),
                        AvifObuSupport.obuRaw(15, new byte[4], true, false, false)))));
        return dir;
    }

    /** MSB-first bit packer for the metadata_timecode() bitfields. */
    static byte[] bitPack(int[][] fields) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int acc = 0;
        int nbits = 0;
        for (int[] f : fields) {
            int v = f[0];
            for (int i = f[1] - 1; i >= 0; i--) {
                acc = (acc << 1) | ((v >> i) & 1);
                nbits++;
                if (nbits == 8) {
                    out.write(acc);
                    acc = 0;
                    nbits = 0;
                }
            }
        }
        if (nbits > 0) out.write(acc << (8 - nbits));
        return out.toByteArray();
    }

    static Path errorFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        // cll payload too short (2 of the required 4 bytes)
        Files.write(dir.resolve("mtrunc.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(1, new byte[]{0x01, 0x02})))));
        // mdcv too short (12 of 24)
        Files.write(dir.resolve("mdcvshort.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(2, new byte[12])))));
        // t35 with zero payload bytes (country byte missing)
        Files.write(dir.resolve("t35short.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(4, new byte[]{})))));
        // timecode payload shorter than the 23-bit minimum (2 bytes)
        Files.write(dir.resolve("tcshort.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        metaObu(5, new byte[]{0x00, 0x00})))));
        // OBU size claims beyond the item
        byte[] bad = new byte[]{(byte) ((5 << 3) | 0x02), (byte) 200, 1, 0x01, 0x02};
        Files.write(dir.resolve("obutrun.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(), bad))));
        return dir;
    }

    static byte[] concatBytes(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    static String probe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifMeta

            String of(Bool v) {
                if (v) {
                    return "1"
                }
                return "0"
            }

            String mfacts(AvifObuMetadata m) {
                var s = m.type + " " + m.name + " pb=" + m.payloadBytes
                if (m.type == 4) {
                    s = s + " cc=" + m.country + " ext=" + of(m.extended) + " t35=" + m.t35Bytes
                }
                if (m.type == 1) {
                    s = s + " cll=" + m.maxCll + " fall=" + m.maxFall
                }
                if (m.type == 2) {
                    s = s + " mdcv="
                    for (var v in m.mdcv) {
                        s = s + "," + v
                    }
                }
                if (m.type == 5) {
                    s = s + " tc="
                    for (var v in m.timecode) {
                        s = s + "," + v
                    }
                }
                return s
            }

            main() {
                var base = "%s"
                var ms = readAvifMetadataObus(base + "/t35.avif")
                for (var m in ms) {
                    println("t35 " + mfacts(m))
                }
                ms = readAvifMetadataObus(base + "/t35x.avif")
                for (var m in ms) {
                    println("t35x " + mfacts(m))
                }
                ms = readAvifMetadataObus(base + "/cll.avif")
                for (var m in ms) {
                    println("cll " + mfacts(m))
                }
                ms = readAvifMetadataObus(base + "/mdcv.avif")
                for (var m in ms) {
                    println("mdcv " + mfacts(m))
                }
                ms = readAvifMetadataObus(base + "/tc.avif")
                for (var m in ms) {
                    println("tc " + mfacts(m))
                }
                ms = readAvifMetadataObus(base + "/tcf.avif")
                for (var m in ms) {
                    println("tcf " + mfacts(m))
                }
                ms = readAvifMetadataObus(base + "/mixed.avif")
                for (var m in ms) {
                    println("mixed " + mfacts(m))
                }
            }
            """.formatted(base);
    }

    static String errorProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifMeta

            main() {
                var base = "%s"
                try {
                    readAvifMetadataObus(base + "/mtrunc.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifMetadataObus(base + "/mdcvshort.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifMetadataObus(base + "/t35short.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifMetadataObus(base + "/tcshort.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifMetadataObus(base + "/obutrun.avif")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(base);
    }
}
