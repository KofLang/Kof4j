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
 * End-to-end coverage for the graphics/gaming front slice 3.2b
 * ({@code D-GRAPHICS-GAMING} + {@code D-GRAPHICS-SPIKE} +
 * {@code D-MAINT-BATCH-0510}/G1): the pure-Kof tilemap intent
 * {@code libs/game/Tilemap.kf}.
 *
 * <p>The plan §8 freezes tilemaps as map intent (`tilemap("level.png", 16)`)
 * while tileset/atlas/layers/collision stay open backend questions. The pure
 * half is an unbounded sparse tile grid with deterministic queries
 * (`tileAt`/`setTile`/`clearTile`/`hasTile`/`count`/`worldX`/`worldY`); a
 * negative id clears, an unset cell reads `-1`, and `tileSize <= 0` throws
 * at construction. All integers, no IO, so the golden is trivially identical
 * on every target — the property the plan requires for input goldens.
 */
class GameTilemapE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Tilemap.kf");
    }

    @Test
    void tilemapGridOnJvm() throws Exception {
        assertEquals(tilemapGolden(), runTilemapJvm(tilemapProbe()));
    }

    @Test
    void tilemapGridOnScript() throws Exception {
        assertEquals(tilemapGolden(), runTilemapScript(tilemapProbe()));
    }

    @Test
    void tilemapGridOnNativeX86() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"),
                "native toolchain absent");
        assertEquals(tilemapGolden(), runTilemapNativeX86(tilemapProbe()));
    }

    @Test
    void tilemapGridOnJs() throws Exception {
        assertEquals(tilemapGolden(), runTilemapJs(tilemapProbe()));
    }

    @Test
    void tilemapGridOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(tilemapGolden(), runTilemapCross("riscv64", Target.NATIVE_RISCV64, tilemapProbe()));
    }

    @Test
    void tilemapGridOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(tilemapGolden(), runTilemapCross("aarch64", Target.NATIVE_AARCH64, tilemapProbe()));
    }

    private static String tilemapProbe() {
        return """
                import game.Tilemap

                String tileFlag(Bool v) {
                    if (v) { return "true" }
                    return "false"
                }

                main() {
                    var m = tilemap("level.png", 16)
                    m.setTile(0, 0, 1).setTile(3, 2, 5).setTile(0, 0, 7)
                    m.setTile(-2, -1, 4)
                    println("a=" + m.tileAt(0, 0) + " b=" + m.tileAt(3, 2)
                        + " c=" + m.tileAt(9, 9) + " n=" + m.count())
                    println("wx=" + m.worldX(3) + " wy=" + m.worldY(2)
                        + " neg=" + m.tileAt(-2, -1) + " has=" + tileFlag(m.hasTile(3, 2)))
                    m.clearTile(3, 2)
                    println("d=" + m.tileAt(3, 2) + " n=" + m.count())
                    m.setTile(1, 1, -1)
                    println("e=" + m.count())
                    m.clear()
                    println("f=" + m.count() + " t=" + m.tilesetName() + " s=" + m.tilePixels())
                    try {
                        tilemap("bad.png", 0)
                        println("threw=no")
                    } catch (String e) {
                        println("threw=yes")
                    }
                }
                """;
    }

    private static String tilemapGolden() {
        return """
                a=7 b=5 c=-1 n=3
                wx=48 wy=32 neg=4 has=true
                d=-1 n=2
                e=2
                f=0 t=level.png s=16
                threw=yes""";
    }

    private String runTilemapJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withTilemapLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String runTilemapScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withTilemapLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runTilemapNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withTilemapLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runTilemapBinary(out.resolve("Default/Main"));
    }

    private String runTilemapJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withTilemapLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runTilemapCross(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withTilemapLibrary(root, () -> {
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

    private String runTilemapBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private <T> T withTilemapLibrary(Path root, TilemapCheckedSupplier<T> action) throws Exception {
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
    private interface TilemapCheckedSupplier<T> {
        T get() throws Exception;
    }
}
