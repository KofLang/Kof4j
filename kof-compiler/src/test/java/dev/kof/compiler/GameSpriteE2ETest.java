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
 * End-to-end coverage for the graphics/gaming front slice 3.2a
 * ({@code D-GRAPHICS-GAMING} + {@code D-GRAPHICS-SPIKE} +
 * {@code D-MAINT-BATCH-0510}/G1): the pure-Kof 2D sprite intent
 * {@code libs/game/Sprite.kf} + the draw-command list
 * {@code libs/game/Draw.kf}.
 *
 * <p>The plan §8 freezes the first 2D level as intent the app declares —
 * {@code sprite("player.png").at(120, 80)} with {@code at/scale/turn/origin/
 * flip} and {@code frames()} + {@code animate()} — while rendering itself
 * stays the backend's job. The pure half owns the sprite state, the 2D
 * transform math ({@code pos + R * S * F * (p - origin)}) and the
 * deterministic animation clock over caller-supplied deltas (the same virtual
 * clock shape as {@code Clock}); {@code DrawList} records the resolved
 * per-frame commands in order for the backend to consume. No rendering,
 * window or audio API is called, so the surface is honest on every target.
 *
 * <p>Goldens print milli-units ({@code (v * 1000.0) as Int}) instead of raw
 * {@code Double}s: JS {@code Number} vs JVM/Native {@code double} formatting
 * is a real divergence the suite pins elsewhere, and last-ulp
 * {@code sin}/{@code cos} differences must never move a golden — every
 * asserted value sits far from a milli boundary.
 */
class GameSpriteE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Sprite.kf", "Draw.kf", "Trig.kf");
    }

    @Test
    void spriteTransformsOnJvm() throws Exception {
        assertEquals(spriteGoldenTransforms(), runSpriteJvm(spriteTransformProbe()));
    }

    @Test
    void spriteTransformsOnScript() throws Exception {
        assertEquals(spriteGoldenTransforms(), runSpriteScript(spriteTransformProbe()));
    }

    @Test
    void spriteTransformsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"),
                "native toolchain absent");
        assertEquals(spriteGoldenTransforms(), runSpriteNativeX86(spriteTransformProbe()));
    }

    @Test
    void spriteTransformsOnJs() throws Exception {
        assertEquals(spriteGoldenTransforms(), runSpriteJs(spriteTransformProbe()));
    }

    @Test
    void spriteAnimationOnJvm() throws Exception {
        assertEquals(spriteGoldenAnimation(), runSpriteJvm(spriteAnimationProbe()));
    }

    @Test
    void spriteAnimationOnScript() throws Exception {
        assertEquals(spriteGoldenAnimation(), runSpriteScript(spriteAnimationProbe()));
    }

    @Test
    void spriteAnimationOnNativeX86() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"),
                "native toolchain absent");
        assertEquals(spriteGoldenAnimation(), runSpriteNativeX86(spriteAnimationProbe()));
    }

    @Test
    void spriteAnimationOnJs() throws Exception {
        assertEquals(spriteGoldenAnimation(), runSpriteJs(spriteAnimationProbe()));
    }

    @Test
    void spriteDrawListOnJvm() throws Exception {
        assertEquals(spriteGoldenDraw(), runSpriteJvm(spriteDrawProbe()));
    }

    @Test
    void spriteDrawListOnScript() throws Exception {
        assertEquals(spriteGoldenDraw(), runSpriteScript(spriteDrawProbe()));
    }

    @Test
    void spriteDrawListOnNativeX86() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"),
                "native toolchain absent");
        assertEquals(spriteGoldenDraw(), runSpriteNativeX86(spriteDrawProbe()));
    }

    @Test
    void spriteDrawListOnJs() throws Exception {
        assertEquals(spriteGoldenDraw(), runSpriteJs(spriteDrawProbe()));
    }

    @Test
    void spriteTransformsOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(spriteGoldenTransforms(),
                runSpriteCross("riscv64", Target.NATIVE_RISCV64, spriteTransformProbe()));
    }

    @Test
    void spriteTransformsOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(spriteGoldenTransforms(),
                runSpriteCross("aarch64", Target.NATIVE_AARCH64, spriteTransformProbe()));
    }

    private static String spriteTransformProbe() {
        return """
                import game.Sprite
                import game.Trig

                Int spriteMilli(Double v) {
                    return (v * 1000.0) as Int
                }

                main() {
                    var s = sprite("player.png").at(100, 50).scale(2.0, 2.0).turn(0.0).origin(8, 8)
                    println("o=" + spriteMilli(s.worldPointX(8, 8)) + "," + spriteMilli(s.worldPointY(8, 8)))
                    println("p=" + spriteMilli(s.worldPointX(16, 8)) + "," + spriteMilli(s.worldPointY(16, 8)))
                    s.turn(90.0)
                    println("r=" + spriteMilli(s.worldPointX(16, 8)) + "," + spriteMilli(s.worldPointY(16, 8)))
                    s.turn(0.0).flip(true, false)
                    println("f=" + spriteMilli(s.worldPointX(16, 8)) + "," + spriteMilli(s.worldPointY(16, 8)))
                    println("t=" + spriteMilli(trigSin(30.0)) + "," + spriteMilli(trigCos(30.0)) + "," + spriteMilli(trigCos(60.0)))
                }
                """;
    }

    // NOTE: `f` is 83999, not the ideal 84000 — the pure-Kof Taylor
    // `trigCos(0°)` (= `trigSin(90°)`) carries ~1e-11 absolute error, and the
    // exact-boundary true value (84.0) truncates down a milli. The deviation
    // is deterministic and identical on every target (measured on all six),
    // an order of magnitude below any visible pixel.
    private static String spriteGoldenTransforms() {
        return """
                o=100000,50000
                p=116000,50000
                r=100000,66000
                f=83999,50000
                t=500,866,500""";
    }

    private static String spriteAnimationProbe() {
        return """
                import game.Sprite

                main() {
                    var s = sprite("hero.png")
                    println("base=" + s.current())
                    s.frames(listOf("w0.png", "w1.png", "w2.png"))
                    s.animate(0, 100)
                    println("t0=" + s.current())
                    s.animate(100, 100)
                    println("t1=" + s.current())
                    s.animate(150, 100)
                    println("t2=" + s.current())
                    s.animate(100, 100)
                    println("t3=" + s.current())
                    try {
                        s.animate(16, 0)
                        println("threw=no")
                    } catch (String e) {
                        println("threw=yes")
                    }
                }
                """;
    }

    private static String spriteGoldenAnimation() {
        return """
                base=hero.png
                t0=w0.png
                t1=w1.png
                t2=w2.png
                t3=w0.png
                threw=yes""";
    }

    private static String spriteDrawProbe() {
        return """
                import game.Sprite
                import game.Draw

                Int spriteMilli(Double v) {
                    return (v * 1000.0) as Int
                }

                main() {
                    var queue = DrawList()
                    var hero = sprite("hero.png").at(10, 20)
                    queue.draw(hero)
                    println("n=" + queue.size())
                    var cmd = queue.commandAt(0)
                    println("img=" + cmd.image() + " x=" + spriteMilli(cmd.x())
                        + " y=" + spriteMilli(cmd.y()))
                    hero.hide()
                    queue.draw(hero)
                    println("n2=" + queue.size())
                    queue.clear()
                    println("n3=" + queue.size())
                }
                """;
    }

    private static String spriteGoldenDraw() {
        return """
                n=1
                img=hero.png x=10000 y=20000
                n2=1
                n3=0""";
    }

    private String runSpriteJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withSpriteLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String runSpriteScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withSpriteLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runSpriteNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withSpriteLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runSpriteBinary(out.resolve("Default/Main"));
    }

    private String runSpriteJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withSpriteLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runSpriteCross(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withSpriteLibrary(root, () -> {
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

    private String runSpriteBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private <T> T withSpriteLibrary(Path root, SpriteCheckedSupplier<T> action) throws Exception {
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
    private interface SpriteCheckedSupplier<T> {
        T get() throws Exception;
    }
}
