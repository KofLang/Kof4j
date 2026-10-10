package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suporte do E2E do alvo JVM ({@code JvmE2ETest}): o runner JVM. Os programas
 * Kof hoisted vivem em {@code JvmPrograms}. Vive fora da classe de teste para
 * mantê-la abaixo do limite de 500 linhas de teste (Fase 3 da arquitetura de
 * testes, {@code D-TEST-ARCHITECTURE-GO}); os testes e o nome da classe seguem
 * no {@code JvmE2ETest} — zero drift de citação.
 */
abstract class JvmSupport extends JvmPrograms {

    protected final CompilerDriver driver = new CompilerDriver();

    protected String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path classFile = outDir.resolve("Default/Main.class");
        assertTrue(Files.exists(classFile), "Class file should exist");
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
}
