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
 * + `D-GRAPHICS-SPIKE` + `D-MAINT-BATCH-0510`/G1): the pure-Kof per-frame gamepad
 * snapshot {@code libs/game/Pad.kf}. The plan §7 freezes gamepad input as a
 * snapshot per frame — the stick axes and the states {@code down}/{@code pressed}/
 * {@code released}; the backend translates the device events and calls
 * {@code beginFrame(leftX, leftY, rightX, rightY, buttons)} once per frame, so
 * this module is the backend-independent half and runs on every target.
 *
 * <p>Golden: a virtual pad stream — frame 0 neutral, frame 1 the {@code a} button
 * pressed with the left stick at (0.5, -0.25), frame 2 {@code a} held and
 * {@code b} pressed with the right stick at (0.75, 1.0), frame 3 all released and
 * neutral. The same run on JVM + Script + Native x86-64 + JS must print the
 * identical golden (deterministic input, the plan's golden property). Axes are
 * printed scaled to milli-units (integer) so the golden does not depend on the
 * Double formatting of each backend.
 */
class GamePadE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Clock.kf", "Keys.kf", "Mouse.kf", "Pad.kf");
    }

    @Test
    void padSnapshotOnJvm() throws Exception {
        assertEquals(golden(), runPadJvm(padProbe()));
    }

    @Test
    void padSnapshotOnScript() throws Exception {
        assertEquals(golden(), runPadScript(padProbe()));
    }

    @Test
    void padSnapshotOnNativeX86() throws Exception {
        Assumptions.assumeTrue(hasTool("as", "ld"), "native toolchain absent");
        assertEquals(golden(), runPadNativeX86(padProbe()));
    }

    @Test
    void padSnapshotOnJs() throws Exception {
        assertEquals(golden(), runPadJs(padProbe()));
    }

    // A virtual backend: four frames of stick axes + held buttons, queried for the
    // axes and the three button states. Axes are scaled to milli-units (integer) so
    // the golden is Double-formatting independent across targets.
    private static String padProbe() {
        return """
                import game.Pad

                main() {
                    var pad = Pad()
                    pad.beginFrame(0.0, 0.0, 0.0, 0.0, listOf())
                    println(line(pad))
                    pad.beginFrame(0.5, -0.25, 0.0, 0.0, listOf("a"))
                    println(line(pad))
                    pad.beginFrame(0.5, -0.25, 0.75, 1.0, listOf("a", "b"))
                    println(line(pad))
                    pad.beginFrame(0.0, 0.0, 0.0, 0.0, listOf())
                    println(line(pad))
                }

                String line(Pad p) {
                    return "l=" + milli(p.leftX()) + "," + milli(p.leftY())
                        + " r=" + milli(p.rightX()) + "," + milli(p.rightY())
                        + " a=" + state(p, "a") + " b=" + state(p, "b")
                }

                Int milli(Double v) {
                    return (v * 1000.0) as Int
                }

                String state(Pad p, String button) {
                    if (p.pressed(button)) { return "pressed" }
                    if (p.released(button)) { return "released" }
                    if (p.down(button)) { return "down" }
                    return "-"
                }
                """;
    }

    private static String golden() {
        return """
                l=0,0 r=0,0 a=- b=-
                l=500,-250 r=0,0 a=pressed b=-
                l=500,-250 r=750,1000 a=down b=pressed
                l=0,0 r=0,0 a=released b=released""";
    }

    private String runPadJvm(String code) throws Exception {
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

    private String runPadScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withGameLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runPadNativeX86(String code) throws Exception {
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
        return runPadBinary(out.resolve("Default/Main"));
    }

    private String runPadJs(String code) throws Exception {
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

    private String runPadBinary(Path binary) throws Exception {
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
