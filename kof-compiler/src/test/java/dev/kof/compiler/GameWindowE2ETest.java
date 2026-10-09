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
 * End-to-end coverage for the graphics/gaming front slice 3.1
 * ({@code D-GRAPHICS-GAMING} + {@code D-GRAPHICS-SPIKE} + {@code D-GRAPHICS-WINDOW-FORM}
 * + {@code D-MAINT-BATCH-0610}): the pure-Kof backend window / frame loop host
 * {@code libs/game/Window.kf}.
 *
 * <p>The maintainer DECIDED the form — {@code Window("Pong") { frame { dt -> … } }}
 * with {@code dt} an Int of milliseconds and the first frame {@code 0} — and the
 * loop semantics: <b>A1</b> a long frame's {@code dt} is CLAMPED by a configured
 * ceiling (spiral-of-death guard), <b>A2</b> vsync on/off is the only pacing
 * primitive, <b>A3</b> explicit {@code pause()}/{@code resume()}, {@code minimize()}
 * suspends the frame body and losing focus ({@code blur()}) does NOT pause.
 *
 * <p>Kof has no implicit receiver (SEM015/SEM025), so the literal nested form is
 * written {@code Window("…") { w: Window -> w.frame { dt: Int -> … } }} — the
 * shape {@link TrailingLambdaParamsE2ETest} pins. The host composes the same
 * {@code Clock} the pure surface already ships, over a caller-supplied virtual
 * timestamp source, so the golden is deterministic and identical on every target
 * (plan §6 "virtual clock required ... for goldens").
 */
class GameWindowE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Window.kf", "Clock.kf");
    }

    @Test
    void windowFrameLoopOnJvm() throws Exception {
        assertEquals(goldenLoop(), runWindowJvm(loopProbe()));
    }

    @Test
    void windowFrameLoopOnScript() throws Exception {
        assertEquals(goldenLoop(), runWindowScript(loopProbe()));
    }

    @Test
    void windowFrameLoopOnNativeX86() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"),
                "native toolchain absent");
        assertEquals(goldenLoop(), runWindowNativeX86(loopProbe()));
    }

    @Test
    void windowFrameLoopOnJs() throws Exception {
        assertEquals(goldenLoop(), runWindowJs(loopProbe()));
    }

    @Test
    void windowDtClampOnJvm() throws Exception {
        assertEquals(goldenClamp(), runWindowJvm(clampProbe()));
    }

    @Test
    void windowLoopSemanticsOnJvm() throws Exception {
        assertEquals(goldenSemantics(), runWindowJvm(semanticsProbe()));
    }

    @Test
    void windowFrameLoopOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(goldenLoop(), runWindowCross("riscv64", Target.NATIVE_RISCV64, loopProbe()));
    }

    @Test
    void windowFrameLoopOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(goldenLoop(), runWindowCross("aarch64", Target.NATIVE_AARCH64, loopProbe()));
    }

    // A virtual monotonic source: 1000µs per call, call-counted.
    private static String loopProbe() {
        return """
                import game.Window

                class Source {
                    Int ticks
                    constructor() { this.ticks = 0 }
                    Long now() {
                        this.ticks = this.ticks + 1
                        return this.ticks * 1000
                    }
                }

                main() {
                    var src = Source()
                    var w = Window("Pong")
                    w.clock(() -> src.now())
                    w.frame { dt: Int, self: Window ->
                        println("f=" + self.frames() + " dt=" + dt)
                        if (self.frames() >= 3) { self.stop() }
                    }
                    println("done=" + w.frames() + " last=" + w.dtMillis()
                        + " title=" + w.title() + " clamp=" + w.dtClampMs()
                        + " vsync=" + flag(w.vsyncOn()))
                }

                String flag(Bool v) {
                    if (v) { return "true" }
                    return "false"
                }
                """;
    }

    private static String goldenLoop() {
        return """
                f=1 dt=0
                f=2 dt=1
                f=3 dt=1
                done=3 last=1 title=Pong clamp=250 vsync=true""";
    }

    // A source with one huge jump: the real delta is clamped by the ceiling.
    private static String clampProbe() {
        return """
                import game.Window

                class Step {
                    Int i
                    constructor() { this.i = 0 }
                    Long now() {
                        this.i = this.i + 1
                        if (this.i == 1) { return 0 }
                        if (this.i == 2) { return 1000 }
                        if (this.i == 3) { return 2000 }
                        return 900000
                    }
                }

                main() {
                    var src = Step()
                    var w = Window("Pong")
                    w.clock(() -> src.now())
                    w.dtClampMillis(100)
                    w.frame { dt: Int, self: Window ->
                        println("f=" + self.frames() + " dt=" + dt)
                        if (self.frames() >= 3) { self.stop() }
                    }
                    println("clamp=" + w.dtClampMs())
                }
                """;
    }

    private static String goldenClamp() {
        return """
                f=1 dt=0
                f=2 dt=1
                f=3 dt=100
                clamp=100""";
    }

    // A3: pause()/resume() skip/restore the body; minimize()/restore() suspend
    // it; blur() only records focus and never pauses. The state is driven from
    // the timestamp source (called every iteration, skipped ones included), so
    // the probe is self-contained and terminates.
    private static String semanticsProbe() {
        return """
                import game.Window

                class Driver {
                    Int ticks
                    Window w
                    constructor() { this.ticks = 0 }
                    Long now() {
                        this.ticks = this.ticks + 1
                        if (this.ticks == 3) { this.w.pause() }
                        if (this.ticks == 6) { this.w.resume() }
                        if (this.ticks == 8) { this.w.minimize() }
                        if (this.ticks == 10) { this.w.restore() }
                        if (this.ticks == 12) { this.w.blur() }
                        return this.ticks * 1000
                    }
                }

                main() {
                    var d = Driver()
                    var w = Window("Pong")
                    d.w = w
                    w.clock(() -> d.now())
                    w.frame { dt: Int, self: Window ->
                        println("f=" + self.frames() + " dt=" + dt)
                        if (self.frames() >= 6) { self.stop() }
                    }
                    println("frames=" + w.frames() + " focused=" + flag(w.focused())
                        + " paused=" + flag(w.paused())
                        + " minimized=" + flag(w.minimized()))
                }

                String flag(Bool v) {
                    if (v) { return "true" }
                    return "false"
                }
                """;
    }

    private static String goldenSemantics() {
        return """
                f=1 dt=0
                f=2 dt=1
                f=3 dt=1
                f=4 dt=1
                f=5 dt=1
                f=6 dt=1
                frames=6 focused=false paused=false minimized=false""";
    }

    private String runWindowJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withWindowLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String runWindowScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withWindowLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runWindowNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withWindowLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runWindowBinary(out.resolve("Default/Main"));
    }

    private String runWindowJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withWindowLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runWindowCross(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withWindowLibrary(root, () -> {
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

    private String runWindowBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private <T> T withWindowLibrary(Path root, CheckedSupplier<T> action) throws Exception {
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
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
