package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D-MEM-FFI-CROSS-FULL face 2 (30/09): {@code String[]}→C {@code char**} no
 * JVM (FFM) e nos nativos (x86-64 + cross riscv64/aarch64), com copy-in por
 * chamada. Cada elemento vira um cstr NUL-terminado (o payload da String Kof,
 * offset 24 — o mesmo que o escalar {@code 'S'} passa); {@code null}→0.
 *
 * <p>Oráculo regra 5: o MESMO fonte roda no JVM (FFM) e no binário nativo e a
 * saída é byte-a-byte igual — a fixture C é um {@code .so} real compilado com
 * cc/gcc (host) e com o cross-gcc (alvo). Sem toolchain → skip honesto.
 */
class FfiNativeStringArrayE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            #include <stdlib.h>
            #include <string.h>
            long count(char** xs, long n) { (void) xs; return n; }
            char* join(char** xs, long n) {
                size_t total = 1;
                for (long i = 0; i < n; i++) total += strlen(xs[i]);
                char* out = (char*) malloc(total);
                out[0] = 0;
                for (long i = 0; i < n; i++) strcat(out, xs[i]);
                return out;
            }
            long first_len(char** xs, long n) { return n > 0 ? (long) strlen(xs[0]) : -1; }
            """;

    private static final String KOF = """
            extern "%1$s" count(String[] xs, Long n): Long
            extern "%1$s" join(String[] xs, Long n): String
            extern "%1$s" first_len(String[] xs, Long n): Long

            main() {
                var parts = "aa,bb,cc".split(",")
                println(count(parts, 3))
                println(join(parts, 3))
                println(first_len(parts, 3))
                println(join("x,y".split(","), 2))
                var one = "".split(",")
                println(count(one, 1))
            }
            """;

    private static final String GOLDEN = String.join("\n", "3", "aabbcc", "2", "xy", "1");

    private static String cc(String... cands) {
        for (String c : cands) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) { /* tenta o próximo */ }
        }
        return null;
    }

    private static String buildHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "FFI nativo usa um .so real (Linux + cc)");
        Path src = dir.resolve("libkofstrarr.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofstrarr.so");
        String c = cc("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(c != null, "sem toolchain C (cc/gcc) para a fixture FFI");
        Process p = new ProcessBuilder(c, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "cc falhou: " + out);
        return so.toString();
    }

    private static String compileCrossLib(Path dir, String arch) throws IOException, InterruptedException {
        Path c = dir.resolve("libkofstrarr-" + arch + ".c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofstrarr-" + arch + ".so");
        String cross = arch + "-linux-gnu-gcc";
        assumeTrue(cc(cross) != null, "sem cross-gcc " + cross);
        Process p = new ProcessBuilder(cross, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                cross + " falhou ao compilar a fixture cross: " + out);
        return so.toString();
    }

    private String runNative(Path dir, String kof) throws IOException {
        Path src = dir.resolve("Main-nat.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE String[] extern must bind: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary " + bin + " deve existir");
        try {
            Process p = new ProcessBuilder(bin.toString()).directory(dir.toFile())
                    .redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            assertEquals(0, p.waitFor(), () -> "NATIVE run exit code, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private String runJvmOracle(Path dir, String kof) throws IOException {
        Path src = dir.resolve("Main-jvm.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", out.toString(), "Default.Main")
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            assertEquals(0, p.waitFor(), () -> "JVM run exit code, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void stringArrayBindsOnNativeAndMatchesJvmOracle(@TempDir Path dir) throws Exception {
        String so = buildHostLib(dir);
        String kof = KOF.formatted(so);
        assertEquals(GOLDEN, runJvmOracle(dir, kof),
                "oráculo JVM (FFM char**) deve produzir o golden");
        assertEquals(GOLDEN, runNative(dir, kof),
                "String[] copy-in no Native x86-64 deve concordar byte-a-byte (regra 5)");
    }

    /**
     * D-MEM-FFI-CROSS-FULL face 2 no cross: a MESMA fixture é cross-compilada e
     * o oráculo JVM (FFM) roda o mesmo fonte — saída byte-a-byte igual nas duas
     * archs (regra 5). Sem toolchain cross/qemu → skip honesto.
     */
    @Test
    void stringArrayCrossBindsAndMatchesJvm(@TempDir Path dir) throws Exception {
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchainWithGcc(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            String hostSo = buildHostLib(dir);
            String crossSo = compileCrossLib(dir, arch);

            Path jvmSrc = dir.resolve("ffistrarr-jvm-" + arch + ".kf");
            Files.writeString(jvmSrc, KOF.formatted(hostSo));
            CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-ffistrarr-jvm-" + arch), Target.JVM);
            assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
            assertEquals(GOLDEN, runJvm(dir.resolve("out-ffistrarr-jvm-" + arch)),
                    "oráculo JVM (mesma fixture) deve concordar byte-a-byte (regra 5)");

            Path src = dir.resolve("ffistrarr-" + arch + ".kf");
            Files.writeString(src, KOF.formatted(crossSo));
            Path out = dir.resolve("out-ffistrarr-" + arch);
            CompilationResult rc = driver.compile(src, out, t);
            assertTrue(rc.success(), "D-MEM-FFI-CROSS-FULL face 2: String[] extern must bind on "
                    + arch + ": " + rc.diagnostics().getDiagnostics());
            assertEquals(GOLDEN, runQemu(dir, arch, out.resolve("Default/Main")),
                    "JVM==" + arch + " (String[] cross copy-in)");
        }
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

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            assertEquals(0, p.waitFor(), () -> "JVM run exit code, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
