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
 * A face HANDLE no CROSS riscv64/aarch64 (D-R3-3.3 option A — o espelho cross
 * da fatia x86/JVM b7cfdc66e): o retorno `Handle` do `extern` cruza como o
 * PONTEIRO CRU em a0 (o `sd a0, 0(sp)` do operando) e o Handle devolvido é
 * passado de volta como o MESMO ponteiro — o ciclo de vida malloc/free prova
 * o round-trip opaco (alloc → write through → read → free), sem pcap nem
 * rede: C portável que qualquer toolchain cross compila. Oráculo regra 5:
 * o MESMO fonte roda no JVM, no nativo x86-64 e nos dois cross sob qemu —
 * saídas byte-a-byte iguais. Sem toolchain → skip honesto, nunca falso-verde.
 */
class FfiCrossHandleE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            #include <stdlib.h>

            void* halloc(int v) {
                int* p = (int*)malloc(sizeof(int));
                if (p == 0) return 0;
                *p = v;
                return p;
            }
            int hread(void* h) {
                if (h == 0) return -1;
                return *(int*)h;
            }
            void hfree(void* h) {
                free(h);
            }
            """;

    private static final String KOF_SRC = """
            foreign module fixtures {
                library "%s"
                abi c
                extern halloc(Int v): Handle
                extern hread(Handle h): Int
                extern hfree(Handle h): Void
            }

            main() {
                var h = halloc(4242)
                println(hread(h))
                println(hread(h))
                hfree(h)
                println("done")
            }
            """;

    private static final String EXPECTED = "4242\n4242\ndone";

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

    private Path buildLib(Path dir, String arch) throws Exception {
        Path cFile = dir.resolve("handle-" + arch + ".c");
        Files.writeString(cFile, C_SRC);
        Path so = dir.resolve("libhandle-" + arch + ".so");
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
    void handleParityJvmNativeAndCross(@TempDir Path dir) throws Exception {
        // JVM + native x86-64 (o host lib)
        Path hostSo = buildLib(dir, "NATIVE");
        Path src = dir.resolve("handle-host.kf");
        Files.writeString(src, KOF_SRC.formatted(hostSo, hostSo));
        Path jvmOut = dir.resolve("out-handle-jvm");
        CompilationResult rj = driver.compile(src, jvmOut, Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runJvm(jvmOut), "JVM golden (Handle lifecycle)");

        Path natOut = dir.resolve("out-handle-native");
        CompilationResult rn = driver.compile(src, natOut, Target.NATIVE);
        assertTrue(rn.success(), "native compile: " + rn.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runNativeX86(natOut), "native x86-64 golden (Handle lifecycle)");

        // Os dois cross sob qemu (o .so por arch)
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            Path crossSo = buildLib(dir, arch);
            Path csrc = dir.resolve("handle-" + arch + ".kf");
            Files.writeString(csrc, KOF_SRC.formatted(crossSo, crossSo));
            Path out = dir.resolve("out-handle-" + arch);
            CompilationResult r = driver.compile(csrc, out, t);
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
            assertEquals(EXPECTED, runQemu(arch, out, dir),
                    arch + " golden (Handle lifecycle sob qemu)");
        }
    }
}
