package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3l (AV1 recursive filter-intra
 * prediction, {@code libs/image/Av1FilterIntra.kf}). The Kof module is a direct
 * transcription of AV1 Bitstream &amp; Decoding Process Specification §7.11.2.3
 * (recursive intra prediction process) and the {@code Intra_Filter_Taps} table
 * from the additional tables.
 *
 * <p>This class pins the Kof output against the reference libaom kernel
 * {@code av1_filter_intra_predictor_c} from {@code av1/common/reconintra.c}
 * (extracted verbatim) together with its {@code av1_filter_intra_taps} table,
 * over every AV1 transform size the process admits ({@code w <= 32 && h <= 32})
 * and all five {@code filter_intra_mode} values. The dump is gzip-compressed and
 * base64-encoded as the test resource {@code av1_filter_intra_golden.txt.b64}.
 *
 * <p>The edge arrays are a deterministic function of the case index, so the Kof
 * probe and the libaom harness build byte-identical inputs.
 */
final class Av1FilterIntraSupport {

    private Av1FilterIntraSupport() {}

    static final String RESOURCE = "/av1_filter_intra_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "0f5aae47bab86c958290d9293079f2dc52bc6925ddd0bf2fa66453046a948267";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1FilterIntraSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that dumps every filter-intra prediction block. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1FilterIntra

main() {
    var sizesW = listOf(4, 8, 16, 32, 64, 4, 8, 8, 16, 16, 32, 16, 64, 32, 64, 4, 16, 8, 32)
    var sizesH = listOf(4, 8, 16, 32, 64, 8, 4, 16, 8, 32, 16, 64, 16, 64, 32, 16, 4, 32, 8)
    var co = 40
    var si = 0
    while (si < 19) {
        var w = sizesW.get(si)
        var h = sizesH.get(si)
        if (w <= 32 && h <= 32) {
            var mode = 0
            while (mode < 5) {
                var seed = si * 131 + mode * 7 + 11
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
                ab[co - 1] = (seed * 11 + 5) & 255
                lf[co - 1] = ab[co - 1]
                var out = av1FilterIntraPred(ab, lf, w, h, co, mode)
                println("FI " + w + " " + h + " " + mode)
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
                mode = mode + 1
            }
        }
        si = si + 1
    }
}
""";
}
