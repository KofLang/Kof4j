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
 * Universal platform Stage 6.1 truth test — the FIRST BLAS face
 * ({@code libs/linalg/Linalg.kf}): {@code ddot(x, y)} over
 * {@code libopenblas.so.0}'s {@code ddot_} — ALL copy-in (the in-pointer
 * args are the measured scalar-array copy-in fatia) with a SCALAR return,
 * exactly the landed FFI set. The copy-out faces ({@code axpy_}/{@code
 * gemm_} write their matrix args) stay the measured follow-up.
 */
class LinalgDdotE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "linalg"; }

    @Override public List<String> libraryMarkers() { return List.of("Linalg.kf"); }

    private static final String PROBE = """
            import linalg.Linalg

            main() {
                var x = new Double[2]
                x[0] = 3.0
                x[1] = 4.0
                var y = new Double[2]
                y[0] = 1.0
                y[1] = 1.0
                var d = ddot(x, y)
                println(d == 7.0)
                var p = new Double[3]
                p[0] = 1.0
                p[1] = 2.0
                p[2] = 3.0
                var q = new Double[3]
                q[0] = 1.0
                q[1] = 1.0
                q[2] = 1.0
                var d2 = ddot(p, q)
                println(d2 == 6.0)
            }
            """;

    // 3*1 + 4*1 = 7.0 and 1+2+3 = 6.0 — both binary-exact, so the equality
    // faces carry no formatting risk.
    private static final String EXPECTED = "true\ntrue";

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

    private void compileAndRun(Path root, Target target) throws Exception {
        Path out = root.resolve("out-" + target);
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, target));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        Path main = out.resolve("Default").resolve("Main");
        if (!Files.exists(main)) main = out.resolve("Main");
        assertTrue(Files.exists(main), () -> "no artifact at " + out);
        var proc = new ProcessBuilder(main.toString()).redirectErrorStream(true).start();
        String text = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(EXPECTED, text, target + " run: " + text);
    }

    @Test
    void ddotOnJvm() throws Exception {
        NativeLibraryAvailability.assumePresent("LinalgDdotE2ETest", "libopenblas.so.0");
        Path root = tmp.resolve("ddot-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally { System.setOut(previousOut); }
        assertEquals(EXPECTED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void ddotOnNativeX86() throws Exception {
        NativeLibraryAvailability.assumePresent("LinalgDdotE2ETest", "libopenblas.so.0");
        Path root = tmp.resolve("ddot-native");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        compileAndRun(root, Target.NATIVE);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> { T get() throws Exception; }
}
