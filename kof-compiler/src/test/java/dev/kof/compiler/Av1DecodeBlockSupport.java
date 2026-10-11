package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the slice-3y intra transform-block loop of the
 * AVIF front ({@code libs/image/Av1DecodeBlock.kf}). The enumeration is the
 * REAL libaom {@code decode_token_recon_block} intra branch
 * ({@code av1/decoder/decodeframe.c:908}): the block is chunked into
 * {@code BLOCK_64X64} units ({@code AOMMIN} with the block's
 * {@code max_block_wide/high}) and each chunk's plane bounds are
 * {@code ROUND_POWER_OF_TWO(AOMMIN(mu + row/col, max_blocks), subsampling)},
 * stepping the transform blocks by {@code tx_size_wide_unit}/{@code high_unit}.
 *
 * <p>The golden is that enumeration produced by the pinned libaom build (the
 * real {@code mi_size_wide/high}, {@code tx_size_wide/high_unit} and
 * {@code ROUND_POWER_OF_TWO} tables), plus a second reader here that re-derives
 * the same flow in Java and drives the coefficient encode over it. The dump is
 * gzip+base64 as {@code av1_decode_block_golden.txt.b64}.
 */
final class Av1DecodeBlockSupport {

    private Av1DecodeBlockSupport() {}

    static final String RESOURCE = "/av1_decode_block_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "a871f7f38a7f8d06eef5724f5787c7a54327cec08ea38f5b5bcc504c723946da";

    record Grid(String tag, int bsize, int tx, int ssX, int ssY,
                int mw, int mh, int plane, int pb, int pw, int ph) {}

    static String golden() {
        try (InputStream raw = Av1DecodeBlockSupport.class.getResourceAsStream(RESOURCE)) {
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
        for (String line : golden().split("\n")) {
            if (!line.startsWith("G ")) continue;
            String[] f = line.split(" ");
            out.add(new Grid(f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                    Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                    Integer.parseInt(f[6]), Integer.parseInt(f[7]),
                    Integer.parseInt(f[8]), Integer.parseInt(f[9]),
                    Integer.parseInt(f[10]), Integer.parseInt(f[11])));
        }
        return out;
    }

    /** The libaom enumeration golden (the B lines), grouped per grid tag. */
    static List<String> goldenBlocks() {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("B ")) out.add(line);
        }
        return out;
    }

    /** {@code ROUND_POWER_OF_TWO(value, n)} ({@code aom_ports/mem.h}). */
    static int roundPow2(int value, int n) {
        return n == 0 ? value : (value + (1 << (n - 1))) >> n;
    }

    /** The {@code decode_token_recon_block} enumeration, re-derived in Java. */
    static List<int[]> enumerate(Grid g) {
        int muW = Math.min(g.mw(), 16);
        int muH = Math.min(g.mh(), 16);
        int stepc = Av1TxSupport.TXW[g.tx()] >> 2;
        int stepr = Av1TxSupport.TXH[g.tx()] >> 2;
        List<int[]> out = new ArrayList<>();
        for (int row = 0; row < g.mh(); row += muH) {
            for (int col = 0; col < g.mw(); col += muW) {
                int unitH = roundPow2(Math.min(muH + row, g.mh()), g.ssY());
                int unitW = roundPow2(Math.min(muW + col, g.mw()), g.ssX());
                for (int br = row >> g.ssY(); br < unitH; br += stepr) {
                    for (int bc = col >> g.ssX(); bc < unitW; bc += stepc) {
                        out.add(new int[]{br, bc});
                    }
                }
            }
        }
        return out;
    }

    /** The libaom B lines for one grid, re-derived (B tag r4 c4). */
    static List<String> enumerateLines(Grid g) {
        List<String> out = new ArrayList<>();
        for (int[] b : enumerate(g)) {
            out.add("B " + g.tag() + " " + b[0] + " " + b[1]);
        }
        return out;
    }

    /**
     * Re-derives the whole oracle in Java: the enumeration (asserted against
     * the libaom B lines) plus a coefficient encode driven over it, producing
     * the reader bytes the Kof driver consumes and the expected store.
     */
    static List<String> goldenFacts() {
        List<String> out = new ArrayList<>();
        for (Grid g : grids()) {
            List<String> lines = enumerateLines(g);
            out.addAll(lines);
        }
        List<String> pinned = goldenBlocks();
        if (!out.equals(pinned)) {
            throw new IllegalStateException("enumeration mismatch: re-derived "
                    + out.size() + " lines vs libaom " + pinned.size());
        }
        return out;
    }

    static int[] encode(Grid g) {
        Av1CoeffsSupport.Store store = new Av1CoeffsSupport.Store(80);
        Av1CoeffPlaneEnc enc = new Av1CoeffPlaneEnc();
        int twu = Av1TxSupport.TXW[g.tx()] >> 2;
        int thu = Av1TxSupport.TXH[g.tx()] >> 2;
        int[] above = new int[g.pw() * twu];
        int[] left = new int[g.ph() * thu];
        List<int[]> blocks = enumerate(g);
        for (int i = 0; i < blocks.size(); i++) {
            int br = blocks.get(i)[0];
            int bc = blocks.get(i)[1];
            int[] a = Av1CoeffPlaneSupport.slice(above, bc, twu);
            int[] l = Av1CoeffPlaneSupport.slice(left, br, thu);
            int[] ctx = Av1CoeffPlaneSupport.getTxbCtx(a, l, twu, thu, g.pb(),
                    g.tx(), g.plane());
            int cul = (i % 4) + 1;
            int dcCat = i % 3;
            Av1CoeffPlaneSupport.Block blk = Av1CoeffPlaneSupport.blockLevels(
                    cul, dcCat, g.tx());
            Av1CoeffPlaneSupport.encodeBlock(enc, store, g.tx(), g.plane(),
                    ctx[1], ctx[0], blk.levels(), blk.eob());
            int packed = Av1CoeffPlaneSupport.av1TxbEntropyContext(cul, dcCat);
            Av1CoeffPlaneEdgeSupport.storeAbove(above, bc, twu, packed, g.pw());
            Av1CoeffPlaneEdgeSupport.storeLeft(left, br, thu, packed, g.ph());
        }
        return enc.done();
    }

    /** The expected store line after the whole grid (A tag above | left). */
    static String storeLine(Grid g) {
        int twu = Av1TxSupport.TXW[g.tx()] >> 2;
        int thu = Av1TxSupport.TXH[g.tx()] >> 2;
        int[] above = new int[g.pw() * twu];
        int[] left = new int[g.ph() * thu];
        List<int[]> blocks = enumerate(g);
        for (int i = 0; i < blocks.size(); i++) {
            int br = blocks.get(i)[0];
            int bc = blocks.get(i)[1];
            int cul = (i % 4) + 1;
            int dcCat = i % 3;
            int packed = Av1CoeffPlaneSupport.av1TxbEntropyContext(cul, dcCat);
            Av1CoeffPlaneEdgeSupport.storeAbove(above, bc, twu, packed, g.pw());
            Av1CoeffPlaneEdgeSupport.storeLeft(left, br, thu, packed, g.ph());
        }
        return "A " + g.tag() + " " + Av1CoeffPlaneSupport.join(above) + " | "
                + Av1CoeffPlaneSupport.join(left);
    }

    static String kofExpectedText() {
        return kofExpectedText(grids());
    }

    static String kofExpectedText(List<Grid> gs) {
        List<String> out = new ArrayList<>();
        for (Grid g : gs) {
            out.addAll(enumerateLines(g));
            out.add(storeLine(g));
        }
        return String.join("\n", out);
    }

    static String kofProbe() {
        return kofProbe(grids());
    }

    static String kofProbe(List<Grid> gs) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1DecodeBlock\n");
        sb.append("import image.Av1Coeffs\n");
        sb.append("import image.Av1Symbol\n\n");
        sb.append("void runBlockGrid(String tag, Int pb, Int plane, Int tx, Int txType, ")
          .append("Int ssX, Int ssY, Int mw, Int mh, Int pw, Int ph, ")
          .append("Int[] bytes, Int[] above, Int[] left) {\n");
        sb.append("    var order = av1DecodeBlockGridOrder(tx, ssX, ssY, mw, mh)\n");
        sb.append("    var k = 0\n");
        sb.append("    while (k < order.size) {\n");
        sb.append("        println(\"B \" + tag + \" \" + order.get(k) + \" \" + order.get(k + 1))\n");
        sb.append("        k = k + 2\n");
        sb.append("    }\n");
        sb.append("    var store = Av1CoeffCdfStore(80)\n");
        sb.append("    var sym = Av1Symbol(bytes)\n");
        sb.append("    av1DecodeBlockGrid(sym, store, pb, plane, tx, txType, ssX, ssY, mw, mh, pw, ph, above, left)\n");
        sb.append("    var s = \"A \" + tag\n");
        sb.append("    var i = 0\n");
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
            sb.append("    var a = new Int[").append(g.pw() * twu).append("]\n");
            sb.append("    var l = new Int[").append(g.ph() * thu).append("]\n");
            sb.append("    runBlockGrid(\"").append(g.tag()).append("\", ").append(g.pb())
              .append(", ").append(g.plane()).append(", ").append(g.tx())
              .append(", 0, ").append(g.ssX()).append(", ").append(g.ssY())
              .append(", ").append(g.mw()).append(", ").append(g.mh())
              .append(", ").append(g.pw()).append(", ").append(g.ph())
              .append(", b, a, l)\n");
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
