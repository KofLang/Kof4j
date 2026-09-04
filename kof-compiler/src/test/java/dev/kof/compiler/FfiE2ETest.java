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