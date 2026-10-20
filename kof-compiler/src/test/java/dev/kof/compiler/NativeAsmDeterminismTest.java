package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §20 (determinism) of {@code docs/development/assembly-optimization-plan.md}:
 * "given same source, same target, same flags, same compiler version, the result
 * must be reproducible."
 *
 * <p>{@code OptimizerDeterminismTest} checks the OPTIMIZER (same ops in, same ops
 * out). This checks the emitted ARTIFACT: compiling the same source twice, in two
 * separate output directories, must produce a byte-identical executable. That is
 * the end-to-end claim §20 makes, and it also covers the ISA peepholes (§7) and
 * the label counter, which are only visible after emission.
 *
 * <p>x86-64 only: the cross targets need their toolchain to link, and their
 * peepholes are the same deterministic text passes (already covered by their own
 * unit tests). A non-deterministic label counter or hash-ordered set would show
 * up here first.
 */
class NativeAsmDeterminismTest {

    @Test
    void theSameSourceEmitsAByteIdenticalBinary(@TempDir Path dir) throws Exception {
        String src = """
                Int f(Int a, Int b) {
                    var s = 0
                    for (var i = 0; i < 5; i++) { if (i == 2) { continue }; s = s + a * 2 + b }
                    return s
                }
                main() { println(f(1, 2)) }
                """;
        assertByteIdentical(dir, src);
    }

    @Test
    void aBranchHeavyProgramIsAlsoReproducible(@TempDir Path dir) throws Exception {
        String src = """
                Int classify(Int n) {
                    if (n < 0) { return 0 }
                    if (n == 0) { return 1 }
                    if (n < 10) { return 2 }
                    return 3
                }
                main() { println(classify(-5)); println(classify(0)); println(classify(5)); println(classify(50)) }
                """;
        assertByteIdentical(dir, src);
    }

    private static void assertByteIdentical(Path dir, String src) throws Exception {
        byte[] first = null;
        for (int run = 0; run < 2; run++) {
            Path root = dir.resolve("run" + run);
            Files.createDirectories(root);
            Path source = root.resolve("Main.kf");
            Files.writeString(source, src);
            new CompilerDriver().compile(source, root.resolve("out"), Target.NATIVE);
            byte[] binary = Files.readAllBytes(root.resolve("out").resolve("Default").resolve("Main"));
            assertTrue(binary.length > 0, "empty executable");
            if (first != null) {
                assertArrayEquals(first, binary,
                        "§20: two compiles of the same source produced different binaries");
            }
            first = binary;
        }
        assertNotNull(first);
    }
}
