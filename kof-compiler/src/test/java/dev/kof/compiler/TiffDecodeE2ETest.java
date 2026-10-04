package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static dev.kof.compiler.TiffDecodeFixtures.REFUSALS;
import static dev.kof.compiler.TiffDecodeFixtures.golden;
import static dev.kof.compiler.TiffDecodeFixtures.javaRefusal;
import static dev.kof.compiler.TiffDecodeFixtures.probe;
import static dev.kof.compiler.TiffDecodeFixtures.refusalProbe;
import static dev.kof.compiler.TiffDecodeFixtures.writeFixtures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the pure-Kof TIFF decoder in {@code libs/image/Tiff.kf}
 * (image-vision pixel slice, provisional {@code Raster} surface).
 *
 * <p>Baseline TIFF 6.0: both byte orders, 8-bit samples, chunky layout,
 * grayscale / RGB / RGBA / gray-alpha / palette, one or many strips, and
 * Compression 1 (none) or 32773 (PackBits). Values whose byte size fits the
 * 4-byte IFD field are read inline (TIFF 6.0 §2) — a real correctness point
 * that a first draft (and the fixture writer) missed. Everything outside the
 * subset is refused with a named {@code IMAGE:} diagnostic.
 *
 * <p>Fixtures are hand-built byte-exactly per the spec and cross-validated
 * offline against PIL and Java {@code ImageIO} before the golden was pinned.
 * The golden is produced by a second, independent plain-Java reader
 * ({@link TiffDecodeFixtures#readFacts}) — the Kof library and the Java reader
 * agreeing on the pixels is the proof. Proven on JVM + Native x86-64 +
 * riscv64(qemu) + aarch64(qemu) + Script; JS refuses {@code IOJS001} (the
 * library reads files through {@code kof.io}). No compiler change.
 */
class TiffDecodeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final List<String> REFUSAL_GOLDEN = List.of(
            "IMAGE: TIFF compression not covered",
            "IMAGE: TIFF planar configuration not covered",
            "IMAGE: TIFF predictor not covered",
            "IMAGE: TIFF bits per sample not covered",
            "IMAGE: TIFF photometric not covered",
            "IMAGE: TIFF extra samples not covered",
            "IMAGE: TIFF samples per pixel not covered",
            "IMAGE: TIFF associated alpha not covered",
            "IMAGE: TIFF palette samples not covered");

    @Test
    void tiffDecodesOnJvm() throws Exception {
        Path dir = writeFixtures(tmp.resolve("jvm-tiff"));
        assertEquals(golden(dir), runJvm(probe(dir)));
    }

    @Test
    void tiffDecodesOnScript() throws Exception {
        Path root = tmp.resolve("script-tiff");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(golden(dir), result.stdout().strip());
    }

    @Test
    void tiffDecodesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = writeFixtures(tmp.resolve("x86-tiff"));
        assertEquals(golden(dir), runNativeX86(probe(dir)));
    }

    @Test
    void tiffDecodesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = writeFixtures(tmp.resolve("riscv-tiff"));
        assertEquals(golden(dir), runCrossCode("riscv64", Target.NATIVE_RISCV64, probe(dir)));
    }

    @Test
    void tiffDecodesOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Path dir = writeFixtures(tmp.resolve("aarch-tiff"));
        assertEquals(golden(dir), runCrossCode("aarch64", Target.NATIVE_AARCH64, probe(dir)));
    }

    @Test
    void tiffOnJsIsIoJs001() throws Exception {
        Path root = tmp.resolve("js-tiff");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readBytes binding (R6)");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("IOJS001"),
                () -> "expected IOJS001, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void tiffRefusalsOnJvm() throws Exception {
        Path dir = writeFixtures(tmp.resolve("refusal-tiff"));
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < REFUSALS.size(); i++) {
            if (i > 0) {
                actual.append('\n');
            }
            actual.append(runJvm(refusalProbe(dir.resolve(REFUSALS.get(i) + ".tif"))));
        }
        assertEquals(String.join("\n", REFUSAL_GOLDEN), actual.toString());
    }

    @Test
    void tiffJavaReaderAgrees() throws Exception {
        Path dir = writeFixtures(tmp.resolve("oracle-tiff"));
        // Positive facts: the Kof golden was built by the Java reader, so the
        // JVM run proving that golden IS the agreement for the positive set.
        assertEquals(golden(dir), runJvm(probe(dir)));
        // Refusals: the Kof library and the Java reader must name the SAME
        // diagnostic for every out-of-subset fixture.
        for (int i = 0; i < REFUSALS.size(); i++) {
            assertEquals(REFUSAL_GOLDEN.get(i), javaRefusal(dir, REFUSALS.get(i)),
                    "java reader refusal for " + REFUSALS.get(i));
        }
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
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

    private String runCrossCode(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(), arch + " compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), arch + " output: " + output);
        return output;
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

    private static void copyLibrary(Path destinationRoot) throws Exception {
        Path sourceRoot = findLibraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve("image")
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findLibraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/image");
        if (Files.isRegularFile(fromRepository.resolve("Tiff.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/image").normalize();
        if (Files.isRegularFile(fromModule.resolve("Tiff.kf"))) return fromModule;

        throw new IllegalStateException("libs/image not found from " + workingDirectory);
    }
}
