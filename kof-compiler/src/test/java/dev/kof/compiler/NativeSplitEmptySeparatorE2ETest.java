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
 * known-bugs §738 (NATIVE x86 FACE): {@code String.split("")} — an EMPTY
 * separator — silently returned ONE element on the native backend where
 * JVM/Script/JS return one element PER CHARACTER ({@code "abc".split("")} = 3).
 * The emitter detected the empty separator and passed {@code sep_char = 0} (NUL)
 * into {@code kof_string_split}, and a NUL never occurs in the text, so the
 * "split" was the whole string (rule 5 — a wrong value propagated).
 *
 * <p>Fix: the empty-separator branch now calls the ADDITIVE helper
 * {@code kof_string_split_chars} (per UTF-8 CODE POINT, the {@code D-STR-UNICODE}
 * direction), covering the {@code (String, Int)} limit overload too; the
 * non-empty-separator path is byte-identical to before.
 *
 * <p>Oracle is the JVM (≡ the Script interpreter, both measured). Documented
 * boundary: an ASTRAL character is ONE element here (the JVM, splitting by UTF-16
 * code unit, would give 2 for a surrogate pair) — the probe covers the BMP.
 * The {@code replace("", x)} sibling face stays OPEN in the ledger (its own
 * helper); the cross targets are a separate face.
 */
class NativeSplitEmptySeparatorE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                println("a1=" + "abc".split("").size)
                println("a2=" + "".split("").size)
                println("a3=" + "a".split("").size)
                println("a4=" + "a,b,".split(",").size)
                println("a5=" + "abc".split(":").size)
                println("a6=" + "abc".split("", 2).size)
                println("a7=" + "abcdef".split("", 3).size)
                var parts = "abcdef".split("", 3)
                var s = ""
                for (var p in parts) { s = s + p + "|" }
                println("j1=" + s)
                var q = "abc".split("")
                var t = ""
                for (var p in q) { t = t + p + "|" }
                println("j2=" + t)
                println("u1=" + "caf\u00e9".split("").size)
                var u = "caf\u00e9".split("")
                var v = ""
                for (var p in u) { v = v + p + "|" }
                println("u2=" + v)
                var em = "".split("")
                var w = ""
                for (var p in em) { w = w + p + "|" }
                println("e1=" + w)
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "a1=3",
            "a2=1",
            "a3=1",
            "a4=2",
            "a5=1",
            "a6=2",
            "a7=3",
            "j1=a|b|cdef|",
            "j2=a|b|c|",
            "u1=4",
            "u2=c|a|f|\u00e9|",
            "e1=|");

    @Test
    void jvmIsTheContractForSplitEmpty() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnSplitEmpty() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnSplitEmpty() throws Exception {
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
    void nativeX86SplitEmptyMatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("x86");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
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
