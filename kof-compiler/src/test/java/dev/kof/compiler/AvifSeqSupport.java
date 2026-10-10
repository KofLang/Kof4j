package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Fixtures + independent second reader for AVIF slice 2b (sequence header walk). */
final class AvifSeqSupport {

    private AvifSeqSupport() {}

    /** OBU wrapper: forbidden0/type1/ext0/has-size + one-byte length. */
    static int bitsFor(int v) {
        int n = 1;
        while ((1 << n) <= v) {
            n++;
        }
        return n;
    }

    static byte[] obu(byte[] payload) {
        return obuType(1, payload);
    }

    static byte[] obuType(int type, byte[] payload) {
        byte[] out = new byte[2 + payload.length];
        out[0] = (byte) ((type << 3) | 0x02);
        out[1] = (byte) payload.length;
        System.arraycopy(payload, 0, out, 2, payload.length);
        return out;
    }

    static byte[] delimiter() {
        return obuType(2, new byte[0]);
    }

    /**
     * NON-REDUCED sequence header per AV1 5.5.1 with the mono color_config
     * path (no 4:2:0): profile, still=0, reduced=0, timing flag (and 64 bits
     * when set), decoder-model flag, display-delay flag, one operating
     * point, frame sizes, no frame ids, zeroed capability bits, color config
     * mono, trailing bits.
     */
    static byte[] nrMonoObu(int profile, int level, boolean high, boolean twelve,
                            boolean timing, boolean model, boolean film,
                            int frameW, int frameH) {
        return nrMonoObuType(1, profile, level, high, twelve, timing, model, film, frameW, frameH);
    }

    /** Same walk, arbitrary OBU type (for slice 2c item-stream fixtures). */
    static byte[] nrMonoObuType(int type, int profile, int level, boolean high, boolean twelve,
                            boolean timing, boolean model, boolean film,
                            int frameW, int frameH) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(profile, 3);
        w.bits(0, 1);                       // still_picture
        w.bits(0, 1);                       // reduced_still_picture_header
        w.bits(timing ? 1 : 0, 1);
        if (timing) {
            w.bits(30, 32);                 // num_units_in_display_tick
            w.bits(1, 32);                  // time_scale
            w.bits(0, 1);                   // equal_picture_interval
            w.bits(model ? 1 : 0, 1);       // decoder_model_info_present_flag
                                            // (per spec: read only when
                                            //  timing_info_present_flag = 1)
        }
        if (timing && model) {
            w.bits(0, 5);                   // buffer_delay_length_minus_1
            w.bits(0, 32);                  // num_units_in_decoding_tick
            w.bits(0, 5);                   // buffer_removal_time_length...
            w.bits(0, 5);                   // frame_presentation_time_length...
            w.bits(0, 1);                   // decoder_model_present_for_this_op
        }
        w.bits(0, 1);                       // initial_display_delay_present_flag
        w.bits(0, 5);                       // operating_points_cnt_minus_1
        w.bits(0, 12);                      // operating_point_idc[0] (f(12), AV1 5.5.1)
        w.bits(level, 5);                   // seq_level_idx[0]
        if (level > 7) {
            w.bits(0, 1);                   // seq_tier
        }
        int wb = bitsFor(frameW - 1);
        int hb = bitsFor(frameH - 1);
        w.bits(wb - 1, 4);                  // frame_width_bits_minus_1
        w.bits(hb - 1, 4);                  // frame_height_bits_minus_1
        w.bits(frameW - 1, wb);             // max_frame_width_minus_1
        w.bits(frameH - 1, hb);             // max_frame_height_minus_1
        w.bits(0, 1);                       // frame_id_numbers_present
        w.bits(0, 3);                       // 128x128/filter_intra/intra_edge
        w.bits(0, 5);                       // inter block + order hint off
        w.bits(1, 1);                       // seq_choose_screen_content_tools
        w.bits(1, 1);                       // seq_choose_integer_mv
        w.bits(0, 3);                       // superres/cdef/restoration
        w.bits(high ? 1 : 0, 1);            // high_bitdepth
        if (profile == 2 && high) {
            w.bits(twelve ? 1 : 0, 1);      // twelve_bit
        }
        w.bits(1, 1);                       // mono_chrome
        w.bits(0, 1);                       // color_description_present
        w.bits(0, 1);                       // color_range
        w.bits(film ? 1 : 0, 1);            // film_grain_params_present
        w.bits(1, 1);                       // trailing one bit
        return obuType(type, w.bytes());
    }

    static String javaSeqFacts(Path file) throws Exception {
        byte[] b = Files.readAllBytes(file);
        int[] ps = javaSeqObuRange(b);
        return javaSeqCore(b, ps[0], ps[1]);
    }

    static int[] javaSeqObuRange(byte[] b) {
        int meta = AvifMetadataSupport.findBox(b, 0, b.length, "meta");
        int metaEnd = AvifMetadataSupport.boxEnd(b, meta);
        int av1At = AvifMetadataSupport.findBox(b, meta + 12, metaEnd, "av1C");
        int iprp = AvifMetadataSupport.findBox(b, meta + 12, metaEnd, "iprp");
        if (av1At < 0) {
            av1At = AvifMetadataSupport.findBox(b, AvifMetadataSupport.boxEnd(b, iprp) - 8 + 0, iprp, "av1C");
        }
        int rec = av1At + 8;
        // configOBUs[] follow the 4-byte record directly (AV1-ISOBMFF 2.3.3).
        int ext = (b[rec + 4] >> 2) & 1;
        int p = rec + 5 + ext;
        int size = 0;
        for (int i = 0; i < 8; i++) {
            int x = b[p] & 255;
            p++;
            size = (size << 7) | (x & 127);
            if ((x & 128) == 0) {
                break;
            }
        }
        return new int[]{p, p + size};
    }

    static String javaSeqCore(byte[] b, int p, int limit) {
        int bits = p * 8;
        int profile = readBits(b, bits, 3); bits += 3;
        if (profile > 2) throw new AssertionError("prof3");
        int still = readBits(b, bits, 1); bits += 1;
        int reduced = readBits(b, bits, 1); bits += 1;
        if (reduced == 1) {
            bits += 5;
        } else {
            int timing = readBits(b, bits, 1); bits += 1;
            if (timing == 1) {
                bits += 64;
                int equal = readBits(b, bits, 1); bits += 1;
                if (equal == 1) bits += uvlcBits(b, bits);
                int dm = readBits(b, bits, 1); bits += 1;
                if (dm == 1) throw new AssertionError("model");
            }
            int delay = readBits(b, bits, 1); bits += 1;
            int cnt = readBits(b, bits, 5); bits += 5;
            for (int i = 0; i <= cnt; i++) {
                bits += 12; // operating_point_idc[i] (f(12), AV1 5.5.1)
                int lv = readBits(b, bits, 5); bits += 5;
                if (lv > 7) bits += 1;
                if (delay == 1) {
                    int d = readBits(b, bits, 1); bits += 1;
                    if (d == 1) bits += 4;
                }
            }
        }
        int wb = readBits(b, bits, 4) + 1; bits += 4;
        int hb = readBits(b, bits, 4) + 1; bits += 4;
        int maxW = readBits(b, bits, wb) + 1; bits += wb;
        int maxH = readBits(b, bits, hb) + 1; bits += hb;
        if (reduced == 0) {
            int fid = readBits(b, bits, 1); bits += 1;
            if (fid == 1) bits += 7;
        }
        bits += 3;
        if (reduced == 0) {
            bits += 4;
            int oh = readBits(b, bits, 1); bits += 1;
            if (oh == 1) bits += 2;
            int csct = readBits(b, bits, 1); bits += 1;
            int fsct = 1;
            if (csct == 0) {
                fsct = readBits(b, bits, 1); bits += 1;
            }
            if (fsct > 0) {
                int cmv = readBits(b, bits, 1); bits += 1;
                if (cmv == 0) bits += 1;
            }
            if (oh == 1) bits += 3;
        }
        bits += 3;
        int high = readBits(b, bits, 1); bits += 1;
        int depth = 8;
        if (profile == 2 && high == 1) {
            depth = readBits(b, bits, 1) == 1 ? 12 : 10; bits += 1;
        } else if (profile <= 2 && high == 1) {
            depth = 10;
        }
        int mono = 0;
        if (profile != 1) {
            mono = readBits(b, bits, 1); bits += 1;
        }
        int desc = readBits(b, bits, 1); bits += 1;
        int primaries = 2, transfer = 2, matrix = 2;
        if (desc == 1) {
            primaries = readBits(b, bits, 8); bits += 8;
            transfer = readBits(b, bits, 8); bits += 8;
            matrix = readBits(b, bits, 8); bits += 8;
        }
        int subX = 0, subY = 0;
        if (mono == 1) {
            bits += 1;
            subX = 1; subY = 1;
        } else {
            if (!(primaries == 1 && transfer == 12 && matrix == 0)) {
                bits += 1;
                if (profile == 0) { subX = 1; subY = 1; }
                else if (profile == 1) { subX = 0; subY = 0; }
                else if (depth == 12) {
                    subX = readBits(b, bits, 1); bits += 1;
                    subY = 0;
                    if (subX == 1) { subY = readBits(b, bits, 1); bits += 1; }
                } else { subX = 1; subY = 0; }
                if (subX == 1 && subY == 1) bits += 2;
            }
            bits += 1; // separate_uv_delta_q
        }
        int film = readBits(b, bits, 1); bits += 1;
        if (bits > limit * 8) throw new AssertionError("trunc");
        if (film == 1) throw new AssertionError("film");
        return "p=" + profile + " r=" + reduced + " w=" + maxW + " h=" + maxH
                + " mono=" + mono + " sub=" + subX + "/" + subY
                + " depth=" + depth + " still=" + still;
    }

    static int readBits(byte[] b, int bitPos, int n) {
        int v = 0;
        for (int i = 0; i < n; i++) {
            int p = bitPos + i;
            v = (v << 1) | ((b[p >> 3] >> (7 - (p & 7))) & 1);
        }
        return v;
    }

    static int uvlcBits(byte[] b, int bitPos) {
        int zeros = 0;
        while (readBits(b, bitPos + zeros, 1) == 0) {
            zeros++;
        }
        return zeros + 1 + (zeros > 0 ? zeros : 0);
    }

    static Path fixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        // non-reduced mono, profile 0, 8x8, depth 8
        byte[] obu0 = nrMonoObu(0, 2, false, false, false, false, false, 8, 8);
        Files.write(dir.resolve("nr8.avif"), containerWith(obu0, 0, 2, false, false,
                true, false, false, 8, 8));
        // non-reduced mono, profile 2, depth 12, with timing info, 32x24
        byte[] obu1 = nrMonoObu(2, 12, true, true, true, false, false, 32, 24);
        Files.write(dir.resolve("nr12t.avif"), containerWith(obu1, 2, 12, false, true,
                true, false, false, 32, 24));
        // decoder model info present -> refusal
        byte[] obu2 = nrMonoObu(0, 2, false, false, true, true, false, 8, 8);
        Files.write(dir.resolve("model.avif"), containerWith(obu2, 0, 2, false, false,
                true, false, false, 8, 8));
        // film grain present -> refusal
        byte[] obu3 = nrMonoObu(0, 2, false, false, false, false, true, 8, 8);
        Files.write(dir.resolve("film.avif"), containerWith(obu3, 0, 2, false, false,
                true, false, false, 8, 8));
        // profile 3 -> refusal
        byte[] obu4 = nrMonoObu(3, 2, false, false, false, false, false, 8, 8);
        Files.write(dir.resolve("prof3.avif"), containerWith(obu4, 3, 2, false, false,
                true, false, false, 8, 8));
        Files.write(dir.resolve("flat.avif"),
                containerWith(AvifMetadataSupport.configObu(0, 2, false, false, true),
                        0, 2, false, false, true, false, false, 8, 8));
        // reduced profile 1 (the form real AVIF stills use: 4:2:0, no mono
        // bit, no chroma_sample_position) — the 3-bit seq_profile regression.
        byte[] obu5 = AvifMetadataSupport.configObu(1, 2, false, false, false);
        Files.write(dir.resolve("r1.avif"),
                containerWith(obu5, 1, 2, false, false, false, false, false, 32, 32));
        // empty configOBUs: a legal AVIF still image whose sequence header is
        // in the item data — the av1C face refuses it explicitly (the item
        // path is readAvifItemObus).
        Files.write(dir.resolve("noconf.avif"),
                containerWith(new byte[0], 0, 2, false, false, true, false, false, 8, 8));
        return dir;
    }

    static byte[] containerWith(byte[] obu, int profile, int level, boolean tier,
                                boolean high, boolean mono, boolean subX, boolean subY,
                                int w, int h) {
        return AvifMetadataSupport.container("avif", List.of(
                AvifMetadataSupport.av1c(profile, level, tier, high,
                        profile == 2 && high, mono, subX, subY, obu),
                AvifMetadataSupport.ispe(w, h),
                AvifMetadataSupport.pitm(1),
                AvifMetadataSupport.iinf(1, List.of(AvifMetadataSupport.infe(1, "av01"))),
                AvifMetadataSupport.hdlr(),
                AvifMetadataSupport.iprp(List.of(
                        AvifMetadataSupport.av1c(profile, level, tier, high,
                                profile == 2 && high, mono, subX, subY, obu),
                        AvifMetadataSupport.ispe(w, h)),
                        List.of(AvifMetadataSupport.ipmaEntry(1, 1, 2)))));
    }

    static String probe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifSeq

            String of(Bool v) {
                if (v) {
                    return "1"
                }
                return "0"
            }

            main() {
                var base = "%s"
                var m = readAvifSeqHeader(base + "/nr8.avif")
                println("nr8 p=" + m.seqProfile + " r=" + of(m.reduced)
                    + " w=" + m.maxWidth + " h=" + m.maxHeight
                    + " mono=" + of(m.monochrome) + " sub=" + of(m.subsamplingX)
                    + "/" + of(m.subsamplingY) + " depth=" + m.bitDepth
                    + " still=" + of(m.stillPicture))
                var t = readAvifSeqHeader(base + "/nr12t.avif")
                println("nr12t p=" + t.seqProfile + " r=" + of(t.reduced)
                    + " w=" + t.maxWidth + " h=" + t.maxHeight
                    + " mono=" + of(t.monochrome) + " sub=" + of(t.subsamplingX)
                    + "/" + of(t.subsamplingY) + " depth=" + t.bitDepth
                    + " still=" + of(t.stillPicture))
                var f = readAvifSeqHeader(base + "/flat.avif")
                println("flat p=" + f.seqProfile + " r=" + of(f.reduced)
                    + " w=" + f.maxWidth + " h=" + f.maxHeight
                    + " mono=" + of(f.monochrome) + " sub=" + of(f.subsamplingX)
                    + "/" + of(f.subsamplingY) + " depth=" + f.bitDepth
                    + " still=" + of(f.stillPicture))
                var r1 = readAvifSeqHeader(base + "/r1.avif")
                println("r1 p=" + r1.seqProfile + " r=" + of(r1.reduced)
                    + " w=" + r1.maxWidth + " h=" + r1.maxHeight
                    + " mono=" + of(r1.monochrome) + " sub=" + of(r1.subsamplingX)
                    + "/" + of(r1.subsamplingY) + " depth=" + r1.bitDepth
                    + " still=" + of(r1.stillPicture))
            }
            """.formatted(base);
    }

    static String errorProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifSeq

            main() {
                var base = "%s"
                try {
                    readAvifSeqHeader(base + "/model.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifSeqHeader(base + "/film.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifSeqHeader(base + "/prof3.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifSeqHeader(base + "/noconf.avif")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(base);
    }
}
