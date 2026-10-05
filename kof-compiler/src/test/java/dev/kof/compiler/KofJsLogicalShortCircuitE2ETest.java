package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.kof.compiler.KofJsPrograms.SRC_LOGICAL_AND_OR_SHORT_CIRCUIT;

/**
 * KofJS logical-operator short-circuit E2E (known-bugs §601).
 *
 * Split out of KofJsE2ETest to keep that class under the test-hygiene size budget;
 * behavior is unchanged — these run the same compiled-JS programs through the shared
 * KofJsSupport runner and assert on stdout.
 */
class KofJsLogicalShortCircuitE2ETest extends KofJsSupport {

    @Test
    void logicalAndOrShortCircuit(@TempDir Path tempDir) throws IOException {
        // && / || booleanos devem short-circuitar no JS (o lado de não não é
        // avaliado). Antes o backend emitia & / | bitwise → os dois lados
        // eram sempre avaliados (f-rodou aparecia 3x em vez de 0x).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_LOGICAL_AND_OR_SHORT_CIRCUIT);
        runJs(source, tempDir.resolve("out"), "y\nfalse");
    }

    @Test
    void bitwiseAndOrStillWorks(@TempDir Path tempDir) throws IOException {
        // & / | / ^ continuam bitwise (avaliando os dois lados) — o fix do
        // short-circuit não pode ter quebrado a aritmética de bits.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(3 & 5)
                println(3 | 5)
                println(3 ^ 5)
            }
            """);
        runJs(source, tempDir.resolve("out"), "1\n7\n6");
    }

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
