package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression for the cross-target FFI contract: a STATEFUL C library must keep
 * its globals across Kof {@code extern} calls on every target. The JVM/JS host
 * loaded the library into the per-call confined {@code Arena} and closed it, so
 * the library was released and its globals reset between calls — while Native
 * (link-by-use) persisted them. Measured pre-fix on the shipped CLI: Native
 * {@code 0/1/2/2}, JVM and JS {@code 0/1/1/0}.
 *
 * <p>This is the parity blocker for any stateful C library (SDL3, SQLite, …):
 * the library's global state must survive the call. Fix: cache the
 * {@code SymbolLookup} per library path in a never-closed shared arena and reuse
 * it; the per-call arena still owns the argument/return memory.
 */
class FfiLibraryStateE2ETest {

    private static final String C_SRC = """
            static int kofstate_value = 0;
            int kofstate_inc(void) { return ++kofstate_value; }
            int kofstate_get(void) { return kofstate_value; }
            """;

    private static final String GOLDEN = """
            get0=0
            inc=1
            inc=2
            get=2""";

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void statefulLibraryPersistsOnJvm() throws Exception {
        String so = compileHostLib();
        assertEquals(GOLDEN, runJvm(compile("jvm", so)));
    }

    @Test
    void statefulLibraryPersistsOnJs() throws Exception {
        String so = compileHostLib();
        assertEquals(GOLDEN, runJs(compile("js", so)));
    }

    @Test
    void statefulLibraryPersistsOnNative() throws Exception {
        assumeTrue(hasTool("as", "ld"), "native toolchain absent");
        String so = compileHostLib();
        assertEquals(GOLDEN, runNative(compile("native", so)));
    }

    private String compile(String target, String so) throws Exception {
        Path dir = tmp.resolve(target);
        Files.createDirectories(dir);
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, program(so));
        Path out = dir.resolve("out");
        Target t = switch (target) {
            case "jvm" -> Target.JVM;
            case "js" -> Target.JS;
            default -> Target.NATIVE;
        };
        CompilationResult result = driver.compile(src, out, t);
        assertTrue(result.success(), () -> target + " compile: " + result.diagnostics().getDiagnostics());
        return out.toString();
    }

    private static String program(String so) {
        return """
                extern "%s" kofstate_inc(): Int
                extern "%s" kofstate_get(): Int

                main() {
                    println("get0=" + kofstate_get())
                    println("inc=" + kofstate_inc())
                    println("inc=" + kofstate_inc())
                    println("get=" + kofstate_get())
                }
                """.formatted(so, so);
    }

    private String compileHostLib() throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "stateful host lib uses a native .so (Linux)");
        Path c = tmp.resolve("libkofstate.c");
        Files.writeString(c, C_SRC);
        Path so = tmp.resolve("libkofstate.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "no C toolchain (cc/gcc) for the host lib");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc failed to build the host lib: " + out);
        return so.toString();
    }

    private String runJvm(String outDir) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process p = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", outDir, "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "JVM exit, output: " + output);
        return output;
    }

    private String runJs(String outDir) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(Path.of(outDir).resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit, output: " + out);
        return out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
    }

    private String runNative(String outDir) throws Exception {
        Path bin = Path.of(outDir).resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary must exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "Native exit, output: " + output);
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

    private static String firstPresent(String... candidates) {
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {
                // try the next candidate
            }
        }
        return null;
    }
}
