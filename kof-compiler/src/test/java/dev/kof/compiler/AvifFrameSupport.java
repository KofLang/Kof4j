package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

/** Fixtures + independent second reader for AVIF slice 2d (frame header prefix). */
final class AvifFrameSupport {

    private AvifFrameSupport() {}

    /**
     * Reduced-frame prefix per AV1 5.9.2/5.9.5/5.9.6/5.9.15 (writer mirrors
     * the spec bit order, no guards where the spec has none):
     * disable_cdf, allow_sct, [force_mv], [size override + coded sizes],
     * render flag [+16+16], [intrabc], uniform_tile_spacing_flag,
     * [inc_tile_cols_log2, inc_tile_rows_log2], trailing one bit.
     * configObu(0,2,...) is 32x32 (sbCols=sbRows=1 -> maxCols=maxRows=1,
     * minCols=0, minTiles=0): the single loop iteration reads a STOP bit;
     * a 1 there reaches the cap (no further stop). redSeq128() (128x128,
     * sbCols=8) allows incs up to 3 with stops at the cap ends.
     */
    static byte[] frameReduced(int cdf, int sct, int mv, int ov, int codedW, int codedH,
                               int render, int rw16, int rh16, int intrabc, int obuType,
                               int uniform, int incCols, int incRows,
                               int sbCols, int sbRows, int use128) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(cdf, 1);
        w.bits(sct, 1);
        if (sct == 1) {
            w.bits(mv, 1);
        }
        // 5.9.2: reduced_still_picture_header INFERS frame_size_override_flag
        // = 0 (no bit); SWITCH infers 1; only other forms read it.
        w.bits(render, 1);
        if (render == 1) {
            w.bits(rw16, 16);
            w.bits(rh16, 16);
        }
        if (sct == 1) {
            w.bits(intrabc, 1);
        }
        if (uniform == 1) {
            uniformTiles(w, sbCols, sbRows, use128, incCols, incRows);
        } else if (uniform == 0) {
            w.bits(0, 1);                               // uniform_tile_spacing_flag = 0
        }
        tailLossless(w, 1, 1);
        if (obuType == 6) {
            // frame_obu: no trailing bits; the inline tile group follows the
            // byte_alignment (w.bytes() zero-pads = byte_alignment).
            return AvifSeqSupport.obuType(6, w.bytes());
        }
        w.bits(1, 1);                       // frame_header_obu trailing one bit
        return AvifSeqSupport.obuType(obuType, w.bytes());
    }

    /** tile_info() UNIFORM path (5.9.15) for a reduced frame: the
     *  increment_tile_cols_log2 / increment_tile_rows_log2 loops start at the
     *  spec minima and emit `incCols`/`incRows` ones; a stop zero is written
     *  only while the running log2 is below the per-axis cap. */
    static void uniformTiles(AvifMetadataSupport.Bits w, int sbCols, int sbRows,
                             int use128, int incCols, int incRows) {
        int sbShift = use128 == 1 ? 5 : 4;
        int sbSize = sbShift + 2;
        int maxTileWidthSb = 4096 >> sbSize;
        int maxTileAreaSb = 9437184 >> (2 * sbSize);
        int minCols = tlog2(maxTileWidthSb, sbCols);
        int maxCols = tlog2(1, Math.min(sbCols, 64));
        int maxRows = tlog2(1, Math.min(sbRows, 64));
        int minTiles = Math.max(minCols, tlog2(maxTileAreaSb, sbRows * sbCols));
        int colsLog2 = minCols + incCols;
        w.bits(1, 1);                                   // uniform_tile_spacing_flag
        for (int i = 0; i < incCols; i++) {
            w.bits(1, 1);
        }
        if (colsLog2 < maxCols) {
            w.bits(0, 1);                               // cols loop stop
        }
        int rowsLog2 = Math.max(minTiles - colsLog2, 0) + incRows;
        for (int i = 0; i < incRows; i++) {
            w.bits(1, 1);
        }
        if (rowsLog2 < maxRows) {
            w.bits(0, 1);                               // rows loop stop
        }
        if (colsLog2 > 0 || rowsLog2 > 0) {
            w.bits(0, colsLog2 + rowsLog2);             // context_update_tile_id
            w.bits(0, 2);                               // tile_size_bytes_minus_1
        }
    }

    /** Reduced mono frame with a NON-lossless header tail: base_q_idx = 32,
     *  no delta/segmentation, loop_filter_level = 7/7, sharpness 0, no lf
     *  delta, cdef damping 1 / cdef_bits 0 (one 4+2-bit Y entry), no
     *  restoration, tx_mode_select 1, reduced_tx_set 0. Pairs with
     *  {@code configObuCap(..., cdef=true, restoration=false)}. */
    static byte[] frameQ32() {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(1, 1);                       // disable_cdf
        w.bits(0, 1);                       // allow_sct = 0
        w.bits(0, 1);                       // render_and_frame_size_different = 0
        w.bits(1, 1);                       // uniform_tile_spacing_flag (1x1)
        w.bits(32, 8);                      // base_q_idx
        w.bits(0, 1);                       // DeltaQYDc coded flag = 0
        w.bits(0, 1);                       // using_qmatrix = 0
        w.bits(0, 1);                       // segmentation_enabled = 0
        // base_q_idx > 0 -> delta_q_params; delta_q_present = 0 so
        // delta_lf_params is NOT read (it is guarded by delta_q_present)
        w.bits(0, 1);                       // delta_q_present = 0
        w.bits(7, 6);                       // loop_filter_level[0]
        w.bits(7, 6);                       // loop_filter_level[1]
        w.bits(0, 3);                       // sharpness (mono: no UV)
        w.bits(0, 1);                       // loop_filter_delta_enabled = 0
        w.bits(1, 2);                       // cdef_damping_minus_3
        w.bits(0, 2);                       // cdef_bits = 0 -> one entry
        w.bits(0, 4);                       // cdef_y_pri_strength
        w.bits(0, 2);                       // cdef_y_sec_strength
        w.bits(1, 1);                       // tx_mode_select = 1
        w.bits(0, 1);                       // reduced_tx_set
        w.bits(1, 1);                       // frame_header_obu trailing one bit
        return AvifSeqSupport.obuType(3, w.bytes());
    }

    private static int tlog2(int u, int v) {
        int k = 0;
        while ((u << k) < v) {
            k++;
        }
        return k;
    }

    /** ns(n) writer (AV1 4.10.6): w = FloorLog2(n)+1, m = (1<<w)-n; value < m
     *  is written as f(w-1), otherwise v = (value+m)>>1 in f(w-1) then the
     *  low bit as the extra bit. */
    static void writeNs(AvifMetadataSupport.Bits w, int n, int value) {
        int wbits = floorLog2(n) + 1;
        int m = (1 << wbits) - n;
        if (value < m) {
            w.bits(value, wbits - 1);
        } else {
            int v = (value + m) >> 1;
            int extra = (value + m) & 1;
            w.bits(v, wbits - 1);
            w.bits(extra, 1);
        }
    }

    private static int floorLog2(int n) {
        int k = 0;
        int v = 1;
        while (v <= n / 2) {
            v <<= 1;
            k++;
        }
        return k;
    }

    /**
     * Reduced mono KEY frame with a NON-uniform tile_info() (AV1 5.9.15):
     * disable_cdf=1, allow_sct=0, render=0, uniform_tile_spacing_flag=0, the
     * col size list {1,1} over sbCols=2 and the row size list {2} over
     * sbRows=2 (redSeq128 is 128x128, 16px superblocks -> 2x2), so TileCols=2,
     * TileRows=1, colsLog2=1 -> a 1-bit context_update_tile_id and a 1-byte
     * tile size field; lossless tail.
     */
    static byte[] frameNonUniform() {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(1, 1);                       // disable_cdf
        w.bits(0, 1);                       // allow_sct = 0
        w.bits(0, 1);                       // render_and_frame_size_different = 0
        w.bits(0, 1);                       // uniform_tile_spacing_flag = 0
        writeNs(w, 2, 0);                   // width_in_sbs_minus_1 = 0 (size 1)
        writeNs(w, 1, 0);                   // width_in_sbs_minus_1 = 0 (size 1)
        writeNs(w, 2, 1);                   // height_in_sbs_minus_1 = 1 (size 2)
        w.bits(0, 1);                       // context_update_tile_id (1 bit)
        w.bits(0, 2);                       // tile_size_bytes_minus_1 = 0
        tailLossless(w, 0, 1);
        w.bits(1, 1);                       // frame_header_obu trailing one bit
        return AvifSeqSupport.obuType(3, w.bytes());
    }

    /**
     * uncompressed_header tail per AV1 5.9.2 with CodedLossless = 1 (base_q_idx
     * = 0): quantization_params (base_q_idx f(8), the per-plane read_delta_q
     * flags, using_qmatrix f(1)), segmentation_enabled f(1) = 0, no
     * delta_q_params (base_q_idx == 0), and — because lossless — no
     * loop_filter/cdef/restoration bits and no tx_mode_select; then
     * reduced_tx_set f(1). numPlanes selects the UV delta flags.
     */
    static void tailLossless(AvifMetadataSupport.Bits w, int reducedTxSet, int numPlanes) {
        w.bits(0, 8);           // base_q_idx = 0 -> CodedLossless
        w.bits(0, 1);           // DeltaQYDc coded flag = 0
        if (numPlanes > 1) {
            w.bits(0, 1);       // DeltaQUDc coded flag = 0
            w.bits(0, 1);       // DeltaQUAc coded flag = 0
        }
        w.bits(0, 1);           // using_qmatrix = 0
        w.bits(0, 1);           // segmentation_enabled = 0
        // base_q_idx == 0 -> no delta_q_params; lossless -> no lf/cdef/lr/tx
        w.bits(reducedTxSet, 1); // reduced_tx_set
    }

    /**
     * Non-reduced prefix over nrMonoObuType(8x8, SELECT sct/mv, order hint
     * off, ref-frame-MV off): show_existing, frame_type, show, [showable],
     * [error_resilient], disable_cdf, allow_sct, [force_mv], [ids], ov,
     * [refresh], [4+4 coded], render [+16+16], [intrabc], [frame-end cdf],
     * uniform [+incs], trailing. The interp/motion bits (5.9.10) are NOT
     * written: they are read only on the non-intra path, which this reader
     * refuses before reaching them.
     * nr8: sbCols=sbRows=2 -> minCols=0, maxCols=maxRows=1, minTiles=0.
     */
    static byte[] frameNr(int type2Bits, int show, int err, int cdf, int sct, int mv,
                          int ov, int codedW, int codedH, int render, int rw16,
                          int rh16, int intrabc, int uniform, int incCols) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(0, 1);                       // show_existing_frame = 0
        w.bits(type2Bits, 2);               // frame_type
        w.bits(show, 1);                    // show_frame
        if (show == 0) {
            w.bits(1, 1);                   // showable_frame
        }
        if (!(type2Bits == 0 && show == 1)) {
            w.bits(err, 1);                 // error_resilient_mode
        }
        w.bits(cdf, 1);
        w.bits(sct, 1);
        if (sct == 1) {
            w.bits(mv, 1);
        }
        w.bits(ov, 1);
        if (!(type2Bits == 0 && show == 1)) {
            w.bits(255, 8);                 // refresh_frame_flags (allFrames)
        }
        if (ov == 1) {
            w.bits(codedW - 1, 3);          // nrMonoObuType 8x8: wBits=2 -> 3 bits
            w.bits(codedH - 1, 3);
        }
        w.bits(render, 1);
        if (render == 1) {
            w.bits(rw16, 16);
            w.bits(rh16, 16);
        }
        if (sct == 1 && (ov == 0 || codedW == 8)) {
            w.bits(intrabc, 1);
        }
        if (cdf == 0) {
            w.bits(0, 1);                   // disable_frame_end_update_cdf = 0
        }
        if (uniform >= 0) {
            w.bits(uniform, 1);
            for (int i = 0; i < incCols; i++) {
                w.bits(1, 1);
            }
        }
        tailLossless(w, 0, 1);
        w.bits(1, 1);
        return AvifSeqSupport.obuType(3, w.bytes());
    }

    /** Reduced OBU_SEQUENCE_HEADER with 128x128 max frame (wBits/hBits=6) —
     *  the 32x32 default gives sbCols=sbRows=1, so a multi-tile fixture
     *  needs >= 65 coded samples per axis (spec 5.9.15 loops). */
    static byte[] redSeq128() {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(0, 3);                       // seq_profile
        w.bits(1, 1);                       // still_picture
        w.bits(1, 1);                       // reduced_still_picture_header
        w.bits(2, 5);                       // seq_level_idx
        w.bits(6, 4);                       // frame_width_bits_minus_1
        w.bits(6, 4);                       // frame_height_bits_minus_1
        w.bits(127, 7);                     // max_frame_width_minus_1 (128)
        w.bits(127, 7);                     // max_frame_height_minus_1 (128)
        w.bits(0, 3);                       // 128x128 / filter_intra / intra_edge
        w.bits(0, 3);                       // superres / cdef / restoration
        w.bits(0, 1);                       // high_bitdepth
        w.bits(1, 1);                       // mono_chrome (profile 0)
        w.bits(0, 1);                       // color_description_present
        w.bits(0, 1);                       // color_range (mono)
        w.bits(0, 1);                       // film_grain_params_present
        w.bits(1, 1);                       // trailing one bit
        byte[] payload = w.bytes();
        byte[] out = new byte[2 + payload.length];
        out[0] = 0x0A;
        out[1] = (byte) payload.length;
        System.arraycopy(payload, 0, out, 2, payload.length);
        return out;
    }

    static byte[] nrSeq() {
        return AvifSeqSupport.nrMonoObuType(1, 0, 2, false, false, false, false,
                                            false, 8, 8);
    }

    static Path fixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("red-k.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 3, 1, 0, 0, 1, 1, 0)))));
        Files.write(dir.resolve("nr-k.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        frameNr(0, 1, 1, 1, 1, 1, 1, 5, 3, 0, 0, 0, 0, 1, 0)))));
        Files.write(dir.resolve("rend.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        frameReduced(1, 1, 1, 0, 0, 0, 1, 10, 5, 0, 3, 1, 0, 0, 1, 1, 0)))));
        Files.write(dir.resolve("tile4.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        redSeq128(),
                        frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 3, 1, 1, 1, 2, 2, 0)))));
        // INTRA_ONLY (type 2), show=1, error_resilient=0, size override
        // (coded 5x3 vs max 8x8): the non-reduced intra path with the
        // interp/motion bits absent (AV1 5.9.2 has them only off-intra).
        Files.write(dir.resolve("intra.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        frameNr(2, 1, 0, 1, 1, 1, 1, 5, 3, 0, 0, 0, 0, 1, 0)))));
        // non-lossless tail: cdef enabled, restoration off, base_q_idx 32,
        // loop filter 7/7 (exercises the cdef branch and the lf delta skip)
        Files.write(dir.resolve("q32.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObuCap(0, 2, false, false, true,
                                                         false, true, false),
                        frameQ32()))));
        // NON-uniform tile_info (uniform_tile_spacing_flag=0): the ns() col
        // size list {4,4} + row list {8} over redSeq128 -> 2x1 tiles.
        Files.write(dir.resolve("nonuni.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        redSeq128(),
                        frameNonUniform()))));
        return dir;
    }

    static Path errorFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        // show-existing: non-reduced first bit = 1
        AvifMetadataSupport.Bits se = new AvifMetadataSupport.Bits();
        se.bits(1, 1);
        se.bits(0, 2);
        se.bits(1, 1);
        se.bits(1, 1);
        Files.write(dir.resolve("showexisting.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        AvifSeqSupport.obuType(3, se.bytes())))));
        // inter frame: frame_type bits = 1 (INTER, AV1 5.9.2)
        AvifMetadataSupport.Bits it = new AvifMetadataSupport.Bits();
        it.bits(0, 1);
        it.bits(1, 2);
        it.bits(1, 1);
        it.bits(0, 1);
        it.bits(1, 1);
        it.bits(0, 56);
        Files.write(dir.resolve("inter.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        AvifSeqSupport.obuType(3, it.bytes())))));
        // intra block copy: reduced, sct=1, last data bit = 1
        Files.write(dir.resolve("intrabc.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 3, 1, 0, 0, 1, 1, 0)))));
        // truncated: reduced render=1 claims 32 bits in a 1-byte payload,
        // padding OBU behind keeps the reads inside the item
        AvifMetadataSupport.Bits tr = new AvifMetadataSupport.Bits();
        tr.bits(1, 1);
        tr.bits(1, 1);
        tr.bits(1, 1);
        tr.bits(1, 1);
        tr.bits(0, 4);
        Files.write(dir.resolve("trunc.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        AvifSeqSupport.obuType(3, tr.bytes()),
                        AvifObuSupport.obuRaw(15, new byte[8], true, false, false)))));
        // no frame at all
        Files.write(dir.resolve("noframe.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true)))));
        return dir;
    }

    static String probe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifFrame

            String of(Bool v) {
                if (v) {
                    return "1"
                }
                return "0"
            }

            String facts(AvifFrameHeader f) {
                return "t=" + f.frameType + " show=" + of(f.showFrame)
                    + " err=" + of(f.errorResilient) + " ov=" + of(f.sizeOverride)
                    + " w=" + f.codedWidth + " h=" + f.codedHeight
                    + " rd=" + of(f.renderDifferent) + " rw=" + f.renderWidth
                    + " rh=" + f.renderHeight
                    + " tiles=" + f.tileColumns + "x" + f.tileRows
                    + " hb=" + f.headerBytes + " bq=" + f.baseQIdx
                    + " lf=" + f.loopFilterLevel0 + " cd=" + f.cdefBits
            }

            main() {
                var base = "%s"
                println("red-k " + facts(readAvifFrameHeader(base + "/red-k.avif")))
                println("nr-k " + facts(readAvifFrameHeader(base + "/nr-k.avif")))
                println("rend " + facts(readAvifFrameHeader(base + "/rend.avif")))
                println("tile4 " + facts(readAvifFrameHeader(base + "/tile4.avif")))
                println("intra " + facts(readAvifFrameHeader(base + "/intra.avif")))
                println("q32 " + facts(readAvifFrameHeader(base + "/q32.avif")))
                println("nonuni " + facts(readAvifFrameHeader(base + "/nonuni.avif")))
            }
            """.formatted(base);
    }

    static String errorProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifFrame

            main() {
                var base = "%s"
                try {
                    readAvifFrameHeader(base + "/showexisting.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/inter.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/intrabc.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/trunc.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/noframe.avif")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(base);
    }
}
