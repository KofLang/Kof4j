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
 * A face OUT-PARAM ponteiro-para-ponteiro (o residual `pkthdr**` do pcap,
 * #1160/#1156 — D-R3-3.3 + a fatia COPY-OUT): um `Handle[]` emprestado
 * (`ownership borrowed`) cruza como o PONTEIRO DO PAYLOAD (obj+24) — a C
 * escreve os ponteiros nos slots e o chamador Kof lê `slot[0]` de volta
 * como um Handle de verdade. O fixture malloc/write-through/free prova o
 * ciclo COMPLETO (o slot sai null, a C escreve, o Handle volta, o valor é
 * lido através dele, o free fecha). Oráculo regra 5: o MESMO fonte roda no
 * JVM, no nativo x86-64 e nos dois cross sob qemu — saídas byte-a-byte
 * iguais. O JS fica FORA (o 'H' escalar já é a recusa nomeada — o 'PH'
 * segue a mesma). Sem toolchain → skip honesto, nunca falso-verde.
 */
class FfiOutParamHandleE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            #include <stdlib.h>

            void xout(void** slot) {
                void* p = malloc(sizeof(int));
                if (p == 0) return;
                *(int*)p = 777;
                *slot = p;
            }
            int xread(void* h) {
                if (h == 0) return -1;
                return *(int*)h;
            }
            void xfree(void* h) {
                free(h);
            }
            """;

    private static final String KOF_SRC = """
            foreign module fixtures {
                library "%s"
                abi c
                ownership borrowed
                extern xout(Handle[] slot): Void
                extern xread(Handle h): Int
                extern xfree(Handle h): Void
            }

            main() {
                var slot = new Handle[1]
                xout(slot)
                var h = slot[0]
                println(xread(h))
                xfree(h)
                println("done")
            }
            """;

    private static final String EXPECTED = "777\ndone";

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
        Path cFile = dir.resolve("outparam-" + arch + ".c");
        Files.writeString(cFile, C_SRC);
        Path so = dir.resolve("liboutparam-" + arch + ".so");
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
    void outParamHandleParityJvmNativeAndCross(@TempDir Path dir) throws Exception {
        // JVM + native x86-64 (o host lib)
        Path hostSo = buildLib(dir, "NATIVE");
        Path src = dir.resolve("outparam-host.kf");
        Files.writeString(src, KOF_SRC.formatted(hostSo, hostSo));
        Path jvmOut = dir.resolve("out-outparam-jvm");
        CompilationResult rj = driver.compile(src, jvmOut, Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runJvm(jvmOut), "JVM golden (Handle[] out-param)");

        Path natOut = dir.resolve("out-outparam-native");
        CompilationResult rn = driver.compile(src, natOut, Target.NATIVE);
        assertTrue(rn.success(), "native compile: " + rn.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runNativeX86(natOut), "native x86-64 golden (Handle[] out-param)");

        // Os dois cross sob qemu (o .so por arch)
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            Path crossSo = buildLib(dir, arch);
            Path csrc = dir.resolve("outparam-" + arch + ".kf");
            Files.writeString(csrc, KOF_SRC.formatted(crossSo, crossSo));
            Path out = dir.resolve("out-outparam-" + arch);
            CompilationResult r = driver.compile(csrc, out, t);
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
            assertEquals(EXPECTED, runQemu(arch, out, dir),
                    arch + " golden (Handle[] out-param sob qemu)");
        }
    }
}
