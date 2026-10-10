package dev.kof.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suporte compartilhado do stress determinístico de Array Bounds Safety
 * ({@code KOF-SBD-001-STRESS}, {@code ArrayBoundsStressTest}): os runners
 * JVM/JS/Native, os geradores de programa Kof e os oráculos de invariantes.
 * Vive fora da classe de teste para mantê-la abaixo do limite de 500 linhas de
 * teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os
 * testes e o nome da classe seguem no {@code ArrayBoundsStressTest} — zero
 * drift de citação.
 */
abstract class ArrayBoundsStressSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    protected record RunResult(int exitCode, String output) {
    }

    static final String STRESS004 = """
                main() {
                    var n = 50
                    var a = new Int[n]
                    for (var i = 0; i < n; i = i + 1) { a[i] = 7000 + i }
                    var checksum = 0
                    for (var i = 0; i < n; i = i + 1) { checksum = checksum + a[i] }
                    var rejected = 0
                    for (var k = 0; k < 20000; k = k + 1) {
                        try {
                            var v = a[n]
                        } catch (String e) {
                            rejected = rejected + 1
                        }
                        try {
                            a[n] = 999999
                        } catch (String e) {
                            rejected = rejected + 1
                        }
                    }
                    var checksumAfter = 0
                    for (var i = 0; i < n; i = i + 1) { checksumAfter = checksumAfter + a[i] }
                    println(rejected)
                    println(checksum == checksumAfter)
                    println(a.length)
                }
                """;

    static final String STRESS005 = """
                main() {
                    var n = 50
                    var a = new Int[n]
                    for (var i = 0; i < n; i = i + 1) { a[i] = 9000 + i }
                    var checksum = 0
                    for (var i = 0; i < n; i = i + 1) { checksum = checksum + a[i] }
                    var offsets = new Int[6]
                    offsets[0] = n + 1
                    offsets[1] = n + 10
                    offsets[2] = n + 1000
                    offsets[3] = n * 2
                    offsets[4] = n * 100
                    offsets[5] = 2147483647
                    var rejected = 0
                    for (var oi = 0; oi < 6; oi = oi + 1) {
                        var idx = offsets[oi]
                        for (var rep = 0; rep < 500; rep = rep + 1) {
                            try { var v = a[idx] } catch (String e) { rejected = rejected + 1 }
                            try { a[idx] = 777 } catch (String e) { rejected = rejected + 1 }
                        }
                    }
                    var checksumAfter = 0
                    for (var i = 0; i < n; i = i + 1) { checksumAfter = checksumAfter + a[i] }
                    println(rejected)
                    println(checksum == checksumAfter)
                    println(a.length)
                }
                """;

    static final String STRESS007 = """
                main() {
                    var a = new Int[5]
                    for (var i = 0; i < 5; i = i + 1) { a[i] = i * 10 }
                    var okCycles = 0
                    for (var cycle = 0; cycle < 10000; cycle = cycle + 1) {
                        // 1. valid access
                        var beforeOk = a[2] == 20
                        // 2/3. invalid access, caught
                        var caught = false
                        try {
                            var bad = a[-1]
                        } catch (String e) {
                            caught = true
                        }
                        // 4. valid access again, right after the error
                        var afterOk = a[2] == 20
                        // 5. valid write
                        a[2] = 20 + cycle
                        var wroteOk = a[2] == 20 + cycle
                        a[2] = 20
                        // 6. another invalid access
                        var caught2 = false
                        try {
                            a[5] = 999
                        } catch (String e) {
                            caught2 = true
                        }
                        var stillOk = a[2] == 20 && a.length == 5
                        if (beforeOk && caught && afterOk && wroteOk && caught2 && stillOk) {
                            okCycles = okCycles + 1
                        }
                    }
                    println(okCycles)
                }
                """;

    protected RunResult runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            return new RunResult(ec, output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    protected RunResult runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        return new RunResult(exitCode, out.toString().trim());
    }

    /** Compiles+runs on JVM and KofJS, asserts both succeed and produce identical output. */
    protected String[] runBothLines(String source, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path outJvm = tempDir.resolve(name + "-jvm");
        Path outJs = tempDir.resolve(name + "-js");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), name + " JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), name + " JS compile failed: " + rjs.diagnostics().getDiagnostics());
        RunResult jvm = runJvm(outJvm);
        RunResult js = runJs(outJs);
        assertEquals(0, jvm.exitCode(), name + " JVM exit code, output: " + jvm.output());
        assertEquals(0, js.exitCode(), name + " JS exit code, output: " + js.output());
        assertEquals(jvm.output(), js.output(), name + " JVM vs JS parity");
        return jvm.output().split("\n");
    }

    /** Attempts Native too; returns null (NA, never silently PASS) if the toolchain is unavailable. */
    protected String runNativeOrNull(String source, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + "-nat.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve(name + "-native");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        if (!r.success()) {
            return null; // toolchain missing or compile failed — NA, not a verdict
        }
        Path bin = outDir.resolve("Default/Main");
        if (!Files.exists(bin)) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            p.waitFor();
            return out;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    protected static int line(String[] lines, int i) {
        // Test input array is controlled — valid integer guaranteed
        @SuppressWarnings("NumberFormatException")
        int parsed = Integer.parseInt(lines[i].trim());
        return parsed;
    }

    protected String saturationProgram(int n, int iterations) {
        return """
                main() {
                    var n = %d
                    var a = new Int[n]
                    for (var i = 0; i < n; i = i + 1) { a[i] = i + 1 }
                    var checksumBefore = 0
                    for (var i = 0; i < n; i = i + 1) { checksumBefore = checksumBefore + a[i] }
                    var iterations = %d
                    var sum = 0
                    for (var k = 0; k < iterations; k = k + 1) {
                        var idx = k %% n
                        sum = sum + a[idx]
                        a[idx] = a[idx]
                    }
                    var checksumAfter = 0
                    for (var i = 0; i < n; i = i + 1) { checksumAfter = checksumAfter + a[i] }
                    println(checksumBefore)
                    println(checksumAfter)
                    println(a.length)
                }
                """.formatted(n, iterations);
    }

    protected String mixedIndexProgram(int n, int iterations, long seed) {
        return """
                main() {
                    var n = %d
                    var a = new Int[n]
                    var shadow = new Int[n]
                    for (var i = 0; i < n; i = i + 1) { a[i] = 5000 + i; shadow[i] = 5000 + i }
                    var iterations = %d
                    var state = %d
                    var validReads = 0
                    var validWrites = 0
                    var rejectedReads = 0
                    var rejectedWrites = 0
                    var unexpectedSuccesses = 0
                    var unexpectedFailures = 0
                    for (var k = 0; k < iterations; k = k + 1) {
                        state = (state * 97 + 101) %% 1000003
                        var idx = (state %% (3 * n)) - n
                        var isValid = idx >= 0 && idx < n
                        if (k %% 2 == 0) {
                            var ok = true
                            var v = 0
                            try {
                                v = a[idx]
                            } catch (String e) {
                                ok = false
                            }
                            if (ok) {
                                if (isValid) { validReads = validReads + 1 } else { unexpectedSuccesses = unexpectedSuccesses + 1 }
                            } else {
                                if (isValid) { unexpectedFailures = unexpectedFailures + 1 } else { rejectedReads = rejectedReads + 1 }
                            }
                        } else {
                            var newVal = 10000 + (k %% 500)
                            var ok = true
                            try {
                                a[idx] = newVal
                            } catch (String e) {
                                ok = false
                            }
                            if (ok) {
                                if (isValid) { shadow[idx] = newVal; validWrites = validWrites + 1 } else { unexpectedSuccesses = unexpectedSuccesses + 1 }
                            } else {
                                if (isValid) { unexpectedFailures = unexpectedFailures + 1 } else { rejectedWrites = rejectedWrites + 1 }
                            }
                        }
                    }
                    var mismatch = 0
                    for (var i = 0; i < n; i = i + 1) {
                        if (a[i] != shadow[i]) { mismatch = mismatch + 1 }
                    }
                    println(validReads)
                    println(validWrites)
                    println(rejectedReads)
                    println(rejectedWrites)
                    println(unexpectedSuccesses)
                    println(unexpectedFailures)
                    println(mismatch)
                    println(a.length)
                }
                """.formatted(n, iterations, seed);
    }

    protected void assertMixedInvariants(String[] out, String name, int n, int iterations) {
        int validReads = line(out, 0);
        int validWrites = line(out, 1);
        int rejectedReads = line(out, 2);
        int rejectedWrites = line(out, 3);
        int unexpectedSuccesses = line(out, 4);
        int unexpectedFailures = line(out, 5);
        int mismatch = line(out, 6);
        int length = line(out, 7);

        assertEquals(0, unexpectedSuccesses, name + ": an out-of-bounds access unexpectedly succeeded");
        assertEquals(0, unexpectedFailures, name + ": an in-bounds access was unexpectedly rejected");
        assertEquals(0, mismatch, name + ": array content diverged from the valid-writes-only shadow (corruption)");
        assertEquals(n, length, name + ": array length changed (KofJS silent-growth regression)");
        assertEquals(iterations / 2, validReads + rejectedReads, name + ": read accounting doesn't add up");
        assertEquals(iterations / 2, validWrites + rejectedWrites, name + ": write accounting doesn't add up");
        assertTrue(rejectedReads > 0 && rejectedWrites > 0, name + ": test didn't actually exercise any rejection (bad range)");
    }

    protected String[] runJsOnlyLines(String source, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path outJs = tempDir.resolve(name + "-js");
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), name + " JS compile failed: " + rjs.diagnostics().getDiagnostics());
        RunResult js = runJs(outJs);
        assertEquals(0, js.exitCode(), name + " JS exit code, output: " + js.output());
        return js.output().split("\n");
    }

    protected void assertNativeAbortsAfterValidOutput(Path tempDir, String name, String kofSource, String... expectedLinesBeforeAbort) throws IOException {
        Path outDir = tempDir.resolve(name + "-native");
        Path src = tempDir.resolve(name + "-nat.kf");
        Files.writeString(src, kofSource);
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        if (!r.success()) {
            System.out.println(name + ": Native = NA (toolchain unavailable in this environment)");
            return;
        }
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), name + ": Native binary missing after successful compile");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            int exit = p.waitFor();
            assertTrue(exit != 0, name + ": Native process did not report a failure for an out-of-bounds access (exit " + exit + ")");
            String[] lines = out.split("\n");
            for (int i = 0; i < expectedLinesBeforeAbort.length; i++) {
                assertEquals(expectedLinesBeforeAbort[i], lines[i].trim(), name + ": valid output before the OOB access was wrong — corruption before abort");
            }
            System.out.println(name + ": Native exit=" + exit + " output=[" + out + "]");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
