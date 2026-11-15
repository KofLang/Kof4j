package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A fatia COPY-OUT de arrays no CROSS riscv64/aarch64 (o #1156 face — o
 * espelho cross da fatia x86/JVM 15/11): o `extern` com parâmetro `Double[]`/
 * `Int[]` num módulo `ownership borrowed` cruza como o PONTEIRO DO PAYLOAD
 * DIRETO (obj+24; o heap Kof cross não move objetos) — a C escreve no array
 * do chamador e o array ENXERGA depois da chamada. O by-value inline segue
 * copy-in (a cópia protege). Oráculo regra 5: o MESMO fonte roda no JVM,
 * no nativo x86-64 e nos dois cross sob qemu — saídas byte-a-byte iguais.
 * Fixture `.so` real compilada por arch (host cc + cross-gcc); sem
 * toolchain → skip honesto, nunca falso-verde.
 */
class FfiCrossArrayCopyOutE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            void xfill_double(double* arr, int n) {
                for (int i = 0; i < n; i++) arr[i] = (double)(i * 10);
            }
            void xfill_int(int* arr, int n) {
                for (int i = 0; i < n; i++) arr[i] = i + 100;
            }
            """;

    private static final String KOF_SRC = """
            foreign module fixtures {
                library "%s"
                abi c
                ownership borrowed
                extern xfill_double(Double[] arr, Int n): Void
                extern xfill_int(Int[] arr, Int n): Void
            }

            main() {
                println("start")
                var d = new Double[3]
                xfill_double(d, 3)
                println(d[0])
                println(d[1])
                println(d[2])
                var i = new Int[2]
                xfill_int(i, 2)
                println(i[0])
                println(i[1])
                println("done")
            }
            """;

    private static final String EXPECTED = "start\n0.0\n10.0\n20.0\n100\n101\ndone";

    private static String cc(String... candidates) {
        for (String c : candidates) {
            try {
                if (new ProcessBuilder(c, "--version").start().waitFor(30, TimeUnit.SECONDS)
                        && true) return c;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private Path buildLib(Path dir, String arch) throws Exception {
        Path cFile = dir.resolve("fill-" + arch + ".c");
        Files.writeString(cFile, C_SRC);
        Path so = dir.resolve("libfill-" + arch + ".so");
        String comp = "NATIVE".equals(arch) || "jvm".equals(arch)
                ? firstOf("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc")
                : firstOf(arch + "-linux-gnu-gcc");
        assertNotNull(comp, "sem toolchain C para " + arch);
        Process p = new ProcessBuilder(comp, "-shared", "-fPIC", "-o",
                so.toString(), cFile.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "cc " + arch + " timeout");
        assertEquals(0, p.exitValue(), "cc " + arch + " falhou: " + o);
        return so;
    }

    private static String firstOf(String... candidates) {
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                p.waitFor(30, TimeUnit.SECONDS);
                return c;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private String runJvm(Path out) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().transferTo(baos);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "JVM timeout");
        assertEquals(0, p.exitValue(), "JVM exit, output: "
                + baos.toString(StandardCharsets.UTF_8));
        return baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    private String runNativeX86(Path out) throws Exception {
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary must exist");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().transferTo(baos);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "native timeout");
        assertEquals(0, p.exitValue(), "native exit, output: "
                + baos.toString(StandardCharsets.UTF_8));
        return baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    private String runQemu(String arch, Path out, Path libDir) throws Exception {
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        // O sysroot glibc do cross (o suse instala em /usr/<arch>-suse-linux/sys-root):
        // o qemu precisa do ld.so + libc da arch (o binário é dinâmico).
        String sysroot = "/usr/" + arch + "-suse-linux/sys-root";
        if (Files.isDirectory(Path.of(sysroot))) {
            pb.environment().put("QEMU_LD_PREFIX", sysroot);
        }
        pb.environment().put("LD_LIBRARY_PATH", libDir.toString());
        pb.redirectErrorStream(true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Process p = pb.start();
        p.getInputStream().transferTo(baos);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), arch + " timeout");
        assertEquals(0, p.exitValue(), arch + " exit, output: "
                + baos.toString(StandardCharsets.UTF_8));
        return baos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    @Test
    void arrayCopyOutFlowsBackOnJs(@TempDir Path dir) throws Exception {
        // O 'P' no runner JS: packArrayOut registra o par (array JS, segmento)
        // e o copyBack element-typed devolve após o downcall (o espelho do
        // buffer INOUT; o by-value 'p' nunca devolve — o contrato D6-2).
        String so = buildLib(dir, "NATIVE").toString();
        Path src = dir.resolve("copyout-js.kf");
        Files.writeString(src, KOF_SRC.formatted(so, so));
        Path out = dir.resolve("out-copyout-js");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), baos,
                new java.io.ByteArrayInputStream(new byte[0]), baos);
        assertEquals(0, ec, "JS exit code, output: "
                + baos.toString(StandardCharsets.UTF_8));
        assertEquals(EXPECTED, baos.toString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim(), "JS golden (copy-out borrowed)");
    }

    @Test
    void arrayCopyOutParityJvmNativeAndCross(@TempDir Path dir) throws Exception {
        // JVM + native x86-64 (o host lib)
        Path hostSo = buildLib(dir, "NATIVE");
        Path jvmSrc = dir.resolve("copyout-jvm.kf");
        Files.writeString(jvmSrc, KOF_SRC.formatted(hostSo, hostSo));
        Path jvmOut = dir.resolve("out-copyout-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);
        assertEquals(EXPECTED, jvm, "JVM golden (copy-out borrowed)");

        Path natOut = dir.resolve("out-copyout-native");
        CompilationResult rn = driver.compile(jvmSrc, natOut, Target.NATIVE);
        assertTrue(rn.success(), "native compile: " + rn.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runNativeX86(natOut), "native x86-64 golden (copy-out borrowed)");

        // Os dois cross sob qemu (o .so por arch)
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            Path crossSo = buildLib(dir, arch);
            Path src = dir.resolve("copyout-" + arch + ".kf");
            Files.writeString(src, KOF_SRC.formatted(crossSo, crossSo));
            Path out = dir.resolve("out-copyout-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
            assertEquals(EXPECTED, runQemu(arch, out, dir),
                    arch + " golden (copy-out borrowed sob qemu)");
        }
    }
}
