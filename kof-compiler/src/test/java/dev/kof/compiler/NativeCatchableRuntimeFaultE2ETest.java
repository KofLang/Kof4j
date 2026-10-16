package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §729 (a bounds error was a hard ABORT on native — {@code kof_panic},
 * so a Kof {@code catch (String e)} never fired while JVM/Script/JS caught it) and
 * the latent x86 face it exposed: the runtime guards passed an {@code .asciz}
 * C-string as the exception, so a catch that USED {@code e} (concat/`length`)
 * read a garbage header and SIGSEGV'd — masked because §693's E2E never used
 * {@code e}.
 *
 * <p>Fix: the runtime fault guards ({@code kof_bounds_error}, and the x86
 * div-by-zero guard that was the lone asciz outlier) build a real KofString via
 * {@code kof_string_from_literal} before {@code kof_throw_string}.
 *
 * <p>Oracle is the JVM. The native fault TEXT is the runtime's own
 * ({@code Runtime error: array index out of bounds}) vs the JVM's
 * ({@code Index 99 out of bounds for length 2}) — a documented boundary (§729);
 * this test pins the CONTRACT that the fault is CATCHABLE and the message is a
 * usable non-empty KofString on every target.
 */
class NativeCatchableRuntimeFaultE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                var xs = listOf(1, 2)
                try { println(xs.get(99)) } catch (String e) { println("bounds-caught=" + (e.length() > 0)) }
                var z = 0
                try { println(10 / z) } catch (String e) { println("div-caught=" + (e.length() > 0)) }
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n", "bounds-caught=true", "div-caught=true", "end");

    @Test
    void jvmIsTheContractForCatchableFault() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnCatchableFault() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnCatchableFault() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var so = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), so,
                new java.io.ByteArrayInputStream(new byte[0]), so);
        assertEquals(0, exit, () -> "js saída: " + so);
        assertEquals(GOLDEN, so.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86CatchableFaultMatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    @Test
    void nativeRiscv64CatchableFaultMatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("riscv", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64CatchableFaultMatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("aarch64", Target.NATIVE_AARCH64, "aarch64"));
    }

    // ── harness ────────────────────────────────────────────────────────────

    private String runJvm() throws Exception {
        Path dir = tmp.resolve("jvm");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "jvm");
        assertEquals(0, p.exitValue(), () -> "oráculo JVM: " + o);
        return o;
    }

    private String runNative(String name, Target target, String qemu) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        ProcessBuilder pb = qemu == null ? new ProcessBuilder(bin.toString())
                : NativeRiscv64E2ETest.qemu(qemu, bin);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = NativeRiscv64E2ETest.runBounded(p, name);
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
