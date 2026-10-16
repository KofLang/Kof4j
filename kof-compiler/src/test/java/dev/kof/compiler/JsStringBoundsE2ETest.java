package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §730: {@code String.substring}/{@code charAt} OUT OF RANGE silently
 * CLAMPED on the JS backend (the host {@code String.prototype} clamps by
 * definition and SWAPS the args of {@code substring}), where the JVM/Script
 * raise the named {@code Range [..) out of bounds for length N} /
 * {@code Index i out of bounds for length N} message. The silent variant is the
 * worst class (rule 5).
 *
 * <p>Fix: the JS emitter routes {@code charAt}/{@code substring} to
 * {@code kofStrCharAt}/{@code kofStrSubstring}, which perform the range check
 * and THROW the JVM's exact message (a JS string the Kof {@code catch (String e)}
 * receives), in their own runtime slice.
 *
 * <p>Oracle is the JVM. (The native face ABORTS via §729 — a separate bug — so it
 * is out of scope here, matching the ledger's JVM/Script/JS table.)
 */
class JsStringBoundsE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                try { println("abc".substring(9)) } catch (String e) { println("A=" + e) }
                try { println("abc".substring(2, 1)) } catch (String e) { println("B=" + e) }
                try { println("abc".substring(-1)) } catch (String e) { println("C=" + e) }
                try { println("abc".charAt(9)) } catch (String e) { println("D=" + e) }
                try { println("abc".charAt(-1)) } catch (String e) { println("E=" + e) }
                println("abc".substring(1, 2))
                println("abc".charAt(1))
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "A=Range [9, 3) out of bounds for length 3",
            "B=Range [2, 1) out of bounds for length 3",
            "C=Range [-1, 3) out of bounds for length 3",
            "D=Index 9 out of bounds for length 3",
            "E=Index -1 out of bounds for length 3",
            "b",
            "b");

    @Test
    void jvmIsTheContractForStringBounds() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnStringBounds() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnStringBounds() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "§730: o JS clampou em vez de lançar — saída: " + stdout);
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
