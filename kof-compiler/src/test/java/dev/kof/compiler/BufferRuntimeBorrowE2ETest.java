package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * B-03 runtime half (D-MEM030-BORROW-RUNTIME, 30/09): the writable-borrow state
 * on {@code Buffer(U8)}. The compile face ({@code MEM020} over the
 * {@code OwnershipPass}) is landed ({@code D-MEM020-COMPILE}); this pins the
 * RUNTIME face — an {@code extern} INOUT write acquires an exclusive borrow and
 * a second concurrent acquisition raises {@code MEM020}.
 *
 * The C fixture fences the first writer inside the call so the race is
 * deterministic: {@code barriered_write} writes the buffer, drops a READY file,
 * then blocks until the harness creates the GO file. The borrow acquire runs at
 * the FFI call site, so a worker's PRE-call mark does not prove it reached the
 * acquire. Instead each worker marks BEFORE the call (1/2) and the LOSER of the
 * race marks a THIRD time (3) from its catch — thrown by the failed acquire,
 * before it can enter the fenced C. The harness holds GO until READY + marks 1,
 * 2 and 3 exist, so exactly one MEM020 always fires while the winner still
 * holds the borrow, with no time-based wait (the 0.6.10 CI 300ms sleep became
 * these marks).
 */
class BufferRuntimeBorrowE2ETest {

    private static final String C_SRC = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <unistd.h>

            int barrier_mark(int n) {
                const char* at = getenv("KOF_BUF_ATTEMPT");
                if (at) { FILE* f = fopen(at, "a"); if (f) { fprintf(f, "%d\\n", n); fclose(f); } }
                return n;
            }

            int barriered_write(unsigned char* buf, int n, int v) {
                if (buf && n > 0) buf[0] = (unsigned char) v;
                const char* ready = getenv("KOF_BUF_READY");
                const char* go = getenv("KOF_BUF_GO");
                if (ready) { FILE* f = fopen(ready, "w"); if (f) fclose(f); }
                if (go) { while (access(go, F_OK) != 0) usleep(2000); }
                return n;
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    // The buffer reaches the write through a list: the strict compile pass
    // (OwnershipPass) tracks direct Buffer arguments only, so this is exactly
    // the aliasing the RUNTIME face must catch.
    private static final String KOF = """
            extern "%s" barriered_write(Buffer(U8) buf, Int n, Int v): Int
            extern "%s" barrier_mark(Int n): Int

            main() {
                var b = buffer.alloc(2)
                var box = listOf(b)
                var h1 = spawn {
                    barrier_mark(1)
                    var v = -1
                    try {
                        v = barriered_write(box.get(0), 2, 9)
                    } catch (String e) {
                        barrier_mark(3)
                        println(e)
                    }
                    return v
                }
                var h2 = spawn {
                    barrier_mark(2)
                    var v = -1
                    try {
                        v = barriered_write(box.get(0), 2, 9)
                    } catch (String e) {
                        barrier_mark(3)
                        println(e)
                    }
                    return v
                }
                var r1 = await h1
                var r2 = await h2
                println(b.bytes())
            }
            """;

    private static final String KOF_CONTROL = """
            extern "%s" barriered_write(Buffer(U8) buf, Int n, Int v): Int
            extern "%s" barrier_mark(Int n): Int

            main() {
                var b = buffer.alloc(2)
                barrier_mark(1)
                println(barriered_write(b, 2, 9))
                println(b.bytes())
            }
            """;

    @Test
    void concurrentBufferBorrowRaisesMem020Jvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("borrow-race.kf");
        Files.writeString(src, KOF.formatted(so, so));
        Path out = dir.resolve("out-jvm-borrow-race");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());

        String output = runJvmWithBarrier(dir, out, "ready-a", "go-a", "att-a", 3);
        long mem020 = output.lines().filter(l -> l.contains("MEM020")).count();
        assertEquals(1L, mem020, "exactly one concurrent writer must hit MEM020; got:\n" + output);
    }

    @Test
    void singleWritableBorrowStaysCleanJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("borrow-single.kf");
        Files.writeString(src, KOF_CONTROL.formatted(so, so));
        Path out = dir.resolve("out-jvm-borrow-single");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());

        assertFalse(runJvmWithBarrier(dir, out, "ready-c", "go-c", "att-c", 1).contains("MEM020"),
                "a single writer must never raise MEM020");
    }

    @Test
    void singleWritableBorrowStaysCleanJs(@TempDir Path dir) throws Exception {
        // D-MEM030-BORROW-RUNTIME: the JS face carries the borrow primitive for
        // uniformity, but JS spawn is cooperative (async/Promise), so a
        // concurrent negative case is structurally unreachable there. Proven:
        // the exactly-once acquire/release around a single extern writer stays
        // clean and the copy-back golden matches the JVM.
        String so = compileHostLib(dir);
        Path src = dir.resolve("borrow-single-js.kf");
        Files.writeString(src, KOF_CONTROL.formatted(so, so));
        Path out = dir.resolve("out-js-borrow-single");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        String js = runJs(out);
        assertFalse(js.contains("MEM020"), "single writer must never raise MEM020 on JS");
        assertEquals("2\n[9, 0]", js, "JS golden (borrow acquire/release + copy-back)");
    }

    @Test
    void concurrentBufferBorrowRaisesMem020NativeX86(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("borrow-race-native.kf");
        Files.writeString(src, KOF.formatted(so, so));
        Path out = dir.resolve("out-native-borrow-race");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "NATIVE compile: " + r.diagnostics().getDiagnostics());

        String output = runBinaryWithBarrier(dir, out.resolve("Default/Main"), "ready-na", "go-na", "att-na", 3);
        long mem020 = output.lines().filter(l -> l.contains("MEM020")).count();
        assertEquals(1L, mem020, "exactly one concurrent writer must hit MEM020 on x86-64; got:\n" + output);
    }

    @Test
    void singleWritableBorrowStaysCleanNativeX86(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("borrow-single-native.kf");
        Files.writeString(src, KOF_CONTROL.formatted(so, so));
        Path out = dir.resolve("out-native-borrow-single");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "NATIVE compile: " + r.diagnostics().getDiagnostics());
        String o = runBinary(out.resolve("Default/Main"));
        assertFalse(o.contains("MEM020"), "single writer must never raise MEM020 on x86-64; got:\n" + o);
        assertEquals("2\n[9, 0]", o, "x86-64 golden (borrow acquire/release + payload untouched)");
    }

    @Test
    void concurrentBufferBorrowRaisesMem020Cross(@TempDir Path dir) throws Exception {
        // D-MEM030-BORROW-RUNTIME: cross riscv64/aarch64 são preemptivos
        // (pthread), então a corrida negativa é real aqui; o barriered_write C
        // torna-a determinística sob qemu. Desbloqueado por §545 (spawn ×
        // extern: o worker de clone cru agora ganha tp via _dl_allocate_tls).
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchainWithGcc(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            String so = compileCrossLib(dir, arch);
            Path src = dir.resolve("borrow-race-" + arch + ".kf");
            Files.writeString(src, KOF.formatted(so, so));
            Path out = dir.resolve("out-borrow-race-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
            String output = runQemuWithBarrier(dir, arch, out.resolve("Default/Main"),
                    "ready-" + arch, "go-" + arch, "att-" + arch, 3);
            long mem020 = output.lines().filter(l -> l.contains("MEM020")).count();
            assertEquals(1L, mem020, arch + ": exactly one concurrent writer must hit MEM020; got:\n" + output);
        }
    }

    @Test
    void singleWritableBorrowStaysCleanCross(@TempDir Path dir) throws Exception {
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchainWithGcc(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            String so = compileCrossLib(dir, arch);
            Path src = dir.resolve("borrow-single-" + arch + ".kf");
            Files.writeString(src, KOF_CONTROL.formatted(so, so));
            Path out = dir.resolve("out-borrow-single-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
            String o = NativeRiscv64E2ETest.runQemuWithLibPath(dir, arch, out.resolve("Default/Main"));
            assertFalse(o.contains("MEM020"), arch + ": single writer must never raise MEM020; got:\n" + o);
            assertEquals("2\n[9, 0]", o, arch + " golden (borrow acquire/release + payload untouched)");
        }
    }

    @Test
    void sequentialWritersReleaseBorrowCross(@TempDir Path dir) throws Exception {
        // O release precisa LIMPAR o flag após a downcall: dois writers
        // SEQUENCIAIS (não concorrentes) não podem colidir. Pega o bug em que o
        // release relia o OBJ do bloco de args — o C sobrescrevia o bloco, o
        // release lia ponteiro-lixo e o flag vazava (o 2º acquire acharia o
        // borrow preso → MEM020 espúrio).
        String kof = """
                extern "%s" barriered_write(Buffer(U8) buf, Int n, Int v): Int

                main() {
                    var b = buffer.alloc(2)
                    println(barriered_write(b, 2, 9))
                    println(barriered_write(b, 2, 8))
                    println(b.bytes())
                }
                """;
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchainWithGcc(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            String so = compileCrossLib(dir, arch);
            Path src = dir.resolve("borrow-seq-" + arch + ".kf");
            Files.writeString(src, kof.formatted(so));
            Path out = dir.resolve("out-borrow-seq-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
            String o = NativeRiscv64E2ETest.runQemuWithLibPath(dir, arch, out.resolve("Default/Main"));
            assertFalse(o.contains("MEM020"), arch + ": release must clear the flag; got:\n" + o);
            assertEquals("2\n2\n[8, 0]", o, arch + " golden (second sequential writer succeeds)");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static int countMarks(Path attempt) throws IOException {
        if (Files.notExists(attempt)) return 0;
        return Files.readAllLines(attempt).size();
    }

    private static String compileHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "buffer host lib usa um .so nativo (Linux)");
        Path c = dir.resolve("libkofborrow.c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofborrow.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de borrow");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou ao compilar o host de borrow: " + out);
        return so.toString();
    }

    private static String runBinary(Path bin) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "native binary must finish");
        assertEquals(0, p.exitValue(), "native exit, output: " + o);
        return o;
    }

    private static String runBinaryWithBarrier(Path dir, Path bin, String readyName, String goName,
            String attemptName, int expectMarks)
            throws IOException, InterruptedException {
        Path ready = dir.resolve(readyName);
        Path go = dir.resolve(goName);
        Path attempt = dir.resolve(attemptName);
        Files.deleteIfExists(ready);
        Files.deleteIfExists(go);
        Files.deleteIfExists(attempt);
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        pb.environment().put("KOF_BUF_READY", ready.toString());
        pb.environment().put("KOF_BUF_GO", go.toString());
        pb.environment().put("KOF_BUF_ATTEMPT", attempt.toString());
        Process p = pb.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try {
                output.append(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        });
        reader.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (Files.notExists(ready)) {
            if (!p.isAlive()) {
                reader.join(1000);
                fail("native exited before the barrier; output:\n" + output);
            }
            if (System.nanoTime() > deadline) {
                p.destroyForcibly();
                fail("timeout waiting for the barrier READY file");
            }
            TestServerFixture.awaitTrue(1, 5, () -> Files.exists(ready));
        }
        // Determinismo sem espera por tempo: o GO só sai depois que READY e as
        // marcas 1, 2 e 3 existem. A marca 3 vem do PERDEDOR da corrida, no seu
        // catch — o acquire falhou (MEM020) antes de entrar no C cercado. Como o
        // vencedor só solta o empréstimo depois do GO (bloqueado no C), o
        // perdedor sempre tenta adquirir enquanto o vencedor o detém: exatamente
        // um MEM020, sempre.
        long marksDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (countMarks(attempt) < expectMarks) {
            if (!p.isAlive()) {
                reader.join(1000);
                fail("native exited before the attempt marks; output:\n" + output);
            }
            if (System.nanoTime() > marksDeadline) {
                p.destroyForcibly();
                fail("timeout waiting for " + expectMarks + " attempt marks");
            }
            TestServerFixture.awaitTrue(1, 5, () -> countMarks(attempt) >= expectMarks);
        }
        Files.writeString(go, "go");
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "native must finish after GO");
        reader.join(5000);
        return output.toString().replace("\r\n", "\n").trim();
    }

    private static String runQemuWithBarrier(Path dir, String arch, Path bin,
                                             String readyName, String goName,
                                             String attemptName, int expectMarks)
            throws IOException, InterruptedException {
        Path ready = dir.resolve(readyName);
        Path go = dir.resolve(goName);
        Path attempt = dir.resolve(attemptName);
        Files.deleteIfExists(ready);
        Files.deleteIfExists(go);
        Files.deleteIfExists(attempt);
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        pb.redirectErrorStream(true);
        pb.environment().put("LD_LIBRARY_PATH", dir.toString());
        pb.environment().put("KOF_BUF_READY", ready.toString());
        pb.environment().put("KOF_BUF_GO", go.toString());
        pb.environment().put("KOF_BUF_ATTEMPT", attempt.toString());
        Process p = pb.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try {
                output.append(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        });
        reader.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (Files.notExists(ready)) {
            if (!p.isAlive()) {
                reader.join(2000);
                fail(arch + " exited before the barrier; exit=" + p.exitValue()
                        + " output:\n" + output);
            }
            if (System.nanoTime() > deadline) {
                p.destroyForcibly();
                fail(arch + ": timeout waiting for the barrier READY file");
            }
            TestServerFixture.awaitTrue(1, 5, () -> Files.exists(ready));
        }
        long marksDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (countMarks(attempt) < expectMarks) {
            if (!p.isAlive()) {
                reader.join(2000);
                fail(arch + " exited before the attempt marks; exit=" + p.exitValue()
                        + " output:\n" + output);
            }
            if (System.nanoTime() > marksDeadline) {
                p.destroyForcibly();
                fail(arch + ": timeout waiting for " + expectMarks + " attempt marks");
            }
            TestServerFixture.awaitTrue(1, 5, () -> countMarks(attempt) >= expectMarks);
        }
        Files.writeString(go, "go");
        assertTrue(p.waitFor(90, TimeUnit.SECONDS), arch + " must finish after GO");
        reader.join(5000);
        return output.toString().replace("\r\n", "\n").trim();
    }

    private static String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit code, output: " + out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    private static String compileCrossLib(Path dir, String arch) throws IOException, InterruptedException {
        Path c = dir.resolve("libkofborrow-" + arch + ".c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofborrow-" + arch + ".so");
        String cc = arch + "-linux-gnu-gcc";
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                cc + " falhou ao compilar o host cross: " + out);
        return so.toString();
    }

    private static String firstPresent(String... candidates) {
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {
                // tenta o próximo candidato
            }
        }
        return null;
    }

    private String runJvmWithBarrier(Path dir, Path outDir, String readyName, String goName,
            String attemptName, int expectMarks)
            throws IOException, InterruptedException {
        Path ready = dir.resolve(readyName);
        Path go = dir.resolve(goName);
        Path attempt = dir.resolve(attemptName);
        Files.deleteIfExists(ready);
        Files.deleteIfExists(go);
        Files.deleteIfExists(attempt);
        String javaHome = System.getProperty("java.home");
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(javaHome, "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", outDir.toString(),
                "Default.Main");
        pb.redirectErrorStream(true);
        pb.environment().put("KOF_BUF_READY", ready.toString());
        pb.environment().put("KOF_BUF_GO", go.toString());
        pb.environment().put("KOF_BUF_ATTEMPT", attempt.toString());
        Process p = pb.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try {
                output.append(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        });
        reader.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (Files.notExists(ready)) {
            if (!p.isAlive()) {
                reader.join(1000);
                fail("JVM exited before the barrier was reached; output:\n" + output);
            }
            if (System.nanoTime() > deadline) {
                p.destroyForcibly();
                fail("timeout waiting for the barrier READY file");
            }
            TestServerFixture.awaitTrue(1, 5, () -> Files.exists(ready));
        }
        long marksDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (countMarks(attempt) < expectMarks) {
            if (!p.isAlive()) {
                reader.join(1000);
                fail("JVM exited before the attempt marks; output:\n" + output);
            }
            if (System.nanoTime() > marksDeadline) {
                p.destroyForcibly();
                fail("timeout waiting for " + expectMarks + " attempt marks");
            }
            TestServerFixture.awaitTrue(1, 5, () -> countMarks(attempt) >= expectMarks);
        }
        Files.writeString(go, "go");
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "JVM must finish after GO");
        reader.join(5000);
        return output.toString().replace("\r\n", "\n").trim();
    }
}
