package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the slice-3ag AV1 intra tile driver
 * ({@code libs/image/Av1DecodeTileRecon.kf}).
 *
 * <p>The Kof driver composes the stage libaom runs as {@code parse_decode_block}:
 * per block, the mode info (slices 3p/3q/3r), the transform size (3s) and THEN
 * the same block's coefficients (3e/3x), availability (3ab), prediction (3u) and
 * reconstruction (3z) — from ONE shared entropy stream, so each block sees the
 * CDF adaptations and the reconstructed samples the previous blocks left behind.
 *
 * <p>The golden is the REAL libaom path: a C encoder writes the mode info, the
 * transform size and the coefficients in the exact order libaom reads them, with
 * libaom's OWN default CDFs ({@code av1_init_mode_probs}/{@code
 * av1_default_coef_probs}) and its OWN range encoder; the oracle then decodes
 * that stream with libaom's REAL {@code read_intra_frame_mode_info} +
 * {@code read_tx_size} + {@code av1_read_coeffs_txb} + the
 * {@code av1_predict_intra_block} builders + {@code av1_highbd_inv_txfm_add_c},
 * dumping each block's decisions, each transform block's EOB, the final entropy
 * context and the reconstructed plane. The dump is gzip+base64 as
 * {@code av1_decode_tile_recon_golden.txt.b64}.
 *
 * <p>Format per case: {@code C ci bd lossless txMode nblk mw mh}, one
 * {@code P ci r c bs} per block, {@code T ci bytes...}, one {@code D ci b skip
 * seg txSz mode angleY uvMode angleUV ufi fim qIndex} per block, one {@code E ci
 * b gi eob} per transform block, {@code A ci above... | left...} and {@code mh}
 * rows.
 */
final class Av1DecodeTileReconSupport {

    private Av1DecodeTileReconSupport() {}

    static final String RESOURCE = "/av1_decode_tile_recon_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "202acbb32920089506ba7ef69bb4f4810605160662ea90e5848cbd4e62feedb1";

    /** One mode-info block: {@code P} line. */
    record Blk(int r, int c, int bs) {}

    /** One oracle case: the {@code C} header plus its blocks. */
    record Case(int ci, int bd, int lossless, int txMode, int nblk, int mw, int mh,
                List<Blk> blocks) {}

    static String golden() {
        try (InputStream raw = Av1DecodeTileReconSupport.class.getResourceAsStream(RESOURCE)) {
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
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
                String actual = sha256(text);
                if (!actual.equals(GOLDEN_SHA256)) {
                    throw new IllegalStateException(
                            "stale golden " + RESOURCE + ": expected " + GOLDEN_SHA256
                                    + " but hashed " + actual);
                }
                return text;
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * {@code known-bugs} §761: the golden's case 2 (the "skip path") is
     * NON-CONFORMANT — its stream carries transform-block syntax for a
     * {@code skip_txfm=1} block (the oracle's writer emits it and the oracle
     * calls {@code av1_read_coeffs_txb} directly, without libaom's
     * {@code read_coeffs_tx_intra_block} guard). A real encoder writes nothing
     * for a skipped block (measured on
     * {@code avif_enc_nopal_cu0_2tile192.avif}: the skipped block's span is
     * ZERO bits), so the case is EXCLUDED by name here; cases 0/1 stay pinned,
     * and the skip semantics are pinned by the real-stream test
     * {@code AvifFullPlaneE2ETest#skippedBlockReadsNoCoefficientSymbols}.
     */
    private static final int EXCLUDED_NON_CONFORMANT_SKIP_CASE = 2;

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        Case cur = null;
        List<Blk> blocks = null;
        for (String line : golden().split("\n")) {
            String[] f = line.split(" ");
            if (f[0].equals("C")) {
                if (cur != null && cur.ci() != EXCLUDED_NON_CONFORMANT_SKIP_CASE) {
                    out.add(finish(cur, blocks));
                }
                cur = new Case(Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                        Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                        Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                        Integer.parseInt(f[7]), null);
                blocks = new ArrayList<>();
            } else if (f[0].equals("P")) {
                blocks.add(new Blk(Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        Integer.parseInt(f[4])));
            }
        }
        if (cur != null && cur.ci() != EXCLUDED_NON_CONFORMANT_SKIP_CASE) {
            out.add(finish(cur, blocks));
        }
        return out;
    }

    private static Case finish(Case c, List<Blk> blocks) {
        return new Case(c.ci(), c.bd(), c.lossless(), c.txMode(), c.nblk(),
                c.mw(), c.mh(), List.copyOf(blocks));
    }

    /** The tile bytes of case {@code ci} (the {@code T} line). */
    static int[] bytes(int ci) {
        for (String line : golden().split("\n")) {
            if (line.startsWith("T " + ci + " ")) {
                String[] f = line.split(" ");
                int[] out = new int[f.length - 2];
                for (int i = 2; i < f.length; i++) out[i - 2] = Integer.parseInt(f[i]);
                return out;
            }
        }
        throw new IllegalStateException("no T line for case " + ci);
    }

    /** Case {@code ci}'s expected lines: {@code D}/{@code E}/{@code A} and the plane rows. */
    private static List<String> expected(int ci) {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("D " + ci + " ") || line.startsWith("A " + ci + " ")) {
                out.add(line);
            } else if (line.startsWith("R " + ci + " ")) {
                /* "R ci r v0 v1 ..." -> "v0 v1 ..." (what the Kof probe prints). */
                String[] f = line.split(" ");
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < f.length; i++) {
                    if (i > 3) sb.append(' ');
                    sb.append(f[i]);
                }
                out.add(sb.toString());
            }
        }
        return out;
    }

    static String expectedText() {
        List<String> out = new ArrayList<>();
        for (Case c : cases()) out.addAll(expected(c.ci()));
        return String.join("\n", out);
    }

    static List<Integer> allCaseIndices() {
        List<Integer> out = new ArrayList<>();
        for (Case c : cases()) out.add(c.ci());
        return out;
    }

    static String kofProbe() {
        return kofProbe(allCaseIndices());
    }

    /**
     * Kof probe source. Each case is a self-contained {@code reconTileCase} call, so
     * the cross native faces can run one case per process (the documented
     * {@code known-bugs} §602 workaround). Output is byte-for-byte identical.
     */
    static String kofProbe(List<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1DecodeTileRecon\n");
        sb.append("import image.Av1DecodeChroma\n");
        sb.append("import image.Av1ModeInfo\n");
        sb.append("import image.Av1Symbol\n");
        sb.append("import image.Av1TxType\n\n");
        sb.append("void reconTileCase(Int ci, Int bd, Int lossless, Int txMode, Int miw, ")
          .append("Int mih, Int[] data, List<Int> blocks) {\n");
        sb.append("    var mw = miw << 2\n");
        sb.append("    var mh = mih << 2\n");
        sb.append("    var stride = mw + 2\n");
        sb.append("    var rows = mh + 2\n");
        sb.append("    var frame = new Int[stride * rows]\n");
        sb.append("    var mask = (1 << bd) - 1\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < frame.size) {\n");
        sb.append("        frame[i] = (i * 37 + 11) & mask\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    var sym = Av1Symbol(data)\n");
        sb.append("    var store = Av1CoeffCdfStore(80)\n");
        sb.append("    var txStore = Av1TxType(sym)\n");
        sb.append("    var txTypes = new Int[(mw >> 2) * (mh >> 2)]\n");
        sb.append("    var above = new Int[miw]\n");
        sb.append("    var left = new Int[mih]\n");
        sb.append("    var levels = listOf<Int[]>()\n");
        sb.append("    var txTypeOut = listOf<Int>()\n");
        sb.append("    var modes = listOf<Int>()\n");
        sb.append("    var cfg = Av1ModeTail(12, 0, 0, 0, 0, 0, 0, 0, 0, 3)\n");
        /* Luma-only cases: `numPlanes == 1`, so the chroma walk never runs and the
         * bundle is unused (libaom's `av1_num_planes(cm)`). */
        sb.append("    var chroma = Av1ChromaPlanes(new Int[1], 1, 0, 0, new Int[1], new Int[1], ")
          .append("new Int[1], 1, 0, 0, new Int[1], new Int[1])\n");
        sb.append("    var levelsU = listOf<Int[]>()\n");
        sb.append("    var levelsV = listOf<Int[]>()\n");
        sb.append("    var txU = listOf<Int>()\n");
        sb.append("    var txV = listOf<Int>()\n");
        sb.append("    var out = av1DecodeTileRecon(frame, stride, mw, mh, blocks, cfg, ")
          .append("lossless, 1, 1, 0, 0, bd, txMode, 80, 0, 0, 0, 0, 74, 87, 1, 0, 0, ")
          .append("sym, store, txStore, txTypes, above, left, levels, txTypeOut, modes, ")
          .append("chroma, 0, 0, levelsU, levelsV, txU, txV)\n");
        sb.append("    var nb = blocks.size / 3\n");
        sb.append("    var b = 0\n");
        sb.append("    while (b < nb) {\n");
        sb.append("        var o = b * 17\n");
        sb.append("        println(\"D \" + ci + \" \" + b + \" \" + modes[o] + \" \" ")
          .append("+ modes[o + 1] + \" \" + modes[o + 4] + \" \" + modes[o + 5] + \" \" ")
          .append("+ modes[o + 6] + \" \" + modes[o + 7] + \" \" + modes[o + 8] + \" \" ")
          .append("+ modes[o + 11] + \" \" + modes[o + 12])\n");
        sb.append("        b = b + 1\n");
        sb.append("    }\n");
        sb.append("    var k = 0\n");
        sb.append("    var a = \"A \" + ci\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < above.size) {\n");
        sb.append("        a = a + \" \" + above[k]\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    a = a + \" |\"\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < left.size) {\n");
        sb.append("        a = a + \" \" + left[k]\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    println(a)\n");
        sb.append("    var r = 0\n");
        sb.append("    while (r < mh) {\n");
        sb.append("        var line = \"\"\n");
        sb.append("        var col = 0\n");
        sb.append("        while (col < mw) {\n");
        sb.append("            if (col > 0) {\n");
        sb.append("                line = line + \" \"\n");
        sb.append("            }\n");
        sb.append("            line = line + out[r * mw + col]\n");
        sb.append("            col = col + 1\n");
        sb.append("        }\n");
        sb.append("        println(line)\n");
        sb.append("        r = r + 1\n");
        sb.append("    }\n");
        sb.append("}\n\n");
        sb.append("main() {\n");
        for (Case c : cases()) {
            if (!indices.contains(c.ci())) continue;
            int[] b = bytes(c.ci());
            sb.append("    var b").append(c.ci()).append(" = new Int[")
              .append(b.length).append("]\n");
            for (int k = 0; k < b.length; k++) {
                sb.append("    b").append(c.ci()).append('[').append(k).append("] = ")
                  .append(b[k]).append('\n');
            }
            sb.append("    var blk").append(c.ci()).append(" = listOf<Int>(");
            boolean first = true;
            for (Blk blk : c.blocks()) {
                for (int v : new int[]{blk.r(), blk.c(), blk.bs()}) {
                    if (!first) sb.append(", ");
                    sb.append(v);
                    first = false;
                }
            }
            sb.append(")\n");
            sb.append("    reconTileCase(").append(c.ci()).append(", ").append(c.bd())
              .append(", ").append(c.lossless()).append(", ").append(c.txMode())
              .append(", ").append(c.mw()).append(", ").append(c.mh())
              .append(", b").append(c.ci()).append(", blk").append(c.ci()).append(")\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
