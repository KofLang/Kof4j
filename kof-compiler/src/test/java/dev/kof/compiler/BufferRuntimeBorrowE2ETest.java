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
 * then blocks until the harness creates the GO file.
 */
class BufferRuntimeBorrowE2ETest {

    private static final String C_SRC = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <unistd.h>

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

            main() {
                var b = buffer.alloc(2)
                var box = listOf(b)
                var h1 = spawn { return barriered_write(box.get(0), 2, 9) }
                var h2 = spawn { return barriered_write(box.get(0), 2, 9) }
                var r1 = 0
                var r2 = 0
                try {
                    r1 = await h1
                } catch (String e) {
                    println(e)
                }
                try {
                    r2 = await h2
                } catch (String e) {
                    println(e)
                }
                println(b.bytes())
            }
            """;

    private static final String KOF_CONTROL = """
            extern "%s" barriered_write(Buffer(U8) buf, Int n, Int v): Int

            main() {
                var b = buffer.alloc(2)
                println(barriered_write(b, 2, 9))
                println(b.bytes())
            }
            """;

    @Test
    void concurrentBufferBorrowRaisesMem020Jvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("borrow-race.kf");
        Files.writeString(src, KOF.formatted(so));
        Path out = dir.resolve("out-jvm-borrow-race");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());

        String output = runJvmWithBarrier(dir, out, "ready-a", "go-a");
        long mem020 = output.lines().filter(l -> l.contains("MEM020")).count();
        assertEquals(1L, mem020, "exactly one concurrent writer must hit MEM020; got:\n" + output);
    }

    @Test
    void singleWritableBorrowStaysCleanJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("borrow-single.kf");
        Files.writeString(src, KOF_CONTROL.formatted(so));
        Path out = dir.resolve("out-jvm-borrow-single");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());

        assertFalse(runJvmWithBarrier(dir, out, "ready-c", "go-c").contains("MEM020"),
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
        Files.writeString(src, KOF_CONTROL.formatted(so));
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
        Files.writeString(src, KOF.formatted(so));
        Path out = dir.resolve("out-native-borrow-race");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "NATIVE compile: " + r.diagnostics().getDiagnostics());

        String output = runBinaryWithBarrier(dir, out.resolve("Default/Main"), "ready-na", "go-na");
        long mem020 = output.lines().filter(l -> l.contains("MEM020")).count();
        assertEquals(1L, mem020, "exactly one concurrent writer must hit MEM020 on x86-64; got:\n" + output);
    }

    @Test
    void singleWritableBorrowStaysCleanNativeX86(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("borrow-single-native.kf");
        Files.writeString(src, KOF_CONTROL.formatted(so));
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
            Files.writeString(src, KOF.formatted(so));
            Path out = dir.resolve("out-borrow-race-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
            String output = runQemuWithBarrier(dir, arch, out.resolve("Default/Main"),
                    "ready-" + arch, "go-" + arch);
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
            Files.writeString(src, KOF_CONTROL.formatted(so));
            Path out = dir.resolve("out-borrow-single-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
            String o = runQemu(dir, arch, out.resolve("Default/Main"));
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
            String o = runQemu(dir, arch, out.resolve("Default/Main"));
            assertFalse(o.contains("MEM020"), arch + ": release must clear the flag; got:\n" + o);
            assertEquals("2\n2\n[8, 0]", o, arch + " golden (second sequential writer succeeds)");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private static String runBinaryWithBarrier(Path dir, Path bin, String readyName, String goName)
            throws IOException, InterruptedException {
        Path ready = dir.resolve(readyName);
        Path go = dir.resolve(goName);
        Files.deleteIfExists(ready);
        Files.deleteIfExists(go);
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        pb.environment().put("KOF_BUF_READY", ready.toString());
        pb.environment().put("KOF_BUF_GO", go.toString());
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
        Files.writeString(go, "go");
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "native must finish after GO");
        reader.join(5000);
        return output.toString().replace("\r\n", "\n").trim();
    }

    private static String runQemu(Path dir, String arch, Path bin) throws IOException, InterruptedException {
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        pb.environment().put("LD_LIBRARY_PATH", dir.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), arch + " must finish");
        assertEquals(0, p.exitValue(), arch + " exit, output: " + o);
        return o;
    }

    private static String runQemuWithBarrier(Path dir, String arch, Path bin,
                                             String readyName, String goName)
            throws IOException, InterruptedException {
        Path ready = dir.resolve(readyName);
        Path go = dir.resolve(goName);
        Files.deleteIfExists(ready);
        Files.deleteIfExists(go);
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        pb.redirectErrorStream(true);
        pb.environment().put("LD_LIBRARY_PATH", dir.toString());
        pb.environment().put("KOF_BUF_READY", ready.toString());
        pb.environment().put("KOF_BUF_GO", go.toString());
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

    private String runJvmWithBarrier(Path dir, Path outDir, String readyName, String goName)
            throws IOException, InterruptedException {
        Path ready = dir.resolve(readyName);
        Path go = dir.resolve(goName);
        Files.deleteIfExists(ready);
        Files.deleteIfExists(go);
        String javaHome = System.getProperty("java.home");
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(javaHome, "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", outDir.toString(),
                "Default.Main");
        pb.redirectErrorStream(true);
        pb.environment().put("KOF_BUF_READY", ready.toString());
        pb.environment().put("KOF_BUF_GO", go.toString());
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
        Files.writeString(go, "go");
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "JVM must finish after GO");
        reader.join(5000);
        return output.toString().replace("\r\n", "\n").trim();
    }
}
