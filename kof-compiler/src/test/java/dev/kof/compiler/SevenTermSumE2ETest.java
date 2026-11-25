package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §1218 (reduzido 25/11): a soma de SETE literais
 * {@code 20 + 40 + 12 + 12 + 32 + 48 + 20} vale 196, e o compilador a dobra em
 * <b>184</b> — medido no JVM (bytecode {@code sipush 184}) e no nativo
 * ({@code movq $184, %rax}), no interpretador e no CLI. A soma de CINCO
 * literais que também vale 196 está certa, então a forma é a contagem de
 * termos, não o valor.
 *
 * <p>O teste fixa o valor certo; fica RED até o defeito fechar.
 */
class SevenTermSumE2ETest {

    private static final String PROG = """
            Int soma() { return 20 + 40 + 12 + 12 + 32 + 48 + 20 }

            main() {
                println(soma())
            }
            """;

    @Test
    void theSevenTermSumIs196() {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("kof7");
            Path src = dir.resolve("Main.kf");
            Files.writeString(src, PROG);
            String out = new CompilerDriver().interpret(List.of(src), dir, new String[0])
                    .stdout().strip();
            assertEquals("196", out, "20 + 40 + 12 + 12 + 32 + 48 + 20 = 196 (medido 184)");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theFiveTermSumOf196IsRight() {
        String out;
        try {
            Path dir = Files.createTempDirectory("kof5");
            Path src = dir.resolve("Main.kf");
            Files.writeString(src, "Int soma() { return 100 + 20 + 30 + 20 + 26 }\n\n"
                    + "main() {\n    println(soma())\n}\n");
            out = new CompilerDriver().interpret(List.of(src), dir, new String[0]).stdout().strip();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals("196", out, "o controle de cinco termos");
    }
}
