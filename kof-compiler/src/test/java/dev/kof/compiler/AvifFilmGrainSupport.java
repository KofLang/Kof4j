package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fixtures for the film-grain gate — the pair that replaced the sequence-level
 * refusal (#900).
 * <p>
 * The 140th pass ({@code 2f5af376}) made film grain a FRAME decision: a
 * sequence header that signals {@code film_grain_params_present} is CARRIED,
 * and the frame header reads the first {@code film_grain_params()} bit
 * ({@code apply_grain}) — only an actually APPLIED grain is refused. The 141st
 * pass ({@code 1fdad2b0}) lifted the temporal-delimiter requirement, since a TD
 * is OPTIONAL in an AV1 {@code temporal_unit()} and an ISOBMFF item is already
 * self-delimited. Neither change updated the refusal goldens, which is what
 * #900 reported: the strings did not disappear, the COVERAGE moved.
 * <p>
 * Both sides of the pair are pinned here so a future change cannot silently
 * drop either the refusal or the acceptance.
 */
final class AvifFilmGrainSupport {

    private AvifFilmGrainSupport() {}

    /**
     * REDUCED profile-0 mono sequence header (32x32, depth 8) carrying
     * {@code film_grain_params_present}, with the cdef/restoration capability
     * bits exposed (a non-lossless frame tail needs them to reach its branches).
     */
    static byte[] redSeqFilm(boolean film, boolean cdef, boolean restoration) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(0, 3);                       // seq_profile
        w.bits(1, 1);                       // still_picture
        w.bits(1, 1);                       // reduced_still_picture_header
        w.bits(2, 5);                       // seq_level_idx
        w.bits(4, 4);                       // frame_width_bits_minus_1  (32)
        w.bits(4, 4);                       // frame_height_bits_minus_1 (32)
        w.bits(31, 5);                      // max_frame_width_minus_1
        w.bits(31, 5);                      // max_frame_height_minus_1
        w.bits(0, 3);                       // use_128x128 / filter_intra / intra_edge
        w.bits(0, 1);                       // enable_superres
        w.bits(cdef ? 1 : 0, 1);            // enable_cdef
        w.bits(restoration ? 1 : 0, 1);     // enable_restoration
        w.bits(0, 1);                       // high_bitdepth
        w.bits(1, 1);                       // mono_chrome (profile 0)
        w.bits(0, 1);                       // color_description_present
        w.bits(0, 1);                       // color_range (mono)
        w.bits(film ? 1 : 0, 1);            // film_grain_params_present
        w.bits(1, 1);                       // trailing one bit
        return AvifSeqSupport.obuType(1, w.bytes());
    }

    /**
     * Reduced mono KEY frame (1x1 tile, lossless tail) whose
     * {@code apply_grain} bit is the argument — the frame half of the gate.
     */
    static byte[] frameReducedFilm(int applyGrain) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(1, 1);                       // disable_cdf_update
        w.bits(0, 1);                       // allow_screen_content_tools = 0
        w.bits(0, 1);                       // render_and_frame_size_different = 0
        AvifFrameSupport.uniformTiles(w, 1, 1, 0, 0, 0);  // 1x1: no loops, no stop
        AvifFrameSupport.tailLossless(w, 0, 1);
        w.bits(applyGrain, 1);              // film_grain_params(): apply_grain
        w.bits(1, 1);                       // frame_header_obu trailing one bit
        return AvifSeqSupport.obuType(3, w.bytes());
    }

    /** {@code grain.avif} applies grain (refused); {@code nograin.avif} does not. */
    static Path fixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("grain.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(List.of(
                        AvifSeqSupport.delimiter(),
                        redSeqFilm(true, false, false),
                        frameReducedFilm(1)))));
        Files.write(dir.resolve("nograin.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(List.of(
                        AvifSeqSupport.delimiter(),
                        redSeqFilm(true, false, false),
                        frameReducedFilm(0)))));
        return dir;
    }

    /** Reads the applied-grain stream so its refusal prints among the others. */
    static String errorProbe(Path dir) {
        return """
            import image.AvifFrame

            main() {
                try {
                    readAvifFrameHeader("%s")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(dir.resolve("grain.avif").toString().replace('\\', '/'));
    }
}
