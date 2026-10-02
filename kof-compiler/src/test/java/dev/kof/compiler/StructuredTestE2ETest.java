package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G6 — `test "nome" { }`: a suíte estruturada do Kof. Cada teste vira uma
 * função em compile-time (nunca reflection); `kof test` roda o runner
 * sintetizado com PASS/FAIL por nome e exit code por resultado.
 * Paridade JVM + Native + JS. Também cobre process.exit(code).
 */
class StructuredTestE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SUITE = """
            test "soma simples" {
                assert(2 + 2 == 4)
            }
            test "string igual" {
                assert("kof" == "kof", "strings iguais")
            }
            main() {
                println("main do usuario — ignorado pelo harness")
            }
            """;

    private static final String SUITE_COM_FALHA = """
            test "passa" {
                assert(true)
            }
            test "falha" {
                assert(1 == 2, "um nao eh dois")
            }
            """;

    /** Executa o harness compilado e devolve (exitCode, stdout). */
    private record Run(int exitCode, String output) {
    }

    private Run runJvm(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compileForTests(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            return new Run(p.waitFor(), output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private Run runNative(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compileForTests(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            return new Run(p.waitFor(), output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private Run runJs(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compileForTests(source, outDir, Target.JS);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path entry = outDir.resolve("Default.mjs");
        if (!Files.exists(entry)) {
            List<Path> entries;
            try (var s = Files.walk(outDir)) {
                entries = s.filter(p -> p.toString().endsWith(".mjs"))
                        .filter(p -> !p.toString().contains("kof-runtime")).toList();
            }
            assertFalse(entries.isEmpty(), "JS entry module should exist");
            entry = entries.get(0);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(entry, out,
                java.io.InputStream.nullInputStream(), java.io.OutputStream.nullOutputStream(),
                false, new String[0]);
        return new Run(ec, out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim());
    }

    @Test
    void discoversTestsInOrder(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SUITE);
        driver.compileForTests(source, tempDir.resolve("out"), Target.JVM);
        List<CompilerDriver.TestInfo> tests = driver.discoveredTests();
        assertEquals(2, tests.size());
        assertEquals("soma simples", tests.get(0).name());
        assertEquals("kof_test_0", tests.get(0).functionName());
        assertEquals("string igual", tests.get(1).name());
        assertEquals("kof_test_1", tests.get(1).functionName());
    }

    @Test
    void jvmHarnessRunsSuiteAndIgnoresUserMain(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SUITE);
        Run r = runJvm(source, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("PASS soma simples"), () -> "output: " + r.output());
        assertTrue(r.output().contains("PASS string igual"), () -> "output: " + r.output());
        assertFalse(r.output().contains("main do usuario"),
                "user main must not run in test mode");
        assertTrue(r.output().contains("0 failed of 2 tests"), () -> "output: " + r.output());
    }

    @Test
    void nativeHarnessMatchesJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SUITE);
        Run r = runNative(source, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("PASS soma simples"));
        assertTrue(r.output().contains("PASS string igual"));
        assertTrue(r.output().contains("0 failed of 2 tests"));
    }

    @Test
    void jsHarnessMatchesJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SUITE);
        Run r = runJs(source, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("PASS soma simples"));
        assertTrue(r.output().contains("PASS string igual"));
    }

    @Test
    void failingTestReportsNameMessageAndExitCodeOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SUITE_COM_FALHA);
        Run r = runJvm(source, tempDir.resolve("out"));
        assertEquals(1, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("FAIL falha: um nao eh dois"), () -> "output: " + r.output());
        assertTrue(r.output().contains("PASS passa"));
        assertTrue(r.output().contains("1 failed of 2 tests"));
        // sem stack trace cru no output do harness
        assertFalse(r.output().contains("Exception in thread"), () -> "output: " + r.output());
    }

    @Test
    void failingTestReportsExitCodeOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SUITE_COM_FALHA);
        Run r = runNative(source, tempDir.resolve("out"));
        assertEquals(1, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("FAIL falha: um nao eh dois"));
        assertTrue(r.output().contains("PASS passa"));
    }

    @Test
    void failingTestReportsExitCodeOnJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SUITE_COM_FALHA);
        Run r = runJs(source, tempDir.resolve("out"));
        assertEquals(1, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("FAIL falha: um nao eh dois"));
    }

    @Test
    void filesWithoutTestsCompileUnchanged(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    println("programa normal")
                }
                """);
        CompilationResult plain = driver.compile(source, tempDir.resolve("plain"), Target.JVM);
        assertTrue(plain.success());
        CompilationResult harness = driver.compileForTests(source, tempDir.resolve("harness"), Target.JVM);
        assertTrue(harness.success());
        assertTrue(driver.discoveredTests().isEmpty());
        // sem testes, o modo harness mantém o main original
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp",
                    tempDir.resolve("harness").toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            assertEquals("programa normal", output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void testSourcesHonorExplicitModuleRoot(@TempDir Path tempDir) throws IOException {
        // #708: compileForTestsSources must use the moduleRoot ARGUMENT, not a
        // stale/null driver.moduleRoot. With a fresh driver the package-dir
        // correspondence (PKG004) only holds when the explicit root is used.
        Path root = tempDir.resolve("app");
        Files.createDirectories(root.resolve("exemplo"));
        Files.writeString(root.resolve("exemplo/Calculo.kf"), """
                package exemplo
                Int somar(Int a, Int b) { return a + b }
                """);
        Files.writeString(root.resolve("exemplo/CalculoTest.kf"), """
                package exemplo
                import exemplo.Calculo
                test "soma" { assert(somar(2, 3) == 5) }
                """);
        List<Path> sources = List.of(
                root.resolve("exemplo/Calculo.kf"),
                root.resolve("exemplo/CalculoTest.kf"));
        CompilationResult r = driver.compileForTestsSources(
                sources, tempDir.resolve("out"), Target.JVM, root);
        assertTrue(r.success(),
                "explicit moduleRoot must satisfy PKG004: " + r.diagnostics().getDiagnostics());
        assertEquals(1, driver.discoveredTests().size(),
                "the suite must be discovered from the explicit root: " + driver.discoveredTests());
    }

    @Test
    void processExitSetsCodeOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    println("antes")
                    process.exit(7)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), () -> "" + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp",
                    tempDir.resolve("out").toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int ec = p.waitFor();
            assertEquals(7, ec, () -> "output: " + output);
            assertEquals("antes", output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void processExitSetsCodeOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    println("antes")
                    process.exit(3)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), () -> "" + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out/Default/Main");
        assertTrue(Files.exists(bin));
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            assertEquals(3, p.waitFor());
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void processExitSetsCodeOnJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    println("antes")
                    process.exit(5)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JS);
        assertTrue(result.success(), () -> "" + result.diagnostics().getDiagnostics());
        Path entry = tempDir.resolve("out/Default.mjs");
        assertTrue(Files.exists(entry), "Default.mjs should exist");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(entry, out,
                java.io.InputStream.nullInputStream(), java.io.OutputStream.nullOutputStream(),
                false, new String[0]);
        assertEquals(5, ec);
    }
}
