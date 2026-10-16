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
 * Coverage for {@code libs/image/Av1Palette.kf} — the AV1 palette COLOUR-INDEX
 * reader (`palette_tokens()`), transcribed from libaom's
 * {@code av1_get_palette_color_index_context} + {@code
 * av1_palette_color_index_context_lookup} (av1/common/entropymode.c) and
 * {@code decode_color_map_tokens} (av1/decoder/detokenize.c).
 *
 * <p>The lookup table is pinned against libaom's exact values, and the context
 * function is checked on two cases computed BY HAND from the reference
 * algorithm (neighbour weights 2/1/2 for left/top-left/top):
 * <ul>
 *   <li>n=2, r=0 c=1, left neighbour colour 0: scores [2,0,...], the sort is a
 *       no-op, hash = 2*1 = 2, lookup[2] = 0.</li>
 *   <li>n=3, r=1 c=1 with top=0, top-left=1, left=2: scores [2,1,2]; the i=1
 *       pass moves colour 2 ahead of colour 1, so the order becomes [0,2,1] and
 *       hash = 2*1 + 2*2 + 1*2 = 8, lookup[8] = 1.</li>
 * </ul>
 */
class Av1PaletteTokensE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Symbol.kf"); }

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN =
            "LOOKUP -1 -1 0 -1 -1 4 3 2 1\n"
          + "A 0 0 1 2 3 4 5 6 7\n"
          + "B 1 0 2 1 3 4 5 6 7";

    private static String probe() {
        return """
import image.Av1Palette

main() {
    var look = ""
    var h = 0
    while (h < 9) {
        look = look + av1PaletteCtxHashLookup(h)
        if (h < 8) { look = look + " " }
        h = h + 1
    }
    println("LOOKUP " + look)

    // A: n=2, r=0 c=1, the left neighbour holds colour 0.
    var mapA = new Int[4]
    mapA[0] = 0
    var ordA = new Int[8]
    var ctxA = av1PaletteColorIndexContext(mapA, 2, 0, 1, 2, ordA)
    var sA = ""
    var i = 0
    while (i < 8) {
        sA = sA + ordA[i]
        if (i < 7) { sA = sA + " " }
        i = i + 1
    }
    println("A " + ctxA + " " + sA)

    // B: n=3, r=1 c=1 with top=0, top-left=1, left=2.
    var mapB = new Int[4]
    mapB[0] = 1
    mapB[1] = 0
    mapB[2] = 2
    var ordB = new Int[8]
    var ctxB = av1PaletteColorIndexContext(mapB, 2, 1, 1, 3, ordB)
    var sB = ""
    i = 0
    while (i < 8) {
        sB = sB + ordB[i]
        if (i < 7) { sB = sB + " " }
        i = i + 1
    }
    println("B " + ctxB + " " + sB)
}
""";
    }

    @Test
    void av1PaletteTokensOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(probe()));
    }

    @Test
    void av1PaletteTokensOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> driver.compile(source, out, Target.JVM));
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
}
