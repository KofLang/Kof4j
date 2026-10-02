package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nível de log compartilhado ({@code KofLogE2ETest}/{@code NativeLogE2ETest}): o
 * programa e as 3 asserções de nível IDÊNTICAS entre JVM e Native; cada classe
 * fornece o seu runner. Vive fora das classes (Fase 4/harness,
 * {@code D-TEST-ARCHITECTURE-GO}) — os testes e os nomes das classes seguem nos
 * dois arquivos, zero drift (as contagens 11 e 7 não mudam).
 */
abstract class LogLevelSupport {

    protected static final String LOG_PROGRAM = """
            main() {
                log.debug("detail message")
                log.info("hello from kof")
                log.warn("careful")
                log.error("boom")
            }
            """;

    /** Executa o programa com {@code KOF_LOG_LEVEL}, devolvendo {stdout, stderr}. */
    protected abstract String[] run(Path tempDir, String kofSource, String level) throws IOException;

    @Test
    void errorLevelSuppressesInfo(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, LOG_PROGRAM, "error");
        assertFalse(out[0].contains("hello from kof"), out[0]);
        assertTrue(out[1].contains("ERROR boom"), out[1]);
    }

    @Test
    void offSuppressesEverything(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, LOG_PROGRAM, "off");
        assertEquals("", out[0].trim());
        assertEquals("", out[1].trim());
    }

    @Test
    void warnGoesToStderr(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, """
                main() {
                    log.warn("to stderr")
                }
                """, null);
        assertEquals("", out[0].trim());
        assertTrue(out[1].contains("WARN to stderr"), out[1]);
    }
}
