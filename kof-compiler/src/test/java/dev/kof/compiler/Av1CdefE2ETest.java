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
 * AV1 CDEF ({@code libs/image/Av1Cdef.kf}): the constrained directional
 * enhancement filter, transcribed from the FETCHED libaom sources (never from
 * memory — {@code av1/common/cdef_block.c} for the direction search and the
 * filter taps, {@code cdef.h} for {@code constrain}, {@code cdef.c} for the
 * driver's packing/damping).
 *
 * <p>The fixture {@code av1_cdef_cfl.bin} carries the PRE-CDEF luma 32x24, U
 * and V 16x12 planes that Kof reconstructs from {@code avif_real_cfl32x24.avif}
 * (verified byte-for-byte against libaom's entropy -> dequant -> inverse
 * transform -> CFL prediction chain) and ffmpeg's POST-CDEF V plane (libaom
 * and libdav1d agree byte for byte). The frame's parameters:
 * {@code damping 5}, luma packed 0, chroma packed 56 (primary 14, secondary 0).
 *
 * <p>Expected results, all from {@code scripts/av1_cdef_ref.py} (the validated
 * transcription; {@code CDEF ok: luma copied, U unchanged, V == ffmpeg}):
 * the luma direction search returns dir 6 (vertical) with the four distinct
 * variances below on every row; the luma is COPIED (strength 0); the U plane
 * is invariant; and the V plane becomes ffmpeg's exact 12x16 plane.
 */
class Av1CdefE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Cdef.kf"); }

    /** The four column variances of the 32x24 ramp, repeated on all three block rows. */
    private static final String DIRS =
            "DIRS n=12 6 16201 6 16445 6 16852 6 16411"
                    + " 6 16201 6 16445 6 16852 6 16411"
                    + " 6 16201 6 16445 6 16852 6 16411";

    private static final String GOLDEN = String.join("\n",
            DIRS, "YOK true", "UOK true", "VOK true", "VFIRST 130 159 159");

    @Test
    void cdefMatchesTheFetchedLibaomOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(probe()));
    }

    @Test
    void cdefMatchesTheFetchedLibaomOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe());
        installCdefLibraries(root);
        KofInterpreter.Result result = compiler.interpret(
                List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    /**
     * The probe reads the planes from the binary fixture, runs the direction
     * search and the three plane filters with the frame's own parameters, and
     * prints the comparison counts. The script face does not run the JVM
     * reflection helper.
     */
    private String probe() {
        return """
import image.Av1Cdef

main() {
    var b = File("%s").readRange(0, 2000)
    var y = new Int[768]
    var u = new Int[192]
    var v = new Int[192]
    var ev = new Int[192]
    var i = 0
    while (i < 768) {
        y[i] = b[i]
        i = i + 1
    }
    i = 0
    while (i < 192) {
        u[i] = b[768 + i]
        v[i] = b[960 + i]
        ev[i] = b[1152 + i]
        i = i + 1
    }
    var dirs = av1CdefDirs(y, 32, 0, 0, 32, 24, 0)
    var dl = ""
    i = 0
    while (i < dirs.size) {
        dl = dl + " " + dirs[i]
        i = i + 1
    }
    println("DIRS n=" + (dirs.size / 2) + dl)
    // luma packed 0 -> t = 0 and sec = 0 -> the block is COPIED.
    var yf = av1CdefFilterPlane(y, 32, 0, 0, 32, 24, 0, 0, 56, 5, dirs, 4, 0, 0, 0)
    var uf = av1CdefFilterPlane(u, 16, 0, 0, 16, 12, 1, 0, 56, 5, dirs, 4, 1, 1, 0)
    var vf = av1CdefFilterPlane(v, 16, 0, 0, 16, 12, 1, 0, 56, 5, dirs, 4, 1, 1, 0)
    var badY = 0
    i = 0
    while (i < 768) {
        if (yf[i] != y[i]) { badY = badY + 1 }
        i = i + 1
    }
    var badU = 0
    i = 0
    while (i < 192) {
        if (uf[i] != u[i]) { badU = badU + 1 }
        i = i + 1
    }
    var badV = 0
    i = 0
    while (i < 192) {
        if (vf[i] != ev[i]) { badV = badV + 1 }
        i = i + 1
    }
    println("YOK " + (badY == 0))
    println("UOK " + (badU == 0))
    println("VOK " + (badV == 0))
    println("VFIRST " + vf[0] + " " + vf[176] + " " + vf[191])
}
""".formatted(fixture());
    }

    private Path fixture() {
        return Path.of("src/test/resources", "av1_cdef_cfl.bin").toAbsolutePath();
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        installCdefLibraries(root);
        CompilationResult result = compiler.compile(root.resolve("Main.kf"), out, Target.JVM);
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

    private void installCdefLibraries(Path root) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
    }

    private final CompilerDriver compiler = new CompilerDriver();

    @TempDir Path tmp;
}
