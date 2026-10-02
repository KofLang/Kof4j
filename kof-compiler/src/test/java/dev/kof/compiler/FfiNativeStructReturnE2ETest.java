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
 * D-MEM-FFI-CROSS-FULL face 3 (30/09): retorno de {@code record} por MEMÓRIA
 * (sret, campos INTEGER &gt; 16 B) no alvo cross. O ponteiro do resultado
 * diverge por arch — RISC-V LP64 usa {@code a0} (primeiro arg real em
 * {@code a1}); AAPCS64 usa {@code x8} (primeiro arg real ainda {@code x0}) —
 * por isso a emissão é arch-aware (medido 30/09 com cross-gcc -S).
 *
 * <p>Oráculo regra 5: o MESMO fonte roda no JVM (FFM, o Linker usa
 * SegmentAllocator p/ o sret), no x86-64 e nos dois cross; a saída é
 * byte-a-byte igual. A fixture C é um {@code .so} real (host e cross-gcc).
 * Sem toolchain → skip honesto (nunca falso-verde).
 */
class FfiNativeStructReturnE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            typedef struct { long a; long b; long c; } Big;
            Big bigmake(long x) { Big r; r.a = x; r.b = x * 2; r.c = x + 100; return r; }
            """;

    private static final String KOF = """
            record Big(Long a, Long b, Long c)

            extern "%1$s" bigmake(Long x): Big

            main() {
                val g = bigmake(7)
                println(g.a())
                println(g.b())
                println(g.c())
            }
            """;

    private static final String GOLDEN = String.join("\n", "7", "14", "107");

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
        Path src = dir.resolve("libkofbigret.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofbigret.so");
        String c = cc("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(c != null, "sem toolchain C (cc/gcc) para a fixture FFI");
        Process p = new ProcessBuilder(c, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "cc falhou: " + out);
        return so.toString();
    }

    private static String compileCrossLib(Path dir, String arch) throws IOException, InterruptedException {
        Path c = dir.resolve("libkofbigret-" + arch + ".c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofbigret-" + arch + ".so");
        String cross = arch + "-linux-gnu-gcc";
        assumeTrue(cc(cross) != null, "sem cross-gcc " + cross);
        Process p = new ProcessBuilder(cross, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                cross + " falhou ao compilar a fixture cross: " + out);
        return so.toString();
    }

    private String runJvm(Path dir, String kof, String tag) throws IOException, InterruptedException {
        Path src = dir.resolve("bigret-jvm-" + tag + ".kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-bigret-jvm-" + tag);
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", out.toString(), "Default.Main");
        pb.directory(dir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "JVM run exit, output: " + o);
        return o;
    }

    private String runNativeHost(Path dir, String kof) throws IOException {
        Path src = dir.resolve("bigret-nat.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-bigret-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE x86-64 struct-sret extern must bind: "
                + r.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            assertEquals(0, p.waitFor(), () -> "NATIVE x86-64 run exit, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private String runQemu(Path dir, String arch, Target t, String kof) throws IOException, InterruptedException {
        Path src = dir.resolve("bigret-" + arch + ".kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-bigret-" + arch);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), "D-MEM-FFI-CROSS-FULL face 3: struct-sret extern must bind on "
                + arch + ": " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, out.resolve("Default/Main"));
        pb.environment().put("LD_LIBRARY_PATH", dir.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), arch + " must finish");
        assertEquals(0, p.exitValue(), arch + " exit, output: " + o);
        return o;
    }

    @Test
    void structSretBindsOnNativeAndMatchesJvmOracle(@TempDir Path dir) throws Exception {
        String so = buildHostLib(dir);
        String kof = KOF.formatted(so);
        String jvm = runJvm(dir, kof, "host");
        assertEquals(GOLDEN, jvm, "oráculo JVM (FFM SegmentAllocator/sret) deve produzir o golden");
        assertEquals(GOLDEN, runNativeHost(dir, kof),
                "record sret (> 16 B) no Native x86-64 (SysV rdi) deve concordar (regra 5)");
    }

    @Test
    void structSretCrossBindsAndMatchesJvm(@TempDir Path dir) throws Exception {
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            String hostSo = buildHostLib(dir);
            String crossSo = compileCrossLib(dir, arch);
            String jvm = runJvm(dir, KOF.formatted(hostSo), arch);
            assertEquals(GOLDEN, jvm, "oráculo JVM deve concordar byte-a-byte (regra 5)");
            assertEquals(GOLDEN, runQemu(dir, arch, t, KOF.formatted(crossSo)),
                    "JVM==" + arch + " (record sret arch-aware: a0 riscv64 / x8 aarch64)");
        }
    }
}
