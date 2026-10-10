package dev.kof.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F10 — {@code kof.process} {@code spawn} + handle ops on JVM and JS with the
 * SAME observable contract (host binding {@code KofJsProcessBridge} landed
 * 19/09; the JS shim routes to the identical ProcessBuilder code path, so
 * parity is by construction, not imitation). Handles: failed spawn = -1,
 * {@code readLine} at EOF/dead = "", {@code exitCode} while alive =
 * {@code Integer.MIN_VALUE}, kill = destroyForcibly + forget (handle becomes
 * dead afterwards). MEASURED quirk of the JVM binding, mirrored on JS:
 * stdin is redirected from {@code /dev/null}, so {@code write} is an honest
 * no-op on both targets today — turning input into a live pipe is a contract
 * change (rule 6), not a parity bug. Native keeps the honest compile-time
 * PROC001 on the Native cross/MCU (the x86-64 native face landed 26/09 —
 * {@code ProcessSpawnNativeE2ETest}; pinned in {@code DomainGapCodesTest}).
 * This is also the platform prerequisite the shell plan names for JS
 * {@code pipeline}.
 */
class ProcessSpawnE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    /** Both targets must run the same Kof source to the SAME output. */
    private void assertJvmJsParity(String source, String... expectedInOutput) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), source);
        Run jvm = runJvm(tmp.resolve("S.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(tmp.resolve("S.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        for (String e : expectedInOutput) {
            assertTrue(jvm.output().contains(e), () -> "JVM expected '" + e + "' in: " + jvm.output());
            assertEquals(jvm.output(), js.output(), "process.spawn JVM/JS parity broken");
        }
    }

    @Test
    void spawnEchoReadsStdoutLineByLine() throws Exception {
        assertJvmJsParity("""
            main() {
                val h = process.spawn("echo", "vivo")
                println(h.readLine())
                var spin = 0
                while (h.alive() && spin < 1000000) { spin = spin + 1 }
                println(h.exitCode())
            }
            """, "vivo", "0");
    }

    @Test
    void readLineAtEofIsHonestEmpty() throws Exception {
        assertJvmJsParity("""
            main() {
                val h = process.spawn("echo", "uma")
                println(h.readLine())
                var spin = 0
                while (h.alive() && spin < 1000000) { spin = spin + 1 }
                println("|" + h.readLine() + "|")
            }
            """, "uma", "||");
    }

    @Test
    void spawnMissingProgramIsHonestDeadHandle() throws Exception {
        assertJvmJsParity("""
            main() {
                val h = process.spawn("no-such-binary-9f3c7a")
                println(if (h.alive()) "alive" else "dead")
                println("|" + h.readLine() + "|")
                println(h.exitCode())
            }
            """, "dead", "||", "-1");
    }

    @Test
    void exitCodeWhileAliveIsSentinelAndKillMakesHandleDead() throws Exception {
        assertJvmJsParity("""
            main() {
                val h = process.spawn("sleep", "30")
                println(h.exitCode())
                println(if (h.alive()) "alive" else "dead")
                h.kill()
                println(if (h.alive()) "alive" else "dead")
                println(h.exitCode())
            }
            """, String.valueOf(Integer.MIN_VALUE), "alive", "dead", "-1");
    }

    /**
     * #753 (04/10): a bare {@code Handle} DECLARED return type was accepted by
     * the unresolved-type guard (it is a builtin simple name, §249) but never
     * mapped to {@code kof.concurrent.Handle} (unlike {@code List}/{@code
     * Channel}), so the JVM emitted {@code LHandle;} and the class died at LOAD
     * with {@code NoClassDefFoundError: Handle}. A method may now return the
     * concurrency handle a {@code spawn} produces, exactly like bare {@code
     * List} returns a {@code listOf}.
     */
    @Test
    void bareHandleDeclaredReturnTypeIsNameableAndRuns() throws Exception {
        assertJvmJsParity("""
            Handle start() {
                return spawn { 7 }
            }

            main() {
                println("ok " + await start())
            }
            """, "ok 7");
    }

    /**
     * #753 (04/10): the same bare-{@code Handle} gap on a DECLARED PARAMETER
     * (a distinct descriptor path from the return type). `declaredCollectionType`
     * did not pin `Handle`, so the parameter erased to `LHandle;` and the class
     * failed at LOAD with {@code NoClassDefFoundError: Handle}.
     */
    @Test
    void bareHandleDeclaredParameterTypeIsNameableAndRuns() throws Exception {
        assertJvmJsParity("""
            Handle start() {
                return spawn { 7 }
            }

            Int use(Handle h) {
                return await h
            }

            main() {
                println("ok " + use(start()))
            }
            """, "ok 7");
    }
}
