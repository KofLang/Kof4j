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
