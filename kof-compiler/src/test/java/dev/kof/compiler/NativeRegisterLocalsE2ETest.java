package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D of the assembly-optimization plan ({@code D-CODEGEN-REWRITE}): the
 * register-mapped local model, gated by the {@code kof.native.register.locals}
 * system property (default OFF). This test runs a native program with int locals
 * through the register-local path and asserts the OUTPUT is correct — the
 * correctness proof for the switch-over.
 *
 * <p>The gate is set via {@code System.setProperty} in the test (not the surefire
 * argLine) so the default-OFF production path is exercised by every other native
 * test, and this one proves the ON path.
 */
class NativeRegisterLocalsE2ETest {

    @Test
    void intLocalsInRegistersProduceCorrectOutput(@TempDir Path tempDir) throws IOException {
        String prev = System.getProperty("kof.native.register.locals");
        System.setProperty("kof.native.register.locals", "true");
        try {
            Path source = tempDir.resolve("Main.kf");
            Files.writeString(source, """
                Int add(Int a, Int b) { return a + b }
                Int accum(Int n) {
                    var s = 0
                    for (var i = 0; i < n; i++) { s = s + i }
                    return s
                }
                main() {
                    println(add(3, 4))
                    println(accum(5))
                }
                """);
            Path outDir = tempDir.resolve("out");
            CompilerDriver driver = new CompilerDriver();
            CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
            assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
            Path bin = outDir.resolve("Default/Main");
            assertTrue(Files.exists(bin), "binary exists");
            assertEquals("7\n10", run(bin), "register-mapped int locals compute the right result");
        } finally {
            if (prev == null) System.clearProperty("kof.native.register.locals");
            else System.setProperty("kof.native.register.locals", prev);
        }
    }

    @Test
    void aMethodCallAcrossLiveIntLocalsStaysCorrect(@TempDir Path tempDir) throws IOException {
        // A local live across a call must survive it (callee-saved placement or
        // spill) — the call-awareness path.
        String prev = System.getProperty("kof.native.register.locals");
        System.setProperty("kof.native.register.locals", "true");
        try {
            Path source = tempDir.resolve("Main.kf");
            Files.writeString(source, """
                Int square(Int n) { return n * n }
                main() {
                    var a = 5
                    var b = square(a)
                    var c = a + b
                    println(c)
                }
                """);
            Path outDir = tempDir.resolve("out");
            CompilerDriver driver = new CompilerDriver();
            CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
            assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
            assertEquals("30", run(outDir.resolve("Default/Main")),
                    "a value live across a call is not clobbered");
        } finally {
            if (prev == null) System.clearProperty("kof.native.register.locals");
            else System.setProperty("kof.native.register.locals", prev);
        }
    }

    @Test
    void aReferenceLocalIsSafeInTheFrameAcrossGcPressure(@TempDir Path tempDir) throws IOException {
        // A String local held live across 50000 allocations. References stay in the
        // FRAME (not registers — measured unsafe with try/catch), so this proves the
        // frame path keeps a live reference correct under heavy GC pressure.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var keep = "alive" + "!"
                var n = 0
                for (var i = 0; i < 50000; i++) { var tmp = "x" + i; n = n + tmp.length() }
                println(keep)
                println(n)
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        String out = run(outDir.resolve("Default/Main"));
        assertTrue(out.startsWith("alive!\n"), "the live String survived GC: " + out);
    }

    private static String run(Path bin) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "native exit code, output: " + out);
            return out;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
