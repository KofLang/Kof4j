package dev.kof.compiler;

/**
 * The AV1 tile-walk instrument for the committed real 64x64 testsrc fixture
 * (`avif_real_64.avif`), split out of {@code AvifRealFileE2ETest} (which hit the
 * test-size ratchet). It walks the tile with the decoder bound and reports the
 * block grid, the payload CONSUMPTION, the per-transform-block non-zero counts,
 * the reconstructed Y samples and the per-block reader positions.
 *
 * <p>MEASURED 15/10 (the OPEN `3bd` symptom): `blocks=21 covered=256 frame=256
 * overlap=0 symBits=862 payloadBits=2344 txb=63 nzTot=102
 * nzHead=0,0,26,8,0,0,2,0,...`. The walk consumes only 862 of the 2344 payload
 * bits (37%) — `ffprobe` puts the frame OBU at 311 bytes = 293 of tile data
 * after the 18 header bytes, so the decoder really stops 63% short. ffmpeg's
 * reference Y for the same fixture is (0,0)=16 (0,32)=41 (0,63)=235 (32,0)=16
 * (32,32)=210 (32,63)=16, while Kof reconstructs 128 2 10 128 127 83 — `128` is
 * the bare DC prediction, i.e. block 0's residual was skipped. So the WRONG
 * decode and the under-consumption are the same event.
 *
 * <p>`palettePossible` is wired from the frame's `allow_screen_content_tools`
 * (`avif_real_cb8.avif` and `avif_real_64.avif` set it, `cb16`/`solid` do not): with
 * the flag hardcoded to 0 the screen-content fixtures desynced at the palette
 * syntax (cb8: 23/152 bits, `all_zero=1` everywhere); wired from the frame they
 * read the palette size symbol and consume 76/152 bits with 6 non-zero coefficients.
 *
 * <p>What does NOT discriminate: an exact block tiling is GUARANTEED BY
 * CONSTRUCTION (`decode_partition` partitions the fixed frame rectangle; a
 * corrupted stream still tiles), so it is not evidence the stream is in sync.
 * What IS validated: corrupting an UNREAD tail byte (285) leaves the dump
 * identical while a CONSUMED byte (10) changes it — so `position()` is a
 * consumed counter and the shortfall is real.
 */
final class AvifRealTileProbe {

    private AvifRealTileProbe() {}

/**
 * Instrument over the committed real 64x64 testsrc fixture
 * (`avif_real_64.avif`), walking the tile with the decoder bound. It reports
 * the block grid, the payload CONSUMPTION and the per-transform-block
 * non-zero counts, and asserts only that the walk does not read PAST the
 * payload.
     *
 * <p>MEASURED 15/10 (the OPEN `3bd` symptom): `blocks=21 covered=256
 * frame=256 overlap=0 symBits=862 payloadBits=2344 txb=63 nzTot=102
 * nzHead=0,0,26,8,0,0,2,0,...`. The walk consumes only 862 of the 2344
 * payload bits (37%) — a correct tile decode consumes essentially the whole
 * payload, and `ffprobe` puts the frame OBU at 311 bytes (293 of tile data
 * after the 18 header bytes, matching the payload) — so ~63% of the stream is
 * read as `all_zero` and skipped. THAT is the discriminating datum.
     *
 * <p>What does NOT discriminate, and it RETRACTS the 25th pass's "the
 * partition is exonerated": the grid always TILES the frame. Perturbing ONE
 * payload byte and re-walking still yields `covered=256 overlap=0` (while
 * `blocks` moves 21 -> 25 and `symBits` 862 -> 1302), because
 * `decode_partition` partitions the fixed frame rectangle BY CONSTRUCTION —
 * any symbols yield a valid tiling. An exact tiling is therefore NOT evidence
 * the stream is in sync; only the consumption is.
     *
 * <p>The consumption reading is itself validated: corrupting a byte in the
 * UNREAD tail (byte 285) leaves the whole dump IDENTICAL to the baseline,
 * while corrupting a CONSUMED byte (byte 10) changes it (blocks 21 -> 25,
 * symBits 862 -> 1302). So {@code position()} is a consumed counter and the
 * 63% shortfall is real, not a reader-window artifact.
 */
    static String probe(String path) {
        return """
import image.Av1Block
import image.Av1DecodeChroma
import image.Av1DecodeTileRecon
import image.Av1ModeInfo
import image.Av1Partition
import image.Av1Quant
import image.Av1Symbol
import image.Av1TxType
import image.AvifFrame
import image.AvifGroup
import image.AvifObu

main() {
    var path = "%s"
    var h = readAvifFrameHeader(path)
    var seq = readAvifItemObus(path).seq
    var data = readAvifTilePayloads(path).get(0)
    var mw = h.codedWidth
    var mh = h.codedHeight
    var miw = mw >> 2
    var mih = mh >> 2
    var txMode = 1
    if (h.txModeSelect == 1) { txMode = 2 }
    var stride = mw + 2
    var frame = new Int[stride * (mh + 2)]
    var cw = mw >> 1
    var ch = mh >> 1
    var cstride = cw + 2
    var chroma = Av1ChromaPlanes(new Int[cstride * (ch + 2)], cstride, cw, ch, new Int[miw], new Int[mih], new Int[cstride * (ch + 2)], cstride, cw, ch, new Int[miw], new Int[mih])
    var sym = Av1Symbol(data)
    sym.enableTrace()
    var store = Av1CoeffCdfStore(h.baseQIdx)
    var txStore = Av1TxType(sym)
    var qt = Av1QuantTables()
    var dcq = qt.dcQ(seq.bitDepth, h.baseQIdx)
    var acq = qt.acQ(seq.bitDepth, h.baseQIdx)
    var fi = 0
    if (seq.enableFilterIntra) { fi = 1 }
    var de = 1
    if (seq.enableIntraEdgeFilter) { de = 0 }
    var ec = 0
    if (seq.enableCdef) { ec = 1 }
    var modes = listOf<Int>()
    var levels = listOf<Int[]>()
    var pal = 0
    if (h.allowScreenContent) { pal = 1 }
    var cfg = Av1ModeTail(12, h.cdefBits, ec, h.lossless, h.deltaQPresent, h.deltaQRes, h.deltaLfPresent, h.deltaLfMulti, h.deltaLfRes, 3)
    var tile = Av1TileDecode(frame, stride, mw, mh, cfg, h.lossless, 1, 3, fi, pal, seq.bitDepth, txMode, h.baseQIdx, 0, 0, 0, 0, dcq, acq, de, 0, h.reducedTxSet, sym, store, txStore, new Int[miw * mih], new Int[miw], new Int[mih], levels, listOf<Int>(), modes, chroma, 1, 1, listOf<Int[]>(), listOf<Int[]>(), listOf<Int>(), listOf<Int>())
    var walk = Av1Partition(data, miw, mih)
    walk.useReader(sym)
    walk.bindDecoder(tile)
    var steps = walk.walk()
    var cover = new Int[miw * mih]
    var blocks = 0
    var overlap = 0
    var i = 0
    while (i < steps.size) {
        var s = steps.get(i)
        if (s[0] == 1) {
            var w = av1BlockNum4x4Wide(s[3])
            var bh = av1BlockNum4x4High(s[3])
            blocks = blocks + 1
            var rr = 0
            while (rr < bh) {
                var cc = 0
                while (cc < w) {
                    var idx = (s[1] + rr) * miw + (s[2] + cc)
                    if (idx >= 0 && idx < cover.size) {
                        if (cover[idx] == 1) { overlap = overlap + 1 }
                        cover[idx] = 1
                    }
                    cc = cc + 1
                }
                rr = rr + 1
            }
        }
        i = i + 1
    }
    var covered = 0
    var k = 0
    while (k < cover.size) {
        covered = covered + cover[k]
        k = k + 1
    }
    var totNz = 0
    var head = ""
    var b = 0
    while (b < levels.size) {
        var lv = levels.get(b)
        var nz = 0
        var q = 0
        while (q < lv.size) {
            if (lv[q] != 0) { nz = nz + 1 }
            q = q + 1
        }
        totNz = totNz + nz
        if (b < 12) {
            if (b > 0) { head = head + "," }
            head = head + nz
        }
        b = b + 1
    }
    var ph = ""
    var pi = 0
    while (pi < data.size && pi < 12) {
        if (pi > 0) { ph = ph + "," }
        ph = ph + data[pi]
        pi = pi + 1
    }
    println("PAYLOAD " + data.size + " " + ph + " path=" + path)
    var sl = sym.log()
    var sc = 0
    var sline = ""
    while (sc < sl.size / 3 && sc < 24) {
        if (sc > 0) { sline = sline + " " }
        sline = sline + sl[sc * 3] + ":" + sl[sc * 3 + 1] + "@" + sl[sc * 3 + 2]
        sc = sc + 1
    }
    println("TRACE n=" + (sl.size / 3) + " " + sline)
    println("blocks=" + blocks + " covered=" + covered + " frame=" + (miw * mih) + " overlap=" + overlap + " symBits=" + walk.position() + " payloadBits=" + (data.size * 8) + " txb=" + levels.size + " nzTot=" + totNz + " nzHead=" + head)
    println("Y 00=" + frame[1 * stride + 1] + " 032=" + frame[1 * stride + 33] + " 063=" + frame[1 * stride + 64] + " 320=" + frame[33 * stride + 1] + " 3232=" + frame[33 * stride + 33] + " 3263=" + frame[33 * stride + 64])
    var pos = tile.positions()
    var b2 = 0
    while (b2 < modes.size / 17) {
        var mi = b2 * 17
        println("B " + b2 + " " + pos[b2 * 2] + " " + pos[b2 * 2 + 1] + " " + modes[mi] + " " + modes[mi + 4] + " " + modes[mi + 5] + " " + modes[mi + 6] + " " + modes[mi + 7] + " " + modes[mi + 8])
        b2 = b2 + 1
    }
}
""".formatted(path);
    }
}
