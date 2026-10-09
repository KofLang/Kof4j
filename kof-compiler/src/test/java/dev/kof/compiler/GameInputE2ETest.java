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
 * + `D-GRAPHICS-SPIKE`): the pure-Kof per-frame input snapshot
 * {@code libs/game/Keys.kf}. The plan §7 freezes input as a snapshot per frame
 * with the states {@code down}/{@code pressed}/{@code released}; the backend
 * translates the OS events and calls {@code beginFrame(held)} once per frame,
 * so this module is the backend-independent half and runs on every target.
 *
 * <p>Golden: a virtual held-key stream driven by a virtual clock — frame 0
 * {@code [left]}, frame 1 {@code [left, space]}, frame 2 {@code [space]},
 * frame 3 {@code []}. The same run on JVM + Script + Native x86-64 + JS must
 * print the identical golden (deterministic input, the plan's golden property).
 */
class GameInputE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Clock.kf", "Keys.kf");
    }

    @Test
    void inputSnapshotOnJvm() throws Exception {
        assertEquals(golden(), runKeysJvm(inputProbe()));
    }

    @Test
    void inputSnapshotOnScript() throws Exception {
        assertEquals(golden(), runKeysScript(inputProbe()));
    }

    @Test
    void inputSnapshotOnNativeX86() throws Exception {
        Assumptions.assumeTrue(hasTool("as", "ld"), "native toolchain absent");
        assertEquals(golden(), runKeysNativeX86(inputProbe()));
    }

    @Test
    void inputSnapshotOnJs() throws Exception {
        assertEquals(golden(), runKeysJs(inputProbe()));
    }

    // A virtual backend: four frames of held keys, queried for all three states.
    private static String inputProbe() {
        return """
                import game.Keys

                main() {
                    var keys = Keys()
                    var frames = listOf(listOf("left"), listOf("left", "space"), listOf("space"), listOf())
                    var i = 0
                    while (i < frames.size) {
                        keys.beginFrame(frames.get(i))
                        println("f" + i
                            + " left=" + state(keys, "left")
                            + " space=" + state(keys, "space"))
                        i = i + 1
                    }
                }

                String state(Keys k, String key) {
                    if (k.pressed(key)) { return "pressed" }
                    if (k.released(key)) { return "released" }
                    if (k.down(key)) { return "down" }
                    return "-"
                }
                """;
    }

    private static String golden() {
        return """
                f0 left=pressed space=-
                f1 left=down space=pressed
                f2 left=released space=down
                f3 left=- space=released""";
    }

    private String runKeysJvm(String code) throws Exception {
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

    private String runKeysScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withGameLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runKeysNativeX86(String code) throws Exception {
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
        return runKeysBinary(out.resolve("Default/Main"));
    }

    private String runKeysJs(String code) throws Exception {
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

    private String runKeysBinary(Path binary) throws Exception {
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
