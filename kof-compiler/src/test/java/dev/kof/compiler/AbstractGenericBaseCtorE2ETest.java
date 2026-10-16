package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §720 (JVM face): a class extending a GENERIC abstract base
 * (`class Impl(Int n) extends Base<Int>`) emitted a `super()` call whose OWNER
 * was the RAW superclass name `"Base&lt;Int&gt;"` (with the type argument) instead
 * of the ERASED `Base` — {@code VerifyError: Bad operand type when invoking
 * <init>} on the JVM.
 *
 * <p>Root: {@code CompilerRecordSupport.generateRecordConstructor} used
 * {@code rec.superClass()} RAW, unlike {@code CompilerClassLowering} (line 24)
 * and {@code CompilerIfaceRecordLowering} (line 48), which both call
 * {@code eraseTypeArgs}. Fix: erase the type args.
 *
 * <p>NOTE: the NATIVE face of §720 (an abstract generic method call boxes the
 * erased `T` while the concrete override returns raw) is a SEPARATE mechanism
 * (same family as §717) and remains OPEN — this test covers the JVM/Script/JS
 * faces only.
 *
 * <p>Oracle is the JVM. RED-first: the JVM face threw {@code VerifyError}.
 */
class AbstractGenericBaseCtorE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            abstract class Base<T> {
                abstract T value()
                T twice() { return value() }
            }

            class Impl(Int n) extends Base<Int> {
                Int value() { return n }
            }

            main() {
                var x: Base<Int> = Impl(4)
                println(x.value())
                println(x.twice())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n", "4", "4", "end");

    @Test
    void jvmIsTheContractForAbstractGenericBase() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnAbstractGenericBase() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnAbstractGenericBase() throws Exception {
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
