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
 * known-bugs §733 (the STRING/reference face): per the DECIDED contract §565
 * ({@code D-JSON-MISSING-PRIMITIVE}, maintainer 03/10), only a missing PRIMITIVE
 * component throws the named {@code JSN004}; a REFERENCE component (String, …)
 * stays {@code null} with no error. Two targets diverged from that contract:
 * <ul>
 *   <li>the SCRIPT threw {@code JSN004} for a missing String too (the §733 fix
 *       over-reached — it keyed on {@code !containsKey} for every field);</li>
 *   <li>the native x86 returned the EMPTY STRING for a missing String, because
 *       {@code kof_json_find_value} used an empty KofString as BOTH the absent
 *       sentinel AND a legitimate empty value.</li>
 * </ul>
 *
 * <p>Fix: (a) {@code KofInterpreterRuntime.decodeKofValue} throws only when the
 * field type is a non-nullable primitive; (b) {@code kof_json_find_value}'s
 * not-found branch returns NULL (a sentinel distinct from a real empty string),
 * and the three security/JWT call sites (which dereferenced the result) gained a
 * null check.
 */
class NativeJsonDecodeMissingStringE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            record S(String a, Int b)
            main() {
                var r = json.decode<S>("{\\"b\\":2}")
                println("missingNotE=" + (r.a == ""))
                try { var p = json.decode<S>("{\\"a\\":\\"x\\"}"); println("nothrow") }
                catch (String e) { println("missingPrim=" + e.startsWith("JSN004")) }
                var e2 = json.decode<S>("{\\"a\\":\\"\\",\\"b\\":2}")
                println("emptyok=" + (e2.a == "") + ",b=" + e2.b)
                var g = json.decode<S>("{\\"a\\":\\"xy\\",\\"b\\":2}")
                println("good=" + g.a + "," + g.b)
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "missingNotE=false", "missingPrim=true", "emptyok=true,b=2", "good=xy,2");

    @Test
    void jvmIsTheContractForNativeJsonMissingString() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnNativeJsonMissingString() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnNativeJsonMissingString() throws Exception {
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
    void nativeX86JsonMissingStringMatchesTheJvm() throws Exception {
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
