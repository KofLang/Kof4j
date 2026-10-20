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
     * A SKIPPED block ({@code skip_txfm == 1}) reads NO coefficient symbols:
     * libaom's {@code read_coeffs_tx_intra_block} guards the whole
     * {@code av1_read_coeffs_txb} call on {@code !mbmi->skip_txfm}, and
     * {@code decode_block} then resets the block's entropy contexts
     * ({@code av1_reset_entropy_context}). Fixture: the same 4x4-pixel
     * checkerboard 16/235, encoded with {@code -cpu-used 0} (the slower search
     * makes the first block skip=1), 2x1 tiles, palette off. The {@code TILESTATS}
     * prefix is the driver's per-tile stage data: tile 0's block 0 runs
     * 10 -> 34 -> 34 (its transform size ends at 34 and its residual ends at
     * 30: ZERO coefficient bits) and the tile yields 29 transform blocks /
     * 100 levels. Before the guard the same block ran past its transform-size
     * read (hundreds of coefficient bits the encoder never wrote). Tile 1's own
     * sync
     * ({@code b32/txb256/nz256}, its exact-payload decode) is the control: the
     * guard must not disturb a block that is not skipped.
     */
    @Test
    void skippedBlockReadsNoCoefficientSymbols() throws Exception {
        var line = runFullPlaneProbe(
                Path.of("src/test/resources", "avif_enc_nopal_cu0_2tile192.avif")
                        .toAbsolutePath());
        assertTrue(line.contains("t0:b5/txb29/nz100/st10,30,30,33,33"),
                () -> "a skipped block must consume no coefficient symbols: " + line);
        assertTrue(line.contains("t1:b32/txb256/nz256"),
                () -> "the non-skipped tile must keep its exact decode: " + line);
    }

    /**
     * RECORDED, not asserted as correct ({@code known-bugs} §760): the SMALLEST
     * self-contained reproducer of the TX_16X64 desync — 128x64, 4x4-pixel
     * checkerboard 16/235, {@code -cpu-used 0}, ONE tile (its tile stream is
     * byte-identical to the 192x64 fixture's tile 0, so it decodes identically
     * and isolates the failure from the tile split). The walk reads 5 blocks —
     * the first SB as {@code PARTITION_VERT_4} (four 16x64 strips) plus the
     * second SB — and stops at 704 of 7368 bits; the reference decodes a
     * checkerboard while Kof reconstructs a flat ~127.
     *
     * <p>Audit trail measured for the next slice: the failing transform block's
     * 102 base/eob contexts replay EXACTLY against an independent computation
     * from the decoded levels; the 16x64 effective scan equals the spec's
     * {@code Default_Scan_16x32} entry for entry (512/512); the EOB group/offset
     * tables match libaom's; the partition context tables and formulas are
     * libaom-exact and the mode-CDF audit is green. The reference pixels imply
     * the FIRST block cannot be {@code skip=1} while the decode says it is, so
     * the divergence sits at or before the first block's skip symbol —
     * upstream of the coefficient walk.
     *
     * <p>The 154th pass moved these recorded numbers (704/7368 -> 585/7368 and
     * the mode records) when the tile walk started honouring the sequence's
     * {@code use_128x128_superblock}: these encodes use 128x128 superblocks and
     * the walk had hard-coded 64x64. The recorded divergence itself is
     * unchanged; the walk now matches libaom's own reader for the first block's
     * eight mode-info symbols.
     */
    @Test
    void cu0SingleTileStreamRecordsTheTx16x64Divergence() throws Exception {
        var line = runFullPlaneProbe(
                Path.of("src/test/resources", "avif_enc_nopal_128x64_cu0.avif")
                        .toAbsolutePath());
        assertTrue(line.contains("TILES n=1"), () -> "the stream must be 1 tile: " + line);
        assertTrue(line.contains("blocks=5 covered=512 frame=512 overlap=0 "
                        + "symBits=585 payloadBits=7368"),
                () -> "the recorded TX_16X64 walk moved: " + line);
        assertTrue(line.contains("MODESA n=5 (1,2,0,0) (0,2,0,0) (1,2,0,0) "
                        + "(0,17,6,2) (0,2,9,0)"),
                () -> "the recorded mode records moved: " + line);
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
