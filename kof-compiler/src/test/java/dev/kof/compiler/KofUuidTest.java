package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofUuidTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void uuidV4Jvm(@TempDir Path tmp) throws Exception {
        // v4 válido: 32 hex minúsculos, 8-4-4-4-12, dígito 13 = '4',
        // dígito 17 ∈ {8,9,a,b}. Dois uuids devem diferir (entropia).
        runJvm(tmp, """
            main() {
                var u = uuid.v4()
                assert(u.length == 36)
                assert(u.charAt(8) == 45)
                assert(u.charAt(13) == 45)
                assert(u.charAt(18) == 45)
                assert(u.charAt(23) == 45)
                assert(u.charAt(14) == 52)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                var w = uuid.v4()
                assert(u != w)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV4Native(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                var u = uuid.v4()
                assert(u.length == 36)
                assert(u.charAt(8) == 45)
                assert(u.charAt(13) == 45)
                assert(u.charAt(14) == 52)
                assert(u.charAt(18) == 45)
                assert(u.charAt(19) == 56)
                assert(u.charAt(23) == 45)
                var w = uuid.v4()
                assert(u != w)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV4Js(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                var u = uuid.v4()
                assert(u.length == 36)
                assert(u.charAt(14) == 52)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV4GatedOnCrossArch(@TempDir Path tmp) throws Exception {
        // SECN000 (política crypto lane): riscv/aarch sem primitiva de
        // random no asm puro — gate em compile-time, nunca link quebrado.
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var u = uuid.v4()
                println(u)
            }
            """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(source, tmp.resolve("cross-" + t), t);
            assertFalse(r.success(), t + " deve reportar SECN000");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SECN000"),
                    t + ": " + r.diagnostics().getDiagnostics());
        }
    }

    private String runJvm(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code, output: " + output);
        assertEquals(expected, output, "JVM output");
        return output;
    }

    private String runNative(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: " + output);
        assertEquals(expected, output, "Native output");
        return output;
    }

    private String runJs(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
             java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), err);
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output
                    + " err: " + err.toString(java.nio.charset.StandardCharsets.UTF_8).trim());
            assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        return Files.walk(dir).flatMap(p -> java.util.stream.Stream.of(p))
                .filter(p -> p.toString().endsWith(".mjs"))
                .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
    }
}
