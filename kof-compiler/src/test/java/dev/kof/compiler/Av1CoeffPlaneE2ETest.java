package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.kof.compiler.Av1CoeffPlaneSupport.golden;
import static dev.kof.compiler.Av1CoeffPlaneSupport.goldenFacts;
import static dev.kof.compiler.Av1CoeffPlaneSupport.grids;
import static dev.kof.compiler.Av1CoeffPlaneSupport.kofExpectedText;
import static dev.kof.compiler.Av1CoeffPlaneSupport.kofProbe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 3x (image-vision front, plan §34): the
 * pure-Kof coefficient plane decode driver {@code libs/image/Av1CoeffPlane.kf}
 * ({@code av1DecodeCoeffsPlane}), which ties the slice-3w neighbour-context
 * selection ({@code av1TxbCtx}, {@code av1TxbEntropyContext}) to the slice-3e
 * coefficient walk ({@code Av1Coeffs.decodeBlock}) across the transform-block
 * grid of a plane, maintaining the plane's above/left entropy contexts —
 * libaom's {@code av1_read_coeffs_txb} + {@code av1_set_entropy_contexts}.
 *
 * <p>The reference is the real libaom {@code get_txb_ctx} over the grids (see
 * {@link Av1CoeffPlaneSupport}); the same reader re-derives the flow in Java,
 * encodes each block with a faithful {@code od_ec} port and decodes it with the
 * slice-3e decoder. The Kof driver must reproduce the decoded levels and the
 * final above/left arrays on every target.
 */
class Av1CoeffPlaneE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void goldenMatchesSecondReader() {
        assertEquals(golden(), String.join("\n", goldenFacts()));
    }

    @Test
    void av1CoeffPlaneOnJvm() throws Exception {
        assertEquals(kofExpectedText(), runJvm(kofProbe()));
    }

    @Test
    void av1CoeffPlaneOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(kofExpectedText(), result.stdout().strip());
    }

    @Test
    void av1CoeffPlaneOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(kofExpectedText(), runNativeX86(kofProbe()));
    }

    @Test
    void av1CoeffPlaneOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(kofExpectedText(), runCrossPerGrid("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void av1CoeffPlaneOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(kofExpectedText(), runCrossPerGrid("aarch64", Target.NATIVE_AARCH64));
    }

    @Test
    void av1CoeffPlaneOnJs() throws Exception {
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        assertEquals(kofExpectedText(), stdout.toString(StandardCharsets.UTF_8).strip());
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

    private String runNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"));
    }

    /**
     * Runs each plane grid in its own cross binary (one process per grid) and
     * concatenates the outputs. The cross native backends' conservative GC
     * (known-bugs §602) misses a live receiver once one binary decodes ~20+
     * blocks, so a plane per process is the documented workaround; the output is
     * byte-for-byte identical to a single run.
     */
    private String runCrossPerGrid(String arch, Target target) throws Exception {
        StringBuilder all = new StringBuilder();
        List<Av1CoeffPlaneSupport.Grid> gs = grids();
        for (int gi = 0; gi < gs.size(); gi++) {
            final int gridIndex = gi;
            String code = kofProbe(List.of(gs.get(gi)));
            Path root = tmp.resolve("cross-" + arch + "-" + gi);
            Files.createDirectories(root);
            Files.writeString(root.resolve("Main.kf"), code);
            Path out = root.resolve("out");
            withLibrary(root, () -> {
                CompilationResult result = compile(root.resolve("Main.kf"), out, target);
                assertTrue(result.success(),
                        () -> arch + " grid " + gridIndex + " compile: "
                                + result.diagnostics().getDiagnostics());
                return null;
            });
            Path binary = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(binary), arch + " grid " + gi + " binary must exist");
            ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").strip();
            assertEquals(0, process.waitFor(), arch + " grid " + gi + " output: " + output);
            if (all.length() > 0) all.append('\n');
            all.append(output);
        }
        return all.toString();
    }

    private String runBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private CompilationResult compile(Path source, Path out, Target target) {
        return driver.compile(source, out, target);
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
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

    @Override
    public String libraryName() {
        return "image";
    }

    @Override
    public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Av1Symbol.kf");
    }
}
