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
 * known-bugs §746 (JS FACE): the synthetic record {@code toString} rendered a
 * COLLECTION field with JavaScript's native {@code +} coercion, so a
 * {@code List} field printed as {@code xs=2,3} (the JVM/Script/native print
 * {@code xs=[2, 3]}) and a {@code Map} field printed as the useless
 * {@code mm=[object Map]} — a silent wrong output (rule 5), while the other
 * five targets agree with the JVM.
 *
 * <p>Fix: every field value now goes through the existing {@code kofFormat}
 * helper (the JS's own Kof-shaped collection formatter, the same one bare
 * {@code println(list)} uses) — {@code kofFormat} leaves scalars as
 * {@code String(x)}, so a String field stays unquoted and a nested record still
 * renders through its own synthetic {@code toString}.
 *
 * <p>Oracle is the JVM (≡ Script ≡ the three native targets, all measured).
 */
class JsRecordToStringCollectionFieldE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            record Inner(Int a, String b)
            record Outer(Inner in1, List<Int> xs, String tag)
            record Plain(List<String> ss, Map<String, Int> mm)
            main() {
                println(Outer(Inner(1, "x"), listOf(2, 3), "t"))
                println(Plain(listOf("a", "b"), mapOf("k", 1)))
                println(Inner(1, "x"))
                println(listOf(listOf(1, 2)))
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "Outer[in1=Inner[a=1, b=x], xs=[2, 3], tag=t]",
            "Plain[ss=[a, b], mm={k=1}]",
            "Inner[a=1, b=x]",
            "[[1, 2]]");

    @Test
    void jvmIsTheContractForRecordToString() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptRecordToStringMatchesTheJvm() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsRecordToStringMatchesTheJvm() throws Exception {
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
    void nativeX86RecordToStringMatchesTheJvm() throws Exception {
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

    @Test
    void riscv64RecordToStringMatchesTheJvm() throws Exception {
        runCross("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64RecordToStringMatchesTheJvm() throws Exception {
        runCross("aarch64", Target.NATIVE_AARCH64);
    }

    private void runCross(String arch, Target target) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                arch + " cross toolchain/qemu absent");
        Path dir = tmp.resolve(arch);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> arch + " compile: " + r.diagnostics().getDiagnostics());
        String o = NativeRiscv64E2ETest.runQemu(arch, out.resolve("Default/Main"));
        assertEquals(GOLDEN, o.strip());
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
