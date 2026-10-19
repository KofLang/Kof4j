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
 * Coverage for the AV1 EOB decode geometry — previously UNTESTED. Three
 * load-bearing pieces, each of which shifts the entropy stream when wrong (the
 * open {@code 3bd} symptom):
 *
 * <ul>
 *   <li>{@code Av1Coeffs.eobMultiSize} selects WHICH and HOW MANY symbols the
 *       {@code eob_pt} read consumes (5..11 symbols, via the
 *       {@code eob_flag_cdfNN} row).</li>
 *   <li>the {@code eob_pt -> EOB position} tables ({@code av1_eob_group_start}
 *       and {@code av1_eob_offset_bits}).</li>
 *   <li>the coefficient-BR context of the EOB coefficient, where libaom selects
 *       {@code get_br_ctx_eob} — a POSITION-only closed form — while the other
 *       coefficients use the level-magnitude {@code get_br_ctx}.</li>
 * </ul>
 *
 * <p>Every golden below is libaom's own table, fetched from the source rather
 * than recalled: {@code av1/common/common_data.h},
 * {@code av1/common/txb_common.c} and {@code av1/common/txb_common.h}, plus
 * {@code av1/decoder/decodetxb.c} (which calls {@code get_br_ctx_eob} for the
 * coefficient at {@code c == eob - 1}).
 */
class Av1EobMultiSizeE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Symbol.kf"); }

    /** libaom `txsize_log2_minus4[TX_SIZES_ALL]`, spec TX-size order. */
    private static final int[] LIBAOM = {
        0, 2, 4, 6, 6, 1, 1, 3, 3, 5, 5, 6, 6, 2, 2, 4, 4, 5, 5,
    };

    /** libaom `av1_eob_offset_bits[12]` (`av1/common/txb_common.c`). */
    private static final int[] EOB_OFFSET_BITS = {
        0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
    };

    /** libaom `av1_eob_group_start[12]` (`av1/common/txb_common.c`). */
    private static final int[] EOB_GROUP_START = {
        0, 1, 2, 3, 5, 9, 17, 33, 65, 129, 257, 513,
    };

    /** Tx types picking each transform class: DCT_DCT = 2D, H_DCT = HORIZ, V_DCT = VERT. */
    private static final int[] CLASS_TX_TYPE = {0, 11, 10};

    /**
     * libaom {@code get_br_ctx_eob} (`av1/common/txb_common.h`): the BR context
     * of the EOB coefficient depends ONLY on its position and the transform
     * class — 0 at the DC, 7 near the origin (2D) or on the directional axis,
     * else 14. It is the closed form of {@code get_br_ctx} when every level is
     * still zero, which is exactly the state when the EOB coefficient is read.
     */
    private static int brCtxEob(int pos, int bhl, int txClass) {
        var col = pos >> bhl;
        var row = pos - (col << bhl);
        if (pos == 0) return 0;
        if ((txClass == 0 && row < 2 && col < 2) || (txClass == 1 && col == 0)
                || (txClass == 2 && row == 0)) return 7;
        return 14;
    }

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static String golden() {
        var sb = new StringBuilder();
        for (var i = 0; i < LIBAOM.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append("multi ").append(i).append(' ').append(LIBAOM[i]);
        }
        for (var p = 0; p < EOB_OFFSET_BITS.length; p++) {
            sb.append("\noff ").append(p).append(' ').append(EOB_OFFSET_BITS[p]);
        }
        for (var e = 0; e < EOB_GROUP_START.length; e++) {
            sb.append("\nstart ").append(e).append(' ').append(EOB_GROUP_START[e]);
        }
        for (var tt = 0; tt < CLASS_TX_TYPE.length; tt++) {
            for (var q = 0; q < 64; q++) {
                sb.append("\nbr ").append(tt).append(' ').append(q).append(' ')
                        .append(brCtxEob(q, 3, tt));
            }
        }
        return sb.toString();
    }

    @Test
    void av1EobDecodeGeometryMatchesLibaomOnJvm() throws Exception {
        assertEquals(golden(), runJvm(probe()));
    }

    @Test
    void av1EobDecodeGeometryMatchesLibaomOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(golden(), result.stdout().strip());
    }

    /** Reads the multi-size class, the EOB tables and the EOB BR context. */
    private static String probe() {
        return """
import image.Av1CoeffCtx
import image.Av1Coeffs
import image.Av1Symbol

main() {
    var sym = Av1Symbol(new Int[8])
    var store = Av1CoeffCdfStore(0)
    var i = 0
    while (i < 19) {
        var c = Av1Coeffs(sym, store, i, 0, 0)
        println("multi " + i + " " + c.eobMultiSize(i))
        i = i + 1
    }
    var p = 0
    while (p < 12) {
        println("off " + p + " " + av1EobOffsetBits(p))
        p = p + 1
    }
    var e = 0
    while (e < 12) {
        println("start " + e + " " + av1RecEobPos(e, 0))
        e = e + 1
    }
    // The EOB coefficient is read with EVERY level still zero, so the generic
    // magnitude-based BR context must collapse to libaom's `get_br_ctx_eob`.
    var zero = new Int[64]
    var tt = 0
    while (tt < 3) {
        var txType = 0
        if (tt == 1) { txType = 11 }
        if (tt == 2) { txType = 10 }
        var q = 0
        while (q < 64) {
            println("br " + tt + " " + q + " " + av1GetCoeffBrCtx(1, txType, zero, q))
            q = q + 1
        }
        tt = tt + 1
    }
}
""";
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
