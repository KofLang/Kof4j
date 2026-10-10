package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the slice-3x edge-clamping face of the AV1
 * coefficient plane driver ({@code libs/image/Av1CoeffPlane.kf}). The recorded
 * slice-3x gap: libaom's {@code av1_set_entropy_contexts}
 * ({@code av1/common/blockd.c}) clamps the above/left context store to the
 * plane's right/bottom edge ({@code mb_to_right_edge}/{@code mb_to_bottom_edge}
 * via {@code max_block_wide}/{@code max_block_high}), zeroing the transform
 * units that lie outside the plane; the slice-3x golden grids were all exactly
 * plane-sized, so the clamp was never exercised.
 *
 * <p>The golden is the REAL libaom edge formula (the real
 * {@code block_size_wide}/{@code tx_size_wide_unit} tables and the real
 * {@code get_txb_ctx} from {@code av1/common/txb_common.h}) over grids whose
 * last column/row block overhangs the plane; the second reader here re-derives
 * the same flow in Java from the pinned slice-3w primitives. The dump is
 * gzip+base64 as {@code av1_coeffplane_edge_golden.txt.b64}.
 */
final class Av1CoeffPlaneEdgeSupport {

    private Av1CoeffPlaneEdgeSupport() {}

    static final String RESOURCE = "/av1_coeffplane_edge_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "d0bcf587c2e18e42211e1f7a3ad3fadbe7d5cbc4cf024b85bdcb28986c242bbb";

    record Ctx(int r4, int c4, int skip, int dc, int cul, int dcCat) {}

    record Grid(String tag, int rows, int cols, int tx, int pb, int plane,
                int planeW, int planeH, List<Ctx> ctxs,
                int[] finalAbove, int[] finalLeft) {}

    static String golden() {
        try (InputStream raw = Av1CoeffPlaneEdgeSupport.class.getResourceAsStream(RESOURCE)) {
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
        int rows = 0, cols = 0, tx = 0, pb = 0, plane = 0, pw = 0, ph = 0;
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
                pw = Integer.parseInt(f[7]);
                ph = Integer.parseInt(f[8]);
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
                out.add(new Grid(tag, rows, cols, tx, pb, plane, pw, ph,
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

    /** The clamped above-store (libaom {@code av1_set_entropy_contexts}). */
    static int[] storeAbove(int[] above, int c4, int twu, int packed, int planeW) {
        int limit = twu;
        if (planeW > 0) {
            limit = planeW - c4;
            if (limit > twu) limit = twu;
        }
        for (int k = 0; k < twu; k++) above[c4 + k] = (k < limit) ? packed : 0;
        return above;
    }

    static int[] storeLeft(int[] left, int r4, int thu, int packed, int planeH) {
        int limit = thu;
        if (planeH > 0) {
            limit = planeH - r4;
            if (limit > thu) limit = thu;
        }
        for (int k = 0; k < thu; k++) left[r4 + k] = (k < limit) ? packed : 0;
        return left;
    }

    /** Re-derives the libaom oracle dump (G/C/A lines) in Java. */
    static List<String> goldenFacts() {
        List<String> out = new ArrayList<>();
        for (Grid g : grids()) {
            int twu = Av1TxSupport.TXW[g.tx()] >> 2;
            int thu = Av1TxSupport.TXH[g.tx()] >> 2;
            int[] above = new int[g.cols() * twu];
            int[] left = new int[g.rows() * thu];
            Av1CoeffsSupport.Store store = new Av1CoeffsSupport.Store(80);
            Av1CoeffsSupport.Dec dec = new Av1CoeffsSupport.Dec(encode(g));
            out.add("G " + g.tag() + " " + g.rows() + " " + g.cols() + " " + g.tx()
                    + " " + g.pb() + " " + g.plane() + " " + g.planeW() + " " + g.planeH());
            for (int i = 0; i < g.ctxs().size(); i++) {
                Ctx c = g.ctxs().get(i);
                int[] a = Av1CoeffPlaneSupport.slice(above, c.c4(), twu);
                int[] l = Av1CoeffPlaneSupport.slice(left, c.r4(), thu);
                int[] ctx = Av1CoeffPlaneSupport.getTxbCtx(a, l, twu, thu, g.pb(),
                        g.tx(), g.plane());
                if (ctx[0] != c.skip() || ctx[1] != c.dc()) {
                    throw new IllegalStateException("context mismatch " + g.tag()
                            + " block " + i + " got " + ctx[0] + "," + ctx[1]
                            + " want " + c.skip() + "," + c.dc());
                }
                int[] row = Av1CoeffsSupport.readBlock(dec, store, g.tx(), 0,
                        g.plane(), c.dc(), c.skip());
                out.add("C " + c.r4() + " " + c.c4() + " " + ctx[0] + " " + ctx[1]
                        + " " + row[1] + " " + row[2]);
                int packed = Av1CoeffPlaneSupport.av1TxbEntropyContext(row[1], row[2]);
                storeAbove(above, c.c4(), twu, packed, g.planeW());
                storeLeft(left, c.r4(), thu, packed, g.planeH());
            }
            out.add("A " + g.tag() + " " + Av1CoeffPlaneSupport.join(above) + " | "
                    + Av1CoeffPlaneSupport.join(left));
        }
        return out;
    }

    /** The P/A lines the Kof driver must print. */
    static String kofExpectedText() {
        return kofExpectedText(grids());
    }

    static String kofExpectedText(List<Grid> gs) {
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
                out.add("P " + g.tag() + " " + i + " " + Av1CoeffPlaneSupport.join(levels));
                int packed = Av1CoeffPlaneSupport.av1TxbEntropyContext(row[1], row[2]);
                storeAbove(above, c.c4(), twu, packed, g.planeW());
                storeLeft(left, c.r4(), thu, packed, g.planeH());
            }
            out.add("A " + g.tag() + " " + Av1CoeffPlaneSupport.join(above) + " | "
                    + Av1CoeffPlaneSupport.join(left));
        }
        return String.join("\n", out);
    }

    static int[] encode(Grid g) {
        Av1CoeffsSupport.Store store = new Av1CoeffsSupport.Store(80);
        Av1CoeffPlaneEnc enc = new Av1CoeffPlaneEnc();
        int twu = Av1TxSupport.TXW[g.tx()] >> 2;
        int thu = Av1TxSupport.TXH[g.tx()] >> 2;
        int[] above = new int[g.cols() * twu];
        int[] left = new int[g.rows() * thu];
        for (Ctx c : g.ctxs()) {
            int[] a = Av1CoeffPlaneSupport.slice(above, c.c4(), twu);
            int[] l = Av1CoeffPlaneSupport.slice(left, c.r4(), thu);
            int[] ctx = Av1CoeffPlaneSupport.getTxbCtx(a, l, twu, thu, g.pb(),
                    g.tx(), g.plane());
            Av1CoeffPlaneSupport.Block blk = Av1CoeffPlaneSupport.blockLevels(
                    c.cul(), c.dcCat(), g.tx());
            Av1CoeffPlaneSupport.encodeBlock(enc, store, g.tx(), g.plane(),
                    ctx[1], ctx[0], blk.levels(), blk.eob());
            int packed = Av1CoeffPlaneSupport.av1TxbEntropyContext(c.cul(), c.dcCat());
            storeAbove(above, c.c4(), twu, packed, g.planeW());
            storeLeft(left, c.r4(), thu, packed, g.planeH());
        }
        return enc.done();
    }

    static String kofProbe() {
        return kofProbe(grids());
    }

    static String kofProbe(List<Grid> gs) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1CoeffPlane\n");
        sb.append("import image.Av1Coeffs\n");
        sb.append("import image.Av1Symbol\n\n");
        sb.append("void runEdgeGrid(String tag, Int pb, Int plane, Int pw, Int ph, ")
          .append("Int[] bytes, List<Int> grid, Int[] above, Int[] left) {\n");
        sb.append("    var store = Av1CoeffCdfStore(80)\n");
        sb.append("    var sym = Av1Symbol(bytes)\n");
        sb.append("    var out = av1DecodeCoeffsPlaneEdge(sym, store, pb, plane, grid, above, left, pw, ph)\n");
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
            sb.append("    runEdgeGrid(\"").append(g.tag()).append("\", ").append(g.pb())
              .append(", ").append(g.plane()).append(", ").append(g.planeW())
              .append(", ").append(g.planeH()).append(", b, g, a, l)\n");
            sb.append("}\n\n");
        }
        sb.append("main() {\n");
        for (int gi = 0; gi < gs.size(); gi++) {
            sb.append("    grid").append(gi).append("()\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
