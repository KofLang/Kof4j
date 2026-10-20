package dev.kof.compiler;

/**
 * The KOF PROGRAM the AVIF tile probe runs — one file because a Kof program is a
 * single unit, and separated from {@link AvifRealTileProbe} so the harness class
 * stays within the repository's size ratchet. Every diagnostic it prints is read
 * back by {@link AvifRealTileProbe#summary(String)} or asserted verbatim by the
 * E2E tests.
 *
 * <p>NOTE for editors: this is a Java text block passed through
 * {@code String.formatted}, so a Kof {@code %} (the modulo operator) is a FORMAT
 * SPECIFIER and must be doubled — inside comments too.
 */
final class AvifRealTileProbeProgram {

    private AvifRealTileProbeProgram() { }

    static String program(String path) {
        return """
import image.Av1Block
import image.Av1Cdef
import image.Av1DecodeChroma
import image.Av1DecodeTileRecon
import image.Av1IntraBlock
import image.Av1ModeInfo
import image.Av1Partition
import image.Av1Quant
import image.Av1Symbol
import image.Av1TxType
import image.AvifFrame
import image.AvifGroup
import image.AvifObu
import image.AvifFrame
import image.AvifSeq

//#DIAG
main() {
    var path = "%s"
    var h = readAvifFrameHeader(path)
    var seq = readAvifItemObus(path).seq
    // How many tiles the stream carries: every fixture but the multi-tile
    // one is single-tile, and this line is what lets a test PROVE it.
    var tiles = readAvifTilePayloads(path)
    println("TILES n=" + tiles.size)
    var data = tiles.get(0)
    var mw = h.codedWidth
    var mh = h.codedHeight
    // The walk's mi grid is the SPEC's `MiCols`: libaom's decoder rounds the
    // frame up to whole 8x8 luma blocks before deriving it — `dec_set_mb_mi`
    // (fetched, av1/decoder/decoder.c) comments "Ensure that the decoded width
    // and height are both multiples of 8 luma pixels" and sets
    // `mi_cols = ALIGN_POWER_OF_TWO(width, 3) >> MI_SIZE_LOG2`. A FLOOR
    // (`mw >> 2`) is smaller whenever a dimension is not a multiple of 8, which
    // changes `has_rows`/`has_cols` at the frame's edge and therefore which
    // PARTITION BRANCH the walk takes.
    var miw = 2 * ((mw + 7) >> 3)
    var mih = 2 * ((mh + 7) >> 3)
    var txMode = 1
    if (h.txModeSelect == 1) { txMode = 2 }
    var stride = mw + 2
    var frame = new Int[stride * (mh + 2)]
    var cw = mw >> 1
    var ch = mh >> 1
    var cstride = cw + 2
    // The entropy contexts (`above`/`left` for the coefficient walk) are PADDED
    // to the superblock grid, exactly as libaom allocates them
    // (`ALIGN_POWER_OF_TWO(num_mi_cols, MAX_MIB_SIZE_LOG2)`): a block at the
    // frame's bottom edge keeps its full height, so its transform blocks slice
    // these arrays PAST the frame's mi extent.
    var ctxW = av1TxCtxExtent(miw)
    var ctxH = av1TxCtxExtent(mih)
    var chroma = Av1ChromaPlanes(new Int[cstride * (ch + 2)], cstride, cw, ch, new Int[ctxW], new Int[ctxH], new Int[cstride * (ch + 2)], cstride, cw, ch, new Int[ctxW], new Int[ctxH])
    // TILE 0's reader carries the trace, and every diagnostic below reports
    // from it; the other tiles get their own reader in the loop.
    var sym = Av1Symbol(data)
    sym.enableTrace()
    var qt = Av1QuantTables()
    var dcq = qt.dcQ(seq.bitDepth, h.baseQIdx)
    var acq = qt.acQ(seq.bitDepth, h.baseQIdx)
    var fi = 0
    if (seq.enableFilterIntra) { fi = 1 }
    var de = 1
    if (seq.enableIntraEdgeFilter) { de = 0 }
    var ec = 0
    if (seq.enableCdef) { ec = 1 }
    // The 130th pass's post-reconstruction suspect: CDEF is PARSED but never
    // applied, and a directional filter on a smooth chroma gradient produces
    // exactly the small, smooth +/-1 deviation the V plane shows. Print the
    // sequence-header switch and the frame header's `cdef_bits` so the next
    // measurement knows whether the filter is even on for this fixture.
    println("CDEF ec=" + ec + " bits=" + h.cdefBits + " damp=" + h.cdefDamping
        + " y=" + h.cdefYStrengths + " uv=" + h.cdefUvStrengths + " bq=" + h.baseQIdx)
    var modes = listOf<Int>()
    var levels = listOf<Int[]>()
    // The CHROMA transform blocks' decoded levels: the V plane's residual is where
    // the 116th pass's +/-1 has to live (the AC is hand-computed EXACT and the U
    // matches ffmpeg through the same path).
    var levelsU = listOf<Int[]>()
    var levelsV = listOf<Int[]>()
    var txTypesU = listOf<Int>()
    var txTypesV = listOf<Int>()
    var chromaPreds = listOf<Int>()
    var pal = 0
    if (h.allowScreenContent) { pal = 1 }
    var cfg = Av1ModeTail(12, h.cdefBits, ec, h.lossless, h.deltaQPresent, h.deltaQRes, h.deltaLfPresent, h.deltaLfMulti, h.deltaLfRes, 3)
    // EVERY TILE: a fresh CDF store, transform-type store, driver and walk.
    // libaom gives each tile its own copy of the frame context
    // (`tile_data->tctx = *cm->fc`, av1/decoder/decodeframe.c) and bounds its
    // superblock loop by the tile's mi rectangle, so a tile that inherited
    // another's adapting CDFs — or a walk that ran past the tile — would decode
    // the wrong stream.
    var nSbCols = (miw + 15) / 16
    var nSbRows = (mih + 15) / 16
    var tileCols2 = h.tileColumns
    if (tileCols2 < 1) { tileCols2 = 1 }
    var tileRows2 = h.tileRows
    if (tileRows2 < 1) { tileRows2 = 1 }
    var tileSbW = (nSbCols + tileCols2 - 1) / tileCols2
    var tileSbH = (nSbRows + tileRows2 - 1) / tileRows2
    var allSteps = listOf<Int[]>()
    var allModes = listOf<Int>()
    var tileStats = ""
    var totalSymBits = 0
    var totalPayloadBits = 0
    var tile = Av1TileDecode(frame, stride, mw, mh, cfg, h.lossless, 1, 3, fi, pal, seq.bitDepth, txMode, h.baseQIdx, 0, 0, 0, 0, dcq, acq, de, 0, h.reducedTxSet, sym, Av1CoeffCdfStore(h.baseQIdx), Av1TxType(sym), new Int[ctxW * ctxH], new Int[ctxW], new Int[ctxH], levels, listOf<Int>(), modes, chroma, 1, 1, levelsU, levelsV, txTypesU, txTypesV, chromaPreds)
    var walk = Av1Partition(data, miw, mih)
    var tline = ""
    var ti = 0
    while (ti < tiles.size) {
        var t0r = ((ti / tileCols2) * tileSbH) * 16
        // NOTE: the modulo operator below is DOUBLED because this program is a
        // Java text block passed through String.formatted, where a lone percent
        // sign is a format specifier -- in COMMENTS too.
        var t0c = ((ti %% tileCols2) * tileSbW) * 16
        var t1r = t0r + tileSbH * 16
        if (t1r > mih) { t1r = mih }
        var t1c = t0c + tileSbW * 16
        if (t1c > miw) { t1c = miw }
        var tsym = sym
        if (ti > 0) { tsym = Av1Symbol(tiles.get(ti)) }
        tsym.enableTrace()
        // The per-block diagnostics are rebuilt per tile as well: they pair a
        // driver's own lists (positions, stages) with `modes`/`levels`, so a
        // SHARED pair across tiles would index one tile's list with another
        // tile's block count.
        modes = listOf<Int>()
        levels = listOf<Int[]>()
        levelsU = listOf<Int[]>()
        levelsV = listOf<Int[]>()
        txTypesU = listOf<Int>()
        txTypesV = listOf<Int>()
        chromaPreds = listOf<Int>()
        chroma = Av1ChromaPlanes(chroma.u, chroma.strideU, chroma.wU, chroma.hU, new Int[ctxW], new Int[ctxH], chroma.v, chroma.strideV, chroma.wV, chroma.hV, new Int[ctxW], new Int[ctxH])
        tile = Av1TileDecode(frame, stride, mw, mh, cfg, h.lossless, 1, 3, fi, pal, seq.bitDepth, txMode, h.baseQIdx, 0, 0, 0, 0, dcq, acq, de, 0, h.reducedTxSet, tsym, Av1CoeffCdfStore(h.baseQIdx), Av1TxType(tsym), new Int[ctxW * ctxH], new Int[ctxW], new Int[ctxH], levels, listOf<Int>(), modes, chroma, 1, 1, levelsU, levelsV, txTypesU, txTypesV, chromaPreds)
        walk = Av1Partition(tiles.get(ti), miw, mih)
        walk.useReader(tsym)
        walk.bindDecoder(tile)
        var tsteps = walk.walkTileSb(t0r, t0c, t1r, t1c, av1SuperblockSize(seq.use128x128Superblock))
        var si = 0
        while (si < tsteps.size) {
            allSteps.add(tsteps.get(si))
            si = si + 1
        }
        tileStats = tileStats + av1TileStatsLine(ti, modes, levels, tile, walk, tiles.get(ti).size)
        allModes = av1AppendAll(allModes, modes)
        totalSymBits = totalSymBits + walk.position()
        totalPayloadBits = totalPayloadBits + tiles.get(ti).size * 8
        if (ti > 0) { tline = tline + " " }
        tline = tline + "t" + ti + "(" + (t0c * 4) + "," + (t0r * 4) + ")=" + frame[(t0r * 4 + 1) * stride + (t0c * 4 + 1)] + "(" + (t0c * 4 + 4) + "," + (t0r * 4) + ")=" + frame[(t0r * 4 + 1) * stride + (t0c * 4 + 5)]
        ti = ti + 1
    }
    println("TILEY " + tline)
    println("TILESTATS" + tileStats)
    var steps = allSteps
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
    var lvOut = ""
    var ln = 0
    var li = 0
    while (li < levels.size && ln < 3) {
        var lv = levels.get(li)
        var nzq = 0
        var q2 = 0
        while (q2 < lv.size) {
            if (lv[q2] != 0) { nzq = nzq + 1 }
            q2 = q2 + 1
        }
        if (nzq > 0) {
            if (ln > 0) { lvOut = lvOut + " | " }
            lvOut = lvOut + "tx" + li + ":"
            var q3 = 0
            while (q3 < lv.size && q3 < 10) {
                lvOut = lvOut + "[" + q3 + "]=" + lv[q3] + " "
                q3 = q3 + 1
            }
            ln = ln + 1
        }
        li = li + 1
    }
    println("LEVELS " + lvOut)
    var bs1 = 0
    var st = 0
    var yline = ""
    while (st < steps.size) {
        var s = steps.get(st)
        if (s[0] == 1) {
            if (bs1 < 12) {
                if (bs1 > 0) { yline = yline + " " }
                yline = yline + "(" + s[1] + "," + s[2] + ",bs" + s[3] + ")=" + frame[(s[1] * 4 + 1) * stride + (s[2] * 4 + 1)]
            }
            bs1 = bs1 + 1
        }
        st = st + 1
    }
    println("BLKY " + yline)
    var pl = tile.predBlocks()
    var pbl = 0
    var pline = ""
    while (pbl < pl.size / 7 && pbl < 12) {
        if (pbl > 0) { pline = pline + " " }
        pline = pline + "tx" + pl[pbl * 7] + "(r" + pl[pbl * 7 + 1] + ",c" + pl[pbl * 7 + 2] + ")+" + pl[pbl * 7 + 3] + "," + pl[pbl * 7 + 4] + " p=" + pl[pbl * 7 + 5] + "/r=" + pl[pbl * 7 + 6]
        pbl = pbl + 1
    }
    println("PREDBLK " + pline)
    // Per-block STAGE reader positions: body end > palette-token end > tx-size end
    // > residual end. It shows which stage of a block eats the bits.
    var sg = tile.stages()
    var po2 = tile.positions()
    var si = 0
    var sline2 = ""
    while (si < sg.size / 2 && si < 4) {
        sline2 = sline2 + " b" + si + ":" + sg[si * 2] + ">" + sg[si * 2 + 1] + ">" + po2[si * 2] + ">" + po2[si * 2 + 1]
        si = si + 1
    }
    println("STAGES" + sline2)
    // Per-block palette MODE info: does the stream even signal a palette here?
    var pi2 = tile.palInfos()
    var pil = 0
    var piline = ""
    while (pil < pi2.size / 2 && pil < 6) {
        if (pil > 0) { piline = piline + " " }
        piline = piline + "b" + pil + ":palY" + pi2[pil * 2] + "/palUv" + pi2[pil * 2 + 1]
        pil = pil + 1
    }
    println("PALINFO " + piline)
    // For the FIRST palette block: its colours and its first 8 decoded map indices,
    // beside the reference's two values.
    var fi2 = 0
    var found = 0
    var fline = ""
    var st3 = 0
    while (st3 < steps.size) {
        var s3 = steps.get(st3)
        if (s3[0] == 1) {
            // pi2 = the LAST tile's info; steps spans the frame: bound the index.
            if (found == 0 && fi2 * 2 + 1 < pi2.size && pi2[fi2 * 2] > 0) {
                fline = fline + "pal(r" + s3[1] + ",c" + s3[2] + ",bs" + s3[3] + ",n" + pi2[fi2 * 2] + ")"
                var k3 = 0
                while (k3 < pi2[fi2 * 2]) {
                    fline = fline + " col" + k3 + "=" + tile.palColorAt(s3[1], s3[2], k3)
                    k3 = k3 + 1
                }
                var k4 = 0
                while (k4 < 8) {
                    fline = fline + " idx" + k4 + "=" + tile.palIndexAt(s3[1], s3[2], k4)
                    k4 = k4 + 1
                }
                found = 1
            }
            fi2 = fi2 + 1
        }
        st3 = st3 + 1
    }
    println("PALBLK " + fline)
    // The traced reads INSIDE a position window (80..130 bits): it exposes the
    // palette block's mode-info/colour reading symbol by symbol.
    var wsl = sym.log()
    var wi = 0
    var wline = ""
    while (wi < wsl.size / 3) {
        var wpos = wsl[wi * 3 + 2]
        if (wpos >= 19 && wpos <= 74) {
            wline = wline + " " + wsl[wi * 3] + ":" + wsl[wi * 3 + 1] + "@" + wpos
        }
        wi = wi + 1
    }
    println("TRACEW" + wline)
    // The reader position at each block's ENTRY: after the partition symbols,
    // before the mode info. blockEntry - previous residual end = the partition cost.
    var bs2 = tile.blockStarts()
    var bl = 0
    var bline = ""
    while (bl < bs2.size && bl < 6) {
        if (bl > 0) { bline = bline + " " }
        bline = bline + "b" + bl + ":" + bs2[bl]
        bl = bl + 1
    }
    println("BSTART " + bline)
    // The mode-info BODY field positions (4 per block): which field eats the bits.
    var bf = tile.bodyFields()
    var bfl = 0
    var bfline = ""
    while (bfl < bf.size && bfl < 16) {
        if (bfl > 0) { bfline = bfline + " " }
        bfline = bfline + bf[bfl]
        bfl = bfl + 1
    }
    println("BODYF n=" + (bf.size / 4) + " stages=" + (sg.size / 2) + " " + bfline)
    // The Y-MODE span per block, recorded AT the read: before-position, value, after.
    var ys = tile.ySpans()
    var yi = 0
    var ysline = ""
    while (yi < ys.size / 3 && yi < 4) {
        if (yi > 0) { ysline = ysline + " " }
        ysline = ysline + "b" + yi + ":" + ys[yi * 3] + "v" + ys[yi * 3 + 1] + ">" + ys[yi * 3 + 2]
        yi = yi + 1
    }
    // The Y modes are sampled at FIXED indices, so a frame with fewer mode
    // entries than the sample points (a one-block fixture: cb8, pskip) prints
    // "-" instead of reading past the list — the probe must not crash on a
    // frame that is simply smaller than the diagnostic's window.
    var mline = ""
    var mk = listOf(5, 22, 39, 56)
    var mi = 0
    while (mi < mk.size) {
        if (mi > 0) { mline = mline + "," }
        if (mk[mi] < modes.size) {
            mline = mline + modes[mk[mi]]
        } else {
            mline = mline + "-"
        }
        mi = mi + 1
    }
    println("YSPAN n=" + (ys.size / 3) + " " + ysline + " modesY=" + mline)
    // The end of each transform block's coefficients (diagnostic).
    var te = tile.txEnds()
    var ti2 = 0
    var teline = ""
    while (ti2 < te.size && ti2 < 8) {
        if (ti2 > 0) { teline = teline + "," }
        teline = teline + te[ti2]
        ti2 = ti2 + 1
    }
    println("TXEND n=" + te.size + " " + teline)
    // The walk's INTERNAL partition decisions (s[0] == 0): r, c, bs and the
    // partition value the dispatch acted on.
    var pst = 0
    var ptline = ""
    var pc = 0
    while (pst < steps.size && pc < 8) {
        var sp = steps.get(pst)
        if (sp[0] == 0) {
            if (pc > 0) { ptline = ptline + " " }
            ptline = ptline + "(r" + sp[1] + ",c" + sp[2] + ",bs" + sp[3] + ")p" + sp[4]
            pc = pc + 1
        }
        pst = pst + 1
    }
    println("PART n=" + pc + " " + ptline)
    // How many symbols the palette-token stage of block 1 read (its window comes
    // from STAGES: body end .. palette end), to compare against the colour map's
    // expected `rows * cols - 1` index symbols.
    if (sg.size >= 4) {
        var tsl = sym.log()
        var pcount = 0
        var plast = 0
        var ti = 0
        while (ti < tsl.size / 3) {
            var tpos = tsl[ti * 3 + 2]
            if (tpos > sg[2] && tpos < sg[3]) { pcount = pcount + 1 }
            if (tpos > sg[2] && tpos <= sg[3]) { plast = tpos }
            ti = ti + 1
        }
        println("PALTOK window=" + sg[2] + ".." + sg[3] + " bits=" + (sg[3] - sg[2]) + " symbols=" + pcount + " lastPos=" + plast + " total=" + (tsl.size / 3))
    }
    // Experiment: reproduce block 1's BOTTOM transform block (8x8 at pixel
    // origin (8,8)) by calling the predictor DIRECTLY with explicit arguments,
    // for all four (aboveRight, belowLeft) combinations — so a wrong
    // availability is separated from a wrong angle application without
    // touching the driver.
    var bmi = 17
    if (modes.size >= 34) {
        var bMode = modes[bmi + 5]
        var bAng = modes[bmi + 6]
        var bFI = modes[bmi + 11]
        var bFIM = modes[bmi + 12]
        var combos = ""
        var ar = 0
        while (ar <= 1) {
            var bl = 0
            while (bl <= 1) {
                var pp = av1IntraPredictBlock(frame, stride, 9, 9, 8, 8, mw, mh, 1, 1, ar, bl, bMode, bAng, bFI, bFIM, seq.bitDepth, 0, true)
                combos = combos + " ar" + ar + "bl" + bl + "=" + pp[0]
                bl = bl + 1
            }
            ar = ar + 1
    }
    println("PREDTEST mode=" + bMode + " ang=" + bAng + " fi=" + bFI + " fim=" + bFIM + combos)
    println("PREDREF above=" + frame[8 * stride + 9] + "," + frame[8 * stride + 10] + "," + frame[8 * stride + 11] + " left=" + frame[9 * stride + 8] + "," + frame[10 * stride + 8] + "," + frame[11 * stride + 8] + " corner=" + frame[8 * stride + 8])
    }
    // A PATCH of the FINAL frame (an 18x18 window at the origin, rows/cols
    // 0..17): it shows where a block's write landed a value the reference
    // never has, which localises an out-of-extent or desynced write.
    var pmaxr = 17
    if (pmaxr >= mh) { pmaxr = mh - 1 }
    var pmaxc = 17
    if (pmaxc >= mw) { pmaxc = mw - 1 }
    var py2 = 0
    while (py2 <= pmaxr) {
        var prow = ""
        var px2 = 0
        while (px2 <= pmaxc) {
            prow = prow + frame[(py2 + 1) * stride + (px2 + 1)] + " "
            px2 = px2 + 1
        }
        println("PATCH r" + py2 + " " + prow)
        py2 = py2 + 1
    }
    println("SEG segEnabled=" + h.segEnabled + " altQ0=" + h.segAltQ[0] + " altQ1=" + h.segAltQ[1])
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
    // How many blocks carry UV_CFL_PRED (uvMode 13): the CFL path is what a
    // block at a frame edge exercises, and a test must be able to PROVE the
    // fixture reaches it.
    var uvCfl = 0
    var uc = 0
    while (uc < modes.size / 17) {
        if (modes[uc * 17 + 7] == 13) { uvCfl = uvCfl + 1 }
        uc = uc + 1
    }
    println("blocks=" + blocks + " covered=" + covered + " frame=" + (miw * mih) + " overlap=" + overlap + " symBits=" + totalSymBits + " payloadBits=" + totalPayloadBits + " txb=" + levels.size + " nzTot=" + totNz + " nzHead=" + head + " uvCFL=" + uvCfl)
    probeTail(frame, stride, mw, mh, chroma, levelsV, steps, dcq, acq, h.baseQIdx, seq.bitDepth, tile, modes, txTypesV, levelsU, txTypesU, h.quantDeltas, chromaPreds, h.cdefDamping, h.cdefYStrengths, h.cdefUvStrengths, allModes)
}
""".formatted(path).replace("//#DIAG", AvifRealTileProbeDiag.source());
    }
}
