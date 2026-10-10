package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §553 / D-EQ-UNBOUNDED-T (maintainer rule-6, 02/10): `==` on an
 * UNBOUNDED type parameter `T` means STRUCTURAL content equality, matching the
 * concrete `String`/record contract.
 *
 * <p>Before the fix the operands erased to `Object` and `==` lowered to
 * REFERENCE identity on JVM (`if_acmp`)/Script (`a == b`)/Native (pointer
 * compare), while JS compared structurally (`===` on the erased value) — one
 * source, three observable behaviours, and literal caching masked the
 * JVM/Script face (`eq(1,1)` true) while Native had no cache (`eq(200,200)`
 * false). The concrete-type `==` contract is untouched.
 *
 * <p>Golden = the JVM oracle. Every target must match it (parity rule 5).
 */
class GenericEqualityE2ETest {

    static final String PROGRAM = """
            record Point(Int x, Int y)

            Bool eq<T>(T a, T b) { return a == b }
            Bool neq<T>(T a, T b) { return a != b }

            String dyn() {
                var s = "a"
                s += "a"
                return s
            }

            main() {
                println("i1=" + eq(1, 1))
                println("i2=" + eq(200, 200))
                println("i3=" + neq(200, 201))
                println("s1=" + eq("aa", "aa"))
                println("s2=" + eq(dyn(), dyn()))
                println("s3=" + neq(dyn(), "ab"))
                println("r1=" + eq(Point(1, 2), Point(1, 2)))
                println("r2=" + eq(Point(1, 2), Point(1, 3)))
                println("r3=" + neq(Point(1, 2), Point(1, 3)))
            }
            """;

    static final String GOLDEN = """
            i1=true
            i2=true
            i3=true
            s1=true
            s2=true
            s3=true
            r1=true
            r2=false
            r3=true""";

    private final CompilerDriver driver = new CompilerDriver();

    private Path outDirFor(Path tempDir, String name, Target t) {
        return tempDir.resolve("out-" + name + "-" + t);
    }

    private String runJvm(Path tempDir, String name) throws Exception {
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                outDirFor(tempDir, name, Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run must exit 0, got:\n" + out);
        return out;
    }

    @Test
    void unboundedEqualityIsContentOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GE.kf");
        Files.writeString(src, PROGRAM);
        CompilationResult r = driver.compile(src, outDirFor(tempDir, "GE", Target.JVM), Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, runJvm(tempDir, "GE"),
                "§553: == on unbounded T must be structural content equality");
    }

    @Test
    void unboundedEqualityIsContentOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GJ.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outGJ");
        CompilationResult r = driver.compile(src, outDir, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals(GOLDEN, txt, "js must match the JVM oracle (parity rule 5)");
    }

    @Test
    void unboundedEqualityIsContentOnScript(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GS.kf");
        Files.writeString(src, PROGRAM);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals(GOLDEN, ir.stdout().replace("\r\n", "\n").trim(),
                "script must match the JVM oracle (parity rule 5)");
    }

    @Test
    void unboundedEqualityIsContentOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GN.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outGN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "native x86_64 must match the JVM oracle (rule 5)");
    }

    private void genericCross(Path tempDir, String name, Target target, String arch) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                "cross " + arch + " + qemu ausente — pulando");
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("out-" + name);
        CompilationResult r = driver.compile(src, outDir, target);
        assertTrue(r.success(), name + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), name + " binary should exist");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
        String out = NativeRiscv64E2ETest.runBounded(p, arch + " generic-eq probe")
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.exitValue(), name + " run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, name + " must match the JVM oracle (parity rule 5)");
    }

    @Test
    void unboundedEqualityIsContentOnNativeRiscv64(@TempDir Path tempDir) throws Exception {
        genericCross(tempDir, "GRV", Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void unboundedEqualityIsContentOnNativeAarch64(@TempDir Path tempDir) throws Exception {
        genericCross(tempDir, "GAR", Target.NATIVE_AARCH64, "aarch64");
    }

    // --- null-safe boundary: T? == T? ---

    static final String NULL_PROGRAM = """
            Bool eq<T>(T? a, T? b) { return a == b }

            main() {
                var m = mapOf("k", "v")
                var miss1: String? = m.get("z")
                var miss2: String? = m.get("w")
                var hit: String? = m.get("k")
                println("n1=" + eq(miss1, miss2))
                println("n2=" + eq(miss1, hit))
                println("n3=" + eq(hit, hit))
            }
            """;

    static final String NULL_GOLDEN = """
            n1=true
            n2=false
            n3=true""";

    @Test
    void unboundedEqualityNullSafeOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GNJ.kf");
        Files.writeString(src, NULL_PROGRAM);
        CompilationResult r = driver.compile(src, outDirFor(tempDir, "GNJ", Target.JVM), Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        assertEquals(NULL_GOLDEN, runJvm(tempDir, "GNJ"),
                "§553: null-safe Objects.equals semantics (null==null true, one null false)");
    }

    @Test
    void unboundedEqualityNullSafeOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GNJS.kf");
        Files.writeString(src, NULL_PROGRAM);
        Path outDir = tempDir.resolve("outGNJS");
        CompilationResult r = driver.compile(src, outDir, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals(NULL_GOLDEN, txt, "js must match the JVM oracle (parity rule 5)");
    }

    @Test
    void unboundedEqualityNullSafeOnScript(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GNSS.kf");
        Files.writeString(src, NULL_PROGRAM);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals(NULL_GOLDEN, ir.stdout().replace("\r\n", "\n").trim(),
                "script must match the JVM oracle (parity rule 5)");
    }

    @Test
    void unboundedEqualityNullSafeOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GNSN.kf");
        Files.writeString(src, NULL_PROGRAM);
        Path outDir = tempDir.resolve("outGNSN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + out);
        assertEquals(NULL_GOLDEN, out, "native x86_64 must match the JVM oracle (rule 5)");
    }

    // --- kof.test generic pair (unblocked by D-EQ-UNBOUNDED-T) ---

    static final String ASSERT_PROGRAM = """
            import kof.test
            record Point(Int x, Int y)
            main() {
                assertEqual(1, 1, "int eq")
                assertEqual("a", "a", "str eq")
                assertEqual(Point(1, 2), Point(1, 2), "rec eq")
                assertNotEqual(1, 2, "int neq")
                assertNotEqual(Point(1, 2), Point(1, 3), "rec neq")
                println("ok")
            }
            """;

    @Test
    void genericAssertPairWorksOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GA.kf");
        Files.writeString(src, ASSERT_PROGRAM);
        CompilationResult r = driver.compile(src, outDirFor(tempDir, "GA", Target.JVM), Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        assertEquals("ok", runJvm(tempDir, "GA"), "kof.test assertEqual<T>/assertNotEqual<T>");
    }

    @Test
    void genericAssertPairWorksOnScript(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GAS.kf");
        Files.writeString(src, ASSERT_PROGRAM);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals("ok", ir.stdout().trim(), "script must match the JVM oracle (parity rule 5)");
    }

    @Test
    void genericAssertPairWorksOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GAJ.kf");
        Files.writeString(src, ASSERT_PROGRAM);
        Path outDir = tempDir.resolve("outGAJ");
        CompilationResult r = driver.compile(src, outDir, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals("ok", txt, "js must match the JVM oracle (parity rule 5)");
    }

    @Test
    void genericAssertPairWorksOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("GAN.kf");
        Files.writeString(src, ASSERT_PROGRAM);
        Path outDir = tempDir.resolve("outGAN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + out);
        assertEquals("ok", out, "native x86_64 must match the JVM oracle (rule 5)");
    }

    @Test
    void genericAssertPairFailsOnMismatch(@TempDir Path tempDir) throws Exception {
        String program = """
                import kof.test
                main() {
                    try {
                        assertEqual(1, 2, "boom")
                    } catch (String e) {
                        println("caught")
                    }
                }
                """;
        Path src = tempDir.resolve("GAF.kf");
        Files.writeString(src, program);
        CompilationResult r = driver.compile(src, outDirFor(tempDir, "GAF", Target.JVM), Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        assertEquals("caught", runJvm(tempDir, "GAF"), "assertEqual must throw on a real mismatch");
    }

    // --- concrete-type == is UNCHANGED (reference for classes, content for String/record) ---

    @Test
    void concreteEqualityUnaffected(@TempDir Path tempDir) throws Exception {
        String program = """
                class Box { Int v }
                record Point(Int x, Int y)
                main() {
                    var a = new Box()
                    var b = new Box()
                    println("ref=" + (a == b))
                    println("str=" + ("aa" == "aa"))
                    println("rec=" + (Point(1, 2) == Point(1, 2)))
                }
                """;
        Path src = tempDir.resolve("GC.kf");
        Files.writeString(src, program);
        CompilationResult r = driver.compile(src, outDirFor(tempDir, "GC", Target.JVM), Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        assertEquals("ref=false\nstr=true\nrec=true", runJvm(tempDir, "GC"),
                "concrete == keeps its frozen contract (class=identity, String/record=content)");
    }
}
