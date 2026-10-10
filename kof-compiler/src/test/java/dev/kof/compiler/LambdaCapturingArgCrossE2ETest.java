package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §620 — a capturing lambda passed as a function-typed argument must receive
 * its ARGUMENT correctly on the cross natives (riscv64/aarch64).
 *
 * <p>Before the fix the cross prologue assigned the incoming argument registers
 * to the method locals in insertion order ({@code this, capture, param})
 * instead of by IR slot, so the real parameter was read from the wrong
 * register: the capturing lambda printed the CAPTURE as its first argument and
 * the parameter slot held garbage. The x86-64 emitter already skips captures
 * ("bug 9"); the cross emitter did not.
 */
class LambdaCapturingArgCrossE2ETest extends LambdaSupport {

    private static final String CAPTURING_ARG = """
            class Runner {
                run(f: (Int) -> Void) {
                    f(7)
                }
            }

            main() {
                var x = 100
                var r = Runner()
                r.run { dt: Int -> println("dt=" + dt + " x=" + x) }
            }
            """;
    private static final String EXPECTED = "dt=7 x=100";

    /** Two params + a capture: the capture must not shift either argument. */
    private static final String TWO_ARGS_CAPTURE = """
            class Adder {
                run(f: (Int, Int) -> Void) {
                    f(3, 4)
                }
            }

            main() {
                var x = 50
                var a = Adder()
                a.run { p: Int, q: Int -> println("p=" + p + " q=" + q + " x=" + x) }
            }
            """;
    private static final String TWO_ARGS_EXPECTED = "p=3 q=4 x=50";

    @Test
    void jvmAndX86Baseline(@TempDir Path t) throws IOException {
        Path s = t.resolve("Main.kf");
        Files.writeString(s, CAPTURING_ARG);
        runJvm(s, t.resolve("jvm"), EXPECTED);
        runNative(s, t.resolve("x86"), EXPECTED);
    }

    @Test
    void crossRiscv(@TempDir Path t) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "riscv64 toolchain (as/ld/qemu) ausente — pulando (NATIVE002)");
        runCross(t, Target.NATIVE_RISCV64, "riscv64", CAPTURING_ARG, EXPECTED);
    }

    @Test
    void crossAarch64(@TempDir Path t) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "aarch64 toolchain (as/ld/qemu) ausente — pulando (NATIVE002)");
        runCross(t, Target.NATIVE_AARCH64, "aarch64", CAPTURING_ARG, EXPECTED);
    }

    @Test
    void twoArgsCaptureCross(@TempDir Path t) throws Exception {
        Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("riscv64")
                        && NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "cross toolchain ausente — pulando (NATIVE002)");
        runCross(t, Target.NATIVE_RISCV64, "riscv64", TWO_ARGS_CAPTURE, TWO_ARGS_EXPECTED);
        runCross(t, Target.NATIVE_AARCH64, "aarch64", TWO_ARGS_CAPTURE, TWO_ARGS_EXPECTED);
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
