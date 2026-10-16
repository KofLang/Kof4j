package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §733 (SCRIPT face): {@code json.decode<Rec>} with a MISSING field
 * SILENTLY defaulted the field (0) on the Script instead of raising the
 * {@code JSN004: missing field '..' for ..} String the JVM/JS raise — the
 * SILENT variant (a wrong value propagates).
 *
 * <p>Fix: {@code KofInterpreterRuntime.decodeKofValue} now raises the SAME String
 * (via {@code KofInterpreterFrame.asThrowable}) when the parsed object does not
 * contain a declared field ({@code containsKey} distinguishes absent from a JSON
 * null).
 *
 * <p>Oracle is the JVM. The NATIVE faces (a missing field AND a malformed
 * payload) remain OPEN — the native {@code kof_json_find_value} returns an empty
 * KofString for BOTH "not found" and a legitimately empty value, so the native
 * fix needs a not-found sentinel (a separate unit).
 */
class JsonDecodeMissingFieldE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            record Point(Int x, Int y)
            main() {
                try { var p = json.decode<Point>("{\\"x\\":1}"); println("ok=" + p.x + "," + p.y) }
                catch (String e) { println("missing=[" + e + "]") }
                var q = json.decode<Point>("{\\"x\\":1,\\"y\\":2}")
                println("good=" + q.x + "," + q.y)
            }
            """;

    private static final String GOLDEN =
            String.join("\n", "missing=[JSN004: missing field 'y' for Point]", "good=1,2");

    @Test
    void jvmIsTheContractForMissingField() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptRaisesTheMissingFieldInsteadOfDefaulting() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip(),
                "§733: o Script defaultou um campo ausente em vez de lançar JSN004");
    }

    @Test
    void jsRaisesTheMissingFieldInsteadOfDefaulting() throws Exception {
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
}
