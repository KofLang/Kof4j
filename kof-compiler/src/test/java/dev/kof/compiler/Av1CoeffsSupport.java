package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixtures + independent second reader for AVIF slice 3e (the AV1 tile
 * coefficient walk, {@code libs/image/Av1Coeffs.kf}). The fixture byte streams
 * are produced by a faithful port of libaom's entropy encoder
 * ({@code aom_dsp/entenc.c}, built on the dev host 05/10, not committed) over
 * a seeded level matrix for each transform size and transform type; every tile
 * round-trips through that encoder's decoder. {@link #javaDecode} is a second,
 * independent implementation of the walk — the arithmetic decoder plus the
 * spec's §5.11.39 / §9.3.3 {@code coeffs()} process — in plain Java, sharing
 * only the pinned default CDFs ({@link Av1CoeffCdfSupport}), the context
 * selection ({@link Av1CoeffCtxSupport}) and the scan generation
 * ({@link Av1TxSupport}) that the Kof library itself is built from. The Kof
 * library must reproduce the second reader's output for all eight tiles (40
 * blocks) exactly.
 */
final class Av1CoeffsSupport {

    private Av1CoeffsSupport() {}

    /** One transform block of a tile: tx size, tx type, plane, contexts. */
    record Block(int tx, int txType, int plane, int dc, int az) {}

    /** One encoded tile: base Q index, payload bytes, ordered blocks. */
    record Tile(int q, int[] bytes, List<Block> blocks) {}

    static List<Tile> tiles() {
        List<Tile> out = new ArrayList<>();
        for (String spec : SPECS.strip().split("\n")) {
            String[] f = spec.split("\\|");
            int q = Integer.parseInt(f[0]);
            int[] bytes = ints(f[1], ",");
            List<Block> blocks = new ArrayList<>();
            for (String b : f[2].split(";")) {
                int[] v = ints(b, ",");
                blocks.add(new Block(v[0], v[1], v[2], v[3], v[4]));
            }
            out.add(new Tile(q, bytes, blocks));
        }
        return out;
    }

    private static int[] ints(String csv, String sep) {
        String[] parts = csv.split(sep);
        int[] out = new int[parts.length];
        for (int i = 0; i < out.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        return String.join("\n", facts(javaDecode())).strip();
    }

    /** The per-line facts of the second reader, for the Kof probe comparison. */
    static List<String> facts(List<int[][]> decoded) {
        List<String> lines = new ArrayList<>();
        for (int ti = 0; ti < decoded.size(); ti++) {
            int[][] blocks = decoded.get(ti);
            for (int bi = 0; bi < blocks.length; bi++) {
                int[] row = blocks[bi];
                int eob = row[0];
                int cul = row[1];
                int dc = row[2];
                StringBuilder lev = new StringBuilder();
                for (int k = 3; k < row.length; k++) {
                    if (k > 3) lev.append(' ');
                    lev.append(row[k]);
                }
                lines.add("T" + ti + " B" + bi + " eob " + eob + " cul " + cul
                        + " dc " + dc);
                lines.add(lev.toString());
            }
        }
        return lines;
    }

    /** Kof probe source that decodes every tile through the Kof library. */
    static String kofProbe() {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1Coeffs\n");
        sb.append("import image.Av1Symbol\n\n");
        sb.append("void dumpCoeffRow(Int[] a) {\n");
        sb.append("    var s = \"\"\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < a.size) {\n");
        sb.append("        if (i > 0) { s = s + \" \" }\n");
        sb.append("        s = s + a[i]\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    println(s)\n");
        sb.append("}\n\n");
        // The per-block decode lives in a helper and each tile in its own
        // function. A single flat `main` that decodes all eight tiles keeps
        // every live `Av1Coeffs` in one frame; on the cross native backends
        // that frame's temporaries are not all visible to the conservative
        // collector, so the walk crashes once the heap is reclaimed. The
        // helper/one-function-per-tile shape keeps each frame small and every
        // live receiver reachable, and is byte-for-byte identical in output.
        sb.append("void decodeOne(Av1CoeffCdfStore store, Av1Symbol sym, Int tx, "
                + "Int txType, Int plane, Int az, Int dc, String tag) {\n");
        sb.append("    var c = Av1Coeffs(sym, store, tx, txType, plane)\n");
        sb.append("    var e = c.decodeBlock(az, dc)\n");
        sb.append("    println(tag + \" eob \" + e + \" cul \" + c.culLevel"
                + " + \" dc \" + c.dcCategory)\n");
        sb.append("    dumpCoeffRow(c.levels)\n");
        sb.append("}\n\n");
        List<Tile> ts = tiles();
        for (int ti = 0; ti < ts.size(); ti++) {
            Tile t = ts.get(ti);
            sb.append("void tile").append(ti).append("() {\n");
            sb.append("    var store = Av1CoeffCdfStore(").append(t.q()).append(")\n");
            sb.append("    var buf = new Int[").append(t.bytes().length).append("]\n");
            for (int k = 0; k < t.bytes().length; k++) {
                sb.append("    buf[").append(k).append("] = ")
                  .append(t.bytes()[k]).append('\n');
            }
            sb.append("    var sym = Av1Symbol(buf)\n");
            for (int bi = 0; bi < t.blocks().size(); bi++) {
                Block b = t.blocks().get(bi);
                sb.append("    decodeOne(store, sym, ").append(b.tx()).append(", ")
                  .append(b.txType()).append(", ").append(b.plane()).append(", ")
                  .append(b.az()).append(", ").append(b.dc())
                  .append(", \"T").append(ti).append(" B").append(bi).append("\")\n");
            }
            sb.append("}\n\n");
        }
        sb.append("void main() {\n");
        for (int ti = 0; ti < ts.size(); ti++) {
            sb.append("    tile").append(ti).append("()\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Independent second reader: the libaom arithmetic decoder plus the
    // spec's coefficient walk. The decoder is a port of aom_dsp/entdec.c
    // (32-bit window); the walk is the spec §5.11.39 / §9.3.3 process.
    // ------------------------------------------------------------------

    /** Decoded per-block facts: [eob, cul, dc, levels...]. */
    static List<int[][]> javaDecode() {
        List<int[][]> out = new ArrayList<>();
        for (Tile t : tiles()) {
            Dec d = new Dec(t.bytes());
            Store store = new Store(t.q());
            int[][] rows = new int[t.blocks().size()][];
            for (int bi = 0; bi < t.blocks().size(); bi++) {
                Block b = t.blocks().get(bi);
                rows[bi] = readBlock(d, store, b.tx(), b.txType(), b.plane(),
                        b.dc(), b.az());
            }
            out.add(rows);
        }
        return out;
    }

    static int[] readBlock(Dec d, Store store, int tx, int txType, int plane,
                           int dcSignCtx, int allZeroCtx) {
        int ptype = plane > 0 ? 1 : 0;
        int adj = Av1TxSupport.TXADJ[tx];
        int txw = Av1TxSupport.TXW[adj];
        int txh = Av1TxSupport.TXH[adj];
        int[] scan = getScan(tx, txType);
        int[] levels = new int[txw * txh];
        int cul = 0;
        int dcCat = 0;
        if (d.symbol(store.txbSkipCdf(tx, allZeroCtx), 2) != 0) {
            return result(levels, 0, 0, 0);
        }
        int multi = eobMultiSize(tx);
        int ctx = (txClass(txType) != 0 && multi < 5) ? 1 : 0;
        int n = new int[]{16, 32, 64, 128, 256, 512, 1024}[Math.min(multi, 6)];
        int nsym = new int[]{5, 6, 7, 8, 9, 10, 11}[Math.min(multi, 6)];
        int eobPt = d.symbol(store.eobPtCdf(n, ptype, ctx), nsym) + 1;
        int eobExtra = 0;
        int bits = eobOffsetBits(eobPt);
        if (bits > 0) {
            int eobCtx = eobPt - 3;
            if (d.symbol(store.eobExtraCdf(tx, ptype, eobCtx), 2) != 0) {
                eobExtra += 1 << (bits - 1);
            }
            for (int i = 1; i < bits; i++) {
                if (d.bool() != 0) eobExtra += 1 << (bits - 1 - i);
            }
        }
        int eob = recEobPos(eobPt, eobExtra);
        int c = eob - 1;
        while (c >= 0) {
            int pos = scan[c];
            int level;
            if (c == eob - 1) {
                int cc = Av1CoeffCtxSupport.baseCtxQ(tx, txType, pos, c, true, levels)
                        - 42 + 4;
                level = d.symbol(store.coeffBaseEobCdf(tx, ptype, cc), 3) + 1;
            } else {
                int cc = Av1CoeffCtxSupport.baseCtxQ(tx, txType, pos, c, false, levels);
                level = d.symbol(store.coeffBaseCdf(tx, ptype, cc), 4);
            }
            if (level > 2) {
                int[] cdf = store.coeffBrCdf(tx, ptype,
                        Av1CoeffCtxSupport.brCtxQ(tx, txType, pos, levels));
                int idx = 0;
                while (idx < 12) {
                    int br = d.symbol(cdf, 4);
                    level += br;
                    if (br < 3) break;
                    idx += 3;
                }
            }
            levels[pos] = level;
            c -= 1;
        }
        c = 0;
        while (c < eob) {
            int pos = scan[c];
            int sign = 0;
            if (levels[pos] != 0) {
                if (c == 0) {
                    sign = d.symbol(store.dcSignCdf(ptype, dcSignCtx), 2);
                } else {
                    sign = d.bool();
                }
            }
            if (levels[pos] > 14) levels[pos] += d.golomb();
            if (pos == 0 && levels[pos] > 0) dcCat = sign == 0 ? 2 : 1;
            cul += levels[pos];
            if (sign != 0) levels[pos] = -levels[pos];
            c += 1;
        }
        return result(levels, eob, Math.min(cul, 63), dcCat);
    }

    private static int[] result(int[] levels, int eob, int cul, int dc) {
        int[] out = new int[levels.length + 3];
        out[0] = eob;
        out[1] = cul;
        out[2] = dc;
        System.arraycopy(levels, 0, out, 3, levels.length);
        return out;
    }

    static int txClass(int txType) {
        return Av1CoeffCtxSupport.txClass(txType);
    }

    static int[] getScan(int tx, int txType) {
        int cls = txClass(txType);
        if (cls == 1) return Av1TxSupport.scan(tx, 'R');
        if (cls == 2) return Av1TxSupport.scan(tx, 'C');
        return Av1TxSupport.scan(tx, 'D');
    }

    static int eobMultiSize(int tx) {
        if (tx == 17 || tx == 18) return 5;
        if (Av1TxSupport.TXSQUP[tx] == 4) return 6;
        int wl = Math.min(Av1TxSupport.TXWL[tx], 5);
        int hl = Math.min(Av1TxSupport.TXHL[tx], 5);
        return wl + hl - 4;
    }

    static int eobOffsetBits(int eobPt) {
        return new int[]{0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9}[eobPt];
    }

    static int recEobPos(int token, int extra) {
        int start = new int[]{0, 1, 2, 3, 5, 9, 17, 33, 65, 129, 257, 513}[token];
        return start > 2 ? start + extra : start;
    }

    // --- per-tile adapted CDF store (mirrors Av1CoeffCdfStore) ---

    static final class Store {
        final int q;
        final Map<Integer, int[]> txbSkip = new HashMap<>();
        final Map<Integer, int[]> eobPt = new HashMap<>();
        final Map<Integer, int[]> eobExtra = new HashMap<>();
        final Map<Integer, int[]> dcSign = new HashMap<>();
        final Map<Integer, int[]> coeffBaseEob = new HashMap<>();
        final Map<Integer, int[]> coeffBase = new HashMap<>();
        final Map<Integer, int[]> coeffBr = new HashMap<>();

        Store(int baseQ) { this.q = baseQ <= 20 ? 0 : baseQ <= 60 ? 1 : baseQ <= 120 ? 2 : 3; }

        private static int[] get(Map<Integer, int[]> cache, int key, int[] fresh) {
            int[] cur = cache.get(key);
            if (cur == null) { cache.put(key, fresh); return fresh; }
            return cur;
        }

        int[] txbSkipCdf(int tx, int ctx) {
            int t = Av1TxSupport.txSzCtx(tx);
            return get(txbSkip, t * 13 + ctx,
                    Av1CoeffCdfSupport.slice(Av1CoeffCdfSupport.TXB_SKIP,
                            (q * 5 + t) * 13 + ctx, 3));
        }

        int[] eobPtCdf(int n, int ptype, int ctx) {
            int[] tab = Av1CoeffCdfSupport.eobTable(n);
            int per = Av1CoeffCdfSupport.eobLen(n);
            int nctx = n <= 256 ? 2 : 1;
            return get(eobPt, n * 4 + ptype * per + ctx,
                    Av1CoeffCdfSupport.slice(tab, (q * 2 + ptype) * nctx + ctx, per));
        }

        int[] eobExtraCdf(int tx, int ptype, int ctx) {
            int t = Av1TxSupport.txSzCtx(tx);
            return get(eobExtra, (t * 2 + ptype) * 9 + ctx,
                    Av1CoeffCdfSupport.slice(Av1CoeffCdfSupport.EOB_EXTRA,
                            ((q * 5 + t) * 2 + ptype) * 9 + ctx, 3));
        }

        int[] dcSignCdf(int ptype, int ctx) {
            return get(dcSign, ptype * 3 + ctx,
                    Av1CoeffCdfSupport.slice(Av1CoeffCdfSupport.DC_SIGN,
                            (q * 2 + ptype) * 3 + ctx, 3));
        }

        int[] coeffBaseEobCdf(int tx, int ptype, int ctx) {
            int t = Av1TxSupport.txSzCtx(tx);
            return get(coeffBaseEob, (t * 2 + ptype) * 4 + ctx,
                    Av1CoeffCdfSupport.slice(Av1CoeffCdfSupport.COEFF_BASE_EOB,
                            ((q * 5 + t) * 2 + ptype) * 4 + ctx, 4));
        }

        int[] coeffBaseCdf(int tx, int ptype, int ctx) {
            int t = Av1TxSupport.txSzCtx(tx);
            return get(coeffBase, (t * 2 + ptype) * 42 + ctx,
                    Av1CoeffCdfSupport.slice(Av1CoeffCdfSupport.COEFF_BASE,
                            ((q * 5 + t) * 2 + ptype) * 42 + ctx, 5));
        }

        int[] coeffBrCdf(int tx, int ptype, int ctx) {
            int t = Math.min(Av1TxSupport.txSzCtx(tx), 3);
            return get(coeffBr, (t * 2 + ptype) * 21 + ctx,
                    Av1CoeffCdfSupport.slice(Av1CoeffCdfSupport.COEFF_BR,
                            ((q * 5 + t) * 2 + ptype) * 21 + ctx, 5));
        }
    }

    // --- libaom od_ec decoder (32-bit window, aom_dsp/entdec.c) ---

    static final class Dec {
        static final int WINDOW = 32;
        static final int LOTS = 0x4000;
        final int[] data;
        int bptr;
        long tellOffs;
        long dif;
        int rng = 0x8000;
        int cnt = -15;

        Dec(int[] data) {
            this.data = data;
            this.tellOffs = 10 - (WINDOW - 8);
            this.dif = (1L << (WINDOW - 1)) - 1;
            refill();
        }

        void refill() {
            long d = dif & 0xFFFFFFFFL;
            int c = cnt;
            int b = bptr;
            int end = data.length;
            int s = WINDOW - 9 - (c + 15);
            while (s >= 0 && b < end) {
                d ^= ((long) (data[b] & 0xFF)) << s;
                c += 8;
                s -= 8;
                b += 1;
            }
            if (b >= end) {
                tellOffs += LOTS - c;
                c = LOTS;
            }
            dif = d;
            cnt = c;
            bptr = b;
        }

        int normalize(long d, int r, int ret) {
            int bits = 16 - bitLength(r);
            cnt -= bits;
            dif = (((d + 1) << bits) - 1) & 0xFFFFFFFFL;
            rng = r << bits;
            if (cnt < 0) refill();
            return ret;
        }

        int decodeCdf(int[] icdf, int nsyms) {
            long d = dif;
            int r = rng;
            int N = nsyms - 1;
            int c = (int) (d >>> (WINDOW - 16));
            int v = r;
            int ret = -1;
            int u;
            while (true) {
                u = v;
                ret += 1;
                v = ((r >> 8) * (icdf[ret] >> 6) >> 1) + 4 * (N - ret);
                if (!(c < v)) break;
            }
            r = u - v;
            d -= ((long) v) << (WINDOW - 16);
            return normalize(d, r, ret);
        }

        int symbol(int[] cdf, int n) {
            int[] icdf = new int[n];
            for (int i = 0; i < n; i++) icdf[i] = 32768 - cdf[i];
            int sym = decodeCdf(icdf, n);
            updateCdf(cdf, sym, n);
            return sym;
        }

        int decodeBool(int f) {
            long d = dif;
            int r = rng;
            int v = ((r >> 8) * (f >> 6) >> 1) + 4;
            long vw = ((long) v) << (WINDOW - 16);
            int ret = 1;
            int rNew = v;
            if (d >= vw) {
                rNew = r - v;
                d -= vw;
                ret = 0;
            }
            return normalize(d, rNew, ret);
        }

        int bool() { return decodeBool(16384); }

        int golomb() {
            int x = 1;
            int length = 0;
            int i = 0;
            while (i == 0) {
                i = bool();
                length += 1;
                if (length > 20) throw new IllegalStateException("golomb");
            }
            for (int k = 0; k < length - 1; k++) x = (x << 1) | bool();
            return x - 1;
        }

        private static int bitLength(int v) {
            return 32 - Integer.numberOfLeadingZeros(v);
        }
    }

    static void updateCdf(int[] cdf, int val, int nsymbs) {
        int count = cdf[nsymbs];
        int rate = 3 + (count > 15 ? 1 : 0) + (count > 31 ? 1 : 0);
        int l2 = 0;
        for (int x = nsymbs; x > 1; x >>= 1) l2 += 1;
        rate += Math.min(l2, 2);
        int tmp = 0;
        for (int i = 0; i < nsymbs - 1; i++) {
            if (i == val) tmp = 1 << 15;
            if (tmp < cdf[i]) cdf[i] -= (cdf[i] - tmp) >> rate;
            else cdf[i] += (tmp - cdf[i]) >> rate;
        }
        if (cdf[nsymbs] < 32) cdf[nsymbs] += 1;
    }

    // q | bytes | tx,txType,plane,dc,az;...
    private static final String SPECS = """
80|18,138,253,148,144,33,215,27,233,204,79,38,2,39,245,152,193,177,133,242,191,191,255,98,89,152,44,133,166,212,227,126,16,102,191,159,13,129,169,19,253,254,127,255,197,237,117,101,65,36,90,233,254,223,243,241,64|18,13,0,1,2;0,0,0,1,6;2,3,0,2,0;3,0,1,1,2;11,7,0,1,1
10|0,212,77,201,195,34,216,1,169,34,78,52,145,149,49,255,235,108,91,236,114,196,184,63,251,245,101,255,231,47,253,246,42,149,126,78,69,69,125,37,22,178,247,80,235,99,230,162,192|6,3,0,2,1;13,4,0,2,10;11,7,0,2,7;6,3,1,1,6;13,4,1,2,10
10|23,85,255,5,95,242,154,47,47,151,208,109,57,203,185,224,179,213,231,153,108,134,255,252,215,252,104,103,211,187,216,203,22,41,48,238,31,66,125,175,254,224,135,173,221,119,29,246,193,42,74,178,64|2,3,0,1,0;14,11,0,1,2;7,5,1,0,12;14,11,0,1,12;14,11,0,0,3
140|66,166,220,244,201,36,224,223,236,158,220,127,255,254,150,165,104,147,4,67,2,178,9,77,137,0,164,112,248,153,191,81,175,255,189,203,217,168,98,146,66,64|9,0,0,1,12;1,1,0,2,8;2,3,0,0,6;8,2,0,0,2;5,4,0,1,12
32|7,32,231,243,11,249,133,59,6,210,35,71,37,72,242,140,90,83,115,70,85,220,153,212,166,103,106,27,242,156,80|13,4,0,1,4;14,11,1,2,9;16,5,0,0,11;1,1,0,0,2;18,13,0,1,8
32|46,129,127,255,255,255,175,177,127,255,244,137,107,8,172,90,146,48,4,84,177,103,35,142,108,43,140,53,255,255,188,183,250,127,184,241,7,244,102,51,195,244,128,205,12,68,41,237,44,237,245,252,93,162,152,128|16,5,1,0,1;5,4,0,0,5;15,0,1,0,2;7,5,1,1,5;12,10,1,2,8
10|6,167,15,45,31,253,68,8,25,191,161,132,173,131,116,52,236,16,16,183,206,137,218,217,216,92,236,14,204,207,181,27,29,132,48,197,234,36|5,4,0,1,11;6,3,0,1,1;6,3,0,2,5;14,11,1,2,1;0,0,0,0,2
10|73,195,178,75,189,179,226,104,130,43,189,219,192,251,138,111,254,51,109,14,36,141,54,117,125,117,144,27,120,182,247,176,192,76,39,217,95,243,61,36|17,9,0,0,5;5,4,0,1,1;14,11,0,1,6;5,4,0,1,1;13,4,0,0,4
""";
}
