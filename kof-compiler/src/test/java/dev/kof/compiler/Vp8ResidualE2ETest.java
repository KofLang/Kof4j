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
 * End-to-end coverage for the VP8 (lossy WebP) dequantization + inverse
 * transforms ({@code libs/image/Vp8Residual.kf}, RFC 6386 §14) — slice 5 of the
 * pure-Kof VP8 chain ({@code D-WEBP-LOSSY-PURE-KOF}).
 *
 * <p>The oracle is an <b>independent RFC §14 implementation</b> (a small Python
 * implementation run offline): it re-decodes the coefficients and applies the
 * dequantization tables, the inverse WHT and the inverse DCT exactly as
 * specified. For every macroblock the Kof residue must reproduce the
 * signed/absolute sums of the luma (16x16) and chroma (8x8) planes. Proven on
 * JVM + Native x86-64 + riscv64 (qemu) + Script; no compiler change.
 */
class Vp8ResidualE2ETest {

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

    private static final String GOLDEN =
              "flat16 -256:256,0:0,0:0\n"
            + "diag32 -1159:2135,0:0,0:0,275:487,0:0,0:0,32:32,0:0,0:0,16:48,0:0,0:0\n"
            + "skip64 -11520:11520,-2368:2368,7168:7168,-11520:11520,-64:64,0:0,-11520:11520,0:0,0:0,-11520:11520,0:0,0:0,-256:256,-64:64,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0,0:0\n"
            + "cat64 -22448:22448,-496:496,0:416,2816:2816,0:0,1360:1360,2816:2816,-320:320,1360:1360,2752:2752,0:0,1328:1328,5504:5504,-640:640,-1168:1168,0:704,-512:512,-96:512,0:384,544:544,-96:96,0:384,-288:288,-96:96,6272:6272,-816:816,-1152:1152,-384:384,640:640,-192:192,0:0,-192:192,-288:288,0:0,-400:400,-96:96,5504:5504,-480:480,-976:976,0:512,-608:608,0:416,0:0,0:0,336:336,0:0,224:224,0:0";

    @Test
    void vp8ResidualOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runJvm(root, probe(dir)));
    }

    @Test
    void vp8ResidualOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runNativeX86(root, probe(dir)));
    }

    @Test
    void vp8ResidualOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, root, probe(dir)));
    }

    @Test
    void vp8ResidualOnScript() throws Exception {
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
        return "import image.Vp8Frame\n"
             + "import image.Vp8Residual\n\n"
             + "String sums(Int[] xs, Int mb, Int stride, Int n) {\n"
             + "    var s = 0\n"
             + "    var a = 0\n"
             + "    var k = 0\n"
             + "    while (k < n) {\n"
             + "        var v = xs[mb * stride + k]\n"
             + "        s = s + v\n"
             + "        if (v < 0) {\n"
             + "            a = a - v\n"
             + "        } else {\n"
             + "            a = a + v\n"
             + "        }\n"
             + "        k = k + 1\n"
             + "    }\n"
             + "    return \"\" + s + \":\" + a\n"
             + "}\n\n"
             + "String residue(Int[] bytes) {\n"
             + "    var r = Vp8Residual(vp8FrameFromWebp(bytes))\n"
             + "    var n = r.count()\n"
             + "    var ys = r.y()\n"
             + "    var us = r.u()\n"
             + "    var vs = r.v()\n"
             + "    var out = \"\"\n"
             + "    var mb = 0\n"
             + "    while (mb < n) {\n"
             + "        if (mb > 0) {\n"
             + "            out = out + \",\"\n"
             + "        }\n"
             + "        out = out + sums(ys, mb, 256, 256) + \",\" + sums(us, mb, 64, 64)"
             + " + \",\" + sums(vs, mb, 64, 64)\n"
             + "        mb = mb + 1\n"
             + "    }\n"
             + "    return out\n"
             + "}\n\n"
             + "main() {\n"
             + "    println(\"flat16 \" + residue(File(\"" + base + "/flat16.webp\").readBytes()))\n"
             + "    println(\"diag32 \" + residue(File(\"" + base + "/diag32.webp\").readBytes()))\n"
             + "    println(\"skip64 \" + residue(File(\"" + base + "/skip64.webp\").readBytes()))\n"
             + "    println(\"cat64 \" + residue(File(\"" + base + "/cat64.webp\").readBytes()))\n"
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
