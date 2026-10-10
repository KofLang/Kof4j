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
 * End-to-end coverage for the PNG {@code tRNS} transparency chunk of the
 * pure-Kof decoder in {@code libs/image/Png.kf} (image-vision pixel slice,
 * provisional {@code Raster} surface).
 *
 * <p>The decoder ignored {@code tRNS}; this slice adds it. A {@code tRNS} chunk
 * turns the output into an alpha channel: grayscale (color type 0) becomes
 * gray+alpha with the alpha indexed by the gray value, RGB (color type 2)
 * becomes RGBA with the alpha indexed by the 24-bit color key, and palette
 * (color type 3) becomes RGBA with the per-color alpha (missing entries are
 * opaque). A {@code tRNS} chunk on a color type that cannot carry transparency
 * (4/6) is refused with an explicit {@code IMAGE:} diagnostic.
 *
 * <p>The tRNS component is always a 16-bit big-endian value (PNG spec
 * §11.3.2) regardless of the image bit depth, so the color key is mapped into
 * the same 8-bit space the decoded samples use: the high byte for 16-bit images
 * (the {@code pngUnpack16} rule), a {@code 255/maxval} scaling for sub-byte
 * gray, and the byte itself for 8-bit. This is what makes a 16-bit sample that
 * shares its high byte with the key (but not its low byte) stay opaque.
 *
 * <p>Six fixtures cover the three allowed color types and the depth-sensitive
 * key mapping: gray 12x6, RGB 10x5, palette 11x7 (all 8-bit), 16-bit gray 4x1,
 * 16-bit RGB 4x1 and 4-bit gray 4x2. Each is independently readable by PIL and
 * Java {@code ImageIO}. Golden = the decoded sample sum and a 24-bit rolling
 * hash. Proven on JVM + Native x86-64 + riscv64 (qemu) + Script; no compiler
 * change.
 */
class PngTransparencyE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String TGRAY_HEX =
            "89504e470d0a1a0a0000000d494844520000000c000000060800000000d285205d00000002"
          + "74524e5300809b2b4e18000000594944415478da014e00b1ff0000112233445566778899aabb"
          + "000d1e2f405162738495a6b7c8001a2b3c4d5e6f8091a2b3c4d5002738495a6b7c8d9eafc0d1"
          + "e2003445566778899aabbccddeef00415263748596a7b8c9daebfca2fe237180706942000000"
          + "0049454e44ae426082";
    private static final String TRGB_HEX =
            "89504e470d0a1a0a0000000d494844520000000a000000050802000000f306ea3f00000006"
          + "74524e53000a0014001ec53629ff000000a34944415478da63606060e0656596e26253e7e7"
          + "3411e17194e4f793138a5616cdd09028d5956660e716141110511415d79392b29697f550510"
          + "8d5544ed253cb37d6acb1d061e01353929656d550d03055d576d2d2f3d7378c3131c9b4342fb"
          + "3b36a76b665105534565233d3d7b6b431b0f134b50fb3724ab6772d70f1a8f5f4eef2f36390d"
          + "171d134743733f372b6f60d700888750dcef20a2bf78f6c09899918190f00b28c20225c4462fd"
          + "0000000049454e44ae426082";
    private static final String TPAL_HEX =
            "89504e470d0a1a0a0000000d494844520000000b000000070803000000e9b0476f0000000c"
          + "504c54450000003c5a7878b4f0b40e687d60d0e50000000474524e530080ff40b75ec1f80000"
          + "001c4944415478da63606066628462064604878109c1415282c2c1a51e00133200737bcd160"
          + "f0000000049454e44ae426082";

    private static final String T16G_HEX =
            "89504e470d0a1a0a0000000d49484452000000040000000110000000008cc78c"
          + "520000000274524e5312342fd3495e000000114944415478da63103211fabffa"
          + "2c0303000c7f02d0f91cb3720000000049454e44ae426082";
    private static final String T16RGB_HEX =
            "89504e470d0a1a0a0000000d494844520000000400000001100200000026ce44"
          + "d90000000674524e53123456789abc89e44ee6000000224944415478da631032"
          + "09ab98b547e87fd8ff59ff191819981898ffff7ff7eeee5d0081d00c04d03c3b"
          + "130000000049454e44ae426082";
    private static final String TSUB4_HEX =
            "89504e470d0a1a0a0000000d49484452000000040000000204000000009f33cf"
          + "be0000000274524e53000a964624260000000e4944415478da63105dcfb06039"
          + "000512020c72c604020000000049454e44ae426082";

    private static final String GOLDEN =
              "tgray PNG 12x6 ch=2 27177:13599977\n"
            + "trgb PNG 10x5 ch=4 20975:14788335\n"
            + "tpal PNG 11x7 ch=4 29545:1876073\n"
            + "t16g PNG 4x1 ch=2 717:16766381\n"
            + "t16rgb PNG 4x1 ch=4 1740:9520396\n"
            + "tsub4 PNG 4x2 ch=2 2261:14524949";

    @Test
    void trnsPngOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-pngtrns");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runJvm(root, probe(dir)));
    }

    @Test
    void trnsPngOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-pngtrns");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runNativeX86(root, probe(dir)));
    }

    @Test
    void trnsPngOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-pngtrns");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, root, probe(dir)));
    }

    @Test
    void trnsPngOnScript() throws Exception {
        Path root = tmp.resolve("script-pngtrns");
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
        Files.write(dir.resolve("tgray.png"), hex(TGRAY_HEX));
        Files.write(dir.resolve("trgb.png"), hex(TRGB_HEX));
        Files.write(dir.resolve("tpal.png"), hex(TPAL_HEX));
        Files.write(dir.resolve("t16g.png"), hex(T16G_HEX));
        Files.write(dir.resolve("t16rgb.png"), hex(T16RGB_HEX));
        Files.write(dir.resolve("tsub4.png"), hex(TSUB4_HEX));
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
             + "    println(probe(\"tgray\", \"" + base + "/tgray.png\"))\n"
             + "    println(probe(\"trgb\", \"" + base + "/trgb.png\"))\n"
             + "    println(probe(\"tpal\", \"" + base + "/tpal.png\"))\n"
             + "    println(probe(\"t16g\", \"" + base + "/t16g.png\"))\n"
             + "    println(probe(\"t16rgb\", \"" + base + "/t16rgb.png\"))\n"
             + "    println(probe(\"tsub4\", \"" + base + "/tsub4.png\"))\n"
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

    private <T> T withLibrary(Path root, PngTrnsAction<T> action) throws Exception {
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
    private interface PngTrnsAction<T> {
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
