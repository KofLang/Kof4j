package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #199 — switch-EXPRESSION com PADRÃO TIPO-GUARDADO
 * ({@code case String s if s.length() > 2 -> s.length()}) omitia o store da
 * variável bound: o braço usa `s` mas o slot local nunca era escrito →
 * VerifyError "Bad local variable type" no LOAD da classe (ec≠0).
 *
 * RAIZ (SwitchExprLowerer.emitSwitchChain): com GUARDA, o MESMO `bodyLabel`
 * era visitado DUAS vezes — (a) alvo do `instanceof NE` (antes do binding) e
 * (b) alvo do `guarda EQ 0 → senão` (depois do binding). Em todas as
 * resoluções de label dos backends a ÚLTIMA visita vence, então o
 * instanceof-true CAÍA DEPOIS do `astore` → a var bound nunca era escrita no
 * caminho que a usa. O forma-STATEMENT (SwitchStmtLowerer) já fazia certo
 * (guarda no TESTE com #guardCast separado); só a forma-EXPRESSION estava
 * quebrada.
 *
 * FIX: com guarda, `bindingLabel` distinto de `bodyLabel` — instanceof-true
 * vai ao binding, guarda-true vai ao corpo. Sem guarda, bindingLabel ==
 * bodyLabel (queda direta, uma visita só — idêntico ao antes). Um ajuste na
 * IR compartilhada cura JVM/Native/Script (regra 5: a raiz é do lowerer).
 *
 * Borda honesta NÃO corrigida aqui (pré-existente, NÃO regressão): o alvo JS
 * tem um FRONTEND próprio (parseExpressionFragment) que NÃO lowering da IR e
 * CHUTA `KofConditionalJump` em posição de statement — o MESMO programa dá
 * COMP002 no JS no HEAD LIMPO da issue (medido no jar pré-#199). É um gap
 * R6-compliant (nunca silencioso) do KofJS, separado e mais antigo; catálogo
 * na issue, NÃO é a raiz do #199 nem foi introduzido por este fix.
 */
class GuardedPatternSwitchExprE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    // Corpo exato do #199 (switch-expr, padrão String guardado): os três
    // braços (guarda true, guarda false, default). Antes: VerifyError.
    @Test
    void guardedStringPatternSwitchExprJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("guard199.kf");
        Files.writeString(src, """
                Int pick(Object o) {
                    return switch (o) {
                        case String s if s.length() > 2 -> s.length()
                        case String s -> 1
                        default -> 0
                    }
                }
                main() {
                    println(pick("abc"))
                    println(pick("a"))
                    println(pick(5))
                }
                """);
        Path out = tempDir.resolve("guard199-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("3\n1\n0", runJvm(out));
    }

    // Borda de INTENTO (Q3): a var bound é lida DENTRO do corpo do braço
    // guardado (não só na guarda) — o slot precisa estar escrito no caminho
    // do corpo. Usa o objeto de domínio com campo, padrão de destructuring.
    @Test
    void guardedPatternBindsForArmBodyJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("guard199b.kf");
        Files.writeString(src, """
                class Num {
                    Int v
                    constructor(Int v) { this.v = v }
                }
                Int big(Num? n) {
                    return switch (n) {
                        case Num x if x.v > 10 -> x.v * 2
                        case Num x -> x.v
                        default -> -1
                    }
                }
                main() {
                    println(big(Num(50)))
                    println(big(Num(5)))
                    println(big(null))
                }
                """);
        Path out = tempDir.resolve("guard199b-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("100\n5\n-1", runJvm(out));
    }

    // Paridade Script (interpretador, mesma IR — `kof run --target script`).
    @Test
    void guardedPatternSwitchExprScript(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("guard199s.kf");
        Files.writeString(src, """
                Int pick(Object o) {
                    return switch (o) {
                        case String s if s.length() > 2 -> s.length()
                        case String s -> 1
                        default -> 0
                    }
                }
                main() {
                    println(pick("abc"))
                    println(pick("a"))
                    println(pick(5))
                }
                """);
        KofInterpreter.Result r = driver.interpret(List.of(src), src.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), "Script exit/stderr: " + r.stdout() + " " + r.stderr());
        assertEquals("3\n1\n0", r.stdout().trim());
    }
}
