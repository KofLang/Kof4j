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
 * The CFL AC for a block that OVERHANGS the luma plane ({@code libs/image/Av1Cfl.kf},
 * {@code av1CflAcFromLuma}). libaom's {@code cfl_pad} (fetched, never recalled —
 * {@code av1/common/cfl.c}) states the rule in its own comment: "Due to frame
 * boundary issues, it is possible that the total area covered by chroma exceeds
 * that of luma. When this happens, we fill the missing pixels by repeating the
 * last columns and/or rows." The AC therefore covers the WHOLE chroma block, the
 * cells past the plane taking the last in-frame value of their row (columns
 * first, then the whole last row) — and the average is over all of it.
 *
 * <p>The oracle below applies that rule to the same plane, independently of the
 * library, and compares the AC entry by entry.
 */
class Av1CflAcPadE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Cfl.kf"); }

    /** The plane: {@code PW} x {@code PH} pixels, bordered by one sample. */
    private static final int PW = 8;

    private static final int PH = 8;

    private static final int STRIDE = PW + 2;

    /** The block: luma origin (6, 0) — a 4x2 CHROMA block whose right half is past the plane. */
    private static final int OX = 6;

    private static final int OY = 0;

    private static final int CW = 4;

    private static final int CH = 2;

    private static final int LOG2N = 3;

    /** The plane's sample at (x, y), in the bordered buffer's coordinates. */
    private static int luma(int x, int y) {
        return 40 + ((x * 7 + y * 13) % 61);
    }

    /** The oracle: subsample, PAD by the last in-frame column/row, subtract the average. */
    private static String oracle() {
        var sub = new int[CW * CH];
        for (var j = 0; j < CH; j++) {
            for (var i = 0; i < CW; i++) {
                var sx = OX + 1 + (i << 1);
                var sy = OY + 1 + (j << 1);
                // q3, like the library: the 4:2:0 sum shifted left by one is the
                // AVERAGE times eight (`av1CflSubsamplePixel`).
                sub[j * CW + i] = (luma(sx, sy) + luma(sx + 1, sy)
                        + luma(sx, sy + 1) + luma(sx + 1, sy + 1)) << 1;
            }
        }
        var wIn = (PW - (OX + 1) + 1) >> 1;
        if (wIn > CW) wIn = CW;
        if (wIn < 1) wIn = 1;
        var hIn = (PH - (OY + 1) + 1) >> 1;
        if (hIn > CH) hIn = CH;
        if (hIn < 1) hIn = 1;
        var sum = 1 << (LOG2N - 1);
        for (var j = 0; j < CH; j++) {
            var sj = Math.min(j, hIn - 1);
            for (var i = 0; i < CW; i++) {
                sum += sub[sj * CW + Math.min(i, wIn - 1)];
            }
        }
        var avg = sum >> LOG2N;
        var sb = new StringBuilder();
        for (var j = 0; j < CH; j++) {
            for (var i = 0; i < CW; i++) {
                sb.append("ac ").append(j * CW + i).append(' ')
                        .append(sub[Math.min(j, hIn - 1) * CW + Math.min(i, wIn - 1)] - avg).append('\n');
            }
        }
        return sb.toString().strip();
    }

    @Test
    void cflAcPadsTheCellsPastThePlaneOnJvm() throws Exception {
        assertEquals(oracle(), runCflProbe(cflProbe()));
    }

    /**
     * Prints the AC the library computes for the overhanging block. With no pad
     * the cells past the plane are read from the buffer's border (a sentinel
     * here), so the values differ from the oracle's replicated ones.
     */
    private static String cflProbe() {
        return """
import image.Av1Cfl

main() {
    var stride = 10
    var buf = new Int[stride * 10]
    var x = 1
    while (x <= 8) {
        var y = 1
        while (y <= 8) {
            buf[y * stride + x] = 40 + ((x * 7 + y * 13) % 61)
            y = y + 1
        }
        x = x + 1
    }
    // The border (row/column 0 and the far edges) is a sentinel, so a read past
    // the plane cannot be mistaken for a real sample.
    var k = 0
    while (k < buf.size) {
        if (buf[k] == 0) { buf[k] = 200 }
        k = k + 1
    }
    var ac = av1CflAcFromLuma(buf, stride, 7, 1, 4, 2, 1, 1, 3, 8, 8)
    var i = 0
    while (i < ac.size) {
        println("ac " + i + " " + ac[i])
        i = i + 1
    }
}
""";
    }

    private String runCflProbe(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        installCflLibraries(root);
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

    private void installCflLibraries(Path root) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
    }

    private final CompilerDriver compiler = new CompilerDriver();

    @TempDir Path tmp;
}
