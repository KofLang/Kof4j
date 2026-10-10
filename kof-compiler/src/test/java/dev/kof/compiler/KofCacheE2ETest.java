package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for {@code kof.cache} — get/set/ttl/delete/clear nos 3 targets.
 */
class KofCacheE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private void assertTargetRuns(Path tempDir, String kofSource, String expected, Target target, String dirName) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve(dirName);
        CompilationResult result = driver.compile(source, outDir, target);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        String output;
        int ec;
        try {
            if (target == Target.JVM) {
                Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                        "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main")
                        .redirectErrorStream(true).start();
                output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n").trim();
                ec = p.waitFor();
            } else if (target == Target.NATIVE) {
                Process p = new ProcessBuilder(outDir.resolve("Default/Main").toString())
                        .redirectErrorStream(true).start();
                output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                ec = p.waitFor();
            } else {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                        InputStream.nullInputStream(), new ByteArrayOutputStream());
                output = buf.toString(StandardCharsets.UTF_8).trim();
            }
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running target " + target, e);
        }
        assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
        assertEquals(expected, output, "Unexpected output for target " + target);
    }

    @Test
    void cacheRoundtripAllTargets(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    cache.set("name", "Mel")
                    println(cache.get("name"))
                    println(cache.get("missing"))
                }
                """;
        assertTargetRuns(tempDir, src, "Mel\nnull", Target.JVM, "out-jvm");
        assertTargetRuns(tempDir, src, "Mel\nnull", Target.NATIVE, "out-native");
        assertTargetRuns(tempDir, src, "Mel\nnull", Target.JS, "out-js");
    }

    @Test
    void overwriteExistingKey(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    cache.set("k", "v1")
                    cache.set("k", "v2")
                    println(cache.get("k"))
                }
                """;
        assertTargetRuns(tempDir, src, "v2", Target.JVM, "out-jvm");
        assertTargetRuns(tempDir, src, "v2", Target.NATIVE, "out-native");
        assertTargetRuns(tempDir, src, "v2", Target.JS, "out-js");
    }

    @Test
    void ttlValueAndExpiry(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    cache.set("t", "x", 1)
                    println(cache.ttl("t") >= 0 && cache.ttl("t") <= 1)
                    println(cache.get("t"))
                    time.sleep(1300)
                    println(cache.get("t"))
                    println(cache.ttl("t") == -1)
                }
                """;
        assertTargetRuns(tempDir, src, "true\nx\nnull\ntrue", Target.JVM, "out-jvm");
        assertTargetRuns(tempDir, src, "true\nx\nnull\ntrue", Target.NATIVE, "out-native");
        assertTargetRuns(tempDir, src, "true\nx\nnull\ntrue", Target.JS, "out-js");
    }

    @Test
    void deleteAndClear(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    cache.set("a", "1")
                    cache.set("b", "2")
                    cache.delete("a")
                    println(cache.get("a"))
                    println(cache.get("b"))
                    cache.clear()
                    println(cache.get("b"))
                }
                """;
        assertTargetRuns(tempDir, src, "null\n2\nnull", Target.JVM, "out-jvm");
        assertTargetRuns(tempDir, src, "null\n2\nnull", Target.NATIVE, "out-native");
        assertTargetRuns(tempDir, src, "null\n2\nnull", Target.JS, "out-js");
    }

    @Test
    void setWithoutTtlHasNoExpiry(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    cache.set("k", "v")
                    println(cache.ttl("k") == -1)
                }
                """;
        assertTargetRuns(tempDir, src, "true", Target.JVM, "out-jvm");
        assertTargetRuns(tempDir, src, "true", Target.NATIVE, "out-native");
        assertTargetRuns(tempDir, src, "true", Target.JS, "out-js");
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
}