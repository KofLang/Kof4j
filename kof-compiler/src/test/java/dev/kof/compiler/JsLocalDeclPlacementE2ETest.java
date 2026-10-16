package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §708: a local declared with an initializer and later assigned
 * inside an {@code if}/{@code else} lost its JS declaration — the {@code let}
 * was emitted at the FIRST store, which (once the optimizer removed the
 * top-of-method dead store) is INSIDE the branch, so the other branch and the
 * code after the {@code if} referenced an undeclared name
 * ({@code ReferenceError: <var> is not defined}).
 *
 * <p>Root: the JS backend's {@code storeLocalStatement} adds a slot to
 * {@code ctx.declared} at its first store and emits {@code let x = v} inline;
 * the method-scope predecl ran AFTER the body parse, so it only caught slots
 * the body had NOT already declared. Fix: pre-declare EVERY local at the top of
 * the method BEFORE parsing the body, so no store emits an inline {@code let}
 * and the declaration is always at method scope — robust to optimized IR.
 *
 * <p>Oracle is the JVM. RED-first: the JS face was RED pre-fix.
 */
class JsLocalDeclPlacementE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int assignedInIf(Int flag) {
                var w = 5
                var x = 3
                var sum = w + x
                if (flag > 0) { w = 6 } else { w = 7 }
                return w + sum
            }

            Int assignedInNestedIf(Int n) {
                var acc = 0
                if (n > 0) {
                    if (n > 10) { acc = 100 } else { acc = 50 }
                } else {
                    acc = 1
                }
                return acc
            }

            Int assignedInLoop(Int n) {
                var r = 0
                var i = 0
                while (i < n) {
                    if (i % 2 == 0) { r = r + i } else { r = r + 1 }
                    i = i + 1
                }
                return r
            }

            Int assignedInSwitch(Int k) {
                var v = 0
                switch (k) {
                    case 1: { v = 10 }
                    case 2: { v = 20 }
                    default: { v = 99 }
                }
                return v
            }

            Int plainLocal() {
                var a = 7
                var b = a * 2
                return a + b
            }

            main() {
                println(assignedInIf(1))
                println(assignedInIf(-1))
                println(assignedInNestedIf(20))
                println(assignedInNestedIf(5))
                println(assignedInNestedIf(-1))
                println(assignedInLoop(5))
                println(assignedInSwitch(1))
                println(assignedInSwitch(2))
                println(assignedInSwitch(7))
                println(plainLocal())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "14", "15", "100", "50", "1", "8", "10", "20", "99", "21", "end");

    @Test
    void jvmIsTheContractForLocalDeclPlacement() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnLocalDeclPlacement() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnLocalDeclPlacement() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "§708: compilação JS falhou — " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "§708: `let` de local caiu no escopo do ramo — saída: " + stdout);
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
