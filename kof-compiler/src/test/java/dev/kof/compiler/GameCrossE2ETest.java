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
 * Cross-target end-to-end coverage for the graphics/gaming front slice 3.1
 * (`D-GRAPHICS-GAMING` + `D-GRAPHICS-SPIKE` + `D-MAINT-BATCH-0510`/G1): the four
 * pure-Kof `kof.game` modules — {@code Clock} + {@code Keys} + {@code Mouse} +
 * {@code Pad} — driven together on riscv64 and aarch64 under qemu, beside the JVM
 * oracle. The plan §11 puts x86-64/aarch64/riscv64 in the target matrix and §12
 * requires the same observable contract per target, so the pure snapshot halves
 * must be byte-identical on the cross backends too (not only the four CI targets
 * the per-module suites cover).
 *
 * <p>Golden: one virtual frame stepped by a virtual clock, querying every face of
 * the §7 input contract — frame 0 {@code dt=0} (clock), {@code left} pressed
 * (keys), pointer (100, 50) with no movement (mouse), neutral pad (pad); frame 1
 * {@code dt=16}, {@code space} pressed with {@code left} held, pointer (140, 70)
 * moved +40/+20 with {@code left} pressed, left stick (0.5, -0.25) with {@code a}
 * pressed. Axes are printed in milli-units so the golden is Double-formatting
 * independent.
 */
class GameCrossE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Clock.kf", "Keys.kf", "Mouse.kf", "Pad.kf");
    }

    @Test
    void gameCrossOnJvm() throws Exception {
        assertEquals(golden(), runGameJvm(gameProbe()));
    }

    @Test
    void gameCrossOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(golden(), runGameCross("riscv64", Target.NATIVE_RISCV64, gameProbe()));
    }

    @Test
    void gameCrossOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(golden(), runGameCross("aarch64", Target.NATIVE_AARCH64, gameProbe()));
    }

    // One virtual frame stepped by a virtual clock, exercising all four §7 faces.
    private static String gameProbe() {
        return """
                import game.Clock
                import game.Keys
                import game.Mouse
                import game.Pad

                main() {
                    var clock = Clock()
                    var keys = Keys()
                    var mouse = Mouse()
                    var pad = Pad()
                    clock.start(1000)
                    clock.beginFrame(1000)
                    keys.beginFrame(listOf("left"))
                    mouse.beginFrame(100, 50, listOf())
                    pad.beginFrame(0.0, 0.0, 0.0, 0.0, listOf())
                    println("f0 dt=" + clock.dtMicros()
                        + " left=" + keyState(keys, "left")
                        + " mx=" + mouse.x() + " my=" + mouse.y()
                        + " mdx=" + mouse.dx() + " mdy=" + mouse.dy()
                        + " mleft=" + btnState(mouse.pressed("left"), mouse.released("left"), mouse.down("left"))
                        + " lx=" + milli(pad.leftX()) + " ly=" + milli(pad.leftY())
                        + " a=" + btnState(pad.pressed("a"), pad.released("a"), pad.down("a")))
                    clock.beginFrame(1016)
                    keys.beginFrame(listOf("left", "space"))
                    mouse.beginFrame(140, 70, listOf("left"))
                    pad.beginFrame(0.5, -0.25, 0.0, 0.0, listOf("a"))
                    println("f1 dt=" + clock.dtMicros()
                        + " left=" + keyState(keys, "left")
                        + " space=" + keyState(keys, "space")
                        + " mx=" + mouse.x() + " my=" + mouse.y()
                        + " mdx=" + mouse.dx() + " mdy=" + mouse.dy()
                        + " mleft=" + btnState(mouse.pressed("left"), mouse.released("left"), mouse.down("left"))
                        + " lx=" + milli(pad.leftX()) + " ly=" + milli(pad.leftY())
                        + " a=" + btnState(pad.pressed("a"), pad.released("a"), pad.down("a")))
                    clock.stop()
                    println("hasNext=" + flag(clock.hasNext()))
                }

                String flag(Bool v) {
                    if (v) { return "true" }
                    return "false"
                }

                Int milli(Double v) {
                    return (v * 1000.0) as Int
                }

                String keyState(Keys k, String key) {
                    if (k.pressed(key)) { return "pressed" }
                    if (k.released(key)) { return "released" }
                    if (k.down(key)) { return "down" }
                    return "-"
                }

                String btnState(Bool pressed, Bool released, Bool down) {
                    if (pressed) { return "pressed" }
                    if (released) { return "released" }
                    if (down) { return "down" }
                    return "-"
                }
                """;
    }

    private static String golden() {
        return """
                f0 dt=0 left=pressed mx=100 my=50 mdx=0 mdy=0 mleft=- lx=0 ly=0 a=-
                f1 dt=16 left=down space=pressed mx=140 my=70 mdx=40 mdy=20 mleft=pressed lx=500 ly=-250 a=pressed
                hasNext=false""";
    }

    private String runGameJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withGameLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String runGameCross(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withGameLibrary(root, () -> {
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

    private <T> T withGameLibrary(Path root, CheckedSupplier<T> action) throws Exception {
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
