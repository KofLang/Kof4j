package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de paridade multi-target para strings.indent e strings.dedent.
 * Cobrindo JVM, KofJS e Native (x86_64 / cross).
 */
class KofStringsIndentDedentTest {
    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN_SOURCE = """
        main() {
            println(strings.indent("a\\nb\\nc", 2))
            println(strings.indent("a\\n\\nb", 2))
            println(strings.indent("hello", 0))
            println(strings.indent("hello", 3))
            println(strings.indent("", 4) + "|")
            println(strings.dedent("    a\\n      b\\n    c"))
            println(strings.dedent("  a\\n\\n  b"))
            println(strings.dedent("no indent"))
            println(strings.dedent("") + "|")
        }
        """;

    private static final String EXPECTED_OUTPUT = """
          a
          b
          c
          a

          b
        hello
           hello
        |
        a
          b
        c
        a

        b
        no indent
        |""".trim();

    @Test
    void indentDedentJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, GOLDEN_SOURCE, EXPECTED_OUTPUT);
    }

    @Test
    void indentDedentJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, GOLDEN_SOURCE, EXPECTED_OUTPUT);
    }

    @Test
    void indentDedentNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, GOLDEN_SOURCE, EXPECTED_OUTPUT);
    }

    @Test
    void indentDedentCrossArch(@TempDir Path tmp) throws Exception {
        String assertSrc = """
            main() {
                assert(strings.indent("a\\nb", 2) == "  a\\n  b")
                assert(strings.indent("x", 0) == "x")
                assert(strings.dedent("  a\\n    b") == "a\\n  b")
                assert(strings.dedent("hello") == "hello")
            }
            """;
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            runQemu(tmp, t, qemu, assertSrc);
        }
    }

    private void runJvm(Path tempDir, String source, String expected) throws Exception {
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
        assertEquals(0, ec, "JVM exit code: " + ec + ", output:\n" + output);
        assertEquals(expected, output);
    }

    private void runNative(Path tempDir, String source, String expected) throws Exception {
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
        assertEquals(0, ec, "Native exit code: " + ec + ", output:\n" + output);
        assertEquals(expected, output);
    }

    private void runJs(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        Path mjs = findJsEntry(outDir);
        Process p = new ProcessBuilder("node", mjs.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JS exit code: " + ec + ", output:\n" + output);
        assertEquals(expected, output);
    }

    private void assumeToolchain(String... tools) {
        for (String c : tools) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) {
                    Assumptions.assumeTrue(false, "toolchain ausente: " + c);
                }
            } catch (Exception e) {
                Assumptions.assumeTrue(false, "toolchain ausente: " + c);
            }
        }
    }

    private void runQemu(Path tempDir, Target target, String qemu, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(qemu, bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, target + " runtime exit " + ec + ", out: " + output);
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        return Files.walk(dir).flatMap(java.util.stream.Stream::of)
                .filter(p -> p.toString().endsWith(".mjs"))
                .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
    }
}
