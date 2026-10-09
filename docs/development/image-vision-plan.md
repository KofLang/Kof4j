[English](image-vision-plan.md) | [Português](image-vision-plan.pt_BR.md)

# Strategic plan — Kof Image & Vision

**Owner:** `192.168.15.21:9092` (lane pipeline/image-vision — ONE plan, ONE owner per `D-PLAN-ONE-OWNER`). ⚠️ AVIF slices 2a–2g (01–02/10) were written by `192.168.15.101:9092`, which owns `memory-safety-plan` — a `D-PLAN-ONE-OWNER` violation recorded 02/10; the owner lane MUST re-verify that state at tip (re-review order, DOING 02/10).
> **AVIF slice 3u LANDED 08/10 (pure Kof, AV1 INTRA PREDICTION COMPOSITION — `libs/image/Av1IntraBlock.kf`, new):** the `predict_intra` process of AV1 §7.11.2 that the tile decode drives per transform block. `av1IntraPredictBlock( frame, stride, x, y, w, h, maxX, maxY, haveLeft, haveAbove, haveAboveRight, haveBelowLeft, mode, angleDelta, useFilterIntra, filterIntraMode, bitDepth, filterType, enableEdgeFilter )` builds `AboveRow[0..w+h-1]`/`LeftCol[0..w+h-1]` from the already-reconstructed plane (the availability flags, the `haveAboveRight`/`haveBelowLeft` `Min(maxX, x + (haveAboveRight ? 2*w : w) - 1)` / `Min(maxY, y + (haveBelowLeft ? 2*h : h) - 1)` read limits, the missing-edge substitutions `(1<<(BitDepth-1))-1` / `+1` and the corner `128`), then dispatches to the slice-3j base predictors, the slice-3k directional process (`av1DrPredict`, edge-filter guarded by `enableEdgeFilter`) or the slice-3l recursive filter-intra process. The new `av1FilterIntraPredBd(..., bitDepth)` gives the filter-intra process the `Clip1` bound `(1<<bitDepth)-1` (the 8-bit `av1FilterIntraPred` now delegates to it). **Two landed-slice bugs caught and fixed here** (the composition exercises edge availability and both edge-filter faces that slice 3k never did): (1) `av1DrPredict` derived `needAbove`/`needLeft` from the MODE (`extend_modes`), but libaom and §7.11.2.4 derive them from `pAngle` (`need_above = pAngle < 180`, `need_left = pAngle > 90`) — the mode-based reading diverged for `V_PRED` with `pAngle > 90` and `H_PRED` with `pAngle < 180` (121/2128 slice-3k cases); (2) the intra-edge UPSAMPLE step ran outside the `enable_intra_edge_filter` guard, but libaom/spec gate it INSIDE that guard. Both fixed in `libs/image/Av1IntraDr.kf`; the slice-3k golden was regenerated from the faithful libaom harness (`SHA256 77c7ec46…`, was `9536c4d7…`) and `Av1IntraDrE2ETest` re-passed **6/6**. **Oracle = a second, independent source:** the REAL libaom build path `build_non_directional_intra_predictors` + `build_directional_and_filter_intra_predictors` from `av1/common/reconintra.c` (the same kernels slices 3j/3k/3l pinned) driven exactly as `av1_predict_intra_block` dispatches them, over 4 transform sizes × 13 base/directional modes × the recursive filter-intra mode × 2 bit depths (8/12) × 4 availability combinations (incl. above-right/below-left) × both `enable_intra_edge_filter` faces (896 blocks, 7,168 lines) with byte-identical frame inputs; the Kof probe reproduces every sample with zero mismatches, pinned gzip+base64 (`SHA256 9d446979…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1IntraBlock' not found`, measured); post-slice `Av1IntraBlockE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS, non-regression `Av1IntraDrE2ETest`+`Av1FilterIntraE2ETest`+`Av1IntraE2ETest` **18/18**. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3t LANDED 07/10 (pure Kof, AV1 TRANSFORM-TYPE SELECTION — `libs/image/Av1TxType.kf`, new):** the `transform_type` syntax `decode_block` runs after the slice-3s transform-size read (§5.11.31). `Av1TxType.readTxType( txSz, reducedTxSet, qindex, intraMode, useFilterIntra, filterIntraMode, txTypes, stride, x4, y4 )` forces `DCT_DCT` when `qindex == 0` (coded lossless) or the transform set is `DCTONLY` (`av1TxSet` returns 0), and otherwise reads the `@@intra_tx_type` symbol from the `Default_Intra_Tx_Type_Set1/Set2_Cdf` row selected by the transform set (`av1TxSet`, §5.9.2) and the square transform size, at the intra mode's column (a filter-intra mode resolves through `fimode_to_intradir`), inverts it with `Tx_Type_Intra_Inv_Set1/Set2` and stamps the block's 4x4 units in the frame `TxTypes` map (chroma then derives its type via `compute_tx_type`, slice 3b). The store holds persistent adapting CDF copies (`av1IntraTxTypeSet1Data`/`Set2Data`, 65 rows: `Default_Intra_Tx_Type_Set1_Cdf[2][INTRA_MODES][8]` + `Default_Intra_Tx_Type_Set2_Cdf[3][INTRA_MODES][6]`, cross-checked value-for-value against libaom `intra_ext_tx_cdf`, zero differences). The inter `@@inter_tx_type` path is not reachable in an intra-only frame and is not covered. **Oracle = libaom's real range ENCODER:** the verbatim `aom_dsp/entenc.c` `aom_writer` (`allow_update_cdf`) emits a tile byte stream driving libaom's own transform-set selection (`av1_get_ext_tx_set_type`/`get_ext_tx_set`/`av1_num_ext_tx_set`/`av1_ext_tx_ind`/`av1_ext_tx_inv`) over seven cases (every transform size, both `reduced_tx_set` values, the coded-lossless gate, filter-intra mode resolution, all intra modes); the Kof reader reproduces all 424 decisions with zero mismatches, pinned gzip+base64 (`SHA256 fd4d5cb9…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1TxType' not found`, measured); post-slice `Av1TxTypeE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS, non-regression `Av1TxSizeE2ETest`+`Av1ModeTailE2ETest`+`Av1ModeBodyE2ETest` **18/18**. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3s LANDED 07/10 (pure Kof, AV1 TRANSFORM-SIZE SELECTION — `libs/image/Av1TxSize.kf`, new):** the `read_tx_size`/`read_selected_tx_size` stage `decode_block` runs after the mode info (§5.11.31). `Av1TxSize.readTxSize( r, c, bSize, skip, txMode, isInter, lossless )` fixes `TX_4X4` when Lossless, otherwise, for a block larger than BLOCK_4X4, reads the `@@tx_depth` symbol from the slice-3m `Default_Tx_Size_Cdf` row `cat*3 + ctx` (`cat = bsize_to_tx_size_cat`, `maxDepth+1` symbols) when `txMode == TX_MODE_SELECT` and the transform is selectable, and otherwise derives it from the mode (`av1TxSizeFromTxMode`, `ONLY_4X4`/`TX_MODE_LARGEST`); BLOCK_4X4 always takes the rectangular max (`TX_4X4`). `readSelectedTxSize` applies `av1DepthToTxSize` (`Split_Tx_Size` `depth` times) and the neighbour context is `get_tx_size_context` over the per-4x4 transform map (`av1TxSizeSqr`/`tx_size_wide/high`), maintained by `setTxfmCtxs` (`set_txfm_ctxs`). New tables quoted from the spec/libaom: `Split_Tx_Size`, `bsize_to_max_depth`, `bsize_to_tx_size_depth`. **Oracle = libaom's real range ENCODER:** the verbatim `aom_dsp/entenc.c` `aom_writer` (`allow_update_cdf`) emits a tile byte stream driving libaom's own `tx_size_cdf` selection (`bsize_to_tx_size_cat` + `get_tx_size_context`) over seven cases (TX_MODE_SELECT/LARGEST/ONLY_4X4, the lossless shortcut, the inter allow-select gate, 64x64/16x16/8x8 walks); the Kof reader reproduces all 1359 decisions with zero mismatches, pinned gzip+base64 (`SHA256 08e39e72…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1TxSize' not found`, measured); post-slice `Av1TxSizeE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. The inter `read_block_tx_size` var-tx tree is not reachable in a still intra frame and is not covered here. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3r LANDED 07/10 (pure Kof, AV1 INTRA MODE-INFO TAIL — CDEF index + quantizer delta + loop-filter deltas — `libs/image/Av1ModeInfo.kf`):** the tail of `intra_frame_mode_info` after the 3q body, in spec order (§5.11.8 `:2081-2083`). `Av1ModeInfo.readCdef( r, c, bSize, skip, cfg )` reads the `@@cdef_idx` literal (`cdef_bits` wide) from the FIRST non-skip block touching each 64x64 CDEF unit and broadcasts it over the unit (`clear_cdef`/`read_cdef` `:4158`/`:4175`); it returns without reading when `skip`/`CodedLossless`/`!enable_cdef` (the `allow_intrabc` gate folds into the same config). `Av1ModeInfo.readDeltaQ( r, c, bSize, skip, cfg )` reads `@@delta_q_abs` (§5.11.8 `read_delta_qindex` `:2240`; `DELTA_Q_SMALL=3` escape: `L(3)` rem + `L(rem+1)` bits; sign `L(1)`), returning the reduced delta shifted by `delta_q_res`. `Av1ModeInfo.readDeltaLf( r, c, bSize, skip, cur, cfg )` reads one level per `frameLfCount` entry (`1`, or `4` with `delta_lf_multi` and >1 plane, `2` for monochrome) with the `DELTA_LF_SMALL=3` escape, accumulating into `cur` and clamping to `[-MAX_LOOP_FILTER, +MAX_LOOP_FILTER]` (`±63`). All three share the libaom superblock-first-block gate (`mi_row/mi_col & (mib_size-1) == 0`) and the spec `MiSize == sbSize && skip` early return; the new `record Av1ModeTail( sbSize, cdefBits, enableCdef, codedLossless, deltaQPresent, deltaQRes, deltaLfPresent, deltaLfMulti, deltaLfRes, numPlanes )` carries the frame-level state (`cdefBits` from `AvifFrameHeader`), and the reader keeps persistent adapting copies of the slice-3m `deltaQ()`/`deltaLf()` defaults. **Oracle = libaom's real range ENCODER:** the verbatim `aom_dsp/entenc.c` `aom_writer` (`allow_update_cdf`) emits a tile byte stream driving libaom's own `read_cdef`/`read_delta_qindex`/`read_delta_lflevel` over the default `delta_q_cdf`/`delta_lf_cdf`/`delta_lf_multi_cdf`; seven cases cover 64x64 and 128x128 superblocks, CDEF on/off, the coded-lossless guard, the `ReadDeltas` gate, `delta_lf_multi` with 1 and 3 planes, and sub-block walks inside a superblock; the Kof reader reproduces all 1227 decisions with zero mismatches, pinned gzip+base64 (`SHA256 93fcdc9a…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`SEM025 Cannot resolve method 'readCdef' on type 'Av1ModeInfo'`, measured); post-slice `Av1ModeTailE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS, non-regression `Av1ModeInfoE2ETest`+`Av1ModeBodyE2ETest` **12/12**. This slice also enforces the 3q/3r `GOLDEN_SHA256` pin inside `golden()` (the 3q constant had drifted from its resource; corrected to the measured `cef3c09d…`). No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3q LANDED 07/10 (pure Kof, AV1 INTRA MODE-INFO BODY — Y/UV mode + angle + CFL + filter-intra — `libs/image/Av1ModeInfo.kf`):** the non-palette body of `intra_frame_mode_info` after the 3p prefix. `Av1ModeInfo.readBody( r, c, bSize, lossless, hasChroma, enableFilterIntra, palettePossible )` reads the intra Y mode (§9.4.2 `intra_frame_y_mode`, the spec `Intra_Mode_Context` neighbour context over `YModes`), the Y angle delta (§5.11.10.2 `intra_angle_info_y`, `is_directional_mode` and `MiSize >= BLOCK_8X8`), the UV mode (the CFL-allowed set selection per §9.4.2: lossless BLOCK_4X4, or non-lossless with max block dimension <= 32), the CFL sign/alpha symbols (§5.11.10.3 `read_cfl_alphas`) when the UV mode is `UV_CFL_PRED`, the UV angle delta, and `filter_intra_mode_info` (Y `DC_PRED` and max block dimension <= 32), in spec order; it keeps persistent adapting CDF stores (the slice-3m accessors return fresh copies) and returns `[ yMode, angleDeltaY, uvMode, angleDeltaUV, cflAlphaU, cflAlphaV, useFilterIntra, filterIntraMode ]`. Palette is not covered yet: `palettePossible == 1` refuses by name (`IMAGE: av1 palette mode not covered`), never a silent skip. **Oracle = libaom's real range ENCODER:** the verbatim `aom_dsp/entenc.c` `aom_writer` (`allow_update_cdf`) encodes six mode-info body sequences over a block grid (both the CFL-allowed and CFL-not-allowed UV sets — lossless BLOCK_4X4, non-lossless up to 32, and 64x64 — monochrome, and the filter-intra guard) while driving libaom's own `intra_mode_context` and the default CDFs; the Kof reader reproduces all 884 decisions with zero mismatches, pinned gzip+base64 (`SHA256 cef3c09d…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`SEM025 Cannot resolve method 'readBody' on type 'Av1ModeInfo'`, measured); post-slice `Av1ModeBodyE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3p LANDED 06/10 (pure Kof, AV1 INTRA MODE-INFO PREFIX — segment id + skip — `libs/image/Av1ModeInfo.kf`, new):** the first two syntax elements `intra_frame_mode_info` reads after the partition walk of slice 3o. `class Av1ModeInfo` reads the segment id (§5.11.8 `read_segment_id`: the spatial predictor/context of libaom's `av1_get_spatial_seg_pred` over the 4x4 segment map, the `@@segment_id` symbol with the slice-3m segment CDFs, then the `neg_deinterleave` postprocess against `last_active_segid`) and the skip flag (§5.11.11 `read_skip`: the spec neighbour context `Skips[MiRow-1][MiCol] + Skips[MiRow][MiCol-1]`, the `SEG_LVL_SKIP` shortcut when `SegIdPreSkip`), in the spec order, and returns `[ skip, segment_id ]`. Slice 3m gains the two missing tables (`Default_Segment_Id_Cdf` 3x9, `Default_Segment_Id_Predicted_Cdf` 3x3) as `Av1ModeCdf.segmentId(ctx)`/`segmentIdPred(ctx)`. **Oracle = libaom's real range ENCODER:** the verbatim `aom_dsp/entenc.c` `aom_writer` (`allow_update_cdf`) encodes a scripted `intra_frame_mode_info` prefix sequence over five frame shapes (both `SegIdPreSkip` orders, two `last_active_segid` values) while driving libaom's own `av1_get_spatial_seg_pred`/`av1_neg_deinterleave` and the spec skip context over the default segment/skip CDFs; the Kof reader reproduces all 1951 decisions with zero mismatches, pinned gzip+base64 (`SHA256 3724ad52…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1ModeInfo' not found`, measured); post-slice `Av1ModeInfoE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3o LANDED 06/10 (pure Kof, AV1 PARTITION TREE WALK — `libs/image/Av1Partition.kf`, new):** the recursive `decode_partition( r, c, bSize )` of AV1 §5.11.4/§6.10.4, the first stage that CONSUMES the entropy tables of slices 3a/3m/3n. Given a tile's bytes and the frame size in 4x4 units, `class Av1Partition` reads the partition symbol of every square block with the slice-3a `Av1Symbol` decoder and the slice-3m default partition CDFs, keeps libaom's `partition_plane_context` neighbour state (the `above`/`left` `partition_context_lookup` bytes, `bsl = Mi_Width_Log2[bSize] - 1`, row index `(left*2 + above) + bsl*4`), adapts the full partition CDF in place per context, and reads the derived 2-symbol `split_or_horz`/`split_or_vert` CDFs (slice 3n) with `read_cdf` — NOT adapted — when only one neighbour exists, then recurses through `Partition_Subsize` (slice 3n) and applies libaom's `update_ext_partition_context` AFTER the recursion, exactly as the spec orders it. Each step is emitted as `P r c bSize partition` (a `@@partition` read) or `B r c subSize` (a `decode_block` call) in walk order. **Oracle = libaom's real range ENCODER:** the verbatim `aom_dsp/entenc.c` `aom_writer` (with `allow_update_cdf`) encodes four frame shapes (6x5, 24x20, 33x21, 16x40 MI, different superblock grids and edge clipping) while driving libaom's own `partition_plane_context`/`update_ext_partition_context`/`partition_context_lookup` and the `partition_gather_*_alike` helpers; the Kof walk reproduces all 53 decisions with zero mismatches, pinned gzip+base64 (`SHA256 33b0ee9e…`). **Corrected in this slice:** the 3n gathered-CDF helpers were swapped (spec `split_or_horz` = libaom `partition_gather_vert_alike` and vice versa) and the 3n oracle repeated the same transposed reading — a shared blind spot; both the module and the 3n golden are corrected here, and this slice's oracle exercises the fixed helpers end-to-end. **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1Partition' not found`, measured); post-slice `Av1PartitionE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3n LANDED 06/10 (pure Kof, AV1 BLOCK-SIZE & PARTITION DESCRIPTOR — `libs/image/Av1Block.kf`, new):** the conversion tables the partition walk and the intra mode-info reader index by block size, plus the partition-symbol CDF selection the recursive `decode_partition` performs. Per AV1 §9.3 (conversion tables) and §10 (additional tables) the module carries `Num_4x4_Blocks_Wide`/`Num_4x4_Blocks_High`, `Block_Width`/`Block_Height` (= 4× the MI counts), `Size_Group`, `Num_Pels_Log2`, `Mi_Width_Log2`/`Mi_Height_Log2`, `Max_Tx_Size_Rect` and `Max_Tx_Size`, `Partition_Subsize[10][22]` (with the spec `BLOCK_INVALID` sentinel 22) and the partition-symbol helpers `av1PartitionCtx` (`bsl*4 + left*2 + above`, `PARTITION_PLOFFSET=4`), `av1PartitionCdfLength` (4/8/10) and the derived 2-symbol `av1PartitionGatherHorzAlike`/`av1PartitionGatherVertAlike` (`cdf[0] = (1<<15) - psum`, `cdf[1] = 1<<15`, `cdf[2] = 0`, exactly as `decode_partition` needs for `@@split_or_horz`/`@@split_or_vert`). **Oracle = two independent sources:** all block tables cross-checked value-for-value against libaom `av1/common/common_data.h` (`block_size_wide/high`, `mi_size_wide/high`, `mi_size_wide/high_log2`, `size_group_lookup`, `num_pels_log2_lookup`, `max_txsize_rect_lookup`, `subsize_lookup`) AND against the spec §9.3/§10 tables — 484 values, zero differences (the only divergence is the four unreachable 8X8 extended-partition `Partition_Subsize` entries, where libaom stores INVALID and the spec lists a value; the spec is authoritative); the gathered CDFs are the verbatim libaom `partition_gather_horz_alike`/`partition_gather_vert_alike` (MIRROR-named relative to the spec: spec `split_or_horz` = libaom `partition_gather_vert_alike`, spec `split_or_vert` = libaom `partition_gather_horz_alike`; the first landing had them swapped because module and oracle shared the transposed reading — corrected 06/10) + `cdf_element_prob` over libaom's `default_partition_cdf` normalised to the spec forward shape, reproduced for all four non-8X8 block sizes × four contexts. **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1Block' not found`, measured); post-slice `Av1BlockE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3m LANDED 06/10 (pure Kof, AV1 DEFAULT MODE-INFO CDFs — `libs/image/Av1ModeCdf.kf`, new):** the non-coefficient entropy tables the tile block syntax reads, per AV1 §10 (additional tables): the partition tree (`Default_Partition_W8/W16/W32/W64/W128_Cdf`), the intra frame Y mode (`Default_Intra_Frame_Y_Mode_Cdf`, 5×5), the intra Y mode (`Default_Y_Mode_Cdf`, 4 size groups), the UV mode (CFL-not-allowed/allowed), angle delta, filter-intra mode and use, the skip / tx-size / delta-q / delta-lf symbols, the CFL sign/alpha and the transform-partition split (`Default_Txfm_Split_Cdf`). `class Av1ModeCdf` embeds every table as a comma-separated string parsed with `split`/`toInt` (pure Kof, all targets) and exposes flat-slice accessors (`partitionW(bsl, ctx)`, `kfY(aboveCtx, leftCtx)`, `ifY(sizeGroup)`, `uv(cflAllowed, mode)`, `angle(mode)`, `filterMode()`, `filterIntra(bs)`, `skip(ctx)`, `intrabc()`, `deltaQ()`, `deltaLf()`, `txSize(cat, ctx)`, `cflSign()`, `cflAlpha(ctx)`, `txfmSplit(ctx)`) returning fresh copies so the tile walk can adapt them in place without mutating the defaults. **Oracle = a second, independent source:** all 15 tables were cross-checked value-for-value against libaom's `av1/common/entropymode.c` default CDF arrays (the `AOM_CDFn` macros expanded to the spec shape `[symbols…, 32768, 0]`) — 1,348 numbers, zero differences; the test then requires the Kof probe to reproduce that libaom-derived golden for every row. **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1ModeCdf' not found`, measured); post-slice `Av1ModeCdfE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3l LANDED 06/10 (pure Kof, AV1 RECURSIVE FILTER-INTRA PREDICTION — `libs/image/Av1FilterIntra.kf`, new):** the `use_filter_intra` face of §7.11.2 that the base (slice 3j) and directional (slice 3k) prediction do not cover, per AV1 §7.11.2.3 (recursive intra prediction process). For each 4x2 block the process gathers seven neighbouring samples p[0..6] (the above row left of the block, the corner, the left column) and filters them with `Intra_Filter_Taps[filter_intra_mode][(i1<<2)+j1][i]` (`FILTER_INTRA_MODES = 5`, `8` outputs per block, `7` taps), writing `Clip1( Round2Signed( pr, INTRA_FILTER_SCALE_BITS ) )` with `INTRA_FILTER_SCALE_BITS = 4`. The module carries the whole tap table quoted verbatim from the spec additional tables and builds the serial block in a local `(h+1) x (w+1)` buffer (`av1FilterIntraTap(mode,k,tap)`, `av1FilterIntraPred(above, left, w, h, co, mode)`); valid for `w <= 32` and `h <= 32` (the libaom assertion). **Oracle = a second, independent source:** the REAL libaom `av1_filter_intra_predictor_c` and its `av1_filter_intra_taps` table from `av1/common/reconintra.c` (extracted verbatim by `gen_filter_oracle.py`) over every transform size the process admits and all five `filter_intra_mode` values (85 blocks, 1,090 lines) with byte-identical edge inputs; the Kof probe reproduces every sample with zero mismatches, and the golden is pinned gzip+base64 (`SHA256 0f5aae47…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1FilterIntra' not found`, measured); post-slice `Av1FilterIntraE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3k LANDED 06/10 (pure Kof, AV1 DIRECTIONAL INTRA PREDICTION + INTRA EDGE FILTERING — `libs/image/Av1IntraDr.kf`, new):** the stage that completes the §7.11.2 intra prediction started by slice 3j, per AV1 §7.11.2.4 (directional intra prediction process) and the edge processes it invokes: §7.11.2.7 (filter corner), §7.11.2.9 (edge filter strength selection), §7.11.2.10 (edge upsample selection), §7.11.2.11 (upsample) and §7.11.2.12 (edge filter). The module carries `Mode_To_Angle` (the eight angular modes 1..8 → 90/180/45/135/113/157/203/67), `av1IntraPAngle` (`pAngle = Mode_To_Angle[mode] + angleDelta * ANGLE_STEP`), the `Dr_Intra_Derivative[90]` table quoted verbatim, `av1DrGetDx`/`av1DrGetDy`, the three zone kernels `av1DrPredZ1`/`Z2`/`Z3` (the `(val+16)>>5` interpolation with the `upsample`/`fracBits` shifts and the `maxBaseX`/`maxBaseY` clamp), `av1DrFilterCorner` (`Round2(LeftCol[0]*5 + AboveRow[-1]*6 + AboveRow[0]*5, 4)`), `av1DrEdgeFilterStrength`/`av1DrUseUpsample` (both `intra_edge_filter_type` faces), `av1DrFilterEdge` (the 3×5 kernels `{0,4,8,4,0}`/`{0,5,6,5,0}`/`{2,4,4,4,2}` with `(s+8)>>4` and the spec's `k` clamp) and `av1DrUpsampleEdge` (`-in[i] + 9*in[i+1] + 9*in[i+2] - in[i+3]`, `(s+8)>>4`, `Clip1`), with `av1DrPredict(mode, angleDelta, above, left, w, h, co, nTopPx, nLeftPx, bitDepth, filterType)` composing them exactly as libaom `build_directional_and_filter_intra_predictors` does (`need_above`/`need_left` from `extend_modes`, the corner when `w+h >= 24`, the `+1`/`+h`/`+w` `n_px`, upsample only on the needed edge). The recursive filter-intra mode remains a later slice; the arithmetic is target-independent (no I/O). **Oracle = a second, independent source:** the REAL libaom kernels from `av1/common/reconintra.c` (extracted verbatim by `gen_dr_oracle.py` — `av1_dr_prediction_z1/z2/z3_c`, `av1_filter_intra_edge_c`, `filter_intra_edge_corner`, `intra_edge_filter_strength`, `av1_use_intra_edge_upsample`, `av1_upsample_intra_edge_c` and the `dr_intra_derivative`/`av1_get_dx`/`av1_get_dy` helpers) driven exactly as the libaom build function does, over every transform size (square + rectangular), the eight angular modes, all seven in-range `angleDelta` values and both left-edge configurations (2,128 blocks, 51,856 lines) with byte-identical edge inputs; the Kof probe reproduces every sample with zero mismatches, and the golden is pinned gzip+base64 (`SHA256 9536c4d7…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1IntraDr' not found`, measured); post-slice `Av1IntraDrE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3j LANDED 05/10 (pure Kof, AV1 BASE INTRA PREDICTION — `libs/image/Av1Intra.kf`, new):** the stage that forms the prediction the reconstruction (slice 3i) adds the residual to, per AV1 §7.11.2 (intra prediction process). This slice covers the non-directional predictors: `av1IntraDcPred` (§7.11.2.5 — the `haveLeft && haveAbove` average `avg = (sum + ((w+h)>>1))/(w+h)` over `LeftCol`+`AboveRow`, the `leftAvg`/`aboveAvg` forms, and the `1 << (BitDepth-1)` constant when neither edge exists), `av1IntraV`/`av1IntraH` (the angle-90/180 degenerate cases), `av1IntraPaethPred` (§7.11.2.2 — `base = AboveRow[j] + LeftCol[i] - AboveRow[-1]`, the three `Abs` distances and the `pLeft <= pTop && pLeft <= pTopLeft` / `pTop <= pTopLeft` tie-break), and `av1IntraSmoothPred` (§7.11.2.6 — SMOOTH `Round2(smWeightsY[i]*AboveRow[j] + (256-smWeightsY[i])*LeftCol[h-1] + smWeightsX[j]*LeftCol[i] + (256-smWeightsX[j])*AboveRow[w-1], 9)`, SMOOTH_V with `smWeightsY` and `Round2(·,8)`, SMOOTH_H with `smWeightsX`), with the five `Sm_Weights_Tx_{4x4,8x8,16x16,32x32,64x64}` tables quoted verbatim from the spec. `av1IntraPred(mode, above, left, w, h, haveAbove, haveLeft, topLeft, bitDepth)` dispatches `DC_PRED`(0)/`V_PRED`(1)/`H_PRED`(2)/`SMOOTH_*`(9/10/11)/`PAETH_PRED`(12) and refuses a directional mode (3..8) with an explicit `IMAGE:` diagnostic (the directional edge filter/upsample is slice 3k), never a silent wrong prediction. **Oracle = a second, independent source:** the REAL libaom kernels (`aom_dsp/intrapred.c` compiled from source — `aom_{v,h,smooth,smooth_v,smooth_h,paeth,dc,dc_128,dc_top,dc_left}_predictor_WxH_c` and their `aom_highbd_*` variants) over every transform size (square + rectangular), all three bit depths (8/10/12) and all four `haveAbove`/`haveLeft` combinations (1,596 blocks, 38,892 lines) with byte-identical edge inputs (the spec's missing-edge substitutions applied before prediction); the Kof probe reproduces every sample with zero mismatches, and the golden is pinned gzip+base64 (`SHA256 1db200be…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1Intra' not found`, measured); post-slice `Av1IntraE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3i LANDED 05/10 (pure Kof, AV1 RECONSTRUCTION — `libs/image/Av1Recon.kf`, new):** the stage that turns the dequantized transform block produced by slice 3f back into reconstructed samples, per AV1 §7.11.4 (reconstruct process), steps 2 and 3. Step 2 is the 2D inverse transform (`av1InvTx2d`, slice 3g); this module carries step 3, the residual-to-prediction sum with the FLIPADST flips and `Clip1`: `av1ReconFlipUd` (1 for `FLIPADST_DCT`=4, `FLIPADST_ADST`=8, `V_FLIPADST`=14, `FLIPADST_FLIPADST`=6), `av1ReconFlipLr` (1 for `DCT_FLIPADST`=5, `ADST_FLIPADST`=7, `H_FLIPADST`=15, `FLIPADST_FLIPADST`=6), `av1ReconClip1` = `Clip3(0, (1<<BitDepth)-1, ·)`, `av1ReconAddPred` (for each `(i,j)` it sets `xx = flipLR ? w-j-1 : j`, `yy = flipUD ? h-i-1 : i` and `out[yy][xx] = Clip1(pred[yy][xx] + residual[i][j])` — the flips live in the destination index, per the spec) and `av1Reconstruct` composing `av1InvTx2d` with the sum. The caller composes `av1Reconstruct(av1Dequant(quant, txSz, bitDepth, dcQuant, acQuant), pred, txSz, txType, bitDepth, lossless)`. **Oracle = a second, independent source:** a copy of libaom's `inv_txfm2d_add_c` (`av1/common/av1_inv_txfm2d.c`) with the FLIPADST index flips ENABLED and the residual added to a prediction buffer with `Clip1`, over every valid `(txSz,txType,bitDepth)` combination (the same 579 the inverse-transform slice admits) with a deterministic prediction ramp so the `Clip1` boundary is crossed; the Kof probe reproduces every sample with zero mismatches, and the golden is pinned gzip+base64 (`SHA256 f7a43da4…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1Recon' not found`, measured); post-slice `Av1ReconE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3h LANDED 05/10 (pure Kof, AV1 LOOP FILTER — `libs/image/Av1Lf.kf`, new):** the deblocking stage that follows the inverse transform, per AV1 §7.14 (loop filter process). The module carries the target-independent arithmetic the §7.14.2 edge walk drives: `av1LfMaxLoopFilter` (`MAX_LOOP_FILTER` = 63), `av1LfIndex`/`av1LfSegFeature` (`i = (plane==0) ? pass : plane+1`, `feature = SEG_LVL_ALT_LF_Y_V + i`), `av1LfModeType` (libaom `mode_lf_lut`: intra 0..12 → 0, single inter 13..16 → 1,1,0,1 with `GLOBALMV`=15 → 0, compound 17..24 → 1,1,1,1,1,1,0,1 with `GLOBAL_GLOBALMV`=23 → 0); `av1LfFilterSize` (§7.14.3, `Min(Tx_Width/Height[prevTxSz], Tx_Width/Height[txSz])` then `Min(16,·)` luma / `Min(8,·)` chroma); `av1LfStrength` (§7.14.4/§7.14.5: `Clip3(0,63,deltaLF+loop_filter_level[i])`, segment `FeatureData` add, the `loop_filter_delta_enabled` `nShift = lvlSeg>>5` `ref_deltas`/`mode_deltas` scaling, then `shift` from `loop_filter_sharpness`, `limit` = `Clip3(1,9-sharpness,·)` or `Max(1,·)`, `blimit = 2*(lvl+2)+limit`, `thresh = lvl>>4`); `av1LfMask` (§7.14.6.2 `hevMask`/`filterMask`/`flatMask`/`flatMask2` with `threshBd`/`limitBd`/`blimitBd` = `<<(BitDepth-8)` and `thresholdBd = 1<<(BitDepth-8)`); `av1LfNarrow` (§7.14.6.3, `filter4` arithmetic on `-0x80<<(BitDepth-8)`-shifted samples, `filter1/filter2` rounding, outer taps only when `hevMask==0`) and `av1LfWide` (§7.14.6.4, `log2Size` 3/4 with `n`=3/6 luma or 2 chroma and `n2`=0/1); `av1LfSample` dispatches exactly as §7.14.6 (no filter / narrow / wide-8 / wide-16). **Oracle = a second, independent source:** the §7.14.6 sample filtering runs the REAL libaom kernels (`aom_lpf_{horizontal,vertical}_{4,6,8,14}_c` and their `aom_highbd_*` variants from `aom_dsp/loopfilter.c`, including the actual `filter4`/`filter6`/`filter8`/`filter14` taps) over 240 cases (8 seeds × bit depths 8/10/12 × luma/chroma × filter sizes 4/8/16 × both edge directions, each filtering four consecutive boundaries like the kernel's inner loop); the §7.14.3 size (1444 cases), §7.14.4/§7.14.5 strength (300 cases, including the clamp and `nShift` boundaries) and the `mode_lf_lut` (25 modes) are reproduced from libaom's `av1/common/av1_loopfilter.c` (`update_sharpness`, `get_filter_level`) and its `mode_lf_lut[]`. The Kof probe reproduces every line with zero mismatches, and the golden is pinned gzip+base64 (`SHA256 65ac3261…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1Lf' not found`, measured); post-slice `Av1LfE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until reconstruction closes the chain.
> **TIFF decode LANDED 04/10 (pure Kof, all targets — `libs/image/Tiff.kf`, new; `decodeRaster` dispatches `fmt == "TIFF"`).** The plan's "progressively support common formats: … TIFF" had only metadata; `decodeRaster` refused TIFF. This slice adds Baseline TIFF 6.0 decode: both byte orders (`II`/`MM`), 8-bit samples, chunky layout, grayscale (WhiteIsZero/BlackIsZero) / RGB / RGBA / gray-alpha / palette, one or many strips, Compression 1 (none) and 32773 (PackBits). The classic IFD walk reads tags 256/257/258/259/262/273/277/278/279/284/317/320/338; **TIFF 6.0 §2 value rule** — a value whose byte size fits the 4-byte field is read INLINE, only larger arrays live at the offset (`tiffValues`) — a correctness point a first draft missed (it always read the offset, so an inline `BitsPerSample` array desynced). `ExtraSamples` is honoured by VALUE (2 = unassociated alpha is admitted; 1 = associated is refused `IMAGE: TIFF associated alpha not covered`), and `SamplesPerPixel` must match the photometric (`IMAGE: TIFF samples per pixel not covered`). Named refusals: compression, planar, predictor, bits-per-sample, photometric, extra-samples, samples-per-pixel, palette-samples. **Proof RED-first:** pre-fix `tiffDecodesOnJvm` fails `IMAGE: raster decode is not supported for TIFF` (measured by reverting the dispatch); post-fix `TiffDecodeE2ETest` **8/8** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, plus 9 named refusals. Fixtures are hand-built byte-exactly per the spec and cross-validated offline against PIL and Java `ImageIO`; the golden is produced by a second, independent plain-Java TIFF reader (`TiffDecodeFixtures.readFacts`) that agrees fact-for-fact with the Kof library on the pixels AND on every refusal string. No compiler change, no new gap, no decision required. Next: continue the AVIF decode chain (below).
> **AVIF slice 3g LANDED 05/10 (pure Kof, AV1 INVERSE TRANSFORM — `libs/image/Av1InvTx.kf`, new):** the stage that turns the dequantized coefficient block produced by slice 3f back into spatial residual samples, per AV1 §7.13 (inverse transform process) with §7.13.2.1 butterflies. The module carries the `Cos128_Lookup[65]` table quoted verbatim from the spec and builds the whole 1D/2D machinery from it: `av1Brev`, `av1InvRound2`/`av1InvRound2L` (`Round2(x,n)=(x+(1<<(n-1)))>>n`), `av1InvClip3`, the butterfly primitives `av1InvB(a,b,angle,flip)` (`T[a]=Round2(T[a]*cos128(angle)-T[b]*sin128(angle),12)`, `T[b]=Round2(T[a]*sin128(angle)+T[b]*cos128(angle),12)`, swapped when `flip==1`) and `av1InvH(a,b,flag)` (`T[a]=Clip(x+y)`, `T[b]=Clip(x-y)`, reordered when `flag==1`); the inverse DCT (`av1InvDct`, n=2..6, all §7.13.2.3 stages with the spec's `av1InvDctPermute`), the inverse ADST (`av1InvAdst4`/`8`/`16` with the §7.13.2.4 input permutation and the §7.13.2.10 output permutation, dispatched by `av1InvAdst`), the inverse WHT (`av1InvWht`, §7.13.2.9) and the identity transform (`av1InvIdentity`, n=2..5); then `av1InvTx2d(dequant, txSz, txType, bitDepth, lossless)` applies the §7.13.3 2D process: `rowShift = Lossless ? 0 : Transform_Row_Shift[txSz]`, `colShift = Lossless ? 0 : 4`, the `rowClampRange`/`colClampRange` (`BitDepth+8` / `Max(BitDepth+6,16)`), the `Abs(log2W-log2H)==1` pre-scale `T[j]=Round2(T[j]*2896,12)`, the WHT path when lossless, the `Clip3` between the row and column stages, and the final `Round2(T[j],rowShift)` / `Round2(T[i],colShift)` — consuming the dequantized raster with the effective `Min(32,w) × Min(32,h)` stride (`dequant[i*tw+j]`, slice 3f). The row/column transform-type mapping is derived from the spec's `03.symbols.md` name table (`<col>_<row>`): Row DCT = {0,1,4,11}, Row ADST = {2,3,5,6,7,8,13,15}, Col DCT = {0,2,5,10}, Col ADST = {1,3,4,6,7,8,12,14}. The §7.12 step-3 `flipUD`/`flipLR` are NOT applied here — they belong to the residual-to-prediction sum, so `av1InvTx2d` returns the residual and the caller owns the flips. **Oracle = a second, independent source:** a libaom inverse-transform harness (`av1_inv_txfm2d.c` with the local `av1_inv_txfm1d.c`/`av1_txfm.c`) with the loop-filter flips forced off produces the golden for all 579 valid `(txSz,txType,bitDepth)` combinations of the 19 sizes × 16 types × 3 depths (the invalid 1D-kernel combos, e.g. DCT64/ADST64/identity on a 64 axis, are excluded); the Kof probe reproduces every sample with zero mismatches, and the golden is pinned gzip+base64 (`SHA256 4edb7b94…`). **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1InvTx' not found`, measured); post-slice `Av1InvTxE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. No compiler change, no new gap, no decision. `decodeRaster` still refuses AVIF until the loop filter and reconstruction close the chain.
> **AVIF slice 3f LANDED 05/10 (pure Kof, AV1 DEQUANTIZATION — `libs/image/Av1Quant.kf`, new):** the stage that scales the quantized coefficient levels produced by slice 3e back to transform-domain values, per AV1 §7.12 (reconstruction and dequantization) / §7.12.2 (dequantization functions). The module carries `Dc_Qlookup[3][256]` and `Ac_Qlookup[3][256]` quoted verbatim from the spec (the `[3]` axis indexed by `(BitDepth-8)>>1`), `av1DcQ`/`av1AcQ` (`dc_q`/`ac_q`), `av1DqDenom` (the `TX_32X32`/`TX_16X32`/`TX_32X16`/`TX_16X64`/`TX_64X16` → 2 and `TX_64X64`/`TX_32X64`/`TX_64X32` → 4 rule) and `av1Dequant`, the §7.12.2 step-1 loop over the effective `Min(32,w) × Min(32,h)` raster (`q = dcQuant` at `(0,0)` else `acQuant`; `sign * (Abs(dq) & 0xFFFFFF) / dqDenom`; `Clip3(-(1<<(7+BitDepth)), (1<<(7+BitDepth))-1, ·)`). This is the non-qmatrix path (`using_qmatrix == 0`), which is what every AVIF still image and the host `.avif` use. **Oracle = a second, independent source:** all 1536 table numbers were cross-checked value-for-value against libaom's `av1/common/quant_common.c` (`dc_qlookup_QTX`/`_10_QTX`/`_12_QTX` and `ac_qlookup_QTX`/`_10_QTX`/`_12_QTX`; six 256-entry arrays, zero differences); the test then drives 19 dequantized transform blocks (a deterministic quantized-coefficient input, all 19 transform sizes, all three bit depths) through the Kof accessors and requires a second, independent Java reader to reproduce every value. **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1Quant' not found`, measured); post-slice `Av1QuantE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3d LANDED 04/10 (pure Kof, AV1 COEFFICIENT CONTEXT SELECTION — `libs/image/Av1CoeffCtx.kf`, new):** the context functions the tile coefficient walk (`coeffs()`, AV1 §6.4.3/§5.11.39) calls to pick which entropy CDF a symbol uses — `av1GetCoeffBaseCtx` (`get_coeff_base_ctx`, §9.3.3.3, both the `coeff_base` and the `coeff_base_eob` variants), `av1GetCoeffBrCtx` (`get_coeff_br_ctx`, §9.3.3.4), `av1AllZeroCtxY`/`av1AllZeroCtxUV` (the `all_zero` context, §9.3.3.1) and `av1DcSignCtx`/`av1DcSignContribution` (`dc_sign`, §9.3.3.2), with the four position-offset tables (`Sig_Ref_Diff_Offset`, `Coeff_Base_Ctx_Offset`, `Coeff_Base_Pos_Ctx_Offset`, `Mag_Ref_Offset_With_Tx_Class`) quoted from the spec. The functions only read the partially-decoded `Quant[]` levels (`pos = scan[c]`) and take `txType`/`txSz` from the caller, so they are pure and target independent. **Oracle = two independent sources:** the embedded 2D `Coeff_Base_Ctx_Offset` table was verified position-for-position against libaom's `av1_nz_map_ctx_offset` (`av1/common/txb_common.c`) — 7,440 positions, zero differences, and separately against the spec's own offset algorithm; the test then drives 2,000 seeded cases across all 19 transform sizes and all 16 transform types (plus the `all_zero`/`dc_sign` grids) through the Kof accessors and requires a second, independent Java reader to reproduce every value — 3,130 facts, zero differences. **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1CoeffCtx' not found`, measured); post-slice `Av1CoeffCtxE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3c LANDED 04/10 (pure Kof, AV1 DEFAULT COEFFICIENT CDFs — `libs/image/Av1CoeffCdf.kf`, new):** the third stage of the AVIF decode chain, the entropy tables the tile coefficient walk reads. `class Av1CoeffCdf(qctx)` carries the 13 default coefficient CDF tables of the AV1 spec §10 additional tables — `Default_Txb_Skip_Cdf`, `Default_Eob_Pt_16/32/64/128/256/512/1024_Cdf`, `Default_Eob_Extra_Cdf`, `Default_Dc_Sign_Cdf`, `Default_Coeff_Base_Eob_Cdf`, `Default_Coeff_Base_Cdf`, `Default_Coeff_Br_Cdf` — embedded verbatim from the spec and selected by `COEFF_CDF_Q_CTXS` via `get_q_ctx(base_q_idx)` (`<=20 → 0`, `<=60 → 1`, `<=120 → 2`, else `3`; libaom `av1/common/entropy.c`). The accessors (`txbSkipCdf`, `eobPtCdf`, `eobExtraCdf`, `dcSignCdf`, `coeffBaseEobCdf`, `coeffBaseCdf`, `coeffBrCdf`) flatten the spec's `[qctx][txSzCtx][ptype][ctx]` layout and return a fresh copy so the tile walk can adapt it in place without mutating the defaults; `coeffBrCdf` applies the spec's `Min(txSzCtx, TX_32X32)` clamp. The values are parsed from chunked string literals (`split`/`toInt`), so the module stays pure Kof on every target. **Oracle = a second, independent source:** all 13 tables were cross-checked value-for-value against libaom's `av1/common/token_cdfs.h` (the `AOM_CDFn` macros expanded to the spec CDF shape; the 512/1024 pair's collapsed `[2]` axis and duplicate normalised to the spec shape) — 15,996 numbers, zero differences; the test then requires the Kof probe to reproduce that libaom-derived golden for every CDF across all four qctx. **Proof RED-first:** the pre-slice tree cannot compile the probe (`PKG006 import 'image.Av1CoeffCdf' not found`, measured); post-slice `Av1CoeffCdfE2ETest` **6/6** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3b LANDED 04/10 (pure Kof, AV1 TRANSFORM DESCRIPTOR — `libs/image/Av1Tx.kf`, new):** the second stage of the AVIF decode chain, on top of the slice-3a entropy decoder. It provides everything the coefficient walk selects from: the transform-size tables (`Tx_Width`/`Tx_Height`/`Tx_Width_Log2`/`Tx_Height_Log2`/`Tx_Size_Sqr`/`Tx_Size_Sqr_Up`/`Adjusted_Tx_Size`/`txSzCtx`), the **scan orders** (`get_scan`/`get_default_scan`/`get_mrow_scan`/`get_mcol_scan`), the transform classes (`get_tx_class`), the transform sets (`get_tx_set`) and the transform-type selection (`compute_tx_type`/`is_tx_type_in_set`/`Mode_To_Txfm`), every table and rule quoted from the AV1 spec §3/§5.9.2/§6.4.3 and the additional tables, read on the dev host 04/10. The scans are GENERATED, not embedded: a zig-zag for square transforms, a row-diagonal for tall, a column-diagonal for wide, row-major for `mrow`, column-major for `mcol`, with the spec's effective dimensions for the 64-wide sizes (a 64-square-up transform scans as 32x32, a 16x64/64x16 as 16x32/32x16 — the spec's "half of the coefficients are zero" rule). The test pins every one of the 19x3 orders against libaom's `av1_scan_orders` (the spec markdown writes the same physical order row-major while libaom flattens column-major; both are normalised and agree). Proof RED-first: the pre-slice tree cannot even compile the probe (`PKG006 import 'image.Av1Tx' not found`, measured); post-slice `Av1TxE2ETest` **7/7** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS (pure arithmetic, no `IOJS001`), with a second, independent plain-Java generator (`Av1TxSupport.javaFacts`) agreeing fact-for-fact on the transform tables, all 57 scan orders (length + rolling hash), the transform classes/sets and the transform-type selection. `decodeRaster` still refuses AVIF until the chain closes.
> **AVIF slice 3a LANDED 04/10 (pure Kof, AV1 SYMBOL / ENTROPY DECODER — `libs/image/Av1Symbol.kf`, new):** the first stage of the decode chain. Implements the AV1 spec §9.2/§9.3 range coder verbatim: `init_symbol(sz)` (seed `Min(sz*8,15)` bits, `SymbolValue=((1<<15)-1)^padded`, `SymbolRange=1<<15`, `SymbolMaxBits=8*sz-15`), `read_symbol(cdf)` (`cur = ((SymbolRange>>8)*(f>>6))>>1 + 4*(N-symbol-1)`, renorm, then the spec's adaptation `rate = 3 + (cdf[N]>15) + (cdf[N]>31) + Min(FloorLog2(N),2)`, `cdf[i] += (tmp-cdf[i])>>rate`, `cdf[N] += (count<32)`), `read_bool()` = `read_symbol` over the fixed equiprobable `[1<<14,1<<15,0]` with adaptation suppressed, `read_literal(n)` MSB-first, `exit_symbol()`, `position()`. The class also carries `av1FloorLog2`/`av1Bool`. **Oracle:** byte streams produced by libaom's entropy encoder (`aom_dsp/entenc.c`) for six chosen (CDF, symbol-sequence) cases, round-tripped by libaom's own decoder (`entdec.c`), built on the dev host 04/10 (not committed); the pinned golden is the symbol sequence (`literal20`/`literal12`/`sym4fixed`/`sym5adapt`/`sym2adapt`/`sym8adapt`). **Real-file proof:** the host `.avif`'s primary tile payload (slice 2n `readAvifTilePayloads`, 42132 bytes) decodes its first 64 booleans + 8 literals to `BOOLS 1 0 0 1 0 1 1 1 1 1 0 1 0 0 0 0 0 0 0 1 1 1 1 1 0 1 1 1 1 1 0 0 1 1 1 0 0 1 0 0 1 0 1 0 1 1 0 0 0 1 1 0 1 1 0 0 1 0 1 1 1 1 0 0 LIT 236 22 144 184 174 78 131 116`, matching libaom's `od_ec_decode_bool_q15` on the same bytes (note: the literals continue the stream after the 64 bools; a fresh-decoder measurement gives a different byte run). The second independent Java reader (`Av1SymbolSupport.javaDecode`, a plain-Java spec walk) agrees fact-for-fact on all six fixtures AND the real tile. Proof RED-first: `Av1SymbolE2ETest` **8/8** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS (compile AND run — the decoder is pure arithmetic with no `kof.io`, so JS does not refuse `IOJS001`; that is the correct behavior for this slice) + the two agreement tests. Bug caught: the dev-host oracle printed the encoder buffer AFTER `od_ec_enc_clear` freed it (use-after-free corrupted the first fixture's bytes) — fixed by `memcpy` before the clear; a reminder that fixtures are re-measured, never remembered. No compiler change, no new gap, no decision required. Next: tile coefficient walk → loop filter → quantization → `decodeRaster` AVIF.
> **AVIF slice 2o LANDED 03/10 (pure Kof, NON-UNIFORM tile_info — `libs/image/AvifFrame.kf` + `AvifSeq.kf`):** the tile_info walk (2e) refused `IMAGE: avif tile size list not covered` when `uniform_tile_spacing_flag == 0`; this slice implements the spec's non-uniform branch (AV1 5.9.15): the per-axis `ns()` size lists `width_in_sbs_minus_1` (bounded by `Min(sbCols - startSb, maxTileWidthSb)`) and `height_in_sbs_minus_1` (bounded by `Min(sbRows - startSb, maxTileHeightSb)`, where `maxTileHeightSb` uses the spec's `widestTileSb` area rule and the `minLog2Tiles` adjustment), then derives `TileCols`/`TileRows` and their `tile_log2` values (so `context_update_tile_id`/`tile_size_bytes` are read with the right width). New helper `seqNs(b, bitPos, n)` implements the AV1 4.10.6 non-symmetric descriptor (`w = FloorLog2(n)+1`, `m = (1<<w)-n`, one extra bit when `v >= m`). The uniform path is byte-identical (the real file's 1x1 still parses). **Proof RED-first:** new `nonuni.avif` fixture (uniform flag 0, col sizes {1,1} + row size {2} over the 128x128 `redSeq128`, 2x1 tiles) — pre-fix the walk refused `IMAGE: avif tile size list not covered`; post-fix `nonuni t=0 ... tiles=2x1 hb=3`. The second Java reader (`AvifFrameJavaSupport`) gained its own independent `ns()` walk; the old `sizelist` refusal fixture (non-uniform, no longer a refusal) is removed. `AvifFrameE2ETest` **16/16** + AVIF battery **56/56** on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`. Next: tile payload/coefficient walk → loop filter → quantization → `decodeRaster` AVIF.
> **AVIF slice 2n LANDED 03/10 (pure Kof, TILE PAYLOADS — real-file `readAvifTilePayloads`; `libs/image/AvifGroup.kf`):** the tile-group walk (2f/2m) enumerates each tile's byte size but discards its position, so the byte range the AV1 coefficient decoder consumes was not yet addressable. This slice records each tile's interleaved start offset (`AvifTileGroup.tileOffsets`) at the exact 6.10.1 position (`sz -= tileSize + TileSizeBytes`, so the offset is `p` AFTER the `le(TileSizeBytes)` field and BEFORE the tile's bytes) and adds `avifTilePayload(item, group, i)` (bounds-checked `Int[]` copy), `avifItemTilePayloads(item)` (all tiles in tile order across the frame's groups) and `readAvifTilePayloads(path)` (the file-level composition over `avifItemBytes`). Still a payload EXTRACTION, not a decode: the coefficient walk, loop filter and quantization ride the next decode slices, and `decodeRaster` keeps refusing AVIF. **Real-file proof (executed):** `readAvifTilePayloads` on the host `.avif` → `tile 0 len=42132 first=151 last=136 h=4094`, matching the 2m group walk (`total=42132`) and `iloc` item1 length. Fixtures/readers made byte-DISTINCT per tile (the old uniform `0x07` filler masked a wrong offset): `groupBytes` now writes an increasing seed, and both the Kof probe and the second Java reader hash each tile's bytes (`avifTilePayload` vs `javaTileFacts`) — agreement is the proof. Proof RED-first: `AvifFrameE2ETest` **16/16** and the whole AVIF battery **56/56** (JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, second-reader agreement). Next: tile payload/coefficient walk → loop filter → quantization → `decodeRaster` AVIF.
> **AVIF slice 2m LANDED 03/10 (pure Kof, HEADER TAIL + OBU_FRAME INLINE TILE GROUP — real-file `readAvifTileGroups`; `libs/image/AvifFrame.kf`, `AvifGroup.kf`, `AvifSeq.kf`):** slice 2l left the real OBU_FRAME (type 6) honestly refused because the inline tile group sits AFTER the full `uncompressed_header`, whose tail (quantization/segmentation/delta_q/delta_lf/loop-filter/cdef/restoration/tx-mode/reduced_tx_set) was not walked. This slice walks that 5.9.2 tail to the `byte_alignment()` and locates the inline group exactly. `AvifSeq.kf` now CAPTURES `enable_cdef`/`enable_restoration` into `AvifSeqHeader` (previously read and discarded — the exact bits that guard the `cdef_params`/`lr_params` branches); `AvifFrame.frameTailWalk` implements `quantization_params` (5.9.9, `read_delta_q` su(1+6) via the new `seqSu`), `segmentation_params` (5.9.11, `seg_id`/`seg_alt_q` with `CodedLossless` derived per the spec's `LosslessArray` loop), `delta_q_params`/`delta_lf_params` (5.9.12/13), `loop_filter_params` (5.9.8), `cdef_params` (5.9.14), `lr_params` (5.9.16), `read_tx_mode` (5.9.17) and `reduced_tx_set`, with `frame_reference_mode`/`skip_mode_params` inferred for the intra-only frames this face admits (`film_grain_params` refused earlier by `seqWalk`). `AvifFrameHeader.headerBytes` is now the ALIGNED header size RELATIVE to the OBU payload start (was absolute — a real bug the OBU_FRAME fixture exposed), and the record gains `baseQIdx`/`loopFilterLevel0`/`cdefBits`. `AvifGroup.avifItemTileGroups` reads the OBU_FRAME's single inline group at `p + headerBytes` instead of refusing, and `tileGroupWalk` was corrected to the 6.10.1 interleaved layout (`sz -= tileSize + TileSizeBytes`: each tile's payload is skipped before the NEXT size field) — the old contiguous-size-table shape only worked for ≤2 tiles and mis-decoded a 4-tile group. **Real-file proof:** `readAvifFrameHeader` → `t=0 480x410 tiles=1x1 hb=11 bq=32 lf=7 cd=0` (base_q_idx 32, loop_filter_level[0] 7, cdef disabled — matches the spec-parser measurement); `readAvifTileGroups` → `group 0..0 n=1 sizes=0 last=42132 total=42132` (the single tile takes the remaining payload). Fixtures/readers made spec-faithful: `frameReduced` now computes the uniform tile grid from the real `sbCols`/`sbRows` (the old writer wrote a fixed 2-bit `context_update_tile_id`, wrong for 2x2), new non-lossless `q32.avif` exercises the cdef branch and the lf-delta skip, new `tg-inline.avif` OBU_FRAME positive golden, and both second readers (`AvifFrameJavaSupport`, `AvifGroupJavaSupport`) walk the same tail independently. Proof: `AvifFrameE2ETest` **16/16** and the whole AVIF battery **56/56** (JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, second-reader agreement). Next: tile payload/coefficient walk → loop filter → quantization → `decodeRaster` AVIF.
> **AVIF slice 2l LANDED 03/10 (pure Kof, FRAME-HEADER INTRA-BRANCH SPEC ALIGNMENT — real-file `readAvifFrameHeader`; `libs/image/AvifFrame.kf`):** composing the slice-2k item chain to the frame header on the REAL host `.avif` refused with `IMAGE: avif tile size list not covered`, although that file's `tile_info()` is uniform 1x1. Root cause: the intra path read `is_filter_switchable` + `is_motion_mode_switchable` (AV1 5.9.10) although the spec reads them ONLY in the non-intra branch (5.9.2:766-777); on a reduced/intra frame those two bits do not exist, so the walk consumed two spurious bits and desynced `tile_info` (measured by a Python spec parser: the code started `tile_info` at bit 6, the spec at bit 3). The same two deviations were shared by all three hand-built fixtures and both second readers — the shared-blind-spot lesson again. Corrected to spec: the interp/motion bits are not read on the intra path; `frame_type` uses the spec numbering (KEY=0 / INTER=1 / INTRA_ONLY=2 / SWITCH=3 — the old code treated 0 as INTER and 1 as KEY, so the non-reduced path also threw on a valid INTRA_ONLY frame); and the unreachable `IMAGE: avif frame size-with-refs not covered` refusal is removed (`frame_size_with_refs` is non-intra only, 5.9.2:760). **Real-file proof:** pre-fix `readAvifFrameHeader` → `IMAGE: avif tile size list not covered`; post-fix → `t=0 480x410 render=480x410 tiles=1x1 tileSizeBytes=0 hb=18`, matching the `ispe`/`av1C` facts; the inline tile group is then honestly refused (`IMAGE: avif frame obu tile group not covered`, the next slice — the real frame is OBU_FRAME type 6). Fixtures made spec-faithful (`AvifFrameSupport.frameReduced`/`frameNr` drop the intra interp bits, KEY `error_resilient` is now inferred; new non-reduced INTRA_ONLY `intra.avif`; the fabricated `sizerefs`/`interp` refusals removed) and both second readers (`AvifFrameJavaSupport`, `AvifGroupJavaSupport`) aligned. Proof: `AvifFrameE2ETest` **16/16** and the whole AVIF battery **56/56** (JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, second-reader agreement). Next: inline OBU_FRAME tile group → tile payload/coefficient walk → loop filter → quantization → `decodeRaster` AVIF.
> **AVIF slice 2k LANDED 03/10 (pure Kof, REAL-FILE ITEM-DATA CHAIN — `seq_profile` f(3) + `operating_point_idc` f(12) + LEB128 `obu_size`):** running the COMPOSED item chain (`readAvifItemObus`) on the REAL AVIF file (slice 2i host file) threw `IMAGE: truncated avif sequence header`. Root cause: `seqWalk` read `seq_profile` as **f(2)** but AV1 §5.5.1 is **f(3)**; the wrong profile selected the profile-0 `color_config` branch and consumed two spurious `chroma_sample_position` bits. The same §5.5.1 sweep found `operating_point_idc` skipped as **16 bits** but the spec is **f(12)** — invisible because the fabricated non-reduced fixtures and the second reader shared the same wrong width (the shared-blind-spot lesson of 2h/2i/2j again). After the profile fix the walk hit `IMAGE: avif obu truncated`: `obu_size` was accumulated big-endian (`(size<<7)|…`) but AV1 §4.10.5 is **LEB128 little-endian** (`value |= (b&0x7f) << (i*7)`), which only diverges for sizes ≥128 bytes — exactly the real frame OBU (42143 bytes). Fixed in all six sites (`Avif.kf` `seqHeaderReduced`, `AvifSeq.kf` config-OBU + `seqWalk`, `AvifObu.kf`, `AvifFrame.kf`, `AvifGroup.kf`, `AvifMeta.kf` OBU size **and** `metadata_type`). **Real-file proof:** `readAvifItemObus` now returns `total=3 seq=1 frames=1`, sequence header `480x410 depth=8 profile=1` — matching `ispe` 480x410, `av1C` profile 1 and `ffprobe yuv444p`; `readAvifMetadata` reports `480x410 items=2 primary=1 alpha=true profile=1 depth=8`. Fixtures made spec-faithful (`seq_profile` f(3), `operating_point_idc` f(12)) in `AvifSeqSupport`/`AvifMetadataSupport`/`AvifFrameSupport` and all three second readers (`AvifSeqSupport.javaSeqCore`, `AvifFrameJavaSupport`, `AvifGroupJavaSupport`); new reduced profile-1 fixture `r1.avif` (the real-file form) and a >127-byte-OBU fixture `big.avif` (200-byte padding then metadata, LEB128 writer+reader). Proof: `AvifSeqE2ETest` **8/8**, `AvifObuE2ETest` **8/8** (+`big`), `AvifFrameE2ETest` **16/16**, `AvifMetaE2ETest` **8/8**, `AvifItemsE2ETest` **8/8**, `AvifMetadataE2ETest` **8/8** — JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, second-reader agreement (AVIF battery 56/56). Next: tile-group payload/coefficient walk → loop filter → quantization → `decodeRaster` AVIF.
> **AVIF slice 2j LANDED 03/10 (pure Kof, REAL-FILE CONFORMANCE — `ipma` property selection + `av1C` record layout + `iref`/`auxl` alpha; `libs/image/Avif.kf`, `AvifSeq.kf`):** continuing the D-PLAN-ONE-OWNER re-review against the REAL AVIF file (slice 2i), running the COMPOSED chain on it surfaced THREE more real container bugs the hand-built fixtures could not catch because the fixtures and the readers shared the same assumptions: (1) the primary item's properties were read as "exactly one `ispe` and one `av1C` in `ipco`" — the real file has TWO `av1C` (colour + alpha) and two `ispe`, so the whole reader refused `IMAGE: avif av1C property not covered`; the fix parses `iprp/ipma` (ISO 14496-12 §8.11.4, `entry_count` **32-bit**, version-0 16-bit item ids, per-association essential bit) and selects the PRIMARY item's properties, and `AvifSeq.av1cPayload` shares the same selection so the whole AVIF chain stops refusing two-`av1C` files. (2) The `av1C` record was modelled with a 4-byte `configOBUsLength` prefix that DOES NOT EXIST: AV1-ISOBMFF §2.3.3 is `AV1CodecConfigurationRecord` = 4 fixed bytes then `configOBUs[]` directly (the real record is `81 21 00 00`, 4 bytes, empty array); the length prefix was a fixture/reader shared blind spot. (3) `iref` was modelled as a FullBox with a top-level `entry_count` and an inline HEVC `auxc` URN; the real file uses the ISO 14496-12 §8.11.12 child-box list with an `auxl` entry from the alpha item to the primary, and the alpha type lives in the `auxC` AuxiliaryTypeProperty URN `urn:mpeg:mpegB:cicp:systems:auxiliary:alpha` (AVIF §4.1). All three syntaxes are quoted from the spec sources read on the dev host (AV1-ISOBMFF v1.3.0 §2.3.3; AVIF v1.2.0 §4.1) and cross-checked against FFmpeg (`mov_read_iprp`) and the real file. Also honest: `avifSeqHeader` now refuses an EMPTY `configOBUs` with `IMAGE: avif config obu absent` (a legal AVIF still image carries its sequence header in the item data — `readAvifItemObus` is the path that yields it), instead of reading the item bytes as if they were the config OBU. Proof RED-first: on the real file the pre-fix library refused at `readAvifMetadata` with `IMAGE: avif av1C property not covered` (measured); post-fix it reports `avif 480x410 items=2 primary=1 alpha=1 profile=1 level=1 tier=0 mono=0 sub=0/0 depth=8/8`, matching `ffprobe` (`yuv444p`, AV1 profile 1, 480x410). `AvifMetadataE2ETest` **8/8** (the `alpha.avif` fixture rewritten to the real two-`av1C` + `auxl` + `auxC` shape; the second Java reader now parses `ipma`/`auxl`/`auxC` independently) and `AvifSeqE2ETest` **8/8** (new empty-`configOBUs` refusal) on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`; the whole AVIF battery **56/56** unchanged. NEXT: the AVIF decode chain (item-data sequence-header walk — the real header is reduced profile 0 but `seqWalk` diverges on it; tile-group payload/coefficient walk), then AVIF `decodeRaster`.
> **AVIF slice 2i LANDED 03/10 (pure Kof, `iloc` VERSION 0 + multi-extent + a REAL-FILE proof — `libs/image/AvifItems.kf`):** re-verifying the slice-2a state (D-PLAN-ONE-OWNER re-review) against a REAL AVIF file found on the dev host (a `.avif` shipped inside a browser-extension bundle — the fixture-host gap the plan recorded 01/10 is closed for this slice) surfaced a REAL misalignment: the slice-2a `iloc` parser read `item_count` at the wrong offset (one byte late), skipped the index_size/reserved byte, and put `construction_method` at `(word>>5)&7` instead of the spec's low nibble. The reader AND the second Java reader shared the same off-by-one, so the slice-2a oracle had a blind spot (the lesson of slice 2h). The syntax is now quoted from ISO 14496-12 §8.7.4 and cross-checked against TWO independent implementations read on the dev host 03/10 (FFmpeg `mov_read_iloc` and the mp4parser `ItemLocationBox` javadoc, which carries the spec text verbatim) AND validated byte-for-byte against the real file. Changes: `iloc` VERSION 0 (16-bit item ids, no construction word) is decoded instead of refused; VERSION 1 keeps the 12-bit-reserved + 4-bit-construction word at the correct offset; offset_size/length_size in {4,8} and base_offset_size in {0,4,8} are honored as measured field sizes (the real file is offset=4/length=4/base=0); an item with MORE THAN ONE extent is concatenated in order (AVIF §2.3); construction 0/1 unchanged, 2 refused; v2 still refused. Proof RED-first: `AvifItemsE2ETest` **8/8** — five item facts (mdat 2 items, idat, a v0/base-0 fixture matching the real-file shape, a TWO-extent concatenation) on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, 4 named refusals, and the second independent Java reader (rewritten spec-faithful) agreeing fact-for-fact; the pre-fix library reads the real file's `item_count` as 512 and refuses with `IMAGE: avif iloc item list not covered` (measured), and a Python spec parser independently confirms the real item1 range [14723,56883) len=42160 and item2 [430,14723) len=14293. Docs EN+PT: this line + the slice-2a description below, CHANGELOG, status, README 0f. No compiler change, no new gap, no decision required. NEXT: the AVIF decode chain (tile-group payload/coefficient walk), then AVIF `decodeRaster`.
> **AVIF slice 1 LANDED 01/10 (pure Kof, container facts — `D-WEBP-LOSSY-PURE-KOF` chain: 'AVIF follows after VP8'):** `libs/image/Avif.kf` reads the ISOBMFF/AVIF CONTAINER from a bounded 4 KiB prefix — ftyp brand (`avif`/`avis`, major or compatible), `meta` (full box) children walk, `pitm` primary id, `iinf` item count (v0), `iprp/ipco` exactly-one `ispe` dimensions + exactly-one `av1C` configuration record (AVIF §3.1.1): seq_profile/level/tier/high_bitdepth/twelve_bit→bit depths (AV1 §5.1 index tables), monochrome, subsampling (monochrome consistency checked; profile/depth consistency checked), config OBU = forbidden-bits-zero OBU_SEQUENCE_HEADER with size field set AND the REDUCED-form flag — non-reduced forms REFUSED explicitly (`IMAGE: avif sequence header form not covered`; the full non-reduced field walk is its own later slice). `iref`/`auxc` alpha association to the primary (`urn:mpeg:hevc:2015:auxid:1`), version-0 boxes only (v1 = explicit refusal). Pixel decode NOT touched: `decodeRaster` keeps refusing AVIF. **Fixtures:** hand-built byte-exact per spec (no encoder exists on the test host: ffmpeg/avifenc/pip measured ABSENT 01/10; the libheif .so has no headers for an honest ABI pin — recorded as the front's TOOLING GAP; real-file goldens ride a later slice with a fixture host). **Oracle:** a SECOND, independent reader written in plain Java inside the test harness agrees byte-for-byte with the Kof library on every fact (`secondJavaReaderAgreesWithKofLibrary`). Proof: `AvifMetadataE2ETest` **8/8** — JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) goldens, JS = `IOJS001` compile refusal (`readRange`), 5 explicit refusal messages, second-reader agreement; `AvifMetadataSupport` carries builders + second reader (test-hygiene ratchet split). Next slice candidates (plan §34): non-reduced sequence header + full item-location (iloc/idat) facts, then the AV1 intra decode chain. **Superseded by slice 2j (03/10, above): the exactly-one-`av1C`/exactly-one-`ispe` rule, the inline `iref/auxc` alpha and the length-prefixed `av1C` record were real-file bugs — `ipma` selection, the 4-byte record + `configOBUs[]` layout and the `iref/auxl`+`auxC` alpha are the spec-faithful behavior now.**
> **AVIF slice 2a LANDED 01/10 (pure Kof, ITEM LOCATION — the mechanism every later AVIF slice needs):** `libs/image/AvifItems.kf` extracts the bytes of ONE stored item through the ISOBMFF `iloc` box (ISO 14496-12 §8.7.4): VERSION 1 only (v0/v2 = explicit refusals — the v0 bit-packing is measured against real files in its own slice), offset_size == length_size == 4, base_offset_size 4/8 (zero high word), reserved nibbles/bit + data_reference_index + index_size must be zero, construction_method 0 (file-absolute, e.g. `mdat`) and 1 (`idat` payload-relative); 2 = refused; the targeted item must carry EXACTLY one extent (multi-extent = refused, its own slice); a single `idat` at most; the extracted range must live inside the bounded 64 KiB prefix (beyond = refusal, never a truncated answer). `readAvifItemBytes(path, id)`/`avifItemBytes(bytes, id)`. No pixel decoding. Proof: `AvifItemsE2ETest` **8/8** — spec-golden byte facts (len/first/last/sum) on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS = `IOJS001` compile refusal, 4 explicit refusal messages, and a SECOND independent Java reader (`AvifItemsSupport.readItemJava`) byte-for-byte on every item; image battery `AvifMetadataE2ETest` 8/8 + `ImageMetadataE2ETest` 7/7 + `RasterDecodeE2ETest` 26/26 (1 env-skip) unchanged. Next slice candidates: non-reduced `OBU_SEQUENCE_HEADER` field walk, multi-extent/v0 iloc against real encoder files (fixture host), then the AV1 intra decode chain. **Superseded by slice 2i (03/10, above): the v1-only and one-extent limits were a parser offset bug; v0 and multi-extent now decode, and the parser is spec-faithful.**
> **AVIF slice 2b LANDED 02/10 (pure Kof, SEQUENCE HEADER field walk — reduced AND non-reduced forms):** `libs/image/AvifSeq.kf` walks the full `OBU_SEQUENCE_HEADER` — every width quoted from the AV1 spec PDF read line-by-line on the dev host (5.5.1/5.5.2, not from memory): profile(2)/still(1)/reduced(1); reduced = level(5) only; non-reduced = timing branch (64-bit tick/scale, equal-picture uvlc consumed; decoder model info REFUSED), initial display delay, operating-point loop (idc 16, level 5, tier when level>7, per-op delay 4), frame width/height bits + max frame size, frame id numbers (4+3), capability block (128x128/filter/intra-edge, inter block/order hint/screen-content/integer-MV), superres/cdef/restoration, color_config: high/twelve bit depths, monochrome, color description (8+8+8), per-profile subsampling, chroma sample position, separate-UV delta-Q, film-grain flag. Refusals: profile 3 (`IMAGE: avif sequence profile not covered`), decoder model info, film grain, truncated config OBU. OBU wrapper: forbidden bits 0, type 1, size field, LEB128 size bounded by the av1C record. `readAvifSeqHeader(path)`/`avifSeqHeader(bytes)` -> `AvifSeqHeader` record (profile, still, reduced, max frame size, mono, subsampling, bit depth, chroma position, separate UV). `Avif.kf` container metadata face and `decodeRaster` unchanged. Proof RED-first: `AvifSeqE2ETest` **8/8** — reduced + non-reduced spec-fact goldens (profile 0 8-bit 8x8; profile 2 12-bit 32x24 with timing) on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), 3 refusal messages, SECOND independent Java reader (`AvifSeqSupport.javaSeqFacts`) fact-for-fact on all three forms; image battery `AvifMetadataE2ETest` 8/8 + `AvifItemsE2ETest` 8/8 unchanged (slice-1 `configObu` rewritten spec-exact: it had been missing the reduced frame-size/capability bits). Bugs the RED-first caught before green: config OBU end bound checked against the box header instead of the record; OBU extension flag read from the size byte; reserved identifier `byte` in Kof; `separate_uv_delta_q` mispositioned on the sRGB color path. Next slice candidates: item OBU-stream walk (delimiter/frame OBU types over the located item bytes), multi-extent/v0 iloc against real encoder files (fixture host), then the AV1 intra decode chain.
> **AVIF slice 2c LANDED 02/10 (pure Kof, ITEM OBU-STREAM WALK — enumeration, not decode):** `libs/image/AvifObu.kf` walks the primary AV1 item's bytes (located by slices 1+2a) OBU by OBU with every rule quoted from the AV1 spec PDF read on the dev host (5.2/5.3.1/5.3.2/6.2): header byte forbidden(1)=0/type(4)/extension(1)/size-present(1)/reserved(1)=0, extension header 8 bits, LEB128 `obu_size` ("the size in bytes of the OBU not including the bytes within obu_header or the obu_size syntax element", §6.2.2); low-overhead streams — the AVIF item form — REQUIRE the size field on every OBU (§5.2: "When using this format, obu_has_size_field must be equal to 1") — a missing size field is REFUSED, never guessed; the stream must BEGIN with a temporal delimiter; the first sequence header is parsed by the slice-2b `seqWalk` (extracted bit-range function, behavior identical); types counted per the §6.2 table (delimiter/seq/frame header/redundant/tile group/tile list/metadata/frame/padding/reserved — reserved 0/9..14 skipped per "shall be ignored by AV1 decoder" §6.2). Refusals: `IMAGE: avif obu truncated`, `IMAGE: avif item obu missing size field`, `IMAGE: avif item obu reserved bit set`, `IMAGE: avif item obu forbidden bits`, `IMAGE: avif item does not begin with a temporal delimiter`, `IMAGE: avif item has no sequence header`, `IMAGE: empty avif item`. ENUMERATION only — frame/tile payloads are not decoded, `decodeRaster` keeps refusing AVIF. `readAvifItemObus(path)` composes container→pitm→iloc→walk; `avifItemObus(item)` is the pure-bytes face. Proof RED-first: `AvifObuE2ETest` **8/8** — mixed-stream spec-fact goldens (4-OBUs delimiter+seq+metadata+padding; 8-OBUs covering frame header/redundant/tile group/tile list/reserved/frame counts + reduced and non-reduced seq facts) on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), 6 refusal messages, SECOND independent Java reader (`AvifObuSupport.javaObuFacts` + `AvifSeqSupport.javaSeqCore`) fact-for-fact; image battery `AvifMetadataE2ETest` 8/8 + `AvifItemsE2ETest` 8/8 + `AvifSeqE2ETest` 8/8 + `ImageMetadataE2ETest` 7/7 + `RasterDecodeE2ETest` 26/26 (1 env-skip) unchanged. Bugs RED-first caught: a trunc fixture that claimed less than the item held read zeros instead of refusing (fixture corrected to a size past the end); `Bits` dedup for the ratchet. Next slice candidates: frame-header/tile-group payload walks (the AV1 intra decode chain), multi-extent/v0 iloc against real encoder files (fixture host).
> **AVIF slice 2e LANDED 02/10 (pure Kof, TILE_INFO EXTENSION — uniform-spacing counts, still no decode):** the slice-2d frame walk extends through the 5.9.10 flags and the 5.9.15 `tile_info()` uniform path: `is_filter_switchable` (frame-level filter selection refused as `IMAGE: avif frame interp not covered`), `is_motion_mode_switchable`, `disable_frame_end_update_cdf` (read only when the prefix allowed it), then the uniform flag, the `increment_tile_cols_log2`/`increment_tile_rows_log2` loops (the spec's BREAK keeps the current log2 — a fixture initially invented stop bits past the cap and the harness caught it), `context_update_tile_id` + `tile_size_bytes_minus_1` (f(2)) when either log2 is > 0. Non-uniform spacing is refused by name (`IMAGE: avif tile size list not covered`) — the `ns()` per-tile tables are their own slice. `AvifFrameHeader` gains `tileColumns`/`tileRows` (the derived sb-grid counts); `AvifSeqHeader` grows the frame-context further with `use128x128Superblock` and `enableRefFrameMvs` captured as values (the walk consumes the same bits as before — the 2b/2c/2d suites stayed green). `frameWalk` now ends its documented stop at tile_info; loop filter/quantization/coefficient syntax ride the decode slices. Proof RED-first: `AvifFrameE2ETest` **8/8** — goldens now carry exact tile counts (32x32 uniform 1x1; 128x128 with two col + two row increments 2x2), JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, the refusal list grows to 8 named messages, and the SECOND independent Java reader agrees fact-for-fact on the goldens AND on all eight refusals (it diverged three real times during the loop: a missing allow_intrabc advance, a duplicated uniform advance, and stop bits written past the cap — each caught by the agreement test before green). Bugs RED-first caught here first: the walk read the motion bit twice in the draft; the `1x1` uniform tile at sbCols=1 does NOT read any increment (max=0) and the fixture needed a 128x128 sequence header to exercise a real multi-tile. Next: tile-group OBU header (§6.4.2 `tg_start`/`tg_end` + tile size column — "how many tiles, what sizes" without decode)
> **AVIF slice 2f LANDED 02/10 (pure Kof, TILE-GROUP HEADER — tile byte sizes without decode, `libs/image/AvifGroup.kf`):** the tile_group_obu() header prefix per 5.11.1 + the 6.10.1 conformance rules, in its own file (split by responsibility): range flag when NumTiles>1, tg_start/tg_end f(tileBits), `byte_alignment()` (zero bits to the next byte, 5.3.5), the le(TileSizeBytes) per non-last tile with the LAST tile taking the remaining payload bytes. Named refusals for every conformance violation: full-range-with-flag, inverted range, out-of-order tg_start (`tg_start == TileNum` rule), last group not ending at NumTiles-1, OBU_TILE_LIST (8), redundant frame header (7), truncation anywhere — and an honest limit: an OBU_FRAME (6) places its inline group AFTER the full uncompressed_header (quantization/segmentation/loop-filter syntax between tile_info and the end depend on decode-chain values), so type 6 is refused (`IMAGE: avif frame obu tile group not covered`), never a guessed offset. `AvifFrameHeader` gained tileBits/tileSizeBytes/headerBytes (tileWalk now CONSUMES the tile_size_bytes value — spec-faithful); `AvifTileGroup(tgStart, tgEnd, tileCount, tileSizes, lastTileSize, totalBytes)`; `avifItemTileGroups(item)` + `readAvifTileGroups(path)`. Proof RED-first: `AvifFrameE2ETest` **16/16** — goldens `one 0..0 n=1 sizes= last=2 total=2` + `split 0..1 n=2 sizes=,5 last=3 total=8` / `split 2..3 n=2 sizes=,4 last=2 total=6` on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, 8 named refusals, and the second independent Java reader (`AvifGroupJavaSupport`, fresh 5.9/5.11 walk with its own tileBits capture) agreeing fact-for-fact on goldens AND on all eight refusal strings. Bugs the agreement loop caught: the fixture writer emitted only the size field WITHOUT the per-tile payload bytes (both readers refused trunc identically → writer bug, proof the two implementations were live), and the trunc fixture's 200-byte claim overflowed the single-byte OBU size field (hand-encoded instead). Suite note: the 8 red `NETN001` JS-net tests measured on the OLDER rebase base were already fixed upstream by `6395dbafc` (D-NET-JS-V1 vote (c)); §567 closed the same day with green proof on tip (`KofNetTest` 4/4, `NetJsV1RefusalE2ETest` 3/3, `ConformanceMatrixTest` 14/14). Next: multi-extent/v0 iloc against real encoder files (fixture host), the tile-group PAYLOAD/coefficient walk (decode chain), then AVIF decodeRaster., multi-extent/v0 iloc against real encoder files (fixture host).
> **AVIF slice 2g LANDED 02/10 (pure Kof, METADATA OBU — OBU_METADATA payload walk, `libs/image/AvifMeta.kf`):** metadata_obu() per 5.8.1–5.8.4 + the 6.4.1 metadata_type table read from the spec corpus on the dev host (0 reserved for AOM use, 1 hdrCll, 2 hdrMdcv, 3 scalability, 4 itutT35, 5 timecode, 6–31 unregistered private, 32+ reserved for AOM use — the table is the contract, memory is not; a first landing had swapped 4/5 and mislabelled 32+ as user private, corrected 02/10 in slice 2h): leb128 metadata_type; T35 = country f(8) + optional extension byte when country==0xFF + raw payload byte count (the "last non-zero byte" content rule NOT applied — honest raw count reported); CLL = max_cll/max_fall f(16) exact numbers; MDCV = 8×f(16) chromaticities + 2×f(32) luminances as ten values; scalability and the private/reserved types are ENUMERATED with name + payload size, their sub-syntax not walked (structured/opaque video faces — documented frontier, never guessed). An unknown type never refuses (enumeration is the policy); only truncated reads throw `IMAGE:`. `AvifObuMetadata(type,name,payloadBytes,country,extended,t35Bytes,maxCll,maxFall,mdcv)`; `avifItemMetadataObus(item)` + `readAvifMetadataObus(path)` reuse the slice 2a/2c OBU framing and `Avif.kf` u8/be16/be32 (the new file imports the package helpers instead of redefining them). Proof RED-first: `AvifMetaE2ETest` **8/8** — goldens over t35/t35x/cll/mdcv/mixed fixtures (mixed stream carries T35 + scalability + unregistered-private metadata OBUs and a padding OBU in between) on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS refusal `IOJS001`, 4 honest truncation refusals, and a second independent Java reader (`AvifMetaJavaSupport`) agreeing fact-for-fact INCLUDING the refusal strings. The agreement loop caught a bad fixture: y=71241 chromaticity exceeds f(16) (both readers wrapped to 5705 identically → fixture bug, fixed to 60000). Suite note: the `NetJsE2ETest` red that reappeared was a STALE compiled class in the shared `target/test-classes` after upstream `6395dbafc` renamed the source to `NetJsV1RefusalE2ETest` — environmental, purged; net tests green 4/4 + 3/3 on the current tree. Next: slice 2h = metadata_timecode() (5.8.7 bitfields + 6.7.7 semantics) on `libs/image/AvifMeta.kf` reusing the 2d bit-reader; scalability(3) stays name+size (scalability_structure is deep video syntax — separate frontier); then multi-extent/v0 iloc against real encoder files (fixture host), then the tile-group payload/coefficient walk (decode chain), then AVIF decodeRaster.
> **AVIF slice 2h LANDED 02/10 (pure Kof, METADATA TIMECODE — `metadata_timecode()` walk + the 6.4.1 table correction, `libs/image/AvifMeta.kf`):** the metadata_type table was measured wrong in the slice-2g landing (4/5 swapped, 32+ mislabelled as user private); the contract was re-read from the AV1 spec source (`07.bitstream.semantics.md`) AND cross-checked against the AOM `aom/aom_codec.h` `OBU_METADATA_TYPE_*` enum (0 reserved for AOM use, 1 hdrCll, 2 hdrMdcv, 3 scalability, 4 itutT35, 5 timecode, 6–31 unregistered private, 32+ reserved for AOM use) — the two independent sources agree. The timecode payload (5.8.7 syntax + 6.7.7 semantics) is now walked: counting_type f(5), full_timestamp_flag f(1), discontinuity_flag f(1), cnt_dropped_flag f(1), n_frames f(9); when full_timestamp_flag is set, seconds f(6)/minutes f(6)/hours f(5); otherwise the seconds/minutes/hours flags gate their values (a value not present is reported as -1 — the spec's “inferred from the previous set” is stream-level state this metadata-only walk does not carry); then time_offset_length f(5) and, when > 0, time_offset_value f(time_offset_length), all read MSB-first with the 2d `seqRead` bit-reader. `AvifObuMetadata` gains `timecode: List<Int>` ([countingType, fullTimestamp, discontinuity, cntDropped, nFrames, seconds, minutes, hours, timeOffset]). Proof RED-first: `AvifMetaE2ETest` **8/8** — two new fixtures (`tc`: full timestamp, counting 3, n_frames 24, 23:59:45, offset 0; `tcf`: partial timestamp, every flag 0, time_offset_length 4/offset 9) plus the corrected T35 fixtures (type 4) and the mixed stream (now t35(4)+scalability(3)+private(7)+reserved(32)) on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, 5 honest truncation refusals (incl. a 2-byte timecode payload), and the second independent Java reader agreeing fact-for-fact on goldens AND refusals. The pre-fix library compiles the probe as `SEM025 Cannot resolve field 'timecode'` (measured); a table-only revert would print `4 timecode` and fail the golden. Bugs the correction caught: the first landing's table was wrong AND the agreement loop had a shared blind spot (both readers carried the same bad table), which is why 56/56 was green — the fixtures now anchor to the spec, not to a shared assumption.
> **AVIF slice 2d LANDED 02/10 (pure Kof, FRAME HEADER PREFIX — parse-and-stop, not decode):** `libs/image/AvifFrame.kf` parses `frame_header_obu`/`uncompressed_header` (AV1 spec §5.9.2, quoted from the PDF read on the dev host) over the located item bytes, carrying the context the slice-2b `seqWalk` now exposes on `AvifSeqHeader` (frame-id lengths, order-hint bits, frame size bit widths, SELECT screen-content/integer-MV markers): show_existing_frame, frame_type, show/showable, error_resilient (forced 1 for a shown KEY), disable_cdf_update, allow_screen_content_tools/force_integer_mv (SELECT vs seq-forced), current_frame_id (delta+additional+3 widths), frame_size_override_flag, order_hint, refresh_frame_flags + ref_order_hint loop, coded size (override widths `wBits+1`/`hBits+1`), superres refusal, render_size (the 5.9.6 flag has NO reduced guard — measured, corrected in-walk) and the allow_intrabc stop point. Refusals (named, never silent): `IMAGE: avif frame show-existing not covered`, `IMAGE: avif inter frame not covered`, `IMAGE: avif intra block copy not covered`, `IMAGE: avif frame size-with-refs not covered`, `IMAGE: avif superres not covered`, `IMAGE: truncated avif frame header`, `IMAGE: avif item has no frame header`. `readAvifFrameHeader(path)` composes container→pitm→iloc→OBU framing→this walk; `avifItemFrameHeader(item)` is the pure-bytes face; `AvifFrameHeader` reports frame type, show/error-resilient flags, size override, coded and render sizes. METADATA ONLY — tile info, loop filter and quantization syntax past allow_intrabc ride the decode slices; `decodeRaster` keeps refusing AVIF. Proof RED-first: `AvifFrameE2ETest` **8/8** — reduced-key, non-reduced KEY with size override (coded 5x3 vs max 8x8) and render-size goldens on JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, 6 refusal messages, SECOND independent Java reader (full 5.5/5.9 prefix parse in `AvifFrameSupport.javaFrameFacts`) fact-for-fact on the goldens AND on all six refusals; image battery unchanged (Metadata 8/8, Items 8/8, Seq 8/8, Obu 8/8, ImageMetadata 7/7, RasterDecode 26+1-skip); full kof-compiler suite 4361/0F. Bugs RED-first caught: a first-draft walk read the size bits before the order-hint/refresh and treated render_size as reduced-skipped (spec has no such guard); the frame-id width was a hardcoded 4 instead of the seq's delta+additional+3; the java reader desynced on the per-operating-point display-delay bit. Next slice candidates: tile-group payload walk (the AV1 intra decode chain), multi-extent/v0 iloc against real encoder files (fixture host).


> **State (29/09): UNDER DEVELOPMENT — promoted `future/` → `docs/development/` by `D-FUTURE-PROMOTION` + `D-IMAGE-VISION-GO` (maintainer 29/09), library-first (`D-KOF-FIRST-IMPL`).**
> **Slice 1 LANDED 29/09:** pure-Kof `libs/image/` — `Image(path).format()/.width()/.height()` read the **format + pixel dimensions** from the leading bytes (`PNG`/`GIF`/`BMP` info+core/`JPEG` SOF/`WEBP` VP8·VP8L·VP8X + `TIFF` + `ICO`/`CUR` + `PNM` P1–P6 + `QOI` + `PSD`/`DDS`/`farbfeld`/`AVIF`-`HEIF`) and the `Bool isImage(path)` helper, over a bounded 4 KiB prefix of `kof.io.readRange`; no codec, no pixels, no new syntax. Golden measured on JVM + Native x86-64 + riscv64 (qemu) + Script, JS gap `IOJS001` — `ImageMetadataE2ETest` 7/7.
> Heavy per R1/R9: `kof.image`/`kof.vision` are **official packages** (born `experimental`); codecs and algorithms come from mature libraries isolated behind the Kof API (imageio/turbojpeg/OpenCV/ONNX, evaluated per license/target). Pixels/filters and `kof.vision` remain future slices; a measured native-lane finding is catalogued as `known-bugs` **§540** (cross natives fail a single ≥64 Ki Int allocation).
> **How to finish:** pixel decode + `Image` data → `resize`/`crop`/`rotate` (interop slice), then Phase 2 processing, Phase 3 `kof.vision`; each slice additive, with docs + all-target golden. Rule 6: any new operator/semantics is a maintainer decision; real syntax is `var`/`val` (never `let`/`const`).
> **Decision RESOLVED 29/09 (`D-IMAGE-SURFACE`, maintainer):** the pixel surface **reuses `Raster`** (no new `Image`/`Pixel`/`Color`); codecs are pure Kof wherever feasible and ride JVM **imageio** (through the `kof.image` builtin `image.decode`, an explicit JVM-only commitment) only where a pure-Kof decoder is infeasible, with an honest `IMG001` gap elsewhere — see §34. JPEG interop landed 29/09 (`RasterDecodeE2ETest#jpegDecodesOnJvmViaInterop`). (The original decision request follows for history.)
> **Slice 2a LANDED 29/09 (Kof-first half of the pixel slice):** `decodeRaster(path)` returns a provisional `Raster(format, width, height, channels, samples)` for **uncompressed** formats — PNM `P5`/`P6` and farbfeld — bounded to ≤16384 samples (one read, under the then-open §540 cross-native cap; later raised to 262144, §5); compressed formats stay interop-first behind the decision. Proof: `RasterDecodeE2ETest` 7/7 (PNM/farbfeld golden + unsupported/oversized; JVM + Native x86-64/riscv64 + Script; JS `IOJS001`).
> **Slice 2b LANDED 29/09:** pure-Kof raster operations over the provisional `Raster` — `cropRaster(r,x,y,w,h)` and `resizeNearest(r,w,h)` (nearest-neighbour), output bounded by the same cap; smooth filtering waits for the interop slice. Proof: `RasterDecodeE2ETest` 7/7.
> **Slice 3g LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8lTransforms.kf` (new) + `libs/image/Vp8l.kf` — VP8L **predictor** (14 modes, §3.5.1) and **color** (§3.5.2, `ColorTransformDelta = (s8(t)*s8(c))>>5`) inverse transforms, applied in reverse order; the transform loop now reads `size_bits`/subresolution grids for both and the entropy decode was factored into `vp8lDecodeImage(r,w,h,metaAllowed)` so transform sub-images never read the ARGB-only meta-prefix bit (§3.8.3). Color-indexing transform and meta-Huffman groups still refused with explicit `IMAGE:` diagnostics. Proof: `RasterDecodeE2ETest` 19/19 (`webpVp8lDecodesOn*` incl. a new 8x8 libwebp-generated predictor+color stream **byte-validated against libwebp**, JVM + Native x86-64 + riscv64(qemu) + Script); RED measured on the pre-slice decoder (`IMAGE: WebP predictor transform is not supported yet`).
> **Slice 3h LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8lTransforms.kf` + `libs/image/Vp8l.kf` — VP8L **COLOR_INDEXING transform** (§3.5.4): the palette sub-image (`num_colors = ReadBits(8)+1`, colors delta-coded left-to-right and expanded to `1 << (8 >> bits)` entries) is applied to the entropy image, whose green channel packs `1 << bits` indices of `8 >> bits` bits, least significant first, at the reduced width `ceil(w / 2^bits)`. Fixed a latent **`max_symbol`** bug in `vp8lReadNormal` (`ReadHuffmanCodeLengths` caps the number of decoded code-length *symbols*, not the resulting array length — RFC 9649 §3.6.2.1); the bug only surfaced when the "use length" flag reduced `max_symbol` below the alphabet and desynced the bitstream. Meta-Huffman groups still refused with an explicit `IMAGE:` diagnostic. Proof: `RasterDecodeE2ETest` 19/19 (new 8x8 libwebp-generated 8-color indexing stream, byte-validated against libwebp; JVM + Native x86-64 + riscv64(qemu) + Script); RED measured on the pre-slice decoder (`IMAGE: WebP color-indexing transform is not supported yet`).
> **VP8 lossy slice 2 LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8Frame.kf` + `libs/image/Vp8Probs.kf` (new) — RIFF/`WEBP` walk + full `VP8 ` frame header (RFC 6386 §9/§19): key-frame tag/start code/dimensions, segmentation, loop filter, token-partition count, the six dequant indices and the 1056-entry coefficient-probability table (defaults + updates). `Vp8Bool` gained `signedOrZero`/`bytePosition`. Proof: `Vp8FrameE2ETest` 4/4 vs an independent RFC §19.2 oracle on JVM + Native x86-64 + riscv64(qemu) + Script. Modeled as a class (not a wide record) because the cross backend corrupted calls with ≥9 arguments (`known-bugs` §546, issue #703 — **FIXED 30/09**, `NativeCrossWideArgsE2ETest` 3/3).
> **VP8 lossy slice 3 LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8Frame.kf` now decodes the key-frame macroblock prediction records (RFC 6386 §10/§11): per-macroblock segment id (when `update_mb_segmentation_map`), `mb_skip_coeff` (when `mb_no_skip_coeff`), the luma 16x16 mode, the 16 context-coded luma subblock modes when the mode is `B_PRED` (the 10×10×9 `kf_bmode_prob` indexed by the subblock modes above/left, across macroblock boundaries) and the chroma mode. New `libs/image/Vp8ModeProbs.kf` carries the three fixed tables (`kf_ymode_prob`, `kf_uv_mode_prob`, `kf_bmode_prob`). Proof: `Vp8ModeE2ETest` 4/4 vs an independent RFC §7.3/§10/§11 oracle over three libwebp lossy files (4×4 segment map + `mb_skip_coeff`; all-`B_PRED` 2×2; mixed luma/chroma modes) on JVM + Native x86-64 + riscv64(qemu) + Script — every segment id, skip flag and mode identical. Next slices: DCT coefficient decoding (§13), intra prediction + inverse DCT/WHT (§12/§14), the loop filter (§15).
> **VP8 lossy slice 4 LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8Coeffs.kf` (new) — the token-partition DCT/WHT **coefficient decoder** (RFC 6386 §13.2/§13.3): for every macroblock not marked `mb_skip_coeff`, the Y2/16 Y/4 U/4 V 4×4 blocks are read from the block tree (end-of-block, zero, 1, 2 and 3–4 branches, the three context-coded value nodes and the six category tokens with their fixed extra-bit probabilities `Pcat1..Pcat6`) into `mb*400 + block*16 + zig-zagIndex`, with the end-of-block position per block; the above/left non-zero predictors cross macroblock boundaries and are cleared for a skipped macroblock. `vp8TokenPartition` builds the single token-partition bool decoder and refuses a multi-partition frame with an explicit diagnostic (libwebp always emits one) rather than mis-decoding. Proof: `Vp8CoeffE2ETest` **4/4** vs an independent RFC §7.3/§13 oracle over four real libwebp lossy files (DC-only 16×16, all-`B_PRED` 32×32, a 64×64 with `mb_no_skip_coeff` + skips, and a 64×64 mixing every coefficient category), reproducing the non-empty-block count and signed/absolute coefficient sums of every macroblock on JVM + Native x86-64 + riscv64(qemu) + Script. The oracle's per-position band lookup was cross-checked against the RFC §20.16 reference `tokens.c` (`prob += bands_x[c]`, a single mapping). Next slices: intra prediction + inverse DCT/WHT (§12/§14), the loop filter (§15).
> **VP8 lossy slice 5a LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8Residual.kf` (new) — **dequantization + inverse transforms** (RFC 6386 §14). Every macroblock's quantized coefficients are dequantized with its frame/segment factors (`dc_qlookup`/`ac_qlookup` §14.1; Y2 DC `×2`, Y2 AC `×155/100` min 8, UV DC clamped to 132), the Y2 block is inverted with the inverse Walsh-Hadamard transform (§14.3) and its 4×4 output becomes the DC term of the 16 luma subblocks, then every luma/chroma subblock is inverted with the inverse DCT (§14.4, `20091`/`35468`). The two 128-entry quant tables are built **once** into a `Vp8QuantTables` object (holding them alive across macroblocks — rebuilding the `listOf` per call triggered a riscv64 GC mark race that corrupted the residue; the object form is stable across all targets). Proof: `Vp8ResidualE2ETest` **4/4** vs an independent RFC §14 oracle over four real libwebp lossy files (one `B_PRED` DC-only, a 2×2 all-`B_PRED`, a segmented/skip frame, a frame mixing every category) — the signed/absolute luma/chroma sums of every macroblock identical on JVM + Native x86-64 + riscv64(qemu) + Script. Next: intra prediction (§12) + reconstruction (add the residue to the predicted pixels), then the loop filter (§15).
> **VP8 lossy slice 5 LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8Reconstruct.kf` (new, `vp8Reconstruct`) turns the quantized coefficients into the three reconstructed **pre-loop-filter** planes (RFC 6386 §12/§14) — the residue — per-segment dequantization §14.1, inverse Walsh-Hadamard transform of the Y2 DC block §14.3 and inverse 4×4 DCT §14.4, all in `libs/image/Vp8Residual.kf` (landed as slice 5a) — then 16×16 luma (DC/V/H/TM) + the ten `B_PRED` 4×4 subblock modes §12.3 (`libs/image/Vp8Predict4.kf`) + 8×8 chroma modes §12.2 and the saturating prediction+residue sum §14.5, into padded planes. The `B_PRED` above/right pixels replicate the macroblock top-right pixels down the row (libwebp `top_right[BPS]=…`), the Y-block DC uses the Y2 `(dc[0]+3)>>3` shortcut when only the DC is non-zero, and U/V are predicted independently from their own planes. **Oracle = libwebp itself** decoded with the loop filter disabled (`ffmpeg -skip_loop_filter all`): slice 5 stops before §15, so the exact pre-filter planes are the golden. Proof: `Vp8ReconstructE2ETest` **4/4** — four real libwebp files (`flat16` 16×16 V_PRED + DC-only Y2, `diag32` all-`B_PRED` 32×32, `skip64` mixed 16×16 + skipped MBs, `cat64` mixed modes + every residual) reproduce libwebp's per-plane sample sum and a rolling hash for Y, U and V on JVM + Native x86-64 + riscv64(qemu) + Script. Next slice: the loop filter (§15), after which the full decoder is byte-identical to libwebp.
> **VP8 lossy slice 6 LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8Filter.kf` (new, `vp8LoopFilter`) — the in-loop **deblocking filter** (RFC 6386 §15), the last stage of key-frame reconstruction. Per macroblock it derives the strength from the frame level and the segment override (§15.4: `interior_limit`, `hev_threshold`, the `+4` inter-macroblock edge limit), then filters the left vertical, internal vertical, top horizontal and internal horizontal edges in that order: the 4-tap adjustment (`DoFilter4`/`common_adjust` without outer taps) on inter-sub-block edges, the 6-tap one (`DoFilter6`/`MBfilter`) on inter-macroblock edges, and the simple 2-tap adjustment on high edge variance; chroma is left untouched by the simple filter type. Internal edges are skipped for a macroblock that is neither `B_PRED` nor carries coefficients (§15.1). **Oracle = libwebp itself** with its default filter enabled (plain `ffmpeg` decode): `Vp8FilterE2ETest` **4/4** — the four slice-5 fixtures reproduce libwebp's filtered per-plane sample sum and 24-bit rolling hash (`skip64`/`cat64` are the ones whose planes the filter changes), on JVM + Native x86-64 + riscv64(qemu) + Script; the two pre-filter fixtures are unchanged, matching libwebp. With this slice the pure-Kof VP8 key-frame decoder reproduces libwebp end-to-end.
> **VP8 lossy slice 7 LANDED 01/10 (pure Kof, all targets):** `libs/image/Vp8Raster.kf` (new, `vp8Raster`) routes a lossy WebP to the `Raster` view — the honest boundary is closed. `decodeRaster(path)` now dispatches a `VP8 ` chunk through the whole key-frame chain (frame header → modes → coefficients → dequantization + intra prediction → `vp8LoopFilter`) and converts the filtered YUV 4:2:0 planes to interleaved RGB (BT.601 limited range, nearest chroma: `clip((298*(Y-16) + 409*(V-128) + 128) >> 8)`, the arithmetic shift matching libwebp's fixed point). The planes are read through the reconstruction's own `yAt/uAt/vAt` accessors, so the macroblock-padded stride (`mbCols*16+1`) is handled — the naive `width+1` stride silently corrupts a frame whose width is not a multiple of 16. Proof: `Vp8RasterE2ETest` **4/4** — five real libwebp files (`flat16`, `diag32`, `skip64`, `cat64`, `odd20x28` 20×28) reproduce libwebp's Y/U/V (slice 6) through the documented limited-range matrix; the formula was validated against libwebp's RGB output on solid chroma; JVM + Native x86-64 + riscv64(qemu) + Script, RED-first (`PKG006 import 'image.Vp8Raster' not found`).
> **VP8 lossy slice 8 LANDED 01/10 (pure Kof, all targets):** `libs/image/Vp8Coeffs.kf` (`vp8TokenPartitions`) decodes the **multi-token-partition** key frames (RFC 6386 §9.5) instead of refusing them — the first data partition holds the 3-byte size of each of the first `n-1` partitions and macroblock row `r` uses partition `r % n` (2, 4 or 8 partitions). Fixtures are real libvpx 1.14 encodes (`VP8E_SET_TOKEN_PARTITIONS`) of a 16x128 frame. Proof: `Vp8CoeffE2ETest` **4/4** (eight fixtures: `np2`/`np4`/`np8` reproduce the single-partition coefficient golden), RED-first on JVM + Native x86-64 + riscv64(qemu) + Script.
> **`kof.vision` region descriptors LANDED 30/09 (pure Kof, all targets):** `libs/vision/Regions.kf` (new) adds `componentBoxes(labels, width)` (exact axis-aligned bounding box per component label), `componentAreas(labels)` (pixel count per label) and `labelComponents(labels, width)` (the `List<Component>` object form) — the §12 "regions" / §14 "contour extraction" descriptors, built on `componentLabels`. Proof: `VisionAnalysisE2ETest` **4/4** (the 6×4 two-blob PGM yields `areas=3,4`, `box1=0,0,1,1`, `box2=3,1,4,2`, `regions=2 r1=1@0,0 a3`) on JVM + Native x86-64 + riscv64(qemu) + Script.
> **Slice 3i LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L **meta-Huffman groups** (RFC 9649 §3.7.2.2): `prefix_bits = ReadBits(3)+2`; the entropy image `ceil(w/2^bits) × ceil(h/2^bits)` is entropy-decoded, each pixel's red/green bytes give its group index, one prefix-code group is read per distinct value, and each pixel selects its group by `entropy[(y>>bits)*xw + (x>>bits)]` (the LZ77 copy is not clamped to group blocks, matching libwebp). This was the last VP8L refusal — the whole VP8L lossless path now decodes. Proof: `RasterDecodeE2ETest` 19/19 (new 8x8 two-group libwebp-generated stream, byte-validated against libwebp; JVM + Native x86-64 + riscv64(qemu) + Script); RED measured on the pre-slice decoder (`IMAGE: WebP meta-Huffman groups are not supported yet`).
> **Slice 3f LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L **color cache** (RFC 9649 §3.6.2.3: `color_cache_code_bits` 1..11, slot `(0x1e35a7bd * argb) >> (32 - bits)`, every literal/copied pixel inserted in stream order, `S >= 256+24` reads the cache). The green prefix code alphabet is now `256+24+cache_size`. Decodes subtract-green/color-cache/single-group streams. Predictor/color/indexing transforms and meta-Huffman still refused with explicit `IMAGE:` diagnostics. Proof: `RasterDecodeE2ETest` 19/19 (`webpVp8lDecodesOn*` incl. a new 8x8 color-cache stream **generated by libwebp and byte-validated against it**, JVM + Native x86-64 + riscv64(qemu) + Script); RED measured on the pre-slice decoder (`IMAGE: WebP color cache is not supported yet`).
> **Slice 3e LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L **normal (code-length) Huffman codes** + **LZ77 backward references** (length/distance prefix extra bits + the §3.6.2.2.1 distance map). Decodes the subtract-green/no-cache/single-group subset that real libwebp emits. Predictor/color/indexing transforms, color cache and meta-Huffman still refused with explicit `IMAGE:` diagnostics. Proof: `RasterDecodeE2ETest` 19/19 (`webpVp8lDecodesOn*` incl. a hand-built normal-Huffman+LZ77 stream, libwebp-validated, JVM + Native x86-64 + riscv64(qemu) + Script). Re-test of the VP8L native face after the §541/§543 fixes.
> **Slice 3d LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L transform loop + **SUBTRACT_GREEN** inverse; predictor/color/indexing, cache, meta and LZ77 still refused with explicit `IMAGE:` diagnostics.
> **Slice 3c LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L core (bit reader + simple Huffman + literals); transforms/cache/meta/LZ77 refused with explicit `IMAGE:` diagnostics. Proof: `RasterDecodeE2ETest` 19 run/0F (`webpVp8lDecodesOn*` on JVM + Native x86-64/riscv64 + Script).
> **Slice 3b LANDED 29/09 (pure Kof, all targets):** `libs/image/Gif.kf` decodes the first GIF frame (Kof LZW, global/local palette, interlaced) to RGB. Proof: `RasterDecodeE2ETest` 15 run/0F (`gifDecodesOn*` on JVM + Native x86-64/riscv64 + Script).
> **Slice 2f LANDED 29/09 (pure Kof, parity — no gap):** `decodeRaster` decodes **QOI** (all chunks: RGB/RGBA/diff/luma/run/index) in Kof, so a compressed-format decode ships on every target. Decision `D-IMAGE-SURFACE` (reuse `Raster`; pure Kof when feasible, JVM imageio only where infeasible) + §34 TODO recorded. Proof: `RasterDecodeE2ETest` 7/7 (QOI golden incl. a RUN chunk; JVM + Native x86-64 + riscv64 + Script).
> **Slice 2c LANDED 29/09:** `flipHorizontal`, `flipVertical` and `rotate90` (clockwise, dimensions swap) over the provisional `Raster`. Proof: `RasterDecodeE2ETest` 7/7.
> **Slice 2d LANDED 29/09:** `decodeRaster` also decodes uncompressed **BMP** 24/32-bit (BGR rows padded to 4 bytes, bottom-up or top-down, alpha dropped). Proof: `RasterDecodeE2ETest` 7/7.
> **Slice 2e LANDED 29/09 (processing overlap):** `grayscale` (BT.601), `threshold(level)` and `boxBlur` (3x3, clamped borders) over the `Raster`. Proof: `RasterDecodeE2ETest` 7/7.

## Objective

Create native Kof support for **image manipulation and computer
vision**, through own idiomatic APIs, integrated with the language and
stdlib architecture.

The project splits conceptually into:

```text
kof.image  → image manipulation and processing
kof.vision → computer vision and visual analysis
```

`kof.file` remains responsible for files and storage formats.

The responsibility of `kof.image` and `kof.vision` starts from the
already-loaded image data.

---

# FUNDAMENTAL RULE — KOF IS KOF

Before implementing anything:

1. Read the current Kof grammar.
2. Read real examples in the project.
3. Consult existing stdlib APIs.
4. Consult the type system.
5. Consult the current arrays/buffers model.
6. Consult the memory model.
7. Consult the existing targets.
8. Consult the module system.
9. Run the current tests.

Do not invent syntax.

Kof uses `var`.

Do not use: `let`, `const`, JavaScript variations, Python syntax, Kotlin
syntax.

Do not turn the API into a DSL inspired by another language.

All examples in this document are conceptual and must be adapted to the
real Kof syntax before being implemented.

---

# 1. Architecture

The desired architecture is:

```text
kof.file
    │  bytes / stream / file
    ▼
kof.image
    ├── Image  ├── Pixel  ├── Color  ├── ImageBuffer
    ├── ImageIO  ├── Transform  └── Processing
    ▼
kof.vision
    ├── Detection  ├── Features  ├── Segmentation
    ├── Tracking   ├── Geometry  ├── OCR  └── ML integration
```

The final structure must follow Kof's existing architecture.

Do not create modules just to reproduce this tree literally.

---

# 2. `kof.image`

`kof.image` must provide its own abstraction for images.

Conceptually:

```text
Image
├── width
├── height
├── format
├── channels
├── pixels
└── metadata
```

The internal representation must be efficient and adequate to the
targets.

---

# 3. Image formats

Progressively support common formats: PNG, JPEG, WebP, GIF, BMP, TIFF.

The first implementation does not need to support all of them.

Prioritize the most-used formats and those with mature libraries
available.

---

# 4. Reading and writing

Integrate with `kof.file`.

Conceptually:

```text
arquivo → kof.file → bytes/stream → kof.image → Image
Image → kof.image → encoder → kof.file → arquivo
```

The image API must not need to know filesystem details.

---

# 5. Pixels

Provide pixel access when needed.

Support representations like: RGB, RGBA, Grayscale.

Evaluate later: BGR, BGRA, YUV, HSV, Lab.

Do not create dozens of pixel formats in the first version.

---

# 6. Basic operations

Implement progressively:

* resize; crop; rotate; flip; transpose; scale; padding;
* composition; format conversion; channel conversion; grayscale;
* brightness; contrast; saturation; alpha; normalization.

The API should favor composable operations.

---

# 7. Image processing

Add classic processing operations:

```text
Blur / Gaussian Blur / Median Blur / Sharpen
Threshold / Adaptive Threshold / Edge Detection
Morphology / Convolution / Histogram / Equalization
```

Prioritize classic, well-defined algorithms.

Do not add algorithms just to increase the feature count.

---

# 8. Geometry

Create own types when needed:

```text
Point  Size  Rect  Circle  Line  Polygon  Contour
```

These structures must be reusable by `kof.image` and `kof.vision`.

---

# 9. Masks

Support image masks.

Conceptual example:

```text
Image + Mask → Operation → Image
```

Enable: selection; composition; cropping; mathematical operations;
localized processing.

---

# 10. Histograms

Provide histogram infrastructure.

Allow: per-channel histograms; grayscale; distribution; equalization;
statistical analysis.

This is useful for both processing and computer vision.

---

# 11. `kof.vision`

`kof.vision` must be responsible for computer-vision algorithms.

The API must work over `Image` and the geometric structures of
`kof.image`.

---

# 12. Detection

Support progressively: edges; lines; circles; contours; regions;
objects; features.

The first implementation must prioritize classic algorithms.

---

# 13. Feature detection

Evaluate support for:

```text
Corners  Keypoints  Descriptors  Feature Matching
```

Possible algorithms:

```text
Harris  FAST  ORB  SIFT
```

The choice must consider: license; performance; maturity; real need;
availability per target.

Do not implement everything simultaneously.

---

# 14. Segmentation

Add progressively:

* thresholding; binary segmentation; connected components; region
  growing; contour extraction; watershed when appropriate.

The API must produce structures reusable by other operations.

---

# 15. Tracking

Evaluate support for tracking objects/regions in image sequences.

Possible components:

```text
Tracker  Frame  Region  Object  Trajectory
```

Do not implement tracking before there is adequate infrastructure for
frames and incremental processing.

---

# 16. Camera

Create an abstraction for frame capture when the target allows it.

Conceptually:

```text
Camera → Frame stream → Image → Vision pipeline
```

It must support: open; close; resolution; FPS; capture; streaming;
resource control.

Do not block the main thread unnecessarily.

Do not create naive infinite loops.

A target without adequate support: document the limitation (gap
`XXX00x`, R6) instead of a fake implementation.

---

# 17. Pipelines

One of the important features of `kof.vision` must be operation
composition.

Conceptually:

```text
Camera → Frame → Resize → Grayscale → Blur → Edge Detection → Contour Detection → Result
```

The model must allow efficient pipelines without creating unnecessary
image copies.

Evaluate:

* reusable buffers;
* in-place operations when safe;
* lazy processing;
* operation fusion;
* streaming.

Do not implement complex optimizations before having benchmarks.

---

# 18. OCR

Evaluate integration with OCR.

The first version does not need to implement its own OCR.

It may use a mature external engine, isolated behind a Kof API.

Conceptually:

```text
Image → OCR → Text
```

Future possibilities:

* bounding boxes; confidence; lines; words; characters; language.

---

# 19. QR Code

`kofqrcode` must remain a specific module.

But there must be natural integration with `kof.image` and, in the
future, `kof.vision`.

Architecture:

```text
kof.image → Image → kofqrcode → QR Result
```

Do not duplicate image decoder/encoder inside `kofqrcode`.

---

# 20. Machine Learning

`kof.vision` must leave room for future integration with ML models.

Do not create an entire ML framework inside this module.

The initial responsibility may be:

```text
Image → Tensor/Buffer → Model → Inference → Detection/Classification/Segmentation
```

Evaluate later integration with runtimes such as:

* ONNX Runtime;
* TensorFlow Lite;
* other adequate runtimes.

The public API must remain independent of the runtime used.

---

# 21. Object detection

In the future:

```text
Image → Object Detector → Detection[]
```

Each detection may conceptually have:

```text
class  confidence  boundingBox
```

The data model must be simple and reusable.

---

# 22. Classification

Support in the future:

```text
Image → Classifier → Classification[]
```

With: class; confidence; optional metadata.

---

# 23. Semantic segmentation

Plan future support for:

```text
Image → Segmentation Model → Mask
```

Reusing the mask abstractions that already exist.

---

# 24. Performance

Computer vision can be extremely intensive.

Design considering:

* SIMD; reusable buffers; contiguous memory; in-place operations;
  zero-copy when possible; parallel processing; GPU when available;
  specific accelerators; WASM SIMD; Native SIMD.

Do not sacrifice the clean API in the name of micro-optimizations.

---

# 25. Targets

Evaluate progressively:

```text
JVM  Native  JS  WASM
```

### JVM

May use mature libraries when needed.

### Native

Prioritize performance and efficient memory access.

### JS

Support operations compatible with the browser.

### WASM

Explore:

* WASM SIMD;
* local processing;
* image pipelines;
* inference when there is an adequate runtime.

Do not promise artificial parity between targets.

Document clearly the support of each API.

---

# 26. Security

Consider:

* malformed images; giant files; decompression bombs; dimension
  overflow; invalid buffers; corrupted formats; excessive memory
  consumption; untrusted models; camera input; processing of external
  data.

Do not trust images received from external sources.

---

# 27. Dependencies

Do not implement complex codecs or algorithms from scratch when there
are mature, adequate libraries.

But:

**the dependency must not leak into Kof's public API.**

For example, the user must not need to know a specific class of an
external library to work with `Image`.

The external library is an implementation detail.

Evaluate:

* license; maturity; security; maintenance; performance; size;
  compatibility with targets.

---

# 28. Tests

Create tests for:

## Image

* open; save; resize; crop; rotate; grayscale; conversion; channels; pixels; metadata.

## Processing

* blur; threshold; edge detection; morphology; histogram.

## Vision

* contours; lines; circles; features; segmentation.

## Camera

* open; capture; lifecycle; close.

## OCR

* recognition; bounding boxes; errors.

## QR Code

* integration with `kof.image`; read; generate.

---

# 29. Integration tests

Create real pipelines.

Conceptual examples:

```text
Image file → kof.file → kof.image → grayscale → threshold → kof.vision → contours → result
Camera → Image → Vision → Detection
Image → QR Code Reader → Text
```

---

# 30. Benchmarks

Add benchmarks for critical operations:

* decode; encode; resize; grayscale; blur; edge detection;
  convolution; segmentation; feature detection.

Compare:

* image size; time; memory; throughput.

Do not make performance claims without a benchmark.

---

# 31. Incremental implementation

Do not try to create the whole computer-vision stack at once.

### Phase 1

```text
kof.image
├── Image
├── Pixel
├── Color
├── ImageIO
└── resize/crop/rotate
```

### Phase 2

```text
processing
├── grayscale
├── blur
├── threshold
├── histogram
└── edges
```

### Phase 3

```text
kof.vision
├── contours
├── lines
├── circles
├── geometry
└── segmentation
```

### Phase 4

```text
camera  tracking  features  OCR
```

### Phase 5

```text
ML  object detection  classification  semantic segmentation  GPU acceleration
```

The order may change according to the architecture and the existing
targets.

---

# 32. Architecture criteria

Do not turn `kof.image` into:

* an OpenCV clone;
* an ML framework;
* a graphics library;
* an image editor;
* a giant wrapper of external libraries.

`kof.image` must handle **images**.

`kof.vision` must handle **computer vision**.

External runtimes must remain implementation details.

---

---

# 34. TODO — what is missing (implementation plan, 29/09)

Decision **`D-IMAGE-SURFACE`** (maintainer 29/09): the value surface **reuses
`Raster`** (no new `Image`/`Pixel`/`Color` types); the codecs are **pure Kof
whenever feasible** (full cross-target parity, zero gaps) and fall back to the
JVM **imageio** interop only where a pure-Kof decoder is technically
infeasible. No gap is added "just to add one" — it exists only where the
ability genuinely does not exist on a target.

**Done (pure Kof, all targets):**
- metadata for 17 formats (`Image.kf`);
- raw decode: PNM `P5`/`P6`, farbfeld, BMP 24/32-bit, **QOI** (all chunks), **TIFF** (both byte orders, 8-bit grayscale/RGB/RGBA/gray-alpha/palette, multi-strip, PackBits);
- ops: `cropRaster`, `resizeNearest`, `flipHorizontal`/`flipVertical`,
  `rotate90`, `grayscale`, `threshold`, `boxBlur`;
- encode: `encodeRaster`/`writeRaster` for PNM/farbfeld/BMP/QOI;
- vision: `histogram`/`normalizedHistogram`/`otsuLevel`/`otsuBinarize`, `equalizationLut`/`equalizeRaster`, `sobelMagnitude`, `componentLabels`/`componentCount`, `erode`/`dilate`/`openRaster`/`closeRaster`.

**Missing — ordered by cost:**

1. **PNG decode (pure Kof) — LANDED 29/09 on JVM/riscv64/Script/x86-64 (`known-bugs` §541, fixed 29/09: x86 heap returned dirty reused memory, now zeroed).**
   - Files: `libs/image/Png.kf` (new), `libs/image/Raster.kf` (`decodeRaster`
     dispatch `fmt == "PNG"`).
   - Work: `IHDR` parse (color type 0/2/3/4/6, bit depth 8), `IDAT`
     concatenation, **zlib inflate** (DEFLATE: stored/fixed/dynamic Huffman)
     in pure Kof, per-scanline filters 0–4 (None/Sub/Up/Average/Paeth),
     de-palette (`PLTE`) and expand to the `Raster` channels.
   - Proof: `RasterDecodeE2ETest#pngDecodesOnJvm` (known-good PNG bytes → golden samples) on JVM +
     Native x86-64 + riscv64 + Script; JS `IOJS001` (the library still uses
     `readRange`). No new gap: the decoder is target-independent.
   - Risk: inflate correctness; mitigate with a fixed/dynamic-block golden and
     the zlib Adler-32 check (ignore trailing, must not crash).
2. **JPEG decode (infeasible pure Kof → JVM imageio interop) — LANDED 29/09.**
   - `kof.image` platform builtin `image.decode(path): Int[]` (layout
     `[w,h,samples…]`) + JVM runtime `JvmImageRuntime` via
     `javax.imageio.ImageIO`; `libs/image/Jpeg.kf` wraps it as
     `decodeJpegRaster(path): Raster` (JPEG, RGB/RGBA); the wrapper binds the
     result to an explicit `Int[]` local (`var` on the call inferred an
     `Unknown[]` element in the emit — measured, harmless once typed). Other targets:
     honest **`IMG001`** compile-time gap in the namespace lowering
     (`ExpressionMethodCallLowerer`), never a silent fallback. Because the
     builtin exists only on the JVM, importing `image.Jpeg` is the explicit
     JVM-only commitment; the gap-free pure-Kof formats in `Raster.kf` stay
     untouched. Registered in the stdlib ledger (`platform`,
     `experimental`) and pinned in the parity matrix.
   - Proof: `RasterDecodeE2ETest#jpegDecodesOnJvmViaInterop` (JVM golden ==
     an ImageIO-decoded fixture) + `#jpegOnNonJvmIsImg001` (JS refuses with
     `IMG001`) + `DomainGapCodesTest#imageDecodeOnJsIsImg001`.
3. **GIF — LANDED 29/09 (pure Kof, all targets).** `libs/image/Gif.kf` decodes the first frame with a Kof LZW (variable width 2–12, KwKwK), global/local palette and interlaced rows, RGB output.
   **WebP VP8L — slices A–I LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` + `libs/image/Vp8lTransforms.kf` decode the full VP8L lossless path: **simple and normal (code-length) Huffman**, **LZ77 backward references**, the **color cache**, the **predictor + color inverse transforms** (14 predictor modes and the §3.5.2 color delta, applied in reverse order), the **COLOR_INDEXING transform** (§3.5.4) and **meta-Huffman groups** (§3.7.2.2; RFC 9649 §3.5/§3.6.2.1/§3.6.2.2/§3.7). Fixtures are libwebp-validated (the hand-built normal-Huffman+LZ77 stream, a libwebp-generated 8x8 color-cache stream, a libwebp-generated 8x8 predictor+color stream, a libwebp-generated 8x8 8-color indexing stream and a libwebp-generated 8x8 two-group meta-Huffman stream, all byte-matched by PIL); the VP8L native face was re-tested green after the §541/§543 native fixes. No VP8L machinery remains refused. Next: WebP lossy (`VP8 `) and AVIF still pending (interop/gap).
4. **Encode/write — LANDED 30/09 (pure Kof, all targets).** `libs/image/Encode.kf`
   adds `encodeRaster(r, format): Int[]` and `writeRaster(path, r, format): Bool`
   for **PNM `P5`/`P6`**, **farbfeld**, **BMP** 24-bit and **QOI** (full
   encoder: RUN/INDEX/DIFF/LUMA/RGB/RGBA + end marker), so a raster can be
   written back on every target. Proof: `RasterEncodeE2ETest` **4/4** — a
   decode → encode → decode round-trip is byte-identical on JVM + Native x86-64
   + riscv64(qemu) + Script, the emitted BMP is independently read by
   `javax.imageio`, and the re-decoded QOI/PNM/farbfeld match the source
   samples. No new gap: pure Kof, full parity (JS inherits `IOJS001` through
   the `kof.io` write).
5. **Larger rasters — LANDED 30/09 (post-`§540`/`§542`).** The cap that was
   deliberately held at 16384 samples until the native allocation fixes landed
   is raised to **262144** (a 1 MiB `Int` array, fitting the cross-native
   16 MiB arena of `known-bugs` §540 and the x86-64 contiguous arena of §542).
   Proof: `RasterDecodeE2ETest#largeRasterAboveOldCapDecodes` (JVM) and
   `#largeRasterAboveOldCapDecodesOnNativeRiscv64` (riscv64/qemu) decode a
   200×200 P6 (120 000 samples, far past both the old 16384 cap and the old
   256 KiB cross arena) plus the bumped `#oversizedRasterThrows` (400×400).
   The VP8L path now shares the same guard (`libs/image/Vp8l.kf` calls
   `guardRaster(pixels * 4)` instead of its own 16384-pixel cap), verified on
   JVM and Native x86-64 with a libwebp-generated 160×120 lossless WebP
   (`#largeWebpAboveOldPixelCapDecodesOnJvm`/`...OnNativeX86`); Native riscv64
   and aarch64 now decode the same fixture correctly — §544 FIXED 03/10
   (owner = native/GC lane `192.168.15.101:9092`, issue #700); the cross tests
   are `#largeWebpOnNativeRiscv64Decodes` / `#largeWebpOnNativeAarch64Decodes`.

6. **`kof.vision` slice 1 — LANDED 30/09 (pure Kof, all targets).** New
   `libs/vision/` package opens the vision front: `histogram(r): Int[256]`
   (BT.601 luminance bins, same rule as `image.grayscale`),
   `normalizedHistogram(r): Double[256]` (bins as probabilities) and
   `otsuLevel(r): Int` + `otsuBinarize(r): Raster` (Otsu 1979 optimal global
   threshold and its black/white raster, alpha preserved — §14's first
   segmentation primitive). Built on the shared `image.Raster`
   (`D-IMAGE-SURFACE`); deterministic, O(256) after the histogram, no interop,
   no ML. Proof: `VisionAnalysisE2ETest` **4/4** — a hand-built bimodal PGM
   (10×30, 6×220) yields `hist=6,10`, `norm=375`, `otsu=30`,
   `bw=0,0,255` byte-identically on JVM + Native x86-64 + riscv64(qemu) +
   Script. Next vision slices (edges/contours, §12) are additive.

7. **`kof.vision` slice 2a — Sobel edges — LANDED 30/09 (pure Kof, all targets).**
   `libs/vision/Edges.kf` adds `sobelMagnitude(r): Raster` (a single-channel
   `"SOBEL"` raster) and `sobelValues(r): Double[]` — the classic Sobel
   gradient magnitude of the BT.601 luminance, borders 0. The square root is a
   deterministic Newton iteration (no libm), so the result is byte-identical on
   every target. §12's first detection primitive. Proof: `VisionAnalysisE2ETest`
   **4/4** — a 5×5 PGM with a single interior 255 and a 5×5 "cross" ramp give
   the exact magnitudes (`edge=98`, centre `0`, borders `0`) on JVM + Native
   x86-64 + riscv64(qemu) + Script.

8. **`kof.vision` slice 2b — connected components — LANDED 30/09 (pure Kof,
   all targets).** `libs/vision/Components.kf` adds `componentLabels(r): Int[]`
   (4-connected labeling of the non-zero luminance, 0 = background, iterative
   LIFO flood fill — no recursion) and `componentCount(labels): Int` (§14's
   "connected components"). Proof: `VisionAnalysisE2ETest` **4/4** — a 6×4 PGM
   with two disjoint blobs gives `comp=2 a=1 b=2 bg=0` on JVM + Native x86-64 +
   riscv64(qemu) + Script.

9. **`kof.vision` processing slice — morphology — LANDED 30/09 (pure Kof, all
   targets).** `libs/vision/Morphology.kf` adds `erode(r)`/`dilate(r)` (3x3
   square element, minimum/maximum over every channel, alpha preserved, borders
   clamped), plus the compositions `openRaster(r)` (erode→dilate) and
   `closeRaster(r)` (dilate→erode) — plan §Processing. Proof:
   `VisionAnalysisE2ETest` **4/4** — a 5×5 PGM with one isolated 255 gives
   `erode=0 dilate=255,255`, `open=0 close=255` on JVM + Native x86-64 +
   riscv64(qemu) + Script.

10. **`kof.vision` processing slice — histogram equalization — LANDED 30/09
    (pure Kof, all targets).** `libs/vision/Histogram.kf` adds
    `equalizationLut(r): Int[256]` (the cumulative-distribution remap) and
    `equalizeRaster(r): Raster` (applies it to every colour channel, alpha
    preserved; a uniform raster maps to all-0). Proof: `VisionAnalysisE2ETest`
    **4/4** — a 16-pixel low-contrast PGM (60/200) stretches to `eqLow=0,255`,
    `out=0,255`, and a 64-pixel six-level ramp maps to `eqSix=47,94,141,188`,
    on JVM + Native x86-64 + riscv64(qemu) + Script.

11. **VP8 lossy slice 1 — boolean range decoder — LANDED 30/09 (pure Kof,
    all targets).** `libs/image/Vp8.kf` adds `Vp8Bool`, the entropy decoder
    shared by every VP8 partition (RFC 6386 §7.3): `bit(prob)` (one bool at
    `prob/256`) and `literal(n)` (an `n`-bit value at 1/2). All arithmetic stays
    within 17 bits, so a 32-bit `Int` is exact on every backend. Proof:
    `Vp8BoolE2ETest` **4/4** — an **independent RFC §7.3 encoder** (offline
    Python) writes 64 bools at a fixed seed over an 8-probability pattern into a
    20-byte partition, and the Kof decoder reproduces the exact sequence
    (`vp8bool=1101…0010`) byte-for-byte on JVM + Native x86-64 + riscv64(qemu) +
    Script (no compiler change). Next slices: RIFF/`VP8 ` container + frame
    header, then per-macroblock modes/coefficients, intra prediction + inverse
    DCT, and the loop filter.

12. **VP8 lossy slice 2 — RIFF/`VP8 ` container + frame header — LANDED 30/09
    (pure Kof, all targets).** `libs/image/Vp8Frame.kf` (new) walks the RIFF/
    `WEBP` envelope, extracts the `VP8 ` chunk and parses the uncompressed
    chunk (§9.1: frame tag, key-frame start code, 14-bit dimensions) plus the
    whole frame header (§9.2–§9.11): color space/clamp, segmentation, loop
    filter type/level/sharpness and per-macroblock delta groups, token-partition
    count, the six dequant indices, `refresh_entropy`, the full `[4][8][3][11]`
    coefficient-probability table (defaults + per-frame updates) and
    `mb_no_skip_coeff`/`prob_skip_false`. `libs/image/Vp8Probs.kf` (new) carries
    the two RFC tables (`§13.4` update probs, `§13.5` defaults); `Vp8Bool` gained
    `signedOrZero(n)` (RFC `bool_maybe_get_int`) and `bytePosition()`. Modeled
    as a **single-argument-constructor class** rather than a wide record: the
    riscv64/aarch64 cross backend corrupted calls with ≥9 arguments (measured,
    catalogued as `known-bugs` **§546** / issue **#703**; **FIXED 30/09** — the
    parser was kept inside the verified arity at the time, and a wide record may
    now be revisited), so the parser stayed green on every target. Proof:
    `Vp8FrameE2ETest` **4/4** against an **independent RFC §19.2 parser**
    (offline Python) over a real libwebp 8×8 lossy file — identical
    `w=8,h=8,lf=3,qi=9,parts=1,pos=13,sum=174173` (the 1056-entry table sum,
    including the 3 per-frame updates) on JVM + Native x86-64 + riscv64(qemu) +
    Script (no compiler change). Next slices: per-macroblock modes/coefficients
    (§11/§13), intra prediction + inverse DCT/WHT (§12/§14), the loop filter
    (§15).

13. **VP8 lossy slice 3 — key-frame macroblock prediction records — LANDED
    30/09 (pure Kof, all targets).** `libs/image/Vp8Frame.kf` parses the first
    data-partition macroblock records (RFC 6386 §10/§11): the per-macroblock
    segment id when `update_mb_segmentation_map` is set (3-probability tree),
    `mb_skip_coeff` when `mb_no_skip_coeff` is set, the luma 16x16 mode
    (`kf_ymode_tree`), and when it is `B_PRED` the 16 luma subblock modes using
    the context-dependent 10×10×9 `kf_bmode_prob` (context from the subblocks
    above and to the left, including the neighbouring macroblocks, with the
    16x16 mode mapped to a constant subblock mode), then the chroma mode
    (`uv_mode_tree`). New `libs/image/Vp8ModeProbs.kf` carries `kf_ymode_prob`,
    `kf_uv_mode_prob` and `kf_bmode_prob`; `vp8Tree` walks any RFC bool tree.
    Proof: `Vp8ModeE2ETest` **4/4** against an **independent RFC §7.3/§10/§11
    parser** (offline Python) over three real libwebp lossy files — a 4×4
    segment-mapped frame with `mb_no_skip_coeff=1` (`seg64`), a 2×2 all-`B_PRED`
    frame whose subblock modes are context-coded (`diag32`) and a 4×4 frame
    mixing every luma/chroma mode (`mix`) — reproducing every segment id, skip
    flag, luma mode, subblock mode and chroma mode on JVM + Native x86-64 +
    riscv64(qemu) + Script (no compiler change). Next slices: intra prediction
    + inverse DCT/WHT (§12/§14), the loop filter (§15).

14. **VP8 lossy slice 4 — DCT/WHT coefficient decoder — LANDED 30/09 (pure
    Kof, all targets).** `libs/image/Vp8Coeffs.kf` (new) walks the token
    partition(s) and decodes the quantized residue of every macroblock that was
    not `mb_skip_coeff` (RFC 6386 §13.2/§13.3). The block tree is read with the
    three context-coded value nodes and the six category tokens, each category
    carrying its fixed extra-bit probabilities (`Pcat1..Pcat6`) and a trailing
    sign bit; the result lands in `coeffs` at `mb*400 + block*16 + zigzag` with
    the end-of-block position stored per block. The above/left non-zero
    predictors are indexed by `left_context_index`/`above_context_index` (the
    Y2 predictor keeps the most recent macroblock that has a Y2 block) and are
    cleared for a skipped macroblock. `vp8TokenPartition` builds the single
    token-partition bool decoder; a frame that splits its residue across more
    than one partition is refused with an explicit diagnostic (the available
    libwebp encoders always emit one) instead of a silent wrong decode. Proof:
    `Vp8CoeffE2ETest` **4/4** against an **independent RFC §7.3/§13 coefficient
    decoder** (offline Python) over four real libwebp lossy files — a DC-only
    16×16, an all-`B_PRED` 32×32, a 64×64 with `mb_no_skip_coeff=1` and many
    skipped macroblocks, and a 64×64 mixing every coefficient category —
    reproducing the non-empty-block count and the signed/absolute coefficient
    sums of every macroblock on JVM + Native x86-64 + riscv64(qemu) + Script
    (no compiler change). The oracle's per-position band lookup was cross-checked
    against the RFC §20.16 reference `tokens.c` (`prob += bands_x[c]`, a single
    mapping; the Kof decoder applies it once). Next slice: intra prediction +
    inverse DCT/WHT (§12/§14), then the loop filter (§15).

15. **VP8 lossy slice 5a — dequantization + inverse transforms — LANDED 30/09
    (pure Kof, all targets).** `libs/image/Vp8Residual.kf` (new) converts the
    quantized residue produced by slice 4 into the prediction-free residue of
    every macroblock (RFC 6386 §14). For each macroblock it derives the six
    dequantization factors from its frame quantizer and segment quantizer
    (§14.1): the `dc_qlookup`/`ac_qlookup` tables feed Y DC/AC, Y2 DC (`×2`) and
    AC (`×155/100`, minimum 8), and the chroma DC/AC (DC clamped to 132). The Y2
    block is inverted with the inverse Walsh-Hadamard transform (§14.3) and its
    4×4 output becomes the DC coefficient of each of the 16 luma subblocks;
    every luma and chroma subblock is then inverted with the inverse DCT (§14.4,
    fixed-point `20091`/`35468`). Results are stored as `y` (16×16 per
    macroblock) and `u`/`v` (8×8), with `B_PRED` macroblocks taking their DC
    directly from the coefficient stream (no Y2). The two 128-entry quant tables
    are built **once** inside a `Vp8QuantTables` object shared by the decode:
    rebuilding the `listOf` on each dequantization call left a deep-live
    temporary that the riscv64 collector marked race-collected and corrupted the
    working arrays (a native GC finding, worked around structurally in pure
    Kof). Proof: `Vp8ResidualE2ETest` **4/4** against an **independent RFC §14
    oracle** (offline Python) over four real libwebp lossy files — a DC-only
    `B_PRED` frame, an all-`B_PRED` 32×32, a segment-mapped/skip 64×64, and a
    64×64 mixing every coefficient category — reproducing the signed and
    absolute sums of the luma and chroma residue planes of every macroblock on
    JVM + Native x86-64 + riscv64(qemu) + Script (no compiler change). Next
    slice: intra prediction (§12) + reconstruction (add the residue to the
    predicted pixels), then the loop filter (§15).
16. **`kof.vision` slice 2c — connected-component regions — LANDED 30/09 (pure
    Kof, all targets).** `libs/vision/Regions.kf` adds the reusable region
    descriptors of §12 ("regions") / §14 ("contour extraction"):
    `componentBoxes(labels, width): List<ComponentBox>` (one exact axis-aligned
    bounding box per component label, index 0 = background),
    `componentAreas(labels): Int[]` (pixel count per label) and
    `labelComponents(labels, width): List<Component>` (the object form — a
    `Component(label, box, area)` per region, in label order). Built on
    `componentLabels`; deterministic, no interop, all targets. Proof:
    `VisionAnalysisE2ETest` **4/4** — the 6×4 two-blob PGM yields
    `areas=3,4`, `box1=0,0,1,1`, `box2=3,1,4,2` and `regions=2 r1=1@0,0 a3`
    on JVM + Native x86-64 + riscv64(qemu) + Script (same class extends the
    histogram/Otsu/Sobel/components/morphology golden).

17. **VP8 lossy slice 5 — intra prediction + inverse DCT/WHT — LANDED 30/09
    (pure Kof, all targets).** `libs/image/Vp8Reconstruct.kf` (`vp8Reconstruct`)
    reconstructs the three **pre-loop-filter** key-frame planes from the
    coefficients of slice 4 (RFC 6386 §12/§14). Split by responsibility: it builds on the slice-5a residue
    `libs/image/Vp8Residual.kf` (§14.1 dequantization + §14.3/§14.4 inverse
    WHT/DCT) and adds `libs/image/Vp8Predict4.kf` (the ten `B_PRED` 4×4 modes,
    §12.3) and the reconstruction driver (16×16 DC/V/H/TM §12.3, 8×8 chroma
    §12.2, prediction+residue sum §14.5). The `B_PRED` above/right samples
    replicate the macroblock's top-right pixels down the row (libwebp
    `top_right[BPS] = top_right[2*BPS] = …`), the `B_PRED` cells read the row
    above the subblock / the column to its left in the frame buffer, the Y-block
    DC is the inverse-WHT output (or the `(dc[0]+3)>>3` shortcut when only the
    Y2 DC is non-zero), and U/V predict independently from their own planes.
    **Oracle = libwebp itself**, decoded with the loop filter disabled
    (`ffmpeg -skip_loop_filter all`): slice 5 stops at §14.5, so libwebp's exact
    pre-filter planes are the golden. Proof: `Vp8ReconstructE2ETest` **4/4** —
    four real libwebp files (`flat16` 16×16 V_PRED + DC-only Y2, `diag32`
    all-`B_PRED` 32×32, `skip64` mixed 16×16 + skipped macroblocks, `cat64`
    mixed modes + every residual) match libwebp's per-plane sample sum and a
    24-bit rolling hash for Y, U and V on JVM + Native x86-64 + riscv64(qemu) +
    Script; RED-first (`PKG006 import 'image.Vp8Reconstruct' not found`) on the
    pre-slice tree. Next slice: the loop filter (§15), which completes the
    byte-exact VP8 decoder.

18. **VP8 lossy slice 6 — loop filter — LANDED 30/09 (pure Kof, all targets).**
    `libs/image/Vp8Filter.kf` (`vp8LoopFilter`) applies the in-loop **deblocking
    filter** (RFC 6386 §15) to the reconstructed planes, the final key-frame
    stage. Per macroblock it derives the strength from the frame
    `loop_filter_level` plus the segment override when segmentation is absolute
    or delta (§15.4, `interior_limit`, the `hev_threshold` key-frame ladder and
    the `+4` inter-macroblock edge limit), then filters the left vertical,
    internal vertical, top horizontal and internal horizontal edges in that
    order: the 4-tap `DoFilter4` (simple adjustment without outer taps, plus the
    two inner pixels moved half as far) on inter-sub-block edges, the 6-tap
    `DoFilter6` (`MBfilter`) on inter-macroblock edges, and the 2-tap
    `common_adjust` on high edge variance; the simple filter type only touches
    luma, and internal edges are skipped for a macroblock that is neither
    `B_PRED` nor carries coefficients (§15.1). **Oracle = libwebp itself** with
    its default filter enabled (plain `ffmpeg` decode). Proof:
    `Vp8FilterE2ETest` **4/4** — the four slice-5 fixtures reproduce libwebp's
    filtered per-plane sample sum and 24-bit rolling hash on JVM + Native
    x86-64 + riscv64(qemu) + Script (`flat16`/`diag32` are unchanged by the
    filter, matching libwebp; `skip64`/`cat64` change), RED-first
    (`PKG006 import 'image.Vp8Filter' not found`). This completes the pure-Kof
    VP8 key-frame decoder end-to-end against libwebp.

19. **VP8 lossy slice 7 — `decodeRaster` route — LANDED 01/10 (pure Kof, all
    targets).** `libs/image/Vp8Raster.kf` (new, `vp8Raster`) closes the honest
    boundary: `decodeRaster(path)` dispatches a `VP8 ` chunk through the full
    key-frame chain and returns a bounded `Raster` (RGB, 3 channels) — frame
    header (`vp8FrameFromWebp`) → `Vp8Coeffs` → `Vp8Reconstruct` →
    `vp8LoopFilter` → YUV 4:2:0 to RGB (BT.601 limited range, nearest chroma).
    The conversion reads the reconstruction's `yAt/uAt/vAt` (macroblock-padded
    stride) so a width that is not a multiple of 16 is correct. Proof:
    `Vp8RasterE2ETest` **4/4** — five real libwebp files including a 20×28
    partial frame, golden from libwebp's own Y/U/V plus the documented
    limited-range matrix validated against libwebp's RGB on solid chroma, on
    JVM + Native x86-64 + riscv64(qemu) + Script.

20. **VP8 lossy slice 8 — multi-token-partition decode — LANDED 01/10 (pure
    Kof, all targets).** `libs/image/Vp8Coeffs.kf` (`vp8TokenPartitions`) now
    decodes frames whose residue is split across 2, 4 or 8 token partitions
    (RFC 6386 §9.5), replacing the `IMAGE: VP8 multiple token partitions are
    not supported yet` refusal. When the frame header declares more than one
    token partition, the first data partition carries the sizes of the first
    `n-1` partitions as 3 little-endian bytes each (the last takes the
    remainder); macroblock row `r` is read with partition `r % n`. The
    single-partition path is unchanged (the residue still follows the first
    partition directly). The previous decoder already threaded the entropy
    decoder per macroblock, so only the partition selection and offset table
    were added. Fixtures are real libvpx 1.14 encodes
    (`VP8E_SET_TOKEN_PARTITIONS`) of a 16x128 frame (8 macroblock rows) at 2, 4
    and 8 partitions — `libvpx` is the only available encoder that emits
    multiple partitions (libwebp and ffmpeg's WebP muxer always emit one) — and
    each decodes to exactly the single-partition coefficient golden. Proof:
    `Vp8CoeffE2ETest` **4/4** (eight fixtures now: the five prior plus
    `np2`/`np4`/`np8`), RED-first on the pre-slice tree
    (`IMAGE: VP8 multiple token partitions are not supported yet`, 4/4 red), on
    JVM + Native x86-64 + riscv64(qemu) + Script.

21. **PNG Adam7 interlace — LANDED 01/10 (pure Kof, all targets).**
    `libs/image/Png.kf` de-interlaces the seven Adam7 passes (PNG spec §9),
    replacing the `IMAGE: interlaced PNG is not supported` refusal. The whole
    IDAT stream is inflated once to the pass-summed raw length
    (`pngAdam7RawLen`); each pass is an independent sub-image with its own
    scanline filters, reversed by the existing `unfilter` (now offset-based)
    and scattered into the full `width x height` buffer (`unfilterAdam7`).
    Empty passes (sub-image width/height zero) and passes exactly one pixel
    wide/tall are handled, matching the spec's pass geometry. Bit-depth-8 color
    types 0/2/3/4/6 on the non-interlaced path are unchanged. Fixtures are real
    ImageMagick interlaced PNGs (RGB 20x13 covering every pass, RGBA 13x9,
    grayscale 17x11 with empty and one-pixel passes), each independently
    byte-validated by Java `ImageIO` and PIL. Proof: `PngInterlaceE2ETest`
    **4/4** (sample sum + 24-bit rolling hash against the PIL/ImageIO pixels),
    RED-first (`IMAGE: interlaced PNG is not supported` with the old decoder),
    on JVM + Native x86-64 + riscv64(qemu) + Script; the existing
    `RasterDecodeE2ETest` PNG tests stay 4/4.

22. **PNG bit depths 1/2/4/16 — LANDED 01/10 (pure Kof, all targets).**
    `libs/image/Png.kf` now decodes every spec-allowed bit depth instead of
    refusing `IMAGE: unsupported PNG bit depth`. The decoder is generalized to
    bits-per-pixel: `pngSampleChannels` validates the depth/color-type
    combination, `pngRawLen`/`pngAdam7RawLen` size the inflated stream, and
    `unfilter`/`unfilterAdam7` move sub-byte samples as bit-fields and 8/16-bit
    samples as bytes. After unfiltering, `pngUnpackSub` scales 1/2/4-bit gray by
    `255/maxval` (palette indices are kept raw) and `pngUnpack16` takes the high
    byte of 16-bit samples (the `farbfeld` rule). The 8-bit path is unchanged.
    Fixtures cover every new combination — 1/2/4-bit gray, 16-bit gray, 16-bit
    RGB, 2/4-bit palette — plus two Adam7-interlaced sub-byte files (4-bit gray
    19x11, 4-bit palette 18x10) that exercise the sub-byte scatter; the sub-byte
    gray and 16-bit files are hand-built (zlib) and all are independently
    readable by PIL and Java `ImageIO`. Proof: `PngBitDepthE2ETest` **4/4**
    (sample sum + 24-bit rolling hash) on JVM + Native x86-64 + riscv64(qemu) +
    Script, RED-first (`IMAGE: unsupported PNG bit depth 1` with the old decoder,
    measured); neighbors `RasterDecodeE2ETest` PNG 4/4 and `PngInterlaceE2ETest`
    4/4 unchanged.

23. **PNG `tRNS` transparency — LANDED 01/10 (pure Kof, all targets).**
    `libs/image/Png.kf` now reads the `tRNS` chunk (previously ignored). For
    grayscale (color type 0) the output becomes gray+alpha with alpha 0 where the
    sample equals the tRNS gray key (else 255); for RGB (color type 2) it
    becomes RGBA with alpha 0 on the exact color-key match (else 255); for
    palette (color type 3) it becomes RGBA with the per-color alpha, entries past
    the tRNS length being opaque. A `tRNS` on a color type that cannot carry
    transparency (4/6) is refused with an explicit `IMAGE:` diagnostic. The tRNS
    component is always a 16-bit big-endian value (PNG spec §11.3.2) regardless
    of the image bit depth, so the key is mapped into the same 8-bit space the
    decoded samples use (`pngTrnsKey`): the high byte for 16-bit images (the
    `pngUnpack16` rule), a `255/maxval` scaling for sub-byte gray, and the byte
    itself for 8-bit — a 16-bit sample sharing its high byte with the key but not
    its low byte stays opaque. Fixtures are a gray 12x6, an RGB 10x5, a palette
    11x7, a 16-bit gray 4x1, a 16-bit RGB 4x1 and a 4-bit gray 4x2 PNG, each
    independently readable by PIL and Java `ImageIO`. Proof:
    `PngTransparencyE2ETest` **4/4** (sample sum + 24-bit rolling hash) on JVM +
    Native x86-64 + riscv64(qemu) + Script, RED-first (the pre-slice decoder
    ignored `tRNS`, measured; the depth-aware key was added after the first
    landing measured a wrong alpha on the 16-bit and sub-byte fixtures);
    neighbors `RasterDecodeE2ETest` PNG 4/4, `PngInterlaceE2ETest` 4/4 and
    `PngBitDepthE2ETest` 4/4 unchanged.

**DECIDED 30/09 (`D-WEBP-LOSSY-PURE-KOF`, option C): WebP lossy `VP8 ` + AVIF
as a pure-Kof decoder on all targets.** The measured finding that forced the
decision: the JPEG escape hatch does not extend — OpenJDK 25 `javax.imageio`
has **no** WebP or AVIF reader (`ImageIO.getImageReadersByFormatName("webp"/
"avif")` empty), so `image.decode` cannot back either format without a
third-party plugin (TwelveMonkeys / an AVIF lib), a dependency the maintainer
rejected. The route is a pure-Kof VP8 lossy decoder (RFC 6386), library-first,
same shape as the VP8L slices, with the same slice discipline (each one a
complete, tested unit; no interim half-decode). Slice chain: (1) RIFF/`VP8 `
parser + frame header + boolean range decoder (§7); (2) per-macroblock mode/
segment header + coefficient probability tables; (3) dequantization + inverse
DCT/WHT (§14, slice 5a LANDED) then intra prediction + reconstruction (§12);
(4) in-loop deblocking filter; (5) the adaptive (non-keyframe) path. The
key-frame chain is complete and routed (slice 7 LANDED 01/10): `decodeRaster`
now decodes a lossy WebP through `libs/image/Vp8Raster.kf` (no silent wrong
decode); AVIF follows after VP8. Multi-token-partition key frames now decode
(slice 8 LANDED 01/10, `libs/image/Vp8Coeffs.kf`); the adaptive (non-keyframe)
path remains an explicit `IMAGE:` refusal, not a half-decode. The AVIF decode
chain is in progress: slice 3a LANDED 04/10 (`libs/image/Av1Symbol.kf`, the AV1
symbol/entropy range decoder §9.2/§9.3, proven against libaom's encoder/decoder
on six fixtures and on the host `.avif`'s real tile payload) and slice 3b
LANDED 04/10 (`libs/image/Av1Tx.kf`, the transform descriptor — transform-size
tables, the 19x3 generated scan orders pinned against libaom's
`av1_scan_orders`, the transform classes/sets and `compute_tx_type`) and slice
3c LANDED 04/10 (`libs/image/Av1CoeffCdf.kf`, the 13 default coefficient CDF
tables selected by `COEFF_CDF_Q_CTXS`, all 15,996 numbers cross-checked against
libaom's `token_cdfs.h`) and slice 3d LANDED 04/10 (`libs/image/Av1CoeffCtx.kf`,
the coefficient context selection — `get_coeff_base_ctx`/`get_coeff_br_ctx` and
the `all_zero`/`dc_sign` rules, the 2D offset table verified against libaom's
`av1_nz_map_ctx_offset`) and slice 3e LANDED 05/10 (`libs/image/Av1Coeffs.kf`,
the tile coefficient walk `coeffs()` — `all_zero`, the `eob_pt_*`/`eob_extra`
EOB token, the `coeff_base_eob`/`coeff_base`/`coeff_br` level loop, the sign and
`read_golomb` escape, and the per-tile adapted CDF store `Av1CoeffCdfStore`; the
40-block fixture set round-trips a faithful libaom entropy encoder/decoder and
the levels match a second independent Java reader and the Python oracle) and
slice 3f LANDED 05/10 (`libs/image/Av1Quant.kf`, the AV1 dequantization stage
§7.12.2 — `Dc_Qlookup`/`Ac_Qlookup`, `dc_q`/`ac_q`, the `dqDenom` rule and the
step-1 dequantization loop over the effective `Min(32,w) × Min(32,h)` raster;
all 1536 table numbers cross-checked against libaom's `quant_common.c` and 19
dequantized blocks pinned against a second independent Java reader) and
slice 3g LANDED 05/10 (`libs/image/Av1InvTx.kf`, the AV1 inverse transform
§7.13 — the `Cos128_Lookup[65]` table, the §7.13.2.1 butterflies
`av1InvB`/`av1InvH`, inverse DCT/ADST/WHT/identity, and the §7.13.3 2D
`av1InvTx2d` with `Transform_Row_Shift`, the `BitDepth+8`/`Max(BitDepth+6,16)`
clamps, the `Abs(log2W-log2H)==1` pre-scale, the lossless WHT path and the
row/col transform-type mapping; the §7.12 flips stay with the caller; the
golden is a libaom inverse-transform harness with the flips off over all 579
valid size×type×depth combinations, zero mismatches) and
slice 3h LANDED 05/10 (`libs/image/Av1Lf.kf`, the AV1 loop filter §7.14 —
the §7.14.3 filter size process, the §7.14.4/§7.14.5 adaptive filter strength
(`MAX_LOOP_FILTER`=63, `SEG_LVL_ALT_LF_*`, the `nShift` `ref_deltas`/
`mode_deltas` scaling) and the §7.14.6 sample filtering (`hevMask`/`filterMask`/
`flatMask`/`flatMask2`, the narrow `filter4` and the wide 8/16 low-pass with
`n2`; the real libaom `aom_lpf_*` kernels are the oracle over 240 sample cases,
plus libaom's `get_filter_level`/`update_sharpness` for 300 strength cases) and
slice 3i LANDED 05/10 (`libs/image/Av1Recon.kf`, the AV1 reconstruction §7.11.4 —
the `flipUD`/`flipLR` derivations, `av1ReconAddPred` with `xx = flipLR ? w-j-1 : j`,
`yy = flipUD ? h-i-1 : i` and `Clip1( pred + residual )`, and `av1Reconstruct`
composing `av1InvTx2d` with the sum; the oracle is a copy of libaom
`inv_txfm2d_add_c` with the flips ENABLED over all 579 valid size×type×depth
combinations, zero mismatches) and
slice 3j LANDED 05/10 (`libs/image/Av1Intra.kf`, the AV1 base intra prediction
§7.11.2 — the DC/V/H/PAETH/SMOOTH/SMOOTH_V/SMOOTH_H predictors with the
`Sm_Weights_Tx_*` tables, `av1IntraPred` dispatching them and refusing a
directional mode 3..8 with an explicit `IMAGE:` diagnostic; the oracle is the
REAL libaom `aom_dsp/intrapred.c` kernels over every transform size, all three
bit depths and all four `haveAbove`/`haveLeft` combinations, zero mismatches) and
slice 3k LANDED 06/10 (`libs/image/Av1IntraDr.kf`, the AV1 directional intra
prediction §7.11.2.4 with its edge processes §7.11.2.7/.9/.10/.11/.12 — the
`Mode_To_Angle`/`Dr_Intra_Derivative` tables, the zone kernels
`av1DrPredZ1`/`Z2`/`Z3`, `av1DrFilterCorner`, `av1DrEdgeFilterStrength`,
`av1DrUseUpsample`, `av1DrFilterEdge` and `av1DrUpsampleEdge`, composed by
`av1DrPredict`; the oracle is the REAL libaom `av1/common/reconintra.c` kernels
over every transform size, the eight angular modes, all in-range `angleDelta`
values and both left-edge configurations, zero mismatches) and
slice 3l LANDED 06/10 (`libs/image/Av1FilterIntra.kf`, the AV1 recursive
filter-intra prediction §7.11.2.3 — the `Intra_Filter_Taps` table and the
4x2-block recursive filter with `Clip1(Round2Signed(pr, 4))`; the oracle is the
REAL libaom `av1_filter_intra_predictor_c` kernel and its taps table over every
transform size the process admits and all five `filter_intra_mode` values, zero
mismatches) and
slice 3m LANDED 06/10 (`libs/image/Av1ModeCdf.kf`, the AV1 default mode-info
CDFs §10 — the partition tree, the intra frame Y mode, the Y/UV mode, angle
delta, filter-intra mode/use, the skip/tx-size/delta-q/delta-lf symbols,
the CFL sign/alpha and the transform-partition split, 15 tables parsed from
comma-separated strings and cross-checked value-for-value against libaom
`av1/common/entropymode.c`, zero differences) and
slice 3n LANDED 06/10 (`libs/image/Av1Block.kf`, the AV1 block-size & partition
descriptor §9.3/§10 — the conversion tables (`Num_4x4_Blocks_Wide/High`,
`Block_Width/Height`, `Size_Group`, `Num_Pels_Log2`, `Mi_Width/Height_Log2`,
`Max_Tx_Size_Rect`, `Max_Tx_Size`, `Partition_Subsize`) plus the partition
selection helpers `av1PartitionCtx`/`av1PartitionCdfLength` and the derived
2-symbol `av1PartitionGatherHorzAlike`/`av1PartitionGatherVertAlike`; the tables
are cross-checked against libaom `common_data.h` and the spec, and the gathered
CDFs against libaom `partition_gather_*_alike`, zero differences);
slice 3o LANDED 06/10 (`libs/image/Av1Partition.kf`, the AV1 recursive
`decode_partition` tile walk §5.11.4/§6.10.4 — the first stage that CONSUMES
the entropy tables: it reads the partition symbol of every square block with the
slice-3a decoder and the slice-3m CDFs, keeps libaom's `partition_plane_context`
neighbour state, adapts the full partition CDF per context, reads the 2-symbol
`split_or_horz`/`split_or_vert` gathered CDFs unadapted when only one neighbour
exists, recurses through `Partition_Subsize` and applies
`update_ext_partition_context` after the recursion; the oracle is libaom's real
range ENCODER driving its own partition helpers, 53 decisions zero mismatches —
and this slice also CORRECTED the 3n gathered-CDF helpers, which were swapped);
slice 3p LANDED 06/10 (`libs/image/Av1ModeInfo.kf`, the AV1 intra mode-info
prefix — segment id + skip — the first two syntax elements
`intra_frame_mode_info` reads after the partition walk: the segment id
§5.11.8 with libaom's `av1_get_spatial_seg_pred` predictor/context and the
`neg_deinterleave` postprocess, and the skip flag §5.11.11 with the spec
neighbour context; slice 3m gained the segment CDFs; the oracle is libaom's
real range ENCODER over five frame shapes, 1951 decisions zero mismatches);
slice 3q LANDED 07/10 (`libs/image/Av1ModeInfo.kf`, the AV1 intra mode-info
BODY — the non-palette rest of `intra_frame_mode_info`: the intra Y mode with
the spec `Intra_Mode_Context` neighbour context, the Y angle delta, the UV mode
with the CFL-allowed set selection, the CFL sign/alpha symbols, the UV angle
delta and `filter_intra_mode_info`, with persistent adapting CDF stores; palette
refuses by name; the oracle is libaom's real range ENCODER over six block-grid
sequences, 884 decisions zero mismatches);
slice 3r LANDED 07/10 (`libs/image/Av1ModeInfo.kf`, the AV1 intra mode-info
TAIL — CDEF index, quantizer delta and loop-filter deltas: `readCdef` (the
`@@cdef_idx` literal per 64x64 unit, first non-skip block, with the
coded-lossless/`enable_cdef` gates), `readDeltaQ` (§5.11.8 `read_delta_qindex`,
the `DELTA_Q_SMALL` escape and sign) and `readDeltaLf` (`read_delta_lf`, one
level per `frameLfCount`, `delta_lf_multi` aware, clamped to ±63), all sharing
the superblock-first-block gate and the spec `MiSize == sbSize && skip` early
return; new `record Av1ModeTail` carries the frame-level state; the oracle is
libaom's real range ENCODER over seven cases, 1227 decisions zero mismatches);
slice 3s LANDED 07/10 (`libs/image/Av1TxSize.kf`, new, the AV1 transform-size
selection — `read_tx_size`/`read_selected_tx_size`: the Lossless shortcut, the
mode-derived transform (`ONLY_4X4`/`TX_MODE_LARGEST`), the BLOCK_4X4 rectangular
max, and the `@@tx_depth` symbol over the `Default_Tx_Size_Cdf` selected by the
bsize category and the neighbour transform context, with `Split_Tx_Size` and the
per-4x4 transform map; the oracle is libaom's real range ENCODER over seven
cases, 1359 decisions zero mismatches);
slice 3t LANDED 07/10 (`libs/image/Av1TxType.kf`, new, the AV1 transform-type
selection — `transform_type` for intra luma: the coded-lossless/DCTONLY gate, the
`@@intra_tx_type` symbol over `Default_Intra_Tx_Type_Set1/Set2_Cdf` selected by
the transform set and the intra mode (filter-intra resolved through
`fimode_to_intradir`), inverted with `Tx_Type_Intra_Inv_Set1/Set2` and stamped
into the frame `TxTypes`; the oracle is libaom's real range ENCODER over seven
cases, 424 decisions zero mismatches);
slice 3u LANDED 08/10 (`libs/image/Av1IntraBlock.kf`, new, the AV1 intra
prediction COMPOSITION — the `predict_intra` process §7.11.2: `av1IntraPredictBlock`
builds `AboveRow`/`LeftCol` from the reconstructed plane (availability flags, the
`haveAboveRight`/`haveBelowLeft` `2*w`/`2*h` read limits, the `128±1` missing-edge
substitutions and the corner) and dispatches to the base (3j), directional (3k) or
recursive filter-intra (3l) predictors; `av1FilterIntraPredBd` gives the
filter-intra process the bit-depth `Clip1` bound. This slice caught and fixed two
landed-slice bugs: `av1DrPredict` derived `need_above`/`need_left` from the MODE
instead of `pAngle` (121/2128 slice-3k cases diverged), and the intra-edge UPSAMPLE
ran outside the `enable_intra_edge_filter` guard (libaom/spec gate it inside); the
slice-3k golden was regenerated (`SHA256 77c7ec46…`) and `Av1IntraDrE2ETest` stays
6/6; the oracle is the REAL libaom `build_*_intra_predictors` over 4 sizes × 13
modes × filter-intra × 2 bit depths × 4 availability combos × both edge-filter
faces, 896 blocks zero mismatches);
next is the palette mode (an explicit refusal until its slice), then the
per-block prediction + coefficients, then the plane reconstruction, then
`decodeRaster` AVIF (still refused until the chain closes). The cross native
face of the coefficient-walk slice is gated by known-bugs §602 (a large single
frame trips the riscv64/aarch64 GC; the proof splits the walk into a per-block
helper and one function per tile).

## PT
[Português](image-vision-plan.pt_BR.md)

# 33. Final rule

The goal is for Kof to evolve from:

```text
arquivo → imagem → processamento → visão computacional → resultado
```

with own, consistent, cross-platform APIs.

The Kof developer must not need to abandon the language to do:

* image processing;
* camera reading;
* detection;
* OCR;
* QR Code;
* visual analysis;
* model inference.

Everything must be built incrementally, preserving the existing base
and following the philosophy of Kof:

**less accidental complexity, small APIs, clear intention and control
over the implementation.**
