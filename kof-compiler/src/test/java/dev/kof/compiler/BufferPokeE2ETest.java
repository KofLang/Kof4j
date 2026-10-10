package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Graphics/gaming slice 3.4c (`D-GRAPHICS-GAMING`, decision F row in
 * {@code D-MAINT-BATCH-0510}, ORDERED 08/10): the poke primitive — the write
 * counterpart of the peek — binds and RUNS on JVM + Native x86-64 + cross
 * riscv64/aarch64 (under qemu).
 *
 * <p>Raw form {@code buffer.poke64(addr, value)} writes 8/4/1 bytes LE at any
 * address; Buffer form {@code buffer.poke64(b, off, value)} writes at a
 * payload offset (the {@code av_*_free} out-params and C-struct setup this
 * slice unblocks). Bounds (Buffer form): negative offset or offset+n beyond
 * the cap → honest trap on every target (never a silent fallback).
 */
class BufferPokeE2ETest implements NativeToolchainAssumptions {

    private final CompilerDriver driver = new CompilerDriver();

    /** strdup("kof12345") copies 9 bytes — the 8-byte poke stays inside. */
    private static final String GOLDEN_RAW = "w8=88\nw64=72340172838076673\nw32=1094861636";

    private static final String GOLDEN_BUF = "b64=72340172838076673\nb8=16";

    private static final String PROGRAM_RAW = """
            extern "%s" strdup(String s): Long

            main() {
                var p = strdup("kof12345")
                buffer.poke8(p, 88)
                println("w8=" + buffer.peek8(p))
                buffer.poke64(p, 72340172838076673)
                println("w64=" + buffer.peek64(p))
                buffer.poke32(p + 4, 1094861636)
                println("w32=" + buffer.peek32(p + 4))
            }
            """;

    private static final String PROGRAM_BUF = """
            main() {
                var b = buffer.alloc(16)
                buffer.poke64(b, 0, 72340172838076673)
                println("b64=" + buffer.peek64(b, 0))
                buffer.poke8(b, 15, 16)
                println("b8=" + buffer.peek8(b, 15))
            }
            """;

    private static final String PROGRAM_BOUNDS = """
            main() {
                var b = buffer.alloc(16)
                buffer.poke64(b, 16, 1)
                println("no-trap")
            }
            """;

    private static String libc() {
        for (String p : new String[]{
                "/lib/x86_64-linux-gnu/libc.so.6", "/lib64/libc.so.6",
                "/usr/lib/x86_64-linux-gnu/libc.so.6"}) {
            if (new File(p).exists()) return p;
        }
        return null;
    }

    @Test
    void pokeRawWritesRealPointerJvm(@TempDir Path tempDir) throws Exception {
        String libc = libc();
        assumeTrue(libc != null, "libc host ausente");
        assertEquals(GOLDEN_RAW, compileAndRun(tempDir, "jvm", Target.JVM,
                PROGRAM_RAW.formatted(libc)), "poke raw form on JVM");
    }

    @Test
    void pokeRawWritesRealPointerNativeX86(@TempDir Path tempDir) throws Exception {
        assumeNativeX86_64();
        String libc = libc();
        assumeTrue(libc != null, "libc host ausente");
        assertEquals(GOLDEN_RAW, compileAndRun(tempDir, "nat", Target.NATIVE,
                PROGRAM_RAW.formatted(libc)), "poke raw form on Native x86-64");
    }

    @Test
    void pokeRawWritesRealPointerRiscv64(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        String libc = crossLibc("riscv64");
        assumeTrue(libc != null, "libc cross riscv64 ausente no sysroot");
        assertEquals(GOLDEN_RAW, compileAndRun(tempDir, "riscv64", Target.NATIVE_RISCV64,
                PROGRAM_RAW.formatted(libc)), "poke raw form on riscv64 under qemu");
    }

    @Test
    void pokeRawWritesRealPointerAarch64(@TempDir Path tempDir) throws Exception {
        assumeNativeAarch64WithSysroot();
        String libc = crossLibc("aarch64");
        assumeTrue(libc != null, "libc cross aarch64 ausente no sysroot");
        assertEquals(GOLDEN_RAW, compileAndRun(tempDir, "aarch64", Target.NATIVE_AARCH64,
                PROGRAM_RAW.formatted(libc)), "poke raw form on aarch64 under qemu");
    }

    @Test
    void pokeBufFormWritesPayloadJvm(@TempDir Path tempDir) throws Exception {
        assertEquals(GOLDEN_BUF, compileAndRun(tempDir, "jvm", Target.JVM, PROGRAM_BUF),
                "poke Buffer form on JVM");
    }

    @Test
    void pokeBufFormWritesPayloadNativeX86(@TempDir Path tempDir) throws Exception {
        assumeNativeX86_64();
        assertEquals(GOLDEN_BUF, compileAndRun(tempDir, "nat", Target.NATIVE, PROGRAM_BUF),
                "poke Buffer form on Native x86-64");
    }

    @Test
    void pokeBufFormWritesPayloadRiscv64(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        assertEquals(GOLDEN_BUF, compileAndRun(tempDir, "riscv64", Target.NATIVE_RISCV64, PROGRAM_BUF),
                "poke Buffer form on riscv64 under qemu");
    }

    @Test
    void pokeBufFormWritesPayloadAarch64(@TempDir Path tempDir) throws Exception {
        assumeNativeAarch64WithSysroot();
        assertEquals(GOLDEN_BUF, compileAndRun(tempDir, "aarch64", Target.NATIVE_AARCH64, PROGRAM_BUF),
                "poke Buffer form on aarch64 under qemu");
    }

    @Test
    void pokeOutOfBoundsTrapsJvm(@TempDir Path tempDir) throws Exception {
        String out = compileAndRunOrTrap(tempDir, "jvm", Target.JVM, PROGRAM_BOUNDS);
        assertTrue(!out.contains("no-trap"),
                "poke OOB must trap (never a silent fallback), got: " + out);
    }

    @Test
    void pokeOutOfBoundsTrapsNativeX86(@TempDir Path tempDir) throws Exception {
        assumeNativeX86_64();
        String out = compileAndRunOrTrap(tempDir, "nat", Target.NATIVE, PROGRAM_BOUNDS);
        assertTrue(!out.contains("no-trap"),
                "poke OOB must trap (never a silent fallback), got: " + out);
    }

    @Test
    void pokeOutOfBoundsTrapsRiscv64(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        String out = compileAndRunOrTrap(tempDir, "riscv64", Target.NATIVE_RISCV64, PROGRAM_BOUNDS);
        assertTrue(!out.contains("no-trap"),
                "poke OOB must trap (never a silent fallback), got: " + out);
    }

    @Test
    void pokeOutOfBoundsTrapsAarch64(@TempDir Path tempDir) throws Exception {
        assumeNativeAarch64WithSysroot();
        String out = compileAndRunOrTrap(tempDir, "aarch64", Target.NATIVE_AARCH64, PROGRAM_BOUNDS);
        assertTrue(!out.contains("no-trap"),
                "poke OOB must trap (never a silent fallback), got: " + out);
    }

    private String compileAndRun(Path tempDir, String tag, Target t, String program)
            throws Exception {
        Path src = tempDir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        Path out = tempDir.resolve("out-" + tag);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), tag + " compile: " + r.diagnostics().getDiagnostics());
        if (t == Target.JVM) {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                    "-cp", out.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            return run(pb);
        }
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist for " + tag);
        ProcessBuilder pb;
        if (t == Target.NATIVE) {
            pb = new ProcessBuilder(bin.toString());
        } else {
            pb = NativeRiscv64E2ETest.qemu(tag, bin);
        }
        pb.redirectErrorStream(true);
        return run(pb);
    }

    private String compileAndRunOrTrap(Path tempDir, String tag, Target t, String program)
            throws Exception {
        Path src = tempDir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        Path out = tempDir.resolve("out-" + tag);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), tag + " compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb;
        if (t == Target.JVM) {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                    "-cp", out.toString(), "Default.Main");
        } else if (t == Target.NATIVE) {
            pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        } else {
            pb = NativeRiscv64E2ETest.qemu(tag, out.resolve("Default/Main"));
        }
        pb.redirectErrorStream(true);
        return runOrTrap(pb, tag);
    }

    private static String runOrTrap(ProcessBuilder pb, String tag) throws IOException {
        Process p = pb.start();
        boolean done = false;
        try {
            done = p.waitFor(180, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        } finally {
            if (!done) p.destroyForcibly();
        }
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(done, tag + ": process did not finish in 180s, output: '" + output + "'");
        assertNotEquals(0, p.exitValue(), tag + ": the bounds trap must exit nonzero, output: '" + output + "'");
        return output;
    }

    private static String run(ProcessBuilder pb) throws IOException {
        Process p = pb.start();
        boolean done = false;
        try {
            done = p.waitFor(180, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        } finally {
            if (!done) p.destroyForcibly();
        }
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(done, "process did not finish in 180s, output: '" + output + "'");
        assertEquals(0, p.exitValue(), "exit code, output: '" + output + "'");
        return output;
    }

    private static String crossLibc(String arch) {
        String sysroot = System.getenv("KOF_CROSS_SYSROOT");
        if (sysroot == null || sysroot.isBlank()) {
            if (new File("/tmp/opencode/x/usr/" + arch + "-linux-gnu/lib/libc.so.6").exists()) {
                sysroot = "/tmp/opencode/x";
            } else {
                return null;
            }
        }
        Path p = Path.of(sysroot, "usr", arch + "-linux-gnu", "lib", "libc.so.6");
        return Files.exists(p) ? p.toString() : null;
    }
}
