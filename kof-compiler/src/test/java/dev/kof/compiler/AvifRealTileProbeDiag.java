package dev.kof.compiler;

/**
 * The AVIF tile probe's DIAGNOSTIC TAIL as a Kof function: the Y and UV
 * sample lines, the V plane's levels and their dequantized values, and the
 * per-block `B` lines. Split out of {@link AvifRealTileProbeProgram} so each
 * Java file stays within the repository's size ratchet — the Kof side is
 * unchanged, because the harness injects this source into the same program.
 *
 * <p>NOTE for editors: this text is injected AFTER {@code String.formatted}
 * runs, so a Kof {@code %} needs no doubling here.
 */
final class AvifRealTileProbeDiag {

    private AvifRealTileProbeDiag() { }

    static String source() {
        return """
List<Int> av1AppendAll(List<Int> dst, List<Int> src) {
    var i = 0
    while (i < src.size) {
        dst.add(src.get(i))
        i = i + 1
    }
    return dst
}

// One tile's counters: blocks / transform blocks / NONZERO levels / the
// driver's stage positions / the walk's end position / the tile's payload
// bits. The 137th pass used it to show that mt192's second block decodes 16
// all-zero TXBs and the tile stops 190 bits early (known-bugs §754).
String av1TileStatsLine(Int ti, List<Int> modes, List<Int[]> levels,
                        Av1TileDecode tile, Av1Partition walk, Int payBytes) {
    var tz = 0
    var tl = 0
    while (tl < levels.size) {
        var la = levels.get(tl)
        var lk = 0
        while (lk < la.size) {
            if (la[lk] != 0) { tz = tz + 1 }
            lk = lk + 1
        }
        tl = tl + 1
    }
    var stv = ""
    var sv = 0
    var stl = tile.stages()
    while (sv < stl.size) {
        stv = stv + "," + stl.get(sv)
        sv = sv + 1
    }
    var ys2 = ""
    var yl2 = tile.ySpans()
    var yi2 = 0
    while (yi2 < yl2.size) {
        ys2 = ys2 + "," + yl2.get(yi2)
        yi2 = yi2 + 1
    }
    var bf2 = ""
    var bl2 = tile.bodyFields()
    var bi2 = 0
    while (bi2 < bl2.size) {
        bf2 = bf2 + "," + bl2.get(bi2)
        bi2 = bi2 + 1
    }
    return " t" + ti + ":b" + (modes.size / 17) + "/txb" + levels.size + "/nz" + tz
        + "/st" + stl.size() + stv + "/y" + ys2 + "/bf" + bf2 + "/pos" + walk.position() + "/pay" + payBytes * 8
}

void probeTail(Int[] frame, Int stride, Int mw, Int mh,
               Av1ChromaPlanes chroma, List<Int[]> levelsV, List<Int[]> steps,
               Int dcq, Int acq, Int baseQIdx, Int bitDepth,
               Av1TileDecode tile, List<Int> modes, List<Int> txTypesV,
               List<Int[]> levelsU, List<Int> txTypesU, List<Int> quantDeltas,
               List<Int> chromaPreds, Int cdefDamping, List<Int> cdefY,
               List<Int> cdefUv, List<Int> allModes) {
        // The sample positions are clamped to the frame: they were hardcoded for a
    // 64x64 frame and read out of range on anything smaller (a 32x32 frame put
    // the (32,63) sample at index 1186 of a 1156-entry plane+border buffer).
    var sx1 = 32
    if (sx1 >= mw) { sx1 = mw - 1 }
    var sx2 = 63
    if (sx2 >= mw) { sx2 = mw - 1 }
    var sy = 32
    if (sy >= mh) { sy = mh - 1 }
    println("Y 00=" + frame[1 * stride + 1] + " 032=" + frame[1 * stride + (sx1 + 1)] + " 063=" + frame[1 * stride + (sx2 + 1)] + " 320=" + frame[(sy + 1) * stride + 1] + " 3232=" + frame[(sy + 1) * stride + (sx1 + 1)] + " 3263=" + frame[(sy + 1) * stride + (sx2 + 1)])
    // The CHROMA at the same points: CFL acts on the chroma, so a luma oracle
    // alone cannot see it (the 115th pass's instrument gap).
    var cl = ""
    var ci2 = 0
    while (ci2 < levelsV.size) {
        var lv = levelsV.get(ci2)
        var k5 = 0
        while (k5 < lv.size) {
            if (lv[k5] != 0) { cl = cl + " t" + ci2 + "[" + k5 + "]=" + lv[k5] }
            k5 = k5 + 1
        }
        ci2 = ci2 + 1
    }
    // The V transform blocks' TYPES: the 16x16 DCT path is exercised exactly by
    // other fixtures, so a different type here is the discriminator for the +/-1.
    // The CFL ALPHAS of the first block (offsets 9 and 10 of the 17-field mode
    // record: skip, segId, cdef, qIndex, txSz, body[0..7], deltaLf[0..3]).
    // They are the prediction's only free input, and the 123rd pass left the
    // prediction as the last place the V plane's +/-1 can be.
    var aline = "none"
    if (modes.size >= 17) { aline = modes[9] + " " + modes[10] }
    // The frame header's per-plane quantizer deltas [Y, Udc, Uac, Vdc, Vac] and
    // the quantization-matrix flag: parsed and DISCARDED until the 126th pass,
    // which is the 125th pass's candidate root cause for the V plane's +/-1.
    println("QDELTAS " + quantDeltas)
    // The chroma PREDICTIONS (2 per transform block, U then V), before the
    // residual: the only term of the V plane's chain never measured.
    println("CPRED n=" + (chromaPreds.size / 2) + " " + chromaPreds)
    println("CFLA " + aline)
    println("VTXT n=" + txTypesV.size + " " + txTypesV)
    // The U plane's levels too: if the U's residual is ZERO then its exactness
    // says nothing about the residual path, and the V is its only witness.
    var cu = ""
    var ui = 0
    while (ui < levelsU.size) {
        var lu = levelsU.get(ui)
        var k7 = 0
        while (k7 < lu.size) {
            if (lu[k7] != 0) { cu = cu + " t" + ui + "[" + k7 + "]=" + lu[k7] }
            k7 = k7 + 1
        }
        ui = ui + 1
    }
    println("ULEV n=" + levelsU.size + cu)
    println("VLEV n=" + levelsV.size + cl)
    // The DEQUANTIZED values of that same V transform block: the three levels
    // above times the quantizer. The dequant is a plain multiply (the AV1's
    // dc_q/ac_q tables already carry the scaling), so a hand computation can
    // say whether the ±1 enters here or in the inverse transform.
    // The DEQUANTIZED values of the first V transform block (the step between the
    // levels and the pixel, where a +/-1 can enter with the entropy intact). Only
    // when the block's shape matches the level array: on a multi-block fixture the
    // first V TX may belong to a later block.
    var dqv = ""
    var bs2 = 9
    var s2 = 0
    while (s2 < steps.size) {
        if (steps.get(s2)[0] == 1) { bs2 = steps.get(s2)[3] }
        s2 = s2 + 1
    }
    var uvTx2 = av1GetMaxUvTxSize(bs2, 1, 1)
    if (levelsV.size > 0 && av1TxWidth(uvTx2) * av1TxHeight(uvTx2) == levelsV.get(0).size) {
        var dq2 = av1Dequant(levelsV.get(0), uvTx2, bitDepth, dcq, acq)
        var k6 = 0
        while (k6 < dq2.size) {
            if (dq2[k6] != 0) { dqv = dqv + " t0[" + k6 + "]=" + dq2[k6] }
            k6 = k6 + 1
        }
    }
    println("DQV dcq=" + dcq + " acq=" + acq + " qIdx=" + baseQIdx + dqv)
    // The V transform block's RECONSTRUCTED RESIDUAL (the inverse transform's
    // own output): av1ReconstructBlock with a ZERO prediction returns it, and
    // it is the one link of the chain with no oracle yet. The U's residual is
    // DC-only, so the AC butterflies are exercised only here.
    var vres = ""
    if (levelsV.size > 0 && txTypesV.size > 0 && av1TxWidth(uvTx2) * av1TxHeight(uvTx2) == levelsV.get(0).size) {
        var z = new Int[av1TxWidth(uvTx2) * av1TxHeight(uvTx2)]
        var rec = av1ReconstructBlock(levelsV.get(0), z, uvTx2, txTypesV.get(0), bitDepth, false, dcq, acq)
        var w2 = av1TxWidth(uvTx2)
        // The V transform may be a NON-SQUARE 16x4/4x16 (the guard above only
        // checks the COEFFICIENT COUNT), so the sample indices are clamped by
        // BOTH dimensions; for the square blocks this prints exactly as before.
        var h2 = av1TxHeight(uvTx2)
        var iB = w2
        if (h2 < 2) { iB = 0 }
        vres = " (0,0)=" + rec[0] + " (0,1)=" + rec[1] + " (1,0)=" + rec[iB]
        var s15 = 15
        if (s15 >= w2) { s15 = w2 - 1 }
        if (s15 >= h2) { s15 = h2 - 1 }
        vres = vres + " (1,1)=" + rec[iB + 1] + " (15,15)=" + rec[w2 * s15 + s15]
        // The BOTTOM rows the derived prediction uses (the 124th pass): with
        // alpha_V = 0 the prediction should be a per-block constant, so the
        // spread 129 -> 127/125 has to be in the residual or in the DC.
        var r11 = 11
        if (r11 >= w2) { r11 = w2 - 1 }
        if (r11 >= h2) { r11 = h2 - 1 }
        vres = vres + " (0,11)=" + rec[w2 * r11] + " (8,11)=" + rec[w2 * r11 + (w2 >> 1)]
    }
    // The U transform block's residual too: it is DC-ONLY, so it is the case the
    // decoder is known to get EXACTLY right and the reference transcription can be
    // validated against.
    var ures = ""
    if (levelsU.size > 0 && txTypesU.size > 0 && av1TxWidth(uvTx2) * av1TxHeight(uvTx2) == levelsU.get(0).size) {
        var zu = new Int[av1TxWidth(uvTx2) * av1TxHeight(uvTx2)]
        var recu = av1ReconstructBlock(levelsU.get(0), zu, uvTx2, txTypesU.get(0), bitDepth, false, dcq, acq)
        ures = " (0,0)=" + recu[0] + " (0,1)=" + recu[1] + " (1,0)=" + recu[av1TxWidth(uvTx2)]
    }
    println("URES" + ures)
    println("VRES" + vres)
    println("UV 00=" + chroma.u[1 * chroma.strideU + 1] + "," + chroma.v[1 * chroma.strideV + 1]
        + " 032=" + chroma.u[1 * chroma.strideU + ((sx1 >> 1) + 1)] + "," + chroma.v[1 * chroma.strideV + ((sx1 >> 1) + 1)]
        + " 063=" + chroma.u[1 * chroma.strideU + ((sx2 >> 1) + 1)] + "," + chroma.v[1 * chroma.strideV + ((sx2 >> 1) + 1)]
        + " 320=" + chroma.u[((sy >> 1) + 1) * chroma.strideU + 1] + "," + chroma.v[((sy >> 1) + 1) * chroma.strideV + 1]
        + " 3232=" + chroma.u[((sy >> 1) + 1) * chroma.strideU + ((sx1 >> 1) + 1)] + "," + chroma.v[((sy >> 1) + 1) * chroma.strideV + ((sx1 >> 1) + 1)]
        + " 3263=" + chroma.u[((sy >> 1) + 1) * chroma.strideU + ((sx2 >> 1) + 1)] + "," + chroma.v[((sy >> 1) + 1) * chroma.strideV + ((sx2 >> 1) + 1)])
    // The FULL reconstructed planes (pre-CDEF): the V plane's +/-1 is the
    // missing CDEF filter, and a per-sample print cannot feed the filter's
    // oracle. Y rows then U/V rows, every sample, no clamping.
    var yr3 = 0
    while (yr3 < mh) {
        var yl3 = ""
        var xr3 = 0
        while (xr3 < mw) {
            yl3 = yl3 + " " + frame[(yr3 + 1) * stride + (xr3 + 1)]
            xr3 = xr3 + 1
        }
        println("YROW " + yr3 + yl3)
        yr3 = yr3 + 1
    }
    var ch3 = (mh + 1) / 2
    var cw3 = (mw + 1) / 2
    var cr3 = 0
    while (cr3 < ch3) {
        var ul3 = ""
        var vl3 = ""
        var xc3 = 0
        while (xc3 < cw3) {
            ul3 = ul3 + " " + chroma.u[(cr3 + 1) * chroma.strideU + (xc3 + 1)]
            vl3 = vl3 + " " + chroma.v[(cr3 + 1) * chroma.strideV + (xc3 + 1)]
            xc3 = xc3 + 1
        }
        println("UROW " + cr3 + ul3)
        println("VROW " + cr3 + vl3)
        cr3 = cr3 + 1
    }
    // POST-CDEF through the TILE's own state: the per-mi skip grid
    // (`is_8x8_block_skip`) and the per-64x64 cdef configuration index select
    // the strengths, and the luma direction drives the taps. This is the tile
    // integration of the core `Av1CdefE2ETest` proves; the V plane below must
    // become ffmpeg's entire 12x16 post-CDEF plane.
    var dirsC = av1CdefDirs(frame, stride, 1, 1, mw, mh, 0)
    var lnbx = (mw + 7) / 8
    var lnby = (mh + 7) / 8
    var skipsC = tile.skipMap()
    var cdefsC = tile.cdefMap()
    var miWc = tile.miCols()
    var bpY = new Int[lnbx * lnby]
    var bpUv = new Int[lnbx * lnby]
    var bsk = new Int[lnbx * lnby]
    var byc = 0
    while (byc < lnby) {
        var bxc = 0
        while (bxc < lnbx) {
            var gi = byc * lnbx + bxc
            var unitR = ((byc * 2) / 16) * 16
            var unitC = ((bxc * 2) / 16) * 16
            var idx = cdefsC[unitR * miWc + unitC]
            // An index outside the frame header's configuration list cannot
            // select a strength: copy the block (and stay in bounds — the
            // palette-free stream exposed an index past the list).
            if (idx < 0 || idx >= cdefY.size) {
                bsk[gi] = 1
            } else {
                if (av1CdefSkip8(skipsC, miWc, byc * 2, bxc * 2)) {
                    bsk[gi] = 1
                } else {
                    bpY[gi] = cdefY.get(idx)
                    bpUv[gi] = cdefUv.get(idx)
                }
            }
            bxc = bxc + 1
        }
        byc = byc + 1
    }
    var cw4 = (mw + 1) / 2
    var ch4 = (mh + 1) / 2
    // The COMPLETE post-CDEF frame: luma (its own configuration), U and V (the
    // UV configuration), each block resolved through the driver's maps.
    var yf4 = av1CdefFilterPlaneBlocks(frame, stride, 1, 1, mw, mh, 0, bpY, bsk, cdefDamping, dirsC, lnbx, 0, 0, 0)
    var uf4 = av1CdefFilterPlaneBlocks(chroma.u, chroma.strideU, 1, 1, cw4, ch4, 1, bpUv, bsk, cdefDamping, dirsC, lnbx, 1, 1, 0)
    var vf4 = av1CdefFilterPlaneBlocks(chroma.v, chroma.strideV, 1, 1, cw4, ch4, 1, bpUv, bsk, cdefDamping, dirsC, lnbx, 1, 1, 0)
    var rr4 = 0
    while (rr4 < mh) {
        var yl4 = ""
        var cc4 = 0
        while (cc4 < mw) {
            yl4 = yl4 + " " + yf4[rr4 * mw + cc4]
            cc4 = cc4 + 1
        }
        println("YROWF " + rr4 + yl4)
        rr4 = rr4 + 1
    }
    // One plane at a time: the golden assertions compare each plane's rows as
    // contiguous lines.
    rr4 = 0
    while (rr4 < ch4) {
        var ul4 = ""
        var cc4 = 0
        while (cc4 < cw4) {
            ul4 = ul4 + " " + uf4[rr4 * cw4 + cc4]
            cc4 = cc4 + 1
        }
        println("UROWF " + rr4 + ul4)
        rr4 = rr4 + 1
    }
    rr4 = 0
    while (rr4 < ch4) {
        var vl4 = ""
        var cc4 = 0
        while (cc4 < cw4) {
            vl4 = vl4 + " " + vf4[rr4 * cw4 + cc4]
            cc4 = cc4 + 1
        }
        println("VROWF " + rr4 + vl4)
        rr4 = rr4 + 1
    }
    // EVERY tile's mode records (the per-tile `B` lines below only see the
    // LAST tile): skip, txSize, yMode, uvMode per block, in walk order.
    var ma = ""
    var mb = 0
    while (mb < allModes.size / 17) {
        var mi = mb * 17
        ma = ma + " (" + allModes[mi] + "," + allModes[mi + 4] + "," + allModes[mi + 5] + "," + allModes[mi + 6] + ")"
        mb = mb + 1
    }
    println("MODESA n=" + (allModes.size / 17) + ma)
    // The LAST tile reader's per-symbol trace (n:symbol@bit): the coefficient
    // walk of its blocks, which is where a palette-free stream stops short.
    var sl2 = tile.reader.log()
    var sl2line = ""
    var li2 = 0
    while (li2 + 2 < sl2.size) {
        sl2line = sl2line + " " + sl2[li2] + ":" + sl2[li2 + 1] + "@" + sl2[li2 + 2]
        li2 = li2 + 3
    }
    println("TRACEL n=" + (sl2.size / 3) + sl2line)
    var pos = tile.positions()
    var b2 = 0
    while (b2 < modes.size / 17) {
        var mi = b2 * 17
        println("B " + b2 + " " + pos[b2 * 2] + " " + pos[b2 * 2 + 1] + " " + modes[mi] + " " + modes[mi + 4] + " " + modes[mi + 5] + " " + modes[mi + 6] + " " + modes[mi + 7] + " " + modes[mi + 8])
        b2 = b2 + 1
    }
}
""";
    }
}
