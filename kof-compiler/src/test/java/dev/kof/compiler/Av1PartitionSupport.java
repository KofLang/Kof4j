package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3o (the AV1 recursive
 * {@code decode_partition} tile walk, {@code libs/image/Av1Partition.kf}).
 *
 * <p>The golden is derived from libaom: an {@code aom_writer} (the real
 * {@code aom_dsp/entenc.c} range encoder with {@code allow_update_cdf}) encodes
 * a chosen partition tree while driving libaom's own partition context
 * ({@code partition_plane_context}/{@code update_ext_partition_context} over
 * {@code partition_context_lookup}) and the verbatim
 * {@code partition_gather_horz_alike}/{@code partition_gather_vert_alike}
 * helpers over {@code default_partition_cdf}. Four frame shapes (with different
 * superblock grids and edge clipping) are encoded; the golden is the sequence
 * of decisions — {@code P r c bSize partition} for a {@code @@partition} read
 * and {@code B r c subSize} for a {@code decode_block} call — in walk order.
 * The dump is gzip-compressed and base64-encoded as
 * {@code av1_partition_golden.txt.b64}.
 */
final class Av1PartitionSupport {

    private Av1PartitionSupport() {}

    static final String RESOURCE = "/av1_partition_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text (stripped), stale-resource guard. */
    static final String GOLDEN_SHA256 =
        "33b0ee9eb3b223011939a791765822dbdef3609776ebe99a2ea5242e8276f19c";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1PartitionSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that walks every encoded tile and dumps the decisions. */
    static String kofProbe() {
        return PROBE;
    }

    /**
     * The expected block grid, derived from the golden's own {@code B r c subSize}
     * records (libaom's {@code decode_block} calls) — the independent reference
     * for {@code av1TileBlockGrid}. Emitted in the same {@code G r c bs} shape the
     * grid probe prints, so the two compare line for line.
     */
    static String expectedGrid() {
        StringBuilder sb = new StringBuilder();
        for (String line : golden().split("\n")) {
            if (!line.startsWith("B ")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append('G').append(line.substring(1));
        }
        return sb.toString();
    }

    /** Kof probe that prints ONLY the block grid the tile driver consumes. */
    static String kofGridProbe() {
        return GRID_PROBE;
    }

    /**
     * Kof probe for the SHARED-READER seam ({@code Av1Partition.useReader}).
     *
     * <p>A real tile's partition symbols and block payloads share ONE entropy
     * stream (libaom's {@code decode_partition} reads the partition symbol and
     * then calls {@code decode_block} on the same {@code aom_reader}), so a walk
     * that must stay in sync with a block decoder cannot own its reader. This
     * probe builds the {@code Av1Symbol} ONCE and hands it to the walk, then
     * prints the same decisions as {@link #kofProbe()} — the seam must not change
     * a single decision.
     */
    static String kofSharedReaderProbe() {
        return SHARED_PROBE;
    }

    /**
     * Kof probe that PROVES the seam binds: it walks the same bytes twice — once
     * with the walk bound to a caller-owned {@code Av1Symbol}, once with the
     * constructor's own reader — and prints each reader's final bit position. If
     * {@code useReader} really hands the walk the shared reader, both positions
     * are EQUAL and greater than zero; if it silently does nothing, the shared
     * reader is never touched and stays at 0.
     */
    static String kofSeamProbe() {
        return SEAM_PROBE;
    }

    /**
     * The FIRST REAL INTERLEAVED TILE STREAM the walk ever reads.
     *
     * <p>Every harness before this one fed the walk a byte stream carrying ONLY
     * partition symbols. This one is a real tile: libaom's {@code decode_partition}
     * reads the partition symbol and then calls {@code decode_block} on the SAME
     * {@code aom_reader}, so the symbols and the block payloads are interleaved.
     * The stream was produced by the {@code oracle4.c} harness (one 64x64
     * superblock: PARTITION_SPLIT then PARTITION_NONE in each of the four
     * quadrants) and read back by libaom's OWN {@code read_partition} +
     * {@code av1_read_coeffs_txb}, which recovered the intended four
     * {@code BLOCK_32X32}/TX_32X32/DC_PRED blocks and a final entropy context of
     * 17 — the harness is self-consistent.
     *
     * <p>Only the first TWO partition decisions are asserted: the walk has no
     * block decoder to consume the payload between symbols, so it stays in sync
     * exactly until the first {@code decode_block}. That boundary is the whole
     * point of the next slice.
     */
    static final String REAL_TILE_BYTES = "180, 72, 0, 0, 0, 0, 0, 0, 0, 16";

    /**
     * The two decisions the walk reads from the real interleaved stream:
     * PARTITION_SPLIT (3) at BLOCK_64X64 (bSize 12), then PARTITION_NONE (0) at
     * BLOCK_32X32 (bSize 9) — the sizes are the slice-3n BLOCK_SIZES indices, not
     * the mi dimensions.
     */
    static final String REAL_TILE_HEAD =
        "W 0 0 12 3\nW 0 0 9 0";

    /** Kof probe: walk the REAL interleaved tile and print its first decisions. */
    static String kofRealTileProbe() {
        return REAL_TILE_PROBE;
    }

    private static final String REAL_TILE_PROBE = """
import image.Av1Partition
import image.Av1Symbol

main() {
    var bytes = listOf(180, 72, 0, 0, 0, 0, 0, 0, 0, 16)
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var shared = Av1Symbol(data)
    var p = Av1Partition(data, 16, 16)
    p.useReader(shared)
    var steps = p.walk()
    var shown = 0
    var i = 0
    while (i < steps.size() && shown < 2) {
        var s = steps.get(i)
        if (s[0] == 0) {
            println("W " + s[1] + " " + s[2] + " " + s[3] + " " + s[4])
            shown = shown + 1
        }
        i = i + 1
    }
}
""";

    /**
     * The REAL-TILE end-to-end reference (slice 3au): the tree walk driving the
     * tile DRIVER over the interleaved stream. The oracle is
     * {@code oracle4.c}, which ENCODED this stream (one 64x64 superblock:
     * PARTITION_SPLIT then PARTITION_NONE per quadrant, all DC_PRED, TX_32X32,
     * DC-only residuals) and decoded it back with libaom's OWN
     * {@code read_partition} + {@code av1_read_coeffs_txb}; these are its decoded
     * per-block mode records and final entropy context, with the case index
     * stripped so they compare line for line with the Kof probe.
     */
    static final String REAL_TILE_DECODED =
        "D 0 0 0 3 0 0 0 0 0 0\n"
        + "D 1 0 0 3 0 0 0 0 0 0\n"
        + "D 2 0 0 3 0 0 0 0 0 0\n"
        + "D 3 0 0 3 0 0 0 0 0 0\n"
        + "A 17 17 17 17 17 17 17 17 17 17 17 17 17 17 17 17"
        + " | 17 17 17 17 17 17 17 17 17 17 17 17 17 17 17 17\n"
        + "P 128 128 128 128\n"
        + "S 524288";

    /** Kof probe: the walk decodes the real interleaved tile end to end. */
    static String kofTileTreeProbe() {
        return TREE_PROBE;
    }

    private static final String TREE_PROBE = """
import image.Av1Coeffs
import image.Av1DecodeChroma
import image.Av1DecodeTileRecon
import image.Av1ModeInfo
import image.Av1Partition
import image.Av1Symbol
import image.Av1TxType

main() {
    var bytes = listOf(180, 72, 0, 0, 0, 0, 0, 0, 0, 16)
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var mw = 64
    var mh = 64
    var miw = 16
    var mih = 16
    var stride = mw + 2
    var frame = new Int[stride * (mh + 2)]
    var sym = Av1Symbol(data)
    var store = Av1CoeffCdfStore(80)
    var txStore = Av1TxType(sym)
    var txTypes = new Int[(mw >> 2) * (mh >> 2)]
    var above = new Int[miw]
    var left = new Int[mih]
    var levels = listOf<Int[]>()
    var txTypeOut = listOf<Int>()
    var modes = listOf<Int>()
    var cfg = Av1ModeTail(12, 0, 0, 0, 0, 0, 0, 0, 0, 3)
    var chroma = Av1ChromaPlanes(new Int[1], 1, 0, 0, new Int[1], new Int[1], new Int[1], 1, 0, 0, new Int[1], new Int[1])
    var levelsU = listOf<Int[]>()
    var levelsV = listOf<Int[]>()
    var txU = listOf<Int>()
    var txV = listOf<Int>()
    var tile = Av1TileDecode(frame, stride, mw, mh, cfg, 0, 1, 1, 0, 0, 8, 2, 80, 0, 0, 0, 0, 74, 87, 1, 0, 0, sym, store, txStore, txTypes, above, left, levels, txTypeOut, modes, chroma, 0, 0, levelsU, levelsV, txU, txV)
    var walk = Av1Partition(data, miw, mih)
    walk.useReader(sym)
    walk.bindDecoder(tile)
    walk.walk()
    var nb = modes.size / 17
    var b = 0
    while (b < nb) {
        var o = b * 17
        println("D " + b + " " + modes[o] + " " + modes[o + 1] + " " + modes[o + 4] + " " + modes[o + 5] + " " + modes[o + 6] + " " + modes[o + 7] + " " + modes[o + 8] + " " + modes[o + 11] + " " + modes[o + 12])
        b = b + 1
    }
    var a = "A"
    k = 0
    while (k < above.size) {
        a = a + " " + above[k]
        k = k + 1
    }
    a = a + " |"
    k = 0
    while (k < left.size) {
        a = a + " " + left[k]
        k = k + 1
    }
    println(a)
    // The RECONSTRUCTED pixels of the same driver, pinned: DC_PRED with no
    // neighbours and a zero residual is flat mid-gray (128 at bit depth 8),
    // so a wrong prediction/transform/plane-offset shows up here, not only in
    // the mode records above.
    var p = "P"
    var qs = listOf(0, 32)
    var qi = 0
    while (qi < qs.size) {
        var r = qs.get(qi)
        var qj = 0
        while (qj < qs.size) {
            var c = qs.get(qj)
            p = p + " " + frame[(r + 1) * stride + (c + 1)]
            qj = qj + 1
        }
        qi = qi + 1
    }
    println(p)
    var sum = 0
    var rr = 0
    while (rr < mh) {
        var cc = 0
        while (cc < mw) {
            sum = sum + frame[(rr + 1) * stride + (cc + 1)]
            cc = cc + 1
        }
        rr = rr + 1
    }
    println("S " + sum)
}
""";

    private static final String SEAM_PROBE = """
import image.Av1Partition
import image.Av1Symbol

main() {
    runCase(6, 5, listOf(236))
    runCase(24, 20, listOf(242, 194, 176))
}

void runCase(Int w, Int h, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var shared = Av1Symbol(data)
    var p = Av1Partition(data, w, h)
    p.useReader(shared)
    p.walk()
    var own = Av1Partition(data, w, h)
    own.walk()
    println("R " + shared.position() + " " + own.position())
}
""";

    private static final String SHARED_PROBE = """
import image.Av1Partition
import image.Av1Symbol

main() {
    runCase(6, 5, listOf(236))
    runCase(24, 20, listOf(242, 194, 176))
    runCase(33, 21, listOf(176, 149, 80))
    runCase(16, 40, listOf(239, 12))
}

void runCase(Int w, Int h, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var shared = Av1Symbol(data)
    var p = Av1Partition(data, w, h)
    p.useReader(shared)
    var steps = p.walk()
    var i = 0
    while (i < steps.size()) {
        var s = steps.get(i)
        if (s[0] == 0) {
            println("P " + s[1] + " " + s[2] + " " + s[3] + " " + s[4])
        } else {
            println("B " + s[1] + " " + s[2] + " " + s[3])
        }
        i = i + 1
    }
}
""";

    private static final String GRID_PROBE = """
import image.Av1Partition

main() {
    runCase(6, 5, listOf(236))
    runCase(24, 20, listOf(242, 194, 176))
    runCase(33, 21, listOf(176, 149, 80))
    runCase(16, 40, listOf(239, 12))
}

void runCase(Int w, Int h, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var p = Av1Partition(data, w, h)
    var grid = av1TileBlockGrid(p.walk())
    var i = 0
    while (i < grid.size()) {
        println("G " + grid.get(i) + " " + grid.get(i + 1) + " " + grid.get(i + 2))
        i = i + 3
    }
}
""";

    private static final String PROBE = """
import image.Av1Partition

main() {
    runCase(6, 5, listOf(236))
    runCase(24, 20, listOf(242, 194, 176))
    runCase(33, 21, listOf(176, 149, 80))
    runCase(16, 40, listOf(239, 12))
}

void runCase(Int w, Int h, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var p = Av1Partition(data, w, h)
    var steps = p.walk()
    var i = 0
    while (i < steps.size()) {
        var s = steps.get(i)
        if (s[0] == 0) {
            println("P " + s[1] + " " + s[2] + " " + s[3] + " " + s[4])
        } else {
            println("B " + s[1] + " " + s[2] + " " + s[3])
        }
        i = i + 1
    }
}
""";
}
