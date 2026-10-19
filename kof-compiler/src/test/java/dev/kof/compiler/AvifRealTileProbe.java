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
    /** The probe's `blocks=...` summary line (the trace line comes first). */
    static String summary(String out) {
        for (var l : out.split("\n")) {
            if (l.startsWith("blocks=")) return l;
        }
        throw new IllegalStateException("no summary line in: " + out);
    }

    static String probe(String path) {
        return AvifRealTileProbeProgram.program(path);
    }
}
