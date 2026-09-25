package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-FULL-PARITY-050 (row 6, lane parity, 26/09): {@code kof.gpu} no cross
 * (riscv64/aarch64). Sem libvkchain.so o alvo nativo degrada honestamente:
 * {@code available()=false} e dispatch devolve o codigo de fallback (nao-zero)
 * para o caller cair no golden CPU — mesmo contrato de {@code JvmVkStubRuntime}
 * (Android) e dos stubs x86-64. Antes desta unidade o cross NAO linkava
 * (undefined reference a {@code kof_vk_*}/{@code kof_mv64_*}), porque
 * {@code KofGpu.supportedOn} libera o nativo mas nenhum runtime era emitido no
 * ramo riscv/aarch64.
 */
class KofGpuCrossTest {

    private static final String SRC = """
            main() {
                assert(gpu.available() == false)
                assert(!gpu.available())
                assert(gpu.dispatchMatmul(new Int[4], new Int[4], new Int[4], 2, 2, 2) == -1)
                assert(gpu.dispatchMatmul64(new Long[4], new Long[4], new Long[4], 2, 2, 2) == -1)
                assert(gpu.mvSetShape(2, 2) == -1)
                assert(gpu.mvLoadW(new Long[4], 2, 2) == -1)
                assert(gpu.failReason().contains("Vulkan"))
                assert(gpu.failReason().contains("fallback CPU"))
                println("gpu-ok")
            }
            """;

    @Test
    void gpuCrossArchRiscv64(@TempDir Path tmp) {
        cross(tmp, Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void gpuCrossArchAarch64(@TempDir Path tmp) {
        cross(tmp, Target.NATIVE_AARCH64, "aarch64");
    }

    @Test
    void gpuScriptFallback(@TempDir Path tmp) throws IOException {
        // Script (interpretador) usa o runtime JVM real (FFM): sem
        // libvkchain.so no host degrada para available=false/dispatch=-1 —
        // o MESMO contrato de fallback dos outros alvos, com a mensagem do
        // runtime FFM (libvkchain) em vez da do stub. Nao afirma GPU001.
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    assert(gpu.available() == false)
                    assert(gpu.dispatchMatmul(new Int[4], new Int[4], new Int[4], 2, 2, 2) == -1)
                    assert(gpu.dispatchMatmul64(new Long[4], new Long[4], new Long[4], 2, 2, 2) == -1)
                    assert(gpu.failReason().length() > 0)
                    println("gpu-ok")
                }
                """);
        KofInterpreter.Result ir = new CompilerDriver().interpret(java.util.List.of(source), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertTrue(ir.stdout().contains("gpu-ok"), "script stdout: " + ir.stdout());
    }

    @Test
    void gpuJsFallback(@TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, SRC);
        Path out = tmp.resolve("js");
        CompilationResult r = new CompilerDriver().compile(source, out, Target.JS);
        assertTrue(r.success(), "JS should accept gpu.* now (row 6): " + r.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(out), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String outStr = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + outStr);
            assertEquals("gpu-ok", outStr, "JS fallback CPU honesto");
        }
    }

    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new IOException("no .mjs in " + dir));
        }
    }

    private void cross(Path tempDir, Target target, String arch) {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                hasCmd(arch + "-linux-gnu-as", arch + "-linux-gnu-ld", "qemu-" + arch),
                "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
        try {
            Path source = tempDir.resolve("Main-" + arch + "-" + System.nanoTime() + ".kf");
            Files.writeString(source, SRC);
            Path out = tempDir.resolve("out-" + arch + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(source, out, target);
            assertTrue(r.success(), arch + " compile failed: " + r.diagnostics().getDiagnostics());
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.exists(bin), arch + " binary should exist");
            Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
            String outStr = new String(p.getInputStream().readAllBytes()).trim();
            int ec = p.waitFor();
            assertEquals(0, ec, arch + " exit code, output: " + outStr);
            assertEquals("gpu-ok", outStr, arch + " output (fallback CPU honesto)");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static boolean hasCmd(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
