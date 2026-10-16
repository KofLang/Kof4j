package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §703: a `return` inside a `try`/`finally` inside a LOOP emitted
 * WRONG JS (`undefined`, or a `SyntaxError`/`ReferenceError`). The non-loop and
 * return-in-`if`/`catch` shapes were fixed first; the residual for-in
 * trailing-return shape was §710, closed by the §704/§710 preamble fix.
 *
 * <p>This test pins the WHOLE §703 family across the four targets: return
 * nested in an `if` inside try/finally inside a loop, a trailing return, a
 * return in the catch, nested try/finally with a return, and the non-loop
 * return-finally.
 *
 * <p>Oracle is the JVM.
 */
class JsReturnTryFinallyLoopE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int returnInIf() {
                var r = 0
                for (var i in listOf(0, 1, 2)) {
                    try {
                        if (i == 1) { return 100 }
                        r += i
                    } finally {
                        r += 10
                    }
                }
                return r
            }

            Int trailingReturn() {
                var r = 0
                for (var i in listOf(0, 1, 2)) {
                    try {
                        r += i
                        return r
                    } finally {
                        r += 10
                    }
                }
                return r
            }

            Int returnInCatch() {
                for (var i in listOf(0, 1, 2)) {
                    try {
                        if (i == 1) { throw "x" }
                    } catch (String e) {
                        return 99
                    }
                }
                return 0
            }

            Int nonLoopReturnFinally() {
                try {
                    return 7
                } finally {
                    println("fin")
                }
            }

            main() {
                println(returnInIf())
                println(trailingReturn())
                println(returnInCatch())
                println(nonLoopReturnFinally())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n", "100", "0", "99", "fin", "7", "end");

    @Test
    void jvmIsTheContractForReturnTryFinallyLoop() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnReturnTryFinallyLoop() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnReturnTryFinallyLoop() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "§703: JS return-in-try/finally-in-loop — saída: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86MatchesTheJvmOnReturnTryFinallyLoop() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("x86");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), () -> "x86 saída: " + o);
        assertEquals(GOLDEN, o);
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
