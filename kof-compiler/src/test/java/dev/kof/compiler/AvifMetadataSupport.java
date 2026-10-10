package dev.kof.compiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture builders + the independent second reader for AVIF slice 1
 * (image-vision front, plan §34). Split from {@link AvifMetadataE2ETest}
 * by the test-hygiene ratchet (precedent: {@code KofValidationSupport}).
 */
final class AvifMetadataSupport {

    private AvifMetadataSupport() {
    }

    // --- fixtures (byte-exact per AVIF/AV1-ISOBMFF/AV1 specs) ---------------

    // OBU_SEQUENCE_HEADER (type 1), header 0x0A (no ext, size field set,
    // no padding) + LEB size + payload whose first bytes carry:
    // seq_profile(3) seq_level_idx(5) [seq_tier(1)] show_existing_frame(1)
    // reduced_frame_header(1) + still picture(1, reduced form) +
    // operating_points_cnt_minus_1(5) = 0 + op level/tier + 32-bit timing
    // zeros. The remaining fields (colour config, frame sizes, ...) are NOT
    // read by slice 1 — the av1C record is the authority; the reduced-form
    // flag position is what the refusal test exercises.
    /** MSB-first bit writer for the spec-exact sequence header payload. */
    static final class Bits {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int cur, n;
        void bits(int v, int w) {
            for (int i = w - 1; i >= 0; i--) {
                cur = (cur << 1) | ((v >> i) & 1);
                n++;
                if (n == 8) {
                    out.write(cur);
                    cur = 0;
                    n = 0;
                }
            }
        }
        void pad() {
            if (n > 0) {
                out.write(cur << (8 - n));
                cur = 0;
                n = 0;
            }
        }
        byte[] bytes() {
            pad();
            return out.toByteArray();
        }
    }

    /** REDUCED form per AV1 5.5.1: profile(3) still(1) reduced(1) level(5)
     *  then color_config (5.5.2) + superres/cdef/restoration + film grain
     *  (absent=0) + trailing one bit. */
    static byte[] configObu(int profile, int level, boolean high, boolean twelve,
                            boolean mono) {
        return configObuCap(profile, level, high, twelve, mono, false, false, false);
    }

    /** configObu with the superres/cdef/restoration capability bits explicit
     *  (a non-lossless frame header needs cdef/restoration enabled to reach
     *  those tail branches). */
    static byte[] configObuCap(int profile, int level, boolean high, boolean twelve,
                               boolean mono, boolean superres, boolean cdef,
                               boolean restoration) {
        Bits w = new Bits();
        w.bits(profile, 3);
        w.bits(1, 1);           // still_picture (conformance for reduced)
        w.bits(1, 1);           // reduced_still_picture_header
        w.bits(level, 5);
        // reduced-form defaults (spec 5.5.1): 32x32 max frame, 5-bit id
        w.bits(4, 4);           // frame_width_bits_minus_1
        w.bits(4, 4);           // frame_height_bits_minus_1
        w.bits(31, 5);          // max_frame_width_minus_1  (32)
        w.bits(31, 5);          // max_frame_height_minus_1 (32)
        w.bits(0, 3);           // use_128x128/filter_intra/intra_edge
        w.bits(superres ? 1 : 0, 1);
        w.bits(cdef ? 1 : 0, 1);
        w.bits(restoration ? 1 : 0, 1);
        w.bits(high ? 1 : 0, 1);
        if (profile == 2 && high) {
            w.bits(twelve ? 1 : 0, 1);
        }
        if (profile != 1) {
            w.bits(mono ? 1 : 0, 1);
        }
        w.bits(0, 1);           // color_description_present = 0
        if (mono) {
            w.bits(0, 1);       // color_range
        } else if (profile == 2) {
            w.bits(0, 1);       // color_range
            w.bits(0, 1);       // subsampling_x (-> subY=0)
            w.bits(0, 1);       // separate_uv_delta_q
        } else {
            w.bits(0, 1);       // color_range
            w.bits(0, 1);       // separate_uv_delta_q
        }
        w.bits(0, 1);           // film_grain_params_present = 0
        w.bits(1, 1);           // trailing one bit pads the byte
        byte[] payload = w.bytes();
        byte[] out = new byte[2 + payload.length];
        out[0] = 0x0A;          // forbidden0 | type=1 | ext0 | size=1 | 0
        out[1] = (byte) payload.length;
        System.arraycopy(payload, 0, out, 2, payload.length);
        return out;
    }

    // av1C record: byte0 0x81 (marker+version), byte1 = profile<<5|level,
    // byte2 = tier<<7|high<<6|twelve<<5|mono<<4|subX<<3|subY<<2, byte3 = the
    // chroma_sample_position(2)+reserved(3)+initial_presentation_delay(3),
    // then configOBUs[] DIRECTLY (AV1-ISOBMFF 2.3.3) — no length prefix. The
    // array may be empty.
    static byte[] av1c(int profile, int level, boolean tier, boolean high,
                               boolean twelve, boolean mono, boolean subX, boolean subY,
                               byte[] obu) {
        int b2 = ((tier ? 1 : 0) << 7) | ((high ? 1 : 0) << 6)
                | ((twelve ? 1 : 0) << 5) | ((mono ? 1 : 0) << 4)
                | ((subX ? 1 : 0) << 3) | ((subY ? 1 : 0) << 2);
        byte[] rec = new byte[4 + obu.length];
        rec[0] = (byte) 0x81;
        rec[1] = (byte) ((profile << 5) | (level & 31));
        rec[2] = (byte) b2;
        rec[3] = 0;
        System.arraycopy(obu, 0, rec, 4, obu.length);
        return box("av1C", rec);
    }

    static byte[] ispe(int w, int h) {
        return box("ispe", new byte[]{0, 0, 0, 0,
                (byte) (w >>> 24), (byte) (w >>> 16), (byte) (w >>> 8), (byte) w,
                (byte) (h >>> 24), (byte) (h >>> 16), (byte) (h >>> 8), (byte) h});
    }

    static byte[] infe(int id, String type) {
        // version 2: ver+flags(4) item_ID(2) item_protection_index(2) item_type(4)
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[4 + 2 + 2 + 4];
        body[4] = (byte) (id >>> 8);
        body[5] = (byte) id;
        System.arraycopy(t, 0, body, 8, 4);
        return box("infe", body);
    }

    static byte[] pitm(int id) {
        return box("pitm", new byte[]{0, 0, 0, 0, (byte) (id >>> 8), (byte) id});
    }

    // iinf full box: ver+flags(4) entry_count(2) + the infe boxes as children
    static byte[] iinf(int count, List<byte[]> infeBoxes) {
        byte[] head = new byte[]{0, 0, 0, 0, (byte) (count >>> 8), (byte) count};
        return box("iinf", concatWith(head, infeBoxes));
    }

    static byte[] hdlr() {
        byte[] b = new byte[21];
        byte[] p = "pict".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(p, 0, b, 8, 4);
        return box("hdlr", b);
    }

    static byte[] iprp(List<byte[]> children, List<byte[]> entries) {
        // ipma v0: ver+flags(4) entry_count(4) + item_id(2) assoc_count(1) idx(1)*
        // entry_count is 32-bit (ISO 14496-12 8.11.4; cross-checked against
        // FFmpeg mov_read_iprp) — a 16-bit field here was a shared blind spot.
        int n = entries.size();
        byte[] head = new byte[]{0, 0, 0, 0, (byte) (n >>> 24), (byte) (n >>> 16),
                (byte) (n >>> 8), (byte) n};
        byte[] ipma = box("ipma", concatWith(head, entries));
        return box("iprp", concat(List.of(box("ipco", concat(children)), ipma)));
    }

    // one ipma entry: item id + 1-based property indexes into ipco
    static byte[] ipmaEntry(int id, int... idx) {
        byte[] b = new byte[2 + 1 + idx.length];
        b[0] = (byte) (id >>> 8);
        b[1] = (byte) id;
        b[2] = (byte) idx.length;
        for (int i = 0; i < idx.length; i++) b[3 + i] = (byte) idx[i];
        return b;
    }

    static byte[] metaChildless(String type, byte[] body) {
        return box("meta", body);
    }

    // iref is a FullBox (ver+flags) whose payload is a LIST of reference
    // boxes (ISO 14496-12 8.11.12): size(4) type(4) from_id(2) ref_count(2)
    // to_id(2*ref_count). AVIF 4.1 uses an `auxl` entry from the aux item to
    // the primary; the aux type lives in the auxC property.
    static byte[] irefAuxl(int auxItem, int primary) {
        byte[] refBody = new byte[2 + 2 + 2];
        refBody[0] = (byte) (auxItem >>> 8);
        refBody[1] = (byte) auxItem;
        refBody[2] = 0;
        refBody[3] = 1; // reference_count
        refBody[4] = (byte) (primary >>> 8);
        refBody[5] = (byte) primary;
        return box("iref", concatWith(new byte[]{0, 0, 0, 0}, List.of(box("auxl", refBody))));
    }

    // AuxiliaryTypeProperty ('auxC'): FullBox(ver+flags) then the
    // NUL-terminated aux_type URN.
    static byte[] auxc(String urn) {
        byte[] u = (urn + "\0").getBytes(StandardCharsets.US_ASCII);
        return box("auxC", concatWith(new byte[]{0, 0, 0, 0}, List.of(u)));
    }

    static byte[] container(String major, List<byte[]> metaChildren) {
        return containerWithCompat(major, List.of("mif1"), metaChildren);
    }

    static byte[] containerWithCompat(String major, List<String> compat,
                                              List<byte[]> metaChildren) {
        List<byte[]> brands = new java.util.ArrayList<>();
        brands.add(major.getBytes(StandardCharsets.US_ASCII));
        brands.add(new byte[]{0, 0, 0, 2});
        for (String c : compat) {
            brands.add(c.getBytes(StandardCharsets.US_ASCII));
        }
        byte[] ftyp = box("ftyp", concat(brands));
        byte[] metaBox = box("meta", concatWith(new byte[]{0, 0, 0, 0}, metaChildren));
        return concat(List.of(ftyp, metaBox));
    }

    static int sum(List<byte[]> parts) {
        int s = 0;
        for (byte[] b : parts) s += b.length;
        return s;
    }

    static byte[] concat(List<byte[]> parts) {
        return concatWith(new byte[0], parts);
    }

    static byte[] concatWith(byte[] head, List<byte[]> parts) {
        byte[] out = new byte[head.length + sum(parts)];
        int p = 0;
        System.arraycopy(head, 0, out, p, head.length);
        p += head.length;
        for (byte[] b : parts) {
            System.arraycopy(b, 0, out, p, b.length);
            p += b.length;
        }
        return out;
    }

    static byte[] box(String type, byte[] body) {
        int size = 8 + body.length;
        byte[] out = new byte[size];
        out[0] = (byte) (size >>> 24);
        out[1] = (byte) (size >>> 16);
        out[2] = (byte) (size >>> 8);
        out[3] = (byte) size;
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(t, 0, out, 4, 4);
        System.arraycopy(body, 0, out, 8, body.length);
        return out;
    }

    static Path fixtures(Path dir) throws Exception {
        Files.createDirectories(dir);

        byte[] obu0 = configObu(0, 2, false, false, true);
        byte[] flat = container("avif", List.of(
                av1c(0, 2, false, false, false, true, false, false, obu0),
                ispe(8, 8),
                pitm(1),
                iinf(1, List.of(infe(1, "av01"))),
                hdlr(),
                iprp(List.of(av1c(0, 2, false, false, false, true, false, false, obu0), ispe(8, 8)), List.of(ipmaEntry(1, 1, 2)))));
        Files.write(dir.resolve("flat.avif"), flat);

        // Two av1C properties (colour + alpha), two ispe, an auxC and an
        // `auxl` iref: the primary selects {ispe, colour av1C}, the alpha item
        // selects {auxC, alpha av1C, ispe}. A reader that ignores ipma and
        // requires a unique av1C must refuse this file.
        byte[] obu1 = configObu(1, 4, true, false, false);
        byte[] obuA = configObu(0, 2, false, false, true);
        byte[] alpha = container("avif", List.of(
                av1c(1, 4, false, true, false, false, true, true, obu1),
                ispe(16, 16),
                pitm(1),
                iinf(2, List.of(infe(1, "av01"), infe(2, "av01"))),
                hdlr(),
                irefAuxl(2, 1),
                iprp(List.of(
                                ispe(16, 16),
                                av1c(1, 4, false, true, false, false, true, true, obu1),
                                auxc("urn:mpeg:mpegB:cicp:systems:auxiliary:alpha"),
                                av1c(0, 2, false, false, false, true, false, false, obuA),
                                ispe(16, 16)),
                        List.of(ipmaEntry(1, 1, 2), ipmaEntry(2, 3, 4, 5)))));
        Files.write(dir.resolve("alpha.avif"), alpha);

        byte[] obu2 = configObu(2, 0, true, true, false);
        byte[] compat = containerWithCompat("mif1", List.of("avis", "mif1"), List.of(
                av1c(2, 0, true, true, true, false, false, false, obu2),
                ispe(32, 24),
                pitm(7),
                iinf(1, List.of(infe(7, "av01"))),
                hdlr(),
                iprp(List.of(av1c(2, 0, true, true, true, false, false, false, obu2), ispe(32, 24)), List.of(ipmaEntry(7, 1, 2)))));
        Files.write(dir.resolve("compat.avif"), compat);
        return dir;
    }

    static byte[] configObuNotReduced() {
        Bits w = new Bits();
        w.bits(0, 3);       // seq_profile
        w.bits(0, 1);       // still_picture = 0
        w.bits(0, 1);       // reduced_still_picture_header = 0  -> slice 1 refuses
        w.bits(0, 1);       // timing_info_present_flag = 0
        w.bits(0, 1);       // initial_display_delay_present = 0
        w.bits(0, 5);       // operating_points_cnt_minus_1
        w.bits(0, 12);      // operating_point_idc[0] (f(12), AV1 5.5.1)
        w.bits(2, 5);       // seq_level_idx[0]
        w.bits(0, 4);       // frame_width_bits_minus_1
        w.bits(0, 4);       // frame_height_bits_minus_1
        w.bits(7, 4);       // max_frame_width_minus_1
        w.bits(7, 4);       // max_frame_height_minus_1
        w.bits(0, 1);       // frame_id_numbers_present
        w.bits(0, 3);       // 128x128 / filter_intra / intra_edge
        w.bits(0, 1); w.bits(0, 1); w.bits(0, 1); w.bits(0, 1); w.bits(0, 1); // inter block
        w.bits(1, 1);       // seq_choose_screen_content_tools
        w.bits(1, 1);       // seq_choose_integer_mv
        w.bits(0, 1); w.bits(0, 1); w.bits(0, 1); // superres/cdef/restoration
        w.bits(0, 1);       // high_bitdepth
        w.bits(1, 1);       // mono_chrome
        w.bits(0, 1);       // color_description_present
        w.bits(0, 1);       // color_range
        w.bits(0, 1);       // film_grain
        w.bits(1, 1);       // trailing one bit
        byte[] payload = w.bytes();
        byte[] out = new byte[2 + payload.length];
        out[0] = 0x0A;
        out[1] = (byte) payload.length;
        System.arraycopy(payload, 0, out, 2, payload.length);
        return out;
    }

    static String probe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.Avif

            String flag(Bool b) {
                if (b) {
                    return "1"
                }
                return "0"
            }

            main() {
                var base = "%s"
                var files = listOf("flat.avif", "alpha.avif", "compat.avif")
                for (var name in files) {
                    var m = readAvifMetadata(base + "/" + name)
                    println(m.brand + " " + m.width + "x" + m.height
                        + " items=" + m.itemCount + " primary=" + m.primaryId
                        + " alpha=" + flag(m.hasAlpha) + " profile=" + m.seqProfile
                        + " level=" + m.levelIdx + " tier=" + flag(m.seqTier)
                        + " mono=" + flag(m.monochrome) + " sub=" + flag(m.chromaSubX)
                        + "/" + flag(m.chromaSubY) + " depth=" + m.bitDepthLuma
                        + "/" + m.bitDepthChroma)
                }
            }
            """.formatted(base);
    }

    static String errorProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.Avif

            main() {
                var base = "%s"
                var files = listOf("notisobmf.avif", "heic.avif", "nonreduced.avif",
                    "baddepth.avif", "trunc.avif")
                for (var name in files) {
                    try {
                        var m = readAvifMetadata(base + "/" + name)
                        println(m.brand)
                    } catch (String e) {
                        println(e)
                    }
                }
            }
            """.formatted(base);
    }

    static Path errorFixtures(Path root) throws Exception {
        Files.createDirectories(root);
        Path dir = root.resolve("errors-fixtures");
        Files.createDirectories(dir);

        byte[] jpeg = new byte[64];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        Files.write(dir.resolve("notisobmf.avif"), jpeg);

        byte[] heic = container("heic", List.of(
                av1c(0, 2, false, false, false, true, false, false, configObu(0, 2, false, false, true)),
                ispe(8, 8),
                pitm(1),
                iinf(1, List.of(infe(1, "av01"))),
                hdlr(),
                iprp(List.of(av1c(0, 2, false, false, false, true, false, false, configObu(0, 2, false, false, true)), ispe(8, 8)), List.of(ipmaEntry(1, 1, 2)))));
        Files.write(dir.resolve("heic.avif"), heic);

        byte[] nonReduced = container("avif", List.of(
                av1c(0, 2, false, false, false, true, false, false, configObuNotReduced()),
                ispe(8, 8),
                pitm(1),
                iinf(1, List.of(infe(1, "av01"))),
                hdlr(),
                iprp(List.of(av1c(0, 2, false, false, false, true, false, false, configObuNotReduced()), ispe(8, 8)), List.of(ipmaEntry(1, 1, 2)))));
        Files.write(dir.resolve("nonreduced.avif"), nonReduced);

        byte[] badDepth = container("avif", List.of(
                av1c(0, 2, false, true, false, true, false, false, configObu(0, 2, false, false, true)),
                ispe(8, 8),
                pitm(1),
                iinf(1, List.of(infe(1, "av01"))),
                hdlr(),
                iprp(List.of(av1c(0, 2, false, true, false, true, false, false, configObu(0, 2, false, false, true)), ispe(8, 8)), List.of(ipmaEntry(1, 1, 2)))));
        Files.write(dir.resolve("baddepth.avif"), badDepth);

        byte[] truncated = new byte[8];
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, truncated, 4, 4);
        Files.write(dir.resolve("trunc.avif"), truncated);

        return root.resolve("errors-fixtures");
    }

    // --- independent box helpers for the second reader ----------------------

    static int be16(byte[] b, int i) {
        return ((b[i] & 255) << 8) | (b[i + 1] & 255);
    }

    static int be32(byte[] b, int i) {
        return ((b[i] & 255) << 24) | ((b[i + 1] & 255) << 16)
                | ((b[i + 2] & 255) << 8) | (b[i + 3] & 255);
    }

    static int boxEnd(byte[] b, int at) {
        int s = be32(b, at);
        assertTrue(s >= 8 && at + s <= b.length, "box size");
        return at + s;
    }

    static int findBox(byte[] b, int from, int to, String type) {
        int p = from;
        while (p + 8 <= to) {
            int s = be32(b, p);
            if (s < 8 || p + s > to) {
                return -1;
            }
            if (new String(b, p + 4, 4, StandardCharsets.US_ASCII).equals(type)) {
                return p;
            }
            p += s;
        }
        return -1;
    }
}
