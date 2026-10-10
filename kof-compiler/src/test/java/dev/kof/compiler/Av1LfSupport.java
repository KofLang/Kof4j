package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3h (the AV1 loop filter,
 * {@code libs/image/Av1Lf.kf}). The Kof module is a direct transcription of AV1
 * Bitstream &amp; Decoding Process Specification §7.14; this class pins its
 * output against the reference libaom implementation, built on the dev host:
 * the §7.14.6 sample filtering runs the real {@code aom_lpf_*_c} /
 * {@code aom_highbd_lpf_*_c} kernels ({@code aom_dsp/loopfilter.c}), while the
 * §7.14.3 filter size, §7.14.4 adaptive strength and the {@code mode_lf_lut}
 * mode type are reproduced from libaom's {@code av1/common/av1_loopfilter.c}
 * ({@code update_sharpness}, {@code get_filter_level}) and its
 * {@code mode_lf_lut[]}. The dump is gzip-compressed and base64-encoded as the
 * test resource {@code av1_lf_golden.txt.b64}.
 *
 * <p>The sample cases walk all eight seeds, bit depths 8/10/12, luma and chroma,
 * filter sizes 4/8/16 and both edge directions. Each case filters a 17x17
 * block at four consecutive boundary positions (the kernels process four lines
 * per call), matching the §7.14.2 edge walk's inner loop.
 */
final class Av1LfSupport {

    private Av1LfSupport() {}

    static final String RESOURCE = "/av1_lf_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "65ac32610264050d939a9a03c640b4ec6d08d3a1f6087e5bea36afaa6c77f295";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1LfSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that dumps the sample, size, mode and strength cases. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1Lf

Int lfValue(Int seed, Int bd, Int r, Int c) {
    var span = 1 << bd
    var amp = 1 + (seed % 8)
    var base = span / 2
    var v = base + ((r * 7 + c * 11 + seed * 3) % (amp + 1))
    if (v < 0) {
        v = 0
    }
    if (v >= span) {
        v = span - 1
    }
    return v
}

void lfCase(Int seed, Int bd, Int plane, Int fs, Int dir, Int limit, Int blimit, Int thresh) {
    var n = 17
    var mid = 8
    var s = new Int[n * n]
    var r = 0
    while (r < n) {
        var c = 0
        while (c < n) {
            s[r * n + c] = lfValue(seed, bd, r, c)
            c = c + 1
        }
        r = r + 1
    }
    var dx = 0
    var dy = 1
    if (dir == 1) {
        dx = 1
        dy = 0
    }
    var t = 0
    while (t < 4) {
        if (dir == 1) {
            av1LfSample(s, n, n, mid, mid + t, dx, dy, plane, limit, blimit, thresh, fs, bd)
        } else {
            av1LfSample(s, n, n, mid + t, mid, dx, dy, plane, limit, blimit, thresh, fs, bd)
        }
        t = t + 1
    }
    println("CASE seed " + seed + " bd " + bd + " plane " + plane + " fs " + fs + " dir " + dir + " limit " + limit + " blimit " + blimit + " thresh " + thresh)
    r = 0
    while (r < n) {
        var c = 0
        var line = ""
        while (c < n) {
            if (c > 0) {
                line = line + " "
            }
            line = line + s[r * n + c]
            c = c + 1
        }
        println(line)
        r = r + 1
    }
}

void lfStrengthCase(Int base, Int delta, Int segActive, Int segData, Int ref, Int modeType, Int sharpness, Int deltaEnabled, Int refDelta, Int modeDelta) {
    var st = av1LfStrength(base, delta, segActive, segData, ref, modeType, sharpness, deltaEnabled == 1, refDelta, modeDelta)
    println("STRENGTH base " + base + " delta " + delta + " segActive " + segActive + " segData " + segData + " ref " + ref + " modeType " + modeType + " sharpness " + sharpness + " deltaEnabled " + deltaEnabled + " refDelta " + refDelta + " modeDelta " + modeDelta + " -> " + st[0] + " " + st[1] + " " + st[2] + " " + st[3])
}

main() {
    var seeds = listOf(1, 2, 3, 4, 5, 6, 7, 8)
    var si = 0
    while (si < seeds.size) {
        var seed = seeds[si]
        var limit = (seed * 3 + 1) % 8
        var blimit = (seed * 5 + 2) % 16
        var thresh = (seed * 7 + 3) % 8
        var bd = 8
        while (bd <= 12) {
            var plane = 0
            while (plane <= 1) {
                var fs = 4
                while (fs <= 16) {
                    if (plane == 0 || fs != 16) {
                        var dir = 0
                        while (dir <= 1) {
                            lfCase(seed, bd, plane, fs, dir, limit, blimit, thresh)
                            dir = dir + 1
                        }
                    }
                    fs = fs * 2
                }
                plane = plane + 1
            }
            bd = bd + 2
        }
        si = si + 1
    }
    var mode = 0
    while (mode <= 24) {
        println("MODETYPE mode " + mode + " -> " + av1LfModeType(mode))
        mode = mode + 1
    }
    var txSz = 0
    while (txSz < 19) {
        var prevTxSz = 0
        while (prevTxSz < 19) {
            var pass = 0
            while (pass <= 1) {
                var plane = 0
                while (plane <= 1) {
                    println("FSIZE tx " + txSz + " prev " + prevTxSz + " pass " + pass + " plane " + plane + " -> " + av1LfFilterSize(txSz, prevTxSz, pass, plane))
                    plane = plane + 1
                }
                pass = pass + 1
            }
            prevTxSz = prevTxSz + 1
        }
        txSz = txSz + 1
    }
    var baseVals = listOf(0, 5, 20, 31, 32, 40, 63)
    var deltaVals = listOf(-10, 0, 5)
    var segActiveVals = listOf(0, 1)
    var segDataVals = listOf(-5, 3)
    var refVals = listOf(0, 1, 4)
    var modeTypeVals = listOf(0, 1)
    var sharpVals = listOf(0, 1, 4, 5, 7)
    var deltaEnabledVals = listOf(0, 1)
    var refDeltaVals = listOf(-2, 1)
    var modeDeltaVals = listOf(-1, 2)
    var k = 0
    while (k < 300) {
        var base = baseVals[k % 7]
        var delta = deltaVals[(k * 2) % 3]
        var sa = segActiveVals[(k * 5) % 2]
        var sd = segDataVals[(k * 7) % 2]
        var rf = refVals[(k * 11) % 3]
        var mt = modeTypeVals[(k * 13) % 2]
        var sh = sharpVals[(k * 17) % 5]
        var de = deltaEnabledVals[(k * 19) % 2]
        var rd = refDeltaVals[(k * 23) % 2]
        var md = modeDeltaVals[(k * 29) % 2]
        lfStrengthCase(base, delta, sa, sd, rf, mt, sh, de, rd, md)
        k = k + 1
    }
}
""";
}
