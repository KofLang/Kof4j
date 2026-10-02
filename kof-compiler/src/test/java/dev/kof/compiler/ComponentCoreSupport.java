package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Suporte do E2E de componentes ({@code ComponentCoreE2ETest}): os runners JVM/Native/JS. Os
 * programas Kof hoisted vivem em {@code ComponentCorePrograms}. Vive fora da classe de teste (Fase
 * 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os testes e o
 * nome da classe seguem no {@code ComponentCoreE2ETest} — zero drift de citação.
 */
abstract class ComponentCoreSupport extends ComponentCorePrograms {

    protected final CompilerDriver driver = new CompilerDriver();

    protected static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    protected String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    protected String runNative(Path source, Path outDir, String expected) throws IOException {
        assumeTrue(isLinux(), "Native target runs on Linux");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    protected void both(Path tempDir, String name, String program, String expected) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        runJvm(source, tempDir.resolve("jvm-" + name), expected);
        runNative(source, tempDir.resolve("native-" + name), expected);
    }

    /** Compiles for JS, runs in the embedded engine and returns stdout. */
    protected String runJs(Path tempDir, String name, String program) throws IOException {
        Path source = tempDir.resolve(name + "-js.kf");
        Files.writeString(source, program);
        CompilationResult js = driver.compile(source, tempDir.resolve("js-" + name), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(
                tempDir.resolve("js-" + name).resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS run should succeed: " + out);
        return out.toString().trim();
    }

    /** Compiles for JS and evaluates an extra probe expression via a shim module. */
    protected String runJsProbe(Path tempDir, String name, String program,
                              String probeJs) throws IOException {
        Path source = tempDir.resolve(name + "-js.kf");
        Files.writeString(source, program);
        CompilationResult js = driver.compile(source, tempDir.resolve("js-" + name), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        Path module = tempDir.resolve("js-" + name).resolve("Default.mjs");
        // append the probe to the generated module (it re-runs main; the
        // probe asserts on the resulting runtime state and prints the result)
        Files.writeString(module, Files.readString(module) + "\n" + probeJs + "\n");
        dev.kof.compiler.js.JsRuntimeTestSupport.includeImportsOf(module.getParent(), probeJs);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(module, out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS probe run should succeed: " + out);
        return out.toString().trim();
    }

}
