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
 * End-to-end coverage for the VP8 (lossy WebP) key-frame macroblock prediction
 * records ({@code libs/image/Vp8Frame.kf}, RFC 6386 §10/§11) — slice 3 of the
 * pure-Kof VP8 chain ({@code D-WEBP-LOSSY-PURE-KOF}).
 *
 * <p>The oracle is an <b>independent RFC §7.3/§10/§11 parser</b> (a small Python
 * implementation run offline) over real libwebp-generated lossy files that
 * exercise: a 4x4 segment-mapped frame with {@code mb_no_skip_coeff=1} and
 * {@code update_mb_segmentation_map=1} ({@code seg64}), a 2x2 all-{@code B_PRED}
 * frame whose 16 subblock modes are context-coded ({@code diag32}), and a 4x4
 * frame mixing every luma and chroma mode ({@code mix}). The Kof parser must
 * reproduce every segment id, skip flag, luma/chroma mode and subblock mode.
 * Proven on JVM + Native x86-64 + riscv64 (qemu) + Script; no compiler change.
 */
class Vp8ModeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String SEG64_HEX =
            "524946465c000000574542505650382050000000b004009d012a400040003ed168b052a82624"
          + "a2a20801001a0969062807eff89d000857b2d956263da7d79b57400000feeb36fffe84d8f6a2"
          + "bffff424ffe26cff89b3e115b4f4b331cb99d6de02000000";
    private static final String DIAG32_HEX =
            "524946465800000057454250565038204c0000009004009d012a200020002e25188c462122"
          + "22220200244b480005cf044784367991338898ded3a8a046b6753b8000fefffe13039ed077"
          + "8e57d36a60c4b734e4a2442744e950c19c65de000000";
    private static final String MIX_HEX =
            "524946468a00000057454250565038207e0000003005009d012a400040003f71b6ce5dbbb1"
          + "29bfac1d5803f02e096206700c40ffa23f042099e275feffeb03ffffe4c86ffbf000feefea"
          + "3c87dbba1ae81df7cea6b0ba4b7d01db3e51344d74e50de3adef74cd294734473b63de1363"
          + "d6c97e017c2accc9adb06c756c3ebda3959f373f50db6ff0739c41c1136c0872340000";

    private static final String GOLDEN =
              "seg64 ym=0111000000000000 uv=0000000000000000 sk=0000011111111111"
            + " sg=0333333333333333 bm=\n"
            + "diag32 ym=4444 uv=0000 sk=0000 sg=0000"
            + " bm=0111444144414441|1100111111111111|4444444144414441|4441444144414441|\n"
            + "mix ym=0333133313331333 uv=2000003313330033 sk=0000000000000000"
            + " sg=3001033303330333 bm=";

    @Test
    void vp8ModesOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-vp8m");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runJvm(root, probe(dir)));
    }

    @Test
    void vp8ModesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-vp8m");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runNativeX86(root, probe(dir)));
    }

    @Test
    void vp8ModesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-vp8m");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, root, probe(dir)));
    }

    @Test
    void vp8ModesOnScript() throws Exception {
        Path root = tmp.resolve("script-vp8m");
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
        Files.write(dir.resolve("seg64.webp"), hex(SEG64_HEX));
        Files.write(dir.resolve("diag32.webp"), hex(DIAG32_HEX));
        Files.write(dir.resolve("mix.webp"), hex(MIX_HEX));
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
        return "import image.Vp8Frame\n\n"
             + "String modes(Int[] bytes) {\n"
             + "    var f = vp8FrameFromWebp(bytes)\n"
             + "    var n = f.mbCount()\n"
             + "    var ym = \"\"\n"
             + "    var uv = \"\"\n"
             + "    var sk = \"\"\n"
             + "    var sg = \"\"\n"
             + "    var bm = \"\"\n"
             + "    var i = 0\n"
             + "    while (i < n) {\n"
             + "        ym = ym + f.yMode(i)\n"
             + "        uv = uv + f.uvMode(i)\n"
             + "        sk = sk + f.skipCoeff(i)\n"
             + "        sg = sg + f.segmentId(i)\n"
             + "        if (f.yMode(i) == 4) {\n"
             + "            var j = 0\n"
             + "            while (j < 16) {\n"
             + "                bm = bm + f.bMode(i, j)\n"
             + "                j = j + 1\n"
             + "            }\n"
             + "            bm = bm + \"|\"\n"
             + "        }\n"
             + "        i = i + 1\n"
             + "    }\n"
             + "    return \"ym=\" + ym + \" uv=\" + uv + \" sk=\" + sk + \" sg=\" + sg + \" bm=\" + bm\n"
             + "}\n\n"
             + "main() {\n"
             + "    println(\"seg64 \" + modes(File(\"" + base + "/seg64.webp\").readBytes()))\n"
             + "    println(\"diag32 \" + modes(File(\"" + base + "/diag32.webp\").readBytes()))\n"
             + "    println(\"mix \" + modes(File(\"" + base + "/mix.webp\").readBytes()))\n"
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
