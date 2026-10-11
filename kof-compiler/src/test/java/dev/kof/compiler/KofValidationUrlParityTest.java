package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;

/**
 * Regressão da paridade de {@code validation.isUrl} no backend Native (Fase 6
 * slice 25). Vive fora do {@code KofValidationTest} para manter o arquivo
 * original abaixo do limiar de {@code oversized} do ratchet de higiene
 * ({@code scripts/check_test_hygiene.sh}).
 *
 * <p>O asm x86 testava {@code byte4==':'} com {@code jne .Lv_url_false} ANTES do
 * ramo https ficar alcançável, então {@code https://...} (byte4='s') caía
 * direto em false — e todo URL com path/query também. O asm riscv já estava
 * correto.
 */
class KofValidationUrlParityTest extends KofValidationSupport {

    @Test
    void validationUrlNativeParity(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(validation.isUrl("http://a.b"))
                assert(validation.isUrl("https://kof.dev"))
                assert(validation.isUrl("https://example.com/path?q=1"))
                assert(!validation.isUrl("ftp://x"))
                assert(!validation.isUrl("http:/x"))
                println("ok")
            }
            """, "ok");
    }
}
