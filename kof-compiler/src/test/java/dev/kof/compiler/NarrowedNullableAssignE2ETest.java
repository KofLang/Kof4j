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
 * known-bugs §736 (issue #845): a narrowed {@code T?} assigned to a PRIMITIVE
 * local ({@code if (i != null) { Int j = i } }) reached the {@code istore} still
 * BOXED — the JVM refused the class ({@code VerifyError: Bad type on operand
 * stack}, "Integer not assignable to integer"), masked by the launcher's
 * misleading "JavaFX ausente" message.
 *
 * <p>Root: the origin slot of a {@code Nullable(primitivo)} is boxed (since
 * D-NULL-INTENT) but the DESTINATION slot here is the raw primitive; the
 * declaration lowering did the widening/narrowing but never the UNBOX, unlike
 * the ARGUMENT path (#770, {@code CompilerEmission2}). Fix:
 * {@code StatementLowererLocalBoxing.emitLocalDeclaration} now mirrors the
 * argument path — when the declared type is a raw primitive and the
 * initializer's inferred type is a {@code Nullable(primitivo)}, it emits
 * {@code emitErasureUnboxSoft}.
 *
 * <p>Two producers are pinned: the erased {@code Map.get} and the NULLABLE
 * RECORD FIELD getter. Oracle is the JVM.
 */
class NarrowedNullableAssignE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            record Box(Int? v)
            main() {
                var m = mapOf("a", 1)
                var i = m.get("a")
                if (i != null) {
                    Int j = i
                    println("map=" + j)
                }
                var b = Box(5)
                var x = b.v
                if (x != null) {
                    Int k = x
                    println("field=" + k)
                }
                var ml = mapOf("a", 9L)
                var q = ml.get("a")
                if (q != null) {
                    Long z = q
                    println("long=" + z)
                }
                var l = m.get("z")
                if (l == null) {
                    println("null-ok")
                }
                println("done")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "map=1", "field=5", "long=9", "null-ok", "done");

    @Test
    void jvmIsTheContractForNarrowedNullableAssign() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnNarrowedNullableAssign() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnNarrowedNullableAssign() throws Exception {
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
    void nativeX86MatchesTheJvmOnNarrowedNullableAssign() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
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