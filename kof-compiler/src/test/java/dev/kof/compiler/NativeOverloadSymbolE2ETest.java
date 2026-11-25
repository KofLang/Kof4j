package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1210 (23/11, app do Énois): duas chamadas a um método ESTÁTICO
 * sobrecarregado (mesma aridade, tipos diferentes) compilam para x86-64; a
 * correção e a regressão ficam registradas no `known-bugs` — o RED deste teste
 * é `ld: referência não definida para "Duas_soma"`.
 */
class NativeOverloadSymbolE2ETest {

    @Test
    void twoTwoArgumentOverloadsLinkAndRun(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
            class Duas {
                static Int soma(Int a, Int b) { return a + b }
                static Int soma(String a, String b) { return 2 }
            }

            main() {
                println(Duas.soma(1, 2))
                println(Duas.soma("x", "y"))
            }
            """);
        CompilationResult r = new CompilerDriver().compile(src, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        Process p = new ProcessBuilder(tempDir.resolve("out/Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS), "o programa termina");
        assertTrue(out.contains("3") && out.contains("2"), "a saída: " + out);
    }
}
