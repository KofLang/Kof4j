package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §1218 RED: the second of two prints of the SAME value is served from the
 * frontend's constant fold — its IR already carries ONE literal instead of
 * the sum. Today {@code println(2)} + {@code println(1 + 1)} prints
 * {@code 2 2}, which is the right output for the wrong reason (the sum never
 * runs), and the same cause is what printed {@code 90 184} when the first
 * line was {@code 90}.
 *
 * <p>Two shapes: the fold must not replace the second print's operand with a
 * literal, and the sum must be evaluated at run time (the control keeps the
 * value that differs from the first line, which is right today).
 */
class PrintFoldReuseE2ETest {

    private static final String SAME_VALUE = """
            main() {
                println(2)
                println(1 + 1)
            }
            """;

    private static final String DIFFERENT_VALUE = """
            main() {
                println(5)
                println(40 + 40)
            }
            """;

    private static IRMethod main(Path dir, String program) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, program);
        IRModule ir = CompilerPipeline.prepareForInterpretation(new CompilerDriver(), List.of(src), dir);
        for (var c : ir.classes()) {
            for (var m : c.methods()) {
                if (m.name().equals("main")) return m;
            }
        }
        throw new AssertionError("sem main no módulo");
    }

    @Test
    void theSecondPrintKeepsItsArithmetic(@TempDir Path dir) throws Exception {
        IRMethod m = main(dir, SAME_VALUE);
        int literals = 0;
        int arith = 0;
        for (var b : m.basicBlocks()) {
            for (var op : b.operations()) {
                if (op instanceof KofLoadLiteral) literals++;
                if (op instanceof KofBinary) arith++;
            }
        }
        assertEquals(2, literals, "os dois literais impressos, nenhum dobrando o outro");
        assertEquals(1, arith, "o `1 + 1` tem de rodar como soma");
    }

    @Test
    void theControlWithADifferentValueIsRight(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, DIFFERENT_VALUE);
        String out = new CompilerDriver().interpret(List.of(src), dir, new String[0]).stdout().strip();
        assertEquals("5\n80", out);
    }
}
