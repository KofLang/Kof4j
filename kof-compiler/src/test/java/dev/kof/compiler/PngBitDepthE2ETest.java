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
 * End-to-end coverage for the non-8-bit PNG paths of the pure-Kof decoder in
 * {@code libs/image/Png.kf} (image-vision pixel slice, provisional
 * {@code Raster} surface).
 *
 * <p>The decoder already handled bit depth 8; this slice adds the spec's other
 * bit depths: 1/2/4 (only gray/palette) scale to 8-bit by {@code 255/maxval},
 * and 16 (gray/RGB/gray+alpha/RGBA) downsamples to 8-bit by taking the high
 * byte — the same rule {@code farbfeld} uses — replacing the former
 * {@code IMAGE: unsupported PNG bit depth} refusal. Both the non-interlaced and
 * Adam7 paths are generalized to bits-per-pixel, so sub-byte samples move as
 * bit-fields and 16-bit samples as bytes.
 *
 * <p>Seven fixtures exercise every new bit-depth/color-type combination:
 * 1/2/4-bit gray, 16-bit gray, 16-bit RGB and 2/4-bit palette. Sub-byte gray
 * and the 16-bit files are hand-built (zlib); the palettes and all files are
 * independently readable by PIL and Java {@code ImageIO}. Golden = the decoded
 * sample sum and a 24-bit rolling hash. Proven on JVM + Native x86-64 +
 * riscv64 (qemu) + Script; no compiler change.
 */
class PngBitDepthE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String G1_HEX =
            "89504e470d0a1a0a0000000d4948445200000011000000090100000000fc93faa100000013"
          + "4944415478da63080d656058b5aa81010f0d00c1d90aa3defdedd80000000049454e44ae42"
          + "6082";
    private static final String G2_HEX =
            "89504e470d0a1a0a0000000d494844520000000f000000070200000000b85e505a0000001e"
          + "4944415478da63b0b4b4b460f0f3f3f361983c79f204862740c0802a0600c9f70c55be67f8"
          + "130000000049454e44ae426082";
    private static final String G4_HEX =
            "89504e470d0a1a0a0000000d494844520000000d00000009040000000009e114b70000003a"
          + "4944415478da6360ce3cafdafdd08121629f48d505b3090c6bc1020f183e81050c18dcc102"
          + "0d0c7320020c0fc1020a0c66608102866eb0c0010031251e45c48370db0000000049454e44"
          + "ae426082";
    private static final String G16_HEX =
            "89504e470d0a1a0a0000000d494844520000000b000000071000000000ab9534c200000077"
          + "4944415478da4dcbc50a80400005c007a2d8b57677b7a208feff7779ddb90f0086e1384190"
          + "654d334ddbf6bc304c12b02ccf4b92aa1a8665b96e10c471969525445151749d10c7f1fd28"
          + "4ad3a2a8ebae035df3bcaada7618e619746d9abe9fa6753d0ed0751c9765dfafeb7940d76d"
          + "3bcffb7edfeffb0174d3120db89de94f0000000049454e44ae426082";
    private static final String RGB16_HEX =
            "89504e470d0a1a0a0000000d494844520000000900000005100200000048a18d7f00000106"
          + "4944415478da1dc1cbef81000000e036c33cc3caa39f470fa564a5f5b01e13a9a98cdad066"
          + "72e1820b274e6efe70edf77d00f0af544aa7532904c9e73399c1000473394982a062713e6f"
          + "b54070b3e9766bb5e3912060f87aa5e96613c8660b856a15822a1508c230186e34380e49e8"
          + "7aafd7e9b86ebf8fa2fb3dc310c4f9cc7114f5788822c300e572bd8ee37f0992a46914a569"
          + "59264996b5ace190e38280e705218ea5c4eda6aa93c9eb359d6a1a00c318268a384e518ac2"
          + "f32cabaa86311e1b86e7c9b2694691a659d6e5629a8ef37cdab6eb7e3e9eb75a01edf66864"
          + "db0c2308cba592f0fdc542d7d7eb309ccdc2f074729cedf67ef7fd287abf83e070f87e77bb"
          + "38fe01b3ef3625792419930000000049454e44ae426082";
    private static final String P2_HEX =
            "89504e470d0a1a0a0000000d494844520000000b000000070203000000a3005fce0000000c"
          + "504c54450000003c5a7878b4f0b40e687d60d0e50000001b4944415478da63b0b4b460f0f3"
          + "f361983c7902c393274f1890f8007910093dcfebe4e30000000049454e44ae426082";
    private static final String P4_HEX =
            "89504e470d0a1a0a0000000d494844520000000d0000000904030000001b54bb5900000030"
          + "504c5445000000100c0720180e30241540301c503c2360482a705431806038906c3fa07846"
          + "b0844dc09054d09c5be0a862f0b4692d28bd230000003a4944415478da6360ce3cafdafdd0"
          + "8121629f48d505b3090c6bc1020f183e81050c18dcc1020d0c7320020c0fc1020a0c666081"
          + "02866eb0c0010031251e45c48370db0000000049454e44ae426082";

    private static final String I4_4_HEX =
            "89504e470d0a1a0a0000000d49484452000000130000000b04000000010a49347100000002"
          + "624b4744000f3a323ea30000000774494d4507ea0a010e1c205e80bd1f0000006f4944415408"
          + "d763e0606068686038c2e0c3e070c28121e95502c332bd050caf921e302cc8d178b280e109886"
          + "2d000510c965f37d61a305c9e1f2d7e81a116c8296010077204183602391b18a2819c0086887"
          + "d225517cce67c8ad8c0f009c60e609803637f603083b127305c80b10d006e033a95bd63d24d0"
          + "0000000049454e44ae426082";
    private static final String I3_4_HEX =
            "89504e470d0a1a0a0000000d49484452000000120000000a04030000013c6223040000002063"
          + "48524d00007a26000080840000fa00000080e8000075300000ea6000003a98000017709cba51"
          + "3c0000001b504c5445000000554466aa88cc111122775588ccaaee3322448877aaffffff58445a"
          + "3300000001624b47440886de957a0000000774494d4507ea0a010e1c205e80bd1f0000004a4944"
          + "415408d763600003172074707060505363484a0291092a40c80022141884c385c305180a4d0b4"
          + "d0b18804ce1000620b3d0002a5e20641206c60c61302683098cc900931442a80300a4db174dd"
          + "63e08090000000049454e44ae426082";

    private static final String GOLDEN =
              "g1 PNG 17x9 ch=1 19380:8069556\n"
            + "g2 PNG 15x7 ch=1 13260:3723852\n"
            + "g4 PNG 13x9 ch=1 14926:8587790\n"
            + "g16 PNG 11x7 ch=1 2310:14402374\n"
            + "rgb16 PNG 9x5 ch=3 6930:5364882\n"
            + "p2 PNG 11x7 ch=3 21052:4389180\n"
            + "p4 PNG 13x9 ch=3 30730:3391178\n"
            + "i4_4 PNG 19x11 ch=1 26452:10666324\n"
            + "i3_4 PNG 18x10 ch=3 52666:4395994";

    @Test
    void bitDepthPngOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-pngbd");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runJvm(root, probe(dir)));
    }

    @Test
    void bitDepthPngOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-pngbd");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runNativeX86(root, probe(dir)));
    }

    @Test
    void bitDepthPngOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-pngbd");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, root, probe(dir)));
    }

    @Test
    void bitDepthPngOnScript() throws Exception {
        Path root = tmp.resolve("script-pngbd");
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
        Files.write(dir.resolve("g1.png"), hex(G1_HEX));
        Files.write(dir.resolve("g2.png"), hex(G2_HEX));
        Files.write(dir.resolve("g4.png"), hex(G4_HEX));
        Files.write(dir.resolve("g16.png"), hex(G16_HEX));
        Files.write(dir.resolve("rgb16.png"), hex(RGB16_HEX));
        Files.write(dir.resolve("p2.png"), hex(P2_HEX));
        Files.write(dir.resolve("p4.png"), hex(P4_HEX));
        Files.write(dir.resolve("i4_4.png"), hex(I4_4_HEX));
        Files.write(dir.resolve("i3_4.png"), hex(I3_4_HEX));
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
             + "    println(probe(\"g1\", \"" + base + "/g1.png\"))\n"
             + "    println(probe(\"g2\", \"" + base + "/g2.png\"))\n"
             + "    println(probe(\"g4\", \"" + base + "/g4.png\"))\n"
             + "    println(probe(\"g16\", \"" + base + "/g16.png\"))\n"
             + "    println(probe(\"rgb16\", \"" + base + "/rgb16.png\"))\n"
             + "    println(probe(\"p2\", \"" + base + "/p2.png\"))\n"
             + "    println(probe(\"p4\", \"" + base + "/p4.png\"))\n"
             + "    println(probe(\"i4_4\", \"" + base + "/i4_4.png\"))\n"
             + "    println(probe(\"i3_4\", \"" + base + "/i3_4.png\"))\n"
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

    private <T> T withLibrary(Path root, PngBdAction<T> action) throws Exception {
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
    private interface PngBdAction<T> {
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
