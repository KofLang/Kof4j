package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3s (the AV1 transform-size selection —
 * {@code read_tx_size}/{@code read_selected_tx_size} — {@code
 * libs/image/Av1TxSize.kf}).
 *
 * <p>The golden is derived from libaom: an {@code aom_writer} (the real {@code
 * aom_dsp/entenc.c} range encoder with {@code allow_update_cdf}) emits a tile
 * byte stream driving libaom's own {@code tx_size_cdf} selection
 * ({@code bsize_to_tx_size_cat} + {@code get_tx_size_context}) and tables
 * ({@code max_txsize_rect_lookup}, {@code tx_size_wide/high},
 * {@code sub_tx_size_map}, {@code bsize_to_max_depth},
 * {@code bsize_to_tx_size_depth}). Seven cases cover TX_MODE_SELECT/LARGEST/
 * ONLY_4X4, the lossless shortcut, the inter allow-select gate, and 64x64 /
 * 16x16 / 8x8 block walks. The golden is the sequence of {@code C ...} config
 * lines and {@code T r c bs skip txSize} decisions in walk order. The dump is
 * gzip-compressed and base64-encoded as {@code av1_txsize_golden.txt.b64}.
 */
final class Av1TxSizeSupport {

    private Av1TxSizeSupport() {}

    static final String RESOURCE = "/av1_txsize_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text (stripped), stale-resource guard. */
    static final String GOLDEN_SHA256 =
        "08e39e72aab47e5576cbc063a8dea33dbf11fd8c3b63aa135f0066ff2c7d48a5";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1TxSizeSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that reads every encoded transform size and dumps the decisions. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1TxSize

main() {
    runTx(32, 32, 12345, 2, 0, 0, 1, listOf(61,66,103,233,126,14,101,163,115,162,39,105,72,96,38,250,16,41,150,254,210,204,233,26,54,191,181,238,7,92,28,51,83,229,252,194,206,118,201,111,217,110,204,186,33,33,155,52,188,87,192,188,177,175,122,167,177,208,74,155,207,157,62,155,42))
    runTx(32, 32, 777, 2, 0, 0, 2, listOf(33,104,6,51,39,218,168,225,153,206,218,160,42,185,108,43,211,184,134,215,62,28,64))
    runTx(32, 32, 424242, 1, 0, 0, 1, listOf(201,109,235,119,152,185,115,158,16,50,125,229,129,151,248,32,61,212,25,133,209,168,1,204,15,185,168,203,157,56,108,253,128))
    runTx(32, 32, 99, 0, 0, 0, 1, listOf(77,106,245,137,13,0,69,223,120,139,43,35,162,76,88,47,120,83,8,102,99,254,34,13,162,33,161,243,141,56,48,110,64))
    runTx(32, 32, 555, 2, 1, 0, 1, listOf(4,251,57,79,167,139,17,117,242,252,210,46,63,10,33,3,164,76,56,152,146,55,63,249,63,13,242,88,19,141,251,98,128))
    runTx(32, 32, 31337, 2, 0, 1, 1, listOf(95,187,126,227,212,235,153,78,101,192,220,92,122,59,24,11,132,58,213,211,102,112,189,62,236,142,0,5,216,219,140,60,238,156,194,116,27,61,39,1,102,20,238,35,111,62,163,64))
    runTx(64, 32, 8, 2, 0, 0, 0, listOf(54,239,108))
}

void runTx(Int w, Int h, Int seed, Int txMode, Int lossless, Int isInter, Int subdiv, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    println("C " + w + " " + h + " " + txMode + " " + lossless + " " + isInter + " " + subdiv)
    var m = Av1TxSize(data, w, h)
    var step = 16
    var bs = 12
    if (subdiv == 1) {
        step = 2
        bs = 3
    }
    if (subdiv == 2) {
        step = 4
        bs = 6
    }
    var sr = 0
    while (sr < h) {
        var sc = 0
        while (sc < w) {
            var i = 0
            while (i < 16) {
                var j = 0
                while (j < 16) {
                    if (sr + i + step <= h && sc + j + step <= w) {
                        var r = sr + i
                        var cc = sc + j
                        var skip = m.sym.readLiteral(1)
                        var tx = m.readTxSize(r, cc, bs, skip, txMode, isInter, lossless)
                        m.setTxfmCtxs(r, cc, bs, tx, skip)
                        println("T " + r + " " + cc + " " + bs + " " + skip + " " + tx)
                    }
                    j = j + step
                }
                i = i + step
            }
            sc = sc + 16
        }
        sr = sr + 16
    }
}
""";
}
