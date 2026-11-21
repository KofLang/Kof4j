package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1159 (21/11): o runtime web nativo (o conjunto de símbolos {@code kof_web_*})
 * é x86-64-ONLY — o cross riscv64/aarch64 não tem NENHUM deles.
 *
 * <p>MEDIDO: antes desta fatia, {@code param()}/{@code query()} eram EMITIDOS
 * no cross e o build quebrava no {@code ld} com {@code undefined reference to
 * 'kof_web_param'} — um link break silencioso em vez de um gap honesto (R6).
 * O gate de contexto passou a ser POR ALVO
 * ({@code KofWeb.contextNativeSupported(Target, fn)}): no cross toda função de
 * contexto recusa em compile-time com {@code WEB001}, como o T1 recusa com
 * {@code NAT007}.
 *
 * <p>Classe separada (o pin file `DomainGapCodesTest` tem o teto de 500 linhas
 * do ratchet de higiene de testes) e registrada como pin file em
 * {@code DomainGapParityMatrixTest} — o código `WEB001` continua documentado
 * na matriz de paridade.
 */
class NativeWebContextCrossGapTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void contextFunctionsOnCrossAreWeb001(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            assertGap(tmp, t, "param", """
                main() {
                    println(param("id"))
                }
                """);
            assertGap(tmp, t, "query", """
                main() {
                    println(query("id"))
                }
                """);
        }
    }

    /** O gate honesto: compile-time com o código nomeado, NUNCA um link break. */
    private void assertGap(Path tmp, Target target, String fn, String source) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        CompilationResult result = driver.compile(file, tmp.resolve("out-" + System.nanoTime()), target);
        assertFalse(result.success(),
                target + " must refuse the web context '" + fn + "()' (no kof_web_* symbol exists there)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("WEB001"),
                "the honest WEB001 gap for '" + fn + "' on " + target + ", got: " + diags);
        assertFalse(diags.contains("undefined reference"),
                "the refusal must happen at compile time, never at the link: " + diags);
    }
}
