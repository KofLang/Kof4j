package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1212 (23/11, app do Énois, mistura do `Skeleton`): a soma de dois produtos
 * com o parêntese `a * (100 - t)` devolve o DOBRO do segundo termo (o primeiro
 * é descartado) num módulo real — o `as` e a IR estão corretos, medido no
 * `Skeleton` (a ponta do pulso sai branca). A forma NÃO se reproduz sozinha no
 * módulo mínimo (o arranjo de registradores depende do resto do módulo): o
 * teste mede a forma e o comportamento esperado.
 */
class NativeSumOfProductsE2ETest {

    @Test
    void aSumOfTwoProductsPaintsTheRightNumbers(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
            class Ch {
                static Int ch(Int a, Int b, Int t) {
                    var v = a * (100 - t)
                    v = v + b * t
                    return v
                }
            }

            main() {
                println(Ch.ch(255, 237, 0))
                println(Ch.ch(237, 255, 100))
            }
            """);
        CompilationResult r = new CompilerDriver().compile(src, dir.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        Process p = new ProcessBuilder(dir.resolve("out/Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS), "o programa termina");
        assertTrue(out.contains("25500"), "o primeiro termo a*(100-t): " + out);
    }
}
