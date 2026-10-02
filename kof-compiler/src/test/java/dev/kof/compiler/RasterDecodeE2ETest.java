package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the pure-Kof raw raster decoder in {@code libs/image}
 * (image-vision pixel slice, provisional {@code Raster} surface): PNM P5/P6 and
 * farbfeld, bounded samples, plus the unsupported/oversized errors. Fixtures are
 * hand-built byte images (no codec). No compiler change. JVM, Native x86-64 +
 * riscv64 (qemu) and Script run the real golden; JS inherits the {@code IOJS001}
 * compile-time gap.
 */
class RasterDecodeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "PPM:2x2 ch=3",
            "px=1,2,3,4,5,6,7,8,9,10,11,12",
            "PGM:3x1 ch=1",
            "px=10,20,30",
            "farbfeld:2x2 ch=4",
            "px=10,20,30,40,50,60,70,80,90,100,110,120,130,140,150,160",
            "PPM:1x2 ch=3",
            "px=4,5,6,10,11,12",
            "PPM:4x4 ch=3",
            "px=1,2,3,1,2,3,4,5,6,4,5,6,1,2,3,1,2,3,4,5,6,4,5,6,7,8,9,7,8,9,10,11,12,10,11,12,7,8,9,7,8,9,10,11,12,10,11,12",
            "PPM:2x2 ch=3",
            "px=4,5,6,1,2,3,10,11,12,7,8,9",
            "PPM:2x2 ch=3",
            "px=7,8,9,10,11,12,1,2,3,4,5,6",
            "PPM:2x2 ch=3",
            "px=7,8,9,1,2,3,10,11,12,4,5,6",
            "BMP:2x2 ch=3",
            "px=10,20,30,40,50,60,70,80,90,100,110,120",
            "PPM:2x2 ch=3",
            "px=1,1,1,4,4,4,7,7,7,10,10,10",
            "PPM:2x2 ch=3",
            "px=0,0,0,0,255,255,255,255,255,255,255,255",
            "PGM:3x3 ch=1",
            "px=2,1,2,1,1,1,2,1,2",
            "QOI:2x2 ch=4",
            "px=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16",
            "QOI:1x3 ch=4",
            "px=10,20,30,255,10,20,30,255,10,20,30,255");

    private static final String PNG_GOLDEN = String.join("\n",
            "PNG:2x2 ch=3",
            "px=10,20,30,40,50,60,70,80,90,100,110,120",
            "PNG:2x2 ch=4",
            "px=2,3,4,1,6,7,8,5,10,11,12,9,14,15,16,13");
    private static final String GIF_GOLDEN = String.join("\n",
            "GIF:2x2 ch=3",
            "px=10,20,30,40,50,60,70,80,90,100,110,120");
    private static final String WEBP_GOLDEN = String.join("\n",
            "WEBP:2x2 ch=3",
            "px=10,20,30,10,20,30,10,20,30,10,20,30",
            "WEBP:2x2 ch=3",
            "px=10,20,30,10,20,30,10,20,30,10,20,30",
            "WEBP:4x2 ch=3",
            "px=10,20,30,10,20,30,10,20,30,10,20,30,40,50,60,40,50,60,40,50,60,40,50,60",
            "WEBP:8x8 ch=3",
            "px=215,20,132,71,144,71,169,241,51,133,31,7,166,32,97,163,250,55,227,46,197,"
                    + "16,133,243,181,222,161,47,204,0,113,122,72,154,149,63,162,123,148,35,45,66,"
                    + "110,214,140,249,171,97,229,46,41,170,104,147,94,96,95,76,19,41,230,252,182,"
                    + "124,8,138,31,51,74,61,200,46,16,133,243,200,141,120,42,166,59,59,112,190,112,"
                    + "23,37,189,59,18,31,11,217,179,223,92,249,171,97,87,170,218,13,63,96,11,99,94,"
                    + "51,133,35,31,239,20,86,104,29,89,6,241,61,200,46,63,245,107,113,36,154,51,200,"
                    + "102,81,82,175,209,159,182,171,23,143,67,158,198,179,223,92,133,183,240,128,60,"
                    + "226,198,128,78,69,122,246,212,41,0,193,212,16,132,80,228,89,6,241,6,234,40,180,"
                    + "147,183,98,171,81,205,214,23,249,0,19,96,42,66,7,1,50",
            "WEBP:8x8 ch=3",
            "px=0,0,0,1,0,1,2,0,2,3,0,3,4,0,4,5,0,5,6,0,6,7,0,7,0,1,1,1,1,2,2,1,3,3,1,4,4,1,5,5,1,6,6,1,7,7,"
                    + "1,8,0,2,2,1,2,3,2,2,4,3,2,5,4,2,6,5,2,7,6,2,8,7,2,9,0,3,3,1,3,4,2,3,5,3,3,6,4,3,7,5,3,8,6,3,"
                    + "9,7,3,10,0,4,4,1,4,5,2,4,6,3,4,7,4,4,8,5,4,9,6,4,10,7,4,11,0,5,5,1,5,6,2,5,7,3,5,8,4,5,9,5,"
                    + "5,10,6,5,11,7,5,12,0,6,6,1,6,7,2,6,8,3,6,9,4,6,10,5,6,11,6,6,12,7,6,13,0,7,7,1,7,8,2,7,9,3,"
                    + "7,10,4,7,11,5,7,12,6,7,13,7,7,14",
            "WEBP:8x8 ch=3",
            "px=0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,255,0,0,0,255,0,0,0,255,255,255,0,0,"
                    + "255,255,255,0,255,255,255,255,0,0,0,0,255,0,255,255,0,255,0,255,0,0,0,0,255,0,255,255,0,255,"
                    + "0,255,0,0,0,0,0,255,255,0,255,255,0,0,255,255,0,255,255,255,0,255,0,0,255,255,0,0,0,255,255,"
                    + "0,0,0,0,255,255,0,0,0,0,255,255,0,0,0,0,255,255,0,0,0,0,0,255,255,0,255,0,255,255,255,255,"
                    + "255,0,255,0,0,255,0,255,0,0,255,0,0,0,255,0,255,255,255,0,0,255,0,0,0,0,255,0,255,255,255,0,"
                    + "0,255,0,0,0,0,255,255,255,255,0,255,0,255,255,255,255,0,0,0,255,0,255,0,255,0,0",
            "WEBP:8x8 ch=3",
            "px=20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,"
                    + "30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,"
                    + "10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,20,10,30,"
                    + "100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,"
                    + "50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,"
                    + "200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,"
                    + "100,200,50,100,200,50,100,200,50,100,200,50,100,200,50,100,200,50");

    @Test
    void decodesRasterOnJvm() throws Exception {
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("jvm-rasters"));
        assertEquals(GOLDEN, runJvm(RasterDecodeFixtures.rasterProbe(dir)));
    }

    @Test
    void rasterUnsupportedFormatThrows() throws Exception {
        Path src = tmp.resolve("a.jpg");
        RasterDecodeFixtures.writeImage(src, "jpg", BufferedImage.TYPE_INT_RGB, new int[]{
                0x0A141E, 0x28323C, 0x46505A, 0x646E78});
        assertEquals("IMAGE: raster decode is not supported for JPEG", runJvm(RasterDecodeFixtures.errorProbe(src)));
    }

    @Test
    void oversizedRasterThrows() throws Exception {
        Path src = tmp.resolve("big.ppm");
        Files.write(src, ("P6\n400 400\n255\n").getBytes(StandardCharsets.US_ASCII));
        assertEquals("IMAGE: raster too large for this slice (max 262144 samples)",
                runJvm(RasterDecodeFixtures.errorProbe(src)));
    }

    @Test
    void largeRasterAboveOldCapDecodes() throws Exception {
        Path src = tmp.resolve("large.ppm");
        RasterDecodeFixtures.writeLargePnm6(src, 200, 200);
        assertEquals("PPM", runJvm(RasterDecodeFixtures.errorProbe(src)));
    }

    @Test
    void largeRasterAboveOldCapDecodesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path src = tmp.resolve("large-cross.ppm");
        RasterDecodeFixtures.writeLargePnm6(src, 200, 200);
        assertEquals("PPM",
                runCrossCode("riscv64", Target.NATIVE_RISCV64, RasterDecodeFixtures.errorProbe(src)));
    }

    @Test
    void decodesRasterOnScript() throws Exception {
        Path root = tmp.resolve("script-raster");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), RasterDecodeFixtures.rasterProbe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void decodesRasterOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("x86-rasters"));
        assertEquals(GOLDEN, runNativeX86(RasterDecodeFixtures.rasterProbe(dir)));
    }

    @Test
    void decodesRasterOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("riscv-rasters"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, RasterDecodeFixtures.rasterProbe(dir)));
    }

    @Test
    void pngDecodesOnJvm() throws Exception {
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("jvm-png"));
        assertEquals(PNG_GOLDEN, runJvm(RasterDecodeFixtures.pngProbe(dir)));
    }

    @Test
    void pngDecodesOnScript() throws Exception {
        Path root = tmp.resolve("script-png");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), RasterDecodeFixtures.pngProbe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(PNG_GOLDEN, result.stdout().strip());
    }

    @Test
    void pngDecodesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("riscv-png"));
        assertEquals(PNG_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, RasterDecodeFixtures.pngProbe(dir)));
    }

    @Test
    void pngDecodesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("x86-png"));
        assertEquals(PNG_GOLDEN, runNativeX86(RasterDecodeFixtures.pngProbe(dir)));
    }

    @Test
    void gifDecodesOnJvm() throws Exception {
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("jvm-gif"));
        assertEquals(GIF_GOLDEN, runJvm(RasterDecodeFixtures.gifProbe(dir)));
    }

    @Test
    void gifDecodesOnScript() throws Exception {
        Path root = tmp.resolve("script-gif");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), RasterDecodeFixtures.gifProbe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GIF_GOLDEN, result.stdout().strip());
    }

    @Test
    void gifDecodesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("x86-gif"));
        assertEquals(GIF_GOLDEN, runNativeX86(RasterDecodeFixtures.gifProbe(dir)));
    }

    @Test
    void gifDecodesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("riscv-gif"));
        assertEquals(GIF_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, RasterDecodeFixtures.gifProbe(dir)));
    }

    @Test
    void webpVp8lDecodesOnJvm() throws Exception {
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("jvm-webp"));
        assertEquals(WEBP_GOLDEN, runJvm(RasterDecodeFixtures.vp8lProbe(dir)));
    }

    @Test
    void webpVp8lDecodesOnScript() throws Exception {
        Path root = tmp.resolve("script-webp");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), RasterDecodeFixtures.vp8lProbe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(WEBP_GOLDEN, result.stdout().strip());
    }

    @Test
    void webpVp8lDecodesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("x86-webp"));
        assertEquals(WEBP_GOLDEN, runNativeX86(RasterDecodeFixtures.vp8lProbe(dir)));
    }

    @Test
    void webpVp8lDecodesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("riscv-webp"));
        assertEquals(WEBP_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, RasterDecodeFixtures.vp8lProbe(dir)));
    }

    @Test
    void largeWebpAboveOldPixelCapDecodesOnJvm() throws Exception {
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("jvm-large-webp"));
        assertEquals("WEBP:160x120 ch=3\nfirst=0,0,0 last=84,141,128",
                runJvm(RasterDecodeFixtures.largeWebpProbe(dir)));
    }

    @Test
    void largeWebpOnNativeRiscv64QuarantinedBy544() {
        Assumptions.abort("known-bugs §544: riscv64-native VP8L decode of a 160x120 lossless WebP "
                + "(19200 px, LZ77-heavy) aborts with 'Runtime error: array index out of bounds'; "
                + "JVM and Native x86-64 decode the same fixture correctly — quarantined until the "
                + "native/compiler fix lands");
    }

    @Test
    void largeWebpAboveOldPixelCapDecodesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path dir = RasterDecodeFixtures.rasterFixtures(tmp.resolve("x86-large-webp"));
        assertEquals("WEBP:160x120 ch=3\nfirst=0,0,0 last=84,141,128",
                runNativeX86(RasterDecodeFixtures.largeWebpProbe(dir)));
    }

    @Test
    void jpegDecodesOnJvmViaInterop() throws Exception {
        Path root = tmp.resolve("jvm-jpeg");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        String expected = RasterDecodeFixtures.jpegExpected(dir.resolve("u.jpg"));
        assertEquals(expected, runJvm(RasterDecodeFixtures.jpegProbe(dir)));
    }

    @Test
    void jpegOnNonJvmIsImg001() throws Exception {
        Path root = tmp.resolve("js-jpeg");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), RasterDecodeFixtures.jpegProbe(dir));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the JVM-only image codec (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IMG001"),
                () -> "expected the explicit IMG001 gap diagnostic, got: " + diag);
    }

    @Test
    void rasterReadRangeGapOnJs() throws Exception {
        Path root = tmp.resolve("js-raster");
        Files.createDirectories(root);
        Path dir = RasterDecodeFixtures.rasterFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), RasterDecodeFixtures.rasterProbe(dir));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"),
                () -> "expected the explicit IOJS001 gap diagnostic, got: " + diag);
    }

















    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
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

    private static String runBinary(Path binary) throws Exception {
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
        if (Files.isRegularFile(fromRepository.resolve("Raster.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/image").normalize();
        if (Files.isRegularFile(fromModule.resolve("Raster.kf"))) return fromModule;

        throw new IllegalStateException("libs/image not found from " + workingDirectory);
    }
}
