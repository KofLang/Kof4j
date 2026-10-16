package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3m (AV1 default mode-info CDFs,
 * {@code libs/image/Av1ModeCdf.kf}). The Kof module carries the non-coefficient
 * entropy tables the tile block syntax reads — the partition tree, the intra
 * frame Y mode, the Y/UV mode, angle delta, filter-intra, the segment/skip/tx
 * size/delta-q/delta-lf symbols, the CFL sign/alpha and the transform-partition
 * split — from the AV1 Bitstream &amp; Decoding Process Specification §10.
 *
 * <p>The golden is derived from libaom's {@code av1/common/entropymode.c}
 * default CDF arrays (the {@code AOM_CDFn} macros expanded to the spec shape
 * {@code [symbols..., 32768, 0]}), extracted and cross-checked against the spec
 * markdown: 15 tables, zero differences. The dump is gzip-compressed and
 * base64-encoded as the test resource {@code av1_mode_cdf_golden.txt.b64}.
 */
final class Av1ModeCdfSupport {

    private Av1ModeCdfSupport() {}

    static final String RESOURCE = "/av1_mode_cdf_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "5e6a448d0cf17c229ca718ef4edc6b68697b9216bfb040469bb4d247d839e41d";

    /**
     * The six mode CDFs the base {@link #golden()} dump does NOT cover — segment
     * id, segment id predicted, palette Y/UV size and palette Y/UV mode —
     * derived the same way from libaom ({@code default_spatial_pred_seg_tree_cdf},
     * {@code default_segment_pred_cdf}, {@code default_palette_{y,uv}_size_cdf},
     * {@code default_palette_{y,uv}_mode_cdf}), expanded to the spec CDF shape.
     */
    static final String RESOURCE_EXT = "/av1_mode_cdf_ext_golden.txt.b64";

    /** Expected SHA-256 of the decoded EXT golden text, to catch a stale resource. */
    static final String GOLDEN_EXT_SHA256 =
        "b324b339e9b60c40285c89774abd2eafce9db6160b70d3cb7e71f7a0f07fd7a8";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        return load(RESOURCE);
    }

    /** The reference dump the EXT probe must reproduce (segment + palette CDFs). */
    static String goldenExt() {
        return load(RESOURCE_EXT);
    }

    private static String load(String resource) {
        try (InputStream raw = Av1ModeCdfSupport.class.getResourceAsStream(resource)) {
            if (raw == null) {
                throw new IllegalStateException("missing test resource " + resource);
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
            throw new IllegalStateException("cannot read " + resource, e);
        }
    }

    /** Kof probe source that dumps every mode-info CDF row. */
    static String kofProbe() {
        return PROBE;
    }

    /** Kof probe source that dumps the segment + palette CDF rows. */
    static String kofProbeExt() {
        return PROBE_EXT;
    }

    private static final String PROBE_EXT = """
import image.Av1ModeCdf

String row(String tag, Int r, Int[] c) {
    var line = tag + " " + r
    var i = 0
    while (i < c.size) {
        line = line + " " + c[i]
        i = i + 1
    }
    return line
}

main() {
    var m = Av1ModeCdf()
    var sg = 0
    while (sg < 3) {
        println(row("SEG", sg, m.segmentId(sg)))
        sg = sg + 1
    }
    var sp = 0
    while (sp < 3) {
        println(row("SEGP", sp, m.segmentIdPred(sp)))
        sp = sp + 1
    }
    var ys = 0
    while (ys < 7) {
        println(row("PYS", ys, m.paletteYSize(ys)))
        ys = ys + 1
    }
    var us = 0
    while (us < 7) {
        println(row("PUS", us, m.paletteUvSize(us)))
        us = us + 1
    }
    var ym = 0
    while (ym < 21) {
        println(row("PYM", ym, m.paletteYMode(ym / 3, ym % 3)))
        ym = ym + 1
    }
    var um = 0
    while (um < 2) {
        println(row("PUM", um, m.paletteUvMode(um)))
        um = um + 1
    }
    var py = 0
    while (py < 7) {
        var pc = 0
        while (pc < 5) {
            println(row("PYC", py * 5 + pc, m.paletteYColorIndex(py, pc)))
            println(row("PUC", py * 5 + pc, m.paletteUvColorIndex(py, pc)))
            pc = pc + 1
        }
        py = py + 1
    }
}
""";

    private static final String PROBE = """
import image.Av1ModeCdf

String row(String tag, Int r, Int[] c) {
    var line = tag + " " + r
    var i = 0
    while (i < c.size) {
        line = line + " " + c[i]
        i = i + 1
    }
    return line
}

main() {
    var m = Av1ModeCdf()
    var bsl = 1
    while (bsl <= 5) {
        var c = 0
        while (c < 4) {
            println(row("PART", c, m.partitionW(bsl, c)))
            c = c + 1
        }
        bsl = bsl + 1
    }
    var a = 0
    while (a < 5) {
        var l = 0
        while (l < 5) {
            println(row("KFY", a * 5 + l, m.kfY(a, l)))
            l = l + 1
        }
        a = a + 1
    }
    var g = 0
    while (g < 4) {
        println(row("IFY", g, m.ifY(g)))
        g = g + 1
    }
    var um = 0
    while (um < 13) {
        println(row("UVN", um, m.uv(0, um)))
        um = um + 1
    }
    um = 0
    while (um < 13) {
        println(row("UVA", um, m.uv(1, um)))
        um = um + 1
    }
    var am = 0
    while (am < 8) {
        println(row("ANG", am, m.angle(am)))
        am = am + 1
    }
    println(row("FIM", 0, m.filterMode()))
    var bs = 0
    while (bs < 22) {
        println(row("FI", bs, m.filterIntra(bs)))
        bs = bs + 1
    }
    var sc = 0
    while (sc < 3) {
        println(row("SKIP", sc, m.skip(sc)))
        sc = sc + 1
    }
    println(row("IB", 0, m.intrabc()))
    println(row("DQ", 0, m.deltaQ()))
    println(row("DL", 0, m.deltaLf()))
    var tc = 0
    while (tc < 3) {
        println(row("TX0", tc, m.txSize(0, tc)))
        tc = tc + 1
    }
    tc = 0
    while (tc < 3) {
        println(row("TX1", tc, m.txSize(1, tc)))
        tc = tc + 1
    }
    tc = 0
    while (tc < 3) {
        println(row("TX2", tc, m.txSize(2, tc)))
        tc = tc + 1
    }
    tc = 0
    while (tc < 3) {
        println(row("TX3", tc, m.txSize(3, tc)))
        tc = tc + 1
    }
    println(row("CFS", 0, m.cflSign()))
    var cc = 0
    while (cc < 6) {
        println(row("CFA", cc, m.cflAlpha(cc)))
        cc = cc + 1
    }
    var xc = 0
    while (xc < 21) {
        println(row("TXP", xc, m.txfmSplit(xc)))
        xc = xc + 1
    }
}
""";
}
