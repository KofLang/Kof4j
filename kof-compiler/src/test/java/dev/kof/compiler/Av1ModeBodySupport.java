package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3q (the AV1 intra mode-info body —
 * Y/UV mode, angle delta, CFL alphas, filter-intra — {@code
 * libs/image/Av1ModeInfo.kf}).
 *
 * <p>The golden is derived from libaom: an {@code aom_writer} (the real {@code
 * aom_dsp/entenc.c} range encoder with {@code allow_update_cdf}) encodes a
 * scripted sequence of {@code intra_frame_mode_info} bodies over a grid of
 * blocks while driving libaom's own {@code intra_mode_context} and the default
 * CDFs (spec §10 {@code Default_Intra_Frame_Y_Mode_Cdf}, {@code
 * Default_UV_Mode_Cdf}, {@code Default_Angle_Delta_Cdf}, {@code
 * Default_Filter_Intra_Mode_Cdf}, {@code Default_Filter_Intra_Cdf}, {@code
 * Default_CFL_Sign_Cdf}, {@code Default_CFL_Alpha_Cdf}, identical to libaom's
 * {@code default_kf_y_mode_cdf}, {@code default_uv_mode_cdf}, etc.). Six cases
 * cover both CFL-allowed and CFL-not-allowed UV sets (lossless BLOCK_4X4,
 * non-lossless up to 32, and 64x64), monochrome (no chroma), and the
 * filter-intra guard; the golden is the sequence of
 * {@code B r c yMode angleY uvMode angleUV cflU cflV useFilterIntra
 * filterIntraMode} decisions in walk order. The dump is gzip-compressed and
 * base64-encoded as {@code av1_modebody_golden.txt.b64}.
 */
final class Av1ModeBodySupport {

    private Av1ModeBodySupport() {}

    static final String RESOURCE = "/av1_modebody_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text (stripped), stale-resource guard. */
    static final String GOLDEN_SHA256 =
        "cef3c09dc31aab8e4e970381a60c7e28551ec38b2a10e80b8bf7d64337a6dc1e";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1ModeBodySupport.class.getResourceAsStream(RESOURCE)) {
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
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Kof probe source that reads every encoded mode-info body and dumps the decisions. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1Block
import image.Av1ModeInfo

main() {
    runBody(6, 5, 0, 1, 1, 3, listOf(81,95,202,249,49,99,149,76,120,76))
    runBody(24, 20, 0, 1, 1, 3, listOf(81,95,202,249,49,100,151,66,237,62,209,151,242,184,14,127,154,76,191,124,139,199,113,183,30,107,98,91,209,52,255,242,119,190,131,145,157,140,26,23,52,133,245,202,94,243,234,26,212,166,24,89,6,113,198,107,228,103,3,13,10,10,216,71,41,172,22,114,26,153,193,151,44,165,124,198,156,223,201,108,151,203,142,15,229,255,12,75,154,202,90,19,133,236,1,15,131,147,63,201,143,56,128,247,200,75,176,159,45,230,60,229,22,78,37,102,134,214,47,196,41,226,217,63,76,66,217,78,213,148,154,187,171,161,132,234,223,255,253,207,73,163,31,185,155,187,224,121,36,192,132,54,187,207,183,91,103,197,56,45,120,117,82,192,251,97,59,82,70,55,56,246,215,25,128,78,152,254,250,37,103,6,248,29,191,252,241,116,70,114,79,195,168,235,237,196,116,97,1,72,1,104,207,210,84,200,55,254,229,56,16,10,222,13,122,76,45,70,9,222,240,183,22,47,150,126,2,202,113,154,129,38,237,108))
    runBody(33, 21, 1, 1, 1, 0, listOf(219,18,75,207,204,203,120,136,138,39,77,149,82,54,252,124,93,154,254,240,159,189,162,151,19,183,132,52,209,16,31,237,161,138,174,69,251,176,98,198,24,143,48,94,84,246,178,43,179,251,23,64,85,60,240,12,11,151,75,11,63,219,192,249,47,77,212,96,120,36,207,81,82,203,173,232,184,44,243,168,199,236,31,7,144,105,81,39,164,161,81,30,22,82,137,56,33,56,183,98,101,250,241,2,248,11,225,110,175,2,106,134,243,217,141,232,137,222,0,73,4,123,12,0,170,167,90,142,30,180,253,118,198,208,74,12,100,226,206,121,26,221,181,142,110,45,236,253,68,199,219,24,114,21,75,132,125,36,227,95,32,183,62,178,162,110,2,151,236,128,72,132,25,249,255,41,103,222,183,26,149,220,221,20,211,71,113,11,21,36,131,241,1,94,166,216,168,205,126,57,125,210,152,111,30,120,187,154,109,147,165,207,241,98,252,51,188,183,108,69,43,16,30,239,78,148,97,142,107,19,214,3,85,4,57,211,251,244,92,140,24,5,200,14,210,69,39,50,231,250,27,5,179,96,116,158,222,127,49,240,228,194,17,119,22,102,58,160,50,112,31,221,88,124,23,245,231,114,64,100,62,179,30,123,31,71,195,166,129,245,205,42,212,246,166,118,229,109,146,76,72,251,198,223,196,95,99,92,21,220,149,44,222,81,134,75,87,166,152,128,41,24,167,176,67,77,27,152,243,42,109,43,183,65,28,223,103,58,202,113,49,53,191,69,217,70,254,3,89,19,53,73,107,51,209,115,42,242,36,70,109,216,103,88,73,159,163,166,207,226,86,233,185,212,250,32,130,68,179,132,239,219,223,73,192,43,21,38,29,34,118,201,219,5,0,55,216,90,68,27,189,169,23,98,191,253,222,211,13,98,92,37,99,91,62,42,203,3,78,250,180,118,37,69,118,68,72,65,201,143,7,216,37,202,170,29,146,154,30,184,170,203,183,50,103,136,60,227,113,0,88,134,253,67,62,17,160,53,192,140,94,174,159,76,36,187,215,128,150,145,15,146,132,119,176,215,153,157,66,160,20,46,254,200,106,35,54,133,33,19,199,2,127,102,183,22,174,52,45,217,242,173,225,213,200,6,215,175,200,77,165,102,180,255,236,119,30,210,44,127,121,224,253,49,164,163,29,116,192,65,18,225,106,79,132,206,203,40,71,189,77,65,95,147,233,220,135,23,174,41,43,203,225,18,201,197,54,17,173,71,44,57,177,197,153,246,23,187,27,226,157,236,0,130,5,142,52,74,133,216,81,112,93,188,87,39,170,134,86,178,156,137,13,184,221,198,204,78,183,80,119,144,173,238,82,7,187,126,95,152,156,20,27,227,39,185,35,243,140,133,206,219,241,232,160,200,232,224,39,16,135,255,51,236,86,44,155,207,34,17,199,32,47,50,107,211,187,138,169,19,8,179,214,69,6,64,219,210,37,110,56,27,207,199,167,40,149,172,97,171,80,57,239,99,0,28,221,173,13,29,72,148,145,208,191,163,241,103,44,94,64,240,59,169,115,198,162,244,162,188,56,114,51,143,147,229,4,88,191,140,247,176,166,207,183,31,183,173,175,154,69,43,36,126,19,187,150,240,104,125,58,144,214,17,110,223,68,32,101,153,248,166,148,185,15,13,117,146,65,190,33,4,179,62,254,158,159,116,166,27,251,205,248,219,178,56,168,111,237,114,3,96,205,43,169,132,123,90,102,229,231,116,183,26,116,21,144,178,248,40,84,216,139,160,190,29,215,32,89,247,214,86,243,33,111,238,224,1,205,180,212,140,224,94,177,154,33,218,14,49,161,251,74,244,21,136,251,45,54,73,93,232,247,172,218,138,218,85,112,227,90,48,59,128,10,219,32,149,168,249,181,52,166,239,96,40,55,120,156,44,178,250,156,252,124,45,84,115,4,96,214,203,183,91,16,211,91,193,9,249,11,82,126,197,22,143,182,143,160,179,181,214,187,206,47,105,117,69,9,79,108,149,185,141,209,148,37,44,141,65,255,224,118,27,12,154,204,43,61,208,203,117,128,250,250,130,40,252,154,108,57,137,223,16,44,79,216,207,160,148,63,134,199,200,140,103,157,50,173,197,244,113,2,77,51,72,45,114,56,230,164,0,62,100,28,63,122,171,183,218,243,176,252,112,14,148,87,90,240,84,230,14,156,178,204,74,120,245,193,143,191,100,18,240,124,15,178,89,15,60,19,53,190,222,224,196,110,123,91,206,75,218,156,245,34,109,3,120,62,165,74,59,126,2,50,161,196,91,107,10,54,104))
    runBody(16, 40, 0, 1, 1, 6, listOf(153,87,74,2,54,4,163,142,204,157,202,146,198,58,121,209,48,28,31,88,41,120,82,136,151,142,21,85,126,47,92,163,253,214,172,247,194,126,31,187,174,184,187,191,237,0,221,63,223,177,192,253,120,233,97,126,191,23,221,34,205,147,100,111,61,252,220,90,3,187,23,73,38,151,95,68,10,80,73,192,23,67,92,250,218,234,181,95,246,5,150,238))
    runBody(16, 16, 0, 1, 1, 12, listOf(153,78))
    runBody(12, 9, 0, 0, 1, 3, listOf(134,216,247,204,49,38,138,194,241,141,164,255,200,155,231,85,131))
}

void runBody(Int w, Int h, Int lossless, Int hasChroma, Int enableFilterIntra, Int bs, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    println("C " + w + " " + h + " " + lossless + " " + hasChroma + " " + enableFilterIntra + " " + bs)
    var m = Av1ModeInfo(data, w, h)
    var bw = av1BlockNum4x4Wide(bs)
    var bh = av1BlockNum4x4High(bs)
    var r = 0
    while (r + bh <= h) {
        var c = 0
        while (c + bw <= w) {
            var s = m.readBody(r, c, bs, lossless, hasChroma, enableFilterIntra, 0)
            println("B " + r + " " + c + " " + s[0] + " " + s[1] + " " + s[2] + " " + s[3] + " " + s[4] + " " + s[5] + " " + s[6] + " " + s[7])
            c = c + bw
        }
        r = r + bh
    }
}
""";
}
