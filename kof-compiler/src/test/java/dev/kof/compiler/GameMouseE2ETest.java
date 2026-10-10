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
 * End-to-end coverage for the graphics/gaming front slice 3.1 (`D-GRAPHICS-GAMING`
 * + `D-GRAPHICS-SPIKE`): the pure-Kof per-frame mouse snapshot
 * {@code libs/game/Mouse.kf}. The plan §7 freezes mouse input as a snapshot per
 * frame — position, movement and the states {@code down}/{@code pressed}/
 * {@code released}; the backend translates the OS pointer events and calls
 * {@code beginFrame(x, y, buttons)} once per frame, so this module is the
 * backend-independent half and runs on every target.
 *
 * <p>Golden: a virtual pointer stream — frame 0 at (100, 50) with no button,
 * frame 1 at (100, 50) left down (a click without movement), frame 2 at (140, 70)
 * left still down (drag: +40/+20), frame 3 at (140, 70) left released. The same
 * run on JVM + Script + Native x86-64 + JS must print the identical golden
 * (deterministic input, the plan's golden property). The first frame's movement
 * is 0 (no prior position to diff), mirroring the clock's first-frame {@code dt=0}.
 */
class GameMouseE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Clock.kf", "Keys.kf", "Mouse.kf");
    }

    @Test
    void mouseSnapshotOnJvm() throws Exception {
        assertEquals(golden(), runMouseJvm(mouseProbe()));
    }

    @Test
    void mouseSnapshotOnScript() throws Exception {
        assertEquals(golden(), runMouseScript(mouseProbe()));
    }

    @Test
    void mouseSnapshotOnNativeX86() throws Exception {
        Assumptions.assumeTrue(hasTool("as", "ld"), "native toolchain absent");
        assertEquals(golden(), runMouseNativeX86(mouseProbe()));
    }

    @Test
    void mouseSnapshotOnJs() throws Exception {
        assertEquals(golden(), runMouseJs(mouseProbe()));
    }

    // A virtual backend: four frames of pointer position + held buttons, queried
    // for position, movement and the three button states.
    private static String mouseProbe() {
        return """
                import game.Mouse

                main() {
                    var mouse = Mouse()
                    mouse.beginFrame(100, 50, listOf())
                    println(line(mouse, "left"))
                    mouse.beginFrame(100, 50, listOf("left"))
                    println(line(mouse, "left"))
                    mouse.beginFrame(140, 70, listOf("left"))
                    println(line(mouse, "left"))
                    mouse.beginFrame(140, 70, listOf())
                    println(line(mouse, "left"))
                }

                String line(Mouse m, String button) {
                    return "pos=" + m.x() + "," + m.y()
                        + " d=" + m.dx() + "," + m.dy()
                        + " moved=" + flag(m.moved())
                        + " left=" + state(m, button)
                }

                String flag(Bool v) {
                    if (v) { return "true" }
                    return "false"
                }

                String state(Mouse m, String button) {
                    if (m.pressed(button)) { return "pressed" }
                    if (m.released(button)) { return "released" }
                    if (m.down(button)) { return "down" }
                    return "-"
                }
                """;
    }

    private static String golden() {
        return """
                pos=100,50 d=0,0 moved=false left=-
                pos=100,50 d=0,0 moved=false left=pressed
                pos=140,70 d=40,20 moved=true left=down
                pos=140,70 d=0,0 moved=false left=released""";
    }

    private String runMouseJvm(String code) throws Exception {
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

    private String runMouseScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withGameLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runMouseNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withGameLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runMouseBinary(out.resolve("Default/Main"));
    }

    private String runMouseJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withGameLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runMouseBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private static boolean hasTool(String... cmds) {
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
