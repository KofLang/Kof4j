package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * FFI callbacks/upcalls no Native x86-64 (memory-safety M1, face 4 de
 * {@code D-MEM-FFI-CROSS-FULL}): um {@code extern} com parâmetro de tipo-função
 * recebe um PONTEIRO C para um trampolim gerado que roda o valor-função Kof
 * (vtable[0] = invoke). Primeiro corte: INTEGER-only (Int/Long/Boolean).
 * Contrato síncrono e não-escapante. Oráculo = o MESMO fonte na JVM (FFM).
 */
class FfiNativeCallbackE2ETest {

    private static final String C_SRC = """
            typedef int (*ii)(int,int);
            int kof_cb_add(int a, int b, ii cb) { return cb(a,b); }
            typedef long (*ll)(long,long);
            long kof_cb_addl(long a, long b, ll cb) { return cb(a,b); }
            typedef long (*li)(int);
            long kof_cb_inc(int a, li cb) { return cb(a); }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private static String buildHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "callback host usa um .so nativo (Linux)");
        Path src = dir.resolve("libkofcb.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofcb.so");
        String cc = null;
        for (String cand : new String[] {"/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc"}) {
            try {
                Process p = new ProcessBuilder(cand, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) { cc = cand; break; }
            } catch (Exception ignored) { /* tenta o próximo */ }
        }
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de callback");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou: " + out);
        return so.toString();
    }

    private static final String CALLBACK_KOF = """
            extern "%1$s" kof_cb_add(Int a, Int b, (Int, Int) -> Int cb): Int
            extern "%1$s" kof_cb_addl(Long a, Long b, (Long, Long) -> Long cb): Long
            extern "%1$s" kof_cb_inc(Int a, (Int) -> Long cb): Long

            main() {
                println(kof_cb_add(20, 22, (x: Int, y: Int) -> x + y))
                println(kof_cb_addl(20 as Long, 22 as Long, (a: Long, b: Long) -> a + b))
                println(kof_cb_inc(41, (n: Int) -> n + 1L))
            }
            """;

    @Test
    void nativeCallbacksComputeAcrossIntegerAbis(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = CALLBACK_KOF.formatted(lib);

        Path jvmSrc = dir.resolve("cb-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve("out-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), () -> "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);
        assertEquals("42\n42\n42", jvm, "JVM golden (callbacks inteiros)");

        Path src = dir.resolve("cb-native.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-native");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE callbacks must bind (M1 face 4): "
                + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary " + bin + " deve existir");
        Process p = new ProcessBuilder(bin.toString()).directory(dir.toFile())
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "NATIVE run exit code, output: " + o);
        assertEquals(jvm, o, "JVM↔Native callback parity byte-for-byte (M1 face 4)");
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
