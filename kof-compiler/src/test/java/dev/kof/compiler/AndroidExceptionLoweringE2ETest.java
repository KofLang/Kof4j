package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §622 — o alvo ANDROID reusa o {@code JvmBackend} e o MESMO contrato de
 * exceção String→{@code RuntimeException} do JVM. O wrap de `throw`/`assert`
 * estava gated a {@code Target.JVM} apenas, então o `athrow` recebia a
 * {@code String} crua na pilha → {@code VerifyError} (mascarado pelo launcher
 * como "componentes de runtime do JavaFX") em TODA app Android com
 * `throw`/`assert`. Descoberto na caça da issue #777.
 *
 * <p>Android reusa o backend JVM: o bytecode emitido é o mesmo e roda no host.
 */
class AndroidExceptionLoweringE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String THROW_PROGRAM = """
            Int f() {
                throw "boom"
            }
            main() {
                println("before")
                println(f())
                println("after")
            }
            """;

    private static final String ASSERT_PROGRAM = """
            Int f() {
                return 2
            }
            main() {
                assert(f() == 1)
                println("unreachable")
            }
            """;

    @Test
    void androidThrowEmitsVerifiableRuntimeException(@TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, THROW_PROGRAM);

        CompilationResult android = driver.compile(source, tmp.resolve("android"), Target.ANDROID);
        assertTrue(android.success(), "ANDROID compila throw: " + android.diagnostics().getDiagnostics());

        // Sem a correção, o bytecode é inválido e a classe nem carrega
        // (VerifyError). Com ela, o main roda e imprime "before" antes de
        // propagar o RuntimeException("boom").
        String out = runClass(tmp.resolve("android"));
        assertTrue(out.startsWith("before"), "esperava 'before' impresso antes da exceção, veio: " + out);
        assertTrue(out.contains("java.lang.RuntimeException: boom"),
                "a String deve ser embrulhada em RuntimeException no ANDROID, veio: " + out);
    }

    @Test
    void androidAssertEmitsVerifiableRuntimeException(@TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, ASSERT_PROGRAM);

        CompilationResult android = driver.compile(source, tmp.resolve("android"), Target.ANDROID);
        assertTrue(android.success(), "ANDROID compila assert: " + android.diagnostics().getDiagnostics());

        String out = runClass(tmp.resolve("android"));
        assertTrue(out.contains("java.lang.RuntimeException: assertion failed"),
                "assert falho deve virar RuntimeException no ANDROID, veio: " + out);
    }

    private String runClass(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            p.waitFor();
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running class", e);
        }
    }
}
