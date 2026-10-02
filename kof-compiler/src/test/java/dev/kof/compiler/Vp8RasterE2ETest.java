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
 * End-to-end coverage for the WebP lossy (`VP8 `) raster route — slice 7 of the
 * pure-Kof VP8 chain ({@code D-WEBP-LOSSY-PURE-KOF}).
 *
 * <p>{@code decodeRaster(path)} now dispatches a {@code VP8 } chunk through the
 * full pure-Kof pipeline — frame header, modes, coefficients, dequantization +
 * intra prediction and the in-loop deblocking filter (slice 6) — to the bounded
 * {@code Raster} view, converting the filtered YUV 4:2:0 planes to interleaved
 * RGB (BT.601 limited range, nearest chroma). The golden is libwebp's own
 * Y/U/V planes (already proven by slices 5 and 6) with the documented
 * limited-range matrix; the formula was validated against libwebp's RGB output
 * on solid chroma. The five fixtures cover a flat luma, an all-{@code B_PRED}
 * frame, skipped macroblocks, every coefficient category and a partial (20x28)
 * frame.
 *
 * <p>Proven on JVM + Native x86-64 + riscv64 (qemu) + Script; no compiler
 * change.
 */
class Vp8RasterE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String FLAT16_HEX =
            "52494646240000005745425056503820180000005001009d012a1000100000c01225a400047400"
          + "00fe000000";
    private static final String DIAG32_HEX =
            "524946465800000057454250565038204c0000009004009d012a200020002e25188c462122"
          + "22220200244b480005cf044784367991338898ded3a8a046b6753b8000fefffe13039ed077"
          + "8e57d36a60c4b734e4a2442744e950c19c65de000000";
    private static final String SKIP64_HEX =
            "524946465c000000574542505650382050000000b004009d012a400040003ed168b052a82624"
          + "a2a20801001a0969062807eff89d000857b2d956263da7d79b57400000feeb36fffe84d8f6a2"
          + "bffff424ffe26cff89b3e115b4f4b331cb99d6de02000000";
    private static final String CAT64_HEX =
            "524946468a00000057454250565038207e0000003005009d012a400040003f71b6ce5dbbb1"
          + "29bfac1d5803f02e096206700c40ffa23f042099e275feffeb03ffffe4c86ffbf000feefea"
          + "3c87dbba1ae81df7cea6b0ba4b7d01db3e51344d74e50de3adef74cd294734473b63de1363"
          + "d6c97e017c2accc9adb06c756c3ebda3959f373f50db6ff0739c41c1136c0872340000";
    private static final String ODD20X28_HEX =
            "52494646a800000057454250565038209c0000005005009d012a14001c003ec156a64ca7a4a3"
          + "a2280aa8f018096c009d32ecff87a01f8881342620bd03b33f585d4a12a857ee0000fefe59"
          + "eb30acc58c43679567f68084613e1957977a8cb7b65fe3bb8e97e05cd063a1fed9a94c8037"
          + "7e6bff0514ecb817fda1e333df1e08f14c0593f30987a5370197d36985c809ce18c1854018"
          + "0abfefc4cf3fff443546b349d1e4fff58bcf5978fb474a27f50000";

    private static final String GOLDEN =
              "flat16 WEBP 16x16 ch=3 98304:15302656\n"
            + "diag32 WEBP 32x32 ch=3 380427:2335211\n"
            + "skip64 WEBP 64x64 ch=3 1049760:10522272\n"
            + "cat64 WEBP 64x64 ch=3 1299351:15983607\n"
            + "odd20x28 WEBP 20x28 ch=3 207858:3415570";

    @Test
    void vp8RasterOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runJvm(root, probe(dir)));
    }

    @Test
    void vp8RasterOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runNativeX86(root, probe(dir)));
    }

    @Test
    void vp8RasterOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, root, probe(dir)));
    }

    @Test
    void vp8RasterOnScript() throws Exception {
        Path root = tmp.resolve("script-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    private Path writeFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("flat16.webp"), hex(FLAT16_HEX));
        Files.write(dir.resolve("diag32.webp"), hex(DIAG32_HEX));
        Files.write(dir.resolve("skip64.webp"), hex(SKIP64_HEX));
        Files.write(dir.resolve("cat64.webp"), hex(CAT64_HEX));
        Files.write(dir.resolve("odd20x28.webp"), hex(ODD20X28_HEX));
        return dir;
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String probe(Path dir) {
        String base = RasterDecodeFixtures.path(dir);
        return "import image.Raster\n"
             + "\n"
             + "String probe(String label, String path) {\n"
             + "    var r = decodeRaster(path)\n"
             + "    var sum = 0 var hash = 0 var i = 0\n"
             + "    while (i < r.samples.length) {\n"
             + "        var v = r.samples[i]\n"
             + "        sum = sum + v\n"
             + "        hash = ((hash << 5) + hash + v) & 16777215\n"
             + "        i = i + 1\n"
             + "    }\n"
             + "    return label + \" \" + r.format + \" \" + r.width + \"x\" + r.height\n"
             + "        + \" ch=\" + r.channels + \" \" + sum + \":\" + hash\n"
             + "}\n\n"
             + "main() {\n"
             + "    println(probe(\"flat16\", \"" + base + "/flat16.webp\"))\n"
             + "    println(probe(\"diag32\", \"" + base + "/diag32.webp\"))\n"
             + "    println(probe(\"skip64\", \"" + base + "/skip64.webp\"))\n"
             + "    println(probe(\"cat64\", \"" + base + "/cat64.webp\"))\n"
             + "    println(probe(\"odd20x28\", \"" + base + "/odd20x28.webp\"))\n"
             + "}\n";
    }

    private String runJvm(Path root, String code) throws Exception {
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String runNativeX86(Path root, String code) throws Exception {
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"));
    }

    private String runCrossCode(String arch, Target target, Path root, String code) throws Exception {
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
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

    private static String runBinary(Path binary) throws Exception {
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

    private <T> T withLibrary(Path root, Vp8Action<T> action) throws Exception {
        Path lib = root.resolve("kof-install/lib/kof-libs/image");
        Path sourceRoot = libraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = lib.resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
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
    private interface Vp8Action<T> {
        T get() throws Exception;
    }

    private static Path libraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/image");
        if (Files.isDirectory(fromRepository)) return fromRepository;
        Path fromModule = workingDirectory.resolve("../libs/image").normalize();
        if (Files.isDirectory(fromModule)) return fromModule;
        throw new IllegalStateException("libs/image not found from " + workingDirectory);
    }
}
