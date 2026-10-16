package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §722: a class METHOD whose name is a JS RESERVED word (e.g. `of`)
 * was DEFINED with the raw name but CALLED with the sanitized name (`_of`), so
 * the method did not exist on JS — `TypeError: f._of is not a function`.
 *
 * <p>Root: {@code JsMethodParser.lowerFunction} used the raw method name for
 * the definition, while {@code JsCallEmitter} applied
 * {@code JsTypeMapper.sanitizeName} at the call site (which prefixes reserved
 * words). Fix: the definition now passes through the SAME sanitize.
 *
 * <p>Oracle is the JVM. RED-first: the JS face threw {@code TypeError} pre-fix.
 */
class JsReservedMethodNameE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            class Factory {
                Int of(Int n) { return n + 1 }
            }

            class Box<T>(T value) {
                T of(Int n) { return n * 2 }
            }

            class Other {
                Int make(Int n) { return n + 10 }
            }

            Int reservedOnPlainClass() {
                return Factory().of(5)
            }

            Int reservedOnGenericClass() {
                return Box<Int>(0).of(5)
            }

            Int nonReservedStillWorks() {
                return Other().make(5)
            }

            main() {
                println(reservedOnPlainClass())
                println(reservedOnGenericClass())
                println(nonReservedStillWorks())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n", "6", "10", "15", "end");

    @Test
    void jvmIsTheContractForReservedMethodName() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnReservedMethodName() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnReservedMethodName() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "§722: método com nome reservado JS — saída: " + stdout);
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
