package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for a SMALL screen-content frame that bounds the open {@code 3bd}
 * divergence far more tightly than the 64x64 {@code testsrc} fixture.
 *
 * <ul>
 *   <li>{@code avif_real_sq1.avif} — 32x32, an 18-byte payload. The left half
 *       is ONE {@code BLOCK_16X16} that reconstructs EXACTLY (flat 100) and the
 *       payload is consumed to its LAST BIT (144/144), while the right half
 *       (vertical 4px 16/235 stripes) decodes WRONG. It is the smallest
 *       reproducer on this front and the first whose entropy is FULLY consumed
 *       yet whose pixels still diverge — so full consumption alone is not a
 *       correctness criterion.</li>
 * </ul>
 */
class AvifScreenContentGeoE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Symbol.kf"); }

    private final CompilerDriver compiler = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void smallFrameConsumesTheWholePayloadYetDivergesOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_sq1.avif", "sq1.avif"));
        assertTrue(line.contains("symBits=144 payloadBits=144"),
                () -> "the whole payload must be consumed: " + line);
        assertTrue(line.contains("Y 00=100 032=235"), () -> "the samples are not the reference: " + line);
        // RECORDED, not asserted as correct. The palette-token stage of block 1
        // (window 107..144) costs 37 bits. Its symbol count is a LOWER BOUND:
        // the reader saturates at the payload end, so every later read reports
        // the SAME position and cannot be told apart from a palette read.
        // RECORDED: block 1 IS signalled as a 2-colour palette block (palY=2)
        // over a 16x8 map, so "Kof takes the palette path where the encoder
        // wrote none" is REFUTED — a 2-colour palette is exactly right for the
        // reference's 4px 16/235 stripes, and ~37 bits for its 128-cell index
        // map is plausible.
        // RECORDED: the block-ENTRY reader positions decompose each block into
        // partition / mode-info / residual. sq1: b0 entry 19, b1 entry 76 —
        // so block 0 is 1 bit of partition + 5 body + 1 tx size + 49 residual,
        // and block 1 is 2 bits of partition + 31 body + 37 palette.
        // The BODY field positions still decompose block 1's body.
        assertTrue(line.contains("BODYF n=8 stages=8 21 21 22 24 78 78 "),
                () -> "the body-field decomposition moved: " + line);
        assertTrue(line.contains("BSTART b0:19 b1:76 b2:129"),
                () -> "the block-entry decomposition moved: " + line);
        assertTrue(line.contains("PALINFO b0:palY0/palUv0 b1:palY2/palUv0"),
                () -> "the palette MODE info moved: " + line);
        assertTrue(line.contains("PALBLK pal(r0,c4,bs5,n2) col0=16 col1=235 idx0=0 idx1=0 "
                + "idx2=0 idx3=0 idx4=1"), () -> "the palette map is not exact: " + line);
        // Recorded, NOT asserted as correct: the left half is exact, the right
        // half (reference: 16/235 vertical stripes) is not.
        assertTrue(line.contains(
                "PATCH r0 100 100 100 100 100 100 100 100 100 100 100 100 100 100 100 100 16 16"),
                () -> "the palette block is not the reference: " + line);
    }

    /**
     * Frame heights that are NOT a multiple of the block grid (32x24 and 32x40,
     * the same screen content as {@code sq1}) — built to exercise the palette
     * OVERHANG defect found in the 65th pass. RECORDED, not asserted as correct:
     * both fail in the SAME pattern as the 32x32 frame, with block 1's
     * palette-token stage consuming the rest of the payload
     * ({@code b0:24>24>25>74 b1:107>136>136>136} at 32x24,
     * {@code b0:25>25>26>76 b1:109>152>152>152} at 32x40), and the bottom-right
     * pixel reads 81 where the reference is 235.
     *
     * <p>They also REFUTE the overhang defect as the trigger of this desync:
     * block 1 is at (0,4) BLOCK_16X8 in a 32-wide frame, i.e. its
     * {@code mb_to_right_edge == 0}, so {@code av1_get_block_dimensions} does
     * NOT reduce it — the failure reproduces at a NON-overhanging block.
     */
    @Test
    void overhangingHeightFramesRecordTheSameBlockOneDesyncOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_oh24.avif", "oh24.avif"));
        assertTrue(line.contains("BLKY (0,0,bs6)=100 (0,4,bs5)=16"),
                () -> "the 32x24 first palette block is not the reference: " + line);
        var line2 = runKof(canonical("avif_real_oh40.avif", "oh40.avif"));
        assertTrue(line2.contains("covered=80 frame=80"), () -> "the 32x40 walk moved: " + line2);
    }

    /**
     * The 64x256 TALL fixture (built to exercise the palette-Y MODE context at
     * the 64x64 superblock row starts). RECORDED, not asserted as correct: it
     * consumes its payload to the last bit (296/296) and its RIGHT half is wrong
     * while the LEFT half is exact. Geometry it records: block 0 is a
     * `BLOCK_32X32` covering the flat left half (reconstructed 100 = the
     * reference) and the FIRST right-half block — `(0,8)` `BLOCK_16X8` — is a
     * 2-colour palette block (`PALINFO b1:palY2`) whose palette-token stage costs
     * 90 bits (118 -> 208) of the 296, after which the reader is exhausted.
     */
    @Test
    void tallFrameRecordsTheRightHalfDivergenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_tall.avif", "tall.avif"));
        assertTrue(line.contains("blocks=30 covered=1024 frame=1024 overlap=0 "
                + "symBits=296 payloadBits=296"), () -> "the tall walk moved: " + line);
        assertTrue(line.contains("Y 00=100 "), () -> "the left half must be exact: " + line);
        assertTrue(line.contains("BLKY (0,0,bs9)=100 (0,8,bs5)=16"),
                () -> "the half-frame split moved: " + line);
        assertTrue(line.contains("PALINFO b0:palY0/palUv0 b1:palY2/palUv0"),
                () -> "the tall palette MODE info moved: " + line);
        assertTrue(line.contains("STAGES b0:22>22>23>82 b1:117>139"),
                () -> "the tall stage positions moved: " + line);
    }

    /**
     * The SAME 32x32 screen content re-encoded by libaom with `-lossless 1`.
     * RECORDED, not asserted as correct — and it carries a SHARPER symptom than
     * any previous fixture: the LEFT half is exact (`100` x16) and the right half
     * is painted with the TWO PALETTE COLOURS INVERTED — Kof writes `235` at
     * x = 16..19 where the reference writes `16` (the reference's stripes start
     * with 16 at x = 16). A 2-colour palette whose index map or colour order is
     * swapped produces exactly this, and the payload is fully consumed
     * (152/152), so the entropy stays in sync: this is a MAP/COLOUR-ORDER
     * symptom, not a desync.
     */
    @Test
    void losslessEncodePaletteMapIsExactOnJvm() throws Exception {
        var line = runKof(canonical("avif_enc_ll.avif", "ll.avif"));
        assertTrue(line.contains("symBits=152 payloadBits=152"),
                () -> "consumption moved: " + line);
        // The reference rows 0..5 are `100` x16 then `16 16` at x = 16,17.
        assertTrue(line.contains("PATCH r0 100 100 100 100 100 100 100 100 100 100 "
                + "100 100 100 100 100 100 16 16"),
                () -> "the palette block is not the reference: " + line);
        assertTrue(line.contains("Y 00=100 032=235"), () -> "samples moved: " + line);
        // THE FIX'S PROOF: the colours are the reference's two values AND the
        // index map is EXACT — 0,0,0,0,1,1,1,1 is the reference's stripe pattern
        // (x = 16..23 are 16,16,16,16,235,235,235,235).
        assertTrue(line.contains("PALBLK pal(r0,c4,bs5,n2) col0=16 col1=235 "
                + "idx0=0 idx1=0 idx2=0 idx3=0 idx4=1 idx5=1 idx6=1 idx7=1"),
                () -> "the palette map is not exact after the fix: " + line);
    }

    /**
     * A NON-SQUARE screen-content frame: 64x16, carrying the same 4px 16/235
     * checkerboard the exact fixtures use. Every fixture that decoded EXACTLY
     * before this one was square (or flat), and a 64x16 frame was measured to
     * THROW inside the Kof decode (`Index 64 out of bounds for length 64`,
     * recorded in the plan with its recipe and deliberately NOT committed).
     * ffmpeg's reference Y is (0,0)=16 (32,0)=16 (63,0)=235 (0,15)=235
     * (32,15)=235 (63,15)=16.
     */
    @Test
    void nonSquareFrameDecodesToReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_ns6416.avif", "ns.avif"));
        assertTrue(line.contains("Y 00=16 032=16 063=235 320=235 3232=235 3263=16"),
                () -> "must match the ffmpeg reference: " + line);
    }

    /**
     * A frame whose dimensions are NOT multiples of 8: 20x20, carrying a 2px
     * 16/235 checkerboard — the finest partition this encoder will choose at
     * this size, which is what makes the edge block reachable. The spec and libaom size
     * the mi grid as {@code 2 * ceil(w / 8)} (the spec's {@code MiCols} — the mi
     * extent ROUNDED UP to a whole 8x8 block; fetched, av1/common/alloccommon.c
     * {@code ALIGN_POWER_OF_TWO(width, 3) >> MI_SIZE_LOG2}), while the probe used
     * {@code w >> 2}, a FLOOR: on a 20x20 frame the walk's {@code has_rows} /
     * {@code has_cols} at the edge answered from a 5x5 grid instead of the
     * reference's 6x6 and therefore took different partition BRANCHES (a full
     * partition read where libaom gathers, or the reverse). ffmpeg's reference
     * rows (columns 0..17) are {@code 16 16 235 235 16 16 235 235 16 16 235 235
     * 16 16 235 235 16 16} at y = 0 and the inverse at y = 2 (a 2px checkerboard,
     * the finest partition the encoder can reach at this size).
     */
    @Test
    void unalignedFrameDecodesToReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_ua20.avif", "ua.avif"));
        assertTrue(line.contains("PATCH r0 16 16 235 235 16 16 235 235 16 16 235 235 16 16 235 235 16 16"),
                () -> "row 0 must match the ffmpeg reference: " + line);
        assertTrue(line.contains("PATCH r2 235 235 16 16 235 235 16 16 235 235 16 16 235 235 16 16 235 235"),
                () -> "row 2 must match the ffmpeg reference: " + line);
    }

    /**
     * A frame coded with MULTIPLE TILES: 128x128, the same 4px checkerboard,
     * encoded with {@code -tiles 2x2}. Every other fixture in this suite is
     * SINGLE-tile, so the tile-group header's per-tile sizes and the payload
     * split were never exercised at all. The probe decodes the FIRST tile (the
     * top-left 64x64), whose samples ffmpeg's plane gives as (0,0)=16 (32,0)=16
     * (63,0)=235 (0,32)=16 (32,32)=16 (63,32)=235.
     */
    @Test
    void multiTileFrameDecodesItsFirstTileToReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_mt128.avif", "mt.avif"));
        assertTrue(line.contains("TILES n=4"), () -> "the fixture must be 2x2 tiles: " + line);
        assertTrue(line.contains("Y 00=16 032=16 063=235 320=16 3232=16 3263=235"),
                () -> "tile 0 must match the ffmpeg reference: " + line);
    }

    /**
     * The WHOLE multi-tile frame, not just its first tile: each tile gets its
     * own reader, CDF store and driver (libaom gives every tile a copy of the
     * frame context, `tile_data->tctx = *cm->fc`) and its own walk, bounded by
     * the tile's mi rectangle (libaom's `decode_tile` bounds its superblock loop
     * by `TileInfo.mi_row_start`/`mi_row_end`). ffmpeg's plane gives, at every
     * tile's own origin pair, the checkerboard's even cell (16) and the one 4px
     * right of it (235) — a tile decoded with another tile's CDFs, or a walk
     * that ran past its tile, breaks that pattern.
     */
    @Test
    void multiTileFrameDecodesTheWholeFrameToReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_mt128.avif", "mt.avif"));
        assertTrue(line.contains("TILES n=4"), () -> "the fixture must be 2x2 tiles: " + line);
        assertTrue(line.contains("TILEY t0(0,0)=16(4,0)=235 t1(64,0)=16(68,0)=235 "
                        + "t2(0,64)=16(4,64)=235 t3(64,64)=16(68,64)=235"),
                () -> "every tile must match the ffmpeg reference: " + line);
        assertTrue(line.contains("covered=1024 frame=1024"),
                () -> "the four tiles must cover the whole frame: " + line);
    }

    /**
     * UNEQUAL tiles: 192x64 — three superblocks across, one down — encoded with
     * {@code -tiles 2x1}. The encoder spaces uniform tiles by
     * {@code ceil(sbCols / tileCols)} = 2 superblocks wide, so the FIRST tile is
     * 128 px and the LAST gets the remaining ONE superblock (64 px) — a tile
     * whose mi rectangle ends past the frame's own extent and is CLAMPED to it.
     * ffmpeg's plane gives, at each tile's origin and 4px right of it, the
     * checkerboard's 16 and 235.
     */
    @Test
    void unequalTilesDecodeToReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_mt192.avif", "mt192.avif"));
        assertTrue(line.contains("TILES n=2"), () -> "the fixture must be 2 tiles: " + line);
        assertTrue(line.contains("TILEY t0(0,0)=16(4,0)=235 t1(128,0)=16(132,0)=235"),
                () -> "both tiles must match the ffmpeg reference: " + line);
        assertTrue(line.contains("covered=768 frame=768"),
                () -> "the two tiles must cover the whole frame: " + line);
    }

    /**
     * A GRADIENT whose single 64x64 block OVERHANGS its frame: 64x40 (a
     * chroma-correlated ramp — luma with X, U with X, V with Y), encoded so the
     * block at the bottom edge keeps its full height. It was written to reach
     * CFL at a frame edge and does NOT (measured: `uvCFL=0`, the chroma is
     * DC), so that case is still open — but the fixture EARNED ITS PLACE: it
     * found two REAL crashes of the overhanging class, both fixed in this pass —
     * the coefficient walk slicing the `above`/`left` entropy contexts past the
     * frame (`Index 10 out of bounds for length 10`) and the transform-TYPE map
     * (`Index 160 out of bounds for length 160`) — because libaom PADS those
     * arrays (`ALIGN_POWER_OF_TWO(num_mi_cols, MAX_MIB_SIZE_LOG2)`) and Kof's
     * were frame-sized.
     */
    @Test
    void gradientFrameOverhangingItsSuperblockMatchesReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_grad64x40.avif", "grad.avif"));
        assertTrue(line.contains("blocks=1 covered=160 frame=160 overlap=0 symBits=168 payloadBits=168"),
                () -> "the frame must decode whole: " + line);
        assertTrue(line.contains("Y 00=2 032=128 063=249 320=2 3232=128 3263=249"),
                () -> "must match the ffmpeg reference: " + line);
    }

    /**
     * CFL AT A FRAME EDGE — the case the 112th pass's AC pad exists for, and the
     * fixture that finally REACHES it: 32x24 (one superblock, overhanging by 8
     * rows), a chroma-correlated ramp (luma with X, U with X, V with Y) encoded
     * at {@code -cpu-used 4} so the encoder evaluates CFL. A 64x64 block CANNOT
     * use it at all — {@code is_cfl_allowed} wants both dims &lt;= 32 — which is
     * why the 64x40 gradient of the 114th pass never did; here the single 32x32
     * block is eligible, and measured it takes it: {@code uvCFL=1} with
     * {@code uvMode 13}.
     *
     * <p>It also caught a REAL BUG in the 112th pass's own fix: the pad covered
     * the VALUES, but {@code av1CflSubsample} still READ the luma of the cells
     * outside the plane — past the buffer for this frame
     * ({@code Index 885 out of bounds for length 884}) — so the subsample is now
     * bounded by the within-bounds cells, as libaom's store is.
     */
    @Test
    void cflAtAFrameEdgeMatchesReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_cfl32x24.avif", "cfl.avif"));
        assertTrue(line.contains("uvCFL=1"), () -> "the fixture must reach CFL: " + line);
        // THE ROOT CAUSE OF THE V PLANE'S +/-1 (130th pass, PROVEN): CDEF. The
        // sequence header ENABLES cdef (`ec=1`), the frame header carries ONE
        // configuration (`bits=0`), the luma strengths are ZERO (the luma is
        // copied untouched — which is why it matched ffmpeg exactly) and the
        // chroma primary strength is 14 (`uv=[56]` = 14*4 + 0 secondary) with
        // damping 5. libaom/dav1d filter the chroma with the co-located luma
        // direction; Kof parses these values but does NOT filter. The fetched
        // libaom `cdef_block.c` transcription (`scripts/av1_cdef_ref.py`)
        // applied to Kof's own pre-CDEF V plane reproduces ffmpeg's ENTIRE
        // 12x16 plane exactly, so the decode chain (entropy, dequant, inverse
        // transform, prediction) is EXONERATED end to end and the remaining
        // work is the filter, not a counter bug.
        assertTrue(line.contains("CDEF ec=1 bits=0 damp=5 y=[0] uv=[56]"),
                () -> "the CDEF parameters moved: " + line);
        assertTrue(line.contains("blocks=1 covered=48 frame=48 overlap=0 symBits=128 payloadBits=128"),
                () -> "the frame must decode whole: " + line);
        assertTrue(line.contains("Y 00=3 032=244 063=244 320=3 3232=244 3263=244"),
                () -> "the luma must match the ffmpeg reference: " + line);
        // The CHROMA, which is what CFL actually acts on — the oracle the 115th
        // pass named as missing. U matches ffmpeg EXACTLY (129/159 at every
        // sample). V does NOT: it is 129/160 where ffmpeg has 130/159, a ±1
        // deviation this oracle is the first to see. RECORDED, not asserted as
        // correct: the values below are Kof's, so any change to them fails here
        // and the ±1 stays visible until its root cause is found.
        // The V plane's decoded levels: ONE transform block with three non-zero
        // coefficients. RECORDED (they are Kof's own decode), and they are the
        // half of the ±1 the AC oracle cannot see: the AC is hand-computed EXACT
        // and the prediction is exonerated, so the deviation lives in this
        // residual's dequantization/inverse transform.
        // The DEQUANTIZED coefficients, and they are EXACT: 12*223 = 2676,
        // -5*305 = -1525, -1*305 = -305 — a plain multiply by the quantizer, as
        // the AV1 specifies (dc_q for the DC, ac_q for the rest; the tables carry
        // the scaling). With the AC hand-computed EXACT and the prediction
        // exonerated, the ±1 therefore lives in the 16x16 INVERSE TRANSFORM —
        // which only the V exercises here, because the U's residual is zero.
        // The V transform block's TYPE: DCT_DCT (0). That EXONERATES the 16x16
        // inverse transform as the ±1's source — the same type and size is
        // exercised exactly by the other fixtures' luma — and it is why this
        // pass's scan-order experiment mattered: flipping Kof's generated zigzag
        // to what I read as libaom's table order BROKE this fixture's U (which
        // was exact) and made V worse, so the original direction is right and the
        // scan is CONFIRMED by measurement, not merely assumed.
        // The V's RECONSTRUCTED RESIDUAL (av1ReconstructBlock with a zero
        // prediction): the last link of the chain that had no oracle. RECORDED —
        // these are Kof's values, small and gradient-like, and the next step is to
        // compute the reference 2D inverse transform for the SAME input (the DC
        // 2676 plus the two ACs) from libaom's seven-stage av1_idct16 and compare.
        // The U's residual is DC-ONLY and comes out FLAT at 16 — exactly what the
        // validated reference (scripts/av1_idct16_ref.py) gives for its single
        // coefficient (level 9 x dc_q 223 = 2007). Together with the V below, the
        // decoder's 16x16 inverse transform is PROVEN correct on both the DC-only
        // and the AC case, so the V plane's ±1 lies in the prediction or the add.
        // The CFL alphas: U = 1, V = 0. The V's prediction therefore has NO AC
        // contribution — it is the DC predictor alone — so the V plane's ±1 cannot
        // be the AC. That leaves the DC prediction's VALUE (a per-block constant)
        // and the residual (validated in the 123rd pass) as the only terms, and a
        // DC error would shift the plane uniformly while the observed deviation is
        // a SPREAD — recorded here as the open discrepancy the next pass attacks.
        // The frame header's per-plane quantizer deltas and the quantization-matrix
        // flag — ALL ZERO, which REFUTES the 125th pass's candidate root cause: the
        // dequantization is exonerated (every plane legitimately uses the luma
        // quantizers). The values are exposed now instead of being parsed and
        // discarded, so any non-zero one would be visible.
        // The chroma PREDICTIONS, MEASURED at last (the 127th/128th passes' last
        // unmeasured term): 2 transform blocks, the U's (113, 115 — the CFL result
        // with alpha_U = 1) then the V's (128, 128 — the DC ALONE, alpha_V = 0),
        // which is EXACTLY the value the arithmetic derived. The prediction is
        // therefore exonerated. The V's ±1 was CLOSED in the 130th pass: it is
        // the CDEF filter (see the `CDEF` assertion above), not a rounding or
        // multi-coefficient difference — libaom and libdav1d agree byte for byte
        // on this file, and the transcribed filter reproduces the plane exactly.
        assertTrue(line.contains("CPRED n=2 [113, 115, 128, 128]"),
                () -> "the chroma predictions moved: " + line);
        assertTrue(line.contains("QDELTAS [0, 0, 0, 0, 0, 0]"), () -> "the header deltas moved: " + line);
        assertTrue(line.contains("CFLA 1 0"), () -> "the CFL alphas moved: " + line);
        assertTrue(line.contains("URES (0,0)=16 (0,1)=16 (1,0)=16"),
                () -> "the U residual moved: " + line);
        // The BOTTOM rows too. With these, the arithmetic closes: the decoder's
        // V final (129/160) minus its own residual (1/32) is a CONSTANT 128 — the
        // correct neighbourless DC — so the prediction is NOT the ±1's source;
        // ffmpeg's final (130/159) implies a residual of 2/31, i.e. the decoder
        // and the encoder disagree by ±1 in the RESIDUAL's INPUTS (the levels or
        // the quantizers), not in the prediction.
        assertTrue(line.contains("VRES (0,0)=1 (0,1)=1 (1,0)=2 (1,1)=2 (15,15)=34 (0,11)=32 (8,11)=32"),
                () -> "the V residual moved: " + line);
        // The U's residual is DC-ONLY (one coefficient, 9): its exactness tests the
        // inverse transform's DC scaling and NOTHING of its AC butterflies, which
        // only the V's three coefficients exercise.
        assertTrue(line.contains("ULEV n=1 t0[0]=9"), () -> "the U residual moved: " + line);
        assertTrue(line.contains("VTXT n=1 [0]"), () -> "the V transform type moved: " + line);
        assertTrue(line.contains("DQV dcq=223 acq=305 qIdx=160 t0[0]=2676 t0[16]=-1525 t0[32]=-305"),
                () -> "the dequant moved: " + line);
        assertTrue(line.contains("VLEV n=1 t0[0]=12 t0[16]=-5 t0[32]=-1"),
                () -> "the V residual moved: " + line);
        assertTrue(line.contains("UV 00=129,129 032=159,129 063=159,129 320=129,160 3232=159,160 3263=159,160"),
                () -> "the chroma moved (U must be 129/159, V is RECORDED at 129/160 vs ffmpeg's 130/159): " + line);
        // POST-CDEF: the filter runs on the REAL tile state — the driver's own
        // skip grid and per-64x64 cdef configuration select the strengths, and
        // the luma direction drives the taps. The V plane must become ffmpeg's
        // ENTIRE 12x16 plane (130,131,133,...,159), closing the pre-CDEF
        // `UV` record above without changing it.
        // The COMPLETE post-CDEF frame, all three planes, through the tile path:
        // the luma (strength 0 -> copied), the U (invariant) and the V (the
        // filter's visible effect). The expected planes come from the SAME
        // binary fixture that feeds the decoder: its luma/U are Kof's pre-CDEF
        // planes (which libaom equals) and its last plane is ffmpeg's post-CDEF
        // V (which libaom and libdav1d produce byte for byte).
        assertTrue(line.contains(postCdefPlane("YROWF", 0, 32, 24)),
                () -> "the post-CDEF luma moved: " + line);
        assertTrue(line.contains(postCdefPlane("UROWF", 768, 16, 12)),
                () -> "the post-CDEF U plane moved: " + line);
        assertTrue(line.contains(postCdefPlane("VROWF", 1152, 16, 12)),
                () -> "the post-CDEF V plane moved: " + line);
    }

    /** One post-CDEF plane's golden rows, read from the binary fixture. */
    private static String postCdefPlane(String tag, int offset, int w, int h) throws Exception {
        var bytes = Files.readAllBytes(Path.of("src/test/resources", "av1_cdef_cfl.bin"));
        var sb = new StringBuilder();
        for (var r = 0; r < h; r++) {
            sb.append(tag).append(' ').append(r);
            for (var c = 0; c < w; c++) {
                sb.append(' ').append(bytes[offset + r * w + c] & 0xff);
            }
            if (r + 1 < h) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** The fixture with a temporal delimiter prepended (the canonical OBU order). */
    private Path canonical(String resource, String name) throws Exception {
        byte[] raw = Files.readAllBytes(Path.of("src/test/resources", resource));
        int m = -1;
        for (int i = 0; i + 4 <= raw.length; i++) {
            if (raw[i] == 'm' && raw[i + 1] == 'd' && raw[i + 2] == 'a' && raw[i + 3] == 't') {
                m = i;
                break;
            }
        }
        int size = ((raw[m - 4] & 0xff) << 24) | ((raw[m - 3] & 0xff) << 16)
                | ((raw[m - 2] & 0xff) << 8) | (raw[m - 1] & 0xff);
        byte[] item = new byte[size - 8];
        System.arraycopy(raw, m + 4, item, 0, item.length);
        byte[] delim = AvifSeqSupport.delimiter();
        byte[] both = new byte[delim.length + item.length];
        System.arraycopy(delim, 0, both, 0, delim.length);
        System.arraycopy(item, 0, both, delim.length, item.length);
        Path out = tmp.resolve(name);
        Files.write(out, AvifObuSupport.containerWithItem(both));
        return out;
    }

    private String runKof(Path fixture) throws Exception {
        String code = AvifRealTileProbe.probe(fixture.toString());
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        installLib(root);
        CompilationResult result = compiler.compile(source, out, Target.JVM);
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private void installLib(Path root) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
    }
}
