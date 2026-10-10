package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.TimeUnit;

/**
 * Suporte dos testes E2E que sobem um servidor Kof como processo-filho
 * ({@code KofWeb*}/{@code KofHttp*}/{@code PaginationPageRequestE2ETest}/
 * {@code KofMediaE2ETest}): o campo {@code serverProcess} e a parada idêntica
 * (destroy → wait 5s → destroyForcibly) viviam copiados em 12 classes. Vive fora
 * delas (Fase 5/harness, {@code D-TEST-ARCHITECTURE-PHASES}); os testes e os
 * nomes das classes seguem nos arquivos — zero drift de citação.
 */
abstract class ServerProcessSupport {

    protected Process serverProcess;

    @AfterEach
    void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
    }
}
