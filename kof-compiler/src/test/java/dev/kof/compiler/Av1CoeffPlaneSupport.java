package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3x (the AV1 coefficient plane decode
 * driver, {@code libs/image/Av1CoeffPlane.kf}). The Kof driver ties the
 * slice-3w neighbour-context selection ({@code av1TxbCtx}) and the slice-3e
 * coefficient walk ({@code Av1Coeffs.decodeBlock}) together across the grid of
 * transform blocks of a plane, then stores the packed
 * {@code av1TxbEntropyContext} byte into the plane's above/left arrays —
 * libaom's {@code av1_read_coeffs_txb} + {@code av1_set_entropy_contexts}.
 *
 * <p>The golden is the REAL libaom {@code get_txb_ctx} over a raster grid of
 * transform blocks (compiled from source on the dev host, not committed) plus
 * the {@code av1_get_txb_entropy_context} store semantics; the second reader
 * here re-derives the context flow from the pinned slice-3w primitives
 * ({@link Av1CoeffCtxSupport}), encodes each block with a faithful port of
 * libaom's {@code od_ec} encoder (validated byte-for-byte against the slice-3e
 * pinned streams), and decodes the result with the slice-3e decoder to produce
 * the expected facts. The dump is gzip+base64 as
 * {@code av1_coeffplane_golden.txt.b64}.
 */
final class Av1CoeffPlaneSupport {

    private Av1CoeffPlaneSupport() {}

    static final String RESOURCE = "/av1_coeffplane_golden.txt.b64";

    static final String GOLDEN_SHA256 =
        "53e14f81707b911e9c715f2a0adbdf603ad730ccb6e1e6fd73a0abba0173bfd1";

    /** txsize_to_bsize (AV1 §9.3; pinned by Av1BlockE2ETest). */
    private static final int[] TXTOBS = {
        0, 3, 6, 9, 12, 1, 2, 4, 5, 7, 8, 10, 11, 16, 17, 18, 19, 20, 21
    };
    /** Num_4x4_Blocks_Wide / High by block size index (BLOCK_*). */
    private static final int[] B4W = {
        1, 1, 2, 2, 2, 4, 4, 4, 8, 8, 8, 16, 16, 16, 32, 32, 1, 4, 2, 8, 4, 16
    };
    private static final int[] B4H = {
        1, 2, 1, 2, 4, 2, 4, 8, 4, 8, 16, 8, 16, 32, 16, 32, 4, 1, 8, 2, 16, 4
    };
    /** Num_Pels_Log2 by block size index (BLOCK_*). */
    private static final int[] NPELS = {
        4, 5, 5, 6, 7, 7, 8, 9, 9, 10, 11, 11, 12, 13, 13, 14, 6, 6, 8, 8, 10, 10
    };

    record Ctx(int r4, int c4, int skip, int dc, int cul, int dcCat) {}

    record Grid(String tag, int rows, int cols, int tx, int pb, int plane,
                List<Ctx> ctxs, int[] finalAbove, int[] finalLeft) {}

    static String golden() {
        try (InputStream raw = Av1CoeffPlaneSupport.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("missing test resource " + RESOURCE);
            }
            byte[] b64 = raw.readAllBytes();
            StringBuilder sb = new StringBuilder(b64.length);
            for (byte b : b64) {
                char c = (char) (b & 0xFF);
                if (c != '\n' && c != '\r' && c != ' ' && c != '\t') sb.append(c);
            }
            byte[] gz = Base64.getDecoder().decode(sb.toString());
            try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    static List<Grid> grids() {
        List<Grid> out = new ArrayList<>();
        String tag = null;
        int rows = 0, cols = 0, tx = 0, pb = 0, plane = 0;
        List<Ctx> ctxs = new ArrayList<>();
        int[] fa = null, fl = null;
        for (String line : golden().split("\n")) {
            String[] f = line.split(" ");
            if (f[0].equals("G")) {
                tag = f[1];
                rows = Integer.parseInt(f[2]);
                cols = Integer.parseInt(f[3]);
                tx = Integer.parseInt(f[4]);
                pb = Integer.parseInt(f[5]);
                plane = Integer.parseInt(f[6]);
                ctxs = new ArrayList<>();
            } else if (f[0].equals("C")) {
                ctxs.add(new Ctx(Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                        Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                        Integer.parseInt(f[5]), Integer.parseInt(f[6])));
            } else if (f[0].equals("A")) {
                int bar = line.indexOf('|');
                String[] head = line.substring(2, bar).trim().split(" ");
                String[] tail = line.substring(bar + 1).trim().split(" ");
                fa = ints(java.util.Arrays.copyOfRange(head, 1, head.length));
                fl = ints(tail);
                out.add(new Grid(tag, rows, cols, tx, pb, plane,
                        new ArrayList<>(ctxs), fa, fl));
            }
        }
        return out;
    }

    private static int[] ints(String[] parts) {
        int[] out = new int[parts.length];
        for (int i = 0; i < out.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }

    // --- second reader: context flow + encode + decode, all in Java ---

    /**
     * Re-derives the libaom oracle dump (G/C/A lines) from the pinned slice-3w
     * primitives + the od_ec port + the slice-3e decoder. Compared against the
     * C golden; the per-block context is asserted against the golden's C lines.
     */
    static List<String> goldenFacts() {
        return goldenFacts(grids());
    }

    static List<String> goldenFacts(List<Grid> gs) {
        List<String> out = new ArrayList<>();
        for (Grid g : gs) {
            int twu = Av1TxSupport.TXW[g.tx()] >> 2;
            int thu = Av1TxSupport.TXH[g.tx()] >> 2;
            int[] above = new int[g.cols() * twu];
            int[] left = new int[g.rows() * thu];
            Av1CoeffsSupport.Store store = new Av1CoeffsSupport.Store(80);
            Av1CoeffsSupport.Dec dec = new Av1CoeffsSupport.Dec(encode(g));
            out.add("G " + g.tag() + " " + g.rows() + " " + g.cols() + " " + g.tx()
                    + " " + g.pb() + " " + g.plane());
            for (int i = 0; i < g.ctxs().size(); i++) {
                Ctx c = g.ctxs().get(i);
                int[] a = slice(above, c.c4(), twu);
                int[] l = slice(left, c.r4(), thu);
                int[] ctx = getTxbCtx(a, l, twu, thu, g.pb(), g.tx(), g.plane());
                if (ctx[0] != c.skip() || ctx[1] != c.dc()) {
                    throw new IllegalStateException("context mismatch " + g.tag()
                            + " block " + i + " got " + ctx[0] + "," + ctx[1]
                            + " want " + c.skip() + "," + c.dc());
                }
                int[] row = Av1CoeffsSupport.readBlock(dec, store, g.tx(), 0,
                        g.plane(), c.dc(), c.skip());
                out.add("C " + c.r4() + " " + c.c4() + " " + ctx[0] + " " + ctx[1]
                        + " " + row[1] + " " + row[2]);
                int packed = av1TxbEntropyContext(row[1], row[2]);
                for (int k = 0; k < twu; k++) above[c.c4() + k] = packed;
                for (int k = 0; k < thu; k++) left[c.r4() + k] = packed;
            }
            out.add("A " + g.tag() + " " + join(above) + " | " + join(left));
        }
        return out;
    }

    /** The canonical dump the Kof probe must reproduce (P/A lines). */
    static List<String> kofExpected() {
        return kofExpected(grids());
    }

    static List<String> kofExpected(List<Grid> gs) {
        List<String> out = new ArrayList<>();
        for (Grid g : gs) {
            int twu = Av1TxSupport.TXW[g.tx()] >> 2;
            int thu = Av1TxSupport.TXH[g.tx()] >> 2;
            int[] above = new int[g.cols() * twu];
            int[] left = new int[g.rows() * thu];
            Av1CoeffsSupport.Store store = new Av1CoeffsSupport.Store(80);
            Av1CoeffsSupport.Dec dec = new Av1CoeffsSupport.Dec(encode(g));
            for (int i = 0; i < g.ctxs().size(); i++) {
                Ctx c = g.ctxs().get(i);
                int[] row = Av1CoeffsSupport.readBlock(dec, store, g.tx(), 0,
                        g.plane(), c.dc(), c.skip());
                int[] levels = new int[row.length - 3];
                System.arraycopy(row, 3, levels, 0, levels.length);
                out.add("P " + g.tag() + " " + i + " " + join(levels));
                int packed = av1TxbEntropyContext(row[1], row[2]);
                for (int k = 0; k < twu; k++) above[c.c4() + k] = packed;
                for (int k = 0; k < thu; k++) left[c.r4() + k] = packed;
            }
            out.add("A " + g.tag() + " " + join(above) + " | " + join(left));
        }
        return out;
    }

    static String kofExpectedText() {
        return kofExpectedText(grids());
    }

    static String kofExpectedText(List<Grid> gs) {
        return String.join("\n", kofExpected(gs));
    }

    static int[] encode(Grid g) {
        Av1CoeffsSupport.Store store = new Av1CoeffsSupport.Store(80);
        Av1CoeffPlaneEnc enc = new Av1CoeffPlaneEnc();
        int twu = Av1TxSupport.TXW[g.tx()] >> 2;
        int thu = Av1TxSupport.TXH[g.tx()] >> 2;
        int[] above = new int[g.cols() * twu];
        int[] left = new int[g.rows() * thu];
        for (Ctx c : g.ctxs()) {
            int[] a = slice(above, c.c4(), twu);
            int[] l = slice(left, c.r4(), thu);
            int[] ctx = getTxbCtx(a, l, twu, thu, g.pb(), g.tx(), g.plane());
            Block blk = blockLevels(c.cul(), c.dcCat(), g.tx());
            encodeBlock(enc, store, g.tx(), g.plane(), ctx[1], ctx[0],
                    blk.levels(), blk.eob());
            int packed = av1TxbEntropyContext(c.cul(), c.dcCat());
            for (int k = 0; k < twu; k++) above[c.c4() + k] = packed;
            for (int k = 0; k < thu; k++) left[c.r4() + k] = packed;
        }
        return enc.done();
    }

    record Block(int[] levels, int eob) {}

    /** Build a coefficient matrix that decodes to the given cul/dcCat. */
    static Block blockLevels(int cul, int dcCat, int tx) {
        int[] levels = new int[Av1TxSupport.TXW[Av1TxSupport.TXADJ[tx]]
                * Av1TxSupport.TXH[Av1TxSupport.TXADJ[tx]]];
        if (cul == 0) return new Block(levels, 0);
        if (dcCat == 0) {
            // DC zero, one AC coefficient equal to cul.
            levels[Av1CoeffsSupport.getScan(tx, 0)[1]] = cul;
            return new Block(levels, 2);
        }
        levels[0] = dcCat == 1 ? -cul : cul;
        return new Block(levels, 1);
    }

    static void encodeBlock(Av1CoeffPlaneEnc enc, Av1CoeffsSupport.Store store, int tx,
                            int plane, int dcSignCtx, int allZeroCtx,
                            int[] levels, int eob) {
        int ptype = plane > 0 ? 1 : 0;
        enc.symbol(store.txbSkipCdf(tx, allZeroCtx), 2, eob == 0 ? 1 : 0);
        if (eob == 0) return;
        int multi = Av1CoeffsSupport.eobMultiSize(tx);
        int ctx = (Av1CoeffsSupport.txClass(0) != 0 && multi < 5) ? 1 : 0;
        int n = new int[]{16, 32, 64, 128, 256, 512, 1024}[Math.min(multi, 6)];
        int nsym = new int[]{5, 6, 7, 8, 9, 10, 11}[Math.min(multi, 6)];
        int[] info = eobToken(eob);
        enc.symbol(store.eobPtCdf(n, ptype, ctx), nsym, info[0] - 1);
        int bits = Av1CoeffsSupport.eobOffsetBits(info[0]);
        if (bits > 0) {
            int eobCtx = info[0] - 3;
            enc.symbol(store.eobExtraCdf(tx, ptype, eobCtx), 2,
                    (info[1] >> (bits - 1)) & 1);
            for (int i = 1; i < bits; i++) enc.bit((info[1] >> (bits - 1 - i)) & 1);
        }
        int[] scan = Av1CoeffsSupport.getScan(tx, 0);
        int[] partial = new int[levels.length];
        for (int c = eob - 1; c >= 0; c--) {
            int pos = scan[c];
            int level = Math.abs(levels[pos]);
            if (c == eob - 1) {
                int cc = Av1CoeffCtxSupport.baseCtxQ(tx, 0, pos, c, true, partial) - 42 + 4;
                enc.symbol(store.coeffBaseEobCdf(tx, ptype, cc), 3, Math.min(level, 3) - 1);
            } else {
                int cc = Av1CoeffCtxSupport.baseCtxQ(tx, 0, pos, c, false, partial);
                enc.symbol(store.coeffBaseCdf(tx, ptype, cc), 4, Math.min(level, 3));
            }
            if (level > 2) {
                int baseRange = level - 3;
                int[] cdf = store.coeffBrCdf(tx, ptype,
                        Av1CoeffCtxSupport.brCtxQ(tx, 0, pos, partial));
                for (int idx = 0; idx < 12; idx += 3) {
                    int k = Math.min(baseRange - idx, 3);
                    enc.symbol(cdf, 4, k);
                    if (k < 3) break;
                }
            }
            partial[pos] = level;
        }
        for (int c = 0; c < eob; c++) {
            int pos = scan[c];
            int level = Math.abs(levels[pos]);
            int sign = levels[pos] < 0 ? 1 : 0;
            if (level != 0) {
                if (c == 0) enc.symbol(store.dcSignCdf(ptype, dcSignCtx), 2, sign);
                else enc.bit(sign);
                if (level > 14) golomb(enc, level - 15);
            }
        }
    }

    static int[] eobToken(int eob) {
        int[] starts = {0, 1, 2, 3, 5, 9, 17, 33, 65, 129, 257, 513, 1025};
        for (int t = 1; t <= 11; t++) {
            if (eob >= starts[t] && eob < starts[t + 1]) return new int[]{t, eob - starts[t]};
        }
        throw new IllegalStateException("eob " + eob);
    }

    static void golomb(Av1CoeffPlaneEnc enc, int level) {
        int x = level + 1;
        int length = 0;
        for (int i = x; i != 0; i >>= 1) length++;
        for (int i = 0; i < length - 1; i++) enc.bit(0);
        for (int i = length - 1; i >= 0; i--) enc.bit((x >> i) & 1);
    }

    // --- slice-3w primitives, re-derived in Java ---

    static int[] getTxbCtx(int[] above, int[] left, int twu, int thu,
                           int pb, int tx, int plane) {
        int dcSign = 0;
        for (int k = 0; k < twu; k++) dcSign += Av1CoeffCtxSupport.dcSignContribution(above[k] >> 3);
        for (int k = 0; k < thu; k++) dcSign += Av1CoeffCtxSupport.dcSignContribution(left[k] >> 3);
        int dcCtx = Av1CoeffCtxSupport.dcSignCtx(dcSign);
        int skip;
        if (plane == 0) {
            if (pb == TXTOBS[tx]) {
                skip = 0;
            } else {
                int top = 0, lft = 0;
                for (int k = 0; k < twu; k++) top = Math.max(top, above[k] & 7);
                for (int k = 0; k < thu; k++) lft = Math.max(lft, left[k] & 7);
                skip = Av1CoeffCtxSupport.allZeroCtxY(top, lft, B4W[pb], B4H[pb], twu, thu);
            }
        } else {
            boolean a = false, l = false;
            for (int k = 0; k < twu; k++) a |= above[k] != 0;
            for (int k = 0; k < thu; k++) l |= left[k] != 0;
            boolean bigger = NPELS[pb] > NPELS[TXTOBS[tx]];
            skip = Av1CoeffCtxSupport.allZeroCtxUV(a, l, bigger);
        }
        return new int[]{skip, dcCtx};
    }

    static int av1TxbEntropyContext(int culLevel, int dcCategory) {
        int cul = Math.min(culLevel, 7);
        if (dcCategory == 1) return cul + 8;
        if (dcCategory == 2) return cul + 16;
        return cul;
    }

    static int[] slice(int[] src, int off, int len) {
        int[] out = new int[len];
        System.arraycopy(src, off, out, 0, len);
        return out;
    }

    static String join(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    /** Kof probe source driving the Kof driver over every grid. */
    static String kofProbe() {
        return kofProbe(grids());
    }

    /**
     * Kof probe source for a subset of the grids. The cross native targets run
     * one grid per process: the conservative cross GC (known-bugs §602) misses a
     * live receiver once a single binary decodes ~20+ blocks, so a plane per
     * process is the documented workaround (the slice-3e proof splits the same
     * way). Output is byte-for-byte identical.
     */
    static String kofProbe(List<Grid> gs) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1CoeffPlane\n");
        sb.append("import image.Av1Coeffs\n");
        sb.append("import image.Av1Symbol\n\n");
        sb.append("void runGrid(String tag, Int pb, Int plane, ")
          .append("Int[] bytes, List<Int> grid, Int[] above, Int[] left) {\n");
        sb.append("    var store = Av1CoeffCdfStore(80)\n");
        sb.append("    var sym = Av1Symbol(bytes)\n");
        sb.append("    var out = av1DecodeCoeffsPlane(sym, store, pb, plane, grid, above, left)\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < out.size) {\n");
        sb.append("        var s = \"P \" + tag + \" \" + i\n");
        sb.append("        var k = 0\n");
        sb.append("        while (k < out[i].size) {\n");
        sb.append("            s = s + \" \" + out[i][k]\n");
        sb.append("            k = k + 1\n");
        sb.append("        }\n");
        sb.append("        println(s)\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    var s = \"A \" + tag\n");
        sb.append("    i = 0\n");
        sb.append("    while (i < above.size) {\n");
        sb.append("        s = s + \" \" + above[i]\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    s = s + \" |\"\n");
        sb.append("    i = 0\n");
        sb.append("    while (i < left.size) {\n");
        sb.append("        s = s + \" \" + left[i]\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    println(s)\n");
        sb.append("}\n\n");
        for (int gi = 0; gi < gs.size(); gi++) {
            Grid g = gs.get(gi);
            int[] bytes = encode(g);
            int twu = Av1TxSupport.TXW[g.tx()] >> 2;
            int thu = Av1TxSupport.TXH[g.tx()] >> 2;
            sb.append("void grid").append(gi).append("() {\n");
            sb.append("    var b = new Int[").append(bytes.length).append("]\n");
            for (int k = 0; k < bytes.length; k++) {
                sb.append("    b[").append(k).append("] = ").append(bytes[k]).append('\n');
            }
            sb.append("    var g = listOf<Int>(");
            for (int i = 0; i < g.ctxs().size(); i++) {
                Ctx c = g.ctxs().get(i);
                if (i > 0) sb.append(", ");
                sb.append(c.r4()).append(", ").append(c.c4()).append(", ")
                  .append(g.tx()).append(", 0");
            }
            sb.append(")\n");
            sb.append("    var a = new Int[").append(g.cols() * twu).append("]\n");
            sb.append("    var l = new Int[").append(g.rows() * thu).append("]\n");
            sb.append("    runGrid(\"").append(g.tag()).append("\", ").append(g.pb())
              .append(", ").append(g.plane()).append(", b, g, a, l)\n");
            sb.append("}\n\n");
        }
        sb.append("main() {\n");
        for (int gi = 0; gi < gs.size(); gi++) {
            sb.append("    grid").append(gi).append("()\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    // --- the od_ec range-encoder port lives in Av1CoeffPlaneEnc ---
}
