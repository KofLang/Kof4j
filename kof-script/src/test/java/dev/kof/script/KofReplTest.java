package dev.kof.script;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #739 — REPL incremental. O modelo antigo re-avaliava o histórico inteiro a
 * cada linha (duplicando efeitos colaterais) e só ecoava `var` de topo. Estes
 * pins cobrem: eco do valor de expressão/variável, ausência de duplicação,
 * persistência de globais e rollback em erro.
 */
class KofReplTest {

    private String runRepl(String input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        KofScript.repl(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8));
        // remove banner + prompt prefixes (o valor ecoado divide a linha com o
        // prompt: "kof> 2"), mantendo o que o programa imprimiu.
        return out.toString(StandardCharsets.UTF_8)
                .replace("KofScript REPL 0.1.2-beta — type 'exit' to quit\n", "")
                .replace("kof> ", "").replace("...> ", "")
                .strip();
    }

    @Test
    void echoesBareExpressionValue() throws Exception {
        assertEquals("2", runRepl("1 + 1\nexit\n"));
    }

    @Test
    void echoesVariableReference() throws Exception {
        assertEquals("João\nJoão", runRepl("var name = \"João\"\nname\nexit\n"));
    }

    @Test
    void doesNotDuplicateSideEffects() throws Exception {
        assertEquals("X\nY", runRepl("println(\"X\")\nprintln(\"Y\")\nexit\n"));
    }

    @Test
    void globalsPersistAcrossLines() throws Exception {
        assertEquals("5\n6\n10", runRepl("var x = 5\nx + 1\nx = 10\nx\nexit\n"));
    }

    @Test
    void declaredFunctionIsUsable() throws Exception {
        assertEquals("5", runRepl("add(a: Int, b: Int): Int = a + b\nadd(2, 3)\nexit\n"));
    }

    @Test
    void errorRollsBackLastLine() throws Exception {
        String out = runRepl("var y = 1\ny = \ny\nexit\n");
        assertTrue(out.contains("error:"), "expected an error line, got: " + out);
        String values = out.lines()
                .filter(l -> !l.startsWith("error:") && !l.startsWith("Main.kf:"))
                .reduce("", (a, b) -> a + b + "\n").strip();
        assertEquals("1\n1", values, "y must keep 1 after the failed line: " + out);
    }
}
