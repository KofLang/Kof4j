package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code @Test} de oraculo JVM compartilhado da familia {@code NativeIo*CrossTest}
 * (Fase 5 do {@code test-architecture-plan}, {@code D-TEST-ARCHITECTURE-PHASES}).
 * Antes cada classe declarava um {@code jvmOracle} byte-identico que so variava
 * no programa-fonte e no golden; agora a subclasse fornece os dois e herda o
 * teste. As subclasses que nao seguem o shape JVM (bytes/copy/move/text) ficam
 * em {@link NativeCrossSupport} e mantem os seus proprios {@code @Test}.
 */
abstract class NativeIoJvmOracleSupport extends NativeCrossSupport {

    /**
     * O programa JVM que produz o golden. Cada subclasse escreve o seu (o
     * subdir {@code jvm-*} so evita colisao de arquivos por alvo).
     */
    protected abstract Path jvmOracleSource(Path tempDir) throws IOException;

    /** O golden esperado no JVM. */
    protected abstract String jvmOracleExpected();

    @Test
    void jvmOracle(@TempDir Path tempDir) throws IOException {
        String out = runJvm(driver, jvmOracleSource(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(jvmOracleExpected(), out);
    }
}
