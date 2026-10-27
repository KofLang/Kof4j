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
 * Real-encoder streams: the FILM-GRAIN gate and the TILE-GROUP size fields.
 * <p>
 * The FRAME HEADER of a stream from a REAL encoder that signals
 * {@code film_grain_params_present = 1} (ffmpeg {@code -c:v libaom-av1}, a
 * 192x64 checkerboard encoded with {@code -tiles 2x1}). Before the 140th pass
 * the sequence walker REFUSED the flag outright ({@code IMAGE: avif film grain
 * not covered}); the frame header now reads the first
 * {@code film_grain_params()} bit ({@code apply_grain}), accepts 0 (the
 * parameters end there — libaom memsets and returns) and still refuses an
 * actually applied grain. The fixture's frame is {@code apply_grain = 0}, so
 * the header facts are the encoder's: 192x64, base_q_idx 80, loop filter 0,
 * lossless 0, tx_mode_select 1, no delta-LF, cdef_bits 0.
 *
 * <p>RED-first: with the old sequence walk this program throws before printing
 * a single fact (measured when the fixture was introduced).
 */
class AvifEncodedFilmGrainE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Avif.kf"); }

    @Test
    void libaomFilmGrainStreamHeaderOnJvm() throws Exception {
        var code = """
import image.AvifFrame

main() {
    var h = readAvifFrameHeader("%s")
    println("hdr " + h.codedWidth + "x" + h.codedHeight + " bq=" + h.baseQIdx
        + " lf=" + h.loopFilterLevel0 + " ll=" + h.lossless + " tx=" + h.txModeSelect
        + " dfl=" + h.deltaLfPresent + " cdef=" + h.cdefBits)
}
""".formatted(Path.of("src/test/resources", "avif_enc_check192.avif").toAbsolutePath());
        assertEquals("hdr 192x64 bq=80 lf=0 ll=0 tx=1 dfl=0 cdef=0", runEncodedHeader(code));
    }

    /**
     * A palette-FREE stream's TILE GROUP: the per-tile size is
     * {@code tile_size_minus_1 le(TileSizeBytes)} — LITTLE-endian. The 1-byte
     * fields of the palette baseline hid the byte order; this stream's 2-byte
     * fields do not (known-bugs §759). Before the 143rd pass the walk read them
     * big-endian and refused the group as truncated; the two payloads must now
     * split as 1981 + 1005 bytes.
     */
    @Test
    void encodedPaletteFreeStreamSplitsItsTiles() throws Exception {
        var code = """
import image.AvifGroup

main() {
    var tiles = readAvifTilePayloads("%s")
    println("sizes " + tiles.size + " " + tiles.get(0).size + " " + tiles.get(1).size)
}
""".formatted(Path.of("src/test/resources", "avif_enc_nopal192.avif").toAbsolutePath());
        assertEquals("sizes 2 1981 1005", runEncodedHeader(code));
    }

    private String runEncodedHeader(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
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

    private final CompilerDriver compiler = new CompilerDriver();

    @TempDir Path tmp;
}
