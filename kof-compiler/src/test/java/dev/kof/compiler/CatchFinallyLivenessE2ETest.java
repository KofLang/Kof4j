package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §711: a {@code try}/{@code catch}/{@code finally} where the
 * {@code finally} READS a local that the try/catch assigns produced a JVM
 * {@code VerifyError: Bad local variable type} (the handler saw the local as
 * {@code top}). The Phase C {@code LivenessDeadStores} pass removed the local's
 * initialization.
 *
 * <p>Root: a try has MORE THAN ONE exception handler — the user's {@code catch}
 * AND the SYNTHETIC catch-all that emulates {@code finally} — with DISTINCT
 * {@code handlerLabel}s. §709's liveness fix built the exception edge from the
 * {@code KofTryStart.handlerLabel} only, so the {@code finally} handler block was
 * outside the flow; a slot read only there (or initialized before the try)
 * looked dead and its store was removed.
 *
 * <p>Fix: the liveness exception edges (successors AND the handler-liveIn
 * pseudo-USE) now cover EVERY {@code KofCatchStart} in the try region, not just
 * the declared handler label.
 *
 * <p>Oracle is the JVM. RED-first: the JVM face threw {@code VerifyError}
 * pre-fix (exposed via {@code KofJvmMain}, since the JDK launcher masks it with
 * the false missing-JavaFX message).
 */
class CatchFinallyLivenessE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int catchAssignsFinallyReads() {
                var v = 0
                try {
                    v = 1
                    throw "x"
                } catch (String e) {
                    v = 99
                } finally {
                    v = v + 1
                }
                return v
            }

            Int noThrowFinallyReads() {
                var v = 0
                try {
                    v = 1
                } catch (String e) {
                    v = 99
                } finally {
                    v = v + 1
                }
                return v
            }

            Int finallyPrintsAssigned() {
                var v = 0
                try {
                    v = 7
                    throw "x"
                } catch (String e) {
                    v = 99
                } finally {
                    println(v)
                }
                return v
            }

            String catchReturnsFinallyReads() {
                var v = 0
                try {
                    v = 1
                    throw "x"
                } catch (String e) {
                    v = 99
                    return "c" + v
                } finally {
                    v = v + 1
                }
            }

            main() {
                println(catchAssignsFinallyReads())
                println(noThrowFinallyReads())
                println(finallyPrintsAssigned())
                println(catchReturnsFinallyReads())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "100", "2", "99", "99", "c99", "end");

    @Test
    void jvmIsTheContractForCatchFinallyLiveness() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnCatchFinallyLiveness() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnCatchFinallyLiveness() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js saída: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86MatchesTheJvmOnCatchFinallyLiveness() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    // ── harness ────────────────────────────────────────────────────────────

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM): " + o);
        return o;
    }

    private String runNative(String name, Target target, String arch) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), name + ": binário nativo ausente");
        ProcessBuilder pb = arch == null ? new ProcessBuilder(binary.toString())
                : NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " (" + target + ")");
        assertEquals(0, p.exitValue(), () -> name + " saída: " + o);
        return o;
    }

    private static boolean has(String... cmds) {
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
}
