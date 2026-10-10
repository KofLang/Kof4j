package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3k (AV1 directional intra prediction +
 * intra edge filtering, {@code libs/image/Av1IntraDr.kf}). The Kof module is a
 * direct transcription of AV1 Bitstream &amp; Decoding Process Specification
 * §7.11.2.4 (directional intra prediction process) and the edge processes it
 * invokes: §7.11.2.7 (filter corner), §7.11.2.9 (edge filter strength
 * selection), §7.11.2.10 (edge upsample selection), §7.11.2.11 (upsample) and
 * §7.11.2.12 (edge filter).
 *
 * <p>This class pins the Kof output against the reference libaom kernels from
 * {@code av1/common/reconintra.c} built on the dev host: the REAL
 * {@code av1_dr_prediction_z1/z2/z3_c} zone kernels plus
 * {@code av1_filter_intra_edge_c}, {@code filter_intra_edge_corner},
 * {@code intra_edge_filter_strength} and {@code av1_upsample_intra_edge_c},
 * driven exactly as {@code build_directional_and_filter_intra_predictors} does.
 * The harness covers every AV1 transform size (square + rectangular), the eight
 * angular modes (V/H/D45/D135/D113/D157/D203/D67), all seven
 * {@code angleDelta} values that keep {@code pAngle} in range and both left-edge
 * availability configurations. The dump is gzip-compressed and base64-encoded
 * as the test resource {@code av1_intra_dr_golden.txt.b64}.
 *
 * <p>The edge arrays are a deterministic function of the case index, so the Kof
 * probe and the libaom harness build byte-identical inputs.
 */
final class Av1IntraDrSupport {

    private Av1IntraDrSupport() {}

    static final String RESOURCE = "/av1_intra_dr_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "77c7ec46ea623d97e961c414a2a2066a7502d29b6df6fff46af5fe029b95a3e3";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1IntraDrSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that dumps every directional intra prediction block. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1IntraDr

Int drModeAngle(Int mode) {
    return av1IntraModeToAngle(mode)
}

main() {
    var sizesW = listOf(4, 8, 16, 32, 64, 4, 8, 8, 16, 16, 32, 16, 64, 32, 64, 4, 16, 8, 32)
    var sizesH = listOf(4, 8, 16, 32, 64, 8, 4, 16, 8, 32, 16, 64, 16, 64, 32, 16, 4, 32, 8)
    var modes = listOf(1, 2, 3, 4, 5, 6, 7, 8)
    var co = 40
    var si = 0
    while (si < 19) {
        var w = sizesW.get(si)
        var h = sizesH.get(si)
        var mi = 0
        while (mi < 8) {
            var mode = modes.get(mi)
            var ad = -3
            while (ad <= 3) {
                var angle = drModeAngle(mode) + ad * 3
                if (angle > 0 && angle < 270) {
                    var fl = 0
                    while (fl < 2) {
                        var seed = si * 97 + mi * 13 + ad * 5 + fl
                        var total = w + h
                        var len = co + 2 * total + 8
                        var ab = new Int[len]
                        var lf = new Int[len]
                        var i = 0
                        while (i < total) {
                            var v = (seed * 7 + i * 13 + 3) & 255
                            ab[co + i] = v
                            lf[co + i] = v
                            i = i + 1
                        }
                        var tl = (seed * 11 + 5) & 255
                        if (fl == 1) {
                            i = 0
                            while (i < total) {
                                lf[co + i] = ab[co]
                                i = i + 1
                            }
                            tl = ab[co]
                        }
                        ab[co - 1] = tl
                        lf[co - 1] = tl
                        var out = av1DrPredict(mode, ad, ab, lf, w, h, co, w, h, 8, 0, true)
                        println("DR " + w + " " + h + " " + mode + " " + angle + " " + fl)
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
                }
                ad = ad + 1
            }
            mi = mi + 1
        }
        si = si + 1
    }
}
""";
}
