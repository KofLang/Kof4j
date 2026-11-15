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
 * Universal platform Stage 6.2 — the FIRST copy-out faces of the linalg
 * library ({@code dscal}, {@code axpy}): the FORTRAN {@code scal_}/{@code
 * axpy_} WRITE their vector argument, and the array copy-out fatia (the
 * #1156 face, landed 15/11 on the JVM + native x86-64) makes the write
 * visible in the caller's Kof array. The lib's own comment called these
 * "the measured follow-up" — unlocked by that fatia.
 */
class LinalgBlasLevel2E2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "linalg"; }

    @Override public List<String> libraryMarkers() { return List.of("Linalg.kf"); }

    private static final String PROBE = """
            import linalg.Linalg

            main() {
                var x = new Double[2]
                x[0] = 2.0
                x[1] = 3.0
                dscal(x, 3.0)
                println(x[0])
                println(x[1])
                var y = new Double[2]
                y[0] = 10.0
                y[1] = 20.0
                var v = new Double[2]
                v[0] = 1.0
                v[1] = 2.0
                axpy(2.0, v, y)
                println(y[0])
                println(y[1])
                println("done")
            }
            """;

    private static final String EXPECTED = "6.0\n9.0\n12.0\n24.0\ndone";

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
            assertEquals(0, p.waitFor(), "JVM exit, output: " + baos.toString(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.exists(bin), "binary must exist");
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(baos);
            assertEquals(0, p.waitFor(), "native exit, output: " + baos.toString(java.nio.charset.StandardCharsets.UTF_8));
        }
        return baos.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> { T get() throws Exception; }

    @Test
    void level2CopyOutJvm(@TempDir Path root) throws Exception {
        assertEquals(EXPECTED, withLibrary(root, () -> compileAndRun(root, Target.JVM)),
                "JVM golden (copy-out)");
    }

    @Test
    void level2CopyOutNativeX86(@TempDir Path root) throws Exception {
        NativeLibraryAvailability.assumePresent("LinalgBlasLevel2E2ETest", "libopenblas.so.0");
        assertEquals(EXPECTED, withLibrary(root, () -> compileAndRun(root, Target.NATIVE)),
                "native golden (copy-out)");
    }
}
