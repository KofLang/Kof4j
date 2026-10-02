package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §540 — (a) a single large allocation and (b) many live objects on the cross
 * natives (riscv64/aarch64) must behave like JVM/x86-64.
 *
 * <p>Before the fix the cross arena was a static 256 KiB, so `new Int[65536]`
 * OOMed; and beyond ~10 000 live blocks the conservative GC could not find a
 * block (linear scan capped at 10 000) and freed live memory (SIGSEGV). The
 * fixed cross uses a 16 MiB arena plus an O(1) block-start bitmap in the GC.
 *
 * <p>§542 — the x86-64 native had a SEPARATE heap problem: it looked blocks up
 * by a LINEAR scan of the gc list, so marking was O(N) per candidate and the
 * whole program went cubic (1 000 live strings ≈ 17 s, 2 000 ≈ 136 s). §542
 * gives x86 a contiguous mmap arena + the same O(1) block-start bitmap, so the
 * x86 many-live-objects case now completes in seconds; it is asserted here.
 */
class CrossHeapParityE2ETest extends KofStringsSupport {

    private static final String LARGE = """
            main() {
                var b = new Int[65536]
                b[0] = 1
                b[65535] = 2
                println(b[0] + b[65535])
                var c = new Int[262144]
                c[262143] = 7
                println(c.length)
                println(c[262143])
            }
            """;
    private static final String LARGE_EXPECTED = "3\n262144\n7";

    private static final String MANY_LIVE_STRINGS = """
            main() {
                val l = listOf("")
                var i = 0
                while (i < 15000) { l.add("padding " + i + " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"); i = i + 1 }
                println(l.size)
            }
            """;
    private static final String MANY_EXPECTED = "15001";

    private static final String MANY_LIVE_STRINGS_X86 = """
            main() {
                val l = listOf("")
                var i = 0
                while (i < 2000) { l.add("padding " + i + " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"); i = i + 1 }
                println(l.size)
            }
            """;
    private static final String MANY_X86_EXPECTED = "2001";

    private static final String HUGE_X86 = """
            main() {
                var b = new Int[70000000]
                b[0] = 11
                b[69999999] = 22
                println(b[0] + b[69999999])
                println(b.length)
            }
            """;
    private static final String HUGE_X86_EXPECTED = "33\n70000000";

    @Test
    void largeAllocationJvm(@TempDir Path t) throws Exception {
        runJvm(t, LARGE, LARGE_EXPECTED);
    }

    @Test
    void largeAllocationNativeX86(@TempDir Path t) throws Exception {
        runNative(t, LARGE, LARGE_EXPECTED);
    }

    @Test
    void largeAllocationCross(@TempDir Path t) throws Exception {
        assumeToolchain("qemu-riscv64", "qemu-aarch64");
        crossHeapRunQemuExpect(t, Target.NATIVE_RISCV64, "qemu-riscv64", LARGE, LARGE_EXPECTED);
        crossHeapRunQemuExpect(t, Target.NATIVE_AARCH64, "qemu-aarch64", LARGE, LARGE_EXPECTED);
    }

    @Test
    void manyLiveObjectsNativeX86(@TempDir Path t) throws Exception {
        Path file = t.resolve("Main-x86-" + System.nanoTime() + ".kf");
        Files.writeString(file, MANY_LIVE_STRINGS_X86);
        Path outDir = t.resolve("out-x86-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "x86 compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(outDir.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        boolean done = p.waitFor(30, TimeUnit.SECONDS);
        String output = done
                ? new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim() : "";
        if (!done) {
            p.destroyForcibly();
        }
        assertTrue(done, "x86 many-live-objects timed out (GC regression, §542)");
        assertEquals(0, p.exitValue(), "x86 exit, out: " + output);
        assertEquals(MANY_X86_EXPECTED, output, "x86 output");
    }

    @Test
    void hugeAllocationNativeX86(@TempDir Path t) throws Exception {
        // >256 MiB: the §542 arena must not cap the heap at the old 256 MiB
        // value (the pre-§542 x86 heap was an unbounded mmap per allocation).
        Path file = t.resolve("Huge-x86-" + System.nanoTime() + ".kf");
        Files.writeString(file, HUGE_X86);
        Path outDir = t.resolve("huge-x86-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "x86 compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(outDir.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        boolean done = p.waitFor(60, TimeUnit.SECONDS);
        String output = done
                ? new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim() : "";
        if (!done) {
            p.destroyForcibly();
        }
        assertTrue(done, "x86 huge allocation timed out (§542 arena)");
        assertEquals(0, p.exitValue(), "x86 exit, out: " + output);
        assertEquals(HUGE_X86_EXPECTED, output, "x86 output");
    }

    @Test
    void manyLiveObjectsCross(@TempDir Path t) throws Exception {
        assumeToolchain("qemu-riscv64", "qemu-aarch64");
        crossHeapRunQemuExpect(t, Target.NATIVE_RISCV64, "qemu-riscv64", MANY_LIVE_STRINGS, MANY_EXPECTED);
        crossHeapRunQemuExpect(t, Target.NATIVE_AARCH64, "qemu-aarch64", MANY_LIVE_STRINGS, MANY_EXPECTED);
    }

    private void crossHeapRunQemuExpect(Path tempDir, Target target, String qemu, String source,
                               String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), bin).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), target + " timed out");
        assertEquals(0, p.exitValue(), target + " exit, out: " + output);
        assertEquals(expected, output, target + " output");
    }
}
