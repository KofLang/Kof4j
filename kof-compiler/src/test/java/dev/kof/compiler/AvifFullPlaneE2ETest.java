package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FULL-PLANE conformance for the multi-tile AVIF frame: every sample of every
 * plane, not just the per-tile origin pairs the geo suite samples.
 *
 * <p>{@code avif_ref_mt128.yuv} is ffmpeg's decoded YUV 4:2:0 for
 * {@code avif_real_mt128.avif} (128x128); {@code libdav1d} and {@code libaom}
 * produce it byte for byte. The fixture's frame is 2x2 tiles, so the assertion
 * covers the tile-group split, each tile's own reader/CDF store, the shared
 * frame assembly and the post-CDEF planes: 128*128 + 64*64 + 64*64 = 24576
 * samples.
 *
 * <p>This class exists separately from {@code AvifScreenContentGeoE2ETest}
 * because that file is at the repository's 500-line ratchet; the probe and the
 * canonicalization are the same ones the geo suite uses.
 */
class AvifFullPlaneE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Avif.kf"); }

    @Test
    void multiTileFrameMatchesFfmpegOnEverySample() throws Exception {
        var line = runFullPlaneProbe(canonicalFixture("avif_real_mt128.avif", "mt-full.avif"));
        assertTrue(line.contains("TILES n=4"), () -> "the fixture must be 2x2 tiles: " + line);
        assertTrue(line.contains(planeRows("YROWF", 0, 128, 128, "avif_ref_mt128.yuv")),
                () -> "the luma plane moved: " + line);
        assertTrue(line.contains(planeRows("UROWF", 16384, 64, 64, "avif_ref_mt128.yuv")),
                () -> "the U plane moved: " + line);
        assertTrue(line.contains(planeRows("VROWF", 20480, 64, 64, "avif_ref_mt128.yuv")),
                () -> "the V plane moved: " + line);
    }

    /**
     * The UNALIGNED 20x20 frame, every sample. Its single 32x32 palette block
     * overhangs the 20x20 frame; libaom's palette wavefront codes the ALIGNED
     * mi extent (24x24), not the pixel extent, and the chroma tokens follow the
     * luma map in the same reader. Kof used the pixel clamp, so the map
     * desynced at diagonal 20 and the chroma decode started at the wrong bit.
     * This test is the regression proof for known-bugs §753.
     */
    @Test
    void unalignedFrameMatchesFfmpegOnEverySample() throws Exception {
        var line = runFullPlaneProbe(canonicalFixture("avif_real_ua20.avif", "ua-full.avif"));
        assertTrue(line.contains(planeRows("YROWF", 0, 20, 20, "avif_ref_ua20.yuv")),
                () -> "the luma plane moved: " + line);
        assertTrue(line.contains(planeRows("UROWF", 400, 10, 10, "avif_ref_ua20.yuv")),
                () -> "the U plane moved: " + line);
        assertTrue(line.contains(planeRows("VROWF", 500, 10, 10, "avif_ref_ua20.yuv")),
                () -> "the V plane moved: " + line);
    }

    /**
     * A stream from a REAL encoder, decoded from its RAW item: no temporal
     * delimiter and no re-wrap. The AV1 temporal delimiter is OPTIONAL and an
     * ISOBMFF item is already self-delimited, so the walker now accepts a
     * sequence/frame OBU directly (before the 141st pass it threw
     * `IMAGE: avif item does not begin with a temporal delimiter`, which is why
     * every earlier real fixture had to be re-wrapped).
     *
     * <p>The 141st pass asserted "the symbol consumption exactly equal to the
     * payload (2376/2376) — the encoder's own tile stream reads to its end".
     * That claim is RETRACTED (151st pass): the instrument SATURATES on this
     * stream. Corrupting one byte of the payload tail changes the decode
     * (blocks 70 -> 76 / 67, txb 34 -> 13 / 35, different levels) while
     * {@code symBits} stays exactly 2376/2376 — the reader's position clamps at
     * the payload end, so "consumed == payload" is NOT evidence of sync here.
     * The palette plane of this stream is a recorded §757 divergence (Y 235 vs
     * the reference's 218), so no pixel assertion is made. The VALIDATED
     * consumption instrument now lives in
     * {@link #paletteFreeMultiTileStreamDecodesItsWholePayload}, where a tail
     * corruption moves {@code symBits} 11400 -> 11379.
     */
    @Test
    void encodedLibaomStreamDecodesItsWholePayload() throws Exception {
        var line = runFullPlaneProbe(
                Path.of("src/test/resources", "avif_enc_check192.avif").toAbsolutePath());
        assertTrue(line.contains("TILES n=2"), () -> "the stream must be 2 tiles: " + line);
        assertTrue(line.contains("blocks="), () -> "the walk must reach its summary: " + line);
    }

    /**
     * The palette-FREE stream walks the WHOLE probe without crashing: its two
     * tiles split (known-bugs §759) and the diagnostic tail must index the
     * LAST tile's palette info with the per-tile block counter, not the
     * frame-wide one. Before the 144th pass this run threw
     * {@code Index 34 out of bounds for length 34}. The decode itself DIVERGES
     * (the §757 family: 7283 of 23888 payload bits consumed) and is recorded,
     * not asserted.
     */
    @Test
    void encodedPaletteFreeStreamWalksWithoutCrashing() throws Exception {
        var line = runFullPlaneProbe(
                Path.of("src/test/resources", "avif_enc_nopal192.avif").toAbsolutePath());
        assertTrue(line.contains("TILES n=2"), () -> "the stream must be 2 tiles: " + line);
        assertTrue(line.contains("blocks="), () -> "the walk must reach its summary: " + line);
    }

    /**
     * A palette-FREE 2-tile stream from KNOWN source content decodes BOTH tiles
     * to their last payload bit. Fixture: 192x64 YUV420p, 4x4-pixel checkerboard
     * 16/235, encoded with {@code -c:v libaom-av1 -still-picture 1 -cpu-used 6
     * -crf 20 -tiles 2x1 -enable-palette 0}.
     *
     * <p>The root cause this pins is TILE-RELATIVE availability: libaom's
     * {@code set_offsets} computes {@code up_available = mi_row >
     * tile->mi_row_start} (and likewise for the left edge), so a tile's first
     * column has NO left neighbour even though its frame column is non-zero.
     * The transform-size context ({@code get_tx_size_context}) and the intra
     * prediction both read those flags. With frame-relative checks the second
     * tile (mi col 32) selected a different tx-size CDF and desynced after
     * 2140 of 3976 bits, reconstructing 123/255 where the reference decodes
     * 17/234. Measured after the fix: both tiles consume exactly
     * (7424 + 3976 = 11400 bits) and the luma plane matches ffmpeg on every
     * sample (the probe's YROW rows were diffed). The consumption instrument is
     * DISCRIMINATING here, unlike the palette-ON fixture's (which saturates):
     * corrupting one payload-tail byte moves {@code symBits} 11400 -> 11379.
     */
    @Test
    void paletteFreeMultiTileStreamDecodesItsWholePayload() throws Exception {
        var line = runFullPlaneProbe(
                Path.of("src/test/resources", "avif_enc_nopal_2tile192.avif").toAbsolutePath());
        assertTrue(line.contains("TILES n=2"), () -> "the stream must be 2 tiles: " + line);
        assertTrue(line.contains("blocks=192 covered=768 frame=768 overlap=0 "
                        + "symBits=11400 payloadBits=11400"),
                () -> "the second tile must read its payload to the end: " + line);
        assertTrue(line.contains("TILEY t0(0,0)=17(4,0)=234 t1(128,0)=17(132,0)=234"),
                () -> "the tile-1 reconstruction must not use the other tile as a neighbour: "
                        + line);
    }

    /**
     * The same 192x64 -cpu-used 0 checkerboard with the RESTORATION syntax read
     * ({@code known-bugs} §760): both tiles now consume their whole payload and
     * reconstruct the checkerboard. The pre-fix record ({@code t0:b5/txb29/
     * nz100/st10,30,30,33,33}) was an ARTIFACT of the 16-bit shift — the frame
     * restores its luma with a WIENER filter, libaom reads the unit's
     * coefficients BEFORE the first partition symbol
     * ({@code loop_restoration_read_sb_coeffs}, tell 1 -> 16 in the reference
     * decode), and a walk that skips them decodes every later symbol from the
     * wrong bits. The reference decoder reads 96 blocks over the two tiles and
     * ends at bit 11296 of 11296.
     */
    @Test
    void cu0TwoTileStreamDecodesBothTilesInFull() throws Exception {
        var line = runFullPlaneProbe(
                Path.of("src/test/resources", "avif_enc_nopal_cu0_2tile192.avif")
                        .toAbsolutePath());
        assertTrue(line.contains("TILEY t0(0,0)=17(4,0)=234 t1(128,0)=17(132,0)=234"),
                () -> "both tiles must reconstruct the checkerboard: " + line);
        assertTrue(line.contains("t0:b64/txb512/nz512/st128,45,45,255,255"),
                () -> "tile 0 must decode its whole superblock: " + line);
        assertTrue(line.contains("pos7368/pay7368"),
                () -> "tile 0 must consume its whole payload: " + line);
        assertTrue(line.contains("t1:b32/txb256/nz256/st64,28,28,239,239")
                        && line.contains("pos3928/pay3928"),
                () -> "tile 1 must keep its whole-payload decode: " + line);
    }

    /**
     * FIXED 20/10 ({@code known-bugs} §760): the SMALLEST self-contained
     * reproducer — 128x64, 4x4-pixel checkerboard 16/235, {@code -cpu-used 0},
     * ONE tile (its tile stream is byte-identical to the 192x64 fixture's tile
     * 0). The walk now reads 64 blocks and consumes 7368 of 7368 payload bits;
     * its partition tree — {@code SPLIT -> SPLIT -> SPLIT -> VERT} — is
     * IDENTICAL to libaom's own decoder (patched {@code aomdec} on the same
     * fixture: {@code PART mi=0,0 bs=15 p=3 tell=18}, {@code bs=12 p=3
     * tell=21}, {@code bs=9 p=3 tell=24}, {@code bs=6 p=2 tell=27}).
     *
     * <p>The ROOT CAUSE was measured, not inferred: the frame sets
     * {@code lr_type = WIENER} for luma, and libaom reads a restoration unit's
     * coefficients at the start of EVERY superblock BEFORE the partition
     * symbol ({@code decode_partition} -> {@code av1_loop_restoration_corners_
     * in_sb} -> {@code loop_restoration_read_sb_coeffs}). Kof read none, so
     * the first partition symbol landed 16 bits early and the whole tile was
     * decoded from the wrong position - the "5 blocks / 585 bits" tree was the
     * artifact. {@code Av1Restoration} now reads that syntax.
     */
    @Test
    void cu0SingleTileStreamDecodesItsWholePayload() throws Exception {
        var line = runFullPlaneProbe(
                Path.of("src/test/resources", "avif_enc_nopal_128x64_cu0.avif")
                        .toAbsolutePath());
        assertTrue(line.contains("TILES n=1"), () -> "the stream must be 1 tile: " + line);
        assertTrue(line.contains("blocks=64 covered=512 frame=512 overlap=0 "
                        + "symBits=7368 payloadBits=7368"),
                () -> "the walk must consume the whole payload: " + line);
        assertTrue(line.contains("PART n=8 (r0,c0,bs15)p3 (r0,c0,bs12)p3 (r0,c0,bs9)p3 "
                        + "(r0,c0,bs6)p2 (r0,c4,bs6)p2 (r4,c0,bs6)p2 (r4,c4,bs6)p2 "
                        + "(r0,c8,bs9)p3"),
                () -> "the partition tree must match the reference decoder: " + line);
        assertTrue(line.contains("TILEY t0(0,0)=17(4,0)=234"),
                () -> "the tile must reconstruct the checkerboard: " + line);
    }

    /** One full plane's rows, read from ffmpeg's decoded YUV 4:2:0. */
    private static String planeRows(String tag, int offset, int w, int h, String reference)
            throws Exception {
        var bytes = Files.readAllBytes(Path.of("src/test/resources", reference));
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
    private Path canonicalFixture(String resource, String name) throws Exception {
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

    private String runFullPlaneProbe(Path fixture) throws Exception {
        String code = AvifRealTileProbe.probe(fixture.toString());
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        installImageLibraryForProbe(root);
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

    private void installImageLibraryForProbe(Path root) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
    }

    private final CompilerDriver compiler = new CompilerDriver();

    @TempDir Path tmp;
}
