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
 * End-to-end coverage for the VP8 (lossy WebP) key-frame reconstruction
 * ({@code libs/image/Vp8Reconstruct.kf}, RFC 6386 §12/§14) — slice 5 of the
 * pure-Kof VP8 chain ({@code D-WEBP-LOSSY-PURE-KOF}).
 *
 * <p>The oracle is libwebp itself, decoded with the loop filter disabled
 * ({@code ffmpeg -skip_loop_filter all}): slice 5 stops at §14.5 (prediction +
 * inverse DCT/WHT), so the loop filter (§15, the next slice) is not applied.
 * The {@code ffmpeg} VP8 decoder honours the skip flag, yielding the exact
 * pre-filter planes. Each fixture is a real libwebp file exercising a different
 * reconstruction path: {@code flat16} (16×16 V_PRED + DC-only Y2), {@code diag32}
 * (all-{@code B_PRED}), {@code skip64} (mixed 16×16 + skipped macroblocks) and
 * {@code cat64} (mixed modes + every residual). For every fixture the three
 * reconstructed planes must reproduce libwebp's per-plane sample sum and a
 * 24-bit rolling hash exactly.
 *
 * <p>Proven on JVM + Native x86-64 + riscv64 (qemu) + Script; no compiler
 * change.
 */
class Vp8ReconstructE2ETest {

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
              "flat16 Y32256:12623360 U8192:16654336 V8192:16654336\n"
            + "diag32 Y125303:11904471 U32768:16285696 V32768:16285696\n"
            + "skip64 Y336128:2232576 U91392:2651392 V245760:4702208\n"
            + "cat64 Y486864:12479696 U102384:9653488 V135936:8880896";

    @Test
    void vp8ReconstructOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runJvm(root, probe(dir)));
    }

    @Test
    void vp8ReconstructOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runNativeX86(root, probe(dir)));
    }

    @Test
    void vp8ReconstructOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-vp8r");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, root, probe(dir)));
    }

    @Test
    void vp8ReconstructOnScript() throws Exception {
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
             + ""
             + "import image.Vp8Reconstruct\n\n"
             + "String plane(Int[] a, Int stride, Int w, Int h) {\n"
             + "    var sum = 0 var hash = 0 var r = 0\n"
             + "    while (r < h) {\n"
             + "        var c = 0\n"
             + "        while (c < w) {\n"
             + "            var v = a[(r + 1) * stride + (c + 1)]\n"
             + "            sum = sum + v\n"
             + "            hash = ((hash << 5) + hash + v) & 16777215\n"
             + "            c = c + 1\n"
             + "        }\n"
             + "        r = r + 1\n"
             + "    }\n"
             + "    return sum + \":\" + hash\n"
             + "}\n\n"
             + "String recon(String label, String path) {\n"
             + "    var f = vp8FrameFromWebp(File(path).readBytes())\n"
             + "    var p = vp8Reconstruct(f)\n"
             + "    var w = p.width() var h = p.height()\n"
             + "    var ystr = w + 1 var ustr = w / 2 + 1\n"
             + "    return label\n"
             + "        + \" Y\" + plane(p.y(), ystr, w, h)\n"
             + "        + \" U\" + plane(p.u(), ustr, w / 2, h / 2)\n"
             + "        + \" V\" + plane(p.v(), ustr, w / 2, h / 2)\n"
             + "}\n\n"
             + "main() {\n"
             + "    println(recon(\"flat16\", \"" + base + "/flat16.webp\"))\n"
             + "    println(recon(\"diag32\", \"" + base + "/diag32.webp\"))\n"
             + "    println(recon(\"skip64\", \"" + base + "/skip64.webp\"))\n"
             + "    println(recon(\"cat64\", \"" + base + "/cat64.webp\"))\n"
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
