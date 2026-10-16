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
 * known-bugs §707: a {@code do-while} loop whose body contains a
 * {@code break}/{@code continue} (or any {@code if} whose then-branch exits
 * unconditionally) did NOT COMPILE on JS —
 * {@code COMP002: Internal compiler error: KofJS: unexpected op in expression
 * statement: KofConditionalJump}.
 *
 * <p>Root: the do-while is the one construct whose condition (a
 * {@code KofConditionalJump} targeting the loop's own start label) comes AFTER
 * the body. When the body contained an {@code if} whose then-branch ends
 * unconditionally ({@code break}/{@code continue}/{@code return}), the JS
 * {@code parseIfBody} routed the following code to {@code parseElse}, which
 * consumed the rest of the body AND the do-while condition, leaving the
 * condition CJump at the expression-statement level — the dispatcher threw.
 *
 * <p>The oracle is the JVM. RED-first: the JS face was RED pre-fix.
 */
class JsDoWhileBreakContinueE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int doWhileBreak() {
                var s = 0
                var i = 0
                do {
                    i++
                    if (i == 3) { break }
                    s += i
                } while (i < 10)
                return s
            }

            Int doWhileContinue() {
                var s = 0
                var i = 0
                do {
                    i++
                    if (i == 2) { continue }
                    s += i
                } while (i < 4)
                return s
            }

            Int doWhileReturn() {
                var s = 0
                var i = 0
                do {
                    i++
                    if (i == 3) { return 99 }
                    s += i
                } while (i < 10)
                return s
            }

            Int doWhilePlainIf() {
                var s = 0
                var i = 0
                do {
                    i++
                    if (i == 3) { s += 100 }
                    s += i
                } while (i < 5)
                return s
            }

            Int doWhileNestedIf() {
                var s = 0
                var i = 0
                do {
                    i++
                    if (i > 1) {
                        if (i == 3) { break }
                        s += 10
                    }
                    s += i
                } while (i < 10)
                return s
            }

            Int doWhileNoExit() {
                var s = 0
                var i = 0
                do {
                    s += i
                    i++
                } while (i < 3)
                return s
            }

            Int whileBreakControl() {
                var s = 0
                var i = 0
                while (i < 10) {
                    i++
                    if (i == 3) { break }
                    s += i
                }
                return s
            }

            main() {
                println(doWhileBreak())
                println(doWhileContinue())
                println(doWhileReturn())
                println(doWhilePlainIf())
                println(doWhileNestedIf())
                println(doWhileNoExit())
                println(whileBreakControl())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "3", "8", "99", "115", "13", "3", "3", "end");

    @Test
    void jvmIsTheContractForDoWhileBreakContinue() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnDoWhileBreakContinue() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip(), "§707: o Script já compilava");
    }

    @Test
    void jsMatchesTheJvmOnDoWhileBreakContinue() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "§707: o do-while com break/continue não compilava no JS — "
                + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js saída: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86MatchesTheJvmOnDoWhileBreakContinue() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    @Test
    void nativeRiscv64MatchesTheJvmOnDoWhileBreakContinue() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("riscv64", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64MatchesTheJvmOnDoWhileBreakContinue() throws Exception {
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
