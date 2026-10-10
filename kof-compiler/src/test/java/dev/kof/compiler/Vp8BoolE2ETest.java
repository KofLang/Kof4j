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
 * End-to-end coverage for the VP8 boolean range decoder
 * ({@code libs/image/Vp8.kf}, RFC 6386 §7.3) — the foundation of the pure-Kof
 * VP8 lossy decoder ({@code D-WEBP-LOSSY-PURE-KOF}).
 *
 * <p>The oracle is an <b>independent RFC §7.3 encoder</b> (a small Python
 * implementation of {@code write_bool}/{@code flush_bool_encoder}) run offline:
 * 64 bools drawn at a fixed seed with a repeating 8-probability pattern are
 * encoded into a 20-byte partition, and the Kof decoder must reproduce the
 * exact bool sequence. This is a real cross-implementation proof, not a
 * self-consistency check. Proven on JVM + Native x86-64 + riscv64 (qemu) +
 * Script; no compiler change.
 */
class Vp8BoolE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String PARTITION_HEX = "fe6607ffc1c03e85fc3807c8370b1699c7dc5500";
    private static final int[] PROBS = {3, 255, 128, 17, 200, 64, 1, 250};
    private static final String GOLDEN =
            "vp8bool=1101100111001001011010001100011110001011101011100011111110110010";

    @Test
    void vp8BoolOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-vp8bool");
        Files.createDirectories(root);
        Path dir = writePartition(root.resolve("fixtures"));
        assertEquals(GOLDEN, runJvm(root, MutableLists.probe(dir)));
    }

    @Test
    void vp8BoolOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-vp8bool");
        Files.createDirectories(root);
        Path dir = writePartition(root.resolve("fixtures"));
        assertEquals(GOLDEN, runNativeX86(root, MutableLists.probe(dir)));
    }

    @Test
    void vp8BoolOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-vp8bool");
        Files.createDirectories(root);
        Path dir = writePartition(root.resolve("fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, root, MutableLists.probe(dir)));
    }

    @Test
    void vp8BoolOnScript() throws Exception {
        Path root = tmp.resolve("script-vp8bool");
        Files.createDirectories(root);
        Path dir = writePartition(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), MutableLists.probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    private Path writePartition(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("vp8bool.bin"), hex(PARTITION_HEX));
        return dir;
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
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

    /** Builds the probe source with the probability table inlined. */
    static final class MutableLists {
        private MutableLists() {}

        static String probe(Path dir) {
            StringBuilder sb = new StringBuilder();
            sb.append("import image.Vp8\n\n");
            sb.append("main() {\n");
            sb.append("    var data = File(\"").append(RasterDecodeFixtures.path(dir))
                    .append("/vp8bool.bin\").readRange(0, ").append(PARTITION_HEX.length() / 2).append(")\n");
            sb.append("    var probs = new Int[").append(PROBS.length).append("]\n");
            for (int i = 0; i < PROBS.length; i++) {
                sb.append("    probs[").append(i).append("] = ").append(PROBS[i]).append("\n");
            }
            sb.append("    var d = Vp8Bool(data)\n");
            sb.append("    var s = \"\"\n");
            sb.append("    var i = 0\n");
            sb.append("    while (i < 64) {\n");
            sb.append("        s = s + d.bit(probs[i % ").append(PROBS.length).append("]).toString()\n");
            sb.append("        i = i + 1\n");
            sb.append("    }\n");
            sb.append("    println(\"vp8bool=\" + s)\n");
            sb.append("}\n");
            return sb.toString();
        }
    }
}
