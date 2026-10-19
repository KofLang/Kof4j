package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AVIF slice 3ay — the container/header/tile-group walk over a REAL AVIF file.
 *
 * <p>Every AVIF fixture in the suite before this one is SYNTHESISED by the test
 * (hand-built {@code ftyp}/{@code meta}/{@code mdat} boxes). This one is a real
 * encoder's output: {@code resources/avif_real_64.avif} was produced by
 * ffmpeg's {@code libaom-av1} encoder through the {@code avif} muxer
 * ({@code ffmpeg -f lavfi -i testsrc=size=64x64 -frames:v 1 -pix_fmt yuv420p
 * -c:v libaom-av1 -crlf 40 -cpu-used 8 -still-picture 1 -f avif}). It pins the
 * whole read path — metadata, the frame header (including the slice-3ax
 * {@code lossless}/{@code txModeSelect}/{@code reducedTxSet} facts), and the
 * tile-group walk with its per-tile payload — against bytes the Kof code did
 * not write. The reconstruction of the tile itself is the NEXT slice; the
 * decode-only probe below stops at the payload so this test never claims a
 * pixel result it does not check.
 */
class AvifRealFileE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Symbol.kf"); }

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    /**
     * The real-file facts, pinned. {@code meta} = the {@code ispe}/brand row,
     * {@code hdr} = the frame header (with the 3ax facts), {@code groups} = the
     * tile-group walk, {@code payload} = the first tile's byte length and head.
     */
    private static final String GOLDEN =
            "meta 64 64 avif 1 false false sub=true/true bd=8/8 prof=0\n"
          + "hdr t=0 w=64 h=64 tiles=1x1 hb=7 bq=160 ll=0 tx=1 rts=0\n"
          + "groups n=1 start=0 end=0 count=1\n"
          + "payload n=1 len=293 head=220 133 40 245 33 125 18 124 164 116 147 164";

    private static String probe(String path) {
        return """
import image.Avif
import image.AvifFrame
import image.AvifGroup

main() {
    var path = "%s"
    var md = readAvifMetadata(path)
    println("meta " + md.width + " " + md.height + " " + md.brand + " " + md.primaryId
        + " " + md.monochrome + " " + md.hasAlpha
        + " sub=" + md.chromaSubX + "/" + md.chromaSubY
        + " bd=" + md.bitDepthLuma + "/" + md.bitDepthChroma + " prof=" + md.seqProfile)
    var h = readAvifFrameHeader(path)
    println("hdr t=" + h.frameType + " w=" + h.codedWidth + " h=" + h.codedHeight
        + " tiles=" + h.tileColumns + "x" + h.tileRows + " hb=" + h.headerBytes
        + " bq=" + h.baseQIdx + " ll=" + h.lossless + " tx=" + h.txModeSelect
        + " rts=" + h.reducedTxSet)
    var groups = readAvifTileGroups(path)
    var g = groups.get(0)
    println("groups n=" + groups.size + " start=" + g.tgStart + " end=" + g.tgEnd
        + " count=" + g.tileCount)
    var payloads = readAvifTilePayloads(path)
    var p = payloads.get(0)
    var head = ""
    var k = 0
    while (k < p.size && k < 12) {
        head = head + p[k]
        if (k < 11) { head = head + " " }
        k = k + 1
    }
    println("payload n=" + payloads.size + " len=" + p.size + " head=" + head)
}
""".formatted(path);
    }

    /** Copy the fixture to a temp dir so the absolute path is stable per run. */
    private Path fixture() throws Exception {
        Path f = tmp.resolve("real.avif");
        Files.copy(Path.of("src/test/resources/avif_real_64.avif"), f);
        return f;
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    @Test
    void realAvifWalkOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(probe(fixture().toString())));
    }

    @Test
    void realAvifWalkOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(fixture().toString()));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    /**
     * The CANONICALIZED read: ffmpeg writes the item WITHOUT a leading temporal
     * delimiter (its data is `[OBU_SEQUENCE_HEADER][OBU_FRAME]`), and the Kof
     * item walk requires the delimiter first. Prepending one yields the
     * canonical AV1 order with IDENTICAL semantics — same real sequence header,
     * same real frame, same real tile — so the reads below are the reference
     * encoder's own bytes, not synthesized ones. The container shell is the
     * test's minimal builder; only the ITEM is ffmpeg's.
     */
    private static byte[] realItem() throws Exception {
        byte[] f = Files.readAllBytes(Path.of("src/test/resources/avif_real_64.avif"));
        int m = -1;
        for (int i = 0; i + 4 <= f.length; i++) {
            if (f[i] == 'm' && f[i + 1] == 'd' && f[i + 2] == 'a' && f[i + 3] == 't') { m = i; break; }
        }
        if (m < 0) throw new IllegalStateException("no mdat in the real fixture");
        int size = ((f[m - 4] & 0xff) << 24) | ((f[m - 3] & 0xff) << 16) | ((f[m - 2] & 0xff) << 8) | (f[m - 1] & 0xff);
        byte[] item = new byte[size - 8];
        System.arraycopy(f, m + 4, item, 0, item.length);
        return item;
    }

    private Path canonicalized() throws Exception {
        byte[] item = realItem();
        byte[] delim = AvifSeqSupport.delimiter();
        byte[] both = new byte[delim.length + item.length];
        System.arraycopy(delim, 0, both, 0, delim.length);
        System.arraycopy(item, 0, both, delim.length, item.length);
        Path f = tmp.resolve("delim.avif");
        Files.write(f, AvifObuSupport.containerWithItem(both));
        return f;
    }

    private static final String DELIM_GOLDEN =
            "obus total=3 seqH=1 fh=0 frames=1\n"
          + "seq bd=8 sub=true sub=true fi=1 ie=1\n"
          + "hdr bq=160 ll=0 tx=1 rts=0 payload=293";

    private static String delimProbe(String path) {
        return """
import image.AvifFrame
import image.AvifGroup
import image.AvifObu

main() {
    var path = "%s"
    var o = readAvifItemObus(path)
    var seq = o.seq
    var fi = 0
    if (seq.enableFilterIntra) { fi = 1 }
    var ie = 0
    if (seq.enableIntraEdgeFilter) { ie = 1 }
    println("obus total=" + o.total + " seqH=" + o.seqHeaders + " fh=" + o.frameHeaders + " frames=" + o.frames)
    println("seq bd=" + seq.bitDepth + " sub=" + seq.subsamplingX + " sub=" + seq.subsamplingY + " fi=" + fi + " ie=" + ie)
    var h = readAvifFrameHeader(path)
    var p = readAvifTilePayloads(path).get(0)
    println("hdr bq=" + h.baseQIdx + " ll=" + h.lossless + " tx=" + h.txModeSelect + " rts=" + h.reducedTxSet + " payload=" + p.size)
}
""".formatted(path);
    }

    @Test
    void realAvifCanonicalizedReadOnJvm() throws Exception {
        assertEquals(DELIM_GOLDEN, runJvm(delimProbe(canonicalized().toString())));
    }

    @Test
    void realAvifCanonicalizedReadOnScript() throws Exception {
        Path root = tmp.resolve("script-delim");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), delimProbe(canonicalized().toString()));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(DELIM_GOLDEN, result.stdout().strip());
    }

    /**
     * The COMPOSITION, corrected (slice 3bc): decode a REAL solid-gray 64x64
     * AVIF and match the reference decoder's flat Y. The earlier "divergence"
     * was a GLUE bug, not a decoder bug: the tile driver's `txMode` takes the
     * AV1 TxMode VALUE (0=ONLY_4X4, 1=LARGEST, 2=SELECT), NOT the raw
     * `tx_mode_select` bit. Passing the bit put tx_mode at TX_MODE_ONLY_4X4, so
     * a 64x64 block was decoded as 256 4x4 transform blocks of garbage. With
     * the value mapped (bit 0 -> 1, bit 1 -> 2) the frame reads ONE block, one
     * TX_64X64 transform, and reconstructs Y=126 — exactly what ffmpeg's
     * libdav1d reads for the same file.
     */
    private Path solidFixture(String name) throws Exception {
        return canonResource("avif_real_solid64.avif", name);
    }

    /**
     * Canonicalize a committed real fixture: the mdat item (ffmpeg writes it
     * WITHOUT a leading temporal delimiter) with the delimiter prepended, in the
     * test's minimal container. Only the ITEM is the encoder's.
     */
    private Path canonResource(String resource, String name) throws Exception {
        byte[] f = Files.readAllBytes(Path.of("src/test/resources/" + resource));
        int m = -1;
        for (int i = 0; i + 4 <= f.length; i++) {
            if (f[i] == 'm' && f[i + 1] == 'd' && f[i + 2] == 'a' && f[i + 3] == 't') { m = i; break; }
        }
        int size = ((f[m - 4] & 0xff) << 24) | ((f[m - 3] & 0xff) << 16) | ((f[m - 2] & 0xff) << 8) | (f[m - 1] & 0xff);
        byte[] item = new byte[size - 8];
        System.arraycopy(f, m + 4, item, 0, item.length);
        byte[] delim = AvifSeqSupport.delimiter();
        byte[] both = new byte[delim.length + item.length];
        System.arraycopy(delim, 0, both, 0, delim.length);
        System.arraycopy(item, 0, both, delim.length, item.length);
        Path out = tmp.resolve(name);
        Files.write(out, AvifObuSupport.containerWithItem(both));
        return out;
    }

    private static final String SOLID_GOLDEN =
            "blocks=1 levels=1 txSz=4\n"
          + "Y 126 126 126";

    private static String solidProbe(String path) {
        return """
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
    var cfg = Av1ModeTail(12, h.cdefBits, ec, h.lossless, h.deltaQPresent, h.deltaQRes, h.deltaLfPresent, h.deltaLfMulti, h.deltaLfRes, 3)
    // 39th arg = the 129th pass's chroma-prediction sink (unused by this program).
    var tile = Av1TileDecode(frame, stride, mw, mh, cfg, h.lossless, 1, 3, fi, 0, seq.bitDepth, txMode, h.baseQIdx, 0, 0, 0, 0, dcq, acq, de, 0, h.reducedTxSet, sym, store, txStore, new Int[miw * mih], new Int[miw], new Int[mih], levels, listOf<Int>(), modes, chroma, 1, 1, listOf<Int[]>(), listOf<Int[]>(), listOf<Int>(), listOf<Int>(), listOf<Int>())
    var walk = Av1Partition(data, miw, mih)
    walk.useReader(sym)
    walk.bindDecoder(tile)
    walk.walk()
    println("blocks=" + (modes.size / 17) + " levels=" + levels.size + " txSz=" + modes[4])
    println("Y " + frame[(0 + 1) * stride + (0 + 1)] + " " + frame[(32 + 1) * stride + (32 + 1)] + " " + frame[(63 + 1) * stride + (63 + 1)])
}
""".formatted(path);
    }

        /**
     * POSITIVE CONTROL for the open `3bd` bug: `avif_real_cb16.avif` (a 64x64
     * 16x16-checkerboard, ffmpeg/libaom) is MULTI-BLOCK (4 blocks) and has
     * `tx_mode_select = 1`, yet it decodes EXACTLY and consumes its WHOLE payload.
     * ffmpeg's reference Y is (0,0)=17 (0,32)=17 (0,63)=235 (32,0)=17 (32,32)=17
     * (32,63)=235. So neither multi-block partitioning nor the `txModeSelect`
     * path is the fault; the failure tracks a large EOB (see the cb8 case in the
     * plan's 30th pass).
     */
    @Test
    void realChecker16DecodesToReferenceOnJvm() throws Exception {
        var line = runJvm(AvifRealTileProbe.probe(canonResource("avif_real_cb16.avif", "cb16.avif").toString()));
        assertTrue(line.contains("blocks=4 "), () -> "expected 4 mode blocks: " + line);
        assertTrue(line.contains("symBits=384 payloadBits=384"),
                () -> "the whole payload must be consumed: " + line);
        assertTrue(line.contains("Y 00=17 032=17 063=235 320=17 3232=17 3263=235"),
                () -> "must match the ffmpeg reference: " + line);
    }

    /**
     * The RED case, committed as a REPRODUCER: `avif_real_cb8.avif` (64x64
     * 8x8-checkerboard, high frequency -> large EOB) is ONE 64x64 block with 16
     * TX blocks, and every TX block reads `all_zero = 1` where the content needs
     * a large residual: it consumes only 23 of 152 payload bits (15%) and
     * reconstructs bare DC 128 against ffmpeg's 16/235. Only the REFERENCE-FREE
     * invariants are asserted (a red suite is not allowed); the wrong values are
     * recorded in the plan's 30th pass.
     */
    /**
     * A MINIMAL screen-content fixture — a 64x64 frame whose left half is FLAT
     * (100) and whose right half is a 4px horizontal ramp — generated with
     * `ffmpeg -f lavfi -i "nullsrc=size=64x64,geq=lum='if(lt(X,32),100,235-4*mod(X,8))':cb=128:cr=128"
     * -c:v libaom-av1 -crf 30 -cpu-used 6 -still-picture 1`. It sets
     * `allow_screen_content_tools` and codes as ONE block; it consumes its whole
     * payload and reconstructs EXACTLY (ffmpeg's reference Y is (0,0)=100
     * (0,32)=235 (0,63)=207 (32,0)=100 (32,32)=234 (32,63)=208).
     */
    @Test
    void realMinimalScreenContentDecodesToReferenceOnJvm() throws Exception {
        var line = runJvm(AvifRealTileProbe.probe(canonResource("avif_real_pskip.avif", "ps.avif").toString()));
        var parts = AvifRealTileProbe.summary(line).split(" ");
        var symBits = Integer.parseInt(parts[4].split("=")[1]);
        var payloadBits = Integer.parseInt(parts[5].split("=")[1]);
        assertEquals(payloadBits, symBits, () -> "the whole payload must be consumed: " + line);
        assertTrue(line.contains("Y 00=100 032=235 063=207 320=100 3232=234 3263=208"),
                () -> "must match the ffmpeg reference: " + line);
    }

    /**
     * A SMALLER frame (32x32) — the fixture that used to "crash the decoder".
     * It never did: the tile probe's Y SAMPLE POSITIONS were hardcoded for a
     * 64x64 frame, so the `(32,63)` sample read index 1186 of a 1156-entry
     * plane+border buffer and threw. The probe now clamps its sample positions
     * to the frame, and this test pins that: the fixture decodes to completion
     * and its block grid tiles the frame. The PIXELS are still wrong (a real
     * desync: 503 of 1968 payload bits consumed), so only the reference-free
     * invariants are asserted and the consumption is RECORDED.
     */
    @Test
    void realSmallFrameDecodesWithoutCrashOnJvm() throws Exception {
        var line = runJvm(AvifRealTileProbe.probe(canonResource("avif_real_ts32.avif", "ts32.avif").toString()));
        var parts = AvifRealTileProbe.summary(line).split(" ");
        var covered = Integer.parseInt(parts[1].split("=")[1]);
        var frameMi = Integer.parseInt(parts[2].split("=")[1]);
        var overlap = Integer.parseInt(parts[3].split("=")[1]);
        var symBits = Integer.parseInt(parts[4].split("=")[1]);
        var payloadBits = Integer.parseInt(parts[5].split("=")[1]);
        assertEquals(frameMi, covered, () -> "the block grid does not cover the frame: " + line);
        assertEquals(0, overlap, () -> "the block grid overlaps blocks: " + line);
        assertTrue(symBits <= payloadBits, () -> "the walk read past the payload: " + line);
        // Recorded, NOT asserted as correct: the frame is still desynced.
        assertTrue(line.contains("symBits=503 payloadBits=1968"), () -> "consumption moved: " + line);
    }

    @Test
    void realChecker8UnderConsumptionRecordedOnJvm() throws Exception {
        var line = runJvm(AvifRealTileProbe.probe(canonResource("avif_real_cb8.avif", "cb8.avif").toString()));
        var parts = AvifRealTileProbe.summary(line).split(" ");
        var covered = Integer.parseInt(parts[1].split("=")[1]);
        var frameMi = Integer.parseInt(parts[2].split("=")[1]);
        var overlap = Integer.parseInt(parts[3].split("=")[1]);
        var symBits = Integer.parseInt(parts[4].split("=")[1]);
        var payloadBits = Integer.parseInt(parts[5].split("=")[1]);
        assertEquals(frameMi, covered, () -> "the block grid does not cover the frame: " + line);
        assertEquals(0, overlap, () -> "the block grid overlaps blocks: " + line);
        assertTrue(symBits <= payloadBits, () -> "the walk read past the payload: " + line);
        // The frame sets `allow_screen_content_tools`, so the stream carries the
        // PALETTE syntax and the walk MUST read it: without the palette read this
        // fixture consumed only 23/152 bits and read `all_zero = 1` for every TX
        // block; with it the walk reads the palette size symbol (arity 7) and
        // consumes 76 bits with 6 non-zero coefficients.
        assertTrue(line.contains(" 7:"), () -> "the palette size symbol was not read: " + line);
        // With the palette SYMBOLS, the colour-INDEX COLORS and the TOKENS all
        // read in the reference order, the whole tile is consumed — before the
        // tokens were wired this fixture stopped at 23/152 bits (15%).
        assertEquals(payloadBits, symBits, () -> "the whole payload must be consumed: " + line);
        // The PALETTE RECONSTRUCTION: every pixel takes the block's palette colour
        // indexed by the decoded colour map, so this HIGH-FREQUENCY 8x8
        // checkerboard reconstructs EXACTLY — ffmpeg's reference Y is
        // (0,0)=16 (0,32)=16 (0,63)=235 (32,0)=16 (32,32)=16 (32,63)=235.
        assertTrue(line.contains("Y 00=16 032=16 063=235 320=16 3232=16 3263=235"),
                () -> "the palette reconstruction must match ffmpeg: " + line);
    }

    @Test
    void realTileWalkDoesNotReadPastThePayloadOnJvm() throws Exception {
        var line = runJvm(AvifRealTileProbe.probe(canonicalized().toString()));
        var parts = AvifRealTileProbe.summary(line).split(" ");
        var covered = Integer.parseInt(parts[1].split("=")[1]);
        var frameMi = Integer.parseInt(parts[2].split("=")[1]);
        var overlap = Integer.parseInt(parts[3].split("=")[1]);
        var symBits = Integer.parseInt(parts[4].split("=")[1]);
        var payloadBits = Integer.parseInt(parts[5].split("=")[1]);
        // Guard, NOT a correctness discriminator: a skipped superblock would show
        // as low coverage — though the tiling is guaranteed by construction even
        // for a desynced stream (see the javadoc).
        assertEquals(frameMi, covered, () -> "the block grid does not cover the frame: " + line);
        assertEquals(0, overlap, () -> "the block grid overlaps blocks: " + line);
        // The real invariant: a tile decode must not consume more than the payload.
        assertTrue(symBits <= payloadBits, () -> "the walk read past the payload: " + line);
        // This fixture sets `allow_screen_content_tools`, so the stream carries the
        // palette syntax. With the palette gate missing its BLOCK_8X8 lower bound
        // the walk THREW (`Index -2 out of bounds`, palette-bsize-ctx -1 on a 4x8
        // block); with it, the walk reads the palette and the residual path is
        // real: nzTot 0 -> 171 with the first TX blocks non-zero (nzHead 7,21,6,8).
        var nzTot = Integer.parseInt(AvifRealTileProbe.summary(line).split("nzTot=")[1].split(" ")[0]);
        assertTrue(line.contains(" 7:"), () -> "the palette size symbol was not read: " + line);
        assertTrue(nzTot > 0, () -> "the residual path must decode coefficients: " + line);
        // The prediction/reconstruction diagnostic is wired: it pairs each
        // transform block's PREDICTION with its RECONSTRUCTION at the block's
        // own origin (7 ints per TX block).
        assertTrue(line.contains("PREDBLK tx0") && line.contains("PATCH r0") && line.contains("PREDTEST mode="),
                () -> "the prediction/patch diagnostics are not wired: " + line);

        // The per-block position record must be consistent: 2 ints per block,
        // non-decreasing across the tile, ending exactly at the walk's position.
        var lines = line.split("\n");
        var blocks = 0;
        var prev = -1;
        var last = -1;
        for (var ln : lines) {
            if (!ln.startsWith("B ")) continue;
            var f = ln.split(" ");
            var p0 = Integer.parseInt(f[2]);
            var p1 = Integer.parseInt(f[3]);
            assertTrue(p0 >= prev && p1 >= p0,
                    () -> "the per-block positions are not monotonic: " + ln);
            prev = p1;
            last = p1;
            blocks++;
        }
        var declared = Integer.parseInt(parts[0].split("=")[1]);  // blocks=N
        assertEquals(declared, blocks, () -> "one position record per mode block: " + line);
        assertEquals(symBits, last, () -> "the last block position must equal the walk's: " + line);
    }

    @Test
    void realSolidGrayDecodesToReferenceOnJvm() throws Exception {
        assertEquals(SOLID_GOLDEN, runJvm(solidProbe(solidFixture("solid-jvm.avif").toString())));
    }

    @Test
    void realSolidGrayDecodesToReferenceOnScript() throws Exception {
        Path root = tmp.resolve("script-solid");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), solidProbe(solidFixture("solid-scr.avif").toString()));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(SOLID_GOLDEN, result.stdout().strip());
    }
}
