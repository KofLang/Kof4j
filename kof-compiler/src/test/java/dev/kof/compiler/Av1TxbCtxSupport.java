package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3w (the AV1 coefficient entropy
 * context, {@code libs/image/Av1CoeffCtx.kf}). The Kof module implements
 * libaom's {@code av1_get_txb_entropy_context} (the per-block context stored
 * after decoding: {@code Min(7, culLevel)} with the DC sign packed on top) and
 * {@code get_txb_ctx} (the {@code all_zero} / {@code dc_sign} neighbour-context
 * selection for the coefficient walk), plus the {@code txsize_to_bsize} table
 * ({@code libs/image/Av1Block.kf}).
 *
 * <p>This class pins the Kof output against the reference libaom code built on
 * the dev host: {@code av1_get_txb_entropy_context} /
 * {@code set_dc_sign} ({@code av1/encoder/encodetxb.c}, {@code
 * av1/common/txb_common.h}) over 200 seeded coefficient blocks, and the REAL
 * {@code get_txb_ctx} ({@code av1/common/txb_common.h}) over 400 seeded
 * {@code (txSz, planeBsize, plane, above[], left[])} cases. The dump is
 * gzip-compressed and base64-encoded as the test resource
 * {@code av1_txbctx_golden.txt.b64}.
 *
 * <p>The Kof probe is generated from the pinned golden so the two can never
 * drift: each {@code E}/{@code I} input becomes a literal call to the Kof
 * library, and the probe must print the golden verbatim.
 */
final class Av1TxbCtxSupport {

    private Av1TxbCtxSupport() {}

    static final String RESOURCE = "/av1_txbctx_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "a573190ca86f5287ea3a1747284894b580a99f7cd20ddc6fad04271f498cbe2b";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1TxbCtxSupport.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("missing test resource " + RESOURCE);
            }
            byte[] b64 = raw.readAllBytes();
            StringBuilder sb = new StringBuilder(b64.length);
            for (byte b : b64) {
                char c = (char) (b & 0xFF);
                if (c != '\n' && c != '\r' && c != ' ' && c != '\t') {
                    sb.append(c);
                }
            }
            byte[] gz = Base64.getDecoder().decode(sb.toString());
            try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    /** Kof probe source generated from the golden, driving the Kof library. */
    static String kofProbe() {
        List<String> eLines = new ArrayList<>();
        List<String> iLines = new ArrayList<>();
        boolean inB = false;
        for (String line : golden().split("\n")) {
            if (line.equals("A")) continue;
            if (line.equals("B")) { inB = true; continue; }
            if (line.startsWith("E ")) eLines.add(line);
            else if (line.startsWith("I ")) iLines.add(line);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1CoeffCtx\n");
        sb.append("import image.Av1Tx\n\n");
        sb.append("void emitEntropy(Int cul, Int dc) {\n");
        sb.append("    println(\"E \" + cul + \" \" + dc + \" \" + av1TxbEntropyContext(cul, dc))\n");
        sb.append("}\n\n");
        sb.append("void emitCtx(Int tx, Int pb, Int plane, List<Int> av, List<Int> lv) {\n");
        sb.append("    var twu = av1TxWidth(tx) >> 2\n");
        sb.append("    var thu = av1TxHeight(tx) >> 2\n");
        sb.append("    var above = new Int[twu]\n");
        sb.append("    var left = new Int[thu]\n");
        sb.append("    var k = 0\n");
        sb.append("    while (k < twu) {\n");
        sb.append("        above[k] = av.get(k)\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < thu) {\n");
        sb.append("        left[k] = lv.get(k)\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    var ctx = av1TxbCtx(above, left, twu, thu, pb, tx, plane)\n");
        sb.append("    var s = \"I \" + tx + \" \" + pb + \" \" + plane\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < twu) {\n");
        sb.append("        s = s + \" \" + above[k]\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    s = s + \" |\"\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < thu) {\n");
        sb.append("        s = s + \" \" + left[k]\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    s = s + \" \" + ctx.get(0) + \" \" + ctx.get(1)\n");
        sb.append("    println(s)\n");
        sb.append("}\n\n");
        sb.append("main() {\n");
        sb.append("    println(\"A\")\n");
        for (String line : eLines) {
            String[] f = line.split(" ");
            sb.append("    emitEntropy(").append(f[1]).append(", ").append(f[2]).append(")\n");
        }
        sb.append("    println(\"B\")\n");
        for (String line : iLines) {
            sb.append(emitCtxCall(line));
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String emitCtxCall(String line) {
        // I <tx> <pb> <plane> <a...> | <l...> <skip> <dc>
        int bar = line.indexOf('|');
        String[] head = line.substring(2, bar).trim().split(" ");
        String[] tail = line.substring(bar + 1).trim().split(" ");
        int tx = Integer.parseInt(head[0]);
        int pb = Integer.parseInt(head[1]);
        int plane = Integer.parseInt(head[2]);
        int twu = head.length - 3;
        int thu = tail.length - 2;
        StringBuilder av = new StringBuilder("listOf(");
        for (int i = 0; i < twu; i++) {
            if (i > 0) av.append(", ");
            av.append(head[3 + i]);
        }
        av.append(")");
        StringBuilder lv = new StringBuilder("listOf(");
        for (int i = 0; i < thu; i++) {
            if (i > 0) lv.append(", ");
            lv.append(tail[i]);
        }
        lv.append(")");
        return "    emitCtx(" + tx + ", " + pb + ", " + plane + ", " + av + ", " + lv + ")\n";
    }
}
