package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §738 (JS FACE): {@code String.replace} with an EMPTY needle
 * diverged SILENTLY (rule 5 — a wrong value propagated) on the JS backend. The
 * lowering was {@code receiver.split(from).join(to)}, a correct emulation of the
 * JVM's replace-all for a NON-empty needle, but JS {@code split("")} produces NO
 * boundary element, so the separators the JVM inserts at BOTH ends were lost
 * ({@code "aXbXc".replace("", "-")} gave {@code a-X-b-X-c} instead of
 * {@code -a-X-b-X-c-}), and {@code "".replace("", "-")} gave {@code ""} instead
 * of {@code -}.
 *
 * <p>Fix: the emitter routes {@code replace} to the runtime helper
 * {@code kofStrReplace} (the §730 slice shape — the JS IR has no conditional
 * EXPRESSION node, so the empty-needle branch lives in a helper); the non-empty
 * branch is byte-identical to the previous lowering.
 *
 * <p>Oracle is the JVM (≡ the Script interpreter, both measured). The NATIVE x86
 * face of §738 ({@code replace("",x)} is a no-op there) is a SEPARATE face and
 * stays open in the ledger — out of scope here.
 */
class JsStringReplaceEmptyE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                println("r1=[" + "aXbXc".replace("X", "-") + "]")
                println("r2=[" + "aXbXc".replace("", "-") + "]")
                println("r3=[" + "".replace("", "-") + "]")
                println("r4=[" + "abc".replace("", "") + "]")
                println("r5=[" + "aXbXc".replace("X", "") + "]")
                println("r6=[" + "abc".replace("z", "-") + "]")
                println("r7=[" + "aaa".replace("aa", "-") + "]")
                println("r8=[" + "ab".replace("ab", "c") + "]")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "r1=[a-b-c]",
            "r2=[-a-X-b-X-c-]",
            "r3=[-]",
            "r4=[abc]",
            "r5=[abc]",
            "r6=[abc]",
            "r7=[-a]",
            "r8=[c]");

    @Test
    void jvmIsTheContractForReplaceEmpty() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnReplaceEmpty() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnReplaceEmpty() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "§738: o JS perdeu os separadores de borda — saída: " + stdout);
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
