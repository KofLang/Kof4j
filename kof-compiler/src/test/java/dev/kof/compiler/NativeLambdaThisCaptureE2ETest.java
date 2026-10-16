package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #876 — a lambda que captura o `this` da classe ENVOLVENTE, no alvo nativo.
 *
 * <pre>
 *   class Contador {
 *       Int somar(Int n) { return n + 1 }
 *       (Int) -> Int explicito() { return (n: Int) -> this.somar(n) }
 *   }
 * </pre>
 *
 * No native x86-64 isto dava **SIGSEGV (rc=139)**. A causa foi lida no binário e
 * no gdb: o prólogo tratava como receptor TODO local chamado `this`, mas uma
 * lambda tem DOIS — o slot 0 (a própria lambda, plantado pelo
 * `CompilerLambdaClass`) e a CAPTURA do `this` externo, re-hospedada num slot
 * POSTERIOR com o mesmo nome (§735). O construtor gerado gravava o PRÓPRIO OBJETO
 * no campo da captura (`Lambda0_init_1`: `mov %rdi,-0x8; mov %rdi,-0x10` — a
 * captura em `%rsi` era ignorada), então o "this capturado" virava a lambda e o
 * dispatch virtual caía no `Lambda0_invoke` dela mesma: **recursão infinita**
 * (gdb: 174.665 frames de `invoke`) até estourar a pilha.
 *
 * <p>Estreitado por medição: lambda SEM captura, captura de LOCAL, captura de
 * OBJETO (com método builtin OU de usuário) e lambda devolvida de um método de
 * instância capturando local TODAS rodavam — o gatilho era exatamente a captura
 * do `this` da envolvente.
 *
 * <p>O oráculo é a JVM (rule 5): a MESMA saída tem de valer no Native x86-64.
 */
class NativeLambdaThisCaptureE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String IMPLICIT_AND_EXPLICIT = """
            class Contador {
                Int somar(Int n) { return n + 1 }

                (Int) -> Int explicito() { return (n: Int) -> this.somar(n) }
                (Int) -> Int implicito() { return (n: Int) -> somar(n) }
            }

            main() {
                var c = Contador()
                println(c.explicito()(5))
                println(c.implicito()(5))
            }
            """;

    private static final String GOLDEN = "6\n6";

    @Test
    void lambdaCapturingThisRunsOnNativeX86(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, IMPLICIT_AND_EXPLICIT);

        Path outJvm = tempDir.resolve("out-jvm");
        CompilationResult rj = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rj.success(), "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", outJvm.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String oracle = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, p.waitFor(), "JVM oracle run: " + oracle);
        assertEquals(GOLDEN, oracle, "JVM oracle golden");

        Path out = tempDir.resolve("out-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary must exist");
        ProcessBuilder pb2 = new ProcessBuilder(bin.toString());
        pb2.redirectErrorStream(true);
        Process p2 = pb2.start();
        String output = new String(p2.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        // rc=139 era o SIGSEGV (recursão infinita do `Lambda0_invoke`).
        assertEquals(0, p2.waitFor(), "native x86-64 output (139 = the old SIGSEGV): " + output);
        assertEquals(oracle, output, "Native x86-64 must match the JVM oracle (rule 5)");
    }
}