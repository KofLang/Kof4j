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
 * Conformance lock (test-architecture Phase 6): two collection contracts that a
 * probe round measured correct on all six targets but that NO test pinned, so a
 * silent cross-target regression would go unnoticed. The golden route is
 * currently blocked by the known COBOL-fixture count mismatch, so this is the
 * equivalent cross-target E2E increment.
 *
 * <ol>
 *   <li><b>{@code sorted(comparator)} is STABLE</b> — the JVM's {@code List.sort}
 *       guarantees it, and equal keys must keep their insertion order
 *       ({@code [a(1), b(0), c(1), d(0)]} by key ⇒ {@code b d a c}).</li>
 *   <li><b>Nested generics erase consistently</b> — {@code Map<String,List<Int>>},
 *       {@code List<List<Int>>}, a function over {@code List<List<Int>>} and a
 *       record whose collection fields print the Kof shapes must agree, since a
 *       mis-erased element would show as garbage on the native targets.</li>
 * </ol>
 *
 * <p>Measured identical on JVM/Script/JS/native x86 before being pinned here;
 * the riscv64/aarch64 faces run under qemu. The {@code Map} LOOKUP is written
 * through {@code getOrDefault} because {@code get} returns the nullable
 * {@code T?} and the contract (SEM049) requires narrowing.
 */
class CrossTargetCollectionContractE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            record P(Int k, String v)
            record Holder(Map<String, List<Int>> m, List<List<Int>> l)
            List<Int> firsts(List<List<Int>> xs) { return xs.get(0) }
            main() {
                var ps = listOf(P(1, "a"), P(0, "b"), P(1, "c"), P(0, "d"))
                var sp = ps.sorted((x: P, y: P) -> x.k - y.k)
                var t = ""
                for (var p in sp) { t = t + p.v }
                println("stable=" + t)
                println("desc=" + listOf(3, 1, 2).sorted((a: Int, b: Int) -> b - a))
                println("asc=" + listOf(3, 1, 2).sorted((a: Int, b: Int) -> a - b))
                var m = mapOf("a", listOf(1, 2), "b", listOf(3))
                println("m1=" + m.getOrDefault("a", listOf()))
                println("m2=" + m.getOrDefault("b", listOf()).get(0))
                var l = listOf(listOf(1, 2), listOf(3))
                println("l1=" + l.get(0).get(1))
                println("l2=" + firsts(l))
                var w = ""
                for (var row in l) { w = w + row.size }
                println("l3=" + w)
                println("h1=" + Holder(m, l))
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "stable=bdac",
            "desc=[3, 2, 1]",
            "asc=[1, 2, 3]",
            "m1=[1, 2]",
            "m2=3",
            "l1=2",
            "l2=[1, 2]",
            "l3=21",
            "h1=Holder[m={a=[1, 2], b=[3]}, l=[[1, 2], [3]]]");

    @Test
    void jvmIsTheContractForTheCollectionContracts() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptCollectionContractsMatchTheJvm() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsCollectionContractsMatchTheJvm() throws Exception {
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
    void nativeX86CollectionContractsMatchTheJvm() throws Exception {
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
    void riscv64CollectionContractsMatchTheJvm() throws Exception {
        runCross("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64CollectionContractsMatchTheJvm() throws Exception {
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
