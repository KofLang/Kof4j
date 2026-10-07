package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3p (the AV1 intra mode-info prefix —
 * segment id + skip — {@code libs/image/Av1ModeInfo.kf}).
 *
 * <p>The golden is derived from libaom: an {@code aom_writer} (the real
 * {@code aom_dsp/entenc.c} range encoder with {@code allow_update_cdf}) encodes
 * a scripted sequence of {@code intra_frame_mode_info} prefixes while driving
 * libaom's own spatial segment predictor/context
 * ({@code av1_get_spatial_seg_pred} / {@code av1_neg_deinterleave}) and the
 * spec skip context over the default segment/skip CDFs (spec §10
 * {@code Default_Segment_Id_Cdf} / {@code Default_Skip_Cdf}, identical to
 * libaom's {@code default_spatial_pred_seg_tree_cdf} /
 * {@code default_skip_txfm_cdfs}). Five frame shapes are encoded (both
 * {@code SegIdPreSkip} orders, two {@code last_active_segid} values); the golden
 * is the sequence of {@code M r c skip segment_id} decisions in walk order. The
 * dump is gzip-compressed and base64-encoded as
 * {@code av1_modeinfo_golden.txt.b64}.
 */
final class Av1ModeInfoSupport {

    private Av1ModeInfoSupport() {}

    static final String RESOURCE = "/av1_modeinfo_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text (stripped), stale-resource guard. */
    static final String GOLDEN_SHA256 =
        "3724ad52ddc16a6510e2ab69c42963372b8dde4b17d512a2f0631facb955faca";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1ModeInfoSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that reads every encoded prefix and dumps the decisions. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1ModeInfo

main() {
    runPrefix(6, 5, 0, 0, 7, listOf(209,42,65,47,29,89,188,173,97,70,80,51,245,215,242,96))
    runPrefix(24, 20, 0, 0, 7, listOf(209,42,65,60,54,166,217,50,146,54,120,10,253,107,226,196,207,40,78,97,121,146,130,157,29,181,137,77,98,100,125,24,39,32,45,21,170,55,101,138,133,192,212,149,160,243,67,36,241,48,126,194,241,125,191,85,205,154,94,90,44,230,1,159,74,100,109,128,67,151,24,161,24,138,4,136,134,87,170,217,223,230,104,81,149,88,134,182,8,210,169,63,147,91,226,179,110,165,33,188,253,247,241,114,187,101,157,5,164,169,47,38,34,118,100,209,230,175,136,28,201,165,193,10,107,197,162,56,46,228,76,136,17,145,60,111,62,232,165,108,251,243,97,201,109,112,233,204,98,105,179,59,3,180,31,12,99,126,240,191,214,250,106,6,249,53,231,62))
    runPrefix(33, 21, 1, 0, 7, listOf(205,123,102,78,167,149,134,160,55,124,128,19,201,207,36,18,162,44,38,219,124,179,185,90,157,49,50,3,64,10,22,253,153,29,114,250,112,29,47,197,135,220,2,16,4,72,141,149,133,190,185,242,43,225,233,76,210,202,107,83,35,11,235,152,109,41,232,227,19,185,80,49,58,92,74,162,80,35,124,40,246,15,115,232,206,110,80,144,60,247,202,195,96,254,160,89,81,224,138,111,99,149,205,106,252,251,32,161,185,222,165,117,184,137,9,186,208,94,247,9,242,86,213,8,198,248,201,0,210,98,179,16,75,191,111,153,9,123,218,4,234,195,42,241,1,128,54,8,224,68,162,23,52,47,121,225,174,184,128,198,116,236,27,189,65,88,31,134,117,163,10,87,153,191,79,19,79,54,210,77,188,92,114,242,8,89,214,121,214,28,84,36,251,170,51,147,34,67,165,139,169,63,99,251,87,42,137,211,226,113,89,68,71,85,222,112,143,152,116,53,211,134,25,54,99,135,124,246,94,110,5,48,205,252,11,124,69,203,78,133,177,132,213,99,90,36,111,15,251,150,139,119,188,54,74,48,21,221,129,148,157,83,133,255,139,1,53,109,238,244,112,170,149,93,110,144,232,116,208,175,7,66,195,117,227,82,101,20,84,188,10,178,60,3,240,189,245,86,157,78,135,208,21,57,192,86,217,251,16,180,56,184,126,12,18,100,67,119,181,187,238,158,150,183,210,93,17,35,142,122,226,8,94,155,237,92,69,58,30,158,39,184,171,244,234,188,214,98,170,69,63,21,229,205,169,123,191,184))
    runPrefix(16, 40, 1, 0, 3, listOf(141,248,22,166,228,13,159,197,114,234,137,233,107,99,1,192,126,87,223,106,132,101,121,89,132,127,114,82,56,18,144,187,246,242,24,39,164,3,14,107,216,245,112,23,149,89,243,202,205,3,44,136,86,113,184,184,36,112,155,131,145,58,116,41,108,45,18,240,137,70,80,127,193,4,223,18,186,56,18,221,204,32,183,248,70,162,229,26,249,159,140,162,210,105,187,15,121,186,253,190,140,28,47,220,149,127,107,41,103,152,12,237,95,92,206,26,229,147,40,123,6,92,106,35,21,61,86,132,134,53,101,157,153,58,7,241,34,123,92,34,2,121,232,62,57,68,153,194,222,225,177,198,120,119,199,95,220,133,169,55,90,43,134,212,209,219,18,51,62,199,160,205,203,31,74,96,109,118,245,82,57,161,250,78,112,9,145,129,241,151,202,245,98,186,240,130,228,227,125,45,45,25,206,91,40,67,158,210,69,163,236,199,110,30,101,239,29,139,137,145,180,135,236,60,232,237,137,146,205,62,88,183,15,243,32,39,199,227,140,75,34,58,191,114,74,166,27,140,237,170,77,251,161,2,52,201,222,203,152))
    runPrefix(12, 9, 0, 0, 7, listOf(210,249,176,162,237,154,99,59,139,247,250,80,248,44,63,5,245,91,213,183,239,21,92,34,135,199,82,185,247,219,201,2,226,68,2,28,233,179,21,226,152,74))
}

void runPrefix(Int w, Int h, Int pre, Int act, Int last, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var m = Av1ModeInfo(data, w, h)
    var r = 0
    while (r < h) {
        var c = 0
        while (c < w) {
            var s = m.readPrefix(r, c, pre, last, act)
            println("M " + r + " " + c + " " + s[0] + " " + s[1])
            c = c + 1
        }
        r = r + 1
    }
}
""";
}
