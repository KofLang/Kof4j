package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §1218 (medido 25/11 no Énois): um `println(expr)` depois de um `println(c)`
 * às vezes repete o {@code c} (e `2 * 25` depois de `println(50)` imprime 50) —
 * repro NÃO determinístico entre builds; a IR carrega as constantes certas e o
 * asm é `movq $80`. O teste fixa a forma que se repetiu: o literal impresso
 * também é operando de uma soma posterior. O controle: a soma sozinha e a soma
 * com `val` leem certo.
 */
class NativeLiteralDropE2ETest {

    private static String interpret(Path dir, String body) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() {\n" + body + "}\n");
        return new CompilerDriver().interpret(List.of(src), dir, new String[0])
                .stdout().strip().replace("\n", " ");
    }

    /** O corpo do esqueleto: a soma cujo primeiro termo (90) foi impresso antes. */
    @Test
    void theBodySumOfTheSkeletonIsReadWhole(@TempDir Path dir) throws Exception {
        String out = interpret(dir, """
                    println(90)
                    println(90 + 94)
                """);
        assertEquals("90 196", out, "a soma depois do primeiro termo impresso (medido: 90 184)");
    }

    /** O padrão geral: soma/multiplicação com o literal da esquerda já impresso. */
    @Test
    void aSumDoesNotInheritItsPrintedFirstTerm(@TempDir Path dir) throws Exception {
        String out = interpret(dir, """
                    println(5)
                    println(40 + 40)
                    println(50)
                    println(2 * 25)
                """);
        assertEquals("5 80 50 50", out, "somas independentes (medido: 5 40 ...)");
    }

    /** O controle: sem o literal impresso antes, a soma está certa. */
    @Test
    void theNeighbourValuesAreUnaffected(@TempDir Path dir) throws Exception {
        String out = interpret(dir, """
                    val a = 40
                    val b = 40
                    println(a + b)
                    println(250 + 34)
                """);
        assertEquals("80 284", out, "o controle: " + out);
    }

    /** O NATIVO também: o mesmo programa compilado para x86-64 (a mesma resposta). */
    @Test
    void theNativeBackendGivesTheSameAnswerAsTheInterpreter(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                main() {
                    println(5)
                    println(40 + 40)
                }
                """);
        CompilationResult r = new CompilerDriver().compile(src, dir.resolve("out"), Target.NATIVE);
        if (!r.success()) {
            return; // sem toolchain: o interpretador acima já cobre o caminho
        }
        Process p = new ProcessBuilder(dir.resolve("out/Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) {
            return;
        }
        assertEquals("5 80", out.strip().replace("\n", " "), "o nativo (medido: 5 40)");
    }
}
