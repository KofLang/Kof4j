package dev.kof.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base for JVM+JS output-parity E2E tests: compiles a Kof program to both
 * targets, runs each, asserts the JVM output equals the expected value and the
 * JS output equals the JVM output. Consolidates the byte-identical
 * `driver`/`runJvm`/`runJs`/`assertBoth` helpers that were copied across the
 * JS-fold/loop-tail and nullable-relational condition suites (Phase 5 slice 8,
 * test-architecture-plan). No per-target assertion moves: only the execution
 * mechanism is shared.
 */
abstract class JsParityRunSupport {

    private final CompilerDriver driver = new CompilerDriver();

    protected String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    protected String runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        String s = out.toString().trim();
        assertEquals(0, exitCode, "JS exit code, output: " + s);
        return s;
    }

    protected void assertBoth(String program, String expected, Path tempDir, String name) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        Path outJvm = tempDir.resolve(name + "-jvm");
        Path outJs = tempDir.resolve(name + "-js");
        Files.createDirectories(outJvm);
        Files.createDirectories(outJs);
        CompilationResult rjvm = driver.compile(source, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(source, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        String jvm = runJvm(outJvm);
        String js = runJs(outJs);
        assertEquals(expected, jvm, "JVM output");
        assertEquals(jvm, js, "stdout parity JVM vs JS");
    }
}
