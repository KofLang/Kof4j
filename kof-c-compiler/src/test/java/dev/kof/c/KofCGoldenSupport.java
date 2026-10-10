package dev.kof.c;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * Laço de golden dos testes C que validam uma lista de programas por ISA
 * ({@code KofCParamsCompilerTest}/{@code KofCStructCompilerTest}). Vive fora das
 * classes (Fase 4/harness, {@code D-TEST-ARCHITECTURE-GO}) e SÓ elas o estendem
 * (o {@code KofCSupport} puro fica para as classes sem lista de programas), de
 * modo que as 3 {@code @Test} não vazam para outras classes. Os testes e os
 * nomes das classes seguem nos 2 arquivos — zero drift de citação.
 */
abstract class KofCGoldenSupport extends KofCSupport {

    @Test
    void x86OracleMatchesEveryGolden(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.X86_64);
        assertAllPrograms(KofCTarget.X86_64, tmp);
    }

    @Test
    void riscv64MatchesEveryGolden(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.RISCV64);
        assertAllPrograms(KofCTarget.RISCV64, tmp);
    }

    @Test
    void aarch64MatchesEveryGolden(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.AARCH64);
        assertAllPrograms(KofCTarget.AARCH64, tmp);
    }
}
