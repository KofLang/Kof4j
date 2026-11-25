package dev.kof.compiler.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §1218 RED-first no artefato: o programa real (`println(2)` + `println(1 + 1)`)
 * pelo caminho do `driver.interpret` imprime `2 2` (o segundo statement repete o
 * valor do primeiro). Esperado: `2 2`? NÃO — o certo é {@code 2 2} só quando a
 * soma é 2; aqui a soma É 2, então o caso precisa de um par com valores
 * distintos: {@code println(2)} + {@code println(3 + 4)} deve dar {@code 2 7}.
 *
 * <p>O teste usa o par que o §1218 mediu como errado fora desta árvore
 * ({@code 90} + {@code 90 + 94} → {@code 90 184}) e exige o valor certo; fica
 * RED até o defeito fechar.
 */
class PrintRepeatProducerTest {

    private static String interpret(Path dir, String body) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() {\n" + body + "}\n");
        return new dev.kof.compiler.CompilerDriver().interpret(List.of(src), dir, new String[0])
                .stdout().strip().replace("\n", " ");
    }

    @Test
    void theSecondStatementPrintsItsOwnSum(@TempDir Path dir) throws Exception {
        assertEquals("90 184", interpret(dir, """
                    println(90)
                    println(90 + 94)
                """), "90 + 94 = 184 é o valor certo; o teste pin o comportamento atual");
    }

    @Test
    void aDistinctSumPrintsItsOwnValue(@TempDir Path dir) throws Exception {
        assertEquals("2 7", interpret(dir, """
                    println(2)
                    println(3 + 4)
                """), "3 + 4 = 7 (o valor do primeiro statement não pode vazar)");
    }

    @Test
    void theControlStaysRight(@TempDir Path dir) throws Exception {
        assertEquals("5 80", interpret(dir, """
                    println(5)
                    println(40 + 40)
                """), "o controle");
    }
}
