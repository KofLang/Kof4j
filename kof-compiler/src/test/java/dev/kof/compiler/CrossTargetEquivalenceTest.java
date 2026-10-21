package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §16 (cross-target equivalence) of
 * {@code docs/development/assembly-optimization-plan.md}: "the same Kof program
 * should produce semantically equivalent results on x86_64/aarch64/riscv64. The
 * assembly does not need to be identical. It is necessary that results,
 * observable effects, ABI, layout, semantics are equivalent."
 *
 * <p>Each program is compiled ONCE and run on every target whose toolchain is
 * present: the JVM is the reference, native x86-64 runs directly, and the riscv64
 * and aarch64 binaries run under qemu. A missing cross toolchain SKIPS that
 * target honestly (an environment condition, never a Kof regression) — the
 * assertions that DO run are always against the JVM reference, so the test
 * cannot pass by comparing nothing.
 *
 * <p>The programs deliberately stress the areas §16 names: observable output,
 * ABI calls, layout arithmetic and the semantics of numeric/overflow edges.
 */
class CrossTargetEquivalenceTest {

    /** Calls, records (layout), loops/branches, integer and float arithmetic. */
    private static final String SRC_MIXED = """
            record Point(Int x, Int y)

            Int add(Int a, Int b) { return a + b }
            Long widen(Long v) { return v * 3L }
            Double scale(Double v) { return v * 2.5 }

            Int abs(Int v) { if (v < 0) { return 0 - v } ; return v }

            main() {
                var p = Point(3, 4)
                println(add(p.x(), p.y()))
                println(widen(1000000000L))
                println(scale(1.5))
                println(abs(-7))
                var total = 0
                for (var i = 1; i <= 5; i++) { total = total + i * i }
                println(total)
                var s = "ab" + "cd"
                println(s.length())
            }
            """;
    private static final String EXPECTED_MIXED = "7\n3000000000\n3.75\n7\n55\n4";

    /** Overflow / wrapping and integer boundaries (semantics §16). */
    private static final String SRC_BOUNDARIES = """
            main() {
                var max = 2147483647
                println(max + 1)
                var min = -2147483648
                println(min - 1)
                println(7 / 2)
                println(-7 / 2)
                println(7 % 3)
                println(-7 % 3)
                println(1L << 40)
                println((255 & 15) | 16)
            }
            """;
    /**
     * MEASURED on the JVM reference (not assumed): {@code max + 1} WRAPS to
     * Int.MIN, while {@code min - 1} evaluates as {@code -2147483649} — the
     * literal {@code -2147483648} is not an Int constant here, so the subtraction
     * is done in Long. Both are Kof semantics (§16 asks for EQUIVALENCE across
     * targets, not for JVM/Java integer rules), so the golden pins what Kof
     * actually does; the cross targets must agree with it.
     */
    private static final String EXPECTED_BOUNDARIES =
            "-2147483648\n-2147483649\n3\n-3\n1\n-1\n1099511627776\n31";

    /** Collections, `switch` and exceptions — the remaining §16 surface. */
    private static final String SRC_COLLECTIONS = """
            String classify(Int n) {
                switch (n) {
                    case 0: return "zero"
                    case 1: return "one"
                    default: return "many"
                }
            }

            main() {
                var xs = listOf(3, 1, 2)
                var m = mapOf("a", 1, "b", 2)
                println(xs.size())
                println(m.get("b"))
                println(classify(0))
                println(classify(1))
                println(classify(9))
                var t = 0
                for (var x in xs) { t = t + x }
                println(t)
                try { throw "boom" } catch (String e) { println("caught:" + e) }
            }
            """;
    private static final String EXPECTED_COLLECTIONS =
            "3\n2\nzero\none\nmany\n6\ncaught:boom";

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean hasTools(String... names) {
        for (String n : names) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + n).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Test
    void theProgramsAreEquivalentOnTheJvmReference(@TempDir Path dir) throws IOException {
        assertEquals(EXPECTED_MIXED, runJvm(dir, "mixed", SRC_MIXED));
        assertEquals(EXPECTED_BOUNDARIES, runJvm(dir, "b", SRC_BOUNDARIES));
        assertEquals(EXPECTED_COLLECTIONS, runJvm(dir, "c", SRC_COLLECTIONS));
    }

    @Test
    void theProgramsAreEquivalentOnNativeX86(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(hasTools("as", "ld"), "native x86-64 toolchain absent");
        assertEquals(EXPECTED_MIXED, runNative(dir, "mixed", SRC_MIXED, Target.NATIVE));
        assertEquals(EXPECTED_BOUNDARIES, runNative(dir, "b", SRC_BOUNDARIES, Target.NATIVE));
        assertEquals(EXPECTED_COLLECTIONS, runNative(dir, "c", SRC_COLLECTIONS, Target.NATIVE));
    }

    @Test
    void theProgramsAreEquivalentOnRiscv64(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(
                hasTools("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (environment, not a Kof regression)");
        assertEquals(EXPECTED_MIXED, runCross(dir, "mixed", SRC_MIXED, "riscv64", Target.NATIVE_RISCV64));
        assertEquals(EXPECTED_BOUNDARIES, runCross(dir, "b", SRC_BOUNDARIES, "riscv64", Target.NATIVE_RISCV64));
        assertEquals(EXPECTED_COLLECTIONS,
                runCross(dir, "c", SRC_COLLECTIONS, "riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void theProgramsAreEquivalentOnAarch64(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(
                hasTools("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (environment, not a Kof regression)");
        assertEquals(EXPECTED_MIXED, runCross(dir, "mixed", SRC_MIXED, "aarch64", Target.NATIVE_AARCH64));
        assertEquals(EXPECTED_BOUNDARIES, runCross(dir, "b", SRC_BOUNDARIES, "aarch64", Target.NATIVE_AARCH64));
        assertEquals(EXPECTED_COLLECTIONS,
                runCross(dir, "c", SRC_COLLECTIONS, "aarch64", Target.NATIVE_AARCH64));
    }

    // ── runners ──────────────────────────────────────────────────────

    private Path compileInto(Path root, String name, String source, Target target) throws IOException {
        Files.createDirectories(root);
        Path src = root.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(src, out, target);
        assertTrue(result.success(), target + " compile (" + name + "): "
                + result.diagnostics().getDiagnostics());
        return out;
    }

    private String runJvm(Path dir, String name, String source) throws IOException {
        Path out = compileInto(dir.resolve("jvm-" + name), name, source, Target.JVM);
        return runToCompletion(new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", out.toString(), "Default.Main"), "jvm");
    }

    private String runNative(Path dir, String name, String source, Target target) throws Exception {
        Path out = compileInto(dir.resolve("x86-" + name), name, source, target);
        return runToCompletion(new ProcessBuilder(out.resolve("Default").resolve("Main").toString()), "x86-64");
    }

    private String runCross(Path dir, String name, String source, String arch, Target target)
            throws Exception {
        Path out = compileInto(dir.resolve(arch + "-" + name), name, source, target);
        Path binary = out.resolve("Default").resolve("Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        return runToCompletion(NativeRiscv64E2ETest.qemu(arch, binary), arch);
    }

    private String runToCompletion(ProcessBuilder pb, String what) throws IOException {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        p.getInputStream().transferTo(sink);
        int exit;
        try {
            exit = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        String output = sink.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, exit, what + " exit code, output: " + output);
        return output;
    }
}
