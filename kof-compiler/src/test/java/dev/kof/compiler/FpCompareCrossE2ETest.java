package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §622 — a second/nested conditional assignment to a Double local was lost on
 * the cross natives (riscv64/aarch64) because the cross conditional-jump
 * compared the raw IEEE-754 bit patterns with SIGNED INTEGER branches
 * ({@code bge}/{@code ble}). That ordering is non-monotonic for negatives
 * ({@code -1.0} has a larger signed bit pattern than {@code -2.0}), so a range
 * reduction whose later branch tested a negative double silently kept the old
 * value: {@code t2=2283185} on x86-64 became {@code t2=-4000000} under
 * riscv64/aarch64. The fix loads the operands into the FPU and compares with
 * {@code feq}/{@code flt}/{@code fle}, and the aarch64 translator maps those to
 * the NaN-correct {@code mi}/{@code ls} conditions.
 *
 * <p>The same probe also pinned §625: the Script interpreter compared doubles
 * with {@code Double.compare}, which ORDERS NaN as greater than everything, so
 * {@code NaN > 1.0} returned true while JVM/JS return false. Fixed to IEEE.
 *
 * <p>§627: the value path ({@code a == b} as an expression) and the
 * conditional-jump path ({@code if (a == b)}) diverged. The interpreter's
 * {@code compare} returned {@code false} for EQ/NE on Float/Double, so
 * {@code kof.test}'s {@code assertEqualDouble(1.5, 2.5)} never threw on Script;
 * and the cross cond-jump used {@code feq.d}/{@code flt.d}/{@code fle.d} for
 * Float too — {@code fmv.w.x} NaN-boxes the single into the upper 32 bits, so
 * {@code 2.5 == 2.5} was false on riscv64/aarch64. Both now use the right path
 * ({@code x == y} and {@code feq.s}).
 */
class FpCompareCrossE2ETest extends LambdaSupport {

    /** The original 15-line §622 reproducer (literals only). */
    private static final String TWO_IF = """
            Double twoIfLit(Double v) {
                var r = v % 6.283185307179586
                if (r > 3.141592653589793) { return r - 6.283185307179586 }
                if (r < 0.0 - 3.141592653589793) { return r + 6.283185307179586 }
                return r
            }
            main() {
                println("t=" + ((twoIfLit(17.27875959474586) * 1000000.0) as Int))
                println("t2=" + ((twoIfLit(0.0 - 4.0) * 1000000.0) as Int))
            }
            """;
    private static final String TWO_IF_EXPECTED = "t=-1570796\nt2=2283185";

    /**
     * Every ordered operator on negative doubles (the exact case the integer
     * branches got wrong), plus Float, equality and NaN. {@code b()} prints a
     * Bool as 1/0 so the golden is byte-exact on every target.
     */
    private static final String OPERATORS = """
            Bool dlt(Double a, Double b) { return a < b }
            Bool dgt(Double a, Double b) { return a > b }
            Bool dle(Double a, Double b) { return a <= b }
            Bool dge(Double a, Double b) { return a >= b }
            Bool deq(Double a, Double b) { return a == b }
            Bool dne(Double a, Double b) { return a != b }
            Bool flt(Float a, Float b) { return a < b }
            Int b(Bool v) { if (v) { return 1 } return 0 }
            Void ifEqF(Float a, Float b, String label) {
                if (a == b) { println("EQ " + label) } else { println("NE " + label) }
            }
            Void ifNeD(Double a, Double b, String label) {
                if (a != b) { println("NE " + label) } else { println("EQ " + label) }
            }
            main() {
                println("negneg_lt=" + b(dlt(0.0 - 1.0, 0.0 - 2.0)) + b(dlt(0.0 - 2.0, 0.0 - 1.0)))
                println("negneg_gt=" + b(dgt(0.0 - 1.0, 0.0 - 2.0)) + b(dgt(0.0 - 2.0, 0.0 - 1.0)))
                println("negneg_le=" + b(dle(0.0 - 2.0, 0.0 - 2.0)) + b(dle(0.0 - 1.0, 0.0 - 2.0)))
                println("negneg_ge=" + b(dge(0.0 - 2.0, 0.0 - 2.0)) + b(dge(0.0 - 2.0, 0.0 - 1.0)))
                println("mix_lt=" + b(dlt(0.0 - 2.0, 1.0)) + b(dlt(1.0, 0.0 - 2.0)))
                println("eqne=" + b(deq(1.5, 1.5)) + b(dne(1.5, 1.5)) + b(deq(1.5, 2.5)) + b(dne(1.5, 2.5)))
                println("flt=" + b(flt(0.0 - 3.5, 0.0 - 1.5)) + b(flt(0.0 - 1.5, 0.0 - 3.5)))
                var nan = 0.0 / 0.0
                println("nan=" + b(dlt(nan, 1.0)) + b(dgt(nan, 1.0)) + b(dle(nan, 1.0)) + b(dge(nan, 1.0)) + b(deq(nan, nan)) + b(dne(nan, nan)))
                ifEqF(2.5, 2.5, "same")
                ifEqF(1.5, 2.5, "diff")
                ifNeD(2.5, 2.5, "same")
                ifNeD(1.5, 2.5, "diff")
            }
            """;
    private static final String OPERATORS_EXPECTED = """
            negneg_lt=01
            negneg_gt=10
            negneg_le=10
            negneg_ge=10
            mix_lt=10
            eqne=1001
            flt=10
            nan=000001
            EQ same
            NE diff
            EQ same
            NE diff""";

    @Test
    void twoIfLiteralsJvmScriptAndX86(@TempDir Path t) throws Exception {
        Path s = t.resolve("Main.kf");
        Files.writeString(s, TWO_IF);
        runJvm(s, t.resolve("jvm"), TWO_IF_EXPECTED);
        runScript(s, TWO_IF_EXPECTED);
        runNative(s, t.resolve("x86"), TWO_IF_EXPECTED);
    }

    @Test
    void twoIfLiteralsCross(@TempDir Path t) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain("riscv64")
                        && NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "cross toolchain ausente — pulando (NATIVE002)");
        runCross(t, Target.NATIVE_RISCV64, "riscv64", TWO_IF, TWO_IF_EXPECTED);
        runCross(t, Target.NATIVE_AARCH64, "aarch64", TWO_IF, TWO_IF_EXPECTED);
    }

    @Test
    void everyOrderedOperatorAllTargets(@TempDir Path t) throws Exception {
        Path s = t.resolve("Ops.kf");
        Files.writeString(s, OPERATORS);
        runJvm(s, t.resolve("jvm"), OPERATORS_EXPECTED);
        runScript(s, OPERATORS_EXPECTED);
        runJs(s, t.resolve("js"), OPERATORS_EXPECTED);
        runNative(s, t.resolve("x86"), OPERATORS_EXPECTED);
    }

    @Test
    void everyOrderedOperatorCross(@TempDir Path t) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain("riscv64")
                        && NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "cross toolchain ausente — pulando (NATIVE002)");
        runCross(t, Target.NATIVE_RISCV64, "riscv64", OPERATORS, OPERATORS_EXPECTED);
        runCross(t, Target.NATIVE_AARCH64, "aarch64", OPERATORS, OPERATORS_EXPECTED);
    }

    private void runCross(Path t, Target target, String arch, String source, String expected)
            throws Exception {
        Path s = t.resolve("Main-" + arch + "-" + System.nanoTime() + ".kf");
        Files.writeString(s, source);
        Path out = t.resolve("out-" + arch + "-" + System.nanoTime());
        CompilationResult r = driver.compile(s, out, target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), target + " timeout");
        assertEquals(0, p.exitValue(), target + " exit: " + output);
        assertEquals(expected, output, target + " output");
    }
}
