package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** #851: a superfície REAL do receptor estático de tipo embutido, travada.
 *
 *  `training/idioms/strings.md` listava `Math` e `System` entre os receptores
 *  estáticos de tipo embutido — o compilador recusa os dois (`SEM011 Undefined
 *  variable or type`), e o idioma oficial de matemática é o NAMESPACE `math.*`.
 *  A causa de a lista errada sobreviver à revisão está no próprio compilador:
 *  `CompilerDriverState.isBuiltinStaticReceiver` lista `Math`/`System` entre os
 *  NOMES aceitos como receptor — a tabela abre a porta sem fornecer os métodos,
 *  e a resolução recusa em seguida. Este teste trava as duas direções: os nomes
 *  que são receptores de verdade continuam compilando, e `Math`/`System` seguem
 *  recusados com `SEM011` (se alguém abrir `Math` como receptor, é decisão
 *  deliberada — e este teste precisa mudar junto com o doc).
 */
class BuiltinStaticReceiverSurfaceE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path dir, String name, String body) throws IOException {
        Path src = dir.resolve(name + ".kf");
        Files.writeString(src, "main() {\n" + body + "\n}\n");
        return driver.compile(src, dir.resolve(name + "-out"), Target.JVM);
    }

    @Test
    void realBuiltinStaticReceiversCompile(@TempDir Path dir) throws IOException {
        assertTrue(compile(dir, "valueof", "println(\"n=\" + String.valueOf(42))").success(),
                "String.valueOf is a real builtin static receiver");
        assertTrue(compile(dir, "integer", "println(Integer.toHexString(255))").success(),
                "Integer.toHexString is a real builtin static receiver");
        assertTrue(compile(dir, "mathns", "println(math.max(3, 4))\nprintln(math.abs(-3))").success(),
                "math.* is the official Kof namespace for arithmetic helpers");
    }

    @Test
    void mathAndSystemAreNotReceivers(@TempDir Path dir) throws IOException {
        for (String[] c : new String[][]{
                {"mathupper", "println(Math.max(3, 4))", "Math"},
                {"systemupper", "println(System.lineSeparator())", "System"}}) {
            CompilationResult r = compile(dir, c[0], c[1]);
            assertFalse(r.success(), c[2] + " must NOT be a Kof receiver (use math.* / println)");
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("SEM011"),
                    c[2] + " refusal must be SEM011, got: " + diags);
            assertTrue(diags.contains("Undefined variable or type: '" + c[2] + "'"),
                    c[2] + " refusal message, got: " + diags);
        }
    }
}