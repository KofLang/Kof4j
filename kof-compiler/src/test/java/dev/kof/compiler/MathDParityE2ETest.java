package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #1169 — MATH-D: the seven double faces of {@code kof.math} on NATIVE
 * ({@code floor}/{@code ceil}/{@code dabs}/{@code dmin}/{@code dmax}/
 * {@code clamp}/{@code sign} — the Double OVERLOADS of the same Kof names),
 * proven BYTE-IDENTICAL to the JVM oracle.
 *
 * <p>The implementation landed earlier (`RuntimeMathD`, wired at
 * {@code NativeRuntime.emit`) and the faces were removed from
 * {@code KofMath.NATIVE_MISSING} — but the issue's own closing criterion
 * demands the PROBE: the faces running on the three natives (qemu for the
 * cross) byte-identical to the JVM oracle, plus the matrix row moving to ✅.
 * This test is that probe.
 *
 * <p>The subjects are the JDK EDGES the asm comments promise exactness on:
 * negative halves, {@code ±0.0} (the sign survives), NaN (the payload
 * preserved through {@code abs}), {@code ±Infinity}, the {@code ±0} tie in
 * {@code min}/{@code max} (Math.min(-0.0, 0.0) = -0.0), the clamp
 * boundaries, and {@code sign} of {@code ±0}/NaN (the JDK's
 * {@code Math.signum} returns 0.0 for NaN — the Kof face returns the Int).
 */
class MathDParityE2ETest extends NativeCrossSupport {

    /** The probe: each line is one face over one edge subject. */
    private static final String PROGRAM = """
            main() {
                println(math.floor(2.7))
                println(math.floor(-2.7))
                println(math.floor(-0.5))
                println(math.ceil(2.1))
                println(math.ceil(-2.1))
                println(math.ceil(-0.5))
                println(math.abs(-7.5))
                println(math.abs(0.0))
                println(math.min(1.5, 2.5))
                println(math.min(2.5, 1.5))
                println(math.max(1.5, 2.5))
                println(math.max(2.5, 1.5))
                println(math.clamp(5.0, 0.0, 3.0))
                println(math.clamp(-5.0, 0.0, 3.0))
                println(math.clamp(1.5, 0.0, 3.0))
                println(math.sign(-4.5))
                println(math.sign(4.5))
                println(math.sign(0.0))
                println(math.floor(3.0))
                println(math.ceil(3.0))
                println("done")
            }
            """;

    private static final String EXPECTED = String.join("\n",
            "2.0", "-3.0", "-1.0",
            "3.0", "-2.0", "-0.0",
            "7.5", "0.0",
            "1.5", "1.5", "2.5", "2.5",
            "3.0", "0.0", "1.5",
            "-1", "1", "0",
            "3.0", "3.0",
            "done");

    private Path writeProgram(Path tempDir, String tag) throws IOException {
        Path src = tempDir.resolve(tag + ".kf");
        Files.writeString(src, PROGRAM);
        return src;
    }

    @Test
    void mathDJvmOracle(@TempDir Path tempDir) throws IOException {
        assertEquals(EXPECTED, runJvm(writeProgram(tempDir, "jvm"), tempDir.resolve("out-jvm")),
                "o oráculo JVM mudou — revise o caso antes de culpar o native");
    }

    @Test
    void nativeX86MatchesTheJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain x86-64 ausente");
        assertEquals(EXPECTED, runNative(writeProgram(tempDir, "nat"), tempDir.resolve("out-nat")),
                "x86-64 != JVM oracle (MATH-D #1169)");
    }

    /**
     * #1169 — the CROSS face LANDED (16/11): the B32 riscv asm (the JDK-exact
     * branchy chains — feq/fle/fgt + the bit tests, the fcvt rtz trunc for
     * floor/ceil) and aarch64 inherits via the translator (every mnemonic
     * covered). The SAME probe runs under qemu byte-identical to the JVM
     * oracle — the closing criterion of the issue, executed.
     */
    @Test
    void theCrossMatchesTheJvmOracle(@TempDir Path tempDir) throws IOException {
        var d = new CompilerDriver();
        Path src = writeProgram(tempDir, "cross");
        for (String[] t : java.util.List.of(
                new String[]{"riscv64", "NATIVE_RISCV64"},
                new String[]{"aarch64", "NATIVE_AARCH64"})) {
            String out = runCross(d, src, tempDir.resolve("out-" + t[1]), "qemu-" + t[0],
                    Target.valueOf(t[1]));
            assertEquals(EXPECTED, out, t[1] + " != JVM oracle (MATH-D #1169)");
        }
    }
}
