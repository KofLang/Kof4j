package dev.kof.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suporte do E2E de KofJS ({@code KofJsE2ETest}): os runners JS. Os programas Kof
 * hoisted vivem em {@code KofJsPrograms}. Vive fora da classe de teste (Fase 3 da
 * arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os testes e o nome da
 * classe seguem no {@code KofJsE2ETest} — zero drift de citação.
 */
abstract class KofJsSupport extends KofJsPrograms {

    protected final CompilerDriver driver = new CompilerDriver();

    protected String runJs(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path jsFile = outDir.resolve("Default.mjs");
        assertTrue(Files.exists(jsFile), "Generated JS module should exist");
        ExecResult exec = execModule(jsFile, "");
        assertEquals(0, exec.exitCode(), "Exit code should be 0, output: '" + exec.output() + "'");
        assertEquals(expected, exec.output(), "Unexpected output");
        return exec.output();
    }

    protected String runJsWithStdin(Path source, Path outDir, String stdin, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path jsFile = outDir.resolve("Default.mjs");
        ExecResult exec = execModule(jsFile, stdin);
        assertEquals(0, exec.exitCode(), "Exit code should be 0, output: '" + exec.output() + "'");
        assertEquals(expected, exec.output(), "Unexpected output");
        return exec.output();
    }

    protected record ExecResult(int exitCode, String output) {
    }

    protected ExecResult execModule(Path jsFile, String stdin) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(jsFile, out,
                new ByteArrayInputStream(stdin.getBytes()), out);
        return new ExecResult(exitCode, out.toString().trim());
    }

    // 1. Hello World ─────────────────────────────────────────────────
}
