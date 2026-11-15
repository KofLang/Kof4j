package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal platform Stage 6.3 — the level-3 face ({@code gemm}): the matrix
 * multiply through {@code cblas_dgemm} with the CBLAS enum parameters (the
 * standard openblas convention: CblasRowMajor=102, CblasNoTrans=111). THE
 * ABI FIT: the CBLAS interface takes the SCALARS BY VALUE (M/N/K, alpha/
 * beta, lda/ldb/ldc — the landed scalar face) and the matrices as pointers
 * (copy-in for A/B; the COPY-OUT for C via the {@code ownership borrowed}
 * opt-in — the array copy-out fatia landed 15/11). Kof arrays are ROW-MAJOR
 * and CBLAS takes the layout explicitly — no transpose dance (the FORTRAN
 * interface pain avoided).
 *
 * The oracle (hand-computed): A(2x3)=[[1,2,3],[4,5,6]] × B(3x2)=[[7,8],[9,10],
 * [11,12]] = C(2x2)=[[58,64],[139,154]].
 */
class LinalgBlasLevel3E2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "linalg"; }

    @Override public List<String> libraryMarkers() { return List.of("Linalg.kf"); }

    private static final String PROBE = """
            import linalg.Linalg

            main() {
                var a = new Double[6]
                a[0] = 1.0
                a[1] = 2.0
                a[2] = 3.0
                a[3] = 4.0
                a[4] = 5.0
                a[5] = 6.0
                var b = new Double[6]
                b[0] = 7.0
                b[1] = 8.0
                b[2] = 9.0
                b[3] = 10.0
                b[4] = 11.0
                b[5] = 12.0
                var c = new Double[4]
                gemm(2, 2, 3, a, b, c)
                println(c[0])
                println(c[1])
                println(c[2])
                println(c[3])
                println("done")
            }
            """;

    private static final String EXPECTED = "58.0\n64.0\n139.0\n154.0\ndone";

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try { return action.get(); }
        finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    private String compileAndRun(Path root, Target target) throws Exception {
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out-" + target.name().toLowerCase());
        CompilationResult r = driver.compile(root.resolve("Main.kf"), out, target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (target == Target.JVM) {
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", out.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(baos);
            assertEquals(0, p.waitFor(), "JVM exit, output: "
                    + baos.toString(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.exists(bin), "binary must exist");
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(baos);
            assertEquals(0, p.waitFor(), "native exit, output: "
                    + baos.toString(java.nio.charset.StandardCharsets.UTF_8));
        }
        return baos.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> { T get() throws Exception; }

    @Test
    void level3GemmJvm(@TempDir Path root) throws Exception {
        assertEquals(EXPECTED, withLibrary(root, () -> compileAndRun(root, Target.JVM)),
                "JVM golden (gemm copy-out)");
    }

    @Test
    void level3GemmNativeX86(@TempDir Path root) throws Exception {
        NativeLibraryAvailability.assumePresent("LinalgBlasLevel3E2ETest", "libopenblas.so.0");
        assertEquals(EXPECTED, withLibrary(root, () -> compileAndRun(root, Target.NATIVE)),
                "native golden (gemm copy-out)");
    }
}
