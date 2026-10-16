package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §703: a {@code return} inside a {@code try}/{@code finally} used to
 * emit WRONG JS — the IR lowers a return-in-try to {@code _retVal = X; goto
 * returnFinally}, and the JS backend rendered the return-finally EPILOGUE
 * ({@code return _retVal}) INLINE right after the try/finally block. Outside a
 * loop that lands at the method tail (accidentally OK); INSIDE a loop it lands
 * in the loop body, running unconditionally and returning the still-undefined
 * {@code _retVal}. And when the finally body was empty (its store removed by
 * the optimizer) the JS emitted {@code try { ... }} with NO clause (SyntaxError).
 *
 * <p>Fix: inside the body of a try that HAS a return-finally (the try body AND
 * each catch body), a store to {@code #retVal} is emitted as {@code return X},
 * letting JS's native try/finally run the finally on the abrupt completion; the
 * inline epilogue is dropped; and a try with an empty finally and no catch emits
 * an explicit {@code finally {}} so the syntax stays valid.
 *
 * <p>Oracle is the JVM. RED-first: the JS face was RED pre-fix.
 */
class JsReturnTryFinallyE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int nonLoopTrailing() {
                var x = 0
                try { return 5 } finally { x = 7 }
                return x
            }

            Int nonLoopPlusEq() {
                var x = 0
                try { return 5 } finally { x += 7 }
                return x
            }

            Int loopReturnInIf() {
                var r = 0
                for (var i in listOf(0, 1, 2)) {
                    try {
                        if (i == 1) { return 100 }
                        r += i
                    } finally { r += 10 }
                }
                return r
            }

            Int nestedTryFinally() {
                try {
                    try {
                        return 1
                    } finally {
                        println("inner")
                    }
                } finally {
                    println("outer")
                }
            }

            main() {
                println(nonLoopTrailing())
                println(nonLoopPlusEq())
                println(loopReturnInIf())
                println(nestedTryFinally())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "5", "5", "100", "inner", "outer", "1", "end");

    @Test
    void jvmIsTheContractForReturnTryFinally() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnReturnTryFinally() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnReturnTryFinally() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "§703: compilação JS falhou — " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "§703: return em try/finally — saída: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip());
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
}
