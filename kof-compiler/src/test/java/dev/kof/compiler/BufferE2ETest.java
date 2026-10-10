package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.buffer / nominal {@code Buffer(U8)} (D-R3-BUFFER, maintainer 21/09).
 * Incremental slice (R6-SCOPE): {@code buffer.alloc(Int)} + {@code Buffer.bytes()}
 * on the JVM and (21/09) on the JS target; #651 fatia A1 adds the x86-64
 * native surface (alloc/bytes/println) with JVM byte-for-byte parity.
 */
class BufferE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void allocAndBytesJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("buf.kf");
        Files.writeString(src, """
                main() {
                    val b = buffer.alloc(4)
                    println(b)
                    println(b.bytes())
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-buf"), Target.JVM);
        assertTrue(r.success(), "buffer.alloc must bind on JVM: " + r.diagnostics().getDiagnostics());
        assertEquals("Buffer[4]\n[0, 0, 0, 0]", runJvm(dir.resolve("out-buf")),
                "alloc(4) is zero-filled and 4 bytes long");
    }

    @Test
    void allocZeroAndNegativeClampJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("buf0.kf");
        Files.writeString(src, """
                main() {
                    println(buffer.alloc(0))
                    println(buffer.alloc(-3))
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-buf0"), Target.JVM);
        assertTrue(r.success(), "edge sizes must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("Buffer[0]\nBuffer[0]", runJvm(dir.resolve("out-buf0")),
                "0 stays 0; negative clamps to 0 (no crash, no huge alloc)");
    }

    @Test
    void allocBytesAndPrintlnNativeParity(@TempDir Path dir) throws IOException {
        String kof = """
                main() {
                    val b = buffer.alloc(4)
                    println(b)
                    println(b.bytes())
                    println(buffer.alloc(0))
                    println(buffer.alloc(-3))
                }
                """;
        Path jvmSrc = dir.resolve("bufnat-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-bufnat-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(dir.resolve("out-bufnat-jvm"));

        Path natSrc = dir.resolve("bufnat.kf");
        Files.writeString(natSrc, kof);
        CompilationResult rn = driver.compile(natSrc, dir.resolve("out-bufnat"), Target.NATIVE);
        assertTrue(rn.success(), "#651 fatia A1: buffer.alloc/bytes/println must bind on x86-64 Native: "
                + rn.diagnostics().getDiagnostics());
        String nativeOut = runNative(dir.resolve("out-bufnat"));
        assertEquals(jvm, nativeOut, "JVM==Native byte-for-byte parity (kof.buffer x86-64 fatia A1)");
        assertEquals("Buffer[4]\n[0, 0, 0, 0]\nBuffer[0]\nBuffer[0]", nativeOut,
                "native Buffer keeps the JVM contract: zero-filled, clamp, toString, bytes copy");
    }

    @Test
    void allocBytesAndPrintlnCrossParity(@TempDir Path dir) throws IOException {
        // #651 fatia B (29/09): a superfície Buffer(U8) (alloc/bytes/println)
        // binda no cross riscv64/aarch64 via NativeRiscvAsmBuffer, com o MESMO
        // contrato e layout do x86-64/JVM — oráculo JVM (regra 5).
        String kof = """
                main() {
                    val b = buffer.alloc(4)
                    println(b)
                    println(b.bytes())
                    println(buffer.alloc(0))
                    println(buffer.alloc(-3))
                }
                """;
        Path jvmSrc = dir.resolve("bufcross-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-bufcross-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(dir.resolve("out-bufcross-jvm"));
        assertEquals("Buffer[4]\n[0, 0, 0, 0]\nBuffer[0]\nBuffer[0]", jvm, "JVM golden");

        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            org.junit.jupiter.api.Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            Path src = dir.resolve("bufcross-" + arch + ".kf");
            Files.writeString(src, kof);
            CompilationResult rc = driver.compile(src, dir.resolve("out-bufcross-" + arch), t);
            assertTrue(rc.success(), "#651 fatia B: Buffer surface must bind on " + arch + ": "
                    + rc.diagnostics().getDiagnostics());
            Path bin = dir.resolve("out-bufcross-" + arch + "/Default/Main");
            assertTrue(Files.exists(bin), "binary " + bin + " must exist");
            ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
            pb.redirectErrorStream(true);
            try {
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
                assertEquals(0, p.waitFor(), arch + " exit, output: " + out);
                assertEquals(jvm, out, "JVM==" + arch + " byte-for-byte (Buffer surface, fatia B)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
        }
    }

    @Test
    void allocAndBytesJsParity(@TempDir Path dir) throws IOException {
        // kof.buffer no JS (D-R3-BUFFER, 21/09): mesmo contrato do JVM —
        // zero-filled, clamp de tamanho, bytes() materializa Byte[].
        String kof = """
                main() {
                    val b = buffer.alloc(4)
                    println(b)
                    println(b.bytes())
                    println(buffer.alloc(0))
                    println(buffer.alloc(-3))
                }
                """;
        Path jvmSrc = dir.resolve("bufjs-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-bufjs-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(dir.resolve("out-bufjs-jvm"));
        assertEquals("Buffer[4]\n[0, 0, 0, 0]\nBuffer[0]\nBuffer[0]", jvm, "JVM golden");

        Path jsSrc = dir.resolve("bufjs-js.kf");
        Files.writeString(jsSrc, kof);
        CompilationResult rjs = driver.compile(jsSrc, dir.resolve("out-bufjs-js"), Target.JS);
        assertTrue(rjs.success(), "buffer.alloc must bind on JS (21/09): "
                + rjs.diagnostics().getDiagnostics());
        String js = runJs(dir.resolve("out-bufjs-js"));
        assertEquals(jvm, js, "JVM==JS byte-for-byte parity (kof.buffer)");
    }

    private String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit code, output: " + out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    private String runNative(Path outDir) throws IOException {
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary " + bin + " deve existir");
        try {
            Process p = new ProcessBuilder(bin.toString())
                    .directory(outDir.getParent().toFile())
                    .redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, () -> "NATIVE run exit code, output: " + o);
            return o;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
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
