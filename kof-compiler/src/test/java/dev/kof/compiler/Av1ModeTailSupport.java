package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3r (the AV1 intra mode-info tail —
 * CDEF strength index, quantizer delta and loop-filter deltas — {@code
 * libs/image/Av1ModeInfo.kf}).
 *
 * <p>The golden is derived from libaom: an {@code aom_writer} (the real {@code
 * aom_dsp/entenc.c} range encoder with {@code allow_update_cdf}) emits a tile
 * byte stream driving libaom's own {@code read_cdef}, {@code read_delta_qindex}
 * and {@code read_delta_lflevel} against the default {@code delta_q_cdf} /
 * {@code delta_lf_cdf} / {@code delta_lf_multi_cdf} (spec §10 {@code
 * Default_Delta_Q_Cdf}, {@code Default_Delta_Lf_Cdf}). Seven cases cover the
 * 64x64 and 128x128 superblocks, CDEF on/off, the coded-lossless guard, the
 * {@code ReadDeltas} gate, {@code delta_lf_multi} with 1 and 3 planes, and
 * sub-block walks inside a superblock. The golden is the sequence of
 * {@code C ...} config lines and {@code T r c bs skip cdef deltaQ deltaLF...}
 * decisions in walk order. The dump is gzip-compressed and base64-encoded as
 * {@code av1_mode_tail_golden.txt.b64}.
 */
final class Av1ModeTailSupport {

    private Av1ModeTailSupport() {}

    static final String RESOURCE = "/av1_mode_tail_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text (stripped), stale-resource guard. */
    static final String GOLDEN_SHA256 =
        "93fcdc9a13f5a42d4c2e3aac8f7a77c1f5a5b7e97c982cc4fad97ee9db48b732";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1ModeTailSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that reads every encoded mode-info tail and dumps the decisions. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1Block
import image.Av1ModeInfo

main() {
    runTail(32, 32, 12, 2, 1, 0, 1, 0, 1, 1, 0, 3, 1, listOf(29,161,158,186,181,133,250,25,212,216,62,211,21,170,79,145,7,23,31,192,153,220,224,224,31,185,109,241,177,121,120,138,117,83,213,176,9,142,255,22,199,182,163,65,241,45,229,100,231,102,216,55,27,119,48))
    runTail(32, 32, 12, 3, 1, 0, 1, 2, 0, 0, 0, 3, 1, listOf(45,247,215,251,56,178,164,155,183,212,180,220,157,111,114,21,132,236,191,138,46,131,241,175,242,204,7,112,143,254,202,239,24,38,45,164,107,188))
    runTail(64, 32, 12, 0, 0, 0, 1, 1, 1, 0, 1, 3, 2, listOf(255,212,125,162,54,13,38,238,183,196,135,141,72,165,145,205,63,71,18,109,126,111,71,84,242,133,206,1,47,228,240))
    runTail(32, 32, 15, 2, 1, 0, 1, 0, 1, 1, 0, 3, 1, listOf(55,45,52,74,211,143,190,74,162,176,70,223,30,35,72,185,113,22,34,186,41,139,73,101,23,167,35,115,171,167,237,112,146,38,157,31,192))
    runTail(32, 32, 12, 2, 1, 1, 1, 1, 1, 1, 0, 3, 1, listOf(93,107,214,75,104,119,90,10,0,79,224,242,180,203,63,10,33,3,164,76,116,110,233,92,0,200,10,139,54,200,203,2,90,29,137,140,113,161,61,76,122,1,168,226,53,251,92,90,85,14))
    runTail(32, 32, 12, 3, 1, 0, 1, 0, 1, 1, 0, 1, 2, listOf(60,219,89,220,153,36,23,72,203,210,180,177,39,122,29,52,122,191,58,26,55,232))
    runTail(32, 32, 12, 2, 1, 0, 1, 1, 1, 0, 1, 3, 0, listOf(32,16,231,48))
}

void runTail(Int w, Int h, Int sbSize, Int cdefBits, Int enableCdef, Int lossless, Int dqPresent, Int dqRes, Int dlfPresent, Int dlfMulti, Int dlfRes, Int numPlanes, Int subdiv, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    println("C " + w + " " + h + " " + sbSize + " " + cdefBits + " " + enableCdef + " " + lossless + " " + dqPresent + " " + dqRes + " " + dlfPresent + " " + dlfMulti + " " + dlfRes + " " + numPlanes + " " + subdiv)
    var m = Av1ModeInfo(data, w, h)
    var cfg = Av1ModeTail(sbSize, cdefBits, enableCdef, lossless, dqPresent, dqRes, dlfPresent, dlfMulti, dlfRes, numPlanes)
    var sb4 = 16
    if (sbSize == 15) {
        sb4 = 32
    }
    var cur = listOf(0, 0, 0, 0)
    var sr = 0
    while (sr < h) {
        var sc = 0
        while (sc < w) {
            var step = sb4
            var bs = sbSize
            if (subdiv == 1) {
                step = 2
                bs = 3
            }
            if (subdiv == 2) {
                step = 4
                bs = 6
            }
            var i = 0
            while (i < sb4) {
                var j = 0
                while (j < sb4) {
                    if (sr + i + step <= h && sc + j + step <= w) {
                        var r = sr + i
                        var cc = sc + j
                        var skip = m.sym.readLiteral(1)
                        var cd = m.readCdef(r, cc, bs, skip, cfg)
                        var dq = m.readDeltaQ(r, cc, bs, skip, cfg)
                        var lf = m.readDeltaLf(r, cc, bs, skip, cur, cfg)
                        var line = "T " + r + " " + cc + " " + bs + " " + skip + " " + cd + " " + dq
                        if (lf.size() > 0) {
                            cur = lf
                            var q = 0
                            while (q < lf.size()) {
                                line = line + " " + lf.get(q)
                                q = q + 1
                            }
                        }
                        println(line)
                    }
                    j = j + step
                }
                i = i + step
            }
            sc = sc + sb4
        }
        sr = sr + sb4
    }
}
""";
}
