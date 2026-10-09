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
 * End-to-end coverage for the graphics/gaming front slice 3.4a
 * ({@code D-GRAPHICS-GAMING} + {@code D-GRAPHICS-SPIKE} +
 * {@code D-MAINT-BATCH-0510}/G1): the pure-Kof video playback intent
 * {@code libs/game/Video.kf}.
 *
 * <p>The plan §10 freezes video as intent (`video("intro.mp4")` + playback
 * controls) while demuxer/decoder/codec stay out of the app (no own codecs;
 * the FFmpeg LGPL-vendoring decision F is the maintainer's). The pure half
 * is the playback state machine over caller-supplied timestamps —
 * play/pause/stop/seek/volume/loop/mute plus position and frame index —
 * with metadata supplied by the demuxing backend. No decoder, no window, no
 * clock of its own; honest on every target. All integers and booleans in the
 * golden (volume prints in milli-units, never a raw {@code Double}).
 */
class GameVideoE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Video.kf");
    }

    @Test
    void videoPlaybackOnJvm() throws Exception {
        assertEquals(videoGolden(), runVideoJvm(videoProbe()));
    }

    @Test
    void videoPlaybackOnScript() throws Exception {
        assertEquals(videoGolden(), runVideoScript(videoProbe()));
    }

    @Test
    void videoPlaybackOnNativeX86() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"),
                "native toolchain absent");
        assertEquals(videoGolden(), runVideoNativeX86(videoProbe()));
    }

    @Test
    void videoPlaybackOnJs() throws Exception {
        assertEquals(videoGolden(), runVideoJs(videoProbe()));
    }

    @Test
    void videoPlaybackOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(videoGolden(), runVideoCross("riscv64", Target.NATIVE_RISCV64, videoProbe()));
    }

    @Test
    void videoPlaybackOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(videoGolden(), runVideoCross("aarch64", Target.NATIVE_AARCH64, videoProbe()));
    }

    private static String videoProbe() {
        return """
                import game.Video

                String videoFlag(Bool v) {
                    if (v) { return "true" }
                    return "false"
                }

                main() {
                    var v = video("intro.mp4", 10000, 640, 480, 30.0)
                    println("meta=" + v.width() + "x" + v.height() + " dur=" + v.duration())
                    v.play()
                    v.tick(1000)
                    println("p1=" + v.position() + " f1=" + v.frameIndex() + " playing=" + videoFlag(v.isPlaying()))
                    v.tick(9000)
                    println("p2=" + v.position() + " playing=" + videoFlag(v.isPlaying()) + " finished=" + videoFlag(v.finished()))
                    v.seek(2500)
                    v.play()
                    v.tick(500)
                    println("p3=" + v.position() + " f3=" + v.frameIndex())
                    v.pause()
                    v.tick(1000)
                    println("paused=" + videoFlag(!v.isPlaying()) + " pos=" + v.position())
                    v.setVolume(0.5)
                    v.mute()
                    println("vol=" + ((v.volume() * 1000.0) as Int) + " muted=" + videoFlag(v.isMuted()))
                    v.setLoop(true)
                    v.seek(9000)
                    v.play()
                    v.tick(2000)
                    println("looped=" + v.position() + " playing=" + videoFlag(v.isPlaying()))
                    v.stop()
                    println("stopped=" + v.position() + " playing=" + videoFlag(v.isPlaying()))
                    v.seek(99999)
                    println("hiclamp=" + v.position())
                    v.seek(0 - 5)
                    println("loclamp=" + v.position())
                    try {
                        video("bad.mp4", 100, 0, 480, 30.0)
                        println("threw=no")
                    } catch (String e) {
                        println("threw=yes")
                    }
                }
                """;
    }

    private static String videoGolden() {
        return """
                meta=640x480 dur=10000
                p1=1000 f1=30 playing=true
                p2=10000 playing=false finished=true
                p3=3000 f3=90
                paused=true pos=3000
                vol=500 muted=true
                looped=1000 playing=true
                stopped=0 playing=false
                hiclamp=10000
                loclamp=0
                threw=yes""";
    }

    private String runVideoJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withVideoLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String runVideoScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withVideoLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runVideoNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withVideoLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runVideoBinary(out.resolve("Default/Main"));
    }

    private String runVideoJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withVideoLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runVideoCross(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withVideoLibrary(root, () -> {
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

    private String runVideoBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private <T> T withVideoLibrary(Path root, VideoCheckedSupplier<T> action) throws Exception {
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
    private interface VideoCheckedSupplier<T> {
        T get() throws Exception;
    }
}
