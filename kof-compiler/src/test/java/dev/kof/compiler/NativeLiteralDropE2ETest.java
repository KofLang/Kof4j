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
 * <p>O PADRÃO medido: um {@code println} de um LITERAL gruda como valor atual —
 * {@code println(50)}, {@code println(2 * 25)}, {@code println(25 + 25)},
 * {@code println(40 + 40)} imprimem {@code 50 50 50 80}. O mesmo fonte na JVM
 * imprime certo, e o asm nativo é o correto ({@code movq $90, %rax / addl $94,
 * %eax}), então o defeito está no caminho da impressão (o conjunto de slices
 * do runtime que sobrevive ao prune).
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
        return out.strip().replace("\n", " ");
    }

    /** O corpo do esqueleto: 90 + 94 sai 184 (o primeiro termo) em vez de 196. */
    @Test
    void theBodySumOfTheSkeletonIsReadWhole(@TempDir Path dir) throws Exception {
        String out = run(dir, "    println(90 + 94)\n");
        assertTrue(out.equals("196"), "90 + 94 = 196 (medido: " + out + ")");
    }

    /** O vizinho: um valor que nao e' o 184 continua certo (o controle). */
    @Test
    void theNeighbourValuesAreUnaffected(@TempDir Path dir) throws Exception {
        String out = run(dir, "    println(250 + 34)\n");
        assertTrue(out.equals("284"), "250 + 34 = 284: " + out);
    }

    /** O padrao medido: o println de um literal REPETE o valor anterior. */
    @Test
    void thePreviousLiteralPrintIsNotRepeated(@TempDir Path dir) throws Exception {
        String out = run(dir, """
                    println(50)
                    println(2 * 25)
                    println(25 + 25)
                    println(40 + 40)
                """);
        assertTrue(out.equals("50 50 50 80"), "o 2o/3o println repetem o 50 (medido: " + out + ")");
    }
}
