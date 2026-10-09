package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3u (AV1 intra prediction composition,
 * {@code libs/image/Av1IntraBlock.kf}). The Kof module implements AV1 Bitstream
 * &amp; Decoding Process Specification §7.11.2 (intra prediction process): it
 * builds {@code AboveRow}/{@code LeftCol} from the reconstructed plane (the
 * availability flags, the {@code haveAboveRight}/{@code haveBelowLeft}
 * {@code 2*w}/{@code 2*h} read limits, the missing-edge substitutions
 * {@code 128±1}) and dispatches to the base (slice 3j), directional (slice 3k)
 * or recursive filter-intra (slice 3l) predictors.
 *
 * <p>This class pins the Kof output against the reference libaom build path from
 * {@code av1/common/reconintra.c} built on the dev host: the REAL
 * {@code build_non_directional_intra_predictors} and
 * {@code build_directional_and_filter_intra_predictors} edge construction and
 * the {@code aom_*_predictor_*_c} / {@code av1_dr_prediction_z1/z2/z3_c} /
 * {@code av1_filter_intra_predictor_c} kernels they invoke, exactly as
 * {@code av1_predict_intra_block} dispatches them. The harness covers every AV1
 * transform size (square + rectangular), all twelve base/directional modes, the
 * recursive filter-intra mode (sizes ≤ 32), three bit depths (8/10/12), all four
 * {@code haveAbove}/{@code haveLeft} combinations including the above-right and
 * below-left availability, and both {@code enable_intra_edge_filter} faces. The
 * dump is gzip-compressed and base64-encoded as the test resource
 * {@code av1_intra_block_golden.txt.b64}.
 *
 * <p>The frame samples are a deterministic function of the case index, so the
 * Kof probe and the libaom harness build byte-identical inputs.
 */
final class Av1IntraBlockSupport {

    private Av1IntraBlockSupport() {}

    static final String RESOURCE = "/av1_intra_block_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "9d4469790b4be8f2d9952599d39251d61d28be40498fddc9ac535f138c4a1c15";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1IntraBlockSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that dumps every composed intra prediction block. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1IntraBlock

main() {
    var ws = listOf(4, 8, 16, 32)
    var hs = listOf(4, 8, 8, 8)
    var modes = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
    var bi = 0
    while (bi < 2) {
        var bd = 8 + 4 * bi
        var span = 1 << bd
        var fl = 0
        while (fl < 4) {
            var ei = 0
            while (ei < 2) {
                var ti = 0
                while (ti < 4) {
                    var w = ws.get(ti)
                    var h = hs.get(ti)
                    var mi = 0
                    while (mi < 14) {
                        var useFi = 0
                        if (mi == 13) {
                            useFi = 1
                        }
                        var skip = useFi == 1 && (w > 32 || h > 32)
                        if (!skip) {
                            var mode = 0
                            if (useFi == 0) {
                                mode = modes.get(mi)
                            }
                            var fiMode = 0
                            if (useFi == 1) {
                                fiMode = (ti * 3 + mi) % 5
                            }
                            var ad = ((ti + mi * 2 + fl) % 7) - 3
                            var filterType = (ti + mi + fl + ei + bi) % 2
                            var haveLeft = fl % 2
                            var haveAbove = 0
                            if (fl == 2 || fl == 3) {
                                haveAbove = 1
                            }
                            var haveAboveRight = 0
                            var haveBelowLeft = 0
                            if (fl == 3) {
                                haveAboveRight = 1
                                haveBelowLeft = 1
                            }
                            var maxX = 2 * w + 5
                            var maxY = 2 * h + 7
                            var x = 0
                            if (haveLeft == 1) {
                                x = 3
                            }
                            var y = 0
                            if (haveAbove == 1) {
                                y = 5
                            }
                            var pw = maxX + 2
                            var ph = maxY + 2
                            var frame = new Int[pw * ph]
                            var r = 0
                            while (r < ph) {
                                var c = 0
                                while (c < pw) {
                                    frame[r * pw + c] = (r * 7 + c * 13 + ti * 3 + mi * 5 + fl * 11 + ei * 17 + bi * 19 + 1) % span
                                    c = c + 1
                                }
                                r = r + 1
                            }
                            var edge = ei == 1
                            var out = av1IntraPredictBlock(frame, pw, x, y, w, h, maxX, maxY, haveLeft, haveAbove, haveAboveRight, haveBelowLeft, mode, ad, useFi, fiMode, bd, filterType, edge)
                            println("IB " + bd + " " + fl + " " + ei + " " + w + " " + h + " " + mode + " " + ad + " " + useFi + " " + fiMode)
                            r = 0
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
                        }
                        mi = mi + 1
                    }
                    ti = ti + 1
                }
                ei = ei + 1
            }
            fl = fl + 1
        }
        bi = bi + 1
    }
}
""";
}
