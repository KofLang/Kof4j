package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #846 (`known-bugs` §735) — o receptor IMPLÍCITO dentro de uma lambda.
 *
 * <pre>
 *   class Contador {
 *       Int somar(Int n) { return n + 1 }
 *       (Int) -> Int implicito() { return (n: Int) -> somar(n) }   // SEM `this.`
 *   }
 * </pre>
 *
 * O corpo da lambda chama um método de INSTÂNCIA da classe ENVOLVENTE sem
 * receptor. A chamada nua não menciona `this`, então a captura nunca acontecia e
 * o emissor empilhava `aload_0` — o slot 0 da LAMBDA — como receptor:
 *
 * <ul>
 *   <li>JVM: {@code VerifyError: Bad type on operand stack} (a classe nem carrega);</li>
 *   <li>Script: valor ERRADO e silencioso;</li>
 *   <li>JS: lança em runtime ({@code this} é a lambda);</li>
 *   <li>Native x86-64: SIGSEGV.</li>
 * </ul>
 *
 * O caminho EXPLÍCITO (`this.somar(n)`) sempre funcionou — é o controle deste
 * teste. A correção tem dois sítios: (a) o scanner de capturas passa a tratar a
 * chamada nua de método de instância da envolvente como uso do `this` EXTERNO
 * (resolvido pela CLASSE + aridade, não por nome simples); (b) o
 * {@code ExpressionBareCallLowerer} resolve o receptor pelo NOME (`findLocalVar`,
 * que varre de trás para frente e por isso acha a captura) em vez de carregar o
 * slot 0 fixo.
 *
 * O oráculo é a JVM (rule 5): a MESMA saída tem de valer em Script e JS.
 *
 * <p><b>O alvo NATIVE não está aqui de propósito — e não é este defeito:</b>
 * MEDIDO (15/10), o controle EXPLÍCITO `this.somar(n)` também morre com SIGSEGV
 * (rc=139) no native x86-64, enquanto uma lambda SEM captura roda normal
 * (`6/6`) — ou seja, o crash é de "lambda que CAPTURA algo" no backend nativo,
 * pré-existente e independente do receptor implícito (issue própria). Travar a
 * face nativa aqui seria travar um defeito de OUTRA família.
 */
class LambdaImplicitReceiverE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String IMPLICIT = """
            class Contador {
                Int somar(Int n) { return n + 1 }

                (Int) -> Int implicito() { return (n: Int) -> somar(n) }
            }

            main() {
                var c = Contador()
                println(c.implicito()(5))
            }
            """;

    /** O controle: o mesmo programa com `this.somar(n)` — sempre funcionou. */
    private static final String EXPLICIT = """
            class Contador {
                Int somar(Int n) { return n + 1 }

                (Int) -> Int explicito() { return (n: Int) -> this.somar(n) }
            }

            main() {
                var c = Contador()
                println(c.explicito()(5))
            }
            """;

    private static final String GOLDEN = "6";

    private Path write(Path dir, String tag, String program) throws Exception {
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    private String runJvmOracle(Path src, Path outDir) throws Exception {
        CompilationResult rj = driver.compile(src, outDir, Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM oracle run (a lambda VerifyError lands here): " + out);
        assertEquals(GOLDEN, out, "JVM oracle golden");
        return out;
    }

    @Test
    void implicitReceiverOnJvmScriptAndJs(@TempDir Path tempDir) throws Exception {
        Path src = write(tempDir, "implicit", IMPLICIT);
        String oracle = runJvmOracle(src, tempDir.resolve("out-jvm"));

        KofInterpreter.Result script = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, script.exitCode(), "Script exit, stderr=" + script.stderr());
        assertEquals(oracle, script.stdout().trim(), "Script must match the JVM oracle (rule 5)");

        Path outJs = tempDir.resolve("out-js");
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile: " + rjs.diagnostics().getDiagnostics());
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(outJs.resolve("Default.mjs"), stdout,
                new ByteArrayInputStream(new byte[0]), stdout);
        String js = stdout.toString(StandardCharsets.UTF_8).strip();
        assertEquals(0, exit, "JS exit (the lambda `this` used to throw here): " + js);
        assertEquals(oracle, js, "JS must match the JVM oracle (rule 5)");
    }

    @Test
    void explicitReceiverStillWorks(@TempDir Path tempDir) throws Exception {
        Path src = write(tempDir, "explicit", EXPLICIT);
        String oracle = runJvmOracle(src, tempDir.resolve("out-jvm-exp"));
        KofInterpreter.Result script = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, script.exitCode(), "Script exit, stderr=" + script.stderr());
        assertEquals(oracle, script.stdout().trim(), "Script must match the JVM oracle (rule 5)");
    }

}
