package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

/** Second independent AVIF frame reader (plain Java, written from AV1 5.5/5.9/5.9.15):
 *  the agreement oracle for slice 2d/2e (split from AvifFrameSupport by the size ratchet). */
final class AvifFrameJavaSupport {

    private AvifFrameJavaSupport() {}

    // --- second independent reader (plain Java, written from AV1 5.5/5.9) ---

    static String javaFrameFacts(Path file) throws Exception {
        byte[] item = AvifItemsSupport.readItemJava(file, 1);
        int pos = 0;
        int n = item.length;
        boolean haveSeq = false;
        int reduced = 0, maxW = 0, maxH = 0, wB = 0, hB = 0, ohBits = 0;
        int forceSct = 2, forceMv = 2, delta = 0, add = 0;
        boolean frameIds = false, superres = false, use128 = false, refMvs = false;
        boolean enableCdef = false, enableRestoration = false;
        while (pos < n) {
            int head = item[pos] & 255;
            if ((head >> 7) != 0) throw new AssertionError("forbidden");
            int type = (head >> 3) & 15;
            int ext = (head >> 2) & 1;
            if (((head >> 1) & 1) != 1) throw new AssertionError("nosize");
            if ((head & 1) != 0) throw new AssertionError("resbit");
            int p = pos + 1 + ext;
            int size = 0;
            boolean sized = false;
            for (int i = 0; i < 8; i++) {
                int x = item[p++] & 255;
                size = (size << 7) | (x & 127);
                if ((x & 128) == 0) {
                    sized = true;
                    break;
                }
            }
            if (!sized) throw new AssertionError("size");
            int limit = p + size;
            if (limit > n) throw new AssertionError("obutrunc");
            if (type == 1 && !haveSeq) {
                int bits = p * 8;
                int profile = AvifSeqSupport.readBits(item, bits, 3); bits += 3;
                if (profile > 2) throw new AssertionError("prof3");
                bits += 1;                                  // still_picture
                reduced = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                if (reduced == 1) {
                    bits += 5;                              // seq_level_idx
                } else {
                    int timing = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (timing == 1) throw new AssertionError("timing");
                    int delay = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    int cnt = AvifSeqSupport.readBits(item, bits, 5); bits += 5;
                    for (int i = 0; i <= cnt; i++) {
                        bits += 12;                         // op idc (f(12))
                        int lv = AvifSeqSupport.readBits(item, bits, 5); bits += 5;
                        if (lv > 7) bits += 1;              // seq_tier
                        if (delay == 1) bits += 1;          // per-op delay flag
                    }
                }
                wB = AvifSeqSupport.readBits(item, bits, 4) + 1; bits += 4;
                hB = AvifSeqSupport.readBits(item, bits, 4) + 1; bits += 4;
                maxW = AvifSeqSupport.readBits(item, bits, wB) + 1; bits += wB;
                maxH = AvifSeqSupport.readBits(item, bits, hB) + 1; bits += hB;
                if (reduced == 0) {
                    frameIds = AvifSeqSupport.readBits(item, bits, 1) == 1; bits += 1;
                    if (frameIds) {
                        delta = AvifSeqSupport.readBits(item, bits, 4); bits += 4;
                        add = AvifSeqSupport.readBits(item, bits, 3); bits += 3;
                    }
                }
                use128 = AvifSeqSupport.readBits(item, bits, 1) == 1;
                bits += 3;                                  // + filter/edge
                if (reduced == 0) {
                    bits += 4;                              // inter-block caps
                    int oh = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (oh == 1) {
                        AvifSeqSupport.readBits(item, bits, 1);
                        refMvs = AvifSeqSupport.readBits(item, bits + 1, 1) == 1;
                        bits += 2;                          // jnt comp + rf mvs
                    }
                    int csct = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (csct == 0) {
                        forceSct = AvifSeqSupport.readBits(item, bits, 1);
                        bits += 1;
                    } else {
                        forceSct = 2;
                    }
                    if (forceSct > 0) {
                        int cmv = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                        if (cmv == 0) {
                            forceMv = AvifSeqSupport.readBits(item, bits, 1);
                            bits += 1;
                        } else {
                            forceMv = 2;
                        }
                    }
                    if (oh == 1) {
                        ohBits = AvifSeqSupport.readBits(item, bits, 3) + 1;
                        bits += 3;
                    }
                }
                superres = AvifSeqSupport.readBits(item, bits, 1) == 1;
                bits += 1;                                  // superres
                enableCdef = AvifSeqSupport.readBits(item, bits, 1) == 1; bits += 1;
                enableRestoration = AvifSeqSupport.readBits(item, bits, 1) == 1; bits += 1;
                haveSeq = true;
            } else if ((type == 3 || type == 6) && haveSeq) {
                int bits = p * 8;
                int frameType = 0, show = 1, err = 1, sct = forceSct, ov = 0;
                if (reduced == 0) {
                    if (AvifSeqSupport.readBits(item, bits, 1) == 1) {
                        throw new AssertionError("showexisting");
                    }
                    bits += 1;
                    frameType = AvifSeqSupport.readBits(item, bits, 2); bits += 2;
                    if (frameType == 1 || frameType == 3) throw new AssertionError("inter");
                    show = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (show == 0) bits += 1;
                    if (frameType == 0 && show == 1) {
                        err = 1;
                    } else {
                        err = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    }
                }
                int cdfv = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                if (forceSct == 2) {
                    sct = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                }
                if (sct == 1 && forceMv == 2) {
                    bits += 1;                              // force_integer_mv
                }
                if (frameIds) {
                    bits += delta + add + 3;
                }
                if (reduced == 0) {
                    ov = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                }
                bits += ohBits;                             // order_hint
                if (reduced == 0 && !(frameType == 0 && show == 1)) {
                    if (AvifSeqSupport.readBits(item, bits, 8) != 255) {
                        throw new AssertionError("refrefresh");
                    }
                    bits += 8;                              // refresh (allFrames)
                }
                int codedW = maxW, codedH = maxH;
                if (ov == 1) {
                    codedW = AvifSeqSupport.readBits(item, bits, wB) + 1; bits += wB;
                    codedH = AvifSeqSupport.readBits(item, bits, hB) + 1; bits += hB;
                }
                if (superres) {
                    if (AvifSeqSupport.readBits(item, bits, 1) == 1) {
                        throw new AssertionError("superres");
                    }
                    bits += 1;
                }
                int rd = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                int rW = codedW, rH = codedH;
                if (rd == 1) {
                    rW = AvifSeqSupport.readBits(item, bits, 16) + 1; bits += 16;
                    rH = AvifSeqSupport.readBits(item, bits, 16) + 1; bits += 16;
                }
                if (bits > limit * 8) throw new AssertionError("trunc");
                int intrabc = 0;
                if (sct == 1 && codedW == maxW) {
                    intrabc = AvifSeqSupport.readBits(item, bits, 1);
                    bits += 1;
                    if (intrabc == 1) {
                        throw new AssertionError("intrabc");
                    }
                }
                // read_interpolation_filter / is_motion_mode_switchable /
                // use_ref_frame_mvs are read only off-intra (5.9.2); intra
                // frames (the only ones this reader accepts) skip them.
                if (reduced == 0 && cdfv == 0) bits += 1;   // disable_frame_end_update_cdf
                // tile_info() uniform path (5.9.15), counts per the loops
                int sbShift = use128 ? 5 : 4;
                int sbSize = sbShift + 2;
                int miCols = (codedW + 3) >> 2;
                int miRows = (codedH + 3) >> 2;
                int sbCols = (miCols + (1 << sbShift) - 1) >> sbShift;
                int sbRows = (miRows + (1 << sbShift) - 1) >> sbShift;
                int minCols = tileLog2(4096 >> sbSize, sbCols);
                int maxCols = tileLog2(1, Math.min(sbCols, 64));
                int maxRows = tileLog2(1, Math.min(sbRows, 64));
                int minTiles = Math.max(minCols, tileLog2(4096 * 2304 >> (2 * sbSize), sbRows * sbCols));
                int colsLog2;
                int rowsLog2;
                int tiles;
                int trows;
                if (AvifSeqSupport.readBits(item, bits, 1) == 1) {
                    bits += 1;
                    colsLog2 = minCols;
                    boolean stop = false;
                    while (colsLog2 < maxCols && !stop) {
                        if (bits > limit * 8) throw new AssertionError("trunc");
                        if (AvifSeqSupport.readBits(item, bits, 1) == 0) {
                            stop = true;
                        } else {
                            colsLog2 += 1;
                        }
                        bits += 1;
                    }
                    rowsLog2 = Math.max(minTiles - colsLog2, 0);
                    stop = false;
                    while (rowsLog2 < maxRows && !stop) {
                        if (bits > limit * 8) throw new AssertionError("trunc");
                        if (AvifSeqSupport.readBits(item, bits, 1) == 0) {
                            stop = true;
                        } else {
                            rowsLog2 += 1;
                        }
                        bits += 1;
                    }
                    int tileWidthSb = (sbCols + (1 << colsLog2) - 1) >> colsLog2;
                    int tileHeightSb = (sbRows + (1 << rowsLog2) - 1) >> rowsLog2;
                    tiles = 0;
                    for (int start = 0; start < sbCols; start += tileWidthSb) tiles++;
                    trows = 0;
                    for (int start = 0; start < sbRows; start += tileHeightSb) trows++;
                } else {
                    bits += 1;
                    int widest = 0;
                    tiles = 0;
                    for (int start = 0; start < sbCols; ) {
                        if (bits > limit * 8) throw new AssertionError("trunc");
                        int maxWidth = Math.min(sbCols - start, 4096 >> sbSize);
                        int[] ns = readNs(item, bits, maxWidth);
                        int sizeSb = ns[0] + 1;
                        bits = ns[1];
                        widest = Math.max(sizeSb, widest);
                        start += sizeSb;
                        tiles++;
                    }
                    int areaSb = sbRows * sbCols;
                    if (minTiles > 0) {
                        areaSb = areaSb >> (minTiles + 1);
                    }
                    int maxHeight = Math.max(areaSb / widest, 1);
                    trows = 0;
                    for (int start = 0; start < sbRows; ) {
                        if (bits > limit * 8) throw new AssertionError("trunc");
                        int mh = Math.min(sbRows - start, maxHeight);
                        int[] ns = readNs(item, bits, mh);
                        int sizeSb = ns[0] + 1;
                        bits = ns[1];
                        start += sizeSb;
                        trows++;
                    }
                    colsLog2 = tileLog2(1, tiles);
                    rowsLog2 = tileLog2(1, trows);
                }
                if (colsLog2 > 0 || rowsLog2 > 0) {
                    bits += colsLog2 + rowsLog2;
                    bits += 2;                              // tile_size_bytes_minus_1
                }
                if (bits > limit * 8) throw new AssertionError("trunc");
                // uncompressed_header tail (5.9.2), after tile_info: mono
                // fixtures -> numPlanes = 1; CodedLossless is derived.
                int numPlanes = 1;
                int baseQ = AvifSeqSupport.readBits(item, bits, 8); bits += 8;
                bits += 1;                                  // DeltaQYDc coded flag
                bits += 1;                                  // using_qmatrix
                int segEnabled = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                boolean codedLossless = baseQ == 0 && segEnabled == 0;
                int lf0Out = 0, cdefBitsOut = 0;
                if (baseQ > 0) {
                    int dqp = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (dqp == 1) {
                        bits += 2;                          // delta_q_res
                        if (intrabc == 0) {
                            int dflp = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                            if (dflp == 1) bits += 3;
                        }
                    }
                }
                if (!codedLossless && intrabc == 0) {
                    int lf0 = AvifSeqSupport.readBits(item, bits, 6); bits += 6;
                    int lf1 = AvifSeqSupport.readBits(item, bits, 6); bits += 6;
                    lf0Out = lf0;
                    if ((lf0 != 0 || lf1 != 0) && numPlanes > 1) bits += 12; // UV
                    bits += 3;                              // sharpness
                    int lfDelta = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (lfDelta == 1) {
                        int upd = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                        if (upd == 1) {
                            for (int k = 0; k < 10; k++) {
                                int u = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                                if (u == 1) bits += 7;
                            }
                        }
                    }
                }
                if (!codedLossless && intrabc == 0 && enableCdef) {
                    bits += 2;                              // damping_minus_3
                    int cdefBits = AvifSeqSupport.readBits(item, bits, 2); bits += 2;
                    cdefBitsOut = cdefBits;
                    for (int c = 0; c < (1 << cdefBits); c++) {
                        bits += 6;                          // mono: Y only
                    }
                }
                if (!codedLossless && intrabc == 0 && enableRestoration) {
                    int lr = AvifSeqSupport.readBits(item, bits, 2); bits += 2;
                    if (lr != 0) {
                        int lrUnitShift = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                        if (!use128 && lrUnitShift != 0) bits += 1;
                    }
                }
                if (!codedLossless) bits += 1;              // tx_mode_select
                bits += 1;                                  // reduced_tx_set
                if (bits > limit * 8) throw new AssertionError("trunc");
                int hb = bits;
                while ((hb & 7) != 0) hb++;
                int headerBytes = (hb - p * 8) / 8;
                return "t=" + frameType + " show=" + show + " err=" + err + " ov=" + ov
                        + " w=" + codedW + " h=" + codedH + " rd=" + rd
                        + " rw=" + rW + " rh=" + rH + " tiles=" + tiles + "x" + trows
                        + " hb=" + headerBytes + " bq=" + baseQ
                        + " lf=" + lf0Out + " cd=" + cdefBitsOut;
            }
            pos = limit;
        }
        throw new AssertionError("noframe");
    }

    private static int tileLog2(int u, int v) {
        int k = 0;
        while ((u << k) < v) k++;
        return k;
    }

    // ns(n) per AV1 4.10.6, returning {value, nextBitPos}.
    private static int[] readNs(byte[] b, int bits, int n) {
        int w = floorLog2(n) + 1;
        int m = (1 << w) - n;
        int v = AvifSeqSupport.readBits(b, bits, w - 1);
        bits += w - 1;
        if (v < m) {
            return new int[]{v, bits};
        }
        int extra = AvifSeqSupport.readBits(b, bits, 1);
        bits += 1;
        return new int[]{(v << 1) - m + extra, bits};
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

    static String javaFrameFactsError(Path file) throws Exception {
        try {
            return javaFrameFacts(file);
        } catch (AssertionError e) {
            return "REFUSED:" + e.getMessage();
        }
    }
}
