package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regressão do `known-bugs` §601: um relacional como operando ESQUERDO de
 * `&&`/`||` (`i < n && f() > 0`) baixava para bitwise `&`/`|` no JS (o RHS era
 * avaliado sempre). Fase 3 da arquitetura de testes: vive fora de
 * `KofJsE2ETest` para manter a classe de teste sob o teto de linhas
 * (`check_test_hygiene`); os testes seguem o mesmo runner `KofJsSupport`.
 */
class JsComparisonShortCircuitE2ETest extends KofJsSupport {

    @Test
    void logicalShortCircuitWithComparisonLeftOperand(@TempDir Path tempDir) throws IOException {
        // §601: um relacional como operando ESQUERDO de && / || (`i < n && f() > 0`)
        // deixava o `accType` do lowering numérico, então o backend JS via um
        // operando não-Bool e emitia `&`/`|` bitwise — o RHS era avaliado sempre
        // (`rhs` aparecia) e guardas como `i < end && b[i] != …` liam fora dos
        // limites. Agora o tipo do resultado é Bool e o short-circuit volta.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            Int f() {
                println("rhs")
                return 1
            }
            main() {
                var i = 3
                var n = 3
                if (i < n && f() > 0) {
                    println("both")
                }
                var r = i < n && f() > 0
                println(r)
                if (i <= n || f() > 0) {
                    println("or")
                }
            }
            """);
        runJs(source, tempDir.resolve("out"), "false\nor");
    }
}
