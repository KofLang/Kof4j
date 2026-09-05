package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FFI end-to-end (TIER 2.1.4): extern binds to a real .so (libc) via FFM and
 * is callable from Kof. Requires a full JDK on Linux (libc.so.6).
 */
class FfiE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void libcAbsEndToEndNative(@TempDir Path dir) throws IOException, InterruptedException {
        Path src = dir.resolve("ffi-native.kf");
        Files.writeString(src, """
                extern "libc.so.6" abs(Int x): Int

                main() {
                    println(abs(-5))
                }
                """);

        Path out = dir.resolve("out-native");
        CompilationResult result = driver.compile(src, out, Target.NATIVE);
        assertTrue(result.success(), "native compile must succeed (extern bound): "
                + result.diagnostics().getDiagnostics());

        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary must exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native exit code, output: " + output);
        assertEquals("5", output, "abs(-5) must return 5 via libc (native dlsym)");
    }

    @Test
    void jsExternEmitsFfi002(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi-js.kf");
        Files.writeString(src, """
                extern "libc.so.6" abs(Int x): Int

                main() {
                    println("hi")
                }
                """);

        CompilationResult result = driver.compile(src, dir.resolve("out-js"), Target.JS);
        assertFalse(result.success(), "JS target must not silently drop extern");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("FFI002"), "expected FFI002 on JS, got: " + diags);
    }

    @Test
    void libcAtoiStringToIntBothTargets(@TempDir Path dir) throws Exception {
        String kof = """
                extern "libc.so.6" atoi(String s): Int

                main() {
                    println(atoi("42"))
                }
                """;

        // JVM
        Path jvmSrc = dir.resolve("atoi-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        assertEquals("42", runJava(dir.resolve("out-jvm")));

        // Native
        Path natSrc = dir.resolve("atoi-native.kf");
        Files.writeString(natSrc, kof);
        CompilationResult rn = driver.compile(natSrc, dir.resolve("out-native"), Target.NATIVE);
        assertTrue(rn.success(), "Native compile: " + rn.diagnostics().getDiagnostics());
        assertEquals("42", runNative(dir.resolve("out-native")));
    }

    @Test
    void libcFabsDoubleToDouble(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi-double.kf");
        Files.writeString(src, """
                extern "libm.so.6" sqrt(Double x): Double

                main() {
                    println(sqrt(9.0))
                }
                """);

        Path out = dir.resolve("out-double");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "JVM double extern must compile: "
                + result.diagnostics().getDiagnostics());
        assertEquals("3.0", runJava(out));
    }

    @Test
    void arraySumViaGccSo(@TempDir Path dir) throws Exception {
        Path c = dir.resolve("arr_sum.c");
        Files.writeString(c, "int arr_sum(int* a, int n) { int s = 0; for (int i = 0; i < n; i++) s += a[i]; return s; }\n");
        Path so = dir.resolve("libarr_sum.so");
        ProcessBuilder gcc = new ProcessBuilder("gcc", "-shared", "-fPIC", "-O2", "-o", so.toString(), c.toString());
        gcc.redirectErrorStream(true);
        Process gp = gcc.start();
        String gccOut = new String(gp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, gp.waitFor(), "gcc: " + gccOut);
        assertTrue(Files.exists(so), ".so must exist");

        Path kof = dir.resolve("arr.kf");
        Files.writeString(kof, """
                extern "%s" arr_sum(Int[] a): Int

                main() {
                    var a = new Int[3]
                    a[0] = 10
                    a[1] = 20
                    a[2] = 30
                    println(arr_sum(a))
                }
                """.formatted(so.toString()));

        Path out = dir.resolve("out-arr");
        CompilationResult result = driver.compile(kof, out, Target.JVM);
        assertTrue(result.success(), "JVM array extern must compile: "
                + result.diagnostics().getDiagnostics());
        assertEquals("60", runJava(out));

        Path outNat = dir.resolve("out-arr-native");
        CompilationResult rn = driver.compile(kof, outNat, Target.NATIVE);
        assertTrue(rn.success(), "Native array extern must compile: "
                + rn.diagnostics().getDiagnostics());
        assertEquals("60", runNative(outNat));
    }

    @Test
    void libcAbsEndToEnd(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi.kf");
        Files.writeString(src, """
                extern "libc.so.6" abs(Int x): Int

                main() {
                    println(abs(-5))
                }
                """);

        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "compile must succeed (extern bound on JVM): "
                + result.diagnostics().getDiagnostics());

        String output = runJvm(out);
        assertEquals("5", output, "abs(-5) must return 5 via libc");
    }

    private String runJava(Path outDir) throws IOException {
        return runJvm(outDir);
    }

    private String runNative(Path outDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(outDir.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "native exit code, output: " + output);
            return output;
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
                    "--enable-preview",
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