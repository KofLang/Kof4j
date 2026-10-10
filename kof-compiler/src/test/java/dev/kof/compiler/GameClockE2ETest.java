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
 * + `D-GRAPHICS-SPIKE` + `D-MAINT-BATCH-0510`/G1-G2): the pure-Kof frame clock
 * {@code libs/game/Clock.kf}. The clock is the backend-independent half of the
 * plan §6 loop contract — it owns the frame bookkeeping and {@code dt} over
 * caller-supplied timestamps (the "virtual clock" the plan requires for
 * goldens) and calls no rendering/window/audio API, so it runs on every target.
 *
 * <p>Golden: a virtual source whose timestamp advances 1000µs per call; frame 0
 * has {@code dt=0} and every later frame {@code dt=1000µs}. The same run on
 * JVM + Script + Native x86-64 + JS must print the identical golden.
 */
class GameClockE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() { return java.util.List.of("Clock.kf"); }

    @Test
    void gameClockOnJvm() throws Exception {
        assertEquals(golden(), runClockJvm(probe()));
    }

    @Test
    void gameClockOnScript() throws Exception {
        assertEquals(golden(), runClockScript(probe()));
    }

    @Test
    void gameClockOnNativeX86() throws Exception {
        Assumptions.assumeTrue(hasTool("as", "ld"), "native toolchain absent");
        assertEquals(golden(), runClockNativeX86(probe()));
    }

    @Test
    void gameClockOnJs() throws Exception {
        assertEquals(golden(), runClockJs(probe()));
    }

    // A virtual monotonic source: 1000µs per call, call-counted.
    private static String probe() {
        return """
                import game.Clock

                class Source {
                    Int ticks
                    constructor() { this.ticks = 0 }
                    Long now() {
                        this.ticks = this.ticks + 1
                        return (this.ticks * 1000) as Long
                    }
                }

                main() {
                    var src = Source()
                    var clock = Clock()
                    clock.start(src.now())
                    while (clock.hasNext()) {
                        var frame = clock.beginFrame(src.now())
                        println("frame=" + frame + " dt=" + clock.dtMicros() + " ms=" + clock.dtMillis())
                        if (frame >= 2) { clock.stop() }
                    }
                    println("stopped=" + clock.hasNext())
                    println("elapsed=" + clock.elapsedMicros(src.now()))
                }
                """;
    }

    private static String golden() {
        return """
                frame=0 dt=0 ms=0
                frame=1 dt=1000 ms=1
                frame=2 dt=1000 ms=1
                stopped=false
                elapsed=4000""";
    }

    private String runClockJvm(String code) throws Exception {
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

    private String runClockScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withGameLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runClockNativeX86(String code) throws Exception {
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
        return runClockBinary(out.resolve("Default/Main"));
    }

    private String runClockJs(String code) throws Exception {
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

    private String runClockBinary(Path binary) throws Exception {
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
