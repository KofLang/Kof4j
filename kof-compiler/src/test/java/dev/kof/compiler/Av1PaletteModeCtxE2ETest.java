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
 * Coverage for the palette-Y MODE context ({@code libs/image/Av1ModeInfo.kf},
 * {@code av1PaletteModeCtx}) — previously computed INLINE and therefore
 * untestable, and WRONG at every 64x64 superblock row start.
 *
 * <p>libaom's {@code av1_get_palette_mode_ctx} (fetched, never recalled —
 * {@code av1/common/pred_common.h}) is the count of AVAILABLE neighbours that
 * carry a palette:
 *
 * <pre>
 *   if (above_mi) ctx += (above_mi->palette_mode_info.palette_size[0] > 0);
 *   if (left_mi)  ctx += (left_mi->palette_mode_info.palette_size[0] > 0);
 * </pre>
 *
 * so availability is {@code mi_row > 0} / {@code mi_col > 0}. Kof used
 * {@code r % 16 != 0} for the above neighbour — the first mi row OF THE 64x64
 * SUPERBLOCK — which reports "above unavailable" at {@code r = 16, 32, 48, …}
 * where the neighbour really exists. The oracle below is the reference rule; the
 * probe dumps Kof's function over a grid that includes the superblock rows.
 */
class Av1PaletteModeCtxE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Symbol.kf"); }

    /** mi rows probed: the frame's first row, superblock row starts, and others. */
    private static final int[] ROWS = {0, 1, 15, 16, 17, 31, 32, 33, 48};

    private static final int[] COLS = {0, 1, 5};

    private static final int MI_COLS = 16;

    /** Palette sizes on a probe grid: the above neighbour of every row has one. */
    private static final boolean HAS_PALETTE = true;

    private final CompilerDriver compiler = new CompilerDriver();

    @TempDir Path tmp;

    /** The reference rule, transcribed from the fetched libaom helper. */
    private static int oracle(int r, int c, boolean abovePalette, boolean leftPalette) {
        var ctx = 0;
        if (r > 0 && abovePalette) ctx++;
        if (c > 0 && leftPalette) ctx++;
        return ctx;
    }

    private static String golden() {
        var sb = new StringBuilder();
        for (var r : ROWS) {
            for (var c : COLS) {
                sb.append("ctx ").append(r).append(' ').append(c).append(' ')
                        .append(oracle(r, c, HAS_PALETTE, HAS_PALETTE)).append('\n');
            }
        }
        return sb.toString().strip();
    }

    @Test
    void paletteModeContextMatchesLibaomOnJvm() throws Exception {
        assertEquals(golden(), runKof(oracleProbe()));
    }

    /**
     * The probe fills EVERY cell with palette size 2, so both neighbours always
     * carry a palette: the context is then exactly the number of neighbours that
     * EXIST, which is what the superblock-row bug got wrong.
     */
    private static String oracleProbe() {
        return """
import image.Av1ModeInfo

main() {
    var miCols = 16
    var map = new Int[64 * miCols]
    var i = 0
    while (i < map.size) {
        map[i] = 2
        i = i + 1
    }
    var rows = listOf(0, 1, 15, 16, 17, 31, 32, 33, 48)
    var cols = listOf(0, 1, 5)
    var a = 0
    while (a < rows.size) {
        var b = 0
        while (b < cols.size) {
            println("ctx " + rows[a] + " " + cols[b] + " " + av1PaletteModeCtx(rows[a], cols[b], map, miCols))
            b = b + 1
        }
        a = a + 1
    }
}
""";
    }

    private String runKof(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        installLibraries(root);
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

    private void installLibraries(Path root) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
    }
}
