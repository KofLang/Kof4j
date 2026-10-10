package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.log no target Native — mesmo contrato do JVM (KofLogE2ETest):
 * "yyyy-MM-dd HH:mm:ss.SSS LEVEL msg", KOF_LOG_LEVEL filtra, warn/error
 * vão para stderr. Delta documentado: timestamp em UTC.
 */
class NativeLogE2ETest extends LogLevelSupport {

    private final CompilerDriver driver = new CompilerDriver();

    /** Retorna {stdout, stderr} da execução do binário nativo. */
    protected String[] run(Path tempDir, String kofSource, String level) throws IOException {
        Path source = tempDir.resolve("Log.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("native");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(false);
            if (level != null) pb.environment().put("KOF_LOG_LEVEL", level);
            Process p = pb.start();
            String stdout = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            String stderr = new String(p.getErrorStream().readAllBytes());
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, stderr: '" + stderr + "'");
            return new String[]{stdout, stderr};
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void defaultLevelIsInfo(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, LOG_PROGRAM, null);
        assertTrue(out[0].trim().endsWith("INFO hello from kof"), "stdout: " + out[0]);
        assertFalse(out[0].contains("detail message"), "debug suppressed by default: " + out[0]);
        assertTrue(out[1].contains("WARN careful"), out[1]);
        assertTrue(out[1].contains("ERROR boom"), out[1]);
    }

    @Test
    void timestampHasCivilFormat(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, LOG_PROGRAM, null);
        // yyyy-MM-dd HH:mm:ss.SSS LEVEL msg — checa formato completo da linha INFO
        String line = out[0].lines().filter(l -> l.contains("INFO")).findFirst().orElse("");
        assertTrue(line.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} INFO .*"),
                "linha: '" + line + "'");
    }

    @Test
    void debugLevelShowsDebug(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, LOG_PROGRAM, "debug");
        assertTrue(out[0].contains("DEBUG detail message"), out[0]);
    }

    @Test
    void uppercaseEnvLevelAccepted(@TempDir Path tempDir) throws IOException {
        // o parse é case-insensitive ("DEBUG" também ativa debug)
        String[] out = run(tempDir, LOG_PROGRAM, "DEBUG");
        assertTrue(out[0].contains("DEBUG detail message"), out[0]);
    }
}
