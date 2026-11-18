package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §767 — the cross map grow died at the FIRST grow (16→17 keys)
 * with {@code Runtime error: out of memory}.
 *
 * <p>THE ROOT (measured 18/11, this fix): the grow block in
 * {@code NativeRiscvAsmMapset0} computed the two allocation sizes in
 * {@code t2}/{@code t3}, then called {@code kof_copy_alloc} — whose
 * {@code kof_alloc} clobbers the CALLER-SAVED {@code t2}/{@code t3}. The
 * SECOND array's {@code kof_copy_alloc} therefore received garbage sizes
 * ({@code alloc_bytes} = whatever {@code kof_alloc} left in {@code a1}'s
 * source register), the bump walked past {@code _kof_heap_end}, the one-shot
 * {@code collect_now} retry could not recover and the honest OOM panic
 * followed — always at the first grow, on both cross arches, while x86/JVM
 * printed {@code ok 3000}.
 *
 * <p>THE FIX: the grow's values live in the CALLEE-SAVED {@code s3}/{@code s4}
 * (frame 48→64, both saved/restored), and the new {@code cap} is written to
 * the map ONLY after both arrays are in place — so the entry-tick collection
 * never observes a map whose {@code cap} is doubled while its arrays are
 * still the old size.
 *
 * <p>RED-first: pre-fix, {@code n=17} panicked with the OOM message;
 * {@code n=16} was green (the grow is exactly at the boundary).
 */
class CrossMapGrowE2ETest extends NativeCrossSupport {

    /** The grow boundary + a few grows past it. */
    private static final String SRC = """
            main() {
                var m = mapOf()
                for (var i = 0; i < 3000; i++) {
                    m.put("k" + i, i)
                }
                println("ok " + m.size)
                println(m.get("k2999"))
                println(m.get("k16"))
            }
            """;

    private static final String GOLDEN = String.join("\n", "ok 3000", "2999", "16");

    private Path write(Path tmp) throws Exception {
        Path p = tmp.resolve("Main.kf");
        Files.writeString(p, SRC, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void riscv64MapGrowsPastTheFirstBoundary(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("qemu-riscv64"),
                "qemu-riscv64 absent — environmental skip");
        Path out = tmp.resolve("out-riscv");
        CompilationResult r = driver.compile(write(tmp), out, Target.NATIVE_RISCV64);
        assertTrue(r.success(), () -> "riscv64 compile: " + r.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, runQemu("riscv64", out.resolve("Default/Main"), tmp),
                "§767: the cross map grow must survive past the 16→17 boundary");
    }

    @Test
    void aarch64MapGrowsPastTheFirstBoundary(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("qemu-aarch64"),
                "qemu-aarch64 absent — environmental skip");
        Path out = tmp.resolve("out-aarch");
        CompilationResult r = driver.compile(write(tmp), out, Target.NATIVE_AARCH64);
        assertTrue(r.success(), () -> "aarch64 compile: " + r.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, runQemu("aarch64", out.resolve("Default/Main"), tmp),
                "§767: the cross map grow must survive past the 16→17 boundary");
    }

    private String runQemu(String arch, Path bin, Path tmp) throws Exception {
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, p.waitFor(), () -> arch + " exit, output: " + out);
        return out;
    }
}
