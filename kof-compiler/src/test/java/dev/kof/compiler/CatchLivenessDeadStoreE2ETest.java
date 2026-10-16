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
 * known-bugs §709: the Phase C {@code LivenessDeadStores} pass removed the
 * initialization of a local that is READ ONLY IN A CATCH. The liveness CFG did
 * not model the exception edge because {@code KofTryStart.handlerLabel()} is
 * carried by the {@code KofCatchStart} op, NOT by a {@code KofLabel} — so
 * {@code ControlFlowGraph.blockOf(handler)} returned {@code -1}, the edge was
 * silently dropped, the slot looked dead, and the dead-store pass removed its
 * {@code store} together with the {@code LoadLiteral} producer.
 *
 * <p>Symptom: {@code var r = 0; try { throw "x" } catch (String e) { r += 1 }
 * return r} — the Script threw {@code ClassCastException: not an int: null}
 * (reading the never-stored {@code r}) and JS returned {@code 0} (the catch
 * never ran). Fix: {@code ControlFlowGraph} marks a {@code KofCatchStart} as a
 * block leader and maps the try's handler label to that block, so the
 * exception edge is built and the slot is live.
 *
 * <p>Oracle is the JVM. RED-first: with the CFG fix reverted the Script face
 * throws and the JS face is wrong.
 */
class CatchLivenessDeadStoreE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int catchOnlyRead() {
                var r = 0
                try {
                    throw "x"
                } catch (String e) {
                    r += 1
                }
                return r
            }

            Int catchAndAfter() {
                var r = 0
                try {
                    r = 5
                } catch (String e) {
                    r = 9
                }
                return r + 1
            }

            Int nestedCatch() {
                var r = 0
                try {
                    try {
                        throw "inner"
                    } catch (String e) {
                        r += 1
                    }
                } catch (String e) {
                    r += 100
                }
                return r
            }

            Int finallyOnlyRead() {
                var r = 0
                try {
                    r = 3
                } finally {
                    r += 2
                }
                return r
            }

            main() {
                println(catchOnlyRead())
                println(catchAndAfter())
                println(nestedCatch())
                println(finallyOnlyRead())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n", "1", "6", "1", "5", "end");

    @Test
    void jvmIsTheContractForCatchLiveness() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnCatchLiveness() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "§709: o Script lançou (store do catch removido): " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnCatchLiveness() throws Exception {
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
    void nativeX86MatchesTheJvmOnCatchLiveness() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    @Test
    void nativeRiscv64MatchesTheJvmOnCatchLiveness() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("riscv64", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64MatchesTheJvmOnCatchLiveness() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("aarch64", Target.NATIVE_AARCH64, "aarch64"));
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
        ProcessBuilder pb = arch == null
                ? new ProcessBuilder(binary.toString())
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
