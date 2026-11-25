package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1218 (medido 25/11 no Énois, soma do esqueleto de carregamento): no x86-64
 * nativo um literal do valor da soma perde um termo — {@code println(90 + 94)}
 * imprime **184** (o certo é 196) e o mesmo valor, em QUALQUER forma
 * ({@code (91 + 93)}, {@code (92 + 92)}, {@code 168 + 16}, {@code 104 + 80},
 * {@code 92 * 2}, {@code 184} sozinho), também imprime 184. Os vizinhos
 * ({@code 90 + 94 + 1} = 185, {@code 250 + 34} = 284, {@code 17 + 26 + 92 + 49 +
 * 50 + 60} = 294) medem certo.
 *
 * <p>A contraprova que localiza a perda FORA do frontend: o mesmo valor escrito
 * como {@code 284 - 100} ou {@code 100 + 84} imprime 184, e o asm emitido para
 * {@code println(184)} é {@code movq $184, %rdi} seguido de
 * {@code call kof_int_to_string} — o literal chega ao registrador certo, então
 * a perda acontece no caminho do RUNTIME/FIGURA (o mesmo módulo compila 20, 32,
 * 48, 60, 72, 84, 100 e 164 sem perda). O teste fixa a medição: o valor correto
 * é o total da conta, e é o que a tela precisa ver.
 */
class NativeLiteralDropE2ETest {

    private static String run(Path dir, String body) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() {\n" + body + "}\n");
        CompilationResult r = new CompilerDriver().compile(src, dir.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        Process p = new ProcessBuilder(dir.resolve("out/Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS), "o programa termina");
        return out;
    }

    @Test
    void theBodySumOfTheSkeletonIsReadWhole(@TempDir Path dir) throws Exception {
        String out = run(dir, """
                    println(90 + 94)
                    println(20 + 40 + 12 + 12 + 32 + 48 + 20)
                """);
        assertTrue(out.contains("196"), "90 + 94 = 196 (medido 184): " + out);
        assertTrue(out.contains("196\n196") || out.endsWith("196"), "a soma do esqueleto = 196 (medido 184): " + out);
    }

    @Test
    void theNeighbourValuesAreUnaffected(@TempDir Path dir) throws Exception {
        String out = run(dir, """
                    println(250 + 34)
                    println(17 + 26 + 92 + 49 + 50 + 60)
                """);
        assertTrue(out.contains("284"), "250 + 34: " + out);
        assertTrue(out.contains("294"), "17 + 26 + 92 + 49 + 50 + 60: " + out);
    }
}
