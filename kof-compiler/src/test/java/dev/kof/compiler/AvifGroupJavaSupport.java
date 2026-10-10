package dev.kof.compiler;

import java.nio.file.Path;

/** Second independent AVIF tile-group reader (plain Java, written fresh
 *  from AV1 5.9/5.11.1/6.10.1): the agreement oracle for slice 2f. Walks
 *  seq -> frame prefix -> sibling OBU_TILE_GROUP payloads and emits the
 *  SAME fact strings / SAME `IMAGE:` refusal messages as the Kof face —
 *  agreement is the proof, divergence is a bug in one of the two. */
final class AvifGroupJavaSupport {

    private AvifGroupJavaSupport() {}

    private static byte[] itemOf(Path file) throws Exception {
        return AvifItemsSupport.readItemJava(file, 1);
    }

    static String javaGroupFacts(Path file) throws Exception {
        byte[] item = itemOf(file);
        int n = item.length;
        int pos = 0;
        int tiles = 0, trows = 0, tileBits = 0, tsb = 0;
        boolean haveSeq = false, haveFrame = false;
        int[] sq = null;
        int totalTileNum = 0, lastTgEnd = -1, groups = 0;
        StringBuilder out = new StringBuilder();
        while (pos < n) {
            int head = item[pos] & 255;
            if ((head >> 7) != 0) throw new AssertionError("IMAGE: avif item obu forbidden bits");
            int type = (head >> 3) & 15;
            int ext = (head >> 2) & 1;
            if ((head & 1) != 0) throw new AssertionError("IMAGE: avif item obu reserved bit set");
            if (((head >> 1) & 1) != 1) throw new AssertionError("IMAGE: avif item obu missing size field");
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
            if (!sized) throw new AssertionError("IMAGE: avif obu size too long");
            int limit = p + size;
            if (limit > n) throw new AssertionError("IMAGE: avif obu truncated");
            if (type == 1 && !haveSeq) {
                sq = walkSeq(item, p, limit);
                haveSeq = true;
            } else if (type == 3 || type == 6) {
                if (!haveSeq) throw new AssertionError("IMAGE: avif item has no sequence header");
                int[] t = walkFrameTileInfo(item, p, limit, sq);
                tiles = t[0];
                trows = t[1];
                tileBits = t[2];
                tsb = t[3];
                haveFrame = true;
                if (type == 6) {
                    // ONE inline tile group after the byte-aligned header
                    String line = groupFacts(item, p + t[4], limit, tiles * trows,
                                              tileBits, tsb, totalTileNum);
                    if (out.length() > 0) out.append("\n");
                    out.append(line);
                    totalTileNum = tiles * trows;
                    lastTgEnd = tiles * trows - 1;
                    groups++;
                }
            } else if (type == 4) {
                if (!haveFrame) throw new AssertionError("IMAGE: avif tile group without frame header");
                String line = groupFacts(item, p, limit, tiles * trows, tileBits, tsb,
                                          totalTileNum);
                int dot = line.indexOf("..");
                int sp = line.indexOf(" n=");
                int tgStart = Integer.parseInt(line.substring(0, dot));
                int tgEnd = Integer.parseInt(line.substring(dot + 2, sp));
                if (out.length() > 0) out.append("\n");
                out.append(line);
                totalTileNum += tgEnd - tgStart + 1;
                lastTgEnd = tgEnd;
                groups++;
            } else if (type == 7) {
                throw new AssertionError("IMAGE: avif redundant frame header not covered");
            } else if (type == 8) {
                throw new AssertionError("IMAGE: avif tile list obu not covered");
            }
            pos = limit;
        }
        if (!haveFrame) throw new AssertionError("IMAGE: avif item has no frame header");
        if (groups == 0) throw new AssertionError("IMAGE: avif frame has no tile group");
        if (lastTgEnd != tiles * trows - 1) {
            throw new AssertionError("IMAGE: avif tile group end incomplete");
        }
        return out.toString();
    }

    // --- tile group header prefix per 5.11.1 + 6.10.1 (independent shape:
    //     it formats as it walks, the Kof face builds a record) ---
    private static String groupFacts(byte[] b, int p0, int limit, int numTiles,
                                     int tileBits, int tsb, int totalTileNum) {
        int bits = p0 * 8;
        int tgStart = 0;
        int tgEnd = numTiles - 1;
        if (numTiles > 1) {
            if (AvifSeqSupport.readBits(b, bits, 1) == 1) {
                bits += 1;
                tgStart = AvifSeqSupport.readBits(b, bits, tileBits);
                bits += tileBits;
                tgEnd = AvifSeqSupport.readBits(b, bits, tileBits);
                bits += tileBits;
                if (tgStart == 0 && tgEnd == numTiles - 1) {
                    throw new AssertionError("IMAGE: avif tile group full range flag set");
                }
            } else {
                bits += 1;
            }
        }
        if (tgEnd < tgStart) throw new AssertionError("IMAGE: avif tile group range invalid");
        if (tgStart != totalTileNum) throw new AssertionError("IMAGE: avif tile group order invalid");
        while ((bits & 7) != 0) bits++;
        if (bits > limit * 8) throw new AssertionError("IMAGE: truncated avif tile group");
        int q = bits / 8;
        int count = tgEnd + 1 - tgStart;
        StringBuilder sizes = new StringBuilder();
        int total = 0;
        for (int i = 0; i + 1 < count; i++) {
            if (q + tsb > limit) throw new AssertionError("IMAGE: truncated avif tile group");
            int v = 0;
            for (int k = 0; k < tsb; k++) {
                v = (v << 8) | (b[q++] & 255);
            }
            int t = v + 1;
            q += t;                                   // skip this tile's bytes
            if (q > limit) throw new AssertionError("IMAGE: truncated avif tile group");
            sizes.append(",").append(t);
            total += t;
        }
        int sz = limit - q;
        if (sz < 0) throw new AssertionError("IMAGE: truncated avif tile group");
        StringBuilder sb = new StringBuilder();
        sb.append(tgStart).append("..").append(tgEnd).append(" n=").append(count)
          .append(" sizes=").append(sizes).append(" last=").append(sz)
          .append(" total=").append(total + sz);
        return sb.toString();
    }

    // --- sequence header prefix (5.5.1): capture what the frame needs ---
    private static int[] walkSeq(byte[] b, int p, int limit) {
        int bits = p * 8;
        int profile = AvifSeqSupport.readBits(b, bits, 3); bits += 3;
        if (profile > 2) throw new AssertionError("IMAGE: avif sequence profile 3 not covered");
        bits += 1;
        int reduced = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
        if (reduced == 1) {
            bits += 5;
        } else {
            int timing = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
            if (timing == 1) throw new AssertionError("IMAGE: avif sequence timing not covered");
            int delay = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
            int cnt = AvifSeqSupport.readBits(b, bits, 5); bits += 5;
            for (int i = 0; i <= cnt; i++) {
                bits += 12;
                int lv = AvifSeqSupport.readBits(b, bits, 5); bits += 5;
                if (lv > 7) bits += 1;
                if (delay == 1) bits += 1;
            }
        }
        int wB = AvifSeqSupport.readBits(b, bits, 4) + 1; bits += 4;
        int hB = AvifSeqSupport.readBits(b, bits, 4) + 1; bits += 4;
        int maxW = AvifSeqSupport.readBits(b, bits, wB) + 1; bits += wB;
        int maxH = AvifSeqSupport.readBits(b, bits, hB) + 1; bits += hB;
        boolean frameIds = false;
        int delta = 0, add = 0, ohBits = 0, forceSct = 2, forceMv = 2;
        boolean refMvs = false;
        if (reduced == 0) {
            frameIds = AvifSeqSupport.readBits(b, bits, 1) == 1; bits += 1;
            if (frameIds) {
                delta = AvifSeqSupport.readBits(b, bits, 4); bits += 4;
                add = AvifSeqSupport.readBits(b, bits, 3); bits += 3;
            }
        }
        boolean use128 = AvifSeqSupport.readBits(b, bits, 1) == 1; bits += 3;
        if (reduced == 0) {
            bits += 4;
            int oh = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
            if (oh == 1) {
                bits += 1;
                refMvs = AvifSeqSupport.readBits(b, bits, 1) == 1;
                bits += 1;
            }
            int csct = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
            if (csct == 0) {
                forceSct = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
            }
            if (forceSct > 0) {
                int cmv = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
                if (cmv == 0) {
                    forceMv = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
                }
            }
            if (oh == 1) {
                ohBits = AvifSeqSupport.readBits(b, bits, 3) + 1; bits += 3;
            }
        }
        boolean superres = AvifSeqSupport.readBits(b, bits, 1) == 1; bits += 1;
        boolean enableCdef = AvifSeqSupport.readBits(b, bits, 1) == 1; bits += 1;
        boolean enableRestoration = AvifSeqSupport.readBits(b, bits, 1) == 1; bits += 1;
        return new int[]{reduced, maxW, maxH, wB, hB, frameIds ? 1 : 0, delta, add,
                         use128 ? 1 : 0, superres ? 1 : 0, ohBits, forceSct, forceMv,
                         refMvs ? 1 : 0, enableCdef ? 1 : 0, enableRestoration ? 1 : 0};
    }

    // --- frame prefix (5.9.2/5.9.10/5.9.15): return the tile-grid context
    //     {tileCols, tileRows, tileBits, tileSizeBytes} ---
    private static int[] walkFrameTileInfo(byte[] b, int p, int limit, int[] sq) {
        int reduced = sq[0], maxW = sq[1], maxH = sq[2], wB = sq[3], hB = sq[4];
        boolean frameIds = sq[5] == 1;
        int delta = sq[6], add = sq[7];
        boolean use128 = sq[8] == 1, superres = sq[9] == 1;
        int ohBits = sq[10], forceSct = sq[11], forceMv = sq[12];
        boolean refMvs = sq[13] == 1;
        int bits = p * 8;
        int frameType = 0, show = 1, err = 1, sct = forceSct, ov = 0, cdfv = 1;
        if (reduced == 0) {
            if (AvifSeqSupport.readBits(b, bits, 1) == 1) {
                throw new AssertionError("IMAGE: avif frame show-existing not covered");
            }
            bits += 1;
            frameType = AvifSeqSupport.readBits(b, bits, 2); bits += 2;
            if (frameType == 1 || frameType == 3) {
                throw new AssertionError("IMAGE: avif inter frame not covered");
            }
            show = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
            if (show == 0) bits += 1;
            if (frameType == 0 && show == 1) {
                err = 1;
            } else {
                err = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
            }
        }
        cdfv = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
        if (forceSct == 2) {
            sct = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
        }
        if (sct == 1 && forceMv == 2) bits += 1;
        if (frameIds) bits += delta + add + 3;
        if (reduced == 0) {
            ov = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
        }
        bits += ohBits;
        if (reduced == 0 && !(frameType == 0 && show == 1)) {
            if (AvifSeqSupport.readBits(b, bits, 8) != 255) {
                throw new AssertionError("IMAGE: avif frame partial refresh not covered");
            }
            bits += 8;
        }
        int codedW = maxW, codedH = maxH;
        if (ov == 1) {
            codedW = AvifSeqSupport.readBits(b, bits, wB) + 1; bits += wB;
            codedH = AvifSeqSupport.readBits(b, bits, hB) + 1; bits += hB;
        }
        if (superres) {
            if (AvifSeqSupport.readBits(b, bits, 1) == 1) {
                throw new AssertionError("IMAGE: avif superres not covered");
            }
            bits += 1;
        }
        int rd = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
        if (rd == 1) bits += 32;
        if (bits > limit * 8) throw new AssertionError("IMAGE: truncated avif frame header");
        if (sct == 1 && codedW == maxW) {
            if (AvifSeqSupport.readBits(b, bits, 1) == 1) {
                throw new AssertionError("IMAGE: avif intra block copy not covered");
            }
            bits += 1;
        }
        // read_interpolation_filter / is_motion_mode_switchable /
        // use_ref_frame_mvs are read only off-intra (5.9.2).
        if (reduced == 0 && cdfv == 0) bits += 1;         // disable_frame_end_update_cdf
        int sbShift = use128 ? 5 : 4;
        int sbSize = sbShift + 2;
        int miCols = (codedW + 3) >> 2;
        int miRows = (codedH + 3) >> 2;
        int sbCols = (miCols + (1 << sbShift) - 1) >> sbShift;
        int sbRows = (miRows + (1 << sbShift) - 1) >> sbShift;
        int minCols = lz(4096 >> sbSize, sbCols);
        int maxCols = lz(1, Math.min(sbCols, 64));
        int maxRows = lz(1, Math.min(sbRows, 64));
        int minTiles = Math.max(minCols, lz(4096 * 2304 >> (2 * sbSize), sbRows * sbCols));
        if (AvifSeqSupport.readBits(b, bits, 1) == 0) {
            throw new AssertionError("IMAGE: avif tile size list not covered");
        }
        bits += 1;
        int colsLog2 = minCols;
        boolean stop = false;
        while (colsLog2 < maxCols && !stop) {
            if (bits > limit * 8) throw new AssertionError("IMAGE: truncated avif frame header");
            if (AvifSeqSupport.readBits(b, bits, 1) == 0) {
                stop = true;
            } else {
                colsLog2++;
            }
            bits += 1;
        }
        int rowsLog2 = Math.max(minTiles - colsLog2, 0);
        stop = false;
        while (rowsLog2 < maxRows && !stop) {
            if (bits > limit * 8) throw new AssertionError("IMAGE: truncated avif frame header");
            if (AvifSeqSupport.readBits(b, bits, 1) == 0) {
                stop = true;
            } else {
                rowsLog2++;
            }
            bits += 1;
        }
        int tileBits = 0, tsb = 0;
        if (colsLog2 > 0 || rowsLog2 > 0) {
            tileBits = colsLog2 + rowsLog2;
            bits += tileBits;                            // context_update_tile_id
            tsb = AvifSeqSupport.readBits(b, bits, 2) + 1;
            bits += 2;
        }
        int tileWidthSb = (sbCols + (1 << colsLog2) - 1) >> colsLog2;
        int tileHeightSb = (sbRows + (1 << rowsLog2) - 1) >> rowsLog2;
        int cols = 0;
        for (int start = 0; start < sbCols; start += tileWidthSb) cols++;
        int rows = 0;
        for (int start = 0; start < sbRows; start += tileHeightSb) rows++;
        // uncompressed_header tail (5.9.2), mono fixtures -> numPlanes = 1
        boolean enableCdef = sq[14] == 1, enableRestoration = sq[15] == 1;
        int baseQ = AvifSeqSupport.readBits(b, bits, 8); bits += 8;
        bits += 1;                                        // DeltaQYDc coded flag
        bits += 1;                                        // using_qmatrix
        int segEnabled = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
        boolean codedLossless = baseQ == 0 && segEnabled == 0;
        if (baseQ > 0) {
            int dqp = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
            if (dqp == 1) bits += 2;                      // delta_q_res
        }
        if (!codedLossless) {
            int lf0 = AvifSeqSupport.readBits(b, bits, 6); bits += 6;
            int lf1 = AvifSeqSupport.readBits(b, bits, 6); bits += 6;
            if (lf0 != 0 || lf1 != 0) bits += 12;         // mono: no UV
            bits += 3;                                    // sharpness
            int lfDelta = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
            if (lfDelta == 1) {
                int upd = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
                if (upd == 1) {
                    for (int k = 0; k < 10; k++) {
                        int u = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
                        if (u == 1) bits += 7;
                    }
                }
            }
            if (enableCdef) {
                bits += 2;                                // damping_minus_3
                int cdefBits = AvifSeqSupport.readBits(b, bits, 2); bits += 2;
                bits += 6 * (1 << cdefBits);              // mono: Y only
            }
            if (enableRestoration) {
                int lr = AvifSeqSupport.readBits(b, bits, 2); bits += 2;
                if (lr != 0) {
                    int lrUnitShift = AvifSeqSupport.readBits(b, bits, 1); bits += 1;
                    if (!use128 && lrUnitShift != 0) bits += 1;
                }
            }
            bits += 1;                                    // tx_mode_select
        }
        bits += 1;                                        // reduced_tx_set
        if (bits > limit * 8) throw new AssertionError("IMAGE: truncated avif frame header");
        while ((bits & 7) != 0) bits++;
        int headerBytes = (bits - p * 8) / 8;
        return new int[]{cols, rows, tileBits, tsb, headerBytes};
    }

    private static int lz(int u, int v) {
        int k = 0;
        while ((u << k) < v) k++;
        return k;
    }

    // --- slice 2n: independent tile PAYLOAD extraction. Walks the item,
    //     records each tile's (offset,size) at the interleaved 6.10.1
    //     position, then hashes the bytes in tile order. ---
    static String javaTileFacts(Path file, String prefix) throws Exception {
        byte[] item = itemOf(file);
        int n = item.length;
        int pos = 0;
        int tiles = 0, trows = 0, tileBits = 0, tsb = 0;
        boolean haveSeq = false, haveFrame = false;
        int[] sq = null;
        int totalTileNum = 0, groups = 0;
        StringBuilder out = new StringBuilder();
        while (pos < n) {
            int head = item[pos] & 255;
            int type = (head >> 3) & 15;
            int ext = (head >> 2) & 1;
            int p = pos + 1 + ext;
            int size = 0;
            for (int i = 0; i < 8; i++) {
                int x = item[p++] & 255;
                size = (size << 7) | (x & 127);
                if ((x & 128) == 0) break;
            }
            int limit = p + size;
            if (type == 1 && !haveSeq) {
                sq = walkSeq(item, p, limit);
                haveSeq = true;
            } else if (type == 3 || type == 6) {
                int[] t = walkFrameTileInfo(item, p, limit, sq);
                tiles = t[0];
                trows = t[1];
                tileBits = t[2];
                tsb = t[3];
                haveFrame = true;
                if (type == 6) {
                    tilePayloads(item, p + t[4], limit, tiles * trows, tileBits, tsb, prefix, out);
                    groups++;
                }
            } else if (type == 4) {
                if (!haveFrame) throw new AssertionError("tile group without frame header");
                tilePayloads(item, p, limit, tiles * trows, tileBits, tsb, prefix, out);
                groups++;
            }
            pos = limit;
        }
        if (!haveFrame) throw new AssertionError("no frame header");
        if (groups == 0) throw new AssertionError("no tile group");
        return out.toString();
    }

    private static void tilePayloads(byte[] b, int p0, int limit, int numTiles,
                                     int tileBits, int tsb, String prefix, StringBuilder out) {
        int bits = p0 * 8;
        int tgStart = 0, tgEnd = numTiles - 1;
        if (numTiles > 1) {
            if (AvifSeqSupport.readBits(b, bits, 1) == 1) {
                bits += 1;
                tgStart = AvifSeqSupport.readBits(b, bits, tileBits);
                bits += tileBits;
                tgEnd = AvifSeqSupport.readBits(b, bits, tileBits);
                bits += tileBits;
            } else {
                bits += 1;
            }
        }
        while ((bits & 7) != 0) bits++;
        int q = bits / 8;
        int count = tgEnd + 1 - tgStart;
        for (int i = 0; i < count; i++) {
            int size;
            if (i + 1 < count) {
                int v = 0;
                for (int k = 0; k < tsb; k++) v = (v << 8) | (b[q++] & 255);
                size = v + 1;
            } else {
                size = limit - q;
            }
            int hash = 0;
            for (int k = 0; k < size; k++) hash = (hash * 31 + (b[q + k] & 255)) % 100003;
            if (out.length() > 0) out.append("\n");
            out.append(prefix).append(" len=").append(size).append(" h=").append(hash);
            q += size;
        }
    }

    static String javaGroupFactsError(Path file) {
        try {
            javaGroupFacts(file);
            return "OK";
        } catch (AssertionError e) {
            return e.getMessage();
        } catch (Exception e) {
            return "IO:" + e;
        }
    }
}
