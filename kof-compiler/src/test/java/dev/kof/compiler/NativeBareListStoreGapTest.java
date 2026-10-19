package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §745: storing a PRIMITIVE into a list whose element type is a BARE
 * (inferred) type parameter compiles on every native target, including the
 * lambda form {@code var b = listOf()} +
 * {@code listOf(1,2,3).forEach((x) -> b.add(x * 2))}, which used to print
 * {@code [?, ?, ?]} on native. The runtime values are pinned by
 * {@code BareCollectionPrimitiveArgE2ETest}. {@code DomainGapParityMatrixTest}
 * still reads this file as a pin file.
 */
class NativeBareListStoreGapTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void addPrimitiveToBareListInsideLambdaCompilesOnNative(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64,
                Target.NATIVE_AARCH64}) {
            Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
            Files.writeString(file, """
                main() {
                    var b = listOf()
                    listOf(1, 2, 3).forEach((x) -> b.add(x * 2))
                    println(b)
                }
                """);
            CompilationResult r = driver.compile(file, tmp.resolve("out-" + System.nanoTime()), t);
            assertTrue(r.success(), () -> t + " must compile the lambda add into a bare list: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void bareListBoundaryIsNotOverRefusedOnNative(@TempDir Path tmp) throws Exception {
        // §745 boundary: an EXPLICIT element type and a REFERENCE element stay
        // accepted on the native targets (only the bare-typed PRIMITIVE store is
        // the hazard) — measured correct on all six targets.
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64,
                Target.NATIVE_AARCH64}) {
            Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
            Files.writeString(file, """
                main() {
                    List<Int> a = listOf()
                    listOf(1, 2, 3).forEach((x) -> a.add(x * 2))
                    println(a)
                    var s = listOf()
                    s.add("x")
                    println(s)
                }
                """);
            CompilationResult r = driver.compile(file, tmp.resolve("out-" + System.nanoTime()), t);
            assertTrue(r.success(), () -> t + " must NOT refuse the explicit/reference forms: "
                    + r.diagnostics().getDiagnostics());
        }
    }
}
