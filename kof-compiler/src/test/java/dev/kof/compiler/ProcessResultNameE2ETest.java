package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `D-MAINT-BATCH-0610`/C (name the handle): `kof.process.Result` is a
 * declarable name (var/param/field/return) — same pattern as
 * `KofUi.typeByName` (§179) and `KofNet.typeByName` (fatia 6), registered
 * via `CompilerTypes.builtinDeclaredType` (the user homonym still wins,
 * §243). Pre-fix a declared `Result` died `SEM011`; now the handle flows
 * between functions with field access intact, on JVM + Script + JS +
 * Native x86-64.
 */
class ProcessResultNameE2ETest implements NativeToolchainAssumptions {

    private static final String SRC = """
            import kof.process

            Int codeOf(Result r) {
                return r.exitCode
            }

            Result runEcho() {
                var out: Result = process.run("echo", "hi")
                return out
            }

            main() {
                var r: Result = runEcho()
                println(codeOf(r))
                println(r.stdout)
            }
            """;

    private static final String EXPECTED = "0\nhi";

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    @Test
    void declaredResultFlowsOnJvm(@TempDir Path tmp) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path src = tmp.resolve("M.kf");
        Files.writeString(src, SRC);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + diags(r));
        String[] cp = {out.toString(), System.getProperty("user.dir") + "/../kof-runtime/target/classes"};
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", String.join(":", cp), "Default.Main")
                .redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run, output: " + s);
        assertEquals(EXPECTED, s, "JVM");
    }

    @Test
    void declaredResultFlowsOnScript(@TempDir Path tmp) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path src = tmp.resolve("M.kf");
        Files.writeString(src, SRC);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().replace("\r\n", "\n").trim(), "Script");
    }

    @Test
    void declaredResultFlowsOnJs(@TempDir Path tmp) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path src = tmp.resolve("M.kf");
        Files.writeString(src, SRC);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + diags(r));
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        assertEquals(EXPECTED, stdout.toString(java.nio.charset.StandardCharsets.UTF_8).strip(), "JS");
    }

    @Test
    void declaredResultFlowsOnNativeX86(@TempDir Path tmp) throws Exception {
        assumeNativeX86_64();
        CompilerDriver driver = new CompilerDriver();
        Path src = tmp.resolve("M.kf");
        Files.writeString(src, SRC);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + diags(r));
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(bin), "native binary must exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "native output: " + s);
        assertEquals(EXPECTED, s, "Native x86-64");
    }
}
