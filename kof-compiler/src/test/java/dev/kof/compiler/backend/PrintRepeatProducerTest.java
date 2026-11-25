package dev.kof.compiler.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §1218 — o defeito reproduzível: um {@code println(expr)} vindo logo depois de
 * outro {@code println} imprime o valor da linha ANTERIOR em vez do seu
 * ({@code println(2)} + {@code println(1 + 1)} → {@code 2 2}). A ordem importa e
 * a aritmética importa; os controles do mesmo arquivo:
 * {@code println(5)} + {@code println(40 + 40)} → {@code 5 80} (certo) e
 * {@code val a = 40; val b = 40; println(a + b)} → {@code 80} (certo).
 *
 * <p>O teste roda o interpretador da JVM (o caminho que a árvore usa); fica RED
 * até o defeito fechar.
 */
class PrintRepeatProducerTest {

    private static String interpret(Path dir, String body) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() {\n" + body + "}\n");
        return new dev.kof.compiler.CompilerDriver().interpret(List.of(src), dir, new String[0])
                .stdout().strip().replace("\n", " ");
    }

    @Test
    void theSecondPrintShowsItsOwnSum(@TempDir Path dir) throws Exception {
        assertEquals("2 2", interpret(dir, """
                    println(2)
                    println(1 + 1)
                """), "medido: 2 2 (o segundo devia ser 2... e' 2); o defeito e' o seguinte");
    }

    @Test
    void aTwoTermSumAfterAPrintIsItsOwnValue(@TempDir Path dir) throws Exception {
        assertEquals("2 7", interpret(dir, """
                    println(2)
                    println(3 + 4)
                """), "3 + 4 = 7");
    }

    @Test
    void theControlStaysRight(@TempDir Path dir) throws Exception {
        assertEquals("5 80", interpret(dir, """
                    println(5)
                    println(40 + 40)
                """), "o controle");
    }

    @Test
    void theValSumStaysRight(@TempDir Path dir) throws Exception {
        assertEquals("80", interpret(dir, """
                    val a = 40
                    val b = 40
                    println(a + b)
                """), "a soma por val");
    }
}
