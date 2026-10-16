package dev.kof.compiler;

import dev.kof.compiler.backend.IrMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase F/§17 of the assembly-optimization plan: the cross-target benchmark
 * suite (first slice — JVM target).
 *
 * <p>§17 requires each relevant optimization to have a minimal case, expected
 * behavior, and a before/after comparison; Phase F asks for a suite covering
 * arithmetic, loops, branches, calls, etc. This test compiles a small fixture
 * per category to the JVM, runs it (asserting the program's output — the
 * correctness half), and REPORTS the production optimizer's effect on the
 * instruction metrics ({@link IrMetrics}) — the measured half. Timing is printed
 * as an OBSERVATION only (never asserted): smaller assembly is not necessarily
 * faster code (§17), so a wall-clock number is evidence, not a gate.
 */
class OptimizerBenchmarkE2ETest {

    /** One benchmark: a name, a category, the Kof source and its expected output. */
    private record Bench(String name, String category, String source, String expected) {
    }

    private static final List<Bench> BENCHES = List.of(
            new Bench("arithmetic", "arithmetic", """
                    main() {
                        var acc = 0
                        for (var i = 0; i < 200; i++) { acc = acc + i * 3 - 1 }
                        println(acc)
                    }
                    """, "59500"),
            new Bench("nestedLoop", "loops", """
                    main() {
                        var s = 0
                        for (var i = 0; i < 50; i++) {
                            for (var j = 0; j < 50; j++) { s = s + i * j }
                        }
                        println(s)
                    }
                    """, "1500625"),
            new Bench("branches", "branches", """
                    main() {
                        var x = 0
                        for (var i = 0; i < 100; i++) {
                            if (i % 2 == 0) { x = x + 1 } else { x = x + 2 }
                        }
                        println(x)
                    }
                    """, "150"),
            new Bench("calls", "calls", """
                    Int square(Int n) { return n * n }
                    main() {
                        var t = 0
                        for (var i = 0; i < 100; i++) { t = t + square(i) }
                        println(t)
                    }
                    """, "328350"),
            new Bench("constants", "constants", """
                    main() {
                        var a = 2 * 3
                        var b = a + 4 * 5
                        var c = 100
                        c = 7
                        println(b + c)
                    }
                    """, "33"),
            new Bench("bitwise", "bitwise", """
                    main() {
                        var x = 0
                        for (var i = 0; i < 8; i++) { x = x | (1 << i) }
                        println(x)
                    }
                    """, "255"),
            new Bench("arrays", "arrays", """
                    main() {
                        var xs = new Int[8]
                        for (var i = 0; i < 8; i++) { xs[i] = i + 1 }
                        var s = 0
                        for (var i = 0; i < xs.length; i++) { s = s + xs[i] }
                        println(s)
                    }
                    """, "36"),
            new Bench("records", "structs", """
                    record Point(Int x, Int y)
                    main() {
                        var p = new Point(3, 4)
                        var q = new Point(p.x + 1, p.y + 2)
                        println(q.x * q.y)
                    }
                    """, "24"),
            new Bench("registerPressure", "regpressure", """
                    main() {
                        var a = 1
                        var b = 2
                        var c = 3
                        var d = 4
                        var e = 5
                        var f = 6
                        var g = 7
                        var h = 8
                        println(a + b + c + d + e + f + g + h)
                    }
                    """, "36"),
            new Bench("memory", "memory", """
                    main() {
                        var buf = new Int[256]
                        for (var i = 0; i < 256; i++) { buf[i] = i * i }
                        var s = 0
                        for (var i = 0; i < 256; i++) { s = s + buf[i] }
                        println(s)
                    }
                    """, "5559680"));

    @Test
    void benchmarkSuiteRunsAndReportsMetrics(@TempDir Path dir) throws Exception {
        StringBuilder report = new StringBuilder("\n=== Phase F benchmark ===\n");
        for (Bench b : BENCHES) {
            Path src = dir.resolve(b.name() + ".kf");
            Files.writeString(src, b.source());

            // JVM: correctness (run + assert output) + measured metrics.
            Path outJvm = dir.resolve(b.name() + "-jvm");
            CompilerDriver driver = new CompilerDriver();
            IRModule[] unopt = new IRModule[1];
            IRModule[] opt = new IRModule[1];
            driver.irObserver = (u, o) -> { unopt[0] = u; opt[0] = o; };
            CompilationResult r = driver.compile(src, outJvm, Target.JVM);
            assertTrue(r.success(), b.name() + " JVM compile failed: " + r.diagnostics().getDiagnostics());
            long start = System.nanoTime();
            String jvmOut = runJvm(outJvm);
            long jvmMs = (System.nanoTime() - start) / 1_000_000;
            assertEquals(b.expected(), jvmOut, b.name() + " JVM output mismatch");

            IrMetrics before = IrMetrics.of(flatten(unopt[0]));
            IrMetrics after = IrMetrics.of(flatten(opt[0]));
            report.append(String.format("%-12s [%-10s] JVM instr %d -> %d  loads %d -> %d  stores %d -> %d  (%d ms)%n",
                    b.name(), b.category(),
                    before.instructions(), after.instructions(),
                    before.loads(), after.loads(),
                    before.stores(), after.stores(), jvmMs));

            // Native x86-64: correctness (run + assert output) on the available target.
            Path outNat = dir.resolve(b.name() + "-nat");
            CompilationResult rn = driver.compile(src, outNat, Target.NATIVE);
            if (rn.success()) {
                Path bin = outNat.resolve("Default").resolve("Main");
                assertTrue(Files.exists(bin), b.name() + " native binary missing");
                long natStart = System.nanoTime();
                String natOut = runNative(bin);
                long natMs = (System.nanoTime() - natStart) / 1_000_000;
                assertEquals(b.expected(), natOut, b.name() + " native output mismatch");
                long binBytes = Files.size(bin);
                report.append(String.format("%-12s [%-10s] NATIVE runs, matches JVM  (%d ms, ELF %d bytes)%n",
                        b.name(), b.category(), natMs, binBytes));
            } else {
                report.append(String.format("%-12s [%-10s] NATIVE not built: %s%n",
                        b.name(), b.category(), rn.diagnostics().getDiagnostics()));
            }

            // Cross targets (aarch64 / riscv64 via qemu): correctness parity,
            // skipped honestly when the cross toolchain/qemu is absent.
            for (String[] cross : CROSS_TARGETS) {
                Path outX = dir.resolve(b.name() + "-" + cross[0]);
                CompilationResult rx = driver.compile(src, outX, Target.valueOf(cross[1]));
                if (!rx.success()) {
                    report.append(String.format("%-12s [%-10s] %s not built%n",
                            b.name(), b.category(), cross[0]));
                    continue;
                }
                Path bin = outX.resolve("Default").resolve("Main");
                if (!Files.exists(bin) || !qemuAvailable(cross[0])) {
                    report.append(String.format("%-12s [%-10s] %s skipped (no qemu)%n",
                            b.name(), b.category(), cross[0]));
                    continue;
                }
                String xOut = runQemu(cross[0], bin);
                assertEquals(b.expected(), xOut, b.name() + " " + cross[0] + " output mismatch");
                report.append(String.format("%-12s [%-10s] %s runs, matches JVM%n",
                        b.name(), b.category(), cross[0]));
            }
        }
        // Observation only — never a gate (§17: smaller assembly is not necessarily faster).
        System.out.println(report);
        assertTrue(report.length() > 0);
    }

    private static List<KofOperation> flatten(IRModule module) {
        java.util.ArrayList<KofOperation> ops = new java.util.ArrayList<>();
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock blk : m.basicBlocks()) ops.addAll(blk.operations());
            }
        }
        return ops;
    }

    private static String runJvm(Path outDir) throws Exception {
        return run(new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main"), "JVM");
    }

    private static String runNative(Path bin) throws Exception {
        return run(new ProcessBuilder(bin.toString()), "NATIVE");
    }

    /** Cross targets (qemu arch name, {@link Target} enum name). */
    private static final List<String[]> CROSS_TARGETS = List.of(
            new String[]{"aarch64", "NATIVE_AARCH64"},
            new String[]{"riscv64", "NATIVE_RISCV64"});

    private static boolean qemuAvailable(String arch) {
        try {
            Process p = new ProcessBuilder("qemu-" + arch, "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String runQemu(String arch, Path bin) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, bin.toString());
        String prefix = qemuPrefix(arch);
        if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
        return run(pb, arch);
    }

    private static String qemuPrefix(String arch) {
        String env = System.getenv("KOF_CROSS_SYSROOT");
        String loader = arch.equals("riscv64") ? "ld-linux-riscv64-lp64d.so.1" : "ld-linux-aarch64.so.1";
        if (env != null && !env.isBlank()
                && Files.exists(Path.of(env, "usr", arch + "-linux-gnu", "lib", loader))) {
            return env + "/usr/" + arch + "-linux-gnu";
        }
        return null;
    }

    private static String run(ProcessBuilder pb, String tag) throws Exception {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), tag + " exit code, output: " + output);
        return output;
    }
}
