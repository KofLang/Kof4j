package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the pure-Kof raw raster encoder in
 * {@code libs/image/Encode.kf} (image-vision pixel slice, §34.4): PNM P5/P6,
 * farbfeld, BMP 24-bit and QOI. The proof is a decode → encode → decode
 * round-trip that must be byte-identical on JVM, Native x86-64 + riscv64
 * (qemu) and Script, plus an independent {@code javax.imageio} read of the
 * emitted BMP (a real external decoder) and a QOI re-decode. No compiler
 * change. JS inherits the {@code IOJS001} gap (the library uses {@code kof.io}).
 */
class RasterEncodeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String RGB_GOLDEN = String.join("\n",
            "PPM:2x2 ch=3",
            "px=1,2,3,4,5,6,7,8,9,10,11,12",
            "PGM:2x2 ch=1",
            "px=1,4,7,10",
            "BMP:2x2 ch=3",
            "px=1,2,3,4,5,6,7,8,9,10,11,12",
            "farbfeld:2x2 ch=4",
            "px=10,20,30,40,50,60,70,80,90,100,110,120,130,140,150,160",
            "QOI:2x2 ch=4",
            "px=10,20,30,40,50,60,70,80,90,100,110,120,130,140,150,160",
            "QOI:2x2 ch=3",
            "px=1,2,3,4,5,6,7,8,9,10,11,12");

    @Test
    void encodeRoundTripsOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-encode");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        assertEquals(RGB_GOLDEN, jvmProgram(root, RasterDecodeFixtures.encodeProbe(dir)));
        assertBmpIsReadableByImageIo(dir.resolve("e.bmp"));
    }

    @Test
    void encodeRoundTripsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-encode");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        assertEquals(RGB_GOLDEN, x86Program(root, RasterDecodeFixtures.encodeProbe(dir)));
    }

    @Test
    void encodeRoundTripsOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-encode");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        assertEquals(RGB_GOLDEN,
                crossProgram("riscv64", Target.NATIVE_RISCV64, root,
                        RasterDecodeFixtures.encodeProbe(dir)));
    }

    @Test
    void encodeRoundTripsOnScript() throws Exception {
        Path root = tmp.resolve("script-encode");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), RasterDecodeFixtures.encodeProbe(dir));
        KofInterpreter.Result result = stageLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(RGB_GOLDEN, result.stdout().strip());
    }

    private static void assertBmpIsReadableByImageIo(Path bmp) throws Exception {
        BufferedImage img = ImageIO.read(bmp.toFile());
        assertTrue(img != null, "javax.imageio must read the emitted BMP: " + bmp);
        assertEquals(2, img.getWidth());
        assertEquals(2, img.getHeight());
        assertEquals(0x010203, img.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0x0A0B0C, img.getRGB(1, 1) & 0xFFFFFF);
    }

    private String jvmProgram(Path root, String code) throws Exception {
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = stageLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String x86Program(Path root, String code) throws Exception {
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        stageLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runNativeBinary(out.resolve("Default/Main"));
    }

    private String crossProgram(String arch, Target target, Path root, String code) throws Exception {
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        stageLibrary(root, () -> {
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

    private static String runNativeBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
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

    private <T> T stageLibrary(Path root, LibraryAction<T> action) throws Exception {
        copyImageLibrary(root.resolve("kof-install/lib/kof-libs"));
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
    private interface LibraryAction<T> {
        T get() throws Exception;
    }

    private static void copyImageLibrary(Path destinationRoot) throws Exception {
        Path sourceRoot = imageLibraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve("image")
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path imageLibraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/image");
        if (Files.isRegularFile(fromRepository.resolve("Raster.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/image").normalize();
        if (Files.isRegularFile(fromModule.resolve("Raster.kf"))) return fromModule;

        throw new IllegalStateException("libs/image not found from " + workingDirectory);
    }
}
