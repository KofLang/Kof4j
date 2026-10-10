package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3j (AV1 base intra prediction,
 * {@code libs/image/Av1Intra.kf}). The Kof module is a direct transcription of
 * AV1 Bitstream &amp; Decoding Process Specification §7.11.2 (the base intra
 * prediction process): the DC (§7.11.2.5), V/H, PAETH (§7.11.2.2) and
 * SMOOTH/SMOOTH_V/SMOOTH_H (§7.11.2.6) predictors.
 *
 * <p>This class pins the Kof output against the reference libaom kernels
 * ({@code aom_dsp/intrapred.c}) built on the dev host: the REAL
 * {@code aom_{v,h,smooth,smooth_v,smooth_h,paeth,dc,dc_128,dc_top,dc_left}_predictor_WxH_c}
 * and their {@code aom_highbd_*} variants, called over every AV1 transform
 * size (square + rectangular), all three bit depths (8/10/12) and all four
 * {@code haveAbove}/{@code haveLeft} combinations. The dump is
 * gzip-compressed and base64-encoded as the test resource
 * {@code av1_intra_golden.txt.b64}.
 *
 * <p>The edge arrays are a deterministic function of the case index, so the
 * Kof probe and the libaom harness build byte-identical inputs; the spec's
 * missing-edge substitutions (mirror of the available edge, or the
 * {@code (1<<(BitDepth-1))-1}/{@code +1} pair when both are absent) are
 * applied before prediction.
 */
final class Av1IntraSupport {

    private Av1IntraSupport() {}

    static final String RESOURCE = "/av1_intra_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "1db200be121ff9884d358b9e039c851b3861d4e2c9a52d3e32dc3f3e7ab68b2b";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1IntraSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that dumps every intra prediction block. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1Intra

main() {
    var sizesW = listOf(4, 8, 16, 32, 64, 4, 8, 8, 16, 16, 32, 16, 64, 32, 64, 4, 16, 8, 32)
    var sizesH = listOf(4, 8, 16, 32, 64, 8, 4, 16, 8, 32, 16, 64, 16, 64, 32, 16, 4, 32, 8)
    var modes = listOf(0, 1, 2, 9, 10, 11, 12)
    var bi = 0
    while (bi < 3) {
        var bd = 8 + 2 * bi
        var span = 1 << bd
        var si = 0
        while (si < 19) {
            var w = sizesW.get(si)
            var h = sizesH.get(si)
            var mi = 0
            while (mi < 7) {
                var mode = modes.get(mi)
                var fl = 0
                while (fl < 4) {
                    var haveA = (fl & 1) != 0
                    var haveL = (fl & 2) != 0
                    var seed = fl * 17 + si * 3 + mode
                    var total = w + h
                    var above = new Int[total]
                    var left = new Int[total]
                    var i = 0
                    while (i < total) {
                        var v = (seed * 7 + i * 13 + 3) % span
                        if (v < 0) {
                            v = v + span
                        }
                        above[i] = v
                        left[i] = v
                        i = i + 1
                    }
                    var tl = (seed * 11 + 5) % span
                    if (!haveA && haveL) {
                        i = 0
                        while (i < total) {
                            above[i] = left[0]
                            i = i + 1
                        }
                        tl = left[0]
                    } else {
                        if (haveA && !haveL) {
                            i = 0
                            while (i < total) {
                                left[i] = above[0]
                                i = i + 1
                            }
                            tl = above[0]
                        } else {
                            if (!haveA && !haveL) {
                                i = 0
                                while (i < total) {
                                    above[i] = (1 << (bd - 1)) - 1
                                    left[i] = (1 << (bd - 1)) + 1
                                    i = i + 1
                                }
                                tl = 1 << (bd - 1)
                            }
                        }
                    }
                    var out = av1IntraPred(mode, above, left, w, h, haveA, haveL, tl, bd)
                    println("IP " + w + " " + h + " " + mode + " " + bd + " " + fl)
                    var r = 0
                    while (r < h) {
                        var c = 0
                        var line = ""
                        while (c < w) {
                            if (c > 0) {
                                line = line + " "
                            }
                            line = line + out[r * w + c]
                            c = c + 1
                        }
                        println(line)
                        r = r + 1
                    }
                    fl = fl + 1
                }
                mi = mi + 1
            }
            si = si + 1
        }
        bi = bi + 1
    }
}
""";
}
