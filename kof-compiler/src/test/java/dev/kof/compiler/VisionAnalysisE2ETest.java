package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the first {@code kof.vision} slice in
 * {@code libs/vision} (plan §11/§14): a luminance histogram, its normalized
 * form and Otsu's optimal threshold + binarization, over the shared
 * {@code image.Raster}. The golden is a hand-built bimodal PGM (10×30, 6×220)
 * so Otsu's level and the black/white output are deterministic. Proven on JVM,
 * Native x86-64 + riscv64 (qemu) and Script; JS inherits the {@code IOJS001}
 * decode gap but the pure computations are target-independent. No compiler
 * change.
 */
class VisionAnalysisE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "P5:4x4 ch=1 px=16",
            "hist=6,10",
            "norm=375",
            "otsu=30 bw=0,0,255",
            "sobelRamp=SOBEL:5x5 ch=1",
            "sobelRamp-mid=0 edge=98",
            "sobelDot=0 border=0,0",
            "comp=2 a=1 b=2 bg=0",
            "erode=0 dilate=255,255",
            "open=0 close=255",
            "eqLow=0,255 out=0,255",
            "eqSix=47,94,141,188",
            "areas=3,4",
            "box1=0,0,1,1",
            "box2=3,1,4,2",
            "regions=2 r1=1@0,0 a3");

    @Test
    void visionAnalysisOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-vision");
        Files.createDirectories(root);
        Path dir = VisionFixtures.visionFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, visionJvm(root, VisionFixtures.visionProbe(dir)));
    }

    @Test
    void visionAnalysisOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-vision");
        Files.createDirectories(root);
        Path dir = VisionFixtures.visionFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, visionX86(root, VisionFixtures.visionProbe(dir)));
    }

    @Test
    void visionAnalysisOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(hasTool("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-vision");
        Files.createDirectories(root);
        Path dir = VisionFixtures.visionFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, visionCross("riscv64", Target.NATIVE_RISCV64, root,
                VisionFixtures.visionProbe(dir)));
    }

    @Test
    void visionAnalysisOnScript() throws Exception {
        Path root = tmp.resolve("script-vision");
        Files.createDirectories(root);
        Path dir = VisionFixtures.visionFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), VisionFixtures.visionProbe(dir));
        KofInterpreter.Result result = stageVisionLibraries(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    private String visionJvm(Path root, String code) throws Exception {
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = stageVisionLibraries(root, () -> driver.compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());

        var stdout = new java.io.ByteArrayOutputStream();
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

    private String visionX86(Path root, String code) throws Exception {
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        stageVisionLibraries(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runVisionBinary(out.resolve("Default/Main"));
    }

    private String visionCross(String arch, Target target, Path root, String code) throws Exception {
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        stageVisionLibraries(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(),
                    () -> arch + " compile: " + result.diagnostics().getDiagnostics());
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

    private static String runVisionBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private static boolean hasTool(String... cmds) {
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

    private <T> T stageVisionLibraries(Path root, VisionAction<T> action) throws Exception {
        copyPackage(root.resolve("kof-install/lib/kof-libs"), "image");
        copyPackage(root.resolve("kof-install/lib/kof-libs"), "vision");
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
    private interface VisionAction<T> {
        T get() throws Exception;
    }

    private static void copyPackage(Path destinationRoot, String pkg) throws Exception {
        Path sourceRoot = libraryRoot(pkg);
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve(pkg)
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path libraryRoot(String pkg) {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/" + pkg);
        if (Files.isDirectory(fromRepository)) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/" + pkg).normalize();
        if (Files.isDirectory(fromModule)) return fromModule;

        throw new IllegalStateException("libs/" + pkg + " not found from " + workingDirectory);
    }
}
