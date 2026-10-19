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
 * known-bugs §749 (SCRIPT FACE): the interpreter leaked its internal
 * representation when an ENUM value was turned into a String — printing
 * {@code Dir.N} or a {@code Dir.valueOf(...)} result gave {@code Dir@3ace6346}
 * (Java's identity {@code toString}) where the JVM, JS and all three native
 * targets print the constant's NAME.
 *
 * <p>An enum is a REAL class (§211) carrying the field {@code kofName} plus a
 * synthetic {@code toString} accessor that returns it ({@code
 * CompilerEnumLowering}), so the other backends were already right; the
 * interpreter's own {@code kofToString} dispatched only records and then fell
 * back to {@code Name@identityHash}.
 *
 * <p>Fix: {@code kofToString} now returns {@code kofName} when the object's
 * class carries it — a precise marker (a normal class has no such field), and
 * the rest of the object path (records, the identity fallback) is untouched.
 *
 * <p>Oracle is the JVM (≡ JS ≡ the three native targets, all measured). The
 * riscv64/aarch64 faces were EXCLUDED at first because {@code Dir.valueOf(String)}
 * SIGSEGVs there (§750); after the §750 gate (static {@code valueOf} on a user
 * owner takes the cross DIRECT call) they join this contract.
 */
class ScriptEnumAsStringE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            enum Dir { N, S, E, W }
            main() {
                println("e1=" + Dir.N)
                println("e7=" + Dir.valueOf("S"))
                println("e2=" + Dir.N.name() + " e3=" + Dir.N.ordinal())
                println("e4=" + (Dir.N == Dir.N) + " e5=" + (Dir.N == Dir.S))
                println("e6=" + Dir.values().size)
                var m = mapOf(Dir.N, 1, Dir.S, 2)
                println("e8=" + m.get(Dir.S) + " e9=" + setOf(Dir.N).contains(Dir.N))
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "e1=N",
            "e7=S",
            "e2=N e3=0",
            "e4=true e5=false",
            "e6=4",
            "e8=2 e9=true");

    @Test
    void jvmIsTheContractForEnumToString() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptEnumToStringMatchesTheJvm() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsEnumToStringMatchesTheJvm() throws Exception {
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
    void nativeX86EnumToStringMatchesTheJvm() throws Exception {
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
    void riscv64EnumToStringMatchesTheJvm() throws Exception {
        runCross("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64EnumToStringMatchesTheJvm() throws Exception {
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
