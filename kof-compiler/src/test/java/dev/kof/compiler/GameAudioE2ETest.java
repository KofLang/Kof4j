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
 * End-to-end coverage for the graphics/gaming front slice 3.3a
 * ({@code D-GRAPHICS-GAMING} + {@code D-GRAPHICS-SPIKE} +
 * {@code D-MAINT-BATCH-0510}/G1): the pure-Kof offline audio mixer
 * {@code libs/game/Audio.kf}.
 *
 * <p>The plan §10 freezes sound/music as intent while the backend owns
 * decoder/buffer/mixer/output, and §13 requires the offline path
 * (program → mixer → PCM → hash/reference, no speakers). The pure half
 * synthesizes sine tones (`Sound`) scheduled on a `Mixer` that renders
 * deterministic 16-bit PCM into an `Int[]`: overlapping voices sum and
 * clamp to [-32768, 32767], each loop repetition restarts the phase, and
 * `mixer(rate <= 0)` throws. Sample synthesis uses only `game.Trig`
 * (never `math.sin`, which has no Native symbols — `known-bugs` §621), so
 * every operation is integer or deterministic floating point on all
 * targets; the golden pins exact integers only (length, sum, peak,
 * individual samples), never raw `Double`s.
 */
class GameAudioE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Audio.kf");
    }

    @Test
    void audioMixOnJvm() throws Exception {
        assertEquals(audioGolden(), runAudioJvm(audioProbe()));
    }

    @Test
    void audioMixOnScript() throws Exception {
        assertEquals(audioGolden(), runAudioScript(audioProbe()));
    }

    @Test
    void audioMixOnNativeX86() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"),
                "native toolchain absent");
        assertEquals(audioGolden(), runAudioNativeX86(audioProbe()));
    }

    @Test
    void audioMixOnJs() throws Exception {
        assertEquals(audioGolden(), runAudioJs(audioProbe()));
    }

    @Test
    void audioMixOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(audioGolden(), runAudioCross("riscv64", Target.NATIVE_RISCV64, audioProbe()));
    }

    @Test
    void audioMixOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(audioGolden(), runAudioCross("aarch64", Target.NATIVE_AARCH64, audioProbe()));
    }

    private static String audioProbe() {
        return """
                import game.Audio

                main() {
                    var mix = mixer(8000)
                    mix.play(Sound("a440", 440.0, 100.0, 1.0, 1), 0)
                    mix.play(Sound("e660", 660.0, 100.0, 0.5, 1), 50)
                    println("voices=" + mix.voices() + " rate=" + mix.sampleRate())
                    var pcm = mix.render()
                    println("len=" + pcm.length)
                    var sum = 0
                    var peak = 0
                    var i = 0
                    while (i < pcm.length) {
                        var v = pcm[i]
                        sum = sum + v
                        if (v < 0) { v = 0 - v }
                        if (v > peak) { peak = v }
                        i = i + 1
                    }
                    println("sum=" + sum + " peak=" + peak)
                    println("s0=" + pcm[0] + " s50=" + pcm[50] + " s100=" + pcm[100] + " s1199=" + pcm[1199])
                    var again = mix.render()
                    println("stable=" + (again.length == pcm.length))
                    var empty = mixer(8000).render()
                    println("empty=" + empty.length)
                    try {
                        mixer(0)
                        println("threw=no")
                    } catch (String e) {
                        println("threw=yes")
                    }
                }
                """;
    }

    private static String audioGolden() {
        return """
                voices=2 rate=8000
                len=1200
                sum=-43 peak=32768
                s0=0 s50=-32767 s100=0 s1199=-8117
                stable=true
                empty=0
                threw=yes""";
    }

    private String runAudioJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withAudioLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String runAudioScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withAudioLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runAudioNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withAudioLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runAudioBinary(out.resolve("Default/Main"));
    }

    private String runAudioJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withAudioLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runAudioCross(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withAudioLibrary(root, () -> {
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

    private String runAudioBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private <T> T withAudioLibrary(Path root, AudioCheckedSupplier<T> action) throws Exception {
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
    private interface AudioCheckedSupplier<T> {
        T get() throws Exception;
    }
}
