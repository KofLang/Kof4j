package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the graphics/gaming front slice 3.3d
 * ({@code D-GRAPHICS-GAMING} + {@code D-GRAPHICS-SPIKE} +
 * {@code D-MAINT-BATCH-0510}/G1): the pure-Kof WAV encoder
 * {@code libs/game/Wav.kf}.
 *
 * <p>The offline mixer (`Audio.kf`) renders PCM into memory; the encoder
 * makes those samples shippable as a WAVE file image (RIFF header +
 * little-endian PCM16) that any player decodes and future backend playback
 * can submit verbatim. Tags come from `charAt` codes (never magic numbers);
 * mono/stereo only (`channels` outside {1,2} throws, like `rate <= 0`);
 * samples clamp to [-32768, 32767]; the readers (`wavSampleCount`/
 * `wavRate`/`wavChannels`/`wavSampleAt`) decode a `encodeWav` image
 * field-exact, so the golden pins the format both ways. All integers and
 * bytes, no IO — identical on every target by construction.
 */
class GameWavE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Wav.kf");
    }

    @Test
    void wavEncodeOnJvm() throws Exception {
        assertEquals(wavGolden(), runWavJvm(wavProbe()));
    }

    @Test
    void wavEncodeOnScript() throws Exception {
        assertEquals(wavGolden(), runWavScript(wavProbe()));
    }

    @Test
    void wavEncodeOnNativeX86() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"),
                "native toolchain absent");
        assertEquals(wavGolden(), runWavNativeX86(wavProbe()));
    }

    @Test
    void wavEncodeOnJs() throws Exception {
        assertEquals(wavGolden(), runWavJs(wavProbe()));
    }

    @Test
    void wavEncodeOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(wavGolden(), runWavCross("riscv64", Target.NATIVE_RISCV64, wavProbe()));
    }

    @Test
    void wavEncodeOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(wavGolden(), runWavCross("aarch64", Target.NATIVE_AARCH64, wavProbe()));
    }

    private static String wavProbe() {
        return """
                import game.Wav

                main() {
                    var samples = new Int[5]
                    samples[0] = 0
                    samples[1] = 1
                    samples[2] = 0 - 1
                    samples[3] = 32767
                    samples[4] = 0 - 32768
                    var wav = encodeWav(samples, 8000, 1)
                    println("len=" + wav.length)
                    println("n=" + wavSampleCount(wav) + " rate=" + wavRate(wav) + " ch=" + wavChannels(wav))
                    println("s0=" + wavSampleAt(wav, 0) + " s1=" + wavSampleAt(wav, 1) + " s2=" + wavSampleAt(wav, 2)
                        + " s3=" + wavSampleAt(wav, 3) + " s4=" + wavSampleAt(wav, 4))
                    println("riff=" + wavUbyte(wav, 0) + "," + wavUbyte(wav, 1) + "," + wavUbyte(wav, 2) + "," + wavUbyte(wav, 3))
                    println("cksize=" + wavU32read(wav, 4) + " fmt=" + wavU16read(wav, 20)
                        + " br=" + wavU32read(wav, 28) + " ba=" + wavU16read(wav, 32) + " bps=" + wavU16read(wav, 34))
                    try {
                        encodeWav(samples, 8000, 3)
                        println("threw=no")
                    } catch (String e) {
                        println("threw=yes")
                    }
                }
                """;
    }

    private static String wavGolden() {
        return """
                len=54
                n=5 rate=8000 ch=1
                s0=0 s1=1 s2=-1 s3=32767 s4=-32768
                riff=82,73,70,70
                cksize=46 fmt=1 br=16000 ba=2 bps=16
                threw=yes""";
    }

    private String runWavJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withWavLibrary(root, () -> driver.compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
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

    private String runWavScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withWavLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runWavNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withWavLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runWavBinary(out.resolve("Default/Main"));
    }

    private String runWavJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withWavLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runWavCross(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withWavLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(), arch + " compile: " + result.diagnostics().getDiagnostics());
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

    private String runWavBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private <T> T withWavLibrary(Path root, WavCheckedSupplier<T> action) throws Exception {
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
    private interface WavCheckedSupplier<T> {
        T get() throws Exception;
    }
}
