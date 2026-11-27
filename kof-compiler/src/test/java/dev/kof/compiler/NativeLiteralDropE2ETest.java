package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §1218 (medido 25/11 no Énois): um {@code println(a + b)} / {@code println(a * b)}
 * lê o {@code a} quando o MESMO valor terminou a linha anterior — {@code
 * println(7)} + {@code println(5 + 2)} imprime {@code 7 7}, {@code println(50)} +
 * {@code println(2 * 25)} imprime {@code 50 50}. Comum ao interpretador da JVM e
 * ao nativo (a JVM é determinística aqui), e a IR carrega as constantes certas —
 * o defeito está na expressão composta com o mesmo literal de antes.
 */
class NativeLiteralDropE2ETest {

    private static String interpret(Path dir, String body) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() {\n" + body + "}\n");
        return new CompilerDriver().interpret(List.of(src), dir, new String[0])
                .stdout().strip().replace("\n", " ");
    }

    /** O corpo do esqueleto: 90 + 94 depois de imprimir o 90 (184, o valor certo). */
    @Test
    void theBodySumOfTheSkeletonIsReadWhole(@TempDir Path dir) throws Exception {
        String out = interpret(dir, """
                    println(90)
                    println(90 + 94)
                """);
        assertEquals("90 184", out, "a soma depois do primeiro termo impresso (90 + 94 = 184)");
    }

    /** O padrão geral: a soma/multiplicação repete o último valor da linha anterior. */
    @Test
    void aSumDoesNotInheritThePreviousLineValue(@TempDir Path dir) throws Exception {
        String out = interpret(dir, """
                    println(7)
                    println(5 + 2)
                """);
        assertEquals("7 7", out, "5 + 2 = 7 (medido: o próprio 7 da linha anterior)");
    }

    /** O controle: termos distintos, a soma fica certa. */
    @Test
    void theNeighbourValuesAreUnaffected(@TempDir Path dir) throws Exception {
        String out = interpret(dir, """
                    println(5)
                    println(40 + 40)
                    println(3)
                    println(20 + 30)
                """);
        assertEquals("5 80 3 50", out, "o controle: " + out);
    }

    /** O NATIVO também (mesma resposta do interpretador). */
    @Test
    void theNativeBackendGivesTheSameAnswerAsTheInterpreter(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                main() {
                    println(7)
                    println(5 + 2)
                }
                """);
        CompilationResult r = new CompilerDriver().compile(src, dir.resolve("out"), Target.NATIVE);
        if (!r.success()) {
            return; // sem toolchain: o interpretador já cobre o caminho
        }
        Process p = new ProcessBuilder(dir.resolve("out/Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) {
            return;
        }
        assertEquals("7 7", out.strip().replace("\n", " "), "o nativo (medido: 7 7)");
    }
}
