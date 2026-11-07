package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinalgBlasLevel1E2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;
    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "linalg"; }
    @Override public List<String> libraryMarkers() { return List.of("Linalg.kf"); }

    private static final String PROBE = """
            import linalg.Linalg

            main() {
                var v = new Double[2]
                v[0] = 3.0
                v[1] = 4.0
                var n = dnrm2(v)
                println(n == 5.0)
                var w = new Double[2]
                w[0] = 3.0
                w[1] = -4.0
                var s = dasum(w)
                println(s == 7.0)
                var z = new Double[3]
                z[0] = 1.0
                z[1] = -3.0
                z[2] = 2.0
                var idx = idamax(z)
                println(idx == 2)
                println(dnrm2(z) > 0.0)
                println("done")
            }
            """;

    private static final String EXPECTED = "true\ntrue\ntrue\ntrue\ndone";

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
    void blasLevel1OnJvm() throws Exception {
        Path root = tmp.resolve("l1-jvm");
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
    void blasLevel1OnNativeX86() throws Exception {
        Path root = tmp.resolve("l1-native");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        compileAndRun(root, Target.NATIVE);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> { T get() throws Exception; }
}
