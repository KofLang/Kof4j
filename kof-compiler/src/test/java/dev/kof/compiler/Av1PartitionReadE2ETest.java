package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the two partition-syntax helpers that turn a partition CDF row
 * into the reads and the context writes of {@code read_partition} /
 * {@code update_ext_partition_context} (fetched, never recalled —
 * {@code av1/common/av1_common_int.h}, {@code av1/common/av1_common_int.h} and
 * {@code av1/decoder/decodeframe.c}).
 *
 * <p><b>The gathers.</b> When a block overhangs the frame edge, libaom codes the
 * partition with a gathered 2-symbol CDF instead of the full one, and its two
 * helpers are named after the direction that is IMPOSSIBLE in the branch that
 * calls them:
 *
 * <pre>
 *   !has_rows &amp;&amp; has_cols -&gt; partition_gather_vert_alike -&gt; SPLIT : HORZ
 *    has_rows &amp;&amp; !has_cols -&gt; partition_gather_horz_alike -&gt; SPLIT : VERT
 * </pre>
 *
 * while Kof's {@code av1PartitionGatherHorzAlike} / {@code ...VertAlike} are
 * named after the mass they COLLECT. The two naming schemes are the mirror of
 * each other, so the oracle below pins each Kof function to the libaom helper it
 * must be, element set by element set, from the row Kof itself printed.
 *
 * <p><b>The update.</b> {@code update(subsize, bsize)} memsets the
 * {@code partition_context_lookup} byte of {@code subsize} over a
 * {@code bsize}-shaped REGION. The probe fills both context arrays with a
 * sentinel first, so the dump shows exactly the cells each partition type
 * touches.
 */
class Av1PartitionReadE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Symbol.kf"); }

    /** The parent block of every probed update case: {@code BLOCK_16X16}. */
    private static final int PARENT = 6;

    /** mi columns of the probed frame (sq1's geometry is 8). */
    private static final int MI_COLS = 8;

    /** {@code partition_context_lookup[..].above}, libaom's table. */
    private static final int[] LOOKUP_ABOVE = {
            31, 31, 30, 30, 30, 28, 28, 28, 24, 24, 24, 16, 16, 16, 0, 0, 31, 28, 30, 24, 28, 16};

    /** {@code partition_context_lookup[..].left}, libaom's table. */
    private static final int[] LOOKUP_LEFT = {
            31, 30, 31, 30, 28, 30, 28, 24, 28, 24, 16, 24, 16, 0, 16, 0, 28, 31, 24, 30, 16, 28};

    /** {@code get_partition_subsize(BLOCK_16X16, p)} for p = 0..9. */
    private static final int[] SUB16 = {6, 5, 4, 3, 5, 5, 4, 4, 17, 16};

    /** The sentinel the probe fills both context arrays with. */
    private static final int SENTINEL = 99;

    /** {@code Mi_Width_Log2} probed, and the square block size it names. */
    private static final int[] BSL = {1, 2, 3, 4, 5};

    private static final int[] BSL_BS = {3, 6, 9, 12, 15};

    private final CompilerDriver compiler = new CompilerDriver();

    @TempDir Path tmp;

    // ---------------------------------------------------------------- gathers

    /**
     * {@code P(e)} on a row in the SPEC form, which is the form Kof stores:
     * cumulative entries ending at {@code 1 << 15}, so an element probability is
     * the STEP between neighbours. libaom's {@code cdf_element_prob} is the same
     * quantity on its INVERSE form ({@code cdf[e-1] - cdf[e]}, top first) — using
     * that formula here flips the sign of every term.
     */
    private static int elemProb(int[] row, int e) {
        return row[e] - (e > 0 ? row[e - 1] : 0);
    }

    /** libaom {@code partition_gather_vert_alike}: the NOT-horz-alike complement. */
    private static int vertAlike(int[] row, boolean is128) {
        var p = 32768 - elemProb(row, 2) - elemProb(row, 3) - elemProb(row, 4)
                - elemProb(row, 6) - elemProb(row, 7);
        if (!is128) p -= elemProb(row, 9);
        return p;
    }

    /** libaom {@code partition_gather_horz_alike}: the NOT-vert-alike complement. */
    private static int horzAlike(int[] row, boolean is128) {
        var p = 32768 - elemProb(row, 1) - elemProb(row, 3) - elemProb(row, 4)
                - elemProb(row, 5) - elemProb(row, 6);
        if (!is128) p -= elemProb(row, 8);
        return p;
    }

    /** {@code partition_cdf_length(bsize) + 1}: 5 / 11 / 9 entries. */
    private static int rowLength(int bs) {
        if (bs <= 3) return 5;
        if (bs == 15) return 9;
        return 11;
    }

    @Test
    void partitionGathersMatchLibaomsElementSetsOnJvm() throws Exception {
        var out = runPartitionProbe(gatherProbe());
        var rows = new java.util.HashMap<String, int[]>();
        for (var line : out.split("\n")) {
            var f = line.split(" ");
            if (f[0].equals("row")) {
                assertEquals(rowLength(BSL_BS[Integer.parseInt(f[1]) - 1]), f.length - 3,
                        () -> "row length: " + line);
                var values = new int[f.length - 3];
                for (var i = 0; i < values.length; i++) values[i] = Integer.parseInt(f[i + 3]);
                rows.put(f[1] + " " + f[2], values);
            }
        }
        assertEquals(BSL.length * 4, rows.size(), () -> "rows printed:\n" + out);
        for (var line : out.split("\n")) {
            var f = line.split(" ");
            if (f[0].equals("row")) continue;
            var row = rows.get(f[1] + " " + f[2]);
            assertTrue(row != null, () -> "row missing for: " + line);
            var is128 = f[1].equals("5");
            var expected = f[0].equals("h") ? vertAlike(row, is128) : horzAlike(row, is128);
            assertEquals(expected, Integer.parseInt(f[3]),
                    () -> "gather " + f[0] + " bsl " + f[1] + " ctx " + f[2] + ": " + line);
            assertEquals(32768, Integer.parseInt(f[4]), () -> "cdf top: " + line);
        }
    }

    /**
     * Prints every partition CDF row and both gathers over it. The oracle applies
     * libaom's element set to the row KOF printed, so it checks the gather
     * arithmetic (and the 128x128 VERT_4/HORZ_4 guard, where the row is one entry
     * shorter) without transcribing the tables a second time.
     */
    private static String gatherProbe() {
        return """
import image.Av1Block
import image.Av1ModeCdf

main() {
    var m = Av1ModeCdf()
    var ks = listOf(1, 2, 3, 4, 5)
    var bss = listOf(3, 6, 9, 12, 15)
    var i = 0
    while (i < ks.size) {
        var ctx = 0
        while (ctx < 4) {
            var row = m.partitionW(ks[i], ctx)
            var line = "row " + ks[i] + " " + ctx
            var j = 0
            while (j < row.size) {
                line = line + " " + row[j]
                j = j + 1
            }
            println(line)
            // libaom's `read_partition` reaches the gathers only with
            // `assert(bsize > BLOCK_8X8)`; an 8x8 row carries 4 symbols and the
            // gather reads element 6, so bsl 1 is out of its domain.
            if (ks[i] > 1) {
                var h = av1PartitionGatherHorzAlike(row, bss[i])
                var v = av1PartitionGatherVertAlike(row, bss[i])
                println("h " + ks[i] + " " + ctx + " " + h[0] + " " + h[1])
                println("v " + ks[i] + " " + ctx + " " + v[0] + " " + v[1])
            }
            ctx = ctx + 1
        }
        i = i + 1
    }
}
""";
    }

    // ----------------------------------------------------------------- update

    private static int width4(int bs) {
        return switch (bs) {
            case 3, 4 -> 2;
            case 5, 6 -> 4;
            case 16 -> 1;
            case 17 -> 4;
            default -> throw new IllegalArgumentException("bs " + bs);
        };
    }

    private static int height4(int bs) {
        return switch (bs) {
            case 3, 5 -> 2;
            case 4, 6 -> 4;
            case 16 -> 4;
            case 17 -> 1;
            default -> throw new IllegalArgumentException("bs " + bs);
        };
    }

    /** One {@code update_partition_context(subsize, bsize)} call. */
    private static void update(int[] above, int[] left, int r, int c, int subSize, int region) {
        for (var i = 0; i < width4(region); i++) above[c + i] = LOOKUP_ABOVE[subSize];
        for (var i = 0; i < height4(region); i++) left[(r + i) & 15] = LOOKUP_LEFT[subSize];
    }

    /** The reference rule, transcribed from the fetched libaom helper. */
    private static String ctxOracle(int partition) {
        var above = new int[MI_COLS + 16];
        var left = new int[16];
        Arrays.fill(above, SENTINEL);
        Arrays.fill(left, SENTINEL);
        var r = 0;
        var c = 0;
        var hbs = 2;
        var sub = SUB16[partition];
        var bsize2 = SUB16[3];
        switch (partition) {
            case 0, 1, 2, 8, 9 -> update(above, left, r, c, sub, PARENT);
            case 3 -> { }
            case 4 -> {
                update(above, left, r, c, bsize2, sub);
                update(above, left, r + hbs, c, sub, sub);
            }
            case 5 -> {
                update(above, left, r, c, sub, sub);
                update(above, left, r + hbs, c, bsize2, sub);
            }
            case 6 -> {
                update(above, left, r, c, bsize2, sub);
                update(above, left, r, c + hbs, sub, sub);
            }
            default -> {
                update(above, left, r, c, sub, sub);
                update(above, left, r, c + hbs, bsize2, sub);
            }
        }
        var sb = new StringBuilder();
        for (var i = 0; i < above.length; i++) {
            sb.append('p').append(partition).append(" A ").append(i).append(' ')
                    .append(above[i]).append('\n');
        }
        for (var i = 0; i < left.length; i++) {
            sb.append('p').append(partition).append(" L ").append(i).append(' ')
                    .append(left[i]).append('\n');
        }
        return sb.toString().strip();
    }

    private static String ctxGolden() {
        var sb = new StringBuilder();
        for (var p = 0; p < 10; p++) {
            sb.append(ctxOracle(p)).append('\n');
        }
        return sb.toString().strip();
    }

    @Test
    void partitionContextUpdateWritesLibaomsCellsOnJvm() throws Exception {
        assertEquals(ctxGolden(), runPartitionProbe(ctxProbe()));
    }

    /**
     * Drives {@code updateExtCtx} for every partition type at a {@code BLOCK_16X16}
     * parent and dumps both context arrays; the sentinel survives in every cell the
     * update must NOT touch.
     */
    private static String ctxProbe() {
        return """
import image.Av1Block
import image.Av1Partition

main() {
    var p = 0
    while (p < 10) {
        var part = Av1Partition(new Int[16], 8, 8)
        var k = 0
        while (k < part.above.size) {
            part.above[k] = 99
            k = k + 1
        }
        k = 0
        while (k < part.left.size) {
            part.left[k] = 99
            k = k + 1
        }
        var bs = 6
        var sub = av1BlockPartitionSubsize(p, bs)
        var split = av1BlockPartitionSubsize(3, bs)
        var half = av1BlockNum4x4Wide(bs) >> 1
        part.updateExtCtx(0, 0, bs, p, sub, split, half)
        k = 0
        while (k < part.above.size) {
            println("p" + p + " A " + k + " " + part.above[k])
            k = k + 1
        }
        k = 0
        while (k < part.left.size) {
            println("p" + p + " L " + k + " " + part.left[k])
            k = k + 1
        }
        p = p + 1
    }
}
""";
    }

    // ---------------------------------------------------------------- harness

    private String runPartitionProbe(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        installPartitionLibraries(root);
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

    private void installPartitionLibraries(Path root) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
    }
}
